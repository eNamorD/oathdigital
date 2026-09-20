package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Campaign on the walker, in the rulebook's order: choose the kind and the
  * targets, commit force and gather both dice pools, use battle plans, roll
  * the attack, sacrifice, roll the defense, declare the victor, kill the
  * defeated warbands, resolve the victory.
  *
  * Each early decision is built when reached from live state that nothing
  * before it has changed, plus the answers. After the losses run, every later
  * step reads the durable `CampaignResult` and the answers instead, because
  * the losses change the board.
  */
object CampaignProcedure {
  val decisionIds: Set[String] = CampaignIds.all

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(CampaignSetup.legalKinds(state, actor).nonEmpty, (),
      OathViolation.CampaignUnavailable("Campaign needs a ruled pawn site to " +
        "Conquest or a co-located enemy pawn to Raid"))
  } yield tree(catalog, state, actor)

  /** Whether Campaign could start now: the gates pass and the first walk (the
    * Supply cost, up to the first decision) is accepted, restrictions
    * included.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(catalog, state, actor))

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Campaign.key} takes no start selection, " +
        s"got ${args.map(_.kind).mkString(", ")}"))

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, CampaignIds.SupplyCost)),
      Some(PowerWindow.CampaignCost)),
    Sequence(Vector[Operation](kindStep(actor), defenderStep(actor),
      targetsStep(actor)), Some(PowerWindow.CampaignBeforeTargets)),
    forceStep(state, actor),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      CampaignSetup.setup(ready, actor, pending).toRight(
        OathViolation.InvalidEventOrder(
          "Campaign gathered its dice pools without a complete setup"))
        .map(CampaignBattle.gatherPools(catalog, _)))),
      Some(PowerWindow.CampaignGatherPools)),
    CampaignPlanSteps.attacker(catalog, actor),
    CampaignPlanSteps.defender(catalog, actor)),
    Some(PowerWindow.CampaignActionEligibility))

  /** Omitted when exactly one kind is legal. */
  private def kindStep(actor: PlayerId): Operation = Branch((ready, _) => {
    val kinds = CampaignSetup.kindOptions(ready, actor)
    if (kinds.size < 2) Vector.empty
    else Vector(Decide(CampaignIds.kind, actor, DecisionQuery.ChooseOne(kinds,
      heading = Some("Choose a Campaign")),
      window = Some(PowerWindow.CampaignKindSelection)))
  })

  /** A Raid only, and only when several enemy pawns stand here. */
  private def defenderStep(actor: PlayerId): Operation = Branch((ready, pending) =>
    if (!CampaignSetup.kindOf(ready, actor, pending)
        .contains(CampaignKind.Raid)) Vector.empty
    else {
      val defenders = CampaignSetup.defenderOptions(ready, actor)
      if (defenders.size < 2) Vector.empty
      else Vector(Decide(CampaignIds.defender, actor, DecisionQuery.ChooseOne(
        defenders, heading = Some("Choose whom to Raid")),
        window = Some(PowerWindow.CampaignDefenderSelection)))
    })

  /** The optional additions to the mandatory target. */
  private def targetsStep(actor: PlayerId): Operation = Branch((ready, pending) =>
    (for {
      kind <- CampaignSetup.kindOf(ready, actor, pending)
      defender <- CampaignSetup.defenderOf(ready, actor, pending, kind)
      options = CampaignSetup.targetOptions(ready, actor, kind, defender)
      if options.nonEmpty
    } yield Vector[Operation](Decide(CampaignIds.targets, actor,
      DecisionQuery.ChooseMany(0, options.size, options, heading = Some(kind match {
        case CampaignKind.Conquest =>
          "Also target these sites ruled by the same defender"
        case CampaignKind.Raid =>
          "Also target the defender's faceup relics and banners"
      })), window = Some(PowerWindow.CampaignTargetSelection))))
      .getOrElse(Vector.empty))

  /** Always asked, even for zero: a Campaign with no force is legal, and the
    * confirmation states what it commits.
    */
  private def forceStep(state: ReadyGame, actor: PlayerId): Operation = {
    val warbands = state.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)
    Decide(CampaignIds.force, actor, DecisionQuery.ChooseAmount(0, warbands,
      Some(s"Commit warbands to the Campaign: 0 to $warbands, each adds one " +
        "attack die"), "Commit force"),
      window = Some(PowerWindow.CampaignForceSelection))
  }
}
