package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object TravelPowers {
  private val modifier = Some(MajorActionType.Travel)
  private def topology = Vector(ReviewedHandler.automatic(PowerWindow.TravelCost,
    implemented = true))
  object BrokenPeaksMountain extends ReviewedPower("site.broken-peaks.mountain", modifier, topology)
  object DesolateShoreCoast extends ReviewedPower("site.desolate-shore.coast", modifier, topology)
  object FairIsleCoast extends ReviewedPower("site.fair-isle.coast", modifier, topology)
  object FairIsleIsland extends ReviewedPower("site.fair-isle.island", modifier, topology)
  object GreenShoreCoast extends ReviewedPower("site.green-shore.coast", modifier, topology)
  object HeadwatersMountain extends ReviewedPower("site.headwaters.mountain", modifier, topology)
  object HiddenPlaceMountain extends ReviewedPower("site.hidden-place.mountain", modifier, topology)
  object MinesMountain extends ReviewedPower("site.mines.mountain", modifier, topology)
  object NarrowPass extends ReviewedPower("site.narrow-pass.pass", modifier, topology)
  object RockyCoast extends ReviewedPower("site.rocky-coast.coast", modifier, topology)
  object SunkenIslesCoast extends ReviewedPower("site.sunken-isles.coast", modifier, topology)
  object SunkenIslesIsland extends ReviewedPower("site.sunken-isles.island", modifier, topology)
  object TidalMarshesCoast extends ReviewedPower("site.tidal-marshes.coast", modifier, topology)
  val powers: Vector[Power] = Vector(BrokenPeaksMountain, DesolateShoreCoast,
    FairIsleCoast, FairIsleIsland, GreenShoreCoast, HeadwatersMountain,
    HiddenPlaceMountain, MinesMountain, NarrowPass, RockyCoast,
    SunkenIslesCoast, SunkenIslesIsland, TidalMarshesCoast)
}
