package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.phases.RestCleanup
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Finish Rest (rest-walker spec, `FinishRest`).
  *
  * {{{
  * Sequence(
  *   BuildOps(cleanup)        // window = RestReturnFavor
  *   BuildOps(supplyRefresh)
  *   BuildOps(beginNextTurn))
  * }}}
  *
  * Every leaf derives its operations at walk time, so a power folded in
  * front of cleanup (League Treaty) changes what cleanup finds. The gate
  * holds for the whole procedure: nothing before `BeginTurn` leaves Rest, so
  * resume rebuilds through the same function.
  */
object FinishRestProcedure:
  val ExileSupply: SupplyRules = SupplyRules(SupplyTrack.Maximum, Vector(
    SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
    SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
    SupplyRefreshBand(InclusiveIntRange(0, 3), 4)))

  private val notes = NoteSet(ProcedureNotes.id(PhaseTransitionRef.FinishRest))
  /** "Increased supply from 5 to 7". Both numbers are plain, as the rule
    * this replaced wrote them into one `Text` span. */
  val supplyIncreased: NoteKey = notes.key("supply-increased", Vector(
    NotePart.Text("Increased supply from "), NotePart.Arg(0),
    NotePart.Text(" to "), NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared

  def gate(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Either[OathViolation, ReadyGame] =
    val current = ready.game.current
    if current.result.nonEmpty then Left(GameEnded)
    else if current.turn.activePlayer != player then
      Left(WrongPlayer(current.turn.activePlayer, player))
    else BeginRestProcedure.validateSupportedState(catalog, ready)
      .map(_ => ready)

  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Finish Rest selects nothing"))
    _ <- gate(catalog, ready, player)
  yield Sequence(Vector(
    BuildOps((state, _) => RestCleanup.operations(catalog, state, player),
      window = Some(PowerWindow.RestReturnFavor)),
    BuildOps((state, _) => supplyRefresh(state, player)),
    // Between the refresh and the hand-over, so `previous` is the refresh.
    // The rule this replaced matched only a `GainSupply`, so a refresh that
    // spent Supply posted nothing; requiring a rise reads the same.
    Note(ProcedureNotes.id(PhaseTransitionRef.FinishRest),
      supplyNote(player)),
    BuildOps((state, _) => beginNextTurn(state, player))))

  private def supplyNote(resting: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap { (before, after) =>
    val was = supplyOf(before, resting)
    val now = supplyOf(after, resting)
    Option.when(now > was)(supplyIncreased.sourceless(
      NoteArg.Number(was), NoteArg.Number(now)))
  }

  private def supplyOf(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.supply.supply)

  /** The warbands the resting player's own bank holds right now -- the one
    * count both the Supply band and its preview are read from, so neither
    * can be derived a different way and disagree with the other. The count
    * itself is `PlayerFacts.banked`, which the table's Supply overlay also
    * reads; only the guard on an unbounded supply is Rest's.
    */
  private def bankedWarbands(ready: ReadyGame, player: PlayerState)
      : Either[OathViolation, Int] =
    val kind = ForceKind.Exile(player.lineage)
    Either.cond(ready.banks.warbandSupply.contains(kind),
      PlayerFacts.banked(ready, kind),
      UnsupportedRestState(s"no bounded warband supply for $kind"))

  private def restingPlayer(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, PlayerState] =
    ready.game.current.players.find(_.player == resting)
      .toRight(UnsupportedRestState(s"unknown resting player $resting"))

  /** The Supply the resting player would end Rest holding, from the warbands
    * their bank holds right now. Read before Rest it is a preview: a power
    * folded in front of cleanup can still move warbands and change the band.
    */
  def supplyAfterRest(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Int] = for
    player <- restingPlayer(ready, resting)
    banked <- bankedWarbands(ready, player)
    refreshed <- ExileSupply.refresh(banked, player.board.supply.supply)
      .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
  yield refreshed.supply

  /** The Supply the resting player's band returns, BEFORE the track's
    * ceiling takes its cut -- the band's base, which is what Rest adds to
    * whatever is left unspent.
    *
    * This is the number the client shows on the button that ends the Act.
    * The capped gain (`supplyAfterRest` minus the track) would shrink as the
    * player saved Supply and read as a reason to spend nothing; the band
    * does not move with the track, so it reads as the Supply this Act may
    * still spend without costing the player anything at Rest.
    */
  def supplyGainAtRest(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Int] = for
    player <- restingPlayer(ready, resting)
    banked <- bankedWarbands(ready, player)
    base <- ExileSupply.baseSupplyFor(banked)
      .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
  yield base

  private def supplyRefresh(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = for
    player <- restingPlayer(ready, resting)
    refreshed <- supplyAfterRest(ready, resting)
  yield
    val change = refreshed - player.board.supply.supply
    if change > 0 then Vector(GainSupply(resting, change))
    else if change < 0 then Vector(SpendSupply(resting, -change))
    else Vector.empty

  private def beginNextTurn(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val order = turnOrder(ready)
    val index = order.indexOf(resting)
    if index < 0 then Left(UnsupportedRestState(s"$resting is not in turn order"))
    else if index == order.size - 1 then
      Right(Vector(BeginTurn(order.head, Phase.RoundEnd)))
    else Right(Vector(BeginTurn(order(index + 1), Phase.Wake)))

  def turnOrder(ready: ReadyGame): Vector[PlayerId] =
    val participants = ready.game.current.players.map(_.player)
    val index = participants.indexOf(ready.setup.firstPlayer)
    participants.drop(index) ++ participants.take(index)
