package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
import com.github.j5ik2o.akka.persistence.s3.config.SnapshotPluginConfig
import com.typesafe.config.Config

import scala.annotation.unused

trait SnapshotBucketNameResolver {

  def resolve(persistenceId: PersistenceId): String

}

object SnapshotBucketNameResolver {

  class PersistenceId(@unused config: Config) extends SnapshotBucketNameResolver {
    override def resolve(
        persistenceId: com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
    ): String = SnapshotPluginConfig.defaultBucketName
  }

}
