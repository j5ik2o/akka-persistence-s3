package com.github.j5ik2o.akka.persistence.s3.resolver

import akka.persistence.SnapshotMetadata

trait KeyResolver {

  def resolve(snapshotMetadata: SnapshotMetadata): String
  def parse(key: String): SnapshotMetadata
}

object KeyResolver {
  val extensionName = "snapshot"

  lazy val Pattern = ("""^(.+)/(\d+)-(\d+)\.""" + extensionName + "$").r

  class PersistenceId extends KeyResolver {
    override def resolve(snapshotMetadata: SnapshotMetadata): String =
      s"${snapshotMetadata.persistenceId}/${snapshotMetadata.sequenceNr.toString.reverse}-${snapshotMetadata.timestamp}.$extensionName"

    override def parse(key: String): SnapshotMetadata = key match {
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
