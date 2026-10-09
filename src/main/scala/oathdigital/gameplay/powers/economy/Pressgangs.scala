package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object PressgangsCard extends Denizen(DenizenId("6"), "Pressgangs", Suit.Order):
  val power = PrintedPower(PowerId("denizen.pressgangs"),
    persistent = false, cost = Cost.free,
    text = "You can muster on cards that already have [favor] or " +
      "[secret] on them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Pressgangs (card 6), a selected Muster modifier with no cost: the Muster
  * may draw on a card that holds any number of favor or secrets.
  *
  * It folds after the powers that add sources
  * ([[EconomySources.ExemptionPriority]]) and makes every source the
  * token-free rule withheld available, so with Golem Legions selected too,
  * Golem Legions may be mustered on twice in one turn. The Muster's payment
  * onto the answered source is marked `intoOccupied`. It does not apply to
  * a Trade. It writes no line: the start line names it.
  */
final case class Pressgangs private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = PressgangsCard.id
  def id: PowerId = Pressgangs.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)
  override def priority: Int = EconomySources.ExemptionPriority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
      EconomySources.exempted(operations, _ => true))),
    PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
      EconomySources.intoOccupied(operations, ctx.activePlayer,
        MusterProcedure.decisionId, ctx.answered))))

object Pressgangs:
  val id: PowerId = PressgangsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Pressgangs =
    new Pressgangs(catalog)
