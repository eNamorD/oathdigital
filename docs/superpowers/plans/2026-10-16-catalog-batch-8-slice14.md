# Catalog Batch 8, Slice 14 (Off-Turn Actor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two cards:

- 98 Sneak Attack: "After another player's campaign, you may campaign, spending no Supply, if you declare them as the defender."
- 16 Second Wind, defender side: "[secret] [favor-burnt] After you're victorious, you may travel and then may campaign, spending no Supply for either."

**Architecture:**

- **A walk belongs to its entry's actor.**
  - A new `CurrentGameState.walkActor` reads the actor of the queue entry the walk was started from (`walkerEntry`), else the turn's player.
  - Every walk-scoped read of `turn.activePlayer` in the walker, the start path, the simulation, the projector and roll ownership reads `walkActor` instead.
  - `WalkCtx.activePlayer` and `PowerCtx.activePlayer` keep their names, but now hold the walk's actor. Nearly every power that reads them already means the walk's actor (the attacker, the traveller), so those powers follow with no change.
- **A queued start accepts its entry's actor.** `startNow` accepts a requester who is the dequeued entry's actor. A menu start still requires the turn's player.
- **What stays on the turn.**
  - Off-turn cost settlement (`PayCostSettlement`), placements, and Vow of Wandering's Imperial warbands stay on the turn's player.
  - So do the phase menus, the HUD (`activePlayerResources`) and the undo barrier.
  - Special Envoy ends the Act Phase only when its traveller is the turn's player.
- **A queued Campaign may name its defender.** A Campaign entry with a source may carry `DecisionOptionRef.Player(defender)` as its one start argument.
  - `CampaignSetup.queuedDefender` reads it.
  - The Conquest defender and the Raid candidates are limited to that player, so the legal kinds, the defender question and the extra targets follow.
- **Second Wind** is offered to the defender too. A winning player-defender owes a Travel and then a Campaign, queued at the back and walked off turn.
- **Sneak Attack** is a persistent adviser rule at the Campaign's root, built like Herald.
  - After a Campaign that recorded a result, it puts an `Offer` at the front of the queue for its holder, who is not the attacker, when the holder could campaign against the attacker.
  - The entry names the attacker as the defender, and the card is its source, so the holder's Campaign spends no Supply.
  - Second Wind's offers go to the back, so Sneak Attack is offered first.
- **The Game Log names an off-turn actor once.**
  - A run's actor is its dequeued entry's actor, so the lines inside an off-turn walk read as an on-turn walk's do.
  - When that actor is not the turn's player, the run's tag starts with their name ("Blue · Campaign · Sneak Attack"), and a skipped offer ends "skipped by Blue."

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "The bar" (the slice 14 bullet), "Slice 14: Off-turn actor", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 14 entry. Task 5 replaces it with this plan's rulings, listed under "Rulings" below.
- `docs/architecture/follow-up-actions.md`, for the queue, the `Offer` and `Run` kinds, the Supply waiver and its "Sneak Attack hook" section. `FollowUpOffer.scala`, `QueuedStart.scala`, `Herald.scala` and `SecondWind.scala` restate what this slice uses.

**Starts from:** `main` once slice 13 (branch `claude/batch8-slice13`) has merged. Before Task 1, check that main has `CampaignSetup.queuedOrigin` and `CampaignProcedure.startArgs`. If it does not, stop and ask. Main also has the Game Log procedure registry (`ProcedureLog.scala`, commit `1e957d7a`). Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Rulings

The product owner settled these on 2026-10-16. They override the slice 14 entry of the rulings appendix where the two differ.

1. **The walk's actor.**
   - A walk started from a queue entry belongs to that entry's actor. Every other walk belongs to the turn's player.
   - `WalkCtx.activePlayer` and `PowerCtx.activePlayer` keep their names and hold the walk's actor. ROADMAP records the rename to `actor`.
2. **Starts.** A queued start accepts its entry's actor as the requester. A menu start still requires the turn's player. A parked roll is the walk actor's.
3. **Off-turn costs.** A cost paid by an off-turn actor settles off turn, since it is another player's turn: favor to the bank, secrets flip facedown. Placements and Vow of Wandering's Imperial warbands stay on the turn's player.
4. **Modifiers off turn.** The off-turn actor picks their own Campaign and Travel modifiers and battle plans. Spiteful Mirror's per-turn uses are not reachable, since only Campaign and Travel run off turn.
5. **Special Envoy off turn** waives the Supply, and does not end the Act Phase: it is not the traveller's Act Phase.
6. **Sneak Attack's order.**
   - Sneak Attack is offered first, at the end of the other player's Campaign: its `Offer` goes to the front of the queue.
   - The follow-ups the Campaign queued, Second Wind's on either side, go to the back. They run after the Sneak Attack, whether it is taken or skipped.
   - Taking it puts the Campaign `Run` at the front, as every taken offer does.
7. **Which Campaigns trigger it.**
   - A Campaign that recorded a result. A Campaign skipped, or ended with nothing to attack (Vow of Union), does not.
   - The holder is not that Campaign's attacker. They may be its defender or uninvolved.
   - An off-turn Campaign counts: a defender's Second Wind Campaign is another player's Campaign.
8. **The declared defender.**
   - The entry's start argument names the earlier attacker.
   - Only kinds where that player defends are legal: a Conquest of an origin they rule, or a Raid of their pawn at the origin. The defender question offers only them, and a Conquest's extra targets are sites they rule.
   - The argument is accepted only from a queue entry with a source, as slice 13's origin is.
9. **No offer without a target.** The offer is queued only when the holder has a legal kind against the earlier attacker. A start that a restriction refuses (Vow of Peace) still shows Take unavailable with its reason.
10. **One holder.** Sneak Attack is a single card, so the appendix's "several holders, in turn order" cannot arise. The design's "two holders in turn order" test is dropped.
11. **Second Wind for a defender.**
    - The plan is offered on both sides.
    - A winning player-defender gets Travel, then Campaign, `Offer` entries at the back, spending no Supply. A Bandit defender gets nothing.
    - The defender pays off turn (ruling 3). The chain still ends only when the cost cannot be paid.
12. **The Game Log.**
    - A run's actor is the actor of its dequeued entry, falling back to the turn's player. The lines inside omit that actor as on-turn lines do.
    - When the run's actor is not the turn's player, the tag starts with their name: "Blue · Campaign · Sneak Attack".
    - A skipped offer whose actor is not the turn's player reads "Sneak Attack: Campaign skipped by Blue."
13. **Client.** No frontend change. The client already shows a decision's controls to its owner, and the HUD stays on the turn's player.
14. **Undo.** No change. Issuer gating already stops the turn's player undoing an off-turn actor's answers.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `LogScripts.scala` is at its limit: new journaled scripts go in a new file beside it, as `CaptainsLogScripts.scala` does.
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - Engine files under `gameplay/walker` and `gameplay/operations` never name a power.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** This slice adds no wire field: the defender argument is an existing `DecisionOptionRef.Player`.
- **Hover reasons.** An unusable option stays listed, unavailable with its reason. It is never hidden. Ruling 9's gate is a trigger condition, not a hidden option.
- **Log lines omit the actor.** Only a non-actor, or an actor off turn, is named (ruling 12).
- **Lessons from earlier slices' tests:**
  - To read a parked decision, match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`. `PlanDriver.awaits(run, who, id)` wraps it.
  - The quiet table (`Table.start`) has p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant. Each has 3 board warbands and 7 Supply, and it is p1's Act.
  - `CampaignFixture.board` puts 2 Bandits at Ancient City, p1 there, and p2 and p3 together at the first other site. `withEnemyAtOrigin` moves p2 (the rival) to the origin.
  - `PlanDriver.winningDice` makes every attack die a sword, and `losingDice` every attack die half a sword. Both make every defense die blank.
  - `Table.offering(entry)` parks a follow-up offer for `entry`. `FollowUpPlay.taken` and `skipped` answer it, and `FollowUpPlay.offered(state)` reads the parked offer's entry.
  - `PlanDriver.committed(game, started, b, force, raid = ...)` answers the kind, targets and force as `b.actor`. For an off-turn Campaign, pass `b.copy(actor = holder, ready = ...)`.
- **Note keys** are declared through a `NoteSet`. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"` when a task changes keys. This plan adds none.
- **The unimplemented-powers snapshot** is `src/test/resources/powers/unimplemented.txt`. Regenerate it with `UNIMPLEMENTED_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite"`, and check that the diff drops only `denizen.sneak-attack`.
- **Names, exactly:**
  - `CurrentGameState.walkActor: PlayerId` in `model/GameState.scala`.
  - `CampaignSetup.queuedDefender(ready, actor): Option[PlayerId]` and `CampaignSetup.kindsAgainst(ready, actor, defender: PlayerId): Vector[CampaignKind]`.
  - `SneakAttack` (a `final case class` with `forCatalog`) and `SneakAttackCard`, both in `gameplay/powers/campaign/SneakAttack.scala`. The card object moves out of `HoldingDiscordDenizens.scala`.
  - `OffTurnWalkSuite` (Task 1), `SecondWindSuite` (extended, Task 2), `SneakAttackSuite` (Task 3), `OffTurnLogScripts` and `GameLogOffTurnSuite` (Task 4).

---

### Task 1: A walk belongs to its entry's actor

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`CurrentGameState`, the `QueuedProcedure` doc)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`advance`, `roll`, `resolve`, `narrowParked`, `awaitedPlayer`, `awaitedPlayers`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (`followUpAt`, `foldedChildrenAt`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (four reads)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkState.scala` (doc)
- Modify: `src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala` (doc)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startNow`, `startTriggered`, `resolveWalker`, `rollWalkerPrepared`, `walkerResumeContext`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (two rebuilds)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (`PowerCtx.activePlayer` doc)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala`
- Modify: `src/main/scala/oathdigital/model/PendingTree.scala` (doc only, if it still says the procedure belongs to `turn.activePlayer`)
- Create: `src/test/scala/oathdigital/gameplay/OffTurnWalkSuite.scala`

**Interfaces:**
- Produces: `CurrentGameState.walkActor: PlayerId`. Every later task relies on walks, simulations and projections using it.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.SecondWindCard
import oathdigital.gameplay.powers.travel.SpecialEnvoy
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A queued walk belongs to its entry's actor, on another player's turn
  * (catalog batch 8, slice 14, rulings 1, 2 and 5). */
class OffTurnWalkSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val secondWind = PowerSourceRef.Card(SecondWindCard.id)

  /** An offer owed to p2 on p1's turn, sourced by Second Wind. */
  private def owedToP2(action: ActionRef): QueuedProcedure =
    QueuedProcedure(p2, action, Vector.empty, Some(secondWind),
      QueueKind.Offer)

  /** The quiet table, p1's Act, with p2 owed `action`. */
  private def p2OwedOnP1sTurn(action: ActionRef): Table =
    Table.start.offering(owedToP2(action))

  private def spent(events: Vector[OathEvent]): Vector[SpendSupply] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
      .collect { case spend: SpendSupply => spend }

  /** Who the parked decision awaits, as the projection reads it. */
  private def awaitedBy(state: OathState): Option[PlayerId] =
    PlanDriver.parked.parkedDecision(state).map(_.awaiting)

  private def parkedDecisionId(state: OathState): Option[String] =
    PlanDriver.parked.parkedDecision(state).map(_.decision)

  test("an off-turn Travel is walked and answered by its actor, for no " +
      "Supply"):
    val taken = accepted(FollowUpPlay.taken(rules,
      p2OwedOnP1sTurn(ActionRef.Travel).state, p2))
    assertEquals(awaitedBy(taken.state), Some(p2))
    val refused = rules.resolveWalker(taken.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant)))
    assertEquals(refused.left.toOption,
      Some(OathViolation.WrongPlayer(p2, p1)))
    val done = accepted(rules.resolveWalker(taken.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.pawn(p2), buriedGiant)
    assertEquals(after.pawn(p1), ancientCity)
    assertEquals(after.supply(p2), 7)
    assertEquals(spent(done.events), Vector.empty)
    assertEquals(after.phase, Phase.Act)
    assertEquals(Outcomes.ready(done.state).game.current.turn.activePlayer,
      p1)

  test("an off-turn Campaign is walked by its actor as the attacker"):
    // Two bandits rule Broken Peaks, where p2 stands alone.
    val table = p2OwedOnP1sTurn(ActionRef.Campaign).bandits(brokenPeaks, 2)
    val taken = accepted(FollowUpPlay.taken(rules, table.state, p2))
    assertEquals(awaitedBy(taken.state), Some(p2))
    val parked = Outcomes.ready(taken.state).game.current
    assertEquals(parked.walkerEntry.map(_.actor), Some(p2))
    assertEquals(parked.walkActor, p2)
    // Conquest of the bandits is p2's only kind, so the force is asked.
    assertEquals(parkedDecisionId(taken.state), Some(CampaignIds.force))

  test("a menu start still requires the turn's player"):
    assertEquals(rules.startWalker(Table.start.state, ActionRef.Travel, p2)
      .left.toOption, Some(OathViolation.WrongPlayer(p1, p2)))

  test("a walk with no entry belongs to the turn's player"):
    assertEquals(Table.start.ready.game.current.walkActor, p1)

  test("an off-turn Travel with Special Envoy spends no Supply and leaves " +
      "the Act Phase running"):
    val board = p2OwedOnP1sTurn(ActionRef.Travel)
      .adviser(p2, "Special Envoy").state
    val taken = accepted(FollowUpPlay.taken(rules, board, p2))
    val started = accepted(ModifierPlay.picking(rules, taken, p2,
      PowerWindow.TravelModifierSelection, Vector(SpecialEnvoy.id)))
    val done = accepted(rules.resolveWalker(started.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    assertEquals(Look(Outcomes.ready(done.state)).phase, Phase.Act)
    assertEquals(Look(Outcomes.ready(done.state)).pawn(p2), buriedGiant)
    assert(!PhasePowerHarness.ops(done.events).contains(
      EnterPhase(Phase.Rest)), done.events)

  test("a replayed off-turn Travel reaches the same state"):
    val from = p2OwedOnP1sTurn(ActionRef.Travel).ready
    val taken = accepted(FollowUpPlay.taken(rules, OathState.Ready(from),
      p2))
    val done = accepted(rules.resolveWalker(taken.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    assertEquals(ActPowerHarness.replayed(rules, from,
      taken.events ++ done.events), Outcomes.ready(done.state))
```

Notes for the implementer:
- `PlanDriver` lives in `oathdigital.gameplay.powers.campaign` (test sources). Import it, and drop the unused `ProcedureWalker` import.
- Check that `PhasePowerHarness.ops` and `ActPowerHarness.replayed` have the signatures used here (`src/test/scala/oathdigital/testkit/`). Adjust the call, not the assertion.
- Broken Peaks is unused unless the Campaign test needs it. Remove any unused `val` before building, since `-Wunused` fails the build.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OffTurnWalkSuite"`
Expected: compile error: `walkActor` is not a member of `CurrentGameState`. Once Step 3's first edit is in, the Travel and Campaign tests fail with `WrongPlayer(p1, p2)` from `requireActivePlayer`, and the Special Envoy test fails on `Phase.Rest`.

- [ ] **Step 3: Add `walkActor`**

In `model/GameState.scala`, give `CurrentGameState` a body after its last field (`walkerDeferred`):

```scala
):
  /** The player the current walk belongs to (catalog batch 8, slice 14):
    * the actor of the queue entry it was started from, else the turn's
    * player. A queued walk's actor may be off turn (Sneak Attack, a
    * defender's Second Wind). */
  def walkActor: PlayerId = walkerEntry.fold(turn.activePlayer)(_.actor)
```

In the `QueuedProcedure` doc, replace "`actor` owns the walk: the one place an off-turn actor (Sneak Attack) will differ from the active player." with "`actor` owns the walk (`CurrentGameState.walkActor`), and may be off turn."

- [ ] **Step 4: Read `walkActor` in the walker**

Replace `state.game.current.turn.activePlayer` with `state.game.current.walkActor` at each of these:
- `ProcedureWalker.advance`, `roll`, `resolve` and `narrowParked` (the `WalkCtx` actor).
- `ProcedureWalker.awaitedPlayer` and `awaitedPlayers` (the parked `Roll`'s owner). Update both Scaladocs: "or the walk's actor for a parked `Roll`".
- `WalkerPowerGather.followUpAt` (the `ExecutedTriggers.batch` actor) and `foldedChildrenAt`.
- `WalkerSimulation`: all four reads.

In `WalkState.scala`, change "the acting player" to "the walk's actor (`CurrentGameState.walkActor`)". In `ContributingPower.scala`, replace the `activePlayer` doc with:

```scala
    /** The player whose procedure is running: the walk's actor
      * (`CurrentGameState.walkActor`), the traveller, the recoverer, the
      * attacker. Off turn for a queued walk whose entry names another
      * player. Not a parked decision's owner, which a contribution hooked
      * on a `Decide` reads from `operation`.
      */
```

In `QueuedStart.scala`, drop the doc sentence saying it does not check the entry's actor "until an off-turn actor exists (Sneak Attack)". Replace it with: "The simulated walk is the entry's actor's, since `startingFrom` sets `walkerEntry`."

- [ ] **Step 5: Read `walkActor` on the start and resume paths**

In `OathRulesWalker.scala`:
- `startNow`: `val actor = ready.game.current.walkActor`. The requester check becomes `requireWalkActor(ready, requester)`. Build, check restrictions, take start facts and run the fallback with `actor`.
- `startTriggered`, `resolveWalker`'s restriction check and `walkerResumeContext`: read `walkActor`.
- `rollWalkerPrepared`: `requireWalkActor`.
- Rename `requireActivePlayer` to `requireWalkActor`:

```scala
  /** Rejects a requester who is not the walk's actor: the turn's player,
    * or a dequeued entry's actor (catalog batch 8, slice 14, ruling 2).
    * Starting a procedure and rolling at its park are the actor's alone,
    * so neither check needs the tree. A menu start has no entry until the
    * menu queues it, so it still requires the turn's player.
    */
  private def requireWalkActor(ready: ReadyGame, requester: PlayerId)
      : Either[OathViolation, Unit] =
    val actor = ready.game.current.walkActor
    Either.cond(requester == actor, (), WrongPlayer(actor, requester))
```

A menu Campaign is queued by `PhaseMenu` with the turn's player as its actor, so a menu start is still the turn's player's. A test's direct `startWalker` has no entry and reads the turn's player. Leave `recordCardPlayFallback` on the turn's player: Search and Play Facedown Adviser run only on turn.

In `WalkerDecisionProjector.scala`, both rebuilds read `current.walkActor` (`context.current.walkActor`).

- [ ] **Step 6: Special Envoy ends only the traveller's own Act**

In `SpecialEnvoy.scala`:

```scala
  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx, ctx.activePlayer, operations) ++ (
        if ctx.activePlayer != ctx.state.game.current.turn.activePlayer
        then Vector.empty
        else Vector(EnterPhase(Phase.Rest),
          Note(id, _ => Some(SpecialEnvoy.ended(
            PowerSourceRef.Card(cardId))))))))))
```

Add to its Scaladoc: "Off turn (a defender's Second Wind Travel) it only waives the Supply: it is not the traveller's Act Phase to end (catalog batch 8, slice 14, ruling 5)."

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.OffTurnWalkSuite oathdigital.gameplay.FollowUpRulesSuite oathdigital.gameplay.FollowUpSupplyWaiverSuite oathdigital.gameplay.OathRulesWalkerPowerSuite oathdigital.gameplay.MinorActionsSuite"`
Expected: all pass. `MinorActionsSuite`'s off-turn refusal still gives `WrongPlayer(p1, p2)`, since a minor action has no queue entry.

Run: `./sbtw "test"`
Expected: all pass. Every existing walk's entry actor is the turn's player, so no outcome changes. If a suite fails, it built a `walkerEntry` whose actor differs from the turn's player. Read it before changing anything, and report it.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkState.scala \
  src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala \
  src/test/scala/oathdigital/gameplay/OffTurnWalkSuite.scala
git commit -m "feat(walker): a queued walk belongs to its entry's actor"
```

Add `PendingTree.scala` to the list if Step 4 changed its doc.

---

### Task 2: Second Wind for a winning defender

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SecondWind.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/SecondWindSuite.scala`

**Interfaces:**
- Consumes: Task 1's off-turn walk.
- Produces: a defender's `Offer` entries `QueuedProcedure(defender, Travel | Campaign, Vector.empty, Some(Card(16)), Offer)`, queued at the back. Task 3's ordering test relies on these going to the back.

- [ ] **Step 1: Write the failing tests**

In `SecondWindSuite`, replace the test "a defender is not offered it" with the tests below, and add the helpers beside `holding`:

```scala
  /** The rival rules the origin with 2 warbands and holds Second Wind
    * faceup, with one faceup secret and one favor to pay for it. p1 can
    * only conquer the rival there. */
  private def defenderHolding: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1).favor(base.rival, 1))

  private def owedTo(player: PlayerId, action: ActionRef): QueuedProcedure =
    QueuedProcedure(player, action, Vector.empty,
      Some(PowerSourceRef.Card(card)), QueueKind.Offer)

  /** p1 attacks with `force`; the rival picks Second Wind as the
    * defender's plan, then every window is finished. */
  private def defendedWithSecondWind(game: OathRules, b: Board,
      force: Int): Run =
    commit(game, b, force).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows

  test("a defender is offered it"):
    val b = defenderHolding
    val run = commit(rules(losingDice), b, 2).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(run.offered(b.rival).contains(ref))

  test("a winning defender is owed a Travel, then a Campaign"):
    val b = defenderHolding
    val held = defendedWithSecondWind(rules(losingDice), b, 2)
    assertEquals(held.winner, Some(false))
    assertEquals(followUps(held), Vector(owedTo(b.rival, ActionRef.Travel),
      owedTo(b.rival, ActionRef.Campaign)))

  test("a beaten defender is owed nothing"):
    val b = defenderHolding
    val beaten = defendedWithSecondWind(rules(winningDice), b, 5)
    assertEquals(beaten.winner, Some(true))
    assertEquals(followUps(beaten), Vector.empty)

  test("the defender pays off turn: the secret flips facedown and a " +
      "favor burns"):
    val b = defenderHolding
    val chosen = commit(rules(losingDice), b, 2).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(ready(chosen.state)).faceUpSecrets(b.rival), 0)
    assertEquals(Look(ready(chosen.state)).favor(b.rival), 0)

  test("the defender walks the Travel off turn, then is offered the " +
      "Campaign"):
    val b = defenderHolding
    val game = rules(losingDice)
    val held = defendedWithSecondWind(game, b, 2)
    val taken = accepted(FollowUpPlay.taken(game, held.state, b.rival))
    val to = Look(ready(taken.state)).pawn(b.actor)
    val travelled = accepted(game.resolveWalker(taken.state, b.rival,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(to))))
    val after = ready(travelled.state)
    assertEquals(Look(after).pawn(b.rival), to)
    assertEquals(Look(after).supply(b.rival), Look(b.ready).supply(b.rival))
    assertEquals(after.game.current.turn.activePlayer, b.actor)
    assertEquals(FollowUpPlay.offered(travelled.state),
      Some(owedTo(b.rival, ActionRef.Campaign)))
```

Imports to add: `oathdigital.gameplay.actions.travel.TravelProcedure` and `oathdigital.model.DecisionAnswer.ChooseOneAnswer`.

Notes for the implementer:
- With `losingDice`, p1's 2 attack dice score 1 against the rival's 2 warbands, so the defender wins. With `winningDice`, 5 swords beat 2. Check `winner` before relying on it, as the tests do.
- `finishAttackerPlans(attacker)` closes p1's window. If the defender's window parks first on this board, swap the order to match.
- `faceUpSecrets` and `favor` assert the off-turn settlement of ruling 3. If `PayCostSettlement` settles the favor differently (to a bank rather than burnt), keep the assertion on the defender's own favor, which is 0 either way.
- The Travel's destination is p1's pawn site, any site other than the rival's own. If the destination question does not list it, pick the first site it offers.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SecondWindSuite"`
Expected: the defender tests fail. The plan is not offered to the defender, so `pick` throws "campaign.defender-plan was refused".

- [ ] **Step 3: Offer it on both sides**

In `SecondWind.scala`:

```scala
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
```

`later` already queues for `use.user` when `use.won.contains(true)`. For the defender side, `PlanUse.user` is the player-defender and `won` is `!attackerWins`. A Bandit defender auto-applies only cost-free plans, so it never picks Second Wind.

Replace the Scaladoc's last paragraph ("Offered to the attacker only. …") with:

```scala
  * Offered to either side (catalog batch 8, slice 14, ruling 11). A winning
  * defender's Travel and Campaign are queued at the back with the defender
  * as their actor, so they are walked off turn after the attacker's
  * follow-ups, and the defender pays the plan's cost off turn.
```

Also correct the summary line "an attacker's battle plan" to "a battle plan".

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SecondWindSuite oathdigital.gameplay.powers.campaign.BattlePlanSuite"`
Expected: all pass. If `BattlePlanSuite` (or a plan-catalog snapshot) lists each plan's sides, update the Second Wind row only.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/SecondWind.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/SecondWindSuite.scala
git commit -m "feat(batch8): Second Wind for a winning defender, walked off turn"
```

---

### Task 3: A declared defender, and Sneak Attack

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala` (`startArgs`)
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SneakAttack.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `SneakAttackCard`, list it from its new home as the file lists moved cards)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` (register)
- Modify: `src/test/resources/powers/unimplemented.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/SneakAttackSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala`

**Interfaces:**
- Consumes: Task 1's `walkActor`, and Task 2's back-queued Second Wind entries.
- Produces:
  - `CampaignSetup.queuedDefender(ready, actor): Option[PlayerId]`.
  - `CampaignSetup.kindsAgainst(ready, actor, defender): Vector[CampaignKind]`.
  - `SneakAttack.forCatalog(catalog)`, whose entry is `QueuedProcedure(holder, ActionRef.Campaign, Vector(DecisionOptionRef.Player(attacker)), Some(PowerSourceRef.Card(SneakAttackCard.id)), QueueKind.Offer)`, queued with `atFront = true`.

- [ ] **Step 1: Write the failing setup tests**

Add to `CampaignSetupSuite`. Match its existing imports and board helpers. These use `CampaignFixture`.

```scala
  test("a queued defender limits the Raid candidates and the Conquest to " +
      "that player"):
    // p1 at the bandit-ruled origin with p2 and p3 both there.
    val b = on(withEnemyAtOrigin(CampaignFixture.board(warbands = 3,
      supply = 7)))(_.pawn(Table.p3, at = CampaignFixture.board(
        warbands = 3, supply = 7).origin))
    val entry = QueuedProcedure(b.actor, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(b.rival)),
      Some(PowerSourceRef.Card(DenizenId("98"))), QueueKind.Run)
    val dequeued = QueuedStart.startingFrom(b.ready, entry)
    assertEquals(CampaignSetup.queuedDefender(dequeued, b.actor),
      Some(b.rival))
    assertEquals(CampaignSetup.raidDefenders(dequeued, b.actor),
      Vector(b.rival))
    // The bandits rule the origin, so no Conquest is against the rival.
    assertEquals(CampaignSetup.legalKinds(dequeued, b.actor),
      Vector(CampaignKind.Raid))

  test("a menu Campaign has no queued defender"):
    val b = withEnemyAtOrigin(CampaignFixture.board(warbands = 3,
      supply = 7))
    assertEquals(CampaignSetup.queuedDefender(b.ready, b.actor), None)

  test("the kinds against a player, with no Campaign under way"):
    val b = withEnemyAtOrigin(CampaignFixture.board(warbands = 3,
      supply = 7))
    // The rival shares the origin with p1, and the bandits rule it.
    assertEquals(CampaignSetup.kindsAgainst(b.ready, b.rival, b.actor),
      Vector(CampaignKind.Raid))
    val ruled = actorRules(b, b.origin)
    assertEquals(CampaignSetup.kindsAgainst(ruled.ready, b.rival, b.actor),
      Vector(CampaignKind.Conquest, CampaignKind.Raid))
    val apart = CampaignFixture.board(warbands = 3, supply = 7)
    assertEquals(CampaignSetup.kindsAgainst(apart.ready, apart.rival,
      apart.actor), Vector.empty)
```

`actorRules` replaces the origin's bandits with p1's 2 warbands. If it adds to the bandits instead, clear the bandits first with the Table step the suite uses. Build the three-pawn board in the first test with one `board(...)` call stored in a `val`. The repeated call above is only there to name the origin.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignSetupSuite"`
Expected: compile error: `queuedDefender` and `kindsAgainst` are not members of `CampaignSetup`.

- [ ] **Step 3: Read the queued defender**

In `CampaignSetup.scala`, share the entry filter that `queuedOrigin` uses, and add the defender:

```scala
  /** The `Run` entry a power queued for this Campaign's walk, while it
    * runs for `actor`. A menu Campaign has none. */
  private def queuedRun(ready: ReadyGame, actor: PlayerId)
      : Option[QueuedProcedure] =
    ready.game.current.walkerEntry.filter(entry =>
      entry.procedure == ActionRef.Campaign && entry.actor == actor &&
        entry.source.nonEmpty && entry.kind == QueueKind.Run)

  def queuedOrigin(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    queuedRun(ready, actor).flatMap(_.startArgs match
      case Vector(DecisionOptionRef.Site(site)) => Some(site)
      case _ => None)

  /** The defender a power's queued Campaign declares, while that
    * Campaign's walk runs for `actor` (catalog batch 8, slice 14): Sneak
    * Attack's earlier attacker, its one start argument. Only that player
    * may defend: the Conquest needs them ruling the origin, and the Raid
    * needs their pawn there. */
  def queuedDefender(ready: ReadyGame, actor: PlayerId): Option[PlayerId] =
    queuedRun(ready, actor).flatMap(_.startArgs match
      case Vector(DecisionOptionRef.Player(player)) => Some(player)
      case _ => None)
```

Keep `queuedOrigin`'s existing Scaladoc.

Limit the two defender rules:

```scala
  def conquestDefender(ready: ReadyGame, actor: PlayerId)
      : Option[CampaignDefender] =
    originOf(ready, actor).flatMap(defenderAt(ready, _))
      .filterNot(_ == CampaignDefender.Player(actor))
      .filter(defender => queuedDefender(ready, actor)
        .forall(player => defender == CampaignDefender.Player(player)))

  def raidDefenders(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    originOf(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site) &&
        queuedDefender(ready, actor).forall(_ == p.player)).map(_.player))
```

`legalKinds`, `kindOptions`, `defenderOptions`, `defenderOf` and the Conquest extras in `targetOptions` (sites ruled by the same defender) follow with no change.

Add the trigger's check:

```scala
  /** The kinds `actor` could campaign with against `defender` from their
    * own pawn site, with no Campaign of theirs under way: whether Sneak
    * Attack's Campaign has a target (catalog batch 8, slice 14, ruling 9).
    * In Conquest order, then Raid. */
  def kindsAgainst(ready: ReadyGame, actor: PlayerId, defender: PlayerId)
      : Vector[CampaignKind] =
    val site = playerOf(ready, actor).flatMap(_.pawnSite)
    Vector(
      Option.when(site.flatMap(defenderAt(ready, _))
          .contains(CampaignDefender.Player(defender)))(
        CampaignKind.Conquest: CampaignKind),
      Option.when(site.exists(at => playerOf(ready, defender)
          .exists(_.pawnSite.contains(at))))(
        CampaignKind.Raid: CampaignKind)).flatten
```

In `CampaignProcedure.startArgs`, accept the defender:

```scala
      case Vector(DecisionOptionRef.Player(player))
          if CampaignSetup.queuedDefender(state, actor).contains(player) =>
        Right(())
```

Its error text becomes "takes no start selection but a power's queued origin or defender, got …". Update its Scaladoc: "None, or the one site or player a power's queued entry names."

- [ ] **Step 4: Run the setup tests**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.campaign.CampaignOriginSuite"`
Expected: all pass.

- [ ] **Step 5: Write the failing Sneak Attack tests**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.FollowUpPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.DistributeAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

/** Sneak Attack (card 98): after another player's Campaign, its holder may
  * campaign against that player for no Supply, off turn, before the
  * Campaign's other follow-ups (catalog batch 8, slice 14, rulings 6 to
  * 9). */
class SneakAttackSuite extends munit.FunSuite:
  private val sneak = SneakAttackCard.id
  private val source = PowerSourceRef.Card(sneak)
  private val secondWind = CatalogNames.denizen("Second Wind")

  /** p1 at the bandit-ruled origin with the rival, who holds Sneak Attack
    * faceup with 3 board warbands. */
  private def rivalHoldsSneakAttackAtTheOrigin: Board =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    withAdviserFor(b, b.rival, sneak, Orientation.FaceUp)

  /** The offer the rival is owed against p1. */
  private def sneakOffer(b: Board): QueuedProcedure =
    QueuedProcedure(b.rival, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(b.actor)), Some(source),
      QueueKind.Offer)

  private def ownedSecondWind(b: Board, action: ActionRef): QueuedProcedure =
    QueuedProcedure(b.actor, action, Vector.empty,
      Some(PowerSourceRef.Card(secondWind)), QueueKind.Offer)

  test("after another player's Campaign its holder is offered a Campaign " +
      "against them"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.result.attacker, b.actor)
    assertEquals(FollowUpPlay.offered(done.state), Some(sneakOffer(b)))

  test("the taken Campaign is the holder's, off turn, against the earlier " +
      "attacker, for no Supply"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val game = rules(winningDice)
    val done = commit(game, b, 4).finishPlanWindows
    val taken = accepted(FollowUpPlay.taken(game, done.state, b.rival))
    val holder = b.copy(actor = b.rival, ready = ready(done.state))
    val sneaked = committed(game, taken, holder, force = 3, raid = true)
      .finishPlanWindows
    val result = sneaked.result
    assertEquals(result.attacker, b.rival)
    assertEquals(result.defender, CampaignDefender.Player(b.actor))
    assertEquals(Look(ready(sneaked.state)).supply(b.rival),
      Look(b.ready).supply(b.rival))
    assertEquals(ready(sneaked.state).game.current.turn.activePlayer, b.actor)

  test("it is offered before the Campaign's Second Wind follow-ups, which " +
      "follow whether it is taken or skipped"):
    val base = rivalHoldsSneakAttackAtTheOrigin
    val b = on(withAdviserFor(base, base.actor, secondWind,
      Orientation.FaceUp))(_.secrets(base.actor, faceUp = 1)
      .favor(base.actor, 1))
    val game = rules(winningDice)
    val won = commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(secondWind)).finishPlanWindows
    assertEquals(FollowUpPlay.offered(won.state), Some(sneakOffer(b)))
    assertEquals(ready(won.state).game.current.queue,
      Vector(ownedSecondWind(b, ActionRef.Travel),
        ownedSecondWind(b, ActionRef.Campaign)))
    val skipped = accepted(FollowUpPlay.skipped(game, won.state, b.rival))
    assertEquals(FollowUpPlay.offered(skipped.state),
      Some(ownedSecondWind(b, ActionRef.Travel)))

  test("no offer when the holder cannot campaign against the attacker"):
    // The rival stands apart from p1, at a site nobody rules.
    val b = withAdviserFor(board(warbands = 4, supply = 7), Table.p2, sneak,
      Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), None)
    assertEquals(ready(done.state).game.current.queue, Vector.empty)

  test("no offer after the holder's own Campaign"):
    val b = withAdviserFor(withEnemyAtOrigin(board(warbands = 4,
      supply = 7)), Table.p1, sneak, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), None)

  test("no offer when the holder's Sneak Attack is facedown"):
    val b = withAdviserFor(withEnemyAtOrigin(board(warbands = 4,
      supply = 7)), Table.p2, sneak, Orientation.FaceDown)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), None)

  test("a holder under Vow of Peace sees Take unavailable, with its " +
      "reason"):
    val b = withAdviserFor(rivalHoldsSneakAttackAtTheOrigin, Table.p2,
      CatalogNames.denizen("Vow of Peace"), Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), Some(sneakOffer(b)))
    val take = done.options(b.rival).collectFirst {
      case DecisionOption.Unavailable(shown, reason) => (shown, reason) }
    assertEquals(take.map(_._2), Some(OptionReason.Written(
      "Vow of Peace prevents its ruler from campaigning")))

  test("a defender's Second Wind Campaign triggers it too"):
    // The rival rules the origin and holds Second Wind; p3, the bystander,
    // holds Sneak Attack where the rival's and p3's pawns start. p1 cannot
    // reach p3, so p3 is offered nothing after p1's Campaign. The rival wins
    // as defender, skips the Travel and raids p3, off turn; p3 is then
    // offered a Campaign against the rival.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withAdviserFor(base, base.rival, secondWind,
        Orientation.FaceUp), base.bystander, sneak, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1).favor(base.rival, 1))
    val game = rules(losingDice)
    val held = commit(game, b, 2).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(secondWind)).finishPlanWindows
    assertEquals(held.winner, Some(false))
    val noTravel = accepted(FollowUpPlay.skipped(game, held.state, b.rival))
    val toCampaign = accepted(FollowUpPlay.taken(game, noTravel.state,
      b.rival))
    val rival = b.copy(actor = b.rival, ready = ready(noTravel.state))
    val raided = committed(game, toCampaign, rival, force = 2, raid = true)
      .finishPlanWindows
    assertEquals(raided.result.attacker, b.rival)
    assertEquals(FollowUpPlay.offered(raided.state), Some(QueuedProcedure(
      b.bystander, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(b.rival)), Some(source),
      QueueKind.Offer)))

  test("no offer after a Campaign its attacker skips"):
    // p3 holds Vow of Union and rules Broken Peaks, in their region; the
    // rival holds Sneak Attack at the origin. p3 moves Broken Peaks'
    // warbands to their board, and p1 then skips the Campaign. The walk
    // ends before the root's trailing Branch, so nothing is offered.
    val brokenPeaks = CatalogNames.site("Broken Peaks")
    val base = rivalHoldsSneakAttackAtTheOrigin
    val b = on(withAdviserFor(base, base.bystander,
        CatalogNames.denizen("Vow of Union"), Orientation.FaceUp))(
      _.warbandsAt(brokenPeaks, base.bystander, 2))
    val game = rules(winningDice)
    val started = game.startWalker(OathState.Ready(b.ready),
      ActionRef.Campaign, b.actor).fold(e => fail(s"no start: $e"), identity)
    val moved = Run(game, started, started.events)
      .pick(b.bystander, VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      .answer(b.bystander, VowOfUnion.arrangeDecisionId, DistributeAnswer(
        Vector(DistributeAmount(DecisionOptionRef.Player(b.bystander), 5),
          DistributeAmount(DecisionOptionRef.Site(brokenPeaks), 0))))
    val skipped = moved.pick(b.actor, CampaignIds.kind, CampaignIds.skip)
    assertEquals(FollowUpPlay.offered(skipped.state), None)
    assertEquals(ready(skipped.state).game.current.lastCampaignResult, None)

  test("a replayed Sneak Attack Campaign reaches the same state"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val game = rules(winningDice)
    val done = commit(game, b, 4).finishPlanWindows
    val taken = accepted(FollowUpPlay.taken(game, done.state, b.rival))
    val holder = b.copy(actor = b.rival, ready = ready(done.state))
    val sneaked = committed(game, taken, holder, force = 3, raid = true)
      .finishPlanWindows
    assertEquals(ActPowerHarness.replayed(game, b.ready, sneaked.events),
      ready(sneaked.state))
```

Notes for the implementer:
- `Table` must be imported (`oathdigital.testkit.Table`) for `Table.p1` and `Table.p2`. Prefer `b.rival` and `b.actor` where a `Board` is in scope.
- The defender's Second Wind test proves ruling 7's third clause: the rival's Campaign is off turn. `rivalRulesOriginAndHoldsOathkeeper` leaves the rival's pawn with p3's, so the rival can raid p3 without travelling. If the defender's plan window parks before the attacker's, swap `finishAttackerPlans` and the `pick`.
- The skip test follows `VowOfUnionSuite`'s first test. p3's board starts with 3 warbands, so moving Broken Peaks' 2 leaves 5 on the board. Import `DistributeAmount` from `oathdigital.model`. If the arrangement's slot order differs, copy that suite's `arranged` shape.
- In the Vow of Peace test, the `Unavailable` option's fields may be named differently. Match how `WildAlliesSuite` reads an unavailable reason.
- `commit(..., 4).finishPlanWindows` against 2 bandits with `winningDice` wins the Conquest. The placement answer of 0 may leave the origin unruled: the Raid against p1, who still stands there, is the legal kind. That is why the taken Campaign commits with `raid = true`.

- [ ] **Step 6: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SneakAttackSuite"`
Expected: compile error: `SneakAttackCard` is not in `oathdigital.gameplay.powers.campaign`.

- [ ] **Step 7: Write Sneak Attack**

Move `SneakAttackCard` from `HoldingDiscordDenizens.scala` into the new file. Check how that file references cards that moved out, for example `SecondWindCard`, and list Sneak Attack the same way.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

object SneakAttackCard extends Denizen(DenizenId("98"), "Sneak Attack", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sneak-attack"),
    persistent = true, cost = Cost.free,
    text = "After another player's campaign, you may campaign, spending " +
      "no Supply, if you declare them as the defender.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Sneak Attack (card 98), a persistent rule of a faceup adviser: "After
  * another player's campaign, you may campaign, spending no Supply, if you
  * declare them as the defender."
  *
  * A `Transform` at the Campaign's root, `CampaignActionEligibility`, which
  * appends a `Branch` after the outcome, as Herald does. So it reads this
  * Campaign's recorded result, and a Campaign that ended before its result
  * (skipped, or left with nothing to attack) never reaches it. It fires
  * when the holder is not the attacker and could campaign against them
  * from the holder's pawn site (`CampaignSetup.kindsAgainst`).
  *
  * It queues an `Offer` at the front of the queue, so the holder is asked
  * before the Campaign's other follow-ups, which go to the back (catalog
  * batch 8, slice 14, ruling 6). The entry names the attacker as its one
  * start argument, the declared defender (`CampaignSetup.queuedDefender`),
  * and the card as its source, so the Campaign spends no Supply. The holder
  * walks it off turn as the attacker.
  */
final case class SneakAttack private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = SneakAttackCard.id
  def id: PowerId = SneakAttack.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.activePlayer))))

  private def offer(actor: PlayerId): Operation = Branch((ready, _) => (for
    result <- ready.game.current.lastCampaignResult
    if result.attacker == actor
    holder <- holderOf(ready)
    if holder != actor
    if CampaignSetup.kindsAgainst(ready, holder, actor).nonEmpty
  yield Vector(QueueProcedure(QueuedProcedure(holder, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(actor)),
      Some(PowerSourceRef.Card(cardId)), QueueKind.Offer), atFront = true))
  ).getOrElse(Vector.empty))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object SneakAttack:
  val id: PowerId = SneakAttackCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SneakAttack =
    new SneakAttack(catalog)
```

Register it in `PlanRules.scala` after Herald: `Vector(SneakAttack.forCatalog(catalog)) ++`. Check Herald's imports: copy the `Branch`, `QueueProcedure` and `DenizenState` imports from wherever Herald gets them.

If a power-catalog check requires every registered power to declare a line (as slice 13 found for phase powers), Sneak Attack declares none. The offer, the tag and the skip line speak for it. Say so in the check's allow list, citing ruling 12.

Regenerate the unimplemented snapshot. The diff drops `denizen.sneak-attack` only.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SneakAttackSuite oathdigital.gameplay.powers.campaign.SecondWindSuite oathdigital.gameplay.powers.campaign.HeraldSuite oathdigital.gameplay.powers.UnimplementedPowersSuite oathdigital.gameplay.CampaignSetupSuite"`
Expected: all pass.

Run: `./sbtw "test"`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SneakAttack.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/test/resources/powers/unimplemented.txt \
  src/test/scala/oathdigital/gameplay/powers/campaign/SneakAttackSuite.scala \
  src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala
git commit -m "feat(batch8): Sneak Attack campaigns off turn against the last attacker"
```

---

### Task 4: The Game Log names an off-turn actor once

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (`walker`'s `Run` actor, the `FollowUpSkipped` line)
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala` (the tag)
- Modify: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala` (the `Run` doc)
- Create: `src/test/scala/oathdigital/application/gamelog/OffTurnLogScripts.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogOffTurnSuite.scala`

**Interfaces:**
- Consumes: Tasks 1 to 3.
- Produces: `Run.actor` is the walk's actor. `StartLines` reads whether the run is off turn.

- [ ] **Step 1: Write the scripts**

Model them on `CaptainsLogScripts.scala`: `atTable(name, table, dice)`, answers through `act.withAnswers`, and `Script(name, service, actor)`. Check that `withAnswers` answers each park as its awaited player. If it answers as the script's actor, give the off-turn answers their owner in the same way `LogScripts` answers a defender's plan.

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.campaign.SneakAttackCard
import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, raidAnswers}

/** Journaled walks by a player who is not the active player, for the Game
  * Log suites (catalog batch 8, slice 14). Kept beside `LogScripts`, which
  * is at its size limit. */
object OffTurnLogScripts:
  /** p1 and p2 at Ancient City, which two bandits rule; p2 holds Sneak
    * Attack faceup. p3 stands at Buried Giant. */
  private def sneakAttackTable(using munit.Location): Table =
    Table.start.pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant").bandits("Ancient City", 2)
      .adviser(p2, SneakAttackCard.id).banditsRefilled

  private def menuCampaign: Step = MenuPlay.start(p1, ActionRef.Campaign)

  /** p1 conquers Ancient City, then p2 takes Sneak Attack and raids p1
    * there, off turn. */
  def sneakAttackTaken(using munit.Location): Script =
    val (service, act) = atTable("sneak-attack", sneakAttackTable,
      Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(FollowUpOffer.take)
    }).after(menuCampaign)
    Script("sneak-attack", service, p1)

  /** The same, with p2 skipping the offer. */
  def sneakAttackSkipped(using munit.Location): Script =
    val (service, act) = atTable("sneak-attack-skipped", sneakAttackTable,
      Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(FollowUpOffer.skip)
    }).after(menuCampaign)
    Script("sneak-attack-skipped", service, p1)
```

Notes for the implementer:
- `raidAnswers` answers the kind with Raid. p1's own Campaign is a Raid of p2 at Ancient City here, and p2's Sneak Attack is then a Raid of p1. That is fine: both are Campaigns. If `raidAnswers` picks Raid for p1 and the bandits make the kind a choice, the result is still a Campaign that records a result.
- Check `MenuPlay.start`'s signature, and `Step`'s import, against `CaptainsLogScripts`.
- A menu Campaign spends 2 Supply. Table.start gives 7.

- [ ] **Step 2: Write the failing log tests**

```scala
package oathdigital.application.gamelog

import oathdigital.testkit.Table.p2
import LogScripts._
import OffTurnLogScripts._

/** An off-turn walk's lines read as an on-turn walk's, and its tag names
  * the actor (catalog batch 8, slice 14, ruling 12). */
class GameLogOffTurnSuite extends munit.FunSuite:
  private def lines(script: Script) =
    texts(format(script, None).filter(_.depth == 1))

  test("an off-turn Campaign's tag names its attacker and its source"):
    val all = lines(sneakAttackTaken)
    val tag = s"${playerLabel(p2)} · Campaign · Sneak Attack"
    val start = all.indexWhere(_ == tag)
    assert(start >= 0, all)
    assert(all(start + 1).startsWith("Raid against "), all(start + 1))

  test("an off-turn Campaign's lines omit its attacker and name the " +
      "defender"):
    val all = lines(sneakAttackTaken)
    val start = all.indexWhere(_.endsWith(" · Campaign · Sneak Attack"))
    val after = all.drop(start + 1)
    assert(after.exists(_.startsWith("Raid against ")), after)
    assert(!after.exists(_.startsWith(s"${playerLabel(p2)} activated")),
      after)

  test("an on-turn Campaign's tag names no player"):
    val all = lines(sneakAttackTaken)
    assert(all.exists(_.startsWith("Campaign −")), all)

  test("a skipped off-turn offer names who skipped it"):
    val all = lines(sneakAttackSkipped)
    assert(all.contains(
      s"Sneak Attack: Campaign skipped by ${playerLabel(p2)}."), all)
```

Add a third script to `OffTurnLogScripts`, for the defender's off-turn Travel:

```scala
  /** p2 rules Ancient City with 2 warbands and holds Second Wind faceup,
    * with a faceup secret and a favor; p1 conquers there with blank dice
    * and loses. p2 picks Second Wind, travels to Buried Giant off turn,
    * then skips the Campaign. */
  def secondWindDefenderTravels(using munit.Location): Script =
    val secondWind = CatalogNames.denizen("Second Wind")
    val table = Table.start.pawn(p1, "Ancient City")
      .pawn(p3, "Buried Giant").warbandsAt("Ancient City", p2, 2)
      .adviser(p2, secondWind).secrets(p2, faceUp = 1).favor(p2, 1)
      .banditsRefilled
    val (service, act) = atTable("second-wind-defender", table, Dice.blanks)
    act.withAnswers({
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(DecisionOptionRef.Button("conquest"))
      case park if park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(secondWind))
      case park if park.decisionId == FollowUpOffer.decisionId &&
          park.awaiting == p2 && !travelled(park) =>
        ChooseOneAnswer(FollowUpOffer.take)
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(FollowUpOffer.skip)
      case park if park.decisionId == TravelProcedure.destinationDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Site(
          CatalogNames.site("Buried Giant")))
    }.orElse(raidAnswers(act).answers)).after(menuCampaign)
    Script("second-wind-defender", service, p1)
```

This sketch has three gaps, which you resolve from `Situation`'s `Answers` type:
- **Take, then Skip.** The script must take the first offer (the Travel) and skip the second (the Campaign). Tell them apart by the parked offer's entry: Travel or Campaign, as `FollowUpPlay.offered` reads it. Write a small private `offersTravel(park)` and drop the `travelled` stand-in.
- **Later picks.** Once Second Wind is picked, answer the defender's plan window with `CampaignIds.finish`.
- **Answer chaining.** `raidAnswers(act, plans)` takes the extra answers as its second argument, so call `raidAnswers(act, secondWindAnswers)` rather than chaining `.answers`.

The test:

```scala
  test("a defender's off-turn Travel posts its line under a tag naming " +
      "them, and the skipped Campaign names them too"):
    val all = lines(secondWindDefenderTravels)
    val tag = all.indexWhere(_ == s"${playerLabel(p2)} · Travel · Second Wind")
    assert(tag >= 0, all)
    assert(all.drop(tag + 1).exists(_.contains("Buried Giant")), all)
    assert(all.contains(
      s"Second Wind: Campaign skipped by ${playerLabel(p2)}."), all)
```

Read `ActionLines.travelLines` for the Travel line's exact words. If it names the site with its catalog name rather than "Buried Giant", assert that name.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogOffTurnSuite"`
Expected: the tag tests fail, since the tag starts "Campaign". The skip test fails, since the line ends "Campaign skipped.". The Travel test fails because the Travel line is missing: `travelLines` posts only when the mover is `run.actor`.

- [ ] **Step 4: The run's actor is the walk's**

In `GameLogFormatter.walker`, read the run's actor from the first segment's `ProcedureDequeued`, as `StartLines.queuedBy` reads its source:

```scala
    current.orElse(for
      procedure <- journal.procedureAt(at)
      ready <- journal.readyBefore(at)
    yield Run(procedure, dequeuedActor(journal, at)
      .getOrElse(ready.game.current.turn.activePlayer), at, None)) match
```

```scala
  /** The actor of the entry a run was started from: its `ProcedureDequeued`
    * is in the run's first segment. A walk with no entry is the turn's
    * player's. */
  private def dequeuedActor(journal: LogJournal, at: Int): Option[PlayerId] =
    (at to journal.segmentEnd(at)).iterator.map(journal.event)
      .collectFirst { case ProcedureDequeued(entry) => entry.actor }
```

If `ProcedureDequeued` is the event just before `at`, so that the run begins after it, scan from `journal.segmentStart(at)` or whatever `queuedBy` scans from. Read `queuedBy` and match its range exactly.

Update the `Run` doc in `LogJournal.scala`: "whose action it is (the walk's actor: a dequeued entry's actor, else the turn's player)".

- [ ] **Step 5: The tag names an off-turn actor**

In `StartLines.start`, prefix the tag when the run is off turn:

```scala
        Posted(LogKind.Section, offTurn(journal, run) ++ (Text(named) +:
          (queuedBy(journal, run, at, viewer) ++ cost(journal, run, at)))) +:
```

```scala
  /** "Blue · " when the run's actor is not the turn's player (catalog
    * batch 8, slice 14, ruling 12): the only place an off-turn walk names
    * its actor. */
  private def offTurn(journal: LogJournal, run: Run): Vector[LogSpan] =
    journal.readyBefore(run.first).filter(
      _.game.current.turn.activePlayer != run.actor).toVector
      .flatMap(_ => Vector(words.player(run.actor), Text(" · ")))
```

In `GameLogFormatter`, the `FollowUpSkipped` line becomes "… skipped by Blue." when the entry's actor is not the turn's player at `readyBefore(at)`, and stays "… skipped." otherwise. The " by " and the player span go before the final ".". Keep the two variants parallel.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, the existing Game Log suites included. Every existing run's dequeued actor is the turn's player.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala \
  src/main/scala/oathdigital/application/gamelog/StartLines.scala \
  src/main/scala/oathdigital/application/gamelog/LogJournal.scala \
  src/test/scala/oathdigital/application/gamelog/OffTurnLogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogOffTurnSuite.scala
git commit -m "feat(log): an off-turn walk's tag names its actor"
```

---

### Task 5: Rulings, docs, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 14)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", "Slice 14", "Testing", the Status line)
- Modify: `docs/architecture/follow-up-actions.md` ("Sneak Attack hook")
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

Replace the slice 14 entry of the rulings appendix with this plan's "Rulings" section, written as the appendix writes entries: one bullet per card with its printed text quoted, then the shared rules. Drop the "several holders, in turn order" sub-bullet and say why (ruling 10).

- [ ] **Step 2: Update the design**

Replace the slice 14 bullet of "The bar" with:

```markdown
- **An off-turn actor** (slice 14). A walk started from a queue entry
  belongs to the entry's actor (`CurrentGameState.walkActor`), which
  `WalkCtx.activePlayer` and `PowerCtx.activePlayer` hold. A queued start
  accepts that actor. Cost settlement, placements, menus and the HUD stay on
  the turn's player. A power's queued Campaign may name its defender as its
  one start argument (`CampaignSetup.queuedDefender`).
```

In "Slice 14: Off-turn actor", replace "`CampaignSetup.pin` fixes the defender" with "the entry's start argument fixes the defender". Add that Sneak Attack is offered before the Campaign's other follow-ups.

In "Testing", replace "two holders in turn order" with "before Second Wind's follow-ups".

- [ ] **Step 3: Update the follow-up actions doc**

Replace the "Sneak Attack hook" section of `docs/architecture/follow-up-actions.md` with a short "Off-turn actors" section:
- A queued entry's actor owns its walk.
- Sneak Attack queues its offer at the front, naming its defender.
- A defender's Second Wind queues at the back.

Remove "modifiers are selected only on turn" wherever this doc, or `SelectedModifier.scala`'s Scaladoc, still says it.

- [ ] **Step 4: Add the rename to the ROADMAP**

Under batch 8, after item 14, add:

```markdown
- [ ] **14b. Rename the walk's actor.** `WalkCtx.activePlayer` and
  `PowerCtx.activePlayer` hold the walk's actor since slice 14: a queued
  entry's actor, else the turn's player. Rename both to `actor`, across
  about 105 files. Recorded while planning slice 14 on 2026-10-16.
```

- [ ] **Step 5: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by about 6 + 5 + 13 + 5 = 29 new tests, less the one replaced test, plus any the reviews ask for. The frontend count does not change.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 6: Record the status**

- In the design's Status line, write "Slices 1 to 14 are done." If an earlier slice is still open, write the slices that are done.
- In the ROADMAP's "Now" paragraph, update the count the same way.
- Mark item 14 done (`- [x] **14. Off-turn actor:** Sneak Attack, Second Wind for a defender.`).
- Rewrite the False Prophet note that says it "needs batch 8's off-turn actor": the actor now exists, and only the player-aware card play is left.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/architecture/follow-up-actions.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 14 done"
```

Add `SelectedModifier.scala` to the list if Step 3 changed it.
