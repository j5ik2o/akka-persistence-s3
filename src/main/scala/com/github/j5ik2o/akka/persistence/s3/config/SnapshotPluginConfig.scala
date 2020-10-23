package com.github.j5ik2o.akka.persistence.s3.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

object SnapshotPluginConfig {

  val defaultBucketName = "j5ik2o.akka-persistence-s3-snapshot"

  def fromConfig(rootConfig: Config): SnapshotPluginConfig = {
    SnapshotPluginConfig(
      bucketName = rootConfig.getAs[String]("bucket-name"),
      bucketNameResolverClassName = rootConfig.as[String]("bucket-name-resolver-class-name"),
      keyConverterClassName = rootConfig.as[String]("key-converter-class-name"),
      pathPrefix = rootConfig.getAs[String]("path-prefix"),
      pathPrefixResolverClassName = rootConfig.as[String]("path-prefix-resolver-class-name"),
      extensionName = rootConfig.as[String]("extension-name"),
      maxLoadAttempts = rootConfig.as[Int]("max-load-attempts"),
      clientConfig = S3ClientConfig.fromConfig(rootConfig.getConfig("s3-client"))
    )
  }

}

final case class SnapshotPluginConfig(
    bucketName: Option[String],
    bucketNameResolverClassName: String,
    keyConverterClassName: String,
    pathPrefix: Option[String],
    pathPrefixResolverClassName: String,
    extensionName: String,
    maxLoadAttempts: Int,
    clientConfig: S3ClientConfig
)
