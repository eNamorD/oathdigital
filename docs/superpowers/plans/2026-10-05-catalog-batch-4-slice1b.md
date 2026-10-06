# Catalog Batch 4, Slice 1b (Losses and After the Campaign) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement eight Campaign powers: Shield Wall (108), Billowing Fog (59), Traveling Doctor (51), Cursed Cauldron (R02) and Salt the Earth (89) as battle plans, and Sealing Ward (72), Herald (143) and Diplomat (230) as persistent rules. Two refactors support them: Hospital's loss counter is shared (R3), and so are Sticky Fire's kill branches (R4).

**Architecture:**

- **R4, `KillWholeForce`.** Sticky Fire's kill of a defeated side's whole force moves into a shared helper, and Shield Wall uses it too.
  - The Conquest kill reads the defender's board when it runs.
  - So when both Shield Wall and Sticky Fire kill the same force, the second finds nothing left.
- **R3, `LossRewrite`.** Hospital's private `Tally` becomes a shared rewriter of a window's core operations, plus the loss counter built on it.
  - Hospital counts its user's dead with it, Cursed Cauldron counts its enemy's, and Billowing Fog and Traveling Doctor rewrite the losses through it.
  - Hospital moves from priority 1 to 2.
- **Losses fold order:**
  - Shield Wall and Sticky Fire stay at priority 0.
  - Fog and Doctor take priority 1, and ignore the other two through the collector's named ignore (`ContributingPower.ignores`).
  - Hospital takes 2, and Cursed Cauldron takes 3.
- **Each card is a power.**
  - The battle plans are `BattlePlan`s (`src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`).
  - Sealing Ward, Herald and Diplomat are `ContributingPower`s.
  - Each card's object moves out of `catalog/holding/` into its power's file.
- **Registration.** Everything in `powers/campaign` registers in `PlanRules`. Diplomat registers in `TargetProtections`.
- **No engine change.** No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change. Every change is inside `gameplay/powers/`, plus:
  - the card objects moving between `catalog/holding/` and `gameplay/cards/NewFoundations.scala`'s imports;
  - the test fixture `CampaignFixture.raidBoard`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`: the sections "Powers-side refactors", "Fold order for losses", "Seams used for the first time", "Log lines", "Slice 1: battle plans", "Testing" and "Verify at plan time".
- `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md`: the per-card rulings under "Slice 1b: losses and after the Campaign".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Slice 1a must be on `main` first.** Both slices change `PlanRules.scala`, `NewFoundations.scala` and the holding files.
  - `git log main --oneline | grep "record catalog batch 4 slice 1a"` must print a line.
  - If it prints nothing, stop and report.
- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build.
  - Each file below lists the imports it needs.
  - When a refactor or a card move removes the last use of an import, remove the import.
  - A refutable pattern in a `val` (such as `val Vector(a, b) = ...`) also fails, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Note templates.** A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- **Log lines, exactly** (spec, "Log lines", "Slice 1: battle plans"). `{Blue}` is the user's chip wherever either side may use the power, since the defender acts off turn.

  | Card | Key | Line | When |
  |---|---|---|---|
  | Shield Wall | `killed` | Shield Wall: {Blue}'s whole force was killed. | its kill killed a warband |
  | Billowing Fog, Traveling Doctor | `spared` | {Card}: {Blue}'s force was spared. | its user, a player, was defeated, and the defeat would have killed one of their warbands for good |
  | Traveling Doctor | `discarded` | Traveling Doctor: Discarded after the Campaign. | the discard happened |
  | Cursed Cauldron | `gained` | Cursed Cauldron: {Blue} gained {n warbands}. | at least one warband was gained |
  | Salt the Earth | `buried` | Salt the Earth: Buried {cards} from {site}. | a card was buried; covers the generic "Buried" lines |
  | Sealing Ward | `added` | Sealing Ward: Added {n} more defense dice. ("1 more defense die" for one) | at least one relic of its holder was targeted |
  | Herald | `gained` | Herald: {Blue} gained {1 favor} from {the Order bank}. | favor was gained; covers the Gain line |

  No line for Shield Wall's dice, or for Diplomat, whose hidden options write nothing (roadmap, "Explain restricted options where they are offered").
- **Batch rulings that every task applies** (rulings, "Slice 1b"; batch 3 rulings, "Rules that apply to the whole batch"):
  - **Bandit defenders** apply their free plans without choosing, and never use a plan that costs. `CampaignPlanChoice` already does this.
  - A **persistent rule** acts only from a faceup adviser.
- **Baseline.** Record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end. This plan adds 40 server tests.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **Registration pins.**
   - The five battle plans get no pin, as in slice 1a: every suite below runs through `WalkerPowerCatalog.default`, so an unregistered plan is never offered and its suite fails.
   - The three persistent rules join `PowerKindsCatalogSuite.persistentRules`, which pins `persistent: true` for the persistent rules built so far. Each of them takes its resolution from `CatalogResolution`, as Book Binders does.
2. **Fog and Doctor ignore the kill-all powers; they do not drop their kills.**
   - The spec's "Fold order for losses" says Fog and Doctor "see Shield Wall's and Sticky Fire's kills to drop them". Instead, `SparedForce.ignores` uses the collector's named ignore (`ContributingPower.ignores`, read in `ContributionCollector.gather`): at `CampaignLosses`, a player who chose either plan and was defeated ignores Shield Wall and Sticky Fire.
   - This is the card's own wording ("Ignore powers that kill all of your force"). Sticky Fire therefore neither asks nor kills, and does not give its favor either.
   - Fog's rewrite then needs to find only the losses' own kill and return, so it can rewrite them in place.
   - Task 7 updates the spec's paragraph to match.
   - Cost if wrong: Sticky Fire's winner keeps their favor, where a reading that dropped only the kill would have them give it.
3. **What Fog and Doctor rewrite.** The rewrite reaches the core operations through `LossRewrite` and changes only the user's own losses:
   - **Attacker:** the losses' kill from the attacker's board keeps only the skull losses and the sacrifice.
   - **Conquest defender:** the losses' return from the supply to the board returns every warband that stood at the targets, read when the losses window is folded.
   - **Raid defender:** the losses' kill from the board is dropped.
4. **Fog's and Doctor's line** is written when the defeat would have killed at least one of the user's warbands for good:
   - an attacker's survivors after skulls and sacrifice, halved;
   - a Conquest defender's warbands at the targets, halved;
   - a Raid defender's board, halved.

   This is computed when the losses window is folded.
5. **Shield Wall and Sticky Fire together.**
   - Both kill a defeated Conquest defender's returned warbands. `KillWholeForce.ofDefender` kills the smaller of what was returned and how much the board has grown since the losses window was folded, so the second kill finds nothing.
   - With either power alone, the board has grown by exactly the returned amount, so Sticky Fire's behavior and suite are unchanged.
   - A Raid kill already reads the live board.
6. **Cursed Cauldron's count and gain.**
   - It folds at priority 3, after Fog and Doctor (1) and Hospital (2). It counts its enemy's dead with `LossRewrite.counted` in the plans applied after it was chosen and in the losses, mirroring Hospital, so a sacrifice applied before it was chosen is not counted.
   - It gains at the end of the losses window, not at the root. That is before Hospital places its saved warbands at the root, so it subtracts what Hospital will place (`Hospital.placeable`, read from the same state Hospital's placement will read). Nothing between the losses and the root moves the enemy's warbands out of the supply.
   - Hospital's count runs before the gain, so Hospital never reads the gain as a returned warband.
   - Cost if wrong: Cauldron's line comes among the loss lines, before the placement question, rather than at the very end.
7. **Salt the Earth asks when the Campaign targeted two or more sites, offering every target**, as the rulings say.
   - The targets are durable, so the question never changes after the cards are gone.
   - The decision is not declared narrated: a site with no card writes no line, and the power log lines rules then want the "Chose" line.
   - Relics are buried with no favor returned (`Bury.standard` with no suit, as Relic Breaker does). A relic at a site holds no favor.
8. **Herald, Sealing Ward and Diplomat read a faceup adviser.**
   - Herald and Sealing Ward each add one `Branch` whatever the board holds, so their window's node count never depends on live state (Book Binders' rule). The `Branch` selects nothing when the rule does not apply, so no step is recorded.
   - Herald's decision is declared narrated: once asked, a stocked bank always gives, and its line names the bank.
9. **Herald fires for a Campaign inside another action.** It hooks the Campaign's root window, which a nested Campaign has too. No test pins the nested case: Knights Errant's nested Campaign moves to Phase - Follow-up actions.
10. **Diplomat's start refusal** is FortressRule's shape: it refuses a Campaign whose every legal kind it blocks, until the Campaign has answered one of its own decisions. `FortressRule`'s private `underway` moves to its companion so that Diplomat can read it.
11. **`CampaignFixture.raidBoard`'s relic** changes from Cursed Cauldron to Oracular Pig (R14).
    - Oracular Pig has the same printed defense (2) and only an ACTION power, so every Raid suite's arithmetic is unchanged.
    - The fixture's plan-id guard gains `relic.cursed-cauldron`.
12. **The spec's "Verify at plan time" items for this slice:**
    - **Hospital at priority 2.** Only Hospital and Sticky Fire hook `CampaignLosses` (checked by searching for `CampaignLosses` under `gameplay/powers/`).
      - At `CampaignPlanApplication` the only other hook is `PlanSurcharge` (Gleaming Armor, Insect Swarm), at priority 0.
      - Moving Hospital from 1 to 2 changes no existing order.
    - **Herald.** Its `Branch` is appended to the root's children, after the outcome step, so it runs after `RecordCampaignResult`. `HeraldSuite`'s first test asserts the result is recorded when its question is asked.
    - **Salt the Earth.** `Bury` from a site reaches `CardContainer.Site(site, SiteCardArea.Relics)` in `CardMovementOperations`, for either orientation. `SaltTheEarthSuite`'s first test buries a facedown relic from a site.
    - **Generic lines:**
      - `DetailLines` writes "Buried {card}" for each `Bury`, which Salt the Earth's line covers.
      - The favor `Move` that Herald's take makes writes the Gain line, which `NoteSupport.gainedFromNote` covers (as for Book Binders).

## File Structure

Production, under `src/main/scala/oathdigital/gameplay/powers/` unless stated:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `campaign/KillWholeForce.scala` | Create | R4: the kill of a defeated side's whole force | 1 |
| `campaign/StickyFire.scala` | Modify | Kills through `KillWholeForce` | 1 |
| `campaign/ShieldWall.scala` | Create | +2 defense dice; a defeated defender's whole force dies | 1 |
| `campaign/LossRewrite.scala` | Create | R3: the window rewriter and the shared loss counter | 2 |
| `campaign/Hospital.scala` | Modify | Counts through `LossRewrite`; priority 2; `placeable` | 2, 3 |
| `campaign/SparedForce.scala` | Create | Fog's and Doctor's rule, their ignore and their line | 2 |
| `campaign/BillowingFog.scala`, `campaign/TravelingDoctor.scala` | Create | The two plans | 2 |
| `campaign/CursedCauldron.scala` | Create | Warbands per enemy dead | 3 |
| `campaign/SaltTheEarth.scala` | Create | A targeted site's cards buried | 4 |
| `campaign/SealingWard.scala`, `campaign/Herald.scala` | Create | Persistent Campaign rules | 5 |
| `targeting/Diplomat.scala` | Create | Campaigns against the holder blocked | 6 |
| `targeting/FortressRules.scala` | Modify | `underway` moves to the companion | 6 |
| `campaign/PlanRules.scala` | Modify | Registers seven powers | 1 to 5 |
| `targeting/TargetProtections.scala` | Modify | Registers Diplomat | 6 |
| `src/main/scala/oathdigital/catalog/holding/Holding{Arcane,Hearth,Order,Discord}Denizens.scala`, `HoldingRelics.scala` | Modify | Each card's object leaves | 1 to 6 |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | Modify | Imports each moved card object | 1 to 6 |

Tests, under `src/test/scala/oathdigital/gameplay/`:

| File | Task |
|---|---|
| `powers/campaign/ShieldWallSuite.scala` (6 tests) | 1 |
| `powers/campaign/BillowingFogSuite.scala` (7), `powers/campaign/TravelingDoctorSuite.scala` (3) | 2 |
| `powers/campaign/CursedCauldronSuite.scala` (5); `CampaignFixture.scala` (modify) | 3 |
| `powers/campaign/SaltTheEarthSuite.scala` (4) | 4 |
| `powers/campaign/SealingWardSuite.scala` (3), `powers/campaign/HeraldSuite.scala` (5); `PowerKindsCatalogSuite.scala` (modify) | 5 |
| `powers/targeting/DiplomatSuite.scala` (7); `PowerKindsCatalogSuite.scala` (modify) | 6 |

Docs: `docs/ROADMAP.md` and the design spec's "Fold order for losses" (Task 7).

### How the suites drive a Campaign

Read `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` before writing a suite. In short:

**Boards** (`CampaignFixture`):

- `board(extras, warbands, supply)`:
  - p1 (`b.actor`) stands at Ancient City (the origin, printed defense 2), which two bandits rule.
  - `extras` further sites are bandit-ruled with two bandits each, in map order: Broken Peaks (defense 2) first. `b.extras` lists them.
  - p2 (`b.rival`) and p3 (`b.bystander`) stand at the first site nobody rules.
  - Every site starts with no card.
- `rivalRulesOriginAndHoldsOathkeeper(b)`: p2 rules the origin with two warbands and holds the Oathkeeper title.
  - p2 is then offered the title's defense plan, which keeps p2's plan window open.
  - `finishPlanWindows` and `finishDefenderPlans` close it.
- `withEnemyAtOrigin(b)`: p2's pawn joins p1 at the origin, so a Raid is legal.
- Holdings:
  - `withAdviserFor(b, who, card, orientation)` and `withAdviser(b, card, orientation)` give an adviser.
  - `withRelicFor(b, who, relic)` and `withRelic(b, relic)` give a faceup relic.
  - `withSiteCard(b, site, card)` puts a faceup denizen at a site.
  - `on(b)(_ ...)` applies Table steps: `.favor(p, n)`, `.secrets(p, faceUp = n)`, `.boardWarbands(p, n)`, `.warbandsAt(site, p, n)`, `.denizen(card, at = site)`, `.edifice(card, side, at = site)`, `.relicAt(card, at = site)` (facedown), `.adviser(p, card, facedown = …)`, `.pawn(p, at = site)` and `.banksHoldingOnly(suit -> n, …)`.

**Running a Campaign** (`PlanDriver`):

- `commit(rules(dice), b, force, targets, raid)` starts the Campaign and answers the kind, the optional targets and the force.
- `.pick(who, id, ref)` answers a choice. `.answer(who, id, answer)` answers anything.
- `.finishPlanWindows` finishes every plan window and answers the sacrifice and a one-target placement with 0, until the Campaign ends or asks something else.
- A plan window closes by itself once nothing in it is left to offer.

**Dice:**

- `winningDice`: every attack die is a sword and every defense die is blank, so a force of 4 beats two warbands.
- `losingDice`: every attack die is a hollow sword, so 4 dice make 2, which does not beat two warbands.

**Reading the result:**

- `run.ops` holds every recorded operation, and `picked.since(run)` only what came after `run`.
- `run.winner` is `Some(true)` when the attacker won.
- `awaits(run, who, id)` tells whether the run is parked on `id` for `who`, and `run.offered(actor)` gives the parked choice's available options.
- `NoteText.said(powerId, noteKeys, run.events)` gives a power's lines as `Said(key, text, covers)`. Players and sites render as their ids, a bank as "the Order bank", and `NoteArg.Cards` as ids joined by ", ".

### How to move a card object out of `catalog/holding/`

Each task moves its cards' objects with this script, which deletes one `object ... extends Denizen(...)` or `extends Relic(...)` block from a holding file:

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

Replace `FILE` and `OBJECT` with the values the task gives. The new power file defines the object exactly as the holding file did (each task shows it). In the same step:

- Add the object's name to the matching `import oathdigital.gameplay.powers.<package>.{...}` list in `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`.
  - Keep the list in alphabetical order, and rewrap lines at 80 columns.
  - `NewFoundations` already lists each card by name, and its wildcard `catalog.holding._` import no longer finds it.
- If the holding file is left with an unused import, remove it.

---

### Task 1: Shield Wall on a shared whole-force kill (R4)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/KillWholeForce.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/ShieldWall.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/StickyFire.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ShieldWallSuite.scala`

**Interfaces:**
- Consumes:
  - `PlanUse` (`side`, `user`, `result`, `won`, `ready`, `actor`, `defender`)
  - `PlanContext.denizen(id): Option[CampaignPlanSource]`
  - `NoteSupport.warbands(step, player): Int`
  - `PlayerFacts.forceKind(ready, player): Either[OathViolation, ForceKind]`
- Produces, used by Tasks 2 and 3:
  - `KillWholeForce.Returns(board: Int, returned: Int)`
  - `KillWholeForce.returns(ready: ReadyGame, result: CampaignResult): KillWholeForce.Returns`
  - `KillWholeForce.ofDefender(ready, result, returns): Either[OathViolation, Vector[CoreOperation]]`
  - `KillWholeForce.ofAttacker(ready, result): Either[OathViolation, Vector[CoreOperation]]`
  - `ShieldWallCard`, `ShieldWall` (`ShieldWall.id`, `ShieldWall.Favor = 1`, `ShieldWall.Dice = 2`, `ShieldWall.killed`)

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/ShieldWallSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Shield Wall: a defender's plan that adds two defense dice, and kills its
  * user's whole force when they are defeated. */
class ShieldWallSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Shield Wall")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def warbands(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.warbands

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(ShieldWall.id, ShieldWall.noteKeys, run.events)

  private def killed(who: PlayerId): NoteText.Said = NoteText.Said("killed",
    s"${who.value}'s whole force was killed.", covers = false)

  /** The rival rules the origin with two warbands, has three on its board,
    * holds the Oathkeeper title and Shield Wall faceup, and has the favor to
    * place on it. */
  private def rivalHoldsShieldWall: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3).favor(base.rival, ShieldWall.Favor))

  test("it adds two defense dice"):
    val b = rivalHoldsShieldWall
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.defensePool, ShieldWall.Dice)))

  test("a defeated Conquest defender gets none of its warbands back"):
    val b = rivalHoldsShieldWall
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four swords beat the two warbands and four blank dice. Both warbands
    // at the origin die, and the one the victory returns dies again.
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.rival), 3)
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("a defeated Raid defender loses its whole board"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3).favor(base.rival, ShieldWall.Favor))
    val won = commit(rules(winningDice), b, 4, raid = true)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four swords beat the board's three warbands and four blank dice.
    assert(awaits(won, b.actor, CampaignIds.relocation))
    val done = won.pick(b.actor, CampaignIds.relocation,
      won.offered(b.actor).head)
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.rival), 0)
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("a defender who wins loses nothing more"):
    val b = rivalHoldsShieldWall
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four hollow swords make 2, against the origin's two warbands.
    assertEquals(done.winner, Some(false))
    assertEquals(warbands(done.state, b.rival), 3)
    assertEquals(ready(done.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 2))
    assertEquals(lines(done), Vector.empty)

  test("Hospital saves the warbands Shield Wall kills"):
    val hospital = CatalogNames.denizen("Hospital")
    // Broken Peaks, the first site after the origin, which nobody else rules.
    val site = CatalogNames.site("Broken Peaks")
    val shielded = rivalHoldsShieldWall
    val b = withSiteCard(on(shielded)(_.warbandsAt(site, shielded.rival, 2)),
      site, hospital)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(hospital))
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // Both origin warbands stayed dead, the returned one killed again, and
    // Hospital places both on its site beside the two already there.
    assertEquals(warbands(done.state, b.rival), 3)
    assertEquals(ready(done.state).game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 4))
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("with Sticky Fire also killing the force, the returned warband dies " +
      "once"):
    val fire = CatalogNames.relic("Sticky Fire")
    val b = withRelic(rivalHoldsShieldWall, fire)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, DecisionOptionRef.Relic(fire))
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, StickyFire.decisionId, StickyFire.yes).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // Shield Wall kills the returned warband. Sticky Fire finds nothing more
    // to kill, and the three the rival held before the Campaign stay.
    assertEquals(warbands(done.state, b.rival), 3)
    assertEquals(lines(done), Vector(killed(b.rival)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.ShieldWallSuite"`
Expected: compilation FAIL, `ShieldWall` not found.

- [ ] **Step 3: Create `KillWholeForce.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** The kill of a defeated side's whole force, after the Campaign's losses
  * (R4, from Sticky Fire). Sticky Fire kills its enemy's force this way when
  * its user wins, and Shield Wall kills its own user's when they lose.
  *
  *  - A defeated attacker loses every warband on its board, committed to the
  *    Campaign or not.
  *  - A defeated Conquest defender keeps none of the warbands the victory
  *    returned to its board.
  *  - A defeated Raid defender loses every warband left on its board.
  *
  * Bandits lose nothing more: every targeted bandit already died. Each kill
  * reads the board when it runs, so when Sticky Fire and Shield Wall both
  * kill the same force, the second finds nothing left to kill.
  */
private[campaign] object KillWholeForce:
  /** A player defender's board before the losses run, and what a Conquest
    * the attacker won will return to it. */
  final case class Returns(board: Int, returned: Int)

  /** Read when the losses window is folded, which is before they run. */
  def returns(ready: ReadyGame, result: CampaignResult): Returns =
    (result.kind, result.defender) match
      case (CampaignKind.Conquest, CampaignDefender.Player(defender)) =>
        val total = result.targetSites.flatMap(ready.game.current.map.sites.get)
          .map(_.forces).collect {
            case SiteForces.Occupied(_, count) => count }.sum
        Returns(board(ready, defender), total - total / 2)
      case _ => Returns(0, 0)

  /** The defeated defender's whole force, as the losses left it. A Conquest
    * defender loses what the losses returned and is still on its board: the
    * smaller of the returned amount and how much the board has grown. */
  def ofDefender(ready: ReadyGame, result: CampaignResult, returns: Returns)
      : Either[OathViolation, Vector[CoreOperation]] = result.defender match
    case CampaignDefender.Player(defender) => result.kind match
      case CampaignKind.Conquest => killed(ready, defender,
        math.min(returns.returned, board(ready, defender) - returns.board))
      case CampaignKind.Raid => killed(ready, defender, board(ready, defender))
    case CampaignDefender.Bandits => Right(Vector.empty)

  /** The defeated attacker's whole board. */
  def ofAttacker(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    killed(ready, result.attacker, board(ready, result.attacker))

  private def board(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)

  private def killed(ready: ReadyGame, player: PlayerId, amount: Int)
      : Either[OathViolation, Vector[CoreOperation]] =
    if amount <= 0 then Right(Vector.empty)
    else PlayerFacts.forceKind(ready, player).map(kind => Vector(Kill(
      Piece.Warbands(kind, amount),
      PositionedLocation(Location.PlayArea(player)))))
```

- [ ] **Step 4: Rewrite Sticky Fire's kills through `KillWholeForce`**

In `StickyFire.scala`:

1. Change the import line `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}` to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
2. Delete the three private methods `burn`, `returnedToDefender` and `kills`, with their doc comments, and put this `burn` where the old one was. `burnNote`, `loserOf`, `ask` and `gives` stay as they are.

```scala
  /** The killing and the favor, when the user said yes. The returns are read
    * now, when the losses window is folded, before the losses run. */
  private def burn(use: PlanUse, user: PlayerId, result: CampaignResult)
      : Operation =
    val returns = KillWholeForce.returns(use.ready, result)
    BuildOps((ready, pending) =>
      if !PowerAnswers.one(pending, StickyFire.decisionId).contains(StickyFire.yes) then
        Right(Vector.empty)
      else
        val kills = use.side match
          case CampaignPlanSide.Defender => KillWholeForce.ofAttacker(ready, result)
          case CampaignPlanSide.Attacker =>
            KillWholeForce.ofDefender(ready, result, returns)
        kills.map(_ ++ gives(use.side, user, result)))
```

3. In the class doc comment, after the three bullets, add one sentence: "The kills are `KillWholeForce`'s, which Shield Wall shares."

- [ ] **Step 5: Create `ShieldWall.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` and `OBJECT=ShieldWallCard`. Add `ShieldWallCard` to the `powers.campaign` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object ShieldWallCard extends Denizen(DenizenId("108"), "Shield Wall", Suit.Order):
  val power = PrintedPower(PowerId("denizen.shield-wall"),
    persistent = false, cost = Cost(favor = 1),
    text = "+2 [defense-die] If you're defeated, kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Shield Wall (card 108), a defender's battle plan: "[favor] +2 defense
  * dice. If you're defeated, kill all of your force."
  *
  * A favor is placed onto the card, so a bandit defender never uses it. When
  * its user is defeated, their whole force dies after the losses
  * (`KillWholeForce`): a Conquest defender keeps none of the warbands the victory
  * returned, and a Raid defender loses its whole board. It folds at the
  * default priority, before Billowing Fog and Traveling Doctor, which ignore
  * it, and before Hospital, which still saves what it kills.
  *
  * Its line names its user, the defender, who acts off turn: "{Blue}'s whole
  * force was killed.", when the kill killed a warband. The dice write no line.
  */
case object ShieldWall extends BattlePlan:
  val cardId: DenizenId = ShieldWallCard.id
  val id: PowerId = ShieldWallCard.power.id
  /** The favor placed to choose it. */
  val Favor: Int = 1
  /** The defense dice it adds. */
  val Dice: Int = 2

  /** "{Blue}'s whole force was killed." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Arg(0),
    NotePart.Text("'s whole force was killed.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(ShieldWall.killed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Shield Wall: add 2 defense dice; if defeated, kill all of your force",
      Vector(CampaignPlanCost.Favor(ShieldWall.Favor)),
      Vector(CampaignPlanEffect.AddDefenseDice(ShieldWall.Dice))))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) => (for
      user <- use.user
      result <- use.result
      if use.won.contains(false)
    yield
      val returns = KillWholeForce.returns(use.ready, result)
      losses :+ BuildOps((ready, _) =>
        KillWholeForce.ofDefender(ready, result, returns)) :+
        Note(id, killedNote(user))).getOrElse(losses)))

  /** What the kill took from the user's board, read from its step. */
  private def killedNote(user: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    if NoteSupport.warbands(step, user) < 0
  yield ShieldWall.killed(PowerSourceRef.Card(cardId), NoteArg.Player(user))
```

- [ ] **Step 6: Register Shield Wall**

In `PlanRules.scala`, append `++ Vector(ShieldWall)` to the end of the `forCatalog` vector chain. In the doc comment, add "Shield Wall (the defender's whole force killed)" to the list before "Each names its card".

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.ShieldWallSuite oathdigital.gameplay.powers.campaign.StickyFireSuite oathdigital.gameplay.powers.campaign.HospitalSuite"`
Expected: PASS. `StickyFireSuite` and `HospitalSuite` pass unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/KillWholeForce.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/ShieldWall.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/StickyFire.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/ShieldWallSuite.scala
git commit -m "feat(campaign): Shield Wall kills a defeated defender's whole force, through Sticky Fire's shared kill"
```

---

### Task 2: Billowing Fog and Traveling Doctor on a shared loss rewriter (R3)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BillowingFog.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/TravelingDoctor.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BillowingFogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/TravelingDoctorSuite.scala`

**Interfaces:**
- Consumes:
  - Task 1's `ShieldWallCard` and `ShieldWall`
  - `StickyFireCard`, `StickyFire.decisionId`
  - `PlanUse.chosen(ready, pending, actor, ref, sides, afterOutcome): Option[PlanUse]`
  - `PlanDiscard.afterCampaign(catalog, power, use, card): Vector[Operation]`, `PlanDiscard.discarded`
  - `ContributingPower.ignores(ctx: PowerCtx, other: ContributingPower): Boolean`
- Produces, used by Task 3:
  - `LossRewrite.apply(children: Vector[Operation])(f: CoreOperation => Vector[CoreOperation]): Vector[Operation]`
  - `LossRewrite.counted(pool: PoolKey, kind: ForceKind, owner: Option[PlayerId], returns: Boolean)(children: Vector[Operation]): Vector[Operation]`
  - `Hospital.Priority = 2`
  - `SparedForce.Priority = 1`, `SparedForce.spared`, `SparedForce.ignores(ctx, plan, other)`, `SparedForce.spare(power, card, use, losses)`
  - `BillowingFogCard`, `BillowingFog` (`BillowingFog.Secret = 1`), `TravelingDoctorCard`, `TravelingDoctor` (`TravelingDoctor.id`, `TravelingDoctor.forCatalog`)

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/BillowingFogSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Billowing Fog: when its user is defeated, the defeat kills none of their
  * force, and the powers that kill all of it are ignored. */
class BillowingFogSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Billowing Fog")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def warbands(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.warbands

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(BillowingFog.id, BillowingFog.noteKeys, run.events)

  private def spared(who: PlayerId): NoteText.Said = NoteText.Said("spared",
    s"${who.value}'s force was spared.", covers = false)

  /** `who` holds Billowing Fog faceup and a faceup secret to place on it. */
  private def withFogFor(b: Board, who: PlayerId): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(
      _.secrets(who, faceUp = BillowingFog.Secret))

  test("a defeated attacker loses none of its surviving warbands"):
    val base = board(warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Four hollow swords make 2, against the origin's two bandits. The
    // defeat would have killed half of the four survivors.
    assertEquals(done.winner, Some(false))
    assertEquals(warbands(done.state, b.actor), 5)
    assertEquals(lines(done), Vector(spared(b.actor)))

  test("a defeated attacker still loses its sacrifice"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val extra = b.extras.head
    val done = commit(rules(losingDice), b, 4,
        targets = Vector(DecisionOptionRef.Site(extra)))
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
      .finishPlanWindows
    // 2 from the dice and 1 sacrificed do not beat four bandits and four
    // blank dice. The defeat would kill the sacrifice and half of the three
    // survivors; only the sacrifice dies.
    assertEquals(done.winner, Some(false))
    assert(done.ops.contains(Kill(
      Piece.Warbands(ForceKind.Exile(b.player(b.actor).lineage), 1),
      PositionedLocation(Location.PlayArea(b.actor)))))
    assertEquals(warbands(done.state, b.actor), 4)
    assertEquals(lines(done), Vector(spared(b.actor)))

  test("a defeated Conquest defender gets back every warband at the targets"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withFogFor(base, base.rival)
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Both warbands at the origin die, and both return, not one.
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.rival), before + 2)
    assertEquals(lines(done), Vector(spared(b.rival)))

  test("a defeated Raid defender loses no warband from its board"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withFogFor(base, base.rival))(_.boardWarbands(base.rival, 3))
    val won = commit(rules(winningDice), b, 4, raid = true)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assert(awaits(won, b.actor, CampaignIds.relocation))
    val done = won.pick(b.actor, CampaignIds.relocation,
      won.offered(b.actor).head)
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.rival), 3)
    assertEquals(lines(done), Vector(spared(b.rival)))

  test("a defeated user ignores Sticky Fire: nothing is asked, nothing more " +
      "dies and no favor is given"):
    val fire = CatalogNames.relic("Sticky Fire")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withRelicFor(withFogFor(base, base.actor), base.rival, fire))(
      _.favor(base.rival, 2))
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, DecisionOptionRef.Relic(fire))
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!awaits(done, b.rival, StickyFire.decisionId))
    assertEquals(warbands(done.state, b.actor), 5)
    assertEquals(Look(done.state).player(b.rival).board.favor, 2)
    assertEquals(lines(done), Vector(spared(b.actor)))

  test("a defeated defender ignores its own Shield Wall"):
    val wall = CatalogNames.denizen("Shield Wall")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withFogFor(base, base.rival), base.rival, wall,
      Orientation.FaceUp))(_.favor(base.rival, ShieldWall.Favor))
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, DecisionOptionRef.Denizen(wall))
      .finishPlanWindows
    // Four swords beat the two warbands and four blank dice.
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.rival), before + 2)
    assertEquals(NoteText.said(ShieldWall.id, ShieldWall.noteKeys,
      done.events), Vector.empty)
    assertEquals(lines(done), Vector(spared(b.rival)))

  test("a victorious user changes nothing and writes nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.actor), 5)
    assertEquals(lines(done), Vector.empty)
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/TravelingDoctorSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Traveling Doctor: Billowing Fog's rule, free, and discarded after a
  * Campaign its user lost. A bandit defender applies it and gains nothing
  * from it. */
class TravelingDoctorSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Traveling Doctor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(TravelingDoctor.id,
      Vector(SparedForce.spared, PlanDiscard.discarded), run.events)

  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def inDiscard(state: OathState): Boolean =
    ready(state).game.current.commonCards.regionalDiscards.values
      .exists(_.contains(card))

  test("a defeated attacker loses no survivor, and the Doctor is discarded"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 5)
    assert(inDiscard(done.state))
    assertEquals(lines(done), Vector(NoteText.Said("spared",
      s"${b.actor.value}'s force was spared.", covers = false), discarded))

  test("a victorious user keeps the Doctor"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(Look(done.state).player(b.actor).advisers.exists(_.id == card))
    assertEquals(lines(done), Vector.empty)

  test("a bandit defender applies it, loses every targeted warband, and " +
      "discards it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(done.ops.contains(Kill(Piece.Warbands(ForceKind.Bandit, 2),
      PositionedLocation(Location.Site(b.origin)))))
    assert(inDiscard(done.state))
    assertEquals(lines(done), Vector(discarded))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BillowingFogSuite oathdigital.gameplay.powers.campaign.TravelingDoctorSuite"`
Expected: compilation FAIL, `BillowingFog`, `TravelingDoctor` and `SparedForce` not found.

- [ ] **Step 3: Create `LossRewrite.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The core operations a Campaign window's children produce, rewritten one
  * at a time (R3, from Hospital's private tally). It reaches through
  * `BuildOps`, `Sequence` and `Branch`, the only nodes the plan application
  * and losses windows hold, and leaves every other node as it is.
  *
  * Billowing Fog and Traveling Doctor rewrite the losses with it. `counted`
  * is the shared loss counter: Hospital counts its user's dead with it, and
  * Cursed Cauldron its enemy's.
  */
private[campaign] object LossRewrite:
  def apply(children: Vector[Operation])(
      f: CoreOperation => Vector[CoreOperation]): Vector[Operation] =
    children.map(rewrite(_, f))

  /** `children`, with each kill of `owner`'s warbands of force `kind` counted
    * in `pool`, a pool that is never rolled or shown and that the walker
    * clears with every pool when the Campaign ends. Bandits are no player,
    * so only their kills count. When `returns` is set, a warband returned
    * from the supply to the owner's board did not stay dead, so it comes
    * back out of the count; only the losses return warbands.
    *
    * It counts the amount each operation asks for, which the Campaign's
    * kills always execute in full because they read the board they kill
    * from. */
  def counted(pool: PoolKey, kind: ForceKind, owner: Option[PlayerId],
      returns: Boolean)(children: Vector[Operation]): Vector[Operation] =
    apply(children) {
      case kill @ Kill(Piece.Warbands(`kind`, count), _) =>
        Vector(kill, ModifyDicePool(pool, count))
      case sacrifice @ Sacrifice(player, Piece.Warbands(`kind`, count), _)
          if owner.contains(player) =>
        Vector(sacrifice, ModifyDicePool(pool, count))
      case move @ Move(Piece.Warbands(`kind`, count),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(Location.PlayArea(player), _), None)
          if returns && owner.contains(player) =>
        Vector(move, ModifyDicePool(pool, -count))
      case other => Vector(other)
    }

  private def rewrite(operation: Operation,
      f: CoreOperation => Vector[CoreOperation]): Operation = operation match
    case ops: BuildOps => ops.copy(build = (ready, pending) =>
      ops.build(ready, pending).map(_.flatMap(f)))
    case sequence: Sequence =>
      sequence.copy(children = sequence.children.map(rewrite(_, f)))
    case branch: Branch => Branch((ready, pending) =>
      branch.select(ready, pending).map(rewrite(_, f)))
    case core: CoreOperation => f(core) match
      case Vector(same) => same
      case several => Sequence(several)
    case other => other
```

- [ ] **Step 4: Rewrite Hospital through `LossRewrite`, at priority 2**

In `Hospital.scala`:

1. Replace the `Priority` doc comment and value:

```scala
  /** Folds after Billowing Fog and Traveling Doctor (priority 1), which
    * change the losses, and after every plan at the default priority 0, so
    * its count sees what still dies: the kills Sticky Fire and Shield Wall
    * add there included. */
  val Priority: Int = 2
```

2. In the class doc comment, replace the bullet

```
  *  - every kill in the losses (`CampaignLosses`), including the kills
  *    Sticky Fire adds there, so Hospital folds after every plan at the
  *    default priority. A Conquest defender's returned half comes back out of
  *    the count, because those warbands did not stay dead.
```

with

```
  *  - every kill in the losses (`CampaignLosses`), including the kills
  *    Sticky Fire and Shield Wall add there, so Hospital folds at priority 2,
  *    after them and after Billowing Fog's and Traveling Doctor's changes.
  *    A Conquest defender's returned half comes back out of the count,
  *    because those warbands did not stay dead.
```

3. Replace the `counted` method with:

```scala
  /** A window's children, with each kill of the user's warbands counted.
    * `returns` says whether the window can hand back a Conquest defender's
    * half, which only the losses do. */
  private def counted(returns: Boolean)(use: PlanUse,
      children: Vector[Operation]): Vector[Operation] = (for
    user <- use.user
    kind <- PlayerFacts.forceKind(use.ready, user).toOption
  yield LossRewrite.counted(Hospital.Saved, kind, Some(user), returns)(
    children)).getOrElse(children)
```

4. Delete the whole `Tally` class and its doc comment, which end the file.

- [ ] **Step 5: Create `SparedForce.scala`**

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Billowing Fog's and Traveling Doctor's shared rule: "If you're defeated,
  * kill no warbands in your force. Ignore powers that kill all of your
  * force." It acts only when its user is a player and was defeated.
  *
  *  - A defeated attacker still loses its skulls and its sacrifice, but not
  *    half of what survived.
  *  - A defeated Conquest defender's target warbands all die and all return
  *    to its board, not half of them.
  *  - A defeated Raid defender loses no warband from its board.
  *
  * The two powers that kill all of a force, Shield Wall and Sticky Fire, are
  * ignored at the losses through the collector's named ignore, so neither
  * asks nor kills. Fog and Doctor fold at priority 1: after those two (0),
  * and before Hospital (2) and Cursed Cauldron (3), which count what still
  * dies.
  *
  * The line, "{Blue}'s force was spared.", names the user, since either side
  * may use it. It is written when the defeat would have killed at least one
  * of their warbands for good.
  */
private[campaign] object SparedForce:
  val Priority: Int = 1

  /** "{Blue}'s force was spared." */
  val spared: NoteKey = NoteKey("spared", Vector(NotePart.Arg(0),
    NotePart.Text("'s force was spared.")))

  /** The powers that kill all of a defeated force, by id, so that no plan
    * object depends on another at initialisation. */
  private val killAll: Set[PowerId] =
    Set(ShieldWallCard.power.id, StickyFireCard.power.id)

  /** Whether `plan`, chosen by a player who was defeated, ignores `other`:
    * only at the losses, and only a power that kills all of a force. */
  def ignores(ctx: PowerCtx, plan: BattlePlan, other: ContributingPower)
      : Boolean =
    ctx.window == PowerWindow.CampaignLosses && killAll(other.id) &&
      PlanUse.chosen(ctx.state, PendingTree(ctx.nodePath, ctx.answered),
        ctx.activePlayer, plan.cardRef, plan.sides, afterOutcome = true)
        .flatMap(defeated).nonEmpty

  /** The losses without the defeat's toll on the user's force, then the
    * line. Read when the losses window is folded, before the losses run. */
  def spare(power: PowerId, card: DenizenId, use: PlanUse,
      losses: Vector[Operation]): Vector[Operation] = (for
    user <- defeated(use)
    result <- use.result
    kind <- PlayerFacts.forceKind(use.ready, user).toOption
  yield
    val atTargets = targetForce(use.ready, result, kind)
    val cancelled = use.side match
      case CampaignPlanSide.Attacker =>
        (result.force - result.skullLosses - result.sacrificed) / 2
      case CampaignPlanSide.Defender => result.kind match
        case CampaignKind.Conquest => atTargets / 2
        case CampaignKind.Raid => board(use.ready, user) / 2
    val line = Note(power, _ => Option.when(cancelled > 0)(
      SparedForce.spared(PowerSourceRef.Card(card), NoteArg.Player(user))))
    LossRewrite(losses)(kept(use.side, user, kind, result, atTargets)) :+ line
  ).getOrElse(losses)

  /** The plan's user, when they are a player and were defeated. */
  private def defeated(use: PlanUse): Option[PlayerId] =
    use.user.filter(_ => use.won.contains(false))

  /** Each of the losses' own operations on the user's force, as the rule
    * leaves it. The kill-all powers are ignored, so no other kill or return
    * of the user's warbands is in the losses. */
  private def kept(side: CampaignPlanSide, user: PlayerId, kind: ForceKind,
      result: CampaignResult, atTargets: Int)(operation: CoreOperation)
      : Vector[CoreOperation] = operation match
    case kill @ Kill(Piece.Warbands(`kind`, count),
        PositionedLocation(Location.PlayArea(`user`), _)) => side match
      case CampaignPlanSide.Attacker =>
        val toll = math.min(count, result.skullLosses + result.sacrificed)
        if toll > 0 then Vector(kill.copy(warbands = Piece.Warbands(kind, toll)))
        else Vector.empty
      case CampaignPlanSide.Defender => Vector.empty
    case move @ Move(Piece.Warbands(`kind`, _),
        PositionedLocation(Location.WarbandBank(_), _),
        PositionedLocation(Location.PlayArea(`user`), _), None) =>
      Vector(move.copy(piece = Piece.Warbands(kind, atTargets)))
    case other => Vector(other)

  /** The user's warbands at the Campaign's target sites. A Raid targets no
    * site. */
  private def targetForce(ready: ReadyGame, result: CampaignResult,
      kind: ForceKind): Int =
    result.targetSites.flatMap(ready.game.current.map.sites.get)
      .map(_.forces).collect {
        case SiteForces.Occupied(`kind`, count) => count }.sum

  private def board(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)
```

- [ ] **Step 6: Create the two plans and move their card objects**

Run the move script:

- with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` and `OBJECT=BillowingFogCard`;
- with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` and `OBJECT=TravelingDoctorCard`.

Then add `BillowingFogCard` and `TravelingDoctorCard` to the `powers.campaign` import in `NewFoundations.scala`.

Create `BillowingFog.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model._

object BillowingFogCard extends Denizen(DenizenId("59"), "Billowing Fog", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.billowing-fog"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you're defeated, kill no warbands in your force. " +
      "**Ignore** powers that kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Billowing Fog (card 59), a battle plan for either side: "[secret] If
  * you're defeated, kill no warbands in your force. Ignore powers that kill
  * all of your force."
  *
  * A secret is placed onto the card, so a bandit defender never uses it. The
  * rule, its ignore and its line are `SparedForce`'s, shared with Traveling
  * Doctor.
  */
case object BillowingFog extends BattlePlan:
  val cardId: DenizenId = BillowingFogCard.id
  val id: PowerId = BillowingFogCard.power.id
  /** The secret placed to choose it. */
  val Secret: Int = 1

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = SparedForce.Priority
  override def noteKeys: Vector[NoteKey] = Vector(SparedForce.spared)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Billowing Fog: if defeated, kill no warbands in your force",
      Vector(CampaignPlanCost.Secret(BillowingFog.Secret)), Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) =>
      SparedForce.spare(id, cardId, use, losses)))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    SparedForce.ignores(ctx, this, other)
```

Create `TravelingDoctor.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model._

object TravelingDoctorCard extends Denizen(DenizenId("51"), "Traveling Doctor", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.traveling-doctor"),
    persistent = false, cost = Cost.free,
    text = "If you're defeated, kill no warbands in your force and " +
      "discard Traveling Doctor. **Ignore** powers that kill all " +
      "of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Traveling Doctor (card 51), a battle plan for either side: "If you're
  * defeated, kill no warbands in your force and discard Traveling Doctor.
  * Ignore powers that kill all of your force."
  *
  * Free. The rule, its ignore and its line are `SparedForce`'s, shared with
  * Billowing Fog. When its user was defeated, the card takes the standard
  * discard once the Campaign has resolved (`PlanDiscard.afterCampaign`). A
  * bandit defender applies it too: bandits are no player, so the rule spares
  * nothing and every targeted bandit still dies, and the card is discarded.
  */
final case class TravelingDoctor private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = TravelingDoctorCard.id
  def id: PowerId = TravelingDoctor.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = SparedForce.Priority
  override def noteKeys: Vector[NoteKey] =
    Vector(SparedForce.spared, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Traveling Doctor: if defeated, kill no warbands in your force, then " +
        "discard it", Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) =>
      SparedForce.spare(id, cardId, use, losses)))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if use.won.contains(false) then
        PlanDiscard.afterCampaign(catalog, id, use, cardId)
      else Vector.empty))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    SparedForce.ignores(ctx, this, other)

object TravelingDoctor:
  val id: PowerId = TravelingDoctorCard.power.id

  def forCatalog(catalog: ExecutableCatalog): TravelingDoctor =
    new TravelingDoctor(catalog)
```

- [ ] **Step 7: Register the two plans**

In `PlanRules.scala`, append `++ Vector(BillowingFog) ++ Vector(TravelingDoctor.forCatalog(catalog))` to the `forCatalog` chain. In the doc comment, add "Billowing Fog and Traveling Doctor (the defeat's losses cancelled)" to the list before "Each names its card".

- [ ] **Step 8: Run the loss suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BillowingFogSuite oathdigital.gameplay.powers.campaign.TravelingDoctorSuite oathdigital.gameplay.powers.campaign.HospitalSuite oathdigital.gameplay.powers.campaign.StickyFireSuite oathdigital.gameplay.powers.campaign.ShieldWallSuite oathdigital.gameplay.powers.campaign.WrestlersSuite"`
Expected: PASS. `HospitalSuite`, `StickyFireSuite` and `WrestlersSuite` pass unchanged.

If "a defeated user ignores Sticky Fire" fails because Sticky Fire still asks, `ignores` is not reached. Check that Billowing Fog is discovered at `CampaignLosses`, which its `wrapping` entry declares. Do not change the collector.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BillowingFog.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/TravelingDoctor.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BillowingFogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/TravelingDoctorSuite.scala
git commit -m "feat(campaign): Billowing Fog and Traveling Doctor spare a defeated force, on Hospital's shared loss counter"
```

---

### Task 3: Cursed Cauldron

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/CursedCauldron.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/CursedCauldronSuite.scala`

**Interfaces:**
- Consumes:
  - Task 2's `LossRewrite.counted` and `Hospital.Priority = 2`
  - `NoteSupport.gainedKey(name)`, `NoteSupport.gainNote(key, source, player, unit, read)`, `NoteSupport.warbands`
  - `Gain.Warbands(player, kind, amount)`, which caps at the supply
- Produces:
  - `Hospital.placeable(ready: ReadyGame, user: PlayerId): Int`
  - `CursedCauldronCard`, `CursedCauldron` (`CursedCauldron.Priority = 3`, `CursedCauldron.Died`, `CursedCauldron.gained`)

- [ ] **Step 1: Swap `raidBoard`'s relic**

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, inside `raidBoard` and its doc comment:

- Replace `Cursed Cauldron` with `Oracular Pig` in the doc comment (`holding Cursed Cauldron` → `holding Oracular Pig`).
- Replace the comment line `// Cursed Cauldron prints no battle plan, so the defender is offered none.` with `// Oracular Pig prints no battle plan, so the defender is offered none.`
- Replace `val relic = CatalogNames.relic("Cursed Cauldron")` with `val relic = CatalogNames.relic("Oracular Pig")`.
- Add `"relic.cursed-cauldron"` to the `plans` set, after `"relic.bag-of-siegeworks"`.

Oracular Pig (R14) prints defense 2, as Cursed Cauldron does, and only an ACTION power.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/CursedCauldronSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Cursed Cauldron: a victorious user gains a warband per enemy warband that
  * died in the Campaign, net of those that came back. */
class CursedCauldronSuite extends munit.FunSuite:
  private val relic = CatalogNames.relic("Cursed Cauldron")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  private def warbands(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.warbands

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(CursedCauldron.id, CursedCauldron.noteKeys, run.events)

  private def gained(who: PlayerId, amount: Int): NoteText.Said =
    val noun = if amount == 1 then "warband" else "warbands"
    NoteText.Said("gained", s"${who.value} gained $amount $noun.",
      covers = false)

  test("a victorious attacker gains a warband per bandit killed"):
    val base = board(warbands = 5, supply = 7)
    val b = withRelic(base, relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Both bandits at the origin died, and no survivor was placed.
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.actor), 7)
    assertEquals(lines(done), Vector(gained(b.actor, 2)))

  test("a Conquest defender's returned warband did not die"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelic(base, relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Two died at the origin, and one of them returned to the rival's board.
    assertEquals(done.winner, Some(true))
    assertEquals(warbands(done.state, b.actor), 6)
    assertEquals(lines(done), Vector(gained(b.actor, 1)))

  test("a victorious defender gains a warband per attacker warband killed"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelicFor(base, base.rival, relic)
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // The defeat killed half of the attacker's four survivors.
    assertEquals(done.winner, Some(false))
    assertEquals(warbands(done.state, b.rival), before + 2)
    assertEquals(lines(done), Vector(gained(b.rival, 2)))

  test("a defeated user gains nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = withRelic(base, relic)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(warbands(done.state, b.actor), 3)
    assertEquals(lines(done), Vector.empty)

  test("a warband the enemy's Hospital places on its site did not die"):
    val hospital = CatalogNames.denizen("Hospital")
    // Broken Peaks, the first site after the origin, which nobody else rules.
    val site = CatalogNames.site("Broken Peaks")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelic(withSiteCard(
      on(base)(_.warbandsAt(site, base.rival, 2)), site, hospital), relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(hospital))
      .finishPlanWindows
    // Two died at the origin: one returned to the rival's board, and Hospital
    // places the other on its site.
    assertEquals(done.winner, Some(true))
    assertEquals(ready(done.state).game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 3))
    assertEquals(warbands(done.state, b.actor), 5)
    assertEquals(lines(done), Vector.empty)
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.CursedCauldronSuite"`
Expected: compilation FAIL, `CursedCauldron` not found.

- [ ] **Step 4: Add `Hospital.placeable`**

In `Hospital.scala`, replace the private `placement` method with these two:

```scala
  /** How many saved warbands the placement moves to Hospital's site: none
    * unless `user` rules it now, and no more than their supply holds. Cursed
    * Cauldron reads it to leave out the warbands that did not stay dead. */
  def placeable(ready: ReadyGame, user: PlayerId): Int = (for
    site <- SiteRulers.siteOf(ready, cardId)
    if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(user))
    kind <- PlayerFacts.forceKind(ready, user).toOption
  yield Hospital.saved(ready)
    .min(ready.banks.warbandSupply.getOrElse(kind, 0))).getOrElse(0)

  /** The saved warbands, from the user's supply to Hospital's site, when the
    * user rules it now. */
  private def placement(ready: ReadyGame, user: PlayerId)
      : Option[CoreOperation] = for
    site <- SiteRulers.siteOf(ready, cardId)
    kind <- PlayerFacts.forceKind(ready, user).toOption
    count = placeable(ready, user)
    if count > 0
  yield Move(Piece.Warbands(kind, count),
    PositionedLocation(Location.WarbandBank(kind)),
    PositionedLocation(Location.Site(site)))
```

- [ ] **Step 5: Create `CursedCauldron.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` and `OBJECT=CursedCauldronCard`. Add `CursedCauldronCard` to the `powers.campaign` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

object CursedCauldronCard extends Relic(RelicId("R02"), "Cursed Cauldron", value = 84, defense = 2):
  val power = PrintedPower(PowerId("relic.cursed-cauldron"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain 1 warband per enemy warband " +
      "killed in this campaign.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Cursed Cauldron (relic R02), a battle plan for either side: "If you're
  * victorious, gain 1 warband per enemy warband killed in this campaign."
  *
  * Free; the relic must be faceup in its user's play area, so bandits never
  * use it. Once chosen, it counts the kills of its enemy's warbands with the
  * shared loss counter (`LossRewrite.counted`), in the plans applied after it
  * and in the losses, the kills Sticky Fire and Shield Wall add there
  * included. The count is net:
  *
  *  - a Conquest defender's returned warbands did not die, so they come back
  *    out of it;
  *  - warbands Billowing Fog or Traveling Doctor spare are never killed, since
  *    those fold first;
  *  - warbands the enemy's Hospital will place on its site did not stay dead
  *    (`Hospital.placeable`).
  *
  * When its user won, they gain that many warbands at the end of the losses,
  * up to their supply, and it says so: "{Blue} gained 2 warbands.". The gain
  * comes after Hospital's count, so Hospital never reads it as a return.
  */
case object CursedCauldron extends BattlePlan:
  val relicId: RelicId = CursedCauldronCard.id
  val id: PowerId = CursedCauldronCard.power.id

  /** Folds after Hospital (2), whose saves it subtracts, and after every plan
    * that changes the losses. */
  val Priority: Int = 3

  /** The enemy's dead since it was chosen. It is never rolled or shown, and
    * the walker clears it with every pool when the Campaign ends. */
  val Died: PoolKey = PoolKey("campaign.cursed-cauldron.died")

  /** "{Blue} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = CursedCauldron.Priority
  override def noteKeys: Vector[NoteKey] = Vector(CursedCauldron.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Cursed Cauldron: gain a warband per enemy warband killed if victorious",
      Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignPlanApplication -> ((use, children) =>
      counted(use, children, returns = false)),
    PowerWindow.CampaignLosses -> ((use, losses) => (for
      user <- use.user
      if use.won.contains(true)
    yield counted(use, losses, returns = true) ++ Vector(gain(use, user),
      Note(id, NoteSupport.gainNote(CursedCauldron.gained, cardRef, user,
        NoteUnit.Warband, NoteSupport.warbands)))).getOrElse(losses)))

  /** The other side of the Campaign. */
  private def enemy(use: PlanUse): Option[CampaignDefender] = use.side match
    case CampaignPlanSide.Attacker =>
      use.result.map(_.defender).orElse(use.defender)
    case CampaignPlanSide.Defender => Some(CampaignDefender.Player(use.actor))

  private def playerOf(side: CampaignDefender): Option[PlayerId] = side match
    case CampaignDefender.Player(player) => Some(player)
    case CampaignDefender.Bandits => None

  private def forceOf(ready: ReadyGame, side: CampaignDefender)
      : Option[ForceKind] = side match
    case CampaignDefender.Player(player) =>
      PlayerFacts.forceKind(ready, player).toOption
    case CampaignDefender.Bandits => Some(ForceKind.Bandit)

  private def counted(use: PlanUse, children: Vector[Operation],
      returns: Boolean): Vector[Operation] = (for
    foe <- enemy(use)
    kind <- forceOf(use.ready, foe)
  yield LossRewrite.counted(CursedCauldron.Died, kind, playerOf(foe),
    returns)(children)).getOrElse(children)

  /** The count, less what the enemy's Hospital will place, as warbands for
    * `user`. */
  private def gain(use: PlanUse, user: PlayerId): Operation =
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, user).map { kind =>
      val saved = enemy(use).flatMap(playerOf)
        .fold(0)(Hospital.placeable(ready, _))
      val died = ready.game.current.rollPools.get(CursedCauldron.Died)
        .fold(0)(_.count) - saved
      if died > 0 then Vector[CoreOperation](Gain.Warbands(user, kind, died))
      else Vector.empty
    })
```

- [ ] **Step 6: Register Cursed Cauldron**

In `PlanRules.scala`, append `++ Vector(CursedCauldron)` to the `forCatalog` chain. In the doc comment, add "Cursed Cauldron (warbands for the enemy's dead)" to the list before "Each names its card".

- [ ] **Step 7: Run the Campaign suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign* oathdigital.gameplay.powers.targeting.*"`
Expected: PASS. Every suite that uses `raidBoard` passes unchanged with Oracular Pig.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/CursedCauldron.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/CampaignFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CursedCauldronSuite.scala
git commit -m "feat(campaign): Cursed Cauldron gains a warband per enemy warband that died"
```

---

### Task 4: Salt the Earth

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SaltTheEarth.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/SaltTheEarthSuite.scala`

**Interfaces:**
- Consumes:
  - `Bury.standard(card, from, suit, favor, secrets, actingPlayer): Vector[CoreOperation]`
  - `BuryableCard.{Denizen, Edifice, Relic}`
  - `PowerAnswers.one(pending, id): Option[DecisionOptionRef]`, `PowerAnswers.missing(id): OathViolation`
  - `CampaignProcedure.decisionPrefix`
  - `ExecutableCatalog.suitOf(card): Option[Suit]`
- Produces: `SaltTheEarthCard`, `SaltTheEarth` (`SaltTheEarth.id`, `SaltTheEarth.decisionId`, `SaltTheEarth.buried`, `SaltTheEarth.forCatalog`)

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/SaltTheEarthSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.CatalogNames

/** Salt the Earth: an attacker who wins a Conquest buries every card at one
  * targeted site, relics included. */
class SaltTheEarthSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Salt the Earth")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val code = planlessCard("Code of Honor")
  private val giant = planlessCard("Mountain Giant")
  /** An edifice whose powers are not built, so it changes no Campaign. It is
    * locked, which a burial ignores. */
  private val clock = CatalogNames.edifice("Great Clock")
  private val sticks = CatalogNames.relic("Dowsing Sticks")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(SaltTheEarth.id, Vector(SaltTheEarth.buried), run.events)

  private def cardsAt(state: OathState, site: SiteId): Vector[CardId] =
    val held = ready(state).game.current.map.sites(site)
    held.denizens.map[CardId](_.id) ++ held.relics.map[CardId](_.id)

  /** The actor holds Salt the Earth faceup. The origin holds Code of Honor,
    * the Great Clock and Dowsing Sticks, facedown. */
  private def saltableOrigin: Board =
    val base = board(warbands = 5, supply = 7)
    on(withAdviser(base, card, Orientation.FaceUp))(_
      .denizen(code, at = base.origin)
      .edifice(clock, EdificeSide.Intact, at = base.origin)
      .relicAt(sticks, at = base.origin))

  test("a victorious attacker buries every card at its one target, a " +
      "facedown relic included"):
    val b = saltableOrigin
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(cardsAt(done.state, b.origin), Vector.empty)
    val decks = ready(done.state).game.current.commonCards
    assert(decks.worldDeck.contains(code))
    assert(decks.edificeDeck.contains(clock))
    assert(decks.relicDeck.contains(sticks))
    assertEquals(lines(done), Vector(NoteText.Said("buried",
      s"Buried ${code.value}, ${clock.value}, ${sticks.value} from " +
        s"${b.origin.value}.", covers = true)))

  test("with two targets the attacker chooses the site, and the other " +
      "keeps its cards"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val extra = base.extras.head
    val b = on(withAdviser(base, card, Orientation.FaceUp))(_
      .denizen(code, at = base.origin).denizen(giant, at = extra))
    val asked = commit(rules(winningDice), b, 3,
        targets = Vector(DecisionOptionRef.Site(extra)))
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(3))
    // Three swords and three sacrificed beat four bandits and four blank
    // dice, and no survivor is left to place.
    assertEquals(asked.winner, Some(true))
    assert(awaits(asked, b.actor, SaltTheEarth.decisionId))
    assertEquals(asked.offered(b.actor).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Site(b.origin), DecisionOptionRef.Site(extra)))
    val done = asked.pick(b.actor, SaltTheEarth.decisionId,
      DecisionOptionRef.Site(extra))
    assertEquals(cardsAt(done.state, extra), Vector.empty)
    assertEquals(cardsAt(done.state, b.origin), Vector[CardId](code))
    assertEquals(lines(done), Vector(NoteText.Said("buried",
      s"Buried ${giant.value} from ${extra.value}.", covers = true)))

  test("it is offered in a Conquest, not in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = withAdviser(base, card, Orientation.FaceUp)
    assert(commit(rules(winningDice), b, 2)
      .offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    val raid = commit(rules(winningDice), b, 2, raid = true)
    assert(!awaits(raid, b.actor, CampaignIds.attackerPlan))

  test("a defeated attacker buries nothing"):
    val b = saltableOrigin
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(cardsAt(done.state, b.origin),
      Vector[CardId](code, clock, sticks))
    assertEquals(lines(done), Vector.empty)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SaltTheEarthSuite"`
Expected: compilation FAIL, `SaltTheEarth` not found.

- [ ] **Step 3: Create `SaltTheEarth.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` and `OBJECT=SaltTheEarthCard`. Add `SaltTheEarthCard` to the `powers.campaign` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object SaltTheEarthCard extends Denizen(DenizenId("89"), "Salt the Earth", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.salt-the-earth"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious in a conquest, bury all cards at one " +
      "site you targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Salt the Earth (card 89), an attacker's battle plan: "If you're
  * victorious in a conquest, bury all cards at one site you targeted."
  *
  * Free, and offered only in a Conquest. Once the Campaign has resolved and
  * the attacker won, every card at one targeted site is buried: its denizens
  * and edifice, and its relics, facedown ones included. Each burial makes the
  * returns a discard makes (`Bury.standard`): favor to the card's suit bank,
  * secrets to the attacker. A relic has no suit and returns no favor.
  *
  * The attacker chooses the site only when the Campaign targeted two or more.
  * The question offers every target: the targets are durable, so the
  * question never changes once cards are gone. A site that held no card
  * writes no line, so the decision is not narrated.
  *
  * Its line names the cards and the site, "Buried Tinker, the Great Clock and
  * a relic from Ancient City.", and covers the generic "Buried" lines.
  */
final case class SaltTheEarth private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = SaltTheEarthCard.id
  def id: PowerId = SaltTheEarth.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(SaltTheEarth.buried)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Salt the Earth: bury every card at a targeted site if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use => (for
      result <- use.result
      if use.won.contains(true) && result.kind == CampaignKind.Conquest
    yield salt(use.actor, result.targetSites)).getOrElse(Vector.empty)))

  private def salt(actor: PlayerId, targets: Vector[SiteId])
      : Vector[Operation] =
    Option.when(targets.size >= 2)(Decide(SaltTheEarth.decisionId, actor,
      DecisionQuery.ChooseOne(targets.map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
        heading = Some("Salt the Earth: bury every card at one site")))
    ).toVector ++ Vector[Operation](
      BuildOps((ready, pending) => saltedSite(targets, pending)
        .flatMap(burials(ready, actor, _))),
      Note(id, buriedNote(targets), covers = true))

  /** The site to salt: the only target, or the one chosen. */
  private def saltedSite(targets: Vector[SiteId], pending: PendingTree)
      : Either[OathViolation, SiteId] = targets match
    case Vector(only) => Right(only)
    case _ => PowerAnswers.one(pending, SaltTheEarth.decisionId).collect {
      case DecisionOptionRef.Site(chosen) if targets.contains(chosen) => chosen
    }.toRight(PowerAnswers.missing(SaltTheEarth.decisionId))

  /** Every card at `site`, buried with the standard returns. */
  private def burials(ready: ReadyGame, actor: PlayerId, site: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val from = PositionedLocation(Location.Site(site))
    val cards: Vector[(BuryableCard, Tokens)] =
      ready.game.current.map.sites.get(site).toVector.flatMap(state =>
        state.denizens.map {
          case held: DenizenState => BuryableCard.Denizen(held.id) -> held.tokens
          case held: EdificeState => BuryableCard.Edifice(held.id) -> held.tokens
        } ++ state.relics.map(held =>
          BuryableCard.Relic(held.id) -> held.tokens))
    cards.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty)) { case (done, (card, tokens)) =>
      val suit = catalog.suitOf(card.id)
      for
        earlier <- done
        _ <- Either.cond(tokens.favor == 0 || suit.isDefined, (),
          OathViolation.InvalidEventOrder(
            s"no suit is known for ${card.id.value}"))
      yield earlier ++ Bury.standard(card, from, suit, tokens.favor,
        tokens.secrets, actor)
    }

  /** The cards that left the salted site in the step before. */
  private def buriedNote(targets: Vector[SiteId])(states: NoteStates)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    site <- targets.find(site => SaltTheEarth.cardsAt(before, site) !=
      SaltTheEarth.cardsAt(after, site))
    gone = SaltTheEarth.cardsAt(before, site)
      .filterNot(SaltTheEarth.cardsAt(after, site).contains)
    if gone.nonEmpty
  yield SaltTheEarth.buried(PowerSourceRef.Card(cardId), NoteArg.Cards(gone),
    NoteArg.Site(site))

object SaltTheEarth:
  val id: PowerId = SaltTheEarthCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "salt-the-earth"
  /** "Buried {cards} from {site}." */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))

  /** The cards at `site`: its denizens and edifice, then its relics. */
  private def cardsAt(ready: ReadyGame, site: SiteId): Vector[CardId] =
    ready.game.current.map.sites.get(site).toVector.flatMap(state =>
      state.denizens.map[CardId](_.id) ++ state.relics.map[CardId](_.id))

  def forCatalog(catalog: ExecutableCatalog): SaltTheEarth =
    new SaltTheEarth(catalog)
```

- [ ] **Step 4: Register Salt the Earth**

In `PlanRules.scala`, append `++ Vector(SaltTheEarth.forCatalog(catalog))` to the `forCatalog` chain. In the doc comment, add "Salt the Earth (a targeted site's cards buried)" to the list before "Each names its card".

- [ ] **Step 5: Run the suite to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SaltTheEarthSuite"`
Expected: PASS, 4 tests.

If the first test fails because the facedown relic is not buried, `Bury` refuses a relic from a site. That is an engine change, so stop and report.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/SaltTheEarth.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/SaltTheEarthSuite.scala
git commit -m "feat(campaign): Salt the Earth buries every card at a conquered site"
```

---

### Task 5: Sealing Ward and Herald

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SealingWard.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Herald.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/SealingWardSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HeraldSuite.scala`

**Interfaces:**
- Consumes:
  - `CampaignSetup.setup(ready, actor, pending): Option[CampaignSetup]`
  - `CampaignRaidTarget.Relic(player, relic)`
  - `FavorBankChoice.take(ready, player, amount, decisionId, heading): Vector[Operation]` (`private[powers]`)
  - `NoteSupport.gainedFromKey`, `NoteSupport.gainedFromNote`
  - `CatalogResolution.of(catalog, id)`
- Produces:
  - `SealingWardCard`, `SealingWard` (`SealingWard.id`, `SealingWard.added`, `SealingWard.forCatalog`)
  - `HeraldCard`, `Herald` (`Herald.id`, `Herald.decisionId`, `Herald.gained`, `Herald.forCatalog`)

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/SealingWardSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Sealing Ward: each of its holder's relics a Raid targets adds a defense
  * die, added when the pools are gathered. */
class SealingWardSuite extends munit.FunSuite:
  private val ward = CatalogNames.denizen("Sealing Ward")
  /** A second faceup relic for the rival; its only power is an ACTION. */
  private val horse = CatalogNames.relic("Brass Horse")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(SealingWard.id, Vector(SealingWard.added), run.events)

  private def defenseDice(run: Run): Int = ready(run.state).game.current
    .rollPools.get(CampaignIds.defensePool).fold(0)(_.count)

  /** The Raid board, with the rival holding Sealing Ward on `orientation`
    * and Brass Horse faceup. */
  private def wardedRaidBoard(orientation: Orientation): RaidBoard =
    val raid = raidBoard()
    raid.copy(board = withRelicFor(withAdviserFor(raid.board,
      raid.board.rival, ward, orientation), raid.board.rival, horse))

  test("each targeted relic of its holder adds a defense die"):
    val RaidBoard(b, relic) = wardedRaidBoard(Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Relic(relic), DecisionOptionRef.Relic(horse)),
      raid = true)
    // The pawn and the two relics print 2 each; the Ward adds one per relic.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assertEquals(defenseDice(run), 8)
    assertEquals(lines(run), Vector(NoteText.Said("added",
      "Added 2 more defense dice.", covers = false)))

  test("a Raid that targets none of its holder's relics adds nothing"):
    val RaidBoard(b, _) = wardedRaidBoard(Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4, raid = true)
    assertEquals(defenseDice(run), 2)
    assertEquals(lines(run), Vector.empty)

  test("a facedown Sealing Ward adds nothing"):
    val RaidBoard(b, relic) = wardedRaidBoard(Orientation.FaceDown)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(relic)), raid = true)
    assertEquals(defenseDice(run), 4)
    assertEquals(lines(run), Vector.empty)
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/HeraldSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Herald: after another player campaigns against a player, its holder
  * gains a favor from a bank they choose. */
class HeraldSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Herald")

  private def favorOf(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.favor

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Herald.id, Vector(Herald.gained), run.events)

  private def gained(who: PlayerId, suit: Suit): NoteText.Said =
    NoteText.Said("gained", s"${who.value} gained 1 favor from the $suit bank.",
      covers = true)

  /** p1 can conquer the origin, which the rival rules; the player `holder`
    * picks holds Herald on `orientation`; only `stocks` hold favor. */
  private def conquestWithHerald(holder: Board => PlayerId,
      orientation: Orientation, stocks: (Suit, Int)*): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, holder(base), card, orientation))(
      _.banksHoldingOnly(stocks*))

  test("after another player's Campaign against a player, a third player " +
      "holding it chooses the bank"):
    val b = conquestWithHerald(_.bystander, Orientation.FaceUp,
      Suit.Order -> 2, Suit.Hearth -> 3)
    val asked = commit(rules(winningDice), b, 4).finishPlanWindows
    // It asks once the Campaign's result is recorded.
    assertEquals(asked.winner, Some(true))
    assert(awaits(asked, b.bystander, Herald.decisionId))
    assertEquals(asked.offered(b.actor).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.FavorBank(Suit.Order),
      DecisionOptionRef.FavorBank(Suit.Hearth)))
    val done = asked.pick(b.bystander, Herald.decisionId,
      DecisionOptionRef.FavorBank(Suit.Order))
    assertEquals(favorOf(done.state, b.bystander),
      favorOf(OathState.Ready(b.ready), b.bystander) + 1)
    assertEquals(ready(done.state).banks.favor(Suit.Order), 1)
    assertEquals(lines(done), Vector(gained(b.bystander, Suit.Order)))

  test("the defender holding it gains too, from the one stocked bank, " +
      "without being asked"):
    val b = conquestWithHerald(_.rival, Orientation.FaceUp, Suit.Order -> 2)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(parked.parkedDecision(done.state), None)
    assertEquals(favorOf(done.state, b.rival),
      favorOf(OathState.Ready(b.ready), b.rival) + 1)
    assertEquals(lines(done), Vector(gained(b.rival, Suit.Order)))

  test("a Campaign against bandits gives nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = on(withAdviserFor(base, base.bystander, card, Orientation.FaceUp))(
      _.banksHoldingOnly(Suit.Order -> 2))
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(favorOf(done.state, b.bystander),
      favorOf(OathState.Ready(b.ready), b.bystander))
    assertEquals(lines(done), Vector.empty)

  test("the attacker's own Herald gives nothing"):
    val b = conquestWithHerald(_.actor, Orientation.FaceUp, Suit.Order -> 2)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(favorOf(done.state, b.actor),
      favorOf(OathState.Ready(b.ready), b.actor))
    assertEquals(lines(done), Vector.empty)

  test("a facedown Herald, or empty banks, give nothing"):
    val hidden = conquestWithHerald(_.bystander, Orientation.FaceDown,
      Suit.Order -> 2)
    assertEquals(lines(commit(rules(winningDice), hidden, 4)
      .finishPlanWindows), Vector.empty)
    val empty = conquestWithHerald(_.bystander, Orientation.FaceUp)
    val done = commit(rules(winningDice), empty, 4).finishPlanWindows
    assertEquals(parked.parkedDecision(done.state), None)
    assertEquals(lines(done), Vector.empty)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SealingWardSuite oathdigital.gameplay.powers.campaign.HeraldSuite"`
Expected: compilation FAIL, `SealingWard` and `Herald` not found.

- [ ] **Step 3: Create `SealingWard.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` and `OBJECT=SealingWardCard`. Add `SealingWardCard` to the `powers.campaign` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

object SealingWardCard extends Denizen(DenizenId("72"), "Sealing Ward", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sealing-ward"),
    persistent = true, cost = Cost.free,
    text = "Your relics add one more [defense-die] when targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Sealing Ward (card 72), a persistent rule of a faceup adviser: "Your
  * relics add one more defense die when targeted."
  *
  * A `Transform` at `CampaignGatherPools`, the first power to hook it. When
  * the pools are gathered for a Raid against the holder, each of the
  * holder's relics the Raid targets adds one defense die. A Conquest targets
  * sites, not relics, so it adds nothing.
  *
  * The window's own step gathers only the printed defense, which the Game
  * Log's "Defense Pool" line restates, so the added dice write their own
  * line: "Added 2 more defense dice." It adds one `Branch` whatever the board
  * holds, so the window's node count never depends on live state.
  */
final case class SealingWard private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = SealingWardCard.id
  def id: PowerId = SealingWard.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(SealingWard.added)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignGatherPools -> Vector(Transform((ctx, children) =>
      children :+ Branch(added(ctx.activePlayer)))))

  /** The dice and their line, or nothing when no relic of the holder is
    * targeted. */
  private def added(actor: PlayerId)(ready: ReadyGame, pending: PendingTree)
      : Vector[Operation] = dice(ready, actor, pending) match
    case 0 => Vector.empty
    case count => Vector[Operation](
      ModifyDicePool(CampaignIds.defensePool, count),
      Note(id, _ => Some(SealingWard.added(PowerSourceRef.Card(cardId),
        NoteArg.Number(count)))))

  /** One die per relic of the holder this Raid targets. */
  private def dice(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Int = (for
    setup <- CampaignSetup.setup(ready, actor, pending)
    if setup.kind == CampaignKind.Raid
    holder <- playerOf(setup.defender)
    if holds(ready, holder)
  yield setup.raidTargets.count {
    case CampaignRaidTarget.Relic(`holder`, _) => true
    case _ => false
  }).getOrElse(0)

  private def playerOf(defender: CampaignDefender): Option[PlayerId] =
    defender match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

object SealingWard:
  val id: PowerId = SealingWardCard.power.id
  /** "Added {n} more defense dice." */
  val added: NoteKey = NoteKey("added", Vector(NotePart.Text("Added "),
    NotePart.Arg(0),
    NotePart.Plural(0, " more defense die.", " more defense dice.")))

  def forCatalog(catalog: ExecutableCatalog): SealingWard =
    new SealingWard(catalog)
```

- [ ] **Step 4: Create `Herald.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` and `OBJECT=HeraldCard`. Add `HeraldCard` to the `powers.campaign` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

object HeraldCard extends Denizen(DenizenId("143"), "Herald", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.herald"),
    persistent = true, cost = Cost.free,
    text = "After another player campaigns against a player _(not " +
      "bandits)_, you gain [favor] from any favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Herald (card 143), a persistent rule of a faceup adviser: "After another
  * player campaigns against a player (not bandits), you gain [favor] from
  * any favor bank."
  *
  * A `Transform` at the Campaign's root, `CampaignActionEligibility`, which
  * appends a `Branch` after the outcome, so it reads this Campaign's
  * recorded result. It fires when the attacker is not the holder and the
  * defender is a player: the holder may be the defender or a third player.
  * The root window is the Campaign's own, so it also fires for a Campaign a
  * power runs inside another action.
  *
  * The holder chooses the bank off turn (`FavorBankChoice`). One stocked bank
  * is taken without asking, and none leaves nothing to do. It adds the one
  * `Branch` whatever the board holds, as Book Binders does.
  *
  * Its line, "{Blue} gained 1 favor from the Order bank.", covers the Gain
  * line and names the bank, so its decision is narrated.
  */
final case class Herald private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = HeraldCard.id
  def id: PowerId = Herald.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(Herald.gained)
  override def narratedDecisions: Set[String] = Set(Herald.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ reward(ctx.activePlayer))))

  private def reward(actor: PlayerId): Operation = Branch((ready, _) => (for
    result <- ready.game.current.lastCampaignResult
    if result.attacker == actor && result.defender != CampaignDefender.Bandits
    holder <- holderOf(ready)
    if holder != actor
  yield
    val take = FavorBankChoice.take(ready, holder, Herald.Favor,
      Herald.decisionId, "Herald: take a favor from a bank")
    if take.isEmpty then take
    else take :+ Note(id, NoteSupport.gainedFromNote(Herald.gained,
      PowerSourceRef.Card(cardId), holder), covers = true)
  ).getOrElse(Vector.empty))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object Herald:
  val id: PowerId = HeraldCard.power.id
  /** The favor gained. */
  val Favor: Int = 1
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "herald"
  /** "{Blue} gained {1 favor} from {the Order bank}." */
  val gained: NoteKey = NoteSupport.gainedFromKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Herald = new Herald(catalog)
```

- [ ] **Step 5: Register both and pin their flags**

1. In `PlanRules.scala`, append `++ Vector(SealingWard.forCatalog(catalog)) ++ Vector(Herald.forCatalog(catalog))` to the `forCatalog` chain.
2. In the same doc comment, change its first sentence to say "the battle plans that reach beyond the plan window, the rules that tax them, and the persistent rules of a Campaign". Then add "Sealing Ward (defense dice for targeted relics) and Herald (a favor after another player's Campaign)" to the list before "Each names its card".
3. In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add `"denizen.sealing-ward", "denizen.herald"` to the end of `persistentRules`.

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SealingWardSuite oathdigital.gameplay.powers.campaign.HeraldSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CampaignRaidSuite"`
Expected: PASS.

If the Sealing Ward's dice do not reach the pool, the Transform at `CampaignGatherPools` is not applied. That is the spec's first-use seam: stop and report, and do not change the walker.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/SealingWard.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Herald.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/SealingWardSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/HeraldSuite.scala
git commit -m "feat(campaign): Sealing Ward adds defense dice for targeted relics, and Herald gains a favor after a Campaign"
```

---

### Task 6: Diplomat

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/targeting/Diplomat.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/targeting/DiplomatSuite.scala`

**Interfaces:**
- Consumes:
  - `CampaignSetup.{legalKinds, conquestDefender, raidDefenders}`
  - `RuledCards.of(catalog, ready, ruler, suit): Vector[CardId]`
  - `OptionRestriction(fn)` and `Restriction(fn)`, whose notes default to silent
- Produces:
  - `FortressRule.underway(ready: ReadyGame): Boolean` (`private[targeting]`)
  - `DiplomatCard`, `Diplomat` (`Diplomat.id`, `Diplomat.forCatalog`)

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/targeting/DiplomatSuite.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powerresolver.{PowerCtx, Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.{accepted, as}

/** Diplomat: a player who rules no hearth card cannot Raid or Conquer its
  * holder. The kinds and defenders it blocks are hidden, and a Campaign left
  * with nothing to attack is refused when it starts. */
class DiplomatSuite extends munit.FunSuite:
  import CampaignFixture.{Board, board, on, withEnemyAtOrigin}
  import TargetingFixture.{optionsAt, start}

  private val card = CatalogNames.denizen("Diplomat")
  /** A hearth card with no Campaign power. */
  private val hearth = CatalogNames.denizen("Homesteaders")
  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val conquest = DecisionOptionRef.Button("conquest")
  private val raid = DecisionOptionRef.Button("raid")

  private def startCampaign(b: Board) = start(b.ready, ActionRef.Campaign, b.actor)

  /** The rival rules the origin with two warbands and holds Diplomat faceup;
    * the rival's pawn is elsewhere, so a Conquest is the only kind. */
  private def rivalRulesOriginWithDiplomat: Board =
    val b = board(warbands = 4, supply = 7)
    on(b)(_.warbandsAt(b.origin, b.rival, 2).adviser(b.rival, card))

  /** The rival stands at the origin, which bandits rule, holding Diplomat
    * facedown when `facedown` is set. */
  private def rivalAtOriginWithDiplomat(facedown: Boolean): Board =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    on(b)(_.adviser(b.rival, card, facedown = facedown))

  test("an attacker who rules no hearth card cannot Conquer the holder, so " +
      "the Campaign is refused"):
    val b = rivalRulesOriginWithDiplomat
    assertEquals(CampaignProcedure.startable(catalog, b.ready, b.actor, powers),
      false)
    assert(startCampaign(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))

  test("a faceup hearth adviser lets the attacker Conquer the holder"):
    val base = rivalRulesOriginWithDiplomat
    val b = on(base)(_.adviser(base.actor, hearth))
    assert(startCampaign(b).isRight)

  test("a hearth card at a site the attacker rules counts too"):
    val base = board(extras = 1, warbands = 4, supply = 7)
    val site = base.extras.head
    val b = on(base)(_.warbandsAt(base.origin, base.rival, 2)
      .adviser(base.rival, card).warbandsAt(site, base.actor, 2)
      .denizen(hearth, at = site))
    assert(startCampaign(b).isRight)

  test("a Raid on the holder is hidden while the origin's bandits can still " +
      "be conquered"):
    val b = rivalAtOriginWithDiplomat(facedown = false)
    assertEquals(optionsAt(accepted(startCampaign(b)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))

  test("a second enemy keeps the Raid, and the holder is not offered as its " +
      "defender"):
    val base = rivalAtOriginWithDiplomat(facedown = false)
    val b = on(base)(_.pawn(base.bystander, at = base.origin))
    val started = accepted(startCampaign(b))
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
    val kind = accepted(TargetingFixture.rules.resolveWalker(started.state,
      b.actor, CampaignIds.kind, ChooseOneAnswer(raid)))
    assertEquals(optionsAt(kind, ActionRef.Campaign),
      Vector[DecisionOptionRef](DecisionOptionRef.Player(b.bystander)))

  test("a facedown Diplomat protects nobody"):
    val b = rivalAtOriginWithDiplomat(facedown = true)
    assertEquals(optionsAt(accepted(startCampaign(b)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))

  test("a Campaign already under way is never refused by the start rule"):
    val b = rivalRulesOriginWithDiplomat
    val power = Diplomat.forCatalog(catalog)
    val restriction = as[Restriction](
      power.contributions(PowerWindow.CampaignActionEligibility).head)
    def ctx(state: ReadyGame) = PowerCtx(state, b.actor, power.source,
      PowerWindow.CampaignActionEligibility, Vector.empty,
      Sequence(Vector.empty))
    assert(restriction.fn(ctx(b.ready), Sequence(Vector.empty)).nonEmpty)
    // The Campaign has answered its force decision: it is under way.
    val underway = b.ready.updateCurrent(_.copy(walkerPending =
      Some(PendingTree(Vector("2"), Vector(Answered(CampaignIds.force,
        DecisionAnswer.ChooseAmountAnswer(0), b.actor))))))
    assertEquals(restriction.fn(ctx(underway), Sequence(Vector.empty)), None)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.DiplomatSuite"`
Expected: compilation FAIL, `Diplomat` not found.

- [ ] **Step 3: Move `underway` to `FortressRule`'s companion**

In `FortressRules.scala`:

1. Delete the class's private method and its doc comment:

```scala
  /** The Campaign has answered one of its own decisions. */
  private def underway(ready: ReadyGame): Boolean =
    ready.game.current.walkerPending.exists(_.answered.exists(answered =>
      CampaignIds.all.contains(answered.decisionId)))

```

2. In `startGuard`, change `Option.when(!underway(ctx.state) &&` to `Option.when(!FortressRule.underway(ctx.state) &&`.

3. In `object FortressRule`, after `allShielded`, add:

```scala

  /** The Campaign has answered one of its own decisions. Once under way it
    * must be able to finish, so a start rule no longer refuses it. Diplomat
    * reads it too. */
  private[targeting] def underway(ready: ReadyGame): Boolean =
    ready.game.current.walkerPending.exists(_.answered.exists(answered =>
      CampaignIds.all.contains(answered.decisionId)))
```

- [ ] **Step 4: Create `Diplomat.scala` and move the card object**

Run the move script with `FILE=src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` and `OBJECT=DiplomatCard`. Add `DiplomatCard` to the `powers.targeting` import in `NewFoundations.scala`.

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{CatalogResolution, RuledCards}
import oathdigital.model._

object DiplomatCard extends Denizen(DenizenId("230"), "Diplomat", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.diplomat"),
    persistent = true, cost = Cost.free,
    text = "Players **cannot** campaign against you unless they rule a " +
      "[suit-hearth] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Diplomat (card 230), a persistent rule of a faceup adviser: "Players
  * cannot campaign against you unless they rule a hearth card."
  *
  * An attacker who rules no hearth card (`RuledCards`: their faceup advisers
  * and the faceup cards at the sites they rule) cannot Raid or Conquer the
  * holder. As for the Fortress, three hooks apply it:
  *
  *  - the kind decision loses a kind whose every defender is the holder;
  *  - a Raid's defender decision loses the holder;
  *  - a Campaign whose every legal kind it blocks is refused when it starts,
  *    until the Campaign has answered one of its own decisions
  *    (`FortressRule.underway`).
  *
  * Targets need no filter: every target belongs to the defender. Its hidden
  * options write no line (roadmap, "Explain restricted options where they
  * are offered").
  */
final case class Diplomat private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = DiplomatCard.id
  def id: PowerId = Diplomat.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignKindSelection -> Vector(OptionRestriction(kindGuard)),
    PowerWindow.CampaignDefenderSelection ->
      Vector(OptionRestriction(defenderGuard)),
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => startGuard(ctx))))

  /** The holder `attacker` may not campaign against, if any. */
  private def protects(ready: ReadyGame, attacker: PlayerId): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player).filter(holder => holder != attacker &&
      RuledCards.of(catalog, ready, SiteRuler.Player(attacker), Suit.Hearth)
        .isEmpty)

  /** Whether every defender `kind` could have is a holder `attacker` may not
    * campaign against. */
  private def blocked(ready: ReadyGame, attacker: PlayerId,
      kind: CampaignKind): Boolean = protects(ready, attacker) match
    case None => false
    case Some(holder) => kind match
      case CampaignKind.Conquest => CampaignSetup.conquestDefender(ready,
        attacker).contains(CampaignDefender.Player(holder))
      case CampaignKind.Raid =>
        val defenders = CampaignSetup.raidDefenders(ready, attacker)
        defenders.nonEmpty && defenders.forall(_ == holder)

  private def refused(detail: String): OathViolation =
    OathViolation.CampaignUnavailable(detail)

  private def kindGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Button("conquest")
        if blocked(ctx.state, ctx.activePlayer, CampaignKind.Conquest) =>
      Some(refused("Diplomat protects the defender of this Conquest"))
    case DecisionOptionRef.Button("raid")
        if blocked(ctx.state, ctx.activePlayer, CampaignKind.Raid) =>
      Some(refused("Diplomat protects every player a Raid could target"))
    case _ => None

  private def defenderGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Player(defender)
        if protects(ctx.state, ctx.activePlayer).contains(defender) =>
      Some(refused(s"Diplomat protects ${defender.value} from a Raid"))
    case _ => None

  private def startGuard(ctx: PowerCtx): Option[OathViolation] =
    val kinds = CampaignSetup.legalKinds(ctx.state, ctx.activePlayer)
    Option.when(!FortressRule.underway(ctx.state) && kinds.nonEmpty &&
      kinds.forall(blocked(ctx.state, ctx.activePlayer, _)))(
      refused("Diplomat protects every defender this Campaign could have"))

object Diplomat:
  val id: PowerId = DiplomatCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Diplomat = new Diplomat(catalog)
```

- [ ] **Step 5: Register Diplomat and pin its flag**

1. In `TargetProtections.scala`, append `++ Vector(Diplomat.forCatalog(catalog))` to the chain.
2. In its doc comment, change "from being targeted by a Raid, a Challenge or a Conspiracy, or taken" to "from being targeted by a Raid, a Challenge or a Conspiracy, campaigned against, or taken".
3. In `PowerKindsCatalogSuite.scala`, add `"denizen.diplomat"` to the end of `persistentRules`.

- [ ] **Step 6: Run the targeting suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. `FortressRulesSuite` passes unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/Diplomat.scala \
  src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala \
  src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/targeting/DiplomatSuite.scala
git commit -m "feat(targeting): Diplomat blocks Campaigns against its holder by players who rule no hearth card"
```

---

### Task 7: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`

- [ ] **Step 1: Run the full suites and fix fallout**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, with the server at the baseline plus 40.

If a suite outside the new ones fails because one of the eight cards now acts (a fixture that holds it as filler):

- Change that fixture to a card `PlanDriver.planless` accepts, and say so in the commit message.
- Do not change a power's behavior to suit filler.

Sealing Ward is filler for Locked in several suites. Its rule acts only in a Raid against its holder, so those suites should pass unchanged.

- [ ] **Step 2: Update the roadmap and the spec's fold order**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("docs/ROADMAP.md")
s = p.read_text()
replacements = [
    ("slice\n1a is done.", "slices\n1a and 1b are done."),
    ("- [ ] 1b. Losses and after the Campaign: Billowing Fog, Traveling Doctor,\n"
     "  Shield Wall, Cursed Cauldron, Salt the Earth, Sealing Ward, Herald,\n"
     "  Diplomat.\n",
     ""),
]
for old, new in replacements:
    assert s.count(old) == 1, old
    s = s.replace(old, new)
p.write_text(s)

p = Path("docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md")
s = p.read_text()
old = ("At `CampaignLosses`, Shield Wall stays at priority 0, Billowing Fog and\n"
       "Traveling Doctor take priority 1, and Hospital moves to 2. Fog and Doctor then\n"
       "see Shield Wall's and Sticky Fire's kills to drop them, and Hospital saves\n"
       "from whatever kill is left. Cursed Cauldron counts at priority 1 or above, so\n"
       "it sees Sticky Fire's kills.\n")
new = ("At `CampaignLosses`, Shield Wall and Sticky Fire stay at priority 0, Billowing\n"
       "Fog and Traveling Doctor take priority 1, Hospital moves to 2 and Cursed\n"
       "Cauldron takes 3. A defeated user of Fog or Doctor ignores Shield Wall and\n"
       "Sticky Fire there, through the collector's named ignore, so neither asks nor\n"
       "kills. Hospital saves from whatever kill is left, and Cursed Cauldron counts\n"
       "after both, less what Hospital will place.\n")
assert s.count(old) == 1, old
s = s.replace(old, new)
p.write_text(s)
PY
```

If the roadmap's first assertion fails because another session changed the "Now" paragraph, read the paragraph and record slice 1b where it fits.

- [ ] **Step 3: Run the remaining gates**

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md
git commit -m "docs: record catalog batch 4 slice 1b"
```
