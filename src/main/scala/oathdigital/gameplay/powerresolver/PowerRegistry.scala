package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId
import oathdigital.model.PowerWindow

final class PowerRegistry private (
    private val auditedIds: Set[PowerId],
    private val byId: Map[PowerId, Power],
    private val byWindow: Map[PowerWindow, Vector[(Power, PowerHandler)]]
) {
  def lookup(id: PowerId): Option[Power] = byId.get(id)
  def handler(id: PowerId, window: PowerWindow): Option[PowerHandler] =
    byId.get(id).flatMap(_.handlers.find(_.window == window))
  def isAudited(id: PowerId): Boolean = auditedIds(id)

  def at(window: PowerWindow): Vector[(Power, PowerHandler)] =
    byWindow.getOrElse(window, Vector.empty)
}

object PowerRegistry {
  def apply(entries: Power*): PowerRegistry = {
    withAudited(entries.map(_.id).toSet, entries: _*)
  }

  def withAudited(auditedIds: Set[PowerId], entries: Power*)
      : PowerRegistry = {
    entries.foreach(Power.validate)
    val ids = entries.map(_.id)
    require(ids.distinct.size == ids.size, "registered power IDs must be unique")
    require(ids.forall(auditedIds), "registered powers must be audited")
    val ordered = entries.toVector
    val windows = ordered.flatMap(power => power.handlers.map(handler =>
      handler.window -> (power -> handler)))
      .groupBy(_._1).map { case (window, values) =>
        window -> values.map(_._2).sortBy(_._1.id.value)
      }
    new PowerRegistry(auditedIds,
      ordered.map(power => power.id -> power).toMap,
      windows)
  }
}
