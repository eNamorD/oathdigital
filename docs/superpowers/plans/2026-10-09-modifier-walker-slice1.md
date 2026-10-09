# Modifier Selection on the Walker, Slice 1 (The Core, Then Search) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Search and a facedown-adviser play ask for their modifiers inside the walk, as the walk's first decision, and pay each one when it is picked.

**Architecture:**

- **The picks are answers.** A new vocabulary object, `ModifierPicks` (in `gameplay.powerresolver`), names the modifier decision (`search.modifiers`), its "Start" answer, and reads the picked modifiers back out of the answers.
- **The walker's powers follow the answers.** `WalkerPowers` keeps the full catalog as its `pool`. `withAnswers` adds the powers picked in the answers. `ProcedureWalker` applies it at every entry point and after each answer, so no caller has to.
- **A new node, `ModifierChoice`,** is one pass of a `Repeat`, like `CampaignPlanChoice`. Each pass lists the modifiers not yet picked, priced or unavailable with a reason, and puts a "Start Search" button first. A pick pays at once, through the power's `selectionPayments`. The walker gathers the node like an `OfferHost`, and applies the window's `OptionRestriction`s to its decision.
- **Three new `ContributingPower` hooks:**
  - `selectionCard`: the card the option names.
  - `selectionWindows`: where the power may be picked.
  - `offerReason`: the card's own verdict on its option, given the picks so far.

  `SelectedModifier` implements the first two. Batch 6's cards implement the third.
- **Search and facedown-adviser play move.**
  - Their trees start with `ModifierChoice.loop`. `StartWalker` refuses modifiers for them.
  - `SelectedModifier` no longer pays a Search modifier at the eligibility window.
  - The client starts them directly, without the modifier flow.
  - The other actions work as today until their slices.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`. Read it whole before starting. Task 1 adds the rulings made at plan time, so read its "Plan-time rulings" section after Task 1 too. The pattern this slice copies is `CampaignPlanChoice` and `CampaignPlanSteps` (`gameplay/actions/campaign/`).

**Starts from:** `main` at `35d8065d` or later. Catalog batch 6 slice 1 (`docs/superpowers/plans/2026-10-09-catalog-batch-6-slice1.md`) also edits `WalkerPowerGather.applyWindow`. If it has landed, add this plan's `ModifierChoice` case beside its `OfferHost` case. If it lands while you work, the conflict is one `case` and merges by keeping both.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it. Each file below lists the imports it needs. A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production and test Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `ProcedureWalker.scala` is at 625 and `WalkerPowerGather.scala` at 443; the edits below add a few lines to each.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. `ModifierPicks` lives in `gameplay.powerresolver` so that `SelectedModifier` can use it.
  - **No power name appears in walker sources**, not even in a comment, in any case. `SelectedModifier` counts as a power name, so `gameplay/walker` must never contain the text "selectedmodifier" in any case. Name no card in `ModifierChoice.scala` either. `BackendArchitectureSuite` enforces this.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - The walker (`gameplay/walker`) does not import `gameplay/actions` outside `WalkerProcedureRegistry`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.** A browser check runs a scratch server on port 8081 through Bash, never `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` into the worktree if a ledger exists; it is gitignored.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No compatibility for parked saves** (spec ruling 10). A journal parked mid-Search before this change fails to resume. Fixtures and development data are updated in place (roadmap standing rule).
- **Texts, exactly:**
  - Decision heading: "Choose modifiers, or start".
  - Search's start button: "Start Search". Facedown-adviser play's: "Play facedown adviser".
  - A modifier its player cannot pay for: "Needs {n} favor; you have {m}", or "Needs {n} secret(s); you have {m}", else "Cannot be paid for now".
  - A modifier that is not applicable for another reason: "Cannot be used now".
  - Start when nothing can follow: the first refused option's reason from the procedure's own option pricing (Search: "Needs 2 Supply; you have 0"), else "Cannot start now".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report the difference; this plan adds about 30 server tests and changes, but does not add, frontend tests.
- **The verification gate** (roadmap standing rule), run in Task 6:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
  4. `git diff --check main...HEAD`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-modifier-walker-design.md` | Modify | Plan-time rulings |
| `src/main/scala/oathdigital/gameplay/powerresolver/ModifierPicks.scala` | Create | The modifier decision's ids, and the picks read from answers |
| `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` | Modify | `OfferReason`; the `selectionCard`, `selectionWindows` and `offerReason` hooks |
| `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala` | Modify | The hooks; a pick applies at its window; Search pays at pick |
| `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala` | Modify | `pool` and `withAnswers` |
| `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` | Modify | Picks join the powers at every entry and after each answer |
| `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` | Modify | Picks join the powers in `leafAt` and the restriction scan; the `ModifierChoice` case |
| `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala` | Create | The modifier decision node and its `Repeat` |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | Modify | `choosesModifiers` |
| `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | Modify | Refuse `StartWalker.modifiers` for a procedure that chooses them |
| `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala` | Modify | The tree starts with the modifier decision; `startable` previews what follows it |
| `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala` | Modify | The facedown tree starts with the modifier decision; picked cards reach the slots |
| `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala` | Modify | A picked card is "In use as a modifier this action" |
| `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` | Modify | `ActiveModifier` also holds picked cards |
| `src/main/scala/oathdigital/application/gamelog/StartLines.scala` | Modify | The start line names the picks; the tags wait for them |
| `src/main/scala/oathdigital/application/gamelog/DetailLines.scala` | Modify | The modifier decision posts no "Chose" line |
| `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` | Modify | Hands `StartLines` the pick reader |
| `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala` | Modify | Search and facedown play start directly |
| `src/test/scala/oathdigital/gameplay/ModifierPlay.scala` | Create | Test helper: pick modifiers through the decision |
| Tests | Create/Modify | Listed per task |
| `docs/ROADMAP.md`, `CONTEXT.md` | Modify | Slice 1 checked; the modifier flow's entry |

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`

These rulings were settled while writing this plan. They fill gaps the spec left open. Nothing here changes an approved ruling.

- [ ] **Step 1: Add a "Plan-time rulings" section after "The Game Log"**

Insert this section, in normal prose, before `## Slices`:

```markdown
## Plan-time rulings

Settled while planning slice 1:

1. **The decision is `{action}.modifiers`** (`search.modifiers`), one id per
   modifier window, so a facedown-adviser play shares Search's. A pick
   answers with the modifier's card. "Start" answers with the button
   `start`, and it is listed first, so a test's default answer starts the
   action with no modifier.
2. **Who is listed.** Every player-selected power that may be picked at the
   window and whose card the actor may use now. Each is judged in this
   order:
   - Its own `offerReason`, from the picks so far.
   - A dry run of its payment, priced, or unavailable with the shortfall.
   - Whether it is applicable, else "Cannot be used now".

   An `offerReason` warning marks an otherwise available option.
3. **When a pass asks.** The first pass asks only when some modifier can be
   picked, so a player with nothing to pick is asked nothing (ruling 1).
   After a pick, a pass asks while any modifier is still listed, available
   or not, so the reasons stay visible. Once every listed modifier is
   picked, the action starts by itself.
4. **"Start"'s reason** is the first refused option of the action's first
   decision, as the procedure prices it (Search: "Needs 2 Supply; you
   have 0"). A procedure that prices nothing says "Cannot start now".
5. **`selectionPayments` stays.** It is the pick's payment. Only the
   eligibility-window Transform goes, action by action as each moves
   (`SelectedModifier.paidAtPick`).
6. **The powers follow the answers.** `WalkerPowers` keeps the full catalog
   as its pool. `ProcedureWalker` adds the powers picked in the answers at
   every entry and after each answer. Within a command, picks are only ever
   added.
7. **A picked modifier applies at its own selection window,** however much
   it could still pay, so its option restrictions reach the remaining
   options.
8. **Picked cards are protected.** `CardSlots` reads the picked cards from
   the answers, and `ActiveModifier` refuses their discard, as it does for
   the modifiers a start selected.
9. **The Game Log.** The modifier decision posts no "Chose" line. The Search
   tag waits until the decision is settled: it was never asked, "Start" was
   answered, or a later decision was (a walk whose every modifier is picked
   starts by itself). The facedown-adviser tag moves from the start to the
   adviser's pick.
10. **Labels.** Search's button reads "Start Search", and the facedown-adviser
    play's reads "Play facedown adviser".
```

- [ ] **Step 2: Correct "What goes away"**

In the bullet `SelectedModifier`'s eligibility-window payment Transform and `selectionPayments`., replace the sentence with:

```markdown
- `SelectedModifier`'s eligibility-window payment Transform. Its
  `selectionPayments` stays as the pick's payment (plan-time ruling 5).
```

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: passes.

```bash
git add docs/superpowers/specs/2026-10-09-modifier-walker-design.md
git commit -m "docs(modifiers): record the slice 1 plan-time rulings"
```

---

### Task 2: The picks vocabulary and powers that follow the answers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/ModifierPicks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Create: `src/test/scala/oathdigital/gameplay/powerresolver/ModifierPicksSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala`

**Interfaces:**
- Produces:
  - `ModifierPicks.start: DecisionOptionRef.Button`
  - `ModifierPicks.decisionId(window: PowerWindow): String`
  - `ModifierPicks.isDecision(decisionId: String): Boolean`
  - `ModifierPicks.cardRef(card: CardId): Option[DecisionOptionRef]`
  - `ModifierPicks.cards(answered: Vector[Answered]): Vector[CardId]`
  - `ModifierPicks.powerFor(pool: Vector[ContributingPower], window: PowerWindow, ref: DecisionOptionRef): Option[ContributingPower]`
  - `ModifierPicks.picks(pool: Vector[ContributingPower], answered: Vector[Answered]): Vector[PowerId]`
  - `ModifierPicks.started(answered: Vector[Answered], decisionId: String): Boolean`
  - `ModifierPicks.settled(answered: Vector[Answered]): Boolean`
  - `ModifierPicks.isPicked(answered: Vector[Answered], window: PowerWindow, card: CardId): Boolean`
  - `enum OfferReason { case Unavailable(reason: String); case Warned(reason: String) }` in `gameplay.powerresolver`
  - `ContributingPower.selectionCard: Option[CardId]`, `selectionWindows: Set[PowerWindow]`, `offerReason(ctx: PowerCtx): Option[OfferReason]`
  - `SelectedModifier.selectionWindow(action: MajorActionType): PowerWindow`
  - `WalkerPowers.pool: Vector[ContributingPower]`, `WalkerPowers.withAnswers(answered: Vector[Answered]): WalkerPowers`

- [ ] **Step 1: Write the failing vocabulary suite**

Create `src/test/scala/oathdigital/gameplay/powerresolver/ModifierPicksSuite.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p1

/** The modifier decision's vocabulary: its id per window, its Start answer,
  * and the picks read back out of the answers (modifier walker design,
  * plan-time rulings 1 and 6). */
class ModifierPicksSuite extends munit.FunSuite:
  private val tents = CatalogNames.denizen("Tents")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val search = ModifierPicks.decisionId(
    PowerWindow.SearchModifierSelection)

  /** A stand-in on `card`, pickable at `windows`: player-selected unless
    * `automatic`. */
  private final case class Pickable(name: String, card: CardId,
      windows: Set[PowerWindow], automatic: Boolean = false)
      extends ContributingPower:
    val id: PowerId = PowerId(s"probe.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    val contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution =
      if automatic then PowerResolution.Automatic
      else PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = windows

  private val atSearch = Pickable("search", tents,
    Set(PowerWindow.SearchModifierSelection))
  private val atTravel = Pickable("travel", tents,
    Set(PowerWindow.TravelModifierSelection))
  private val second = Pickable("second", wrestlers,
    Set(PowerWindow.SearchModifierSelection))
  private val pool = Vector(atTravel, atSearch, second)

  private def picked(card: DenizenId, id: String = search): Answered =
    Answered(id, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Denizen(card)), p1)
  private val started =
    Answered(search, DecisionAnswer.ChooseOneAnswer(ModifierPicks.start), p1)

  test("the decision is named for its action"):
    assertEquals(search, "search.modifiers")
    assertEquals(ModifierPicks.decisionId(
      PowerWindow.TravelModifierSelection), "travel.modifiers")
    assert(ModifierPicks.isDecision(search))
    assert(!ModifierPicks.isDecision("search.source"))

  test("the picks are the powers the answers name, in pick order, at the " +
      "answered window"):
    val answered = Vector(picked(wrestlers), picked(tents), started)
    assertEquals(ModifierPicks.picks(pool, answered),
      Vector(second.id, atSearch.id))
    assertEquals(ModifierPicks.cards(answered),
      Vector[CardId](wrestlers, tents))

  test("an automatic power on a picked card is not a pick"):
    val automatic = Pickable("auto", tents,
      Set(PowerWindow.SearchModifierSelection), automatic = true)
    assertEquals(ModifierPicks.picks(Vector(automatic), Vector(picked(tents))),
      Vector.empty)

  test("Start ends the decision; settled means never asked, started, or " +
      "moved on"):
    val source = Answered("search.source", DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("world")), p1)
    assert(!ModifierPicks.started(Vector(picked(tents)), search))
    assert(ModifierPicks.started(Vector(picked(tents), started), search))
    assert(ModifierPicks.settled(Vector.empty))
    assert(!ModifierPicks.settled(Vector(picked(tents))))
    assert(ModifierPicks.settled(Vector(picked(tents), started)))
    assert(ModifierPicks.settled(Vector(picked(tents), source)))

  test("a card is picked at the window its answer names"):
    val answered = Vector(picked(tents))
    assert(ModifierPicks.isPicked(answered,
      PowerWindow.SearchModifierSelection, tents))
    assert(!ModifierPicks.isPicked(answered,
      PowerWindow.TravelModifierSelection, tents))
    assertEquals(ModifierPicks.powerFor(pool,
      PowerWindow.TravelModifierSelection, DecisionOptionRef.Denizen(tents)),
      Some(atTravel))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powerresolver.ModifierPicksSuite"`
Expected: compile error, `ModifierPicks` not found.

- [ ] **Step 3: Add the hooks to `ContributingPower`**

In `ContributingPower.scala`, add `CardId` to the `oathdigital.model` import. After the `Offer` case class, add:

```scala
/** A modifier's own verdict on its option in the modifier decision, given
  * the picks so far (modifier walker design, ruling 5): unavailable with a
  * reason, or available with a warning. */
enum OfferReason:
  case Unavailable(reason: String)
  case Warned(reason: String)
```

Inside `trait ContributingPower`, after `selectionPayments`, add:

```scala
  /** The card a player-selected power is printed on: its option in the
    * modifier decision names this card. None for any other power. */
  def selectionCard: Option[CardId] = None
  /** The modifier-selection windows at which the player may pick this
    * power. Empty for any power that is not player-selected. */
  def selectionWindows: Set[PowerWindow] = Set.empty
  /** Why this power's option in the modifier decision is unavailable or
    * deserves a warning, given the picks so far in `ctx.answered`. A rule
    * that one modifier states about another lives on the card that states
    * it. None by default. */
  def offerReason(ctx: PowerCtx): Option[OfferReason] = None
```

Also reword `selectionPayments`' doc comment to say what it is now:

```scala
  /** What selecting this power pays: the operations the modifier decision
    * runs when the player picks it, and, for an action not yet on that
    * decision, the operations a start's selection is dry-run against
    * (`OathRules.validateModifiers`). Free by default.
    */
```

- [ ] **Step 4: Create `ModifierPicks`**

Create `src/main/scala/oathdigital/gameplay/powerresolver/ModifierPicks.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model._

/** The modifier decision's vocabulary (modifier walker design): the decision
  * an action asks first, one modifier per pass, and the picks read back out
  * of its answers. The picks are the selection: nothing else records them.
  *
  * A pick answers with the modifier's card. "Start" answers with the
  * [[start]] button. The decision is named for its action, so every
  * procedure that shares a modifier window shares its id.
  */
object ModifierPicks:
  /** The answer that ends the decision and starts the action. */
  val start: DecisionOptionRef.Button = DecisionOptionRef.Button("start")

  private val suffix = ".modifiers"

  /** `search.modifiers` for `SearchModifierSelection`. */
  def decisionId(window: PowerWindow): String =
    window.associatedMajorAction.fold(window.key)(_.key) + suffix

  def isDecision(decisionId: String): Boolean = decisionId.endsWith(suffix)

  /** What a modifier's option names it by: its card. */
  def cardRef(card: CardId): Option[DecisionOptionRef] = card match
    case id: DenizenId => Some(DecisionOptionRef.Denizen(id))
    case id: RelicId => Some(DecisionOptionRef.Relic(id))
    case id: EdificeId => Some(DecisionOptionRef.Edifice(id))
    case _ => None

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Relic(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  /** Each pick as its decision id and the card it names, in pick order. */
  private def answers(answered: Vector[Answered])
      : Vector[(String, DecisionOptionRef)] = answered.collect {
    case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
        if isDecision(id) && ref != start => id -> ref
  }

  /** The cards picked, in pick order. */
  def cards(answered: Vector[Answered]): Vector[CardId] =
    answers(answered).flatMap((_, ref) => cardOf(ref))

  private def pickable(power: ContributingPower, ref: DecisionOptionRef)
      : Boolean = power.resolution == PowerResolution.PlayerSelected &&
    power.selectionCard.flatMap(cardRef).contains(ref)

  /** The power in `pool` an answer naming `ref` picks at `window`. */
  def powerFor(pool: Vector[ContributingPower], window: PowerWindow,
      ref: DecisionOptionRef): Option[ContributingPower] =
    pool.find(power => pickable(power, ref) &&
      power.selectionWindows(window))

  /** The powers of `pool` the answers pick, in pick order. */
  def picks(pool: Vector[ContributingPower], answered: Vector[Answered])
      : Vector[PowerId] = answers(answered).flatMap((id, ref) =>
    pool.find(power => pickable(power, ref) &&
      power.selectionWindows.exists(decisionId(_) == id)).map(_.id))

  /** Whether "Start" has been answered at `decisionId`. */
  def started(answered: Vector[Answered], decisionId: String): Boolean =
    answered.exists(answer => answer.decisionId == decisionId &&
      answer.answer == DecisionAnswer.ChooseOneAnswer(start))

  /** Whether no more picks can follow: no modifier decision was answered,
    * "Start" was, or a later decision was. A walk whose every modifier is
    * picked starts by itself, so "Start" is not always answered. */
  def settled(answered: Vector[Answered]): Boolean =
    val asked = answered.filter(answer => isDecision(answer.decisionId))
    asked.isEmpty ||
      asked.exists(_.answer == DecisionAnswer.ChooseOneAnswer(start)) ||
      answered.lastOption.exists(answer => !isDecision(answer.decisionId))

  /** Whether `card` has been picked at `window`. */
  def isPicked(answered: Vector[Answered], window: PowerWindow,
      card: CardId): Boolean = cardRef(card).exists(ref =>
    answered.exists(answer => answer.decisionId == decisionId(window) &&
      answer.answer == DecisionAnswer.ChooseOneAnswer(ref)))
```

- [ ] **Step 5: Run the vocabulary suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powerresolver.ModifierPicksSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Write the failing `SelectedModifier` tests**

Append to `SelectedModifierSuite.scala`. The suite's `Probe`, `ctx`, `travel` and `tentsAtAncientCity` are already there. Add `import oathdigital.gameplay.powerresolver.ModifierPicks` to the imports.

```scala
  test("its option names its card, at its actions' selection windows"):
    val both = Probe(card, Cost.free,
      Set(MajorActionType.Travel, MajorActionType.Search))
    assertEquals(both.selectionCard, Some(card))
    assertEquals(both.selectionWindows, Set[PowerWindow](
      PowerWindow.TravelModifierSelection,
      PowerWindow.SearchModifierSelection))

  test("once picked it applies at its selection window, though it could not " +
      "be paid for again"):
    val priced = Probe(card, Cost(favor = 1), travel)
    // The card now holds the favor the pick paid, so a second payment would
    // be refused; the pick still applies, for its option restrictions.
    val paid = tentsAtAncientCity(0).tokens(card, favor = 1).ready
    val answered = Vector(Answered(
      ModifierPicks.decisionId(PowerWindow.TravelModifierSelection),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)), p1))
    val window = PowerWindow.TravelModifierSelection
    assert(!priced.applicable(ctx(paid, priced, window)))
    assert(priced.applicable(ctx(paid, priced, window)
      .copy(answered = answered)))
```

- [ ] **Step 7: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.SelectedModifierSuite"`
Expected: the two new tests FAIL (`selectionCard` is `None`; the picked probe is not applicable).

- [ ] **Step 8: Implement the hooks in `SelectedModifier`**

In `SelectedModifier.scala`, add `ModifierPicks` to the `oathdigital.gameplay.powerresolver` import. Inside the trait, after `appliesAt`, add:

```scala
  override def selectionCard: Option[CardId] = Some(cardId)
  override def selectionWindows: Set[PowerWindow] =
    actions.map(SelectedModifier.selectionWindow)
```

Replace `applicable`:

```scala
  /** At its selection window: a picked power always applies there, so its
    * option restrictions reach the options left; an unpicked one applies
    * when the player may select it now. */
  final override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window) match
      case Some(action) => actions(action) &&
        (ModifierPicks.isPicked(ctx.answered, ctx.window, cardId) ||
          selectable(ctx.state, ctx.activePlayer))
      case None => SelectedModifier.isEligibility(ctx.window) ||
        appliesAt(ctx)
```

In `object SelectedModifier`, after `selectionAction`, add:

```scala
  private val windowOf: Map[MajorActionType, PowerWindow] =
    selection.map(_.swap)

  /** The modifier-selection window of `action`. */
  def selectionWindow(action: MajorActionType): PowerWindow = windowOf(action)
```

Update the trait's doc comment: the first bullet of "`applicable` answers three questions" now reads "At a `*ModifierSelection` window it asks "may the player select this now?" ... A power already picked in `ctx.answered` always applies there."

- [ ] **Step 9: Run the `SelectedModifier` suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.SelectedModifierSuite"`
Expected: PASS.

- [ ] **Step 10: Give `WalkerPowers` its pool and `withAnswers`**

In `WalkerPowers.scala`, change the imports to:

```scala
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.{ContributingPower, ModifierPicks,
  PowerWindowIndex}
import oathdigital.model.{Answered, OperationRestriction, PowerId,
  PowerResolution}
```

Add a `pool` field after `modifiers`, and `withAnswers` after `quiet`:

```scala
final case class WalkerPowers(powers: Vector[ContributingPower],
    probing: Boolean = true,
    restrictionSet: OperationRestrictions = OperationRestrictions.none,
    modifiers: Vector[PowerId] = Vector.empty,
    pool: Vector[ContributingPower] = Vector.empty):
```

```scala
  /** These powers with the modifiers picked in `answered` added (modifier
    * walker design, "Active powers"). Picks are only ever added: a dry run
    * that starts from no answers keeps the picks already made. Without a
    * pool there is nothing to add from. */
  def withAnswers(answered: Vector[Answered]): WalkerPowers =
    val wanted = (modifiers ++ ModifierPicks.picks(pool, answered)).distinct
    if wanted == modifiers then this
    else WalkerPowers.selected(copy(powers = pool), wanted)
```

In `selected`, keep the catalog as the pool:

```scala
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    catalog.copy(powers = catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)) :+ FollowUpSupplyWaiver,
      modifiers = modifiers, pool = catalog.powers)
```

Add one sentence to the class doc comment: "`pool` is the full catalog a selection was made from, so the modifiers a walk picks can join it (`withAnswers`)."

- [ ] **Step 11: Apply the picks at every walker entry**

In `ProcedureWalker.scala`:

1. In `advance`, pass `powers.withAnswers(answered)` to the `WalkCtx` in place of `powers`.
2. In `roll` and in `resolve`, pass `powers.withAnswers(pending.answered)` in place of `powers`.
3. In `narrowParked`, pass `powers.withAnswers(pending.answered)` in place of `powers`.
4. In `runNarrowed`, the `AnswerResume` case for a matching `Decide` becomes:

```scala
              case decide: Decide if decide.decisionId == answer.decisionId =>
                // A pick joins the powers at once, so the rest of this
                // command walks with it (modifier walker design, ruling 4).
                WalkerSteps.answerDecide(decide, ctx, path, answer,
                    contributions)
                  .map(next => Done(next.copy(
                    powers = next.powers.withAnswers(next.answered))))
```

In `WalkerPowerGather.scala`:

1. `leafAt` resolves with `powers.withAnswers(pending.answered)`. Bind it once (`val picked = powers.withAnswers(pending.answered)`) and pass `picked` to both `resolveAt` and `narrowParked`.
2. `restrictionViolations` passes `powers.withAnswers(answered)` to `attributed`.

`parkedRoll`, `openDecisions`, `parkedDecide`, `awaitedPlayer` and `awaitedPlayers` all go through `leafAt`, and `WalkerSimulation` goes through `advance`, `resolve` and `parkedDecide`, so they need nothing more.

- [ ] **Step 12: Run the walker and power suites**

Run: `./sbtw "testOnly oathdigital.gameplay.*"`
Expected: PASS. No walk answers a modifier decision yet, so `withAnswers` returns the same powers everywhere.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ModifierPicks.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/test/scala/oathdigital/gameplay/powerresolver/ModifierPicksSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala
git commit -m "feat(walker): read modifier picks from the answers"
```

---

### Task 3: The modifier decision node

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Create: `src/test/scala/oathdigital/gameplay/walker/ModifierChoiceSuite.scala`

**Interfaces:**
- Consumes: everything Task 2 produces.
- Produces:
  - `ModifierChoice.loop(actor: PlayerId, window: PowerWindow, rest: Operation, startLabel: String, explain: Option[WalkerProcedureRegistry.OptionPricing] = None): Operation`. It is a `Repeat` of `ModifierChoice` passes. A tree runs it first, then `rest`.
  - `ModifierChoice.heading: String = "Choose modifiers, or start"`
  - `ModifierChoice.cannotStart`, `unusable` and `unpayable`: the texts in Global Constraints.

The suite drives a hand-built tree through `ProcedureWalker`, with stand-in powers, so the node is proven before any action uses it. The tree is `Sequence(loop, rest)`. `rest` asks "Go", then spends `rest` Supply in a `SearchCost` window. A stand-in that waives the cost proves that a pick is active in the rest of the walk.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/walker/ModifierChoiceSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, OfferReason, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** The modifier decision on its own: a hand-built tree whose first decision
  * is `ModifierChoice.loop`, walked with stand-in modifiers (modifier walker
  * design, rulings 1 to 6, and plan-time rulings 2 to 4). */
class ModifierChoiceSuite extends munit.FunSuite:
  private val window = PowerWindow.SearchModifierSelection
  private val decisionId = ModifierPicks.decisionId(window)
  private val alpha = CatalogNames.denizen("Wrestlers")
  private val beta = CatalogNames.denizen("Mercenaries")
  private val gamma = CatalogNames.denizen("Errand Boy")
  /** A card p1 does not hold. */
  private val outside = CatalogNames.denizen("Homesteaders")
  private val go = DecisionOptionRef.Button("go")

  /** A stand-in modifier on `card`, pickable at the Search window. */
  private final case class Stand(name: String, card: DenizenId,
      cost: Cost = Cost.free,
      reason: PowerCtx => Option[OfferReason] = _ => None,
      effects: Map[PowerWindow, Vector[Contribution]] = Map.empty)
      extends ContributingPower:
    val id: PowerId = PowerId(s"stand.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = effects
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = Set(window)
    override def offerReason(ctx: PowerCtx): Option[OfferReason] = reason(ctx)
    override def selectionPayments(ready: ReadyGame, actor: PlayerId)
        : Vector[CoreOperation] =
      if cost == Cost.free then Vector.empty
      else Vector(Costs.onCard(actor, card, cost, catalog))

  /** Asks "Go", then spends `supply` Supply at the Search cost window. */
  private def rest(supply: Int): Operation = Sequence(Vector(
    Decide("stand.go", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(go, "Go")))),
    BuildOps((_, _) => Right(Vector(SpendSupply(p1, supply))),
      window = Some(PowerWindow.SearchCost), required = true)))

  private def tree(supply: Int = 1): Operation =
    val after = rest(supply)
    Sequence(Vector(ModifierChoice.loop(p1, window, after, "Start"), after))

  private def powers(stands: Stand*): WalkerPowers =
    WalkerPowers.selected(WalkerPowers(stands.toVector), Vector.empty)

  /** p1 at Ancient City with `favor` favor and `supply` Supply, holding
    * alpha, beta and gamma faceup. */
  private def table(favor: Int = 3, supply: Int = 5): ReadyGame =
    Table.start.pawn(p1, "Ancient City").favor(p1, favor)
      .supply(p1, supply).adviser(p1, alpha).adviser(p1, beta)
      .adviser(p1, gamma).ready

  /** Where a walk parked, with the state its steps left, as replay leaves
    * it. */
  private final case class At(ready: ReadyGame, pending: PendingTree,
      events: Vector[OathEvent])

  private def parked(from: ReadyGame, before: PendingTree,
      outcome: WalkerOutcome)(using munit.Location): At = outcome match
    case WalkerOutcome.Parked(next, events) =>
      val start: OathState = OathState.Ready(from.updateCurrent(
        _.copy(walkerPending = Some(before))))
      val after = events.collect { case step: WalkerStepRecorded => step }
        .foldLeft(start)((state, step) =>
          accepted(ProcedureWalker.applyRecorded(state, step)))
      At(Outcomes.ready(after).updateCurrent(
        _.copy(walkerPending = Some(next))), next, events)
    case other => fail(s"expected a park, got $other")

  private def begin(ready: ReadyGame, on: Operation, walking: WalkerPowers)
      (using munit.Location): At =
    parked(ready, PendingTree(Vector.empty, Vector.empty),
      accepted(ProcedureWalker.advance(ready, on, None, walking)))

  private def answer(at: At, on: Operation, walking: WalkerPowers,
      id: String, ref: DecisionOptionRef)(using munit.Location): At =
    parked(at.ready, at.pending, accepted(ProcedureWalker.resolve(at.ready,
      on, at.pending, Answered(id, DecisionAnswer.ChooseOneAnswer(ref), p1),
      walking)))

  private def decide(at: At, on: Operation, walking: WalkerPowers)
      (using munit.Location): Decide =
    ProcedureWalker.parkedDecide(at.ready, on, at.pending, walking)
      .getOrElse(fail("not parked on a decision"))

  private def options(at: At, on: Operation, walking: WalkerPowers)
      (using munit.Location): Vector[DecisionOption] =
    decide(at, on, walking).query match
      case DecisionQuery.ChooseOne(listed, _) => listed
      case other => fail(s"expected a choose-one, got $other")

  private def card(id: DenizenId): DecisionOption =
    DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
  private val start = DecisionOption.Button(ModifierPicks.start, "Start")
  private def unavailable(option: DecisionOption, reason: String) =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

  test("with nothing to pick, nothing is asked"):
    val on = tree()
    val walked = powers(Stand("away", outside))
    assertEquals(decide(begin(table(), on, walked), on, walked).decisionId,
      "stand.go")

  test("it lists Start first, then each modifier the player may use"):
    val on = tree()
    val walked = powers(Stand("a", alpha), Stand("b", beta))
    val at = begin(table(), on, walked)
    assertEquals(decide(at, on, walked).decisionId, decisionId)
    assertEquals(decide(at, on, walked).query, DecisionQuery.ChooseOne(
      Vector(start, card(alpha), card(beta)),
      heading = Some(ModifierChoice.heading)))

  test("a modifier is priced from its payment, or unavailable with the " +
      "shortfall"):
    val on = tree()
    val walked = powers(Stand("a", alpha, Cost(favor = 2)),
      Stand("b", beta, Cost(favor = 4)))
    assertEquals(options(begin(table(favor = 3), on, walked), on, walked),
      Vector(start,
        DecisionOption.Priced(card(alpha), OptionPrice(favor = 2)),
        unavailable(card(beta), "Needs 4 favor; you have 3")))

  test("a modifier nobody can pay for is not asked about"):
    val on = tree()
    val walked = powers(Stand("b", beta, Cost(favor = 4)))
    assertEquals(decide(begin(table(favor = 3), on, walked), on, walked)
      .decisionId, "stand.go")

  test("a pick pays at once, and the next pass prices what is left after " +
      "it"):
    val on = tree()
    val walked = powers(Stand("a", alpha, Cost(favor = 2)),
      Stand("b", beta, Cost(favor = 2)))
    val first = begin(table(favor = 3), on, walked)
    val second = answer(first, on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assert(second.events.collect { case step: WalkerStepRecorded => step.ops }
      .flatten.exists {
        case PayCost(_, Location.OnCard(paid), _, _, _, _) => paid == alpha
        case _ => false
      })
    assertEquals(options(second, on, walked), Vector(start,
      unavailable(card(beta), "Needs 2 favor; you have 1")))

  test("a modifier's own reason makes it unavailable, or warns, from the " +
      "picks"):
    def after(ref: CardId)(ctx: PowerCtx): Boolean =
      ModifierPicks.isPicked(ctx.answered, window, ref)
    val on = tree()
    val walked = powers(Stand("a", alpha),
      Stand("b", beta, reason = ctx => Option.when(after(alpha)(ctx))(
        OfferReason.Unavailable("Not with Wrestlers"))),
      Stand("c", gamma, reason = ctx => Option.when(after(alpha)(ctx))(
        OfferReason.Warned("Wasted with Wrestlers"))))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked), Vector(start,
      unavailable(card(beta), "Not with Wrestlers"),
      DecisionOption.Warned(card(gamma),
        OptionReason.Written("Wasted with Wrestlers"))))

  test("a picked modifier's option restriction acts on the options left"):
    val forbids = OptionRestriction(
      (_, ref) => Option.when(ref == DecisionOptionRef.Denizen(beta))(
        OathViolation.InvalidEventOrder("not with alpha")),
      note = (_, _) => Some(PowerNote(PowerSourceRef.Card(alpha), "barred",
        Vector.empty)))
    val restricting = Stand("a", alpha,
      effects = Map(window -> Vector(forbids)))
    val on = tree()
    val walked = powers(restricting, Stand("b", beta))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked), Vector(start,
      DecisionOption.Unavailable(card(beta), OptionReason.Noted(
        restricting.id, PowerNote(PowerSourceRef.Card(alpha), "barred",
          Vector.empty)))))

  test("Start says why the action cannot go on, and a pick that makes it " +
      "possible opens it"):
    val waive = Transform((_, _) => Vector.empty)
    val waiver = Stand("a", alpha,
      effects = Map(PowerWindow.SearchCost -> Vector(waive)))
    val on = tree(supply = 2)
    val walked = powers(waiver, Stand("b", beta))
    val first = begin(table(supply = 0), on, walked)
    assertEquals(options(first, on, walked).head,
      unavailable(start, ModifierChoice.cannotStart))
    val picked = answer(first, on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked).head, start)
    val begun = answer(picked, on, walked, decisionId, ModifierPicks.start)
    assertEquals(decide(begun, on, walked).decisionId, "stand.go")
    val done = accepted(ProcedureWalker.resolve(begun.ready, on,
      begun.pending, Answered("stand.go",
        DecisionAnswer.ChooseOneAnswer(go), p1), walked))
    assert(done.isInstanceOf[WalkerOutcome.Finished], done)

  test("once every modifier is picked, the action starts by itself"):
    val on = tree()
    val walked = powers(Stand("a", alpha))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(decide(picked, on, walked).decisionId, "stand.go")
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ModifierChoiceSuite"`
Expected: compile error, `ModifierChoice` not found.

- [ ] **Step 3: Create `ModifierChoice`**

Create `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala`. Mind the import rules: this file names no card and never spells the selected-modifier trait's name.

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.{ContributingPower, ModifierPicks,
  OfferReason, PowerCtx}
import oathdigital.model._

/** One pass of an action's modifier decision (modifier walker design): the
  * walk's first decision, a `Repeat` of these passes that ends on "Start".
  * The modifier counterpart of the battle-plan window's pass.
  *
  * A pass lists "Start" first, then every player-selected power that may be
  * picked at the window and whose card the actor may use, less those
  * already picked. Each is judged by its own `offerReason`, then by a dry
  * run of its payment, then by whether it applies now. A pick pays at once,
  * as the pass's second child, so the next pass is priced after it.
  *
  * "Start" is unavailable, with a reason, when no answer to the action's
  * first decision can be completed under the picks.
  *
  * The first pass asks only when a modifier can be picked. A later pass asks
  * while any modifier is still listed. A walk resuming inside a pass keeps
  * its two children whatever is listed.
  */
final class ModifierChoice(val actor: PlayerId, modifierWindow: PowerWindow,
    rest: Operation, startLabel: String,
    explain: Option[WalkerProcedureRegistry.OptionPricing])
    extends Operation:
  override val window: Option[PowerWindow] = Some(modifierWindow)
  override val children: Vector[Operation] = Vector.empty
  private val decisionId = ModifierPicks.decisionId(modifierWindow)

  def expand(pass: ModifierChoice.Pass): Vector[Operation] =
    val picked = ModifierPicks.picks(pass.powers.pool, pass.answered).toSet
    val listed = candidates(pass).filterNot(power => picked(power.id))
      .flatMap(option(pass, _))
    val asks = listed.exists(_.available) ||
      (picked.nonEmpty && listed.nonEmpty)
    if !asks && !pass.resuming then Vector.empty
    else Vector(
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        start(pass) +: listed, heading = Some(ModifierChoice.heading))),
      payment(pass.powers))

  private def candidates(pass: ModifierChoice.Pass)
      : Vector[ContributingPower] = pass.powers.pool.filter(power =>
    power.resolution == PowerResolution.PlayerSelected &&
      power.selectionWindows(modifierWindow) &&
      power.selectionCard.exists(card =>
        PowerAccess.locate(pass.state, actor, card).isDefined))
    .sortBy(ContributingPower.sortKey)

  private def option(pass: ModifierChoice.Pass, power: ContributingPower)
      : Option[DecisionOption] = for
    card <- power.selectionCard
    ref <- ModifierPicks.cardRef(card)
    shown <- DecisionOption.forRef(ref)
  yield
    val ctx = PowerCtx(pass.state, actor, power.source, modifierWindow,
      Vector.empty, this, None, pass.answered)
    power.offerReason(ctx) match
      case Some(OfferReason.Unavailable(reason)) => unavailable(shown, reason)
      case judged =>
        val priced = WalkerSimulation.applies(paying(power), pass.state,
            pass.powers.quiet) match
          case Left(_) => unavailable(shown, shortfall(pass.state,
            power.selectionPayments(pass.state, actor)))
          case Right(_) if !power.applicable(ctx) =>
            unavailable(shown, ModifierChoice.unusable)
          case Right(operations) => price(shown, operations)
        judged match
          case Some(OfferReason.Warned(reason)) if priced.available =>
            DecisionOption.Warned(priced, OptionReason.Written(reason))
          case _ => priced

  /** The pick's payment, onto its card, required. */
  private def paying(power: ContributingPower): Operation =
    BuildOps((ready, _) => Right(power.selectionPayments(ready, actor)),
      required = true)

  /** After the pass's answer: the payment of the modifier it picked. */
  private def payment(powers: WalkerPowers): Operation =
    Branch((_, tree) => tree.answered.reverseIterator.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) =>
        ref
    }.flatMap(ModifierPicks.powerFor(powers.pool, modifierWindow, _))
      .toVector.map(paying))

  private def price(shown: DecisionOption, operations: Vector[CoreOperation])
      : DecisionOption =
    val paid = operations.foldLeft(OptionPrice()):
      case (total, PayCost(_, _, cost, _, _, _)) => total.copy(
        favor = total.favor + cost.favor,
        secrets = total.secrets + cost.secret,
        favorBurnt = total.favorBurnt + cost.favorBurnt,
        secretsBurnt = total.secretsBurnt + cost.secretBurnt)
      case (total, _) => total
    if paid.isFree then shown else DecisionOption.Priced(shown, paid)

  /** Why `payments` cannot be made, read off the actor's board: the
    * pipeline's refusal carries only a code. */
  private def shortfall(state: ReadyGame, payments: Vector[CoreOperation])
      : String =
    val costs = payments.collect { case PayCost(_, _, cost, _, _, _) => cost }
    val favor = costs.map(cost => cost.favor + cost.favorBurnt).sum
    val secrets = costs.map(cost => cost.secret + cost.secretBurnt).sum
    state.game.current.players.find(_.player == actor).map(_.board)
      .flatMap(board =>
        if board.favor < favor then Some(needs(favor, "favor", board.favor))
        else if board.faceUpSecrets < secrets then Some(needs(secrets,
          if secrets == 1 then "secret" else "secrets", board.faceUpSecrets))
        else None)
      .getOrElse(ModifierChoice.unpayable)

  private def needs(required: Int, what: String, available: Int): String =
    s"Needs $required $what; you have $available"

  private def start(pass: ModifierChoice.Pass): DecisionOption =
    val button = DecisionOption.Button(ModifierPicks.start, startLabel)
    refusal(pass).fold[DecisionOption](button)(unavailable(button, _))

  /** Why no answer to the action's first decision completes under the
    * picks, or `None` when one does. */
  private def refusal(pass: ModifierChoice.Pass): Option[String] =
    val powers = pass.powers.quiet
    val previewed = WalkerSimulation.preview(rest, pass.state, powers)
    if previewed.exists(_.exists(_.outcome.isRight)) then None
    else Some(explained(pass.state, powers)
      .getOrElse(ModifierChoice.cannotStart))

  /** The first refused option of the action's first decision, as the
    * procedure prices it. */
  private def explained(state: ReadyGame, powers: WalkerPowers)
      : Option[String] = for
    pricing <- explain
    pending <- ProcedureWalker.advance(state, rest, None, powers).toOption
      .collect { case WalkerOutcome.Parked(at, _) => at }
    decide <- ProcedureWalker.parkedDecide(state, rest, pending, powers,
      probing = false)
    reason <- pricing(state, rest, pending, powers, decide).collectFirst {
      case DecisionOption.Unavailable(_, OptionReason.Written(text)) => text
    }
  yield reason

  private def unavailable(option: DecisionOption, reason: String)
      : DecisionOption =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

object ModifierChoice:
  /** What the walker tells a pass about this fold: the state, the answers
    * so far, whether the walk resumes inside the pass, and the powers it
    * walks with, picks included. */
  final case class Pass(state: ReadyGame, answered: Vector[Answered],
      resuming: Boolean, powers: WalkerPowers)

  val heading: String = "Choose modifiers, or start"
  val cannotStart: String = "Cannot start now"
  val unusable: String = "Cannot be used now"
  val unpayable: String = "Cannot be paid for now"

  /** The modifier decision for `window`, as the first node of an action's
    * tree: passes until "Start". `rest` is what the tree runs after it,
    * which "Start" previews; `explain` is the procedure's option pricing,
    * which words why "Start" is unavailable. */
  def loop(actor: PlayerId, window: PowerWindow, rest: Operation,
      startLabel: String,
      explain: Option[WalkerProcedureRegistry.OptionPricing] = None)
      : Operation =
    val id = ModifierPicks.decisionId(window)
    Repeat((_, pending) => !ModifierPicks.started(pending.answered, id),
      new ModifierChoice(actor, window, rest, startLabel, explain))
```

- [ ] **Step 4: Expand the node in the gather**

In `WalkerPowerGather.applyWindow`, add a case after the `OfferHost` case (keep batch 6's edits to the `OfferHost` case if they have landed):

```scala
          case choice: ModifierChoice =>
            (restrictOptions(folded ++ choice.expand(ModifierChoice.Pass(
              state, answered, resuming, powers)),
              gathered.optionRestrictions, ctxFor, byId), gathered.order)
```

Add one line to the method's doc comment: "When the node is a [[ModifierChoice]], its pass is expanded with these powers, and the window's option restrictions apply to its decision."

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ModifierChoiceSuite"`
Expected: PASS, 9 tests. If a test fails on an option's exact shape (a `Priced` around a card, or a `Warned` around a `Priced`), fix the code to match the test, not the test. The shapes follow `PlanPrice.priced` and `CardSlots`.

- [ ] **Step 6: Run the walker and architecture suites**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. A failure in "the engine never learns its powers" names a power in `ModifierChoice.scala`; reword it.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/test/scala/oathdigital/gameplay/walker/ModifierChoiceSuite.scala
git commit -m "feat(walker): the modifier decision node"
```

---

### Task 4: Search and facedown-adviser play choose their modifiers in the walk

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Create: `src/test/scala/oathdigital/gameplay/ModifierPlay.scala`
- Create: `src/test/scala/oathdigital/gameplay/SearchModifierChoiceSuite.scala`
- Modify: the test helpers and suites listed in Step 10

**Interfaces:**
- Consumes: `ModifierChoice.loop`, `ModifierPicks.*` (Tasks 2 and 3).
- Produces:
  - `WalkerProcedureRegistry.choosesModifiers(procedure: ProcedureRef): Boolean`
  - `SelectedModifier.paidAtPick: Set[MajorActionType]`
  - `CardPlayProcedure.unchecked(catalog, ready, actor, card, origin, picked: Vector[CardId] = Vector.empty)`
  - `OperationRestrictions.active(powerRestrictions, modifiers, picked: Vector[CardId] = Vector.empty)`
  - Test helpers:
    - `ModifierPlay.pick(power: PowerId, powers: WalkerPowers = …): DecisionAnswer`
    - `ModifierPlay.start: DecisionAnswer`
    - `ModifierPlay.started(rules, state, procedure, actor, window, modifiers, powers = …)`
    - `ModifierPlay.answers(window, modifiers, powers = …): Answers`

- [ ] **Step 1: Write the test helper**

Create `src/test/scala/oathdigital/gameplay/ModifierPlay.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{Answers, ParkedNode}

/** A start that picks its modifiers through the walk's modifier decision,
  * as a player does (modifier walker design). */
object ModifierPlay:
  private val production: WalkerPowers = WalkerPowerCatalog.default(catalog)

  /** The answer that picks `power`: its card. */
  def pick(power: PowerId, powers: WalkerPowers = production)
      : DecisionAnswer =
    powers.powers.find(_.id == power).flatMap(_.selectionCard)
      .flatMap(ModifierPicks.cardRef)
      .fold(throw new IllegalArgumentException(
        s"${power.value} is not a player-selected power"))(
        DecisionAnswer.ChooseOneAnswer(_))

  /** The answer that ends the decision and starts the action. */
  val start: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ModifierPicks.start)

  /** `actor` starts `procedure`, picks `modifiers` in order and starts. With
    * no modifier, "Start" is answered only when the decision was asked. The
    * events are every command's. */
  def started(rules: OathRules, state: OathState, procedure: StartableRef,
      actor: PlayerId, window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production)
      : Either[OathViolation, OathTransition] =
    val decisionId = ModifierPicks.decisionId(window)
    def answer(from: OathTransition, given: DecisionAnswer) =
      rules.resolveWalker(from.state, actor, decisionId, given)
        .map(next => next.copy(events = from.events ++ next.events))
    for
      begun <- rules.startWalker(state, procedure, actor)
      picked <- modifiers.foldLeft[Either[OathViolation, OathTransition]](
        Right(begun))((done, power) => done.flatMap(answer(_,
          pick(power, powers))))
      ended <- if asking(picked.state, decisionId, powers) then
          answer(picked, start)
        else Right(picked)
    yield ended

  /** A `Situation`'s answers at `window`'s decision: each of `modifiers`
    * not yet picked, in order, then "Start". */
  def answers(window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production): Answers =
    case park if park.decisionId == ModifierPicks.decisionId(window) =>
      val answered = park.ready.game.current.walkerPending
        .fold(Vector.empty[Answered])(_.answered)
      val done = ModifierPicks.picks(powers.powers, answered)
      modifiers.find(!done.contains(_)).fold(start)(pick(_, powers))

  private def asking(state: OathState, decisionId: String,
      powers: WalkerPowers): Boolean =
    ParkedNode.of(state, catalog, powers).toOption.flatten
      .exists(_.decisionId == decisionId)
```

- [ ] **Step 2: Write the failing Search suite**

Create `src/test/scala/oathdigital/gameplay/SearchModifierChoiceSuite.scala`. It uses real cards. News from Afar costs 2 favor and makes the Search's Supply free. Wild Cry, at the actor's site, is offered for a Search and for a facedown-adviser play.

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.cardplay.WildCry
import oathdigital.gameplay.powers.search.NewsFromAfar
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Outcomes, ParkedNode, PowerRules,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Search and facedown-adviser play ask for their modifiers inside the walk
  * (modifier walker design, slice 1). */
class SearchModifierChoiceSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerPowers
  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val search = ModifierPicks.decisionId(
    PowerWindow.SearchModifierSelection)
  private val news = CatalogNames.denizen("News from Afar")

  /** p1 at Ancient City with `supply` Supply and `favor` favor, News from
    * Afar faceup. */
  private def withNews(supply: Int, favor: Int): ReadyGame =
    Table.start.pawn(p1, "Ancient City").supply(p1, supply).favor(p1, favor)
      .adviser(p1, news).ready

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Search, p1))

  /** The decision the walk is parked on, rebuilt as the projector does. */
  private def parked(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, walkerPowers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def options(transition: OathTransition)(using munit.Location)
      : Vector[DecisionOption] = parked(transition.state).query match
    case DecisionQuery.ChooseOne(listed, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  private def newsOption: DecisionOption =
    DecisionOption.Denizen(DecisionOptionRef.Denizen(news))

  test("a Search starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(withNews(supply = 5, favor = 2))
    assertEquals(parked(begun.state).decisionId, search)
    assertEquals(options(begun), Vector(
      DecisionOption.Button(ModifierPicks.start, "Start Search"),
      DecisionOption.Priced(newsOption, OptionPrice(favor = 2))))

  test("with no modifier to pick, a Search starts on its source"):
    val begun = started(Table.start.pawn(p1, "Ancient City").ready)
    assertEquals(parked(begun.state).decisionId,
      SearchProcedure.sourceDecisionId)

  test("Start says why the Search cannot go on, until a pick makes it free"):
    val begun = started(withNews(supply = 0, favor = 2))
    assertEquals(options(begun).head, DecisionOption.Unavailable(
      DecisionOption.Button(ModifierPicks.start, "Start Search"),
      OptionReason.Written("Needs 2 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1, search,
      ModifierPlay.pick(NewsFromAfar.id)))
    // The favor is paid at the pick, onto the card.
    assertEquals(Outcomes.ready(picked.state).game.current.players
      .find(_.player == p1).map(_.board.favor), Some(0))
    assertEquals(parked(picked.state).decisionId,
      SearchProcedure.sourceDecisionId)

  test("a start may no longer carry modifiers for a Search"):
    assertEquals(rejected(rules.startWalker(Ready(withNews(5, 2)),
      ActionRef.Search, p1, Vector(NewsFromAfar.id))),
      OathViolation.InvalidEventOrder(
        "search chooses its modifiers inside the walk"))

  test("a picked modifier pays once, at the pick"):
    val ready = withNews(supply = 5, favor = 3)
    val searched = accepted(SearchPlay.searched(rules, Ready(ready), p1,
      modifiers = Vector(NewsFromAfar.id)))
    assertEquals(Outcomes.ready(searched.state).game.current.players
      .find(_.player == p1).map(_.board.favor), Some(1))

  test("a facedown-adviser play offers the Search modifiers first"):
    val wildCry = CatalogNames.denizen("Wild Cry")
    val ready = Table.start.pawn(p1, "Ancient City")
      .denizen(wildCry, at = "Ancient City")
      .adviser(p1, "Errand Boy", facedown = true).ready
    val begun = accepted(rules.startWalker(Ready(ready),
      ActionRef.PlayFacedownAdviser, p1))
    assertEquals(parked(begun.state).decisionId, search)
    assertEquals(options(begun).head,
      DecisionOption.Button(ModifierPicks.start, "Play facedown adviser"))
    val picked = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.PlayFacedownAdviser, p1, PowerWindow.SearchModifierSelection,
      Vector(WildCry.id)))
    assertEquals(parked(picked.state).decisionId,
      CardPlayProcedure.facedownAdviserDecisionId)
```

`parked(state)` is a guess at `ParkedDecisionAssertions`' reader. Read `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertions.scala` first, and use the reader it has for the parked `Decide`; keep the assertions as written. Table steps that do not exist under these names (`supply`, `denizen(..., at =)`) are named in `testkit/Table.scala`; use those.

- [ ] **Step 3: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchModifierChoiceSuite"`
Expected: FAIL. The Search parks on its source, and the start with a modifier is accepted.

- [ ] **Step 4: Register which procedures choose their modifiers**

In `WalkerProcedureRegistry.scala`, add a field to `Entry` after `requiresPlayableOption`:

```scala
      /** Whether the tree asks for its modifiers itself, through the
        * modifier decision (modifier walker design), so a start carries
        * none. */
      choosesModifiers: Boolean = false,
```

Set `choosesModifiers = true` on the `ActionRef.Search` and `ActionRef.PlayFacedownAdviser` entries. After `requiresPlayableOption`, add:

```scala
  /** Whether `procedure` asks for its modifiers inside the walk. */
  def choosesModifiers(procedure: ProcedureRef): Boolean =
    lookup(procedure, entries).exists(_.choosesModifiers)
```

In `OathRulesWalker.startNow`, add a check before `validateModifiers`:

```scala
        _ <- Either.cond(modifiers.isEmpty ||
          !WalkerProcedureRegistry.choosesModifiers(procedure), (),
          InvalidEventOrder(
            s"${procedure.key} chooses its modifiers inside the walk"))
```

`procedure.key` must read `search` for `ActionRef.Search`; the test above pins it.

- [ ] **Step 5: Start Search with the modifier decision**

In `SearchProcedure.scala`:

1. Add the imports `oathdigital.gameplay.powerresolver.ModifierPicks` and `oathdigital.gameplay.walker.ModifierChoice` (beside `WalkerPowers`, `WalkerSimulation`).
2. Split the start gates out of `build`, and make `startable` preview what follows the decision:

```scala
  /** Whether a Search could start now under `powers`: some source survives
    * the preview of what follows the modifier decision. The Act panel asks
    * it of each selection `ModifierOffers.selections` tries. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    gated(state, actor, Vector.empty)
      .map(afterModifiers(catalog, actor, _))
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    gated(ready, actor, args).map(tree(catalog, actor, _))

  /** The start-only gates, and the region searched from. */
  private def gated(ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Region] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    _ <- noSelection(args)
    _ <- Either.cond(ready.game.current.temporaryHands.valuesIterator
      .forall(_.isEmpty), (), OathViolation.SearchDrawMismatch(
      "a temporary card hand already exists"))
    origin <- actorRegion(ready, actor)
  yield origin
```

3. Rename the old `tree` to `afterModifiers` (same body: the source pick and its `Branch`), and add the new `tree`:

```scala
  private def tree(catalog: ExecutableCatalog, actor: PlayerId,
      origin: Region): Operation =
    val rest = afterModifiers(catalog, actor, origin)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.SearchModifierSelection, rest, startLabel,
      Some(SearchOptions.priced)), rest))
```

with `private val startLabel = "Start Search"` beside `heading`.

4. In `body`'s `placement` branch, pass the picked cards to the card play:

```scala
          val cardTree = CardPlayProcedure.unchecked(catalog, ready, actor, kept,
            CardPlayProcedure.Origin.TemporaryHand,
            ModifierPicks.cards(pending.answered))
```

5. Rewrite the object's doc comment to the new shape:

```scala
/** Search on the walker (search source design; modifier walker design).
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),            // the modifiers, one per pass, then
  *                                      // "Start Search"
  *   Decide(search.source),             // a board choose-one: the world deck
  *                                      // and the actor's region's discard
  *   Branch { after the answer:
  *     Sequence(cost, draw, query, placement) })  // window = SearchActionEligibility
  * }}}
  *
  * **The modifiers come first.** Each pick pays at once, so the source
  * decision shows what each source costs under them. The start carries no
  * selection.
  *
  * **The region is read from the pawn.** The pawn does not move during a
  * Search, so a resume reads the same region and needs no start fact.
  *
  * **What does not gate a start.** Supply, tolls and empty piles are not
  * build gates: "Start Search" previews both sources and says why neither
  * can be searched, and with no modifier to pick, the registry's
  * playable-option gate refuses a start neither source can complete.
  * Placement is the shared subtree.
  */
```

- [ ] **Step 6: Start the facedown-adviser play with the modifier decision**

In `CardPlayProcedure.scala`:

1. Add the imports `oathdigital.gameplay.powerresolver.ModifierPicks` and `oathdigital.gameplay.walker.ModifierChoice`.
2. `facedownTree` becomes:

```scala
  /** The modifiers, then the pick, then the picked card's placement. Once
    * the card has been played, a resumed walk rebuilds the pick without it;
    * the walk is past the pick by then, and the placement settles from its
    * answers. */
  private def facedownTree(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId): Operation =
    val pick = Decide(facedownAdviserDecisionId, actor, DecisionQuery.ChooseOne(
      facedownAdvisers(ready, actor).map(adviserOption),
      heading = Some("Choose a facedown adviser to play")))
    // The walk reaches the Branch after the pick's answer, so the placement
    // is planned from the state the card is played in.
    val placement = Branch((current, pending) =>
      pending.answered.collectFirst {
        case Answered(`facedownAdviserDecisionId`,
            DecisionAnswer.ChooseOneAnswer(ref), _) => ref
      }.flatMap(worldCard).toVector.map(card =>
        unchecked(catalog, current, actor, card, Origin.FacedownAdviser,
          ModifierPicks.cards(pending.answered))))
    val rest = Sequence(Vector(pick, placement))
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.SearchModifierSelection, rest, "Play facedown adviser"),
      rest))
```

3. Thread `picked: Vector[CardId] = Vector.empty` through `unchecked`, into `PlacementTree`'s builder, `childrenFor`, `played`, and on to `CardSlots.slots` and `CardSlots.moreDiscards`. Callers that pass nothing (Oracle, the legacy facedown rebuild) keep today's behaviour.

- [ ] **Step 7: Protect the picked cards**

In `OperationRestrictions.scala`:

```scala
  def active(powerRestrictions: Vector[OperationRestriction],
      modifiers: Vector[PowerId], picked: Vector[CardId] = Vector.empty)
      : Vector[OperationRestriction] =
    printed ++ catalog.filter(_ => modifiers.nonEmpty || picked.nonEmpty)
      .map(ActiveModifier(_, modifiers, picked)).toVector ++ powerRestrictions
```

`ActiveModifier` gains `picked: Vector[CardId] = Vector.empty`. `refused(card)` refuses when `picked.contains(card)` or a modifier prints it. Update its doc comment: "a card picked in the modifier decision counts as one a selected modifier prints."

In `CardSlots.scala`, `slots` and `moreDiscards` gain a last parameter `picked: Vector[CardId] = Vector.empty` and pass it to `judged`. `judged` reads:

```scala
      val restrictions = OperationRestrictions.forCatalog(catalog)
        .active(Vector.empty, ready.game.current.walkerModifiers, picked)
```

Add a `CardSlotsSuite` test beside the existing "In use as a modifier this action" test (around line 175). Copy its setup, but pass the card as picked instead of writing `walkerModifiers`:

```scala
  test("a card picked as a modifier this action is refused as a Replace " +
      "slot"):
    val crop = CatalogNames.denizen("Crop Rotation")
    val ready = atHome(crop).hand(p1, card).ready
    assertEquals(atSite(slotsOf(ready, rules = permitted,
      picked = Vector(crop))), Vector(siteSlot(1),
      refused(replacing(siteSlot(0), crop),
        "In use as a modifier this action")))
```

Give the suite's `slotsOf` helper a `picked: Vector[CardId] = Vector.empty` parameter that it passes to `CardSlots.slots`.

- [ ] **Step 8: Pay Search modifiers at the pick only**

In `SelectedModifier.scala`, prepend the payment only for the actions still selected at the start:

```scala
  final override lazy val contributions: Map[PowerWindow, Vector[Contribution]] =
    val payments: Map[PowerWindow, Vector[Contribution]] =
      if cost == Cost.free then Map.empty
      else actions.filterNot(SelectedModifier.paidAtPick)
        .map(action => SelectedModifier.eligibility(action) ->
          Vector[Contribution](Transform((ctx, operations) =>
            payment(ctx.activePlayer) +: operations))).toMap
```

and in the object:

```scala
  /** The actions whose modifier decision pays each pick (modifier walker
    * design): their modifiers prepend no payment at the eligibility window.
    * It grows slice by slice, with `WalkerProcedureRegistry`'s
    * `choosesModifiers`, until slice 4 deletes the payment Transform. */
  val paidAtPick: Set[MajorActionType] = Set(MajorActionType.Search)
```

Update the trait's doc comment paragraph about payment: a Search modifier is paid when picked, and the other actions still pay at the eligibility window until their slices.

`SelectedModifierSuite`'s test "a cost is paid at the root of the action's tree" uses a Travel probe, so it still holds. Add one line to it:

```scala
    val both = Probe(card, Cost(favor = 1),
      Set(MajorActionType.Travel, MajorActionType.Search))
    assertEquals(both.contributions.keySet,
      Set[PowerWindow](PowerWindow.TravelActionEligibility))
```

- [ ] **Step 9: Run the new suites**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchModifierChoiceSuite oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.SelectedModifierSuite"`
Expected: PASS.

- [ ] **Step 10: Move the Search and facedown helpers and suites onto the decision**

The two shared helpers move first. Most power suites reach the Search through them.

- `gameplay/SearchPlay.scala`: `searched` starts through `ModifierPlay.started(rules, state, ActionRef.Search, actor, PowerWindow.SearchModifierSelection, modifiers)`, then answers the source. Update its doc comment.
- `gameplay/FacedownAdviserPlay.scala`: `parkedOnPlacement` starts through `ModifierPlay.started(..., ActionRef.PlayFacedownAdviser, ...)`, then picks the card.

Then run every server suite and move each failing case onto the decision by these rules:

- **A start that passes modifiers** (`startWalker(..., ActionRef.Search, actor, modifiers)` or `GameCommand.StartWalker(ActionRef.Search, StartPayload(p1, modifiers…))`) starts with none and picks them:
  - Through rules, use `ModifierPlay.started`.
  - Through a `Situation`, add `ModifierPlay.answers(PowerWindow.SearchModifierSelection, modifiers)` with `.withAnswers(…)`, combined with the suite's own answers by `orElse`.
  - Through a service, send `GameCommand.ResolveWalker(p1, TreeDecision("search.modifiers", ModifierPlay.pick(id)))` for each pick, then `ModifierPlay.start`.
- **A test of `offerableWalkerPowers`** for Search is unchanged: the start buttons still ask it (spec ruling 9).
- **A test that the start refuses an unpayable or inapplicable Search modifier** now asserts the refusal of a start with modifiers ("search chooses its modifiers inside the walk"). Or it asserts the option unavailable in the decision, when that is what the test is about.
- **A test that a start parked on the source** with a modifier offerable now first answers "Start".

The suites known to start a Search or a facedown play with modifiers:

- `gameplay/powers/search/HuntingPartySuite.scala`
- `application/SearchSourceServiceSuite.scala`. Its test "Undo takes back a start that selected a modifier" becomes the two undo tests below.
- `application/SearchSourceRowsSuite.scala`
- `application/ModifierStartControlsSuite.scala`
  - Its Search test now asserts that the start with no modifier is accepted.
  - The start parks on the modifier decision, with Start unavailable for "Needs 2 Supply; you have 0".
  - Picking News from Afar then reaches the source.
- `application/gamelog/LogScripts.scala` (around line 498, Augury)
- `testkit/Refusals.scala`. Leave its `notApplicable` in place if other actions still use it.

`SearchFixture.start`, `WildCrySuite`, `WelcomingPartySuite`, `FamilyWagonArrivalsSuite`, `AugurySuite`, `DisciplesSuite`, `CropRotationSuite`, `TruthfulHarpSuite` and `NewsFromAfarSuite` reach the Search through `SearchPlay` or `FacedownAdviserPlay`. They should pass unchanged. Fix any that do not by the rules above.

Replace the modifier undo test in `SearchSourceServiceSuite` with:

```scala
  test("Undo takes back a pick, and then the start"):
    val withNews = table.adviser(p1, "News from Afar").favor(p1, 2)
    val (service, _) = withNews.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("search.modifiers",
        ModifierPlay.pick(NewsFromAfar.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withNews.state)
```

Add `import oathdigital.gameplay.ModifierPlay` to it. `TreeDecision` is the walker answer the service already takes. If the suite spells it differently, follow `ParkedServiceFixture.pickSearchSource`.

Run: `./sbtw "test"`
Expected: PASS. Repeat the rules above until it does. If a failure is not covered by them, stop and report it; do not change production code to suit a test.

- [ ] **Step 11: Commit**

Stage the production files above, the two new test files, and every test file you changed. List them with `git status --short`, and stage each by path.

```bash
git add src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/test/scala/oathdigital/gameplay/ModifierPlay.scala \
  src/test/scala/oathdigital/gameplay/SearchModifierChoiceSuite.scala
# then each changed test file by path
git commit -m "feat(search): choose Search modifiers inside the walk"
```

---

### Task 5: The Game Log names the picks

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`

**Interfaces:**
- Consumes: `ModifierPicks.picks`, `ModifierPicks.settled`, `ModifierPicks.isDecision`; `ModifierPlay.answers` (Task 4).

The start line keeps its words: the tag "Search · 2 Supply", then "With News from Afar". Three things change:

- The modifiers come from the answers.
- The modifier decision posts no "Chose" line.
- The tags wait until no more picks can follow: a line may read only to the end of its own segment.

- [ ] **Step 1: Write the failing log tests**

Add two scripts to `LogScripts.scala`, after `augury`. Add the imports `oathdigital.gameplay.ModifierPlay`, `oathdigital.gameplay.powers.cardplay.WildCry` and `oathdigital.gameplay.powers.search.NewsFromAfar`.

```scala
  /** Augury at p1's site and News from Afar as p1's adviser, with 2 favor
    * to pay for it. The Search picks News from Afar, then Augury; every
    * modifier is then picked, so it starts by itself. News from Afar spends
    * no Supply. */
  def twoPicks(using munit.Location): Script =
    val (service, act) = atTable("two-picks", tableWithOnePawnPerSite
      .denizen("Augury", "Ancient City").adviser(p1, "News from Afar")
      .favor(p1, 2).banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.SearchModifierSelection,
      Vector(NewsFromAfar.id, Augury.id))).after(GameCommand.StartWalker(
      ActionRef.Search, StartPayload(p1, Vector.empty, Vector.empty)))
    Script("two-picks", service, p1)

  /** Wild Cry at p1's site and a facedown adviser: the play picks Wild Cry,
    * then plays the adviser by default. */
  def facedownWithCry(using munit.Location): Script =
    val conclave = CatalogNames.denizen("Wizard's Conclave")
    val (service, act) = atTable("facedown-cry", tableWithOnePawnPerSite
      .denizen("Wild Cry", "Ancient City")
      .adviser(p1, conclave, facedown = true).banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.SearchModifierSelection,
      Vector(WildCry.id))).after(GameCommand.StartWalker(
      ActionRef.PlayFacedownAdviser, StartPayload(p1, Vector.empty,
        Vector.empty)))
    Script("facedown-cry", service, p1)
```

Add to `GameLogStartLineSuite.scala`:

```scala
  test("a Search with two picks names both once, in pick order, with no " +
      "Chose line"):
    val entries = format(twoPicks, None)
    val tag = entryStarting(entries, "Search")
    assertEquals(tag.kind, LogKind.Section)
    // News from Afar spends no Supply, so the tag carries no cost.
    assertEquals(text(tag), "Search")
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      "With News from Afar and Augury")
    assertEquals(texts(entries).count(_.startsWith("With ")), 1)
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))

  test("a facedown-adviser play with a pick is tagged at the adviser's " +
      "pick, naming it"):
    val entries = format(facedownWithCry, None)
    val tag = entryStarting(entries, "Play facedown")
    assertEquals(text(entries(entries.indexOf(tag) + 1)), "With Wild Cry")
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))
```

If `LogWords.join` writes two names as something other than "News from Afar and Augury", use its words in the expectation. The test is about which modifiers are named, and in which order.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogStartLineSuite"`
Expected: the new tests FAIL. The Search tag posts at News from Afar's payment, naming only it, and "Chose" lines appear.

- [ ] **Step 3: Read the picks in `StartLines`**

Give `StartLines` the pick reader:

```scala
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution],
    picks: Vector[Answered] => Vector[PowerId]):
```

In `GameLogFormatter`, build it from the same catalog:

```scala
  private val walkerPowers = WalkerPowerCatalog.default(catalog).powers
  private val starts = new StartLines(words,
    (walkerPowers :+ FollowUpSupplyWaiver)
      .map(power => power.id -> power.resolution).toMap,
    ModifierPicks.picks(walkerPowers, _))
```

In `StartLines.modifiers`, add the picks to what a park records:

```scala
    val picked = picks(journal.answers(run, journal.segmentEnd(at)))
    val shown = (chosen ++ picked).distinct.filterNot(id =>
      resolutions.get(id).contains(PowerResolution.Automatic))
```

In `opening`, make the Search and Travel tag wait for the decision to settle, and move the facedown play's tag to its pick:

```scala
      case ActionRef.Travel | ActionRef.Search =>
        name(run.procedure).filter(_ =>
          (journal.ops(at).nonEmpty &&
            ModifierPicks.settled(journal.answers(run, at))) || completing)
          .map(named => (named, Vector.empty))
      // The adviser's pick: every modifier is picked by then.
      case ActionRef.PlayFacedownAdviser =>
        name(run.procedure).filter(_ => completing ||
          (journal.event(at) match
            case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _) =>
              id == CardPlayProcedure.facedownAdviserDecisionId
            case _ => false))
          .map(named => (named, Vector.empty))
```

Add the imports this needs: `ModifierPicks`, `ChoicePayload`, `CardPlayProcedure`. Update the doc comments of `opening` ("the run's first event, except ... the facedown-adviser play (its adviser's pick) ...") and of `modifiers` ("the player's selection as a parked run records it, and the picks its answers make").

- [ ] **Step 4: Silence the decision's "Chose" line**

In `DetailLines.narrated`, add the modifier decision:

```scala
  def narrated(decisionId: String): Boolean =
    NarratedIds(decisionId) || ModifierPicks.isDecision(decisionId) ||
      NarratedPrefixes.exists(decisionId.startsWith)
```

and add "the modifier decision, which the start line names" to the doc comment above `NarratedPrefixes`.

- [ ] **Step 5: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. A facedown-adviser test that pinned the tag at the start now sees it at the pick. Update that expectation only if the order of the visible lines is unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/StartLines.scala \
  src/main/scala/oathdigital/application/gamelog/DetailLines.scala \
  src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
# and any other log test file you changed, by path
git commit -m "feat(gamelog): name the modifiers picked in the walk"
```

---

### Task 6: The client starts directly; record the slice and run the gates

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala`
- Modify: `docs/ROADMAP.md`, `CONTEXT.md`, `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`

- [ ] **Step 1: Update the frontend tests first**

- `ModifierSelectionStateSuite` (around lines 68-76): a "search" start and a "play-facedown-adviser" start no longer map to a preview. `ModifierFlowDraft.action` returns `None` for both, and the expected vector loses its two "search" entries.
- `ModifierFlowSuite`: the test "a facedown-adviser start previews as Search and goes out with no card" becomes "a facedown-adviser start goes out directly". The start is sent, with no preview requested. Add the same test for a "search" start.
- `ModifierFlowDrafts` and every other fixture that uses a "search" start as an action with a modifier stage: switch it to "travel", which still has one.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite oathdigital.frontend.ModifierSelectionStateSuite"`
Expected: FAIL. Search still previews.

- [ ] **Step 2: Start Search and facedown play directly**

In `ModifierFlowDraft.scala`, drop `"search"` from `walkerActions` and delete the `play-facedown-adviser` case of `action`. Extend `walkerActions`' doc comment: Search and facedown-adviser play ask for their modifiers inside the walk (modifier walker design), so they start directly.

Run: `./sbtw "frontend/test"`
Expected: PASS.

- [ ] **Step 3: Check the decision in the browser**

The modifier decision is a board choose-one with a panel button, which the client already renders. Look at it once, on a scratch database. Never use `preview_start`; it serves the live database.

1. Start a scratch server on port 8081 through Bash, as the project's notes describe for a scratch database.
2. In a game, give the first player News from Afar, and start a Search.
3. Confirm these:
   - The panel reads "Choose modifiers, or start", with "Start Search".
   - News from Afar is highlighted on the board with its price.
   - Picking it pays and moves on to the source.
   - Undo takes the pick back.
4. Stop the scratch server.

If any of this does not render, report it with a screenshot. Do not fix the renderer in this slice.

- [ ] **Step 4: Record the slice**

- `docs/ROADMAP.md`: check "Slice 1: the modifier decision, then Search and facedown-adviser play." under "Phase - Modifier selection on the walker".
- `CONTEXT.md`: in the **Modifier flow** entry, add that Search and facedown-adviser play ask for their modifiers inside the walk, in the modifier decision. Add a **Modifier decision** entry beside it:
  - It is a major action's first walker decision.
  - It is asked one modifier at a time, and each pick is paid at once.
  - "Start" ends it.
  - _Avoid_: modifier stage, modifier step.
- `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`: under ruling 1 ("Modifiers stay before the start"), add one line saying that the modifier walker design moved Search's modifiers into the walk in its slice 1. Link that design by its file name, `2026-10-09-modifier-walker-design.md`, which sits beside it. The full rewrite of the ruling waits for slice 4, as that spec says.

- [ ] **Step 5: Run the verification gate**

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
4. `git diff --check main...HEAD`

Expected: all pass. Compare the test counts with the baseline and report the difference.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala \
  docs/ROADMAP.md CONTEXT.md \
  docs/superpowers/specs/2026-10-07-search-source-walker-design.md
git commit -m "feat(frontend): start Search without the modifier flow"
```
