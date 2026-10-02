package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object TentsCard extends Denizen(DenizenId("29"), "Tents", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.tents"),
    persistent = false, cost = Cost(favor = 1),
    text = "Spend no Supply if you're traveling to a site in your " +
      "region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tents (card 29), a selected Travel modifier. Cost: 1 favor placed on the
  * card. If the destination is in the region of the pawn's current site, Travel
  * costs no Supply.
  *
  * The player owns the choice to select it. Selecting Tents places the favor at
  * the start of every Travel (the kit does), whether or not the destination is
  * in the pawn's region, and the Supply payment (terrain adds included) is
  * removed only when it is.
  */
final case class Tents private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = TentsCard.id
  def id: PowerId = Tents.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = Tents.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if sameRegion(ctx) then
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def sameRegion(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists { route =>
      val source = TravelPayments.region(ctx.state, route.source)
      source.nonEmpty &&
        source == TravelPayments.region(ctx.state, route.destination)
    }

object Tents:
  val id: PowerId = TentsCard.power.id
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Tents =
    new Tents(catalog)
