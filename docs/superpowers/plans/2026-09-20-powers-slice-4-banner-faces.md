# Powers Slice 4: Banner Faces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the two banner-face powers of the first powers batch: Wandering Flame, the Banner of the Darkest Secret's two ACTION powers (move your pawn to a site with a secret on it, and place a secret on your site), and Mob, the Banner of the People's Favor's permission to discard a card of a site before playing a card there.

**Architecture:** Wandering Flame is two `PhasePower`s sourced from the banner, each a few lines over the operations that already exist (`Move`, and the slice 1d pawn helpers). Mob is one `ContributingPower`: a `Transform` at the card-play window that sets `PlacementRules.siteDiscardFirst`, which slice 2a built for it. The design expected no engine change beyond E3's banner source and E6's `PlacementRules`, both merged, and the walker and the operations need none. Planning found three small gaps around the powers instead (the source index lists no power for these faces, the reviewed catalog would refuse the ids, and the projector has no name for a banner power). Each is in the task that needs it.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) (E3's banner source, E6's `PlacementRules`, and the slicing table) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (the "Slice 4: banner faces" section, "Rules that apply to every power", and the slice 2a implementation notes). Builds on the [slice 2 plan](2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (2a supplied `PlacementRules`) and the [slice 1d plan](2026-09-20-powers-slice-1d-movement.md) (the pawn helpers).

## Global Constraints

- `BackendArchitectureSuite` and `scripts/check-architecture.py` apply: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources; a power imports nothing from `oathdigital.gameplay.walker`; no direct state write (`copy(advisers =`, `map.copy(sites =` and similar) under `gameplay/powers`; no `gameplay` source contains the word `rulesText`. `scripts/check-markdown-links.py` gates the docs.
- `DiscardRestrictionsCoverageSuite`: a production file that builds a `Discard.Denizen(`, `Discard.Vision(` or `Discard.RuinedEdifice(` must attach `DiscardRestrictions`. Neither sub-slice builds a discard: Mob only turns on the card-play planner's own discard, which already attaches them.
- Every power is declared solely as a `ContributingPower` or a `PhasePower` over existing operations. No engine code changes.
- A power is registered through one object of powers, `BannerFacePowers`, and one line in a catalog: `PhasePowerCatalog` for Wandering Flame (4a) and `WalkerPowerCatalog` for Mob (4b), as slices 1 to 3 did.
- A power name that extends `ContributingPower` or `PhasePower` directly is scanned for in the engine sources and must be at least four characters, so Mob is `PeoplesFavorMob`.
- Recorded operations must survive the journal wire and replay. Each suite that runs a power asserts that a replay of the recorded events reaches the state the command reached, and the Wandering Flame suites assert `PaidActionHarness.wireRoundTrips`.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the whole suite with `./sbtw test`, and one suite with `./sbtw "testOnly <fully.qualified.Suite>"`. The full run must print a `Passed: Total` line.
- Test fixtures keep the card inventory whole: a card leaves the place it came from when a fixture places it.

## How this slice is split

The slice is small, and its two powers share no code beyond a registration object. It should be **executed, reviewed and merged as two sub-slices**, in this order. Each ends green (`./sbtw test` and the architecture check) and is mergeable alone.

| Sub-slice | Tasks | Contents | Engine changes | Needs |
| --- | --- | --- | --- | --- |
| 4a. Wandering Flame | 1 | the move power and the place-a-secret power; the source index listing the Wandering Flame face's powers; the audited ids; the projector's names for banner powers; `BannerFacePowers` | none | nothing |
| 4b. Mob | 2 | `PeoplesFavorMob`; the source index listing the Mob face's power; its audited id; the registration in `WalkerPowerCatalog` | none | 4a for `BannerFacePowers` and the fixtures |

Why this split, and not one task:

- **The two powers use different seams.** Wandering Flame is a phase power the player starts, with a decision and a `Move`. Mob is a rule that changes how a card play is planned, and its tests drive whole Searches. A reviewer can accept one and reject the other.
- **4a carries the shared plumbing.** The three gaps in the list of what planning found (the index, the audit, the projector) are about banner faces as a whole, and the first power that needs them takes them. 4b then adds one line to each.
- **Either could go first.** 4b needs only the registration object and the fixtures that 4a creates, so the order is a convenience. If 4b had to go first it would create `BannerFacePowers` itself, holding only `contributions`.

## Engine changes

**None to the walker, the operations, or the operation vocabulary.** E3's banner source (`PowerSourceRef.Banner`, the widened `PhasePowerProcedure`) and E6's `PlacementRules` are merged and are all the two powers need: Wandering Flame's move and placement are plain `Move`s, and Mob is one `Transform` that calls `PlacementTree.adjust` with `_.withSiteDiscardFirst`.

Three changes outside the powers were needed. None is in the design. Each is small, each is covered by a test that fails without it, and each is in the report:

- **The source index lists the powers of the Mob and Wandering Flame faces (Tasks 1 and 2).** `RuleSourceIndex` gave both faces an empty power list, so `PhasePowerProcedure.sources`, which finds a phase power's sources through that list, never found a Wandering Flame power. It now lists the two Wandering Flame ids on that face and the Mob id on Mob's, as it already listed the two synthetic ids of the other faces.
- **The reviewed catalog audits those ids (Tasks 1 and 2).** The legacy resolver rejects any listed id that is not audited (`PowerResolver.validateSources`), and `PowerRuntime.options` runs it at every modifier selection. Listing the ids without auditing them would fail every Search, Travel and other action while the banner is on that face, which is the first game's default. The ids join `ReviewedPowerCatalog.syntheticIds`, beside the two banner ids already there. The handler fingerprint is unchanged, because synthetic ids are outside it.
- **The projector names a banner's phase power (Task 1).** `PhasePowerProjector.printed` returned nothing for a banner source, with a comment that the slice using them would supply labels. Without a name and a text the projector drops the power, and the client never gets its `usePower` button. The names live in `BannerFacePowers`, because a banner face has no catalog entry.

## What planning found

These facts are read from the code, or established by compiling and running the plan's code in a throwaway copy of `main`. They shape the tasks and are not in the design.

1. **E3 and E6 are enough.** `PhasePowerSuite` already shows a banner is a phase-power source for its holder only (`PowerAccess.accessible` checks the holder) using a synthetic power on the Grand Council face. `PhasePower.cost` defaults to free, and `PhasePowerProcedure.payable` refuses a costed banner power on purpose (banners have no costs), which suits both Wandering Flame powers. `PlacementTree.adjust` and `PlacementRules.withSiteDiscardFirst` are what `SiteDiscardFirstSuite` exercises with a test double.
2. **The first game is staged for these powers, and it needs clearing.** Both banners start unheld, on the Mob and Wandering Flame faces. The actor (p2) stands at ancient-city, p1 at buried-giant and p3 at broken-peaks. But broken-peaks starts with two secrets on the site (a site's starting resources) and fair-isle with three favor, so a test that counts sites with a secret first clears them (`BannerFixture.withoutSiteSecrets`).
3. **A `Move` of a secret onto a site moves faceup secrets only.** `OperationSecretPlanner` treats every destination other than a play area as faceup-only, so "move 1 secret from your board onto the site" can only mean a faceup secret, which also matches the cost rules (a secret placed on a card rests faceup). A player with only facedown secrets has nothing to place. The product owner ruled that the power is then not usable (the button is hidden), so `usable` reads `board.faceUpSecrets`, and the effect guards on it again because it is derived afresh on every command. A facedown secret is never flipped or moved.
4. **`PaidAction` with `Cost.free` is the right kit.** Slice 1's `PaidAction` states an ACTION power gated only by its cost, and a free power is its degenerate case. Act powers are not tracked in `usedPowers`, so both powers are unlimited without any code. The banner resource action (`PlaceBannerResource`) is a walker procedure, not a phase power, and is unaffected.
5. **`PawnMoves` (slice 1d) already holds what the move power needs**: `pawnSite`, `sitesOtherThan`, `siteChoice`, `relocate` and `chosenSite`. The decision is the live-`Branch` shape of Brass Horse, asked only when several sites qualify. Nothing runs between the park and the answer, so the resume derives the same choice.
6. **Both powers are found through the source index, so the face gates them.** `PhasePowerProcedure.sources` keeps a source whose listed power ids contain the power, and `PowerAccess.accessible` keeps it only for the holder. The powers therefore need no face check of their own, and the tests prove both gates: the Festival face lists neither, and a non-holder finds neither.
7. **Banner powers are not catalogued.** `CatalogResolution.printed` finds nothing for these ids, so `PowerKindsCatalogSuite` needs no line: its flags describe printed catalog powers. A test in `BannerFaceSourcesSuite` states this.
8. **The word `rulesText` must not appear in gameplay sources.** `check-architecture.py` scans for it. `BannerFacePowers` calls the text `text`, and the projector, in the application layer, builds the `CatalogPower` the projection wants.
9. **Power names are scanned.** `BackendArchitectureSuite` reads the nearest declaration before every `extends ContributingPower` or `PhasePower`, strips a trailing `Contribution`, requires at least four characters, and searches the engine sources for the lowercase name. `Mob` would fail the length rule, so the object is `PeoplesFavorMob`. The Wandering Flame objects extend `PaidAction`, which is not scanned.
10. **Mob reaches every play to a site.** Search and the facedown-adviser action both build `CardPlayProcedure.PlacementTree`, so one `Transform` at `SearchPlayAdviser` covers both. The playing player is `PowerCtx.activePlayer`. A Conspiracy or a Vision cannot be played to a site, so it is unaffected.
11. **Mob composes.** `PlacementRules` changes are independent, so Mob and Silver Tongue's adviser limit apply in either order. The faceup Silver Tongue is a locked card, so it cannot be the discard that makes room for a third adviser.
12. **Mob builds no discard.** The card-play planner builds it, with `DiscardRestrictions` attached, so the generic rules decide the cards: a locked card, an intact edifice and a card that prints a power selected for the running action are never offered. A ruined edifice may go, back to the edifice deck. `DiscardRestrictionsCoverageSuite` needs no line.
13. **A secret placed on a site can be taken by Take Wealth.** Take Wealth takes a favor or a secret from the site's tokens whatever put it there, so a secret Wandering Flame places is available to anyone at that site (no enemy pawn present), once per turn. The engine already treats site tokens uniformly, and no code changes.
14. **The client needs no code.** `PhasePowerButtons` is generic, and the client's `banner`/`darkest-secret` source decodes to `DecisionOptionRef.Banner` through `DecisionOptionRef.fromWire`. `BannerFaceProjectionSuite` proves the projection, the legal control and the binding.

## File Structure

All paths are under `src/main/scala/oathdigital/` (production) or `src/test/scala/oathdigital/` (tests) unless written in full. The step blocks below give full paths.

- **Task 1 (4a):** create `gameplay/powers/banner/WanderingFlameMove.scala`, `WanderingFlamePlace.scala` and `BannerFacePowers.scala`; modify `gameplay/powers/PhasePowerCatalog.scala`, `gameplay/RuleSourceIndex.scala`, `gameplay/powers/ReviewedPowerCatalog.scala` and `application/PhasePowerProjector.scala`. Test: create `gameplay/powers/banner/BannerFixture`, `WanderingFlameMoveSuite`, `WanderingFlamePlaceSuite`, `BannerFaceSourcesSuite` and `application/BannerFaceProjectionSuite`. Docs: the design's status and slicing table, the rulings' Slice 4 table and implementation notes.
- **Task 2 (4b):** create `gameplay/powers/banner/PeoplesFavorMob.scala`; modify `gameplay/powers/banner/BannerFacePowers.scala`, `gameplay/powers/WalkerPowerCatalog.scala`, `gameplay/RuleSourceIndex.scala` and `gameplay/powers/ReviewedPowerCatalog.scala`. Test: create `gameplay/powers/banner/PeoplesFavorMobSuite`. Docs: the same three places.

---

## Sub-slice 4a: Wandering Flame

### Task 1: Wandering Flame

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlameMove.scala`, `WanderingFlamePlace.scala`, `BannerFacePowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`, `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala`, `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/banner/{BannerFixture, WanderingFlameMoveSuite, WanderingFlamePlaceSuite, BannerFaceSourcesSuite}.scala`, `src/test/scala/oathdigital/application/BannerFaceProjectionSuite.scala`
- Docs: `docs/superpowers/specs/2026-09-20-powers-design.md`, `docs/superpowers/specs/2026-09-20-powers-rulings.md`

**Interfaces:**
- Consumes: E3's `PhasePower`, `PowerSourceRef.Banner` and `PhasePowerProcedure`; slice 1's `PaidAction`; slice 1d's `PawnMoves` (`pawnSite`, `sitesOtherThan`, `siteChoice`, `relocate`, `chosenSite`); `RuleSourceIndex`, `ReviewedPowerCatalog.syntheticIds`.
- Produces: `case object WanderingFlameMove` (id `banner.darkest-secret.wandering-flame.move`, `decisionId = "power.wandering-flame.site"`); `case object WanderingFlamePlace` (id `banner.darkest-secret.wandering-flame.place`); `object BannerFacePowers { val phasePowers: Vector[PhasePower]; def printed(id: PowerId): Option[(String, String)] }` (4b adds `contributions`); the test object `BannerFixture` (`holdingFlame`, `holdingFavor`, `withSiteSecrets`, `withoutSiteSecrets`, `withCardTokens`, `siteSecrets`, `pawnOf`, `usable`, `backToActing`, `ops`, and the site and banner constants).

**Rules built (from the rulings).** Both are Act `PhasePower`s sourced from the Banner of the Darkest Secret, usable by its holder only while its Wandering Flame face is up, free and unlimited.

| Power | Ruling |
| --- | --- |
| Move | Place your pawn at any other site with a secret on the site itself (`SiteState.tokens.secrets`), not on its cards. A plain `Move`, not Travel. The decision is asked only when more than one site qualifies. Usable only when a site qualifies. |
| Place a secret | Move 1 secret from your board onto the site your pawn is at. Only a faceup secret can rest on a site, so the power is usable only while the holder has a faceup secret on their board (product ruling: it is hidden otherwise). A facedown secret is never flipped or moved. |


- [ ] **Step 1: Write the tests**

`BannerFixture` stages the banner faces and the sites. The four suites cover the powers' rulings, the source index and audit, and what the client is shown.

Create `src/test/scala/oathdigital/gameplay/powers/banner/BannerFixture.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.model._

/** Staging shared by the banner-face suites. The first game seats three
  * players: the actor (p2) at ancient-city, p1 at buried-giant and p3 at
  * broken-peaks. Both banners start unheld, on Mob and Wandering Flame.
  */
object BannerFixture {
  import PowerFixture._

  val p1: PlayerId = PlayerId("p1")
  val p3: PlayerId = PlayerId("p3")

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val brokenPeaks: SiteId = SiteId("site:broken-peaks")
  val buriedGiant: SiteId = SiteId("site:buried-giant")
  val deepWoods: SiteId = SiteId("site:deep-woods")

  val darkestSecret: DecisionOptionRef =
    DecisionOptionRef.Banner(Banner.DarkestSecret)
  val peoplesFavor: DecisionOptionRef =
    DecisionOptionRef.Banner(Banner.PeoplesFavor)

  /** The actor holds the Banner of the Darkest Secret, on `face`. */
  def holdingFlame(ready: ReadyGame,
      face: DarkestSecretFace = DarkestSecretFace.WanderingFlame,
      holder: Option[PlayerId] = Some(actor)): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = c.banners.copy(darkestSecret =
      c.banners.darkestSecret.copy(active = face, holder = holder))))

  /** The actor holds the Banner of the People's Favor, on `face`. */
  def holdingFavor(ready: ReadyGame,
      face: PeoplesFavorFace = PeoplesFavorFace.Mob,
      holder: Option[PlayerId] = Some(actor)): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = c.banners.copy(peoplesFavor =
      c.banners.peoplesFavor.copy(active = face, holder = holder))))

  def withSiteSecrets(ready: ReadyGame, site: SiteId, secrets: Int)
      : ReadyGame = ready.updateCurrent(c => c.copy(map = c.map.copy(
    sites = c.map.sites.updated(site, c.map.sites(site).copy(tokens =
      c.map.sites(site).tokens.copy(secrets = secrets))))))

  /** No site holds a secret of its own. The first game starts with some (a
    * site's printed starting resources), so a test that counts them clears
    * them first.
    */
  def withoutSiteSecrets(ready: ReadyGame): ReadyGame =
    ready.game.current.map.inPlay.foldLeft(ready)(withSiteSecrets(_, _, 0))

  /** `card`, already at `site`, holds `tokens`. */
  def withCardTokens(ready: ReadyGame, site: SiteId, card: DenizenId,
      tokens: Tokens): ReadyGame = ready.updateCurrent(c => c.copy(map =
    c.map.copy(sites = c.map.sites.updated(site, c.map.sites(site).copy(
      denizens = c.map.sites(site).denizens.map {
        case d: DenizenState if d.id == card => d.copy(tokens = tokens)
        case other => other
      })))))

  def siteSecrets(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites(site).tokens.secrets

  def pawnOf(ready: ReadyGame, id: PlayerId = actor): SiteId =
    player(ready, id).pawnSite.get

  def usable(ready: ReadyGame, power: PowerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == power)

  def backToActing(transition: OathTransition): Boolean =
    transition.continue == OathContinue.ActActionSelection(actor)

  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: oathdigital.gameplay.walker.WalkerStepRecorded =>
      step.ops }.flatten
}
```

Create `src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlameMoveSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WanderingFlameMoveSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import TargetsFixture.{after, answer, awaits, pick, replayed, use}

  private val power = WanderingFlameMove

  /** The actor holds the banner in the Act phase, and exactly one secret lies
    * on each site in `marked`.
    */
  private def staged(marked: SiteId*): ReadyGame = inPhase(
    marked.foldLeft(withoutSiteSecrets(holdingFlame(base)))(
      withSiteSecrets(_, _, 1)), Phase.Act)

  test("it is a registered phase power with its own id") {
    assert(PhasePowerCatalog.default(catalog).find(power.id).isDefined)
    assertEquals(power.id.value, "banner.darkest-secret.wandering-flame.move")
  }

  test("one secret-bearing site: the pawn goes there with no question, by a " +
      "plain Move that leaves the secret where it is") {
    val start = staged(brokenPeaks)
    val done = use(start, power, darkestSecret).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(after(done)), brokenPeaks)
    assertEquals(siteSecrets(after(done), brokenPeaks), 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Pawn(actor), PositionedLocation(Location.Site(ancientCity)),
        PositionedLocation(Location.Site(brokenPeaks)))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("several candidates: the player chooses among exactly the other " +
      "sites that hold a secret") {
    val start = staged(brokenPeaks, buriedGiant, ancientCity)
    val parked = use(start, power, darkestSecret).toOption.get
    assert(awaits(parked, power.decisionId))
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(ancientCity))).isLeft, "the pawn's own site")
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(deepWoods))).isLeft, "a site with no secret")
    val done = answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(buriedGiant))).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(after(done)), buriedGiant)
    assertEquals(replayed(start, parked.events ++ done.events),
      Right(done.state))
  }

  test("a secret on a card at a site does not count, only one on the site") {
    val card = DenizenId("7")
    val onCard = withCardTokens(atSite(staged(), card, brokenPeaks),
      brokenPeaks, card, Tokens(0, 1))
    assert(!usable(onCard, power.id))
    assert(usable(withSiteSecrets(onCard, brokenPeaks, 1), power.id))
  }

  test("it is unlimited: it can be used again in the same turn") {
    val start = staged(brokenPeaks, buriedGiant)
    val first = use(start, power, darkestSecret).toOption.get
    val parked = answer(first, actor, power.decisionId,
      pick(DecisionOptionRef.Site(brokenPeaks))).toOption.get
    val second = use(after(parked), power, darkestSecret)
    assert(second.isRight, second.toString)
    assertEquals(after(parked).game.current.turn.usedPowers, Set.empty[PowerUseRef])
  }

  test("it is unusable with no other site holding a secret, without the " +
      "banner, or on the Festival face") {
    assert(!usable(staged(), power.id))
    assert(use(staged(), power, darkestSecret).isLeft)
    assert(!usable(staged(ancientCity), power.id), "only the pawn's own site")
    assert(usable(staged(brokenPeaks), power.id))
    val notHeld = inPhase(withSiteSecrets(holdingFlame(base, holder = None),
      brokenPeaks, 1), Phase.Act)
    assert(!usable(notHeld, power.id))
    assert(use(notHeld, power, darkestSecret).isLeft)
    val theirs = inPhase(withSiteSecrets(holdingFlame(base, holder = Some(p1)),
      brokenPeaks, 1), Phase.Act)
    assert(!usable(theirs, power.id))
    val festival = inPhase(withSiteSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), brokenPeaks, 1), Phase.Act)
    assert(!usable(festival, power.id))
    assert(use(festival, power, darkestSecret).isLeft)
  }

  test("it is usable in the Act phase only") {
    val wake = inPhase(withSiteSecrets(holdingFlame(base), brokenPeaks, 1),
      Phase.Wake)
    assert(!usable(wake, power.id))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlaceSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WanderingFlamePlaceSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import TargetsFixture.{after, replayed, use, withSecrets}

  private val power = WanderingFlamePlace

  /** The actor holds the banner in the Act phase with the given secrets. */
  private def staged(faceUp: Int, faceDown: Int = 0): ReadyGame = inPhase(
    withSecrets(holdingFlame(base), actor, faceUp, faceDown), Phase.Act)

  test("it is a registered phase power with its own id") {
    assert(PhasePowerCatalog.default(catalog).find(power.id).isDefined)
    assertEquals(power.id.value, "banner.darkest-secret.wandering-flame.place")
    assertNotEquals(power.id, WanderingFlameMove.id)
  }

  test("it moves one secret from the actor's board onto the pawn's site, " +
      "and nothing else changes") {
    val start = staged(faceUp = 2, faceDown = 1)
    val done = use(start, power, darkestSecret).toOption.get
    val end = after(done)
    assert(backToActing(done))
    assertEquals(player(end).board.faceUpSecrets, 1)
    assertEquals(player(end).board.faceDownSecrets, 1)
    assertEquals(siteSecrets(end, ancientCity), siteSecrets(start, ancientCity) + 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(ancientCity)))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("it places on the site the pawn is at, whichever site that is") {
    val start = TargetsFixture.withPawn(staged(faceUp = 1), actor, brokenPeaks)
    val end = after(use(start, power, darkestSecret).toOption.get)
    assertEquals(siteSecrets(end, brokenPeaks), siteSecrets(start, brokenPeaks) + 1)
    assertEquals(siteSecrets(end, ancientCity), siteSecrets(start, ancientCity))
  }

  test("it is unlimited: each use places another secret") {
    val first = use(staged(faceUp = 3), power, darkestSecret).toOption.get
    val second = use(after(first), power, darkestSecret).toOption.get
    val end = after(second)
    assertEquals(player(end).board.faceUpSecrets, 1)
    assertEquals(siteSecrets(end, ancientCity),
      siteSecrets(staged(faceUp = 3), ancientCity) + 2)
    assertEquals(end.game.current.turn.usedPowers, Set.empty[PowerUseRef])
  }

  test("with no faceup secret it is not usable, and a facedown secret is " +
      "never flipped or moved") {
    Vector(staged(faceUp = 0), staged(faceUp = 0, faceDown = 2)).foreach { start =>
      assert(!usable(start, power.id))
      assert(use(start, power, darkestSecret).isLeft)
    }
    assert(usable(staged(faceUp = 1), power.id))
    val end = after(use(staged(faceUp = 1, faceDown = 2), power, darkestSecret)
      .toOption.get)
    assertEquals(player(end).board.faceUpSecrets, 0)
    assertEquals(player(end).board.faceDownSecrets, 2)
    assert(!usable(end, power.id), "only facedown secrets are left")
  }

  test("it is unusable without the banner, on the Festival face or outside " +
      "the Act phase") {
    val notHeld = inPhase(withSecrets(holdingFlame(base, holder = None), actor,
      2, 0), Phase.Act)
    assert(!usable(notHeld, power.id))
    assert(use(notHeld, power, darkestSecret).isLeft)
    val theirs = inPhase(withSecrets(holdingFlame(base, holder = Some(p1)),
      actor, 2, 0), Phase.Act)
    assert(!usable(theirs, power.id))
    val festival = inPhase(withSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), actor, 2, 0), Phase.Act)
    assert(!usable(festival, power.id))
    assert(use(festival, power, darkestSecret).isLeft)
    assert(!usable(inPhase(staged(2), Phase.Wake), power.id))
  }

  test("the Move power and the place power are separate uses of one banner") {
    val start = withSiteSecrets(staged(faceUp = 1), brokenPeaks, 1)
    assertEquals(PaidActionHarness.usableIds(start).toSet,
      Set(WanderingFlameMove.id, WanderingFlamePlace.id))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/banner/BannerFaceSourcesSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.{PowerRuntime, RuleSourceFace, RuleSourceIndex}
import oathdigital.gameplay.powers.{CatalogResolution, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The source index lists each banner face's powers, and the reviewed
  * catalog still audits every source the index lists.
  */
class BannerFaceSourcesSuite extends munit.FunSuite {
  import PowerFixture.base
  import BannerFixture._

  private def darkest(ready: ReadyGame) = RuleSourceIndex.enumerate(catalog,
    ready).filter(_.source == RuleSourceRef.Banner(Banner.DarkestSecret.key))

  test("the Wandering Flame face lists its two powers, and Festival its own") {
    val flame = darkest(base).head
    assertEquals(flame.face, RuleSourceFace.WanderingFlame)
    assertEquals(flame.powerIds, Vector(WanderingFlameMove.id,
      WanderingFlamePlace.id))
    val festival = darkest(holdingFlame(base, DarkestSecretFace.Festival)).head
    assertEquals(festival.handlerIds, Vector("banner.darkest-secret.festival"))
  }

  test("the reviewed catalog audits the banner powers, so options resolve " +
      "for a game whose banner is on that face") {
    assert(PowerRuntime.options(catalog, base, PowerFixture.actor,
      ActionKind.Travel).isRight)
    assert(PowerRuntime.options(catalog, holdingFlame(base), PowerFixture.actor,
      ActionKind.Travel).isRight)
  }

  test("banner faces are not catalogued: no printed entry names their powers, " +
      "so no catalog flag needs pinning") {
    Vector("banner.darkest-secret.wandering-flame.move",
      "banner.darkest-secret.wandering-flame.place",
      "banner.peoples-favor.mob").foreach(id => assertEquals(
      CatalogResolution.printed(catalog, PowerId(id)), None, id))
  }
}
```

Create `src/test/scala/oathdigital/application/BannerFaceProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.banner.{BannerFixture, WanderingFlameMove, WanderingFlamePlace}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}

/** The banner faces' phase powers reach the client: they are projected with a
  * name and a text, their `usePower` control is legal for the holder only,
  * and the client's source (a banner) binds back to the engine's source.
  */
class BannerFaceProjectionSuite extends munit.FunSuite {
  import PowerFixture.{actor, base, inPhase}
  import BannerFixture._

  private val projector = new GameProjector(catalog)
  private val move = WanderingFlameMove.id.value
  private val place = WanderingFlamePlace.id.value

  /** The holder has a faceup secret, and another site holds one. */
  private def holding: ReadyGame = inPhase(TargetsFixture.withSecrets(
    withSiteSecrets(holdingFlame(base), brokenPeaks, 1), actor, 1, 0), Phase.Act)

  test("the holder is shown both Wandering Flame powers, named, with a text") {
    val projected = projector.project("flame", LoadedGame(Ready(holding), 30L),
      actor)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name)), Vector(
      (move, "banner", "darkest-secret", "Wandering Flame: move"),
      (place, "banner", "darkest-secret", "Wandering Flame: place a secret")))
    assert(projected.phasePowers.forall(_.rulesText.trim.nonEmpty))
    assert(projected.legalControls.contains(s"usePower:$move:darkest-secret"))
    assert(projected.legalControls.contains(s"usePower:$place:darkest-secret"))
  }

  test("a power with nothing to do is not projected: Move needs a secret-" +
      "bearing site, and the place power needs a faceup secret") {
    def shown(ready: ReadyGame) = projector.project("flame",
      LoadedGame(Ready(ready), 30L), actor)
    val noSite = inPhase(TargetsFixture.withSecrets(
      withoutSiteSecrets(holdingFlame(base)), actor, 1, 0), Phase.Act)
    assertEquals(shown(noSite).phasePowers.map(_.powerId), Vector(place))
    val noSecret = TargetsFixture.withSecrets(holding, actor, 0, 2)
    assertEquals(shown(noSecret).phasePowers.map(_.powerId), Vector(move))
    val neither = inPhase(TargetsFixture.withSecrets(
      withoutSiteSecrets(holdingFlame(base)), actor, 0, 2), Phase.Act)
    assertEquals(shown(neither).phasePowers, Vector.empty)
    assert(!shown(neither).legalControls.exists(_.startsWith("usePower:")))
  }

  test("another player, and the Festival face, are shown neither") {
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    val theirs = projector.project("flame", LoadedGame(Ready(holding), 30L),
      other)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
    val festival = inPhase(withSiteSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), brokenPeaks, 1), Phase.Act)
    assertEquals(projector.project("flame", LoadedGame(Ready(festival), 30L),
      actor).phasePowers, Vector.empty)
  }

  test("the client's banner source binds to the engine's banner source") {
    assertEquals(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "darkest-secret"))),
      Right(GameCommand.UsePower(actor, WanderingFlameMove.id,
        DecisionOptionRef.Banner(Banner.DarkestSecret))))
    assert(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "no-such-banner"))).isLeft)
  }
}
```


- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.* oathdigital.application.BannerFaceProjectionSuite"`

Expected: FAIL at compile time with `not found: value WanderingFlameMove` (and `WanderingFlamePlace`).


- [ ] **Step 3: Write the two powers and register them**

The move power reads the candidate sites once in `usable`, in the `Branch` that asks and in the effect. Nothing runs between the question and the answer, so all three agree. The place power guards on the board's faceup secrets and is otherwise one `Move`. Neither carries a face or holder check, because the source index lists them only on the Wandering Flame face and access keeps only the holder.

Create `src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlameMove.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.action.{PaidAction, PawnMoves}
import oathdigital.model._

/** Wandering Flame, move (the Banner of the Darkest Secret, Wandering Flame
  * face), ACTION: place your pawn at any other site with a secret on the site
  * itself. It costs nothing and has no once-per-turn limit.
  *
  * The engine finds the banner as the source, and only for its holder while
  * the Wandering Flame face is up (`RuleSourceIndex` lists the power only
  * then), so nothing here checks either. The pawn goes by a plain `Move`, not
  * Travel, so no Travel window runs. A secret on a card at a site does not
  * count. The decision is a live `Branch`, asked only when more than one site
  * qualifies. Nothing runs before it, so the resume derives the same choice.
  * The power is usable only when a site qualifies.
  */
case object WanderingFlameMove extends PaidAction(
    "banner.darkest-secret.wandering-flame.move", Cost.free) {
  val decisionId: String = "power.wandering-flame.site"

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = destinations(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => move(state, player, pending)))))

  /** The other sites, in map order, with a secret on the site itself. */
  private def destinations(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    PawnMoves.pawnSite(ready, player).toOption.toVector.flatMap(here =>
      PawnMoves.sitesOtherThan(ready, here).filter(site =>
        ready.game.current.map.sites.get(site).exists(_.tokens.secrets > 0)))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    destinations(ready, player) match {
      case sites if sites.size > 1 => Vector(PawnMoves.siteChoice(decisionId,
        player, sites, "Wandering Flame: choose the site to move your pawn to"))
      case _ => Vector.empty
    }

  private def move(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    destinations(ready, player) match {
      case Vector() => Right(Vector.empty)
      case Vector(only) => PawnMoves.relocate(ready, player, only)
      case sites => PawnMoves.chosenSite(pending, decisionId).flatMap(site =>
        if (sites.contains(site)) PawnMoves.relocate(ready, player, site)
        else Left(OathViolation.InvalidEventOrder(
          s"${site.value} holds no secret of its own")))
    }
}
```

Create `src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlace.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.action.{PaidAction, PawnMoves}
import oathdigital.model._

/** Wandering Flame, place a secret (the Banner of the Darkest Secret,
  * Wandering Flame face), ACTION: move one secret from your board onto the
  * site your pawn is at. It costs nothing and has no once-per-turn limit.
  *
  * A secret placed on a site rests faceup, so only a faceup secret can go. The
  * power is usable only while the holder has one (a facedown secret is never
  * flipped or moved). The engine finds the banner as the source, for its holder
  * and on the Wandering Flame face only.
  */
case object WanderingFlamePlace extends PaidAction(
    "banner.darkest-secret.wandering-flame.place", Cost.free) {
  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = faceUpSecrets(ready, player) > 0

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(BuildOps((state, _) => place(state, player)))

  private def faceUpSecrets(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.faceUpSecrets)

  private def place(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PawnMoves.pawnSite(ready, player).map { here =>
      if (faceUpSecrets(ready, player) == 0) Vector.empty
      else Vector(Move(Piece.Secrets(1),
        PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.Site(here))))
    }
}
```

`BannerFacePowers` is the one registration object. It also holds the names and texts the client is shown, because a banner face has no catalog entry.

Create `src/main/scala/oathdigital/gameplay/powers/banner/BannerFacePowers.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model.PowerId

/** The powers printed on the banner faces, registered through this one object
  * by [[oathdigital.gameplay.powers.PhasePowerCatalog]], like
  * [[oathdigital.gameplay.powers.action.MovementPowers]] and the other groups.
  *
  * A banner face has no catalog entry, so the name and text a player sees for
  * each of its phase powers are declared here.
  */
object BannerFacePowers {
  val phasePowers: Vector[PhasePower] =
    Vector[PhasePower](WanderingFlameMove, WanderingFlamePlace)

  private val texts: Vector[(PowerId, String, String)] = Vector(
    (WanderingFlameMove.id, "Wandering Flame: move",
      "ACTION: Place your pawn at any other site with a secret on it."),
    (WanderingFlamePlace.id, "Wandering Flame: place a secret",
      "ACTION: Move a secret from your board to the site your pawn is at."))

  /** The name and the text of a banner face's phase power. */
  def printed(id: PowerId): Option[(String, String)] =
    texts.collectFirst { case (`id`, name, text) => (name, text) }
}
```

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.rest.SilverTongue
```

with:

```scala
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.rest.SilverTongue
```

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, replace:

```scala
      MovementPowers.forCatalog(catalog))
```

with:

```scala
      MovementPowers.forCatalog(catalog) ++
      BannerFacePowers.phasePowers)
```


- [ ] **Step 4: Run the tests: the powers exist but the engine cannot find them yet**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.* oathdigital.application.BannerFaceProjectionSuite"`

Expected: it compiles, and 13 of the 21 tests FAIL (8 pass already, the ones that only check registration, refusals and the audit). `RuleSourceIndex` lists no power on the Wandering Flame face, so `PhasePowerProcedure` finds no source: a use is refused with `is not an accessible source of banner.darkest-secret.wandering-flame.move` (the tests that expect it to work fail on `None.get`), the powers are never usable, the projection is empty, and the index suite sees an empty list.


- [ ] **Step 5: List the face's powers, audit their ids and name them for the client**

The index lists the two ids on the Wandering Flame face. The audit line is what keeps every reviewed-catalog resolution working while the face is up (`BannerFaceSourcesSuite` fails without it). The projector's new case turns the names and texts from `BannerFacePowers` into the `CatalogPower` the projection expects. The text is built here, in the application layer, because no `gameplay` source may name a catalog rules text.

In `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`, replace:

```scala
          case DarkestSecretFace.WanderingFlame => Vector.empty
```

with:

```scala
          case DarkestSecretFace.WanderingFlame => ids(Vector(
            "banner.darkest-secret.wandering-flame.move",
            "banner.darkest-secret.wandering-flame.place"))
```

In `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala`, replace:

```scala
    PowerId("banner.darkest-secret.festival"),
```

with:

```scala
    PowerId("banner.darkest-secret.festival"),
    PowerId("banner.darkest-secret.wandering-flame.move"),
    PowerId("banner.darkest-secret.wandering-flame.place"),
```

In `src/main/scala/oathdigital/application/PhasePowerProjector.scala`, replace:

```scala
import oathdigital.gameplay.powers.PhasePowerCatalog
```

with:

```scala
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.powers.banner.BannerFacePowers
```

In `src/main/scala/oathdigital/application/PhasePowerProjector.scala`, replace:

```scala
    case _ => None // Banner faces get their labels with the slice that uses them.
```

with:

```scala
    case PowerSourceRef.Banner(_) => BannerFacePowers.printed(power).map {
      case (name, text) =>
        name -> oathdigital.catalog.CatalogPower(power, persistent = false, text)
    }
    case _ => None
```


- [ ] **Step 6: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.* oathdigital.application.BannerFaceProjectionSuite"`

Expected: PASS, 21 tests (7 in `WanderingFlameMoveSuite`, 7 in `WanderingFlamePlaceSuite`, 3 in `BannerFaceSourcesSuite`, 4 in `BannerFaceProjectionSuite`).


- [ ] **Step 7: Run the whole suite and the architecture check**

Run: `./sbtw test`

Expected: PASS, and the output ends with an `[info] Passed: Total` line (1516 tests on `main` before this task, 1537 after). Check the line is there: a run that only compiles has not run the tests.

Run: `python3 scripts/check-architecture.py`

Expected: `architecture check passed`.


- [ ] **Step 8: Record the sub-slice in the docs**

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a to 3d, so slice 3 is complete.
```

with:

```markdown
> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a to 3d, so slice 3 is complete.

> Slice 4 (banner faces) is planned in two sub-slices, 4a Wandering Flame and 4b Mob: see its [plan](../plans/2026-09-20-powers-slice-4-banner-faces.md). Implemented so far: 4a.
```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |
```

with:

```markdown
| 4. Banner faces (4a implemented) | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules`; the source index lists the faces' powers |
```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
3d Warning Signals, Sticky Fire and Gleaming Armor. See its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md).
```

with:

```markdown
3d Warning Signals, Sticky Fire and Gleaming Armor. See its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md).

Slice 4 is planned in two sub-slices: 4a Wandering Flame, which also lets the source index and the projector see banner faces, and 4b Mob. See its [plan](../plans/2026-09-20-powers-slice-4-banner-faces.md).
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
| Darkest Secret: Wandering Flame, move | An Act `PhasePower` sourced from the banner, usable by the holder only, no cost, unlimited. Place your pawn at any other site with a secret on the site itself (`SiteState.tokens.secrets`), not on its cards. A plain `Move`, not Travel. |
```

with:

```markdown
| Darkest Secret: Wandering Flame, move | An Act `PhasePower` sourced from the banner, usable by the holder only, no cost, unlimited. Place your pawn at any other site with a secret on the site itself (`SiteState.tokens.secrets`), not on its cards. A plain `Move`, not Travel. Implemented (slice 4a). |
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
| Darkest Secret: Wandering Flame, place a secret | A second `PhasePower` with its own id, holder only, no cost, unlimited. Move 1 secret from your board onto the site your pawn is at. No secret is a no-op. |
```

with:

```markdown
| Darkest Secret: Wandering Flame, place a secret | A second `PhasePower` with its own id, holder only, no cost, unlimited. Move 1 secret from your board onto the site your pawn is at. Not usable without a faceup secret on your board: the button is hidden, and a facedown secret is never flipped or moved (product ruling). Implemented (slice 4a). |
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
## Deferred and parked
```

with:

```markdown
### Slice 4 implementation notes

- **4a:** the two Wandering Flame powers are `PaidAction`s with no cost, in `gameplay/powers/banner`, registered through `BannerFacePowers` in `PhasePowerCatalog`. `RuleSourceIndex` lists them on the Wandering Flame face and the reviewed catalog audits their ids, so the engine finds the banner as a source for its holder only while that face is up. `PhasePowerProjector` names them from `BannerFacePowers`, because a banner face has no catalog entry. Move is usable only when another site holds a secret on the site itself. It asks which site only when several qualify, and relocates by a plain `Move`, so no Travel window runs. Place moves one faceup secret from the board onto the pawn's site and is usable only while the holder has a faceup secret, because a secret on a site rests faceup and a facedown secret is never flipped or moved. Neither power is limited, because Act powers record no use. A secret placed on a site can later be taken with Take Wealth, like any resource on a site. The suites clear the secrets the first game starts on some sites.

## Deferred and parked
```


- [ ] **Step 9: Check the docs' links**

Run: `python3 scripts/check-markdown-links.py`

Expected: `Markdown link check passed`.


- [ ] **Step 10: Commit**

```bash
git add src/main/scala src/test/scala docs
git commit -m "feat: implement Wandering Flame

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


---

## Sub-slice 4b: Mob

### Task 2: Mob

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/banner/BannerFacePowers.scala`, `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`, `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala`
- Docs: `docs/superpowers/specs/2026-09-20-powers-design.md`, `docs/superpowers/specs/2026-09-20-powers-rulings.md`

**Interfaces:**
- Consumes: E6's `PlacementRules.withSiteDiscardFirst` and `CardPlayProcedure.PlacementTree.adjust`; `PowerCtx.activePlayer` and `PowerCtx.operation`; Task 1's `BannerFacePowers`, `BannerFixture` (`holdingFavor`, `withCardTokens`) and the source index and audit lines.
- Produces: `object PeoplesFavorMob extends ContributingPower` (id `banner.peoples-favor.mob`, automatic); `BannerFacePowers.contributions: Vector[ContributingPower]`.

**Rule built (from the rulings).** Holder only. When you play a card to a site you may first discard a card from the site's card list, through `PlacementRules.siteDiscardFirst`. At a full site, where a play is normally impossible, it makes the play legal. The card goes through the standard discard: facedown to the next region's discard pile, favor to the suit bank, secrets to the player facedown. `DiscardRestrictions` decide what is discardable, so an intact edifice is refused as locked.

`PlacementRules` already does the work: with `siteDiscardFirst` the planner offers the discard at any capacity (optional with room, required without), lifts the rule that a full site takes only a card matching its Homeland, and offers only what the generic discard rules allow. The power only turns it on, for the right player, on the right face.


- [ ] **Step 1: Write the tests**

The suite drives real Searches and a facedown-adviser play through the production walker powers, so it also proves the registration. It covers the holder's question and declining, the standard discard, a full site, every player who is not asked, the intact edifice, the facedown-adviser origin, Silver Tongue and Welcoming Party.

Create `src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._

class PeoplesFavorMobSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import SearchFixture.{denizensOf, keep, place, rules, start}

  private val noDiscard = CardPlayProcedure.noReplacement.ref
  private val played = denizensOf(Suit.Beast).head
  private val drawn = Vector[WorldCardId](played) ++
    denizensOf(Suit.Arcane).take(2)
  private val capacity = catalog.sites.find(_.id == home(base)).get.capacity

  /** A Search of `drawn` at a pawn site holding `site`, the Banner of the
    * People's Favor held as `favor` arranges it (by the actor, on the Mob
    * face, unless a test says otherwise). `played` is the card that is kept.
    */
  private def staged(site: Vector[DenizenId],
      favor: ReadyGame => ReadyGame = holdingFavor(_)): ReadyGame =
    favor(site.foldLeft(SearchFixture.staged(drawn))(atHome))

  private def decisionId(kind: String) = s"cardplay.$kind.denizen.${played.value}"

  /** Searches, keeps the played card and chooses to play it to the site. */
  private def toSite(ready: ReadyGame): OathTransition = (for {
    started <- start(ready)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  } yield placed.copy(events = started.events ++ kept.events ++ placed.events))
    .fold(error => throw new AssertionError(error.toString), identity)

  private def asksToDiscard(transition: OathTransition): Boolean =
    transition.continue == OathContinue.AwaitingSearchDecision(actor,
      DecisionId(decisionId("replace")))

  private def discardAnswer(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    actor, decisionId("replace"), DecisionAnswer.ChooseOneAnswer(chosen))

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    ready.game.current.map.sites(home(ready)).denizens.map(_.id)

  test("Mob is a registered persistent rule, so it is automatic") {
    val registered = WalkerPowerCatalog.default(catalog).powers
      .find(_.id == PeoplesFavorMob.id)
    assertEquals(registered.map(_.resolution), Some(PowerResolution.Automatic))
    assertEquals(PeoplesFavorMob.id.value, "banner.peoples-favor.mob")
  }

  test("the source index lists Mob on the Mob face and the Grand Council's " +
      "own power on the other") {
    def favor(ready: ReadyGame) = oathdigital.gameplay.RuleSourceIndex
      .enumerate(catalog, ready).find(_.source ==
        RuleSourceRef.Banner(Banner.PeoplesFavor.key)).get
    assertEquals(favor(base).powerIds, Vector(PeoplesFavorMob.id))
    assertEquals(favor(holdingFavor(base, PeoplesFavorFace.GrandCouncil))
      .handlerIds, Vector("banner.peoples-favor.grand-council"))
  }

  test("its holder is asked whether to discard a site card before a play " +
      "to a site with room, and may decline") {
    val hearth = denizensOf(Suit.Hearth)
    val (kept, other) = (hearth(0), hearth(1))
    val ready = staged(Vector(kept, other))
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val asked = toSite(ready)
    assert(asksToDiscard(asked))
    val done = discardAnswer(asked, noDiscard).toOption.get
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](kept, other, played))
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
  }

  test("an empty site has nothing to discard, so the holder is asked nothing") {
    val done = toSite(staged(Vector.empty))
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](played))
  }

  test("choosing a card discards it by the standard discard: facedown to the " +
      "next region's pile, its favor to the bank, its secret to the holder") {
    val kept = denizensOf(Suit.Hearth).head
    val ready = withCardTokens(staged(Vector(kept)), home(base), kept,
      Tokens(1, 1))
    val asked = toSite(ready)
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(kept)).toOption.get
    val end = SearchFixture.after(done)
    val region = CardPlay.nextRegion(
      ready.game.current.map.regionOf(home(base)).get)
    assertEquals(siteCards(end), Vector[CardId](played))
    assertEquals(end.game.current.commonCards.discard(region).last, kept)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0),
      ready.banks.favor.getOrElse(Suit.Hearth, 0) + 1)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
  }

  test("a full site with no matching homeland takes the play only with a " +
      "discard, and only for the holder") {
    val fillers = (denizensOf(Suit.Hearth) ++ denizensOf(Suit.Order))
      .take(capacity)
    assertEquals(fillers.size, capacity)
    val full = staged(fillers)
    assert(place(keep(start(full).toOption.get, played).toOption.get, played,
      "site").isRight)
    val asked = toSite(full)
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, noDiscard).isLeft, "a discard is required")
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(fillers.head))
      .toOption.get
    assertEquals(siteCards(SearchFixture.after(done)).size, capacity)
    assert(siteCards(SearchFixture.after(done)).contains(played))
    val without = staged(fillers, identity)
    val attempt = for {
      started <- start(without)
      kept <- keep(started, played)
      placed <- place(kept, played, "site")
    } yield placed
    assert(attempt.isLeft, "a full site accepts no play without Mob")
  }

  test("nobody else is asked: not another holder, not the Grand Council face, " +
      "not the other banner, not an unheld banner") {
    val kept = denizensOf(Suit.Hearth).head
    val unheld: Vector[ReadyGame => ReadyGame] = Vector(
      holdingFavor(_, holder = Some(p1)),
      holdingFavor(_, PeoplesFavorFace.GrandCouncil),
      holdingFavor(_, holder = None),
      holdingFlame(_))
    unheld.zipWithIndex.foreach { case (favor, index) =>
      val ready = staged(Vector(kept), favor)
      val done = toSite(ready)
      assert(!asksToDiscard(done), s"case $index")
      assertEquals(siteCards(SearchFixture.after(done)),
        Vector[CardId](kept, played), s"case $index")
    }
  }

  test("an intact edifice is never offered for the discard: it is locked") {
    val kept = denizensOf(Suit.Hearth).head
    val hall = EdificeId("E16")
    val (built, who, site) = PlacementFixture.staged(played,
      Vector(PlacementFixture.denizen(kept),
        EdificeState(hall, EdificeSide.Intact, Tokens.empty)))
    val ready = PlacementFixture.ruledByActor(built, site)
    val choice = CardPlay.legalChoices(catalog, holdingFavor(ready), who,
      played, CardPlay.Origin.TemporaryHand,
      oathdigital.gameplay.actions.PlacementRules.default.withSiteDiscardFirst)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get
    assertEquals(choice.replacements, Vector[CardId](kept))
  }

  test("it applies to a facedown adviser played to a site as well") {
    val kept = denizensOf(Suit.Hearth).head
    val ready = asAdviser(staged(Vector(kept)), played, Orientation.FaceDown)
    val started = rules.startWalker(OathState.Ready(ready),
      ActionRef.PlayFacedownAdviser, actor, Vector.empty,
      Vector(DecisionOptionRef.Denizen(played))).toOption.get
    val placed = place(started, played, "site").toOption.get
    assert(asksToDiscard(placed))
  }

  test("it composes with Silver Tongue's limit of two advisers, both from " +
      "the production walker powers") {
    val tongue = DenizenId("92")
    val hearth = denizensOf(Suit.Hearth)
    val (kept, adviser) = (hearth(0), hearth(1))
    val (built, who, _) = PlacementFixture.staged(played,
      Vector(PlacementFixture.denizen(kept)))
    val bare = TargetsFixture.withoutAdvisers(built, who)
    val ready = holdingFavor(TargetsFixture.giveAdviser(
      TargetsFixture.giveAdviser(bare, who, tongue, Orientation.FaceUp), who,
      adviser, Orientation.FaceDown))
    val powers = WalkerPowerCatalog.default(catalog)
    val tree = PlacementFixture.build(ready, who, played)
    val parked = PlacementFixture.park(ready, tree, powers)
    // Two advisers held under Silver Tongue: a faceup adviser needs a discard,
    // and the faceup Silver Tongue is a locked card, so only the other goes.
    val faceup = PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(played, "place"),
      DecisionOptionRef.Button("adviser-faceup"), who)
      .asInstanceOf[WalkerOutcome.Parked]
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(adviser)))
    // The site still offers the optional discard, under the same walk.
    val site = PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(played, "place"),
      DecisionOptionRef.Button("site"), who).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      noDiscard)
    assert(powers.powers.exists(_.id == SilverTongue.id))
  }

  test("it leaves a selected card's power alone: Welcoming Party still pays " +
      "once, and the selected card itself cannot be the discard") {
    val party = DenizenId("50")
    val kept = denizensOf(Suit.Hearth).head
    val ready = atHome(staged(Vector(kept)), party)
    val selected = Vector(oathdigital.gameplay.powers.cardplay.WelcomingParty.id)
    val started = start(ready, selected).toOption.get
    val chosen = keep(started, played).toOption.get
    val asked = place(chosen, played, "site").toOption.get
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, DecisionOptionRef.Denizen(party)).isLeft,
      "a card that prints a selected power cannot be discarded")
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(kept)).toOption.get
    val end = SearchFixture.after(done)
    val hearth = (s: ReadyGame) => s.banks.favor.getOrElse(Suit.Hearth, 0)
    // The play gains 1 favor from the played card's own bank, Welcoming Party
    // gains 1 from the Hearth bank.
    assertEquals(player(end).board.favor, player(ready).board.favor + 2)
    assertEquals(hearth(end), hearth(ready) - 1)
    assertEquals(siteCards(end), Vector[CardId](party, played))
  }
}
```


- [ ] **Step 2: Run the test to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite"`

Expected: FAIL at compile time with `not found: value PeoplesFavorMob`.


- [ ] **Step 3: Write Mob and register it**

The `Transform` matches the `PlacementTree` it is handed and adjusts its rules, so it composes with any other contributor, as Silver Tongue's does. `applicable` reads the banner from state: the player whose procedure runs must hold the People's Favor banner with the Mob face up. It never builds a discard, so `DiscardRestrictionsCoverageSuite` has nothing to add.

Create `src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.model._

/** Mob (the Banner of the People's Favor, Mob face): when you play a card to a
  * site, you may first discard a card from the site's card list.
  *
  * A persistent rule of the banner's holder, automatic. It changes only the
  * rules the play is planned under, through the `SearchPlayAdviser` window, so
  * it composes with Silver Tongue's limit in either order. The card-play
  * planner offers the discard, at any capacity, and the generic discard rules
  * decide which cards may go (an intact edifice and a locked card may not).
  * A full site that would refuse the play accepts it with a discard.
  */
object PeoplesFavorMob extends ContributingPower {
  val id: PowerId = PowerId("banner.peoples-favor.mob")
  def source: RuleSourceRef = RuleSourceRef.Banner(Banner.PeoplesFavor.key)

  /** The player plays a card while holding the banner, Mob face up. */
  override def applicable(ctx: PowerCtx): Boolean = {
    val favor = ctx.state.game.current.banners.peoplesFavor
    favor.active == PeoplesFavorFace.Mob && favor.holder.contains(ctx.activePlayer)
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.withSiteDiscardFirst)
        case _ => children
      })))
}
```

In `src/main/scala/oathdigital/gameplay/powers/banner/BannerFacePowers.scala`, replace:

```scala
import oathdigital.gameplay.powerresolver.PhasePower
```

with:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, PhasePower}
```

In `src/main/scala/oathdigital/gameplay/powers/banner/BannerFacePowers.scala`, replace:

```scala
  private val texts: Vector[(PowerId, String, String)] = Vector(
```

with:

```scala
  /** The banner faces' rules that change how the engine plans an action. */
  val contributions: Vector[ContributingPower] =
    Vector[ContributingPower](PeoplesFavorMob)

  private val texts: Vector[(PowerId, String, String)] = Vector(
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRules, SimplePlans, VowOfPeaceContribution}
```

with:

```scala
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRules, SimplePlans, VowOfPeaceContribution}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
```

with:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
      BannerFacePowers.contributions ++
```


- [ ] **Step 4: Run the test: the behaviour is there, the source index is not**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite"`

Expected: it compiles and the suite mostly PASSES, because the power is registered and needs no source. It FAILS one test, "the source index lists Mob on the Mob face and the Grand Council's own power on the other", because the Mob face still lists no power.


- [ ] **Step 5: List Mob on its face and audit its id**

In `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`, replace:

```scala
          case PeoplesFavorFace.Mob => Vector.empty
```

with:

```scala
          case PeoplesFavorFace.Mob => ids(Vector("banner.peoples-favor.mob"))
```

In `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala`, replace:

```scala
    PowerId("banner.peoples-favor.grand-council"),
```

with:

```scala
    PowerId("banner.peoples-favor.grand-council"),
    PowerId("banner.peoples-favor.mob"),
```


- [ ] **Step 6: Run the test to see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.*"`

Expected: PASS, 11 tests in `PeoplesFavorMobSuite` and every other banner suite.


- [ ] **Step 7: Run the whole suite and the architecture check**

Run: `./sbtw test`

Expected: PASS, and the output ends with an `[info] Passed: Total` line (1537 tests after Task 1, 1548 after this task). `BackendArchitectureSuite` scans the new object's name (`PeoplesFavorMob`, fifteen characters) in the engine sources and finds nothing.

Run: `python3 scripts/check-architecture.py`

Expected: `architecture check passed`.


- [ ] **Step 8: Record the sub-slice in the docs**

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
> Slice 4 (banner faces) is planned in two sub-slices, 4a Wandering Flame and 4b Mob: see its [plan](../plans/2026-09-20-powers-slice-4-banner-faces.md). Implemented so far: 4a.
```

with:

```markdown
> Slice 4 (banner faces) is planned in two sub-slices, 4a Wandering Flame and 4b Mob: see its [plan](../plans/2026-09-20-powers-slice-4-banner-faces.md). Implemented so far: 4a and 4b, so slice 4 is complete.
```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
| 4. Banner faces (4a implemented) | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules`; the source index lists the faces' powers |
```

with:

```markdown
| 4. Banner faces (implemented) | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules`; the source index lists the faces' powers |
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
| People's Favor: Mob | Holder only. When you play a card to a site you may first discard a card from the site's card list. It is enabled through `PlacementRules.siteDiscardFirst`. At a full site, where a play is normally impossible, it makes the play legal. The card goes through the standard discard: facedown to the next region's discard, favor to the suit bank, secrets to you facedown. `DiscardRestrictions` decide what is discardable, so an intact edifice is refused as locked. |
```

with:

```markdown
| People's Favor: Mob | Holder only. When you play a card to a site you may first discard a card from the site's card list. It is enabled through `PlacementRules.siteDiscardFirst`. At a full site, where a play is normally impossible, it makes the play legal. The card goes through the standard discard: facedown to the next region's discard, favor to the suit bank, secrets to you facedown. `DiscardRestrictions` decide what is discardable, so an intact edifice is refused as locked. Implemented (slice 4b). |
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
- **4a:** the two Wandering Flame powers are `PaidAction`s with no cost, in `gameplay/powers/banner`, registered through `BannerFacePowers` in `PhasePowerCatalog`. `RuleSourceIndex` lists them on the Wandering Flame face and the reviewed catalog audits their ids, so the engine finds the banner as a source for its holder only while that face is up. `PhasePowerProjector` names them from `BannerFacePowers`, because a banner face has no catalog entry. Move is usable only when another site holds a secret on the site itself. It asks which site only when several qualify, and relocates by a plain `Move`, so no Travel window runs. Place moves one faceup secret from the board onto the pawn's site and is usable only while the holder has a faceup secret, because a secret on a site rests faceup and a facedown secret is never flipped or moved. Neither power is limited, because Act powers record no use. A secret placed on a site can later be taken with Take Wealth, like any resource on a site. The suites clear the secrets the first game starts on some sites.
```

with:

```markdown
- **4a:** the two Wandering Flame powers are `PaidAction`s with no cost, in `gameplay/powers/banner`, registered through `BannerFacePowers` in `PhasePowerCatalog`. `RuleSourceIndex` lists them on the Wandering Flame face and the reviewed catalog audits their ids, so the engine finds the banner as a source for its holder only while that face is up. `PhasePowerProjector` names them from `BannerFacePowers`, because a banner face has no catalog entry. Move is usable only when another site holds a secret on the site itself. It asks which site only when several qualify, and relocates by a plain `Move`, so no Travel window runs. Place moves one faceup secret from the board onto the pawn's site and is usable only while the holder has a faceup secret, because a secret on a site rests faceup and a facedown secret is never flipped or moved. Neither power is limited, because Act powers record no use. A secret placed on a site can later be taken with Take Wealth, like any resource on a site. The suites clear the secrets the first game starts on some sites.
- **4b:** Mob is `PeoplesFavorMob`, a contribution registered through `BannerFacePowers` in `WalkerPowerCatalog`. It is a `Transform` at `SearchPlayAdviser` that sets `PlacementRules.siteDiscardFirst`, applicable while the playing player holds the People's Favor banner on the Mob face, and `RuleSourceIndex` lists it on that face. It reaches a Search play and a facedown-adviser play alike and composes with Silver Tongue's limit. With room the player is offered "Discard nothing" beside the cards. At a full site the play is legal only with a discard. Nothing is asked at an empty site, or where no card may be discarded. The generic discard rules decide the cards: a locked card, an intact edifice and a card that prints a selected power are never offered, and a ruined edifice goes back to the edifice deck. No engine change was needed.
```


- [ ] **Step 9: Check the docs' links**

Run: `python3 scripts/check-markdown-links.py`

Expected: `Markdown link check passed`.


- [ ] **Step 10: Commit**

```bash
git add src/main/scala src/test/scala docs
git commit -m "feat: implement Mob

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


---

## Open questions for the product owner

Each has a recommended default. The plan builds the default, and each is a small change if the answer differs.

1. **Wandering Flame, move, when no other site holds a secret on the site itself.** Built: the power is not usable, so the client shows no button and a command that names it is refused, because there is nothing to choose. The general rule "an effect with nothing to do is a no-op" points the other way. The product owner's answer to question 2 applies the same reading to the place power, so both powers are hidden when they have nothing to do. Recommended: keep as built. If a usable no-op is wanted, drop the `usable` override in `WanderingFlameMove`.
2. **Answered: Wandering Flame, place a secret, with no faceup secret.** The product owner: hide the button. The power is not usable when the holder has no faceup secret on their board, and the projector shows no button. The rulings row said "No secret is a no-op", and the row now says not usable. The move power stays not usable when no other site holds a secret on the site itself.
3. **Answered: which secret the place power moves.** The product owner: "Banner should only have faceup secrets". Read as: only a faceup secret is moved onto the site, a facedown secret is never flipped or moved, and site secrets are always faceup. A player with only facedown secrets cannot use the power (question 2). Another reading is possible, that the Banner of the Darkest Secret itself counts only faceup secrets, which is CR p. 26 and how the engine already counts it (`BannerRules.playerResources`). It does not conflict with this reading, and no code follows from it in this slice.
4. **A single candidate site.** Built: with exactly one other site holding a secret, the pawn goes there and no question is asked (Brass Horse's precedent). Whistle and Magic Carpet always ask. Recommended: as built, one click fewer. The alternative is to always ask, so the player can see where the flame leads.
5. **Which secrets make a site a destination.** As ruled, a secret on the site itself. A secret resting on a card at the site, on a relic, or on a banner does not count, and the pawn's own site is never a destination even when it holds one (the ruling says "any other site"). Recommended: as ruled. Example: the holder places a secret on their own site with the place power, which makes the site a destination for other holders of the banner (there is one banner) but never for the holder.
6. **Take Wealth and a secret placed on a site.** Take Wealth takes a favor or secret from the site's tokens whatever put it there, so a secret the holder places is available to anyone at that site who could Take Wealth there (no enemy pawn present), once per turn each. Recommended: accept, since the engine treats site tokens uniformly and the printed Wandering Flame text gives no exception. The alternative is a separate token that Take Wealth ignores, which needs a new resource location.
7. **Mob at a full site.** Built: with Mob, a play to a full site is legal and requires a discard (there is no "Discard nothing" option). The discard may be any card the generic rules allow, so a full site whose cards are all locked or an intact edifice still refuses the play. Recommended: as ruled. Example: a full non-matching site with three denizens, one holding a favor: the holder picks one, its favor returns to the suit bank, and the played card takes the space.
8. **Which cards Mob may discard.** Any card of the site's card list that the generic discard rules allow: a denizen (with its favor returned to the suit bank and its secrets to the holder, facedown), or a ruined edifice, which goes back to the edifice deck. Never an intact edifice, a locked card, or a card that prints a power selected for the running action. Recommended: as ruled. The ruling says "a card from the site's card list"; the rest follows from the generic rules recorded in the slice 2a notes.
9. **Mob is asked at every play to a site with room.** The question is offered whenever a play to a site is chosen and some card there could go, even when discarding would be pointless. It is skipped at an empty site or when nothing can be discarded. Recommended: ask. The player answers "Discard nothing" in one click, and a rule that skipped the question when it looked pointless would have to define "pointless".
10. **Mob applies to a facedown adviser played to a site.** Built: any play to a site by the holder, whether the card came from a Search or from the holder's facedown advisers. Recommended: yes, since the ruling says "when you play a card to a site".
11. **Mob and Silver Tongue.** They compose: Mob's site discard and Silver Tongue's limit of two advisers apply together, in either order. The faceup Silver Tongue is a locked card, so it cannot be discarded to make room for a third adviser. Recommended: as built. This is not a question of Mob, only a consequence of the generic rules.
12. **Mob and Welcoming Party or Wild Cry.** Their triggers fire on the play, as before. The discarded site card is not a played card, so it triggers nothing. A card that prints a selected power (the Welcoming Party the player selected) cannot be the discard. Recommended: as built.
13. **The names and texts the client shows.** Built: "Wandering Flame: move" and "Wandering Flame: place a secret", with a text paraphrased from the rulings, because a banner face has no catalog entry. Recommended: the product owner supplies the printed text (New Foundations p. 12), and it replaces the two strings in `BannerFacePowers`. Nothing else changes.
14. **Deferred, unchanged.** The card-slot redesign of card play, in which Mob would become a contribution to slot options (ROADMAP, Phase 3); the Grand Council and Festival faces, which stay listed as synthetic ids with no behaviour; Empire rulers. Recommended: leave.

## Risks to check while executing

- **The source index reaches every consumer of banner sources.** Listing power ids on the Mob and Wandering Flame faces changes what `RuleSourceIndex.enumerate` returns for the first game's default banners. The whole suite passes with the change, and `BannerFaceSourcesSuite` asserts that the reviewed catalog resolves options for a game on the Wandering Flame face. If a later suite asserts that a banner has no handlers, it needs the new ids.
- **The audit line is what protects every action.** Without the ids in `syntheticIds`, `PowerRuntime.options` fails with `UnsupportedRuleCatalog` at every modifier selection, in any game whose banner shows one of these faces. `BannerFaceSourcesSuite` pins it, and its second test was checked to fail without the line.
- **A costed banner power is refused.** `PhasePowerProcedure.payable` rejects a non-free cost from a banner source on purpose, so a later banner power that costs something needs E3's cost placement extended first. Neither Wandering Flame power costs anything.
- **Mob's `applicable` reads two facts of state.** It is asked for every play, so it must stay a pure function of the state it is handed. It reads the banner's face and holder and nothing else.
- **`PlacementBody.adjust` composes rules, it does not order them.** A future power that sets a rule Mob also sets (none exists) would need a rule of its own for which wins. Mob only turns a permission on.
- **The first game's staging.** Broken-peaks starts with two secrets on the site and fair-isle with three favor. A suite that counts site secrets clears them first, as `BannerFixture.withoutSiteSecrets` does.
- **File sizes.** The new production files are under 60 lines each, `RuleSourceIndex.scala` stays under 200, and no production file nears the 800-line bound.
- **The projector shows a banner power under the banner's key.** The client's source is `banner`/`darkest-secret`. No frontend code changes and no frontend test covers it, so `BannerFaceProjectionSuite` is the check.


## Self-review

- **Spec coverage.** Wandering Flame's move and its place-a-secret power (Task 1) and Mob (Task 2) are the whole of the "Slice 4: banner faces" section of the rulings. E3's banner source and E6's `PlacementRules` are used and not changed. The design's "verify at plan time" items that this slice touches are settled: a secret on `Location.Site` is accepted (the place suite moves one and replays it), the shared bank's secrets are not used, `PlaceBannerResource` is a walker action that records no `usedPowers` (and so are Act phase powers), and no structural fingerprint changes (`BackendArchitectureSuite` still finds the handler inventory equal to the audited vocabulary). The parked and deferred lists are untouched.
- **Placeholders.** None. Every code step is a complete file or an exact replacement, and each was applied in a throwaway copy in the order below.
- **Validation.** Every file and replacement in Tasks 1 and 2 was applied, in this order, to a fresh copy of `main` with the tooling and `node_modules` linked, by a script that reads this document, then compiled and run. Task 1: Step 2 failed to compile on `WanderingFlameMove`; after Step 3, 13 of 21 tests failed; after Step 5 all 21 passed; `./sbtw test` printed `Passed: Total 1537` (1516 on `main`), and the architecture and link checks passed. Task 2: Step 2 failed to compile on `PeoplesFavorMob`; after Step 3, 1 of 11 failed; after Step 5 all 11 passed; `./sbtw test` printed `Passed: Total 1548`, and both checks passed. The audit line and the projector case were each removed once to confirm that a test fails without them.
- **Types.** `WanderingFlameMove.decisionId`, `WanderingFlameMove.id` and `WanderingFlamePlace.id` (Task 1) are read by every Task 1 suite. `BannerFacePowers.printed` (Task 1) is read by `PhasePowerProjector`, and `BannerFacePowers.contributions` (Task 2) by `WalkerPowerCatalog`. `BannerFixture.holdingFavor` and `withCardTokens` are created in Task 1 and first used by Task 2's suite. `PeoplesFavorMob.id` (Task 2) is the id the index and the audit list.
- **Docs.** Each task's docs step edits the design's status line and slicing row and the rulings' Slice 4 rows and implementation notes, with the exact strings as they stand after the earlier task, so the two tasks can be merged separately.
