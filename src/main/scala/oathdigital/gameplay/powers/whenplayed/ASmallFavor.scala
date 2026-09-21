package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** A Small Favor (card 15), WHEN PLAYED: gain four warbands. The gain is
  * optional, so it is capped by what the warband bank still holds.
  */
final case class ASmallFavor private (cardId: DenizenId)
    extends WhenPlayedPower {
  def id: PowerId = ASmallFavor.id

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(BuildOps((ready, _) =>
    PlayerFacts.forceKind(ready, ctx.activePlayer).map(kind => Vector(
      Gain.Warbands(ctx.activePlayer, kind, ASmallFavor.Warbands)))))
}

object ASmallFavor {
  val id: PowerId = PowerId("denizen.a-small-favor")
  val Warbands: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[ASmallFavor] =
    WhenPlayedPower.cardOf(catalog, id).map(new ASmallFavor(_))
}
