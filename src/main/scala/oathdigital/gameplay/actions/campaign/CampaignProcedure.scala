package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.dice.RollStep
import oathdigital.gameplay.walker.{ModifierChoice, WalkerRollFeedback}
import oathdigital.model._

/** Campaign on the walker, in the rulebook's order: choose the kind and the
  * targets, commit force and gather both dice pools, use battle plans, roll
  * the attack, sacrifice, roll the defense, declare the victor, kill the
  * defeated warbands, resolve the victory.
  *
  * Each early decision is built when reached from live state that nothing
  * before it has changed, plus the answers. Gathering the pools pins the kind
  * and the defender (`CampaignSetup.pin`), because the battle plans that
  * follow may change who rules the targets. After the losses run, every later
  * step reads the durable `CampaignResult` and the answers instead, because
  * the losses change the board.
  *
  * A Campaign ends before its force is committed when the attacker skips it
  * or nothing is left to attack (`endStep`).
  */
object CampaignProcedure:
  /** Every decision a Campaign asks starts with this, whether the engine or a
    * power asks it, so a parked one is always a Campaign decision.
    */
  val decisionPrefix: String = "campaign."

  /** "Start Campaign": the modifier decision's button. */
  val startLabel: String = "Start Campaign"

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- startArgs(state, actor, args)
    _ <- afterModifiers(catalog, state, actor)
  yield tree(catalog, state, actor)

  /** Why a Campaign cannot start with no site to conquer and no pawn to
    * raid. */
  val NothingToAttack: String = "Campaign needs a ruled pawn site to " +
    "Conquest or a co-located enemy pawn to Raid"


  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    startArgs(state, actor, args).map(_ => tree(catalog, state, actor))

  /** The Campaign itself, behind the start's gates. */
  private def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    _ <- Either.cond(CampaignSetup.legalKinds(state, actor).nonEmpty, (),
      OathViolation.CampaignUnavailable(NothingToAttack))
  yield battle(catalog, state, actor)

  /** The modifier decision, then the Campaign. No power selects at
    * `CampaignModifierSelection` yet, so the decision asks nothing. Battle
    * plans are not modifiers: the Campaign asks for them itself. */
  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation =
    val rest = battle(catalog, state, actor)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.CampaignModifierSelection, rest, startLabel), rest))

  /** None, or the one site or player a power's queued entry names: its
    * origin, or its declared defender. */
  private def startArgs(state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Unit] =
    args match
      case Vector() => Right(())
      case Vector(DecisionOptionRef.Site(site))
          if CampaignSetup.queuedOrigin(state, actor).contains(site) =>
        Right(())
      case Vector(DecisionOptionRef.Player(player))
          if CampaignSetup.queuedDefender(state, actor).contains(player) =>
        Right(())
      case _ => Left(OathViolation.InvalidEventOrder(
        s"walker procedure ${ActionRef.Campaign.key} takes no start " +
          "selection but a power's queued origin or defender, got " +
          args.map(_.kind).mkString(", ")))

  private def battle(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, CampaignIds.SupplyCost)),
      Some(PowerWindow.CampaignCost)),
    Sequence(Vector[Operation](kindStep(actor), defenderStep(actor),
      targetsStep(catalog, actor), endStep(actor)),
      Some(PowerWindow.CampaignBeforeTargets)),
    forceStep(catalog, state, actor),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup => CampaignSetup.pin(setup) +:
        CampaignBattle.gatherPools(catalog, setup)))),
      Some(PowerWindow.CampaignGatherPools)),
    CampaignPlanSteps.attacker(catalog, actor),
    CampaignPlanSteps.defender(catalog, actor),
    RollStep(CampaignIds.attackPool, DiceSpec(DiceKind.Attack), Some(actor),
      Some(PowerWindow.CampaignAttackRoll)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.attackResultOps(ready, setup)))),
      Some(PowerWindow.CampaignAttackResult)),
    sacrificeStep(actor),
    Branch((ready, pending) => Vector(RollStep(CampaignIds.defensePool,
      DiceSpec(DiceKind.Defense), CampaignSetup.setup(ready, actor, pending)
        .map(_.defender).collect { case CampaignDefender.Player(player) =>
          player }, Some(PowerWindow.CampaignDefenseRoll)))),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.defenseResultOps(ready, setup)))),
      Some(PowerWindow.CampaignDefenseResult)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup => Vector(RecordCampaignResult(
        CampaignBattle.result(ready, setup, pending)))))),
      Some(PowerWindow.CampaignAfterOutcome)),
    outcomeStep(catalog, actor)),
    Some(PowerWindow.CampaignActionEligibility))

  private def withSetup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)(
      f: CampaignSetup => Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    CampaignSetup.setup(ready, actor, pending).map(f).toRight(
      OathViolation.InvalidEventOrder(
        "Campaign reached a battle step without a complete setup"))

  /** The sacrifice question is asked about the attack roll, and the placement
    * and relocation questions are asked after the defense roll, so each shows
    * the roll it is about. A Campaign has no target: the two scores are
    * compared to each other, not to a difficulty.
    */
  def rollFeedback(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, decisionId: String): Option[WalkerRollFeedback] =
    decisionId match
      case CampaignIds.sacrifice =>
        val skulls = ready.game.current.rollOutcomes
          .get(CampaignIds.attackPool).fold(0)(_.skulls)
        Some(WalkerRollFeedback(CampaignIds.attackPool, detail =
          if skulls == 0 then Vector.empty
          else Vector(s"$skulls skull loss${if skulls == 1 then "" else "es"}")))
      case CampaignIds.placement | CampaignIds.relocation =>
        Some(WalkerRollFeedback(CampaignIds.defensePool))
      case _ => None

  /** Omitted when no force survives the skulls. */
  private def sacrificeStep(actor: PlayerId): Operation =
    Branch((ready, pending) => CampaignSetup.setup(ready, actor, pending)
      .filter(setup => CampaignBattle.sacrificeMax(ready, setup) > 0).map { setup =>
        val max = CampaignBattle.sacrificeMax(ready, setup)
        Vector[Operation](Decide(CampaignIds.sacrifice, actor,
          DecisionQuery.ChooseAmount(0, max,
            Some(CampaignBattle.sacrificeHeading(max,
              CampaignBattle.sacrificeWorth(ready))), "Sacrifice"),
          window = Some(PowerWindow.CampaignSacrificeSelection)))
      }.getOrElse(Vector.empty))

  /** Read from the durable result, never from the board: this is selected
    * again after the losses have changed it.
    */
  private def outcomeStep(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Branch((ready, _) => ready.game.current.lastCampaignResult match {
      case Some(result) => CampaignOutcome.steps(ready, catalog, actor, result)
      case None => Vector(BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
        "Campaign reached its outcome without a recorded result"))))
    })

  /** Asked when several kinds are legal, or when a power at the Campaign's
    * start offered the Skip, which is then the last option. Omitted when no
    * kind is legal: `endStep` then ends the Campaign. */
  private def kindStep(actor: PlayerId): Operation = Branch((ready, _) => {
    val kinds = CampaignSetup.kindOptions(ready, actor)
    val skip = CampaignSetup.skipOffered(ready)
    if kinds.isEmpty || (kinds.size < 2 && !skip) then Vector.empty
    else Vector(Decide(CampaignIds.kind, actor, DecisionQuery.ChooseOne(
      kinds ++ Option.when(skip)(DecisionOption.Button(CampaignIds.skip,
        "Skip Campaign")), heading = Some("Choose a Campaign")),
      window = Some(PowerWindow.CampaignKindSelection)))
  })

  /** Ends the Campaign when no kind is settled once its targets are chosen:
    * the attacker skipped, or a power at its start left nothing to attack.
    * The Supply stays spent. It is the last of the before-targets steps, so
    * no later step's place in the tree moves. */
  private def endStep(actor: PlayerId): Operation =
    Branch((ready, pending) =>
      if CampaignSetup.kindOf(ready, actor, pending).isEmpty then
        Vector(EndWalk)
      else Vector.empty)

  /** A Raid only, and only when several enemy pawns stand here. */
  private def defenderStep(actor: PlayerId): Operation = Branch((ready, pending) =>
    if !CampaignSetup.kindOf(ready, actor, pending)
        .contains(CampaignKind.Raid) then Vector.empty
    else {
      val defenders = CampaignSetup.defenderOptions(ready, actor)
      if defenders.size < 2 then Vector.empty
      else Vector(Decide(CampaignIds.defender, actor, DecisionQuery.ChooseOne(
        defenders, heading = Some("Choose whom to Raid"),
        confirmVerb = Some("Raid")),
        window = Some(PowerWindow.CampaignDefenderSelection)))
    })

  /** The optional additions to the mandatory target, which is shown locked,
    * with the two sums a player weighs and why every other target is not on
    * offer.
    */
  private def targetsStep(catalog: ExecutableCatalog, actor: PlayerId)
      : Operation = Branch((ready, pending) =>
    (for
      (kind, defender) <- CampaignSetup.declaredAt(ready, actor, pending)
      origin <- CampaignSetup.originOf(ready, actor)
      options = CampaignSetup.targetOptions(ready, actor, kind, defender)
      if options.nonEmpty
    yield
      val locked = CampaignTargets.lockedOf(kind, origin, defender).toVector
      Vector[Operation](Decide(CampaignIds.targets, actor,
        DecisionQuery.ChooseMany(0, options.size, options ++
          CampaignTargets.withheld(ready, actor, kind, origin, defender,
            options), heading = Some(kind match {
            case CampaignKind.Conquest =>
              "Also target these sites ruled by the same defender"
            case CampaignKind.Raid =>
              "Also target the defender's faceup relics and banners"
          }), locked = locked,
          tallies = CampaignTargets.tallies(catalog, ready,
            (locked ++ options).map(_.ref)),
          summary = Some(CampaignTargets.summary(kind)),
          confirm = Some(CampaignTargets.confirm(kind))),
        window = Some(PowerWindow.CampaignTargetSelection))))
      .getOrElse(Vector.empty))

  /** Always asked, even for zero: a Campaign with no force is legal, and the
    * confirmation states what it commits. A queued origin's warbands join
    * the board's, and the heading names both (slice 13).
    */
  private def forceStep(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation =
    val board = state.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)
    val atSite = CampaignSetup.atOrigin(state, actor)
    val warbands = board + atSite
    val split = CampaignSetup.queuedOrigin(state, actor)
      .filter(_ => atSite > 0).flatMap(catalog.site).fold("")(site =>
        s" ($board on your board, $atSite at ${site.name})")
    // Suggests the whole force: committing everything is what an attacker
    // nearly always wants, and holding warbands back is the deliberate act.
    Decide(CampaignIds.force, actor, DecisionQuery.ChooseAmount(0, warbands,
      Some(s"Commit warbands to the Campaign: 0 to $warbands$split, each " +
        "adds one attack die"), "Commit force", suggested = Some(warbands)),
      window = Some(PowerWindow.CampaignForceSelection))
