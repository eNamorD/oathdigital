package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Sticky Fire (relic R01), a battle plan for either side: "If you're victorious,
  * you may kill all the warbands in your enemy's force. If you do, you must give
  * them [favor] if able."
  *
  * Choosing it costs nothing. When its user wins, the losses step asks them,
  * before anything dies, whether to burn the enemy's force. If they do:
  *
  *  - after a Conquest won by the attacker, the defender keeps none of the
  *    warbands the victory would have returned to their board;
  *  - after a Raid won by the attacker, every warband left on the defender's
  *    board dies, not half;
  *  - after a defense won by the defender, every warband on the attacker's board
  *    dies, committed to the Campaign or not.
  *
  * Then the winner gives the loser a favor if they can. A `Give` takes only what
  * its giver holds, and against bandits the favor is given to the shared bank,
  * which burns it.
  */
final case class StickyFire private (relicId: RelicId) extends BattlePlan {
  def id: PowerId = StickyFire.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Sticky Fire: kill the enemy's force if victorious", Vector.empty,
      Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) => (for {
      user <- use.user
      result <- use.result
      if use.won.contains(true)
    } yield ask(user) +: (losses :+ burn(use, user, result))).getOrElse(losses)))

  private def ask(user: PlayerId): Operation = Decide(StickyFire.decisionId, user,
    DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(StickyFire.yes, "Kill the warbands in the enemy's force"),
      DecisionOption.Button(StickyFire.no, "Do not")),
      heading = Some("Sticky Fire: kill all the warbands in your enemy's force?")))

  /** The killing and the favor, when the user said yes. */
  private def burn(use: PlanUse, user: PlayerId, result: CampaignResult)
      : Operation = {
    val returned = returnedToDefender(use.ready, use.side, result)
    BuildOps((ready, pending) =>
      if (!PowerAnswers.one(pending, StickyFire.decisionId).contains(StickyFire.yes))
        Right(Vector.empty)
      else for {
        kills <- kills(ready, use.side, result, returned)
        gift = gives(use.side, user, result)
      } yield kills ++ gift)
  }

  /** The warbands a victorious attacker's Conquest would give back to a player
    * defender: what stands at the targets now, before the losses kill it, less
    * half. Read when the losses window is folded, which is before they run.
    */
  private def returnedToDefender(ready: ReadyGame, side: CampaignPlanSide,
      result: CampaignResult): Int =
    if (side != CampaignPlanSide.Attacker || result.kind != CampaignKind.Conquest)
      0
    else result.defender match {
      case CampaignDefender.Player(_) =>
        val total = result.targetSites.flatMap(ready.game.current.map.sites.get)
          .map(_.forces).collect {
            case SiteForces.Occupied(_, count) => count }.sum
        total - total / 2
      case CampaignDefender.Bandits => 0
    }

  private def kills(ready: ReadyGame, side: CampaignPlanSide,
      result: CampaignResult, returned: Int)
      : Either[OathViolation, Vector[CoreOperation]] = {
    def killed(player: PlayerId, amount: Int)
        : Either[OathViolation, Vector[CoreOperation]] =
      if (amount <= 0) Right(Vector.empty)
      else PlayerFacts.forceKind(ready, player).map(kind => Vector(Kill(
        Piece.Warbands(kind, amount),
        PositionedLocation(Location.PlayArea(player)))))
    def board(player: PlayerId): Int = ready.game.current.players
      .find(_.player == player).fold(0)(_.board.warbands)
    (side, result.defender) match {
      case (CampaignPlanSide.Defender, _) =>
        killed(result.attacker, board(result.attacker))
      case (CampaignPlanSide.Attacker, CampaignDefender.Player(defender)) =>
        result.kind match {
          case CampaignKind.Conquest => killed(defender, returned)
          case CampaignKind.Raid => killed(defender, board(defender))
        }
      case (CampaignPlanSide.Attacker, CampaignDefender.Bandits) =>
        Right(Vector.empty)
    }
  }

  private def gives(side: CampaignPlanSide, user: PlayerId,
      result: CampaignResult): Vector[CoreOperation] = {
    val to: Location = (side, result.defender) match {
      case (CampaignPlanSide.Defender, _) => Location.PlayArea(result.attacker)
      case (_, CampaignDefender.Player(defender)) => Location.PlayArea(defender)
      case (_, CampaignDefender.Bandits) => Location.SharedBank
    }
    Vector(Give(Piece.Favor(1), user, Location.PlayArea(user), to))
  }
}

object StickyFire {
  val id: PowerId = PowerId("relic.sticky-fire")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "sticky-fire"
  val yes: DecisionOptionRef.Button = DecisionOptionRef.Button("kill")
  val no: DecisionOptionRef.Button = DecisionOptionRef.Button("spare")

  def forCatalog(catalog: ExecutableCatalog): Option[StickyFire] =
    CatalogCards.relic(catalog, id).map(new StickyFire(_))
}
