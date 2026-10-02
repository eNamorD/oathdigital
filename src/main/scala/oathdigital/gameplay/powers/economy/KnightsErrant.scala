package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, SelectedModifier}
import oathdigital.model._

object KnightsErrantCard extends Denizen(DenizenId("120"), "Knights Errant", Suit.Order):
  val power = PrintedPower(PowerId("denizen.knights-errant"),
    persistent = false, cost = Cost.free,
    text = "After mustering, you may campaign, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

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
  * A Campaign-wide Restriction (Vow of Peace, a protecting Fortress) still
  * reaches the nested Campaign: the walker's restriction look-ahead probes the
  * "campaign" option before offering it, walking the tree that answer would
  * derive, so a forbidden nested Campaign hides that option instead of
  * parking on it and refusing the answer.
  *
  * Choosing to campaign writes "{Red} campaigns for no Supply." right after
  * the choice.
  */
final case class KnightsErrant private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = KnightsErrantCard.id
  def id: PowerId = KnightsErrant.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)
  override def noteKeys: Vector[NoteKey] = Vector(KnightsErrant.campaigns)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(offer(ctx.activePlayer), campaign(ctx.activePlayer)))),
    PowerWindow.CampaignCost -> Vector(Transform((ctx, operations) =>
      operations.filterNot {
        case SpendSupply(player, _, _) => player == ctx.activePlayer
        case _ => false
      })))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.window match
    case PowerWindow.CampaignCost =>
      ctx.procedure.contains(ActionRef.Muster)
    case _ => true

  private def offer(actor: PlayerId): Operation = Branch((ready, _) =>
    if CampaignSetup.legalKinds(ready, actor).isEmpty then Vector.empty
    else Vector(Decide(KnightsErrant.decisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(KnightsErrant.campaignOption, "Campaign"),
        DecisionOption.Button(KnightsErrant.declineOption, "Do not campaign")),
      heading = Some("Knights Errant: campaign for no Supply?"))),
      Note(id, campaignNote(actor))))

  /** Its line, once the player chose to campaign. */
  private def campaignNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    Option.when(NoteSupport.answer(states, KnightsErrant.decisionId)
      .contains(KnightsErrant.campaignOption))(KnightsErrant.campaigns(
      PowerSourceRef.Card(cardId), NoteArg.Player(actor)))

  private def campaign(actor: PlayerId): Operation = Branch((ready, pending) =>
    if !PowerAnswers.one(pending, KnightsErrant.decisionId)
        .contains(KnightsErrant.campaignOption) then Vector.empty
    else CampaignProcedure.rebuild(catalog, ready, actor, Vector.empty)
      .fold(error => Vector[Operation](BuildOps((_, _) => Left(error))),
        tree => Vector(tree)))

object KnightsErrant:
  val id: PowerId = KnightsErrantCard.power.id
  /** Under the `muster.` prefix. */
  val decisionId: String = "muster.knights-errant.campaign"
  val campaignOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("campaign")
  val declineOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("decline")
  /** "{Red} campaigns for no Supply." */
  val campaigns: NoteKey = NoteKey("campaigns", Vector(NotePart.Arg(0),
    NotePart.Text(" campaigns for no Supply.")))

  def forCatalog(catalog: ExecutableCatalog): KnightsErrant =
    new KnightsErrant(catalog)
