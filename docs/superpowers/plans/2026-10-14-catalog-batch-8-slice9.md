# Catalog Batch 8, Slice 9 (Darkest Secret Lens) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Yew Staff (R40): "When using card powers, you may act as if you hold the Darkest Secret."

**Architecture:**

- **One check for card powers.** A new `CardPowerBanners.holdsDarkestSecret(ready, player)` says whether a player holds the Darkest Secret as a card power they use sees it. They do if they hold the banner, or if a power that overrides the check says they act as if they do.
  - The override is a trait, `CardPowerBanners.DarkestSecretOverride`. Yew Staff is its one implementation, and `CardPowerBanners` lists it once.
  - The card powers that ask the question call `CardPowerBanners` and never name Yew Staff: Disciples, Terror Spells, The Red Seer, and through `PlanContext` Fae Battalion, Rusting Ray, Fire Talkers and Unstable Summon.
  - Challenge, scoring, the Oathkeeper goal and every other reader keep `BannerRules.holder`. The design's `forCardPower` flag is dropped: the object's name carries the scope.
- **Yew Staff** is a persistent rule with no contributions, registered so the card counts as implemented. It holds the check while its holder has it faceup on their board. It writes no line.
- **Armed Mob** reads the check for its user only. With Yew Staff the user can be a target of their own Armed Mob, beside the banner's holder. With two targets it first asks whose adviser to discard.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 9 bullet);
  - "Slice 9: Darkest Secret lens";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 9 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` at `1c02b35d` or later. Slice 9 needs nothing from slice 8, but the two touch the same files. Two other changes may land on `main` while you work:

- **Slice 8 (effective suit)** changes Armed Mob's discard suit (`catalog.suitOf` becomes `EffectiveSuit.of`). It also removes `SpitefulMirrorCard`, the object right after `YewStaffCard` in `HoldingRelics.scala`, and edits `PowerKindsCatalogSuite`, `WalkerPowerCatalog`, the design's Status line and the ROADMAP. When you rewrite Armed Mob in Task 4, keep the suit reader that `main` has.
- **The note-keys plan** (`docs/superpowers/plans/2026-10-14-derive-note-keys.md`) changes how a power declares its note keys, and commits a snapshot, `src/test/resources/notes/declared-keys.txt`. If it has landed:
  - declare Armed Mob's new key the way its other keys are declared on `main`;
  - regenerate the snapshot as that plan says, and check that the only difference is Armed Mob's new `used.empty-both` key.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a reader stops using `BannerRules`, remove its import.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). Check with `wc -l`.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - Nothing under `gameplay/actions` imports `gameplay/powers`. `CardPowerBanners` lives under `gameplay/powers` for that reason: no reader outside the card powers can reach it.
- **Tree addresses.** Only Armed Mob's tree changes: it gains a `Branch` before its adviser question, which moves that question's address. Old replays need no support. No other procedure tree gains, loses or moves a node.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`. An untracked `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala` belongs to another session; leave it alone.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - File `src/main/scala/oathdigital/gameplay/powers/banner/CardPowerBanners.scala`: `object CardPowerBanners` with:
    - `trait DarkestSecretOverride` with `def actsAsDarkestSecretHolder(ready: ReadyGame, player: PlayerId): Boolean`;
    - `def holdsDarkestSecret(ready: ReadyGame, player: PlayerId): Boolean`.
  - File `src/main/scala/oathdigital/gameplay/powers/banner/YewStaff.scala`:
    - `object YewStaffCard`, moved unchanged from `catalog/holding/HoldingRelics.scala`;
    - `case object YewStaff extends ContributingPower with CardPowerBanners.DarkestSecretOverride`.
  - `PlanContext.holdsDarkestSecret: Boolean` replaces `PlanContext.holds(banner: Banner)`.
  - `ArmedMob` (the object) gains:
    - `val playerDecisionId: String = "power.armed-mob.player"`;
    - `val playerHeading: String`, `val noAdviser: String`, `def adviserHeading(own: Boolean): String`;
    - `val neither: NoteKey` (key name `used.empty-both`);
    - `def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId]`, replacing `target`.

---

## Rulings made at plan time

The product owner settled these on 2026-10-14. Task 1 writes them into the design and the rulings appendix.

1. **Unstable Summon.** With Yew Staff, its holder burns X secrets from the Darkest Secret wherever the banner is: held by another player or in the bank. The line is unchanged. With no secret on the banner it is still shown unavailable, with "Needs the Darkest Secret with a secret on it".
2. **Only when the holder uses the power.** Yew Staff works only while its holder is the one using a card power. An enemy's Armed Mob does not make the Yew Staff holder a target. Banner Breakers ("if the defender has the Darkest Secret") is the attacker's power, so it is unaffected.
3. **No line.** Yew Staff applies automatically and writes nothing. The power's own line shows its effect.
4. **One check, no flag.** `CardPowerBanners.holdsDarkestSecret` serves the card powers. Yew Staff overrides it through `CardPowerBanners.DarkestSecretOverride`, so no card power names Yew Staff. Every reader that is not a card power keeps `BannerRules.holder`.
5. **Slice 9 stays separate** from slice 10. The cards that are not built yet:
   - Giant Python (slice 10), Ancient Pact and High Priest read the check when they land.
   - Tome Guardians' (E18) protection reads the real holder, because it guards the actual banner.
   - The Darkest Secret banner's own powers are not card powers, so Yew Staff does not reach them.
6. **Armed Mob used by the Yew Staff holder.** The holder may be a target of their own Armed Mob unless they also hold the People's Favor.
   - The targets are the players who hold the Darkest Secret but not the People's Favor, in seat order. The user holds it as their card powers see it; every other player only by holding the banner.
   - **With two targets,** Armed Mob first asks "Armed Mob: discard a faceup adviser of which player?". A target with no faceup adviser is shown unavailable, with "No faceup adviser to discard". If neither target has one, nothing is asked.
   - **With one target,** nothing is asked about players.
   - The adviser question's heading is "Armed Mob: discard one of your faceup advisers" for the user's own advisers. It stays "Armed Mob: discard a faceup adviser of the Darkest Secret's holder" for another player's.
   - **Lines.** The existing lines are unchanged. With two targets and nothing to discard, the line is "Neither target had a faceup adviser to discard." The player question posts no "Chose" line, because every line after it names the player.

## File map

- Create:
  - `src/main/scala/oathdigital/gameplay/powers/banner/CardPowerBanners.scala`.
  - `src/main/scala/oathdigital/gameplay/powers/banner/YewStaff.scala`: `YewStaffCard` and `YewStaff`.
  - `src/test/scala/oathdigital/gameplay/powers/banner/YewStaffSuite.scala`: 3 tests.
- Modify, the check (Task 2):
  - `catalog/holding/HoldingRelics.scala`: `YewStaffCard` moves out;
  - `gameplay/powers/WalkerPowerCatalog.scala`;
  - test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`.
- Modify, the readers (Task 3), all under `gameplay/powers`:
  - `search/Disciples.scala`, `action/TerrorSpells.scala`, `action/TheRedSeer.scala`;
  - `campaign/PlanContext.scala`, `campaign/FaeBattalion.scala`, `campaign/RustingRay.scala`, `campaign/FireTalkers.scala`, `campaign/UnstableSummon.scala`;
  - tests, one each: `DisciplesSuite`, `TerrorSpellsSuite`, `TheRedSeerSuite`, `FaeBattalionSuite`, `RustingRaySuite`, `FireTalkersSuite`, `UnstableSummonSuite`.
- Modify, Armed Mob (Task 4): `gameplay/powers/action/ArmedMob.scala`; test `ArmedMobSuite`, 6 tests.
- Modify, docs (Tasks 1 and 5): the design, the rulings appendix and `docs/ROADMAP.md`.

`gameplay/cards/NewFoundations.scala` needs no change: it already imports `oathdigital.gameplay.powers.banner._`, so `YewStaffCard` resolves at its new home.

---

### Task 1: Write the plan-time rulings into the design and the rulings appendix

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

**Interfaces:** none.

- [ ] **Step 1: Replace the slice 9 bullet of "The bar"**

In the design, replace the bullet that starts `- **One Darkest Secret check** (slice 9).` with:

```markdown
- **One Darkest Secret check for card powers** (slice 9, detailed at plan
  time). `CardPowerBanners.holdsDarkestSecret(ready, player)` says whether
  a player holds the Darkest Secret as a card power they use sees it: they
  hold the banner, or a power that overrides the check
  (`CardPowerBanners.DarkestSecretOverride`) says they act as if they do.
  Yew Staff is the one override, listed once in `CardPowerBanners`. The
  card powers that ask (Disciples, Terror Spells, The Red Seer, Fae
  Battalion, Rusting Ray, Fire Talkers, Unstable Summon, and Armed Mob for
  its user) call it and never name Yew Staff. Challenge, scoring, the
  Oathkeeper goal and every other reader keep `BannerRules.holder`, so the
  check needs no flag.
```

- [ ] **Step 2: Add the plan-time rulings under the design's Slice 9**

In the design, right after the paragraph under `## Slice 9: Darkest Secret lens`, add:

```markdown
### Plan-time rulings

Settled 2026-10-14; the rulings appendix has them per card.

- Yew Staff works only while its holder uses the card power. It applies
  automatically and writes no line.
- Unstable Summon burns from the Darkest Secret wherever the banner is.
- The Yew Staff holder may target themselves with their own Armed Mob.
  With two targets, Armed Mob first asks whose adviser to discard.
- Slice 9 stays separate from slice 10. Giant Python, Ancient Pact and
  High Priest read the check when they land; Tome Guardians reads the real
  holder.
```

- [ ] **Step 3: Rewrite the rulings appendix's Slice 9**

In the rulings appendix, replace everything from `- **R40 Yew Staff.**` to the line before `## Slice 10: Power-use restrictions` with:

```markdown
- **R40 Yew Staff.** "When using card powers, you may act as if you hold
  the Darkest Secret."
  - Every condition inside a card power its holder uses that asks whether
    they hold the Darkest Secret is met: Disciples, Terror Spells, The Red
    Seer, Fae Battalion, Rusting Ray, Fire Talkers and Unstable Summon.
    Giant Python (slice 10) reads it too.
  - It works only while its holder is the one using the power, with Yew
    Staff faceup on their board. An enemy's Armed Mob does not make them a
    target, and Banner Breakers, the attacker's power, is unaffected.
  - It applies automatically and writes no line; the power's own line
    shows its effect.
  - It does not reach Challenge, scoring, the Oathkeeper goal or the
    Darkest Secret banner's own powers, which are not card powers.
  - **Unstable Summon** burns X secrets from the Darkest Secret wherever
    the banner is: held by another player or in the bank. The line is
    unchanged (plan-time ruling).
  - **Armed Mob used by the holder** (plan-time ruling). The holder may be
    a target of their own Armed Mob unless they also hold the People's
    Favor.
    - With two targets it first asks "Armed Mob: discard a faceup adviser
      of which player?". A target with no faceup adviser is shown
      unavailable, with "No faceup adviser to discard". With neither
      having one, nothing is asked, and the line is "Neither target had a
      faceup adviser to discard."
    - With one target nothing is asked about players. The adviser question
      reads "Armed Mob: discard one of your faceup advisers" for the
      user's own advisers.
  - **Cards not built yet** (plan-time ruling). Ancient Pact and High
    Priest read the check when they land. Tome Guardians' protection reads
    the real holder, because it guards the actual banner.
```

- [ ] **Step 4: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: no finding.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 9's plan-time rulings"
```

---

### Task 2: The check, and Yew Staff

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/banner/CardPowerBanners.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/banner/YewStaff.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/banner/YewStaffSuite.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `BannerRules.holder(current: GameState, banner: Banner): Option[PlayerId]` (`gameplay/actions/BannerRules.scala`).
- Produces: `CardPowerBanners.holdsDarkestSecret(ready: ReadyGame, player: PlayerId): Boolean`, which Tasks 3 and 4 call; `YewStaffCard`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/banner/YewStaffSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** Yew Staff: its holder holds the Darkest Secret as the card powers they
  * use see it. Each power's own suite tests that it reads the check. */
class YewStaffSuite extends munit.FunSuite:
  private val yewStaff = "Yew Staff"

  private def holdsForCardPowers(table: Table, player: PlayerId): Boolean =
    CardPowerBanners.holdsDarkestSecret(table.ready, player)

  test("the Darkest Secret's holder holds it, and no one else"):
    val table = Table.start.darkestSecret(Some(p1), 1)
    assert(holdsForCardPowers(table, p1))
    assert(!holdsForCardPowers(table, p2))

  test("a faceup Yew Staff holds it for its holder, wherever the banner is"):
    for banner <- Vector(Some(p2), None) do
      val table = Table.start.darkestSecret(banner, 1).relic(p1, yewStaff)
      assert(holdsForCardPowers(table, p1), banner.toString)

  test("a facedown Yew Staff, or one at a site, holds nothing"):
    val facedown = Table.start.darkestSecret(None, 1)
      .relic(p1, yewStaff, facedown = true)
    assert(!holdsForCardPowers(facedown, p1))
    val atSite = Table.start.darkestSecret(None, 1)
      .pawn(p1, "Ancient City").relicAt(yewStaff, "Ancient City")
    assert(!holdsForCardPowers(atSite, p1))
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.YewStaffSuite"`
Expected: compilation fails, because `CardPowerBanners` does not exist.

- [ ] **Step 3: Create `CardPowerBanners.scala`**

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The banners as a card power sees them for the player using it (catalog
  * batch 8, slice 9). Only card powers read it. Challenge, scoring, the
  * Oathkeeper goal and every other rule read `BannerRules`.
  */
object CardPowerBanners:
  /** A power that lets its holder act as if they hold the Darkest Secret
    * while using card powers. */
  trait DarkestSecretOverride:
    def actsAsDarkestSecretHolder(ready: ReadyGame, player: PlayerId)
        : Boolean

  /** Every override in the catalog. */
  private val overrides: Vector[DarkestSecretOverride] = Vector(YewStaff)

  /** Whether `player` holds the Darkest Secret, as a card power they use
    * asks it: they hold the banner, or an override says they act as if
    * they do. */
  def holdsDarkestSecret(ready: ReadyGame, player: PlayerId): Boolean =
    BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player) ||
      overrides.exists(_.actsAsDarkestSecretHolder(ready, player))
```

- [ ] **Step 4: Create `YewStaff.scala`**

Cut `object YewStaffCard` out of `catalog/holding/HoldingRelics.scala` and paste it, unchanged, where the comment below says. `HoldingRelics.scala` keeps its imports, because its other relics use them.

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

// object YewStaffCard, moved here unchanged from HoldingRelics.scala.

/** Yew Staff (relic R40): "When using card powers, you may act as if you
  * hold the Darkest Secret."
  *
  * While its holder has it faceup, every card power they use that asks
  * whether they hold the Darkest Secret finds that they do
  * (`CardPowerBanners.holdsDarkestSecret`). It applies automatically and
  * writes no line. This power hooks no window. It is registered so the
  * card counts as implemented.
  */
case object YewStaff extends ContributingPower
    with CardPowerBanners.DarkestSecretOverride:
  val id: PowerId = YewStaffCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty

  def actsAsDarkestSecretHolder(ready: ReadyGame, player: PlayerId)
      : Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.relics.exists {
        case RelicState(card, Orientation.FaceUp, _) =>
          card == YewStaffCard.id
        case _ => false
      })
```

- [ ] **Step 5: Register Yew Staff**

In `WalkerPowerCatalog.scala`:
- after `Vector(SecretTestament) ++`, add `Vector(YewStaff) ++`;
- in the doc comment, after the sentence about Secret Testament, add: `Yew Staff hooks no window: card powers read it through CardPowerBanners.`

In `PowerKindsCatalogSuite`, add `"relic.yew-staff"` as the last entry of `persistentRules`.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.YewStaffSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS, 3 new tests.

Run: `./sbtw test`
Expected: PASS, the baseline plus 3.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/banner/CardPowerBanners.scala \
  src/main/scala/oathdigital/gameplay/powers/banner/YewStaff.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/banner/YewStaffSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Yew Staff and the card powers' Darkest Secret check"
```

---

### Task 3: The card powers read the check

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/TerrorSpells.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/TheRedSeer.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/FaeBattalion.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/RustingRay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/FireTalkers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala`
- Test: `DisciplesSuite`, `TerrorSpellsSuite`, `TheRedSeerSuite`, `FaeBattalionSuite`, `RustingRaySuite`, `FireTalkersSuite`, `UnstableSummonSuite`, one new test each.

**Interfaces:**
- Consumes from Task 2: `CardPowerBanners.holdsDarkestSecret`.
- Produces: `PlanContext.holdsDarkestSecret: Boolean`.

- [ ] **Step 1: Write the failing tests**

Add each test at the end of its suite. Each suite's helpers are already defined there.

`src/test/scala/oathdigital/gameplay/powers/search/DisciplesSuite.scala`:

```scala
  test("with Yew Staff and no Darkest Secret, a world Search costing 3 " +
      "costs 2"):
    for holder <- Vector(Some(p2), None) do
      val ready = Table.from(holdingDisciples(visions = 1, holder = holder))
        .relic(p1, "Yew Staff").ready
      assertEquals(spent(ready, modifiers), 2, holder.toString)
```

`src/test/scala/oathdigital/gameplay/powers/action/TerrorSpellsSuite.scala`:

```scala
  test("with Yew Staff and no Darkest Secret it kills two"):
    val ready = staged.darkestSecret(Some(p2), 1).relic(p1, "Yew Staff")
      .boardWarbands(p2, 0).ready
    val done = accepted(use(ready, TerrorSpells, source))
    assert(backToActing(done))
    assertEquals(Look(after(done)).boardWarbands(p1), 1)
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Killed 2 warbands.", covers = false)))
```

`src/test/scala/oathdigital/gameplay/powers/action/TheRedSeerSuite.scala`:

```scala
  test("with Yew Staff and no Darkest Secret it is usable"):
    val ready = Table.from(seerHeldUnder(OathkeeperGoal.Supremacy,
      holds = false)).relic(p1, "Yew Staff").ready
    assert(usableNow(ready).exists(_.power.id == TheRedSeer.id))
    assert(awaits(accepted(use(ready, TheRedSeer, source)),
      TheRedSeer.decisionId))
```

`src/test/scala/oathdigital/gameplay/powers/campaign/FaeBattalionSuite.scala`:

```scala
  test("with Yew Staff it counts hollow swords while another player holds " +
      "the Darkest Secret"):
    val base = attacker
    val b = on(base)(_.darkestSecret(Some(base.rival), secrets = 1)
      .relic(base.actor, "Yew Staff"))
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 3)
```

`src/test/scala/oathdigital/gameplay/powers/campaign/RustingRaySuite.scala`:

```scala
  test("with Yew Staff the defender uses it without the Darkest Secret"):
    val base = defender(darkest = false)
    val b = on(base)(_.relic(base.rival, "Yew Staff"))
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 0)
    assertEquals(said(RustingRay, done.events), Vector(ignored))
```

`src/test/scala/oathdigital/gameplay/powers/campaign/FireTalkersSuite.scala`:

```scala
  test("with Yew Staff it is offered while another player holds the " +
      "Darkest Secret"):
    val attacker = attackerHoldingFireTalkersAndTheDarkestSecret
    val b = on(withDarkestSecret(attacker, attacker.rival))(
      _.relic(attacker.actor, "Yew Staff"))
    val picked = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
```

`src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala`:

```scala
  test("with Yew Staff it burns from the Darkest Secret another player " +
      "holds"):
    val base = attackerWithSummon(bannerSecrets = 3,
      attackerHoldsBanner = false)
    val b = on(base)(_.relic(base.actor, "Yew Staff"))
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, UnstableSummon.decisionId, ChooseAmountAnswer(2))
    assertEquals(Look(done.state).secretsOnDarkestSecret, 1)
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(lines(done), Vector(burned(
      "2 secrets from the Darkest Secret for 2 attack dice")))
```

Add any import a suite lacks. `DisciplesSuite`, `TerrorSpellsSuite` and `TheRedSeerSuite` already import `Table`.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.DisciplesSuite oathdigital.gameplay.powers.action.TerrorSpellsSuite oathdigital.gameplay.powers.action.TheRedSeerSuite oathdigital.gameplay.powers.campaign.FaeBattalionSuite oathdigital.gameplay.powers.campaign.RustingRaySuite oathdigital.gameplay.powers.campaign.FireTalkersSuite oathdigital.gameplay.powers.campaign.UnstableSummonSuite"`
Expected: the 7 new tests fail, because the powers read the banner only. The other tests pass.

- [ ] **Step 3: Route the readers through the check**

Each change replaces a direct banner read. Add `import oathdigital.gameplay.powers.banner.CardPowerBanners` to each file changed below, and remove `BannerRules` from its imports when nothing else in the file uses it.

`Disciples.scala`, in the `SpendSupply` case's guard, replace:

```scala
              BannerRules.holder(ready.game.current, Banner.DarkestSecret)
                .contains(player) =>
```

with:

```scala
              CardPowerBanners.holdsDarkestSecret(ready, player) =>
```

`TerrorSpells.scala`, replace the body of `private def holds`:

```scala
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    CardPowerBanners.holdsDarkestSecret(ready, player)
```

`TheRedSeer.scala`, replace the body of `override def usable`:

```scala
  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    CardPowerBanners.holdsDarkestSecret(ready, player)
```

`PlanContext.scala`, replace `def holds(banner: Banner)` and its doc comment with:

```scala
  /** Whether the plan's user holds the Darkest Secret, as the card powers
    * they use see it (`CardPowerBanners`). A bandit defender holds
    * none. */
  def holdsDarkestSecret: Boolean =
    user.exists(CardPowerBanners.holdsDarkestSecret(ready, _))
```

`PlanContext` keeps its `BannerRules` import, which `enemyHolds` uses.

In `FaeBattalion.scala`, `RustingRay.scala`, `FireTalkers.scala` and `UnstableSummon.scala`, replace `context.holds(Banner.DarkestSecret)` with `context.holdsDarkestSecret`. These four files do not need the `CardPowerBanners` import.

In `UnstableSummon`'s doc comment, change "While its user holds the Darkest Secret with a secret on it" to "While its user holds the Darkest Secret, as their card powers see it, with a secret on it". Then add at the end of that paragraph: "With Yew Staff the secrets burn from the banner wherever it is."

- [ ] **Step 4: Check that only the allowed readers read the banner directly**

Run: `grep -rn "Banner.DarkestSecret" src/main/scala/oathdigital/gameplay/powers | grep "holder"`
Expected, and nothing else:
- `banner/CardPowerBanners.scala`: the check itself;
- `whenplayed/WizardsConclave.scala`: who takes the banner;
- `action/ArmedMob.scala`: its targets (Task 4 changes it).

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command again.
Expected: PASS.

Run: `./sbtw test`
Expected: PASS, the baseline plus 10.

- [ ] **Step 6: Commit**

Stage the 8 production files and 7 suites by path, then:

```bash
git commit -m "feat(batch8): card powers read the Darkest Secret through Yew Staff's lens"
```

---

### Task 4: Armed Mob with Yew Staff

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/ArmedMobSuite.scala`

**Interfaces:**
- Consumes from Task 2: `CardPowerBanners.holdsDarkestSecret`.
- Produces: the `ArmedMob` object members listed in Global Constraints.

- [ ] **Step 1: Write the failing tests**

In `ArmedMobSuite`:
- add `ParkedNode` to the `oathdigital.testkit` import;
- add `import oathdigital.gameplay.powers.PhasePowerCatalog`;
- add this helper and value after `choose`:

```scala
  private val yewStaff = "Yew Staff"

  /** The options of the decision the use is parked on, unavailable ones
    * included. */
  private def parkedChoiceOptions(t: OathTransition)
      : Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _, _) => options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  private def playerOption(player: PlayerId): DecisionOption =
    DecisionOption.Player(DecisionOptionRef.Player(player))
```

Then add these tests at the end:

```scala
  test("an enemy's Yew Staff makes them no target"):
    val ready = staged().darkestSecret(None, 1).relic(p2, yewStaff)
      .adviser(p2, homesteaders).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](homesteaders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none",
      "No player held the Darkest Secret without the People's Favor.",
      covers = true)))

  test("with Yew Staff the player is a target beside the holder: it asks " +
      "whose adviser, then which"):
    val ready = staged().relic(p1, yewStaff).adviser(p1, homesteaders)
      .adviser(p2, landWarden).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.playerDecisionId))
    assertEquals(offered(started, p1),
      Some(Vector("player" -> p1.value, "player" -> p2.value)))
    val chose = accepted(answer(started, p1, ArmedMob.playerDecisionId,
      pick(DecisionOptionRef.Player(p1))))
    assert(awaits(chose, ArmedMob.decisionId))
    assertEquals(offered(chose, p1),
      Some(Vector("denizen" -> homesteaders.value)))
    val done = accepted(answer(chose, p1, ArmedMob.decisionId,
      pick(DecisionOptionRef.Denizen(homesteaders))))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](landWarden))
    val events = started.events ++ chose.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${homesteaders.value} from " +
        s"${p1.value}'s advisers.",
      covers = true)))

  test("a target with no faceup adviser is shown unavailable"):
    val ready = staged().relic(p1, yewStaff).adviser(p1, homesteaders)
      .adviser(p2, landWarden, facedown = true).ready
    val started = accepted(use(ready, power, source))
    assertEquals(parkedChoiceOptions(started), Vector[DecisionOption](
      playerOption(p1), DecisionOption.Unavailable(playerOption(p2),
        OptionReason.Written(ArmedMob.noAdviser))))

  test("with Yew Staff and no other target it asks only for the player's " +
      "own adviser"):
    val ready = staged().darkestSecret(None, 1).relic(p1, yewStaff)
      .adviser(p1, homesteaders).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.decisionId))
    assertEquals(offered(started, p1),
      Some(Vector("denizen" -> homesteaders.value)))

  test("with the People's Favor, Yew Staff makes the player no target"):
    val ready = staged().peoplesFavor(Some(p1), 1).relic(p1, yewStaff)
      .adviser(p1, homesteaders).adviser(p2, landWarden).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.decisionId))
    assertEquals(offered(started, p1),
      Some(Vector("denizen" -> landWarden.value)))

  test("with two targets and no faceup adviser nothing is asked, and the " +
      "line says so"):
    val done = accepted(use(staged().relic(p1, yewStaff).ready, power,
      source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.empty-both", "Neither target had a faceup adviser to discard.",
      covers = true)))
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArmedMobSuite"`
Expected: compilation fails, because `ArmedMob.playerDecisionId` and `ArmedMob.noAdviser` do not exist.

- [ ] **Step 3: Rewrite Armed Mob**

Replace the doc comment, the class and the object in `ArmedMob.scala` with the code below. Keep `ArmedMobCard` as it is.

Add `import oathdigital.gameplay.powers.banner.CardPowerBanners`.

In `discardOf`, keep the suit reader that `main` has. The code below shows `catalog.suitOf`; after slice 8 it is `EffectiveSuit.of`. If the note-keys plan has landed, declare `neither` the way `main` declares the other keys, instead of the `noteKeys` override shown.

```scala
/** Armed Mob (card 53, site-only), ACTION: place 1 favor on this card, then
  * discard a faceup adviser from a player who holds the Darkest Secret but
  * not the People's Favor.
  *
  * The targets are the players who hold the Darkest Secret but not the
  * People's Favor, in seat order. The player holds it as their card powers
  * see it (Yew Staff, `CardPowerBanners`); every other player only by
  * holding the banner. So the player may be a target, and with Yew Staff
  * one beside the banner's holder.
  *
  * With two targets the player first chooses whose adviser to discard. A
  * target with no faceup adviser is shown unavailable, and with neither
  * having one nothing is asked. The candidates are the target's faceup
  * denizen advisers, in adviser order. The chosen card gets the standard
  * discard: to the discard pile of the region after the player's, its
  * favor to its suit's bank and its secrets to the player facedown. The
  * discard is a required batch, so the Locked restriction hides a locked
  * adviser, and the question passes when nothing is left. With no target,
  * or nothing to discard, the cost stays paid and one line says so.
  *
  * Its line covers the generic Discarded line.
  */
final case class ArmedMob private (catalog: ExecutableCatalog)
    extends PaidAction(ArmedMobCard.power):
  import ArmedMob._

  override def noteKeys: Vector[NoteKey] =
    Vector(discarded, nobody, bare, neither)
  override def narratedDecisions: Set[String] =
    Set(playerDecisionId, decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askPlayer(live, player)),
    Branch((live, pending) => askAdviser(live, player, pending)),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  /** Whose adviser to discard, while two players are targets and one of
    * them has a faceup adviser. A target with none is shown unavailable. */
  private def askPlayer(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] =
    val found = targets(ready, actor)
    if found.size < 2 || found.forall(candidates(ready, _).isEmpty) then
      Vector.empty
    else Vector(Decide(playerDecisionId, actor, DecisionQuery.ChooseOne(
      found.map { owner =>
        val option = DecisionOption.Player(DecisionOptionRef.Player(owner))
        if candidates(ready, owner).nonEmpty then option
        else DecisionOption.Unavailable(option,
          OptionReason.Written(noAdviser))
      },
      heading = Some(playerHeading))))

  private def askAdviser(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    owner(ready, actor, pending).toVector.flatMap(target =>
      candidates(ready, target) match
        case Vector() => Vector.empty
        case found => Vector(Decide(decisionId, actor,
          DecisionQuery.ChooseOne(found.map(card => DecisionOption.Denizen(
            DecisionOptionRef.Denizen(card.id))),
            heading = Some(adviserHeading(target == actor))),
          passWhenEmpty = true)))

  /** The target the discard comes from: the player chosen, else the only
    * target. */
  private def owner(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[PlayerId] =
    PowerAnswers.one(pending, playerDecisionId) match
      case Some(DecisionOptionRef.Player(chosen)) => Some(chosen)
      case _ => targets(ready, actor) match
        case Vector(only) => Some(only)
        case _ => None

  /** No answer means there was no target or nothing it could lose. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => discardOf(ready, actor, pending, ref)

  private def discardOf(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree, ref: DecisionOptionRef)
      : Either[OathViolation, Vector[CoreOperation]] =
    for
      target <- owner(ready, actor, pending).toRight(
        OathViolation.InvalidEventOrder("no player is an Armed Mob target"))
      chosen <- candidates(ready, target)
        .find(card => DecisionOptionRef.Denizen(card.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Armed Mob can discard"))
      suit <- catalog.suitOf(chosen.id)
        .toRight(OathViolation.UnknownWorldCard(chosen.id))
      region <- PowerAccess.pawnSite(ready, actor)
        .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
        .toRight(OathViolation.PawnSiteMissing(actor))
    yield Vector[CoreOperation](Discard.Denizen(chosen.id,
      PositionedLocation(Location.PlayArea(target)), region, suit,
      chosen.tokens.favor, chosen.tokens.secrets, actor, required = true))

  /** The card discarded and its owner; with no card chosen, why. */
  private def discardedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Denizen(chosen)) =>
          discardedLine(states, actor, card, chosen)
        case _ => Some(unansweredLine(states, actor, card)))

  /** Why nothing was discarded: no target, a target with nothing to lose,
    * or two such targets. */
  private def unansweredLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef): PowerNote =
    NoteSupport.answer(states, playerDecisionId) match
      case Some(DecisionOptionRef.Player(chosen)) =>
        bare(card, NoteArg.Player(chosen))
      case _ => targets(states.now, actor) match
        case Vector() => nobody(card)
        case Vector(only) => bare(card, NoteArg.Player(only))
        case _ => neither(card)

  /** The owner is whoever held the card before the discard. */
  private def discardedLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef, chosen: DenizenId): Option[PowerNote] = for
    step <- states.previous
    owner <- step._1.game.current.players
      .find(_.advisers.exists(_.id == chosen)).map(_.player)
  yield discarded(card, NoteArg.Player(actor), NoteArg.Card(chosen),
    NoteArg.Player(owner))

object ArmedMob:
  val id: PowerId = ArmedMobCard.power.id
  val decisionId: String = "power.armed-mob.adviser"
  val playerDecisionId: String = "power.armed-mob.player"
  val playerHeading: String =
    "Armed Mob: discard a faceup adviser of which player?"
  val noAdviser: String = "No faceup adviser to discard"

  /** The adviser question's heading, for the player's own advisers or the
    * banner holder's. */
  def adviserHeading(own: Boolean): String =
    if own then "Armed Mob: discard one of your faceup advisers"
    else "Armed Mob: discard a faceup adviser of the Darkest Secret's holder"

  // Keep the existing `discarded`, `nobody` and `bare` keys, unchanged,
  // here.

  /** "Neither target had a faceup adviser to discard." */
  val neither: NoteKey = NoteKey("used.empty-both", Vector(NotePart.Text(
    "Neither target had a faceup adviser to discard.")))

  /** The players who hold the Darkest Secret but not the People's Favor,
    * in seat order. The actor holds it as their card powers see it; every
    * other player only by holding the banner. */
  def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    val current = ready.game.current
    current.players.map(_.player).filter(player =>
      holds(ready, actor, player) &&
        !BannerRules.holder(current, Banner.PeoplesFavor).contains(player))

  private def holds(ready: ReadyGame, actor: PlayerId, player: PlayerId)
      : Boolean =
    if player == actor then CardPowerBanners.holdsDarkestSecret(ready, actor)
    else BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player)

  /** `owner`'s faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame, owner: PlayerId)
      : Vector[DenizenState] =
    PlayerFacts.player(ready, owner).toOption.toVector.flatMap(
      _.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card })

  def forCatalog(catalog: ExecutableCatalog): ArmedMob =
    new ArmedMob(catalog)
```

The class reads `candidates` through `import ArmedMob._`. A class sees its companion's private members, so `candidates` and `holds` stay private.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArmedMobSuite"`
Expected: PASS, the 6 new tests and every existing one. The existing "the player may be the target" test now sees the "one of your faceup advisers" heading; it checks no heading, so it passes unchanged.

Run: `./sbtw test`
Expected: PASS, the baseline plus 16.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ArmedMobSuite.scala
git commit -m "feat(batch8): Armed Mob may target its Yew Staff user"
```

If the note-keys snapshot was regenerated, stage `src/test/resources/notes/declared-keys.txt` too.

---

### Task 5: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:** none.

- [ ] **Step 1: Run the gates**

1. `./sbtw "test" "frontend/test"`. Expected: PASS, the server count the baseline plus 16, the frontend count unchanged.
2. `./sbtw frontend/fullOptJS`. Expected: success.
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. Expected: no finding.

- [ ] **Step 2: Mark the slice done**

In the design's Status line, add slice 9 to the slices done. If slice 8 is done, "Slices 1 to 8 are done." becomes "Slices 1 to 9 are done.". If not, "Slices 1 to 7 are done." becomes "Slices 1 to 7 and 9 are done.".

In `docs/ROADMAP.md`:
- check the box of `**9. Darkest Secret lens:**`;
- in the Now section, say slice 9 is done in the same way. If slice 8 is done, write "slices 1 to 9 are done, and slice 10 is next". If not, write "slices 1 to 7 and 9 are done, and slice 8 is next".

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/ROADMAP.md
git commit -m "docs(batch8): slice 9 done"
```
