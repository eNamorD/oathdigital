package oathdigital.gameplay.operations

import oathdigital.catalog.holding.AncientBloodlineCard
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Ancient Bloodline (card 165, adviser-only): "Your enemies act as if
  * denizens and relics at sites you rule are locked."
  *
  * While a player holds it faceup, every other player is their enemy, and
  * every card at a site the holder rules is locked for that enemy. The
  * enemy cannot move it, so cannot take, give, swap or discard it. A bury
  * still goes through, as for every lock. The mover is the move's own
  * `by`. A card played to such a site, or a relic that arrives there, is
  * not refused; it is locked once it stands there.
  *
  * The holder may consent outside a Campaign (consent design, ruling 1).
  * A grant covers that card's move by that player for the turn.
  *
  * An Imperial holder rules through the Empire, which the engine does not
  * model yet: `PowerAccess.ruledSites` counts only a player ruler, so such
  * a holder protects nothing. That case is to be handled with the Imperial
  * rules.
  */
case object AncientBloodline extends AsIfLocked with WaivableRule:
  val id: PowerId = AncientBloodlineCard.power.id
  val source: PowerSourceRef = PowerSourceRef.Card(AncientBloodlineCard.id)

  /** The decision slot of a grant's key. The grant covers a card's move,
    * which the operation check sees without its decision, so the key names
    * the move instead. */
  val moveKey: String = "operation.move"

  /** The keys this rule owns. */
  val notes: NoteSet = NoteSet.unowned("AncientBloodline")
  /** "Locked.": the reason of a card nobody may be asked about. */
  val locked: NoteKey = notes.key("locked", Vector(NotePart.Text(
    "Locked.")))
  /** "Locked and needs the holder's consent.": the reason of a card its
    * holder may be asked about. */
  val lockedConsent: NoteKey = notes.key("locked-consent", Vector(
    NotePart.Text("Locked and needs the holder's consent.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  def consentNote: NoteKey = lockedConsent

  /** The player who holds Ancient Bloodline faceup, who may consent. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.collectFirst {
      case player if player.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) =>
          card == AncientBloodlineCard.id
        case _ => false
      } => player.player
    }

  def refusal(ready: ReadyGame, card: CardId, at: Location,
      by: Option[PlayerId]): Option[OperationReason] =
    for
      site <- Some(at).collect { case Location.Site(site) => site }
      mover <- by
      holder <- granter(ready)
      if mover != holder
      if PowerAccess.ruledSites(ready, holder).contains(site)
      ref <- cardRef(card)
    yield OperationReason("bloodline-locked",
      s"${card.value} is locked for ${mover.value}",
      OperationReasonKind.Impossible, Some(OperationWaiver(id, Some(holder),
        ConsentKey(id, mover, moveKey, ref), lockedConsent(source),
        locked(source))))

  private def cardRef(card: CardId): Option[DecisionOptionRef] = card match
    case denizen: DenizenId => Some(DecisionOptionRef.Denizen(denizen))
    case relic: RelicId => Some(DecisionOptionRef.Relic(relic))
    case edifice: EdificeId => Some(DecisionOptionRef.Edifice(edifice))
    case _ => None
