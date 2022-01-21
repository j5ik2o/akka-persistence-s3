package com.github.j5ik2o.akka.persistence.s3.serialization

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

trait FlowPersistentReprSerializer[T] extends PersistentReprSerializer[T] {

  def deserializeFlow(implicit ec: ExecutionContext): Flow[T, (PersistentRepr, Set[String], Long), NotUsed] = {
    Flow[T].mapAsync(1)(deserialize)
  }

  def deserializeFlowWithoutTags(implicit ec: ExecutionContext): Flow[T, PersistentRepr, NotUsed] = {
    deserializeFlow.map(keepPersistentRepr)
  }

  // ---

  def deserializeFlowAsTry(implicit
      ec: ExecutionContext
  ): Flow[T, Try[(PersistentRepr, Set[String], Long)], NotUsed] = {
    Flow[T]
      .mapAsync(1)(deserialize)
      .map(Success(_)).recover { case ex =>
        Failure(ex)
      }
  }

  def deserializeFlowWithoutTagsAsTry(implicit ec: ExecutionContext): Flow[T, Try[PersistentRepr], NotUsed] = {
    deserializeFlowAsTry.map(_.map(keepPersistentRepr))
  }

  private def keepPersistentRepr(tup: (PersistentRepr, Set[String], Long)): PersistentRepr = tup match {
    case (repr, _, _) => repr
  }

}
