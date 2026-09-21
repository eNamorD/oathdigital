package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Alchemist (card 9), ACTION: place 1 secret on this card and burn 1, then
  * gain 4 favor from any bank or banks.
  *
  * The player chooses the split only when there is a choice: two or more
  * banks hold favor and more than 4 is available in all. Otherwise every
  * legal answer takes the same favor, so none is asked, which is also what
  * `DecisionQueries.wellFormed` requires of a `Distribute`. The decision is a
  * live `Branch`, read after the cost is paid, and the effect recomputes the
  * same condition from live state.
  */
case object Alchemist extends PaidAction("denizen.alchemist",
    Cost(secret = 1, secretBurnt = 1)) {
  val Favor: Int = 4
  val decisionId: String = "power.alchemist.banks"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => gain(live, player, pending)))))

  /** The banks that hold favor, with their stock, in suit order. */
  private def stocked(ready: ReadyGame): Vector[(Suit, Int)] =
    Suit.all.map(suit => suit -> ready.banks.favor.getOrElse(suit, 0))
      .filter(_._2 > 0)

  private def choosing(banks: Vector[(Suit, Int)]): Boolean =
    banks.size >= 2 && banks.map(_._2).sum > Favor

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val banks = stocked(ready)
    if (!choosing(banks)) Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      banks.map { case (suit, stock) => DistributeSlot(
        DecisionOptionRef.FavorBank(suit), 0, math.min(stock, Favor), None) },
      total = Favor,
      heading = Some("Alchemist: take 4 favor from any banks"),
      confirmLabel = "Take favor")))
  }

  private def gain(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val banks = stocked(ready)
    if (!choosing(banks)) Right(banks.map { case (suit, stock) =>
      Gain.Favor(player, suit, math.min(stock, Favor)) })
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).map(_.collect {
        case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) if n > 0 =>
          Gain.Favor(player, suit, n) })
  }
}
