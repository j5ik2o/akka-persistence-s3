package com.github.j5ik2o.akka.persistence.s3.resolver

trait BucketNameResolver {

  def resolve(persistenceId: String): String

}

object BucketNameResolver {

  class PersistenceId extends BucketNameResolver {
    override def resolve(persistenceId: String): String =
      "akka-persistence-s3"
  }

}
