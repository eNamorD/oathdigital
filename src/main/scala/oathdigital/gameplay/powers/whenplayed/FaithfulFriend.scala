package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Faithful Friend (card 28), WHEN PLAYED: gain 4 Supply. `GainSupply`
  * clamps at the track maximum.
  */
final case class FaithfulFriend private (cardId: DenizenId)
    extends WhenPlayedPower {
  def id: PowerId = FaithfulFriend.id

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(GainSupply(ctx.activePlayer, FaithfulFriend.Supply))
}

object FaithfulFriend {
  val id: PowerId = PowerId("denizen.faithful-friend")
  val Supply: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[FaithfulFriend] =
    WhenPlayedPower.cardOf(catalog, id).map(new FaithfulFriend(_))
}
