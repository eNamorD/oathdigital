# Chronicle and Randomized Setup

> Status: design approved 2026-09-21. Not yet planned. Each slice below gets its own implementation plan.

## Goal and scope

Every alpha game starts from a randomized board with working cards. Setup becomes a pure function of a **Chronicle** (the between-game record) plus recorded shuffle orders, and runs on the procedure walker so that SETUP powers are ordinary contributions. A generator produces a random first-game Chronicle.

In scope:
- A `Chronicle` model shaped to match the Oath NF TTS Chronicle export format, so a later codec can import and export the same strings.
- A random first-game Chronicle generator and a shuffle port with an "implemented first" policy for the alpha.
- Setup from a Chronicle, on the walker, replacing `FirstGameSetupRules`.
- The six parked batch-1 edifice faces: Great Market / Bandit Market (E02), Great Forge / Broken Forge (E06), Proving Grounds / Empty Grounds (E22).

Out of scope, recorded as follow-ups (see the end): the Chronicle string codec, later-game setup, the Chronicle phase that writes a Chronicle back at game end, and WHEN EXPLORED triggers.

## How the rulings were gathered

The design was grilled in four rounds. Every decision below is the product owner's answer or a fact read from the code or from the TTS codec ([chronicle-codec.js](https://github.com/harsch1/oath-nf-tts-scripts/blob/main/chronicle-codec.js)).

## Current state

- `OathGame = (catalog, campaign: CampaignState, current: CurrentGameState)`. `CampaignState` holds the atlas, foundations, lineages, reliquary, dispossessed, suited reserves, Oathkeeper goal and era. During play it is read for lineage roles, the unaltered-Foundation gates, the Oathkeeper goal, and as the atlas, reliquary and dispossessed card containers. Setup always builds it empty.
- Setup is `FirstGameSetupRules`, an event machine separate from the walker. `FirstGameStarted(plan)` records every shuffled order, so neither command handling nor replay has an RNG. `PlacePawn` and `ChooseAdviser` are its commands, with their own intents, codecs, events and frontend controls.
- `DevelopmentFirstGamePlanFactory` builds a deterministic plan (first 8 sites, first 10 denizens per suit, first edifice per suit). Trusted-game provisioning uses it too, so every alpha game today has the same board.
- The materializer places a ruined matching edifice at each Homeland and validates 60 denizens at 10 per suit and the vision packets (10 denizens + 2 Visions, then 15 + 3).
- Only 3 of the 6 batch-1 edifices work. E02, E06 and E22 were parked because no setup or explore hook existed.
- An unimplemented card plays with no effect and records an `IgnoredRulesRecorded` diagnostic.

## The Chronicle model

`Chronicle` is a new between-game value. Its fields mirror the TTS export sections, using this project's catalog ids:

| Field | TTS section | Meaning |
| --- | --- | --- |
| `atlasBox: Vector[StoredSite]` | `atlasBox` | Sites in storage. Index 0 is the Recent end. |
| `world: Vector[StoredSite]` | `world` | The Empire's sites, which stay on the map into the next game. |
| `worldDeck: Vector[DenizenId]` | `worldDeck` | Denizens only; Visions are not part of it. |
| `relicDeck: Vector[RelicId]` | `relicDeck` | Ordinary relics. |
| `dispossessed: Vector[DenizenId]` | `dispossessed` | |
| `reliquary`, `foundations`, lineages | `reliquary`, `foundations`, player sections | Typed; empty or default for a first game. |

`StoredSite(site: SiteId, items: Vector[CardId])` holds up to three ordered items: denizens, relics or edifices.

Rules of the model:
- **Deck order is kept but not meaningful.** Setup shuffles both decks. Keeping the import order lets a future import-then-export reproduce the same string.
- **An edifice has no side between games.** At setup, an edifice at a `world` site starts intact and every other edifice starts ruined. A first game therefore has only ruined edifices.
- **Ids line up with the TTS mapping.** Denizens use their printed number (the same as our catalog ids, 255 cards). Relics are 401 to 447 there and `R`-numbered here; edifices are 501 to 530 there and `E01` to `E30` here; sites are an index by name there. The deferred codec translates.
- `CampaignState` is unchanged. Setup builds it from the Chronicle: atlas entries from `atlasBox` beyond the sites in play, `dispossessed`, `reliquary`, `foundations` and lineages. The decks go to `current.commonCards`, where order matters.

A test builds a `Chronicle` from the TTS codec's sample decoded JSON (its self-test string) to prove the shape fits. It does not parse the string format.

## Randomness

- Shuffling happens in the application layer, through a random port shaped like `CampaignDicePort`. The resolved orders are recorded in the start event. The engine and replay stay RNG-free.
- The shuffle policy is a port. The alpha policy is **implemented first**: for the world deck and for the relic deck, the cards whose powers are all implemented are shuffled and placed on top, and the rest are shuffled below. Regional discards, starting hands and the first packet are dealt from the top, so they draw implemented cards first. A uniform policy replaces it once the catalog is complete. "Implemented" is read from the reviewed power catalog, not from a hand-kept list.
- Unimplemented cards reachable later play with the existing ignored-rule diagnostic.

## The first-game generator

The generator lives in the application layer, draws through the random port, and produces ordinary Chronicle input:

- `atlasBox`: all 24 sites in random order. Each of the 6 Homelands (one per suit) carries its suit's implemented edifice as an item.
- `worldDeck`: 60 denizens, 10 per suit: the 5 implemented denizens of each suit plus 5 random unimplemented ones.
- `dispossessed`: 2 random unimplemented denizens per suit (12), taken from the denizens left after the 60.
- `relicDeck`: all 47 ordinary relics.
- Everything else empty or default.

The generator validates its own output at the end: 60 denizens at 10 per suit, 12 dispossessed at 2 per suit, and every card unique. The 60-card check moves here from setup.

A Homeland outside the first 8 sites keeps its edifice in storage, so a game may show fewer than 6 Homeland edifices. This is the rule outcome; the generator does not force Homelands onto the map.

`DevelopmentFirstGamePlanFactory` becomes a deterministic dev-Chronicle fixture. Trusted-game provisioning switches to the generator.

## Setup from a Chronicle

`GameStarted(chronicle, orders)` replaces `FirstGameStarted(plan)`. `orders` holds the shuffled world deck, relic deck and anything else setup shuffles.

- **Map.** The first 8 `atlasBox` sites, in order: 2 Cradle, 3 Provinces, 3 Hinterland. Each site's stored edifice is placed with its side as above.
- **Deck construction, discards and hands** keep today's rules: 6 denizens to the regional discards, 3 per player as a temporary hand, then the Vision packets.
- **Relics** fill site relic slots from the top of the shuffled relic deck, as today.
- **Validation.** Setup checks only what it needs: known and unique ids, and enough cards to deal. It has no game kind and no suit-count check.
- **Refused** with an `UnsupportedChronicle` violation: a non-empty `world` (needs the Empire), and stored denizens or relics on an `atlasBox` site (needs placement rules not specified yet). Stored edifices are accepted.
- The game is materialized as a `ReadyGame` in a new `Phase.Setup`, with no pawns and no advisers. `pawnSite` is already optional and temporary hands already exist.

## Setup on the walker

A `Setup` procedure runs on the walker:

1. For each player in turn order, from the first player: the player places their pawn at a site in play. The `SetupPawnPlaced` window runs for that site. Then the player chooses one adviser from their three-card temporary hand; the other two go to the discard of the region after the pawn's region, as today.
2. The `SetupEnd` window runs. Its automatic contributions resolve in site order, Cradle to Hinterland, and by position within a site. The actor for powers with no "you" is the first player.
3. The game enters Wake of round 1.

Deleted, as in the other walker ports: `FirstGameSetupRules`, the `PlacePawn` and `ChooseAdviser` commands, intents, codecs and events, the `FirstGameSetupCommand` machinery, and the frontend's setup controls. The walker decision panel shows the setup decisions. Development data is reset, which the pre-release rule allows.

## Setup powers

Each is a `ContributingPower` over existing Operations. Each also names a `WhenExplored` window, which nothing fires until an explore procedure exists. This lets one power serve SETUP and WHEN EXPLORED without branches.

| Edifice | Face | Window | Effect |
| --- | --- | --- | --- |
| E02 Great Market | intact | `SetupEnd` | Place one favor on this site for each denizen in this region, this card included. |
| E02 Bandit Market | ruined | `SetupEnd` | Place one favor on each site ruled by the bandits. Burn one favor from each favor bank. |
| E06 Great Forge | intact | `SetupPawnPlaced` (this site) | The placing player draws a relic from the relic deck and takes it facedown. |
| E06 Broken Forge | ruined | `SetupPawnPlaced` (this site) | Discard all relics at sites in this region. Discarded relics go to `setAsideRelics`. |
| E22 Proving Grounds | intact | `SetupPawnPlaced` (this site) | The placing player gains three warbands. |
| E22 Empty Grounds | ruined | `SetupEnd` | Discard all other denizens in this region. Edifices count as denizens; a discarded ruined edifice returns to the edifice deck. |

A first game places only ruined faces. The intact faces are built now because they share the windows and a Chronicle with a `world` site will need them.

## Slices

1. **Chronicle model, generator and port.** Add `Chronicle`, the random port with the implemented-first policy, and the generator. Feed the existing setup machine from a Chronicle; replace `DevelopmentFirstGamePlanFactory` and switch provisioning to the generator.
2. **Setup on the walker.** `Phase.Setup`, the `Setup` procedure, the two windows, and the deletion of the legacy setup path.
3. **Setup powers.** E02, E06 and E22, both faces.

## Testing

- The Chronicle shape test against the TTS sample JSON.
- Generator invariants: counts per suit, dispossessed cards all unimplemented, uniqueness, every Homeland carrying its implemented edifice, implemented cards on top after the shuffle.
- Replay of `GameStarted` reproduces the same `ReadyGame` with no RNG.
- `UnsupportedChronicle` for each refused shape.
- A walker setup suite: turn order, pawn then adviser, discards of the rejected cards, `SetupEnd` order, entry into Wake.
- One suite per edifice face.

## Follow-ups

- Simultaneous setup effects are resolved by the Chancellor or first player. Site order is used until then.
- Player choices earlier in setup once foundations and legacies exist, such as the Chancellor choosing Recent or Forgotten sites when filling the map.
- The Chronicle string codec (TTS import and export), including what to do with sections the TTS format has not defined yet (reliquary, foundations, player areas).
- Later-game setup: a non-empty `world`, stored denizens and relics, legacies and altered foundations.
- WHEN EXPLORED triggers, once an explore procedure exists.
- The Chronicle phase that writes a new Chronicle at game end.
