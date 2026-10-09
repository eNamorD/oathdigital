# Consent System, Slice 1 (Machinery and the Pass on Travel) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player whose Travel destination is blocked only by a player-ruled Narrow Pass can ask that ruler for consent. The ruler answers Yes or No, or sets a standing preference that answers later requests automatically. Answers never get in the way of undo, and preferences are private.

**Architecture:**

- **State.** `ReadyGame.consent: ConsentState` holds three things: preferences per granter, power and requester; answers remembered for the turn; and at most one pending request. It sits beside `knowledge`, outside `CurrentGameState`.
- **Events.** Five `ConsentEvent`s fold into it. The journal fold closes any open command batch when it meets one, so consent events never join an undoable batch. `StepTakenBack` and `StepRestored` carry the current `ConsentState` (minus a pending request) onto the state they rewind to.
- **Commands.** The service routes the three new commands and the requester's Undo to a `ConsentDesk`, never through the rules' walker gate:
  - `RequestConsent` (the requester's Undo withdraws a request it opened)
  - `AnswerConsent`
  - `SetConsentPreference`

  When an answer completes a request with every ask granted, the same append carries `CommandAccepted(requester)` and the requester's answer to the parked decision. After every append, preferences whose granter no longer rules the card are cleared.
- **Walker.** A tree-level `Restriction` may declare a `Waiver`. In `WalkerPowerGather`, one ledger check drops a breach whose power, requester, decision and answer are remembered as granted this turn. The look-ahead marks an option `OptionReason.Askable` when every new breach is waivable by a player other than the requester and nothing for that option was refused this turn. The Pass declares a waiver on its Travel restriction.
- **Projection.** An askable option has state `askable` and a `consent` field naming its granters and powers. The requester sees a waiting notice instead of the decision, and an Undo that withdraws. Each asked granter sees a prompt. The viewer gets their own preferences, one row per other player for each waivable power they rule. Site powers gain their power id.
- **Game Log.** One line for the request and one per answer. A withdrawn request's line is struck through with no group header. Preference lines are formatted only for their granter.
- **Frontend.** Askable options are amber and open a confirmation in the card overlay. An asked granter gets a prompt panel with four answers. Site power labels become buttons that open the power's text in the overlay, with the preferences grid when the viewer rules the power.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), ujson codecs, built through `./sbtw`. Impeccable (`~/.claude/skills/impeccable`) for the CSS pass.

**Spec:** `docs/superpowers/specs/2026-10-08-consent-system-design.md`. Read the whole spec before starting. This plan covers its "Slices" item 1.

## Plan decisions beyond the spec

The code forced these, or the spec left them open. Task 13 records each one in the spec.

1. **Consent events close the open batch.** The spec says the fold needs no change because consent events open no batch. That holds for a live append, where each append's open batch closes at its end. A full replay folds the whole journal in one pass, though, so a consent event appended after another player's batch would join that batch, and live play and reload would offer different undo steps. `JournalFold` therefore closes any open batch when it meets a `ConsentEvent`, as it does for a `CommandAccepted`.
2. **An askable option is an unavailable option with a new reason, `OptionReason.Askable(power, note, asks)`**, not a new `DecisionOption.Askable`. The narrowing path (`WalkerSearch.narrowed`, `explained`, `PricedOptions`) already carries a reason per hidden option, and an unavailable option is already refused by the answer check. The projector draws it as `askable`.
3. **`Waiver(granter: ReadyGame => Option[PlayerId], source: PowerSourceRef, note: (PowerCtx, DecisionOptionRef) => PowerNote)`, on `Restriction` only.**
   - The granter reads only the state, because the grid and the stale-preference check have no `PowerCtx`.
   - It returns a player, or `None` for bandits, the Empire and an unruled site. None of those ever consents (ruling 4).
   - `source` names the power in the log and the projection.
   - `OptionRestriction` gains its waiver in slice 2, with the campaign path.
4. **Answers carry their turn.** `ConsentState.turn` records the turn (round and active player) its `answers` belong to. `remembered` returns nothing for any other turn, and the next settlement replaces the map. This is ruling 12 with no clearing step in the fold and no event.
5. **Private lines are filtered by viewer in the formatter.** `GameLogFormatter.format` already takes the viewer, so a preference line is formatted only for its granter. `LogEntry` gains no `visibleTo` field.
6. **`awaitedPlayers` is unchanged.** Its only caller hands the whole parked decision to every player it returns, so adding granters would show them the requester's options. The granter's prompt is its own projection field, `consentPrompt`.
7. **While a request is pending:**
   - The requester's `walkerDecision` is `None`, so they cannot answer around it.
   - Their Undo offer reads `Withdraw your consent request`.
   - The Redo offer is hidden for everyone.
8. **Askable only at a choose-one.** A grant answers the parked decision with the target, which only makes sense where one option is the whole answer. Slice 1's Travel destination is a choose-one.
9. **No browser acceptance run in this slice.** A real server's game starts from `GameStarted`, so a game with a player-ruled Pass cannot be seeded until a journaled arranged start exists (`docs/ROADMAP.md`). As with Travel, Search and follow-ups, Task 13 adds a roadmap play-test item. The jsdom suites cover the DOM. The CSS is checked with the Impeccable detector.
10. **The client words the questions.** Player labels on the server are player ids, so the projection carries player ids and power names, and the client words "Ask Red for consent to ignore Narrow Pass?" with display names.
11. **The confirmation and the grid live in the card overlay.** It is the client's one document-level dialog. `CardInspection.Request` gains `Confirm` and `Power`.
12. **`SitePowerProjection` gains `id`**, the power's id, so the client can attach a power's preference grid to its label.
13. **A withdrawn request's line is marked `takenBack` with an empty header.** The log pane draws such an entry in place as a struck line (`log-withdrawn`), not as a collapsible group.
14. **Bandits.** The fixture board's Narrow Pass holds bandits, so the existing tests that expect the Pass to block a destination with its ordinary reason keep passing unchanged.

## Global Constraints

- **Build rules.** Every project builds with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import, private member or local fails the build: delete whatever the compiler names.
- **File size.** Every Scala file, production or test, stays at or under 800 lines (`scripts/check-architecture.py`, `BackendArchitectureSuite`).
  - Put new code in the new files this plan names, never in a file near the limit.
  - `GameApplicationService.scala` is at 577 lines and `WalkerDecisionProjector.scala` at 677; keep each addition to what the task shows.
- **Shared protocol.** No `.str` anywhere under `shared/src/main/scala/oathdigital/protocol` (`BackendArchitectureSuite`); use the codec helpers `string`, `strings` and `field`. An intent field must never be named `actor`, `actorId`, `actorPlayerId` or `playerId` (`CommandJsonSupport.rejectActorFields`); the requester of a preference is `requesterId`.
- **Ids and wording, exactly:**
  - Event types: `consent.requested`, `consent.answered`, `consent.request-withdrawn`, `consent.preference-set`, `consent.preferences-cleared`.
  - Intent tags: `requestConsent` (`decisionId`, `target`), `answerConsent` (`choice`), `setConsentPreference` (`powerId`, `requesterId`, `setting`).
  - Choice keys: `never`, `no`, `yes`, `always`. Setting keys: `never`, `case-by-case`, `always`.
  - Violations: `OathViolation.ConsentPending` and `OathViolation.ConsentUnavailable(reason: String)`. Rejection texts: `Waiting for an answer to a consent request`, and the reason itself.
  - `ConsentUnavailable` reasons:
    - `no consent request is waiting`
    - `{granter} has not been asked for consent`
    - `{target wire id} is not an option to ask consent for`
    - `{power id} cannot be waived`
    - `{granter} does not rule {power id}`
    - `{requester} is not another player`
    - `the game is over`

    Names are the ids' `value`s.
  - The Pass's consent note key is `no-travel-consent`, worded `Cannot travel to other sites in the region without ruler's consent.`
  - Option state `askable`. Classes `board-option-askable` and `walker-choice-askable`.
  - Confirmation: `Ask {granters} for consent to ignore {powers}?` with buttons `Yes` and `No`.
  - Prompt: `Give {requester} consent to ignore {powers} and select {target}?`, with buttons `Never Allow`, `No`, `Yes` and `Always Allow`. Notice: `You can adjust your consent preferences by clicking on the {powers} power.`
  - Waiting notice: `Waiting for {granters}'s consent…`, where "{granters}'s" is each name with `'s`, joined with ` and `. The Undo offer note while pending is `Withdraw your consent request`.
  - Grid heading: `Consent preferences`. Its buttons are `Never Allow`, `Case-by-case` and `Always Allow`.
  - Name lists read `A`, `A and B`, or `A, B and C`.
  - Log lines (player labels are ids on the server):
    - `Asked p2 for consent to ignore Narrow Pass and select Dunes.`
    - `p2 gave consent.` and `p2 gave no consent.`
    - `p2 now always allows p1 to ignore Narrow Pass.` and `p2 now never allows p1 to ignore Narrow Pass.`
    - `p2 now decides case by case whether p1 may ignore Narrow Pass.`
    - `p2's consent preferences for Narrow Pass were reset.`
- **Rulings** (spec, "Rulings"). These apply: 3 (the game always enforces), 4 (bandits never consent), 5 (own powers), 6 (ignore effects first), 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18 and 19. Ruling 9's several pending asks are modelled (`asks` is a vector), but no slice-1 power can produce two granters.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree on branch `feat/consent-slice1`.
  - `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.model.ConsentStateSuite"` (any fully qualified suite name).
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentAskingSuite"`.
  - One shared suite: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite"`. If sbt names the project differently, `./sbtw projects` lists it.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 13):
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `./sbtw coverage test coverageReport`
    4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    5. `git diff --check`

## File map

Created:

- `src/main/scala/oathdigital/model/Consent.scala`: preferences, settings, choices, keys, asks, the request, the turn, and `ConsentState` with its transitions.
- `src/main/scala/oathdigital/gameplay/consent/ConsentEvolution.scala`: folds consent events into `ReadyGame.consent`.
- `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala`: the five event payloads.
- `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`: the waivable powers, their granters and sources.
- `src/main/scala/oathdigital/application/ConsentDesk.scala`: the consent commands as events to append.
- `src/main/scala/oathdigital/application/ConsentProjector.scala`: `ConsentNames` and `ConsentProjector`.
- `src/main/scala/oathdigital/application/gamelog/ConsentLines.scala`: the consent log lines.
- `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala` and `ConsentProjectionCodec.scala`.
- Frontend: `ConsentAsking.scala`, `ConsentPanels.scala`, `SitePowerLinks.scala`.
- Tests:
  - `model/ConsentStateSuite`
  - `serialization/ConsentEventWireSuite`
  - `gameplay/powers/ConsentPowersSuite`
  - `gameplay/powers/travel/NarrowPassConsentSuite`
  - `application/ConsentFixture`
  - `application/ConsentPreferenceServiceSuite`
  - `application/ConsentRequestServiceSuite`
  - `application/ConsentProjectionSuite`
  - `application/gamelog/GameLogConsentSuite`
  - `application/ConsentIntentMapperSuite`
  - Frontend: `ConsentAskingSuite`, `ConsentPromptPanelSuite`, `SitePowerGridSuite`, `GameLogWithdrawnSuite`

Modified (main ones): `GameStateProtocol.scala`, `GameEventProtocol.scala`, `GameViolation.scala`, `Decisions.scala`, `OathRules.scala`, `OathRulesWalker.scala`, `ContributingPower.scala`, `WalkerPowerGather.scala`, `ProcedureWalker.scala`, `TravelSitePowers.scala`, `GameEventWire.scala`, `JournalFold.scala`, `GameApplicationService.scala`, `GameCommands.scala`, `GameIntentMapper.scala`, `Authorization.scala`, `GameRoutes.scala`, `WalkerDecisionProjector.scala`, `GameProjection.scala`, `GamePresentationProjector.scala`, `GameLogFormatter.scala`, shared `CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`, `ActionProjectionDtos.scala`, `ActionProjectionCodec.scala`, `GameProjectionDto.scala`, `GameProjectionCodec.scala`, `WorldProjectionDtos.scala`, `WorldProjectionCodec.scala`, frontend `frontend.scala`, `BoardOptionViews.scala`, `BoardOptions.scala`, `BoardDecisionPanel.scala`, `WalkerPanelSupport.scala`, `ParkedDecision.scala`, `CardInspection.scala`, `CardInspectionOverlay.scala`, `ServerUiSupport.scala`, `WorldBoardRenderer.scala`, `TableScreen.scala`, `GameLogPane.scala`, `frontend/styles.css`, `DESIGN.md`, docs.

---

### Task 1: Consent state, events and their wire format

**Files:**
- Create: `src/main/scala/oathdigital/model/Consent.scala`
- Create: `src/main/scala/oathdigital/gameplay/consent/ConsentEvolution.scala`
- Create: `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala`
- Modify: `src/main/scala/oathdigital/model/GameStateProtocol.scala` (`ReadyGame`)
- Modify: `src/main/scala/oathdigital/model/GameEventProtocol.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:68-117` (`evolve`)
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala:19-21, 27-46, 246-271`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala:134-177` (one silent arm, replaced in Task 8)
- Test: `src/test/scala/oathdigital/model/ConsentStateSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/consent/ConsentEvolutionSuite.scala`

**Interfaces:**
- Produces (model, package `oathdigital.model`):
  - `enum ConsentPreference(val key: String) { Never, Always }`
  - `enum ConsentSetting(val key: String) { Never, CaseByCase, Always }` with `preference: Option[ConsentPreference]` and `ConsentSetting.fromKey(key: String): Option[ConsentSetting]`
  - `enum ConsentChoice(val key: String) { Never, No, Yes, Always }` with `granted: Boolean`, `setting: Option[ConsentSetting]` and `ConsentChoice.fromKey`
  - `enum ConsentAnswer { Granted, Refused }`
  - `ConsentKey(power: PowerId, requester: PlayerId, decisionId: String, target: DecisionOptionRef)`
  - `ConsentAsk(granter: PlayerId, powers: Vector[PowerId], answer: Option[Boolean] = None)`
  - `ConsentRequest(requester, decisionId, target, asks: Vector[ConsentAsk])` with `keys` and `open`
  - `ConsentTurn(round: Int, player: PlayerId)` with `ConsentTurn.of(ready: ReadyGame)`
  - `ConsentState(preferences, answers, pending, turn)` with these methods:
    - `preference(granter, power, requester)`
    - `remembered(key, now)`
    - `requested`, `answered`, `withdrawn`, `preferenceSet`, `cleared`
    - `carried`
  - `ReadyGame.consent: ConsentState = ConsentState()`
- Produces (events): `sealed trait ConsentEvent extends OathEvent`, with these cases in `object OathEvent`:
  - `ConsentRequested(request: ConsentRequest)`
  - `ConsentAnswered(granter: PlayerId, granted: Boolean)`
  - `ConsentRequestWithdrawn(requester: PlayerId)`
  - `ConsentPreferenceSet(granter: PlayerId, power: PowerId, requester: PlayerId, setting: ConsentSetting)`
  - `ConsentPreferencesCleared(granter: PlayerId, power: PowerId)`
- Produces (gameplay): `oathdigital.gameplay.consent.ConsentEvolution.evolve(ready: ReadyGame, event: ConsentEvent): Either[OathViolation, ReadyGame]`

- [ ] **Step 1: Write the failing model tests**

Create `src/test/scala/oathdigital/model/ConsentStateSuite.scala`:

```scala
package oathdigital.model

import oathdigital.testkit.Unwrap.accepted

/** The consent ledger's transitions (consent design, "State" and rulings
  * 9, 12, 14, 15 and 17). */
class ConsentStateSuite extends munit.FunSuite:
  private val red = PlayerId("red")
  private val blue = PlayerId("blue")
  private val green = PlayerId("green")
  private val pass = PowerId("site.narrow-pass.pass")
  private val police = PowerId("denizen.secret-police")
  private val woods = DecisionOptionRef.Site(SiteId("site:deep-woods"))
  private val turn = ConsentTurn(1, blue)

  private def request(asks: ConsentAsk*): ConsentRequest =
    ConsentRequest(blue, "travel.destination", woods, asks.toVector)
  private def key(power: PowerId): ConsentKey =
    ConsentKey(power, blue, "travel.destination", woods)
  private def waitingOn(asks: ConsentAsk*): ConsentState =
    accepted(ConsentState().requested(request(asks*)))
  private val redOnPass = ConsentAsk(red, Vector(pass))
  private val greenOnPolice = ConsentAsk(green, Vector(police))

  test("a grant from the only ask is remembered for the turn and ends the request"):
    val granted = accepted(waitingOn(redOnPass).answered(red, granted = true,
      turn))
    assertEquals(granted.pending, None)
    assertEquals(granted.remembered(key(pass), turn),
      Some(ConsentAnswer.Granted))

  test("a refusal settles the request at once and refuses every key"):
    val refused = accepted(waitingOn(redOnPass, greenOnPolice)
      .answered(green, granted = false, turn))
    assertEquals(refused.pending, None)
    assertEquals(refused.remembered(key(pass), turn),
      Some(ConsentAnswer.Refused))
    assertEquals(refused.remembered(key(police), turn),
      Some(ConsentAnswer.Refused))

  test("a grant with another ask still open keeps the request waiting"):
    val partial = accepted(waitingOn(redOnPass, greenOnPolice)
      .answered(red, granted = true, turn))
    assertEquals(partial.pending.map(_.open.map(_.granter)),
      Some(Vector(green)))
    assertEquals(partial.remembered(key(pass), turn), None)

  test("answers expire with their turn, and the next turn's replace them"):
    val granted = accepted(waitingOn(redOnPass).answered(red, granted = true,
      turn))
    val later = ConsentTurn(1, red)
    assertEquals(granted.remembered(key(pass), later), None)
    val next = accepted(accepted(granted.requested(request(greenOnPolice)))
      .answered(green, granted = false, later))
    assertEquals(next.answers.keySet, Set(key(police)))

  test("only an asked granter answers, and only the requester withdraws"):
    val open = waitingOn(redOnPass)
    assert(open.answered(green, granted = true, turn).isLeft)
    assert(open.withdrawn(red).isLeft)
    assertEquals(accepted(open.withdrawn(blue)), ConsentState())

  test("a second request while one waits is refused, as is one with no ask"):
    assert(waitingOn(redOnPass).requested(request(greenOnPolice)).isLeft)
    assert(ConsentState().requested(request()).isLeft)

  test("a preference is set, replaced, removed by case by case, and " +
      "cleared per granter and power"):
    val always = ConsentState().preferenceSet(red, pass, blue,
      ConsentSetting.Always)
    assertEquals(always.preference(red, pass, blue),
      Some(ConsentPreference.Always))
    val never = always.preferenceSet(red, pass, blue, ConsentSetting.Never)
    assertEquals(never.preference(red, pass, blue),
      Some(ConsentPreference.Never))
    assertEquals(never.preferenceSet(red, pass, blue,
      ConsentSetting.CaseByCase), ConsentState())
    val three = never.preferenceSet(red, pass, green, ConsentSetting.Always)
      .preferenceSet(red, police, blue, ConsentSetting.Always)
    assertEquals(three.cleared(red, pass).preferences.keySet,
      Set((red, police, blue)))

  test("what an undo carries keeps answers and preferences, not a request"):
    val open = waitingOn(redOnPass).preferenceSet(red, pass, green,
      ConsentSetting.Always)
    assertEquals(open.carried, open.copy(pending = None))

  test("a choice's grant and the preference it sets"):
    assertEquals(ConsentChoice.values.toVector.map(choice =>
      (choice.key, choice.granted, choice.setting)), Vector(
      ("never", false, Some(ConsentSetting.Never)),
      ("no", false, None),
      ("yes", true, None),
      ("always", true, Some(ConsentSetting.Always))))
    assertEquals(ConsentSetting.fromKey("case-by-case"),
      Some(ConsentSetting.CaseByCase))
    assertEquals(ConsentChoice.fromKey("maybe"), None)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.model.ConsentStateSuite"`
Expected: compilation FAIL, `Not found: type ConsentTurn` (and the other consent types).

- [ ] **Step 3: Write the model**

Create `src/main/scala/oathdigital/model/Consent.scala`:

```scala
package oathdigital.model

/** A granter's standing answer to one requester on one power (consent
  * design, ruling 15). Case-by-case is no entry. */
enum ConsentPreference(val key: String):
  case Never extends ConsentPreference("never")
  case Always extends ConsentPreference("always")

/** What a preference change sets: a standing answer, or case by case, which
  * removes the entry. */
enum ConsentSetting(val key: String):
  case Never extends ConsentSetting("never")
  case CaseByCase extends ConsentSetting("case-by-case")
  case Always extends ConsentSetting("always")

  def preference: Option[ConsentPreference] = this match
    case Never => Some(ConsentPreference.Never)
    case CaseByCase => None
    case Always => Some(ConsentPreference.Always)

object ConsentSetting:
  def fromKey(key: String): Option[ConsentSetting] = values.find(_.key == key)

/** A granter's answer to a prompt. Never Allow and Always Allow also set
  * the granter's preference for the requester (user story, step 6). */
enum ConsentChoice(val key: String):
  case Never extends ConsentChoice("never")
  case No extends ConsentChoice("no")
  case Yes extends ConsentChoice("yes")
  case Always extends ConsentChoice("always")

  def granted: Boolean = this == Yes || this == Always

  def setting: Option[ConsentSetting] = this match
    case Never => Some(ConsentSetting.Never)
    case Always => Some(ConsentSetting.Always)
    case No | Yes => None

object ConsentChoice:
  def fromKey(key: String): Option[ConsentChoice] = values.find(_.key == key)

enum ConsentAnswer:
  case Granted, Refused

/** What one answer is remembered for (ruling 12): the power, the requester,
  * the decision and the option. The decision keeps Deep Woods as a Travel
  * destination apart from Deep Woods in another question. */
final case class ConsentKey(power: PowerId, requester: PlayerId,
    decisionId: String, target: DecisionOptionRef)

/** One granter asked about their powers on one option, and their answer
  * once given. */
final case class ConsentAsk(granter: PlayerId, powers: Vector[PowerId],
    answer: Option[Boolean] = None)

final case class ConsentRequest(requester: PlayerId, decisionId: String,
    target: DecisionOptionRef, asks: Vector[ConsentAsk]):
  /** Every key the request settles: each asked power, for this option. */
  def keys: Vector[ConsentKey] = asks.flatMap(ask =>
    ask.powers.map(ConsentKey(_, requester, decisionId, target)))

  /** The asks still waiting on their granter. */
  def open: Vector[ConsentAsk] = asks.filter(_.answer.isEmpty)

/** The turn answers belong to: a round and its active player. */
final case class ConsentTurn(round: Int, player: PlayerId)
object ConsentTurn:
  def of(ready: ReadyGame): ConsentTurn = ConsentTurn(
    ready.game.current.tracks.round, ready.game.current.turn.activePlayer)

/** The consent ledger (consent design, "State"): preferences keyed by
  * granter, power and requester; the answers of `turn`; and the request
  * waiting on its granters, if any. It lives beside the game, not in it,
  * because undo rewinds the game to a saved state and the ledger must
  * outlive that. */
final case class ConsentState(
    preferences: Map[(PlayerId, PowerId, PlayerId), ConsentPreference] =
      Map.empty,
    answers: Map[ConsentKey, ConsentAnswer] = Map.empty,
    pending: Option[ConsentRequest] = None,
    turn: Option[ConsentTurn] = None):

  def preference(granter: PlayerId, power: PowerId, requester: PlayerId)
      : Option[ConsentPreference] = preferences.get((granter, power, requester))

  /** The answer remembered for `key` this turn: none once the turn the
    * answers belong to has ended (plan decision 4). */
  def remembered(key: ConsentKey, now: ConsentTurn): Option[ConsentAnswer] =
    if turn.contains(now) then answers.get(key) else None

  def requested(request: ConsentRequest): Either[OathViolation, ConsentState] =
    if pending.nonEmpty then Left(OathViolation.InvalidEventOrder(
      "a consent request is already waiting"))
    else if request.asks.isEmpty || request.asks.exists(_.answer.nonEmpty) then
      Left(OathViolation.InvalidEventOrder(
        "a consent request opens with every ask unanswered"))
    else Right(copy(pending = Some(request)))

  /** `granter`'s answer. A refusal settles the whole request as refused; the
    * last grant settles it as granted (rulings 9 and 12). */
  def answered(granter: PlayerId, granted: Boolean, now: ConsentTurn)
      : Either[OathViolation, ConsentState] =
    pending.filter(_.open.exists(_.granter == granter)) match
      case None => Left(OathViolation.InvalidEventOrder(
        s"${granter.value} has no open consent ask"))
      case Some(request) =>
        val updated = request.copy(asks = request.asks.map(ask =>
          if ask.granter == granter then ask.copy(answer = Some(granted))
          else ask))
        if !granted then Right(settled(updated, ConsentAnswer.Refused, now))
        else if updated.open.isEmpty then
          Right(settled(updated, ConsentAnswer.Granted, now))
        else Right(copy(pending = Some(updated)))

  /** The requester takes their unanswered request back; nothing is
    * remembered (ruling 14). */
  def withdrawn(requester: PlayerId): Either[OathViolation, ConsentState] =
    if pending.exists(_.requester == requester) then Right(copy(pending = None))
    else Left(OathViolation.InvalidEventOrder(
      s"${requester.value} has no consent request to withdraw"))

  def preferenceSet(granter: PlayerId, power: PowerId, requester: PlayerId,
      setting: ConsentSetting): ConsentState =
    val key = (granter, power, requester)
    copy(preferences = setting.preference.fold(preferences - key)(
      preferences.updated(key, _)))

  /** Every preference `granter` set on `power` (ruling 17). */
  def cleared(granter: PlayerId, power: PowerId): ConsentState =
    copy(preferences = preferences.filterNot { case ((by, on, _), _) =>
      by == granter && on == power })

  /** What survives an undo or a redo: everything but a waiting request
    * (consent design, "Undo"). */
  def carried: ConsentState = copy(pending = None)

  private def settled(request: ConsentRequest, answer: ConsentAnswer,
      now: ConsentTurn): ConsentState =
    val kept = if turn.contains(now) then answers
      else Map.empty[ConsentKey, ConsentAnswer]
    copy(answers = kept ++ request.keys.map(_ -> answer), pending = None,
      turn = Some(now))
```

In `src/main/scala/oathdigital/model/GameStateProtocol.scala`, give `ReadyGame` its ledger after `knowledge`:

```scala
final case class ReadyGame(
    game: OathGame,
    playerColors: Map[PlayerId, PlayerColor],
    setup: FirstGameSupportState,
    banks: MaterialBankState,
    knowledge: CardKnowledge = CardKnowledge(),
    consent: ConsentState = ConsentState()
):
```

Then run `grep -rn "ReadyGame(" src/main/scala src/test/scala | grep -v "ReadyGame(game\|ReadyGame(\$"`. If a positional pattern match `case ReadyGame(a, b, c, d, e)` exists, add a `_` for the new field. A constructor call with five arguments needs no change.

- [ ] **Step 4: Run the model tests**

Run: `./sbtw "testOnly oathdigital.model.ConsentStateSuite"`
Expected: PASS, 9 tests.

- [ ] **Step 5: Write the failing event tests**

Create `src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala`:

```scala
package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

/** The consent facts round-trip through the envelope (consent design,
  * "Events"). */
class ConsentEventWireSuite extends munit.FunSuite:
  private val p1 = PlayerId("p1")
  private val p2 = PlayerId("p2")
  private val pass = PowerId("site.narrow-pass.pass")
  private val request = ConsentRequest(p1, "travel.destination",
    DecisionOptionRef.Site(SiteId("site:dunes")),
    Vector(ConsentAsk(p2, Vector(pass))))

  private def roundTrip(event: OathEvent): Either[Any, OathEvent] =
    GameEventWire.encodeEvent("g", 7L, event).flatMap(encoded =>
      GameEventWire.decode(encoded)).map(_.event)

  private val events: Vector[(OathEvent, String)] = Vector(
    ConsentRequested(request) -> "consent.requested",
    ConsentAnswered(p2, granted = true) -> "consent.answered",
    ConsentAnswered(p2, granted = false) -> "consent.answered",
    ConsentRequestWithdrawn(p1) -> "consent.request-withdrawn",
    ConsentPreferenceSet(p2, pass, p1, ConsentSetting.CaseByCase) ->
      "consent.preference-set",
    ConsentPreferencesCleared(p2, pass) -> "consent.preferences-cleared")

  test("every consent fact round-trips"):
    events.foreach { case (event, _) =>
      assertEquals(roundTrip(event), Right(event)) }

  test("each consent fact has its own event type"):
    events.foreach { case (event, eventType) =>
      assertEquals(GameEventWire.encodeEvent("g", 0L, event)
        .map(_("eventType").str), Right(eventType)) }

  test("an unknown preference setting is refused"):
    val encoded = GameEventWire.encodeEvent("g", 0L,
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always))
      .map(value => { value("payload")("setting") = "sometimes"; value })
    assert(encoded.flatMap(GameEventWire.decode).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/consent/ConsentEvolutionSuite.scala`:

```scala
package oathdigital.gameplay.consent

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** The rules fold consent facts into the ledger and nothing else. */
class ConsentEvolutionSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val pass = PowerId("site.narrow-pass.pass")
  private val dunes = DecisionOptionRef.Site(SiteId("site:dunes"))
  private val request = ConsentRequest(p1, "travel.destination", dunes,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val table = Table.start.state

  private def folded(events: OathEvent*): ReadyGame =
    Outcomes.ready(accepted(events.foldLeft[Either[OathViolation, OathState]](
      Right(table))((state, event) => state.flatMap(rules.evolve(_, event)))))

  test("a request, then its grant, is remembered for the active turn"):
    val after = folded(ConsentRequested(request), ConsentAnswered(p2, true))
    assertEquals(after.consent.pending, None)
    assertEquals(after.consent.remembered(
      ConsentKey(pass, p1, "travel.destination", dunes),
      ConsentTurn.of(after)), Some(ConsentAnswer.Granted))
    assertEquals(after.game, Outcomes.ready(table).game)

  test("a withdrawal, a preference and a clearing change only the ledger"):
    val after = folded(ConsentRequested(request), ConsentRequestWithdrawn(p1),
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always))
    assertEquals(after.consent.pending, None)
    assertEquals(after.consent.preference(p2, pass, p1),
      Some(ConsentPreference.Always))
    assertEquals(folded(ConsentPreferenceSet(p2, pass, p1,
      ConsentSetting.Always), ConsentPreferencesCleared(p2, pass)).consent,
      ConsentState())

  test("an answer with no request waiting is refused"):
    assert(rules.evolve(table, ConsentAnswered(p2, true)).isLeft)
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.serialization.ConsentEventWireSuite oathdigital.gameplay.consent.ConsentEvolutionSuite"`
Expected: compilation FAIL, `ConsentRequested is not a member of object OathEvent`.

- [ ] **Step 7: Add the events, their evolution and their codec**

In `src/main/scala/oathdigital/model/GameEventProtocol.scala`, after `trait WalkerEvent extends OathEvent` (line 14), add:

```scala
/** The consent facts (consent design, "Events"). None opens a command
  * batch: the journal fold closes any open batch before one, so consent
  * stays off the undo stack. */
sealed trait ConsentEvent extends OathEvent
```

and inside `object OathEvent`, after `StepRestored`:

```scala
  /** `request.requester` asks the granters in `request.asks`; every ask is
    * unanswered. */
  final case class ConsentRequested(request: ConsentRequest)
      extends ConsentEvent
  /** `granter`'s answer to the waiting request. A refusal, or the last
    * grant, settles it. */
  final case class ConsentAnswered(granter: PlayerId, granted: Boolean)
      extends ConsentEvent
  /** The requester took their unanswered request back with Undo. */
  final case class ConsentRequestWithdrawn(requester: PlayerId)
      extends ConsentEvent
  final case class ConsentPreferenceSet(granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting) extends ConsentEvent
  /** `granter` no longer rules `power`'s card: their preferences on it go
    * (ruling 17). */
  final case class ConsentPreferencesCleared(granter: PlayerId,
      power: PowerId) extends ConsentEvent
```

Create `src/main/scala/oathdigital/gameplay/consent/ConsentEvolution.scala`:

```scala
package oathdigital.gameplay.consent

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

/** Folds the consent facts into `ReadyGame.consent` (consent design,
  * "Events"). Nothing else in the game changes. */
object ConsentEvolution:
  def evolve(ready: ReadyGame, event: ConsentEvent)
      : Either[OathViolation, ReadyGame] =
    val consent = ready.consent
    (event match
      case ConsentRequested(request) => consent.requested(request)
      case ConsentAnswered(granter, granted) =>
        consent.answered(granter, granted, ConsentTurn.of(ready))
      case ConsentRequestWithdrawn(requester) => consent.withdrawn(requester)
      case ConsentPreferenceSet(granter, power, requester, setting) =>
        Right(consent.preferenceSet(granter, power, requester, setting))
      case ConsentPreferencesCleared(granter, power) =>
        Right(consent.cleared(granter, power))
    ).map(next => ready.copy(consent = next))
```

In `OathRules.evolve` (`src/main/scala/oathdigital/gameplay/OathRules.scala`), add before the undo arms (`case _: CommandAccepted => Right(state)`):

```scala
      case event: ConsentEvent => state match
        case Ready(ready) => ConsentEvolution.evolve(ready, event)
          .map(Ready.apply)
        case _ => Left(GameNotStarted)
```

and import `oathdigital.gameplay.consent.ConsentEvolution`.

Create `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala`:

```scala
package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

/** The consent facts (consent design, "Events"). */
private[serialization] trait ConsentEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val consentDiscriminator
      : PartialFunction[OathEvent, String] =
      case _: ConsentRequested => ConsentRequestedType
      case _: ConsentAnswered => ConsentAnsweredType
      case _: ConsentRequestWithdrawn => ConsentRequestWithdrawnType
      case _: ConsentPreferenceSet => ConsentPreferenceSetType
      case _: ConsentPreferencesCleared => ConsentPreferencesClearedType

  protected final val consentEncoder: PartialFunction[OathEvent, ujson.Value] =
      case ConsentRequested(request) => ujson.Obj(
        "requester" -> request.requester.value,
        "decisionId" -> request.decisionId,
        "target" -> DecisionAnswerCodec.encodeRef(request.target),
        "asks" -> ujson.Arr.from(request.asks.map(ask => ujson.Obj(
          "granter" -> ask.granter.value,
          "powers" -> stringArray(ask.powers.map(_.value))))))
      case ConsentAnswered(granter, granted) =>
        ujson.Obj("granter" -> granter.value, "granted" -> granted)
      case ConsentRequestWithdrawn(requester) =>
        ujson.Obj("requester" -> requester.value)
      case ConsentPreferenceSet(granter, power, requester, setting) =>
        ujson.Obj("granter" -> granter.value, "powerId" -> power.value,
          "requester" -> requester.value, "setting" -> setting.key)
      case ConsentPreferencesCleared(granter, power) =>
        ujson.Obj("granter" -> granter.value, "powerId" -> power.value)

  protected final def consentDecode(eventType: String, payload: ujson.Value,
      path: String): Option[Either[WireError, OathEvent]] =
    def player(name: String) = PlayerId(payload(name).str)
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] =
        case ConsentRequestedType =>
          DecisionAnswerCodec.decodeRef(payload("target"), s"$path.target")
            .map(target => ConsentRequested(ConsentRequest(player("requester"),
              payload("decisionId").str, target,
              payload("asks").arr.toVector.map(ask => ConsentAsk(
                PlayerId(ask("granter").str),
                ask("powers").arr.toVector.map(power => PowerId(power.str))))
            )))
        case ConsentAnsweredType =>
          Right(ConsentAnswered(player("granter"), payload("granted").bool))
        case ConsentRequestWithdrawnType =>
          Right(ConsentRequestWithdrawn(player("requester")))
        case ConsentPreferenceSetType =>
          val key = payload("setting").str
          ConsentSetting.fromKey(key).toRight(WireError.InvalidValue(
            s"$path.setting", s"unknown consent setting '$key'"))
            .map(ConsentPreferenceSet(player("granter"),
              PowerId(payload("powerId").str), player("requester"), _))
        case ConsentPreferencesClearedType =>
          Right(ConsentPreferencesCleared(player("granter"),
            PowerId(payload("powerId").str)))
    decoder.lift(eventType)
}
```

In `GameEventWire.scala`:
- add `with ConsentEventCodec` after `with UndoEventCodec` in the object's parents;
- after `val StepRestoredType`, add:

```scala
  val ConsentRequestedType = "consent.requested"
  val ConsentAnsweredType = "consent.answered"
  val ConsentRequestWithdrawnType = "consent.request-withdrawn"
  val ConsentPreferenceSetType = "consent.preference-set"
  val ConsentPreferencesClearedType = "consent.preferences-cleared"
```

- chain the three dispatchers: append `.orElse(consentDiscriminator)` to `discriminatorDispatch`, `.orElse(consentEncoder)` to `encoderDispatch`, and `.orElse(consentDecode(eventType, payload, path))` after the `undoDecode` line in `decodePayload`.

`GameLogFormatter.eventLines` matches every `OathEvent` by name and fails compilation now. Add a silent arm above the `_: OathEvent.CommandAccepted` arm (Task 8 replaces it):

```scala
      // Consent's lines are Task 8's; until then a consent fact posts none.
      case _: ConsentEvent => (Vector.empty, run)
```

Run `./sbtw compile` and fix any other non-exhaustive match the compiler names over `OathEvent` the same way: a consent event changes nothing there.

- [ ] **Step 8: Run the Task 1 suites**

Run: `./sbtw "testOnly oathdigital.model.ConsentStateSuite oathdigital.serialization.ConsentEventWireSuite oathdigital.gameplay.consent.ConsentEvolutionSuite"`
Expected: PASS.

- [ ] **Step 9: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. `ReadyGame` equality now includes an empty ledger on both sides.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/Consent.scala src/main/scala/oathdigital/model/GameStateProtocol.scala src/main/scala/oathdigital/model/GameEventProtocol.scala src/main/scala/oathdigital/gameplay/consent/ConsentEvolution.scala src/main/scala/oathdigital/gameplay/OathRules.scala src/main/scala/oathdigital/serialization/ConsentEventCodec.scala src/main/scala/oathdigital/serialization/GameEventWire.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/model/ConsentStateSuite.scala src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala src/test/scala/oathdigital/gameplay/consent/ConsentEvolutionSuite.scala
git commit -m "feat(consent): add the consent ledger, its events and their wire format"
```

(Add any other file Step 7's compile fixes touched.)

---

### Task 2: Waivers and the waivable-power registry

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala:68-79` (`Restriction`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala:144-228` (`NarrowPassSitePower`)
- Create: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`

**Interfaces:**
- Consumes: nothing from Task 1 but the model package.
- Produces:
  - `oathdigital.gameplay.powerresolver.Waiver(granter: ReadyGame => Option[PlayerId], source: PowerSourceRef, note: (PowerCtx, DecisionOptionRef) => PowerNote)`
  - `Restriction(fn, note = Contribution.silent, waiver: Option[Waiver] = None)`
  - `NarrowPassSitePower.noTravelConsent: NoteKey`, with key `no-travel-consent`
  - `final class ConsentPowers(powers: Vector[ContributingPower])` with these methods:
    - `waiver(power: PowerId): Option[Waiver]`
    - `granter(ready: ReadyGame, power: PowerId): Option[PlayerId]`
    - `source(power: PowerId): Option[PowerSourceRef]`
    - `ruledBy(ready: ReadyGame, player: PlayerId): Vector[PowerId]`, sorted by id
  - `ConsentPowers.default(catalog: ExecutableCatalog): ConsentPowers`

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.powers.travel.{MinesSite, NarrowPassSite,
  TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table.{p1, p2}

/** Which powers can be waived, and who may waive them (consent design,
  * rulings 1, 4 and 16). */
class ConsentPowersSuite extends munit.FunSuite:
  import TravelFixture.{board, on, pass}
  private val powers = ConsentPowers.default(catalog)
  private val ruled = on(board())(_.warbandsAt(pass, p2, 1))

  test("the Narrow Pass is waivable, named for its site"):
    assert(powers.waiver(NarrowPassSite.pass).nonEmpty)
    assertEquals(powers.source(NarrowPassSite.pass),
      Some(PowerSourceRef.Site(NarrowPassSite.id)))

  test("a power that restricts nobody's enemies is not waivable"):
    assertEquals(powers.waiver(MinesSite.mountain), None)
    assertEquals(powers.source(MinesSite.mountain), None)

  test("the Pass's granter is the player who rules it, and nobody under " +
      "bandits"):
    assertEquals(powers.granter(ruled, NarrowPassSite.pass), Some(p2))
    assertEquals(powers.granter(board(), NarrowPassSite.pass), None)

  test("a player rules the waivable powers of the sites they rule"):
    assertEquals(powers.ruledBy(ruled, p2), Vector(NarrowPassSite.pass))
    assertEquals(powers.ruledBy(ruled, p1), Vector.empty)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.ConsentPowersSuite"`
Expected: compilation FAIL, `Not found: ConsentPowers`.

- [ ] **Step 3: Add `Waiver`, the Pass's waiver and the registry**

In `ContributingPower.scala`, add `PowerSourceRef` to the `oathdigital.model` import list, then replace the `Restriction` case class with:

```scala
/** Validates the whole action tree root, returning a violation to reject the
  * action wholesale. Covers cannot-effects. `note` is the hover reason of an
  * option the restriction look-ahead hides because of it; it writes no Game
  * Log line (travel destination design, ruling 10). `waiver` is set when the
  * power restricts its ruler's enemies, whose ruler may consent to ignore
  * it (consent design, "Powers declare waivability").
  */
final case class Restriction(
    fn: (PowerCtx, Operation) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent,
    waiver: Option[Waiver] = None
) extends Contribution

/** How a restriction on enemies may be waived (consent design, rulings 1 and
  * 4). `granter` is the player who may consent: the power's ruler, or `None`
  * when bandits, the Empire or nobody rules it, since none of them consents.
  * `source` names the power in the log. `note` is the hover reason of an
  * option the player may ask consent for.
  */
final case class Waiver(
    granter: ReadyGame => Option[PlayerId],
    source: PowerSourceRef,
    note: (PowerCtx, DecisionOptionRef) => PowerNote)
```

Run `grep -rn "case Restriction(" src/main/scala src/test/scala`. A two-field pattern gains a `_`. Today the search returns nothing.

In `NarrowPassSitePower` (`TravelSitePowers.scala`):

```scala
  override def noteKeys: Vector[NoteKey] =
    Vector(NarrowPassSitePower.noTarget, NarrowPassSitePower.noTravel,
      NarrowPassSitePower.noTravelConsent)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx), travelNote,
        Some(travelWaiver))),
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked, campaignNote)))

  /** "Unless you have the consent of the Pass's ruler" (consent design,
    * slice 1): the player who rules the Pass may waive it for a Travel. */
  private val travelWaiver = Waiver(
    ready => SiteRulers.rulerOf(ready, site).collect {
      case SiteRuler.Player(player) => player },
    PowerSourceRef.Site(site),
    (_, _) => NarrowPassSitePower.noTravelConsent(PowerSourceRef.Site(site)))
```

Also add, in `object NarrowPassSitePower`:

```scala
  /** "Cannot travel to other sites in the region without ruler's consent.":
    * the reason of a destination its ruler may be asked about. */
  val noTravelConsent: NoteKey = NoteKey("no-travel-consent", Vector(
    NotePart.Text("Cannot travel to other sites in the region without " +
      "ruler's consent.")))
```

Import `oathdigital.gameplay.SiteRulers` and `oathdigital.gameplay.powerresolver.Waiver` in that file if they are not already imported. `SiteRulers` is `private[gameplay]`, which this package can see.

Create `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction,
  Waiver}
import oathdigital.model.{PlayerId, PowerId, PowerSourceRef, ReadyGame}

/** The waivable powers (consent design, "Powers declare waivability"): every
  * power with a contribution that declares a `Waiver`. The grid, the
  * stale-preference check and the log all look a power up here. */
final class ConsentPowers(powers: Vector[ContributingPower]):
  private val waivers: Map[PowerId, Waiver] = powers.flatMap(power =>
    power.contributions.values.flatten.collectFirst {
      case Restriction(_, _, Some(waiver)) => power.id -> waiver
    }).toMap

  def waiver(power: PowerId): Option[Waiver] = waivers.get(power)

  def granter(ready: ReadyGame, power: PowerId): Option[PlayerId] =
    waivers.get(power).flatMap(_.granter(ready))

  def source(power: PowerId): Option[PowerSourceRef] =
    waivers.get(power).map(_.source)

  /** The waivable powers `player` rules now, by id (ruling 16). */
  def ruledBy(ready: ReadyGame, player: PlayerId): Vector[PowerId] =
    waivers.keys.toVector.sortBy(_.value)
      .filter(granter(ready, _).contains(player))

object ConsentPowers:
  def default(catalog: ExecutableCatalog): ConsentPowers =
    new ConsentPowers(WalkerPowerCatalog.default(catalog).powers)
```

- [ ] **Step 4: Run the test, then the Travel suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.ConsentPowersSuite oathdigital.gameplay.TravelSitePowersSuite oathdigital.application.TravelDestinationRowsSuite"`
Expected: PASS. No walker code reads the waiver yet, so the Travel suites are unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala
git commit -m "feat(consent): let a restriction declare its waiver, and the Pass declare one for Travel"
```

---

### Task 3: Preferences through the service, kept across undo

This task builds the service routing that every consent command uses, and the one command that needs no walker: `SetConsentPreference`. It also fixes the fold (plan decision 1) and adds the stale-preference clearing after every append.

**Files:**
- Create: `src/main/scala/oathdigital/application/ConsentDesk.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Modify: `src/main/scala/oathdigital/application/JournalFold.scala:74-102`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:228-275, 327-410, 451-531`
- Modify: `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala:31-48`
- Modify: `src/test/scala/oathdigital/testkit/SituationDriver.scala:164-201`
- Create: `src/test/scala/oathdigital/application/ConsentFixture.scala`
- Test: `src/test/scala/oathdigital/application/ConsentPreferenceServiceSuite.scala`

**Interfaces:**
- Consumes: Task 1's ledger and events. Task 2's `ConsentPowers`.
- Produces:
  - `GameCommand.SetConsentPreference(granter: PlayerId, power: PowerId, requester: PlayerId, setting: ConsentSetting)`, a case of the new `sealed trait GameCommand.ConsentCommand extends GameCommand`.
  - `OathViolation.ConsentPending` and `OathViolation.ConsentUnavailable(reason: String)`.
  - `private[application] final class ConsentDesk(rules: OathRules, powers: ConsentPowers)` with these methods, each `Result = Either[OathViolation, Vector[OathEvent]]`:
    - `pending(state: OathState): Option[ConsentRequest]`
    - `setPreference(state, granter, power, requester, setting): Result`
    - `stale(state: OathState): Vector[OathEvent]`
  - Task 5 adds `request`, `answer` and `withdraw` to the desk.
  - `GameApplicationService` routes every `ConsentCommand`, and Undo and Redo, through one `routed` method. It appends a stale-preference clearing after every batch.
  - `ConsentFixture` (test): `passRuledByP2: ReadyGame`, `dunes: DecisionOptionRef.Site`, `service()`, `startTravel` and `setPreference(setting, granter = p2, requester = p1)`.

- [ ] **Step 1: Write the test fixture and the failing service tests**

Create `src/test/scala/oathdigital/application/ConsentFixture.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A game for consent through the service: TravelFixture's board with the
  * Narrow Pass ruled by p2. p1 is active at the Cradle's plains, and the
  * Pass blocks Dunes, the Provinces' plains, from there. */
object ConsentFixture:
  import TravelFixture.{board, on, pass, provincePlains}

  val passRuledByP2: ReadyGame = on(board())(_.warbandsAt(pass, p2, 1))
  val dunes: DecisionOptionRef.Site = DecisionOptionRef.Site(provincePlains)
  val gameId = "consent"

  def service(at: ReadyGame = passRuledByP2)(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.from(at).service()

  /** p1 starts a Travel; the walk parks on the destination. */
  def startTravel(service: GameApplicationService, at: Long = 0L)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at, ParkedServiceFixture.startTravel(p1)))

  def setPreference(setting: ConsentSetting, granter: PlayerId = p2,
      requester: PlayerId = p1): GameCommand =
    GameCommand.SetConsentPreference(granter, NarrowPassSite.pass, requester,
      setting)

  /** p1 asks for consent to travel to Dunes. */
  val requestDunes: GameCommand = GameCommand.RequestConsent(p1,
    TravelProcedure.destinationDecisionId, dunes)

  def consent(accepted: GameAccepted)(using munit.Location): ConsentState =
    Outcomes.ready(accepted.state).consent
```

`requestDunes` names `GameCommand.RequestConsent`, which Task 5 adds. Leave that member out of this task's copy of the file, along with the `TravelProcedure` import only it uses, and add both in Task 5, Step 1.

Create `src/test/scala/oathdigital/application/ConsentPreferenceServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.travel.{MinesSite, NarrowPassSite}
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentPreferenceSet,
  ConsentPreferencesCleared}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Consent preferences through the service (consent design, rulings 15 to
  * 18, "Undo" and plan decision 1). */
class ConsentPreferenceServiceSuite extends munit.FunSuite:
  import ConsentFixture._
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  test("a preference is accepted during another player's parked decision " +
      "and opens no command batch"):
    val (service, repository) = ConsentFixture.service()
    val started = startTravel(service)
    val set = accepted(service.handle(gameId, started.nextSequence,
      setPreference(ConsentSetting.Always)))
    assertEquals(set.events, Vector(ConsentPreferenceSet(p2,
      NarrowPassSite.pass, p1, ConsentSetting.Always)))
    assertEquals(consent(set).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Always))
    // p1's start is still the step offered back to p1, and a reload, which
    // folds the whole journal in one pass, offers the same one.
    assertEquals(set.undo.map(step => (step.issuer, step.from, step.until)),
      Some((p1, 0L, started.nextSequence)))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.flatMap(_.undo)),
      Right(set.undo))

  test("an undo after a preference keeps it, and a reload agrees"):
    val (service, repository) = ConsentFixture.service()
    val started = startTravel(service)
    val set = accepted(service.handle(gameId, started.nextSequence,
      setPreference(ConsentSetting.Never)))
    val undone = accepted(service.handle(gameId, set.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(consent(undone).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Never))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(_.state)),
      Right(Some(undone.state)))

  test("a redo keeps a preference set after the undo"):
    val (service, _) = ConsentFixture.service()
    val started = startTravel(service)
    val undone = accepted(service.handle(gameId, started.nextSequence,
      GameCommand.Undo(p1)))
    val set = accepted(service.handle(gameId, undone.nextSequence,
      setPreference(ConsentSetting.Always)))
    val redone = accepted(service.handle(gameId, set.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(consent(redone).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Always))

  test("case by case removes the preference"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val cleared = accepted(service.handle(gameId, set.nextSequence,
      setPreference(ConsentSetting.CaseByCase)))
    assertEquals(consent(cleared), ConsentState())

  test("only the power's ruler sets a preference, for another player at " +
      "the table, on a waivable power"):
    val (service, repository) = ConsentFixture.service()
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, granter = p3)),
      rejected(OathViolation.ConsentUnavailable(
        "p3 does not rule site.narrow-pass.pass")))
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, requester = p2)),
      rejected(OathViolation.ConsentUnavailable("p2 is not another player")))
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, requester = PlayerId("p9"))),
      rejected(OathViolation.ConsentUnavailable("p9 is not another player")))
    assertEquals(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, MinesSite.mountain, p1,
        ConsentSetting.Always)),
      rejected(OathViolation.ConsentUnavailable(
        "site.mines.mountain cannot be waived")))
    assertEquals(repository.load(gameId), Right(None))

  test("after a command, a ruler's preferences on a card they no longer " +
      "rule are cleared"):
    // The Pass holds bandits, yet the ledger still holds p2's preference:
    // the first command's append clears it.
    val stale = TravelFixture.board().copy(consent = ConsentState()
      .preferenceSet(p2, NarrowPassSite.pass, p1, ConsentSetting.Always))
    val (service, _) = ConsentFixture.service(stale)
    val started = startTravel(service)
    assertEquals(started.events.last,
      ConsentPreferencesCleared(p2, NarrowPassSite.pass))
    assertEquals(consent(started), ConsentState())
    // The clearing is not part of p1's start: the start is still offered.
    assertEquals(started.undo.map(_.until), Some(started.nextSequence - 1L))
```

Add `import oathdigital.gameplay.powers.travel.TravelFixture` to the suite's imports for the last test.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.ConsentPreferenceServiceSuite"`
Expected: compilation FAIL, `SetConsentPreference is not a member of object GameCommand`.

- [ ] **Step 3: Add the violations and the command**

In `src/main/scala/oathdigital/model/GameViolation.scala`, after `FollowUpPending`:

```scala
  /** A consent request waits on its granters: only their answers, the
    * requester's withdrawal and preference changes run (consent design,
    * "Commands"). */
  case object ConsentPending extends OathViolation
  /** A consent command that cannot apply now, and why. */
  final case class ConsentUnavailable(reason: String) extends OathViolation
```

In `GameCommands.scala`, inside `object GameCommand`, after `Redo`:

```scala
  /** A command the service routes to its consent desk, never to the rules'
    * walker gate: each is legal over a parked decision (consent design,
    * "Commands"). */
  sealed trait ConsentCommand extends GameCommand
  /** `granter` sets their standing answer for `requester` on `power`. */
  final case class SetConsentPreference(granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting) extends ConsentCommand
```

and add to `issuer` (consent commands open no batch of their own):

```scala
    case _: ConsentCommand => None
```

- [ ] **Step 4: Write the consent desk**

Create `src/main/scala/oathdigital/application/ConsentDesk.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentPreferenceSet,
  ConsentPreferencesCleared}

/** The consent commands (consent design, "Commands"). Each answers with the
  * events to append as one batch, or why it cannot run. Consent events open
  * no command batch, so nothing here is undoable. */
private[application] final class ConsentDesk(rules: OathRules,
    powers: ConsentPowers):
  private type Result = Either[OathViolation, Vector[OathEvent]]

  def pending(state: OathState): Option[ConsentRequest] = state match
    case OathState.Ready(ready) => ready.consent.pending
    case _ => None

  /** Accepted at any time, even during another player's decision (ruling
    * 18), from the player who rules the power's card now. */
  def setPreference(state: OathState, granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting): Result = for
    ready <- playing(state)
    _ <- powers.waiver(power).toRight(OathViolation.ConsentUnavailable(
      s"${power.value} cannot be waived"))
    _ <- Either.cond(powers.granter(ready, power).contains(granter), (),
      OathViolation.ConsentUnavailable(
        s"${granter.value} does not rule ${power.value}"))
    _ <- Either.cond(requester != granter &&
      ready.game.current.players.exists(_.player == requester), (),
      OathViolation.ConsentUnavailable(
        s"${requester.value} is not another player"))
  yield Vector(ConsentPreferenceSet(granter, power, requester, setting))

  /** One clearing per granter and power whose granter no longer rules the
    * power's card (ruling 17), appended after every command. */
  def stale(state: OathState): Vector[OathEvent] = state match
    case OathState.Ready(ready) =>
      ready.consent.preferences.keys.toVector
        .map((granter, power, _) => (granter, power)).distinct
        .filterNot((granter, power) =>
          powers.granter(ready, power).contains(granter))
        .sortBy((granter, power) => (granter.value, power.value))
        .map(ConsentPreferencesCleared(_, _))
    case _ => Vector.empty

  private def playing(state: OathState): Either[OathViolation, ReadyGame] =
    state match
      case OathState.Ready(ready) if ready.game.current.result.isEmpty =>
        Right(ready)
      case OathState.Ready(_) =>
        Left(OathViolation.ConsentUnavailable("the game is over"))
      case _ => Left(OathViolation.GameNotStarted)
```

`rules` is used from Task 5 on. Until then the compiler reports it as an unused private member, so in this task declare it `rules: OathRules` without `private val`. A constructor parameter that is never read compiles cleanly under `-Wunused:privates`. If the compiler still flags it, keep the parameter, read it through a `private def walker: OathRules = rules`, and delete that def in Task 5.

- [ ] **Step 5: Close a batch at a consent event, and carry the ledger across undo**

In `JournalFold.scala`, add `ConsentEvent` to the imports (`import oathdigital.model._` already covers it) and add this case to `Progress.next`, before the final `case event =>`:

```scala
        // A consent fact opens no batch and joins none (consent design,
        // "Events"): it closes the open batch, as a batch opening does, so
        // a whole-journal replay batches exactly as live appends did.
        case event: ConsentEvent =>
          val settled = closedAt(record.index)
          evolve(settled.state, event).left
            .map(EventReplayFailure(record.index, _))
            .map(after => Progress(settled.recorded(record, after), None))
```

In the same method, make both markers carry the ledger:
- in the `StepTakenBack` case, replace `settled.recorded(record, top.before)` with `settled.recorded(record, JournalFold.carried(top.before, settled.state))`;
- in the `StepRestored` case, replace `settled.recorded(record, top.after)` with `settled.recorded(record, JournalFold.carried(top.after, settled.state))`.

Add a companion object at the end of the file:

```scala
private[application] object JournalFold:
  /** `target`, a state an undo or a redo returns to, with the consent
    * ledger of `current` minus any waiting request (consent design,
    * "Undo"): grants, refusals and preferences outlive the rewind. */
  def carried(target: OathState, current: OathState): OathState =
    (target, current) match
      case (OathState.Ready(game), OathState.Ready(now)) =>
        OathState.Ready(game.copy(consent = now.consent.carried))
      case _ => target
```

- [ ] **Step 6: Route consent commands, and clear stale preferences after every append**

In `GameApplicationService.scala`:

1. Build the desk beside `journalFold`:

```scala
  private val consent = new ConsentDesk(rules, ConsentPowers.default(catalog))
```

and import `oathdigital.gameplay.powers.ConsentPowers` (the file already imports `oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}`; add it to that list).

2. In `handle`, send both branches through a new `routed`. The missing-stream branch's `handleAgainst(gameId, FoldedJournal(genesis, Vector.empty, Nil), command, ExpectedStream.MustNotExist, 0L)` becomes:

```scala
          routed(gameId, FoldedJournal(genesis, Vector.empty, Nil), command,
            ExpectedStream.MustNotExist, 0L)
```

and the existing-stream branch's inner `command match` becomes:

```scala
          command match
            case GameCommand.Begin(_, _) => Left(DuplicateGame(gameId))
            case _ =>
              reconstruct(gameId, stream).flatMap(journal =>
                routed(gameId, journal, command,
                  ExpectedStream.AtNextSequence(stream.nextSequence),
                  stream.nextSequence))
```

3. Add `routed` after `handle`:

```scala
  /** Undo and Redo go through the fold, not the rules: both are legal over a
    * pending walker (undo design, "Rulings"). Consent commands go to the
    * consent desk, legal at any time (consent design, "Commands").
    * Everything else runs in the rules. */
  private def routed(gameId: String, journal: FoldedJournal,
      command: GameCommand, expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    command match
      case GameCommand.Undo(actor) =>
        takeBack(gameId, journal, actor, expected, nextSequence)
      case GameCommand.Redo(actor) =>
        restore(gameId, journal, actor, expected, nextSequence)
      case GameCommand.SetConsentPreference(granter, power, requester,
          setting) =>
        consentBatch(gameId, journal, consent.setPreference(journal.state,
          granter, power, requester, setting), expected, nextSequence)
      case _ => handleAgainst(gameId, journal, command, expected, nextSequence)

  /** A consent command's events, appended as one batch. Any batch opening
    * among them is left out of the reported events, as it is for every
    * command. */
  private def consentBatch(gameId: String, journal: FoldedJournal,
      events: Either[OathViolation, Vector[OathEvent]],
      expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    events.left.map(CommandRejected.apply).flatMap(appended =>
      appendBatch(gameId, journal, appended, appended.filterNot(
        _.isInstanceOf[OathEvent.CommandAccepted]), expected, nextSequence))
```

4. `takeBack` and `restore` take `expected: ExpectedStream` after `actor`, and pass it to `appendBatch` in place of `ExpectedStream.AtNextSequence(nextSequence)`. They no longer pass a state: drop the `batch.before` and `batch.after` arguments, since `appendBatch` now reports the state the fold reaches, which carries the ledger. `handleAgainst` likewise drops its `transition.state` argument and passes `expected`, as before.

5. Replace `appendBatch` with a version that clears stale preferences and reports the fold's state:

```scala
  /** Appends `appended`, then any stale-preference clearing it leaves
    * (consent design, "Commands"), as one append, and reports `reported`
    * plus the clearing. The state reported is the fold's, which an undo
    * or a redo has given the current consent ledger. The fold runs before
    * the append, so a batch it would reject is never written. */
  private def appendBatch(
      gameId: String,
      journal: FoldedJournal,
      appended: Vector[OathEvent],
      reported: Vector[OathEvent],
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for
      opened <- extended(journal, appended, nextSequence)
      clearing = consent.stale(opened.state)
      folded <- extended(opened, clearing, nextSequence + appended.size)
      records <- encode(gameId, nextSequence, appended ++ clearing)
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(GameAccepted(folded.state, reported ++ clearing,
            nextSequence + count,
            undo = folded.undoable.map(offered(folded, _)),
            redo = folded.redoable.map(offered(folded, _)),
            logRevision = folded.logRevision))
        case StreamAlreadyExists => Left(DuplicateGame(gameId))
        case RepositoryAppendResult.StreamNotFound =>
          Left(GameApplicationError.StreamNotFound(gameId))
        case RepositoryAppendResult.SequenceConflict(wanted, actual) =>
          Left(GameApplicationError.SequenceConflict(wanted, actual))
        case Appended(first, count) =>
          Left(AppendAcknowledgementMismatch(
            s"expected first=$nextSequence count=${records.size}; " +
              s"repository returned first=$first count=$count"
          ))
    yield accepted

  private def extended(journal: FoldedJournal, events: Vector[OathEvent],
      from: Long): Either[GameApplicationError, FoldedJournal] =
    journalFold.extend(journal, events.zipWithIndex.map {
        case (event, offset) => RecordedEvent(from + offset, event) })
      .left.map(failure => ReplayFailure(failure.index, failure.violation))
```

6. In `applyUnblockedCommand`, add an arm that the compiler needs for exhaustiveness:

```scala
      // `routed` sends these to the consent desk; none reaches the rules.
      case _: GameCommand.ConsentCommand => Left(OathViolation.InvalidEventOrder(
        "a consent command never reaches the rules"))
```

- [ ] **Step 7: Keep the command enumerations complete**

In `PendingWalkerInvariantSuite.everyCommand`, append (the power is not waivable, so the sample is refused over every park):

```scala
    GameCommand.SetConsentPreference(actor,
      oathdigital.gameplay.powers.travel.MinesSite.mountain,
      PlayerId("p2"), ConsentSetting.Always),
```

In `SituationDriver.transition`, add before the Undo/Redo arm:

```scala
      case _: GameCommand.ConsentCommand =>
        Left(OathViolation.InvalidEventOrder(
          "consent commands need a journal; use a journaled situation"))
```

- [ ] **Step 8: Run the preference suite and the undo suites**

Run: `./sbtw "testOnly oathdigital.application.ConsentPreferenceServiceSuite oathdigital.application.UndoServiceSuite oathdigital.application.RedoServiceSuite oathdigital.application.JournalFoldSuite oathdigital.application.JournalFoldRedoSuite oathdigital.application.PendingWalkerInvariantSuite"`
Expected: PASS.

- [ ] **Step 9: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/application/ConsentDesk.scala src/main/scala/oathdigital/application/GameCommands.scala src/main/scala/oathdigital/model/GameViolation.scala src/main/scala/oathdigital/application/JournalFold.scala src/main/scala/oathdigital/application/GameApplicationService.scala src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala src/test/scala/oathdigital/testkit/SituationDriver.scala src/test/scala/oathdigital/application/ConsentFixture.scala src/test/scala/oathdigital/application/ConsentPreferenceServiceSuite.scala
git commit -m "feat(consent): set preferences at any time, kept across undo and cleared when the ruler changes"
```

---
### Task 4: Askable options and the ledger check in the walker

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:328-331` (`OptionReason`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala:1-3, 168-247`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:443-453` (`narrowAt`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (new `consentAsks`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:560-564` (`reasonText`, one arm)
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassConsentSuite.scala`

**Interfaces:**
- Consumes: Task 1's `ConsentState.remembered`, `ConsentKey`, `ConsentTurn`, `ConsentAsk`. Task 2's `Restriction.waiver` and `NarrowPassSitePower.noTravelConsent`.
- Produces:
  - `OptionReason.Askable(power: PowerId, note: PowerNote, asks: Vector[ConsentAsk])`
  - `WalkerPowerGather.lookAheadNoted(...)` now returns `Vector[(DecisionOptionRef, OptionReason)]`
  - `OathRulesWalker.consentAsks(state: OathState, requester: PlayerId, decisionId: String, target: DecisionOptionRef): Either[OathViolation, Vector[ConsentAsk]]`. It returns the asks of `target` when `target` is an askable option of the parked `decisionId` that `requester` owns. Otherwise it returns `ConsentUnavailable("{target wire id} is not an option to ask consent for")`.

- [ ] **Step 1: Write the failing walker tests**

Create `src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassConsentSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Restriction}
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks}
import oathdigital.testkit.Table.p2
import oathdigital.testkit.Unwrap.{accepted, present, rejected}

/** Which Travel destinations the Narrow Pass makes askable (consent
  * design, rulings 4 to 8 and 12, "Walker and powers"). The fixture board's
  * Pass holds bandits; `ruled` hands it to p2. */
class NarrowPassConsentSuite extends munit.FunSuite:
  import PowerFixture.actor
  import TravelFixture._

  private val ruled = on(board())(_.warbandsAt(pass, p2, 1))
  private val dunes = DecisionOptionRef.Site(provincePlains)
  private val passPower = NarrowPassSite.pass
  private val destination = TravelProcedure.destinationDecisionId
  private val key = ConsentKey(passPower, actor, destination, dunes)

  /** `ready` remembering `answer` for Dunes, for `turn`. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer,
      turn: ConsentTurn): ReadyGame = ready.copy(consent = ConsentState(
    answers = Map(key -> answer), turn = Some(turn)))

  /** A restriction nobody can waive, on Dunes too. */
  private object DunesBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.dunes-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(provincePlains)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.TravelActionEligibility -> Vector(Restriction(
        (ctx, _) => TravelRoute.pawnMove(ctx.operation)
          .filter(_.destination == provincePlains)
          .map(_ => OathViolation.InvalidEventOrder("the barrier")),
        (_, _) => Some(NarrowPassSitePower.noTravel(
          PowerSourceRef.Site(provincePlains))))))

  /** Dunes as the parked destination decision offers it, once `actor`
    * starts a Travel with `modifiers` under the catalog's powers and
    * `extra`. */
  private def dunesOffered(ready: ReadyGame,
      modifiers: Vector[PowerId] = Vector.empty,
      extra: Vector[ContributingPower] = Vector.empty)(using munit.Location)
      : DecisionOption =
    val powers = WalkerPowers(WalkerPowerCatalog.default(catalog).powers ++
      extra)
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(ready), ActionRef.Travel, actor, modifiers))
    present(PlacementPicks.parkedOptions(started.state,
      WalkerPowers.selected(powers, modifiers)).find(_.ref == dunes),
      "Dunes among the destinations")

  private def assertNotedBy(option: DecisionOption, noteKey: String)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, noteKey)
    case other => fail(s"expected an unavailable option noted $noteKey, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  test("a destination only a player-ruled Pass blocks is askable of its ruler"):
    dunesOffered(ruled) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks)) =>
        assertEquals(power, passPower)
        assertEquals(note, NarrowPassSitePower.noTravelConsent(
          PowerSourceRef.Site(NarrowPassSite.id)))
        assertEquals(asks, Vector(ConsentAsk(p2, Vector(passPower))))
      case other => fail(s"expected an askable option, got $other")

  test("a Pass the bandits hold blocks with its ordinary reason: bandits " +
      "never consent"):
    assertNotedBy(dunesOffered(board()), "no-travel")

  test("a Pass the traveller rules blocks nothing"):
    assert(dunesOffered(on(board())(_.warbandsAt(pass, actor, 1))).available)

  test("Forest Paths drops the Pass before anyone is asked"):
    val ready = on(ruled)(_.adviser(actor, CatalogNames.denizen("Forest Paths"))
      .favor(actor, 1).denizen(CatalogNames.denizen("Errand Boy"),
        at = provincePlains))
    assert(dunesOffered(ready, Vector(ForestPaths.id)).available)

  test("a block nobody can waive beside the Pass leaves the destination " +
      "unavailable, not askable"):
    dunesOffered(ruled, extra = Vector(DunesBarrier)) match
      case DecisionOption.Unavailable(_, _: OptionReason.Noted) => ()
      case other => fail(s"expected a plain unavailable option, got $other")

  test("a grant remembered this turn opens the destination, and the Travel " +
      "goes there"):
    val granted = remembering(ruled, ConsentAnswer.Granted,
      ConsentTurn.of(ruled))
    assert(dunesOffered(granted).available)
    val result = Outcomes.ready(accepted(travel(granted,
      provincePlains)).state)
    assertEquals(Look(result).player(actor).pawnSite, Some(provincePlains))

  test("a refusal remembered this turn leaves the ordinary reason"):
    val refused = remembering(ruled, ConsentAnswer.Refused,
      ConsentTurn.of(ruled))
    assertNotedBy(dunesOffered(refused), "no-travel")
    assertEquals(rejected(travel(refused, provincePlains)),
      destinationNotOffered)

  test("a grant from another turn counts for nothing"):
    val stale = remembering(ruled, ConsentAnswer.Granted,
      ConsentTurn(ruled.game.current.tracks.round, p2))
    assert(askable(dunesOffered(stale)))

  test("the asks of an askable destination, for its owner only"):
    val started = accepted(rules.startWalker(Ready(ruled), ActionRef.Travel,
      actor, Vector.empty)).state
    assertEquals(rules.consentAsks(started, actor, destination, dunes),
      Right(Vector(ConsentAsk(p2, Vector(passPower)))))
    val coastRef = DecisionOptionRef.Site(coast)
    assertEquals(rules.consentAsks(started, actor, destination, coastRef),
      Left(OathViolation.ConsentUnavailable(
        s"${coastRef.wireId} is not an option to ask consent for")))
    assertEquals(rules.consentAsks(started, p2, destination, dunes),
      Left(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))
    assertEquals(rules.consentAsks(started, actor, "travel.elsewhere", dunes),
      Left(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.NarrowPassConsentSuite"`
Expected: compilation FAIL, `Askable is not a member of object OptionReason`.

- [ ] **Step 3: Add the reason**

In `src/main/scala/oathdigital/model/Decisions.scala`, inside `object OptionReason`:

```scala
  /** Blocked only by waivable powers whose rulers may be asked (consent
    * design, ruling 8). `power` and `note` are the first such power and its
    * consent-worded reason; `asks` are the granters with their powers. */
  final case class Askable(power: PowerId, note: PowerNote,
      asks: Vector[ConsentAsk]) extends OptionReason
```

In `WalkerDecisionProjector.reasonText`, add the arm the compiler now asks for (Task 7 projects the state itself):

```scala
    case OptionReason.Askable(power, note, _) =>
      reasons.text(power, note, ready, viewer).getOrElse("Not on offer now")
```

- [ ] **Step 4: The ledger check and the askable look-ahead**

In `WalkerPowerGather.scala`:

1. Add `ConsentAnswer, ConsentAsk, ConsentKey, ConsentTurn` to the `oathdigital.model` import list.
2. In `attributed`, drop a granted breach:

```scala
    val rejected = windows.flatMap:
      case (window, path, operation) =>
        val ctx = ctxFor(window, path, operation)
        ContributionCollector.gather(window, powers.windowIndex, ctx).restrictions
          .flatMap { case (powerId, restriction) =>
            val at = ctx(byId(powerId))
            restriction.fn(at, tree).filterNot(_ => waived(powerId,
              restriction, at)).map(Attributed(powerId, at, restriction, _))
          }
```

3. Add, after `attributed`:

```scala
  /** Whether a remembered grant drops `power`'s breach (consent design, "One
    * ledger check"): the restriction is waivable, and a decision of this
    * walk was answered with an option its answerer was granted this turn.
    * Every caller of the tree check passes through here, so the walk, the
    * search and the previews agree. */
  private def waived(power: PowerId, restriction: Restriction,
      ctx: PowerCtx): Boolean =
    restriction.waiver.nonEmpty && {
      val turn = ConsentTurn.of(ctx.state)
      ctx.answered.exists:
        case Answered(decisionId, DecisionAnswer.ChooseOneAnswer(target),
            by) =>
          ctx.state.consent.remembered(ConsentKey(power, by, decisionId,
            target), turn).contains(ConsentAnswer.Granted)
        case _ => false
    }
```

4. Replace `lookAheadNoted` with:

```scala
  /** Why each option `narrowed` no longer offers is hidden, read from the
    * restrictions whose violation answering it would add. It is askable
    * when every one of them is waivable by a player other than the
    * decision's owner and nothing on the option was refused this turn
    * (consent design, ruling 8); otherwise it is the first one's note. Empty
    * without a power that restricts, since only a restriction can explain a
    * hidden option.
    */
  def lookAheadNoted(root: Operation, decide: Decide, narrowed: Decide,
      state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef])
      : Vector[(DecisionOptionRef, OptionReason)] =
    val offered = optionRefs(narrowed).toSet
    val hidden = optionRefs(decide).filterNot(offered)
    if hidden.isEmpty || !powers.hasRestrictions then Vector.empty
    else
      val quiet = powers.quiet
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def found(answers: Vector[Answered]): Vector[Attributed] =
        attributed(root, quiet, seen, activePlayer, answers)._1
      val baseline = found(answered).map(_.violation).toSet
      hidden.flatMap { ref =>
        val answer = decide.query match
          case _: DecisionQuery.ChooseMany =>
            DecisionAnswer.ChooseManyAnswer(Vector(ref))
          case _ => DecisionAnswer.ChooseOneAnswer(ref)
        val fresh = found(answered :+ Answered(decide.decisionId, answer,
          decide.owner)).filterNot(breach => baseline(breach.violation))
        askable(decide, ref, fresh, seen).orElse(fresh.headOption.flatMap(
          breach => breach.restriction.note(breach.ctx, ref).map(note =>
            OptionReason.Noted(breach.power, note))))
          .map(ref -> _)
      }

  /** `ref`'s consent ask, when every breach answering it adds is waivable by
    * a player other than the decision's owner, and none of their powers'
    * answers on it was refused this turn. Only a choose-one: a grant answers
    * the decision with `ref` alone (plan decision 8). */
  private def askable(decide: Decide, ref: DecisionOptionRef,
      fresh: Vector[Attributed], state: ReadyGame): Option[OptionReason] =
    val requester = decide.owner
    val turn = ConsentTurn.of(state)
    val waivers = fresh.map(breach => breach.restriction.waiver.flatMap(
      waiver => waiver.granter(state).filter(_ != requester)
        .map(granter => (breach, waiver, granter))))
    val choosesOne = decide.query.isInstanceOf[DecisionQuery.ChooseOne]
    if !choosesOne || fresh.isEmpty || waivers.exists(_.isEmpty) then None
    else
      val all = waivers.flatten
      val refused = all.exists((breach, _, _) => state.consent.remembered(
        ConsentKey(breach.power, requester, decide.decisionId, ref), turn)
        .contains(ConsentAnswer.Refused))
      Option.unless(refused):
        val (first, waiver, _) = all.head
        val asks = all.map(_._3).distinct.map(granter => ConsentAsk(granter,
          all.collect { case (breach, _, `granter`) => breach.power }.distinct))
        OptionReason.Askable(first.power, waiver.note(first.ctx, ref), asks)
```

In `ProcedureWalker.narrowAt`, the look-ahead now gives the reasons themselves:

```scala
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path)).map(_.map {
      cut =>
        val reasons = WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
          ctx.state, ctx.activePlayer, ctx.powers, ctx.answered, ctx.procedure)
          .toMap
        WalkerSearch.explained(decide, cut, reasons)
    })
```

Delete any import the compiler then reports unused in `ProcedureWalker.scala`.

- [ ] **Step 5: Expose the asks to the application**

In `OathRulesWalker.scala`, after `resolveWalker`, add:

```scala
  /** The asks a consent request for `target` sends (consent design,
    * "Commands"). `target` must be an askable option of the parked
    * `decisionId`, which `requester` owns; the look-ahead decided that, so
    * nothing here re-derives who may consent. */
  def consentAsks(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef): Either[OathViolation, Vector[ConsentAsk]] =
    walkerResumeContext(state).flatMap:
      case (ready, _, tree, pending, powers, _, _) =>
        walkerCall(Right(ProcedureWalker.parkedDecide(ready, tree, pending,
          powers))).flatMap(decide => decide
          .filter(found => found.decisionId == decisionId &&
            found.owner == requester)
          .flatMap(found => PricedOptions.options(found).collectFirst {
            case DecisionOption.Unavailable(option,
                OptionReason.Askable(_, _, asks)) if option.ref == target =>
              asks
          })
          .toRight(ConsentUnavailable(
            s"${target.wireId} is not an option to ask consent for")))
```

Import `oathdigital.gameplay.walker.PricedOptions` (beside the file's other walker imports). `ConsentUnavailable` comes from the file's `oathdigital.model.OathViolation._` import; if the file names violations qualified instead, write `OathViolation.ConsentUnavailable`.

- [ ] **Step 6: Run the walker tests and the Travel suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.NarrowPassConsentSuite oathdigital.gameplay.powers.travel.ForestPathsSuite oathdigital.gameplay.TravelSitePowersSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.application.TravelDestinationRowsSuite"`
Expected: PASS. Every pre-existing Pass test uses the fixture's bandit-held Pass (plan decision 14), so its reason is still `no-travel`.

- [ ] **Step 7: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/OathRulesWalker.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassConsentSuite.scala
git commit -m "feat(consent): mark a destination askable when only waivable powers block it, and honour a grant"
```

---

### Task 5: Requests and answers through the service

**Files:**
- Modify: `src/main/scala/oathdigital/application/ConsentDesk.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (`routed`, `applyCommand`)
- Modify: `src/test/scala/oathdigital/application/ConsentFixture.scala` (add `requestDunes`)
- Modify: `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`
- Test: `src/test/scala/oathdigital/application/ConsentRequestServiceSuite.scala`

**Interfaces:**
- Consumes: Task 3's desk, routing and fixture. Task 4's `OathRules.consentAsks`.
- Produces:
  - `GameCommand.RequestConsent(requester: PlayerId, decisionId: String, target: DecisionOptionRef)` and `GameCommand.AnswerConsent(granter: PlayerId, choice: ConsentChoice)`, both `ConsentCommand`s.
  - `ConsentDesk.request`, `answer` and `withdraw`.
  - A grant that completes a request appends `CommandAccepted(requester)` and the requester's answer to the parked decision in the same append. The reported events leave the opening out.
  - While a request is pending, the service accepts only `AnswerConsent` from an asked granter, `Undo` from the requester (it withdraws) and `SetConsentPreference`. Everything else is refused with `ConsentPending`, except an answer from a player who was not asked, which is refused with `ConsentUnavailable`.

- [ ] **Step 1: Write the failing service tests**

Add `requestDunes` to `ConsentFixture` (its text is in Task 3, Step 1).

Create `src/test/scala/oathdigital/application/ConsentRequestServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, ConsentAnswered,
  ConsentPreferenceSet, ConsentRequestWithdrawn, ConsentRequested}
import oathdigital.testkit.{Look, Outcomes, PlacementPicks}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking for consent and answering through the service (consent design,
  * user story, rulings 10 to 14, "Commands"). */
class ConsentRequestServiceSuite extends munit.FunSuite:
  import ConsentFixture._
  private val pass = NarrowPassSite.pass
  private val request = ConsentRequest(p1,
    TravelProcedure.destinationDecisionId, dunes,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  /** p1 starts a Travel and asks about Dunes. */
  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def pawnOfP1(at: GameAccepted)(using munit.Location)
      : Option[SiteId] =
    Look(Outcomes.ready(at.state)).player(p1).pawnSite

  private def parked(at: GameAccepted)(using munit.Location): Boolean =
    Outcomes.ready(at.state).game.current.walkerPending.nonEmpty

  private def dunesOffered(at: GameAccepted)(using munit.Location)
      : DecisionOption =
    present(PlacementPicks.parkedOptions(at.state, powers)
      .find(_.ref == dunes), "Dunes among the destinations")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  private def refusedPlainly(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      note.key == "no-travel"
    case _ => false

  test("a request waits on the Pass's ruler, and Yes travels there for the " +
      "requester, as the requester's own undoable step"):
    val (service, repository) = ConsentFixture.service()
    val asked = asking(service)
    assertEquals(asked.events, Vector(ConsentRequested(request)))
    assertEquals(consent(asked).pending, Some(request))
    val yes = answering(service, asked, ConsentChoice.Yes)
    assertEquals(yes.events.head, ConsentAnswered(p2, granted = true))
    assert(!yes.events.exists(_.isInstanceOf[CommandAccepted]))
    assertEquals(pawnOfP1(yes), Some(TravelFixture.provincePlains))
    // The move is p1's batch: it opens right after the answer.
    assertEquals(yes.undo.map(step => (step.issuer, step.from)),
      Some((p1, asked.nextSequence + 1L)))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((yes.state, yes.undo))))

  test("No leaves Dunes greyed out with its ordinary reason, and it cannot " +
      "be asked about again"):
    val (service, _) = ConsentFixture.service()
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    assert(parked(no))
    assert(refusedPlainly(dunesOffered(no)))
    assertEquals(service.handle(gameId, no.nextSequence, requestDunes),
      rejected(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))

  test("Always Allow grants and sets the ruler's preference"):
    val (service, _) = ConsentFixture.service()
    val always = answering(service, asking(service), ConsentChoice.Always)
    assertEquals(always.events.take(2), Vector(
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always),
      ConsentAnswered(p2, granted = true)))
    assertEquals(consent(always).preference(p2, pass, p1),
      Some(ConsentPreference.Always))
    assertEquals(pawnOfP1(always), Some(TravelFixture.provincePlains))

  test("Never Allow refuses and sets the ruler's preference"):
    val (service, _) = ConsentFixture.service()
    val never = answering(service, asking(service), ConsentChoice.Never)
    assertEquals(never.events, Vector(
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Never),
      ConsentAnswered(p2, granted = false)))
    assert(parked(never))

  test("a ruler's Always answers a request at once"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(service, set.nextSequence)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(asked.events.take(2), Vector(ConsentRequested(request),
      ConsentAnswered(p2, granted = true)))
    assertEquals(pawnOfP1(asked), Some(TravelFixture.provincePlains))

  test("a ruler's Never refuses a request at once"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Never)))
    val started = startTravel(service, set.nextSequence)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(asked.events, Vector(ConsentRequested(request),
      ConsentAnswered(p2, granted = false)))
    assert(parked(asked))

  test("after a grant the requester may undo the move, and pick Dunes " +
      "again without asking"):
    val (service, _) = ConsentFixture.service()
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    val undone = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.Undo(p1)))
    assert(parked(undone))
    assert(dunesOffered(undone).available)
    val picked = accepted(service.handle(gameId, undone.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1,
        TravelFixture.provincePlains)))
    assertEquals(pawnOfP1(picked), Some(TravelFixture.provincePlains))
    assert(!picked.events.exists(_.isInstanceOf[ConsentEvent]))

  test("after a refusal the requester may undo the Travel, and Dunes stays " +
      "refused this turn"):
    val (service, _) = ConsentFixture.service()
    val no = answering(service, asking(service), ConsentChoice.No)
    val undone = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.Undo(p1)))
    assert(!parked(undone))
    val again = startTravel(service, undone.nextSequence)
    assert(refusedPlainly(dunesOffered(again)))

  test("Undo while the request waits withdraws it, and nothing is " +
      "remembered"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    val withdrawn = accepted(service.handle(gameId, asked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(withdrawn.events, Vector(ConsentRequestWithdrawn(p1)))
    assertEquals(consent(withdrawn), ConsentState())
    assert(parked(withdrawn))
    assert(askable(dunesOffered(withdrawn)))

  test("while a request waits, only its answers, its withdrawal and " +
      "preferences run"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    val at = asked.nextSequence
    assertEquals(service.handle(gameId, at, ParkedServiceFixture
      .pickTravelDestination(p1, TravelFixture.coast)),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, requestDunes),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, GameCommand.AnswerConsent(p3,
      ConsentChoice.Yes)), rejected(OathViolation.ConsentUnavailable(
      "p3 has not been asked for consent")))
    assertEquals(service.handle(gameId, at, GameCommand.Undo(p3)),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, GameCommand.Redo(p1)),
      rejected(OathViolation.ConsentPending))
    // A preference changed while prompted leaves the prompt open (ruling 19).
    val set = accepted(service.handle(gameId, at,
      setPreference(ConsentSetting.Always)))
    assertEquals(consent(set).pending, Some(request))

  test("with no request waiting, an answer is refused"):
    val (service, _) = ConsentFixture.service()
    val started = startTravel(service)
    assertEquals(service.handle(gameId, started.nextSequence,
      GameCommand.AnswerConsent(p2, ConsentChoice.Yes)),
      rejected(OathViolation.ConsentUnavailable(
        "no consent request is waiting")))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.ConsentRequestServiceSuite"`
Expected: compilation FAIL, `RequestConsent is not a member of object GameCommand`.

- [ ] **Step 3: Add the commands**

In `GameCommands.scala`, beside `SetConsentPreference`:

```scala
  /** `requester` asks the rulers of the powers blocking `target` at the
    * parked `decisionId` for consent to choose it. */
  final case class RequestConsent(requester: PlayerId, decisionId: String,
      target: DecisionOptionRef) extends ConsentCommand
  /** `granter` answers the request waiting on them. */
  final case class AnswerConsent(granter: PlayerId, choice: ConsentChoice)
      extends ConsentCommand
```

- [ ] **Step 4: Requests, answers and withdrawals in the desk**

In `ConsentDesk.scala`, widen the event import to `{CommandAccepted, ConsentAnswered, ConsentPreferenceSet, ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}`, and add:

```scala
  /** Asks the rulers of the waivable powers blocking `target` (consent
    * design, "Commands"), answering at once for each granter whose
    * preferences decide (ruling 10). */
  def request(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef): Result = for
    ready <- playing(state)
    _ <- Either.cond(ready.consent.pending.isEmpty, (),
      OathViolation.ConsentPending)
    asks <- rules.consentAsks(state, requester, decisionId, target)
    request = ConsentRequest(requester, decisionId, target, asks)
    events <- settled(state,
      ConsentRequested(request) +: automatic(ready.consent, request))
  yield events

  /** `granter`'s answer. Never Allow and Always Allow first set their
    * preference for the requester on every power of their ask. */
  def answer(state: OathState, granter: PlayerId, choice: ConsentChoice)
      : Result = for
    ready <- playing(state)
    request <- ready.consent.pending.toRight(OathViolation.ConsentUnavailable(
      "no consent request is waiting"))
    ask <- request.open.find(_.granter == granter).toRight(
      OathViolation.ConsentUnavailable(
        s"${granter.value} has not been asked for consent"))
    preferences = choice.setting.toVector.flatMap(setting => ask.powers.map(
      ConsentPreferenceSet(granter, _, request.requester, setting)))
    events <- settled(state,
      preferences :+ ConsentAnswered(granter, choice.granted))
  yield events

  /** The requester's Undo while their request waits (ruling 14). Anyone
    * else's Undo waits for the answer. */
  def withdraw(state: OathState, requester: PlayerId): Result =
    pending(state) match
      case Some(request) if request.requester == requester =>
        Right(Vector(ConsentRequestWithdrawn(requester)))
      case _ => Left(OathViolation.ConsentPending)

  /** The answers the granters' preferences give, in ask order (ruling 10):
    * Never on any of a granter's powers refuses, Always on all of them
    * grants. The first refusal settles the request, so nothing follows it.
    */
  private def automatic(consent: ConsentState, request: ConsentRequest)
      : Vector[OathEvent] =
    val answers = request.asks.flatMap { ask =>
      val set = ask.powers.map(consent.preference(ask.granter, _,
        request.requester))
      if set.contains(Some(ConsentPreference.Never)) then
        Some(ConsentAnswered(ask.granter, granted = false))
      else if set.forall(_.contains(ConsentPreference.Always)) then
        Some(ConsentAnswered(ask.granter, granted = true))
      else None
    }
    answers.indexWhere(!_.granted) match
      case -1 => answers
      case refusal => answers.take(refusal + 1)

  /** `events`, followed, when their last answer completes the request with
    * every ask granted, by the requester's answer to the parked decision.
    * That answer is the requester's command batch, though the granter's
    * command produced it, so the requester can undo the move (consent
    * design, "Settling"). */
  private def settled(state: OathState, events: Vector[OathEvent]): Result =
    folded(state, events).flatMap { after =>
      val request = events.collectFirst { case ConsentRequested(opened) =>
        opened }.orElse(pending(state))
      val granted = pending(after).isEmpty && (events.lastOption match
        case Some(ConsentAnswered(_, true)) => true
        case _ => false)
      request.filter(_ => granted) match
        case Some(done) => rules.resolveWalker(after, done.requester,
            done.decisionId, DecisionAnswer.ChooseOneAnswer(done.target))
          .map(answered =>
            events ++ (CommandAccepted(done.requester) +: answered.events))
        case None => Right(events)
    }

  private def folded(state: OathState, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (now, event) => now.flatMap(rules.evolve(_, event)))
```

If Task 3 added a `walker` def to keep `rules` read, delete it now.

- [ ] **Step 5: Route them, withdraw on Undo, and hold everything else**

In `GameApplicationService.routed`, replace the Undo and Redo arms and add two consent arms:

```scala
      // The requester's Undo withdraws an unanswered request (ruling 14).
      case GameCommand.Undo(actor) => consent.pending(journal.state) match
        case Some(_) => consentBatch(gameId, journal,
          consent.withdraw(journal.state, actor), expected, nextSequence)
        case None => takeBack(gameId, journal, actor, expected, nextSequence)
      case GameCommand.Redo(actor) =>
        if consent.pending(journal.state).nonEmpty then
          Left(CommandRejected(OathViolation.ConsentPending))
        else restore(gameId, journal, actor, expected, nextSequence)
      case GameCommand.RequestConsent(requester, decisionId, target) =>
        consentBatch(gameId, journal, consent.request(journal.state,
          requester, decisionId, target), expected, nextSequence)
      case GameCommand.AnswerConsent(granter, choice) =>
        consentBatch(gameId, journal, consent.answer(journal.state, granter,
          choice), expected, nextSequence)
```

In `applyCommand`, make this the first case:

```scala
      // A consent request waits on its granters; nothing the rules run may
      // move past it (consent design, "Commands"). `routed` sends what may
      // run to the consent desk instead.
      case OathState.Ready(ready) if ready.consent.pending.nonEmpty =>
        Left(OathViolation.ConsentPending)
```

- [ ] **Step 6: Keep the command enumeration complete**

In `PendingWalkerInvariantSuite.everyCommand`, append (both are refused over every park: no decision is `not-parked` and no request waits):

```scala
    GameCommand.RequestConsent(actor, "not-parked",
      DecisionOptionRef.Button("go")),
    GameCommand.AnswerConsent(actor, ConsentChoice.Yes),
```

- [ ] **Step 7: Run the consent suites and the invariants**

Run: `./sbtw "testOnly oathdigital.application.ConsentRequestServiceSuite oathdigital.application.ConsentPreferenceServiceSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.application.UndoServiceSuite"`
Expected: PASS.

- [ ] **Step 8: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/application/ConsentDesk.scala src/main/scala/oathdigital/application/GameCommands.scala src/main/scala/oathdigital/application/GameApplicationService.scala src/test/scala/oathdigital/application/ConsentFixture.scala src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala src/test/scala/oathdigital/application/ConsentRequestServiceSuite.scala
git commit -m "feat(consent): ask a ruler for consent, answer it, and withdraw it with Undo"
```

---

### Task 6: The consent intents on the wire

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala`
- Modify: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala:8-44`
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala:13-39`
- Modify: `src/main/scala/oathdigital/application/Authorization.scala:51-73`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala:28-40`
- Modify: `src/test/scala/oathdigital/server/CommandRejectionMessageSuite.scala`
- Test: `src/test/scala/oathdigital/application/ConsentIntentMapperSuite.scala`

**Interfaces:**
- Consumes: Tasks 3 and 5's commands, `ConsentChoice.fromKey`, `ConsentSetting.fromKey`.
- Produces (shared, `GameIntent`):
  - `RequestConsent(decisionId: String, target: WalkerStartArgWire)`
  - `AnswerConsent(choice: String)`
  - `SetConsentPreference(powerId: String, requesterId: String, setting: String)`

  The frontend tasks send these. `AuthorizedPlayer.requestConsent`, `answerConsent` and `setConsentPreference` bind the sender as requester or granter.

- [ ] **Step 1: Write the failing tests**

In `CommandProtocolSuite.examples`, append:

```scala
    RequestConsent("travel.destination", WalkerStartArgWire("site",
      "site:deep-woods")),
    AnswerConsent("always"),
    SetConsentPreference("site.narrow-pass.pass", "blue", "case-by-case"),
```

Create `src/test/scala/oathdigital/application/ConsentIntentMapperSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}

/** The consent intents bind their sender as requester or granter. */
class ConsentIntentMapperSuite extends munit.FunSuite:
  private val red = PlayerId("red")
  private def failure(path: String, message: String) =
    Left(GameIntentMappingFailure(path, message))

  test("a consent request binds its sender as the requester"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.RequestConsent(
      "travel.destination", WalkerStartArgWire("site", "site:deep-woods"))),
      Right(GameCommand.RequestConsent(red, "travel.destination",
        DecisionOptionRef.Site(SiteId("site:deep-woods")))))

  test("an answer binds its sender as the granter"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.AnswerConsent("never")),
      Right(GameCommand.AnswerConsent(red, ConsentChoice.Never)))
    assertEquals(GameIntentMapper.bind(red, GameIntent.AnswerConsent("maybe")),
      failure("$.intent.choice", "invalid consent choice 'maybe'"))

  test("a preference binds its sender as the granter"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "case-by-case")),
      Right(GameCommand.SetConsentPreference(red,
        PowerId("site.narrow-pass.pass"), PlayerId("blue"),
        ConsentSetting.CaseByCase)))
    assertEquals(GameIntentMapper.bind(red, GameIntent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "sometimes")),
      failure("$.intent.setting", "invalid consent setting 'sometimes'"))
```

In `CommandRejectionMessageSuite`, add to the first test:

```scala
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentPending),
      "Waiting for an answer to a consent request")
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentUnavailable(
      "the game is over")), "the game is over")
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite" "testOnly oathdigital.application.ConsentIntentMapperSuite oathdigital.server.CommandRejectionMessageSuite"`
Expected: compilation FAIL, `RequestConsent is not a member of object GameIntent`.

- [ ] **Step 3: Add the intents and their codec**

In `CommandIntents.scala`, inside `object GameIntent`, after `ResolveWalker`:

```scala
  /** Asks the rulers of the powers blocking `target` at the parked
    * `decisionId` for consent to choose it (consent design). */
  final case class RequestConsent(decisionId: String,
      target: WalkerStartArgWire) extends GameIntent
  /** Answers the consent request waiting on the sender: `never`, `no`,
    * `yes` or `always`. */
  final case class AnswerConsent(choice: String) extends GameIntent
  /** Sets the sender's standing answer for `requesterId` on `powerId`:
    * `never`, `case-by-case` or `always`. Not `playerId`: the codec refuses
    * a field that could name the sender. */
  final case class SetConsentPreference(powerId: String, requesterId: String,
      setting: String) extends GameIntent
```

In `CommandIntentCodec.encode`, after the `ResolveWalker` arm:

```scala
    case RequestConsent(id, target) => tagged("requestConsent",
      "decisionId" -> id,
      "target" -> CommandNestedCodecs.encodeStartArgWire(target))
    case AnswerConsent(choice) => tagged("answerConsent", "choice" -> choice)
    case SetConsentPreference(power, requester, setting) =>
      tagged("setConsentPreference", "powerId" -> power,
        "requesterId" -> requester, "setting" -> setting)
```

In `CommandIntentDecoders.decode`, before the `case other` arm:

```scala
    case "requestConsent" =>
      for
        _ <- exact(value, Set("type", "decisionId", "target"), path)
        id <- string(value, "decisionId", path)
        target <- field(value, "target", path).flatMap(raw =>
          CommandNestedCodecs.decodeStartArgsWire(ujson.Arr(raw),
            s"$path.target"))
          .flatMap:
            case Vector(one) => Right(one)
            case _ => Left(InvalidValue(s"$path.target",
              "expected one consent target"))
      yield RequestConsent(id, target)
    case "answerConsent" => one(value, path, "choice")(AnswerConsent.apply)
    case "setConsentPreference" =>
      for
        _ <- exact(value, Set("type", "powerId", "requesterId", "setting"),
          path)
        power <- string(value, "powerId", path)
        requester <- string(value, "requesterId", path)
        setting <- string(value, "setting", path)
      yield SetConsentPreference(power, requester, setting)
```

- [ ] **Step 4: Bind them on the server, and word the rejections**

In `Authorization.scala`, inside `AuthorizedPlayer`:

```scala
  def requestConsent(decisionId: String, target: DecisionOptionRef)
      : GameCommand =
    GameCommand.RequestConsent(access.playerId, decisionId, target)
  def answerConsent(choice: ConsentChoice): GameCommand =
    GameCommand.AnswerConsent(access.playerId, choice)
  def setConsentPreference(power: PowerId, requester: PlayerId,
      setting: ConsentSetting): GameCommand =
    GameCommand.SetConsentPreference(access.playerId, power, requester,
      setting)
```

In `GameIntentMapper.bind`, after the `ResolveWalker` arm:

```scala
      case Intent.RequestConsent(id, target) =>
        optionRef(target.optionKind, target.optionId, "$.intent.target")
          .map(actor.requestConsent(id, _))
      case Intent.AnswerConsent(value) => ConsentChoice.fromKey(value)
        .toRight(GameIntentMappingFailure("$.intent.choice",
          s"invalid consent choice '$value'")).map(actor.answerConsent)
      case Intent.SetConsentPreference(value, requester, setting) =>
        for
          power <- PowerId.fromValue(value).toRight(GameIntentMappingFailure(
            "$.intent.powerId", s"invalid power id '$value'"))
          chosen <- ConsentSetting.fromKey(setting).toRight(
            GameIntentMappingFailure("$.intent.setting",
              s"invalid consent setting '$setting'"))
        yield actor.setConsentPreference(power, PlayerId(requester), chosen)
```

In `CommandRejectionMessage.text` (`GameRoutes.scala`), after the `FollowUpPending` arm:

```scala
      case oathdigital.model.OathViolation.ConsentPending =>
        "Waiting for an answer to a consent request"
      case oathdigital.model.OathViolation.ConsentUnavailable(reason) => reason
```

- [ ] **Step 5: Run the tests**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite" "testOnly oathdigital.application.ConsentIntentMapperSuite oathdigital.server.CommandRejectionMessageSuite oathdigital.application.GameIntentMapperUndoSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/CommandIntents.scala shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala src/main/scala/oathdigital/application/GameIntentMapper.scala src/main/scala/oathdigital/application/Authorization.scala src/main/scala/oathdigital/server/GameRoutes.scala src/test/scala/oathdigital/server/CommandRejectionMessageSuite.scala src/test/scala/oathdigital/application/ConsentIntentMapperSuite.scala
git commit -m "feat(consent): carry consent requests, answers and preferences on the wire"
```

---

### Task 7: What each viewer is shown

**Files:**
- Create: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala`
- Create: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:146-157` (`DecisionOptionProjection`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:312-338`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala:3-38`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala:12-22, 74-78, 136-147`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala:72` and `WorldProjectionCodec.scala:47-54`
- Create: `src/main/scala/oathdigital/application/ConsentProjector.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:333-354` (`optionProjection`)
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:13-53`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala:403-406` (`sitePower`)
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Test: `src/test/scala/oathdigital/application/ConsentProjectionSuite.scala`

**Interfaces:**
- Consumes: Tasks 3 to 5's service and fixture. Task 4's `OptionReason.Askable`. Task 2's `ConsentPowers`.
- Produces (shared DTOs, package `oathdigital.protocol.projection`):
  - `ConsentAskProjection(granterPlayerIds: Vector[String], powers: Vector[String])`
  - `ConsentWaitingProjection(granterPlayerIds: Vector[String])`
  - `ConsentPromptProjection(requesterPlayerId: String, powers: Vector[String], target: String)`
  - `ConsentPreferencesProjection(powerId: String, power: String, rows: Vector[ConsentPreferenceRowProjection])`
  - `ConsentPreferenceRowProjection(playerId: String, setting: String)`
  - `DecisionOptionProjection.consent: Option[ConsentAskProjection] = None`
  - `GameProjection` gains `consentWaiting: Option[ConsentWaitingProjection] = None`, `consentPrompt: Option[ConsentPromptProjection] = None` and `consentPreferences: Vector[ConsentPreferencesProjection] = Vector.empty`
  - `SitePowerProjection.id: String = ""`
- Produces (application): `ConsentNames.power(id: PowerId): String` and `ConsentProjector.applied(context, projection)`. `ConsentProjector.withdrawNote = "Withdraw your consent request"`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/ConsentProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.travel.NarrowPassSite
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection._
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What each viewer is shown of consent (consent design, "Projection" and
  * "Testing", hidden information). */
class ConsentProjectionSuite extends munit.FunSuite:
  import ConsentFixture._
  private val projector = new GameProjector(catalog)
  private val askReason = "Narrow Pass: Cannot travel to other sites in " +
    "the region without ruler's consent."

  private def view(at: GameAccepted, viewer: PlayerId): GameProjection =
    projector.project(gameId, at.loaded, viewer)

  private def dunesRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(view(at, p1).walkerDecision.flatMap(_.query).toVector
      .flatMap(_.offeredOptions).find(_.id == dunes.wireId), "the Dunes row")

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  test("a destination its ruler may be asked about is askable, with the " +
      "consent reason and whom to ask"):
    val (service, _) = ConsentFixture.service()
    val row = dunesRow(startTravel(service))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some(askReason))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Narrow Pass"))))
    assert(!row.choosable)

  test("after a refusal it is unavailable, with the ordinary reason"):
    val (service, _) = ConsentFixture.service()
    val row = dunesRow(answering(service, asking(service), ConsentChoice.No))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some(
      "Narrow Pass: Cannot travel to other sites in the region."))
    assertEquals(row.consent, None)

  test("while a request waits, the requester sees the wait and an Undo " +
      "that withdraws, and no decision"):
    val (service, _) = ConsentFixture.service()
    val shown = view(asking(service), p1)
    assertEquals(shown.walkerDecision, None)
    assertEquals(shown.consentWaiting,
      Some(ConsentWaitingProjection(Vector("p2"))))
    assertEquals(shown.consentPrompt, None)
    assertEquals(shown.undo, Some(StepOfferProjection(
      "Withdraw your consent request")))
    assertEquals(shown.redo, None)

  test("the asked ruler sees the prompt; a third player and the public see " +
      "neither"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    assertEquals(view(asked, p2).consentPrompt, Some(ConsentPromptProjection(
      "p1", Vector("Narrow Pass"), "Dunes")))
    assertEquals(view(asked, p2).consentWaiting, None)
    Vector(view(asked, p3), projector.projectPublic(gameId, asked.loaded))
      .foreach { shown =>
        assertEquals(shown.consentPrompt, None)
        assertEquals(shown.consentWaiting, None)
      }

  test("the Pass's ruler alone sees their preferences, one row per other " +
      "player"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    assertEquals(view(set, p2).consentPreferences, Vector(
      ConsentPreferencesProjection("site.narrow-pass.pass", "Narrow Pass",
        Vector(ConsentPreferenceRowProjection("p1", "always"),
          ConsentPreferenceRowProjection("p3", "case-by-case")))))
    assertEquals(view(set, p1).consentPreferences, Vector.empty)
    assertEquals(projector.projectPublic(gameId, set.loaded)
      .consentPreferences, Vector.empty)

  test("a site power carries its power id"):
    val (service, _) = ConsentFixture.service()
    val shown = projector.projectPublic(gameId, startTravel(service).loaded)
    assertEquals(shown.world.flatMap(_.sites)
      .find(_.siteId == NarrowPassSite.id.value).map(_.powers.map(_.id)),
      Some(Vector("site.narrow-pass.pass")))

  test("the requester's view cannot tell an automatic grant from a manual " +
      "one"):
    val (manualService, _) = ConsentFixture.service()
    val manual = answering(manualService, asking(manualService),
      ConsentChoice.Yes)
    val (autoService, _) = ConsentFixture.service()
    val set = accepted(autoService.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(autoService, set.nextSequence)
    val automatic = accepted(autoService.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(view(automatic, p1).copy(nextSequence = 0L),
      view(manual, p1).copy(nextSequence = 0L))
```

In `ProjectionProtocolSuite`, after the follow-up tests, add:

```scala
  test("the consent parts of a projection round-trip"):
    assertRoundTrips(projection.copy(
      consentWaiting = Some(ConsentWaitingProjection(Vector("p2"))),
      consentPrompt = Some(ConsentPromptProjection("p1",
        Vector("Narrow Pass"), "Dunes")),
      consentPreferences = Vector(ConsentPreferencesProjection(
        "site.narrow-pass.pass", "Narrow Pass",
        Vector(ConsentPreferenceRowProjection("p1", "always"))))))

  test("an askable option's consent ask and a site power's id round-trip"):
    assertRoundTrips(projection.copy(
      walkerDecision = Some(WalkerDecisionProjection("travel",
        "travel.destination", "decide", query = Some(
          DecisionQueryProjection.ChooseOne(Vector(DecisionOptionProjection(
            "site", "site:dunes", "Dunes", state = "askable",
            reason = Some("Narrow Pass: needs consent"),
            consent = Some(ConsentAskProjection(Vector("p2"),
              Vector("Narrow Pass"))))))))),
      world = projection.world.map(region => region.copy(sites =
        region.sites.map(site => site.copy(powers = Vector(
          SitePowerProjection("pass", "Pass", Some("text"),
            "site.narrow-pass.pass"))))))))

  test("a payload without consent parts decodes to none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    Vector("consentWaiting", "consentPrompt", "consentPreferences")
      .foreach(raw.obj.remove)
    assertEquals(GameProjectionCodec.decode(ujson.write(raw)).map(decoded =>
      (decoded.consentWaiting, decoded.consentPrompt,
        decoded.consentPreferences)), Right((None, None, Vector.empty)))
```

The suite's existing follow-up test shows how `raw` is read; if it reads the encoded projection differently, follow it.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.ConsentProjectionSuite" "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compilation FAIL, `Not found: ConsentAskProjection`.

- [ ] **Step 3: The DTOs and their codec**

Create `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala`:

```scala
package oathdigital.protocol.projection

/** Whom an askable option would ask, and about which powers, for the
  * client to word its confirmation (consent design, "Projection"). Player
  * ids: the client words their display names. */
final case class ConsentAskProjection(granterPlayerIds: Vector[String],
    powers: Vector[String])

/** The requester's own view of their waiting request: who has not yet
  * answered. */
final case class ConsentWaitingProjection(granterPlayerIds: Vector[String])

/** An asked granter's prompt: who asks, about which of the granter's own
  * powers, for which option. */
final case class ConsentPromptProjection(requesterPlayerId: String,
    powers: Vector[String], target: String)

/** A waivable power the viewer rules, with their setting for each other
  * player: `never`, `case-by-case` or `always`. Only ever the viewer's own
  * (ruling 16). */
final case class ConsentPreferencesProjection(powerId: String, power: String,
    rows: Vector[ConsentPreferenceRowProjection])
final case class ConsentPreferenceRowProjection(playerId: String,
    setting: String)
```

Create `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala`:

```scala
package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object ConsentProjectionCodec:
  def encodeAsk(value: ConsentAskProjection): ujson.Value = ujson.Obj(
    "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)),
    "powers" -> encoded(value.powers)(ujson.Str(_)))

  def decodeAsk(raw: ujson.Value, path: String)
      : Result[ConsentAskProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds", "powers"), path)
    granters <- strings(row, "granterPlayerIds", path)
    powers <- strings(row, "powers", path)
  yield ConsentAskProjection(granters, powers)

  def encodeWaiting(value: ConsentWaitingProjection): ujson.Value = ujson.Obj(
    "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)))

  def decodeWaiting(raw: ujson.Value, path: String)
      : Result[ConsentWaitingProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds"), path)
    granters <- strings(row, "granterPlayerIds", path)
  yield ConsentWaitingProjection(granters)

  def encodePrompt(value: ConsentPromptProjection): ujson.Value = ujson.Obj(
    "requesterPlayerId" -> value.requesterPlayerId,
    "powers" -> encoded(value.powers)(ujson.Str(_)),
    "target" -> value.target)

  def decodePrompt(raw: ujson.Value, path: String)
      : Result[ConsentPromptProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("requesterPlayerId", "powers", "target"), path)
    requester <- string(row, "requesterPlayerId", path)
    powers <- strings(row, "powers", path)
    target <- string(row, "target", path)
  yield ConsentPromptProjection(requester, powers, target)

  def encodePreferences(value: ConsentPreferencesProjection): ujson.Value =
    ujson.Obj("powerId" -> value.powerId, "power" -> value.power,
      "rows" -> encoded(value.rows)(row => ujson.Obj(
        "playerId" -> row.playerId, "setting" -> row.setting)))

  def decodePreferences(raw: ujson.Value, path: String)
      : Result[ConsentPreferencesProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("powerId", "power", "rows"), path)
    powerId <- string(row, "powerId", path)
    power <- string(row, "power", path)
    rowRaws <- array(row, "rows", path)
    rows <- traverse(rowRaws, s"$path.rows") { (entryRaw, child) => for
      entry <- obj(entryRaw, child)
      _ <- exact(entry, Set("playerId", "setting"), child)
      player <- string(entry, "playerId", child)
      setting <- string(entry, "setting", child)
    yield ConsentPreferenceRowProjection(player, setting) }
  yield ConsentPreferencesProjection(powerId, power, rows)
```

`DecisionOptionProjection` (`ActionProjectionDtos.scala`): add a last field `consent: Option[ConsentAskProjection] = None`, and extend the doc comment's state list with `askable` (shown, choosable only by asking `consent`'s granters, with `reason` saying why). `choosable` stays as it is: an askable option is not answered directly.

In `ActionProjectionCodec`:
- in `encodeOptionRow`, add `"consent" -> option(row.consent)(ConsentProjectionCodec.encodeAsk)`;
- in `decodeOptionRow`, add `"consent"` to the `exact` set, decode it with `consent <- optionalAbsent(row, "consent", child)(ConsentProjectionCodec.decodeAsk)`, and pass `consent` last to the constructor.

`GameProjection` (`GameProjectionDto.scala`): append after `followUp`:

```scala
    ,consentWaiting: Option[ConsentWaitingProjection] = None
    ,consentPrompt: Option[ConsentPromptProjection] = None
    ,consentPreferences: Vector[ConsentPreferencesProjection] = Vector.empty
```

In `GameProjectionCodec`:
- add `"consentWaiting", "consentPrompt", "consentPreferences"` to `Fields`;
- in `encodeValue`, after `"followUp" -> ...`:

```scala
    "consentWaiting" -> option(value.consentWaiting)(
      ConsentProjectionCodec.encodeWaiting),
    "consentPrompt" -> option(value.consentPrompt)(
      ConsentProjectionCodec.encodePrompt),
    "consentPreferences" -> encoded(value.consentPreferences)(
      ConsentProjectionCodec.encodePreferences))
```

- in `decodeValue`, after `followUp <- ...`:

```scala
    consentWaiting <- optionalAbsent(value, "consentWaiting", path)(
      ConsentProjectionCodec.decodeWaiting)
    consentPrompt <- optionalAbsent(value, "consentPrompt", path)(
      ConsentProjectionCodec.decodePrompt)
    preferenceRaws <- default(value, "consentPreferences", path,
      Vector.empty[ujson.Value])(array)
    consentPreferences <- traverse(preferenceRaws,
      s"$path.consentPreferences")(ConsentProjectionCodec.decodePreferences)
```

  and pass `consentWaiting, consentPrompt, consentPreferences` after `followUp` in the `yield GameProjection(...)`.

`SitePowerProjection` (`WorldProjectionDtos.scala:72`) gains a last field `id: String = ""`, the power's id. In `WorldProjectionCodec`, `encodePower` adds `"id" -> value.id`. `decodePower` adds `"id"` to its `exact` set, reads `id <- default(value, "id", path, "")(string)`, and passes it last.

- [ ] **Step 4: The server side**

In `GamePresentationProjector.sitePower`, pass `handler.value` as the new last argument of both `SitePowerProjection(...)` calls.

Create `src/main/scala/oathdigital/application/ConsentProjector.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.protocol.projection._

/** A waivable power's name as the panels write it: its source's name, the
  * Narrow Pass for the Pass. */
private[application] final class ConsentNames(
    presentation: GamePresentationProjector, powers: ConsentPowers):
  def power(id: PowerId): String = powers.source(id) match
    case Some(PowerSourceRef.Site(site)) => presentation.siteLabel(site)
    case Some(PowerSourceRef.Card(card)) =>
      presentation.cardDetails(card, None, hidden = false).name
    case Some(PowerSourceRef.Banner(banner)) => BannerRules.displayName(banner)
    case None => presentation.safeLabel(id.value)

/** The consent parts of a viewer's projection (consent design,
  * "Projection"): the requester's waiting notice in place of the decision,
  * each asked granter's prompt, and the viewer's own preferences. Nothing
  * here says whether an answer was automatic, and no other player's
  * preferences leave the server. */
private[application] final class ConsentProjector(
    presentation: GamePresentationProjector,
    decisions: WalkerDecisionProjector, powers: ConsentPowers):
  private val names = new ConsentNames(presentation, powers)

  def applied(context: ScopedProjectionContext, projection: GameProjection)
      : GameProjection =
    val ready = context.ready
    val request = ready.consent.pending
    val requesting = request.filter(waiting =>
      context.viewer.contains(waiting.requester))
    val prompt = for
      waiting <- request
      viewer <- context.viewer
      ask <- waiting.open.find(_.granter == viewer)
    yield ConsentPromptProjection(waiting.requester.value,
      ask.powers.map(names.power), target(context, waiting.target))
    projection.copy(
      walkerDecision =
        if requesting.nonEmpty then None else projection.walkerDecision,
      consentWaiting = requesting.map(waiting =>
        ConsentWaitingProjection(waiting.open.map(_.granter.value))),
      consentPrompt = prompt,
      consentPreferences = context.viewer.toVector.flatMap(
        preferences(ready, _)))

  private def preferences(ready: ReadyGame, viewer: PlayerId)
      : Vector[ConsentPreferencesProjection] =
    powers.ruledBy(ready, viewer).map(power => ConsentPreferencesProjection(
      power.value, names.power(power),
      ready.game.current.players.map(_.player).filter(_ != viewer).map(
        other => ConsentPreferenceRowProjection(other.value,
          ready.consent.preference(viewer, power, other)
            .fold(ConsentSetting.CaseByCase.key)(_.key)))))

  /** The option asked about, named as its decision row names it. */
  private def target(context: ScopedProjectionContext,
      ref: DecisionOptionRef): String =
    DecisionOption.forRef(ref).flatMap(option => decisions.optionProjection(
        context.ready, context.viewer, context.ready.game.cardIndex.toOption,
        option))
      .fold(ref.wireId)(_.label)

private[application] object ConsentProjector:
  val withdrawNote = "Withdraw your consent request"
```

In `WalkerDecisionProjector`:
- add `ConsentAskProjection` to the `oathdigital.protocol.projection` import list, and import `oathdigital.gameplay.powers.ConsentPowers` (beside `PhasePowerCatalog, WalkerPowerCatalog`);
- add `private lazy val consentNames = new ConsentNames(presentation, ConsentPowers.default(catalog))` after `reasons`;
- in `optionProjection`, put this arm before the `DecisionOption.Unavailable(inner, reason)` arm:

```scala
      // Unavailable, but its rulers may be asked (consent design,
      // "Projection"): shown as askable, with whom to ask.
      case DecisionOption.Unavailable(inner,
          reason @ OptionReason.Askable(_, _, asks)) =>
        optionProjection(ready, viewer, index, inner, details).map(_.copy(
          state = "askable", reason = Some(reasonText(ready, viewer, reason)),
          consent = Some(ConsentAskProjection(asks.map(_.granter.value),
            asks.flatMap(_.powers).distinct.map(consentNames.power)))))
```

In `GameProjection.scala` (`GameProjector`):
- import `oathdigital.gameplay.powers.ConsentPowers` (beside `PhasePowerCatalog, WalkerPowerCatalog`);
- after `pendingProjector`, add `private val consent = new ConsentProjector(presentation, walkerDecisions, ConsentPowers.default(catalog))`;
- replace the `Ready(ready)` arm of `projectFor` with:

```scala
    case Ready(ready) =>
      val context = ScopedProjectionContext(ready, requestingPlayer)
      val waiting = ready.consent.pending
      // While a request waits, its requester's Undo withdraws it, and nobody
      // redoes past it (consent design, plan decision 7).
      val withdraws = waiting.exists(request =>
        requestingPlayer.contains(request.requester))
      consent.applied(context, readyProjection(gameId, loaded.nextSequence,
        context).copy(
        undo = if withdraws then
            Some(StepOfferProjection(ConsentProjector.withdrawNote))
          else offer(loaded.undo, requestingPlayer, "Undo"),
        redo = if waiting.nonEmpty then None
          else offer(loaded.redo, requestingPlayer, "Redo"),
        logRevision = loaded.logRevision))
```

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.ConsentProjectionSuite oathdigital.application.TravelDestinationRowsSuite oathdigital.application.UndoProjectionSuite oathdigital.application.SitePowerTextSuite" "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 6: Run the whole server and frontend suites**

Run: `./sbtw test frontend/test`
Expected: PASS. The frontend reads the new fields only from Task 9 on, and every new field has a default.

- [ ] **Step 7: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala src/main/scala/oathdigital/application/ConsentProjector.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/GameProjection.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/test/scala/oathdigital/application/ConsentProjectionSuite.scala
git commit -m "feat(consent): project askable options, the prompt, the wait and each ruler's own preferences"
```

---

### Task 8: The consent lines in the Game Log

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/ConsentLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala:22-75, 83-101, 134-177`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala`

**Interfaces:**
- Consumes: Tasks 3 to 5's service and `ConsentFixture`. Task 2's `ConsentPowers.source`.
- Produces: the log lines in Global Constraints. A withdrawn request's entry has `takenBack = Some(LogTakenBack(entry.sequence, Vector.empty))`, which Task 10's pane draws.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{ConsentFixture, GameAccepted,
  GameApplicationService, GameCommand}
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.{formatter, texts}

/** What the Game Log says about consent, and to whom (consent design,
  * "Game Log"). */
class GameLogConsentSuite extends munit.FunSuite:
  import ConsentFixture._
  private val asked = "Asked p2 for consent to ignore Narrow Pass and " +
    "select Dunes."

  private def log(service: GameApplicationService, viewer: PlayerId)
      : Vector[LogEntry] =
    formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(viewer))

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  test("the request and a grant read the same to every player"):
    val (service, _) = ConsentFixture.service()
    answering(service, asking(service), ConsentChoice.Yes)
    Vector(p1, p2, p3).foreach { viewer =>
      val lines = texts(log(service, viewer))
      assert(lines.contains(asked), lines)
      assert(lines.contains("p2 gave consent."), lines)
    }

  test("a refusal names the ruler"):
    val (service, _) = ConsentFixture.service()
    answering(service, asking(service), ConsentChoice.Never)
    assert(texts(log(service, p1)).contains("p2 gave no consent."))

  test("a preference line is written for its granter alone"):
    val (service, _) = ConsentFixture.service()
    val always = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val never = accepted(service.handle(gameId, always.nextSequence,
      setPreference(ConsentSetting.Never)))
    accepted(service.handle(gameId, never.nextSequence,
      setPreference(ConsentSetting.CaseByCase)))
    val ruler = texts(log(service, p2))
    Vector("p2 now always allows p1 to ignore Narrow Pass.",
      "p2 now never allows p1 to ignore Narrow Pass.",
      "p2 now decides case by case whether p1 may ignore Narrow Pass.")
      .foreach(line => assert(ruler.contains(line), ruler))
    Vector(p1, p3).foreach(viewer =>
      assert(!texts(log(service, viewer)).exists(_.contains("Narrow Pass")),
        texts(log(service, viewer))))

  test("a reset line is written for its granter alone"):
    val stale = TravelFixture.board().copy(consent = ConsentState()
      .preferenceSet(p2, NarrowPassSite.pass, p1, ConsentSetting.Always))
    val (service, _) = ConsentFixture.service(stale)
    startTravel(service)
    val reset = "p2's consent preferences for Narrow Pass were reset."
    assert(texts(log(service, p2)).contains(reset))
    assert(!texts(log(service, p1)).contains(reset))

  test("a withdrawn request's line is struck through, under no header"):
    val (service, _) = ConsentFixture.service()
    val waiting = asking(service)
    accepted(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p1)))
    val entry = present(log(service, p1).find(LogScripts.text(_) == asked),
      "the request line")
    assertEquals(entry.takenBack,
      Some(LogTakenBack(entry.sequence, Vector.empty)))

  test("the requester's log cannot tell an automatic grant from a manual " +
      "one"):
    val (manual, _) = ConsentFixture.service()
    answering(manual, asking(manual), ConsentChoice.Yes)
    val (automatic, _) = ConsentFixture.service()
    val set = accepted(automatic.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(automatic, set.nextSequence)
    accepted(automatic.handle(gameId, started.nextSequence, requestDunes))
    assertEquals(texts(log(automatic, p1)), texts(log(manual, p1)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogConsentSuite"`
Expected: FAIL. Task 1's silent arm posts nothing, so `lines.contains(asked)` is false.

- [ ] **Step 3: Write the lines**

Create `src/main/scala/oathdigital/application/gamelog/ConsentLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequested}
import LogSpan.Text

/** The consent lines (consent design, "Game Log"). The request leaves out
  * its actor, as every actor line does; an answer names its granter, who
  * acts off turn. A preference line, and a reset, are formatted for their
  * granter alone. */
private[gamelog] final class ConsentLines(words: LogWords,
    choices: ChoiceWords, powers: ConsentPowers):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    (for
      before <- journal.readyBefore(at)
      after <- journal.readyAfter(at)
    yield
      def named(ids: Vector[PowerId]): Vector[LogSpan] = LogWords.join(
        ids.map(id => powers.source(id).fold(Vector[LogSpan](Text(id.value)))(
          words.source(_, Vector(before), viewer))))
      def line(spans: Vector[LogSpan]) =
        Vector(Posted.line(LogKind.Decision, spans))
      journal.event(at) match
        case ConsentRequested(request) => line(
          (Text("Asked ") +: LogWords.join(request.asks.map(ask =>
            Vector(words.player(ask.granter))))) ++
          (Text(" for consent to ignore ") +:
            named(request.asks.flatMap(_.powers).distinct)) ++
          (Text(" and select ") +: choices.option(request.target, before,
            after, viewer)) :+ Text("."))
        case ConsentAnswered(granter, granted) => line(Vector(
          words.player(granter),
          Text(if granted then " gave consent." else " gave no consent.")))
        case ConsentPreferenceSet(granter, power, requester, setting)
            if viewer.contains(granter) =>
          val (verb, linking) = setting match
            case ConsentSetting.Always => (" now always allows ", " to ignore ")
            case ConsentSetting.Never => (" now never allows ", " to ignore ")
            case ConsentSetting.CaseByCase =>
              (" now decides case by case whether ", " may ignore ")
          line((Vector(words.player(granter), Text(verb),
            words.player(requester), Text(linking)) ++ named(Vector(power))) :+
            Text("."))
        case ConsentPreferencesCleared(granter, power)
            if viewer.contains(granter) =>
          line((Vector(words.player(granter),
            Text("'s consent preferences for ")) ++ named(Vector(power))) :+
            Text(" were reset."))
        case _ => Vector.empty
    ).getOrElse(Vector.empty)
```

- [ ] **Step 4: Wire them into the formatter**

In `GameLogFormatter.scala`:

1. Import `oathdigital.gameplay.powers.ConsentPowers` (beside `WalkerPowerCatalog`). After `takenBack`, add:

```scala
  private val consent = new ConsentLines(words, choices,
    ConsentPowers.default(catalog))
```

2. In `eventLines`, replace Task 1's silent arm with:

```scala
      case _: ConsentEvent => (consent.lines(journal, at, viewer), run)
```

3. In `formatLive`, file consent lines in the latest section, as a Skip is filed:

```scala
          case _: FollowUpSkipped | _: ConsentEvent => opened.map(_.copy(
            section = entries.reverseIterator.flatMap(_.section).nextOption()))
```

   Then update that arm's comment to say that a consent line, like a Skip, belongs to the latest section in time order.

4. In `format`, strike withdrawn requests. Replace the final expression with `withdrawn(steps, takenBack.mark(...))`, and add after `format`:

```scala
  /** A withdrawn request's line, struck through under no header (consent
    * design, "Game Log"). At most one request waits at a time, so each
    * withdrawal takes back the latest request before it. */
  private def withdrawn(steps: Vector[ReplayStep[OathState, OathEvent]],
      entries: Vector[LogEntry]): Vector[LogEntry] =
    val struck = steps.foldLeft((Option.empty[Long], Set.empty[Long])) {
      case ((open, done), step) => step.event.event match
        case _: OathEvent.ConsentRequested => (Some(step.event.index), done)
        case _: OathEvent.ConsentRequestWithdrawn => (None, done ++ open)
        case _ => (open, done)
    }._2
    entries.map(entry =>
      if struck(entry.sequence) then entry.copy(takenBack =
        Some(LogTakenBack(entry.sequence, Vector.empty)))
      else entry)
```

- [ ] **Step 5: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ConsentLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/application/gamelog/GameLogConsentSuite.scala
git commit -m "feat(consent): log requests and answers for everyone, preferences for their ruler alone"
```

---

### Task 9: Askable options ask before they send

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala` (aliases)
- Create: `frontend/src/main/scala/oathdigital/frontend/ConsentAsking.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:133-139`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala:4-11, 93-106`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala:17-18, 64-105, 118-134`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala:95-115`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:145-152, 154-215`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:101-104`
- Test: `frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala`

**Interfaces:**
- Consumes: Task 7's DTOs. Task 6's `GameIntent.RequestConsent(decisionId, WalkerStartArgWire)`.
- Produces:
  - Aliases `ConsentAskState`, `ConsentPromptState`, `ConsentWaitingState`, `ConsentPreferencesState` and `ConsentPreferenceRowState`.
  - `OptionState.Askable`.
  - `CardInspection.Request.Confirm(question, confirm: () => Unit, origin)` and `CardInspection.openConfirm`.
  - `CardInspectionOverlay.showConfirm`.
  - `ConsentAsking.name(value, playerId)`, `listed(words)`, `question(value, ask)` and `asker(decisionId, value, canControl, controls): (DecisionOptionState, dom.html.Element) => Unit`.
  - `BoardOptions(views, pick, ask = (_, _) => ())`.
  - `BoardOptions.of(surface, drafts, canControl, controls, value)`.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.click
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent, WalkerStartArgWire}
import ParkedDecision.Surface
import org.scalajs.dom

/** An askable option asks before it sends (consent design, user story steps
  * 1 and 2, "Frontend"). */
class ConsentAskingSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue), activePlayer = Some("blue"))
  private val ask = ConsentAskState(Vector("red"), Vector("Narrow Pass"))
  private val reason = "Narrow Pass: Cannot travel to other sites in the " +
    "region without ruler's consent."
  private val woods = DecisionOptionState("site", "site:deep-woods",
    "Deep Woods", state = "askable", reason = Some(reason),
    consent = Some(ask))
  private val query = DecisionQueryState.ChooseOne(Vector(woods))
  private val decision = WalkerDecisionState("travel", "travel.destination",
    "decide", query = Some(query), presentation = "board")
  private val requested = Intent.RequestConsent("travel.destination",
    WalkerStartArgWire("site", "site:deep-woods"))
  private var opened = Vector.empty[CardInspection.Request]

  override def beforeEach(context: BeforeEach): Unit =
    opened = Vector.empty
    CardInspection.onOpen(request => opened :+= request)

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  private def confirmation(using munit.Location): CardInspection.Request.Confirm =
    opened match
      case Vector(confirm: CardInspection.Request.Confirm) => confirm
      case other => fail(s"expected one confirmation, got $other")

  test("the question names the granters and the powers"):
    assertEquals(ConsentAsking.question(table, ask),
      "Ask Red for consent to ignore Narrow Pass?")
    assertEquals(ConsentAsking.listed(Vector("Red", "Green", "White")),
      "Red, Green and White")
    assertEquals(ConsentAsking.listed(Vector("Red", "Green")), "Red and Green")

  test("on the board an askable option is amber, says why, and asks " +
      "before it sends"):
    val ui = new RecordingControls()
    val views = BoardOptionViews.of(decision, query, None)
    assertEquals(views.get(BoardOptionViews.key(woods)).map(_.state),
      Some(OptionState.Askable))
    val options = new BoardOptions(views,
      _ => fail("an askable option is never picked"),
      ConsentAsking.asker("travel.destination", table, canControl = true, ui))
    val box = dom.document.createElement("article")
    options.decorate(box, Vector(BoardOptionViews.key(woods)))
    assert(box.classList.contains("board-option-askable"))
    assertEquals(box.getAttribute("title"), reason)
    click(box)
    assertEquals(confirmation.question,
      "Ask Red for consent to ignore Narrow Pass?")
    assertEquals(ui.submitted, Vector.empty)
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(requested))

  test("without control, an askable option asks nothing"):
    val ui = new RecordingControls()
    ConsentAsking.asker("travel.destination", table, canControl = false, ui)(
      woods, dom.document.createElement("button")
        .asInstanceOf[dom.html.Element])
    assertEquals(opened, Vector.empty)

  test("in the panel an askable option is an enabled amber button that " +
      "asks"):
    val ui = new RecordingControls()
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(Surface.ChooseOne(
      decision.copy(presentation = "panel"), query), table, canControl = true,
      panel, ui)
    val control = panel.querySelector(".walker-choice")
      .asInstanceOf[dom.html.Button]
    assert(control.classList.contains("walker-choice-askable"))
    assert(!control.disabled)
    assertEquals(control.getAttribute("title"), reason)
    control.click()
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(requested))

  test("the overlay's Yes runs the question's action and closes; No only " +
      "closes"):
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val overlay = new CardInspectionOverlay(root)
    val opener = dom.document.createElement("button")
      .asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    var confirmed = 0
    overlay.showConfirm("Ask Red?", () => confirmed += 1, opener)
    root.querySelector(".overlay-confirm-yes").asInstanceOf[dom.html.Button]
      .click()
    assert(!overlay.isOpen)
    assertEquals(confirmed, 1)
    overlay.showConfirm("Ask Red?", () => confirmed += 1, opener)
    root.querySelector(".overlay-confirm-no").asInstanceOf[dom.html.Button]
      .click()
    assert(!overlay.isOpen)
    assertEquals(confirmed, 1)
    overlay.dispose()
    root.remove()
    opener.remove()
```

`WalkerDecisionState` is the frontend's alias for `WalkerDecisionProjection`; check `frontend.scala` for its exact name. `BoardOptionViews.of(decision, query, draft)` is the call `BoardOptions.of` makes; read its signature in `BoardOptionViews.scala` and match it.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentAskingSuite"`
Expected: compilation FAIL, `Not found: ConsentAskState`.

- [ ] **Step 3: Aliases, the confirmation and the asker**

In `frontend.scala`, beside the other decision aliases:

```scala
type ConsentAskState = protocol.projection.ConsentAskProjection
val ConsentAskState = protocol.projection.ConsentAskProjection
type ConsentPromptState = protocol.projection.ConsentPromptProjection
val ConsentPromptState = protocol.projection.ConsentPromptProjection
type ConsentWaitingState = protocol.projection.ConsentWaitingProjection
val ConsentWaitingState = protocol.projection.ConsentWaitingProjection
type ConsentPreferencesState = protocol.projection.ConsentPreferencesProjection
val ConsentPreferencesState = protocol.projection.ConsentPreferencesProjection
type ConsentPreferenceRowState = protocol.projection.ConsentPreferenceRowProjection
val ConsentPreferenceRowState = protocol.projection.ConsentPreferenceRowProjection
```

In `CardInspection.scala`, add to `object Request`:

```scala
    /** A yes-or-no question whose Yes runs `confirm` (consent design,
      * "Frontend"). */
    final case class Confirm(question: String, confirm: () => Unit,
        origin: dom.html.Element) extends Request
```

and to the object:

```scala
  def openConfirm(question: String, confirm: () => Unit,
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Confirm(question, confirm, origin)))
```

In `CardInspectionOverlay.scala`, after `showCards`:

```scala
  /** A yes-or-no question (consent design, "Frontend"): Yes closes the
    * overlay and runs `confirm`; No only closes it. */
  def showConfirm(question: String, confirm: () => Unit,
      origin: dom.html.Element): Unit =
    clear()
    node.setAttribute("aria-label", question)
    val panel = element("div", "card-overlay-details overlay-confirm")
    panel.appendChild(text("p", "overlay-question", question))
    val yes = button("Yes", "overlay-confirm-yes")
    val no = button("No", "overlay-confirm-no")
    yes.onclick = _ => { hide(); confirm() }
    no.onclick = _ => hide()
    panel.appendChild(yes)
    panel.appendChild(no)
    body.appendChild(panel)
    open(origin)
```

In `GameTableShell`'s `CardInspection.onOpen` handler, add:

```scala
    case CardInspection.Request.Confirm(question, confirm, origin) =>
      inspector.showConfirm(question, confirm, origin)
```

Create `frontend/src/main/scala/oathdigital/frontend/ConsentAsking.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import org.scalajs.dom

/** Asking for consent from the table (consent design, user story steps 1
  * and 2). The projection names players by id; this words their display
  * names. */
private[frontend] object ConsentAsking:
  def name(value: GameProjection, playerId: String): String =
    value.players.find(_.playerId == playerId).map(_.displayName)
      .getOrElse(playerId)

  /** "A", "A and B", "A, B and C". */
  def listed(words: Vector[String]): String = words match
    case Vector() => ""
    case Vector(only) => only
    case _ => s"${words.init.mkString(", ")} and ${words.last}"

  /** "Ask Red for consent to ignore Narrow Pass?" */
  def question(value: GameProjection, ask: ConsentAskState): String =
    s"Ask ${listed(ask.granterPlayerIds.map(name(value, _)))} for consent " +
      s"to ignore ${listed(ask.powers)}?"

  /** What choosing an askable option of `decisionId` does: the
    * confirmation, whose Yes sends the request. Without control, nothing. */
  def asker(decisionId: String, value: GameProjection, canControl: Boolean,
      controls: TableControls): (DecisionOptionState, dom.html.Element) => Unit =
    (option, origin) =>
      if canControl then option.consent.foreach(ask =>
        CardInspection.openConfirm(question(value, ask), () =>
          controls.submitCommand(GameCommand.RequestConsent(decisionId,
            WalkerStartArgWire(option.kind, option.id))), origin))
```

- [ ] **Step 4: Draw the askable state on the board and in the panels**

`BoardOptionViews.scala`:
- add `case Askable extends OptionState(Vector("board-option", "board-option-askable"))` after `Unavailable`;
- in `view`, add `case "askable" => OptionView(option, OptionState.Askable, option.reason)` right after the `"unavailable"` arm.

`BoardOptions.scala`:
- the class takes a third parameter:

```scala
private[frontend] final class BoardOptions(views: Map[String, OptionView],
    pick: DecisionOptionState => Unit,
    ask: (DecisionOptionState, dom.html.Element) => Unit = (_, _) => ()):
```

- in `mark`, an askable option answers a click, Enter or Space by asking; replace the `case OptionState.Available | OptionState.Selected =>` arm's head and its two `pick(found.option)` calls:

```scala
      case OptionState.Available | OptionState.Selected |
          OptionState.Askable =>
        // An askable option asks its granters first (consent design,
        // "Frontend"); any other is picked.
        val act: DecisionOptionState => Unit =
          if found.state == OptionState.Askable then
            option => ask(option, target.asInstanceOf[dom.html.Element])
          else pick
        target.setAttribute("aria-pressed",
          (found.state == OptionState.Selected).toString)
```

  Then call `act(found.option)` where the arm called `pick(found.option)`, in both the click and the keydown listeners.
- `BoardOptions.of` gains `value: GameProjection` last and passes the asker:

```scala
  def of(surface: ParkedDecision.Surface.BoardDecision, drafts: SessionDrafts,
      canControl: Boolean, controls: TableControls,
      value: GameProjection): BoardOptions =
    new BoardOptions(BoardOptionViews.of(surface.decision, surface.query,
      drafts.board), stager(surface, drafts, canControl, controls),
      ConsentAsking.asker(surface.decision.decisionId, value, canControl,
        controls))
```

`TableScreen.scala`: the call becomes `BoardOptions.of(board, drafts, view.controlsAvailable, controls, value)`.

`BoardDecisionPanel.scala`, the loose-option loop: an askable option asks instead of staging.

```scala
    val ask = ConsentAsking.asker(decision.decisionId, value, canControl,
      controls)
    loose.foreach { option =>
      val key = BoardOptionViews.key(option)
      val found = views.get(key)
      val askable = found.exists(_.state == OptionState.Askable)
      val toggle = button(label(option), "board-decision-loose-option")
      if askable then toggle.classList.add("board-option-askable")
      toggle.setAttribute("data-option-ref", key)
      toggle.setAttribute("aria-pressed",
        found.exists(_.state == OptionState.Selected).toString)
      toggle.disabled = !canControl ||
        found.exists(_.state == OptionState.Unavailable)
      found.flatMap(_.reason).foreach(toggle.setAttribute("title", _))
      toggle.onclick = _ => if askable then ask(option, toggle) else stage(option)
      BoardOptions.hoverSync(toggle, key)
      panel.appendChild(toggle)
    }
```

(Use the names the surrounding code already gives the decision, the projection and the controls, as the existing `WalkerPanelSupport.resolveChooseOneCommand(decision, option)` and `BoardHomes.has(value, option)` calls do.)

`WalkerPanelSupport.scala`:
- `withholds` also explains an askable option, without disabling it:

```scala
    if option.state == "unavailable" || option.state == "warning" ||
        option.state == "askable" then
```

- in `renderChooseOnePanel`, add `val ask = ConsentAsking.asker(decision.decisionId, value, canControl, controls)` after `val query = surface.query`, and replace the button's handler:

```scala
      choose.disabled = !canControl
      val askable = option.state == "askable"
      if askable then choose.classList.add("walker-choice-askable")
      choose.onclick = _ =>
        if askable then ask(option, choose)
        else controls.submitCommand(resolveChooseOneCommand(decision, option))
```

Adding `Askable` makes the compiler name every other exhaustive match over `OptionState`. Treat `Askable` like `Available` wherever an option's look is drawn, and like `Unavailable` wherever the code decides whether a pick may be staged.

- [ ] **Step 5: Run the frontend suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentAskingSuite oathdigital.frontend.BoardOptionsRenderSuite oathdigital.frontend.BoardDecisionPanelSuite oathdigital.frontend.WalkerChoicePanelRenderSuite oathdigital.frontend.CardInspectionOverlaySuite"`
Expected: PASS.

- [ ] **Step 6: Run the whole frontend suite**

Run: `./sbtw frontend/test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/frontend.scala frontend/src/main/scala/oathdigital/frontend/ConsentAsking.scala frontend/src/main/scala/oathdigital/frontend/CardInspection.scala frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala
git commit -m "feat(consent): an askable option asks its ruler through a confirmation"
```

---

### Task 10: The granter's prompt, the requester's wait and the withdrawn line

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala:113-135, 225-231`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala:100-117`
- Test: `frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogWithdrawnSuite.scala`

**Interfaces:**
- Consumes: Task 7's `consentPrompt` and `consentWaiting`, Task 9's `ConsentAsking`, and Task 8's withdrawn marking.
- Produces:
  - `ConsentPanels.prompt(value, prompt): String`
  - `ConsentPanels.waiting(value, waiting): String`
  - `ConsentPanels.render(value, canControl, panel, controls)`
  - `ParkedDecision.render` draws the prompt. `ParkedDecision.route`'s notice prefers the consent wait and drops the walker wait for a prompted granter.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.protocol.projection.WalkerWaitingProjection
import org.scalajs.dom

/** The asked ruler's prompt and the requester's wait (consent design, user
  * story steps 3 to 6). Red rules the Pass; Blue asks. */
class ConsentPromptPanelSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue), activePlayer = Some("blue"))
  private val prompted = table.copy(
    walkerWaiting = Some(WalkerWaitingProjection("blue",
      Some("Choose a Travel destination"))),
    consentPrompt = Some(ConsentPromptState("blue", Vector("Narrow Pass"),
      "Deep Woods")))
  private val waiting = table.copy(
    consentWaiting = Some(ConsentWaitingState(Vector("red"))))

  private def pane(value: GameProjection, viewer: String,
      ui: RecordingControls, canControl: Boolean = true): dom.Element =
    val panel = dom.document.createElement("div")
    val presentation = ServerUiSupport.viewerPresentation(value, viewer)
    ParkedDecision.render(value, presentation,
      ParkedDecision.route(value, presentation), canControl, panel,
      SessionDrafts.empty, ui)
    panel

  test("the asked ruler reads the question, four answers and where to " +
      "change preferences, and no waiting line"):
    val panel = pane(prompted, "red", new RecordingControls())
    assertEquals(panel.querySelector(".consent-question").textContent,
      "Give Blue consent to ignore Narrow Pass and select Deep Woods?")
    assertEquals(all(panel, ".consent-answer").map(_.textContent),
      Vector("Never Allow", "No", "Yes", "Always Allow"))
    assertEquals(panel.querySelector(".consent-notice").textContent,
      "You can adjust your consent preferences by clicking on the Narrow " +
        "Pass power.")
    assertEquals(all(panel, ".walker-waiting"), Vector.empty)

  test("each answer sends its choice"):
    val ui = new RecordingControls()
    val panel = pane(prompted, "red", ui)
    all(panel, ".consent-answer").foreach(_.asInstanceOf[dom.html.Button]
      .click())
    assertEquals(ui.submitted, Vector("never", "no", "yes", "always")
      .map(Intent.AnswerConsent(_)))

  test("without control the answers are disabled"):
    val panel = pane(prompted, "red", new RecordingControls(),
      canControl = false)
    assert(all(panel, ".consent-answer")
      .forall(_.asInstanceOf[dom.html.Button].disabled))

  test("the requester waits on the ruler's consent"):
    val panel = pane(waiting, "blue", new RecordingControls())
    assertEquals(all(panel, ".walker-waiting").map(_.textContent),
      Vector("Waiting for Red's consent…"))
    assertEquals(all(panel, ".consent-prompt"), Vector.empty)
```

Create `frontend/src/test/scala/oathdigital/frontend/GameLogWithdrawnSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.projection.{LogSectionWire, LogTakenBackWire}

/** A withdrawn consent request in the log: struck through in place, with
  * no group header (consent design, "Game Log"). */
class GameLogWithdrawnSuite extends munit.FunSuite:
  test("a withdrawn request's line stays in place, struck through, with no " +
      "toggle"):
    val section = Some(LogSectionWire(5L, "travel"))
    val started = TestLog.textEntry(5, "action", depth = 1, "Started Travel",
      section = section)
    val asked = TestLog.textEntry(7, "decision", depth = 1,
      "Asked Red for consent to ignore Narrow Pass and select Deep Woods.",
      section = section, takenBack = Some(LogTakenBackWire(7L, Vector.empty)))
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|blue", Vector(started, asked),
      Map("red" -> "player-red"))
    val line = content.querySelector(".log-withdrawn")
    assert(line != null)
    assertEquals(line.textContent,
      "Asked Red for consent to ignore Narrow Pass and select Deep Woods.")
    assertEquals(content.querySelector(".log-taken-back-toggle"), null)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentPromptPanelSuite oathdigital.frontend.GameLogWithdrawnSuite"`
Expected: compilation FAIL, `Not found: ConsentPanels`. Once that compiles, the withdrawn test fails on `line != null`.

- [ ] **Step 3: The prompt and the wait**

Create `frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The asked ruler's prompt and the requester's waiting line (consent
  * design, user story steps 3 to 6). */
private[frontend] object ConsentPanels:
  private val answers = Vector("Never Allow" -> "never", "No" -> "no",
    "Yes" -> "yes", "Always Allow" -> "always")

  /** "Give Blue consent to ignore Narrow Pass and select Deep Woods?" */
  def prompt(value: GameProjection, asked: ConsentPromptState): String =
    s"Give ${ConsentAsking.name(value, asked.requesterPlayerId)} consent to " +
      s"ignore ${ConsentAsking.listed(asked.powers)} and select " +
      s"${asked.target}?"

  /** "Waiting for Red's consent…" */
  def waiting(value: GameProjection, wait: ConsentWaitingState): String =
    val names = wait.granterPlayerIds.map(id =>
      s"${ConsentAsking.name(value, id)}'s")
    s"Waiting for ${names.mkString(" and ")} consent…"

  /** The prompt, when the viewer is asked. */
  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      controls: TableControls): Unit =
    value.consentPrompt.foreach { asked =>
      val section = element("section", "consent-prompt")
      section.appendChild(text("p", "consent-question", prompt(value, asked)))
      val row = element("div", "consent-answers")
      answers.foreach { (label, choice) =>
        val answer = button(label, s"consent-answer consent-answer-$choice")
        answer.disabled = !canControl
        answer.onclick = _ =>
          controls.submitCommand(GameCommand.AnswerConsent(choice))
        row.appendChild(answer)
      }
      section.appendChild(row)
      section.appendChild(text("p", "consent-notice",
        "You can adjust your consent preferences by clicking on the " +
          s"${ConsentAsking.listed(asked.powers)} power."))
      panel.appendChild(section)
    }
```

In `ParkedDecision.render`, draw the prompt between the surface and the notice:

```scala
    ConsentPanels.render(value, canControl, panel, controls)
    routed.notice.foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))
```

Replace `waitingNotice` with:

```scala
  /** The line shown to a viewer the parked position is not waiting on:
    * the requester's wait for consent first; nothing for an asked granter,
    * whose prompt says it all; else who the decision awaits, with the
    * question's heading when it has one (`None` for a parked Roll). */
  private def waitingNotice(value: GameProjection): Option[String] =
    value.consentWaiting.map(ConsentPanels.waiting(value, _)).orElse(
      if value.consentPrompt.nonEmpty then None
      else value.walkerWaiting.map { waiting =>
        val name = value.players.find(_.playerId == waiting.playerId)
          .map(_.displayName).getOrElse(waiting.playerId)
        waiting.heading.fold(s"Waiting for $name")(heading =>
          s"Waiting for $name: $heading")
      })
```

- [ ] **Step 4: The withdrawn line in the log pane**

In `GameLogPane.redraw`, the `entry.takenBack match` gains an arm before `case Some(taken)`:

```scala
          // A withdrawn consent request: struck in place, under no header
          // (consent design, "Game Log").
          case Some(taken) if taken.header.isEmpty =>
            node.classList.add("log-withdrawn")
            list.appendChild(node)
            None
```

- [ ] **Step 5: Run the frontend suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentPromptPanelSuite oathdigital.frontend.GameLogWithdrawnSuite oathdigital.frontend.GameLogTakenBackPaneSuite oathdigital.frontend.StepControlsSuite oathdigital.frontend.BoardDecisionPanelSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala frontend/src/test/scala/oathdigital/frontend/GameLogWithdrawnSuite.scala
git commit -m "feat(consent): prompt the asked ruler, show the requester's wait, strike a withdrawn request"
```

---

### Task 11: Site power labels open the power, with the ruler's grid

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/SitePowerLinks.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:133-139`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:47-48, 92-103` (`siteDetails`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:153-156, 210`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:105-106`
- Test: `frontend/src/test/scala/oathdigital/frontend/SitePowerGridSuite.scala`

**Interfaces:**
- Consumes: Task 7's `consentPreferences` and `SitePowerProjection.id`. Task 6's `GameIntent.SetConsentPreference(powerId, requesterId, setting)`. Task 9's `ConsentAsking.name`.
- Produces:
  - `ConsentGrid(rows: Vector[ConsentGridRow], choose: (String, String) => Unit)` and `ConsentGridRow(playerId, name, setting)`.
  - `SitePowerLinks.of(value, submit)` and `SitePowerLinks.none`.
  - `ConsentGridView.render(grid)`.
  - `CardInspection.Request.Power(title, lines, grid, origin)` and `CardInspection.openPower`.
  - `CardInspectionOverlay.showPower`.
  - `WorldBoardRenderer.world(value, options, submit = _ => ())`.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/test/scala/oathdigital/frontend/SitePowerGridSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import org.scalajs.dom

/** A site power's label opens the power, and its ruler's preferences grid
  * (consent design, user story step 7, rulings 16 and 18). Red rules the
  * Pass. */
class SitePowerGridSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val passText = "If your pawn is outside this region, you cannot " +
    "travel to other sites in this region."
  private val pass = SitePower("pass", "Pass", Some(passText),
    "site.narrow-pass.pass")
  private val narrowPass = quietSite("site:narrow-pass", "Narrow Pass")
    .copy(powers = Vector(pass))
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue),
    world = Vector(GameRegion("provinces", Vector(narrowPass))))
  private val ruling = table.copy(consentPreferences = Vector(
    ConsentPreferencesState("site.narrow-pass.pass", "Narrow Pass",
      Vector(ConsentPreferenceRowState("blue", "case-by-case")))))
  private var opened = Vector.empty[CardInspection.Request]

  override def beforeEach(context: BeforeEach): Unit =
    opened = Vector.empty
    CardInspection.onOpen(request => opened :+= request)

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  private def power(using munit.Location): CardInspection.Request.Power =
    opened match
      case Vector(request: CardInspection.Request.Power) => request
      case other => fail(s"expected one power request, got $other")

  private def label(world: dom.Element): dom.html.Button =
    world.querySelector(".site-power").asInstanceOf[dom.html.Button]

  test("a site power's label is a button that opens its text, with no " +
      "grid for a player who does not rule it"):
    val world = WorldBoardRenderer.world(table, BoardOptions.none)
    assertEquals(label(world).textContent, "Pass")
    assertEquals(label(world).getAttribute("title"), s"Pass: $passText")
    label(world).click()
    assertEquals((power.title, power.lines, power.grid),
      ("Pass", Vector(passText), None))

  test("its ruler's grid lists the other players, and a choice sends the " +
      "preference"):
    var sent = Vector.empty[Intent]
    val world = WorldBoardRenderer.world(ruling, BoardOptions.none,
      command => sent :+= command)
    label(world).click()
    val grid = present(power.grid)
    assertEquals(grid.rows, Vector(ConsentGridRow("blue", "Blue",
      "case-by-case")))
    grid.choose("blue", "always")
    assertEquals(sent, Vector(Intent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "always")))

  test("opening the power does not pick the site it stands on"):
    var picked = Vector.empty[DecisionOptionState]
    val site = DecisionOptionState("site", "site:narrow-pass", "Narrow Pass")
    val options = new BoardOptions(Map(BoardOptionViews.key(site) ->
      OptionView(site, OptionState.Available, None)), picked :+= _)
    label(WorldBoardRenderer.world(table, options)).click()
    assertEquals(picked, Vector.empty)
    assertEquals(opened.size, 1)

  test("the overlay draws the grid; a choice moves the pressed button and " +
      "is sent once"):
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val overlay = new CardInspectionOverlay(root)
    val opener = dom.document.createElement("button")
      .asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    var chosen = Vector.empty[(String, String)]
    overlay.showPower("Pass", Vector(passText), Some(ConsentGrid(
      Vector(ConsentGridRow("blue", "Blue", "case-by-case")),
      (player, setting) => chosen :+= (player -> setting))), opener)
    assertEquals(root.querySelector(".consent-grid-heading").textContent,
      "Consent preferences")
    val choices = all(root, ".consent-grid-choice")
      .map(_.asInstanceOf[dom.html.Button])
    assertEquals(choices.map(_.textContent),
      Vector("Never Allow", "Case-by-case", "Always Allow"))
    assertEquals(choices.map(_.getAttribute("aria-pressed")),
      Vector("false", "true", "false"))
    choices(2).click()
    choices(2).click()
    assertEquals(choices.map(_.getAttribute("aria-pressed")),
      Vector("false", "false", "true"))
    assertEquals(chosen, Vector("blue" -> "always"))
    overlay.dispose()
    root.remove()
    opener.remove()

  private def present[A](value: Option[A])(using munit.Location): A =
    value.getOrElse(fail("expected a grid"))
```

`TestSites.quietSite(id, label)` is in `TestTablePieces.scala`; if `GameSite`'s id field is not what the board option key uses, read `WorldBoardRenderer.world`'s `BoardOptionViews.key("site", site.siteId)` and match it.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SitePowerGridSuite"`
Expected: compilation FAIL, `Not found: ConsentGridRow`.

- [ ] **Step 3: The links, the grid and the overlay**

Create `frontend/src/main/scala/oathdigital/frontend/SitePowerLinks.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The viewer's consent preferences for one power they rule (ruling 16):
  * a row per other player, and what choosing a setting does. */
private[frontend] final case class ConsentGrid(rows: Vector[ConsentGridRow],
    choose: (String, String) => Unit)
private[frontend] final case class ConsentGridRow(playerId: String,
    name: String, setting: String)

/** What a site power's label opens (consent design, "Frontend"): the
  * power's printed text, and the viewer's grid when they rule the power.
  * `submit` sends a preference. */
private[frontend] final class SitePowerLinks(value: Option[GameProjection],
    submit: GameCommand => Unit):
  def grid(power: SitePower): Option[ConsentGrid] = for
    projection <- value
    preferences <- projection.consentPreferences.find(_.powerId == power.id)
  yield ConsentGrid(preferences.rows.map(row => ConsentGridRow(row.playerId,
      ConsentAsking.name(projection, row.playerId), row.setting)),
    (requester, setting) => submit(GameCommand.SetConsentPreference(power.id,
      requester, setting)))

private[frontend] object SitePowerLinks:
  val none: SitePowerLinks = new SitePowerLinks(None, _ => ())
  def of(value: GameProjection, submit: GameCommand => Unit): SitePowerLinks =
    new SitePowerLinks(Some(value), submit)

/** The grid as the overlay draws it. A choice is shown at once and sent; the
  * overlay ignores polls while open, so it keeps what was chosen, and a
  * choice already shown sends nothing. */
private[frontend] object ConsentGridView:
  private val settings = Vector("never" -> "Never Allow",
    "case-by-case" -> "Case-by-case", "always" -> "Always Allow")

  def render(grid: ConsentGrid): dom.Element =
    val section = element("section", "consent-grid")
    section.appendChild(text("h4", "consent-grid-heading",
      "Consent preferences"))
    grid.rows.foreach { row =>
      val line = element("div", "consent-grid-row")
      line.setAttribute("data-player-id", row.playerId)
      line.appendChild(text("span", "consent-grid-player", row.name))
      val choices = settings.map { (key, label) =>
        val choice = button(label, "consent-grid-choice")
        choice.setAttribute("data-setting", key)
        choice.setAttribute("aria-pressed", (key == row.setting).toString)
        key -> choice
      }
      choices.foreach { (key, choice) =>
        choice.onclick = _ =>
          if choice.getAttribute("aria-pressed") != "true" then
            choices.foreach((_, other) => other.setAttribute("aria-pressed",
              (other eq choice).toString))
            grid.choose(row.playerId, key)
        line.appendChild(choice)
      }
      section.appendChild(line)
    }
    section
```

In `CardInspection.scala`, add to `object Request`:

```scala
    /** A power's printed text, with the viewer's consent preferences for it
      * when they rule it (consent design, user story step 7). */
    final case class Power(title: String, lines: Vector[String],
        grid: Option[ConsentGrid], origin: dom.html.Element) extends Request
```

and to the object:

```scala
  def openPower(title: String, lines: Vector[String],
      grid: Option[ConsentGrid], origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Power(title, lines, grid, origin)))
```

In `CardInspectionOverlay.scala`, after `showText`:

```scala
  /** A power's printed text, and below it the viewer's consent preferences
    * when they rule it (ruling 16). */
  def showPower(title: String, lines: Vector[String],
      grid: Option[ConsentGrid], origin: dom.html.Element): Unit =
    showText(title, lines, origin)
    grid.foreach(value => body.appendChild(ConsentGridView.render(value)))
```

In `GameTableShell`'s `CardInspection.onOpen` handler, add:

```scala
    case CardInspection.Request.Power(title, lines, grid, origin) =>
      inspector.showPower(title, lines, grid, origin)
```

- [ ] **Step 4: Make the labels buttons, and thread the submit**

`ServerUiSupport.siteDetails` takes `links: SitePowerLinks = SitePowerLinks.none` after `options`. Its power loop becomes:

```scala
    // Two lines of labels at most (`.site-powers`). Each label keeps its
    // rules text on its hover title, and opens the power in the inspector.
    // A button, so the site box it sits in leaves the click to it
    // (`BoardOptions.own`).
    val powers = element("p", "site-powers")
    site.powers.zipWithIndex.foreach { case (power, index) =>
      if index > 0 then powers.appendChild(dom.document.createTextNode(" · "))
      val item = button(power.label, "site-power")
      item.setAttribute("title", power.description.fold(power.label)(
        description => s"${power.label}: $description"))
      item.onclick = (event: dom.MouseEvent) =>
        event.stopPropagation()
        CardInspection.openPower(power.label, power.description.toVector,
          links.grid(power), item)
      powers.appendChild(item)
    }
```

`WorldBoardRenderer.world` takes `submit: GameCommand => Unit = _ => ()` after `options` (import `oathdigital.protocol.{GameIntent => GameCommand}` if the file does not already alias it). It passes `SitePowerLinks.of(value, submit)` as the third argument of `siteDetails(site, options, ...)`.

`TableScreen.scala`: the world becomes

```scala
          WorldBoardRenderer.world(value, options, command =>
            if view.controlsAvailable then controls.submitCommand(command)),
```

- [ ] **Step 5: Run the frontend suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SitePowerGridSuite oathdigital.frontend.SiteFaceSuite oathdigital.frontend.BoardOptionsRenderSuite oathdigital.frontend.CardInspectionOverlaySuite"`
Expected: PASS. `SiteFaceSuite` reads `.site-power` text and title, which a button keeps.

- [ ] **Step 6: Run the whole frontend suite**

Run: `./sbtw frontend/test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/SitePowerLinks.scala frontend/src/main/scala/oathdigital/frontend/CardInspection.scala frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/test/scala/oathdigital/frontend/SitePowerGridSuite.scala
git commit -m "feat(consent): open a site power from its label, with its ruler's preference grid"
```

---

### Task 12: The amber token and the consent styles

No suite reads `frontend/styles.css`, so this task is checked with the Impeccable detector and by reading the rules against DESIGN.md.

**Files:**
- Modify: `DESIGN.md` (front matter tokens, around lines 4-80, and "The Pieces Carry the Color Rule", around lines 319-323)
- Modify: `frontend/styles.css` (`:root` at 14-38, `.board-option*` at 248-257, `.site-power` at 323-326, `.game-log` taken-back rules at 1094-1096)
- Modify: `.impeccable/design.json`, if it lists DESIGN.md's colour tokens

**Interfaces:**
- Consumes: the classes Tasks 9 to 11 put on the DOM:
  - `board-option-askable`, `walker-choice-askable`
  - `overlay-confirm`, `overlay-question`, `overlay-confirm-yes`, `overlay-confirm-no`
  - `consent-prompt`, `consent-question`, `consent-answers`, `consent-answer`, `consent-notice`
  - `site-power` (now a button)
  - `consent-grid`, `consent-grid-heading`, `consent-grid-row`, `consent-grid-player`, `consent-grid-choice`
  - `log-withdrawn`
- Produces: the DESIGN.md token `consent-amber: "#e8a33d"` and the CSS custom property `--consent-amber`.

- [ ] **Step 1: Load Impeccable's context and craft floor**

With the worktree root as the working directory, run `~/.claude/skills/impeccable/scripts/impeccable context`. Then read `~/.claude/skills/impeccable/reference/craft-floor.md`.

Record the baseline: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css > /tmp/consent-detect-before.json`. Use your scratchpad directory instead of `/tmp` if you have one. Run the detector inside the worktree, because it reports nothing on a copy outside the repository.

- [ ] **Step 2: Amend DESIGN.md**

In the front matter's colour tokens, after `brass-bright`, add:

```yaml
  consent-amber: "#e8a33d"
```

Under "The Pieces Carry the Color Rule", after the sentence that names brass and selection blue as the only UI accents, add:

```markdown
One exception: **consent amber** (`consent-amber`) marks an option the viewer
may ask another player's consent to choose (consent design). It appears on
nothing else, so amber always means "ask first".
```

If `.impeccable/design.json` lists the DESIGN.md colour tokens, add `consent-amber` there with the same value.

- [ ] **Step 3: Write the styles**

In `frontend/styles.css`:

1. In `:root`, add `--consent-amber: #e8a33d;`.
2. After the `.board-option-unavailable` rule, add:

```css
/* An option the viewer may ask consent for (consent design): amber, not
   brass, and clickable. */
.board-option-askable { border-color: var(--consent-amber); box-shadow: inset 0 0 0 2px var(--consent-amber); }
.board-option-askable:hover, .board-option-askable:focus-visible { border-color: #fff2d6; box-shadow: 0 0 0 3px rgba(232, 163, 61, 0.4); }
.walker-choice-askable { border-color: var(--consent-amber); color: var(--consent-amber); }
```

3. Replace the `.site-power { cursor: help; }` rule with a reset for the button it now is:

```css
.site-power { appearance: none; background: none; border: 0; padding: 0; margin: 0; font: inherit; color: inherit; cursor: pointer; text-decoration: underline dotted; text-underline-offset: 2px; }
.site-power:focus-visible { outline: 2px solid #8ecbff; outline-offset: 1px; }
```

4. After the `.card-overlay-properties` rules, add:

```css
.overlay-confirm { display: grid; gap: 12px; justify-items: start; }
.overlay-question { margin: 0; }
.consent-grid { margin-top: 16px; display: grid; gap: 8px; }
.consent-grid-heading { margin: 0; }
.consent-grid-row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.consent-grid-player { flex: 1 1 6rem; min-width: 0; }
.consent-grid-choice[aria-pressed="true"] { border-color: #8ecbff; background: #243442; }
```

5. Beside the `.walker-waiting` rule, add:

```css
.consent-prompt { display: grid; gap: 8px; }
.consent-question { margin: 0; }
.consent-answers { display: flex; flex-wrap: wrap; gap: 6px; }
.consent-notice { margin: 0; color: #a99f8c; font-size: 12px; }
```

6. After the `.game-log .log-taken-back-lines .log-entry` rule, add:

```css
/* A withdrawn consent request: struck in place, no group (consent design). */
.game-log .log-withdrawn { text-decoration: line-through; text-decoration-color: #928876; }
```

Keep every new rule within the craft floor you read in Step 1. If it asks for something these rules miss, such as focus states or touch target sizes, apply it and note what changed in the commit message body.

- [ ] **Step 4: Run the detector**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css`
Expected: no finding on a line this task added that is not also in the baseline. Compare findings by rule and line against the Step 1 file. Fix any new finding, then run the detector once more to confirm.

- [ ] **Step 5: Build the frontend**

Run: `./sbtw frontend/test frontend/fullOptJS`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add DESIGN.md frontend/styles.css
git commit -m "style(consent): amber askable options, the prompt, the grid and the withdrawn line"
```

(Add `.impeccable/design.json` if Step 2 changed it.)

---

### Task 13: Docs, and the full gate

**Files:**
- Modify: `docs/rules/rules-reference.md:148-154`
- Modify: `docs/rules/implementation-traceability.md:133`
- Modify: `docs/rules/ambiguities.md:20`
- Modify: `docs/ROADMAP.md:171-176` and the play-test list near lines 52-71
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `CONTEXT.md`

**Interfaces:** none. This task changes documentation only, then runs every gate.

- [ ] **Step 1: The rules docs**

In `docs/rules/rules-reference.md`, under "Rules precedence and timing", after the CR p. 43 consent bullet, add:

```markdown
- A persistent power that restricts its ruler's enemies may be ignored with that ruler's consent, except inside a Campaign action unless the power's own text grants it (CR p. 29). The bandits never consent.
- New Foundations makes "Enemies cannot" powers optional for their ruler, decided at the moment of use (NF p. 16). The digital game always enforces them and asks the ruler explicitly ([consent design](../superpowers/specs/2026-10-08-consent-system-design.md)).
```

In `docs/rules/implementation-traceability.md`, replace the "Enemies cannot" consent enforcement row with:

```markdown
| “Enemies cannot” consent enforcement | **Partial** | [CR pp. 29, 43](rules-reference.md#rules-precedence-and-timing); explicit change [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | Narrow Pass's Travel restriction is waivable: a destination only it blocks is askable, its ruling player is prompted or answers by standing preference, answers last the turn and survive undo, and preferences are private (`model/Consent.scala`, `application/ConsentDesk.scala`, `gameplay/walker/WalkerPowerGather.scala`). Tests: `NarrowPassConsentSuite`, `ConsentPreferenceServiceSuite`, `ConsentRequestServiceSuite`, `ConsentProjectionSuite`, `GameLogConsentSuite` and the frontend consent suites. | The Pass on campaign targeting, Secret Police, Hall of Ministers, Forgotten Vault and the toll and penalty cards are consent slices 2-4; unanswered-request timeouts F3. |
```

In `docs/rules/ambiguities.md`, append to decision 3:

```markdown
   Resolved for the digital game: the engine always enforces, and a blocked player asks the ruler explicitly; a ruler may set a standing per-player answer ([consent design](../superpowers/specs/2026-10-08-consent-system-design.md), rulings 3 and 10).
```

- [ ] **Step 2: The roadmap**

Replace the **Phase - Consent system** paragraph with:

```markdown
### Phase - Consent system

A player blocked by an enemy's persistent power can ask its ruler for
consent, and rulers can set private per-player standing answers
([consent design](superpowers/specs/2026-10-08-consent-system-design.md)).

- [x] 1. Machinery and the Pass on Travel
  ([plan](superpowers/plans/2026-10-09-consent-slice1.md)).
- [ ] 2. The Pass on campaign targeting.
- [ ] 3. Hard-block cards: Secret Police, Hall of Ministers, and Forgotten
  Vault outside campaigns.
- [ ] 4. Toll and penalty cards: Toll Roads, Forced Labor, Curfew and
  Grasping Vines.
```

After the follow-up actions play-test item, add:

```markdown
Left behind by the
[consent work](superpowers/specs/2026-10-08-consent-system-design.md):

- [ ] **Play-test consent in a browser.** The amber destination, the
  confirmation, the ruler's prompt, automatic answers, the preferences grid
  and withdrawing with Undo are covered by tests only. Seeding a game with a
  player-ruled Narrow Pass needs the journaled arranged start.
```

If the roadmap's **Now** paragraph names the consent phase, make it name slice 2 as next.

- [ ] **Step 3: Fold the plan's decisions into the spec**

In `docs/superpowers/specs/2026-10-08-consent-system-design.md`, make these replacements:

1. Status: `Not yet implemented.` becomes `Slice 1 is implemented ([plan](../plans/2026-10-09-consent-slice1.md)), and its plan's decisions are folded in below.`
2. Events, first paragraph: `New \`OathEvent\` cases. None opens a \`CommandAccepted\` batch, so the journal fold keeps them off the undo stack without further changes.` becomes `New \`OathEvent\` cases under a sealed \`ConsentEvent\`. None opens a \`CommandAccepted\` batch, and the journal fold closes any open batch when it meets one, so a whole-journal replay batches exactly as live appends did and consent stays off the undo stack.`
3. In the event list, `\`ConsentRequested(requester, decisionId, target, asks)\`` becomes `\`ConsentRequested(request: ConsentRequest)\``.
4. `The fold clears \`answers\` when the turn changes. That expiry writes no event and no log line.` becomes `\`answers\` belong to \`turn\`: they read as empty once the turn has changed, and the next settlement replaces them. That expiry writes no event and no log line.`
5. Under "Walker and powers", `\`Restriction\` and \`OptionRestriction\` gain a field \`waiver: PowerCtx => Option[SiteRuler]\`. It defaults to not waivable and otherwise returns the power's ruler, the granter.` becomes `\`Restriction\` gains a field \`waiver: Option[Waiver]\`, and \`OptionRestriction\` gains the same in slice 2. A \`Waiver\` holds \`granter: ReadyGame => Option[PlayerId]\` (the ruling player; none for bandits, the Empire or nobody), the \`source\` that names the power, and the consent-worded \`note\`.`
6. `The option becomes \`DecisionOption.Askable(option, reason, granters)\` when ruling 8 holds` becomes `The option becomes \`DecisionOption.Unavailable(option, OptionReason.Askable(power, note, asks))\` when ruling 8 holds and the decision is a choose-one`.
7. Under "Projection", `\`awaitedPlayers\` includes the asked granters.` becomes `\`awaitedPlayers\` is unchanged, since it hands the parked decision to everyone it names; the prompt is its own field. The requester's \`walkerDecision\` is withheld while their request waits, and nobody is offered Redo.`
8. Under "Game Log", `\`LogEntry\` gains \`visibleTo: Option[PlayerId]\`; the formatter drops such an entry for every other viewer. Private lines:` becomes `The formatter writes a private line only when it formats for that line's granter. Private lines:`
9. Under "Frontend", add a bullet: `The confirmation and the grid open in the card overlay, the client's one document-level dialog. Site powers carry their power id, which the grid is matched by.`
10. Under "Testing", `One browser acceptance run of the user story.` becomes `One browser acceptance run of the user story, once a game with a player-ruled Pass can be seeded (roadmap play-test item).`

- [ ] **Step 4: The vocabulary**

In `CONTEXT.md`, add a section after the Walker terms:

```markdown
### Consent

**Waivable power**:
A persistent power that restricts its ruler's enemies, whose ruler may
consent to a player ignoring it for one choice. A power worded "players" is
not one.
_Avoid_: optional power, consent power

**Granter**:
The player who may consent to a waivable power being ignored: the player who
rules it. Bandits, the Empire and nobody are never granters.
_Avoid_: ruler (the ruler may be bandits), owner

**Askable option**:
An option of a parked choose-one blocked only by waivable powers whose
granters are other players, with nothing refused for it this turn. Choosing it
asks the granters first.
_Avoid_: consent option, amber option

**Consent request**:
A player's ask, from an askable option, to the granters of the powers blocking
it. At most one waits at a time.
_Avoid_: consent prompt (that is what a granter sees)

**Consent answer**:
A granter's yes or no to a consent request, remembered for the rest of the
turn for its power, requester, decision and option.
_Avoid_: consent, permission

**Consent preference**:
A granter's private standing answer for one requester on one power: never,
always, or case by case (no entry).
_Avoid_: permission, auto-consent

**Consent ledger**:
The preferences, the turn's answers and the waiting request, kept beside the
game so that undo leaves them be.
_Avoid_: consent state (the type's name, not the concept)
```

- [ ] **Step 5: Run the full gate**

Run, in order. Each must pass before the next:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `./sbtw coverage test coverageReport`, which must meet the build's statement and branch minimums. If the consent code leaves a branch untested and the total drops below the minimum, add the missing case to the suite that owns that code. Never lower the minimum.
4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
5. `git diff --check`

- [ ] **Step 6: Commit**

```bash
git add docs/rules/rules-reference.md docs/rules/implementation-traceability.md docs/rules/ambiguities.md docs/ROADMAP.md docs/superpowers/specs/2026-10-08-consent-system-design.md CONTEXT.md
git commit -m "docs(consent): record slice 1 in the rules docs, the roadmap, the spec and the vocabulary"
```
