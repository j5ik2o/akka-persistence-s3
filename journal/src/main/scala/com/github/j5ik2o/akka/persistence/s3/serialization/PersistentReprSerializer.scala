package com.github.j5ik2o.akka.persistence.s3.serialization

import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }

import scala.collection.immutable.{ Seq, Set }
import scala.concurrent.{ ExecutionContext, Future }

trait PersistentReprSerializer[A] {

  def serialize(atomicWrites: Seq[AtomicWrite])(implicit ec: ExecutionContext): Seq[Future[Seq[A]]] = {
    atomicWrites.map { atomicWrite =>
      val serialized = atomicWrite.payload.zipWithIndex.map { case (v, index) =>
        serialize(v, Some(index))
      }
      Future.sequence(serialized)
    }
  }

  def serialize(persistentRepr: PersistentRepr, index: Option[Int])(implicit ec: ExecutionContext): Future[A] =
    persistentRepr.payload match {
      case Tagged(payload, tags) =>
        serialize(persistentRepr.withPayload(payload), tags, index)
      case _ =>
        serialize(persistentRepr, Set.empty[String], index)
    }

  def serialize(persistentRepr: PersistentRepr)(implicit ec: ExecutionContext): Future[A] =
    serialize(persistentRepr, None)

  def serialize(persistentRepr: PersistentRepr, tags: Set[String], index: Option[Int])(implicit
      ec: ExecutionContext
  ): Future[A]

  def deserialize(t: A)(implicit ec: ExecutionContext): Future[(PersistentRepr, Set[String], Long)]

}
