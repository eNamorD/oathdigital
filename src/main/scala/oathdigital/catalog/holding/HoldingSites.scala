package oathdigital.catalog.holding

import oathdigital.catalog.Site
import oathdigital.model.{PowerId, SiteId, Suit, Tokens}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object AncientCitySite extends Site(SiteId("site:ancient-city"), "Ancient City",
    defense = 2, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(3, 0)),
    homeland = None):
  val enduring = PowerId("site.ancient-city.enduring")
  val river = PowerId("site.ancient-city.river")
  val handlers: Vector[PowerId] = Vector(enduring, river)

object BrokenPeaksSite extends Site(SiteId("site:broken-peaks"), "Broken Peaks",
    defense = 2, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 2),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.broken-peaks.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object BuriedGiantSite extends Site(SiteId("site:buried-giant"), "Buried Giant",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val plains = PowerId("site.buried-giant.plains")
  val handlers: Vector[PowerId] = Vector(plains)

object DeepWoodsSite extends Site(SiteId("site:deep-woods"), "Deep Woods",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(1, 2)),
    homeland = Some(Suit.Beast)):
  val homelandPower = PowerId("site.deep-woods.homeland-beast")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object DesolateShoreSite extends Site(SiteId("site:desolate-shore"), "Desolate Shore",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.desolate-shore.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object DunesSite extends Site(SiteId("site:dunes"), "Dunes",
    defense = 1, capacity = 1, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val plains = PowerId("site.dunes.plains")
  val handlers: Vector[PowerId] = Vector(plains)

object FairIsleSite extends Site(SiteId("site:fair-isle"), "Fair Isle",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(3),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.fair-isle.coast")
  val island = PowerId("site.fair-isle.island")
  val handlers: Vector[PowerId] = Vector(coast, island)

object GoldenValleySite extends Site(SiteId("site:golden-valley"), "Golden Valley",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(3, 0)),
    homeland = Some(Suit.Hearth)):
  val homelandPower = PowerId("site.golden-valley.homeland-hearth")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object GreatSlumSite extends Site(SiteId("site:great-slum"), "Great Slum",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(2, 1)),
    homeland = Some(Suit.Discord)):
  val homelandPower = PowerId("site.great-slum.homeland-discord")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object GreenShoreSite extends Site(SiteId("site:green-shore"), "Green Shore",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.green-shore.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object HeadwatersSite extends Site(SiteId("site:headwaters"), "Headwaters",
    defense = 1, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.headwaters.river")
  val mountain = PowerId("site.headwaters.mountain")
  val handlers: Vector[PowerId] = Vector(river, mountain)

object HiddenPlaceSite extends Site(SiteId("site:hidden-place"), "Hidden Place",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.hidden-place.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object MinesSite extends Site(SiteId("site:mines"), "Mines",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.mines.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object NarrowPassSite extends Site(SiteId("site:narrow-pass"), "Narrow Pass",
    defense = 2, capacity = 1, relicSlots = 1,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val pass = PowerId("site.narrow-pass.pass")
  val handlers: Vector[PowerId] = Vector(pass)

object PaintedTowersSite extends Site(SiteId("site:painted-towers"), "Painted Towers",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(2, 1)),
    homeland = Some(Suit.Order)):
  val homelandPower = PowerId("site.painted-towers.homeland-order")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object RiverbankSite extends Site(SiteId("site:riverbank"), "Riverbank",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.riverbank.river")
  val handlers: Vector[PowerId] = Vector(river)

object RockyCoastSite extends Site(SiteId("site:rocky-coast"), "Rocky Coast",
    defense = 2, capacity = 1, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.rocky-coast.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object SaltFlatsSite extends Site(SiteId("site:salt-flats"), "Salt Flats",
    defense = 0, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(2),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val plains = PowerId("site.salt-flats.plains")
  val handlers: Vector[PowerId] = Vector(plains)

object ShroudedWoodsSite extends Site(SiteId("site:shrouded-woods"), "Shrouded Woods",
    defense = 2, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(1, 1),
    forgeRequirements = None,
    homeland = None):
  val plains = PowerId("site.shrouded-woods.plains")
  val handlers: Vector[PowerId] = Vector(plains)

object SolitaryPillarSite extends Site(SiteId("site:solitary-pillar"), "Solitary Pillar",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 2),
    forgeRequirements = None,
    homeland = None):
  val plains = PowerId("site.solitary-pillar.plains")
  val handlers: Vector[PowerId] = Vector(plains)

object StandingStonesSite extends Site(SiteId("site:standing-stones"), "Standing Stones",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(0, 3)),
    homeland = Some(Suit.Arcane)):
  val homelandPower = PowerId("site.standing-stones.homeland-arcane")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object SteppeSite extends Site(SiteId("site:steppe"), "Steppe",
    defense = 0, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(0, 3)),
    homeland = Some(Suit.Nomad)):
  val homelandPower = PowerId("site.steppe.homeland-nomad")
  val handlers: Vector[PowerId] = Vector(homelandPower)

object SunkenIslesSite extends Site(SiteId("site:sunken-isles"), "Sunken Isles",
    defense = 2, capacity = 0, relicSlots = 3,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.sunken-isles.coast")
  val island = PowerId("site.sunken-isles.island")
  val handlers: Vector[PowerId] = Vector(coast, island)

object TidalMarshesSite extends Site(SiteId("site:tidal-marshes"), "Tidal Marshes",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.tidal-marshes.river")
  val coast = PowerId("site.tidal-marshes.coast")
  val handlers: Vector[PowerId] = Vector(river, coast)
