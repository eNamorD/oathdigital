package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.Costs
import oathdigital.model._

/** One chosen battle plan being paid for and applied. It carries the side, the
  * user and the source, in the window `CampaignPlanApplication`, so a power can
  * change what a plan costs or does by matching on it (as Silver Tongue matches
  * `PlacementTree`), and a dry run of it answers whether the user can pay.
  *
  * The children have a fixed shape, a slot each for revealing the source,
  * paying, sacrificing, and then one per effect, and every slot that depends on
  * state is a `Branch`. A walk that parks on a decision inside a plan resumes
  * against the state the earlier slots changed, so a slot must still select the
  * same children then, and a slot that has nothing to do selects none.
  *
  *  - A facedown adviser is revealed when it is used.
  *  - Costs are paid onto the source card, which may already hold resources. A
  *    plan paid outside its owner's turn settles at once: favor goes to the
  *    card's suit bank and secrets flip facedown (see `PayCost`). A bandit
  *    defender pays nothing, and records that it applied the plan.
  *  - A warband sacrifice asks which force pays when several could, then kills
  *    one warband. It comes before every effect, so a plan that cannot pay is
  *    refused before it does anything.
  */
final class CampaignPlanApplication(catalog: ExecutableCatalog,
    val setup: CampaignSetup, val side: CampaignPlanSide,
    val offered: OfferedPlan) extends Operation {
  def source: CampaignPlanSource = offered.offer.source
  /** The player who uses the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = CampaignPlans.userOf(setup, side)

  override val window: Option[PowerWindow] =
    Some(PowerWindow.CampaignPlanApplication)

  override val children: Vector[Operation] = Vector[Operation](
    Branch((ready, _) => reveal(ready)),
    Branch((_, _) => pay),
    Branch((ready, _) => sacrifice(ready))) ++ offered.offer.effects.map(effect) :+
    Branch((_, _) => marker)

  /** A bandit defender chooses nothing, so a later window learns it applied the
    * plan from this record.
    */
  private def marker: Vector[Operation] =
    if (user.nonEmpty) Vector.empty
    else Vector(ModifyDicePool(CampaignPlans.appliedMarker(
      CampaignPlans.refOf(source)), 1))

  private def reveal(ready: ReadyGame): Vector[Operation] = source match {
    case CampaignPlanSource.Adviser(player, id) if ready.game.current.players
        .find(_.player == player).exists(_.advisers.exists {
          case held: DenizenState =>
            held.id == id && held.orientation == Orientation.FaceDown
          case _ => false }) =>
      Vector(Move(Piece.Card(id), PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.PlayArea(player)),
        resultingOrientation = Some(Orientation.FaceUp)))
    case _ => Vector.empty
  }

  private def pay: Vector[Operation] = {
    val costs = offered.offer.costs
    def total(pick: PartialFunction[CampaignPlanCost, Int]): Int =
      costs.collect(pick).sum
    val cost = Cost(
      favor = total { case CampaignPlanCost.Favor(count) => count },
      secret = total { case CampaignPlanCost.Secret(count) => count },
      favorBurnt = total { case CampaignPlanCost.FavorBurnt(count) => count },
      secretBurnt = total { case CampaignPlanCost.SecretBurnt(count) => count })
    if (cost == Cost.free) Vector.empty
    else user match {
      case None => Vector.empty
      case Some(player) => CampaignPlans.cardOf(source) match {
        case Some(card) => Vector(payment(Costs.onCard(player, card, cost,
          catalog, intoOccupied = true)))
        case None if cost.favor + cost.secret == 0 =>
          Vector(payment(PayCost(player, Location.PlayArea(player), cost)))
        case None => Vector(refuse(
          "the title has no card to place a plan's cost on"))
      }
    }
  }

  /** The payment runs as a batch, not as the composite's own moves, so that
    * the pipeline settles it at once when its payer is not the active player.
    */
  private def payment(pay: PayCost): Operation =
    BuildOps((_, _) => Right(Vector[CoreOperation](pay)))

  private def refuse(detail: String): Operation =
    BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(detail)))

  /** The forces a warband can be sacrificed from: a Raid defender's board, or
    * each target site the defender rules and holds a warband on.
    */
  private def sacrificeFrom(ready: ReadyGame, player: PlayerId)
      : Vector[Location] = setup.kind match {
    case CampaignKind.Raid => ready.game.current.players
      .find(_.player == player).filter(_.board.warbands > 0)
      .map(_ => Location.PlayArea(player): Location).toVector
    case CampaignKind.Conquest => setup.targetSites.filter(site =>
      CampaignSetup.defenderAt(ready, site).contains(
        CampaignDefender.Player(player)) &&
        ready.game.current.map.sites.get(site).exists(_.forces match {
          case SiteForces.Occupied(_, count) => count > 0
          case SiteForces.Empty => false
        })).map(site => Location.Site(site): Location)
  }

  private def sacrifice(ready: ReadyGame): Vector[Operation] =
    if (!offered.offer.costs.contains(CampaignPlanCost.SacrificeWarband))
      Vector.empty
    else user match {
      case Some(player) if side == CampaignPlanSide.Defender =>
        sacrificeFrom(ready, player) match {
          case Vector() => Vector(refuse(
            "the plan needs a warband in the defender's force to sacrifice"))
          case Vector(only) => Vector(kill(ready, player, only))
          case several => Vector(
            Decide(CampaignIds.planSacrifice, player, DecisionQuery.ChooseOne(
              several.collect { case Location.Site(site) =>
                DecisionOption.Site(DecisionOptionRef.Site(site)): DecisionOption },
              heading = Some("Choose the site to sacrifice a warband from"))),
            BuildOps((state, pending) => PlanAnswers.site(pending)
              .toRight(OathViolation.InvalidEventOrder(
                "no site is chosen for the plan's sacrifice"))
              .flatMap(site => killOps(state, player, Location.Site(site)))))
        }
      case _ => Vector(refuse(
        "only a player defender can pay a warband sacrifice"))
    }

  private def kill(ready: ReadyGame, player: PlayerId, from: Location)
      : Operation = BuildOps((state, _) => killOps(state, player, from))

  private def killOps(ready: ReadyGame, player: PlayerId, from: Location)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val kind = from match {
      case Location.Site(site) => ready.game.current.map.sites.get(site)
        .map(_.forces).collect { case SiteForces.Occupied(force, _) => force }
      case _ => ready.game.current.players.find(_.player == player)
        .map(p => ForceKind.Exile(p.lineage))
    }
    kind.toRight(OathViolation.InvalidEventOrder(
      "the plan's sacrifice has no warband to kill")).map(force =>
      Vector[CoreOperation](Sacrifice(player, Piece.Warbands(force, 1),
        PositionedLocation(from))))
  }

  private def effect(effect: CampaignPlanEffect): Operation = effect match {
    case CampaignPlanEffect.AddAttackDice(count) =>
      ModifyDicePool(CampaignIds.attackPool, count)
    case CampaignPlanEffect.RemoveAttackDice(count) =>
      ModifyDicePool(CampaignIds.attackPool, -count)
    case CampaignPlanEffect.AddDefenseDice(count) =>
      ModifyDicePool(CampaignIds.defensePool, count)
    case CampaignPlanEffect.Run(operations) => Sequence(operations)
  }
}

/** Reads of the choices a plan's own decisions recorded. */
private[campaign] object PlanAnswers {
  def site(pending: PendingTree): Option[SiteId] = pending.answered.reverse
    .collectFirst {
      case Answered(CampaignIds.planSacrifice,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) => site
    }
}
