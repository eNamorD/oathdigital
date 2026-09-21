package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignPlanApplication, CampaignPlans}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Gleaming Armor (card 66), a persistent rule of a faceup adviser: "Your enemy's
  * battle plans have an added cost of [secret]."
  *
  * While its holder is in a Campaign, as the attacker or as a player defender,
  * every plan the opposing side chooses costs one more secret, placed onto the
  * plan's source card like any plan's cost. The title has no card, so the added
  * cost of the title's plan is turning one of its user's faceup secrets
  * facedown. The added cost is part of the plan's application, so a plan the user
  * cannot afford with it is not offered, and the option's price includes it.
  * Bandits are enemies too, and the cost applies to a bandit defender's plans.
  * Bandits hold no secrets and cannot pay it, so while a holder attacks a bandit
  * defender applies no plan at all.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  */
final case class GleamingArmor private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = GleamingArmor.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlanApplication -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case application: CampaignPlanApplication =>
          surcharge(ctx, application).fold(children)(_ +: children)
        case _ => children
      })))

  private def holder(ctx: PowerCtx): Option[PlayerId] =
    ctx.state.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The added cost, when the plan is the enemy's of a holder in this Campaign. */
  private def surcharge(ctx: PowerCtx, application: CampaignPlanApplication)
      : Option[Operation] = for {
    holding <- holder(ctx)
    if enemy(application, holding)
  } yield application.user.fold[Operation](unpayable)(user =>
    BuildOps((ready, _) => CampaignPlans.cardOf(application.source) match {
      case Some(card) => Right(Vector[CoreOperation](Costs.onCard(user, card,
        Cost(secret = 1), catalog, intoOccupied = true)))
      case None =>
        // Turning a secret facedown does nothing without one, so the cost of the
        // title's plan is checked here rather than left to a best-effort flip.
        val faceUp = ready.game.current.players.find(_.player == user)
          .fold(0)(_.board.faceUpSecrets)
        if (faceUp >= 1) Right(Vector[CoreOperation](FlipSecrets(user, 1,
          SecretSide.FaceUp, SecretSide.FaceDown)))
        else Left(OathViolation.InsufficientSecrets(1, faceUp))
    }))

  private def enemy(application: CampaignPlanApplication, holding: PlayerId)
      : Boolean = application.side match {
    case CampaignPlanSide.Defender => application.setup.actor == holding
    case CampaignPlanSide.Attacker =>
      application.setup.defender == CampaignDefender.Player(holding)
  }

  /** Bandits hold no secrets, so a bandit's plan cannot pay the added cost. */
  private def unpayable: Operation = BuildOps((_, _) =>
    Left(OathViolation.InsufficientSecrets(1, 0)))
}

object GleamingArmor {
  val id: PowerId = PowerId("denizen.gleaming-armor")

  def forCatalog(catalog: ExecutableCatalog): Option[GleamingArmor] =
    CatalogCards.denizen(catalog, id).map(new GleamingArmor(_, catalog))
}
