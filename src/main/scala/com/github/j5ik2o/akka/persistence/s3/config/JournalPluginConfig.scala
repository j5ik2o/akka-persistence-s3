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
      tagSeparator = rootConfig.getOrElse[String](tagSeparatorKey, DefaultTagSeparator),
      clientConfig = S3ClientConfig.fromConfig(rootConfig.getConfig("s3-client"))
    )
  }
}
final case class JournalPluginConfig(
    bucketName: Option[String],
    bucketNameResolverClassName: String,
    tagSeparator: String,
    clientConfig: S3ClientConfig
)
