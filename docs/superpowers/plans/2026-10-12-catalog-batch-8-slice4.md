# Catalog Batch 8, Slice 4 (A Raid's Defeat Replaced) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Keep (5): a defender who rules Keep's site and loses a Raid gives up only what the attacker targeted, and their pawn goes to Keep's site.

**Architecture:**

- **A Raid transfer in two parts.** `CampaignRaid.transfer` builds the takes, the facedown discards, the set-aside relics and the half-favor burn as one batch. It splits into two builders:
  - `CampaignRaid.takes`: the relic and banner takes;
  - `CampaignRaid.consequences`: the discards, the set-aside relics, the burn and the Conspiracy's return to the box.

  The consequences run in their own window, the new `PowerWindow.CampaignRaidConsequences`. Its `Sequence` is nested inside the transfer's `Sequence`, after the takes, so the relocation keeps its tree address. A power drops the consequences by folding that window to nothing.
- **Keep** is a `ContributingPower` with two `Transform`s, both applicable only when the Raid's defeated defender rules Keep's site:
  - at `CampaignRaidConsequences` it returns nothing;
  - at `CampaignRaidRelocation` it replaces the relocation question with a `Branch`. The `Branch` moves the defender's pawn to Keep's site, or leaves it there, and writes the line.
- **The closing line.** `CampaignLines.closing` summarises what the loser lost, including "was sent to {site}" for any pawn move. Keep's line restates its move and covers it. The closing summary now leaves out every step that a covering note restates, so it does not repeat the move as a relocation.
- **No new operation, event or codec entry.** Old journals hold one transfer batch and replay as they are. New ones hold two.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "Goal and scope", "The bar" (the Raid transfer bullet), "Slice 4: A Raid's defeat replaced", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 4 entry.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 3 merged. Slice 3 is being executed in `.claude/worktrees/catalog-batch-8-slice3`.

If slice 3 has not merged when you start, branch from `main` at `20c5b4da` or later, and expect these conflicts when this branch merges:

- `PlanRules.forCatalog`'s list and doc comment: keep both additions;
- `CampaignLines.closing`: keep slice 3's `ended` fallback and this plan's `restated` filter;
- `LogScripts.raidAnswers`: both make it public;
- the design's Status line and the ROADMAP's Now line: write what is true on `main`.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding catalog file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** A parked game is stored by the address of its node in the procedure tree. The only new node in the Campaign tree is the consequences `Sequence`, as the second child of the transfer's `Sequence`. Do not wrap or insert anything elsewhere. The relocation's address must not change.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - `PowerWindow.CampaignRaidConsequences`, key `"campaign.raid-consequences"`.
  - `CampaignRaid.takes` and `CampaignRaid.consequences`, each `(ReadyGame, CampaignResult) => Either[OathViolation, Vector[CoreOperation]]`. `CampaignRaid.transfer` goes away.
  - `oathdigital.gameplay.powers.campaign.Keep` (case object) and `KeepCard`, in `campaign/Keep.scala`. Power id `denizen.keep`, unchanged.
  - `Keep.ended: NoteKey`, name `"ended"`.
  - `Keep.kept(ready): Option[(PlayerId, SiteId)]`.
- **Texts, exactly:**
  - Keep's line: "Ended the Raid with {Blue}'s pawn at {site}." It reads the same whether the pawn moved or stayed.
  - Keep asks no question and posts no "Chose" line.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 8 server tests and no frontend test.
  - It changes no existing test's expectations. `CampaignRaidSuite`'s private `walk` helper gains one parameter with a default.
  - Every existing Campaign, Raid and Game Log suite must pass unchanged.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These are for the product owner's review with the plan. Task 1 writes them into the spec.

1. **When Keep applies.** Keep is faceup at a site, and the Raid's defeated defender rules that site. It is judged after the battle's losses, when the Raid's consequences would run. A Raid's losses and takes never change who rules a site, so the answer holds for the rest of the Raid.
2. **Automatic.** Keep asks nothing, and the defender cannot decline it. "Place your pawn" is a must.
3. **What stays:**
   - the battle's losses, made before the Raid;
   - the attacker's takes, in the printed order;
   - the tokens that leave with a taken banner: the People's Favor's favor returns to the banks, and the Darkest Secret's secrets are burnt. They are part of taking the banner, not of the defeat.
4. **What is dropped:**
   - the half-favor burn;
   - the facedown advisers' discard, including a Conspiracy's return to the box;
   - the facedown relics set aside;
   - the relocation question.
5. **The pawn** moves to Keep's site. If it already stands there (Keep at the Raid's own site), it stays.
6. **"End the action"** ends the Raid. Nothing in the Raid follows the relocation, so nothing more is skipped. The effects at the Campaign's end still run as usual:
   - Book Burning still burns the defender's secrets;
   - Herald still pays;
   - battle plans still discard.
7. **Lines.** Keep writes "Ended the Raid with {Blue}'s pawn at {site}.", in both cases.
   - The Campaign's closing line no longer says the pawn "was sent to" Keep's site.
   - That line has no burn, discard or set-aside to report, so it says only what the battle's losses took.
8. **Saved games.** The relocation keeps its tree address, so a game parked on the relocation question when the server updates still resumes. Old journals replay their one transfer batch unchanged.

## File Structure

- **Create:**
  - `src/main/scala/oathdigital/gameplay/powers/campaign/Keep.scala`: the card and the power.
  - `src/test/scala/oathdigital/gameplay/powers/campaign/KeepSuite.scala`
  - `src/test/scala/oathdigital/application/gamelog/KeepLogSuite.scala`
- **Modify:**
  - `src/main/scala/oathdigital/model/PowerWindow.scala`: `CampaignRaidConsequences`.
  - `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`: the split.
  - `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`: the new window is after the outcome.
  - `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`: registers Keep.
  - `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`: the card moves out.
  - `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports.
  - `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`: the closing summary skips restated steps.
  - `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`: the seam test.
  - `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`: Keep is automatic.
  - `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`: `raidAnswers` becomes public, if slice 3 has not already done so.
  - The design, the rulings appendix and `docs/ROADMAP.md`.

---

### Task 1: Record the plan-time rulings

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", "Slice 4: A Raid's defeat replaced")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` ("Slice 4: A Raid's defeat replaced")

- [ ] **Step 1: Sharpen "The bar".** Replace the "A Raid transfer in two parts" bullet with:

```markdown
- **A Raid transfer in two parts** (slice 4). `CampaignRaid.transfer`
  builds the takes, the facedown discards, the set-aside relics and the
  half-favor burn as one batch. It splits into `takes` and `consequences`.
  The consequences run in their own window, `CampaignRaidConsequences`,
  nested in the transfer's window after the takes, so a power can drop them
  while the takes stay. The relocation keeps its tree address. The
  Campaign's closing line leaves out any step that a power's covering line
  restates.
```

- [ ] **Step 2: Rewrite the design's slice 4 section.** Replace the paragraph under "## Slice 4: A Raid's defeat replaced" with:

```markdown
Keep transforms `CampaignRaidConsequences` and `CampaignRaidRelocation`
when the defeated defender rules Keep's site. The takes stay. The
consequences are dropped, and the relocation question is replaced by
placing the defender's pawn at Keep's site. Nothing else in the Raid
follows the relocation. The losses before the Raid, and the effects at the
Campaign's end, run as usual.

### Plan-time rulings

- **When.** Keep is faceup at a site the defeated defender rules, judged
  after the battle's losses. The losses and the takes never change who
  rules a site, so this holds for the rest of the Raid.
- **Automatic.** Keep asks nothing, and the defender cannot decline it.
- **What stays.** The battle's losses, the attacker's takes, and the tokens
  that leave with a taken banner: the People's Favor's favor returns to
  the banks, and the Darkest Secret's secrets are burnt.
- **What is dropped.** The half-favor burn, the facedown advisers'
  discard (a Conspiracy's return to the box too), the facedown relics set
  aside, and the relocation question.
- **The pawn** moves to Keep's site, or stays when it is already there.
- **"End the action"** ends the Raid. The effects at the Campaign's end
  still run: Book Burning still burns, Herald still pays, and battle plans
  still discard.
- **Lines.** "Ended the Raid with {Blue}'s pawn at {site}." whether the
  pawn moved or stayed. The closing line no longer says the pawn "was sent
  to" Keep's site.
- **Saved games.** The relocation keeps its tree address. Old journals
  replay their one transfer batch unchanged.
```

- [ ] **Step 3: Extend the rulings appendix.** In the slice 4 section, after the last bullet of Keep's entry (`"End the action" ends the Raid there. Powers that act "after the Campaign" still run.`), add:

```markdown
  - Keep applies when it is faceup at a site the defeated defender rules,
    judged after the battle's losses. It asks nothing, and the defender
    cannot decline it (plan-time ruling).
  - The tokens that leave with a taken banner still leave: the People's
    Favor's favor returns to the banks, and the Darkest Secret's secrets
    are burnt. They are part of the take (plan-time ruling).
  - The facedown advisers' discard includes a Conspiracy's return to the
    box, and it is skipped too (plan-time ruling).
  - A pawn already at Keep's site stays there (plan-time ruling).
  - The effects at the Campaign's end still run: Book Burning still burns,
    Herald still pays, and battle plans still discard (plan-time ruling).
  - Line: "Keep: Ended the Raid with {Blue}'s pawn at {site}." The closing
    line no longer says the pawn "was sent to" Keep's site (plan-time
    ruling).
```

- [ ] **Step 4: Check the links.**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): slice 4 plan-time rulings for Keep"
```

---

### Task 2: Split the Raid transfer

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Test: `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`

**Interfaces:**
- Produces: `PowerWindow.CampaignRaidConsequences`; `CampaignRaid.takes` and `CampaignRaid.consequences`. Task 3's Keep folds the new window to nothing.

- [ ] **Step 1: Write the failing test.** In `CampaignRaidSuite.scala`:

  1. Give the private `walk` helper a way to choose the rules. Change its signature and its `game` line, and leave the rest as it is:

```scala
  /** Raids the rival with `attack` swords against blank defense dice,
    * sacrificing `sacrifice`, under `rulesFor`'s rules. */
  private def walk(b: Board, relic: RelicId, attack: Int, sacrifice: Int = 0,
      rulesFor: WalkerDice => OathRules = rules(_)): RaidWalk =
```

```scala
    val game = rulesFor(dice(sword,
      Vector.fill(printed)(DefenseDieFace.Blank)))
```

  2. Add a test-only power and the test at the end of the class:

```scala
  /** Drops every Raid's consequences: the seam Keep relies on. */
  private object DropsRaidConsequences extends ContributingPower:
    val id: PowerId = PowerId("test.drops-raid-consequences")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignRaidConsequences ->
        Vector(Transform((_, _) => Vector.empty)))

  test("a power can drop the Raid's consequences while its takes stay, " +
      "and the relocation is still asked"):
    val RaidBoard(b, relic) = raidBoard()
    val walked = walk(b, relic, attack = 4,
      rulesFor = rulesWith(Vector(DropsRaidConsequences), _))
    val sacrificed = walked.sacrificed
    val after = ready(sacrificed.state)
    val rival = Look(sacrificed.state).player(b.rival)
    // The takes stand.
    assert(Look(sacrificed.state).player(b.actor).relics
      .exists(_.id == relic))
    assertEquals(BannerRules.holder(after.game.current, Banner.PeoplesFavor),
      Some(b.actor))
    // No favor is burnt, and no facedown card is discarded or set aside.
    assertEquals(rival.board.favor, 5)
    assertEquals(rival.advisers.size, 3)
    assertEquals(rival.relics.map(_.id),
      Vector(RelicId("raid-facedown-relic")))
    assertEquals(after.game.current.setAsideRelics, Vector.empty)
    new ParkedDecisionAssertions(catalog,
      WalkerPowers(Vector(DropsRaidConsequences))).assertParked(
        sacrificed.state, ActionRef.Campaign, CampaignIds.relocation, b.actor)
```

Add the imports:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.walker.{ParkedDecisionAssertions,
  ProcedureWalker, WalkerDice, WalkerPowers}
```

The second line replaces the existing `oathdigital.gameplay.walker` import.

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignRaidSuite"`
Expected: FAIL to compile: `CampaignRaidConsequences` is not a member of `PowerWindow`.

- [ ] **Step 3: Add the window.** In `PowerWindow.scala`, after `CampaignRaidTransfer`:

```scala
  case object CampaignRaidConsequences extends CampaignWindow:
    val key = "campaign.raid-consequences"
```

In `BattlePlan.scala`, add it to `afterOutcome`, after `PowerWindow.CampaignRaidTransfer`. Its result is this Campaign's, as the transfer's is.

- [ ] **Step 4: Split the transfer.** In `CampaignRaid.scala`:

  1. In `steps`, replace the transfer's `Sequence` with:

```scala
      Sequence(Vector[Operation](
        BuildOps((state, _) => takes(state, result)),
        Sequence(Vector[Operation](
          BuildOps((state, _) => consequences(state, result))),
          Some(PowerWindow.CampaignRaidConsequences))),
        Some(PowerWindow.CampaignRaidTransfer)),
```

  2. Replace `transfer` with three functions:
     - a private `defeated`, which holds the `for` comprehension's checks;
     - `takes`, with the relic and banner code;
     - `consequences`, with the rest.

     Move the code; do not rewrite it:

```scala
  /** The Raid's defender and the region its facedown advisers are
    * discarded to, the one after the origin's, from live state. */
  private def defeated(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, (PlayerState, Region)] =
    val current = ready.game.current
    for
      defenderId <- result.defender match
        case CampaignDefender.Player(player) => Right(player)
        case CampaignDefender.Bandits => Left(OathViolation.InvalidEventOrder(
          "a Raid requires a player defender"))
      defender <- current.players.find(_.player == defenderId).toRight(
        OathViolation.InvalidEventOrder("the Raid's defender is not in the game"))
      origin <- CampaignSetup.originOf(ready, result.attacker).toRight(
        OathViolation.PawnSiteMissing(result.attacker))
      region <- current.map.regionOf(origin).toRight(
        OathViolation.InvalidEventOrder("the Raid's origin has no region"))
    yield (defender, next(region))

  /** What the attacker targeted, taken: the relics, then the banners. A
    * banner's favor and secrets leave with it. */
  def takes(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    defeated(ready, result).map { (defender, _) =>
      val current = ready.game.current
      val defenderId = defender.player
      val attacker = result.attacker
      val relics = result.raidTargets.collect {
        case CampaignRaidTarget.Relic(_, id) => id }
      val banners = result.raidTargets.collect {
        case CampaignRaidTarget.Banner(_, banner) => banner }
      val relicTakes: Vector[CoreOperation] = relics.map(id => Take(
        Piece.Card(id), attacker, Location.PlayArea(defenderId),
        Location.PlayArea(attacker)))
      // A banner's favor and secrets leave as its Take's children, so a
      // refused Take leaves the banner whole.
      val bannerOps: Vector[CoreOperation] = banners.map { banner =>
        val leaving: Vector[CoreOperation] = banner match
          case Banner.PeoplesFavor =>
            val returned = BannerRules.raidFavorReturn(ready.banks.favor,
              BannerRules.resources(current, banner)).groupBy(identity)
              .view.mapValues(_.size).toMap
            Suit.all.flatMap(suit => returned.get(suit).filter(_ > 0).map(count =>
              Move(Piece.Favor(count),
                PositionedLocation(Location.OnBanner(banner)),
                PositionedLocation(Location.FavorBank(suit))): CoreOperation))
          case Banner.DarkestSecret =>
            val secrets = BannerRules.resources(current, banner)
            Option.when(secrets > 0)(Burn.secrets(secrets,
              PositionedLocation(Location.OnBanner(banner)))).toVector
        Take(Piece.Banner(banner), attacker, Location.PlayArea(defenderId),
          Location.PlayArea(attacker), leaving = leaving)
      }
      relicTakes ++ bannerOps
    }

  /** What the defeat costs the defender beyond the takes, in the printed
    * order: the facedown advisers discarded, the facedown relics set
    * aside, half the favor burnt, and a facedown Conspiracy returned to
    * the box. Read after the takes, which change none of it. */
  def consequences(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    defeated(ready, result).map { (defender, discardRegion) =>
      val defenderId = defender.player
      val facedown: Vector[WorldCardId] = defender.advisers.collect:
        case card: DenizenState if card.orientation == Orientation.FaceDown =>
          card.id: WorldCardId
        case vision: VisionState if vision.orientation == Orientation.FaceDown =>
          vision.id: WorldCardId
      val discards: Vector[CoreOperation] = facedown
        .filterNot(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.RegionalDiscard(discardRegion),
            StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)))
      val boxed: Vector[CoreOperation] = facedown
        .filter(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SharedBank)))
      val setAside: Vector[CoreOperation] = defender.relics
        .filter(_.orientation == Orientation.FaceDown).map(relic => Move(
          Piece.Card(relic.id), PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SetAsideRelics)))
      val burn: Vector[CoreOperation] = Option.when(defender.board.favor / 2 > 0)(
        Burn.favor(defender.board.favor / 2,
          PositionedLocation(Location.PlayArea(defenderId)))).toVector
      discards ++ setAside ++ burn ++ boxed
    }
```

  This is `transfer`'s code, moved, with `RegionalDiscard(discardRegion)` in place of `RegionalDiscard(next(region))`. Drop `transfer` and its doc comment. Re-wrap any moved line that this nesting pushes past 80 characters. The two batches together hold the same operations, in the same order, as the one batch did.

- [ ] **Step 5: Run the Raid suites.**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with 1 new test and no existing test changed.

- [ ] **Step 6: Run the Game Log suites.** The transfer now journals two steps, and the closing line reads every step of the run.

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, with no test changed.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala
git commit -m "feat(batch8): a Raid's consequences run in their own window"
```

---

### Task 3: Keep

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Keep.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/KeepSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `PowerWindow.CampaignRaidConsequences` (Task 2).
- Produces: `Keep`, `Keep.ended`, `Keep.kept`. Task 4 reads Keep's line in the Game Log.

- [ ] **Step 1: Move the card.** Cut `KeepCard` from `HoldingOrderDenizens.scala` into a new `campaign/Keep.scala`, unchanged:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.model._

object KeepCard extends Denizen(DenizenId("5"), "Keep", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.keep"),
    persistent = false, cost = Cost.free,
    text = "If defeated in a raid, after the attacker takes what they " +
      "targeted, place your pawn at this site and end the action. " +
      "_Do not burn favor, move pawn, etc._")
  val powers: Vector[PrintedPower] = Vector(power)
```

In `NewFoundations.scala`, add `KeepCard` to the `oathdigital.gameplay.powers.campaign` import list. The `holding._` wildcard no longer supplies it. `HoldingOrderDenizens.scala` still uses `SiteOnly`, for City Wall and Martial Culture.

- [ ] **Step 2: Write the failing tests.** Create `KeepSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

/** Keep: a defender who rules Keep's site and loses a Raid gives up only
  * what was targeted, and their pawn goes to Keep's site. */
class KeepSuite extends munit.FunSuite:
  private val keep = CatalogNames.denizen("Keep")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")

  /** The Raid board (the rival at the origin with Oracular Pig, a facedown
    * relic, three facedown advisers, both banners, 5 favor and 3 board
    * warbands), with Keep faceup at `site`, where `ruler` holds 2
    * warbands. */
  private def raidBoardWithKeepAt(site: Board => SiteId,
      ruler: Board => PlayerId): RaidBoard =
    val raid = raidBoard()
    val b = raid.board
    RaidBoard(on(b)(_.denizen(keep, at = site(b))
      .warbandsAt(site(b), ruler(b), 2)), raid.relic)

  /** A Raid on the rival's relic and People's Favor, committing the
    * actor's 4 warbands, played until it ends or asks something past the
    * plan windows. */
  private def raidOnRelicAndPeoplesFavor(b: Board, relic: RelicId,
      game: OathRules = rules(winningDice)): Run =
    commit(game, b, 4, targets = Vector(DecisionOptionRef.Relic(relic),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)), raid = true)
      .finishPlanWindows

  private def keepLine(defender: PlayerId, site: SiteId, covers: Boolean)
      : NoteText.Said = NoteText.Said("ended",
    s"Ended the Raid with ${defender.value}'s pawn at ${site.value}.",
    covers)

  test("a defeated defender who rules Keep's site loses only what was " +
      "targeted, and their pawn goes to Keep's site"):
    val raid = raidBoardWithKeepAt(_ => brokenPeaks, _.rival)
    val b = raid.board
    val done = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assertEquals(done.winner, Some(true))
    val rival = Look(done.state).player(b.rival)
    // The takes stand, and the banner's favor goes back to the banks.
    assert(Look(done.state).player(b.actor).relics
      .exists(_.id == raid.relic))
    val after = ready(done.state).game.current
    assertEquals(BannerRules.holder(after, Banner.PeoplesFavor),
      Some(b.actor))
    assertEquals(after.banners.peoplesFavor.favor, 0)
    // No favor is burnt, and no facedown card is discarded or set aside.
    assertEquals(rival.board.favor, 5)
    assertEquals(rival.advisers.size, 3)
    assertEquals(rival.relics.map(_.id),
      Vector(RelicId("raid-facedown-relic")))
    // The battle's losses stand: half of the 3 defending warbands.
    assertEquals(rival.board.warbands, 3 - 3 / 2)
    assertEquals(rival.pawnSite, Some(brokenPeaks))
    // Nothing more is asked: the Campaign is over.
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(said(Keep, done.events),
      Vector(keepLine(b.rival, brokenPeaks, covers = true)))
    assertEquals(ActPowerHarness.replayed(done.game, b.ready, done.events),
      ready(done.state))
    assertWireRoundTrips(done.events)

  test("with Keep at the Raid's own site, the defender's pawn stays " +
      "there"):
    val raid = raidBoardWithKeepAt(_.origin, _.rival)
    val b = raid.board
    val done = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.rival).pawnSite, Some(b.origin))
    assertEquals(Look(done.state).player(b.rival).board.favor, 5)
    val pawnMoves = done.ops.collect {
      case move @ Move(Piece.Pawn(_), _, _, _) => move }
    assertEquals(pawnMoves, Vector.empty)
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(said(Keep, done.events),
      Vector(keepLine(b.rival, b.origin, covers = false)))

  test("Keep at a site another player rules changes nothing"):
    val raid = raidBoardWithKeepAt(_ => brokenPeaks, _.bystander)
    val b = raid.board
    val run = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assert(awaits(run, b.actor, CampaignIds.relocation))
    assertEquals(Look(run.state).player(b.rival).board.favor, 5 - 5 / 2)
    assertEquals(Look(run.state).player(b.rival).advisers, Vector.empty)
    assertEquals(said(Keep, run.events), Vector.empty)

  test("the effects at the Campaign's end still run: Book Burning still " +
      "burns the defender's secrets"):
    val bookBurning = CatalogNames.denizen("Book Burning")
    val raid = raidBoardWithKeepAt(_ => brokenPeaks, _.rival)
    val b = withAdviser(on(raid.board)(_.secrets(raid.board.rival,
      faceUp = 2, faceDown = 2)), bookBurning, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(bookBurning))
      .finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.rival).pawnSite, Some(brokenPeaks))
    assertEquals(Look(done.state).player(b.rival).board.favor, 5)
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
```

Notes on the fixtures:
- **Defense.** `winningDice` makes every defense die blank, so the defense is the rival's 3 board warbands. The actor's 4 swords win.
- **Rule of the origin.** The rival's 2 warbands at the origin, in the second test, replace the Bandits there. They do not defend a Raid, which counts only board warbands.
- **The Book Burning test** targets only the relic: Book Burning's plan does not change the defense, and 4 swords still beat 3.

If a fixture or Table step has a different name or signature, use what is there and say so in the report.

Add to `PowerKindsCatalogSuite.scala`, after the Hospitality test:

```scala
  test("Keep's rule is automatic whatever its flag: it is a must, not a " +
      "modifier"):
    assertEquals(flag("denizen.keep"), Some(false))
    assertEquals(oathdigital.gameplay.powers.campaign.Keep.resolution,
      PowerResolution.Automatic)
```

- [ ] **Step 3: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.KeepSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: FAIL to compile: `Keep` is not found.

- [ ] **Step 4: Write the power.** Append to `Keep.scala`, and extend its imports to:

```scala
import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._
```

```scala
/** Keep (card 5, site-only), a rule of a faceup site card: "If defeated in
  * a raid, after the attacker takes what they targeted, place your pawn at
  * this site and end the action. Do not burn favor, move pawn, etc."
  *
  * It applies when the Raid's defeated defender rules Keep's site once the
  * battle's losses are made. The takes stand. The Raid's consequences (the
  * facedown discards, the set-aside relics, the half-favor burn) are
  * dropped, and the relocation question is replaced: the defender's pawn
  * moves to Keep's site, or stays when it is already there. Nothing else in
  * the Raid follows; the effects at the Campaign's end still run.
  *
  * The catalog marks the power `persistent: false`, but it is not a choice,
  * so it keeps the default automatic resolution. Its line, "Ended the Raid
  * with {Blue}'s pawn at {site}.", restates a pawn move and covers it, so
  * the Campaign's closing line does not say the pawn was sent there.
  */
case object Keep extends ContributingPower:
  val cardId: DenizenId = KeepCard.id
  val id: PowerId = KeepCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** "Ended the Raid with {Blue}'s pawn at {site}." */
  val ended: NoteKey = NoteKey("ended", Vector(
    NotePart.Text("Ended the Raid with "), NotePart.Arg(0),
    NotePart.Text("'s pawn at "), NotePart.Arg(1), NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = Vector(ended)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignRaidConsequences ->
      Vector(Transform((_, _) => Vector.empty)),
    PowerWindow.CampaignRaidRelocation -> Vector(Transform((ctx, _) =>
      kept(ctx.state).toVector.map((defender, site) =>
        placed(defender, site)))))

  override def applicable(ctx: PowerCtx): Boolean = kept(ctx.state).nonEmpty

  /** The Raid's defeated defender and Keep's site, when that defender rules
    * it. Both windows Keep folds come only after a Raid's victory, so the
    * recorded result is this Raid's. */
  def kept(ready: ReadyGame): Option[(PlayerId, SiteId)] = for
    result <- ready.game.current.lastCampaignResult
    defender <- Some(result.defender).collect {
      case CampaignDefender.Player(player) => player }
    site <- SiteRulers.siteOf(ready, cardId)
    if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(defender))
  yield (defender, site)

  /** The defender's pawn placed at `site`, unless it stands there, then the
    * line. The line covers the move it restates. */
  private def placed(defender: PlayerId, site: SiteId): Operation =
    Branch((ready, _) => {
      val from = ready.game.current.players.find(_.player == defender)
        .flatMap(_.pawnSite).filter(_ != site)
      from.fold(Vector[Operation](line(defender, site, covers = false)))(
        origin => Vector[Operation](
          BuildOps((_, _) => Right(Vector[CoreOperation](Move(
            Piece.Pawn(defender), PositionedLocation(Location.Site(origin)),
            PositionedLocation(Location.Site(site)))))),
          line(defender, site, covers = true)))
    })

  private def line(defender: PlayerId, site: SiteId, covers: Boolean)
      : Note = Note(id, _ => Some(ended(PowerSourceRef.Card(cardId),
    NoteArg.Player(defender), NoteArg.Site(site))), covers)
```

Register it in `PlanRules.forCatalog`: append `++ Vector(Keep)` after the last entry. Also add "and Keep (a Raid's defeat replaced)" to the list in the doc comment.

- [ ] **Step 5: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CampaignRaidSuite"`
Expected: PASS, with 5 new tests.

- [ ] **Step 6: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/Keep.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/KeepSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Keep spares a defeated defender who rules its site"
```

---

### Task 4: Keep in the Game Log

**Files:**
- Create: `src/test/scala/oathdigital/application/gamelog/KeepLogSuite.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`

**Interfaces:**
- Consumes: `Keep` and its line (Task 3).

- [ ] **Step 1: Write the failing log suite.** In `LogScripts.scala`, drop `private` from `raidAnswers` if slice 3 has not. Then create `KeepLogSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, raidAnswers, texts}

/** How Keep reads in the Game Log: its line says where the defender's
  * pawn ended up, and the closing line neither sends the pawn there nor
  * reports a burn or a discard. */
class KeepLogSuite extends munit.FunSuite:
  private val keep = CatalogNames.denizen("Keep")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")

  /** p1 raids p2 at Ancient City and wins. p2 holds a facedown Birdsong,
    * and Keep stands at `keepSite`, where p2 holds 2 warbands. The log as
    * p3 sees it. */
  private def raidOnKeepsRuler(keepSite: SiteId): Vector[String] =
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, ancientCity)
      .pawn(p3, "Buried Giant").adviser(p2, "Birdsong", facedown = true)
      .denizen(keep, at = keepSite).warbandsAt(keepSite, p2, 2)
      .banditsRefilled
    val (service, act) = atTable("keep", table, Dice.raid)
    raidAnswers(act).after(MenuPlay.start(p1, ActionRef.Campaign))
    texts(format(Script("keep", service, p1), Some(p3)))

  test("its line names where the pawn went, and the closing line neither " +
      "sends it there nor discards"):
    val log = raidOnKeepsRuler(brokenPeaks)
    assert(log.contains(s"Keep: Ended the Raid with ${playerLabel(p2)}'s " +
      "pawn at Broken Peaks."), log.mkString("\n"))
    assert(!log.exists(_.contains(" was sent to ")), log.mkString("\n"))
    assert(!log.exists(_.contains("discarded")), log.mkString("\n"))

  test("with Keep at the Raid's own site, its line says the pawn is " +
      "there"):
    val log = raidOnKeepsRuler(ancientCity)
    assert(log.contains(s"Keep: Ended the Raid with ${playerLabel(p2)}'s " +
      "pawn at Ancient City."), log.mkString("\n"))
    assert(!log.exists(_.contains(" was sent to ")), log.mkString("\n"))
```

In the second test p2 rules Ancient City, so p1 may conquer it or raid p2. `raidAnswers` chooses the Raid. If the Raid script asks something `raidAnswers` does not answer, add that answer and say so in the report.

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.application.gamelog.KeepLogSuite"`
Expected: the second test passes, because nothing moves. The first fails on " was sent to ": the closing line still reports Keep's move as a relocation.

- [ ] **Step 3: Leave restated steps out of the closing summary.** In `CampaignLines.closing`, the `WalkerCompleted` case reads every step of the run through `journal.runOps`. Keep the index of each step, so a step a covering note restates can be left out, and summarise the rest:

```scala
    case _: WalkerCompleted =>
      val stepped = journal.runOps(run, at)
      val ops = stepped.map(_._2)
      // A power's covering line already restates these steps.
      val restated = stepped.collect {
        case (index, step) if journal.covered(index) => step }
      val outcome = ops.dropWhile {
        case OpStep(_: RecordCampaignResult, _, _) => false
        case _ => true
      }
      val told = outcome.drop(1).filterNot(step => restated.exists(_ eq step))
```

Then pass `told` wherever the case passes `outcome.tail` to `gains` and `losses`. Keep the rest of the case as it is on `main`; after slice 3, that includes the `ended` fallback for a run with no result. Update the doc comment on `closing`: "At completion: what the winner gained, then what the loser lost that no earlier line says, a power's covering line included."

- [ ] **Step 4: Run the log suites.**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, with 2 new tests and no existing test changed.

- [ ] **Step 5: Commit.**

```bash
git add src/main/scala/oathdigital/application/gamelog/CampaignLines.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/KeepLogSuite.scala
git commit -m "feat(batch8): Keep's line replaces the Raid's relocation in the log"
```

Leave `LogScripts.scala` out of the commit if it did not change.

---

### Task 5: Close the slice

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the gates**, in order:

```bash
./sbtw "test" "frontend/test"
```

```bash
./sbtw frontend/fullOptJS
```

```bash
python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py
```

Expected: all pass. The server count is the baseline plus 8; the frontend count is unchanged.

- [ ] **Step 2: Check the client.** Search the frontend and `GamePresentationProjector` for `raid-transfer`, `raid-relocation` and any code that expects a won Raid to ask the relocation question. Fix only what breaks, and report what you checked.

- [ ] **Step 3: Update the docs.**
  - In the design's Status, "Slices 1 to 3 are done." becomes "Slices 1 to 4 are done."
  - In `docs/ROADMAP.md`, check `- [ ] **4. A Raid's defeat replaced:** Keep.` and change the Now line to "**Phase - Catalog batch 8** is under way: slices 1 to 4 are done, and slice 5 is next."
  - If slice 3 has not merged, write what is true on `main` instead.

- [ ] **Step 4: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 4 done"
```

- [ ] **Step 5: Report:**
  - the baseline and final test counts;
  - any fixture, card or site you swapped;
  - whether any existing test needed a change after the split, and why;
  - what the client check found.
