# Consent System, Slice 2 (The Pass on Campaign Targeting) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A campaigner whose extra target is forbidden only by a player-ruled Narrow Pass can ask that ruler for consent. A grant opens the target, and the campaigner goes on choosing targets.

**Architecture:**

- **Powers.** `OptionRestriction` gains `waiver: Option[Waiver]`, as `Restriction` did in slice 1. The Pass declares a waiver on its campaign-targeting `OptionRestriction`, with its own consent note. `ConsentPowers` registers a power waivable through either kind.
- **Walker.** The option path in `WalkerPowerGather` (`restrictOptions`, and `emptiedDecision` beside it) gets the same two consent checks the tree path has:
  - A ledger check. A waivable restriction no longer forbids an option once a grant for its power, the decision's owner, the decision and that option is remembered this turn.
  - An askability check. An option that only waivable restrictions forbid, with other players as granters and nothing refused this turn, becomes `OptionReason.Askable` at a choose-many.

  The tree path and the option path share one `consentAsk` helper.
- **Settling.** `ConsentDesk` answers the parked decision for the requester only when that decision is a choose-one (`OathRules.grantAnswers`). At a choose-many, a grant records the answer and clears the request. The decision then re-renders with the target choosable.
- **Frontend.** No production change is expected. The board path already asks from an askable option at a choose-many and never picks it. Task 3 pins that with a test.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-08-consent-system-design.md`. Read the whole spec before starting, and skim the slice 1 plan's decisions (`docs/superpowers/plans/2026-10-09-consent-slice1.md`, "Plan decisions beyond the spec"). This plan covers the spec's "Slices" item 2.

## Plan decisions beyond the spec

The code forced these, or the spec left them open. Task 3 records each one in the spec.

1. **A grant at a choose-many opens the option and answers nothing.** In slice 1, a grant answers the parked choose-one with the target. Campaign targets are a choose-many (`DecisionQuery.ChooseMany(0, n, ...)`), and the target is one pick among several. Answering with it alone would end the selection. So the grant records `Granted` and clears the request. No `CommandAccepted` is appended. The requester's next projection offers the target, and they confirm their targets as usual. `OathRules.grantAnswers(state, requester, decisionId)` tells `ConsentDesk` which case applies.
2. **On the option path, askable only at a choose-many.** A grant at a choose-one answers the decision. The option path runs no search with the grant remembered (slice 1's `grantable`), so a choose-one there could get a grant that does not answer, leaving the request stuck. No slice-2 option takes that path. A later slice that needs it adds the search.
3. **No search with the grant on the option path.** A grant at a choose-many answers nothing, so no request can be left stuck. If the search then hides the opened option, the option shows what the search says. A campaign's search stops at the force decision after the targets, so a granted target stays open.
4. **The option path ties a grant to its decision.** Its ledger key names the decision's id, the decision's owner and the option being judged. A Travel grant never opens a campaign target, and a campaign grant never opens a Travel destination. The tree path keeps slice 1's per-walk match (slice 1 decision 18). The Pass's Travel destination is still the only decision on the tree path, so it stays exact. The key still does not name the granter.
5. **One granter and source for both of the Pass's clauses, with a note each.** The note keys are `no-travel-consent` and `no-target-consent`. `ConsentPowers` registers a power by its first waiver, of either kind. The grid, the stale-preference check and the log read only the granter and the source.
6. **`consentAsks` reads choose-many options too.** It used `PricedOptions.options`, which returns only a choose-one's options.
7. **Picks made before asking are dropped.** The client keeps a board draft only while the sequence stays the same (`BoardSelectionDraft.reconcile`), and a request appends events. After a grant, the campaigner picks their other targets again. This is accepted for this slice.
8. **Known limit for later slices.** A choose-many drawn in the panel (`WalkerSelectionPanels`) does not handle askable options. Campaign targets are always drawn on the board, because every site has a board home. No slice-2 option takes that path.
9. **An optional choose-many with only askable options is still asked.** Found in review after Task 2: a Conquest whose only extra target the Pass forbids lost its targets decision, because `WalkerSearch.narrowed` drops an optional choose-many with nothing left to pick. It now keeps one while any option is askable (`DecisionOption.askable`), and `DecisionQueries.wellFormed` accepts its `0..0` range then. Tests: `NarrowPassCampaignConsentSuite`, `CampaignConsentServiceSuite` and `DecisionQuerySuite`.
10. **No browser acceptance run in this slice.** The reason is the same as slice 1 decision 9: a game with a player-ruled Pass cannot be seeded yet. Task 3 extends the roadmap's consent play-test item to cover the campaign target.

## Global Constraints

- **Build rules.** Every project builds with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import, private member or local fails the build. Delete whatever the compiler names.
- **File size.** Every Scala file, production or test, stays at or under 800 lines (`scripts/check-architecture.py`, `BackendArchitectureSuite`). `OathRulesWalker.scala` is at 603 lines and `WalkerPowerGather.scala` at 414. Keep each addition to what the task shows.
- **Architecture guard.** `scripts/check-architecture.py` reads a `Map(` call as a lookup table. In a power file, build maps the way the surrounding code does.
- **Ids and wording, exactly:**
  - The Pass's campaign consent note key is `no-target-consent`. Its wording is `Cannot target other sites in the region without ruler's consent.` The reason shown on the option is `Narrow Pass: Cannot target other sites in the region without ruler's consent.`
  - The ordinary reason stays `no-target`, `Cannot target other sites in the region.`
  - Every other id, command, event, log line and UI string is slice 1's. The request line for a campaign target reads `Asked p2 for consent to ignore Narrow Pass and select Deep Woods.`
- **Rulings** (spec, "Rulings"). These apply: 2 (no consent inside a Campaign except the Pass's own clause), 3, 4, 5, 6, 8, 10, 12, 13, 14 and 15–19. There is still only one waivable power, so ruling 9's several granters cannot arise.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree on branch `feat/consent-slice2`.
  - `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.gameplay.powers.travel.NarrowPassCampaignConsentSuite"` (any fully qualified suite name).
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentAskingSuite"`.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 3):
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `./sbtw coverage test coverageReport`
    4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    5. `git diff --check`

## File map

Created:

- `src/test/scala/oathdigital/gameplay/PassMap.scala`: the Pass's three-region map, moved out of `CampaignPowersSuite`.
- `src/test/scala/oathdigital/gameplay/CampaignConsentFixture.scala`: a Campaign the Pass bears on.
- `src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala`
- `src/test/scala/oathdigital/application/CampaignConsentServiceSuite.scala`

Modified:

- `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`: `OptionRestriction.waiver`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`: the option path's ledger and askability checks, and the shared `consentAsk`.
- `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`: the Pass's campaign waiver and `noTargetConsent`.
- `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`: registers option-restriction waivers.
- `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`: `consentAsks` reads choose-many options; `grantAnswers`.
- `src/main/scala/oathdigital/application/ConsentDesk.scala`: a grant answers only a choose-one.
- Tests: `CampaignPowersSuite`, `ConsentPowersSuite`, frontend `ConsentAskingSuite`.
- Docs: the spec, `CONTEXT.md`, `docs/ROADMAP.md` and `docs/rules/implementation-traceability.md`.

---

### Task 1: The Pass's campaign clause is askable

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/PassMap.scala`
- Create: `src/test/scala/oathdigital/gameplay/CampaignConsentFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala:43-67`
- Modify: `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala:85-97`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala:74-130` and `:307-340`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala:145-248`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala:12-16`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:302-320`

**Interfaces:**
- Consumes (slice 1): `Waiver(granter: ReadyGame => Option[PlayerId], source: PowerSourceRef, note: (PowerCtx, DecisionOptionRef) => PowerNote)`; `ConsentState.remembered(key: ConsentKey, now: ConsentTurn): Option[ConsentAnswer]`; `ConsentTurn.of(ready)`; `OptionReason.Askable(power: PowerId, note: PowerNote, asks: Vector[ConsentAsk])`; `OathRules.consentAsks(state, requester, decisionId, target): Either[OathViolation, Vector[ConsentAsk]]`.
- Produces:
  - `OptionRestriction(fn, note = Contribution.silent, waiver: Option[Waiver] = None)`.
  - `NarrowPassSitePower.noTargetConsent: NoteKey`.
  - Test fixtures `PassMap` and `CampaignConsentFixture`, with the members Task 2 uses: `passRuledByP2: ReadyGame`, `deepWoodsRef: DecisionOptionRef.Site`, `targetOptions(state: OathState, extra = Vector.empty): Vector[DecisionOption]` and `parkedAt(state: OathState): Option[String]`.

- [ ] **Step 1: Move the Pass's map out of `CampaignPowersSuite`**

Create `src/test/scala/oathdigital/gameplay/PassMap.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** The Narrow Pass's map. Cradle: Ancient City, Broken Peaks. Provinces:
  * the Pass, Buried Giant, Deep Woods. Hinterland: Desolate Shore, Dunes,
  * Fair Isle. Each site holds its printed bandits. No Table step lays out
  * regions, so the map is built here.
  */
object PassMap:
  val pass: SiteId = CatalogNames.site("Narrow Pass")
  val ancientCity: SiteId = CatalogNames.site("Ancient City")
  val brokenPeaks: SiteId = CatalogNames.site("Broken Peaks")
  val buriedGiant: SiteId = CatalogNames.site("Buried Giant")
  val deepWoods: SiteId = CatalogNames.site("Deep Woods")
  val desolateShore: SiteId = CatalogNames.site("Desolate Shore")
  val dunes: SiteId = CatalogNames.site("Dunes")
  val fairIsle: SiteId = CatalogNames.site("Fair Isle")

  val map: MapState =
    val cradle = Vector(ancientCity, brokenPeaks)
    val provinces = Vector(pass, buriedGiant, deepWoods)
    val hinterland = Vector(desolateShore, dunes, fairIsle)
    val states = (cradle ++ provinces ++ hinterland).map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    MapState(cradle, provinces, hinterland, states)

  /** `ready` on `map`, with `player`'s pawn at `site`. */
  def withPawnAt(ready: ReadyGame, player: PlayerId, site: SiteId,
      on: MapState = map): ReadyGame =
    Table.from(ready.updateCurrent(_.copy(map = on))).pawn(player, at = site)
      .ready
```

In `CampaignPowersSuite.scala`, replace the block from `private val pass = CatalogNames.site("Narrow Pass")` through the end of `withPawnAt` (lines 44–67) with:

```scala
  import PassMap.{ancientCity, brokenPeaks, buriedGiant, deepWoods,
    desolateShore, pass}
  private def withPawnAt(site: SiteId, map: MapState = PassMap.map)
      : ReadyGame =
    PassMap.withPawnAt(initial, actor, site, map)
```

In the test "a Pass that is not in play forbids nothing", replace both uses of `passMap` with `PassMap.map`. Delete any import the compiler then names as unused (`CatalogNames` stays if the Vow of Peace tests use it; `dunes` and `fairIsle` were never used by the tests).

- [ ] **Step 2: Run the moved tests**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite"`
Expected: PASS, the same 9 tests as before.

- [ ] **Step 3: Write the Campaign fixture**

Create `src/test/scala/oathdigital/gameplay/CampaignConsentFixture.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A Campaign the Narrow Pass bears on (consent design, slice 2), on
  * `PassMap`. In p1's Act, p1 stands at Ancient City, in the Cradle, which
  * its bandits rule, with 3 warbands and 4 Supply. p2 stands at Fair Isle
  * and p3 at Dunes, so a Campaign by p1 is a Conquest of the bandits. The
  * Pass, in the Provinces, forbids Buried Giant and Deep Woods as its extra
  * targets.
  */
object CampaignConsentFixture:
  val passHeldByBandits: ReadyGame =
    Table.from(PassMap.withPawnAt(Table.start.ready, p1, PassMap.ancientCity))
      .pawn(p2, at = PassMap.fairIsle).pawn(p3, at = PassMap.dunes)
      .boardWarbands(p1, 3).supply(p1, 4).ready

  /** The same board, with p2 ruling the Pass with one warband. */
  val passRuledByP2: ReadyGame =
    Table.from(passHeldByBandits).warbandsAt(PassMap.pass, p2, 1).ready

  val deepWoodsRef: DecisionOptionRef.Site =
    DecisionOptionRef.Site(PassMap.deepWoods)

  /** The catalog's walker powers and `extra`. */
  def rules(extra: Vector[ContributingPower] = Vector.empty): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers(extra))

  /** p1 starts a Campaign on `ready`; the walk parks on the targets. */
  def started(ready: ReadyGame = passRuledByP2,
      extra: Vector[ContributingPower] = Vector.empty)(
      using munit.Location): OathState =
    accepted(rules(extra).startWalker(Ready(ready), ActionRef.Campaign, p1,
      Vector.empty)).state

  /** Every option of the targets decision `state` is parked on, as offered. */
  def targetOptions(state: OathState,
      extra: Vector[ContributingPower] = Vector.empty)(
      using munit.Location): Vector[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowers.selected(powers(extra),
        Vector.empty)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case many: DecisionQuery.ChooseMany => many.options
        case other => munit.Assertions.fail(
          s"expected a choose-many, got $other")
      case other => munit.Assertions.fail(
        s"expected a parked decision, got $other")

  /** The id of the decision `state` is parked on; `None` when nothing is. */
  def parkedAt(state: OathState)(using munit.Location): Option[String] =
    ParkedNode.of(state, catalog, WalkerPowers.selected(powers(Vector.empty),
        Vector.empty)) match
      case Right(node) => node.map(_.decisionId)
      case Left(reason) => munit.Assertions.fail(reason)

  private def powers(extra: Vector[ContributingPower]): WalkerPowers =
    WalkerPowers(WalkerPowerCatalog.default(catalog).powers ++ extra)
```

If a `Table` step's name or parameter order differs (`boardWarbands`, `supply`, `warbandsAt`), use the one `CampaignFixture.board` and `TravelFixture` use. If `.ready` rejects the board, use `.unchecked` as `CampaignFixture.on` does.

- [ ] **Step 4: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.{CampaignConsentFixture, PassMap}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Which campaign targets the Narrow Pass makes askable (consent design,
  * ruling 2 and slice 2). The Pass's own campaign clause grants consent, so
  * an extra target only the Pass forbids may be asked of its ruler. */
class NarrowPassCampaignConsentSuite extends munit.FunSuite:
  import CampaignConsentFixture._
  private val passPower = NarrowPassSite.pass

  /** `ready` remembering `answer` for Deep Woods at `decisionId`, this
    * turn. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer,
      decisionId: String = CampaignIds.targets): ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(ConsentKey(passPower, p1, decisionId, deepWoodsRef) ->
        answer),
      turn = Some(ConsentTurn.of(ready))))

  /** A target restriction nobody can waive, on Deep Woods too. */
  private object WoodsBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.woods-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(PassMap.deepWoods)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(
        (_, ref) => Option.when(ref == deepWoodsRef)(
          OathViolation.CampaignUnavailable("the barrier")),
        (_, _) => Some(NarrowPassSitePower.noTarget(
          PowerSourceRef.Site(PassMap.deepWoods))))))

  private def woods(options: Vector[DecisionOption])(
      using munit.Location): DecisionOption =
    present(options.find(_.ref == deepWoodsRef), "Deep Woods among the targets")

  private def assertNotedBy(option: DecisionOption, noteKey: String)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, noteKey)
    case other => fail(s"expected an unavailable option noted $noteKey, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  private val woodsOnly =
    DecisionAnswer.ChooseManyAnswer(Vector(deepWoodsRef))

  test("a target only a player-ruled Pass forbids is askable of its ruler"):
    woods(targetOptions(started())) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks)) =>
        assertEquals(power, passPower)
        assertEquals(note, NarrowPassSitePower.noTargetConsent(
          PowerSourceRef.Site(NarrowPassSite.id)))
        assertEquals(asks, Vector(ConsentAsk(p2, Vector(passPower))))
      case other => fail(s"expected an askable target, got $other")

  test("a Pass the bandits hold forbids with its ordinary reason: bandits " +
      "never consent"):
    assertNotedBy(woods(targetOptions(started(passHeldByBandits))),
      "no-target")

  test("a Pass the campaigner rules forbids nothing"):
    val ruled = Table.from(passRuledByP2).warbandsAt(PassMap.pass, p1, 1)
      .ready
    assert(woods(targetOptions(started(ruled))).available)

  test("a block nobody can waive beside the Pass leaves the target " +
      "unavailable, not askable"):
    val extra = Vector(WoodsBarrier)
    woods(targetOptions(started(extra = extra), extra)) match
      case DecisionOption.Unavailable(_, _: OptionReason.Noted) => ()
      case other => fail(s"expected a plain unavailable target, got $other")

  test("a grant remembered for the targets opens Deep Woods, and the " +
      "Campaign takes it"):
    val state = started(remembering(passRuledByP2, ConsentAnswer.Granted))
    assert(woods(targetOptions(state)).available)
    val answered = accepted(rules().resolveWalker(state, p1,
      CampaignIds.targets, woodsOnly))
    assertEquals(parkedAt(answered.state), Some(CampaignIds.force))

  test("a grant for another decision opens nothing here: a grant is tied " +
      "to its decision"):
    val travelGrant = remembering(passRuledByP2, ConsentAnswer.Granted,
      TravelProcedure.destinationDecisionId)
    assert(askable(woods(targetOptions(started(travelGrant)))))

  test("a refusal remembered this turn leaves the ordinary reason, and " +
      "the target is refused"):
    val state = started(remembering(passRuledByP2, ConsentAnswer.Refused))
    assertNotedBy(woods(targetOptions(state)), "no-target")
    assert(rules().resolveWalker(state, p1, CampaignIds.targets, woodsOnly)
      .isLeft)

  test("a grant from another turn counts for nothing"):
    val stale = passRuledByP2.copy(consent = ConsentState(
      answers = Map(ConsentKey(passPower, p1, CampaignIds.targets,
        deepWoodsRef) -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn(passRuledByP2.game.current.tracks.round, p2))))
    assert(askable(woods(targetOptions(started(stale)))))

  test("the asks of an askable target"):
    assertEquals(rules().consentAsks(started(), p1, CampaignIds.targets,
      deepWoodsRef), Right(Vector(ConsentAsk(p2, Vector(passPower)))))

  test("at a choose-one the Pass's target clause is not askable: a grant " +
      "there would answer the decision"):
    val passOnly = WalkerPowers(Vector(present(TravelSitePowers.all
      .collectFirst { case power: NarrowPassSitePower => power },
      "the Pass power")))
    val tree = Sequence(Vector[Operation](Decide(CampaignIds.targets, p1,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Site(deepWoodsRef),
        DecisionOption.Site(DecisionOptionRef.Site(PassMap.brokenPeaks)))),
      window = Some(PowerWindow.CampaignTargetSelection))))
    val pending = Outcomes.parked(ProcedureWalker.advance(passRuledByP2, tree,
      None, passOnly)).tree
    present(ProcedureWalker.parkedDecide(passRuledByP2, tree, pending,
        passOnly), "the choose-one").query match
      case one: DecisionQuery.ChooseOne =>
        assertNotedBy(woods(one.options), "no-target")
      case other => fail(s"expected a choose-one, got $other")
```

Add to `ConsentPowersSuite.scala`, with the imports it needs (`ContributingPower`, `Contribution`, `OptionRestriction` and `Waiver` from `oathdigital.gameplay.powerresolver`; `NarrowPassSitePower` from `oathdigital.gameplay.powers.travel`):

```scala
  /** A power waivable only on its options. */
  private object TargetBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.target-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(NarrowPassSite.id)
    private val at = PowerSourceRef.Site(NarrowPassSite.id)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(
        (_, _) => None, Contribution.silent, Some(Waiver(_ => Some(p2), at,
          (_, _) => NarrowPassSitePower.noTargetConsent(at))))))

  test("a power waivable only on its options is waivable"):
    val only = new ConsentPowers(Vector(TargetBarrier))
    assertEquals(only.granter(ruled, TargetBarrier.id), Some(p2))
```

- [ ] **Step 5: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.NarrowPassCampaignConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite"`
Expected: compilation FAILS. `OptionRestriction` takes no third argument, and `NarrowPassSitePower.noTargetConsent` does not exist.

- [ ] **Step 6: Give `OptionRestriction` a waiver**

In `ContributingPower.scala`, replace the `OptionRestriction` doc and case class with:

```scala
/** Forbids one option of the `Decide` the window hooks. Called once per offered
  * option in the window fold, before the query is parked, so a forbidden
  * option is absent from what the projector offers, from what `accepts`
  * validates and from what a simulation answers. Covers cannot-effects that
  * name a choice rather than the whole action. `note` is the hover reason of
  * each option it hides; it writes no Game Log line (travel destination
  * design, ruling 10). `waiver` is set when the power restricts its ruler's
  * enemies: a grant remembered for the decision and the option lifts it, and
  * at a choose-many an option it alone forbids may be asked about (consent
  * design, slice 2).
  */
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent,
    waiver: Option[Waiver] = None
) extends Contribution
```

- [ ] **Step 7: Check the option path against the ledger and offer to ask**

In `WalkerPowerGather.scala`:

1. Add `Waiver` to the `oathdigital.gameplay.powerresolver` import.
2. Below `Attributed`, add:

```scala
  /** An option restriction that forbids an option, with the context it
    * judged in and its violation. */
  private final case class Forbidding(power: PowerId,
      restriction: OptionRestriction, ctx: PowerCtx, violation: OathViolation)

  /** One block on an option, from either kind of restriction: its power, its
    * waiver and the context it judged in. */
  private final case class Block(power: PowerId, waiver: Option[Waiver],
      ctx: PowerCtx)
```

3. Replace `firstNote`, `permits` and `restrictOptions` (from the `firstNote` doc comment to the end of `restrictOptions`) with:

```scala
  /** The restrictions that forbid `ref` of `decide`, in power order. A
    * waivable one is left out once a grant for its power, the decision's
    * owner, the decision and `ref` is remembered this turn (consent design,
    * "One ledger check"). The reference is the option itself, so the grant
    * is tied to its decision.
    */
  private def forbidding(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower])(ref: DecisionOptionRef)
      : Vector[Forbidding] =
    restrictions.flatMap { case (id, restriction) =>
      val ctx = ctxFor(byId(id))
      restriction.fn(ctx, ref)
        .filterNot(_ => restriction.waiver.nonEmpty && ctx.state.consent
          .remembered(ConsentKey(id, decide.owner, decide.decisionId, ref),
            ConsentTurn.of(ctx.state)).contains(ConsentAnswer.Granted))
        .map(Forbidding(id, restriction, ctx, _))
    }

  /** Removes the forbidden options from every `Decide` in `ops`. A
    * forbidden option is kept as askable when it may be asked about, else as
    * unavailable when the first restriction that forbids it notes why; see
    * [[OptionRestriction]] for what happens when nothing is left.
    */
  private def restrictOptions(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[Operation] =
    if restrictions.isEmpty then ops
    else ops.flatMap:
      case decide: Decide =>
        val forbids = forbidding(decide, restrictions, ctxFor, byId)
        WalkerSearch.narrowed(decide, forbids(_).isEmpty, { ref =>
          val found = forbids(ref)
          optionAskable(decide, ref, found).orElse(found.headOption.flatMap(
            first => first.restriction.note(first.ctx, ref)
              .map(OptionReason.Noted(first.power, _))))
        }).toVector
      case other => Vector(other)

  /** `ref`'s consent ask on the option path. Only at a choose-many: a grant
    * there opens the option and answers nothing, so no request can be left
    * waiting on a grant that does not answer (consent slice 2). */
  private def optionAskable(decide: Decide, ref: DecisionOptionRef,
      found: Vector[Forbidding]): Option[OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseMany] then None
    else found.headOption.flatMap(first => consentAsk(decide, ref,
      found.map(forbid => Block(forbid.power, forbid.restriction.waiver,
        forbid.ctx)), first.ctx.state))
```

4. In `emptiedDecision`, replace the `verdicts` line:

```scala
      val verdicts = refs.map(ref => forbidding(decide, restrictions, ctx,
        byId)(ref).headOption.map(_.violation))
```

5. Replace the tree path's `askable` (its doc comment and body) with the shared helper and a slimmer `askable`:

```scala
  /** `ref`'s consent ask on the tree path, when a grant of its powers would
    * open it: a destination the traveller cannot pay for is not worth asking
    * about. Only a choose-one: a grant answers the decision with `ref` alone
    * (slice 1 plan decision 8). */
  private def askable(decide: Decide, ref: DecisionOptionRef,
      fresh: Vector[Attributed], state: ReadyGame,
      grantable: (DecisionOptionRef, Vector[PowerId]) => Boolean)
      : Option[OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseOne] then None
    else consentAsk(decide, ref, fresh.map(breach => Block(breach.power,
      breach.restriction.waiver, breach.ctx)), state)
      .filter(ask => grantable(ref, ask.asks.flatMap(_.powers).distinct))

  /** The ask for `ref` when every block on it is waivable by a player other
    * than the decision's owner and none of their answers on it was refused
    * this turn (consent design, ruling 8). Its reason is the first block's
    * consent note, and it asks each granter about all of their powers. */
  private def consentAsk(decide: Decide, ref: DecisionOptionRef,
      blocks: Vector[Block], state: ReadyGame)
      : Option[OptionReason.Askable] =
    val requester = decide.owner
    val turn = ConsentTurn.of(state)
    val granters = blocks.map(_.waiver.flatMap(_.granter(state))
      .filter(_ != requester))
    val refused = blocks.exists(block => state.consent.remembered(
      ConsentKey(block.power, requester, decide.decisionId, ref), turn)
      .contains(ConsentAnswer.Refused))
    if blocks.isEmpty || granters.exists(_.isEmpty) || refused then None
    else
      val owed = blocks.map(_.power).zip(granters.flatten)
      val asks = owed.map(_._2).distinct.map(granter => ConsentAsk(granter,
        owed.collect { case (power, `granter`) => power }.distinct))
      val first = blocks.head
      first.waiver.map(waiver => OptionReason.Askable(first.power,
        waiver.note(first.ctx, ref), asks))
```

6. Delete any import the compiler names as unused. `PowerNoted` is still used by other code in the package, so leave its definition alone.

- [ ] **Step 8: Declare the Pass's campaign waiver**

In `TravelSitePowers.scala`, in `NarrowPassSitePower`:

1. Add `NarrowPassSitePower.noTargetConsent` to `noteKeys`.
2. Make both clauses use one `waiver` builder:

```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx), travelNote,
        Some(waiver(NarrowPassSitePower.noTravelConsent)))),
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked, campaignNote,
        Some(waiver(NarrowPassSitePower.noTargetConsent)))))

  /** "Unless you have the consent of the Pass's ruler" (consent design,
    * slices 1 and 2): the player who rules the Pass may waive it, for a
    * Travel or for a campaign target. `note` words the reason of an option
    * its ruler may be asked about. */
  private def waiver(note: NoteKey): Waiver = Waiver(
    ready => SiteRulers.rulerOf(ready, site).collect {
      case SiteRuler.Player(player) => player },
    PowerSourceRef.Site(site),
    (_, _) => note(PowerSourceRef.Site(site)))
```

   Delete the old `private val travelWaiver`.
3. In the doc comment of `campaignBlocked`, replace the sentence `Consent is approximated as ruling the Pass, as it was before this was a power.` with `Its ruler is never blocked (consent design, ruling 5); anyone else may ask its ruler for consent (slice 2).`
4. In `object NarrowPassSitePower`, after `noTravelConsent`, add:

```scala
  /** "Cannot target other sites in the region without ruler's consent.":
    * the reason of a campaign target its ruler may be asked about. */
  val noTargetConsent: NoteKey = NoteKey("no-target-consent", Vector(
    NotePart.Text("Cannot target other sites in the region without " +
      "ruler's consent.")))
```

- [ ] **Step 9: Register option-restriction waivers, and read choose-many asks**

In `ConsentPowers.scala`, add `OptionRestriction` to the `powerresolver` import and widen the `collectFirst`:

```scala
    waiver <- power.contributions.values.flatten.collectFirst {
      case Restriction(_, _, Some(waiver)) => waiver
      case OptionRestriction(_, _, Some(waiver)) => waiver }
```

Update the class comment's first sentence to read `every power with a contribution, of either restriction kind, that declares a Waiver`.

In `OathRulesWalker.scala`, `consentAsks`: replace `PricedOptions.options(found)` with `offered(found)`, and add beside it:

```scala
  /** The options of a choose-one or a choose-many, as offered. */
  private def offered(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case many: DecisionQuery.ChooseMany => many.options
      case _ => Vector.empty
```

If `PricedOptions` is then unused in the file, delete it from the import. Add any model import the compiler asks for (`DecisionOption`, `DecisionQuery`).

- [ ] **Step 10: Run the new suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.NarrowPassCampaignConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite"`
Expected: PASS, 10 and 5 tests.

- [ ] **Step 11: Run the neighbours, then the whole server**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.powers.travel.NarrowPassConsentSuite oathdigital.gameplay.powers.targeting.* oathdigital.application.Consent*"`
Expected: PASS. The fixture board's bandit-held Pass still forbids with `no-target`.

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/test/scala/oathdigital/gameplay/PassMap.scala \
  src/test/scala/oathdigital/gameplay/CampaignConsentFixture.scala \
  src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala
git commit -m "feat(consent): let the Narrow Pass's ruler waive it for a campaign target"
```

---

### Task 2: A grant at the targets opens the target and answers nothing

**Files:**
- Create: `src/test/scala/oathdigital/application/CampaignConsentServiceSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (beside `consentAsks`)
- Modify: `src/main/scala/oathdigital/application/ConsentDesk.scala:103-123`

**Interfaces:**
- Consumes (Task 1): `CampaignConsentFixture.passRuledByP2`, `deepWoodsRef`, `targetOptions(state)` and `parkedAt(state)`; the askable campaign target.
- Produces: `OathRules.grantAnswers(state: OathState, requester: PlayerId, decisionId: String): Boolean`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/application/CampaignConsentServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.CampaignConsentFixture
import oathdigital.gameplay.CampaignConsentFixture.{deepWoodsRef,
  passRuledByP2}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.travel.NarrowPassSite
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentRequested}
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking for consent at a Campaign's targets through the service (consent
  * design, slice 2): a grant opens the target, and the campaigner goes on
  * choosing targets. */
class CampaignConsentServiceSuite extends munit.FunSuite:
  private val gameId = "campaign-consent"
  private val pass = NarrowPassSite.pass
  private val request = ConsentRequest(p1, CampaignIds.targets, deepWoodsRef,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val startCampaign = GameCommand.StartWalker(ActionRef.Campaign,
    StartPayload(p1, Vector.empty, Vector.empty))
  private val requestWoods = GameCommand.RequestConsent(p1,
    CampaignIds.targets, deepWoodsRef)
  private val projector = new GameProjector(catalog)

  private def started(service: GameApplicationService, at: Long = 0L)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at, startCampaign))

  /** p1 starts a Campaign and asks about Deep Woods. */
  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, started(service).nextSequence,
      requestWoods))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def woods(at: GameAccepted)(using munit.Location): DecisionOption =
    present(CampaignConsentFixture.targetOptions(at.state)
      .find(_.ref == deepWoodsRef), "Deep Woods among the targets")

  test("Yes opens Deep Woods and answers nothing: the campaigner goes on " +
      "choosing"):
    val (service, repository) = Table.from(passRuledByP2).service()
    val begun = started(service)
    val asked = accepted(service.handle(gameId, begun.nextSequence,
      requestWoods))
    assertEquals(asked.events, Vector(ConsentRequested(request)))
    val yes = answering(service, asked, ConsentChoice.Yes)
    assertEquals(yes.events, Vector(ConsentAnswered(p2, granted = true)))
    assertEquals(CampaignConsentFixture.parkedAt(yes.state),
      Some(CampaignIds.targets))
    assert(woods(yes).available)
    // Still p1's Campaign to undo: the grant opened no batch.
    assertEquals(yes.undo.map(step => (step.issuer, step.from)),
      begun.undo.map(step => (step.issuer, step.from)))
    val chosen = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(CampaignIds.targets,
        DecisionAnswer.ChooseManyAnswer(Vector(deepWoodsRef))))))
    assertEquals(CampaignConsentFixture.parkedAt(chosen.state),
      Some(CampaignIds.force))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((chosen.state, chosen.undo))))

  test("No leaves Deep Woods with the ordinary reason"):
    val (service, _) = Table.from(passRuledByP2).service()
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    woods(no) match
      case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
        assertEquals(note.key, "no-target")
      case other => fail(s"expected a plain unavailable target, got $other")

  test("Always Allow opens Deep Woods and sets the ruler's preference"):
    val (service, _) = Table.from(passRuledByP2).service()
    val always = answering(service, asking(service), ConsentChoice.Always)
    assertEquals(always.events, Vector(ConsentAnswered(p2, granted = true),
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always)))
    assert(woods(always).available)

  test("after a grant, the campaigner may undo the Campaign, and Deep " +
      "Woods is open when it starts again"):
    val (service, _) = Table.from(passRuledByP2).service()
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    val undone = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(CampaignConsentFixture.parkedAt(undone.state), None)
    assert(woods(started(service, undone.nextSequence)).available)

  test("the campaigner sees Deep Woods askable, with the consent reason " +
      "and whom to ask"):
    val (service, _) = Table.from(passRuledByP2).service()
    val begun = started(service)
    val row = present(projector.project(gameId, begun.loaded, p1)
      .walkerDecision.flatMap(_.query).toVector.flatMap(_.offeredOptions)
      .find(_.id == deepWoodsRef.wireId), "the Deep Woods row")
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Narrow Pass: Cannot target other sites " +
      "in the region without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Narrow Pass"))))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.application.CampaignConsentServiceSuite"`
Expected: 3 FAIL, 2 PASS.
- The tests for Yes, Always Allow and undo FAIL: the answer is rejected, because `ConsentDesk.settled` answers the choose-many targets with a choose-one answer.
- The No and projection tests PASS on Task 1's work.

- [ ] **Step 3: Tell the desk when a grant answers the decision**

In `OathRulesWalker.scala`, below `consentAsks`, add:

```scala
  /** Whether a grant answers `requester`'s parked `decisionId` with its
    * target. Only a choose-one: one option is its whole answer. A grant at a
    * choose-many opens the option, and the requester goes on choosing
    * (consent slice 2). */
  def grantAnswers(state: OathState, requester: PlayerId, decisionId: String)
      : Boolean =
    val parked = walkerResumeContext(state).flatMap {
      case (ready, _, tree, pending, powers, _, _) =>
        walkerCall(Right(ProcedureWalker.parkedDecide(ready, tree, pending,
          powers)))
    }
    parked.toOption.flatten.exists(decide =>
      decide.decisionId == decisionId && decide.owner == requester &&
        decide.query.isInstanceOf[DecisionQuery.ChooseOne])
```

In `ConsentDesk.scala`, `settled`:

1. Guard the case that answers the decision:

```scala
      request.filter(_ => granted) match
        case Some(done) if rules.grantAnswers(after, done.requester,
            done.decisionId) =>
          rules.resolveWalker(after, done.requester, done.decisionId,
            DecisionAnswer.ChooseOneAnswer(done.target))
          .map(answered =>
            events ++ (CommandAccepted(done.requester) +: answered.events))
        case _ => Right(events)
```

2. Replace its doc comment with:

```scala
  /** `events`, followed, when their last answer completes the request with
    * every ask granted at a choose-one, by the requester's answer to the
    * parked decision. That answer is the requester's command batch, though
    * the granter's command produced it, so the requester can undo the move
    * (consent design, "Settling"). At a choose-many, the grant only opens
    * the option, and the requester goes on choosing (slice 2). */
```

- [ ] **Step 4: Run the suite and the slice 1 suites**

Run: `./sbtw "testOnly oathdigital.application.CampaignConsentServiceSuite oathdigital.application.Consent*"`
Expected: PASS. Slice 1's Travel grant still moves the pawn, because its destination is a choose-one.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/application/ConsentDesk.scala \
  src/test/scala/oathdigital/application/CampaignConsentServiceSuite.scala
git commit -m "feat(consent): a grant at the campaign targets opens the target and answers nothing"
```

---

### Task 3: The board's choose-many asks, the docs, and the gate

**Files:**
- Modify: `frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `CONTEXT.md:79-83`
- Modify: `docs/ROADMAP.md:75-78` and `:180-182`
- Modify: `docs/rules/implementation-traceability.md:133`

**Interfaces:**
- Consumes (slice 1): `BoardOptions(views, pick, ask)`, `BoardOptionViews.of(decision, query, draft)`, `ConsentAsking.asker(decisionId, value, canControl, controls)`, `BoardSelectionDraft(context, decisionId, query, keys)`, `BoardSelectionContext(gameId, playerId, sequence)`.
- Produces: nothing new.

- [ ] **Step 1: Pin the board's choose-many path with a test**

Add to `ConsentAskingSuite.scala`:

```scala
  test("in a choose-many on the board an askable target asks, is never " +
      "picked, and the other targets still pick"):
    val peaks = DecisionOptionState("site", "site:broken-peaks",
      "Broken Peaks")
    val targets = DecisionQueryState.ChooseMany(Vector(peaks, woods), 0, 2)
    val conquest = WalkerDecisionState("campaign", "campaign.targets",
      "decide", query = Some(targets), presentation = "board")
    val ui = new RecordingControls()
    var picked = Vector.empty[String]
    val views = BoardOptionViews.of(conquest, targets, None)
    assertEquals(views.get(BoardOptionViews.key(woods)).map(_.state),
      Some(OptionState.Askable))
    val options = new BoardOptions(views, option => picked :+= option.id,
      ConsentAsking.asker("campaign.targets", table, canControl = true, ui))
    val woodsBox = dom.document.createElement("article")
    val peaksBox = dom.document.createElement("article")
    options.decorate(woodsBox, Vector(BoardOptionViews.key(woods)))
    options.decorate(peaksBox, Vector(BoardOptionViews.key(peaks)))
    click(woodsBox)
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(Intent.RequestConsent(
      "campaign.targets", WalkerStartArgWire("site", "site:deep-woods"))))
    click(peaksBox)
    assertEquals(picked, Vector("site:broken-peaks"))
    // A draft never holds it either.
    val draft = BoardSelectionDraft(BoardSelectionContext("g", "blue", 1L),
      "campaign.targets", targets, Vector.empty)
    assertEquals(draft.click(woods).keys, Vector.empty)
```

- [ ] **Step 2: Run it**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ConsentAskingSuite"`
Expected: PASS. The board path already treats an askable option alike at a choose-one and a choose-many.

If it fails, the failure names the path that mishandles a choose-many. Fix that path in `BoardOptions.scala` or `BoardSelectionDraft.scala`, following how the choose-one path treats `OptionState.Askable`. Then rerun.

- [ ] **Step 3: Record slice 2 in the spec**

In `docs/superpowers/specs/2026-10-08-consent-system-design.md`, make these replacements:

1. In the status line, replace

   `Slice 1 is implemented ([plan](../plans/2026-10-09-consent-slice1.md)), and its plan's decisions are folded in below.`

   with

   `Slices 1 and 2 are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md)), and their plans' decisions are folded in below.`

2. Under "Settling", after the bullet that begins `- When all asks are granted, record Granted`, add the bullet:

   ```
   - At a choose-many, a grant answers nothing. It records Granted and clears
     the request, and the decision re-renders with the target choosable. The
     requester goes on choosing, so no batch opens.
   ```

   In the bullet before it, replace `When all asks are granted,` with `When all asks are granted at a choose-one,`.

3. Under "Walker and powers", replace

   `` `Restriction` gains a field `waiver: Option[Waiver]`, and `OptionRestriction` gains the same in slice 2. ``

   with

   `` `Restriction` and `OptionRestriction` each have a field `waiver: Option[Waiver]`. ``

4. Under "One ledger check", after the bullet `- For an `OptionRestriction`, the reference is the option being judged.`, append to that bullet: ` Its key names the decision and the option, so the grant is tied to its decision.`

5. Replace

   ``reason. The same classification applies
on the `restrictOptions` and `firstNote` path that `OptionRestriction` uses.``

   with

   ``reason. On the `restrictOptions` path that `OptionRestriction` uses, an
option that only waivable restrictions forbid becomes askable at a
choose-many only, with no search: a grant there answers nothing, so it cannot
leave a request waiting on a grant that fails.``

6. Under "Reasons", after the Pass's `no-travel-consent` sentence, add: `Its campaign clause gets `no-target-consent`: "Cannot target other sites in the region without ruler's consent."`

7. Under "Deferred", replace the item that begins `- Tying a grant to the decision that caused the breach.` (all its lines) with:

   ```
   - Tying a grant to the decision that caused the breach on the tree path.
     The ledger check drops a waivable `Restriction`'s breach for any granted
     choose-one answer of the walk. The option path's key names the decision,
     so it is exact. The key does not name the granter. The Pass's Travel
     destination is the only tree-path decision so far; slice 4 must tie
     them.
   ```

8. In the last "Deferred" item, replace `board-decision buttons.` with `board-decision buttons, and on a choose-many drawn in the panel.`, and replace `no slice-1 option takes them.` with `no option yet takes them.`

9. Add a "Deferred" item:

   ```
   - Keeping a campaigner's picks across a consent request. The client drops
     a board draft when the sequence moves, so after a grant the other
     targets are picked again.
   ```

Check that each replaced string is found exactly once before replacing it.

- [ ] **Step 4: Update the vocabulary, the roadmap and the traceability**

In `CONTEXT.md`, replace the **Askable option** definition's text with:

```
An option of a parked choose-one, or of a choose-many whose options a power
forbids one by one, blocked only by waivable powers whose granters are other
players, with nothing refused for it this turn, which a grant would open.
Choosing it asks the granters first. A grant answers a choose-one with it,
and only opens it in a choose-many.
```

In `docs/ROADMAP.md`:

- Replace `- [ ] 2. The Pass on campaign targeting.` with:

  ```
  - [x] 2. The Pass on campaign targeting
    ([plan](superpowers/plans/2026-10-09-consent-slice2.md)).
  ```

- In the "Play-test consent in a browser" item, replace `The amber destination, the` with `The amber destination and campaign target, the`.

In `docs/rules/implementation-traceability.md`, row "“Enemies cannot” consent enforcement":

- Replace `Narrow Pass's Travel restriction is waivable: a destination only it blocks is askable,` with `Narrow Pass is waivable on Travel and on campaign targeting: a destination or extra target only it blocks is askable,`.
- Replace `Tests: `NarrowPassConsentSuite`,` with `Tests: `NarrowPassConsentSuite`, `NarrowPassCampaignConsentSuite`, `CampaignConsentServiceSuite`,`.
- Replace `The Pass on campaign targeting, Secret Police,` with `Secret Police,`, and `consent slices 2-4` with `consent slices 3 and 4`.

- [ ] **Step 5: Run the full gate**

Run, in order:
1. `./sbtw "test" "frontend/test"` — expected PASS.
2. `./sbtw frontend/fullOptJS` — expected success.
3. `./sbtw coverage test coverageReport` — expected PASS, with the coverage gate met.
4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py` — expected no findings.
5. `git diff --check` — expected no output.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/test/scala/oathdigital/frontend/ConsentAskingSuite.scala \
  docs/superpowers/specs/2026-10-08-consent-system-design.md \
  docs/superpowers/plans/2026-10-09-consent-slice2.md \
  CONTEXT.md docs/ROADMAP.md docs/rules/implementation-traceability.md
git commit -m "docs(consent): record slice 2 in the spec, the vocabulary, the roadmap and the traceability"
```
