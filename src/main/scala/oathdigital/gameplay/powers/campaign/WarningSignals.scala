package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Warning Signals (card 25), a defender's battle plan: "Move any warbands to and
  * from your board and any sites you rule (except the last warband from a site).
  * At end, discard Warning Signals."
  *
  * Only a player defender uses it, from an adviser or a site the defender rules.
  * It costs nothing. When it is chosen the defender arranges their warbands
  * again, before their force is scored: one distribution over their board and
  * every site they rule, whether or not it is targeted, that keeps the total and
  * leaves each site at least one warband. Nothing is asked when there is nowhere
  * to move to. The card is discarded when the Campaign has resolved, whether or
  * not the defender won.
  */
final case class WarningSignals private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan {
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Warning Signals: rearrange your warbands",
        Vector.empty, Vector(CampaignPlanEffect.Run(Vector(rearrange(user)))))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))

  /** The board and each ruled site, with the warbands each holds now. */
  private def holdings(ready: ReadyGame, user: PlayerId)
      : (Int, Vector[(SiteId, Int)]) = {
    val current = ready.game.current
    val board = current.players.find(_.player == user).fold(0)(_.board.warbands)
    val sites = current.map.inPlay.filter(site => CampaignSetup
      .defenderAt(ready, site).contains(CampaignDefender.Player(user)))
      .flatMap(site => current.map.sites(site).forces match {
        case SiteForces.Occupied(_, count) if count > 0 => Some(site -> count)
        case _ => None
      })
    (board, sites)
  }

  private def rearrange(user: PlayerId): Operation = Branch((ready, _) => {
    val (board, sites) = holdings(ready, user)
    val total = board + sites.map(_._2).sum
    if (sites.isEmpty || total == 0) Vector.empty
    else {
      val room = total - (sites.size - 1)
      val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
        Some(board)) +: sites.map { case (site, count) => DistributeSlot(
          DecisionOptionRef.Site(site), 1, room, Some(count)) }
      Vector(
        Decide(WarningSignals.decisionId, user, DecisionQuery.Distribute.exactly(
          slots, total, Some("Warning Signals: arrange your warbands. Your " +
            "board holds the ones no site keeps, and each site keeps at least one"),
          "Move warbands")),
        BuildOps((state, pending) => PowerAnswers.distribution(pending,
          WarningSignals.decisionId).toRight(PowerAnswers.missing(
          WarningSignals.decisionId)).flatMap(rows => moves(state, user, rows))))
    }
  })

  /** The moves from what each site holds now to what the answer gives it:
    * every site that shrinks sends its extras to the board first, so the board
    * always holds what the sites that grow are given.
    */
  private def moves(ready: ReadyGame, user: PlayerId,
      rows: Vector[DistributeAmount])
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, user).map { kind =>
      val (_, sites) = holdings(ready, user)
      val changes = sites.flatMap { case (site, now) => rows.collectFirst {
        case DistributeAmount(DecisionOptionRef.Site(`site`), wanted) =>
          (site, wanted - now)
      }}
      def move(site: SiteId, count: Int, out: Boolean): CoreOperation =
        Move(Piece.Warbands(kind, count),
          PositionedLocation(if (out) Location.Site(site)
            else Location.PlayArea(user)),
          PositionedLocation(if (out) Location.PlayArea(user)
            else Location.Site(site)))
      changes.collect { case (site, delta) if delta < 0 =>
        move(site, -delta, out = true) } ++
        changes.collect { case (site, delta) if delta > 0 =>
          move(site, delta, out = false) }
    }
}

object WarningSignals {
  val id: PowerId = PowerId("denizen.warning-signals")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "warning-signals"

  def forCatalog(catalog: ExecutableCatalog): Option[WarningSignals] =
    CatalogCards.denizen(catalog, id).map(new WarningSignals(_, catalog))
}
