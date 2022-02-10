package com.github.j5ik2o.akka.persistence.s3.snapshot

import com.github.j5ik2o.akka.persistence.s3.base.model.{ PersistenceId, SequenceNumber }

final case class SnapshotRow(
    persistenceId: PersistenceId,
    sequenceNumber: SequenceNumber,
    created: Long,
    snapshot: Array[Byte]
) {
  def length: Int = snapshot.length
}
