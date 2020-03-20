package com.github.j5ik2o.akka.persistence.s3.snapshot

class S3SnapshotException(message: String, cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull)
