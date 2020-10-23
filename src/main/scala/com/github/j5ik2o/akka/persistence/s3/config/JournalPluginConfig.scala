package com.github.j5ik2o.akka.persistence.s3.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

object JournalPluginConfig {
  val defaultBucketName = "j5ik2o.akka-persistence-s3-journal"
  val tagSeparatorKey   = "tag-separator"

  val DefaultTagSeparator: String = ","

  def fromConfig(rootConfig: Config): JournalPluginConfig = {
    JournalPluginConfig(
      bucketName = rootConfig.getAs[String]("bucket-name"),
      bucketNameResolverClassName = rootConfig.as[String]("bucket-name-resolver-class-name"),
      keyConverterClassName = rootConfig.as[String]("key-converter-class-name"),
      pathPrefix = rootConfig.getAs[String]("path-prefix"),
      pathPrefixResolverClassName = rootConfig.as[String]("path-prefix-resolver-class-name"),
      extensionName = rootConfig.as[String]("extension-name"),
      tagSeparator = rootConfig.getOrElse[String](tagSeparatorKey, DefaultTagSeparator),
      listObjectsBatchSize = rootConfig.as[Int]("list-objects-batch-size"),
      clientConfig = S3ClientConfig.fromConfig(rootConfig.getConfig("s3-client"))
    )
  }
}
final case class JournalPluginConfig(
    bucketName: Option[String],
    bucketNameResolverClassName: String,
    keyConverterClassName: String,
    pathPrefix: Option[String],
    pathPrefixResolverClassName: String,
    extensionName: String,
    tagSeparator: String,
    listObjectsBatchSize: Int,
    clientConfig: S3ClientConfig
)
