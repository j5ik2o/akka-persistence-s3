package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.model.SequenceNumber
import com.github.j5ik2o.akka.persistence.s3.base.resolver.Key
import com.typesafe.config.Config

import scala.util.matching.Regex

trait JournalMetadataKeyConverter {
  def convertTo(
      journalKeyMetadata: JournalMetadataKey,
      extensionName: String
  ): Key

  def convertFrom(key: Key, extensionName: String): JournalMetadataKey
}

object JournalMetadataKeyConverter {

  class PersistenceId(config: Config) extends JournalMetadataKeyConverter {
    override def convertTo(journalKeyMetadata: JournalMetadataKey, extensionName: String): Key = {
      val deleted =
        if (journalKeyMetadata.deleted) "inactive"
        else "active"
      val reversedSeqNr = "%019d".format(journalKeyMetadata.sequenceNumber.value).reverse
      s"${journalKeyMetadata.persistenceId.asString}/${reversedSeqNr}.$deleted.$extensionName"
    }

    override def convertFrom(key: Key, extensionName: String): JournalMetadataKey = {
      val pattern: Regex = ("""^(.+)/(\d+)\.(.+)\.""" + extensionName + "$").r
      key match {
        case pattern(
              persistenceId,
              sequenceNr,
              deleted
            ) =>
          JournalMetadataKey(
            com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId(persistenceId),
            SequenceNumber(sequenceNr.reverse.toLong),
            if (deleted == "inactive") true else false
          )
      }
    }
  }

}
