package oathdigital.gameplay.powers.recover

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object RelicWorshipCard extends Denizen(DenizenId("173"), "Relic Worship", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.relic-worship"),
    persistent = false, cost = Cost(secret = 1),
    text = "After recovering a relic, gain 2 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Relic Worship (card 173), a selected Recover modifier. Cost: 1 secret placed
  * on the card. After recovering a relic, gain 2 Supply.
  *
  * The secret is paid when it is picked in the walk's modifier decision
  * (the kit does it), and the gain follows the move that takes the relic, at
  * `RecoverAfterRelic`. A Recover that ends without a relic has still paid the
  * secret and gains nothing: the player owns the choice to pick it. Picking
  * needs a faceup secret and an empty card. Once one pick spends the last
  * faceup secret, the decision lists the other modifiers unavailable with the
  * shortfall.
  */
final case class RelicWorship private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = RelicWorshipCard.id
  def id: PowerId = RelicWorship.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Recover)
  override def cost: Cost = Cost(secret = RelicWorship.Secrets)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.RecoverAfterRelic -> Vector(Transform((ctx, operations) =>
      operations :+ GainSupply(ctx.activePlayer, RelicWorship.Supply))))

object RelicWorship:
  val id: PowerId = RelicWorshipCard.power.id
  val Secrets: Int = 1
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): RelicWorship =
    new RelicWorship(catalog)
