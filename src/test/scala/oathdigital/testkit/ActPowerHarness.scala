package oathdigital.testkit

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions,
  WalkerCompleted, WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Drives a phase power by its id through the rules, with fixed dice and a
  * journal replay check. The power suites of every phase use it. The acting
  * player is the quiet table's p1, in the Act unless a suite moves the turn.
  */
object ActPowerHarness:
  private val actor: PlayerId = Table.p1

  /** Rules with every production phase power. Dice fail loudly unless given,
    * so a power that must not roll proves it by not asking.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable): OathRules =
    new OathRules(catalog,
      phasePowerCatalog = PhasePowerCatalog.default(catalog),
      walkerDice = dice)

  /** `rules` with the production walker powers too, so the printed operation
    * restrictions hold: a suite about a power that the search prunes by them
    * needs this. */
  def rulesWithPrintedRestrictions(dice: WalkerDice = WalkerDice.unavailable)
      : OathRules =
    new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      phasePowerCatalog = PhasePowerCatalog.default(catalog),
      walkerDice = dice)

  /** The parked decision, rebuilt with the powers
    * `rulesWithPrintedRestrictions` carries: a decision the walker powers
    * shape, such as a reroll choice, is found only with them. */
  val parkedWithWalkerPowers = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** Stub of WalkerDice: the defense dice show exactly these faces, a defense
    * pool of another size fails, and no attack die may be rolled. */
  def defenseDice(faces: DefenseDieFace*): WalkerDice =
    WalkerDiceFixture.dice(defense = faces.toVector)

  /** Stub of WalkerDice: the attack dice show exactly these faces, an attack
    * pool of another size fails, and no defense die may be rolled. */
  def attackDice(faces: AttackDieFace*): WalkerDice =
    WalkerDiceFixture.dice(attack = faces.toVector)

  /** The actor's Act. */
  def act(ready: ReadyGame): ReadyGame =
    Table.from(ready).turn(actor, Phase.Act).unchecked

  def use(rules: OathRules, ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.UsePower(id), actor,
      Vector(source))

  def answer(rules: OathRules, state: OathState, decisionId: String,
      ref: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  /** Ids of the phase powers the actor can use now. */
  def usableIds(ready: ReadyGame): Vector[PowerId] =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty).map(_.power.id)

  /** The state a journal replay of `events` reaches from `from`; fails with
    * the violation that stopped it. */
  def replayed(rules: OathRules, from: ReadyGame, events: Vector[OathEvent])(
      using munit.Location): ReadyGame =
    val replay = events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))
    Outcomes.ready(Unwrap.accepted(replay))

  /** The events up to the first walk's end, before the driver goes on: a
    * power that ends the Act phase leaves the player in Rest here, where the
    * driver then finishes Rest when no REST power is usable. */
  def untilWalkEnds(events: Vector[OathEvent]): Vector[OathEvent] =
    events.take(events.indexWhere(_.isInstanceOf[WalkerCompleted]) + 1)

  /** The actor's secrets, faceup and facedown together. */
  def allSecrets(state: ReadyGame): Int =
    val look = Look(state)
    look.faceUpSecrets(actor) + look.faceDownSecrets(actor)
