package oathdigital.gameplay.powers

import oathdigital.model._
import oathdigital.testkit.{Look, Table}

/** Staging shared by the power suites, built on the quiet `Table`:
  * `quietTable` is p1's Act with p1's pawn at Ancient City, nothing at any
  * site and nobody advised. `actor` is p1. Every method keeps the card
  * inventory whole: a card leaves the place it came from when it is placed.
  */
object PowerFixture:
  val quietTable: ReadyGame = Table.start.pawn(Table.p1, "Ancient City").ready
  val actor: PlayerId = Table.p1

  /** The site the actor's pawn stands on. */
  def actorsSite(ready: ReadyGame): SiteId = Look(ready).pawn(actor)

  def inPhase(ready: ReadyGame, phase: Phase)(using munit.Location): ReadyGame =
    Table.from(ready).turn(actor, phase).ready

  def asAdviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp)(
          using munit.Location): ReadyGame =
    Table.from(ready).adviser(actor, id,
      facedown = orientation == Orientation.FaceDown).ready

  def atSite(ready: ReadyGame, id: DenizenId, site: SiteId)(
      using munit.Location): ReadyGame =
    Table.from(ready).denizen(id, at = site).ready

  /** `id` at the site the actor's pawn stands on. */
  def atActorsSite(ready: ReadyGame, id: DenizenId): ReadyGame =
    atSite(ready, id, actorsSite(ready))

  /** A faceup relic in the actor's play area, taken from the relic deck or
    * from whichever site holds it.
    */
  def withRelic(ready: ReadyGame, id: RelicId,
      orientation: Orientation = Orientation.FaceUp)(
          using munit.Location): ReadyGame =
    Table.from(ready).relic(actor, id,
      facedown = orientation == Orientation.FaceDown).ready

  /** An edifice from the edifice deck, placed at `site` on `side`. */
  def withEdifice(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      site: SiteId)(using munit.Location): ReadyGame =
    Table.from(ready).edifice(id, side, at = site).ready

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
  def leaveInBank(ready: ReadyGame, kind: ForceKind, left: Int)(
      using munit.Location): ReadyGame =
    Table.from(ready).boardWarbands(actor, Look(ready).boardWarbands(actor) +
      warbandBank(ready, kind) - left).ready
