package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Gambling Hall (card 93), ACTION: place 2 favor on this card, roll 4
  * defense dice, and when the total X is above zero choose any favor bank
  * (even an empty one) and take the smaller of X and its stock.
  *
  * The tree is `ModifyDicePool`, an automatic `Roll`, a `Branch` that holds
  * only the bank decision, and a `BuildOps` that reads the answer. The
  * `Branch` is the walker's "live decision" shape: it sits after the roll that
  * wrote the state it reads, so it selects the same decision again when the
  * walker resumes against the state stored at the park. `Gain.Favor` is
  * optional, so it reduces to what the bank holds.
  */
case object GamblingHall extends PaidAction("denizen.gambling-hall",
    Cost(favor = 2)) {
  val Dice: Int = 4
  val pool: PoolKey = PoolKey("gambling-hall")
  val decisionId: String = "gambling-hall.bank"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    ModifyDicePool(pool, Dice),
    Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
    Branch((state, _) => {
      val total = RollResults.score(state, pool)
      if (total > 0) Vector(choice(player, total)) else Vector.empty
    }),
    BuildOps((state, pending) => take(state, player, pending)))))

  /** The bank question's copy. The roll has already happened and the whole
    * total is taken, so the heading states the number rather than offering
    * it as a ceiling the player chooses under.
    */
  def bankHeading(total: Int): String = s"Gain $total favor from one bank"

  private def choice(player: PlayerId, total: Int): Decide =
    Decide(decisionId, player, DecisionQuery.ChooseOne(Suit.all.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(bankHeading(total))))

  private def take(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val total = RollResults.score(state, pool)
    if (total <= 0) Right(Vector.empty)
    else pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(suit)), _) => suit
    }.toRight(OathViolation.InvalidEventOrder(
      "no Gambling Hall bank is recorded")).map(suit =>
      Vector(Gain.Favor(player, suit, total)))
  }
}
