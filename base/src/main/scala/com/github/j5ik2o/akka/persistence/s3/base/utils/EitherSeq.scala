package com.github.j5ik2o.akka.persistence.s3.base.utils

import scala.collection.immutable.{ Seq, Vector }

object EitherSeq {

  def sequence[A](seq: Seq[Either[Throwable, A]]): Either[Throwable, Seq[A]] = {
    def recurse(remaining: Seq[Either[Throwable, A]], processed: Seq[A]): Either[Throwable, Seq[A]] =
      remaining match {
        case Seq()               => Right(processed)
        case Right(head) +: tail => recurse(remaining = tail, processed :+ head)
        case Left(t) +: _        => Left(t)
      }
    recurse(seq, Vector.empty)
  }
}
