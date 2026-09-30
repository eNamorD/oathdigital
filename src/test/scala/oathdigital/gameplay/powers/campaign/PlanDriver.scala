package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.CampaignFixture.Board
import oathdigital.gameplay.powers.{PowerImplementationStatus, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerDice, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** Drives a Campaign through the rules with the production battle plans, for the
  * suites that test one plan through a whole Campaign. Every attack die is a sword
  * (`winning`) or half a sword (`losing`) and every defense die is blank, whatever
  * the pool holds, so a suite chooses who wins by the force it commits: a Conquest
  * against two bandit or player warbands is won by four swords and lost by four
  * hollow ones.
  */
object PlanDriver:
  private def dice(attack: AttackDieFace): WalkerDice = (kind, count) =>
    Right(kind match {
      case DiceKind.Attack => Vector.fill(count)(attack: DieFace)
      case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
    })

  val winning: WalkerDice = dice(AttackDieFace.OneSword)
  val losing: WalkerDice = dice(AttackDieFace.HollowSword)

  /** `count` denizens of `suit` whose powers do nothing yet, to hold as
    * advisers without offering a plan of their own. */
  def inert(suit: Suit, count: Int): Vector[String] =
    val implemented = PowerImplementationStatus.implemented(catalog)
    catalog.denizens.filter(card => card.suit == suit &&
      card.handlers.forall(handler => !implemented(PowerId(handler))))
      .map(_.id.value).take(count)

  def ready(state: OathState): ReadyGame = state match
    case Ready(value) => value
    case other => throw new IllegalStateException(s"not a ready game: $other")

  def player(state: OathState, id: PlayerId): PlayerState =
    ready(state).game.current.players.find(_.player == id).get

  /** The parked decision, as this file's suites rebuild it: the same catalog
    * and walker power catalog `CampaignFixture.rules(dice, powers = true)`
    * builds `OathRules` with.
    */
  val parked = new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog))

  /** Whether the run is parked on `id`, awaiting `who`. */
  def awaits(run: Run, who: PlayerId, id: String): Boolean =
    parked.parkedDecision(run.state).exists(facts =>
      facts.decision == id && facts.awaiting == who)

  /** A Campaign in progress: the transition it reached and every event so far. */
  final case class Run(game: OathRules, transition: OathTransition,
      events: Vector[OathEvent]):
    def state: OathState = transition.state

    /** The operations recorded so far, in order. */
    def ops: Vector[CoreOperation] = events.collect {
      case step: WalkerStepRecorded => step.ops }.flatten

    /** The operations recorded since `earlier`. */
    def since(earlier: Run): Vector[CoreOperation] = ops.drop(earlier.ops.size)

    def answer(who: PlayerId, id: String, answer: DecisionAnswer): Run =
      game.resolveWalker(state, who, id, answer).fold(
        error => throw new IllegalStateException(s"$id was refused: $error"),
        next => Run(game, next, events ++ next.events))

    def pick(who: PlayerId, id: String, ref: DecisionOptionRef): Run =
      answer(who, id, ChooseOneAnswer(ref))

    /** The parked decision's query, as `actor`'s Campaign builds it. */
    def query(actor: PlayerId): DecisionQuery =
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowerCatalog.default(catalog)).head.query

    /** The options the parked choice offers. */
    def options(actor: PlayerId): Vector[DecisionOption] = query(actor) match
      case DecisionQuery.ChooseOne(options, _) => options
      case _ => Vector.empty

    def offered(actor: PlayerId): Vector[DecisionOptionRef] =
      options(actor).map(_.ref)

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
    def finish: Run = parked.parkedDecision(state) match
      case Some(facts) => facts.decision match
        case CampaignIds.attackerPlan | CampaignIds.defenderPlan =>
          answer(facts.awaiting, facts.decision,
            ChooseOneAnswer(CampaignIds.finish)).finish
        case CampaignIds.sacrifice | CampaignIds.placement =>
          answer(facts.awaiting, facts.decision, ChooseAmountAnswer(0)).finish
        case _ => this
      case None => this

  /** Starts a Campaign, chooses a Raid when asked and `raid` is set, answers the
    * optional targets (when asked) and the force.
    */
  def commit(game: OathRules, b: Board, force: Int,
      targets: Vector[DecisionOptionRef] = Vector.empty,
      raid: Boolean = false): Run =
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .fold(error => throw new IllegalStateException(s"no start: $error"),
        identity)
    val run = Run(game, started, started.events)
    val kind =
      if awaits(run, b.actor, CampaignIds.kind) then run.pick(b.actor,
        CampaignIds.kind, DecisionOptionRef.Button(if raid then "raid" else "conquest"))
      else run
    val asked =
      if awaits(kind, b.actor, CampaignIds.targets) then
        kind.answer(b.actor, CampaignIds.targets, ChooseManyAnswer(targets))
      else kind
    asked.answer(b.actor, CampaignIds.force, ChooseAmountAnswer(force))
