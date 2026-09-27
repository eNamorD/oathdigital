# Catalog Batch 2, Slice 5 (Triggers and When Played) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the last four powers of catalog batch 2: Shifting Fog (denizen 214), Twin Brother (170), Chaos Cult (101) and Hunger (216). Along the way, build the two engine additions they need, N7 and N8, and the roadmap's adviser-slot decision option.

**Architecture:**

- **Adviser-slot option (roadmap item, Task 1).** `DecisionOptionRef.AdviserSlot(owner, slot)` names an adviser by its owner and its position, as `RelicSlot` names a relic. An option can then offer another player's facedown adviser without disclosing the card.
  - The projector presents the slot as the card when the viewer may identify it, and as a card back otherwise.
  - The log names it the same way, through `LogWords.slotted`.
  - The frontend already draws an option's `card` with `CardFace`, backs included, so it needs no change.
  - Ivory Eye drops its `Button` workaround for the new ref. Hunger is the ref's first new user.
- **Shifting Fog and Twin Brother (Task 2)** are `WhenPlayedPower`s, like Faithful Friend and Family Heirloom.
  - Each adds exactly one node to the card-played window: a `Branch` whose children are read live. The node count therefore never depends on live state (Book Binders' doc explains why that matters).
  - Shifting Fog moves each bank's favor with one `Move` per bank. Each amount is read before any move, so the order of the moves does not matter.
  - Twin Brother is already the actor's faceup adviser when its hook runs (`CardPlayProcedure` places the card before the hook). It asks, then `Swap`s. Card tokens live on the card state, so favor and secrets travel with each card.
- **N7 and Chaos Cult (Task 3).**
  - `OathkeeperProcedure` wraps both of its trees in a `Sequence` with the new window `PowerWindow.OathkeeperTitleChange`.
  - Chaos Cult is a `ContributingPower` that appends a `BuildOps` take and a `Note` there.
  - Every applied `SetOathkeeper` is a real change of holder (`setOathkeeper` rejects an unchanged holder), and nothing sets the Usurper side yet. The take therefore only checks that the new holder is someone other than Chaos Cult's holder.
- **N8 and Hunger (Task 4).**
  - `PhasePower` gains `forced: Boolean = false`. A forced power is never offered or accepted as an optional use.
  - A new triggered procedure, `TriggeredProcedureRef.ForcedWake`, runs `ForcedWakeProcedure`: every forced Wake power the waking player can access, in catalog order.
  - `OathRules.enterWake` starts it after the Usurper and Vision checks, when the game is not over and a forced power is due. A parked walker already blocks End Wake and every optional power, so nothing new is needed to make the step come first.
  - Like `UsePower`, the procedure needs the phase power catalog. It is threaded through the same three places `UsePower` uses:
    - `OathRulesWalker.buildWalker`;
    - `WalkerDecisionProjector.tree`;
    - `WalkerProcedureRegistry.lookup`.
  - Hunger is Crystal Vial's shape: a live `Branch` question over `AdviserSlot`s, a `Bury.standard`, and a covering `Note`.
- **Log lines.**
  - None of the new effects posts a generic detail line, except Hunger's `Bury` ("Buried {card}"). Hunger's note covers it.
  - A favor move between banks, a `Swap` between play areas, and a `Take` between boards post nothing.
  - Every note key here is a trigger key, never `used`, since none of these runs as a `UsePower`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The only frontend-visible change is a new option kind, which the existing option renderer draws.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md`: "N7", "N8", "Slicing", "Log lines" ("Added effects") and "Testing". The per-card rulings are in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md`. The adviser-slot option is the ROADMAP item "an adviser-slot decision option". Read all three before starting. Slices 1 to 4 are merged on `main`.

**Rulings made while planning:**

- **The adviser-slot option ships in this slice.** Hunger must offer another player's facedown adviser, and a `Denizen` option would send that card's id to the chooser. Ivory Eye moves to it in the same task, so no `Button` workaround is left. Cost if wrong: one extra task.
- **The first Wake of the game gets no forced step.** Setup keeps every starting adviser facedown (`SetupProcedure.chooseAdviser`), so no forced power can be accessible then. Setup's completion stays untouched. Cost if wrong: a Hunger that somehow starts faceup would skip one Wake.
- **Hunger offers every adviser, denizen or Vision, in either orientation,** of every player whose pawn is at the holder's site, the holder included. Only Hunger itself is excluded. Locked cards are offered, since `Bury` ignores locked. The options are listed in seat order, then adviser order. Hunger asks even when only one option exists. The standard returns send favor to the card's suit bank and secrets to Hunger's holder, as Crystal Vial's do. Cost if wrong: a filter.
- **Hunger's keys are `buried` and `none`, not `used`.** The run is a `ForcedWake`, not a `UsePower`, so the lines are Trigger lines and no "Used Hunger" line exists to replace.
- **Shifting Fog writes its line only when some favor moved.** With every bank empty it writes nothing, as the spec's "amounts are what happened" rule implies.
- **Twin Brother's candidates are other players' faceup nomad denizen advisers whose restrictions are neither `Locked` nor `LockedAdviserOnly`.** The actor may decline with a "Keep Twin Brother" button, listed last. With no candidate nothing is asked. It writes nothing when declined, as the spec says.
- **Chaos Cult takes from the new holder's board only when they hold favor.** Its key is `took`. It fires on every title change the Oathkeeper procedure makes, including a holder's forced transfer. Its catalog flag is `persistent: false`, so its resolution must stay the trait default (`Automatic`), as Dazzle's does.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member, or a non-exhaustive match over a sealed type, fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A power under `gameplay/powers` never imports `gameplay.walker`.
  - No walker source (`gameplay/walker`) names a power.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree branched from local `main`, with the main checkout's `.tooling` symlinked in before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Card ids:

  | Card | Card id | Power id | Kind |
  |---|---|---|---|
  | Shifting Fog | `DenizenId("214")` | `denizen.shifting-fog` | When Played |
  | Twin Brother (adviser-only) | `DenizenId("170")` | `denizen.twin-brother` | When Played |
  | Chaos Cult (adviser-only) | `DenizenId("101")` | `denizen.chaos-cult` | Title-change trigger |
  | Hunger (adviser-only, locked) | `DenizenId("216")` | `denizen.hunger` | Forced WAKE |

- Log lines, where `{Red}` is the acting player and `{Blue}` another:

  | Card | Key | Line | Covers |
  |---|---|---|---|
  | Shifting Fog | `moved` | Shifting Fog: Every bank's favor moved to the next bank. | |
  | Twin Brother | `swapped` | Twin Brother: {Red} swapped it for {Blue}'s {card}. | |
  | Chaos Cult | `took` | Chaos Cult: {Red} took {1 favor} from {Blue}. | |
  | Hunger | `buried` | Hunger: {Red} buried {Blue}'s {card}. | the Buried line |
  | Hunger, nothing to bury | `none` | Hunger: No adviser could be buried. | |

- Decision ids:
  - `cardplay.twin-brother.swap`;
  - `power.hunger.adviser`.
- New wire kinds and keys:
  - option kind `adviser-slot`, id `{owner}:{slot}`;
  - window key `oathkeeper.title-change`;
  - triggered procedure key `forced-wake`.
- Baselines: record the server test count from your first full `./sbtw "test"` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`;
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Modify `src/main/scala/oathdigital/model/Decisions.scala` | `DecisionOptionRef.AdviserSlot`, its wire parse, `DecisionOption.AdviserSlot`. |
| Modify `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | Presents an adviser slot; threads phase powers to `ForcedWake`. |
| Modify `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala` | Names a chosen adviser slot. |
| Modify `src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala` | Offers `AdviserSlot`s. |
| Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFog.scala` | Rotates the favor banks. |
| Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala` | Optional swap with another player's nomad adviser. |
| Modify `src/main/scala/oathdigital/model/PowerWindow.scala` | `OathkeeperTitleChange`. |
| Modify `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala` | Wraps its trees in the window. |
| Create `src/main/scala/oathdigital/gameplay/powers/title/ChaosCult.scala` | Takes 1 favor from a new Oathkeeper. |
| Modify `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` | Registers Shifting Fog, Twin Brother, Chaos Cult. |
| Modify `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala` | `forced`. |
| Modify `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` | Never offers or accepts a forced power. |
| Create `src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala` | The forced step's tree. |
| Modify `src/main/scala/oathdigital/model/ProcedureRef.scala` | `TriggeredProcedureRef.ForcedWake`. |
| Modify `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | Registers `ForcedWake` with the phase powers. |
| Modify `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | Builds `ForcedWake` with the phase powers. |
| Modify `src/main/scala/oathdigital/gameplay/OathRules.scala` | Starts the forced step at Wake. |
| Modify `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` | No action line for `ForcedWake`. |
| Create `src/main/scala/oathdigital/gameplay/powers/wake/Hunger.scala` | Buries an adviser at the site. |
| Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` | Registers Hunger. |
| Tests | One suite per card; codec, projector and log additions for the slot; `EnumShapeSuite`, `PowerKindsCatalogSuite`, `PowerImplementationStatusSuite`; a `hunger` log script with golden logs. |
| Modify `docs/ROADMAP.md` | Slice 5 and batch 2 done; adviser-slot item done. |

---

### Task 1: The adviser-slot option, and Ivory Eye on it

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala`
- Test: `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala`

**Interfaces:**
- Produces:
  - `DecisionOptionRef.AdviserSlot(owner: PlayerId, slot: Int)`, with kind `"adviser-slot"`;
  - `DecisionOption.AdviserSlot(ref: DecisionOptionRef.AdviserSlot)`;
  - `IvoryEye.optionFor(owner, slot): DecisionOptionRef.AdviserSlot`.
  - Task 4's Hunger uses the ref.

- [ ] **Step 1: Write the failing tests**

In `DecisionOptionRefSuite`, add after the relic-slot tests:

```scala
  private val adviser = DecisionOptionRef.AdviserSlot(PlayerId("blue"), 1)

  test("an adviser slot spells itself as a kind and owner-and-index id and " +
      "parses back"):
    assertEquals((adviser.kind, adviser.wireId), ("adviser-slot", "blue:1"))
    assertEquals(DecisionOptionRef.fromWire(adviser.kind, adviser.wireId),
      Some(adviser))
    val odd = DecisionOptionRef.AdviserSlot(PlayerId("a:b"), 0)
    assertEquals(DecisionOptionRef.fromWire(odd.kind, odd.wireId), Some(odd))

  test("a malformed adviser slot is not a reference"):
    Vector("blue", "blue:", ":2", "blue:-1", "blue:x", "blue:1.5").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("adviser-slot", id), None, id)
    }

  test("an adviser slot is presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(adviser),
      Some(DecisionOption.AdviserSlot(adviser)))
```

In `GameEventWireSuite`, add `DecisionOptionRef.AdviserSlot(PlayerId("blue"), 1),` to the `refs` vector beside `RelicSlot` (line 239).

In `GameLogRareLinesSuite`, in "a chosen option of every kind reads as its name, never its key", add after the `RelicSlot` assertion. `other` holds its starting adviser facedown at slot 0, and the viewer is `None`:

```scala
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 0)),
      s"${name(other)}'s facedown adviser (slot 1)")
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 9)),
      s"${name(other)}'s adviser (slot 10)")
```

In `WalkerDecisionProjectorSuite`, add after the relic-slot test:

```scala
  test("an adviser slot is the card to a viewer who identifies it, a card " +
      "back to anyone else, and a slot past the advisers suppresses it"):
    val (base, actor) = parked(ActionRef.Recover)
    val enemy = base.ready.game.current.players.find(_.player != actor).get
    def slot(owner: PlayerId, at: Int) = DecisionOption.AdviserSlot(
      DecisionOptionRef.AdviserSlot(owner, at))
    val theirs = projects(base, actor, Vector(slot(enemy.player, 0)))
      .getOrElse(fail("another player's facedown adviser must project"))
    val row = theirs.offeredOptions.head
    assertEquals((row.kind, row.id), ("adviser-slot", s"${enemy.player.value}:0"))
    assert(row.card.exists(_.hidden), row.toString)
    assert(!row.toString.contains(enemy.advisers.head.id.value), row.toString)
    assert(row.label.contains("facedown adviser"), row.label)
    val mine = projects(base, actor, Vector(slot(actor, 0)))
      .getOrElse(fail("the viewer's own adviser must project"))
    assert(mine.offeredOptions.head.card.exists(!_.hidden))
    assertEquals(projects(base, actor, Vector(slot(enemy.player, 9))), None)
```

If `parked(ActionRef.Recover)` stages an actor with no adviser at slot 0, stage one with `base.copy(ready = ...)` as the relic-slot test does. Starting advisers are facedown, and every player holds one after Setup.

In `IvoryEyeSuite`, add:

```scala
  test("its options are adviser slots, drawn as card backs for another " +
      "player's adviser"):
    val t = use(staged, IvoryEye, source).toOption.get
    val options = queryOf(t, actor).get.offeredOptions
    assert(options.forall(_.kind == "adviser-slot"), options.toString)
    assert(options.filter(_.id.startsWith(s"${target.value}:"))
      .forall(_.card.exists(_.hidden)), options.toString)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly *DecisionOptionRefSuite *IvoryEyeSuite"`
Expected: compilation failure, because `AdviserSlot` is not a member of `DecisionOptionRef`.

- [ ] **Step 3: Add the reference and the option**

In `src/main/scala/oathdigital/model/Decisions.scala`, add after `RelicSlot`:

```scala
  /** One adviser of a player, named by its position in their advisers
    * rather than by the card, so a facedown adviser's identity is never
    * disclosed by an option. The owner may contain a colon; the slot is
    * always the text after the last one.
    */
  final case class AdviserSlot(owner: PlayerId, slot: Int)
      extends DecisionOptionRef:
    require(slot >= 0, "an adviser slot must be non-negative")
    val kind: String = "adviser-slot"
    def wireId: String = s"${owner.value}:$slot"
```

In `fromWire`, replace the `"relic-slot"` case with these two cases:

```scala
      case "relic-slot" => slotOf(wireId).map((owner, slot) =>
        RelicSlot(owner, slot))
      case "adviser-slot" => slotOf(wireId).map((owner, slot) =>
        AdviserSlot(owner, slot))
```

Add this helper after `fromWire`:

```scala
  /** The owner and non-negative slot of a slot reference's wire id. */
  private def slotOf(wireId: String): Option[(PlayerId, Int)] =
    val at = wireId.lastIndexOf(':')
    Option.when(at > 0)(wireId.take(at)).filter(_.trim.nonEmpty)
      .flatMap(owner => wireId.drop(at + 1).toIntOption
        .filter(_ >= 0).map(PlayerId(owner) -> _))
```

Update `fromWire`'s doc from "eleven variants" to "twelve variants".

In `object DecisionOption`, add after `RelicSlot`:

```scala
  final case class AdviserSlot(ref: DecisionOptionRef.AdviserSlot)
      extends DecisionOption
```

In `forRef`, add after the `RelicSlot` case:

```scala
    case value: DecisionOptionRef.AdviserSlot => Some(AdviserSlot(value))
```

- [ ] **Step 4: Present it and name it**

In `WalkerDecisionProjector.optionProjection`, add after the `DecisionOption.RelicSlot` case:

```scala
      // The card when the viewer may identify it, else its back: a slot
      // never discloses a facedown adviser, but the panel still draws a card.
      case DecisionOption.AdviserSlot(slot) =>
        ready.game.current.players.find(_.player == slot.owner)
          .flatMap(_.advisers.lift(slot.slot)).flatMap { held =>
            val owner = presentation.safeLabel(slot.owner.value)
            card(ready, viewer, index, held.id) match
              case Some(details) => row(details.name, Some(details),
                extra = Vector(s"$owner's adviser"))
              case None => row(s"$owner's facedown adviser ${slot.slot + 1}",
                Some(presentation.hiddenCard(presentation.cardKind(held.id))))
          }
```

In `ChoiceWords.option`, add after the `RelicSlot` case:

```scala
    case DecisionOptionRef.AdviserSlot(owner, slot) =>
      before.game.current.players.find(_.player == owner)
        .flatMap(_.advisers.lift(slot)).fold(Vector[LogSpan](
          words.player(owner), Text(s"'s adviser (slot ${slot + 1})")))(held =>
          words.player(owner) +: Text("'s ") +:
            words.slotted(held.id, owner, before, after, viewer))
```

Compile with `./sbtw "Test/compile"`. For any other non-exhaustive match over `DecisionOptionRef` or `DecisionOption` that the compiler names, add a case mirroring that match's `RelicSlot` case.

- [ ] **Step 5: Move Ivory Eye onto the slot**

In `IvoryEye.scala`:

1. Delete `private val Prefix = "adviser:"`.
2. Replace `optionFor` with:

```scala
  /** The option for the facedown adviser in position `slot` of `owner`'s
    * advisers.
    */
  def optionFor(owner: PlayerId, slot: Int): DecisionOptionRef.AdviserSlot =
    DecisionOptionRef.AdviserSlot(owner, slot)
```

3. In `Target`, make `ref` return `DecisionOptionRef.AdviserSlot` and delete `label`.
4. In `ask`, offer `found.map(t => DecisionOption.AdviserSlot(t.ref))`.
5. Replace the class doc's last paragraph with:

```scala
  * The options are adviser slots, not card references, so a facedown
  * adviser of another player is offered without disclosing it. The slot is
  * read from live state, so an answer cannot name a card that has since moved.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly *DecisionOptionRefSuite *GameEventWireSuite *WalkerDecisionProjectorSuite *GameLogRareLinesSuite *IvoryEyeSuite"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala
git commit -m "feat(decisions): an adviser-slot option offers a facedown adviser unnamed"
```

Add any file the compiler made you touch in Step 4 to the `git add`.

---

### Task 2: Shifting Fog and Twin Brother

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFog.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/TwinBrotherSuite.scala`

**Interfaces:**
- Consumes: `WhenPlayedPower`, `WhenPlayedHarness`, `PowerFixture`, `TargetsFixture.giveAdviser`, `NoteText`, `NoteSupport.answer`.
- Produces:
  - `ShiftingFog.forCatalog`, `ShiftingFog.next(suit)`;
  - `TwinBrother.forCatalog`, `TwinBrother.decisionId`, `TwinBrother.keep`.

- [ ] **Step 1: Write the failing suites**

Create `ShiftingFogSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class ShiftingFogSuite extends munit.FunSuite:
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = ShiftingFog.forCatalog(catalog).get
  private val card = power.cardId
  private def banked(ready: ReadyGame, favor: Map[Suit, Int]) =
    ready.copy(banks = ready.banks.copy(favor = favor))
  private val staged = asAdviser(base, card)

  test("Shifting Fog is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("the next bank to the right, Nomad's going to Discord"):
    assertEquals(Suit.all.map(ShiftingFog.next), Vector(Suit.Arcane,
      Suit.Order, Suit.Hearth, Suit.Beast, Suit.Nomad, Suit.Discord))

  test("every bank's favor moves at once to the next bank"):
    val ready = banked(staged, Suit.all.zipWithIndex.map((suit, i) =>
      suit -> (i + 1)).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(Suit.all.map(done.treeless.banks.favor(_)),
      Vector(6, 1, 2, 3, 4, 5))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("moved",
        "Every bank's favor moved to the next bank.", covers = false)))

  test("equal banks still move, and the line is written"):
    val ready = banked(staged, Suit.all.map(_ -> 2).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events).size, 6)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events).size, 1)

  test("with every bank empty nothing moves and nothing is written"):
    val ready = banked(staged, Suit.all.map(_ -> 0).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)
```

Create `TwinBrotherSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.model._

class TwinBrotherSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture.{giveAdviser, others, updatePlayer}
  import WhenPlayedHarness._

  private val power = TwinBrother.forCatalog(catalog).get
  private val card = power.cardId
  private val enemy = others(base)(0)
  private val archers = DenizenId("24")   // nomad, unrestricted
  private val faithful = DenizenId("28")  // nomad, locked
  private val charming = DenizenId("131") // hearth

  /** Twin Brother is the actor's faceup adviser; the enemy holds Horse
    * Archers faceup with 1 favor and 2 secrets on it. */
  private def staged = updatePlayer(giveAdviser(asAdviser(base, card), enemy,
    archers, Orientation.FaceUp), enemy)(p => p.copy(advisers = p.advisers.map {
      case d: DenizenState if d.id == archers => d.copy(tokens = Tokens(1, 2))
      case other => other }))

  private def choice(ready: ReadyGame) =
    val first = parked(play(ready, power, card))
    ProcedureWalker.parkedDecide(ready, hook(card), first.tree, powers(power))
      .get.query.asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)

  private def answer(ready: ReadyGame, ref: DecisionOptionRef) =
    val first = parked(play(ready, power, card))
    (first, finished(ProcedureWalker.resolve(ready, hook(card), first.tree,
      Answered(TwinBrother.decisionId, DecisionAnswer.ChooseOneAnswer(ref),
        actor), powers(power))))

  test("Twin Brother is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("it offers other players' faceup unlocked nomad advisers, then keeping"):
    val ready = giveAdviser(giveAdviser(giveAdviser(staged, enemy, faithful,
      Orientation.FaceUp), enemy, charming, Orientation.FaceUp), actor,
      DenizenId("26"), Orientation.FaceUp)
    assertEquals(choice(ready), Vector(DecisionOptionRef.Denizen(archers),
      TwinBrother.keep))

  test("a facedown nomad adviser is not offered, and with none nothing is asked"):
    val ready = giveAdviser(asAdviser(base, card), enemy, archers,
      Orientation.FaceDown)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)

  test("the swap moves both cards faceup, each with its own favor and secrets"):
    val (first, done) = answer(staged, DecisionOptionRef.Denizen(archers))
    val mine = player(done.treeless).advisers
    val theirs = player(done.treeless, enemy).advisers
    assert(mine.contains(DenizenState(archers, Orientation.FaceUp,
      Tokens(1, 2))), mine.toString)
    assert(theirs.contains(DenizenState(card, Orientation.FaceUp,
      Tokens.empty)), theirs.toString)
    assert(!mine.exists(_.id == card))
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("swapped", s"${actor.value} swapped it for " +
        s"${enemy.value}'s ${archers.value}.", covers = false)))

  test("keeping Twin Brother changes nothing and writes nothing"):
    val (_, done) = answer(staged, TwinBrother.keep)
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)

  test("an option that is not offered is rejected"):
    val first = parked(play(staged, power, card))
    assert(ProcedureWalker.resolve(staged, hook(card), first.tree, Answered(
      TwinBrother.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(charming)), actor), powers(power)).isLeft)
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly *ShiftingFogSuite *TwinBrotherSuite"`
Expected: compilation failure, because `ShiftingFog` and `TwinBrother` are not found.

- [ ] **Step 3: Write Shifting Fog**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFog.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Shifting Fog (card 214), WHEN PLAYED: move all favor in each favor bank
  * to the next bank to the right. Nomad's moves to Discord's.
  *
  * The move is simultaneous. Each bank sends what it held before any move,
  * all read live when the step runs, so a bank that receives favor first
  * still sends only its own. The effect is one `Branch`, so the node count
  * this power adds to the card-played window never depends on live state.
  * With every bank empty nothing moves and no line is written.
  */
final case class ShiftingFog private (cardId: DenizenId)
    extends WhenPlayedPower:
  import ShiftingFog._
  def id: PowerId = ShiftingFog.id

  override def noteKeys: Vector[NoteKey] = Vector(moved)

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(Branch((live, _) =>
    if moves(live).isEmpty then Vector.empty
    else Vector(BuildOps((ready, _) => Right(moves(ready))),
      Note(id, _ => Some(moved(PowerSourceRef.Card(cardId)))))))

object ShiftingFog:
  val id: PowerId = PowerId("denizen.shifting-fog")
  /** "Every bank's favor moved to the next bank." */
  val moved: NoteKey = NoteKey("moved", Vector(
    NotePart.Text("Every bank's favor moved to the next bank.")))

  def forCatalog(catalog: ExecutableCatalog): Option[ShiftingFog] =
    WhenPlayedPower.cardOf(catalog, id).map(new ShiftingFog(_))

  /** The bank to the right of `suit`, in the printed order. */
  def next(suit: Suit): Suit =
    Suit.all((Suit.all.indexOf(suit) + 1) % Suit.all.size)

  /** One move per stocked bank, each of what that bank holds now. */
  def moves(ready: ReadyGame): Vector[CoreOperation] = Suit.all.flatMap { suit =>
    val held = ready.banks.favor.getOrElse(suit, 0)
    Option.when(held > 0)(Move(Piece.Favor(held),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.FavorBank(next(suit)))))
  }
```

- [ ] **Step 4: Write Twin Brother**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Twin Brother (card 170, adviser-only), WHEN PLAYED: you may swap this
  * card with a faceup nomad adviser of another player.
  *
  * It fires only when played faceup, and card play places the card before
  * the hook, so it is the actor's faceup adviser when this runs. The
  * candidates are every other player's faceup nomad denizen advisers that
  * are not locked, read live. With none, nothing is asked. Otherwise the
  * actor picks one or keeps Twin Brother. The `Swap` exchanges the two cards
  * between the play areas. Each keeps its orientation and the tokens on it,
  * so both stay faceup and carry their favor and secrets.
  *
  * The effect is one `Branch`, so the node count this power adds to the
  * card-played window never depends on live state. Nothing between the
  * question and the swap changes the candidates, so the `Branch` selects the
  * same children on resume.
  */
final case class TwinBrother private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import TwinBrother._
  def id: PowerId = TwinBrother.id

  override def noteKeys: Vector[NoteKey] = Vector(swapped)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = candidates(live, actor)
      if found.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          found.map(c => DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))) :+
            DecisionOption.Button(keep, "Keep Twin Brother"),
          heading = Some("Twin Brother: swap it for another player's faceup " +
            "nomad adviser?"))),
        BuildOps((ready, pending) => swap(ready, actor, pending)),
        Note(id, swappedNote(actor)))))

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    for
      held <- ready.game.current.players if held.player != actor
      card <- held.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id }
      definition <- catalog.denizen(card).toVector
      if definition.suit == Suit.Nomad && !Locked(definition.restrictions)
    yield Candidate(held.player, card)

  private def swap(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match
      case Some(`keep`) => Right(Vector.empty)
      case Some(DecisionOptionRef.Denizen(target)) =>
        candidates(ready, actor).find(_.card == target).toRight(OathViolation
          .InvalidEventOrder(s"${target.value} is not a nomad adviser Twin " +
            "Brother can swap with")).map(chosen => Vector(Swap(cardId,
          PositionedLocation(Location.PlayArea(actor)), chosen.card,
          PositionedLocation(Location.PlayArea(chosen.owner)))))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a Twin Brother choice"))
      case None => Left(OathViolation.InvalidEventOrder(
        "no Twin Brother choice is recorded"))

  /** Written only when the actor now holds the chosen card. */
  private def swappedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    case DecisionOptionRef.Denizen(target) <-
      NoteSupport.answer(states, decisionId)
    (before, after) <- states.previous
    owner <- before.game.current.players
      .find(_.advisers.exists(_.id == target)).map(_.player)
    if after.game.current.players.find(_.player == actor)
      .exists(_.advisers.exists(_.id == target))
  yield swapped(PowerSourceRef.Card(cardId), NoteArg.Player(actor),
    NoteArg.Player(owner), NoteArg.Card(target))

object TwinBrother:
  val id: PowerId = PowerId("denizen.twin-brother")
  val decisionId: String = "cardplay.twin-brother.swap"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** "{Red} swapped it for {Blue}'s {card}." */
  val swapped: NoteKey = NoteKey("swapped", Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it for "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(".")))

  private val Locked: Set[CardRestrictions] =
    Set(CardRestrictions.Locked, CardRestrictions.LockedAdviserOnly)

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  def forCatalog(catalog: ExecutableCatalog): Option[TwinBrother] =
    WhenPlayedPower.cardOf(catalog, id).map(new TwinBrother(_, catalog))
```

If the `case ... <-` pattern in `swappedNote` does not compile, use `ref <- NoteSupport.answer(...)` followed by `target <- Some(ref).collect { case DecisionOptionRef.Denizen(id) => id }`.

- [ ] **Step 5: Register both**

In `WalkerPowerCatalog.default`, after `FamilyHeirloom.forCatalog(catalog).toVector ++`, add:

```scala
      ShiftingFog.forCatalog(catalog).toVector ++
      TwinBrother.forCatalog(catalog).toVector ++
```

Add `ShiftingFog` and `TwinBrother` to that file's `whenplayed` import.

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly *ShiftingFogSuite *TwinBrotherSuite *PowerNoteCatalogSuite"`
Expected: PASS.

If `Swap` is refused when it moves an adviser between play areas, read `CardMovementOperations` and fix the refusal only if it is not a rule. Otherwise record a ruling and use two `Move`s instead.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFog.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/ShiftingFogSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/TwinBrotherSuite.scala
git commit -m "feat(powers): Shifting Fog rotates the favor banks; Twin Brother swaps with a nomad adviser"
```

---

### Task 3: The title-change window (N7) and Chaos Cult

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Modify: `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/title/ChaosCult.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/title/ChaosCultSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Produces:
  - `PowerWindow.OathkeeperTitleChange`;
  - `ChaosCult.forCatalog`, `ChaosCult.cardId`, `ChaosCult.took`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/title/ChaosCultSuite.scala`:

```scala
package oathdigital.gameplay.powers.title

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.powers.{NoteText, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ChaosCultSuite extends munit.FunSuite:
  import TargetsFixture.{giveAdviser, updatePlayer}

  private val power = ChaosCult.forCatalog(catalog).get
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(power)))
  private val active = base.game.current.turn.activePlayer
  private val cultist = players.find(_ != active).get
  private val leader = players.find(p => p != active && p != cultist).get

  private def favor(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player).get.board.favor

  /** `cultist` holds Chaos Cult (faceup unless told otherwise); `owners`
    * rule the sites; every player holds 2 favor. */
  private def staged(owners: Vector[Option[PlayerId]],
      orientation: Orientation = Orientation.FaceUp,
      holder: Option[PlayerId] = None): ReadyGame =
    val ruled0 = inPhase(ruled(base, owners, holder = holder), Phase.Act)
    players.foldLeft(giveAdviser(ruled0, cultist, power.cardId, orientation))(
      (ready, p) => updatePlayer(ready, p)(s => s.copy(board =
        s.board.copy(favor = 2))))

  private def travel(ready: ReadyGame) =
    val pawn = ready.game.current.players.find(_.player == active).get.pawnSite.get
    val to = ready.game.current.map.inPlay.find(_ != pawn).get
    rules.startWalker(Ready(ready), ActionRef.Travel, active, Vector.empty,
      Vector(DecisionOptionRef.Site(to)))

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  test("Chaos Cult is in the default walker catalog and automatic"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
    assertEquals(power.resolution, PowerResolution.Automatic)

  test("when another player takes the title, the holder takes 1 favor from them"):
    val ready = staged(Vector(Some(leader)))
    val done = travel(ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(leader))
    assertEquals(favor(after, cultist), 3)
    assertEquals(favor(after, leader), 1)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("took", s"${cultist.value} took 1 favor from " +
        s"${leader.value}.", covers = false)))
    replays(ready, done.events, done.state)

  test("the holder taking the title takes nothing"):
    val done = travel(staged(Vector(Some(cultist)))).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(cultist))
    assertEquals(favor(after, cultist), 2)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a new Oathkeeper with no favor gives nothing and nothing is written"):
    val ready = updatePlayer(staged(Vector(Some(leader))), leader)(s =>
      s.copy(board = s.board.copy(favor = 0)))
    val done = travel(ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)
    assert(!done.events.exists {
      case step: WalkerStepRecorded => step.ops.exists(_.isInstanceOf[Take])
      case _ => false
    })
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a facedown Chaos Cult takes nothing"):
    val done = travel(staged(Vector(Some(leader)), Orientation.FaceDown))
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)

  test("after a tie the chosen Oathkeeper is taken from, once answered"):
    val ready = staged(Vector(Some(active), Some(leader)), holder = Some(cultist))
    val parked = travel(ready).toOption.get
    val done = rules.resolveWalker(parked.state, cultist,
      oathdigital.gameplay.oathkeeper.OathkeeperProcedure.recipientDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(leader)))
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(leader))
    assertEquals(favor(after, cultist), 3)
    replays(ready, parked.events ++ done.events, done.state)
```

In `PowerKindsCatalogSuite`, add after the Vow of Obedience test:

```scala
  test("Chaos Cult fires on every title change whatever its flag"):
    assertEquals(flag("denizen.chaos-cult"), Some(false))
    assertEquals(oathdigital.gameplay.powers.title.ChaosCult
      .forCatalog(catalog).get.resolution, PowerResolution.Automatic)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly *ChaosCultSuite"`
Expected: compilation failure, because `ChaosCult` is not found.

- [ ] **Step 3: Add the window and put the procedure in it**

In `src/main/scala/oathdigital/model/PowerWindow.scala`, add after `ConspiracyTargetSelection`:

```scala
  /** The Oathkeeper title changing hands (catalog batch 2, N7). A power
    * appends what happens after the change: Chaos Cult's take. */
  case object OathkeeperTitleChange extends OtherWindow:
    val key = "oathkeeper.title-change"
```

In `OathkeeperProcedure.build`, give both `Sequence`s the window:

- `Right(Sequence(Vector(SetOathkeeper(holder)), Some(PowerWindow.OathkeeperTitleChange)))` for `Transfer`;
- `Right(Sequence(Vector(Decide(...), BuildOps(...)), Some(PowerWindow.OathkeeperTitleChange)))` for `Choose`.

Replace the doc's tree and its last sentence:

```scala
  * {{{
  * Transfer(holder)  -> Sequence(SetOathkeeper(holder))              // window = OathkeeperTitleChange
  * Choose(holder, c) -> Sequence(Decide(recipient, owner = holder),
  *                        BuildOps(SetOathkeeper))                  // window = OathkeeperTitleChange
  * }}}
  *
  * A single leader is a forced choice, so it omits the `Decide` and applies the
  * transfer itself. `build` is also `rebuild`: only resume commands are
  * accepted while this parks, so the outcome cannot change under it. The
  * window (catalog batch 2, N7) lets a power append what follows a change
  * of holder, after the change: every applied `SetOathkeeper` changes the
  * holder, since the operation rejects an unchanged one.
```

- [ ] **Step 4: Write Chaos Cult**

Create `src/main/scala/oathdigital/gameplay/powers/title/ChaosCult.scala`:

```scala
package oathdigital.gameplay.powers.title

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

/** Chaos Cult (card 101, adviser-only), a rule of a faceup adviser: "After
  * another player takes the Oathkeeper title, you take [favor] from them."
  *
  * A `Transform` on the title-change window (catalog batch 2, N7) appends a
  * take after the change. The take reads the new holder live, so it runs
  * after the `SetOathkeeper` of either the forced transfer or the holder's
  * choice. It takes 1 favor with `Take` when the new holder is another
  * player with favor, and nothing otherwise. The appended nodes depend only
  * on who holds Chaos Cult faceup, which a title change does not alter, so
  * the fold is the same while the choice is parked.
  *
  * The card is catalogued `persistent: false`, so its resolution stays the
  * trait default: it fires on every title change.
  */
final case class ChaosCult private (cardId: DenizenId)
    extends ContributingPower:
  import ChaosCult._
  def id: PowerId = ChaosCult.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(took)

  override def applicable(ctx: PowerCtx): Boolean = holderOf(ctx.state).nonEmpty

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.OathkeeperTitleChange -> Vector(Transform((ctx, children) =>
      holderOf(ctx.state).fold(children)(holder => children ++ Vector(
        BuildOps((live, _) => Right(take(live, holder))),
        Note(id, tookNote(holder)))))))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  private def take(ready: ReadyGame, holder: PlayerId): Vector[CoreOperation] =
    ready.game.current.title.holder.filter(_ != holder)
      .flatMap(taker => ready.game.current.players.find(_.player == taker))
      .filter(_.board.favor > 0).toVector.map(from => Take(Piece.Favor(Favor),
        holder, Location.PlayArea(from.player), Location.PlayArea(holder)))

  /** What the holder gained in the take step, from the new Oathkeeper. */
  private def tookNote(holder: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    amount = NoteSupport.favor(step, holder)
    if amount > 0
    from <- step._2.game.current.title.holder
  yield took(PowerSourceRef.Card(cardId), NoteArg.Player(holder),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(from))

object ChaosCult:
  val id: PowerId = PowerId("denizen.chaos-cult")
  val Favor: Int = 1
  /** "{Red} took {1 favor} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): Option[ChaosCult] =
    CatalogCards.denizen(catalog, id).map(new ChaosCult(_))
```

In `WalkerPowerCatalog.default`, add `ChaosCult.forCatalog(catalog).toVector ++` after the Twin Brother line. Import `oathdigital.gameplay.powers.title.ChaosCult`.

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly *ChaosCultSuite *PowerKindsCatalogSuite *OathkeeperProcedureSuite *OathkeeperRulesSuite *PowerNoteCatalogSuite"`
Expected: PASS. The Oathkeeper suites must pass unchanged: with no applicable power, the window adds nothing.

If `ruled`'s single leader does not produce `Transfer(leader)` from a `None` holder here, read `OathkeeperRules.outcome` and stage the leaders it needs, as `OathkeeperProcedureSuite` does.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala src/main/scala/oathdigital/gameplay/powers/title/ChaosCult.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/title/ChaosCultSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): a title-change window, where Chaos Cult takes favor from a new Oathkeeper"
```

---

### Task 4: Forced Wake steps (N8) and Hunger

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/Hunger.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/wake/HungerSuite.scala`
- Test: `src/test/scala/oathdigital/model/EnumShapeSuite.scala`

**Interfaces:**
- Consumes: `DecisionOptionRef.AdviserSlot` (Task 1), `Bury.standard`, `PhasePowerProcedure.sources`, `OathRulesWalker.startTriggered`.
- Produces:
  - `PhasePower.forced: Boolean`;
  - `TriggeredProcedureRef.ForcedWake` (key `forced-wake`);
  - `ForcedWakeProcedure.due(catalog, ready, player, powers)`, `ForcedWakeProcedure.build(powers)`;
  - `Hunger.forCatalog`, `Hunger.decisionId`, `Hunger#cardId`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/wake/HungerSuite.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.OathState.Ready

class HungerSuite extends munit.FunSuite:
  import PowerFixture.{actor, base, player}
  import TargetsFixture.{giveAdviser, updatePlayer, withPawn}

  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val rules = new OathRules(catalog, phasePowerCatalog = phasePowers)
  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = phasePowers)
  private val hunger = Hunger.forCatalog(catalog).get
  private val card = hunger.cardId
  private val next = FinishRestProcedure.turnOrder(base)(1)
  private val third = base.game.current.players.map(_.player)
    .find(p => p != actor && p != next).get

  /** The actor in Act. `next` holds Hunger (faceup unless told otherwise)
    * and shares its site with `third`, whose starting adviser carries 1
    * favor and 2 secrets. */
  private def staged(orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val act = base.updateCurrent(_.copy(turn = TurnState(actor, Phase.Act,
      Set.empty)))
    val site = player(act, next).pawnSite.get
    updatePlayer(withPawn(giveAdviser(act, next, card, orientation), third,
      site), third)(p => p.copy(advisers = p.advisers.zipWithIndex.map {
        case (d: DenizenState, 0) => d.copy(tokens = Tokens(1, 2))
        case (other, _) => other }))

  private def rested(ready: ReadyGame) = rules.startWalker(Ready(ready),
    PhaseTransitionRef.BeginRest, actor).toOption.get

  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  private def bury(from: OathTransition, slot: DecisionOptionRef) =
    rules.resolveWalker(from.state, next, Hunger.decisionId,
      DecisionAnswer.ChooseOneAnswer(slot))

  test("Hunger is a registered, forced WAKE power"):
    assert(phasePowers.find(Hunger.id).isDefined)
    assert(hunger.forced)
    assertEquals(hunger.timing, PowerTiming.Wake)

  test("the waking holder must answer Hunger before anything else, choosing " +
      "among the advisers at their site but not Hunger"):
    val t = rested(staged())
    parked.assertParked(t.state, TriggeredProcedureRef.ForcedWake,
      Hunger.decisionId, next)
    val now = ready(t.state)
    assertEquals(now.game.current.turn, TurnState(next, Phase.Wake, Set.empty))
    val here = player(now, next).pawnSite
    val expected = now.game.current.players.filter(_.pawnSite == here)
      .map(_.player).flatMap(owner =>
      player(now, owner).advisers.zipWithIndex.collect {
        case (held, slot) if held.id != card =>
          DecisionOptionRef.AdviserSlot(owner, slot)
      })
    assertEquals(parked.parkedDecision(t.state).map(_.decision),
      Some(Hunger.decisionId))
    assertEquals(TargetsFixture.offered(t, next).map(_.map(_._1).distinct),
      Some(Vector("adviser-slot")))
    assertEquals(TargetsFixture.offered(t, next).map(_.size), Some(expected.size))
    assert(rules.startWalker(t.state, PhaseTransitionRef.EndWake, next).isLeft)
    assert(PhasePowerProcedure.usable(catalog, now, next, phasePowers)
      .forall(_.power.id != Hunger.id))

  test("burying another player's adviser returns its favor and gives its " +
      "secrets to the holder"):
    val before = staged()
    val t = rested(before)
    val victim = player(ready(t.state), third).advisers.head
    val suit = catalog.suitOf(victim.id).get
    val bank = ready(t.state).banks.favor(suit)
    val secrets = player(ready(t.state), next).board
    val done = bury(t, DecisionOptionRef.AdviserSlot(third, 0)).toOption.get
    val after = ready(done.state)
    assert(!player(after, third).advisers.exists(_.id == victim.id))
    assertEquals(after.game.current.commonCards.worldDeck.last, victim.id)
    assertEquals(after.banks.favor(suit), bank + 1)
    val gained = player(after, next).board
    assertEquals(gained.faceUpSecrets + gained.faceDownSecrets,
      secrets.faceUpSecrets + secrets.faceDownSecrets + 2)
    assert(done.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assertEquals(NoteText.said(hunger, done.events), Vector(NoteText.Said(
      "buried", s"${next.value} buried ${third.value}'s ${victim.id.value}.",
      covers = true)))
    assertEquals(t.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(before)))((s, e) => s.flatMap(rules.evolve(_, e)))
      .flatMap(s => done.events.foldLeft[Either[OathViolation, OathState]](
        Right(s))((s, e) => s.flatMap(rules.evolve(_, e)))), Right(done.state))

  test("a slot that is not offered is rejected"):
    val t = rested(staged())
    assert(bury(t, DecisionOptionRef.AdviserSlot(actor, 0)).isLeft)

  test("with nothing to bury, Hunger says so and Wake goes on"):
    val ready = staged()
    val site = player(ready, next).pawnSite
    val elsewhere = ready.game.current.map.inPlay.find(s => !site.contains(s)).get
    val moved = Vector(actor, third).foldLeft(ready)((r, p) =>
      withPawn(r, p, elsewhere))
    val alone = updatePlayer(moved, next)(p =>
      p.copy(advisers = p.advisers.filter(_.id == card)))
    val t = rested(alone)
    parked.assertResumed(t.state, Phase.Wake, next)
    assertEquals(NoteText.said(hunger, t.events), Vector(NoteText.Said(
      "none", "No adviser could be buried.", covers = true)))

  test("a facedown Hunger does nothing"):
    val t = rested(staged(Orientation.FaceDown))
    parked.assertResumed(t.state, Phase.Wake, next)
    assert(!t.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))

  test("Hunger cannot be used as an optional power"):
    val t = rested(staged(Orientation.FaceDown))
    val faceup = ready(t.state).updateCurrent(c => c.copy(players =
      c.players.map(p => if p.player != next then p else p.copy(advisers =
        p.advisers.map {
          case d: DenizenState if d.id == card =>
            d.copy(orientation = Orientation.FaceUp)
          case other => other }))))
    assert(rules.startWalker(Ready(faceup), ActionRef.UsePower(Hunger.id), next,
      Vector.empty, Vector(DecisionOptionRef.Denizen(card))).isLeft)
```

In `EnumShapeSuite`, change the triggered keys to `Vector("oathkeeper", "setup", "forced-wake")`.

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly *HungerSuite"`
Expected: compilation failure, because `Hunger` is not found.

- [ ] **Step 3: Mark forced powers and keep them out of optional use**

In `PhasePower.scala`, add to the trait after `timing`:

```scala
  /** A power its holder must use (catalog batch 2, N8): it runs on its own
    * at the start of its phase and is never offered or accepted as an
    * optional use. Only WAKE powers are forced today. */
  def forced: Boolean = false
```

In `PhasePowerProcedure.check`, add after the phase-timing gate:

```scala
      _ <- Either.cond(!power.forced, (), InvalidEventOrder(
        s"${power.id.value} runs on its own at the start of its phase"))
```

In `PhasePowerProcedure.usable`, change the timing guard to:

```scala
        if power.forced || !timingOf(current.turn.phase).contains(power.timing)
        then Vector.empty
```

- [ ] **Step 4: The forced Wake procedure**

In `ProcedureRef.scala`, add to `TriggeredProcedureRef`:

```scala
  /** The forced Wake powers of the player whose Wake begins (catalog batch 2,
    * N8). The turn boundary starts it; nothing else may run until it ends. */
  case ForcedWake extends TriggeredProcedureRef("forced-wake")
```

Add `ForcedWake` to `TriggeredProcedureRef.all`.

Create `src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala`:

```scala
package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.model._

/** Forced Wake steps (catalog batch 2, N8).
  *
  * {{{
  * Sequence(power1.build(...), power2.build(...), ...)
  * }}}
  *
  * Every forced WAKE power the waking player can access, in catalog order,
  * each asking its own decision. The turn boundary starts this as a
  * triggered procedure after the Wake checks, so the player can do nothing
  * else until it ends: a parked walker refuses End Wake and every optional
  * power.
  *
  * `build` is also `rebuild`. The list is read from the state the walk
  * resumes in, so a forced power must not change which forced powers are
  * due before its own last decision. Hunger, the only one, asks before it
  * changes anything, and cannot bury itself.
  */
object ForcedWakeProcedure:
  def due(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[(PhasePower, DecisionOptionRef)] =
    powers.powers.filter(power => power.forced &&
      power.timing == PowerTiming.Wake).flatMap(power =>
      PhasePowerProcedure.sources(catalog, ready, player, power).collect {
        case (_, ref) if power.usable(ready, player, ref) => power -> ref
      })

  def build(powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "the forced Wake step selects nothing"))
    found = due(catalog, ready, player, powers)
    _ <- Either.cond(found.nonEmpty, (), OathViolation.InvalidEventOrder(
      "no forced Wake power is due"))
    trees <- found.foldLeft[Either[OathViolation, Vector[Operation]]](
      Right(Vector.empty)) { case (built, (power, source)) =>
      built.flatMap(done => power.build(ready, player, source).map(done :+ _))
    }
  yield Sequence(trees)
```

In `WalkerProcedureRegistry`:

1. Import `oathdigital.gameplay.phases.wake.ForcedWakeProcedure` (extend the existing `phases.wake` import).
2. In `lookup`, add after the `UsePower` case:

```scala
      case TriggeredProcedureRef.ForcedWake => Right(forcedWakeEntry(powers))
```

3. Add beside `usePowerEntry`:

```scala
  /** The forced Wake step reads the phase powers, as `UsePower` does. It is
    * started by the turn boundary, so it has no fallback kind, no modifier
    * window and no start selection. */
  private def forcedWakeEntry(powers: PhasePowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = ForcedWakeProcedure.build(powers),
    rebuild = ForcedWakeProcedure.build(powers))
```

4. In `isRegistered`, add `|| procedure == TriggeredProcedureRef.ForcedWake`.

In `OathRulesWalker.buildWalker`, change both `UsePower` cases to also match the forced step:

```scala
      case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake if starting =>
        WalkerProcedureRegistry.build(
          procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
      case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake =>
        WalkerProcedureRegistry.rebuild(
          procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
```

In `WalkerDecisionProjector.tree`, change `case _: ActionRef.UsePower =>` to `case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake =>`.

In `OathRules`:

1. Import `oathdigital.gameplay.phases.wake.ForcedWakeProcedure`.
2. At the end of `enterWake`, append `.flatMap(forcedWake)` to the `prepared.flatMap(...)` expression.
3. Add:

```scala
  /** The forced Wake powers of the player whose Wake begins (catalog batch
    * 2, N8), after the Usurper and Vision checks and only while the game
    * goes on. Setup keeps every starting adviser facedown, so the first Wake
    * of a game, which does not pass here, has none. */
  private def forcedWake(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.isEmpty &&
        ready.game.current.turn.phase == Phase.Wake &&
        ForcedWakeProcedure.due(catalog, ready,
          ready.game.current.turn.activePlayer, phasePowerCatalog).nonEmpty =>
      startTriggered(transition, TriggeredProcedureRef.ForcedWake)
    case _ => Right(transition)
```

In `ActionLines.lines`, add before the `TriggeredProcedureRef.Setup` case:

```scala
      // Each forced power writes its own lines.
      case TriggeredProcedureRef.ForcedWake => Vector.empty
```

For any other non-exhaustive match over `TriggeredProcedureRef` or `ProcedureRef` that the compiler names, add a case beside its `Oathkeeper` case.

- [ ] **Step 5: Write Hunger**

Create `src/main/scala/oathdigital/gameplay/powers/wake/Hunger.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PowerAnswers}
import oathdigital.model._

/** Hunger (card 216, adviser-only, locked), WAKE: you must bury an adviser
  * held by a player whose pawn is at your site, even yourself, but cannot
  * bury this card.
  *
  * A forced power (catalog batch 2, N8): the forced Wake step runs it at the
  * start of its holder's Wake. The candidates are every adviser, denizen or
  * Vision, in either orientation, of every player at the holder's site, the
  * holder included and Hunger excluded, in seat order. They are offered as
  * adviser slots, so another player's facedown adviser is not disclosed.
  * `Bury` ignores locked, so a locked adviser is a candidate. The question
  * is asked whenever there is one. The burial uses the standard returns:
  * favor to the card's suit bank, secrets to Hunger's holder facedown. With
  * no candidate, nothing is buried and the line says so.
  *
  * Its line covers the generic "Buried" line.
  */
final case class Hunger private (cardId: DenizenId, catalog: ExecutableCatalog)
    extends PhasePower:
  import Hunger._
  def id: PowerId = Hunger.id
  def timing: PowerTiming = PowerTiming.Wake
  override def forced: Boolean = true

  override def noteKeys: Vector[NoteKey] = Vector(buried, spared)

  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)),
    Note(id, buriedNote(_, player), covers = true))))

  private def candidates(ready: ReadyGame, holder: PlayerId): Vector[Candidate] =
    val site = PowerAccess.pawnSite(ready, holder)
    for
      owner <- ready.game.current.players
      if site.nonEmpty && owner.pawnSite == site
      (held, slot) <- owner.advisers.zipWithIndex
      if held.id != cardId
    yield held match
      case d: DenizenState => Candidate(owner.player, slot,
        BuryableCard.Denizen(d.id), d.tokens)
      case v: VisionState => Candidate(owner.player, slot,
        BuryableCard.Vision(v.id), Tokens.empty)

  private def ask(ready: ReadyGame, holder: PlayerId): Vector[Operation] =
    val found = candidates(ready, holder)
    if found.isEmpty then Vector.empty
    else Vector(Decide(decisionId, holder, DecisionQuery.ChooseOne(
      found.map(c => DecisionOption.AdviserSlot(c.ref)),
      heading = Some("Hunger: bury an adviser at your site"))))

  private def bury(ready: ReadyGame, holder: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = candidates(ready, holder)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      chosen <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not an adviser Hunger can bury"))
      suit = catalog.suitOf(chosen.card.id)
      _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
        OathViolation.InvalidEventOrder(
          s"no suit is known for ${chosen.card.id.value}"))
    yield Bury.standard(chosen.card,
      PositionedLocation(Location.PlayArea(chosen.owner)), suit,
      chosen.tokens.favor, chosen.tokens.secrets, holder)

  /** The chosen adviser, read where it stood before the bury step. No
    * answer means nothing could be buried. */
  private def buriedNote(states: NoteStates, holder: PlayerId)
      : Option[PowerNote] =
    val card = PowerSourceRef.Card(cardId)
    NoteSupport.answer(states, decisionId) match
      case None => Some(spared(card))
      case Some(ref) => for
        step <- states.previous
        chosen <- candidates(step._1, holder).find(_.ref == ref)
      yield buried(card, NoteArg.Player(holder), NoteArg.Player(chosen.owner),
        NoteArg.Card(chosen.card.id))

object Hunger:
  val id: PowerId = PowerId("denizen.hunger")
  val decisionId: String = "power.hunger.adviser"
  /** "{Red} buried {Blue}'s {card}." */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No adviser could be buried." */
  val spared: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No adviser could be buried.")))

  private final case class Candidate(owner: PlayerId, slot: Int,
      card: BuryableCard, tokens: Tokens):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  def forCatalog(catalog: ExecutableCatalog): Option[Hunger] =
    CatalogCards.denizen(catalog, id).map(new Hunger(_, catalog))
```

In `PhasePowerCatalog.default`, add `Hunger.forCatalog(catalog).toVector ++` before `RiverSitePower.forCatalog(catalog)`, and import `Hunger` with `MarbleFountains`.

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly *HungerSuite *EnumShapeSuite *PhasePowerSuite *RestWalkerSuite *EndWakeProcedureSuite *GameStartToWakeSuite *PowerNoteCatalogSuite *WalkerProcedureRegistrySuite"`
Expected: PASS.

If `rested` does not reach `next`'s Wake because Begin Rest stops for a usable REST power, finish Rest explicitly with `PhaseTransitionRef.FinishRest`, as `RestWalkerSuite` does.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala src/main/scala/oathdigital/model/ProcedureRef.scala src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala src/main/scala/oathdigital/gameplay/OathRulesWalker.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/gameplay/OathRules.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/gameplay/powers/wake/Hunger.scala src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/wake/HungerSuite.scala src/test/scala/oathdigital/model/EnumShapeSuite.scala
git commit -m "feat(powers): forced Wake steps, where Hunger buries an adviser at its holder's site"
```

---

### Task 5: Hunger's log, the status pin and the roadmap

**Files:**
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Create: `src/test/resources/gamelog/hunger.actor.log`, `src/test/resources/gamelog/hunger.other.log`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Add the script and the tests**

In `LogScripts.scala`:

1. Import `oathdigital.gameplay.phases.rest.FinishRestProcedure` and `oathdigital.gameplay.powers.wake.Hunger`.
2. Add after `barbedNet`:

```scala
  /** Hunger faceup with the second player, whose Wake begins when the first
    * player rests. The forced step parks for them. Pawns are spread, so the
    * only candidate is their own starting adviser, which the default answer
    * buries. The script's actor is Hunger's holder. */
  def hunger(using munit.Location): Script =
    val card = DenizenId("216")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("hunger")
    val woken = Situation.wake(driver, chronicle, orders)
    val first = active(woken)
    val holder = FinishRestProcedure.turnOrder(woken.ready)(1)
    woken.after(Step.Arrange(Vector(ParkedServiceFixture.topOfWorldDeck(card,
        Location.PlayArea(holder)))),
      GameCommand.EndWake(first), GameCommand.BeginRest(first))
    Script("hunger", service, holder)
```

3. Add `"hunger" -> (() => hunger)` to `named`.

If `Hunger` is unused in the script, do not import it.

In `GameLogPowerLinesSuite`, add:

```scala
  test("Hunger's forced step writes one trigger line that covers the burial, " +
      "naming the card to whoever may identify it"):
    val script = hunger
    val holder = name(script.actor)
    val other = script.players.find(_ != script.actor).get
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val at = all.indexWhere(_.startsWith(s"Hunger: $holder buried $holder's "))
      assert(at >= 0, all)
      assertEquals(shown(at).kind, LogKind.Trigger)
      assert(!all.exists(_.startsWith("Buried ")), all)
      assert(!all.exists(_.startsWith("Used Hunger")), all)
      assertEquals(all(at).endsWith("a Denizen."), viewer != script.actor, all(at))
    }
```

In `PowerImplementationStatusSuite`, add:

```scala
  test("catalog batch 2's triggers and When Played powers are implemented"):
    Vector("denizen.shifting-fog", "denizen.twin-brother",
      "denizen.chaos-cult", "denizen.hunger")
      .foreach(id => assert(implemented(PowerId(id)), id))
```

The file has no trailing newline. Keep it that way, or add one. Either is fine.

- [ ] **Step 2: Write and review the golden logs**

Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly *GameLogGoldenSuite"`

Read both new files:

- `hunger.actor.log` shows the second player's turn, then "Chose {own}'s {card}", then "[card:216|Hunger]: {holder} buried {holder}'s [card:…|…]."
- `hunger.other.log` shows the same lines with "a Denizen" or "facedown adviser (slot 1)" wherever the card would be named.
- Neither shows "Buried".

Check that `git status` changed no other golden log.

- [ ] **Step 3: Run the suites to verify they pass**

Run: `./sbtw "testOnly *GameLogGoldenSuite *GameLogPowerLinesSuite *PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 4: Record the slice in the roadmap**

In `docs/ROADMAP.md`:

1. Record catalog batch 2's slice 5 as done, naming Shifting Fog, Hunger, Twin Brother and Chaos Cult, with the title-change window (N7) and forced Wake steps (N8). Record that batch 2 is complete.
2. Mark the "an adviser-slot decision option" item done. Replace its body with one sentence: Hunger and Ivory Eye offer `AdviserSlot`s.

Match the wording and form of the slice 4 entry.

- [ ] **Step 5: Run the gates**

1. Run `./sbtw "test" "frontend/test"` and compare the server count against the baseline.
2. Run `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

- [ ] **Step 6: Commit**

```bash
git add src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/resources/gamelog/hunger.actor.log src/test/resources/gamelog/hunger.other.log src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 5"
```
