package com.github.j5ik2o.akka.persistence.s3.snapshot

import akka.actor.ActorSystem
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{
  SelectedSnapshot,
  SnapshotMetadata,
  SnapshotSelectionCriteria
}
import akka.serialization.SerializationExtension
import com.github.j5ik2o.akka.persistence.s3.config.S3ClientConfig
import com.github.j5ik2o.akka.persistence.s3.resolver.{
  BucketNameResolver,
  KeyResolver
}
import com.github.j5ik2o.akka.persistence.s3.utils.{
  ClassUtil,
  HttpClientBuilderUtils,
  S3ClientBuilderUtils
}
import com.github.j5ik2o.reactive.aws.s3.S3AsyncClient
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import software.amazon.awssdk.core.async.{
  AsyncRequestBody,
  AsyncResponseTransformer
}
import software.amazon.awssdk.services.s3.model.{
  DeleteObjectRequest,
  GetObjectRequest,
  ListObjectsRequest,
  PutObjectRequest
}

import scala.collection.immutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class S3SnapshotStore(config: Config) extends SnapshotStore {
  implicit val system: ActorSystem = context.system
  import system.dispatcher

  private val s3ClientConfig: S3ClientConfig =
    S3ClientConfig.fromConfig(config.getConfig("s3-client"))

  private val httpClientBuilder = HttpClientBuilderUtils.setup(s3ClientConfig)
  private val javaS3ClientBuilder =
    S3ClientBuilderUtils.setup(s3ClientConfig, httpClientBuilder.build())

  private val s3AsyncClient = S3AsyncClient(javaS3ClientBuilder.build())
  private val serialization = SerializationExtension(system)

  protected val bucketNameResolver: BucketNameResolver =
    ClassUtil.create(
      classOf[BucketNameResolver],
      config
        .as[String]("bucket-name-resolver-class-name")
    )

  protected val keyResolver: KeyResolver =
    ClassUtil.create(
      classOf[KeyResolver],
      config
        .as[String]("key-resolver-class-name")
    )

  val maxLoadAttempts = 1

  override def loadAsync(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] =
    snapshotMetadatas(persistenceId, criteria)
      .map(_.sorted.takeRight(maxLoadAttempts))
      .flatMap(load)

  override def saveAsync(metadata: SnapshotMetadata,
                         snapshot: Any): Future[Unit] = {
    val (byteArray, size) = serialize(Snapshot(snapshot))
    log.info("saveAsync:byteArray.size = {}", byteArray.size)
    log.info("saveAsync:size = {}", size)
    val putObjectRequest = PutObjectRequest
      .builder()
      .contentLength(size.toLong)
      .bucket(bucketNameResolver.resolve(metadata.persistenceId))
      .key(keyResolver.resolve(metadata))
      .build()
    s3AsyncClient
      .putObject(putObjectRequest, AsyncRequestBody.fromBytes(byteArray))
      .flatMap { response =>
        val sdkHttpResponse = response.sdkHttpResponse
        log.info(s"saveAsync:response = $response")
        if (response.sdkHttpResponse().isSuccessful)
          Future.successful(())
        else
          Future.failed(
            new S3SnapshotException(
              s"Failed to PutObjectRequest: statusCode = ${sdkHttpResponse.statusCode()}"
            )
          )
      }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    if (metadata.timestamp == 0L)
      deleteAsync(
        metadata.persistenceId,
        SnapshotSelectionCriteria(
          metadata.sequenceNr,
          Long.MaxValue,
          metadata.sequenceNr,
          Long.MinValue
        )
      )
    else {
      val request = DeleteObjectRequest
        .builder()
        .bucket(bucketNameResolver.resolve(metadata.persistenceId))
        .key(keyResolver.resolve(metadata))
        .build()
      s3AsyncClient.deleteObject(request).flatMap { response =>
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
    }
  }

  override def deleteAsync(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[Unit] = {
    val metadatas = snapshotMetadatas(persistenceId, criteria)
    metadatas.map(list => Future.sequence(list.map(deleteAsync)))
  }

  private def load(
    metadata: immutable.Seq[SnapshotMetadata]
  ): Future[Option[SelectedSnapshot]] = metadata.lastOption match {
    case None => Future.successful(None)
    case Some(md) =>
      val request = GetObjectRequest
        .builder()
        .bucket(bucketNameResolver.resolve(md.persistenceId))
        .key(keyResolver.resolve(md))
        .build()
      s3AsyncClient
        .getObject(request, AsyncResponseTransformer.toBytes())
        .map { responseBytes =>
          if (responseBytes.response().sdkHttpResponse().isSuccessful) {
            log.info(s"load:response = ${responseBytes.response()}")
            log.info(s"load:responseBytes = $responseBytes")
            log.info(
              s"load:responseBytes.length = ${responseBytes.asByteArray().length}"
            )
            val snapshot = deserialize(responseBytes.asByteArray())
            Some(SelectedSnapshot(md, snapshot.data))
          } else {
            log.warning("load: result = None")
            None
          }
        } recoverWith {
        case NonFatal(e) =>
          log.error(e, s"Error loading snapshot [${md}]")
          load(metadata.init) // try older snapshot
      }
  }

  private def snapshotMetadatas(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[List[SnapshotMetadata]] = {
    val request =
      ListObjectsRequest
        .builder()
        .bucket(bucketNameResolver.resolve(persistenceId))
        .prefix(prefixFromPersistenceId(persistenceId))
        .delimiter("/")
        .build()
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
              .map { s =>
                keyResolver.parse(s.key())
              }
              .filter { m =>
                m.sequenceNr >= criteria.minSequenceNr &&
                m.sequenceNr <= criteria.maxSequenceNr &&
                m.timestamp >= criteria.minTimestamp &&
                m.timestamp <= criteria.maxTimestamp
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

  def prefixFromPersistenceId(persisitenceId: String): String =
    s"$persisitenceId/"

  protected def deserialize(bytes: Array[Byte]): Snapshot =
    serialization
      .deserialize(bytes, classOf[Snapshot])
      .get

  private def serialize(snapshot: Snapshot): (Array[Byte], Int) = {
    val serialized =
      serialization.findSerializerFor(snapshot).toBinary(snapshot)
    (serialized, serialized.length)
  }

}
