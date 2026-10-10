# Modifier Selection on the Walker, Slice 4 (Recover, the Empty Windows and the Cleanup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every major action asks for its modifiers inside the walk, and everything that chose modifiers before the start is deleted: the client's modifier flow, the preview endpoint, the start command's modifiers and the durable selection.

**Architecture:**

- **The client goes first.** Task 1 deletes the client's modifier flow. Every Act panel button already builds its `StartWalker` with no modifiers, so the buttons stay; only the router between them and the session goes. `TableSession` becomes the `TableControls` the renderers call. Recover, Forge and Challenge start without modifiers from then on, which is harmless until Task 3, since nothing selects at the Forge or Challenge windows.
- **The empty windows join.** Forge, Challenge and Campaign gain the modifier decision at the root of their trees, as Search and Travel did. No power selects at their windows, so the decision asks nothing and records nothing, and the Game Log, the goldens and the event streams do not change. Only walker paths shift under a new first child.
- **Recover moves last of the actions.** Its tree gains the modifier decision. Its two modifiers become pickable:
  - **Relic Worship** is a `SelectedModifier`; joining the walk is enough.
  - **Catacombs** is a hand-written `ContributingPower`. It gains a selection card and window. Its eligibility Transform keeps placing the relic but no longer pays, because the pick pays. It gains an `offerReason` for a site with no empty relic slot or an empty relic deck, so the player never pays for a pick that strands the Recover.
- **Paying at the eligibility window goes with Recover.** Once Recover pays at the pick, every action does, so `SelectedModifier` loses its payment Transform, `paidAtPick` and the eligibility-window helpers.
- **The Recover tag waits for the dice.** The Game Log's Recover tag now anchors at the step that opens Recover's dice pool. That step follows the modifier decision. An un-asked Recover posts the same lines as before.
- **Then the cleanup, in three server tasks:**
  - **Task 4:** the preview endpoint and the dead ordered-modifiers channel.
  - **Task 5:** the start command's modifiers, from the wire to `OathRulesWalker`.
  - **Task 6:** the durable selection: `WalkerParked.modifiers` and `CurrentGameState.walkerModifiers`.
- **Old journals still load.** The journal stores events, never commands, so dropping `StartWalker.modifiers` breaks no stored game. A stored `walker.parked` record keeps its `modifiers` key, which the decoder now ignores; a test pins that. Old finished games lose the "With X" part of a start line whose modifiers were chosen at the start (user decision). A game saved parked mid-walk with start modifiers may not resume (spec ruling 10).
- **The client wire changes in step with the server.** `GameIntent.StartWalker` loses `modifiers`, and its decoder rejects the key. A browser holding an old bundle gets one rejection, and a reload fixes it (user decision).

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`. Read it whole, including "What goes away" and its "Plan-time rulings". The slice 2 and slice 3 plans (`docs/superpowers/plans/2026-10-09-modifier-walker-slice2.md`, `-slice3.md`) show how Travel, Muster and Trade moved; Task 3 copies that move for Recover. The files to copy from are:

- `TravelProcedure` (`gameplay/actions/travel/`): `afterModifiers`, `startable` and the wrapped `tree`.
- `TravelModifierChoiceSuite` (`src/test/scala/oathdigital/gameplay/powers/travel/`).
- `WineOfWelcome` (`gameplay/powers/search/`): an `offerReason`.

**Starts from:** `main` at `3983af1f` (the slice 3 merge) or later.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
  - Most tasks here delete code, and each deletion can leave an import or a private helper unused. Delete it when the compiler says so.
  - A refutable pattern in a `val` also fails the build.
- **File size.** Production and test Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources, in any case, not even in a comment.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.** A browser check runs a scratch server on port 8081 through Bash, never `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` into the worktree if a ledger exists; it is gitignored.
  - Before merging, leave the worktree with `ExitWorktree` (keep), then merge from the main checkout.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No compatibility for parked saves** (spec ruling 10). A journal parked mid-walk with start modifiers may fail to resume.
- **Texts, exactly:**
  - The start buttons: "Start Recover", "Start Forge", "Start Challenge", "Start Campaign".
  - Catacombs' reasons: "This site has no empty relic slot", "The relic deck is empty".
  - The Recover start refusal, until Task 5 deletes it: "recover chooses its modifiers inside the walk". Likewise "forge …", "challenge …", "campaign …".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree. Each task states how its counts move; compare at the end. Shared tests (`shared/src/test`) run in both builds, so a shared test counts once in each. In all, the server count drops by 21 and the frontend count by 43.
- **The verification gate** (roadmap standing rule), run in Task 7:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
  4. `git diff --check main...HEAD`

---

## File Structure

| Area | Files | Task |
|---|---|---|
| Client flow (delete) | `frontend/.../ModifierFlow.scala`, `ModifierFlowDraft.scala`, `ModifierSelectionState.scala`; their suites and `ModifierFlowDrafts` | 1 |
| Client wiring | `TableControls.scala`, `SessionDrafts.scala`, `TableSession.scala`, `TableScreen.scala`, `ServerModeUi.scala`, `ActionDecisionRenderer.scala`, `GameClient.scala`, `FollowUpControls.scala`, `styles.css` | 1 |
| Empty windows | `gameplay/actions/forge/ForgeProcedure.scala`, `challenge/ChallengeProcedure.scala`, `campaign/CampaignProcedure.scala`, `walker/WalkerProcedureRegistry.scala` | 2 |
| Recover | `gameplay/actions/recover/RecoverProcedure.scala`, `powers/recover/CatacombsContribution.scala`, `RelicWorship.scala`, `powers/SelectedModifier.scala`, `application/gamelog/StartLines.scala`, `frontend/.../ParkedDecision.scala` | 3 |
| Preview and ordered modifiers (delete) | `shared/.../MajorActionPreviewProtocol.scala`, `CommandProtocol.scala`; `server/GameRoutes.scala`, `TrustedGameGateway.scala`, `AuthenticatedGameRoutes.scala`, `TrustedSeatRoutes.scala`; `application/GameApplicationService.scala`, `GameCommands.scala`, `GameIntentMapper.scala`, `PreviewModifierDescriptions.scala`, `gamelog/LogWords.scala`; `gameplay/PowerRuntime.scala`; `model/RuleFallbackProtocol.scala`, `GameViolation.scala` | 4 |
| Start modifiers (delete) | `shared/.../CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`; `application/GameCommands.scala`, `GameIntentMapper.scala`; `gameplay/OathRulesWalker.scala`, `walker/WalkerProcedureRegistry.scala`, `walker/ModifierOffers.scala` | 5 |
| Durable selection (delete) | `gameplay/walker/WalkerEvents.scala`, `WalkerReplay.scala`, `WalkerPowers.scala`; `serialization/WalkerEventCodec.scala`; `model/GameState.scala`; `gameplay/OathRulesWalker.scala`; `application/WalkerDecisionProjector.scala`, `gamelog/StartLines.scala`; `gameplay/actions/cardplay/CardSlots.scala` | 6 |
| Docs | the spec, three amended specs, `docs/ROADMAP.md`, `CONTEXT.md`, `DESIGN.md`, `PRODUCT.md`, `docs/architecture/follow-up-actions.md`, `docs/rules/implementation-traceability.md` | 7 |

Each task names its test files.

---

### Task 1: The client starts every action directly

**Files:**
- Delete: `frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala`, `ModifierFlowDraft.scala`, `ModifierSelectionState.scala`
- Delete: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala`, `ModifierSelectionStateSuite.scala`, `ModifierFlowDraftSuite.scala`, `ActionPanePromptSuite.scala`, `ModifierFlowDrafts.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableControls.scala`, `SessionDrafts.scala`, `TableSession.scala`, `TableScreen.scala`, `ServerModeUi.scala`, `ActionDecisionRenderer.scala`, `GameClient.scala`, `FollowUpControls.scala`
- Modify: `frontend/styles.css`
- Modify (tests): `RecordingControls.scala`, `ActionPanels.scala`, `SessionDraftsSuite.scala`, `TableSessionSuite.scala`, `ServerModeSessionSuite.scala`, `HttpGameClientSuite.scala`, `FollowUpControlsSuite.scala`, `BannerControlsSuite.scala`, `NegotiationControlsSuite.scala`, `CampaignControlsSuite.scala`, and every suite that reads `.element` off `actionsPanel` (Step 6)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `TableSession` implements `TableControls`: `stage(draft: Draft): Unit` and `submitCommand(command: GameIntent): Unit`.
  - `ActionDecisionRenderer.actionsPanel(value, presentation, routed, canControl, drafts, controls: TableControls): dom.Element`. It returns the panel itself; `ActionPane` is gone.
  - `SessionDrafts(context, partition, distribute, selection, board)`: five slots.
  - `GameClient.submit(gameId, selectedPlayerId, expectedNextSequence, command)`: no `orderedModifiers`, and no `preview`.

The shared wire types (`MajorActionPreviewProtocol`, `ModifierInvocation`, `GameIntent.StartWalker.modifiers`) stay until Tasks 4 and 5. The client stops using them here.

- [ ] **Step 1: Delete the flow and its suites**

```bash
git rm frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala \
  frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala \
  frontend/src/main/scala/oathdigital/frontend/ModifierSelectionState.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowDraftSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ActionPanePromptSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala
```

Three `ModifierFlowSuite` tests are about the session, not the flow. Step 9 moves them to `TableSessionSuite`: a command is sent as it is, nothing is sent with no projection, and staging replaces one slot and redraws.

- [ ] **Step 2: `TableControls` loses `ActionControls`**

In `TableControls.scala`, delete the `ActionControls` trait and its doc comment (from "/** The modifier flow's commands" through `def cancelModifiers(): Unit`). Change the import to:

```scala
import oathdigital.protocol.GameIntent => GameCommand
```

- [ ] **Step 3: `SessionDrafts` loses the flow slot**

In `SessionDrafts.scala`:

- Delete the `PreviewModifier` import.
- Delete the field `modifiers: Option[ModifierFlowDraft]` and the `modifiers = ModifierFlowDraft.reconcile(modifiers, context)` line of `reconcile`. The `board = …` argument above it ends the call.
- Delete `leave`, `step`, `enum FlowExit` and `enum FlowStep`, with their doc comments.
- `empty` becomes `SessionDrafts(None, None, None, None, None)`.
- Replace the doc comment on `enum Draft` with:

```scala
/** A draft a panel or the board can stage. `Board` carries an `Option`
  * because a second click on the picked site clears the pick, and that
  * clear is a stage like any other.
  */
```

- In the class doc, "every write is one of the three methods below" becomes "every write is one of the two methods below".

- [ ] **Step 4: `TableSession` is the table's controls**

In `TableSession.scala`:

- `extends SessionControls, FlowHost, TableView` becomes `extends SessionControls, TableControls, TableView`.
- Delete the `scala.concurrent.Future` import.
- `submitTransport` loses its `modifiers` parameter:

```scala
  private def submitTransport(command: GameCommand): Unit =
    projection.foreach { current =>
      val request = coordinator.capture
      client
        .submit(gameId, selectedPlayer, current.nextSequence, command)
        .foreach:
```

  The rest of its body is unchanged.
- Replace the whole `// FlowHost (the modifier flow)` block, from that comment through `submitTransport(command, modifiers)`, with:

```scala
  // TableControls (the panels and the board).
  def stage(draft: Draft): Unit =
    drafts = drafts.staged(draft)
    redraw()
  /** Sends under the session's identity, at the displayed position; with
    * no position displayed, nothing is sent. */
  def submitCommand(command: GameCommand): Unit = submitTransport(command)

  private def redraw(): Unit = repaint()
```

- In the class doc, "and is the modifier flow's host, the development toolbar's session and the screen's view" becomes "and is the table's controls, the development toolbar's session and the screen's view".

- [ ] **Step 5: `ServerModeUi` and `TableScreen` take the session as controls**

In `ServerModeUi.scala`, delete the `flow` line and pass the session:

```scala
    lazy val screen: TableScreen = new TableScreen(shell, session, session,
      session)
```

In `TableScreen.scala`:

- The constructor's `controls: ActionControls` becomes `controls: TableControls`.
- `ActionDecisionRenderer.actionsPanel` now returns the element (Step 6), so the pane lines become:

```scala
        val pane = ActionDecisionRenderer.actionsPanel(value, presentation,
          routed, view.controlsAvailable, drafts, controls)
        actionContent.appendChild(pane)
        val decisionKey = Vector(selectedPlayer, value.phase,
          value.activeParticipantId.getOrElse(""),
          value.walkerDecision.map(_.decisionId).getOrElse(""))
          .mkString("|")
```

- In the `TableView` doc, "The names repeat neither the session's private state nor `SessionControls`' or `FlowHost`'s members, so one class implements all three traits with no name answering two questions." becomes "The names repeat neither the session's private state nor `SessionControls`' or `TableControls`' members, so one class implements all three traits with no name answering two questions."

- [ ] **Step 6: The action pane is the panel alone**

In `ActionDecisionRenderer.scala`:

- Delete the `ActionPane` doc comment and case class.
- `actionsPanel` takes `controls: TableControls` and returns `dom.Element`. Delete `var prompt` and `promptOnce`.
- Replace the follow-up block and the start of the Act block:

```scala
   val followUpPending = value.phase == "follow-up-pending" &&
     presentation.showGameplayControls
   if followUpPending then
     FollowUpControls.render(value, canControl, panel, submitCommand)
   if showActActionControls(value, presentation) then
     val groups = new ActionSections
```

  That is: delete the comment "A queued action's start opens the modifier flow…", the `drafts.modifiers` conditions, the whole `if drafts.modifiers.nonEmpty then` ordering panel (through the "Cancel action" button), and the `else`. Dedent what was inside the `else` by two spaces.
- The last line, `ActionPane(panel, prompt)`, becomes `panel`.

Every caller that read `.element` off the result now takes the result itself. In main that is `TableScreen` (Step 5). In the tests, run:

```bash
perl -0pi -e 's/(actionsPanel\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))\.element/$1/g' \
  frontend/src/test/scala/oathdigital/frontend/*.scala
```

Then compile (Step 12); fix by hand any `.element` the compiler still reports on an `actionsPanel` result.

- [ ] **Step 7: `GameClient` loses `preview` and `orderedModifiers`**

In `GameClient.scala`:

- The protocol import becomes:

```scala
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  ProtocolDecodeFailure, GameIntent => GameCommand}
```

- In the `GameClient` trait, delete the `orderedModifiers` parameter of `submit` and the whole `preview` method.
- In `HttpGameClient` and `TrustedHttpGameClient`, delete the `orderedModifiers` parameter of `submit`, pass `GameJson.encodeCommand(expectedNextSequence, command)`, and delete each `preview` override.
- `GameJson.encodeCommand` becomes:

```scala
  def encodeCommand(sequence: Long, command: GameCommand): String =
    ActorlessCommandCodec.encode(ActorlessCommandRequest(sequence, command))
```

- [ ] **Step 8: Docs in code, and the styles**

In `FollowUpControls.scala`, the doc's last sentence "The start goes through the modifier flow, so modifiers are offered as for a normal start." becomes "The start asks for the action's modifiers inside the walk, as a normal start does."

In `frontend/styles.css`:

- Delete the flow-only rules: `.modifier-option`, `.modifier-toggle`, the comment and rule for `.modifier-toggle .card-face`, `.modifier-modifies, .modifier-cost`, `.modifier-ordinal`, and `.modifier-earlier, .modifier-later, .modifier-confirm, .modifier-back, .modifier-cancel`.
- In the `.pane-actions :where(.move-option, …)` selector, drop `.modifier-earlier` and `.modifier-later`:

```css
.pane-actions :where(.move-option, .put-on-top,
    .distribute-decrement, .distribute-increment) { border-color: #7c694b;
  border-radius: 4px; background: #302a20; color: #e8d9bb; }
```

- In the helper-ink selector list (`.selection-cardinality, .board-draft, …`), drop `.modifier-instruction`.

Then check nothing else uses the classes:

```bash
grep -rn "modifier-\(option\|toggle\|modifies\|cost\|ordinal\|earlier\|later\|confirm\|back\|cancel\|instruction\)" frontend
```

Expected: no output.

- [ ] **Step 9: The session's tests**

In `RecordingControls.scala`:

- `extends ActionControls` becomes `extends TableControls`.
- Delete the five no-op stubs and the `PreviewModifier` import (`import oathdigital.protocol.GameIntent => Intent`).
- Its doc's first lines become "Spy of `TableControls`: records what was staged and submitted instead of touching a session, shared by every panel render suite." Delete "every other method is a no-op stub." from the doc.

In `TableSessionSuite.scala`:

- The protocol import becomes `import oathdigital.protocol.GameIntent => GameCommand`.
- In `RecordingClient`, delete the `Preview` case class, `previews`, `pendingPreviews`, `preview` and `answerPreview`. `submit` loses its `orderedModifiers` parameter. Its doc becomes "Spy of `GameClient`: records every load, submit and log load."
- In "a stale-position submit empties the drafts, reloads, and keeps the notice", `fixture.session.send(GameCommand.BeginRest, Vector.empty)` becomes `fixture.session.submitCommand(GameCommand.BeginRest)`.
- Delete "a preview answered after the session changed never lands" (with its `/** Spec, Fix 2. */` comment), "a preview answered under the same session lands", and "fail shows the failure and redraws; replaceDrafts does not redraw".
- Add these three in their place:

```scala
  test("a submitted command is sent as it is, at the displayed position"):
    displayed(2).map { fixture =>
      val start = GameCommand.StartWalker("recover", Vector.empty)
      fixture.session.submitCommand(start)
      assertEquals(fixture.client.submits, Vector(Submit(2L, start)))
    }

  test("with no position displayed, nothing is sent"):
    val fixture = new Fixture(trusted = false)
    fixture.session.submitCommand(GameCommand.BeginRest)
    assertEquals(fixture.client.submits, Vector.empty)

  test("staging replaces one slot and redraws"):
    displayed(2).map { fixture =>
      val before = fixture.redraws
      val board = BoardSelectionDraft(ForgeFixture.context, "forge-9",
        ForgeFixture.query, Vector("site:site:a"))
      fixture.session.stage(Draft.Board(Some(board)))
      assertEquals(fixture.session.viewedDrafts.board, Some(board))
      assertEquals(fixture.redraws, before + 1)
    }
```

In `ServerModeSessionSuite.scala`, replace "a previewed major action orders its modifier and posts the ordered command" (with its doc comment) by:

```scala
  /** An Act button posts its start at once: the action asks for its
    * modifiers inside the walk, so nothing is previewed first. */
  test("an Act button posts its start directly"):
    TestBrowser.use("?gameId=g&playerId=red"): browser =>
      val projection = GameProjectionCodec.encode(
        TestProjections.actingProjection(gameId = "g",
          phase = "act-action-selection",
          players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
          legalControls = Vector("beginRecover"))
          .copy(actionSelectionOpen = true))
      val transport = ScriptedTransport((_, url, _) =>
        val json = if url.contains("/events") then """{"events":[]}"""
          else projection
        Future.successful(Right(TransportResponse(200, json))))
      val requests = transport.requests
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ =>
        browser.click("recover-action")
        browser.settle
      }.map { _ =>
        val posts = requests.filter(_.method == "POST").toVector
        assertEquals(posts.map(_.url), Vector(
          "/api/dev/first-games/g/commands?playerId=red"))
        val command = present(posts.head.body, "the posted command body")
        assert(command.contains("\"action\":\"recover\""), command)
      }
```

In `HttpGameClientSuite.scala`, "trusted client uses canonical cookie APIs and actorless bodies" loses its preview: drop the second scripted response, the `client.preview(…)` step and the expected `"POST" -> "/games/game%20%2F%3F/api/preview"`, and drop `MajorActionPreviewRequest` from the import. The test becomes:

```scala
  test("trusted client uses canonical cookie APIs and actorless bodies"):
    val transport = transportAnsweringInOrder(Vector(
      Right(TransportResponse(200, projectionJson(7))),
      Right(TransportResponse(200, projectionJson(8)))))
    val client = new TrustedHttpGameClient(transport)
    client.load("game /?", "seat-must-not-travel").flatMap { loaded =>
      assertEquals(accepted(loaded).nextSequence, 7L)
      client.submit("game /?", "seat-must-not-travel", 7,
        GameIntent.RevealOwnedRelic("relic:a"))
    }.map { submitted =>
      assertEquals(accepted(submitted).nextSequence, 8L)
      assertEquals(transport.requests.map(r => r.method -> r.url).toVector,
        Vector(
          "GET" -> "/games/game%20%2F%3F/api",
          "POST" -> "/games/game%20%2F%3F/api/commands"))
      assertEquals(transport.requests.head.body, None)
      assertEquals(ActorlessCommandCodec.decode(
        present(transport.requests.last.body, "the submitted command body")),
        Right(ActorlessCommandRequest(7, GameIntent.RevealOwnedRelic("relic:a"))))
      assert(!transport.requests.toString.contains("seat-must-not-travel"))
    }
```

- [ ] **Step 10: The draft set's tests**

In `SessionDraftsSuite.scala`:

- Delete the `PreviewModifier` import, the `modifier` and `ordering` values, and the tests "every exit clears only the flow draft", "Ordering sets only the flow draft" and "Toggle and Move map the selection and nothing else".
- `full` becomes `SessionDrafts.empty.reconcile(context, projection())`, and its doc "Every slot the projection fills, all bound to `context`."
- In "reconcile fills every slot the projection asks for and sets the context", delete the `drafts.modifiers` assertion.
- Replace "reconcile keeps every slot under the same context and drops them under another" with:

```scala
  test("reconcile keeps every slot under the same context and drops them under another"):
    val moved = present(full.partition, "a partition draft")
      .move("denizen:denizen:3", "pay-favor")
    val staged = full.staged(Draft.Partition(moved))
    assertEquals(staged.reconcile(context, projection()).partition,
      Some(moved))
    val fresh = staged.reconcile(context.copy(sequence = 10), projection())
    assertEquals(fresh.partition.map(_.context),
      Some(context.copy(sequence = 10)))
    assertNotEquals(fresh.partition, Some(moved))
```

- The suite doc becomes "The draft set as a value: what `reconcile` keeps and drops, and what `staged` touches."

If `reconcile` at the same context does not keep the moved partition, stop and report: that would be a draft-set behaviour this task must not change.

- [ ] **Step 11: The controls suites**

Delete these tests, each to the end of its body:

- `FollowUpControlsSuite`: "a queued start that offers modifiers shows their order in place of the panel". Drop `MajorActionPreviewResponse` from its import: `import oathdigital.protocol.GameIntent`.
- `BannerControlsSuite`: "Challenge starts through the modifier flow and Place Banner Resource does not".
- `NegotiationControlsSuite`: "Negotiation does not start through the modifier flow".
- `CampaignControlsSuite`: "Campaign does not start through the modifier flow".

Each was the last test in its file; make sure the file still ends with a newline.

- [ ] **Step 12: Compile and run the frontend tests**

Run: `./sbtw "frontend/test"`

Expected: PASS. Fix any remaining reference the compiler reports (an `.element`, an unused import) without changing behaviour. The frontend count drops by 40: 19 + 9 + 3 + 2 suite tests, 3 in `SessionDraftsSuite`, and 1 each in the four controls suites. `TableSessionSuite` and `ServerModeSessionSuite` keep their counts.

Then check nothing outside the shared wire still names the flow:

```bash
grep -rn "ModifierFlow\|ModifierSelection\|FlowHost\|ActionControls\|PreviewModifier\|MajorActionPreview\|orderedModifiers" frontend/src
```

Expected: no output.

- [ ] **Step 13: Commit**

```bash
git add -u frontend
git commit -m "feat(frontend): start every action directly and delete the modifier flow"
```

`git add -u frontend` stages only tracked files under `frontend/`, including the deletions from Step 1. End the message with your trailer.

---
### Task 2: Forge, Challenge and Campaign open with the modifier decision

**Files:**
- Create: `src/test/scala/oathdigital/testkit/EmptyWindowModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/forge/ForgeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Test: `src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala`, `ChallengeProcedureSuite.scala`, `CampaignProcedureSuite.scala`

**Interfaces:**
- Consumes: `ModifierChoice.loop(actor, window, rest, startLabel, explain = None)` and `ModifierPicks.start`, from slice 1.
- Produces:
  - `ForgeProcedure.startLabel`, `ChallengeProcedure.startLabel`, `CampaignProcedure.startLabel`: "Start Forge", "Start Challenge", "Start Campaign".
  - Each tree is `Sequence(Vector(ModifierChoice.loop(…), rest))`; the action itself is the root's second child and keeps its `*ActionEligibility` window.
  - `EmptyWindowModifier.OnTents(actions)`, `EmptyWindowModifier.powersFor(action): WalkerPowers` and `EmptyWindowModifier.parkedWithFirstOption(state, powers): (String, Option[DecisionOption])`, for tests.

No power selects at these three windows, so with the production catalog the decision asks nothing and records nothing (spec ruling 11). Event streams, the Game Log and its goldens do not change. Walker paths gain a leading `"1"`.

- [ ] **Step 1: A stand-in modifier for the empty windows**

Create `src/test/scala/oathdigital/testkit/EmptyWindowModifier.scala`:

```scala
package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** A modifier for an action no card gives one to yet: Forge, Challenge or
  * Campaign. It stands in for the future power those windows are kept for
  * (modifier walker design, ruling 11).
  */
object EmptyWindowModifier:
  /** Stub of SelectedModifier: free, printed on Tents (whose catalog power
    * is player-selected), picked for `actions`, with no effect. */
  final case class OnTents(actions: Set[MajorActionType])
      extends SelectedModifier:
    def catalog: ExecutableCatalog = FirstGameSetupFixture.catalog
    val cardId: CardId = CatalogNames.denizen("Tents")
    def id: PowerId = PowerId("denizen.tents")
    def effects: Map[PowerWindow, Vector[Contribution]] = Map.empty

  /** That modifier for `action`, and no other power. */
  def powersFor(action: MajorActionType): WalkerPowers =
    WalkerPowers(Vector(OnTents(Set(action))))

  /** The decision `state` is parked on, and its first option, rebuilt with
    * `powers`. */
  def parkedWithFirstOption(state: OathState, powers: WalkerPowers)(
      using munit.Location): (String, Option[DecisionOption]) =
    ParkedNode.of(state, FirstGameSetupFixture.catalog, powers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.decisionId -> (decide.query match
          case DecisionQuery.ChooseOne(options, _) => options.headOption
          case _ => None)
      case other =>
        munit.Assertions.fail(s"expected a parked decision, got $other")
```

- [ ] **Step 2: Write the failing tests**

Add to `ForgeProcedureSuite` (it already has `forgeBoard()` and `actor`):

```scala
  test("a modifier for the Forge is asked about first, beside Start Forge"):
    val powers = EmptyWindowModifier.powersFor(MajorActionType.Forge)
    val board = forgeBoard().adviser(actor, "Tents").ready
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(board), ActionRef.Forge, actor))
    assertEquals(
      EmptyWindowModifier.parkedWithFirstOption(started.state, powers),
      ("forge.modifiers", Some(DecisionOption.Button(ModifierPicks.start,
        "Start Forge"))))
```

Add to `ChallengeProcedureSuite`:

```scala
  test("a modifier for the Challenge is asked about first, beside Start " +
      "Challenge"):
    val powers = EmptyWindowModifier.powersFor(MajorActionType.Challenge)
    val board = challenger.peoplesFavor(None, favor = 2).adviser(p1, "Tents")
      .ready
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(board), ActionRef.Challenge, p1))
    assertEquals(
      EmptyWindowModifier.parkedWithFirstOption(started.state, powers),
      ("challenge.modifiers", Some(DecisionOption.Button(ModifierPicks.start,
        "Start Challenge"))))
    // Nothing is spent until the Challenge starts.
    assertEquals(Look(started.state).supply(p1), 7)
```

Add to `CampaignProcedureSuite`:

```scala
  test("a modifier for the Campaign is asked about first, beside Start " +
      "Campaign"):
    val powers = EmptyWindowModifier.powersFor(MajorActionType.Campaign)
    val b = board(warbands = 5, supply = 7)
    val withTents = on(b)(_.adviser(b.actor, "Tents"))
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(withTents.ready), ActionRef.Campaign, b.actor))
    assertEquals(
      EmptyWindowModifier.parkedWithFirstOption(started.state, powers),
      ("campaign.modifiers", Some(DecisionOption.Button(ModifierPicks.start,
        "Start Campaign"))))
```

Add the imports each suite lacks: `oathdigital.testkit.EmptyWindowModifier`, `oathdigital.gameplay.powerresolver.ModifierPicks`, and `oathdigital.testkit.Table` or `oathdigital.model.OathState.Ready` where missing.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ForgeProcedureSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.CampaignProcedureSuite"`

Expected: the three new tests FAIL. Each start parks on the action's first own decision or finishes, not on `*.modifiers`.

- [ ] **Step 4: Forge opens with the decision**

In `ForgeProcedure.scala`:

- Add `import oathdigital.gameplay.walker.ModifierChoice`.
- Rename the private `tree(actor: PlayerId, cost: Tokens)` to `forging(actor: PlayerId, cost: Tokens)`. Keep its body and its doc comment.
- Add, above `forging`:

```scala
  /** "Start Forge": the modifier decision's button. */
  val startLabel: String = "Start Forge"

  /** The modifier decision, then the Forge. No power selects at
    * `ForgeModifierSelection` yet, so the decision asks nothing; a power
    * that does needs no change here. */
  private def tree(actor: PlayerId, cost: Tokens): Operation =
    val rest = forging(actor, cost)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.ForgeModifierSelection, rest, startLabel), rest))
```

- In the object's doc comment, the tree becomes:

```scala
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                    // the modifiers, one per
  *                                              // pass, then "Start Forge"
  *   Sequence(                                  // window = ForgeActionEligibility
  *     BuildOps(SpendSupply(actor, 1)),        // window = ForgeCost
  *     Branch(-> Decide("forge.assignment")),   // mixed printed cost only
  *     BuildOps(one PayCost per placed resource,
  *              Play(relic-deck top -> play area, FaceDown))))
  * }}}
```

- In the "Windows:" paragraph, "the root carries `ForgeActionEligibility`" becomes "the Forge itself, the root's second child, carries `ForgeActionEligibility`". Replace "`ForgeModifierSelection` is not a tree node: it is the window a player-selected power is offered at, answered by `StartWalker`'s `modifiers`. No other node carries a window." with "The modifier decision carries `ForgeModifierSelection`. No other node carries a window."

- [ ] **Step 5: Challenge opens with the decision**

In `ChallengeProcedure.scala`:

- The walker import becomes `import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers, WalkerSimulation}`.
- Replace `build`, `startable` and `rebuild`, and rename the private `tree(actor)` to `challenge(actor)`, keeping its body:

```scala
  /** "Start Challenge": the modifier decision's button. */
  val startLabel: String = "Start Challenge"

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noStartArgs(args)
    _ <- afterModifiers(state, actor)
  yield tree(actor)

  /** Whether Challenge could start now: the gates pass and the first walk
    * after the modifier decision (the Supply cost, up to the banner
    * decision) is accepted.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    afterModifiers(state, actor)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** The Challenge itself, behind the start's gates. */
  private def afterModifiers(state: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- supportedFaces(state)
    _ <- Either.cond(legalBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.Challenge.key))
  yield challenge(actor)

  /** The modifier decision, then the Challenge. No power selects at
    * `ChallengeModifierSelection` yet, so the decision asks nothing. */
  private def tree(actor: PlayerId): Operation =
    val rest = challenge(actor)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.ChallengeModifierSelection, rest, startLabel), rest))
```

- In the object's doc, after "Takes no start selection.", add: "It opens with the modifier decision, which asks nothing while no power selects at its window."

- [ ] **Step 6: Campaign opens with the decision**

In `CampaignProcedure.scala`:

- The walker import becomes `import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers, WalkerRollFeedback, WalkerSimulation}`. Re-wrap it to 80 characters.
- Rename the private `tree(catalog, state, actor)` to `battle(catalog, state, actor)`, keeping its body.
- Replace `build`, `startable`, `refusal` and `rebuild` with:

```scala
  /** "Start Campaign": the modifier decision's button. */
  val startLabel: String = "Start Campaign"

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noStartArgs(args)
    _ <- afterModifiers(catalog, state, actor)
  yield tree(catalog, state, actor)
```

  `NothingToAttack` stays where it is, between `build` and `startable`. Then:

```scala
  /** Whether Campaign could start now: the gates pass and the first walk
    * after the modifier decision (the Supply cost, up to the first
    * decision) is accepted, restrictions included.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    afterModifiers(catalog, state, actor)
      .exists(WalkerSimulation.starts(_, state, powers))

  /** Why a Campaign could not start now, or `None` when it could (follow-up
    * actions design, "Projection and UI"). A restriction answers first, so
    * Vow of Peace is the reason even when nothing could be attacked; then
    * the gates; then the first walk. */
  def refusal(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Option[OathViolation] =
    WalkerSimulation.restriction(battle(catalog, state, actor), state, powers)
      .orElse(afterModifiers(catalog, state, actor)
        .fold(Some(_), WalkerSimulation.refusal(_, state, powers)))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(catalog, state, actor))

  /** The Campaign itself, behind the start's gates. */
  private def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- Either.cond(CampaignSetup.legalKinds(state, actor).nonEmpty, (),
      OathViolation.CampaignUnavailable(NothingToAttack))
  yield battle(catalog, state, actor)

  /** The modifier decision, then the Campaign. No power selects at
    * `CampaignModifierSelection` yet, so the decision asks nothing. Battle
    * plans are not modifiers: the Campaign asks for them itself. */
  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation =
    val rest = battle(catalog, state, actor)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.CampaignModifierSelection, rest, startLabel), rest))
```

`refusal` asks the restriction of `battle`, not of `tree`: Vow of Peace is a restriction at `CampaignActionEligibility`, which the battle's root carries.

- [ ] **Step 7: The registry says the three choose their modifiers**

In `WalkerProcedureRegistry.scala`, add `choosesModifiers = true` to the `ActionRef.Forge`, `ActionRef.Challenge` and `ActionRef.Campaign` entries. For Forge, the entry ends:

```scala
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Forge,
        args).flatMap(_ => ForgeProcedure.rebuild(catalog, state, activePlayer)),
      choosesModifiers = true),
```

For Challenge, after `rebuild = ChallengeProcedure.rebuild,`; for Campaign, after `rebuild = CampaignProcedure.rebuild,`. In the Challenge entry's doc, "Its first step spends the Supply" becomes "Its first step after the modifier decision spends the Supply".

- [ ] **Step 8: Run the three suites**

Run: `./sbtw "testOnly oathdigital.gameplay.ForgeProcedureSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.CampaignProcedureSuite"`

Expected: the three new tests PASS. `ForgeProcedureSuite` fails where it reads paths and the root; Step 9 fixes those.

- [ ] **Step 9: `ForgeProcedureSuite` walks the decision first**

The suite walks `ForgeProcedure.build`'s tree with no powers. The decision asks nothing, but every path gains a leading `"1"`, and the Forge is the root's second child.

- In `parkAtAssignment`, `assertEquals(parked.tree.at, Vector("1", "0"))` becomes `assertEquals(parked.tree.at, Vector("1", "1", "0"))`. Its comment gains: "The Forge is the root's second child, after the modifier decision."
- Rename "a successful build roots at ForgeActionEligibility and pays one supply at a ForgeCost-windowed first leaf" to "a successful build's Forge sits at ForgeActionEligibility and pays one supply at a ForgeCost-windowed first leaf". Its first lines become:

```scala
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    // The modifier decision comes first; the Forge is the second child.
    val forge = tree.children(1)
    assertEquals(forge.window,
      Some(PowerWindow.ForgeActionEligibility): Option[PowerWindow])
    val first = forge.children.head
```

  Its node-id expectation `Vector("0.0")` becomes `Vector("1.0.0")`, and the comment's `("0.0", not "0")` becomes `("1.0.0", not "1.0")`.
- In "a three-favor or three-secret site declares no Decide node at all and resolves its forced split without parking", the two reads of `tree.children` become `tree.children(1).children`, and `Vector("0.0", "1")` becomes `Vector("1.0.0", "1.1")`.
- In "a legal answer finishes the walk…" and "a Forge started with exactly one supply is still resumable and answerable", `Vector("1.0", "2")` becomes `Vector("1.1.0", "1.2")`.
- Replace "the tree windows exactly its root and its supply payment; every other node carries none" with:

```scala
  test("the tree windows exactly its modifier decision, the Forge and its " +
      "supply payment; every other node carries none"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val windowed = allNodes(tree).filter(_.window.isDefined)

    assertEquals(windowed.size, 3,
      s"expected exactly 3 windowed nodes, found ${windowed.size}: $windowed")
    assertEquals(windowed.map(_.window).toSet, Set[Option[PowerWindow]](
      Some(PowerWindow.ForgeModifierSelection),
      Some(PowerWindow.ForgeActionEligibility), Some(PowerWindow.ForgeCost)))
    assert(windowed.contains(tree.children(1)),
      "the Forge, after the modifier decision, must carry " +
        "ForgeActionEligibility")
    assertEquals(windowed.collect { case node: BuildOps => node.window },
      Vector(Some(PowerWindow.ForgeCost)),
      "exactly the supply-paying BuildOps carries ForgeCost -- the " +
        "completion BuildOps stays unwindowed")
```

- [ ] **Step 10: Run every server test**

Run: `./sbtw "test"`

Expected: PASS, with 3 more server tests than the baseline. If another suite asserts a Forge, Challenge or Campaign walker path, prefix it as above. If a Game Log golden or an event-stream golden changes, stop and report: the decision should record nothing.

- [ ] **Step 11: Commit**

```bash
git add src/test/scala/oathdigital/testkit/EmptyWindowModifier.scala \
  src/main/scala/oathdigital/gameplay/actions/forge/ForgeProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala
git commit -m "feat(walker): Forge, Challenge and Campaign open with the modifier decision"
```

Add any other suite Step 10 made you edit. End the message with your trailer.

---
### Task 3: Recover asks for its modifiers inside the walk

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/powers/recover/RecoverModifierChoiceSuite.scala`
- Delete: `src/test/scala/oathdigital/gameplay/SelectionPaymentsSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`, `RelicWorship.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala`
- Modify (tests): `testkit/CatacombsGame.scala`, `gameplay/powers/recover/RelicWorshipSuite.scala`, `gameplay/CatacombsContributionSuite.scala`, `gameplay/RecoverProcedureSuite.scala`, `gameplay/WalkerReplayDriftSuite.scala`, `gameplay/OathRulesWalkerPowerSuite.scala`, `gameplay/powers/SelectedModifierSuite.scala`, `application/GameApplicationServiceRecoverSuite.scala`, `application/WalkerDecisionProjectionSuite.scala`, `application/UndoServiceSuite.scala`, `application/gamelog/LogScripts.scala`, `application/gamelog/GameLogStartLineSuite.scala`, `frontend/.../ParkedDecisionSuite.scala`

**Interfaces:**
- Consumes: `ModifierChoice.loop`, `ModifierPlay.started` / `pick` / `start`, `ModifierPicks.decisionId`, from slices 1–3.
- Produces:
  - `RecoverProcedure.startLabel: String` ("Start Recover").
  - `RecoverProcedure.afterModifiers(catalog, state, activePlayer): Either[OathViolation, Operation]`: the Recover itself, behind the start's gates.
  - `CatacombsContribution.noEmptySlot`, `CatacombsContribution.emptyRelicDeck`: its offer reasons.
  - `CatacombsGame.modifierDecisionId: String` ("recover.modifiers") and `CatacombsGame.modifierOption(state, card): DecisionOption`.
  - `SelectedModifier` without `paidAtPick`, `eligibility` and `isEligibility`; its `contributions` are its `effects`.
  - `LogScripts.recoverWorship`.

Recover prices no option, so "Start Recover" reads "Cannot start now" when the Recover cannot start. The Act panel never offers a Recover the player cannot pay for (`startable`), so the reason shows only when a modifier makes the decision ask.

- [ ] **Step 1: Two test helpers in `CatacombsGame`**

Add to `src/test/scala/oathdigital/testkit/CatacombsGame.scala`:

```scala
  /** "recover.modifiers". */
  val modifierDecisionId: String =
    ModifierPicks.decisionId(PowerWindow.RecoverModifierSelection)

  /** The option naming `card` in the modifier decision `state` is parked
    * on, rebuilt with the production powers. */
  def modifierOption(state: OathState, card: DenizenId)(
      using munit.Location): DecisionOption =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _)))
          if decide.decisionId == modifierDecisionId =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _) =>
            options.find(_.ref == DecisionOptionRef.Denizen(card))
              .getOrElse(munit.Assertions.fail(s"$card is not listed"))
          case other =>
            munit.Assertions.fail(s"expected a choose-one, got $other")
      case other =>
        munit.Assertions.fail(s"expected the modifier decision, got $other")
```

Imports: `oathdigital.gameplay.powerresolver.ModifierPicks`, `oathdigital.gameplay.powers.WalkerPowerCatalog`.

- [ ] **Step 2: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/recover/RecoverModifierChoiceSuite.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatacombsGame, CatalogNames, Look, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Recover asks for its modifiers inside the walk (modifier walker design,
  * slice 4). */
class RecoverModifierChoiceSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.shields)

  private val worship = CatalogNames.denizen("Relic Worship")

  /** p1 at Fair Isle, where Dowsing Sticks lies facedown, with `supply`
    * Supply, one faceup secret and Relic Worship as a faceup adviser; with
    * no Relic Worship unless `withWorship`. */
  private def atFairIsle(supply: Int = 4, withWorship: Boolean = true)
      : ReadyGame =
    val table = Table.start.pawn(p1, "Fair Isle")
      .relicAt("Dowsing Sticks", "Fair Isle").secrets(p1, faceUp = 1)
      .supply(p1, supply)
    (if withWorship then table.adviser(p1, worship) else table).ready

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Recover, p1))

  private val startRecover: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Recover")

  /** The first option of the decision `state` is parked on. */
  private def firstOption(state: OathState)(using munit.Location)
      : DecisionOption =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _) => options.head
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a decision, got $other")

  test("a Recover starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(atFairIsle())
    // Nothing is spent or rolled until the Recover starts.
    assertEquals(Look(begun.state).supply(p1), 4)
    assertEquals(CatacombsGame.modifierOption(begun.state, worship),
      DecisionOption.Priced(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(worship)),
        OptionPrice(secrets = 1)))

  test("with no modifier to pick, a Recover rolls at once"):
    val begun = started(atFairIsle(withWorship = false))
    assertEquals(Look(begun.state).supply(p1), 4 - 1)
    assertEquals(
      Outcomes.ready(begun.state).game.current.walkerPending.map(_.at),
      // The relic decision, after the modifier decision asked nothing.
      Some(Vector("1", "2", "0")))

  test("Start says the Recover cannot start without the Supply to roll"):
    val begun = started(atFairIsle(supply = 0))
    assertEquals(firstOption(begun.state), DecisionOption.Unavailable(
      startRecover,
      OptionReason.Written("Cannot start now")))

  test("a start may no longer carry modifiers for a Recover"):
    assertEquals(rejected(rules.startWalker(Ready(atFairIsle()),
      ActionRef.Recover, p1, Vector(RelicWorship.id))),
      OathViolation.InvalidEventOrder(
        "recover chooses its modifiers inside the walk"))

```

The relic decision's path in the second test is `Vector("1", "2", "0")`: the Recover is the root's second child, its success Branch is its third, and the relic decision is that Branch's first. If the walk parks elsewhere, print the path and fix the expectation only if the parked decision is `RecoverProcedure.relicDecisionId`.

Add to `ParkedDecisionSuite` (frontend):

```scala
  test("a Recover modifier decision goes to the board, as any action's does"):
    val modifiers = parked("recover",
      ParkedDecision.recoverModifiersDecisionId, one)
      .copy(presentation = "board")
    assertEquals(routeOf(modifiers).surface,
      Some(Surface.BoardDecision(modifiers, one)))
```

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.recover.RecoverModifierChoiceSuite"`

Expected: FAIL. The start rolls at once and `startLabel` is not there.

- [ ] **Step 4: Recover opens with the decision**

In `RecoverProcedure.scala`:

- The walker import becomes `import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers, WalkerRollFeedback, WalkerSimulation}`, wrapped to 80 characters.
- Rename the private `tree(actor: PlayerId, siteId: SiteId, difficulty: Int)` to `recovery(…)`, keeping its body and doc comment.
- Replace `build`, `startable` and `rebuild` with:

```scala
  /** "Start Recover": the modifier decision's button. Recover prices no
    * option, so when it cannot start the button says "Cannot start now". */
  val startLabel: String = "Start Recover"

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId)
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    built <- rebuild(catalog, state, activePlayer)
  yield built

  /** The Recover itself, after its modifier decision, behind the start's
    * gates. */
  def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    rest <- recoveryAt(catalog, state, activePlayer)
  yield rest

  /** Whether Recover could start now: the gates pass and the first walk
    * after the modifier decision (the Supply cost, up to the first roll) is
    * accepted, so a player without the Supply is not offered it.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers): Boolean =
    afterModifiers(catalog, state, activePlayer)
      .exists(WalkerSimulation.starts(_, state, powers))

  /** Rebuilds the same command-local tree for an already-started Recover:
    * the modifier decision, then the Recover. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] =
    recoveryAt(catalog, state, activePlayer).map(rest =>
      Sequence(Vector(ModifierChoice.loop(activePlayer,
        PowerWindow.RecoverModifierSelection, rest, startLabel), rest)))

  /** The Recover at the actor's site, which must have a Recover
    * difficulty. */
  private def recoveryAt(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    siteId <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  yield recovery(actor, siteId, difficulty)
```

- In the object's doc comment, the tree gains the decision. Indent the existing diagram by two spaces inside a new outer `Sequence`:

```scala
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                  // the modifiers, one per
  *                                            // pass, then "Start Recover"
  *   Sequence(                                // window = RecoverActionEligibility
  *     ModifyDicePool("recover", +2),         // window = RecoverBeforeFirstRoll
  *     …the Repeat and the relic Branch, as before…))
  * }}}
```

  Write the Repeat and Branch lines out in full, as they are now, two spaces deeper.
- In the window list, "`RecoverActionEligibility` - At the start of the action, for checking prerequisites" becomes "`RecoverActionEligibility` - At the start of the Recover itself, after the modifier decision".

`recover.choice`'s `stopped` and the relic move read `pending.answered.lastOption`. Both stay correct: after "Start", the last answer is the `start` button, which is not "stop", and the relic move runs right after the relic answer.

- [ ] **Step 5: The registry says Recover chooses its modifiers**

In `WalkerProcedureRegistry.scala`, the `ActionRef.Recover` entry ends:

```scala
      rollFeedback = RecoverProcedure.rollFeedback,
      choosesModifiers = true),
```

- [ ] **Step 6: Run the new suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.recover.RecoverModifierChoiceSuite"`

Expected: PASS.

- [ ] **Step 7: Catacombs is picked in the walk**

Replace the class and companion in `CatacombsContribution.scala` (keep `CatacombsCard` as it is):

```scala
/** Catacombs (Task 5), a Recover modifier picked in the walk's modifier
  * decision. The pick pays its secret onto the card. Once picked, a
  * Transform at `RecoverActionEligibility` (ruling C) places the relic
  * deck's top relic facedown; ruling K keeps permission and effect in one
  * contribution. The card may sit at the pawn's site, at a site the actor
  * rules, or be an adviser. The relic goes to the card's own site, or to
  * the pawn's site for an adviser.
  *
  * A site with no empty relic slot, or an empty relic deck, shows the pick
  * unavailable, so the secret is never paid for a relic that cannot be
  * placed. */
final case class CatacombsContribution private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CatacombsCard.id
  def id: PowerId = CatacombsContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def selectionCard: Option[CardId] = Some(cardId)
  override def selectionWindows: Set[PowerWindow] =
    Set(PowerWindow.RecoverModifierSelection)
  // Stable across the action: card presence, never the relic/secrets spent.
  // At a modifier-selection window it is offered for a Recover only.
  override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window).forall(
      _ == MajorActionType.Recover) &&
      PowerAccess.locate(ctx.state, ctx.activePlayer, cardId).isDefined
  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    placement(ctx.state, ctx.activePlayer).left.toOption.collect {
      case OathViolation.RecoverUnavailable(reason) =>
        OfferReason.Unavailable(reason)
    }
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    Vector(Costs.onCard(actor, cardId, Cost(secret = 1), catalog))
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RecoverActionEligibility -> Vector(
      Transform((ctx, ops) => place(ctx.activePlayer) +: ops)))

  private def place(actor: PlayerId): Operation = BuildOps((ready, _) =>
    placement(ready, actor).map((siteId, relic) => Vector[CoreOperation](
      Move(Piece.Card(relic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(siteId)),
        resultingOrientation = Some(Orientation.FaceDown)))))

  /** Where the relic goes and which relic, or why it cannot be placed. No
    * generic execution path enforces a site's `relicSlots` for a card Move,
    * so the placement checks the capacity itself. */
  private def placement(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, (SiteId, RelicId)] = for
    siteId <- PowerAccess.siteOf(ready, actor, cardId)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- Either.cond(catalog.site(siteId).exists(site =>
      ready.game.current.map.sites.get(siteId).fold(0)(_.relics.size) <
        site.relicSlots),
      (), OathViolation.RecoverUnavailable(CatacombsContribution.noEmptySlot))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable(CatacombsContribution.emptyRelicDeck))
  yield (siteId, relic)

object CatacombsContribution:
  val id: PowerId = CatacombsCard.power.id
  /** Why it cannot be picked at a site whose relic slots are full. */
  val noEmptySlot: String = "This site has no empty relic slot"
  /** Why it cannot be picked with no relic left to draw. */
  val emptyRelicDeck: String = "The relic deck is empty"
  def forCatalog(catalog: ExecutableCatalog): CatacombsContribution =
    new CatacombsContribution(catalog)
```

The payment left `place`: the pick pays it now.

In `RelicWorship.scala`, replace the class doc comment with:

```scala
/** Relic Worship (card 173), a selected Recover modifier. Cost: 1 secret placed
  * on the card. After recovering a relic, gain 2 Supply.
  *
  * The secret is paid when it is picked in the walk's modifier decision
  * (the kit does it), and the gain follows the move that takes the relic, at
  * `RecoverAfterRelic`. A Recover that ends without a relic has still paid the
  * secret and gains nothing: the player owns the choice to pick it. Picking
  * needs a faceup secret and an empty card. Once one pick spends the last
  * faceup secret, the decision lists the other modifiers unavailable with the
  * shortfall.
  */
```

- [ ] **Step 8: Every modifier pays at the pick, so the kit's payment goes**

In `SelectedModifier.scala`:

- `contributions` becomes:

```scala
  final override lazy val contributions
      : Map[PowerWindow, Vector[Contribution]] = effects
```

- `applicable`'s last case becomes `case None => appliesAt(ctx)`.
- In the companion, delete `eligibilityWindows`, `paidAtPick` (with its doc), `eligibility` and `isEligibility`.
- Drop `Transform` from the `powerresolver` import.
- Replace the trait's doc comment with:

```scala
/** A power the player picks for a major action in the walk's modifier
  * decision (modifier walker design), and that then applies to that action
  * for free unless it names a cost.
  *
  * A modifier that names a `cost` pays it when it is picked, onto its card
  * (`selectionPayments`), whatever the modifier then does: the player owns
  * the choice to pick it, and the next pass of the decision sees what it
  * paid.
  *
  * `applicable` answers two questions, told apart by the window:
  *
  *  - At a `*ModifierSelection` window it asks "may the player select this
  *    now?": the power belongs to that action, its card is one the player may
  *    use (`PowerAccess`), and its `cost` is payable on its own, including the
  *    empty-card rule. A power already picked in `ctx.answered` always
  *    applies there.
  *  - At every other window it asks `appliesAt`, which reads only the node the
  *    power is hooked on. It must not read state the action itself changes,
  *    because the walker folds every window again on each resume and a fold
  *    that differs from the first one moves the parked position.
  *
  * The selection window is checked by action, so a Travel modifier is never
  * offered for a Search, whichever window a later power hooks.
  *
  * A power states what it does inside the walk as `effects`, by window. Its
  * resolution is read from the catalog (`persistent: false` is selected).
  */
```

Forest Paths hooks `TravelActionEligibility` with a Transform that changes nothing and applies only while its route reads a pawn move. Until now the eligibility rule applied it there regardless. Step 13 runs `ForestPathsSuite` to show it ignores site powers as before.

- [ ] **Step 9: The Recover tag waits for the dice**

In `StartLines.scala`:

- Add `import oathdigital.gameplay.actions.recover.RecoverProcedure`.
- In `opening`, before `case ActionRef.Travel | ActionRef.Search =>`, add:

```scala
      // The step that opens Recover's dice pool: the first of the Recover
      // itself, after its modifier decision.
      case ActionRef.Recover =>
        name(run.procedure).filter(_ =>
          opensDicePool(journal, at) || completing)
          .map(named => (named, Vector.empty))
```

- After `paysCost`, add:

```scala
  private def opensDicePool(journal: LogJournal, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(pool: ModifyDicePool, _, _) =>
        pool.pool == RecoverProcedure.recoverPool
      case _ => false
    }
```

- In `opening`'s doc, "except Muster and Trade (their cost step, after the source decision)," becomes "except Muster and Trade (their cost step, after the source decision), Recover (the step that opens its dice pool, after its modifier decision),".

An un-asked Recover's dice-pool step is its first, so its lines and the goldens do not change. A Recover whose last pick starts it by itself rolls in the same command, and the tag comes after that command's picks.

- [ ] **Step 10: The board draws a Recover's modifier decision**

In `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala`:

- Beside `recoverRelicDecisionId`, add:

```scala
  private[frontend] val recoverModifiersDecisionId = "recover.modifiers"
```

- After `recoverStep`, add:

```scala
  /** A Recover park only Recover's panel may answer: every one but its
    * modifier decision, which the board draws as any action's is. */
  private def recoverPanelOnly(decision: WalkerDecisionState): Boolean =
    decision.action == "recover" &&
      decision.decisionId != recoverModifiersDecisionId
```

- In `formSurface`, the board case's `decision.action != "recover" && onTable(…)` becomes `!recoverPanelOnly(decision) && onTable(…)`, and the refusing case's `if decision.action == "recover" || decision.kind != "decide"` becomes `if recoverPanelOnly(decision) || decision.kind != "decide"`.

- [ ] **Step 11: Convert the Recover suites**

**`SelectionPaymentsSuite`:** delete it (`git rm`). Its three stub tests covered the joint refusal at a start, which no action has now. Its Catacombs tests move below.

**`RelicWorshipSuite`:** starts pick through the decision.

- Imports: add `oathdigital.gameplay.ModifierPlay` to the `oathdigital.gameplay` import, and `oathdigital.testkit.CatacombsGame`. Drop `Refusals`.
- `recover`'s start becomes:

```scala
    val started = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection, selected))
```

- In "a Recover that ends without a relic has still paid the secret, and gains nothing", the start becomes `accepted(ModifierPlay.started(failing, Ready(ready), ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection, modifiers))`, and the comment "The secret is paid at the start, before the roll that fails." becomes "The secret is paid at the pick, before the roll that fails."
- Replace "Catacombs and Relic Worship with one faceup secret are refused at selection, and accepted with two" with:

```scala
  test("with one faceup secret, picking Catacombs leaves Relic Worship " +
      "unpayable; with two, both are picked and paid"):
    def table(secrets: Int) =
      relicWorshipAtFairIsleTable(secrets, relicAtSite = false).ready
    val begun = accepted(rules.startWalker(Ready(table(1)),
      ActionRef.Recover, p1))
    val afterCatacombs = accepted(rules.resolveWalker(begun.state, p1,
      CatacombsGame.modifierDecisionId,
      ModifierPlay.pick(CatacombsContribution.id)))
    assertEquals(CatacombsGame.modifierOption(afterCatacombs.state, worship),
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(worship)),
        OptionReason.Written("Needs 1 secret; you have 0")))
    val both = accepted(ModifierPlay.started(rules, Ready(table(2)),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      Vector(CatacombsContribution.id, RelicWorship.id)))
    // Catacombs and Relic Worship each pay one of the two secrets.
    assertEquals(me(Outcomes.ready(both.state)).board.faceUpSecrets, 0)
```

- Replace "it cannot be selected without a faceup secret, or onto an occupied card" with:

```scala
  test("without a faceup secret nothing is asked; onto an occupied card it " +
      "is listed unpayable"):
    // Neither modifier can be paid for, so the Recover rolls at once.
    val broke = accepted(rules.startWalker(
      Ready(relicWorshipAtFairIsle(secrets = 0)), ActionRef.Recover, p1))
    assertEquals(me(Outcomes.ready(broke.state)).board.supply.supply, 4 - 1)
    // Catacombs can be picked at a relic-less Fair Isle, so the decision
    // asks, and lists Relic Worship unpayable.
    val occupied = relicWorshipAtFairIsleTable(relicAtSite = false)
      .tokens(worship, secrets = 1).ready
    val begun = accepted(rules.startWalker(Ready(occupied),
      ActionRef.Recover, p1))
    assertEquals(CatacombsGame.modifierOption(begun.state, worship),
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(worship)),
        OptionReason.Written("Cannot be paid for now")))
```

- Delete the now-unused `val catacombs = PowerId("denizen.catacombs")` if it was local to the replaced test; it was.

**`CatacombsContributionSuite`:** Catacombs is picked; its secret is paid at the pick; the Recover is the root's second child.

- Imports: add `oathdigital.gameplay.operations.Costs` and `oathdigital.gameplay.powers.recover.CatacombsContribution`.
- `started` becomes:

```scala
  private def started(fixture: Game, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(ModifierPlay.started(rules, Ready(fixture.ready),
      ActionRef.Recover, fixture.actor, PowerWindow.RecoverModifierSelection,
      modifiers))
```

- Replace the first test with:

```scala
  test("Catacombs makes a relic-less site recoverable: the pick pays its " +
      "secret, and the Recover places the relic in a step attributed to " +
      "the power"):
    val fixture = reliclessSite(secrets = 2)
    val transition = started(fixture, Vector(catacombsId))
    val acting = steps(transition.events).filter(_.ops.nonEmpty)

    // The pick's payment, onto the card.
    assertEquals(acting.head.ops, Vector[CoreOperation](
      PayCost(fixture.actor, Location.OnCard(catacombsCard),
        Cost(secret = 1), matchingBank = catalog.suitOf(catacombsCard))))
    // The transform prepended ONE node to the Recover's children, so the
    // placement is the Recover's first step, naming Catacombs.
    val placed = acting(1)
    assertEquals(placed.nodeId, "1.0")
    assertEquals(placed.contributions, Vector(catacombsId))
    assertEquals(placed.ops, Vector[CoreOperation](
      Move(Piece.Card(fixture.topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(fixture.site)),
        resultingOrientation = Some(Orientation.FaceDown))))

    val after = Outcomes.ready(transition.state)
    val site = after.game.current.map.sites(fixture.site)
    assertEquals(site.relics.map(relic => relic.id -> relic.orientation),
      Vector(fixture.topRelic -> Orientation.FaceDown))
    assertEquals(site.denizens.head.tokens.secrets, 1)
    // p1's 2 faceup secrets, less the 1 paid onto Catacombs.
    assertEquals(Look(after).player(fixture.actor).board.faceUpSecrets, 2 - 1)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck.tail)

    // The Recover is the root's second child, after the modifier decision,
    // and the inserted node shifts each later index by one: the choice the
    // failed first roll parks on (the Repeat, its first round, the round's
    // choice Branch, its Decide) sits under "2" instead of the bare
    // Recover's "1".
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "2", "0", "2", "0")))
```

- In "without the Catacombs modifier the same relic-less start remains legal", the expected park becomes `Some(Vector("1", "1", "0", "2", "0"))`, and the comment begins "The Recover, after the modifier decision (1), parks the failed first roll's choice at the Repeat (1), …".
- In "a site that already holds a facedown relic starts with no modifier and records no placement operations", the node ids become `Vector("1.0.0", "1.1.0.0", "1.1.0.1")` and the park `Some(Vector("1", "1", "0", "2", "0"))`. Catacombs is listed unavailable there (no empty slot) and nothing else can be picked, so nothing is asked.
- In "the Catacombs fold survives its own effect…", the expected park becomes `Some(Vector("1", "2", "0", "2", "0"))`.
- Replace "Catacombs is rejected when the site's relic slots are full" with:

```scala
  test("Catacombs is listed unavailable at a full relic site and with an " +
      "empty relic deck"):
    // Relic Worship can be picked, so the decision asks and lists Catacombs.
    def withWorship(game: Game): ReadyGame =
      Table.from(game.ready).adviser(Table.p1, "Relic Worship").ready
    def catacombsOption(ready: ReadyGame)(using munit.Location)
        : DecisionOption =
      val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Recover,
        Table.p1))
      modifierOption(begun.state, catacombsCard)
    def unavailable(reason: String): DecisionOption =
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(catacombsCard)),
        OptionReason.Written(reason))
    assertEquals(catacombsOption(withWorship(fullRelicSite())),
      unavailable(CatacombsContribution.noEmptySlot))
    val noRelics = withWorship(reliclessSite(secrets = 2)).updateCurrent(
      current => current.copy(commonCards =
        current.commonCards.copy(relicDeck = Vector.empty)))
    assertEquals(catacombsOption(noRelics),
      unavailable(CatacombsContribution.emptyRelicDeck))
```

- Replace "Catacombs at a site the actor neither rules nor stands on is not applicable" with:

```scala
  test("Catacombs at a site the actor neither rules nor stands on is not " +
      "listed"):
    val fixture = ruledElsewhere(ruled = false)
    val begun = accepted(rules.startWalker(Ready(fixture.ready),
      ActionRef.Recover, fixture.actor))
    val after = Outcomes.ready(begun.state)
    // Nothing to pick, so nothing is asked: the Recover rolls at once.
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "1", "0", "2", "0")))
    assertEquals(after.game.current.map.sites(fixture.site).relics,
      fixture.ready.game.current.map.sites(fixture.site).relics)
```

- Add, from `SelectionPaymentsSuite`:

```scala
  test("Catacombs pays its secret onto its card, as its selection payment"):
    val fixture = reliclessSite(secrets = 1)
    val power = CatacombsContribution.forCatalog(catalog)
    assertEquals(power.selectionPayments(fixture.ready, fixture.actor),
      Vector(Costs.onCard(fixture.actor, power.cardId, Cost(secret = 1),
        catalog)))
```

- The suite doc's "on use places the relic deck's top card facedown at the site for a printed 1-secret cost placed on the Catacombs card itself" becomes "when picked, pays a printed 1-secret cost onto the Catacombs card, and the Recover then places the relic deck's top card facedown at the site".

**`RecoverProcedureSuite`:** replace every `RecoverProcedure.build(` with `RecoverProcedure.afterModifiers(`, except in "a supply-zero initial roll is rejected, and resume derivation…". The tests walk the Recover itself, as before, so their paths do not change. In "the tree windows exactly its root, its pool-opening node, and its relic-moving node; every other node carries none", the assertion message "the tree root must carry RecoverActionEligibility" becomes "the Recover's root must carry RecoverActionEligibility".

**`WalkerReplayDriftSuite`:** Catacombs is picked by an answer, on both tracks.

- Replace `catacombsPowers` and its doc with:

```scala
  /** The production catalog with nothing picked: the modifier decision's
    * answer picks Catacombs, and both tracks read that answer. Both tracks
    * receive this SAME value, matching the invariant the suite-level doc
    * states: "the powered walk must gather the same contributions on both
    * tracks, or you are testing your test harness rather than the engine."
    */
  private val catacombsPowers: WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)
```

- In "drift check: Catacombs-modified Recover (advance -> resolve) -- the first corpus entry where a power changed the tree", the script becomes:

```scala
      Vector(StartWalk(),
        AnswerResume(Answered(CatacombsGame.modifierDecisionId,
          ModifierPlay.pick(CatacombsContribution.id), fixture.actor),
          Vector(highRoll)),
        AnswerResume(Answered(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(fixture.topRelic)),
          fixture.actor))), catacombsPowers)
```

  The start parks on the modifier decision without rolling; the pick starts the Recover, which rolls.

**`OathRulesWalkerPowerSuite`:** delete from the comment block that opens "Fix-round ruling I: a player-selected `StartWalker` modifier must persist across a resume" through the end of the test "StartWalker rejects a known but inapplicable modifier id before the walk runs". That removes four tests, the comment block before the last one and the `insertingPower` helper. Each started a Recover with modifiers, which the start now refuses. Delete any import the compiler then reports unused.

**`SelectedModifierSuite`:** delete "a cost not paid at the pick is paid at the root of the action's tree, and only for a costed modifier", "at the action's eligibility window a selected modifier always applies" and "a payment and an effect at the same window keep the payment first". Rename "the payment is what selecting it pays, for the combined check" to "the payment is what picking it pays". Add:

```scala
  test("a modifier contributes only its effects: its cost is paid at the " +
      "pick"):
    val priced = Probe(card, Cost(favor = 1), MajorActionType.values.toSet)
    assertEquals(priced.contributions,
      Map.empty[PowerWindow, Vector[Contribution]])
    // Outside its selection windows it asks only `appliesAt`, at the
    // action's eligibility window too.
    val idle = Probe(card, Cost(favor = 1), travel, applies = false)
    assert(!idle.applicable(ctx(quietTable, idle,
      PowerWindow.TravelActionEligibility)))
```

**`GameApplicationServiceRecoverSuite`:**

- "StartWalker drives Catacombs through the full persisted path…": the start asks for Catacombs, and its pick runs the Recover. Replace the `val started = service.handle(…) match …` block with:

```scala
    val begun = accepted(service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))))
    // Catacombs is the one modifier p1 can pick, so the start asks for it,
    // and picking it starts the Recover.
    val started = service.handle(gameId, begun.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("recover.modifiers",
        ModifierPlay.pick(catacombsPower)))) match
      case Right(accepted) => accepted
      case Left(error) =>
        fail(s"picking Catacombs must persist, got $error")
```

  The batch's first two ops are now the pick's payment, then the relic move: swap the two expected operations, and change the comment to "The pick's payment onto the card, then Catacombs' relic move off the top of the relic deck, verbatim, survived encoding." Import `oathdigital.gameplay.ModifierPlay`.
- "preview offers the walker Catacombs contribution, and OathRules.startWalker accepts exactly the offered id (Task 9a)": delete its second half, from the comment "The property that matters" to the end of the test. A start no longer takes the offered id. Task 4 deletes the rest with the preview.

**`WalkerDecisionProjectionSuite`:** in "the projector reports the decision the walker actually parked at with Catacombs in effect…", the start becomes:

```scala
    val started = accepted(ModifierPlay.started(rules, Ready(fixture.ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      Vector(CatacombsGame.catacombsId)))
```

Import `oathdigital.gameplay.ModifierPlay`. If the unwired projector no longer returns `None`, stop and report: it means the picks reach a projector that has no pool to find them in.

- [ ] **Step 12: Undo and the Game Log for a Recover pick**

Add to `UndoServiceSuite`:

```scala
  test("Undo takes back a Recover modifier pick, and then the start"):
    // Catacombs and Relic Worship can both be picked, so picking one asks
    // again instead of rolling.
    val withBoth = table.pawn(p1, "Fair Isle").secrets(p1, faceUp = 2)
      .denizen("Catacombs", at = "Fair Isle").adviser(p1, "Relic Worship")
    val (service, _) = withBoth.service()
    val started = accepted(service.handle("g", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1))))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("recover.modifiers",
        ModifierPlay.pick(RelicWorship.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withBoth.state)
```

Import `oathdigital.gameplay.powers.recover.RelicWorship`.

Add to `LogScripts`, after `revealRelic`:

```scala
  /** p1 recovers at Broken Peaks with Relic Worship, their adviser, and one
    * faceup secret. Relic Worship is the only modifier, so its pick starts
    * the Recover by itself. */
  def recoverWorship(using munit.Location): Script =
    val (service, act) = atTable("recover-worship",
      ParkedServiceFixture.recoverTable.adviser(p1, "Relic Worship")
        .secrets(p1, faceUp = 1).banditsRefilled, Dice.steady)
    act.withAnswers(ModifierPlay.answers(
      PowerWindow.RecoverModifierSelection, Vector(RelicWorship.id)))
      .after(GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))
    Script("recover-worship", service, p1)
```

Import `oathdigital.gameplay.powers.recover.RelicWorship`.

Add to `GameLogStartLineSuite`:

```scala
  test("a Recover with a pick names it on its own line, with no Chose line"):
    val entries = format(recoverWorship, None)
    val tag = entryStarting(entries, "Recover")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(text(tag), "Recover −1 Supply")
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      "With Relic Worship")
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))
```

- [ ] **Step 13: Run every test**

Run: `./sbtw "test" "frontend/test"`

Expected: PASS.

- Server: +4 (`RecoverModifierChoiceSuite`), −5 (`SelectionPaymentsSuite`), +1 (`CatacombsContributionSuite`), −4 (`OathRulesWalkerPowerSuite`), −2 (`SelectedModifierSuite`), +1 (`UndoServiceSuite`), +1 (`GameLogStartLineSuite`): −4 in all.
- Frontend: +1 (`ParkedDecisionSuite`).
- `ForestPathsSuite` passes unchanged.
- The Game Log goldens (`src/test/resources/gamelog/recover-*.log`, `reveal-relic.log`) do not change. If one does, stop and report: an un-asked Recover's tag must post where it did.

If a suite not named here starts a Recover with modifiers, convert it to `ModifierPlay.started` as above and name it in the commit message.

- [ ] **Step 14: Commit**

```bash
git rm src/test/scala/oathdigital/gameplay/SelectionPaymentsSuite.scala
git add src/test/scala/oathdigital/gameplay/powers/recover/RecoverModifierChoiceSuite.scala \
  src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala \
  src/main/scala/oathdigital/gameplay/powers/recover/RelicWorship.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/application/gamelog/StartLines.scala \
  frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala \
  frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala \
  src/test/scala/oathdigital/testkit/CatacombsGame.scala \
  src/test/scala/oathdigital/gameplay/powers/recover/RelicWorshipSuite.scala \
  src/test/scala/oathdigital/gameplay/CatacombsContributionSuite.scala \
  src/test/scala/oathdigital/gameplay/RecoverProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/WalkerReplayDriftSuite.scala \
  src/test/scala/oathdigital/gameplay/OathRulesWalkerPowerSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala \
  src/test/scala/oathdigital/application/GameApplicationServiceRecoverSuite.scala \
  src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala \
  src/test/scala/oathdigital/application/UndoServiceSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
git commit -m "feat(recover): choose Recover modifiers inside the walk"
```

End the message with your trailer.

---
### Task 4: Delete the preview endpoint and the ordered-modifiers channel

**Files:**
- Delete: `shared/src/main/scala/oathdigital/protocol/MajorActionPreviewProtocol.scala`
- Delete: `src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/PrintedCards.scala`
- Delete: `src/test/scala/oathdigital/application/GameIntentMapperModifierSuite.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandProtocol.scala`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala`, `TrustedGameGateway.scala`, `TrustedSeatRoutes.scala`, `AuthenticatedGameRoutes.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`, `GameCommands.scala`, `GameIntentMapper.scala`, `gamelog/LogWords.scala`
- Modify: `src/main/scala/oathdigital/gameplay/PowerRuntime.scala`, `walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/model/RuleFallbackProtocol.scala`, `GameViolation.scala`
- Modify (tests): `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala`, `CommandRejectionMessageSuite.scala`, `src/test/scala/oathdigital/application/GameApplicationServiceActionsSuite.scala`, `GameApplicationServiceRecoverSuite.scala`, `FollowUpServiceSuite.scala`, `PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/banner/BannerFaceSourcesSuite.scala`, `src/test/scala/oathdigital/testkit/SituationDriver.scala`, `SituationSuite.scala`

**Interfaces:**
- Consumes: Task 1 (the client no longer calls the preview or sends ordered modifiers).
- Produces:
  - `ActorlessCommandRequest(expectedNextSequence, intent)`: no `orderedModifiers`. Its decoder rejects the key.
  - `GameIntentMapper.bind(actorId, intent)`: the only `bind`.
  - `PrintedCards(catalog).printedOn(ready, actor, handlerId): Option[CardId]`, private to `gamelog`.
  - Gone: `MajorActionPreviewRequest`, `MajorActionPreviewResponse`, `PreviewModifier`, `PreviewIgnoredRule`, `MajorActionPreviewCodec`, `ModifierInvocation`, `GameCommand.WithModifiers`, `OrderedRuleInvocation`, `OathViolation.InvalidModifierInvocation`, `GameApplicationService.preview`, `MajorActionPreviewAccepted`, `MajorActionPreviewGuard`, `PowerRuntime.options`.

`WithModifiers` is already dead: every one is refused, because `majorAction` matches nothing. The preview has had no caller since Task 1. This task deletes both, with no behaviour change for any client in this build.

- [ ] **Step 1: Delete the shared preview protocol and the ordered modifiers**

```bash
git rm shared/src/main/scala/oathdigital/protocol/MajorActionPreviewProtocol.scala
```

In `CommandProtocol.scala`:

- `ActorlessCommandRequest` becomes:

```scala
final case class ActorlessCommandRequest(
    expectedNextSequence: Long,
    intent: GameIntent
)
```

- Delete `ModifierInvocation`.
- In `ActorlessCommandCodec.encode`, delete the `if request.orderedModifiers.nonEmpty then …` statement; `encode` writes the object it builds.
- In `decodeValue`, the exact set becomes `Set("expectedNextSequence", "intent")`. Delete the `modifiers <- …` generator and the duplicate check after it. The `yield` becomes `ActorlessCommandRequest(sequence, intent)`.
- Delete `requiredString`, which only the ordered modifiers used.

- [ ] **Step 2: The routes lose the preview**

In `GameRoutes.scala`:

- Delete the `oathdigital.protocol.{MajorActionPreviewRequest, …}` import.
- In `CommandRejectionMessage.text`, delete the `InvalidModifierInvocation` case.
- In `GameServerGateway`, delete `preview`.
- Delete `MajorActionPreviewGuard`.
- In `route`, delete the `path("preview") { … } ~` alternative, so the log route is followed by `path("commands")`.
- In the commands route, `GameIntentMapper.bind(PlayerId(validPlayerId), request.intent, request.orderedModifiers)` becomes `GameIntentMapper.bind(PlayerId(validPlayerId), request.intent)`.
- Delete `completePreview`.

In `TrustedGameGateway.scala`, `submit` binds with `GameIntentMapper.bind(player, request.intent)`, and `preview` is deleted.

In `TrustedSeatRoutes.scala`, delete the `~ path("preview"): post { … }` alternative after the log route, through its closing `} }`, and delete `decodePreview` with the comment above it.

In `AuthenticatedGameRoutes.scala`:

- The protocol import becomes `import oathdigital.protocol.{ActorlessCommandRequest, FirstGameBootstrapRequest}`.
- `submit` binds with `GameIntentMapper.bind(actor.access.playerId, request.intent)`.
- Delete the gateway's `preview`.
- In the route, delete the `~ path("preview") { … }` alternative, so `path("commands") { … }` is followed by `~ path("bootstrap"):`.
- Delete `completePreview`.

- [ ] **Step 3: The application loses the preview and `WithModifiers`**

In `GameCommands.scala`, delete `WithModifiers` and its `issuer` case.

In `GameIntentMapper.scala`, delete the three-argument `bind`, `bindModifiers` and `modifier`.

In `GameApplicationService.scala`:

- Delete `MajorActionPreviewAccepted`, the `descriptions` value, `preview`, `walkerAction`, `acceptPreview` and `majorAction`, each with its doc comment.
- In `applyUnblockedCommand`, delete the `case GameCommand.WithModifiers(inner, ordered) => …` arm.
- In `answersFollowUp`, delete the `WithModifiers` case.
- Delete the imports of `PowerRuntime`, `WalkerProcedureRegistry` and `PreviewModifier`. `import oathdigital.gameplay.{PowerRuntime, OathRules}` becomes `import oathdigital.gameplay.OathRules`.

In `PowerRuntime.scala`, delete `options`, and drop `OrderedRuleInvocation` from the model import. `ignored` and `ignoredAtSource` stay: the rules' fallback diagnostics and their replay use them.

In `RuleFallbackProtocol.scala`, delete `OrderedRuleInvocation`. In `GameViolation.scala`, delete `InvalidModifierInvocation`.

In `WalkerProcedureRegistry.scala`, `isRegistered`'s doc becomes "Whether `procedure` runs on the generic walker at all." It keeps its tests.

- [ ] **Step 4: The Game Log keeps the one lookup it used**

The Game Log named a modifier by the card it is printed on through `PreviewModifierDescriptions.printedOn`. Move that lookup into the log and delete the class.

Create `src/main/scala/oathdigital/application/gamelog/PrintedCards.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, RuleSourceFace, RuleSourceIndex}
import oathdigital.model._

/** The card a power is printed on, so the log can name a modifier or a used
  * power by its card.
  */
private[gamelog] final class PrintedCards(catalog: ExecutableCatalog):
  /** The card in play that carries `handlerId`. A walker power's source is
    * usually only a `GameRule` naming its own id, which a card in play
    * carries.
    *
    * Only a card the actor may already read is named: one of their own
    * advisers or relics first (they know those whichever way up), else a
    * faceup card on the board. A facedown card of anyone else is never a
    * candidate, so the lookup cannot reveal one. The log judges visibility
    * per viewer itself.
    */
  def printedOn(ready: ReadyGame, actor: PlayerId, handlerId: String)
      : Option[CardId] =
    val holders = RuleSourceIndex.enumerate(catalog, ready)
      .filter(_.powerIds.exists(_.value == handlerId))
    val own = holders.map(_.source).collectFirst:
      case RuleSourceRef.Adviser(`actor`, id) => id
      case RuleSourceRef.Relic(`actor`, id) => id: CardId
    def public = holders.filter(_.face != RuleSourceFace.FaceDown)
      .flatMap((holder: IndexedRuleSource) => cardOf(holder.source))
      .headOption
    own.orElse(public)

  /** The card a rule source is printed on. A banner face, a foundation and a
    * game rule have none.
    */
  private def cardOf(source: RuleSourceRef): Option[CardId] = source match
    case RuleSourceRef.Adviser(_, id) => Some(id)
    case RuleSourceRef.SiteCard(_, id) => Some(id)
    case RuleSourceRef.Relic(_, id) => Some(id)
    case RuleSourceRef.SiteRelic(_, id) => Some(id)
    case RuleSourceRef.Edifice(_, id) => Some(id)
    case _ => None
```

The two methods are `printedOn` and `cardOf` from `PreviewModifierDescriptions`, unchanged. Compare them with the file before you delete it; if they differ from the code above, copy the file's.

```bash
git rm src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala
```

In `LogWords.scala`:

- The application import becomes `import oathdigital.application.GamePresentationProjector`.
- `private val descriptions = new PreviewModifierDescriptions(catalog, presentation)` becomes `private val printed = new PrintedCards(catalog)`.
- In `power`, `descriptions.printedOn(…)` becomes `printed.printedOn(…)`, and its doc's "as the modifier picker names it" becomes "as the modifier decision names it".

- [ ] **Step 5: The tests**

```bash
git rm src/test/scala/oathdigital/application/GameIntentMapperModifierSuite.scala
```

Delete these tests, each to the end of its body:

- `CommandProtocolSuite` (shared): "ordered modifier transport preserves click order and rejects duplicates", "a major-action preview request and response round trip", "a preview modifier round-trips its printed cost", "a preview modifier round-trips its card and the action it modifies".
- `GameApplicationServiceActionsSuite`: "major-action preview is stateless stale-safe and rejects unavailable modifiers". Delete `playFacedownAdviser` too if nothing else uses it.
- `GameApplicationServiceRecoverSuite`: the rest of "preview offers the walker Catacombs contribution, and OathRules.startWalker accepts exactly the offered id (Task 9a)", and "a Travel preview does not offer Catacombs".
- `FollowUpServiceSuite`: "the head's start passes the gate with modifiers too".
- `BannerFaceSourcesSuite`: "Travel offers no option, whether the Banner of the Darkest Secret is unheld or held on Wandering Flame". Drop `PowerRuntime` from its import.
- `SituationSuite`: "the rules adapter refuses ordered modifiers rather than skip their check".
- `TrustedSeatRoutesSuite`: "trusted preview binds resolved actor and rejects a different game" and "a preview body with a playerId is refused".

Edit these:

- `PendingWalkerInvariantSuite`: delete `GameCommand.WithModifiers(GameCommand.EndWake(actor), Vector.empty),` from `everyCommand`.
- `CommandRejectionMessageSuite`: delete the `InvalidModifierInvocation` assertion in "rejections surface their authoritative detail".
- `SituationDriver`: delete the `Step.Command(_: GameCommand.WithModifiers)` case in `apply` and the `case command: GameCommand.WithModifiers` case in `transition`. In the `Rules` doc, delete "`WithModifiers` is not supported, since checking its modifiers is the service's."
- `TrustedSeatRoutesSuite`, `OriginGame` loses its preview:

```scala
  /** "origin-game", created from the configured HTTPS origin, with p2's
    * seat cookie and an endWake command expecting sequence 0. The game is
    * at 2 (GameStarted and WalkerParked), so the command is stale. */
  private final case class OriginGame(code: String, cookie: Option[String],
      command: String)
```

  `originGame` passes only the command. In "trusted endpoints reject queries and cross-origin mutations", unpack `val OriginGame(code, cookie, command) = originGame(server)`, drop `"/games/origin-game/api/preview"` from the paths and the preview branch from `payload`, and replace the last `Vector("commands" -> command, "preview" -> preview).foreach { … }` with:

```scala
      assertEquals(send(server, "/games/origin-game/api/commands",
        Some(command), cookie,
        Some("https://evil.example.test")).statusCode(), 403)
```

  Rename "stale commands and previews are refused with 409 under the configured Origin or none" to "stale commands are refused with 409 under the configured Origin or none", and make its body:

```scala
    withServer(Some("https://games.example.test")) { (server, _) =>
      val OriginGame(_, cookie, command) = originGame(server)
      assertEquals(send(server, "/games/origin-game/api/commands",
        Some(command), cookie,
        Some("https://games.example.test")).statusCode(), 409)
      assertEquals(send(server, "/games/origin-game/api/commands",
        Some(command), cookie).statusCode(), 409)
    }
```

- [ ] **Step 6: Run every test**

Run: `./sbtw "test" "frontend/test"`

Expected: PASS. Fix any import the compiler reports unused. The server count drops by 13: 4 shared, 1 actions, 2 Recover service, 1 mapper, 1 follow-up, 1 banner, 1 situation, 2 trusted-seat routes. The frontend count drops by 4, the shared tests.

Then check that nothing names the deleted channel:

```bash
grep -rn "MajorActionPreview\|PreviewModifier\|PreviewIgnoredRule\|ModifierInvocation\|orderedModifiers\|WithModifiers\|OrderedRuleInvocation\|InvalidModifierInvocation\|bindModifiers" src shared frontend/src
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add -u src shared
git add src/main/scala/oathdigital/application/gamelog/PrintedCards.scala
git commit -m "refactor(server): delete the modifier preview and the ordered-modifiers channel"
```

`git add -u src shared` stages only tracked files under those two directories, including the deletions. Check `git status` first: if another session left unrelated changes there, stage this task's paths one by one instead. End the message with your trailer.

---
### Task 5: A start carries no modifiers

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, `walker/WalkerProcedureRegistry.scala`, `walker/ModifierOffers.scala`
- Modify (frontend main): every file that builds a `StartWalker` (Step 5's script finds them)
- Modify (tests): the suites listed in Steps 5 and 6

**Interfaces:**
- Consumes: Tasks 2 and 3 (every procedure with a modifier window asks for its modifiers inside the walk).
- Produces:
  - `GameIntent.StartWalker(action: String, startArgs: Vector[WalkerStartArgWire] = Vector.empty)`. The wire object has `type`, `action` and an optional `startArgs`; a `modifiers` key is rejected as unexpected.
  - `StartPayload(actor: PlayerId, startArgs: Vector[DecisionOptionRef] = Vector.empty)`.
  - `OathRulesWalker.startWalker(state, procedure, requester, startArgs = Vector.empty)`.
  - Gone: `Entry.choosesModifiers` and `WalkerProcedureRegistry.choosesModifiers`, `validateModifiers`, `requirePayable`. `ModifierOffers.paying` becomes private: `offerable` and `selections` still dry-run payments with it, for the Act panel's start controls (spec ruling 9).

The journal stores no command, so no stored game changes. A browser holding an old bundle sends `"modifiers":[]` and gets one rejection; a reload fixes it.

- [ ] **Step 1: The wire's start loses `modifiers`**

In `CommandIntents.scala`, `StartWalker` becomes:

```scala
  /** Starts a walker procedure. `action` is the engine's persisted
    * `StartableRef` wire key (e.g. `"recover"`). An action asks for its
    * modifiers inside the walk, as its first decision, so a start names none
    * (modifier walker design).
    *
    * `startArgs` is what the player selected before the action started, for
    * an action whose tree needs it: Trade's resource and Take Wealth's pile.
    * The engine rejects a selection an action did not ask for.
    */
  final case class StartWalker(action: String,
      startArgs: Vector[WalkerStartArgWire] = Vector.empty) extends GameIntent
```

Check the "Trade's resource and Take Wealth's pile" examples against the registry's entries that take start args, and name exactly those.

In `CommandIntentCodec.scala`:

```scala
    case StartWalker(action, startArgs) =>
      tagged("startWalker", "action" -> action,
        "startArgs" -> ujson.Arr.from(startArgs.map(
          CommandNestedCodecs.encodeStartArgWire)))
```

In `CommandIntentDecoders.scala`, the `"startWalker"` case becomes:

```scala
    case "startWalker" =>
      for
        _ <- exact(value, Set("type", "action", "startArgs"), path)
        action <- string(value, "action", path)
        // Optional and empty by default: most procedures select nothing
        // before they start.
        startArgs <- value.value.get("startArgs") match
          case None => Right(Vector.empty[WalkerStartArgWire])
          case Some(args) => CommandNestedCodecs.decodeStartArgsWire(args,
            s"$path.startArgs")
      yield StartWalker(action, startArgs)
```

If `strings` is now unused, delete it.

- [ ] **Step 2: The command's start loses `modifiers`**

In `GameCommands.scala`, replace the two doc comments above `StartPayload`, and the class, with:

```scala
/** Generic action-start data: who starts, and what they selected before
  * the action started.
  *
  * `startArgs` (batch-1 Task 5) is that selection, for an action whose tree
  * needs it. They are the same `DecisionOptionRef`s a decision option names,
  * and the command layer does not interpret them: `OathRules.startWalker`
  * hands them to the registered action's own builder, which rejects a shape
  * it did not ask for. A start names no modifiers: the action asks for them
  * inside the walk (modifier walker design).
  */
final case class StartPayload(actor: PlayerId,
    startArgs: Vector[DecisionOptionRef] = Vector.empty)
```

In `GameIntentMapper.scala`, the `StartWalker` case becomes:

```scala
      case Intent.StartWalker(value, startArgs) =>
        for
          ref <- actionRef(value)
          args <- traverse(startArgs.zipWithIndex)(walkerStartArg.tupled)
        yield GameCommand.StartWalker(ref, StartPayload(actorId, args))
```

Delete `powerId`, which only that case used.

In `GameApplicationService.scala`, `rules.startWalker(state, procedure, start.actor, start.modifiers, start.startArgs)` becomes `rules.startWalker(state, procedure, start.actor, start.startArgs)`, and the `UsePower` arm's `rules.startWalker(state, ActionRef.UsePower(power), playerId, Vector.empty, Vector(source))` becomes `rules.startWalker(state, ActionRef.UsePower(power), playerId, Vector(source))`.

- [ ] **Step 3: The rules stop checking start modifiers**

In `OathRulesWalker.scala`:

- `startWalker`'s doc becomes:

```scala
  /** Starts one action on the generic procedure walker. An action asks for
    * its modifiers inside the walk, as its first decision (modifier walker
    * design), so a start carries only what the player selected before it
    * (`startArgs`). With a queue, only the head may start (`startFollowUp`).
    */
```

- `startWalker` loses its `modifiers` parameter; `startFollowUp` and `startNow` lose theirs too. Each call between them drops the argument.
- In `startNow`'s `run`, delete the `choosesModifiers` refusal and the `validateModifiers` line. `powers = walkerPowers(ready, activePlayer, modifiers)` becomes `powers = walkerPowers(ready, activePlayer, Vector.empty)`, and `walkerTransition(…, powers, modifiers, startArgs, …)` passes `Vector.empty` for the modifiers. Task 6 removes those two arguments.
- Delete `validateModifiers` and `requirePayable` with their docs.
- `offerableWalkerPowers`' doc becomes:

```scala
  /** The `ContributingPower`s a player could pick for `actor` on `procedure`
    * right now: `PlayerSelected` powers in `walkerPowerCatalog` that are
    * `applicable` at that procedure's modifier-selection window and whose
    * selection payment `actor` can make (`ModifierOffers.offerable`). The
    * Act panel's start controls try selections of these (spec ruling 9); the
    * modifier decision itself judges each option exactly.
    *
    * An entry declaring `modifierWindow = None` offers nothing; a procedure
    * absent from `registrations` is a `Left`, since "registers no window" and
    * "is not registered" are different facts and only the first is a rule.
    * `registrations` defaults to the production `WalkerProcedureRegistry
    * .entries`; a suite passes its own to prove behaviour that differs
    * between procedures.
    */
```

In `WalkerProcedureRegistry.scala`:

- Delete the `choosesModifiers` field of `Entry`, with its doc comment, and the `choosesModifiers = true` line of every entry (Search, PlayFacedownAdviser, Travel, Muster, Trade, Recover, Forge, Challenge, Campaign). Where that line ended an entry, move the closing parenthesis to the line before.
- Delete the `choosesModifiers` accessor.
- In `Entry`'s doc, replace the paragraph that opens "`modifierWindow` (batch-1 Task 1) is the [[oathdigital.model.PowerWindow]] at which a player-selected `ContributingPower` is offered as a `StartWalker` modifier" with:

```scala
    * `modifierWindow` is the [[oathdigital.model.PowerWindow]] at which the
    * procedure's modifier decision (`ModifierChoice`) offers player-selected
    * `ContributingPower`s, and at which `ModifierOffers.offerable` looks for
    * the Act panel's start controls.
```

  In the paragraph after it, "`offerableWalkerPowers` returns empty and `validateModifiers` rejects every id." becomes "`offerableWalkerPowers` returns empty."
- In `entries`' doc, "`offerableWalkerPowers`/`validateModifiers`" becomes "`offerableWalkerPowers`".
- In `modifierWindow`'s doc, "`OathRules.offerableWalkerPowers` and `OathRules.validateModifiers` read this instead of naming `PowerWindow.RecoverModifierSelection`." becomes "`ModifierOffers.offerable` reads this."

In `ModifierOffers.scala`:

- The object doc becomes:

```scala
/** The modifiers a player may select for a procedure, read from one walker
  * power catalog. An Act panel start control asks `selections` whether some
  * selection the player could make lets the procedure start (modifier walker
  * design, ruling 9). The modifier decision itself judges each option
  * exactly, inside the walk.
  */
```

- `paying` becomes `private def paying`, with the doc "Dry-runs the selection payments of `powers` together against `ready`: whether the player could pick them all."

Then check:

```bash
grep -rn "validateModifiers\|requirePayable\|choosesModifiers\|ModifierOffers.paying\|start\.modifiers\|StartWalker.*modifiers" src/main shared/src/main frontend/src/main
```

Expected: no output.

- [ ] **Step 4: Compile the server to list the calls to fix**

Run: `./sbtw "Test/compile"`

Expected: FAIL. Every remaining failure is a call that still passes the modifiers. Step 5 fixes most of them by script.

- [ ] **Step 5: Fix the calls**

The two-argument `StartWalker` whose second argument was the empty modifiers now reads as empty start args, so it still compiles. Rewrite it anyway, so no call says "empty" twice, and fix the three-argument forms:

```bash
perl -0pi -e '
  s/StartWalker\(("[^"]*"|[A-Za-z_][\w.]*),\s*Vector\.empty,\s*/StartWalker($1, /g;
  s/StartWalker\(("[^"]*"|[A-Za-z_][\w.]*),\s*Vector\.empty\)/StartWalker($1)/g;
  s/StartPayload\(([^,()]+),\s*Vector\.empty,\s*/StartPayload($1, /g;
  s/StartPayload\(([^,()]+),\s*Vector\.empty\)/StartPayload($1)/g;
  s/"modifiers":\[\],//g;
  s/, "modifiers" -> ujson\.Arr\(\)//g;
' $(grep -rl 'StartWalker\|StartPayload\|"modifiers"' src shared frontend/src)
```

`StartWalker` here is the wire intent and the application command alike. The command's second argument is always a `StartPayload`, so the script never touches it. The lowercase `startWalker` method is not matched.

Then compile again: `./sbtw "Test/compile" "frontend/Test/compile"`. Fix what remains by hand:

- **Positional `startWalker` calls** that pass `Vector.empty` as the modifiers before the start args: delete that argument. The inventory found them in `testkit/TakeWealthFixture.scala`, `SituationDriver.scala`, `PhasePowerHarness.scala`, `ActPowerHarness.scala`; `gameplay/PhasePowerSuite.scala`, `CampaignConsentFixture.scala`, `TravelProcedureSuite.scala`, `ChallengeProcedureSuite.scala`, `PendingWalkerRulesSuite.scala`, `EndWakeProcedureSuite.scala`; `application/TravelDestinationRowsSuite.scala`; and under `gameplay/powers`: `economy/VowOfPovertySuite.scala`, `economy/CurfewSuite.scala`, `economy/VowOfBeastkinSuite.scala`, `wake/QuartermasterSuite.scala`, `wake/MarbleFountainsSuite.scala`, `cardplay/VowOfObedienceSuite.scala`, `cardplay/FamilyWagonArrivalsSuite.scala`, `action/WaysideInnSuite.scala`, `action/MovementFixture.scala`, `action/OracleSuite.scala`, `action/MagicWaterskinSuite.scala`, `action/ElderSuite.scala`, `travel/NarrowPassConsentSuite.scala`, `rest/InsomniaSuite.scala`, `rest/SilverTongueSuite.scala`.
- **`SituationDriver.transition`**: `rules.startWalker(state, procedure, start.actor, start.modifiers, start.startArgs)` becomes `rules.startWalker(state, procedure, start.actor, start.startArgs)`.
- **A `StartPayload` whose actor is an expression with parentheses** (`LogScripts.start` has `StartPayload(Look(situation.ready).active, Vector.empty, args.toVector)`): delete the `Vector.empty`.
- **A pattern** on the old arity, such as `GameIntent.StartWalker(_, _, _)`: drop one wildcard.

- [ ] **Step 6: The tests that started with modifiers**

Delete, each to the end of its body:

- `SearchModifierChoiceSuite`: "a start may no longer carry modifiers for a Search".
- `TravelModifierChoiceSuite`: "a start may no longer carry modifiers for a Travel".
- `MusterModifierChoiceSuite`: "a start may no longer carry modifiers for a Muster".
- `TradeModifierChoiceSuite`: "a start may no longer carry modifiers for a Trade".
- `RecoverModifierChoiceSuite`: "a start may no longer carry modifiers for a Recover".
- `GameApplicationServiceRecoverSuite`: "StartWalker rejects an unknown power id in modifiers, appending no events, while empty modifiers still starts Recover exactly as today". The suite's other tests start a Recover with no modifiers.
- `GameHttpWireSuite`: "a malformed walker modifier id is rejected with a typed failure, not an exception".

Edit:

- `GameHttpWireSuite`, "walker intents bind to StartWalker, RollWalker and ResolveWalker commands for the requester": delete the first assertion, which bound `StartWalker("recover", Vector("denizen.catacombs"))`.
- `CommandProtocolSuite` (shared): delete `StartWalker("recover", Vector("denizen.catacombs")),` from `examples`.
- `OathRulesWalkerPowerSuite`, "an entry declaring no modifier window offers nothing and rejects a modifier id a windowed entry accepts": rename it "an entry declaring no modifier window offers nothing", and delete its two `validateModifiers` assertions. Keep the `offerableWalkerPowers` one. In the comment block above `windowScoped`'s neighbours, "`offerableWalkerPowers`/`validateModifiers` read the window and stop" becomes "`offerableWalkerPowers` reads the window and stops".
- `ModifierStartControlsSuite`: `refusal` loses its `modifiers` parameter:

```scala
  /** Why `viewer` may not start `action` at `ready`; fails the test if the
    * start is accepted. */
  private def refusal(ready: ReadyGame, viewer: PlayerId, action: ActionRef)(
      using munit.Location): OathViolation =
    rejected(rules.startWalker(OathState.Ready(ready), action, viewer))
```

  Its caller drops the `Vector.empty`.

- [ ] **Step 7: Run every test**

Run: `./sbtw "test" "frontend/test"`

Expected: PASS. The server count drops by 7: five refusal tests, one Recover service test and one wire test. The frontend count is unchanged.

- [ ] **Step 8: Commit**

```bash
git status --short
git add -u src shared frontend/src
git commit -m "refactor(walker): a start carries no modifiers"
```

`git status --short` shows what `git add -u` would stage. If another session left unrelated changes under those directories, stage this task's paths one by one instead. End the message with your trailer.

---
### Task 6: The picks are the only record of the selection

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, `WalkerReplay.scala`, `WalkerPowers.scala`, `WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`, `OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`, `gameplay/operations/OperationRestrictions.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `gamelog/StartLines.scala`
- Modify (tests): `testkit/ParkedNode.scala`, `gameplay/CampaignProcedureSuite.scala`, `gameplay/powers/targeting/TargetingFixture.scala`, `gameplay/powers/campaign/PlanDriver.scala`, `gameplay/CardSlotsSuite.scala`, `serialization/WalkerDecisionWireSuite.scala`, `gameplay/WalkerReplayDriftSuite.scala`, `gameplay/walker/WalkerStartFactsReplaySuite.scala`, `gameplay/OathRulesWalkerPowerSuite.scala`, `application/ParkedServiceFixture.scala`

**Interfaces:**
- Consumes: Task 5 (no start carries modifiers, so every park records none).
- Produces:
  - `WalkerParked(procedure, at, answered, startArgs, startFacts = Vector.empty)`. Its codec writes no `modifiers` key and ignores one in an old record.
  - `CurrentGameState` without `walkerModifiers`.
  - `OathRulesWalker.walkerPowers: WalkerPowers`: the catalog with nothing selected. The walker adds the picks from the answers (`WalkerPowers.withAnswers`).

The picks in a walk's answers are the selection (spec ruling 4); the walker, the projector and `CardSlots` already read them. This task deletes the second record.

- [ ] **Step 1: Write the failing test**

Add to `WalkerDecisionWireSuite`:

```scala
  test("a park written with the modifiers its start selected still decodes, " +
      "and the modifiers are ignored"):
    val park = WalkerParked(ActionRef.Recover,
      Vector("1", "1", "0", "2", "0"), Vector.empty, Vector.empty)
    // Before the modifier walker, every park carried its start's modifiers.
    def withOldModifiers(value: ujson.Value): ujson.Value = value match
      case ujson.Obj(fields) =>
        val rebuilt = ujson.Obj.from(fields.toSeq.map((key, inner) =>
          key -> withOldModifiers(inner)))
        if fields.contains("startFacts") then
          rebuilt("modifiers") = ujson.Arr(ujson.Str("denizen.catacombs"))
        rebuilt
      case ujson.Arr(items) => ujson.Arr.from(items.map(withOldModifiers))
      case other => other
    val old = ujson.write(withOldModifiers(ujson.read(
      encodedStream("old-park", Vector[OathEvent](park)))))
    assert(old.contains("\"modifiers\":[\"denizen.catacombs\"]"), old)
    assertEquals(accepted(GameEventWire.decodeStream(old)).map(_.event),
      Vector[OathEvent](park))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.serialization.WalkerDecisionWireSuite"`

Expected: FAIL to compile: `WalkerParked` still takes `modifiers`.

- [ ] **Step 3: The park and the state lose the modifiers**

In `WalkerEvents.scala`:

- Delete the `modifiers: Vector[PowerId],` field of `WalkerParked`.
- In its doc, delete the sentence that opens "`modifiers` (fix-round ruling I)", and "`startArgs` (batch-1 Task 5) is carried on every park for exactly the same reason:" becomes "`startArgs` (batch-1 Task 5) is carried on every park because". Re-wrap the paragraph.

In `WalkerEventCodec.scala`:

- The `WalkerParked` encode case becomes `case WalkerParked(procedure, at, answered, startArgs, startFacts) =>`, without the `"modifiers"` entry. Its comment "Always written, empty for an action that selects nothing, exactly as `modifiers` is." becomes "Always written, empty for an action that selects nothing."
- In `decodeParked`, delete the `modifiers = …` line, and the `yield` becomes `WalkerParked(procedure, value("at").arr.toVector.map(_.str), answered, startArgs, startFacts)`. A park written before this change still has a `modifiers` key; nothing reads it.

In `GameState.scala`, delete `walkerModifiers` with its comment. In the comment of `walkerStartArgs`, "Durable for exactly the reason `walkerModifiers` is: a resumed command" becomes "Durable because a resumed command".

In `WalkerReplay.scala`:

- The case becomes `case WalkerParked(procedure, at, answered, startArgs, startFacts) =>`.
- Delete the `walkerModifiers == modifiers` check, and the `walkerModifiers = …` lines in both the park and the completion.

- [ ] **Step 4: The rules walk with the catalog and the picks**

In `OathRulesWalker.scala`:

- Replace `walkerPowers` and its doc with:

```scala
  /** The powers a command walks with: every automatic power of
    * `walkerPowerCatalog`, and none of its player-selected ones. The walker
    * adds the modifiers the walk's answers pick (`WalkerPowers.withAnswers`),
    * on every command alike, so a resume folds every window as the start
    * did.
    */
  def walkerPowers: WalkerPowers =
    WalkerPowers.selected(walkerPowerCatalog, Vector.empty)
```

- Every `walkerPowers(ready, …, …)` call becomes `walkerPowers`. In `OathRules.scala` there are three.
- `walkerTransition` loses its `modifiers: Vector[PowerId]` parameter, and builds `WalkerParked(procedure, pending.at, pending.answered, startArgs, startFacts)`. Each caller drops that argument: in `startNow` and `startTriggered` it was `Vector.empty`; in `resolveWalker` and `rollWalkerPrepared` it was `modifiers`.
- `walkerResumeContext` returns a six-element tuple: delete `Vector[PowerId]` from its type, the `modifiers = ready.game.current.walkerModifiers` line, and `modifiers` from the `yield`; `powers = walkerPowers`. Delete its doc comment, which was about the modifiers.
- `resumeWalker`'s `run` loses its `Vector[PowerId]` parameter. Every pattern on the tuple drops one element: `case (ready, procedure, tree, pending, powers, startArgs)` and `case (ready, _, tree, pending, powers, _)`.

In `WalkerPowers.scala`:

- Its doc's "`restrictionSet` is the catalog's global operation restrictions and `modifiers` the powers selected for this command." becomes "`restrictionSet` is the catalog's global operation restrictions and `modifiers` the modifiers picked so far."
- `selected`'s doc becomes:

```scala
  /** Powers offered to one command out of a full catalog: an `Automatic`
    * power fires unconditionally; a `PlayerSelected` power fires only when
    * its id appears in `modifiers`, the picks. Shared by
    * `OathRules.walkerPowers` (command time) and `WalkerDecisionProjector`
    * (park-time projection) so both fold a shared window identically. The
    * modifiers are kept, so the active-modifier restriction sees them. The
    * engine's follow-up waiver is always offered; it applies only to a run
    * started from the queue.
    */
```

In `WalkerProcedureRegistry.scala`, `Entry`'s doc paragraph on `rebuild`: "read back from the durable `CurrentGameState.walkerStartArgs`, for the same reason `walkerModifiers` is carried: a resumed command must rebuild the tree the start built, and a selection is not re-derivable from state." becomes "read back from the durable `CurrentGameState.walkerStartArgs`: a resumed command must rebuild the tree the start built, and a selection is not re-derivable from state."

- [ ] **Step 5: The projector and `CardSlots` read the picks only**

In `WalkerDecisionProjector.scala`:

- `WalkerPowers.selected(walkerPowerCatalog, context.current.walkerModifiers).withAnswers(pending.answered)` becomes `WalkerPowers.selected(walkerPowerCatalog, Vector.empty).withAnswers(pending.answered)`.
- `WalkerPowers.selected(walkerPowerCatalog, current.walkerModifiers)` in `parkedDecide` becomes `WalkerPowers.selected(walkerPowerCatalog, Vector.empty)`. `ProcedureWalker.parkedDecide` adds the picks itself.
- In the class doc, the paragraph on `walkerPowerCatalog` becomes:

```scala
  * `walkerPowerCatalog` (Task 5) is the same full catalog `OathRules`
  * offers a live command, with the modifiers the walk's answers pick added
  * (`WalkerPowers.withAnswers`), the SAME powers `OathRules.walkerPowers`
  * walks with at command time. Without this, a power that inserts
  * operations at a shared window (Catacombs at `RecoverActionEligibility`)
  * would make this projector re-fold the tree differently from the walker
  * that actually parked it, misreporting the parked node.
```

In `CardSlots.scala`, `.active(Vector.empty, ready.game.current.walkerModifiers, picked)` becomes `.active(Vector.empty, Vector.empty, picked)`. The picked cards already come from the answers.

In `OperationRestrictions.scala`:

- The class doc's "`active` adds the [[ActiveModifier]] rule for the modifiers selected for the running action" becomes "`active` adds the [[ActiveModifier]] rule for the modifiers picked for the running action".
- `ActiveModifier`'s doc becomes:

```scala
/** A card that prints a modifier picked for the running action cannot be
  * discarded while the action runs: a modifier the player picked in the
  * action's modifier decision stays in play until the action ends.
  * `modifiers` are the picked powers, as the walker holds them; `picked` are
  * their cards, for a check made outside the walk. */
```

- [ ] **Step 6: The Game Log reads the picks only**

In `StartLines.scala`, `modifiers` becomes:

```scala
  /** The modifiers the run's answers pick, in pick order. Automatic powers
    * are left out. */
  private def modifiers(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val shown = picks(journal.answers(run, journal.segmentEnd(at)))
      .filterNot(id =>
        resolutions.get(id).contains(PowerResolution.Automatic))
    journal.readyBefore(run.first).filter(_ => shown.nonEmpty)
      .fold(Vector.empty[LogSpan])(ready => LogWords.join(
        shown.map(id => words.power(ready, run.actor, id, viewer))))
```

Drop `WalkerParked` from the walker import if nothing else uses it. A game finished before this change, with modifiers chosen at its start, loses the "With …" part of that start line (user decision); its state replays as before.

- [ ] **Step 7: The tests**

Replace `current.walkerModifiers` (or `current.game.current.walkerModifiers`) with `Vector.empty` in:

- `testkit/ParkedNode.scala` (`WalkerPowers.selected(walkerPowers, current.walkerModifiers)`). In its doc, "the procedure, the start selection and the selected modifiers all come from the state" becomes "the procedure and the start selection come from the state, and the picked modifiers from its answers".
- `gameplay/CampaignProcedureSuite.scala`, `gameplay/powers/targeting/TargetingFixture.scala`, `gameplay/powers/campaign/PlanDriver.scala`.

In `CardSlotsSuite`, delete "a card in use as a modifier shows why it stays", which wrote `walkerModifiers`. The test after it covers a picked card.

Drop the modifiers from every `WalkerParked` a test builds. It is the fourth argument; delete it:

- `serialization/WalkerDecisionWireSuite.scala`: five constructions. In the existing start-facts test, `WalkerParked(ActionRef.Travel, Vector("0"), Vector.empty, Vector.empty, Vector.empty)` becomes `WalkerParked(ActionRef.Travel, Vector("0"), Vector.empty, Vector.empty)`.
- `gameplay/WalkerReplayDriftSuite.scala`: `WalkerParked(procedure, pending.at, pending.answered, Vector.empty)`.
- `gameplay/walker/WalkerStartFactsReplaySuite.scala`: `WalkerParked(ActionRef.Travel, Vector(at), Vector.empty, Vector.empty, facts)`.
- `gameplay/OathRulesWalkerPowerSuite.scala`: three expected parks.
- `application/ParkedServiceFixture.scala`: the pattern `WalkerParked(TriggeredProcedureRef.Oathkeeper, _, _, _, _, _)` loses one wildcard.

- [ ] **Step 8: Run every test**

Run: `./sbtw "test" "frontend/test"`

Expected: PASS. The server count is unchanged: +1 wire test, −1 `CardSlotsSuite`. The Game Log goldens do not change. If one does, stop and report: no run in a golden picked a modifier through the contributions fallback this step removed.

Then check:

```bash
grep -rn "walkerModifiers\|parked\.modifiers" src
```

Expected: no output.

- [ ] **Step 9: Commit**

```bash
git status --short
git add -u src
git commit -m "refactor(walker): the walk's picks are the only record of its modifiers"
```

If `git status --short` shows unrelated changes under `src/`, stage this task's paths one by one instead. End the message with your trailer.

---
### Task 7: Record the slice, rewrite the amended rulings, run the gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`
- Modify: `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`, `2026-10-07-travel-destination-walker-design.md`, `2026-10-06-undo-design.md`
- Modify: `docs/ROADMAP.md`, `CONTEXT.md`, `PRODUCT.md`, `DESIGN.md`, `docs/architecture/follow-up-actions.md`, `docs/rules/implementation-traceability.md`

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: nothing in code.

- [ ] **Step 1: The modifier walker spec**

In `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`:

- The status line becomes "**Status:** designed and implemented 2026-10-09, in four slices. It is a prerequisite of slices 3 and 4 of [Catalog batch 6](../specs/2026-10-09-catalog-batch-6-design.md)."
- After "Plan-time rulings", add:

```markdown
Settled while planning slice 4:

11. **Catacombs says why it cannot be picked.** At a site with no empty
    relic slot it reads "This site has no empty relic slot"; with an empty
    relic deck, "The relic deck is empty" (`offerReason`). Its eligibility
    Transform still places the relic, and no longer pays: the pick does.
12. **Recover's "Start" says "Cannot start now".** Recover prices no option,
    so ruling 4's generic reason stands.
13. **The Recover tag waits for the dice.** It anchors at the step that
    opens Recover's dice pool, the first of the Recover itself.
14. **Old journals.** The journal stores events, not commands, so every old
    game still loads. A park's `modifiers` key is ignored. A game finished
    before slice 4, with modifiers chosen at its start, loses the "With …"
    part of that start line.
15. **The wire.** `StartWalker` has no `modifiers` key, and its decoder
    rejects one. A stale browser gets one rejection; a reload fixes it.
16. **What stays.** `ModifierOffers.paying` stays, private: the Act panel's
    start controls still dry-run payments (ruling 9). The Game Log's card
    lookup moved from `PreviewModifierDescriptions` to the log's own
    `PrintedCards`.
```

- In "What goes away", after "`OathRulesWalker.validateModifiers` and `requirePayable`, and `ModifierOffers.paying`.", add " (`paying` stays as a private helper; plan-time ruling 16)". Keep the rest: it records what slice 4 deleted.

- [ ] **Step 2: The three amended rulings**

In `2026-10-07-search-source-walker-design.md`:

- Replace ruling 1 with:

```markdown
1. **Modifiers are the walk's first decision.** `StartWalker("search")`
   carries no start selection and no modifiers; a start that names a
   selection is refused. The walk asks for its modifiers first, each paid
   when it is picked, then for the source
   ([modifier walker design](2026-10-09-modifier-walker-design.md)).
```

- In ruling 6, "The ordering stage's "Back" and "Cancel action" stay. Nothing new is added." becomes "Undo takes back each modifier pick too. The client's ordering stage, with its "Back" and "Cancel action", is gone (modifier walker design)."

In `2026-10-07-travel-destination-walker-design.md`:

- Replace ruling 1 with:

```markdown
1. **Modifiers are the walk's first decision.** The walk asks for the
   Travel's modifiers first, each paid when it is picked, then for the
   destination
   ([modifier walker design](2026-10-09-modifier-walker-design.md)).
```

- In ruling 8, "The ordering stage's "Back" and "Cancel action" stay." becomes "Undo takes back each modifier pick too; the client's ordering stage is gone (modifier walker design)."

In `2026-10-06-undo-design.md`, controls ruling 5 becomes:

```markdown
5. The client-side modifier flow, with its Back and Cancel, is gone. An
   action asks for its modifiers inside the walk, so Undo takes back a
   modifier pick like any other answer
   ([modifier walker design](2026-10-09-modifier-walker-design.md)).
```

- [ ] **Step 3: The living docs**

- `docs/ROADMAP.md`: check "Slice 4: Recover, the empty windows, and deleting the client modifier flow and the preview endpoint."
- `CONTEXT.md`:
  - **Draft set**: "the board draft, the partition, distribute and selection drafts, and the modifier order" becomes "the board draft and the partition, distribute and selection drafts".
  - Delete the **Modifier flow** entry. The **Modifier decision** entry stays; add to it: "Every major action has one; an action with nothing to pick is asked nothing."
- `PRODUCT.md`: in the list of `CONTEXT.md` product terms, "draft set, modifier flow" becomes "draft set, modifier decision".
- `DESIGN.md`:
  - Brass: delete "the modifier ordinal disc, " from its uses.
  - Shape: "The modifier ordinal is a full circle, as is the color swatch and the knowable pip." becomes "The color swatch and the knowable pip are full circles."
  - Action face: "every act, decision, confirm and modifier button in the Actions pane" becomes "every act, decision and confirm button in the Actions pane", and the movers' list loses ", modifier Earlier/Later".
  - Delete the "**Modifier ordinal**" component entry.
  - If `.impeccable/design.json` names the modifier ordinal, remove it there too.
- `docs/architecture/follow-up-actions.md`: "The start goes through the modifier flow, and the modifier order replaces the panel while it is open (a queued Travel with Special Envoy, say)." becomes "The start asks for the action's modifiers inside the walk, as a normal start does (a queued Travel with Special Envoy, say)."
- `docs/rules/implementation-traceability.md`: "`gameplay/powers`, `PowerRegistry`, `PowerResolver`, `PowerRuntime`, application preview/command mapping, lifecycle event codec, server routes, and `frontend/ModifierSelectionState.scala` are the implementation evidence." becomes "`gameplay/powers`, `PowerRegistry`, `PowerResolver`, `PowerRuntime`, application command mapping, lifecycle event codec, server routes and the walker's modifier decision (`ModifierChoice`) are the implementation evidence."

Then check what still names the flow outside the historical plans and specs:

```bash
grep -rn -i "modifier flow\|ModifierSelectionState\|ModifierFlow\|modifier ordinal\|Earlier/Later\|preview endpoint" \
  CONTEXT.md PRODUCT.md DESIGN.md README.md docs --include=*.md | grep -v "docs/superpowers/"
```

Expected: only the ROADMAP's phase text and its checked slice line, which describe the history. Fix anything else.

- [ ] **Step 4: Run the verification gate**

1. `./sbtw "test" "frontend/test"`. Expected: PASS. Against the baseline, the server count is down 21 (+3, −4, −13, −7, 0 by task) and the frontend count down 43 (−40, +1, −4).
2. `./sbtw frontend/fullOptJS`. Expected: success.
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. Expected: no findings.
4. `git diff --check main...HEAD`. Expected: no output.

- [ ] **Step 5: Browser check on a scratch database**

Follow the memory recipe (`preview-start-hits-live-db`). Never use `preview_start`, and never open `var/oathdigital`.

1. Seed a scratch HSQLDB in the scratchpad from `FirstGameSetupFixture.initialSituation().endingWake(p2)`. In the throwaway seeding suite, assert that p2's site has a Recover difficulty (`RecoverRules.difficulty`) and holds a facedown relic. If it has no difficulty, have p2 Travel first, through the situation, to a site that has one. Arrange Relic Worship as p2's adviser (`Play` onto `Location.PlayArea(p2)`) and give p2 one faceup secret.
2. Run the server on port 8081 through Bash, in the background.
3. Open the game as p2 in the browser pane.
4. Check each of these:
   - Every Act button posts `/commands` and nothing posts `/preview` (`read_network_requests`).
   - The Recover button parks on "Choose modifiers, or start", with "Start Recover" first and Relic Worship priced "1 secret" on the board.
   - Undo takes back the start.
   - Recover again, then pick Relic Worship. It is the only modifier, so the Recover starts by itself, rolls, and shows Recover's own panel.
   - The Game Log shows "Recover −1 Supply", then "With Relic Worship".
   - No ordering panel appears anywhere ("Order … modifiers", "Earlier", "Later").
5. Stop the server. Delete the throwaway seeding suite.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-modifier-walker-design.md \
  docs/superpowers/specs/2026-10-07-search-source-walker-design.md \
  docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md \
  docs/superpowers/specs/2026-10-06-undo-design.md \
  docs/ROADMAP.md CONTEXT.md PRODUCT.md DESIGN.md \
  docs/architecture/follow-up-actions.md \
  docs/rules/implementation-traceability.md
git commit -m "docs: record modifier walker slice 4 and rewrite the amended rulings"
```

Add `.impeccable/design.json` if Step 3 changed it. End the message with your trailer.
