package com.github.j5ik2o.akka.persistence.s3.snapshot

import java.util.UUID

import akka.actor.{ ActorSystem, DynamicAccess, ExtendedActorSystem }
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.serialization.{ Serialization, SerializationExtension }
import com.github.j5ik2o.akka.persistence.s3.base.config.S3ClientConfig
import com.github.j5ik2o.akka.persistence.s3.base.metrics.{ MetricsReporter, MetricsReporterProvider }
import com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
import com.github.j5ik2o.akka.persistence.s3.base.resolver.PathPrefixResolver
import com.github.j5ik2o.akka.persistence.s3.base.utils.{ HttpClientBuilderUtils, S3ClientBuilderUtils }
import com.github.j5ik2o.akka.persistence.s3.config.SnapshotPluginConfig
import com.github.j5ik2o.akka.persistence.s3.resolver.{ SnapshotBucketNameResolver, SnapshotMetadataKeyConverter }
import com.github.j5ik2o.reactive.aws.s3.S3AsyncClient
import com.typesafe.config.Config
import software.amazon.awssdk.core.async.{ AsyncRequestBody, AsyncResponseTransformer }
import software.amazon.awssdk.services.s3.model._

import scala.collection.immutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

class S3SnapshotStore(config: Config) extends SnapshotStore {
  implicit val system: ActorSystem = context.system
  import system.dispatcher

  private val pluginConfig: SnapshotPluginConfig  = SnapshotPluginConfig.fromConfig(config)
  private val bucketNameResolverClassName: String = pluginConfig.bucketNameResolverClassName
  private val keyConverterClassName: String       = pluginConfig.keyConverterClassName
  private val pathPrefixResolverClassName: String = pluginConfig.pathPrefixResolverClassName
  private val extensionName: String               = pluginConfig.extensionName
  private val maxLoadAttempts: Int                = pluginConfig.maxLoadAttempts
  private val s3ClientConfig: S3ClientConfig      = pluginConfig.clientConfig

  private val httpClientBuilder = HttpClientBuilderUtils.setup(s3ClientConfig)
  private val javaS3ClientBuilder =
    S3ClientBuilderUtils.setup(s3ClientConfig, httpClientBuilder.build())
  private val s3AsyncClient = S3AsyncClient(javaS3ClientBuilder.build())

  private val extendedSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  private val dynamicAccess: DynamicAccess        = extendedSystem.dynamicAccess

  protected val metricsReporter: Option[MetricsReporter] = {
    val metricsReporterProvider = MetricsReporterProvider.create(dynamicAccess, pluginConfig)
    metricsReporterProvider.create
  }

  protected val bucketNameResolver: SnapshotBucketNameResolver = {
    dynamicAccess
      .createInstanceFor[SnapshotBucketNameResolver](
        bucketNameResolverClassName,
        immutable.Seq(classOf[Config] -> config)
      )
      .getOrElse(throw new ClassNotFoundException(bucketNameResolverClassName))
  }

  protected val keyConverter: SnapshotMetadataKeyConverter = {
    dynamicAccess
      .createInstanceFor[SnapshotMetadataKeyConverter](keyConverterClassName, immutable.Seq(classOf[Config] -> config))
      .getOrElse(throw new ClassNotFoundException(keyConverterClassName))
  }

  protected val pathPrefixResolver: PathPrefixResolver = {
    dynamicAccess
      .createInstanceFor[PathPrefixResolver](pathPrefixResolverClassName, immutable.Seq(classOf[Config] -> config))
      .getOrElse(throw new ClassNotFoundException(pathPrefixResolverClassName))
  }

  private val serialization: Serialization = SerializationExtension(system)

  private def resolvePathPrefix(
      persistenceId: PersistenceId
  ): Option[String] = {
    pluginConfig.pathPrefix.orElse(pathPrefixResolver.resolve(persistenceId))
  }

  private def resolveBucketName(snapshotMetadata: SnapshotMetadata) = {
    pluginConfig.bucketName
      .map(_.stripPrefix("/"))
      .getOrElse(bucketNameResolver.resolve(PersistenceId(snapshotMetadata.persistenceId)))
  }

  private def convertToKey(snapshotMetadata: SnapshotMetadata) = {
    keyConverter.convertTo(snapshotMetadata, extensionName)
  }

  private def convertToSnapshotMetadata(s: S3Object) = {
    keyConverter.convertFrom(s.key(), extensionName)
  }

  protected def deserialize(metadata: SnapshotMetadata, bytes: Array[Byte]): Snapshot = {
    val context    = MetricsReporter.newContext(UUID.randomUUID(), PersistenceId(metadata.persistenceId))
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreDeserializeSnapshot(context))
    try serialization
      .deserialize(bytes, classOf[Snapshot])
      .get
    catch {
      case ex: Throwable =>
        metricsReporter.foreach(_.errorSnapshotStoreDeserializeSnapshot(newContext, ex))
        throw ex
    } finally {
      metricsReporter.foreach(_.afterSnapshotStoreDeserializeSnapshot(newContext))
    }
  }

  private def serialize(metadata: SnapshotMetadata, snapshot: Snapshot): (Array[Byte], Int) = {
    val pid        = PersistenceId(metadata.persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreSerializeSnapshot(context))
    val serialized =
      try serialization.findSerializerFor(snapshot).toBinary(snapshot)
      catch {
        case ex: Throwable =>
          metricsReporter.foreach(_.errorSnapshotStoreSerializeSnapshot(newContext, ex))
          throw ex
      } finally {
        metricsReporter.foreach(_.afterSnapshotStoreSerializeSnapshot(newContext))
      }
    (serialized, serialized.length)
  }

  override def loadAsync(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {
    val pid        = PersistenceId(persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreLoadAsync(context))
    val future = snapshotMetadatas(persistenceId, criteria)
      .map(_.sorted.takeRight(maxLoadAttempts))
      .flatMap(load)
    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterSnapshotStoreLoadAsync(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorSnapshotStoreLoadAsync(newContext, ex))
    }
    future
  }

  override def saveAsync(snapshotMetadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val pid               = PersistenceId(snapshotMetadata.persistenceId)
    val context           = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext        = metricsReporter.fold(context)(_.beforeSnapshotStoreSaveAsync(context))
    val (byteArray, size) = serialize(snapshotMetadata, Snapshot(snapshot))
    val putObjectRequest = PutObjectRequest
      .builder()
      .contentLength(size.toLong)
      .bucket(resolveBucketName(snapshotMetadata))
      .key(convertToKey(snapshotMetadata))
      .build()
    val future = s3AsyncClient
      .putObject(putObjectRequest, AsyncRequestBody.fromBytes(byteArray))
      .flatMap { response =>
        val sdkHttpResponse = response.sdkHttpResponse
        if (response.sdkHttpResponse().isSuccessful)
          Future.successful(())
        else
          Future.failed(
            new S3SnapshotException(
              s"Failed to PutObjectRequest: statusCode = ${sdkHttpResponse.statusCode()}"
            )
          )
      }
    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterSnapshotStoreSaveAsync(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorSnapshotStoreSaveAsync(newContext, ex))
    }
    future
  }

  override def deleteAsync(snapshotMetadata: SnapshotMetadata): Future[Unit] = {
    if (snapshotMetadata.timestamp == 0L)
      deleteAsync(
        snapshotMetadata.persistenceId,
        SnapshotSelectionCriteria(
          snapshotMetadata.sequenceNr,
          Long.MaxValue,
          snapshotMetadata.sequenceNr,
          Long.MinValue
        )
      )
    else {
      val pid        = PersistenceId(snapshotMetadata.persistenceId)
      val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
      val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreDeleteAsync(context))
      val request = DeleteObjectRequest
        .builder()
        .bucket(resolveBucketName(snapshotMetadata))
        .key(convertToKey(snapshotMetadata))
        .build()
      val future = s3AsyncClient.deleteObject(request).flatMap { response =>
        val sdkHttpResponse = response.sdkHttpResponse
        if (response.sdkHttpResponse().isSuccessful)
          Future.successful(())
        else
          Future.failed(
            new S3SnapshotException(
              s"Failed to DeleteObjectRequest: statusCode = ${sdkHttpResponse.statusCode()}"
            )
          )
      }
      future.onComplete {
        case Success(_) =>
          metricsReporter.foreach(_.afterSnapshotStoreDeleteAsync(newContext))
        case Failure(ex) =>
          metricsReporter.foreach(_.errorSnapshotStoreDeleteAsync(newContext, ex))
      }
      future
    }
  }

  override def deleteAsync(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria
  ): Future[Unit] = {
    val pid        = PersistenceId(persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreDeleteWithCriteriaAsync(context))
    val metadatas  = snapshotMetadatas(persistenceId, criteria)
    val future     = metadatas.flatMap(list => Future.sequence(list.map(deleteAsync))).map(_ => ())
    future.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterSnapshotStoreDeleteWithCriteriaAsync(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorSnapshotStoreDeleteWithCriteriaAsync(newContext, ex))
    }
    future
  }

  private def load(
      metadata: immutable.Seq[SnapshotMetadata]
  ): Future[Option[SelectedSnapshot]] =
    metadata.lastOption match {
      case None => Future.successful(None)
      case Some(snapshotMetadata) =>
        val request = GetObjectRequest
          .builder()
          .bucket(resolveBucketName(snapshotMetadata))
          .key(convertToKey(snapshotMetadata))
          .build()
        s3AsyncClient
          .getObject(request, AsyncResponseTransformer.toBytes())
          .map { responseBytes =>
            if (responseBytes.response().sdkHttpResponse().isSuccessful) {
              val snapshot = deserialize(snapshotMetadata, responseBytes.asByteArray())
              Some(SelectedSnapshot(snapshotMetadata, snapshot.data))
            } else None
          } recoverWith {
          case NonFatal(e) =>
            log.error(e, s"Error loading snapshot [${snapshotMetadata}]")
            load(metadata.init) // try older snapshot
        }
    }

  private def snapshotMetadatas(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria
  ): Future[List[SnapshotMetadata]] = {
    val pid = PersistenceId(persistenceId)
    var builder = ListObjectsRequest
      .builder()
      .bucket(bucketNameResolver.resolve(pid))
      .delimiter("/")
    builder = resolvePathPrefix(pid).fold(builder)(builder.prefix)
    val request = builder.build()
    s3AsyncClient
      .listObjects(request)
      .flatMap { response =>
        val sdkHttpResponse = response.sdkHttpResponse
        if (sdkHttpResponse.isSuccessful)
          Future.successful(
            response
              .contents()
              .asScala
              .toList
              .map(convertToSnapshotMetadata)
              .filter { snapshotMetadata =>
                snapshotMetadata.sequenceNr >= criteria.minSequenceNr &&
                snapshotMetadata.sequenceNr <= criteria.maxSequenceNr &&
                snapshotMetadata.timestamp >= criteria.minTimestamp &&
                snapshotMetadata.timestamp <= criteria.maxTimestamp
              }
          )
        else
          Future.failed(
            new S3SnapshotException(
              s"Failed to ListObjectsRequest: statusCode = ${sdkHttpResponse.statusCode()}"
            )
          )
      }

  }

}
