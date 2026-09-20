package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

/** Staging shared by the batch-1 power suites. Every method keeps the card
  * inventory whole: a card leaves the place it came from when it is placed.
  */
object PowerFixture {
  val base: ReadyGame = initialReady
  val actor: PlayerId = base.game.current.turn.activePlayer

  def player(ready: ReadyGame, id: PlayerId = actor): PlayerState =
    ready.game.current.players.find(_.player == id).get
  def home(ready: ReadyGame): SiteId = player(ready).pawnSite.get

  def updateActor(ready: ReadyGame)(f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actor) f(p) else p)))

  def withBoard(ready: ReadyGame)(
      f: PlayerBoardState => PlayerBoardState): ReadyGame =
    updateActor(ready)(p => p.copy(board = f(p.board)))

  def inPhase(ready: ReadyGame, phase: Phase): ReadyGame =
    ready.updateCurrent(_.copy(turn = TurnState(actor, phase, Set.empty)))

  /** The first game deals only some denizens, so `id` may not be in the world
    * deck at all. It is then a card the fixture adds, and there is nothing to
    * take out.
    */
  private def outOfWorldDeck(ready: ReadyGame, id: DenizenId): ReadyGame =
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == id))))

  def asAdviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    updateActor(outOfWorldDeck(ready, id))(p => p.copy(advisers =
      p.advisers :+ DenizenState(id, orientation, Tokens.empty)))

  def atSite(ready: ReadyGame, id: DenizenId, site: SiteId): ReadyGame =
    outOfWorldDeck(ready, id).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(site, c.map.sites(site).copy(denizens =
        c.map.sites(site).denizens :+
          DenizenState(id, Orientation.FaceUp, Tokens.empty))))))

  def atHome(ready: ReadyGame, id: DenizenId): ReadyGame =
    atSite(ready, id, home(ready))

  /** A faceup relic in the actor's play area, taken from the relic deck or
    * from whichever site holds it.
    */
  def withRelic(ready: ReadyGame, id: RelicId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    ready.updateCurrent { c =>
      val cleared = c.copy(
        commonCards = c.commonCards.copy(
          relicDeck = c.commonCards.relicDeck.filterNot(_ == id)),
        map = c.map.copy(sites = c.map.sites.map { case (site, state) =>
          site -> state.copy(relics = state.relics.filterNot(_.id == id)) }))
      cleared.copy(players = cleared.players.map(p =>
        if (p.player != actor) p
        else p.copy(relics = p.relics :+
          RelicState(id, orientation, Tokens.empty))))
    }

  /** An edifice from the edifice deck, placed at `site` on `side`. */
  def withEdifice(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      site: SiteId): ReadyGame = {
    require(ready.game.current.commonCards.edificeDeck.contains(id),
      s"${id.value} is not in the edifice deck")
    ready.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        edificeDeck = c.commonCards.edificeDeck.filterNot(_ == id)),
      map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(id, side, Tokens.empty))))))
  }

  /** Warbands of `kind` still in the bank: the printed supply less every
    * board and site.
    */
  def warbandBank(ready: ReadyGame, kind: ForceKind): Int = {
    val current = ready.game.current
    val boards = current.players.filter(p =>
      PlayerForceKind.of(ready, p).contains(kind)).map(_.board.warbands).sum
    val sites = current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum
    ready.banks.warbandSupply(kind) - boards - sites
  }

  /** Moves warbands from the bank onto the actor's board until the bank
    * holds `left`.
    */
  def leaveInBank(ready: ReadyGame, kind: ForceKind, left: Int): ReadyGame =
    withBoard(ready)(board => board.copy(
      warbands = board.warbands + warbandBank(ready, kind) - left))
}
