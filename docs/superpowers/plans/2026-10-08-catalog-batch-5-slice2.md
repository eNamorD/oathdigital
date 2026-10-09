# Catalog Batch 5, Slice 2 (Vow of Wandering) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Vow of Wandering (247): every Muster of its holder gains two more warbands, and its holder cannot place warbands on sites.

**Architecture:**

- **Two powers in one file, `gameplay/powers/economy/VowOfWandering.scala`.** The card prints two powers, and each is registered under its own id, so both count as implemented.
  - `VowOfWanderingMuster` (`denizen.vow-of-wandering.muster`) adds two warbands at `MusterGain`.
  - `VowOfWandering` (`denizen.vow-of-wandering`) has two parts. Its operation restriction refuses any placement of the holder's warbands on a site. A Transform at `CampaignPlacement` replaces the Conquest placement question with a line.
- **A shared helper, `ExtraWarbands`,** adds a vow's warbands to the Muster's gain. Vow of Beastkin moves onto it. Each vow's line is capped at its own count, so a Muster under both vows writes each vow's own part.
- **The restriction is a backstop of kind `Impossible`.**
  - An `Impossible` refusal skips an optional placement and never fails a walk.
  - The search hides an option only when its dry walk fails, so a skipped step hides nothing.
  - Each power that places warbands therefore reads `VowOfWandering.holds` itself, to write the Vow's line or to offer only what is legal: Garrison, Key to the City, Bandit Prince, Ballot Box, Hospital, Messenger and Warning Signals.
- **The minor action reports a skipped operation's reason** as its refusal, in `MinorActions.evolveOperations`. The product owner approved this change outside `gameplay/powers/` at plan time.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The frontend gets no change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`: "The bar", "Slice 2: Vow of Wandering", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`: "Rules that apply to the whole batch" and "Slice 2: Warband placement".

Task 1 amends both with the rulings made at plan time, so read them after Task 1 too. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Log lines omit the actor.** A power's line names only other players, or a player acting off turn.
- **The Vow's line and reason, exactly:**
  - Line: "Vow of Wandering forbids placing warbands on sites."
  - Restriction detail and minor action refusal: "Vow of Wandering forbids placing warbands on sites".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. This plan adds 22 server tests and no frontend test. It also rewrites one assertion in `MinorActionsSuite`.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled these on 2026-10-08, after mapping showed that the spec's "an option it refuses is hidden by the search" does not hold. Task 1 writes them into the spec.

1. **Every source counts.** The ban covers any arrival of the holder's warbands on a site: from the board, from the bank (Hospital, and the `Replace` of Bandit Prince and Ballot Box) or from another site.
2. **Whose warbands.** Warbands are the holder's when they leave the holder's board, or when they are of the holder's kind. Imperial warbands are shared by the Chancellor and the Citizens. They count only while the holder is the active player.
3. **How each path shows it.** The refusal is `Impossible`, and each power handles the Vow itself:
   - Garrison gains its warbands. It does not ask for sites, places none and writes the Vow's line.
   - Key to the City kills and gains as usual. It places nothing and writes the Vow's line instead of its placement line.
   - Bandit Prince does not ask, replaces nothing and writes the Vow's line. The walker never asks an optional question whose every option is unavailable, so the line takes the place of the hover reasons.
   - Hospital saves nothing: the holder's dead stay killed.
   - Messenger and Warning Signals cap each site at what it holds now, so the holder can only bring warbands back to the board.
4. **Ballot Box removes them all.** None of the warbands at the site can be replaced, so "remove any that can't be replaced" removes every one. The Vow's line comes first.
5. **The minor action names the reason.** A move to the site is refused with `MinorActionUnavailable("Vow of Wandering forbids placing warbands on sites")`. The client still offers the move: the projector is not changed.

Also settled at plan time:

6. **Conquest's line:** "Kept {n} surviving warbands on the board.", written under Vow of Wandering. `{n}` is the survivors the question would have offered.
7. **The Vow's line under other powers** is one shared key, `VowOfWandering.forbidden` (name `vow-of-wandering`). Each power that writes it lists it in its own `noteKeys`.

## File Structure

Production, under `src/main/scala/oathdigital/` unless stated:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/powers/economy/ExtraWarbands.scala` | create | a vow's warbands on the Muster's gain, and its line | 2 |
| `gameplay/powers/economy/VowOfBeastkin.scala` | modify | uses `ExtraWarbands` | 2 |
| `gameplay/powers/economy/VowOfWandering.scala` | create | the card, its Muster power, its rule (restriction and Conquest) | 2, 3 |
| `catalog/holding/HoldingNomadDenizens.scala` | modify | Vow of Wandering leaves | 2 |
| `gameplay/cards/NewFoundations.scala` | modify | imports the card from the economy package | 2 |
| `gameplay/powers/ActionModifiers.scala` | modify | registers both powers | 2, 3 |
| `gameplay/actions/MinorActions.scala` | modify | a skipped operation's reason refuses the minor action | 3 |
| `gameplay/powers/whenplayed/Garrison.scala` | modify | gains, then the Vow's line | 4 |
| `gameplay/powers/whenplayed/KeyToTheCity.scala` | modify | kills and gains, then the Vow's line | 4 |
| `gameplay/powers/whenplayed/BanditPrince.scala` | modify | the Vow's line in place of the question | 4 |
| `gameplay/powers/action/BallotBox.scala` | modify | removes every warband, after the Vow's line | 4 |
| `gameplay/powers/campaign/Hospital.scala` | modify | saves nothing for a holder | 4 |
| `gameplay/powers/WarbandArrangement.scala` | modify | `toSites` caps each site at what it holds | 5 |
| `gameplay/powers/action/Messenger.scala` | modify | passes `toSites` | 5 |
| `gameplay/powers/campaign/WarningSignals.scala` | modify | passes `toSites` | 5 |

Tests, under `src/test/scala/oathdigital/` unless stated:

| File | Change | Tests added | Task |
|---|---|---|---|
| `gameplay/powers/economy/VowOfWanderingSuite.scala` | create | 4, then 5 | 2, 3 |
| `gameplay/powers/economy/VowOfWanderingConquestSuite.scala` | create | 2 | 3 |
| `gameplay/PowerKindsCatalogSuite.scala` | modify | 1 (and a list entry) | 2, 3 |
| `gameplay/MinorActionsSuite.scala` | modify | 1 (1 rewritten) | 3 |
| `gameplay/powers/whenplayed/GarrisonSuite.scala` | modify | 2 | 4 |
| `gameplay/powers/whenplayed/KeyToTheCitySuite.scala` | modify | 1 | 4 |
| `gameplay/powers/whenplayed/BanditPrinceSuite.scala` | modify | 1 | 4 |
| `gameplay/powers/action/BallotBoxSuite.scala` | modify | 1 | 4 |
| `gameplay/powers/campaign/HospitalSuite.scala` | modify | 1 | 4 |
| `gameplay/powers/action/MessengerSuite.scala` | modify | 2 | 5 |
| `gameplay/powers/campaign/WarningSignalsSuite.scala` | modify | 1 | 5 |

Docs: the batch 5 design and rulings in Task 1; `docs/ROADMAP.md` in Task 6.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md` ("The bar"; "Slice 2: Vow of Wandering")
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md` ("Slice 2: Warband placement")

- [ ] **Step 1: Add the fifth approved change to "The bar"**

In the design, replace:

```markdown
inside `gameplay/powers/` are allowed. The product owner approved four
changes outside `gameplay/powers/`:
```

with:

```markdown
inside `gameplay/powers/` are allowed. The product owner approved five
changes outside `gameplay/powers/`:
```

After the bullet `- **A warband placement restriction**, an `OperationRestriction` that a power registers (slice 2).` (two lines), add:

```markdown
- **The minor action's refusal** names the reason of an operation a
  restriction skipped, in `MinorActions` (slice 2, approved at plan time).
```

- [ ] **Step 2: Replace the slice 2 section of the design**

Replace everything from `## Slice 2: Vow of Wandering` up to, not including, `## Slice 3: Adviser counting` with:

```markdown
## Slice 2: Vow of Wandering

- **The Muster gain** adds two warbands to the Muster's own gain at
  `MusterGain`, as Vow of Beastkin's does. It is best effort from the
  supply. Its line reads what the gain added beyond its amount before the
  Vow, at most two. A Muster under both Vows writes each Vow's own part.
- **The placement restriction** is an `OperationRestriction` the card
  registers through `ContributingPower.operationRestrictions`, as Lost
  Tongue does. While the card is a faceup adviser, the restriction refuses
  any `Move` that puts its holder's warbands on a site: from the board, from
  the bank or from another site. It reads the leaves of a composite, so a
  `Replace` is refused too. A move from a site to the board stays legal.
  - Warbands are the holder's when they leave the holder's board, or when
    they are of the holder's kind. The Chancellor and the Citizens share
    Imperial warbands, so Imperial ones count only while the holder is the
    active player.
  - The refusal is `Impossible`: an optional placement it refuses is
    skipped, and no walk fails because of it. The search hides an option
    only when its walk fails, so it hides nothing here. Each power that
    places warbands reads the Vow itself:
    - Garrison gains its warbands, does not ask for sites, and writes the
      Vow's line.
    - Key to the City kills and gains, and writes the Vow's line instead of
      its placement line.
    - Bandit Prince does not ask, and writes the Vow's line. The walker
      never asks an optional question whose every option is unavailable, so
      the line takes the place of the hover reasons.
    - Ballot Box can replace none of the warbands, so it removes them all,
      after the Vow's line.
    - Hospital saves nothing: the holder's dead stay killed.
    - Messenger and Warning Signals cap each site at what it holds, so the
      holder can only bring warbands back to the board.
  - The Vow's line, under the power it stopped: "Vow of Wandering forbids
    placing warbands on sites."
  - The minor action refuses a move to the site with the reason of the
    operation the restriction skipped: "Vow of Wandering forbids placing
    warbands on sites". The client still offers the move.
- **Campaign conquest** asks "place up to N survivors" with a
  `ChooseAmount`, which cannot carry a hover reason. A Transform on
  `CampaignPlacement` replaces the question with a line, so the survivors
  stay on the board: "Kept {n} surviving warbands on the board." The
  restriction still guards the placement.
```

- [ ] **Step 3: Replace the slice 2 ruling**

In the rulings, replace the `- **247 Vow of Wandering.**` entry (its whole bullet) with:

```markdown
- **247 Vow of Wandering.** Its Muster power adds two warbands to the
  Muster's gain, best effort from the supply. While it is a faceup adviser,
  its holder cannot place warbands on a site, from anywhere: the board, the
  bank or another site. Moving warbands from a site to the board stays
  legal. After a Conquest victory, the placement question is not asked and
  the survivors stay on the board, with a line saying why.
  - The holder's warbands are those leaving the holder's board, and those
    of the holder's kind. Imperial warbands, which the Chancellor and the
    Citizens share, count only while the holder is the active player.
  - Garrison and Key to the City still gain their warbands, and place none.
    Bandit Prince replaces nothing. Hospital saves nothing.
  - Ballot Box: none of the warbands can be replaced, so it removes them
    all.
  - Messenger and Warning Signals may only bring warbands back to the
    board.
  - The minor action refuses a move to the site, naming the Vow.
```

- [ ] **Step 4: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md
git commit -m "docs(spec): settle how Vow of Wandering stops each placement"
```

---

### Task 2: The Muster gain, shared with Vow of Beastkin

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala:99-106`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala:45-50`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Produces:
  - `private[economy] object ExtraWarbands` with `val gained: NoteKey` (name `gained`) and `def added(power: PowerId, card: DenizenId, extra: Int, actor: PlayerId, operations: Vector[Operation]): Vector[Operation]`.
  - `object VowOfWanderingCard` (id 247, `Locked`, `AdviserOnly`), with `muster` and `power`.
  - `case object VowOfWanderingMuster extends ContributingPower`, with `cardId`, `id` and `Warbands = 2`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}

class VowOfWanderingSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val beastkin = CatalogNames.denizen("Vow of Beastkin")
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen to Muster on. It matches no Nomad adviser. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Errand Boy, with 1 favor and 7 Supply to pay
    * a Muster, and no adviser yet. */
  private def besideErrandBoy: Table = Table.start.pawn(p1, at = home)
    .denizen(errandBoy, at = home).favor(p1, 1).supply(p1, 7)

  private def musteredOnErrandBoy(ready: ReadyGame)
      : EconomyActions.Completed = EconomyActions.muster(rules, ready, p1,
    Vector.empty, DecisionOptionRef.Denizen(errandBoy))

  private def gainedLine(n: Int): NoteText.Said = NoteText.Said("gained",
    s"Gained $n more ${if n == 1 then "warband" else "warbands"}.",
    covers = false)

  private def wanderingSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfWanderingMuster.id, VowOfWanderingMuster.noteKeys,
      events)

  test("every Muster gains two more warbands, and the line says so"):
    val ready = besideErrandBoy.adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus none for a matching adviser, plus the Vow's 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("with Vow of Beastkin, each vow's line tells its own part"):
    val ready = besideErrandBoy.adviser(p1, beastkin).adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus 1 for Vow of Beastkin, a Beast adviser, plus 1 and 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 5)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(NoteText.said(VowOfBeastkin.id,
      VowOfBeastkin.forCatalog(catalog).noteKeys, done.journal.events),
      Vector(gainedLine(1)))

  test("a short bank gives what it holds, and the line says what came"):
    val ready = besideErrandBoy.adviser(p1, vow).warbandsInBank(p1, 2).ready
    val done = musteredOnErrandBoy(ready)
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 2)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(1)))

  test("another player's Vow, or a facedown one, changes nothing"):
    val rivalHolds = besideErrandBoy.adviser(p2, vow).ready
    val facedown = besideErrandBoy.adviser(p1, vow, facedown = true).ready
    Vector(rivalHolds, facedown).foreach { ready =>
      val done = musteredOnErrandBoy(ready)
      assertEquals(Look(done.result).boardWarbands(p1),
        Look(ready).boardWarbands(p1) + 1)
      assertEquals(wanderingSaid(done.journal.events), Vector.empty)
    }
```

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add after the test "Vow of Beastkin's rule is automatic whatever its flag":

```scala
  test("Vow of Wandering's Muster power is automatic whatever its flag"):
    assertEquals(flag("denizen.vow-of-wandering.muster"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfWanderingMuster
      .resolution, PowerResolution.Automatic)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfWanderingSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: compile failure, `Not found: VowOfWanderingMuster`.

- [ ] **Step 3: Create the shared helper**

Create `src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** A vow's warbands added to a Muster's own gain (Vow of Beastkin, Vow of
  * Wandering), with the vow's line right after the gain: "Gained {n} more
  * warband."
  *
  * The line tells what the gain added beyond its amount before this vow, at
  * most the vow's own count. Under two vows, each adds to the gain the other
  * already changed, and each line tells its own part. A short bank shortens
  * the line of the vow that folded last. An empty bank writes nothing.
  */
private[economy] object ExtraWarbands:
  /** "Gained {n} more warband." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))

  /** `operations` with `extra` more warbands on `actor`'s gain, and the line
    * of `power`, from `card`, after it. Unchanged when they hold no such
    * gain. */
  def added(power: PowerId, card: DenizenId, extra: Int, actor: PlayerId,
      operations: Vector[Operation]): Vector[Operation] =
    val at = operations.indexWhere {
      case Gain.Warbands(player, _, _) => player == actor
      case _ => false
    }
    if at < 0 then operations
    else operations(at) match
      case gain: Gain.Warbands => operations.patch(at, Vector(
        gain.copy(amount = gain.amount + extra),
        Note(power, line(_, card, actor, gain.amount, extra))), 1)
      case _ => operations

  /** What the gain's step added beyond `before`, at most `extra`. */
  private def line(states: NoteStates, card: DenizenId, actor: PlayerId,
      before: Int, extra: Int): Option[PowerNote] = for
    step <- states.previous
    more = math.min(extra, NoteSupport.warbands(step, actor) - before)
    if more > 0
  yield gained(PowerSourceRef.Card(card), NoteArg.Number(more))
```

- [ ] **Step 4: Move Vow of Beastkin onto it**

In `src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala`:

- Delete the import line `import oathdigital.gameplay.powers.NoteSupport`.
- Replace the `MusterGain` entry of `contributions`:

```scala
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      withExtra(ctx.activePlayer, operations))))
```

with:

```scala
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      ExtraWarbands.added(id, cardId, VowOfBeastkin.Warbands,
        ctx.activePlayer, operations))))
```

- Delete the private methods `withExtra` and `extraNote`, with their doc comments.
- In the companion, replace the definition of `gained`:

```scala
  /** "Gained {n} more warband." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))
```

with:

```scala
  /** "Gained {n} more warband." */
  val gained: NoteKey = ExtraWarbands.gained
```

- In the class doc comment, replace "The extra warband joins the Muster's own gain, and the line follows that gain at once: "Gained {n} more warband.", what the gain added beyond its base." with "The extra warband joins the Muster's own gain, and the line follows that gain at once ([[ExtraWarbands]]): "Gained {n} more warband."".

The test sources still fail to compile until Step 5, so Step 7 runs Beastkin's suite.

- [ ] **Step 5: Create the card and its Muster power**

Delete the `object VowOfWanderingCard ...` block (8 lines, from `object VowOfWanderingCard` to its `val powers` line) from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`. `Locked` and `AdviserOnly` stay imported: other cards there still use them.

Create `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._

object VowOfWanderingCard extends Denizen(DenizenId("247"), "Vow of Wandering", Suit.Nomad) with Locked with AdviserOnly:
  val muster = PrintedPower(PowerId("denizen.vow-of-wandering.muster"),
    persistent = false, cost = Cost.free,
    text = "Gain two more warbands.")
  val power = PrintedPower(PowerId("denizen.vow-of-wandering"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** place warbands on sites.")
  val powers: Vector[PrintedPower] = Vector(muster, power)

/** Vow of Wandering's Muster power (card 247, adviser-only, locked), an
  * automatic rule of a faceup adviser: every Muster of its holder gains two
  * more warbands, best effort from the supply. They join the Muster's own
  * gain, as Vow of Beastkin's one does, and the line after the gain says how
  * many came ([[ExtraWarbands]]): "Gained {n} more warbands." The catalog
  * marks the power `persistent: false`, but it is not a choice, so it keeps
  * the default automatic resolution.
  */
case object VowOfWanderingMuster extends ContributingPower:
  val cardId: DenizenId = VowOfWanderingCard.id
  val id: PowerId = VowOfWanderingCard.muster.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val Warbands: Int = 2

  override def noteKeys: Vector[NoteKey] = Vector(ExtraWarbands.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      ExtraWarbands.added(id, cardId, Warbands, ctx.activePlayer,
        operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })
```

- [ ] **Step 6: Register it and import the card**

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, change the economy import's last line from `TheOldOakCard, VillageIdiotCard, VowOfBeastkinCard, VowOfPovertyCard}` to:

```scala
  TheOldOakCard, VillageIdiotCard, VowOfBeastkinCard, VowOfPovertyCard,
  VowOfWanderingCard}
```

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the economy import's last line from `VowOfBeastkin, VowOfPoverty}` to `VowOfBeastkin, VowOfPoverty, VowOfWanderingMuster}`.
- After `Vector(VowOfBeastkin.forCatalog(catalog)) ++`, add `Vector(VowOfWanderingMuster) ++`.
- In the doc comment, replace "and the Vows' limits." with "and the Vows' limits and extra warbands."

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfWanderingSuite oathdigital.gameplay.powers.economy.VowOfBeastkinSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: all pass. `CardCatalogSuite` checks that the card left the holding files.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Vow of Wandering gains two more warbands on a Muster"
```

---

### Task 3: The placement restriction, Conquest and the minor action

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala:169-176`
- Modify: `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingConquestSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `VowOfWanderingCard` and `VowOfWanderingMuster` from Task 2.
- Produces, on `case object VowOfWandering extends ContributingPower`:
  - `val id: PowerId` (`denizen.vow-of-wandering`) and `val cardId: DenizenId`.
  - `def holds(ready: ReadyGame, player: PlayerId): Boolean`: whether `player` holds the card faceup.
  - `val Reason: String = "Vow of Wandering forbids placing warbands on sites"`.
  - `val forbidden: NoteKey`: the line "Vow of Wandering forbids placing warbands on sites.", name `vow-of-wandering`.
  - `val kept: NoteKey`: "Kept {n} surviving warband(s) on the board.", name `kept`.
  - `operationRestrictions`: one restriction, reason code `vow-of-wandering`, kind `Impossible`.

- [ ] **Step 1: Write the failing restriction tests**

In `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala`, append inside the class:

```scala
  // ---- the restriction ----------------------------------------------------
  private val near = CatalogNames.site("Broken Peaks")
  private val placing = VowOfWandering.operationRestrictions.head
  private val refusal = Some(OperationReason("vow-of-wandering",
    "Vow of Wandering forbids placing warbands on sites",
    OperationReasonKind.Impossible))

  /** p1's Act holding Vow of Wandering faceup, with 3 warbands on the board
    * and 2 at Broken Peaks. */
  private def holdingVowInAct: Table = Table.start.turn(p1, Phase.Act)
    .adviser(p1, vow).boardWarbands(p1, 3).warbandsAt(near, p1, 2)

  /** One warband of `kind` moved from `from` to `to`. */
  private def moved(kind: ForceKind, from: Location, to: Location)
      : CoreOperation = Move(Piece.Warbands(kind, 1),
    PositionedLocation(from), PositionedLocation(to))

  /** `ready` with `player`'s lineage made Citizens. No Table step reaches
    * lineage roles. */
  private def asCitizen(ready: ReadyGame, player: PlayerId): ReadyGame =
    val lineage = Look(ready).player(player).lineage
    ready.updateCampaign(campaign => campaign.copy(lineages =
      campaign.lineages.updatedWith(lineage)(
        _.map(_.copy(role = Role.Citizen)))))

  test("the holder's warbands may not reach a site from the board, the " +
      "bank or another site"):
    val ready = holdingVowInAct.ready
    val mine = Look(ready).forceKind(p1)
    Vector(Location.PlayArea(p1), Location.WarbandBank(mine),
      Location.Site(near)).foreach(from => assertEquals(
        placing.reason(ready, moved(mine, from, Location.Site(home))),
        refusal, from.toString))

  test("a Replace that brings them to a site is refused too"):
    val ready = holdingVowInAct.ready
    assertEquals(placing.reason(ready, Replace(
      Piece.Warbands(ForceKind.Bandit, 2),
      Piece.Warbands(Look(ready).forceKind(p1), 2),
      PositionedLocation(Location.Site(home)))), refusal)

  test("moving them back to the board, and other players' warbands, are " +
      "allowed"):
    val ready = holdingVowInAct.ready
    assertEquals(placing.reason(ready, moved(Look(ready).forceKind(p1),
      Location.Site(near), Location.PlayArea(p1))), None)
    assertEquals(placing.reason(ready, moved(Look(ready).forceKind(p2),
      Location.PlayArea(p2), Location.Site(home))), None)
    assertEquals(placing.reason(ready, moved(ForceKind.Bandit,
      Location.WarbandBank(ForceKind.Bandit), Location.Site(home))), None)

  test("a facedown Vow refuses nothing, and another player's binds only " +
      "them"):
    val facedown = Table.start.turn(p1, Phase.Act)
      .adviser(p1, vow, facedown = true).ready
    assertEquals(placing.reason(facedown, moved(Look(facedown).forceKind(p1),
      Location.PlayArea(p1), Location.Site(home))), None)
    val rivals = Table.start.turn(p1, Phase.Act).adviser(p2, vow).ready
    assertEquals(placing.reason(rivals, moved(Look(rivals).forceKind(p1),
      Location.PlayArea(p1), Location.Site(home))), None)
    assertEquals(placing.reason(rivals, moved(Look(rivals).forceKind(p2),
      Location.PlayArea(p2), Location.Site(home))), refusal)

  test("a Citizen holder's Imperial warbands are refused on their own " +
      "turn, or from their own board"):
    val fromBank = moved(ForceKind.Imperial,
      Location.WarbandBank(ForceKind.Imperial), Location.Site(home))
    assertEquals(placing.reason(asCitizen(holdingVowInAct.ready, p1),
      fromBank), refusal)
    // On p2's turn, Imperial warbands from the bank may be the Chancellor's.
    val p2sTurn = asCitizen(holdingVowInAct.turn(p2, Phase.Act).ready, p1)
    assertEquals(placing.reason(p2sTurn, fromBank), None)
    assertEquals(placing.reason(p2sTurn, moved(ForceKind.Imperial,
      Location.PlayArea(p1), Location.Site(home))), refusal)
```

- [ ] **Step 2: Write the failing Conquest tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingConquestSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{Run, awaits, commit,
  parked, winningDice}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{CatalogNames, Look}

/** Vow of Wandering after a Conquest victory: the placement question is not
  * asked, and the survivors stay on the board. */
class VowOfWanderingConquestSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val game = CampaignFixture.rules(winningDice)

  /** `run` with every plan window finished and nothing sacrificed, until the
    * Campaign ends or asks something else. Unlike `finishPlanWindows`, it
    * never answers the placement. */
  private def throughPlans(run: Run): Run =
    parked.parkedDecision(run.state) match
      case Some(facts) if facts.decision == CampaignIds.attackerPlan ||
          facts.decision == CampaignIds.defenderPlan =>
        throughPlans(run.answer(facts.awaiting, facts.decision,
          ChooseOneAnswer(CampaignIds.finish)))
      case Some(facts) if facts.decision == CampaignIds.sacrifice =>
        throughPlans(run.answer(facts.awaiting, facts.decision,
          ChooseAmountAnswer(0)))
      case _ => run

  test("the holder is not asked to place the survivors, who stay on the " +
      "board"):
    val b = CampaignFixture.withAdviser(
      CampaignFixture.board(warbands = 5, supply = 7), vow,
      Orientation.FaceUp)
    val done = throughPlans(commit(game, b, 4))
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 5)
    assertEquals(NoteText.said(VowOfWandering.id, VowOfWandering.noteKeys,
      done.events), Vector(NoteText.Said("kept",
        "Kept 4 surviving warbands on the board.", covers = false)))

  test("without the Vow, the same Conquest asks for the placement"):
    val b = CampaignFixture.board(warbands = 5, supply = 7)
    assert(awaits(throughPlans(commit(game, b, 4)), b.actor,
      CampaignIds.placement))
```

- [ ] **Step 3: Write the failing minor action test, and update the old one**

In `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`:

- Change the import `import oathdigital.testkit.{CatalogNames, Journal, Look, Outcomes, PlacementPicks, Table}` to `import oathdigital.testkit.{CatalogNames, Journal, Look, Outcomes, PlacementPicks, PowerRules, Table}`.
- In the test "minor actions run under the global restrictions", replace the expected value:

```scala
      Vector(refuseAllStub)), Left(OathViolation.InvalidEventOrder(
        "Minor action effect differs from recorded outcome")))
```

with:

```scala
      Vector(refuseAllStub)), Left(OathViolation.MinorActionUnavailable(
        "every operation is refused")))
```

- After that test, add:

```scala
  test("Vow of Wandering refuses a move to the site with its reason, and " +
      "allows one back to the board"):
    val holding = minorBoard.adviser(p1,
      CatalogNames.denizen("Vow of Wandering")).ready
    val withPowers = PowerRules.withWalkerPowers
    assertEquals(withPowers.handle(Ready(holding),
      MinorActionCommand.MoveWarbands(p1, toSite = true, 1)),
      Left(OathViolation.MinorActionUnavailable(
        "Vow of Wandering forbids placing warbands on sites")))
    assert(withPowers.handle(Ready(holding),
      MinorActionCommand.MoveWarbands(p1, toSite = false, 2)).isRight)
```

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the last line of `persistentRules` from `"denizen.forced-labor", "denizen.way-station")` to:

```scala
    "denizen.forced-labor", "denizen.way-station", "denizen.vow-of-wandering")
```

- [ ] **Step 4: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfWandering* oathdigital.gameplay.MinorActionsSuite"`
Expected: compile failure, `Not found: VowOfWandering`.

- [ ] **Step 5: Add the rule to `VowOfWandering.scala`**

In `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala`:

- Replace the imports with:

```scala
import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._
```

- In `VowOfWanderingMuster`, replace `holds(ctx.state, ctx.activePlayer)` with `VowOfWandering.holds(ctx.state, ctx.activePlayer)`, and delete its private `holds` method.
- Append:

```scala
/** Vow of Wandering (card 247, adviser-only, locked), a persistent rule of a
  * faceup adviser: "You cannot place warbands on sites."
  *
  *  - **The restriction.** An operation restriction refuses any move that
  *    puts the holder's warbands on a site: from the board, the bank or
  *    another site. It reads the leaves of a composite, so a `Replace` is
  *    refused too. A move from a site to the board stays legal. Warbands are
  *    the holder's when they leave the holder's board, or when they are of
  *    the holder's kind. The Chancellor and the Citizens share Imperial
  *    warbands, so those count only while the holder is the active player.
  *    The refusal is `Impossible`: an optional placement is skipped, and no
  *    walk fails because of it.
  *  - **The powers that place warbands** read [[holds]] themselves, to write
  *    [[forbidden]] or to offer only what is legal. The minor action refuses
  *    with the restriction's reason.
  *  - **Conquest.** After a victory, the placement question is replaced by a
  *    line, and the survivors stay on the board: "Kept {n} surviving
  *    warbands on the board."
  */
case object VowOfWandering extends ContributingPower:
  val cardId: DenizenId = VowOfWanderingCard.id
  val id: PowerId = VowOfWanderingCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** The restriction's detail, which the minor action shows. */
  val Reason: String = "Vow of Wandering forbids placing warbands on sites"
  /** "Vow of Wandering forbids placing warbands on sites.", the line a power
    * writes under its own name when the Vow stops its placement. */
  val forbidden: NoteKey = NoteKey("vow-of-wandering",
    Vector(NotePart.Text(s"$Reason.")))
  /** "Kept {n} surviving warband on the board." */
  val kept: NoteKey = NoteKey("kept", Vector(NotePart.Text("Kept "),
    NotePart.Arg(0), NotePart.Plural(0, " surviving warband on the board.",
      " surviving warbands on the board.")))

  override def noteKeys: Vector[NoteKey] = Vector(kept)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlacement -> Vector(Transform((_, operations) =>
      operations.map {
        case decide: Decide if decide.decisionId == CampaignIds.placement =>
          Note(id, _ => Some(kept(PowerSourceRef.Card(cardId),
            NoteArg.Number(survivors(decide.query)))))
        case other => other
      })))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)

  /** Whether `player` holds this card faceup. */
  def holds(ready: ReadyGame, player: PlayerId): Boolean =
    holderOf(ready).contains(player)

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The survivors the placement question offers. */
  private def survivors(query: DecisionQuery): Int = query match
    case amount: DecisionQuery.ChooseAmount => amount.max
    case spread: DecisionQuery.Distribute => spread.maxTotal
    case _ => 0

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = holderOf(ready)
      .filter(holder =>
        Operation.flatten(operation).exists(places(ready, holder, _)))
      .map(_ => OperationReason("vow-of-wandering", Reason,
        OperationReasonKind.Impossible))

  /** Whether `step` puts `holder`'s warbands on a site. */
  private def places(ready: ReadyGame, holder: PlayerId, step: Operation)
      : Boolean = step match
    case Move(Piece.Warbands(kind, _), from,
        PositionedLocation(Location.Site(_), _), _) =>
      from.location == Location.PlayArea(holder) ||
        holdersKind(ready, holder, kind)
    case _ => false

  /** Whether warbands of `kind` are `holder`'s wherever they come from. */
  private def holdersKind(ready: ReadyGame, holder: PlayerId,
      kind: ForceKind): Boolean =
    ready.game.current.players.find(_.player == holder)
      .flatMap(PlayerForceKind.of(ready, _)).contains(kind) &&
      (kind != ForceKind.Imperial ||
        ready.game.current.turn.activePlayer == holder)
```

- [ ] **Step 6: Register the rule**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the economy import's last line to `VowOfBeastkin, VowOfPoverty, VowOfWandering, VowOfWanderingMuster}`.
- Replace `Vector(VowOfWanderingMuster) ++` with `Vector(VowOfWanderingMuster, VowOfWandering) ++`.
- In the doc comment, replace "and the Vows' limits and extra warbands." with "the Vows' limits and extra warbands, and Vow of Wandering's ban on placing warbands."

- [ ] **Step 7: Report the skipped operation's reason in the minor action**

In `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala`, replace `evolveOperations`:

```scala
  private def evolveOperations(
      restrictions: Vector[OperationRestriction],
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Either[OathViolation, ReadyGame] =
    OperationPipeline.run(ready, operations, operationAllowlist,
      restrictions)(Right(_))
      .flatMap(_.expectEffects(operations,
        "Minor action effect differs from recorded outcome"))
```

with:

```scala
  /** Runs `operations` as the event's effect. A restriction that skips one
    * refuses the minor action with its own reason, such as Vow of
    * Wandering's. */
  private def evolveOperations(
      restrictions: Vector[OperationRestriction],
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Either[OathViolation, ReadyGame] =
    OperationPipeline.run(ready, operations, operationAllowlist,
      restrictions)(Right(_))
      .flatMap(run => run.skipped.flatMap(_.reasons).headOption match
        case Some(reason) => Left(MinorActionUnavailable(reason.detail))
        case None => run.expectEffects(operations,
          "Minor action effect differs from recorded outcome"))
```

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfWandering* oathdigital.gameplay.MinorActionsSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: all pass.

If the Citizen test fails because `Look(...).forceKind` or the lineage copy does not compile, check the lineage case class in `src/main/scala/oathdigital/model/` (`RecoverProcedureSuite` changes a role the same way) and keep the test's intent.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/main/scala/oathdigital/gameplay/actions/MinorActions.scala src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingSuite.scala src/test/scala/oathdigital/gameplay/powers/economy/VowOfWanderingConquestSuite.scala src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Vow of Wandering keeps its holder's warbands off sites"
```

---

### Task 4: The card powers that place warbands

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Garrison.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCity.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BallotBox.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala`
- Modify: the five matching suites under `src/test/scala/oathdigital/gameplay/powers/`

**Interfaces:**
- Consumes: `VowOfWandering.holds(ready, player)`, `VowOfWandering.forbidden` and `VowOfWandering.cardId` from Task 3. Import `oathdigital.gameplay.powers.economy.VowOfWandering` in each production file.

These suites drive one power at a time. Each power reads the Vow from the state, so the Vow need not be registered in the suite.

- [ ] **Step 1: Write the failing tests**

Each test below uses this expected line; declare it in each suite that uses it:

```scala
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val forbidden = NoteText.Said("vow-of-wandering",
    "Vow of Wandering forbids placing warbands on sites.", covers = false)
```

**`gameplay/powers/whenplayed/GarrisonSuite.scala`.** Add `import oathdigital.gameplay.powers.NoteText`. Add the two declarations above, and append:

```scala
  test("a holder of Vow of Wandering gains the warbands but places none"):
    val ready = Table.from(ruling(Vector(1, 1, 1), onBoard = 5))
      .adviser(p1, vow).ready
    val done = Outcomes.finished(play(ready, power, card))
    sites.foreach(site => assertEquals(Look(done.treeless).forces(site),
      SiteForces.Occupied(kind, 1)))
    // 5 on the board and 3 gained, none placed.
    assertEquals(Look(done.treeless).player(p1).board.warbands, 8)
    assertEquals(said(power, done.events), Vector(forbidden))
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("a holder short of warbands is not asked to choose the sites"):
    // The bank holds one warband, so the board holds fewer than the sites.
    val ready = Table.from(ruling(Vector(4, 4, 5), onBoard = 0))
      .adviser(p1, vow).ready
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(Look(done.treeless).player(p1).board.warbands, 1)
    assertEquals(said(power, done.events), Vector(forbidden))
```

**`gameplay/powers/whenplayed/KeyToTheCitySuite.scala`.** Add `CatalogNames` to the `oathdigital.testkit` import. Add the two declarations above, and append:

```scala
  test("a holder of Vow of Wandering kills and gains, but places nothing"):
    val ready = start.adviser(p1, vow).warbandsAt(homeSite, p2, 2).ready
    val done = Outcomes.finished(fire(ready, power, played))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Empty)
    // 3 on the board and 1 gained.
    assertEquals(after.boardWarbands(p1), 4)
    assertEquals(said(power, done.events), Vector(killedTwo, forbidden))
    assertJournalRebuilds(ready, done.events, done.treeless)
```

**`gameplay/powers/whenplayed/BanditPrinceSuite.scala`.** Add `CatalogNames` to the `oathdigital.testkit` import. Add the two declarations above, and append:

```scala
  test("a holder of Vow of Wandering is not asked, and the bandits stay"):
    val ready = bandited.adviser(p1, vow).ready
    val done = Outcomes.finished(play(ready, power, prince))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(said(power, done.events), Vector(forbidden))
```

**`gameplay/powers/action/BallotBoxSuite.scala`.** Add the two declarations above, and append:

```scala
  test("a holder of Vow of Wandering removes the warbands, since none can " +
      "be replaced"):
    val ready = matching.adviser(p1, vow).warbandsAt(home, p2, 2).ready
    val t = accepted(use(ready, power, source))
    // Ancient City's printed bandit capacity is 3.
    assertEquals(Look(after(t)).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(Look(after(t)).boardWarbands(p1),
      Look(ready).boardWarbands(p1))
    assertEquals(NoteText.said(power, t.events), Vector(forbidden,
      NoteText.Said("removed",
        s"Removed 2 ${p2.value} warbands at ${home.value}.",
        covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
```

**`gameplay/powers/campaign/HospitalSuite.scala`.** Add only `private val vow = CatalogNames.denizen("Vow of Wandering")`, and append:

```scala
  test("a holder of Vow of Wandering has the dead killed as usual"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(hospitalFor(base, base.actor, hospitalSite), vow,
      Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!done.ops.contains(returned(b, b.actor, 2, hospitalSite)))
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.actor), 2))
    assertEquals(lines(done), Vector.empty)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.GarrisonSuite oathdigital.gameplay.powers.whenplayed.KeyToTheCitySuite oathdigital.gameplay.powers.whenplayed.BanditPrinceSuite oathdigital.gameplay.powers.action.BallotBoxSuite oathdigital.gameplay.powers.campaign.HospitalSuite"`
Expected: the six new tests fail.
- Garrison places a warband, or parks on its question.
- Key to the City writes "had no warband to place."
- Bandit Prince parks on its question.
- Ballot Box writes no Vow line.
- Hospital places the dead.

The old tests pass.

- [ ] **Step 3: Garrison**

In `Garrison.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`. Replace `effect`:

```scala
  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      BuildOps((ready, _) => gain(ready, actor)),
      Branch((ready, _) => ask(ready, actor)),
      BuildOps((ready, pending) => place(ready, actor, pending)))
```

with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(VowOfWandering.forbidden)

  /** Whether Vow of Wandering stops the placement: the actor holds it and
    * rules a site. */
  private def barred(ready: ReadyGame, actor: PlayerId): Boolean =
    VowOfWandering.holds(ready, actor) && ruled(ready, actor).nonEmpty

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      BuildOps((ready, _) => gain(ready, actor)),
      Branch((ready, _) =>
        if barred(ready, actor) then Vector(Note(id, _ =>
          Some(VowOfWandering.forbidden(PowerSourceRef.Card(cardId)))))
        else ask(ready, actor)),
      BuildOps((ready, pending) =>
        if barred(ready, actor) then Right(Vector.empty)
        else place(ready, actor, pending)))
```

The three siblings keep their places, so a parked Garrison question resumes at the same path. In the doc comment, add after "The placement puts a warband on every ruled site, or on the chosen ones.": "A holder of Vow of Wandering places none and is not asked: the Vow's line takes the place of the question."

- [ ] **Step 4: Key to the City**

In `KeyToTheCity.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`.

- Change `noteKeys` to `Vector(killed, bandits, placed, unplaced, guarded, VowOfWandering.forbidden)`.
- In `steps`, replace the last two entries of the `case None` vector:

```scala
      BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
        Vector(Move(Piece.Warbands(kind, Placed),
          PositionedLocation(Location.PlayArea(actor)),
          PositionedLocation(Location.Site(site)))))),
      Note(id, placedNote(_, actor, site), covers = true))
```

with:

```scala
      BuildOps((live, _) =>
        if VowOfWandering.holds(live, actor) then Right(Vector.empty)
        else PlayerFacts.forceKind(live, actor).map(kind =>
          Vector(Move(Piece.Warbands(kind, Placed),
            PositionedLocation(Location.PlayArea(actor)),
            PositionedLocation(Location.Site(site)))))),
      Note(id, placedNote(_, actor, site), covers = true),
      Note(id, states => Option.when(VowOfWandering.holds(states.now, actor))(
        VowOfWandering.forbidden(cardSource))))
```

- In `placedNote`, return `None` for a holder. Replace its body's first line, `PlayerFacts.forceKind(states.now, actor).toOption.map { kind =>`, with:

```scala
    if VowOfWandering.holds(states.now, actor) then None
    else PlayerFacts.forceKind(states.now, actor).toOption.map { kind =>
```

- The Vow's line does not cover: the gain's own line stays.
- In the doc comment, add at the end: "A holder of Vow of Wandering places nothing: the Vow's line replaces the placement line."

- [ ] **Step 5: Bandit Prince**

In `BanditPrince.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`.

- Change `noteKeys` to `Vector(replaced, none, VowOfWandering.forbidden)`.
- In `effect`, after the `if sites.isEmpty then ...` branch, add:

```scala
      else if VowOfWandering.holds(live, actor) then Vector(Note(id, _ =>
        Some(VowOfWandering.forbidden(PowerSourceRef.Card(cardId)))))
```

- In the doc comment, add after "With no site left that fits, the question is not asked.": "A holder of Vow of Wandering is not asked either, and the Vow's line says why."

- [ ] **Step 6: Ballot Box**

In `BallotBox.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`.

- Change `noteKeys` to `Vector(replaced, replacedBandits, removed, removedBandits, unmatched, empty, VowOfWandering.forbidden)`.
- In `vote`, replace the `case Some((kind, count)) => Vector(` branch with:

```scala
      case Some((kind, count)) =>
        (if VowOfWandering.holds(ready, player) then
          line(VowOfWandering.forbidden) else Vector.empty) ++ Vector(
          BuildOps((live, _) =>
            replacement(live, player, site, kind, count)),
          Note(this.id, replacedNote(_, site, kind, source), covers = true),
          Note(this.id, removedNote(_, site, kind, source)))
```

- In `replacement`, replace `val covered = count.min(PlayerFacts.banked(ready, mine))` with:

```scala
      val covered =
        if VowOfWandering.holds(ready, player) then 0
        else count.min(PlayerFacts.banked(ready, mine))
```

- In the doc comment, add after "Those the supply cannot cover are killed first, since a site holds one kind of warband.": "A holder of Vow of Wandering can replace none, so every one is killed, after the Vow's line."

- [ ] **Step 7: Hospital**

In `Hospital.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`.

- In `placeable`, after `if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(user))`, add `if !VowOfWandering.holds(ready, user)`.
- Change its doc comment's first sentence to "How many saved warbands the placement moves to Hospital's site: none unless `user` rules it now and holds no Vow of Wandering, and no more than their supply holds."
- In the class doc comment, add after "When its site was a Conquest target the attacker won, the user rules it no longer and nothing comes back.": "Nor does it for a holder of Vow of Wandering: their dead stay killed, and Hospital writes no line."

Cursed Cauldron reads `placeable`, so it counts the holder's dead as staying dead.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.action.BallotBoxSuite oathdigital.gameplay.powers.campaign.*"`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/Garrison.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCity.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala src/main/scala/oathdigital/gameplay/powers/action/BallotBox.scala src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/GarrisonSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCitySuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala src/test/scala/oathdigital/gameplay/powers/action/BallotBoxSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/HospitalSuite.scala
git commit -m "feat(powers): card powers that place warbands respect Vow of Wandering"
```

---

### Task 5: Messenger and Warning Signals only bring warbands back

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Messenger.scala:40-50`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala:55-62`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/MessengerSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/WarningSignalsSuite.scala`

**Interfaces:**
- Consumes: `VowOfWandering.holds` from Task 3.
- Produces:
  - `WarbandArrangement.movable(board: Int, sites: Vector[(SiteId, Int)], toSites: Boolean): Boolean`
  - `WarbandArrangement.query(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)], heading: String, toSites: Boolean): DecisionQuery`

- [ ] **Step 1: Write the failing tests**

**`gameplay/powers/action/MessengerSuite.scala`.** Add `private val vow = CatalogNames.denizen("Vow of Wandering")`, and append:

```scala
  test("a holder of Vow of Wandering may only bring warbands back to the " +
      "board"):
    // 3 on the board, 2 at Ancient City and 1 at Deep Woods: 6. No site
    // may grow.
    val ready = Table.from(staged(board = 3, atHome = 2)).adviser(p1, vow)
      .ready
    assertEquals(arranged(ready, arrangement(1, 4, 1)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${Messenger.decisionId} distributes to " +
          s"site/${home.value} outside 1..2")))
    val end = after(accepted(arranged(ready, arrangement(4, 1, 1))))
    assertEquals(Look(end).boardWarbands(p1), 4)
    assertEquals(count(end, home), 1)

  test("a holder whose sites keep one warband each has nothing to move"):
    val ready = Table.from(staged(board = 3, atHome = 1)).adviser(p1, vow)
      .ready
    val t = accepted(use(ready, Messenger, source))
    assert(!awaits(t, Messenger.decisionId))
    assertEquals(NoteText.said(Messenger, t.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))
```

**`gameplay/powers/campaign/WarningSignalsSuite.scala`.** Add `private val vow = CatalogNames.denizen("Vow of Wandering")`, and append:

```scala
  test("a defender holding Vow of Wandering may only bring warbands back " +
      "to the board"):
    val base = defending
    val b = withAdviserFor(base, base.rival, vow, Orientation.FaceUp)
    val run = chosen(b)
    // The origin holds 2, so its slot tops out at 2.
    assertEquals(run.refused(b.rival, decision,
      arrangement(b, board = 0, origin = 4, extra = 3)),
      Some(OathViolation.InvalidEventOrder(s"decision $decision " +
        s"distributes to site/${b.origin.value} outside 1..2")))
    val moved = run.answer(b.rival, decision,
      arrangement(b, board = 5, origin = 1, extra = 1))
    assertEquals(Look(moved.state).player(b.rival).board.warbands, 5)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MessengerSuite oathdigital.gameplay.powers.campaign.WarningSignalsSuite"`
Expected: the three new tests fail.
- The refused answers are accepted, or are refused with a wider range.
- The stuck Messenger parks on its question.

- [ ] **Step 3: Cap the sites in `WarbandArrangement`**

In `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`, replace `movable` and `query`:

```scala
  /** Whether a warband can move: there is a site, and a warband beyond the
    * one each site keeps. */
  def movable(board: Int, sites: Vector[(SiteId, Int)]): Boolean =
    sites.nonEmpty && board + sites.map(_._2).sum > sites.size

  /** The exact distribution over the board and `sites`, each slot opening on
    * what it holds now. */
  def query(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      heading: String): DecisionQuery =
    val total = board + sites.map(_._2).sum
    val room = total - (sites.size - 1)
    val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
      Some(board)) +: sites.map { case (site, count) => DistributeSlot(
        DecisionOptionRef.Site(site), 1, room, Some(count)) }
```

with:

```scala
  /** Whether a warband can move: there is a site, and a warband beyond the
    * one each site keeps. When the sites may not grow (`toSites` false),
    * only a site holding more than its one can send a warband back. */
  def movable(board: Int, sites: Vector[(SiteId, Int)], toSites: Boolean)
      : Boolean =
    if toSites then sites.nonEmpty && board + sites.map(_._2).sum > sites.size
    else sites.exists(_._2 > 1)

  /** The exact distribution over the board and `sites`, each slot opening on
    * what it holds now. When the sites may not grow (`toSites` false), as for
    * a holder of Vow of Wandering, each site's slot tops out at what it
    * holds. */
  def query(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      heading: String, toSites: Boolean): DecisionQuery =
    val total = board + sites.map(_._2).sum
    val room = total - (sites.size - 1)
    val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
      Some(board)) +: sites.map { case (site, count) => DistributeSlot(
        DecisionOptionRef.Site(site), 1, if toSites then room else count,
        Some(count)) }
```

- [ ] **Step 4: Pass `toSites` from both callers**

In `Messenger.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`. In `build`, after the `val (board, sites) = ...` line, add `val toSites = !VowOfWandering.holds(live, player)`. Then:

- Change `if !WarbandArrangement.movable(board, sites) then` to `if !WarbandArrangement.movable(board, sites, toSites) then`.
- Change the query's heading argument to end with `"keeps, and each site keeps at least one", toSites)),`.

In `WarningSignals.scala`, add `import oathdigital.gameplay.powers.economy.VowOfWandering`. In `rearrange`, after the `val (board, sites) = ...` line, add `val toSites = !VowOfWandering.holds(ready, user)`. Then:

- Change `if !WarbandArrangement.movable(board, sites) then Vector.empty` to `if !WarbandArrangement.movable(board, sites, toSites) then Vector.empty`.
- Change the query's heading argument to end with `"holds the ones no site keeps, and each site keeps at least one", toSites)),`.

Run `grep -rn "WarbandArrangement\.\(movable\|query\)" src` and check that no other caller exists.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MessengerSuite oathdigital.gameplay.powers.campaign.WarningSignalsSuite"`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala src/main/scala/oathdigital/gameplay/powers/action/Messenger.scala src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala src/test/scala/oathdigital/gameplay/powers/action/MessengerSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/WarningSignalsSuite.scala
git commit -m "feat(powers): Messenger and Warning Signals keep a Vow holder's sites from growing"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 5)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass, with 22 server tests more than the baseline and the same frontend count.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the bullet `- [ ] **Slice 2: Warband placement.** Vow of Wandering.` under "### Phase - Catalog batch 5".
- In "## Now", replace `**Phase - Catalog batch 5** is in progress: slice 1 is done.` (it spans two lines) with `**Phase - Catalog batch 5** is in progress: slices 1 and 2 are done.`

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 5 slice 2"
```
