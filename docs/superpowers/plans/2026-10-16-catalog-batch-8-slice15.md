# Catalog Batch 8, Slice 15 (Palanquin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 107 Palanquin: "[favor] ACTION: Choose a player whose pawn is at your site. Put your pawn on a site that they can travel to. Make them travel to that site, spending no Supply."

**Architecture:**

- **A Travel sent to a site.**
  - A queued Travel `Run` with a source may carry its destination as its one start argument. Its walk asks no destination question.
  - Its queue entry names its `sender`, the player whose power sent the traveller. A waivable restriction the sender could waive does not block it.
  - The queued destination counts as the traveller's answer to the destination decision. A grant of that answer lets a waivable restriction through, as it does for a destination the traveller picked.
- **The consent a queued start waits on.** `QueuedStarts.consent` tells a phase power whether an entry starts, is refused, or waits only on waivable restrictions. In the last case it names each grant and the key the started walk looks it up under.
- **Consent from either of several players.** A consent ask may be an alternative.
  - A request settles granted when every required ask grants and, if it has alternatives, one of them grants.
  - It settles refused when a required ask refuses, or every alternative does.
  - The requester may ask one alternative or all of them. The client offers "Ask Blue", "Ask Red" and "Ask both".
- **Palanquin** is a `PaidAction`. It asks the target, then the destination, then (when the user rules Toll Roads) the toll, then places the user's pawn and queues the target's Travel at the front.
  - A destination that needs consent is askable. The power builds that ask itself: the grants `QueuedStarts.consent` names, plus Toll Roads' two alternatives.
  - The walker keeps a decision whose only remaining options are askable ones its builder declared.
- **Toll Roads in a sent Travel** asks nothing. The target pays if they agreed to; otherwise the toll was waived.

**Tech Stack:** Scala 3 on the JVM, Scala.js frontend, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "The bar" (the slice 15 bullet), "Slice 15: Palanquin", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 15 entry. Task 7 replaces it with this plan's rulings.
- `docs/superpowers/specs/2026-10-08-consent-system-design.md`, for requests, asks, keys, covers, preferences and settling.
- `docs/architecture/follow-up-actions.md`, for the queue, `Run` entries and the Supply waiver.
- These restate what this slice builds on: `CampaignFromSite.scala` (a power that queues a `Run` and previews it), `TollRoads.scala`, `PayOrAsk.scala`, `WalkerPowerGather.scala` (`waived`, `consentAsk`), `QueuedStart.scala` and `ConsentDesk.scala`.

**Starts from:** `main` at `18d4946a` or later, with slice 14 merged. Before Task 1, check that `CurrentGameState.walkActor` exists and that `OffTurnWalkSuite` passes. If either is missing, stop and ask. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Rulings

The product owner settled these on 2026-10-16. They replace the slice 15 entry of the rulings appendix.

1. **The target** is another player whose pawn is at the user's site. The user cannot choose themselves.
2. **Destinations.**
   - Every site but the user's, in map order. Each is judged by previewing the target's queued Travel.
   - A site that Travel refuses is shown unavailable with the reason: "Cannot travel to other sites in the region" for a Narrow Pass, the start's own text when it has one, else "Cannot travel there now".
3. **The fixed destination.** The target's Travel goes to the chosen site and asks no destination question. Its modifiers, costs and after-travel powers are the target's: Hospitality, Tyrant, Brass Army and Grasping Vines apply. Only the Supply is waived.
4. **Order.**
   1. The target.
   2. The destination.
   3. The toll question, when the user rules Toll Roads (ruling 10).
   4. The user's pawn is placed at the destination. This is a placement, not a Travel, so Brass Army asks the user right away.
   5. The target's Travel is queued as a `Run` at the front, so it runs before any other queued entry.
5. **When it is usable.**
   - A target who can reach no site, directly or by consent, is shown unavailable with "Cannot travel anywhere now".
   - With no target who can reach a site, the Act menu shows "No valid enemy pawn".
   - Ward of Silence shows "No valid enemy pawn" when nobody at the site has a faceup secret. It keeps "Nothing to do now" when its card cannot take a secret.
6. **The sender.**
   - A queued walk names the player whose power sent it.
   - A restriction that would block the walk, and that the sender could waive, does not block it: a Narrow Pass the user rules lets the target through.
   - A pay-or-ask step inside the walk is still the traveller's to answer (Grasping Vines).
7. **Waivable restrictions on the target's Travel** make the destination askable. The asks go to the restrictions' granters, on the user's behalf.
   - Today that means Narrow Pass, and any later tree-level waivable restriction on a Travel.
   - The grant is remembered under the key the target's own Travel would check: the target's answer of that site to the destination decision.
8. **Toll Roads ruled by a player other than the user.**
   - A destination where Toll Roads would charge the target is askable two ways: the target agrees to pay, or the ruler waives the toll.
   - The requester may ask either player, or both.
   - With no favor, the target cannot be asked, and only the ruler's ask remains.
   - A refusal stays for the turn, as every refusal does.
9. **Toll Roads ruled by bandits.** Only the target is asked, to burn 1 favor. With no favor, the destination is unavailable: "{Blue} has no favor for it."
10. **The user rules Toll Roads.**
    - The destination is available.
    - A question owned by the user follows the pick. Its options are "Take 1 favor", which needs the target's consent, and "Waive the toll".
    - Option labels cannot name a player in the engine, so the labels do not name the target. The ask's hover does.
11. **The toll inside the sent Travel.** No toll question is asked. If the target agreed, they pay. Otherwise the toll was waived, and the existing line reads "{Blue} paid nothing, with {Red}'s consent."
12. **One granter, two grants.** When a player asked for a restriction's waiver also rules the Toll Roads that charges the target, their one answer covers both, and the target is not asked.
13. **Settling an either-or request.**
    - The first alternative to grant settles the alternatives, and the other prompts close.
    - An alternative's refusal leaves the others open.
    - The request is refused once every alternative refuses.
    - A required ask must grant as before, and its refusal settles the whole request.
14. **Wording.**
    - Hovers carry the card's name, as every power hover does, followed by the text:
      - "Toll Roads: {Blue} must agree to give 1 favor to {Red}, or {Red} must waive it."
      - "Toll Roads: {Red} must waive it." when the target has no favor.
      - "Toll Roads: {Blue} must agree to give 1 favor to {Red}." after the ruler refused.
      - "Toll Roads: {Blue} must agree to burn 1 favor." under bandits.
    - Refusals read "{Blue} refused.", "{Red} refused to waive it." or "{Blue} refused, and {Red} refused to waive it."
    - The target's prompt reads "{Green} wants to send you to {Coast}. Consent to pay Toll Roads and travel to {Coast}?"
    - The ruler's prompt keeps the fixed template: "Give {Green} consent to ignore Toll Roads and select {Coast}?"
    - Log: "Asked {Blue} for consent to pay Toll Roads and travel to {Coast}." for the target. The existing ignore line is kept for every other ask. Answers keep "{Blue} gave consent." and "gave no consent."
15. **Preferences.** The ruler's Toll Roads preference applies to their ask. The target's agreement belongs to no card's ruler, so no preference applies to it, and the target is always asked by hand.
16. **The Game Log.**
    - Palanquin's run: "Moved to {Coast}; {Blue} travels there."
    - The target's run is tagged "{Blue} · Travel · Palanquin", with "Travelled to {Coast}" and its own lines.
17. **Client.**
    - An askable option with alternatives opens a choice: one button per alternative, then "Ask both" (or "Ask all" for three or more), and Cancel.
    - The requester's waiting text reads "Waiting for {Blue}'s or {Red}'s consent…" while only alternatives are open.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `LogScripts.scala` is at its limit: new journaled scripts go in a new file beside it, as `CaptainsLogScripts.scala` does. `ProcedureWalker.scala`, `WalkerPowerGather.scala` and `TollRoads.scala` may be near it. Check with `wc -l` before adding, and split a cohesive helper object out if a file would pass 800.
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - Engine files under `gameplay/walker` and `gameplay/operations` never name a power, in code or comments.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** The new wire fields (`QueuedProcedure.sender`, `ConsentAsk.alternative`, `RequestConsent`'s granters) are written only when set, matching the codecs' style.
- **Hover reasons.** An unusable option stays listed, unavailable with its reason. It is never hidden.
- **Log lines omit the actor.** Only a non-actor, or an actor off turn, is named.
- **Lessons from earlier slices' tests:**
  - `TravelFixture.board()` has p1 (the `actor`) at `cradlePlains` (Buried Giant), p2 at `provincePlains` (Dunes) and p3 at `mountain`, in p1's Act, with every site holding its printed bandits. The Narrow Pass (`pass`) holds bandits; `warbandsAt(pass, p, 1)` hands it to `p`. The Pass blocks Dunes from the Cradle's plains.
  - `TollConsentFixture.tolled` puts Toll Roads at `provincePlains` and has p2 rule it and `coast`.
  - The quiet table (`Table.start`) has p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant, in p1's Act.
  - To read a parked decision, use `ParkedNode.of(state, catalog, ...)` and match `ParkedNode.Decision(_, decide, _)`, as `WardOfSilenceSuite.parkedOptions` and `TravelFixture.parkedDecision` do. Pass both the walker and the phase power catalogs.
  - `ActPowerHarness.rulesWithPrintedRestrictions()` has every production walker and phase power. `ActPowerHarness.use` and `answer` act as p1.
  - A consent answer is remembered with `ConsentState(answers = Map(key -> ConsentAnswer.Granted.by(p)), turn = Some(ConsentTurn.of(ready)))`, as `NarrowPassConsentSuite.remembering` does.
- **Note keys** are declared through a `NoteSet`. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"` when a task adds keys (Tasks 2 and 5).
- **The unimplemented-powers snapshot** is `src/test/resources/powers/unimplemented.txt`. Regenerate it with `UNIMPLEMENTED_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite"`, and check that the diff drops only `denizen.palanquin`.
- **Names, exactly:**
  - `QueuedProcedure.sender: Option[PlayerId]` (last field, default `None`) in `model/GameState.scala`; wire field `senderId`.
  - `TravelProcedure.queuedDestination(state, actor): Option[SiteId]` and `TravelProcedure.queuedAnswers(state): Vector[Answered]`.
  - `WalkerProcedureRegistry.queuedAnswers(state): Vector[Answered]`.
  - `QueuedStarts.consent(ready, entry): QueuedStarts.Verdict`, with `QueuedStarts.Verdict.{Starts, Waiting(needed), Refused(violation)}` and `QueuedStarts.Needed(power, granter, key, asked, plain)`.
  - `QueuedStart.consent(catalog, ready, entry, powers, phasePowers)` and `WalkerPowerGather.waivableBreaches(...)` with `WalkerPowerGather.WaivableBreach`.
  - `ConsentAsk.alternative: Boolean` (default `false`); `ConsentRequest.required`, `.alternatives`, `.granted`, `.refused`, `.keysOf(ask)`.
  - `GameCommand.RequestConsent(requester, decisionId, target, granters: Vector[PlayerId] = Vector.empty)` and `GameIntent.RequestConsent(decisionId, target, granters: Vector[String] = Vector.empty)`; wire field `granters`.
  - `ConsentAskProjection(granterPlayerIds, powers, alternatives: Vector[ConsentAlternativeProjection] = Vector.empty)`, `ConsentAlternativeProjection(granterPlayerId, powers, paying)`, `ConsentWaitingProjection(granterPlayerIds, either: Boolean = false)` and `ConsentPromptProjection(requesterPlayerId, powers, target, paying: Boolean = false)`.
  - `ConsentPowers.paying(power): Boolean`.
  - `TollRoads.payerId` (`PowerId("denizen.toll-roads.payer")`), `TollRoads.payerKey`, `TollRoads.freeKey`, `TollRoads.payerWaiver`, `TollRoads.owed`, `TollRoads.sending` and `TollRoads.Sending`.
  - `Palanquin` (a `case object`) and `PalanquinCard`, both in `gameplay/powers/action/Palanquin.scala`. The card object moves out of `catalog/holding/HoldingOrderDenizens.scala`. Decision ids `power.palanquin.target`, `power.palanquin.destination` and `power.palanquin.toll`.
  - `PawnMoves.noValidEnemyPawn` (`"No valid enemy pawn"`).
  - Suites: `SentTravelSuite` (Task 1), `PalanquinSuite` (Task 2), `QueuedStartConsentSuite` (Task 3), `PalanquinConsentSuite` and `PalanquinConsentServiceSuite` (Task 5), `PalanquinLogScripts` and `GameLogPalanquinSuite` (Task 6).

---

### Task 1: A Travel sent to a site

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`QueuedProcedure` and its doc)
- Modify: `src/main/scala/oathdigital/serialization/FollowUpWire.scala` (encode and decode `senderId`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (`queuedAnswers`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (`waived`)
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/SentTravelSuite.scala`

**Interfaces:**
- Produces: `QueuedProcedure.sender`, `TravelProcedure.queuedDestination`, `TravelProcedure.queuedAnswers` and `WalkerProcedureRegistry.queuedAnswers(state)`. Tasks 2, 3 and 5 rely on a queued Travel `Run` with `Vector(Site(dest))` start args walking to `dest` for its actor, with its sender's waivable restrictions lifted.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, QueuedStart}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** A Travel a power sends a player on: to the site its entry names, with
  * no destination question, and past the sender's own waivable
  * restrictions (catalog batch 8, slice 15, rulings 3 and 6). */
class SentTravelSuite extends munit.FunSuite:
  import TravelFixture._

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val palanquin = PowerSourceRef.Card(DenizenId("107"))
  private val dunes = DecisionOptionRef.Site(provincePlains)

  /** p2's Travel to `site`, sent by p1 with Palanquin as its source. */
  private def p2SentTo(site: SiteId): QueuedProcedure =
    QueuedProcedure(p2, ActionRef.Travel, Vector(DecisionOptionRef.Site(site)),
      Some(palanquin), QueueKind.Run, sender = Some(p1))

  /** The board with p2 beside p1 at the Cradle's plains. The Narrow Pass is
    * held by `passHolder`'s warband, or by its bandits without one. */
  private def p2BesideP1(passHolder: Option[PlayerId] = None): ReadyGame =
    on(board())(table => passHolder.fold(table)(holder =>
      table.warbandsAt(pass, holder, 1)).pawn(p2, cradlePlains))

  /** `entry` started on `ready`, as the driver starts a dequeued `Run`. */
  private def startedFromTheQueue(ready: ReadyGame, entry: QueuedProcedure)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(QueuedStart.startingFrom(ready, entry)),
      entry.procedure, entry.actor, entry.startArgs)

  private def startRefusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation] =
    QueuedStart.refusal(catalog, ready, entry, powers(Vector.empty),
      PhasePowerCatalog.default(catalog))

  /** `ready` remembering p3's grant of Dunes as p2's destination. */
  private def p3GrantedDunesToP2(ready: ReadyGame): ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(NarrowPassSite.pass, p2,
        TravelProcedure.destinationDecisionId, dunes) ->
        ConsentAnswer.Granted.by(p3)),
      turn = Some(ConsentTurn.of(ready))))

  test("a sent Travel goes to its site with no destination question, for " +
      "no Supply"):
    val ready = p2BesideP1()
    val done = accepted(startedFromTheQueue(ready, p2SentTo(coast)))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.pawn(p2), coast)
    assertEquals(after.supply(p2), Look(ready).supply(p2))
    assertEquals(after.active, p1)

  test("a site start argument without a matching queued entry is refused"):
    val refused = rejected(rules.startWalker(Ready(p2BesideP1()),
      ActionRef.Travel, p1, Vector(DecisionOptionRef.Site(coast))))
    assert(refused.isInstanceOf[OathViolation.InvalidEventOrder],
      refused.toString)

  test("a bandit-held Narrow Pass refuses a sent Travel into its region"):
    assert(startRefusal(p2BesideP1(), p2SentTo(provincePlains)).nonEmpty)

  test("the Pass ruler's grant of the queued destination lets it through"):
    val ready = p2BesideP1(passHolder = Some(p3))
    assert(startRefusal(ready, p2SentTo(provincePlains)).nonEmpty)
    val granted = p3GrantedDunesToP2(ready)
    assertEquals(startRefusal(granted, p2SentTo(provincePlains)), None)
    val done = accepted(startedFromTheQueue(granted, p2SentTo(provincePlains)))
    assertEquals(Look(Outcomes.ready(done.state)).pawn(p2), provincePlains)

  test("the sender's own Narrow Pass does not block it, another's does"):
    val ready = p2BesideP1(passHolder = Some(p1))
    assertEquals(startRefusal(ready, p2SentTo(provincePlains)), None)
    assert(startRefusal(ready,
      p2SentTo(provincePlains).copy(sender = None)).nonEmpty)

  test("a sent entry's sender survives the wire"):
    assertWireRoundTrips(Vector(ProcedureDequeued(p2SentTo(coast))))
```

`ProcedureDequeued` is in `gameplay/walker/WalkerEvents.scala`. If `assertWireRoundTrips` needs a different event type, round-trip whatever event `FollowUpWire` encodes a queue entry for (for example a queue event).

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.SentTravelSuite"`
Expected: compile failure on `sender`.

- [ ] **Step 3: Add the sender**

In `model/GameState.scala`:

```scala
/** A procedure the walker runs as its own walk once the current one ends
  * (walker-driven game loop design, "The queue"). `actor` owns the walk
  * (`CurrentGameState.walkActor`), and may be off turn. `startArgs` are what
  * a start would carry. `source` is the power that queued it, which the
  * Game Log credits; an entry with a source is a follow-up, whose walk
  * spends no Supply. `sender` is the player whose power sent `actor` on it
  * (catalog batch 8, slice 15): a restriction they could waive does not
  * block the walk. A `Run` starts at once; an `Offer` asks its actor first,
  * with Skip. */
final case class QueuedProcedure(actor: PlayerId, procedure: StartableRef,
    startArgs: Vector[DecisionOptionRef], source: Option[PowerSourceRef],
    kind: QueueKind, sender: Option[PlayerId] = None)
```

In `FollowUpWire.scala`, the encoder appends `"senderId" -> sender.value` when `sender` is set, as it does `source`. The decoder reads `value.obj.get("senderId").map(raw => PlayerId(raw.str))` and passes it as the new last argument.

- [ ] **Step 4: Accept and use a queued destination**

In `TravelProcedure.scala`, replace `noSelection` with a check that accepts a queued destination, and walk to it without asking:

```scala
  /** The destination a power's queued Travel names, while that Travel's
    * walk runs for `actor` (catalog batch 8, slice 15): its one start
    * argument, a site. Only a `Run` entry with a source carries one; a menu
    * Travel never does. */
  def queuedDestination(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.walkerEntry.filter(entry =>
      entry.procedure == ActionRef.Travel && entry.actor == actor &&
        entry.source.nonEmpty && entry.kind == QueueKind.Run)
      .flatMap(_.startArgs match
        case Vector(DecisionOptionRef.Site(site)) => Some(site)
        case _ => None)

  /** The queued destination as its actor's answer to the destination
    * decision, which the walk does not ask. A waivable restriction finds a
    * grant under it as it finds one under a destination the actor picked. */
  def queuedAnswers(state: ReadyGame): Vector[Answered] =
    state.game.current.walkerEntry.toVector.flatMap(entry =>
      queuedDestination(state, entry.actor).map(site => Answered(
        destinationDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)),
        entry.actor)))

  private def startArgs(state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Unit] =
    args match
      case Vector() => Right(())
      case Vector(DecisionOptionRef.Site(site))
          if queuedDestination(state, actor).contains(site) => Right(())
      case _ => Left(OathViolation.InvalidEventOrder(
        "travel takes no start selection but a power's queued destination"))
```

`build` and `rebuild` call `startArgs(state, activePlayer, args)` where they called `noSelection(args)`. `journey` walks the queued destination when there is one:

```scala
  /** The destination pick and the route to it, or only the route to a
    * queued destination. */
  private def journey(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    queuedDestination(state, actor).fold(picked(catalog, state, actor,
      source))(destination => route(catalog, actor, source, destination))

  private def picked(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val pick = Decide(destinationDecisionId, actor, DecisionQuery.ChooseOne(
      state.game.current.map.inPlay.filter(_ != source).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading), confirmVerb = Some("Travel to")))
    val chosen = Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`destinationDecisionId`,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) =>
        site
    }.toVector.map(route(catalog, actor, source, _)))
    Sequence(Vector(pick, chosen))

  /** The route to `destination`, priced on the state it is reached in. The
    * windows sit inside its `Branch`, as the class doc says. */
  private def route(catalog: ExecutableCatalog, actor: PlayerId,
      source: SiteId, destination: SiteId): Operation =
    Branch((current, _) => Vector(TravelRules.cost(catalog, current, source,
      destination).fold(error => BuildOps((_, _) => Left(error)),
      base => routeTree(actor, source, destination, base))))
```

The nested `Branch` keeps every Travel power's view: a window flattens to the `Move` inside `routeTree`. Update the class doc's "The start carries no selection." to say that a power's queued Travel may carry its destination, and then asks nothing.

In `WalkerProcedureRegistry.scala`, add:

```scala
  /** The answers a queued entry's start arguments stand for, by the
    * procedure the open walk's entry names (catalog batch 8, slice 15):
    * a queued Travel's destination. */
  def queuedAnswers(state: ReadyGame): Vector[Answered] =
    state.game.current.walkerEntry.map(_.procedure) match
      case Some(ActionRef.Travel) => TravelProcedure.queuedAnswers(state)
      case _ => Vector.empty
```

- [ ] **Step 5: Lift the sender's restrictions and read the queued answer**

In `WalkerPowerGather.waived`, a restriction is waived when its granter is the open walk's sender, and its grant may sit under a queued answer:

```scala
  private def waived(power: PowerId, restriction: Restriction,
      ctx: PowerCtx): Boolean =
    restriction.waiver.exists { waiver =>
      val turn = ConsentTurn.of(ctx.state)
      val granter = waiver.granter(ctx.state)
      val sent = granter.exists(by => ctx.state.game.current.walkerEntry
        .exists(_.sender.contains(by)))
      sent || (ctx.answered ++ WalkerProcedureRegistry.queuedAnswers(
          ctx.state)).exists:
        case Answered(decisionId, DecisionAnswer.ChooseOneAnswer(target),
            by) =>
          ctx.state.consent.answer(ConsentKey(power, by, decisionId,
            target), granter, turn).contains(ConsentAnswer.Granted)
        case _ => false
    }
```

Update its doc: the sender's own waivable restrictions do not block a walk sent by them, and a queued entry's start arguments count as answers.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.SentTravelSuite"`
Expected: PASS.

If "a bandit-held Narrow Pass refuses a sent Travel" fails because `QueuedStart.refusal` accepts the start, the preview does not run the tree-level restriction check that `startNow` runs (`checkRestrictions`). Add the same check to `QueuedStart.priced`, on the `startingFrom` state, before the simulation, and run again.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.*" "testOnly oathdigital.gameplay.OffTurnWalkSuite" "testOnly oathdigital.serialization.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/serialization/FollowUpWire.scala \
  src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/SentTravelSuite.scala
git commit -m "feat(travel): a queued Travel goes to the site its sender named"
```

Add `QueuedStart.scala` to the list if Step 6 changed it.

---

### Task 2: Palanquin, and Ward of Silence's reason

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Palanquin.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `PalanquinCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala` (register `Palanquin`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala` (`noValidEnemyPawn`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala` (`usable`, `refusal`, doc)
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/WardOfSilenceSuite.scala`
- Modify: `src/test/resources/powers/unimplemented.txt`, `src/test/resources/notes/declared-keys.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/PalanquinSuite.scala`

**Interfaces:**
- Consumes: Task 1's sent Travel.
- Produces: `Palanquin.entry(user, target, site): QueuedProcedure`, `Palanquin.targetOptions(ready, user, starts)`, `Palanquin.destinationOptions(ready, user, target, starts)`, the decision ids and `Palanquin.moved`. Task 5 rewrites `destinationOptions` and adds the toll question.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.travel.{BrassArmyMoves, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Palanquin: the user is placed where the target could travel, and the
  * target travels there, off turn and for no Supply (catalog batch 8,
  * slice 15). */
class PalanquinSuite extends munit.FunSuite:
  import TravelFixture.{board, coast, cradlePlains, on, provincePlains}

  private val rules = ActPowerHarness.rulesWithPrintedRestrictions()
  private val source = DecisionOptionRef.Denizen(PalanquinCard.id)

  /** TravelFixture's board: p1 at the Cradle's plains holds Palanquin and
    * 1 favor, and p2 stands beside p1. p3 stands at the mountain. */
  private def palanquinWithP2Beside(steps: Table => Table = identity)
      : ReadyGame =
    on(board())(table => steps(table.adviser(p1, PalanquinCard.id)
      .favor(p1, 1).pawn(p2, cradlePlains)))

  /** p1 uses Palanquin, aims it at `target` and picks `site`, with every
    * command's events. */
  private def sent(ready: ReadyGame, target: PlayerId, site: SiteId)
      : OathTransition =
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    val aimed = accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(target)))
    val done = accepted(ActPowerHarness.answer(rules, aimed.state,
      Palanquin.destinationDecisionId, DecisionOptionRef.Site(site)))
    done.copy(events = used.events ++ aimed.events ++ done.events)

  private def parkedDecide(state: OathState): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def options(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  /** Why the Act menu shows Palanquin unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  private def dequeued(events: Vector[OathEvent]): Vector[QueuedProcedure] =
    events.collect { case ProcedureDequeued(entry) => entry }

  test("it offers only the other players at the user's site"):
    val used = accepted(ActPowerHarness.use(rules, palanquinWithP2Beside(),
      Palanquin.id, source))
    val decide = parkedDecide(used.state)
    assertEquals(decide.decisionId, Palanquin.targetDecisionId)
    assertEquals(options(decide).map(_.ref),
      Vector(DecisionOptionRef.Player(p2)))

  test("with no other player at the site, the menu says why"):
    val alone = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1))
    assertEquals(menuReason(alone),
      Some(OptionReason.Written(PawnMoves.noValidEnemyPawn)))

  test("the user is placed there, and the target travels there off turn " +
      "for no Supply"):
    val ready = palanquinWithP2Beside()
    val done = sent(ready, p2, coast)
    val result = Outcomes.ready(done.state)
    val after = Look(result)
    assertEquals(after.pawn(p1), coast)
    assertEquals(after.pawn(p2), coast)
    assertEquals(after.supply(p2), Look(ready).supply(p2))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.active, p1)
    assertEquals(after.phase, Phase.Act)
    assertEquals(dequeued(done.events),
      Vector(Palanquin.entry(p1, p2, coast)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("a destination the target cannot reach is unavailable with why"):
    val aimed = accepted(ActPowerHarness.answer(rules,
      accepted(ActPowerHarness.use(rules, palanquinWithP2Beside(),
        Palanquin.id, source)).state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p2)))
    // The Pass's bandits block Dunes from the Cradle's plains.
    assertEquals(options(parkedDecide(aimed.state))
      .find(_.ref == DecisionOptionRef.Site(provincePlains)),
      Some(DecisionOption.Unavailable(
        DecisionOption.Site(DecisionOptionRef.Site(provincePlains)),
        OptionReason.Written("Cannot travel to other sites in the region"))))

  test("a target who can reach no site is unavailable with why"):
    val refusingEvery: QueuedStarts = (_, _) =>
      Some(OathViolation.InvalidEventOrder("refused"))
    assertEquals(Palanquin.targetOptions(palanquinWithP2Beside(), p1,
      refusingEvery), Vector(DecisionOption.Unavailable(
        DecisionOption.Player(DecisionOptionRef.Player(p2)),
        OptionReason.Written(Palanquin.noDestination))))

  test("Brass Army asks the user right after the placement, before the " +
      "target travels"):
    val ready = palanquinWithP2Beside(_.relic(p1,
      CatalogNames.relic("Brass Army")).secrets(p1, faceUp = 1))
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    val aimed = accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p2)))
    val placed = accepted(ActPowerHarness.answer(rules, aimed.state,
      Palanquin.destinationDecisionId, DecisionOptionRef.Site(coast)))
    val decide = parkedDecide(placed.state)
    assertEquals(decide.decisionId, BrassArmyMoves.decisionId)
    assertEquals(decide.owner, p1)
    assertEquals(Look(Outcomes.ready(placed.state)).pawn(p2), cradlePlains)

  test("Brass Army asks the target after their own move"):
    val ready = palanquinWithP2Beside(_.relic(p2,
      CatalogNames.relic("Brass Army")).secrets(p2, faceUp = 1))
    val decide = parkedDecide(sent(ready, p2, coast).state)
    assertEquals(decide.decisionId, BrassArmyMoves.decisionId)
    assertEquals(decide.owner, p2)

  test("Hospitality pays the target"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Hospitality"))
      .adviser(p2, CatalogNames.denizen("Messenger"))
      .denizen(CatalogNames.denizen("Siege Engines"), coast)
      .banksHoldingOnly(Suit.Order -> 2))
    val after = Look(Outcomes.ready(sent(ready, p2, coast).state))
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)

  test("Tyrant acts for the target off turn"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Tyrant")).bandits(coast, 2))
    assertEquals(Look(Outcomes.ready(sent(ready, p2, coast).state))
      .forces(coast), SiteForces.Occupied(ForceKind.Bandit, 1))

  test("the target picks their own Travel modifiers"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Special Envoy")))
    val decide = parkedDecide(sent(ready, p2, coast).state)
    assertEquals(decide.decisionId, TravelFixture.modifierDecisionId)
    assertEquals(decide.owner, p2)
```

Check the `ParkedNode.of` parameter names and the `Table.relic` and `adviser` signatures against `testkit`, and adjust the calls. If Hospitality asks a bank here, change the bank stocks so one bank matches, as `HospitalitySuite` does.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PalanquinSuite"`
Expected: compile failure on `Palanquin`.

- [ ] **Step 3: Write Palanquin**

Remove `PalanquinCard` from `HoldingOrderDenizens.scala`, dropping any import left unused. `NewFoundations.scala` already imports `gameplay.powers.action._`, so it finds the moved card.

Create `gameplay/powers/action/Palanquin.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object PalanquinCard extends Denizen(DenizenId("107"), "Palanquin",
    Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.palanquin"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. Put " +
      "your pawn on a site that they can travel to. Make them " +
      "travel to that site, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Palanquin (card 107, catalog batch 8, slice 15): the user chooses
  * another player at their site, and a site that player's Travel would
  * reach, previewed through `QueuedStarts`. The user's pawn is placed
  * there, then the target's Travel to it is queued as a `Run` at the front,
  * with Palanquin as its source and the user as its sender. The target
  * walks it off turn, for no Supply, and owns its decisions.
  *
  * A target with no reachable site is unavailable with `noDestination`; a
  * site the Travel refuses, with why. With no target who can reach a site,
  * the ACTION is unavailable with "No valid enemy pawn".
  *
  * Its line: "Moved to {Coast}; {Blue} travels there."
  */
case object Palanquin extends PaidAction(PalanquinCard.power):
  val cardId: DenizenId = PalanquinCard.id
  val targetDecisionId: String = "power.palanquin.target"
  val destinationDecisionId: String = "power.palanquin.destination"
  val noDestination: String = "Cannot travel anywhere now"
  private val notes = NoteSet(id)
  /** "Moved to {Coast}; {Blue} travels there." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0), NotePart.Text("; "),
    NotePart.Arg(1), NotePart.Text(" travels there.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(targetDecisionId, destinationDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    build(ready, player, source, QueuedStarts.unchecked)

  override def build(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(targetDecisionId, player,
      DecisionQuery.ChooseOne(targetOptions(live, player, starts),
        heading = Some("Palanquin: choose a player at your site"))))),
    Branch((live, pending) => chosenTarget(pending).toVector.map(target =>
      Decide(destinationDecisionId, player, DecisionQuery.ChooseOne(
        destinationOptions(live, player, target, starts),
        heading = Some("Palanquin: choose where you both go"),
        confirmVerb = Some("Go to"))))),
    BuildOps((live, pending) => sent(live, player, pending)),
    Note(id, states => for
      site <- NoteSupport.answer(states, destinationDecisionId).collect {
        case DecisionOptionRef.Site(site) => site }
      target <- NoteSupport.answer(states, targetDecisionId).collect {
        case DecisionOptionRef.Player(target) => target }
    yield moved(PowerSourceRef.Card(cardId), NoteArg.Site(site),
      NoteArg.Player(target))))))

  override def refusal(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts): Option[String] =
    Option.unless(targetOptions(ready, player, starts).exists(_.available))(
      PawnMoves.noValidEnemyPawn)

  /** The target's queued Travel to `site`, sent by `user`. */
  def entry(user: PlayerId, target: PlayerId, site: SiteId)
      : QueuedProcedure =
    QueuedProcedure(target, ActionRef.Travel,
      Vector(DecisionOptionRef.Site(site)),
      Some(PowerSourceRef.Card(cardId)), QueueKind.Run, sender = Some(user))

  /** The other players at `user`'s site, one who can reach no site
    * unavailable. */
  def targetOptions(ready: ReadyGame, user: PlayerId, starts: QueuedStarts)
      : Vector[DecisionOption] = targets(ready, user).map { target =>
    val shown = DecisionOption.Player(DecisionOptionRef.Player(target))
    if destinationOptions(ready, user, target, starts).exists(reachable)
    then shown
    else DecisionOption.Unavailable(shown,
      OptionReason.Written(noDestination))
  }

  /** Every site but `user`'s, one the target's Travel would not reach
    * unavailable with why. */
  def destinationOptions(ready: ReadyGame, user: PlayerId, target: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    destinations(ready, user).map { site =>
      val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
      starts.refusal(ready, entry(user, target, site))
        .fold[DecisionOption](shown)(violation => DecisionOption.Unavailable(
          shown, OptionReason.Written(reason(violation))))
    }

  /** Why the target's Travel would not reach a site, in the player's
    * words. */
  def reason(violation: OathViolation): String = violation match
    case _: OathViolation.TravelPassBlocked =>
      "Cannot travel to other sites in the region"
    case OathViolation.StartUnavailable(text) => text
    case _ => "Cannot travel there now"

  /** Whether a destination option can be chosen, now or once asked. */
  private def reachable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case other => other.available

  private def targets(ready: ReadyGame, user: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, user).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != user &&
            other.pawnSite.contains(site) => other.player
      })

  private def destinations(ready: ReadyGame, user: PlayerId)
      : Vector[SiteId] = PowerAccess.pawnSite(ready, user).toVector
    .flatMap(PawnMoves.sitesOtherThan(ready, _))

  private def chosenTarget(pending: PendingTree): Option[PlayerId] =
    PowerAnswers.one(pending, targetDecisionId).collect {
      case DecisionOptionRef.Player(target) => target }

  /** The placement and the queued Travel, once both are answered. */
  private def sent(ready: ReadyGame, user: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[Operation]] =
    (chosenTarget(pending), PowerAnswers.one(pending,
        destinationDecisionId)) match
      case (Some(target), Some(DecisionOptionRef.Site(site))) =>
        PawnMoves.relocate(ready, user, site).map(moves => moves ++
          Vector[Operation](QueueProcedure(entry(user, target, site),
            atFront = true)))
      case _ => Right(Vector.empty)
```

Match `BuildOps`'s function type to what `WardOfSilence.ward` returns; if it expects `Vector[CoreOperation]`, widen as it does. Register `Palanquin` in `MovementPowers.scala`, after `BrassHorse`. Add to `PawnMoves`:

```scala
  /** The menu's reason when no other player at the site is a valid target
    * (catalog batch 8, slice 15): Palanquin and Ward of Silence. */
  val noValidEnemyPawn: String = "No valid enemy pawn"
```

- [ ] **Step 4: Give Ward of Silence the same reason**

In `WardOfSilence.scala`, `usable` checks only that the card could take a secret, and `refusal` gives the target reason:

```scala
  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    Costs.affordable(ready, player, Location.OnCard(cardId),
      Cost(secret = 1))

  override def refusal(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts): Option[String] =
    Option.unless(targets(ready, player).exists(faceUp(_) > 0))(
      PawnMoves.noValidEnemyPawn)
```

Update its doc: with nobody at the site holding a faceup secret, the Act menu reads "No valid enemy pawn"; when the card cannot take a secret, "Nothing to do now". In `WardOfSilenceSuite`, split "it is unavailable without a faceup secret, …":

```scala
  test("it is unavailable without a faceup secret of its own, or while " +
      "its card holds a secret"):
    val unusable = Vector(
      wardHeldWithP2AndP3AtTheSite(own = 0),
      Table.from(wardHeldWithP2AndP3AtTheSite()).tokens(ward, secrets = 1)
        .ready)
    unusable.foreach { ready =>
      assert(!usableNow(ready).exists(_.power.id == WardOfSilence.id))
      assertEquals(menuReason(ready),
        Some(OptionReason.Written("Nothing to do now")))
    }

  test("with nobody at the site holding a faceup secret, the menu says no " +
      "valid enemy pawn"):
    assertEquals(menuReason(wardHeldWithP2AndP3AtTheSite(targetFaceUp = 0)),
      Some(OptionReason.Written(PawnMoves.noValidEnemyPawn)))
```

- [ ] **Step 5: Regenerate the snapshots and run the tests**

Run: `UNIMPLEMENTED_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite"`, then `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`.
Expected: the unimplemented diff drops only `denizen.palanquin`. The note-key diff adds Palanquin's `used` key.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*" "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite" "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Palanquin.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala \
  src/test/scala/oathdigital/gameplay/powers/action/PalanquinSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/WardOfSilenceSuite.scala \
  src/test/resources/powers/unimplemented.txt \
  src/test/resources/notes/declared-keys.txt
git commit -m "feat(action): Palanquin sends a player at your site where you go"
```

---

### Task 3: The consent a queued start waits on

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala` (`QueuedStarts`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala` (`consent`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (`waivableBreaches`)
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` (`startsFor`)
- Create: `src/test/scala/oathdigital/gameplay/walker/QueuedStartConsentSuite.scala`

**Interfaces:**
- Consumes: Task 1's `WalkerProcedureRegistry.queuedAnswers` and the sender.
- Produces: `QueuedStarts.consent` and its `Verdict` and `Needed`. Task 5 reads them.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a queued start waits on when only waivable restrictions refuse it
  * (catalog batch 8, slice 15, ruling 7). */
class QueuedStartConsentSuite extends munit.FunSuite:
  import TravelFixture._

  private val dunes = DecisionOptionRef.Site(provincePlains)
  private val grantKey = ConsentKey(NarrowPassSite.pass, p2,
    TravelProcedure.destinationDecisionId, dunes)

  private def p2SentTo(site: SiteId): QueuedProcedure =
    QueuedProcedure(p2, ActionRef.Travel, Vector(DecisionOptionRef.Site(site)),
      Some(PowerSourceRef.Card(DenizenId("107"))), QueueKind.Run,
      sender = Some(p1))

  private def p2BesideP1WithThePassHeldBy(holder: Option[PlayerId])
      : ReadyGame =
    on(board())(table => holder.fold(table)(by =>
      table.warbandsAt(pass, by, 1)).pawn(p2, cradlePlains))

  private def verdict(ready: ReadyGame, entry: QueuedProcedure)
      : QueuedStarts.Verdict =
    QueuedStart.consent(catalog, ready, entry, powers(Vector.empty),
      PhasePowerCatalog.default(catalog))

  test("an open route starts"):
    assertEquals(verdict(p2BesideP1WithThePassHeldBy(None), p2SentTo(coast)),
      QueuedStarts.Verdict.Starts)

  test("a Pass a player rules waits on that player's grant, keyed as the " +
      "traveller's own destination"):
    verdict(p2BesideP1WithThePassHeldBy(Some(p3)), p2SentTo(provincePlains))
      match
        case QueuedStarts.Verdict.Waiting(Vector(needed)) =>
          assertEquals((needed.power, needed.granter, needed.key),
            (NarrowPassSite.pass, p3, grantKey))
        case other => fail(s"expected one grant to wait on, got $other")

  test("a Pass the bandits hold is refused"):
    assert(verdict(p2BesideP1WithThePassHeldBy(None),
      p2SentTo(provincePlains)).isInstanceOf[QueuedStarts.Verdict.Refused])

  test("once granted, it starts"):
    val ready = p2BesideP1WithThePassHeldBy(Some(p3))
    val granted = ready.copy(consent = ConsentState(
      answers = Map(grantKey -> ConsentAnswer.Granted.by(p3)),
      turn = Some(ConsentTurn.of(ready))))
    assertEquals(verdict(granted, p2SentTo(provincePlains)),
      QueuedStarts.Verdict.Starts)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.QueuedStartConsentSuite"`
Expected: compile failure on `QueuedStart.consent`.

- [ ] **Step 3: Add the verdict to `QueuedStarts`**

In `powerresolver/PhasePower.scala` (add `ConsentKey` and `PowerNote` to the model import):

```scala
trait QueuedStarts:
  def refusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation]

  /** Whether the entry would start, and when only waivable restrictions
    * refuse it, the grants that would let it start (catalog batch 8, slice
    * 15). By default what `refusal` says, with nothing to ask. */
  def consent(ready: ReadyGame, entry: QueuedProcedure)
      : QueuedStarts.Verdict =
    refusal(ready, entry).fold(QueuedStarts.Verdict.Starts)(
      QueuedStarts.Verdict.Refused(_))

object QueuedStarts:
  /** No engine behind it: every start is taken to be accepted. */
  val unchecked: QueuedStarts = (_, _) => None

  /** One grant a refused start waits on: `granter` consenting to `power`,
    * remembered under `key`, the key the started walk looks it up under.
    * `asked` is the reason while it may be asked; `plain` the reason once
    * it was refused. */
  final case class Needed(power: PowerId, granter: PlayerId,
      key: ConsentKey, asked: PowerNote, plain: Option[PowerNote])

  enum Verdict:
    case Starts
    case Waiting(needed: Vector[Needed])
    case Refused(violation: OathViolation)
```

`QueuedStarts` stays a single-method trait, so the `(_, _) => None` lambdas still compile.

- [ ] **Step 4: Find the waivable breaches**

In `WalkerPowerGather.scala`, beside `breaches`:

```scala
  /** A breach a grant would lift: its power, the player who may grant it,
    * and its reasons for the option asked about. */
  final case class WaivableBreach(power: PowerId, granter: PlayerId,
      asked: PowerNote, plain: Option[PowerNote])

  /** The tree-level restrictions that refuse `root` with `answered`, when
    * every one of them may be waived by a player (catalog batch 8, slice
    * 15). `None` when nothing refuses, or when any refusal has no granter.
    * `ref` is the option the grants would be asked about. */
  def waivableBreaches(root: Operation, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef],
      ref: DecisionOptionRef): Option[Vector[WaivableBreach]] =
    if !powers.hasRestrictions then None
    else
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      val found = attributed(root, powers.quiet, seen, activePlayer,
        answered)._1
      val waivable = found.map(breach => for
        waiver <- breach.restriction.waiver
        granter <- waiver.granter(breach.ctx.state)
      yield WaivableBreach(breach.power, granter,
        waiver.note(breach.ctx, ref), breach.restriction.note(breach.ctx, ref)))
      Option.when(found.nonEmpty && waivable.forall(_.nonEmpty))(
        waivable.flatten.distinctBy(_.power))
```

Read `attributed` first and match its argument order. `Attributed` carries `power`, `restriction`, `ctx` and `violation`, as `lookAheadNoted` uses them.

- [ ] **Step 5: Judge a queued start's consent**

In `QueuedStart.scala`:

```scala
  /** `refusal`, and for a start only waivable restrictions refuse, the
    * grants that would let it start (catalog batch 8, slice 15). Each grant
    * is keyed on the entry's queued answer, so the started walk finds it,
    * and the start is tried again with every one of them supposed, as the
    * look-ahead tries a grant before it offers to ask (consent design,
    * ruling 8). */
  def consent(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): QueuedStarts.Verdict =
    priced(catalog, ready, entry, powers, phasePowers) match
      case Right(_) => QueuedStarts.Verdict.Starts
      case Left(violation) =>
        val state = startingFrom(ready, entry)
        val waiting = for
          answer <- WalkerProcedureRegistry.queuedAnswers(state).headOption
          ref <- answer.answer match
            case DecisionAnswer.ChooseOneAnswer(ref) => Some(ref)
            case _ => None
          tree <- WalkerProcedureRegistry.build(entry.procedure, catalog,
            state, entry.actor, entry.startArgs, phasePowers,
            walkerPowers = powers).toOption
          breaches <- WalkerPowerGather.waivableBreaches(tree, state,
            entry.actor, powers, Vector(answer), Some(entry.procedure), ref)
          needed = breaches.map(breach => QueuedStarts.Needed(breach.power,
            breach.granter, ConsentKey(breach.power, answer.by,
              answer.decisionId, ref), breach.asked, breach.plain))
          supposed = ready.copy(consent = ready.consent.granting(
            needed.map(_.key), ConsentTurn.of(ready)))
          if priced(catalog, supposed, entry, powers, phasePowers).isRight
        yield QueuedStarts.Verdict.Waiting(needed)
        waiting.getOrElse(QueuedStarts.Verdict.Refused(violation))
```

Add the imports this needs (`QueuedStarts` from `powerresolver`). Read `Answered`'s field names in `model` and match them.

- [ ] **Step 6: Fill it in for phase powers**

In `PhasePowerProcedure.startsFor`, return a `QueuedStarts` whose `refusal` and `consent` are each kept for the life of the tree, keyed as `refusal` is keyed now:

```scala
  private def startsFor(catalog: ExecutableCatalog, phasePowers: PhasePowers,
      walkerPowers: WalkerPowers): QueuedStarts =
    val refusals = new java.util.concurrent.ConcurrentHashMap[ReadyGame,
      Option[OathViolation]]
    val verdicts = new java.util.concurrent.ConcurrentHashMap[ReadyGame,
      QueuedStarts.Verdict]
    def kept[A](known: java.util.concurrent.ConcurrentHashMap[ReadyGame, A],
        key: ReadyGame)(found: => A): A =
      Option(known.get(key)).getOrElse:
        val value = found
        known.put(key, value)
        value
    new QueuedStarts:
      def refusal(ready: ReadyGame, entry: QueuedProcedure)
          : Option[OathViolation] =
        kept(refusals, QueuedStart.startingFrom(ready, entry))(
          QueuedStart.refusal(catalog, ready, entry, walkerPowers,
            phasePowers))
      override def consent(ready: ReadyGame, entry: QueuedProcedure)
          : QueuedStarts.Verdict =
        kept(verdicts, QueuedStart.startingFrom(ready, entry))(
          QueuedStart.consent(catalog, ready, entry, walkerPowers,
            phasePowers))
```

Keep the existing doc and add one sentence: the consent verdict is kept the same way.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.QueuedStartConsentSuite" "testOnly oathdigital.gameplay.powers.action.*" "testOnly oathdigital.gameplay.walker.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala \
  src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala \
  src/test/scala/oathdigital/gameplay/walker/QueuedStartConsentSuite.scala
git commit -m "feat(walker): a queued start names the grants it waits on"
```

---

### Task 4: Consent from either of several players

**Files:**
- Modify: `src/main/scala/oathdigital/model/Consent.scala` (`ConsentAsk`, `ConsentRequest`, `ConsentState.requested`, `answered`, `grantedBy`)
- Modify: `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala` (`alternative`)
- Modify: `src/main/scala/oathdigital/application/ConsentDesk.scala` (`request`, `automatic`)
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `GameApplicationService.scala`, `Authorization.scala`, `GameIntentMapper.scala` (`granters`)
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala` (`granters`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala` and its codec `ConsentProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (the ask projection, about line 363)
- Modify: `src/main/scala/oathdigital/application/ConsentProjector.scala` (`consentWaiting`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ConsentAsking.scala`, `ConsentPanels.scala`, and `CardInspection.scala` if it has no multi-button dialog
- Test: `src/test/scala/oathdigital/model/ConsentStateSuite.scala`, `ConsentEventWireSuite`, `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala`, `ConsentPromptPanelSuite.scala`

**Interfaces:**
- Produces: alternative asks end to end. Task 5 builds an `OptionReason.Askable` whose asks are `ConsentAsk(..., alternative = true)`, and relies on the desk, the projection and the client handling them.

- [ ] **Step 1: Write the failing model tests**

Add to `ConsentStateSuite` (reuse its turn and player helpers; read the suite's top first):

```scala
  /** p1 asks p2 or p3 about Toll Roads' two keys on the coast. */
  private def eitherP2OrP3: ConsentRequest =
    val coast = DecisionOptionRef.Site(SiteId("coast"))
    val payer = PowerId("test.payer")
    val ruler = PowerId("test.ruler")
    ConsentRequest(p1, "test.destination", coast,
      Vector(ConsentAsk(p2, Vector(payer), alternative = true),
        ConsentAsk(p3, Vector(ruler), alternative = true)),
      covers = Vector(ConsentKey(payer, p1, "test.destination", coast),
        ConsentKey(ruler, p1, "test.destination", coast)))

  test("one alternative's grant settles the request, granting only its " +
      "keys"):
    val asked = present(ConsentState().requested(eitherP2OrP3).toOption,
      "the request")
    val done = present(asked.answered(p3, granted = true, now).toOption,
      "the answer")
    assertEquals(done.pending, None)
    val Vector(payerKey, rulerKey) = eitherP2OrP3.covers
    assertEquals(done.answer(rulerKey, Some(p3), now),
      Some(ConsentAnswer.Granted))
    assertEquals(done.answer(payerKey, Some(p2), now), None)

  test("one alternative's refusal leaves the others asked"):
    val asked = present(ConsentState().requested(eitherP2OrP3).toOption,
      "the request")
    val refused = present(asked.answered(p2, granted = false, now).toOption,
      "the answer")
    assertEquals(refused.pending.map(_.open.map(_.granter)), Some(Vector(p3)))
    assertEquals(refused.answer(eitherP2OrP3.covers.head, Some(p2), now),
      Some(ConsentAnswer.Refused))

  test("every alternative refused settles it refused"):
    val asked = present(ConsentState().requested(eitherP2OrP3).toOption,
      "the request")
    val done = for
      one <- asked.answered(p2, granted = false, now)
      both <- one.answered(p3, granted = false, now)
    yield both
    assertEquals(done.map(_.pending), Right(None))

  test("a required ask still has to grant beside the alternatives"):
    val withPass = eitherP2OrP3.copy(asks = ConsentAsk(PlayerId("p4"),
      Vector(PowerId("test.pass"))) +: eitherP2OrP3.asks,
      covers = eitherP2OrP3.covers :+ ConsentKey(PowerId("test.pass"), p1,
        "test.destination", eitherP2OrP3.target))
    val asked = present(ConsentState().requested(withPass).toOption,
      "the request")
    val one = present(asked.answered(p2, granted = true, now).toOption,
      "the answer")
    assertEquals(one.pending.map(_.open.map(_.granter)),
      Some(Vector(PlayerId("p4"))))

  test("an alternative may not share a required ask's granter"):
    val clash = eitherP2OrP3.copy(asks =
      ConsentAsk(p2, Vector(PowerId("test.pass"))) +: eitherP2OrP3.asks)
    assert(ConsentState().requested(clash).isLeft)
```

`now` stands for the suite's `ConsentTurn`; use whatever name it already has. `PlayerId("p4")` is safe in a model test, which has no table.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.model.ConsentStateSuite"`
Expected: compile failure on `alternative`.

- [ ] **Step 3: Change the model**

In `model/Consent.scala`:

```scala
/** One granter asked about their powers on one option, and their answer
  * once given. An `alternative` ask is one of several, any of whose grant
  * will do (catalog batch 8, slice 15). */
final case class ConsentAsk(granter: PlayerId, powers: Vector[PowerId],
    answer: Option[Boolean] = None, alternative: Boolean = false)
```

In `ConsentRequest`, add:

```scala
  def required: Vector[ConsentAsk] = asks.filterNot(_.alternative)
  def alternatives: Vector[ConsentAsk] = asks.filter(_.alternative)

  /** Every required ask granted and, when it has alternatives, one of
    * them. */
  def granted: Boolean = required.forall(_.answer.contains(true)) &&
    (alternatives.isEmpty || alternatives.exists(_.answer.contains(true)))

  /** A required ask refused, or every alternative. */
  def refused: Boolean = required.exists(_.answer.contains(false)) ||
    (alternatives.nonEmpty && alternatives.forall(_.answer.contains(false)))

  /** The keys `ask`'s answer settles. */
  def keysOf(ask: ConsentAsk): Vector[ConsentKey] =
    keys.filter(key => ask.powers.contains(key.power))
```

and replace `open`:

```scala
  /** The asks still waiting on their granter: each unanswered required
    * one, and the unanswered alternatives until one of them grants. */
  def open: Vector[ConsentAsk] =
    val settled = alternatives.exists(_.answer.contains(true))
    asks.filter(ask => ask.answer.isEmpty && !(ask.alternative && settled))
```

In `ConsentState.requested`, add one more refusal before `Right`:

```scala
    else if request.asks.map(_.granter).distinct.size != request.asks.size then
      Left(OathViolation.InvalidEventOrder(
        "a consent request asks each granter once"))
```

Replace `answered` and `grantedBy`:

```scala
  /** `granter`'s answer (rulings 9 and 12; catalog batch 8, slice 15). A
    * required refusal settles the whole request as refused. An
    * alternative's refusal is remembered and the others stay asked, until
    * all have refused. The last grant needed settles it as granted. */
  def answered(granter: PlayerId, granted: Boolean, now: ConsentTurn)
      : Either[OathViolation, ConsentState] =
    pending.filter(_.open.exists(_.granter == granter)) match
      case None => Left(OathViolation.InvalidEventOrder(
        s"${granter.value} has no open consent ask"))
      case Some(request) =>
        val updated = request.copy(asks = request.asks.map(ask =>
          if ask.granter == granter then ask.copy(answer = Some(granted))
          else ask))
        val alternative = request.asks.exists(ask =>
          ask.granter == granter && ask.alternative)
        if !granted && !alternative then
          Right(refusedBy(granter, updated, now))
        else if updated.granted || updated.refused then
          Right(settledBy(updated, now))
        else if !granted then Right(copy(
          answers = answersAt(now) ++ answersOf(updated), pending =
            Some(updated), turn = Some(now)))
        else Right(copy(pending = Some(updated)))

  /** Each answered ask's keys, as its granter answered them. */
  private def answersOf(request: ConsentRequest)
      : Vector[(ConsentKey, Remembered)] =
    request.asks.flatMap(ask => ask.answer.toVector.flatMap(given =>
      request.keysOf(ask).map(_ -> (if given then ConsentAnswer.Granted
        else ConsentAnswer.Refused).by(ask.granter))))

  /** The request settled: each answered ask's keys remembered as answered,
    * and nothing waiting. */
  private def settledBy(request: ConsentRequest, now: ConsentTurn)
      : ConsentState =
    copy(answers = answersAt(now) ++ answersOf(request), pending = None,
      turn = Some(now))
```

`settledBy` replaces `grantedBy`. With no alternatives it remembers what `grantedBy` did: every key, granted by the ask that holds its power. Keep `refusedBy` as it is.

- [ ] **Step 4: Run the model tests**

Run: `./sbtw "testOnly oathdigital.model.ConsentStateSuite" "testOnly oathdigital.gameplay.consent.*" "testOnly oathdigital.application.*Consent*"`
Expected: PASS.

- [ ] **Step 5: Wire, command and desk**

- `ConsentEventCodec`: each ask writes `"alternative" -> true` only when set. The decoder reads it with `ask.obj.get("alternative").exists(_.bool)`.
- `GameIntent.RequestConsent(decisionId, target, granters: Vector[String] = Vector.empty)`. The codec writes `"granters"` only when non-empty, and the decoder reads it as optional.
- `GameCommand.RequestConsent(requester, decisionId, target, granters: Vector[PlayerId] = Vector.empty)`. `GameIntentMapper` maps the ids with `PlayerId(_)`, and `Authorization` and `GameApplicationService` pass `granters` through.
- `ConsentDesk.request` takes `granters` and narrows the asks:

```scala
  def request(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef, granters: Vector[PlayerId] = Vector.empty)
      : Result = for
    ready <- playing(state)
    _ <- Either.cond(ready.consent.pending.isEmpty, (),
      OathViolation.ConsentPending)
    ask <- rules.consentAsk(state, requester, decisionId, target)
    asks <- chosen(ask.asks, granters)
    covers = ask.covers.filter(key => asks.exists(_.powers.contains(key.power)))
    request = ConsentRequest(requester, decisionId, target, asks, covers)
    events <- settled(state,
      ConsentRequested(request) +: automatic(ready.consent, request))
  yield events

  /** The asks a request sends: all of them, or the required ones and the
    * alternatives whose granter `granters` names (catalog batch 8, slice
    * 15). */
  private def chosen(asks: Vector[ConsentAsk], granters: Vector[PlayerId])
      : Either[OathViolation, Vector[ConsentAsk]] =
    if granters.isEmpty then Right(asks)
    else
      val kept = asks.filter(ask => !ask.alternative ||
        granters.contains(ask.granter))
      val unknown = granters.filterNot(by => asks.exists(_.granter == by))
      if unknown.nonEmpty then Left(OathViolation.ConsentUnavailable(
        s"${unknown.map(_.value).mkString(", ")} would not be asked"))
      else if asks.exists(_.alternative) && !kept.exists(_.alternative) then
        Left(OathViolation.ConsentUnavailable(
          "ask at least one of the players who could consent"))
      else Right(kept)
```

  A request that keeps a single alternative leaves it marked `alternative`, and it settles alone.

- `ConsentDesk.automatic` keeps answers only until the request settles:

```scala
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
    answers.foldLeft((Option(request), Vector.empty[ConsentAnswered])) {
      case ((Some(open), kept), answer)
          if open.open.exists(_.granter == answer.granter) =>
        val next = open.copy(asks = open.asks.map(ask =>
          if ask.granter == answer.granter then
            ask.copy(answer = Some(answer.granted))
          else ask))
        val required = !open.asks.exists(ask =>
          ask.granter == answer.granter && ask.alternative)
        val over = next.granted || next.refused ||
          (required && !answer.granted)
        (Option.unless(over)(next), kept :+ answer)
      case (done, _) => done
    }._2
```

  Update its doc: a required ask's Never settles the request; an alternative's Never refuses only that alternative.

- [ ] **Step 6: Projection and client**

- `ConsentAskProjection(granterPlayerIds, powers, alternatives: Vector[ConsentAlternativeProjection] = Vector.empty)` and `final case class ConsentAlternativeProjection(granterPlayerId: String, powers: Vector[String], paying: Boolean)`. In the codec, `alternatives` is written only when non-empty.
- In `WalkerDecisionProjector`, build the projection from the required asks into `granterPlayerIds` and `powers`, and each alternative into an `ConsentAlternativeProjection`. Set `paying = false` for now; Task 5 sets it from `ConsentPowers.paying`.
- `ConsentWaitingProjection(granterPlayerIds, either: Boolean = false)`. In `ConsentProjector`, `either` is true when every open ask is an alternative and more than one is open.
- `ConsentPanels.waiting` writes "Waiting for A's or B's consent…" when `either`, and keeps "and" otherwise.
- `ConsentAsking`:

```scala
  /** One way to ask: its button, the confirmation's question, and the
    * alternatives' granters it sends (empty for every ask). */
  final case class AskChoice(label: String, granters: Vector[String])

  /** "Ask Blue", "Ask Red", then "Ask both", or "Ask all" past two: one
    * per alternative, each with the required granters too. Empty without
    * alternatives. */
  def choices(value: GameProjection, ask: ConsentAskState)
      : Vector[AskChoice] =
    if ask.alternatives.isEmpty then Vector.empty
    else
      val required = ask.granterPlayerIds
      val one = ask.alternatives.map(alt => AskChoice(
        s"Ask ${listed((required :+ alt.granterPlayerId).map(name(value, _)))}",
        Vector(alt.granterPlayerId)))
      val all = AskChoice(if required.isEmpty && ask.alternatives.size == 2
        then "Ask both" else "Ask all", Vector.empty)
      if ask.alternatives.size > 1 then one :+ all else one
```

  `asker` keeps its confirm when `choices` is empty. Otherwise it opens a choice dialog whose question is `question(value, ask)`, with one button per choice and Cancel. Each button sends `GameCommand.RequestConsent(decisionId, WalkerStartArgWire(option.kind, option.id), choice.granters)`. `question` lists both the required and the alternative granters, joined by "or" for the alternatives: "Ask Blue or Red for consent to ignore Toll Roads?". If `CardInspection` has only `openConfirm`, add `openChoices(question: String, choices: Vector[(String, () => Unit)], origin: dom.html.Element)` beside it, built the way `openConfirm` builds its dialog, with Cancel last.

Add the client's frontend type alias for `ConsentAlternativeProjection` in `frontend.scala`, next to `ConsentAskState`.

- [ ] **Step 7: Write the wire and client tests**

- `ConsentEventWireSuite`: a `ConsentRequested` with one alternative ask round-trips, and one without alternatives encodes no `alternative` field.
- `CommandProtocolSuite`: `RequestConsent("travel.destination", WalkerStartArgWire("site", "coast"), Vector("p2"))` round-trips, and one without granters encodes no `granters` field.
- `ConsentAskingSuite`:

```scala
  test("an ask with alternatives offers one button each, then both"):
    val ask = ConsentAskProjection(Vector.empty, Vector.empty, Vector(
      ConsentAlternativeProjection("p3", Vector("Toll Roads"), paying = true),
      ConsentAlternativeProjection("p2", Vector("Toll Roads"),
        paying = false)))
    assertEquals(ConsentAsking.choices(projection, ask).map(_.label),
      Vector("Ask Carol", "Ask Bob", "Ask both"))
    assertEquals(ConsentAsking.choices(projection, ask).map(_.granters),
      Vector(Vector("p3"), Vector("p2"), Vector.empty))
```

  Use the suite's own projection and its players' display names in place of "Carol" and "Bob".
- `ConsentPanels` waiting: "Waiting for A's or B's consent…" with `either`.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/Consent.scala \
  src/main/scala/oathdigital/serialization/ConsentEventCodec.scala \
  src/main/scala/oathdigital/application/ConsentDesk.scala \
  src/main/scala/oathdigital/application/GameCommands.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/main/scala/oathdigital/application/Authorization.scala \
  src/main/scala/oathdigital/application/GameIntentMapper.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/ConsentProjector.scala \
  shared/src/main/scala/oathdigital/protocol/CommandIntents.scala \
  shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala \
  shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala \
  frontend/src/main/scala/oathdigital/frontend/ConsentAsking.scala \
  frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala \
  frontend/src/main/scala/oathdigital/frontend/frontend.scala
git add src/test/scala/oathdigital/model/ConsentStateSuite.scala \
  shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala
git commit -m "feat(consent): ask either of several players"
```

Add `CardInspection.scala`, the wire suite and the panel suite to the list if this task changed them. Find each file's real path with `git status` before staging.

---

### Task 5: Palanquin's consent, and Toll Roads on a sent Travel

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Palanquin.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala` (`payments`, `paying`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`, `reach`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`paying`)
- Modify: `src/main/scala/oathdigital/application/ConsentProjector.scala` (the prompt)
- Modify: `src/main/scala/oathdigital/application/gamelog/ConsentLines.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala` (the paying prompt)
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/PalanquinConsentSuite.scala`
- Create: `src/test/scala/oathdigital/application/PalanquinConsentServiceSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ConsentPromptPanelSuite.scala`

**Interfaces:**
- Consumes: `QueuedStarts.consent` (Task 3), alternative asks and `granters` (Task 4), `Palanquin` (Task 2).
- Produces: `TollRoads.payerId`, `payerKey`, `freeKey`, `owed`, `sending`, `Sending`, `payerWaiver`; `Palanquin.tollDecisionId`; `ConsentPowers.paying`.

- [ ] **Step 1: Write the failing gameplay tests**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TollRoads,
  TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Palanquin's destinations that wait on consent: Toll Roads' payer or
  * ruler, a Pass's ruler, and the user's own toll (catalog batch 8, slice
  * 15, rulings 6 to 12). */
class PalanquinConsentSuite extends munit.FunSuite:
  import TravelFixture.{board, coast, cradlePlains, on, pass, provincePlains}

  private val rules = ActPowerHarness.rulesWithPrintedRestrictions()
  private val source = DecisionOptionRef.Denizen(PalanquinCard.id)
  private val toll = CatalogNames.denizen("Toll Roads")
  private val coastRef = DecisionOptionRef.Site(coast)

  /** p1 holds Palanquin and 1 favor at the Cradle's plains, with p3 beside
    * them holding `p3Favor`. Toll Roads stands at the Provinces' plains,
    * and `tollRuler` rules it and the coast. */
  private def tolledWithP3Beside(tollRuler: PlayerId, p3Favor: Int = 1,
      steps: Table => Table = identity): ReadyGame =
    on(board())(table => steps(table.adviser(p1, PalanquinCard.id)
      .favor(p1, 1).pawn(p3, cradlePlains).favor(p3, p3Favor)
      .denizen(toll, at = provincePlains)
      .warbandsAt(provincePlains, tollRuler, 1)
      .warbandsAt(coast, tollRuler, 1)))

  private def remembering(ready: ReadyGame,
      answers: (ConsentKey, Remembered)*): ReadyGame =
    ready.copy(consent = ConsentState(answers = answers.toMap,
      turn = Some(ConsentTurn.of(ready))))

  /** p1's Palanquin aimed at p3, parked on the destination. */
  private def aimedAtP3(ready: ReadyGame): OathTransition =
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p3)))

  private def parkedDecide(state: OathState): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def offered(state: OathState, ref: DecisionOptionRef)
      : DecisionOption = parkedDecide(state).query match
    case one: DecisionQuery.ChooseOne => one.options.find(_.ref == ref)
      .getOrElse(fail(s"$ref not offered"))
    case other => fail(s"expected a choose-one, got $other")

  private def askOf(option: DecisionOption): OptionReason.Askable =
    option match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) => ask
      case other => fail(s"expected an askable option, got $other")

  /** p1 sends p3 to the coast, from the parked destination. */
  private def sentToTheCoast(ready: ReadyGame): OathTransition =
    accepted(ActPowerHarness.answer(rules, aimedAtP3(ready).state,
      Palanquin.destinationDecisionId, coastRef))

  test("a tolled destination may be asked of the target or the ruler"):
    val ask = askOf(offered(aimedAtP3(tolledWithP3Beside(p2)).state,
      coastRef))
    assertEquals(ask.asks, Vector(
      ConsentAsk(p3, Vector(TollRoads.payerId), alternative = true),
      ConsentAsk(p2, Vector(TollRoads.id), alternative = true)))
    assertEquals(ask.covers, Vector(TollRoads.payerKey(p3, coast),
      TollRoads.freeKey(p3, coast)))

  test("the target's grant sends them paying, with no toll question"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.payerKey(p3, coast) -> ConsentAnswer.Granted.by(p3))
    val after = Look(Outcomes.ready(sentToTheCoast(ready).state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 0)
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)

  test("the ruler's grant sends them free"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.freeKey(p3, coast) -> ConsentAnswer.Granted.by(p2))
    val after = Look(Outcomes.ready(sentToTheCoast(ready).state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 1)
    assertEquals(after.favor(p2), Look(ready).favor(p2))

  test("a target with no favor leaves only the ruler to ask"):
    val ask = askOf(offered(aimedAtP3(tolledWithP3Beside(p2, p3Favor = 0))
      .state, coastRef))
    assertEquals(ask.asks, Vector(ConsentAsk(p2, Vector(TollRoads.id))))

  test("both refused leave the destination unavailable with why"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.payerKey(p3, coast) -> ConsentAnswer.Refused.by(p3),
      TollRoads.freeKey(p3, coast) -> ConsentAnswer.Refused.by(p2))
    offered(aimedAtP3(ready).state, coastRef) match
      case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
        assertEquals(note.key, TollRoads.sentBothRefused.key)
      case other => fail(s"expected a noted refusal, got $other")

  test("the user rules Toll Roads: a take-or-waive question follows"):
    val ready = tolledWithP3Beside(p1)
    val picked = sentToTheCoast(ready)
    assertEquals(parkedDecide(picked.state).decisionId,
      Palanquin.tollDecisionId)
    assertEquals(askOf(offered(picked.state, Palanquin.take)).asks,
      Vector(ConsentAsk(p3, Vector(TollRoads.payerId))))
    val waived = accepted(ActPowerHarness.answer(rules, picked.state,
      Palanquin.tollDecisionId, Palanquin.waive))
    val after = Look(Outcomes.ready(waived.state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 1)

  test("a destination behind a ruled Pass is asked of its ruler, keyed as " +
      "the target's own"):
    val ready = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, cradlePlains).warbandsAt(pass, p2, 1))
    val dunes = DecisionOptionRef.Site(provincePlains)
    val ask = askOf(offered(aimedAtP3(ready).state, dunes))
    assertEquals(ask.asks, Vector(ConsentAsk(p2,
      Vector(NarrowPassSite.pass))))
    assertEquals(ask.covers, Vector(ConsentKey(NarrowPassSite.pass, p3,
      TravelProcedure.destinationDecisionId, dunes)))

  test("the user's own Pass lets the target through"):
    val ready = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, cradlePlains).warbandsAt(pass, p1, 1))
    assert(offered(aimedAtP3(ready).state,
      DecisionOptionRef.Site(provincePlains)).available)

  test("a destination decision whose every site waits on consent still " +
      "parks"):
    val others = Vector(TravelFixture.coast, TravelFixture.provincePlains,
      TravelFixture.mountain, TravelFixture.pass,
      TravelFixture.hinterlandPlains, TravelFixture.island,
      TravelFixture.secondHinterlandPlains)
    val ready = tolledWithP3Beside(p2, steps = table =>
      others.foldLeft(table)((ruled, site) => ruled.warbandsAt(site, p2, 1)))
    val decide = parkedDecide(aimedAtP3(ready).state)
    assertEquals(decide.decisionId, Palanquin.destinationDecisionId)
```

For the bandit-ruled case, add one more test built the way `TollRoadsSuite`'s bandits test builds its board. Its toll site and destination are ruled by bandits, and the target holds 1 favor. Assert that the coast's ask is `Vector(ConsentAsk(p3, Vector(TollRoads.payerId)))`. Then, with the payer's grant remembered, the target burns 1 favor and travels.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PalanquinConsentSuite"`
Expected: compile failure on `TollRoads.payerId`.

- [ ] **Step 3: Toll Roads for a sent Travel**

In `TollRoads.scala`, factor out who is owed, and add the sent-Travel API to the companion. Read `SiteRule.enemies` and `SiteRulers` as `charged` uses them.

```scala
  /** Who a Travel by `traveller` to `destination` owes Toll Roads to, if
    * anyone: its ruler, when the traveller is their enemy and the ruler
    * rules the destination. */
  def owed(ready: ReadyGame, traveller: PlayerId, destination: SiteId)
      : Option[SiteRuler] = for
    ruler <- SiteRulers.rulerOfCard(ready, TollRoadsCard.id)
    if SiteRule.enemies(ruler, SiteRuler.Player(traveller))
    if SiteRulers.rulerOf(ready, destination).contains(ruler)
  yield ruler

  /** The traveller's own agreement to pay, which a sent Travel asks of them
    * (catalog batch 8, slice 15). It belongs to no card's ruler, so no
    * preference is ever set on it. */
  val payerId: PowerId = PowerId("denizen.toll-roads.payer")

  /** `traveller` agreed to pay to reach `destination`. */
  def payerKey(traveller: PlayerId, destination: SiteId): ConsentKey =
    ConsentKey(payerId, traveller, decisionId,
      DecisionOptionRef.Site(destination))

  /** The ruler let `traveller` reach `destination` free: the key of the
    * free option of `traveller`'s own toll step there. */
  def freeKey(traveller: PlayerId, destination: SiteId): ConsentKey =
    ConsentKey(id, traveller, decisionId, free(destination))

  /** The payer's entry for the consent registry: named for Toll Roads,
    * with nobody who rules it. */
  val payerWaiver: (PowerId, Waiver) = payerId -> Waiver(_ => None,
    PowerSourceRef.Card(TollRoadsCard.id), (_, _) => payerAsked(
      PowerSourceRef.Card(TollRoadsCard.id)))

  /** What a Travel `sender` sends `traveller` on to `destination` waits on
    * for Toll Roads. */
  enum Sending:
    /** Nothing is owed. */
    case NotOwed
    /** The sender rules Toll Roads: they choose to take or waive. */
    case SenderRules
    /** The traveller agreed to pay, or the ruler waived it. */
    case Settled
    /** Either of `alternatives` would settle it. */
    case Asks(alternatives: Vector[Alternative], hover: PowerNote)
    /** Nobody may settle it. */
    case Barred(reason: PowerNote)

  /** One way to settle it: `granter` consenting under `key`. */
  final case class Alternative(granter: PlayerId, power: PowerId,
      key: ConsentKey)

  def sending(ready: ReadyGame, traveller: PlayerId, destination: SiteId,
      sender: PlayerId): Sending =
    owed(ready, traveller, destination) match
      case None => Sending.NotOwed
      case Some(SiteRuler.Player(owner)) if owner == sender =>
        Sending.SenderRules
      case Some(ruler) =>
        val turn = ConsentTurn.of(ready)
        val card = PowerSourceRef.Card(TollRoadsCard.id)
        val owner = ruler match
          case SiteRuler.Player(player) => Some(player)
          case _ => None
        val pays = ready.consent.answer(payerKey(traveller, destination),
          Some(traveller), turn)
        val waives = owner.flatMap(by => ready.consent.answer(
          freeKey(traveller, destination), Some(by), turn))
        if pays.contains(ConsentAnswer.Granted) ||
            waives.contains(ConsentAnswer.Granted) then Sending.Settled
        else
          val canPay = ready.game.current.players.find(_.player == traveller)
            .exists(_.board.favor >= Favor)
          val payer = Option.when(canPay &&
              !pays.contains(ConsentAnswer.Refused))(
            Alternative(traveller, payerId, payerKey(traveller, destination)))
          val waiver = owner.filterNot(_ =>
              waives.contains(ConsentAnswer.Refused))
            .map(by => Alternative(by, id, freeKey(traveller, destination)))
          val them = NoteArg.Player(traveller)
          val favor = NoteArg.Amount(Favor, NoteUnit.Favor)
          (payer, waiver, owner) match
            case (Some(_), Some(_), Some(by)) => Sending.Asks(
              Vector(payer, waiver).flatten,
              sentEither(card, them, favor, NoteArg.Player(by)))
            case (Some(_), None, Some(by)) => Sending.Asks(payer.toVector,
              sentPayer(card, them, favor, NoteArg.Player(by)))
            case (Some(_), None, None) => Sending.Asks(payer.toVector,
              sentBurn(card, them, favor))
            case (None, Some(_), Some(by)) => Sending.Asks(waiver.toVector,
              sentWaiver(card, NoteArg.Player(by)))
            case (_, _, Some(by)) if pays.contains(ConsentAnswer.Refused) &&
                waives.contains(ConsentAnswer.Refused) =>
              Sending.Barred(sentBothRefused(card, them, NoteArg.Player(by)))
            case (_, _, Some(by)) =>
              Sending.Barred(sentWaiveRefused(card, NoteArg.Player(by)))
            case _ if pays.contains(ConsentAnswer.Refused) =>
              Sending.Barred(sentRefused(card, them))
            case _ => Sending.Barred(sentNoFavor(card, them))
```

Check how a player's favor is read elsewhere in powers (for example `RulerToll.payOption`), and use that in place of `_.board.favor`. Declare the keys in `TollRoads.notes`:

| Key | Text |
|---|---|
| `sentEither` | "{Blue} must agree to give {1 favor} to {Red}, or {Red} must waive it." (args 0, 1, 2, 2) |
| `sentPayer` | "{Blue} must agree to give {1 favor} to {Red}." |
| `sentBurn` | "{Blue} must agree to burn {1 favor}." |
| `sentWaiver` | "{Red} must waive it." |
| `sentRefused` | "{Blue} refused." |
| `sentWaiveRefused` | "{Red} refused to waive it." |
| `sentBothRefused` | "{Blue} refused, and {Red} refused to waive it." |
| `sentNoFavor` | "{Blue} has no favor for it." |
| `payerAsked` | "The traveller may agree to pay." |

`charged` uses `owed`. In a sent Travel it asks nothing:

```scala
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- TollRoads.owed(ctx.state, route.player, route.destination)
  yield
    val paid = Vector(payment(route.player, ruler),
      Note(id, paidNote(route.player, ruler)))
    ruler match
      case SiteRuler.Player(owner) if sent(ctx.state, route) =>
        // A sent Travel asks nothing (catalog batch 8, slice 15, ruling
        // 11): its sender settled the toll before sending it.
        if agreed(ctx.state, route) then TravelRoute.beforeMove(operations,
          paid)
        else TravelRoute.beforeMove(operations, Vector(Note(id, _ =>
          Some(TollRoads.waived(PowerSourceRef.Card(cardId),
            NoteArg.Player(route.player), NoteArg.Player(owner))))))
      case SiteRuler.Player(owner) => ... the existing step, unchanged ...
      case _ => TravelRoute.beforeMove(operations, paid)

  /** Whether this Travel was sent by another player's power to its
    * destination. */
  private def sent(ready: ReadyGame, route: TravelRoute.Route): Boolean =
    ready.game.current.walkerEntry.exists(entry =>
      entry.procedure == ActionRef.Travel && entry.actor == route.player &&
        entry.sender.nonEmpty &&
        entry.startArgs == Vector(DecisionOptionRef.Site(route.destination)))

  private def agreed(ready: ReadyGame, route: TravelRoute.Route): Boolean =
    ready.consent.answer(TollRoads.payerKey(route.player, route.destination),
      Some(route.player), ConsentTurn.of(ready))
      .contains(ConsentAnswer.Granted)
```

Use the real name of the route type `TravelRoute.pawnMove` returns. Update the class doc with a paragraph on the sent Travel. If `TollRoads.scala` would pass 800 lines, move the sent-Travel API into `TollRoadsSent.scala` beside it.

In `ConsentPowers.scala`, take a third source of waivers and say which are payments:

```scala
final class ConsentPowers(powers: Vector[ContributingPower],
    operations: Vector[(PowerId, Waiver)] = Vector.empty,
    payments: Vector[(PowerId, Waiver)] = Vector.empty):
  private val waivers: Map[PowerId, Waiver] = (... as now ...) ++
    operations ++ payments
  private val paid: Set[PowerId] = payments.map(_._1).toSet

  /** Whether `power` is a player's agreement to pay rather than a ruler's
    * waiver (catalog batch 8, slice 15): its prompt and log line say
    * "pay". */
  def paying(power: PowerId): Boolean = paid(power)
```

`ConsentPowers.default` passes `payments = Vector(TollRoads.payerWaiver)`. Since a payer's granter is always `None`, `ruledBy` never lists it, `setPreference` rejects it, and `stale` clears nothing for it.

- [ ] **Step 4: Palanquin's consent**

In `Palanquin.scala`, add the toll question's ids and rebuild `destinationOptions` on `starts.consent` and `TollRoads.sending`:

```scala
  val tollDecisionId: String = "power.palanquin.toll"
  val take: DecisionOptionRef.Button = DecisionOptionRef.Button("toll.take")
  val waive: DecisionOptionRef.Button = DecisionOptionRef.Button("toll.waive")

  def destinationOptions(ready: ReadyGame, user: PlayerId, target: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    destinations(ready, user).map(site =>
      destinationOption(ready, user, target, site, starts))

  /** `site`, available once every grant it waits on was given; askable
    * while some are still to ask; unavailable with why once nobody may
    * give one. */
  private def destinationOption(ready: ReadyGame, user: PlayerId,
      target: PlayerId, site: SiteId, starts: QueuedStarts)
      : DecisionOption =
    val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
    def unavailable(reason: OptionReason) =
      DecisionOption.Unavailable(shown, reason)
    starts.consent(ready, entry(user, target, site)) match
      case QueuedStarts.Verdict.Refused(violation) =>
        unavailable(OptionReason.Written(reason(violation)))
      case verdict =>
        val turn = ConsentTurn.of(ready)
        val needed = verdict match
          case QueuedStarts.Verdict.Waiting(needed) => needed
          case _ => Vector.empty
        def answer(one: QueuedStarts.Needed) =
          ready.consent.answer(one.key, Some(one.granter), turn)
        val pending = needed.filterNot(answer(_)
          .contains(ConsentAnswer.Granted))
        pending.find(answer(_).contains(ConsentAnswer.Refused)) match
          case Some(refused) => unavailable(refused.plain.fold[OptionReason](
            OptionReason.Written("Cannot travel there now"))(
            OptionReason.Noted(refused.power, _)))
          case None => TollRoads.sending(ready, target, site, user) match
            case TollRoads.Sending.Barred(note) =>
              unavailable(OptionReason.Noted(TollRoads.id, note))
            case toll =>
              val alternatives = toll match
                case TollRoads.Sending.Asks(found, _) => found
                case _ => Vector.empty
              if pending.isEmpty && alternatives.isEmpty then shown
              else unavailable(askable(pending, toll))

  /** The ask for a destination: each pending grant's granter, required,
    * and Toll Roads' alternatives (ruling 8). A toll ruler already asked
    * for a required grant answers for the toll too, and the target is
    * then not asked (ruling 12). */
  private def askable(pending: Vector[QueuedStarts.Needed],
      toll: TollRoads.Sending): OptionReason.Askable =
    val (alternatives, hover) = toll match
      case TollRoads.Sending.Asks(found, note) => (found, Some(note))
      case _ => (Vector.empty, None)
    val required = pending.map(_.granter).distinct.map(granter =>
      ConsentAsk(granter, pending.filter(_.granter == granter)
        .map(_.power).distinct))
    val joined = alternatives.filter(alt =>
      required.exists(_.granter == alt.granter))
    val asks =
      if joined.nonEmpty then required.map(ask => joined
        .find(_.granter == ask.granter)
        .fold(ask)(alt => ask.copy(powers = ask.powers :+ alt.power)))
      else required ++ alternatives.map(alt => ConsentAsk(alt.granter,
        Vector(alt.power), alternative = alternatives.size > 1))
    val keys = pending.map(_.key) ++
      (if joined.nonEmpty then joined else alternatives).map(_.key)
    pending.headOption match
      case Some(first) => OptionReason.Askable(first.power, first.asked,
        asks, keys)
      case None => OptionReason.Askable(TollRoads.id, hover.get, asks, keys)
```

Replace `hover.get` with a total match if the build's lint objects. The toll question goes between the destination `Branch` and the `BuildOps`:

```scala
    Branch((live, pending) => (chosenTarget(pending),
        PowerAnswers.one(pending, destinationDecisionId)) match
      case (Some(target), Some(DecisionOptionRef.Site(site)))
          if TollRoads.sending(live, target, site, player) ==
            TollRoads.Sending.SenderRules =>
        Vector(Decide(tollDecisionId, player, DecisionQuery.ChooseOne(
          Vector(takeOption(live, target, site, player),
            DecisionOption.Button(waive, "Waive the toll")),
          heading = Some("Toll Roads: take 1 favor, or waive the toll"))))
      case _ => Vector.empty),
```

```scala
  /** "Take 1 favor": askable of the target, available once they agreed,
    * unavailable once they refused or with no favor (ruling 10). */
  private def takeOption(ready: ReadyGame, target: PlayerId, site: SiteId,
      user: PlayerId): DecisionOption =
    val shown = DecisionOption.Button(take, "Take 1 favor")
    val card = PowerSourceRef.Card(TollRoadsCard.id)
    val key = TollRoads.payerKey(target, site)
    val them = NoteArg.Player(target)
    val given = ready.consent.answer(key, Some(target),
      ConsentTurn.of(ready))
    val favor = ready.game.current.players.find(_.player == target)
      .exists(_.board.favor >= TollRoads.Favor)
    if given.contains(ConsentAnswer.Granted) then shown
    else if given.contains(ConsentAnswer.Refused) then
      DecisionOption.Unavailable(shown, OptionReason.Noted(TollRoads.id,
        TollRoads.sentRefused(card, them)))
    else if !favor then DecisionOption.Unavailable(shown,
      OptionReason.Noted(TollRoads.id, TollRoads.sentNoFavor(card, them)))
    else DecisionOption.Unavailable(shown, OptionReason.Askable(
      TollRoads.payerId, TollRoads.sentPayer(card, them,
        NoteArg.Amount(TollRoads.Favor, NoteUnit.Favor),
        NoteArg.Player(user)), Vector(ConsentAsk(target,
          Vector(TollRoads.payerId))), Vector(key)))
```

Add `tollDecisionId` to `narratedDecisions`. Read the favor the way Step 3 does. `TollRoadsCard` is in `gameplay.powers.travel`, which a power may import. Update the class doc with the consent paragraph.

- [ ] **Step 5: The walker keeps a decision whose builder declared its asks**

In `ProcedureWalker.scala`:

```scala
  /** The options `decide` itself declares askable: a power that builds its
    * own consent asks has already judged that the grants would open them
    * (catalog batch 8, slice 15). */
  private def declaredAsks(decide: Decide)
      : Map[DecisionOptionRef, OptionReason] = decide.query match
    case one: DecisionQuery.ChooseOne => one.options.collect {
      case DecisionOption.Unavailable(option, ask: OptionReason.Askable) =>
        option.ref -> (ask: OptionReason)
    }.toMap
    case _ => Map.empty
```

In `narrowAt`'s `Left(refused)` branch, `val asks = declaredAsks(decide) ++ asksAt(decide, ctx, path) ++ operationAsksAt(...)`. In `reach`'s `Left(refused)` branch, also stop when `declaredAsks(decide).nonEmpty`. Add one sentence to both docs. The comment must not name Palanquin (engine files never name a power).

- [ ] **Step 6: Run the gameplay tests**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`, then `./sbtw "testOnly oathdigital.gameplay.powers.action.*" "testOnly oathdigital.gameplay.powers.travel.*" "testOnly oathdigital.gameplay.walker.*"`
Expected: PASS. The note-key diff adds only the nine Toll Roads keys.

- [ ] **Step 7: Prompt, log and projection**

- `WalkerDecisionProjector`: each alternative's `paying` is `ask.powers.forall(consentPowers.paying)`. Pass the `ConsentPowers` the projector is built with; if it has none, take it from `GameProjector`'s construction the way `ConsentProjector` gets it.
- `ConsentProjector.applied`: the prompt's `paying` is `ask.powers.forall(powers.paying)`. A paying prompt's `target` is the label of the site its keys name: `waiting.keysOf(ask).collectFirst { case ConsentKey(_, _, _, site: DecisionOptionRef.Site) => site }`, labelled as `target` labels a site. Fall back to `target(context, waiting)`.
- `ConsentPanels.prompt` (client): when `paying`, "{requester} wants to send you to {target}. Consent to pay {powers} and travel to {target}?". Otherwise keep the template.
- `ConsentLines`: split a request's asks into paying and ignoring. The ignoring asks keep the existing line, written only when there are any. Each paying ask writes "Asked {granter} for consent to pay {powers} and travel to {site}.", where the site comes from `request.keysOf(ask)` and is worded through `choices.option` as the target is.

- [ ] **Step 8: Write the service and client tests**

```scala
package oathdigital.application

import oathdigital.application.gamelog.LogScripts.{formatter, texts}
import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.gameplay.powers.travel.{TollRoads, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection._
import oathdigital.testkit.{Look, MenuPlay, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Palanquin's toll asked of the target or the ruler, through the service
  * (catalog batch 8, slice 15, rulings 8, 13, 14 and 17). */
class PalanquinConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val coast = TravelFixture.coast
  private val coastRef = DecisionOptionRef.Site(coast)

  /** TollConsentFixture's tolled board, with p1 holding Palanquin and p3
    * beside p1 with 1 favor. */
  private val p3BesideP1UnderTheToll: ReadyGame =
    TravelFixture.on(TollConsentFixture.tolled)(_
      .adviser(p1, PalanquinCard.id).pawn(p3, TravelFixture.cradlePlains)
      .favor(p3, 1))

  /** p1 uses Palanquin and aims it at p3; the walk parks on the
    * destination. */
  private def atTheDestination(service: GameApplicationService)(
      using munit.Location): GameAccepted =
    val used = accepted(service.handle(gameId, 0L, MenuPlay.starting(p1,
      ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id)))))
    accepted(service.handle(gameId, used.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Palanquin.targetDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(p3))))))

  private def asking(service: GameApplicationService,
      granters: Vector[PlayerId])(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, atTheDestination(service).nextSequence,
      GameCommand.RequestConsent(p1, Palanquin.destinationDecisionId,
        coastRef, granters)))

  private def answering(service: GameApplicationService, at: GameAccepted,
      granter: PlayerId, choice: ConsentChoice)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(granter, choice)))

  private def coastRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(projector.project(gameId, at.loaded, p1).walkerDecision
      .flatMap(_.query).toVector.flatMap(_.offeredOptions)
      .find(_.id == coastRef.wireId), "the coast row")

  test("the tolled coast is askable of either player"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val row = coastRow(atTheDestination(service))
    assertEquals(row.state, "askable")
    assertEquals(row.consent, Some(ConsentAskProjection(Vector.empty,
      Vector.empty, Vector(
        ConsentAlternativeProjection("p3", Vector("Toll Roads"),
          paying = true),
        ConsentAlternativeProjection("p2", Vector("Toll Roads"),
          paying = false)))))

  test("asking only the target prompts them to pay and travel"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val asked = asking(service, Vector(p3))
    val prompt = projector.project(gameId, asked.loaded, p3).consentPrompt
    assertEquals(prompt.map(_.paying), Some(true))
    assertEquals(prompt.map(_.powers), Some(Vector("Toll Roads")))
    assertEquals(projector.project(gameId, asked.loaded, p2).consentPrompt,
      None)

  test("the target's Yes sends them paying, as p1's undoable step"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val yes = answering(service, asking(service, Vector(p3)), p3,
      ConsentChoice.Yes)
    val after = Look(Outcomes.ready(yes.state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 0)
    assertEquals(yes.undo.map(_.issuer), Some(p1))

  test("asking both: the ruler's Yes settles it and closes the target's " +
      "prompt"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val yes = answering(service, asking(service, Vector.empty), p2,
      ConsentChoice.Yes)
    assertEquals(Look(Outcomes.ready(yes.state)).favor(p3), 1)
    assertEquals(projector.project(gameId, yes.loaded, p3).consentPrompt,
      None)

  test("under Ask both, one refusal leaves the other asked"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val no = answering(service, asking(service, Vector.empty), p3,
      ConsentChoice.No)
    assert(projector.project(gameId, no.loaded, p2).consentPrompt.nonEmpty)
    assertEquals(projector.project(gameId, no.loaded, p1).consentWaiting,
      Some(ConsentWaitingProjection(Vector("p2"))))

  test("the log writes one line per kind of ask"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    asking(service, Vector.empty)
    val lines = texts(formatter.format(present(accepted(service.history(
      gameId)), "a stored game").steps, Some(p1)))
    assert(lines.exists(line => line.startsWith(
      "Asked p3 for consent to pay Toll Roads and travel to ")), lines)
    assert(lines.exists(line => line.startsWith(
      "Asked p2 for consent to ignore Toll Roads and select ")), lines)
```

Check `MenuPlay.starting`'s parameters and `ConsentFixture.service`'s game id against the testkit. In `ConsentPromptPanelSuite`, add: a paying prompt reads "{requester} wants to send you to {target}. Consent to pay Toll Roads and travel to {target}?".

- [ ] **Step 9: Run the tests**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala \
  src/main/scala/oathdigital/gameplay/powers/action/Palanquin.scala \
  src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/ConsentProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ConsentLines.scala \
  frontend/src/main/scala/oathdigital/frontend/ConsentPanels.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/scala/oathdigital/gameplay/powers/action/PalanquinConsentSuite.scala \
  src/test/scala/oathdigital/application/PalanquinConsentServiceSuite.scala
git commit -m "feat(action): Palanquin asks the target, the toll's ruler or a Pass"
```

Add `TollRoadsSent.scala` and the panel suite if this task created or changed them.

---

### Task 6: The Game Log

**Files:**
- Create: `src/test/scala/oathdigital/application/gamelog/PalanquinLogScripts.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogPalanquinSuite.scala`

**Interfaces:**
- Consumes: Palanquin's line (Task 2) and slice 14's off-turn tag.

- [ ] **Step 1: Write the script and the failing tests**

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2}
import LogScripts.atTable

/** Journaled Palanquin uses, for the Game Log suites (catalog batch 8,
  * slice 15). Kept beside `LogScripts`, which is at its size limit. */
object PalanquinLogScripts:
  /** p1 holds Palanquin and 1 favor at Ancient City, where p2 stands too;
    * p1 sends p2 to Buried Giant. */
  def palanquinSendsP2ToBuriedGiant(using munit.Location): Script =
    val table = Table.start.pawn(p2, "Ancient City")
      .adviser(p1, PalanquinCard.id).favor(p1, 1).banditsRefilled
    val (service, act) = atTable("palanquin", table)
    act.withAnswers {
      case park if park.decisionId == Palanquin.targetDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(p2))
      case park if park.decisionId == Palanquin.destinationDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Site(
          CatalogNames.site("Buried Giant")))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id))))
    Script("palanquin", service, p1)
```

```scala
package oathdigital.application.gamelog

import oathdigital.testkit.Table.p2
import LogScripts._
import PalanquinLogScripts._

/** Palanquin's line, and the target's Travel tagged with them (catalog
  * batch 8, slice 15, ruling 16). */
class GameLogPalanquinSuite extends munit.FunSuite:
  private def lines(script: Script) =
    texts(format(script, None).filter(_.depth == 1))

  test("Palanquin's run says where the user went and who follows"):
    val all = lines(palanquinSendsP2ToBuriedGiant)
    assert(all.contains(
      s"Moved to Buried Giant; ${playerLabel(p2)} travels there."), all)

  test("the target's Travel is tagged with them and Palanquin, and reads " +
      "as their own"):
    val all = lines(palanquinSendsP2ToBuriedGiant)
    val tag = all.indexWhere(
      _.startsWith(s"${playerLabel(p2)} · Travel · Palanquin"))
    assert(tag >= 0, all)
    val after = all.drop(tag + 1)
    assert(after.exists(_.contains("Buried Giant")), after)
    assert(!after.exists(_.startsWith(playerLabel(p2))), after)
```

If the Travel from Ancient City to Buried Giant is not legal on the quiet table, pick another site the Travel reaches and use it in both files.

- [ ] **Step 2: Run them**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPalanquinSuite"`
Expected: PASS. If the Palanquin line reads differently (for example a "Chose" line appears because `narratedDecisions` misses a decision), fix the power, not the test.

- [ ] **Step 3: Commit**

```bash
git add src/test/scala/oathdigital/application/gamelog/PalanquinLogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPalanquinSuite.scala
git commit -m "test(log): Palanquin's line and the target's tagged Travel"
```

---

### Task 7: Rulings, docs, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 15)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", "Slice 15", "Seams used for the first time", "Testing", the Status line)
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `docs/architecture/follow-up-actions.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

Replace the slice 15 entry of the rulings appendix with this plan's "Rulings" section, written as the appendix writes entries: the card's printed text quoted, then one bullet per rule. Keep "Toll Roads: the target must consent to pay its ruler" as ruling 8's origin, and note that the product owner widened it on 2026-10-16. The ruler may waive instead, and a waivable restriction (Narrow Pass) is asked the same way.

- [ ] **Step 2: Update the design**

Replace the slice 15 bullet of "The bar" with:

```markdown
- **A Travel sent to a site** (slice 15). A power's queued Travel `Run`
  may carry its destination as its one start argument, and then asks no
  destination question. Its entry names its `sender`: a waivable
  restriction the sender could waive does not block it, and the queued
  destination counts as its actor's answer, so a grant of it lets the
  restriction through. `QueuedStarts.consent` tells a power which grants a
  queued start waits on. A consent ask may be one of several alternatives,
  any of whose grant settles the request.
```

In "Slice 15: Palanquin", add the toll and the Pass: the user may ask the target to pay or the ruler to waive, and a ruled Pass's ruler to let them through. In "Testing", add "Narrow Pass asked and the user's own Pass" to slice 15's list.

- [ ] **Step 3: Update the consent design**

Add a short section "Alternatives (catalog batch 8, slice 15)" to `2026-10-08-consent-system-design.md`. It covers:
- an alternative ask;
- settling: the first grant, the last refusal, and required asks beside alternatives;
- `RequestConsent`'s `granters`;
- the client's per-player buttons and "Ask both";
- a payer ask, its wording, and the fact that it has no preference.

- [ ] **Step 4: Update the follow-up actions doc**

In `docs/architecture/follow-up-actions.md`, under "Off-turn actors", add: a queued entry may name its sender, and a power's queued Travel may carry its destination (Palanquin).

- [ ] **Step 5: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by about 6 + 10 + 1 + 4 + 5 + 3 + 11 + 6 + 2 = 48 new tests, plus any the reviews ask for. The frontend count rises by about 3.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 6: Record the status**

- In the design's Status line, write "Slices 1 to 15 are done." If an earlier slice is still open, write the slices that are done.
- In the ROADMAP's "Now" paragraph, update the count the same way.
- Mark item 15 done: `- [x] **15. Palanquin.**`
- If the batch 8 list has no open item left but 10b, 13b, 13c and 14b, say so in one line under the list.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/superpowers/specs/2026-10-08-consent-system-design.md \
  docs/architecture/follow-up-actions.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 15 done"
```
