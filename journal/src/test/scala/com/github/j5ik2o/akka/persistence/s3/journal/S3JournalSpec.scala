package com.github.j5ik2o.akka.persistence.s3.journal

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.dimafeng.testcontainers.{ Container, ForEachTestContainer }
import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, RandomPortUtil, S3SpecSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Second, Seconds, Span }

object S3JournalSpec {
  val accessKeyId     = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val minioPort       = RandomPortUtil.temporaryServerPort()
  val bucketName      = JournalPluginConfig.defaultBucketName
}
class S3JournalSpec
    extends JournalSpec(
      ConfigHelper.config(
        Some("journal-reference"),
        sys.env.getOrElse("SBT_TEST_TIME_FACTOR", "1").toDouble,
        S3JournalSpec.minioPort,
        S3JournalSpec.accessKeyId,
        S3JournalSpec.secretAccessKey,
        None
      )
    )
    with ScalaFutures
    with ForEachTestContainer
    with S3SpecSupport
    with Eventually {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.on()

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override protected def minioAccessKeyId: String = S3JournalSpec.accessKeyId

  override protected def minioSecretAccessKey: String = S3JournalSpec.secretAccessKey

  override protected def minioPort: Int = S3JournalSpec.minioPort

  override protected def s3BucketName: String = S3JournalSpec.bucketName

  override def container: Container = minioContainer

  override def afterStart(): Unit = {
    super.afterStart()
    import system.dispatcher
    eventually {
      createS3Bucket()
    }
  }

}
