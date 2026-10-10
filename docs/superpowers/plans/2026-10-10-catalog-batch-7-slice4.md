# Catalog Batch 7, Slice 4 (Battle Rewrites) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement six battle plans that rewrite how a Campaign's battle is resolved: Careful Plans (254), Weeping Banner (R42), Zealots (87), Bear Traps (3), Signal Trees (217) and Hearts and Minds (138).

**Architecture:**

- **A reordered battle.** Careful Plans' rewrite at the Campaign's root (`CampaignActionEligibility`) folds `Late`, chosen or not. It finds the battle's five steps by the windows of the two result steps and puts them in one `Branch`, so the root has the same shape on every fold. The branch reads at walk time whether the plan was chosen, and if so walks the defense roll and its result first.
- **Skull kills.** `AttackRules` gains `skullKills`, the warbands each skull kills, 2 with Weeping Banner. The attack scorer caps the kills by the force and scores a skull face only when all its kills are paid.
- **The sacrifice's worth.** Zealots records what each sacrificed warband adds in a Campaign pool, `campaign.sacrifice-worth`, when it is applied. The sacrifice question, `CampaignBattle.result` and the Game Log read that pool. `CampaignResult` records the worth, and its attack total counts it.
- **A warning on a battle plan.** `CampaignPlanOffer.warning` is shown beside an option its user can pay for, which stays choosable. Zealots warns when its condition fails.
- **Plans inside the plan window.** Bear Traps removes an attack die and kills a warband from the attacker's board. Signal Trees moves the top three cards of its user's region's discard pile to the next region's pile, reveals each there, and adds or removes two attack dice per Beast. Both read the board when the walk reaches them.
- **A plan that decides the battle.** Hearts and Minds reuses `VictoriousNow` in a new silent form, and is discarded after the Campaign unless its user holds the People's Favor.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`: "Goal and scope", "The bar", "Catalog corrections", "Slice 4: Battle rewrites", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`: "Rules that apply to the whole batch" and the six slice 4 entries.

Task 1 amends both with this plan's rulings, so read them again after Task 1.

**Starts from:** `main` with catalog batch 7 slices 1 and 2 merged. Slice 4 does not need slice 3, and the two may run side by side. If slice 3 merges into `main` while you work, merge `main` into your branch and keep both sides of every conflict. The files both slices change are the design's Status paragraph, `docs/ROADMAP.md`, `PlanRules.scala`, the `powers.campaign` import in `NewFoundations.scala`, `HoldingDiscordDenizens.scala` and `CampaignPlanOffer` in `CampaignTypes.scala`.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding catalog file, remove any import that file no longer uses. `HoldingHearthDenizens.scala` loses its only `SiteOnly` card in Task 7.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - Plans: `CarefulPlans`, `WeepingBanner`, `Zealots`, `BearTraps` (case objects); `SignalTrees` and `HeartsAndMinds` (final case classes with `forCatalog`).
  - `AttackRules.skullKills: Int = 1`.
  - `CampaignIds.sacrificeWorth: PoolKey = PoolKey("campaign.sacrifice-worth")`.
  - `CampaignBattle.sacrificeWorth(ready)` and `CampaignBattle.sacrificeHeading(max, worth = 1)`.
  - `CampaignResult.sacrificeWorth: Int = 1`, wire field `"sacrificeWorth"`.
  - `CampaignPlanOffer.warning: Option[OptionReason] = None`.
  - `VictoriousNow.silent`.
  - Weeping Banner's power id: `relic.weeping-banner`.
- **Texts, exactly:**
  - Zealots' warning: "Has no effect unless defending force is larger than yours", rendered after the card's name as "Zealots: …".
  - The sacrifice heading with Zealots: "Sacrifice up to 2 warbands for three attack each".
  - Lines:
    - Bear Traps: "Killed 1 warband on {Red}'s board.", only when a warband died.
    - Signal Trees: "Revealed and discarded {cards}; added 4 attack dice." ("removed" for a defender), "Revealed and discarded {cards}." when no Beast is revealed, and "Nothing was revealed." for an empty pile.
    - Hearts and Minds: "Discarded after the Campaign.", only when discarded.
    - Careful Plans, Weeping Banner and Zealots write no line of their own.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 24 server tests and no frontend test.
  - It changes one existing test: `CampaignBattleSuite`'s sacrifice heading test gains an assertion (Task 4).
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **Careful Plans reshapes every Campaign.** A plan's rewrite must give the root the same shape on every fold, so the battle's five steps always sit in one `Branch`, whether or not Careful Plans is in play. The steps are found by the windows of the attack and defense results. If the root's shape ever differs, the rewrite leaves it alone and Careful Plans' suite fails. That is the point to fall back to an order hook in the Campaign tree, which needs the product owner. A game parked in a Campaign's battle when the server updates may not resume, because the parked node ids after the plan step change once.
2. **The sacrifice's worth is recorded, not scored.** The design put it in `AttackRules`. But `AttackRules` is a pure function of the chosen plans, Zealots' condition is fixed when it is chosen, and nothing under `gameplay/actions` may import the powers. So Zealots records a worth of three in the pool `campaign.sacrifice-worth` when it is applied, and the engine reads the pool. `CampaignResult.sacrificeWorth` (1 by default) records it, and `attackTotal` is the attack plus the sacrificed warbands times their worth. A stored result without the field reads 1.
3. **A plan may carry a warning.** `CampaignPlanOffer.warning` is shown beside an option its user can pay for, in the option's warning state. Zealots' reads "Zealots: Has no effect unless defending force is larger than yours" while the defending force is not larger.
4. **The attack line counts losses.** The Game Log's "Attack: 3 with 3 skulls" counts the warbands the skulls kill, as the sacrifice question's "3 skull losses" does. With Weeping Banner that can exceed the skulls rolled. This plan leaves the words as they are; the alternative is "with 3 skull losses" everywhere.
5. **Bear Traps kills outside the force.** The committed force stays on the attacker's board until the losses, so "not in the committed force" means a warband beyond it. When every warband on the board is committed, nothing dies and no line is written.
6. **Signal Trees writes one line.** The ruling's "Revealed …; 4 attack dice." followed by a discard line becomes one line that covers both, as Call for Help's does: "Revealed and discarded Wolves, Tavern Songs and Sacred Ground; added 4 attack dice." ("removed" for a defender). With no Beast it reads "Revealed and discarded …."; with an empty pile, "Nothing was revealed.". It names no player, as slice 3's Mountain Giant line names none. Each card is revealed on the next region's pile after it moves there, because a card entering a pile is forgotten. No plan option shows a dice change, so the card's text is the only preview.
7. **Where they register.** Careful Plans and Hearts and Minds join `PlanRewrites`; Weeping Banner joins `PlanRules`; Zealots, Bear Traps and Signal Trees join `SimplePlans`. `VictoriousNow` gains a silent form for Hearts and Minds.

## File map

**Careful Plans (Task 2):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/CarefulPlans.scala`: new. `CarefulPlansCard` moves out of `catalog/holding/HoldingOrderDenizens.scala`.
- Registration: `PlanRewrites.scala`, and the import in `NewFoundations.scala`.
- Tests: new `CarefulPlansSuite`.

**Weeping Banner (Task 3):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`: `skullKills`, and Weeping Banner's change.
- `src/main/scala/oathdigital/gameplay/powers/campaign/WeepingBanner.scala`: new. `WeepingBannerCard` moves out of `catalog/holding/HoldingRelics.scala`, with its power id renamed.
- `docs/catalog/reference/new-foundations-component-catalog.json`: the renamed id.
- Registration: `PlanRules.scala`, and the import in `NewFoundations.scala`.
- Tests: `AttackScoreSuite` gains one test; new `WeepingBannerSuite`.

**Zealots (Task 4):**
- `src/main/scala/oathdigital/model/CampaignTypes.scala`: `CampaignPlanOffer.warning`, `CampaignResult.sacrificeWorth`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`: `CampaignIds.sacrificeWorth`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`: reads the worth.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`: the heading.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`: shows the warning.
- `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`: the wire field.
- `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`: the sacrifice line.
- `src/main/scala/oathdigital/gameplay/powers/campaign/Zealots.scala`: new. `ZealotsCard` moves out of `catalog/holding/HoldingDiscordDenizens.scala`.
- Registration: `SimplePlans.scala`, and the import in `NewFoundations.scala`.
- Tests: new `ZealotsSuite`; `CampaignResultCodecSuite` and `GameLogCampaignSuite` gain one test each; `CampaignBattleSuite` gains an assertion.

**Bear Traps (Task 5):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/BearTraps.scala`: new. `BearTrapsCard` moves out of `catalog/holding/HoldingOrderDenizens.scala`.
- Registration: `SimplePlans.scala`, and the import in `NewFoundations.scala`.
- Tests: new `BearTrapsSuite`.

**Signal Trees (Task 6):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala`: new. `SignalTreesCard` moves out of `catalog/holding/HoldingBeastDenizens.scala`.
- Registration: `SimplePlans.scala`, and the import in `NewFoundations.scala`.
- Tests: new `SignalTreesSuite`.

**Hearts and Minds (Task 7):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala`: `silent`.
- `src/main/scala/oathdigital/gameplay/powers/campaign/HeartsAndMinds.scala`: new. `HeartsAndMindsCard` moves out of `catalog/holding/HoldingHearthDenizens.scala`.
- Registration: `PlanRewrites.scala`, and the import in `NewFoundations.scala`.
- Tests: new `HeartsAndMindsSuite`.

**Roadmap (Task 8):** `docs/ROADMAP.md`, and the design's status.

Each registry's doc comment lists its plans by name; add each new plan's name there as you register it.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`

- [ ] **Step 1: Amend the design**

1. In the **Status** paragraph, add that slice 4 is planned, keeping whatever it says of slice 3. On `main` without slice 3 it becomes `Slices 1 and 2 are done and slice 4 is planned;`. Re-wrap the paragraph to 78 characters.

2. In "The bar", replace the two bullets that begin `- **Attack scoring fields** (slice 4).` and `- **A reordered battle** (slice 4).` with:

```markdown
- **Attack scoring and the sacrifice's worth** (slice 4). `AttackRules`
  gains the warbands a skull kills (Weeping Banner). A plan records what
  each sacrificed warband adds in the Campaign pool
  `campaign.sacrifice-worth` when it is applied (Zealots). The sacrifice
  question, `CampaignBattle.result` and the Game Log read the pool, and
  `CampaignResult` records the worth, which its attack total counts.
- **A reordered battle** (slice 4). Careful Plans' rewrite at
  `CampaignActionEligibility` puts the battle's five steps in one `Branch`,
  chosen or not. When the plan was chosen, the branch walks the defense
  roll and its result first.
- **A warning on a battle plan** (slice 4). `CampaignPlanOffer.warning` is
  shown beside an option its user can pay for, which stays choosable
  (Zealots).
```

3. In the paragraph after "The bar"'s bullets, replace `The serialization codec gains `Reroll` and `SetDieFaces`.` with `The serialization codec gains `Reroll`, `SetDieFaces` and the sacrifice's worth in a recorded `CampaignResult`.`

4. At the end of "## Slice 4: Battle rewrites", after the Hearts and Minds bullet, add:

```markdown
### Plan-time rulings

- **Careful Plans reshapes every Campaign.** A plan's rewrite gives the
  root the same shape on every fold, so the battle's five steps always sit
  in one `Branch`, found by the windows of the attack and defense results.
  If the root's shape differs, the rewrite leaves it alone and Careful
  Plans' suite fails; the fallback order hook then needs the product owner.
  A game parked in a battle when the server updates may not resume.
- **The sacrifice's worth is recorded.** `AttackRules` is a pure function
  of the chosen plans, and Zealots' condition is fixed when it is chosen,
  so Zealots records a worth of three in `campaign.sacrifice-worth` when it
  is applied. `CampaignResult.sacrificeWorth` (1 by default) records it,
  and `attackTotal` counts it. A stored result without it reads 1.
- **A plan may carry a warning**, shown beside an option its user can pay
  for. Zealots' reads "Zealots: Has no effect unless defending force is
  larger than yours".
- **The attack line counts losses.** "Attack: 3 with 3 skulls" counts the
  warbands the skulls kill, so with Weeping Banner it can exceed the skulls
  rolled. The words stay as they are.
- **Bear Traps kills outside the force.** The committed force stays on
  the board until the losses, so Bear Traps kills a warband beyond it. With
  every warband committed, nothing dies.
- **Signal Trees writes one line** covering the reveal and the discard,
  and names no player. Each card is revealed on the next region's pile
  after it moves there, since a card entering a pile is forgotten.
- **Where they register.** Careful Plans and Hearts and Minds join
  `PlanRewrites`, Weeping Banner `PlanRules`, and Zealots, Bear Traps and
  Signal Trees `SimplePlans`. `VictoriousNow` gains a silent form.
```

- [ ] **Step 2: Amend the rulings appendix**

1. In the **R42 Weeping Banner** entry, after the bullet list, before `It writes no line of its own.`, add: `The Game Log's attack line counts the warbands the skulls kill, so "Attack: 3 with 3 skulls" may follow two skulls rolled (plan-time ruling).`

2. In the **87 Zealots** entry, replace `While it holds, each sacrificed warband adds three to the attack, and the sacrifice heading says so.` with `While it holds, the plan records a worth of three when it is applied, so each sacrificed warband adds three to the attack, and the sacrifice heading says so: "Sacrifice up to 2 warbands for three attack each".` Then replace `it carries the warning "Zealots: Has no effect unless defending force is larger than yours".` with `its option carries the warning "Zealots: Has no effect unless defending force is larger than yours", and stays choosable.`

3. In the **3 Bear Traps** entry, replace `With an empty board, nothing dies.` with `The committed force stays on the board until the losses, so with no warband beyond it, nothing dies and no line is written.`

4. In the **217 Signal Trees** entry, replace the line paragraph that begins `Line: "Signal Trees: Revealed Wolves` with:

```markdown
  Line (plan-time ruling, one line for the reveal and the discard):
  "Signal Trees: Revealed and discarded Wolves, Tavern Songs and Sacred
  Ground; added 4 attack dice." ("removed" for a defender), "Signal Trees:
  Revealed and discarded …." with no Beast, and "Signal Trees: Nothing was
  revealed." for an empty pile.
```

Re-wrap every edited paragraph to 78 characters.

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: no broken link.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md
git commit -m "docs(batch7): record slice 4 plan-time rulings"
```

---

### Task 2: Careful Plans reorders the battle

This is the slice's riskiest seam, so it comes first. If its first test cannot pass without changing `CampaignProcedure`, stop and ask the product owner: the design's fallback is an order hook in the Campaign tree.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/CarefulPlans.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `CarefulPlansCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/CarefulPlansSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan.rewrites`, `PlanUse.chosen`, the root `CampaignProcedure.battle` builds.
- Produces: `CarefulPlans` (case object), `CarefulPlansCard`.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Careful Plans: one favor placed, and the defender rolls before the
  * attacker; the sacrifice stays after the attack roll. */
class CarefulPlansSuite extends munit.FunSuite:
  import AttackDieFace.{OneSword, TwoSwordsSkull}
  import DefenseDieFace.{Blank, OneShield}

  private val card = CatalogNames.denizen("Careful Plans")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val attackFaces = Vector[DieFace](OneSword, TwoSwordsSkull)
  private val defenseFaces = Vector[DieFace](Blank, OneShield)

  /** p1 attacks the bandit-ruled origin with 5 warbands, holding Careful
    * Plans faceup, `favor` favor, and Longbows, a free plan that keeps the
    * attacker's window asked. Ancient City prints 2 defense dice. */
  private def attacker(favor: Int): Board =
    val b = withAdviser(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), longbows, Orientation.FaceUp)
    on(b)(_.favor(b.actor, favor))

  /** The pools that hold a roll now. */
  private def rolled(run: Run): Set[PoolKey] =
    ready(run.state).game.current.rollOutcomes.keySet

  test("the defense rolls first, and the sacrifice is asked with both " +
      "rolls known"):
    val b = attacker(favor = 1)
    // The scripted dice hand out rolls in the order they are asked for.
    val game = rules(scripted(defenseFaces, attackFaces))
    val picked = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card),
      Tokens(1, 0))
    val asked = picked.finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(rolled(asked),
      Set(CampaignIds.attackPool, CampaignIds.defensePool))
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](Blank, OneShield))
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, TwoSwordsSkull))
    // A shield and the two Bandits defend with 3; a sword, a paid skull and
    // the sacrifice attack with 4.
    assertEquals(done.result.defenseScore, 3)
    assertEquals(done.result.attackTotal, 4)
    assertEquals(said(CarefulPlans, done.events), Vector.empty)

  test("unchosen, the attack rolls first"):
    val b = attacker(favor = 1)
    val game = rules(scripted(attackFaces, defenseFaces))
    val asked = commit(game, b, 2).finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(rolled(asked), Set(CampaignIds.attackPool))

  test("without a favor it is shown unavailable"):
    val b = attacker(favor = 0)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 1 favor; you have 0")))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.CarefulPlansSuite"`
Expected: compile failure, `CarefulPlans` not found.

- [ ] **Step 3: Write the plan**

Delete `CarefulPlansCard` from `HoldingOrderDenizens.scala`, and create `CarefulPlans.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.model._

object CarefulPlansCard extends Denizen(DenizenId("254"), "Careful Plans",
    Suit.Order):
  val power = PrintedPower(PowerId("denizen.careful-plans"),
    persistent = false, cost = Cost(favor = 1),
    text = "The defender **must** roll their [defense-die] before the " +
      "attacker rolls their [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Careful Plans (card 254), an attacker's battle plan: "The defender must
  * roll their defense dice before the attacker rolls their attack dice."
  *
  * One favor is placed onto the card. Its rewrite at the Campaign's root
  * folds `Late`, chosen or not, and finds the battle's five steps by the
  * windows of the two result steps: the attack roll, the attack result and
  * the sacrifice, then the defense roll and the defense result. It puts
  * them in one `Branch`, so the root keeps the same shape on every fold,
  * and the branch reads at walk time whether the plan was chosen. When it
  * was, the defense roll and its result come first, so the sacrifice is
  * decided with both rolls known. The defense result adds the defender's
  * force in either order. A root of any other shape is left as it is.
  *
  * It writes no line of its own.
  */
case object CarefulPlans extends BattlePlan:
  val cardId: DenizenId = CarefulPlansCard.id
  val id: PowerId = CarefulPlansCard.power.id
  /** The attack roll, the attack result and the sacrifice, which come
    * before the defense roll and its result. */
  private val AttackSteps = 3

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Careful Plans: the defender rolls first",
      Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      reordered(ctx.activePlayer, children)))

  /** `children`, with the battle's steps in one branch. */
  private def reordered(actor: PlayerId, children: Vector[Operation])
      : Vector[Operation] =
    val attackResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignAttackResult))
    val defenseResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignDefenseResult))
    val start = attackResult - 1
    if start < 0 || defenseResult != start + AttackSteps + 1 then children
    else
      val battle = children.slice(start, defenseResult + 1)
      val (attack, defense) = battle.splitAt(AttackSteps)
      children.take(start) ++ Vector(Branch((ready, pending) =>
        if used(ready, pending, actor) then defense ++ attack
        else battle)) ++ children.drop(defenseResult + 1)

  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Boolean = PlanUse.chosen(ready, pending, actor, cardRef, sides,
    afterOutcome = false).nonEmpty
```

Register it in `PlanRewrites.forCatalog`: `Vector(PeaceEnvoy, WildMounts.forCatalog(catalog), CarefulPlans)`. Add `CarefulPlansCard` to the `oathdigital.gameplay.powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS. Every Campaign suite now walks the battle inside the branch, so a failure elsewhere means the reshape broke something: fix it before going on.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/CarefulPlans.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/CarefulPlansSuite.scala
git commit -m "feat(batch7): Careful Plans rolls the defense first"
```

---

### Task 3: Weeping Banner kills two warbands per skull

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/WeepingBanner.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `WeepingBannerCard`)
- Modify: `docs/catalog/reference/new-foundations-component-catalog.json`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WeepingBannerSuite.scala`

**Interfaces:**
- Consumes: `AttackScore.rescore`, `AttackScore.changes`.
- Produces: `AttackRules.skullKills: Int = 1`, `WeepingBanner` (case object), `WeepingBannerCard`.

- [ ] **Step 1: Write the failing tests**

In `AttackScoreSuite`, add a helper beside `defender`:

```scala
  private def relicDefender(name: String)(using munit.Location): Plan =
    Plan(CampaignPlanSide.Defender,
      DecisionOptionRef.Relic(CatalogNames.relic(name)))
```

and a test at the end:

```scala
  test("with the defender's Weeping Banner each skull kills two, capped by " +
      "the force, and scores only when both are paid"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword)
    val banner = relicDefender("Weeping Banner")
    assertScores(faces, 4, chosen(banner))(score = 1 + 4, lost = 4)
    // An odd force: the last warband dies and its skull scores nothing.
    assertScores(faces, 3, chosen(banner))(score = 1 + 2, lost = 3)
    assertScores(faces, 3, chosen(banner, attacker("Fearsome General")))(
      score = 1 + 4, lost = 3)
    assertScores(faces, 3, chosen(banner, attacker("Outriders")))(
      score = 1 + 4, lost = 0)
```

Create `WeepingBannerSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Weeping Banner: a defender's relic plan, a secret placed, under which
  * each skull kills two warbands. */
class WeepingBannerSuite extends munit.FunSuite:
  import AttackDieFace.{OneSword, TwoSwordsSkull}
  import DefenseDieFace.TwoShields

  private val relic = CatalogNames.relic("Weeping Banner")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)
  private val general = CatalogNames.denizen("Fearsome General")
  private val outriders = CatalogNames.denizen("Outriders")
  // Two double shields and the two warbands defend with 6, so the attacker
  // loses every battle here and nothing is placed.
  private val shields = Vector(TwoShields, TwoShields)

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, and
    * holds Weeping Banner faceup and one faceup secret. p1 has 5 warbands.
    */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withRelicFor(base, base.rival, relic))(
      _.secrets(base.rival, faceUp = 1))

  /** Two skulls, then a sword for each committed warband beyond two. */
  private def skulls(force: Int): Vector[AttackDieFace] =
    Vector(TwoSwordsSkull, TwoSwordsSkull) ++ Vector.fill(force - 2)(OneSword)

  test("each skull kills two warbands, and the secret is paid off turn"):
    val b = defender
    val facedown = b.player(b.rival).board.faceDownSecrets
    val done = commit(rules(dice(skulls(4), shields)), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 4)
    // Both skulls are paid: two swords each, and two single swords.
    assertEquals(done.result.attackScore, 6)
    assertEquals(Look(done.state).player(b.rival).board.faceDownSecrets,
      facedown + 1)
    assertEquals(said(WeepingBanner, done.events), Vector.empty)

  test("with an odd force the last warband dies and its skull scores " +
      "nothing"):
    val b = defender
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 3)
    assertEquals(done.result.attackScore, 1 + 2)

  test("with Fearsome General a paid skull is worth four, as without it"):
    val b = withAdviser(defender, general, Orientation.FaceUp)
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(general))
      .finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 3)
    assertEquals(done.result.attackScore, 1 + 4)

  test("with the skulls ignored nothing dies and every face scores"):
    val b = withAdviser(defender, outriders, Orientation.FaceUp)
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(outriders))
      .finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore, 1 + 4)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.AttackScoreSuite oathdigital.gameplay.powers.campaign.WeepingBannerSuite"`
Expected: compile failure, `WeepingBanner` not found.

- [ ] **Step 3: Score the skull kills**

In `AttackScore.scala`, add to `AttackRules`'s doc list:

```scala
  *  - `skullKills`: the warbands each skull kills, 2 with the defender's
  *    Weeping Banner. The kills are capped by the force, and a skull face
  *    scores only when all of its kills are paid.
```

and the field, last: `skullFacesScoreNothing: Boolean = false, skullKills: Int = 1)`.

In `AttackScore.of`, replace the `lost` and `scoring` lines with:

```scala
    val lost =
      if rules.skullsIgnored then 0
      else math.min(skulls * rules.skullKills, force)
    val scoring =
      if rules.skullsIgnored then skulls
      else math.min(skulls, force / rules.skullKills)
```

Add to the end of `AttackScore.changes`:

```scala
    AttackChange(DecisionOptionRef.Relic(WeepingBannerCard.id),
      CampaignPlanSide.Defender, _.copy(skullKills = 2)))
```

- [ ] **Step 4: Write the plan**

Delete `WeepingBannerCard` from `HoldingRelics.scala`. In `docs/catalog/reference/new-foundations-component-catalog.json`, change `"id": "relic.keeping-banner"` to `"id": "relic.weeping-banner"`. Create `WeepingBanner.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object WeepingBannerCard extends Relic(RelicId("R42"), "Weeping Banner",
    value = 63, defense = 2):
  val power = PrintedPower(PowerId("relic.weeping-banner"),
    persistent = false, cost = Cost(secret = 1),
    text = "The attacker **must** resolve another [skull] for each " +
      "[skull] rolled.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Weeping Banner (relic R42), a defender's battle plan: "The attacker must
  * resolve another skull for each skull rolled."
  *
  * One secret is placed onto the relic. Each skull the attacker rolls kills
  * two warbands of the force instead of one (`AttackRules.skullKills`),
  * capped by the force, and a skull face scores its swords only when both
  * of its warbands are paid, so with an odd force the last warband dies and
  * scores nothing. It adds no swords: a paid skull face is worth four with
  * Fearsome General, as without it. When the skulls are ignored (Outriders
  * and the like), nothing dies and every face scores as usual. Once the
  * attack is scored, the plan writes it again through the attack scorer
  * (`AttackScore`).
  *
  * It writes no line of its own.
  */
case object WeepingBanner extends BattlePlan:
  val relicId: RelicId = WeepingBannerCard.id
  val id: PowerId = WeepingBannerCard.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Weeping Banner: each skull kills two warbands",
      Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Vector(AttackScore.rescore(use.actor))))
```

Register it in `PlanRules.forCatalog` after `Vector(FearsomeGeneral) ++`: `Vector(WeepingBanner) ++`. Add `WeepingBannerCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS. A catalog suite that compares the holding catalog with the reference JSON passes once both carry `relic.weeping-banner`; if one names the old id elsewhere, rename it there too.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala src/main/scala/oathdigital/gameplay/powers/campaign/WeepingBanner.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala docs/catalog/reference/new-foundations-component-catalog.json src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/WeepingBannerSuite.scala
git commit -m "feat(batch7): Weeping Banner kills two warbands per skull"
```

---

### Task 4: Zealots, the sacrifice's worth and a plan's warning

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala` (`CampaignIds`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`
- Modify: `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Zealots.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `ZealotsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ZealotsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`

**Interfaces:**
- Consumes: `CampaignBattle.defenderForce`, `PlanPrice.priced`, `DecisionOption.Warned`.
- Produces: `CampaignIds.sacrificeWorth`, `CampaignBattle.sacrificeWorth(ready: ReadyGame): Int`, `CampaignBattle.sacrificeHeading(max: Int, worth: Int = 1): String`, `CampaignResult.sacrificeWorth: Int = 1`, `CampaignPlanOffer.warning: Option[OptionReason] = None`, `Zealots` (case object) with `Zealots.Worth` and `Zealots.noEffect`.

- [ ] **Step 1: Write the failing tests**

In `CampaignBattleSuite`'s test "the sacrifice heading is the prompt alone -- the dice are glyphs", add:

```scala
    assertEquals(CampaignBattle.sacrificeHeading(2, worth = 3),
      "Sacrifice up to 2 warbands for three attack each")
```

In `CampaignResultCodecSuite`, add:

```scala
  test("the sacrifice's worth round trips, and a record without it reads " +
      "one"):
    encodedStreamThatRoundTrips("campaign-result-worth", Vector(
      recordedOperation("campaign",
        RecordCampaignResult(conquest.copy(sacrificeWorth = 3)))))
    def encoded = ujson.read(accepted(GameEventWire.encodeEvent(
      "campaign-result", 0,
      recordedOperation("campaign", RecordCampaignResult(conquest)))))
    val older = encoded
    older("payload")("ops")(0)("result").obj.remove("sacrificeWorth")
    assertEquals(GameEventWire.decode(older), GameEventWire.decode(encoded))
```

In `GameLogCampaignSuite`, add `ChooseAmountAnswer` to the `DecisionAnswer` import and this test:

```scala
  test("a sacrifice line counts what each warband adds"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    def sacrificeLine(before: Vector[OathEvent]): String =
      val tail = before ++ Vector[OathEvent](
        WalkerStepRecorded("sacrifice", ChoicePayload(CampaignIds.sacrifice,
          ChooseAmountAnswer(2), script.actor), Vector.empty, Vector.empty),
        WalkerCompleted(ActionRef.Campaign))
      texts(formatAppended(script, tail).filter(_.sequence >= steps.size))
        .find(_.startsWith("Sacrificed ")).get
    def attackOf(line: String): Int = line.split(' ').last.toInt
    val plain = sacrificeLine(Vector.empty)
    val zealous = sacrificeLine(Vector(WalkerStepRecorded("zealots",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("plan")),
      Vector(ModifyDicePool(CampaignIds.sacrificeWorth, 3)), Vector.empty)))
    assert(plain.startsWith("Sacrificed 2 warbands for an attack of "), plain)
    // Each of the two warbands adds three, not one.
    assertEquals(attackOf(zealous) - attackOf(plain), 4)
```

Create `ZealotsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Zealots: one favor placed; while the defending force is larger when it
  * is chosen, each sacrificed warband adds three. */
class ZealotsSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, TwoSwordsSkull}
  import DefenseDieFace.Blank

  private val card = CatalogNames.denizen("Zealots")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val source = PowerSourceRef.Card(card)
  // Ancient City's two blank defense dice and its two Bandits defend with 2.
  private val blanks = Vector(Blank, Blank)

  /** p1 attacks the origin, which 2 Bandits rule, with `warbands`
    * warbands, holding Zealots faceup and 1 favor. */
  private def attacker(warbands: Int): Board =
    val b = withAdviser(board(warbands = warbands, supply = 7), card,
      Orientation.FaceUp)
    on(b)(_.favor(b.actor, 1))

  /** The parked sacrifice question's heading. */
  private def sacrificeHeading(run: Run, b: Board): Option[String] =
    run.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount => amount.heading
      case _ => None

  /** Whether `run` recorded a sacrifice worth. */
  private def recordsWorth(run: Run): Boolean = run.ops.exists {
    case ModifyDicePool(pool, _, _) => pool == CampaignIds.sacrificeWorth
    case _ => false
  }

  test("with a larger defending force each sacrificed warband adds three"):
    val b = attacker(warbands = 1)
    val game = rules(dice(Vector(HollowSword), blanks))
    val asked = commit(game, b, 1)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(asked.state).adviserTokens(b.actor, card),
      Tokens(1, 0))
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(sacrificeHeading(asked, b),
      Some("Sacrifice up to 1 warband for three attack each"))
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(done.result.sacrificeWorth, 3)
    // Half a sword scores nothing; the sacrifice alone beats the 2.
    assertEquals(done.result.attackTotal, 3)
    assertEquals(done.winner, Some(true))
    assertEquals(said(Zealots, done.events), Vector.empty)

  test("without a larger defending force it warns, and a sacrifice adds " +
      "one"):
    val b = attacker(warbands = 3)
    val run = commit(rules(dice(Vector.fill(3)(HollowSword), blanks)), b, 3)
    val warning = run.options(b.actor).collectFirst {
      case shown: DecisionOption.Warned if shown.ref == ref => shown.reason }
    assertEquals(warning, Some(OptionReason.Noted(Zealots.id,
      Zealots.noEffect(source))))
    assertEquals(NoteText.sentence(Zealots.noteKeys, Zealots.noEffect(source)),
      "Has no effect unless defending force is larger than yours")
    val done = run.pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
    assertEquals(done.result.sacrificeWorth, 1)
    // Three halves make one sword, and the sacrifice one more: a tie,
    // which the defender wins.
    assertEquals(done.result.attackTotal, 2)
    assertEquals(done.winner, Some(false))
    assert(!recordsWorth(done))

  test("a skull lost after it is chosen does not bring it into force"):
    // Two warbands against two Bandits is not larger. A skull then kills
    // one of the two, which does not make Zealots hold.
    val b = attacker(warbands = 2)
    val game = rules(dice(Vector(TwoSwordsSkull, HollowSword), blanks))
    val done = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
    assertEquals(done.result.skullLosses, 1)
    assertEquals(done.result.sacrificeWorth, 1)
    assertEquals(done.result.attackTotal, 2 + 1)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.ZealotsSuite oathdigital.gameplay.CampaignBattleSuite oathdigital.serialization.CampaignResultCodecSuite oathdigital.application.gamelog.GameLogCampaignSuite"`
Expected: compile failure, `Zealots` and `sacrificeWorth` not found.

- [ ] **Step 3: The model**

In `CampaignTypes.scala`, add to `CampaignPlanOffer`'s doc:

```scala
  * `warning` is why a plan its user can pay for deserves a second look
  * (Zealots, whose condition fails). The option shows it and stays
  * choosable.
```

and the field, last: `withheld: Option[OptionReason] = None, alone: Boolean = false, warning: Option[OptionReason] = None)`.

In `CampaignResult`, add the field after `attackerWins`, `sacrificeWorth: Int = 1`, and replace `def attackTotal: Int = attackScore + sacrificed` with `def attackTotal: Int = attackScore + sacrificed * sacrificeWorth`. In its doc, replace `The attacker prevails when `attackTotal > defenseScore`` with `` `sacrificeWorth` is what each sacrificed warband adds, three with Zealots. The attacker prevails when `attackTotal > defenseScore` ``, re-wrapped.

In `CampaignIds` (`CampaignSetup.scala`), after `defensePool`:

```scala
  /** What each sacrificed warband adds, recorded by a plan when it is
    * applied (Zealots); one while the pool is absent. It is never rolled,
    * and it is cleared with the others when the Campaign ends. */
  val sacrificeWorth: PoolKey = PoolKey("campaign.sacrifice-worth")
```

- [ ] **Step 4: The engine reads the worth**

In `CampaignBattle.scala`, replace `sacrificeHeading` with:

```scala
  /** What each sacrificed warband adds to the attack: one, or what a plan
    * recorded when it was applied (Zealots). */
  def sacrificeWorth(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.sacrificeWorth)
      .fold(1)(_.count)

  /** The question alone. The dice, the attack total and the skull loss are
    * projected as a roll outcome and drawn as glyphs beside this, so writing
    * them here again would print the same facts twice, once as words.
    */
  def sacrificeHeading(max: Int, worth: Int = 1): String =
    s"Sacrifice up to $max warband${if max == 1 then "" else "s"} for " +
      s"${worthWord(worth)} attack each"

  private def worthWord(worth: Int): String = worth match
    case 1 => "one"
    case 3 => "three"
    case other => other.toString
```

In `CampaignBattle.result`, bind `val worth = sacrificeWorth(ready)` beside `sacrificed`, and end the constructor call with `defenseScore, attackScore + sacrificed * worth > defenseScore, worth)`.

In `CampaignProcedure.sacrificeStep`, pass the worth: `Some(CampaignBattle.sacrificeHeading(max, CampaignBattle.sacrificeWorth(ready)))`.

In `CampaignPlanChoice.userPass`, wrap the priced option:

```scala
          case Right(operations) => warned(offered, PlanPrice.priced(
            CampaignPlans.optionOf(offered), offered.offer, operations))
```

and add beside `applicationOf`:

```scala
  /** The option, with the offer's warning when it has one. */
  private def warned(offered: OfferedPlan, option: DecisionOption)
      : DecisionOption =
    offered.offer.warning.fold(option)(DecisionOption.Warned(option, _))
```

In the class doc's first bullet, after `then "Finish".`, add `A plan its user can pay for shows its offer's warning.`

- [ ] **Step 5: The wire and the Game Log**

In `CampaignResultCodec.scala`, encode `"sacrificeWorth" -> result.sacrificeWorth` after `"attackerWins"`, and end the decoded constructor call with `value("attackerWins").bool, value.obj.get("sacrificeWorth").fold(1)(_.num.toInt))`, with a comment: `// A result recorded before the worth existed reads one.`

In `CampaignLines.scala`, the sacrifice arm becomes:

```scala
      // Each warband sacrificed adds its worth to the attack; saying the
      // new total spares the reader the sum against the defense.
      val before = journal.readyBefore(at)
      val attack = before.flatMap(_.game.current.rollOutcomes
        .get(CampaignIds.attackPool)).fold(0)(_.score)
      val worth = before.fold(1)(CampaignBattle.sacrificeWorth)
      Vector(Posted.line(LogKind.Decision, Vector(Text(
        s"Sacrificed $count ${plural(count, "warband", "warbands")} " +
          s"for an attack of ${attack + count * worth}"))))
```

- [ ] **Step 6: Write the plan**

Delete `ZealotsCard` from `HoldingDiscordDenizens.scala`, and create `Zealots.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}
import oathdigital.model._

object ZealotsCard extends Denizen(DenizenId("87"), "Zealots", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.zealots"),
    persistent = false, cost = Cost(favor = 1),
    text = "If the defending force is larger than your force, each " +
      "warband you sacrifice will add three _(not one)_ to your " +
      "attack.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Zealots (card 87), an attacker's battle plan: "If the defending force is
  * larger than your force, each warband you sacrifice will add three (not
  * one) to your attack."
  *
  * One favor is placed onto the card. The condition compares the defending
  * warbands (`CampaignBattle.defenderForce`) with the committed force when
  * the plan is chosen, and the outcome is recorded then (FAQ: later skull
  * losses do not trigger it). While it holds, the plan records a worth of
  * three in `CampaignIds.sacrificeWorth`, which the sacrifice question,
  * the battle's result and the Game Log read. When it does not hold, the
  * plan may still be chosen and does nothing, and its option carries the
  * warning "Has no effect unless defending force is larger than yours".
  *
  * It writes no line of its own.
  */
case object Zealots extends BattlePlan:
  val cardId: DenizenId = ZealotsCard.id
  val id: PowerId = ZealotsCard.power.id
  /** What each sacrificed warband adds while the plan holds. */
  val Worth: Int = 3
  /** "Has no effect unless defending force is larger than yours", the
    * option's warning. */
  val noEffect: NoteKey = NoteKey("no-effect", Vector(NotePart.Text(
    "Has no effect unless defending force is larger than yours")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Zealots.noEffect)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val offer = CampaignPlanOffer(source,
        "Zealots: each sacrificed warband adds three",
        Vector(CampaignPlanCost.Favor(1)), Vector.empty)
      if holds(context) then offer.copy(effects = Vector(
        CampaignPlanEffect.Run(Vector(
          ModifyDicePool(CampaignIds.sacrificeWorth, Zealots.Worth)))))
      else offer.copy(warning = Some(OptionReason.Noted(id,
        Zealots.noEffect(PowerSourceRef.Card(cardId)))))
    }

  /** Whether the defending force is larger than the committed one now. */
  private def holds(context: PlanContext): Boolean =
    CampaignBattle.defenderForce(context.ready, context.setup) >
      context.setup.force
```

Register it in `SimplePlans.forCatalog`, last: `Vector(Zealots)`. Add `ZealotsCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign* oathdigital.serialization.* oathdigital.application.gamelog.* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala src/main/scala/oathdigital/serialization/CampaignResultCodec.scala src/main/scala/oathdigital/application/gamelog/CampaignLines.scala src/main/scala/oathdigital/gameplay/powers/campaign/Zealots.scala src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/ZealotsSuite.scala src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala
git commit -m "feat(batch7): Zealots makes each sacrificed warband add three"
```

---

### Task 5: Bear Traps

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BearTraps.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `BearTrapsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BearTrapsSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.forceKind`, `CampaignSetup`.
- Produces: `BearTraps` (case object) with `BearTraps.killed`, `BearTrapsCard`.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Bear Traps: a free defender's plan that removes an attack die and kills
  * a warband on the attacker's board, beyond the committed force. */
class BearTrapsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Bear Traps")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** One warband killed from `player`'s board. */
  private def killedFrom(b: Board, player: PlayerId): CoreOperation =
    Kill(Piece.Warbands(Look(b.ready).forceKind(player), 1),
      PositionedLocation(Location.PlayArea(player)))

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, and
    * holds Bear Traps faceup. p1 has 5 warbands. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("a defender removes an attack die and kills a warband beyond the " +
      "force"):
    val b = defender
    val run = commit(rules(losingDice), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assert(picked.since(run).contains(killedFrom(b, b.actor)))
    assertEquals(Look(picked.state).boardWarbands(b.actor), 4)
    assertEquals(said(BearTraps, picked.events), Vector(NoteText.Said(
      "killed", s"Killed 1 warband on ${b.actor.value}'s board.",
      covers = true)))

  test("with every warband committed nothing dies"):
    val b = defender
    val run = commit(rules(losingDice), b, 5)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assertEquals(Look(picked.state).boardWarbands(b.actor), 5)
    assertEquals(said(BearTraps, picked.events), Vector.empty)

  test("bandits apply it at a site they rule"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    // Three of four dice are left; three swords beat the two Bandits, so
    // no warband of the force dies.
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))
    assert(done.ops.contains(killedFrom(b, b.actor)))
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.actor), 4)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BearTrapsSuite"`
Expected: compile failure, `BearTraps` not found.

- [ ] **Step 3: Write the plan**

Delete `BearTrapsCard` from `HoldingOrderDenizens.scala`, and create `BearTraps.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object BearTrapsCard extends Denizen(DenizenId("3"), "Bear Traps", Suit.Order):
  val power = PrintedPower(PowerId("denizen.bear-traps"),
    persistent = false, cost = Cost.free,
    text = "- [attack-die] Kill one warband on the attacker's board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bear Traps (card 3), a defender's battle plan: "- attack die. Kill one
  * warband on the attacker's board."
  *
  * It is free, so a bandit defender applies it at a site it rules. It
  * removes one attack die and kills one of the attacker's warbands on their
  * board beyond the committed force, which stays on the board until the
  * losses. With none beyond the force, nothing dies.
  *
  * When a warband died it writes "Killed 1 warband on {Red}'s board.".
  */
case object BearTraps extends BattlePlan:
  val cardId: DenizenId = BearTrapsCard.id
  val id: PowerId = BearTrapsCard.power.id
  /** "Killed {1 warband} on {Red}'s board." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" on "), NotePart.Arg(1),
    NotePart.Text("'s board.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(BearTraps.killed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Bear Traps: remove 1 attack die and kill a warband on the " +
        "attacker's board", Vector.empty, Vector(
        CampaignPlanEffect.RemoveAttackDice(1),
        CampaignPlanEffect.Run(Vector(trap(context.setup),
          Note(id, killedNote(context.setup.actor), covers = true))))))

  /** One warband off the attacker's board, when one stands there beyond
    * the committed force. Read when the walk reaches it. */
  private def trap(setup: CampaignSetup): Operation =
    BuildOps((ready, _) =>
      if warbands(ready, setup.actor) <= setup.force then Right(Vector.empty)
      else PlayerFacts.forceKind(ready, setup.actor).map(kind =>
        Vector[CoreOperation](Kill(Piece.Warbands(kind, 1),
          PositionedLocation(Location.PlayArea(setup.actor))))))

  private def warbands(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)

  /** The line, when the step before killed `attacker`'s warbands. */
  private def killedNote(attacker: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    states.previous.map((before, after) =>
      warbands(before, attacker) - warbands(after, attacker))
      .filter(_ > 0).map(count => BearTraps.killed(
        PowerSourceRef.Card(cardId), NoteArg.Amount(count, NoteUnit.Warband),
        NoteArg.Player(attacker)))
```

Register it in `SimplePlans.forCatalog`, last: `Vector(BearTraps)`. Add `BearTrapsCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BearTraps.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/BearTrapsSuite.scala
git commit -m "feat(batch7): Bear Traps removes a die and kills from the board"
```

---

### Task 6: Signal Trees reveals on application only

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `SignalTreesCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/SignalTreesSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite`, `CardPlay.nextRegion`, `Reveal`, Call for Help's move-then-reveal.
- Produces: `SignalTrees` (final case class) with `SignalTrees.forCatalog`, `SignalTrees.id`, the keys `added`, `removed`, `revealed`, `none`; `SignalTreesCard`.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Signal Trees: a secret placed; the top three cards of the user's
  * region's discard pile are revealed and discarded, for two attack dice
  * per Beast. */
class SignalTreesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Signal Trees")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val power = SignalTrees.forCatalog(catalog)
  private val wolves = CatalogNames.denizen("Wolves")
  private val swarm = CatalogNames.denizen("Insect Swarm")
  private val songs = CatalogNames.denizen("Tavern Songs")
  private val oracle = CatalogNames.denizen("Oracle")
  // The origin, Ancient City, is in the Cradle.
  private val next = CardPlay.nextRegion(Region.Cradle)

  /** p1 attacks the bandit-ruled origin with 5 warbands, holding Signal
    * Trees faceup and one faceup secret, with `pile` in the Cradle's
    * discard pile, the last on top. */
  private def attacker(pile: DenizenId*): Board =
    val b = withSecrets(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), faceUp = 1)
    on(b)(_.discarded(Region.Cradle, pile*))

  private def everyone(b: Board): Vector[PlayerId] =
    Vector(b.actor, b.rival, b.bystander)

  private def line(key: String, text: String): NoteText.Said =
    NoteText.Said(key, text, covers = true)

  test("an attacker reveals the top three and adds two dice per Beast"):
    assertEquals(Vector(wolves, swarm, songs).map(catalog.suitOf),
      Vector(Some(Suit.Beast), Some(Suit.Beast), Some(Suit.Hearth)))
    val b = attacker(oracle, swarm, songs, wolves)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 4)))
    val after = Look(picked.state)
    assertEquals(after.discardPile(Region.Cradle), Vector(oracle))
    // Drawn top first, so the first drawn lies deepest.
    assertEquals(after.discardPile(next), Vector(wolves, songs, swarm))
    for shown <- Vector(wolves, songs, swarm); viewer <- everyone(b) do
      assert(after.knowsAdviser(viewer, shown), s"$viewer misses $shown")
    assertEquals(said(power, picked.events), Vector(line("added",
      s"Revealed and discarded ${wolves.value}, ${songs.value}, " +
        s"${swarm.value}; added 4 attack dice.")))

  test("until it is applied, no player knows the cards"):
    val b = attacker(oracle, swarm, songs, wolves)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val now = Look(run.state)
    for hidden <- Vector(wolves, songs, swarm); viewer <- everyone(b) do
      assert(!now.knowsAdviser(viewer, hidden), s"$viewer knows $hidden")
    assertEquals(now.discardPile(Region.Cradle),
      Vector(oracle, swarm, songs, wolves))

  test("a short pile reveals what it has"):
    val b = attacker(songs, wolves)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(Look(picked.state).discardPile(Region.Cradle), Vector.empty)
    assertEquals(Look(picked.state).discardPile(next), Vector(wolves, songs))
    assertEquals(said(power, picked.events), Vector(line("added",
      s"Revealed and discarded ${wolves.value}, ${songs.value}; added 2 " +
        "attack dice.")))

  test("a defender removes two dice per Beast"):
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1)
        .discarded(Region.Cradle, oracle, wolves))
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(said(power, picked.events), Vector(line("removed",
      s"Revealed and discarded ${wolves.value}, ${oracle.value}; removed 2 " +
        "attack dice.")))

  test("an empty pile reveals nothing"):
    val b = attacker()
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false })
    assertEquals(said(power, picked.events), Vector(NoteText.Said("none",
      "Nothing was revealed.", covers = false)))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SignalTreesSuite"`
Expected: compile failure, `SignalTrees` not found.

- [ ] **Step 3: Write the plan**

Delete `SignalTreesCard` from `HoldingBeastDenizens.scala`, and create `SignalTrees.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object SignalTreesCard extends Denizen(DenizenId("217"), "Signal Trees",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.signal-trees"),
    persistent = false, cost = Cost(secret = 1),
    text = "Reveal the top three cards of your region's discard pile. " +
      "±2 [attack-die] per revealed [suit-beast]. Discard all of " +
      "the revealed cards.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Signal Trees (card 217), a battle plan for either side: "Reveal the top
  * three cards of your region's discard pile. ±2 attack dice per revealed
  * Beast. Discard all of the revealed cards."
  *
  * One secret is placed onto the card, so bandits never use it. When it is
  * applied, the top three cards of the discard pile of the region its
  * user's pawn is in, or as many as the pile holds, move off the top one at
  * a time to the top of the next region's pile, so the first drawn ends
  * deepest. Each is revealed there to every player: a card entering a pile
  * is forgotten, so the reveal follows the move, as Call for Help's does.
  * An attacker adds two attack dice per Beast revealed; a defender removes
  * two. The pile is read when the walk reaches the plan, so the plan
  * window's dry run, which records no event, shows nobody the cards.
  *
  * It writes "Revealed and discarded {cards}; added {4} attack dice."
  * ("removed" for a defender), "Revealed and discarded {cards}." when no
  * Beast is revealed, and "Nothing was revealed." for an empty pile.
  */
final case class SignalTrees private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = SignalTreesCard.id
  def id: PowerId = SignalTrees.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(SignalTrees.added,
    SignalTrees.removed, SignalTrees.revealed, SignalTrees.none)

  private def source: PowerSourceRef = PowerSourceRef.Card(cardId)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Signal Trees: two attack dice per Beast revealed",
      Vector(CampaignPlanCost.Secret(1)), context.user.toVector.map(user =>
        CampaignPlanEffect.Run(Vector(signal(user, context.side))))))

  /** What the plan does for `user` on `side`, read when the walk reaches
    * it: one branch, so the plan's shape never depends on the pile. */
  private def signal(user: PlayerId, side: CampaignPlanSide): Operation =
    Branch((ready, _) =>
      if drawn(ready, user).isEmpty then
        Vector(Note(id, _ => Some(SignalTrees.none(source))))
      else Vector(BuildOps((now, _) => Right(moves(now, user, side))),
        Note(id, signalled(user, side), covers = true)))

  private def home(ready: ReadyGame, user: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, user).flatMap(ready.game.current.map.regionOf)

  /** The top cards of `user`'s region's pile, top first. */
  private def drawn(ready: ReadyGame, user: PlayerId): Vector[WorldCardId] =
    home(ready, user).fold(Vector.empty[WorldCardId])(region =>
      ready.game.current.commonCards.discard(region)
        .takeRight(SignalTrees.Revealed).reverse)

  private def beasts(cards: Vector[WorldCardId]): Int =
    cards.count(card => catalog.suitOf(card).contains(Suit.Beast))

  /** Each drawn card to the top of the next region's pile, revealed there,
    * and the attack dice its Beasts add or remove. */
  private def moves(ready: ReadyGame, user: PlayerId,
      side: CampaignPlanSide): Vector[CoreOperation] =
    home(ready, user).toVector.flatMap { region =>
      val next = CardPlay.nextRegion(region)
      val cards = drawn(ready, user)
      val dice = SignalTrees.PerBeast * beasts(cards)
      val signed = side match
        case CampaignPlanSide.Attacker => dice
        case CampaignPlanSide.Defender => -dice
      cards.flatMap(card => Vector[CoreOperation](
        Move(Piece.Card(card),
          PositionedLocation(Location.RegionalDiscard(region)),
          PositionedLocation(Location.RegionalDiscard(next),
            StackPosition.Top)),
        Reveal(card, Location.RegionalDiscard(next)))) ++
        Option.when(dice > 0)(
          ModifyDicePool(CampaignIds.attackPool, signed)).toVector
    }

  /** The line, from the pile as the step before found it. */
  private def signalled(user: PlayerId, side: CampaignPlanSide)(
      states: NoteStates): Option[PowerNote] =
    states.previous.map { (before, _) =>
      val cards = drawn(before, user)
      val dice = SignalTrees.PerBeast * beasts(cards)
      val shown = NoteArg.Cards(cards)
      if dice == 0 then SignalTrees.revealed(source, shown)
      else side match
        case CampaignPlanSide.Attacker =>
          SignalTrees.added(source, shown, NoteArg.Number(dice))
        case CampaignPlanSide.Defender =>
          SignalTrees.removed(source, shown, NoteArg.Number(dice))
    }

object SignalTrees:
  val id: PowerId = SignalTreesCard.power.id
  /** The most cards revealed. */
  val Revealed: Int = 3
  /** The attack dice each Beast revealed adds or removes. */
  val PerBeast: Int = 2
  /** "Revealed and discarded {cards}; added {4} attack dice." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text("; added "), NotePart.Arg(1),
    NotePart.Plural(1, " attack die.", " attack dice.")))
  /** "Revealed and discarded {cards}; removed {4} attack dice." */
  val removed: NoteKey = NoteKey("removed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text("; removed "), NotePart.Arg(1),
    NotePart.Plural(1, " attack die.", " attack dice.")))
  /** "Revealed and discarded {cards}." */
  val revealed: NoteKey = NoteKey("revealed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(".")))
  /** "Nothing was revealed." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nothing was revealed.")))

  def forCatalog(catalog: ExecutableCatalog): SignalTrees =
    new SignalTrees(catalog)
```

Register it in `SimplePlans.forCatalog`, last: `Vector(SignalTrees.forCatalog(catalog))`. Add `SignalTreesCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.whenplayed.CallForHelpSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/SignalTreesSuite.scala
git commit -m "feat(batch7): Signal Trees reveals discards for Beast dice"
```

---

### Task 7: Hearts and Minds

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/HeartsAndMinds.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `HeartsAndMindsCard`, and `SiteOnly` from its import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HeartsAndMindsSuite.scala`

**Interfaces:**
- Consumes: `VictoriousNow.wrapping`, `PlanDiscard.afterCampaign`, `BannerRules.holder`.
- Produces: `VictoriousNow.silent: Map[PowerWindow, BattlePlan.Wrap]`, `HeartsAndMinds` (final case class) with `forCatalog`, `HeartsAndMindsCard`.

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Hearts and Minds: three favor placed; the defender wins without a roll,
  * and the card is discarded after the Campaign unless its user holds the
  * People's Favor. */
class HeartsAndMindsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Hearts and Minds")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val power = HeartsAndMinds.forCatalog(catalog)

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, Hearts
    * and Minds stands there, and p2 has `favor` favor; `favored` says
    * whether p2 holds the People's Favor. p1 has 5 warbands. */
  private def defender(favor: Int, favored: Boolean): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withSiteCard(base, base.origin, card))(_.favor(base.rival, favor)
      .peoplesFavor(Option.when(favored)(base.rival), favor = 1))

  test("the defender wins without a roll, the attacker loses half the " +
      "force, and the People's Favor keeps the card"):
    val b = defender(favor = 3, favored = true)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.sacrificed, 0)
    // Half of the four committed warbands die.
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)
    assert(Look(done.state).cardsAt(b.origin).contains(card))
    assertEquals(said(power, done.events), Vector.empty)

  test("without the People's Favor it is discarded after the Campaign"):
    val b = defender(favor = 3, favored = false)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))

  test("a defender without three favor sees the shortfall"):
    val b = defender(favor = 2, favored = true)
    val run = commit(rules(winningDice), b, 4)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 3 favor; you have 2")))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HeartsAndMindsSuite"`
Expected: compile failure, `HeartsAndMinds` not found.

- [ ] **Step 3: A silent victory**

In `VictoriousNow.scala`, replace `wrapping` with:

```scala
  def wrapping(line: PlanUse => Note): Map[PowerWindow, BattlePlan.Wrap] =
    windows(use => Vector(line(use)))

  /** The same, with no line of the plan's own (Hearts and Minds). */
  def silent: Map[PowerWindow, BattlePlan.Wrap] = windows(_ => Vector.empty)

  /** The four windows, writing `after` once the result is recorded. */
  private def windows(after: PlanUse => Vector[Operation])
      : Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignAttackRoll -> unrolled,
    PowerWindow.CampaignSacrificeSelection -> unrolled,
    PowerWindow.CampaignDefenseRoll -> unrolled,
    PowerWindow.CampaignAfterOutcome -> ((ctx, use, children) =>
      recorded(ctx, use.side, children) ++ after(use)))
```

In its doc, replace `(Peace Envoy; Hearts and Minds can reuse it)` with `(Peace Envoy and Hearts and Minds)`, and `and `line` is written after it.` with `and Peace Envoy's `line` is written after it; Hearts and Minds writes none.`

- [ ] **Step 4: Write the plan**

Delete `HeartsAndMindsCard` from `HoldingHearthDenizens.scala` and drop `SiteOnly` from that file's import. Create `HeartsAndMinds.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

object HeartsAndMindsCard extends Denizen(DenizenId("138"),
    "Hearts and Minds", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hearts-and-minds"),
    persistent = false, cost = Cost(favor = 3),
    text = "As defender, you're victorious now. At end, discard Hearts " +
      "and Minds unless you hold the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hearts and Minds (card 138, site only), a defender's battle plan: "As
  * defender, you're victorious now. At end, discard Hearts and Minds unless
  * you hold the People's Favor."
  *
  * Three favor are placed onto the card, so bandits never use it. Once
  * used, it decides the battle for the defender (`VictoriousNow`): nothing
  * is rolled or sacrificed, and the defender wins. The defeated attacker's
  * losses are the printed ones, half the surviving force. At the end of the
  * Campaign the card is discarded unless its user holds the People's Favor
  * then, a People's Favor won in this Campaign included.
  *
  * It writes no line of its own, only the discard's "Discarded after the
  * Campaign.".
  */
final case class HeartsAndMinds private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = HeartsAndMindsCard.id
  def id: PowerId = HeartsAndMinds.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Hearts and Minds: win the battle", Vector(CampaignPlanCost.Favor(3)),
      Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] =
    VictoriousNow.silent

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if use.user.exists(favored(use.ready, _)) then Vector.empty
      else PlanDiscard.afterCampaign(catalog, id, use, cardId)))

  private def favored(ready: ReadyGame, player: PlayerId): Boolean =
    BannerRules.holder(ready.game.current, Banner.PeoplesFavor)
      .contains(player)

object HeartsAndMinds:
  val id: PowerId = HeartsAndMindsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): HeartsAndMinds =
    new HeartsAndMinds(catalog)
```

Register it in `PlanRewrites.forCatalog`: `Vector(PeaceEnvoy, WildMounts.forCatalog(catalog), CarefulPlans, HeartsAndMinds.forCatalog(catalog))`. Add `HeartsAndMindsCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS, `PeaceEnvoySuite` included.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala src/main/scala/oathdigital/gameplay/powers/campaign/HeartsAndMinds.scala src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/HeartsAndMindsSuite.scala
git commit -m "feat(batch7): Hearts and Minds wins the defense outright"
```

---

### Task 8: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 24; the frontend count is the baseline. If a count differs, find out why before going on.

- [ ] **Step 2: Mark the slice done**

In `docs/ROADMAP.md`, under "### Phase - Catalog batch 7 - Campaign dice", change `- [ ] Slice 4: battle rewrites (Careful Plans, Weeping Banner, Zealots, Bear` to `- [x] Slice 4: battle rewrites (Careful Plans, Weeping Banner, Zealots, Bear`. In the summary near the top of the roadmap that says which batch 7 slices have landed, bring the sentence up to date with the boxes as they now stand.

In the design's **Status** paragraph, record slice 4 as done, keeping what it says of slice 3: without slice 3 merged, `Slices 1, 2 and 4 are done;`; with slice 3 done, `Slices 1 to 4 are done;`. Re-wrap the paragraph to 78 characters.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md
git commit -m "docs(batch7): slice 4 done"
```
