package com.github.j5ik2o.akka.persistence.s3.base.metrics

import scala.concurrent.duration._

class Stopwatch {
  val startedAtNanos: Long = System.nanoTime()

  def elapsed(): Duration = {
    (System.nanoTime() - startedAtNanos).nanos
  }
}

object Stopwatch {
  def start(): Stopwatch = new Stopwatch
}
