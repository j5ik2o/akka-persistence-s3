package com.github.j5ik2o.akka.persistence.s3.serialization

import akka.persistence.SnapshotMetadata
import akka.persistence.serialization.Snapshot
import akka.serialization.{ AsyncSerializer, Serialization, Serializer }
import com.github.j5ik2o.akka.persistence.s3.base.metrics.MetricsReporter
import com.github.j5ik2o.akka.persistence.s3.base.model.{ PersistenceId, SequenceNumber }
import com.github.j5ik2o.akka.persistence.s3.base.trace.TraceReporter
import com.github.j5ik2o.akka.persistence.s3.snapshot.SnapshotRow

import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ByteArraySnapshotSerializer(
    serialization: Serialization,
    metricsReporter: Option[MetricsReporter],
    traceReporter: Option[TraceReporter]
) extends SnapshotSerializer[SnapshotRow] {

  private def serializerAsync: Future[Serializer] = {
    try Future.successful(serialization.serializerFor(classOf[Snapshot]))
    catch {
      case ex: Throwable =>
        Future.failed(ex)
    }
  }

  private def toBinaryAsync(serializer: Serializer, snapshot: Snapshot): Future[Array[Byte]] = {
    serializer match {
      case async: AsyncSerializer => async.toBinaryAsync(snapshot)
      case serializer =>
        try Future.successful(serializer.toBinary(snapshot))
        catch {
          case ex: Throwable =>
            Future.failed(ex)
        }
    }
  }

  private def fromBinaryAsync(serializer: Serializer, data: Array[Byte])(implicit
      ec: ExecutionContext
  ): Future[Snapshot] = {
    val future = serializer match {
      case async: AsyncSerializer => async.fromBinaryAsync(data, classOf[Snapshot].getName)
      case serializer =>
        try Future.successful(serializer.fromBinary(data, classOf[Snapshot]))
        catch {
          case ex: Throwable =>
            Future.failed(ex)
        }
    }
    future.map(_.asInstanceOf[Snapshot])
  }

  override def serialize(
      metadata: SnapshotMetadata,
      snapshot: Any
  )(implicit ec: ExecutionContext): Future[SnapshotRow] = {
    val pid        = PersistenceId(metadata.persistenceId)
    val context    = MetricsReporter.newContext(UUID.randomUUID(), pid)
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreSerializeSnapshot(context))

    def future = for {
      serializer <- serializerAsync
      serialized <- toBinaryAsync(serializer, Snapshot(snapshot))
    } yield SnapshotRow(
      PersistenceId(metadata.persistenceId),
      SequenceNumber(metadata.sequenceNr),
      metadata.timestamp,
      serialized
    )

    val traced = traceReporter.fold(future)(_.traceSnapshotStoreSerializeSnapshot(context)(future))

    traced.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterSnapshotStoreSerializeSnapshot(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorSnapshotStoreSerializeSnapshot(newContext, ex))
    }

    traced
  }

  override def deserialize(snapshotRow: SnapshotRow)(implicit ec: ExecutionContext): Future[(SnapshotMetadata, Any)] = {
    val context    = MetricsReporter.newContext(UUID.randomUUID(), snapshotRow.persistenceId)
    val newContext = metricsReporter.fold(context)(_.beforeSnapshotStoreDeserializeSnapshot(context))

    def future = for {
      serializer   <- serializerAsync
      deserialized <- fromBinaryAsync(serializer, snapshotRow.snapshot)
    } yield {
      val snapshotMetadata =
        SnapshotMetadata(snapshotRow.persistenceId.asString, snapshotRow.sequenceNumber.value, snapshotRow.created)
      (snapshotMetadata, deserialized.data)
    }

    val traced = traceReporter.fold(future)(_.traceSnapshotStoreDeserializeSnapshot(context)(future))

    traced.onComplete {
      case Success(_) =>
        metricsReporter.foreach(_.afterSnapshotStoreDeserializeSnapshot(newContext))
      case Failure(ex) =>
        metricsReporter.foreach(_.errorSnapshotStoreDeserializeSnapshot(newContext, ex))
    }

    traced
  }
}
