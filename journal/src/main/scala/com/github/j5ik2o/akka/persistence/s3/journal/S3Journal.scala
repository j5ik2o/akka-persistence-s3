package com.github.j5ik2o.akka.persistence.s3.journal

import akka.actor.{ ActorSystem, DynamicAccess, ExtendedActorSystem }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Attributes
import akka.stream.scaladsl.{ Sink, Source }
import com.github.j5ik2o.akka.persistence.s3.base.config.S3ClientConfig
import com.github.j5ik2o.akka.persistence.s3.base.metrics.{ MetricsReporter, MetricsReporterProvider }
import com.github.j5ik2o.akka.persistence.s3.base.model.{ PersistenceId, SequenceNumber }
import com.github.j5ik2o.akka.persistence.s3.base.resolver.{ Key, PathPrefixResolver }
import com.github.j5ik2o.akka.persistence.s3.base.utils.{ HttpClientBuilderUtils, S3ClientBuilderUtils }
import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.github.j5ik2o.akka.persistence.s3.resolver.{
  JournalBucketNameResolver,
  JournalMetadataKey,
  JournalMetadataKeyConverter
}
import com.github.j5ik2o.akka.persistence.s3.serialization.{ ByteArrayJournalSerializer, FlowPersistentReprSerializer }
import com.typesafe.config.Config
import software.amazon.awssdk.core.async.{ AsyncRequestBody, AsyncResponseTransformer }
import software.amazon.awssdk.services.s3.model._

import java.util.UUID
import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

class S3Journal(config: Config) extends AsyncWriteJournal {
  implicit val system: ActorSystem = context.system

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

  private val extendedSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  private val dynamicAccess: DynamicAccess        = extendedSystem.dynamicAccess

  private val javaS3ClientBuilder =
    S3ClientBuilderUtils.setup(dynamicAccess, pluginConfig, httpClientBuilder.build())
  private val s3AsyncClient = javaS3ClientBuilder.build()

  protected val metricsReporter: Option[MetricsReporter] = {
    val metricsReporterProvider = MetricsReporterProvider.create(dynamicAccess, pluginConfig)
    metricsReporterProvider.create
  }

  protected val bucketNameResolver: JournalBucketNameResolver = {
    dynamicAccess
      .createInstanceFor[JournalBucketNameResolver](
        bucketNameResolverClassName,
        immutable.Seq(classOf[Config] -> config)
      )
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
    new ByteArrayJournalSerializer(serialization, pluginConfig.tagSeparator, metricsReporter)

  protected val logLevels: Attributes = Attributes.logLevels(
    onElement = Attributes.LogLevels.Debug,
    onFailure = Attributes.LogLevels.Error,
    onFinish = Attributes.LogLevels.Debug
  )

  override def asyncWriteMessages(atomicWrites: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    val persistenceId = atomicWrites.head.persistenceId
    val pid           = PersistenceId(persistenceId)
    val context       = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext    = metricsReporter.fold(context)(_.beforeJournalAsyncWriteMessages(context))

    implicit val ec: ExecutionContext = system.dispatcher

    val serializedFutures = serializer.serialize(atomicWrites)
    val rowsToWriteFutures = serializedFutures.map { serializeFuture =>
      serializeFuture.recoverWith { case _ =>
        Future.successful(Seq.empty)
      }
    }

    def resultWhenWriteComplete(implicit ec: ExecutionContext): Future[Vector[Future[Unit]]] = {
      Future.sequence(serializedFutures).map(_ => true).recover { case _ => false }.map { b =>
        if (b) {
          Vector.empty
        } else {
          serializedFutures.toVector.map(s => s.map(_ => ()))
        }
      }
    }

    def putObject(journalRow: JournalRow)(implicit ec: ExecutionContext): Future[PutObjectResponse] = {
      val key = resolveKey(journalRow.persistenceId, journalRow.sequenceNumber)
      val req = PutObjectRequest
        .builder()
        .bucket(resolveBucketName(journalRow.persistenceId))
        .key(key)
        .build()
      s3AsyncClient.putObject(req, AsyncRequestBody.fromBytes(journalRow.message)).toScala.flatMap { res =>
        if (res.sdkHttpResponse().isSuccessful)
          Future.successful(res)
        else
          Future.failed(
            new S3JournalException(
              s"Failed to putObject: statusCode = ${res.sdkHttpResponse.statusCode()}"
            )
          )
      }
    }

    def execute(implicit ec: ExecutionContext) = Future
      .traverse(rowsToWriteFutures) { rowsToWriteFuture =>
        rowsToWriteFuture.flatMap { rowsToWrite =>
          rowsToWrite.foldLeft(Future.successful(Vector.empty[PutObjectResponse])) { case (result, journalRow) =>
            for {
              r <- result
              e <- putObject(journalRow)
            } yield r :+ e
          }
        }
      }.flatMap { _ =>
        resultWhenWriteComplete.flatMap { f =>
          f.foldLeft(Future(Vector.empty[Try[Unit]])) { (result, element) =>
            (for {
              r <- result
              e <- element
            } yield r :+ Success(e))
              .recoverWith { case ex =>
                result.map { r => r :+ Failure(ex) }
              }
          }
        }
      }

    val future = execute

    future.onComplete { result: Try[Seq[Try[Unit]]] =>
      result match {
        case Success(_) =>
          metricsReporter.foreach(_.afterJournalAsyncWriteMessages(newContext))
        case Failure(ex) =>
          metricsReporter.foreach(_.errorJournalAsyncWriteMessages(newContext, ex))
      }
    }

    future
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher

    val pid        = PersistenceId(persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeJournalAsyncDeleteMessagesTo(context))

    def deleteObject(pid: PersistenceId, obj: S3Object)(implicit ec: ExecutionContext): Future[DeleteObjectResponse] = {
      val req = DeleteObjectRequest.builder().bucket(resolveBucketName(pid)).key(obj.key()).build()
      s3AsyncClient.deleteObject(req).toScala.flatMap { res =>
        if (res.sdkHttpResponse().isSuccessful)
          Future.successful(res)
        else
          Future.failed(
            new S3JournalException(
              s"Failed to deleteObject: statusCode = ${res.sdkHttpResponse.statusCode()}"
            )
          )
      }
    }

    def copyObject(pid: PersistenceId, obj: S3Object, seqNr: Long)(implicit
        ec: ExecutionContext
    ): Future[CopyObjectResponse] = {
      val req = CopyObjectRequest
        .builder()
        .sourceBucket(resolveBucketName(pid))
        .sourceKey(obj.key())
        .destinationBucket(resolveBucketName(pid))
        .destinationKey(resolveKey(pid, SequenceNumber(seqNr), deleted = true))
        .build()
      s3AsyncClient.copyObject(req).toScala.flatMap { res =>
        if (res.sdkHttpResponse().isSuccessful) {
          Future.successful(res)
        } else
          Future.failed(
            new S3JournalException(
              s"Failed to copyObject: statusCode = ${res.sdkHttpResponse.statusCode()}"
            )
          )
      }
    }

    def execute(implicit ec: ExecutionContext): Future[Unit] = listObjectsSource(pid, listObjectsBatchSize)
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
      .mapAsync(1) { case (obj, _, seqNr) =>
        for {
          _   <- copyObject(pid, obj, seqNr)
          res <- deleteObject(pid, obj)
        } yield res
      }
      .withAttributes(logLevels)
      .runWith(Sink.ignore)
      .map(_ => ())

    val future = execute

    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterJournalAsyncDeleteMessagesTo(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorJournalAsyncDeleteMessagesTo(newContext, ex))
    }
    future
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit
  ): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher

    val pid        = PersistenceId(persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeJournalAsyncReplayMessages(context))

    def getObject(pid: PersistenceId, key: Key)(implicit ec: ExecutionContext): Future[Array[Byte]] = {
      val req = GetObjectRequest
        .builder()
        .bucket(resolveBucketName(pid))
        .key(key)
        .build()
      s3AsyncClient.getObject(req, AsyncResponseTransformer.toBytes[GetObjectResponse]).toScala.flatMap { result =>
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

    val fromSeqNr = Math.max(1, fromSequenceNr)

    val source =
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
          .filter { case (_, deleted, pid, seqNr) =>
            !deleted && pid == persistenceId && fromSeqNr <= seqNr && seqNr <= toSequenceNr
          }
          .log("element")
          .mapAsync(1) { case (key, _, _, _) =>
            getObject(pid, key)
          }
          .map { bytes =>
            serialization
              .deserialize(bytes, classOf[PersistentRepr])
          }
          .take(max)
      }

    val future = source
      .withAttributes(logLevels)
      .runForeach { result =>
        result.foreach(recoveryCallback)
      }
      .map(_ => ())

    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterJournalAsyncReplayMessages(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorJournalAsyncReplayMessages(newContext, ex))
    }

    future
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    implicit val ec: ExecutionContext = system.dispatcher

    val pid        = PersistenceId(persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeJournalAsyncReadHighestSequenceNr(context))

    val fromSeqNr = Math.max(1, fromSequenceNr)
    val future = listObjectsSource(pid, listObjectsBatchSize)
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
      .filter { case (pid, seqNr) =>
        pid == persistenceId && fromSeqNr <= seqNr
      }
      .fold(Vector.empty[(String, Long)])(_ :+ _)
      .map(_.sortWith(_._2 < _._2))
      .map(_.lastOption.fold(0L)(_._2))
      .withAttributes(logLevels)
      .runWith(Sink.head)

    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterJournalAsyncReadHighestSequenceNr(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorJournalAsyncReadHighestSequenceNr(newContext, ex))
    }

    future
  }

  private def listObjectsSource(persistenceId: PersistenceId, batchSize: Int)(implicit ec: ExecutionContext) = {
    var builder = ListObjectsV2Request
      .builder()
      .bucket(resolveBucketName(persistenceId))
      .maxKeys(batchSize)
      .delimiter("/")
    builder = resolvePathPrefix(persistenceId).fold(builder)(builder.prefix)
    val request = builder.build()
    Source
      .unfoldAsync[(ListObjectsV2Request, FlowControl), ListObjectsV2Response](
        (request, Continue)
      ) { case (request, control) =>
        def retrieveNextBatch(): Future[Some[((ListObjectsV2Request, FlowControl), ListObjectsV2Response)]] =
          s3AsyncClient.listObjectsV2(request).toScala.flatMap { res =>
            if (res.sdkHttpResponse().isSuccessful) {
              if (res.nextContinuationToken() != null) {
                val newReq = request.toBuilder.continuationToken(res.nextContinuationToken()).build()
                Future.successful(Some((newReq, Continue), res))
              } else {
                Future.successful(Some((request, Stop), res))
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
