package com.github.j5ik2o.akka.persistence.s3.journal

import java.util.function.Consumer

import akka.actor.{ ActorSystem, DynamicAccess, ExtendedActorSystem }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Attributes
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.github.j5ik2o.akka.persistence.s3.base.PersistenceId
import com.github.j5ik2o.akka.persistence.s3.config.{ JournalPluginConfig, S3ClientConfig }
import com.github.j5ik2o.akka.persistence.s3.resolver.BucketNameResolver
import com.github.j5ik2o.akka.persistence.s3.serialization.{ ByteArrayJournalSerializer, FlowPersistentReprSerializer }
import com.github.j5ik2o.akka.persistence.s3.utils.{ HttpClientBuilderUtils, S3ClientBuilderUtils }
import com.github.j5ik2o.reactive.aws.s3.S3AsyncClient
import com.typesafe.config.Config
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncRequestBody
import software.amazon.awssdk.services.s3.model._

import scala.collection.immutable
import scala.collection.immutable.{ Nil, Seq }
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

class S3Journal(config: Config) extends AsyncWriteJournal {
  implicit val system: ActorSystem = context.system
  import system.dispatcher

  private val pluginConfig: JournalPluginConfig   = JournalPluginConfig.fromConfig(config)
  private val bucketNameResolverClassName: String = pluginConfig.bucketNameResolverClassName
  private val s3ClientConfig: S3ClientConfig      = pluginConfig.clientConfig
  private val httpClientBuilder                   = HttpClientBuilderUtils.setup(s3ClientConfig)
  private val javaS3ClientBuilder =
    S3ClientBuilderUtils.setup(s3ClientConfig, httpClientBuilder.build())
  private val s3AsyncClient = S3AsyncClient(javaS3ClientBuilder.build())

  private val extendedSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  private val dynamicAccess: DynamicAccess        = extendedSystem.dynamicAccess

  protected val bucketNameResolver: BucketNameResolver = {
    dynamicAccess
      .createInstanceFor[BucketNameResolver](bucketNameResolverClassName, immutable.Seq(classOf[Config] -> config))
      .getOrElse(throw new ClassNotFoundException(bucketNameResolverClassName))
  }

  private val serialization: Serialization = SerializationExtension(system)

  private def resolveBucketName(persistenceId: PersistenceId) = {
    pluginConfig.bucketName
      .map(_.stripPrefix("/"))
      .getOrElse(bucketNameResolver.resolve(persistenceId))
  }

  private def resolveKey(persistenceId: PersistenceId, seqNr: Long): String = {
    "%s.%011d.journal".format(persistenceId.asString, seqNr)
  }

  protected val serializer: FlowPersistentReprSerializer[JournalRow] =
    new ByteArrayJournalSerializer(serialization, pluginConfig.tagSeparator)

  protected val logLevels: Attributes = Attributes.logLevels(
    onElement = Attributes.LogLevels.Debug,
    onFailure = Attributes.LogLevels.Error,
    onFinish = Attributes.LogLevels.Debug
  )
  override def asyncWriteMessages(atomicWrites: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    val serializedTries = serializer.serialize(atomicWrites)
    val rowsToWrite = for {
      serializeTry <- serializedTries
      row <- serializeTry match {
        case Right(value) => value
        case Left(_)      => Seq.empty
      }
    } yield row
    def resultWhenWriteComplete =
      if (serializedTries.forall(_.isRight))
        Nil
      else
        serializedTries.map {
          case Right(_) => Right(())
          case Left(ex) => Left(ex)
        }
    val future = rowsToWrite
      .foldLeft(Future.successful(Vector.empty[PutObjectResponse])) {
        case (result, journalRow) =>
          val key = resolveKey(journalRow.persistenceId, journalRow.sequenceNumber.value)
          val req = PutObjectRequest.builder().bucket(resolveBucketName(journalRow.persistenceId)).key(key).build()
          for {
            r <- result
            e <- s3AsyncClient.putObject(req, new ByteArrayAsyncRequestBody(journalRow.message))
          } yield r :+ e
      }
      .map { _ =>
        resultWhenWriteComplete.map {
          case Right(value) => Success(value)
          case Left(ex)     => Failure(ex)
        }.toVector
      }
    future
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    val pid = PersistenceId(persistenceId)
    val req = ListObjectsV2Request
      .builder()
      .bucket(resolveBucketName(pid))
      .build()
    Source
      .single(req)
      .mapAsync(1) { req =>
        s3AsyncClient
          .listObjectsV2(req)
      }
      .mapConcat { res =>
        res.contents.asScala.map { obj =>
          val key    = obj.key()
          val result = key.split("\\.")
          val pid    = result(0)
          val seqNr  = result(1).toLong
          (key, pid, seqNr)
        }.toVector
      }
      .log("element")
      .filter { case (_, pid, seqNr) => pid == persistenceId && seqNr <= toSequenceNr }
      .map {
        case (key, _, _) =>
          ObjectIdentifier.builder().key(key).build()
      }
      .batch(10, ArrayBuffer(_)) { _ :+ _ }
      .log("batch")
      .mapAsync(1) { objects =>
        val delete    = Delete.builder().objects(objects.asJava).build()
        val deleteReq = DeleteObjectsRequest.builder().bucket(resolveBucketName(pid)).delete(delete).build()
        s3AsyncClient.deleteObjects(deleteReq)
      }
      .map(_ => ())
      .withAttributes(logLevels)
      .runWith(Sink.head)

  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit
  ): Future[Unit] = {
    if (max == 0 || fromSequenceNr > toSequenceNr)
      Future.successful(())
    else {
      val fromSeqNr = Math.max(1, fromSequenceNr)
      val pid       = PersistenceId(persistenceId)
      val req = ListObjectsV2Request
        .builder()
        .bucket(resolveBucketName(pid))
        .build()
      Source
        .single(req)
        .mapAsync(1) { req =>
          s3AsyncClient
            .listObjectsV2(req)
        }
        .mapConcat { res =>
          if (res.hasContents)
            Option(res.contents)
              .map(_.asScala)
              .map {
                _.map { obj =>
                  val key    = obj.key()
                  val result = key.split("\\.")
                  val pid    = result(0)
                  val seqNr  = result(1).toLong
                  (key, pid, seqNr)
                }.sortBy { e => e._1 }.toVector
              }
              .getOrElse(Vector.empty)
          else
            Vector.empty
        }
        .filter {
          case (_, pid, seqNr) =>
            pid == persistenceId && fromSeqNr <= seqNr && seqNr <= toSequenceNr
        }
        .log("element")
        .mapAsync(1) {
          case (key, _, _) =>
            val req = GetObjectRequest
              .builder()
              .bucket(resolveBucketName(pid))
              .key(key)
              .build()
            s3AsyncClient.getObjectAsBytes(req).map { result =>
              result.asByteArray()
            }
        }
        .map { bytes =>
          serialization
            .deserialize(bytes, classOf[PersistentRepr])
        }
        .take(max)
        .withAttributes(logLevels)
        .runForeach { result =>
          result.foreach(recoveryCallback)
        }
        .map(_ => ())
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val fromSeqNr = Math.max(1, fromSequenceNr)
    val pid       = PersistenceId(persistenceId)
    val req = ListObjectsV2Request
      .builder()
      .bucket(resolveBucketName(pid))
      .build()
    Source
      .single(req)
      .mapAsync(1) { req =>
        s3AsyncClient
          .listObjectsV2(req)
      }
      .mapConcat { res =>
        if (res.hasContents)
          Option(res.contents)
            .map(_.asScala)
            .map {
              _.map { obj =>
                val key    = obj.key()
                val result = key.split("\\.")
                val pid    = result(0)
                val seqNr  = result(1).toLong
                (key, pid, seqNr)
              }.sortBy { e => e._1 }.toVector
            }
            .getOrElse(Vector.empty)
        else
          Vector.empty
      }
      .filter {
        case (_, pid, seqNr) =>
          pid == persistenceId && fromSeqNr <= seqNr
      }
      .fold(Vector.empty[(String, String, Long)])(_ :+ _)
      .map { res =>
        if (res.nonEmpty)
          res.last._3
        else
          0
      }
      .withAttributes(logLevels)
      .runWith(Sink.head)
  }

}
