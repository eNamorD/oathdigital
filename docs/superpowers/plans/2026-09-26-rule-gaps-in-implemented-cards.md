# Rule Gaps in Implemented Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the printed rules that walker migrations dropped from cards already in play: Vow of Obedience (with its REST), Secret Police and Sacred Ground forbid a faceup Vision, Book Binders rewards one, and Vow of Peace stops attackers sacrificing against its holder. To do it without refusing a choice after the player makes it, the walker learns to hide any option that would break a `Restriction`.

**Architecture:**

- **Look-ahead.** Before the walker offers a `Decide`, `WalkerPowerGather.probe` appends a hypothetical answer for each option and runs `restrictionViolations` over the tree those answers derive. An option whose answer adds a violation is removed. One function serves the live walk (where it parks or answers) and `leafAt` (projection, `accepts`, `WalkerSimulation`).
- **Vision-play cards.** Each card is a power on the card-play hook `ActionCardPlayedFaceup`. Three are `Restriction`s and Book Binders is a `Transform`. The look-ahead then removes a Vision's faceup placement from Search and from facedown-adviser play.
- **Vow of Peace.** A `Transform` at `CampaignSacrificeSelection` removes the attacker's sacrifice decision when the defender holds the Vow.

**Tech Stack:** Scala 3, munit. Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-26-rule-gaps-in-implemented-cards-design.md`. Read it all before starting. The sections map to tasks as follows:

- §2 is Task 1.
- §1 is Task 2.
- §3 is Tasks 3 and 4.
- §4 is Task 5.
- §5 is Task 6.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Work in a git worktree. `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first. Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Persisted text (code comments, docs, commit messages) is plain, complete English prose.
- Implementing a card changes which denizens a generated first game deals, because `FirstGameChronicleGenerator` puts implemented cards first. A suite that pins generated content may fail only for that reason. If one does, update its expectation, and say so in your report.
- Baselines: record the server and frontend test counts, and the wall time of `./sbtw test`, from your first full run (Task 1, Step 5).
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). The spec already records 1, 2, 4 and 5.

1. **Baseline.** An option is removed only when its answer adds a violation the answers so far do not already produce. Without this, a violation brought about earlier in the same command would empty every later decision. An example is Take Wealth's per-site use, which is recorded during the walk.
2. **The flag.** `WalkerPowers` gains `probing: Boolean = true`. The probe runs its traversal with a copy that has `probing = false`. `WalkerPowers` already reaches every dry run the traversal makes (an `OfferHost` pass calls `WalkerSimulation.applies` with the same powers), so no other signature changes. The probe runs only when `WalkerPowers.hasRestrictions`.
3. **One view of the state.** The probe runs against the state with `walkerPending = None` and `walkerProcedure` set to the walk's procedure. The live walk's state is stripped, while `leafAt` reads the persisted state, so without this the two could offer different options.
4. **Only while unplayed.** A Vision restriction binds only while the Vision is still in the player's temporary hand or among their facedown advisers (`VisionPlay.pending`). The hook stays in the tree after the play, and a later command's restriction check reads the new state. Without this, a check could refuse a play that was legal when it was made.
5. **Registration.** The three restrictions and Book Binders go into `CardPlayTriggers`. Vow of Obedience also goes into `PhasePowerCatalog`, and its rule keeps the default `Automatic` resolution, as Silver Tongue's does. The reviewed stub `RestPowers.VowOfObedience` is deleted. Gossip, Book Binders and Secret Police have no reviewed entry either.
6. **Shared favor-bank choice.** Vow of Obedience's REST and Book Binders both take favor "from any one favor bank". They share `FavorBankChoice`:
   - Several stocked banks: the player picks one.
   - One stocked bank: it is taken without asking.
   - No stocked bank: nothing happens.
   - A bank holding less than the amount: the player takes what is there.
7. **Book Binders' decision id** is `book-binders-{round}-{holder}-{vision}`. A Vision is played faceup at most once a round.
8. **Browser check.** A live game cannot be arranged to deal Vow of Obedience with a Vision, so the browser check is a smoke check on a scratch database. The suites prove the hidden option.

### Facts this plan relies on (verified against the code on 2026-09-26, at `032ba507`)

- **The walker.**
  - `ProcedureWalker.scala` (655 lines) holds `WalkerPowers` (line 41), `advance`, `roll` and `resolve`. Each builds `WalkCtx(base, Vector.empty, activePlayer, answered, powers, dice, procedure)`, at lines 137, 162 and 187.
  - `WalkCtx` is at line 272.
  - `runLeaf` (around line 444) parks a `Decide` when `cursor` is `None`. It answers one through `answerDecide` under `AnswerResume`, and `answerDecide` validates with `DecisionQueries.accepts`.
  - A rejected choose-one answer reads `OathViolation.InvalidEventOrder("decision {id} does not offer the selected option")`.
- **`WalkerPowerGather.scala`** (275 lines) holds these:
  - `applyWindow`.
  - `restrictOptions`, which narrows a `ChooseOne` or `ChooseMany` and drops an emptied optional `ChooseMany`.
  - `restrictionViolations`. Its `ctxFor` omits `answered`, and it computes `windowsIn(tree, Vector.empty)` twice.
  - `leafAt`.
- **Card play.** `CardPlayProcedure.childrenFor` builds two nodes: the placement `Decide` `cardplay.place.{kind}.{value}`, which has no window, and a `Branch`. The `Branch` reads the answer and yields:
  - the replacement decision,
  - the `BuildOps` that moves the card,
  - a hook: `CardPlayedFaceup(card, source)` (window `ActionCardPlayedFaceup`) for a faceup or site play, or `CardPlayedFacedown`.

  A Vision is offered `discard`, `adviser-faceup` and `adviser-facedown`, never `site`. Search keeps one card and plays it from the temporary hand. Play-Facedown-Adviser plays a held facedown card. These are the only faceup Vision paths.
- **Rulers.** `TravelRulers` is in `powers/travel/TravelPayments.scala` (lines 36–64) and is used only by `TollRoads.scala:34-36` and `GraspingVines.scala:32-34`. `SiteRule.enemies(left, right)` is in `model/World.scala:63`. `PowerAccess` (`gameplay/PowerAccess.scala`) is `private[gameplay]` and has `pawnSite(ready, actor)`.
- **Cards.**
  - Vow of Obedience is `DenizenId("121")`, `denizen.vow-of-obedience`, `persistent: false`.
  - Secret Police is `DenizenId("113")`, `denizen.secret-police`, site-only.
  - Book Binders is `DenizenId("140")`, `denizen.book-binders`, and gives two favor.
  - Sacred Ground is `edifice.e08.intact`, and `CatalogCards.edifice(catalog, id)` gives `EdificeId("E08")`.
  - `VisionRules.Conspiracy` is `VisionId("vision:conspiracy")`.
- **Campaign.**
  - `CampaignProcedure.sacrificeStep` builds `Decide(CampaignIds.sacrifice, actor, ChooseAmount(...), window = Some(CampaignSacrificeSelection))` inside a `Branch`, and no other power hooks that window.
  - `CampaignSetup.setup(ready, actor, PendingTree(ctx.nodePath, ctx.answered))` gives the `defender`, which is `CampaignDefender.Bandits` or `CampaignDefender.Player(id)`.
  - `CampaignSetup.sacrificed` reads zero when the decision is absent.
- **Test helpers.**
  - `SearchFixture`: `staged`, `start`, `keep`, `place`, `play` and `rules`.
  - `PowerFixture`: `base`, `actor`, `player`, `home`, `asAdviser`, `atSite` and `inPhase`.
  - `TargetsFixture`: `others`, `giveAdviser` and `giveVision`.
  - `CardStaging.without` and `ParkedNode.of`.
  - `CampaignFixture`: `board`, `againstPlayer`, `withEnemyAtOrigin`, `withAdviserFor`, `cardWith` and `rules`.
  - `PlanDriver`: `commit`, `Run`, `parked`, `ready` and `losing`.
  - `ProcedureWalkerSuite.TestRestrictionPower`.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/SiteRulers.scala`, which holds the ruler helpers moved out of Travel.
- Modify these walker files:
  - `gameplay/walker/WalkerPowerGather.scala`: `probe`, `narrowed`, and `answered` in `restrictionViolations`.
  - `gameplay/walker/ProcedureWalker.scala`: `WalkerPowers.probing` and `hasRestrictions`, `WalkCtx.root`, and the `runLeaf` wrapper.
- Create these files in `gameplay/powers/cardplay/`:
  - `VisionPlay.scala`
  - `FavorBankChoice.scala`
  - `VowOfObedience.scala`
  - `SecretPolice.scala`
  - `SacredGround.scala`
  - `BookBinders.scala`
- Modify these files:
  - `CardPlayTriggers.scala`, `PhasePowerCatalog.scala` and `RestPowers.scala`.
  - `TravelPayments.scala`, `TollRoads.scala` and `GraspingVines.scala`.
  - `VowOfPeaceContribution.scala`.
- Tests:
  - Create `RestrictionLookAheadSuite`.
  - In `powers/cardplay/`, create `VisionPlayFixture`, `VowOfObedienceSuite`, `SecretPoliceSuite`, `SacredGroundSuite` and `BookBindersSuite`.
  - Create `powers/campaign/VowOfPeaceSuite`.
  - Extend `PowerKindsCatalogSuite` and `PowerImplementationStatusSuite`.
- Docs:
  - `docs/architecture/rule-resolution.md`, `bounded-visions-and-conspiracy.md` and `bounded-campaign.md`.
  - `docs/rules/implementation-traceability.md`, `docs/ROADMAP.md` and the spec's status.

---

### Task 1: Move the ruler helpers out of Travel

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/SiteRulers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelPayments.scala:35-64`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala:32-37`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala:30-35`

**Interfaces:**
- Produces: `private[gameplay] object SiteRulers` with these methods:
  - `siteOf(ready: ReadyGame, card: DenizenId): Option[SiteId]`
  - `rulerOf(ready: ReadyGame, site: SiteId): Option[SiteRuler]`
  - `rulerOfCard(ready: ReadyGame, card: DenizenId): Option[SiteRuler]`
- An enemy is `SiteRule.enemies(ruler, SiteRuler.Player(player))`.

This is a refactor that changes no behaviour, so the existing suites are its tests.

- [ ] **Step 1: Create `SiteRulers.scala`**

```scala
package oathdigital.gameplay

import oathdigital.model._

/** Who rules a site, and where a rule card stands, for the powers whose reach
  * is "the sites this card's ruler rules": Toll Roads, Grasping Vines and
  * Secret Police. Whether a player is an enemy of a ruler is
  * `SiteRule.enemies(ruler, SiteRuler.Player(player))`, the model's single
  * definition.
  */
private[gameplay] object SiteRulers:

  /** The site holding `card` faceup. */
  def siteOf(ready: ReadyGame, card: DenizenId): Option[SiteId] =
    ready.game.current.map.sites.collectFirst:
      case (id, site) if site.denizens.exists {
        case DenizenState(`card`, Orientation.FaceUp, _) => true
        case _ => false
      } => id

  def rulerOf(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)

  /** The ruler of the site holding `card`, when that is a player or bandits.
    * An Empire ruler is not supported.
    */
  def rulerOfCard(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    siteOf(ready, card).flatMap(rulerOf(ready, _)).filter:
      case SiteRuler.Player(_) | SiteRuler.Bandits => true
      case _ => false
```

- [ ] **Step 2: Delete `TravelRulers`**

In `TravelPayments.scala`, delete everything from the comment line `/** Who rules the site a rule card stands at, for Toll Roads and Grasping Vines. */` to the end of the file. That removes the `TravelRulers` object with its `siteOf`, `rulerOf`, `rulerOfCard` and `isEnemy`.

- [ ] **Step 3: Point Toll Roads and Grasping Vines at `SiteRulers`**

In `TollRoads.scala`, add `import oathdigital.gameplay.SiteRulers` and replace the body of `toll`'s for-comprehension head with:

```scala
  private def toll(ctx: PowerCtx): Option[CoreOperation] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  yield ruler match
```

Leave the `yield` body unchanged. In `GraspingVines.scala`, add the same import and replace `kill`'s head with:

```scala
  private def kill(ctx: PowerCtx): Option[CoreOperation] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  yield Kill(warband, PositionedLocation(Location.PlayArea(route.player)))
```

- [ ] **Step 4: Run the covering suites**

Run: `./sbtw "testOnly *TollRoadsSuite *GraspingVinesSuite oathdigital.model.WorldModelSuite"`
Expected: PASS, with no test changed.

- [ ] **Step 5: Run the full server suite and record the baseline**

Run: `time ./sbtw test`
Expected: PASS. Record the test count and the wall time; Task 2 compares against the time.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/SiteRulers.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelPayments.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala
git commit -m "refactor(powers): move the site ruler helpers out of Travel"
```

---

### Task 2: Restriction look-ahead

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`WalkerPowers` line 41, the three `WalkCtx(` constructions, `WalkCtx` line 272, `runLeaf`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Test: `src/test/scala/oathdigital/gameplay/RestrictionLookAheadSuite.scala`
- Docs: `docs/architecture/rule-resolution.md`

**Interfaces:**
- Produces:
  - `WalkerPowers(powers: Vector[ContributingPower], probing: Boolean = true)`, with `lazy val hasRestrictions: Boolean`.
  - `WalkerPowerGather.probe(root: Operation, decide: Decide, state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers, answered: Vector[Answered], procedure: Option[ProcedureRef]): Either[OathViolation, Option[Decide]]`.
- Behaviour that later tasks rely on:
  - A `Restriction` hooked on a window that only an answer opens removes that option from the parked `Decide`, from `ProcedureWalker.resolve`'s check and from `openDecisions`/`parkedDecide`.
  - `PowerCtx.answered` is filled in restriction traversals.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/RestrictionLookAheadSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{OfferHost, PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers,
  WalkerSimulation}
import oathdigital.model._

/** The walker hides an option whose answer would break a `Restriction`, even
  * when the restriction's window exists only once that answer is given, so a
  * player is never refused a choice they were offered.
  */
class RestrictionLookAheadSuite extends munit.FunSuite:
  import ProcedureWalkerSuite.TestRestrictionPower

  private val ready = initialReady
  private val actor = ready.game.current.turn.activePlayer
  private val nested = PowerWindow.CampaignActionEligibility
  private val ask = "test.ask"
  private val violation = OathViolation.CampaignUnavailable("the test forbids it")
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)

  private val forbidding = TestRestrictionPower(PowerId("test.forbidding"),
    nested, (_, _) => Some(violation))
  private val powers = WalkerPowers(Vector(forbidding))

  /** A subtree carrying the forbidding window. */
  private val opened: Vector[Operation] =
    Vector(Sequence(Vector.empty, Some(nested)))

  /** `question`, then the forbidding subtree when `forbids` holds for the
    * recorded answer.
    */
  private def asking(question: DecisionQuery,
      forbids: DecisionAnswer => Boolean): Operation =
    Sequence(Vector[Operation](Decide(ask, actor, question),
      Branch((_, pending) => if pending.answered.exists {
        case Answered(`ask`, answer, _) => forbids(answer)
        case _ => false
      } then opened else Vector.empty)))

  private val yesOrNo = asking(DecisionQuery.ChooseOne(
    Vector(button("yes"), button("no"))), _ ==
      DecisionAnswer.ChooseOneAnswer(ref("yes")))

  private def parked(tree: Operation): PendingTree =
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    pending

  private def parkedQuery(tree: Operation): DecisionQuery =
    ProcedureWalker.openDecisions(ready, tree, parked(tree), powers).head.query

  test("an option whose answer opens a forbidden subtree is not offered"):
    assertEquals(parkedQuery(yesOrNo),
      DecisionQuery.ChooseOne(Vector(button("no"))))
    assertEquals(WalkerSimulation.previewParked(ready, yesOrNo, parked(yesOrNo),
      powers).map(_.map(_.option.ref)), Right(Vector(ref("no"))))

  test("a submitted forbidden answer is still rejected"):
    assertEquals(ProcedureWalker.resolve(ready, yesOrNo, parked(yesOrNo),
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("yes")), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        s"decision $ask does not offer the selected option")))
    assert(ProcedureWalker.resolve(ready, yesOrNo, parked(yesOrNo),
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("no")), actor),
      powers).isRight)

  test("a choose-many loses the forbidden option and keeps its bounds valid"):
    val tree = asking(DecisionQuery.ChooseMany(1, 2,
      Vector(button("a"), button("b"), button("c")), Some("Pick")), {
        case DecisionAnswer.ChooseManyAnswer(selected) =>
          selected.contains(ref("b"))
        case _ => false
      })
    assertEquals(parkedQuery(tree), DecisionQuery.ChooseMany(1, 2,
      Vector(button("a"), button("c")), Some("Pick")))

  test("an optional choose-many the probe empties is not asked"):
    val tree = asking(DecisionQuery.ChooseMany(0, 1, Vector(button("b")),
      Some("Pick")), _ => true)
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Finished(_, _)) => ()
      case other => fail(s"the emptied decision must be passed, got $other")

  private def amounts(forbidden: Set[Int]): Operation =
    asking(DecisionQuery.ChooseAmount(0, 4, Some("How many?"), "Confirm"), {
      case DecisionAnswer.ChooseAmountAnswer(amount) => forbidden(amount)
      case _ => false
    })

  test("a choose-amount narrows to the permitted values"):
    assertEquals(parkedQuery(amounts(Set(3, 4))),
      DecisionQuery.ChooseAmount(0, 2, Some("How many?"), "Confirm"))
    assertEquals(parkedQuery(amounts(Set(0))),
      DecisionQuery.ChooseAmount(1, 4, Some("How many?"), "Confirm"))

  test("a choose-amount whose permitted values have a gap is left whole"):
    assertEquals(parkedQuery(amounts(Set(2))),
      DecisionQuery.ChooseAmount(0, 4, Some("How many?"), "Confirm"))

  test("a required decision the probe empties stops the action from starting"):
    val onlyYes = asking(DecisionQuery.ChooseOne(Vector(button("yes"))),
      _ => true)
    assertEquals(ProcedureWalker.advance(ready, onlyYes, None, powers),
      Left(violation))
    assert(!WalkerSimulation.starts(onlyYes, ready, powers))

  test("a violation the answers so far already produce hides nothing"):
    val always = Sequence(Vector[Operation](Sequence(Vector.empty, Some(nested)),
      Decide(ask, actor, DecisionQuery.ChooseOne(Vector(button("yes"),
        button("no"))))))
    assertEquals(parkedQuery(always), DecisionQuery.ChooseOne(
      Vector(button("yes"), button("no"))))

  test("the walk inside a probe does not probe its own decisions"):
    var calls = 0
    val counting = TestRestrictionPower(PowerId("test.counting"), nested,
      (_, _) => { calls += 1; None })
    val inner: Operation = Sequence(Vector[Operation](
      Sequence(Vector.empty, Some(nested)),
      Decide("test.inner", actor, DecisionQuery.ChooseOne(
        Vector(button("x"), button("y"), button("z"))))))
    val host = new OfferHost:
      override val window: Option[PowerWindow] =
        Some(PowerWindow.CampaignAttackerBattlePlans)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] =
        val _ = pass.applies(inner)
        Vector.empty
    val root = Sequence(Vector[Operation](Sequence(Vector.empty, Some(nested)),
      Decide(ask, actor, DecisionQuery.ChooseOne(Vector(button("yes"),
        button("no")))), host))
    val _ = ProcedureWalker.advance(ready, root, None,
      WalkerPowers(Vector(counting)))
    // One traversal for the baseline and one per option, each meeting the
    // root's window once. A probe inside the host's dry run would add four
    // more traversals per host fold.
    assertEquals(calls, 3)

  test("a Restriction reads the answers so far from its context"):
    val reading = TestRestrictionPower(PowerId("test.reading"), nested,
      (ctx: PowerCtx, _) => Option.when(ctx.answered.exists(
        _.decisionId == ask))(violation))
    val root = Sequence(Vector.empty, Some(nested))
    val answered = Vector(Answered(ask,
      DecisionAnswer.ChooseOneAnswer(ref("yes")), actor))
    assertEquals(ProcedureWalker.restrictionViolations(root,
      WalkerPowers(Vector(reading)), ready, actor), Vector.empty[OathViolation])
    assertEquals(ProcedureWalker.restrictionViolations(root,
      WalkerPowers(Vector(reading)), ready, actor, answered), Vector(violation))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.RestrictionLookAheadSuite"`
Expected: FAIL. The offered-option tests still see "yes", the narrowing tests see the declared queries, the emptied decisions park, the recursion test counts 0 calls, and the answers test gets no violation. Two tests already pass, because they assert that nothing is hidden: "a violation the answers so far already produce hides nothing" and "a choose-amount whose permitted values have a gap is left whole".

- [ ] **Step 3: Give `WalkerPowers` the flag**

In `ProcedureWalker.scala`, change the import to `import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction}` and replace the `WalkerPowers` declaration (line 41) with:

```scala
final case class WalkerPowers(powers: Vector[ContributingPower],
    probing: Boolean = true):
  /** Whether any power can reject an action. The restriction look-ahead
    * ([[WalkerPowerGather.probe]]) has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = powers.exists(_.contributions.values
    .exists(_.exists(_.isInstanceOf[Restriction])))
```

Extend the scaladoc above it with one paragraph:

```scala
  * `probing` is on for every command. The restriction look-ahead turns it off
  * for the traversal it runs, so a dry run inside a probe (an `OfferHost`
  * pass) never probes in turn.
```

- [ ] **Step 4: Add `probe` and `narrowed` to `WalkerPowerGather`, and pass the answers to restrictions**

In `WalkerPowerGather.scala`:

1. Add `DecisionAnswer` and `ProcedureRef` to the `oathdigital.model` import.
2. Replace the whole `restrictOptions` method with the two methods below. `narrowed` is the old per-`Decide` body, shared with the probe:

```scala
  /** Removes the forbidden options from every `Decide` in `ops`; see
    * [[OptionRestriction]] for what happens when nothing is left.
    */
  private def restrictOptions(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[Operation] =
    if restrictions.isEmpty then ops
    else
      val permitted = permits(restrictions, ctxFor, byId)
      ops.flatMap:
        case decide: Decide => narrowed(decide, permitted).toVector
        case other => Vector(other)

  /** `decide` offering only the permitted options. `None` is an optional
    * choose-many with nothing left, which is not asked. Other query kinds
    * are returned unchanged.
    */
  private def narrowed(decide: Decide,
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
```

3. Add `probe` directly after `restrictionViolations`:

```scala
  /** The restriction look-ahead (rule-gaps design, section 1): `decide`
    * without the options whose answer would break a `Restriction` somewhere in
    * `root`. Each option is probed by appending a hypothetical answer by the
    * decision's owner to `answered` and running [[restrictionViolations]]. An
    * option is removed only when that adds a violation the answers so far do
    * not already produce, so a violation no option causes never empties a
    * decision; the answer-time check still reports it.
    *
    * `ChooseOne` and `ChooseMany` are probed option by option and narrowed as
    * [[restrictOptions]] narrows them. `ChooseAmount` is probed value by value
    * and narrowed to the permitted values when they form one range; with a
    * gap it is left whole and the answer-time check decides. Other query
    * kinds are not probed. `Left` is a required decision with nothing left,
    * carrying the first option's violation. `Right(None)` is an optional one
    * with nothing left, which is not asked.
    *
    * The traversal runs with probing off, so a dry run inside it never probes
    * in turn, and against the state as the walk sees it (no pending tree, the
    * walk's procedure), so the live walk and [[leafAt]] offer the same
    * options.
    */
  def probe(root: Operation, decide: Decide, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, answered: Vector[Answered],
      procedure: Option[ProcedureRef]): Either[OathViolation, Option[Decide]] =
    if !powers.probing || !powers.hasRestrictions then Right(Some(decide))
    else
      val quiet = powers.copy(probing = false)
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def violations(answers: Vector[Answered]): Vector[OathViolation] =
        restrictionViolations(root, quiet, seen, activePlayer, answers)
      val baseline = violations(answered).toSet
      def added(answer: DecisionAnswer): Option[OathViolation] =
        violations(answered :+ Answered(decide.decisionId, answer,
          decide.owner)).find(!baseline(_))
      def byOption(refs: Vector[DecisionOptionRef],
          answer: DecisionOptionRef => DecisionAnswer,
          required: Boolean): Either[OathViolation, Option[Decide]] =
        val verdicts = refs.map(ref => ref -> added(answer(ref))).toMap
        if required && refs.nonEmpty && refs.forall(verdicts(_).nonEmpty) then
          Left(verdicts(refs.head).get)
        else Right(narrowed(decide, ref => verdicts(ref).isEmpty))
      decide.query match
        case one: DecisionQuery.ChooseOne => byOption(one.options.map(_.ref),
          DecisionAnswer.ChooseOneAnswer(_), required = true)
        case many: DecisionQuery.ChooseMany => byOption(
          many.options.map(_.ref),
          ref => DecisionAnswer.ChooseManyAnswer(Vector(ref)), many.min >= 1)
        case amount: DecisionQuery.ChooseAmount =>
          val verdicts = (amount.min to amount.max).toVector.map(value =>
            value -> added(DecisionAnswer.ChooseAmountAnswer(value)))
          val allowed = verdicts.collect { case (value, None) => value }
          if allowed.isEmpty then Left(verdicts.flatMap(_._2).head)
          else if allowed.last - allowed.head + 1 != allowed.size then
            Right(Some(decide))
          else Right(Some(decide.copy(query = amount.copy(min = allowed.head,
            max = allowed.last, suggested = amount.suggested.map(value =>
              math.max(allowed.head, math.min(allowed.last, value)))))))
        case _ => Right(Some(decide))
```

4. In `restrictionViolations`, make `ctxFor` pass the answers. Compute the windows once, and use that value in both passes:

```scala
    def ctxFor(window: PowerWindow, path: Vector[String], operation: Operation)
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, activePlayer, power.source, window, path,
        operation, state.game.current.walkerProcedure, answered)
```

```scala
    val windows = windowsIn(tree, Vector.empty)
    val rejected = windows.flatMap:
```

```scala
    val emptied = windows.flatMap:
```

   Add one sentence to its scaladoc: "Each contribution's context carries `answered`, so a restriction can read what was chosen."

5. Probe the node `leafAt` returns. Replace `leafAt`'s body with:

```scala
    resolveAt(state, pending, powers, action, pending.at, Vector.empty,
      Set.empty).map:
        case decide: Decide => probe(action, decide, state,
          state.game.current.turn.activePlayer, powers, pending.answered,
          state.game.current.walkerProcedure).toOption.flatten
          .getOrElse(decide)
        case other => other
```

   Add to `leafAt`'s scaladoc: "A `Decide` is narrowed by the restriction look-ahead ([[probe]]), exactly as the walk narrowed it before parking. A position the walk would not have parked at, whose probe empties the decision, is returned as declared, and the answer-time check refuses it."

- [ ] **Step 5: Probe in the live walk**

In `ProcedureWalker.scala`:

1. Add `root: Operation` as the last field of `WalkCtx`, with the doc line `/** The whole action tree, which the restriction look-ahead probes. */`.
2. Pass `action` as the new last argument in the three `WalkCtx(` constructions, in `advance`, `roll` and `resolve`.
3. Rename the existing `runLeaf` to `runNarrowed`, keeping its body and signature, and add this before it:

```scala
  /** Executes or parks one leaf at its own position, recording
    * `contributions` (every enclosing window's gather order, this leaf's own
    * included) on whatever event it emits. A `Decide` where it is asked --
    * reached fresh or being answered -- is first narrowed by the restriction
    * look-ahead ([[WalkerPowerGather.probe]]). A required decision with
    * nothing left rejects the command, and an optional one with nothing left
    * is passed without asking.
    */
  private def runLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, contributions: Vector[PowerId],
      strict: Boolean): Either[OathViolation, Step] =
    leaf match
      case decide: Decide if asked(cursor, resume) =>
        WalkerPowerGather.probe(ctx.root, decide, ctx.state, ctx.activePlayer,
            ctx.powers, ctx.answered, ctx.procedure).flatMap:
          case Some(narrowed) => runNarrowed(narrowed, ctx, path, cursor,
            resume, contributions, strict)
          case None if cursor.isEmpty => Right(Done(ctx))
          case None => Left(OathViolation.InvalidEventOrder(
            s"decision ${decide.decisionId} has nothing left to offer"))
      case _ => runNarrowed(leaf, ctx, path, cursor, resume, contributions,
        strict)

  /** Whether the leaf is where a decision is asked: reached fresh, or at the
    * cursor's end while an answer resumes. A plain resume only re-parks it.
    */
  private def asked(cursor: Option[Vector[String]], resume: Resume): Boolean =
    cursor match
      case None => true
      case Some(remaining) =>
        remaining.isEmpty && resume.isInstanceOf[AnswerResume]
```

   Change `runNarrowed`'s scaladoc to: "Executes or parks one leaf that the look-ahead has already narrowed."

- [ ] **Step 6: Run the new suite**

Run: `./sbtw "testOnly oathdigital.gameplay.RestrictionLookAheadSuite"`
Expected: PASS.

- [ ] **Step 7: Run the whole server suite and compare the time**

Run: `time ./sbtw test`
Expected: PASS with the Task 1 count plus the new tests.

- If a suite fails because an option is now hidden where it used to be refused, check whether the restriction concerned really depends on that answer. If it does, the new behaviour is right: update the test to assert the option is absent, and say so in your report. If it does not, stop and report.
- If the wall time grew by more than 25% over Task 1's, stop and report the two times, and do not optimise on your own.

- [ ] **Step 8: Document the look-ahead**

In `docs/architecture/rule-resolution.md`, insert this paragraph after the paragraph that begins "A power can forbid a choice as well as a whole action.":

```markdown
A `Restriction` also hides options. Before the walker offers a decision it
probes each option: it appends a hypothetical answer and runs the restriction
traversal over the tree those answers derive
(`WalkerPowerGather.probe`). An option whose answer adds a violation the
answers so far do not already produce is not offered, in the walk, in
`accepts`, in the projector and in `WalkerSimulation`. `ChooseOne` and
`ChooseMany` are probed per option and `ChooseAmount` per value; a range with
a gap is left whole. A required decision left with nothing rejects the action,
so its start control is hidden. The probe's own traversal does not probe
(`WalkerPowers.probing`), and it runs only when some power contributes a
`Restriction`. The answer-time check stays as a backstop. So a "cannot" card
needs only a `Restriction`, even when the window it checks exists only after
the answer, as a card-play hook does.
```

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/test/scala/oathdigital/gameplay/RestrictionLookAheadSuite.scala \
  docs/architecture/rule-resolution.md
git commit -m "feat(walker): hide options whose answer would break a restriction"
```

---

### Task 3: Vow of Obedience

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RestPowers.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/cardplay/VisionPlayFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: Task 2's look-ahead, which removes a forbidden faceup placement.
- Produces:
  - `private[cardplay] object VisionPlay` with these methods:
    - `played(ctx: PowerCtx): Option[VisionId]`
    - `pending(ctx: PowerCtx): Option[VisionId]`
    - `forbidden(card: String): OathViolation`
  - `private[cardplay] object FavorBankChoice` with these methods:
    - `stocked(ready: ReadyGame): Vector[Suit]`
    - `take(ready: ReadyGame, player: PlayerId, amount: Int, decisionId: String, heading: String): Vector[Operation]`
  - `VowOfObedience` with these members:
    - `VowOfObedience.id`
    - `VowOfObedience.forCatalog(catalog): Option[VowOfObedience]`
    - `VowOfObedience.choiceDecisionId(ready, player): String`
    - `cardId`
  - `VisionPlayFixture` (test), with these methods:
    - `searched(ready: ReadyGame, vision: VisionId): OathTransition`
    - `fromAdvisers(ready: ReadyGame, vision: VisionId): OathTransition`
    - `offered(from: OathTransition): Vector[String]`

- [ ] **Step 1: Write the test fixture**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/VisionPlayFixture.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture,
  TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.ParkedNode

/** The two ways to play a Vision, each stopped at its placement decision, for
  * the suites of the cards that forbid or reward a faceup Vision.
  */
object VisionPlayFixture:
  import PowerFixture._

  private def orFail(result: Either[OathViolation, OathTransition])
      : OathTransition =
    result.fold(error => throw new AssertionError(error.toString), identity)

  /** A Search that drew `vision`, parked on its placement. `ready` must come
    * from `SearchFixture.staged(Vector(vision))`.
    */
  def searched(ready: ReadyGame, vision: VisionId): OathTransition =
    orFail(SearchFixture.start(ready).flatMap(SearchFixture.keep(_, vision)))

  /** The Play-Facedown-Adviser action on `vision`, which the actor is given as
    * a facedown adviser, parked on its placement. `ready` must be in the Act
    * phase.
    */
  def fromAdvisers(ready: ReadyGame, vision: VisionId): OathTransition =
    val holding = TargetsFixture.giveVision(CardStaging.without(ready, vision),
      actor, vision, Orientation.FaceDown)
    orFail(SearchFixture.rules.startWalker(Ready(holding),
      ActionRef.PlayFacedownAdviser, actor, Vector.empty,
      Vector(DecisionOptionRef.Vision(vision))))

  /** The buttons of the placement decision the walk is parked on. */
  def offered(from: OathTransition): Vector[String] =
    ParkedNode.of(from.state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref).collect {
          case DecisionOptionRef.Button(key) => key }
        case other => throw new AssertionError(s"not a choose-one: $other")
      case other => throw new AssertionError(s"not parked on a decision: $other")
```

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{CardStaging, PhasePowerCatalog, PowerFixture,
  PowerImplementationStatus, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready

class VowOfObedienceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val vow = DenizenId("121")
  private val other = TargetsFixture.others(base).head

  private def holding(ready: ReadyGame, owner: PlayerId = actor,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(CardStaging.without(ready, vow), owner, vow,
      orientation)

  private def search(vision: VisionId, arrange: ReadyGame => ReadyGame)
      : OathTransition =
    val ready = arrange(SearchFixture.staged(Vector(vision)))
    searched(ready, vision)

  test("Vow of Obedience is registered as a walker rule and a REST power, " +
      "and is implemented"):
    val power = VowOfObedience.forCatalog(catalog).get
    assertEquals(power.cardId, vow)
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == VowOfObedience.id))
    assert(PhasePowerCatalog.default(catalog).find(VowOfObedience.id).nonEmpty)
    assert(PowerImplementationStatus.implemented(catalog)(VowOfObedience.id))

  test("a faceup holder's Search does not offer a Vision faceup, and refuses it"):
    val parkedAt = search(VisionRules.Faith, holding(_))
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("the holder cannot play a facedown Vision faceup either"):
    val parkedAt = fromAdvisers(holding(inPhase(base, Phase.Act)),
      VisionRules.Faith)
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))

  test("the Conspiracy is a Vision, so it is forbidden too"):
    val parkedAt = search(VisionRules.Conspiracy, holding(_))
    assert(!offered(parkedAt).contains("adviser-faceup"))

  test("a facedown Vow, or another player's faceup Vow, forbids nothing"):
    assert(offered(search(VisionRules.Faith, holding(_,
      orientation = Orientation.FaceDown))).contains("adviser-faceup"))
    assert(offered(search(VisionRules.Faith, holding(_, owner = other)))
      .contains("adviser-faceup"))

  // ---- REST: Take a favor from any one favor bank ----

  private val restRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val restParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
  private val use = ActionRef.UsePower(VowOfObedience.id)
  private val source = DecisionOptionRef.Denizen(vow)

  /** The Rest phase, the actor holding a faceup Vow, with exactly these banks
    * holding favor.
    */
  private def resting(stocked: Map[Suit, Int]): ReadyGame =
    val ready = inPhase(holding(base), Phase.Rest)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(suit => suit -> stocked.getOrElse(suit, 0)).toMap))

  private def rest(ready: ReadyGame): OathTransition = restRules.startWalker(
    Ready(ready), use, actor, Vector.empty, Vector(source)).fold(
    error => throw new AssertionError(error.toString), identity)

  test("REST: several stocked banks ask which one to take a favor from"):
    val ready = resting(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val choice = VowOfObedience.choiceDecisionId(ready, actor)
    val started = rest(ready)
    restParked.assertParked(started.state, use, choice, actor)
    val taken = restRules.resolveWalker(started.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .toOption.get
    val after = SearchFixture.after(taken)
    assertEquals(after.banks.favor(Suit.Order), 2)
    assertEquals(after.banks.favor(Suit.Arcane), 3)
    assertEquals(player(after).board.favor, player(ready).board.favor + 1)

  test("REST: one stocked bank is taken without asking"):
    val ready = resting(Map(Suit.Hearth -> 2))
    val after = SearchFixture.after(rest(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 1)
    assertEquals(player(after).board.favor, player(ready).board.favor + 1)

  test("REST: with every bank empty the power is not offered"):
    assert(!PhasePowerProcedure.usable(catalog, resting(Map.empty), actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == VowOfObedience.id))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.VowOfObedienceSuite"`
Expected: FAIL to compile, because `VowOfObedience` is not defined.

- [ ] **Step 4: Write `VisionPlay`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Reads a faceup Vision play off the card-play hook
  * (`ActionCardPlayedFaceup`), for the cards that forbid or reward one. The
  * player is the one whose action runs, `ctx.activePlayer`.
  */
private[cardplay] object VisionPlay:

  /** The Vision the hook plays faceup, the Conspiracy included. */
  def played(ctx: PowerCtx): Option[VisionId] = ctx.operation match
    case CardPlayedFaceup(card: VisionId, _) => Some(card)
    case _ => None

  /** The Vision the hook plays faceup, while the play is still to be made:
    * the card is in the player's temporary hand or among their facedown
    * advisers. The hook stays in the tree after the card has moved, and a
    * restriction must not refuse a play that was legal when it was made
    * because the state has changed since.
    */
  def pending(ctx: PowerCtx): Option[VisionId] =
    played(ctx).filter(held(ctx.state, ctx.activePlayer, _))

  def forbidden(card: String): OathViolation =
    OathViolation.InvalidSearchPlacement(s"$card forbids playing a Vision faceup")

  private def held(ready: ReadyGame, player: PlayerId, card: VisionId): Boolean =
    val current = ready.game.current
    current.temporaryHands.getOrElse(player, Vector.empty).contains(card) ||
      current.players.find(_.player == player).exists(
        _.advisers.contains(VisionState(card, Orientation.FaceDown)))
```

- [ ] **Step 5: Write `FavorBankChoice`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.model._

/** "Take favor from any one favor bank", for Vow of Obedience's REST and
  * Book Binders. The player chooses among the banks that hold favor; one
  * stocked bank is taken without asking, and none leaves nothing to do.
  */
private[cardplay] object FavorBankChoice:

  /** The banks that hold favor, in suit order. */
  def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  /** The operations that move up to `amount` favor from one stocked bank to
    * `player`'s play area, asking `player` with `decisionId` when several
    * banks are stocked. A bank holding less gives what it holds.
    */
  def take(ready: ReadyGame, player: PlayerId, amount: Int,
      decisionId: String, heading: String): Vector[Operation] =
    stocked(ready) match
      case Vector() => Vector.empty
      case Vector(only) => Vector(move(player, amount, _ => Right(only)))
      case several => Vector(
        Decide(decisionId, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some(heading))),
        move(player, amount, chosen(_, decisionId)))

  private def chosen(pending: PendingTree, decisionId: String)
      : Either[OathViolation, Suit] = pending.answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.FavorBank(suit)), _) => suit
  }.toRight(OathViolation.InvalidEventOrder(
    s"no favor bank is recorded for $decisionId"))

  private def move(player: PlayerId, amount: Int,
      bank: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((state, pending) => bank(pending).map { suit =>
      val taken = math.min(amount, state.banks.favor.getOrElse(suit, 0))
      if taken == 0 then Vector.empty
      else Vector(Move(Piece.Favor(taken),
        PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.PlayArea(player))))
    })
```

- [ ] **Step 6: Write `VowOfObedience`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Obedience (card 121): "You cannot play Visions faceup. REST: Take
  * [favor] from any one favor bank."
  *
  * The rule is a `Restriction` on the card-play hook. The walker's
  * restriction look-ahead then leaves a faceup copy's holder no faceup
  * placement for a Vision, the Conspiracy included, in Search and in
  * facedown-adviser play alike. The REST power is a [[PhasePower]], as Silver
  * Tongue's is. The catalog marks the power `persistent: false` because of
  * its REST, so the rule keeps the default automatic resolution rather than
  * reading one from the catalog.
  */
final case class VowOfObedience private (cardId: DenizenId)
    extends PhasePower with ContributingPower:
  def id: PowerId = VowOfObedience.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Restriction((ctx, _) =>
      VisionPlay.pending(ctx).filter(_ => holds(ctx.state, ctx.activePlayer))
        .map(_ => VisionPlay.forbidden("Vow of Obedience")))))

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    FavorBankChoice.stocked(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = VowOfObedience.choiceDecisionId(ready, player)
    Right(Branch((state, _) => FavorBankChoice.take(state, player, 1, choice,
      "Vow of Obedience: take a favor from a bank")))

  /** Whether `player` holds this card as a faceup adviser. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

object VowOfObedience:
  val id: PowerId = PowerId("denizen.vow-of-obedience")

  def forCatalog(catalog: ExecutableCatalog): Option[VowOfObedience] =
    CatalogCards.denizen(catalog, id).map(new VowOfObedience(_))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"vow-of-obedience-${ready.game.current.tracks.round}-${player.value}"
```

- [ ] **Step 7: Register it and delete the stub**

In `CardPlayTriggers.scala`, replace the scaladoc and the body:

```scala
/** The powers that act when a card is played, registered together: the
  * triggers that reward a play and the rules that forbid one. A power whose
  * card is absent from `catalog` is omitted.
  */
object CardPlayTriggers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    WildCry.forCatalog(catalog).toVector ++
      WelcomingParty.forCatalog(catalog).toVector ++
      Gossip.forCatalog(catalog).toVector ++
      VowOfObedience.forCatalog(catalog).toVector
```

In `PhasePowerCatalog.scala`, add `import oathdigital.gameplay.powers.cardplay.VowOfObedience` and change the first line of the vector to:

```scala
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      VowOfObedience.forCatalog(catalog).toVector ++
```

In `RestPowers.scala`, delete the `object VowOfObedience extends ReviewedPower(...)` declaration (two lines) and remove `VowOfObedience` from `powers`. `powers` becomes `Vector(Naysayers, SilverTongue, Insomnia, VowOfPoverty)`. Change the scaladoc's second sentence to: "League Treaty and Vow of Obedience are audited through their walker contributions instead."

- [ ] **Step 8: Pin the catalog flag**

In `PowerKindsCatalogSuite.scala`, add after the Dazzle test:

```scala
  test("Vow of Obedience's rule is automatic whatever its flag: the flag is " +
      "false because of its REST"):
    assertEquals(flag("denizen.vow-of-obedience"), Some(false))
    assertEquals(oathdigital.gameplay.powers.cardplay.VowOfObedience
      .forCatalog(catalog).get.resolution, PowerResolution.Automatic)
```

- [ ] **Step 9: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.RuleResolutionSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/FavorBankChoice.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/main/scala/oathdigital/gameplay/powers/RestPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/VisionPlayFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Vow of Obedience forbids faceup Visions and takes favor at Rest"
```

---

### Task 4: Secret Police and Sacred Ground

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/SacredGround.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/SacredGroundSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `SiteRulers` from Task 1, `VisionPlay` and `VisionPlayFixture` from Task 3, and `PowerAccess.pawnSite`.
- Produces: `SecretPolice.id`, `SecretPolice.forCatalog`, `SacredGround.id` and `SacredGround.forCatalog`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, PowerFixture,
  PowerImplementationStatus, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SecretPoliceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val police = DenizenId("113")
  private val other = TargetsFixture.others(base).head
  private val elsewhere: SiteId =
    base.game.current.map.inPlay.find(_ != home(base)).get

  private def exile(id: PlayerId): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(player(base, id).lineage), 2)
  private val bandits: SiteForces = SiteForces.Occupied(ForceKind.Bandit, 2)

  private def withForces(ready: ReadyGame, site: SiteId,
      forces: SiteForces): ReadyGame = ready.updateCurrent(c => c.copy(
    map = c.map.copy(sites = c.map.sites.updated(site,
      c.map.sites(site).copy(forces = forces)))))

  /** Secret Police faceup at `at`, ruled by `policeRuler`, and the actor's
    * pawn site ruled by `homeRuler`.
    */
  private def policed(ready: ReadyGame, at: SiteId, policeRuler: SiteForces,
      homeRuler: SiteForces): ReadyGame =
    withForces(withForces(atSite(CardStaging.without(ready, police), police, at),
      at, policeRuler), home(base), homeRuler)

  private def search(at: SiteId, policeRuler: SiteForces, homeRuler: SiteForces,
      vision: VisionId = VisionRules.Faith): OathTransition =
    searched(policed(SearchFixture.staged(Vector(vision)), at, policeRuler,
      homeRuler), vision)

  test("Secret Police is a registered, implemented rule"):
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == SecretPolice.id))
    assert(PowerImplementationStatus.implemented(catalog)(SecretPolice.id))

  test("an enemy at a site its ruler rules cannot play a Vision faceup"):
    val parkedAt = search(home(base), exile(other), exile(other))
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("any site the ruler rules binds, not only the Police site"):
    assert(!offered(search(elsewhere, exile(other), exile(other)))
      .contains("adviser-faceup"))

  test("the Police site's own ruler plays freely"):
    assert(offered(search(home(base), exile(actor), exile(actor)))
      .contains("adviser-faceup"))

  test("a pawn at a site with a different ruler is not bound"):
    assert(offered(search(elsewhere, exile(other), exile(actor)))
      .contains("adviser-faceup"))

  test("under Bandit rule every player at a Bandit site is bound"):
    assert(!offered(search(elsewhere, bandits, bandits))
      .contains("adviser-faceup"))

  test("the Conspiracy is forbidden too"):
    assert(!offered(search(home(base), exile(other), exile(other),
      VisionRules.Conspiracy)).contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = policed(inPhase(base, Phase.Act), home(base), exile(other),
      exile(other))
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))
```

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/SacredGroundSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, CatalogCards, PowerFixture,
  PowerImplementationStatus, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** E08 never appears in a generated game (the Nomad Homeland takes E06), so
  * each test places it by hand.
  */
class SacredGroundSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val e08: EdificeId = CatalogCards.edifice(catalog, SacredGround.id).get
  private val elsewhere: SiteId =
    base.game.current.map.inPlay.find(_ != home(base)).get

  private def withE08(ready: ReadyGame, site: SiteId,
      side: EdificeSide = EdificeSide.Intact): ReadyGame =
    CardStaging.without(ready, e08).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(site, c.map.sites(site).copy(denizens =
        c.map.sites(site).denizens :+ EdificeState(e08, side, Tokens.empty))))))

  private def search(site: SiteId, vision: VisionId = VisionRules.Faith,
      side: EdificeSide = EdificeSide.Intact): OathTransition =
    searched(withE08(SearchFixture.staged(Vector(vision)), site, side), vision)

  test("Sacred Ground is a registered, implemented rule"):
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == SacredGround.id))
    assert(PowerImplementationStatus.implemented(catalog)(SacredGround.id))

  test("a player whose pawn is elsewhere cannot play a Vision faceup"):
    val parkedAt = search(elsewhere)
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("a player whose pawn is at Sacred Ground plays freely"):
    assert(offered(search(home(base))).contains("adviser-faceup"))

  test("the site's ruler is bound like everyone else"):
    val ruled = withE08(SearchFixture.staged(Vector(VisionRules.Faith)),
      elsewhere).updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(elsewhere, c.map.sites(elsewhere).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(player(base).lineage), 2))))))
    assert(!offered(searched(ruled, VisionRules.Faith))
      .contains("adviser-faceup"))

  test("the Conspiracy is excepted"):
    assert(offered(search(elsewhere, VisionRules.Conspiracy))
      .contains("adviser-faceup"))

  test("the ruined face, Desecrated Ground, forbids nothing"):
    assert(offered(search(elsewhere, side = EdificeSide.Ruined))
      .contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = withE08(inPhase(base, Phase.Act), elsewhere)
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.SecretPoliceSuite oathdigital.gameplay.powers.cardplay.SacredGroundSuite"`
Expected: FAIL to compile, because `SecretPolice` and `SacredGround` are not defined.

- [ ] **Step 3: Write `SecretPolice`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Secret Police (card 113), a persistent rule of a site card: "Enemies cannot
  * play Visions faceup while their pawn is at any site ruled by Secret
  * Police's ruler."
  *
  * The Police's ruler is the ruler of the site it stands at. A player ruler
  * binds every other player whose pawn is at a site that player rules. A
  * Bandit ruler binds every player at any Bandit-ruled site, the Police site
  * included, since bandits are every player's enemy. An Empire ruler binds no
  * one until the Empire phase decides otherwise. The rule binds players who
  * cannot use the card, so the card is found on the map, as Toll Roads is,
  * and not through `PowerAccess`.
  */
final case class SecretPolice private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = SecretPolice.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] = for
    _ <- VisionPlay.pending(ctx)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(ctx.activePlayer))
    pawn <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
    if SiteRulers.rulerOf(ctx.state, pawn).contains(ruler)
  yield VisionPlay.forbidden("Secret Police")

object SecretPolice:
  val id: PowerId = PowerId("denizen.secret-police")

  def forCatalog(catalog: ExecutableCatalog): Option[SecretPolice] =
    CatalogCards.denizen(catalog, id).map(new SecretPolice(_))
```

- [ ] **Step 4: Write `SacredGround`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/SacredGround.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powers.setup.EdificeSetupSupport
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Sacred Ground (E08's intact face), a persistent rule: "Players cannot play
  * Visions, except the Conspiracy, faceup unless their pawn is at this site."
  *
  * It binds every player whose pawn is elsewhere, the site's own ruler
  * included. Its ruined face, Desecrated Ground, is not implemented; E08
  * never appears in a generated game.
  */
final case class SacredGround private (edifice: EdificeId)
    extends ContributingPower:
  def id: PowerId = SacredGround.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] = for
    vision <- VisionPlay.pending(ctx)
    if vision != VisionRules.Conspiracy
    site <- EdificeSetupSupport.siteOf(ctx.state, edifice, EdificeSide.Intact)
    if ctx.state.game.current.map.inPlay.contains(site)
    if !PowerAccess.pawnSite(ctx.state, ctx.activePlayer).contains(site)
  yield VisionPlay.forbidden("Sacred Ground")

object SacredGround:
  val id: PowerId = PowerId("edifice.e08.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[SacredGround] =
    CatalogCards.edifice(catalog, id).map(new SacredGround(_))
```

- [ ] **Step 5: Register them**

In `CardPlayTriggers.forCatalog`, replace the last line, `VowOfObedience.forCatalog(catalog).toVector`, with:

```scala
      VowOfObedience.forCatalog(catalog).toVector ++
      SecretPolice.forCatalog(catalog).toVector ++
      SacredGround.forCatalog(catalog).toVector
```

In `PowerKindsCatalogSuite`, add `"denizen.secret-police"` and `"edifice.e08.intact"` to `persistentRules`.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/SacredGround.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/SacredGroundSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Secret Police and Sacred Ground forbid faceup Visions"
```

---

### Task 5: Book Binders

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/BookBinders.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/BookBindersSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`

**Interfaces:**
- Consumes: `VisionPlay.played` and `FavorBankChoice.take` from Task 3.
- Produces: `BookBinders.id`, `BookBinders.Favor` (which is 2), `BookBinders.forCatalog`, and `BookBinders.decisionId(ready: ReadyGame, holder: PlayerId, vision: VisionId): String`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/BookBindersSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.{PowerCtx, Transform}
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture,
  TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class BookBindersSuite extends munit.FunSuite:
  import PowerFixture._

  private val binders = DenizenId("140")
  private val holder = TargetsFixture.others(base).head
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** A Search drawing Faith, `owner` holding Book Binders, and exactly these
    * banks holding favor.
    */
  private def arranged(banks: Map[Suit, Int], owner: PlayerId = holder,
      orientation: Orientation = Orientation.FaceUp,
      vision: VisionId = VisionRules.Faith): ReadyGame =
    val ready = TargetsFixture.giveAdviser(CardStaging.without(
      SearchFixture.staged(Vector(vision)), binders), owner, binders,
      orientation)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(suit => suit -> banks.getOrElse(suit, 0)).toMap))

  private def faceup(ready: ReadyGame): OathTransition =
    SearchFixture.play(ready, Vector.empty, VisionRules.Faith, "adviser-faceup")

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor

  test("Book Binders is a registered persistent rule, so it is automatic"):
    val power = BookBinders.forCatalog(catalog).get
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == BookBinders.id))

  test("another player's faceup Vision gives the holder a choice of bank, " +
      "then two favor"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val placed = faceup(ready)
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    parked.assertParked(placed.state, ActionRef.Search, choice, holder)
    val taken = SearchFixture.rules.resolveWalker(placed.state, holder, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .toOption.get
    val after = SearchFixture.after(taken)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Order), 1)
    assertEquals(after.banks.favor(Suit.Arcane), 3)

  test("a single stocked bank is taken without asking"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Hearth), 3)

  test("a bank holding one favor gives one"):
    val ready = arranged(Map(Suit.Hearth -> 1))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)

  test("with every bank empty nothing happens"):
    val ready = arranged(Map.empty)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("the holder's own faceup Vision gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5), owner = actor)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("a facedown Book Binders is not active"):
    val ready = arranged(Map(Suit.Hearth -> 5),
      orientation = Orientation.FaceDown)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("a facedown play gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(SearchFixture.play(ready, Vector.empty,
      VisionRules.Faith, "adviser-facedown"))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("the Conspiracy triggers it"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 3),
      vision = VisionRules.Conspiracy)
    val power = BookBinders.forCatalog(catalog).get
    val hook = CardPlayedFaceup(VisionRules.Conspiracy,
      RuleSourceRef.Adviser(actor, VisionRules.Conspiracy))
    val ctx = PowerCtx(ready, actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, hook)
    val Transform(fn) =
      power.contributions(PowerWindow.ActionCardPlayedFaceup).head: @unchecked
    val folded = fn(ctx, Vector(hook))
    assertEquals(folded.head, hook)
    assertEquals(folded.collect { case decide: Decide => decide.owner },
      Vector(holder))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.BookBindersSuite"`
Expected: FAIL to compile, because `BookBinders` is not defined.

- [ ] **Step 3: Write `BookBinders`**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/BookBinders.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Book Binders (card 140), a persistent rule of a faceup adviser: "After
  * another player plays a Vision faceup, you gain [favor] [favor] from any one
  * favor bank."
  *
  * A `Transform` on the card-play hook, as Gossip's is. The card says
  * "another player", not "an enemy", so any other player's faceup Vision
  * triggers it, the Conspiracy included, from Search or from the advisers.
  * The holder chooses the bank off turn, as League Treaty's ruler does. One
  * stocked bank is taken without asking, and a bank holding one favor gives
  * one.
  */
final case class BookBinders private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = BookBinders.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      reward(ctx).fold(children)(children ++ _))))

  override def applicable(ctx: PowerCtx): Boolean = reward(ctx).nonEmpty

  /** The holder's take, when another player plays a Vision faceup. */
  private def reward(ctx: PowerCtx): Option[Vector[Operation]] = for
    vision <- VisionPlay.played(ctx)
    holder <- holderOf(ctx.state).filter(_ != ctx.activePlayer)
  yield FavorBankChoice.take(ctx.state, holder, BookBinders.Favor,
    BookBinders.decisionId(ctx.state, holder, vision),
    "Book Binders: take two favor from a bank")

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object BookBinders:
  val id: PowerId = PowerId("denizen.book-binders")
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BookBinders] =
    CatalogCards.denizen(catalog, id).map(new BookBinders(_, catalog))

  /** A Vision is played faceup at most once a round. */
  def decisionId(ready: ReadyGame, holder: PlayerId, vision: VisionId): String =
    s"book-binders-${ready.game.current.tracks.round}-${holder.value}-" +
      vision.value
```

- [ ] **Step 4: Register it and pin its flag and status**

1. In `CardPlayTriggers.forCatalog`, insert `BookBinders.forCatalog(catalog).toVector ++` after the Gossip line.
2. In `PowerKindsCatalogSuite`, add `"denizen.book-binders"` to `persistentRules`.
3. In `PowerImplementationStatusSuite`, add:

```scala
  test("the Vision-play cards are implemented"):
    Vector("denizen.vow-of-obedience", "denizen.secret-police",
      "denizen.book-binders", "edifice.e08.intact").foreach(id =>
      assert(implemented(PowerId(id)), id))
```

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/BookBinders.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/BookBindersSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala
git commit -m "feat(powers): Book Binders rewards another player's faceup Vision"
```

---

### Task 6: Vow of Peace's second sentence

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceContribution.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceSuite.scala`

**Interfaces:**
- Consumes: `CampaignSetup.setup` and the `CampaignSacrificeSelection` window on the sacrifice `Decide`.
- Produces: no new names. The existing `CampaignPowersSuite` tests for the first sentence must still pass.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Vow of Peace's second sentence: attackers cannot sacrifice warbands to
  * increase their attack against a faceup holder.
  */
class VowOfPeaceSuite extends munit.FunSuite:
  private val vow = cardWith("denizen.vow-of-peace")
  private val wrestlers = cardWith("denizen.wrestlers")

  private def defendedBy(b: Board, orientation: Orientation): Board =
    withAdviserFor(b, b.other, vow, orientation)

  /** Every decision the Campaign parks on until it ends, answering the plan
    * windows with Finish and every amount with zero.
    */
  private def asked(run: Run): Vector[String] =
    parked.parkedDecision(run.state) match
      case Some(facts) if facts.decision == CampaignIds.attackerPlan ||
          facts.decision == CampaignIds.defenderPlan =>
        facts.decision +: asked(run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseOneAnswer(CampaignIds.finish)))
      case Some(facts) if facts.decision == CampaignIds.sacrifice ||
          facts.decision == CampaignIds.placement =>
        facts.decision +: asked(run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseAmountAnswer(0)))
      case Some(facts) => Vector(facts.decision)
      case None => Vector.empty

  test("an attacker against a faceup holder is not asked to sacrifice, and " +
      "the battle still resolves"):
    val run = commit(rules(losing), defendedBy(againstPlayer(board()),
      Orientation.FaceUp), 2)
    assert(!asked(run).contains(CampaignIds.sacrifice))
    assert(ready(run.finish.state).game.current.lastCampaignResult.nonEmpty)

  test("a Raid against a faceup holder is protected too"):
    val run = commit(rules(losing), defendedBy(withEnemyAtOrigin(board()),
      Orientation.FaceUp), 2, raid = true)
    assert(!asked(run).contains(CampaignIds.sacrifice))

  test("an attacker against anyone else is still asked"):
    assert(asked(commit(rules(losing), againstPlayer(board()), 2))
      .contains(CampaignIds.sacrifice))

  test("a facedown Vow of Peace protects no one"):
    assert(asked(commit(rules(losing), defendedBy(againstPlayer(board()),
      Orientation.FaceDown), 2)).contains(CampaignIds.sacrifice))

  test("a holder who is not the defender is not protected"):
    assert(asked(commit(rules(losing), defendedBy(board(), Orientation.FaceUp),
      2)).contains(CampaignIds.sacrifice))

  test("the defender's own sacrifice is unaffected"):
    val defended = defendedBy(againstPlayer(board()), Orientation.FaceUp)
    val b = withAdviserFor(defended, defended.other, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(wrestlers)))
    assert(picked.since(run).exists(_.isInstanceOf[Sacrifice]))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfPeaceSuite"`
Expected: FAIL. The two protection tests find `campaign.sacrifice` among the decisions asked. The others pass.

- [ ] **Step 3: Add the transform**

Replace the body of `VowOfPeaceContribution.scala` above `object VowOfPeaceContribution` with the following, and keep the object as it is:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Peace: "You cannot campaign. Attackers cannot sacrifice warbands to
  * increase their attack against you." A faceup copy held as an adviser does
  * both. A facedown copy is not active, and the card is adviser-only, so no
  * other location is considered.
  *
  * The first sentence is a `Restriction` at the Campaign's root, the same
  * shape as Narrow Pass blocking a Travel. The second is a `Transform` at
  * `CampaignSacrificeSelection` that removes the attacker's sacrifice decision
  * when the defender holds the Vow, so the battle reads a sacrifice of zero.
  * In a Conquest the defender is the ruler of the targets. The defender's own
  * sacrifices, such as Wrestlers', are not the attacker's and stay.
  */
final case class VowOfPeaceContribution private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = VowOfPeaceContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))),
    PowerWindow.CampaignSacrificeSelection ->
      Vector(Transform((ctx, children) =>
        if defenderHolds(ctx) then Vector.empty else children)))

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case card: DenizenState =>
        card.id == cardId && card.orientation == Orientation.FaceUp
      case _ => false
    })

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    Option.when(holds(ctx.state, ctx.activePlayer))(
      OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning"))

  /** Whether this Campaign's defender is a player holding the Vow faceup. */
  private def defenderHolds(ctx: PowerCtx): Boolean =
    CampaignSetup.setup(ctx.state, ctx.activePlayer,
      PendingTree(ctx.nodePath, ctx.answered)).exists(_.defender match {
        case CampaignDefender.Player(player) => holds(ctx.state, player)
        case CampaignDefender.Bandits => false
      })
```

- [ ] **Step 4: Run the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.CampaignPowersSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceContribution.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceSuite.scala
git commit -m "feat(powers): attackers cannot sacrifice against a Vow of Peace holder"
```

---

### Task 7: Docs, gates and the smoke check

**Files:**
- Modify: `docs/architecture/bounded-visions-and-conspiracy.md:66-75`
- Modify: `docs/architecture/bounded-campaign.md:124-127`
- Modify: `docs/rules/implementation-traceability.md:111`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-09-26-rule-gaps-in-implemented-cards-design.md` (status line)

- [ ] **Step 1: Rewrite the stale Vision-legality paragraph**

In `docs/architecture/bounded-visions-and-conspiracy.md`, replace the paragraph that begins "The faceup-Vision legality boundary fingerprints" and ends "are the sole faceup paths." with:

```markdown
Three cards forbid a faceup Vision play and one rewards it. Each is a power on
the card-play hook `ActionCardPlayedFaceup`:

- **Vow of Obedience** (`denizen.vow-of-obedience`): its faceup holder cannot
  play Visions faceup, the Conspiracy included. Its REST takes one favor from
  any stocked bank.
- **Secret Police** (`denizen.secret-police`): enemies of its site's ruler
  cannot while their pawn is at a site that ruler rules. Under Bandit rule
  that is every player at a Bandit site. An Empire ruler binds no one yet.
- **Sacred Ground** (`edifice.e08.intact`): no player can, except with the
  Conspiracy, unless their pawn is at its site.
- **Book Binders** (`denizen.book-binders`): after another player plays a
  Vision faceup, its holder takes two favor from a bank of their choice.

The first three are `Restriction`s. They bind only while the Vision is still
in the player's temporary hand or among their facedown advisers. The walker's
restriction look-ahead removes the faceup placement from Search and from
facedown-adviser play, which are the only faceup paths, and the answer-time
check refuses a submitted one. Desecrated Ground (`edifice.e08.ruined`) is
not implemented; E08 never appears in a generated game.
```

- [ ] **Step 2: Update the Campaign doc**

In `docs/architecture/bounded-campaign.md`, replace the sentence "The second printed sentence (attackers cannot sacrifice against a holder) is not modelled." with:

```markdown
Its second sentence is a `Transform` at `CampaignSacrificeSelection` that
removes the attacker's sacrifice decision when the defender (the targets'
ruler in a Conquest) holds a faceup copy.
```

- [ ] **Step 3: Update the traceability row**

In `docs/rules/implementation-traceability.md`, find the row that begins "| Vision reveal/qualification/victory and Conspiracy |" (line 111).

- At the end of its evidence column, after "...shared with direct adviser play and Search placement.", add: "Vow of Obedience, Secret Police and Sacred Ground forbid a faceup Vision through the restriction look-ahead, and Book Binders rewards one (`gameplay/powers/cardplay/`, one suite each)."
- In its last column, replace "and executable Vision/reveal modifiers remain deferred" with "Desecrated Ground and other Vision/reveal modifiers remain deferred".

- [ ] **Step 4: Update the roadmap**

In `docs/ROADMAP.md`:

1. Delete the whole `### Phase - Rule gaps in implemented cards` section, from its heading to the line before `### Phase - Power log lines`.
2. Replace the "Now" paragraph with:

```markdown
**Phase - Power log lines** is next, then **Phase - Catalog batch 2**.
```

- [ ] **Step 5: Mark the spec implemented**

In the spec, replace the status line and its continuation with:

```markdown
**Status:** implemented (plan
`docs/superpowers/plans/2026-09-26-rule-gaps-in-implemented-cards.md`)
```

- [ ] **Step 6: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS for both. The server count is Task 1's plus this plan's new tests, and the frontend count is unchanged.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both report no problems.

- [ ] **Step 7: Smoke-check in the browser on a scratch database**

1. Copy `var/oathdigital` to a scratch directory. Never open the live one.
2. Serve this worktree's build against the copy on port 8093:

```bash
./sbtw "runMain oathdigital.server.OathServer --database-path <scratch>/oathdigital --catalog-path docs/catalog/new-foundations-component-catalog.json --port 8093"
```

   If the server takes no `--port` flag, use whatever flag Slice 1's verification used, as `docs/superpowers/plans/2026-09-26-game-log-slice1.md` records it.

3. Open a seat whose player can act. Start a Search, and confirm that the placement decision renders its buttons and that a placement completes.
4. Stop the server.

A live game cannot be arranged to deal Vow of Obedience with a Vision, so this is a smoke check; the suites prove the hidden option. Report what you saw.

- [ ] **Step 8: Commit**

```bash
git add docs/architecture/bounded-visions-and-conspiracy.md \
  docs/architecture/bounded-campaign.md \
  docs/rules/implementation-traceability.md docs/ROADMAP.md \
  docs/superpowers/specs/2026-09-26-rule-gaps-in-implemented-cards-design.md
git commit -m "docs: record the Vision-play cards, Vow of Peace and the look-ahead"
```
