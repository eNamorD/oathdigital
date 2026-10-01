package oathdigital.catalog.holding

import oathdigital.catalog.{Site}
import oathdigital.model.{SiteId, Suit, Tokens}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object AncientCitySite extends Site(SiteId("site:ancient-city"), "Ancient City",
    defense = 2, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(3, 0)),
    homeland = None,
    handlers = Vector("site.ancient-city.enduring", "site.ancient-city.river"))

object BrokenPeaksSite extends Site(SiteId("site:broken-peaks"), "Broken Peaks",
    defense = 2, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 2),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.broken-peaks.mountain"))

object BuriedGiantSite extends Site(SiteId("site:buried-giant"), "Buried Giant",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.buried-giant.plains"))

object DeepWoodsSite extends Site(SiteId("site:deep-woods"), "Deep Woods",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(1, 2)),
    homeland = Some(Suit.Beast),
    handlers = Vector("site.deep-woods.homeland-beast"))

object DesolateShoreSite extends Site(SiteId("site:desolate-shore"), "Desolate Shore",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.desolate-shore.coast"))

object DunesSite extends Site(SiteId("site:dunes"), "Dunes",
    defense = 1, capacity = 1, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.dunes.plains"))

object FairIsleSite extends Site(SiteId("site:fair-isle"), "Fair Isle",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(3),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.fair-isle.coast", "site.fair-isle.island"))

object GoldenValleySite extends Site(SiteId("site:golden-valley"), "Golden Valley",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(3, 0)),
    homeland = Some(Suit.Hearth),
    handlers = Vector("site.golden-valley.homeland-hearth"))

object GreatSlumSite extends Site(SiteId("site:great-slum"), "Great Slum",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(2, 1)),
    homeland = Some(Suit.Discord),
    handlers = Vector("site.great-slum.homeland-discord"))

object GreenShoreSite extends Site(SiteId("site:green-shore"), "Green Shore",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.green-shore.coast"))

object HeadwatersSite extends Site(SiteId("site:headwaters"), "Headwaters",
    defense = 1, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.headwaters.river", "site.headwaters.mountain"))

object HiddenPlaceSite extends Site(SiteId("site:hidden-place"), "Hidden Place",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.hidden-place.mountain"))

object MinesSite extends Site(SiteId("site:mines"), "Mines",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.mines.mountain"))

object NarrowPassSite extends Site(SiteId("site:narrow-pass"), "Narrow Pass",
    defense = 2, capacity = 1, relicSlots = 1,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.narrow-pass.pass"))

object PaintedTowersSite extends Site(SiteId("site:painted-towers"), "Painted Towers",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(2, 1)),
    homeland = Some(Suit.Order),
    handlers = Vector("site.painted-towers.homeland-order"))

object RiverbankSite extends Site(SiteId("site:riverbank"), "Riverbank",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.riverbank.river"))

object RockyCoastSite extends Site(SiteId("site:rocky-coast"), "Rocky Coast",
    defense = 2, capacity = 1, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.rocky-coast.coast"))

object SaltFlatsSite extends Site(SiteId("site:salt-flats"), "Salt Flats",
    defense = 0, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(2),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.salt-flats.plains"))

object ShroudedWoodsSite extends Site(SiteId("site:shrouded-woods"), "Shrouded Woods",
    defense = 2, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(1, 1),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.shrouded-woods.plains"))

object SolitaryPillarSite extends Site(SiteId("site:solitary-pillar"), "Solitary Pillar",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 2),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.solitary-pillar.plains"))

object StandingStonesSite extends Site(SiteId("site:standing-stones"), "Standing Stones",
    defense = 1, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(0, 3)),
    homeland = Some(Suit.Arcane),
    handlers = Vector("site.standing-stones.homeland-arcane"))

object SteppeSite extends Site(SiteId("site:steppe"), "Steppe",
    defense = 0, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(0, 3)),
    homeland = Some(Suit.Nomad),
    handlers = Vector("site.steppe.homeland-nomad"))

object SunkenIslesSite extends Site(SiteId("site:sunken-isles"), "Sunken Isles",
    defense = 2, capacity = 0, relicSlots = 3,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.sunken-isles.coast", "site.sunken-isles.island"))

object TidalMarshesSite extends Site(SiteId("site:tidal-marshes"), "Tidal Marshes",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None,
    handlers = Vector("site.tidal-marshes.river", "site.tidal-marshes.coast"))
