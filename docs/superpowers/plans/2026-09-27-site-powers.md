# Site Powers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the River site power work, correct Homeland to its printed text, and show every site power's printed text.

**Architecture:**

- The River becomes a Wake `PhasePower` whose source is its site. There is one power per River handler, like `TravelSitePowers`, and `PhasePowerCatalog` registers them.
- `PhasePowerProcedure` learns to map a site source (`RuleSourceRef.Site`) to `PowerSourceRef.Site` and `DecisionOptionRef.Site`, and back. Until now a phase power could not come from a site.
- A power may name the source its Game Log notes are written under (`NotingPower.noteSource`). The River names "River"; every other power keeps its source's name.
- One table, `application/SitePowerText`, holds each site power's name and printed text. The phase power projection and the site details projection both read it.
- Homeland stays a card-play rule in `CardPlay`. A play of a card of the Homeland's suit to that site is treated as if a power permitted a discard first, and the Homeland is read from the site's own `homeland-<suit>` handler.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change: the frontend already renders one button per projected phase power and every projected site power.

**Spec:** `docs/superpowers/specs/2026-09-27-site-powers-design.md`. Read it all before starting; it is short.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). Every file touched here stays well under.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Test code may import `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - To merge at the end, leave the worktree with `ExitWorktree` (`keep`) first. The worktree guard refuses `git -C` on the main checkout.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- The River's line reads exactly "River: {Red} placed at {Ancient City}." Narrow Pass and the Homeland rule keep their site names as their source.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
- If a golden log under `src/test/resources/gamelog/` changes, stop and report which line changed. Do not regenerate it. No golden game uses the River or a Homeland discard, so none should change.

## File Structure

| File | Change | Task |
|---|---|---|
| `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` | `NotingPower.noteSource` | 1 |
| `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` | site source cases | 1 |
| `src/main/scala/oathdigital/gameplay/powers/wake/RiverSitePower.scala` | new: the River | 1 |
| `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` | registers the River | 1 |
| `src/test/scala/oathdigital/gameplay/powers/wake/RiverFixture.scala` | new: puts more Rivers in play | 1 |
| `src/test/scala/oathdigital/gameplay/powers/wake/RiverSitePowerSuite.scala` | new | 1 |
| `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala` | carries `noteSource` | 2 |
| `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` | writes a named source | 2 |
| `src/main/scala/oathdigital/application/SitePowerText.scala` | new: printed site powers | 2 |
| `src/main/scala/oathdigital/application/PhasePowerProjector.scala` | site source case | 2 |
| `src/main/scala/oathdigital/application/GamePresentationProjector.scala` | `sitePower` reads the table | 2 |
| `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` | named source tests | 2 |
| `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala` | River projection test | 2 |
| `src/test/scala/oathdigital/application/GamePresentationProjectorPrintedFacesSuite.scala` | site power text test | 2 |
| `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` | `homelandSuit`, Homeland as a permission | 3 |
| `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala` | doc comment | 3 |
| `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala` | doc comment | 3 |
| `src/test/scala/oathdigital/gameplay/PlacementFixture.scala` | `staged(..., at)`, `homeland` | 3 |
| `src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala` | new | 3 |
| `src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala` | stages at a real Homeland | 3 |
| `src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala` | stages at a real Homeland | 3 |
| `docs/ROADMAP.md`, `docs/rules/implementation-traceability.md`, `docs/superpowers/specs/2026-09-26-power-log-lines-design.md` | record | 4 |

---

### Task 1: The River is a Wake power of its site

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (trait `NotingPower`, near line 169)
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` (`sourceRef` and `sourceOf`, near lines 153-174)
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/RiverSitePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/wake/RiverFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/wake/RiverSitePowerSuite.scala`

**Interfaces:**
- Produces:
  - `NotingPower.noteSource: Option[String]`, default `None`.
  - `final case class RiverSitePower(id: PowerId, site: SiteId, catalog: ExecutableCatalog) extends PhasePower`.
  - `object RiverSitePower`:
    - `val name: String = "River"`
    - `val siteDecisionId: String = "power.river.site"`
    - `val placed: NoteKey`
    - `val supported: Vector[PowerId]`
    - `def isRiver(catalog: ExecutableCatalog, site: SiteId): Boolean`
    - `def forCatalog(catalog: ExecutableCatalog): Vector[RiverSitePower]`
  - Test: `object RiverFixture` with `ancientCity`, `riverbank`, `tidalMarshes: SiteId`, `withRivers(ready, rivers): ReadyGame`, `staged(rivers, phase): ReadyGame`, `river: RiverSitePower` (Ancient City's).

Context you need:

- `PhasePower` (`gameplay/powerresolver/PhasePower.scala`) has `id`, `timing`, `usable(ready, player, source)` and `build(ready, player, source)`. `PhasePowerProcedure.check` already enforces access, timing and the once-per-turn record, then asks `usable`.
- `RuleSourceIndex.enumerate` already lists each in-play site as `RuleSourceRef.Site(id)` with its handler ids as power ids, and `PowerAccess.accessible` already grants a site source to the player whose pawn is there. The only gap is `PhasePowerProcedure.sourceRef` and `sourceOf`, which drop site sources.
- `PawnMoves` (`gameplay/powers/action/PawnMoves.scala`) has the site question, the relocation `Move` and the "{player} placed at {site}." note that Magic Carpet uses. Reuse all three.
- The first-game fixture (`PowerFixture.base`) has one River in play, Ancient City, and the actor's pawn stands there. The tests swap two more Rivers in.

- [ ] **Step 1: Write the fixture**

Create `src/test/scala/oathdigital/gameplay/powers/wake/RiverFixture.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Staging for the River. The first game has one River in play, Ancient
  * City, where the actor's pawn stands. `withRivers` puts more in play.
  */
object RiverFixture:
  import PowerFixture._

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val riverbank: SiteId = SiteId("site:riverbank")
  val tidalMarshes: SiteId = SiteId("site:tidal-marshes")

  /** Ancient City's River. */
  lazy val river: RiverSitePower = RiverSitePower.forCatalog(catalog)
    .find(_.site == ancientCity).get

  /** `rivers` take the places of the first in-play sites, in map order, that
    * are not a River and hold no pawn. Each keeps the replaced site's state,
    * so every card and piece stays in the game.
    */
  def withRivers(ready: ReadyGame, rivers: Vector[SiteId]): ReadyGame =
    val current = ready.game.current
    require(rivers.forall(!current.map.inPlay.contains(_)),
      s"already in play: $rivers")
    val pawns = current.players.flatMap(_.pawnSite).toSet
    val replaced = current.map.inPlay.filter(site =>
      !RiverSitePower.isRiver(catalog, site) && !pawns.contains(site))
      .take(rivers.size)
    val swaps = replaced.zip(rivers).toMap
    def swap(ids: Vector[SiteId]) = ids.map(id => swaps.getOrElse(id, id))
    ready.updateCurrent(c => c.copy(map = c.map.copy(
      cradle = swap(c.map.cradle), provinces = swap(c.map.provinces),
      hinterland = swap(c.map.hinterland),
      sites = swaps.foldLeft(c.map.sites) { case (sites, (from, to)) =>
        sites.removed(from).updated(to, sites(from)) })))

  def staged(rivers: Vector[SiteId] = Vector(riverbank, tidalMarshes),
      phase: Phase = Phase.Wake): ReadyGame =
    inPhase(withRivers(base, rivers), phase)
```

If `MapState`'s region fields are not named `cradle`, `provinces` and `hinterland`, read `model/World.scala:73` and use its names.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/wake/RiverSitePowerSuite.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._

class RiverSitePowerSuite extends munit.FunSuite:
  import PowerFixture._
  import RiverFixture._
  import TargetsFixture.{after, answer, awaits, offered, pick, replayed,
    usableNow, withPawn}

  private val source = DecisionOptionRef.Site(ancientCity)
  private def use(ready: ReadyGame) = TargetsFixture.use(ready, river, source)
  private def rivers(ready: ReadyGame) = usableNow(ready).collect {
    case usable if usable.power.isInstanceOf[RiverSitePower] =>
      usable.power.id -> usable.ref }

  /** Starts Ancient City's River and answers the site question. */
  private def placedAt(ready: ReadyGame, at: SiteId) =
    val parked = use(ready).toOption.get
    assert(awaits(parked, RiverSitePower.siteDecisionId))
    (parked, answer(parked, actor, RiverSitePower.siteDecisionId,
      pick(DecisionOptionRef.Site(at))).toOption.get)

  test("every River is a registered phase power"):
    assertEquals(PhasePowerCatalog.default(catalog).powers.collect {
      case power: RiverSitePower => power.id.value }.toSet,
      Set("site.ancient-city.river", "site.headwaters.river",
        "site.riverbank.river", "site.tidal-marshes.river"))

  test("the actor's pawn starts at Ancient City, a River"):
    assertEquals(home(base), ancientCity)
    assert(RiverSitePower.isRiver(catalog, ancientCity))

  test("it is usable in the Wake at a River while another River is in play"):
    assertEquals(rivers(staged()), Vector(river.id -> source))

  test("it is not usable when no other River is in play"):
    val alone = staged(Vector.empty)
    assertEquals(rivers(alone), Vector.empty)
    assert(use(alone).isLeft)

  test("it is not usable outside the Wake"):
    Vector(Phase.Act, Phase.Rest).foreach { phase =>
      val acting = staged(phase = phase)
      assertEquals(rivers(acting), Vector.empty, phase.toString)
      assert(use(acting).isLeft, phase.toString)
    }

  test("it is not usable from a site that is not a River"):
    val ready = staged()
    val dry = ready.game.current.map.inPlay.find(site =>
      !RiverSitePower.isRiver(catalog, site)).get
    val away = withPawn(ready, actor, dry)
    assertEquals(rivers(away), Vector.empty)
    assert(use(away).isLeft)

  test("it offers every other River in play, in map order"):
    val ready = staged()
    val expected = ready.game.current.map.inPlay
      .filter(Set(riverbank, tidalMarshes).contains)
    assertEquals(expected.size, 2)
    val parked = use(ready).toOption.get
    assertEquals(offered(parked, actor), Some(expected.map(site =>
      "site" -> site.value)))

  test("it places the pawn at the chosen River without Travel or Supply"):
    val ready = staged()
    val (parked, done) = placedAt(ready, riverbank)
    val placed = after(done)
    assertEquals(player(placed).pawnSite, Some(riverbank))
    assertEquals(player(placed).board.supply, player(ready).board.supply)
    val moves = (parked.events ++ done.events).collect {
      case step: WalkerStepRecorded => step.ops }.flatten.collect {
      case move @ Move(Piece.Pawn(_), _, _, _) => move }
    assertEquals(moves, Vector(Move(Piece.Pawn(actor),
      PositionedLocation(Location.Site(ancientCity)),
      PositionedLocation(Location.Site(riverbank)))))
    assertEquals(replayed(ready, parked.events ++ done.events),
      Right(done.state))

  test("it is once per turn from each River"):
    val (_, done) = placedAt(staged(), riverbank)
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(ancientCity),
      river.id)
    assert(after(done).game.current.turn.usedPowers.contains(used))
    val back = withPawn(after(done), actor, ancientCity)
    assertEquals(use(back).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    // Riverbank's River is another source, so it is still usable.
    assertEquals(rivers(after(done)).map(_._2),
      Vector(DecisionOptionRef.Site(riverbank)))

  test("it writes where it placed the pawn"):
    val (parked, done) = placedAt(staged(), riverbank)
    assertEquals(NoteText.said(river, parked.events ++ done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} placed at ${riverbank.value}.",
        covers = false)))
    assertEquals(river.noteSource, Some("River"))
```

If `offered` projects a site option under a kind other than `"site"`, read `DecisionOptionRef.Site.kind` and use it.

- [ ] **Step 3: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.RiverSitePowerSuite"`
Expected: compilation fails, because `RiverSitePower` does not exist.

- [ ] **Step 4: Add `noteSource` to `NotingPower`**

In `gameplay/powerresolver/ContributingPower.scala`, trait `NotingPower`, after `def noteKeys`:

```scala
  /** The name this power's notes are written under, when it is not the name
    * of its source. A site power four sites share names itself, so its line
    * reads "River: ..." wherever it was used. */
  def noteSource: Option[String] = None
```

- [ ] **Step 5: Let a phase power come from a site**

In `gameplay/phases/PhasePowerProcedure.scala`, `sourceRef` gains a case before `case _ => None`:

```scala
    case RuleSourceRef.Site(id) =>
      Some(PowerSourceRef.Site(id) -> DecisionOptionRef.Site(id))
```

and `sourceOf` gains a case before `case other =>`:

```scala
    case DecisionOptionRef.Site(id) => Right(PowerSourceRef.Site(id))
```

`payable` already refuses a cost from any source that is not a card, so a site source stays free.

- [ ] **Step 6: Write the River**

Create `src/main/scala/oathdigital/gameplay/powers/wake/RiverSitePower.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.action.PawnMoves
import oathdigital.model._

/** The River site power (CR p. 31, NF p. 11), WAKE: "You may place your pawn
  * at another River. This is not a Travel action."
  *
  * It belongs to its site, so the player whose pawn is there may use it, once
  * per turn from each River; the engine enforces both. The placement is a
  * plain `Move`: no Supply is paid and no Travel window runs, so no Pass and
  * no Travel cost modifier applies. Its notes are written under "River",
  * since four sites share the power.
  */
final case class RiverSitePower(id: PowerId, site: SiteId,
    catalog: ExecutableCatalog) extends PhasePower:
  import RiverSitePower._

  def timing: PowerTiming = PowerTiming.Wake
  override def noteKeys: Vector[NoteKey] = Vector(placed)
  override def noteSource: Option[String] = Some(name)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = destinations(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    PawnMoves.siteChoice(siteDecisionId, player, destinations(ready),
      "River: choose the River to place your pawn at"),
    BuildOps((state, pending) => PawnMoves.chosenSite(pending, siteDecisionId)
      .flatMap(PawnMoves.relocate(state, player, _))),
    Note(id, PawnMoves.placedNote(placed, source, player)))))

  /** Every River in play but this one, in map order. */
  private def destinations(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(other =>
      other != site && isRiver(catalog, other))

object RiverSitePower:
  val name: String = "River"
  val siteDecisionId: String = "power.river.site"
  /** "{player} placed at {site}.", the power's own line. */
  val placed: NoteKey = PawnMoves.placedKey(NoteKey.Used)

  /** The reviewed River handlers, one power each. */
  val supported: Vector[PowerId] = Vector("site.ancient-city.river",
    "site.headwaters.river", "site.riverbank.river",
    "site.tidal-marshes.river").map(PowerId(_))

  def isRiver(catalog: ExecutableCatalog, site: SiteId): Boolean =
    catalog.site(site).exists(_.handlers.exists(_.endsWith(".river")))

  /** A River power for each reviewed handler `catalog` has. */
  def forCatalog(catalog: ExecutableCatalog): Vector[RiverSitePower] =
    supported.flatMap(id => catalog.siteWithHandler(id).map(definition =>
      RiverSitePower(id, definition.id, catalog)))
```

`PowerId(...)` may be a smart constructor; if `PowerId("...")` does not compile, use what `TravelSitePowers.supported` uses.

- [ ] **Step 7: Register it**

In `gameplay/powers/PhasePowerCatalog.scala`, add the import `oathdigital.gameplay.powers.wake.RiverSitePower` beside `MarbleFountains` (one import line: `import oathdigital.gameplay.powers.wake.{MarbleFountains, RiverSitePower}`), and append to the sum in `default`:

```scala
      BannerFacePowers.phasePowers ++
      RiverSitePower.forCatalog(catalog))
```

- [ ] **Step 8: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.RiverSitePowerSuite"`
Expected: all 10 tests pass.

- [ ] **Step 9: Run the full server suite**

Run: `./sbtw test`
Expected: green, with 10 more tests than the baseline. A suite that counts implemented powers or phase powers may need its expectation widened by the four River ids; widen it only if the new count is exactly the River ids, and say so in the commit message.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/wake/RiverSitePower.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/wake/RiverFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/wake/RiverSitePowerSuite.scala
git commit -m "feat(powers): the River places the pawn at another River in the Wake"
```

End the message body with the trailer.

---

### Task 2: The River's line, and every site power's printed text

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` (method `line`, near line 32)
- Create: `src/main/scala/oathdigital/application/SitePowerText.scala`
- Modify: `src/main/scala/oathdigital/application/PhasePowerProjector.scala` (method `printed`)
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` (method `sitePower`, near line 371)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Test: `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala`
- Test: `src/test/scala/oathdigital/application/GamePresentationProjectorPrintedFacesSuite.scala`

**Interfaces:**
- Consumes: `NotingPower.noteSource`, `RiverSitePower`, `RiverFixture` (Task 1).
- Produces:
  - `NoteWordings(templates, sources: Map[PowerId, String] = Map.empty)`, with `def source(power: PowerId): Option[String]` and `NoteWordings.of(power, keys, source: Option[String] = None)`.
  - `private[application] object SitePowerText`:
    - `final case class Printed(label: String, text: String)`
    - `def kindOf(handler: String): String`
    - `def of(kind: String): Option[Printed]`

- [ ] **Step 1: Write the failing log tests**

Append to `GameLogPowerLinesSuite` (it already imports `oathdigital.model._`, `LogScripts._` and `PowerNoted`):

```scala
  test("a power that names its notes' source reads under that name, not its site"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val site = SiteId("site:ancient-city")
    val noted = inserted(steps, take(steps), PowerNoted(power, took(
      PowerSourceRef.Site(site), NoteArg.Player(script.actor),
      NoteArg.Amount(1, NoteUnit.Favor)), covers = false))
    val named = new GameLogFormatter(catalog, presentation,
      NoteWordings.default(catalog) ++ NoteWordings.of(power, Vector(took),
        Some("River")))
    assert(texts(named.format(noted, None).filter(_.depth == 1)).contains(
      s"River: ${name(script.actor)} took 1 favor."))
    // Without a name the site is the source, as Narrow Pass's line has it.
    assert(lines(noted).contains(
      s"${presentation.siteLabel(site)}: ${name(script.actor)} took 1 favor."),
      lines(noted))

  test("the River's notes are written under River; Narrow Pass keeps its site"):
    val wordings = NoteWordings.default(catalog)
    assertEquals(wordings.source(PowerId("site.riverbank.river")), Some("River"))
    assertEquals(wordings.source(PowerId("site.narrow-pass.pass")), None)
```

- [ ] **Step 2: Write the failing projection tests**

Append to `PhasePowerProjectorSuite`:

```scala
  test("a site's phase power is projected under its power's name and printed text"):
    import oathdigital.gameplay.powers.wake.RiverFixture
    val ready = RiverFixture.staged()
    val actor = ready.game.current.turn.activePlayer
    val projected = projector.project("river", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(projected.phasePowers.filter(_.powerId ==
      "site.ancient-city.river").map(p =>
      (p.source.kind, p.source.id, p.name, p.rulesText)), Vector(("site",
      "site:ancient-city", "River",
      "WAKE: You may place your pawn at another River. This is not a Travel action.")))
    assert(projected.legalControls.contains(
      "usePower:site.ancient-city.river:site:ancient-city"))
```

Append to `GamePresentationProjectorPrintedFacesSuite`:

```scala
  test("a site's powers show their printed text"):
    val sites = projector.readyWorld(initialReady, None).flatMap(_.sites)
    def powers(id: String) = sites.find(_.siteId == id).get.powers
      .map(p => (p.kind, p.label, p.description))
    assertEquals(powers("site:ancient-city"), Vector(
      ("enduring", "Enduring", Some("Cards at this site are not discarded in " +
        "the Chronicle Phase during the Shape Empire step.")),
      ("river", "River", Some("WAKE: You may place your pawn at another " +
        "River. This is not a Travel action."))))
    assertEquals(powers("site:deep-woods"), Vector(("homeland-beast",
      "Homeland", Some("There is a Homeland of each suit. When playing a card " +
        "of its Homeland suit to this site, you may discard a card from the " +
        "site first (even one of matching suit)."))))
```

If `readyWorld` is not public, or the region projection's site list is not named `sites`, read `GamePresentationProjector.scala:77-100` and `WorldProjectionDtos.scala:47-67` and reach the site projections the same way the existing projector tests do.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite oathdigital.application.PhasePowerProjectorSuite oathdigital.application.GamePresentationProjectorPrintedFacesSuite"`
Expected: compilation fails on `NoteWordings.of(..., Some("River"))` and `wordings.source`.

- [ ] **Step 4: Carry `noteSource` in `NoteWordings`**

Replace the body of `application/gamelog/NoteWordings.scala` from the class down with:

```scala
/** Every power's note templates, by power and key (power log lines design,
  * "Wording"), and the name a power's notes are written under when it names
  * one. A note whose power or key is missing here renders nothing. */
private[application] final case class NoteWordings(
    templates: Map[(PowerId, String), Vector[NotePart]],
    sources: Map[PowerId, String] = Map.empty):
  def template(power: PowerId, key: String): Option[Vector[NotePart]] =
    templates.get((power, key))
  /** The name `power`'s notes are written under in place of their source. */
  def source(power: PowerId): Option[String] = sources.get(power)
  /** `other`'s entries win where both have one. */
  def ++(other: NoteWordings): NoteWordings =
    NoteWordings(templates ++ other.templates, sources ++ other.sources)

private[application] object NoteWordings:
  def of(power: PowerId, keys: Vector[NoteKey],
      source: Option[String] = None): NoteWordings =
    NoteWordings(keys.map(key => (power, key.name) -> key.template).toMap,
      source.map(power -> _).toMap)

  /** The walker powers', the phase powers' and the game rules' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      of(power.id, power.noteKeys, power.noteSource)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, power.noteKeys, power.noteSource)) ++
      RuleNotes.all.map((id, keys) => of(id, keys)))
      .foldLeft(NoteWordings(Map.empty))(_ ++ _)
```

- [ ] **Step 5: Write a named source in `PowerLines`**

In `application/gamelog/PowerLines.scala`, method `line`, replace

```scala
        words.source(note.source, seen, viewer) ++
```

with

```scala
        wordings.source(power).fold(words.source(note.source, seen, viewer))(
          named => Vector(Text(named))) ++
```

- [ ] **Step 6: Write the printed site powers**

Create `src/main/scala/oathdigital/application/SitePowerText.scala`:

```scala
package oathdigital.application

/** Each site power's name and printed text (CR p. 31, NF p. 11), by the kind
  * its handler ends in: `site.riverbank.river` is a River. The six Homelands,
  * `homeland-<suit>`, share one text. */
private[application] object SitePowerText:
  final case class Printed(label: String, text: String)

  def kindOf(handler: String): String =
    handler.split('.').lastOption.getOrElse(handler)

  def of(kind: String): Option[Printed] =
    if kind.startsWith("homeland-") then Some(homeland) else printed.get(kind)

  private val homeland = Printed("Homeland", "There is a Homeland of each " +
    "suit. When playing a card of its Homeland suit to this site, you may " +
    "discard a card from the site first (even one of matching suit).")

  private val printed: Map[String, Printed] = Map(
    "plains" -> Printed("Plains", "This site has no power."),
    "coast" -> Printed("Coast", "Traveling from here to a Coast or Island " +
      "costs only 1 Supply and ignores other Travel modifiers (Mountain, " +
      "Pass, etc.)."),
    "island" -> Printed("Island", "This site has the Coast power. Traveling " +
      "to it costs 2 Supply more than its normal Travel cost unless you're " +
      "traveling from a Coast or Island."),
    "river" -> Printed("River", "WAKE: You may place your pawn at another " +
      "River. This is not a Travel action."),
    "mountain" -> Printed("Mountain", "Traveling to this site costs one more " +
      "Supply than its normal Travel cost. Do not add this Supply cost if " +
      "this site also has the Coast power and you're traveling to it from a " +
      "Coast."),
    "pass" -> Printed("Pass", "If your pawn is outside this region, you " +
      "cannot travel to other sites in this region or target other sites in " +
      "this region in campaigns, unless you have the consent of the Pass's " +
      "ruler. You can ignore this power if you rule the Pass. The bandits " +
      "never consent."),
    "enduring" -> Printed("Enduring", "Cards at this site are not discarded " +
      "in the Chronicle Phase during the Shape Empire step."))
```

- [ ] **Step 7: Read the table in both projectors**

In `GamePresentationProjector.scala`, replace the body of `sitePower` with:

```scala
  private def sitePower(handler: String): SitePowerProjection =
    val kind = SitePowerText.kindOf(handler)
    SitePowerText.of(kind).fold(SitePowerProjection(kind, safeLabel(kind), None))(
      printed => SitePowerProjection(kind, printed.label, Some(printed.text)))
```

In `PhasePowerProjector.scala`, `printed` gains a case before `case _ => None`:

```scala
    case PowerSourceRef.Site(_) =>
      SitePowerText.of(SitePowerText.kindOf(power.value)).map(site =>
        site.label -> oathdigital.catalog.CatalogPower(power,
          persistent = false, site.text))
```

- [ ] **Step 8: Run the three suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite oathdigital.application.PhasePowerProjectorSuite oathdigital.application.GamePresentationProjectorPrintedFacesSuite"`
Expected: all pass.

- [ ] **Step 9: Run the full server and frontend suites**

Run: `./sbtw "test" "frontend/test"`
Expected: green. The frontend's `SiteFaceSuite` builds its own site power fixtures, so it does not change. No golden log changes.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/NoteWordings.scala \
  src/main/scala/oathdigital/application/gamelog/PowerLines.scala \
  src/main/scala/oathdigital/application/SitePowerText.scala \
  src/main/scala/oathdigital/application/PhasePowerProjector.scala \
  src/main/scala/oathdigital/application/GamePresentationProjector.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala \
  src/test/scala/oathdigital/application/GamePresentationProjectorPrintedFacesSuite.scala
git commit -m "feat(log): the River writes under its name; site powers show their printed text"
```

---

### Checkpoint A

Dispatch one reviewer subagent (Sonnet, no higher) over `git diff <base>..HEAD` for Tasks 1-2, with the spec. Ask it to check:

- The River follows the spec's rulings: access, destinations, once per turn per source, no Travel window, no Supply.
- `RiverSitePower` rebuilds the same tree on a resume (`PhasePowerProcedure.rebuild`), since the site question parks.
- Only the River's notes change their source name; Narrow Pass and the Homeland rule do not.
- No `gameplay` file imports `application`, and the printed texts match CR p. 31 word for word.

Fix what it finds, re-run the touched suites, and commit the fixes before Task 3.

---

### Task 3: Homeland follows its printed text

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` (`legalChoices` near line 52; `validateSiteReplacement` near line 353)
- Modify: `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala` (class doc comment)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala` (`homelandNote` doc comment near line 240)
- Modify: `src/test/scala/oathdigital/gameplay/PlacementFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala` (`fullHomeland`, near line 101)

**Interfaces:**
- Produces:
  - `CardPlay.homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit]`.
  - Test: `PlacementFixture.staged(card, site, at: Option[SiteId] = None)`, which moves the actor's pawn to `at` when given, and `PlacementFixture.homeland: (SiteId, Suit)`, the first in-play Homeland of the first game.

Context you need:

- Today `validateSiteReplacement` allows a site discard in two cases. One is under `PlacementRules.siteDiscardFirst`, which People's Favor's Mob face sets. The other is only at a *full* site holding an *edifice* of the played card's suit.
- The printed Homeland has neither condition. After this task, a play to the Homeland of the card's suit behaves exactly as under `siteDiscardFirst`: the discard is optional with room and required when full.
- The Homeland is the site's `site.<id>.homeland-<suit>` handler.
- The existing Homeland tests stage a Hall of Ministers (E16) at the actor's pawn site, Ancient City, which has no Homeland handler. After this task that site is no Homeland, so those tests move to a real one.

- [ ] **Step 1: Let the fixture stage at another site**

In `PlacementFixture.scala`, add the import `oathdigital.gameplay.actions.CardPlay` (the file already imports `PlacementRules` from that package: make it `import oathdigital.gameplay.actions.{CardPlay, PlacementRules}`).

Give `staged` a third parameter and move the pawn with it. Replace its signature and the lines that pick the site and build the state:

```scala
  /** `card` in the actor's hand and the site at `at`, or the actor's pawn
    * site, holding exactly `site`; the actor's pawn moves to `at`. Every card
    * that leaves a place goes to the matching deck, so the inventory stays
    * whole.
    */
  def staged(card: DenizenId, site: Vector[SiteDenizenState],
      at: Option[SiteId] = None): (ReadyGame, PlayerId, SiteId) =
    val base = initialReady
    val current = base.game.current
    val actor = actorOf(base)
    val siteId = at.getOrElse(actor.pawnSite.get)
```

and add `players` to the `copy` inside `base.updateCurrent(_.copy(`:

```scala
      players = current.players.map(p =>
        if p.player == actor.player then p.copy(pawnSite = Some(siteId)) else p),
```

Add after `staged`:

```scala
  /** The first in-play Homeland of the first game, and its suit. */
  lazy val homeland: (SiteId, Suit) = initialReady.game.current.map.inPlay
    .flatMap(site => CardPlay.homelandSuit(catalog, site).map(site -> _)).head
```

- [ ] **Step 2: Write the failing rule suite**

Create `src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The Homeland site power (CR p. 31): "When playing a card of its Homeland
  * suit to this site, you may discard a card from the site first (even one of
  * matching suit)." The site's handler names the suit, and the discard is
  * offered whether or not the site is full.
  */
class HomelandRuleSuite extends munit.FunSuite:
  import PlacementFixture._

  private val (homeSite, homeSuit) = homeland
  private val capacity = catalog.site(homeSite).get.capacity

  private def ofSuit(matching: Boolean): Vector[DenizenId] =
    plain(initialReady).filter(id =>
      catalog.suitOf(id).contains(homeSuit) == matching)

  private def siteChoice(ready: ReadyGame, actor: PlayerId, card: DenizenId) =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** `card` played at the Homeland holding `cards`, which the actor rules. */
  private def at(card: DenizenId, cards: Vector[DenizenId]) =
    val (ready, actor, site) = staged(card, cards.map(denizen(_)), Some(homeSite))
    (ruledByActor(ready, site), actor)

  test("the Homeland suit is read from the site's handler"):
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:deep-woods")),
      Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:ancient-city")), None)

  test("a matching Homeland with room offers an optional discard"):
    val card = ofSuit(true).head
    val kept = ofSuit(false).head
    assert(capacity > 1, s"the Homeland must have room, capacity $capacity")
    val (ready, actor) = at(card, Vector(kept))
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements, Vector[CardId](kept))
    assert(choice.replacementOptional)

  test("a card of another suit is offered no discard at a Homeland with room"):
    val Vector(card, kept) = ofSuit(false).take(2)
    val (ready, actor) = at(card, Vector(kept))
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements, Vector.empty)
    assert(!choice.replacementOptional)

  test("a full matching Homeland requires a discard, even of a matching card"):
    val card = ofSuit(true).head
    val fillers = ofSuit(true).tail.take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor) = at(card, fillers)
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements.toSet, fillers.toSet[CardId])
    assert(!choice.replacementOptional)

  test("a full Homeland refuses a card of another suit"):
    val card = ofSuit(false).head
    val fillers = ofSuit(false).tail.take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor) = at(card, fillers)
    assertEquals(siteChoice(ready, actor, card), None)

  test("an edifice of the card's suit does not make a site a Homeland"):
    val hall = EdificeId("E16")
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(catalog.suitOf(_).contains(hallSuit)).get
    val (_, _, pawnSite) = staged(card, Vector.empty)
    assertEquals(CardPlay.homelandSuit(catalog, pawnSite), None)
    val fillers = cards.filter(_ != card)
      .take(catalog.site(pawnSite).get.capacity - 1)
    val (built, actor, site) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, EdificeSide.Intact, Tokens.empty))
    assertEquals(siteChoice(ruledByActor(built, site), actor, card), None)
```

- [ ] **Step 3: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.HomelandRuleSuite"`
Expected: compilation fails, because `CardPlay.homelandSuit` does not exist.

- [ ] **Step 4: Read the Homeland from the site, and treat it as a permission**

In `CardPlay.scala`, add after `legalChoices`:

```scala
  /** The suit whose Homeland `site` is, from its `site.<id>.homeland-<suit>`
    * handler (CR p. 31). */
  def homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit] =
    catalog.site(site).toVector.flatMap(_.handlers).flatMap(handler =>
      handler.split('.').lastOption.filter(_.startsWith("homeland-"))
        .flatMap(kind => Suit.fromKey(kind.stripPrefix("homeland-"))))
      .headOption

  /** A play of a `suit` card to `site` may discard a card there first: under
    * a power's permission, or at the Homeland of `suit`. */
  private def discardFirst(catalog: ExecutableCatalog, site: SiteId,
      suit: Suit, rules: PlacementRules): Boolean =
    rules.siteDiscardFirst || homelandSuit(catalog, site).contains(suit)
```

In `legalChoices`, replace

```scala
      // A play to a site may be preceded by a discard even where it has room.
      val optional = direct && rules.siteDiscardFirst &&
        placement.isInstanceOf[SearchPlacement.Site]
```

with

```scala
      // A play to a site may be preceded by a discard even where it has room,
      // under a power's permission or at the Homeland of the card's suit.
      val optional = direct && (placement match
        case _: SearchPlacement.Site => player.flatMap(_.pawnSite).exists(site =>
          catalog.suitOf(card).exists(discardFirst(catalog, site, _, rules)))
        case _ => false)
```

In `validateSiteReplacement`, replace everything from `if rules.siteDiscardFirst then replace match` to the end of the method with:

```scala
    if discardFirst(catalog, siteId, suit, rules) then replace match
      // At any capacity: the discard is optional with room and required
      // without, and it may name any card of the site's card list.
      // `DiscardRestrictions` decide what may actually be discarded: a locked
      // card, an intact edifice and an active modifier may not.
      case None if full => Left(InvalidSearchPlacement(
        "a full site requires a site-card discard"))
      case None => Right(None)
      case Some(id) => site.denizens.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement card is not at the site"))
        .map(Some(_))
    else if full then Left(InvalidSearchPlacement(
      "a full site takes a card only at the Homeland of its suit"))
    else if replace.nonEmpty then Left(InvalidSearchPlacement(
      "a site discard needs the Homeland of the card's suit or a power's permission"))
    else Right(None)
```

If `catalog` becomes unused in a helper the old branch used, the compiler says so; remove only what it names.

- [ ] **Step 5: Update the two doc comments**

In `PlacementRules.scala`, the `siteDiscardFirst` and `siteDiscardNote` bullets become:

```scala
  *  - `siteDiscardFirst` lets a play to a site first discard one card of the
  *    site's card list, at any capacity. It is optional with room and required
  *    without, and it lifts the rule that a full site accepts a card only at
  *    the Homeland of its suit. The Homeland of the card's suit gives the same
  *    permission without it.
  *  - `siteDiscardNote` is the line of the power that permits the discard.
  *    Card play writes it after the discard answer at a site. Without the
  *    permission, the discard is asked only at the Homeland of the card's
  *    suit, and card play writes the Homeland rule's line there instead.
```

In `CardPlayProcedure.scala`, the `homelandNote` comment becomes:

```scala
  /** The Homeland rule's line, "{site}: {Red} may discard a card at their
    * site first.": without a power's permission, only the Homeland of the
    * card's suit asks for a discard at a site. */
```

- [ ] **Step 6: Move the line and restriction tests to a real Homeland**

Replace `HomelandLineSuite`'s class comment and `fullHomeland` with:

```scala
/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * the Homeland of the card's suit asks for a discard, and the Homeland says
  * why once the discard is answered.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture._

  private val none = WalkerPowers.empty
  private val (homeSite, homeSuit) = homeland

  /** The Homeland, full or with room, ruled by the actor, and a card in hand
    * whose suit is the Homeland's or not. */
  private def atHomeland(matching: Boolean, full: Boolean = true)
      : (ReadyGame, PlayerId, DenizenId) =
    val cards = plain(initialReady)
    val card = cards.find(id =>
      catalog.suitOf(id).contains(homeSuit) == matching).get
    val capacity = catalog.site(homeSite).get.capacity
    val fillers = cards.filter(_ != card).take(if full then capacity else 1)
    val (ready, actor, site) = staged(card, fillers.map(denizen(_)),
      Some(homeSite))
    (ruledByActor(ready, site), actor, card)
```

In its two tests, replace `fullHomeland(matching = true)` with `atHomeland(matching = true)` and `fullHomeland(matching = false)` with `atHomeland(matching = false)`. Rename the first test to `"a play to a full matching Homeland writes the Homeland's line after the discard"` (unchanged text) and add:

```scala
  test("a play with room at a matching Homeland writes the line after the " +
      "discard it chose"):
    val (ready, actor, card) = atHomeland(matching = true, full = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, _) = answer(ready, tree,
      park(ready, tree, none), none, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor): @unchecked
    val discarded = options(ready, tree, pending, none).collectFirst {
      case ref: DecisionOptionRef.Denizen => ref }.get
    val WalkerOutcome.Finished(_, events) = answer(ready, tree, pending, none,
      decisionId(card, "replace"), discarded, actor): @unchecked
    assertEquals(said(events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))
```

Remove the now-unused `hall` value from `HomelandLineSuite`.

In `DiscardRestrictionsSuite`, replace `fullHomeland` with:

```scala
  /** The full Homeland of the played card's suit, holding the Hall, so the
    * played card may replace one of its cards, and the actor rules it.
    */
  private def fullHomeland(side: EdificeSide)
      : (ReadyGame, DenizenId, Vector[DenizenId]) =
    val (homeSite, homeSuit) = homeland
    val cards = plain(initialReady)
    val card = cards.find(catalog.suitOf(_).contains(homeSuit)).get
    val capacity = catalog.site(homeSite).get.capacity
    val fillers = cards.filter(_ != card).take(capacity - 1)
    val (ready, _, site) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, side, Tokens.empty), Some(homeSite))
    (ruledByActor(ready, site), card, fillers)
```

The suite's `siteChoice` uses a suite-level `actor`; `staged` keeps the same actor, so it still applies.

- [ ] **Step 7: Run the placement suites**

Run: `./sbtw "testOnly oathdigital.gameplay.HomelandRuleSuite oathdigital.gameplay.HomelandLineSuite oathdigital.gameplay.DiscardRestrictionsSuite oathdigital.gameplay.SiteDiscardFirstSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite oathdigital.gameplay.powers.cardplay.SacredGroundSuite"`
Expected: all pass. If a suite outside this task's files fails because it staged a Homeland by edifice, report it before changing it.

- [ ] **Step 8: Run the full server suite**

Run: `./sbtw test`
Expected: green.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlay.scala \
  src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala \
  src/test/scala/oathdigital/gameplay/PlacementFixture.scala \
  src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala \
  src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala \
  src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala
git commit -m "fix(cardplay): Homeland offers its discard at any capacity, read from the site"
```

---

### Task 4: Record the phase

**Files:**
- Modify: `docs/ROADMAP.md` (Phase - Cleanup tasks)
- Modify: `docs/rules/implementation-traceability.md` (Wake row near line 85, Travel row near line 96)
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md` (near lines 151-154, 338 and 420-422)
- Modify: `docs/superpowers/specs/2026-09-27-site-powers-design.md` (status line)

- [ ] **Step 1: Roadmap**

Replace the unticked "Implement every site power" item with:

```markdown
- [x] **Implement every site power.** The River is a Wake power of its site
  that places the pawn at another River, and Homeland offers its discard at
  any capacity, read from the site. Plains has no power; Coast, Island,
  Mountain and Pass were already built. See the
  [site powers design](superpowers/specs/2026-09-27-site-powers-design.md).
- [ ] **Enduring (Ancient City) waits for a Chronicle Phase.** Its cards are
  not discarded in the Chronicle Phase's Shape Empire step, which the engine
  does not have yet. The Pass's consent waits for the Consent system phase.
```

Check the link resolves from `docs/ROADMAP.md`.

- [ ] **Step 2: Traceability**

In the Wake row's last column, remove "River movement, " from "Generic powers, River movement, Imperials, and altered Foundations remain deferred." In the Travel row's last column, remove "River, " from "River, consent workflow, ...". In the Wake row's implementation column, append: "The River is a Wake phase power of its site (`gameplay/powers/wake/RiverSitePower.scala`)."

- [ ] **Step 3: Power log lines spec**

- Lines 151-154: "`CardPlay` lets a player discard a card at a full Homeland whose edifice matches the played card's suit" becomes "`CardPlay` lets a player discard a card at the Homeland of the played card's suit".
- Line 338's row label: "Homeland rule, at a full Homeland matching the played card's suit, after the discard answer" becomes "Homeland rule, at the Homeland of the played card's suit, after the discard answer".
- Lines 420-422: "A card played to a full matching Homeland reads the Homeland line; a card played to a full Homeland of another suit is refused as today and reads nothing." becomes "A card played to the Homeland of its suit reads the Homeland line after its discard; a card played to a full Homeland of another suit is refused and reads nothing."

- [ ] **Step 4: Site powers spec status**

Change "**Status:** designed 2026-09-27." to "**Status:** designed and built 2026-09-27."

- [ ] **Step 5: Gates**

Run: `./sbtw "test" "frontend/test"`
Expected: green; the server count is the baseline plus 21 (10 River, 4 log and projection, 6 Homeland rule, 1 Homeland line).

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

Confirm no file under `src/test/resources/gamelog/` changed: `git status --short src/test/resources/gamelog/` prints nothing.

- [ ] **Step 6: Commit**

```bash
git add docs/ROADMAP.md docs/rules/implementation-traceability.md \
  docs/superpowers/specs/2026-09-26-power-log-lines-design.md \
  docs/superpowers/specs/2026-09-27-site-powers-design.md
git commit -m "docs: site powers built; Enduring waits for a Chronicle Phase"
```

---

### Checkpoint B

Dispatch one reviewer subagent (Sonnet, no higher) over the whole branch diff, with the spec. Ask it to check:

- Every row of the spec's Rulings table marked "built" or "corrected" has code and a test, and every Testing bullet has a test.
- The Homeland change does not widen People's Favor or any other `siteDiscardFirst` path, and a full site of another suit is still refused.
- The docs say what the code does.

Fix what it finds, re-run the gates, and commit before finishing the branch.
