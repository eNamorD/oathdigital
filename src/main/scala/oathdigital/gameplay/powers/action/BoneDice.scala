package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Bone Dice (relic R24), ACTION: place 1 secret on this relic, roll 2 attack
  * dice, gain Supply equal to the sword score, then bury this relic if any
  * skull face rolled.
  *
  * The bury uses the standard returns, so the secret the cost just placed on
  * the relic goes back to its holder facedown. The tokens are read inside the
  * `BuildOps`, after the engine has paid the cost, and not in `build`, which
  * runs before it.
  */
case object BoneDice extends PaidAction("relic.bone-dice", Cost(secret = 1)) {
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("bone-dice")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(id) => Right(Sequence(Vector(
      ModifyDicePool(pool, Dice),
      Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic),
      BuildOps((state, _) => settle(state, player, id)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def settle(state: ReadyGame, player: PlayerId, id: RelicId)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    held <- PlayerFacts.player(state, player)
    relic <- held.relics.find(_.id == id).toRight(
      OathViolation.InvalidEventOrder(
        s"${id.value} is not held by ${player.value}"))
  } yield {
    val supply = RollResults.score(state, pool)
    val gain: Vector[CoreOperation] =
      if (supply > 0) Vector(GainSupply(player, supply)) else Vector.empty
    val bury: Vector[CoreOperation] =
      if (RollResults.skulls(state, pool) > 0)
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0,
          relic.tokens.secrets, player)
      else Vector.empty
    gain ++ bury
  }
}
