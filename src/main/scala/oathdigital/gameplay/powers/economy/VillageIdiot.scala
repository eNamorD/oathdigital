package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object VillageIdiotCard extends Denizen(DenizenId("231"), "Village Idiot", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.village-idiot"),
    persistent = false, cost = Cost.free,
    text = "After mustering with this card, gain [favor] from the " +
      "[suit-hearth] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Village Idiot (card 231, site-only), a selected Muster modifier with no
  * cost: "After mustering with this card, gain [favor] from the Hearth bank."
  *
  * As for Rowdy Pub, the card mustered on is the answer to the Muster's
  * source decision, read when the gain node runs, so the favor is gained only
  * when Village Idiot is the card. The favor is best-effort: an empty Hearth
  * bank gives nothing. The generic gain line tells it, so it writes no line.
  */
final case class VillageIdiot private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = VillageIdiotCard.id
  def id: PowerId = VillageIdiot.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ favor(ctx.activePlayer))))

  private def favor(actor: PlayerId): Operation = BuildOps((_, pending) =>
    Right(if PowerAnswers.one(pending, MusterProcedure.decisionId)
        .contains(DecisionOptionRef.Denizen(cardId)) then
      Vector[CoreOperation](Gain.Favor(actor, Suit.Hearth, VillageIdiot.Favor))
    else Vector.empty))

object VillageIdiot:
  val id: PowerId = VillageIdiotCard.power.id
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): VillageIdiot =
    new VillageIdiot(catalog)
