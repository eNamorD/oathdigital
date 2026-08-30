package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId

final class PowerRegistry private (
    private val auditedIds: Set[PowerId],
    private val byId: Map[PowerId, RegisteredPower],
    private val byWindow: Map[PowerWindow, Vector[RegisteredPower]]
) {
  def lookup(id: PowerId): Option[RegisteredPower] = byId.get(id)
  def isAudited(id: PowerId): Boolean = auditedIds(id)

  def at(window: PowerWindow): Vector[RegisteredPower] =
    byWindow.getOrElse(window, Vector.empty)
}

object PowerRegistry {
  def apply(entries: RegisteredPower*): PowerRegistry = {
    withAudited(entries.map(_.definition.id).toSet, entries: _*)
  }

  def withAudited(auditedIds: Set[PowerId], entries: RegisteredPower*)
      : PowerRegistry = {
    val ids = entries.map(_.definition.id)
    require(ids.distinct.size == ids.size, "registered power IDs must be unique")
    require(ids.forall(auditedIds), "registered powers must be audited")
    val ordered = entries.toVector
    val windows = ordered.flatMap(power => power.definition.windows.map(_ -> power))
      .groupBy(_._1).map { case (window, values) =>
        window -> values.map(_._2).sortBy(_.definition.id.value)
      }
    new PowerRegistry(auditedIds,
      ordered.map(power => power.definition.id -> power).toMap,
      windows)
  }
}
