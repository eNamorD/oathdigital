# Catalog Batch 4, Slice 1a (Battle Scoring) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement nine battle plans, Rusting Ray (57), Fae Battalion (215), War Tortoise (187), Lancers (154), Fearsome General (253), Kindred Warriors (60), Rangers (45), Mounted Patrol (163) and Scouts (8), on top of a shared attack scorer and defense scorer.

**Architecture:**

- **R1, the attack scorer.** `AttackScore` scores the attack from the faces rolled, the committed force and every chosen plan in its `changes` table.
  - Each plan that changes the attack appends `AttackScore.rescore` at `CampaignAttackResult`, and so do Outriders and The Great Levy.
  - Every rescoring plan writes the same total, so their fold order no longer matters.
- **R2, the defense scorer.** `DefenseScore` does the same for the defense: face filters, then doublers, then the defender's force.
  - Rain Boots, Bag of Siegeworks, Garrison Armory and War Tortoise write through it.
- **The nine cards.** Each is a `BattlePlan` (`src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`), and each card's object moves out of `catalog/holding/` into its plan file.
- **Registration.** Scouts registers in `SimplePlans`. The other eight register in `PlanRules`.
- **No engine change.** No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change. Every change is inside `gameplay/powers/`, plus the card objects moving between `catalog/holding/` and `gameplay/cards/NewFoundations.scala`'s import.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md` ("Powers-side refactors", "Seams used for the first time", "Log lines", "Slice 1: battle plans", "Testing"), with the per-card rulings in `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md` ("Slice 1a: battle scoring"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs. When a refactor removes the last use of an import, remove the import.
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
- Log lines, exactly (spec, "Log lines", "Slice 1: battle plans"). None names the acting player.

  | Card | Key | Line | When |
  |---|---|---|---|
  | Rusting Ray | `ignored` | Rusting Ray: Hollow swords ignored. | a hollow sword was rolled |
  | Fae Battalion | `counted` | Fae Battalion: Hollow swords counted as swords. | a hollow sword was rolled |
  | War Tortoise, defender | `attack` | War Tortoise: Double swords added no attack. | a skull face was rolled |
  | War Tortoise, attacker | `defense` | War Tortoise: Double shields added no defense. | a double shield was rolled |
  | Lancers | `doubled` | Lancers: The attack roll was doubled. | the attack rolled a die |
  | Fearsome General | `added` | Fearsome General: Skulls added {n} more swords. | it added a sword |
  | Kindred Warriors, attacker | `ignored` | Kindred Warriors: Skulls ignored. | a skull was rolled |
  | Rangers | `ignored` | Rangers: Skulls ignored. | a skull was rolled |
  | Mounted Patrol | `halved` | Mounted Patrol: The attack pool was halved to {n} dice. ("1 die" for one) | always |
  | Scouts | `gained` | Scouts: Gained {n} Supply. | Supply was gained |
  | Lancers, Mounted Patrol | `discarded` | {Card}: Discarded after the Campaign. | the discard happened |

  No line for Kindred Warriors' and Rangers' dice: their effect there is only a dice change.
- Batch rulings that every task applies (rulings, "Slice 1a"; batch 3 rulings, "Rules that apply to the whole batch"):
  - **Sign.** "±" is fixed by the side: an attacker adds attack dice, a defender removes them (`PlanDice`).
  - **Zero.** A dice plan whose count would be zero is not offered, unless it does something else too (Kindred Warriors for an attacker, Rangers).
  - **Bandit defenders** apply their free plans without choosing, and never use a plan that costs. `CampaignPlanChoice` already does this.
- Baseline: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end. This plan adds 38 server tests.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** The spec's Testing section says `PowerImplementationStatusSuite` and `PowerKindsCatalogSuite` pin the new cards. As in batch 3 (slice 1a plan, ruling 1), the per-card registration pins were pruned, and `PowerKindsCatalogSuite` does not list battle plans. Every suite below runs through `WalkerPowerCatalog.default`, so an unregistered plan is never offered and its suite fails.
2. **The scorers find chosen plans by card and side.** `AttackScore.changes` and `DefenseScore.changes` list each rescoring plan's source ref, the side it must be chosen on, and its change to the rules. They read `PlanUse.chosen`, which already covers a bandit defender's applied plan. The tables name the cards' ids, not the plan objects, so no object depends on another at initialisation. A plan that rescores but is missing from its table scores as if it were not chosen, and its own suite fails.
3. **A rescore writes only a changed score.** `rescore` writes `ModifyRollOutcome` only when the computed score, or the attack's skull loss, differs from the recorded one. The Game Log tells only the last write of a pool (`CampaignLines.rewritten`), so extra writes would be harmless, but fewer recorded operations keep the existing operation-list assertions unchanged.
4. **Rain Boots and Bag of Siegeworks move from `wrapping` to `later`.** They now write after the window's own step, which writes dice plus force, so their rescore must write the full total. A `wrapping` rescore would have the force added twice. The visible effect: their line now follows the "Defense: n" line, as Outriders' line already follows "Attack: n".
5. **Garrison Armory's line reads the force.** Today its line is the score change of its own write. Once several plans write the same total, the first to fold makes the whole change, so the line reads `CampaignBattle.defenderForce` instead, in a `Branch` evaluated when the plan's ops run.
6. **When each line is written.** Rusting Ray and Fae Battalion write when a hollow sword was rolled, as Outriders does for a skull. Lancers writes when the attack rolled at least one die. Fearsome General's `{n}` is the score with the plan minus the score without it (`AttackScore.addedBy`), and its line is written when that is above zero, so an unpayable skull or War Tortoise silences it. Kindred Warriors and Rangers reuse `Outriders.ignored`, the "Skulls ignored." line, through `Outriders.ignoreSkulls`.
7. **Kindred Warriors' X** is `RuledCards` per suit, through `PlanContext.cardsRuled`: the user's faceup advisers and the faceup denizens and edifices at sites they rule. Arcane is excluded, so Kindred Warriors itself never counts. Revealing a facedown Kindred Warriors when it is chosen cannot change X.
8. **Rangers reads the defense pool's count** (`rollPools(defensePool).count`) when it is offered. The attacker's window runs before the defender's, so dice the defender adds later cannot count.
9. **Mounted Patrol halves in a `wrapping` at `CampaignAttackRoll`.** This is the leaf `Roll`, whose fold is `Vector(roll)` (`ProcedureWalker.walkLeaf`). The halving is a `BuildOps` before the roll that reads the live pool, so defender plans chosen after it still count. It writes nothing when the cut is zero. This is the spec's first-use seam. **If the halving does not take effect, stop and report; do not change the walker.**
10. **Scouts gains through `CampaignPlanEffect.Run`**: a `GainSupply`, then its line. `GainSupply` caps at `SupplyTrack.Maximum`. A Campaign spends a Supply before the plans, so a plain Campaign cannot reach Scouts at the cap, and no test pins the cap.
11. **Registration.** Scouts needs nothing beyond the plan window, so it registers in `SimplePlans`. The other eight register in `PlanRules`, which holds the plans that reach later windows.
12. **`PlanContext.holds(banner)`** reads whether the plan's user holds a banner. Fire Talkers switches to it, with its suite unchanged.
13. **The spec's "Verify at plan time" items for this slice.** Only "Fold order" applies. Task 3's `ScoringFoldOrderSuite` runs the same Campaigns with the rescoring plans folding in reverse id order, through a test-only wrapper that changes a power's `priority`, which the fold sorts by first (`ContributingPower.sortKey`).

## File Structure

Production, all under `src/main/scala/oathdigital/gameplay/powers/campaign/` unless stated:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `AttackScore.scala` | Create | R1: `HollowWorth`, `AttackRules`, `AttackChange`, `AttackScore` | 1 (rows added in 2, 3, 4) |
| `DefenseScore.scala` | Create | R2: `DefenseRules`, `DefenseChange`, `DefenseScore` | 1 (row added in 3) |
| `Outriders.scala` | Modify | `ignoreSkulls` writes through `AttackScore` | 1 |
| `GreatLevy.scala` | Modify | Passes its use to `ignoreSkulls` | 1 |
| `SingleShields.scala` | Modify | Writes through `DefenseScore` | 1 |
| `RainBoots.scala`, `BagOfSiegeworks.scala` | Modify | Rescore in `later`, not `wrapping` | 1 |
| `GarrisonArmory.scala` | Modify | Writes through `DefenseScore`; its line reads the force | 1 |
| `PlanContext.scala` | Modify | Adds `holds(banner)` | 2 |
| `FireTalkers.scala` | Modify | Uses `holds` | 2 |
| `RustingRay.scala`, `FaeBattalion.scala` | Create | Hollow swords | 2 |
| `WarTortoise.scala`, `Lancers.scala`, `FearsomeGeneral.scala` | Create | Skull faces, doubling, double shields | 3 |
| `KindredWarriors.scala`, `Rangers.scala` | Create | Skulls ignored, with dice | 4 |
| `MountedPatrol.scala`, `Scouts.scala` | Create | The halved pool; a Supply | 5 |
| `PlanRules.scala` | Modify | Registers eight plans | 2, 3, 4, 5 |
| `SimplePlans.scala` | Modify | Registers Scouts | 5 |
| `src/main/scala/oathdigital/catalog/holding/Holding{Arcane,Beast,Nomad,Order}Denizens.scala` | Modify | Each card's object leaves | 2, 3, 4, 5 |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | Modify | Imports each moved card object | 2, 3, 4, 5 |

Tests, all under `src/test/scala/oathdigital/gameplay/powers/campaign/`:

| File | Task |
|---|---|
| `AttackScoreSuite.scala`, `DefenseScoreSuite.scala` | 1 |
| `RustingRaySuite.scala`, `FaeBattalionSuite.scala` | 2 |
| `WarTortoiseSuite.scala`, `LancersSuite.scala`, `FearsomeGeneralSuite.scala`, `ScoringFoldOrderSuite.scala` | 3 |
| `KindredWarriorsSuite.scala`, `RangersSuite.scala` | 4 |
| `MountedPatrolSuite.scala`, `ScoutsSuite.scala` | 5 |

Docs: `docs/ROADMAP.md` (Task 6).

### How the suites drive a Campaign

Read `PlanDriver.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` before writing a suite. In short:

- `board(extras, warbands, supply)`: p1, the actor, stands at Ancient City (the origin, printed defense 2), which two bandits rule. `extras` further sites are bandit-ruled too, in map order: Broken Peaks (defense 2), then Buried Giant. p2 (`b.rival`) and p3 stand at the first site nobody rules. Every other site starts empty.
- `rivalRulesOriginAndHoldsOathkeeper(b)`: p2 rules the origin with two warbands and holds the Oathkeeper title, so p2 is offered the title's defense plan. That keeps p2's plan window open, so a "not offered" check against a player defender is not vacuous.
- `commit(rules(dice), b, force, targets, raid)` starts the Campaign and answers the kind, the targets and the force. The origin is always a target; `targets` adds more.
- `run.pick(who, CampaignIds.attackerPlan | defenderPlan, ref)` chooses a plan. `run.finishPlanWindows` finishes every window and answers the sacrifice and placement with 0, until the Campaign ends.
- `run.ops` is every recorded operation; `picked.since(run)` is only what the pick recorded. Use `since` when a dice change could equal the force or the printed defense.
- `run.result` is the recorded `CampaignResult`: `attackScore`, `skullLosses`, `attackFaces`, `defenseScore`, `defenseFaces`.
- `run.offers(actor, who, id, ref)` is whether the run waits on `who` at `id` with `ref` offered. `run.offered(actor)` always takes the Campaign's actor, even in the defender's window.
- Dice: `winningDice` (every attack die a sword, every defense die blank), `losingDice` (every attack die a hollow sword), `WalkerDiceFixture.skullThenSwords` (first attack die a skull face), `swordsCyclingDefense(faces*)`, and `dice(attack, defense)`, which must match each pool's size exactly.
- `NoteText.said(powerId, noteKeys, run.events)` is a power's lines as `Said(key, text, covers)`.
- Table steps through `on(b)(_ ...)`: `.favor(player, n)`, `.secrets(player, faceUp = n)`, `.darkestSecret(Some(player), secrets = n)`.

### How to move a card object out of `catalog/holding/`

Each task moves its cards' objects with this script, which deletes one `object ... extends Denizen(...)` block from a holding file:

```bash
python3 - FILE OBJECT <<'PY'
import sys
from pathlib import Path
path, name = sys.argv[1], sys.argv[2]
p = Path(path)
s = p.read_text()
start = s.index(f"\nobject {name} extends ") + 1
end = s.find("\n\nobject ", start)
s = s[:start] + (s[end + 2:] if end != -1 else "")
p.write_text(s.rstrip("\n") + "\n")
PY
```

Replace `FILE` and `OBJECT` with the values the task gives. The plan file then defines the object exactly as the holding file did. In the same step, add the object's name to the `import oathdigital.gameplay.powers.campaign.{...}` list in `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, in alphabetical order, rewrapping lines at 80 columns. `NewFoundations` already lists each card by name, and its wildcard `catalog.holding._` import no longer finds it.

---

### Task 1: The attack and defense scorers (R1, R2)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/DefenseScore.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/GreatLevy.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SingleShields.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/RainBoots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/GarrisonArmory.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/DefenseScoreSuite.scala`

**Interfaces:**
- Consumes: `PlanUse.chosen(ready, pending, actor, ref, sides, afterOutcome): Option[PlanUse]`, `CampaignSetup.setup(ready, actor, pending): Option[CampaignSetup]`, `CampaignBattle.attackFacesOf(ready): Vector[AttackDieFace]`, `CampaignBattle.defenderForce(ready, setup): Int`.
- Produces, used by Tasks 2 to 5:
  - `enum HollowWorth { case Half, Whole, Zero }`
  - `AttackRules(hollow: HollowWorth = Half, skullSwords: Int = 2, doubled: Boolean = false, skullsIgnored: Boolean = false, skullFacesScoreNothing: Boolean = false)`
  - `AttackChange(ref: DecisionOptionRef, side: CampaignPlanSide, change: AttackRules => AttackRules)`
  - `AttackScore.of(faces, force, rules): (Int, Int)` (score, skulls lost); `AttackScore.changes: Vector[AttackChange]`; `AttackScore.rules(ready, pending, actor): AttackRules`; `AttackScore.addedBy(ready, pending, actor, without: AttackRules => AttackRules): Int`; `AttackScore.rescore(actor: PlayerId): Operation`; `AttackScore.hollowRolled(ready): Boolean`; `AttackScore.skullRolled(ready): Boolean`
  - `DefenseRules(singleShieldsIgnored = false, doubleShieldsIgnored = false, forceTwice = false)`, `DefenseChange(ref, side, change)`
  - `DefenseScore.dice(faces, rules): Int`; `DefenseScore.of(faces, force, rules): Int`; `DefenseScore.changes`; `DefenseScore.rules(ready, pending, actor)`; `DefenseScore.faces(ready): Vector[DefenseDieFace]`; `DefenseScore.rescore(actor: PlayerId): Operation`
  - `Outriders.ignoreSkulls(use: PlanUse, power: PowerId, source: PowerSourceRef): Vector[Operation]`
  - `SingleShields.ignore(use: PlanUse, power: PowerId, source: PowerSourceRef): Vector[Operation]`

- [ ] **Step 1: Write the failing suites**

Create `AttackScoreSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model.AttackDieFace
import oathdigital.model.AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}

/** The attack scorer's steps (catalog batch 4 rulings, "The attack score"):
  * face values, Lancers' doubling, the skull cap, War Tortoise, the total. */
class AttackScoreSuite extends munit.FunSuite:
  private def check(faces: Vector[AttackDieFace], force: Int,
      rules: AttackRules)(score: Int, lost: Int)(using munit.Location): Unit =
    assertEquals(AttackScore.of(faces, force, rules), (score, lost))

  test("with no plan chosen it scores as the printed rules do"):
    val rolls = Vector(Vector(OneSword, HollowSword, HollowSword, HollowSword),
      Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), Vector(TwoSwordsSkull),
      Vector.empty)
    for faces <- rolls; force <- 0 to 3 do
      assertEquals(AttackScore.of(faces, force, AttackRules()),
        CampaignBattle.attackResult(faces, force, ignoreSkulls = false))

  test("a hollow sword is worth half, a whole sword with Fae Battalion, and " +
      "nothing with Rusting Ray"):
    val faces = Vector.fill(3)(HollowSword)
    check(faces, 3, AttackRules())(1, 0)
    check(faces, 3, AttackRules(hollow = HollowWorth.Whole))(3, 0)
    check(faces, 3, AttackRules(hollow = HollowWorth.Zero))(0, 0)

  test("Lancers doubles every face, skulls included, before the skull cap"):
    // Two hollow swords make one sword, two swords, and two payable skulls
    // of two swords each.
    check(Vector(HollowSword, OneSword, TwoSwordsSkull), 5,
      AttackRules(doubled = true))(1 + 2 + 4, 2)

  test("the rulings' example: Lancers and Fearsome General with a force of 3"):
    // Doubled: four skulls and two swords. Three skulls are payable and score
    // 12, the fourth scores 0, the swords add 2.
    check(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 3,
      AttackRules(doubled = true, skullSwords = 4))(14, 3)

  test("an unpayable skull scores nothing, with Fearsome General too"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    check(faces, 1, AttackRules())(2, 1)
    check(faces, 1, AttackRules(skullSwords = 4))(4, 1)

  test("an ignored skull scores its swords and kills nothing, four with " +
      "Fearsome General"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    check(faces, 1, AttackRules(skullsIgnored = true))(4, 0)
    check(faces, 1, AttackRules(skullsIgnored = true, skullSwords = 4))(8, 0)

  test("War Tortoise scores every skull face as nothing, Fearsome General's " +
      "included, and payable skulls still kill"):
    check(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 1,
      AttackRules(skullFacesScoreNothing = true, skullSwords = 4))(1, 1)
    check(Vector(TwoSwordsSkull, OneSword), 2,
      AttackRules(skullFacesScoreNothing = true, skullsIgnored = true))(1, 0)
```

Create `DefenseScoreSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model.DefenseDieFace
import oathdigital.model.DefenseDieFace.{Blank, Doubler, OneShield, TwoShields}

/** The defense scorer: face filters, then doublers on what is left, then the
  * defender's force (catalog batch 4 rulings, "The defense score"). */
class DefenseScoreSuite extends munit.FunSuite:
  test("with no plan chosen it scores the dice and adds the force"):
    for faces <- Vector(Vector(OneShield, TwoShields, Doubler), Vector(Blank),
        Vector.empty[DefenseDieFace]) do
      assertEquals(DefenseScore.of(faces, 2, DefenseRules()),
        DefenseDieFace.score(faces) + 2)

  test("single shields score nothing, as SingleShields scores them"):
    val faces = Vector(OneShield, TwoShields, Doubler)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true)), 4)
    assertEquals(SingleShields.score(faces), 4)

  test("War Tortoise with Rain Boots and a doubler: the filters run before " +
      "the doubler"):
    val faces = Vector(OneShield, TwoShields, TwoShields, Doubler)
    assertEquals(DefenseScore.dice(faces, DefenseRules()), 10)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      doubleShieldsIgnored = true)), 2)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true)), 8)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true, doubleShieldsIgnored = true)), 0)

  test("Garrison Armory adds the force twice, undoubled"):
    assertEquals(DefenseScore.of(Vector(TwoShields, Doubler), 2,
      DefenseRules(forceTwice = true)), 4 + 2 + 2)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.AttackScoreSuite oathdigital.gameplay.powers.campaign.DefenseScoreSuite"`
Expected: compile failure, `Not found: AttackScore` and `Not found: DefenseScore`.

- [ ] **Step 3: Create `AttackScore.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignSetup}
import oathdigital.model._

/** What a hollow sword is worth: half a sword, counted in pairs and rounded
  * down; a whole sword with Fae Battalion; nothing with Rusting Ray. */
enum HollowWorth:
  case Half, Whole, Zero

/** How the plans chosen in a Campaign score its attack (catalog batch 4
  * rulings, "The attack score"). The defaults are the printed rules.
  *
  *  - `hollow`: what a hollow sword is worth.
  *  - `skullSwords`: the swords a two-swords-and-skull face is worth, 4 with
  *    Fearsome General.
  *  - `doubled`: Lancers doubles the count of every face rolled, skulls
  *    included, before the skull cap.
  *  - `skullsIgnored`: no skull kills a warband, and every skull face scores
  *    its swords (Outriders, The Great Levy, Kindred Warriors, Rangers).
  *  - `skullFacesScoreNothing`: War Tortoise used by the defender. Payable
  *    skulls still kill.
  */
final case class AttackRules(hollow: HollowWorth = HollowWorth.Half,
    skullSwords: Int = 2, doubled: Boolean = false,
    skullsIgnored: Boolean = false, skullFacesScoreNothing: Boolean = false)

/** One plan's change to how the attack is scored: the plan's source as its
  * decision names it, the side that must have chosen it, and the change. */
final case class AttackChange(ref: DecisionOptionRef, side: CampaignPlanSide,
    change: AttackRules => AttackRules)

/** The attack scorer (catalog batch 4 design, R1). Every plan that changes
  * the attack writes the score computed from the faces, the committed force
  * and every chosen plan in `changes`, so the plans write the same total
  * whichever order their transforms fold in. With no plan chosen it scores
  * as `CampaignBattle.attackResult` does, which the window's own step
  * writes first.
  */
object AttackScore:
  /** The attack's score and the skulls it loses, in that order. The
    * sacrifice is added later and is never doubled. */
  def of(faces: Vector[AttackDieFace], force: Int, rules: AttackRules)
      : (Int, Int) =
    val times = if rules.doubled then 2 else 1
    def count(face: AttackDieFace): Int = faces.count(_ == face) * times
    val hollow = count(AttackDieFace.HollowSword)
    val skulls = count(AttackDieFace.TwoSwordsSkull)
    val lost = if rules.skullsIgnored then 0 else math.min(skulls, force)
    val scoring = if rules.skullsIgnored then skulls else lost
    val hollowScore = rules.hollow match
      case HollowWorth.Half => hollow / 2
      case HollowWorth.Whole => hollow
      case HollowWorth.Zero => 0
    val skullScore =
      if rules.skullFacesScoreNothing then 0 else scoring * rules.skullSwords
    (count(AttackDieFace.OneSword) + hollowScore + skullScore, lost)

  private def ignored(rules: AttackRules): AttackRules =
    rules.copy(skullsIgnored = true)

  /** Every plan that changes how the attack is scored. */
  val changes: Vector[AttackChange] = Vector(
    AttackChange(DecisionOptionRef.Denizen(OutridersCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Denizen(TheGreatLevyCard.id),
      CampaignPlanSide.Attacker, ignored))

  /** The rules of the plans chosen in `actor`'s Campaign. */
  def rules(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : AttackRules =
    changes.filter(change => PlanUse.chosen(ready, pending, actor, change.ref,
      Set(change.side), afterOutcome = false).nonEmpty)
      .foldLeft(AttackRules())((rules, change) => change.change(rules))

  /** What one plan added to the attack: the score with every chosen plan,
    * less the score once `without` undoes that plan's change. Zero without
    * a Campaign setup. */
  def addedBy(ready: ReadyGame, pending: PendingTree, actor: PlayerId,
      without: AttackRules => AttackRules): Int =
    CampaignSetup.setup(ready, actor, pending).fold(0) { setup =>
      val faces = CampaignBattle.attackFacesOf(ready)
      val chosen = rules(ready, pending, actor)
      of(faces, setup.force, chosen)._1 -
        of(faces, setup.force, without(chosen))._1
    }

  /** Writes the attack as every chosen plan scores it over the recorded one.
    * Nothing is written when the attack never rolled, or when the recorded
    * score and skull loss already stand. */
  def rescore(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    CampaignSetup.setup(ready, actor, pending).toRight(
      OathViolation.InvalidEventOrder(
        "the attack was scored again without a Campaign setup")).map { setup =>
      ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .flatMap { recorded =>
          val (score, lost) = of(CampaignBattle.attackFacesOf(ready),
            setup.force, rules(ready, pending, actor))
          Option.when(score != recorded.score || lost != recorded.skulls)(
            ModifyRollOutcome(CampaignIds.attackPool, Some(lost), Some(score)))
        }
    })

  /** Whether the attack rolled a hollow sword. */
  def hollowRolled(ready: ReadyGame): Boolean =
    CampaignBattle.attackFacesOf(ready).contains(AttackDieFace.HollowSword)

  /** Whether the attack rolled a skull face. */
  def skullRolled(ready: ReadyGame): Boolean =
    AttackDieFace.skulls(CampaignBattle.attackFacesOf(ready)) > 0
```

- [ ] **Step 4: Create `DefenseScore.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignSetup}
import oathdigital.model._

/** How the plans chosen in a Campaign score its defense (catalog batch 4
  * rulings, "The defense score"). The defaults are the printed rules.
  *
  *  - `singleShieldsIgnored`: a single shield scores nothing (Rain Boots, Bag
  *    of Siegeworks).
  *  - `doubleShieldsIgnored`: two shields score nothing (War Tortoise used by
  *    the attacker).
  *  - `forceTwice`: the defender's force is added twice (Garrison Armory).
  */
final case class DefenseRules(singleShieldsIgnored: Boolean = false,
    doubleShieldsIgnored: Boolean = false, forceTwice: Boolean = false)

/** One plan's change to how the defense is scored: the plan's source as its
  * decision names it, the side that must have chosen it, and the change. */
final case class DefenseChange(ref: DecisionOptionRef, side: CampaignPlanSide,
    change: DefenseRules => DefenseRules)

/** The defense scorer (catalog batch 4 design, R2): the faces the plans
  * filter out score nothing, the doublers multiply what is left, and the
  * defender's force is added, undoubled. Every plan that changes the defense
  * writes the total from every chosen plan in `changes`, after the window's
  * own step has written the printed score, so their fold order does not
  * matter.
  */
object DefenseScore:
  /** The dice's score once the chosen plans have filtered the faces. */
  def dice(faces: Vector[DefenseDieFace], rules: DefenseRules): Int =
    DefenseDieFace.score(faces.filterNot {
      case DefenseDieFace.OneShield => rules.singleShieldsIgnored
      case DefenseDieFace.TwoShields => rules.doubleShieldsIgnored
      case _ => false
    })

  /** The defense: the dice's score plus the defender's force. */
  def of(faces: Vector[DefenseDieFace], force: Int, rules: DefenseRules): Int =
    dice(faces, rules) + force * (if rules.forceTwice then 2 else 1)

  private def singleShields(rules: DefenseRules): DefenseRules =
    rules.copy(singleShieldsIgnored = true)

  /** Every plan that changes how the defense is scored. */
  val changes: Vector[DefenseChange] = Vector(
    DefenseChange(DecisionOptionRef.Denizen(RainBootsCard.id),
      CampaignPlanSide.Attacker, singleShields),
    DefenseChange(DecisionOptionRef.Relic(BagOfSiegeworksCard.id),
      CampaignPlanSide.Attacker, singleShields),
    DefenseChange(DecisionOptionRef.Denizen(GarrisonArmoryCard.id),
      CampaignPlanSide.Defender, _.copy(forceTwice = true)))

  /** The rules of the plans chosen in `actor`'s Campaign. */
  def rules(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : DefenseRules =
    changes.filter(change => PlanUse.chosen(ready, pending, actor, change.ref,
      Set(change.side), afterOutcome = false).nonEmpty)
      .foldLeft(DefenseRules())((rules, change) => change.change(rules))

  /** The faces the defense pool rolled. */
  def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })

  /** Writes the defense as every chosen plan scores it over the recorded
    * one. Nothing is written when the recorded score already stands. */
  def rescore(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    CampaignSetup.setup(ready, actor, pending).toRight(
      OathViolation.InvalidEventOrder(
        "the defense was scored again without a Campaign setup")).map { setup =>
      val total = of(faces(ready), CampaignBattle.defenderForce(ready, setup),
        rules(ready, pending, actor))
      val recorded = ready.game.current.rollOutcomes
        .get(CampaignIds.defensePool).fold(0)(_.score)
      Option.when(total != recorded)(ModifyRollOutcome(CampaignIds.defensePool,
        None, Some(total))).toVector
    })
```

- [ ] **Step 5: Run the two suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.AttackScoreSuite oathdigital.gameplay.powers.campaign.DefenseScoreSuite"`
Expected: PASS, 11 tests. (`SingleShields.score` still exists; Step 6 rewires it to `DefenseScore`.)

- [ ] **Step 6: Write Outriders, The Great Levy, Rain Boots, Bag of Siegeworks and Garrison Armory through the scorers**

In `Outriders.scala`:
- Delete the import `oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}`: the faces are now read through `AttackScore`.
- In the class comment, replace the sentence "The rewrite is `Outriders.ignoreSkulls`, which The Great Levy shares." with "The rewrite is `Outriders.ignoreSkulls`, through the attack scorer (`AttackScore`), which The Great Levy, Kindred Warriors and Rangers share."
- Replace `ignoreSkulls` and its comment with:

```scala
  /** The attack scored again by every chosen plan (`AttackScore`), then
    * `power`'s "Skulls ignored." line from `source`, only when the attack
    * rolled a skull to ignore. The faces are kept, so they still show the
    * skulls. */
  private[campaign] def ignoreSkulls(use: PlanUse, power: PowerId,
      source: PowerSourceRef): Vector[Operation] = Vector(
    AttackScore.rescore(use.actor),
    Note(power, states => Option.when(AttackScore.skullRolled(states.now))(
      ignored(source))))
```

- Replace `later` with:

```scala
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))))
```

In `GreatLevy.scala`, replace `Outriders.ignoreSkulls(id, PowerSourceRef.Card(cardId))` with `Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))`.

Replace the whole of `SingleShields.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The single-shield rescoring of the defense, for the plans that ignore the
  * defense dice showing a single shield (Bag of Siegeworks, Rain Boots). Each
  * single-shield die scores 0; two shields and doublers score as usual. The
  * defense is written again through the defense scorer (`DefenseScore`), so
  * two plans that both rescore write the same score.
  */
private[campaign] object SingleShields:
  /** "Single shields ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseScore.dice(faces, DefenseRules(singleShieldsIgnored = true))

  /** What a plan adds after the defense result's own step: the defense
    * scored again, then `power`'s line from `source`, written only when a
    * single shield was rolled. */
  def ignore(use: PlanUse, power: PowerId, source: PowerSourceRef)
      : Vector[Operation] = Vector(
    DefenseScore.rescore(use.actor),
    Note(power, states => Option.when(
      DefenseScore.faces(states.now).contains(DefenseDieFace.OneShield))(
      ignored(source))))
```

In `RainBoots.scala`, delete the `wrapping` override and replace `later` with:

```scala
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use =>
      SingleShields.ignore(use, id, PowerSourceRef.Card(cardId))),
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))
```

In `BagOfSiegeworks.scala`:
- Replace the comment's last sentence ("The rescoring is `SingleShields`, which Rain Boots shares; it is written before the defender's force is added, so the recorded defense carries the change.") with "The rescoring is `SingleShields`, which Rain Boots shares; it writes the whole defense through `DefenseScore` after the window's own step."
- Replace the `wrapping` override with:

```scala
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use =>
      SingleShields.ignore(use, id, PowerSourceRef.Card(relicId))))
```

In `GarrisonArmory.scala`:
- Change the import `oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignSetup}` to `oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignSetup}`.
- Replace the second paragraph of the class comment ("A favor is placed onto the card. ... never applies it.") with:

```scala
  * A favor is placed onto the card. It is offered only in a Conquest. When the
  * defense is scored, it writes the defense through the defense scorer
  * (`DefenseScore`), which adds the warbands at the targets twice, after any
  * single-shield rescoring of the dice and after the doubler, which multiplies
  * the dice only. Bandits pay nothing, so a bandit defender never applies it.
```

- Delete the private `defense` and `again` methods.
- Replace `later` with:

```scala
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use => Vector(
      DefenseScore.rescore(use.actor),
      Branch((ready, pending) => CampaignSetup.setup(ready, use.actor, pending)
        .toVector.flatMap { setup =>
          val force = CampaignBattle.defenderForce(ready, setup)
          Option.when(force > 0)(Note(id, _ => Some(GarrisonArmory.added(
            PowerSourceRef.Card(cardId), NoteArg.Number(force))))).toVector
        }))))
```

- [ ] **Step 7: Run the existing plan suites to verify they pass unchanged**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign* oathdigital.application.gamelog.GameLogCampaignSuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS. `OutridersSuite`, `GreatLevySuite`, `RainBootsSuite`, `BagOfSiegeworksSuite`, `GarrisonArmorySuite`, `HospitalSuite` and `StickyFireSuite` pass without edits. If one fails, fix the production code, not the suite.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/DefenseScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/GreatLevy.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SingleShields.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/RainBoots.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BagOfSiegeworks.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/GarrisonArmory.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/DefenseScoreSuite.scala
git commit -m "refactor(campaign): score the attack and the defense from every chosen plan"
```

---

### Task 2: Rusting Ray and Fae Battalion

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/FireTalkers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/RustingRay.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/FaeBattalion.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`, `HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/RustingRaySuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/FaeBattalionSuite.scala`

**Interfaces:**
- Consumes: `AttackScore.rescore`, `AttackScore.hollowRolled`, `AttackChange`, `HollowWorth` (Task 1).
- Produces: `PlanContext.holds(banner: Banner): Boolean`; `RustingRayCard`, `RustingRay`; `FaeBattalionCard`, `FaeBattalion`.

- [ ] **Step 1: Write the failing suites**

Create `FaeBattalionSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Fae Battalion: a free attacker's plan, offered while its user holds the
  * Darkest Secret, that counts each hollow sword as a sword. */
class FaeBattalionSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Fae Battalion")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val counted = NoteText.Said("counted",
    "Hollow swords counted as swords.", covers = false)

  /** p1 holds Fae Battalion and Longbows faceup; Longbows keeps the
    * attacker's window open. */
  private def attacker: Board = withAdviser(withAdviser(
    board(warbands = 5, supply = 7), card, Orientation.FaceUp), "Longbows",
    Orientation.FaceUp)

  test("with the Darkest Secret each hollow sword counts as a sword, and it " +
      "says so"):
    val base = attacker
    val b = on(base)(_.darkestSecret(Some(base.actor), secrets = 1))
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Three hollow swords score 3 instead of 1.
    assertEquals(done.result.attackScore, 3)
    assertEquals(NoteText.said(FaeBattalion.id, FaeBattalion.noteKeys,
      done.events), Vector(counted))

  test("it is not offered while its user lacks the Darkest Secret"):
    val base = attacker
    val b = on(base)(_.darkestSecret(Some(base.rival), secrets = 1))
    val run = commit(rules(losingDice), b, 3)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(!run.offered(b.actor).contains(ref))
```

Create `RustingRaySuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Rusting Ray: a defender's plan, a secret placed, offered while its user
  * holds the Darkest Secret, that scores the attacker's hollow swords as
  * nothing. */
class RustingRaySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Rusting Ray")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val ignored = NoteText.Said("ignored", "Hollow swords ignored.",
    covers = false)

  /** p2 rules the origin, holds the Oathkeeper title, Rusting Ray faceup and
    * one faceup secret; `darkest` says whether p2 also holds the Darkest
    * Secret. */
  private def defender(darkest: Boolean): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val armed = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    if darkest then on(armed)(_.darkestSecret(Some(base.rival), secrets = 1))
    else armed

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(RustingRay.id, RustingRay.noteKeys, run.events)

  test("the defender places a secret, off turn, and the attacker's hollow " +
      "swords score nothing"):
    val b = defender(darkest = true)
    val facedown = b.player(b.rival).board.faceDownSecrets
    val picked = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    // Paid outside the defender's turn, the secret flips facedown at once.
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.faceDownSecrets,
      facedown + 1)
    val done = picked.finishPlanWindows
    // Four hollow swords would score 2.
    assertEquals(done.result.attackScore, 0)
    assertEquals(lines(done), Vector(ignored))

  test("with no hollow sword rolled it writes nothing"):
    val b = defender(darkest = true)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 4)
    assertEquals(lines(done), Vector.empty)

  test("it is not offered while its user lacks the Darkest Secret"):
    val b = defender(darkest = false)
    // With no attacker plan the attacker's window is skipped; the title's
    // plan keeps the defender's open.
    val run = commit(rules(losingDice), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.FaeBattalionSuite oathdigital.gameplay.powers.campaign.RustingRaySuite"`
Expected: compile failure, `Not found: FaeBattalion` and `Not found: RustingRay`.

- [ ] **Step 3: Add `PlanContext.holds` and switch Fire Talkers to it**

In `PlanContext.scala`, after `enemyHolds`, add:

```scala
  /** Whether the plan's user holds `banner`. A bandit defender holds none. */
  def holds(banner: Banner): Boolean = user.exists(player =>
    BannerRules.holder(ready.game.current, banner).contains(player))
```

In `FireTalkers.scala`, replace `.filter(_ => holdsDarkestSecret(context))` with `.filter(_ => context.holds(Banner.DarkestSecret))`, delete the private `holdsDarkestSecret`, and delete the import `oathdigital.gameplay.actions.BannerRules`.

- [ ] **Step 4: Move the card objects**

Run the move script (see "How to move a card object") twice:
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`, `OBJECT` = `RustingRayCard`
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`, `OBJECT` = `FaeBattalionCard`

Add `FaeBattalionCard` and `RustingRayCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 5: Create `RustingRay.scala` and `FaeBattalion.scala`**

`RustingRay.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object RustingRayCard extends Denizen(DenizenId("57"), "Rusting Ray", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.rusting-ray"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you hold the Darkest Secret, **ignore** all your enemy's " +
      "rolls of hollow swords [hollow-sword].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rusting Ray (card 57), a defender's battle plan: "[secret] If you hold the
  * Darkest Secret, ignore all your enemy's rolls of hollow swords."
  *
  * A secret is placed onto the card. It is offered only while its user holds
  * the Darkest Secret, so a bandit defender never uses it. Once the attack is
  * scored, each hollow sword scores nothing (`AttackScore`). Fae Battalion,
  * the attacker's opposite, needs the Darkest Secret too, so the two never
  * meet.
  *
  * When the attack rolled a hollow sword it writes "Hollow swords ignored."
  */
case object RustingRay extends BattlePlan:
  val cardId: DenizenId = RustingRayCard.id
  val id: PowerId = RustingRayCard.power.id
  /** "Hollow swords ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Hollow swords ignored.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(RustingRay.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => context.holds(Banner.DarkestSecret))
      .map(source => CampaignPlanOffer(source,
        "Rusting Ray: the attack's hollow swords score nothing",
        Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(AttackScore.hollowRolled(states.now))(
        RustingRay.ignored(PowerSourceRef.Card(cardId)))))))
```

`FaeBattalion.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object FaeBattalionCard extends Denizen(DenizenId("215"), "Fae Battalion", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.fae-battalion"),
    persistent = false, cost = Cost.free,
    text = "If you have the Darkest Secret, you may count each " +
      "[hollow-sword] as a [sword] instead.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fae Battalion (card 215), an attacker's battle plan: "If you have the
  * Darkest Secret, you may count each [hollow-sword] as a [sword] instead."
  *
  * It is free, and offered only while its user holds the Darkest Secret.
  * Choosing it is the "may": once chosen it always applies, and each hollow
  * sword scores one sword (`AttackScore`).
  *
  * When the attack rolled a hollow sword it writes "Hollow swords counted as
  * swords."
  */
case object FaeBattalion extends BattlePlan:
  val cardId: DenizenId = FaeBattalionCard.id
  val id: PowerId = FaeBattalionCard.power.id
  /** "Hollow swords counted as swords." */
  val counted: NoteKey = NoteKey("counted",
    Vector(NotePart.Text("Hollow swords counted as swords.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(FaeBattalion.counted)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => context.holds(Banner.DarkestSecret))
      .map(source => CampaignPlanOffer(source,
        "Fae Battalion: count hollow swords as swords", Vector.empty,
        Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(AttackScore.hollowRolled(states.now))(
        FaeBattalion.counted(PowerSourceRef.Card(cardId)))))))
```

- [ ] **Step 6: Add their rows to the attack scorer and register them**

In `AttackScore.scala`, append to `changes`:

```scala
    AttackChange(DecisionOptionRef.Denizen(FaeBattalionCard.id),
      CampaignPlanSide.Attacker, _.copy(hollow = HollowWorth.Whole)),
    AttackChange(DecisionOptionRef.Denizen(RustingRayCard.id),
      CampaignPlanSide.Defender, _.copy(hollow = HollowWorth.Zero))
```

In `PlanRules.scala`, append `Vector(RustingRay) ++ Vector(FaeBattalion)` to the registry (keep the `++` chain), and add "Rusting Ray and Fae Battalion (the attack scored again)" to the class comment's list.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, including `FireTalkersSuite` unchanged and the 5 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/FireTalkers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/RustingRay.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/FaeBattalion.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/RustingRaySuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/FaeBattalionSuite.scala
git commit -m "feat(campaign): Rusting Ray and Fae Battalion rescore hollow swords"
```

---

### Task 3: War Tortoise, Lancers and Fearsome General, and the fold order

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/WarTortoise.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Lancers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/FearsomeGeneral.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/DefenseScore.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`, `HoldingNomadDenizens.scala`, `HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WarTortoiseSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/LancersSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/FearsomeGeneralSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ScoringFoldOrderSuite.scala`

**Interfaces:**
- Consumes: `AttackScore.rescore/addedBy/skullRolled`, `DefenseScore.rescore/faces`, `AttackChange`, `DefenseChange` (Task 1); `PlanDiscard.afterCampaign/discarded`.
- Produces: `WarTortoiseCard`, `WarTortoise`; `LancersCard`, `Lancers` (`Lancers.forCatalog(catalog)`, `Lancers.id`); `FearsomeGeneralCard`, `FearsomeGeneral`.

- [ ] **Step 1: Write the failing suites**

Create `WarTortoiseSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.{skullThenSwords,
  swordsCyclingDefense}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** War Tortoise: a favor placed. Used by the defender, the attacker's skull
  * faces score nothing and payable skulls still kill; used by the attacker,
  * the defender's double shields score nothing. */
class WarTortoiseSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("War Tortoise")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val general = CatalogNames.denizen("Fearsome General")
  private val noAttack = NoteText.Said("attack",
    "Double swords added no attack.", covers = false)
  private val noDefense = NoteText.Said("defense",
    "Double shields added no defense.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(WarTortoise.id, WarTortoise.noteKeys, run.events)

  /** `run` with `actor`'s plan window finished, if it is still open. */
  private def attackerDone(run: Run, actor: PlayerId): Run =
    if awaits(run, actor, CampaignIds.attackerPlan) then
      run.pick(actor, CampaignIds.attackerPlan, CampaignIds.finish)
    else run

  /** p2 rules the origin with two warbands, holds the Oathkeeper title, War
    * Tortoise faceup and one favor. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 1))

  test("a defender pays off turn; the skull face scores nothing and still " +
      "kills, and it says so"):
    val b = defender
    val picked = commit(rules(skullThenSwords), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(picked.state).player(b.rival).board.favor, 0)
    val done = picked.finishPlanWindows
    // A skull face and a sword: the skull face scores nothing.
    assertEquals(done.result.attackScore, 1)
    assertEquals(done.result.skullLosses, 1)
    assertEquals(lines(done), Vector(noAttack))

  test("used by the defender it silences Fearsome General's four swords"):
    val b = withAdviser(defender, general, Orientation.FaceUp)
    val chosen = commit(rules(skullThenSwords), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, DecisionOptionRef.Denizen(general))
    val done = attackerDone(chosen, b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 1)
    assertEquals(NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys,
      done.events), Vector.empty)
    assertEquals(lines(done), Vector(noAttack))

  test("an attacker places a favor and the double shields score nothing"):
    val base = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val b = on(base)(_.favor(base.actor, 1))
    val picked = commit(rules(swordsCyclingDefense(DefenseDieFace.TwoShields,
      DefenseDieFace.OneShield)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    val done = picked.finishPlanWindows
    // Two shields score nothing; one shield and the two bandits remain.
    assertEquals(done.result.defenseScore, 1 + 2)
    assertEquals(lines(done), Vector(noDefense))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winningDice), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
```

Create `LancersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.AttackDieFace.{OneSword, TwoSwordsSkull}
import oathdigital.testkit.{CatalogNames, Look}

/** Lancers: a free attacker's plan that doubles the count of every face
  * rolled, before the skull cap, and is discarded after the Campaign. */
class LancersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Lancers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val general = CatalogNames.denizen("Fearsome General")
  private val doubled = NoteText.Said("doubled",
    "The attack roll was doubled.", covers = false)
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Lancers.id, Vector(Lancers.doubled, PlanDiscard.discarded),
      run.events)

  test("the rulings' example: with Fearsome General, a force of 3 rolling " +
      "two skulls and a sword attacks for 14 and loses 3"):
    val b = withAdviser(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), general, Orientation.FaceUp)
    val roll = dice(attack = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    val done = commit(rules(roll), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(general)).finishPlanWindows
    assertEquals(done.result.attackScore, 14)
    assertEquals(done.result.skullLosses, 3)
    assertEquals(lines(done), Vector(doubled, discarded))
    assertEquals(NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys,
      done.events), Vector(NoteText.Said("added",
      "Skulls added 6 more swords.", covers = false)))
    assert(Look(done.state).inRegionalDiscard(card))

  test("doubled hollow swords make whole swords in pairs"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Three hollow swords doubled are six, worth three swords.
    assertEquals(done.result.attackScore, 3)
    assertEquals(lines(done), Vector(doubled, discarded))
```

Create `FearsomeGeneralSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.AttackDieFace.TwoSwordsSkull
import oathdigital.testkit.CatalogNames

/** Fearsome General: a free attacker's plan; each skull face is worth four
  * swords under every rule the face follows. */
class FearsomeGeneralSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Fearsome General")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows: DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen("Longbows"))
  private val outriders: DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen("Outriders"))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys, run.events)

  private def added(n: Int): NoteText.Said = NoteText.Said("added",
    s"Skulls added $n more swords.", covers = false)

  /** p1 holds Fearsome General and Longbows faceup, and commits one warband
    * and Longbows' die: two dice, both skull faces. */
  private def twoSkullsForOneWarband(extra: Vector[DecisionOptionRef]): Run =
    val b = withAdviser(withAdviser(withAdviser(board(warbands = 5,
      supply = 7), card, Orientation.FaceUp), "Longbows", Orientation.FaceUp),
      "Outriders", Orientation.FaceUp)
    val roll = dice(attack = Vector.fill(2)(TwoSwordsSkull),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    (Vector(ref, longbows) ++ extra).foldLeft(commit(rules(roll), b, 1))(
      (run, plan) => run.pick(b.actor, CampaignIds.attackerPlan, plan))
      .finishPlanWindows

  test("an unpayable skull scores nothing, so only the payable one adds"):
    val done = twoSkullsForOneWarband(Vector.empty)
    assertEquals(done.result.attackScore, 4)
    assertEquals(done.result.skullLosses, 1)
    assertEquals(lines(done), Vector(added(2)))

  test("an ignored skull scores four swords"):
    val done = twoSkullsForOneWarband(Vector(outriders))
    assertEquals(done.result.attackScore, 8)
    assertEquals(done.result.skullLosses, 0)
    assertEquals(lines(done), Vector(added(4)))

  test("with no skull rolled it writes nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 2)
    assertEquals(lines(done), Vector.empty)
```

Create `ScoringFoldOrderSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.model.AttackDieFace.{OneSword, TwoSwordsSkull}
import oathdigital.model.DefenseDieFace.{OneShield, TwoShields}
import oathdigital.testkit.CatalogNames

/** `inner` with its priority replaced, so its transforms fold elsewhere. */
private final class Reprioritised(inner: ContributingPower,
    override val priority: Int) extends ContributingPower:
  def id: PowerId = inner.id
  def source: RuleSourceRef = inner.source
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    inner.contributions
  override def noteKeys: Vector[NoteKey] = inner.noteKeys
  override def resolution: PowerResolution = inner.resolution

/** The scorers make the rescoring plans write the same total whatever order
  * their transforms fold in (catalog batch 4 design, "Verify at plan time",
  * "Fold order"). Transforms fold by priority first, so the same Campaign is
  * run again with the plans' priorities set to fold them in reverse id
  * order. */
class ScoringFoldOrderSuite extends munit.FunSuite:

  /** The production rules, but the powers named by `ids` fold in the reverse
    * of their id order: the first id alphabetically folds last. */
  private def reversedRules(ids: Vector[String], roll: WalkerDice): OathRules =
    val default = WalkerPowerCatalog.default(catalog)
    val rank = ids.sorted.zipWithIndex.toMap
    assertEquals(default.powers.count(power => rank.contains(power.id.value)),
      ids.size)
    val powers = default.powers.map(power => rank.get(power.id.value)
      .fold(power)(at => Reprioritised(power, ids.size - at)))
    new OathRules(catalog, walkerPowerCatalog = default.copy(powers = powers),
      walkerDice = roll)

  private def refOf(name: String): DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen(name))

  test("Lancers, Fearsome General and Outriders score the same attack in " +
      "either fold order"):
    val b = Vector("Lancers", "Fearsome General", "Outriders").foldLeft(
      board(warbands = 5, supply = 7))((held, name) =>
      withAdviser(held, name, Orientation.FaceUp))
    val roll = dice(attack = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    def scored(game: OathRules): (Int, Int) =
      val done = Vector("Lancers", "Fearsome General", "Outriders")
        .foldLeft(commit(game, b, 3))((run, name) =>
          run.pick(b.actor, CampaignIds.attackerPlan, refOf(name)))
        .finishPlanWindows
      (done.result.attackScore, done.result.skullLosses)
    // Doubled and ignored: four skulls of four swords, and two swords.
    assertEquals(scored(rules(roll)), (18, 0))
    assertEquals(scored(reversedRules(Vector("denizen.fearsome-general",
      "denizen.lancers", "denizen.outriders"), roll)), (18, 0))

  test("Rain Boots, War Tortoise, Bag of Siegeworks and Garrison Armory " +
      "score the same defense in either fold order"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val attacker = withSecrets(withRelic(Vector("Rain Boots", "War Tortoise")
      .foldLeft(base)((held, name) =>
        withAdviser(held, name, Orientation.FaceUp)), "Bag of Siegeworks"), 1)
    val b = on(withAdviserFor(attacker, base.rival, "Garrison Armory",
      Orientation.FaceUp))(_.favor(base.actor, 1).favor(base.rival, 1))
    val roll = dice(attack = Vector.fill(5)(OneSword),
      defense = Vector(OneShield, TwoShields))
    val attackerPlans = Vector(refOf("Rain Boots"), refOf("War Tortoise"),
      DecisionOptionRef.Relic(CatalogNames.relic("Bag of Siegeworks")))
    def scored(game: OathRules): Int =
      val chosen = attackerPlans.foldLeft(commit(game, b, 5))((run, plan) =>
        run.pick(b.actor, CampaignIds.attackerPlan, plan))
      val attackDone =
        if awaits(chosen, b.actor, CampaignIds.attackerPlan) then
          chosen.pick(b.actor, CampaignIds.attackerPlan, CampaignIds.finish)
        else chosen
      attackDone.pick(b.rival, CampaignIds.defenderPlan,
        refOf("Garrison Armory")).finishPlanWindows.result.defenseScore
    // Both faces are ignored; the two warbands are added twice.
    assertEquals(scored(rules(roll)), 4)
    assertEquals(scored(reversedRules(Vector("denizen.garrison-armory",
      "denizen.rain-boots", "denizen.war-tortoise", "relic.bag-of-siegeworks"),
      roll)), 4)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WarTortoiseSuite oathdigital.gameplay.powers.campaign.LancersSuite oathdigital.gameplay.powers.campaign.FearsomeGeneralSuite oathdigital.gameplay.powers.campaign.ScoringFoldOrderSuite"`
Expected: compile failure, `Not found: WarTortoise`, `Lancers` and `FearsomeGeneral`.

- [ ] **Step 3: Move the card objects**

Run the move script three times:
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`, `OBJECT` = `WarTortoiseCard`
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`, `OBJECT` = `LancersCard`
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`, `OBJECT` = `FearsomeGeneralCard`

Add `FearsomeGeneralCard`, `LancersCard` and `WarTortoiseCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Create `WarTortoise.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object WarTortoiseCard extends Denizen(DenizenId("187"), "War Tortoise", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.war-tortoise"),
    persistent = false, cost = Cost(favor = 1),
    text = "**Ignore** all attack or defense added by your enemy's " +
      "rolls of [sword] [sword] [skull] or [shield] [shield]. " +
      "_(Any [skull] they roll still kill their warbands.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** War Tortoise (card 187), a battle plan for either side: "[favor] Ignore
  * all attack or defense added by your enemy's rolls of two swords and a
  * skull, or two shields. (Any skull they roll still kills their warbands.)"
  *
  * A favor is placed onto the card. Used by the defender, each of the
  * attacker's skull faces scores nothing, Fearsome General's four swords
  * included, and payable skulls still kill (`AttackScore`). Used by the
  * attacker, each of the defender's double shields scores nothing, before
  * the doublers (`DefenseScore`). Bandits pay nothing, so a bandit defender
  * never applies it.
  *
  * It writes "Double swords added no attack." when a skull face was rolled
  * against it, and "Double shields added no defense." when a double shield
  * was.
  */
case object WarTortoise extends BattlePlan:
  val cardId: DenizenId = WarTortoiseCard.id
  val id: PowerId = WarTortoiseCard.power.id
  /** "Double swords added no attack." */
  val attack: NoteKey = NoteKey("attack",
    Vector(NotePart.Text("Double swords added no attack.")))
  /** "Double shields added no defense." */
  val defense: NoteKey = NoteKey("defense",
    Vector(NotePart.Text("Double shields added no defense.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] =
    Vector(WarTortoise.attack, WarTortoise.defense)

  private def label(side: CampaignPlanSide): String = side match
    case CampaignPlanSide.Attacker =>
      "War Tortoise: double shields add no defense"
    case CampaignPlanSide.Defender =>
      "War Tortoise: double swords add no attack"

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      label(context.side), Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  /** The card its lines name. `BattlePlan.source` is the rule source, so
    * this takes another name. */
  private def lineSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side != CampaignPlanSide.Defender then Vector.empty
      else Vector(AttackScore.rescore(use.actor),
        Note(id, states => Option.when(AttackScore.skullRolled(states.now))(
          WarTortoise.attack(lineSource))))),
    PowerWindow.CampaignDefenseResult -> (use =>
      if use.side != CampaignPlanSide.Attacker then Vector.empty
      else Vector(DefenseScore.rescore(use.actor),
        Note(id, states => Option.when(DefenseScore.faces(states.now)
          .contains(DefenseDieFace.TwoShields))(
          WarTortoise.defense(lineSource))))))
```

- [ ] **Step 5: Create `Lancers.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model._

object LancersCard extends Denizen(DenizenId("154"), "Lancers", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.lancers"),
    persistent = false, cost = Cost.free,
    text = "Double your total attack roll. At end, discard Lancers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Lancers (card 154), an attacker's battle plan: "Double your total attack
  * roll. At end, discard Lancers."
  *
  * It is free. Once the attack is scored, the count of every face rolled is
  * doubled, skulls included, before the skull cap (`AttackScore`); the
  * sacrifice is added later and is never doubled. The card is discarded
  * through the standard discard once the Campaign has resolved, whoever won,
  * as Horse Archers is.
  *
  * When the attack rolled a die it writes "The attack roll was doubled."
  */
final case class Lancers private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = LancersCard.id
  def id: PowerId = Lancers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] =
    Vector(Lancers.doubled, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Lancers: double the attack roll", Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(
        CampaignBattle.attackFacesOf(states.now).nonEmpty)(
        Lancers.doubled(PowerSourceRef.Card(cardId)))))),
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object Lancers:
  val id: PowerId = LancersCard.power.id
  /** "The attack roll was doubled." */
  val doubled: NoteKey = NoteKey("doubled",
    Vector(NotePart.Text("The attack roll was doubled.")))

  def forCatalog(catalog: ExecutableCatalog): Lancers = new Lancers(catalog)
```

- [ ] **Step 6: Create `FearsomeGeneral.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object FearsomeGeneralCard extends Denizen(DenizenId("253"), "Fearsome General", Suit.Order):
  val power = PrintedPower(PowerId("denizen.fearsome-general"),
    persistent = false, cost = Cost.free,
    text = "Each [skull] you roll adds two more [sword] _(4 total)_, " +
      "even if the [skull] is ignored _(Outriders, etc.)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fearsome General (card 253), an attacker's battle plan: "Each skull you
  * roll adds two more swords (4 total), even if the skull is ignored
  * (Outriders, etc.)."
  *
  * It is free. Each skull face is worth four swords instead of two, under
  * every rule the face follows (`AttackScore`): an unpayable skull still
  * scores nothing, an ignored one scores four, and War Tortoise scores it as
  * nothing.
  *
  * When it added swords it writes "Skulls added {n} more swords.", with the
  * swords it added: the score with it, less the score without it.
  */
case object FearsomeGeneral extends BattlePlan:
  val cardId: DenizenId = FearsomeGeneralCard.id
  val id: PowerId = FearsomeGeneralCard.power.id
  /** "Skulls added {n} more swords." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Skulls added "), NotePart.Arg(0),
    NotePart.Text(" more swords.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(FearsomeGeneral.added)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Fearsome General: each skull is worth four swords", Vector.empty,
      Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Branch((ready, pending) => {
        val swords = AttackScore.addedBy(ready, pending, use.actor,
          _.copy(skullSwords = 2))
        Option.when(swords > 0)(Note(id, _ => Some(FearsomeGeneral.added(
          PowerSourceRef.Card(cardId), NoteArg.Number(swords))))).toVector
      }))))
```

- [ ] **Step 7: Add their rows to the scorers and register them**

In `AttackScore.scala`, append to `changes`:

```scala
    AttackChange(DecisionOptionRef.Denizen(LancersCard.id),
      CampaignPlanSide.Attacker, _.copy(doubled = true)),
    AttackChange(DecisionOptionRef.Denizen(FearsomeGeneralCard.id),
      CampaignPlanSide.Attacker, _.copy(skullSwords = 4)),
    AttackChange(DecisionOptionRef.Denizen(WarTortoiseCard.id),
      CampaignPlanSide.Defender, _.copy(skullFacesScoreNothing = true))
```

In `DefenseScore.scala`, append to `changes`:

```scala
    DefenseChange(DecisionOptionRef.Denizen(WarTortoiseCard.id),
      CampaignPlanSide.Attacker, _.copy(doubleShieldsIgnored = true))
```

In `PlanRules.scala`, append `Vector(WarTortoise) ++ Vector(Lancers.forCatalog(catalog)) ++ Vector(FearsomeGeneral)`, and add "War Tortoise (the attack or the defense scored again), Lancers (the attack scored again and a discard at the end) and Fearsome General (the attack scored again)" to the class comment's list.

- [ ] **Step 8: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with the 11 new tests.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/WarTortoise.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Lancers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/FearsomeGeneral.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/DefenseScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/WarTortoiseSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/LancersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/FearsomeGeneralSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/ScoringFoldOrderSuite.scala
git commit -m "feat(campaign): War Tortoise, Lancers and Fearsome General rescore the battle"
```

---

### Task 4: Kindred Warriors and Rangers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/KindredWarriors.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Rangers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`, `HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/KindredWarriorsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/RangersSuite.scala`

**Interfaces:**
- Consumes: `Outriders.ignoreSkulls(use, power, source)`, `Outriders.ignored`, `AttackChange` (Task 1); `PlanContext.cardsRuled(catalog, who, suit)`, `PlanContext.ruler`; `PlanDice.label/effect`.
- Produces: `KindredWarriorsCard`, `KindredWarriors` (`KindredWarriors.forCatalog(catalog)`, `KindredWarriors.id`); `RangersCard`, `Rangers`.

- [ ] **Step 1: Write the failing suites**

Create `KindredWarriorsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.skullThenSwords
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Kindred Warriors: a secret placed. ±X attack dice, X the suits other than
  * Arcane among the cards its user rules; an attacker also ignores every
  * skull, and so is offered it at X = 0. */
class KindredWarriorsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Kindred Warriors")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val ignored = NoteText.Said("ignored", "Skulls ignored.",
    covers = false)

  private def attackDice(ops: Vector[CoreOperation]): Vector[Int] =
    ops.collect { case ModifyDicePool(CampaignIds.attackPool, delta, _) =>
      delta }

  /** p1 holds Kindred Warriors faceup with one faceup secret, and these
    * denizens faceup. */
  private def attacker(others: Vector[DenizenId]): Board =
    val held = others.foldLeft(withAdviser(board(warbands = 5, supply = 7),
      card, Orientation.FaceUp))((b, other) =>
      withAdviser(b, other, Orientation.FaceUp))
    withSecrets(held, 1)

  test("an attacker ruling a Nomad and an Order card adds two dice, places " +
      "a secret and ignores every skull"):
    val b = attacker(planless("Mountain Giant", "Code of Honor"))
    val run = commit(rules(skullThenSwords), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector(2))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(0, 1))
    val done = picked.finishPlanWindows
    // A skull face and three swords, the skull ignored.
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore, 2 + 3)
    assertEquals(NoteText.said(KindredWarriors.id, Vector(Outriders.ignored),
      done.events), Vector(ignored))

  test("an attacker ruling only Arcane cards is offered it for the skulls " +
      "alone"):
    val b = attacker(Vector.empty)
    val run = commit(rules(skullThenSwords), b, 2)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector.empty)
    assertEquals(picked.finishPlanWindows.result.skullLosses, 0)

  test("a defender is offered it only when X is above zero, and removes X " +
      "dice"):
    val ruled = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val base = on(withAdviserFor(ruled, ruled.rival, card,
      Orientation.FaceUp))(_.secrets(ruled.rival, faceUp = 1))
    val bare = commit(rules(winningDice), base, 4)
    assert(awaits(bare, base.rival, CampaignIds.defenderPlan))
    assert(!bare.offered(base.actor).contains(ref))
    val b = withAdviserFor(base, base.rival, planlessCard("Mountain Giant"),
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector(-1))
```

Create `RangersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.skullThenSwords
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Rangers: an attacker's plan, a favor placed, that ignores every skull and
  * adds two attack dice when the defense pool holds four or more dice. */
class RangersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Rangers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def attackDice(ops: Vector[CoreOperation]): Vector[Int] =
    ops.collect { case ModifyDicePool(CampaignIds.attackPool, delta, _) =>
      delta }

  /** p1 holds Rangers faceup and one favor. */
  private def attacker(extras: Int): Board =
    val base = withAdviser(board(extras = extras, warbands = 5, supply = 7),
      card, Orientation.FaceUp)
    on(base)(_.favor(base.actor, 1))

  test("against four or more defense dice it adds two attack dice, places " +
      "a favor and ignores every skull"):
    val b = attacker(extras = 2)
    // The origin prints two defense dice and every site prints at least one,
    // so three targets roll at least four.
    val run = commit(rules(skullThenSwords), b, 2,
      targets = b.extras.map(DecisionOptionRef.Site(_)))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector(2))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    val done = picked.finishPlanWindows
    assertEquals(done.result.skullLosses, 0)
    assertEquals(NoteText.said(Rangers.id, Rangers.noteKeys, done.events),
      Vector(NoteText.Said("ignored", "Skulls ignored.", covers = false)))

  test("against fewer defense dice it is offered for the skulls alone"):
    val b = attacker(extras = 0)
    val run = commit(rules(skullThenSwords), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector.empty)
    assertEquals(picked.finishPlanWindows.result.skullLosses, 0)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.KindredWarriorsSuite oathdigital.gameplay.powers.campaign.RangersSuite"`
Expected: compile failure, `Not found: KindredWarriors` and `Not found: Rangers`.

- [ ] **Step 3: Move the card objects**

Run the move script twice:
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`, `OBJECT` = `KindredWarriorsCard`
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`, `OBJECT` = `RangersCard`

Add `KindredWarriorsCard` and `RangersCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Create `KindredWarriors.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.model._

object KindredWarriorsCard extends Denizen(DenizenId("60"), "Kindred Warriors", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.kindred-warriors"),
    persistent = false, cost = Cost(secret = 1),
    text = "Ignore all skulls [skull] you roll. ±X [attack-die] up to " +
      "the number of other suits you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Kindred Warriors (card 60), a battle plan for either side: "[secret]
  * Ignore all skulls you roll. ±X [attack-die] up to the number of other
  * suits you rule."
  *
  * A secret is placed onto the card. X is the number of suits other than
  * Arcane among the cards its user rules (`RuledCards`), and the plan always
  * adds or removes the full X: no amount is asked. An attacker also ignores
  * every skull, exactly as Outriders does (`Outriders.ignoreSkulls`), so an
  * attacker is offered it at X = 0. A defender rolls no attack dice, so at
  * X = 0 it would do nothing and is not offered. Bandits pay nothing, so a
  * bandit defender never applies it.
  */
final case class KindredWarriors private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = KindredWarriorsCard.id
  def id: PowerId = KindredWarriors.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  /** X: the suits other than Arcane among the cards the user rules. */
  private def suits(context: PlanContext): Int =
    Suit.all.filterNot(_ == Suit.Arcane).count(suit =>
      context.cardsRuled(catalog, context.ruler, suit).nonEmpty)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = suits(context)
    context.denizen(cardId)
      .filter(_ => dice > 0 || context.side == CampaignPlanSide.Attacker)
      .map(source => CampaignPlanOffer(source,
        KindredWarriors.label(context.side, dice),
        Vector(CampaignPlanCost.Secret(1)),
        Option.when(dice > 0)(PlanDice.effect(context.side, dice)).toVector))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side == CampaignPlanSide.Attacker then
        Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))
      else Vector.empty))

object KindredWarriors:
  val id: PowerId = KindredWarriorsCard.power.id

  /** "Kindred Warriors: add 2 attack dice and ignore all attack skulls", or
    * without the dice at X = 0; "Kindred Warriors: remove 2 attack dice"
    * for a defender. */
  private def label(side: CampaignPlanSide, dice: Int): String = side match
    case CampaignPlanSide.Attacker if dice == 0 =>
      "Kindred Warriors: ignore all attack skulls"
    case CampaignPlanSide.Attacker =>
      s"${PlanDice.label("Kindred Warriors", side, dice)} and ignore all " +
        "attack skulls"
    case CampaignPlanSide.Defender =>
      PlanDice.label("Kindred Warriors", side, dice)

  def forCatalog(catalog: ExecutableCatalog): KindredWarriors =
    new KindredWarriors(catalog)
```

A companion's `private` member is visible to its class, so `KindredWarriors.label` compiles.

- [ ] **Step 5: Create `Rangers.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object RangersCard extends Denizen(DenizenId("45"), "Rangers", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.rangers"),
    persistent = false, cost = Cost(favor = 1),
    text = "Ignore all skulls [skull] you roll. +2 [attack-die] if the " +
      "defense pool has 4+ [defense-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rangers (card 45), an attacker's battle plan: "[favor] Ignore all skulls
  * you roll. +2 [attack-die] if the defense pool has 4+ [defense-die]."
  *
  * A favor is placed onto the card. Every skull the attack rolls is ignored,
  * exactly as Outriders does (`Outriders.ignoreSkulls`). The two dice are
  * added when the defense pool holds four or more dice when Rangers is
  * chosen; the defender's window comes later, so dice the defender adds do
  * not count. It is offered when the condition fails too, for the skulls.
  */
case object Rangers extends BattlePlan:
  val cardId: DenizenId = RangersCard.id
  val id: PowerId = RangersCard.power.id
  val Dice: Int = 2
  /** The defense dice that earn the extra attack dice. */
  val Pool: Int = 4

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  private def defensePool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.defensePool).fold(0)(_.count)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = Option.when(defensePool(context.ready) >= Rangers.Pool)(
      Rangers.Dice)
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      dice.fold("Rangers: ignore all attack skulls")(count =>
        s"${PlanDice.label("Rangers", context.side, count)} and ignore all " +
          "attack skulls"),
      Vector(CampaignPlanCost.Favor(1)),
      dice.map(CampaignPlanEffect.AddAttackDice(_)).toVector))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))))
```

- [ ] **Step 6: Add their rows to the attack scorer and register them**

In `AttackScore.scala`, append to `changes`:

```scala
    AttackChange(DecisionOptionRef.Denizen(KindredWarriorsCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Denizen(RangersCard.id),
      CampaignPlanSide.Attacker, ignored)
```

In `PlanRules.scala`, append `Vector(KindredWarriors.forCatalog(catalog)) ++ Vector(Rangers)`, and add "Kindred Warriors and Rangers (the attack scored again)" to the class comment's list.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with the 5 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/KindredWarriors.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Rangers.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/KindredWarriorsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/RangersSuite.scala
git commit -m "feat(campaign): Kindred Warriors and Rangers ignore skulls and add dice"
```

---

### Task 5: Mounted Patrol and Scouts

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/MountedPatrol.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Scouts.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`, `HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/MountedPatrolSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ScoutsSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan.wrapping`, `PlanDiscard.afterCampaign/discarded`, `NoteSupport.supply`.
- Produces: `MountedPatrolCard`, `MountedPatrol` (`MountedPatrol.forCatalog(catalog)`, `MountedPatrol.id`, `MountedPatrol.halved`); `ScoutsCard`, `Scouts`.

- [ ] **Step 1: Write the failing suites**

Create `MountedPatrolSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Mounted Patrol: a free defender's plan that halves the attack pool,
  * rounded down, just before the attack roll, and is discarded after the
  * Campaign. Its halving is the first `wrapping` on the attack roll's leaf
  * (catalog batch 4 design, "Seams used for the first time"). */
class MountedPatrolSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Mounted Patrol")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def halved(text: String): NoteText.Said =
    NoteText.Said("halved", s"The attack pool was halved to $text.",
      covers = false)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    MountedPatrol.id, Vector(MountedPatrol.halved, PlanDiscard.discarded),
    run.events)

  /** p2 rules the origin, holds the Oathkeeper title and Mounted Patrol
    * faceup. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("the defender halves the attack pool before the roll, and the card " +
      "is discarded"):
    val b = defender
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(done.result.attackFaces.size, 2)
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(halved("2 dice"), discarded))

  test("a defender plan chosen after it still counts"):
    val base = defender
    val b = withAdviserFor(base, base.rival, longbows, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(longbows)).finishPlanWindows
    // Four dice, one removed by Longbows, then halved.
    assertEquals(done.result.attackFaces.size, 1)
    assertEquals(lines(done), Vector(halved("1 die"), discarded))

  test("a bandit defender applies it and discards it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.result.attackFaces.size, 2)
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(halved("2 dice"), discarded))

  test("a pool of one is halved to none"):
    val b = defender
    val done = commit(rules(winningDice), b, 1)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.attackScore, 0)
    assertEquals(lines(done), Vector(halved("0 dice"), discarded))
```

If "a pool of one is halved to none" fails because the walker refuses to roll an empty attack pool, stop and report it: the ruling offers Mounted Patrol at a pool of 0 or 1, and an empty roll would be an engine question.

Create `ScoutsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Scouts: a free attacker's plan that gains a Supply when it is applied. */
class ScoutsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Scouts")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  test("the attacker gains a Supply when it is chosen, and says so"):
    val b = withAdviser(board(warbands = 5, supply = 4), card,
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(GainSupply(b.actor, 1)))
    // The Campaign spent one of the four; Scouts gives it back.
    assertEquals(Look(picked.state).player(b.actor).board.supply.supply, 4)
    assertEquals(NoteText.said(Scouts.id, Scouts.noteKeys, picked.events),
      Vector(NoteText.Said("gained", "Gained 1 Supply.", covers = false)))

  test("a defender is not offered it"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MountedPatrolSuite oathdigital.gameplay.powers.campaign.ScoutsSuite"`
Expected: compile failure, `Not found: MountedPatrol` and `Not found: Scouts`.

- [ ] **Step 3: Move the card objects**

Run the move script twice:
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`, `OBJECT` = `MountedPatrolCard`
- `FILE` = `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`, `OBJECT` = `ScoutsCard`

Add `MountedPatrolCard` and `ScoutsCard` to the `powers.campaign` import in `NewFoundations.scala`.

- [ ] **Step 4: Create `MountedPatrol.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object MountedPatrolCard extends Denizen(DenizenId("163"), "Mounted Patrol", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mounted-patrol"),
    persistent = false, cost = Cost.free,
    text = "The attacker rolls only half the [attack-die] in their " +
      "attack pool, rounded down. At end, discard Mounted Patrol.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mounted Patrol (card 163), a defender's battle plan: "The attacker rolls
  * only half the [attack-die] in their attack pool, rounded down. At end,
  * discard Mounted Patrol."
  *
  * It is free. The pool is halved just before the attack roll, from the pool
  * as it stands then, so defender plans chosen after it still count: a
  * `wrapping` at the roll's leaf adds the halving before the roll. It is
  * offered even when the pool holds 0 or 1 dice. A bandit defender applies
  * it at a site it rules. The card is discarded through the standard discard
  * once the Campaign has resolved, whoever won, as Horse Archers is.
  *
  * It writes "The attack pool was halved to {n} dice.", with the dice left.
  */
final case class MountedPatrol private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = MountedPatrolCard.id
  def id: PowerId = MountedPatrol.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] =
    Vector(MountedPatrol.halved, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Mounted Patrol: halve the attack pool", Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackRoll -> ((_, children) => Vector(
      MountedPatrol.halve,
      Note(id, states => Some(MountedPatrol.halved(PowerSourceRef.Card(cardId),
        NoteArg.Number(MountedPatrol.pool(states.now)))))) ++ children))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object MountedPatrol:
  val id: PowerId = MountedPatrolCard.power.id
  /** "The attack pool was halved to {n} dice." */
  val halved: NoteKey = NoteKey("halved", Vector(
    NotePart.Text("The attack pool was halved to "), NotePart.Arg(0),
    NotePart.Plural(0, " die.", " dice.")))

  /** The dice the attack pool holds now. */
  def pool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.attackPool).fold(0)(_.count)

  /** Removes the half of the pool that is not rolled, rounded so that the
    * half rolled is rounded down. Nothing is written when nothing is cut. */
  val halve: Operation = BuildOps((ready, _) => {
    val cut = pool(ready) - pool(ready) / 2
    Right(Option.when(cut > 0)(
      ModifyDicePool(CampaignIds.attackPool, -cut)).toVector)
  })

  def forCatalog(catalog: ExecutableCatalog): MountedPatrol =
    new MountedPatrol(catalog)
```

- [ ] **Step 5: Create `Scouts.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object ScoutsCard extends Denizen(DenizenId("8"), "Scouts", Suit.Order):
  val power = PrintedPower(PowerId("denizen.scouts"),
    persistent = false, cost = Cost.free,
    text = "Gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Scouts (card 8), an attacker's battle plan: "Gain 1 Supply." The printed
  * card's action icon is the red battle plan (catalog batch 4 design, "Goal
  * and scope").
  *
  * It is free. When it is applied the attacker gains 1 Supply, up to the
  * Supply track's maximum (`GainSupply`). It writes "Gained {n} Supply." with
  * the Supply gained, and nothing when none was.
  */
case object Scouts extends BattlePlan:
  val cardId: DenizenId = ScoutsCard.id
  val id: PowerId = ScoutsCard.power.id
  val Supply: Int = 1
  /** "Gained {n} Supply." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Text(".")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Scouts.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Scouts: gain 1 Supply", Vector.empty,
        Vector(CampaignPlanEffect.Run(Vector(GainSupply(user, Scouts.Supply),
          Note(id, states => states.previous
            .map(NoteSupport.supply(_, user)).filter(_ > 0)
            .map(supply => Scouts.gained(PowerSourceRef.Card(cardId),
              NoteArg.Amount(supply, NoteUnit.Supply))))))))))
```

- [ ] **Step 6: Register them**

In `PlanRules.scala`, append `Vector(MountedPatrol.forCatalog(catalog))`, and add "Mounted Patrol (the attack pool halved before the roll and a discard at the end)" to the class comment's list.

In `SimplePlans.scala`, append `Vector(Scouts)`, and add Scouts to the class comment's list.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with the 6 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/MountedPatrol.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Scouts.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/MountedPatrolSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/ScoutsSuite.scala
git commit -m "feat(campaign): Mounted Patrol halves the attack pool and Scouts gains a Supply"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full suites and fix fallout**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, server at the baseline plus 38. If a suite outside `powers/campaign` fails because one of the nine cards now offers a plan (a fixture that holds it as filler), change that fixture to a card `PlanDriver.planless` accepts, and say so in the commit message. Do not change a plan's behavior to suit filler.

- [ ] **Step 2: Update the roadmap**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("docs/ROADMAP.md")
s = p.read_text()
replacements = [
    ("2026-10-01 playtest. No other phase is in progress.",
     "2026-10-01 playtest. **Phase - Catalog batch 4** is in progress: slice\n"
     "1a is done."),
    ("- [ ] 1a. Battle scoring: Rusting Ray, Fae Battalion, War Tortoise, Lancers,\n"
     "  Fearsome General, Kindred Warriors, Rangers, Mounted Patrol, Scouts.\n",
     ""),
]
for old, new in replacements:
    assert s.count(old) == 1, old
    s = s.replace(old, new)
p.write_text(s)
PY
```

If the first assertion fails because another session changed the "Now" paragraph, read it and add the same sentence where it fits.

- [ ] **Step 3: Run the remaining gates**

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 4 slice 1a"
```
