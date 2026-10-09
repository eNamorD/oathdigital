# Consent System, Slice 4 (Toll and Penalty Cards) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An enemy of the player who rules Toll Roads, Forced Labor, Curfew or Grasping Vines chooses between paying and going on without paying, and may ask that ruler for consent to go on free, even when they cannot pay.

**Architecture:**

- **One pay-or-ask step per card.** When a player other than the payer rules the card, its toll or kill becomes a choose-one:
  - the paying option, "Give 1 favor to p2" (or "Kill 1 warband"), unavailable with "Needs 1 favor; you have 0" when the payer is short;
  - the free option, "Travel without paying" (or the card's wording).

  Under bandits the payment runs as before.
  - The step is a `Decide` in a new window, `PowerWindow.PayOrAsk`. Only the card's waivable `OptionRestriction` hooks it, and it bars the free option until the ruler consents.
  - A shared helper, `gameplay/powers/PayOrAsk.scala`, builds the step, the restriction and the waiver. `RulerToll` uses it for Forced Labor and Curfew.
  - Every question of a cost is asked before any of it is paid: each step goes first in the cost, and every payment, Way Station's included, runs last.
- **The walker asks at a choose-one on the option path.** Slice 2 made the option path askable at a choose-many only, because it had no search to check that a grant would open the option. Where the walker narrows a parked decision, `WalkerPowerGather.optionAsks` now makes a choose-one's barred option askable when the search, with the grant remembered, reaches a legal end from it.
- **A payer who cannot pay may still ask.** A search that finds no answer at a step stops there when an option may be asked about. The step is then shown with the paying option unavailable and the free option askable.
- **Previews follow through a pay-or-ask step.** A destination's badge ("1 Supply · 1 favor") and a refused option's shortfall ("Needs 1 favor; you have 0") read the toll from the step's answers. For a payer who cannot pay, they read the free option as if its ruler had consented.
- **Application.** The ruler's prompt names a button option by its label. The rest of slice 1's machinery (requests, answers, preferences, the grid, the log) applies unchanged.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, unchanged here), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-08-consent-system-design.md`. Read the whole spec before starting. Skim the decisions of the slice 1, 2 and 3 plans (`docs/superpowers/plans/2026-10-09-consent-slice1.md`, `-slice2.md` and `-slice3.md`, "Plan decisions beyond the spec"). This plan covers the spec's "Slices" item 4 and its "Toll and penalty steps" paragraph.

**Builds on slice 3,** merged into `main` at 560b9a4d. This plan uses:

- slice 3's four-field `OptionReason.Askable`, whose `covers` field defaults to empty;
- its card-id preference grid;
- its spec text, which Task 7 edits.

## Plan decisions beyond the spec

The code forced these, or the spec left them open. Task 7 records each one in the spec.

1. **A new window for the step.** `PowerWindow.PayOrAsk` is an `OtherWindow` with the key `consent.pay-or-ask`. Only each card's restriction hooks it. Each card's `applicable` answers per window: at the step, it applies only to its own step.
2. **A choose-one's option-path ask is judged with the search.** Slice 2 decision 2 left this to the first slice that needs it.
   - The window fold cannot search, so it still marks a barred option unavailable with its ordinary note.
   - Where the walker narrows a parked decision (`ProcedureWalker.narrowAt`), `WalkerPowerGather.optionAsks` gathers the decision's own window again. It makes a barred option askable when every block on it is waivable (ruling 8) and `grantable` says a grant opens it.
   - So a grant always answers the step, and no request is left waiting on a grant that fails.
   - Only a decision hooked by its own window gets these asks. Every pay-or-ask step is one.
3. **The free option's key names the occasion.** Its reference is `Button("free.<occasion>")`, where the occasion is:
   - the destination, for Toll Roads;
   - the pawn's site, for Forced Labor;
   - the traded card, for Curfew;
   - the site left, for Grasping Vines.

   A grant covers that occasion for the rest of the turn (ruling 12), not every toll the card charges.
4. **Every question of a cost is asked before any of it is paid.** A resume folds a cost node again against the state the walk parked in. A payment made before a later question in the same node would change what that fold reads. Way Station decides whether to ask from the favor held, so its question would vanish and the parked position would point at the wrong node. Two changes avoid this:
   - Each step goes first in the cost node, and what its answer runs goes last (`PayOrAsk.around`).
   - Every toll and penalty payment goes last too, under bandits and for Way Station's answer. A cost's payments therefore follow its Supply, and the toll's log line now follows the Supply spent.
5. **A payer who cannot pay may still ask.**
   - **What the payer sees.** The paying option is built unavailable with "Needs 1 favor; you have 0". A search that finds no answer at the step stops there when an option may be asked about and a grant would open it. So the destination, pile or card is offered, and the step shows only the askable free option.
   - **After a refusal.** The step has nothing to choose, and the payer's Undo takes back the pick that led there. Nothing hidden has been shown by then, because the step comes before the move, the draw and the trade. The pick is then shut for the turn, with its shortfall as the reason.
   - **The walk agrees with the search.** If a paying option that looked affordable fails the search, the walk keeps the step for the askable free option instead of refusing it.
6. **Previews read the free option for a payer who cannot pay.** `WalkerSimulation.previewParked` previews a pay-or-ask step with no available option through its askable options, as if their ruler had consented. A destination's badge then shows what going on free costs, not "0 Supply".
7. **Lines.**
   - Paying keeps today's lines.
   - Going on free writes a parallel line:
     - Toll Roads: "p1 paid nothing, with p2's consent."
     - Forced Labor and Curfew: "Gave nothing, with p2's consent."
     - Grasping Vines: "Killed nothing, with p2's consent."
   - Every answer writes a line, so each step's decision is narrated: no "Chose …" line.
8. **Previews follow through a pay-or-ask step.**
   - `WalkerSimulation.previewParked` previews a pay-or-ask step whether or not `followUp` asks for it, and the step does not use up the follow-up level.
   - `PricedOptions` reads every way on, through each further question. So Toll Roads keeps its badge, and Way Station with Toll Roads still reads "1 Supply · 1 favor or 2 favor".
9. **Way Station counts Toll Roads' step as owed favor.** It cannot know whether the ruler will waive the toll, so it keeps assuming the toll is paid.
10. **The prompt names a button by its label.** `ConsentProjector` read a target's label from `DecisionOption.forRef`, which has no label for a button, so the prompt would have shown the raw key. It now reads a button's label from the parked decision.
11. **Tying tree-path grants stays deferred.** Slice 4 adds no tree-path decision. The Pass's Travel destination and Secret Police's placement are still each the only such decision in their walk.
12. **The Hall's discard paths with no option stay deferred.** A pay-or-ask step needs an option to ask from, and Dark Enforcer and the region discards have none.
13. **No frontend change.** The action panel already draws an askable choose-one option (slice 1, "keep askable choices amber in the action pane"). A toll card is a site card, which opens its ruler's grid through slice 3's `cardId`.
14. **No browser acceptance run.** The reason is the same as slice 1 decision 9. Task 7 extends the roadmap's consent play-test item.

## Global Constraints

- **Build rules.** Every project builds with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import, private member or local fails the build. Delete whatever the compiler names. A match on a sealed type must stay exhaustive.
- **File size.** Every Scala file, production or test, stays at or under 800 lines (`scripts/check-architecture.py`, `BackendArchitectureSuite`). Sizes before this plan:
  - `ProcedureWalker.scala` 633, `WalkerPowerGather.scala` 484;
  - `WalkerSimulation.scala` 197, `WalkerSearch.scala` 176, `PricedOptions.scala` 124.

  Keep each addition to what the task shows.
- **Architecture guard.** `scripts/check-architecture.py` reads a `Map(` call in production code as a lookup table. Build maps the way the surrounding code does. A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. No power name appears in walker sources; the window name `PayOrAsk` is not a power name.
- **Ids and wording, exactly:**
  - Window `PowerWindow.PayOrAsk`, key `consent.pay-or-ask`.
  - Paying option `Button("pay")`. Free option `Button("free.<occasion>")`.
  - A favor toll's paying option, when its payer is short: unavailable with the written reason `Needs 1 favor; you have <held>`.
  - Decision ids: `power.toll-roads.toll`, `power.forced-labor.toll`, `power.curfew.toll`, `power.grasping-vines.kill`.
  - Steps:

    | Card | Heading | Paying label | Free label |
    |---|---|---|---|
    | Toll Roads | `Toll Roads: give 1 favor to travel there?` | `Give 1 favor to <ruler id>` | `Travel without paying` |
    | Forced Labor | `Forced Labor: give 1 favor to search here?` | `Give 1 favor to <ruler id>` | `Search without paying` |
    | Curfew | `Curfew: give 1 favor to trade with this card?` | `Give 1 favor to <ruler id>` | `Trade without paying` |
    | Grasping Vines | `Grasping Vines: kill 1 warband to travel on?` | `Kill 1 warband` | `Travel without killing` |

  - Ordinary and consent note keys, worded exactly:
    - Toll Roads:
      - `no-free-travel`: `Cannot travel to its ruler's sites without giving favor.`
      - `no-free-travel-consent`: `Cannot travel to its ruler's sites without giving favor or ruler's consent.`
    - Forced Labor:
      - `no-free-search`: `Cannot search at its ruler's sites without giving favor.`
      - `no-free-search-consent`: `Cannot search at its ruler's sites without giving favor or ruler's consent.`
    - Curfew:
      - `no-free-trade`: `Cannot trade with its ruler's cards without giving favor.`
      - `no-free-trade-consent`: `Cannot trade with its ruler's cards without giving favor or ruler's consent.`
    - Grasping Vines:
      - `must-kill`: `Must kill a warband when traveling from its ruler's sites.`
      - `must-kill-consent`: `Must kill a warband when traveling from its ruler's sites, unless ruler consents.`
  - Free lines:
    - Toll Roads `waived`: `{traveller} paid nothing, with {ruler}'s consent.`
    - `RulerToll.waived`: `Gave nothing, with {ruler}'s consent.`
    - Grasping Vines `spared`: `Killed nothing, with {ruler}'s consent.`
  - Every other id, command, event, log line and UI string is slice 1's.
- **Rulings** (spec, "Rulings"). These apply: 1, 3, 4, 5, 7, 8, 10, 12, 13, 14 and 15–19. Each step carries one power, so ruling 9's several granters still cannot arise.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree on branch `feat/consent-slice4`.
  - `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
  - Copy this plan into the worktree if it is not committed on `main` yet; Task 7 commits it.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One suite: `./sbtw "testOnly oathdigital.gameplay.walker.ChooseOneConsentSuite"` (any fully qualified suite name).
  - Whole server: `./sbtw test`.
  - Full gate (Task 7):
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `./sbtw coverage test coverageReport`
    4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    5. `git diff --check`

## File map

All `src/main/scala/...` paths below are under `src/main/scala/oathdigital/`, and test paths under `src/test/scala/oathdigital/`.

Created:

- `gameplay/powers/PayOrAsk.scala`: the step, its restriction and its waiver.
- Tests:
  - `gameplay/walker/ChooseOneConsentSuite.scala`
  - `gameplay/powers/travel/TollRoadsConsentSuite.scala`
  - `application/TollConsentFixture.scala`
  - `application/TollConsentServiceSuite.scala`

Modified:

- Model: `model/PowerWindow.scala` gets `PayOrAsk`.
- Walker:
  - `gameplay/walker/WalkerPowerGather.scala`: `optionAsks`.
  - `gameplay/walker/WalkerSearch.scala`: `reasoned`.
  - `gameplay/walker/ProcedureWalker.scala`: `narrowAt` applies the asks; `reach` stops where an option may be asked about.
  - `gameplay/walker/WalkerSimulation.scala`: previews follow a step, and read an unpayable step's askable options.
  - `gameplay/walker/PricedOptions.scala`: badges and shortfalls read every way on.
  - `gameplay/powerresolver/ContributingPower.scala`: the `OptionRestriction` doc.
- Powers:
  - `gameplay/powers/RulerToll.scala`: `payOption`, `waived` and `owed`.
  - `gameplay/powers/travel/TollRoads.scala`, `WayStation.scala` and `GraspingVines.scala`.
  - `gameplay/powers/search/ForcedLabor.scala` and `gameplay/powers/economy/Curfew.scala`.
- Application: `application/ConsentProjector.scala`, the button label.
- Tests:
  - `gameplay/walker/WalkerPreviewSuite.scala`
  - `gameplay/powers/travel/TravelFixture.scala`, `TollRoadsSuite.scala`, `WayStationSuite.scala` and `GraspingVinesSuite.scala`
  - `gameplay/powers/search/ForcedLaborSuite.scala` and `gameplay/powers/economy/CurfewSuite.scala`
  - `gameplay/powers/ConsentPowersSuite.scala`
  - `application/TravelDestinationRowsSuite.scala`
  - `application/gamelog/GameLogConsentSuite.scala`, `GameLogStartLineSuite.scala` and `LogScripts.scala`
- Docs: the spec, `CONTEXT.md`, `docs/ROADMAP.md` and `docs/rules/implementation-traceability.md`.

---

### Task 1: The walker asks about a choose-one's barred option, and previews through a step

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala`
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (`previewParked`, `answer`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (doc only)
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala`

**Interfaces:**
- Consumes (slices 1 to 3):
  - `OptionRestriction(fn, note, waiver)` and `Waiver(granter, source, note)`.
  - `OptionReason.Askable(power, note, asks)`, with slice 3's `covers` left at its default.
  - `ConsentState.granting(keys, turn)` and `ConsentTurn.of(ready)`.
  - `PowerStubs.ContributingStub(id, contributions)`.
- Produces:
  - `PowerWindow.PayOrAsk`.
  - `WalkerPowerGather.optionAsks(decide, state, activePlayer, powers, path, procedure, answered, grantable): Vector[(DecisionOptionRef, OptionReason)]`.
  - `WalkerSearch.reasoned(decide: Decide, reasons: Map[DecisionOptionRef, OptionReason]): Decide`.
  - Previews and badges follow a pay-or-ask step.
  - In `ChooseOneConsentSuite`, for Task 5:
    - the helpers `step(payOption, afterPay, afterFree)`, `parkedAt(at, action, powers)` and `freeOffered(at, action, powers)`;
    - the values `toll`, `payButton`, `freeButton`, `askable`, `refused` and `ready`.

- [ ] **Step 1: Write the failing walker tests**

Create `src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{OptionRestriction, Waiver}
import oathdigital.model._
import oathdigital.testkit.PowerStubs.ContributingStub
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** An option a waivable `OptionRestriction` bars at a choose-one hooked by
  * its own window: askable when a grant would open it, judged with the
  * search where the walker narrows the parked decision (consent slice 4). */
class ChooseOneConsentSuite extends munit.FunSuite:
  private val ready = Table.start.supply(p1, 5).ready
  private val stepId = "test.step"
  private val pay = DecisionOptionRef.Button("pay")
  private val free = DecisionOptionRef.Button("free")
  private val payButton = DecisionOption.Button(pay, "Pay")
  private val freeButton = DecisionOption.Button(free, "Go free")
  private val source = PowerSourceRef.Site(SiteId("test-site"))
  private val plain = NoteKey("plain", Vector(NotePart.Text("Pay first.")))
  private val consent = NoteKey("consent",
    Vector(NotePart.Text("Pay first, unless its ruler consents.")))

  /** A power barring `free` at the step until `granter` consents. */
  private def barrier(granter: Option[PlayerId]): ContributingStub =
    ContributingStub(PowerId("test.toll"), Map(PowerWindow.PayOrAsk ->
      Vector(OptionRestriction(
        (_, ref) => Option.when(ref == free)(
          OathViolation.InvalidEventOrder("the toll is owed")),
        (_, ref) => Option.when(ref == free)(plain(source)),
        Some(Waiver(_ => granter, source, (_, _) => consent(source)))))))
  private val toll = barrier(Some(p2))
  private val key = ConsentKey(toll.id, p1, stepId, free)
  private val askable = OptionReason.Askable(toll.id, consent(source),
    Vector(ConsentAsk(p2, Vector(toll.id))))
  private val refused = ready.copy(consent = ConsentState(
    answers = Map(key -> ConsentAnswer.Refused),
    turn = Some(ConsentTurn.of(ready))))

  /** The step offering `payOption` and the free option, then `afterPay` or
    * `afterFree` by its answer. */
  private def step(payOption: DecisionOption = payButton,
      afterPay: Vector[Operation] = Vector.empty,
      afterFree: Vector[Operation] = Vector.empty): Operation =
    Sequence(Vector[Operation](
      Decide(stepId, p1, DecisionQuery.ChooseOne(Vector(payOption,
        freeButton)), window = Some(PowerWindow.PayOrAsk)),
      Branch((_, pending) => pending.answered.collectFirst {
        case Answered(`stepId`, DecisionAnswer.ChooseOneAnswer(ref), _) =>
          if ref == free then afterFree else afterPay
      }.getOrElse(Vector.empty))))

  private def parkedAt(at: ReadyGame, action: Operation,
      powers: WalkerPowers)(using munit.Location): PendingTree =
    Outcomes.parked(ProcedureWalker.advance(at, action, None, powers)).tree

  /** The free option as the parked step offers it. */
  private def freeOffered(at: ReadyGame, action: Operation = step(),
      powers: WalkerPowers = WalkerPowers(Vector(toll)))(
      using munit.Location): DecisionOption =
    ProcedureWalker.openDecisions(at, action, parkedAt(at, action, powers),
      powers).head.query match
      case DecisionQuery.ChooseOne(options, _) =>
        options.find(_.ref == free).getOrElse(fail("free is listed"))
      case other => fail(s"a choose-one, not $other")

  private def plainly: DecisionOption = DecisionOption.Unavailable(freeButton,
    OptionReason.Noted(toll.id, plain(source)))

  test("a barred option a grant would open is askable, asking the " +
      "barrier's ruler"):
    assertEquals(freeOffered(ready),
      DecisionOption.Unavailable(freeButton, askable))

  test("a barred option a grant would not open keeps its ordinary reason"):
    // Going free costs 99 Supply, which p1 does not have.
    assertEquals(freeOffered(ready, step(afterFree =
      Vector(SpendSupply(p1, 99)))), plainly)

  test("a barrier nobody may waive keeps its ordinary reason"):
    val rulerless = barrier(None)
    assertEquals(freeOffered(ready, powers = WalkerPowers(Vector(rulerless))),
      DecisionOption.Unavailable(freeButton,
        OptionReason.Noted(rulerless.id, plain(source))))

  test("a remembered refusal keeps its ordinary reason"):
    assertEquals(freeOffered(refused), plainly)

  test("a remembered grant opens the option, and the walk takes it"):
    val granted = ready.copy(consent = ConsentState()
      .granting(Vector(key), ConsentTurn.of(ready)))
    val powers = WalkerPowers(Vector(toll))
    assertEquals(freeOffered(granted), freeButton)
    accepted(ProcedureWalker.resolve(granted, step(),
      parkedAt(granted, step(), powers),
      Answered(stepId, DecisionAnswer.ChooseOneAnswer(free), p1), powers))
```

`PowerWindow.PayOrAsk` does not exist yet, so the suite does not compile.

- [ ] **Step 2: Write the failing preview tests**

In `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala`, change the import `import oathdigital.gameplay.walker.WalkerSimulation.PreviewOutcome` to `import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome, PreviewedOption}`, then append:

```scala
  /** Two plain decisions, then a step in `stepWindow`, then 2 Supply. */
  private def chain(stepWindow: Option[PowerWindow]): Operation =
    Sequence(Vector[Operation](
      Decide("first", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))),
      Decide("second", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(dear, "Dear")))),
      Decide("step", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(extra, "Pay"))), window = stepWindow),
      SpendSupply(p1, 2)))

  /** What `previewParked` with a follow-up says the first answer leads to. */
  private def followed(action: Operation)(using munit.Location)
      : Either[OathViolation, Vector[Either[OathViolation, PreviewOutcome]]] =
    val ready = withSupply(5)
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, action, None, WalkerPowers.empty))
    WalkerSimulation.previewParked(ready, action, parked.tree,
      WalkerPowers.empty, followUp = true).map(_.map(_.outcome))

  test("a pay-or-ask step is previewed past the one follow-up level"):
    val paid = PreviewOutcome(Vector(SpendSupply(p1, 2)), complete = true)
    val stepped = PreviewOutcome(Vector.empty, complete = false,
      next = Vector(PreviewedOption(DecisionOption.Button(extra, "Pay"),
        Right(paid))))
    assertEquals(followed(chain(Some(PowerWindow.PayOrAsk))),
      Right(Vector(Right(PreviewOutcome(Vector.empty, complete = false,
        next = Vector(PreviewedOption(DecisionOption.Button(dear, "Dear"),
          Right(stepped))))))))
    // Any other decision past the first follow-up is not previewed.
    assertEquals(followed(chain(None)),
      Right(Vector(Right(PreviewOutcome(Vector.empty, complete = false,
        next = Vector(PreviewedOption(DecisionOption.Button(dear, "Dear"),
          Right(PreviewOutcome(Vector.empty, complete = false)))))))))

  test("a badge reads every way on, through each further question"):
    val paying = PreviewOutcome(Vector(SpendSupply(p1, 2)), complete = true)
    val waived = PreviewOutcome(Vector.empty, complete = true)
    val stepped = PreviewOutcome(Vector(SpendSupply(p1, 1)), complete = false,
      next = Vector(
        PreviewedOption(DecisionOption.Button(cheap, "Pay"), Right(paying)),
        PreviewedOption(DecisionOption.Button(dear, "Free"), Right(waived))))
    val outer = PreviewOutcome(Vector.empty, complete = false, next = Vector(
      PreviewedOption(DecisionOption.Button(extra, "Go"), Right(stepped))))
    assertEquals(PricedOptions.badge(outer, p1), "3 Supply or 1 Supply")
```

- [ ] **Step 3: Add the window**

In `src/main/scala/oathdigital/model/PowerWindow.scala`, after the `NegotiationSettlement` case, add:

```scala
  /** A toll or penalty step an enemy of the card's player ruler answers: pay,
    * or go on without paying, which that ruler may consent to (consent
    * design, "Toll and penalty steps"). Only the card's waivable restriction
    * hooks it. */
  case object PayOrAsk extends OtherWindow { val key = "consent.pay-or-ask" }
```

- [ ] **Step 4: Run the walker tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ChooseOneConsentSuite oathdigital.gameplay.walker.WalkerPreviewSuite"`

Expected: FAIL.
- "a barred option a grant would open is askable" fails: the free option is `Noted`, not `Askable`.
- "a pay-or-ask step is previewed past the one follow-up level" fails: the step is not previewed.
- "a badge reads every way on" fails: the badge reads `1 Supply`.
- The other tests already pass.

- [ ] **Step 5: Add `optionAsks` to `WalkerPowerGather`**

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, add `DecisionOption` to the `oathdigital.model` import. After `optionAskable`, add:

```scala
  /** The asks of a parked choose-one's options that its own window's option
    * restrictions bar (consent slice 4). The fold cannot search, so it marks
    * each such option unavailable with the first restriction's note; here an
    * option every one of whose blocks is waivable (ruling 8) becomes askable
    * when `grantable` says a grant would open it. Empty for any other query
    * and for a decision with no window of its own. */
  def optionAsks(decide: Decide, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers, path: Vector[String],
      procedure: Option[ProcedureRef], answered: Vector[Answered],
      grantable: (DecisionOptionRef, Vector[PowerId]) => Boolean)
      : Vector[(DecisionOptionRef, OptionReason)] =
    (decide.query, decide.window) match
      case (one: DecisionQuery.ChooseOne, Some(window)) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, window, path, decide,
            procedure, answered)
        val forbids = forbidding(decide, ContributionCollector.gather(window,
          powers.windowIndex, ctxFor).optionRestrictions, ctxFor, byId)
        one.options.collect {
          case DecisionOption.Unavailable(option,
              OptionReason.Noted(power, _)) => (option.ref, power)
        }.flatMap { (ref, noted) =>
          val found = forbids(ref)
          found.headOption.filter(_.power == noted).flatMap(first =>
            consentAsk(decide, ref, found.map(forbid => Block(forbid.power,
              forbid.restriction.waiver, forbid.ctx)), first.ctx.state))
            .filter(ask => grantable(ref, ask.asks.flatMap(_.powers).distinct))
            .map(ref -> _)
        }
      case _ => Vector.empty
```

The `filter(_.power == noted)` keeps an option some other rule withheld (a written reason, another power's note) as it is.

- [ ] **Step 6: Add `reasoned` to `WalkerSearch`**

In `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`, after `explained`, add:

```scala
  /** `decide` with each unavailable option `reasons` names given that reason
    * instead: an option the fold barred with its note, made askable once the
    * search found that a grant would open it (consent slice 4). */
  def reasoned(decide: Decide, reasons: Map[DecisionOptionRef, OptionReason])
      : Decide =
    if reasons.isEmpty then decide
    else decide.query match
      case one: DecisionQuery.ChooseOne => decide.copy(query = one.copy(
        options = one.options.map {
          case DecisionOption.Unavailable(option, _)
              if reasons.contains(option.ref) =>
            DecisionOption.Unavailable(option, reasons(option.ref))
          case other => other
        }))
      case _ => decide
```

- [ ] **Step 7: Apply the asks in `narrowAt`**

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace `narrowAt` and its doc comment with:

```scala
  /** `decide` narrowed to the options from which some path reaches a legal
    * end (global operation restrictions design, "Lazy pruning"), each hidden
    * option a restriction's note explains kept as unavailable (the note is
    * its hover reason and nothing is journaled), or as declared when the
    * powers do not search. An option its own window's waivable restriction
    * bars is made askable when a grant would open it (consent slice 4). */
  private def narrowAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Option[Decide]] =
    if !ctx.powers.probing then Right(Some(decide))
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path)).map(_.map {
      cut =>
        val grants = grantable(decide, ctx, path)
        val reasons = WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
          ctx.state, ctx.activePlayer, ctx.powers, ctx.answered, ctx.procedure,
          grants).toMap
        val asks = WalkerPowerGather.optionAsks(decide, ctx.state,
          ctx.activePlayer, ctx.powers, path, ctx.procedure, ctx.answered,
          grants).toMap
        WalkerSearch.reasoned(WalkerSearch.explained(decide, cut, reasons),
          asks)
    })
```

- [ ] **Step 8: Run the walker suite**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ChooseOneConsentSuite"`

Expected: PASS, 5 tests.

- [ ] **Step 9: Make previews follow a step**

In `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`, add `PowerWindow` to the `oathdigital.model` import.

In the doc comment of `previewParked`, replace `(one level deep).` with:

```
(one level deep). A pay-or-ask step is previewed whether or not
    * `followUp` asks for it, and does not use up that level, so a priced
    * option still shows the toll its step asks (consent slice 4).
```

Replace `answer` with:

```scala
  private def answer(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, option: DecisionOption,
      followUp: Boolean): Either[OathViolation, PreviewOutcome] = guarded:
    ProcedureWalker.resolve(state, tree, pending,
      Answered(decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
        decide.owner), powers, WalkerDice.placeholder).map:
      case WalkerOutcome.Finished(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = true)
      case WalkerOutcome.Parked(next, events) =>
        val at = after(state, pending, next, events)
        val step = at.toOption.exists(waitsOnStep(_, tree, next, powers))
        PreviewOutcome(recordedOperations(events), complete = false,
          next = if !followUp && !step then Vector.empty
            else at.flatMap(previewParked(_, tree, next, powers,
              followUp = followUp && step)).getOrElse(Vector.empty))

  /** Whether the walk parked at `next` waits on a pay-or-ask step. */
  private def waitsOnStep(ready: ReadyGame, tree: Operation,
      next: PendingTree, powers: WalkerPowers): Boolean =
    ProcedureWalker.parkedDecide(ready, tree, next, powers, probing = false)
      .exists(_.window.contains(PowerWindow.PayOrAsk))
```

- [ ] **Step 10: Make badges and shortfalls read every way on**

In `src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala`, replace `answerable` and `badge` (with their doc comments) with:

```scala
  /** Whether the walk can go on from `outcome`: it finished, its further
    * question could not be previewed, or some answer to it can go on. */
  private def answerable(outcome: PreviewOutcome): Boolean =
    outcome.complete || outcome.next.isEmpty ||
      outcome.next.exists(_.outcome.exists(answerable))

  /** The operations along each way the walk goes on from `outcome`: its own,
    * then those of each accepted answer to the further question, a
    * pay-or-ask step's included. Its own alone when no further answer is
    * accepted. */
  private def paths(outcome: PreviewOutcome): Vector[Vector[CoreOperation]] =
    outcome.next.collect { case PreviewedOption(_, Right(following)) =>
      following }.flatMap(paths) match
      case Vector() => Vector(outcome.operations)
      case found => found.map(outcome.operations ++ _)

  /** "2 Supply", "2 Supply · 1 favor", "1 favor", or each way on joined
    * with " or ": "2 Supply or 1 favor". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    paths(outcome).map(words(_, actor)).distinct.mkString(" or ")
```

In `shortfall`, replace `val (supply, favor) = spent(outcome.operations, actor)` with `val (supply, favor) = spent(paths(outcome).head, actor)`.

- [ ] **Step 11: Update the `OptionRestriction` doc**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, in the `OptionRestriction` doc comment, replace

```
  * enemies: a grant remembered for the decision and the option lifts it, and
  * at a choose-many an option it alone forbids may be asked about (consent
  * design, slice 2).
```

with

```
  * enemies: a grant remembered for the decision and the option lifts it. An
  * option it alone forbids may be asked about at a choose-many (consent
  * design, slice 2), and at a choose-one hooked by its own window when the
  * search finds a grant would open it (slice 4).
```

- [ ] **Step 12: Run the walker suites and the whole server**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.*"`. Expected: PASS.

Run: `./sbtw test`. Expected: PASS. No card uses the window yet, so every other suite is unchanged.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala
git commit -m "feat(consent): ask about a choose-one's barred option, and preview through a pay-or-ask step"
```

---

### Task 2: Toll Roads asks to pay or travel free

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/PayOrAsk.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsConsentSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala` (`payOption`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala` (`rewrite`, `favorOwed`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala`
- Modify: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`

**Interfaces:**
- Consumes (Task 1): `PowerWindow.PayOrAsk`; the walker's choose-one asks; previews through a step.
- Produces:
  - `PayOrAsk(power: PowerId, card: DenizenId, decisionId: String, plain: NoteKey, consent: NoteKey)`, with:
    - `source: PowerSourceRef`;
    - `around(operations, payer, occasion, heading, pay: DecisionOption, freeLabel, paid, waived): Vector[Operation]`;
    - `question(payer, occasion, heading, pay: DecisionOption, freeLabel): Decide`;
    - `outcome(paid, waived): Branch`;
    - `restriction: OptionRestriction`;
    - `asking(ctx: PowerCtx): Boolean`;
    - `granter(ready): Option[PlayerId]`.
  - `PayOrAsk.pay: DecisionOptionRef.Button`, `PayOrAsk.free(occasion: String): DecisionOptionRef.Button` and `PayOrAsk.isFree(ref): Boolean`.
  - `RulerToll.payOption(ready: ReadyGame, payer: PlayerId, owner: PlayerId): DecisionOption`.
  - `TollRoads.decisionId`, `TollRoads.step`, `TollRoads.free(destination: SiteId)`, `TollRoads.waived`, `TollRoads.noFreeTravel` and `TollRoads.noFreeTravelConsent`.
  - `TravelFixture.answeringStep(from: OathTransition, decisionId: String, choice: DecisionOptionRef): Either[OathViolation, OathTransition]`.

- [ ] **Step 1: Add the test helper**

In `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala`, after `travel`, add:

```scala
  /** `from`, parked on a pay-or-ask step, continued by `actor` answering it
    * with `choice`. The events are both transitions'. */
  def answeringStep(from: OathTransition, decisionId: String,
      choice: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(choice))
      .map(done => done.copy(events = from.events ++ done.events))
```

- [ ] **Step 2: Write the failing consent tests**

Create `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsConsentSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks,
  Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Toll Roads' pay-or-ask step (consent design, "Toll and penalty steps",
  * rulings 7, 8 and 12). */
class TollRoadsConsentSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val rival = Table.p2
  private val power = TollRoads.forCatalog(catalog)
  private val card = PowerSourceRef.Card(TollRoadsCard.id)
  private val free = TollRoads.free(coast)
  private val key = ConsentKey(TollRoads.id, actor, TollRoads.decisionId, free)
  private val freeButton =
    DecisionOption.Button(free, "Travel without paying")

  /** Toll Roads at the Provinces' plains; `rival` rules it and the coast.
    * The actor, at the Cradle's plains with 1 favor, travels to the coast. */
  private val rivalRules: ReadyGame =
    on(board(supply = 7))(_.denizen(CatalogNames.denizen("Toll Roads"),
      at = provincePlains).favor(actor, 1)
      .warbandsAt(provincePlains, rival, 1).warbandsAt(coast, rival, 1))

  private def granted(ready: ReadyGame): ReadyGame = ready.copy(consent =
    ConsentState().granting(Vector(key), ConsentTurn.of(ready)))

  /** The option `ref` of the step a Travel to `destination` parks on. */
  private def offered(ready: ReadyGame, destination: SiteId,
      ref: DecisionOptionRef)(using munit.Location): DecisionOption =
    present(PlacementPicks.parkedOptions(accepted(travel(ready,
      destination)).state, powers(Vector.empty)).find(_.ref == ref),
      s"${ref.wireId} on the step")

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy of a player ruler is asked to pay, and may ask to go free"):
    assertEquals(offered(rivalRules, coast, PayOrAsk.pay),
      DecisionOption.Button(PayOrAsk.pay, s"Give 1 favor to ${rival.value}"))
    assertEquals(offered(rivalRules, coast, free), DecisionOption.Unavailable(
      freeButton, OptionReason.Askable(TollRoads.id,
        TollRoads.noFreeTravelConsent(card),
        Vector(ConsentAsk(rival, Vector(TollRoads.id))))))

  test("a remembered grant lets the traveller go free, with its line"):
    val ready = granted(rivalRules)
    val done = accepted(travel(ready, coast).flatMap(
      answeringStep(_, TollRoads.decisionId, free)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).favor(actor), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("waived",
      s"${actor.value} paid nothing, with ${rival.value}'s consent.",
      covers = false)))

  test("a remembered refusal leaves the free option its ordinary reason"):
    val refused = rivalRules.copy(consent = ConsentState(
      answers = Map(key -> ConsentAnswer.Refused),
      turn = Some(ConsentTurn.of(rivalRules))))
    assertEquals(offered(refused, coast, free), DecisionOption.Unavailable(
      freeButton, OptionReason.Noted(TollRoads.id,
        TollRoads.noFreeTravel(card))))

  test("a grant covers its destination only"):
    // With the Pass the actor's, the ruler's own site is reachable too.
    val bothOpen = granted(on(rivalRules)(_.warbandsAt(pass, actor, 1)))
    assert(offered(bothOpen, provincePlains,
      TollRoads.free(provincePlains)).askable)
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.TollRoadsConsentSuite"`

Expected: compilation FAIL: `PayOrAsk` and `TollRoads.free` are not defined.

- [ ] **Step 4: Write `PayOrAsk`**

Create `src/main/scala/oathdigital/gameplay/powers/PayOrAsk.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx,
  Waiver}
import oathdigital.model._

/** A toll or penalty a card's ruling player may waive (consent design, "Toll
  * and penalty steps"). When a player other than the payer rules the card,
  * the payer is asked to pay or to go on without paying. The free option is
  * barred by the card's waivable restriction: it is askable until the ruler
  * answers, and choosable once they consent. The step is hooked by
  * [[PowerWindow.PayOrAsk]], where only that restriction speaks.
  *
  * The free option's key names the occasion (the destination, the pawn's
  * site, the traded card, the site left), so a grant covers that occasion
  * for the rest of the turn (ruling 12), not every toll the card charges.
  */
final case class PayOrAsk(power: PowerId, card: DenizenId,
    decisionId: String, plain: NoteKey, consent: NoteKey):
  val source: PowerSourceRef = PowerSourceRef.Card(card)

  /** `operations` with the step `payer` answers on `occasion` first and what
    * its answer runs last: `paid` when they pay, `waived` when they go on
    * free. Every question of a cost node is asked before any of it is paid,
    * because a resume folds the node again against the state it parked in,
    * and a payment made before a later question would change what that fold
    * reads: Way Station decides whether to ask from the favor held. */
  def around(operations: Vector[Operation], payer: PlayerId,
      occasion: String, heading: String, pay: DecisionOption,
      freeLabel: String, paid: Vector[Operation],
      waived: Vector[Operation]): Vector[Operation] =
    question(payer, occasion, heading, pay, freeLabel) +: operations :+
      outcome(paid, waived)

  /** The step: `pay`, which is unavailable with its reason when the payer
    * cannot pay, or the free option. */
  def question(payer: PlayerId, occasion: String, heading: String,
      pay: DecisionOption, freeLabel: String): Decide =
    Decide(decisionId, payer, DecisionQuery.ChooseOne(Vector(pay,
      DecisionOption.Button(PayOrAsk.free(occasion), freeLabel)),
      heading = Some(heading)), window = Some(PowerWindow.PayOrAsk),
      presentation = DecisionPresentation.Panel)

  /** What the step's answer runs. */
  def outcome(paid: Vector[Operation], waived: Vector[Operation]): Branch =
    Branch((_, pending) => PowerAnswers.one(pending, decisionId) match
      case Some(PayOrAsk.pay) => paid
      case Some(_) => waived
      case None => Vector.empty)

  /** Bars the free option of this card's step until its ruler consents. */
  lazy val restriction: OptionRestriction = OptionRestriction(
    (ctx, ref) => Option.when(barred(ctx, ref))(
      OathViolation.ConsentUnavailable(
        s"${power.value} is owed unless its ruler consents")),
    (ctx, ref) => Option.when(barred(ctx, ref))(plain(source)),
    Some(Waiver(granter, source, (_, _) => consent(source))))

  /** Whether `ctx` is gathered at this card's step. */
  def asking(ctx: PowerCtx): Boolean = ctx.operation match
    case decide: Decide => decide.decisionId == decisionId
    case _ => false

  /** The player ruling the card; none under bandits. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    SiteRulers.rulerOfCard(ready, card).collect {
      case SiteRuler.Player(player) => player }

  private def barred(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    asking(ctx) && PayOrAsk.isFree(ref)

object PayOrAsk:
  val pay: DecisionOptionRef.Button = DecisionOptionRef.Button("pay")

  /** The free option of the step asked on `occasion`. */
  def free(occasion: String): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"free.$occasion")

  def isFree(ref: DecisionOptionRef): Boolean = ref match
    case DecisionOptionRef.Button(key) => key.startsWith("free.")
    case _ => false
```

- [ ] **Step 5: Add the paying option to `RulerToll`**

In `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala`, after `enemyRuler`, add:

```scala
  /** The paying option of a toll step, "Give 1 favor to {owner}",
    * unavailable with what it needs when `payer` holds less than the toll.
    * The step is asked before any of its cost is paid, so the favor held
    * now is the favor the payment will find. */
  def payOption(ready: ReadyGame, payer: PlayerId, owner: PlayerId)
      : DecisionOption =
    val option = DecisionOption.Button(PayOrAsk.pay,
      s"Give $Favor favor to ${owner.value}")
    val held = ready.game.current.players.find(_.player == payer)
      .fold(0)(_.board.favor)
    if held >= Favor then option
    else DecisionOption.Unavailable(option,
      OptionReason.Written(s"Needs $Favor favor; you have $held"))
```

- [ ] **Step 6: Convert Toll Roads**

In `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala`:

1. Change the import `import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}` to `import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport, PayOrAsk, RulerToll}`.

2. Replace the class's doc comment with:

```scala
/** Toll Roads (card 118), a persistent rule of a faceup site card: enemies
  * cannot travel to a site ruled by Toll Roads' ruler unless they give 1 favor
  * to that ruler, or burn it when bandits rule. Toll Roads' ruler is the ruler
  * of the site it stands at, and the rule covers every site that ruler holds,
  * Toll Roads' own included. Empire rulers are not supported.
  *
  * When a player rules it, the traveller is asked first to pay or to travel
  * without paying, which its ruler may consent to ([[PayOrAsk]], consent
  * slice 4). Under bandits the favor is burnt, unasked. The payment is a
  * required operation placed after the rest of the Travel's cost and before
  * the pawn's move, so a traveller who cannot pay and is not let off is
  * rejected. The ruler is exempt.
  *
  * The payment writes "{Red} paid 1 favor to {Blue}.", or "{Red} burned 1
  * favor." when bandits rule, read from the payment's step. Travelling free
  * writes "{Red} paid nothing, with {Blue}'s consent."
  */
```

3. Replace `noteKeys`, `contributions` and `applicable` with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(TollRoads.paid,
    TollRoads.burned, TollRoads.waived, TollRoads.noFreeTravel,
    TollRoads.noFreeTravelConsent)
  override def narratedDecisions: Set[String] = Set(TollRoads.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(TollRoads.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then TollRoads.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty
```

4. Replace `toll` (with its doc comment) with:

```scala
  /** `operations` with what this Travel owes, if it owes anything: the step
    * when a player rules Toll Roads, the payment when bandits do. Either way
    * the payment runs after the rest of the cost ([[PayOrAsk.around]]). */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  yield
    val paid = Vector(payment(route.player, ruler),
      Note(id, paidNote(route.player, ruler)))
    ruler match
      case SiteRuler.Player(owner) => TollRoads.step.around(operations,
        route.player, route.destination.value,
        "Toll Roads: give 1 favor to travel there?",
        RulerToll.payOption(ctx.state, route.player, owner),
        "Travel without paying", paid,
        Vector(Note(id, _ => Some(TollRoads.waived(PowerSourceRef.Card(cardId),
          NoteArg.Player(route.player), NoteArg.Player(owner))))))
      case _ => operations ++ paid
```

5. In the `TollRoads` companion, after `burned`, add:

```scala
  val decisionId: String = "power.toll-roads.toll"
  /** "{Red} paid nothing, with {Blue}'s consent." */
  val waived: NoteKey = NoteKey("waived", Vector(NotePart.Arg(0),
    NotePart.Text(" paid nothing, with "), NotePart.Arg(1),
    NotePart.Text("'s consent.")))
  /** "Cannot travel to its ruler's sites without giving favor.": the reason
    * of the free option once its ruler refused. */
  val noFreeTravel: NoteKey = NoteKey("no-free-travel", Vector(NotePart.Text(
    "Cannot travel to its ruler's sites without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeTravelConsent: NoteKey = NoteKey("no-free-travel-consent",
    Vector(NotePart.Text("Cannot travel to its ruler's sites without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, TollRoadsCard.id, decisionId,
    noFreeTravel, noFreeTravelConsent)

  /** The free option of a Travel to `destination`. */
  def free(destination: SiteId): DecisionOptionRef.Button =
    PayOrAsk.free(destination.value)
```

- [ ] **Step 7: Ask Way Station first and pay it last, and count the step's favor**

In `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala`:

1. In the class's doc comment, replace

```
  * pawn's move stays outside the question, so later windows still see the
  * route. The question parks before the move, and Travel's source is a start
  * fact, so a resume rebuilds the same tree.
```

with

```
  * pawn's move stays outside the question, so later windows still see the
  * route. The question parks before the move, and Travel's source is a start
  * fact, so a resume rebuilds the same tree. Its payment runs after the rest
  * of the cost, so every question of the cost, a toll's step included, is
  * asked before any of it is paid (`PayOrAsk.around`).
```

2. In `rewrite`, replace the final `else` expression

```scala
      Vector[Operation](
        Decide(WayStation.decisionId, traveller, DecisionQuery.ChooseOne(
          options, heading = Some("Way Station: spend no Supply?")),
          presentation = DecisionPresentation.Panel),
        Branch((_, pending) =>
          PowerAnswers.one(pending, WayStation.decisionId) match
            case Some(WayStation.favorOption) => Vector(
              RulerToll.payment(traveller, ruler),
              Note(id, RulerToll.note(cardId, traveller, ruler)))
            case Some(_) => payment
            case None => Vector.empty)) ++
        SupplyStrip(traveller, operations)
```

with

```scala
      Vector[Operation](
        Decide(WayStation.decisionId, traveller, DecisionQuery.ChooseOne(
          options, heading = Some("Way Station: spend no Supply?")),
          presentation = DecisionPresentation.Panel)) ++
        SupplyStrip(traveller, operations) :+
        Branch((_, pending) =>
          PowerAnswers.one(pending, WayStation.decisionId) match
            case Some(WayStation.favorOption) => Vector(
              RulerToll.payment(traveller, ruler),
              Note(id, RulerToll.note(cardId, traveller, ruler)))
            case Some(_) => payment
            case None => Vector.empty)
```

3. Replace `favorOwed` and its doc comment with:

```scala
  /** The favor `operations` already take from `traveller` (a toll, a
    * selected modifier's payment), which the favor answer must leave. Toll
    * Roads' step counts as owed, though its ruler may waive it. */
  private[travel] def favorOwed(operations: Vector[Operation],
      traveller: PlayerId): Int = operations.collect {
    case Give(Piece.Favor(amount), giver, _, _, _) if giver == traveller =>
      amount
    case PayCost(player, _, cost, _, _, _) if player == traveller =>
      cost.favor + cost.favorBurnt
    case step: Decide if step.decisionId == TollRoads.decisionId &&
        step.owner == traveller => TollRoads.Favor
  }.sum
```

- [ ] **Step 8: Run the consent suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.TollRoadsConsentSuite"`

Expected: PASS, 4 tests.

- [ ] **Step 9: Update the Toll Roads and Way Station suites**

In `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`:

1. Change the import `import oathdigital.gameplay.powers.{NoteText, PowerFixture}` to `import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture}`.

2. After `said`, add:

```scala
  /** A Travel to `destination`, with Toll Roads' step answered by paying. */
  private def travelledPaying(ready: ReadyGame, destination: SiteId)
      : Either[OathViolation, OathTransition] =
    travel(ready, destination).flatMap(
      answeringStep(_, TollRoads.decisionId, PayOrAsk.pay))
```

3. In these three tests, replace `travel(` with `travelledPaying(`:
   - "an enemy pays the ruler 1 favor to travel to a site the ruler rules"
   - "the rule covers every site the ruler holds, Toll Roads' own included"
   - "the toll paid to the ruler is written as the Roads' line"

   Leave every other test as it is. A broke traveller is still refused until Task 5. A site the ruler does not rule, the ruler's own Travel and bandits get no step.

In `src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala`:

1. Change the import `import oathdigital.gameplay.powers.{NoteText, PowerFixture, RulerToll}` to `import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture, RulerToll}`.

2. Replace the last two tests with:

```scala
  test("a favor the toll takes first is not offered twice"):
    val stepped = accepted(travel(tolled(favor = 1), coast))
    // Not asked: the toll's step owes the only favor.
    parked.assertParked(stepped.state, ActionRef.Travel, TollRoads.decisionId,
      actor)
    val done = accepted(answeringStep(stepped, TollRoads.decisionId,
      PayOrAsk.pay))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 6)
    assertEquals(Look(result).favor(actor), 0)

  test("with favor for both, both questions are asked before either is " +
      "paid, and the answers pay the toll and the Way Station"):
    val asked = accepted(travel(tolled(favor = 2), coast))
    assertAsked(asked.state)
    val stepped = answer(asked.state, WayStation.favorOption)
    // Still 2 favor: the Way Station pays last.
    assertEquals(Look(Outcomes.ready(stepped.state)).favor(actor), 2)
    val done = accepted(answeringStep(stepped, TollRoads.decisionId,
      PayOrAsk.pay))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
```

- [ ] **Step 10: Pin the badge of a granted toll**

In `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`, change the import `import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}` to `import oathdigital.gameplay.powers.travel.{Tents, TollRoads, TravelFixture}`. After the test "a toll adds its favor to the badge", add:

```scala
  test("a toll its ruler waived reads both ways on"):
    val tolled = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 1).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    val granted = tolled.copy(consent = ConsentState().granting(Vector(
      ConsentKey(TollRoads.id, actor, TollRoads.decisionId,
        TollRoads.free(coast))), ConsentTurn.of(tolled)))
    assertEquals(row(granted, coast).badge,
      Some("1 Supply · 1 favor or 1 Supply"))
```

- [ ] **Step 11: Run the travel suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.application.TravelDestinationRowsSuite"`

Expected: PASS. The existing toll badges ("1 Supply · 1 favor", "1 Supply · 1 favor or 2 favor") and "Needs 1 favor; you have 0" keep their text, because the previews follow the step.

- [ ] **Step 12: Run the whole server**

Run: `./sbtw test`

Expected: PASS. If a suite outside this task's list fails because a Travel now parks on Toll Roads' step, answer the step with `PayOrAsk.pay` there the way Step 9 does, and list the suite in the commit.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PayOrAsk.scala \
  src/main/scala/oathdigital/gameplay/powers/RulerToll.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala \
  src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala
git commit -m "feat(consent): Toll Roads asks an enemy to pay or travel free with its ruler's consent"
```

---

### Task 3: Forced Labor and Curfew ask to pay or go free

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (doc only)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala` (a test's name)

**Interfaces:**
- Consumes (Task 2): `PayOrAsk` (`around`, `question`, `outcome`), `PayOrAsk.pay`, `PayOrAsk.free` and `RulerToll.payOption`.
- Produces:
  - `RulerToll.waived: NoteKey`, now also in `RulerToll.keys`.
  - `RulerToll.owed(step: PayOrAsk, ready: ReadyGame, payer: PlayerId, ruler: SiteRuler, operations: Vector[Operation], occasion: String, heading: String, freeLabel: String): Vector[Operation]`.
  - `ForcedLabor.decisionId`, `ForcedLabor.step`, `ForcedLabor.free(site: SiteId)`, `ForcedLabor.noFreeSearch` and `ForcedLabor.noFreeSearchConsent`.
  - `Curfew.decisionId`, `Curfew.step`, `Curfew.free(source: DecisionOptionRef)`, `Curfew.noFreeTrade` and `Curfew.noFreeTradeConsent`.
  - In `ForcedLaborSuite` and `CurfewSuite`, for Task 5: the suite-level `automatic` powers.

- [ ] **Step 1: Write the failing tests**

In `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`:

1. Change these imports:
   - `import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}` to `import oathdigital.gameplay.powers.{NoteText, PayOrAsk, SearchFixture, WalkerPowerCatalog}`;
   - `import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}` to `import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, PlacementPicks, Table}`;
   - `import oathdigital.testkit.Unwrap.accepted` to `import oathdigital.testkit.Unwrap.{accepted, present}`.

2. After `said`, add:

```scala
  private val automatic = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** A Search of the world deck, with Forced Labor's step answered with
    * `choice`. */
  private def searchedAnswering(ready: ReadyGame, choice: DecisionOptionRef)(
      using munit.Location): OathTransition =
    val started = accepted(SearchFixture.start(ready))
    val done = accepted(rules.resolveWalker(started.state, p1,
      ForcedLabor.decisionId, DecisionAnswer.ChooseOneAnswer(choice)))
    done.copy(events = started.events ++ done.events)
```

3. In the test "an enemy whose pawn stands at a site the ruler rules gives the ruler 1 favor on top of the Search's Supply", replace `val started = accepted(SearchFixture.start(ready))` with `val started = searchedAnswering(ready, PayOrAsk.pay)`.

4. In the test "a searcher who cannot pay is not offered the Search", delete the two lines that define the local `automatic`:

```scala
    val automatic = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
```

The suite-level `automatic` replaces them.

5. Append:

```scala
  test("an enemy of a player ruler may ask to search without paying; once " +
      "granted, it gives nothing"):
    val ready = rivalRulesBoth.ready
    val free = ForcedLabor.free(home)
    val asked = present(PlacementPicks.parkedOptions(
      accepted(SearchFixture.start(ready)).state, automatic)
      .find(_.ref == free), "the free option")
    assertEquals(asked, DecisionOption.Unavailable(
      DecisionOption.Button(free, "Search without paying"),
      OptionReason.Askable(ForcedLabor.id, ForcedLabor.noFreeSearchConsent(
        PowerSourceRef.Card(ForcedLaborCard.id)),
        Vector(ConsentAsk(p2, Vector(ForcedLabor.id))))))
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(ForcedLabor.id, p1, ForcedLabor.decisionId, free)),
      ConsentTurn.of(ready)))
    val done = searchedAnswering(granted, free)
    assertEquals(Look(SearchFixture.after(done)).favor(p1),
      Look(ready).favor(p1))
    assertEquals(said(done.events), Vector(NoteText.Said("waived",
      s"Gave nothing, with ${p2.value}'s consent.", covers = false)))
```

In `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`:

1. Change these imports, and add one:
   - `import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}` to `import oathdigital.gameplay.powers.{NoteText, PayOrAsk, WalkerPowerCatalog}`;
   - `import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions, Look, Table}` to `import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions, Look, Outcomes, PlacementPicks, Table}`;
   - add `import oathdigital.testkit.Unwrap.{accepted, present}`.

2. After `said`, add:

```scala
  /** The Trade for favor with Wrestlers, with Curfew's step answered with
    * `choice`: the events of both, and the state after. */
  private def tradedAnswering(ready: ReadyGame, choice: DecisionOptionRef)(
      using munit.Location): (Vector[OathEvent], ReadyGame) =
    val traded = tradedForFavorWithWrestlers(ready)
    val done = accepted(rules.resolveWalker(traded.journal.state, p1,
      Curfew.decisionId, DecisionAnswer.ChooseOneAnswer(choice)))
    (traded.journal.events ++ done.events, Outcomes.ready(done.state))
```

3. Replace the first test, "an enemy trading with a card the ruler rules gives the ruler 1 favor on top of the Trade's cost", with:

```scala
  test("an enemy trading with a card the ruler rules gives the ruler 1 favor " +
      "on top of the Trade's cost"):
    val ready = rivalRulesBoth.ready
    val (events, result) = tradedAnswering(ready, PayOrAsk.pay)
    val end = Look(result)
    // The start's 1 favor went to p2, then the Trade gained 1 Order favor.
    assertEquals(end.favor(p1), 1)
    assertEquals(end.favor(p2), Look(ready).favor(p2) + 1)
    assertEquals(end.bankFavor(Suit.Order), 3 - 1)
    assertEquals(said(events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${p2.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, events), result)
    assertWireRoundTrips(events)
```

4. Append:

```scala
  test("an enemy of a player ruler may ask to trade without paying; once " +
      "granted, it gives nothing"):
    val ready = rivalRulesBoth.ready
    val free = Curfew.free(DecisionOptionRef.Denizen(wrestlers))
    val asked = present(PlacementPicks.parkedOptions(
      tradedForFavorWithWrestlers(ready).journal.state, automatic)
      .find(_.ref == free), "the free option")
    assertEquals(asked, DecisionOption.Unavailable(
      DecisionOption.Button(free, "Trade without paying"),
      OptionReason.Askable(Curfew.id, Curfew.noFreeTradeConsent(
        PowerSourceRef.Card(CurfewCard.id)),
        Vector(ConsentAsk(p2, Vector(Curfew.id))))))
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(Curfew.id, p1, Curfew.decisionId, free)),
      ConsentTurn.of(ready)))
    val (events, result) = tradedAnswering(granted, free)
    assertEquals(Look(result).favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(said(events), Vector(NoteText.Said("waived",
      s"Gave nothing, with ${p2.value}'s consent.", covers = false)))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.ForcedLaborSuite oathdigital.gameplay.powers.economy.CurfewSuite"`

Expected: compilation FAIL: `ForcedLabor.decisionId`, `ForcedLabor.free`, `Curfew.decisionId` and `Curfew.free` are not defined.

- [ ] **Step 3: Add the shared toll step to `RulerToll`**

In `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala`:

1. In the object's doc comment, replace

```
  * The payment is required, so an enemy who cannot pay is refused. Toll
  * Roads charges the same toll for Travel, with lines that name the
  * traveller.
```

with

```
  * The payment is required, so an enemy who cannot pay, and is not let off,
  * is refused. When a player rules the card, the payer is asked first to
  * pay or to go on free, which that ruler may consent to ([[PayOrAsk]]).
  * The payment runs after the rest of the cost either way. Toll Roads
  * charges the same toll for Travel, with lines that name the traveller.
```

2. Replace `val keys: Vector[NoteKey] = Vector(gave, burned)` with:

```scala
  /** "Gave nothing, with {Blue}'s consent.", when the ruler waived it. */
  val waived: NoteKey = NoteKey("waived", Vector(
    NotePart.Text("Gave nothing, with "), NotePart.Arg(0),
    NotePart.Text("'s consent.")))
  val keys: Vector[NoteKey] = Vector(gave, burned, waived)

  /** `operations` with what `payer` owes `ruler` for `step`'s card on
    * `occasion`: the step when a player rules it, the payment and its line
    * under bandits, run after the rest of the cost either way. */
  def owed(step: PayOrAsk, ready: ReadyGame, payer: PlayerId,
      ruler: SiteRuler, operations: Vector[Operation], occasion: String,
      heading: String, freeLabel: String): Vector[Operation] =
    val paid = Vector(payment(payer, ruler),
      Note(step.power, note(step.card, payer, ruler)))
    ruler match
      case SiteRuler.Player(owner) => step.around(operations, payer,
        occasion, heading, payOption(ready, payer, owner), freeLabel, paid,
        Vector(Note(step.power, _ => Some(waived(step.source,
          NoteArg.Player(owner))))))
      case _ => operations ++ paid
```

- [ ] **Step 4: Convert Forced Labor**

In `src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala`:

1. Change `import oathdigital.gameplay.powers.{CatalogResolution, RulerToll}` to `import oathdigital.gameplay.powers.{CatalogResolution, PayOrAsk, RulerToll}`.

2. In the class's doc comment, replace

```
  * The payment is required and comes before the Search's Supply, so a
  * searcher who cannot pay is refused and the Search is not offered. Its line
  * reads the payment's step.
```

with

```
  * The payment is required and comes after the Search's Supply, so a
  * searcher who cannot pay is refused and the Search is not offered. When a
  * player rules it, the searcher is asked first to pay or to search without
  * paying, which its ruler may consent to (consent slice 4). Its line reads
  * the payment's step.
```

3. Replace `noteKeys`, `contributions`, `applicable` and `toll` with:

```scala
  override def noteKeys: Vector[NoteKey] = RulerToll.keys ++
    Vector(ForcedLabor.noFreeSearch, ForcedLabor.noFreeSearchConsent)
  override def narratedDecisions: Set[String] = Set(ForcedLabor.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(ForcedLabor.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then ForcedLabor.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty

  /** `operations` with what this Search owes, if it owes anything. */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    ruler <- RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
    site <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
    if SiteRulers.rulerOf(ctx.state, site).contains(ruler)
  yield RulerToll.owed(ForcedLabor.step, ctx.state, ctx.activePlayer, ruler,
    operations, site.value, "Forced Labor: give 1 favor to search here?",
    "Search without paying")
```

4. In the `ForcedLabor` companion, after `val id`, add:

```scala
  val decisionId: String = "power.forced-labor.toll"
  /** "Cannot search at its ruler's sites without giving favor.": the reason
    * of the free option once its ruler refused. */
  val noFreeSearch: NoteKey = NoteKey("no-free-search", Vector(NotePart.Text(
    "Cannot search at its ruler's sites without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeSearchConsent: NoteKey = NoteKey("no-free-search-consent",
    Vector(NotePart.Text("Cannot search at its ruler's sites without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, ForcedLaborCard.id, decisionId,
    noFreeSearch, noFreeSearchConsent)

  /** The free option of a Search from `site`. */
  def free(site: SiteId): DecisionOptionRef.Button = PayOrAsk.free(site.value)
```

- [ ] **Step 5: Convert Curfew**

In `src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala`:

1. Change `import oathdigital.gameplay.powers.{CatalogResolution, PowerAnswers, RuledCards, RulerToll}` to `import oathdigital.gameplay.powers.{CatalogResolution, PayOrAsk, PowerAnswers, RuledCards, RulerToll}`.

2. In the class's doc comment, replace

```
  * The toll is a required batch placed before the Trade's cost, so a trader
  * who cannot pay is refused, and the restriction look-ahead hides that
  * source. The batch reads the Trade's source when it runs and is placed for
  * every Trade by an enemy, so the cost node's shape never depends on the
  * answer. Its line reads the payment's step.
```

with

```
  * The toll runs after the rest of the Trade's cost and reads the Trade's
  * source when it runs. Under bandits it is a required batch placed for
  * every Trade by an enemy. When a player rules Curfew, the trader is asked
  * first to pay or to trade without paying, which its ruler may consent to
  * (consent slice 4), only for a source that ruler rules. A trader who
  * cannot pay and is not let off is refused, and the restriction look-ahead
  * hides that source. Its line reads the payment's step.
```

3. Replace `noteKeys`, `contributions`, `applicable` and `toll` (with its doc comment) with:

```scala
  override def noteKeys: Vector[NoteKey] = RulerToll.keys ++
    Vector(Curfew.noFreeTrade, Curfew.noFreeTradeConsent)
  override def narratedDecisions: Set[String] = Set(Curfew.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
        .fold(operations)(charged(ctx.activePlayer, _, operations)))),
    PowerWindow.PayOrAsk -> Vector(Curfew.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then Curfew.step.asking(ctx)
    else RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer).nonEmpty

  /** `operations` with what a Trade with a card `ruler` rules owes, read
    * against the Trade's source when it runs: the step first and its outcome
    * last when a player rules Curfew, the payment and its line last under
    * bandits. */
  private def charged(trader: PlayerId, ruler: SiteRuler,
      operations: Vector[Operation]): Vector[Operation] = ruler match
    case SiteRuler.Player(owner) =>
      val paid = Vector(RulerToll.payment(trader, ruler),
        Note(id, RulerToll.note(cardId, trader, ruler)))
      val waived = Vector(Note(id, _ => Some(RulerToll.waived(
        Curfew.step.source, NoteArg.Player(owner)))))
      Branch((ready, pending) => tradedWith(ready, ruler, pending).toVector
        .map(source => Curfew.step.question(trader, source.wireId,
          "Curfew: give 1 favor to trade with this card?",
          RulerToll.payOption(ready, trader, owner),
          "Trade without paying"))) +: operations :+
        Branch((ready, pending) => tradedWith(ready, ruler, pending).toVector
          .map(_ => Curfew.step.outcome(paid, waived)))
    case _ => operations ++ Vector(
      BuildOps((ready, pending) => Right(tradedWith(ready, ruler, pending)
        .map(_ => RulerToll.payment(trader, ruler)).toVector),
        required = true),
      Note(id, RulerToll.note(cardId, trader, ruler)))

  /** The Trade's source, when it is a card `ruler` rules. */
  private def tradedWith(ready: ReadyGame, ruler: SiteRuler,
      pending: PendingTree): Option[DecisionOptionRef] =
    PowerAnswers.one(pending, TradeProcedure.decisionId).filter(ref =>
      cardOf(ref).exists(ruled(ready, ruler, _)))
```

4. In the `Curfew` companion, after `val id`, add:

```scala
  val decisionId: String = "power.curfew.toll"
  /** "Cannot trade with its ruler's cards without giving favor.": the
    * reason of the free option once its ruler refused. */
  val noFreeTrade: NoteKey = NoteKey("no-free-trade", Vector(NotePart.Text(
    "Cannot trade with its ruler's cards without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeTradeConsent: NoteKey = NoteKey("no-free-trade-consent",
    Vector(NotePart.Text("Cannot trade with its ruler's cards without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, CurfewCard.id, decisionId, noFreeTrade,
    noFreeTradeConsent)

  /** The free option of a Trade with `source`. */
  def free(source: DecisionOptionRef): DecisionOptionRef.Button =
    PayOrAsk.free(source.wireId)
```

- [ ] **Step 6: Run the two suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.ForcedLaborSuite oathdigital.gameplay.powers.economy.CurfewSuite"`

Expected: PASS.

- [ ] **Step 7: Rename the log test whose toll now follows the Supply**

Forced Labor's toll now runs after the Search's Supply, so the log script's story changes.

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, in the doc comment of `forcedLaborSearch`, replace

```
    * Ancient City, where p1 stands: p1's Search of the world deck gives p2
    * 1 favor before it spends 2 Supply. p1 holds 5 Supply and the start's
    * 1 favor. */
```

with

```
    * Ancient City, where p1 stands: p1's Search of the world deck spends 2
    * Supply and, its step answered by paying, gives p2 1 favor. p1 holds 5
    * Supply and the start's 1 favor. */
```

In `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`, rename the test

```scala
  test("a Search whose first recorded step is Forced Labor's toll opens " +
      "with one tag above the toll, carrying the Supply spent after it"):
```

to

```scala
  test("a Search that pays Forced Labor's toll opens with one tag above " +
      "the toll, carrying the Supply spent before it"):
```

Leave its body as it is.

- [ ] **Step 8: Run the suites that read Forced Labor's toll**

Run: `./sbtw "testOnly oathdigital.application.SearchSourceRowsSuite oathdigital.application.gamelog.GameLogStartLineSuite"`

Expected: PASS.
- "Forced Labor's toll joins the badge of both piles" keeps `2 Supply · 1 favor`, because the previews follow the step.
- The log script answers every park with its first available option, which is the paying one. The "Search −2 Supply" tag still comes first, and the toll line follows it.

If the start-line test fails, stop and report the log's lines. Do not change the test's assertions.

- [ ] **Step 9: Run the whole server**

Run: `./sbtw test`

Expected: PASS. If a suite outside this task's list fails because a Search or a Trade now parks on the step, answer the step with `PayOrAsk.pay` there, and list the suite in the commit.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/RulerToll.scala \
  src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala \
  src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
git commit -m "feat(consent): Forced Labor and Curfew ask an enemy to pay or go free with their ruler's consent"
```

---

### Task 4: Grasping Vines asks to kill or travel free

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/GraspingVinesSuite.scala`

**Interfaces:**
- Consumes (Task 2): `PayOrAsk.around`, `PayOrAsk.pay`, `PayOrAsk.free` and `TravelFixture.answeringStep`.
- Produces: `GraspingVines.decisionId`, `GraspingVines.step`, `GraspingVines.free(site: SiteId)`, `GraspingVines.spared`, `GraspingVines.mustKill` and `GraspingVines.mustKillConsent`.

- [ ] **Step 1: Write the failing tests**

In `src/test/scala/oathdigital/gameplay/powers/travel/GraspingVinesSuite.scala`:

1. Change these imports:
   - `import oathdigital.gameplay.powers.{NoteText, PowerFixture}` to `import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture}`;
   - `import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes, Table}` to `import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes, PlacementPicks, Table}`;
   - `import oathdigital.testkit.Unwrap.accepted` to `import oathdigital.testkit.Unwrap.{accepted, present}`.

2. After `said`, add:

```scala
  /** A Travel to `destination`, with the Vines' step answered by killing. */
  private def travelledKilling(ready: ReadyGame, destination: SiteId)
      : Either[OathViolation, OathTransition] =
    travel(ready, destination).flatMap(
      answeringStep(_, GraspingVines.decisionId, PayOrAsk.pay))
```

3. Replace `travel(` with `travelledKilling(` in:
   - the test "an enemy traveling from a site the ruler rules kills a warband of their own";
   - the `out` line of "only the site the traveller leaves counts, not the destination";
   - the test "the kill is written as the Vines' line, naming the traveller".

   Leave the bandit, ruler, no-warband and facedown tests as they are: none of them gets a step.

4. Append:

```scala
  test("an enemy of a player ruler may ask to travel without killing; once " +
      "granted, no warband dies"):
    val ready = vinesAtHome(Some(rival))
    val free = GraspingVines.free(cradlePlains)
    val asked = present(PlacementPicks.parkedOptions(
      accepted(travel(ready, coast)).state, powers(Vector.empty))
      .find(_.ref == free), "the free option")
    assertEquals(asked, DecisionOption.Unavailable(
      DecisionOption.Button(free, "Travel without killing"),
      OptionReason.Askable(GraspingVines.id, GraspingVines.mustKillConsent(
        PowerSourceRef.Card(GraspingVinesCard.id)),
        Vector(ConsentAsk(rival, Vector(GraspingVines.id))))))
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(GraspingVines.id, actor, GraspingVines.decisionId, free)),
      ConsentTurn.of(ready)))
    val done = accepted(travel(granted, coast).flatMap(
      answeringStep(_, GraspingVines.decisionId, free)))
    assertEquals(warbands(Outcomes.ready(done.state)), warbands(ready))
    assertEquals(said(done.events), Vector(NoteText.Said("spared",
      s"Killed nothing, with ${rival.value}'s consent.", covers = false)))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.GraspingVinesSuite"`

Expected: compilation FAIL: `GraspingVines.decisionId` and `GraspingVines.free` are not defined.

- [ ] **Step 3: Convert Grasping Vines**

In `src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala`:

1. Change `import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}` to `import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport, PayOrAsk}`.

2. In the class's doc comment, replace

```
  * The kill is unconditional and not required: it is a plain `Kill` placed
  * before the pawn's move, which does nothing for a board with no warband, so a
  * traveller with none is not stopped.
```

with

```
  * The kill is not required: it is a plain `Kill` placed after the rest of
  * the Travel's cost and before the pawn's move, which does nothing for a
  * board with no warband, so a traveller with none is not stopped. When a
  * player rules the Vines, a traveller with a warband is asked first to kill
  * it or to travel without killing, which its ruler may consent to (consent
  * slice 4).
```

and after the line `  * traveller with no warband reads nothing.`, add:

```
  * Travelling free writes "Killed nothing, with {Blue}'s consent."
```

3. Replace `noteKeys`, `contributions`, `applicable` and `kill` (with its doc comment) with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(GraspingVines.killed,
    GraspingVines.spared, GraspingVines.mustKill,
    GraspingVines.mustKillConsent)
  override def narratedDecisions: Set[String] = Set(GraspingVines.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(GraspingVines.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then GraspingVines.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty

  /** `operations` with what this Travel owes, if it owes anything: the step
    * when a player rules the Vines, the kill when bandits do. Either way the
    * kill runs after the rest of the cost ([[PayOrAsk.around]]). */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  yield
    val killed = Vector(Kill(warband,
      PositionedLocation(Location.PlayArea(route.player))),
      Note(id, NoteSupport.killedNote(GraspingVines.killed,
        PowerSourceRef.Card(cardId), route.player)))
    ruler match
      case SiteRuler.Player(owner) => GraspingVines.step.around(operations,
        route.player, route.source.value,
        "Grasping Vines: kill 1 warband to travel on?",
        DecisionOption.Button(PayOrAsk.pay, "Kill 1 warband"),
        "Travel without killing", killed,
        Vector(Note(id, _ => Some(GraspingVines.spared(
          PowerSourceRef.Card(cardId), NoteArg.Player(owner))))))
      case _ => operations ++ killed
```

4. In the `GraspingVines` companion, after `val killed`, add:

```scala
  val decisionId: String = "power.grasping-vines.kill"
  /** "Killed nothing, with {Blue}'s consent." */
  val spared: NoteKey = NoteKey("spared", Vector(
    NotePart.Text("Killed nothing, with "), NotePart.Arg(0),
    NotePart.Text("'s consent.")))
  /** "Must kill a warband when traveling from its ruler's sites.": the
    * reason of the free option once its ruler refused. */
  val mustKill: NoteKey = NoteKey("must-kill", Vector(NotePart.Text(
    "Must kill a warband when traveling from its ruler's sites.")))
  /** The free option's reason while its ruler may be asked. */
  val mustKillConsent: NoteKey = NoteKey("must-kill-consent", Vector(
    NotePart.Text("Must kill a warband when traveling from its ruler's " +
      "sites, unless ruler consents.")))
  val step: PayOrAsk = PayOrAsk(id, GraspingVinesCard.id, decisionId,
    mustKill, mustKillConsent)

  /** The free option of a Travel out of `site`. */
  def free(site: SiteId): DecisionOptionRef.Button = PayOrAsk.free(site.value)
```

- [ ] **Step 4: Run the Vines suite, then the whole server**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.GraspingVinesSuite"`. Expected: PASS.

Run: `./sbtw test`. Expected: PASS. If a suite outside this task's list fails because a Travel now parks on the Vines' step, answer the step with `PayOrAsk.pay` there, and list the suite in the commit.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/GraspingVinesSuite.scala
git commit -m "feat(consent): Grasping Vines asks an enemy to kill or travel free with its ruler's consent"
```

---

### Task 5: A payer who cannot pay may still ask

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`, `reach`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (`previewParked`)
- Test: `src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`, `TollRoadsConsentSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`
- Test: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`

**Interfaces:**
- Consumes (Tasks 1 to 4):
  - `WalkerPowerGather.optionAsks` and `WalkerSearch.reasoned`.
  - The helpers and values `ChooseOneConsentSuite` defines in Task 1.
  - `RulerToll.payOption`, which already marks a short payer's paying option unavailable.
- Produces:
  - A search stops at a step whose only way on is an askable option.
  - `narrowAt` keeps such a step.
  - `previewParked` previews an unpayable step's askable options as if granted.

- [ ] **Step 1: Write the failing walker tests**

In `src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala`, add the import `import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome, PreviewedOption}`, then append:

```scala
  private val unpayable = DecisionOption.Unavailable(payButton,
    OptionReason.Written("Needs more"))
  private val go = DecisionOptionRef.Button("go")

  /** A first decision whose one answer leads on to `next`. */
  private def behind(next: Operation): Operation = Sequence(Vector[Operation](
    Decide("go", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(go, "Go")))), next))

  test("a step its payer cannot pay is still reached, to ask"):
    val powers = WalkerPowers(Vector(toll))
    val action = behind(step(unpayable))
    val stepped = Outcomes.parked(ProcedureWalker.resolve(ready, action,
      parkedAt(ready, action, powers),
      Answered("go", DecisionAnswer.ChooseOneAnswer(go), p1), powers)).tree
    assertEquals(ProcedureWalker.openDecisions(ready, action, stepped,
      powers).head.query, DecisionQuery.ChooseOne(Vector(unpayable,
        DecisionOption.Unavailable(freeButton, askable))))

  test("a step whose paying option fails the search is kept for the ask"):
    // Paying then costs 99 Supply, which p1 does not have.
    val powers = WalkerPowers(Vector(toll))
    val action = step(afterPay = Vector(SpendSupply(p1, 99)))
    assertEquals(ProcedureWalker.openDecisions(ready, action,
      parkedAt(ready, action, powers), powers).head.query,
      DecisionQuery.ChooseOne(Vector(
        DecisionOption.Unavailable(freeButton, askable))))

  test("after a refusal, a step nobody can pay is not reached"):
    assert(ProcedureWalker.advance(refused, behind(step(unpayable)), None,
      WalkerPowers(Vector(toll))).isLeft)

  test("a preview reads an unpayable step's free way on as if granted"):
    val powers = WalkerPowers(Vector(toll))
    val action = behind(step(unpayable,
      afterFree = Vector(SpendSupply(p1, 1))))
    assertEquals(WalkerSimulation.previewParked(ready, action,
      parkedAt(ready, action, powers), powers, followUp = true)
      .map(_.map(_.outcome)), Right(Vector(Right(PreviewOutcome(Vector.empty,
        complete = false, next = Vector(PreviewedOption(
          DecisionOption.Unavailable(freeButton, askable),
          Right(PreviewOutcome(Vector(SpendSupply(p1, 1)),
            complete = true)))))))))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ChooseOneConsentSuite"`

Expected: FAIL.
- "a step its payer cannot pay is still reached" fails in `parkedAt`. The search finds no answer at the step, so "go" has nothing left and the advance is refused.
- "a step whose paying option fails the search" fails in `parkedAt`, for the same reason at the step itself.
- "a preview reads an unpayable step's free way on" fails in `parkedAt` too.
- "after a refusal" already passes.

- [ ] **Step 3: Let the search stop where an option may be asked about**

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace `reach` and its doc comment with:

```scala
  /** A search at a decision it reaches. Back at a decision it already stands
    * at in the same state, it stops: the loop leads on exactly when the first
    * visit does, and the first visit tries the other answers. A decision no
    * answer leads on from is still a stop when one of its options may be
    * asked about and a grant would open it: a payer who cannot pay may ask
    * to go on free (consent slice 4). */
  private def reach(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Step] =
    if ctx.visited((path, ctx.state)) then Right(Stopped(ctx))
    else WalkerSearch.reach(decide, verdict(decide, ctx, path)) match
      case Left(refused) =>
        if asksAt(decide, ctx, path).nonEmpty then Right(Stopped(ctx))
        else Left(refused)
      case Right(WalkerSearch.Reach.Skipped) => Right(Done(ctx))
      case Right(WalkerSearch.Reach.Answerable) => Right(Stopped(ctx))

  /** The asks `decide`'s own window allows, each kept only where a grant
    * would open its option. */
  private def asksAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Map[DecisionOptionRef, OptionReason] =
    WalkerPowerGather.optionAsks(decide, ctx.state, ctx.activePlayer,
      ctx.powers, path, ctx.procedure, ctx.answered,
      grantable(decide, ctx, path)).toMap
```

Add `OptionReason` to the `oathdigital.model` import if it is not there.

- [ ] **Step 4: Keep a step the walk reaches only to ask**

In the same file, replace `narrowAt` (Task 1's version) with:

```scala
  /** `decide` narrowed to the options from which some path reaches a legal
    * end (global operation restrictions design, "Lazy pruning"), each hidden
    * option a restriction's note explains kept as unavailable (the note is
    * its hover reason and nothing is journaled), or as declared when the
    * powers do not search. An option its own window's waivable restriction
    * bars is made askable when a grant would open it (consent slice 4). A
    * required decision with nothing left is kept when such an option
    * remains, as the search kept it ([[reach]]): its player may ask. */
  private def narrowAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Option[Decide]] =
    if !ctx.powers.probing then Right(Some(decide))
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path)) match
      case Left(refused) =>
        val asks = asksAt(decide, ctx, path)
        if asks.isEmpty then Left(refused)
        else Right(Some(WalkerSearch.reasoned(WalkerSearch.narrowed(decide,
          _ => false).getOrElse(decide), asks)))
      case Right(narrowed) => Right(narrowed.map { cut =>
        val reasons = WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
          ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
          ctx.procedure, grantable(decide, ctx, path)).toMap
        WalkerSearch.reasoned(WalkerSearch.explained(decide, cut, reasons),
          asksAt(decide, ctx, path))
      })
```

- [ ] **Step 5: Preview an unpayable step's free way on**

In `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`, add `ConsentKey`, `ConsentTurn` and `OptionReason` to the `oathdigital.model` import.

In the doc comment of `previewParked`, after the sentence Task 1 added (ending `asks (consent slice 4).`), add:

```
    * A pay-or-ask step with no option its payer may take previews each
    * askable option as if its ruler had consented, so a priced option shows
    * what going on free costs.
```

In `previewParked`, replace

```scala
            case DecisionQuery.ChooseOne(options, _) =>
              Right(options.filter(_.available).map(option =>
                PreviewedOption(option,
                  answer(state, tree, pending, powers, decide, option,
                    followUp))))
```

with

```scala
            case DecisionQuery.ChooseOne(options, _) =>
              Right(options.filter(_.available).map(option =>
                PreviewedOption(option,
                  answer(state, tree, pending, powers, decide, option,
                    followUp))) ++
                consented(state, decide, options).map((option, granted) =>
                  PreviewedOption(option, answer(granted, tree, pending,
                    powers, decide, option, followUp))))
```

If the existing lines are wrapped differently, match on the `options.filter(_.available).map(` expression and keep its arguments.

After `waitsOnStep`, add:

```scala
  /** For a pay-or-ask step with no option its payer may take, each askable
    * option with the state as it would stand once its ruler consents. Empty
    * for any other decision. */
  private def consented(state: ReadyGame, decide: Decide,
      options: Vector[DecisionOption]): Vector[(DecisionOption, ReadyGame)] =
    if !decide.window.contains(PowerWindow.PayOrAsk) ||
        options.exists(_.available) then Vector.empty
    else options.collect {
      case option @ DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        option -> state.copy(consent = state.consent.granting(
          ask.asks.flatMap(_.powers).distinct.map(ConsentKey(_, decide.owner,
            decide.decisionId, option.ref)), ConsentTurn.of(state)))
    }
```

- [ ] **Step 6: Run the walker suite**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.*"`

Expected: PASS.

- [ ] **Step 7: Rewrite the card tests a short payer now passes**

In `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`, replace the test "a traveller who cannot pay does not get the destination" with:

```scala
  test("a traveller who cannot pay, and was refused this turn, does not " +
      "get the destination"):
    val broke = on(rivalRules)(_.favor(actor, 0))
    val refused = broke.copy(consent = ConsentState(
      answers = Map(ConsentKey(TollRoads.id, actor, TollRoads.decisionId,
        TollRoads.free(coast)) -> ConsentAnswer.Refused),
      turn = Some(ConsentTurn.of(broke))))
    assertEquals(candidates(refused).get(coast), None)
    assertEquals(travel(refused, coast).left.toOption,
      Some(destinationNotOffered))
    // A site the rival does not rule is still offered.
    assert(candidates(refused).contains(hinterlandPlains))
```

In `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsConsentSuite.scala`, append:

```scala
  test("a traveller who cannot pay is offered the destination to ask, and " +
      "a grant lets them through"):
    val broke = on(rivalRules)(_.favor(actor, 0))
    assert(candidates(broke).contains(coast))
    assertEquals(offered(broke, coast, PayOrAsk.pay),
      DecisionOption.Unavailable(DecisionOption.Button(PayOrAsk.pay,
        s"Give 1 favor to ${rival.value}"),
        OptionReason.Written("Needs 1 favor; you have 0")))
    assert(offered(broke, coast, free).askable)
    val done = accepted(travel(granted(broke), coast).flatMap(
      answeringStep(_, TollRoads.decisionId, free)))
    assertEquals(Look(Outcomes.ready(done.state)).player(actor).pawnSite,
      Some(coast))
```

In `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`, replace the test "a searcher who cannot pay is not offered the Search" with:

```scala
  test("a searcher who cannot pay may start the Search only to ask its " +
      "ruler; under bandits, not at all"):
    val startable = (ready: ReadyGame) =>
      SearchProcedure.startable(catalog, ready, p1, automatic)
    assert(startable(rivalRulesBoth.favor(p1, 0).ready))
    val banditBroke = laborTable.bandits(peaks, 1).bandits(home, 1)
      .favor(p1, 0).ready
    assert(!startable(banditBroke))
    assert(SearchFixture.start(banditBroke).isLeft)
```

In `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`, replace the test "a trader who cannot pay is not offered the card" with:

```scala
  test("a trader who cannot pay is offered the card only to ask its ruler; " +
      "under bandits, not at all"):
    assert(wrestlersOffered(rivalRulesBoth.favor(p1, 0).ready))
    assert(!wrestlersOffered(curfewTable.bandits(peaks, 1).bandits(home, 1)
      .favor(p1, 0).ready))
```

In `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`, replace the test "a toll the actor cannot pay says how much favor it needs" with:

```scala
  test("a toll the actor cannot pay says how much favor it needs, under " +
      "bandits"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 0).bandits(provincePlains, 1).bandits(coast, 1).unchecked
    val shown = row(ready, coast)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some("Needs 1 favor; you have 0"))

  test("a toll the actor cannot pay, under a player, reads its free way on"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 0).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    val shown = row(ready, coast)
    assertEquals(shown.state, "available")
    assertEquals(shown.badge, Some("1 Supply"))
```

- [ ] **Step 8: Run the card suites and the whole server**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.gameplay.powers.search.ForcedLaborSuite oathdigital.gameplay.powers.economy.CurfewSuite oathdigital.application.TravelDestinationRowsSuite oathdigital.application.SearchSourceRowsSuite"`. Expected: PASS.

Run: `./sbtw test`. Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala \
  src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala
git commit -m "feat(consent): let a payer who cannot pay a toll ask its ruler to go free"
```

---

### Task 6: The service, the prompt and the log

**Files:**
- Create: `src/test/scala/oathdigital/application/TollConsentFixture.scala`
- Create: `src/test/scala/oathdigital/application/TollConsentServiceSuite.scala`
- Modify: `src/main/scala/oathdigital/application/ConsentProjector.scala` (`target`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`

**Interfaces:**
- Consumes:
  - From Tasks 2 to 5: `TollRoads.decisionId`, `TollRoads.free`, `PayOrAsk.pay`, and each card's `step`.
  - From slice 1: `ConsentFixture.service`, `ConsentFixture.startTravel` and `ParkedServiceFixture.pickTravelDestination`.
- Produces: `TollConsentFixture.tolled`, `TollConsentFixture.broke`, `TollConsentFixture.atTheStep` and `TollConsentFixture.requestFree`.

- [ ] **Step 1: Write the fixture**

Create `src/test/scala/oathdigital/application/TollConsentFixture.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.travel.{TollRoads, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A game for a toll's consent through the service: TravelFixture's board
  * with Toll Roads at the Provinces' plains, and p2 ruling it and the coast.
  * p1, active at the Cradle's plains with 1 favor, travels to the coast. */
object TollConsentFixture:
  import TravelFixture.{board, coast, on, provincePlains}

  val tolled: ReadyGame = on(board(supply = 7))(_
    .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
    .favor(p1, 1).warbandsAt(provincePlains, p2, 1).warbandsAt(coast, p2, 1))

  /** `tolled` with p1 holding no favor. */
  val broke: ReadyGame = on(tolled)(_.favor(p1, 0))

  /** p1 starts a Travel and picks the coast; the walk parks on Toll Roads'
    * step. */
  def atTheStep(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = ConsentFixture.startTravel(service)
    accepted(service.handle(ConsentFixture.gameId, started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, coast)))

  /** p1 asks to travel to the coast without paying. */
  val requestFree: GameCommand = GameCommand.RequestConsent(p1,
    TollRoads.decisionId, TollRoads.free(coast))
```

- [ ] **Step 2: Write the failing service tests**

Create `src/test/scala/oathdigital/application/TollConsentServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.PayOrAsk
import oathdigital.gameplay.powers.travel.{TollRoads, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.ConsentAnswered
import oathdigital.protocol.projection._
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking a toll's ruler to travel without paying, through the service
  * (consent design, "Toll and penalty steps", rulings 7 and 10 to 13). */
class TollConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  import TollConsentFixture._
  private val projector = new GameProjector(catalog)
  private val coast = TravelFixture.coast
  private val free = TollRoads.free(coast)

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, atTheStep(service).nextSequence,
      requestFree))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  /** The rows of the decision p1 is shown. */
  private def rows(at: GameAccepted): Vector[DecisionOptionProjection] =
    projector.project(gameId, at.loaded, p1).walkerDecision
      .flatMap(_.query).toVector.flatMap(_.offeredOptions)

  private def freeRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(rows(at).find(_.id == free.wireId), "the free row")

  test("the free option is askable, with the toll's consent reason and its " +
      "ruler"):
    val (service, _) = ConsentFixture.service(tolled)
    val row = freeRow(atTheStep(service))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Toll Roads: Cannot travel to its " +
      "ruler's sites without giving favor or ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Toll Roads"))))

  test("the ruler's prompt names the free option by its label"):
    val (service, _) = ConsentFixture.service(tolled)
    assertEquals(projector.project(gameId, asking(service).loaded, p2)
      .consentPrompt, Some(ConsentPromptProjection("p1",
        Vector("Toll Roads"), "Travel without paying")))

  test("Yes travels without paying, as p1's own undoable step"):
    val (service, repository) = ConsentFixture.service(tolled)
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    assertEquals(yes.events.head, ConsentAnswered(p2, granted = true))
    val after = Look(Outcomes.ready(yes.state))
    assertEquals(after.player(p1).pawnSite, Some(coast))
    assertEquals(after.favor(p1), 1)
    assertEquals(yes.undo.map(_.issuer), Some(p1))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((yes.state, yes.undo))))

  test("No leaves only paying, with the ordinary reason"):
    val (service, _) = ConsentFixture.service(tolled)
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    val row = freeRow(no)
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some(
      "Toll Roads: Cannot travel to its ruler's sites without giving favor."))
    val paid = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(TollRoads.decisionId,
        DecisionAnswer.ChooseOneAnswer(PayOrAsk.pay)))))
    assertEquals(Look(Outcomes.ready(paid.state)).favor(p1), 0)

  test("a traveller who cannot pay travels free on Yes"):
    val (service, _) = ConsentFixture.service(broke)
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    assertEquals(Look(Outcomes.ready(yes.state)).player(p1).pawnSite,
      Some(coast))

  test("after No, a traveller who cannot pay has only Undo, and then the " +
      "coast is shut for the turn"):
    val (service, _) = ConsentFixture.service(broke)
    val no = answering(service, asking(service), ConsentChoice.No)
    assert(rows(no).nonEmpty && rows(no).forall(!_.choosable), rows(no))
    assert(projector.project(gameId, no.loaded, p1).undo.nonEmpty)
    val undone = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.Undo(p1)))
    val coastRow = present(rows(undone).find(_.id == coast.value),
      "the coast row")
    assertEquals(coastRow.state, "unavailable")
    assertEquals(coastRow.reason, Some("Needs 1 favor; you have 0"))
```

- [ ] **Step 3: Write the failing log and registry tests**

In `src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala`, change `import oathdigital.application.{ConsentFixture, GameAccepted, GameApplicationService, GameCommand}` to `import oathdigital.application.{ConsentFixture, GameAccepted, GameApplicationService, GameCommand, TollConsentFixture}`, then append:

```scala
  test("a request from a button names it by its label"):
    val (service, _) = ConsentFixture.service(TollConsentFixture.tolled)
    accepted(service.handle(gameId,
      TollConsentFixture.atTheStep(service).nextSequence,
      TollConsentFixture.requestFree))
    assert(texts(log(service, p1)).contains("Asked p2 for consent to ignore " +
      "Toll Roads and select Travel without paying."), texts(log(service, p1)))
```

In `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`:

1. Change `import oathdigital.gameplay.powers.travel.{MinesSite, NarrowPassSite, NarrowPassSitePower, TravelFixture}` to `import oathdigital.gameplay.powers.travel.{GraspingVines, MinesSite, NarrowPassSite, NarrowPassSitePower, TollRoads, TravelFixture}`.
2. Add the imports `import oathdigital.gameplay.powers.economy.Curfew` and `import oathdigital.gameplay.powers.search.ForcedLabor`.
3. Append:

```scala
  test("the toll and penalty cards are waivable, each named for its card"):
    Vector(TollRoads.step, ForcedLabor.step, Curfew.step, GraspingVines.step)
      .foreach { step =>
        assert(powers.waiver(step.power).nonEmpty, step.power)
        assertEquals(powers.source(step.power),
          Some(PowerSourceRef.Card(step.card)))
      }

  test("Toll Roads' granter is the player who rules it, and nobody under " +
      "bandits"):
    val toll = CatalogNames.denizen("Toll Roads")
    val ruledByP2 = on(board())(_.denizen(toll,
      at = TravelFixture.provincePlains)
      .warbandsAt(TravelFixture.provincePlains, p2, 1))
    assertEquals(powers.granter(ruledByP2, TollRoads.id), Some(p2))
    assertEquals(powers.granter(on(board())(_.denizen(toll,
      at = TravelFixture.provincePlains)), TollRoads.id), None)
```

Without p2's warbands, no player rules Toll Roads' site, so the second case has no granter.

- [ ] **Step 4: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.TollConsentServiceSuite oathdigital.application.gamelog.GameLogConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite"`

Expected: FAIL in one test only: "the ruler's prompt names the free option by its label" gets the raw key `free.<coast id>` instead of `Travel without paying`. The other new tests already pass:
- the log line reads the label from the parked decision through `ChoiceWords`;
- `ConsentPowers` finds each card's waiver on its own.

If "after No, a traveller who cannot pay has only Undo" fails because the projector refuses a decision with nothing to choose, stop and report the failure.

- [ ] **Step 5: Name a button by its label in the prompt**

In `src/main/scala/oathdigital/application/ConsentProjector.scala`, replace `target` and its doc comment with:

```scala
  /** The option asked about, named as its decision row names it. A button
    * has no name of its own, so its label is read from the parked
    * decision. */
  private def target(context: ScopedProjectionContext,
      ref: DecisionOptionRef): String =
    val option = ref match
      case button: DecisionOptionRef.Button => decisions
        .parkedDecide(context.ready).toVector.flatMap(offered)
        .find(_.ref == button)
      case _ => DecisionOption.forRef(ref)
    option.flatMap(decisions.optionProjection(context.ready, context.viewer,
        context.ready.game.cardIndex.toOption, _))
      .fold(ref.wireId)(_.label)

  /** The options of a choose-one or a choose-many, as offered. */
  private def offered(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case many: DecisionQuery.ChooseMany => many.options
      case _ => Vector.empty
```

- [ ] **Step 6: Run the tests and the consent suites**

Run: `./sbtw "testOnly oathdigital.application.TollConsentServiceSuite oathdigital.application.gamelog.GameLogConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite oathdigital.application.ConsentProjectionSuite oathdigital.application.ConsentRequestServiceSuite"`

Expected: PASS. The Pass's prompt still reads "Dunes": a site is not a button.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/ConsentProjector.scala \
  src/test/scala/oathdigital/application/TollConsentFixture.scala \
  src/test/scala/oathdigital/application/TollConsentServiceSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala
git commit -m "feat(consent): ask a toll's ruler through the service, and name a button in the prompt"
```

---

### Task 7: The docs and the gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `CONTEXT.md` (the Consent section)
- Modify: `docs/ROADMAP.md` (the consent play-test item and the "Phase - Consent system" list)
- Modify: `docs/rules/implementation-traceability.md` (the "“Enemies cannot” consent enforcement" row)

**Interfaces:**
- Consumes: everything above, and slice 3's text in these files.
- Produces: nothing new.

- [ ] **Step 1: Record slice 4 in the spec**

In `docs/superpowers/specs/2026-10-08-consent-system-design.md`, make these replacements. Check that each replaced string is found exactly once before replacing it; line breaks inside a quoted passage may differ in the file. If one is missing, read the file and stop to report it.

1. In the status line, replace

   ```
   Slices 1 to 3 are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md), [slice 3 plan](../plans/2026-10-09-consent-slice3.md)), and their plans' decisions are folded in below.
   ```

   with

   ```
   All four slices are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md), [slice 3 plan](../plans/2026-10-09-consent-slice3.md), [slice 4 plan](../plans/2026-10-09-consent-slice4.md)), and their plans' decisions are folded in below.
   ```

2. Under "Walker and powers", replace

   ```
   On the `restrictOptions` path that `OptionRestriction` uses, an
   option that only waivable restrictions forbid becomes askable at a
   choose-many only, with no search: a grant there answers nothing, so it cannot
   leave a request waiting on a grant that fails.
   ```

   with

   ```
   On the `restrictOptions` path that `OptionRestriction` uses, an
   option that only waivable restrictions forbid becomes askable at a
   choose-many with no search: a grant there answers nothing, so it cannot
   leave a request waiting on a grant that fails. At a choose-one hooked by
   its own window, the fold keeps the option's ordinary reason, and the
   walker makes it askable where it narrows the parked decision, when a
   search with the grant remembered reaches a legal end from it
   (`optionAsks`). A search that finds no answer at such a decision still
   stops there when an option may be asked about, and the walk keeps the
   decision, so a player whose only way on needs consent may ask.
   ```

3. Under "Reasons", after `registered as a game-rule note.` and before `A refused option uses the ordinary reason.`, add:

   ```
   Each toll step's free option has an ordinary and a consent reason:
   Toll Roads `no-free-travel` ("Cannot travel to its ruler's sites without
   giving favor.") and `no-free-travel-consent` ("… without giving favor or
   ruler's consent."), Forced Labor `no-free-search` and
   `no-free-search-consent` ("Cannot search at its ruler's sites …"),
   Curfew `no-free-trade` and `no-free-trade-consent` ("Cannot trade with
   its ruler's cards …"), and Grasping Vines `must-kill` ("Must kill a
   warband when traveling from its ruler's sites.") and `must-kill-consent`
   ("…, unless ruler consents."). A favor toll's paying option, when its
   payer is short, reads "Needs 1 favor; you have 0".
   ```

4. Replace the "Toll and penalty steps" paragraph:

   ```
   **Toll and penalty steps.** In slice 4, Toll Roads, Forced Labor, Curfew and
   Grasping Vines each become a `Decide` step whose free option is barred by
   that power's waivable `OptionRestriction`. The step appears only when its
   ruler is a player other than the actor.
   ```

   with

   ```
   **Toll and penalty steps.** Toll Roads, Forced Labor, Curfew and Grasping
   Vines each ask a choose-one, "Give 1 favor to Red" (or "Kill 1 warband")
   or "Travel without paying", in the window `PowerWindow.PayOrAsk`
   (`PayOrAsk`). The free option is barred by that power's waivable
   `OptionRestriction` until its ruler consents. Its key names the occasion
   (the destination, the pawn's site, the traded card, the site left), so a
   grant covers that occasion for the turn. The step appears only when a
   player other than the actor rules the card; under bandits the toll or
   the kill is taken unasked.

   Every question of a cost is asked before any of it is paid: each step
   goes first in the cost node, and every payment, Way Station's included,
   runs last. A resume folds the node again against the state it parked in,
   and Way Station decides whether to ask from the favor held.

   A payer who cannot pay sees the paying option unavailable with what it
   needs, and may ask to go on free. After a refusal the step has nothing to
   choose; Undo takes back the pick that led there, before anything hidden
   is shown, and the pick is then shut for the turn. Paying keeps its lines;
   going free writes "Gave nothing, with Red's consent." or its card's
   parallel line. A preview follows an answer through a pay-or-ask step, and
   reads a short payer's free option as if granted, so a destination's
   badge and a shortfall reason still show the toll.
   ```

5. Under "Projection", in the bullet that begins `- While a request is pending,`, after the sentence below, add `A button's label is read from the parked decision.`

   ```
   Each asked granter gets `consentPrompt` with the requester, powers and
   target label.
   ```

6. In "Slices", replace

   ```
   4. **Toll and penalty cards.** Toll Roads, Forced Labor, Curfew and Grasping
      Vines, each converted to a pay-or-ask `Decide` step.
   ```

   with

   ```
   4. **Toll and penalty cards.** Toll Roads, Forced Labor, Curfew and Grasping
      Vines, each converted to a pay-or-ask step, with the walker asking at a
      choose-one on the option path.
   ```

7. Under "Deferred", in the tree-path item, replace `slice 4 must tie them.` with `the first change that puts two in one walk must tie them.`

8. Under "Deferred", in the Hall of Ministers item, replace `Slice 4's pay-or-ask step could reach them.` with `A pay-or-ask step could reach them, once they offer an option.`

- [ ] **Step 2: Update the vocabulary, the roadmap and the traceability**

In `CONTEXT.md`, after the **Consent ledger** entry (its `_Avoid_:` line), add:

```

**Pay-or-ask step**:
The choice a toll or penalty card puts to an enemy of its ruling player: pay,
or go on without paying. The free option is askable until that ruler
answers, even for a payer who cannot pay.
_Avoid_: toll prompt, consent step
```

In `docs/ROADMAP.md`:

- Replace

  ```
  - [ ] 4. Toll and penalty cards: Toll Roads, Forced Labor, Curfew and
    Grasping Vines.
  ```

  with

  ```
  - [x] 4. Toll and penalty cards: Toll Roads, Forced Labor, Curfew and
    Grasping Vines
    ([plan](superpowers/plans/2026-10-09-consent-slice4.md)).
  ```

- In the "Play-test consent in a browser" item, replace `faceup Vision and Hall-protected card, the` with `faceup Vision, Hall-protected card and free option of a toll, the`. Then replace `Narrow Pass, Secret Police or Hall of Ministers needs` with `Narrow Pass, Secret Police, Hall of Ministers or toll card needs`.

In `docs/rules/implementation-traceability.md`, row "“Enemies cannot” consent enforcement":

- Replace `and the Hall of Ministers on any discard an enemy chooses: an option only they block is askable,` with `the Hall of Ministers on any discard an enemy chooses, and Toll Roads, Forced Labor, Curfew and Grasping Vines through a pay-or-ask step, even for a payer who cannot pay: an option only they block is askable,`.
- Replace `` `gameplay/operations/OperationConsent.scala`) `` with `` `gameplay/operations/OperationConsent.scala`, `gameplay/powers/PayOrAsk.scala`) ``.
- Replace `` `HallOfMinistersConsentServiceSuite`, `` with `` `HallOfMinistersConsentServiceSuite`, `ChooseOneConsentSuite`, `TollRoadsConsentSuite`, `TollConsentServiceSuite`, ``.
- Replace `The toll and penalty cards are consent slice 4; ` with nothing.

- [ ] **Step 3: Run the full gate**

Run, in order:
1. `./sbtw "test" "frontend/test"`: expected PASS.
2. `./sbtw frontend/fullOptJS`: expected success.
3. `./sbtw coverage test coverageReport`: expected PASS, with the coverage gate met.
4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`: expected no findings.
5. `git diff --check`: expected no output.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-08-consent-system-design.md \
  docs/superpowers/plans/2026-10-09-consent-slice4.md \
  CONTEXT.md docs/ROADMAP.md docs/rules/implementation-traceability.md
git commit -m "docs(consent): record slice 4 in the spec, the vocabulary, the roadmap and the traceability"
```

---

## Execution notes

The plan ran inline. These departures were forced by the code; the spec records the result.

1. **Travel payments run just before the pawn's move, not last.** A Travel's `TravelCost` node holds the pawn's `Move`, so "payments last" would have paid the toll after the traveller arrived. `TravelRoute.beforeMove` places Toll Roads' outcome, its bandit payment, Way Station's payment and Grasping Vines' kill after the rest of the cost and just before the move. Toll Roads and Grasping Vines call `PayOrAsk.question` and `outcome` directly; `around` serves Forced Labor and Curfew through `RulerToll.owed`.
2. **Grasping Vines asks only a traveller with a warband.** `TravelPayments.ownWarband` returns a piece whatever the count, so the plan's code asked a traveller with no warband. The Vines now owe nothing then.
3. **The tree-level emptied-decision check lets an askable choose-one option through.** For a payer who cannot pay, the paying option is already unavailable, so `emptiedDecision` saw every choosable option forbidden and refused the destination before the search reached the step. A choose-one option that `consentAsk` would make askable no longer counts as forbidden there; the search at the step judges the grant.
4. **Slice 2's Narrow Pass test changed.** `NarrowPassCampaignConsentSuite` pinned that a choose-one option-path ask is never askable. Plan decision 2 lifts that limit, so the test now expects the option askable.
5. **`CurfewSuite` reaches the step with its own helper.** `EconomyActions.trade`'s `journal.state` is the started state, so the suite adds `tradedToStep`.
6. **Badges follow only ways on that can finish.** `PricedOptions.paths` skips an accepted answer whose own further question accepts no answer (a checkpoint review finding).
7. **The paying option counts the favor the rest of the cost takes.** A Trade for secrets pays 2 favor in the same cost node before Curfew's toll, so a trader with exactly 2 favor saw "Give 1 favor" offered, and the search then dropped it without a reason. `RulerToll.payOption` now takes the favor the cost's operations take (`RulerToll.favorTaken`, which Way Station's `favorOwed` also uses), and reads "Needs 3 favor; you have 2" there. A payment built inside a batch is not counted.
8. **Smaller review fixes.** The emptied-decision exemption applies only at a pay-or-ask step. Curfew's outcome no longer checks the Trade's source a second time. The ruler's prompt reads a button's label only from the request's own decision. `GraspingVinesSuite` covers Toll Roads and the Vines in one Travel: Toll Roads' step is asked first, and nothing is paid until both are answered.
