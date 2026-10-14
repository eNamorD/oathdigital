package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.{Deferred, OperationRewrite,
  Transform}
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
  * It is free, and choosing it does nothing at the time. Its rewrite at the
  * Campaign's root folds `Late`, after every plan's own steps there. Once
  * the plan was used, it holds each discard of a Nomad card chosen as a
  * battle plan on its side (`OperationRewrite.ops`, `Deferred`), however
  * the plan built that discard. It does not read the discard's acting
  * player, which is the attacker for a bandit defender's plan. It appends
  * one step that resolves the held discards:
  *
  *  - it asks a player user who rules a Beast card, their faceup advisers
  *    and the faceup denizens at sites they rule, to discard one of them or
  *    to "Skip Wild Mounts". A Beast card chosen is discarded instead of the
  *    plans; skipping runs the held discards;
  *  - for a bandit user, it follows the FAQ: with two or more held
  *    discards, the first Beast card the bandits rule in map order is
  *    discarded instead;
  *  - otherwise it runs the held discards.
  *
  * Whether the plan was used is read at walk time, so a bandit's use,
  * recorded during the walk, counts. A held discard that runs writes the
  * plan's own "Discarded after the Campaign.", under the plan's power.
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
  override def noteKeys: Vector[NoteKey] = WildMounts.noteKeys

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wild Mounts: discard a Beast card instead of Nomad battle plans",
      Vector.empty, Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      OperationRewrite.ops(ctx, children)(swappable(ctx.activePlayer)) :+
        Deferred.resolve(WildMounts.deferred)(swap(ctx.activePlayer))))

  /** The plan, when it was used in this Campaign. */
  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Option[PlanUse] =
    PlanUse.chosen(ready, pending, actor, cardRef, sides, afterOutcome = true)

  /** Holds the discard of a Nomad card chosen as a battle plan on the side
    * the plan was used on. */
  private def swappable(actor: PlayerId)
      : OperationRewrite.Matcher[CoreOperation, CoreOperation] =
    (ready, pending) => {
      case discard: Discard.Denizen if discard.suit == Suit.Nomad &&
          used(ready, pending, actor).exists(use => PlanUse.plans(ready,
            pending, use.side).contains(
              DecisionOptionRef.Denizen(discard.card))) =>
        Vector(Defer(WildMounts.deferred, Vector(discard)))
    }

  /** What resolves the held discards: a question, a swap, or the
    * discards. */
  private def swap(actor: PlayerId)(ready: ReadyGame, pending: PendingTree,
      held: Vector[CoreOperation]): Vector[Operation] =
    used(ready, pending, actor).fold(discarded(held)) { use =>
      use.user match
        case Some(user) => ask(use, user, discarded(held),
          beasts(ready, SiteRuler.Player(user)))
        case None if held.size >= WildMounts.BanditDiscards =>
          beasts(ready, SiteRuler.Bandits).headOption
            .fold(discarded(held))(card => instead(use, use.actor, card))
        case None => discarded(held)
    }

  /** The held discards, each run as the plan built it, then the plan's own
    * line under the plan's power. */
  private def discarded(held: Vector[CoreOperation]): Vector[Operation] =
    held.flatMap(operation =>
      BuildOps((_, _) => Right(Vector(operation))) +: (operation match {
        case discard: Discard.Denizen => planPower(discard.card).toVector
          .map(PlanDiscard.discardedNote(_, discard.card))
        case _ => Vector.empty
      }))

  /** The power printed on `card`, which is the plan's. */
  private def planPower(card: DenizenId): Option[PowerId] =
    catalog.denizen(card).flatMap(_.powers.headOption).map(_.id)

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
  /** Where the held discards wait for the step that resolves them. */
  val deferred: DeferralKey = DeferralKey("wild-mounts")
  /** Bandits swap only when they would discard this many Nomad plans
    * (FAQ). */
  val BanditDiscards: Int = 2
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "wild-mounts"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Discarded {card} instead of the battle plans." */
  val swapped: NoteKey = notes.key("swapped", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0),
    NotePart.Text(" instead of the battle plans.")))
  /** "{Blue} discarded {card} instead of the battle plans.", for a
    * defender. */
  val swappedOffTurn: NoteKey = notes.key("swapped-off-turn", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" instead of the battle plans.")))

  def forCatalog(catalog: ExecutableCatalog): WildMounts =
    new WildMounts(catalog)
