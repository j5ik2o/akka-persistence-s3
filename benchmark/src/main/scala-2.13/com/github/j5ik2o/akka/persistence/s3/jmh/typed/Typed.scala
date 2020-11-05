package com.github.j5ik2o.akka.persistence.s3.jmh.typed

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import com.github.j5ik2o.akka.persistence.s3.jmh.typed.TypedCounter.{ Increment, IncrementReply }
import com.github.j5ik2o.akka.persistence.s3.util.RandomPortUtil
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration._

object Typed {
  val accessKeyId     = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val miniPort        = RandomPortUtil.temporaryServerPort()
}
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class Typed extends BenchmarkHelper {

  @Benchmark
  def increment(): Unit = {
    implicit val to          = Timeout(10 seconds)
    implicit val typedSystem = system.toTyped
    val future               = typedRef.ask[IncrementReply](ref => Increment(1, ref))
    try {
      Await.result(future, Duration.Inf)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
    }
  }

  override protected def minioAccessKeyId: String     = Typed.accessKeyId
  override protected def minioSecretAccessKey: String = Typed.secretAccessKey
  override protected def minioPort: Int               = Typed.miniPort
  override protected val s3BucketName: String         = "typed-" + UUID.randomUUID().toString
}
