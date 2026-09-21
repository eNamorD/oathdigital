package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** What a Muster or Trade draws on: the card its cost is placed on, the suit
  * that decides which advisers match and which favor bank a Trade draws from,
  * and where the card sits. Resolved from the answered reference on every
  * command and never persisted.
  */
final case class MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef,
    option: DecisionOption)

object MusterSource {

  /** The sources the base rule offers: the token-free cards at the actor's
    * pawn site, in site order.
    */
  def atSite(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[MusterSource] =
    for {
      siteId <- pawnSite(state, actor).toVector
      site <- state.game.current.map.sites.get(siteId).toVector
      card <- site.denizens
      if card.tokens.isEmpty
      suit <- catalog.suitOf(card.id).toVector
    } yield sourceOf(siteId, card, suit)

  /** The source a reference names, or why it is not one. This is the single
    * acceptance function: a rule that lets a Muster draw on some other kind of
    * card widens it here. The token-free rule applies to every source.
    */
  def resolve(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef): Either[OathViolation, MusterSource] = for {
    id <- cardIdOf(ref).toRight(OathViolation.InvalidEventOrder(
      s"${ref.kind}/${ref.wireId} is not a card a Muster or Trade can draw on"))
    siteId <- pawnSite(state, actor).toRight(OathViolation.PawnSiteMissing(actor))
    site <- state.game.current.map.sites.get(siteId)
      .toRight(OathViolation.SiteNotInPlay(siteId))
    card <- site.denizens.find(_.id == id)
      .toRight(OathViolation.EconomyCardUnavailable(siteId, id))
    _ <- Either.cond(card.tokens.isEmpty, (),
      OathViolation.EconomyCardNotEmpty(id))
    suit <- catalog.suitOf(card.id).toRight(
      OathViolation.UnsupportedEconomyState(s"no catalog suit for ${card.id}"))
  } yield sourceOf(siteId, card, suit)

  /** The actor's faceup advisers of `suit`: how many warbands, favor or
    * secrets a Muster or Trade adds to its base yield.
    */
  def matching(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      suit: Suit): Int =
    state.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers).count {
        case DenizenState(id, Orientation.FaceUp, _) =>
          catalog.suitOf(id).contains(suit)
        case _ => false
      }

  private def cardIdOf(ref: DecisionOptionRef): Option[CardId] = ref match {
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
  }

  private def pawnSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  private def sourceOf(siteId: SiteId, card: SiteDenizenState,
      suit: Suit): MusterSource = card match {
    case value: DenizenState => MusterSource(value.id, suit,
      RuleSourceRef.SiteCard(siteId, value.id),
      DecisionOption.Denizen(DecisionOptionRef.Denizen(value.id)))
    case value: EdificeState => MusterSource(value.id, suit,
      RuleSourceRef.Edifice(siteId, value.id),
      DecisionOption.Edifice(DecisionOptionRef.Edifice(value.id)))
  }
}
