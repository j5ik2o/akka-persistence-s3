package com.github.j5ik2o.akka.persistence.s3.serialization

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.stream.scaladsl.Flow

import scala.util.{ Failure, Success, Try }

trait FlowPersistentReprSerializer[T] extends PersistentReprSerializer[T] {

  def deserializeFlow: Flow[T, (PersistentRepr, Set[String], Long), NotUsed] = {
    Flow[T].map(deserialize).map {
      case Right(r) => r
      case Left(ex) => throw ex
    }
  }

  def deserializeFlowWithoutTags: Flow[T, PersistentRepr, NotUsed] = {
    deserializeFlow.map(keepPersistentRepr)
  }

  // ---

  def deserializeFlowAsEither: Flow[T, Either[Throwable, (PersistentRepr, Set[String], Long)], NotUsed] = {
    Flow[T].map(deserialize)
  }

  def deserializeFlowWithoutTagsAsEither: Flow[T, Either[Throwable, PersistentRepr], NotUsed] = {
    deserializeFlowAsEither.map {
      case Right(v) => Right(keepPersistentRepr(v))
      case Left(ex) => Left(ex)
    }
  }

  // ---

  def deserializeFlowAsTry: Flow[T, Try[(PersistentRepr, Set[String], Long)], NotUsed] = {
    Flow[T].map(deserialize).map {
      case Right(v) => Success(v)
      case Left(ex) => Failure(ex)
    }
  }

  def deserializeFlowWithoutTagsAsTry: Flow[T, Try[PersistentRepr], NotUsed] = {
    deserializeFlowAsTry.map(_.map(keepPersistentRepr))
  }

  private def keepPersistentRepr(tup: (PersistentRepr, Set[String], Long)): PersistentRepr =
    tup match {
      case (repr, _, _) => repr
    }

}
