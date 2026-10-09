package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.cardplay.CardSlots
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** The cards of `suits` at a site that a power may discard, for Dark
  * Enforcer, Taming Charm and Great Feast: faceup denizens and ruined
  * edifices, in site order, each with its suit. An intact edifice is locked
  * and stays, and a facedown card has no suit. Mounted Library discards
  * itself through `discard`.
  *
  * The standard discard sends a denizen to the discard pile of the region
  * after the site's, and a ruined edifice to the bottom of the edifice deck.
  * Either returns its favor to its suit's bank and its secrets to the
  * acting player facedown.
  */
private[powers] final class SiteCards(catalog: ExecutableCatalog,
    suits: Set[Suit]):

  def at(ready: ReadyGame, site: SiteId): Vector[(SiteDenizenState, Suit)] =
    ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens)
      .flatMap(card => discardable(card).flatMap(catalog.suitOf)
        .filter(suits).map(card -> _))

  private def discardable(card: SiteDenizenState): Option[CardId] = card match
    case DenizenState(id, Orientation.FaceUp, _) => Some(id)
    case EdificeState(id, EdificeSide.Ruined, _) => Some(id)
    case _ => None

  /** The standard discard of `card`, of `suit`, from `site`. */
  def discard(ready: ReadyGame, site: SiteId, card: SiteDenizenState,
      suit: Suit, actor: PlayerId, required: Boolean)
      : Either[OathViolation, CoreOperation] =
    val from = PositionedLocation(Location.Site(site))
    card match
      case denizen: DenizenState => ready.game.current.map.regionOf(site)
        .map(CardPlay.nextRegion)
        .toRight(OathViolation.InvalidEventOrder(
          s"${site.value} is not in play"))
        .map(region => Discard.Denizen(denizen.id, from, region, suit,
          denizen.tokens.favor, denizen.tokens.secrets, actor, required))
      case edifice: EdificeState => Right(Discard.RuinedEdifice(edifice.id,
        from, suit, edifice.tokens.favor, edifice.tokens.secrets, actor,
        required))

  def option(card: SiteDenizenState): DecisionOption = card match
    case denizen: DenizenState =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(denizen.id))
    case edifice: EdificeState =>
      DecisionOption.Edifice(DecisionOptionRef.Edifice(edifice.id))

  def refOf(card: SiteDenizenState): DecisionOptionRef = option(card).ref

  /** The card an answer names. */
  def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  /** `card`'s option as the printed restrictions judge `actor`'s discard of
    * it from `site` (consent design, slice 3). It is kept as it is when
    * nothing refuses, askable when only waivable refusals stop it, shown
    * unavailable with a protection's reason, or left out. */
  def judged(ready: ReadyGame, actor: PlayerId, site: SiteId,
      card: SiteDenizenState, suit: Suit): Option[DecisionOption] =
    CardSlots.judge(catalog, ready, actor, option(card),
      discard(ready, site, card, suit, actor, required = true).map(Vector(_)))

  /** The question over the cards at the player's site, each judged for the
    * player's discard. It is asked only when some card may be chosen: a
    * choose-one has no empty answer, so a question with only askable cards
    * would wait with nothing to choose after a refusal (consent slice 3). */
  def ask(ready: ReadyGame, player: PlayerId, decisionId: String,
      heading: String): Vector[Operation] =
    val found = PowerAccess.pawnSite(ready, player).toVector.flatMap(site =>
      at(ready, site).flatMap((card, suit) =>
        judged(ready, player, site, card, suit)))
    if !found.exists(_.available) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(found,
      heading = Some(heading)), passWhenEmpty = true))

  /** The answered card, with its site and suit, while it is still at the
    * player's site. Nothing when the question was not asked, or passed. */
  def chosen(ready: ReadyGame, player: PlayerId, pending: PendingTree,
      decisionId: String): Option[(SiteId, SiteDenizenState, Suit)] = for
    answer <- PowerAnswers.one(pending, decisionId)
    site <- PowerAccess.pawnSite(ready, player)
    (card, suit) <- at(ready, site).find((card, _) => refOf(card) == answer)
  yield (site, card, suit)
