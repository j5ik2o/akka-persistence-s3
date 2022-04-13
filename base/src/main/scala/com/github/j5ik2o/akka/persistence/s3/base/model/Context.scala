package com.github.j5ik2o.akka.persistence.s3.base.model

import java.util.UUID

trait Context {
  def id: UUID
  def persistenceId: PersistenceId
  def data: Option[Any]
  def withData(value: Option[Any]): Context
}
