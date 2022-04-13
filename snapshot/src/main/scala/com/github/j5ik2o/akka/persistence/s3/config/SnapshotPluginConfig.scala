package com.github.j5ik2o.akka.persistence.s3.config

import com.github.j5ik2o.akka.persistence.s3.base.config.{ PluginConfig, S3ClientConfig }
import com.github.j5ik2o.akka.persistence.s3.base.metrics.{ MetricsReporter, MetricsReporterProvider }
import com.github.j5ik2o.akka.persistence.s3.base.trace.{ TraceReporter, TraceReporterProvider }
import com.github.j5ik2o.akka.persistence.s3.base.utils.ClassCheckUtils
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

object SnapshotPluginConfig {

  val defaultBucketName                               = "j5ik2o.akka-persistence-s3-snapshot"
  val metricsReporterClassNameKey                     = "metrics-reporter-class-name"
  val metricsReporterProviderClassNameKey             = "metrics-reporter-provider-class-name"
  val traceReporterClassNameKey                       = "trace-reporter-class-name"
  val traceReporterProviderClassNameKey               = "trace-reporter-provider-class-name"
  val DefaultMetricsReporterClassName: String         = classOf[MetricsReporter.None].getName
  val DefaultMetricsReporterProviderClassName: String = classOf[MetricsReporterProvider.Default].getName
  val DefaultTraceReporterProviderClassName: String   = classOf[TraceReporterProvider.Default].getName

  def fromConfig(rootConfig: Config): SnapshotPluginConfig = {
    SnapshotPluginConfig(
      bucketName = rootConfig.getAs[String]("bucket-name"),
      bucketNameResolverClassName = rootConfig.as[String]("bucket-name-resolver-class-name"),
      keyConverterClassName = rootConfig.as[String]("key-converter-class-name"),
      pathPrefix = rootConfig.getAs[String]("path-prefix"),
      pathPrefixResolverClassName = rootConfig.as[String]("path-prefix-resolver-class-name"),
      extensionName = rootConfig.as[String]("extension-name"),
      maxLoadAttempts = rootConfig.as[Int]("max-load-attempts"),
      metricsReporterClassName = {
        val className = rootConfig.getAs[String](metricsReporterClassNameKey)
        ClassCheckUtils.requireClass(classOf[MetricsReporter], className)
      },
      metricsReporterProviderClassName = {
        val className =
          rootConfig.getOrElse[String](metricsReporterProviderClassNameKey, DefaultMetricsReporterProviderClassName)
        ClassCheckUtils.requireClass(classOf[MetricsReporterProvider], className)
      },
      traceReporterProviderClassName = {
        val className =
          rootConfig.getOrElse[String](traceReporterProviderClassNameKey, DefaultTraceReporterProviderClassName)
        ClassCheckUtils.requireClass(classOf[TraceReporterProvider], className)
      },
      traceReporterClassName = {
        val className = rootConfig.getAs[String](traceReporterClassNameKey)
        ClassCheckUtils.requireClass(classOf[TraceReporter], className)
      },
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
    metricsReporterProviderClassName: String,
    metricsReporterClassName: Option[String],
    traceReporterProviderClassName: String,
    traceReporterClassName: Option[String],
    clientConfig: S3ClientConfig
) extends PluginConfig
