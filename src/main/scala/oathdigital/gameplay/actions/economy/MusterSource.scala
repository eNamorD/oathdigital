package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.EffectiveSuit
import oathdigital.model._

/** What a Muster or Trade draws on: the card its cost is placed on, the suit
  * that decides which advisers match and which favor bank a Trade draws from,
  * and where the card stands. Resolved from the answered reference on every
  * command and never persisted.
  */
final case class MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef)

object MusterSource:

  /** Why a card that holds favor or secrets is shown at the source decision
    * but cannot be chosen: the token-free rule. A power that lets the actor
    * draw on such a card makes its option available again.
    */
  val Occupied: OptionReason =
    OptionReason.Written("Already holds favor or secrets")

  /** The sources the base rule offers: every card at the actor's pawn site,
    * in site order, one that holds favor or secrets shown unavailable. The
    * decision is the gate: the walker accepts only an available option.
    */
  def atSite(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[DecisionOption] =
    for
      siteId <- state.game.current.pawnSite(actor).toVector
      site <- state.game.current.map.sites.get(siteId).toVector
      card <- site.denizens
      if catalog.suitOf(card.id).nonEmpty
    yield offered(card)

  /** `card` as an option of the source decision: available while it holds
    * no favor or secret, and unavailable for [[Occupied]] otherwise.
    */
  def offered(card: SiteDenizenState): DecisionOption =
    val option = card match
      case value: DenizenState =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(value.id))
      case value: EdificeState =>
        DecisionOption.Edifice(DecisionOptionRef.Edifice(value.id))
    if card.tokens.isEmpty then option
    else DecisionOption.Unavailable(option, Occupied)

  /** The source a reference names, wherever it stands: at a site in play, or
    * among the actor's advisers. It requires neither that the card stand at
    * the pawn site nor that it be token-free. The pawn site only names the
    * site in the refusal of a card found nowhere. The source decision offers
    * only what a rule allows, and the walker accepts only an available
    * option, so a power that lets a Muster or Trade draw on another card adds
    * it to the decision and changes nothing here. A Muster or Trade resumed
    * after its cost finds its source although the cost put tokens on it.
    * The suit is the one the action reads (`EffectiveSuit.traded`): Discord
    * once Spiteful Mirror is picked in `answered`.
    */
  def resolve(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef, answered: Vector[Answered] = Vector.empty)
      : Either[OathViolation, MusterSource] = for
    id <- cardIdOf(ref).toRight(OathViolation.InvalidEventOrder(
      s"${ref.kind}/${ref.wireId} is not a card a Muster or Trade can draw on"))
    siteId <- state.game.current.pawnSite(actor).toRight(OathViolation.PawnSiteMissing(actor))
    found <- locate(state, actor, id)
      .toRight(OathViolation.EconomyCardUnavailable(siteId, id))
    suit <- EffectiveSuit.traded(catalog, state, answered, ref).toRight(
      OathViolation.UnsupportedEconomyState(s"no catalog suit for $id"))
  yield MusterSource(id, suit, found.origin)

  /** The card a reference names, at a site in play or among the actor's
    * advisers, for a power that reads what it holds.
    */
  def cardOf(state: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : Option[SiteDenizenState] =
    cardIdOf(ref).flatMap(locate(state, actor, _)).map(_.card)

  /** The actor's faceup advisers of `suit`, a Marriage counting as two
    * (`EffectiveSuit`): how many warbands, favor or secrets a Muster or Trade
    * adds to its base yield.
    */
  def matching(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      suit: Suit): Int = EffectiveSuit.count(catalog, state, actor, suit)

  /** A card and where it stands. */
  private final case class Located(card: SiteDenizenState,
      origin: RuleSourceRef)

  /** `id` at the first site in play that holds it, in map order, or else
    * among the actor's advisers. */
  private def locate(state: ReadyGame, actor: PlayerId, id: CardId)
      : Option[Located] =
    val current = state.game.current
    val atSites = current.map.inPlay.iterator.flatMap(siteId =>
      current.map.sites.get(siteId).iterator.flatMap(_.denizens).collect {
        case card if card.id == id => Located(card, originAt(siteId, card))
      })
    val held = current.players.find(_.player == actor).iterator
      .flatMap(_.advisers).collect {
        case card: DenizenState if card.id == id =>
          Located(card, RuleSourceRef.Adviser(actor, card.id))
      }
    (atSites ++ held).nextOption()

  private def originAt(siteId: SiteId, card: SiteDenizenState)
      : RuleSourceRef = card match
    case value: DenizenState => RuleSourceRef.SiteCard(siteId, value.id)
    case value: EdificeState => RuleSourceRef.Edifice(siteId, value.id)

  private def cardIdOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

