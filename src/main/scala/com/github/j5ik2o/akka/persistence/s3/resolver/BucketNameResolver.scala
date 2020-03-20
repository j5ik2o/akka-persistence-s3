package com.github.j5ik2o.akka.persistence.s3.resolver

trait BucketNameResolver {

  def resolve(persistenceId: PersistenceId): String

}

object BucketNameResolver {

  class PersistenceId extends BucketNameResolver {
    override def resolve(
      persistenceId: com.github.j5ik2o.akka.persistence.s3.resolver.PersistenceId
    ): String =
      "j5ik2o.akka-persistence-s3"
  }

}
