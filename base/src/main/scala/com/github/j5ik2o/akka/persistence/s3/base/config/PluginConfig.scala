package com.github.j5ik2o.akka.persistence.s3.base.config

trait PluginConfig {
  def bucketName: Option[String]
  def bucketNameResolverClassName: String
  def metricsReporterProviderClassName: String
  def metricsReporterClassName: Option[String]
  def traceReporterProviderClassName: String
  def traceReporterClassName: Option[String]
  def clientConfig: S3ClientConfig
}
