package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.model._

object WildAlliesCard extends Denizen(DenizenId("198"), "Wild Allies", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.wild-allies"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Campaign at any site with a [suit-beast] card. " +
      "Act as if your pawn is there. Spend no Supply and add your " +
      "warbands there to your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wild Allies (card 198), ACTION, one secret: "Campaign at any site with
  * a [suit-beast] card. Act as if your pawn is there. Spend no Supply and
  * add your warbands there to your force." A site qualifies when it holds
  * a faceup denizen, or an edifice, whose effective suit is Beast (catalog
  * batch 8, slice 13, ruling 2); the pawn's site among them. */
final case class WildAllies private (catalog: ExecutableCatalog)
    extends CampaignFromSite(WildAlliesCard.power, WildAlliesCard.id):
  def siteDecisionId: String = WildAllies.siteDecisionId
  def campaignAt: NoteKey = WildAllies.campaignAt
  override def noteKeys: Vector[NoteKey] = WildAllies.noteKeys
  protected def name: String = WildAlliesCard.name
  protected def noSite: String =
    "No site with a Beast card where a Campaign could start"

  protected def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val map = ready.game.current.map
    map.inPlay.filter(site => map.sites.get(site).exists(_.denizens.exists {
      case card: DenizenState => card.orientation == Orientation.FaceUp &&
        beast(ready, card.id)
      case card: EdificeState => beast(ready, card.id)
    }))

  private def beast(ready: ReadyGame, card: CardId): Boolean =
    EffectiveSuit.of(catalog, ready, card).contains(Suit.Beast)

object WildAllies:
  val id: PowerId = WildAlliesCard.power.id
  val siteDecisionId: String = "power.wild-allies.site"
  private val notes = NoteSet(id)
  /** "Campaign at {site}." */
  val campaignAt: NoteKey = notes.key("campaign-at", Vector(
    NotePart.Text("Campaign at "), NotePart.Arg(0), NotePart.Text(".")))
  val noteKeys: Vector[NoteKey] = notes.declared

  def forCatalog(catalog: ExecutableCatalog): WildAllies =
    new WildAllies(catalog)
