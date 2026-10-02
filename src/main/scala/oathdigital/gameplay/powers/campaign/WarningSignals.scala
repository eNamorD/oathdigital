package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers,
  WarbandArrangement}
import oathdigital.model._

object WarningSignalsCard extends Denizen(DenizenId("25"), "Warning Signals", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.warning-signals"),
    persistent = false, cost = Cost.free,
    text = "Move any warbands to and from your board and any sites you " +
      "rule _(except the last warband from a site)_. At end, " +
      "discard Warning Signals.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Warning Signals (card 25), a defender's battle plan: "Move any warbands to and
  * from your board and any sites you rule (except the last warband from a site).
  * At end, discard Warning Signals."
  *
  * Only a player defender uses it, from an adviser or a site the defender rules.
  * It costs nothing. When it is chosen the defender arranges their warbands
  * again, before their force is scored: one distribution over their board and
  * every site they rule, whether or not it is targeted, that keeps the total and
  * leaves each site at least one warband ([[WarbandArrangement]], shared with
  * Messenger). Nothing is asked when there is nowhere to move to: no ruled
  * site, or no warband beyond the one each site keeps. The card is discarded
  * when the Campaign has resolved, whether or not the defender won.
  *
  * Once the defender has answered it writes "{Blue} redistributed their
  * warbands."; the line follows the decision, so a game parked on it resumes
  * where it was.
  */
final case class WarningSignals private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(WarningSignals.redistributed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Warning Signals: rearrange your warbands",
        Vector.empty, Vector(CampaignPlanEffect.Run(Vector(rearrange(user)))))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))

  /** The sites the user defends as a player, in map order. */
  private def defended(ready: ReadyGame, user: PlayerId): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(site => CampaignSetup
      .defenderAt(ready, site).contains(CampaignDefender.Player(user)))

  private def rearrange(user: PlayerId): Operation = Branch((ready, _) => {
    val (board, sites) = WarbandArrangement.holdings(ready, user,
      defended(ready, user))
    if !WarbandArrangement.movable(board, sites) then Vector.empty
    else Vector(
      Decide(WarningSignals.decisionId, user, WarbandArrangement.query(user,
        board, sites, "Warning Signals: arrange your warbands. Your board " +
          "holds the ones no site keeps, and each site keeps at least one")),
      Note(id, _ => Some(WarningSignals.redistributed(
        PowerSourceRef.Card(cardId), NoteArg.Player(user)))),
      BuildOps((state, pending) => PowerAnswers.distribution(pending,
        WarningSignals.decisionId).toRight(PowerAnswers.missing(
        WarningSignals.decisionId)).flatMap(rows => WarbandArrangement.moves(
        state, user, defended(state, user), rows))))
  })

object WarningSignals:
  val id: PowerId = PowerId("denizen.warning-signals")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "warning-signals"
  /** "{Blue} redistributed their warbands." */
  val redistributed: NoteKey = NoteKey("redistributed", Vector(NotePart.Arg(0),
    NotePart.Text(" redistributed their warbands.")))

  def forCatalog(catalog: ExecutableCatalog): Option[WarningSignals] =
    CatalogCards.denizen(catalog, id).map(new WarningSignals(_, catalog))
