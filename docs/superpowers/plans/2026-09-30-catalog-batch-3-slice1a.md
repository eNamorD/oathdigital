# Catalog Batch 3, Slice 1a (Battle Plans: Dice and Conditions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement eight battle plans: Cracking Ground (71), Walled Garden (195), Banner Breakers (222), Extra Provisions (48), Village Constable (132), Encirclement (124), Bandit Standard (R30) and Rival Khan (156).

**Architecture:**

- Every card is a `BattlePlan` (`src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`).
  - Its `plan` builds a `CampaignPlanOffer` from where the card stands and the Campaign's setup.
  - A plan whose condition fails, or whose dice count would be zero, returns `None`.
- `PlanContext` gains three reads of the enemy: `enemy`, `enemyHasAdviser` and `enemyHolds`. Cracked Sage switches to `enemyHasAdviser`, with its suite unchanged.
- Seven plans only change dice. They register in `SimplePlans`.
- Rival Khan discards itself after the Campaign through `PlanDiscard.afterCampaign`, exactly as Horse Archers does. It registers in `PlanRules`.
- No engine change: no new operation, window, query, option kind, `NoteArg` kind, protocol or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Slicing", "Log lines", "Testing"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Slice 1: battle plans" and "Slice 1a"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines", "Slice 1: battle plans"):
  - Rival Khan, key `discarded`: "{Card}: Discarded after the Campaign." This is `PlanDiscard.discarded`, whose source prefix is the card.
  - The other seven plans write no line. Their effect is only a dice change.
- Batch rulings that every task applies (rulings, "Slice 1: battle plans"):
  - **Sign.** "±" is fixed by the side: an attacker adds attack dice, a defender removes them (`PlanDice`).
  - **Enemy.** For an attacker's plan, the defender, a player or bandits. For a defender's plan, the attacker.
  - **Zero.** A dice plan whose count would be zero is not offered.
  - **Bandit defenders** apply their free plans without choosing, and never use a plan that costs. `CampaignPlanChoice` already does this.
- Baseline: `main` at the start of this plan passes 2161 server tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** The spec's Testing section says `PowerImplementationStatusSuite` and `PowerKindsCatalogSuite` pin the new cards. The 2026-09-27 test pruning removed per-card registration pins in favour of one uniqueness test, and `PowerKindsCatalogSuite` does not list battle plans by its own rule. Every suite below runs through `WalkerPowerCatalog.default`, so a plan that is not registered is never offered, and its suite fails. If pins are wanted after all, add them in Task 4.
2. **The enemy reads live on `PlanContext`.** Cracked Sage, Village Constable, Banner Breakers and Rival Khan all read the enemy. `PlanContext` gains `enemy`, `enemyHasAdviser` and `enemyHolds`, and Cracked Sage's private copy is removed. Cracked Sage's suite must pass unchanged. This is a refactor inside `gameplay/powers/`, which the spec allows.
3. **Walled Garden counts faceup denizens and edifices on either face.** Only a faceup card has a suit (batch 2). An edifice has its suit on both faces (`catalog.suitOf`). A facedown denizen at a site counts nothing.
4. **Bandit Standard's region is the region of `setup.origin`.** `setup.origin` is the attacker's pawn site, in a Conquest and in a Raid. A Conquest against bandits is refused before the region is counted.
5. **Encirclement reads `CampaignBattle.defenderForce`.** That is exactly the force the defense adds, so "as the defense counts it" holds by construction. The attacker's force is `setup.force`. Both are read when the plan is offered. The plan window re-offers after each pick, so a Wrestlers sacrifice chosen earlier in the defender's window lowers the defender's force.
6. **Rival Khan registers in `PlanRules`.** `PlanRules` holds the plans that reach beyond the plan window, and Rival Khan discards itself at the Campaign's root, as Horse Archers does.
7. **The spec's "Verify at plan time" items for this slice.** Only "Plan discards" applies here. Rival Khan uses `PlanDiscard.afterCampaign`, so it behaves exactly as Horse Archers does. The other items belong to later slices.

## File Structure

Production, all under `src/main/scala/oathdigital/gameplay/powers/campaign/`:

| File | Change | Responsibility |
|---|---|---|
| `PlanContext.scala` | Modify | Adds `enemy`, `enemyHasAdviser` and `enemyHolds` |
| `CrackedSage.scala` | Modify | Uses `enemyHasAdviser` |
| `VillageConstable.scala` | Create | ±2, unless the enemy holds the People's Favor |
| `BannerBreakers.scala` | Create | +3 for a favor placed and a favor burnt, when the defender holds a banner |
| `RivalKhan.scala` | Create | ±4 when the enemy has a faceup nomad adviser, discarded after the Campaign |
| `CrackingGround.scala` | Create | ±1 per targeted site |
| `WalledGarden.scala` | Create | +1 defense die per beast card in play, when its site is targeted |
| `BanditStandard.scala` | Create | +1 per bandit warband in the pawn's region |
| `ExtraProvisions.scala` | Create | +1 defense die for a favor placed |
| `Encirclement.scala` | Create | ±2 for a favor placed, when its user's force is larger |
| `SimplePlans.scala` | Modify | Registers the seven dice-only plans |
| `PlanRules.scala` | Modify | Registers Rival Khan |

Tests, all under `src/test/scala/oathdigital/gameplay/powers/campaign/`:

| File | Change |
|---|---|
| `PlanDriver.scala` | Modify: adds `Run.offers` |
| `VillageConstableSuite.scala`, `BannerBreakersSuite.scala`, `RivalKhanSuite.scala` | Create (Task 1) |
| `CrackingGroundSuite.scala`, `WalledGardenSuite.scala`, `BanditStandardSuite.scala` | Create (Task 2) |
| `ExtraProvisionsSuite.scala`, `EncirclementSuite.scala` | Create (Task 3) |

Docs: `docs/ROADMAP.md` (Task 4).

### How the suites drive a Campaign

Read `PlanDriver.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` before writing a suite. In short:

- `board(extras, warbands, supply)`: p1, the actor, stands at the origin, which two bandits rule. `extras` further sites are bandit-ruled too (`b.extras`). Every other site starts empty and holds no card. p2 (`b.other`) and p3 stand at the first site nobody rules.
- `againstPlayer(b)`: p2 rules the origin with two warbands and holds the Oath title, so p2 is offered the title's defense plan (`TitleDefensePlan`) until they use it. That keeps p2's plan window open, so a "not offered" check against a player defender is not vacuous.
- `withEnemyAtOrigin(b)`: p2 joins the actor at the origin, so a Raid is legal. The origin stays bandit-ruled.
- `commit(rules(dice), b, force, targets, raid)` starts the Campaign and answers the kind, the targets and the force.
- `run.pick(who, CampaignIds.attackerPlan | defenderPlan, ref)` chooses a plan. `run.finish` finishes every window until the Campaign ends.
- `run.ops` is every recorded operation. `picked.since(run)` is only what the pick recorded. Use `since` whenever a dice change could equal the force or the printed defense, because the gathered pools are `ModifyDicePool` operations too.
- An attacker's added dice record `ModifyDicePool(CampaignIds.attackPool, n)`. A defender's removed dice record `ModifyDicePool(CampaignIds.attackPool, -n)`. Added defense dice record `ModifyDicePool(CampaignIds.defensePool, n)`.
- A bandit defender's applied plan also records `ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)` after its effects.
- `Run.offered(actor)` rebuilds the parked decision with the Campaign's actor, so its argument is always the actor, even in the defender's window.

---

### Task 1: The enemy on `PlanContext`, then Village Constable, Banner Breakers and Rival Khan

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/CrackedSage.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/VillageConstable.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BannerBreakers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/RivalKhan.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/VillageConstableSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BannerBreakersSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/RivalKhanSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen`, `PlanDice.label/effect`, `PlanDiscard.afterCampaign/discarded`, `CatalogCards.denizen`, `BannerRules.holder(current, banner): Option[PlayerId]`.
- Produces, used by Tasks 2 and 3:
  - `PlanContext.enemy: CampaignDefender`
  - `PlanContext.enemyHasAdviser(catalog: ExecutableCatalog, suit: Suit): Boolean`
  - `PlanContext.enemyHolds(banner: Banner): Boolean`
  - `PlanDriver.Run.offers(actor: PlayerId, who: PlayerId, id: String, ref: DecisionOptionRef): Boolean`

- [ ] **Step 1: Add `Run.offers` to the test driver**

In `PlanDriver.scala`, inside `final case class Run`, after `def offered(actor: PlayerId)`, add:

```scala
    /** Whether the run is parked on the plan decision `id`, awaiting `who`,
      * with `ref` among its options. `actor` is the Campaign's actor, which
      * the parked decision is rebuilt with. */
    def offers(actor: PlayerId, who: PlayerId, id: String,
        ref: DecisionOptionRef): Boolean =
      awaits(this, who, id) && offered(actor).contains(ref)
```

- [ ] **Step 2: Write the failing suites**

Create `VillageConstableSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Village Constable: a free, site-only plan for two attack dice, added or
  * removed, unless the enemy holds the People's Favor. */
class VillageConstableSuite extends munit.FunSuite:
  private val card = cardWith("denizen.village-constable")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  /** The actor also rules a site outside the Campaign, where Village
    * Constable stands. */
  private def attacker(b: Board): Board =
    val site = b.ready.game.current.map.inPlay.find(_ != b.origin).get
    withSiteCard(actorRules(b, site), site, card)

  test("an attacker adds two attack dice against bandits, for nothing"):
    val b = attacker(board())
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered while the defender holds the People's Favor"):
    val base = attacker(againstPlayer(board()))
    assert(commit(rules(winning), base, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = on(base)(_.peoplesFavor(Some(base.other), 1))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes two attack dice"):
    val base = againstPlayer(board())
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it unless the attacker holds the People's Favor"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(commit(rules(winning), b, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val favored = on(b)(_.peoplesFavor(Some(b.actor), 1))
    assert(!commit(rules(winning), favored, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
```

Create `BannerBreakersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Banner Breakers: a favor placed and a favor burnt for three attack dice,
  * offered only while the defender holds a banner. */
class BannerBreakersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.banner-breakers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  /** The actor holds Banner Breakers and 2 favor. Nobody holds a banner. */
  private def funded(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 2)
      .peoplesFavor(None, 0).darkestSecret(None, 1))

  private val base: Board = funded(againstPlayer(board()))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker places a favor and burns one for three attack dice " +
      "against the Darkest Secret"):
    val b = on(base)(_.darkestSecret(Some(base.other), 1))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)

  test("it is offered against the People's Favor"):
    val b = on(base)(_.peoplesFavor(Some(base.other), 1))
    assert(commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the defender holds no banner"):
    assert(!commit(rules(winning), base, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is never offered against bandits"):
    val b = funded(board())
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
```

Create `RivalKhanSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Rival Khan: a free plan for four attack dice, added or removed, offered
  * only when the enemy has a faceup nomad adviser, and discarded after the
  * Campaign. */
class RivalKhanSuite extends munit.FunSuite:
  private val card = cardWith("denizen.rival-khan")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomad = inert(Suit.Nomad, 1).head
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(RivalKhan.id, Vector(PlanDiscard.discarded), run.events)

  /** The actor holds Rival Khan; the defender, a player, holds a nomad
    * adviser on `enemy`'s face. */
  private def attacker(enemy: Orientation): Board =
    val base = againstPlayer(board())
    withAdviserFor(withAdviser(base, card, Orientation.FaceUp), base.other,
      nomad, enemy)

  test("an attacker adds four attack dice, and it is discarded after the " +
      "Campaign"):
    val b = attacker(Orientation.FaceUp)
    val run = commit(rules(losing), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finish
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("it is not offered when the enemy's nomad adviser is facedown"):
    val b = attacker(Orientation.FaceDown)
    assert(!commit(rules(losing), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is never offered against bandits"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    assert(!commit(rules(losing), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes four attack dice when the attacker has a nomad " +
      "adviser, and it is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviser(base, nomad, Orientation.FaceUp),
      base.other, card, Orientation.FaceUp)
    val run = commit(rules(losing), b, 5)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    assert(discarded(run.finish.state))
```

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VillageConstableSuite oathdigital.gameplay.powers.campaign.BannerBreakersSuite oathdigital.gameplay.powers.campaign.RivalKhanSuite"`
Expected: compilation fails on `RivalKhan` (not found).

- [ ] **Step 4: Add the enemy reads to `PlanContext`**

In `PlanContext.scala`, add these imports beside the existing ones:

```scala
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
```

Then, inside `final case class PlanContext`, after `def targetsIn`, add:

```scala
  /** The other side: for an attacker's plan the defender, a player or
    * bandits; for a defender's plan the attacker. */
  def enemy: CampaignDefender = side match
    case CampaignPlanSide.Attacker => setup.defender
    case CampaignPlanSide.Defender => CampaignDefender.Player(setup.actor)

  private def enemyPlayer: Option[PlayerState] = enemy match
    case CampaignDefender.Player(player) =>
      ready.game.current.players.find(_.player == player)
    case CampaignDefender.Bandits => None

  /** Whether the enemy has a faceup adviser of `suit`. Only a faceup card has
    * a suit, and bandits hold no advisers. */
  def enemyHasAdviser(catalog: ExecutableCatalog, suit: Suit): Boolean =
    enemyPlayer.exists(_.advisers.exists {
      case DenizenState(held, Orientation.FaceUp, _) =>
        catalog.suitOf(held).contains(suit)
      case _ => false
    })

  /** Whether the enemy holds `banner`. Bandits hold none. */
  def enemyHolds(banner: Banner): Boolean = enemyPlayer.exists(held =>
    BannerRules.holder(ready.game.current, banner).contains(held.player))
```

- [ ] **Step 5: Switch Cracked Sage to `enemyHasAdviser`**

In `CrackedSage.scala`, replace the body of `plan` and delete the private `enemy` and `enemyHasArcane` methods, so the class body after `sides` reads:

```scala
  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.enemyHasAdviser(catalog, Suit.Arcane))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Cracked Sage", context.side, CrackedSage.Dice),
        Vector(CampaignPlanCost.Secret(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(PlanDice.effect(context.side, CrackedSage.Dice))))
```

Leave its doc comment and companion object unchanged.

- [ ] **Step 6: Write Village Constable**

Create `VillageConstable.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Village Constable (card 132), a battle plan for either side: "±2
  * [attack-die] unless your enemy has the People's Favor."
  *
  * It is site-only and free, used by the ruler of its site. An attacker adds
  * two attack dice; a defender removes two. It is not offered while the enemy
  * holds the People's Favor. Bandits hold no banner, so it is always offered
  * against them, and a bandit defender applies it at a site it rules.
  */
final case class VillageConstable private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = VillageConstable.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => !context.enemyHolds(Banner.PeoplesFavor))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Village Constable", context.side, VillageConstable.Dice),
        Vector.empty,
        Vector(PlanDice.effect(context.side, VillageConstable.Dice))))

object VillageConstable:
  val id: PowerId = PowerId("denizen.village-constable")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[VillageConstable] =
    CatalogCards.denizen(catalog, id).map(new VillageConstable(_))
```

- [ ] **Step 7: Write Banner Breakers**

Create `BannerBreakers.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Banner Breakers (card 222), an attacker's battle plan: "[favor]
  * [favor-burnt] +3 [attack-die] if the defender has the Darkest Secret or
  * People's Favor."
  *
  * A favor is placed onto the card and another is burnt. It adds three attack
  * dice, and is offered only while the defender holds either banner, so never
  * against bandits.
  */
final case class BannerBreakers private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = BannerBreakers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => Banner.all.exists(context.enemyHolds))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Banner Breakers", context.side, BannerBreakers.Dice),
        Vector(CampaignPlanCost.Favor(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(CampaignPlanEffect.AddAttackDice(BannerBreakers.Dice))))

object BannerBreakers:
  val id: PowerId = PowerId("denizen.banner-breakers")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[BannerBreakers] =
    CatalogCards.denizen(catalog, id).map(new BannerBreakers(_))
```

- [ ] **Step 8: Write Rival Khan**

Create `RivalKhan.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Rival Khan (card 156), a battle plan for either side: "±4 [attack-die] if
  * your enemy has a [suit-nomad] adviser. At end, discard Rival Khan."
  *
  * It is free. An attacker adds four attack dice; a defender removes four. It
  * is offered only when the enemy has a faceup nomad adviser, so never against
  * bandits. The card is discarded through the standard discard once the
  * Campaign has resolved, whoever won, as Horse Archers is.
  */
final case class RivalKhan private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = RivalKhan.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.enemyHasAdviser(catalog, Suit.Nomad))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Rival Khan", context.side, RivalKhan.Dice),
        Vector.empty, Vector(PlanDice.effect(context.side, RivalKhan.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object RivalKhan:
  val id: PowerId = PowerId("denizen.rival-khan")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[RivalKhan] =
    CatalogCards.denizen(catalog, id).map(new RivalKhan(_, catalog))
```

- [ ] **Step 9: Register the three plans**

In `SimplePlans.scala`, change the doc comment's list and add the two plans at the end:

```scala
/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword, Fire
  * Talkers, Nature Worship, Cracked Sage, Village Constable and Banner
  * Breakers, registered together. A plan whose card is absent from `catalog`
  * is omitted.
  */
object SimplePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Mercenaries.forCatalog(catalog).toVector ++
      Wrestlers.forCatalog(catalog).toVector ++
      FearsomeShield.forCatalog(catalog).toVector ++
      ToweringRampart.forCatalog(catalog).toVector ++
      CrackedRampart.forCatalog(catalog).toVector ++
      BattleHonors.forCatalog(catalog).toVector ++
      Longbows.forCatalog(catalog).toVector ++
      BlackSword.forCatalog(catalog).toVector ++
      FireTalkers.forCatalog(catalog).toVector ++
      NatureWorship.forCatalog(catalog).toVector ++
      CrackedSage.forCatalog(catalog).toVector ++
      VillageConstable.forCatalog(catalog).toVector ++
      BannerBreakers.forCatalog(catalog).toVector
```

In `PlanRules.scala`, change the doc comment's "Horse Archers and Storm Caller (a discard at the end)" to "Horse Archers, Storm Caller and Rival Khan (a discard at the end)", and add Rival Khan after Storm Caller:

```scala
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
```

- [ ] **Step 10: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, including the unchanged `CrackedSageSuite` and the 12 new tests.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/CrackedSage.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/VillageConstable.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BannerBreakers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/RivalKhan.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/VillageConstableSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BannerBreakersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/RivalKhanSuite.scala
git commit -m "feat(powers): add Village Constable, Banner Breakers and Rival Khan"
```

---

### Task 2: Cracking Ground, Walled Garden and Bandit Standard

These plans count something on the board for their dice.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/CrackingGround.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/WalledGarden.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BanditStandard.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/CrackingGroundSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WalledGardenSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BanditStandardSuite.scala`

**Interfaces:**
- Consumes: `PlanDriver.Run.offers` (Task 1), `PlanContext.denizen/relic/targets`, `CampaignSetup.targetSites/origin/kind/defender`, `CampaignPlans.appliedMarker`, `CatalogCards.denizen/relic`.
- Produces: `CrackingGround`, `WalledGarden` and `BanditStandard`, each with `id` and `forCatalog`, registered in `SimplePlans`.

- [ ] **Step 1: Write the failing suites**

Create `CrackingGroundSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Cracking Ground: a free plan for one attack die per targeted site, added
  * or removed. */
class CrackingGroundSuite extends munit.FunSuite:
  private val card = cardWith("denizen.cracking-ground")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  test("an attacker adds one attack die per targeted site, for nothing"):
    val b = withAdviser(board(extras = 1), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2,
      targets = Vector(DecisionOptionRef.Site(b.extras.head)))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered in a Raid, which targets no site"):
    val b = withAdviser(withEnemyAtOrigin(board()), card, Orientation.FaceUp)
    assert(commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
    assert(!commit(rules(winning), b, 2, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes one attack die per targeted site"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("a bandit defender applies it at a site it rules"):
    val base = board(extras = 1)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4,
      targets = Vector(DecisionOptionRef.Site(b.extras.head))).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
```

Create `WalledGardenSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Walled Garden: a free, site-only defender's plan for one defense die per
  * beast card at any site, offered only when its site is a Conquest target. */
class WalledGardenSuite extends munit.FunSuite:
  private val card = cardWith("denizen.walled-garden")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val beast = inert(Suit.Beast, 1).head
  private val beastEdifice =
    catalog.edifices.find(_.suit == Suit.Beast).get.id.value

  /** Sites other than the origin, in map order. */
  private def others(b: Board): Vector[SiteId] =
    b.ready.game.current.map.inPlay.filter(_ != b.origin)

  /** What the bandits' application of Walled Garden recorded as its effect:
    * the operation just before its marker. */
  private def banditEffect(run: Run): Option[CoreOperation] =
    val marker = ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)
    Option.when(run.ops.contains(marker))(run.ops.takeWhile(_ != marker).last)

  test("a defender adds one defense die per beast card at any site, " +
      "counting itself and cards nobody rules"):
    val base = againstPlayer(board())
    val sites = others(base)
    val b = withEdifice(withSiteCard(withSiteCard(base, base.origin, card),
      sites(0), beast), sites(1), beastEdifice, EdificeSide.Ruined)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is offered only when its site is a target"):
    val base = againstPlayer(board())
    val site = others(base).head
    val b = withSiteCard(on(base)(_.warbandsAt(site, base.other, 2)), site, card)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))
    assert(commit(rules(winning), b, 5,
      targets = Vector(DecisionOptionRef.Site(site))).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender applies it at a site it rules"):
    val base = board()
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      others(base).head, beast)
    assertEquals(banditEffect(commit(rules(winning), b, 2)),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
```

Create `BanditStandardSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Bandit Standard: a free attacker's plan from a faceup relic, for one attack
  * die per bandit warband in the region of the attacker's pawn, never against
  * bandits in a Conquest. */
class BanditStandardSuite extends munit.FunSuite:
  private val relic = relicWith("relic.bandit-standard")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  /** A site other than the origin, in the origin's region when `same` is set
    * and in another region otherwise. */
  private def site(b: Board, same: Boolean): SiteId =
    val map = b.ready.game.current.map
    map.inPlay.find(candidate => candidate != b.origin &&
      (map.regionOf(candidate) == map.regionOf(b.origin)) == same).get

  test("an attacker adds one attack die per bandit warband in their pawn's " +
      "region, for nothing"):
    val base = againstPlayer(board())
    val b = on(withRelic(base, relic))(_.bandits(site(base, same = true), 3)
      .bandits(site(base, same = false), 2))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered in a Conquest against bandits"):
    val b = withRelic(board(), relic)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the region holds no bandit"):
    val base = againstPlayer(board())
    val b = on(withRelic(base, relic))(_.bandits(site(base, same = false), 2))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is used in a Raid, counting the bandits at the pawn's site"):
    val b = withRelic(withEnemyAtOrigin(board()), relic)
    val run = commit(rules(winning), b, 3, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.CrackingGroundSuite oathdigital.gameplay.powers.campaign.WalledGardenSuite oathdigital.gameplay.powers.campaign.BanditStandardSuite"`
Expected: every test fails, because no plan is offered (`awaits` is false, or `pick` is refused with "was refused").

- [ ] **Step 3: Write Cracking Ground**

Create `CrackingGround.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Cracking Ground (card 71), a battle plan for either side: "± [attack-die]
  * per site targeted."
  *
  * It is free. An attacker adds one attack die per site the Conquest targets;
  * a defender removes as many. A Raid targets no site, so it is not offered in
  * one. A bandit defender applies it at a site it rules.
  */
final case class CrackingGround private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = CrackingGround.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = context.setup.targetSites.size
    if dice == 0 then None
    else context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Cracking Ground", context.side, dice), Vector.empty,
      Vector(PlanDice.effect(context.side, dice))))

object CrackingGround:
  val id: PowerId = PowerId("denizen.cracking-ground")

  def forCatalog(catalog: ExecutableCatalog): Option[CrackingGround] =
    CatalogCards.denizen(catalog, id).map(new CrackingGround(_))
```

- [ ] **Step 4: Write Walled Garden**

Create `WalledGarden.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Walled Garden (card 195), a defender's battle plan: "+[defense-die] per
  * [suit-beast] at any sites if this site is targeted."
  *
  * It is site-only and free, used by the ruler of its site, and offered only
  * when a Conquest targets that site; a Raid targets no site. It adds one
  * defense die per faceup beast denizen and per beast edifice, on either face,
  * at every site in play, whoever rules it. A facedown denizen has no suit. It
  * counts itself, so it adds at least one. A bandit defender applies it at a
  * site it rules.
  */
final case class WalledGarden private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = WalledGarden.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).collect {
      case source: CampaignPlanSource.SiteCard if context.targets(source.siteId) =>
        val dice = beasts(context)
        val noun = if dice == 1 then "defense die" else "defense dice"
        CampaignPlanOffer(source, s"Walled Garden: add $dice $noun",
          Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)))
    }

  /** The beast cards at every site in play. */
  private def beasts(context: PlanContext): Int =
    val map = context.ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(held, Orientation.FaceUp, _) =>
        catalog.suitOf(held).contains(Suit.Beast)
      case edifice: EdificeState => catalog.suitOf(edifice.id).contains(Suit.Beast)
      case _ => false
    }

object WalledGarden:
  val id: PowerId = PowerId("denizen.walled-garden")

  def forCatalog(catalog: ExecutableCatalog): Option[WalledGarden] =
    CatalogCards.denizen(catalog, id).map(new WalledGarden(_, catalog))
```

- [ ] **Step 5: Write Bandit Standard**

Create `BanditStandard.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Bandit Standard (relic R30), an attacker's battle plan: "+ [attack-die] for
  * each bandit in your region. This cannot be used while targeting sites ruled
  * by bandits."
  *
  * The relic must be faceup in the attacker's play area. It is free and adds
  * one attack die per bandit warband at the sites of the region the attacker's
  * pawn stands in. It is not offered in a Conquest against bandits, nor when
  * that region holds no bandit. A Raid targets no site, so it may be used in
  * one.
  */
final case class BanditStandard private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = BanditStandard.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val againstBandits = context.setup.kind == CampaignKind.Conquest &&
      context.setup.defender == CampaignDefender.Bandits
    val dice = bandits(context)
    if againstBandits || dice == 0 then None
    else context.relic(relicId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Bandit Standard", context.side, dice), Vector.empty,
      Vector(CampaignPlanEffect.AddAttackDice(dice))))

  /** The bandit warbands at the sites of the region of the attacker's pawn. */
  private def bandits(context: PlanContext): Int =
    val map = context.ready.game.current.map
    map.regionOf(context.setup.origin).toVector.flatMap(region =>
      map.inPlay.filter(site => map.regionOf(site).contains(region)))
      .flatMap(map.sites.get).map(_.forces match {
        case SiteForces.Occupied(ForceKind.Bandit, count) => count
        case _ => 0
      }).sum

object BanditStandard:
  val id: PowerId = PowerId("relic.bandit-standard")

  def forCatalog(catalog: ExecutableCatalog): Option[BanditStandard] =
    CatalogCards.relic(catalog, id).map(new BanditStandard(_))
```

- [ ] **Step 6: Register the three plans**

In `SimplePlans.scala`, extend the doc comment's list with "Cracking Ground, Walled Garden and Bandit Standard" (so it ends "…, Village Constable, Banner Breakers, Cracking Ground, Walled Garden and Bandit Standard, registered together."), and append:

```scala
      CrackingGround.forCatalog(catalog).toVector ++
      WalledGarden.forCatalog(catalog).toVector ++
      BanditStandard.forCatalog(catalog).toVector
```

after `BannerBreakers.forCatalog(catalog).toVector`, adding ` ++` to that line.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with the 11 new tests.

If `WalledGardenSuite`'s first test counts one die too many or too few, check which edifice `beastEdifice` found. Its ruined face must print no implemented power that changes the defense. If it does, pick the next beast edifice in the test (`catalog.edifices.filter(_.suit == Suit.Beast)`), and say so in the commit message.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/CrackingGround.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/WalledGarden.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BanditStandard.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CrackingGroundSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/WalledGardenSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BanditStandardSuite.scala
git commit -m "feat(powers): add Cracking Ground, Walled Garden and Bandit Standard"
```

---

### Task 3: Extra Provisions and Encirclement

These plans cost a favor placed, so a bandit defender never applies them.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/ExtraProvisions.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ExtraProvisionsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/EncirclementSuite.scala`

**Interfaces:**
- Consumes: `PlanDriver.Run.offers` (Task 1), `CampaignBattle.defenderForce(ready: ReadyGame, setup: CampaignSetup): Int`, `CampaignSetup.force`, `CampaignPlans.appliedMarker`.
- Produces: `ExtraProvisions` and `Encirclement`, each with `id` and `forCatalog`, registered in `SimplePlans`.

- [ ] **Step 1: Write the failing suites**

Create `ExtraProvisionsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Extra Provisions: a defender's plan, a favor placed for one defense die. */
class ExtraProvisionsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.extra-provisions")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  /** The other player defends the origin, holding Extra Provisions and
    * `favor` favor. */
  private def defender(favor: Int): Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, favor))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("a defender places a favor, off turn, for one defense die"):
    val b = defender(1)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.other).board.favor, 0)

  test("it is not offered to a defender with no favor"):
    val b = defender(0)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
```

Create `EncirclementSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Encirclement: a favor placed for two attack dice, added or removed, offered
  * only while its user's force is strictly larger than the enemy's. */
class EncirclementSuite extends munit.FunSuite:
  private val card = cardWith("denizen.encirclement")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val wrestlers = cardWith("denizen.wrestlers")

  /** The actor holds Encirclement and a favor. */
  private def attacker(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 1))

  /** The other player rules the origin with three warbands and holds
    * Encirclement and a favor. */
  private def defender: Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.warbandsAt(base.origin, base.other, 3).favor(base.other, 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker whose force is larger places a favor for two attack dice"):
    val b = attacker(board())
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))

  test("it is not offered when the forces are equal"):
    val b = attacker(board())
    assert(!commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("in a Raid the defender's force is their board"):
    val base = withEnemyAtOrigin(board())
    val b = attacker(on(base)(_.warbands(base.other, 1)))
    assert(commit(rules(winning), b, 2, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
    assert(!commit(rules(winning), b, 1, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender whose force is larger removes two attack dice"):
    val b = defender
    val run = commit(rules(winning), b, 2)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a plan chosen earlier in the window counts: a Wrestlers sacrifice " +
      "evens the forces"):
    val b = withAdviserFor(defender, defender.other, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(run.offers(b.actor, b.other, CampaignIds.defenderPlan, ref))
    val sacrificed = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(wrestlers)))
    // The title's defense keeps the window open, so this is a real re-offer.
    assert(awaits(sacrificed, b.other, CampaignIds.defenderPlan))
    assert(!sacrificed.offers(b.actor, b.other, CampaignIds.defenderPlan, ref))
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.ExtraProvisionsSuite oathdigital.gameplay.powers.campaign.EncirclementSuite"`
Expected: the offering tests fail, because no plan is offered. The "not offered" tests may already pass.

- [ ] **Step 3: Write Extra Provisions**

Create `ExtraProvisions.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Extra Provisions (card 48), a defender's battle plan: "[favor] +
  * [defense-die]".
  *
  * A favor is placed onto the card for one defense die. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class ExtraProvisions private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = ExtraProvisions.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Extra Provisions: add 1 defense die", Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))

object ExtraProvisions:
  val id: PowerId = PowerId("denizen.extra-provisions")

  def forCatalog(catalog: ExecutableCatalog): Option[ExtraProvisions] =
    CatalogCards.denizen(catalog, id).map(new ExtraProvisions(_))
```

- [ ] **Step 4: Write Encirclement**

Create `Encirclement.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Encirclement (card 124), a battle plan for either side: "[favor] ±2
  * [attack-die] if your force is larger than your enemy's."
  *
  * A favor is placed onto the card. An attacker adds two attack dice; a
  * defender removes two. It is offered only while its user's force is strictly
  * larger than the enemy's. The attacker's force is the warbands committed:
  * dice from a plan such as Brass Army are not force. The defender's force is
  * what the defense adds (`CampaignBattle.defenderForce`): the warbands at
  * every target in a Conquest, bandits included, or the defender's board in a
  * Raid. Both are read when the plan is offered, so a plan chosen earlier in
  * the same window, such as a Wrestlers sacrifice, counts. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class Encirclement private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Encirclement.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => larger(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Encirclement", context.side, Encirclement.Dice),
        Vector(CampaignPlanCost.Favor(1)),
        Vector(PlanDice.effect(context.side, Encirclement.Dice))))

  /** Whether the user's force is strictly larger than the enemy's. */
  private def larger(context: PlanContext): Boolean =
    val attacker = context.setup.force
    val defender = CampaignBattle.defenderForce(context.ready, context.setup)
    context.side match
      case CampaignPlanSide.Attacker => attacker > defender
      case CampaignPlanSide.Defender => defender > attacker

object Encirclement:
  val id: PowerId = PowerId("denizen.encirclement")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[Encirclement] =
    CatalogCards.denizen(catalog, id).map(new Encirclement(_))
```

- [ ] **Step 5: Register the two plans**

In `SimplePlans.scala`, extend the doc comment's list so it ends "…, Cracking Ground, Walled Garden, Bandit Standard, Extra Provisions and Encirclement, registered together.", and append after `BanditStandard.forCatalog(catalog).toVector` (adding ` ++` to that line):

```scala
      ExtraProvisions.forCatalog(catalog).toVector ++
      Encirclement.forCatalog(catalog).toVector
```

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with the 8 new tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/ExtraProvisions.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/ExtraProvisionsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/EncirclementSuite.scala
git commit -m "feat(powers): add Extra Provisions and Encirclement"
```

---

### Task 4: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
69 denizens and relics that copy an implemented power's shape and need no
engine change, in eight slices, plus a fix to Circlet of Command and Forgotten
Vault. Designed in the
[Catalog batch 3 design](superpowers/specs/2026-09-29-catalog-batch-3-design.md)
with its [rulings](superpowers/specs/2026-09-29-catalog-batch-3-rulings.md).
```

with:

```markdown
69 denizens and relics that copy an implemented power's shape and need no
engine change, in eight slices, plus a fix to Circlet of Command and Forgotten
Vault. Designed in the
[Catalog batch 3 design](superpowers/specs/2026-09-29-catalog-batch-3-design.md)
with its [rulings](superpowers/specs/2026-09-29-catalog-batch-3-rulings.md).

Slice 1a is done: the battle plans Cracking Ground, Walled Garden, Banner
Breakers, Extra Provisions, Village Constable, Encirclement, Bandit Standard
and Rival Khan. Slices 1b to 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 31 (12 in Task 1, 11 in Task 2, 8 in Task 3). From a baseline of 2161, that is 2192.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 1a"
```
