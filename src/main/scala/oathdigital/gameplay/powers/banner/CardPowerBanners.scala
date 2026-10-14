package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The banners as a card power sees them for the player using it (catalog
  * batch 8, slice 9). Only card powers read it. Challenge, scoring, the
  * Oathkeeper goal and every other rule read `BannerRules`.
  */
object CardPowerBanners:
  /** A power that lets its holder act as if they hold the Darkest Secret
    * while using card powers. */
  trait DarkestSecretOverride:
    def actsAsDarkestSecretHolder(ready: ReadyGame, player: PlayerId)
        : Boolean

  /** Every override in the catalog. */
  private val overrides: Vector[DarkestSecretOverride] = Vector(YewStaff)

  /** Whether `player` holds the Darkest Secret, as a card power they use
    * asks it: they hold the banner, or an override says they act as if
    * they do. */
  def holdsDarkestSecret(ready: ReadyGame, player: PlayerId): Boolean =
    BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player) ||
      overrides.exists(_.actsAsDarkestSecretHolder(ready, player))
