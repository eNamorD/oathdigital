package oathdigital.gameplay.powers

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.protocol.projection.{DecisionQueryProjection, PlayerBoardProjection}
import oathdigital.testkit.Table

/** Staging and driving shared by the slice 1c suites. `PowerFixture` (slice
  * 1a) stays untouched so that parallel slices do not collide on it.
  */
object TargetsFixture:
  import PowerFixture._

  val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector.empty,
      restrictionSet = OperationRestrictions.forCatalog(catalog)),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val projector = new GameProjector(catalog)

  /** The parked decision, as this file's suites rebuild it: the same catalog
    * and phase power catalog `rules` was built with.
    */
  val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  def others(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.map(_.player).filter(_ != actor)

  def updatePlayer(ready: ReadyGame, id: PlayerId)(
      f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if p.player == id then f(p) else p)))

  def withSecrets(ready: ReadyGame, id: PlayerId, faceUp: Int,
      faceDown: Int): ReadyGame = updatePlayer(ready, id)(p => p.copy(
    board = p.board.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown)))

  def withPawn(ready: ReadyGame, id: PlayerId, site: SiteId): ReadyGame =
    updatePlayer(ready, id)(_.copy(pawnSite = Some(site)))

  /** `card` becomes an adviser of `owner`, taken from wherever it lay. */
  def giveAdviser(ready: ReadyGame, owner: PlayerId, card: DenizenId,
      orientation: Orientation): ReadyGame =
    Table.from(ready).adviser(owner, card,
      facedown = orientation == Orientation.FaceDown).unchecked

  def giveVision(ready: ReadyGame, owner: PlayerId, card: VisionId,
      orientation: Orientation): ReadyGame =
    Table.from(ready).adviser(owner, card,
      facedown = orientation == Orientation.FaceDown).unchecked

  /** Puts `owner`'s advisers at the bottom of the world deck. */
  def withoutAdvisers(ready: ReadyGame, owner: PlayerId): ReadyGame =
    val held = player(ready, owner).advisers.map(_.id).collect {
      case id: WorldCardId => id }
    updatePlayer(ready, owner)(_.copy(advisers = Vector.empty))
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck ++ held)))

  def use(ready: ReadyGame, power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.startWalker(
    OathState.Ready(ready), ActionRef.UsePower(power.id), actor, Vector.empty,
    Vector(source))

  def answer(from: OathTransition, by: PlayerId, decision: String,
      answered: DecisionAnswer): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, by, decision, answered)

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[OathState.Ready].value

  def awaits(transition: OathTransition, decision: String): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == decision && facts.awaiting == actor)

  def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  def usableNow(ready: ReadyGame) = PhasePowerProcedure.usable(catalog, ready,
    actor, PhasePowerCatalog.default(catalog), WalkerPowers(Vector.empty,
      restrictionSet = OperationRestrictions.forCatalog(catalog)))

  /** The parked decision as `viewer` is offered it. */
  def queryOf(transition: OathTransition, viewer: PlayerId)
      : Option[DecisionQueryProjection] = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).walkerDecision.flatMap(_.query)

  /** The `(kind, id)` of every option of the parked decision. */
  def offered(transition: OathTransition, viewer: PlayerId)
      : Option[Vector[(String, String)]] =
    queryOf(transition, viewer).map(_.offeredOptions.map(o => o.kind -> o.id))

  def boardOf(transition: OathTransition, viewer: PlayerId, owner: PlayerId)
      : PlayerBoardProjection = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).playerBoards
    .find(_.playerId == owner.value).get

  def publicBoardOf(transition: OathTransition, owner: PlayerId)
      : PlayerBoardProjection = projector.projectPublic("targets",
    LoadedGame(transition.state, 30L)).playerBoards
    .find(_.playerId == owner.value).get
