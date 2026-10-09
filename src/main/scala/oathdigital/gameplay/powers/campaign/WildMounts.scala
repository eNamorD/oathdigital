package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.gameplay.powers.{PowerAnswers, RuledCards}
import oathdigital.model._

object WildMountsCard extends Denizen(DenizenId("153"), "Wild Mounts", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.wild-mounts"),
    persistent = false, cost = Cost.free,
    text = "If you would discard any number of [suit-nomad] battle " +
      "plans, you may instead discard any one [suit-beast] card " +
      "you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wild Mounts (card 153), a battle plan for either side: "If you would
  * discard any number of [suit-nomad] battle plans, you may instead discard
  * any one [suit-beast] card you rule."
  *
  * It is free, and choosing it does nothing at the time. At the end of the
  * Campaign, its rewrite at the root strips its user's marked discards of
  * Nomad plans (`PlanDiscard.Marked`) from the plans' own steps and appends
  * one step of its own. It folds after every plan that appends a discard
  * there. That step:
  *
  *  - asks a player user who rules a Beast card, their faceup advisers and
  *    the faceup denizens at sites they rule, to discard one of them or to
  *    "Skip Wild Mounts". A Beast card chosen is discarded instead of the
  *    plans; skipping runs the plans' discards;
  *  - for a bandit user, follows the FAQ: with two or more Nomad discards,
  *    the first Beast card the bandits rule in map order is discarded
  *    instead;
  *  - otherwise runs the plans' discards.
  *
  * Whether the plan was used is read at walk time, so a bandit's use,
  * recorded during the walk, counts. The rewrite keeps the root's shape on
  * every fold, used or not.
  *
  * The swap writes "Discarded {card} instead of the battle plans.", or,
  * for a defender, "{Blue} discarded {card} instead of the battle plans.".
  */
final case class WildMounts private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = WildMountsCard.id
  def id: PowerId = WildMounts.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = WildMounts.Priority
  override def noteKeys: Vector[NoteKey] =
    Vector(WildMounts.swapped, WildMounts.swappedOffTurn)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wild Mounts: discard a Beast card instead of Nomad battle plans",
      Vector.empty, Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      children.map(withoutSwappable(ctx.activePlayer, _)) :+
        swap(ctx.activePlayer, children)))

  /** The plan, when it was used in this Campaign. */
  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Option[PlanUse] =
    PlanUse.chosen(ready, pending, actor, cardRef, sides, afterOutcome = true)

  /** Whether `operation` is the marked discard of a Nomad plan of `use`'s
    * user. */
  private def swappable(use: PlanUse)(operation: Operation): Boolean =
    operation match
      case marked: PlanDiscard.Marked => marked.user == use.user &&
        catalog.suitOf(marked.card).contains(Suit.Nomad)
      case _ => false

  /** `child`, without the swappable discards it selects once the plan was
    * used. Only a `Branch` selects a plan's discard. */
  private def withoutSwappable(actor: PlayerId, child: Operation): Operation =
    child match
      case branch: Branch => Branch((ready, pending) => {
        val selected = branch.select(ready, pending)
        used(ready, pending, actor).fold(selected)(use =>
          selected.filterNot(swappable(use)))
      })
      case other => other

  /** The step that asks, swaps, or runs the stripped discards. It selects
    * them again from the original steps, which read only the answers and
    * the plans' records, not what earlier discards moved. */
  private def swap(actor: PlayerId, children: Vector[Operation]): Operation =
    Branch((ready, pending) =>
      used(ready, pending, actor).fold(Vector.empty[Operation]) { use =>
        val discards = children.collect { case branch: Branch => branch }
          .flatMap(_.select(ready, pending)).filter(swappable(use))
        if discards.isEmpty then Vector.empty
        else use.user match
          case Some(user) => ask(use, user, discards,
            beasts(ready, SiteRuler.Player(user)))
          case None if discards.size >= WildMounts.BanditDiscards =>
            beasts(ready, SiteRuler.Bandits).headOption
              .fold(discards)(card => instead(use, use.actor, card))
          case None => discards
      })

  /** The faceup Beast denizens `ruler` rules, in map order. */
  private def beasts(ready: ReadyGame, ruler: SiteRuler): Vector[DenizenId] =
    RuledCards.of(catalog, ready, ruler, Suit.Beast).collect {
      case card: DenizenId => card }

  private def ask(use: PlanUse, user: PlayerId, discards: Vector[Operation],
      ruled: Vector[DenizenId]): Vector[Operation] =
    if ruled.isEmpty then discards
    else Vector(
      Decide(WildMounts.decisionId, user, DecisionQuery.ChooseOne(
        ruled.flatMap(card =>
          DecisionOption.forRef(DecisionOptionRef.Denizen(card))) :+
          DecisionOption.Button(WildMounts.skip, "Skip Wild Mounts"),
        heading = Some("Wild Mounts: discard a Beast card you rule " +
          "instead of your Nomad battle plans"))),
      Branch((_, pending) =>
        PowerAnswers.one(pending, WildMounts.decisionId) match {
          case Some(DecisionOptionRef.Denizen(card)) if ruled.contains(card) =>
            instead(use, user, card)
          case _ => discards
        }))

  /** The discard of `card` in place of the plans', by `acting`, and its
    * line. A player defender is named; bandits are no player. */
  private def instead(use: PlanUse, acting: PlayerId, card: DenizenId)
      : Vector[Operation] =
    val source = PowerSourceRef.Card(cardId)
    val line = (use.side, use.user) match
      case (CampaignPlanSide.Defender, Some(user)) => WildMounts
        .swappedOffTurn(source, NoteArg.Player(user), NoteArg.Card(card))
      case _ => WildMounts.swapped(source, NoteArg.Card(card))
    Vector(PlanDiscard.denizen(catalog, acting, card),
      Note(id, _ => Some(line)))

object WildMounts:
  val id: PowerId = WildMountsCard.power.id
  /** After every plan that appends a discard at the end of the Campaign,
    * whatever its priority (Traveling Doctor's is 1), so the rewrite sees
    * them all among the root's children. */
  val Priority: Int = 10
  /** Bandits swap only when they would discard this many Nomad plans
    * (FAQ). */
  val BanditDiscards: Int = 2
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "wild-mounts"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  /** "Discarded {card} instead of the battle plans." */
  val swapped: NoteKey = NoteKey("swapped", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0),
    NotePart.Text(" instead of the battle plans.")))
  /** "{Blue} discarded {card} instead of the battle plans.", for a
    * defender. */
  val swappedOffTurn: NoteKey = NoteKey("swapped-off-turn", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" instead of the battle plans.")))

  def forCatalog(catalog: ExecutableCatalog): WildMounts =
    new WildMounts(catalog)
