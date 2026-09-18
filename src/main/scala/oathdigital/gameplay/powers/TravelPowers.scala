package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.{MajorActionType, PowerWindow}

/** Reviewed-but-unimplemented Travel classifications.
  *
  * Every terrain rule these ids name is now a `ContributingPower` on the
  * generic walker (see
  * [[oathdigital.gameplay.powers.travel.TravelSitePowers]], wired through
  * [[oathdigital.gameplay.powers.WalkerPowerCatalog]]): Mountain and Island
  * are `Transform`s that raise the pay node, Coast is a `Transform` that
  * replaces it and names the adds it ignores, and Narrow Pass is a
  * `Restriction` returning `OathViolation.TravelPassBlocked` directly.
  *
  * These entries remain so the audited-power inventory stays complete -- the
  * same reason [[RecoverPowers]]' four entries remain after Catacombs moved.
  *
  * Batch-1 Task 5 retired the whole typed-cost vocabulary they used to carry.
  * Each power declared a terrain kind; the kind owned a typed cost fact (add
  * this much, or replace with this much); a window fold read those facts and
  * summed them; a separate global registry held the coast route's rule for
  * ignoring the others; and a codec round-tripped the pass's typed violation
  * through a reason string, because the restriction seam that predated
  * `Restriction` could only return one. A `Transform` over the pay node says
  * each of those things directly, so none of that vocabulary survives to say
  * them a second way.
  */
object TravelPowers {
  private val modifier = Some(MajorActionType.Travel)
  private def topology = Vector(ReviewedHandler.automatic(
    PowerWindow.TravelCost, implemented = true))

  private final case class TerrainPower(idValue: String)
      extends ReviewedPower(idValue, modifier, topology)

  val powers: Vector[Power] = Vector(
    "site.broken-peaks.mountain", "site.desolate-shore.coast",
    "site.fair-isle.coast", "site.fair-isle.island", "site.green-shore.coast",
    "site.headwaters.mountain", "site.hidden-place.mountain",
    "site.mines.mountain", "site.narrow-pass.pass", "site.rocky-coast.coast",
    "site.sunken-isles.coast", "site.sunken-isles.island",
    "site.tidal-marshes.coast").map(TerrainPower)
}
