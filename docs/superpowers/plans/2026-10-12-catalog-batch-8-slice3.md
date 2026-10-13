# Catalog Batch 8, Slice 3 (Warbands at a Campaign's Start) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Vow of Union (185), both halves: warbands moved at the start of any Campaign, and Beast advisers only. Also add the two ways that lets a Campaign end early: nothing left to attack, and the attacker's Skip.

**Architecture:**

- **The Campaign half** is a `ContributingPower`, `VowOfUnion`. A `Transform` at `CampaignBeforeTargets` puts one `Branch` ahead of the Campaign's kind, defender and target steps. The `Branch` finds the holder and asks two questions, both owned by the holder and named under `CampaignProcedure.decisionPrefix`:
  - a `ChooseOne` of the direction;
  - then a `Distribute` in that direction.

  A `BuildOps` then makes the moves, and a `Note` writes the line. Herald already asks an off-turn holder inside a Campaign, so the walker needs nothing more for the question.
- **A one-way warband arrangement.** `WarbandArrangement.oneWay` builds the `Distribute` for one direction. Its slot bounds enforce the direction: in "to your board", each site may only lose warbands, and in "to your sites", each site may only gain them. Any number may move, including none, and a site may empty. The existing `WarbandArrangement.moves` turns the answer into moves.
- **An end to a walk.** A new control node, `EndWalk`, finishes the procedure where it is reached. Nothing after it in the tree runs. It changes no state and is never journaled; the walk completes as usual with `WalkerCompleted`. The walker gains one `Step`, `Ended`.
- **A Campaign that ends early.** `CampaignProcedure` gains one step, `endStep`, as the last child of the before-targets section. It runs `EndWalk` when no Campaign kind is settled. That happens in two cases: no kind is legal any more, or the attacker answered "Skip Campaign".
  - Because the step is appended at the end of that section, no later node moves. Saved games keep their tree addresses, and Careful Plans' reordering, which finds the battle steps by window, is untouched.
  - The force question, the dice, the result record, the outcome and every after-Campaign step are skipped. So no stale `lastCampaignResult` is read.
- **Skip Campaign.** A power at the Campaign's start may offer the attacker a Skip by recording the marker pool `CampaignIds.skipOffered`, as bandit plans record `CampaignPlans.appliedMarker`. The kind question then asks even when only one kind is legal, and adds "Skip Campaign" last. Vow of Union records the marker when it moved at least one warband. The engine names no power.
- **The restriction half shares Family Wagon's code.** A new trait, `SuitOnlyAdvisers`, takes Family Wagon's bury rules and leaves the suit as a parameter. Family Wagon extends it with Nomad and keeps its limit lift. The new `VowOfUnionAdvisers` extends it with Beast and does not lift the limit.
- **The force question needs no change.** `CampaignProcedure` builds the force `Decide` from the state the tree is built with. The walker rebuilds the tree from live state on every command, and the parked query is rebuilt too. So the attacker's force question offers warbands that Vow of Union moved to their board. A test pins this.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "Goal and scope", "The bar" (the warband arrangement bullet), "Catalog and roadmap corrections" (the Vow of Union bullet), "Slice 3: Warbands at a Campaign's start", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 3 entry.

Task 1 amends both with this plan's rulings and adds the end of a walk and the Skip to "The bar". Read them again after Task 1.

**Starts from:** `main` at `a2a2bcba` or later. Slice 2 is merged.

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
- **Tree addresses.** A parked game is stored by the address of its node in the procedure tree. Add nodes to the Campaign tree only where this plan says: one step appended at the end of the before-targets section. Do not wrap or insert anything elsewhere in it.
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
  - `oathdigital.model.EndWalk` (case object, in `model/Operation.scala`), and the walker step `Ended(ctx: WalkCtx)` in `walker/WalkState.scala`.
  - `CampaignIds.skip: DecisionOptionRef.Button` (`Button("skip")`) and `CampaignIds.skipOffered: PoolKey` (`"campaign.skip-offered"`).
  - `CampaignAnswers.skipped(pending): Boolean` and `CampaignSetup.skipOffered(ready): Boolean`.
  - `oathdigital.gameplay.powers.cardplay.SuitOnlyAdvisers` (trait and companion object).
  - `oathdigital.gameplay.powers.cardplay.VowOfUnionAdvisers` (final case class with `forCatalog`). Power id `denizen.vow-of-union.restriction`.
  - `oathdigital.gameplay.powers.campaign.VowOfUnion` (case object) and `VowOfUnionCard`, in `campaign/VowOfUnion.scala`. Power id `denizen.vow-of-union.campaign`.
  - Decisions `campaign.vow-of-union.direction` (`VowOfUnion.directionDecisionId`) and `campaign.vow-of-union.arrange` (`VowOfUnion.arrangeDecisionId`).
  - `WarbandArrangement.oneWay`.
- **Texts, exactly:**
  - The kind question's extra last option: "Skip Campaign".
  - Log lines of a Campaign that ends early: "Skipped the Campaign." and "Nothing left to attack: the Campaign ended."
  - Direction heading: "Vow of Union: move warbands between your board and the sites you rule in your region".
  - Direction options, in this order: `Button("to-board")` "Move warbands to your board", `Button("to-sites")` "Move warbands to your sites", `Button("stay")` "Move none".
  - Reason: "No warbands on your board" (to your sites).
  - Arrangement headings: "Vow of Union: move warbands from the sites you rule in your region to your board" and "Vow of Union: move warbands from your board to the sites you rule in your region". Confirm label "Move warbands".
  - Lines on the holder's own turn: "Moved 2 warbands from sites to the board." and "Moved 3 warbands from the board to sites."
  - Lines off turn: "{Blue} moved 2 warbands from sites to their board." and "{Blue} moved 3 warbands from their board to sites."
  - The bury note on a card play: "Vow of Union will bury it". Bury lines as Family Wagon's: "Buried {cards}." and "{Blue} buried {cards}."
  - Neither Vow of Union question posts a "Chose" line, and the Skip answer posts none either.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 15 server tests and no frontend test.
  - It changes one existing test's data: `PowerKindsCatalogSuite`'s persistent rules gain Vow of Union's two power ids.
  - Family Wagon's two suites and every existing Campaign suite must pass unchanged.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviewed rulings 5 and 6 on 2026-10-12; the rest are for review with the plan. Task 1 writes all of them into the spec.

1. **When it asks.** The questions come after the Campaign's Supply is spent and before the attacker chooses a kind, a defender or targets.
2. **Two questions.** First a direction: "Move warbands to your board", "Move warbands to your sites" or "Move none". Then one distribution in that direction. Each slot opens on what it holds now, so confirming it unchanged moves nothing.
3. **"Your region"** is the region of the holder's pawn when the Campaign starts.
4. **When nothing is asked.** If the holder rules no site in their region, nothing is asked. "Move warbands to your sites" with an empty board is shown unavailable: "No warbands on your board".
5. **A site may empty, and then the Campaign may end** (product owner). Emptying a site of the holder's warbands may lose its rule. If no Campaign kind is legal afterwards, the Campaign ends at once:
   - the Supply stays spent and the major action is used;
   - there is no battle, no result and no after-Campaign effect;
   - the log says "Nothing left to attack: the Campaign ended."
6. **Skip Campaign** (product owner). When Vow of Union moved at least one warband, in either direction and whoever holds it, the attacker's kind question is asked even with one legal kind and adds "Skip Campaign" last. Skip ends the Campaign as ruling 5 does, and the log says "Skipped the Campaign." So the attacker is never forced into a target they did not intend.
   - Nothing moved ("Move none", or amounts confirmed unchanged): no Skip.
   - No kind legal: ruling 5 ends the Campaign without asking.
7. **Lines.** One line when warbands moved, naming the count and the direction. It names the holder only off turn. Nothing moved, no line. Neither question posts a "Chose" line. The generic move lines stay and say which sites.
8. **The restriction half is Family Wagon's code, with Beasts and no limit lift.** It buries every adviser that is not a faceup Beast when Vow of Union arrives and whenever its holder gains one. A card play it would bury carries "Vow of Union will bury it". The holder's adviser limit is unchanged.
9. **The force question reads the moved warbands.** An attacker who holds Vow of Union may move warbands to their board and commit them in the same Campaign.

## File Structure

- **Create:**
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala`: the shared bury rule.
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala`: the restriction half.
  - `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala`: the card (Task 2), then the Campaign half (Task 4).
  - `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisersSuite.scala`
  - `src/test/scala/oathdigital/gameplay/EndWalkSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/campaign/CampaignSkipSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala`
  - `src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala`
- **Modify:**
  - `src/main/scala/oathdigital/model/Operation.scala`: `EndWalk`.
  - `src/main/scala/oathdigital/gameplay/walker/WalkState.scala` and `ProcedureWalker.scala`: the `Ended` step.
  - `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala` (`CampaignIds`, `CampaignSetup.kindOf`, `CampaignAnswers`) and `CampaignProcedure.scala`: the Skip and the early end.
  - `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala` and, if needed, `StartLines.scala`: the early-end lines.
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala`: extends the shared trait.
  - `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`: `oneWay`.
  - `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`: registers the restriction half.
  - `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`: registers the Campaign half.
  - `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`: the card moves out.
  - `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports.
  - `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`: two ids.
  - `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`: `raidAnswers` becomes public.
  - The design, the rulings appendix and `docs/ROADMAP.md`.

---

### Task 1: Record the plan-time rulings

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", "Slice 3: Warbands at a Campaign's start", "Seams used for the first time")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` ("Slice 3: Warbands at a Campaign's start")

- [ ] **Step 1: Extend "The bar".** After the warband arrangement bullet, add:

```markdown
- **An end to a walk** (slice 3, approved 2026-10-12). `EndWalk` is a
  control node: the procedure finishes where it is reached, and nothing
  after it runs. It changes no state and is never journaled.
- **A Campaign that ends early** (slice 3, approved 2026-10-12). The last
  step before the Campaign's targets ends it when no kind is settled: no
  kind is legal any more, or the attacker chose "Skip Campaign". A power at
  the Campaign's start offers the Skip by recording the marker pool
  `CampaignIds.skipOffered`; the kind question is then asked even with one
  legal kind. The Supply stays spent.
```

- [ ] **Step 2: Add the design's plan-time rulings.** Under "## Slice 3: Warbands at a Campaign's start", after its paragraph, add:

```markdown
### Plan-time rulings

- **When it asks.** After the Campaign's Supply is spent, before the kind,
  the defender and the targets.
- **Two questions.** A direction ("Move warbands to your board", "Move
  warbands to your sites" or "Move none"), then one distribution in that
  direction. Its slot bounds enforce the direction, and each slot opens on
  what it holds now.
- **"Your region"** is the region of the holder's pawn when the Campaign
  starts.
- **When nothing is asked.** Nothing is asked when the holder rules no site
  in their region. "Move warbands to your sites" with an empty board is
  shown unavailable: "No warbands on your board".
- **A site may empty** (product owner). If no Campaign kind is legal
  afterwards, the Campaign ends: the Supply stays spent, and there is no
  battle, result or after-Campaign effect. The log says "Nothing left to
  attack: the Campaign ended."
- **Skip Campaign** (product owner). When Vow of Union moved at least one
  warband, the attacker's kind question is asked even with one legal kind
  and adds "Skip Campaign" last, which ends the Campaign the same way. The
  log says "Skipped the Campaign." Nothing moved, no Skip.
- **Lines.** "Moved 2 warbands from sites to the board." or "Moved 3
  warbands from the board to sites." on the holder's turn; "{Blue} moved 2
  warbands from sites to their board." or "{Blue} moved 3 warbands from
  their board to sites." off turn. Nothing moved, no line. Neither question
  posts a "Chose" line.
- **The restriction half** is Family Wagon's code with Beasts and no limit
  lift, shared through `SuitOnlyAdvisers`. A play it would bury carries
  "Vow of Union will bury it".
- **The force question reads the moved warbands.** The walker rebuilds the
  Campaign from live state on every command, so an attacker may move
  warbands to their board and commit them.
```

- [ ] **Step 3: Add the seams.** In "Seams used for the first time", after the Vow of Union bullet, add:

```markdown
- A walk ended early, and a Campaign skipped or left with nothing to attack
  (Vow of Union).
```

- [ ] **Step 4: Extend the rulings appendix.** Replace the slice 3 section's entry with:

```markdown
- **185 Vow of Union.** "At the start of any campaign, you may move any
  number of warbands from sites you rule in your region to your board or
  vice versa." and "You may only have [suit-beast] advisers—bury other
  advisers you have or would gain."
  - It fires at the start of every Campaign, the holder's or another
    player's, and asks the holder: after the Supply is spent, before the
    kind and the targets (plan-time ruling).
  - The sites are those the holder rules in the region of their pawn when
    the Campaign starts.
  - One rearrangement in one direction: from those sites to the board, or
    from the board to those sites. Any number may move, including none. It
    asks the direction ("Move warbands to your board", "Move warbands to
    your sites", "Move none"), then the amounts (plan-time ruling).
  - Emptying a site of the holder's warbands may lose its rule. That is
    allowed. If no Campaign kind is legal afterwards, the Campaign ends with
    its Supply spent: "Nothing left to attack: the Campaign ended." (product
    owner).
  - When it moved at least one warband, the attacker may answer the kind
    question with "Skip Campaign", which ends the Campaign the same way:
    "Skipped the Campaign." (product owner).
  - Lines: "Vow of Union: Moved 2 warbands from sites to the board." on the
    holder's turn, "Vow of Union: {Blue} moved 2 warbands from sites to
    their board." off turn, and the same for the other direction (plan-time
    ruling).
  - The restriction half buries every non-Beast adviser when the card
    arrives, and any non-Beast adviser its holder would gain, as Family
    Wagon does. A play it would bury carries "Vow of Union will bury it".
    It lifts no limit.
```

- [ ] **Step 5: Check links and commit.**

Run: `python3 scripts/check-markdown-links.py`
Expected: no broken link.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 3 plan-time rulings"
```

---

### Task 2: Beast advisers only

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala` (the card only)
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisersSuite.scala`, `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Produces: `trait SuitOnlyAdvisers extends ContributingPower`, with abstract `catalog: ExecutableCatalog`, `cardId: DenizenId`, `suit: Suit`, `buryWarning: String` and an overridable `playRules(rules: CardPlayRules): CardPlayRules`. `SuitOnlyAdvisers.hasFaceUp(ready, player, card): Boolean`. `VowOfUnionAdvisers.forCatalog(catalog)`, `VowOfUnionAdvisers.id`, `VowOfUnionAdvisers.buryWarning`. `VowOfUnionCard` in `oathdigital.gameplay.powers.campaign`.

- [ ] **Step 1: Write the failing suite.** Create `VowOfUnionAdvisersSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText}
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, FaithfulFriend}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Vow of Union's restriction half through card play: under a faceup Vow
  * of Union the arrival pass buries every adviser that is not a faceup
  * Beast, as Family Wagon does with Nomads, and the adviser limit stays. */
class VowOfUnionAdvisersSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park, placementOptions}

  private val actor = Table.p1
  private val vow = CatalogNames.denizen("Vow of Union")
  private val power = VowOfUnionAdvisers.forCatalog(catalog)
  // Beasts with no WHEN PLAYED.
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  private val secondChance = CatalogNames.denizen("Second Chance")
  /** Discord, locked, adviser-only. WHEN PLAYED: gain four warbands. */
  private val smallFavor = CatalogNames.denizen("A Small Favor")
  /** Nomad, locked, adviser-only. WHEN PLAYED: gain 4 Supply. */
  private val friend = CatalogNames.denizen("Faithful Friend")
  private val powers =
    WalkerPowers(Vector(power, ASmallFavor, FaithfulFriend))
  private val buryNote = OptionReason.Written(VowOfUnionAdvisers.buryWarning)

  /** p1's Act at Ancient City with 2 Supply and 3 warbands on the board,
    * `card` in hand, and the advisers `held` adds. */
  private def actWith(card: DenizenId)(held: Table => Table): ReadyGame =
    held(Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .supply(actor, 2).boardWarbands(actor, 3).hand(actor, card)).ready

  /** `card` played from hand to p1's adviser slot `index`, under
    * `powers`. */
  private def playedToAdvisers(ready: ReadyGame, card: DenizenId,
      index: Int, faceup: Boolean): WalkerOutcome.Finished =
    val tree = build(ready, actor, card)
    Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.placementDecisionId(card),
      DecisionOptionRef.AdviserPlay(actor, index, faceup), actor))

  private def vowLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfUnionAdvisers.id, power.noteKeys, events)

  /** The line on the holder's turn, naming `cards`. */
  private def buriedLine(cards: CardId*): Vector[NoteText.Said] =
    Vector(NoteText.Said("buried",
      s"Buried ${cards.map(_.value).mkString(", ")}.", covers = true))

  private def worldDeck(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck

  test("a non-Beast played faceup is buried at once, and its WHEN PLAYED " +
      "does not run; under a facedown Vow it stays"):
    val ready = actWith(smallFavor)(_.adviser(actor, vow)
      .adviser(actor, faeMerchant))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](vow, faeMerchant))
    assertEquals(worldDeck(done.treeless).lastOption, Some(smallFavor))
    // It was never gained, so A Small Favor's four warbands never came.
    assertEquals(Look(done.treeless).boardWarbands(actor), 3)
    assertEquals(vowLines(done.events), buriedLine(smallFavor))
    assertWireRoundTrips(done.events)
    val hidden = actWith(smallFavor)(_.adviser(actor, vow, facedown = true)
      .adviser(actor, faeMerchant))
    val kept = playedToAdvisers(hidden, smallFavor, 2, faceup = true)
    assertEquals(Look(kept.treeless).advisers(actor),
      Vector[CardId](vow, faeMerchant, smallFavor))
    assertEquals(vowLines(kept.events), Vector.empty)

  test("a Beast played faceup is kept, and one played facedown is " +
      "buried, since a facedown card has no suit"):
    val ready = actWith(secondChance)(_.adviser(actor, vow))
    val faceup = playedToAdvisers(ready, secondChance, 1, faceup = true)
    assertEquals(Look(faceup.treeless).advisers(actor),
      Vector[CardId](vow, secondChance))
    assertEquals(vowLines(faceup.events), Vector.empty)
    val facedown = playedToAdvisers(ready, secondChance, 1, faceup = false)
    assertEquals(Look(facedown.treeless).advisers(actor), Vector[CardId](vow))
    assertEquals(worldDeck(facedown.treeless).lastOption, Some(secondChance))
    assertEquals(vowLines(facedown.events), buriedLine(secondChance))

  test("Vow of Union's own arrival buries every adviser that is not a " +
      "faceup Beast, a locked one too"):
    val ready = actWith(vow)(_.adviser(actor, faeMerchant)
      .adviser(actor, friend))
    val done = playedToAdvisers(ready, vow, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](faeMerchant, vow))
    assertEquals(worldDeck(done.treeless).lastOption, Some(friend))
    assertEquals(vowLines(done.events), buriedLine(friend))

  test("a play Vow of Union will bury carries a note, and the holder keeps " +
      "the adviser limit"):
    def adviserOptions(card: DenizenId): Vector[DecisionOption] =
      val ready = actWith(card)(_.adviser(actor, vow)
        .adviser(actor, faeMerchant))
      val tree = build(ready, actor, card)
      placementOptions(ready, tree, park(ready, tree, powers), powers)
        .filter(_.ref.isInstanceOf[DecisionOptionRef.AdviserPlay])
    def thirdSlot(faceup: Boolean): DecisionOption =
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(actor, 2,
        faceup))
    assertEquals(adviserOptions(smallFavor), Vector[DecisionOption](
      DecisionOption.Warned(thirdSlot(faceup = true), buryNote),
      DecisionOption.Warned(thirdSlot(faceup = false), buryNote)))
    assertEquals(adviserOptions(secondChance), Vector[DecisionOption](
      thirdSlot(faceup = true),
      DecisionOption.Warned(thirdSlot(faceup = false), buryNote)))
    val holding = actWith(smallFavor)(_.adviser(actor, vow))
    assertEquals(AdviserLimit.of(catalog, holding, actor),
      AdviserLimit.Default)
```

If `Fae Merchant` or `Second Chance` turns out to print a WHEN PLAYED, or cannot be played as an adviser, swap in another plain Beast denizen from the catalog and say so in the report.

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.VowOfUnionAdvisersSuite"`
Expected: compile failure, `VowOfUnionAdvisers` not found.

- [ ] **Step 3: Create the shared trait.** Create `SuitOnlyAdvisers.scala`. It is Family Wagon's bury code with the card, the suit and the warning left abstract. The trait uses `def`s only, because a subclass's `val`s are not set yet while the trait initialises.

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote,
  CardPlayRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower, Contribution, Transform}
import oathdigital.model._

/** A rule that its holder may have only faceup advisers of one suit, and
  * buries every other adviser they have or would gain: Family Wagon's
  * Nomads and Vow of Union's Beasts.
  *
  * While its holder has the card faceup:
  *  - card play notes each adviser play after which the rule would bury the
  *    played card (`buryWarning`), through a transform at
  *    `SearchPlayAdviser`. `playRules` adds the card's other card-play
  *    rules, such as Family Wagon's lifted limit.
  *  - an adviser arriving in the holder's advisers, by any route, makes the
  *    holder bury every adviser that is not a faceup card of `suit`, in held
  *    order (`onArrival`). The card's own arrival counts. A facedown card
  *    has no suit, so it is buried, and Locked does not stop a bury.
  *
  * A card it buries on arrival was never gained, so its WHEN PLAYED does
  * not run (`WhenPlayedPower`). The bury returns the card's favor to its
  * suit bank and its secrets to the active player, facedown. The line names
  * the holder only when the holder is not the active player, and covers the
  * generic "Buried" lines.
  */
trait SuitOnlyAdvisers extends ContributingPower:
  def catalog: ExecutableCatalog
  /** The card that sets the rule, held as an adviser. */
  def cardId: DenizenId
  /** The suit of the advisers its holder keeps. */
  def suit: Suit
  /** The note on a card play the rule will bury. */
  def buryWarning: String
  /** The card-play rules the card sets besides its bury note. */
  protected def playRules(rules: CardPlayRules): CardPlayRules = rules

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] =
    Vector(SuitOnlyAdvisers.buried, SuitOnlyAdvisers.buriedByHolder)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(rules => playRules(rules).noteAdviserPlay(
            AdviserPlayNote(OptionReason.Written(buryWarning),
              (after, played) => after.contains(faceUp) && !kept(played))))
        case _ => children
      })))

  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = SuitOnlyAdvisers.advisers(arrived.after, holder)
        .filterNot(adviser => kept(AdviserFace.of(adviser)))
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(_, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** Whether `face` stays under the rule: a faceup card of `suit`. */
  private def kept(face: AdviserFace): Boolean =
    face.orientation == Orientation.FaceUp &&
      catalog.suitOf(face.card).contains(suit)

  /** The bury of `adviser` from `holder`'s advisers, returning its favor to
    * its suit bank and its secrets to `actor`, facedown. */
  private def bury(adviser: AdviserState, holder: PlayerId, actor: PlayerId)
      : Vector[CoreOperation] =
    val from = PositionedLocation(Location.PlayArea(holder))
    adviser match
      case DenizenState(card, _, tokens) => Bury.standard(
        BuryableCard.Denizen(card), from, catalog.suitOf(card), tokens.favor,
        tokens.secrets, actor)
      case VisionState(card, _) => Bury.standard(BuryableCard.Vision(card),
        from, None, favor = 0, secrets = 0, actor)

  private def line(holder: PlayerId, actor: PlayerId, cards: Vector[CardId])
      : PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then SuitOnlyAdvisers.buried(card, NoteArg.Cards(cards))
    else SuitOnlyAdvisers.buriedByHolder(card, NoteArg.Player(holder),
      NoteArg.Cards(cards))

  private def faceUp: AdviserFace = AdviserFace(cardId, Orientation.FaceUp)

  /** Whether `player` holds the card among their advisers, in either
    * face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    SuitOnlyAdvisers.advisers(ready, player).exists(_.id == cardId)

  /** The player who has the card faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.map(_.player)
      .find(SuitOnlyAdvisers.hasFaceUp(ready, _, cardId))

object SuitOnlyAdvisers:
  /** "Buried {cards}.", on the holder's turn. */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "{Blue} buried {cards}.", when the holder is not the active player. */
  val buriedByHolder: NoteKey = NoteKey("buried.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(".")))

  /** Whether `player` has `card` faceup among their advisers. */
  def hasFaceUp(ready: ReadyGame, player: PlayerId, card: DenizenId)
      : Boolean = advisers(ready, player).exists {
    case DenizenState(held, Orientation.FaceUp, _) => held == card
    case _ => false
  }

  private[cardplay] def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)
```

- [ ] **Step 4: Make Family Wagon extend it.** Replace everything in `FamilyWagon.scala` after `FamilyWagonCard` with the code below. Keep `FamilyWagonCard` as it is. The imports become `oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, PrintedPower}`, `oathdigital.gameplay.actions.CardPlayRules` and `oathdigital.model._`.

```scala
/** Family Wagon (card 168, adviser-only): "You have no adviser limit but
  * may only have [Nomad] advisers--bury other advisers you have or would
  * gain."
  *
  * It is a [[SuitOnlyAdvisers]] rule for Nomads, shared with Vow of Union.
  * While its holder has it faceup it also lifts their limit:
  *  - card play gives the holder no limit. Its transform at
  *    `SearchPlayAdviser` lifts the base limit while Family Wagon is a
  *    faceup adviser after the play (`CardPlayRules.liftWhileFaceUp`).
  *  - `AdviserLimit.of` gives the holder no limit, before the lowering
  *    powers apply (`lifts`).
  */
final case class FamilyWagon private (catalog: ExecutableCatalog)
    extends SuitOnlyAdvisers:
  def id: PowerId = FamilyWagon.id
  def cardId: DenizenId = FamilyWagon.cardId
  def suit: Suit = Suit.Nomad
  def buryWarning: String = FamilyWagon.buryWarning
  override protected def playRules(rules: CardPlayRules): CardPlayRules =
    rules.liftWhileFaceUp(cardId)

object FamilyWagon:
  val id: PowerId = FamilyWagonCard.power.id
  val cardId: DenizenId = FamilyWagonCard.id
  /** The note on a card play Family Wagon will bury. */
  val buryWarning: String = "Family Wagon will bury it"

  def forCatalog(catalog: ExecutableCatalog): FamilyWagon =
    new FamilyWagon(catalog)

  /** Whether `player` has Family Wagon faceup, and so no adviser limit. */
  def lifts(ready: ReadyGame, player: PlayerId): Boolean =
    SuitOnlyAdvisers.hasFaceUp(ready, player, cardId)
```

- [ ] **Step 5: Run Family Wagon's suites.** They pin the refactor.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.FamilyWagonSuite oathdigital.gameplay.powers.cardplay.FamilyWagonArrivalsSuite"`
Expected: PASS, with no test changed.

- [ ] **Step 6: Move the card.** Cut `VowOfUnionCard` out of `HoldingBeastDenizens.scala` and create `campaign/VowOfUnion.scala` holding it, unchanged:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.model._

object VowOfUnionCard extends Denizen(DenizenId("185"), "Vow of Union", Suit.Beast) with Locked with AdviserOnly:
  // The two PrintedPowers exactly as they were in HoldingBeastDenizens.
```

Copy the `campaign` and `restriction` powers and `powers` verbatim. In `NewFoundations.scala`, add `VowOfUnionCard` to the `oathdigital.gameplay.powers.campaign.{...}` import, after `VowOfPeaceCard`, and re-wrap the import within 80 characters. `HoldingBeastDenizens.scala` keeps its imports while another card there uses them; remove any that become unused.

- [ ] **Step 7: Add the restriction half.** Create `VowOfUnionAdvisers.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.VowOfUnionCard
import oathdigital.model.{DenizenId, PowerId, Suit}

/** Vow of Union's restriction (card 185, adviser-only, locked): "You may
  * only have [Beast] advisers--bury other advisers you have or would gain."
  *
  * It is a [[SuitOnlyAdvisers]] rule for Beasts, as Family Wagon is for
  * Nomads. It lifts no limit. Its Campaign half is
  * [[oathdigital.gameplay.powers.campaign.VowOfUnion]].
  */
final case class VowOfUnionAdvisers private (catalog: ExecutableCatalog)
    extends SuitOnlyAdvisers:
  def id: PowerId = VowOfUnionAdvisers.id
  def cardId: DenizenId = VowOfUnionCard.id
  def suit: Suit = Suit.Beast
  def buryWarning: String = VowOfUnionAdvisers.buryWarning

object VowOfUnionAdvisers:
  val id: PowerId = VowOfUnionCard.restriction.id
  /** The note on a card play Vow of Union will bury. */
  val buryWarning: String = "Vow of Union will bury it"

  def forCatalog(catalog: ExecutableCatalog): VowOfUnionAdvisers =
    new VowOfUnionAdvisers(catalog)
```

In `WalkerPowerCatalog.scala`, add `VowOfUnionAdvisers` to the `oathdigital.gameplay.powers.cardplay.{...}` import and register it right after Family Wagon:

```scala
      Vector(FamilyWagon.forCatalog(catalog)) ++
      Vector(VowOfUnionAdvisers.forCatalog(catalog)) ++
```

- [ ] **Step 8: Pin the flags.** In `PowerKindsCatalogSuite`, add `"denizen.vow-of-union.campaign"` and `"denizen.vow-of-union.restriction"` to the end of `persistentRules`.

- [ ] **Step 9: Run the suites.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS, with 4 new tests.

- [ ] **Step 10: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisersSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Vow of Union keeps Beast advisers only, sharing Family Wagon's bury"
```

---

### Task 3: A walk that ends, and a Campaign skipped

**Files:**
- Modify: `src/main/scala/oathdigital/model/Operation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkState.scala`, `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, and any walker file the compiler names
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Test: `src/test/scala/oathdigital/gameplay/EndWalkSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/campaign/CampaignSkipSuite.scala`

**Interfaces:**
- Produces: `oathdigital.model.EndWalk`. `CampaignIds.skip`, `CampaignIds.skipOffered`, `CampaignAnswers.skipped(pending: PendingTree): Boolean`, `CampaignSetup.skipOffered(ready: ReadyGame): Boolean`. A Campaign whose kind is not settled after the before-targets steps ends with `EndWalk`.

- [ ] **Step 1: Write the failing walker test.** Create `EndWalkSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** `EndWalk` finishes the walk where it is reached: nothing after it runs,
  * in its own children or in any enclosing node. */
class EndWalkSuite extends munit.FunSuite:
  /** A leaf that records a step and names it. */
  private def recording(name: String): ModifyDicePool =
    ModifyDicePool(PoolKey(name), 1)

  test("a walk finishes at EndWalk inside a branch, and no later node runs, " +
      "not even a decision"):
    val later = Decide("test.later", ready.game.current.turn.activePlayer,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
        DecisionOptionRef.Button("ok"), "OK"))))
    val walk = Sequence(Vector[Operation](recording("first"),
      Branch((_, _) => Vector[Operation](recording("second"), EndWalk,
        recording("skipped"))),
      later, recording("last")))
    ProcedureWalker.advance(ready, walk, None, WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect { case step: WalkerStepRecorded =>
          step.ops }.flatten,
          Vector[CoreOperation](recording("first"), recording("second")))
      case other => fail(s"expected the walk to finish at EndWalk, got $other")
```

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.EndWalkSuite"`
Expected: compile failure, `EndWalk` not found.

- [ ] **Step 3: Add `EndWalk`.** In `model/Operation.scala`, after the `Operation` trait:

```scala
/** Ends the walk where it is reached: the procedure finishes at once, and
  * nothing after it in the tree runs. It changes no state and is never
  * part of a recorded batch, so the journal holds only the procedure's
  * usual completion. A Campaign that is skipped, or left with nothing to
  * attack, ends with it. */
case object EndWalk extends Operation:
  val children: Vector[Operation] = Vector.empty
```

- [ ] **Step 4: Teach the walker.**
  - In `WalkState.scala`, beside `Stopped`:

    ```scala
    /** The walk reached `EndWalk`: the procedure finishes here, and every
      * enclosing node stops walking its later children. */
    private[walker] final case class Ended(ctx: WalkCtx) extends Step
    ```

  - In `ProcedureWalker.walk`, add `case EndWalk => Right(Ended(ctx))` before `case note: Note`.
  - Wherever a walk stops on `case stop @ (_: Park | _: Stopped) => Right(stop)`, stop on `Ended` too.
  - In `toOutcome`, add `case Ended(ctx) => Right(WalkerOutcome.Finished(finish(ctx), ctx.events))`.
  - `Step` is sealed, so the compiler names every other match. In each, `Ended` behaves as follows:
    - inside a composite, a window or a `Repeat`, it stops the walk as `Park` does, with nothing after it run;
    - at the top, it finishes as `Done` does;
    - in a search, it reaches no answerable decision.
  - Previews that read the static tree (power gathering, the restriction look-ahead) see a node with no children and need no change. Check that, and say so in the report.

- [ ] **Step 5: Run the walker test.**

Run: `./sbtw "testOnly oathdigital.gameplay.EndWalkSuite oathdigital.gameplay.walker.*"`
Expected: PASS.

- [ ] **Step 6: Write the failing Campaign test.** Create `CampaignSkipSuite.scala`. A test-only power stands in for Vow of Union, which Task 4 adds:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Look

/** A Campaign whose start offered the Skip: the attacker is asked the kind
  * even with one legal kind, and "Skip Campaign" ends it. */
class CampaignSkipSuite extends munit.FunSuite:
  /** Offers the Skip at every Campaign's start, as Vow of Union does after
    * it moves a warband. */
  private object OffersSkipAtCampaignStart extends ContributingPower:
    val id: PowerId = PowerId("test.offers-skip")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignBeforeTargets -> Vector(Transform((_, children) =>
        BuildOps((_, _) => Right(Vector[CoreOperation](
          ModifyDicePool(CampaignIds.skipOffered, 1)))) +: children)))

  private val powers = WalkerPowers(Vector(OffersSkipAtCampaignStart))
  private val game = rulesWith(Vector(OffersSkipAtCampaignStart))

  /** p1, with 4 warbands and 7 Supply, can only conquer the Bandits at
    * Ancient City. Their Campaign is started under the Skip offer. */
  private def conquestOfferingSkip: (Board, Run) =
    val b = board(warbands = 4, supply = 7)
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor).fold(error => fail(s"no start: $error"), identity)
    (b, Run(game, started, started.events, powers))

  test("an offered Skip asks the kind even with one legal kind, and Skip " +
      "ends the Campaign with its Supply spent"):
    val (b, asked) = conquestOfferingSkip
    assert(awaits(asked, b.actor, CampaignIds.kind))
    assertEquals(asked.options(b.actor, powers), Vector[DecisionOption](
      DecisionOption.Button(DecisionOptionRef.Button("conquest"),
        "Conquest"),
      DecisionOption.Button(CampaignIds.skip, "Skip Campaign")))
    val skipped = asked.pick(b.actor, CampaignIds.kind, CampaignIds.skip)
    val current = ready(skipped.state).game.current
    assertEquals(current.walkerProcedure, None)
    assertEquals(current.lastCampaignResult, None)
    assertEquals(Look(skipped.state).supply(b.actor), 7 - CampaignIds.SupplyCost)
    assertEquals(Look(skipped.state).boardWarbands(b.actor), 4)
    assert(!skipped.ops.exists(_.isInstanceOf[RecordCampaignResult]))

  test("choosing the kind instead runs the Campaign on"):
    val (b, asked) = conquestOfferingSkip
    val conquest = asked.pick(b.actor, CampaignIds.kind,
      DecisionOptionRef.Button("conquest"))
    assert(awaits(conquest, b.actor, CampaignIds.force))
```

`Run.ops` collects the recorded operations, as `PlanDriver` defines it. If a different field holds them, read the `WalkerStepRecorded` events instead.

- [ ] **Step 7: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.CampaignSkipSuite"`
Expected: compile failure, `CampaignIds.skip` not found.

- [ ] **Step 8: Add the Skip and the early end.** In `CampaignSetup.scala`:
  - In `CampaignIds`, after `SupplyCost`:

    ```scala
      /** The kind question's last option when a power at the Campaign's start
        * offered it: the attacker ends the Campaign instead. */
      val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
      /** Recorded by a power that moved pieces at the Campaign's start (Vow of
        * Union), so the kind question offers `skip`. It is never rolled, and
        * it is cleared with the other pools when the Campaign ends. */
      val skipOffered: PoolKey = PoolKey("campaign.skip-offered")
    ```

  - In `CampaignAnswers`:

    ```scala
      /** Whether the attacker answered the kind question with Skip. */
      def skipped(pending: PendingTree): Boolean =
        latest(pending, CampaignIds.kind).contains(
          DecisionAnswer.ChooseOneAnswer(CampaignIds.skip))
    ```

  - In `CampaignSetup`, add `skipOffered`. Then make `kindOf` settle no kind when the Campaign was skipped, and settle the one legal kind alone only while no Skip is on offer:

    ```scala
      /** Whether a power at this Campaign's start offered the Skip. */
      def skipOffered(ready: ReadyGame): Boolean =
        ready.game.current.rollPools.contains(CampaignIds.skipOffered)

      def kindOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
          : Option[CampaignKind] =
        if CampaignAnswers.skipped(pending) then None
        else pinned(ready).map(_._1).orElse(
          CampaignAnswers.kind(pending).orElse(legalKinds(ready, actor) match {
            case Vector(only) if !skipOffered(ready) => Some(only)
            case _ => None
          }))
    ```

  Keep `kindOf`'s doc comment, adding: "No kind is settled once the attacker skipped, nor by the one legal kind while the Skip is on offer."

- [ ] **Step 9: Ask the Skip, and end the Campaign.** In `CampaignProcedure.scala`:
  - Replace `kindStep`:

    ```scala
      /** Asked when several kinds are legal, or when a power at the Campaign's
        * start offered the Skip, which is then the last option. Omitted when
        * no kind is legal: `endStep` then ends the Campaign. */
      private def kindStep(actor: PlayerId): Operation = Branch((ready, _) => {
        val kinds = CampaignSetup.kindOptions(ready, actor)
        val skip = CampaignSetup.skipOffered(ready)
        if kinds.isEmpty || (kinds.size < 2 && !skip) then Vector.empty
        else Vector(Decide(CampaignIds.kind, actor, DecisionQuery.ChooseOne(
          kinds ++ Option.when(skip)(DecisionOption.Button(CampaignIds.skip,
            "Skip Campaign")), heading = Some("Choose a Campaign")),
          window = Some(PowerWindow.CampaignKindSelection)))
      })
    ```

  - Add `endStep`:

    ```scala
      /** Ends the Campaign when no kind is settled once its targets are chosen:
        * the attacker skipped, or a power at its start left nothing to attack.
        * The Supply stays spent. It is the last of the before-targets steps,
        * so no later step's place in the tree moves. */
      private def endStep(actor: PlayerId): Operation =
        Branch((ready, pending) =>
          if CampaignSetup.kindOf(ready, actor, pending).isEmpty then
            Vector(EndWalk)
          else Vector.empty)
    ```

  - In `battle`, append it to the before-targets section, and nowhere else:

    ```scala
        Sequence(Vector[Operation](kindStep(actor), defenderStep(actor),
          targetsStep(catalog, actor), endStep(actor)),
          Some(PowerWindow.CampaignBeforeTargets)),
    ```

  - Add one sentence to the object's doc comment: "A Campaign ends before its force is committed when the attacker skips it or nothing is left to attack (`endStep`)."

- [ ] **Step 10: Run the Campaign suites.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign* oathdigital.gameplay.EndWalkSuite"`
Expected: PASS. The 3 new tests pass, and no existing Campaign test changes.

- [ ] **Step 11: Commit.**

```bash
git add src/main/scala/oathdigital/model/Operation.scala src/main/scala/oathdigital/gameplay/walker/WalkState.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala src/test/scala/oathdigital/gameplay/EndWalkSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/CampaignSkipSuite.scala
git commit -m "feat(batch8): end a walk early, and let a Campaign be skipped or end with nothing to attack"
```

Add any other walker file the compiler made you change.

---

### Task 4: Warbands at a Campaign's start

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala` and, if Step 9 needs it, `StartLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala`, `src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala`

**Interfaces:**
- Consumes: `VowOfUnionCard` (Task 2). `CampaignIds.skip`, `CampaignIds.skipOffered`, `CampaignAnswers.skipped` and the early end (Task 3).
- Produces: `WarbandArrangement.oneWay(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)], intoSites: Boolean, heading: String): DecisionQuery`. `VowOfUnion` with `directionDecisionId`, `arrangeDecisionId`, `toBoard`, `toSites`, `stay` and `noBoardWarbands`.

- [ ] **Step 1: Write the failing suite.** Create `VowOfUnionSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer,
  DistributeAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

/** Vow of Union's Campaign half: at the start of any Campaign its holder
  * may move warbands, in one direction, between their board and the sites
  * they rule in their region. A move offers the attacker the Skip, and a
  * move that leaves nothing to attack ends the Campaign. */
class VowOfUnionSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Union")
  /** In the Cradle, with Ancient City, every board's origin. */
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  /** In the Provinces. */
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val game = rules(winningDice)
  private val toBoardHeading = "Vow of Union: move warbands from the sites " +
    "you rule in your region to your board"
  private val toSitesHeading = "Vow of Union: move warbands from your " +
    "board to the sites you rule in your region"

  /** p1's Campaign on `b`, started and parked on its first question. */
  private def campaignStarted(b: Board): Run =
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor).fold(error => fail(s"no start: $error"), identity)
    Run(game, started, started.events)

  /** p3, the bystander, holds Vow of Union on `orientation` and rules
    * Broken Peaks, in their region, with 2 warbands, and Buried Giant,
    * outside it, with 1. Their board holds `onBoard` warbands. p1 can only
    * conquer the Bandits at Ancient City. */
  private def bystanderHoldsVowRulingTwoSites(onBoard: Int = 3,
      orientation: Orientation = Orientation.FaceUp): Board =
    val b = board(warbands = 4, supply = 7)
    on(withAdviserFor(b, b.bystander, vow, orientation))(_
      .warbandsAt(brokenPeaks, b.bystander, 2)
      .warbandsAt(buriedGiant, b.bystander, 1)
      .boardWarbands(b.bystander, onBoard))

  /** A distribution slot from `min` to `max`, opening on `now`. */
  private def slotOpeningOn(ref: DecisionOptionRef, min: Int, max: Int,
      now: Int): DistributeSlot = DistributeSlot(ref, min, max, Some(now))

  /** A distribution answer: the player's board, then each site. */
  private def arranged(onBoard: (PlayerId, Int),
      sites: (SiteId, Int)*): DistributeAnswer =
    DistributeAnswer(DistributeAmount(DecisionOptionRef.Player(onBoard._1),
      onBoard._2) +: sites.toVector.map((site, count) =>
      DistributeAmount(DecisionOptionRef.Site(site), count)))

  private def siteWarbands(run: Run, site: SiteId): Int =
    Look(run.state).forces(site) match
      case SiteForces.Occupied(_, count) => count
      case _ => 0

  /** The kind question's options: `kinds`, then Skip Campaign. */
  private def kindsThenSkip(kinds: (String, String)*): Vector[DecisionOption] =
    kinds.toVector.map((key, label) => DecisionOption.Button(
      DecisionOptionRef.Button(key), label)) :+
      DecisionOption.Button(CampaignIds.skip, "Skip Campaign")

  test("an off-turn holder moves warbands from the sites they rule in " +
      "their region to their board, and the attacker may then skip"):
    val b = bystanderHoldsVowRulingTwoSites()
    val asked = campaignStarted(b)
    assert(awaits(asked, b.bystander, VowOfUnion.directionDecisionId))
    assertEquals(asked.options(b.actor), Vector[DecisionOption](
      DecisionOption.Button(VowOfUnion.toBoard,
        "Move warbands to your board"),
      DecisionOption.Button(VowOfUnion.toSites,
        "Move warbands to your sites"),
      DecisionOption.Button(VowOfUnion.stay, "Move none")))
    val way = asked.pick(b.bystander, VowOfUnion.directionDecisionId,
      VowOfUnion.toBoard)
    // Buried Giant lies outside the holder's region, so it has no slot.
    assertEquals(way.query(b.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(b.bystander), 3, 5, 3),
        slotOpeningOn(DecisionOptionRef.Site(brokenPeaks), 0, 2, 2)),
      5, Some(toBoardHeading), "Move warbands"))
    val done = way.answer(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 5, brokenPeaks -> 0))
    assertEquals(Look(done.state).boardWarbands(b.bystander), 5)
    assertEquals(siteWarbands(done, brokenPeaks), 0)
    assertEquals(siteWarbands(done, buriedGiant), 1)
    assert(awaits(done, b.actor, CampaignIds.kind))
    assertEquals(done.options(b.actor),
      kindsThenSkip("conquest" -> "Conquest"))
    assertEquals(said(VowOfUnion, done.events), Vector(NoteText.Said(
      "moved.board.holder",
      s"${b.bystander.value} moved 2 warbands from sites to their board.",
      covers = false)))
    assertWireRoundTrips(done.events)

  test("an attacker holding it commits the warbands it moved to their " +
      "board, and the line leaves them unnamed"):
    val base = board(warbands = 1, supply = 7)
    val b = on(withAdviser(base, vow, Orientation.FaceUp))(
      _.warbandsAt(brokenPeaks, base.actor, 3))
    val way = campaignStarted(b).pick(b.actor,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
    val moved = way.answer(b.actor, VowOfUnion.arrangeDecisionId,
      arranged(b.actor -> 4, brokenPeaks -> 0))
    assertEquals(said(VowOfUnion, moved.events), Vector(NoteText.Said(
      "moved.board", "Moved 3 warbands from sites to the board.",
      covers = false)))
    val done = moved.pick(b.actor, CampaignIds.kind,
      DecisionOptionRef.Button("conquest"))
    // The force question reads the board after the moves.
    done.query(b.actor) match
      case DecisionQuery.ChooseAmount(min, max, _, _, _) =>
        assertEquals((min, max), (0, 4))
      case other => fail(s"expected the force question, got $other")
    val committed = done.answer(b.actor, CampaignIds.force,
      ChooseAmountAnswer(4))
    assert(!awaits(committed, b.actor, CampaignIds.force))

  test("an off-turn holder moves warbands from their board to their " +
      "sites, and no site may lose any"):
    val b = bystanderHoldsVowRulingTwoSites()
    val way = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.toSites)
    assertEquals(way.query(b.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(b.bystander), 0, 3, 3),
        slotOpeningOn(DecisionOptionRef.Site(brokenPeaks), 2, 5, 2)),
      5, Some(toSitesHeading), "Move warbands"))
    assert(way.refused(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 4, brokenPeaks -> 1)).isDefined)
    val done = way.answer(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 0, brokenPeaks -> 5))
    assertEquals(siteWarbands(done, brokenPeaks), 5)
    assertEquals(said(VowOfUnion, done.events), Vector(NoteText.Said(
      "moved.sites.holder",
      s"${b.bystander.value} moved 3 warbands from their board to sites.",
      covers = false)))

  test("it asks nothing without a ruled site in the holder's region or " +
      "facedown; moving nothing offers no Skip; an empty board's direction " +
      "is unavailable"):
    val base = board(warbands = 4, supply = 7)
    val outside = on(withAdviserFor(base, base.bystander, vow,
      Orientation.FaceUp))(_.warbandsAt(buriedGiant, base.bystander, 1))
    assert(awaits(campaignStarted(outside), base.actor, CampaignIds.force))
    val hidden = bystanderHoldsVowRulingTwoSites(
      orientation = Orientation.FaceDown)
    assert(awaits(campaignStarted(hidden), base.actor, CampaignIds.force))
    val b = bystanderHoldsVowRulingTwoSites()
    val stayed = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.stay)
    assert(awaits(stayed, b.actor, CampaignIds.force))
    assertEquals(Look(stayed.state).boardWarbands(b.bystander), 3)
    assertEquals(siteWarbands(stayed, brokenPeaks), 2)
    assertEquals(said(VowOfUnion, stayed.events), Vector.empty)
    val unchanged = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      .answer(b.bystander, VowOfUnion.arrangeDecisionId,
        arranged(b.bystander -> 3, brokenPeaks -> 2))
    assert(awaits(unchanged, b.actor, CampaignIds.force))
    assertEquals(said(VowOfUnion, unchanged.events), Vector.empty)
    val empty = bystanderHoldsVowRulingTwoSites(onBoard = 0)
    assertEquals(campaignStarted(empty).withheld(base.actor,
      VowOfUnion.toSites),
      Some(OptionReason.Written(VowOfUnion.noBoardWarbands)))

  test("emptying the attacker's only target ends the Campaign with its " +
      "Supply spent; with a Raid still legal, the attacker chooses"):
    val base = board(warbands = 4, supply = 7)
    /** p2, at Broken Peaks with Vow of Union, rules Ancient City, p1's
      * site, with 2 warbands. */
    val rivalWithVowRulesOrigin =
      on(withAdviserFor(base, base.rival, vow, Orientation.FaceUp))(
        _.warbandsAt(base.origin, base.rival, 2))
    def emptiedOrigin(b: Board): Run =
      val way = campaignStarted(b).pick(base.rival,
        VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      assertEquals(way.query(base.actor), DecisionQuery.Distribute.exactly(
        Vector(slotOpeningOn(DecisionOptionRef.Player(base.rival), 3, 5, 3),
          slotOpeningOn(DecisionOptionRef.Site(base.origin), 0, 2, 2)),
        5, Some(toBoardHeading), "Move warbands"))
      way.answer(base.rival, VowOfUnion.arrangeDecisionId,
        arranged(base.rival -> 5, base.origin -> 0))
    val ended = emptiedOrigin(rivalWithVowRulesOrigin)
    val current = ready(ended.state).game.current
    assertEquals(current.walkerProcedure, None)
    assertEquals(current.lastCampaignResult, None)
    assertEquals(Look(ended.state).forces(base.origin), SiteForces.Empty)
    assertEquals(Look(ended.state).supply(base.actor),
      7 - CampaignIds.SupplyCost)
    assertWireRoundTrips(ended.events)
    // With p2's pawn at Ancient City a Raid stays legal.
    val raidable = on(rivalWithVowRulesOrigin)(
      _.pawn(base.rival, at = base.origin))
    val asked = emptiedOrigin(raidable)
    assert(awaits(asked, base.actor, CampaignIds.kind))
    assertEquals(asked.options(base.actor), kindsThenSkip("raid" -> "Raid"))
```

Notes for this suite:
- Two things are stated, not verified at plan time. Broken Peaks and Ancient City lie in the Cradle and Buried Giant in the Provinces, as `SiegeEnginesSuite` states. `CampaignFixture.board` puts p2 and p3 at Broken Peaks. If either fact turns out false, pick sites that keep each test's meaning and say so in the report.
- `Run.options`, `Run.query` and `Run.withheld` rebuild the parked decision with the Campaign's actor, so they take the actor even when the holder is asked.

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfUnionSuite"`
Expected: compile failure, `VowOfUnion` not found.

- [ ] **Step 3: Add the one-way arrangement.** In `WarbandArrangement.scala`, extend the object's doc comment with "Vow of Union arranges them in one direction, with no minimum (`oneWay`)." Then add after `query`:

```scala
  /** One rearrangement in one direction, as Vow of Union makes it: any
    * number of warbands leave `sites` for the board (`intoSites` false), or
    * leave the board for `sites` (`intoSites` true), and none need move.
    * Each slot opens on what it holds now, and its bounds keep the
    * direction. A site may empty. */
  def oneWay(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      intoSites: Boolean, heading: String): DecisionQuery =
    val total = board + sites.map(_._2).sum
    val boardSlot =
      if intoSites then DistributeSlot(DecisionOptionRef.Player(user), 0,
        board, Some(board))
      else DistributeSlot(DecisionOptionRef.Player(user), board, total,
        Some(board))
    val siteSlots = sites.map { case (site, count) =>
      if intoSites then DistributeSlot(DecisionOptionRef.Site(site), count,
        count + board, Some(count))
      else DistributeSlot(DecisionOptionRef.Site(site), 0, count,
        Some(count)) }
    DecisionQuery.Distribute.exactly(boardSlot +: siteSlots, total,
      Some(heading), "Move warbands")
```

`moves` already turns any answer over the same sites into moves, shrinking sites first.

- [ ] **Step 4: Add the Campaign half.** In `campaign/VowOfUnion.scala`, widen the imports to:

```scala
import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.campaign.{CampaignIds,
  CampaignProcedure}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  WarbandArrangement}
import oathdigital.model._
```

Then add after `VowOfUnionCard`:

```scala
/** Vow of Union's Campaign half (card 185, adviser-only, locked): "At the
  * start of any campaign, you may move any number of warbands from sites
  * you rule in your region to your board or vice versa."
  *
  * A transform at `CampaignBeforeTargets` puts one `Branch` ahead of the
  * kind, the defender and the targets, in every Campaign. It finds the
  * player who has Vow of Union faceup. That holder may be the attacker, the
  * defender or neither, and the holder answers both questions, which are
  * named under the Campaign's prefix:
  *  - the direction: to the board, to the sites, or "Move none";
  *  - the amounts, one `Distribute` whose bounds keep the direction
  *    ([[WarbandArrangement.oneWay]]).
  *
  * The sites are those the holder rules in the region of their pawn. Nothing
  * is asked without one. "To your sites" with an empty board is shown
  * unavailable. A site may empty. When at least one warband moved, it offers
  * the attacker the Skip (`CampaignIds.skipOffered`); and if no Campaign
  * kind is legal any more, the Campaign ends (`CampaignProcedure`).
  *
  * Its line follows the moves and names the count and the direction. It
  * names the holder only when the holder is not the active player. It covers
  * nothing: the generic move lines say which sites. Both questions are
  * narrated. Its restriction half is
  * [[oathdigital.gameplay.powers.cardplay.VowOfUnionAdvisers]].
  */
case object VowOfUnion extends ContributingPower:
  val cardId: DenizenId = VowOfUnionCard.id
  val id: PowerId = VowOfUnionCard.campaign.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val directionDecisionId: String =
    CampaignProcedure.decisionPrefix + "vow-of-union.direction"
  val arrangeDecisionId: String =
    CampaignProcedure.decisionPrefix + "vow-of-union.arrange"
  val toBoard: DecisionOptionRef.Button = DecisionOptionRef.Button("to-board")
  val toSites: DecisionOptionRef.Button = DecisionOptionRef.Button("to-sites")
  val stay: DecisionOptionRef.Button = DecisionOptionRef.Button("stay")
  val noBoardWarbands: String = "No warbands on your board"
  /** "Moved {2 warbands} from sites to the board." */
  val movedToBoard: NoteKey = NoteKey("moved.board", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0),
    NotePart.Text(" from sites to the board.")))
  /** "Moved {3 warbands} from the board to sites." */
  val movedToSites: NoteKey = NoteKey("moved.sites", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0),
    NotePart.Text(" from the board to sites.")))
  /** "{Blue} moved {2 warbands} from sites to their board." */
  val holderMovedToBoard: NoteKey = NoteKey("moved.board.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" moved "), NotePart.Arg(1),
    NotePart.Text(" from sites to their board.")))
  /** "{Blue} moved {3 warbands} from their board to sites." */
  val holderMovedToSites: NoteKey = NoteKey("moved.sites.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" moved "), NotePart.Arg(1),
    NotePart.Text(" from their board to sites.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(movedToBoard, movedToSites,
    holderMovedToBoard, holderMovedToSites)
  override def narratedDecisions: Set[String] =
    Set(directionDecisionId, arrangeDecisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignBeforeTargets -> Vector(Transform((ctx, children) =>
      start(ctx.activePlayer) +: children)))

  private def start(attacker: PlayerId): Operation = Branch((ready, _) =>
    holderOf(ready).toVector.flatMap(direction(ready, attacker, _)))

  private def direction(ready: ReadyGame, attacker: PlayerId,
      holder: PlayerId): Vector[Operation] =
    val (board, sites) = held(ready, holder)
    if sites.isEmpty then Vector.empty
    else Vector(
      Decide(directionDecisionId, holder, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(toBoard, "Move warbands to your board"),
        shown(toSites, "Move warbands to your sites",
          Option.when(board == 0)(noBoardWarbands)),
        DecisionOption.Button(stay, "Move none")), heading = Some(
        "Vow of Union: move warbands between your board and the sites " +
          "you rule in your region"))),
      Branch((live, pending) => arrange(live, attacker, holder, pending)))

  private def shown(ref: DecisionOptionRef.Button, label: String,
      reason: Option[String]): DecisionOption =
    val button = DecisionOption.Button(ref, label)
    reason.fold[DecisionOption](button)(why =>
      DecisionOption.Unavailable(button, OptionReason.Written(why)))

  private def arrange(ready: ReadyGame, attacker: PlayerId,
      holder: PlayerId, pending: PendingTree): Vector[Operation] =
    val way = PowerAnswers.one(pending, directionDecisionId)
    if !way.contains(toBoard) && !way.contains(toSites) then Vector.empty
    else
      val intoSites = way.contains(toSites)
      val (board, sites) = held(ready, holder)
      Vector(
        Decide(arrangeDecisionId, holder, WarbandArrangement.oneWay(holder,
          board, sites, intoSites, heading(intoSites))),
        BuildOps((live, answers) => PowerAnswers.distribution(answers,
          arrangeDecisionId).toRight(PowerAnswers.missing(arrangeDecisionId))
          .flatMap(rows => WarbandArrangement.moves(live, holder,
            region(live, holder), rows)).map(offeringSkip)),
        Note(id, movedNote(_, attacker, holder)))

  /** `moves`, and, when any warband moves, the Skip offered to the
    * attacker. */
  private def offeringSkip(moves: Vector[CoreOperation])
      : Vector[CoreOperation] =
    if moves.isEmpty then moves
    else moves :+ ModifyDicePool(CampaignIds.skipOffered, 1)

  private def heading(intoSites: Boolean): String =
    if intoSites then "Vow of Union: move warbands from your board to the " +
      "sites you rule in your region"
    else "Vow of Union: move warbands from the sites you rule in your " +
      "region to your board"

  /** The player who has Vow of Union faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The sites `holder` rules in the region of their pawn, in map order. */
  private def region(ready: ReadyGame, holder: PlayerId): Vector[SiteId] =
    val map = ready.game.current.map
    val ruled = PowerAccess.ruledSites(ready, holder)
    PowerAccess.pawnSite(ready, holder).flatMap(map.regionOf).toVector
      .flatMap(home => map.inPlay.filter(site =>
        ruled.contains(site) && map.regionOf(site).contains(home)))

  /** The holder's board, and each site of their region with what it
    * holds. */
  private def held(ready: ReadyGame, holder: PlayerId)
      : (Int, Vector[(SiteId, Int)]) =
    WarbandArrangement.holdings(ready, holder, region(ready, holder))

  private def boardWarbands(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.warbands)

  /** The warbands the step before moved, by the change on the holder's
    * board: a gain came from the sites, a loss went to them. */
  private def movedNote(states: NoteStates, attacker: PlayerId,
      holder: PlayerId): Option[PowerNote] = for
    step <- states.previous
    change = boardWarbands(step._2, holder) - boardWarbands(step._1, holder)
    if change != 0
  yield
    val card = PowerSourceRef.Card(cardId)
    val count = NoteArg.Amount(math.abs(change), NoteUnit.Warband)
    if holder == attacker then
      (if change > 0 then movedToBoard else movedToSites)(card, count)
    else (if change > 0 then holderMovedToBoard else holderMovedToSites)(
      card, NoteArg.Player(holder), count)
```

- [ ] **Step 5: Register it.** In `PlanRules.scala`, append `++ Vector(VowOfUnion)` after `Vector(Herald.forCatalog(catalog))`. In its doc comment, change "...and\n  * Herald (a favor after another player's Campaign). Each names its card" so that it reads "..., Herald (a favor after another player's Campaign) and Vow of Union (warbands moved at the start of any Campaign). Each names its card", re-wrapped.

- [ ] **Step 6: Run the suite.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfUnionSuite"`
Expected: PASS, 5 tests.

The suite is the first test of a non-attacker's question at the start of a Campaign. If the walker refuses it, or the force question does not read the moved warbands, stop and report. Do not change the walker or `CampaignProcedure` beyond Task 3 without asking.

- [ ] **Step 7: Write the failing log suite.** In `LogScripts.scala`, drop `private` from `raidAnswers`. Then create `VowOfUnionLogSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.VowOfUnion
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, raidAnswers, texts}

/** How Vow of Union reads in the Game Log: an off-turn holder is named,
  * neither of its questions posts a "Chose" line, and a Campaign it lets
  * end early says why. */
class VowOfUnionLogSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Union")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val buriedGiant = CatalogNames.site("Buried Giant")

  /** Vow of Union's answers: the holder moves every warband at `site` to
    * their board, which then holds `after`. */
  private def holderMovesAllToBoard(holder: PlayerId, site: SiteId,
      after: Int): Answers = {
    case park if park.decisionId == VowOfUnion.directionDecisionId =>
      ChooseOneAnswer(VowOfUnion.toBoard)
    case park if park.decisionId == VowOfUnion.arrangeDecisionId =>
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Player(holder), after),
        DistributeAmount(DecisionOptionRef.Site(site), 0)))
  }

  /** p1 campaigns from Ancient City, where p2 stands. p3, at Buried Giant
    * with Vow of Union, rules it with 2 warbands and moves both to their
    * board, which then holds 5. `kind` answers p1's kind question, Raid
    * unless it says otherwise. The log as p2 sees it. */
  private def raidAfterHolderMovedTwo(kind: Answers = PartialFunction.empty)
      : Vector[String] =
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, ancientCity)
      .pawn(p3, buriedGiant).adviser(p3, vow).warbandsAt(buriedGiant, p3, 2)
      .banditsRefilled
    val (service, act) = atTable("vow-of-union", table, Dice.raid)
    raidAnswers(act, kind.orElse(holderMovesAllToBoard(p3, buriedGiant, 5)))
      .after(MenuPlay.start(p1, ActionRef.Campaign))
    texts(format(Script("vow-of-union", service, p1), Some(p2)))

  test("its line names the off-turn holder, with no Chose line for either " +
      "question"):
    val log = raidAfterHolderMovedTwo()
    assert(log.contains(s"Vow of Union: ${playerLabel(p3)} moved 2 " +
      "warbands from sites to their board."), log.mkString("\n"))
    assert(!log.exists(_.startsWith(s"${playerLabel(p3)} chose")),
      log.mkString("\n"))

  test("a skipped Campaign says so, with no Chose line for the Skip"):
    val log = raidAfterHolderMovedTwo {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(CampaignIds.skip)
    }
    assert(log.exists(_.startsWith("Campaign")), log.mkString("\n"))
    assert(log.contains("Skipped the Campaign."), log.mkString("\n"))
    assert(!log.exists(_.contains("Skip Campaign")), log.mkString("\n"))

  test("a Campaign left with nothing to attack says so"):
    // p2, at Broken Peaks with Vow of Union, rules Ancient City, p1's site,
    // and empties it: no Conquest is left, and no Raid was legal.
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, "Broken Peaks")
      .pawn(p3, buriedGiant).adviser(p2, vow).warbandsAt(ancientCity, p2, 2)
      .banditsRefilled
    val (service, act) = atTable("vow-of-union-ended", table, Dice.raid)
    act.withAnswers(holderMovesAllToBoard(p2, ancientCity, 5))
      .after(MenuPlay.start(p1, ActionRef.Campaign))
    val log = texts(format(Script("vow-of-union-ended", service, p1),
      Some(p3)))
    assert(log.exists(_.startsWith("Campaign")), log.mkString("\n"))
    assert(log.contains("Nothing left to attack: the Campaign ended."),
      log.mkString("\n"))
```

Each board starts with 3 warbands, so each answer leaves the holder's board at 5. If `Answers` is not the name of `raidAnswers`' partial-function type, use whatever type it takes. If the Raid script asks something `raidAnswers` does not answer, add that answer and say so in the report.

- [ ] **Step 8: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.application.gamelog.VowOfUnionLogSuite"`
Expected: the first test passes; the other two fail on the missing early-end lines.

- [ ] **Step 9: Write the early-end lines.** In `CampaignLines.closing`, a Campaign run that completes with no `RecordCampaignResult` posts one line. Replace the `WalkerCompleted` case's body so that a run with a result reads as before, and one without reads `ended`:

```scala
    case _: WalkerCompleted =>
      val ops = journal.runOps(run, at).map(_._2)
      val outcome = ops.dropWhile {
        case OpStep(_: RecordCampaignResult, _, _) => false
        case _ => true
      }
      outcome.headOption.collect {
        case OpStep(RecordCampaignResult(result), _, _) => result
      }.fold(ended(journal, run, at))(result =>
        gains(result, outcome.tail, viewer) ++
          losses(result, run.actor, outcome.tail, viewer))
```

```scala
  /** The line of a Campaign that ended before its battle: skipped by the
    * attacker, or left with nothing to attack. */
  private def ended(journal: LogJournal, run: Run, at: Int): Vector[Posted] =
    val skipped = CampaignAnswers.skipped(
      PendingTree(Vector.empty, journal.answers(run, at)))
    Vector(Posted.line(LogKind.Delta, Vector(Text(
      if skipped then "Skipped the Campaign."
      else "Nothing left to attack: the Campaign ended."))))
```

Add `CampaignAnswers` to the `oathdigital.gameplay.actions.campaign` import.

Then run the log suite again and fix what it shows, keeping the Raid and Conquest logs as they are:
  - **No line starts with "Campaign".** The start line waits for the first event that settles the kind and the defender, and a skipped Campaign never settles them. In `StartLines.campaign`, fall back to the bare tag at the run's `WalkerCompleted`: append `.orElse(Option.when(journal.event(at).isInstanceOf[WalkerCompleted])(Vector.empty))`.
  - **A line contains "Skip Campaign".** The Skip answer posted a "Chose" line. Suppress it the way the kind answer's own "Chose" line is suppressed, and leave the kind answer's lines as they are.

- [ ] **Step 10: Run the log suites.**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, with 3 new tests and no existing test changed.

- [ ] **Step 11: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/main/scala/oathdigital/application/gamelog/CampaignLines.scala src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala
git commit -m "feat(batch8): Vow of Union moves warbands at the start of any Campaign"
```

Add `StartLines.scala` if Step 9 changed it.

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

Expected: all pass. The server count is the baseline plus 15; the frontend count is unchanged.

- [ ] **Step 2: Check the client.** The Skip is a new option on an existing question, and an ended Campaign shows no battle. Search the frontend and `GamePresentationProjector` for code that lists rolled pools or expects every finished Campaign to have a result. Then confirm two things:
  - `campaign.skip-offered` is never shown as a dice pool, as `campaign.sacrifice-worth` is not;
  - the Campaign panel copes with a Campaign that has no result.

  Fix only what breaks, and report what you checked.

- [ ] **Step 3: Update the docs.**
  - In the design's Status, "Slices 1 and 2 are done." becomes "Slices 1 to 3 are done."
  - In `docs/ROADMAP.md`, check `- [ ] **3. Warbands at a Campaign's start:** Vow of Union.` and change the Now line to "**Phase - Catalog batch 8** is under way: slices 1 to 3 are done, and slice 4 is next."

- [ ] **Step 4: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 3 done"
```

- [ ] **Step 5: Report:**
  - the baseline and final test counts;
  - any fixture, card or site you swapped;
  - every walker match `Ended` needed, and the previews you checked;
  - what the client check found.
