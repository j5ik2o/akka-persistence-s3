package com.github.j5ik2o.akka.persistence.s3.resolver

import akka.persistence.SnapshotMetadata
import com.github.j5ik2o.akka.persistence.s3.base.{ PersistenceId, SequenceNumber }

trait JournalKeyConverter {
  def convertTo(persistenceId: PersistenceId, sequenceNumber: SequenceNumber, extensionName: String): Key

  def convertFrom(key: Key, extensionName: String): SnapshotMetadata
}

object JournalKeyConverter {}
