# Catalog Batch 8, Slice 3 (Warbands at a Campaign's Start) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Vow of Union (185), both halves: warbands moved at the start of any Campaign, and Beast advisers only.

**Architecture:**

- **The Campaign half** is a `ContributingPower`, `VowOfUnion`. A `Transform` at `CampaignBeforeTargets` puts one `Branch` ahead of the Campaign's kind, defender and target steps. The `Branch` finds the holder and asks two questions, both owned by the holder and named under `CampaignProcedure.decisionPrefix`:
  - a `ChooseOne` of the direction;
  - then a `Distribute` in that direction.

  A `BuildOps` then makes the moves, and a `Note` writes the line. The Campaign runs on afterwards. Herald already asks an off-turn holder inside a Campaign, so the walker needs no change.
- **A one-way warband arrangement.** `WarbandArrangement.oneWay` builds the `Distribute` for one direction. Its slot bounds enforce the direction: in "to your board", each site may only lose warbands, and in "to your sites", each site may only gain them. Any number may move, including none. The existing `WarbandArrangement.moves` turns the answer into moves.
- **The restriction half shares Family Wagon's code.** A new trait, `SuitOnlyAdvisers`, takes Family Wagon's bury rules and leaves the suit as a parameter. Family Wagon extends it with Nomad and keeps its limit lift. The new `VowOfUnionAdvisers` extends it with Beast and does not lift the limit.
- **The force question needs no change.** `CampaignProcedure` builds the force `Decide` from the state the tree is built with. The walker rebuilds the tree from live state on every command, and the parked query is rebuilt too. So the attacker's force question offers warbands that Vow of Union moved to their board. A test pins this.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "Goal and scope", "The bar" (the warband arrangement bullet), "Catalog and roadmap corrections" (the Vow of Union bullet), "Slice 3: Warbands at a Campaign's start", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 3 entry.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 2 merged. Slice 2 also moves a card out of `HoldingBeastDenizens.scala` and edits `NewFoundations.scala`, the design's Status and `docs/ROADMAP.md`. If slice 2 has not merged, stop and ask.

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
  - `oathdigital.gameplay.powers.cardplay.SuitOnlyAdvisers` (trait and companion object).
  - `oathdigital.gameplay.powers.cardplay.VowOfUnionAdvisers` (final case class with `forCatalog`). Power id `denizen.vow-of-union.restriction`.
  - `oathdigital.gameplay.powers.campaign.VowOfUnion` (case object) and `VowOfUnionCard`, in `campaign/VowOfUnion.scala`. Power id `denizen.vow-of-union.campaign`.
  - Decisions `campaign.vow-of-union.direction` (`VowOfUnion.directionDecisionId`) and `campaign.vow-of-union.arrange` (`VowOfUnion.arrangeDecisionId`).
  - `WarbandArrangement.oneWay`.
- **Texts, exactly:**
  - Direction heading: "Vow of Union: move warbands between your board and the sites you rule in your region".
  - Direction options, in this order: `Button("to-board")` "Move warbands to your board", `Button("to-sites")` "Move warbands to your sites", `Button("stay")` "Move none".
  - Reasons: "No warbands on your board" (to your sites), and "The Campaign's target keeps one warband" (to your board).
  - Arrangement headings: "Vow of Union: move warbands from the sites you rule in your region to your board" and "Vow of Union: move warbands from your board to the sites you rule in your region". When a site keeps one, the first gains ". The Campaign's target keeps one warband". Confirm label "Move warbands".
  - Lines on the holder's own turn: "Moved 2 warbands from sites to the board." and "Moved 3 warbands from the board to sites."
  - Lines off turn: "{Blue} moved 2 warbands from sites to their board." and "{Blue} moved 3 warbands from their board to sites."
  - The bury note on a card play: "Vow of Union will bury it". Bury lines as Family Wagon's: "Buried {cards}." and "{Blue} buried {cards}."
  - Neither Campaign question posts a "Chose" line.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 10 server tests and no frontend test.
  - It changes one existing test's data: `PowerKindsCatalogSuite`'s persistent rules gain Vow of Union's two power ids.
  - Family Wagon's two suites must pass unchanged.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **When it asks.** The questions come after the Campaign's Supply is spent and before the attacker chooses a kind, a defender or targets. The Campaign then runs on.
2. **Two questions.** First a direction: "Move warbands to your board", "Move warbands to your sites" or "Move none". Then one distribution in that direction. Each slot opens on what it holds now, so confirming it unchanged moves nothing.
3. **"Your region"** is the region of the holder's pawn when the Campaign starts.
4. **When nothing is asked.** If the holder rules no site in their region, nothing is asked. If neither direction can move a warband, nothing is asked either. A direction that cannot move a warband is shown unavailable:
   - "No warbands on your board" for "to your sites";
   - "The Campaign's target keeps one warband" for "to your board" (ruling 5).
5. **The Campaign's target keeps one warband.** Take a Campaign that can only be a Conquest of the attacker's site, where the holder rules that site and is not the attacker. That site keeps one of the holder's warbands. Otherwise the site would lose its ruler, and the attacker, having spent Supply, would have nothing to attack. The engine cannot run a Campaign with no legal kind. When a Raid is also legal, the site may empty, as the card says.
   - The alternative is to let the site empty and end the Campaign with nothing to attack. That is an engine change and is out of scope.
6. **Lines.** One line when warbands moved, naming the count and the direction. It names the holder only off turn. Nothing moved, no line. Neither question posts a "Chose" line. The generic move lines stay and say which sites.
7. **The restriction half is Family Wagon's code, with Beasts and no limit lift.** It buries every adviser that is not a faceup Beast when Vow of Union arrives and whenever its holder gains one. A card play it would bury carries "Vow of Union will bury it". The holder's adviser limit is unchanged.
8. **The force question reads the moved warbands.** An attacker who holds Vow of Union may move warbands to their board and commit them in the same Campaign.

## File Structure

- **Create:**
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala`: the shared bury rule.
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala`: the restriction half.
  - `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala`: the card (Task 2), then the Campaign half (Task 3).
  - `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisersSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala`
  - `src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala`
- **Modify:**
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
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("Slice 3: Warbands at a Campaign's start")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` ("Slice 3: Warbands at a Campaign's start")

- [ ] **Step 1: Add the design's plan-time rulings.** Under "## Slice 3: Warbands at a Campaign's start", after its paragraph, add:

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
  in their region, or when neither direction can move a warband. A
  direction that cannot is shown unavailable: "No warbands on your board",
  or "The Campaign's target keeps one warband".
- **The Campaign's target keeps one warband.** When the Campaign can only
  be a Conquest of the attacker's site, and the holder rules it and is not
  the attacker, that site keeps one of the holder's warbands. Otherwise the
  attacker would have nothing to attack. When a Raid is also legal the site
  may empty.
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

- [ ] **Step 2: Extend the rulings appendix.** Replace the slice 3 section's entry with:

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
    allowed, except that when the Campaign can only be a Conquest of the
    attacker's site, which the holder rules, that site keeps one warband
    (plan-time ruling).
  - Lines: "Vow of Union: Moved 2 warbands from sites to the board." on the
    holder's turn, "Vow of Union: {Blue} moved 2 warbands from sites to
    their board." off turn, and the same for the other direction (plan-time
    ruling).
  - The restriction half buries every non-Beast adviser when the card
    arrives, and any non-Beast adviser its holder would gain, as Family
    Wagon does. A play it would bury carries "Vow of Union will bury it".
    It lifts no limit.
```

- [ ] **Step 3: Check links and commit.**

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

### Task 3: Warbands at a Campaign's start

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala`, `src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala`

**Interfaces:**
- Consumes: `VowOfUnionCard` (Task 2).
- Produces: `WarbandArrangement.oneWay(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)], floor: SiteId => Int, intoSites: Boolean, heading: String): DecisionQuery`. `VowOfUnion` with `directionDecisionId`, `arrangeDecisionId`, `toBoard`, `toSites`, `stay`, `noBoardWarbands` and `targetKeepsOne`.

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
  * they rule in their region. */
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

  test("an off-turn holder moves warbands from the sites they rule in " +
      "their region to their board, before the Campaign's targets"):
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
    assert(awaits(done, b.actor, CampaignIds.force))
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
    val done = way.answer(b.actor, VowOfUnion.arrangeDecisionId,
      arranged(b.actor -> 4, brokenPeaks -> 0))
    assertEquals(said(VowOfUnion, done.events), Vector(NoteText.Said(
      "moved.board", "Moved 3 warbands from sites to the board.",
      covers = false)))
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
      "facedown, Move none moves nothing, and an empty board's direction " +
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
    val empty = bystanderHoldsVowRulingTwoSites(onBoard = 0)
    assertEquals(campaignStarted(empty).withheld(base.actor,
      VowOfUnion.toSites),
      Some(OptionReason.Written(VowOfUnion.noBoardWarbands)))

  test("when the Campaign can only conquer a site the holder rules, that " +
      "site keeps one warband"):
    val base = board(warbands = 4, supply = 7)
    /** p2, at Broken Peaks with Vow of Union, rules Ancient City, p1's
      * site, with `count` warbands. */
    def rivalWithVowRulesOrigin(count: Int): Board =
      on(withAdviserFor(base, base.rival, vow, Orientation.FaceUp))(
        _.warbandsAt(base.origin, base.rival, count))
    val kept = campaignStarted(rivalWithVowRulesOrigin(2)).pick(base.rival,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
    assertEquals(kept.query(base.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(base.rival), 3, 4, 3),
        slotOpeningOn(DecisionOptionRef.Site(base.origin), 1, 2, 2)),
      5, Some(s"$toBoardHeading. ${VowOfUnion.targetKeepsOne}"),
      "Move warbands"))
    assertEquals(campaignStarted(rivalWithVowRulesOrigin(1))
      .withheld(base.actor, VowOfUnion.toBoard),
      Some(OptionReason.Written(VowOfUnion.targetKeepsOne)))
    // With p2's pawn at Ancient City a Raid is legal too, so it may empty.
    val raidable = on(rivalWithVowRulesOrigin(2))(
      _.pawn(base.rival, at = base.origin))
    val open = campaignStarted(raidable).pick(base.rival,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
    assertEquals(open.query(base.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(base.rival), 3, 5, 3),
        slotOpeningOn(DecisionOptionRef.Site(base.origin), 0, 2, 2)),
      5, Some(toBoardHeading), "Move warbands"))
```

Notes for this suite:
- Two things are stated, not verified at plan time. Broken Peaks and Ancient City lie in the Cradle and Buried Giant in the Provinces, as `SiegeEnginesSuite` states. `CampaignFixture.board` puts p2 and p3 at Broken Peaks. If either fact turns out false, pick sites that keep each test's meaning and say so in the report.
- `Run.options`, `Run.query` and `Run.withheld` rebuild the parked decision with the Campaign's actor, so they take `b.actor` even when the holder is asked.

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfUnionSuite"`
Expected: compile failure, `VowOfUnion` not found.

- [ ] **Step 3: Add the one-way arrangement.** In `WarbandArrangement.scala`, extend the object's doc comment with "Vow of Union arranges them in one direction, with no minimum (`oneWay`)." Then add after `query`:

```scala
  /** One rearrangement in one direction, as Vow of Union makes it: any
    * number of warbands leave `sites` for the board (`intoSites` false), or
    * leave the board for `sites` (`intoSites` true), and none need move.
    * Each slot opens on what it holds now, and its bounds keep the
    * direction. A site may empty, down to what `floor` keeps there. */
  def oneWay(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      floor: SiteId => Int, intoSites: Boolean, heading: String)
      : DecisionQuery =
    val total = board + sites.map(_._2).sum
    val kept = sites.map { case (site, _) => floor(site) }.sum
    val boardSlot =
      if intoSites then DistributeSlot(DecisionOptionRef.Player(user), 0,
        board, Some(board))
      else DistributeSlot(DecisionOptionRef.Player(user), board,
        total - kept, Some(board))
    val siteSlots = sites.map { case (site, count) =>
      if intoSites then DistributeSlot(DecisionOptionRef.Site(site), count,
        count + board, Some(count))
      else DistributeSlot(DecisionOptionRef.Site(site), floor(site), count,
        Some(count)) }
    DecisionQuery.Distribute.exactly(boardSlot +: siteSlots, total,
      Some(heading), "Move warbands")
```

`moves` already turns any answer over the same sites into moves, shrinking sites first.

- [ ] **Step 4: Add the Campaign half.** In `campaign/VowOfUnion.scala`, widen the imports to:

```scala
import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.campaign.{CampaignProcedure,
  CampaignSetup}
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
  * is asked without one, or when neither direction can move a warband. A
  * direction that cannot is shown unavailable. When the Campaign can only
  * be a Conquest of the attacker's site, and the holder rules it and is not
  * the attacker, that site keeps one warband, so the attacker still has
  * something to attack.
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
  val targetKeepsOne: String = "The Campaign's target keeps one warband"
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
    val floor = floors(ready, attacker, holder)
    val leaving = Option.unless(sites.exists { case (site, count) =>
      count > floor(site) })(targetKeepsOne)
    val arriving = Option.when(board == 0)(noBoardWarbands)
    if sites.isEmpty || (leaving.nonEmpty && arriving.nonEmpty) then
      Vector.empty
    else Vector(
      Decide(directionDecisionId, holder, DecisionQuery.ChooseOne(Vector(
        shown(toBoard, "Move warbands to your board", leaving),
        shown(toSites, "Move warbands to your sites", arriving),
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
      val floor = floors(ready, attacker, holder)
      val keeps = sites.exists { case (site, _) => floor(site) > 0 }
      Vector(
        Decide(arrangeDecisionId, holder, WarbandArrangement.oneWay(holder,
          board, sites, floor, intoSites, heading(intoSites, keeps))),
        BuildOps((live, answers) => PowerAnswers.distribution(answers,
          arrangeDecisionId).toRight(PowerAnswers.missing(arrangeDecisionId))
          .flatMap(rows => WarbandArrangement.moves(live, holder,
            region(live, holder), rows))),
        Note(id, movedNote(_, attacker, holder)))

  private def heading(intoSites: Boolean, keeps: Boolean): String =
    if intoSites then "Vow of Union: move warbands from your board to the " +
      "sites you rule in your region"
    else "Vow of Union: move warbands from the sites you rule in your " +
      "region to your board" + (if keeps then s". $targetKeepsOne" else "")

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

  /** What each site keeps when warbands leave it. The attacker's site keeps
    * one when the Campaign can only be a Conquest of it and the holder is
    * not the attacker; the holder rules it, or it has no slot. */
  private def floors(ready: ReadyGame, attacker: PlayerId,
      holder: PlayerId): SiteId => Int =
    val target =
      if holder != attacker && CampaignSetup.legalKinds(ready, attacker) ==
          Vector(CampaignKind.Conquest) then
        CampaignSetup.originOf(ready, attacker)
      else None
    site => if target.contains(site) then 1 else 0

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

The suite is the first test of a non-attacker's question at the start of a Campaign. If the walker refuses it, or the force question does not read the moved warbands, stop and report. Do not change the walker or `CampaignProcedure` without asking.

- [ ] **Step 7: Write the log suite.** In `LogScripts.scala`, drop `private` from `raidAnswers`. Then create `VowOfUnionLogSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.powers.campaign.VowOfUnion
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer}
import oathdigital.testkit.{CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, raidAnswers, texts}

/** How Vow of Union reads in the Game Log: an off-turn holder is named, and
  * neither of its questions posts a "Chose" line. */
class VowOfUnionLogSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Union")
  private val buriedGiant = CatalogNames.site("Buried Giant")

  /** p1 raids p2 at Ancient City. p3, at Buried Giant with Vow of Union,
    * rules it with 2 warbands and moves both to their board when the
    * Campaign starts. The log as p2 sees it. */
  private def holderMovedTwoWarbandsToTheirBoard: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, buriedGiant).adviser(p3, vow).warbandsAt(buriedGiant, p3, 2)
      .banditsRefilled
    val (service, act) = atTable("vow-of-union", table, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == VowOfUnion.directionDecisionId =>
        ChooseOneAnswer(VowOfUnion.toBoard)
      case park if park.decisionId == VowOfUnion.arrangeDecisionId =>
        DistributeAnswer(Vector(
          DistributeAmount(DecisionOptionRef.Player(p3), 5),
          DistributeAmount(DecisionOptionRef.Site(buriedGiant), 0)))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    texts(format(Script("vow-of-union", service, p1), Some(p2)))

  test("its line names the off-turn holder, with no Chose line for either " +
      "question"):
    val log = holderMovedTwoWarbandsToTheirBoard
    assert(log.contains(s"Vow of Union: ${playerLabel(p3)} moved 2 " +
      "warbands from sites to their board."), log.mkString("\n"))
    assert(!log.exists(_.startsWith(s"${playerLabel(p3)} chose")),
      log.mkString("\n"))
```

p3's board starts with 3 warbands, so the answer gives the board 5. If the Raid script asks something `raidAnswers` does not answer, add that answer to the partial function above and say so in the report.

- [ ] **Step 8: Run the log suite.**

Run: `./sbtw "testOnly oathdigital.application.gamelog.VowOfUnionLogSuite oathdigital.application.gamelog.GameLogCampaignSuite"`
Expected: PASS, with 1 new test.

- [ ] **Step 9: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala src/main/scala/oathdigital/gameplay/powers/campaign/VowOfUnion.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/VowOfUnionLogSuite.scala
git commit -m "feat(batch8): Vow of Union moves warbands at the start of any Campaign"
```

---

### Task 4: Close the slice

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

Expected: all pass. The server count is the baseline plus 10; the frontend count is unchanged.

- [ ] **Step 2: Update the docs.**
  - In the design's Status, "Slices 1 and 2 are done." becomes "Slices 1 to 3 are done."
  - In `docs/ROADMAP.md`, check `- [ ] **3. Warbands at a Campaign's start:** Vow of Union.` and change the Now line to "**Phase - Catalog batch 8** is under way: slices 1 to 3 are done, and slice 4 is next."

- [ ] **Step 3: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 3 done"
```

- [ ] **Step 4: Report:**
  - the baseline and final test counts;
  - any fixture, card or site you swapped;
  - whether the walker or `CampaignProcedure` needed anything beyond this plan.
