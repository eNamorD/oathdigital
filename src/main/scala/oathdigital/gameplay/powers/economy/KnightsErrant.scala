package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** Knights Errant (card 120), a selected Muster modifier: after mustering, you
  * may campaign, spending no Supply.
  *
  * The Campaign runs inside the Muster. Two nodes are appended to the Muster's
  * tree, after the gain:
  *
  *  - a live decision, asked only when a Campaign is legal (a ruled site to
  *    Conquest, or an enemy pawn to Raid), whether to campaign;
  *  - a node that, once the answer is "campaign", builds the Campaign's own tree
  *    from live state when it is walked. The Campaign therefore sees the
  *    warbands the Muster just gained, and it is rebuilt on every resume like
  *    any Campaign is.
  *
  * The Campaign's Supply payment is removed at `CampaignCost`, and only when the
  * window is walked for a Muster (`PowerCtx.procedure`). The power cannot be
  * selected for any other action, so this is the Campaign it runs itself.
  *
  * Limitation: a Restriction hooked on the whole Campaign (Vow of Peace) is
  * checked once, when a command starts, against the tree that exists then, and
  * the nested Campaign is not part of it. The decisions of the nested Campaign
  * are restricted as usual.
  */
final case class KnightsErrant private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = KnightsErrant.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(offer(ctx.activePlayer), campaign(ctx.activePlayer)))),
    PowerWindow.CampaignCost -> Vector(Transform((ctx, operations) =>
      operations.filterNot {
        case SpendSupply(player, _, _) => player == ctx.activePlayer
        case _ => false
      })))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.window match {
    case PowerWindow.CampaignCost =>
      ctx.procedure.contains(ActionRef.Muster)
    case _ => true
  }

  private def offer(actor: PlayerId): Operation = Branch((ready, _) =>
    if (CampaignSetup.legalKinds(ready, actor).isEmpty) Vector.empty
    else Vector(Decide(KnightsErrant.decisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(KnightsErrant.campaignOption, "Campaign"),
        DecisionOption.Button(KnightsErrant.declineOption, "Do not campaign")),
      heading = Some("Knights Errant: campaign for no Supply?")))))

  private def campaign(actor: PlayerId): Operation = Branch((ready, pending) =>
    if (!PowerAnswers.one(pending, KnightsErrant.decisionId)
        .contains(KnightsErrant.campaignOption)) Vector.empty
    else CampaignProcedure.rebuild(catalog, ready, actor, Vector.empty)
      .fold(error => Vector[Operation](BuildOps((_, _) => Left(error))),
        tree => Vector(tree)))
}

object KnightsErrant {
  val id: PowerId = PowerId("denizen.knights-errant")
  /** Under the `muster.` prefix, which the Muster's continuation recognises. */
  val decisionId: String = "muster.knights-errant.campaign"
  val campaignOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("campaign")
  val declineOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("decline")

  def forCatalog(catalog: ExecutableCatalog): Option[KnightsErrant] =
    CatalogCards.denizen(catalog, id).map(new KnightsErrant(_, catalog))
}
