# Catalog Batch 8, Slice 13 (Campaign from Another Site) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two cards:

- 198 Wild Allies: "[secret] ACTION: Campaign at any site with a [suit-beast] card. Act as if your pawn is there. Spend no Supply and add your warbands there to your force."
- 115 Captains: "[favor] ACTION: Campaign at any site you rule. Act as if your pawn is there. Spend no Supply and add your warbands there to your force."

**Architecture:**

- **A queued Campaign may carry an origin.**
  - A Campaign entry queued by a power (it has a `source`) may carry one start argument, `DecisionOptionRef.Site(origin)`.
  - `CampaignSetup.queuedOrigin` reads it from `walkerEntry`, and `CampaignSetup.originOf` reads it before the pawn site. Every Campaign rule that judges from the origin follows: targets, Raid defenders, and the Raid's discard region.
  - A Campaign started any other way, a menu Campaign included, refuses a start argument as today.
- **The origin's warbands join the force.** For a Campaign with a queued origin:
  - The force question asks 0 to B+S, where B is the actor's warbands on their board and S their warbands at the origin. Its heading names both counts.
  - The warbands at the origin stay there.
  - The `CampaignResult` records the origin's two counts (`OriginForce`). At the kill step, a question asks how many of the dead come from the origin, when that is a real choice, and the losses kill from the board and from the site.
- **The attacker's own pawn reads become origin reads.** Six powers read the attacker's pawn site during a Campaign walk. Each reads `CampaignSetup.originOf` instead: Fortress notes, Narrow Pass, Peace Envoy, Signal Trees, Vow of Union and PlanDiscard. `PlanContext.pawnAt` reads it too.
- **A phase power can check a queued start.**
  - A new trait in `powerresolver`, `QueuedStarts`, answers whether a `QueuedProcedure` would start, and why not.
  - `PhasePowerProcedure` fills it from `QueuedStart`, and hands it to a power through two defaulted `PhasePower` overloads, so no existing power changes.
- **The cards** are ACTION powers sharing one class, `CampaignFromSite`. The power pays its cost and asks for a site:
  - A site where the queued Campaign would be refused is listed, unavailable with the reason.
  - The ACTION itself is unavailable in the menu, with a reason, when no site can start.
  - Answering queues a `Run` Campaign at the front of the queue, with the site as its origin and the card as its source. A sourced entry spends no Supply, through the existing `FollowUpSupplyWaiver`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "The bar" (the slice 13 bullet), "Slice 13: Campaign from another site", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 13 entry. Task 6 replaces it with this plan's rulings, listed under "Rulings" below.
- `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md`, for the queue, the `Offer` and `Run` kinds, and the Supply waiver. `KnightsErrant.scala`, `FollowUpOffer.scala` and `QueuedStart.scala` restate what this slice uses.

**Starts from:** `main` at `bb662966` ("record Campaign kills at their moment") or later. Slice 12 runs in parallel and touches card play only. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Rulings

The product owner settled these on 2026-10-16. They override the slice 13 entry of the rulings appendix where the two differ.

1. **The walk.**
   - Each card is an ACTION. It pays its cost and asks for a site.
   - Answering queues a Campaign `Run` at the front of the queue. Its origin is the site, its source is the card, and so it spends no Supply. The Campaign asks its modifier decision as any Campaign does.
   - The Campaign accepts a site start argument only from an entry with a source, so a menu Campaign cannot fake an origin.
2. **Wild Allies' sites** are the sites holding a faceup denizen, or an edifice, whose effective suit (`EffectiveSuit.of`) is Beast. Relics don't count. The pawn site counts.
3. **Captains' sites** are the sites the user rules as a player (`PowerAccess.ruledSites`). Empire sites don't count, as with every other power. The pawn site counts. Captains is site-only, so its user is the ruler of its site.
4. **The site question** lists the qualifying sites in map order.
   - A site where the queued Campaign's start would be refused is unavailable, with the refusal's reason. The check is the full start check the menu runs (`QueuedStart`).
   - When no site can start, the ACTION is unavailable in the menu with "No site with a Beast card where a Campaign could start" (Wild Allies) or "No site you rule where a Campaign could start" (Captains).
5. **"Act as if your pawn is there."**
   - Every read of the attacker's own pawn site during the Campaign walk reads the origin. Reads of other players' pawns don't change.
   - The physical pawn doesn't move. Later walks read the real pawn site, including Second Wind's Travel and Campaign and a Knights Errant Campaign.
   - City Wall keeps reading the physical pawn, as its own ruling says ("an as-if site does not count").
6. **The force.**
   - With a queued origin, the force question asks 0 to B+S, headed "Commit warbands to the Campaign: 0 to N (B on your board, S at {site}), each adds one attack die", and suggests the whole N.
   - With no warbands at the origin, the heading is today's. A Campaign without a queued origin adds no site warbands, even at the pawn site.
   - Captains' user may name their own pawn site; its warbands then join.
7. **Deaths.** The joined warbands stay at the site. Take D deaths, B on the board and S at the site, as the result records them.
   - The site's share runs from max(0, D−B) to min(D, S).
   - When that range holds more than one value, the attacker answers "Choose how many of the D dead warbands come from {site}". It is a ChooseAmount over the range that suggests the least from the site.
   - Otherwise the one value applies without a question.
   - Survivors stay where they stand.
   - The question is asked once, at the kill step, where today's engine kills. ROADMAP item 13b moves each kill to its moment, and this question moves with it.
8. **Loss powers.**
   - Billowing Fog and Traveling Doctor cap the attacker's dead at the skulls and the sacrifices. With a split, they keep the board's kill first and trim the site's, so the site keeps its warbands.
   - Bear Traps kills a board warband when one stands outside the force: board plus origin warbands exceed the force, and the board holds one.
   - Hospital and Cursed Cauldron count both kills, as they count any kill of the force kind today.
9. **Lines.**
   - The ACTION writes "Campaign at {site}."
   - The Campaign's start line is unchanged ("Raid against … · Wild Allies").
   - The losses line counts the attacker's dead at the origin. A site kill is the defender's loss in a Conquest and the attacker's in a Raid.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. This is why `QueuedStarts` lives in `powerresolver`.
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
- **Old replays need no support.** A journal written before this plan need not decode.
- **Hover reasons.** An unusable option stays listed, unavailable with its reason. It is never hidden.
- **Log lines omit the actor.** "Campaign at {site}." names no player.
- **Lessons from earlier slices' tests:**
  - To read a parked decision, match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`.
  - Board a site's ruler with `warbandsAt(site, player, n)`, and assert the ruler (`PowerAccess.ruledSites`) before relying on it.
  - The quiet table (`Table.start`) has p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant. Each has 3 board warbands and 7 Supply, and it is p1's Act. `CampaignFixture.board` puts 2 Bandits at Ancient City, p1 there, and p2 and p3 together at the first other site. Read a site's region from `ready.game.current.map.regionOf(site)` rather than assuming it.
  - Errand Boy is a Beast denizen (`CatalogNames.denizen("Errand Boy")`).
  - `PlanDriver.winningDice` makes every attack die a sword, and `losingDice` every attack die half a sword. Both make every defense die blank, so neither rolls skulls.
- **Note keys** are declared through a `NoteSet`. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`, and check that the diff holds only the keys the task names.
- **The unimplemented-powers snapshot** is `src/test/resources/powers/unimplemented.txt`. Regenerate it with `UNIMPLEMENTED_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite"`, and check that the diff drops only the ids the task names.
- **Names, exactly:**
  - `CampaignSetup.queuedOrigin(ready, actor): Option[SiteId]`, `CampaignSetup.atOrigin(ready, actor): Int` and `CampaignSetup.originForce(ready, actor): Option[OriginForce]`.
  - `final case class OriginForce(site: SiteId, atSite: Int, onBoard: Int)` in `model/CampaignTypes.scala`, and `CampaignResult.originForce: Option[OriginForce] = None`, on the wire as `"originForce"` with `"site"`, `"atSite"` and `"onBoard"`.
  - `CampaignIds.originDeaths = "campaign.origin-deaths"`, and `CampaignAnswers.originDeaths(pending): Option[Int]`.
  - `CampaignBattle.attackerDeaths(result): Int`, `CampaignBattle.originDeathRange(origin, deaths): (Int, Int)` and `CampaignBattle.originDeathsStep(catalog, actor, result): Vector[Operation]`.
  - `trait QueuedStarts { def refusal(ready, entry): Option[OathViolation] }` with `QueuedStarts.unchecked`, in `powerresolver/PhasePower.scala`.
  - `PhasePower.build(ready, player, source, starts)` and `PhasePower.refusal(ready, player, source, starts): Option[String]`, both defaulted.
  - `CampaignFromSite` in `gameplay/powers/action/CampaignFromSite.scala`, plus `WildAllies` and `Captains` beside it, each with its card object moved in.
  - The note key `campaign-at`, "Campaign at {site}.", on each card's `NoteSet`.
  - `CampaignFixture.queuedCampaignEntry(b, origin, source)`, `dequeuedCampaignFrom(b, origin, source)` and `campaignQueuedFrom(game, b, origin, source)`.

---

### Task 1: A Campaign queued with an origin

The engine seam, with no card behind it yet. A sourced Campaign entry may name an origin. Every origin-based Campaign rule reads it, and the force question adds the warbands there.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala`. If `src/test/scala/oathdigital/gameplay/campaign/` doesn't exist, place it beside the other Campaign procedure suites (`grep -rl "class CampaignProcedure" src/test/scala`).

**Interfaces:**
- Produces:
  - `CampaignSetup.queuedOrigin(ready: ReadyGame, actor: PlayerId): Option[SiteId]`
  - `CampaignSetup.originOf`, which reads the queued origin first
  - `CampaignSetup.atOrigin(ready: ReadyGame, actor: PlayerId): Int`
  - `CampaignFixture.campaignQueuedFrom(game: OathRules, b: Board, origin: SiteId, source: PowerSourceRef): OathTransition`

- [ ] **Step 1: Add the fixture helper**

In `CampaignFixture.scala`, add the following (imports: `oathdigital.gameplay.walker.QueuedStart`, and the model types already imported there):

```scala
  /** The `Run` entry a power queues for a Campaign from `origin`. */
  def queuedCampaignEntry(b: Board, origin: SiteId,
      source: PowerSourceRef): QueuedProcedure =
    QueuedProcedure(b.actor, ActionRef.Campaign,
      Vector(DecisionOptionRef.Site(origin)), Some(source), QueueKind.Run)

  /** The board as the driver leaves it to start that entry: dequeued. */
  def dequeuedCampaignFrom(b: Board, origin: SiteId,
      source: PowerSourceRef): ReadyGame =
    QueuedStart.startingFrom(b.ready, queuedCampaignEntry(b, origin, source))

  /** A Campaign started as the driver starts a power's queued `Run` that
    * names `origin`: the entry dequeued, then the walk started with it. */
  def campaignQueuedFrom(game: OathRules, b: Board, origin: SiteId,
      source: PowerSourceRef)(using munit.Location): OathTransition =
    game.startWalker(Ready(dequeuedCampaignFrom(b, origin, source)),
      ActionRef.Campaign, b.actor, Vector(DecisionOptionRef.Site(origin)))
      .fold(error => munit.Assertions.fail(s"no start: $error"), identity)
```

If `startWalker` clears `walkerEntry` (read `OathRulesWalker.startWalker` first), start the walk the way `OathDriver.startQueued` does, through the same internal method. Record that in the commit message.

- [ ] **Step 2: Write the failing tests**

Create `CampaignOriginSuite.scala`:

```scala
package oathdigital.gameplay.campaign

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}

class CampaignOriginSuite extends munit.FunSuite:
  private val source =
    PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))

  /** p1 at the bandit-ruled Ancient City with `board` warbands on their
    * board; the rival alone at a third site, with 3 board warbands, where
    * p1 has `atSite` warbands. Returns the board and that site. */
  private def rivalAloneAtASiteTheActorHolds(board: Int, atSite: Int)(
      using munit.Location): (Board, SiteId) =
    val base = CampaignFixture.board(warbands = board, supply = 7)
    val current = base.ready.game.current
    val away = current.map.inPlay.find(site => site != base.origin &&
      !current.players.exists(_.pawnSite.contains(site))).get
    val b = on(base)(table =>
      val placed = table.pawn(base.rival, at = away)
        .boardWarbands(base.rival, 3)
      if atSite > 0 then placed.warbandsAt(away, base.actor, atSite)
      else placed)
    (b, away)

  test("a queued origin is where the Campaign judges from: a lone rival " +
      "there is the Raid's defender, though the pawn stands elsewhere"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules()
    val started = campaignQueuedFrom(game, b, away, source)
    val ready = Outcomes.ready(started.state)
    assertEquals(CampaignSetup.originOf(ready, b.actor), Some(away))
    assertEquals(CampaignSetup.raidDefenders(ready, b.actor),
      Vector(b.rival))
    assertEquals(Look(ready).pawnSite(b.actor), Some(b.origin))

  test("a Campaign started without a source refuses a site argument"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val refused = rules().startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor, Vector(DecisionOptionRef.Site(away)))
    assert(refused.isLeft, refused)

  test("the force question adds the warbands at the origin, names both " +
      "counts and suggests them all"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules()
    val run = Run(game, campaignQueuedFrom(game, b, away, source),
      Vector.empty)
    val asked = run.answeredUntil(CampaignIds.force)
    asked.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals((amount.min, amount.max, amount.suggested),
          (0, 3, Some(3)))
        val name = catalog.site(away).get.name
        assertEquals(amount.heading, Some("Commit warbands to the " +
          s"Campaign: 0 to 3 (1 on your board, 2 at $name), each adds " +
          "one attack die"))
      case other => fail(s"expected the force question, got $other")

  test("a queued Campaign spends no Supply"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 3, raid = true)
    assertEquals(Look(done.state).supply(b.actor), 7)

  test("a queued Campaign from the pawn site with no warbands there keeps " +
      "today's force heading"):
    val b = withEnemyAtOrigin(CampaignFixture.board(warbands = 2,
      supply = 7))
    val game = rules()
    val run = Run(game, campaignQueuedFrom(game, b, b.origin, source),
      Vector.empty)
    run.answeredUntil(CampaignIds.force).query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals(amount.heading, Some("Commit warbands to the " +
          "Campaign: 0 to 2, each adds one attack die"))
      case other => fail(s"expected the force question, got $other")

  test("a replayed queued Campaign reaches the same state"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 3, raid = true)
    assertEquals(ActPowerHarness.replayed(game,
      dequeuedCampaignFrom(b, away, source), done.events),
      Outcomes.ready(done.state))
```

Before running, adapt the test code to the fixtures:
- **`answeredUntil(decision)`** answers the kind, defender and targets questions until `decision` is parked. Add it to `PlanDriver.Run` if it has no such helper: it picks Raid at the kind question, the only player at the defender question, and no extra targets, using the same steps `committed` uses before it commits.
- **`done.events`** must hold every event since the start. If `committed` keeps only the events after its own answers, replay `started.events ++ done.events`.
- Use `Look`'s accessors as `Look` names them (`supply`, `pawnSite`).

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.campaign.CampaignOriginSuite"`
Expected: FAIL. The start with a site argument is refused with "takes no start selection", and the force max is 1.

- [ ] **Step 4: Read the queued origin**

In `CampaignSetup.scala`, replace `originOf` and add the two readers next to it:

```scala
  /** The origin a power's queued Campaign names, while that Campaign's
    * walk runs for `actor` (catalog batch 8, slice 13): its one start
    * argument, a site. Only an entry with a source carries one; a menu
    * Campaign never does. */
  def queuedOrigin(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    ready.game.current.walkerEntry.filter(entry =>
      entry.procedure == ActionRef.Campaign && entry.actor == actor &&
        entry.source.nonEmpty).flatMap(_.startArgs match
      case Vector(DecisionOptionRef.Site(site)) => Some(site)
      case _ => None)

  /** Where `actor` campaigns from: a queued origin, else their pawn site.
    * The mandatory Conquest site, and where a Raid's defender must stand.
    * For any other player it is their pawn site. */
  def originOf(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    queuedOrigin(ready, actor).orElse(playerOf(ready, actor)
      .flatMap(_.pawnSite))

  /** The actor's warbands at a queued origin, which join the force. A
    * Campaign from the pawn site adds none. */
  def atOrigin(ready: ReadyGame, actor: PlayerId): Int = (for
    site <- queuedOrigin(ready, actor)
    player <- playerOf(ready, actor)
    kind <- PlayerForceKind.of(ready, player)
    state <- ready.game.current.map.sites.get(site)
  yield state.forces match
    case SiteForces.Occupied(`kind`, count) => count
    case _ => 0).getOrElse(0)
```

- [ ] **Step 5: Accept the origin and widen the force**

In `CampaignProcedure.scala`:
- Replace `noStartArgs(args)` in `build` and `rebuild` with `startArgs(state, actor, args)`.
- Replace `noStartArgs` with:

```scala
  /** None, or the one site a power's queued entry names as its origin. */
  private def startArgs(state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Unit] =
    args match
      case Vector() => Right(())
      case Vector(DecisionOptionRef.Site(site))
          if CampaignSetup.queuedOrigin(state, actor).contains(site) =>
        Right(())
      case _ => Left(OathViolation.InvalidEventOrder(
        s"walker procedure ${ActionRef.Campaign.key} takes no start " +
          "selection but a power's queued origin, got " +
          args.map(_.kind).mkString(", ")))
```

Change `forceStep(state, actor)` to `forceStep(catalog, state, actor)` at its call in `battle`, and replace its body:

```scala
  /** Always asked, even for zero: a Campaign with no force is legal, and the
    * confirmation states what it commits. A queued origin's warbands join
    * the board's, and the heading names both (slice 13).
    */
  private def forceStep(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation =
    val board = state.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)
    val atSite = CampaignSetup.atOrigin(state, actor)
    val warbands = board + atSite
    val split = CampaignSetup.queuedOrigin(state, actor)
      .filter(_ => atSite > 0).flatMap(catalog.site).fold("")(site =>
        s" ($board on your board, $atSite at ${site.name})")
    // Suggests the whole force: committing everything is what an attacker
    // nearly always wants, and holding warbands back is the deliberate act.
    Decide(CampaignIds.force, actor, DecisionQuery.ChooseAmount(0, warbands,
      Some(s"Commit warbands to the Campaign: 0 to $warbands$split, each " +
        "adds one attack die"), "Commit force", suggested = Some(warbands)),
      window = Some(PowerWindow.CampaignForceSelection))
```

- [ ] **Step 6: Run the suite and the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.campaign.CampaignOriginSuite" "testOnly oathdigital.gameplay.powers.campaign.*" "testOnly oathdigital.gameplay.*Campaign*"`
Expected: PASS. No existing heading changes, because `split` is empty without a queued origin.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/test/scala/oathdigital/gameplay/CampaignFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala \
  src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala
git commit -m "feat(campaign): a power's queued Campaign may campaign from an origin"
```

Stage `PlanDriver.scala` only if Step 2 added `answeredUntil` there.

---

### Task 2: The dead at the origin

The losses learn where the force stands. The result records the origin's counts, a question splits the dead when the split is a real choice, and the loss powers and the losses line read both kills.

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala`
- Modify: `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala` (`CampaignIds`, `CampaignAnswers`, `originForce`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BearTraps.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`
- Test: `src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala`
- Test: the Billowing Fog suite (`grep -rl "BillowingFog" src/test/scala`) and `src/test/scala/oathdigital/gameplay/powers/campaign/BearTrapsSuite.scala` (or wherever Bear Traps is tested)

**Interfaces:**
- Consumes: Task 1's `queuedOrigin`, `atOrigin` and `campaignQueuedFrom`.
- Produces:
  - `OriginForce(site, atSite, onBoard)` and `CampaignResult.originForce`
  - `CampaignIds.originDeaths`, `CampaignAnswers.originDeaths`
  - `CampaignBattle.attackerDeaths`, `originDeathRange`, `originDeathsStep`
  - `CampaignBattle.losses(ready, result, pending)` (it gains `pending`)

- [ ] **Step 1: Write the failing tests**

Add to `CampaignOriginSuite`:

```scala
  /** The parked question's decision id, if any. */
  private def parkedDecision(run: Run): Option[String] =
    run.parkedHere.parkedDecision(run.state).map(_.decision)

  test("a defeated force with a real choice asks how many of the dead " +
      "come from the origin, suggesting the least, and kills from both"):
    // Four committed, two swords against three: a defeat, so two die.
    // One stands on the board, so one or two come from the site.
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 3)
    val game = rules(losingDice)
    val fought = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assertEquals(parkedDecision(fought), Some(CampaignIds.originDeaths))
    fought.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals((amount.min, amount.max, amount.suggested),
          (1, 2, Some(1)))
        val name = catalog.site(away).get.name
        assertEquals(amount.heading, Some("Choose how many of the 2 dead " +
          s"warbands come from $name"))
      case other => fail(s"expected the split question, got $other")
    val done = fought.answer(b.actor, CampaignIds.originDeaths,
      ChooseAmountAnswer(2))
    val end = Look(done.state)
    assertEquals(end.boardWarbands(b.actor), 1)
    assertEquals(end.warbandsAt(away, b.actor), 1)

  test("a split with one possible value asks nothing and kills from the " +
      "site"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 0, atSite = 4)
    val game = rules(losingDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assert(!awaits(done, b.actor, CampaignIds.originDeaths))
    assertEquals(Look(done.state).warbandsAt(away, b.actor), 2)

  test("the result records the origin's warbands and the board's before " +
      "any dies"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 3)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assertEquals(done.result.flatMap(_.originForce),
      Some(OriginForce(away, atSite = 3, onBoard = 1)))
```

Adapt the accessors to the fixtures: `Look.warbandsAt(site, player)` and `boardWarbands`, `Run.parkedHere`, `Run.result`. If `Look` has no per-player site count, read `ready.game.current.map.sites(away).forces`.

Add to `CampaignResultCodecSuite` a value and a test:

```scala
  private val fromOrigin = raid.copy(originForce =
    Some(OriginForce(SiteId("site:a"), atSite = 2, onBoard = 1)))

  test("a result with an origin's force round trips through the wire"):
    val events = Vector(recordedOperation("campaign",
      RecordCampaignResult(fromOrigin)))
    val encoded = encodedStreamThatRoundTrips("campaign-origin", events)
    assertEquals(ujson.read(encoded).arr.head("payload")("ops")(0)(
      "result")("originForce")("atSite").num.toInt, 2)
```

In the Billowing Fog suite, copy its test where a defeated attacker keeps all but the skulls and sacrifices. Change only these:
- the board, to `rivalAloneAtASiteTheActorHolds(board = 1, atSite = 3)`;
- the start, to `campaignQueuedFrom`;
- the split answer, to 2 from the site.

Name the copy "a defeated attacker's split loses no warband at the origin under Billowing Fog". Assert the site still holds 3 warbands and the board 1: the toll is 0, because `losingDice` rolls no skulls.

In the Bear Traps suite, copy its test where the trap kills a warband beyond the force. Board 1 on the board and 2 at the origin, start with `campaignQueuedFrom`, and commit 2. Name it "Bear Traps kills a board warband the force does not need, counting the origin's". Assert the board ends at 0 before the losses.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.campaign.CampaignOriginSuite" "testOnly oathdigital.serialization.CampaignResultCodecSuite"`
Expected: FAIL to compile: no `originForce`, no `OriginForce`, no `CampaignIds.originDeaths`.

- [ ] **Step 3: The model and the codec**

In `CampaignTypes.scala`, before `CampaignResult`:

```scala
/** Where a Campaign's force stood when its result was recorded, for a
  * Campaign with a queued origin whose warbands joined it (catalog batch
  * 8, slice 13): the actor's warbands at the origin and on their board.
  * The losses split the dead between the two. */
final case class OriginForce(site: SiteId, atSite: Int, onBoard: Int)
```

Add `originForce: Option[OriginForce] = None` as `CampaignResult`'s last field, and a line to its Scaladoc.

In `CampaignResultCodec.scala`:
- **Encode:** add `"originForce" -> result.originForce.fold[ujson.Value](ujson.Null)(origin => ujson.Obj("site" -> origin.site.value, "atSite" -> origin.atSite, "onBoard" -> origin.onBoard))`.
- **Decode:** read `value.obj.get("originForce").filterNot(_.isNull).map(origin => OriginForce(SiteId(origin("site").str), origin("atSite").num.toInt, origin("onBoard").num.toInt))`.
- Follow the file's own error style: if it decodes through `Either` helpers, use them, as the `attackFaces` decode does.

- [ ] **Step 4: The ids, the answer reader and the setup's reading**

In `CampaignIds`, add `val originDeaths = "campaign.origin-deaths"` with a doc comment ("How many of the attacker's dead come from a queued origin."), and add it to `all`.

In `CampaignAnswers`:

```scala
  def originDeaths(pending: PendingTree): Option[Int] =
    latest(pending, CampaignIds.originDeaths).collect:
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
```

In `object CampaignSetup`:

```scala
  /** The origin's warbands and the board's, when a queued origin's
    * warbands joined the force: what the result records. */
  def originForce(ready: ReadyGame, actor: PlayerId): Option[OriginForce] =
    for
      site <- queuedOrigin(ready, actor)
      atSite = atOrigin(ready, actor)
      if atSite > 0
      player <- playerOf(ready, actor)
    yield OriginForce(site, atSite, player.board.warbands)
```

- [ ] **Step 5: The losses**

In `CampaignBattle.scala`:
- In `result(...)`, pass `originForce = CampaignSetup.originForce(ready, setup.actor)`.
- Add:

```scala
  /** The attacker's dead: the skull and sacrifice losses, plus half the
    * survivors on a defeat. */
  def attackerDeaths(result: CampaignResult): Int =
    val survivors = result.force - result.skullLosses - result.sacrificed
    result.skullLosses + result.sacrificed +
      (if result.attackerWins then 0 else survivors / 2)

  /** How many of `deaths` may come from the origin: at least what the
    * board cannot hold, at most what the site holds. A toll beyond both
    * takes all the site holds. */
  def originDeathRange(origin: OriginForce, deaths: Int): (Int, Int) =
    val most = math.min(deaths, origin.atSite)
    (math.min(math.max(0, deaths - origin.onBoard), most), most)

  /** The question that splits the attacker's dead, asked only when the
    * split is a real choice. Read from the durable result, so the step
    * keeps its place when the losses have changed the board. */
  def originDeathsStep(catalog: ExecutableCatalog, actor: PlayerId,
      result: CampaignResult): Vector[Operation] =
    val deaths = attackerDeaths(result)
    result.originForce.toVector.flatMap { origin =>
      val (least, most) = originDeathRange(origin, deaths)
      val name = catalog.site(origin.site).fold(origin.site.value)(_.name)
      Option.when(least < most)(Decide(CampaignIds.originDeaths, actor,
        DecisionQuery.ChooseAmount(least, most, Some(s"Choose how many of " +
          s"the $deaths dead ${if deaths == 1 then "warband" else "warbands"}" +
          s" come from $name"), "Choose", suggested = Some(least)))).toVector
    }
```

Change `losses(ready, result)` to `losses(ready, result, pending: PendingTree)`. Replace its body's `deaths` and `own` with:

```scala
        val deaths = attackerDeaths(result)
        val kind = ForceKind.Exile(attacker.lineage)
        val fromSite = result.originForce.fold(0) { origin =>
          val (least, most) = originDeathRange(origin, deaths)
          CampaignAnswers.originDeaths(pending)
            .filter(n => n >= least && n <= most).getOrElse(least)
        }
        val board: Vector[CoreOperation] =
          Option.when(deaths - fromSite > 0)(Kill(
            Piece.Warbands(kind, deaths - fromSite),
            PositionedLocation(Location.PlayArea(result.attacker)))).toVector
        val site: Vector[CoreOperation] = result.originForce
          .filter(_ => fromSite > 0).toVector.map(origin => Kill(
            Piece.Warbands(kind, fromSite),
            PositionedLocation(Location.Site(origin.site))))
        val own = board ++ site
```

The site kill uses the same `kind` as the board kill (`ForceKind.Exile(attacker.lineage)`), as `CampaignBattle.losses` does today. Keep both consistent if a later change reads `PlayerForceKind`.

In `CampaignOutcome.steps`, replace the losses Sequence with:

```scala
    val losses: Operation = Sequence(
      CampaignBattle.originDeathsStep(catalog, actor, result) :+
        BuildOps((ready, pending) =>
          CampaignBattle.losses(ready, result, pending)),
      Some(PowerWindow.CampaignLosses))
```

- [ ] **Step 6: The loss powers and the line**

In `SparedForce.kept`, add before `case other`:

```scala
    // A queued origin's share of the dead (slice 13): the board's kill is
    // kept first, so the site loses only the toll the board cannot hold.
    case kill @ Kill(Piece.Warbands(`kind`, count),
        PositionedLocation(Location.Site(site), _, _))
        if side == CampaignPlanSide.Attacker &&
          result.originForce.exists(_.site == site) =>
      val toll = result.skullLosses + result.sacrificed
      val board = CampaignBattle.attackerDeaths(result) - count
      val kept = math.max(0, toll - board)
      if kept > 0 then Vector(kill.copy(warbands = Piece.Warbands(kind, kept)))
      else Vector.empty
```

Import `CampaignBattle` if needed: `powers` may import `actions`.

In `BearTraps.trap`, replace the condition:

```scala
      val board = warbands(ready, setup.actor)
      val outside = board + CampaignSetup.atOrigin(ready, setup.actor) -
        setup.force
      if board == 0 || outside <= 0 then Right(Vector.empty)
```

Update its Scaladoc: "beyond the committed force, counting a queued origin's warbands as the force's".

In `CampaignLines.losses`, replace the `killed` collect's guard so a site kill is counted by the Campaign's kind:

```scala
    // A site kill is the defender's in a Conquest, at its targets, and the
    // attacker's in a Raid, at a queued origin (slice 13).
    def fromSite(location: Location) = location match
      case _: Location.Site => result.kind match
        case CampaignKind.Conquest => loser == result.defender
        case CampaignKind.Raid =>
          loser == CampaignDefender.Player(result.attacker)
      case _ => false
    val killed = ops.collect {
      case OpStep(Kill(Piece.Warbands(_, count), PositionedLocation(place, _,
          _)), _, _) if from(place) || fromSite(place) => count
    }.sum
```

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.campaign.CampaignOriginSuite" "testOnly oathdigital.serialization.*" "testOnly oathdigital.gameplay.powers.campaign.*" "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. Fix any caller of `losses(ready, result)` the compiler names.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala \
  src/main/scala/oathdigital/serialization/CampaignResultCodec.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BearTraps.scala \
  src/main/scala/oathdigital/application/gamelog/CampaignLines.scala \
  src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala \
  src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala
git commit -m "feat(campaign): split the attacker's dead between board and origin"
```

Add the Billowing Fog and Bear Traps suite paths you edited.

---

### Task 3: The attacker's pawn reads the origin

Ruling 5. Each read of the attacker's own pawn site during a Campaign walk reads `CampaignSetup.originOf`, which falls back to the pawn site for anyone else and outside a queued Campaign.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala` (`kindNote`, line ~94)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala` (`NarrowPassSitePower.campaignBlocked`, lines ~214-234)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala` (`pawnSite`, lines ~161-162)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala` (`home`, line ~63)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala` (`region`, line ~153)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala` (`pawnRegion`, line ~56)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala` (`pawnAt`, lines ~73-76)
- Test: `SignalTreesSuite`, `PeaceEnvoySuite`, `MercenariesSuite`, `VowOfUnionSuite` and `FortressRulesSuite` under `src/test/scala/oathdigital/gameplay/powers/`, plus the Narrow Pass Campaign suite (`gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala`)

**Interfaces:**
- Consumes: Task 1's `CampaignSetup.originOf` and `CampaignFixture.campaignQueuedFrom`.

- [ ] **Step 1: Write the failing tests**

Each test copies an existing test of its suite and changes only the start and the board. The start becomes `campaignQueuedFrom(game, b, origin, source)` with `source = PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))`. On the board, the attacker's pawn moves to a site whose relevant fact differs from the origin's. Name each copy as below, and keep its assertions, aimed at the origin:

1. **SignalTreesSuite:** copy "an attacker reveals the top three and adds two dice per Beast". Put the Beasts on the discard pile of the origin's region. Put the attacker's pawn in another region whose pile holds no Beast. Name it "an attacker campaigning from an origin reveals the origin region's pile".
2. **PeaceEnvoySuite:** copy the attacker test "the attacker places one favor and gives one per defense die, and …". The enemy's pawn stands at the origin and the attacker's pawn elsewhere. Name it "an attacker campaigning from an origin finds the enemy there".
3. **MercenariesSuite:** copy "an adviser is discarded to the region after the region of its …", with the defeat it uses. The origin and the pawn are in different regions. Assert the pile after the origin's region. Name it "an adviser plan is discarded after the origin's region".
4. **VowOfUnionSuite:** copy the test quoted below, where an attacker holding the Vow commits the warbands it moved. The ruled sites stand in the origin's region, and the attacker's pawn in another region. Assert the direction question offers the move. Name it "an attacker holding the Vow gathers from the origin's region".
5. **FortressRulesSuite:** copy "the Rotting Fortress protects every player at the site, so a second …". Stand the Rotting Fortress and the two players at the origin, and the attacker's pawn elsewhere. Assert the note names the origin. Name it "the Fortress note names the origin when it shields everyone there".
6. **NarrowPassCampaignConsentSuite:** copy the test where the Pass refuses a target outside the attacker's region. The origin is inside the target's region and the attacker's pawn outside it. Assert the target is offered. Name it "the Narrow Pass judges the region from the origin".

The Vow of Union test to copy:

```scala
  test("an attacker holding it commits the warbands it moved to their " +
      "board, and the line leaves them unnamed"):
```

(`VowOfUnionSuite.scala:102`).

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SignalTreesSuite" "testOnly oathdigital.gameplay.powers.campaign.PeaceEnvoySuite" "testOnly oathdigital.gameplay.powers.campaign.MercenariesSuite" "testOnly oathdigital.gameplay.powers.campaign.VowOfUnionSuite" "testOnly oathdigital.gameplay.powers.targeting.FortressRulesSuite" "testOnly oathdigital.gameplay.powers.travel.NarrowPassCampaignConsentSuite"`
Expected: the six new tests FAIL, reading the pawn's site.

- [ ] **Step 3: Switch each read**

- **`FortressRules.kindNote`:** `pawnSite(ctx.state, ctx.activePlayer)` becomes `CampaignSetup.originOf(ctx.state, ctx.activePlayer)`. Keep the protected `pawnSite` helper for the defender reads at lines ~189 and ~212.
- **`TravelSitePowers` `campaignBlocked`:** the pawn becomes `CampaignSetup.originOf(ctx.state, ctx.activePlayer)`. Replace the error's "from the pawn site" with "from the Campaign's site". Import `oathdigital.gameplay.actions.campaign.CampaignSetup`.
- **`PeaceEnvoy`:** the private `pawnSite` helper returns `CampaignSetup.originOf(ready, player)`. Rename it `standingSite`, with the doc "Where `player` stands for the Campaign: a queued origin for the attacker, else the pawn site."
- **`SignalTrees.home`:** `CampaignSetup.originOf(ready, user).flatMap(ready.game.current.map.regionOf)`.
- **`VowOfUnion.region`:** `CampaignSetup.originOf(ready, holder).flatMap(map.regionOf)`.
- **`PlanDiscard.operations`:** `pawnRegion` reads `CampaignSetup.originOf(ready, user).flatMap(current.map.regionOf)`, and the line keeps its `PawnSiteMissing(user)` error.
- **`PlanContext.pawnAt`:** `user.exists(player => CampaignSetup.originOf(ready, player).contains(site))`. Update its doc: "has their pawn, or campaigns from a queued origin, at `site`".

Leave `CityWall.walledIn` alone (ruling 5).

- [ ] **Step 4: Run every Campaign and power suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*" "testOnly oathdigital.gameplay.campaign.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala \
  <the six edited suites>
git commit -m "feat(campaign): the attacker's pawn reads a queued origin"
```

---

### Task 4: A phase power checks a queued start, and Wild Allies

The `QueuedStarts` seam, tested through its first power.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (`usePowerEntry`, `lookup`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala` (`priced`)
- Create: `src/main/scala/oathdigital/gameplay/powers/action/CampaignFromSite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/WildAllies.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `WildAlliesCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` and `src/test/resources/powers/unimplemented.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/WildAlliesSuite.scala`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces:

```scala
trait QueuedStarts:
  def refusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation]
object QueuedStarts:
  val unchecked: QueuedStarts = (_, _) => None

// on PhasePower, defaulted:
def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
    starts: QueuedStarts): Either[OathViolation, Operation]
def refusal(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
    starts: QueuedStarts): Option[String]

abstract class CampaignFromSite(power: PrintedPower, card: DenizenId)
    extends PaidAction(power)
```

- [ ] **Step 1: Write the failing tests**

Create `WildAlliesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}

class WildAlliesSuite extends munit.FunSuite:
  private val catalog = oathdigital.gameplay.cards.NewFoundations.catalog
  private val card = WildAlliesCard.id
  private val source = DecisionOptionRef.Denizen(card)
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val map = Table.start.ready.game.current.map
  private val home = CatalogNames.site("Ancient City")
  /** A site in another region than p1's Ancient City, with no pawn. */
  private val wilds = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home) &&
      !Table.start.ready.game.current.players
        .exists(_.pawnSite.contains(site))).get

  /** p1 holds Wild Allies faceup with a secret to pay; Errand Boy, a
    * Beast, stands at the wilds, with p2's pawn and 2 of p1's warbands
    * there. */
  private def alliesWithABeastSiteHoldingTheRival: Table = Table.start
    .adviser(p1, card).secrets(p1, faceUp = 1).denizen(errandBoy,
      at = wilds).pawn(p2, at = wilds).warbandsAt(wilds, p1, 2)

  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason }

  test("with no Beast card at any site the ACTION is unavailable, with " +
      "the reason"):
    val ready = Table.start.adviser(p1, card).secrets(p1, faceUp = 1).ready
    assertEquals(menuReason(ready), Some(OptionReason.Written(
      "No site with a Beast card where a Campaign could start")))

  test("the site question lists the Beast sites, and one with nothing to " +
      "attack is unavailable with why"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions()
    // Errand Boy at the wilds with the rival; a second Beast site alone.
    val lonely = map.inPlay.find(site => site != wilds && site != home &&
      !Table.start.ready.game.current.players
        .exists(_.pawnSite.contains(site))).get
    val ready = alliesWithABeastSiteHoldingTheRival
      .denizen(CatalogNames.denizen("Wolves"), at = lonely).ready
    val asked = Unwrap.accepted(ActPowerHarness.use(rules, ready,
      WildAllies.id, source))
    val rows = PhasePowerHarness.queryOf(asked, p1).toVector
      .flatMap(_.offeredOptions)
    assertEquals(rows.map(row => row.id -> row.state).sortBy(_._1),
      Vector(wilds.value -> "available", lonely.value -> "unavailable")
        .sortBy(_._1))
    assertEquals(rows.find(_.id == lonely.value).flatMap(_.reason),
      Some("Nothing to conquer and no enemy pawn to raid"))

  test("the Campaign runs from the chosen site for no Supply, its " +
      "warbands there join the force, and the pawn stays"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions(winningDice)
    val ready = alliesWithABeastSiteHoldingTheRival.ready
    val asked = Unwrap.accepted(ActPowerHarness.use(rules, ready,
      WildAllies.id, source))
    val queued = Unwrap.accepted(ActPowerHarness.answer(rules, asked.state,
      WildAllies.siteDecisionId, DecisionOptionRef.Site(wilds)))
    val live = Outcomes.ready(queued.state)
    assertEquals(CampaignSetup.originOf(live, p1), Some(wilds))
    assertEquals(CampaignSetup.atOrigin(live, p1), 2)
    assertEquals(Look(live).pawnSite(p1), Some(home))
    assertEquals(Look(live).supply(p1), 7)
    // The secret it cost sits on the card, read as SmallFriendsSuite reads
    // tokens.
    assertEquals(Look(live).tokensOn(card), Tokens(0, 1))

  test("using it writes the site it campaigns at"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions()
    val ready = alliesWithABeastSiteHoldingTheRival.ready
    val asked = Unwrap.accepted(ActPowerHarness.use(rules, ready,
      WildAllies.id, source))
    val queued = Unwrap.accepted(ActPowerHarness.answer(rules, asked.state,
      WildAllies.siteDecisionId, DecisionOptionRef.Site(wilds)))
    assertEquals(said(WildAllies, queued.events), Vector(NoteText.Said(
      "campaign-at", s"Campaign at ${wilds.value}.", covers = false)))

  test("a replayed use reaches the same state"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions(winningDice)
    val ready = alliesWithABeastSiteHoldingTheRival.ready
    val asked = Unwrap.accepted(ActPowerHarness.use(rules, ready,
      WildAllies.id, source))
    val queued = Unwrap.accepted(ActPowerHarness.answer(rules, asked.state,
      WildAllies.siteDecisionId, DecisionOptionRef.Site(wilds)))
    val events = asked.events ++ queued.events
    assertEquals(ActPowerHarness.replayed(rules, MenuPlay.withoutMenu(
      ActPowerHarness.act(ready)), events), Outcomes.ready(queued.state))
```

Ruling 2 reads the effective suit, but Pledge to Discord changes only advisers (`EffectiveSuit.pledged`), so a site card's effective suit is its printed suit today. No test pledges a site card.

Before running, adapt:
- **"Wolves":** if it isn't a Beast denizen in the catalog, use any other Beast denizen (`catalog.denizens.filter(_.suit == Suit.Beast)`).
- **Names:** use `NoteText.Said` and `said` as `PlanDriver` names them. The note's site argument may render as the site's name rather than its id in `said`; assert what `said` renders for a `NoteArg.Site`, as other suites do.
- **The replay test:** the events of `use` include the start. If `replayed` needs the state the start ran on, pass that.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WildAlliesSuite"`
Expected: FAIL to compile: no `WildAllies`.

- [ ] **Step 3: The seam**

In `PhasePower.scala`, add the following before `trait PhasePower` (or wherever the file's types sit):

```scala
/** Whether a procedure a phase power would queue starts now, and why not
  * (catalog batch 8, slice 13): the start check the driver runs, which the
  * engine fills in. A power cannot reach the walker, so it is handed this.
  */
trait QueuedStarts:
  def refusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation]

object QueuedStarts:
  /** No engine behind it: every start is taken to be accepted. */
  val unchecked: QueuedStarts = (_, _) => None
```

In `trait PhasePower`, add:

```scala
  /** `build`, for a power that queues a procedure and must know whether
    * it would start. Every other power builds as `build` does. */
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
      starts: QueuedStarts): Either[OathViolation, Operation] =
    build(ready, player, source)

  /** Why the power cannot be used now, in the player's words, beyond
    * `usable`: for a power whose use only queues a procedure that would
    * not start. `None` by default. */
  def refusal(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
      starts: QueuedStarts): Option[String] = None
```

In `PhasePowerProcedure.scala`:
- `build` and `rebuild` gain a third first-list parameter, `walkerPowers: WalkerPowers`, with no default.
- Each builds `val starts = startsFor(catalog, powers, walkerPowers)` and calls `power.build(ready, player, source, starts)`.
- `build` also passes `starts` to `check`.
- `check` gains `starts: QueuedStarts = QueuedStarts.unchecked`. After its `usable` gate, it adds:

```scala
      _ <- power.refusal(ready, active, source, starts)
        .toLeft(()).left.map(OathViolation.StartUnavailable(_))
```

Then add:

```scala
  /** The driver's start check, for a power that queues a procedure. */
  private def startsFor(catalog: ExecutableCatalog, phasePowers: PhasePowers,
      walkerPowers: WalkerPowers): QueuedStarts = (ready, entry) =>
    QueuedStart.refusal(catalog, ready, entry, walkerPowers, phasePowers)
```

Import `oathdigital.gameplay.walker.{QueuedStart, WalkerPowers}`. `PhaseMenu` in the same package already imports both.

In `WalkerProcedureRegistry.scala`:
- `usePowerEntry(id, powers)` becomes `usePowerEntry(id, powers, walkerPowers)` and passes `walkerPowers` to both `PhasePowerProcedure` calls.
- `lookup`'s `UsePower` case passes its `walkerPowers`.

In `QueuedStart.priced`, pass `walkerPowers = powers` to `WalkerProcedureRegistry.build`. Without it, menu pricing would hand the power `WalkerPowers.empty`, which lacks the follow-up waiver.

`ForcedWakeProcedure` stays as it is; forced WAKE powers queue nothing.

- [ ] **Step 4: The shared class**

Create `CampaignFromSite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** An ACTION that campaigns from a site other than the pawn's (catalog
  * batch 8, slice 13): Wild Allies and Captains. It pays its cost onto the
  * card, asks the site, then queues a Campaign `Run` at the front of the
  * queue with the site as its origin and the card as its source, so it
  * spends no Supply. The Campaign judges its targets and force from the
  * origin, and the actor's warbands there join the force.
  *
  * The site question lists `sites` in map order, a site whose Campaign
  * would not start unavailable with why. With none that would, the ACTION
  * is unavailable with `noSite`.
  */
abstract class CampaignFromSite(power: PrintedPower, card: DenizenId)
    extends PaidAction(power):
  /** The sites the card lets its user campaign from, in map order. */
  protected def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId]
  /** The menu's reason when no site's Campaign would start. */
  protected def noSite: String
  /** The card's name, for the question's heading. */
  protected def name: String

  def siteDecisionId: String
  /** "Campaign at {site}." */
  def campaignAt: NoteKey

  override def narratedDecisions: Set[String] = Set(siteDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    build(ready, player, source, QueuedStarts.unchecked)

  override def build(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(siteDecisionId, player,
      DecisionQuery.ChooseOne(options(live, player, starts),
        heading = Some(s"$name: choose the site to campaign at"),
        confirmVerb = Some("Campaign at")),
      passWhenEmpty = true))),
    Branch((_, pending) => PowerAnswers.one(pending, siteDecisionId)
      .toVector.collect { case DecisionOptionRef.Site(site) =>
        QueueProcedure(entry(player, site), atFront = true): Operation }),
    Note(id, states => NoteSupport.answer(states, siteDecisionId).collect {
      case DecisionOptionRef.Site(site) =>
        campaignAt(PowerSourceRef.Card(card), NoteArg.Site(site)) }))))

  override def refusal(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts): Option[String] =
    Option.unless(sites(ready, player).exists(site =>
      starts.refusal(ready, entry(player, site)).isEmpty))(noSite)

  /** The queued Campaign from `site`. */
  def entry(player: PlayerId, site: SiteId): QueuedProcedure =
    QueuedProcedure(player, ActionRef.Campaign,
      Vector(DecisionOptionRef.Site(site)), Some(PowerSourceRef.Card(card)),
      QueueKind.Run)

  private def options(ready: ReadyGame, player: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    sites(ready, player).map { site =>
      val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
      starts.refusal(ready, entry(player, site)).fold[DecisionOption](shown)(
        violation => DecisionOption.Unavailable(shown,
          OptionReason.Written(CampaignFromSite.reason(violation))))
    }

object CampaignFromSite:
  /** Why a site's Campaign would not start, as the follow-up offer words
    * it, about that site. */
  def reason(violation: OathViolation): String = violation match
    case OathViolation.CampaignUnavailable(detail)
        if detail == CampaignProcedure.NothingToAttack =>
      "Nothing to conquer and no enemy pawn to raid"
    case OathViolation.CampaignUnavailable(detail) => detail
    case OathViolation.StartUnavailable(text) => text
    case _ => "Cannot start now"
```

Check the `Note` signature against `MagicCarpet` and `NoteSupport.answer`. If `Note` takes a `NoteStates => Option[PowerNote]` and the key's apply takes `(source, args*)`, keep the shape above; otherwise follow `MagicCarpet.fateNote`.

- [ ] **Step 5: Wild Allies**

Create `WildAllies.scala`, moving `WildAlliesCard` from `HoldingBeastDenizens.scala` unchanged and removing it there, with any import the holding file no longer uses:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.model._

object WildAlliesCard extends Denizen(DenizenId("198"), "Wild Allies", Suit.Beast):
  // The printed power, exactly as it stands in HoldingBeastDenizens.scala.
  val power = PrintedPower(PowerId("denizen.wild-allies"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Campaign at any site with a [suit-beast] card. " +
      "Act as if your pawn is there. Spend no Supply and add your " +
      "warbands there to your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wild Allies (card 198), ACTION, one secret: Campaign at any site with a
  * Beast card (catalog batch 8, slice 13, ruling 2): a faceup denizen, or
  * an edifice, whose effective suit is Beast. */
final case class WildAllies private (catalog: ExecutableCatalog)
    extends CampaignFromSite(WildAlliesCard.power, WildAlliesCard.id):
  private val notes = NoteSet(id)
  val campaignAt: NoteKey = notes.key("campaign-at", Vector(
    NotePart.Text("Campaign at "), NotePart.Arg(0), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  val siteDecisionId: String = "power.wild-allies.site"
  protected val name: String = WildAlliesCard.name
  protected val noSite: String =
    "No site with a Beast card where a Campaign could start"

  protected def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(site =>
      ready.game.current.map.sites.get(site).exists(_.denizens.exists {
        case card: DenizenState => card.orientation == Orientation.FaceUp &&
          beast(ready, card.id)
        case card: EdificeState => beast(ready, card.id)
        case _ => false
      }))

  private def beast(ready: ReadyGame, card: CardId): Boolean =
    EffectiveSuit.of(catalog, ready, card).contains(Suit.Beast)

object WildAllies:
  val id: PowerId = WildAlliesCard.power.id
  val siteDecisionId: String = "power.wild-allies.site"

  def forCatalog(catalog: ExecutableCatalog): WildAllies =
    new WildAllies(catalog)
```

Make the instance's `siteDecisionId` read the companion's (`WildAllies.siteDecisionId`), so the two never drift. Check how `PaidAction` subclasses declare `noteKeys` and `narratedDecisions` (`MagicCarpet`), and match them. If a `case class` with an abstract-class parent and `private` constructor clashes with `PaidAction`'s `final val id`, follow `GiantPython`'s shape.

Register it in `WorldActionPowers.forCatalog`: append `++ Vector(WildAllies.forCatalog(catalog))`.

- [ ] **Step 6: Regenerate the snapshots and run**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`, then `UNIMPLEMENTED_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.UnimplementedPowersSuite"`.
Expected: the notes diff adds only `denizen.wild-allies` `campaign-at`. The unimplemented diff drops only `denizen.wild-allies`.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*" "testOnly oathdigital.gameplay.phases.*" "testOnly oathdigital.gameplay.walker.*" "testOnly oathdigital.gameplay.BackendArchitectureSuite" "testOnly oathdigital.gameplay.CardCatalogSuite"`
Expected: PASS. If `BackendArchitectureSuite` flags `CampaignFromSite` for importing `gameplay.walker`, the class has drifted from the seam; it must import `powerresolver` only.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala \
  src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala \
  src/main/scala/oathdigital/gameplay/powers/action/CampaignFromSite.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WildAllies.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/resources/powers/unimplemented.txt \
  src/test/scala/oathdigital/gameplay/powers/action/WildAlliesSuite.scala
git commit -m "feat(batch8): Wild Allies campaigns from a Beast site"
```

---

### Task 5: Captains, and the Game Log

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Captains.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `CaptainsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` and `src/test/resources/powers/unimplemented.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/CaptainsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: Task 4's `CampaignFromSite`.

- [ ] **Step 1: Write the failing tests**

Create `CaptainsSuite.scala` in the shape of `WildAlliesSuite`. Captains is site-only, so its source is the card at a site p1 rules: `DecisionOptionRef.Denizen(CaptainsCard.id)`, the ref `PhasePowerProcedure.sources` gives for a site denizen. Check it with `ActPowerHarness.usableIds` or `PhasePowerHarness.menuPowers`.

```scala
  /** Captains at Ancient City, which p1 rules with 1 warband and where p1
    * pays a favor; p1 also rules the wilds with 2 warbands, where p2's
    * pawn stands. */
  private def captainsAtHomeWithTheRivalAtARuledSite: Table = Table.start
    .denizen(CaptainsCard.id, at = home).warbandsAt(home, p1, 1)
    .favor(p1, 1).warbandsAt(wilds, p1, 2).pawn(p2, at = wilds)
```

The tests:
1. **"the site question lists the sites the user rules, their own pawn site included"**: rows for `home` and `wilds`. `home` is unavailable with "Nothing to conquer and no enemy pawn to raid", and `wilds` is available.
2. **"ruling no site where a Campaign could start, the ACTION is unavailable with the reason"**: drop p2 from the wilds. Assert the menu reason "No site you rule where a Campaign could start".
3. **"the Campaign from a ruled site adds its warbands, spends no Supply and pays the favor"**: `atOrigin` is 2, the Supply is unchanged, and the favor sits on Captains.
4. **"a defeat with a real choice splits the dead between board and site"**: board 1, wilds 3, `losingDice`, commit 4. Answer the split with 1 and assert the board is 0 and the wilds hold 2. This is the end-to-end form of Task 2's first test, through the card.
5. **"a replayed use reaches the same state"**: as in `WildAlliesSuite`.

In `LogScripts.scala`, add a script beside `queuedRaid`, built through `atTable`, and named for what it builds:

```scala
  /** p1 uses Captains to Raid p2 at a site p1 rules, away from p1's pawn,
    * and wins. */
  lazy val captainsRaidFromARuledSite: Script = …
```

Build it as `queuedRaid` builds its Raid: answers through `raidAnswers`, `Dice.steady`, and the table of `CaptainsSuite`'s `captainsAtHomeWithTheRivalAtARuledSite`. The Captains use goes through the menu (`MenuPlay`), with the site answer `DecisionOptionRef.Site(wilds)`.

In `GameLogActionLineSuite.scala`, add:

```scala
  test("Captains: the use names the site, and the Campaign's lines follow " +
      "as any Raid's"):
    val all = lines(captainsRaidFromARuledSite, None)
    assert(all.exists(_.startsWith("Campaign at ")), all)
    val start = all.indexWhere(_.startsWith("Campaign −"))
    assert(start >= 0, all)
    assert("^Raid against .+ · Captains$".r.matches(all(start + 1)),
      all(start + 1))
```

Match the start-line pattern to what `StartLines` writes for a sourced Campaign; read `GameLogFollowUpSuite`'s queued Raid assertion and use the same form.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.CaptainsSuite" "testOnly oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: FAIL to compile: no `Captains`.

- [ ] **Step 3: Captains**

Create `Captains.scala`, moving `CaptainsCard` from `HoldingOrderDenizens.scala` unchanged (it keeps `with SiteOnly`), in the shape of `WildAllies.scala`:

```scala
/** Captains (card 115), a site-only ACTION, one favor: Campaign at any
  * site its user rules (catalog batch 8, slice 13, ruling 3), the pawn
  * site included. */
final case class Captains private (catalog: ExecutableCatalog)
    extends CampaignFromSite(CaptainsCard.power, CaptainsCard.id):
  // notes, campaignAt, noteKeys, siteDecisionId, name as Wild Allies,
  // with "power.captains.site"
  protected val noSite: String =
    "No site you rule where a Campaign could start"

  protected def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val ruled = PowerAccess.ruledSites(ready, player)
    ready.game.current.map.inPlay.filter(ruled.contains)
```

Import `oathdigital.gameplay.PowerAccess`. Register it in `WorldActionPowers.forCatalog` after Wild Allies.

- [ ] **Step 4: Regenerate the snapshots and run**

Regenerate both snapshots as in Task 4, Step 6.
Expected: the notes diff adds only `denizen.captains` `campaign-at`, and the unimplemented diff drops only `denizen.captains`.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*" "testOnly oathdigital.application.gamelog.*" "testOnly oathdigital.gameplay.CardCatalogSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Captains.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/resources/powers/unimplemented.txt \
  src/test/scala/oathdigital/gameplay/powers/action/CaptainsSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "feat(batch8): Captains campaigns from a ruled site"
```

---

### Task 6: Rulings, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 13)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", the Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

Replace the slice 13 entry of the rulings appendix with this plan's "Rulings" section, rewritten as the appendix writes entries: one bullet per card with its printed text quoted, then the shared rules. Say that ruling 7's single question follows ROADMAP item 13b when that item moves the kills.

- [ ] **Step 2: Update the design**

Replace the slice 13 bullet of "The bar" with:

```markdown
- **A queued Campaign's origin** (slice 13). A power's queued Campaign may
  carry an origin site as its one start argument. `CampaignSetup.originOf`
  reads it before the pawn site, and so do the powers that read the
  attacker's pawn during a Campaign. The force question adds the actor's
  warbands at the origin, the result records them (`OriginForce`), and the
  losses split the dead between board and site, asking when the split is a
  choice. A phase power checks whether a procedure it would queue starts
  through `QueuedStarts`, which the engine fills in.
```

- [ ] **Step 3: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by about 6 + 6 + 6 + 6 + 6 = 30 new tests, plus any the reviews ask for. The frontend count does not change.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 4: Record the status**

- In the design's Status line, write "Slices 1 to 13 are done." If slice 12 has not merged yet, write the slices that are done.
- In the ROADMAP's "Now" paragraph, update the count the same way, and keep naming whichever item it names as next.
- Mark item 13 done (`- [x] **13. Campaign from another site:** Wild Allies, Captains.`). Leave 13b open.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 13 done"
```
