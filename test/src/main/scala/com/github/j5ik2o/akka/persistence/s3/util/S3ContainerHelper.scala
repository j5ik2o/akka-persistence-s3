package com.github.j5ik2o.akka.persistence.s3.util

import com.github.j5ik2o.dockerController.minio.MinioController
import com.github.j5ik2o.dockerController.{ DockerController, DockerControllerHelper, WaitPredicates }
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.services.s3.model.{ CreateBucketRequest, ListBucketsResponse }
import software.amazon.awssdk.services.s3.{ S3Client => JavaS3SyncClient }

import java.net.URI
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.jdk.CollectionConverters._

trait S3ContainerHelper extends DockerControllerHelper {
  protected val s3ImageVersion = "RELEASE.2020-03-19T21-49-00Z"
  protected val s3ImageName    = s"minio/minio:$s3ImageVersion"

  protected def minioAccessKeyId: String
  protected def minioSecretAccessKey: String

  protected def minioHost: String = dockerHost
  protected def minioPort: Int

  protected val minioController: MinioController =
    MinioController(dockerClient)(minioPort, minioAccessKeyId, minioSecretAccessKey)

  override protected val dockerControllers: Vector[DockerController] = Vector(minioController)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      minioController -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          minioPort,
          1.seconds,
          Some(3.seconds)
        )
      )
    )

  protected def startDockerContainers(): Unit = {
    for (dockerController <- dockerControllers) {
      createDockerContainer(dockerController, None)
    }
    for (dockerController <- dockerControllers) {
      startDockerContainer(dockerController, None)
    }
  }

  protected def stopDockerContainers(): Unit = {
    for (dockerController <- dockerControllers) {
      stopDockerContainer(dockerController, None)
    }
    for (dockerController <- dockerControllers) {
      removeDockerContainer(dockerController, None)
    }
  }

  lazy val javaS3SyncClient: JavaS3SyncClient = JavaS3SyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider
        .create(AwsBasicCredentials.create(minioAccessKeyId, minioSecretAccessKey))
    )
    .endpointOverride(URI.create(s"http://$minioHost:$minioPort"))
    .build()

  protected def s3BucketName: String

  protected def waitBucket(): Unit = {
    while (!javaS3SyncClient.listBuckets().buckets().asScala.exists(_.name() == s3BucketName)) {
      println("waiting create bucket...")
      Thread.sleep(100)
    }
  }

  protected def listBuckets(): ListBucketsResponse = {
    javaS3SyncClient.listBuckets()
  }

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
