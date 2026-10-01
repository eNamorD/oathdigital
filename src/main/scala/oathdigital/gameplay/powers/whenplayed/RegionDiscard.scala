package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The region discard of Dazzle, Threatening Roar and Riots (catalog batch
  * 3, refactor P7): every site denizen and ruined edifice of a kept suit at
  * the sites in the actor's region, as far as the generic discard rules
  * permit. Intact edifices are locked, so they stay. The region is the
  * actor's pawn's, so a card played to a site discards itself when its suit
  * is kept.
  *
  * A power reads its targets live, from a `Branch` the walker reaches after
  * the card is in place, then discards them in one step. Its line names the
  * targets no site holds after that step, so a card a restriction kept is
  * not named, and it says so when nothing was discarded.
  */
private[whenplayed] final class RegionDiscard(catalog: ExecutableCatalog,
    kept: Suit => Boolean):

  /** The kept site denizens and ruined edifices in the actor's region, in
    * map order. A card the catalog does not know is not listed; `discards`
    * rejects it. */
  def targets(ready: ReadyGame, actor: PlayerId): Vector[CardId] =
    cards(ready, actor).collect {
      case (_, card) if discardable(card) &&
          catalog.suitOf(card.id).exists(kept) => card.id }

  /** A discard of each target, read live, to the next region's discard
    * pile. */
  def discards(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    region(ready, actor).toRight(OathViolation.PawnSiteMissing(actor))
      .flatMap(origin => cards(ready, actor).foldLeft[Either[OathViolation,
          Vector[CoreOperation]]](Right(Vector.empty)) {
        case (acc, (site, card)) => acc.flatMap(done =>
          suitOf(card).map(suit =>
            if kept(suit) then done ++ discard(site, card, suit,
              RegionDiscard.next(origin), actor)
            else done))
      })

  /** The whole effect of a power that only discards: the discard step, then
    * its line. */
  def effect(power: PowerId, card: DenizenId, actor: PlayerId)
      : Vector[Operation] = Vector(Branch((live, _) => Vector(
    BuildOps((ready, _) => discards(ready, actor)),
    Note(power, RegionDiscard.said(card, targets(live, actor)),
      covers = true))))

  private def region(ready: ReadyGame, actor: PlayerId): Option[Region] =
    val current = ready.game.current
    current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)

  /** Every site card in the actor's region, with its site, in map order. */
  private def cards(ready: ReadyGame, actor: PlayerId)
      : Vector[(SiteId, SiteDenizenState)] =
    val map = ready.game.current.map
    region(ready, actor).toVector.flatMap(origin => map.inPlay
      .filter(map.regionOf(_).contains(origin))
      .flatMap(site => map.sites.get(site).toVector
        .flatMap(_.denizens.map(site -> _))))

  private def suitOf(card: SiteDenizenState): Either[OathViolation, Suit] =
    catalog.suitOf(card.id).toRight(card match {
      case denizen: DenizenState => OathViolation.UnknownWorldCard(denizen.id)
      case edifice: EdificeState => OathViolation.UnknownEdifice(edifice.id)
    })

  private def discard(site: SiteId, card: SiteDenizenState, suit: Suit,
      to: Region, actor: PlayerId): Option[CoreOperation] =
    val from = PositionedLocation(Location.Site(site))
    card match
      case denizen: DenizenState => Some(Discard.Denizen(denizen.id, from, to,
        suit, denizen.tokens.favor, denizen.tokens.secrets, actor))
      case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
        Some(Discard.RuinedEdifice(edifice.id, from, suit,
          edifice.tokens.favor, edifice.tokens.secrets, actor))
      case _ => None

  private def discardable(card: SiteDenizenState): Boolean = card match
    case _: DenizenState => true
    case edifice: EdificeState => edifice.side == EdificeSide.Ruined

private[whenplayed] object RegionDiscard:
  /** "Discarded {cards}." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
  /** "Nothing was discarded." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nothing was discarded.")))

  /** The region whose discard pile takes the cards discarded in `origin`. */
  def next(origin: Region): Region = origin match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle

  /** The cards of `found` that no site holds in `ready`. */
  def gone(found: Vector[CardId], ready: ReadyGame): Vector[CardId] =
    val sites = ready.game.current.map.sites.values
    found.filterNot(id => sites.exists(_.denizens.exists(_.id == id)))

  /** "Discarded {cards}." for the cards of `found` that left the sites, or
    * "Nothing was discarded." */
  def said(card: DenizenId, found: Vector[CardId])(states: NoteStates)
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(card)
    val left = gone(found, states.now)
    Some(if left.nonEmpty then discarded(source, NoteArg.Cards(left))
      else none(source))
