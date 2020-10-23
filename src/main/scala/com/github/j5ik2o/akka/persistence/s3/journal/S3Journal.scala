package com.github.j5ik2o.akka.persistence.s3.journal

import akka.actor.{ ActorSystem, DynamicAccess, ExtendedActorSystem }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Attributes
import akka.stream.scaladsl.{ Sink, Source }
import com.github.j5ik2o.akka.persistence.s3.base.config.S3ClientConfig
import com.github.j5ik2o.akka.persistence.s3.base.model.{ PersistenceId, SequenceNumber }
import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.github.j5ik2o.akka.persistence.s3.resolver.{
  BucketNameResolver,
  JournalMetadataKey,
  JournalMetadataKeyConverter,
  Key,
  PathPrefixResolver
}
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

  private sealed trait FlowControl

  /** Keep querying - used when we are sure that there is more events to fetch */
  private case object Continue extends FlowControl

  /** Stop querying - used when we reach the desired offset */
  private case object Stop extends FlowControl

  private val pluginConfig: JournalPluginConfig   = JournalPluginConfig.fromConfig(config)
  private val bucketNameResolverClassName: String = pluginConfig.bucketNameResolverClassName
  private val keyConverterClassName: String       = pluginConfig.keyConverterClassName
  private val pathPrefixResolverClassName: String = pluginConfig.pathPrefixResolverClassName
  private val extensionName: String               = pluginConfig.extensionName
  private val listObjectsBatchSize: Int           = pluginConfig.listObjectsBatchSize
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

  protected val keyConverter: JournalMetadataKeyConverter = {
    dynamicAccess
      .createInstanceFor[JournalMetadataKeyConverter](keyConverterClassName, immutable.Seq(classOf[Config] -> config))
      .getOrElse(throw new ClassNotFoundException(keyConverterClassName))
  }

  protected val pathPrefixResolver: PathPrefixResolver = {
    dynamicAccess
      .createInstanceFor[PathPrefixResolver](pathPrefixResolverClassName, immutable.Seq(classOf[Config] -> config))
      .getOrElse(throw new ClassNotFoundException(pathPrefixResolverClassName))
  }

  private val serialization: Serialization = SerializationExtension(system)

  private def resolveBucketName(persistenceId: PersistenceId) = {
    pluginConfig.bucketName
      .map(_.stripPrefix("/"))
      .getOrElse(bucketNameResolver.resolve(persistenceId))
  }

  private def resolveKey(persistenceId: PersistenceId, seqNr: SequenceNumber, deleted: Boolean = false): String = {
    keyConverter.convertTo(JournalMetadataKey(persistenceId, seqNr, deleted), extensionName)
  }

  private def reverseKey(key: Key): (PersistenceId, SequenceNumber, Boolean) = {
    val result = keyConverter.convertFrom(key, extensionName)
    (result.persistenceId, result.sequenceNumber, result.deleted)
  }

  private def resolvePathPrefix(
      persistenceId: PersistenceId
  ): Option[String] = {
    pluginConfig.pathPrefix.orElse(pathPrefixResolver.resolve(persistenceId))
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
          val key = resolveKey(journalRow.persistenceId, journalRow.sequenceNumber)
          val req = PutObjectRequest
            .builder()
            .bucket(resolveBucketName(journalRow.persistenceId))
            .key(key)
            .build()
          for {
            r <- result
            e <- s3AsyncClient.putObject(req, new ByteArrayAsyncRequestBody(journalRow.message))
            _ <- {
              if (e.sdkHttpResponse().isSuccessful)
                Future.successful(())
              else
                Future.failed(
                  new S3JournalException(
                    s"Failed to putObject: statusCode = ${e.sdkHttpResponse.statusCode()}"
                  )
                )
            }
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
    listObjectsSource(pid, listObjectsBatchSize)
      .log("list-objects")
      .mapConcat { res =>
        if (res.hasContents)
          res.contents.asScala
            .map { obj =>
              val key             = obj.key()
              val (pid, seqNr, _) = reverseKey(key)
              (obj, pid.asString, seqNr.value)
            }
            .filter { case (_, pid, seqNr) => pid == persistenceId && seqNr <= toSequenceNr }
            .toVector
            .sortWith(_._3 < _._3)
        else
          Vector.empty
      }
      .mapAsync(1) {
        case (obj, persistenceId, seqNr) =>
          val req = CopyObjectRequest
            .builder()
            .copySource(resolveBucketName(pid) + "/" + obj.key())
            .destinationBucket(resolveBucketName(pid))
            .destinationKey(resolveKey(pid, SequenceNumber(seqNr), deleted = true))
            .build()
          s3AsyncClient.copyObject(req).flatMap { res =>
            if (res.sdkHttpResponse().isSuccessful) {
              val req = DeleteObjectRequest.builder().bucket(resolveBucketName(pid)).key(obj.key()).build()
              s3AsyncClient.deleteObject(req).flatMap { res =>
                if (res.sdkHttpResponse().isSuccessful)
                  Future.successful(res)
                else
                  Future.failed(
                    new S3JournalException(
                      s"Failed to deleteObject: statusCode = ${res.sdkHttpResponse.statusCode()}"
                    )
                  )
              }
            } else
              Future.failed(
                new S3JournalException(
                  s"Failed to copyObject: statusCode = ${res.sdkHttpResponse.statusCode()}"
                )
              )
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
        listObjectsSource(pid, listObjectsBatchSize)
          .log("list-objects")
          .mapConcat { res =>
            if (res.hasContents)
              res.contents.asScala
                .map { obj =>
                  val key                   = obj.key()
                  val (pid, seqNr, deleted) = reverseKey(key)
                  (key, deleted, pid.asString, seqNr.value)
                }
                .toVector
                .sortWith(_._4 < _._4)
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
              s3AsyncClient.getObjectAsBytes(req).flatMap { result =>
                if (result.response().sdkHttpResponse().isSuccessful)
                  Future.successful(result.asByteArray())
                else
                  Future.failed(
                    new S3JournalException(
                      s"Failed to getObjectAsBytes: statusCode = ${result.response().sdkHttpResponse.statusCode()}"
                    )
                  )
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
    listObjectsSource(pid, listObjectsBatchSize)
      .log("list-objects")
      .mapConcat { res =>
        if (res.hasContents)
          res.contents.asScala.map { obj =>
            val key             = obj.key()
            val (pid, seqNr, _) = reverseKey(key)
            (pid.asString, seqNr.value)
          }.toVector
        else
          Vector.empty
      }
      .filter {
        case (pid, seqNr) =>
          pid == persistenceId && fromSeqNr <= seqNr
      }
      .fold(Vector.empty[(String, Long)])(_ :+ _)
      .map(_.sortWith(_._2 < _._2))
      .map {
        _.lastOption.fold(0L)(_._2)
      }
      .withAttributes(logLevels)
      .runWith(Sink.head)
  }

  private def listObjectsSource(persistenceId: PersistenceId, batchSize: Int) = {
    var builder = ListObjectsV2Request
      .builder()
      .bucket(resolveBucketName(persistenceId))
      .maxKeys(batchSize)
      .delimiter("/")
    builder = resolvePathPrefix(persistenceId).fold(builder)(builder.prefix)
    val req = builder.build()
    Source
      .unfoldAsync[(ListObjectsV2Request, FlowControl), ListObjectsV2Response](
        (req, Continue)
      ) {
        case (req, control) =>
          def retrieveNextBatch() =
            s3AsyncClient.listObjectsV2(req).flatMap { res =>
              if (res.sdkHttpResponse().isSuccessful) {
                if (res.nextContinuationToken() != null) {
                  val newReq = req.toBuilder.continuationToken(res.nextContinuationToken()).build()
                  Future.successful(Some((newReq, Continue), res))
                } else {
                  Future.successful(Some((req, Stop), res))
                }
              } else
                Future.failed(
                  new S3JournalException(
                    s"Failed to listObjectsV2: statusCode = ${res.sdkHttpResponse.statusCode()}"
                  )
                )
            }
          control match {
            case Stop     => Future.successful(None)
            case Continue => retrieveNextBatch()
          }
      }
  }

}
