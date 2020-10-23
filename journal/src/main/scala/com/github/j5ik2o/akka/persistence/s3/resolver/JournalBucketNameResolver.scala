package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.typesafe.config.Config

trait JournalBucketNameResolver {

  def resolve(persistenceId: PersistenceId): String

}

object JournalBucketNameResolver {

  class PersistenceId(config: Config) extends JournalBucketNameResolver {
    override def resolve(
        persistenceId: com.github.j5ik2o.akka.persistence.s3.base.model.PersistenceId
    ): String = JournalPluginConfig.defaultBucketName
  }

}
