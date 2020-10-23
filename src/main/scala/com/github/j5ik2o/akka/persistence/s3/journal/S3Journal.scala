package com.github.j5ik2o.akka.persistence.s3.journal

import akka.actor.{ ActorSystem, DynamicAccess, ExtendedActorSystem }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Attributes
import akka.stream.scaladsl.{ Sink, Source }
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

  private def resolveKey(persistenceId: PersistenceId, seqNr: Long, deleted: Boolean = false): String = {
    val k = "%s.%011d".format(persistenceId.asString, seqNr)
    if (deleted)
      k + ".deleted.journal"
    else
      k + ".journal"
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
          val req = PutObjectRequest
            .builder()
            .bucket(resolveBucketName(journalRow.persistenceId))
            .key(key)
            .build()
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
      .delimiter("/")
      .build()
    Source
      .single(req)
      .mapAsync(1) { req =>
        s3AsyncClient
          .listObjectsV2(req)
      }
      .mapConcat { res =>
        if (res.hasContents)
          res.contents.asScala
            .map { obj =>
              val key    = obj.key()
              val result = key.split("\\.")
              val pid    = result(0)
              val seqNr  = result(1).toLong
              (obj, pid, seqNr)
            }
            .filter { case (_, pid, seqNr) => pid == persistenceId && seqNr <= toSequenceNr }
            .toVector
            .sortWith(_._1.key() < _._1.key())
        else
          Vector.empty
      }
      .mapAsync(1) {
        case (obj, persistenceId, seqNr) =>
          val req = CopyObjectRequest
            .builder()
            .copySource(resolveBucketName(pid) + "/" + obj.key())
            .destinationBucket(resolveBucketName(pid))
            .destinationKey(resolveKey(pid, seqNr, true))
            .build()
          s3AsyncClient.copyObject(req).flatMap { _ =>
            val req = DeleteObjectRequest.builder().bucket(resolveBucketName(pid)).key(obj.key()).build()
            s3AsyncClient.deleteObject(req)
          }
      }
      .withAttributes(logLevels)
      .runWith(Sink.ignore)
      .map(_ => ())

  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit
  ): Future[Unit] = {
    val fromSeqNr = Math.max(1, fromSequenceNr)
    val s =
      if (max == 0 || fromSeqNr > toSequenceNr)
        Source.empty
      else {
        val pid = PersistenceId(persistenceId)
        val req = ListObjectsV2Request
          .builder()
          .bucket(resolveBucketName(pid))
          .delimiter("/")
          .build()
        Source
          .single(req)
          .mapAsync(1) { req =>
            s3AsyncClient
              .listObjectsV2(req)
          }
          .mapConcat { res =>
            if (res.hasContents)
              res.contents.asScala
                .map { obj =>
                  val key     = obj.key()
                  val result  = key.split("\\.")
                  val pid     = result(0)
                  val seqNr   = result(1).toLong
                  val deleted = if (result(2) == "deleted") true else false
                  (key, deleted, pid, seqNr)
                }
                .toVector
                .sortWith(_._1 < _._1)
            else
              Vector.empty
          }
          .filter {
            case (_, deleted, pid, seqNr) =>
              !deleted && pid == persistenceId && fromSeqNr <= seqNr && seqNr <= toSequenceNr
          }
          .log("element")
          .mapAsync(1) {
            case (key, _, _, _) =>
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
      }
    s
      .withAttributes(logLevels)
      .runForeach { result =>
        result.foreach(recoveryCallback)
      }
      .map(_ => ())
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val fromSeqNr = Math.max(1, fromSequenceNr)
    val pid       = PersistenceId(persistenceId)
    val req = ListObjectsV2Request
      .builder()
      .bucket(resolveBucketName(pid))
      .delimiter("/")
      .build()
    Source
      .single(req)
      .mapAsync(1) { req =>
        s3AsyncClient
          .listObjectsV2(req)
      }
      .map { res =>
        val s =
          if (res.hasContents)
            res.contents.asScala
              .map { obj =>
                val key    = obj.key()
                val result = key.split("\\.")
                val pid    = result(0)
                val seqNr  = result(1).toLong
                (pid, seqNr)
              }
              .filter {
                case (pid, seqNr) =>
                  pid == persistenceId && fromSeqNr <= seqNr
              }
              .toVector
              .sortWith(_._1 < _._1)
          else
            Vector.empty
        s.lastOption.fold(0L)(_._2)
      }
      .withAttributes(logLevels)
      .runWith(Sink.head)
  }

}
