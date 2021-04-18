package com.github.j5ik2o.akka.persistence.s3.journal

import com.github.j5ik2o.akka.persistence.s3.base.model.{ PersistenceId, SequenceNumber }

final case class JournalRow(
    persistenceId: PersistenceId,
    sequenceNumber: SequenceNumber,
    deleted: Boolean,
    message: Array[Byte],
    ordering: Long,
    tags: Option[String] = None
) {

  def withDeleted: JournalRow               = copy(deleted = true)
  def withOrdering(value: Long): JournalRow = copy(ordering = value)

  def canEqual(other: Any): Boolean = other.isInstanceOf[JournalRow]

  override def equals(other: Any): Boolean =
    other match {
      case that: JournalRow =>
        (that canEqual this) &&
          persistenceId == that.persistenceId &&
          sequenceNumber == that.sequenceNumber
      case _ => false
    }

  override def hashCode(): Int = {
    val state = Seq(persistenceId, sequenceNumber)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

}
