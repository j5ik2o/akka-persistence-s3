package com.github.j5ik2o.akka.persistence.s3.serialization

import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }
import com.github.j5ik2o.akka.persistence.s3.base.utils.EitherSeq

import scala.collection.immutable.{ Seq, Set }

trait PersistentReprSerializer[A] {

  def serialize(atomicWrites: Seq[AtomicWrite]): Seq[Either[Throwable, Seq[A]]] = {
    atomicWrites.map { atomicWrite =>
      val serialized = atomicWrite.payload.zipWithIndex.map {
        case (v, index) => serialize(v, Some(index))
      }
      EitherSeq.sequence(serialized)
    }
  }

  def serialize(persistentRepr: PersistentRepr, index: Option[Int]): Either[Throwable, A] =
    persistentRepr.payload match {
      case Tagged(payload, tags) =>
        serialize(persistentRepr.withPayload(payload), tags, index)
      case _ =>
        serialize(persistentRepr, Set.empty[String], index)
    }

  def serialize(persistentRepr: PersistentRepr): Either[Throwable, A] = serialize(persistentRepr, None)

  def serialize(persistentRepr: PersistentRepr, tags: Set[String], index: Option[Int]): Either[Throwable, A]

  def deserialize(t: A): Either[Throwable, (PersistentRepr, Set[String], Long)]

}
