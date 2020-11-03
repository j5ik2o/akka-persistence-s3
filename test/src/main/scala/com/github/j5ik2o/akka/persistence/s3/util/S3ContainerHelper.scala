package com.github.j5ik2o.akka.persistence.s3.util

import java.net.URI

import com.dimafeng.testcontainers.{ Container, FixedHostPortGenericContainer }
import com.github.j5ik2o.reactive.aws.s3.S3AsyncClient
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.{ S3AsyncClient => JavaS3AsyncClient }

import scala.concurrent.{ ExecutionContext, Future }
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

  private lazy val javaS3Client: JavaS3AsyncClient =
    JavaS3AsyncClient
      .builder()
      .credentialsProvider(
        StaticCredentialsProvider
          .create(AwsBasicCredentials.create(minioAccessKeyId, minioSecretAccessKey))
      )
      .endpointOverride(URI.create(s"http://127.0.0.1:${minioPort}"))
      .build()

  protected def s3BucketName: String

  protected lazy val s3Client: S3AsyncClient = S3AsyncClient(javaS3Client)

  protected def createS3Bucket()(implicit ec: ExecutionContext): Future[Unit] = {
    s3Client
      .listBuckets()
      .flatMap { list =>
        if (
          list
            .buckets()
            .asScala
            .exists(
              _.name() == s3BucketName
            )
        )
          Future.successful(())
        else
          s3Client
            .createBucket(
              CreateBucketRequest
                .builder()
                .bucket(s3BucketName)
                .build()
            )
            .map(_ => ())
      }
  }
}
