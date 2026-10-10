package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ErrandBoyCard extends Denizen(DenizenId("11"), "Errand Boy", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.errand-boy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may draw from a discard pile in a different region " +
      "instead of yours.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Errand Boy (card 11), a selected Search modifier costing 1 favor placed
  * on it, paid when it is picked whatever the pile. The other regions'
  * discard piles are offered at the source decision, each at 2 Supply,
  * after the printed two and in region order (catalog batch 6, slice 3).
  *
  * The actor's own pile and the world deck stay choosable, warned that
  * Errand Boy does nothing there. The warning names the actor's region,
  * which the `warning` hook cannot see, so the Transform marks it. It
  * writes no line: the start line names it, and the Search line names the
  * pile.
  */
final case class ErrandBoy private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ErrandBoyCard.id
  def id: PowerId = ErrandBoy.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = ErrandBoy.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      SearchProcedure.actorRegion(ctx.state, ctx.activePlayer)
        .fold(_ => operations, offered(ctx, operations, _)))))

  /** The other regions' piles added, and the sources it leaves alone
    * warned. */
  private def offered(ctx: PowerCtx, operations: Vector[Operation],
      own: Region): Vector[Operation] =
    val idle: Set[DecisionOptionRef] = Set[SearchSource](
      SearchSource.WorldDeck, SearchSource.RegionalDiscard(own))
      .map(SearchSource.ref)
    SearchSources.warned(ctx, SearchSources.withPiles(ctx, operations,
      Region.all.filterNot(_ == own)), idle, ErrandBoy.idle)

object ErrandBoy:
  val id: PowerId = ErrandBoyCard.power.id
  val Favor: Int = 1
  val idle: String = "Errand Boy does nothing here"

  def forCatalog(catalog: ExecutableCatalog): ErrandBoy =
    new ErrandBoy(catalog)
