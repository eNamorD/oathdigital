# Chronicle Setup Slice 1: Model, Generator and Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Chronicle` between-game model, a random first-game generator with an implemented-first shuffle policy, and a bridge that feeds the unchanged `FirstGameSetupRules` machine from a Chronicle -- then switch the dev fixture and trusted/authenticated bootstrap to go through it.

**Architecture:** `Chronicle` is a new `model` value shaped after the Oath NF TTS Chronicle export. `FirstGameChronicleGenerator` (application layer) draws one through a `ChronicleRandomPort`, using `ShufflePolicy.implementedFirst` and `ImplementedCardCatalog` (which reads "implemented" from the reviewed power catalog, not a hand-kept list) to seed the world deck, dispossessed pile, relic deck and Homeland edifices. `ChronicleFirstGamePlan` bridges any Chronicle into the existing `FirstGameSetupPlan` shape, so `FirstGameSetupRules`, `FirstGameSetupMaterializer` and `model/Setup.scala` are **not touched** in this slice -- that rewrite is slice 2. `DevelopmentFirstGamePlanFactory` is refactored to build a fixed dev Chronicle and bridge it (same observable output as today). A new `GeneratedFirstGamePlanFactory` builds a random Chronicle and bridges it; `ServerRuntime` wires it into authenticated and trusted-game bootstrap, while the dev-only loopback routes keep the deterministic factory.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [docs/superpowers/specs/2026-09-21-chronicle-setup-design.md](../specs/2026-09-21-chronicle-setup-design.md) -- this plan implements slice 1 only ("Chronicle model, generator and port" in the spec's "Slices" section).

## Global Constraints

- Scala 2.13 project; run tests with `./sbtw "testOnly <FQCN>"` for a single suite or `./sbtw test` for everything.
- Do **not** modify `FirstGameSetupRules.scala`, `FirstGameSetupMaterializer.scala`, or `model/Setup.scala` in this slice. Feed them from a Chronicle through a bridge; slice 2 replaces them.
- "Implemented" is always read from `ReviewedPowerCatalog`/`PowerRegistry` (`registry.lookup(id).isDefined`), never a hand-kept list of card ids (spec, "Randomness").
- Deck order is kept but not asserted meaningful between games; a `StoredSite` holds at most 3 items (spec, "The Chronicle model").
- Catalog facts, verified against `docs/catalog/new-foundations-component-catalog.json` on 2026-09-21: 24 sites; 255 denizens (42-43 per suit); 48 relics (47 `Ordinary` + 1 `GrandScepter`); 30 edifices (5 per suit). Alpha batch: 5 denizens implemented per suit (30 total), 1 edifice implemented per suit (12 faces), some ordinary relics implemented.
- Layering (enforced by `scripts/check-architecture.py`): `model` must not import `application`, `gameplay`, `persistence`, `serialization` or `server`; `application` must not import `persistence`, `serialization` or `server` (importing `gameplay` and `catalog` from `application` is fine and already done elsewhere).
- Every new or modified production file stays well under the 800-line architecture-check limit.
- Full verification gate before finishing: `./sbtw test`, `scripts/check-architecture.py`, `scripts/check-markdown-links.py`.

---

## Task 1: The Chronicle model

**Files:**
- Create: `src/main/scala/oathdigital/model/Chronicle.scala`
- Test: `src/test/scala/oathdigital/model/ChronicleSuite.scala`

**Interfaces:**
- Produces: `final case class StoredSite(site: SiteId, items: Vector[CardId] = Vector.empty)` (requires `items.size <= 3`); `final case class Chronicle(atlasBox: Vector[StoredSite], world: Vector[StoredSite] = Vector.empty, worldDeck: Vector[DenizenId], relicDeck: Vector[RelicId], dispossessed: Vector[DenizenId] = Vector.empty, reliquary: Vector[RelicId] = Vector.empty, foundations: Map[FoundationNumber, FoundationState] = Map.empty, lineages: Vector[LineageState] = Vector.empty)`. Both live in `oathdigital.model` and use only types already defined there (`SiteId`, `CardId`, `DenizenId`, `RelicId`, `FoundationNumber`, `FoundationState`, `LineageState`).

- [ ] **Step 1: Write the failing shape test**

The fixture below is the decoded JSON that `chronicle-codec.js`'s own self-test produces for its `plain` sample string (https://github.com/harsch1/oath-nf-tts-scripts/blob/main/chronicle-codec.js, decoded with `node chronicle-codec.js decode`). It proves our model's shape fits the TTS export format; it does not parse the export-string format itself. The sample's card and site names are the TTS project's own strings, wrapped directly into our id types as placeholders -- translating between the two catalogs' ids is the deferred Chronicle codec follow-up, not this test's concern.

```scala
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
    assertEquals(chronicle.dispossessed.size, 41)
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
    assertEquals(chronicle.foundations, Map.empty)
    assertEquals(chronicle.lineages, Vector.empty)
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.model.ChronicleSuite"`
Expected: compile error -- `Chronicle` and `StoredSite` are not defined yet.

- [ ] **Step 3: Implement the model**

```scala
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
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.model.ChronicleSuite"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/Chronicle.scala \
  src/test/scala/oathdigital/model/ChronicleSuite.scala
git commit -m "feat: add the Chronicle between-game model

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: The Chronicle random port and shuffle policy

**Files:**
- Create: `src/main/scala/oathdigital/application/ChronicleShuffle.scala`
- Test: `src/test/scala/oathdigital/application/ChronicleShuffleSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `trait ChronicleRandomPort { def shuffle[A](values: Vector[A]): Vector[A] }` with `ChronicleRandomPort.random`; `trait ShufflePolicy { def order[A](cards: Vector[A], implemented: A => Boolean, random: ChronicleRandomPort): Vector[A] }` with `ShufflePolicy.implementedFirst`. Task 4 (the generator) and Task 7 (the production plan factory) both consume these.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.application

class ChronicleShuffleSuite extends munit.FunSuite {
  /** Reverses the input so ordering effects are deterministic and visible. */
  private val reversing: ChronicleRandomPort = new ChronicleRandomPort {
    def shuffle[A](values: Vector[A]): Vector[A] = values.reverse
  }

  test("implemented-first puts every implemented card ahead of every other") {
    val cards = Vector(1, 2, 3, 4, 5, 6)
    val implemented = Set(2, 4)
    val ordered = ShufflePolicy.implementedFirst.order(cards, implemented, reversing)
    assertEquals(ordered, Vector(4, 2, 6, 5, 3, 1))
  }

  test("an empty implemented set shuffles everything as the remainder") {
    val cards = Vector("a", "b", "c")
    val ordered = ShufflePolicy.implementedFirst.order(cards, Set.empty[String], reversing)
    assertEquals(ordered, Vector("c", "b", "a"))
  }

  test("the random port shuffles without changing membership") {
    val values = Vector(1, 2, 3, 4, 5)
    assertEquals(ChronicleRandomPort.random.shuffle(values).sorted, values)
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.ChronicleShuffleSuite"`
Expected: compile error -- `ChronicleRandomPort` and `ShufflePolicy` are not defined yet.

- [ ] **Step 3: Implement the port and policy**

```scala
package oathdigital.application

/**
 * The application-layer source of randomness for Chronicle setup: every
 * resolved order is recorded in the start event, so the engine and replay
 * stay RNG-free (2026-09-21 Chronicle design, "Randomness").
 */
trait ChronicleRandomPort {
  def shuffle[A](values: Vector[A]): Vector[A]
}
object ChronicleRandomPort {
  val random: ChronicleRandomPort = new ChronicleRandomPort {
    private val rng = new scala.util.Random()
    def shuffle[A](values: Vector[A]): Vector[A] = rng.shuffle(values)
  }
}

/**
 * How a deck's cards are ordered before dealing. `implemented` marks the
 * cards that should reach the top -- read from the reviewed power catalog,
 * never a hand-kept list.
 */
trait ShufflePolicy {
  def order[A](cards: Vector[A], implemented: A => Boolean,
      random: ChronicleRandomPort): Vector[A]
}
object ShufflePolicy {
  /**
   * The alpha policy (2026-09-21 Chronicle design, "Randomness"): implemented
   * cards are shuffled and placed on top, the rest shuffled below. Regional
   * discards, starting hands and the first packet are dealt from the top, so
   * they draw implemented cards first. A uniform policy replaces this once
   * the catalog is complete.
   */
  val implementedFirst: ShufflePolicy = new ShufflePolicy {
    def order[A](cards: Vector[A], implemented: A => Boolean,
        random: ChronicleRandomPort): Vector[A] = {
      val (impl, rest) = cards.partition(implemented)
      random.shuffle(impl) ++ random.shuffle(rest)
    }
  }
}
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.application.ChronicleShuffleSuite"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/ChronicleShuffle.scala \
  src/test/scala/oathdigital/application/ChronicleShuffleSuite.scala
git commit -m "feat: add the Chronicle random port and implemented-first shuffle policy

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Implemented-card catalog

**Files:**
- Create: `src/main/scala/oathdigital/application/ImplementedCardCatalog.scala`
- Test: `src/test/scala/oathdigital/application/ImplementedCardCatalogSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks (it reads `oathdigital.catalog.ExecutableCatalog` and `oathdigital.gameplay.powerresolver.PowerRegistry`, both existing).
- Produces: `object ImplementedCardCatalog { def denizens(catalog, registry): Set[DenizenId]; def ordinaryRelics(catalog, registry): Set[RelicId]; def homelandEdifice(catalog, suit, registry): Option[EdificeId] }`. Task 4 (the generator) consumes all three.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.application

import oathdigital.catalog._
import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerWindow._
import oathdigital.model.{CardRestrictions, DenizenId, EdificeId, PowerId, RelicId, Suit}

class ImplementedCardCatalogSuite extends munit.FunSuite {
  private final case class TestPower(id: PowerId,
      modifier: Option[oathdigital.model.MajorActionType],
      handlers: Vector[PowerHandler]) extends Power

  private def implementedPower(id: String): Power =
    TestPower(PowerId(id), None, Vector(PowerHandlers.automatic(
      RestStart, implemented = true)(
      PowerInspector(_ => PowerInspection(applicable = true)))))

  private val registry = PowerRegistry(
    implementedPower("denizen.solar-hearth-child.done"),
    implementedPower("relic.cup-of-plenty.done"),
    implementedPower("edifice.hall-of-debate.intact"),
    implementedPower("edifice.hall-of-debate.ruined"))

  private def catalogPower(id: String) =
    CatalogPower(id, persistent = false, rulesText = "text")

  private def denizen(id: String, suit: Suit, powerIds: Vector[String]) =
    DenizenDefinition(DefinitionId(id), id, suit, CardRestrictions.Unrestricted,
      powerIds.map(catalogPower))

  private def relic(id: String, role: RelicRole, powerIds: Vector[String]) =
    RelicDefinition(DefinitionId(id), id, role, value = 1, defense = 0,
      powers = powerIds.map(catalogPower))

  private def edificeFace(name: String, powerIds: Vector[String]) =
    EdificeFaceDefinition(name, CardRestrictions.Unrestricted,
      powerIds.map(catalogPower))

  private val implementedDenizen =
    denizen("solar-hearth-child", Suit.Hearth,
      Vector("denizen.solar-hearth-child.done"))
  private val unimplementedDenizen =
    denizen("unwired-card", Suit.Hearth, Vector("denizen.unwired-card.todo"))
  private val powerlessDenizen =
    denizen("blank-card", Suit.Hearth, Vector.empty)

  private val catalog = ExecutableCatalog(
    schemaVersion = "test", ref = CatalogRef("test", "1"),
    denizens = Vector(implementedDenizen, unimplementedDenizen, powerlessDenizen),
    relics = Vector(
      relic("cup-of-plenty", RelicRole.Ordinary, Vector("relic.cup-of-plenty.done")),
      relic("unwired-relic", RelicRole.Ordinary, Vector("relic.unwired-relic.todo")),
      relic("grand-scepter", RelicRole.GrandScepter,
        Vector("relic.cup-of-plenty.done"))),
    edifices = Vector(
      EdificeDefinition(DefinitionId("hall-of-debate"), Suit.Hearth,
        intact = edificeFace("Hall of Debate",
          Vector("edifice.hall-of-debate.intact")),
        ruined = edificeFace("Ruined Hall",
          Vector("edifice.hall-of-debate.ruined"))),
      EdificeDefinition(DefinitionId("unwired-edifice"), Suit.Hearth,
        intact = edificeFace("Unwired", Vector("edifice.unwired-edifice.intact")),
        ruined = edificeFace("Ruined Unwired",
          Vector("edifice.unwired-edifice.ruined")))),
    legacies = Vector.empty,
    sites = Vector.empty)

  test("a denizen is implemented only when every printed power is registered") {
    assertEquals(ImplementedCardCatalog.denizens(catalog, registry),
      Set(DenizenId("solar-hearth-child"), DenizenId("blank-card")))
  }

  test("only ordinary relics with every power registered count as implemented") {
    assertEquals(ImplementedCardCatalog.ordinaryRelics(catalog, registry),
      Set(RelicId("cup-of-plenty")))
  }

  test("a Homeland's implemented edifice needs both faces registered") {
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Hearth, registry),
      Some(EdificeId("hall-of-debate")))
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Beast, registry),
      None)
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.ImplementedCardCatalogSuite"`
Expected: compile error -- `ImplementedCardCatalog` is not defined yet.

- [ ] **Step 3: Implement it**

```scala
package oathdigital.application

import oathdigital.catalog.{CatalogPower, ExecutableCatalog, RelicRole}
import oathdigital.gameplay.powerresolver.PowerRegistry
import oathdigital.model.{DenizenId, EdificeId, RelicId, Suit}

/**
 * Which catalog cards have every printed power implemented, read from the
 * reviewed power catalog rather than a hand-kept list (2026-09-21 Chronicle
 * design, "Randomness").
 */
object ImplementedCardCatalog {
  def denizens(catalog: ExecutableCatalog, registry: PowerRegistry): Set[DenizenId] =
    catalog.denizens.collect {
      case definition if fullyImplemented(definition.powers, registry) =>
        DenizenId(definition.id.value)
    }.toSet

  def ordinaryRelics(catalog: ExecutableCatalog, registry: PowerRegistry): Set[RelicId] =
    catalog.relics.collect {
      case definition if definition.role == RelicRole.Ordinary &&
          fullyImplemented(definition.powers, registry) =>
        RelicId(definition.id.value)
    }.toSet

  /** The lowest-id fully implemented edifice for `suit` (both faces
    * implemented). `None` if the suit has no implemented edifice yet. */
  def homelandEdifice(catalog: ExecutableCatalog, suit: Suit,
      registry: PowerRegistry): Option[EdificeId] =
    catalog.edifices.filter(_.suit == suit)
      .filter(e => fullyImplemented(e.intact.powers, registry) &&
        fullyImplemented(e.ruined.powers, registry))
      .sortBy(_.id.value)
      .headOption
      .map(e => EdificeId(e.id.value))

  private def fullyImplemented(powers: Vector[CatalogPower],
      registry: PowerRegistry): Boolean =
    powers.forall(power => registry.lookup(power.id).isDefined)
}
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.application.ImplementedCardCatalogSuite"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/ImplementedCardCatalog.scala \
  src/test/scala/oathdigital/application/ImplementedCardCatalogSuite.scala
git commit -m "feat: add the implemented-card catalog reader

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: The first-game Chronicle generator

**Files:**
- Create: `src/main/scala/oathdigital/application/FirstGameChronicleGenerator.scala`
- Test: `src/test/scala/oathdigital/application/FirstGameChronicleGeneratorSuite.scala`

**Interfaces:**
- Consumes: `Chronicle`/`StoredSite` (Task 1); `ChronicleRandomPort`, `ShufflePolicy` (Task 2); `ImplementedCardCatalog.{denizens, ordinaryRelics, homelandEdifice}` (Task 3).
- Produces: `sealed trait ChronicleGeneratorFailure` with cases `WrongSiteCount(actual: Int)`, `TooFewSuitDenizens(suit: Suit, implemented: Int, unimplemented: Int)`, `InvariantViolated(detail: String)`; `object FirstGameChronicleGenerator { def generate(catalog: ExecutableCatalog, registry: PowerRegistry, random: ChronicleRandomPort, policy: ShufflePolicy): Either[ChronicleGeneratorFailure, Chronicle] }`. Task 7 (the production plan factory) consumes `generate`.

> **Correction made during execution (2026-09-21):** this task originally also defined `NoImplementedEdifice(suit: Suit)` and failed generation whenever a Homeland's suit had no fully-implemented edifice. Checked against the real registry at execution time, only the Order suit has one (E17 and E19, both faces registered) -- Discord, Nomad, Arcane, Hearth and Beast currently have none, contradicting this plan's Global Constraints claim of "12 edifice faces implemented (1 edifice/suit)" (real count: 7 of 60 faces registered). Generation would therefore fail for 5 of 6 suits against the real catalog. Raised to the user; resolved as: a Homeland always carries its suit's edifice card -- the implemented one when the suit has one, otherwise the lowest-id edifice of that suit (it plays inert with the existing ignored-rule diagnostic, the same treatment already given to unimplemented denizens and relics). `NoImplementedEdifice` is removed as unreachable.

- [ ] **Step 1: Write the failing test**

This test runs the generator against the real production catalog (the same one `FirstGameSetupFixture` loads), through the real reviewed power catalog registry, so it exercises the actual current "implemented" set rather than a synthetic one.

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.Suit

class FirstGameChronicleGeneratorSuite extends munit.FunSuite {
  private val registry = ReviewedPowerCatalog.registry(catalog).toOption.get
  private val random = ChronicleRandomPort.random
  private val policy = ShufflePolicy.implementedFirst

  private def generated: Chronicle =
    FirstGameChronicleGenerator.generate(catalog, registry, random, policy)
      .toOption.get

  test("the atlas box holds all 24 sites, each Homeland carrying its suit's edifice") {
    val chronicle = generated
    assertEquals(chronicle.atlasBox.map(_.site).toSet, catalog.sites.map(_.id).toSet)
    assertEquals(chronicle.atlasBox.map(_.site).distinct.size, 24)
    catalog.sites.foreach { site =>
      val stored = chronicle.atlasBox.find(_.site == site.id).get
      val handlerSuit = site.handlers.collectFirst {
        case handler if handler.contains(".homeland-") =>
          Suit.fromKey(handler.substring(handler.indexOf(".homeland-") + 10)).get
      }
      handlerSuit match {
        case Some(suit) =>
          assertEquals(stored.items.size, 1)
          val edificeId = stored.items.head.asInstanceOf[oathdigital.model.EdificeId]
          assertEquals(catalog.edifices.find(_.id.value == edificeId.value).get.suit, suit)
        case None => assertEquals(stored.items, Vector.empty)
      }
    }
  }

  test("the world deck has 60 denizens, 10 per suit, implemented ones first") {
    val chronicle = generated
    assertEquals(chronicle.worldDeck.size, 60)
    assertEquals(chronicle.worldDeck.distinct.size, 60)
    val implemented = ImplementedCardCatalog.denizens(catalog, registry)
    val (_, rest) = chronicle.worldDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)),
      "no unimplemented denizen may precede an implemented one")
    Suit.all.foreach { suit =>
      val suited = chronicle.worldDeck.filter(id =>
        catalog.denizens.find(_.id.value == id.value).get.suit == suit)
      assertEquals(suited.size, 10)
    }
  }

  test("the dispossessed pile has 12 unimplemented denizens, 2 per suit, " +
      "disjoint from the world deck") {
    val chronicle = generated
    val implemented = ImplementedCardCatalog.denizens(catalog, registry)
    assertEquals(chronicle.dispossessed.size, 12)
    assertEquals(chronicle.dispossessed.distinct.size, 12)
    assert(chronicle.dispossessed.forall(id => !implemented(id)))
    assertEquals(chronicle.worldDeck.toSet intersect chronicle.dispossessed.toSet,
      Set.empty)
    Suit.all.foreach { suit =>
      val suited = chronicle.dispossessed.filter(id =>
        catalog.denizens.find(_.id.value == id.value).get.suit == suit)
      assertEquals(suited.size, 2)
    }
  }

  test("the relic deck has every ordinary relic exactly once, implemented ones first") {
    val chronicle = generated
    val ordinary = catalog.relics.filter(_.role == oathdigital.catalog.RelicRole.Ordinary)
      .map(r => oathdigital.model.RelicId(r.id.value))
    assertEquals(chronicle.relicDeck.toSet, ordinary.toSet)
    assertEquals(chronicle.relicDeck.size, ordinary.size)
    val implemented = ImplementedCardCatalog.ordinaryRelics(catalog, registry)
    val (_, rest) = chronicle.relicDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)))
  }

  test("two runs land different atlas orders: the port is actually consulted") {
    val orders = Vector.fill(5)(generated.atlasBox.map(_.site))
    assert(orders.distinct.size > 1, "24 shuffled sites should not repeat every time")
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.FirstGameChronicleGeneratorSuite"`
Expected: compile error -- `FirstGameChronicleGenerator` and `Chronicle` (unqualified, from the same package's wildcard) are not resolvable yet. Note this test file references `Chronicle` unqualified: add `import oathdigital.model.Chronicle` if your editor does not already resolve it via the file's other imports -- the final file must compile.

- [ ] **Step 3: Implement the generator**

```scala
package oathdigital.application

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.gameplay.powerresolver.PowerRegistry
import oathdigital.model._

sealed trait ChronicleGeneratorFailure extends Product with Serializable
object ChronicleGeneratorFailure {
  final case class WrongSiteCount(actual: Int) extends ChronicleGeneratorFailure
  final case class TooFewSuitDenizens(suit: Suit, implemented: Int, unimplemented: Int)
      extends ChronicleGeneratorFailure
  final case class InvariantViolated(detail: String) extends ChronicleGeneratorFailure
}

/**
 * Produces a random first-game Chronicle (2026-09-21 Chronicle design, "The
 * first-game generator"): all 24 sites shuffled into the atlas box, each
 * Homeland carrying its suit's edifice (its implemented one when the suit
 * has one; otherwise the lowest-id edifice of that suit, which plays inert
 * with the existing ignored-rule diagnostic -- a Homeland is never left
 * without its edifice card just because none of its suit's five are
 * implemented yet); a 60-denizen world deck, 10 per suit (5 implemented plus
 * 5 random unimplemented); 12 dispossessed denizens, 2 unimplemented per
 * suit, drawn from what the 60 left behind; and the full ordinary relic
 * deck. Both decks are ordered implemented-first by `policy`. Self-validates
 * the counts before returning.
 */
object FirstGameChronicleGenerator {
  import ChronicleGeneratorFailure._

  def generate(catalog: ExecutableCatalog, registry: PowerRegistry,
      random: ChronicleRandomPort, policy: ShufflePolicy)
      : Either[ChronicleGeneratorFailure, Chronicle] =
    for {
      atlas <- atlasBox(catalog, registry, random)
      pools <- denizenPools(catalog, registry, random)
      (worldPool, dispossessedPool) = pools
      implementedDenizens = ImplementedCardCatalog.denizens(catalog, registry)
      implementedRelics = ImplementedCardCatalog.ordinaryRelics(catalog, registry)
      relicPool = catalog.relics.filter(_.role == RelicRole.Ordinary)
        .map(r => RelicId(r.id.value))
      chronicle = Chronicle(
        atlas,
        worldDeck = policy.order(worldPool, implementedDenizens, random),
        relicDeck = policy.order(relicPool, implementedRelics, random),
        dispossessed = dispossessedPool)
      _ <- validate(catalog, chronicle)
    } yield chronicle

  private def atlasBox(catalog: ExecutableCatalog, registry: PowerRegistry,
      random: ChronicleRandomPort)
      : Either[ChronicleGeneratorFailure, Vector[StoredSite]] = {
    val sites = catalog.sites.map(_.id)
    if (sites.size != 24) Left(WrongSiteCount(sites.size))
    else Right(random.shuffle(sites).map { siteId =>
      homelandSuit(catalog, siteId) match {
        case None => StoredSite(siteId)
        case Some(suit) =>
          StoredSite(siteId, Vector(edificeForHomeland(catalog, registry, suit)))
      }
    })
  }

  /** The suit's implemented edifice when it has one; otherwise the lowest-id
    * edifice of that suit, so a Homeland always carries an edifice card even
    * when none of its suit's five are implemented yet. */
  private def edificeForHomeland(catalog: ExecutableCatalog, registry: PowerRegistry,
      suit: Suit): EdificeId =
    ImplementedCardCatalog.homelandEdifice(catalog, suit, registry).getOrElse(
      EdificeId(catalog.edifices.filter(_.suit == suit).map(_.id.value).min))

  private def denizenPools(catalog: ExecutableCatalog, registry: PowerRegistry,
      random: ChronicleRandomPort)
      : Either[ChronicleGeneratorFailure, (Vector[DenizenId], Vector[DenizenId])] = {
    val implemented = ImplementedCardCatalog.denizens(catalog, registry)
    Suit.all.foldLeft[Either[ChronicleGeneratorFailure,
        (Vector[DenizenId], Vector[DenizenId])]](Right(Vector.empty -> Vector.empty)) {
      (acc, suit) =>
      acc.flatMap { case (worldPool, dispossessedPool) =>
        val suited = catalog.denizens.filter(_.suit == suit)
          .map(d => DenizenId(d.id.value))
        val (impl, unimpl) = suited.partition(implemented)
        if (impl.size < 5 || unimpl.size < 7)
          Left(TooFewSuitDenizens(suit, impl.size, unimpl.size))
        else {
          val shuffledUnimpl = random.shuffle(unimpl)
          val chosenImpl = random.shuffle(impl).take(5)
          Right((worldPool ++ chosenImpl ++ shuffledUnimpl.take(5),
            dispossessedPool ++ shuffledUnimpl.slice(5, 7)))
        }
      }
    }
  }

  private def homelandSuit(catalog: ExecutableCatalog, siteId: SiteId): Option[Suit] =
    catalog.sites.find(_.id == siteId).get.handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)

  private def validate(catalog: ExecutableCatalog, chronicle: Chronicle)
      : Either[ChronicleGeneratorFailure, Unit] = {
    val suitOf = catalog.denizens.map(d => DenizenId(d.id.value) -> d.suit).toMap
    def perSuitCount(ids: Vector[DenizenId]): Map[Suit, Int] =
      Suit.all.map(suit => suit -> ids.count(id => suitOf.get(id).contains(suit))).toMap

    if (chronicle.worldDeck.size != 60)
      Left(InvariantViolated(
        s"world deck must have 60 denizens, has ${chronicle.worldDeck.size}"))
    else if (chronicle.worldDeck.distinct.size != 60)
      Left(InvariantViolated("world deck denizens must be unique"))
    else if (perSuitCount(chronicle.worldDeck).values.exists(_ != 10))
      Left(InvariantViolated("world deck must have 10 denizens per suit"))
    else if (chronicle.dispossessed.size != 12)
      Left(InvariantViolated(
        s"dispossessed must have 12 denizens, has ${chronicle.dispossessed.size}"))
    else if (chronicle.dispossessed.distinct.size != 12)
      Left(InvariantViolated("dispossessed denizens must be unique"))
    else if (perSuitCount(chronicle.dispossessed).values.exists(_ != 2))
      Left(InvariantViolated("dispossessed must have 2 denizens per suit"))
    else if ((chronicle.worldDeck.toSet intersect chronicle.dispossessed.toSet).nonEmpty)
      Left(InvariantViolated("world deck and dispossessed must not overlap"))
    else Right(())
  }
}
```

Add `import oathdigital.model.Chronicle` to the test file at Task 4 Step 1 if it does not already compile; the implementation file above imports `oathdigital.model._`, which covers `Chronicle` for production code.

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.application.FirstGameChronicleGeneratorSuite"`
Expected: PASS (5 tests). If `TooFewSuitDenizens` fires, the alpha batch's per-suit counts (Global Constraints) have changed since 2026-09-21 -- re-check `docs/catalog/new-foundations-component-catalog.json` and the reviewed power catalog before assuming a code bug.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/FirstGameChronicleGenerator.scala \
  src/test/scala/oathdigital/application/FirstGameChronicleGeneratorSuite.scala
git commit -m "feat: add the first-game Chronicle generator

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Bridge a Chronicle into `FirstGameSetupPlan`

**Files:**
- Create: `src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala`
- Test: `src/test/scala/oathdigital/application/ChronicleFirstGamePlanSuite.scala`

**Interfaces:**
- Consumes: `Chronicle`/`StoredSite` (Task 1); the existing `FirstGameBootstrapConfig`, `FirstGameSetupPlan` (`src/main/scala/oathdigital/model/Setup.scala`, unmodified) and `oathdigital.gameplay.setup.FirstGameRulesData.visions`.
- Produces: `sealed trait ChronicleBridgeFailure` with cases `TooFewAtlasSites(actual: Int)`, `MissingHomelandEdifice(site: SiteId)`; `object ChronicleFirstGamePlan { def build(catalog: ExecutableCatalog, chronicle: Chronicle, config: FirstGameBootstrapConfig): Either[ChronicleBridgeFailure, FirstGameSetupPlan] }`. Tasks 6 and 7 both consume `build`.

- [ ] **Step 1: Write the failing test**

This test builds a Chronicle equivalent to `FirstGameSetupFixture`'s existing plan and checks the bridge reproduces that exact plan -- proving the bridge is a faithful, lossless translation.

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.model._

class ChronicleFirstGamePlanSuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(participants, PlayerId("p2"))

  private val atlasBox: Vector[StoredSite] = sites.map { siteId =>
    homelandEdifices.find(_._1 == siteId) match {
      case Some((_, edificeId)) => StoredSite(siteId, Vector(edificeId))
      case None => StoredSite(siteId)
    }
  }

  private val chronicle = Chronicle(atlasBox, worldDeck = denizens, relicDeck = relics)

  test("bridging the fixture's equivalent Chronicle reproduces its exact plan") {
    val built = ChronicleFirstGamePlan.build(catalog, chronicle, config).toOption.get
    assertEquals(built, plan)
  }

  test("the bridged plan still satisfies the unchanged setup machine") {
    val built = ChronicleFirstGamePlan.build(catalog, chronicle, config).toOption.get
    assert(new FirstGameSetupRules(catalog)
      .handle(OathState.NoGame, FirstGameSetupCommand.Begin(built)).isRight)
  }

  test("fewer than 8 atlas box sites is refused") {
    assertEquals(
      ChronicleFirstGamePlan.build(catalog, chronicle.copy(atlasBox = atlasBox.take(7)),
        config),
      Left(ChronicleBridgeFailure.TooFewAtlasSites(7)))
  }

  test("a Homeland site in play with no stored edifice is refused") {
    val homelandSite = homelandEdifices.head._1
    val stripped = atlasBox.map(stored =>
      if (stored.site == homelandSite) StoredSite(homelandSite) else stored)
    assertEquals(
      ChronicleFirstGamePlan.build(catalog, chronicle.copy(atlasBox = stripped), config),
      Left(ChronicleBridgeFailure.MissingHomelandEdifice(homelandSite)))
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.ChronicleFirstGamePlanSuite"`
Expected: compile error -- `ChronicleFirstGamePlan` and `ChronicleBridgeFailure` are not defined yet.

- [ ] **Step 3: Implement the bridge**

```scala
package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

sealed trait ChronicleBridgeFailure extends Product with Serializable
object ChronicleBridgeFailure {
  final case class TooFewAtlasSites(actual: Int) extends ChronicleBridgeFailure
  final case class MissingHomelandEdifice(site: SiteId) extends ChronicleBridgeFailure
}

/**
 * Bridges a Chronicle into the unchanged `FirstGameSetupPlan` shape so
 * `FirstGameSetupRules` can run from Chronicle input (2026-09-21 Chronicle
 * design, slice 1). Slice 2 replaces this with setup that reads a Chronicle
 * directly.
 */
object ChronicleFirstGamePlan {
  def build(catalog: ExecutableCatalog, chronicle: Chronicle,
      config: FirstGameBootstrapConfig)
      : Either[ChronicleBridgeFailure, FirstGameSetupPlan] =
    if (chronicle.atlasBox.size < 8)
      Left(ChronicleBridgeFailure.TooFewAtlasSites(chronicle.atlasBox.size))
    else {
      val inPlay = chronicle.atlasBox.take(8)
      homelandEdifices(catalog, inPlay).map { homelands =>
        val dealt = 6 + config.participants.size * 3
        val remaining = chronicle.worldDeck.drop(dealt)
        val worldDeckOrder: Vector[WorldCardId] =
          remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
            remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
            remaining.drop(25)
        FirstGameSetupPlan(
          catalog.ref,
          config.participants,
          config.firstPlayer,
          inPlay.map(_.site),
          chronicle.worldDeck,
          worldDeckOrder,
          chronicle.relicDeck,
          homelands
        )
      }
    }

  private def homelandEdifices(catalog: ExecutableCatalog,
      inPlay: Vector[StoredSite])
      : Either[ChronicleBridgeFailure, Vector[(SiteId, EdificeId)]] = {
    val sitesById = catalog.sites.map(s => s.id -> s).toMap
    inPlay.foldLeft[Either[ChronicleBridgeFailure, Vector[(SiteId, EdificeId)]]](
        Right(Vector.empty)) { (acc, stored) =>
      acc.flatMap { built =>
        homelandSuit(sitesById(stored.site).handlers) match {
          case None => Right(built)
          case Some(_) =>
            stored.items.collectFirst { case id: EdificeId => id } match {
              case Some(edificeId) => Right(built :+ (stored.site -> edificeId))
              case None =>
                Left(ChronicleBridgeFailure.MissingHomelandEdifice(stored.site))
            }
        }
      }
    }
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)
}
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.application.ChronicleFirstGamePlanSuite"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala \
  src/test/scala/oathdigital/application/ChronicleFirstGamePlanSuite.scala
git commit -m "feat: bridge a Chronicle into FirstGameSetupPlan

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Rebuild `DevelopmentFirstGamePlanFactory` on a dev Chronicle

**Files:**
- Modify: `src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala`
- Regression test (no new file): `src/test/scala/oathdigital/application/DevelopmentFirstGamePlanFactorySuite.scala` -- its existing assertions are the behavior-preserving gate for this refactor, since `build`'s return type and values do not change.

**Interfaces:**
- Consumes: `Chronicle`/`StoredSite` (Task 1); `ChronicleFirstGamePlan.build` (Task 5).
- Produces: `FirstGameBootstrapConfig`, `BootstrapPlanFailure`, `trait FirstGamePlanFactory` and `class DevelopmentFirstGamePlanFactory` keep their current signatures (`build(config: FirstGameBootstrapConfig): Either[BootstrapPlanFailure, FirstGameSetupPlan]`) -- every existing caller (`GameServerGateway`, `GameRoutes`, `TrustedGameProvisioning`, and the test suites listed in Step 4) is unaffected.

This is a behavior-preserving refactor: the produced `FirstGameSetupPlan` must have the exact same field values as before, just built by way of a `Chronicle`. There is no new observable behavior, so there is no new failing test to write first -- instead, confirm the existing suite passes before touching the file, then confirm it still passes after.

- [ ] **Step 1: Confirm the baseline passes before refactoring**

Run: `./sbtw "testOnly oathdigital.application.DevelopmentFirstGamePlanFactorySuite"`
Expected: PASS (current behavior, before this task's edit).

- [ ] **Step 2: Rewrite the factory to build a dev Chronicle and bridge it**

Replace the full contents of `src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala` with:

```scala
package oathdigital.application

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.model._

final case class FirstGameBootstrapConfig(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId
)

final case class BootstrapPlanFailure(message: String)

trait FirstGamePlanFactory {
  def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGameSetupPlan]
}

/**
 * Development-only deterministic plan derivation: assembles a fixed dev
 * Chronicle (first 8 sites, first 10 denizens per suit, lowest-id edifice
 * per suit, all ordinary relics by printed value) and bridges it through
 * `ChronicleFirstGamePlan` (2026-09-21 Chronicle design, slice 1).
 *
 * This is reproducible fixture construction, not production randomness.
 */
final class DevelopmentFirstGamePlanFactory(catalog: ExecutableCatalog)
    extends FirstGamePlanFactory {
  override def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGameSetupPlan] =
    for {
      chronicle <- devChronicle
      plan <- ChronicleFirstGamePlan.build(catalog, chronicle, config)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
    } yield plan

  private def devChronicle: Either[BootstrapPlanFailure, Chronicle] =
    for {
      atlasBox <- devAtlasBox
      denizens <- devDenizens
    } yield Chronicle(
      atlasBox,
      worldDeck = denizens,
      relicDeck = catalog.relics.filter(_.role == RelicRole.Ordinary)
        .sortBy(relic => relic.value -> relic.id.value)
        .map(relic => RelicId(relic.id.value))
    )

  private def devAtlasBox: Either[BootstrapPlanFailure, Vector[StoredSite]] = {
    val orderedSites = catalog.sites.sortBy(_.id.value).map(_.id)
    if (orderedSites.size < 8)
      Left(BootstrapPlanFailure(
        s"catalog has ${orderedSites.size} sites; first-game setup requires 8"
      ))
    else
      orderedSites.take(8).foldLeft[Either[BootstrapPlanFailure, Vector[StoredSite]]](
          Right(Vector.empty)) { (acc, siteId) =>
        acc.flatMap { built =>
          val site = catalog.sites.find(_.id == siteId).get
          homelandSuit(site.handlers) match {
            case None => Right(built :+ StoredSite(siteId))
            case Some(suit) =>
              catalog.edifices.filter(_.suit == suit).sortBy(_.id.value).headOption match {
                case Some(edifice) =>
                  Right(built :+ StoredSite(siteId, Vector(EdificeId(edifice.id.value))))
                case None =>
                  Left(BootstrapPlanFailure(
                    s"no edifice exists for Homeland suit '${suit.key}'"
                  ))
              }
          }
        }
      }
  }

  /** The dev plan deals from suits in key order. This is a fixed selection
    * order for reproducible dev games, not the rules order in `Suit.all`. */
  private val alphabeticalSuits: Vector[Suit] = Suit.all.sortBy(_.key)

  private def devDenizens: Either[BootstrapPlanFailure, Vector[DenizenId]] = {
    val selected = alphabeticalSuits.flatMap { suit =>
      catalog.denizens.filter(_.suit == suit)
        .sortBy(_.id.value)
        .take(10)
        .map(denizen => DenizenId(denizen.id.value))
    }
    alphabeticalSuits.collectFirst {
      case suit
          if catalog.denizens.count(_.suit == suit) < 10 =>
        BootstrapPlanFailure(
          s"catalog suit '${suit.key}' has fewer than 10 denizens"
        )
    }.toLeft(selected)
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)
}
```

- [ ] **Step 3: Run the regression gate**

Run: `./sbtw "testOnly oathdigital.application.DevelopmentFirstGamePlanFactorySuite"`
Expected: PASS, unchanged (same assertions as Step 1 -- the refactor produces byte-identical plan values).

- [ ] **Step 4: Run the broader regression gate**

These suites construct `DevelopmentFirstGamePlanFactory` directly and exercise it end-to-end; none should change behavior.

Run: `./sbtw "testOnly oathdigital.server.TrustedGameProvisioningSuite oathdigital.server.AuthenticatedGameRoutesSuite oathdigital.server.GameTrustBoundaryRoutesSuite oathdigital.server.AuthenticatedGameBootstrapRoutesSuite oathdigital.server.GameRoutesSuite"`
Expected: PASS, unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala
git commit -m "refactor: build DevelopmentFirstGamePlanFactory from a dev Chronicle

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: `GeneratedFirstGamePlanFactory` for production plans

**Files:**
- Create: `src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala`
- Test: `src/test/scala/oathdigital/application/GeneratedFirstGamePlanFactorySuite.scala`

**Interfaces:**
- Consumes: `FirstGameChronicleGenerator.generate` (Task 4); `ChronicleFirstGamePlan.build` (Task 5); `ChronicleRandomPort.random`, `ShufflePolicy.implementedFirst` (Task 2); the existing `FirstGamePlanFactory` trait, `FirstGameBootstrapConfig`, `BootstrapPlanFailure` (Task 6, unchanged signatures); `oathdigital.gameplay.powers.ReviewedPowerCatalog.registry`.
- Produces: `final class GeneratedFirstGamePlanFactory(catalog: ExecutableCatalog, random: ChronicleRandomPort = ChronicleRandomPort.random, policy: ShufflePolicy = ShufflePolicy.implementedFirst) extends FirstGamePlanFactory`. Task 8 (`ServerRuntime` wiring) consumes this.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.application

import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.model._

class GeneratedFirstGamePlanFactorySuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val config = FirstGameBootstrapConfig(FirstGameSetupFixture.participants,
    PlayerId("p2"))
  private val factory = new GeneratedFirstGamePlanFactory(catalog)

  test("a generated plan feeds the unchanged setup machine") {
    val plan = factory.build(config).toOption.get
    assertEquals(plan.orderedSites.size, 8)
    assertEquals(plan.denizenOrder.size, 60)
    assert(new FirstGameSetupRules(catalog)
      .handle(OathState.NoGame, FirstGameSetupCommand.Begin(plan)).isRight)
  }

  test("successive plans are randomized, not the fixed dev order") {
    val orders = Vector.fill(5)(factory.build(config).toOption.get.orderedSites)
    assert(orders.distinct.size > 1, "site order should vary across generated plans")
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.GeneratedFirstGamePlanFactorySuite"`
Expected: compile error -- `GeneratedFirstGamePlanFactory` is not defined yet.

- [ ] **Step 3: Implement the factory**

```scala
package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.FirstGameSetupPlan

/**
 * Production first-game plan derivation: draws a random Chronicle through
 * `FirstGameChronicleGenerator` and bridges it into a `FirstGameSetupPlan`
 * via `ChronicleFirstGamePlan` (2026-09-21 Chronicle design, slice 1).
 * Trusted-game provisioning and authenticated bootstrap use this; the
 * dev-only loopback routes keep the deterministic
 * `DevelopmentFirstGamePlanFactory`.
 */
final class GeneratedFirstGamePlanFactory(
    catalog: ExecutableCatalog,
    random: ChronicleRandomPort = ChronicleRandomPort.random,
    policy: ShufflePolicy = ShufflePolicy.implementedFirst
) extends FirstGamePlanFactory {
  override def build(config: FirstGameBootstrapConfig)
      : Either[BootstrapPlanFailure, FirstGameSetupPlan] =
    for {
      registry <- ReviewedPowerCatalog.registry(catalog)
        .left.map(violation => BootstrapPlanFailure(violation.toString))
      chronicle <- FirstGameChronicleGenerator.generate(catalog, registry, random, policy)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
      plan <- ChronicleFirstGamePlan.build(catalog, chronicle, config)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
    } yield plan
}
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.application.GeneratedFirstGamePlanFactorySuite"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala \
  src/test/scala/oathdigital/application/GeneratedFirstGamePlanFactorySuite.scala
git commit -m "feat: add GeneratedFirstGamePlanFactory for production plans

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: Wire trusted and authenticated bootstrap to the generated factory

**Files:**
- Modify: `src/main/scala/oathdigital/server/ServerRuntime.scala:59-84`
- Test: `src/test/scala/oathdigital/server/ServerRuntimeSuite.scala` (new)

**Interfaces:**
- Consumes: `GeneratedFirstGamePlanFactory` (Task 7). `ServerRuntime`'s own public fields (`firstGame`, `authenticatedGame`, `trustedGameProvisioning`, `trustedGame`, ...) are unchanged.

Dev-only loopback routes (`firstGame: GameServerGateway`) keep the deterministic `DevelopmentFirstGamePlanFactory`, since `GameServerGateway`'s constructor is typed concretely to that class. Authenticated bootstrap and trusted-game provisioning switch to `GeneratedFirstGamePlanFactory`.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.server

import java.nio.file.{Files, Paths}

import oathdigital.application.TrustedSeat
import oathdigital.protocol._

class ServerRuntimeSuite extends munit.FunSuite {
  private def request(gameId: String) = TrustedGameCreateRequest(gameId, Vector(
    BootstrapParticipantRequest("p1", "l1", "red"),
    BootstrapParticipantRequest("p2", "l2", "blue"),
    BootstrapParticipantRequest("p3", "l3", "yellow")), "p2")

  test("trusted-game provisioning draws a randomized board, not the fixed dev one") {
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("server-runtime-wiring-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    try {
      def siteOrder(gameId: String): Vector[String] = {
        assert(runtime.trustedGameProvisioning
          .create(request(gameId), "https://games.example.test").isRight)
        val seat = TrustedSeat(gameId, "p2")
        val projection = runtime.trustedGame.load(gameId, seat).toOption.get
        projection.world.flatMap(_.sites).map(_.siteId)
      }
      val orders = Vector("wiring-a", "wiring-b", "wiring-c", "wiring-d").map(siteOrder)
      assert(orders.distinct.size > 1,
        "four separately provisioned trusted games should not share one fixed board")
    } finally runtime.close()
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.server.ServerRuntimeSuite"`
Expected: FAIL -- all four site orders are identical (still wired to the deterministic dev factory).

- [ ] **Step 3: Wire the generated factory in**

In `src/main/scala/oathdigital/server/ServerRuntime.scala`, replace:

```scala
          val planFactory =
            new oathdigital.application.DevelopmentFirstGamePlanFactory(
              catalog
            )
          val authorization =
            new MembershipAuthorizationService(database.identities)
          new ServerRuntime(
            new GameServerGateway(
              firstGameService,
              projector,
              planFactory
            ),
            new AuthenticatedGameGateway(
              firstGameService,
              projector,
              authorization,
              database.identities,
              planFactory
            ),
            authorization,
            database.identities,
            new TrustedGameProvisioning(firstGameService, planFactory, database.trustedGames),
            new TrustedGameGateway(firstGameService, projector),
            database
          )
```

with:

```scala
          val devPlanFactory =
            new oathdigital.application.DevelopmentFirstGamePlanFactory(
              catalog
            )
          val generatedPlanFactory =
            new oathdigital.application.GeneratedFirstGamePlanFactory(
              catalog
            )
          val authorization =
            new MembershipAuthorizationService(database.identities)
          new ServerRuntime(
            new GameServerGateway(
              firstGameService,
              projector,
              devPlanFactory
            ),
            new AuthenticatedGameGateway(
              firstGameService,
              projector,
              authorization,
              database.identities,
              generatedPlanFactory
            ),
            authorization,
            database.identities,
            new TrustedGameProvisioning(firstGameService, generatedPlanFactory,
              database.trustedGames),
            new TrustedGameGateway(firstGameService, projector),
            database
          )
```

`GameServerGateway` keeps `devPlanFactory` because its constructor parameter is typed concretely as `DevelopmentFirstGamePlanFactory` (dev-only loopback routes); `AuthenticatedGameGateway` and `TrustedGameProvisioning` both take the `FirstGamePlanFactory` trait, so `generatedPlanFactory` drops in directly.

- [ ] **Step 4: Run it to confirm it passes**

Run: `./sbtw "testOnly oathdigital.server.ServerRuntimeSuite"`
Expected: PASS.

- [ ] **Step 5: Run the broader regression gate**

These suites boot a real `ServerRuntime` (or construct the gateways directly) and exercise trusted/authenticated bootstrap over HTTP; none assert specific board content, so none should change.

Run: `./sbtw "testOnly oathdigital.server.ServerRoutesSuite oathdigital.server.TrustedSeatRoutesSuite oathdigital.server.TrustedGameProvisioningSuite"`
Expected: PASS, unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/server/ServerRuntime.scala \
  src/test/scala/oathdigital/server/ServerRuntimeSuite.scala
git commit -m "feat: wire trusted and authenticated bootstrap to the generated plan factory

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: Full verification gate and slice tracking

**Files:**
- Modify: `docs/ROADMAP.md` (the "Phase - Randomized setup for alpha" section)

- [ ] **Step 1: Run the complete verification gate**

```bash
./sbtw test
./sbtw frontend/test
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
```

Expected: everything passes. If `check-architecture.py` reports a forbidden import, check which of Tasks 1-8 introduced it against the Global Constraints' layering rule and fix the import direction rather than suppressing the check.

- [ ] **Step 2: Record slice 1 as done**

In `docs/ROADMAP.md`, under the existing "### Phase - Randomized setup for alpha" section, add a line after the paragraph that currently ends "...Built in three slices, each with its own plan.":

```markdown
Slice 1 (Chronicle model, generator and port; the dev fixture and trusted and
authenticated bootstrap now build from a Chronicle) is done -- plan at
[docs/superpowers/plans/2026-09-21-chronicle-setup-slice1.md](superpowers/plans/2026-09-21-chronicle-setup-slice1.md).
Slices 2 (setup on the walker) and 3 (the E02/E06/E22 powers) remain.
```

- [ ] **Step 3: Run the markdown link check again**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS (the new plan-file link resolves).

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record Chronicle setup slice 1

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** "The Chronicle model" -> Task 1. "Randomness" (port + implemented-first policy) -> Task 2, backed by "implemented is read from the reviewed power catalog" -> Task 3. "The first-game generator" -> Task 4. Feeding the existing setup machine from a Chronicle -> Task 5 (bridge) plus Task 6/7 (the two factories that use it). "`DevelopmentFirstGamePlanFactory` becomes a deterministic dev-Chronicle fixture" -> Task 6. "Trusted-game provisioning switches to the generator" -> Tasks 7-8. Slice 1 explicitly excludes "Setup from a Chronicle" (the `GameStarted`/`Phase.Setup` rewrite) and "Setup powers" -- those are slices 2 and 3, untouched here.
- **Placeholder scan:** every step has complete code; no "TODO"/"similar to Task N" left in task bodies. Task 6's steps are a regression-guarded refactor rather than new-behavior TDD, which is called out explicitly rather than faked as a "failing test."
- **Type consistency:** `FirstGameBootstrapConfig`, `BootstrapPlanFailure` and `FirstGamePlanFactory` keep the exact shape Task 6 finds them in through Tasks 7-8. `Chronicle`/`StoredSite` (Task 1) are used identically in Tasks 4-7. `ChronicleRandomPort`/`ShufflePolicy` (Task 2) are threaded through Tasks 4 and 7 with the same signatures.
