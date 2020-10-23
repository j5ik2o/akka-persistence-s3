package com.github.j5ik2o.akka.persistence.s3.resolver

import com.github.j5ik2o.akka.persistence.s3.base.PersistenceId
import com.typesafe.config.Config

trait PathPrefixResolver {

  def resolve(persistenceId: PersistenceId): Option[String]

}

object PathPrefixResolver {

  class PersistenceId(config: Config) extends PathPrefixResolver {
    override def resolve(
        persistenceId: com.github.j5ik2o.akka.persistence.s3.base.PersistenceId
    ): Option[String] = {
      Some(persistenceId + "/")
    }
  }

}
