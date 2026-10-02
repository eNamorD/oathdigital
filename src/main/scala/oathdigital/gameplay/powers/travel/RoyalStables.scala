package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object RoyalStablesCard extends Denizen(DenizenId("245"), "Royal Stables", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.royal-stables"),
    persistent = false, cost = Cost.free,
    text = "Travel costs one less Supply _(minimum 1)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Royal Stables (card 245, site-only), a selected Travel modifier with no
  * cost, usable at the pawn's site or a site the player rules: Travel costs
  * one less Supply, never less than 1.
  *
  * It lowers the amount of the Travel's `SpendSupply`, when the Travel has
  * one. Tents and Forest Paths remove the payment entirely, so with either the
  * Travel stays free. Terrain and the other Travel powers settle the amount
  * first: they all sort at priority 0, and Royal Stables sorts after them.
  */
final case class RoyalStables private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = RoyalStablesCard.id
  def id: PowerId = RoyalStables.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def priority: Int = RoyalStables.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.adjustSupply(operations, ctx.activePlayer)(amount =>
        (amount - RoyalStables.Reduction).max(RoyalStables.Minimum)))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

object RoyalStables:
  val id: PowerId = RoyalStablesCard.power.id
  val Reduction: Int = 1
  val Minimum: Int = 1
  /** Folds after every Travel power at the default priority 0. */
  val Priority: Int = 1

  def forCatalog(catalog: ExecutableCatalog): RoyalStables =
    new RoyalStables(catalog)
