package oathdigital.testkit

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.PlayerFacts
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
  /** The favor in `suit`'s bank. */
  def bankFavor(suit: Suit): Int = ready.banks.favor.getOrElse(suit, 0)
  /** Whether `card` lies in a regional discard pile. */
  def inRegionalDiscard(card: CardId): Boolean =
    current.commonCards.regionalDiscards.values.exists(_.contains(card))
  def faceUpSecrets(p: PlayerId): Int = player(p).board.faceUpSecrets
  def faceDownSecrets(p: PlayerId): Int = player(p).board.faceDownSecrets
  def boardWarbands(p: PlayerId): Int = player(p).board.warbands
  def advisers(p: PlayerId): Vector[CardId] = player(p).advisers.map(_.id)
  def relics(p: PlayerId): Vector[RelicId] = player(p).relics.map(_.id)
  def pawn(p: PlayerId): SiteId = player(p).pawnSite
    .getOrElse(throw AssertionError(s"$p has no pawn on the map"))

  /** The cards a site lists: its denizens and edifices. */
  def cardsAt(site: String | SiteId)(using munit.Location): Vector[CardId] =
    current.map.sites(CatalogNames.site(site)).denizens.map(_.id)
  def relicsAt(site: String | SiteId)(using munit.Location): Vector[RelicId] =
    current.map.sites(CatalogNames.site(site)).relics.map(_.id)
  def forces(site: String | SiteId)(using munit.Location): SiteForces =
    current.map.sites(CatalogNames.site(site)).forces
  def siteTokens(site: String | SiteId)(using munit.Location): Tokens =
    current.map.sites(CatalogNames.site(site)).tokens

  def phase: Phase = current.turn.phase
  def active: PlayerId = current.turn.activePlayer

  /** The tokens on `card`, at a site or in a player's play area. Fails when
    * the card lies in neither, where it could carry no tokens. */
  def tokensOn(card: String | CardId)(using munit.Location): Tokens =
    val id = CatalogNames.card(card)
    val onSites = current.map.sites.values.flatMap(s =>
      s.denizens.filter(_.id == id).map(_.tokens) ++
        s.relics.filter(_.id == id).map(_.tokens))
    val held = current.players.flatMap(p =>
      p.advisers.collect { case d: DenizenState if d.id == id => d.tokens } ++
        p.relics.filter(_.id == id).map(_.tokens))
    Unwrap.present((onSites ++ held).headOption,
      s"${CatalogNames.nameOf(id)} at a site or in a play area")

  /** The tokens on `card` as `p`'s adviser. Fails when `p` holds no such
    * adviser. */
  def adviserTokens(p: PlayerId, card: String | CardId)(
      using munit.Location): Tokens =
    val id = CatalogNames.card(card)
    Unwrap.present(player(p).advisers.collectFirst {
      case d: DenizenState if d.id == id => d.tokens },
      s"${CatalogNames.nameOf(id)} as ${p.value}'s adviser")

  /** The discard pile of the region after `site`'s region: where a card a
    * power discards from that site goes. Fails when the site is in no
    * region. */
  def discardAfter(site: String | SiteId)(using munit.Location)
      : Vector[WorldCardId] =
    val id = CatalogNames.site(site)
    val region = current.map.regionOf(id).getOrElse(
      munit.Assertions.fail(s"$id is in no region"))
    current.commonCards.discard(CardPlay.nextRegion(region))

  /** The kind of warband `p`'s own warbands are; fails with the rules'
    * refusal. */
  def forceKind(p: PlayerId)(using munit.Location): ForceKind =
    Unwrap.accepted(PlayerFacts.forceKind(ready, p))

object Look:
  def apply(ready: ReadyGame): Look = new Look(ready)
  def apply(state: OathState)(using munit.Location): Look = state match
    case Ready(ready) => new Look(ready)
    case other => munit.Assertions.fail(s"expected a ready game, got $other")
