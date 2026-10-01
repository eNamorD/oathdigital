package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Military Parade (card 109), a battle plan for either side: "If you're
  * victorious, gain [favor] from the favor banks matching each adviser of your
  * enemy (including Imperial Allies)."
  *
  * It is free. Once the Campaign has resolved, its user gains one favor if they
  * won for each faceup adviser the enemy holds then, from that adviser's suit
  * bank (`FavorBySuit`). A facedown adviser has no suit and gives nothing.
  * Every game is all-Exile, so the Imperial Allies clause does nothing. Bandits
  * hold no advisers, so an attacker is not offered it against bandits. A
  * bandit defender applies it by itself, since it is free, and when the
  * bandits win the favor moves from the banks to the shared bank. The gains
  * write the generic gain lines, so it writes no line of its own.
  */
final case class MilitaryParade private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = MilitaryParade.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.enemy != CampaignDefender.Bandits)
      .map(source => CampaignPlanOffer(source,
        "Military Parade: gain favor for the enemy's advisers if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else MilitaryParade.enemy(use).toVector.flatMap(enemy =>
        FavorBySuit.gains(use.user, FavorBySuit.counts(catalog,
          MilitaryParade.advisers(use.ready, enemy))))))

object MilitaryParade:
  val id: PowerId = PowerId("denizen.military-parade")

  /** The enemy of the plan's user: the attacker for a defender, and the
    * defending player for an attacker. */
  private def enemy(use: PlanUse): Option[PlayerId] = use.side match
    case CampaignPlanSide.Defender => Some(use.actor)
    case CampaignPlanSide.Attacker => use.result.map(_.defender).collect {
      case CampaignDefender.Player(player) => player }

  /** `player`'s faceup advisers. */
  private def advisers(ready: ReadyGame, player: PlayerId): Vector[CardId] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers.collect {
        case DenizenState(held, Orientation.FaceUp, _) => held })

  def forCatalog(catalog: ExecutableCatalog): Option[MilitaryParade] =
    CatalogCards.denizen(catalog, id).map(new MilitaryParade(_, catalog))
