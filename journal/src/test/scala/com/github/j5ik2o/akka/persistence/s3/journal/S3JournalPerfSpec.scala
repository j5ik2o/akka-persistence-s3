package com.github.j5ik2o.akka.persistence.s3.journal

import akka.actor.ActorSystem
import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalPerfSpec
import com.dimafeng.testcontainers.{ Container, ForAllTestContainer, ForEachTestContainer }
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, S3SpecSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Second, Seconds, Span }

import scala.concurrent.duration._

class S3JournalPerfSpec
    extends JournalPerfSpec(
      ConfigHelper.config(
        "journal-reference",
        S3JournalSpec.minioPort,
        S3JournalSpec.accessKeyId,
        S3JournalSpec.secretAccessKey
      )
    )
    with ScalaFutures
    with ForAllTestContainer
    with S3SpecSupport
    with Eventually {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.off()

  /** Override in order to customize timeouts used for expectMsg, in order to tune the awaits to your journal's perf */
  override def awaitDurationMillis: Long = (600 * sys.env.getOrElse("SBT_TEST_TIME_FACTOR", "1").toInt).seconds.toMillis

  /** Number of messages sent to the PersistentActor under test for each test iteration */
  override def eventsCount: Int = 5

  /** Number of measurement iterations each test will be run. */
  override def measurementIterations: Int = 5

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
      createS3Bucket().futureValue
    }
  }

}
