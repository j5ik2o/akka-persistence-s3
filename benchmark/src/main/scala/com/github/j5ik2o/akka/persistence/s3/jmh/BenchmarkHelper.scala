package com.github.j5ik2o.akka.persistence.s3.jmh

import java.util.UUID

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ typed, ActorRef, ActorSystem, Props }
import com.github.j5ik2o.akka.persistence.s3.util.{ ConfigHelper, S3ContainerHelper }
import org.openjdk.jmh.annotations.{ Setup, TearDown }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration.Duration

trait BenchmarkHelper extends S3ContainerHelper {

  var system: ActorSystem                            = _
  var untypedRef: ActorRef                           = _
  var typedRef: typed.ActorRef[TypedCounter.Command] = _

  @Setup
  def setup(): Unit = {
    minioContainer.start()
    Thread.sleep(1000)
    val config =
      ConfigHelper.config(None, minioPort, minioAccessKeyId, minioSecretAccessKey, Some(s3BucketName))
    createS3Bucket()
    system = ActorSystem("benchmark-" + UUID.randomUUID().toString, config)

    val props = Props(new UntypedCounter(UUID.randomUUID()))
    untypedRef = system.actorOf(props, "untyped-counter")
    typedRef = system.spawn(TypedCounter(UUID.randomUUID()), "typed-counter")
  }

  @TearDown
  def tearDown(): Unit = {
    minioContainer.stop()
    system.terminate()
  }
}
