# Walker-Driven Game Loop, Slice 5 (The Cleanup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete what the menus replaced: the legal-controls projection and the other Act-panel fields on the wire, the procedures' own start checks, `ModifierOffers.selections` and the projector-only half of Use Power. Then rewrite the docs that still describe them, including ruling 9 of the modifier walker design.

**Architecture:**

- **Tests read the menu first.** Task 1 moves every test that reads `legalControls`, `phasePowers` or `restSupplyGain` onto the parked menu, through new testkit helpers. No production code changes, so a reviewer can check each conversion against the old assertion.
- **The projection's leftovers go.** Task 2 deletes `LegalActionProjector`, `PhasePowerProjector` and five fields of `GameProjection`, with their DTOs, codecs and frontend fixtures.
- **The start checks go.** Task 3 deletes the nine `startable` and `startOptions` functions, `ModifierOffers.selections` and `PhasePowerProcedure.usable`. Their tests ask the menu's own exact check through a testkit helper.
- **Docs.** Task 4 rewrites the comments and docs that name deleted code, records the slice and merges.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` before starting, including its plan-time rulings for slices 1 to 4. Also read `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`, rulings 9 and plan-time ruling 16, which Task 4 rewrites. Task 1 adds this plan's rulings to the game loop spec.

**Starts from:** `main`, after walker game loop slice 4 has merged. Check that `git log --oneline main` shows "Merge walker game loop slice 4". If it does not, stop and report.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Remove every import, and every private member, that a deletion leaves unused.
  - A refutable pattern in a `val` fails it too, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker or `gameplay/operations` sources.
- **Line width.** Scala lines this plan writes stay at or under 80 characters, as in the files around them. Re-wrap any line that comes out longer.
- **Indentation in lists.** A code block inside a list item is indented two extra spaces for the list. Drop those two when you copy it.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`. Do not push.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No compatibility** (spec ruling 14). The deleted projection fields leave the wire. The client and the server ship together, so neither side keeps a fallback.
- **Changed tests.** This slice deletes code that tests read, so tests change. Each task names the expected changes and the rule for each kind. A converted assertion keeps its meaning: what was offered stays available, and what was withheld stays unavailable. When a converted assertion fails, or a test fails for a reason the task does not name, stop and report it. Do not change production code to make a converted test pass.
- **Texts.** Docs and commit messages use plain English. Doc text this plan gives is to be used as written.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report every test added, renamed, moved and deleted.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes these into the spec.

1. **The projection keeps only what the frontend reads.** After slice 4, nothing in the frontend reads `legalControls`, `actionSelectionOpen`, `minorActions`, `phasePowers` or `restSupplyGain`. These fields leave `GameProjection`. `LegalActionProjector`, `LegalProjection`, `PhasePowerProjector`, `MinorActionsProjection`, `MinorAdviserProjection` and `PhasePowerProjection` go with them, and so do their codecs.
2. **The walker controls go too.** `legalControls` carried "resolveWalkerDecision" and "rollWalker" for a parked walk. The frontend reads who may answer from `walkerDecision`, which only the awaited player receives. Tests read the same.
3. **An Act with nothing parked is "act".** The projection's phase for an unparked Act was "act-action-selection", which named the deleted action selection. It becomes "act", like "wake" and "rest". A live game always has a menu parked in Act, so only a test table shows it.
4. **The start checks are the menu's.** These go:
   - the nine `startable` and `startOptions` functions of Search, Travel, Recover, Challenge, Place Banner Resource, Campaign, Negotiation, Muster and Trade;
   - `EconomyTree.startOptions`, `CampaignProcedure.refusal`, `WalkerSimulation.restriction` and `ModifierOffers.selections`;
   - `PhasePowerProcedure.usable` and `PhasePowerProcedure.PowerSource`, which only the projector used.

   Tests ask the menu's exact check (`QueuedStart.priced`) through the testkit helper `StartPreview`. They ask the menu itself through `MenuPlay.menuOptionFor` and `MenuPlay.availableInMenu`.
5. **Modifier walker ruling 9 is rewritten.** Start options are exact: the menu dry-runs each start through its own modifier decision (this design, ruling 8). `ModifierOffers.paying` stays private, because `offerable` uses it.
6. **Docs that name deleted code are rewritten.** A comment or doc that describes deleted code as current is rewritten to describe the menu. A comment that says how the code used to work may stay.

## What exists before this plan

Check each of these in the code before Task 1. If a name or signature differs, read slice 4's commits and adapt. Stop and report if the difference is more than a name.

- `gameplay/walker/QueuedStart.scala`: `priced(catalog, ready, entry: QueuedProcedure, powers: WalkerPowers, phasePowers: PhasePowers): Either[OathViolation, StartCost]` and `startingFrom(ready, entry): ReadyGame`.
- `gameplay/walker/WalkerSimulation.scala`: `started(tree, state, powers): Either[OathViolation, Started]`, where `Started(at: Option[(ReadyGame, PendingTree)], operations)`. Also `previewParked(state, tree, pending, powers, followUp: Boolean = false): Either[OathViolation, Vector[PreviewedOption]]`.
- `gameplay/walker/WalkerProcedureRegistry.scala`: public `build(procedure, catalog, state, activePlayer, args = Vector.empty, phasePowers = PhasePowers.empty, ...)`.
- `gameplay/phases/PhaseMenu.scala`:
  - `decisionId`, and `enum Menu` with `Wake`, `Rest` and `Act`;
  - `of(phase)`, `optionFor(procedure, args)`, `powerButton(power: PowerId)`, `Offered(entry, option)`;
  - `offers(menu, catalog, ready, actor, phasePowers, powers)`.

  `powerButton` comes from slice 4's plan-time ruling 12: a source with two powers of one phase lists a `use-power:<id>` button for each.
- `QueuedProcedure(actor, procedure, startArgs, source, kind)`, read as `entry.startArgs`.
- `TradeProcedure.selection(resource: TradeResource): Vector[DecisionOptionRef]`.
- Tests:
  - `testkit/MenuPlay`, with `options(state, catalog)`, `parked`, `picking` and the private `optionOf`;
  - `testkit/Table`, with `from(ready)` and `phaseMenuParked`;
  - `testkit/PhasePowerHarness.usableNow`.
- Still present, to be deleted by this plan:
  - `application/LegalActionProjector.scala` and `application/PhasePowerProjector.scala`;
  - `GameProjection.legalControls`, `actionSelectionOpen`, `minorActions`, `phasePowers` and `restSupplyGain`;
  - the nine `startable`/`startOptions` functions and `ModifierOffers.selections`.

  Run `grep -rn "legalControls\|actionSelectionOpen\|restSupplyGain\|minorActions\|\.phasePowers" frontend/src/main`. It must print nothing outside the DTO aliases in `frontend.scala`. If the frontend still reads one of the fields, stop and report.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` | modify | 1, 4 |
| `src/test/scala/oathdigital/testkit/{MenuPlay,ProjectedMenu}.scala` | modify, create | 1 |
| `src/test/scala/oathdigital/testkit/StartPreview.scala` | create | 3 |
| `src/test/scala/oathdigital/testkit/PhasePowerHarness.scala` | modify | 3 |
| server test suites reading the deleted fields (listed in Task 1) | modify, rename | 1 |
| `src/main/scala/oathdigital/application/{LegalActionProjector,PhasePowerProjector}.scala` | delete | 2 |
| `src/main/scala/oathdigital/application/{GameProjection,ScopedProjectionContext,PendingProjector}.scala` | modify | 2 |
| `shared/src/main/scala/oathdigital/protocol/projection/{GameProjectionDto,GameProjectionCodec,ActionProjectionDtos,ActionProjectionCodec}.scala` | modify | 2 |
| `frontend/src/main/scala/oathdigital/frontend/frontend.scala` | modify | 2 |
| frontend and shared test fixtures (listed in Task 2) | modify | 2 |
| the nine procedures, `EconomyTree`, `WalkerSimulation`, `ModifierOffers`, `PhasePowerProcedure` | modify | 3 |
| gameplay test suites calling the start checks (listed in Task 3) | modify | 3 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` and other stale comments | modify | 4 |
| `docs/superpowers/specs/2026-10-09-modifier-walker-design.md` | modify | 4 |
| `docs/architecture/{codebase-structure,rule-resolution,gameplay-modules}.md` | modify | 4 |
| `docs/ROADMAP.md` | modify | 4 |

---

### Task 1: Tests read the menu

Every server test that reads `legalControls`, `actionSelectionOpen`, `phasePowers`, `restSupplyGain` or a `LegalActionProjector` reads the parked menu or the walker decision instead. No production code changes.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `src/test/scala/oathdigital/testkit/MenuPlay.scala`
- Create: `src/test/scala/oathdigital/testkit/ProjectedMenu.scala`
- Modify tests: listed in Steps 4 to 7
- Move: `src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala` to `src/test/scala/oathdigital/gameplay/ModifierMenuOptionsSuite.scala`
- Rename: `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala` to `PhasePowerMenuSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.testkit.MenuPlay`:

  ```scala
  def menuOptionFor(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): DecisionOption
  def availableInMenu(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Boolean
  ```

- Produces, in `oathdigital.testkit.ProjectedMenu`: `def pickableIds(projection: GameProjection): Vector[String]`.

**The conversion table.** Use it in every step below.

| Old control | Menu option (`PhaseMenu.optionFor`) | Procedure |
|---|---|---|
| `beginSearch` | `search` | `ActionRef.Search` |
| `beginTravel` | `travel` | `ActionRef.Travel` |
| `beginCampaign` | `campaign` | `ActionRef.Campaign` |
| `beginMuster` | `muster` | `ActionRef.Muster` |
| `beginTradeFavor` | `trade.favor` | `ActionRef.Trade`, args `TradeProcedure.selection(TradeResource.Favor)` |
| `beginTradeSecret` | `trade.secret` | `ActionRef.Trade`, args `TradeProcedure.selection(TradeResource.Secret)` |
| `beginForge` | `forge` | `ActionRef.Forge` |
| `beginRecover` | `recover` | `ActionRef.Recover` |
| `beginChallenge` | `challenge` | `ActionRef.Challenge` |
| `placeBannerResource` | `place-banner-resource` | `ActionRef.PlaceBannerResource` |
| `beginNegotiation` | `negotiation` | `ActionRef.Negotiation` |
| `facedownAdviserMinorAction` | `play-facedown-adviser` | `ActionRef.PlayFacedownAdviser` |
| `peekSiteRelics` | `peek-site-relics` | `ActionRef.PeekSiteRelics` |
| `revealOwnedRelic` | `reveal-owned-relic` | `ActionRef.RevealOwnedRelic` |
| `moveWarbands` | `move-warbands` | `ActionRef.MoveWarbands` |
| `beginRest` | `end-act` | `PhaseTransitionRef.BeginRest` |
| `usePower:<power>:<source>` | the source's option, or `PhaseMenu.powerButton(power)` when the source carries two powers of the phase | `ActionRef.UsePower(power)`, args `Vector(source)` |
| `resolveWalkerDecision` | — | `projection.walkerDecision.nonEmpty` |
| `rollWalker` | — | `projection.walkerDecision.exists(_.kind == "roll")` |
| no control at all (`legalControls == Vector.empty`) | — | `projection.walkerDecision.isEmpty` |

The rules:
- **`controls.contains(x)`** becomes `MenuPlay.availableInMenu(catalog, ready, procedure, args)`, and `!controls.contains(x)` becomes its negation. The helper parks the phase's menu on `ready` for the active player, as the driver does.
- **An assertion that another player sees no control**, on a table where no walk is parked, is deleted. The menu is the active player's decision. `WalkerDecisionProjectionSuite`'s "…for its owner only" tests cover who sees a parked decision. Name each deletion in your report.
- **An assertion that `actionSelectionOpen` is true or false** is deleted.
- **A test whose only assertions are deleted** is deleted.

- [ ] **Step 1: Set up and record the baseline**

Create the worktree as the Global Constraints say. Run `./sbtw "test" "frontend/test"` and record both test counts.

- [ ] **Step 2: Write the plan-time rulings into the spec**

In the spec, add a section `## Plan-time rulings (slice 5)` after "Plan-time rulings (slice 4)". Copy rulings 1 to 6 above into it as a numbered list. Keep their bold leads and their wording; drop the code formatting of file names that are not code identifiers. Wrap lines at 76 characters, as the spec does.

- [ ] **Step 3: The helpers**

In `testkit/MenuPlay.scala`, add after `options(state, catalog)`:

```scala
  /** The option the phase menu shows for starting `procedure` with `args`,
    * on `ready` with its phase's menu parked for the active player, as the
    * driver parks it. A power whose source carries two powers of the phase
    * is found by its own button (slice 4, plan-time ruling 12). */
  def menuOptionFor(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): DecisionOption =
    val ref = optionOf(procedure, args)
    val listed = options(Ready(Table.from(ready).phaseMenuParked.ready),
      catalog)
    val button = procedure match
      case ActionRef.UsePower(id) => Some(PhaseMenu.powerButton(id))
      case _ => None
    listed.find(_.ref == ref)
      .orElse(button.flatMap(power => listed.find(_.ref == power)))
      .getOrElse(munit.Assertions.fail(s"the menu does not list $ref"))

  /** Whether the phase menu on `ready` offers `procedure` available. */
  def availableInMenu(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Boolean =
    menuOptionFor(catalog, ready, procedure, args).available
```

If `DecisionOption` names its availability differently than `available`, use the name `PhaseMenu.onlyEnding` uses.

Create `src/test/scala/oathdigital/testkit/ProjectedMenu.scala`:

```scala
package oathdigital.testkit

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.protocol.projection.GameProjection

/** The phase menu as a viewer's projection shows it (walker-driven game
  * loop design, slice 5, plan-time ruling 2). */
object ProjectedMenu:
  /** The ids of the menu options the viewer may pick, in menu order: empty
    * when no menu is parked for the viewer. */
  def pickableIds(projection: GameProjection): Vector[String] =
    projection.walkerDecision.filter(_.decisionId == PhaseMenu.decisionId)
      .flatMap(_.query).map(_.offeredOptions).getOrElse(Vector.empty)
      .filterNot(option => option.state == "unavailable" ||
        option.state == "locked")
      .map(_.id)
```

Run: `./sbtw "Test/compile"`
Expected: success.

- [ ] **Step 4: The action suites**

Convert by the table:

- `gameplay/RecoverEligibilitySuite.scala`: delete the private `legalControls` helper and the `projector`. Read `MenuPlay.availableInMenu(catalog, ready, ActionRef.Recover)`. The test names say "Recover is available" in place of "beginRecover is offered", and "is not available" in place of "is not offered".
- `gameplay/RestSuite.scala`, the round-eight test: `projection.legalControls.contains("beginRest")` becomes `MenuPlay.availableInMenu(catalog, roundEight, PhaseTransitionRef.BeginRest)`. Delete the `projection` val.
- `gameplay/powers/economy/PressgangsSuite.scala` and `GolemLegionsSuite.scala`: delete the private `controls` helper and, if nothing else uses it, the `projector`. `assert(controls(ready).contains("beginMuster"), controls(ready))` becomes `assert(MenuPlay.availableInMenu(catalog, ready, ActionRef.Muster))`.
- `application/ChallengeProjectionSuite.scala`: by the table, for Challenge and Place Banner Resource.
- `application/EconomyProjectionSuite.scala`:
  - Delete the private `controls` helper.
  - In "the active player is offered Muster and both Trades, and nobody else", keep the p1 assertion, converted. Delete the two p2 assertions under the "another player" rule. Rename the test to "the active player is offered Muster and both Trades".
  - Convert "a control is offered only while a source survives the preview" by the table, and rename it to "an option is available only while a source survives the preview".
- `application/NegotiationDealProjectionSuite.scala`:
  - Lines with `resolveWalkerDecision` use `walkerDecision` by the table.
  - The `beginNegotiation` lines use `availableInMenu` with `Outcomes.ready(state)` for the state.
  - Delete the assertion on `parkedDeal(...)`: a parked deal is a walk, not a menu, and the driver tests cover that no menu parks under a walk.
- Move `application/ModifierStartControlsSuite.scala` to `gameplay/ModifierMenuOptionsSuite.scala` with `git mv`.
  - Package `oathdigital.gameplay`; class `ModifierMenuOptionsSuite`.
  - Delete `presentation`, `legal` and `controls`. Each `controls(ready, viewer).contains("beginX")` becomes `MenuPlay.availableInMenu(catalog, ready, procedure)` by the table. The viewer is always the active player there.
  - The class doc: "A menu option counts the modifiers its player could select: an action that only a modifier makes affordable is still available, and a modifier the player cannot pay for unlocks nothing." Test names say "available" in place of "offered".

Run: `./sbtw "testOnly oathdigital.gameplay.RecoverEligibilitySuite oathdigital.gameplay.RestSuite oathdigital.gameplay.powers.economy.PressgangsSuite oathdigital.gameplay.powers.economy.GolemLegionsSuite oathdigital.application.ChallengeProjectionSuite oathdigital.application.EconomyProjectionSuite oathdigital.application.NegotiationDealProjectionSuite oathdigital.gameplay.ModifierMenuOptionsSuite"`
Expected: PASS.

- [ ] **Step 5: The phase power suites**

Rename `application/PhasePowerProjectorSuite.scala` to `PhasePowerMenuSuite.scala` with `git mv`, and the class to `PhasePowerMenuSuite`. Then, test by test:

- "a usable REST power is projected for its active player only, and the Rest menu offers it" becomes "a usable REST power is offered in the Rest menu". Keep the menu assertion. Delete the `own` and `theirs` projections and their assertions.
- "a synthetic WAKE or ACTION power is projected only in its phase, and is a legal control only in Act" becomes "a synthetic WAKE or ACTION power is offered only in its phase's menu". For each pair, read the menu with the synthetic catalog:

  ```scala
          val menu = MenuPlay.options(Table.from(holding(shown))
            .phaseMenuParked.state, catalog, WalkerPowers.empty,
            PhasePowers(Vector(TestPower(powerId, timing))))
          assertEquals(menu.exists(option =>
            option.ref == DecisionOptionRef.Denizen(card) && option.available),
            shown == phase, s"$timing power in $shown")
  ```

  If `card` is not a `DenizenId`, build the reference the way `PhasePowerFixture` does.
- "a phase power used from a held relic is projected and legal with the relic's printed name and text" becomes "a phase power used from a held relic is offered at the relic". It asserts that the Act menu, read with `PhasePowers(Vector(TestPower(powerId, PowerTiming.Act)))`, lists `DecisionOptionRef.Relic(relic)` available. Keep the `present(...)` lookups only while an assertion still reads them.
- "a phase power used from an edifice at the pawn's site is projected and legal with the edifice face's printed name" becomes "a phase power used from an edifice at the pawn's site is offered at the edifice". It asserts the same with the `powered` catalog and `DecisionOptionRef.Edifice(id)`.
- "an unusable power is neither projected nor legal" becomes "an unusable power is shown unavailable in the Rest menu". It asserts the Silver Tongue option is listed and not available.
- "a Silver Tongue park offers its owner two favor banks": `tongueOwner.legalControls == Vector("resolveWalkerDecision")` becomes `assert(tongueOwner.walkerDecision.nonEmpty)`.
- "a site's phase power is projected under its power's name and printed text" becomes "a site's phase power is offered in the Wake menu at its site". Delete the `projected` assertion and keep the menu one.
- The drift and League Treaty tests stay as they are.

`application/BannerFaceProjectionSuite.scala`:

- "the holder is shown both Wandering Flame powers, named, with a text" becomes "the holder is offered both Wandering Flame powers, each by name". Read the Act menu on `holding`:

  ```scala
    val menu = MenuPlay.options(Table.from(holding).phaseMenuParked.state,
      catalog)
    assertEquals(menu.collect {
      case DecisionOption.Button(ref, label)
          if ref.value.startsWith("use-power:") => ref -> label
    }, Vector(PhaseMenu.powerButton(WanderingFlameMove.id) ->
      "Wandering Flame: move", PhaseMenu.powerButton(WanderingFlamePlace.id) ->
      "Wandering Flame: place a secret on your site"))
    assert(MenuPlay.availableInMenu(catalog, holding,
      ActionRef.UsePower(WanderingFlameMove.id), Vector(banner)))
    assert(MenuPlay.availableInMenu(catalog, holding,
      ActionRef.UsePower(WanderingFlamePlace.id), Vector(banner)))
  ```

  `banner` is the Darkest Secret's option reference, as the suite names it. If a power button carries a badge, match `DecisionOption.Badged(DecisionOption.Button(ref, label), _)` as well.
- "a power with nothing to do is not projected: …" becomes "a power with nothing to do is unavailable: …". Each `shown(x).phasePowers.map(_.powerId)` assertion becomes a check of which of the two powers `availableInMenu` reports available. For example, on `noSite`, place is available and move is not; on `neither`, both are unavailable.

For a two-power source, `availableInMenu(… UsePower(id), Vector(banner))` finds the power's button through `menuOptionFor`'s fallback (Step 3).

Run: `./sbtw "testOnly oathdigital.application.PhasePowerMenuSuite oathdigital.application.BannerFaceProjectionSuite"`
Expected: PASS.

- [ ] **Step 6: The decision and service suites**

By the table's last three rows:

- `application/WalkerDecisionProjectionSuite.scala`: in "a parked Roll names the roll phase and blocks ordinary Act controls", "a parked continue/stop choice names the decision phase and controls" and "a parked relic choice names the decision phase and controls", delete the three `legal` lines. The names become "a parked Roll names the roll phase", "a parked continue/stop choice names the decision phase" and "a parked relic choice names the decision phase". In the class doc, "the `phase`/`legalControls` effects on [[PendingProjector]]/[[LegalActionProjector]]" becomes "the `phase` effect on [[PendingProjector]]".
- `application/GameApplicationServiceActionsSuite.scala`, `GameApplicationServiceForgeSuite.scala` and `GameApplicationServiceStreamSuite.scala`: by the table. In `GameApplicationServiceStreamSuite`, delete `assert(!view.actionSelectionOpen)`.
- `application/GameApplicationServiceProjectionSuite.scala`:
  - `own.legalControls.contains("resolveWalkerDecision")` becomes `own.walkerDecision.nonEmpty`; `public.legalControls == Vector.empty` becomes `public.walkerDecision == None`.
  - Delete `assert(act.actionSelectionOpen)`.
  - The `act.legalControls` list becomes `ProjectedMenu.pickableIds(act)`, the same options in menu order: `Vector("search", "travel", "campaign", "recover", "play-facedown-adviser", "peek-site-relics", "end-act")`. If slice 4 already rewrote this assertion, convert what it left.
  - The public projection's `legalControls == Vector.empty` becomes `walkerDecision == None`.
- `application/SupplyProjectionSuite.scala`: the Rest return is now the Act menu's ending label (slice 4, plan-time ruling 4).
  - `preview` becomes:

    ```scala
    /** What the Act menu's ending says Rest returns. */
    private def preview(board: ReadyGame, actor: PlayerId): Int =
      val label = MenuPlay.menuOptionFor(catalog, board,
        PhaseTransitionRef.BeginRest) match
        case DecisionOption.Button(_, label) => label
        case other => fail(s"the ending is not a plain button: $other")
      """\(\+(\d+) Supply\)""".r.findFirstMatchIn(label)
        .map(_.group(1).toInt)
        .getOrElse(fail(s"an Act that can Rest must preview the return: $label"))
    ```

    The `actor` parameter stays for the callers' sake; the menu is the active player's.
  - In "the preview is the Supply Rest returns, capped by the track", `gain` is `preview(board, p1)`.
  - Delete "a player who cannot Rest is shown no preview": the menu is the active player's, and nobody else is shown an ending.
  - In "a board carries its bag and the band the bag falls in", the last assertion becomes `assertEquals(mine.restSupplyBase, preview(board, p1))`.
  - The class doc's second paragraph: "The maximum lets the client print "5/7" without knowing the rules, and the Act menu's ending says what ending the Act returns."

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectionSuite oathdigital.application.GameApplicationServiceActionsSuite oathdigital.application.GameApplicationServiceForgeSuite oathdigital.application.GameApplicationServiceStreamSuite oathdigital.application.GameApplicationServiceProjectionSuite oathdigital.application.SupplyProjectionSuite"`
Expected: PASS.

- [ ] **Step 7: Nothing reads the fields any more**

Run: `grep -rn "legalControls\|actionSelectionOpen\|restSupplyGain\|\.minorActions\|\.phasePowers\b\|LegalActionProjector\|PhasePowerProjector" src/test`

Expected: no output. `PhasePowerHarness`, `GameProjection`'s own tests and suites that only construct a projection are not exempt: convert or delete each hit by the rules above, and name each in your report.

Run: `./sbtw "test"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  src/test/scala/oathdigital/testkit/MenuPlay.scala \
  src/test/scala/oathdigital/testkit/ProjectedMenu.scala
git add -u src/test/scala
git add src/test/scala/oathdigital/gameplay/ModifierMenuOptionsSuite.scala \
  src/test/scala/oathdigital/application/PhasePowerMenuSuite.scala
git status --short
git commit -m "test: suites read the phase menu instead of the legal controls"
```

`git add -u src/test/scala` stages only tracked test files you changed, moved or deleted. Check `git status --short` before committing: nothing outside this task may be staged.

---

### Task 2: The projection's leftovers go

**Files:**
- Delete: `src/main/scala/oathdigital/application/LegalActionProjector.scala`, `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala`, `ScopedProjectionContext.scala`, `PendingProjector.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala`, `GameProjectionCodec.scala`, `ActionProjectionDtos.scala`, `ActionProjectionCodec.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala`
- Modify tests: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, the frontend fixtures and suites `grep` finds in Step 4, and every test that expects the phase "act-action-selection"

**Interfaces:**
- Consumes: Task 1's converted suites.
- Produces: `GameProjection` without `legalControls`, `actionSelectionOpen`, `minorActions`, `phasePowers` and `restSupplyGain`. The unparked Act's phase is "act".

- [ ] **Step 1: The server projection**

- Delete `LegalActionProjector.scala` and `PhasePowerProjector.scala`.
- `ScopedProjectionContext.scala`: delete `LegalProjection`.
- `GameProjection.scala`:
  - Delete `phasePowerProjector` and `legalActions`.
  - In `readyProjection`, delete `legal`, `controls` and `projectedPhasePowers`, and the `actionSelectionOpen`, `minorActions`, `phasePowers` and `restSupplyGain` arguments with the comment above `restSupplyGain`.
  - The `NoGame` projection loses the `Vector.empty` that filled `legalControls`: its arguments are then `gameId, loaded.nextSequence, "not-started", None, Vector.empty, Vector.empty, Vector.empty, ready = false, completed = false`.
  - Remove the imports this leaves unused; `FinishRestProcedure` stays while `supplyBands` reads it.
  - The `phasePowers` constructor parameter stays only while `walkerDecisions` reads it.
- `PendingProjector.scala`: `case Phase.Act => "act-action-selection"` becomes `case Phase.Act => "act"` (ruling 3).

- [ ] **Step 2: The wire**

- `GameProjectionDto.scala`: delete the five fields.
- `GameProjectionCodec.scala`: delete them from the key list, from `encode` and from `decode`, with the `controls`, `actionOpen`, `minor`, `powerRaws`, `phasePowers` and `restGain` bindings. Remove the imports of `encodeMinor`, `decodeMinor`, `encodePhasePower` and `decodePhasePower`.
- `ActionProjectionDtos.scala`: delete `MinorActionsProjection`, `PhasePowerProjection` and `MinorAdviserProjection`. Keep `MinorAdviserProjection` if `grep -rn "MinorAdviserProjection" shared/src/main src/main frontend/src/main` finds a user outside the deleted code.
- `ActionProjectionCodec.scala`: delete `encodeMinor`, `decodeMinor`, `encodePhasePower`, `decodePhasePower` and any helper only they used.
- `frontend/.../frontend.scala`: delete the `MinorActionsState` and `PhasePowerState` aliases (both `type` and `val`).

Run: `./sbtw compile frontend/compile`
Expected: success. Any error names a reader this plan missed: if it is production code that reads a deleted field, stop and report.

- [ ] **Step 3: The shared tests**

In `ProjectionProtocolSuite`, delete the deleted fields from every sample projection, and any test whose only subject is one of them (the minor actions round trip and the phase powers round trip, if present). A test that checks that a payload without `actionSelectionOpen` decodes goes too.

Run: `./sbtw "sharedJVM/test"`. If the project names the shared JVM module differently, `./sbtw projects` lists it.
Expected: PASS.

- [ ] **Step 4: The frontend tests**

Run: `grep -rln "legalControls\|actionSelectionOpen\|restSupplyGain\|minorActions\|phasePowers\|MinorActionsState\|PhasePowerState\|act-action-selection" frontend/src/test shared/src/test`

For each file:
- delete the deleted fields from `GameProjection(...)` constructions and `.copy(...)` calls. In a positional construction, the argument after `pawnLocations` was `legalControls`;
- delete a helper whose only job was to set one of them, and update its callers;
- delete a test whose only subject is one of them;
- "act-action-selection" becomes "act".

Run: `./sbtw "frontend/test"`
Expected: PASS.

- [ ] **Step 5: The server tests that name the phase**

Run: `grep -rln "act-action-selection" src/test`. In each hit, "act-action-selection" becomes "act".

Run: `./sbtw "test" "frontend/test"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git rm src/main/scala/oathdigital/application/LegalActionProjector.scala \
  src/main/scala/oathdigital/application/PhasePowerProjector.scala
git add src/main/scala/oathdigital/application/GameProjection.scala \
  src/main/scala/oathdigital/application/ScopedProjectionContext.scala \
  src/main/scala/oathdigital/application/PendingProjector.scala \
  shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala \
  shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala \
  frontend/src/main/scala/oathdigital/frontend/frontend.scala
git add -u src/test/scala shared/src/test/scala frontend/src/test/scala
git status --short
git commit -m "refactor: the legal controls and the Act panel's projection fields go"
```

---

### Task 3: The start checks go

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`, `travel/TravelProcedure.scala`, `recover/RecoverProcedure.scala`, `challenge/ChallengeProcedure.scala`, `challenge/PlaceBannerResourceProcedure.scala`, `campaign/CampaignProcedure.scala`, `negotiation/NegotiationProcedure.scala`, `economy/MusterProcedure.scala`, `economy/TradeProcedure.scala`, `economy/EconomyTree.scala` (all under `gameplay/actions`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`, `ModifierOffers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Create: `src/test/scala/oathdigital/testkit/StartPreview.scala`
- Modify: `src/test/scala/oathdigital/testkit/PhasePowerHarness.scala`
- Modify tests: listed in Step 3

**Interfaces:**
- Consumes: `QueuedStart.priced`, `QueuedStart.startingFrom`, `WalkerSimulation.started`, `WalkerSimulation.previewParked`, `WalkerProcedureRegistry.build`, `PhaseMenu.of` and `PhaseMenu.offers`.
- Produces, in `oathdigital.testkit.StartPreview`:

  ```scala
  def opens(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty): Boolean
  def firstDecisionOptions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty)
      : Vector[WalkerSimulation.PreviewedOption]
  ```

- Produces, in `PhasePowerHarness`: `final case class MenuPower(power: PhasePower, ref: DecisionOptionRef)`; `usableNow(ready: ReadyGame): Vector[MenuPower]`.

- [ ] **Step 1: The test helpers**

Create `src/test/scala/oathdigital/testkit/StartPreview.scala`:

```scala
package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers,
  WalkerProcedureRegistry, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** A start dry-run as the phase menu runs it (walker-driven game loop
  * design, slice 5, plan-time ruling 4). It is for suites that asked a
  * procedure's own start check, which the menu's exact check replaced. */
object StartPreview:
  private def entry(actor: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef]): QueuedProcedure =
    QueuedProcedure(actor, procedure, args, None, QueueKind.Run)

  /** Whether `actor` could start `procedure` with `args` now under
    * `powers`: the check that makes a menu option available. */
  def opens(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty): Boolean =
    QueuedStart.priced(catalog, ready, entry(actor, procedure, args), powers,
      PhasePowers.empty).isRight

  /** The options of the first decision past `procedure`'s modifier
    * decision, each previewed, as the start reaches it under `powers`.
    * Empty when the start would be refused or asks nothing. */
  def firstDecisionOptions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty)
      : Vector[PreviewedOption] =
    val state = QueuedStart.startingFrom(ready, entry(actor, procedure, args))
    WalkerProcedureRegistry.build(procedure, catalog, state, actor, args)
      .flatMap(tree => WalkerSimulation.started(tree, state, powers)
        .flatMap(_.at.fold[Either[OathViolation, Vector[PreviewedOption]]](
          Right(Vector.empty)) { (parked, pending) =>
          WalkerSimulation.previewParked(parked, tree, pending, powers)
        }))
      .getOrElse(Vector.empty)
```

If `{ (parked, pending) => ... }` does not compile against the tuple, write `{ case (parked, pending) => ... }`.

In `testkit/PhasePowerHarness.scala`, replace `usableNow` with:

```scala
  /** A phase power the actor's phase menu offers available, and the source
    * its option names. */
  final case class MenuPower(power: PhasePower, ref: DecisionOptionRef)

  /** The phase powers the actor's phase menu offers available now, under
    * the printed operation restrictions. */
  def usableNow(ready: ReadyGame): Vector[MenuPower] =
    val phasePowers = PhasePowerCatalog.default(catalog)
    val powers = WalkerPowers(Vector.empty,
      restrictionSet = OperationRestrictions.forCatalog(catalog))
    PhaseMenu.of(ready.game.current.turn.phase).toVector.flatMap(menu =>
      PhaseMenu.offers(menu, catalog, ready, actor, phasePowers, powers)
        .filter(_.option.available)
        .flatMap(offered => offered.entry.procedure match
          case ActionRef.UsePower(id) => for
            power <- phasePowers.find(id).toVector
            ref <- offered.entry.startArgs.headOption.toVector
          yield MenuPower(power, ref)
          case _ => Vector.empty))
```

Import `oathdigital.gameplay.phases.PhaseMenu` and `oathdigital.gameplay.powerresolver.PhasePower`. Keep the imports `usableNow` used before only while something still uses them.

Callers read `.power` and `.ref`, which `MenuPower` keeps. A caller that reads `.source` (the old `PowerSourceRef`) reads `.ref` instead. Say which callers you changed.

Run: `./sbtw "Test/compile"`
Expected: success.

- [ ] **Step 2: Delete the start checks**

- `SearchProcedure`, `TravelProcedure`, `RecoverProcedure`, `ChallengeProcedure`, `PlaceBannerResourceProcedure`, `CampaignProcedure` and `NegotiationProcedure`: delete `startable` with its doc.
- `CampaignProcedure`: delete `refusal` with its doc.
- `MusterProcedure` and `TradeProcedure`: delete `startOptions` with its doc.
- `EconomyTree`: delete `startOptions` with its doc. `build`'s doc stays.
- `WalkerSimulation`: delete `restriction`. `starts`, `refusal` and `preview` stay; `PhasePowerProcedure` and `ModifierChoice` read them. The doc of `starts` becomes: "Whether `tree` would start now: the restrictions and first walk a start performs, with nothing persisted. A tree that parks and one that finishes both start; a rejected cost or restriction does not."
- `ModifierOffers`:
  - Delete `selections` with its doc.
  - The object's doc becomes: "The modifiers a player may select for a procedure, read from one walker power catalog. The modifier decision asks `offerable` which to list, and judges each option exactly, inside the walk."
- `PhasePowerProcedure`: delete `usable`, `PowerSource` and the private `starts`, if nothing else uses them.

Run: `./sbtw compile`
Expected: success, once each private helper the deletions leave unused is removed too (`-Wunused:privates` names them). A private helper such as `afterModifiers` that `build` or `tree` still calls stays. If an unused-import or unused-private error names something production code outside these files still calls, stop and report.

- [ ] **Step 3: The tests**

Convert each call; the rule in every case is the same as in Task 1.

- `startable(catalog, ready, actor, powers)` becomes `StartPreview.opens(catalog, ready, actor, procedure, powers)`:
  - `gameplay/NegotiationProcedureSuite.scala` (Negotiation);
  - `gameplay/CampaignProcedureSuite.scala` (Campaign);
  - `gameplay/powers/targeting/DiplomatSuite.scala` and `FortressRulesSuite.scala` (Campaign);
  - `gameplay/powers/search/ForcedLaborSuite.scala` (Search).
- `MusterProcedure.startOptions(catalog, ready, actor, powers)` becomes `StartPreview.firstDecisionOptions(catalog, ready, actor, ActionRef.Muster, powers)`:
  - `gameplay/MusterProcedureSuite.scala`;
  - `gameplay/powers/economy/MusterModifierChoiceSuite.scala`.
- `TradeProcedure.startOptions(catalog, ready, actor, resource, powers)` becomes `StartPreview.firstDecisionOptions(catalog, ready, actor, ActionRef.Trade, powers, TradeProcedure.selection(resource))`:
  - `gameplay/TradeProcedureSuite.scala`;
  - `gameplay/powers/economy/VowOfPovertySuite.scala`;
  - `gameplay/powers/economy/CurfewSuite.scala`.
- Words that name the deleted functions change:
  - `MusterProcedureSuite`: the comment "not a build failure swallowed by `startOptions`" becomes "not a build failure swallowed by the preview".
  - `MusterModifierChoiceSuite`: "the start control previews the sources, not the modifier decision" becomes "the start previews the sources past the modifier decision".
  - `TradeProcedureSuite`: "a Trade the actor cannot pay for is not offered at all" becomes "a Trade the actor cannot pay for offers no source".
  - Any other test name or comment that says "start control" says "menu option".

The new check is exact where the old one was a heuristic. A test that passed a selection of modifiers as `powers` now passes the same `powers`, and the search tries each modifier in it. When a converted assertion fails because the exact check finds a start the old one missed, stop and report the test and both results.

Run: `grep -rn "\.startable(\|startOptions(\|ModifierOffers.selections\|PhasePowerProcedure.usable\|CampaignProcedure.refusal\|WalkerSimulation.restriction" src/main src/test`
Expected: no output.

Run: `./sbtw "test"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/ModifierOffers.scala \
  src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala \
  src/test/scala/oathdigital/testkit/StartPreview.scala
git add -u src/test/scala
git status --short
git commit -m "refactor: the procedures' start checks go; the menu's check is the only one"
```

---

### Task 4: Docs, and the gates

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`, and the stale comments Step 1 finds
- Modify: `src/test/scala/oathdigital/model/ProcedureRefSuite.scala`, if Step 2 deletes `StartableRef.fromKey`
- Modify: `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`, `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `docs/architecture/codebase-structure.md`, `docs/architecture/rule-resolution.md`, `docs/architecture/gameplay-modules.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Stale comments**

Run: `grep -rn "StartWalker\|start control\|Act panel\|legal control\|legalControls\|LegalActionProjector\|PhasePowerProjector\|ModifierOffers.selections\|startOptions\|\.startable\b\|walkerAction\|GameIntentMapper.actionRef" src/main shared/src/main frontend/src/main`

Rewrite each hit by ruling 6. Two of them are known; use this text:

- In `model/ProcedureRef.scala`, the doc of `StartableRef` becomes:

  ```scala
  /** A procedure a queue entry can start: an action or a phase transition.
    * Both members below extend this; a [[TriggeredProcedureRef]] does not,
    * so no queue entry, and no menu option, can name one.
    */
  ```

- In the same file, the doc of `ActionRef.all` becomes:

  ```scala
    /** Every finite action reference.
      *
      * `TakeWealth` names the Wake phase's action, not `ActionKind.Wake`,
      * which names the phase's power timing. Take Wealth's registry entry
      * points at that kind through `fallbackKind`, which keeps the fallback
      * diagnostics the legacy `Wake` command ran under. A preview asked for
      * the Wake kind takes its existing path rather than being answered as
      * Take Wealth.
      */
  ```

Say in your report what you wrote for each other hit.

- [ ] **Step 2: `StartableRef.fromKey`**

Run: `grep -rn "StartableRef.fromKey" src/main shared/src/main`. If it prints nothing:
- delete `StartableRef.fromKey` with its doc, and keep `StartableRef.all` only if `grep -rn "StartableRef.all" src` still finds a user;
- in `ProcedureRefSuite`, `StartableRef.fromKey(use.key)` becomes `ActionRef.fromKey(use.key)`, and `StartableRef.fromKey("end-wake")` becomes `ProcedureRef.fromFamilyKey("phase-transition", "end-wake")`. Delete the two assertions that `"oathkeeper"` and `"forced-wake"` are not startable: the type says so now.

If it prints a user, leave both as they are.

- [ ] **Step 3: The modifier walker design**

In `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`:

- Ruling 9 becomes:

  ```markdown
  9. **Start options are exact.** The phase menu dry-runs each start
     through its own modifier decision, trying no pick first and then each
     pick, so an option is available exactly when some selection the player
     could make starts it. (Rewritten by slice 5 of the
     [walker-driven game loop](2026-10-09-walker-game-loop-design.md); it
     first kept the Act panel's `ModifierOffers.selections` heuristic.)
  ```

- Plan-time ruling 16 becomes:

  ```markdown
  16. **What stays.** `ModifierOffers.paying` stays, private: `offerable`
      dry-runs each modifier's selection payment through it. The Game Log's
      card lookup moved from `PreviewModifierDescriptions` to the log's own
      `PrintedCards`.
  ```

Keep each paragraph's indentation and wrap at 76 characters.

- [ ] **Step 4: The architecture docs**

- `docs/architecture/codebase-structure.md`, under "Projection": "legal targets in `LegalActionProjector`, and the walker decision, its waiting notice and the phase in `PendingProjector`" becomes "and the walker decision, its waiting notice and the phase in `PendingProjector`. What a player may do is always a parked walker decision, the phase's menu included".
- `docs/architecture/rule-resolution.md`, under "Commands, projection, and replay": "Commands and `LegalActionProjector` call the same gameplay legality APIs." becomes "A phase menu prices and judges each option with the same dry run its start performs, so what the projection offers is what a command accepts."
- `docs/architecture/gameplay-modules.md`: "`LegalActionProjector` maps gameplay legality and targets; and `PendingProjector` maps forced decisions" becomes "and `PendingProjector` maps the parked decision, a phase menu included".

Re-wrap each paragraph at its file's width.

- [ ] **Step 5: The spec and the roadmap**

- The game loop spec's status lines become: "**Status:** designed 2026-10-09; implemented in five slices, the last landed 2026-10-10."
- In `docs/ROADMAP.md`, under "Phase - Walker-driven game loop", tick "Slice 5: the cleanup." Leave the section in place, as the finished modifier walker phase above it is left.

- [ ] **Step 6: The gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline and list every test added, renamed, moved and deleted.

- [ ] **Step 7: Commit, and merge**

```bash
git add src/main/scala/oathdigital/model/ProcedureRef.scala \
  docs/superpowers/specs/2026-10-09-modifier-walker-design.md \
  docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  docs/architecture/codebase-structure.md \
  docs/architecture/rule-resolution.md \
  docs/architecture/gameplay-modules.md \
  docs/ROADMAP.md
git add -u src/main/scala src/test/scala shared/src/main/scala \
  frontend/src/main/scala
git status --short
git commit -m "docs: record walker game loop slice 5"
```

Then run `ExitWorktree` with `keep`. In the main checkout, run `git log --oneline -1` and check that `main` has not moved in a way that conflicts. Then:

```bash
git merge --no-ff worktree-<branch> -m "Merge walker game loop slice 5"
```

Use the worktree's branch name. Do not push.
