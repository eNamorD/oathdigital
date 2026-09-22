package oathdigital.model

class ChronicleSuite extends munit.FunSuite {
  // atlasBox: 22 stored sites, four of them carrying 1-2 items (relics, in
  // this TTS sample).
  private val atlasBox = Vector(
    StoredSite(SiteId("Sunken Isles")),
    StoredSite(SiteId("Dunes")),
    StoredSite(SiteId("Riverbank")),
    StoredSite(SiteId("Green Shore"), Vector(RelicId("Amber Doors"))),
    StoredSite(SiteId("Fair Isle")),
    StoredSite(SiteId("Headwaters"), Vector(RelicId("Dowsing Sticks"))),
    StoredSite(SiteId("Golden Valley")),
    StoredSite(SiteId("Great Slum")),
    StoredSite(SiteId("Standing Stones")),
    StoredSite(SiteId("Hidden Place"), Vector(RelicId("Grand Mask"))),
    StoredSite(SiteId("Solitary Pillar"),
      Vector(RelicId("Obsidian Cage"), RelicId("Wine of Welcome"))),
    StoredSite(SiteId("Rocky Coast"), Vector(RelicId("Marble Fountains"))),
    StoredSite(SiteId("Broken Peaks")),
    StoredSite(SiteId("Ancient City")),
    StoredSite(SiteId("Shrouded Woods"),
      Vector(RelicId("Bone Dice"), RelicId("Bandit Crown"))),
    StoredSite(SiteId("Buried Giant"), Vector(RelicId("Skeleton Key"))),
    StoredSite(SiteId("Mines"), Vector(RelicId("Bag of Siegeworks"))),
    StoredSite(SiteId("Steppe")),
    StoredSite(SiteId("Salt Flats"), Vector(RelicId("Black Sword"))),
    StoredSite(SiteId("Desolate Shore"),
      Vector(RelicId("Amber Flame"), RelicId("Yew Staff"))),
    StoredSite(SiteId("Deep Woods")),
    StoredSite(SiteId("Narrow Pass"), Vector(RelicId("Ancient Writ")))
  )

  // world: 2 Empire sites still on the map, each carrying its resident cards.
  private val world = Vector(
    StoredSite(SiteId("Painted Towers"), Vector(
      DenizenId("Forest Horn"), DenizenId("Hidden Passages"),
      DenizenId("Stone Portal"))),
    StoredSite(SiteId("Tidal Marshes"), Vector(
      DenizenId("New Growth"), DenizenId("Roving Terror")))
  )

  private val worldDeckNames = Vector(
    "Careful Plans", "Keep", "Spell Breaker", "Great Crusade", "Vow of Silence",
    "Map Library", "Rowdy Pub", "Downtrodden", "Disciples", "Second Chance",
    "Chaos Cult", "Ward of Silence", "Reliquary Raid", "Bed of Roots",
    "Ancient Bloodline", "Revelation", "Cracked Sage", "Palanquin",
    "Great Feast", "Warning Signals", "Naysayers", "Dissent", "Deed Writer",
    "Watchdog", "War Tortoise", "Grasping Vines", "Ancient Pact",
    "Autumn Wind", "Military Parade", "Bog", "Tents", "Book Binders",
    "Whispering Leaves", "Royal Stables", "Lost Tongue", "Long-Lost Heir",
    "Fabled Feast", "Wild Mounts", "Storm Caller", "Town Meeting",
    "Insect Swarm", "Glamor", "Disgraced Captain", "Relic Hunter",
    "Moving Market", "League Treaty", "Billowing Fog", "A Small Favor",
    "Hunting Party", "Defame", "The Old Oak", "City Wall", "Royal Tax",
    "Zealots", "Silver Tongue")

  private val relicDeckNames = Vector(
    "Barbed Net", "Demon Tail", "Ivory Eye", "Cup of Plenty",
    "Secret Testament", "Brass Army", "Painted Trumpet", "Imperial Seal",
    "Circlet of Command", "Dragonskin Drum", "Brass Horse", "Magic Carpet",
    "Clay Rattle", "Bandit Standard", "Magic Waterskin", "Shifting Map",
    "Singing Mask", "Ring of Devotion", "Truthful Harp", "Whispering Stone",
    "Crystal Vial", "Sticky Fire", "Horned Mask", "Weeping Banner",
    "Book of Records", "Oracular Pig", "Silver Charm", "Cursed Cauldron",
    "Sigil of the Heart", "Fearsome Shield")

  private val dispossessedNames = Vector(
    "Birdsong", "A Round of Ale", "Taming Charm", "Ancient Binding",
    "Family Heirloom", "Saddle Makers", "Bandit Chief", "Sealing Ward",
    "Marsh Spirit", "Alchemist", "Key to the City", "Council Arbiter",
    "A Fast Steed", "Small Friends", "Vow of Wandering", "Dream Thief",
    "Pressgangs", "Peace Envoy", "Resettle", "Vow of Obedience",
    "Plague Engines", "Blackmail", "Tavern Songs", "Fae Battalion",
    "Captains", "Horse Archers", "Master of Disguise", "Memory of Home",
    "Garrison", "Traveling Doctor", "Relic Worship", "Forest Council",
    "Vow of Division", "Inquisitor", "Wizard's Conclave", "Banner Breakers",
    "Bandit Prince", "Walled Garden", "News from Afar", "Mushrooms",
    "Arcane Brokers", "Salt the Earth")

  private val chronicle = Chronicle(
    atlasBox,
    world = world,
    worldDeck = worldDeckNames.map(DenizenId(_)),
    relicDeck = relicDeckNames.map(RelicId(_)),
    dispossessed = dispossessedNames.map(DenizenId(_))
  )

  test("constructs from the TTS sample's shape with its section sizes") {
    assertEquals(chronicle.atlasBox.size, 22)
    assertEquals(chronicle.world.size, 2)
    assertEquals(chronicle.worldDeck.size, 55)
    assertEquals(chronicle.relicDeck.size, 30)
    assertEquals(chronicle.dispossessed.size, 42)
    // Every site across both sections is unique: the sample's 24 sites, none
    // both in play and in storage.
    assertEquals((chronicle.atlasBox ++ chronicle.world).map(_.site).distinct.size, 24)
  }

  test("a stored site holds at most three items") {
    intercept[IllegalArgumentException] {
      StoredSite(SiteId("x"), Vector(RelicId("a"), RelicId("b"),
        RelicId("c"), RelicId("d")))
    }
  }

  test("reliquary, foundations and lineages default empty for a first game") {
    assertEquals(chronicle.reliquary, Vector.empty)
    assertEquals(chronicle.foundations, Map.empty[FoundationNumber, FoundationState])
    assertEquals(chronicle.lineages, Vector.empty)
  }
}
