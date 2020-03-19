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
import com.github.j5ik2o.akka.persistence.s3.resolver.{
  BucketNameResolver,
  KeyResolver
}
import com.github.j5ik2o.akka.persistence.s3.utils.ClassUtil
import com.github.j5ik2o.reactive.aws.s3.S3AsyncClient
import com.typesafe.config.Config
import software.amazon.awssdk.services.s3.model.{
  DeleteObjectRequest,
  GetObjectRequest,
  ListObjectsRequest,
  PutObjectRequest
}
import software.amazon.awssdk.services.s3.{S3AsyncClient => JavaS3AsyncClient}
import com.github.j5ik2o.reactive.aws.s3.implicits._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import software.amazon.awssdk.core.async.{
  AsyncRequestBody,
  AsyncResponseTransformer
}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

class S3SnapshotStore(config: Config) extends SnapshotStore {
  implicit val system: ActorSystem = context.system
  import system.dispatcher
  private val javaS3Client = JavaS3AsyncClient.builder().build()
  private val s3AsyncClient = S3AsyncClient(javaS3Client)
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
        .map { response =>
          val snapshot = deserialize(response.asByteArray())
          Some(SelectedSnapshot(md, snapshot.data))
        } recoverWith {
        case NonFatal(e) =>
          log.error(e, s"Error loading snapshot [${md}]")
          load(metadata.init) // try older snapshot
      }
  }
  override def saveAsync(metadata: SnapshotMetadata,
                         snapshot: Any): Future[Unit] = {
    val (byteArray, size) = serialize(Snapshot(snapshot))
    val putObjectRequest = PutObjectRequest
      .builder()
      .contentLength(size.toLong)
      .bucket(bucketNameResolver.resolve(metadata.persistenceId))
      .key(keyResolver.resolve(metadata))
      .build()
    s3AsyncClient
      .putObject(putObjectRequest, AsyncRequestBody.fromBytes(byteArray))
      .flatMap { response =>
        if (response.sdkHttpResponse().isSuccessful)
          Future.successful(())
        else
          Future.failed(new Exception())
      }
  }
  def prefixFromPersistenceId(persisitenceId: String): String =
    s"$persisitenceId/"

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
        if (response.sdkHttpResponse().isSuccessful)
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
          Future.failed(new Exception)
      }

  }

  protected def deserialize(bytes: Array[Byte]): Snapshot =
    serialization
      .deserialize(bytes, classOf[Snapshot])
      .get

  private def serialize(snapshot: Snapshot): (Array[Byte], Int) = {
    val result = serialization.findSerializerFor(snapshot).toBinary(snapshot)
    (result, result.size)
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
        if (response.sdkHttpResponse().isSuccessful)
          Future.successful(())
        else
          Future.failed(new Exception())
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

}
