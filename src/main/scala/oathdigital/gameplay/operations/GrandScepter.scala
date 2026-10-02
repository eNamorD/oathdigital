package oathdigital.gameplay.operations

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object TheGrandScepterCard extends Relic(RelicId("grand-scepter"), "The Grand Scepter", value = 0, defense = 3):
  val restriction = PrintedPower(PowerId("relic.the-grand-scepter.restriction"),
    persistent = false, cost = Cost.free,
    text = "This relic **cannot** be removed from play or added to a " +
      "lineage.")
  val campaign = PrintedPower(PowerId("relic.the-grand-scepter.campaign"),
    persistent = false, cost = Cost.free,
    text = "[favor-burnt][favor-burnt][favor-burnt] If you are Imperial " +
      "and victorious against a Citizen, you exile them.")
  val action = PrintedPower(PowerId("relic.the-grand-scepter.action"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** You may become a Citizen if an Exile. **IN " +
      "NEGOTIATION:** May offer Citizenship.")
  val negotiation = PrintedPower(PowerId("relic.the-grand-scepter.negotiation"),
    persistent = false, cost = Cost.free,
    text = "**IN NEGOTIATION:** May offer Citizenship.")
  val powers: Vector[PrintedPower] = Vector(restriction, campaign, action, negotiation)

/** The Grand Scepter cannot be removed from play (global operation
  * restrictions design, "Rules"). A move that takes it from a site or a play
  * area to anywhere else is refused: a discard to the set-aside relics or a
  * return to the relic deck. A `Bury` of it is refused too. Passing it between
  * players, by a `Take` or a `Give`, keeps it in play and is allowed. The
  * scepter is never facedown, so a refusal reveals nothing.
  */
final case class GrandScepter(relic: RelicId) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Option.when(Operation.flatten(operation).exists(removes))(OperationReason(
      "grand-scepter", s"${relic.value} cannot be removed from play",
      OperationReasonKind.Impossible))

  private def removes(step: Operation): Boolean = step match
    case Move(Piece.Card(moved), from, to, _) if moved == relic =>
      OperationRestrictions.inPlay(from.location) &&
        !OperationRestrictions.inPlay(to.location)
    case Bury(BuryableCard.Relic(buried), _, _) => buried == relic
    case _ => false
