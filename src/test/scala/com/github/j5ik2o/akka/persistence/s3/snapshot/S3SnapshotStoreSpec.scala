package com.github.j5ik2o.akka.persistence.s3.snapshot

import java.net.{Socket, URI}
import java.util.concurrent.TimeUnit

import akka.persistence.snapshot.SnapshotStoreSpec
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.config.ConfigFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{
  DockerCommandExecutor,
  DockerContainer,
  DockerContainerState,
  DockerFactory,
  DockerReadyChecker,
  LogLineReceiver
}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class TCPPortReadyChecker(port: Int,
                               host: Option[String] = None,
                               duration: Duration =
                                 Duration(1, TimeUnit.SECONDS))
    extends DockerReadyChecker {
  override def apply(container: DockerContainerState)(
    implicit docker: DockerCommandExecutor,
    ec: ExecutionContext
  ): Future[Boolean] = {
    container.getPorts().map(_(port)).flatMap { p =>
      var socket: Socket = null
      Future {
        try {
          socket = new Socket(host.getOrElse(docker.host), p)
          val result = socket.isConnected
          Thread.sleep(duration.toMillis)
          result
        } catch {
          case _: Exception =>
            false
        } finally {
          if (socket != null)
            socket.close()
        }
      }
    }
  }
}
object S3SnapshotStoreSpec {
  val bucketName = "j5ik2o.akka-persistence-s3"
  val accessKeyId = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val minioPort = 9000
}
class S3SnapshotStoreSpec
    extends SnapshotStoreSpec(
      ConfigFactory
        .parseString(s"""
    |akka.persistence.snapshot-store.plugin = "j5ik2o.s3-snapshot-store"
    |j5ik2o.s3-snapshot-store {
    |  s3-client {
    |    access-key-id = "${S3SnapshotStoreSpec.accessKeyId}"
    |    secret-access-key = "${S3SnapshotStoreSpec.secretAccessKey}"
    |    endpoint = "http://127.0.0.1:${S3SnapshotStoreSpec.minioPort}"
    |    s3-options {
    |      path-style-access-enabled = true
    |    }
    |  }
    |}
  """.stripMargin)
        .withFallback(ConfigFactory.load())
    )
    with DockerTestKit
    with BeforeAndAfterAll
    with ScalaFutures {
  import S3SnapshotStoreSpec._
  protected val connectTimeout: FiniteDuration = 3 seconds
  protected val readTimeout: FiniteDuration = 3 seconds

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  protected val dockerClient: DockerClient =
    DefaultDockerClient
      .fromEnv()
      .connectTimeoutMillis(connectTimeout.toMillis)
      .readTimeoutMillis(readTimeout.toMillis)
      .build()

  override implicit def dockerFactory: DockerFactory =
    new SpotifyDockerFactory(dockerClient)

  val minioContainer: DockerContainer =
    DockerContainer("minio/minio:RELEASE.2020-03-19T21-49-00Z")
      .withEnv(
        s"MINIO_ACCESS_KEY=$accessKeyId",
        s"MINIO_SECRET_KEY=$secretAccessKey"
      )
      .withPorts(minioPort -> Some(minioPort))
      .withReadyChecker(
        TCPPortReadyChecker(
          minioPort,
          duration = Duration(
            500 * sys.env.getOrElse("SBT_TEST_TIME_FACTOR", "1").toInt,
            TimeUnit.MILLISECONDS
          )
        )
      )
      .withCommand("server", "--compat", "/data")
      .withLogLineReceiver(LogLineReceiver(true, { message =>
        println(s">>> $message")
      }))

  override def dockerContainers: List[DockerContainer] =
    minioContainer :: super.dockerContainers

  override def beforeAll(): Unit = {
    super.beforeAll()
    val javaS3Client: S3AsyncClient =
      S3AsyncClient
        .builder()
        .credentialsProvider(
          StaticCredentialsProvider
            .create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
        )
        .endpointOverride(URI.create(s"http://127.0.0.1:$minioPort"))
        .build()
    javaS3Client
      .createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
      .get()
  }

}
