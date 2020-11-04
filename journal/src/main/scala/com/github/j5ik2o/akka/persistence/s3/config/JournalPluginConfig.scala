package com.github.j5ik2o.akka.persistence.s3.config

import com.github.j5ik2o.akka.persistence.s3.base.config.{ PluginConfig, S3ClientConfig }
import com.github.j5ik2o.akka.persistence.s3.base.metrics.{ MetricsReporter, MetricsReporterProvider }
import com.github.j5ik2o.akka.persistence.s3.base.utils.ClassCheckUtils
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

object JournalPluginConfig {
  val defaultBucketName                               = "j5ik2o.akka-persistence-s3-journal"
  val tagSeparatorKey                                 = "tag-separator"
  val metricsReporterClassNameKey                     = "metrics-reporter-class-name"
  val metricsReporterProviderClassNameKey             = "metrics-reporter-provider-class-name"
  val DefaultTagSeparator: String                     = ","
  val DefaultMetricsReporterClassName: String         = classOf[MetricsReporter.None].getName
  val DefaultMetricsReporterProviderClassName: String = classOf[MetricsReporterProvider.Default].getName

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
      metricsReporterClassName = {
        val className = rootConfig.getAs[String](metricsReporterClassNameKey)
        ClassCheckUtils.requireClass(classOf[MetricsReporter], className)
      },
      metricsReporterProviderClassName = {
        val className =
          rootConfig.getOrElse[String](metricsReporterProviderClassNameKey, DefaultMetricsReporterProviderClassName)
        ClassCheckUtils.requireClass(classOf[MetricsReporterProvider], className)
      },
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
    metricsReporterProviderClassName: String,
    metricsReporterClassName: Option[String],
    clientConfig: S3ClientConfig
) extends PluginConfig {}
