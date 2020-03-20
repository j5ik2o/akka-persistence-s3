package com.github.j5ik2o.akka.persistence.s3.resolver

import akka.persistence.SnapshotMetadata

trait KeyConverter {

  def convertTo(snapshotMetadata: SnapshotMetadata): Key

  def convertFrom(key: Key): SnapshotMetadata

}

object KeyConverter {
  val extensionName = "snapshot"

  lazy val Pattern = ("""^(.+)/(\d+)-(\d+)\.""" + extensionName + "$").r

  class PersistenceId extends KeyConverter {
    override def convertTo(snapshotMetadata: SnapshotMetadata): Key =
      s"${snapshotMetadata.persistenceId}/${snapshotMetadata.sequenceNr.toString.reverse}-${snapshotMetadata.timestamp}.$extensionName"

    override def convertFrom(key: Key): SnapshotMetadata = key match {
      case Pattern(
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
