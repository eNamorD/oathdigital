package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Magic Waterskin (relic R45), ACTION: bury this relic, then gain 4 Supply.
  * The bury returns any secrets on the relic to its holder facedown before
  * the relic enters the deck. Only a faceup relic in the holder's play area
  * is a source, so the engine's access rule supplies the "faceup" condition.
  */
case object MagicWaterskin extends PaidAction("relic.magic-waterskin",
    Cost.free) {
  val Supply: Int = 4

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(id) => for {
      held <- PlayerFacts.player(ready, player)
      relic <- held.relics.find(_.id == id).toRight(
        OathViolation.InvalidEventOrder(s"${id.value} is not held by ${player.value}"))
    } yield Sequence(Bury.standard(BuryableCard.Relic(id),
      PositionedLocation(Location.PlayArea(player)), None, 0,
      relic.tokens.secrets, player) :+ GainSupply(player, Supply))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }
}
