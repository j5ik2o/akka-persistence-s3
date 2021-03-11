package com.github.j5ik2o.akka.persistence.s3.jmh.untyped

import java.util.UUID

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, S3ContainerHelper }
import org.openjdk.jmh.annotations.{ Setup, TearDown }

trait BenchmarkHelper extends S3ContainerHelper {

  var system: ActorSystem  = _
  var untypedRef: ActorRef = _

  @Setup
  def setup(): Unit = {
    minioContainer.start()
    Thread.sleep(1000)
    val config =
      ConfigHelper.config(
        None,
        testTimeFactor = 1.0,
        s3Host = minioHost,
        s3Port = minioPort,
        accessKeyId = minioAccessKeyId,
        secretAccessKey = minioSecretAccessKey,
        bucketName = Some(s3BucketName)
      )
    createS3Bucket()
    system = ActorSystem("benchmark-" + UUID.randomUUID().toString, config)

    val props = Props(new UntypedCounter(UUID.randomUUID()))
    untypedRef = system.actorOf(props, "untyped-counter")
  }

  @TearDown
  def tearDown(): Unit = {
    minioContainer.stop()
    system.terminate()
  }
}
