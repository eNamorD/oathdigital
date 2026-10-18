package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite,
  Transform}
import oathdigital.gameplay.powers.banner.CardPowerBanners
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object DisciplesCard extends Denizen(DenizenId("205"), "Disciples", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.disciples"),
    persistent = false, cost = Cost.free,
    text = "If you have the Darkest Secret, spend only 2 Supply if " +
      "you're searching the world deck.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Disciples (card 205), a selected Search modifier with no cost: "If you
  * have the Darkest Secret, spend only 2 Supply if you're searching the world
  * deck."
  *
  * The Search's cost is rewritten so that a Supply payment above 2, wherever
  * the cost holds it, is lowered to 2 while the player holds the Darkest
  * Secret.
  * The cost node does not name its source, but a regional discard always
  * costs 2, so only a world-deck Search costs more: lowering any higher cost
  * to 2 is exactly the card. The holder is read when the cost is paid, since
  * the Search builds its payment when it runs. It may be selected whatever
  * the source, as the Cup of Plenty may. The start line's cost span shows
  * what was paid, so it writes no line.
  */
final case class Disciples private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = DisciplesCard.id
  def id: PowerId = Disciples.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((ready, _) => {
        case pay @ SpendSupply(player, amount, _)
            if player == ctx.actor && amount > Disciples.Supply &&
              CardPowerBanners.holdsDarkestSecret(ready, player) =>
          Vector(pay.copy(amount = Disciples.Supply))
      }))))

object Disciples:
  val id: PowerId = DisciplesCard.power.id
  /** What a world-deck Search costs its user while holding the Darkest
    * Secret. */
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Disciples =
    new Disciples(catalog)
