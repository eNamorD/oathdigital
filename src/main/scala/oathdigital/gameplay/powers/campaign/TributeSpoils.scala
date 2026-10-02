package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object TributeSpoilsCard extends Denizen(DenizenId("239"), "Tribute Spoils", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.tribute-spoils"),
    persistent = false, cost = Cost(favor = 1),
    text = "If you're victorious in a conquest, take [favor] for each " +
      "card at targeted sites from the matching favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tribute Spoils (card 239), a battle plan for either side: "[favor] If you're
  * victorious in a conquest, take [favor] for each card at targeted sites from
  * the matching favor bank."
  *
  * A favor is placed onto the card. It is offered only in a Conquest. Once the
  * Campaign has resolved, its user gains one favor if they won for each
  * denizen and edifice at the targeted sites as they stand then, from that
  * card's suit bank (`FavorBySuit`). A facedown denizen has no suit and gives
  * nothing, an edifice gives on either face, and relics count nothing. Bandits
  * pay nothing, so a bandit defender never applies it. The gains write the
  * generic gain lines, so it writes no line of its own.
  */
final case class TributeSpoils private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = TributeSpoils.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Tribute Spoils: gain favor for each card at the targets if victorious",
        Vector(CampaignPlanCost.Favor(TributeSpoils.Favor)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.result.toVector.flatMap(result => FavorBySuit.gains(use.user,
        FavorBySuit.counts(catalog,
          TributeSpoils.cardsAt(use.ready, result.targetSites))))))

object TributeSpoils:
  val id: PowerId = PowerId("denizen.tribute-spoils")
  /** The favor placed to choose it. */
  val Favor: Int = 1

  /** The faceup denizens and the edifices at `sites`. */
  private def cardsAt(ready: ReadyGame, sites: Vector[SiteId]): Vector[CardId] =
    sites.flatMap(ready.game.current.map.sites.get).flatMap(_.denizens).collect {
      case DenizenState(held, Orientation.FaceUp, _) => held
      case edifice: EdificeState => edifice.id
    }

  def forCatalog(catalog: ExecutableCatalog): Option[TributeSpoils] =
    CatalogCards.denizen(catalog, id).map(new TributeSpoils(_, catalog))
