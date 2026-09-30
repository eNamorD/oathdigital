# Global Operation Restrictions, Slice 2 (Lazy Pruning) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide every decision option from which no path reaches a legal end of the action, with a depth-first search that runs the real walker on a copy of the state and stops at hidden information.

**Architecture:** The walker gains a search mode. Before a decision parks, each option is answered on a copy of the state and walked in search mode. A later decision is searched the same way, with the search stopping at the first answer that leads on. The search stops, and counts the path as legal, at dice, draws, shuffles, peeks and cards turned faceup, and at a decision it already stands at in the same state. A pure helper module, `WalkerSearch`, enumerates answers and narrows decisions. `ProcedureWalker` supplies the verdict for one answer. The search replaces `WalkerPowerGather.probe` and keeps its tree-level `Restriction` check.

**Tech Stack:** Scala 3, sbt through `./sbtw`, munit.

**Spec:** [Global operation restrictions design](../specs/2026-09-30-global-operation-restrictions-design.md), section "Lazy pruning" and slice 2 of "Slicing" and "Testing". Read both documents before starting.

## Global Constraints

- Build and test with `./sbtw`. The compiler runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import fails the build.
- No production Scala file may exceed 800 lines (`BackendArchitectureSuite`, "all production Scala files stay bounded"). `ProcedureWalker.scala` has 722 lines at the start.
- `BackendArchitectureSuite` still applies: no power names in walker sources, and powers do not import `gameplay.walker`.
- Every global restriction refuses as `Impossible`. An optional operation it refuses is skipped; a required one rejects the batch.
- No depth cap. If the search is too slow, the architecture is re-evaluated instead.
- **Performance budget:** after slice 2, `sbt test` may grow by at most 15% over the baseline recorded in Task 1, and the Campaign park must answer in under 50 ms. If either fails after the caches in Task 5, stop and bring alternative designs to the product owner. Lowering a depth cap is not one of them.
- Replay applies recorded operations without re-checking restrictions. Do not change `WalkerReplay`.
- Never touch the live database under `var/oathdigital`.
- Commits end with the trailer the session's system reminder names.

## Decisions taken at plan time

These answer the spec's "Verify at plan time" items and the questions put to
the product owner on 2026-09-30. Task 5 records them in the spec.

- **Any rejection prunes.** The search runs the real pipeline, so an option is
  hidden when no path from it reaches a legal end for any reason: a refusal by
  an operation restriction, a tree-level `Restriction`, or a required
  operation the pipeline rejects (an unaffordable cost, for example).
- **Choose-many.** An option survives when some accepted selection that holds
  it survives. Selections are tried smallest first, in declared option order,
  and the search stops at the first one that survives.
- **Choose-amount.** Values are tried one by one. The survivors narrow the
  range when they form one range; with a gap the decision is left whole, as
  today.
- **Partition and Distribute** are not enumerated. A search that reaches one
  counts the path as legal, and the answer-time check stays.
- **Another player's decision** reuses the same simulation: the hypothetical
  answer is given by the decision's owner.
- **Loops.** A search that reaches a decision it already stands at, in the
  same state, stops there and counts the path as legal: the loop leads on
  exactly when the first visit does, and the first visit's other answers are
  tried there.
- **Operation-restriction notes are deferred.** Nothing in slice 2 needs one,
  and the batch 3 spec says Lost Tongue's refused `Take` writes no line. The
  notes that tree-level `Restriction`s write (`lookAheadNotes`) keep working,
  with the search deciding what is hidden.
- **Atomic batches.** `BuildOps` gains `required`. A required `BuildOps` runs
  its whole batch or rejects, so a refused operation inside it fails the path
  instead of being skipped. Twin Brother's swap uses it; Horned Mask's discard
  is already a required operation.
- **Phase powers.** `PhasePowerProcedure.usable` runs the same dry run a start
  runs, so a phase power with no legal path is not offered.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`: the pure half of the search. It enumerates the answers of a decision, narrows a decision against a verdict, decides what a search does at a decision it reaches, and names the operations that hide information.
- Modify `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`: search mode on `WalkCtx`, a `Stopped` step, the verdict for one answer, the narrowing before a park, and the stop at hidden information.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`: `probe` retires; `breaches` gives the tree-level check; `leafAt` narrows through the search.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`: a memoized `quiet` copy replaces `copy(probing = false)`.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`: `DeltaMeaning.of` moves out of `ProcedureWalker` to keep that file under the bound.
- Modify `src/main/scala/oathdigital/model/CoreOperations.scala`: `BuildOps.required`.
- Modify `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala` and `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`: their locked filters retire.
- Modify `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`: `isLocked` retires.
- Modify `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`, `src/main/scala/oathdigital/gameplay/OathRules.scala` and `src/main/scala/oathdigital/application/PhasePowerProjector.scala`: the phase power dry run.
- Tests: create the `SearchBudget` benchmark (a program run by hand, not a suite), `WalkerSearchSuite` and `LazyPruningSuite`; update `HornedMaskSuite`, `WhenPlayedHarness`, `TargetsFixture`, `PhasePowerSuite` and whichever suites Task 3's triage names.
- Docs: the spec and `docs/ROADMAP.md`.

---

### Task 1: Baseline and budget benchmark

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/SearchBudget.scala`
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md` (section "Lazy pruning", paragraph "Performance budget")

**Interfaces:**
- Consumes: `CampaignFixture.board(extras: Int)`, `CampaignFixture.rules(dice)`, `CampaignFixture.anyDice`, `WalkerProcedureRegistry.rebuild`, `ProcedureWalker.openDecisions`, `WalkerPowerCatalog.default`.
- Produces: the recorded baseline, which Task 5 compares against.

- [ ] **Step 1: Measure the baseline**

Run the full server suite twice, so the second run reuses the compiled classes:

```bash
./sbtw test 2>&1 | tail -3
```

```bash
./sbtw test 2>&1 | tail -3
```

Expected: both end with `[info] Passed: Total 2130, Failed 0` and a `[success] Total time: N s` line. Keep the second run's `N`. Call it `BASELINE`.

- [ ] **Step 2: Write the budget benchmark**

The benchmark is a program in the test sources, not a munit suite, so
`sbt test` never runs it: a timing check is not deterministic and must not
become part of the suite. It prints its measures and exits non-zero when one
is over budget.

Create `src/test/scala/oathdigital/gameplay/SearchBudget.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{board, rules}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The lazy pruning's budget (global operation restrictions design,
  * "Performance budget"): a Campaign park on a full board answers in under
  * 50 ms. A benchmark run by hand, never by `sbt test`:
  *
  * {{{./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"}}}
  *
  * Each measure is the best of five runs after three warm-up runs, so a cold
  * first run does not count. It exits with status 1 when a measure is over
  * budget.
  */
object SearchBudget:
  private val budgetMillis = 50L

  private def best(body: => Any): Long =
    (1 to 3).foreach(_ => body)
    (1 to 5).map { _ =>
      val start = System.nanoTime()
      body
      (System.nanoTime() - start) / 1000000L
    }.min

  def main(args: Array[String]): Unit =
    val r = rules(CampaignFixture.anyDice)
    val walkerPowers = WalkerPowerCatalog.default(catalog)
    val b = board(extras = 3)
    def started: ReadyGame =
      r.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor) match
        case Right(transition) => transition.state match
          case Ready(value) => value
          case other => sys.error(s"expected a ready game, got $other")
        case Left(violation) => sys.error(s"Campaign must start: $violation")
    val start = best(started)
    val ready = started
    val pending = ready.game.current.walkerPending.get
    val procedure = ready.game.current.walkerProcedure.get
    val tree = WalkerProcedureRegistry.rebuild(procedure, catalog, ready,
      b.actor, Vector.empty).toOption.get
    val read = best(ProcedureWalker.openDecisions(ready, tree, pending,
      walkerPowers))
    println(s"Campaign start: $start ms; reading its park: $read ms " +
      s"(budget $budgetMillis ms each)")
    if start >= budgetMillis || read >= budgetMillis then sys.exit(1)
```

- [ ] **Step 3: Run it**

Run: `./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`
Expected: it prints the two measures, both under 50 ms, and sbt reports success. Keep the two numbers as `START` and `READ`. If it does not compile because `walkerProcedure` is not an `Option[ProcedureRef]` or `rebuild` takes other arguments, read `CampaignProcedureSuite.parkedDecision` and match its calls. If the start does not park, raise `extras` until it does.

Then check that `sbt test` does not pick it up: `./sbtw test 2>&1 | grep -c SearchBudget` prints `0`.

- [ ] **Step 4: Record the baseline in the spec**

In the spec's "Performance budget" paragraph, after the sentence that ends "the park must answer in under 50 ms.", add this sentence, filling in the numbers from Steps 1 and 3:

```markdown
The baseline, measured on 2026-09-30 before slice 2: `sbt test` took
BASELINE s, and the `SearchBudget` benchmark measured START ms for the Campaign start
and READ ms for reading its park.
```

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/SearchBudget.scala docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md
git commit -m "test(walker): add the lazy pruning budget benchmark and record the baseline"
```

---

### Task 2: The search's answers and narrowing

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (move `narrowed`)
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces (all `private[walker]`):
  - `WalkerSearch.hides(operation: Operation): Boolean`
  - `WalkerSearch.narrow(decide: Decide, verdict: DecisionAnswer => Either[OathViolation, Unit]): Either[OathViolation, Option[Decide]]`
  - `WalkerSearch.reach(decide: Decide, verdict: DecisionAnswer => Either[OathViolation, Unit]): Either[OathViolation, WalkerSearch.Reach]`
  - `enum WalkerSearch.Reach { case Answerable, Skipped }`
  - `WalkerSearch.narrowed(decide: Decide, permitted: DecisionOptionRef => Boolean): Option[Decide]` (moved from `WalkerPowerGather`)

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseManyAnswer,
  ChooseOneAnswer}
import oathdigital.testkit.Table.p1

/** The search's answers and narrowing, apart from the walk (global operation
  * restrictions design, "Lazy pruning"). A verdict here is a plain function,
  * so each test says exactly which answers survive.
  */
class WalkerSearchSuite extends munit.FunSuite:
  private val refused = OathViolation.InvalidEventOrder("refused")
  private val card = DenizenId("24")
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)
  private def decide(query: DecisionQuery) = Decide("test.ask", p1, query)
  private def only(allowed: DecisionAnswer => Boolean)
      : DecisionAnswer => Either[OathViolation, Unit] =
    answer => Either.cond(allowed(answer), (), refused)
  private val abc = Vector(button("a"), button("b"), button("c"))

  test("a choose-one keeps the options whose answer survives"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ == ChooseOneAnswer(ref("b")))),
      Right(Some(decide(DecisionQuery.ChooseOne(Vector(button("b")))))))

  test("a required choose-one with no survivor is refused"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Left(refused))

  test("a choose-many keeps an option that survives only beside another"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    val pair = ChooseManyAnswer(Vector(ref("a"), ref("b")))
    assertEquals(WalkerSearch.narrow(asked, only(_ == pair)),
      Right(Some(decide(DecisionQuery.ChooseMany(1, 2,
        Vector(button("a"), button("b")), None)))))

  test("an optional choose-many with no survivor is not asked"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, abc, None))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Right(None))

  test("a choose-amount narrows to the surviving range, and a gap leaves it " +
      "whole"):
    val asked = decide(DecisionQuery.ChooseAmount(0, 4, Some("How many?"),
      "Confirm"))
    assertEquals(WalkerSearch.narrow(asked, only {
      case ChooseAmountAnswer(value) => value == 1 || value == 2
      case _ => false
    }), Right(Some(decide(DecisionQuery.ChooseAmount(1, 2, Some("How many?"),
      "Confirm")))))
    assertEquals(WalkerSearch.narrow(asked, only {
      case ChooseAmountAnswer(value) => value != 2
      case _ => false
    }), Right(Some(asked)))

  test("a search tries the smallest selections first and stops at a survivor"):
    var tried = Vector.empty[DecisionAnswer]
    val asked = decide(DecisionQuery.ChooseMany(0, 3, abc, None))
    val survivor = ChooseManyAnswer(Vector(ref("b")))
    assertEquals(WalkerSearch.reach(asked, answer => {
      tried = tried :+ answer
      Either.cond(answer == survivor, (), refused)
    }), Right(WalkerSearch.Reach.Answerable))
    assertEquals(tried, Vector(ChooseManyAnswer(Vector.empty),
      ChooseManyAnswer(Vector(ref("a"))), survivor))

  test("a decision no answer survives cannot be reached"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)), Left(refused))

  test("an optional choose-many with no options is passed"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, Vector.empty, None))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)),
      Right(WalkerSearch.Reach.Skipped))

  test("peeks, reveals, draws and cards turned faceup hide the outcome; " +
      "other operations do not"):
    assert(WalkerSearch.hides(Peek(p1, card, Location.PlayArea(p1))))
    assert(WalkerSearch.hides(Reveal(card, Location.PlayArea(p1))))
    assert(WalkerSearch.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceUp)))
    assert(WalkerSearch.hides(Roll(PoolKey("test.roll"),
      DiceSpec(DiceKind.Attack), RollMode.Automatic)))
    assert(!WalkerSearch.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceDown)))
    assert(!WalkerSearch.hides(GainSupply(p1, 1)))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: compilation fails with `Not found: WalkerSearch`.

- [ ] **Step 3: Write `WalkerSearch`**

Create `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionAnswer, DecisionOptionRef,
  DecisionQuery, Draw, Flip, OathViolation, Operation, Orientation, Peek,
  Reveal, Roll, Shuffle}

/** The lazy pruning's answers and narrowing (global operation restrictions
  * design, "Lazy pruning"), apart from the walk. A verdict says whether one
  * answer to a decision can reach a legal end of the action;
  * [[ProcedureWalker]] gives it by walking a copy of the state from that
  * answer.
  */
private[walker] object WalkerSearch:

  /** What a search does at a decision it reaches. */
  enum Reach:
    /** Some answer leads on, or the decision's answers are not tried. */
    case Answerable
    /** An optional decision with nothing to ask, which the walk passes. */
    case Skipped

  /** Whether `operation` shows a player something the chooser cannot see
    * yet: dice, a draw, a shuffle, a peek, or a card turned faceup. A search
    * stops there and counts the path as legal, since pruning past it would
    * leak the outcome.
    */
  def hides(operation: Operation): Boolean = operation match
    case _: Roll | _: Shuffle | _: Draw | _: Peek | _: Reveal => true
    case flip: Flip => flip.orientation == Orientation.FaceUp
    case _ => false

  /** `decide` offering only the options that some answer through them
    * survives. A choose-one is tried option by option. A choose-many keeps an
    * option when some accepted selection holding it survives, smallest first.
    * A choose-amount is tried value by value and narrowed when the survivors
    * form one range; with a gap it is left whole. Other kinds are not
    * narrowed. `Left` is a required decision with nothing left, carrying the
    * first option's rejection. `Right(None)` is an optional one with nothing
    * left, which is not asked.
    */
  def narrow(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Option[Decide]] =
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val refs = one.options.map(_.ref)
        kept(decide, refs, refs.map(ref =>
          ref -> verdict(DecisionAnswer.ChooseOneAnswer(ref))).toMap,
          required = true)
      case many: DecisionQuery.ChooseMany =>
        val refs = many.options.map(_.ref)
        kept(decide, refs, refs.map(ref => ref -> survivor(
          selections(refs, many.min, many.max).filter(_.contains(ref))
            .map(DecisionAnswer.ChooseManyAnswer(_)), verdict, None)).toMap,
          required = many.min >= 1)
      case amount: DecisionQuery.ChooseAmount =>
        val verdicts = (amount.min to amount.max).toVector.map(value =>
          value -> verdict(DecisionAnswer.ChooseAmountAnswer(value)))
        val allowed = verdicts.collect { case (value, Right(_)) => value }
        if verdicts.isEmpty then Right(Some(decide))
        else if allowed.isEmpty then
          Left(verdicts.collect { case (_, Left(violation)) => violation }.head)
        else if allowed.last - allowed.head + 1 != allowed.size then
          Right(Some(decide))
        else Right(Some(decide.copy(query = amount.copy(min = allowed.head,
          max = allowed.last, suggested = amount.suggested.map(value =>
            math.max(allowed.head, math.min(allowed.last, value)))))))
      case _ => Right(Some(decide))

  /** What a search does at `decide`: it passes an optional decision with
    * nothing to ask, stops at the first answer that survives, and fails when
    * none does. A kind whose answers are not tried (`Partition`,
    * `Distribute`) counts as answerable, and the answer-time check stays.
    */
  def reach(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Reach] =
    narrowed(decide, _ => true) match
      case None => Right(Reach.Skipped)
      case Some(_) => answers(decide) match
        case None => Right(Reach.Answerable)
        case Some(all) => survivor(all, verdict, None).map(_ => Reach.Answerable)

  /** `decide` offering only the permitted options. `None` is an optional
    * choose-many with nothing left, which is not asked. Other query kinds
    * are returned unchanged.
    */
  def narrowed(decide: Decide,
      permitted: DecisionOptionRef => Boolean): Option[Decide] =
    decide.query match
      case one: DecisionQuery.ChooseOne => Some(decide.copy(query =
        one.copy(options = one.options.filter(o => permitted(o.ref)))))
      case many: DecisionQuery.ChooseMany =>
        val options = many.options.filter(o => permitted(o.ref))
        if many.min == 0 && options.isEmpty then None
        else Some(decide.copy(query = many.copy(
          min = math.min(many.min, options.size),
          max = math.min(many.max, options.size), options = options)))
      case _ => Some(decide)

  private def kept(decide: Decide, refs: Vector[DecisionOptionRef],
      verdicts: Map[DecisionOptionRef, Either[OathViolation, Unit]],
      required: Boolean): Either[OathViolation, Option[Decide]] =
    val rejected = refs.map(verdicts).collect { case Left(violation) => violation }
    if required && refs.nonEmpty && rejected.size == refs.size then
      Left(rejected.head)
    else Right(narrowed(decide, ref => verdicts(ref).isRight))

  /** Every answer `decide` accepts, in the order a search tries them, or
    * `None` for a kind whose answers are not tried.
    */
  private def answers(decide: Decide): Option[Iterator[DecisionAnswer]] =
    decide.query match
      case one: DecisionQuery.ChooseOne => Some(one.options.iterator.map(
        option => DecisionAnswer.ChooseOneAnswer(option.ref)))
      case many: DecisionQuery.ChooseMany => Some(selections(
        many.options.map(_.ref), many.min, many.max)
        .map(DecisionAnswer.ChooseManyAnswer(_)))
      case amount: DecisionQuery.ChooseAmount => Some(
        (amount.min to amount.max).iterator
          .map(DecisionAnswer.ChooseAmountAnswer(_)))
      case _ => None

  /** The selections of `refs` a choose-many accepts, smallest first, each in
    * declared order. */
  private def selections(refs: Vector[DecisionOptionRef], min: Int,
      max: Int): Iterator[Vector[DecisionOptionRef]] =
    (math.max(min, 0) to math.min(max, refs.size)).iterator
      .flatMap(size => refs.combinations(size))

  /** The first surviving answer's `Right`, or the first rejection. */
  @annotation.tailrec
  private def survivor(answers: Iterator[DecisionAnswer],
      verdict: DecisionAnswer => Either[OathViolation, Unit],
      first: Option[OathViolation]): Either[OathViolation, Unit] =
    if !answers.hasNext then Left(first.getOrElse(
      OathViolation.InvalidEventOrder("the decision offers no answer")))
    else verdict(answers.next()) match
      case Right(()) => Right(())
      case Left(violation) =>
        survivor(answers, verdict, first.orElse(Some(violation)))
```

- [ ] **Step 4: Point `WalkerPowerGather` at the moved `narrowed`**

In `WalkerPowerGather.scala`, delete the private `narrowed` method and its doc comment (the block that starts `/** \`decide\` offering only the permitted options.`). Then replace each remaining call `narrowed(` with `WalkerSearch.narrowed(`:

```bash
python3 - <<'EOF'
import re
p = 'src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala'
s = open(p).read()
start = s.index("  /** `decide` offering only the permitted options.")
end = s.index("  /** A required decision with every option forbidden")
s = s[:start] + s[end:]
s = re.sub(r'(?<![\w.])narrowed\(decide,', 'WalkerSearch.narrowed(decide,', s)
open(p, 'w').write(s)
EOF
grep -n "narrowed(" src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala
```

Expected: every remaining call reads `WalkerSearch.narrowed(`. A local value or parameter named `narrowed` (as in `lookAheadNotes`) is untouched.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerSearchSuite oathdigital.gameplay.RestrictionLookAheadSuite"`
Expected: PASS. `RestrictionLookAheadSuite` is unchanged in behaviour, since `probe` still narrows through the moved `narrowed`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala
git commit -m "feat(walker): add the search's answers and narrowing"
```

---

### Task 3: The walker searches before it parks

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Test: `src/test/scala/oathdigital/gameplay/LazyPruningSuite.scala`, plus the suites the triage in Step 9 names

**Interfaces:**
- Consumes: everything Task 2 produces.
- Produces:
  - `WalkerPowers.quiet: WalkerPowers` (a memoized copy with `probing = false`)
  - `WalkerPowerGather.breaches(root: Operation, state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers, answered: Vector[Answered], procedure: Option[ProcedureRef]): Vector[OathViolation]`
  - `ProcedureWalker.narrowParked(state: ReadyGame, action: Operation, pending: PendingTree, powers: WalkerPowers, decide: Decide): Decide` (`private[walker]`)
  - `DeltaMeaning.of(ops: Vector[CoreOperation], fallback: String): DeltaMeaning`
  - `WalkerPowerGather.probe` no longer exists.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/LazyPruningSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerSimulation}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The walker hides an option from which no path reaches a legal end, and
  * stops looking at hidden information (global operation restrictions
  * design, "Lazy pruning" and "Testing: Slice 2"). Locked refuses to discard
  * the faceup Sealing Ward, which is what makes a path fail here.
  */
class LazyPruningSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val ready = Table.start.adviser(p1, lockedCard).ready
  private val ask = "test.ask"
  private val next = "test.next"
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)

  private def discardLocked(required: Boolean): Operation =
    Discard.Denizen(lockedCard, PositionedLocation(Location.PlayArea(p1)),
      Region.Provinces, catalog.suitOf(lockedCard).get, 0, 0, p1,
      required = required)

  private def answer(pending: PendingTree): Option[DecisionOptionRef] =
    pending.answered.collectFirst {
      case Answered(`ask`, DecisionAnswer.ChooseOneAnswer(chosen), _) => chosen
    }

  /** Asks `ask` with `keys`, then walks what `after` gives for the answer. */
  private def asking(keys: Vector[String],
      after: DecisionOptionRef => Vector[Operation]): Operation =
    Sequence(Vector[Operation](
      Decide(ask, p1, DecisionQuery.ChooseOne(keys.map(button))),
      Branch((_, pending) => answer(pending).toVector.flatMap(after))))

  private def offered(tree: Operation): Vector[DecisionOptionRef] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        ProcedureWalker.openDecisions(ready, tree, pending, powers).head
          .query match
          case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a park, got $other")

  private def discarding(required: Boolean): Operation =
    asking(Vector("discard", "keep"), chosen =>
      if chosen == ref("discard") then Vector(discardLocked(required))
      else Vector.empty)

  test("an option whose required operation is refused is hidden"):
    assertEquals(offered(discarding(required = true)), Vector(ref("keep")))

  test("an option whose refused operation is optional stays offered"):
    assertEquals(offered(discarding(required = false)),
      Vector(ref("discard"), ref("keep")))

  test("a later required decision, another player's, with every option " +
      "pruned hides the earlier option"):
    val tree = asking(Vector("onward", "stop"), chosen =>
      if chosen == ref("onward") then Vector(
        Decide(next, p2, DecisionQuery.ChooseOne(Vector(button("discard")))),
        discardLocked(required = true))
      else Vector.empty)
    assertEquals(offered(tree), Vector(ref("stop")))

  test("an action with no legal path does not start"):
    val tree = asking(Vector("discard"), _ => Vector(discardLocked(true)))
    assert(!WalkerSimulation.starts(tree, ready, powers))
    assert(ProcedureWalker.advance(ready, tree, None, powers).isLeft)

  test("the search stops at a roll: a refusal after it stays offered"):
    val roll = Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Attack),
      RollMode.Automatic)
    val tree = asking(Vector("roll", "keep"), chosen =>
      if chosen == ref("roll") then Vector(roll, discardLocked(true))
      else Vector.empty)
    assertEquals(offered(tree), Vector(ref("roll"), ref("keep")))

  test("a decision that loops back to itself keeps both its options"):
    val stop = DecisionAnswer.ChooseOneAnswer(ref("stop"))
    val loop = Repeat((_, pending) =>
      !pending.answered.lastOption.exists(_.answer == stop),
      Decide(ask, p1, DecisionQuery.ChooseOne(Vector(button("again"),
        button("stop")))))
    assertEquals(offered(loop), Vector(ref("again"), ref("stop")))

  test("a hidden option is refused when it is answered anyway"):
    val tree = discarding(required = true)
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("discard")), p1),
      powers), Left(OathViolation.InvalidEventOrder(
        s"decision $ask does not offer the selected option")))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.LazyPruningSuite"`
Expected: FAIL. The first, third, fourth and last tests fail, because today the look-ahead checks only tree-level `Restriction`s. The optional, roll and loop tests pass already. If `CatalogNames.denizen("Sealing Ward")` is not a lock-icon card, pick another with `CardRestrictions.Locked` or `LockedAdviserOnly` from `GlobalRestrictionsWalkerSuite`.

- [ ] **Step 3: Move `deltaMeaning` out of `ProcedureWalker`**

This keeps `ProcedureWalker.scala` under 800 lines. In `WalkerEvents.scala`, inside `object DeltaMeaning`, after `final case class OperationApplied`, add:

```scala
  /** What an executed batch means, for logs and projections: the single
    * operations they name, or `fallback` for anything else. */
  def of(ops: Vector[CoreOperation], fallback: String): DeltaMeaning =
    ops match
      case Vector(ModifyDicePool(pool, delta, _)) =>
        DicePoolModified(pool, delta)
      case Vector(SpendSupply(player, amount, _)) =>
        SupplySpent(player, amount)
      case Vector(Move(Piece.Card(relic: RelicId),
          PositionedLocation(Location.Site(site), _),
          PositionedLocation(Location.PlayArea(player), _), _)) =>
        RelicAcquired(player, relic, site)
      case _ => OperationApplied(fallback)
```

Add `Location`, `ModifyDicePool`, `Move`, `Piece`, `PositionedLocation` and `SpendSupply` to that file's `oathdigital.model` import. In `ProcedureWalker.scala`, delete the private `deltaMeaning` method and replace its one call, `deltaMeaning(updated.executed, label)`, with `DeltaMeaning.of(updated.executed, label)`. Compile with `./sbtw compile` and remove the imports the compiler now reports as unused in `ProcedureWalker.scala` (expected: `ModifyDicePool`, `Move`, `Piece`, `PositionedLocation`, `Location`, `RelicId`, `SpendSupply` and the four `DeltaMeaning` members).

- [ ] **Step 4: Add `quiet` to `WalkerPowers`**

In `WalkerPowers.scala`, replace the doc comment and body of `hasRestrictions` and add `quiet` after `operationRestrictions`:

```scala
  /** Whether any power can reject an action through a tree-level
    * `Restriction`. The search's tree check has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = powers.exists(_.contributions.values
    .exists(_.exists(_.isInstanceOf[Restriction])))

  /** The operation restrictions every step of this command runs with. */
  lazy val operationRestrictions: Vector[OperationRestriction] =
    restrictionSet.active(powers.flatMap(_.operationRestrictions), modifiers)

  /** These powers without the search, for a dry run inside one. It is built
    * once per instance, so repeated dry runs share one copy and build its
    * restrictions once. */
  lazy val quiet: WalkerPowers =
    if probing then copy(probing = false) else this
```

In the class doc comment, replace the paragraph that begins "`probing` is on for every command." with:

```scala
  * `probing` is on for every command: the walker searches before a decision
  * parks and hides the options from which no path reaches a legal end
  * (global operation restrictions design, "Lazy pruning"). [[quiet]] turns
  * it off for a dry run inside a search, so that run never searches in turn.
```

Replace every `powers.copy(probing = false)` in `ProcedureWalker.scala` and `WalkerPowerGather.scala` with `powers.quiet`:

```bash
sed -i '' 's/powers\.copy(probing = false)/powers.quiet/g' src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala
grep -rn "probing = false)" src/main/scala/oathdigital/gameplay/walker/
```

Expected: the grep shows only `openDecisions`'s `probing` parameter uses, such as `parkedDecide(state, action, pending, powers, probing = false)`.

- [ ] **Step 5: Replace `probe` with `breaches` in `WalkerPowerGather`**

Delete the whole `probe` method and its doc comment (from `/** The restriction look-ahead (rule-gaps design, section 1)` to the end of `probe`). In its place add:

```scala
  /** The tree-level `Restriction` violations of `root` with `answered`,
    * against the state as the walk sees it: no pending tree, the walk's
    * procedure. Empty without a power that restricts. The traversal runs on
    * the quiet powers, so a dry run inside it never searches.
    */
  def breaches(root: Operation, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers, answered: Vector[Answered],
      procedure: Option[ProcedureRef]): Vector[OathViolation] =
    if !powers.hasRestrictions then Vector.empty
    else restrictionViolations(root, powers.quiet, state.updateCurrent(
      _.copy(walkerPending = None, walkerProcedure = procedure)),
      activePlayer, answered)
```

In `leafAt`, replace the body's `.map` so a parked decision is narrowed by the search:

```scala
  def leafAt(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[Operation] =
    resolveAt(state, pending, powers, action, pending.at, Vector.empty,
      Set.empty).map:
        case decide: Decide => ProcedureWalker.narrowParked(state, action,
          pending, powers, decide)
        case other => other
```

In `leafAt`'s doc comment, replace the paragraph that begins "When `powers.probing` is on" with:

```scala
    * When `powers.probing` is on, a `Decide` is narrowed by the search
    * ([[ProcedureWalker.narrowParked]]), exactly as the walk narrowed it
    * before parking. A position the walk would not have parked at, whose
    * search empties the decision, is returned as declared, and the
    * answer-time check refuses it.
```

Step 4's `sed` already turned `lookAheadNotes`' `powers.copy(probing = false)` into `powers.quiet`; nothing more changes there. Replace any remaining doc comment reference to `probe` in `WalkerPowerGather.scala` and `ProcedureWalker.scala` with a reference to the search (`grep -n "probe" src/main/scala/oathdigital/gameplay/walker/*.scala`).

- [ ] **Step 6: Add search mode to `ProcedureWalker`**

Make these edits in `ProcedureWalker.scala`.

(a) Add `DecisionAnswer` to the `oathdigital.model` import.

(b) In `WalkCtx`, after the `previous` field, add two fields (put a comma after `previous`'s default):

```scala
      previous: Option[(ReadyGame, ReadyGame)] = None,
      /** Set on a search's walk (global operation restrictions design, "Lazy
        * pruning"): it stops at hidden information and at a decision some
        * answer leads on from, and its events are thrown away. */
      searching: Boolean = false,
      /** The decisions this search stands at, with the state at each. */
      visited: Set[(Vector[String], ReadyGame)] = Set.empty
```

(c) After `private final case class Park(...)`, add:

```scala
  /** A search stopped here and counts the path as legal: at hidden
    * information, at a decision some answer leads on from, or back at a
    * decision it already stands at in the same state. Only a search's walk
    * returns it. */
  private final case class Stopped(ctx: WalkCtx) extends Step
```

(d) Replace `toOutcome` with:

```scala
  private def toOutcome(step: Step): Either[OathViolation, WalkerOutcome] =
    step match
      case Done(ctx) =>
        Right(WalkerOutcome.Finished(finish(ctx), ctx.events))
      case Park(position, ctx) =>
        Right(WalkerOutcome.Parked(PendingTree(at = position,
          answered = ctx.answered), ctx.events))
      case Stopped(_) => contractViolation("only a search stops short of a park")
```

and change the three `.map(toOutcome)` calls (in `advance`, `roll` and `resolve`) to `.flatMap(toOutcome)`:

```bash
sed -i '' 's/\.map(toOutcome)/.flatMap(toOutcome)/' src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala
grep -n "toOutcome" src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala
```

Expected: three `.flatMap(toOutcome)` calls and the definition.

(e) In `walkRepeat`'s `pass` and in `continue`, a `Stopped` step ends the walk like a park. Replace `case park: Park => Right(park)` in both places:

```bash
sed -i '' 's/case park: Park => Right(park)/case stop @ (_: Park | _: Stopped) => Right(stop)/' src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala
grep -n "case stop @" src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala
```

Expected: two matches.

(f) In `walkFolded`, a search's dry runs inside an `OfferHost` pass run quiet. Replace the `applyWindowNoted` call's powers argument:

```scala
    val (folded, order, hidden) = WalkerPowerGather.applyWindowNoted(window,
      operation, ctx.state, ctx.activePlayer,
      if ctx.searching then ctx.powers.quiet else ctx.powers, path, children,
      ctx.procedure, ctx.answered, cursor.isDefined, noting = cursor.isEmpty)
```

(g) Replace `walkComposite` with:

```scala
  private def walkComposite(composite: Operation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, hooks: WalkerHooks): Either[OathViolation, Step] =
    // A search stops at a composite that shows hidden information (a `Draw`,
    // a `Reveal`) before any of it runs.
    if ctx.searching && cursor.isEmpty && WalkerSearch.hides(composite) then
      Right(Stopped(ctx))
    else
      // The composite is walked as its children, and a bare child is
      // best-effort: without this its own `required` would be lost, and an
      // unaffordable `PayCost` would shrink to what the player holds.
      val required = composite match
        case core: CoreOperation => core.required
        case _ => false
      // A fresh composite is checked whole first; a resumed one already was.
      val runs = composite match
        case core: CoreOperation if cursor.isEmpty =>
          OperationResolution.screen(ctx.state, core,
            ctx.powers.operationRestrictions, required || hooks.strict)
        case _ => Right(true)
      runs.flatMap { run =>
        if !run then Right(Done(ctx.copy(previous = Some((ctx.state, ctx.state)))))
        else walkFolded(composite.window, composite, composite.children, ctx,
          path, cursor, resume,
          if required then hooks.copy(strict = true) else hooks)
      }
```

(h) Replace `runLeaf` (and its doc comment) with:

```scala
  /** Executes or parks one leaf at its own position, recording
    * `contributions` (every enclosing window's gather order, this leaf's own
    * included) on whatever event it emits. A `Decide` where it is asked --
    * reached fresh or being answered -- is first narrowed by the search
    * ([[narrowAt]]). A required decision with nothing left rejects the
    * command, and an optional one with nothing left is passed without
    * asking. A search's walk does not park at a decision it reaches: it asks
    * whether some answer leads on ([[reach]]).
    */
  private def runLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, contributions: Vector[PowerId],
      strict: Boolean): Either[OathViolation, Step] =
    leaf match
      case decide: Decide if ctx.searching && cursor.isEmpty =>
        reach(decide, ctx, path)
      case decide: Decide if !ctx.searching && asked(cursor, resume) =>
        narrowAt(decide, ctx, path).flatMap:
          case Some(narrowed) =>
            // Reached fresh, so this is the only time it is asked this pass.
            val hidden = if cursor.nonEmpty then Vector.empty
              else WalkerPowerGather.lookAheadNotes(ctx.root, decide, narrowed,
                ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
                ctx.procedure)
            runNarrowed(narrowed, ctx.copy(events = ctx.events ++ hidden), path,
              cursor, resume, contributions, strict)
          case None if cursor.isEmpty => Right(Done(ctx))
          case None => Left(OathViolation.InvalidEventOrder(
            s"decision ${decide.decisionId} has nothing left to offer"))
      case _ => runNarrowed(leaf, ctx, path, cursor, resume, contributions,
        strict)

  /** `decide` narrowed to the options from which some path reaches a legal
    * end (global operation restrictions design, "Lazy pruning"), or as
    * declared when the powers do not search. */
  private def narrowAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Option[Decide]] =
    if !ctx.powers.probing then Right(Some(decide))
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path))

  /** A search at a decision it reaches. Back at a decision it already stands
    * at in the same state, it stops: the loop leads on exactly when the first
    * visit does, and the first visit tries the other answers. */
  private def reach(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Step] =
    if ctx.visited((path, ctx.state)) then Right(Stopped(ctx))
    else WalkerSearch.reach(decide, verdict(decide, ctx, path)).map:
      case WalkerSearch.Reach.Skipped => Done(ctx)
      case WalkerSearch.Reach.Answerable => Stopped(ctx)

  /** Whether one answer to `decide`, given by its owner, can reach a legal
    * end: it adds no tree-level `Restriction` violation the answers so far do
    * not already produce, and a search's walk from it on a copy of the state
    * is not rejected. The search runs the real pipeline, so any rejection of
    * a required operation fails the path. */
  private def verdict(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : DecisionAnswer => Either[OathViolation, Unit] =
    val baseline = WalkerPowerGather.breaches(ctx.root, ctx.state,
      ctx.activePlayer, ctx.powers, ctx.answered, ctx.procedure).toSet
    val searching = ctx.copy(events = Vector.empty, searching = true,
      visited = ctx.visited + ((path, ctx.state)))
    answer =>
      val given = Answered(decide.decisionId, answer, decide.owner)
      WalkerPowerGather.breaches(ctx.root, ctx.state, ctx.activePlayer,
          ctx.powers, ctx.answered :+ given, ctx.procedure)
        .find(!baseline(_)).toLeft(())
        .flatMap(_ => searched(walk(ctx.root, searching, Vector.empty,
          Some(path), AnswerResume(given), WalkerHooks.none)))

  /** A search's walk as a verdict. A broken position is a rejection, as it
    * is for a simulation. */
  private def searched(walked: => Either[OathViolation, Step])
      : Either[OathViolation, Unit] =
    try walked.map(_ => ())
    catch
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid searched walker position")))

  /** `decide`, parked at `pending`, narrowed as the walk narrowed it before
    * parking. A position whose search empties a required decision is
    * returned as declared, and the answer-time check refuses it. */
  private[walker] def narrowParked(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers, decide: Decide): Decide =
    val ctx = WalkCtx(strip(state), Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers,
      WalkerDice.placeholder, state.game.current.walkerProcedure, action)
    narrowAt(decide, ctx, pending.at).toOption.flatten.getOrElse(decide)
```

(i) In `runNarrowed`, in the `case None =>` branch, make the first case stop a search at a leaf that hides information, before the automatic roll:

```scala
      case None =>
        leaf match
          case hidden if ctx.searching && WalkerSearch.hides(hidden) =>
            Right(Stopped(ctx))
          case roll: Roll if roll.mode == RollMode.Automatic =>
```

and change the `BuildOps` case to `case build: BuildOps => runBuildOps(build, ctx, path, contributions)` (no `.map(Done(_))`).

(j) Replace `runBuildOps` with a version that returns a step and stops a search at a batch that hides information:

```scala
  /** Executes a [[oathdigital.model.BuildOps]] leaf: `build(state, pending)` returns the delta
    * batch to run through the pipeline, recorded as the node's step ops. An
    * empty batch runs nothing and records nothing, and a note after it reads
    * that nothing changed. A search stops at a batch that shows hidden
    * information.
    */
  private def runBuildOps(build: BuildOps, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, Step] =
    val tree = PendingTree(at = path, answered = ctx.answered)
    build.build(ctx.state, tree).flatMap { ops =>
      if ctx.searching && ops.exists(WalkerSearch.hides) then
        Right(Stopped(ctx))
      else if ops.isEmpty then
        Right(Done(ctx.copy(previous = Some((ctx.state, ctx.state)))))
      else recordBatch(ops, contributions, ctx, path, leafLabel(build))
        .map(Done(_))
    }
```

- [ ] **Step 7: Compile and check the line bound**

```bash
./sbtw compile 2>&1 | grep -E "error|warn|success" | head -20
wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala
```

Expected: `[success]`, and both files at or under 800 lines. Remove any import the compiler reports as unused. If a pattern match is reported as not exhaustive, it is a `Step` match that misses `Stopped`; handle it as a park is handled. If `ProcedureWalker.scala` is over 800 lines, shorten the doc comments this task added before touching other code.

- [ ] **Step 8: Run the new and the look-ahead suites**

Run: `./sbtw "testOnly oathdigital.gameplay.LazyPruningSuite oathdigital.gameplay.RestrictionLookAheadSuite oathdigital.gameplay.PowerNoteHideSuite oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: PASS. `RestrictionLookAheadSuite`'s "the walk inside a probe does not probe its own decisions" still counts 3 restriction calls: one baseline traversal and one per option, while the search's walk dry-runs the host on the quiet powers.

- [ ] **Step 9: Run the full suite and triage**

Run: `./sbtw test 2>&1 | grep -E "==> X|Passed: Total|Failed: Total" | head -40`

For each failing test, read it and decide which case applies:

1. **The option is now hidden, correctly.** The test answered an option, or expected it offered, from which no path reaches a legal end (an unaffordable cost, a refused required operation, a required decision left empty). Update the assertion to expect the option hidden, or the start refused. Keep the test's name truthful: rename it if it said the option is offered.
2. **The search is wrong.** An option with a legal path is hidden, or the walk throws or loops. Fix the search in `ProcedureWalker` or `WalkerSearch`, not the test, and add a test to `LazyPruningSuite` that reproduces the case first.
3. **A count or order changed** (a counting restriction, an event count) only because the search walks more. Check that the walk records the same durable events as before. If it does, update the count and say why in the commit message.

Repeat until the suite is green. List every test changed under case 1 or 3 in the commit message.

- [ ] **Step 10: Run the budget benchmark**

Run: `./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`
Expected: both measures under 50 ms. If it fails, do not tune it here: finish the task and let Task 5 handle the budget.

- [ ] **Step 11: Commit**

```bash
git add -A src/main/scala/oathdigital/gameplay/walker src/test/scala
git commit -m "feat(walker): hide options with no legal path by searching before a park"
```

---

### Task 4: Atomic batches, and the locked filters retire

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`BuildOps`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`runBuildOps`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` (`isLocked` retires)
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`, `src/main/scala/oathdigital/gameplay/OathRules.scala`, `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Test: `src/test/scala/oathdigital/gameplay/GlobalRestrictionsWalkerSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`, `src/test/scala/oathdigital/gameplay/powers/TargetsFixture.scala`, `src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`, `src/test/scala/oathdigital/gameplay/PhasePowerSuite.scala`

**Interfaces:**
- Consumes: the search from Task 3.
- Produces:
  - `BuildOps(build, window = None, required: Boolean = false)`
  - `PhasePowerProcedure.usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId, powers: PhasePowers, walkerPowers: WalkerPowers): Vector[PowerSource]`
  - `OperationRestrictions.isLocked` no longer exists.

- [ ] **Step 1: Give the test fixtures the global restrictions**

The search can only hide a locked card when the walk runs with the catalog's restrictions. Two fixtures build walker powers without them.

In `WhenPlayedHarness.scala`, change `powers` to:

```scala
  def powers(power: ContributingPower): WalkerPowers =
    WalkerPowers(Vector(power),
      restrictionSet = OperationRestrictions.forCatalog(catalog))
```

and add `import oathdigital.gameplay.operations.OperationRestrictions`. If the file has no `catalog` in scope, import `oathdigital.gameplay.setup.FirstGameSetupFixture.catalog`.

In `TargetsFixture.scala`, give `rules` the restrictions and no extra powers:

```scala
  val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector.empty,
      restrictionSet = OperationRestrictions.forCatalog(catalog)),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
```

with imports for `WalkerPowers` (`oathdigital.gameplay.walker`) and `OperationRestrictions` (`oathdigital.gameplay.operations`).

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS. A failure here is a suite that relied on running without Locked or the Hall; triage it as in Task 3, Step 9.

- [ ] **Step 2: Write the failing tests**

In `GlobalRestrictionsWalkerSuite.scala`, add a pair of tests. Both swap the faceup locked adviser with another player's adviser, a valid swap that Locked refuses:

```scala
  private val swapping = Table.start.adviser(p1, lockedCard)
    .adviser(p2, wildCry).ready
  private def swapBatch(required: Boolean): Operation = Sequence(Vector(
    BuildOps((_, _) => Right(Vector(Swap(lockedCard,
      PositionedLocation(Location.PlayArea(p1)), wildCry,
      PositionedLocation(Location.PlayArea(p2))))), required = required)))

  test("a plain BuildOps skips a refused swap"):
    unchanged(swapping, swapBatch(required = false), powers)

  test("a required BuildOps rejects the whole batch when one operation is " +
      "refused"):
    assert(ProcedureWalker.advance(swapping, swapBatch(required = true), None,
      powers).isLeft)
```

In `HornedMaskSuite.scala`, replace the test "a full area of locked advisers takes nothing and asks nothing" with:

```scala
  test("a full area of locked advisers cannot use it, and it is not offered"):
    val ready = holding(locked*)
    assert(use(ready, power, source).isLeft)
    assertEquals(PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowers(Vector(power)), WalkerPowers(Vector.empty,
        restrictionSet = OperationRestrictions.forCatalog(catalog))),
      Vector.empty)
```

with imports `oathdigital.gameplay.phases.PhasePowerProcedure`, `oathdigital.gameplay.powerresolver.PhasePowers`, `oathdigital.gameplay.walker.WalkerPowers` and `oathdigital.gameplay.operations.OperationRestrictions`.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.GlobalRestrictionsWalkerSuite oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: compilation fails: `BuildOps` has no `required` parameter, and `usable` takes four arguments.

- [ ] **Step 4: Add `BuildOps.required`**

In `CoreOperations.scala`, change `BuildOps` to:

```scala
final case class BuildOps(
    build: (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]],
    override val window: Option[PowerWindow] = None,
    override val required: Boolean = false)
    extends PrimitiveOperation
```

and add to its doc comment:

```scala
  * A `required` batch runs whole or rejects: an operation a restriction
  * refuses rejects the batch instead of being skipped, so a search counts the
  * path as failed.
```

In `ProcedureWalker.runBuildOps`, pass the flag:

```scala
      else recordBatch(ops, contributions, ctx, path, leafLabel(build),
        requireAll = build.required).map(Done(_))
```

- [ ] **Step 5: Retire Twin Brother's filter**

In `TwinBrother.scala`, drop the lock check from `candidates`:

```scala
      if definition.suit == Suit.Nomad
```

(in place of the two lines that also call `OperationRestrictions.isLocked`), make the swap's `BuildOps` required:

```scala
        BuildOps((ready, pending) => swap(ready, actor, pending),
          required = true),
```

and remove the `OperationRestrictions` import. The search now hides a locked nomad adviser, because its `Swap` is refused and the required batch fails.

- [ ] **Step 6: Retire Horned Mask's filter**

In `HornedMask.scala`:

- Delete `discardable`.
- `takeable` no longer checks whether a discard is possible:

```scala
  private def takeable(ready: ReadyGame, actor: PlayerId): Vector[DenizenState] =
    site(ready, actor).toVector.flatMap(_._2.denizens.collect {
      case d: DenizenState => d })
```

- In `askDiscard` and in `discard`, replace `discardable(ready, actor)` with `advisers(ready, actor)`.
- Remove the `OperationRestrictions` import.

The discard is already a required operation, so the search hides a locked adviser, and hides every denizen when every adviser is locked.

- [ ] **Step 7: Retire `isLocked`**

In `OperationRestrictions.scala`, delete `isLocked` and its doc comment. If `lockIcon` is now used only by `printedBy`, keep it. Then:

```bash
grep -rn "isLocked" src/main/scala src/test/scala
```

Expected: no matches. If a test called `isLocked`, rewrite it against `LockedCards.showing` and the lock-icon card list, or delete it when `OperationRestrictionsSuite` already covers the same case through `reason`.

- [ ] **Step 8: Add the phase power dry run**

In `PhasePowerProcedure.scala`, add `walkerPowers: WalkerPowers` as the last parameter of `usable`, and require the dry run in its filter:

```scala
  def usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers, walkerPowers: WalkerPowers): Vector[PowerSource] =
```

In the `collect` guard, after `power.usable(ready, player, ref)`, add `&& starts(catalog, ready, player, power, found, ref, walkerPowers)`. Add the helper:

```scala
  /** Whether the use could start now: its tree, cost included, passes the
    * same first walk a start runs, so a use with no legal path is not
    * offered (global operation restrictions design, "Lazy pruning"). */
  private def starts(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, power: PhasePower, found: PowerSourceRef,
      ref: DecisionOptionRef, walkerPowers: WalkerPowers): Boolean =
    power.build(ready, player, ref).exists(tree => WalkerSimulation.starts(
      assemble(catalog, power, player, found, tree), ready, walkerPowers))
```

Import `oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}`. Update the callers:

- `OathRules.restPowerUsable` and `OathRules.wakeOptionOpen`: pass `walkerPowers(ready, player, Vector.empty)`.
- `PhasePowerProjector`: add `private val walkerPowers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)` and pass it. Import `oathdigital.gameplay.powers.WalkerPowerCatalog` and `oathdigital.gameplay.walker.WalkerPowers`.
- `PhasePowerSuite`: add `WalkerPowers.empty` as the last argument of each `PhasePowerProcedure.usable` call the compiler reports, and import `oathdigital.gameplay.walker.WalkerPowers`.

- [ ] **Step 9: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.GlobalRestrictionsWalkerSuite oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.gameplay.powers.whenplayed.TwinBrotherSuite oathdigital.gameplay.PhasePowerSuite"`
Expected: PASS. In `HornedMaskSuite`, "a locked adviser cannot be offered for discard" and "a facedown locked adviser can be offered for discard" pass unchanged; in `TwinBrotherSuite`, "it offers other players' faceup unlocked nomad advisers, then keeping" passes unchanged. The search now does what the filters did.

- [ ] **Step 10: Run the full suite and the architecture check**

```bash
./sbtw test 2>&1 | grep -E "==> X|Passed: Total|Failed: Total" | head -20
python3 scripts/check-architecture.py
```

Expected: all green. Triage any failure as in Task 3, Step 9. If `scripts/check-architecture.py` is not at that path, find it with `find . -name check-architecture.py -not -path "./node_modules/*"`.

- [ ] **Step 11: Commit**

```bash
git add -A src/main/scala src/test/scala
git commit -m "refactor(restrictions): retire the locked filters for the search"
```

---

### Task 5: Budget check and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`
- Modify: `docs/ROADMAP.md`
- Possibly modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, `src/main/scala/oathdigital/gameplay/operations/LockedCards.scala` (only if the budget needs the caches)

- [ ] **Step 1: Measure**

Run the full suite twice and keep the second `Total time`, as in Task 1, Step 1. Run `./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`.

The budget holds when the second run's time is at most `BASELINE × 1.15` and the benchmark reports both measures under 50 ms.

- [ ] **Step 2: If the budget fails, apply the spec's caches and re-measure**

The spec names two caches that stay within the design ("The set's cost inside the search"). Apply them one at a time, re-measuring after each:

1. `CardPlay.legalChoices` builds `OperationRestrictions.forCatalog(catalog)` on every call. Build it once per `ExecutableCatalog`: hold it in a `private val` of the object that owns `legalChoices` when the catalog is fixed there, or pass it in from the caller that holds the catalog.
2. `LockedCards.showing` scans every player and every site per check. Index the faceup lock-icon cards in play once per state: compute a `Set[CardId]` of the cards that show their lock, in one pass over the players' advisers and the sites' denizens, and test membership in it.

If the budget still fails, stop. Do not lower anything that acts as a depth cap. Report the measurements to the product owner with two or three alternative designs (for example: memoizing verdicts by position and state within one command, or searching at parks only while projections read a cached narrowing).

- [ ] **Step 3: Update the spec**

In `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`:

- Under "Lazy pruning", after the bullet **Hidden information.**, add these bullets:

```markdown
- **Any rejection prunes.** The search runs the real pipeline, so a path
  fails when any required operation is rejected, for whatever reason, not
  only when a restriction refuses it.
- **Answers tried.** A choose-many keeps an option when some accepted
  selection holding it survives; selections are tried smallest first, and the
  search stops at the first survivor. A choose-amount is tried value by value.
  Partition and Distribute are not enumerated: a search that reaches one
  counts the path as legal, and the answer-time check stays.
- **Loops.** A search that reaches a decision it already stands at, in the
  same state, stops there and counts the path as legal. The first visit tries
  the other answers.
- **Atomic batches.** A `BuildOps` marked `required` runs whole or rejects,
  so a refused operation inside it fails the path instead of being skipped.
- **Phase powers.** A phase power's use is offered only when the same dry run
  a start runs accepts it.
```

- Replace the bullet **Notes.** with:

```markdown
- **Notes.** The notes that tree-level `Restriction`s write for the options
  they hide stay. An operation restriction's note is deferred until a card
  needs one: Lost Tongue's refused `Take` writes no line (Catalog batch 3).
```

- In "Performance budget", after the baseline sentence from Task 1, add the result: `After slice 2, sbt test took N s (+P%), and the SearchBudget benchmark measured START ms and READ ms.` Fill in the numbers, and say which caches from Step 2 were applied, if any.
- In "What retires", in the bullet about Horned Mask and Twin Brother, add at the end: "Both retired in slice 2, with `OperationRestrictions.isLocked`."
- In "Verify at plan time", under **Search inputs.**, add: "Resolved in the slice 2 plan: see **Answers tried** under Lazy pruning. A decision owned by another player reuses the same simulation, answered by its owner."

- [ ] **Step 4: Update the roadmap**

In `docs/ROADMAP.md`, in "Phase - Global operation restrictions": remove the slice 2 item (the one about the lazy search that "replaces the restriction look-ahead"), and change "in two slices:" to "in one slice:". Keep the numbering of the remaining item correct.

- [ ] **Step 5: Check the docs**

```bash
python3 scripts/check-markdown-links.py
```

Expected: no broken links. Find the script with `find` if it is elsewhere.

- [ ] **Step 6: Final full run and commit**

```bash
./sbtw test 2>&1 | tail -3
```

Expected: all green.

```bash
git add -A docs src
git commit -m "docs: record slice 2 search decisions and the measured budget"
```
