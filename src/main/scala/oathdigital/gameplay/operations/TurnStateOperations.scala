package oathdigital.gameplay.operations

import oathdigital.model._

/** Supply, the Visions Drawn track, dice pools and roll outcomes, and the
  * turn, phase and title writes.
  *
  * Only supply and the Visions Drawn track carry a shape guard here. The
  * other operations are guarded at mutation time alone: whether a phase may
  * be entered or a title may change is a fact about the turn they run in,
  * and a shape guard would read the same state the mutation reads.
  */
private[operations] object TurnStateOperations:
  import OperationError._
  import OperationStateAdapter.playerState
  import OperationStateWrites.updatePlayer

  // ------------------------------------------------------------------
  // Guards
  // ------------------------------------------------------------------

  /** Threads a running per-player supply through one operation's leaves so a
    * batch of spends within one operation is checked cumulatively.
    */
  def supplyViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      supply: Map[PlayerId, Int]
  ): (Vector[OperationError], Map[PlayerId, Int]) =
    playerState(ready, player) match
      case Left(error) => (Vector(error), supply)
      case Right(_) =>
        val current = supply.getOrElse(player, 0)
        if amount < 0 then
          val required = -amount
          if current >= required then
            (Vector.empty, supply.updated(player, current - required))
          else (Vector(InsufficientSupply(required, current)), supply)
        else (Vector.empty, supply.updated(player,
          math.min(SupplyTrack.Maximum, current + amount)))

  def visionsDrawnViolation(ready: ReadyGame): Vector[OperationError] =
    Option.when(ready.game.current.tracks.visionsDrawn == Int.MaxValue)(
      VisionsDrawnOverflow: OperationError).toVector

  // ------------------------------------------------------------------
  // Mutations
  // ------------------------------------------------------------------

  def advanceVisionsDrawn(ready: ReadyGame): Either[OperationError, ReadyGame] =
    // Overflow is the guard's (visionsDrawnViolation).
    val current = ready.game.current
    Right(ready.copy(game = ready.game.copy(current = current.copy(
      tracks = current.tracks.copy(
        visionsDrawn = current.tracks.visionsDrawn + 1)))))

  def recordCampaignResult(ready: ReadyGame, fact: CampaignResult): ReadyGame =
    ready.updateCurrent(_.copy(lastCampaignResult = Some(fact)))

  def queueProcedure(ready: ReadyGame, entry: QueuedProcedure,
      atFront: Boolean): ReadyGame =
    ready.updateCurrent(current => current.copy(queue =
      if atFront then entry +: current.queue else current.queue :+ entry))

  def adjustSupply(ready: ReadyGame, player: PlayerId,
      amount: Int): Either[OperationError, ReadyGame] =
    // Sufficiency is the guard's (supplyViolation).
    playerState(ready, player).flatMap { state =>
      val current = state.board.supply.supply
      val next = if amount < 0 then current + amount
        else math.min(SupplyTrack.Maximum, current + amount)
      updatePlayer(ready, player)(value => value.copy(
        board = value.board.copy(supply = SupplyTrack(next))))
    }

  /** Adds `delta` dice to a named pool's count in `rollPools` state. The
    * count must not go below zero; this slice's trees only raise it (the
    * defensive guard below is a mutation-time floor — shape-level dice-pool
    * validation belongs to a later task that defines underflow semantics).
    */
  def adjustDicePool(ready: ReadyGame, pool: PoolKey,
      delta: Int): Either[OperationError, ReadyGame] =
    val pools = ready.game.current.rollPools
    val current = pools.get(pool).fold(0)(_.count)
    val next = current + delta
    require(next >= 0,
      s"dice pool '${pool.value}' count must not go below zero")
    Right(ready.updateCurrent(state => state.copy(
      rollPools = pools.updated(pool, DicePoolState(next)))))

  /** An upsert: a pool that never rolled starts from an empty outcome, so a
    * step that has no roll (a pool of zero dice is never rolled) can still
    * record a result. Fields left `None` are unchanged.
    */
  def modifyRollOutcome(ready: ReadyGame, pool: PoolKey,
      skulls: Option[Int], score: Option[Int]): ReadyGame =
    val outcomes = ready.game.current.rollOutcomes
    val base = outcomes.getOrElse(pool, RollOutcome(pool, 0, Vector.empty, 0, 0))
    ready.updateCurrent(current => current.copy(rollOutcomes = outcomes.updated(
      pool, base.copy(skulls = skulls.getOrElse(base.skulls),
        score = score.getOrElse(base.score)))))

  /** A set add, so recording a use the turn already holds changes nothing.
    * The turn's used-power set is cleared wholesale when the turn advances,
    * which is why nothing here has to expire anything.
    */
  def recordPowerUse(ready: ReadyGame, power: PowerUseRef): ReadyGame =
    ready.updateCurrent(current => current.copy(turn = current.turn.copy(
      usedPowers = current.turn.usedPowers + power)))

  /** Appends `ops` under `key`, as the journal records them, so a game
    * reloaded from its events holds the same ops as the live one. */
  def defer(ready: ReadyGame, key: DeferralKey,
      ops: Vector[CoreOperation]): ReadyGame =
    val held = ready.game.current.walkerDeferred
    ready.updateCurrent(current => current.copy(walkerDeferred = held.updated(
      key, held.getOrElse(key, Vector.empty) ++
        ops.map(OperationRun.canonical))))

  /** Drops what `key` holds; a key that holds nothing changes nothing. */
  def takeDeferred(ready: ReadyGame, key: DeferralKey): ReadyGame =
    ready.updateCurrent(current =>
      current.copy(walkerDeferred = current.walkerDeferred - key))

  /** Records the site the walk's actor acts as if at; the walker clears it
    * when the walk ends. */
  def actAsIf(ready: ReadyGame, site: SiteId): ReadyGame =
    ready.updateCurrent(_.copy(walkerAsIfSite = Some(site)))

  /** Opens the record of a pick, closing any still open, so payments from
    * here on are added to it. */
  def openPick(ready: ReadyGame, decisionId: String,
      option: DecisionOptionRef, label: Option[String]): ReadyGame =
    closePick(ready).updateCurrent(current => current.copy(walkerPicks =
      current.walkerPicks :+ PlayedPick(decisionId, option, label)))

  /** Closes the open pick record, if any. */
  def closePick(ready: ReadyGame): ReadyGame =
    ready.updateCurrent(current => current.copy(walkerPicks =
      current.walkerPicks.map(_.copy(open = false))))

  /** Adds every `PayCost` and `Sacrifice` inside `operation` to the open
    * pick record. With none open, or nothing paid, the state is unchanged.
    * Each is read where it stands in the tree, before it flattens to its
    * moves. */
  def tallyPayments(ready: ReadyGame, operation: CoreOperation): ReadyGame =
    def payments(node: Operation): Vector[OptionPrice] = node match
      case PayCost(_, _, cost, _, _, _) => Vector(OptionPrice(cost.favor,
        cost.secret, cost.favorBurnt, cost.secretBurnt))
      case Sacrifice(_, warbands, _) =>
        Vector(OptionPrice(warbands = warbands.amount))
      case _: LeafOperation => Vector.empty
      case _ => node.children.flatMap(payments)
    val paid = payments(operation)
    val picks = ready.game.current.walkerPicks
    if paid.isEmpty || !picks.lastOption.exists(_.open) then ready
    else ready.updateCurrent(_.copy(walkerPicks = picks.init :+
      picks.last.copy(paid = paid.foldLeft(picks.last.paid)(added))))

  private def added(total: OptionPrice, more: OptionPrice): OptionPrice =
    OptionPrice(total.favor + more.favor, total.secrets + more.secrets,
      total.favorBurnt + more.favorBurnt,
      total.secretsBurnt + more.secretsBurnt,
      total.warbands + more.warbands)

  /** A write, not an advance: which phase may follow which belongs to the
    * procedure that declared the operation, and is deliberately not restated
    * here. The one thing this does reject is a transition to the phase the
    * turn is already in, which is a corrupt or doubled journal rather than a
    * rule about order.
    */
  def enterPhase(ready: ReadyGame,
      phase: Phase): Either[OperationError, ReadyGame] =
    Either.cond(ready.game.current.turn.phase != phase, ready.updateCurrent(
      current => current.copy(turn = current.turn.copy(phase = phase))),
      PhaseAlreadyEntered(phase))

  def setOathkeeper(ready: ReadyGame,
      holder: Option[PlayerId]): Either[OperationError, ReadyGame] =
    Either.cond(ready.game.current.title.holder != holder,
      ready.updateCurrent(current => current.copy(
        title = OathkeeperState(holder, TitleSide.Oathkeeper))),
      OathkeeperUnchanged(holder))

  def setOathkeeperGoal(ready: ReadyGame,
      goal: OathkeeperGoal): Either[OperationError, ReadyGame] =
    Either.cond(ready.game.campaign.oathkeeperGoal != goal,
      ready.updateCampaign(_.copy(oathkeeperGoal = goal)),
      OathkeeperGoalUnchanged(goal))

  def beginTurn(ready: ReadyGame, player: PlayerId,
      phase: Phase): Either[OperationError, ReadyGame] =
    if !ready.game.current.players.exists(_.player == player) then
      Left(UnknownPlayer(player))
    else if phase != Phase.Wake && phase != Phase.RoundEnd then
      Left(InvalidTurnPhase(phase))
    else Right(ready.updateCurrent(current =>
      current.copy(turn = TurnState(player, phase, Set.empty))))
