package com.github.j5ik2o.akka.persistence.s3.base.trace

import com.github.j5ik2o.akka.persistence.s3.base.metrics.Context

import scala.concurrent.Future

trait TraceReporter {

  def traceJournalAsyncWriteMessages[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalAsyncDeleteMessagesTo[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalAsyncReplayMessages[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalAsyncReadHighestSequenceNr[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalAsyncUpdateEvent[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalSerializeJournal[T](context: Context)(f: => Future[T]): Future[T]

  def traceJournalDeserializeJournal[T](context: Context)(f: => Future[T]): Future[T]

}
