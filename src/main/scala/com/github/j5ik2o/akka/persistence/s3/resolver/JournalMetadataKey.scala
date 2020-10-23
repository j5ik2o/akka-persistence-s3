package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.{ PersistenceId, SequenceNumber }

final case class JournalMetadataKey(
    persistenceId: PersistenceId,
    sequenceNumber: SequenceNumber,
    deleted: Boolean = false
)
