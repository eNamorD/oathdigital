package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PowerAnswers, RulerToll}
import oathdigital.model._

object WayStationCard extends Denizen(DenizenId("169"), "Way Station", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.way-station"),
    persistent = true, cost = Cost.free,
    text = "Spend no Supply if you're traveling to this site and either " +
      "rule Way Station or choose to give [favor] to its ruler. " +
      "_(Give it to Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Way Station (card 169), a persistent rule of a faceup site card: a Travel
  * to its site spends no Supply for Way Station's ruler, and for anyone else
  * who chooses to give 1 favor to that ruler, or burn it when bandits rule.
  * Empire rulers are not supported.
  *
  * It rewrites the Travel's cost node after every other Travel power (priority
  * 3), so it sees the Supply they leave. Its ruler's payment is removed. A
  * non-ruler with favor, on a Travel that still costs Supply, is asked in the
  * Action panel; the Supply answer is offered only when affordable. The
  * pawn's move stays outside the question, so later windows still see the
  * route. The question parks before the move, and Travel's source is a start
  * fact, so a resume rebuilds the same tree. Its payment runs after the rest
  * of the cost and just before the move, so every question of the cost, a
  * toll's step included, is asked before any of it is paid
  * (`TravelRoute.beforeMove`).
  *
  * The favor writes the toll lines Curfew and Forced Labor write: "Gave
  * {1 favor} to {Blue}." or "Burned {1 favor}."
  */
final case class WayStation private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = WayStationCard.id
  def id: PowerId = WayStation.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def priority: Int = 3
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      arriving(ctx).fold(operations)((traveller, ruler) =>
        rewrite(ctx.state, traveller, ruler, operations)))))

  override def applicable(ctx: PowerCtx): Boolean = arriving(ctx).nonEmpty

  /** The traveller and Way Station's ruler, on a Travel to its site. */
  private def arriving(ctx: PowerCtx): Option[(PlayerId, SiteRuler)] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    site <- SiteRulers.siteOf(ctx.state, cardId)
    if route.destination == site
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
  yield (route.player, ruler)

  private def rewrite(ready: ReadyGame, traveller: PlayerId, ruler: SiteRuler,
      operations: Vector[Operation]): Vector[Operation] =
    val payment = operations.collect {
      case spend @ SpendSupply(player, _, _) if player == traveller => spend }
    val supply = payment.map(_.amount).sum
    val board = ready.game.current.players.find(_.player == traveller)
      .map(_.board)
    if supply == 0 then operations
    else if ruler == SiteRuler.Player(traveller) then
      SupplyStrip(traveller, operations)
    else if !board.exists(_.favor >= WayStation.favorOwed(operations, traveller) +
        WayStation.Favor) then operations
    else
      val options = Vector(
        Option.when(board.exists(_.supply.supply >= supply))(
          DecisionOption.Button(WayStation.supplyOption,
            s"Spend $supply Supply")),
        Some(DecisionOption.Button(WayStation.favorOption, ruler match
          case SiteRuler.Player(owner) => s"Give 1 favor to ${owner.value}"
          case _ => "Burn 1 favor"))).flatten
      Vector[Operation](
        Decide(WayStation.decisionId, traveller, DecisionQuery.ChooseOne(
          options, heading = Some("Way Station: spend no Supply?")),
          presentation = DecisionPresentation.Panel)) ++
        TravelRoute.beforeMove(SupplyStrip(traveller, operations), Vector(
          Branch((_, pending) =>
            PowerAnswers.one(pending, WayStation.decisionId) match
              case Some(WayStation.favorOption) => Vector(
                RulerToll.payment(traveller, ruler),
                Note(id, RulerToll.note(cardId, traveller, ruler)))
              case Some(_) => payment
              case None => Vector.empty)))

object WayStation:
  /** The favor `operations` already take from `traveller` (a toll), which
    * the favor answer must leave. A Travel modifier's payment is not among
    * them: it was paid at the pick, before this node. Toll Roads' step
    * counts as owed, though its ruler may waive it. */
  private[travel] def favorOwed(operations: Vector[Operation],
      traveller: PlayerId): Int = operations.collect {
    case step: Decide if step.decisionId == TollRoads.decisionId &&
        step.owner == traveller => TollRoads.Favor
  }.sum + RulerToll.favorTaken(operations, traveller)

  val id: PowerId = WayStationCard.power.id
  val Favor: Int = 1
  /** Under the `travel.` prefix, so the Game Log narrates it. */
  val decisionId: String = "travel.way-station"
  val supplyOption: DecisionOptionRef.Button = DecisionOptionRef.Button("supply")
  val favorOption: DecisionOptionRef.Button = DecisionOptionRef.Button("favor")

  def forCatalog(catalog: ExecutableCatalog): WayStation =
    new WayStation(catalog)
