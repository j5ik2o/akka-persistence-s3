package com.github.j5ik2o.akka.persistence.s3.resolver

import akka.persistence.SnapshotMetadata

import scala.util.matching.Regex

trait KeyConverter {

  def convertTo(snapshotMetadata: SnapshotMetadata, extensionName: String): Key

  def convertFrom(key: Key, extensionName: String): SnapshotMetadata

}

object KeyConverter {

  class PersistenceId extends KeyConverter {
    override def convertTo(snapshotMetadata: SnapshotMetadata,
                           extensionName: String): Key =
      s"${snapshotMetadata.persistenceId}/${snapshotMetadata.sequenceNr.toString.reverse}-${snapshotMetadata.timestamp}.$extensionName"

    override def convertFrom(key: Key,
                             extensionName: String): SnapshotMetadata = {
      val pattern: Regex = ("""^(.+)/(\d+)-(\d+)\.""" + extensionName + "$").r
      key match {
        case pattern(
            persistenceId: String,
            sequenceNr: String,
            timestamp: String
            ) =>
          SnapshotMetadata(
            persistenceId,
            sequenceNr.reverse.toLong,
            timestamp.toLong
          )
      }
    }
  }

}
