package com.github.j5ik2o.akka.persistence.s3.base.config

import com.github.j5ik2o.akka.persistence.s3.base.provider.AwsCredentialsProviderProvider
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration

object S3ClientConfig {

  def fromConfig(rootConfig: Config): S3ClientConfig = {
    val result = S3ClientConfig(
      accessKeyId = rootConfig.getAs[String]("access-key-id"),
      secretAccessKey = rootConfig.getAs[String]("secret-access-key"),
      endpoint = rootConfig.getAs[String]("endpoint"),
      region = rootConfig.getAs[String]("region"),
      awsCredentialsProviderProviderClassName = rootConfig.getOrElse[String](
        "aws-credentials-provider-provider-class-name",
        classOf[AwsCredentialsProviderProvider.Default].getName
      ),
      awsCredentialsProviderClassName = rootConfig.getAs[String]("aws-credentials-provider-class-name"),
      maxConcurrency = rootConfig.getAs[Int]("max-concurrency"),
      maxPendingConnectionAcquires = rootConfig.getAs[Int]("max-pending-connection-acquires"),
      readTimeout = rootConfig.getAs[FiniteDuration]("read-timeout"),
      writeTimeout = rootConfig.getAs[FiniteDuration]("write-timeout"),
      connectionTimeout = rootConfig.getAs[FiniteDuration]("connection-timeout"),
      connectionAcquisitionTimeout = rootConfig.getAs[FiniteDuration]("connection-acquisition-timeout"),
      connectionTimeToLive = rootConfig.getAs[FiniteDuration]("connection-time-to-live"),
      maxIdleConnectionTimeout = rootConfig.getAs[FiniteDuration]("max-idle-connection-timeout"),
      useConnectionReaper = rootConfig.getAs[Boolean]("use-connection-reaper"),
      threadsOfEventLoopGroup = rootConfig.getAs[Int]("threads-of-event-loop-group"),
      useHttp2 = rootConfig.getAs[Boolean]("use-http2"),
      http2MaxStreams = rootConfig.getAs[Long]("http2-max-streams"),
      http2InitialWindowSize = rootConfig.getAs[Int]("http2-initial-window-size"),
      http2HealthCheckPingPeriod = rootConfig.getAs[FiniteDuration]("http2-health-check-ping-period"),
      s3OptionConfig = rootConfig
        .getAs[Config]("s3-options")
        .map(S3ClientOptionsConfig.fromConfig)
    )
    result
  }
}

object S3ClientOptionsConfig {

  def fromConfig(rootConfig: Config): S3ClientOptionsConfig = {
    S3ClientOptionsConfig(
      dualstackEnabled = rootConfig.getAs[Boolean]("dualstack-enabled"),
      accelerateModeEnabled = rootConfig.getAs[Boolean]("accelerate-mode-enabled"),
      pathStyleAccessEnabled = rootConfig.getAs[Boolean]("path-style-access-enabled"),
      checksumValidationEnabled = rootConfig.getAs[Boolean]("checksum-validation-enabled"),
      chunkedEncodingEnabled = rootConfig.getAs[Boolean]("chunked-encoding-enabled"),
      useArnRegionEnabled = rootConfig.getAs[Boolean]("use-arn-region-enabled")
    )
  }

}

final case class S3ClientOptionsConfig(
    dualstackEnabled: Option[Boolean],
    accelerateModeEnabled: Option[Boolean],
    pathStyleAccessEnabled: Option[Boolean],
    checksumValidationEnabled: Option[Boolean],
    chunkedEncodingEnabled: Option[Boolean],
    useArnRegionEnabled: Option[Boolean]
)

final case class S3ClientConfig(
    accessKeyId: Option[String],
    secretAccessKey: Option[String],
    endpoint: Option[String],
    region: Option[String],
    awsCredentialsProviderProviderClassName: String,
    awsCredentialsProviderClassName: Option[String],
    maxConcurrency: Option[Int],
    maxPendingConnectionAcquires: Option[Int],
    readTimeout: Option[FiniteDuration],
    writeTimeout: Option[FiniteDuration],
    connectionTimeout: Option[FiniteDuration],
    connectionAcquisitionTimeout: Option[FiniteDuration],
    connectionTimeToLive: Option[FiniteDuration],
    maxIdleConnectionTimeout: Option[FiniteDuration],
    useConnectionReaper: Option[Boolean],
    threadsOfEventLoopGroup: Option[Int],
    useHttp2: Option[Boolean],
    http2MaxStreams: Option[Long],
    http2InitialWindowSize: Option[Int],
    http2HealthCheckPingPeriod: Option[FiniteDuration],
    s3OptionConfig: Option[S3ClientOptionsConfig]
)
