package com.github.j5ik2o.akka.persistence.s3.base.utils

import java.net.URI

import com.github.j5ik2o.akka.persistence.s3.base.config.{ S3ClientConfig, S3ClientOptionsConfig }
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider,
  WebIdentityTokenFileCredentialsProvider
}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{ S3AsyncClient, S3AsyncClientBuilder, S3Configuration }

object S3ClientBuilderUtils {

  def setup(clientConfig: S3ClientConfig, httpClientBuilder: SdkAsyncHttpClient): S3AsyncClientBuilder = {
    var builder =
      S3AsyncClient.builder().httpClient(httpClientBuilder)
    (sys.env.get("AWS_ROLE_ARN"), clientConfig.accessKeyId, clientConfig.secretAccessKey) match {
      case (Some(_), _, _) =>
        builder = builder.credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
      case (None, Some(a), Some(s)) =>
        builder = builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(a, s))
        )
      case _ =>
    }
    clientConfig.endpoint.foreach { ep => builder = builder.endpointOverride(URI.create(ep)) }
    clientConfig.region.foreach { r => builder = builder.region(Region.of(r)) }
    clientConfig.s3OptionConfig.foreach { o => builder = builder.serviceConfiguration(getS3Configuration(o)) }
    builder
  }

  private def getS3Configuration(
      s3ClientOptionConfig: S3ClientOptionsConfig
  ): S3Configuration = {
    var builder = S3Configuration.builder()
    s3ClientOptionConfig.dualstackEnabled.foreach { c => builder = builder.dualstackEnabled(c) }
    s3ClientOptionConfig.accelerateModeEnabled.foreach { c => builder = builder.accelerateModeEnabled(c) }
    s3ClientOptionConfig.pathStyleAccessEnabled.foreach { c => builder = builder.pathStyleAccessEnabled(c) }
    s3ClientOptionConfig.checksumValidationEnabled.foreach { c => builder = builder.checksumValidationEnabled(c) }
    s3ClientOptionConfig.chunkedEncodingEnabled.foreach { c => builder = builder.checksumValidationEnabled(c) }
    s3ClientOptionConfig.useArnRegionEnabled.foreach { c => builder = builder.useArnRegionEnabled(c) }
    builder.build()
  }

}
