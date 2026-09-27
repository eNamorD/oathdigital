# Catalog Batch 2, Slice 3a (Actions on Yourself, Part 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Tutor (69), Spirit Snare (33), Wizard School (34), Shifting Map (R17), Demon Tail (R46) and Clay Rattle (R47), with the `Shuffle` operation the spec calls N4 and the `NoteArg.Pile` note argument.

**Architecture:**

- Every card is an ACTION phase power built on `PaidAction`, the shape Elders, Wayside Inn and Murky Fountain use. The engine pays the printed cost onto the card and reads "payable" as usability, so each card writes only its `build`. They register through one new group object, `SelfActionPowers`, as the earlier action slices do.
- Tutor copies Elders. Shifting Map and Demon Tail copy Wayside Inn. Wizard School gains a secret like Elders, then ends the Act phase with `EnterPhase(Phase.Rest)`, as Murky Fountain does. Spirit Snare reuses `FavorBankChoice`, the bank take of Vow of Obedience's REST, whose visibility widens from `cardplay` to `powers`.
- `Shuffle(pile, order)` reorders the world deck or one region's discard pile. A tree declares it with no order. The walker fills the order from its random source, the same `WalkerDice` port that rolls automatic dice, and records the filled operation. Replay applies recorded operations only, so it reproduces the order without asking again. The filling code lives in a new `WalkerShuffles` file, because `ProcedureWalker.scala` is 743 lines against the 800-line bound.
- The pile type is the existing `SearchSource` (`WorldDeck` or `RegionalDiscard(region)`), which already has a wire codec. `NoteArg.Pile` carries one and renders "world deck" or "Cradle discard pile".
- Clay Rattle asks which pile with four `Button` options, then shuffles the chosen one through a `Branch` that reads the answer.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. Log lines render server-side into `LogSpan.Text`, so there is no frontend change and Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md` ("N4. Shuffle", "Slicing", "Log lines"), with the per-card rulings in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 3: actions on yourself"). Read both before starting.

**Scope ruling:** the spec's slice 3 holds nine cards and three engine additions, one of them a frontend overlay. It runs as three plans. This plan is 3a. Plan 3b holds Scryer, Oracular Pig and N5 (`Inspect`, the card-list overlay, `NoteArg.Cards` above five cards). Plan 3c holds Oracle and N6. `NoteArg.Pile` is part of N5 in the spec, but Clay Rattle needs it, so it lands here.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `ProcedureWalker.scala` is at 743 lines; this plan adds 4.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines are exactly the spec's (`Log lines`, "Phase powers"), where `{Red}` is the acting player:

  | Card | Key | Line | Covers |
  |---|---|---|---|
  | Tutor | `used` | Tutor: {Red} gained {1} secret. | yes |
  | Spirit Snare | `used` | Spirit Snare: {Red} took {n} favor from {the Order bank}. | yes |
  | Spirit Snare, every bank empty | `used.empty` | Spirit Snare: Every favor bank was empty. | no |
  | Wizard School | `used` | Wizard School: {Red} gained {1} secret. | yes |
  | Wizard School | `ended` | Wizard School: {Red}'s Act phase ended. | no |
  | Shifting Map | `used` | Shifting Map: {Red} gained {n} Supply. | no |
  | Demon Tail | `used` | Demon Tail: {Red} gained {n} Supply. | no |
  | Clay Rattle | `used` | Clay Rattle: {Red} shuffled the {Cradle discard pile}. | no |

  Amounts are what happened, so a Supply gain the track clamps reads the clamped amount, and a gain of zero writes no line, as Wayside Inn does.
- Card ids: Tutor `DenizenId("69")`, Spirit Snare `DenizenId("33")`, Wizard School `DenizenId("34")`, Shifting Map `RelicId("R17")`, Demon Tail `RelicId("R46")`, Clay Rattle `RelicId("R47")`. Tutor is catalogued adviser-only and Wizard School site-only; card placement already enforces both, so no power code reads them.
- Storage order: the world deck is stored top first; a discard pile is stored top last (`Search.draw`). `Shuffle.order` is the pile's new contents in stored order.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/scala/oathdigital/gameplay/powers/action/Tutor.scala` | Tutor. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/ShiftingMap.scala` | Shifting Map. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/DemonTail.scala` | Demon Tail. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/WizardSchool.scala` | Wizard School. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/SpiritSnare.scala` | Spirit Snare. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/ClayRattle.scala` | Clay Rattle. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` | Registers this slice's phase powers. |
| Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` | Adds the group. |
| Modify `src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala` | Visible to all of `powers`. |
| Modify `src/main/scala/oathdigital/model/ActionValues.scala` | `SearchSource.name`. |
| Modify `src/main/scala/oathdigital/model/Cards.scala` | `CardZones.pile` and `CardZones.withPile`. |
| Modify `src/main/scala/oathdigital/model/CoreOperations.scala` | The `Shuffle` operation. |
| Create `src/main/scala/oathdigital/gameplay/operations/PileOperations.scala` | Applies a `Shuffle`. |
| Modify `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` | Dispatches `Shuffle`. |
| Modify `src/main/scala/oathdigital/gameplay/walker/WalkerDice.scala` | `shuffle`, and the placeholder's identity order. |
| Create `src/main/scala/oathdigital/gameplay/walker/WalkerShuffles.scala` | Fills a declared `Shuffle`'s order. |
| Modify `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` | Runs a declared `Shuffle` through `WalkerShuffles`. |
| Modify `src/main/scala/oathdigital/application/GameRandomPorts.scala` | The production shuffle. |
| Modify `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` | Encodes and decodes `Shuffle`. |
| Modify `src/main/scala/oathdigital/model/PowerNotes.scala` | `NoteArg.Pile`. |
| Modify `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` | Encodes and decodes `NoteArg.Pile`. |
| Modify `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` | Renders `NoteArg.Pile`. |
| Modify `src/test/scala/oathdigital/gameplay/powers/NoteText.scala` | Reads `NoteArg.Pile` plainly. |
| Create one suite per card in `src/test/scala/oathdigital/gameplay/powers/action/` | One per card. |
| Create `src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala` | The `Shuffle` write. |
| Modify `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala` | `Shuffle` and `NoteArg.Pile` round trips. |
| Modify `src/test/scala/oathdigital/application/WalkerDiceAdapterSuite.scala` | The production shuffle. |
| Modify `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` | The pile rendering. |
| Modify `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala` | Pins the six cards. |
| Modify `docs/ROADMAP.md` | Records the slice. |

---

### Task 1: Tutor, Shifting Map, Demon Tail and Wizard School

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Tutor.scala`, `ShiftingMap.scala`, `DemonTail.scala`, `WizardSchool.scala`, `SelfActionPowers.scala` (all in `gameplay/powers/action/`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/TutorSuite.scala`, `ShiftingMapSuite.scala`, `DemonTailSuite.scala`, `WizardSchoolSuite.scala`

**Interfaces:**
- Consumes: `PaidAction(idValue: String, cost: Cost)`, `NoteSupport.gainedKey(name)`, `NoteSupport.gainNote(key, source, player, unit, read)(states)`, `NoteSupport.secrets`, `NoteSupport.supply`, `Gain.Secrets(player, amount)`, `GainSupply(player, amount)`, `EnterPhase(Phase.Rest)`.
- Consumes (tests): `PaidActionHarness` (`rules`, `use`, `ready`, `act`, `replayed`, `wireRoundTrips`, `usableIds`, `tokensOn`, `secrets`), `PowerFixture` (`base`, `actor`, `player`, `withBoard`, `atHome`, `asAdviser`, `withRelic`), `ParkedDecisionAssertions.assertResumed`, `NoteText.said`.
- Produces: `Tutor`, `ShiftingMap`, `DemonTail`, `WizardSchool` (case objects with `id`), `SelfActionPowers.powers: Vector[PhasePower]`. Tasks 2 and 4 append `SpiritSnare` and `ClayRattle` to `SelfActionPowers.powers`.

- [ ] **Step 1: Write the four failing suites**

`TutorSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TutorSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val tutor = DenizenId("69")
  private val source = DecisionOptionRef.Denizen(tutor)

  /** Tutor is adviser-only, so the actor holds it faceup. */
  private def staged(favor: Int = 2, faceUp: Int = 1): ReadyGame =
    act(withBoard(asAdviser(base, tutor))(
      _.copy(favor = favor, faceUpSecrets = faceUp)))
  private def tokens(ready: ReadyGame): Tokens =
    player(ready).advisers.collectFirst {
      case card: DenizenState if card.id == tutor => card.tokens }.get

  test("Tutor is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(Tutor.id).isDefined)

  test("it places a favor and a secret on its card and gains a secret"):
    val ready0 = staged()
    val done = use(rules(), ready0, Tutor.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokens(end), Tokens(1, 1))
    assertEquals(player(end).board.favor, 1)
    // One secret placed on the card, one gained from the shared bank.
    assertEquals(secrets(end), secrets(ready0))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("it is unusable without a favor or without a faceup secret"):
    Vector(staged(favor = 0), staged(faceUp = 0)).foreach { broke =>
      assert(!usableIds(broke).contains(Tutor.id))
      assert(use(rules(), broke, Tutor.id, source).isLeft)
    }

  test("it writes its gain as its own line, covering the generic one"):
    val done = use(rules(), staged(), Tutor.id, source).toOption.get
    assertEquals(NoteText.said(Tutor, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
```

`ShiftingMapSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class ShiftingMapSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val map = RelicId("R17")
  private val source = DecisionOptionRef.Relic(map)

  private def staged(supply: Int = 2, faceUp: Int = 1): ReadyGame =
    act(withBoard(withRelic(base, map))(
      _.copy(supply = SupplyTrack(supply), faceUpSecrets = faceUp)))

  test("Shifting Map is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(ShiftingMap.id).isDefined)

  test("it places a secret on the relic and gains 1 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, ShiftingMap.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, map), Tokens(0, 1))
    assertEquals(player(end).board.supply, SupplyTrack(3))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("at the track maximum the cost is paid and nothing is gained"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum),
      ShiftingMap.id, source).toOption.get
    assertEquals(player(ready(done.state)).board.supply, SupplyTrack.full)
    assertEquals(tokensOn(ready(done.state), map), Tokens(0, 1))
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector.empty)

  test("it is unusable without a faceup secret"):
    val broke = staged(faceUp = 0)
    assert(!usableIds(broke).contains(ShiftingMap.id))
    assert(use(rules(), broke, ShiftingMap.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), ShiftingMap.id, source).toOption.get
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))
```

`DemonTailSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DemonTailSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val tail = RelicId("R46")
  private val source = DecisionOptionRef.Relic(tail)

  private def staged(supply: Int = 2, faceUp: Int = 3): ReadyGame =
    act(withBoard(withRelic(base, tail))(_.copy(supply = SupplyTrack(supply),
      faceUpSecrets = faceUp, faceDownSecrets = 0)))

  test("Demon Tail is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(DemonTail.id).isDefined)

  test("it burns three secrets and gains 2 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, DemonTail.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(secrets(end), 0)
    // Burnt secrets go to the shared bank, not onto the relic.
    assertEquals(tokensOn(end, tail), Tokens.empty)
    assertEquals(player(end).board.supply, SupplyTrack(4))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("the gain is clamped at the track maximum, and the line says so"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum - 1),
      DemonTail.id, source).toOption.get
    assertEquals(player(ready(done.state)).board.supply, SupplyTrack.full)
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))

  test("two secrets are not enough"):
    val broke = staged(faceUp = 2)
    assert(!usableIds(broke).contains(DemonTail.id))
    assert(use(rules(), broke, DemonTail.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), DemonTail.id, source).toOption.get
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))
```

`WizardSchoolSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class WizardSchoolSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val school = DenizenId("34")
  private val source = DecisionOptionRef.Denizen(school)

  /** Wizard School is site-only; it stands at the actor's pawn site. */
  private def staged(favor: Int = 2): ReadyGame =
    act(withBoard(atHome(base, school))(_.copy(favor = favor)))

  test("Wizard School is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(WizardSchool.id).isDefined)

  test("it places a favor, gains a secret and ends the Act phase"):
    val ready0 = staged()
    val done = use(rules(), ready0, WizardSchool.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, school), Tokens(1, 0))
    assertEquals(player(end).board.favor, 1)
    assertEquals(secrets(end), secrets(ready0) + 1)
    walkerParked.assertResumed(done.state, Phase.Rest, actor)
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0)
    assert(!usableIds(broke).contains(WizardSchool.id))
    assert(use(rules(), broke, WizardSchool.id, source).isLeft)

  test("it writes the gain, covering the generic line, then the phase's end"):
    val done = use(rules(), staged(), WizardSchool.id, source).toOption.get
    assertEquals(NoteText.said(WizardSchool, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} gained 1 secret.",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.",
        covers = false)))
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TutorSuite oathdigital.gameplay.powers.action.ShiftingMapSuite oathdigital.gameplay.powers.action.DemonTailSuite oathdigital.gameplay.powers.action.WizardSchoolSuite"`
Expected: compile failure, "Not found: Tutor" (and the other three).

- [ ] **Step 3: Write the four powers**

`Tutor.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Tutor (card 69, adviser-only), ACTION: place 1 favor and 1 secret on this
  * card, then gain 1 secret from the shared bank, as Elders does. Its own
  * line restates the gain in place of the generic Gain line.
  */
case object Tutor extends PaidAction("denizen.tutor",
    Cost(favor = 1, secret = 1)):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true))))
```

`ShiftingMap.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Shifting Map (relic R17), ACTION: place 1 secret on this relic, then gain
  * 1 Supply, up to the track's maximum. Its own line reads the Supply the
  * track allowed, as Wayside Inn's does.
  */
case object ShiftingMap extends PaidAction("relic.shifting-map",
    Cost(secret = 1)):
  val Supply: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
```

`DemonTail.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Demon Tail (relic R46), ACTION: burn 3 secrets, then gain 2 Supply, up to
  * the track's maximum. Its own line reads the Supply the track allowed, as
  * Wayside Inn's does.
  */
case object DemonTail extends PaidAction("relic.demon-tail",
    Cost(secretBurnt = 3)):
  val Supply: Int = 2
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
```

`WizardSchool.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Wizard School (card 34, site-only), ACTION: place 1 favor on this card,
  * gain 1 secret, then end the Act phase with `EnterPhase(Rest)`, as Murky
  * Fountain ends it. The phase always ends, so its line needs no read of
  * the state.
  */
case object WizardSchool extends PaidAction("denizen.wizard-school",
    Cost(favor = 1)):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  val ended: NoteKey = NoteKey("ended", Vector(NotePart.Arg(0),
    NotePart.Text("'s Act phase ended.")))
  override def noteKeys: Vector[NoteKey] = Vector(gained, ended)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true),
    EnterPhase(Phase.Rest),
    Note(id, _ => PowerSourceRef.of(source).map(ended(_,
      NoteArg.Player(player)))))))
```

- [ ] **Step 4: Register them**

`SelfActionPowers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[DiceAndRelicDrawPowers]].
  */
object SelfActionPowers:
  val powers: Vector[PhasePower] = Vector(Tutor, ShiftingMap, DemonTail,
    WizardSchool)
```

In `PhasePowerCatalog.scala`, change the action import to:

```scala
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, MovementPowers, SelfActionPowers, TargetPowers, WaysideInn}
```

and add the group after `MovementPowers.forCatalog(catalog) ++`:

```scala
      MovementPowers.forCatalog(catalog) ++
      SelfActionPowers.powers ++
      BannerFacePowers.phasePowers)
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: the Step 2 command.
Expected: PASS, 18 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Tutor.scala \
  src/main/scala/oathdigital/gameplay/powers/action/ShiftingMap.scala \
  src/main/scala/oathdigital/gameplay/powers/action/DemonTail.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WizardSchool.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/action/TutorSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ShiftingMapSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/DemonTailSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/WizardSchoolSuite.scala
git commit -m "feat(powers): Tutor, Shifting Map, Demon Tail and Wizard School"
```

---

### Task 2: Spirit Snare

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SpiritSnare.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala`, `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SpiritSnareSuite.scala`

**Interfaces:**
- Consumes: `FavorBankChoice.take(ready, player, amount, decisionId, heading): Vector[Operation]` (one stocked bank is taken unasked, none gives nothing), `NoteSupport.took`, `NoteSupport.tookNote(source, player)(states)`.
- Consumes (tests): `TargetsFixture` (`use`, `answer`, `after`, `awaits`, `pick`, `replayed`, `offered`, `usableNow`), `PowerFixture`.
- Produces: `SpiritSnare` with `id`, `empty: NoteKey`, `choiceDecisionId(ready, player): String`.

- [ ] **Step 1: Write the failing suite**

`SpiritSnareSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SpiritSnareSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val snare = DenizenId("33")
  private val source = DecisionOptionRef.Denizen(snare)

  /** The banks hold exactly `favor`, and the actor holds one faceup secret
    * beside a site Spirit Snare.
    */
  private def staged(favor: (Suit, Int)*): ReadyGame =
    val ready = inPhase(withSecrets(atHome(base, snare), actor, 1, 0),
      Phase.Act)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(_ -> 0).toMap ++ favor))
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == snare => d }.get
  private def decision(ready: ReadyGame) =
    SpiritSnare.choiceDecisionId(ready, actor)

  test("Spirit Snare is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SpiritSnare.id).isDefined)

  test("one stocked bank gives a favor without asking"):
    val ready = staged(Suit.Order -> 3)
    val t = use(ready, SpiritSnare, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 1)
    assertEquals(bank(after(t), Suit.Order), 2)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 favor from the Order bank.",
      covers = true)))

  test("several stocked banks ask which, and the answer is taken from"):
    val ready = staged(Suit.Arcane -> 2, Suit.Beast -> 1)
    val t = use(ready, SpiritSnare, source).toOption.get
    assert(awaits(t, decision(ready)), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Suit.all.filter(
      Set(Suit.Arcane, Suit.Beast)).map(DecisionOptionRef.FavorBank(_))
      .map(r => r.kind -> r.wireId)))
    val done = answer(t, actor, decision(ready),
      pick(DecisionOptionRef.FavorBank(Suit.Beast))).toOption.get
    assertEquals(bank(after(done), Suit.Beast), 0)
    assertEquals(bank(after(done), Suit.Arcane), 2)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertEquals(NoteText.said(SpiritSnare, t.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Beast bank.", covers = true)))

  test("with every bank empty the cost is paid and nothing is taken"):
    val ready = staged()
    assert(usableNow(ready).exists(_.power.id == SpiritSnare.id))
    val t = use(ready, SpiritSnare, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.favor, player(ready).board.favor)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank was empty.", covers = false)))

  test("it is unusable without a faceup secret"):
    val broke = withSecrets(staged(Suit.Order -> 3), actor, 0, 1)
    assert(!usableNow(broke).exists(_.power.id == SpiritSnare.id))
    assert(use(broke, SpiritSnare, source).isLeft)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SpiritSnareSuite"`
Expected: compile failure, "Not found: SpiritSnare".

- [ ] **Step 3: Widen `FavorBankChoice`**

In `FavorBankChoice.scala`, replace the doc comment's first sentence and the visibility:

```scala
/** "Take favor from any one favor bank", for Vow of Obedience's REST, Book
  * Binders and Spirit Snare. The player chooses among the banks that hold
  * favor; one stocked bank is taken without asking, and none leaves nothing
  * to do.
  */
private[powers] object FavorBankChoice:
```

- [ ] **Step 4: Write Spirit Snare**

`SpiritSnare.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

/** Spirit Snare (card 33), ACTION: place 1 secret on this card, then take 1
  * favor from a favor bank the player chooses. One stocked bank is taken
  * without asking. With every bank empty the cost is paid and nothing else
  * happens, so the power stays usable, unlike Vow of Obedience's REST.
  *
  * Its take line covers the generic one. The empty-banks line is written
  * only when no take line was, and covers nothing.
  */
case object SpiritSnare extends PaidAction("denizen.spirit-snare",
    Cost(secret = 1)):
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took, empty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = SpiritSnare.choiceDecisionId(ready, player)
    val took = NoteSupport.tookNote(source, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, 1, choice,
        "Spirit Snare: take a favor from a bank")),
      Note(id, took, covers = true),
      Note(id, states => if took(states).nonEmpty then None
        else PowerSourceRef.of(source).map(empty(_))))))

  /** The power is used at most once a turn. */
  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"spirit-snare-${ready.game.current.tracks.round}-${player.value}"
```

In `SelfActionPowers.scala`, append `SpiritSnare`:

```scala
  val powers: Vector[PhasePower] = Vector(Tutor, ShiftingMap, DemonTail,
    WizardSchool, SpiritSnare)
```

- [ ] **Step 5: Run the suite, then the bank-take neighbours**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SpiritSnareSuite oathdigital.gameplay.powers.cardplay.*"`
Expected: PASS. Vow of Obedience's and Book Binders' suites stay green.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SpiritSnare.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/SpiritSnareSuite.scala
git commit -m "feat(powers): Spirit Snare takes a favor from a bank the player chooses"
```

---

### Task 3: The `Shuffle` operation (N4)

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionValues.scala`, `src/main/scala/oathdigital/model/Cards.scala`, `src/main/scala/oathdigital/model/CoreOperations.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/PileOperations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerDice.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerShuffles.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/application/GameRandomPorts.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Test: create `src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala`; modify `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/application/WalkerDiceAdapterSuite.scala`

**Interfaces:**
- Consumes: `SearchSource` (`WorldDeck`, `RegionalDiscard(region)`), `CardZones.discard(region)`, `ReadyGame.updateCurrent`, `OperationError.InvalidDescription(detail)`, the codec helpers `encodeSearchSource`, `decodeSearchSource`, `encodeWorldCardId`, `decodeWorldCardId`, `traverse` (all reachable from `WalkerOperationCodec` through its `GameEventJsonSupport` self-type).
- Produces:
  - `final case class Shuffle(pile: SearchSource, order: Option[Vector[WorldCardId]] = None) extends PrimitiveOperation`.
  - `SearchSource.name(source): String`: "world deck" or "Cradle discard pile".
  - `CardZones.pile(source): Vector[WorldCardId]` and `CardZones.withPile(source, cards): CardZones`.
  - `WalkerDice.shuffle(count: Int): Either[OathViolation, Vector[Int]]`, a permutation of `0 until count` where the new pile's card `i` is the old pile's card `order(i)`. The default fails loudly; `WalkerDice.placeholder` keeps the order.

- [ ] **Step 1: Write the failing tests**

`ShuffleOperationSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

class ShuffleOperationSuite extends munit.FunSuite:
  private val executor = new OperationExecutor()
  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  /** The Cradle discard pile holds the world deck's top three cards. */
  private val discarded = initialReady.updateCurrent(c => c.copy(
    commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.drop(3),
      regionalDiscards = c.commonCards.regionalDiscards.updated(Region.Cradle,
        c.commonCards.worldDeck.take(3)))))
  private def zones(ready: ReadyGame): CardZones = ready.game.current.commonCards
  private val pile = zones(discarded).discard(Region.Cradle)
  private val deck = zones(discarded).worldDeck

  test("a shuffle writes the world deck's new order"):
    val done = executor.execute(discarded,
      Shuffle(SearchSource.WorldDeck, Some(deck.reverse)))
    assertEquals(done.map(zones(_).worldDeck), Right(deck.reverse))

  test("a shuffle writes a discard pile's new order and leaves the rest"):
    val done = executor.execute(discarded, Shuffle(cradle, Some(pile.reverse)))
      .toOption.get
    assertEquals(zones(done).discard(Region.Cradle), pile.reverse)
    assertEquals(zones(done).worldDeck, deck)

  test("an order that drops, adds or swaps a card is refused"):
    Vector(pile.tail, pile :+ deck.last, pile.tail :+ deck.last).foreach(
      order => assert(executor.execute(discarded,
        Shuffle(cradle, Some(order))).isLeft, order.toString))

  test("a shuffle the walker never ordered is refused"):
    assert(executor.execute(discarded, Shuffle(SearchSource.WorldDeck)).isLeft)

  test("a pile is named for a log line or a label"):
    assertEquals(SearchSource.name(SearchSource.WorldDeck), "world deck")
    assertEquals(SearchSource.name(cradle), "Cradle discard pile")
```

In `GameEventWireSuite.scala`, test "every CoreOperation variant round-trips through the walker codec", insert these three operations immediately before the line `      SetOathkeeper(Some(PlayerId("red"))),`:

```scala
      Shuffle(SearchSource.WorldDeck, Some(Vector(denizen, vision))),
      Shuffle(SearchSource.RegionalDiscard(Region.Cradle), Some(Vector(vision))),
      Shuffle(SearchSource.RegionalDiscard(Region.Hinterland)),
```

In `WalkerDiceAdapterSuite.scala`, add:

```scala
  test("the adapter shuffles a pile into a permutation of its positions"):
    val order = CampaignDicePort.walkerDice(port).shuffle(6)
    assertEquals(order.map(_.sorted), Right((0 until 6).toVector))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.ShuffleOperationSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.WalkerDiceAdapterSuite"`
Expected: compile failure, "Not found: Shuffle".

- [ ] **Step 3: Name the piles and read them**

In `ActionValues.scala`, inside `object SearchSource`, after `final case class RegionalDiscard(region: Region) extends SearchSource`, add:

```scala

  /** "world deck" or "Cradle discard pile". A log line or a label supplies
    * the article. */
  def name(source: SearchSource): String = source match
    case WorldDeck => "world deck"
    case RegionalDiscard(region) => s"$region discard pile"
```

In `Cards.scala`, inside `CardZones`, after `def discard(region: Region)`'s body, add:

```scala

  /** The cards of a pile Search draws from, in stored order: the world deck
    * top first, a discard pile top last. */
  def pile(source: SearchSource): Vector[WorldCardId] = source match
    case SearchSource.WorldDeck => worldDeck
    case SearchSource.RegionalDiscard(region) => discard(region)

  /** These zones with `source` holding `cards`, in stored order. */
  def withPile(source: SearchSource, cards: Vector[WorldCardId]): CardZones =
    source match
      case SearchSource.WorldDeck => copy(worldDeck = cards)
      case SearchSource.RegionalDiscard(region) =>
        copy(regionalDiscards = regionalDiscards.updated(region, cards))
```

- [ ] **Step 4: Declare the operation**

In `CoreOperations.scala`, after the `AdvanceVisionsDrawn` object (it must stay in this file: the hierarchy is sealed), add:

```scala

/** Reorders a pile Search draws from: the world deck, or one region's
  * discard pile. `order` is the pile's new contents in stored order (the
  * world deck top first, a discard pile top last).
  *
  * A tree declares a shuffle with no order. The walker fills the order from
  * its random source before running it, so the journal records the order and
  * replay applies it as it ran, as it applies an automatic roll's faces.
  * Applying a shuffle with no order, or with an order that is not exactly the
  * pile's cards, is an error.
  */
final case class Shuffle(pile: SearchSource,
    order: Option[Vector[WorldCardId]] = None) extends PrimitiveOperation:
  override val required: Boolean = true
```

- [ ] **Step 5: Apply it**

`PileOperations.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** The `Shuffle` write: a pile Search draws from takes the order the walker
  * generated. The order must hold exactly the pile's cards, so a recorded
  * shuffle can reorder a pile but never add, drop or replace a card.
  */
private[operations] object PileOperations:
  import OperationError.InvalidDescription

  def shuffle(ready: ReadyGame, pile: SearchSource,
      order: Option[Vector[WorldCardId]]): Either[OperationError, ReadyGame] =
    val held = ready.game.current.commonCards.pile(pile)
    val name = SearchSource.name(pile)
    order match
      case None => Left(InvalidDescription(
        s"a shuffle of the $name has no order"))
      case Some(cards) if cards.size != held.size || cards.diff(held).nonEmpty =>
        Left(InvalidDescription(
          s"a shuffle of the $name must hold exactly its cards"))
      case Some(cards) => Right(ready.updateCurrent(current => current.copy(
        commonCards = current.commonCards.withPile(pile, cards))))
```

In `OperationApplication.scala`, in `applyNonMoveLeaves`, after the `AdvanceVisionsDrawn` arm, add (the match ends in a silent `case (result, _) => result`, so a missing arm would compile and do nothing):

```scala
      case (result, Shuffle(pile, order)) =>
        result.flatMap(PileOperations.shuffle(_, pile, order))
```

- [ ] **Step 6: Give the walker a shuffle source**

Replace `WalkerDice.scala` with:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation}

/** Where the walker's randomness comes from: an automatic `Roll`'s faces,
  * asked once per roll with the die kind and the pool's count, and a
  * `Shuffle`'s order. The engine never rolls or shuffles; production
  * supplies the application service's port. Replay never asks: it applies
  * the faces recorded in the `RollPayload` and the order recorded in the
  * `Shuffle`.
  */
trait WalkerDice:
  def roll(kind: DiceKind, count: Int): Either[OathViolation, Vector[DieFace]]

  /** A new order for a pile of `count` cards: a permutation of
    * `0 until count`, where the new pile's card `i` is the old pile's card
    * `order(i)`. A source that cannot shuffle fails loudly, as one that
    * cannot roll does. The walker asks only for a pile of two or more.
    */
  def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
    Left(OathViolation.InvalidEventOrder(
      "walker has no shuffle source for a Shuffle"))

object WalkerDice:
  /** The default: fails loudly, so a walk that reaches an automatic roll
    * without a source is a typed violation and never a silent roll.
    */
  val unavailable: WalkerDice = (_, _) => Left(OathViolation.InvalidEventOrder(
    "walker has no dice source for an automatic roll"))

  /** Fixed faces and an unchanged pile order, for simulations: a simulation
    * reports what a tree would do, and no rule reads a placeholder face or
    * order because a start only walks to its first decision.
    */
  val placeholder: WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] =
      Right(Vector.fill(count)(kind match {
        case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
        case DiceKind.Defense => DefenseDieFace.Blank: DieFace
      }))
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right((0 until count).toVector)
```

`WalkerShuffles.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{OathViolation, ReadyGame, Shuffle}

/** Fills a declared `Shuffle`'s order from the walker's random source. Split
  * out of [[ProcedureWalker]], which is close to the project's per-file
  * bound, as [[WalkerRolls]] is. The filled operation is what the walker runs
  * and records, so replay applies the same order without asking again. A pile
  * of fewer than two cards keeps its order and asks nothing.
  */
private[walker] object WalkerShuffles:
  def ordered(shuffle: Shuffle, state: ReadyGame, dice: WalkerDice)
      : Either[OathViolation, Shuffle] =
    val cards = state.game.current.commonCards.pile(shuffle.pile)
    if cards.size < 2 then Right(shuffle.copy(order = Some(cards)))
    else dice.shuffle(cards.size).flatMap(order => Either.cond(
      order.sorted == cards.indices.toVector,
      shuffle.copy(order = Some(order.map(cards))),
      OathViolation.InvalidEventOrder(
        s"shuffle source gave $order for a pile of ${cards.size} cards")))
```

In `ProcedureWalker.scala`, add `Shuffle` to the long `oathdigital.model` import (between `RollMode, ` and `SpendSupply`), then in the `case None =>` branch's `leaf match`, insert before `case _: Decide | _: Roll => Right(Park(path, ctx))`:

```scala
          case shuffle: Shuffle if shuffle.order.isEmpty =>
            WalkerShuffles.ordered(shuffle, ctx.state, ctx.dice).flatMap(
              record(_, ctx, path, contributions, strict)).map(Done(_))
```

A shuffle that already carries its order falls through to the existing `case delta =>` arm, as any operation does.

In `GameRandomPorts.scala`, replace `CampaignDicePort.walkerDice` with:

```scala
  /** The walker's dice source, backed by `port`: the same faces a legacy
    * Campaign rolled, now drawn by an automatic `Roll` node. Its shuffle is
    * uniform.
    */
  def walkerDice(port: CampaignDicePort): WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] = Right(kind match {
      case DiceKind.Attack => port.rollAttack(count)
      case DiceKind.Defense => port.rollDefense(count)
    })
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right(shuffler.shuffle((0 until count).toVector))

  private val shuffler = new scala.util.Random()
```

- [ ] **Step 7: Encode it**

In `WalkerOperationCodec.scala`, add `Shuffle` to the card operations union:

```scala
  private type CardOperation =
    Peek | Flip | Bury | Discard | Draw | Play | Reveal | Swap | Shuffle
```

In `encodeCardOperation`, after the `Swap` arm, add:

```scala
      case Shuffle(pile, order) =>
        val shuffled = ujson.Obj("kind" -> "shuffle",
          "pile" -> encodeSearchSource(pile))
        order.foreach(cards =>
          shuffled("order") = ujson.Arr.from(cards.map(encodeWorldCardId)))
        shuffled
```

In `decodeCardOperation`, after the `"swap"` arm, add:

```scala
    case "shuffle" => for
      pile <- decodeSearchSource(value("pile"), s"$path.pile")
      order <- decodeShuffleOrder(value, path)
    yield Shuffle(pile, order)
```

and after `decodeCardOperation`, add:

```scala
  /** A recorded shuffle carries its order; a declared one has none. */
  private def decodeShuffleOrder(value: ujson.Value, path: String)
      : Either[WireError, Option[Vector[WorldCardId]]] =
    value.obj.get("order").fold[Either[WireError, Option[Vector[WorldCardId]]]](
      Right(None))(cards => traverse(cards.arr.zipWithIndex.toVector)({
        case (card, index) => decodeWorldCardId(card, s"$path.order[$index]")
      }).map(Some(_)))
```

If `WireError` is not already in scope in this file, import it from where `InvalidValue` comes from (the file already names `InvalidValue`).

- [ ] **Step 8: Run the tests to verify they pass**

Run: the Step 2 command, then `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.CampaignProcedureSuite"`.
Expected: PASS. The walker and Campaign suites prove the dice port change left automatic rolls alone.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/ActionValues.scala \
  src/main/scala/oathdigital/model/Cards.scala \
  src/main/scala/oathdigital/model/CoreOperations.scala \
  src/main/scala/oathdigital/gameplay/operations/PileOperations.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerDice.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerShuffles.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/application/GameRandomPorts.scala \
  src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala \
  src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala \
  src/test/scala/oathdigital/serialization/GameEventWireSuite.scala \
  src/test/scala/oathdigital/application/WalkerDiceAdapterSuite.scala
git commit -m "feat(engine): a Shuffle operation whose order the walker draws and the journal records"
```

---

### Task 4: `NoteArg.Pile` and Clay Rattle

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala`, `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`, `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`, `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/ClayRattle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: create `src/test/scala/oathdigital/gameplay/powers/action/ClayRattleSuite.scala`; modify `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: Task 3's `Shuffle`, `SearchSource.name`, `WalkerDice.shuffle`; `NoteSupport.answer(states, decisionId)`; `Branch(select: (ReadyGame, PendingTree) => Vector[Operation])`; `DecisionQuery.ChooseOne(options, heading = Some(...))`; `DecisionOption.Button(ref, label)`.
- Consumes (tests): `PaidActionHarness`, `PowerFixture`, `TargetsFixture.offered`, `ParkedDecisionAssertions`; in `GameLogPowerLinesSuite` the helpers its banner test uses (`usePower`, `withoutNotes`, `inserted`, `take`, `saying`, `ours`, `text`, `name`).
- Produces: `NoteArg.Pile(pile: SearchSource)`; `ClayRattle` with `id`, `decisionId`, `piles: Vector[SearchSource]`, `ref(pile): DecisionOptionRef.Button`.

- [ ] **Step 1: Write the failing tests**

`ClayRattleSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerDice}
import oathdigital.model._

class ClayRattleSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val rattle = RelicId("R47")
  private val source = DecisionOptionRef.Relic(rattle)
  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  /** Reverses every pile it shuffles, and never rolls. */
  private val reversing: WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] =
      Left(OathViolation.InvalidEventOrder("Clay Rattle never rolls"))
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right((0 until count).reverse.toVector)
  private val shuffling = rules(reversing)

  /** The actor holds Clay Rattle and `faceUp` faceup secrets. The Cradle
    * discard pile holds the world deck's top three cards, and the
    * Hinterland's is empty.
    */
  private def staged(faceUp: Int = 2): ReadyGame =
    act(withBoard(withRelic(base, rattle))(_.copy(faceUpSecrets = faceUp)))
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.drop(3),
        regionalDiscards = c.commonCards.regionalDiscards
          .updated(Region.Cradle, c.commonCards.worldDeck.take(3))
          .updated(Region.Hinterland, Vector.empty))))
  private def zones(ready: ReadyGame): CardZones = ready.game.current.commonCards

  /** Clay Rattle is used under `under` and `pile` is chosen. */
  private def shuffled(pile: SearchSource, under: OathRules = shuffling) =
    val ready0 = staged()
    val started = use(under, ready0, ClayRattle.id, source).toOption.get
    (ready0, started, answer(under, started.state, ClayRattle.decisionId,
      ClayRattle.ref(pile)))

  test("Clay Rattle is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(ClayRattle.id).isDefined)

  test("it places two secrets and asks which pile to shuffle"):
    val started = use(shuffling, staged(), ClayRattle.id, source).toOption.get
    assertEquals(parked.parkedDecision(started.state).map(_.decision),
      Some(ClayRattle.decisionId))
    assertEquals(tokensOn(ready(started.state), rattle), Tokens(0, 2))
    assertEquals(TargetsFixture.offered(started, actor),
      Some(ClayRattle.piles.map(ClayRattle.ref).map(r => r.kind -> r.wireId)))
    assertEquals(ClayRattle.piles.size, 4)

  test("the world deck takes the source's order, and replay applies it"):
    val (ready0, started, answered) = shuffled(SearchSource.WorldDeck)
    val done = answered.toOption.get
    val end = ready(done.state)
    assertEquals(zones(end).worldDeck, zones(ready0).worldDeck.reverse)
    assertEquals(zones(end).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle))
    parked.assertResumed(done.state, Phase.Act, actor)
    // Replay has no shuffle source: it applies the recorded order.
    assertEquals(replayed(rules(), ready0, started.events ++ done.events), end)
    assert(wireRoundTrips(started.events ++ done.events))

  test("a region's discard pile is shuffled, and its line names it"):
    val (ready0, started, answered) = shuffled(cradle)
    val done = answered.toOption.get
    assertEquals(zones(ready(done.state)).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle).reverse)
    assertEquals(zones(ready(done.state)).worldDeck, zones(ready0).worldDeck)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Cradle discard pile.", covers = false)))

  test("an empty pile is shuffled without asking the source"):
    val hinterland = SearchSource.RegionalDiscard(Region.Hinterland)
    val (_, started, answered) = shuffled(hinterland, under = rules())
    val done = answered.toOption.get
    assertEquals(zones(ready(done.state)).discard(Region.Hinterland),
      Vector.empty)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Hinterland discard pile.",
        covers = false)))

  test("a walker with no shuffle source refuses a pile of two or more"):
    val (_, _, answered) = shuffled(SearchSource.WorldDeck, under = rules())
    assert(answered.isLeft)

  test("one faceup secret is not enough"):
    val broke = staged(faceUp = 1)
    assert(!usableIds(broke).contains(ClayRattle.id))
    assert(use(shuffling, broke, ClayRattle.id, source).isLeft)
```

In `GameEventWireSuite.scala`, test "a power note's card list and banner arguments round trip", replace the line `      NoteArg.Cards(Vector.empty), NoteArg.Banner(Banner.PeoplesFavor))` with:

```scala
      NoteArg.Cards(Vector.empty), NoteArg.Banner(Banner.PeoplesFavor),
      NoteArg.Pile(SearchSource.WorldDeck),
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Provinces)))
```

In `GameLogPowerLinesSuite.scala`, after the test "a card list reads as one phrase, and a banner by its name", add:

```scala
  test("a pile reads as its name, the template supplying the article"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val cradle = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Cradle))))
    assertEquals(text(ours(cradle).head),
      s"Silver Tongue: ${name(script.actor)} said Cradle discard pile.")
    val deck = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.WorldDeck)))
    assertEquals(text(ours(deck).head),
      s"Silver Tongue: ${name(script.actor)} said world deck.")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ClayRattleSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: compile failure, "Not found: ClayRattle" and "value Pile is not a member of object NoteArg".

- [ ] **Step 3: Add `NoteArg.Pile`**

In `PowerNotes.scala`, after `final case class Banner(...)` in `object NoteArg`, add:

```scala
  /** "world deck" or "Cradle discard pile". The template supplies the
    * article. */
  final case class Pile(pile: SearchSource) extends NoteArg
```

In `WalkerEventCodec.scala`, add to `encodeNoteArg` after the `Banner` arm:

```scala
    case NoteArg.Pile(pile) => ujson.Obj("kind" -> "pile",
      "pile" -> encodeSearchSource(pile))
```

and to `decodeNoteArg` after the `"banner"` arm:

```scala
    case "pile" => decodeSearchSource(value("pile"), s"$path.pile")
      .map(NoteArg.Pile.apply)
```

In `PowerLines.scala`, add to `argument` after the `Banner` arm:

```scala
    case NoteArg.Pile(pile) => Vector(Text(SearchSource.name(pile)))
```

In the test file `NoteText.scala`, add to `plain` after the `Banner` arm:

```scala
    case NoteArg.Pile(pile) => SearchSource.name(pile)
```

- [ ] **Step 4: Write Clay Rattle**

`ClayRattle.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Clay Rattle (relic R47), ACTION: place 2 secrets on this relic, then
  * shuffle the world deck or any region's discard pile.
  *
  * The player chooses the pile among four buttons, empty piles included:
  * the card lets any pile be chosen. The shuffle is declared with no order,
  * and the walker draws the order from its random source and records it
  * (the `Shuffle` operation). A `Branch` reads the answer, since the tree is
  * built before it is given.
  */
case object ClayRattle extends PaidAction("relic.clay-rattle",
    Cost(secret = 2)):
  val decisionId: String = "power.clay-rattle.pile"
  val shuffled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" shuffled the "), NotePart.Arg(1), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(shuffled)

  /** The piles it may shuffle, in the order they are offered. */
  val piles: Vector[SearchSource] =
    SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_))

  def ref(pile: SearchSource): DecisionOptionRef.Button =
    val key = pile match
      case SearchSource.WorldDeck => "world-deck"
      case SearchSource.RegionalDiscard(region) => region.key
    DecisionOptionRef.Button(key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Decide(decisionId, player, DecisionQuery.ChooseOne(piles.map(pile =>
      DecisionOption.Button(ref(pile), SearchSource.name(pile).capitalize)),
      heading = Some("Clay Rattle: shuffle a pile"))),
    Branch((_, pending) => chosen(pending.answered).toVector.map(Shuffle(_))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      pile <- chosen(states.answered)
    yield shuffled(card, NoteArg.Player(player), NoteArg.Pile(pile))))))

  private def chosen(answered: Vector[Answered]): Option[SearchSource] =
    answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(picked), _) =>
        picked
    }.flatMap(picked => piles.find(ref(_) == picked))
```

In `SelfActionPowers.scala`, append `ClayRattle`:

```scala
  val powers: Vector[PhasePower] = Vector(Tutor, ShiftingMap, DemonTail,
    WizardSchool, SpiritSnare, ClayRattle)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: the Step 2 command, then `./sbtw "testOnly oathdigital.gameplay.powers.*"`.
Expected: PASS. `PowerNoteCatalogSuite` accepts the new template, which starts with an argument.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
  src/main/scala/oathdigital/serialization/WalkerEventCodec.scala \
  src/main/scala/oathdigital/application/gamelog/PowerLines.scala \
  src/main/scala/oathdigital/gameplay/powers/action/ClayRattle.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/NoteText.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ClayRattleSuite.scala \
  src/test/scala/oathdigital/serialization/GameEventWireSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala
git commit -m "feat(powers): Clay Rattle shuffles a pile the player chooses"
```

---

### Task 5: Pin the cards, record the slice, run the gates

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`, `docs/ROADMAP.md`

- [ ] **Step 1: Pin the six cards**

In `PowerImplementationStatusSuite.scala`, after the test "catalog batch 2's battle plans are implemented", add:

```scala

  test("catalog batch 2's first actions on yourself are implemented"):
    Vector("denizen.tutor", "denizen.spirit-snare", "denizen.wizard-school",
      "relic.shifting-map", "relic.demon-tail", "relic.clay-rattle")
      .foreach(id => assert(implemented(PowerId(id)), id))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Record the slice**

In `docs/ROADMAP.md`, "Phase - Catalog batch 2", replace the sentence "Slices 3 to 5 remain: actions on yourself, actions on others, then triggers and when-played powers." with:

```markdown
Slice 3 runs as three plans. Slice 3a is done: Tutor, Spirit Snare, Wizard
School, Shifting Map, Demon Tail and Clay Rattle, with the `Shuffle`
operation. Slice 3b (Scryer, Oracular Pig and the card-list view) and 3c
(Oracle and drawing a Vision) remain, then slice 4, actions on others, and
slice 5, triggers and when-played powers.
```

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every suite passes. The server count is the baseline plus this plan's new tests (18 in Task 1, 5 in Task 2, 6 in Task 3, 8 in Task 4, 1 in Task 5: 38).

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 3a"
```
