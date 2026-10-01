# Catalog Batch 3, Slice 1b (Battle Plans: Ruled Cards and Rescoring) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement seven battle plans: Disgraced Captain (20), Battle Axes (256), Great Crusade (164), Pledge of Defense (243), The Great Levy (137), Rain Boots (14) and Garrison Armory (255), with the refactors P1 and P2.

**Architecture:**

- Every card is a `BattlePlan` (`src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`).
  - Its `plan` builds a `CampaignPlanOffer` from where the card stands and the Campaign's setup.
  - A plan whose condition fails returns `None`.
- **P1.** A new `RuledCards.of` in `gameplay/powers/` lists the cards of a suit a ruler rules. `PlanContext` gains `cardsRuled`, and its private `ruler` becomes public. Later slices (Lost Tongue, Fabled Feast, Town Meeting) reuse `RuledCards` outside battle plans, so it lives outside `campaign/`.
- **P2.** Bag of Siegeworks' single-shield rescoring moves into `SingleShields`. The Bag and Rain Boots both call it. The Bag's suite passes unchanged.
- Outriders' skull rewrite becomes `Outriders.ignoreSkulls`, and The Great Levy calls it. Outriders' suite passes unchanged. This is a refactor inside `gameplay/powers/`, which the spec allows.
- Garrison Armory adds a `later` step at `CampaignDefenseResult`. It runs after the window's own step, which writes the dice score plus the force, and adds the force once more.
- Registration:
  - Disgraced Captain and Battle Axes only change dice. They register in `SimplePlans`.
  - The other five reach beyond the plan window. They register in `PlanRules`.
- No engine change: no new operation, window, query, option kind, `NoteArg` kind, protocol or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Slicing", "Log lines", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch", "Slice 1: battle plans" and "Slice 1b"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

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
  - Great Crusade, Pledge of Defense and Rain Boots, key `discarded`: "{Card}: Discarded after the Campaign." This is `PlanDiscard.discarded`, whose source prefix is the card.
  - The Great Levy, key `ignored`, for an attacker when a skull was rolled: "The Great Levy: Skulls ignored." This is `Outriders.ignored` with The Great Levy as the source.
  - Rain Boots, key `ignored`, when a single shield was rolled: "Rain Boots: Single shields ignored." This is `SingleShields.ignored`, the Bag's key, with Rain Boots as the source.
  - Garrison Armory, key `added`, when the targets held a warband: "Garrison Armory: Warbands on the targets added {n} more defense." `{n}` is a `NoteArg.Number` of what was added.
  - Disgraced Captain and Battle Axes write no line, and neither do the dice of The Great Levy, Great Crusade and Pledge of Defense.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch" and "Slice 1: battle plans"):
  - **Sign.** "±" is fixed by the side: an attacker adds attack dice, a defender removes them (`PlanDice`).
  - **Enemy.** For an attacker's plan, the defender, a player or bandits. For a defender's plan, the attacker (`PlanContext.enemy`).
  - **Cards a player rules.** A player rules their faceup advisers and the denizens and edifices, on either face, at the sites they rule. Bandits rule the cards at the sites they rule. Only a faceup card has a suit, except an edifice, which has its suit on either face.
  - **"At end, discard".** Once the Campaign resolves, whoever won, the card is discarded through `PlanDiscard.afterCampaign`, as Horse Archers is. A bandit defender that applied the card discards it too.
  - **Bandit defenders** apply their free plans without choosing, and never use a plan that costs. `CampaignPlanChoice` already does this.
- Baseline: `main` at `ea37f9ff` passes 2196 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in slice 1a: the 2026-09-27 test pruning removed per-card registration pins, and `PowerKindsCatalogSuite` does not list battle plans. Every suite below runs through `WalkerPowerCatalog.default`, so a plan that is not registered is never offered, and its suite fails.
2. **`RuledCards` takes a `SiteRuler`.** Its later users are not battle plans, so it cannot take a `CampaignDefender`. It finds the ruled sites with `SiteRulers.rulerOf`, the same read `SiteRulers.rulerOfCard` uses for Toll Roads. `PlanContext.cardsRuled` converts a `CampaignDefender` to a `SiteRuler`.
3. **An edifice counts as a "faceup" card for Disgraced Captain.** The ruling says "a faceup order card" and means a card with a suit. An edifice has its suit on either face, so an order edifice at a site the defender rules counts.
4. **Great Crusade and Pledge of Defense count themselves whatever their orientation.** `PlanContext.denizen` offers a facedown adviser, and choosing a plan reveals its source, as Outriders' comment says. So the count is the other nomad cards ruled, plus 1. That keeps "always at least 1" true for a facedown adviser. If a facedown adviser should count 0 instead, the facedown test in Task 2 changes.
5. **The Great Levy reuses Outriders' key and rewrite.** Its line has the same text, so it declares `Outriders.ignored`. With Outriders also chosen, both write the score from the faces, so the attack is scored the same, and each card writes its own line.
6. **Garrison Armory reads the setup inside its step.** A `later` step's `BuildOps` receives the same `PendingTree` the Campaign's own steps receive, so `CampaignSetup.setup(ready, use.actor, pending)` resolves. The step computes `CampaignBattle.defenderForce` and writes the recorded score plus that force. Its note reads the amount from the step's before and after states (`NoteStates.previous`), so the line states what happened.
7. **The count is made when the plan is chosen, in the order the plans are chosen.** The plan window re-offers after each pick, and choosing a facedown adviser plan reveals it first. So a facedown nomad adviser that is chosen as a plan before Great Crusade or Pledge of Defense is faceup when their count is made, and it counts. One chosen after does not raise the dice already added, and a facedown nomad adviser that is never chosen as a plan stays facedown and counts nothing. If the dice should be recounted at the end of the window, Task 2 changes to a `later` step, and the two order tests change with it.
8. **The spec's "Verify at plan time" items for this slice**, checked while writing this plan:
   - **Garrison Armory.** The fold order holds. Bag of Siegeworks and Rain Boots add their rescoring through `wrapping`, before the window's children. The window's own step, `CampaignBattle.defenseResultOps`, writes the dice score plus the force. Garrison Armory's `later` step comes after it. `CampaignBattle.result` reads the recorded score, and nothing else recomputes the defense as dice plus force. The only other readers of `defenseScore` are `CampaignResultCodec` and Encirclement, which reads `defenderForce` at the plan step.
   - **Rain Boots and Bag of Siegeworks.** Both write `SingleShields.score(faces)`, so they compose.
   - **Plan discards.** Great Crusade, Pledge of Defense and Rain Boots use `PlanDiscard.afterCampaign`, so they behave exactly as Horse Archers does.

## File Structure

Production:

| File | Change | Responsibility |
|---|---|---|
| `gameplay/powers/RuledCards.scala` | Create | P1: the cards of a suit a ruler rules |
| `gameplay/powers/campaign/PlanContext.scala` | Modify | `ruler` becomes public; adds `cardsRuled` |
| `gameplay/powers/campaign/DisgracedCaptain.scala` | Create | ±4 for a favor placed and a favor burnt, when the defender rules an order card |
| `gameplay/powers/campaign/BattleAxes.scala` | Create | ±2, when the enemy rules a beast card |
| `gameplay/powers/campaign/GreatCrusade.scala` | Create | ±1 per nomad card ruled, discarded after the Campaign |
| `gameplay/powers/campaign/PledgeOfDefense.scala` | Create | +1 defense die per nomad card ruled, discarded after the Campaign |
| `gameplay/powers/campaign/SingleShields.scala` | Create | P2: the single-shield rescoring |
| `gameplay/powers/campaign/BagOfSiegeworks.scala` | Modify | Uses `SingleShields` |
| `gameplay/powers/campaign/RainBoots.scala` | Create | Single shields ignored, discarded after the Campaign |
| `gameplay/powers/campaign/Outriders.scala` | Modify | Its rewrite becomes `Outriders.ignoreSkulls` |
| `gameplay/powers/campaign/GreatLevy.scala` | Create | ±3 for two favor placed, and an attacker ignores skulls |
| `gameplay/powers/campaign/GarrisonArmory.scala` | Create | The targets' warbands add their defense twice |
| `gameplay/powers/campaign/SimplePlans.scala` | Modify | Registers Disgraced Captain and Battle Axes |
| `gameplay/powers/campaign/PlanRules.scala` | Modify | Registers Great Crusade, Pledge of Defense, Rain Boots, The Great Levy and Garrison Armory |

All production paths are under `src/main/scala/oathdigital/`.

Tests, under `src/test/scala/oathdigital/gameplay/powers/`:

| File | Change |
|---|---|
| `RuledCardsSuite.scala` | Create (Task 1) |
| `campaign/DisgracedCaptainSuite.scala`, `campaign/BattleAxesSuite.scala` | Create (Task 1) |
| `campaign/GreatCrusadeSuite.scala`, `campaign/PledgeOfDefenseSuite.scala` | Create (Task 2) |
| `campaign/RainBootsSuite.scala` | Create (Task 3) |
| `campaign/GreatLevySuite.scala` | Create (Task 4) |
| `campaign/GarrisonArmorySuite.scala` | Create (Task 5) |

`BagOfSiegeworksSuite.scala` and `OutridersSuite.scala` must pass unchanged.

Docs: `docs/ROADMAP.md` (Task 6).

### How the suites drive a Campaign

Read `campaign/PlanDriver.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` before writing a suite. In short:

- `board(extras, warbands, supply)`: p1, the actor, stands at the origin (Ancient City, defense 2), which two bandits rule. `extras` further sites are bandit-ruled too (`b.extras`). Every other site starts empty and holds no card. p2 (`b.other`) and p3 stand at the first site nobody rules, which is the first site after the origin when `extras` is 0.
- `againstPlayer(b)`: p2 rules the origin with two warbands and holds the Oath title, so p2 is offered the title's defense plan until they use it. That keeps p2's plan window open, so a "not offered" check against a player defender is not vacuous.
- `withEnemyAtOrigin(b)`: p2 joins the actor at the origin, so a Raid is legal.
- `actorRules(b, site)`: the actor rules `site` with two warbands.
- `commit(rules(dice), b, force, targets, raid)` starts the Campaign and answers the kind, the targets and the force.
- `run.pick(who, CampaignIds.attackerPlan | defenderPlan, ref)` chooses a plan. The window re-offers after each pick, so a second `pick` in the same window chooses a second plan. `run.finish` finishes every window until the Campaign ends.
- `run.ops` is every recorded operation. `picked.since(run)` is only what the pick recorded. Use `since` whenever a dice change could equal the force or the printed defense, because the gathered pools are `ModifyDicePool` operations too.
- An attacker's added dice record `ModifyDicePool(CampaignIds.attackPool, n)`. A defender's removed dice record `ModifyDicePool(CampaignIds.attackPool, -n)`. Added defense dice record `ModifyDicePool(CampaignIds.defensePool, n)`.
- A bandit defender's applied plan also records `ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)` after its effects.
- `Run.offered(actor)` and `Run.offers(actor, who, id, ref)` rebuild the parked decision with the Campaign's actor, so their first argument is always the actor, even in the defender's window.
- `inert(suit, n)` gives `n` denizen ids of `suit` whose powers do nothing, to hold or place without offering a plan of their own.
- `ready(run.state).game.current.lastCampaignResult` is the finished Campaign's `CampaignResult`: `defenseFaces`, `defenseScore`, `attackFaces`, `attackScore`, `skullLosses`.

---

### Task 1: `RuledCards` (P1), then Disgraced Captain and Battle Axes

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/RuledCards.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptain.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BattleAxes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/RuledCardsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptainSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BattleAxesSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen/enemy/setup/side`, `PlanDice.label/effect`, `CatalogCards.denizen`, `SiteRulers.rulerOf(ready, site): Option[SiteRuler]`, `ExecutableCatalog.suitOf(id: CardId): Option[Suit]`.
- Produces, used by Task 2 and by later slices:
  - `RuledCards.of(catalog: ExecutableCatalog, ready: ReadyGame, ruler: SiteRuler, suit: Suit): Vector[CardId]`
  - `PlanContext.ruler: CampaignDefender` (public; was private)
  - `PlanContext.cardsRuled(catalog: ExecutableCatalog, who: CampaignDefender, suit: Suit): Vector[CardId]`

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/RuledCardsSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.campaign.PlanDriver.inert
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The cards a ruler rules (catalog batch 3 rulings, "Cards a player
  * rules"). */
class RuledCardsSuite extends munit.FunSuite:
  private val nomads = inert(Suit.Nomad, 2).map(DenizenId(_))
  private val order = DenizenId(inert(Suit.Order, 1).head)
  private val nomadEdifice =
    EdificeId(catalog.edifices.find(_.suit == Suit.Nomad).get.id.value)

  private def nomadsOf(b: Board, ruler: SiteRuler): Set[CardId] =
    RuledCards.of(catalog, b.ready, ruler, Suit.Nomad).toSet

  test("a player rules their faceup advisers, not their facedown ones"):
    val base = board()
    val b = withAdviserFor(withAdviser(base, nomads(0).value,
      Orientation.FaceUp), base.actor, nomads(1).value, Orientation.FaceDown)
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set[CardId](nomads(0)))

  test("a player rules the faceup denizens and the edifices, on either " +
      "face, at the sites they rule"):
    val base = board()
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    // A facedown denizen has no suit, and an order card is not a nomad card.
    val b = on(actorRules(base, site))(_
      .denizen(nomads(0), at = site)
      .denizen(nomads(1), at = site, facedown = true)
      .denizen(order, at = site)
      .edifice(nomadEdifice, EdificeSide.Ruined, at = site))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)),
      Set[CardId](nomads(0), nomadEdifice))

  test("bandits rule the cards at every site they rule, and nobody else " +
      "does"):
    val base = board(extras = 1)
    val b = withSiteCard(withSiteCard(base, base.origin, nomads(0).value),
      base.extras.head, nomads(1).value)
    assertEquals(nomadsOf(b, SiteRuler.Bandits),
      Set[CardId](nomads(0), nomads(1)))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set.empty[CardId])
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptainSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Disgraced Captain: a favor placed and a favor burnt for four attack dice,
  * added or removed, offered only while the Campaign's defender rules an
  * order card. */
class DisgracedCaptainSuite extends munit.FunSuite:
  private val card = cardWith("denizen.disgraced-captain")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val order = inert(Suit.Order, 1).head

  /** `who` holds Disgraced Captain and 2 favor. */
  private def holder(b: Board, who: PlayerId): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, 2))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker places a favor and burns one for four attack dice " +
      "against a defender with an order adviser"):
    val base = againstPlayer(board())
    val b = withAdviserFor(holder(base, base.actor), base.other, order,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)

  test("an order card at a site the defender rules counts; a facedown " +
      "order adviser does not"):
    val base = againstPlayer(board())
    val funded = holder(base, base.actor)
    val facedown = withAdviserFor(funded, base.other, order,
      Orientation.FaceDown)
    assert(!commit(rules(winning), facedown, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val atSite = withSiteCard(funded, base.origin, order)
    assert(commit(rules(winning), atSite, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("against bandits it holds when an order card stands at any site " +
      "bandits rule"):
    val base = board(extras = 1)
    val funded = holder(base, base.actor)
    assert(!commit(rules(winning), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = withSiteCard(funded, base.extras.head, order)
    assert(commit(rules(winning), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender's use reads the defender's own cards"):
    val base = againstPlayer(board())
    val defender = holder(base, base.other)
    val own = withAdviserFor(defender, base.other, order, Orientation.FaceUp)
    val run = commit(rules(winning), own, 4)
      .pick(base.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    // The attacker's order card does not count: the plan reads the defender.
    val enemy = withAdviserFor(defender, base.actor, order, Orientation.FaceUp)
    val parked = commit(rules(winning), enemy, 4)
    assert(awaits(parked, base.other, CampaignIds.defenderPlan))
    assert(!parked.offered(base.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(withSiteCard(base, base.origin, card), base.origin,
      order)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/BattleAxesSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Battle Axes: a free plan for two attack dice, added or removed, offered
  * only while the enemy rules a beast card. */
class BattleAxesSuite extends munit.FunSuite:
  private val card = cardWith("denizen.battle-axes")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val beasts = inert(Suit.Beast, 2)

  test("an attacker adds two attack dice, for nothing, when the defender " +
      "rules a beast card"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.other, beasts(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("the user's own beast card and one at a site nobody rules count " +
      "nothing"):
    val base = againstPlayer(board())
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    val b = withSiteCard(withAdviser(withAdviser(base, card,
      Orientation.FaceUp), beasts(0), Orientation.FaceUp), site, beasts(1))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("against bandits it holds when a beast card stands at any site " +
      "bandits rule"):
    val base = board(extras = 1)
    val held = withAdviser(base, card, Orientation.FaceUp)
    assert(!commit(rules(winning), held, 3).offers(held.actor, held.actor,
      CampaignIds.attackerPlan, ref))
    val b = withSiteCard(held, base.extras.head, beasts(0))
    assert(commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes two attack dice when the attacker rules a beast " +
      "card"):
    val base = againstPlayer(board())
    val b = withAdviser(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), beasts(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it only when the attacker rules a beast " +
      "card"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val armed = withAdviser(b, beasts(0), Orientation.FaceUp)
    assert(commit(rules(winning), armed, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.RuledCardsSuite oathdigital.gameplay.powers.campaign.DisgracedCaptainSuite oathdigital.gameplay.powers.campaign.BattleAxesSuite"`
Expected: compile failure, `Not found: RuledCards`.

- [ ] **Step 3: Create `RuledCards`**

Create `src/main/scala/oathdigital/gameplay/powers/RuledCards.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.model._

/** The cards a ruler rules (catalog batch 3 rulings, "Cards a player
  * rules"): a player's faceup advisers, and the denizens and edifices at the
  * sites the ruler rules. Bandits hold no advisers; they rule the cards at the
  * sites they rule, as `SiteRulers.rulerOfCard` treats them. Only a faceup
  * card has a suit, so a facedown adviser or denizen belongs to no suit, while
  * an edifice has its suit on either face.
  */
object RuledCards:
  /** The cards of `suit` that `ruler` rules: its advisers first, then the
    * cards at each ruled site in map order. */
  def of(catalog: ExecutableCatalog, ready: ReadyGame, ruler: SiteRuler,
      suit: Suit): Vector[CardId] =
    val current = ready.game.current
    val advisers: Vector[CardId] = ruler match
      case SiteRuler.Player(player) => current.players.find(_.player == player)
        .toVector.flatMap(_.advisers.collect {
          case DenizenState(held, Orientation.FaceUp, _) => held })
      case _ => Vector.empty
    val atSites: Vector[CardId] = current.map.inPlay
      .filter(site => SiteRulers.rulerOf(ready, site).contains(ruler))
      .flatMap(current.map.sites.get).flatMap(_.denizens).collect {
        case DenizenState(held, Orientation.FaceUp, _) => held
        case edifice: EdificeState => edifice.id
      }
    (advisers ++ atSites).filter(card => catalog.suitOf(card).contains(suit))
```

- [ ] **Step 4: Open `ruler` and add `cardsRuled` to `PlanContext`**

In `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`, add this import after `import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds, CampaignPlans, CampaignSetup}`:

```scala
import oathdigital.gameplay.powers.RuledCards
```

Replace:

```scala
  private def ruler: CampaignDefender = user.fold[CampaignDefender](
    CampaignDefender.Bandits)(CampaignDefender.Player(_))
```

with:

```scala
  /** The plan's user as a side of the Campaign: the player, or bandits for a
    * bandit defender. */
  def ruler: CampaignDefender = user.fold[CampaignDefender](
    CampaignDefender.Bandits)(CampaignDefender.Player(_))
```

After the `enemyHolds` method (the last method of `final case class PlanContext`), add:

```scala

  /** The cards of `suit` that `who` rules (`RuledCards`): the plan's user
    * (`ruler`), the enemy or the Campaign's defender. */
  def cardsRuled(catalog: ExecutableCatalog, who: CampaignDefender,
      suit: Suit): Vector[CardId] =
    val asRuler: SiteRuler = who match
      case CampaignDefender.Player(player) => SiteRuler.Player(player)
      case CampaignDefender.Bandits => SiteRuler.Bandits
    RuledCards.of(catalog, ready, asRuler, suit)
```

- [ ] **Step 5: Create the two plans**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptain.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Disgraced Captain (card 20), a battle plan for either side: "[favor]
  * [favor-burnt] ±4 [attack-die] if the defender rules an [suit-order] card
  * (even an adviser)."
  *
  * A favor is placed onto the card and a favor is burnt. An attacker adds four
  * attack dice; a defender removes four. It is offered only while the
  * Campaign's defender rules an order card (`RuledCards`). It reads the
  * defender whichever side uses it, as printed, so a defender's use checks
  * their own cards. Against bandits it holds when an order card stands at any
  * site bandits rule. Bandits pay nothing, so a bandit defender never applies
  * it.
  */
final case class DisgracedCaptain private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = DisgracedCaptain.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.cardsRuled(catalog, context.setup.defender,
        Suit.Order).nonEmpty)
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Disgraced Captain", context.side, DisgracedCaptain.Dice),
        Vector(CampaignPlanCost.Favor(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(PlanDice.effect(context.side, DisgracedCaptain.Dice))))

object DisgracedCaptain:
  val id: PowerId = PowerId("denizen.disgraced-captain")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[DisgracedCaptain] =
    CatalogCards.denizen(catalog, id).map(new DisgracedCaptain(_, catalog))
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BattleAxes.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Battle Axes (card 256), a battle plan for either side: "±2 [attack-die] if
  * your enemy rules a [suit-beast] card."
  *
  * It is free. An attacker adds two attack dice; a defender removes two. It is
  * offered only while the enemy rules a beast card (`RuledCards`). Against
  * bandits it holds when a beast card stands at any site bandits rule. A
  * bandit defender applies it at a site it rules when the attacker rules a
  * beast card.
  */
final case class BattleAxes private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = BattleAxes.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.cardsRuled(catalog, context.enemy, Suit.Beast)
        .nonEmpty)
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Battle Axes", context.side, BattleAxes.Dice),
        Vector.empty, Vector(PlanDice.effect(context.side, BattleAxes.Dice))))

object BattleAxes:
  val id: PowerId = PowerId("denizen.battle-axes")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BattleAxes] =
    CatalogCards.denizen(catalog, id).map(new BattleAxes(_, catalog))
```

- [ ] **Step 6: Register them in `SimplePlans`**

In `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`, replace the doc comment and the last line of the body.

Replace:

```scala
  * Cracking Ground, Walled Garden, Bandit Standard, Extra Provisions and
  * Encirclement, registered together. A plan whose card is absent from
  * `catalog` is omitted.
```

with:

```scala
  * Cracking Ground, Walled Garden, Bandit Standard, Extra Provisions,
  * Encirclement, Disgraced Captain and Battle Axes, registered together. A
  * plan whose card is absent from `catalog` is omitted.
```

Replace:

```scala
      Encirclement.forCatalog(catalog).toVector
```

with:

```scala
      Encirclement.forCatalog(catalog).toVector ++
      DisgracedCaptain.forCatalog(catalog).toVector ++
      BattleAxes.forCatalog(catalog).toVector
```

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.RuledCardsSuite oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 13 new tests (3, 5 and 5), and every existing campaign plan suite still passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/RuledCards.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala src/main/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptain.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattleAxes.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/test/scala/oathdigital/gameplay/powers/RuledCardsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/DisgracedCaptainSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/BattleAxesSuite.scala
git commit -m "feat(powers): add ruled cards, Disgraced Captain and Battle Axes"
```

---

### Task 2: Great Crusade and Pledge of Defense

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/GreatCrusade.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefense.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefenseSuite.scala`

**Interfaces:**
- Consumes: `PlanContext.cardsRuled` and `PlanContext.ruler` (Task 1), `PlanDice.label/effect`, `PlanDiscard.afterCampaign/discarded`, `CatalogCards.denizen`.
- Produces: `GreatCrusade.nomads(catalog: ExecutableCatalog, context: PlanContext, card: DenizenId): Int`, `private[campaign]`, used by Pledge of Defense.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Great Crusade: a free plan for one attack die per nomad card its user
  * rules, counting itself, added or removed, and the card is discarded after
  * the Campaign. */
class GreatCrusadeSuite extends munit.FunSuite:
  private val card = cardWith("denizen.great-crusade")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomads = inert(Suit.Nomad, 3)
  private val order = inert(Suit.Order, 1).head
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(GreatCrusade.id, Vector(PlanDiscard.discarded), run.events)

  test("an attacker adds one attack die per nomad card they rule, counting " +
      "itself, for nothing"):
    val base = board()
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    // A facedown nomad adviser and an order card at a ruled site count nothing.
    val advisers = withAdviserFor(withAdviser(withAdviser(base, card,
      Orientation.FaceUp), nomads(0), Orientation.FaceUp), base.actor,
      nomads(1), Orientation.FaceDown)
    val b = withSiteCard(withSiteCard(actorRules(advisers, site), site,
      nomads(2)), site, order)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a facedown Great Crusade counts itself, since choosing it reveals it"):
    val b = withAdviser(board(), card, Orientation.FaceDown)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("it is discarded after the Campaign, and says so"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a defender removes one attack die per nomad card they rule, and it " +
      "is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, nomads(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(discarded(run.finish.state))

  test("a bandit defender counts the nomad cards at every site bandits " +
      "rule, and it is discarded"):
    val base = board(extras = 1)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      base.extras.head, nomads(0))
    val done = commit(rules(winning), b, 4).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a facedown nomad plan the defender chose first is revealed and " +
      "counts, and one chosen after does not"):
    val pledge = cardWith("denizen.pledge-of-defense")
    val pledgeRef: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(pledge))
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, pledge, Orientation.FaceDown)
    val run = commit(rules(winning), b, 4)
    val crusadeFirst = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(crusadeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.attackPool, -1)))
    val pledgeFirst = run.pick(b.other, CampaignIds.defenderPlan, pledgeRef)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(pledgeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefenseSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Pledge of Defense: a free defender's plan for one defense die per nomad
  * card its user rules, counting itself, and the card is discarded after the
  * Campaign. */
class PledgeOfDefenseSuite extends munit.FunSuite:
  private val card = cardWith("denizen.pledge-of-defense")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomads = inert(Suit.Nomad, 2)
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(PledgeOfDefense.id, Vector(PlanDiscard.discarded), run.events)

  /** What the bandits' application of Pledge of Defense recorded as its
    * effect: the operation just before its marker. */
  private def banditEffect(run: Run): Option[CoreOperation] =
    val marker = ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)
    Option.when(run.ops.contains(marker))(run.ops.takeWhile(_ != marker).last)

  test("a defender adds one defense die per nomad card they rule, counting " +
      "itself, and it is discarded"):
    val base = againstPlayer(board())
    // The defender rules the origin, where a nomad card stands.
    val b = withSiteCard(withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, nomads(0), Orientation.FaceUp),
      base.origin, nomads(1))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finish
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a bandit defender counts the nomad cards at every site bandits " +
      "rule, and it is discarded"):
    val base = board(extras = 1)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      base.extras.head, nomads(0))
    val done = commit(rules(winning), b, 2).finish
    assertEquals(banditEffect(done),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a facedown nomad plan the defender chose first is revealed and " +
      "counts, and one chosen after does not"):
    val crusade = cardWith("denizen.great-crusade")
    val crusadeRef: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(crusade))
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, crusade, Orientation.FaceDown)
    val run = commit(rules(winning), b, 3)
    val crusadeFirst = run.pick(b.other, CampaignIds.defenderPlan, crusadeRef)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(crusadeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    val pledgeFirst = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(pledgeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.defensePool, 1)))
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GreatCrusadeSuite oathdigital.gameplay.powers.campaign.PledgeOfDefenseSuite"`
Expected: compile failure, `Not found: GreatCrusade`.

- [ ] **Step 3: Create the two plans**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/GreatCrusade.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Great Crusade (card 164), a battle plan for either side: "± [attack-die]
  * per [suit-nomad] card you rule. At end, discard Great Crusade."
  *
  * It is free. An attacker adds one attack die per nomad card they rule; a
  * defender removes one per card. It counts itself, so it is always worth at
  * least one die. A bandit defender counts the nomad cards at every site
  * bandits rule. The card is discarded through the standard discard once the
  * Campaign has resolved, whoever won, as Horse Archers is.
  */
final case class GreatCrusade private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = GreatCrusade.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val dice = GreatCrusade.nomads(catalog, context, cardId)
      CampaignPlanOffer(source, PlanDice.label("Great Crusade", context.side,
        dice), Vector.empty, Vector(PlanDice.effect(context.side, dice)))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object GreatCrusade:
  val id: PowerId = PowerId("denizen.great-crusade")

  /** The nomad cards the plan's user rules, counting `card` once whatever its
    * orientation: choosing a plan reveals a facedown adviser, so it is faceup
    * and ruled once chosen. A bandit defender counts the nomad cards at every
    * site bandits rule. Always at least 1. Pledge of Defense counts the same.
    */
  private[campaign] def nomads(catalog: ExecutableCatalog, context: PlanContext,
      card: DenizenId): Int =
    context.cardsRuled(catalog, context.ruler, Suit.Nomad).count(_ != card) + 1

  def forCatalog(catalog: ExecutableCatalog): Option[GreatCrusade] =
    CatalogCards.denizen(catalog, id).map(new GreatCrusade(_, catalog))
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefense.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Pledge of Defense (card 243), a defender's battle plan: "+X [defense-die]
  * X equals all [suit-nomad] cards you rule. At end, discard this card."
  *
  * It is free. It adds one defense die per nomad card its user rules,
  * counting itself, as Great Crusade counts (`GreatCrusade.nomads`). A bandit
  * defender counts the nomad cards at every site bandits rule. The card is
  * discarded through the standard discard once the Campaign has resolved,
  * whoever won, as Horse Archers is.
  */
final case class PledgeOfDefense private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = PledgeOfDefense.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val dice = GreatCrusade.nomads(catalog, context, cardId)
      val noun = if dice == 1 then "defense die" else "defense dice"
      CampaignPlanOffer(source, s"Pledge of Defense: add $dice $noun",
        Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object PledgeOfDefense:
  val id: PowerId = PowerId("denizen.pledge-of-defense")

  def forCatalog(catalog: ExecutableCatalog): Option[PledgeOfDefense] =
    CatalogCards.denizen(catalog, id).map(new PledgeOfDefense(_, catalog))
```

- [ ] **Step 4: Register them in `PlanRules`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers, Storm Caller, Rival Khan,
  * Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again) and Hospital (killed warbands saved
  * until the end). A power whose card is absent from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      GreatCrusade.forCatalog(catalog).toVector ++
      PledgeOfDefense.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 9 new tests (6 and 3), and every existing campaign plan suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/GreatCrusade.scala src/main/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefense.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/PledgeOfDefenseSuite.scala
git commit -m "feat(powers): add Great Crusade and Pledge of Defense"
```

---

### Task 3: `SingleShields` (P2), then Rain Boots

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SingleShields.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/RainBoots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/RainBootsSuite.scala`

**Interfaces:**
- Consumes: `PlanDiscard.afterCampaign/discarded`, `BattlePlan.wrapping`, `CampaignIds.defensePool`, `DefenseDieFace.score`.
- Produces, `private[campaign]`, used by `RainBootsSuite`:
  - `SingleShields.ignored: NoteKey`
  - `SingleShields.score(faces: Vector[DefenseDieFace]): Int`
  - `SingleShields.ignore(power: PowerId, source: PowerSourceRef): Vector[Operation]`
- Keeps: `BagOfSiegeworks.ignored: NoteKey` and `BagOfSiegeworks.score(faces): Int`, which its suite reads.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/RainBootsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Rain Boots: a free attacker's plan that scores every single-shield defense
  * die as nothing, as Bag of Siegeworks does, and the card is discarded after
  * the Campaign. */
class RainBootsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.rain-boots")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val bag = relicWith("relic.bag-of-siegeworks")
  private val bagRef: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(bag))
  private val ignored = NoteText.Said("ignored", "Single shields ignored.",
    covers = false)
  private val discardedLine = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  /** Every attack die a sword. The first defense die a single shield, the
    * rest two shields: the origin rolls one of each. */
  private val shields: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense => Vector.tabulate(count)(index =>
      (if index == 0 then DefenseDieFace.OneShield
       else DefenseDieFace.TwoShields): DieFace))

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    RainBoots.id, Vector(SingleShields.ignored, PlanDiscard.discarded),
    run.events)

  test("the defense is scored without its single shields, the card is " +
      "discarded, and both are said"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(shields), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finish
    // A single shield and two shields, plus the two bandits: the single
    // shield scores nothing.
    assertEquals(result(done).defenseScore,
      SingleShields.score(result(done).defenseFaces) + 2)
    assertEquals(result(done).defenseScore, 4)
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(ignored, discardedLine))

  test("without a single shield rolled only the discard is said"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(lines(done), Vector(discardedLine))

  test("it is offered in a Raid"):
    val b = withAdviser(withEnemyAtOrigin(board()), card, Orientation.FaceUp)
    assert(commit(rules(winning), b, 3, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("with Bag of Siegeworks also chosen, the defense is scored the same"):
    val b = withSecrets(withRelic(withAdviser(board(), card,
      Orientation.FaceUp), bag), 1)
    val done = commit(rules(shields), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, bagRef)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(result(done).defenseScore, 4)
    assertEquals(lines(done), Vector(ignored, discardedLine))
    assertEquals(NoteText.said(BagOfSiegeworks.id,
      Vector(BagOfSiegeworks.ignored), done.events), Vector(ignored))

  test("a Campaign that does not choose it scores the single shields and " +
      "keeps the card"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(shields), b, 3).finish
    assertEquals(result(done).defenseScore, 5)
    assert(!discarded(done.state))
    assertEquals(lines(done), Vector.empty)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.RainBootsSuite"`
Expected: compile failure, `Not found: SingleShields`.

- [ ] **Step 3: Create `SingleShields`**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/SingleShields.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

/** The single-shield rescoring of the defense, for the plans that ignore the
  * defense dice showing a single shield (Bag of Siegeworks, Rain Boots). Once
  * the defense is rolled, each single-shield die scores 0; two shields and
  * doublers score as usual, so Blank, OneShield and Doubler score 0. The score
  * is written again before the defender's force is added to it
  * (`CampaignDefenseResult`), so the recorded defense carries the change. It
  * is computed from the faces, not from the current score, so two plans that
  * both rescore write the same score.
  */
private[campaign] object SingleShields:
  /** "Single shields ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseDieFace.score(faces.filterNot(_ == DefenseDieFace.OneShield))

  /** What a plan adds before the defense result's own children: the
    * rescoring, then `power`'s line from `source`, written only when a single
    * shield was rolled. */
  def ignore(power: PowerId, source: PowerSourceRef): Vector[Operation] =
    Vector(
      BuildOps((ready, _) => Right(rescored(ready))),
      Note(power, states => Option.when(
        faces(states.now).contains(DefenseDieFace.OneShield))(ignored(source))))

  /** Nothing is written when no single shield was rolled. */
  private def rescored(ready: ReadyGame): Vector[CoreOperation] =
    val rolled = faces(ready)
    if !rolled.contains(DefenseDieFace.OneShield) then Vector.empty
    else Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(score(rolled))))

  private def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })
```

- [ ] **Step 4: Make Bag of Siegeworks use it**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Bag of Siegeworks (relic R37), an attacker's battle plan: "[secret] If
  * you're targeting sites, ignore [defense-die] rolls with a single
  * [shield]."
  *
  * The relic must be faceup in the attacker's play area, and a secret is
  * placed onto it. It is offered only in a Conquest, because a Raid targets
  * no site. Once the defense is rolled, each single-shield die scores 0; two
  * shields and doublers score as usual. The rescoring is `SingleShields`,
  * which Rain Boots shares; it is written before the defender's force is
  * added, so the recorded defense carries the change.
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
      SingleShields.ignore(id, PowerSourceRef.Card(relicId)) ++ children))

object BagOfSiegeworks:
  val id: PowerId = PowerId("relic.bag-of-siegeworks")

  /** "Single shields ignored." */
  val ignored: NoteKey = SingleShields.ignored

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int = SingleShields.score(faces)

  def forCatalog(catalog: ExecutableCatalog): Option[BagOfSiegeworks] =
    CatalogCards.relic(catalog, id).map(new BagOfSiegeworks(_))
```

- [ ] **Step 5: Create Rain Boots**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/RainBoots.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Rain Boots (card 14), an attacker's battle plan: "Ignore all your enemy's
  * rolls of single shields [shield]. At end, discard Rain Boots."
  *
  * It is free. Only the defender rolls shields, so it is the attacker's plan.
  * Once the defense is rolled, each single-shield die scores 0, exactly as for
  * Bag of Siegeworks (`SingleShields`); two shields and doublers are
  * unaffected. Unlike the Bag it is offered in a Raid too. With the Bag also
  * chosen, both write the same score. The card is discarded through the
  * standard discard once the Campaign has resolved, whoever won, as Horse
  * Archers is.
  */
final case class RainBoots private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = RainBoots.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] =
    Vector(SingleShields.ignored, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Rain Boots: ignore single shields", Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> ((_, children) =>
      SingleShields.ignore(id, PowerSourceRef.Card(cardId)) ++ children))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object RainBoots:
  val id: PowerId = PowerId("denizen.rain-boots")

  def forCatalog(catalog: ExecutableCatalog): Option[RainBoots] =
    CatalogCards.denizen(catalog, id).map(new RainBoots(_, catalog))
```

- [ ] **Step 6: Register it in `PlanRules`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers, Storm Caller, Rival Khan,
  * Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again), Rain Boots (the defense scored again
  * and a discard at the end) and Hospital (killed warbands saved until the
  * end). A power whose card is absent from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      GreatCrusade.forCatalog(catalog).toVector ++
      PledgeOfDefense.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      RainBoots.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
```

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 5 new tests, and `BagOfSiegeworksSuite` passes unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/SingleShields.scala src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala src/main/scala/oathdigital/gameplay/powers/campaign/RainBoots.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/RainBootsSuite.scala
git commit -m "feat(powers): share the single-shield rescoring and add Rain Boots"
```

---

### Task 4: `Outriders.ignoreSkulls`, then The Great Levy

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/GreatLevy.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/GreatLevySuite.scala`

**Interfaces:**
- Consumes: `PlanContext.enemyHolds`, `PlanDice.label/effect`, `CampaignBattle.attackFacesOf`, `AttackDieFace.score/skulls`, `PlanUse.side`.
- Produces: `Outriders.ignoreSkulls(power: PowerId, source: PowerSourceRef): Vector[Operation]`, `private[campaign]`.
- Keeps: `Outriders.ignored: NoteKey`, which its suite and The Great Levy read.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/GreatLevySuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** The Great Levy: two favor placed for three attack dice, added or removed,
  * unless the enemy holds the People's Favor; an attacker also ignores every
  * skull, as with Outriders. */
class GreatLevySuite extends munit.FunSuite:
  private val card = cardWith("denizen.the-great-levy")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val outriders = cardWith("denizen.outriders")
  private val outridersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(outriders))
  private val line = NoteText.Said("ignored", "Skulls ignored.", covers = false)

  /** The first attack die two swords and a skull, the rest one sword; every
    * defense die blank. */
  private val skull: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.tabulate(count)(index =>
      (if index == 0 then AttackDieFace.TwoSwordsSkull
       else AttackDieFace.OneSword): DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace))

  /** `who` holds The Great Levy and `favor` favor. */
  private def holder(b: Board, who: PlayerId, favor: Int = 2): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, favor))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(GreatLevy.id, Vector(Outriders.ignored), run.events)

  test("an attacker places two favor for three attack dice, ignores every " +
      "skull, and says so"):
    val base = board()
    val b = holder(base, base.actor)
    val run = commit(rules(skull), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(2, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)
    val done = picked.finish
    assertEquals(result(done).skullLosses, 0)
    assertEquals(result(done).attackScore,
      AttackDieFace.score(result(done).attackFaces))
    assertEquals(lines(done), Vector(line))

  test("it is not offered while the defender holds the People's Favor"):
    val base = againstPlayer(board())
    val funded = holder(base, base.actor)
    assert(commit(rules(winning), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = on(funded)(_.peoplesFavor(Some(base.other), 1))
    assert(!commit(rules(winning), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered to a player with one favor"):
    val base = board()
    val b = holder(base, base.actor, favor = 1)
    assert(!commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes three attack dice, and the attack's skulls " +
      "still count"):
    val base = againstPlayer(board())
    val b = holder(base, base.other)
    val run = commit(rules(skull), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // One die is left, and it rolls the skull.
    val done = run.finish
    assertEquals(result(done).skullLosses, 1)
    assertEquals(lines(done), Vector.empty)

  test("with Outriders also chosen, the attack is scored the same"):
    val base = board()
    val b = withAdviser(holder(base, base.actor), outriders, Orientation.FaceUp)
    val done = commit(rules(skull), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, outridersRef)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(result(done).skullLosses, 0)
    assertEquals(result(done).attackScore,
      AttackDieFace.score(result(done).attackFaces))
    assertEquals(lines(done), Vector(line))
    assertEquals(NoteText.said(Outriders.id, Vector(Outriders.ignored),
      done.events), Vector(line))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GreatLevySuite"`
Expected: compile failure, `Not found: GreatLevy`.

- [ ] **Step 3: Move Outriders' rewrite into its companion**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Outriders (card 104), an attacker's battle plan: "Ignore all skulls you roll."
  *
  * Choosing it costs nothing and changes no dice. Once the attack is scored, the
  * plan writes the roll outcome again without the skull cap, so no skull removes
  * a warband and every skull face keeps its two swords. A facedown Outriders is
  * revealed when it is chosen, as any plan's source is. The rewrite is
  * `Outriders.ignoreSkulls`, which The Great Levy shares.
  *
  * When the attack rolled a skull it writes "Skulls ignored."
  */
final case class Outriders private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Outriders.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Outriders: ignore all attack skulls", Vector.empty, Vector.empty))

  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (_ =>
      Outriders.ignoreSkulls(id, PowerSourceRef.Card(cardId))))

object Outriders:
  val id: PowerId = PowerId("denizen.outriders")
  /** "Skulls ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Skulls ignored.")))

  /** The attack written again without the skull cap, then `power`'s "Skulls
    * ignored." line from `source`, only when the attack rolled a skull to
    * ignore. The rewrite keeps the faces, so they still show the skulls. It
    * is computed from the faces, so two plans that both ignore the skulls
    * write the same score (Outriders, The Great Levy). */
  private[campaign] def ignoreSkulls(power: PowerId, source: PowerSourceRef)
      : Vector[Operation] = Vector(
    BuildOps((ready, _) =>
      Right(ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .map(_ => ModifyRollOutcome(CampaignIds.attackPool, Some(0),
          Some(AttackDieFace.score(CampaignBattle.attackFacesOf(ready))))))),
    Note(power, states => Option.when(
      AttackDieFace.skulls(CampaignBattle.attackFacesOf(states.now)) > 0)(
      ignored(source))))

  def forCatalog(catalog: ExecutableCatalog): Option[Outriders] =
    CatalogCards.denizen(catalog, id).map(new Outriders(_))
```

- [ ] **Step 4: Create The Great Levy**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/GreatLevy.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** The Great Levy (card 137), a battle plan for either side: "[favor] [favor]
  * ±3 [attack-die] and ignore all skulls you roll, unless your enemy has the
  * People's Favor."
  *
  * Two favor are placed onto the card. An attacker adds three attack dice; a
  * defender removes three. It is offered only while the enemy does not hold
  * the People's Favor; bandits hold no banner. For an attacker it also ignores
  * every skull the attack rolls, exactly as Outriders does
  * (`Outriders.ignoreSkulls`), with Outriders' line. A defender rolls no
  * attack dice, so for a defender it only removes three. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class GreatLevy private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = GreatLevy.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => !context.enemyHolds(Banner.PeoplesFavor))
      .map(source => CampaignPlanOffer(source, GreatLevy.label(context.side),
        Vector(CampaignPlanCost.Favor(2)),
        Vector(PlanDice.effect(context.side, GreatLevy.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side == CampaignPlanSide.Attacker then
        Outriders.ignoreSkulls(id, PowerSourceRef.Card(cardId))
      else Vector.empty))

object GreatLevy:
  val id: PowerId = PowerId("denizen.the-great-levy")
  val Dice: Int = 3

  /** "The Great Levy: add 3 attack dice and ignore all attack skulls" for an
    * attacker, "The Great Levy: remove 3 attack dice" for a defender. */
  private def label(side: CampaignPlanSide): String =
    val dice = PlanDice.label("The Great Levy", side, Dice)
    side match
      case CampaignPlanSide.Attacker => s"$dice and ignore all attack skulls"
      case CampaignPlanSide.Defender => dice

  def forCatalog(catalog: ExecutableCatalog): Option[GreatLevy] =
    CatalogCards.denizen(catalog, id).map(new GreatLevy(_))
```

- [ ] **Step 5: Register it in `PlanRules`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers, Storm Caller, Rival Khan,
  * Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again), Rain Boots (the defense scored again
  * and a discard at the end), The Great Levy (the attack scored again) and
  * Hospital (killed warbands saved until the end). A power whose card is
  * absent from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      GreatCrusade.forCatalog(catalog).toVector ++
      PledgeOfDefense.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      RainBoots.forCatalog(catalog).toVector ++
      GreatLevy.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
```

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 6 new tests, and `OutridersSuite` passes unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala src/main/scala/oathdigital/gameplay/powers/campaign/GreatLevy.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/GreatLevySuite.scala
git commit -m "feat(powers): share the skull rewrite and add The Great Levy"
```

---

### Task 5: Garrison Armory

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/GarrisonArmory.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/GarrisonArmorySuite.scala`

**Interfaces:**
- Consumes: `CampaignSetup.setup(ready, actor, pending): Option[CampaignSetup]`, `CampaignBattle.defenderForce(ready, setup): Int`, `CampaignIds.defensePool`, `NoteStates.previous`, `PlanUse.actor`.
- Produces: `GarrisonArmory.added: NoteKey`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/GarrisonArmorySuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Garrison Armory: a defender's plan, a favor placed, in a Conquest, so that
  * the warbands at the targets add their defense twice. */
class GarrisonArmorySuite extends munit.FunSuite:
  private val card = cardWith("denizen.garrison-armory")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val bag = relicWith("relic.bag-of-siegeworks")
  private val bagRef: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(bag))
  private val provisions = cardWith("denizen.extra-provisions")
  private val twoAdded = NoteText.Said("added",
    "Warbands on the targets added 2 more defense.", covers = false)

  /** Every attack die a sword; the defense dice are `faces`, in turn. */
  private def defense(faces: DefenseDieFace*): WalkerDice = (kind, count) =>
    Right(kind match
      case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
      case DiceKind.Defense =>
        Vector.tabulate(count)(index => faces(index % faces.size): DieFace))

  /** The other player rules the origin with two warbands and holds Garrison
    * Armory and a favor. */
  private def defender: Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    GarrisonArmory.id, Vector(GarrisonArmory.added), run.events)

  test("a defender places a favor, off turn, and the targets' warbands add " +
      "their defense twice, and it says so"):
    val b = defender
    val run = commit(rules(defense(DefenseDieFace.TwoShields)), b, 5)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.favor, 0)
    val done = picked.finish
    // Two dice of two shields, then the two warbands at the origin, twice.
    assertEquals(result(done).defenseScore, 4 + 2 + 2)
    assertEquals(lines(done), Vector(twoAdded))

  test("the doubler multiplies the dice only"):
    val done = commit(rules(defense(DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler)), defender, 5)
      .pick(defender.other, CampaignIds.defenderPlan, ref).finish
    // Two shields doubled is 4; the warbands add 2, then 2 more, undoubled.
    assertEquals(result(done).defenseScore, 8)

  test("single shields are ignored before the warbands are added"):
    val b = withSecrets(withRelic(defender, bag), 1)
    val bagged = commit(rules(defense(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields)), b, 5)
      .pick(b.actor, CampaignIds.attackerPlan, bagRef)
    val attackDone =
      if awaits(bagged, b.actor, CampaignIds.attackerPlan) then
        bagged.answer(b.actor, CampaignIds.attackerPlan,
          ChooseOneAnswer(CampaignIds.finish))
      else bagged
    val done = attackDone.pick(b.other, CampaignIds.defenderPlan, ref).finish
    // The single shield scores nothing, so the dice score 2; the warbands add
    // 2, twice.
    assertEquals(result(done).defenseScore, 6)
    assertEquals(lines(done), Vector(twoAdded))

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board())
    // Extra Provisions keeps the defender's window open.
    val b = on(withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, provisions, Orientation.FaceUp))(
      _.favor(base.other, 2))
    val run = commit(rules(winning), b, 3, raid = true)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GarrisonArmorySuite"`
Expected: compile failure, `Not found: GarrisonArmory`.

- [ ] **Step 3: Create Garrison Armory**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/GarrisonArmory.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Garrison Armory (card 255), a defender's battle plan: "[favor] In a
  * conquest, warbands on targeted sites each add +2 defense (instead of
  * +1)."
  *
  * A favor is placed onto the card. It is offered only in a Conquest. When the
  * defense is scored, the warbands at the targets are added once more, after
  * the window's own step has written the dice score plus the force
  * (`CampaignBattle.defenseResultOps`). So it comes after any single-shield
  * rescoring, which applies to the dice only, and the doubler, which the dice
  * score already holds, multiplies the dice only. Bandits pay nothing, so a
  * bandit defender never applies it.
  *
  * When the targets held a warband it writes "Warbands on the targets added
  * {n} more defense."
  */
final case class GarrisonArmory private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = GarrisonArmory.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(GarrisonArmory.added)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Garrison Armory: warbands on the targets add 2 defense each",
        Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use => Vector(
      BuildOps((ready, pending) => CampaignSetup.setup(ready, use.actor, pending)
        .toRight(OathViolation.InvalidEventOrder(
          "Garrison Armory reached the defense without a Campaign setup"))
        .map(GarrisonArmory.again(ready, _))),
      Note(id, states => states.previous.flatMap { case (before, after) =>
        val added = GarrisonArmory.defense(after) - GarrisonArmory.defense(before)
        Option.when(added > 0)(GarrisonArmory.added(PowerSourceRef.Card(cardId),
          NoteArg.Number(added)))
      }))))

object GarrisonArmory:
  val id: PowerId = PowerId("denizen.garrison-armory")

  /** "Warbands on the targets added {n} more defense." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Warbands on the targets added "), NotePart.Arg(0),
    NotePart.Text(" more defense.")))

  /** The recorded defense score. */
  private def defense(ready: ReadyGame): Int =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).fold(0)(_.score)

  /** The recorded defense with the targets' warbands added once more. Nothing
    * is written when the targets hold none. */
  private def again(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] =
    val force = CampaignBattle.defenderForce(ready, setup)
    Option.when(force > 0)(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(defense(ready) + force))).toVector

  def forCatalog(catalog: ExecutableCatalog): Option[GarrisonArmory] =
    CatalogCards.denizen(catalog, id).map(new GarrisonArmory(_))
```

- [ ] **Step 4: Register it in `PlanRules`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers, Storm Caller, Rival Khan,
  * Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again), Rain Boots (the defense scored again
  * and a discard at the end), The Great Levy (the attack scored again),
  * Garrison Armory (the targets' warbands added again) and Hospital (killed
  * warbands saved until the end). A power whose card is absent from `catalog`
  * is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      GreatCrusade.forCatalog(catalog).toVector ++
      PledgeOfDefense.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      RainBoots.forCatalog(catalog).toVector ++
      GreatLevy.forCatalog(catalog).toVector ++
      GarrisonArmory.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 5 new tests.

If the setup does not resolve inside the step (the `InvalidEventOrder` above), stop: plan-time ruling 6 is wrong. Report it before choosing another way to read the targets.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/GarrisonArmory.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/GarrisonArmorySuite.scala
git commit -m "feat(powers): add Garrison Armory"
```

---

### Task 6: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
Slice 1a is done: the battle plans Cracking Ground, Walled Garden, Banner
Breakers, Extra Provisions, Village Constable, Encirclement, Bandit Standard
and Rival Khan. Slices 1b to 4 remain.
```

with:

```markdown
Slice 1a is done: the battle plans Cracking Ground, Walled Garden, Banner
Breakers, Extra Provisions, Village Constable, Encirclement, Bandit Standard
and Rival Khan. Slice 1b is done: the battle plans Disgraced Captain, Battle
Axes, Great Crusade, Pledge of Defense, The Great Levy, Rain Boots and
Garrison Armory. Slices 1c to 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 38 (13 in Task 1, 9 in Task 2, 5 in Task 3, 6 in Task 4, 5 in Task 5). From a baseline of 2196, that is 2234. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 1b"
```
