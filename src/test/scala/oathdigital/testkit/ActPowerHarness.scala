package oathdigital.testkit

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Drives an Act phase power by its id through the rules, with fixed dice
  * and a journal replay check: the harness of the power suites in every
  * phase. The acting player is the quiet table's p1.
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

  /** Dice that show exactly these defense faces. */
  def defenseDice(faces: DefenseDieFace*): WalkerDice =
    WalkerDiceFixture.dice(defense = faces.toVector)

  /** Dice that show exactly these attack faces. */
  def attackDice(faces: AttackDieFace*): WalkerDice =
    WalkerDiceFixture.dice(attack = faces.toVector)

  /** The actor's Act. */
  def act(ready: ReadyGame): ReadyGame =
    Table.from(ready).turn(actor, Phase.Act).unchecked

  def use(rules: OathRules, ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.UsePower(id), actor,
      Vector.empty, Vector(source))

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
    Outcomes.ready(Unwrap.accepted(events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(from))) {
      case (state, event) => state.flatMap(rules.evolve(_, event))
    }))

  /** The actor's secrets, faceup and facedown together. */
  def allSecrets(state: ReadyGame): Int =
    val look = Look(state)
    look.faceUpSecrets(actor) + look.faceDownSecrets(actor)
