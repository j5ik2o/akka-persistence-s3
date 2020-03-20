package com.github.j5ik2o.akka.persistence.s3.resolver

trait PathPrefixResolver {

  def resolve(persistenceId: PersistenceId): Option[String]

}

object PathPrefixResolver {

  class PersistenceId extends PathPrefixResolver {
    override def resolve(
      persistenceId: com.github.j5ik2o.akka.persistence.s3.resolver.PersistenceId
    ): Option[String] =
      Some(persistenceId + "/")
  }

}
