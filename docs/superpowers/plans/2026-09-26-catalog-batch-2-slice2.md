# Catalog Batch 2, Slice 2 (Battle Plans) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement nine battle plans: Fire Talkers (31), Nature Worship (175), Cracked Sage (83), Horse Archers (24), Storm Caller (167), Longbows (4), Black Sword (R35), Bag of Siegeworks (R37) and Hospital (149). This includes the ignored defense faces (N2) and the Campaign kill replacement (N3).

**Architecture:**

- Every card is a `BattlePlan` (see `campaign/BattlePlan.scala`).
  - Its `plan` builds a `CampaignPlanOffer` from where the card stands and the Campaign's setup.
  - A plan with a condition returns `None` when the condition fails.
- The five plans that only change dice register in `SimplePlans`: Longbows, Black Sword, Fire Talkers, Nature Worship and Cracked Sage.
  - They share a new `PlanDice` helper for the "±" rule: an attacker adds attack dice, a defender removes them.
- Horse Archers and Storm Caller use `later` at the Campaign's root, `CampaignActionEligibility`. There they discard the card through `PlanDiscard` and write "Discarded after the Campaign."
- Bag of Siegeworks (N2) uses `wrapping` at `CampaignDefenseResult`. Before the defender's force is added, it rewrites the defense roll's score with `ModifyRollOutcome`, the operation Outriders already uses. No new contribution is needed.
- Hospital (N3) uses `wrapping` at `CampaignLosses`:
  - It rewrites every `Kill` of its user's warbands into a `Move` to Hospital's site, inside the losses step and inside Sticky Fire's burn.
  - It raises its `priority` so it folds after Sticky Fire's wrapping, as Royal Stables does in slice 1.
  - It adds one note at the end.
- Horse Archers, Storm Caller, Bag of Siegeworks and Hospital register in `PlanRules`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change, so Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md` ("N2", "N3", "Log lines"), with the per-card rulings in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 2: battle plans"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Power log lines slice 4 (added effects and altered procedures) may run at the same time. It edits the Mercenaries, Sticky Fire, Outriders and Warning Signals files. This plan leaves those four files alone.
  - If slice 4 has merged when you start, check whether it added a discard-note helper to `PlanDiscard.scala`. If it did, add Task 3's helper beside it and do not duplicate it.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines"):
  - Horse Archers and Storm Caller: "Discarded after the Campaign."
  - Bag of Siegeworks: "Single shields ignored."
  - Hospital: "Placed {n} {Red} warband at {site} instead."
  - The other five plans write no line.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **The spec's "Verify at plan time" items:**
   - `ModifyRollOutcome` can express N2, so no new contribution is added.
   - A Campaign kills warbands in exactly three places:
     - the losses step, `CampaignBattle.losses`: the attacker's deaths, the Conquest target kills with the defender's returned half, and a Raid defender's half board;
     - Sticky Fire's burn, appended to the same window;
     - a Wrestlers sacrifice at the plan step, a `Sacrifice` operation.
2. **Nature Worship with no dice is not offered.** A faceup Nature Worship at a ruled site, with no faceup beast adviser, would add 0 dice. `AddAttackDice` requires a positive count, and a plan that does nothing is noise. If this is wrong, the plan is offered with no effect.
3. **Bandits and the discard.** A bandit defender applies every free plan it is offered (batch 1). It may therefore apply Horse Archers or Storm Caller standing at a site it rules. The card is then discarded too, since the ruling says "whoever won", and the attacker is the discard's acting player. If this is wrong, a bandit-used card stays in play.
4. **Hospital is never offered to a bandit defender.** It has no user whose warbands it could save.
5. **Hospital does nothing when its site is a Conquest target the attacker won.**
   - The spec says only that kills at that site stay kills.
   - Saving warbands from other targets onto a site that falls in the same Campaign would leave them where the attacker is placing its own. So none is replaced.
   - If this is wrong, only the other targets' kills should move.
6. **Hospital hooks only the losses window.** This follows the spec's "a later hook at the Campaign's loss windows". A Wrestlers sacrifice is a cost paid at the plan step, and it stays a kill. If this is wrong, a defender using both loses one warband it should keep.
7. **A Conquest defender's returned half comes from Hospital's site.**
   - The losses kill every warband at the targets and then return half of them from the supply to the defender's board.
   - Under Hospital, the kills become moves to Hospital's site, and the return moves from there instead of the supply. So Hospital's site keeps only the half that would have died.
   - Without this, the defender would gain warbands from the supply.
8. **Hospital's line covers nothing.**
   - The spec lists it as covering "the Killed line". With the kill replaced there is no such line: the Campaign's action line counts only `Kill`s in "lost N warbands", so the saved warbands drop out of it on their own.
   - Setting `covers` would instead hide the losses step's other lines.
9. **The Campaign's "Placed" line counts only moves onto a target.** An attacker's Hospital moves warbands from its board to a site, which `CampaignLines` would otherwise read as a Conquest placement. Task 5 fixes that.
10. **Bag of Siegeworks writes its line only when a single shield was rolled**, since otherwise nothing was ignored.
11. **Mercenaries keeps its own sign logic.** `PlanDice` is new, and moving Mercenaries onto it would touch a file log lines slice 4 edits. A later cleanup can move it.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDice.scala` | The "±" rule: effect and label by side. |
| Create `.../campaign/Longbows.scala`, `BlackSword.scala`, `FireTalkers.scala` | Dice plans (Task 1). |
| Create `.../campaign/NatureWorship.scala`, `CrackedSage.scala` | Dice plans that read advisers' suits (Task 2). |
| Modify `.../campaign/SimplePlans.scala` | Registers the five dice plans. |
| Modify `.../campaign/PlanDiscard.scala` | Adds the "Discarded after the Campaign." note and the end-of-Campaign discard. |
| Create `.../campaign/HorseArchers.scala`, `StormCaller.scala` | Plans discarded at the end (Task 3). |
| Create `.../campaign/BagOfSiegeworks.scala` | N2 (Task 4). |
| Modify `src/main/scala/oathdigital/gameplay/powers/CampaignPowers.scala` | Retires the `BagOfSiegeworks` stub. |
| Create `.../campaign/Hospital.scala` | N3 (Task 5). |
| Modify `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala` | "Placed" counts only moves onto a target. |
| Modify `.../campaign/PlanRules.scala` | Registers Horse Archers, Storm Caller, Bag of Siegeworks and Hospital. |
| Modify `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala` | Adds `inert(suit, count)`. |
| Create one suite per card in `src/test/scala/oathdigital/gameplay/powers/campaign/` | Walker-driven tests. |
| Modify `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala` | Rewords the test that used Bag of Siegeworks as an unimplemented power. |
| Modify `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala` | The "Placed" fix. |
| Modify `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala` | Pins the nine cards. |
| Modify `docs/ROADMAP.md` | Records the slice. |

`...` stands for `src/main/scala/oathdigital/gameplay/powers`.

---

### Task 1: Longbows, Black Sword and Fire Talkers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Longbows.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BlackSword.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/FireTalkers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/LongbowsSuite.scala`, `BlackSwordSuite.scala`, `FireTalkersSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen(id)`, `PlanContext.relic(id)`, `PlanContext.user`, `PlanContext.ready`, `BannerRules.holder(current, banner): Option[PlayerId]`, `CatalogCards.denizen/relic`.
- Test fixtures: `CampaignFixture` (`board`, `againstPlayer`, `withAdviser`, `withAdviserFor`, `withSiteCard`, `withRelic`, `withRelicFor`, `withSecrets`, `replacePlayer`, `cardWith`, `relicWith`, `rules`), `PlanDriver` (`commit`, `winning`, `losing`, `awaits`, `player`, `ready`, `Run.pick`, `Run.ops`, `Run.since`, `Run.offered`, `Run.finish`).
- Produces: `PlanDice.effect(side: CampaignPlanSide, dice: Int): CampaignPlanEffect` and `PlanDice.label(card: String, side: CampaignPlanSide, dice: Int): String`, both `private[campaign]`; `Longbows.id`, `BlackSword.id`, `FireTalkers.id`.

- [ ] **Step 1: Write the failing suites**

`LongbowsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Longbows: a free plan for one more attack die, or one fewer when its user
  * defends. */
class LongbowsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.longbows")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  test("an attacker adds one attack die, for nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a defender removes one attack die"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("a bandit defender applies it at a site the bandits rule"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 2)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == Longbows.id), 1)
```

`BlackSwordSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Black Sword: an attacker burns two secrets for five attack dice. */
class BlackSwordSuite extends munit.FunSuite:
  private val relic = relicWith("relic.black-sword")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  test("an attacker burns two secrets for five attack dice"):
    val b = withSecrets(withRelic(board(), relic), 2)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 5)))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("an attacker with one secret is not offered it"):
    val b = withSecrets(withRelic(board(), relic), 1)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender is not offered it"):
    val base = againstPlayer(board())
    val b = replacePlayer(withRelicFor(base, base.other, relic), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 2)))
    val run = commit(rules(winning), b, 2)
    // The title's defense may still be offered; Black Sword is not.
    assert(!awaits(run, b.other, CampaignIds.defenderPlan) ||
      !run.offered(b.actor).contains(ref))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == BlackSword.id), 1)
```

`FireTalkersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Fire Talkers: a secret placed for three attack dice, added or removed,
  * offered only while its user holds the Darkest Secret. */
class FireTalkersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.fire-talkers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  private def withDarkestSecret(b: Board, who: PlayerId): Board =
    b.copy(ready = b.ready.updateCurrent(current => current.copy(banners =
      current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(who))))))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def attacker: Board =
    val b = withSecrets(withAdviser(board(), card, Orientation.FaceUp), 1)
    withDarkestSecret(b, b.actor)

  test("an attacker holding the Darkest Secret places a secret for three attack dice"):
    val b = attacker
    val picked = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(0, 1)))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("it is not offered while another player holds the Darkest Secret"):
    val b = withDarkestSecret(attacker, attacker.other)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender pays off turn and removes three attack dice"):
    val base = againstPlayer(board())
    val armed = replacePlayer(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 1)))
    val b = withDarkestSecret(armed, base.other)
    val facedown = player(OathState.Ready(b.ready), b.other).board.faceDownSecrets
    val picked = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // Paid outside the defender's turn, the secret settles at once: it flips
    // facedown instead of resting on the card.
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.faceUpSecrets, 0)
    assertEquals(player(picked.state, b.other).board.faceDownSecrets, facedown + 1)

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == FireTalkers.id), 1)
```

- [ ] **Step 2: Run the suites and see them fail to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.LongbowsSuite oathdigital.gameplay.powers.campaign.BlackSwordSuite oathdigital.gameplay.powers.campaign.FireTalkersSuite"`
Expected: compilation fails with "Not found: Longbows" (and `BlackSword`, `FireTalkers`).

- [ ] **Step 3: Create `PlanDice.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The "±" of a battle plan (catalog batch 2 rulings, "Sign"): the side fixes
  * the sign. An attacker adds attack dice; a defender removes them from the
  * attack pool, which never goes below zero. */
private[campaign] object PlanDice:
  def effect(side: CampaignPlanSide, dice: Int): CampaignPlanEffect = side match
    case CampaignPlanSide.Attacker => CampaignPlanEffect.AddAttackDice(dice)
    case CampaignPlanSide.Defender => CampaignPlanEffect.RemoveAttackDice(dice)

  /** The offer's label, "Longbows: add 1 attack die". */
  def label(card: String, side: CampaignPlanSide, dice: Int): String =
    val noun = if dice == 1 then "attack die" else "attack dice"
    side match
      case CampaignPlanSide.Attacker => s"$card: add $dice $noun"
      case CampaignPlanSide.Defender => s"$card: remove $dice $noun"
```

- [ ] **Step 4: Create `Longbows.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Longbows (card 4), a battle plan for either side: "± [attack-die]".
  *
  * It is free. An attacker adds one attack die; a defender removes one. A
  * bandit defender applies it when it stands faceup at a site the bandits
  * rule, as it applies every free plan.
  */
final case class Longbows private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Longbows.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Longbows", context.side, Longbows.Dice), Vector.empty,
      Vector(PlanDice.effect(context.side, Longbows.Dice))))

object Longbows:
  val id: PowerId = PowerId("denizen.longbows")
  val Dice: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Longbows] =
    CatalogCards.denizen(catalog, id).map(new Longbows(_))
```

- [ ] **Step 5: Create `BlackSword.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Black Sword (relic R35), an attacker's battle plan: "[secret-burnt]
  * [secret-burnt] +5 [attack-die]".
  *
  * The relic must be faceup in the attacker's play area. Two faceup secrets
  * are burnt to the shared bank, and nothing is placed on the relic. An
  * attacker with fewer than two faceup secrets cannot pay, and the plan is
  * not offered.
  */
final case class BlackSword private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = BlackSword.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Black Sword: burn 2 secrets for 5 attack dice",
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddAttackDice(BlackSword.Dice))))

object BlackSword:
  val id: PowerId = PowerId("relic.black-sword")
  val Dice: Int = 5

  def forCatalog(catalog: ExecutableCatalog): Option[BlackSword] =
    CatalogCards.relic(catalog, id).map(new BlackSword(_))
```

- [ ] **Step 6: Create `FireTalkers.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Fire Talkers (card 31), a battle plan for either side: "[secret] ±3
  * [attack-die] if you hold the Darkest Secret."
  *
  * A secret is placed onto the card. An attacker adds three attack dice; a
  * defender removes three. It is offered only while its user holds the
  * Darkest Secret, so a bandit defender never uses it.
  */
final case class FireTalkers private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = FireTalkers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => holdsDarkestSecret(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Fire Talkers", context.side, FireTalkers.Dice),
        Vector(CampaignPlanCost.Secret(1)),
        Vector(PlanDice.effect(context.side, FireTalkers.Dice))))

  private def holdsDarkestSecret(context: PlanContext): Boolean =
    context.user.exists(user => BannerRules.holder(context.ready.game.current,
      Banner.DarkestSecret).contains(user))

object FireTalkers:
  val id: PowerId = PowerId("denizen.fire-talkers")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[FireTalkers] =
    CatalogCards.denizen(catalog, id).map(new FireTalkers(_))
```

- [ ] **Step 7: Register the three in `SimplePlans.scala`**

Replace the scaladoc and body with:

```scala
/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword and Fire
  * Talkers, registered together. A plan whose card is absent from `catalog` is
  * omitted.
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
      FireTalkers.forCatalog(catalog).toVector
```

- [ ] **Step 8: Run the suites and see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.LongbowsSuite oathdigital.gameplay.powers.campaign.BlackSwordSuite oathdigital.gameplay.powers.campaign.FireTalkersSuite oathdigital.gameplay.powers.campaign.MercenariesSuite"`
Expected: PASS, 12 tests in the new suites plus the unchanged Mercenaries suite.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanDice.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Longbows.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BlackSword.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/FireTalkers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/LongbowsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BlackSwordSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/FireTalkersSuite.scala
git commit -m "feat(powers): Longbows, Black Sword and Fire Talkers battle plans"
```

---

### Task 2: Nature Worship and Cracked Sage

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/NatureWorship.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/CrackedSage.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/NatureWorshipSuite.scala`, `CrackedSageSuite.scala`

**Interfaces:**
- Consumes: `PlanDice.effect`, `PlanDice.label` (Task 1); `PlanContext.setup: CampaignSetup` (fields `actor: PlayerId`, `defender: CampaignDefender`); `ExecutableCatalog.suitOf(id: CardId): Option[Suit]`; `PowerImplementationStatus.implemented(catalog): PowerId => Boolean`; `DenizenDefinition.suit`, `.handlers`.
- Produces: `NatureWorship.id`, `CrackedSage.id`; test helper `PlanDriver.inert(suit: Suit, count: Int): Vector[String]`.

- [ ] **Step 1: Add `inert` to `PlanDriver`**

Change the import `import oathdigital.gameplay.powers.WalkerPowerCatalog` to:

```scala
import oathdigital.gameplay.powers.{PowerImplementationStatus, WalkerPowerCatalog}
```

Add inside `object PlanDriver`, after `val losing`:

```scala
  /** `count` denizens of `suit` whose powers do nothing yet, to hold as
    * advisers without offering a plan of their own. */
  def inert(suit: Suit, count: Int): Vector[String] =
    val implemented = PowerImplementationStatus.implemented(catalog)
    catalog.denizens.filter(card => card.suit == suit &&
      card.handlers.forall(handler => !implemented(PowerId(handler))))
      .map(_.id.value).take(count)
```

- [ ] **Step 2: Write the failing suites**

`NatureWorshipSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Nature Worship: a secret placed for one attack die, added or removed, per
  * faceup beast adviser its user has, itself included. */
class NatureWorshipSuite extends munit.FunSuite:
  private val card = cardWith("denizen.nature-worship")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val beasts = inert(Suit.Beast, 2)

  private def advised(b: Board, who: PlayerId,
      cards: Vector[(String, Orientation)]): Board =
    cards.foldLeft(b) { case (done, (held, side)) =>
      withAdviserFor(done, who, held, side) }

  private def attacker(self: Orientation,
      others: Vector[(String, Orientation)]): Board =
    val base = board()
    withSecrets(advised(base, base.actor, (card -> self) +: others), 1)

  private def picked(b: Board): Run =
    commit(rules(winning), b, 2).pick(b.actor, CampaignIds.attackerPlan, ref)

  private def ruledSite(b: Board): SiteId =
    b.ready.game.current.map.inPlay.find(_ != b.origin).get

  test("it counts itself and every other faceup beast adviser"):
    val b = attacker(Orientation.FaceUp, beasts.map(_ -> Orientation.FaceUp))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))

  test("a facedown Nature Worship is revealed and counts itself"):
    val b = attacker(Orientation.FaceDown,
      Vector(beasts.head -> Orientation.FaceUp))
    val run = picked(b)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(player(run.state, b.actor).advisers.exists {
      case DenizenState(`id`, Orientation.FaceUp, _) => true
      case _ => false })

  test("a facedown beast adviser has no suit and does not count"):
    val b = attacker(Orientation.FaceUp,
      Vector(beasts.head -> Orientation.FaceDown))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site its user rules it counts the beast advisers but not itself"):
    val base = board()
    val site = ruledSite(base)
    val b = withSecrets(advised(withSiteCard(actorRules(base, site), site, card),
      base.actor, Vector(beasts.head -> Orientation.FaceUp)), 1)
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site with no beast adviser it would add nothing, so it is not offered"):
    val base = board()
    val site = ruledSite(base)
    val b = withSecrets(withSiteCard(actorRules(base, site), site, card), 1)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes one attack die per beast adviser"):
    val base = againstPlayer(board())
    val b = replacePlayer(advised(base, base.other, Vector(
      card -> Orientation.FaceUp, beasts.head -> Orientation.FaceUp)),
      base.other)(p => p.copy(board = p.board.copy(faceUpSecrets = 1)))
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == NatureWorship.id),
      1)
```

`CrackedSageSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Cracked Sage: a secret placed and a favor burnt for four attack dice,
  * added or removed, offered only when the enemy has a faceup arcane
  * adviser. */
class CrackedSageSuite extends munit.FunSuite:
  private val card = cardWith("denizen.cracked-sage")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val arcane = inert(Suit.Arcane, 1).head

  private def funded(b: Board, who: PlayerId): Board =
    replacePlayer(b, who)(p =>
      p.copy(board = p.board.copy(favor = 2, faceUpSecrets = 1)))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  /** The attacker holds the Sage; the defender, a player, holds an arcane
    * adviser on `enemy`'s face. */
  private def attacker(enemy: Orientation): Board =
    val base = againstPlayer(board())
    funded(withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.other, arcane, enemy), base.actor)

  test("an attacker places a secret and burns a favor for four attack dice"):
    val b = attacker(Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(0, 1)))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("it is not offered when the enemy's arcane adviser is facedown"):
    val b = attacker(Orientation.FaceDown)
    assert(!awaits(commit(rules(winning), b, 2), b.actor,
      CampaignIds.attackerPlan))

  test("it is never offered against bandits"):
    val base = board()
    val b = funded(withAdviser(base, card, Orientation.FaceUp), base.actor)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes four attack dice when the attacker has an arcane adviser"):
    val base = againstPlayer(board())
    val b = funded(withAdviserFor(withAdviser(base, arcane, Orientation.FaceUp),
      base.other, card, Orientation.FaceUp), base.other)
    val run = commit(rules(winning), b, 5)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == CrackedSage.id), 1)
```

- [ ] **Step 3: Run the suites and see them fail to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.NatureWorshipSuite oathdigital.gameplay.powers.campaign.CrackedSageSuite"`
Expected: compilation fails with "Not found: NatureWorship" and "Not found: CrackedSage".

- [ ] **Step 4: Create `NatureWorship.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Nature Worship (card 175), a battle plan for either side: "[secret] ±
  * [attack-die] per [suit-beast] adviser you have."
  *
  * A secret is placed onto the card. The dice are one per faceup beast
  * adviser its user has, since only a faceup card has a suit. It counts
  * itself when it is an adviser: a facedown one is revealed when chosen, so
  * it is counted whatever its face now, and the count does not change when
  * the plan reveals it. At a site it is not an adviser and counts only the
  * others. A plan that would add no die is not offered.
  */
final case class NatureWorship private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = NatureWorship.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).flatMap { source =>
      val itself = source match
        case _: CampaignPlanSource.Adviser => 1
        case _ => 0
      val dice = otherBeasts(context) + itself
      Option.when(dice > 0)(CampaignPlanOffer(source,
        PlanDice.label("Nature Worship", context.side, dice),
        Vector(CampaignPlanCost.Secret(1)),
        Vector(PlanDice.effect(context.side, dice))))
    }

  /** The user's faceup beast advisers other than this card. */
  private def otherBeasts(context: PlanContext): Int =
    context.ready.game.current.players.filter(p => context.user.contains(p.player))
      .flatMap(_.advisers).count {
        case DenizenState(held, Orientation.FaceUp, _) =>
          held != cardId && catalog.suitOf(held).contains(Suit.Beast)
        case _ => false
      }

object NatureWorship:
  val id: PowerId = PowerId("denizen.nature-worship")

  def forCatalog(catalog: ExecutableCatalog): Option[NatureWorship] =
    CatalogCards.denizen(catalog, id).map(new NatureWorship(_, catalog))
```

- [ ] **Step 5: Create `CrackedSage.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Cracked Sage (card 83), a battle plan for either side: "[secret]
  * [favor-burnt] ±4 [attack-die] if your enemy has an [suit-arcane] adviser."
  *
  * A secret is placed onto the card and a favor is burnt. An attacker adds
  * four attack dice; a defender removes four. It is offered only when the
  * enemy, the other side's player, has a faceup arcane adviser. Bandits hold
  * no advisers, so an attacker is never offered it against them.
  */
final case class CrackedSage private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = CrackedSage.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => enemyHasArcane(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Cracked Sage", context.side, CrackedSage.Dice),
        Vector(CampaignPlanCost.Secret(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(PlanDice.effect(context.side, CrackedSage.Dice))))

  /** The other side's player: the defender for an attacker's plan, when a
    * player defends, and the attacker for a defender's. */
  private def enemy(context: PlanContext): Option[PlayerId] = context.side match
    case CampaignPlanSide.Attacker => context.setup.defender match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    case CampaignPlanSide.Defender => Some(context.setup.actor)

  private def enemyHasArcane(context: PlanContext): Boolean =
    enemy(context).exists(player => context.ready.game.current.players
      .find(_.player == player).exists(_.advisers.exists {
        case DenizenState(held, Orientation.FaceUp, _) =>
          catalog.suitOf(held).contains(Suit.Arcane)
        case _ => false
      }))

object CrackedSage:
  val id: PowerId = PowerId("denizen.cracked-sage")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[CrackedSage] =
    CatalogCards.denizen(catalog, id).map(new CrackedSage(_, catalog))
```

- [ ] **Step 6: Register both in `SimplePlans.scala`**

Replace the scaladoc's card list and extend the body:

```scala
/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword, Fire
  * Talkers, Nature Worship and Cracked Sage, registered together. A plan whose
  * card is absent from `catalog` is omitted.
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
      CrackedSage.forCatalog(catalog).toVector
```

- [ ] **Step 7: Run the suites and see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, every campaign plan suite including the 12 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/NatureWorship.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/CrackedSage.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/NatureWorshipSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CrackedSageSuite.scala
git commit -m "feat(powers): Nature Worship and Cracked Sage battle plans"
```

---

### Task 3: Horse Archers and Storm Caller

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/HorseArchers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/StormCaller.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HorseArchersSuite.scala`, `StormCallerSuite.scala`

**Interfaces:**
- Consumes: `PlanDice` (Task 1); `PlanDiscard.denizen(catalog, user, card): Operation`; `PlanUse.user: Option[PlayerId]`, `PlanUse.actor: PlayerId`; `Note(power, build, covers)`; `NoteStates.previous: Option[(ReadyGame, ReadyGame)]`; `NoteText.said(id, keys, events)`, `NoteText.Said(key, text, covers)`.
- Produces: `PlanDiscard.discarded: NoteKey` (name `"discarded"`); `PlanDiscard.afterCampaign(catalog: ExecutableCatalog, power: PowerId, use: PlanUse, card: DenizenId): Vector[Operation]`; `HorseArchers.id`, `StormCaller.id`.

- [ ] **Step 1: Write the failing suites**

`HorseArchersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Horse Archers: a free plan for three attack dice, added or removed, and the
  * card is discarded after the Campaign whoever won. */
class HorseArchersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.horse-archers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(HorseArchers.id, Vector(PlanDiscard.discarded), run.events)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  test("an attacker adds three attack dice, for nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is discarded after a Campaign its user won, and says so"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("it is discarded after a Campaign its user lost"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(losing), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a defender removes three attack dice, and it is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(discarded(run.finish.state))

  test("a bandit defender applies it at its site, and it is discarded"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a Campaign that does not choose it leaves it in play and says nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2).finish
    assert(!discarded(done.state))
    assertEquals(lines(done), Vector.empty)

  test("the card is found in the catalog and registered once"):
    assertEquals(PlanRules.forCatalog(catalog).count(_.id == HorseArchers.id), 1)
```

`StormCallerSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Storm Caller: a defender's free plan for two defense dice, and the card is
  * discarded after the Campaign whoever won. */
class StormCallerSuite extends munit.FunSuite:
  private val card = cardWith("denizen.storm-caller")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def defending: Board =
    val base = againstPlayer(board())
    withAdviserFor(base, base.other, card, Orientation.FaceUp)

  test("a defender adds two defense dice, for nothing"):
    val b = defending
    val run = commit(rules(winning), b, 4)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is discarded after the Campaign, and says so"):
    val b = defending
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assert(discarded(done.state))
    assertEquals(NoteText.said(StormCaller.id, Vector(PlanDiscard.discarded),
      done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))

  test("an attacker is not offered it"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a bandit defender applies it at its site, and it is discarded"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(discarded(done.state))

  test("the card is found in the catalog and registered once"):
    assertEquals(PlanRules.forCatalog(catalog).count(_.id == StormCaller.id), 1)
```

- [ ] **Step 2: Run the suites and see them fail to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HorseArchersSuite oathdigital.gameplay.powers.campaign.StormCallerSuite"`
Expected: compilation fails with "Not found: HorseArchers", "Not found: StormCaller" and "value discarded is not a member of object PlanDiscard".

- [ ] **Step 3: Add the note and the end-of-Campaign discard to `PlanDiscard.scala`**

Append to the scaladoc of `object PlanDiscard`, before its closing `*/`:

```scala
  *
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line.
```

Add inside `object PlanDiscard`, after `def denizen`:

```scala
  /** "Discarded after the Campaign." */
  val discarded: NoteKey = NoteKey("discarded",
    Vector(NotePart.Text("Discarded after the Campaign.")))

  /** The standard discard of `card` once the Campaign has resolved, whoever
    * won, and `power`'s line. A bandit defender has no user, so the attacker
    * is the acting player of its discard. The line is written only when the
    * discard happened. */
  def afterCampaign(catalog: ExecutableCatalog, power: PowerId, use: PlanUse,
      card: DenizenId): Vector[Operation] = Vector(
    denizen(catalog, use.user.getOrElse(use.actor), card),
    Note(power, states => Option.when(states.previous.exists {
      case (before, after) => !inDiscard(before, card) && inDiscard(after, card)
    })(discarded(PowerSourceRef.Card(card)))))

  private def inDiscard(ready: ReadyGame, card: DenizenId): Boolean =
    ready.game.current.commonCards.regionalDiscards.values.exists(_.contains(card))
```

The file already imports `oathdigital.catalog.ExecutableCatalog` and `oathdigital.model._`.

- [ ] **Step 4: Create `HorseArchers.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Horse Archers (card 24), a battle plan for either side: "±3 [attack-die]
  * At end, discard Horse Archers."
  *
  * It is free. An attacker adds three attack dice; a defender removes three.
  * The card is discarded through the standard discard once the Campaign has
  * resolved, whoever won, as Warning Signals is. A bandit defender applies it
  * at a site it rules, and it is discarded then too.
  */
final case class HorseArchers private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = HorseArchers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Horse Archers", context.side, HorseArchers.Dice),
      Vector.empty, Vector(PlanDice.effect(context.side, HorseArchers.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object HorseArchers:
  val id: PowerId = PowerId("denizen.horse-archers")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[HorseArchers] =
    CatalogCards.denizen(catalog, id).map(new HorseArchers(_, catalog))
```

- [ ] **Step 5: Create `StormCaller.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Storm Caller (card 167), a defender's battle plan: "+2 [defense-die] At
  * end, discard Storm Caller."
  *
  * It is free and adds two defense dice. The card is discarded through the
  * standard discard once the Campaign has resolved, whoever won. A bandit
  * defender applies it at a site it rules, and it is discarded then too.
  */
final case class StormCaller private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = StormCaller.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Storm Caller: add 2 defense dice", Vector.empty,
      Vector(CampaignPlanEffect.AddDefenseDice(StormCaller.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object StormCaller:
  val id: PowerId = PowerId("denizen.storm-caller")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[StormCaller] =
    CatalogCards.denizen(catalog, id).map(new StormCaller(_, catalog))
```

- [ ] **Step 6: Register both in `PlanRules.scala`**

Replace the scaladoc and body with:

```scala
/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), and Horse Archers and Storm Caller (a
  * discard at the end). A power whose card is absent from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector
```

- [ ] **Step 7: Run the suites and see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, including the 12 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/HorseArchers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/StormCaller.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/HorseArchersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/StormCallerSuite.scala
git commit -m "feat(powers): Horse Archers and Storm Caller, discarded after the Campaign"
```

---

### Task 4: Bag of Siegeworks and the ignored defense faces (N2)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/CampaignPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala:174-190`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworksSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan.wrapping`; `PowerWindow.CampaignDefenseResult`, whose children are the `BuildOps` that write the dice score plus the defender's force (`CampaignBattle.defenseResultOps`); `CampaignIds.defensePool`; `ModifyRollOutcome(pool, skulls, score)`; `DefenseDieFace.score`; `CampaignFixture.raidBoard`.
- Produces: `BagOfSiegeworks.id`, `BagOfSiegeworks.ignored: NoteKey` (name `"ignored"`), `BagOfSiegeworks.score(faces: Vector[DefenseDieFace]): Int`.

- [ ] **Step 1: Write the failing suite**

`BagOfSiegeworksSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{CampaignPowers, NoteText}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Bag of Siegeworks: a secret placed, in a Conquest, so that every
  * single-shield defense die scores nothing. */
class BagOfSiegeworksSuite extends munit.FunSuite:
  private val relic = relicWith("relic.bag-of-siegeworks")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))
  private val line = NoteText.Said("ignored", "Single shields ignored.",
    covers = false)

  private val pattern = Vector(DefenseDieFace.OneShield,
    DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)

  /** Every attack die a sword; the defense dice cycle through `pattern`. */
  private val shields: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense =>
      Vector.tabulate(count)(index => pattern(index % pattern.size): DieFace))

  /** The attacker holds the Bag and a secret; two further bandit sites can be
    * targeted with the origin, so the defense rolls at least three dice. */
  private def holder: Board = withSecrets(withRelic(board(extras = 2), relic), 1)

  private def conquest(b: Board, dice: WalkerDice): Run =
    commit(rules(dice), b, 5, targets = b.extras.map(DecisionOptionRef.Site(_)))

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    BagOfSiegeworks.id, Vector(BagOfSiegeworks.ignored), run.events)

  test("a single shield scores nothing; two shields and doublers score as usual"):
    import DefenseDieFace._
    assertEquals(BagOfSiegeworks.score(Vector(Blank, OneShield, Doubler)), 0)
    assertEquals(BagOfSiegeworks.score(Vector(TwoShields, OneShield, Doubler)), 4)
    assertEquals(BagOfSiegeworks.score(Vector(OneShield, OneShield)), 0)

  test("an attacker places a secret on the relic"):
    val b = holder
    val run = conquest(b, shields)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("the defense is scored without its single shields, and it says so"):
    val b = holder
    val done = conquest(b, shields).pick(b.actor, CampaignIds.attackerPlan, ref)
      .finish
    val result = ready(done.state).game.current.lastCampaignResult.get
    assert(result.defenseFaces.contains(DefenseDieFace.OneShield),
      result.defenseFaces)
    val force = 2 * (1 + b.extras.size)
    assertEquals(result.defenseScore,
      BagOfSiegeworks.score(result.defenseFaces) + force)
    assert(result.defenseScore < DefenseDieFace.score(result.defenseFaces) + force)
    assertEquals(lines(done), Vector(line))

  test("without a single shield rolled nothing is said"):
    val b = holder
    val done = conquest(b, winning).pick(b.actor, CampaignIds.attackerPlan, ref)
      .finish
    assertEquals(lines(done), Vector.empty)

  test("a Campaign that does not choose it scores the single shields"):
    val b = holder
    val done = conquest(b, shields).finish
    val result = ready(done.state).game.current.lastCampaignResult.get
    assertEquals(result.defenseScore,
      DefenseDieFace.score(result.defenseFaces) + 2 * (1 + b.extras.size))
    assertEquals(lines(done), Vector.empty)

  test("a Raid targets no site, so it is not offered"):
    val (raid, _) = raidBoard()
    val b = withSecrets(withRelic(raid, relic), 1)
    val run = commit(rules(winning), b, 2, raid = true)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))

  test("the reviewed-catalog stub is retired and the plan registered once"):
    assertEquals(PlanRules.forCatalog(catalog).count(_.id == BagOfSiegeworks.id),
      1)
    assert(!CampaignPowers.powers.exists(_.id == BagOfSiegeworks.id))
```

- [ ] **Step 2: Run the suite and see it fail to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BagOfSiegeworksSuite"`
Expected: compilation fails with "Not found: BagOfSiegeworks". The name `CampaignPowers.BagOfSiegeworks` exists, but the suite refers to the new object in `powers.campaign`.

- [ ] **Step 3: Create `BagOfSiegeworks.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Bag of Siegeworks (relic R37), an attacker's battle plan: "[secret] If
  * you're targeting sites, ignore [defense-die] rolls with a single
  * [shield]."
  *
  * The relic must be faceup in the attacker's play area, and a secret is
  * placed onto it. It is offered only in a Conquest, because a Raid targets
  * no site. Once the defense is rolled, each single-shield die scores 0; two
  * shields and doublers score as usual, so Blank, OneShield and Doubler
  * score 0. The score is written again before the defender's force is added
  * to it (`CampaignDefenseResult`), so the recorded defense carries the
  * change.
  */
final case class BagOfSiegeworks private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = BagOfSiegeworks.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(BagOfSiegeworks.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Bag of Siegeworks: ignore single shields",
        Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> ((_, children) =>
      Vector[Operation](
        BuildOps((ready, _) => Right(rescored(ready))),
        Note(id, states => Option.when(
          BagOfSiegeworks.faces(states.now).contains(DefenseDieFace.OneShield))(
          BagOfSiegeworks.ignored(PowerSourceRef.Card(relicId))))) ++ children))

  /** The defense roll's score without its single shields. Nothing is written
    * when no single shield was rolled. */
  private def rescored(ready: ReadyGame): Vector[CoreOperation] =
    val faces = BagOfSiegeworks.faces(ready)
    if !faces.contains(DefenseDieFace.OneShield) then Vector.empty
    else Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(BagOfSiegeworks.score(faces))))

object BagOfSiegeworks:
  val id: PowerId = PowerId("relic.bag-of-siegeworks")

  /** "Single shields ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseDieFace.score(faces.filterNot(_ == DefenseDieFace.OneShield))

  private def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })

  def forCatalog(catalog: ExecutableCatalog): Option[BagOfSiegeworks] =
    CatalogCards.relic(catalog, id).map(new BagOfSiegeworks(_))
```

- [ ] **Step 4: Retire the stub in `CampaignPowers.scala`**

Delete these two lines:

```scala
  object BagOfSiegeworks extends ReviewedPower("relic.bag-of-siegeworks", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.CampaignAttackerBattlePlans)))
```

Then replace the `powers` value with:

```scala
  val powers: Vector[Power] = Vector(VowOfPeace, Outriders, BrassArmyCampaign,
    Watchdog)
```

- [ ] **Step 5: Register it in `PlanRules.scala`**

Replace the scaladoc and body with:

```scala
/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers and Storm Caller (a discard
  * at the end), and Bag of Siegeworks (the defense scored again). A power whose
  * card is absent from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector
```

- [ ] **Step 6: Reword the Campaign test that used the Bag as an unimplemented power**

In `CampaignProcedureSuite.scala`, the test `"a held Campaign power the engine does not run does not block the start"` holds a faceup Bag of Siegeworks. The Bag now runs. Rename the test to `"a held battle-plan relic does not block the start: its plan is chosen at the plan step"`. Then replace its closing comment and assertion:

```scala
    // What gets recorded is whatever the reviewed catalog lists for the
    // Campaign modifier window as an unimplemented automatic rule; Bag of
    // Siegeworks is a player-selected plan, which the catalog does not list.
    assert(start(b.copy(ready = holding)).isRight)
```

with:

```scala
    // Bag of Siegeworks is a battle plan, offered at the plan step, so
    // holding it changes nothing at the start.
    assert(start(b.copy(ready = holding)).isRight)
```

- [ ] **Step 7: Run the suites and see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.RuleResolutionSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS, including the 7 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala \
  src/main/scala/oathdigital/gameplay/powers/CampaignPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworksSuite.scala
git commit -m "feat(powers): Bag of Siegeworks ignores single-shield defense dice"
```

---

### Task 5: Hospital and the Campaign kill replacement (N3)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala` (the `placed` collector in `gains`)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HospitalSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`

**Interfaces:**
- Consumes:
  - `BattlePlan.wrapping`.
  - `ContributingPower.priority: Int` (default 0; fold order `(priority, source.stableKey, id)`).
  - `PlanUse.user`, `PlanUse.result: Option[CampaignResult]`, `PlanUse.ready` (the state the window is folded with, before the losses run).
  - `SiteRulers.siteOf(ready, card): Option[SiteId]` and `SiteRulers.rulerOf(ready, site): Option[SiteRuler]`.
  - `PlayerFacts.forceKind(ready, player): Either[OathViolation, ForceKind]`.
  - `StickyFire.decisionId`, `StickyFire.yes`, `CampaignPlans.appliedMarker(ref)`.
- Produces: `Hospital.id`, `Hospital.placed: NoteKey` (name `"placed"`), `Hospital.Priority = 1`.

Background:
- `CampaignOutcome.steps` puts the whole losses step in one `BuildOps` under `CampaignLosses`.
- Sticky Fire wraps the same window: it asks first, then runs the losses, then appends its own `BuildOps` burn.
- Hospital must see the burn, so it folds after Sticky Fire. Both sources are `game:` keys, and `denizen.hospital` sorts before `relic.sticky-fire`, so Hospital raises its priority to 1.
- The Game Log writes no detail line for a warband move inside a Campaign. The Campaign's action line counts only `Kill`s as "lost", so the saved warbands drop out of it (ruling 8).

- [ ] **Step 1: Write the failing suite**

`HospitalSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Hospital: once chosen, each of its user's warbands the Campaign would kill
  * is placed on Hospital's site instead, while the user still rules it. */
class HospitalSuite extends munit.FunSuite:
  private val card = cardWith("denizen.hospital")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def kind(b: Board, who: PlayerId): ForceKind =
    ForceKind.Exile(b.player(who).lineage)

  /** `who` rules `site` with two warbands, and Hospital stands there. */
  private def hospitalFor(b: Board, who: PlayerId, site: SiteId): Board =
    val held = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(forces =
          SiteForces.Occupied(kind(b, who), 2)))))))
    withSiteCard(held, site, card)

  /** A site that is neither the origin nor a bandit-ruled extra. */
  private def spare(b: Board): SiteId = b.ready.game.current.map.inPlay
    .find(site => site != b.origin && !b.extras.contains(site)).get

  private def forces(state: OathState, site: SiteId): SiteForces =
    ready(state).game.current.map.sites(site).forces

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Hospital.id, Vector(Hospital.placed), run.events)

  private def placed(amount: Int, who: PlayerId, site: SiteId): NoteText.Said =
    val noun = if amount == 1 then "warband" else "warbands"
    NoteText.Said("placed",
      s"Placed $amount ${who.value} $noun at ${site.value} instead.",
      covers = false)

  test("a defeated attacker's dead are placed on Hospital's site instead"):
    val base = board()
    val site = spare(base)
    val b = hospitalFor(base, base.actor, site)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    // Half of the four survivors die: they go to Hospital's site, not the bank.
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.actor), 4))
    assertEquals(player(done.state, b.actor).board.warbands, 3)
    assert(!done.ops.exists {
      case Kill(Piece.Warbands(killed, _), _) => killed == kind(b, b.actor)
      case _ => false })
    assertEquals(lines(done), Vector(placed(2, b.actor, site)))

  test("a defeated defender keeps the returned half on its board, and the rest go to Hospital's site"):
    val base = againstPlayer(board())
    val site = spare(base)
    val b = hospitalFor(base, base.other, site)
    val before = b.player(b.other).board.warbands
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // Two warbands held the origin: one returns to the board as usual, and
    // the one that would have died is placed on Hospital's site.
    assertEquals(player(done.state, b.other).board.warbands, before + 1)
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.other), 3))
    assertEquals(lines(done), Vector(placed(1, b.other, site)))

  test("at a Conquest target the attacker won, every kill stays a kill"):
    val base = againstPlayer(board())
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assert(done.ops.contains(Kill(Piece.Warbands(kind(b, b.other), 2),
      PositionedLocation(Location.Site(b.origin)))))
    assertEquals(lines(done), Vector.empty)

  test("the warbands Sticky Fire kills are placed on Hospital's site too"):
    val base = againstPlayer(board())
    val site = spare(base)
    val fire = relicWith("relic.sticky-fire")
    val b = withRelicFor(hospitalFor(base, base.actor, site), base.other, fire)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, DecisionOptionRef.Relic(RelicId(fire)))
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.other, StickyFire.decisionId, StickyFire.yes).finish
    assertEquals(winner(done), Some(false))
    // The two the defeat kills, then the three Sticky Fire kills from the
    // board, all go to Hospital's site.
    assertEquals(player(done.state, b.actor).board.warbands, 0)
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.actor), 7))
    assertEquals(lines(done), Vector(placed(5, b.actor, site)))

  test("a player who does not rule its site is not offered it"):
    val base = board()
    val b = withSiteCard(base, spare(base), card)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a bandit defender does not use it"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!ready(run.state).game.current.rollPools.contains(
      CampaignPlans.appliedMarker(ref)))

  test("the card is found in the catalog and registered once"):
    assertEquals(PlanRules.forCatalog(catalog).count(_.id == Hospital.id), 1)
```

- [ ] **Step 2: Run the suite and see it fail to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HospitalSuite"`
Expected: compilation fails with "Not found: Hospital".

- [ ] **Step 3: Create `Hospital.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts}
import oathdigital.model._

/** Hospital (card 149, site-only), a battle plan for either side: "If any of
  * your warbands would be killed, place them on Hospital's site instead if
  * you still rule it."
  *
  * It is free and used by the player who rules Hospital's site; a bandit
  * defender never uses it. Once chosen, every kill of its user's warbands in
  * the Campaign's losses (`CampaignLosses`) becomes a move to Hospital's
  * site. That includes the kills Sticky Fire adds there, so Hospital folds
  * after every plan at the default priority. A Conquest defender's returned
  * half comes back from Hospital's site instead of the supply, so the site
  * keeps only the warbands that would have died.
  *
  * When Hospital's site is a Conquest target the attacker won, its user loses
  * the site in this Campaign, and no kill is replaced. A sacrifice paid at
  * the plan step, such as Wrestlers', is a cost and stays a kill.
  *
  * It writes one line after the losses, naming how many warbands it placed.
  */
final case class Hospital private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Hospital.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = Hospital.Priority
  override def noteKeys: Vector[NoteKey] = Vector(Hospital.placed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(_ => context.denizen(cardId)).map(source =>
      CampaignPlanOffer(source,
        "Hospital: place your warbands that would be killed on its site",
        Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) => (for
      user <- use.user
      result <- use.result
      site <- SiteRulers.siteOf(use.ready, cardId)
      if SiteRulers.rulerOf(use.ready, site).contains(SiteRuler.Player(user))
      if !conquered(result, site)
      kind <- PlayerFacts.forceKind(use.ready, user).toOption
    yield
      val ward = Ward(user, kind, site)
      val before = Hospital.at(use.ready, site, kind)
      losses.map(ward.rewrite) :+ Note(id, ward.note(_, before))
    ).getOrElse(losses)))

  /** Whether `site` is a Conquest target the attacker won. */
  private def conquered(result: CampaignResult, site: SiteId): Boolean =
    result.attackerWins && result.kind == CampaignKind.Conquest &&
      result.targetSites.contains(site)

  /** The kills of `user`'s warbands, of force `kind`, turned into moves to
    * Hospital's `site`. */
  private final class Ward(val user: PlayerId, val kind: ForceKind,
      val site: SiteId):
    private val ward = PositionedLocation(Location.Site(site))

    def rewrite(operation: Operation): Operation = operation match
      case ops: BuildOps => ops.copy(build = (ready, pending) =>
        ops.build(ready, pending).map(_.map(replaced)))
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(rewrite))
      case branch: Branch => Branch((ready, pending) =>
        branch.select(ready, pending).map(rewrite))
      case core: CoreOperation => replaced(core)
      case other => other

    private def replaced(operation: CoreOperation): CoreOperation =
      operation match
        case Kill(warbands @ Piece.Warbands(`kind`, _), from)
            if from.location != ward.location =>
          Move(warbands, from, ward)
        case Move(warbands @ Piece.Warbands(`kind`, _),
            PositionedLocation(Location.WarbandBank(_), _),
            to @ PositionedLocation(Location.PlayArea(`user`), _), None) =>
          Move(warbands, ward, to)
        case other => other

    /** How many warbands the losses left on the site beyond `before`. */
    def note(states: NoteStates, before: Int): Option[PowerNote] =
      val count = Hospital.at(states.now, site, kind) - before
      Option.when(count > 0)(Hospital.placed(PowerSourceRef.Card(cardId),
        NoteArg.Number(count), NoteArg.Player(user), NoteArg.Site(site)))

object Hospital:
  val id: PowerId = PowerId("denizen.hospital")

  /** Folds after every plan at the default priority 0, so its rewrite sees
    * the kills Sticky Fire adds to the losses. */
  val Priority: Int = 1

  /** "Placed {n} {Red} warband at {site} instead." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(" instead.")))

  /** The warbands of force `kind` at `site`. */
  private[campaign] def at(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count
    }.getOrElse(0)

  def forCatalog(catalog: ExecutableCatalog): Option[Hospital] =
    CatalogCards.denizen(catalog, id).map(new Hospital(_))
```

If `-Wunused` flags `Ward`'s `val` parameters as unused public members, drop the `val` keywords. Backticked patterns accept plain constructor parameters too.

- [ ] **Step 4: Register it in `PlanRules.scala`**

Replace the scaladoc and body with:

```scala
/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers and Storm Caller (a discard
  * at the end), Bag of Siegeworks (the defense scored again) and Hospital (the
  * losses' kills replaced). A power whose card is absent from `catalog` is
  * omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suite and see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, including the 7 Hospital tests.

If the Sticky Fire test places 2 and not 5, Hospital folded before Sticky Fire. Check that `priority` is overridden and that `BattlePlan` does not mark `priority` final.

- [ ] **Step 6: Write the failing log test**

Add to `GameLogRareLinesSuite.scala`, after the test `"a defeated attacker's losses have no subject"`:

```scala
  test("a warband the attacker moves to a site it does not target is not placed"):
    val elsewhere = ready.game.current.map.inPlay.find(_ != site).get
    val warbands = ForceKind.Exile(lineage(actor))
    val result = CampaignResult(actor, CampaignKind.Conquest,
      CampaignDefender.Bandits, Vector(site), Vector.empty, 3, Vector.empty,
      3, 0, 0, Vector.empty, 1, attackerWins = true)
    val lines = posted(ActionRef.Campaign, RecordCampaignResult(result),
      Move(Piece.Warbands(warbands, 1),
        PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(elsewhere))),
      Move(Piece.Warbands(warbands, 2),
        PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(site))))
    assert(lines.contains(s"Placed 2 warbands on ${presentation.siteLabel(site)}"),
      lines)
```

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogRareLinesSuite"`
Expected: FAIL. The line reads "Placed 1 warband on … and 2 warbands on …".

- [ ] **Step 7: Count only moves onto a target in `CampaignLines.scala`**

In `gains`, the Conquest branch, change the guard of the `placed` collector from:

```scala
              if player == result.attacker =>
```

to:

```scala
              if player == result.attacker &&
                result.targetSites.contains(site) =>
```

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/application/gamelog/CampaignLines.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/HospitalSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala
git commit -m "feat(powers): Hospital places its user's killed warbands on its site"
```

---

### Task 6: Status pins, gates and roadmap

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Pin the nine cards**

Add to `PowerImplementationStatusSuite.scala`, after the test `"catalog batch 2's modifiers and restrictions are implemented"`:

```scala
  test("catalog batch 2's battle plans are implemented"):
    Vector("denizen.fire-talkers", "denizen.nature-worship",
      "denizen.cracked-sage", "denizen.horse-archers", "denizen.storm-caller",
      "denizen.longbows", "relic.black-sword", "relic.bag-of-siegeworks",
      "denizen.hospital").foreach(id => assert(implemented(PowerId(id)), id))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 2" section, replace:

```markdown
Slice 1 is done: Animal Playmates, Birdsong, Royal Stables and Forgotten
Vault, with the Travel Supply reduction. Slices 2 to 5 remain: battle plans,
actions on yourself, actions on others, then triggers and when-played powers.
```

with:

```markdown
Slice 1 is done: Animal Playmates, Birdsong, Royal Stables and Forgotten
Vault, with the Travel Supply reduction. Slice 2 is done: the battle plans
Fire Talkers, Nature Worship, Cracked Sage, Horse Archers, Storm Caller,
Longbows, Black Sword, Bag of Siegeworks and Hospital, with ignored defense
faces and the Campaign kill replacement. Slices 3 to 5 remain: actions on
yourself, actions on others, then triggers and when-played powers.
```

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus the new tests: 50 across the nine card suites, 1 log test and 1 status test, so baseline + 52.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 2"
```
