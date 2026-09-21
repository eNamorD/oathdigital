package oathdigital.gameplay.powers

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{DecisionQueryProjection, PlayerBoardProjection}

/** Staging and driving shared by the slice 1c suites. `PowerFixture` (slice
  * 1a) stays untouched so that parallel slices do not collide on it.
  */
object TargetsFixture {
  import PowerFixture._

  val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val projector = new GameProjector(catalog)

  def others(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.map(_.player).filter(_ != actor)

  def updatePlayer(ready: ReadyGame, id: PlayerId)(
      f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == id) f(p) else p)))

  def withSecrets(ready: ReadyGame, id: PlayerId, faceUp: Int,
      faceDown: Int): ReadyGame = updatePlayer(ready, id)(p => p.copy(
    board = p.board.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown)))

  def withPawn(ready: ReadyGame, id: PlayerId, site: SiteId): ReadyGame =
    updatePlayer(ready, id)(_.copy(pawnSite = Some(site)))

  private def outOfWorldDeck(ready: ReadyGame, card: WorldCardId): ReadyGame =
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == card))))

  /** `card` becomes an adviser of `owner`, taken from the world deck. */
  def giveAdviser(ready: ReadyGame, owner: PlayerId, card: DenizenId,
      orientation: Orientation): ReadyGame =
    updatePlayer(outOfWorldDeck(ready, card), owner)(p => p.copy(advisers =
      p.advisers :+ DenizenState(card, orientation, Tokens.empty)))

  def giveVision(ready: ReadyGame, owner: PlayerId, card: VisionId,
      orientation: Orientation): ReadyGame =
    updatePlayer(outOfWorldDeck(ready, card), owner)(p => p.copy(advisers =
      p.advisers :+ VisionState(card, orientation)))

  /** Puts `owner`'s advisers at the bottom of the world deck. */
  def withoutAdvisers(ready: ReadyGame, owner: PlayerId): ReadyGame = {
    val held = player(ready, owner).advisers.map(_.id).collect {
      case id: WorldCardId => id }
    updatePlayer(ready, owner)(_.copy(advisers = Vector.empty))
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck ++ held)))
  }

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
    transition.continue ==
      OathContinue.AwaitingPowerDecision(actor, DecisionId(decision))

  def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  def usableNow(ready: ReadyGame) = PhasePowerProcedure.usable(catalog, ready,
    actor, PhasePowerCatalog.default(catalog))

  /** The parked decision as `viewer` is offered it. */
  def queryOf(transition: OathTransition, viewer: PlayerId)
      : Option[DecisionQueryProjection] = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).walkerDecision.flatMap(_.query)

  /** The `(kind, id)` of every option of the parked decision. */
  def offered(transition: OathTransition, viewer: PlayerId)
      : Option[Vector[(String, String)]] =
    queryOf(transition, viewer).map(_.options.map(o => o.kind -> o.id))

  def boardOf(transition: OathTransition, viewer: PlayerId, owner: PlayerId)
      : PlayerBoardProjection = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).playerBoards
    .find(_.playerId == owner.value).get

  def publicBoardOf(transition: OathTransition, owner: PlayerId)
      : PlayerBoardProjection = projector.projectPublic("targets",
    LoadedGame(transition.state, 30L)).playerBoards
    .find(_.playerId == owner.value).get
}
