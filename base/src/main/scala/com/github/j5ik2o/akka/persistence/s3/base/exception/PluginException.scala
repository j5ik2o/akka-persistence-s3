package com.github.j5ik2o.akka.persistence.s3.base.exception

class PluginException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)
