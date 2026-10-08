package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object NewsFromAfarCard extends Denizen(DenizenId("134"), "News from Afar", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.news-from-alfar"),
    persistent = false, cost = Cost(favor = 2),
    text = "Spend no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** News from Afar (card 134), a selected Search modifier. Cost: 2 favor
  * placed on the card. The Search spends no Supply, whatever its source.
  *
  * The Search's cost node is rewritten, as Disciples rewrites it, so that it
  * drops the player's Supply payment when it runs. A toll the node also
  * holds, such as Forced Labor's, is a separate operation and is still paid.
  * The start line's cost span shows what was paid, so it writes no line.
  */
final case class NewsFromAfar private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = NewsFromAfarCard.id
  def id: PowerId = NewsFromAfar.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = NewsFromAfar.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx.activePlayer, operations))))

object NewsFromAfar:
  val id: PowerId = NewsFromAfarCard.power.id
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): NewsFromAfar =
    new NewsFromAfar(catalog)
