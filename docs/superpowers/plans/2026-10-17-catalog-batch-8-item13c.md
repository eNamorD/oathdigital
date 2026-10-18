# Catalog Batch 8, Item 13c (City Wall Reads an As-If Trade Site) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player who trades as if at City Wall's site is walled in there for the rest of that Trade. A player whose pawn stands there but who trades as if elsewhere is not.

**Architecture:**

- **The as-if site becomes walker scratch.** A new field, `CurrentGameState.walkerAsIfSite: Option[SiteId]`, is set by a new journaled primitive, `ActAsIf(site)`. The walker's `finish` and replay's `WalkerCompleted` clear it, as they clear `walkerDeferred`.
- **Where it is set.** `ActingSite.selectionQuestions` runs a `BuildOps` right after the site `Decide`. It reads the answer and emits `ActAsIf`.
- **One reader.** `PowerAccess.asIfSite(ready, actor)` returns the field when `actor` is the walk's actor. Every reader that took the as-if site from the walk's answers reads it from state instead: `presentSite`, `locate`, `SelectedModifier.selectable` and its overrides, `EconomySources`, and `ActingSite.offerReason`. `asIfSite(answered)` and the `answered` parameters that only carried it are deleted.
- **City Wall** reads `PowerAccess.asIfSite(ready, player)`, else `CampaignSetup.originOf`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-17-city-wall-as-if-site-design.md`: the whole design. This plan implements it.
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: slice 7 ("An as-if pawn site for Trade", "'Start' judged with the walk's answers") and slice 10 ("Enemies", "Consent").
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: 177 Small Friends, 224 Friendly Familiar, 252 City Wall.
- `src/test/scala/oathdigital/gameplay/DeferralSuite.scala`: the model for Task 1. `Defer` is the precedent for walker scratch.

**Starts from:** `main` at `819cbc87` or later, with this plan committed. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## The design's plan-time risk, settled

The design asked whether the later modifier passes and the "Start" dry runs read the state after `ActAsIf` ran. They do:

- **Live walk.** `ProcedureWalker` applies each leaf's batch to `WalkCtx.state` as it walks. The site `Decide` is answered, then the `BuildOps` runs `ActAsIf`, then the `Note`, then the `Repeat` guard. The next pass of `ModifierChoice` expands with that state as `Pass.state`.
- **Resume.** A walk resuming inside a later pass skips the earlier passes' bodies. The current pass expands with the live state, which already holds the site.
- **Dry runs.** "Start" previews `rest` from `pass.state` (`WalkerSimulation.preview`, `refusal`, and `ProcedureWalker.advance` with `seed`). The price of a pick runs `WalkerSimulation.applies` on `pass.state`. `strip` clears only `walkerPending` and `walkerProcedure`, so the field survives into each dry run.
- **The window fold rule.** `SelectedModifier`'s doc says a window must not read state that the action changes, because the walker folds every window again on each resume. `EconomySources.relocated` now reads the field at `TradeSourceSelection`. The field is set before that window is first folded, by a live walk or by a "Start" preview, and it never changes after. So every fold reads the same value.

So the state and the answers agree at every point where a reader used to read the answers, and no reader needs the answers for the as-if site.

## Departures from the design

1. **Test 2, the placing ban.** Every selected modifier is non-persistent (`CatalogResolution.of`), so City Wall gives the use reason for a modifier pick before the placing reason. No modifier pick can show the placing ban. Task 3 tests the placing ban on a payment onto a card while acting as if at the site.
2. **Test 3, after the Trade.** Task 3 tests this at the walker: a walk that runs `ActAsIf` and ends leaves no as-if site, so nothing is refused. Task 1 also tests that `WalkerCompleted` clears the field on replay.
3. **Test 5, another player.** Task 3 tests this directly against `CityWall.refusal`. No Trade path judges another player's use while acting as if.

Task 4 records these in the design.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it, and so does a non-exhaustive match.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `ProcedureWalker.scala` is at 795 lines; Task 1 adds one. `CoreOperations.scala` is at 706, and `WalkerOperationCodec.scala` at 545.
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - Engine files under `gameplay/walker` and `gameplay/operations` never name a power, in code or comments. Tasks 1 and 2 change `ProcedureWalker`, `WalkerReplay`, `ModifierChoice`, `OperationApplication` and `TurnStateOperations`. Their comments name no card.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase. Leave the untracked `HiddenScopeProbeSuite.scala` alone.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. A subagent with another model uses its own model's line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** A Trade with an as-if site now records one more step, and old journals need not replay.
- **No new Game Log line.** `ActAsIf` writes none, and Small Friends' "Acted as if at {site}." line is unchanged.

---

### Task 1: The as-if site as walker scratch

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (after `walkerDeferred`, about line 210)
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (after `TakeDeferred`, about line 688)
- Modify: `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` (after `takeDeferred`, about line 125)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala:210-211`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (lines 44, 256, 545)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:271-275`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:150-162`
- Create: `src/test/scala/oathdigital/gameplay/ActAsIfSuite.scala`
- Modify: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala` (after the deferral test, about line 483)

**Interfaces:**
- Produces: `CurrentGameState.walkerAsIfSite: Option[SiteId]` (default `None`); `final case class ActAsIf(site: SiteId) extends PrimitiveOperation` in `oathdigital.model`; wire kind `"act-as-if"` with field `"siteId"`.

- [ ] **Step 1: Write the failing model test**

Create `src/test/scala/oathdigital/gameplay/ActAsIfSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes}
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.Unwrap.accepted

/** `ActAsIf` (catalog batch 8, item 13c): it records the site the walk's
  * actor acts as if at, a parked walk keeps it, and the walk's end clears
  * it, live and on replay. */
class ActAsIfSuite extends munit.FunSuite:
  private val site = CatalogNames.site("Deep Woods")

  /** `state` after `ActAsIf(site)` ran through the pipeline. */
  private def afterActingAsIf(state: ReadyGame): ReadyGame =
    accepted(OperationPipeline.run(state, Vector(ActAsIf(site)),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  test("it records the site and moves no pawn"):
    val acting = afterActingAsIf(ready)
    assertEquals(acting.game.current.walkerAsIfSite, Some(site))
    assertEquals(pawnSiteOf(acting), pawnSiteOf(ready))

  test("a walk parked after it holds the site, and its events rebuild it"):
    val tree: Operation = Sequence(ActAsIf(site), decide)
    val park = parked(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(recorded(park.events.head).ops,
      Vector[CoreOperation](ActAsIf(site)))
    assertEquals(applyEvents(ready, park.events).game.current.walkerAsIfSite,
      Some(site))

  test("the walk's end clears it, live and on replay"):
    val done = finished(ProcedureWalker.advance(ready, ActAsIf(site), None,
      noPowers))
    assertEquals(done.treeless.game.current.walkerAsIfSite, None)
    val completed = accepted(ProcedureWalker.applyRecorded(
      OathState.Ready(afterActingAsIf(ready)),
      WalkerCompleted(ActionRef.Recover)))
    assertEquals(Outcomes.ready(completed).game.current.walkerAsIfSite, None)
```

- [ ] **Step 2: Write the failing wire test**

In `WalkerOperationWireSuite.scala`, after the test "a deferral and its take round trip, with the held ops", add:

```scala
  test("an as-if site round trips"):
    val event = recordedOperation("acting",
      ActAsIf(CatalogNames.site("Deep Woods")))
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assertEquals(ujson.read(encoded)("payload")("ops")(0)("kind").str,
      "act-as-if")
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ActAsIfSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: compilation fails with "Not found: ActAsIf" and "value walkerAsIfSite is not a member".

- [ ] **Step 4: Add the field**

In `model/GameState.scala`, add a comma after `walkerDeferred: Map[DeferralKey, Vector[CoreOperation]] = Map.empty`, then add:

```scala
    // The site the walk's actor acts as if their pawn were at, for the rest
    // of the walk (catalog batch 8, item 13c). Walker scratch, set by
    // `ActAsIf`: replay rebuilds it from the recorded `ActAsIf`, and the
    // walker and `WalkerCompleted` clear it with the dice pools. Never
    // projected.
    walkerAsIfSite: Option[SiteId] = None
```

- [ ] **Step 5: Add the primitive**

In `model/CoreOperations.scala`, after `final case class TakeDeferred(...)`, add:

```scala

/** Records that the walk's actor acts as if their pawn were at `site` for
  * the rest of the walk, in `CurrentGameState.walkerAsIfSite` (catalog
  * batch 8, item 13c). The walker clears it when the walk ends. The journal
  * records it, so a game reloaded from its events holds the same site. It
  * moves no pawn and writes no Game Log line.
  */
final case class ActAsIf(site: SiteId) extends PrimitiveOperation
```

- [ ] **Step 6: Apply it**

In `gameplay/operations/TurnStateOperations.scala`, after `takeDeferred`, add:

```scala

  /** Records the site the walk's actor acts as if at; the walker clears it
    * when the walk ends. */
  def actAsIf(ready: ReadyGame, site: SiteId): ReadyGame =
    ready.updateCurrent(_.copy(walkerAsIfSite = Some(site)))
```

In `gameplay/operations/OperationApplication.scala`, after the `TakeDeferred` case, add:

```scala
      case (result, ActAsIf(site)) =>
        result.map(TurnStateOperations.actAsIf(_, site))
```

- [ ] **Step 7: Encode and decode it**

In `serialization/WalkerOperationCodec.scala`:

- Change the last line of `TurnOperation` to `    QueueProcedure | Defer | TakeDeferred | ActAsIf`.
- After the encode case for `TakeDeferred`, add:

```scala
      case ActAsIf(site) => ujson.Obj("kind" -> "act-as-if",
        "siteId" -> site.value)
```

- After the decode case `"take-deferred"` (the last case of `decodeTurnOperation`), add:

```scala
    case "act-as-if" => Right(ActAsIf(SiteId(value("siteId").str)))
```

- [ ] **Step 8: Clear it at the walk's end**

In `gameplay/walker/ProcedureWalker.scala`, change `finish` to:

```scala
  private def finish(ctx: WalkCtx): ReadyGame =
    ctx.state.copy(game = ctx.state.game.copy(current =
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerDeferred = Map.empty,
        walkerAsIfSite = None, walkerProcedure = None)))
```

This keeps the file at 795 lines. In `gameplay/walker/WalkerReplay.scala`, in the `WalkerCompleted` case, after `walkerDeferred = Map.empty,` add `walkerAsIfSite = None,`.

- [ ] **Step 9: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ActAsIfSuite oathdigital.serialization.WalkerOperationWireSuite oathdigital.gameplay.DeferralSuite"`
Expected: PASS. If `-Werror` reports a non-exhaustive match over `CoreOperation` elsewhere, add `ActAsIf` beside `TakeDeferred` there and treat it the same way.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala src/test/scala/oathdigital/gameplay/ActAsIfSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "$(cat <<'EOF'
feat(batch8): an as-if site is walker scratch, set by ActAsIf

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The as-if readers read state

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala` (doc, `offerReason`, `selectionQuestions`)
- Modify: `src/main/scala/oathdigital/gameplay/PowerAccess.scala:19-23,40-62,84-92`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala:77-89`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala:16-45`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala:43-47`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala:42-46`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala:33-36`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala:29-59`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala:26-29,52-58`
- Modify: `src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala:104-128`
- Modify: `src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala` (the test "acting at the chosen site, the Trade draws only on its cards, and the line names it")

**Interfaces:**
- Consumes: `CurrentGameState.walkerAsIfSite`, `ActAsIf(site: SiteId)` (Task 1).
- Produces:
  - `PowerAccess.asIfSite(ready: ReadyGame, actor: PlayerId): Option[SiteId]`
  - `PowerAccess.presentSite(ready: ReadyGame, actor: PlayerId): Option[SiteId]`
  - `PowerAccess.locate(ready: ReadyGame, actor: PlayerId, card: CardId): Option[Held]`
  - `SelectedModifier.selectable(ready: ReadyGame, actor: PlayerId): Boolean`
  - `EconomySources.inPawnRegion(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId): Vector[SiteDenizenState]`
  - `PowerAccess.asIfSite(answered)` is deleted.

- [ ] **Step 1: Rewrite the PowerAccess tests on state**

In `PowerAccessSuite.scala`, replace the helper `actingAt` and the two tests after it (from `/** The walk's answer that `actor` acts as if at `site`. */` to the end of "a pawn site the actor rules stays usable beside an as-if site") with:

```scala
  /** `state` with `actor`, the walk's actor on their own turn, acting as if
    * at `site`, as `ActAsIf` leaves it. */
  private def actingAt(state: ReadyGame, site: SiteId): ReadyGame =
    state.updateCurrent(_.copy(walkerAsIfSite = Some(site)))

  test("an as-if site takes the pawn site's place: its cards are usable " +
      "and the pawn site's are not"):
    val state = Table.start.turn(actor, Phase.Act).pawn(actor, at = home)
      .denizen(wrestlers, at = home).denizen(card, at = far).ready
    val acting = actingAt(state, far)
    assertEquals(PowerAccess.locate(acting, actor, card),
      Some(PowerAccess.Held.AtSite(far)))
    assertEquals(PowerAccess.locate(acting, actor, wrestlers), None)
    // Without an as-if site, the pawn site's card is usable as before.
    assertEquals(PowerAccess.locate(state, actor, wrestlers),
      Some(PowerAccess.Held.AtSite(home)))

  test("a pawn site the actor rules stays usable beside an as-if site"):
    val state = actingAt(Table.start.turn(actor, Phase.Act)
      .pawn(actor, at = home).warbandsAt(home, actor, 1)
      .denizen(wrestlers, at = home).denizen(card, at = far).ready, far)
    assertEquals(PowerAccess.locate(state, actor, wrestlers),
      Some(PowerAccess.Held.AtSite(home)))

  test("an as-if site is the walk's actor's only"):
    val state = actingAt(Table.start.turn(actor, Phase.Act)
      .pawn(actor, at = home).pawn(other, at = home)
      .denizen(card, at = far).ready, far)
    assertEquals(PowerAccess.presentSite(state, actor), Some(far))
    assertEquals(PowerAccess.presentSite(state, other), Some(home))
    assertEquals(PowerAccess.locate(state, other, card), None)
```

- [ ] **Step 2: Pin that the site answer sets the state**

In `SmallFriendsSuite.scala`, in the test "acting at the chosen site, the Trade draws only on its cards, and the line names it", after `val acting = actingAt(tradeBegun(ready), away)` add:

```scala
    assertEquals(Outcomes.ready(acting.state).game.current.walkerAsIfSite,
      Some(away))
```

and after `val end = Look(done.state)` add:

```scala
    assertEquals(Outcomes.ready(done.state).game.current.walkerAsIfSite,
      None)
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite oathdigital.gameplay.powers.economy.SmallFriendsSuite"`
Expected: PowerAccessSuite fails to compile, since `presentSite` still takes `answered`. Once Steps 4 and 5 compile, SmallFriendsSuite's new assertion would fail without the `BuildOps` of Step 4.

- [ ] **Step 4: ActingSite records the answer**

In `ActingSite.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, SelectedModifier}`. Wrap it to stay within 80 characters.
- Replace the first doc paragraph and the start of the second with:

```scala
/** What Small Friends and Friendly Familiar share (catalog batch 8, slice
  * 7): a Trade modifier that, once picked and paid for, asks which site its
  * user acts as if at for the rest of the Trade
  * ([[PowerAccess.asIfDecision]]), and records the answer as state with
  * `ActAsIf` (item 13c).
  *
  * The site then takes the pawn site's place wherever a rule reads where
  * the player acts from ([[PowerAccess.asIfSite]]): later passes of the
  * modifier decision offer its cards' modifiers, City Wall walls the player
  * in there, and the Trade's source options are its cards
  * ([[EconomySources.relocated]]), rewritten before the powers that add
  * cards. The question offers only sites other than the pawn site, and has
  * no Skip.
```

  Keep the paragraph that starts "Unavailable, in this order:" as it is.
- In `offerReason`, change `if PowerAccess.asIfSite(ctx.answered).nonEmpty then` to `if PowerAccess.asIfSite(ctx.state, ctx.activePlayer).nonEmpty then`.
- Replace `selectionQuestions` with:

```scala
  /** The site question, then the answer recorded as state, then the line.
    * The pick and its payment come before the question, so they are judged
    * at the pawn site. */
  final override def selectionQuestions(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = Vector(
    Decide(PowerAccess.asIfDecision, actor, DecisionQuery.ChooseOne(
      sites(ready, actor).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading))),
    BuildOps((_, tree) => Right(PowerAnswers.last(tree.answered,
      PowerAccess.asIfDecision).toVector.collect {
      case DecisionOptionRef.Site(site) => ActAsIf(site)
    })),
    Note(id, actedNote))
```

- [ ] **Step 5: PowerAccess reads the state**

In `PowerAccess.scala`:

- Replace the class doc's last paragraph ("A modifier may make its user act as if ...") with:

```scala
  * A modifier may make its user act as if their pawn stood at another site
  * for the rest of the action ([[asIfDecision]]). `ActAsIf` records the
  * answer as walker scratch (`CurrentGameState.walkerAsIfSite`), and the
  * walk's end clears it. For the walk's actor, that site then takes the
  * pawn site's place for `locate` ([[presentSite]]). Ruled sites and the
  * play area stay usable; the physical pawn site does not, unless the
  * player rules it.
  */
```

- Replace `asIfDecision`'s doc, `asIfSite`, `presentSite` and `usableSites` with:

```scala
  /** The decision a modifier asks to name the site its user acts as if at
    * for the rest of the action. Only one such modifier is picked per
    * action. `ActAsIf` records its answer as state. */
  val asIfDecision: String = "modifier.as-if-site"

  /** The site `actor` acts as if at for the rest of the walk, if any. Only
    * the walk's actor acts from it; every other player acts from their
    * pawn (catalog batch 8, item 13c). */
  def asIfSite(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    val current = ready.game.current
    current.walkerAsIfSite.filter(_ => current.walkActor == actor)

  /** Where the actor acts from: the as-if site, else the pawn site. */
  def presentSite(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    asIfSite(ready, actor).orElse(pawnSite(ready, actor))

  private def usableSites(ready: ReadyGame, actor: PlayerId): Set[SiteId] =
    ruledSites(ready, actor) ++ presentSite(ready, actor)
```

- In `accessible`, change `lazy val sites = usableSites(ready, actor, Vector.empty)` to `lazy val sites = usableSites(ready, actor)`. The phase menu runs between walks, where no as-if site is held, so this changes nothing.
- Replace `locate`'s doc and signature with:

```scala
  /** Where `card` is, if `actor` may use it. A site card or edifice at a
    * usable site is `AtSite`: a site the actor rules, or the one they act
    * from ([[presentSite]]). A faceup adviser or relic in the actor's play
    * area is `InPlayArea`.
    */
  def locate(ready: ReadyGame, actor: PlayerId, card: CardId)
      : Option[Held] =
    val current = ready.game.current
    val atSite = usableSites(ready, actor).toVector
```

- [ ] **Step 6: Drop `answered` from `selectable`**

In `SelectedModifier.scala`, in `applicable`, change `selectable(ctx.state, ctx.activePlayer, ctx.answered))` to `selectable(ctx.state, ctx.activePlayer))`. Replace `selectable` with:

```scala
  /** The player may use the card, from where they act
    * ([[PowerAccess.locate]]), and can pay for it. */
  def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    PowerAccess.locate(ready, actor, cardId).isDefined &&
      Costs.affordable(ready, actor, Location.OnCard(cardId), cost)
```

In `SpitefulMirror.scala`, replace the override with:

```scala
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    if used(ready) then PowerAccess.locate(ready, actor, cardId).isDefined
    else super.selectable(ready, actor)
```

In `AwaitedReturn.scala`, replace the override with:

```scala
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PlayerFacts.player(ready, actor).exists(_.board.warbands > 0)
```

In `MapLibrary.scala`, replace the override with:

```scala
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PowerAccess.presentSite(ready, actor).exists(site =>
        SiteRulers.siteOf(ready, cardId).contains(site))
```

Its class doc has a line over 80 characters ("once. A player acting as if at another site draws on that site's region. It writes no line: ..."). Rewrap that paragraph to 80 characters with the same words.

- [ ] **Step 7: EconomySources reads the state**

In `EconomySources.scala`, replace `inPawnRegion` with:

```scala
  /** Every card with a suit at the sites in play in the region of the site
    * `actor` acts from ([[PowerAccess.presentSite]]), that site included,
    * in map then site order. A card without a suit is no source, as at the
    * pawn site. */
  def inPawnRegion(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId): Vector[SiteDenizenState] =
    val map = ready.game.current.map
    val region = PowerAccess.presentSite(ready, actor).flatMap(map.regionOf)
    map.inPlay.filter(site => region.exists(map.regionOf(site).contains))
      .flatMap(site => cardsAt(catalog, ready, site))
```

Replace `relocated`'s doc and its first line with:

```scala
  /** `operations` with each source decision's options replaced by the
    * cards at the site the actor acts as if at, offered as the pawn site's
    * are. Unchanged without such a site. The site is set before this
    * window is first folded and never changes after, so every fold of the
    * window reads the same site. */
  def relocated(catalog: ExecutableCatalog, ctx: PowerCtx,
      operations: Vector[Operation]): Vector[Operation] =
    PowerAccess.asIfSite(ctx.state, ctx.activePlayer).fold(operations)(site =>
```

In `MapLibrary.scala` and `SkilledMerchants.scala`, change the `inPawnRegion` call to:

```scala
      EconomySources.widened(ctx, operations,
        EconomySources.inPawnRegion(catalog, ctx.state, ctx.activePlayer)))))
```

- [ ] **Step 8: ModifierChoice stops handing the answers to `locate`**

In `ModifierChoice.scala`, change `candidates`' last lines to:

```scala
      power.selectionCard.exists(card =>
        PowerAccess.locate(pass.state, actor, card).isDefined))
    .sortBy(ContributingPower.sortKey)
```

Replace the doc's last paragraph ("A pick pays, then asks ...") with:

```scala
  * A pick pays, then asks the picked power's own questions
  * (`selectionQuestions`), inside the pass's second child. The next pass is
  * judged on the state those leave: which cards the actor may use, and the
  * dry runs of "Start", which also start with the walk's answers.
  */
```

- [ ] **Step 9: Confirm nothing else read the answers for the site**

Run: `grep -rn "asIfSite(\|presentSite(\|inPawnRegion(\|selectable(" src/main/scala src/test/scala`
Expected: every `asIfSite` call passes `(ready or ctx.state, actor)`; no call passes `answered` to `presentSite`, `inPawnRegion` or `selectable`.

- [ ] **Step 10: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite oathdigital.gameplay.powers.SelectedModifierSuite oathdigital.gameplay.powers.economy.* oathdigital.gameplay.walker.*"`
Expected: PASS. `SmallFriendsSuite`, `FriendlyFamiliarSuite`, `MapLibrarySuite`, `SkilledMerchantsSuite`, `SpitefulMirrorSuite` and `AwaitedReturnSuite` pass without changes beyond Step 2. If one fails, the move to state changed a behaviour. Find which reader differs before going on; do not edit the expected values.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/PowerAccess.scala src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala
git commit -m "$(cat <<'EOF'
refactor(batch8): the as-if Trade site is read from state

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: City Wall reads the as-if site

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala:1-9,19-30,63-72`
- Modify: `src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala` (imports, and a new section after the Campaign origin tests, before the Tyrant helper `holdingTyrant`)

**Interfaces:**
- Consumes: `PowerAccess.asIfSite(ready, actor)` (Task 2); `ActAsIf`, `CurrentGameState.walkerAsIfSite` (Task 1); `PowerAccess.asIfDecision`.

- [ ] **Step 1: Write the failing tests**

In `CityWallSuite.scala`, extend the imports:

- `import oathdigital.gameplay.{CampaignFixture, OathRules, PowerAccess}`
- add `import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}`
- `import oathdigital.gameplay.powerresolver.{ModifierPicks, PowerUse, PowerUseStep}`
- add `import oathdigital.model.DecisionAnswer.ChooseOneAnswer`
- add `ActPowerHarness` and `PowerRules` to the `oathdigital.testkit` import.

If `OathRules` becomes unused, the build says so; keep only what is used.

Add this section after the test "an attacker standing at City Wall's site but campaigning from elsewhere is not walled in":

```scala
  // ---- an as-if Trade site (item 13c) ------------------------------------

  private val friends = CatalogNames.denizen("Small Friends")
  private val library = CatalogNames.denizen("Map Library")
  /** A plain Beast denizen, so Small Friends may act as if at its site. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val tradeRules = PowerRules.withWalkerPowers
  private val tradeModifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val nonPersistentText =
    "Cannot use non-persistent powers while at its site"

  /** `ready` after `ActAsIf(site)` ran through the pipeline, as the walk's
    * site answer leaves it. */
  private def afterActingAsIfAt(ready: ReadyGame, site: SiteId): ReadyGame =
    accepted(OperationPipeline.run(ready, Vector(ActAsIf(site)),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  /** p1's Act holding Small Friends, with p1's pawn at `pawnAt` and City
    * Wall at Ancient City, which `ruled` gives its ruler (p2 by default).
    * Errand Boy, a Beast card, and Map Library stand at `libraryAt`. */
  private def smallFriendsAndMapLibraryAt(libraryAt: SiteId, pawnAt: SiteId,
      ruled: Table => Table = _.warbandsAt(home, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = pawnAt)
      .adviser(p1, friends).denizen(wall, at = home)
      .denizen(errandBoy, at = libraryAt).denizen(library, at = libraryAt))
      .ready

  /** p1 starts a Trade for favor, parked on the modifier decision. */
  private def tradeForFavorBegun(ready: ReadyGame): OathTransition =
    accepted(MenuPlay.started(tradeRules, Ready(ready), p1, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("favor"))))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the Trade's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(tradeRules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  /** p1's Trade for favor in `ready` with Small Friends picked and acting
    * as if at `site`, parked on the next modifier pass. */
  private def tradingAsIfAt(ready: ReadyGame, site: SiteId): OathTransition =
    answeredWith(answeredWith(tradeForFavorBegun(ready), tradeModifiers,
      DecisionOptionRef.Denizen(friends)), PowerAccess.asIfDecision,
      DecisionOptionRef.Site(site))

  /** Map Library's state and reason at the parked modifier pass. */
  private def mapLibraryOffer(at: OathTransition)
      : Option[(String, Option[String])] =
    PhasePowerHarness.queryOf(at, p1).flatMap(_.offeredOptions
      .find(_.id == library.value)
      .map(option => option.state -> option.reason))

  test("an enemy trading as if at its site is asked before a later " +
      "non-persistent modifier there"):
    val acting = tradingAsIfAt(smallFriendsAndMapLibraryAt(home,
      pawnAt = woods), home)
    assertEquals(mapLibraryOffer(acting).map(_._1), Some("askable"))
    val banditsRule = tradingAsIfAt(smallFriendsAndMapLibraryAt(home,
      pawnAt = woods, ruled = _.bandits(home, 2)), home)
    val (state, reason) = present(mapLibraryOffer(banditsRule),
      "Map Library's option")
    assertEquals(state, "unavailable")
    assert(reason.exists(_.contains(nonPersistentText)), reason.toString)

  test("an enemy acting as if at its site is refused a payment onto any " +
      "card"):
    // Every selected modifier is non-persistent, so a pick shows the use
    // reason first; the placing ban is pinned on the payment itself.
    val ban = CityWall.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(wrestlers), Cost(favor = 1))
    val away = wallAtAncientCity(pawnAt = woods)
    assertEquals(ban.reason(away, pay), None)
    assert(ban.reason(afterActingAsIfAt(away, home), pay).nonEmpty)

  test("once the walk ends, the enemy acts from their pawn again"):
    val away = wallAtAncientCity(pawnAt = woods)
    assert(CityWall.refusal(afterActingAsIfAt(away, home),
      nonPersistentUse).nonEmpty)
    val done = Outcomes.finished(ProcedureWalker.advance(away,
      ActAsIf(home), None, everyPower))
    assertEquals(CityWall.refusal(done.treeless, nonPersistentUse), None)

  test("an enemy at its site who trades as if elsewhere is not walled in " +
      "for the rest of that Trade"):
    val ready = smallFriendsAndMapLibraryAt(woods, pawnAt = home)
    // p2 lets p1 use Small Friends at City Wall's site.
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(CityWall.id, p1, "power.use",
        DecisionOptionRef.Denizen(friends))), ConsentTurn.of(ready)))
    val acting = tradingAsIfAt(granted, woods)
    assertEquals(mapLibraryOffer(acting), Some("available" -> None))

  test("only the walk's actor acts from the as-if site"):
    // Bandits rule City Wall's site, so p2 is an enemy too.
    val ready = afterActingAsIfAt(Table.start.turn(p1, Phase.Act)
      .pawn(p1, at = woods).pawn(p2, at = woods).denizen(wall, at = home)
      .bandits(home, 2).ready, home)
    assert(CityWall.refusal(ready, nonPersistentUse).nonEmpty)
    assertEquals(CityWall.refusal(ready, nonPersistentUse.copy(user = p2)),
      None)

  test("a game parked after the site answer and rebuilt from its events " +
      "keeps the enemy walled in"):
    val ready = smallFriendsAndMapLibraryAt(home, pawnAt = woods)
    val acting = tradingAsIfAt(ready, home)
    val rebuilt = ActPowerHarness.replayed(tradeRules, ready, acting.events)
    assertEquals(rebuilt.game.current.walkerAsIfSite, Some(home))
    assert(CityWall.refusal(rebuilt, nonPersistentUse).nonEmpty)
```

`everyPower`, `wallAtAncientCity`, `nonPersistentUse`, `wrestlers`, `woods` and `home` are already defined in the suite.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.CityWallSuite"`
Expected: FAIL. The first test sees Map Library "available" (City Wall reads the pawn at Deep Woods). The fourth sees it "askable" (City Wall reads the pawn at Ancient City). The second, third, fifth and sixth see no refusal where one is expected.

- [ ] **Step 3: City Wall reads where the player acts from**

In `CityWall.scala`:

- Change the import to `import oathdigital.gameplay.{PowerAccess, SiteRulers}`.
- Replace the doc's sentences from "The site is the one the player acts from" to "(ROADMAP 13c)." with:

```scala
  * site. The site is the one the player acts from: a Trade's as-if site
  * for the walk's actor, from the site answer to the end of the Trade
  * (item 13c), else a queued Campaign's origin for its attacker (slice
  * 13), else the pawn site. A player acting as if elsewhere is not walled
  * in by their pawn.
```

  Keep "A player ruler may consent outside a Campaign. It writes no line."
- Replace `walledIn` with:

```scala
  /** City Wall's ruler, when `player` is their enemy and acts from its
    * site. A Trade never has a queued origin, so the order of the two
    * readers does not matter. */
  private def walledIn(ready: ReadyGame, player: PlayerId)
      : Option[SiteRuler] =
    for
      site <- SiteRulers.siteOf(ready, CityWallCard.id)
      if PowerAccess.asIfSite(ready, player)
        .orElse(CampaignSetup.originOf(ready, player)).contains(site)
      ruler <- RulerBans.ruler(ready, CityWallCard.id)
      if RulerBans.enemy(ruler, player)
    yield ruler
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.CityWallSuite oathdigital.gameplay.powers.economy.*"`
Expected: PASS.

If the bandit case's reason does not contain the text, print `reason` and compare it with `CityWall.usePlain`'s text. The projector renders a `Noted` reason from the note's parts. Keep the assertion on the plain text.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala
git commit -m "$(cat <<'EOF'
feat(batch8): City Wall walls in a player trading as if at its site

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Docs, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-17-city-wall-as-if-site-design.md` (Status, a "Departures" section)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status, slice 7 bullets, slice 10 "Enemies")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (252 City Wall)
- Modify: `docs/ROADMAP.md` ("Now", item 13c, the line under the list)

- [ ] **Step 1: Update the item 13c design**

- Set its Status to "designed 2026-10-17; done 2026-10-17. It covers ..." and keep the rest of the line.
- Add a section "## Departures" after "## Docs", holding this plan's "Departures from the design" list in the design's own style.

- [ ] **Step 2: Update the batch 8 design**

- In slice 7's bullet "**An as-if pawn site for Trade**", replace the sentences from "`PowerAccess.locate` reads the walk's answers:" to "overrides receive them." with:

```markdown
  The answer is recorded as state, `CurrentGameState.walkerAsIfSite`, by
  `ActAsIf`, and the walk's end clears it ([item
  13c](2026-10-17-city-wall-as-if-site-design.md)). `PowerAccess.locate`
  reads it: the as-if site replaces the pawn site in `usableSites`. Ruled
  sites and the player's advisers and relics stay usable; the physical
  pawn site does not, unless the player rules it.
```

- In the bullet "**"Start" judged with the walk's answers**", after "so they see the as-if site as the walk will." add: "Since item 13c they read the site from the pass's state, and still start with the answers."
- In slice 10's "**Enemies.**" bullet, replace "City Wall's "while at this site" reads the physical pawn, not an as-if site." with:

```markdown
  City Wall's "while at this site" reads where the player acts from: a
  Trade's as-if site, a queued Campaign's origin, else the pawn ([item
  13c](2026-10-17-city-wall-as-if-site-design.md)).
```

- [ ] **Step 3: Update the rulings**

In the batch 8 rulings, 252 City Wall, replace "Small Friends' as-if Trade site is ROADMAP item 13c." with:

```markdown
  So does an as-if Trade site, for the walk's actor, from the site answer
  to the end of the Trade; it replaces the pawn site ([item
  13c](2026-10-17-city-wall-as-if-site-design.md)).
```

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test"`
Expected: everything passes. The server count rises by 3 (`ActAsIfSuite`) + 1 (wire) + 1 (`PowerAccessSuite`) + 6 (`CityWallSuite`) = 11 new tests.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 5: Record the status**

- In the batch 8 design's Status line, write "Slices 1 to 15 and items 10b, 13b and 13c are done."
- In the ROADMAP's "Now" paragraph, write "slices 1 to 15 and items 10b, 13b and 13c are done".
- Replace item 13c with:

```markdown
- [x] **13c. City Wall reads an as-if Trade site.** Small Friends' and
  Friendly Familiar's site answer is recorded as state for the rest of the
  Trade, and every rule that read it from the answers reads the state.
  City Wall walls in a player trading as if at its site, and not one whose
  pawn stands there but who trades as if elsewhere.
  Design: [City Wall reads an as-if Trade site](superpowers/specs/2026-10-17-city-wall-as-if-site-design.md).
```

- Change the line under the list to "Only item 14b of catalog batch 8 remains open."

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-10-17-city-wall-as-if-site-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md docs/ROADMAP.md
git commit -m "$(cat <<'EOF'
docs(batch8): item 13c done

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```
