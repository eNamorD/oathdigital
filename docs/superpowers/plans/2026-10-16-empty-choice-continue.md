# Empty Choice Continue (Catalog Batch 8, Slice 10, Part 2b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A choose-one marked `passWhenEmpty` no longer passes silently when nothing can be chosen. It parks, shows each candidate unavailable with its reason, and offers one Continue button, which passes it.

**Architecture:**

- **Today.** A `Decide` marked `passWhenEmpty` is passed without asking when the walker's search leaves it no option, or when its power builds no question because no candidate exists. A refusal the player could ask about is lost: the decision passes before anyone sees it.
- **The engine.** `ProcedureWalker.narrowAt` already computes a reason for each hidden option: a tree restriction's note, an operation ask, a window's ask. When the search leaves a `passWhenEmpty` choose-one with nothing, the live walk now parks it through a new `walker/EmptyChoices.offered`.
  - Every option is shown unavailable. An option keeps its reason, takes the computed one, or, with none, the generic "Cannot be chosen now".
  - A Continue button is appended: `DecisionOption.Button(Decide.Pass, "Continue")`.
  - The heading becomes "Nothing to choose".
- **The search is unchanged.** `WalkerSearch.reach` still passes the decision (`Reach.Skipped`), so the paths that lead to it stay legal and the menus do not change. The walk parks; a search, which only asks whether some path leads on, does not.
- **Continue is the pass.** The answer `ChooseOneAnswer(Decide.Pass)` is recorded like any answer. `PowerAnswers.one`, `PowerAnswers.last` and `NoteSupport.answer` read it as no answer. So every power reads Continue exactly as it read a passed decision, and writes the same "nothing" line. The Game Log writes no "Chose" line for it, as it writes none for an `Inspect`'s Done.
- **Continue appears only when nothing is usable.** An askable option is not usable, so it sits beside Continue. A grant opens it; a refusal leaves Continue, so nobody waits with nothing to choose.
- **Powers always ask.** The ten families that mark `passWhenEmpty` stop skipping the question when no candidate exists up front. They always build the `Decide`, and the engine turns an empty one into the Continue screen. The cost stays paid and nothing happens, as before; the player now sees it.
- **Frontend.** No change. A choose-one's button options render as Decide buttons that answer at once, in `BoardDecisionPanel` and in the generic choose-one panel (`BoardDecisionPanelSuite`, "a Decide button submits at once").

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** this plan carries its own rulings, recorded by Task 3 in:

- `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`, "Lazy pruning", the "Empty choices" bullet;
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, "Slice 10: Power-use restrictions", "Plan-time rulings".

Read both sections before starting, and `docs/superpowers/specs/2026-10-08-consent-system-design.md`, rulings 1 to 8, for the asks.

**Starts from:** `main` with slice 10 part 2 merged (commit `80226fde`). Check that `src/main/scala/oathdigital/gameplay/walker/OperationAsks.scala` exists. Slice 10 part 3 has not run yet; Task 3 amends its plan.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it. A refutable pattern in a `val` also fails the build.
- **File size.** Production and test Scala files stay at or under 800 lines (`scripts/check-architecture.py`, `BackendArchitectureSuite`). `ProcedureWalker.scala` is about 764 lines: its change is one short match that delegates to `EmptyChoices`.
- **Import rules:**
  - The engine (`gameplay/walker`, `gameplay/operations`) never names a power. `BackendArchitectureSuite` scans the engine's sources for every power's name, lowercased.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Pattern rule.** No `_` placeholder in a `DecisionQuery` form pattern. Bind typed: `case one: DecisionQuery.ChooseOne => one.options`.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Reading a parked decision:** match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`; the case's first field is the procedure.
- **Words, exactly:**
  - The heading: "Nothing to choose".
  - The button: "Continue".
  - The generic reason: "Cannot be chosen now".
- **Names, exactly:**
  - `Decide.Pass: DecisionOptionRef.Button`, the reference `DecisionOptionRef.Button("pass")`, in a new `object Decide` in `model/Operation.scala`.
  - File `src/main/scala/oathdigital/gameplay/walker/EmptyChoices.scala`: `private[walker] object EmptyChoices` with `Heading`, `Reason`, `ContinueOption`, `nothingLeft` and `offered`.
  - Test helper `PhasePowerHarness.continuedPast(from: OathTransition, decision: String): Either[OathViolation, OathTransition]`.
- **Scope.** Only decisions marked `passWhenEmpty` change. A required decision, an optional choose-many and every other question are untouched.

---

### Task 1: The Continue screen

**Files:**
- Modify: `src/main/scala/oathdigital/model/Operation.scala` (add `object Decide`; update `Decide`'s doc)
- Create: `src/main/scala/oathdigital/gameplay/walker/EmptyChoices.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala` (`one`, `last`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala` (`answer`)
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala` (`decision`)
- Modify: `src/test/scala/oathdigital/testkit/PhasePowerHarness.scala` (add `continuedPast`)
- Test: `src/test/scala/oathdigital/gameplay/walker/EmptyChoicesSuite.scala`
- Test: the power suites listed in Step 7

**Interfaces:**
- Produces:
  - `Decide.Pass: DecisionOptionRef.Button`, equal to `DecisionOptionRef.Button("pass")`.
  - `EmptyChoices.Heading: String = "Nothing to choose"`, `EmptyChoices.Reason: OptionReason = OptionReason.Written("Cannot be chosen now")`, `EmptyChoices.ContinueOption: DecisionOption = DecisionOption.Button(Decide.Pass, "Continue")`.
  - `EmptyChoices.nothingLeft(decide: Decide): Decide`: a choose-one with every available option removed.
  - `EmptyChoices.offered(decide: Decide, hidden: Map[DecisionOptionRef, OptionReason], asks: Map[DecisionOptionRef, OptionReason]): Decide`.
  - A live walk parks a `passWhenEmpty` choose-one the search empties, as `offered` shapes it. A search still passes it.
  - `PowerAnswers.one`, `PowerAnswers.last` and `NoteSupport.answer` never return `Decide.Pass`.
  - `PhasePowerHarness.continuedPast(from, decision)`: the actor presses Continue on `decision`.

- [ ] **Step 1: Write the failing engine tests**

Create `src/test/scala/oathdigital/gameplay/walker/EmptyChoicesSuite.scala`. Each tree asks one choose-one, `test.pick`, over two buttons, then runs a required batch that refuses any answer naming a button `refused` holds. With both refused the search empties the question.

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

/** A choose-one passed when empty parks on a Continue button when the
  * search leaves it nothing, its options shown with their reasons; a
  * search still passes it (catalog batch 8, slice 10, part 2b). */
class EmptyChoicesSuite extends munit.FunSuite:
  private val ready = Table.start.ready
  private val powers = WalkerPowers(Vector.empty)
  private val pick = "test.pick"
  private val left = DecisionOptionRef.Button("left")
  private val right = DecisionOptionRef.Button("right")

  /** Nothing, unless `test.pick` was answered with a button in `refused`,
    * which is rejected. */
  private def refusing(refused: Set[DecisionOptionRef])(
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, pick) match
      case Some(ref) if refused(ref) => Left(
        OathViolation.InvalidEventOrder(s"${ref.wireId} is refused"))
      case _ => Right(Vector.empty)

  /** `test.pick` over Left and Right, passed when empty, then a required
    * batch that refuses an answer naming a button in `refused`. */
  private def pickRefusing(refused: Set[DecisionOptionRef],
      options: Vector[DecisionOption] = Vector(
        DecisionOption.Button(left, "Left"),
        DecisionOption.Button(right, "Right"))): Vector[Operation] =
    Vector(
      Decide(pick, p1, DecisionQuery.ChooseOne(options,
        heading = Some("Pick a side")), passWhenEmpty = true),
      BuildOps((_, pending) => refusing(refused)(pending), required = true))

  private def parkedAt(tree: Operation)(using munit.Location)
      : (PendingTree, DecisionQuery.ChooseOne) =
    val pending = accepted(ProcedureWalker.advance(ready, tree, None,
      powers)) match
      case WalkerOutcome.Parked(at, _) => at
      case other => fail(s"the walk did not park: $other")
    present(ProcedureWalker.parkedDecide(ready, tree, pending, powers),
      "the parked decision").query match
      case one: DecisionQuery.ChooseOne => (pending, one)
      case other => fail(s"expected a choose-one, got $other")

  test("with every option hidden it parks, each shown unavailable, and " +
      "offers Continue"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(left, right))))
    assertEquals(query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Unavailable(DecisionOption.Button(left, "Left"),
        OptionReason.Written("Cannot be chosen now")),
      DecisionOption.Unavailable(DecisionOption.Button(right, "Right"),
        OptionReason.Written("Cannot be chosen now")),
      DecisionOption.Button(Decide.Pass, "Continue")),
      heading = Some("Nothing to choose")))

  test("an option already unavailable keeps its own reason"):
    val noted = OptionReason.Written("Not today")
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(right), Vector(
      DecisionOption.Unavailable(DecisionOption.Button(left, "Left"), noted),
      DecisionOption.Button(right, "Right")))))
    assertEquals(query.options.headOption, Some(DecisionOption.Unavailable(
      DecisionOption.Button(left, "Left"), noted)))
    assertEquals(query.options.lastOption,
      Some(DecisionOption.Button(Decide.Pass, "Continue")))

  test("with no option at all it parks on Continue alone"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set.empty, Vector.empty)))
    assertEquals(query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(Decide.Pass, "Continue")),
      heading = Some("Nothing to choose")))

  test("pressing Continue passes it, and the power reads no answer"):
    val tree = Sequence(pickRefusing(Set(left, right)))
    val (pending, _) = parkedAt(tree)
    val answer = Answered(pick, DecisionAnswer.ChooseOneAnswer(Decide.Pass),
      p1)
    accepted(ProcedureWalker.resolve(ready, tree, pending, answer,
      powers)) match
      case _: WalkerOutcome.Finished => ()
      case other => fail(s"the walk did not finish: $other")
    val answered = pending.copy(answered = Vector(answer))
    assertEquals(PowerAnswers.one(answered, pick), None)
    assertEquals(PowerAnswers.last(Vector(answer), pick), None)

  test("with one option usable it asks as before, with no Continue"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(right))))
    assertEquals(query.options, Vector(DecisionOption.Button(left, "Left")))
    assertEquals(query.heading, Some("Pick a side"))

  test("a search still passes it, so the option that leads there stays " +
      "open"):
    val go = DecisionOptionRef.Button("go")
    val tree = Sequence(Decide("test.outer", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(go, "Go")))) +:
      pickRefusing(Set(left, right)))
    val (_, outer) = parkedAt(tree)
    assertEquals(outer.options, Vector(DecisionOption.Button(go, "Go")))
```

If `Outcomes.parked` or `ProcedureWalker.resolve` take other arguments on `main`, follow `AutomaticUseSuite`, which walks and resumes the same way.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.EmptyChoicesSuite"`
Expected: compilation fails, because `Decide.Pass` does not exist.

- [ ] **Step 3: Add `Decide.Pass`**

In `model/Operation.scala`, after the `Decide` case class (and its methods), add:

```scala
object Decide:
  /** The answer that passes a choose-one marked `passWhenEmpty`, offered as
    * a Continue button when nothing else may be chosen (catalog batch 8,
    * slice 10, part 2b). A power reads it as no answer. */
  val Pass: DecisionOptionRef.Button = DecisionOptionRef.Button("pass")
```

In `Decide`'s doc, replace the `passWhenEmpty` paragraph with:

```scala
  * `passWhenEmpty` lets the search leave a choose-one with no option (global
  * operation restrictions design, "Empty choices"): a search passes it, as
  * it passes an empty optional choose-many, instead of failing the path.
  * The live walk parks it with every option shown unavailable and one
  * Continue button, [[Decide.Pass]], which passes it. With one option left
  * it parks as any decision does, so the player confirms it.
```

A companion object does not stop the case class's synthesized `apply` and `unapply`.

- [ ] **Step 4: Create `EmptyChoices.scala`**

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionOption, DecisionOptionRef,
  DecisionQuery, OptionReason}

/** The Continue screen of a choose-one passed when empty (catalog batch 8,
  * slice 10, part 2b). When the search leaves it no option, the live walk
  * parks it anyway, so the player sees why nothing can be chosen: every
  * option shown unavailable with its reason, a Continue button that passes
  * it, and a generic heading. An option the search hid takes the reason
  * the walk found for it, or [[Reason]]. An ask still asks, so a grant
  * opens its option; after a refusal Continue remains.
  */
private[walker] object EmptyChoices:
  val Heading: String = "Nothing to choose"
  val Reason: OptionReason = OptionReason.Written("Cannot be chosen now")
  val ContinueOption: DecisionOption =
    DecisionOption.Button(Decide.Pass, "Continue")

  /** `decide` with every available option removed: the cut a search that
    * hid them all would make. */
  def nothingLeft(decide: Decide): Decide = decide.query match
    case one: DecisionQuery.ChooseOne => decide.copy(query = one.copy(
      options = one.options.filterNot(_.available)))
    case _ => decide

  /** `decide` as its Continue screen: each available option shown
    * unavailable with its `hidden` reason or [[Reason]], each `asks` reason
    * over an option already unavailable, then Continue, under [[Heading]]. */
  def offered(decide: Decide, hidden: Map[DecisionOptionRef, OptionReason],
      asks: Map[DecisionOptionRef, OptionReason]): Decide =
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val shown = one.options.map(option =>
          if option.available then DecisionOption.Unavailable(option,
            hidden.getOrElse(option.ref, Reason))
          else option)
        WalkerSearch.reasoned(decide.copy(query = one.copy(
          options = shown :+ ContinueOption, heading = Some(Heading))), asks)
      case _ => decide
```

- [ ] **Step 5: Park the empty choose-one in `narrowAt`**

In `ProcedureWalker.narrowAt`, replace the `case Right(narrowed) => Right(narrowed.map { cut => ... })` branch with:

```scala
      case Right(narrowed) =>
        // A choose-one passed when empty parks on its Continue screen when
        // nothing is left (catalog batch 8, slice 10, part 2b); a search
        // still passes it ([[reach]]).
        val cut = narrowed.getOrElse(EmptyChoices.nothingLeft(decide))
        lazy val reasons =
          val kept = WalkerSearch.choosable(cut).toSet
          val hidden = WalkerSearch.choosable(decide).filterNot(kept)
          operationAsksAt(decide, ctx, path, hidden) ++
            WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
              ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
              ctx.procedure, grantable(decide, ctx, path)).toMap
        narrowed match
          case Some(_) => Right(Some(WalkerSearch.reasoned(
            WalkerSearch.explained(decide, cut, reasons),
            asksAt(decide, ctx, path))))
          case None if decide.passWhenEmpty => Right(Some(
            EmptyChoices.offered(decide, reasons, asksAt(decide, ctx, path))))
          case None => Right(None)
```

The `++` order keeps the existing rule: a tree restriction's note wins over an operation ask for the same option. Update `narrowAt`'s doc comment: a choose-one passed when empty with nothing left parks on its Continue screen. Update `runLeaf`'s doc: "an optional one with nothing left is passed without asking" becomes "an optional choose-many with nothing left is passed without asking; a choose-one passed when empty parks on its Continue screen".

Check `wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` stays at or under 800.

- [ ] **Step 6: Read Continue as no answer**

In `PowerAnswers`:

```scala
  def one(pending: PendingTree, decision: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst:
    case Answered(`decision`, DecisionAnswer.ChooseOneAnswer(ref), _)
        if ref != Decide.Pass => ref
```

and in `last`, add the same guard: `if decisions.contains(id) && ref != Decide.Pass => ref`. Add to the object comment: "Continue, which passes an empty choice (`Decide.Pass`), reads as no answer."

In `NoteSupport.answer`, add the same guard:

```scala
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ref != Decide.Pass => ref
```

In `DetailLines.decision`, beside the `Inspect.Done` case:

```scala
        // Pressing Continue on an empty choice is not a choice either.
        case ChooseOneAnswer(Decide.Pass) => Vector.empty
```

Import `Decide` where the file does not already import `oathdigital.model._`.

In `PhasePowerHarness`, after `answer`:

```scala
  /** `from`, parked on `decision`'s Continue screen, continued by the actor
    * pressing Continue. */
  def continuedPast(from: OathTransition, decision: String)
      : Either[OathViolation, OathTransition] =
    answer(from, actor, decision, pick(Decide.Pass))
```

- [ ] **Step 7: Run the suites and move the hidden-only tests onto Continue**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.powers.action.* oathdigital.application.gamelog.*"`
Expected: `EmptyChoicesSuite` passes with 6 tests. Some power tests fail.

A test fails where a power builds its question but the search hides every option: until now the decision passed and the action ended at once. It now parks on the Continue screen. Rewrite each such test: assert the walk awaits the power's decision, press Continue with `continuedPast`, then keep every assertion it made about the result and the line. The known ones:

- `AssassinSuite`, "with only the locked Assassin to discard it asks nothing, and the line says so";
- `EnchantressSuite`, "offered only a locked adviser, it asks nothing and the line says so";
- `ArmedMobSuite`, "a target with only a locked adviser keeps it, and the line says so";
- `FaeMerchantSuite`, "with only the Grand Scepter held and an empty relic deck, it asks nothing and puts nothing back";
- `ArcaneBrokersSuite`, "holding only the Grand Scepter, it asks nothing, discards nothing and writes nothing", and the same case in `RelicBreakerSuite` and `BogSuite` if they have one.

For example, Assassin's becomes:

```scala
  test("with only the locked Assassin to discard it shows Continue, and " +
      "the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .pawn(p2, at = "Broken Peaks").pawn(p3, at = "Buried Giant")
      .adviser(p1, assassin).favor(p1, 1).ready
    val asked = accepted(use(ready, power, source))
    assert(awaits(asked, Assassin.decisionId))
    val done = accepted(continuedPast(asked, Assassin.decisionId))
    assert(backToActing(done))
    assertEquals(Look(after(done)).advisers(p1), Vector[CardId](assassin))
    assertEquals(NoteText.said(power, asked.events ++ done.events),
      Vector(NoteText.Said("used.none",
        "No faceup adviser could be discarded.", covers = true)))
```

Use the decision id each power exposes (`Assassin.decisionId` and so on). If a power keeps it private, read it from the parked decision with `ParkedNode`, matching `ParkedNode.Decision(_, decide, _)`. Rename a test whose name says "asks nothing" to say it shows Continue. A test where the power finds no candidate at all still passes here, since the power builds no question yet: Task 2 moves those.

Do not change a test that expects a power to be unusable at the phase menu: menu availability does not change.

- [ ] **Step 8: Run the full suite**

Run: `./sbtw test`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/Operation.scala src/main/scala/oathdigital/gameplay/walker/EmptyChoices.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/test/scala/oathdigital/testkit/PhasePowerHarness.scala src/test/scala/oathdigital/gameplay/walker/EmptyChoicesSuite.scala
git commit -m "feat(batch8): an empty choice shows why and offers Continue"
```

Also stage each power suite Step 7 changed, by explicit path.

---

### Task 2: Every pass-when-empty power asks

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Assassin.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Enchantress.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Homesteaders.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Resettle.scala` (`askAdviser`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BedOfRoots.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala` (`askAdviser`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/HeldRelicSpend.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala` (the relic `Branch`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala` (`ask`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala` (`ask`)
- Test: the power suites listed in Step 3
- Test: `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`
- Test: `src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala`, if it pins the skip

**Interfaces:**
- Consumes: Task 1's Continue screen and `PhasePowerHarness.continuedPast`.
- Produces: each of these powers always builds its `passWhenEmpty` question, with no candidate too. No signature changes.

- [ ] **Step 1: Build the question whatever it holds**

In each power below, remove the early exit so the `Decide` is always built. The `Decide` keeps `passWhenEmpty = true`; with no option the engine shows Continue alone.

- `Assassin.ask`, `Enchantress.ask`, `Homesteaders.ask`, `BedOfRoots.ask`, `HeldRelicSpend.ask`: drop `case Vector() => Vector.empty`. With one case left, write the body directly. For Assassin:

  ```scala
  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      candidates(ready, actor).map(candidate =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(candidate.card.id))),
      heading = Some("Assassin: discard a faceup adviser of a player at " +
        "your site")), passWhenEmpty = true))
  ```

  The others follow the same shape: the `found` they matched becomes the candidates read in place, and `held(ready, player)` for `HeldRelicSpend`.
- `Resettle.askAdviser`: the same, over `candidates(ready)`. `askSite` is unchanged: it still asks only once an adviser is chosen, and Continue reads as no adviser.
- `FaeMerchant`: the relic `Branch` becomes `Branch((state, _) => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(held(state, player).map(id => DecisionOption.Relic(DecisionOptionRef.Relic(id))), heading = Some(...)), passWhenEmpty = true)))`, keeping the heading text.
- `ArmedMob.askAdviser`: ask whether or not a target exists. The heading is replaced by the Continue screen's when nothing is left, so its target only matters when one exists:

  ```scala
  private def askAdviser(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    val target = owner(ready, actor, pending)
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      target.toVector.flatMap(candidates(ready, _)).map(card =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(card.id))),
      heading = Some(adviserHeading(target.contains(actor)))),
      passWhenEmpty = true))
  ```

  `askPlayer` is unchanged: it is not passed when empty.
- `SiteCards.ask` and `RovingTerror.ask`: drop `if !found.exists(_.available) then Vector.empty`. A card only an ask stops is now shown askable beside Continue, and after a refusal Continue remains, so the reason for the skip is gone. Rewrite `SiteCards.ask`'s doc: "The question over the cards at the player's site, each judged for the player's discard. With none that may be chosen it is the Continue screen, where an askable card still asks (catalog batch 8, slice 10, part 2b)." Rewrite `RovingTerror.ask`'s doc the same way.

Update each class comment that says "With no candidate nothing is asked", "the question passes when nothing is left" or "asked only when some card may be chosen". It now says the question shows Continue when nothing can be chosen, and the cost stays paid. Keep each comment's other sentences.

- [ ] **Step 2: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.operations.* oathdigital.application.HallOfMinistersConsentServiceSuite oathdigital.application.gamelog.*"`
Expected: some power tests fail where no candidate existed and nothing was asked.

- [ ] **Step 3: Move the no-candidate tests onto Continue**

Rewrite each failing test as in Task 1, Step 7: assert the walk awaits the power's decision, press Continue with `continuedPast`, then keep its assertions. The known ones:

- `HomesteadersSuite`, "with the pawn's site full it asks nothing and the line says so";
- `ResettleSuite`, "with no faceup Nomad adviser it asks nothing and the line says so";
- `BedOfRootsSuite`, "with no faceup adviser, the cost stays paid and the line says so";
- `ArmedMobSuite`, "a holder of both banners is no target, and the line says so", and "with two targets and no faceup adviser nothing is asked, and the ...";
- `TamingCharmSuite`, "with nothing to discard the cost stays paid, and the line names ...", and the same case in `GreatFeastSuite` if it has one;
- `RovingTerrorSuite` and `FaeMerchantSuite`, any case with no card or relic to offer.

Hall of Ministers: a Roving Terror whose only candidate the Hall protects was not asked before. This is the gap the plan closes. In `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`, replace the test "when the Hall protects every candidate the question is not asked: a refusal would leave nothing to choose" with:

```scala
  test("when the Hall protects every candidate the card is askable beside " +
      "Continue, and after a refusal Continue remains"):
    def continueOffered(t: OathTransition): Boolean =
      queryOf(t, p1).toVector.flatMap(_.offeredOptions)
        .exists(_.id == Decide.Pass.wireId)
    val asked = accepted(use(guarded(beside = false), power, source))
    assert(awaits(asked, RovingTerror.decisionId))
    assertEquals(errandBoyRow(asked).state, "askable")
    assert(continueOffered(asked))
    val refused = accepted(use(remembering(guarded(beside = false),
      ConsentAnswer.Refused), power, source))
    assert(awaits(refused, RovingTerror.decisionId))
    assertEquals(errandBoyRow(refused).state, "unavailable")
    assert(continueOffered(refused))
```

If the projection names a button option's id other than its `wireId`, compare with the id the projection gives `DecisionOptionRef.Button("pass")`. `HallOfMinistersConsentServiceSuite` may pin the same skip through the service: update it to the same expectation, and say so in the task report.

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Assassin.scala src/main/scala/oathdigital/gameplay/powers/action/Enchantress.scala src/main/scala/oathdigital/gameplay/powers/action/Homesteaders.scala src/main/scala/oathdigital/gameplay/powers/action/Resettle.scala src/main/scala/oathdigital/gameplay/powers/action/BedOfRoots.scala src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala src/main/scala/oathdigital/gameplay/powers/action/HeldRelicSpend.scala src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala
git commit -m "feat(batch8): a power with nothing to choose shows Continue"
```

Also stage each suite Step 3 changed, by explicit path.

---

### Task 3: Docs, gates and the part 3 plan

**Files:**
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/plans/2026-10-15-catalog-batch-8-slice10-part3.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

In the global operation restrictions design, replace the "**Empty choices.**" bullet with:

```markdown
- **Empty choices.** A choose-one `Decide` marked `passWhenEmpty` is passed
  by a search when no option is left, as an empty optional choose-many is,
  instead of failing the path. The live walk parks it instead: every option
  is shown unavailable with its reason, or "Cannot be chosen now" when the
  walk has none, under the heading "Nothing to choose", with one Continue
  button (`Decide.Pass`) that passes it (catalog batch 8, slice 10, part
  2b). A power reads Continue as no answer. With one option left it parks
  as any decision does: the player sees the one option and confirms it.
  Fae Merchant is the first user: it offers every relic held, the search
  hides the Grand Scepter, and with only the scepter held and an empty
  relic deck it shows Continue and puts nothing back.
```

In the batch 8 design, under "### Plan-time rulings" of "## Slice 10: Power-use restrictions", after the bullet "**A Forge that cannot place** (part 2)", add:

```markdown
- **An empty choice shows Continue** (part 2b). A question passed when
  empty is no longer passed silently. With nothing that may be chosen it
  shows each candidate unavailable with its reason and one Continue
  button, which passes it. An askable candidate still asks, so a ruler's
  grant opens it, and after a refusal Continue remains. A power with no
  candidate at all asks too, so its player sees that nothing can be done.
  The cost stays paid and nothing happens, as before; the menus do not
  change. This closes the gap where such a question passed before its
  player could ask.
```

- [ ] **Step 2: Amend the part 3 plan**

In `docs/superpowers/plans/2026-10-15-catalog-batch-8-slice10-part3.md`, Task 1, Step 4, replace "`ask` reads `slots.options(ready, actor)`, keeps its rule that it asks only when some option may be chosen, and uses the heading ..." with:

```markdown
- `ask` reads `slots.options(ready, actor)` and always builds its
  `passWhenEmpty` question, as part 2b left it: with nothing that may be
  chosen the engine shows its Continue screen. It uses the heading
  "Roving Terror: move to which slot?".
```

Then search that plan for "asks nothing", "passes when" and "asked only when". A test or comment there that expects a passed question now expects the Continue screen: say so beside it in one sentence, naming `PhasePowerHarness.continuedPast`.

- [ ] **Step 3: Note it in the roadmap**

In `docs/ROADMAP.md`, item 10, change "part 3 (Giant Python) is next." to "part 2b (an empty choice shows Continue) is done; part 3 (Giant Python) is next."

- [ ] **Step 4: Run the gates**

Run: `./sbtw "test" "frontend/test"` and `./sbtw "frontend/fullOptJS"`.
Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/plans/2026-10-15-catalog-batch-8-slice10-part3.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 10 part 2b done"
```
