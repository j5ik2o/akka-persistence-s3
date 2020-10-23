package com.github.j5ik2o.akka.persistence.s3.journal

final class S3JournalException(message: String, cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull)
