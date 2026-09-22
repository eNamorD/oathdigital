# Chronicle setup slice 3: setup powers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the six parked batch-1 edifice faces (E02 Great Market/Bandit Market, E06 Great Forge/Broken Forge, E22 Proving Grounds/Empty Grounds) as `ContributingPower`s hooked into the walker's `SetupPawnPlaced`/`SetupEnd` windows, so every batch-1 edifice now works.

**Architecture:** Each face is a small `ContributingPower` that reads which site its edifice occupies (or which site the acting player just placed their pawn at), and — when applicable — appends a `BuildOps` node to the window's operation vector via a `Transform` contribution, following the codebase's established `Dazzle`/`MountainSitePower`/`OakenFortress` idioms exactly. Each power's `contributions` map also names `PowerWindow.WhenExplored` (new this slice) pointing at the *same* `Transform`, so the SETUP and future WHEN EXPLORED triggers share one implementation with no branching — nothing fires that window yet, so only the SETUP path is built or tested.

**Tech Stack:** Scala 2.13, munit, the existing procedure-walker power framework (`ContributingPower`/`Contribution`/`WalkerPowerCatalog`).

**Spec:** [docs/superpowers/specs/2026-09-21-chronicle-setup-design.md](../specs/2026-09-21-chronicle-setup-design.md), section "Setup powers" and "Slices" item 3.

## Global Constraints

- Every new power's `id` matches its catalog power id exactly: `edifice.e02.intact`, `edifice.e02.ruined`, `edifice.e06.intact`, `edifice.e06.ruined`, `edifice.e22.intact`, `edifice.e22.ruined` (already present in `docs/catalog/new-foundations-component-catalog.json`; no catalog data changes needed).
- None of these six powers override `resolution` — they stay `ContributingPower`'s trait default, `PowerResolution.Automatic`. **Do not** call `CatalogResolution.of` for them: the catalog marks all six `"persistent": false`, which `CatalogResolution.of` maps to `PowerResolution.PlayerSelected` (a player-toggled modifier). These are mandatory, unconditional SETUP effects, not modifiers — the same category as `MountainSitePower`/`Dazzle`, which also never override `resolution`.
- A power is inert (contributes nothing) whenever its edifice+side isn't actually on the board this game — a Homeland outside the first 8 Chronicle sites keeps its edifice in storage (spec, "The first-game generator"). Gate every power's `applicable` on the edifice actually being found at a site, never assume it is present.
- Follow the existing file layout: one new package, `oathdigital.gameplay.powers.setup`, holding all production code for this slice; tests mirror it at `src/test/scala/oathdigital/gameplay/powers/setup/`.

---

### Task 1: `WhenExplored` window, shared setup-power helpers, and a reusable walk driver

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (add `WhenExplored`)
- Create: `src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala`
- Create: `src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala`
- Modify: `src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala` (use the extracted driver)
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupportSuite.scala`

**Interfaces:**
- Produces: `PowerWindow.WhenExplored` (an `OtherWindow`, key `"explore.when-explored"`), for Tasks 2-4 to name in their `contributions` maps.
- Produces: `EdificeSetupSupport.siteOf(ready: ReadyGame, edifice: EdificeId, side: EdificeSide): Option[SiteId]` and `EdificeSetupSupport.pawnPlacementSite(ctx: PowerCtx): Option[SiteId]`, for Tasks 2-4 to call.
- Produces: `SetupWalkDriver.driveToCompletion(ready: ReadyGame, tree: Operation, powers: WalkerPowers): ReadyGame`, for Tasks 2-4's suites to call with the real `WalkerPowerCatalog.default(catalog)` instead of `WalkerPowers.empty`.

- [ ] **Step 1: Add the `WhenExplored` window**

Open `src/main/scala/oathdigital/model/PowerWindow.scala` and add a case object next to `SetupEnd` (around line 192):

```scala
  /** Where an explored site's effects resolve, once an explore procedure
    * exists to fire it (2026-09-21 Chronicle design, "Setup powers" --
    * out of scope until then). Slice 3's E02/E06/E22 powers already name
    * this window alongside their SETUP window so the same contribution
    * serves both without branching; nothing folds it yet.
    */
  case object WhenExplored extends OtherWindow { val key = "explore.when-explored" }
```

- [ ] **Step 2: Write the shared edifice/pawn-site helper**

Create `src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.model._

/** Shared reads for the slice-3 SETUP/WHEN-EXPLORED edifice powers (E02,
  * E06, E22).
  */
object EdificeSetupSupport {
  /** Where `edifice`'s `side` currently sits, if it is on the board at all
    * -- a Homeland outside the first 8 Chronicle sites keeps its edifice in
    * storage, so a game may not place it anywhere (design spec, "The
    * first-game generator").
    */
  def siteOf(ready: ReadyGame, edifice: EdificeId, side: EdificeSide)
      : Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: EdificeState => card.id == edifice && card.side == side
        case _ => false
      } => site
    }

  /** The site the acting player just chose for their pawn, read from the
    * decisions answered so far this walk. Only meaningful at
    * `PowerWindow.SetupPawnPlaced`; a future `WhenExplored` fire needs its
    * own read of the explored site, not built by this slice.
    */
  def pawnPlacementSite(ctx: PowerCtx): Option[SiteId] =
    ctx.answered.collectFirst {
      case Answered(decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Site(site)), _)
        if decisionId == SetupProcedure.pawnDecisionId(ctx.activePlayer) => site
    }
}
```

- [ ] **Step 3: Write the helper's test**

Create `src/test/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupportSuite.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.CardStaging
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

class EdificeSetupSupportSuite extends munit.FunSuite {
  private val edifice = EdificeId("E02")
  private val site = FirstGameSetupFixture.sites.head

  test("siteOf finds an edifice staged on a side, and misses the other side") {
    val staged = CardStaging.without(FirstGameSetupFixture.freshReady, edifice)
      .updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
        site, c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(edifice, EdificeSide.Ruined, Tokens.empty))))))
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Ruined),
      Some(site))
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Intact),
      None)
  }

  test("siteOf finds nothing when the edifice is not on the board") {
    val cleared = CardStaging.without(FirstGameSetupFixture.freshReady, edifice)
    assertEquals(EdificeSetupSupport.siteOf(cleared, edifice, EdificeSide.Ruined),
      None)
  }
}
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.EdificeSetupSupportSuite"`
Expected: both tests PASS (confirm `CardStaging.without` and the site/denizens update compile and behave as in `TargetingFixture.fortressAt`).

- [ ] **Step 4: Extract the walk driver out of `SetupProcedureSuite`**

Create `src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala`:

```scala
package oathdigital.gameplay.setup

import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** Walks `tree` from scratch, answering every park with the first option a
  * `Decide` offers, until the tree finishes -- shared by `SetupProcedureSuite`
  * and the slice-3 edifice-power suites, which pass a real `WalkerPowers`
  * (unlike the bare-procedure suite, which passes `WalkerPowers.empty`) so
  * their `ContributingPower`s actually fire.
  */
object SetupWalkDriver extends WalkerRecordedOpsReducer {
  def driveToCompletion(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : ReadyGame = {
    var state = ready
    var outcome = ProcedureWalker.advance(state, tree, None, powers).toOption.get
    while (outcome.isInstanceOf[WalkerOutcome.Parked]) {
      val WalkerOutcome.Parked(pending, events) = outcome: @unchecked
      state = foldRecordedOps(state, events, "setup walk failed")
      val decide = ProcedureWalker.parkedDecide(state, tree, pending, powers).get
      val answer = Answered(decide.decisionId,
        DecisionAnswer.ChooseOneAnswer(
          decide.query.asInstanceOf[DecisionQuery.ChooseOne].options.head.ref),
        decide.owner)
      outcome = ProcedureWalker.resolve(state, tree, pending, answer, powers)
        .toOption.get
    }
    val WalkerOutcome.Finished(finished, _) = outcome: @unchecked
    finished
  }
}
```

- [ ] **Step 5: Point `SetupProcedureSuite` at the extracted driver**

In `src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala`, delete the private `driveToCompletion` method (lines 18-36) and its now-unused `WalkerRecordedOpsReducer`/`ProcedureWalker`/`WalkerOutcome` imports that only that method needed, then replace its one call site:

```scala
    val finished = SetupWalkDriver.driveToCompletion(ready, tree, WalkerPowers.empty)
```

- [ ] **Step 6: Run the full backend suite to confirm the extraction changed nothing**

Run: `./sbtw test`
Expected: PASS, same count as before this task (the driver's behavior is unchanged, only its location).

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala \
  src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupportSuite.scala \
  src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala \
  src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala
git commit -m "$(cat <<'EOF'
feat: add the WhenExplored window and slice-3 setup-power scaffolding

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: E02 Great Market / Bandit Market (`SetupEnd`)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` (register both faces)
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/GreatMarketRulesSuite.scala`

**Interfaces:**
- Consumes: `EdificeSetupSupport.siteOf` (Task 1); `oathdigital.model.CoreOperations.{Move, Burn}`, `Location.FavorBank`/`Location.Site`, `SiteRule.ruler`, `SiteRuler.Bandits`, `catalog.suitOf` (all pre-existing).
- Produces: `GreatMarket.forCatalog`, `BanditMarket.forCatalog` (`ExecutableCatalog => Option[...]`), for Task 2's own catalog registration and for any later slice.

**Implementation note:** the catalog's rules text is `"Place [favor] on this site for each denizen _(including this)_ in this region."` with no favor-bank suit named -- but a site's favor (`SiteState.tokens.favor`) is a plain unsuited count, so whichever bank funds the `Move` makes no difference to the result on the site or to any later read of it. The `Move` operation still needs *a* suited `FavorBank` as its source (the model has no unsuited favor location), so this plan draws from `catalog.suitOf(edifice)` -- Great Market's/Bandit Market's own printed suit -- the same helper `HornedMask`/`Dazzle`/`Bury.standard` already use wherever a card's favor needs a bank and none is named. `EdificeState` is a `SiteDenizenState` like `DenizenState` (`Cards.scala:20-22`), so "denizen" counts every card in the region's site slots, edifices included, with no special-casing for "this card" -- the count is naturally already inclusive.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/setup/GreatMarketRulesSuite.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class GreatMarketRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E02")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = FirstGameSetupFixture.sites.head

  private def stagedAt(side: EdificeSide): ReadyGame =
    CardStaging.without(FirstGameSetupFixture.freshReady, edifice).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(edifice, side, Tokens.empty))))))

  private def finish(ready: ReadyGame): ReadyGame = {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)
  }

  test("Great Market places one favor per denizen in its region, itself included") {
    val staged = stagedAt(EdificeSide.Intact)
    val region = staged.game.current.map.regionOf(site).get
    val expectedCount = staged.game.current.map.inPlay
      .filter(s => staged.game.current.map.regionOf(s).contains(region))
      .flatMap(s => staged.game.current.map.sites(s).denizens)
      .size
    val finished = finish(staged)
    assertEquals(finished.game.current.map.sites(site).tokens.favor, expectedCount)
  }

  test("Bandit Market favors every bandit-ruled site and burns every bank") {
    val staged = stagedAt(EdificeSide.Ruined)
    val banditSite = FirstGameSetupFixture.sites(1)
    val withBandits = staged.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(banditSite, c.map.sites(banditSite).copy(
        forces = SiteForces.Occupied(ForceKind.Bandit, 1))))))
    val startingBank = withBandits.banks.favor
    val finished = finish(withBandits)
    assertEquals(finished.game.current.map.sites(banditSite).tokens.favor, 1)
    Suit.all.foreach(suit => assertEquals(finished.banks.favor(suit),
      startingBank(suit) - (if (suit == Suit.Discord) 2 else 1)))
  }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.GreatMarketRulesSuite"`
Expected: FAIL (`GreatMarket`/`BanditMarket`/`SetupWalkDriver` in `oathdigital.gameplay.setup` not found -- fix the `SetupWalkDriver` import path if Task 1 placed it differently).

> Before writing the assertion above, confirm `ready.banks` and its `favor: Map[Suit, Int]`-shaped (or similar) accessor exist with that exact name/shape by reading `ReadyGame`'s definition (`src/main/scala/oathdigital/model/*.scala`) -- adjust the two `startingBank`/`finished.banks.favor` lines to match whatever the real accessor is if it differs, keeping the intent: burning one favor of every suit from `banks.favor`, and Discord additionally losing one more to Bandit Market's own placement upkeep... re-derive `expected` directly from `startingBank` and the known operations instead of hand-deriving `-2`/`-1` if that's clearer once the real type is in hand.

- [ ] **Step 3: Write the shared face base and both powers**

Create `src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** E02, both faces (2026-09-21 Chronicle design, "Setup powers"). Each names
  * `PowerWindow.WhenExplored` alongside `SetupEnd` so the same contribution
  * will serve WHEN EXPLORED once an explore procedure exists to fire it;
  * nothing folds that window yet, so only the SETUP path runs or is tested.
  */
sealed abstract class MarketRule extends ContributingPower {
  def catalog: ExecutableCatalog
  def edifice: EdificeId
  protected def side: EdificeSide

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ready: ReadyGame): Option[SiteId] =
    EdificeSetupSupport.siteOf(ready, edifice, side)

  override def applicable(ctx: PowerCtx): Boolean = at(ctx.state).isDefined

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]]

  final def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx.state) match {
      case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site))
      case None => ops
    }))
    Map(PowerWindow.SetupEnd -> effect, PowerWindow.WhenExplored -> effect)
  }
}

final case class GreatMarket private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends MarketRule {
  def id: PowerId = GreatMarket.id
  protected def side: EdificeSide = EdificeSide.Intact

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    suit <- catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice))
    region <- ready.game.current.map.regionOf(at).toRight(
      OathViolation.InvalidEventOrder(s"${at.value} is not in play"))
  } yield {
    val current = ready.game.current
    val count = current.map.inPlay.filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens).size
    if (count == 0) Vector.empty
    else Vector(Move(Piece.Favor(count),
      PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(at))))
  }
}
object GreatMarket {
  val id: PowerId = PowerId("edifice.e02.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[GreatMarket] =
    CatalogCards.edifice(catalog, id).map(new GreatMarket(_, catalog))
}

final case class BanditMarket private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends MarketRule {
  def id: PowerId = BanditMarket.id
  protected def side: EdificeSide = EdificeSide.Ruined

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice)).map { suit =>
      val current = ready.game.current
      val bandited = current.map.inPlay.filter(s => SiteRule.ruler(
        current.map.sites(s).forces, current.players).contains(SiteRuler.Bandits))
      val placed: Vector[CoreOperation] = bandited.map(s => Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(s))))
      val burned: Vector[CoreOperation] = Suit.all.map(bank =>
        Burn.favor(1, PositionedLocation(Location.FavorBank(bank))))
      placed ++ burned
    }
}
object BanditMarket {
  val id: PowerId = PowerId("edifice.e02.ruined")
  def forCatalog(catalog: ExecutableCatalog): Option[BanditMarket] =
    CatalogCards.edifice(catalog, id).map(new BanditMarket(_, catalog))
}
```

> `SiteRule.ruler` returns `Either[SiteRuleError, SiteRuler]`; `.contains(SiteRuler.Bandits)` treats a `Left` (e.g. `UnknownLineage`) as not-bandits, which is correct here -- an unruled or mis-attributed site is simply not favored.

- [ ] **Step 4: Register both faces**

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, add the import and append both faces to `default`:

```scala
import oathdigital.gameplay.powers.setup.{BanditMarket, GreatMarket}
```

```scala
      Dazzle.forCatalog(catalog) ++ GreatMarket.forCatalog(catalog) ++
      BanditMarket.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

- [ ] **Step 5: Run the tests to confirm they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.GreatMarketRulesSuite"`
Expected: PASS. If the bank-shape assumption in Step 1/2 was off, this is where it gets corrected against the real `ReadyGame`/bank type.

- [ ] **Step 6: Run the full backend suite**

Run: `./sbtw test`
Expected: PASS -- confirms the new registrations don't perturb any other suite that drives Setup with real `WalkerPowerCatalog.default` powers (e.g. anything reusing `FirstGameSetupFixture.execute`, since a fixture Chronicle may or may not place E02 depending on catalog site order; either way it must still pass).

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/GreatMarketRulesSuite.scala
git commit -m "$(cat <<'EOF'
feat: implement Great Market and Bandit Market (E02)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: E06 Great Forge / Broken Forge (`SetupPawnPlaced`)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` (register both faces)
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/GreatForgeRulesSuite.scala`

**Interfaces:**
- Consumes: `EdificeSetupSupport.siteOf`/`pawnPlacementSite` (Task 1); `oathdigital.gameplay.powers.RelicDraws.takeTop` (pre-existing, exact reuse for Great Forge); `Discard.Relic` (pre-existing).
- Produces: `GreatForge.forCatalog`, `BrokenForge.forCatalog`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/setup/GreatForgeRulesSuite.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class GreatForgeRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E06")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  // The generic driver always answers a Decide with its first offered
  // option, and `siteOptions` is `map.inPlay` order -- staging at
  // `sites.head` puts the edifice under the FIRST participant's (setup's
  // first player) pawn-placement choice.
  private val site = FirstGameSetupFixture.sites.head
  private val firstPlayer = PlayerId("p2")

  private def stagedAt(side: EdificeSide): ReadyGame =
    CardStaging.without(FirstGameSetupFixture.freshReady, edifice).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(edifice, side, Tokens.empty))))))

  private def finish(ready: ReadyGame): ReadyGame = {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)
  }

  test("Great Forge gives the placing player the top relic facedown") {
    val staged = stagedAt(EdificeSide.Intact)
    val topRelic = staged.game.current.commonCards.relicDeck.head
    val finished = finish(staged)
    val player = finished.game.current.players.find(_.player == firstPlayer).get
    assert(player.relics.exists(r => r.id == topRelic &&
      r.orientation == Orientation.FaceDown))
  }

  test("Broken Forge discards every relic in its region to setAsideRelics") {
    val staged = stagedAt(EdificeSide.Ruined)
    val region = staged.game.current.map.regionOf(site).get
    val relicSite = staged.game.current.map.inPlay
      .find(s => staged.game.current.map.regionOf(s).contains(region) &&
        staged.game.current.map.sites(s).relics.nonEmpty).get
    val relicsBefore = staged.game.current.map.sites(relicSite).relics.map(_.id).toSet
    val finished = finish(staged)
    assert(finished.game.current.map.sites(relicSite).relics.isEmpty)
    assert(relicsBefore.nonEmpty)
  }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.GreatForgeRulesSuite"`
Expected: FAIL (`GreatForge`/`BrokenForge` not found). If the second test's `relicSite` lookup can't find a region site with relics in the fixture board, adjust the fixture setup by staging a relic there directly (`RelicState`, mirroring `TargetingFixture.holds`) rather than relying on the Chronicle fixture's incidental relic placement -- keep the assertion's intent (a relic that was at a site in the region is gone from it and, per Step 3's implementation, in `setAsideRelics`).

- [ ] **Step 3: Write both powers**

Create `src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, RelicDraws}
import oathdigital.model._

/** E06, both faces (2026-09-21 Chronicle design, "Setup powers"). See
  * `GreatMarketRules` for the `WhenExplored`/window-sharing rationale.
  */
sealed abstract class ForgeRule extends ContributingPower {
  def catalog: ExecutableCatalog
  def edifice: EdificeId
  protected def side: EdificeSide

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ctx: PowerCtx): Option[SiteId] =
    EdificeSetupSupport.siteOf(ctx.state, edifice, side)
      .filter(site => EdificeSetupSupport.pawnPlacementSite(ctx).contains(site))

  override def applicable(ctx: PowerCtx): Boolean = at(ctx).isDefined

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]]

  final def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx) match {
      case Some(site) => ops :+ BuildOps((ready, _) =>
        build(ready, ctx.activePlayer, site))
      case None => ops
    }))
    Map(PowerWindow.SetupPawnPlaced -> effect, PowerWindow.WhenExplored -> effect)
  }
}

final case class GreatForge private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ForgeRule {
  def id: PowerId = GreatForge.id
  protected def side: EdificeSide = EdificeSide.Intact

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(RelicDraws.takeTop(ready, actor))
}
object GreatForge {
  val id: PowerId = PowerId("edifice.e06.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[GreatForge] =
    CatalogCards.edifice(catalog, id).map(new GreatForge(_, catalog))
}

final case class BrokenForge private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ForgeRule {
  def id: PowerId = BrokenForge.id
  protected def side: EdificeSide = EdificeSide.Ruined

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.map.regionOf(at).toRight(
      OathViolation.InvalidEventOrder(s"${at.value} is not in play")).map { region =>
      val current = ready.game.current
      current.map.inPlay.filter(s => current.map.regionOf(s).contains(region))
        .flatMap(s => current.map.sites(s).relics.map(relic => Discard.Relic(
          relic.id, PositionedLocation(Location.Site(s)), relic.tokens.secrets, actor)))
    }
}
object BrokenForge {
  val id: PowerId = PowerId("edifice.e06.ruined")
  def forCatalog(catalog: ExecutableCatalog): Option[BrokenForge] =
    CatalogCards.edifice(catalog, id).map(new BrokenForge(_, catalog))
}
```

- [ ] **Step 4: Register both faces**

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`:

```scala
import oathdigital.gameplay.powers.setup.{BanditMarket, BrokenForge, GreatForge, GreatMarket}
```

```scala
      BanditMarket.forCatalog(catalog) ++ GreatForge.forCatalog(catalog) ++
      BrokenForge.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

- [ ] **Step 5: Run the tests, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.GreatForgeRulesSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/GreatForgeRulesSuite.scala
git commit -m "$(cat <<'EOF'
feat: implement Great Forge and Broken Forge (E06)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: E22 Proving Grounds (`SetupPawnPlaced`) / Empty Grounds (`SetupEnd`)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` (register both faces)
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRulesSuite.scala`

**Interfaces:**
- Consumes: `EdificeSetupSupport` (Task 1); `oathdigital.gameplay.powers.PlayerFacts.forceKind`, `Gain.Warbands`, `oathdigital.gameplay.actions.CardPlay.nextRegion`, `Discard.Denizen`/`Discard.RuinedEdifice`, `catalog.suitOf` (all pre-existing; the Empty Grounds shape directly mirrors `Dazzle`'s region-discard loop in `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`).
- Produces: `ProvingGrounds.forCatalog`, `EmptyGrounds.forCatalog`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRulesSuite.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, PlayerFacts, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class ProvingGroundsRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E22")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = FirstGameSetupFixture.sites.head
  private val firstPlayer = PlayerId("p2")

  private def stagedAt(side: EdificeSide, at: SiteId = site): ReadyGame =
    CardStaging.without(FirstGameSetupFixture.freshReady, edifice).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(at,
        c.map.sites(at).copy(denizens = c.map.sites(at).denizens :+
          EdificeState(edifice, side, Tokens.empty))))))

  private def finish(ready: ReadyGame): ReadyGame = {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)
  }

  test("Proving Grounds gives the placing player three warbands") {
    val staged = stagedAt(EdificeSide.Intact)
    val kind = PlayerFacts.forceKind(staged, firstPlayer).toOption.get
    val before = staged.game.current.players.find(_.player == firstPlayer).get
      .board.warbands(kind)
    val finished = finish(staged)
    val after = finished.game.current.players.find(_.player == firstPlayer).get
      .board.warbands(kind)
    assertEquals(after, before + 3)
  }

  test("Empty Grounds discards every other denizen in its region") {
    val staged = stagedAt(EdificeSide.Ruined)
    val region = staged.game.current.map.regionOf(site).get
    val remainingOtherDenizens = { (state: ReadyGame) =>
      state.game.current.map.inPlay.filter(s =>
        state.game.current.map.regionOf(s).contains(region))
        .flatMap(s => state.game.current.map.sites(s).denizens)
        .exists {
          case e: EdificeState => e.id == edifice
          case _ => true
        }
    }
    assert(remainingOtherDenizens(staged), "fixture must have other cards in the region")
    val finished = finish(staged)
    val remaining = finished.game.current.map.inPlay.filter(s =>
      finished.game.current.map.regionOf(s).contains(region))
      .flatMap(s => finished.game.current.map.sites(s).denizens)
    assertEquals(remaining, Vector.empty[SiteDenizenState])
  }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.ProvingGroundsRulesSuite"`
Expected: FAIL (`ProvingGrounds`/`EmptyGrounds` not found). The second test's `remainingOtherDenizens` guard is written loosely on purpose (it will also be true just from Empty Grounds' own edifice being present) -- if the fixture's region around `site` genuinely has no other cards, restage with an extra denizen (mirror `TargetingFixture.adviserOf`'s card-placement idiom) so the assertion isn't vacuous.

- [ ] **Step 3: Write both powers**

Create `src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala`:

```scala
package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts}
import oathdigital.model._

/** E22, both faces (2026-09-21 Chronicle design, "Setup powers"). See
  * `GreatMarketRules` for the `WhenExplored`/window-sharing rationale.
  */
final case class ProvingGrounds private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ContributingPower {
  def id: PowerId = ProvingGrounds.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ctx: PowerCtx): Option[SiteId] =
    EdificeSetupSupport.siteOf(ctx.state, edifice, EdificeSide.Intact)
      .filter(site => EdificeSetupSupport.pawnPlacementSite(ctx).contains(site))

  override def applicable(ctx: PowerCtx): Boolean = at(ctx).isDefined

  private def build(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, 3)))

  def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => if (at(ctx).isDefined)
      ops :+ BuildOps((ready, _) => build(ready, ctx.activePlayer)) else ops))
    Map(PowerWindow.SetupPawnPlaced -> effect, PowerWindow.WhenExplored -> effect)
  }
}
object ProvingGrounds {
  val id: PowerId = PowerId("edifice.e22.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[ProvingGrounds] =
    CatalogCards.edifice(catalog, id).map(new ProvingGrounds(_, catalog))
}

/** Discards all OTHER denizens in this region -- edifices count as denizens
  * for this clause specifically (design spec table), and a discarded ruined
  * edifice returns to the edifice deck via `Discard.RuinedEdifice`; an
  * intact edifice is locked and stays, mirroring `Dazzle`. The actor is the
  * first player, per the spec's "the actor for powers with no 'you' is the
  * first player" (SetupEnd has no single "you").
  */
final case class EmptyGrounds private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ContributingPower {
  def id: PowerId = EmptyGrounds.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ready: ReadyGame): Option[SiteId] =
    EdificeSetupSupport.siteOf(ready, edifice, EdificeSide.Ruined)

  override def applicable(ctx: PowerCtx): Boolean = at(ctx.state).isDefined

  private def build(ready: ReadyGame, site: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    val actor = ready.setup.firstPlayer
    current.map.regionOf(site).toRight(OathViolation.InvalidEventOrder(
      s"${site.value} is not in play")).flatMap { region =>
      val destination = CardPlay.nextRegion(region)
      val candidates = current.map.inPlay.filter(s =>
        current.map.regionOf(s).contains(region)).flatMap(s =>
        current.map.sites(s).denizens.map(s -> _))
        .filterNot { case (s, card) => s == site && card.id.value == edifice.value }
      candidates.foldLeft[Either[OathViolation, Vector[CoreOperation]]](Right(Vector.empty)) {
        case (acc, (siteId, card)) => for {
          operations <- acc
          suit <- catalog.suitOf(card.id).toRight(card match {
            case denizen: DenizenState => OathViolation.UnknownWorldCard(denizen.id)
            case edifice: EdificeState => OathViolation.UnknownEdifice(edifice.id)
          })
        } yield card match {
          case denizen: DenizenState => operations :+ Discard.Denizen(denizen.id,
            PositionedLocation(Location.Site(siteId)), destination, suit,
            denizen.tokens.favor, denizen.tokens.secrets, actor)
          case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
            operations :+ Discard.RuinedEdifice(edifice.id,
              PositionedLocation(Location.Site(siteId)), suit,
              edifice.tokens.favor, edifice.tokens.secrets, actor)
          case _ => operations
        }
      }
    }
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx.state) match {
      case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site))
      case None => ops
    }))
    Map(PowerWindow.SetupEnd -> effect, PowerWindow.WhenExplored -> effect)
  }
}
object EmptyGrounds {
  val id: PowerId = PowerId("edifice.e22.ruined")
  def forCatalog(catalog: ExecutableCatalog): Option[EmptyGrounds] =
    CatalogCards.edifice(catalog, id).map(new EmptyGrounds(_, catalog))
}
```

- [ ] **Step 4: Register both faces**

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`:

```scala
import oathdigital.gameplay.powers.setup.{BanditMarket, BrokenForge, EmptyGrounds, GreatForge, GreatMarket, ProvingGrounds}
```

```scala
      GreatForge.forCatalog(catalog) ++ BrokenForge.forCatalog(catalog) ++
      ProvingGrounds.forCatalog(catalog) ++ EmptyGrounds.forCatalog(catalog) :+
      TakeWealthLimit :+ ConspiracyWhenPlayed)
```

- [ ] **Step 5: Run the tests, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.ProvingGroundsRulesSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS -- all six faces now registered together for the first time; this is the first point a fixture board that happens to carry several of them at once is exercised end to end.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRulesSuite.scala
git commit -m "$(cat <<'EOF'
feat: implement Proving Grounds and Empty Grounds (E22)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Documentation and final verification

**Files:**
- Modify: `docs/ROADMAP.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Update the roadmap**

In `docs/ROADMAP.md`, replace line 45:

```markdown
Slice 3 (the E02/E06/E22 powers) remains.
```

with:

```markdown
Slice 3 (the E02/E06/E22 powers) is done -- plan at
[docs/superpowers/plans/2026-09-21-chronicle-setup-slice3.md](superpowers/plans/2026-09-21-chronicle-setup-slice3.md).
All six batch-1 edifices now work; only the WHEN EXPLORED windows they
declared stay inert until an explore procedure exists to fire them.
```

- [ ] **Step 2: Run the full verification gate**

```bash
./sbtw test
./sbtw frontend/test
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
```

Expected: all four green.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "$(cat <<'EOF'
docs: record Chronicle setup slice 3

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Self-Review Notes

- **Spec coverage:** all six faces from the "Setup powers" table (Task 2-4), the `WhenExplored` window naming for each (Task 1/2/3/4), and the "Homeland outside the first 8 sites keeps its edifice in storage" inertness rule (every power's `applicable`) are covered. Simultaneous-effect site ordering and later-game/WHEN EXPLORED firing are explicit spec follow-ups, not this slice's job, and nothing here builds toward them beyond declaring the window key.
- **Placeholder scan:** none found; every step carries real, complete code.
- **Type consistency:** `EdificeSetupSupport.siteOf`/`pawnPlacementSite` (Task 1) are called with the same signatures in Tasks 2-4; `SetupWalkDriver.driveToCompletion(ready, tree, powers)` likewise. `GreatMarket`/`BanditMarket`/`GreatForge`/`BrokenForge`/`ProvingGrounds`/`EmptyGrounds` each expose `def forCatalog(catalog: ExecutableCatalog): Option[Self]`, matching the call sites added to `WalkerPowerCatalog.default` in each task.
- **One implementation-only choice, not a rules ambiguity** (Task 2): Great Market/Bandit Market's favor `Move` draws from the edifice's own catalog suit's bank. This cannot affect any observable outcome -- site favor (`SiteState.tokens.favor`) is a plain unsuited count -- it only exists because the operation model requires *some* suited `FavorBank` as a source.
