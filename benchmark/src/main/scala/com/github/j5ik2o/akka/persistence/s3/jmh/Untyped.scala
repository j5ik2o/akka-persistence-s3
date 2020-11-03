package com.github.j5ik2o.akka.persistence.s3.jmh

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.util.Timeout
import com.github.j5ik2o.akka.persistence.s3.jmh.UntypedCounter.Increment
import com.github.j5ik2o.akka.persistence.s3.util.RandomPortUtil
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration._

object Untyped {
  val accessKeyId     = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val miniPort        = RandomPortUtil.temporaryServerPort()
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class Untyped extends BenchmarkHelper {

  @Benchmark
  def increment(): Unit = {
    implicit val to = Timeout(10 seconds)
    val future      = untypedRef ? Increment(1)
    try {
      Await.result(future, Duration.Inf)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
    }
  }

  override protected def minioAccessKeyId: String = Untyped.accessKeyId

  override protected def minioSecretAccessKey: String = Untyped.secretAccessKey

  override protected def minioPort: Int = Untyped.miniPort

  override protected val s3BucketName: String = "untyped-" + UUID.randomUUID().toString
}
