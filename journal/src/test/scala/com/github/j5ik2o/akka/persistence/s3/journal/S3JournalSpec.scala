package com.github.j5ik2o.akka.persistence.s3.journal

import akka.persistence.CapabilityFlag
import com.github.dockerjava.core.DockerClientConfig
import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, RandomPortUtil, S3SpecSupport }
import com.github.j5ik2o.dockerController.{ DockerClientConfigUtil, DockerControllerSpecSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }

object S3JournalSpec {
  val accessKeyId: String                    = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey: String                = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()
  val minioHost: String                      = DockerClientConfigUtil.dockerHost(dockerClientConfig)
  val minioPort: Int                         = RandomPortUtil.temporaryServerPort()
  val bucketName: String                     = JournalPluginConfig.defaultBucketName
}

class S3JournalSpec
    extends akka.persistence.journal.JournalSpec(
      ConfigHelper.config(
        Some("journal-reference"),
        sys.env.getOrElse("SBT_TEST_TIME_FACTOR", "1").toDouble,
        S3JournalSpec.minioHost,
        S3JournalSpec.minioPort,
        S3JournalSpec.accessKeyId,
        S3JournalSpec.secretAccessKey,
        None
      )
    )
    with ScalaFutures
    with S3SpecSupport
    with DockerControllerSpecSupport
    with Eventually {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.on()

  implicit val pc: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(5, Millis))

  override protected def minioAccessKeyId: String = S3JournalSpec.accessKeyId

  override protected def minioSecretAccessKey: String = S3JournalSpec.secretAccessKey

  override protected def minioHost: String = S3JournalSpec.minioHost

  override protected def minioPort: Int = S3JournalSpec.minioPort

  override protected def s3BucketName: String = S3JournalSpec.bucketName

  override def afterStartContainers(): Unit = {
    var b = false
    while (!b) {
      try {
        listBuckets()
        b = true
      } catch {
        case _: Throwable =>
          Thread.sleep(500)
      }
    }
    createS3Bucket()
    waitBucket()
  }

}
