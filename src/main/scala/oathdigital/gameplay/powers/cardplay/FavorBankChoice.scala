package oathdigital.gameplay.powers.cardplay

import oathdigital.model._

/** "Take favor from any one favor bank", for Vow of Obedience's REST and
  * Book Binders. The player chooses among the banks that hold favor; one
  * stocked bank is taken without asking, and none leaves nothing to do.
  */
private[cardplay] object FavorBankChoice:

  /** The banks that hold favor, in suit order. */
  def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  /** The operations that move up to `amount` favor from one stocked bank to
    * `player`'s play area, asking `player` with `decisionId` when several
    * banks are stocked. A bank holding less gives what it holds.
    */
  def take(ready: ReadyGame, player: PlayerId, amount: Int,
      decisionId: String, heading: String): Vector[Operation] =
    stocked(ready) match
      case Vector() => Vector.empty
      case Vector(only) => Vector(move(player, amount, _ => Right(only)))
      case several => Vector(
        Decide(decisionId, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some(heading))),
        move(player, amount, chosen(_, decisionId)))

  private def chosen(pending: PendingTree, decisionId: String)
      : Either[OathViolation, Suit] = pending.answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.FavorBank(suit)), _) => suit
  }.toRight(OathViolation.InvalidEventOrder(
    s"no favor bank is recorded for $decisionId"))

  private def move(player: PlayerId, amount: Int,
      bank: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((state, pending) => bank(pending).map { suit =>
      val taken = math.min(amount, state.banks.favor.getOrElse(suit, 0))
      if taken == 0 then Vector.empty
      else Vector(Move(Piece.Favor(taken),
        PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.PlayArea(player))))
    })
