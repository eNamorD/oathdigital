package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Murky Fountain (edifice E15, ruined), ACTION: place 1 secret on this card.
  * If your pawn is at this site, roll 2 defense dice and gain Supply equal to
  * the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`,
  * which is what Begin Rest does once its validation gate has passed. If your
  * pawn is elsewhere the cost is paid and nothing else happens, and the dice
  * are never asked for.
  *
  * The ruined face carries its own power id, so an intact Marble Fountains
  * never offers this power. The pawn test is in `build`, which is safe because
  * a pawn cannot move between the command that starts the power and the end of
  * a tree that never parks.
  */
case object MurkyFountain extends PaidAction("edifice.e15.ruined",
    Cost(secret = 1)) {
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("murky-fountain")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Edifice(id) =>
      if (!atPawnSite(ready, player, id)) Right(Sequence(Vector.empty))
      else Right(Sequence(Vector(
        ModifyDicePool(pool, Dice),
        Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
        BuildOps((state, _) => Right(outcome(state, player))))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not an edifice source"))
  }

  private def atPawnSite(ready: ReadyGame, player: PlayerId, id: EdificeId)
      : Boolean = PowerAccess.siteOf(ready, player, id)
    .exists(PowerAccess.pawnSite(ready, player).contains)

  private def outcome(state: ReadyGame, player: PlayerId)
      : Vector[CoreOperation] = {
    val total = RollResults.score(state, pool)
    if (total > 0) Vector(GainSupply(player, total))
    else Vector(EnterPhase(Phase.Rest))
  }
}
