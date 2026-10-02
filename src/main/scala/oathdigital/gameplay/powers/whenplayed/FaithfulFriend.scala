package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object FaithfulFriendCard extends Denizen(DenizenId("28"), "Faithful Friend", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.faithful-friend"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain 4 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Faithful Friend (card 28), WHEN PLAYED: gain 4 Supply. `GainSupply`
  * clamps at the track maximum.
  */
final case class FaithfulFriend private (cardId: DenizenId)
    extends WhenPlayedPower:
  def id: PowerId = FaithfulFriend.id

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(GainSupply(ctx.activePlayer, FaithfulFriend.Supply))

object FaithfulFriend:
  val id: PowerId = PowerId("denizen.faithful-friend")
  val Supply: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[FaithfulFriend] =
    WhenPlayedPower.cardOf(catalog, id).map(new FaithfulFriend(_))
