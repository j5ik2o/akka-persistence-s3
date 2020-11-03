package com.github.j5ik2o.akka.persistence.s3.snapshot

import akka.actor.ActorSystem
import akka.persistence.snapshot.SnapshotStoreSpec
import com.dimafeng.testcontainers.{ Container, ForEachTestContainer }
import com.github.j5ik2o.akka.persistence.s3.config.SnapshotPluginConfig
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, RandomPortUtil, S3SpecSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Second, Seconds, Span }

object S3SnapshotStoreSpec {
  val bucketName      = SnapshotPluginConfig.defaultBucketName
  val accessKeyId     = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val minioPort       = RandomPortUtil.temporaryServerPort()
}

class S3SnapshotStoreSpec
    extends SnapshotStoreSpec(
      ConfigHelper.config(
        "snapshot-reference",
        S3SnapshotStoreSpec.minioPort,
        S3SnapshotStoreSpec.accessKeyId,
        S3SnapshotStoreSpec.secretAccessKey
      )
    )
    with S3SpecSupport
    with ForEachTestContainer
    with ScalaFutures
    with Eventually {

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override protected def minioAccessKeyId: String = S3SnapshotStoreSpec.accessKeyId

  override protected def minioSecretAccessKey: String = S3SnapshotStoreSpec.secretAccessKey

  override protected def minioPort: Int = S3SnapshotStoreSpec.minioPort

  override protected def s3BucketName: String = S3SnapshotStoreSpec.bucketName

  override def container: Container = minioContainer

  override def afterStart(): Unit = {
    super.afterStart()
    import system.dispatcher
    eventually {
      createS3Bucket().futureValue
    }
  }

}
