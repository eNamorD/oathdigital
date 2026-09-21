package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.CampaignFixture.Board
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerDice, WalkerStepRecorded}
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
object PlanDriver {
  private def dice(attack: AttackDieFace): WalkerDice = (kind, count) =>
    Right(kind match {
      case DiceKind.Attack => Vector.fill(count)(attack: DieFace)
      case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
    })

  val winning: WalkerDice = dice(AttackDieFace.OneSword)
  val losing: WalkerDice = dice(AttackDieFace.HollowSword)

  def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => throw new IllegalStateException(s"not a ready game: $other")
  }

  def player(state: OathState, id: PlayerId): PlayerState =
    ready(state).game.current.players.find(_.player == id).get

  def awaits(who: PlayerId, id: String): OathContinue =
    OathContinue.AwaitingCampaignDecision(who, DecisionId(id))

  /** A Campaign in progress: the transition it reached and every event so far. */
  final case class Run(game: OathRules, transition: OathTransition,
      events: Vector[OathEvent]) {
    def state: OathState = transition.state
    def continue: OathContinue = transition.continue

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
    def query(actor: PlayerId): DecisionQuery = {
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowerCatalog.default(catalog)).head.query
    }

    /** The options the parked choice offers. */
    def options(actor: PlayerId): Vector[DecisionOption] = query(actor) match {
      case DecisionQuery.ChooseOne(options, _) => options
      case _ => Vector.empty
    }

    def offered(actor: PlayerId): Vector[DecisionOptionRef] =
      options(actor).map(_.ref)

    def refused(who: PlayerId, id: String, answer: DecisionAnswer)
        : Option[OathViolation] =
      game.resolveWalker(state, who, id, answer).left.toOption

    /** Finishes every plan window, sacrifices nothing and places nothing, until
      * the Campaign ends or asks something else.
      */
    def finish: Run = continue match {
      case OathContinue.AwaitingCampaignDecision(who, DecisionId(id)) => id match {
        case CampaignIds.attackerPlan | CampaignIds.defenderPlan =>
          answer(who, id, ChooseOneAnswer(CampaignIds.finish)).finish
        case CampaignIds.sacrifice | CampaignIds.placement =>
          answer(who, id, ChooseAmountAnswer(0)).finish
        case _ => this
      }
      case _ => this
    }
  }

  /** Starts a Campaign, chooses a Raid when asked and `raid` is set, answers the
    * optional targets (when asked) and the force.
    */
  def commit(game: OathRules, b: Board, force: Int,
      targets: Vector[DecisionOptionRef] = Vector.empty,
      raid: Boolean = false): Run = {
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .fold(error => throw new IllegalStateException(s"no start: $error"),
        identity)
    val run = Run(game, started, started.events)
    val kind =
      if (run.continue == awaits(b.actor, CampaignIds.kind)) run.pick(b.actor,
        CampaignIds.kind, DecisionOptionRef.Button(if (raid) "raid" else "conquest"))
      else run
    val asked =
      if (kind.continue == awaits(b.actor, CampaignIds.targets))
        kind.answer(b.actor, CampaignIds.targets, ChooseManyAnswer(targets))
      else kind
    asked.answer(b.actor, CampaignIds.force, ChooseAmountAnswer(force))
  }
}
