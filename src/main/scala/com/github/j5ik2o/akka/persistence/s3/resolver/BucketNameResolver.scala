package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
import com.github.j5ik2o.akka.persistence.s3.config.SnapshotPluginConfig
import com.typesafe.config.Config

trait BucketNameResolver {

  def resolve(persistenceId: PersistenceId): String

}

object BucketNameResolver {

  class PersistenceId(config: Config) extends BucketNameResolver {
    override def resolve(
        persistenceId: com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
    ): String = SnapshotPluginConfig.defaultBucketName
  }

}
