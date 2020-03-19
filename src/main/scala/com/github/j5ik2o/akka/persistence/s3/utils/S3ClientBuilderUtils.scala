package com.github.j5ik2o.akka.persistence.s3.utils

import java.net.URI

import com.github.j5ik2o.akka.persistence.s3.config.S3ClientConfig
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{
  S3AsyncClient,
  S3AsyncClientBuilder,
  S3Configuration
}

object S3ClientBuilderUtils {

  def setup(clientConfig: S3ClientConfig,
            httpClientBuilder: SdkAsyncHttpClient): S3AsyncClientBuilder = {
    var dynamoDbAsyncClientBuilder =
      S3AsyncClient.builder().httpClient(httpClientBuilder)
    (clientConfig.accessKeyId, clientConfig.secretAccessKey) match {
      case (Some(a), Some(s)) =>
        dynamoDbAsyncClientBuilder =
          dynamoDbAsyncClientBuilder.credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(a, s))
          )
      case _ =>
    }
    clientConfig.endpoint.foreach { ep =>
      dynamoDbAsyncClientBuilder =
        dynamoDbAsyncClientBuilder.endpointOverride(URI.create(ep))
    }
    clientConfig.region.foreach { r =>
      dynamoDbAsyncClientBuilder =
        dynamoDbAsyncClientBuilder.region(Region.of(r))
    }
    dynamoDbAsyncClientBuilder
  }

}
