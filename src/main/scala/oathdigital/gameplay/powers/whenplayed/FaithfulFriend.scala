package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
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
case object FaithfulFriend extends WhenPlayedPower:
  val cardId: DenizenId = FaithfulFriendCard.id
  val id: PowerId = FaithfulFriendCard.power.id
  val Supply: Int = 4

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(GainSupply(ctx.activePlayer, FaithfulFriend.Supply))
