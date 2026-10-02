package oathdigital.gameplay.powers.banner

import oathdigital.model._

/** Staging shared by the banner-face suites, on the quiet table: the actor
  * p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant. Both
  * banners start unheld, on Mob and Wandering Flame, each with one token.
  * The suites set a banner's holder and face with `Table.darkestSecret` and
  * `Table.peoplesFavor`, and read the parked decision and the recorded
  * operations through `PhasePowerHarness`.
  */
object BannerFixture:
  /** The Darkest Secret, as a decision option. */
  val darkestSecretOption: DecisionOptionRef =
    DecisionOptionRef.Banner(Banner.DarkestSecret)
