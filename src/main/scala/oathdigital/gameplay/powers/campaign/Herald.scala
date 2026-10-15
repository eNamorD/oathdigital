package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

object HeraldCard extends Denizen(DenizenId("143"), "Herald", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.herald"),
    persistent = true, cost = Cost.free,
    text = "After another player campaigns against a player _(not " +
      "bandits)_, you gain [favor] from any favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Herald (card 143), a persistent rule of a faceup adviser: "After another
  * player campaigns against a player (not bandits), you gain [favor] from
  * any favor bank."
  *
  * A `Transform` at the Campaign's root, `CampaignActionEligibility`, which
  * appends a `Branch` after the outcome, so it reads this Campaign's
  * recorded result. It fires when the attacker is not the holder and the
  * defender is a player: the holder may be the defender or a third player.
  * The root window is the Campaign's own, so it also fires for a Campaign a
  * power runs inside another action.
  *
  * The holder chooses the bank off turn (`FavorBankChoice`). One stocked bank
  * is taken without asking, and none leaves nothing to do. It adds the one
  * `Branch` whatever the board holds, as Book Binders does.
  *
  * Its line, "{Blue} gained 1 favor from the Order bank.", covers the Gain
  * line and names the bank, so its decision is narrated.
  */
final case class Herald private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = HeraldCard.id
  def id: PowerId = Herald.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Herald.noteKeys
  override def narratedDecisions: Set[String] = Set(Herald.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ reward(ctx.activePlayer))))

  private def reward(actor: PlayerId): Operation = Branch((ready, _) => (for
    result <- ready.game.current.lastCampaignResult
    if result.attacker == actor && result.defender != CampaignDefender.Bandits
    holder <- holderOf(ready)
    if holder != actor
  yield
    val take = FavorBankChoice.take(ready, holder, Herald.Favor,
      Herald.decisionId, "Herald: take a favor from a bank")
    if take.isEmpty then take
    else take :+ Note(id, NoteSupport.gainedFromNote(Herald.gained,
      PowerSourceRef.Card(cardId), holder), covers = true)
  ).getOrElse(Vector.empty))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object Herald:
  val id: PowerId = HeraldCard.power.id
  /** The favor gained. */
  val Favor: Int = 1
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "herald"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "{Blue} gained {1 favor} from {the Order bank}." */
  val gained: NoteKey = NoteSupport.gainedFromKey(notes, "gained")

  def forCatalog(catalog: ExecutableCatalog): Herald = new Herald(catalog)
