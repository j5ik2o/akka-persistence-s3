package com.github.j5ik2o.akka.persistence.s3.base.utils

import org.slf4j.LoggerFactory
import org.slf4j.Logger

trait LoggingSupport {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

}
