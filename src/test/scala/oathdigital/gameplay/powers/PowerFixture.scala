package oathdigital.gameplay.powers

import oathdigital.model._
import oathdigital.testkit.Table

/** Staging shared by the batch-1 power suites, built on the quiet `Table`:
  * `base` is p1's Act at Ancient City with nothing at any site and nobody
  * advised. Every method keeps the card inventory whole: a card leaves the
  * place it came from when it is placed.
  */
object PowerFixture:
  val base: ReadyGame = Table.start.ready
  val actor: PlayerId = Table.p1

  def player(ready: ReadyGame, id: PlayerId = actor): PlayerState =
    ready.game.current.players.find(_.player == id).get
  def home(ready: ReadyGame): SiteId = player(ready).pawnSite.get

  def updateActor(ready: ReadyGame)(f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if p.player == actor then f(p) else p)))

  def withBoard(ready: ReadyGame)(
      f: PlayerBoardState => PlayerBoardState): ReadyGame =
    updateActor(ready)(p => p.copy(board = f(p.board)))

  def inPhase(ready: ReadyGame, phase: Phase): ReadyGame =
    Table.from(ready).turn(actor, phase).unchecked

  def asAdviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    Table.from(ready).adviser(actor, id,
      facedown = orientation == Orientation.FaceDown).unchecked

  def atSite(ready: ReadyGame, id: DenizenId, site: SiteId): ReadyGame =
    Table.from(ready).denizen(id, at = site).unchecked

  def atHome(ready: ReadyGame, id: DenizenId): ReadyGame =
    atSite(ready, id, home(ready))

  /** A faceup relic in the actor's play area, taken from the relic deck or
    * from whichever site holds it.
    */
  def withRelic(ready: ReadyGame, id: RelicId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    Table.from(ready).relic(actor, id,
      facedown = orientation == Orientation.FaceDown).unchecked

  /** An edifice from the edifice deck, placed at `site` on `side`. */
  def withEdifice(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      site: SiteId): ReadyGame =
    Table.from(ready).edifice(id, side, at = site).unchecked

  /** Warbands of `kind` still in the bank: the printed supply less every
    * board and site.
    */
  def warbandBank(ready: ReadyGame, kind: ForceKind): Int =
    val current = ready.game.current
    val boards = current.players.filter(p =>
      PlayerForceKind.of(ready, p).contains(kind)).map(_.board.warbands).sum
    val sites = current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum
    ready.banks.warbandSupply(kind) - boards - sites

  /** Moves warbands from the bank onto the actor's board until the bank
    * holds `left`.
    */
  def leaveInBank(ready: ReadyGame, kind: ForceKind, left: Int): ReadyGame =
    withBoard(ready)(board => board.copy(
      warbands = board.warbands + warbandBank(ready, kind) - left))
