package oathdigital.model

/**
 * Up to three ordered items in storage at a site: denizens, relics or an
 * edifice. Order is kept but not meaningful between games (2026-09-21
 * Chronicle design, "The Chronicle model").
 */
final case class StoredSite(site: SiteId, items: Vector[CardId] = Vector.empty) {
  require(items.size <= 3, "a stored site holds at most three items")
}

/**
 * The between-game record. Setup becomes a pure function of a Chronicle plus
 * recorded shuffle orders (2026-09-21 Chronicle design). Fields mirror the
 * Oath NF TTS Chronicle export format
 * (https://github.com/harsch1/oath-nf-tts-scripts/blob/main/chronicle-codec.js)
 * so an eventual codec can import and export the same strings; this project
 * does not parse that string format yet.
 *
 * `atlasBox` and `world` together account for every site: `atlasBox` holds
 * sites in storage (index 0 is the Recent end) and `world` holds the
 * Empire's sites, which stay on the map into the next game. Deck order
 * (`worldDeck`, `relicDeck`) is kept but not meaningful between games --
 * setup shuffles both. `reliquary`, `foundations` and `lineages` are empty
 * or default for a first game.
 */
final case class Chronicle(
    atlasBox: Vector[StoredSite],
    world: Vector[StoredSite] = Vector.empty,
    worldDeck: Vector[DenizenId],
    relicDeck: Vector[RelicId],
    dispossessed: Vector[DenizenId] = Vector.empty,
    reliquary: Vector[RelicId] = Vector.empty,
    foundations: Map[FoundationNumber, FoundationState] = Map.empty,
    lineages: Vector[LineageState] = Vector.empty
)
