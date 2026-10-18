# Catalog Batch 8, Item 10b (Consent for Refused Automatic Powers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When only waivable restrictions (Spell Breaker, Forest Council, City Wall) refuse an enemy's automatic power, the refusing card's ruler decides through consent whether the power works. The power's user is never asked.

**Architecture:**

- **The power-use step.** An `AutomaticUse` node refused only by waivable restrictions walks a hidden step before its effect. The step is `Decide("power-use.<power>", user, ChooseOne(use, skip))`, followed by a `Branch` that runs the effect only on `use`.
  - The node always has the same shape for one verdict: `[slot, body]`. The verdict reads the raw refusals and ignores the ledger, so a grant or a refusal never moves a parked position.
  - A remembered answer, or a preview, needs no step. The slot is then a no-op or the "stays deactivated" note, and the body is the effect or nothing.
- **The step declares its own ask.** Its `use` option is `Unavailable` with the `Askable` reason that `PowerUseRules.shown` builds from the refusals. A ledger grant makes it available. This is the declared-ask path that slice 15 opened, so the three cards contribute no option restriction.
- **The walk opens the request.** After any command whose walk parks at a step, `ConsentDesk.opened` appends a `ConsentRequested` to the same append. The request names the step, `use`, the used card and the issuer of the batch that walked there (`ConsentUse`). Preferences answer it at once, as for any request.
- **Settling.** A grant answers the step `use`. A refusal answers it `skip`. Either answer opens a batch for the issuer and resumes the walk. The resumed walk may reach another step, which opens the next request.
- **Undo coupling.** Undo and Redo never stop at a position parked at an unanswered step. Undo takes back batches until the state is not parked at a step. Redo restores batches the same way, and asks the rulers again if it ends at a step. A chain that needs a barrier batch cannot be undone. While a walk-opened request waits, the issuer's Undo withdraws it and takes back the batch.
- **Hunger** joins the wrapped powers: `ForcedWakeProcedure` wraps each forced power in an `AutomaticUse`. `due` drops only a power refused by a restriction nobody may waive.

**Tech Stack:** Scala 3 on the JVM, Scala.js frontend, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-15-automatic-power-consent-design.md`: the whole design. Its rulings 1 to 9 are settled; this plan implements them.
- `docs/superpowers/specs/2026-10-08-consent-system-design.md`: requests, asks, keys, covers, preferences, settling and undo.
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 10 entry, "Automatic powers".
- `docs/superpowers/specs/2026-10-06-undo-design.md`: batches, issuers and barriers.
- These restate what this item builds on: `walker/AutomaticUses.scala`, `operations/PowerUseRules.scala`, `operations/OperationConsent.scala`, `powers/poweruse/RulerBans.scala`, `application/ConsentDesk.scala`, `application/JournalFold.scala` and `application/GameApplicationService.scala`.

**Starts from:** `main` at `4409ee3e` or later, with slice 15 merged. Before Task 1, check that `OperationAsks.declared` exists and that `PalanquinConsentServiceSuite` passes. If either is missing, stop and ask. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Departures from the design

The design was written before slices 10 part 2 to 15 landed. These choices fit the code as it is now. Task 6 records them in the design.

1. **The step declares its ask.** The design had Spell Breaker, Forest Council and City Wall contribute an `OptionRestriction` at the step's own window. Instead, the step's builder words the `use` option's `Askable` reason itself, from the refusals, as Palanquin's destination does (slice 15). The step has no window. The keys, granters and preferences are the same.
2. **A remembered answer is not journaled as a step answer.** With a remembered grant, a remembered refusal or a preview, the node builds no step at all. Its slot is a no-op, or the "stays deactivated" note. The body is a `Branch` that reads the ledger's answer. A parked step only ever exists while a request waits.
3. **The node always has two children** (`[slot, body]`) when it is allowed or waivable, and none when it is refused unasked. So a resume inside the effect (cursor head `1`) is never confused with a resume at the step (cursor head `0`). Paths inside an allowed node gain a `1` segment.
4. **The request carries `use: Option[ConsentUse]`**, where `ConsentUse(card, issuer)` holds the used card and the player whose batch walked to the step. The card words the prompt and the log. The issuer opens the answer's batch and may withdraw. The design had only `use: Option[PowerId]`.
5. **The answer batch is opened by the issuer**, not the requester. Coupled undo then takes back batches of one issuer, as the void markers require. On a player's own Travel the two are the same player.
6. **Barriers.** When the batch that walked to the step is a barrier (Hunger's forced Wake follows a turn change), nobody may withdraw the request or undo its answer. Undo takes back a coupled chain whole or not at all.
7. **Redo after a withdrawal asks the rulers again**, with the same automatic answers from preferences.
8. **A manual Never or Always on an automatic power's request** sets the preference before the walk resumes. A second step reached in the same command (Tyrant, then Hospitality) is then answered by it at once.
9. **"Stays deactivated" reads as a power line:** "City Wall: Tyrant stays deactivated." Every power line starts with "{source}: ", so the design's "{Tyrant} stays deactivated by {City Wall}." is reworded. Its key is shared, and each of the three cards borrows it.
10. **Previews** walk with `WalkerPowers.previewed`. An unanswered step is then walked as `use`, so a preview never parks there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `ProcedureWalker.scala` is at 795 lines: this plan changes one argument there and adds no line. `WalkerPowerGather.scala` is at 631. Check with `wc -l` before adding, and split a cohesive helper object out if a file would pass 800.
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - Engine files under `gameplay/walker` and `gameplay/operations` never name a power, in code or comments.
  - `PowerUseStep` lives in `gameplay/powerresolver`, which both the walker and the powers may import.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** `ConsentRequest.use` is written only when set, matching the codec's style, and read back as `None` when absent.
- **Log lines omit the actor.** Only a non-actor, or an actor off turn, is named.
- **Lessons from earlier slices' tests:**
  - `TravelFixture.board()` has p1 (the `actor`) at `cradlePlains` (Buried Giant), p2 at `provincePlains` (Dunes) and p3 at `mountain`, in p1's Act, with every site holding its printed bandits. `warbandsAt(site, p, n)` hands a site to `p`; `bandits(site, n)` hands it to the bandits. The Narrow Pass (`pass`) blocks Dunes from the Cradle's plains; `ConsentFixture.passRuledByP2` hands the Pass to p2.
  - The quiet table (`Table.start`) has p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant, in p1's Act.
  - To read a parked decision, use `TravelFixture.parkedDecision(state)` or `ParkedNode.of(state, catalog, ...)`.
  - A consent answer is remembered with `ConsentState(answers = Map(key -> ConsentAnswer.Granted.by(p)), turn = Some(ConsentTurn.of(ready)))`, as `NarrowPassConsentSuite.remembering` does.
  - A use's key is `ConsentKey(refusing power, user, PowerUseRules.useKey, PowerUseRules.target(use))`. For a denizen, `PowerUseRules.target` is `DecisionOptionRef.Denizen(card)`.
  - In the service, a Travel is `ParkedServiceFixture.travelTo(service, gameId, sequence, actor, site)`. If the start parks on the modifier decision, answer it with `ModifierPicks.start` first, as `ModifierPlay.started` does.
  - Service logs read `texts(formatter.format(present(accepted(service.history(gameId)), "a stored game").steps, Some(viewer)))`, with `formatter` and `texts` from `gamelog.LogScripts`. Player names in the log are their ids ("p2").
- **Note keys** are declared through a `NoteSet`. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"` when a task adds keys (Task 1).
- **Names, exactly:**
  - `PowerUseStep` in `gameplay/powerresolver/PowerUseStep.scala`: `use` (`Button("use")`), `skip` (`Button("skip")`), `decisionId(power)` (`"power-use.<power id>"`), `isDecision(id)`, `answer(answered, power)` and `staysDeactivated` (note key `stays-deactivated`).
  - `PowerUseRules.raised(ready, use, restrictions)` and `PowerUseRules.waivable(use, refusals)`.
  - `WalkerPowers.previewing: Boolean = false` (last field) and `WalkerPowers.previewed`.
  - `AutomaticUses.children(use, state, pending, powers, cursor: Option[Vector[String]])`.
  - `WalkerPowerGather.nodeAt(state, action, pending, powers): Option[Operation]`.
  - `ParkedUses.of(state, action, pending, powers): Option[AutomaticUse]` in `gameplay/walker/ParkedUses.scala`.
  - `OathRulesWalker.powerUseStep(state): Option[AutomaticUse]`.
  - `ConsentUse(card: CardId, issuer: PlayerId)` and `ConsentRequest.use: Option[ConsentUse] = None` (last field), in `model/Consent.scala`. Wire field `use`, an object with `card` and `issuer`.
  - `ConsentDesk.opened(state, issuer): Either[OathViolation, Vector[OathEvent]]`.
  - `UndoCoupling` in `application/UndoCoupling.scala`, with `undoing(journal)` and `redoing(journal)`.
  - `ConsentPromptProjection.use: Option[String] = None` and `ConsentWaitingProjection.use: Option[String] = None`, both last fields; wire field `use`.
  - Suites: `AutomaticUseSuite` (Task 1, extended), `AutomaticUseConsentServiceSuite` (Task 2), `AutomaticUseUndoSuite` (Task 3), `GameLogAutomaticUseSuite` (Task 4), `AutomaticUseStepWiringSuite` and `HungerConsentServiceSuite` (Task 5).

---

### Task 1: The power-use step in the walker

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/PowerUseStep.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/ParkedUses.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/AutomaticUses.scala` (rewrite)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:291-294` (one argument)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (`attributed`, `resolveAt`, `foldedChildrenAt`, new `nodeAt`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala:170` (`explained`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala`, `SpellBreaker.scala`, `ForestCouncil.scala` (borrow the note key)
- Modify: `src/main/scala/oathdigital/model/Operation.scala:160-174` (the `AutomaticUse` Scaladoc)
- Test: `src/test/scala/oathdigital/gameplay/walker/AutomaticUseSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala`
- Test: `src/test/resources/notes/declared-keys.txt` (regenerated)

**Interfaces:**
- Consumes: `AutomaticUse(user, power, card, effect)`, `PowerUse.automatic`, `PowerUseRules.refusals` and `.shown`, `OperationWaiver.granter` and `.covers`.
- Produces: `PowerUseStep.*`, `PowerUseRules.raised` and `.waivable`, `WalkerPowers.previewed`, `ParkedUses.of`, `WalkerPowerGather.nodeAt`. Tasks 2 to 5 use all of them.

- [ ] **Step 1: Write the failing walker tests**

In `AutomaticUseSuite.scala`, keep the existing tests. Change the expected park in "a park inside an automatic use resumes at the same decision" from `Vector("0", "0")` to `Vector("0", "1", "0")`: the effect is now the node's second child.

Add these imports: `oathdigital.gameplay.operations.PowerUseRules`, `oathdigital.gameplay.powerresolver.PowerUseStep`, `oathdigital.testkit.Table.p2` (beside `p1`) and `oathdigital.testkit.Unwrap.present`.

Add, after `RefusesTestPowerWithoutFavor`:

```scala
  /** A test power: refuses every use of `automatic` while p1 holds no
    * favor, and p2 may waive it. */
  private object RefusesTestPowerWithoutFavorUnlessP2Consents
      extends ContributingPower with PowerUseRestriction:
    val id: PowerId = PowerId("test.refuses-without-favor-waivably")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def powerUseRestrictions: Vector[PowerUseRestriction] =
      Vector(this)
    private val note = PowerNote(PowerSourceRef.Card(card), "refused",
      Vector.empty)
    def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
      Option.when(use.power == automatic && Look(ready).favor(p1) == 0)(
        PowerUseRefusal(id, note, OperationReason("test-refused",
          "no favor", OperationReasonKind.Impossible, Some(OperationWaiver(
            id, Some(p2), p1sUseKey, note, note)))))

  /** The key p2's answer on p1's use of the test power is remembered
    * under. */
  private val p1sUseKey: ConsentKey = ConsentKey(
    RefusesTestPowerWithoutFavorUnlessP2Consents.id, p1,
    PowerUseRules.useKey, PowerUseRules.cardRef(card))

  private val askable = WalkerPowers(Vector(
    RefusesTestPowerWithoutFavorUnlessP2Consents))
  private val stepId = PowerUseStep.decisionId(automatic)

  /** `ready` with p2's `answer` on p1's use remembered this turn. */
  private def rememberingP2s(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame = ready.copy(consent = ConsentState(answers = Map(
    p1sUseKey -> answer.by(p2)), turn = Some(ConsentTurn.of(ready))))

  private def parkedAt(from: ReadyGame, tree: Operation,
      walkWith: WalkerPowers)(using munit.Location): PendingTree =
    accepted(ProcedureWalker.advance(from, tree, None, walkWith)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")

  private def finishedWithEvents(outcome: Either[OathViolation,
      WalkerOutcome])(using munit.Location): (ReadyGame, Vector[OathEvent]) =
    accepted(outcome) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")

  private def answeringStep(ref: DecisionOptionRef): Answered =
    Answered(stepId, DecisionAnswer.ChooseOneAnswer(ref), p1)
```

Add the tests:

```scala
  test("a waivable refusal parks at the use's step, which its user owns " +
      "and whose use its granter may be asked about"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val pending = parkedAt(start, tree, askable)
    assertEquals(pending.at, Vector("0", "0"))
    val step = present(ProcedureWalker.parkedDecide(start, tree, pending,
      askable), "the step")
    assertEquals((step.decisionId, step.owner), (stepId, p1))
    step.query match
      case one: DecisionQuery.ChooseOne =>
        assertEquals(one.options.map(_.ref),
          Vector(PowerUseStep.use, PowerUseStep.skip))
        one.options.head match
          case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
            assertEquals(ask.asks, Vector(ConsentAsk(p2, Vector(
              RefusesTestPowerWithoutFavorUnlessP2Consents.id))))
            assertEquals(ask.covers, Vector(p1sUseKey))
          case other => fail(s"expected an askable use, got $other")
      case other => fail(s"expected a choose-one, got $other")

  test("the step's answer decides: skip runs nothing, use runs the effect"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val pending = parkedAt(start, tree, askable)
    val (skipped, _) = finishedWithEvents(ProcedureWalker.resolve(start,
      tree, pending, answeringStep(PowerUseStep.skip), askable))
    assertEquals(Look(skipped).favor(p1), 0)
    val granted = rememberingP2s(start, ConsentAnswer.Granted)
    val (used, _) = finishedWithEvents(ProcedureWalker.resolve(granted,
      tree, pending, answeringStep(PowerUseStep.use), askable))
    assertEquals(Look(used).favor(p1), 1)

  test("a waivable refusal beside one nobody may waive deactivates the " +
      "use unasked, with no line"):
    val both = WalkerPowers(Vector(RefusesTestPowerWithoutFavor,
      RefusesTestPowerWithoutFavorUnlessP2Consents))
    val (end, events) = finishedWithEvents(ProcedureWalker.advance(
      p1Holding(0), Sequence(gainsOneFavor), None, both))
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(events.collect { case noted: PowerNoted => noted },
      Vector.empty)

  test("a remembered grant runs the effect without a step"):
    val (end, _) = finishedWithEvents(ProcedureWalker.advance(
      rememberingP2s(p1Holding(0), ConsentAnswer.Granted),
      Sequence(gainsOneFavor), None, askable))
    assertEquals(Look(end).favor(p1), 1)

  test("a remembered refusal runs nothing and says the use stays " +
      "deactivated"):
    val (end, events) = finishedWithEvents(ProcedureWalker.advance(
      rememberingP2s(p1Holding(0), ConsentAnswer.Refused),
      Sequence(gainsOneFavor), None, askable))
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(events.collect { case PowerNoted(_, note, _) => note },
      Vector(PowerUseStep.staysDeactivated(PowerSourceRef.Card(card),
        NoteArg.Card(card))))

  test("a park inside the effect, after its step was answered, resumes at " +
      "the same decision"):
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    val tree = Sequence(AutomaticUse(p1, automatic, card,
      (_, _) => Vector(ask, oneFavor)))
    val start = p1Holding(0)
    val atStep = parkedAt(start, tree, askable)
    val granted = rememberingP2s(start, ConsentAnswer.Granted)
    val atAsk = accepted(ProcedureWalker.resolve(granted, tree, atStep,
      answeringStep(PowerUseStep.use), askable)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")
    assertEquals(atAsk.at, Vector("0", "1", "0"))
    val (end, _) = finishedWithEvents(ProcedureWalker.resolve(granted, tree,
      atAsk, Answered("test.ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("go")), p1), askable))
    assertEquals(Look(end).favor(p1), 1)

  test("a preview walks a use waiting on its rulers as if they let it " +
      "work"):
    assertEquals(WalkerSimulation.run(Sequence(gainsOneFavor), p1Holding(0),
      askable).map(_.nonEmpty), Right(true))

  test("the walk names the automatic use whose step it is parked at, and " +
      "no other"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val atStep = parkedAt(start, tree, askable)
    assertEquals(ParkedUses.of(start, tree, atStep, askable)
      .map(use => (use.user, use.power, use.card)),
      Some((p1, automatic, card)))
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    val asking = Sequence(AutomaticUse(p1, automatic, card,
      (_, _) => Vector(ask)))
    assertEquals(ParkedUses.of(p1Holding(1), asking,
      parkedAt(p1Holding(1), asking, askable), askable), None)

  test("a decision before the step keeps every option: the search walks " +
      "on through the step"):
    val pick = Decide("test.pick", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("a"), "A"),
      DecisionOption.Button(DecisionOptionRef.Button("b"), "B"))))
    val tree = Sequence(pick, gainsOneFavor)
    val start = p1Holding(0)
    val decide = present(ProcedureWalker.parkedDecide(start, tree,
      parkedAt(start, tree, askable), askable), "the pick")
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        assert(one.options.forall(_.available), one.options)
      case other => fail(s"expected a choose-one, got $other")
```

`DecisionOption.available` is the existing accessor `PricedOptions` reads. If the trait names it differently, use that name.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.AutomaticUseSuite"`
Expected: it fails to compile: `PowerUseStep` and `ParkedUses` do not exist yet.

- [ ] **Step 3: Add `PowerUseStep`**

Create `src/main/scala/oathdigital/gameplay/powerresolver/PowerUseStep.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model._

/** The question an automatic power's use waits on when only waivable
  * restrictions refuse it (automatic power consent design, "The power-use
  * step"). Its owner is the power's user, who never answers it: the
  * refusing cards' rulers decide through consent, and their answer
  * answers it. It is never shown, and the log tells it through the
  * consent lines. */
object PowerUseStep:
  private val Prefix = "power-use."

  /** The answer that lets the power work. */
  val use: DecisionOptionRef.Button = DecisionOptionRef.Button("use")
  /** The answer that deactivates it. */
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")

  def decisionId(power: PowerId): String = s"$Prefix${power.value}"
  def isDecision(decisionId: String): Boolean =
    decisionId.startsWith(Prefix)

  /** The latest answer to `power`'s step in `answered`. */
  def answer(answered: Vector[Answered], power: PowerId)
      : Option[DecisionOptionRef] = answered.reverseIterator.collectFirst {
    case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
        if id == decisionId(power) => ref }

  private val notes = NoteSet.unowned("power-use step")
  /** "{card} stays deactivated.", under the refusing card, when a refusal
    * remembered this turn deactivates the power unasked. Each waivable
    * power-use restriction borrows it. */
  val staysDeactivated: NoteKey = notes.key("stays-deactivated", Vector(
    NotePart.Arg(0), NotePart.Text(" stays deactivated.")))
```

- [ ] **Step 4: Add the raw refusals and the waivable check**

In `PowerUseRules.scala`, replace `refusals` with:

```scala
  /** Every refusal `restrictions` give `use`, whatever consent was given:
    * what an automatic use is shaped by, so a grant or a refusal never
    * changes its shape (automatic power consent design, "The
    * `AutomaticUse` node"). */
  def raised(ready: ReadyGame, use: PowerUse,
      restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal] =
    restrictions.flatMap(_.refusal(ready, use))

  /** Every refusal `restrictions` give `use`, less those a grant
    * remembered this turn lifts. */
  def refusals(ready: ReadyGame, use: PowerUse,
      restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal] =
    raised(ready, use, restrictions).filterNot(refusal =>
      refusal.reason.waiver.exists(waiver => ready.consent.answer(
        waiver.covers, waiver.granter, ConsentTurn.of(ready))
        .contains(ConsentAnswer.Granted)))

  /** Whether consent may lift every one of `refusals` of `use`: the use
    * may be asked about, and each refusal names a player other than its
    * user who may waive it (consent design, rulings 1, 2, 4 and 5). */
  def waivable(use: PowerUse, refusals: Vector[PowerUseRefusal]): Boolean =
    use.consent && refusals.forall(_.reason.waiver.exists(
      _.granter.exists(_ != use.user)))
```

- [ ] **Step 5: Add the preview flag**

In `WalkerPowers.scala`, add a last field after `declared`, with its doc comment inside the parameter list as `declared` has:

```scala
    /** Set for a preview or a simulation (automatic power consent design,
      * "Search and previews"): an automatic use that waits on its rulers
      * is walked as if they let it work, so the walk never parks at its
      * step. */
    previewing: Boolean = false):
```

and, after `quiet`:

```scala
  /** These powers for a preview or a simulation ([[previewing]]). It is
    * built once per instance, as [[quiet]] is. */
  lazy val previewed: WalkerPowers =
    if previewing then this else copy(previewing = true)
```

- [ ] **Step 6: Rewrite `AutomaticUses`**

Replace the body of `walker/AutomaticUses.scala` with:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseStep}
import oathdigital.model._

/** How the walker resolves an [[AutomaticUse]] (catalog batch 8, slice
  * 10; automatic power consent design). Every walker path that resolves
  * a `Branch` resolves this node the same way, so a resume addresses the
  * same vector the walk did.
  *
  * A node the power-use restrictions allow, or refuse only waivably, has
  * two children: a slot, then a body. A node refused by a restriction
  * nobody may waive has none, and its use is deactivated unasked.
  *
  *  - Allowed, or waivable with every grant remembered this turn: the
  *    slot does nothing and the body is the effect.
  *  - Waivable with a refusal remembered this turn: the slot says the use
  *    stays deactivated, and the body does nothing.
  *  - Waivable with nothing remembered: the slot is the power-use step
  *    ([[PowerUseStep]]), and the body runs the effect only when the step
  *    was answered `use`. A preview walks the effect instead, as if its
  *    rulers let it work.
  *
  * The verdict reads the raw refusals and ignores the ledger, so a grant
  * or a refusal never changes the node's shape. A resume addresses the
  * slot only when it is the step, and the body only when it is the
  * effect: a resume `inside` the effect runs it unjudged, since a step of
  * the effect that turned the verdict would otherwise empty the vector
  * the parked position points into. */
private[walker] object AutomaticUses:
  /** The body's index. A resume there is inside the effect. */
  private val Body = "1"
  private val unasked: Operation = Sequence(Vector.empty)

  def children(use: AutomaticUse, state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, cursor: Option[Vector[String]])
      : Vector[Operation] =
    val judged = PowerUse.automatic(use.user, use.power, use.card)
    cursor.flatMap(_.headOption) match
      case Some(Body) => Vector(unasked, effect(use))
      case Some(_) => Vector(step(use, judged, state, powers), answered(use))
      case None =>
        val raised = PowerUseRules.raised(state, judged,
          powers.useRestrictions)
        if raised.isEmpty then Vector(unasked, effect(use))
        else if !PowerUseRules.waivable(judged, raised) then Vector.empty
        else remembered(state, raised) match
          case Some(ConsentAnswer.Refused) =>
            Vector(stays(use, state, raised), unasked)
          case Some(ConsentAnswer.Granted) => Vector(unasked, effect(use))
          case None if powers.previewing => Vector(unasked, effect(use))
          case None =>
            Vector(step(use, judged, state, powers), answered(use))

  private def effect(use: AutomaticUse): Operation =
    Branch((now, tree) => use.effect(now, tree))

  /** The effect, when the step was answered `use`. */
  private def answered(use: AutomaticUse): Operation = Branch((now, tree) =>
    if PowerUseStep.answer(tree.answered, use.power)
        .contains(PowerUseStep.use) then use.effect(now, tree)
    else Vector.empty)

  /** The step: `use`, askable of the rulers whose refusals are not yet
    * lifted, or open once each has granted; and `skip`. */
  private def step(use: AutomaticUse, judged: PowerUse, state: ReadyGame,
      powers: WalkerPowers): Decide =
    val left = PowerUseRules.refusals(state, judged, powers.useRestrictions)
    val using = DecisionOption.Button(PowerUseStep.use, "Let it work")
    Decide(PowerUseStep.decisionId(use.power), use.user,
      DecisionQuery.ChooseOne(Vector(
        if left.isEmpty then using
        else DecisionOption.Unavailable(using, PowerUseRules.shown(state,
          use.user, left, consent = judged.consent)),
        DecisionOption.Button(PowerUseStep.skip, "Deactivate"))))

  /** Refused when any refusal's answer this turn is a refusal, granted
    * when every one's is a grant, and `None` while some ruler has not
    * answered. */
  private def remembered(state: ReadyGame,
      refusals: Vector[PowerUseRefusal]): Option[ConsentAnswer] =
    val answers = refusals.map(answerTo(state, _))
    if answers.contains(Some(ConsentAnswer.Refused)) then
      Some(ConsentAnswer.Refused)
    else if answers.forall(_.contains(ConsentAnswer.Granted)) then
      Some(ConsentAnswer.Granted)
    else None

  private def answerTo(state: ReadyGame, refusal: PowerUseRefusal)
      : Option[ConsentAnswer] = refusal.reason.waiver.flatMap(waiver =>
    state.consent.answer(waiver.covers, waiver.granter,
      ConsentTurn.of(state)))

  /** "{card} stays deactivated.", under the first refusing card whose
    * refusal is remembered. */
  private def stays(use: AutomaticUse, state: ReadyGame,
      refusals: Vector[PowerUseRefusal]): Operation =
    refusals.find(answerTo(state, _).contains(ConsentAnswer.Refused))
      .fold(unasked)(refusal => Note(refusal.power, _ => Some(
        PowerUseStep.staysDeactivated(refusal.note.source,
          NoteArg.Card(use.card)))))
```

- [ ] **Step 7: Pass the cursor to every caller**

In `ProcedureWalker.walk`, change the `AutomaticUse` case's last argument to `AutomaticUses.children` from `inside = cursor.nonEmpty` to `cursor`. The line count must not change.

In `WalkerPowerGather.attributed`, change `inside = false` to `cursor = None`.

In `WalkerPowerGather`, give `foldedChildrenAt` a last parameter `remaining: Vector[String]`, the segments from `node` down to the parked leaf. Pass `remaining` from `resolveAt`'s call to it. In its `AutomaticUse` case, call `AutomaticUses.children(use, state, pending.copy(at = path), powers, Some(remaining))`, and replace the case's comment with "Navigation descends into the node toward a park: at its step, or inside its effect."

Add to `WalkerPowerGather`, after `leafAt`:

```scala
  /** The node `pending.at` addresses itself, unnarrowed: what a caller
    * reads when it needs the composite around a parked leaf. */
  def nodeAt(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[Operation] =
    resolveAt(state, pending, powers.withAnswers(pending.answered), action,
      pending.at, Vector.empty, Set.empty)
```

- [ ] **Step 8: Add `ParkedUses`**

Create `src/main/scala/oathdigital/gameplay/walker/ParkedUses.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** The automatic use whose power-use step a walk is parked at (automatic
  * power consent design, "Reaching the step"): the step is the first
  * child of its node, so the node is the step's parent. */
object ParkedUses:
  def of(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[AutomaticUse] =
    if pending.at.isEmpty then None
    else ProcedureWalker.parkedDecide(state, action, pending, powers,
        probing = false)
      .filter(decide => PowerUseStep.isDecision(decide.decisionId))
      .flatMap(_ => WalkerPowerGather.nodeAt(state, action,
        pending.copy(at = pending.at.init), powers))
      .collect { case use: AutomaticUse => use }
```

- [ ] **Step 9: Walk previews with `previewed`**

In `WalkerSimulation.scala`, every `powers` argument passed to `ProcedureWalker.advance`, `ProcedureWalker.resolve` and `ProcedureWalker.restrictionViolations` becomes `powers.previewed`: in `run`, `applies`, `refusal`, `started`, `through` (its `answering`), `preview` and `answer`. Leave the `parkedDecide` calls as they are: they navigate a position, and navigation follows the cursor.

Add to the object's Scaladoc: "Every walk here uses `WalkerPowers.previewed`: an automatic use waiting on its rulers is walked as if they let it work (automatic power consent design, "Search and previews")."

In `ModifierChoice.explained`, change `ProcedureWalker.advance(state, rest, None, powers,` to `ProcedureWalker.advance(state, rest, None, powers.previewed,`.

- [ ] **Step 10: Borrow the note key**

In `CityWall`, after `placingAsked`:

```scala
  /** "{card} stays deactivated." (automatic power consent design). */
  val staysDeactivated: NoteKey = notes.borrow(PowerUseStep.staysDeactivated)
```

Do the same in `SpellBreaker` (after `asked`) and in the `ForestCouncil` companion (after `placingAsked`). Import `oathdigital.gameplay.powerresolver.PowerUseStep` in each, joining the existing `powerresolver` import.

- [ ] **Step 11: Update the `AutomaticUse` Scaladoc**

In `model/Operation.scala`, rewrite the `AutomaticUse` doc comment to say: the walker runs `effect` when no power-use restriction refuses the use; runs nothing when a restriction nobody may waive refuses it; and otherwise asks the refusing cards' rulers through a hidden step first (automatic power consent design). Keep the sentences about `effect` being built live and the node having no static children.

- [ ] **Step 12: Convert the suites that staged a player-ruled refusal**

Slice 10 refused silently when a player ruled the refusing card. Now the walk parks at the step. In `ForestCouncilSuite`:

- "an enemy's Beast When Played power does not fire": give `errandBoyStays` a parameter `ruled: Table => Table` in place of `rulerOfCouncil`. Assert `errandBoyStays(_.bandits(awaySite, 1))`, and keep the ruler's own Roar with `!errandBoyStays(_.warbandsAt(awaySite, p1, 1))`. Rename the test "an enemy's Beast When Played power does not fire under a bandit Forest Council". Task 5 adds the player-ruled case.
- "an enemy's Vow of Beastkin adds no warband, and its source limit still holds": change `.warbandsAt(woods, p2, 1)` to `.bandits(woods, 1)`, and add "under a bandit Forest Council" to the name.

`CityWallSuite`'s "Tyrant arriving at its site, ruled by an enemy, kills nothing" still passes: the walk parks at the step and nothing dies. Task 5 rewrites it.

- [ ] **Step 13: Regenerate the note keys and run the suites**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: the snapshot gains `stays-deactivated` under City Wall, Spell Breaker and Forest Council, and nothing else changes.

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.powers.*"`
Expected: PASS.

Run: `./sbtw "test"`
Expected: PASS. If another suite fails because it stages a player-ruled refusal of an automatic power, convert it as in Step 12: under bandits now, with a step case left to Task 5. Name each converted suite in the commit message.

- [ ] **Step 14: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/PowerUseStep.scala \
  src/main/scala/oathdigital/gameplay/walker/ParkedUses.scala \
  src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala \
  src/main/scala/oathdigital/gameplay/walker/AutomaticUses.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala \
  src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala \
  src/main/scala/oathdigital/gameplay/powers/poweruse/SpellBreaker.scala \
  src/main/scala/oathdigital/gameplay/powers/poweruse/ForestCouncil.scala \
  src/main/scala/oathdigital/model/Operation.scala \
  src/test/scala/oathdigital/gameplay/walker/AutomaticUseSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala \
  src/test/resources/notes/declared-keys.txt
git commit -m "feat(batch8): a waivably refused automatic power waits at its step"
```

---

### Task 2: The walk opens the request, and the answer settles the step

**Files:**
- Modify: `src/main/scala/oathdigital/model/Consent.scala` (`ConsentUse`, `ConsentRequest.use`)
- Modify: `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`powerUseStep`)
- Modify: `src/main/scala/oathdigital/application/ConsentDesk.scala` (`opened`, `settled`, `answer`)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (`handleAgainst`)
- Create: `src/test/scala/oathdigital/application/AutomaticUseConsentServiceSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala`

**Interfaces:**
- Consumes: `PowerUseStep.{use, skip}`, `ParkedUses.of` (Task 1); `OathRulesWalker.consentAsk`, `.resolveWalker`, `.grantAnswers`; `ConsentDesk.automatic`.
- Produces: `ConsentUse(card, issuer)`, `ConsentRequest.use`, `OathRulesWalker.powerUseStep(state)`, `ConsentDesk.opened(state, issuer)`. Task 3 calls `powerUseStep` and `opened`; Task 4 reads `ConsentRequest.use`.

- [ ] **Step 1: Write the failing service tests**

Create `AutomaticUseConsentServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.gameplay.powers.poweruse.CityWall
import oathdigital.gameplay.powers.travel.{TravelFixture, Tyrant, TyrantCard}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** City Wall's ruler decides whether an enemy's Tyrant works, through the
  * service (automatic power consent design, user story and rulings 2, 5
  * and 6). */
class AutomaticUseConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val wall = CatalogNames.denizen("City Wall")
  private val coast = TravelFixture.coast

  /** The key Tyrant's use by p1 is remembered under, against City Wall. */
  private val tyrantKey = ConsentKey(CityWall.id, p1, PowerUseRules.useKey,
    DecisionOptionRef.Denizen(TyrantCard.id))

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant").denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))

  /** p1 travels to `site` at `sequence`; returns the pick's batch. */
  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice, granter: PlayerId = p2)(using munit.Location)
      : GameAccepted = accepted(service.handle(gameId, at.nextSequence,
    GameCommand.AnswerConsent(granter, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  private def coastWarbands(at: GameAccepted)(using munit.Location): Int =
    Look(readyAfter(at)).forces(coast) match
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0

  private def remembered(at: GameAccepted)(using munit.Location)
      : Option[ConsentAnswer] = readyAfter(at).consent.remembered(tyrantKey,
    ConsentTurn.of(readyAfter(at)))

  test("a Travel to City Wall's site asks its ruler whether Tyrant works, " +
      "and asks the traveller nothing"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assertEquals(readyAfter(waiting).consent.pending, Some(ConsentRequest(
      p1, PowerUseStep.decisionId(Tyrant.id), PowerUseStep.use,
      Vector(ConsentAsk(p2, Vector(CityWall.id))), Vector(tyrantKey),
      use = Some(ConsentUse(TyrantCard.id, p1)))))
    assertEquals(Look(readyAfter(waiting)).pawn(p1), coast)
    assertEquals(coastWarbands(waiting), 2)

  test("Deactivate kills nothing, and the refusal is remembered"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    assertEquals(readyAfter(no).consent.pending, None)
    assertEquals(coastWarbands(no), 2)
    assertEquals(remembered(no), Some(ConsentAnswer.Refused))
    assert(!readyAfter(no).game.current.walkerProcedure
      .contains(ActionRef.Travel))

  test("Let it work kills one warband, and the grant is remembered"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val yes = answering(service, travelling(service, 0L, coast),
      ConsentChoice.Yes)
    assertEquals(coastWarbands(yes), 1)
    assertEquals(remembered(yes), Some(ConsentAnswer.Granted))

  test("Always deactivate and Always let it work also set the ruler's " +
      "preference for the traveller"):
    for (choice, setting, warbands) <- Vector(
        (ConsentChoice.Never, ConsentPreference.Never, 2),
        (ConsentChoice.Always, ConsentPreference.Always, 1)) do
      val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
      val answered = answering(service, travelling(service, 0L, coast),
        choice)
      assertEquals(readyAfter(answered).consent.preference(p2, CityWall.id,
        p1), Some(setting))
      assertEquals(coastWarbands(answered), warbands)

  test("a preference answers the request in the command that opens it"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val set = accepted(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, CityWall.id, p1,
        ConsentSetting.Never)))
    val travelled = travelling(service, set.nextSequence, coast)
    assertEquals(readyAfter(travelled).consent.pending, None)
    assertEquals(coastWarbands(travelled), 2)

  test("a deactivation remembered this turn applies to a later Travel " +
      "there without asking"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    val away = travelling(service, no.nextSequence,
      TravelFixture.cradlePlains)
    val back = travelling(service, away.nextSequence, coast)
    assertEquals(readyAfter(back).consent.pending, None)
    assertEquals(coastWarbands(back), 2)

  test("a grant settling a Narrow Pass ask resumes into Tyrant's step, " +
      "which asks its own ruler"):
    // The Pass, ruled by p2, blocks Dunes; City Wall stands at Dunes,
    // which p3 rules.
    val dunes = TravelFixture.provincePlains
    val ready = TravelFixture.on(ConsentFixture.passRuledByP2)(_
      .adviser(p1, "Tyrant").denizen(wall, at = dunes)
      .warbandsAt(dunes, p3, 1))
    val (service, _) = ConsentFixture.service(ready)
    val started = ConsentFixture.startTravel(service)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      ConsentFixture.requestDunes))
    val granted = answering(service, asked, ConsentChoice.Yes)
    val request = present(readyAfter(granted).consent.pending,
      "Tyrant's request")
    assertEquals(request.asks, Vector(ConsentAsk(p3, Vector(CityWall.id))))
    assertEquals(request.use, Some(ConsentUse(TyrantCard.id, p1)))
    assertEquals(Look(readyAfter(granted)).pawn(p1), dunes)
```

In `ConsentEventWireSuite`, add a round-trip test for a `ConsentRequested` whose request sets `use = Some(ConsentUse(DenizenId("111"), PlayerId("p1")))`, following the suite's existing round-trip test for `covers`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.AutomaticUseConsentServiceSuite oathdigital.serialization.ConsentEventWireSuite"`
Expected: it fails to compile on `ConsentUse` and `ConsentRequest.use`.

- [ ] **Step 3: Add `ConsentUse` and the request's `use`**

In `model/Consent.scala`, before `ConsentRequest`:

```scala
/** The automatic power a request is about (automatic power consent
  * design): the used card, and the player whose batch walked to its
  * step, who opens the answer's batch and may withdraw. */
final case class ConsentUse(card: CardId, issuer: PlayerId)
```

Give `ConsentRequest` a last field `use: Option[ConsentUse] = None`. Add to its Scaladoc (it has none yet; write one): "`use` is set when the walk opened the request at an automatic power's step: its decision is the step, its target the step's `use`, and its answer answers the step."

- [ ] **Step 4: Encode and decode it**

In `ConsentEventCodec`, after the `covers` block of the encoder:

```scala
        // Written only when set (catalog batch 8, item 10b).
        request.use.foreach(use => encoded("use") = ujson.Obj(
          "card" -> encodeCardId(use.card), "issuer" -> use.issuer.value))
```

In the decoder, add beside `covers`:

```scala
    def use: Either[WireError, Option[ConsentUse]] =
      payload.obj.get("use").fold[Either[WireError, Option[ConsentUse]]](
        Right(None))(raw => decodeCardId(raw("card"), s"$path.use.card")
        .map(card => Some(ConsentUse(card, PlayerId(raw("issuer").str)))))
```

and pass `used` (from `used <- use` in the `for`) as the request's last argument. Match `decodeCardId`'s exact signature in `GameEventJsonSupport`.

- [ ] **Step 5: Expose the parked use to the application**

In `OathRulesWalker`, after `grantAnswers`, and import `ParkedUses` from `gameplay.walker`:

```scala
  /** The automatic use whose power-use step the walk is parked at, if it
    * is (automatic power consent design, "Reaching the step"). */
  def powerUseStep(state: OathState): Option[AutomaticUse] =
    walkerResumeContext(state).toOption.flatMap {
      case (ready, _, tree, pending, powers, _) =>
        walkerCall(Right(ParkedUses.of(ready, tree, pending, powers)))
          .toOption.flatten
    }
```

- [ ] **Step 6: Open, settle and answer in the desk**

In `ConsentDesk.scala`, import `oathdigital.gameplay.powerresolver.PowerUseStep`.

Add after `request`:

```scala
  /** The request a walk parked at an automatic power's step opens
    * (automatic power consent design, "Reaching the step"), with the
    * answers the granters' preferences give and what they settle.
    * `issuer` opened the batch that walked there. Nothing when the walk is
    * not parked at a step, or a request already waits. */
  def opened(state: OathState, issuer: PlayerId): Result =
    rules.powerUseStep(state).filter(_ => pending(state).isEmpty) match
      case None => Right(Vector.empty)
      case Some(use) => for
        ready <- playing(state)
        decisionId = PowerUseStep.decisionId(use.power)
        ask <- rules.consentAsk(state, use.user, decisionId, PowerUseStep.use)
        request = ConsentRequest(use.user, decisionId, PowerUseStep.use,
          ask.asks, ask.covers, Some(ConsentUse(use.card, issuer)))
        events <- settled(state,
          ConsentRequested(request) +: automatic(ready.consent, request))
      yield events
```

Replace `settled` with a version that also answers a step, and resumes into the next one:

```scala
  /** `events`, followed by what their last answer settles.
    *
    *  - A request about an automatic power, settled either way: `first`,
    *    then the issuer's batch answering the step `use` on a grant or
    *    `skip` on a refusal (automatic power consent design, "Settling").
    *  - Any other request, granted at a choose-one: the requester's answer
    *    to the parked decision. That answer is the requester's command
    *    batch, though the granter's command produced it, so the requester
    *    can undo the move (consent design, "Settling"). At a choose-many,
    *    the grant only opens the option, and the requester goes on
    *    choosing (slice 2). `first` follows everything.
    *
    * A resumed walk that parks at another step opens its request. */
  private def settled(state: OathState, events: Vector[OathEvent],
      first: Vector[OathEvent] = Vector.empty): Result =
    folded(state, events).flatMap { after =>
      val request = events.collectFirst { case ConsentRequested(opened) =>
        opened }.orElse(pending(state))
      val last = events.lastOption.collect {
        case ConsentAnswered(_, granted) => granted }
      request.filter(_ => pending(after).isEmpty).zip(last) match
        case Some((done, granted)) if done.use.nonEmpty =>
          val issuer = done.use.fold(done.requester)(_.issuer)
          folded(after, first).flatMap(prepared => resumed(prepared, done,
            if granted then PowerUseStep.use else PowerUseStep.skip, issuer))
            .map(events ++ first ++ _)
        case Some((done, true)) if rules.grantAnswers(after, done.requester,
            done.decisionId) =>
          resumed(after, done, done.target, done.requester)
            .map(events ++ _ ++ first)
        case _ => Right(events ++ first)
    }

  /** `issuer`'s batch answering `done`'s parked decision with `answer` as
    * its requester, then the request of any step the walk parks at next. */
  private def resumed(state: OathState, done: ConsentRequest,
      answer: DecisionOptionRef, issuer: PlayerId): Result = for
    answered <- rules.resolveWalker(state, done.requester, done.decisionId,
      DecisionAnswer.ChooseOneAnswer(answer))
    next <- opened(answered.state, issuer)
  yield (CommandAccepted(issuer) +: answered.events) ++ next
```

In `answer`, set a request about an automatic power's preferences before its step is answered, so a second step reached in the same command is answered by them (departure 8):

```scala
    events <- settled(state, Vector(ConsentAnswered(granter,
      choice.granted)), preferences)
  yield events
```

Update `answer`'s Scaladoc: the preferences follow the answer and anything it settles; for a request about an automatic power, they come before the step's answer.

Update the class Scaladoc's "Consent events open no command batch, so nothing here is undoable": add "except the batch that answers a parked decision, which a settled request opens for its requester, or for the issuer of an automatic power's request."

- [ ] **Step 7: Open the request after every command**

In `GameApplicationService.handleAgainst`, open the request a walk parked at, in the same append:

```scala
    for
      transition <- applyCommand(journal.state, command, nextSequence)
        .left.map(CommandRejected.apply)
      issuer = GameCommand.issuer(command)
      opened <- issuer.fold[Either[OathViolation, Vector[OathEvent]]](
          Right(Vector.empty))(consent.opened(transition.state, _))
        .left.map(CommandRejected.apply)
      appended = issuer.map(OathEvent.CommandAccepted(_)).toVector ++
        transition.events ++ opened
      accepted <- appendBatch(gameId, journal, appended,
        transition.events ++ opened.filterNot(
          _.isInstanceOf[OathEvent.CommandAccepted]),
        expected, nextSequence)
    yield accepted
```

Add to its Scaladoc (write one if there is none): "A walk that parks at an automatic power's step opens its request in the same append (automatic power consent design, "Reaching the step")."

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.AutomaticUseConsentServiceSuite oathdigital.serialization.ConsentEventWireSuite oathdigital.application.*Consent*"`
Expected: PASS.

- [ ] **Step 9: Run the server suite**

Run: `./sbtw "test"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/Consent.scala \
  src/main/scala/oathdigital/serialization/ConsentEventCodec.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/application/ConsentDesk.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/test/scala/oathdigital/application/AutomaticUseConsentServiceSuite.scala \
  src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala
git commit -m "feat(batch8): the refusing card's ruler decides whether an automatic power works"
```

---

### Task 3: Undo coupling

**Files:**
- Create: `src/main/scala/oathdigital/application/UndoCoupling.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (`routed`, `takeBack`, `restore`, `appendBatch`, `loadedFrom`, a new `withdrawUse`)
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:47-61` (the undo offer)
- Create: `src/test/scala/oathdigital/application/AutomaticUseUndoSuite.scala`

**Interfaces:**
- Consumes: `OathRulesWalker.powerUseStep`, `ConsentDesk.opened` (Task 2); `FoldedJournal.live` and `.redo`; `CommandBatch.before` and `.after`.
- Produces: `UndoCoupling.undoing(journal): Option[Vector[CommandBatch]]` (latest first) and `UndoCoupling.redoing(journal): Option[Vector[CommandBatch]]` (earliest first).

- [ ] **Step 1: Write the failing undo tests**

Create `AutomaticUseUndoSuite.scala`. Each suite keeps its own fixtures, as the consent suites do, so the board and the helpers repeat Task 2's:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.gameplay.powers.travel.{TravelFixture, TyrantCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Undo and Redo around an automatic power's request (automatic power
  * consent design, "Undo coupling" and ruling 9). */
class AutomaticUseUndoSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val wall = CatalogNames.denizen("City Wall")
  private val coast = TravelFixture.coast
  private val home = TravelFixture.cradlePlains

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant").denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))

  /** p1 travels to `site` at `sequence`; returns the pick's batch. */
  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  private def coastWarbands(at: GameAccepted)(using munit.Location): Int =
    Look(readyAfter(at)).forces(coast) match
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0

  private def undoing(service: GameApplicationService, at: GameAccepted,
      actor: PlayerId = p1)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence, GameCommand.Undo(actor)))

  private def redoing(service: GameApplicationService, at: GameAccepted)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence, GameCommand.Redo(p1)))

  private def parkedOnTheDestination(at: GameAccepted)(
      using munit.Location): Boolean =
    TravelFixture.parkedDecision(at.state).decisionId ==
      TravelProcedure.destinationDecisionId

  test("the traveller's Undo while the ruler decides withdraws the request " +
      "and takes back the Travel"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val undone = undoing(service, travelling(service, 0L, coast))
    assertEquals(readyAfter(undone).consent.pending, None)
    assertEquals(readyAfter(undone).consent.answers, Map.empty)
    assertEquals(Look(readyAfter(undone)).pawn(p1), home)
    assert(parkedOnTheDestination(undone))

  test("nobody else may withdraw it"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p2)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))

  test("while the ruler decides, the traveller is offered Undo and the " +
      "ruler is not"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assert(projector.project(gameId, waiting.loaded, p1).undo.nonEmpty)
    assertEquals(projector.project(gameId, waiting.loaded, p2).undo, None)

  test("one Undo after either answer takes back the answer and the " +
      "Travel, and the answer stays remembered"):
    for choice <- Vector(ConsentChoice.Yes, ConsentChoice.No) do
      val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
      val waiting = travelling(service, 0L, coast)
      val answered = answering(service, waiting, choice)
      assertEquals(projector.project(gameId, answered.loaded, p1).undo,
        projector.project(gameId, waiting.loaded, p1).undo)
      val undone = undoing(service, answered)
      assertEquals(Look(readyAfter(undone)).pawn(p1), home)
      assertEquals(coastWarbands(undone), 2)
      assert(parkedOnTheDestination(undone))
      assertEquals(readyAfter(undone).consent.answers.values.map(_.answer)
        .toVector, Vector(if choice.granted then ConsentAnswer.Granted
          else ConsentAnswer.Refused))

  test("Redo restores the Travel and the answer"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val undone = undoing(service, answering(service,
      travelling(service, 0L, coast), ConsentChoice.Yes))
    val redone = redoing(service, undone)
    assertEquals(Look(readyAfter(redone)).pawn(p1), coast)
    assertEquals(coastWarbands(redone), 1)
    assertEquals(readyAfter(redone).consent.pending, None)

  test("Redo after a withdrawal asks the ruler again"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    val redone = redoing(service, undoing(service, waiting))
    assertEquals(readyAfter(redone).consent.pending,
      readyAfter(waiting).consent.pending)

  test("an off-turn Travel's request is withdrawn by the player whose " +
      "command walked there, not by its traveller"):
    // p1 holds Palanquin and sends p3, who holds Tyrant, to the coast.
    val sent = TravelFixture.on(TravelFixture.board())(_
      .adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, home).adviser(p3, "Tyrant")
      .denizen(wall, at = coast).warbandsAt(coast, p2, 2))
    val (service, _) = ConsentFixture.service(sent)
    val used = accepted(service.handle(gameId, 0L, MenuPlay.starting(p1,
      ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id)))))
    val aimed = accepted(service.handle(gameId, used.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Palanquin.targetDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(p3))))))
    val waiting = accepted(service.handle(gameId, aimed.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        Palanquin.destinationDecisionId, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Site(coast))))))
    val request = present(readyAfter(waiting).consent.pending, "the request")
    assertEquals((request.requester, request.use),
      (p3, Some(ConsentUse(TyrantCard.id, p1))))
    assertEquals(projector.project(gameId, waiting.loaded, p3).undo, None)
    assert(projector.project(gameId, waiting.loaded, p1).undo.nonEmpty)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p3)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))
    val undone = undoing(service, waiting, actor = p1)
    assertEquals(readyAfter(undone).consent.pending, None)
    assertEquals(Look(readyAfter(undone)).pawn(p3), home)
```

`ConsentState.answers` is keyed by `ConsentKey`; if the ledger holds other keys in this board, filter to `tyrantKey` first.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.AutomaticUseUndoSuite"`
Expected: FAIL. The waiting Undo is answered by the plain withdrawal, which leaves the walk parked at the step, and the answered Undo takes back only the answer's batch.

- [ ] **Step 3: Add `UndoCoupling`**

Create `src/main/scala/oathdigital/application/UndoCoupling.scala`:

```scala
package oathdigital.application

import oathdigital.model.OathState

/** The batches one Undo or one Redo moves (automatic power consent
  * design, "Undo coupling"). Neither stops at a position parked at an
  * automatic power's unanswered step, which nobody would answer: the
  * batch that answered a step goes back with the batch that walked to it.
  * `parks` says whether a state is parked at such a step. */
private[application] final class UndoCoupling(parks: OathState => Boolean):
  /** The live batches one Undo takes back, latest first: the latest, and
    * while the state before it is parked at a step, the one before. `None`
    * when there is nothing to undo, or when the chain needs a batch a
    * barrier keeps off the stack. */
  def undoing(journal: FoldedJournal): Option[Vector[CommandBatch]] =
    def from(live: List[CommandBatch]): Option[Vector[CommandBatch]] =
      live match
        case top :: rest =>
          if parks(top.before) then from(rest).map(top +: _)
          else Some(Vector(top))
        case Nil => None
    from(journal.live)

  /** The taken-back batches one Redo restores, earliest first: the latest
    * taken back, and while the state after it is parked at a step, the
    * next. A chain that ends parked at a step leaves its request to be
    * asked again. */
  def redoing(journal: FoldedJournal): Option[Vector[CommandBatch]] =
    def from(redo: List[CommandBatch]): Vector[CommandBatch] = redo match
      case top :: rest if parks(top.after) && rest.nonEmpty =>
        top +: from(rest)
      case top :: _ => Vector(top)
      case Nil => Vector.empty
    Some(from(journal.redo)).filter(_.nonEmpty)
```

- [ ] **Step 4: Couple Undo, Redo and the offered steps in the service**

In `GameApplicationService`, after `consent`:

```scala
  private val coupling = new UndoCoupling(state =>
    rules.powerUseStep(state).nonEmpty)
```

In `routed`, the Undo case becomes:

```scala
      // The requester's Undo withdraws an unanswered request (ruling 14).
      // The issuer's Undo withdraws an automatic power's request and takes
      // back the batch that walked to its step (automatic power consent
      // design, ruling 9).
      case GameCommand.Undo(actor) => consent.pending(journal.state) match
        case Some(request) if request.use.nonEmpty =>
          withdrawUse(gameId, journal, request, actor, expected, nextSequence)
        case Some(_) => consentBatch(gameId, journal,
          consent.withdraw(journal.state, actor), expected, nextSequence)
        case None => takeBack(gameId, journal, actor, expected, nextSequence)
```

Add:

```scala
  /** Withdraws `request`, about an automatic power, and takes back what
    * walked to its step, in one append. Only that batch's issuer may, and
    * not when a barrier keeps it off the stack. */
  private def withdrawUse(gameId: String, journal: FoldedJournal,
      request: ConsentRequest, actor: PlayerId, expected: ExpectedStream,
      nextSequence: Long): Either[GameApplicationError, GameAccepted] =
    coupling.undoing(journal).filter(_.forall(_.issuer == actor)) match
      case None => Left(CommandRejected(OathViolation.ConsentPending))
      case Some(chain) =>
        val events = OathEvent.ConsentRequestWithdrawn(request.requester) +:
          chain.map(batch =>
            OathEvent.StepTakenBack(batch.from, batch.until, actor))
        appendBatch(gameId, journal, events, events, expected, nextSequence)
```

Replace `takeBack`'s body:

```scala
    for
      chain <- coupling.undoing(journal).toRight(
        CommandRejected(OathViolation.NothingToUndo))
      _ <- chain.find(_.issuer != actor).fold[Either[GameApplicationError,
        Unit]](Right(()))(batch => Left(CommandRejected(
          OathViolation.UndoNotIssuer(actor, batch.issuer))))
      markers = chain.map(batch =>
        OathEvent.StepTakenBack(batch.from, batch.until, actor))
      accepted <- appendBatch(gameId, journal, markers, markers, expected,
        nextSequence)
    yield accepted
```

Replace `restore`'s body:

```scala
    for
      chain <- coupling.redoing(journal).toRight(
        CommandRejected(OathViolation.NothingToRedo))
      _ <- chain.find(_.issuer != actor).fold[Either[GameApplicationError,
        Unit]](Right(()))(batch => Left(CommandRejected(
          OathViolation.RedoNotIssuer(actor, batch.issuer))))
      markers = chain.map(batch =>
        OathEvent.StepRestored(batch.from, batch.until, actor))
      restored <- extended(journal, markers, nextSequence)
      reopened <- consent.opened(restored.state, actor)
        .left.map(CommandRejected.apply)
      events = markers ++ reopened
      accepted <- appendBatch(gameId, journal, events, events.filterNot(
        _.isInstanceOf[OathEvent.CommandAccepted]), expected, nextSequence)
    yield accepted
```

Update both Scaladocs: they take back, or restore, a coupled chain (`UndoCoupling`), and a Redo that ends at an automatic power's step asks its rulers again.

Add the offered steps, and use them in `loadedFrom` and in `appendBatch`'s `GameAccepted` in place of `journal.undoable.map(offered(journal, _))` and `journal.redoable.map(offered(journal, _))`:

```scala
  /** The Undo a journal offers: its chain's issuer and whole range, named
    * by the batch at its bottom, which walked to any step. */
  private def offeredUndo(journal: FoldedJournal): Option[UndoStep] =
    coupling.undoing(journal).map(chain =>
      offered(journal, chain.last).copy(until = chain.head.until))

  /** The Redo a journal offers, named by the chain's first batch. */
  private def offeredRedo(journal: FoldedJournal): Option[UndoStep] =
    coupling.redoing(journal).map(chain =>
      offered(journal, chain.head).copy(until = chain.last.until))
```

Run `wc -l` on the service; it must stay at or under 800 lines.

- [ ] **Step 5: Offer Undo to the issuer while an automatic power's request waits**

In `GameProjection.projectFor`, replace the undo offer:

```scala
      // While a request waits, its requester's Undo withdraws it, and nobody
      // else undoes or redoes past it (consent design, plan decision 7). An
      // automatic power's request is withdrawn by the issuer of the batch
      // that walked to its step, whose Undo takes that batch back too
      // (automatic power consent design, ruling 9).
      val walked = waiting.exists(_.use.nonEmpty)
      val withdraws = !walked && waiting.exists(request =>
        requestingPlayer.contains(request.requester))
      consent.applied(context, readyProjection(gameId, loaded.nextSequence,
        context).copy(
        undo = if withdraws then
            Some(StepOfferProjection(ConsentProjector.withdrawNote))
          else if walked then offer(loaded.undo, requestingPlayer, "Undo")
          else if waiting.nonEmpty then None
          else offer(loaded.undo, requestingPlayer, "Undo"),
```

Keep the `redo` and `logRevision` lines as they are.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.*"`
Expected: PASS, the existing undo suites included.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/UndoCoupling.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/main/scala/oathdigital/application/GameProjection.scala \
  src/test/scala/oathdigital/application/AutomaticUseUndoSuite.scala
git commit -m "feat(batch8): undo takes an automatic power's answer back with the step it settled"
```

---

### Task 4: Wording: the prompt, the waiting line and the Game Log

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/ConsentProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ConsentLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:209-210` (`crossCutting`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogAutomaticUseSuite.scala`
- Test: `src/test/scala/oathdigital/application/ConsentProjectionSuite.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala`

**Interfaces:**
- Consumes: `ConsentRequest.use`, `ConsentUse.card` (Task 2); `PowerUseStep.isDecision` (Task 1).
- Produces: `ConsentPromptProjection.use` and `ConsentWaitingProjection.use`, each the used card's name.

- [ ] **Step 1: Write the failing projection and log tests**

In `ConsentProjectionSuite`, import `oathdigital.gameplay.powers.poweruse.CityWall` and `oathdigital.gameplay.powers.travel.TravelFixture`, and add:

```scala
  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant")
      .denizen(CatalogNames.denizen("City Wall"), at = TravelFixture.coast)
      .warbandsAt(TravelFixture.coast, p2, 2))

  /** p1 travels to the walled coast at `sequence`; the pick's batch. */
  private def travelledToTheWalledCoast(service: GameApplicationService,
      sequence: Long)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1,
      TravelFixture.coast).picked
```

then:

```scala
  test("City Wall's ruler is asked whether to deactivate the traveller's " +
      "Tyrant, and the traveller waits on that decision"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelledToTheWalledCoast(service, 0L)
    val prompt = present(view(waiting, p2).consentPrompt, "p2's prompt")
    assertEquals((prompt.requesterPlayerId, prompt.powers, prompt.use),
      ("p1", Vector("City Wall"), Some("Tyrant")))
    assertEquals(view(waiting, p1).consentWaiting, Some(
      ConsentWaitingProjection(Vector("p2"), use = Some("Tyrant"))))

  test("the traveller's view cannot tell an automatic deactivation, or " +
      "an automatic grant, from a manual one"):
    for (choice, setting) <- Vector(
        (ConsentChoice.No, ConsentSetting.Never),
        (ConsentChoice.Yes, ConsentSetting.Always)) do
      val (manualService, _) = ConsentFixture.service(
        tyrantBesideAWalledCoast)
      val manual = accepted(manualService.handle(gameId,
        travelledToTheWalledCoast(manualService, 0L).nextSequence,
        GameCommand.AnswerConsent(p2, choice)))
      val (autoService, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
      val set = accepted(autoService.handle(gameId, 0L,
        GameCommand.SetConsentPreference(p2, CityWall.id, p1, setting)))
      val automatic = travelledToTheWalledCoast(autoService,
        set.nextSequence)
      assertEquals(view(automatic, p1).copy(nextSequence = 0L),
        view(manual, p1).copy(nextSequence = 0L))
```

Create `GameLogAutomaticUseSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{ConsentFixture, GameAccepted,
  GameApplicationService, GameCommand, ParkedServiceFixture}
import oathdigital.gameplay.powers.travel.TravelFixture
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.{formatter, texts}

/** The Game Log of City Wall's ruler deciding on an enemy's Tyrant
  * (automatic power consent design, "Game Log"), as p1 reads it. */
class GameLogAutomaticUseSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val coast = TravelFixture.coast

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant")
      .denizen(CatalogNames.denizen("City Wall"), at = coast)
      .warbandsAt(coast, p2, 2))

  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def p1sLog(service: GameApplicationService)(
      using munit.Location): Vector[String] =
    texts(formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(p1)))

  /** p1's log once p1 travelled to the walled coast and p2 answered. */
  private def travelledAndAnswered(choice: ConsentChoice)(
      using munit.Location): Vector[String] =
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    answering(service, travelling(service, 0L, coast), choice)
    p1sLog(service)

  /** p1's log once p2 deactivated Tyrant at the coast and p1 travelled
    * away and back. */
  private def deactivatedThenTravelledBack()(using munit.Location)
      : Vector[String] =
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    val away = travelling(service, no.nextSequence,
      TravelFixture.cradlePlains)
    travelling(service, away.nextSequence, coast)
    p1sLog(service)

  test("the request and a deactivation read as the ruler's decision"):
    val lines = travelledAndAnswered(ConsentChoice.No)
    assert(lines.contains("City Wall: p2 decides whether Tyrant works."),
      lines)
    assert(lines.contains("p2 deactivated Tyrant with City Wall."), lines)
    assert(!lines.exists(_.startsWith("Chose")), lines)

  test("a grant reads as the ruler letting Tyrant work, then Tyrant's own " +
      "line"):
    val lines = travelledAndAnswered(ConsentChoice.Yes)
    val let = lines.indexOf("p2 let Tyrant work.")
    assert(let >= 0, lines)
    assert(lines.drop(let).exists(_.startsWith("Tyrant: Killed 1 p2 " +
      "warband at ")), lines)

  test("a deactivation remembered this turn says so when it applies"):
    val lines = deactivatedThenTravelledBack()
    assert(lines.contains("City Wall: Tyrant stays deactivated."), lines)
    assertEquals(lines.count(_ == "City Wall: p2 decides whether Tyrant " +
      "works."), 1)
```

A Travel writes no "Chose" line of its own: its destination is narrated, and its modifiers are cross-cutting. So the first test's last check catches a "Chose" line for the step's answer.

In `ProjectionProtocolSuite`, extend the existing round trip (line 93) with `use = Some("Tyrant")` on both the waiting and the prompt projections.

In `ConsentPromptPanelSuite`, add two states beside `prompted` and `waiting`:

```scala
  /** Red rules City Wall, and decides on Blue's Tyrant. */
  private val deactivating = table.copy(
    walkerWaiting = Some(WalkerWaitingProjection("blue", None)),
    consentPrompt = Some(ConsentPromptState("blue", Vector("City Wall"),
      "Let it work", use = Some("Tyrant"))))
  /** Blue waits on Red's decision on Tyrant. */
  private val deciding = table.copy(consentWaiting = Some(
    ConsentWaitingState(Vector("red"), use = Some("Tyrant"))))
```

then:

```scala
  test("the ruler is asked whether to deactivate the power, with four " +
      "answers in its own words"):
    val panel = pane(deactivating, "red", new RecordingControls())
    assertEquals(panel.querySelector(".consent-question").textContent,
      "Use City Wall to deactivate Blue's Tyrant?")
    assertEquals(all(panel, ".consent-answer").map(_.textContent),
      Vector("Always deactivate", "Deactivate", "Let it work",
        "Always let it work"))

  test("its answers send never, no, yes and always"):
    val ui = new RecordingControls()
    val panel = pane(deactivating, "red", ui)
    all(panel, ".consent-answer").foreach(_.asInstanceOf[dom.html.Button]
      .click())
    assertEquals(ui.submitted, Vector("never", "no", "yes", "always")
      .map(Intent.AnswerConsent(_)))

  test("the user waits on the ruler's decision on the power"):
    val panel = pane(deciding, "blue", new RecordingControls())
    assert(panel.textContent.contains("Waiting for Red's decision on " +
      "Tyrant…"), panel.textContent)
```

Match the existing waiting test's way of reading the waiting line if it reads a specific class.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.ConsentProjectionSuite oathdigital.application.gamelog.GameLogAutomaticUseSuite" "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite" "frontend/testOnly oathdigital.frontend.ConsentPromptPanelSuite"`
Expected: it fails to compile on the `use` fields. Use the shared project's name as `build.sbt` declares it.

- [ ] **Step 3: Add `use` to the projections**

In `ConsentProjectionDtos.scala`, give `ConsentWaitingProjection` and `ConsentPromptProjection` a last field `use: Option[String] = None`: "the card whose automatic power the ruler decides on (catalog batch 8, item 10b)". Update both Scaladocs.

In `ConsentProjectionCodec`, write `use` only when set (`value.use.foreach(row("use") = _)`), add `"use"` to both `exact` sets, and read it with `optionalAbsent(row, "use", path)(string)`.

- [ ] **Step 4: Project the card's name**

In `ConsentProjector.applied`, pass `waiting.use.map(use => cardName(use.card))` as the prompt's `use`, and the same for the waiting projection's `use`. Add:

```scala
  /** A used card's name, as the panels write it. */
  private def cardName(card: CardId): String =
    presentation.cardDetails(card, None, hidden = false).name
```

- [ ] **Step 5: Word the log lines**

In `ConsentLines.lines`, pull `named`'s per-id mapping into a local `def source(id: PowerId): Vector[LogSpan]`, keep `named` as `LogWords.join(ids.map(source))`, and add:

```scala
      // "City Wall, Forest Council: ", the refusing cards of an automatic
      // power's request (catalog batch 8, item 10b).
      def sources(ids: Vector[PowerId]): Vector[LogSpan] =
        ids.map(source).reduceLeftOption((done, next) =>
          (done :+ Text(", ")) ++ next).getOrElse(Vector.empty)
      def used(request: ConsentRequest): Vector[LogSpan] =
        request.use.toVector.flatMap(use => words.source(
          PowerSourceRef.Card(use.card), Vector(before), viewer))
```

Add a case before the existing `ConsentRequested` case:

```scala
        // "{City Wall}: {Red} decides whether {Tyrant} works."
        case ConsentRequested(request) if request.use.nonEmpty =>
          val granters = request.asks.map(_.granter)
          line(((sources(request.asks.flatMap(_.powers).distinct) :+
            Text(": ")) ++ LogWords.join(granters.map(granter =>
              Vector(words.player(granter)))) ++ (Text(
                if granters.size == 1 then " decides whether "
                else " decide whether ") +: used(request))) :+
            Text(" works."))
```

Replace the `ConsentAnswered` case:

```scala
        case ConsentAnswered(granter, granted) =>
          before.consent.pending.filter(_.use.nonEmpty) match
            // "{Red} let {Tyrant} work." or "{Red} deactivated {Tyrant}
            // with {City Wall}."
            case Some(request) => line(
              if granted then ((words.player(granter) +: Text(" let ") +:
                used(request)) :+ Text(" work."))
              else ((words.player(granter) +: Text(" deactivated ") +:
                used(request)) ++ (Text(" with ") +: named(request.asks
                  .filter(_.granter == granter).flatMap(_.powers)))) :+
                Text("."))
            case None => line(Vector(words.player(granter),
              Text(if granted then " gave consent."
                else " gave no consent.")))
```

Update the class Scaladoc: an automatic power's request names its refusing cards and granters, and its answers say whether the power worked.

In `DetailLines.crossCutting`, add `|| PowerUseStep.isDecision(decisionId)`, importing `oathdigital.gameplay.powerresolver.PowerUseStep`, so the step's answer posts no "Chose" line: the consent lines tell it.

- [ ] **Step 6: Word the client's prompt and waiting line**

In `ConsentPanels.scala`:

```scala
  /** An automatic power's answers (catalog batch 8, item 10b). */
  private val deciding = Vector("Always deactivate" -> "never",
    "Deactivate" -> "no", "Let it work" -> "yes",
    "Always let it work" -> "always")
```

In `prompt`, before the `paying` branch: `asked.use` set reads `s"Use $powers to deactivate $requester's $card?"`. In `waiting`, `wait.use` set reads `s"Waiting for $names decision on $card…"`, the names joined as today. In `render`, use `deciding` in place of `answers` when `asked.use` is set. Update the Scaladocs with the new wording.

- [ ] **Step 7: Run the tests to verify they pass**

Run the command of Step 2.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala \
  src/main/scala/oathdigital/application/ConsentProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ConsentLines.scala \
  src/main/scala/oathdigital/application/gamelog/DetailLines.scala \
  frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogAutomaticUseSuite.scala \
  src/test/scala/oathdigital/application/ConsentProjectionSuite.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala
git commit -m "feat(batch8): word the ruler's decision on an automatic power"
```

---

### Task 5: Hunger, and every wrapped power under a waivable refusal

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala`
- Modify: `src/test/scala/oathdigital/testkit/AppendedAutomaticUses.scala` (`nodes`)
- Create: `src/test/scala/oathdigital/gameplay/powers/poweruse/AutomaticUseStepWiringSuite.scala`
- Create: `src/test/scala/oathdigital/application/HungerConsentServiceSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala`

**Interfaces:**
- Consumes: Tasks 1 to 4.
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the failing Hunger tests**

In `CityWallSuite`, replace "Hunger is not forced on its holder at its site, ruled by an enemy" with:

```scala
  test("Hunger at its site is still due when an enemy rules it, for the " +
      "ruler to decide, and not under bandits"):
    // p2 holds Hunger faceup and Wrestlers at Broken Peaks, as
    // `HungerSuite` stages it; City Wall stands there.
    val peaks = CatalogNames.site("Broken Peaks")
    def holding(steps: Table => Table): ReadyGame =
      steps(Table.start.adviser(p2, "Hunger").adviser(p2, "Wrestlers")
        .pawn(p2, at = peaks).denizen(wall, at = peaks)).ready
    val phasePowers = PhasePowerCatalog.default(catalog)
    def forced(ready: ReadyGame) =
      ForcedWakeProcedure.due(catalog, ready, p2, phasePowers)
    assert(forced(holding(_.warbandsAt(peaks, p1, 1))).nonEmpty)
    assertEquals(forced(holding(_.bandits(peaks, 1))), Vector.empty)
```

Replace "Tyrant arriving at its site, ruled by an enemy, kills nothing" with two tests:

```scala
  test("Tyrant arriving at its site under bandits kills nothing"):
    import TravelFixture.coast
    val ready = holdingTyrant(_.denizen(wall, at = coast).bandits(coast, 2))
    val done = Outcomes.ready(accepted(TravelFixture.travel(ready, coast))
      .state)
    assertEquals(Look(done).forces(coast),
      SiteForces.Occupied(ForceKind.Bandit, 2))

  test("Tyrant arriving at its site, ruled by an enemy, waits at its step"):
    import TravelFixture.coast
    val ready = holdingTyrant(_.denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))
    val travelled = accepted(TravelFixture.travel(ready, coast))
    assertEquals(TravelFixture.parkedDecision(travelled.state).decisionId,
      PowerUseStep.decisionId(Tyrant.id))
```

Import `oathdigital.gameplay.powerresolver.PowerUseStep` and `oathdigital.gameplay.powers.travel.Tyrant`.

Create `HungerConsentServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.application.gamelog.LogScripts.{formatter, texts}
import oathdigital.gameplay.powers.poweruse.{CityWall, ForestCouncil}
import oathdigital.gameplay.powers.travel.TravelFixture
import oathdigital.gameplay.powers.wake.{Hunger, HungerCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Hunger refused by City Wall and Forest Council: every ruler is asked
  * at once, and the first deactivation settles it (automatic power
  * consent design, rulings 6 and 9; slice 2). */
class HungerConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val woods = CatalogNames.site("Deep Woods")

  /** HungerSuite's board: p2 holds Hunger faceup and Wrestlers, and p3
    * holds Rain Boots, both at Broken Peaks, in p1's Act. City Wall
    * stands at Broken Peaks, ruled by `peaksRuler`, and Forest Council at
    * Deep Woods, ruled by `woodsRuler`. */
  private def hungerUnder(peaksRuler: PlayerId, woodsRuler: PlayerId)
      : ReadyGame =
    Table.start.adviser(p2, HungerCard.id).adviser(p2, "Wrestlers")
      .adviser(p3, "Rain Boots").pawn(p2, at = peaks).pawn(p3, at = peaks)
      .denizen(CatalogNames.denizen("City Wall"), at = peaks)
      .warbandsAt(peaks, peaksRuler, 1)
      .denizen(CatalogNames.denizen("Forest Council"), at = woods)
      .warbandsAt(woods, woodsRuler, 1).ready

  /** p1 begins Rest, and p2's forced Wake reaches Hunger. */
  private def rested(service: GameApplicationService)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, 0L,
      MenuPlay.starting(p1, PhaseTransitionRef.BeginRest)))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice, granter: PlayerId)(using munit.Location)
      : GameAccepted = accepted(service.handle(gameId, at.nextSequence,
    GameCommand.AnswerConsent(granter, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  private def log(service: GameApplicationService, viewer: PlayerId)(
      using munit.Location): Vector[String] =
    texts(formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(viewer)))

  test("Hunger refused by two rulers' cards asks both, and the first " +
      "deactivation settles it"):
    // p1 rules Broken Peaks (City Wall), p3 rules Deep Woods (Forest
    // Council).
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    val request = present(readyAfter(waiting).consent.pending, "the request")
    assertEquals(request.asks.map(ask => ask.granter -> ask.powers.toSet)
      .toSet, Set(p1 -> Set(CityWall.id), p3 -> Set(ForestCouncil.id)))
    val no = answering(service, waiting, ConsentChoice.No, granter = p3)
    assertEquals(readyAfter(no).consent.pending, None)
    assertEquals(Look(readyAfter(no)).player(p2).advisers.size, 2)
    assertEquals(Look(readyAfter(no)).player(p3).advisers.size, 1)
    // The cards and granters follow the restrictions' catalog order.
    val lines = log(service, p2)
    assert(lines.exists(line => line.contains("City Wall") &&
      line.contains("Forest Council") && line.contains(", ") &&
      line.endsWith(" decide whether Hunger works.")), lines)
    assert(lines.contains("p3 deactivated Hunger with Forest Council."),
      lines)

  test("one ruler of both cards is asked once, about both"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p1))
    val request = present(readyAfter(rested(service)).consent.pending,
      "the request")
    assertEquals(request.asks.map(ask => ask.granter -> ask.powers.toSet),
      Vector(p1 -> Set(CityWall.id, ForestCouncil.id)))

  test("when every ruler lets Hunger work, its holder is asked whom to " +
      "bury"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    val both = answering(service, answering(service, waiting,
      ConsentChoice.Yes, granter = p1), ConsentChoice.Yes, granter = p3)
    assertEquals(TravelFixture.parkedDecision(both.state).decisionId,
      Hunger.decisionId)

  test("while Hunger's rulers decide, nobody may undo: the Rest that " +
      "walked there is a barrier"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    for viewer <- Vector(p1, p2, p3) do
      assertEquals(projector.project(gameId, waiting.loaded, viewer).undo,
        None)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p1)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.CityWallSuite oathdigital.application.HungerConsentServiceSuite"`
Expected: FAIL. `due` drops a Hunger that a player-ruled City Wall refuses, so no request opens.

- [ ] **Step 3: Wrap Hunger**

In `ForcedWakeProcedure`, import `oathdigital.gameplay.operations.PowerUseRules` and `oathdigital.gameplay.powerresolver.PowerUse`.

Replace `due`'s refusal check with `!deactivatedUnasked(catalog, ready, player, power, found, powers)`, and add:

```scala
  /** Whether `power` from `source` is refused by a restriction nobody may
    * waive. Such a power is deactivated unasked and is not due. One that
    * only waivable restrictions refuse is due, and its node asks the
    * refusing cards' rulers (automatic power consent design). Raw
    * refusals are read, so a grant or a refusal never changes what is
    * due between the walk and its resume. */
  private def deactivatedUnasked(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, power: PhasePower,
      source: PowerSourceRef, powers: PhasePowers): Boolean =
    source match
      case PowerSourceRef.Card(card) =>
        val use = PowerUse.automatic(player, power.id, card)
        val raised = PowerUseRules.raised(ready, use, powers.useRestrictions)
        raised.nonEmpty && !PowerUseRules.waivable(use, raised)
      case _ => PhasePowerProcedure.refusals(catalog, ready, player, power,
        source, powers.useRestrictions).nonEmpty
```

In `build`, wrap each tree whose source is a card:

```scala
      built.flatMap(done => power.build(ready, player, source).map(tree =>
        done :+ cardOf(source).fold(tree)(card => AutomaticUse(player,
          power.id, card, (_, _) => Vector(tree)))))
```

with:

```scala
  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Relic(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
```

Rewrite the object's Scaladoc sentence about refusals: each forced power runs inside an `AutomaticUse`, so a refusal the refusing card's ruler may waive asks them first; a power refused by a restriction nobody may waive is not due.

- [ ] **Step 4: Write the per-power wiring suite**

In `AppendedAutomaticUses`, move the fold into `nodes`, and let `at` read it:

```scala
  /** Each `AutomaticUse` that `power`'s transforms at `window` append to
    * `operation`'s children, folded for `actor` on an empty table, for a
    * suite that walks them. A windowed leaf folds itself, as the walker
    * folds it. */
  def nodes(power: ContributingPower, window: PowerWindow,
      operation: Operation, actor: PlayerId = Table.p1)
      : Vector[AutomaticUse] =
    val ctx = PowerCtx(Table.start.ready, actor, power.source, window,
      Vector.empty, operation)
    val children = operation match
      case leaf: LeafOperation => Vector(leaf)
      case other => other.children
    power.contributions.getOrElse(window, Vector.empty)
      .collect { case transform: Transform => transform }
      .foldLeft(children)((ops, transform) => transform.fn(ctx, ops))
      .drop(children.size)
      .collect { case use: AutomaticUse => use }

  /** Each appended node's user, power and card. */
  def at(power: ContributingPower, window: PowerWindow, operation: Operation,
      actor: PlayerId = Table.p1): Vector[Named] =
    nodes(power, window, operation, actor).map(use =>
      Named(use.user, use.power, use.card))
```

Create `AutomaticUseStepWiringSuite.scala`:

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerUse, PowerUseRefusal, PowerUseRestriction, PowerUseStep}
import oathdigital.gameplay.powers.economy.{VowOfBeastkin,
  VowOfWanderingMuster}
import oathdigital.gameplay.powers.travel.{Hospitality, Tyrant}
import oathdigital.gameplay.powers.whenplayed.{ConspiracyWhenPlayed,
  ThreateningRoar, ThreateningRoarCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedUses, PowerNoted, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{AppendedAutomaticUses, CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Every wrapped automatic power waits at its own step when only a
  * waivable refusal stops it, and stays deactivated by a refusal
  * remembered this turn (automatic power consent design, slice 2). */
class AutomaticUseStepWiringSuite extends munit.FunSuite:
  private val refusingCard = CatalogNames.denizen("Wrestlers")
  private val refusingSource = PowerSourceRef.Card(refusingCard)

  /** The key p3's answer on `use` is remembered under. */
  private def keyOf(use: AutomaticUse): ConsentKey = ConsentKey(
    RefusesEveryUseUnlessP3Consents.id, use.user, PowerUseRules.useKey,
    PowerUseRules.target(PowerUse.automatic(use.user, use.power, use.card)))

  /** A test power: refuses every automatic use, and p3 may waive it. */
  private object RefusesEveryUseUnlessP3Consents extends ContributingPower
      with PowerUseRestriction:
    val id: PowerId = PowerId("test.refuses-every-use-waivably")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def powerUseRestrictions: Vector[PowerUseRestriction] =
      Vector(this)
    private val note = PowerNote(refusingSource, "refused", Vector.empty)
    def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
      Some(PowerUseRefusal(id, note, OperationReason("test-refused",
        "every use", OperationReasonKind.Impossible, Some(OperationWaiver(
          id, Some(p3), ConsentKey(id, use.user, PowerUseRules.useKey,
            PowerUseRules.target(use)), note, note)))))

  private val refusing = WalkerPowers(Vector(RefusesEveryUseUnlessP3Consents))

  /** p2's pawn moving from Ancient City to Deep Woods in a travel's cost
    * node, as `HospitalitySuite` stages it. */
  private val p2Travels: Operation = Sequence(Vector[Operation](Move(
    Piece.Pawn(p2), PositionedLocation(Location.Site(
      CatalogNames.site("Ancient City"))), PositionedLocation(Location.Site(
      CatalogNames.site("Deep Woods"))))), Some(PowerWindow.TravelCost))

  /** A Muster's gain node: one warband for p1. */
  private val p1MusterGain: Operation = Sequence(Vector[Operation](
    Gain.Warbands(p1, ForceKind.Bandit, 1)), Some(PowerWindow.MusterGain))

  private val wrapped: Vector[(String, ContributingPower, PowerWindow,
      Operation)] = Vector(
    ("Threatening Roar", ThreateningRoar.forCatalog(catalog),
      PowerWindow.ActionCardPlayedFaceup, CardPlayedFaceup(
        ThreateningRoarCard.id, RuleSourceRef.GameRule(
          ThreateningRoar.id.value))),
    ("Conspiracy", ConspiracyWhenPlayed, PowerWindow.ActionCardPlayedFaceup,
      CardPlayedFaceup(VisionRules.Conspiracy, RuleSourceRef.GameRule(
        ConspiracyWhenPlayed.id.value))),
    ("Tyrant", Tyrant, PowerWindow.TravelCost, p2Travels),
    ("Hospitality", Hospitality.forCatalog(catalog), PowerWindow.TravelCost,
      p2Travels),
    ("Vow of Beastkin", VowOfBeastkin.forCatalog(catalog),
      PowerWindow.MusterGain, p1MusterGain),
    ("Vow of Wandering", VowOfWanderingMuster, PowerWindow.MusterGain,
      p1MusterGain))

  wrapped.foreach { (name, power, window, operation) =>
    test(s"$name's use waits at its own step, owned by its user"):
      val use = present(AppendedAutomaticUses.nodes(power, window,
        operation).headOption, s"$name's node")
      val tree = Sequence(Vector[Operation](use))
      val ready = Table.start.ready
      val pending = accepted(ProcedureWalker.advance(ready, tree, None,
        refusing)) match
        case WalkerOutcome.Parked(at, _) => at
        case other => fail(s"the walk did not park: $other")
      val step = present(ProcedureWalker.parkedDecide(ready, tree, pending,
        refusing), "the step")
      assertEquals((step.decisionId, step.owner),
        (PowerUseStep.decisionId(use.power), use.user))
      assertEquals(ParkedUses.of(ready, tree, pending, refusing)
        .map(found => (found.user, found.power, found.card)),
        Some((use.user, use.power, use.card)))

    test(s"$name stays deactivated by a refusal remembered this turn"):
      val use = present(AppendedAutomaticUses.nodes(power, window,
        operation).headOption, s"$name's node")
      val start = Table.start.ready
      val refused = start.copy(consent = ConsentState(answers = Map(
        keyOf(use) -> ConsentAnswer.Refused.by(p3)),
        turn = Some(ConsentTurn.of(start))))
      accepted(ProcedureWalker.advance(refused,
          Sequence(Vector[Operation](use)), None, refusing)) match
        case WalkerOutcome.Finished(_, events) =>
          assertEquals(events.collect { case PowerNoted(_, note, _) =>
            note }, Vector(PowerUseStep.staysDeactivated(refusingSource,
              NoteArg.Card(use.card))))
        case other => fail(s"the walk did not finish: $other")
  }
```

Hospitality's node is p2's, so the test power's granter is p3, never a user.

- [ ] **Step 5: Add Forest Council's player-ruled scenarios**

In `ForestCouncilSuite`, add the cases Task 1 left out, with the ledger seeded:

```scala
  /** `ready` with p2's `answer` on p1's use of `card` remembered this
    * turn, against Forest Council. */
  private def rememberingP2sAnswerOn(ready: ReadyGame, card: DenizenId,
      answer: ConsentAnswer): ReadyGame = ready.copy(consent = ConsentState(
    answers = Map(ConsentKey(ForestCouncil.id, p1, PowerUseRules.useKey,
      DecisionOptionRef.Denizen(card)) -> answer.by(p2)),
    turn = Some(ConsentTurn.of(ready))))

  test("an enemy's Beast When Played power waits on Forest Council's " +
      "ruler, whose remembered answer decides it"):
    import WhenPlayedHarness.{awaySite, homeSite, p1AtHomeSite}
    val roar = WhenPlayedHarness.registered[ThreateningRoar]
    val councilRuledByP2 = p1AtHomeSite.adviser(p1, roar.cardId)
      .denizen(errandBoy, at = homeSite).denizen(council, at = awaySite)
      .warbandsAt(awaySite, p2, 1).ready
    def played(ready: ReadyGame) =
      accepted(WhenPlayedHarness.playBeside(ready, roar, roar.cardId, power))
    assert(played(councilRuledByP2).isInstanceOf[WalkerOutcome.Parked])
    def errandBoyStays(answer: ConsentAnswer): Boolean =
      played(rememberingP2sAnswerOn(councilRuledByP2, roar.cardId,
        answer)) match
        case WalkerOutcome.Finished(end, _) =>
          Look(end).cardsAt(homeSite).contains(errandBoy)
        case other => fail(s"the walk did not finish: $other")
    assert(errandBoyStays(ConsentAnswer.Refused))
    assert(!errandBoyStays(ConsentAnswer.Granted))

  test("an enemy's Vow of Beastkin adds its warband once Forest Council's " +
      "ruler lets it work"):
    val vow = CatalogNames.denizen("Vow of Beastkin")
    val tyrant = CatalogNames.denizen("Tyrant")
    val musterer = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(wrestlers, at = home).adviser(p1, vow).adviser(p1, tyrant)
      .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
    val councilRuledByP2 = musterer.denizen(council, at = woods)
      .warbandsAt(woods, p2, 1).ready
    def mustered(ready: ReadyGame): Int =
      Look(musteredFrom(ready, DecisionOptionRef.Denizen(wrestlers)))
        .boardWarbands(p1)
    val free = mustered(musterer.ready)
    assertEquals(mustered(rememberingP2sAnswerOn(councilRuledByP2, vow,
      ConsentAnswer.Granted)), free)
    assertEquals(mustered(rememberingP2sAnswerOn(councilRuledByP2, vow,
      ConsentAnswer.Refused)), free - VowOfBeastkin.Warbands)
```

The existing bandit test reads the board after the walk with `.treeless`; if `Look(end).cardsAt` needs it, call it the same way. Import `PowerUseRules` and `WalkerOutcome` if the suite lacks them.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.* oathdigital.gameplay.phases.* oathdigital.application.HungerConsentServiceSuite"`
Expected: PASS. `HungerSuite` still passes: with no refusing card, Hunger's node is allowed.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala \
  src/test/scala/oathdigital/testkit/AppendedAutomaticUses.scala \
  src/test/scala/oathdigital/gameplay/powers/poweruse/AutomaticUseStepWiringSuite.scala \
  src/test/scala/oathdigital/application/HungerConsentServiceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala
git commit -m "feat(batch8): Hunger and every wrapped power wait on their rulers"
```

---

### Task 6: Rulings, docs, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 10)
- Modify: `docs/superpowers/specs/2026-10-15-automatic-power-consent-design.md` (Status, a "Departures" section)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (slice 10 section, plan-time rulings, Status)
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

In the rulings appendix's slice 10 entry, replace the bullet "Slice 10 refuses an automatic power silently. Roadmap item 10b lets the refusing card's ruler decide through consent" with the design's rulings 2 to 9, one bullet each, as the appendix writes entries. Name the design as their source, and note that item 10b implemented them on 2026-10-17. Add the log lines: "City Wall: {Red} decides whether {Tyrant} works.", "{Red} deactivated {Tyrant} with {City Wall}.", "{Red} let {Tyrant} work." and "City Wall: {Tyrant} stays deactivated."

- [ ] **Step 2: Update the automatic power consent design**

Set its Status to "designed 2026-10-15; done 2026-10-17". Add a section "Departures" after "Deferred", holding this plan's "Departures from the design" list in the design's own style. Reword the "Game Log" bullet "Remembered refusal" to the note form.

- [ ] **Step 3: Update the batch 8 design**

In the slice 10 section, replace "Slice 10 refuses silently; roadmap item 10b ([automatic power consent design](../specs/2026-10-15-automatic-power-consent-design.md)) adds the waivable branch, where the refusing card's ruler decides through consent, and wraps Hunger." with "Slice 10 refused silently. Roadmap item 10b (the same link) added the waivable branch, where the refusing card's ruler decides through consent, and wrapped Hunger." In "Plan-time rulings", rewrite "A refused automatic power" to say what holds now: refused by a restriction nobody may waive, it does not apply and writes no line; refused only waivably, the refusing card's ruler decides.

Set the Status line to "Slices 1 to 15 and item 10b are done."

- [ ] **Step 4: Update the consent design**

Add a short section "Automatic powers (catalog batch 8, item 10b)" after "Alternatives". It covers:
- the hidden step and who owns it;
- the walk opening the request, and `ConsentUse`;
- settling both ways, and the issuer's batch;
- undo coupling, barriers, and Redo asking again;
- the prompt's and the waiting line's wording;
- the fact that the preferences grid is unchanged.

- [ ] **Step 5: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by about 8 + 7 + 7 + 5 + 12 + 6 = 45 new tests, and the frontend count by 3.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 6: Record the status**

- In the ROADMAP's "Now" paragraph, write "slices 1 to 15 and item 10b are done".
- Mark item 10b done: `- [x] **10b. The ruler's choice over refused automatic powers.**`, keeping its text.
- Change the line under the list to "Only items 13b, 13c and 14b of catalog batch 8 remain open."

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-15-automatic-power-consent-design.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/superpowers/specs/2026-10-08-consent-system-design.md \
  docs/ROADMAP.md
git commit -m "docs(batch8): item 10b done"
```
