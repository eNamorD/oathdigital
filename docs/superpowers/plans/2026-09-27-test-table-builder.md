# Test Table Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give rule tests one readable, named way to build a starting state (`Table`), then move every duplicate board fixture onto it and delete them.

**Architecture:**
- `Table` is an immutable test-side builder. It starts from the real `GameStartRules` output (no setup walk) and makes it quiet.
- Each step states one fact and keeps the card inventory whole. `.ready` validates the card index and the warband inventory.
- `Look` is the matching read side. `table.situation(driver)` hands the state to the existing `Situation` rules adapter so tests can drive real commands.
- Migration then runs fixture by fixture: suites rewrite their setup as `Table` chains, and the fixtures' board builders are deleted.

**Tech Stack:** Scala 3, munit, sbt via `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-27-test-table-builder-design.md` (read it first).

## Global Constraints

**Commands and workflow**
- Build and test with `./sbtw` from the repo root:
  - one suite: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
  - everything: `./sbtw root/test`
- Never use `preview_start`. It hits the live DB.
- Never stage or commit `docs/ROADMAP.md`. Another session edits it.
- HEAD may move under you (concurrent sessions on `main`). Re-check `git log -1` before any amend, reset or rebase; prefer new commits.
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Writing**
- Persisted text (code, comments, commits, docs) is normal prose, not caveman.
- Comments follow the repo's style: doc comments on public helpers, explaining *why*.

**Table rules**
- Name cards and sites as the catalog prints them. The names are unique across denizens and relics, and edifice faces are unique too.
- **Never restore dealt content wholesale.** A migrated test that needs a card, bandit, pawn position, wealth or first player states it with a step.
- **Keep assertions; rewrite only the setup.** Each suite keeps its test count. Never weaken an assertion to make a migrated test pass. If a test turns out to assert nothing once the incidental content is gone, keep it and add it to the list in Task 10.
- Card ids a migrated setup touches become names: `DenizenId("93")` becomes `"Gambling Hall"` (look it up in `docs/catalog/new-foundations-component-catalog.json`).
- Measure CPU time from munit's per-test timings in the `root/test` output.

---

## Amendment (2026-09-27, after Task 4): rebase the big fixtures

Tasks 2–4 rewrote about 40 suites test by test. The remaining shared fixtures are `CampaignFixture` with `PlanDriver` (34 users) and the `PowerFixture` family (about 85). They already read as domain phrases (`withAdviser(b, card, FaceUp)`, `againstPlayer`), and the audit named two of their suites as the repo's DAMP models. The user chose to **rebase** them instead of rewriting every test:

- Each fixture's board builder becomes a documented `Table` chain: no replay, the quiet table, p1 acts.
- Helpers stay as thin wrappers over `Table` steps. `scrub`, `outOfWorldDeck`, `CardStaging.without` and the `inPhase` copies go.
- A suite changes only where it relied on an incidental setup fact. It then states that fact with a step or helper.
- Suites that patch the state by hand (Task 9's inline cases) still get full rewrites.

The Tasks 5–8 step lists below describe the original rewrite. Read them as "rebase the fixture, then fix the suites that break".

## Shared migration rules (Tasks 2–9)

These are part of every migration task.

**Per-suite procedure**

1. Read the whole suite and the fixture helpers it calls. Write down which facts each test actually relies on:
   - the actor, and the other players;
   - pawn sites;
   - cards held or at sites;
   - bandits and forces;
   - board values;
   - phase.
2. Replace the fixture board with a `Table` chain that states exactly those facts. Put the chain inline in the test.
   - A suite-private builder is allowed only if all of these hold: 3 or more tests use it, it has a one-line doc comment saying who holds what, and it is built from `Table` steps.
3. Replace reads with `Look`:
   - `player(ready).board.supply.supply` becomes `Look(ready).supply(p1)`;
   - private `supplyOf`, `ready(state)`, `player(...)` and the like are deleted.
4. **The actor is now p1.** The old base's actor was p2 (the fixture's first player).
   - Replace `actor`, `activeId`, `b.actor` and the like with `p1`.
   - Replace "the other player" with `p2`, and the third with `p3`.
   - Turn order is p1, p2, p3.
5. Run the suite. For each failure, find the incidental fact the test relied on, and add it as a step. Do not change the assertion.
6. When no suite uses a fixture's board builder any more, delete it. Keep domain helpers that build no board (dice, rules constructors, run drivers) where they are.

**Old helper → Table**

| Old | Table |
|---|---|
| `execute()._1`, `initialReady`, `PowerFixture.base` | `Table.start` |
| `inPhase(r, Phase.X)`, `turn.copy(phase = …)`, `TurnState(actor, X, …)` | `.turn(p1, Phase.X)` |
| `withPawn(r, p, site)`, `pawnSite = Some(s)` | `.pawn(p, at = "Site Name")` |
| `asAdviser`, `giveAdviser`, `withAdviser`, `withAdviserFor` | `.adviser(p, "Card", facedown = …)` |
| `giveVision(r, p, v)` | `.adviser(p, visionId)` |
| `withRelic`, `withRelicFor` | `.relic(p, "Relic")` |
| `atSite`, `atHome`, `withSiteCard`, site `denizens = Vector(...)` | `.denizen("Card", at = "Site")` |
| site `relics = …` | `.relicAt("Relic", at = "Site")` |
| `withEdifice`, `spring(r, side)` | `.edifice("Hallowed Spring", side, at = "Site")` |
| `SiteForces.Occupied(Bandit, n)` | `.bandits("Site", n)` |
| `actorRules(b, s)`, Exile forces at a site | `.warbandsAt("Site", p1, n)` |
| board `favor/faceUpSecrets/supply/warbands` copies, `withSecrets` | `.favor(p, n)`, `.secrets(p, faceUp = n, faceDown = m)`, `.supply(p, n)`, `.warbands(p, n)` |
| card `tokens = Tokens(f, s)` | `.tokens("Card", favor = f, secrets = s)` |
| site `tokens = …`, `withSiteSecrets` | `.siteTokens("Site", favor = f, secrets = s)` |
| banner holder/resources copies | `.peoplesFavor(Some(p), favor = n)`, `.darkestSecret(Some(p), secrets = n)` |
| `banks.favor.updated(suit, n)` | `.bankFavor(Suit.X, n)` |
| world-deck order edits (`SearchFixture.staged`) | `.worldDeckTop("A", "B")` |
| `outOfWorldDeck`, `scrub`, `CardStaging.without` | implicit: every placing step moves the card |
| anything else | `.update(ready => …)`, and say why in a comment |

---

## File structure

- **Create** `src/test/scala/oathdigital/testkit/Table.scala`: `Table` (the builder) and `CatalogNames` (name → id resolution with close-match errors).
- **Create** `src/test/scala/oathdigital/testkit/Look.scala`: the read side.
- **Create** `src/test/scala/oathdigital/testkit/TableSuite.scala`: the builder's own tests.
- **Modify** `CONTEXT.md`: add **Table**, amend **Situation**.
- **Modify** each fixture and suite listed in Tasks 2–9. Delete the fixture board builders as they empty.
- **Modify** `docs/superpowers/specs/2026-09-27-test-table-builder-design.md`: status line at the end (Task 10).

---

### Task 1: `Table`, `CatalogNames`, `Look`, `situation`

**Files:**
- Create: `src/test/scala/oathdigital/testkit/Table.scala`
- Create: `src/test/scala/oathdigital/testkit/Look.scala`
- Create: `src/test/scala/oathdigital/testkit/TableSuite.scala`
- Modify: `CONTEXT.md` (the **Situation** entry, around line 101)

**Interfaces:**
- Consumes:
  - `FirstGameSetupFixture.{catalog, chronicle, orders}`
  - `GameStartRules.evolve(catalog, chronicle, orders): Either[OathViolation, ReadyGame]`
  - `CardIndex.from(game, expectedCards): Either[Vector[CardIndexProblem], CardIndex]`
  - `Situation(state, events, nextSequence, driver)`
  - `SituationDriver.Journaled`
- Produces (every later task uses these):
  - **Table values:** `Table.start: Table`, `Table.p1`, `Table.p2`, `Table.p3: PlayerId`, `Table.homeOf(p: PlayerId): SiteId`.
  - **Turn and pawns:** `.turn(p, phase)`, `.pawn(p, at)`.
  - **Cards:**
    - `.adviser(p, card, facedown = false)`, `.relic(p, card, facedown = false)`;
    - `.denizen(card, at, facedown = false)`, `.relicAt(card, at, facedown = true)`;
    - `.edifice(card, side, at)`, `.tokens(card, favor = 0, secrets = 0)`;
    - `.worldDeckTop(cards*)`.
  - **Boards:** `.favor(p, n)`, `.secrets(p, faceUp, faceDown = 0)`, `.supply(p, n)`, `.warbands(p, n)`.
  - **Sites:** `.bandits(site, n)`, `.warbandsAt(site, owner, n)`, `.siteTokens(site, favor = 0, secrets = 0)`.
  - **Banners and banks:** `.peoplesFavor(holder, favor)`, `.darkestSecret(holder, secrets)`, `.bankFavor(suit, n)`.
  - **Escape hatch and outputs:** `.update(f)`, `.ready: ReadyGame`, `.state: OathState`, `.situation(driver): Situation`.
  - **Argument types:**
    - card arguments: `String | DenizenId` (denizen), `String | WorldCardId` (adviser), `String | RelicId`, `String | EdificeId`, or `String | CardId` (tokens);
    - site arguments: `String | SiteId`.
  - **CatalogNames:** `site`, `denizen`, `worldCard`, `relic`, `edifice`, `card`, `nameOf(card: CardId): String`.
  - **`Look(state: OathState)` / `Look(ready: ReadyGame)`**, with:
    - `player`, `supply`, `favor`, `faceUpSecrets`, `faceDownSecrets`, `warbands`;
    - `advisers`, `relics`, `pawn`, `denizens`, `relicsAt`, `forces`, `siteTokens`;
    - `phase`, `active`, `tokensOn`.

- [ ] **Step 1: Write the failing `TableSuite`**

```scala
package oathdigital.testkit

import oathdigital.application.{GameCommand, StartPayload}
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import Table.{p1, p2, p3}

/** `Table` builds rule-test states from the real game start without the
  * setup walk. These tests pin what the quiet table holds, that every step
  * moves a card rather than copying it, and that the rules accept the
  * result as a real game. */
class TableSuite extends munit.FunSuite:

  test("the quiet table: p1's Act, each pawn on its own site, nothing at the sites and no advisers"):
    val look = Look(Table.start.ready)
    assertEquals(look.active, p1)
    assertEquals(look.phase, Phase.Act)
    assertEquals(Vector(p1, p2, p3).map(look.pawn),
      Vector("Ancient City", "Broken Peaks", "Buried Giant").map(CatalogNames.site))
    Table.start.ready.game.current.map.inPlay.foreach { site =>
      assertEquals(look.denizens(site), Vector.empty, site.value)
      assertEquals(look.relicsAt(site), Vector.empty, site.value)
      assertEquals(look.forces(site), SiteForces.Empty, site.value)
      assertEquals(look.siteTokens(site), Tokens.empty, site.value)
    }
    Vector(p1, p2, p3).foreach(p => assertEquals(look.advisers(p), Vector.empty))
    assertEquals(Table.start.ready.game.current.temporaryHands, Map.empty)

  test("the turn passes p1, p2, p3"):
    assertEquals(FinishRestProcedure.turnOrder(Table.start.ready),
      Vector(p1, p2, p3))

  test("boards keep their printed start: 1 favor, 1 faceup secret, 3 warbands, 7 Supply"):
    val look = Look(Table.start.ready)
    assertEquals((look.favor(p1), look.faceUpSecrets(p1), look.warbands(p1),
      look.supply(p1)), (1, 1, 3, 7))

  test("the quiet table holds exactly the cards the game started with"):
    val started = GameStartRules.evolve(catalog, FirstGameSetupFixture.chronicle,
      FirstGameSetupFixture.orders).toOption.get
    assertEquals(CardIndex.from(Table.start.ready.game).toOption.get.ids,
      CardIndex.from(started.game).toOption.get.ids)

  test("placing a card takes it out of the zone that held it"):
    val dealt = Table.start.ready.game.current.commonCards.worldDeck
      .collectFirst { case id: DenizenId => id }.get
    val advised = Table.start.adviser(p1, dealt)
    assert(!advised.ready.game.current.commonCards.worldDeck.contains(dealt))
    val moved = advised.denizen(dealt, at = "Dunes")
    assertEquals(Look(moved.ready).advisers(p1), Vector.empty)
    assertEquals(Look(moved.ready).denizens("Dunes"), Vector(dealt))

  test("a card the first game did not deal joins the table"):
    val inGame = CardIndex.from(Table.start.ready.game).toOption.get.ids
    val outside = catalog.denizens.map(d => DenizenId(d.id.value))
      .find(id => !inGame(id)).get
    val ready = Table.start.adviser(p1, outside).ready
    assertEquals(Look(ready).advisers(p1), Vector(outside))

  test("a card in two places is rejected when the state is read"):
    val card = CatalogNames.denizen("Alchemist")
    val twice = Table.start.adviser(p1, card).update(_.updateCurrent(c =>
      c.copy(players = c.players.map(p => if p.player == p2 then
        p.copy(advisers = Vector(DenizenState(card, Orientation.FaceUp,
          Tokens.empty))) else p))))
    val failure = intercept[munit.FailException](twice.ready)
    assert(failure.getMessage.contains("Alchemist"), failure.getMessage)

  test("more warbands than the printed supply are rejected when the state is read"):
    val failure = intercept[munit.FailException](
      Table.start.bandits("Dunes", 25).ready)
    assert(failure.getMessage.contains("Bandit"), failure.getMessage)

  test("an unknown name fails and lists the closest names"):
    val failure = intercept[munit.FailException](CatalogNames.denizen("Mercenary"))
    assert(failure.getMessage.contains("Mercenaries"), failure.getMessage)

  test("each step states one fact"):
    val ready = Table.start
      .turn(p2, Phase.Wake)
      .pawn(p1, at = "Dunes")
      .adviser(p1, "Alchemist", facedown = true)
      .relic(p2, "Circlet of Command")
      .denizen("Magician's Code", at = "Dunes")
      .relicAt("Brass Horse", at = "Dunes")
      .edifice("Hallowed Spring", EdificeSide.Intact, at = "Fair Isle")
      .bandits("Fair Isle", 2)
      .warbandsAt("Dunes", p1, 3)
      .favor(p1, 4).secrets(p1, faceUp = 2, faceDown = 1).supply(p1, 5)
      .warbands(p1, 6)
      .tokens("Magician's Code", favor = 1)
      .siteTokens("Dunes", secrets = 2)
      .peoplesFavor(Some(p3), favor = 2)
      .bankFavor(Suit.Arcane, 1)
      .ready
    val look = Look(ready)
    assertEquals((look.active, look.phase), (p2, Phase.Wake))
    assertEquals(look.pawn(p1), CatalogNames.site("Dunes"))
    assertEquals(ready.game.current.players.find(_.player == p1).get.advisers,
      Vector(DenizenState(CatalogNames.denizen("Alchemist"),
        Orientation.FaceDown, Tokens.empty)))
    assertEquals(look.relics(p2), Vector(CatalogNames.relic("Circlet of Command")))
    assertEquals(look.denizens("Dunes"), Vector(CatalogNames.denizen("Magician's Code")))
    assertEquals(look.relicsAt("Dunes"), Vector(CatalogNames.relic("Brass Horse")))
    assertEquals(look.denizens("Fair Isle"), Vector(CatalogNames.edifice("Hallowed Spring")))
    assertEquals(look.forces("Fair Isle"), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(look.forces("Dunes"), SiteForces.Occupied(
      ForceKind.Exile(look.player(p1).lineage), 3))
    assertEquals((look.favor(p1), look.faceUpSecrets(p1),
      look.faceDownSecrets(p1), look.supply(p1), look.warbands(p1)), (4, 2, 1, 5, 6))
    assertEquals(look.tokensOn("Magician's Code"), Tokens(1, 0))
    assertEquals(look.siteTokens("Dunes"), Tokens(0, 2))
    assertEquals(ready.game.current.banners.peoplesFavor.holder, Some(p3))
    assertEquals(ready.banks.favor(Suit.Arcane), 1)

  test("the world deck's top can be named"):
    val ready = Table.start.worldDeckTop("Alchemist", "Magician's Code").ready
    assertEquals(ready.game.current.commonCards.worldDeck.take(2), Vector(
      CatalogNames.denizen("Alchemist"), CatalogNames.denizen("Magician's Code")))

  test("real commands run from a table: Rest passes the turn to p2"):
    val rested = Table.start.situation(Situation.rules(catalog))
      .after(GameCommand.BeginRest(p1))
    assertEquals(Look(rested.state).active, p2)

  test("real commands run from a table: Muster from a denizen at the pawn's site"):
    val home = Table.homeOf(p1)
    val mustered = Table.start.denizen("Alchemist", at = home)
      .situation(Situation.rules(catalog))
      .after(GameCommand.StartWalker(ActionRef.Muster, StartPayload(p1)))
    val look = Look(mustered.state)
    assertEquals((look.favor(p1), look.warbands(p1), look.supply(p1)), (0, 4, 6))
    assertEquals(look.tokensOn("Alchemist"), Tokens(1, 0))

  test("real commands run from a table: Travel moves the pawn"):
    val travelled = Table.start.situation(Situation.rules(catalog))
      .after(GameCommand.StartWalker(ActionRef.Travel, StartPayload(p1,
        startArgs = Vector(DecisionOptionRef.Site(CatalogNames.site("Dunes"))))))
    assertEquals(Look(travelled.state).pawn(p1), CatalogNames.site("Dunes"))

  test("a journaled situation cannot start at a table, since a stream begins with GameStarted"):
    val service = oathdigital.application.ParkedServiceFixture.service()
    val driver = Situation.journaled(service, catalog, "table")
    intercept[munit.FailException](Table.start.situation(driver))
```

Notes:
- `ParkedServiceFixture.service()` may not exist under that name. Before running, open `src/test/scala/oathdigital/application/ParkedServiceFixture.scala` and use whatever it exposes to build a `GameApplicationService` (for example the helper that `woken` uses). If it has none, build one the way `ParkedServiceFixture` does.
- Check the Muster expectation against `EconomyFixture`'s Muster test (`MusterProcedureSuite`, "Muster costs one Supply…"): favor −1, Supply −1, warbands +1 with no matching adviser, and 1 favor on the card.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: compilation fails with "Not found: Table" / "Not found: Look".

- [ ] **Step 3: Write `Table.scala`**

```scala
package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Names as the catalog prints them, resolved to ids. An unknown name fails
  * the test and lists the five closest names, so a typo reads as one. */
object CatalogNames:
  private def catalog: ExecutableCatalog = FirstGameSetupFixture.catalog

  def site(site: String | SiteId)(using munit.Location): SiteId = site match
    case id: SiteId => id
    case name: String => catalog.sites.find(_.name == name).map(_.id)
      .getOrElse(unknown("site", name, catalog.sites.map(_.name)))

  def denizen(card: String | DenizenId)(using munit.Location): DenizenId =
    card match
      case id: DenizenId => id
      case name: String => catalog.denizens.find(_.name == name)
        .map(d => DenizenId(d.id.value))
        .getOrElse(unknown("denizen", name, catalog.denizens.map(_.name)))

  def worldCard(card: String | WorldCardId)(using munit.Location): WorldCardId =
    card match
      case id: WorldCardId => id
      case name: String => denizen(name)

  def relic(card: String | RelicId)(using munit.Location): RelicId = card match
    case id: RelicId => id
    case name: String => catalog.relics.find(_.name == name)
      .map(r => RelicId(r.id.value))
      .getOrElse(unknown("relic", name, catalog.relics.map(_.name)))

  /** An edifice by either face's name. */
  def edifice(card: String | EdificeId)(using munit.Location): EdificeId =
    card match
      case id: EdificeId => id
      case name: String => catalog.edifices
        .find(e => e.intact.name == name || e.ruined.name == name)
        .map(e => EdificeId(e.id.value))
        .getOrElse(unknown("edifice", name, catalog.edifices.flatMap(e =>
          Vector(e.intact.name, e.ruined.name))))

  /** A denizen, relic or edifice by name. */
  def card(card: String | CardId)(using munit.Location): CardId = card match
    case id: CardId => id
    case name: String =>
      catalog.denizens.find(_.name == name).map(d => DenizenId(d.id.value): CardId)
        .orElse(catalog.relics.find(_.name == name).map(r => RelicId(r.id.value)))
        .orElse(catalog.edifices.find(e => e.intact.name == name ||
          e.ruined.name == name).map(e => EdificeId(e.id.value)))
        .getOrElse(unknown("card", name, catalog.denizens.map(_.name) ++
          catalog.relics.map(_.name)))

  /** The printed name of `card`, for failure messages. */
  def nameOf(card: CardId): String = card match
    case DenizenId(id) => catalog.denizens.find(_.id.value == id).fold(id)(_.name)
    case RelicId(id) => catalog.relics.find(_.id.value == id).fold(id)(_.name)
    case EdificeId(id) => catalog.edifices.find(_.id.value == id)
      .fold(id)(_.intact.name)
    case other => other.toString

  private def unknown(kind: String, name: String, names: Vector[String])(
      using munit.Location): Nothing =
    val closest = names.sortBy(distance(name.toLowerCase, _)).take(5)
    munit.Assertions.fail(
      s"no $kind named \"$name\"; closest: ${closest.mkString(", ")}")

  private def distance(a: String, b0: String): Int =
    val b = b0.toLowerCase
    val row = Array.tabulate(b.length + 1)(identity)
    for i <- 1 to a.length do
      var diagonal = row(0)
      row(0) = i
      for j <- 1 to b.length do
        val above = row(j)
        row(j) = math.min(math.min(row(j) + 1, row(j - 1) + 1),
          diagonal + (if a(i - 1) == b(j - 1) then 0 else 1))
        diagonal = above
    row(b.length)

/** A game state assembled directly for a rule test, not reached by play
  * (`CONTEXT.md`, "Table").
  *
  * [[Table.start]] is the quiet table: the fixture's real first-game start
  * from `GameStartRules`, with no setup walk. p1, p2 and p3 sit in that turn
  * order, and it is p1's Act. Their pawns stand at Ancient City, Broken
  * Peaks and Buried Giant. No site holds a denizen, edifice, relic, bandit
  * or wealth, nobody holds an adviser, and every board keeps its printed
  * start: 1 favor, 1 faceup secret, 3 warbands and 7 Supply. Ancient City
  * carries the River site power, a Wake option: a test that puts p1 in Wake
  * there has something to decide.
  *
  * Each step states one fact. A step that places a card first takes it out
  * of every zone that held it, so the inventory stays whole. A card the
  * first game did not deal joins the table. `ready` checks the card index
  * and the warband inventory and fails the test naming what is wrong.
  */
final case class Table private (private val game: ReadyGame,
    private val joined: Set[CardId]):
  import Table.*

  def turn(player: PlayerId, phase: Phase): Table =
    update(_.updateCurrent(_.copy(turn = TurnState(player, phase, Set.empty))))

  def pawn(player: PlayerId, at: String | SiteId)(using munit.Location): Table =
    val site = CatalogNames.site(at)
    onPlayer(player)(_.copy(pawnSite = Some(site)))

  /** `card` as `player`'s adviser: a denizen, or a Vision by its id. */
  def adviser(player: PlayerId, card: String | WorldCardId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.worldCard(card)
    val state: AdviserState = id match
      case denizen: DenizenId => DenizenState(denizen, orientation(facedown),
        Tokens.empty)
      case vision: VisionId => VisionState(vision, orientation(facedown))
    moving(id).onPlayer(player)(p => p.copy(advisers = p.advisers :+ state))

  def relic(player: PlayerId, card: String | RelicId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.relic(card)
    moving(id).onPlayer(player)(p => p.copy(relics = p.relics :+
      RelicState(id, orientation(facedown), Tokens.empty)))

  def denizen(card: String | DenizenId, at: String | SiteId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.denizen(card)
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(denizens =
      s.denizens :+ DenizenState(id, orientation(facedown), Tokens.empty)))

  /** A relic at a site. Relics at sites lie facedown in play. */
  def relicAt(card: String | RelicId, at: String | SiteId,
      facedown: Boolean = true)(using munit.Location): Table =
    val id = CatalogNames.relic(card)
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(relics = s.relics :+
      RelicState(id, orientation(facedown), Tokens.empty)))

  def edifice(card: String | EdificeId, side: EdificeSide,
      at: String | SiteId)(using munit.Location): Table =
    val id = CatalogNames.edifice(card)
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(denizens =
      s.denizens :+ EdificeState(id, side, Tokens.empty)))

  /** Sets the tokens on `card`, wherever it sits. */
  def tokens(card: String | CardId, favor: Int = 0, secrets: Int = 0)(
      using munit.Location): Table =
    val id = CatalogNames.card(card)
    val set = Tokens(favor, secrets)
    update(_.updateCurrent(c => c.copy(
      map = c.map.copy(sites = c.map.sites.view.mapValues(s => s.copy(
        denizens = s.denizens.map {
          case d: DenizenState if d.id == id => d.copy(tokens = set)
          case e: EdificeState if e.id == id => e.copy(tokens = set)
          case other => other },
        relics = s.relics.map(r => if r.id == id then r.copy(tokens = set)
          else r))).toMap),
      players = c.players.map(p => p.copy(
        advisers = p.advisers.map {
          case d: DenizenState if d.id == id => d.copy(tokens = set)
          case other => other },
        relics = p.relics.map(r => if r.id == id then r.copy(tokens = set)
          else r))))))

  /** These cards on top of the world deck, first named on top. */
  def worldDeckTop(cards: (String | WorldCardId)*)(using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.worldCard)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck = ids ++
        c.commonCards.worldDeck))))

  def favor(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(favor = n))
  def secrets(player: PlayerId, faceUp: Int, faceDown: Int = 0): Table =
    onBoard(player)(_.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown))
  def supply(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(supply = SupplyTrack(n)))
  def warbands(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(warbands = n))

  def bandits(at: String | SiteId, n: Int)(using munit.Location): Table =
    forces(at, n, ForceKind.Bandit)

  /** `n` of `owner`'s warbands at a site, which `owner` then rules. */
  def warbandsAt(at: String | SiteId, owner: PlayerId, n: Int)(
      using munit.Location): Table =
    forces(at, n, ForceKind.Exile(playerOf(owner).lineage))

  def siteTokens(at: String | SiteId, favor: Int = 0, secrets: Int = 0)(
      using munit.Location): Table =
    onSite(CatalogNames.site(at))(_.copy(tokens = Tokens(favor, secrets)))

  def peoplesFavor(holder: Option[PlayerId], favor: Int): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(peoplesFavor =
      c.banners.peoplesFavor.copy(holder = holder, favor = favor)))))

  def darkestSecret(holder: Option[PlayerId], secrets: Int): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(darkestSecret =
      c.banners.darkestSecret.copy(holder = holder, secrets = secrets)))))

  def bankFavor(suit: Suit, n: Int): Table =
    update(r => r.copy(banks = r.banks.copy(favor = r.banks.favor.updated(suit, n))))

  /** Last resort, for a fact no step states. Say why at the call site; a
    * fact three suites need becomes a step. */
  def update(f: ReadyGame => ReadyGame): Table = copy(game = f(game))

  /** The state, after checking that no card is lost or in two places and
    * that no force outnumbers its printed supply. */
  def ready(using munit.Location): ReadyGame =
    CardIndex.from(game.game, inventory ++ joined).left.foreach { problems =>
      munit.Assertions.fail("the table's cards are not whole: " +
        problems.map(describe).mkString("; "))
    }
    game.banks.warbandSupply.foreach { case (kind, printed) =>
      val inPlay = game.game.current.players.filter(p =>
        PlayerForceKind.of(game, p).contains(kind)).map(_.board.warbands).sum +
        game.game.current.map.sites.values.map(_.forces).collect {
          case SiteForces.Occupied(`kind`, count) => count }.sum
      if inPlay > printed then munit.Assertions.fail(
        s"$kind: $inPlay warbands in play, more than the $printed printed")
    }
    game

  def state(using munit.Location): OathState = Ready(ready)

  /** A situation at this table, driven by `driver`, which must be a rules
    * adapter: a journal's stream begins with `GameStarted`, so a journaled
    * situation cannot start here. */
  def situation(driver: SituationDriver)(using munit.Location): Situation =
    driver match
      case _: SituationDriver.Journaled => munit.Assertions.fail(
        "a Table has no journal; start a journaled situation with " +
          "Situation.wake")
      case rules => Situation(state, Vector.empty, 0L, rules)

  private def moving(card: CardId): Table =
    Table(without(game, card), if inventory(card) then joined else joined + card)

  private def onPlayer(player: PlayerId)(f: PlayerState => PlayerState): Table =
    update(_.updateCurrent(c => c.copy(players = c.players.map(p =>
      if p.player == player then f(p) else p))))

  private def onBoard(player: PlayerId)(
      f: PlayerBoardState => PlayerBoardState): Table =
    onPlayer(player)(p => p.copy(board = f(p.board)))

  private def onSite(site: SiteId)(f: SiteState => SiteState): Table =
    update(_.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, f(c.map.sites(site)))))))

  private def forces(at: String | SiteId, n: Int, kind: ForceKind)(
      using munit.Location): Table =
    onSite(CatalogNames.site(at))(_.copy(forces =
      if n == 0 then SiteForces.Empty else SiteForces.Occupied(kind, n)))

  private def playerOf(player: PlayerId): PlayerState =
    game.game.current.players.find(_.player == player).get

object Table:
  val p1: PlayerId = PlayerId("p1")
  val p2: PlayerId = PlayerId("p2")
  val p3: PlayerId = PlayerId("p3")

  private val homes: Map[PlayerId, String] = Map(
    p1 -> "Ancient City", p2 -> "Broken Peaks", p3 -> "Buried Giant")

  /** Where `player`'s pawn stands on the quiet table. */
  def homeOf(player: PlayerId)(using munit.Location): SiteId =
    CatalogNames.site(homes(player))

  /** The quiet table (see [[Table]]). */
  lazy val start: Table =
    val orders = FirstGameSetupFixture.orders.copy(firstPlayer = p1)
    val fresh = GameStartRules.evolve(FirstGameSetupFixture.catalog,
      FirstGameSetupFixture.chronicle, orders).fold(violation =>
        throw AssertionError(s"the fixture's first game does not start: $violation"),
        identity)
    Table(quiet(fresh), Set.empty)

  /** Every card the fixture's first game deals, wherever it lies. */
  private lazy val inventory: Set[CardId] =
    CardIndex.from(start.game.game).toOption.get.ids

  private def orientation(facedown: Boolean): Orientation =
    if facedown then Orientation.FaceDown else Orientation.FaceUp

  /** The start with every hand, site card, relic, force and site wealth
    * put back: dealt hands and site denizens to the bottom of the world
    * deck, edifices to the edifice deck, relics to the bottom of the relic
    * deck. `banks.warbandSupply` is the printed inventory and the bank holds
    * whatever is not in play, so clearing a site's forces returns them to
    * the bank with no change there. Pawns go to [[homes]], and the turn is
    * p1's Act. */
  private def quiet(fresh: ReadyGame): ReadyGame = fresh.updateCurrent { c =>
    val sites = c.map.inPlay.map(c.map.sites)
    val hands = c.players.flatMap(p => c.temporaryHands.getOrElse(p.player,
      Vector.empty))
    val denizens = sites.flatMap(_.denizens).collect { case d: DenizenState => d.id }
    val edifices = sites.flatMap(_.denizens).collect { case e: EdificeState => e.id }
    val relics = sites.flatMap(_.relics).map(_.id)
    c.copy(
      temporaryHands = Map.empty,
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck ++ hands ++ denizens,
        edificeDeck = c.commonCards.edificeDeck ++ edifices,
        relicDeck = c.commonCards.relicDeck ++ relics),
      map = c.map.copy(sites = c.map.sites.view.mapValues(_ =>
        SiteState(SiteForces.Empty, Vector.empty, Vector.empty, Tokens.empty))
        .toMap),
      players = c.players.map(p => p.copy(pawnSite = Some(
        c.map.inPlay.find(id => FirstGameSetupFixture.catalog.sites
          .exists(s => s.id == id && s.name == homes(p.player))).get))),
      turn = TurnState(p1, Phase.Act, Set.empty))
  }

  /** `ready` with `card` taken out of every zone and every viewer's memory
    * of it. */
  private def without(ready: ReadyGame, card: CardId): ReadyGame =
    def keep(id: CardId): Boolean = id != card
    val c = ready.game.current
    val zones = c.commonCards
    val current = c.copy(
      commonCards = zones.copy(
        worldDeck = zones.worldDeck.filter(keep),
        relicDeck = zones.relicDeck.filter(keep),
        edificeDeck = zones.edificeDeck.filter(keep),
        legacyDeck = zones.legacyDeck.filter(keep),
        regionalDiscards = zones.regionalDiscards.view.mapValues(
          _.filter(keep)).toMap),
      map = c.map.copy(sites = c.map.sites.view.mapValues(s => s.copy(
        denizens = s.denizens.filter(d => keep(d.id)),
        relics = s.relics.filter(r => keep(r.id)))).toMap),
      players = c.players.map(p => p.copy(
        advisers = p.advisers.filter(a => keep(a.id)),
        relics = p.relics.filter(r => keep(r.id)),
        revealedVision = p.revealedVision.filter(v => keep(v.id)))),
      temporaryHands = c.temporaryHands.view.mapValues(_.filter(keep)).toMap,
      setAsideRelics = c.setAsideRelics.filter(keep))
    val campaign = ready.game.campaign
    ready.copy(
      game = ready.game.copy(current = current, campaign = campaign.copy(
        reliquary = campaign.reliquary.filter(keep),
        dispossessed = campaign.dispossessed.filter(keep),
        suitedReserves = campaign.suitedReserves.view.mapValues(
          _.filter(keep)).toMap,
        lineages = campaign.lineages.view.mapValues(l => l.copy(
          legacies = l.legacies.filter(x => keep(x.id)),
          startingAdvisers = l.startingAdvisers.filter(a => keep(a.id))))
          .toMap,
        atlas = AtlasState(campaign.atlas.entries.map {
          case s: AtlasEntry.StoredSite => s.copy(
            denizens = s.denizens.filter(d => keep(d.id)),
            relics = s.relics.filter(r => keep(r.id)),
            edifice = s.edifice.filter(keep))
          case other => other
        }))),
      knowledge = CardKnowledge(
        siteRelics = ready.knowledge.siteRelics.view.mapValues(
          _.view.mapValues(_.filter(keep)).toMap).toMap,
        advisers = ready.knowledge.advisers.view.mapValues(_.filter(keep)).toMap,
        heldRelics = ready.knowledge.heldRelics.view.mapValues(
          _.filter(keep)).toMap))

  private def describe(problem: CardIndexProblem): String = problem match
    case CardIndexProblem.DuplicateCard(id, locations) =>
      s"${CatalogNames.nameOf(id)} is in ${locations.size} places: " +
        locations.mkString(", ")
    case CardIndexProblem.MissingCard(id) =>
      s"${CatalogNames.nameOf(id)} is missing"
    case other => other.toString
```

Before compiling, check `CardIndexProblem.DuplicateCard`'s real field list in `src/main/scala/oathdigital/model/CardIndex.scala` (around line 40). Adjust the pattern in `describe` to match, keeping the card name and its locations in the message.

- [ ] **Step 4: Write `Look.scala`**

```scala
package oathdigital.testkit

import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The reads rule tests make of a state, named the way `Table` names what it
  * places. */
final class Look private (ready: ReadyGame):
  private def current = ready.game.current

  def player(p: PlayerId): PlayerState = current.players.find(_.player == p)
    .getOrElse(throw AssertionError(s"no player $p"))
  def supply(p: PlayerId): Int = player(p).board.supply.supply
  def favor(p: PlayerId): Int = player(p).board.favor
  def faceUpSecrets(p: PlayerId): Int = player(p).board.faceUpSecrets
  def faceDownSecrets(p: PlayerId): Int = player(p).board.faceDownSecrets
  def warbands(p: PlayerId): Int = player(p).board.warbands
  def advisers(p: PlayerId): Vector[CardId] = player(p).advisers.map(_.id)
  def relics(p: PlayerId): Vector[RelicId] = player(p).relics.map(_.id)
  def pawn(p: PlayerId): SiteId = player(p).pawnSite
    .getOrElse(throw AssertionError(s"$p has no pawn on the map"))

  def denizens(site: String | SiteId)(using munit.Location): Vector[CardId] =
    current.map.sites(CatalogNames.site(site)).denizens.map(_.id)
  def relicsAt(site: String | SiteId)(using munit.Location): Vector[RelicId] =
    current.map.sites(CatalogNames.site(site)).relics.map(_.id)
  def forces(site: String | SiteId)(using munit.Location): SiteForces =
    current.map.sites(CatalogNames.site(site)).forces
  def siteTokens(site: String | SiteId)(using munit.Location): Tokens =
    current.map.sites(CatalogNames.site(site)).tokens

  def phase: Phase = current.turn.phase
  def active: PlayerId = current.turn.activePlayer

  /** The tokens on `card`, wherever it sits; empty for a card that carries
    * none. */
  def tokensOn(card: String | CardId)(using munit.Location): Tokens =
    val id = CatalogNames.card(card)
    val onSites = current.map.sites.values.flatMap(s =>
      s.denizens.filter(_.id == id).map(_.tokens) ++
        s.relics.filter(_.id == id).map(_.tokens))
    val held = current.players.flatMap(p =>
      p.advisers.collect { case d: DenizenState if d.id == id => d.tokens } ++
        p.relics.filter(_.id == id).map(_.tokens))
    (onSites ++ held).headOption.getOrElse(Tokens.empty)

object Look:
  def apply(ready: ReadyGame): Look = new Look(ready)
  def apply(state: OathState)(using munit.Location): Look = state match
    case Ready(ready) => new Look(ready)
    case other => munit.Assertions.fail(s"expected a ready game, got $other")
```

- [ ] **Step 5: Run `TableSuite` and fix until green**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: all tests pass.

Where a test fails because of a real game rule, fix the test's staging and keep the intent. For example, if Travel to Dunes is not reachable from Ancient City for Supply reasons, pick a destination the rules accept and name it in the test. Do not weaken the assertions.

- [ ] **Step 6: Update `CONTEXT.md`**

Replace the **Situation** entry and add **Table** after it:

```markdown
**Situation**:
A game reached by real play from a first-game input: every command issued and
every parked decision answered along the way, in order. Named situations are
Wake (Setup complete: the first player's turn, already in Act when that Wake
had nothing to decide), Act (a player's Wake ended) and Rest (a player's Act
ended); any other is described by the steps that reach it. Driven by the rules
adapter, a situation may also start at a Table.
_Avoid_: position (a journal sequence number), fixture, snapshot, initial
game, setup state

**Table**:
A game state assembled directly for a rule test, not reached by play: the real
first-game start made quiet (p1's Act, pawns at their own sites, nothing at the
sites), plus one stated fact per step. A rule test states every fact it relies
on and inherits nothing incidental from Setup.
_Avoid_: board fixture, base state, initial ready
```

- [ ] **Step 7: Run the full root suite**

Run: `./sbtw root/test`
Expected: everything passes (2308 + the new `TableSuite` tests).

- [ ] **Step 8: Commit**

```bash
git add src/test/scala/oathdigital/testkit/Table.scala src/test/scala/oathdigital/testkit/Look.scala src/test/scala/oathdigital/testkit/TableSuite.scala CONTEXT.md
git commit -m "$(cat <<'EOF'
test(testkit): add a Table builder for rule-test states

Rule tests reached their state by replaying the first-game setup and
patching it, inheriting the setup walk's incidental facts. Table
builds from the real game start without the walk, quiets it, and
states one fact per step while keeping the card inventory whole.
Look is the matching read side; situation() hands a table to the
rules adapter so tests can drive real commands.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Pilot: `EconomyFixture` onto `Table`

**Files:**
- Modify (suites):
  - `gameplay/`: `EnclosingProcedureSuite`, `EconomyWalkerSuite`, `SelectionPaymentsSuite`, `MusterProcedureSuite`, `TradeProcedureSuite`
  - `gameplay/powers/economy/`: `CupOfPlentySuite`, `BirdsongSuite`, `AnimalPlaymatesSuite`, `KnightsErrantSuite`, `RowdyPubSuite`
  - `application/`: `GamePresentationProjectorAdviserRedactionSuite`, `SupplyProjectionSuite`, `EconomyProjectionSuite`

  Every path is under `src/test/scala/oathdigital/`.
- Modify: `src/test/scala/oathdigital/gameplay/EconomyFixture.scala`. Keep `AddAdviserSource` and `FreePayment` (test powers, not a board). Delete `act`, `player`, `spring`, `matchingAdviser`, and the id constants once they are unused.
- Consumes: Task 1's `Table`, `Look` and `CatalogNames`.

**What `EconomyFixture.act` hides:**
- The active player (now p1) stands at their site, which holds one token-free "plain" denizen: Alchemist (catalog id 9, Arcane).
- A same-suit "matching" denizen is available as an adviser: Magician's Code (id 32).
- The Arcane bank holds `bank` favor (default 5).
- The board has favor 4, 2 faceup secrets, 7 Supply and 3 warbands.
- Hallowed Spring is edifice E26.

The `economic` id set existed only to keep powered denizens off the site. On the quiet table, nothing is at the site unless placed, so the set goes.

- [ ] **Step 1: Worked example: rewrite `MusterProcedureSuite`'s Muster test**

Before:

```scala
test("Muster costs one Supply and one favor and gains one warband per " +
    "matching adviser plus one"):
  val after = muster(act(advisers = Vector(matchingAdviser)),
    DecisionOptionRef.Denizen(plainId))
  assertEquals(player(after).board.favor, 3)
  ...
```

After:

```scala
test("Muster costs one Supply and one favor and gains one warband per " +
    "matching adviser plus one"):
  val ready = Table.start
    .denizen("Alchemist", at = Table.homeOf(p1))
    .adviser(p1, "Magician's Code")          // the same suit, Arcane
    .favor(p1, 4).warbands(p1, 3)
    .ready
  val after = muster(ready, DecisionOptionRef.Denizen(CatalogNames.denizen("Alchemist")))
  val look = Look(after)
  assertEquals(look.favor(p1), 3)
  assertEquals(look.supply(p1), 6)
  assertEquals(look.warbands(p1), 5)
  assertEquals(look.tokensOn("Alchemist"), Tokens(1, 0))
```

The suite's private `parked`/`muster` walker helpers stay: they drive the procedure and build no board. Replace `player(ready).player` inside them with `p1`.

- [ ] **Step 2: Apply the shared per-suite procedure to the other 12 suites**

Follow "Shared migration rules". For suites that only call `act()` with no arguments, keep the facts the test reads. For example: `.denizen("Alchemist", at = Table.homeOf(p1)).favor(p1, 4).secrets(p1, faceUp = 2)`, plus `.bankFavor(Suit.Arcane, 5)` where a test reads the bank.

- [ ] **Step 3: Delete the emptied helpers from `EconomyFixture`**

Run: `grep -rn "EconomyFixture\.\(act\|player\|spring\|matching\|plain\)" src/test`
Expected: no output. Then delete those members.

- [ ] **Step 4: Run the 13 suites, then the full suite**

Run: `./sbtw "root/testOnly oathdigital.gameplay.*Muster* oathdigital.gameplay.*Trade* oathdigital.gameplay.powers.economy.* oathdigital.application.*Economy* oathdigital.application.*Supply* oathdigital.application.GamePresentationProjectorAdviserRedactionSuite oathdigital.gameplay.EnclosingProcedureSuite oathdigital.gameplay.EconomyWalkerSuite oathdigital.gameplay.SelectionPaymentsSuite"`, then `./sbtw root/test`
Expected: all pass, with the same test count per suite as before.

- [ ] **Step 5: Record timings and commit**

Note the 13 suites' summed CPU time before (from a `root/test` run on the parent commit) and after, in the commit body.

```bash
git add -u src/test
git commit -m "$(cat <<'EOF'
test(economy): build Muster and Trade states with Table

The Economy suites stated their board through EconomyFixture.act,
which replayed setup on every call and hid the actor, the site card
and the matching adviser. Each test now names them.

CPU: <measured before>s -> <measured after>s for these suites

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `ChallengeFixture`, `NegotiationFixture`, `PlacementFixture`

**Files:**
- **Challenge:** `gameplay/ChallengeProcedureSuite`, `gameplay/PlaceBannerResourceProcedureSuite`, `gameplay/powers/targeting/FortressRulesSuite`, `gameplay/powers/targeting/CircletOfCommandSuite`, `application/ChallengeProjectionSuite`.
- **Negotiation:** `gameplay/NegotiationDealSuite`, `gameplay/NegotiationProcedureSuite`, `application/NegotiationDealProjectionSuite`.
- **Placement:**
  - `gameplay/`: `HomelandLineSuite`, `PlacementRulesSuite`, `SiteDiscardFirstSuite`, `DiscardRestrictionsSuite`, `HomelandRuleSuite`, `CardPlayProcedureSuite`
  - `gameplay/powers/banner/PeoplesFavorMobSuite`
- **Fixtures:** delete `ChallengeFixture.ready`, `enemyHolds`, `withSiteSecrets`, `active` and `enemy`, and `NegotiationFixture.board`, `relocate`, `withThirdElsewhere`, `isolated` and `player`. Delete `PlacementFixture.staged`, `actorOf`, `homeland` and `ruledByActor`. Keep `PlacementFixture`'s test powers (`rulePower`, `discardFirst`, `limitTwo`) and its walker helpers (`build`, `park`, `answer`, `options`, `decisionId`).

**What the boards hide:**
- `ChallengeFixture.ready(resources, favor, faceup, facedown, banner)`: the actor's board values, and a banner held by the actor with `resources`.
  - `enemyHolds` moves the rival to a site other than the actor's and gives them the banner.
  - `withSiteSecrets` sets site secrets.
  - These become `.favor`, `.secrets`, `.peoplesFavor` / `.darkestSecret`, `.pawn` and `.siteTokens`.
- `NegotiationFixture.board()`: the three players share a site that holds a relic, the other two hold relics, and knowledge records them. Read `NegotiationFixture.scala:19-40` in full, then state each of those facts with `.pawn`, `.relicAt` and `.relic`.
  - For the knowledge entries, use `.update` with a comment (only this fixture needs them), unless three suites need them. In that case add a `.knows(player, relic)` step to `Table` with a `TableSuite` test.
- `PlacementFixture.staged(card, site, at)`: the actor's site holds exactly `site`, and `card` is in the actor's hand to play. `homeland` is the first in-play site with a Homeland handler, and its suit.
  - On the quiet table, name the site: Deep Woods and Golden Valley are the fixture's Homelands.
  - Look up each suit in the catalog (`site:deep-woods` handlers) and write it as a named constant with a comment.

- [ ] **Step 1: Worked example: `ChallengeProcedureSuite`'s first test**

Open the suite and rewrite its first test's `ready(...)` call as a `Table` chain. For `ready(resources = 2, banner = Banner.PeoplesFavor)` that is:

```scala
val ready = Table.start
  .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)
  .peoplesFavor(Some(p1), favor = 2)
  .ready
```

Take the board values from `ChallengeFixture.ready`'s default parameters (favor 6, faceup 6, facedown 4).

- [ ] **Step 2: Migrate the remaining suites by the shared procedure**
- [ ] **Step 3: Delete the emptied fixture members**

Run: `grep -rn "ChallengeFixture\.\(ready\|enemyHolds\|withSiteSecrets\|active\|enemy\)\|NegotiationFixture\.\(board\|isolated\|withThirdElsewhere\|player\)\|PlacementFixture\.\(staged\|actorOf\|homeland\|ruledByActor\)" src/test`
Expected: no output. Delete the members, and delete `ChallengeFixture.scala` / `NegotiationFixture.scala` if they are empty.

- [ ] **Step 4: Run the 15 suites, then `./sbtw root/test`**

Expected: all pass, with test counts unchanged.

- [ ] **Step 5: Commit** as `test(challenge,negotiation,placement): build states with Table`, with the same body shape as Task 2 (why, plus the CPU line).

---

### Task 4: `OathkeeperFixture`, `PhasePowerFixture`

**Files:**
- `OathkeeperFixture` users: `gameplay/EconomyWalkerSuite`, `gameplay/WalkerReplayDriftSuite`, `gameplay/StateBasedEvaluationSuite`, `gameplay/powers/title/ChaosCultSuite`, `gameplay/oathkeeper/OathkeeperRulesSuite`, `gameplay/oathkeeper/OathkeeperProcedureSuite`, `application/WalkerDecisionProjectorSuite`.
- `PhasePowerFixture` users: `gameplay/PhasePowerSuite`, `application/PhasePowerProjectorSuite`.
- **Fixtures:** delete `OathkeeperFixture.base`, `players` and `inPhase`. Replace `ruled(ready, owners, …)`, which sets each in-play site's forces from a list, with `.warbandsAt` / `.bandits` chains in the tests. Delete `PhasePowerFixture.base`, `actor`, `card` and `inPhase`. Keep its test power (the object at lines 12–20), but give it a named card chosen on the quiet table.
- `WalkerReplayDriftSuite` stays on replay (spec, "Staying on replay"). Migrate only its `OathkeeperFixture` uses.

**Details:**
- **`PhasePowerFixture` card.** It picks "the first world-deck denizen without a phase power", places it at an empty site, and finds its power id. On the quiet table, name one such card explicitly: pick one from `catalog.denizens` with a single non-phase power, and put its name in a constant with a comment. Place it with `.denizen(name, at = …)`.
- **`inPhase(phase)`** becomes `.turn(p1, phase)`.

- [ ] **Step 1: Worked example: `OathkeeperRulesSuite`**

`ruled(base, Vector(Some(p), Some(p), None, …))` gives the player the first two in-play sites. On the quiet table:

```scala
val ready = Table.start
  .warbandsAt("Ancient City", p1, 1)
  .warbandsAt("Broken Peaks", p1, 1)
  .ready
```

Keep the site order the original used, `map.inPlay`: Ancient City, Broken Peaks, Buried Giant, Deep Woods, Desolate Shore, Dunes, Fair Isle, Golden Valley.

- [ ] **Step 2: Migrate the remaining suites by the shared procedure**
- [ ] **Step 3: Delete the emptied fixture members.** Verify with `grep -rn "OathkeeperFixture\.\(base\|inPhase\|players\|ruled\)\|PhasePowerFixture\.\(base\|actor\|inPhase\)" src/test`: no output.
- [ ] **Step 4: Run the 9 suites, then `./sbtw root/test`.** Expected: all pass.
- [ ] **Step 5: Commit** as `test(oathkeeper,phase-powers): build states with Table`.

---

### Task 5: Campaign gameplay suites off `CampaignFixture.board`

**Files:**
- `gameplay/`: `CampaignProcedureSuite`, `CampaignRaidSuite`, `CampaignPlanWindowSuite`, `CampaignPlansSuite`, `CampaignSetupSuite`, `StateBasedEvaluationSuite` (the Campaign uses only)
- `gameplay/powers/economy/KnightsErrantSuite`
- `gameplay/powers/targeting/`: `TargetingFixture`, `FortressRulesSuite`, `ForgottenVaultSuite`, `CircletOfCommandSuite`
- `application/`: `WalkerDecisionProjectionSuite`, `WalkerDecisionProjectorSuite` (the Campaign uses only)
- Do **not** touch `gameplay/powers/campaign/*`. Task 6 handles them.

**What `CampaignFixture.board(extras, warbands, supply)` hides:**
- The actor stands at an origin ruled by 2 bandits: the first in-play site that is neither mountain nor plains.
- `extras` more sites are bandit-ruled (2 each).
- Every other site is empty, and no site holds a denizen.
- The other player stands at the first site that isn't ruled.
- The actor has `warbands` warbands (default 5) and `supply` Supply (default 7). It is Act.

On the quiet table, write it as:

```scala
val campaign = Table.start
  .bandits(Table.homeOf(p1), 2)               // the defenders at p1's site
  .warbands(p1, 5)
```

- Ancient City (p1's home) is neither mountain nor plains: its handlers are `site.ancient-city.enduring` and `site.ancient-city.river`. It serves as the origin.
- Name each extra ruled site explicitly (`.bandits("Dunes", 2)`), in place of `extras`.
- `withEnemyAtOrigin` becomes `.pawn(p2, at = Table.homeOf(p1))`.
- `againstPlayer` / `actorRules` become `.warbandsAt(…)`.
- `raidBoard` becomes an inline chain with the named relic.

**Kept in `CampaignFixture`:** `rules`, `rulesWith`, `anyDice`, `dice`, `cardWith` and `relicWith` (Task 6 still uses them).

**Deleted once Task 6 lands, not here:** the `Board` class and `board`, `withEnemyAtOrigin`, `replacePlayer`, `withAdviser*`, `withRelic*`, `withEdifice`, `withSecrets`, `againstPlayer`, `actorRules`, `withSiteCard`, `scrub` and `raidBoard`.

- [ ] **Step 1: Worked example: `CampaignProcedureSuite` line ~68, the supply test**

```scala
val ready = Table.start.bandits(Table.homeOf(p1), 2).warbands(p1, 5)
  .supply(p1, 7).ready
...
assertEquals(Look(started.state).supply(p1), 5)   // 7 less the Campaign's 2
```

Write the Supply arithmetic in the comment, so the reader need not know a default.

- [ ] **Step 2: Migrate the remaining suites by the shared procedure**
- [ ] **Step 3: Run the listed suites, then `./sbtw root/test`.** Expected: all pass.
- [ ] **Step 4: Commit** as `test(campaign): build Campaign states with Table`.

---

### Task 6: Campaign power suites and `PlanDriver`

**Files:**
- All 19 suites in `src/test/scala/oathdigital/gameplay/powers/campaign/*Suite.scala`, and `PlanDriver.scala`.
- `CampaignFixture.scala`: delete the board members listed in Task 5.

**Details:**
- `PlanDriver.commit(game, b: Board, force, …)` takes a `Board`. Change it to take `(game: OathRules, ready: ReadyGame, force: Int, …)`, with the actor fixed as p1 and the defender read from the state. Keep `Run`, `winning`, `losing`, `inert`, `parked` and `awaits`.
- Delete `PlanDriver.ready` and `player`; use `Look`.
- The DAMP models here are `MercenariesSuite` and `GleamingArmorSuite`. Their private builders, such as `attackerHolds`, may stay if they meet the private-builder rule, rewritten as `Table` chains.

- [ ] **Step 1: Worked example: `GleamingArmorSuite.attackerHolds`**

```scala
/** p1 attacks p2 at p1's site. p1 holds Gleaming Armor; p2 defends with the
  * title and a Watchdog, and has `defenderSecrets` faceup secrets. */
private def attackerHolds(defenderSecrets: Int): ReadyGame =
  Table.start
    .warbandsAt(Table.homeOf(p1), p2, 2)          // p2 rules the site under attack
    .warbands(p1, 5)
    .adviser(p1, "Gleaming Armor")
    .adviser(p2, "Watchdog")
    .secrets(p2, faceUp = defenderSecrets)
    .ready
```

Read `CampaignFixture.againstPlayer` first. Copy exactly what it sets for "defender is a player", such as which site p2 rules, their pawn, and the title, into steps.

- [ ] **Step 2: Migrate the other 18 suites by the shared procedure**
- [ ] **Step 3: Delete the `Board` family from `CampaignFixture`.** Verify with `grep -rn "Board\b\|board(\|withEnemyAtOrigin\|replacePlayer\|withAdviserFor\|againstPlayer\|raidBoard" src/test/scala/oathdigital/gameplay`: only unrelated hits (for example `PlayerBoardState`).
- [ ] **Step 4: Run `./sbtw "root/testOnly oathdigital.gameplay.powers.campaign.*"`, then `./sbtw root/test`.** Expected: all pass, and the campaign suites' CPU time drops (record it).
- [ ] **Step 5: Commit** as `test(campaign-powers): build battle states with Table`.

---

### Task 7: `PowerFixture` family, part 1: `powers/action`

**Files:**
- The 32 suites and 1 fixture in `src/test/scala/oathdigital/gameplay/powers/action/`, listed by `ls src/test/scala/oathdigital/gameplay/powers/action/`.
- `PaidActionHarness.scala`, `MovementFixture.scala`.

**Details:**
- **Step 1 is a no-behavior change** that lets suites migrate one at a time. `PaidActionHarness.use`, `answer` and `usableIds` read the actor from the state (`ready.game.current.turn.activePlayer`) instead of `PowerFixture.actor`. `PowerFixture.actor` equals `initialReady`'s active player, so unmigrated suites are unaffected.
- `PaidActionHarness.act` (`inPhase(ready, Phase.Act)`) is deleted once unused: `Table.start` is already Act.
- `tokensOn` moves to `Look.tokensOn`.
- `MovementFixture` builds on `TargetsFixture`. Migrate its board part onto `Table`, and keep its driving helpers.

- [ ] **Step 1: Make `PaidActionHarness` read the actor from the state; run `./sbtw root/test`; commit** as `test(harness): read the acting player from the state`.

```scala
def use(rules: OathRules, ready: ReadyGame, id: PowerId,
    source: DecisionOptionRef): Either[OathViolation, OathTransition] =
  rules.startWalker(Ready(ready), ActionRef.UsePower(id),
    ready.game.current.turn.activePlayer, Vector.empty, Vector(source))
```

`answer(rules, state, …)` reads the awaited player from `state`'s active player the same way. Check each caller that resolves as someone other than the actor, and keep the explicit player there.

- [ ] **Step 2: Worked example: `GamblingHallSuite`**

`DenizenId("93")` becomes `"Gambling Hall"` (verify the name in the catalog JSON). `asAdviser(act(base), id)` becomes `Table.start.adviser(p1, "Gambling Hall").ready`.

- [ ] **Step 3: Migrate the other action suites and `MovementFixture` by the shared procedure**
- [ ] **Step 4: Run `./sbtw "root/testOnly oathdigital.gameplay.powers.action.*"`, then `./sbtw root/test`.** Expected: all pass.
- [ ] **Step 5: Commit** as `test(action-powers): build states with Table`.

---

### Task 8: `PowerFixture` family, part 2: `cardplay`, `whenplayed`, `search`, `economy` leftovers

**Files:**
- Every suite under `gameplay/powers/{cardplay,whenplayed,search}/`.
- Remaining `SearchFixture` / `CardStaging` users in `gameplay/powers/economy/`.
- Fixtures: `VisionPlayFixture.scala`, `WhenPlayedHarness.scala`, `SearchFixture.scala`, `CardStaging.scala`.

**Details:**
- **`SearchFixture.staged(top, supply)`** empties the actor's site and puts `top` on the world deck. It becomes `Table.start.worldDeckTop(top*).supply(p1, supply)`, since the quiet site is already empty.
- **`SearchFixture.denizensOf(suit)`** picks unpowered denizens of a suit. Replace each use with named cards: a test that needs "two plain Beast cards" names two.
- **`CardStaging.without`** disappears: every placing step moves the card.
- Keep the search driving helpers (`start`, `keep`, `place`, `play`, `playFacedown` and `replace`); change their fixed `actor` to p1.

- [ ] **Step 1: Worked example: `AugurySuite`'s first test** (rewrite its `staged(...)` call as above, naming the top cards).
- [ ] **Step 2: Migrate the rest by the shared procedure**
- [ ] **Step 3: Delete `CardStaging.scala`, and the board parts of `SearchFixture` and `VisionPlayFixture`.** Verify with `grep -rn "CardStaging\|SearchFixture\.staged\|denizensOf" src/test`: no output.
- [ ] **Step 4: Run the folders' suites, then `./sbtw root/test`**
- [ ] **Step 5: Commit** as `test(card-play-powers): build states with Table`.

---

### Task 9: `PowerFixture` family, part 3, and the inline cases

**Files:**
- **Powers suites:**
  - every suite under `gameplay/powers/{wake,travel,banner,targeting,recover}/` not yet migrated;
  - `gameplay/powers/AdviserLimitSuite`, `gameplay/powers/SelectedModifierSuite`;
  - `gameplay/RevealDiscardSuite`, `gameplay/RuleResolutionSuite`;
  - `application/BannerFaceProjectionSuite`, `application/DicePowerDecisionProjectionSuite`.
- **Fixtures:** `RiverFixture`, `TravelFixture`, `BannerFixture`, `TargetingFixture`, `LeagueTreatyFixture`, `SilverTongueFixture` (board parts only).
- **Delete:** `PowerFixture.scala` and `TargetsFixture.scala`'s board helpers (`updatePlayer`, `withSecrets`, `withPawn`, `giveAdviser`, `giveVision`, `withoutAdvisers`). Keep `TargetsFixture`'s rules, `parked` and the projection readers (`queryOf`, `offered`, `boardOf`, `publicBoardOf`) if they are still used.
- **Inline cases** (suites that build from `execute()` or patch the phase themselves):
  - `gameplay/`: `RecoverProcedureSuite`, `RecoverEligibilitySuite`, `TravelProcedureSuite`, `ForgeRulesSuite`, `ForgeProcedureSuite`, `RestSuite`, `StateBasedEvaluationSuite`, `OathRulesWalkerPowerSuite`, `CampaignPowersSuite`, `MinorActionsSuite`, `WakeAutoEndSuite`
  - `application/`: `PricedOptionProjectionSuite`, `WalkerDecisionQueryPowerSuite`, `WalkerDecisionProjectorSuite`
  - `gameplay/powers/wake/HungerSuite` and every other `turn.copy(phase = …)` / `TurnState(…)` patch listed by:
    `grep -rln "phase = Phase\.\|TurnState(" src/test | xargs grep -L "Table"`
  - The cross-suite borrows, such as `CatacombsContributionSuite.relicSite()` used from `OathRulesWalkerPowerSuite`: replace each with a `Table` chain inline in the borrowing test.

Leave these on replay (spec, "Staying on replay"):
- `SetupProcedureSuite`, `GameStartToWakeSuite`, `GameStartRulesSuite`, `SituationSuite`
- `WalkerReplayDriftSuite`, `GameEventWireSuite`, `EndWakeProcedureSuite`
- `GameApplicationServiceSuite` and the other service and route suites
- the `gamelog` suites, `ParkedServiceFixture`, `ForgeWalkerFixture`, `SetupWalkDriver`

**Details:**
- **`RecoverProcedureSuite.recoverable`** returns a 5-tuple `(base, actor, site, relic, difficulty)`. Replace it with a Table chain in each test, naming the site and relic, for example:
  `Table.start.pawn(p1, at = "Fair Isle").relicAt("Cracked Horn", at = "Fair Isle")`.
  Fair Isle is the easiest in-play Recover site (difficulty 3), which is what `recoverable` picked. These tests do not depend on the relic's identity, and Cracked Horn stands in for the relic deck's head. Confirm with `grep -rn cracked-horn src/main` that no Recover rule reads it.
  Read the builder at `RecoverProcedureSuite.scala:46-70` first.
- **`HungerSuite`** stays a DAMP model. Its `staged` builder becomes a Table chain: p2 holds Hunger, and p3 shares p2's site.

- [ ] **Step 1: Worked example: `HungerSuite.staged`**

```scala
/** p1 in Act. p2 holds Hunger (faceup unless told otherwise) and shares
  * Broken Peaks with p3. */
private def staged(orientation: Orientation = Orientation.FaceUp): ReadyGame =
  Table.start
    .adviser(p2, Hunger.forCatalog(catalog).get.cardId,
      facedown = orientation == Orientation.FaceDown)
    .pawn(p3, at = Table.homeOf(p2))
    .ready
```

`next` becomes `p2` and `third` becomes `p3`. The "starting adviser with tokens" that `stocked` edits must now be placed first. Name a plain denizen for p3, add it with `.adviser(p3, …)`, and set its tokens with `.tokens(…)` on the parked state. Keep the note that says why tokens are set on the parked state.

- [ ] **Step 2: Migrate the powers folders and fixtures, then the inline cases, by the shared procedure**
- [ ] **Step 3: Delete `PowerFixture.scala` and the `TargetsFixture` board helpers**

Verify with `grep -rn "PowerFixture\|initialReady\|execute()" src/test --include=*.scala | grep -v -e SetupProcedureSuite -e GameStartToWake -e GameStartRules -e SituationSuite -e WalkerReplayDrift -e GameEventWire -e EndWakeProcedure -e FirstGameSetupFixture -e gamelog/ -e ParkedServiceFixture -e ForgeWalkerFixture -e SetupWalkDriver -e GameApplicationService -e server/`
Expected: no output.

- [ ] **Step 4: `./sbtw root/test`.** Expected: all pass.
- [ ] **Step 5: Commit** as `test(powers): build the remaining rule-test states with Table`.

---

### Task 10: Verification against the success criteria, and the handoff list

**Files:**
- Modify: `docs/superpowers/specs/2026-09-27-test-table-builder-design.md` (append a "Result" section)

- [ ] **Step 1: Check the criteria by grep**

Run:
```bash
grep -rn "execute()\|initialReady" src/test --include=*.scala
grep -rn "phase = Phase\.\|def inPhase" src/test --include=*.scala
ls src/test/scala/oathdigital/gameplay/*Fixture.scala src/test/scala/oathdigital/gameplay/powers/*Fixture.scala
```

Expected:
- The first command lists only the "Staying on replay" suites and fixtures.
- The second lists only `Table.scala`, and suites whose subject is the phase itself (for example `WakeAutoEndSuite` asserting a phase). Review each hit.
- The third lists no board-building fixture.

- [ ] **Step 2: Brittleness check**

```bash
git log -1 --format=%H 5bf7d1a3
git revert --no-commit 5bf7d1a3
./sbtw root/test 2>&1 | grep -E "==> X|Failed:" > /private/tmp/brittle.txt
git revert --abort || git checkout -- . && git status --short
```

- `git revert --abort` restores the tree. If the revert conflicted, resolve it by aborting and running `git checkout -- .`.
- Confirm `git status --short` shows nothing but other sessions' files.
- Inspect `brittle.txt`: no failing suite may be one migrated in Tasks 2–9. The revert also removes `WakeAutoEndSuite`'s subject, so that suite's failures are expected and are not migration failures.
- If the revert does not apply cleanly on the current HEAD, do the check another way. Temporarily make `OathRules.wakeOptionOpen` return `true` (which disables the auto-end), run the suite, then restore it with `git checkout -- src/main`.

- [ ] **Step 3: Record the result in the spec**

Append:

```markdown
## Result

- Fixtures removed: <list>.
- `execute()` / `initialReady` remain in: <list>, as planned.
- Brittleness check: disabling the Wake auto-end failed <n> suites, none of
  them migrated (before: part of 158 tests).
- CPU: campaign suites <before>s → <after>s; whole root suite <before>s →
  <after>s.
- Tests that asserted nothing once incidental content was gone, for the
  pruning project: <list, or "none">.
```

Fill in the placeholders from the actual run. They are measured results, not plan content.

- [ ] **Step 4: Run `./sbtw root/test` one last time, then commit**

```bash
git add docs/superpowers/specs/2026-09-27-test-table-builder-design.md
git commit -m "$(cat <<'EOF'
docs(specs): record the Table builder migration result

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```
