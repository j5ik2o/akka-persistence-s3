package com.github.j5ik2o.akka.persistence.s3.util

import java.net.URI

import com.dimafeng.testcontainers.{ Container, FixedHostPortGenericContainer }
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.{ S3Client => JavaS3SyncClient }

import scala.jdk.CollectionConverters._

trait S3ContainerHelper {
  protected val s3ImageVersion = "RELEASE.2020-03-19T21-49-00Z"
  protected val s3ImageName    = s"minio/minio:$s3ImageVersion"

  protected def minioAccessKeyId: String
  protected def minioSecretAccessKey: String
  protected def minioPort: Int

  protected lazy val minioContainer: Container =
    FixedHostPortGenericContainer(
      s3ImageName,
      env = Map("MINIO_ACCESS_KEY" -> minioAccessKeyId, "MINIO_SECRET_KEY" -> minioSecretAccessKey),
      command = Seq("server", "--compat", "/data"),
      exposedHostPort = minioPort,
      exposedContainerPort = 9000,
      waitStrategy = Wait.defaultWaitStrategy()
    )
  /*
      .configure { c =>
      c.withFileSystemBind("./target/data", "/data", BindMode.READ_WRITE)
    }*/

  lazy val javaS3SyncClient = JavaS3SyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider
        .create(AwsBasicCredentials.create(minioAccessKeyId, minioSecretAccessKey))
    )
    .endpointOverride(URI.create(s"http://127.0.0.1:${minioPort}"))
    .build()

  protected def s3BucketName: String

  protected def createS3Bucket(): Unit = {
    if (!javaS3SyncClient.listBuckets().buckets().asScala.exists(_.name() == s3BucketName)) {
      javaS3SyncClient.createBucket(
        CreateBucketRequest
          .builder()
          .bucket(s3BucketName)
          .build()
      )
    }
  }
}
