package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** A move of `amount` favor out of `sources`, the favor banks it may come
  * from, for Alchemist, Memory of Nature and Town Meeting (catalog batch 3,
  * refactor P8).
  *
  * The player splits the favor only when there is a choice: two or more of
  * the banks hold favor and more than `amount` is available in all.
  * Otherwise every legal answer moves the same favor, so none is asked,
  * which is also what `DecisionQueries.wellFormed` requires of a
  * `Distribute`. The question is asked from a live `Branch`, read after the
  * cost is paid, and `split` recomputes the same condition from live state.
  */
private[powers] final class FavorSplit(decisionId: String,
    sources: Vector[Suit], confirmLabel: String):

  /** The source banks that hold favor, with their stock, in suit order. */
  def stocked(ready: ReadyGame): Vector[(Suit, Int)] =
    sources.map(suit => suit -> ready.banks.favor.getOrElse(suit, 0))
      .filter(_._2 > 0)

  private def choosing(banks: Vector[(Suit, Int)], amount: Int): Boolean =
    amount > 0 && banks.size >= 2 && banks.map(_._2).sum > amount

  /** The question, when there is a choice. */
  def ask(ready: ReadyGame, player: PlayerId, amount: Int, heading: String)
      : Vector[Operation] =
    val banks = stocked(ready)
    if !choosing(banks, amount) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      banks.map { case (suit, stock) => DistributeSlot(
        DecisionOptionRef.FavorBank(suit), 0, math.min(stock, amount), None) },
      total = amount,
      heading = Some(heading),
      confirmLabel = confirmLabel)))

  /** The favor each bank gives: the answer when one was asked, otherwise
    * all of it, up to `amount`, from each stocked bank. */
  def split(ready: ReadyGame, pending: PendingTree, amount: Int)
      : Either[OathViolation, Vector[(Suit, Int)]] =
    val banks = stocked(ready)
    if amount <= 0 then Right(Vector.empty)
    else if !choosing(banks, amount) then
      Right(banks.map { case (suit, stock) => suit -> math.min(stock, amount) })
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).map(_.collect {
        case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) if n > 0 =>
          suit -> n })
