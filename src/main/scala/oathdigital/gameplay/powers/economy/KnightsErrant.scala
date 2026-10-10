package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object KnightsErrantCard extends Denizen(DenizenId("120"), "Knights Errant", Suit.Order):
  val power = PrintedPower(PowerId("denizen.knights-errant"),
    persistent = false, cost = Cost.free,
    text = "After mustering, you may campaign, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Knights Errant (card 120), a free selected Muster modifier: after the
  * Muster's gain, a Campaign is owed to its player, spending no Supply
  * (follow-up actions design, "Cards"). The queued Campaign is a full action
  * of its own: its player starts or skips it once the Muster ends, may
  * select modifiers for it, and sees it disabled with why when it cannot
  * start. The queue is unconditional, so a restriction such as Vow of Peace
  * shows on the queued Campaign's button, not here.
  */
final case class KnightsErrant private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = KnightsErrantCard.id
  def id: PowerId = KnightsErrant.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ QueueProcedure(QueuedProcedure(ctx.activePlayer,
        ActionRef.Campaign, Vector.empty, Some(PowerSourceRef.Card(cardId)),
        QueueKind.Offer)))))

object KnightsErrant:
  val id: PowerId = KnightsErrantCard.power.id

  def forCatalog(catalog: ExecutableCatalog): KnightsErrant =
    new KnightsErrant(catalog)
