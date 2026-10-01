package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Disciples (card 205), a selected Search modifier with no cost: "If you
  * have the Darkest Secret, spend only 2 Supply if you're searching the world
  * deck."
  *
  * The Search's cost node is rewritten so that, when it runs, a Supply
  * payment above 2 is lowered to 2 while the player holds the Darkest Secret.
  * The cost node does not name its source, but a regional discard always
  * costs 2, so only a world-deck Search costs more: lowering any higher cost
  * to 2 is exactly the card. The holder is read when the cost is paid. It may
  * be selected whatever the source, as the Cup of Plenty may. The start
  * line's cost span shows what was paid, so it writes no line.
  */
final case class Disciples private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Disciples.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case cost: BuildOps => cost.copy(build = (ready, pending) =>
          cost.build(ready, pending).map(_.map(capped(ready, ctx.activePlayer))))
        case other => other
      })))

  private def capped(ready: ReadyGame, actor: PlayerId)(
      operation: CoreOperation): CoreOperation = operation match
    case pay @ SpendSupply(player, amount, _) if player == actor &&
        amount > Disciples.Supply && BannerRules.holder(ready.game.current,
          Banner.DarkestSecret).contains(actor) =>
      pay.copy(amount = Disciples.Supply)
    case other => other

object Disciples:
  val id: PowerId = PowerId("denizen.disciples")
  /** What a world-deck Search costs its user while holding the Darkest
    * Secret. */
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[Disciples] =
    CatalogCards.denizen(catalog, id).map(new Disciples(_, catalog))
