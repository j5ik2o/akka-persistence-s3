package com.github.j5ik2o.akka.persistence.s3.util

import com.dimafeng.testcontainers.FixedHostPortGenericContainer
import org.testcontainers.containers.wait.strategy.Wait

trait LocalStackSpecSupport {
  protected val localStackImageVersion = "0.9.5"
  protected val localStackImageName    = s"localstack/localstack:$localStackImageVersion"
  protected def localStackPort: Int

  protected lazy val localStackContainer = new FixedHostPortGenericContainer(
    imageName = localStackImageName,
    exposedPorts = Seq(4572),
    env = Map("SERVICES" -> "s3"),
    command = Seq(),
    classpathResourceMapping = Seq(),
    waitStrategy = Some(Wait.forLogMessage(".*Ready\\.\n", 1)),
    exposedHostPort = localStackPort,
    exposedContainerPort = 4572
  )

}
