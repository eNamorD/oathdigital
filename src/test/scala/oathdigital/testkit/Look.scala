package oathdigital.testkit

import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The reads rule tests make of a state, named the way `Table` names what it
  * places. */
final class Look private (ready: ReadyGame):
  private def current = ready.game.current

  def player(p: PlayerId): PlayerState = current.players.find(_.player == p)
    .getOrElse(throw AssertionError(s"no player $p"))
  def supply(p: PlayerId): Int = player(p).board.supply.supply
  def favor(p: PlayerId): Int = player(p).board.favor
  def faceUpSecrets(p: PlayerId): Int = player(p).board.faceUpSecrets
  def faceDownSecrets(p: PlayerId): Int = player(p).board.faceDownSecrets
  def warbands(p: PlayerId): Int = player(p).board.warbands
  def advisers(p: PlayerId): Vector[CardId] = player(p).advisers.map(_.id)
  def relics(p: PlayerId): Vector[RelicId] = player(p).relics.map(_.id)
  def pawn(p: PlayerId): SiteId = player(p).pawnSite
    .getOrElse(throw AssertionError(s"$p has no pawn on the map"))

  def denizens(site: String | SiteId)(using munit.Location): Vector[CardId] =
    current.map.sites(CatalogNames.site(site)).denizens.map(_.id)
  def relicsAt(site: String | SiteId)(using munit.Location): Vector[RelicId] =
    current.map.sites(CatalogNames.site(site)).relics.map(_.id)
  def forces(site: String | SiteId)(using munit.Location): SiteForces =
    current.map.sites(CatalogNames.site(site)).forces
  def siteTokens(site: String | SiteId)(using munit.Location): Tokens =
    current.map.sites(CatalogNames.site(site)).tokens

  def phase: Phase = current.turn.phase
  def active: PlayerId = current.turn.activePlayer

  /** The tokens on `card`, wherever it sits; empty for a card that carries
    * none. */
  def tokensOn(card: String | CardId)(using munit.Location): Tokens =
    val id = CatalogNames.card(card)
    val onSites = current.map.sites.values.flatMap(s =>
      s.denizens.filter(_.id == id).map(_.tokens) ++
        s.relics.filter(_.id == id).map(_.tokens))
    val held = current.players.flatMap(p =>
      p.advisers.collect { case d: DenizenState if d.id == id => d.tokens } ++
        p.relics.filter(_.id == id).map(_.tokens))
    (onSites ++ held).headOption.getOrElse(Tokens.empty)

object Look:
  def apply(ready: ReadyGame): Look = new Look(ready)
  def apply(state: OathState)(using munit.Location): Look = state match
    case Ready(ready) => new Look(ready)
    case other => munit.Assertions.fail(s"expected a ready game, got $other")
