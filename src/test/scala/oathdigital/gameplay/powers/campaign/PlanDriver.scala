package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.CampaignFixture.Board
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.{NoteText, PowerImplementationStatus,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerDice, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.present

/** Drives a Campaign through the rules with the production battle plans, for
  * the suites that test one plan through a whole Campaign. Every attack die
  * is a sword (`winningDice`) or half a sword (`losingDice`) and every
  * defense die is blank, whatever the pool holds, so a suite chooses who
  * wins by the force it commits: a Conquest against two bandit or player
  * warbands is won by four swords and lost by four hollow ones.
  */
object PlanDriver:
  /** Stub of WalkerDice, the shared WalkerDiceFixture.raid: every attack die
    * is a sword and every defense die blank. */
  val winningDice: WalkerDice = WalkerDiceFixture.raid

  /** Stub of WalkerDice, the shared WalkerDiceFixture.blanks: every attack die
    * is half a sword and every defense die blank. */
  val losingDice: WalkerDice = WalkerDiceFixture.blanks

  /** These denizens, by name, after checking that no power of theirs is
    * implemented yet: held as advisers, they offer no battle plan of their
    * own. */
  def planless(names: String*)(using munit.Location): Vector[DenizenId] =
    val implemented = PowerImplementationStatus.implemented(catalog)
    names.toVector.map { name =>
      val id = CatalogNames.denizen(name)
      val card = catalog.denizens.find(_.id.value == id.value).get
      assert(card.powers.forall(power => !implemented(power.id)),
        s"$name has an implemented power, so it may offer a battle plan")
      id
    }

  /** One planless denizen, by name. */
  def planlessCard(name: String)(using munit.Location): DenizenId =
    planless(name).head

  export oathdigital.testkit.Outcomes.ready

  /** The notes `power` journaled in `events`, in order, as plain sentences. */
  def said(power: ContributingPower, events: Vector[OathEvent])
      : Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  /** The parked decision, as this file's suites rebuild it: the same catalog
    * and walker power catalog `CampaignFixture.rules(dice)`
    * builds `OathRules` with.
    */
  val parked = new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog))

  /** Whether the run is parked on `id`, awaiting `who`. */
  def awaits(run: Run, who: PlayerId, id: String): Boolean =
    run.parkedHere.parkedDecision(run.state).exists(facts =>
      facts.decision == id && facts.awaiting == who)

  /** A Campaign in progress: the transition it reached and every event so far.
    * `powers` are the walker powers `game` was built with, which the parked
    * decision is rebuilt with: the production catalog unless a suite runs its
    * own. */
  final case class Run(game: OathRules, transition: OathTransition,
      events: Vector[OathEvent],
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog)):
    def state: OathState = transition.state

    /** The parked decision, rebuilt with the run's own powers. */
    def parkedHere: ParkedDecisionAssertions =
      new ParkedDecisionAssertions(catalog, powers)

    /** The Campaign's recorded result; fails before the Campaign ends. */
    def result(using munit.Location): CampaignResult =
      present(ready(state).game.current.lastCampaignResult, "a Campaign result")

    /** Whether the attacker won, once the Campaign has ended. */
    def winner(using munit.Location): Option[Boolean] =
      ready(state).game.current.lastCampaignResult.map(_.attackerWins)

    /** The operations recorded so far, in order. */
    def ops: Vector[CoreOperation] = events.collect {
      case step: WalkerStepRecorded => step.ops }.flatten

    /** The operations recorded since `earlier`. */
    def since(earlier: Run): Vector[CoreOperation] = ops.drop(earlier.ops.size)

    def answer(who: PlayerId, id: String, answer: DecisionAnswer): Run =
      game.resolveWalker(state, who, id, answer).fold(
        error => throw new IllegalStateException(s"$id was refused: $error"),
        next => copy(transition = next, events = events ++ next.events))

    def pick(who: PlayerId, id: String, ref: DecisionOptionRef): Run =
      answer(who, id, ChooseOneAnswer(ref))

    /** `attacker` sacrifices no warband. */
    def declineSacrifice(attacker: PlayerId): Run =
      answer(attacker, CampaignIds.sacrifice, ChooseAmountAnswer(0))

    /** `attacker` chooses no more plans: Finish in the attacker's window, when
      * it is still open (a plan chosen last can close it). */
    def finishAttackerPlans(attacker: PlayerId): Run =
      if awaits(this, attacker, CampaignIds.attackerPlan) then
        pick(attacker, CampaignIds.attackerPlan, CampaignIds.finish)
      else this

    /** `defender` chooses no more plans: Finish in the defender's window. */
    def finishDefenderPlans(defender: PlayerId): Run =
      pick(defender, CampaignIds.defenderPlan, CampaignIds.finish)

    /** The parked decision's query, as `actor`'s Campaign builds it with
      * `catalogPowers`: the production catalog unless a suite runs its own.
      */
    def query(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : DecisionQuery =
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      // The run's own powers: a selected modifier folds only when chosen.
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowers.selected(catalogPowers, Vector.empty)).head.query

    /** The options the parked choice shows. */
    def options(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Vector[DecisionOption] = query(actor, catalogPowers) match
      case DecisionQuery.ChooseOne(options, _) => options
      case _ => Vector.empty

    /** The options the parked choice offers that may be chosen. */
    def offered(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Vector[DecisionOptionRef] =
      options(actor, catalogPowers).filter(_.available).map(_.ref)

    /** Why the parked choice shows `ref` without offering it. */
    def withheld(actor: PlayerId, ref: DecisionOptionRef,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Option[OptionReason] =
      options(actor, catalogPowers).collectFirst {
        case shown: DecisionOption.Unavailable if shown.ref == ref =>
          shown.reason
      }

    /** Whether the run is parked on the plan decision `id`, awaiting `who`,
      * with `ref` among its options. `actor` is the Campaign's actor, which
      * the parked decision is rebuilt with. */
    def offers(actor: PlayerId, who: PlayerId, id: String,
        ref: DecisionOptionRef): Boolean =
      awaits(this, who, id) && offered(actor).contains(ref)

    def refused(who: PlayerId, id: String, answer: DecisionAnswer)
        : Option[OathViolation] =
      game.resolveWalker(state, who, id, answer).left.toOption

    /** Finishes every plan window, sacrifices nothing and places nothing, until
      * the Campaign ends or asks something else.
      */
    def finishPlanWindows: Run = parkedHere.parkedDecision(state) match
      case Some(facts) => facts.decision match
        case CampaignIds.attackerPlan | CampaignIds.defenderPlan =>
          answer(facts.awaiting, facts.decision,
            ChooseOneAnswer(CampaignIds.finish)).finishPlanWindows
        case CampaignIds.sacrifice | CampaignIds.placement =>
          answer(facts.awaiting, facts.decision, ChooseAmountAnswer(0))
            .finishPlanWindows
        case _ => this
      case None => this

  /** Starts a Campaign, chooses a Raid when asked and `raid` is set, answers the
    * optional targets (when asked) and the force.
    */
  def commit(game: OathRules, b: Board, force: Int,
      targets: Vector[DecisionOptionRef] = Vector.empty,
      raid: Boolean = false,
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog)): Run =
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .fold(error => throw new IllegalStateException(s"no start: $error"),
        identity)
    committed(game, started, b, force, targets, raid, powers)

  /** The same, for a Campaign `started` some other way: a taken follow-up
    * offer, say. */
  def committed(game: OathRules, started: OathTransition, b: Board,
      force: Int, targets: Vector[DecisionOptionRef] = Vector.empty,
      raid: Boolean = false,
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog)): Run =
    val run = Run(game, started, started.events, powers)
    val kind =
      if awaits(run, b.actor, CampaignIds.kind) then run.pick(b.actor,
        CampaignIds.kind, DecisionOptionRef.Button(if raid then "raid" else "conquest"))
      else run
    val asked =
      if awaits(kind, b.actor, CampaignIds.targets) then
        kind.answer(b.actor, CampaignIds.targets, ChooseManyAnswer(targets))
      else kind
    asked.answer(b.actor, CampaignIds.force, ChooseAmountAnswer(force))
