package com.github.j5ik2o.akka.persistence.s3.snapshot

import com.dimafeng.testcontainers.{ Container, ForEachTestContainer }
import com.github.j5ik2o.akka.persistence.s3.config.SnapshotPluginConfig
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, RandomPortUtil, S3SpecSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import org.testcontainers.DockerClientFactory

object S3SnapshotStoreSpec {
  val bucketName: String      = SnapshotPluginConfig.defaultBucketName
  val accessKeyId: String     = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val minioHost: String       = DockerClientFactory.instance().dockerHostIpAddress()
  val minioPort: Int          = RandomPortUtil.temporaryServerPort()
}

class S3SnapshotStoreSpec
    extends akka.persistence.snapshot.SnapshotStoreSpec(
      ConfigHelper.config(
        Some("snapshot-reference"),
        sys.env.getOrElse("SBT_TEST_TIME_FACTOR", "1").toDouble,
        S3SnapshotStoreSpec.minioHost,
        S3SnapshotStoreSpec.minioPort,
        S3SnapshotStoreSpec.accessKeyId,
        S3SnapshotStoreSpec.secretAccessKey,
        None
      )
    )
    with S3SpecSupport
    with ForEachTestContainer
    with ScalaFutures
    with Eventually {

  implicit val pc: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(5, Millis))

  override protected def minioAccessKeyId: String = S3SnapshotStoreSpec.accessKeyId

  override protected def minioSecretAccessKey: String = S3SnapshotStoreSpec.secretAccessKey

  override protected def minioHost: String = S3SnapshotStoreSpec.minioHost

  override protected def minioPort: Int = S3SnapshotStoreSpec.minioPort

  override protected def s3BucketName: String = S3SnapshotStoreSpec.bucketName

  override def container: Container = minioContainer

  override def afterStart(): Unit = {
    super.afterStart()
    eventually {
      listBuckets()
    }
    createS3Bucket()
    waitBucket()
  }

}
