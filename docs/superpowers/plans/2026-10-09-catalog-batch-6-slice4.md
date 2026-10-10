# Catalog Batch 6, Slice 4 (Search Draws) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the last two selected Search modifiers of catalog batch 6:

- Spinning Bee (232) declares a suit and draws from the actor's own discard pile until a card of that suit is drawn.
- Land Warden (130) lets a Search keep two cards, and makes one of them go to a site.

**Architecture:**

- **Spinning Bee replaces the source decision.** Its Transform at `SearchSourceSelection` (slice 3's window) swaps `search.source` for "Declare a suit".
  - `SearchProcedure.sourcePicked` reads the source answer. With no answer, the Search draws from the actor's own region's discard pile. Only a power that removes the decision gets there.
  - Its Transform at `SearchBeforeDraw` redraws the pile through the first card of the declared suit, writes its line, and appends a node that discards every drawn card when none matches.
  - A placement with no card held plays nothing.
- **Spinning Bee keeps out the other modifiers** with the modifier walker's two tools: its own `offerReason`, and an `OptionRestriction` at the modifier window once it is picked.
- **Land Warden widens the keep decision and limits a play.**
  - A new window, `SearchKeep`, sits on `search.cards`. Land Warden's Transform there lets the Keep section hold two cards.
  - The placement plays the kept cards in the order kept. A later card is planned in its own `Branch`, from the state the earlier plays left.
  - Card play gains `PlacementLimit`, a general and composable rule in `CardPlayRules`. A play a limit refuses is shown unavailable with the limit's reason. When the limits leave no play, the discard pile stays open.
  - Land Warden sets its limit on the second card's play through `SearchPlayAdviser`, as Crop Rotation adjusts card play.
- **The Game Log.** Spinning Bee writes a power line through two new note arguments, `NoteArg.RegionName` and `NoteArg.SuitName`. The Search line reads "kept none" when every drawn card was discarded unasked.
- **The frontend** gives the keep-one-or-two partition its own sentence and an order line.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`: "Goal and scope", "The bar", "Slice 4: Search draws" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`: "Rules that apply to the whole batch" and "Slice 4: Search draws".
- `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`: rulings 1 to 7. They cover the modifier decision, `offerReason` and option restrictions at the modifier window.
- `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`: the source decision and its wording.

Task 1 amends the first two with the plan-time rulings, so read them again after Task 1.

**Starts from:** `main` at `3983af1f` or later. Catalog batch 6 slice 3 and modifier walker slice 3 are merged there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
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
- **Texts, exactly:**
  - Spinning Bee's line: "Spinning Bee: Drawing from the Cradle discard until Hearth card is drawn". No full stop.
  - The Search line with nothing kept: "Drew {cards} from the Cradle discard and kept none".
  - Spinning Bee is unavailable once another modifier is picked: "Cannot be used with other Search modifiers".
  - Every other modifier is unavailable once Spinning Bee is picked: "Spinning Bee: You cannot use other Search modifiers."
  - Spinning Bee is unavailable while the actor's pile is empty: "The Cradle discard pile is empty".
  - The suit decision's heading: "Declare a suit".
  - Land Warden's keep heading: "Choose one or two cards to keep".
  - Land Warden's reason: "Land Warden: one card must go to a site".
  - The keep panel: "Keep one or two; discard the rest." and, on a Keep zone holding two cards, "They are played in this order."
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 17 server tests and 2 frontend tests.
  - It changes one existing test: `PowerNoteWireSuite`'s round trip of every argument kind takes the two new kinds.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled rulings 1 to 4 on 2026-10-09. Task 1 writes all of them into the spec.

1. **The skipped source decision.** Spinning Bee's Transform at `SearchSourceSelection` replaces `search.source` with "Declare a suit". A Search past that point with no source answer draws from the actor's own region's discard pile (`SearchProcedure.sourcePicked`).
2. **Spinning Bee's Game Log.** Its own line, "Spinning Bee: Drawing from the Cradle discard until Hearth card is drawn", comes before the Search line. With no match, the Search line reads "Drew {cards} from the Cradle discard and kept none".
3. **Land Warden's seam.** The product owner asked for the design that holds up longest. The plan uses a card-play rule, not an `OptionRestriction` on the placement decision, because:
   - `CardPlayRules` is already the one place a power changes where a card may go. Card play reads nothing else and never names a power.
   - "When no site has room" depends on every slot the card has. Only `CardSlots`, which builds them all, can see that. A restriction judges one option at a time, so it would have to rebuild the slots.
   - The rule is general: a `PlacementLimit` names the placements it leaves open and the reason for the rest, and limits compose. A future "must go to a site" or "may not be an adviser" card reuses it.
4. **The keep panel.** A Keep section that holds one or two cards reads "Keep one or two; discard the rest.". While it holds two, it says "They are played in this order."
5. **Spinning Bee's texts** are the ones in Global Constraints.
   - The note on the other modifiers follows the batch rule "card name, colon, sentence".
   - The two reasons on Spinning Bee itself follow slice 3's "Cannot be used with Mushrooms" and the source decision's "The Cradle discard pile is empty".
6. **The suit decision.** Its id is `search.suit`. It sits where the source decision sat, before the cost, and offers the six favor banks in `Suit.all` order: Discord, Arcane, Order, Hearth, Beast, Nomad.
7. **No match.** The whole pile is drawn, then every drawn card is discarded in draw order. They go where a Search discards the cards it does not keep: the pile of the region after the actor's.
8. **A Vision has no suit**, so it never matches.
9. **The draw port.** `GameApplicationService` checks the prepared draw at the source answer. A Spinning Bee Search has no source answer, so its draw is not checked against the port.
10. **Land Warden's order** is the order of the Keep section in the answer. The frontend submits the order in which the cards entered Keep, and a drag before another card reorders them.
11. **Land Warden's power id** becomes `denizen.land-warden` (rulings appendix).
12. **Facedown-adviser play** shares the Search modifier window. Both cards are listed there and do nothing, as Augury does. This is unchanged here.

## File map

**Spinning Bee (Task 2):**
- `src/main/scala/oathdigital/gameplay/actions/Search.scala`: `SearchRules.pile`.
- `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`: `sourcePicked`, and a placement that plays nothing when no card is held.
- `src/main/scala/oathdigital/model/PowerNotes.scala`: `NoteArg.RegionName` and `NoteArg.SuitName`.
- `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` and `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`: both new note arguments.
- `src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala`: new. The card object moves out of `catalog/holding/HoldingHearthDenizens.scala`.
- Registration: `ActionModifiers.scala` and the `powers.search` import in `NewFoundations.scala`.
- Tests:
  - New: `SpinningBeeSuite`.
  - Extended: `SearchSuite`.
  - Changed: `PowerNoteWireSuite`, plus the testkit's `NoteText`.

**Land Warden (Task 3):**
- `src/main/scala/oathdigital/model/PowerWindow.scala`: `SearchKeep`.
- `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`: `PlacementLimit`, `limitPlacements` and `refusal`.
- `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`: the limits applied.
- `SearchProcedure.scala`: the keep window, `kept`, and the kept cards played in order.
- `src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala`: new. The card object moves out of holding.
- Registration as for Task 2.
- Tests: `CardSlotsSuite`, plus the new `LandWardenSuite`.

**Game Log (Task 4):** `ActionLines.search` gets "kept none". The new `GameLogSearchKeepSuite` tests it.

**Frontend (Task 5):** `WalkerPanelSupport.scala` and `PartitionPanelRenderSuite.scala`.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`

- [ ] **Step 1: Amend the design**

In the design:

1. Replace the **Status** paragraph with:

```markdown
**Status:** designed 2026-10-09. Slices 1 to 3 are done. Slice 4 is
planned against [modifier selection on the walker](2026-10-09-modifier-walker-design.md);
its plan-time rulings are under "Slice 4". The per-card rulings are in
[the rulings appendix](2026-10-09-catalog-batch-6-rulings.md).
```

2. In "The bar", insert before the bullet that starts `- **Modifiers that restrict or warn about other modifiers**`:

```markdown
- **A source decision a power removes** (slice 4, approved at plan
  time). With no source answer, a Search draws from the actor's own
  region's discard pile (`SearchProcedure.sourcePicked`). Only Spinning
  Bee removes the decision.
- **A keep window, and kept cards played in order** (slice 4).
  `PowerWindow.SearchKeep` on the `search.cards` decision. The placement
  plays each kept card in the order kept, a later one planned from the
  state the earlier plays left. A Search that holds no drawn card plays
  nothing.
- **Placement limits** (slice 4, approved at plan time).
  `CardPlayRules.placementLimits`: a play a limit refuses is shown
  unavailable with the limit's reason. When the limits leave no play, the
  discard pile stays open, since a card that cannot be played is
  discarded.
- **Two note arguments** (slice 4). `NoteArg.RegionName` ("Cradle") and
  `NoteArg.SuitName` ("Hearth"), for Spinning Bee's line.
- **The keep panel** (slice 4, approved at plan time). A Keep section
  that holds one or two cards reads "Keep one or two; discard the rest."
  and, while it holds two, "They are played in this order."
```

3. In "## Slice 4: Search draws":
   - Delete the line `Re-read against the modifier walker design before planning.` and the blank line after it.
   - Replace the last paragraph, from `Skipping the source decision and widening the Keep section need a hook in` to `The modifier walker design decides where it sits.`, with:

```markdown
### Plan-time rulings

- **The skipped source decision.** Spinning Bee's Transform at
  `SearchSourceSelection` replaces `search.source` with "Declare a suit"
  (`search.suit`), a choose-one of the six favor banks in suit order. With
  no source answer, the Search draws from the actor's own region's
  discard pile. A Spinning Bee Search therefore has no source answer, and
  its draw is not checked against the draw port.
- **The exclusion.** Once Spinning Bee is picked, every other Search
  modifier is unavailable through its `OptionRestriction`, with the note
  "Spinning Bee: You cannot use other Search modifiers.". Spinning Bee
  itself is unavailable once another modifier is picked ("Cannot be used
  with other Search modifiers"), and while the actor's own pile is empty
  ("The Cradle discard pile is empty").
- **No match.** The whole pile is drawn, then every drawn card is
  discarded in draw order, where a Search discards the cards it does not
  keep. A Vision has no suit and never matches.
- **The Game Log.** Spinning Bee's line, "Spinning Bee: Drawing from
  the Cradle discard until Hearth card is drawn", comes before the Search
  line. With no match, the Search line ends "and kept none".
- **Land Warden's seam is card play.** `CardPlayRules` is already where a
  power changes where a card may go, and only `CardSlots` sees every slot,
  which "no site has room" needs. Land Warden sets a `PlacementLimit` on
  the second card's play through `SearchPlayAdviser`. Its keep decision's
  heading reads "Choose one or two cards to keep".
- **The order kept** is the order of the Keep section in the answer.
```

4. In "## Seams used for the first time", append:

```markdown
- A Transform that replaces the source decision (Spinning Bee).
- A placement limit, and a keep decision that holds two cards (Land
  Warden).
```

- [ ] **Step 2: Amend the rulings appendix**

In the rulings appendix, under "## Slice 4: Search draws":

1. In the Spinning Bee entry, replace the sub-bullet `- It excludes every other Search modifier, both ways.` with:

```markdown
  - It excludes every other Search modifier, both ways. Picked, it makes
    every other one unavailable with "Spinning Bee: You cannot use other
    Search modifiers.". It is unavailable once another is picked ("Cannot
    be used with other Search modifiers"), and while the actor's own pile
    is empty ("The Cradle discard pile is empty").
```

2. Append to the Spinning Bee entry, after its last sub-bullet:

```markdown
  - Its line, "Spinning Bee: Drawing from the Cradle discard until Hearth
    card is drawn", comes before the Search line. With no match, the
    Search line reads "Drew {cards} from the Cradle discard and kept
    none".
```

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md
git commit -m "docs(batch6): record slice 4 plan-time rulings"
```

---

### Task 2: Spinning Bee

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/Search.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` (`encodeNoteArg` near line 129, `decodeNoteArg` near line 160)
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` (`argument`, near line 83)
- Create: `src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `SpinningBeeCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.search` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala` (`plain`)
- Test: `src/test/scala/oathdigital/gameplay/SearchSuite.scala`, `src/test/scala/oathdigital/serialization/PowerNoteWireSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/search/SpinningBeeSuite.scala`

**Interfaces:**
- Consumes, from slice 3: `PowerWindow.SearchSourceSelection`, `DrawExtension.sourceOf`, `DrawExtension.redrawn`, and the test helpers `ShownOptions.atParkedDecision` and `ShownOptions.optionFor`.
- Produces:
  - `SearchRules.pile(ready: ReadyGame, source: SearchSource): Vector[WorldCardId]`, the whole pile, top first.
  - `SearchProcedure.sourcePicked(pending: PendingTree, origin: Region): Either[OathViolation, SearchSource]`
  - `NoteArg.RegionName(region: Region)` and `NoteArg.SuitName(suit: Suit)`, on the wire as kinds `"region"` and `"suit"`.
  - `SpinningBee.id`, `SpinningBee.suitDecisionId` (`"search.suit"`), `SpinningBee.heading`, `SpinningBee.notWithOthers`, `SpinningBee.alone`, `SpinningBee.drawing`, `SpinningBee.declared(answered)`, and `SpinningBeeCard` in `oathdigital.gameplay.powers.search`.

- [ ] **Step 1: Write the failing tests**

In `SearchSuite`, add at the end of the class:

```scala
  test("a pile lists its cards top first"):
    val state = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant")
      .discarded(Region.Hinterland, "Defame", "Scryer").ready
    assertEquals(SearchRules.pile(state, SearchSource.WorldDeck).take(2),
      Vector("Threatening Roar", "Fae Merchant")
        .map(CatalogNames.denizen(_)))
    assertEquals(SearchRules.pile(state,
      SearchSource.RegionalDiscard(Region.Hinterland)).take(2),
      Vector("Scryer", "Defame").map(CatalogNames.denizen(_)))
```

In `PowerNoteWireSuite`, test "a power note's card list and banner arguments round trip", add two arguments after `NoteArg.Pile(SearchSource.RegionalDiscard(Region.Provinces))`:

```scala
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Provinces)),
      NoteArg.RegionName(Region.Hinterland), NoteArg.SuitName(Suit.Nomad))
```

(the closing parenthesis moves from the `Pile` line to the new one).

Create `src/test/scala/oathdigital/gameplay/powers/search/SpinningBeeSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Spinning Bee (card 232): declare a suit, then draw from the actor's own
  * discard pile until a card of that suit is drawn. No other Search
  * modifier may be used with it (catalog batch 6, slice 4). */
class SpinningBeeSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val spinningBee = CatalogNames.denizen("Spinning Bee")
  private val augury = CatalogNames.denizen("Augury")
  private val modifiers = Vector(SpinningBee.id)
  private val modifierDecision =
    ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
  // The Cradle discard pile, bottom first: Hearth, Order, Discord.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val defame = CatalogNames.denizen("Defame")

  /** p1 at Ancient City (Cradle) holding Spinning Bee faceup, with 5
    * Supply. The Cradle discard pile holds Homesteaders under Wrestlers
    * under Defame unless `emptyPile`. */
  private def holdingBee(emptyPile: Boolean = false): Table =
    val table = Table.start.pawn(p1, "Ancient City").adviser(p1, spinningBee)
      .supply(p1, 5)
    if emptyPile then table
    else table.discarded(Region.Cradle, homesteaders, wrestlers, defame)

  /** The decision `state` is parked on, as the projector rebuilds it. */
  private def parkedDecide(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  /** p1 starts a Search with Spinning Bee picked. */
  private def picked(table: Table)(using munit.Location): OathTransition =
    accepted(ModifierPlay.started(rules, Ready(table.ready), ActionRef.Search,
      p1, PowerWindow.SearchModifierSelection, modifiers))

  /** p1 starts a Search with Spinning Bee picked, and declares `suit`. */
  private def declared(table: Table, suit: Suit)(using munit.Location)
      : OathTransition =
    accepted(rules.resolveWalker(picked(table).state, p1,
      SpinningBee.suitDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))))

  /** How `power`'s option is shown at the modifier decision once p1 has
    * picked `first`, as (state, reason). */
  private def shownAfterPicking(ready: ReadyGame, first: PowerId,
      power: DenizenId)(using munit.Location)
      : Option[(String, Option[String])] =
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val chosen = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecision, ModifierPlay.pick(first)))
    ShownOptions.optionFor(ShownOptions.atParkedDecision(chosen.state),
      DecisionOptionRef.Denizen(power))
      .map(option => (option.state, option.reason))

  test("picked, the source decision gives way to declaring one of the six " +
      "suits"):
    val decide = parkedDecide(picked(holdingBee()).state)
    assertEquals(decide.decisionId, SpinningBee.suitDecisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Suit.all.map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(SpinningBee.heading)))

  test("it draws its own pile from the top through the first card of the " +
      "suit, for 2 Supply, and the keep choice follows"):
    val searched = declared(holdingBee(), Suit.Order)
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(defame, wrestlers))
    assertEquals(Look(after).supply(p1), 5 - 2)
    assertEquals(Look(after).discardPile(Region.Cradle),
      Vector(homesteaders))
    assertEquals(parkedDecide(searched.state).decisionId,
      SearchProcedure.cardDecisionId)

  test("with no card of the suit, every drawn card is discarded and " +
      "nothing is kept"):
    val searched = declared(holdingBee(), Suit.Nomad)
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands.getOrElse(p1,
      Vector.empty), Vector.empty)
    assertEquals(Look(after).discardPile(Region.Cradle), Vector.empty)
    // Discarded in draw order, to the pile after the actor's region's.
    assertEquals(Look(after).discardPile(Region.Provinces).takeRight(3),
      Vector(defame, wrestlers, homesteaders))
    assert(searched.events.exists(_.isInstanceOf[WalkerCompleted]),
      searched.events)

  test("picked, it makes every other Search modifier unavailable"):
    val ready = holdingBee().adviser(p1, augury).ready
    assertEquals(shownAfterPicking(ready, SpinningBee.id, augury),
      Some(("unavailable",
        Some("Spinning Bee: You cannot use other Search modifiers."))))

  test("it is unavailable once another modifier is picked"):
    val ready = holdingBee().adviser(p1, augury).ready
    assertEquals(shownAfterPicking(ready, Augury.id, spinningBee),
      Some(("unavailable", Some(SpinningBee.notWithOthers))))

  test("it is unavailable while the actor's own discard pile is empty"):
    // Augury makes the modifier decision ask; Spinning Bee alone could not.
    val ready = holdingBee(emptyPile = true).adviser(p1, augury).ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    assertEquals(ShownOptions.optionFor(
      ShownOptions.atParkedDecision(begun.state),
      DecisionOptionRef.Denizen(spinningBee))
      .map(option => (option.state, option.reason)),
      Some(("unavailable", Some("The Cradle discard pile is empty"))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchSuite oathdigital.serialization.PowerNoteWireSuite oathdigital.gameplay.powers.search.SpinningBeeSuite"`
Expected: the test sources fail to compile: `pile is not a member of object SearchRules`, `RegionName is not a member of object NoteArg`, `not found: SpinningBee`.

- [ ] **Step 3: Add the pile rule**

In `Search.scala`, add before `draw`, and make `draw` read it:

```scala
  /** Every card of `source`, top first: the world deck head-as-top, a
    * discard pile last-as-top. */
  def pile(ready: ReadyGame, source: SearchSource): Vector[WorldCardId] =
    source match
      case SearchSource.WorldDeck => ready.game.current.commonCards.worldDeck
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse
```

In `draw`, replace the `val topFirst = source match ...` block (three lines) with:

```scala
    val topFirst = pile(ready, source)
```

- [ ] **Step 4: Let the source answer be missing, and nothing be held**

In `SearchProcedure.scala`:

1. In the class doc's tree, after the line `*                                      // window = SearchSourceSelection`, add:
   ```
     *                                      // (Spinning Bee replaces it, and
     *                                      // the actor's own pile is searched)
   ```
2. Add after `sourceOf`:
   ```scala
     /** The pile the walk searches: the source answer's, or, when a power
       * removed the source decision (Spinning Bee), the actor's own
       * region's discard pile (catalog batch 6, slice 4). */
     def sourcePicked(pending: PendingTree, origin: Region)
         : Either[OathViolation, SearchSource] =
       pending.answered.collectFirst {
         case Answered(`sourceDecisionId`, answer, _) => sourceOf(answer)
       }.getOrElse(Right(SearchSource.RegionalDiscard(origin)))
   ```
3. In `afterModifiers`, replace the `search` Branch with:
   ```scala
       // The walk reaches this after the source answer, or after the
       // decision a power put in its place.
       val search = Branch((_, pending) => Vector(
         sourcePicked(pending, origin).fold(fail, body(catalog, actor, _))))
   ```
4. In `body`'s `placement`, replace the `case Right((kept, discarded)) =>` arm's last line, `Vector(removeOthers, cardTree)`, and the `val cardTree = ...` before it, so the arm ends:
   ```scala
             kept.headOption.fold(Vector.empty[Operation]) { first =>
               val cardTree = CardPlayProcedure.unchecked(catalog, ready,
                 actor, first, CardPlayProcedure.Origin.TemporaryHand,
                 ModifierPicks.cards(pending.answered))
               Vector(removeOthers, cardTree)
             }
   ```
   and change `selected` so that it returns the kept cards as a `Vector` and accepts none:
   ```scala
     /** The cards kept, in the order kept, and the cards discarded. One card
       * drawn is kept without a question. No card held keeps nothing:
       * Spinning Bee drew no match and discarded every card. */
     private def selected(ready: ReadyGame, pending: PendingTree,
         actor: PlayerId): Either[OathViolation,
           (Vector[WorldCardId], Vector[WorldCardId])] =
       val cards = selectedCards(ready, pending, actor)
       if cards.size < 2 then Right(cards -> Vector.empty)
       else answered(pending).toRight(OathViolation.SearchChoiceMismatch(
         "Search card selection is missing")).flatMap { placements =>
         val kept = placements.filter(_.sectionKey == keepKey)
           .flatMap(value => card(value.option))
         val discarded = placements.filter(_.sectionKey == discardKey)
           .flatMap(value => card(value.option))
         kept match
           case Vector(_) => Right(kept -> discarded)
           case _ => Left(OathViolation.SearchChoiceMismatch(
             "Search must keep exactly one card"))
       }
   ```
   Task 3 widens the `kept match` to two cards.

- [ ] **Step 5: Add the note arguments**

In `PowerNotes.scala`, inside `object NoteArg`, after `Pile`:

```scala
  /** A region by its name, "Cradle". */
  final case class RegionName(region: Region) extends NoteArg
  /** A suit by its name, "Hearth" (Spinning Bee's declared suit). */
  final case class SuitName(suit: Suit) extends NoteArg
```

In `WalkerEventCodec.scala`, `encodeNoteArg`, after the `Pile` case:

```scala
    case NoteArg.RegionName(region) => ujson.Obj("kind" -> "region",
      "region" -> region.key)
    case NoteArg.SuitName(suit) => ujson.Obj("kind" -> "suit",
      "suit" -> suit.key)
```

In `decodeNoteArg`, before `case other =>`:

```scala
    case "region" => decodeRegion(value("region").str, s"$path.region")
      .map(NoteArg.RegionName.apply)
    case "suit" => decodeSuit(value("suit").str, s"$path.suit")
      .map(NoteArg.SuitName.apply)
```

In `PowerLines.scala`, `argument`, after the `Pile` case:

```scala
    case NoteArg.RegionName(region) => Vector(Text(region.toString))
    case NoteArg.SuitName(suit) => Vector(Text(suit.toString))
```

In the testkit's `NoteText.scala`, `plain`, after the `Pile` case:

```scala
    case NoteArg.RegionName(region) => region.toString
    case NoteArg.SuitName(suit) => suit.toString
```

- [ ] **Step 6: Write Spinning Bee**

Delete `SpinningBeeCard` (the `object SpinningBeeCard ...` block of seven lines and the blank line after it) from `HoldingHearthDenizens.scala`. The file's imports stay: its other cards still use each of them.

Create `src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SpinningBeeCard extends Denizen(DenizenId("232"), "Spinning Bee", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.spinning-bee"),
    persistent = false, cost = Cost.free,
    text = "Declare a suit. Draw from your discard pile until you draw " +
      "a matching card. If you draw none, discard them all. You " +
      "**cannot** use other Search modifiers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spinning Bee (card 232), a free selected Search modifier (catalog batch
  * 6, slice 4):
  *
  *  - Once picked, every other Search modifier is unavailable, with its
  *    note. It is unavailable itself once another is picked, and while the
  *    actor's own discard pile is empty.
  *  - The source decision gives way to "Declare a suit", a choose-one of
  *    the six favor banks. With no source answer, the Search draws from the
  *    actor's own region's discard pile at the usual 2 Supply
  *    (`SearchProcedure.sourcePicked`).
  *  - The draw takes the pile from the top through the first card of the
  *    declared suit. A Vision has no suit. The keep choice then runs over
  *    the drawn cards as usual.
  *  - With no match it draws the whole pile, and every drawn card is
  *    discarded in draw order, where a Search discards the cards it does
  *    not keep. Nothing is kept.
  *
  * Its line, "Drawing from the Cradle discard until Hearth card is drawn",
  * comes before the draw.
  */
final case class SpinningBee private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SpinningBeeCard.id
  def id: PowerId = SpinningBee.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def noteKeys: Vector[NoteKey] =
    Vector(SpinningBee.alone, SpinningBee.drawing)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchModifierSelection -> Vector(OptionRestriction(
      (ctx, ref) => Option.when(excludes(ctx, ref))(
        OathViolation.InvalidEventOrder(
          "Spinning Bee keeps out every other Search modifier")),
      (ctx, ref) => Option.when(excludes(ctx, ref))(
        SpinningBee.alone(PowerSourceRef.Card(cardId))))),
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      operations.map {
        case decide: Decide
            if decide.decisionId == SearchProcedure.sourceDecisionId =>
          SpinningBee.declaration(ctx.activePlayer)
        case other => other
      })),
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      operations.flatMap {
        case node: BuildOps
            if node.window.contains(PowerWindow.SearchBeforeDraw) =>
          Vector(line(ctx.activePlayer),
            node.copy(build = (ready, pending) => node.build(ready, pending)
              .flatMap(throughMatch(ready, pending))),
            discardedUnlessMatched(ctx.activePlayer))
        case other => Vector(other)
      })))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    if ModifierPicks.cards(ctx.answered).nonEmpty then
      Some(OfferReason.Unavailable(SpinningBee.notWithOthers))
    else SearchProcedure.actorRegion(ctx.state, ctx.activePlayer).toOption
      .map(SearchSource.RegionalDiscard(_))
      .filter(SearchRules.pile(ctx.state, _).isEmpty)
      .map(pile => OfferReason.Unavailable(
        s"The ${SearchSource.name(pile)} is empty"))

  /** Once picked, it keeps out every option but its own and "Start". */
  private def excludes(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    ModifierPicks.isPicked(ctx.answered, ctx.window, cardId) &&
      ref != ModifierPicks.start &&
      !ModifierPicks.cardRef(cardId).contains(ref)

  private def matches(suit: Suit)(card: WorldCardId): Boolean =
    catalog.suitOf(card).contains(suit)

  /** The draw taken again from the top, through the first card of the
    * declared suit, or the whole pile when none matches. */
  private def throughMatch(ready: ReadyGame, pending: PendingTree)(
      operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match
      case None => Right(operations)
      case Some(draw) =>
        for
          source <- DrawExtension.sourceOf(draw.source)
          suit <- SpinningBee.declared(pending.answered).toRight(
            OathViolation.InvalidEventOrder(
              "Spinning Bee draws once a suit is declared"))
        yield
          val pile = SearchRules.pile(ready, source)
          val index = pile.indexWhere(matches(suit))
          DrawExtension.redrawn(draw, source,
            if index < 0 then pile else pile.take(index + 1))

  /** Every drawn card discarded, in draw order, when none is of the
    * declared suit. */
  private def discardedUnlessMatched(actor: PlayerId): Operation =
    BuildOps((ready, pending) => {
      val hand = ready.game.current.temporaryHands
        .getOrElse(actor, Vector.empty)
      SpinningBee.declared(pending.answered) match
        case Some(suit) if hand.nonEmpty && !hand.exists(matches(suit)) =>
          hand.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
              Right(Vector.empty)) { (done, card) =>
            for
              previous <- done
              operations <- CardPlay.operations(catalog, ready, actor, card,
                SearchPlacement.Discard, CardPlay.Origin.TemporaryHand)
            yield previous ++ operations
          }
        case _ => Right(Vector.empty)
    })

  /** "Drawing from the Cradle discard until Hearth card is drawn". */
  private def line(actor: PlayerId): Note = Note(id, states => for
    suit <- SpinningBee.declared(states.answered)
    region <- SearchProcedure.actorRegion(states.now, actor).toOption
  yield SpinningBee.drawing(PowerSourceRef.Card(cardId),
    NoteArg.RegionName(region), NoteArg.SuitName(suit)))

object SpinningBee:
  val id: PowerId = SpinningBeeCard.power.id
  /** The decision that takes the source decision's place. */
  val suitDecisionId: String = "search.suit"
  val heading: String = "Declare a suit"
  /** Why Spinning Bee is unavailable once another modifier is picked. */
  val notWithOthers: String = "Cannot be used with other Search modifiers"
  /** "You cannot use other Search modifiers.": why every other modifier
    * is unavailable once Spinning Bee is picked. */
  val alone: NoteKey = NoteKey("alone", Vector(
    NotePart.Text("You cannot use other Search modifiers.")))
  /** "Drawing from the {region} discard until {suit} card is drawn". */
  val drawing: NoteKey = NoteKey("drawing", Vector(
    NotePart.Text("Drawing from the "), NotePart.Arg(0),
    NotePart.Text(" discard until "), NotePart.Arg(1),
    NotePart.Text(" card is drawn")))

  /** "Declare a suit", asked of `actor`: the six favor banks, in suit
    * order. */
  def declaration(actor: PlayerId): Decide = Decide(suitDecisionId, actor,
    DecisionQuery.ChooseOne(Suit.all.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(heading)))

  /** The suit declared in `answered`. */
  def declared(answered: Vector[Answered]): Option[Suit] =
    answered.collectFirst {
      case Answered(`suitDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(suit)), _) => suit
    }

  def forCatalog(catalog: ExecutableCatalog): SpinningBee =
    new SpinningBee(catalog)
```

- [ ] **Step 7: Register it**

- In `ActionModifiers.scala`, add `SpinningBee` to the `powers.search` import, in alphabetical order. Add `Vector(SpinningBee.forCatalog(catalog)) ++` after the `PaintedTrumpet` line.
- In `NewFoundations.scala`, add `SpinningBeeCard` to the `powers.search` import, in alphabetical order. The card list's `SpinningBeeCard,` entry stays as it is.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchSuite oathdigital.serialization.PowerNoteWireSuite oathdigital.gameplay.powers.search.*"`
Expected: PASS.

Then run: `./sbtw test`
Expected: PASS, with 7 tests more than the baseline.

If a suite elsewhere now fails, check two things:
- `SearchFixture.anyPlainDenizensOf(Suit.Hearth)` no longer returns Spinning Bee, since it now has a walker power. Name another card in that suite.
- A suite that holds Spinning Bee faceup and starts a Search directly now parks on `search.modifiers`. Swap its card for "Wrestlers".

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/Search.scala src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala src/main/scala/oathdigital/model/PowerNotes.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/main/scala/oathdigital/application/gamelog/PowerLines.scala src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/NoteText.scala src/test/scala/oathdigital/gameplay/SearchSuite.scala src/test/scala/oathdigital/serialization/PowerNoteWireSuite.scala src/test/scala/oathdigital/gameplay/powers/search/SpinningBeeSuite.scala
git commit -m "feat(search): Spinning Bee declares a suit and draws its own pile until it matches"
```

---

### Task 3: Land Warden

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (after `SearchSourceSelection`, near line 71)
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala` (`slots`, near line 62)
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `LandWardenCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.search` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/search/LandWardenSuite.scala`

**Interfaces:**
- Consumes: Task 2's `selected`, which returns the kept cards as a `Vector`.
- Produces:
  - `PowerWindow.SearchKeep`, the window of the `search.cards` decision.
  - `PlacementLimit(reason: OptionReason, allows: SearchPlacement => Boolean)` in `oathdigital.gameplay.actions`.
  - `CardPlayRules.limitPlacements(limit: PlacementLimit): CardPlayRules` and `CardPlayRules.refusal(placement: SearchPlacement): Option[OptionReason]`.
  - `SearchProcedure.kept(answered: Vector[Answered]): Vector[WorldCardId]`, the kept cards in the order kept.
  - `LandWarden.id`, `LandWarden.heading`, `LandWarden.toSite`, `LandWarden.siteRule`, and `LandWardenCard` in `oathdigital.gameplay.powers.search`.

- [ ] **Step 1: Write the failing tests**

In `CardSlotsSuite`, add the import `PlacementLimit` to the `gameplay.actions` import list (`AdviserFace, AdviserPlayNote, CardPlay, CardPlayRules, PlacementLimit, RuleNotes, VisionRules`). Then add at the end of the class:

```scala
  /** A limit that leaves only plays to a site open. */
  private val siteOnly = CardPlayRules.default.limitPlacements(
    PlacementLimit(OptionReason.Written("Test: site only"), {
      case SearchPlacement.Site(_) => true
      case _ => false
    }))

  test("a placement limit shows each play it refuses unavailable, with its " +
      "reason"):
    assertEquals(options(slotsOf(atHome().hand(p1, card).ready,
      rules = siteOnly)), Vector(refused(pile, "Test: site only"),
      siteSlot(0), refused(adviserPlay(0, faceup = true), "Test: site only"),
      refused(adviserPlay(0, faceup = false), "Test: site only")))

  test("when the limits leave no play open, the discard pile stays open"):
    val full = atHome(rainBoots, binding, wrestlers).hand(p1, card).ready
    assertEquals(options(slotsOf(full, rules = siteOnly)), Vector(pile,
      refused(adviserPlay(0, faceup = true), "Test: site only"),
      refused(adviserPlay(0, faceup = false), "Test: site only")))
```

Create `src/test/scala/oathdigital/gameplay/powers/search/LandWardenSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{PlacementFixture, SearchPlay}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PlacementPicks,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Land Warden (card 130): for 1 favor, a Search may keep two cards,
  * played in the order kept. When the first does not go to a site, the
  * second may go only to one (catalog batch 6, slice 4). */
class LandWardenSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val modifiers = Vector(LandWarden.id)
  /** Three plain Beast cards on top of the world deck. */
  private val drawn = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  /** p1 at Ancient City (room for three, empty) with the start's 1 favor,
    * 5 Supply and the world deck topped by `drawn`, holding Land Warden
    * faceup unless `warden` is false. */
  private def atAncientCity(warden: Boolean = true): Table =
    val table = Table.start.pawn(p1, "Ancient City").supply(p1, 5)
      .worldDeckTop(drawn*)
    if warden then table.adviser(p1, landWarden) else table

  /** p1 searches the world deck with `picked`. */
  private def searched(table: Table, picked: Vector[PowerId] = modifiers)(
      using munit.Location): OathTransition =
    accepted(SearchPlay.searched(rules, Ready(table.ready), p1,
      SearchSource.WorldDeck, picked))

  /** Keeps `first`, then `second`, and discards the third drawn card. */
  private def keepingBoth(from: OathTransition, first: DenizenId,
      second: DenizenId): Either[OathViolation, OathTransition] =
    def placed(card: DenizenId, key: String) =
      DecisionPlacement(PlacementPicks.optionNaming(card), key)
    rules.resolveWalker(from.state, p1, SearchProcedure.cardDecisionId,
      DecisionAnswer.PartitionAnswer(Vector(
        placed(first, SearchProcedure.keepKey),
        placed(second, SearchProcedure.keepKey)) ++
        drawn.filterNot(Set(first, second)).map(
          placed(_, SearchProcedure.discardKey))))

  /** The decision `state` is parked on, as the projector rebuilds it. */
  private def parkedDecide(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, walkerPowers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  /** The second card's placement options once the first was played
    * `way`, at a table that `table` states. */
  private def secondCardOptions(table: Table, way: PlayedAs)(
      using munit.Location): (OathTransition, Vector[DecisionOption]) =
    val kept = accepted(keepingBoth(searched(table), drawn(1), drawn(0)))
    val first = accepted(SearchFixture.place(kept, drawn(1), way))
    (first, PlacementPicks.parkedOptions(first.state, walkerPowers))

  test("picked, it places 1 favor on itself and the Search may keep one " +
      "or two cards"):
    val at = searched(atAncientCity())
    assertEquals(Look(SearchFixture.after(at)).tokensOn(landWarden),
      Tokens(1, 0))
    val shown = parkedDecide(at.state).query match
      case DecisionQuery.Partition(sections, _, heading, _) =>
        Some((sections, heading))
      case _ => None
    assertEquals(shown, Some((Vector(
      DecisionSection(SearchProcedure.keepKey, "Keep", 1, Some(2)),
      DecisionSection(SearchProcedure.discardKey, "Discard", 0)),
      Some(LandWarden.heading))))
    assertEquals(LandWarden.id, PowerId("denizen.land-warden"))

  test("the kept cards are played in the order kept; after a play to a " +
      "site, the second goes anywhere"):
    val kept = accepted(keepingBoth(searched(atAncientCity()), drawn(1),
      drawn(0)))
    assertEquals(parkedDecide(kept.state).decisionId,
      PlacementFixture.cardPlayDecisionId(drawn(1), "place"))
    val (first, options) = secondCardOptions(atAncientCity(),
      PlayedAs.AtSite)
    assertEquals(parkedDecide(first.state).decisionId,
      PlacementFixture.cardPlayDecisionId(drawn(0), "place"))
    assert(options.forall(_.available), options)
    val second = accepted(SearchFixture.place(first, drawn(0),
      PlayedAs.Faceup))
    val after = Look(SearchFixture.after(second))
    assertEquals(after.cardsAt("Ancient City"), Vector(drawn(1)))
    assert(after.advisers(p1).contains(drawn(0)), after.advisers(p1))
    assert(after.inRegionalDiscard(drawn(2)))

  test("when the first did not go to a site, the second may go only to " +
      "one"):
    val (_, options) = secondCardOptions(atAncientCity(), PlayedAs.Faceup)
    assertEquals(PlacementPicks.offered(options), Vector(PlayedAs.AtSite))
    val refused = options.filterNot(option =>
      CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.AtSite))
    assert(refused.nonEmpty, options)
    refused.foreach {
      case DecisionOption.Unavailable(_, reason) =>
        assertEquals(reason, OptionReason.Written(LandWarden.toSite))
      case other => fail(s"expected an unavailable option, got $other")
    }

  test("when the first did not go to a site and no site has room, the " +
      "second is discarded"):
    val full = atAncientCity().denizen("Rain Boots", at = "Ancient City")
      .denizen("Ancient Binding", at = "Ancient City")
      .denizen("Wrestlers", at = "Ancient City")
    val (first, options) = secondCardOptions(full, PlayedAs.Faceup)
    assertEquals(PlacementPicks.offered(options), Vector(PlayedAs.Discarded))
    val second = accepted(SearchFixture.place(first, drawn(0),
      PlayedAs.Discarded))
    assert(Look(SearchFixture.after(second)).inRegionalDiscard(drawn(0)))

  test("without Land Warden, keeping two is refused"):
    assert(keepingBoth(searched(atAncientCity(warden = false),
      picked = Vector.empty), drawn(0), drawn(1)).isLeft)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.search.LandWardenSuite"`
Expected: the test sources fail to compile: `not found: PlacementLimit` and `not found: LandWarden`.

- [ ] **Step 3: Add the window**

In `PowerWindow.scala`, after `SearchSourceSelection`, add:

```scala
  /** The `search.cards` decision, which keeps one card: a power lets it
    * keep more (Land Warden, catalog batch 6, slice 4). */
  case object SearchKeep extends SearchWindow:
    val key = "search.keep"
```

- [ ] **Step 4: Add placement limits to card play**

In `CardPlayRules.scala`:

1. After `AdviserPlayNote` and its doc, add:
   ```scala
   /** A limit on where a card may be played: the placements it `allows`,
     * and the `reason` every other play is refused (Land Warden's "one card
     * must go to a site"). */
   final case class PlacementLimit(reason: OptionReason,
       allows: SearchPlacement => Boolean)
   ```
2. In the `CardPlayRules` doc's bullet list, after the `adviserNotes` bullet, add:
   ```scala
     *  - `placementLimits` are limits on where the card may go. A play one
     *    refuses is shown unavailable with its reason. When the limits
     *    leave no play, the discard pile stays open: a card that cannot be
     *    played is discarded.
   ```
   In the sentence after the list, change `adds a permission or a note` to `adds a permission, a note or a placement limit`.
3. Add the field after `adviserNotes`:
   ```scala
       adviserNotes: Vector[AdviserPlayNote] = Vector.empty,
       placementLimits: Vector[PlacementLimit] = Vector.empty):
   ```
4. After `permitSiteDiscard`, add:
   ```scala
     /** Adds a limit on where the card may be played. */
     def limitPlacements(limit: PlacementLimit): CardPlayRules =
       copy(placementLimits = placementLimits :+ limit)

     /** The reason of the first limit that refuses `placement`. */
     def refusal(placement: SearchPlacement): Option[OptionReason] =
       placementLimits.find(!_.allows(placement)).map(_.reason)
   ```

In `CardSlots.scala`:

1. In the object doc, after the paragraph that ends `Powers' own restrictions (Vow of Obedience) are the walker look-ahead's to apply.`, add:
   ```scala
     *
     * Last, the rules' placement limits (`CardPlayRules.placementLimits`)
     * show each play they refuse unavailable with their reason. When they
     * leave no play open, the discard pile stays open.
   ```
2. In `slots`, replace the `candidates.flatMap { ... }` expression with:
   ```scala
           limited(candidates.flatMap { (option, placement) =>
             judge(catalog, ready, actor, option, CardPlay.operations(catalog,
               ready, actor, card, placement, origin), picked)
               .map(CardSlot(_, placement))
           }, rules)
   ```
3. Add after `slots`:
   ```scala
     /** `slots` under the placement limits of `rules`: a play a limit
       * refuses is shown unavailable with that limit's reason. When the
       * limits leave no play open, the discard pile stays as it was, since
       * a card that cannot be played is discarded. */
     private def limited(slots: Vector[CardSlot], rules: CardPlayRules)
         : Vector[CardSlot] =
       val marked = slots.map(slot => rules.refusal(slot.placement)
         .filter(_ => slot.option.available).fold(slot)(reason =>
           slot.copy(option = DecisionOption.Unavailable(slot.option,
             reason))))
       if marked.exists(_.option.available) then marked
       else marked.zip(slots).map((after, before) =>
         if before.placement == SearchPlacement.Discard then before
         else after)
   ```

- [ ] **Step 5: Keep up to two cards, played in order**

In `SearchProcedure.scala`:

1. In the class doc's tree, change the `Branch` line to:
   ```
     *     Sequence(cost, draw, query, placement) })  // window = SearchActionEligibility;
     *                                                // query's window = SearchKeep
   ```
2. In `body`'s `query`, give the `Decide` its window. The `Decide(...)` call becomes:
   ```scala
         else Vector(Decide(cardDecisionId, actor, DecisionQuery.Partition(
           Vector(DecisionSection(keepKey, "Keep", 1, Some(1)),
             DecisionSection(discardKey, "Discard", 0)),
           cards.map(option), heading = Some("Choose a card to keep"),
           confirmLabel = Some("Continue Search")),
           window = Some(PowerWindow.SearchKeep)))
   ```
3. In `placement`, replace the `kept.headOption.fold(...) { ... }` expression that Task 2 wrote with:
   ```scala
             kept match
               case first +: later =>
                 val cardTree = CardPlayProcedure.unchecked(catalog, ready,
                   actor, first, CardPlayProcedure.Origin.TemporaryHand,
                   ModifierPicks.cards(pending.answered))
                 // A later kept card (Land Warden) is planned when the walk
                 // reaches it, from the state the plays before it left.
                 val laterTrees = later.map(card =>
                   Branch((current, reached) => Vector(
                     CardPlayProcedure.unchecked(catalog, current, actor,
                       card, CardPlayProcedure.Origin.TemporaryHand,
                       ModifierPicks.cards(reached.answered)))))
                 Vector(removeOthers, cardTree) ++ laterTrees
               case _ => Vector.empty
   ```
   The first card's tree is built as before, so a walk parked inside it keeps its shape.
4. Add after `sourcePicked`:
   ```scala
     /** The cards the keep answer in `answered` keeps, in the order kept;
       * empty before that answer. */
     def kept(answered: Vector[Answered]): Vector[WorldCardId] =
       answered.collectFirst {
         case Answered(`cardDecisionId`,
             DecisionAnswer.PartitionAnswer(placements), _) => placements
       }.toVector.flatten.filter(_.sectionKey == keepKey)
         .flatMap(value => card(value.option))
   ```
5. Replace `selected` (keep its doc) with the version below. The keep decision's own sections bound how many cards an answer keeps, and the walker checks them, so `selected` asks only for one at least.
   ```scala
     private def selected(ready: ReadyGame, pending: PendingTree,
         actor: PlayerId): Either[OathViolation,
           (Vector[WorldCardId], Vector[WorldCardId])] =
       val cards = selectedCards(ready, pending, actor)
       if cards.size < 2 then Right(cards -> Vector.empty)
       else answered(pending).toRight(OathViolation.SearchChoiceMismatch(
         "Search card selection is missing")).flatMap { placements =>
         val keeps = kept(pending.answered)
         val discarded = placements.filter(_.sectionKey == discardKey)
           .flatMap(value => card(value.option))
         Either.cond(keeps.nonEmpty, keeps -> discarded,
           OathViolation.SearchChoiceMismatch("Search must keep a card"))
       }
   ```

- [ ] **Step 6: Write Land Warden**

Delete `LandWardenCard` (the `object LandWardenCard ...` block of six lines and the blank line after it) from `HoldingHearthDenizens.scala`. The file's imports stay.

Create `src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.PlacementLimit
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object LandWardenCard extends Denizen(DenizenId("130"), "Land Warden", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.land-warden"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may play two cards you draw _(instead of one)_ if you " +
      "play at least one card to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Land Warden (card 130), a selected Search modifier costing 1 favor
  * placed on it (catalog batch 6, slice 4):
  *
  *  - The keep decision may hold one or two cards (`SearchKeep`).
  *  - The kept cards are played in the order kept. When the first did not
  *    go to a site, the second may go only to one: its other plays are
  *    unavailable with "Land Warden: one card must go to a site". When no
  *    site has room, its discard stays open (`PlacementLimit`).
  *
  * It writes no line: the Search line names both kept cards, and each
  * play has its own line.
  */
final case class LandWarden private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = LandWardenCard.id
  def id: PowerId = LandWarden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = LandWarden.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchKeep -> Vector(Transform((_, operations) =>
      operations.map {
        case decide: Decide
            if decide.decisionId == SearchProcedure.cardDecisionId =>
          LandWarden.widened(decide)
        case other => other
      })),
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if LandWarden.toSiteOnly(ctx.answered, tree.card) =>
          tree.adjust(children)(_.limitPlacements(LandWarden.siteRule))
        case _ => children)))

object LandWarden:
  val id: PowerId = LandWardenCard.power.id
  val Favor: Int = 1
  /** How many cards the keep decision may hold. */
  val Kept: Int = 2
  val heading: String = "Choose one or two cards to keep"
  val toSite: String = "Land Warden: one card must go to a site"
  /** Only a play to a site stays open. */
  val siteRule: PlacementLimit = PlacementLimit(OptionReason.Written(toSite),
    {
      case SearchPlacement.Site(_) => true
      case _ => false
    })

  /** The keep decision `decide`, holding up to [[Kept]] cards. */
  def widened(decide: Decide): Decide = decide.query match
    case partition: DecisionQuery.Partition => decide.copy(query =
      partition.copy(sections = partition.sections.map(section =>
        if section.key == SearchProcedure.keepKey then
          section.copy(maxAllowed = Some(Kept))
        else section), heading = Some(heading)))
    case _ => decide

  /** Whether `card` is the second card kept, and the first did not go to
    * a site. */
  def toSiteOnly(answered: Vector[Answered], card: WorldCardId): Boolean =
    SearchProcedure.kept(answered) match
      case Vector(first, second) if second == card =>
        answered.collectFirst {
          case Answered(decision, DecisionAnswer.ChooseOneAnswer(ref), _)
              if decision ==
                CardPlayProcedure.placementDecisionId(first) => ref
        }.flatMap(CardPlayProcedure.playedAs)
          .exists(_ != PlayedAs.AtSite)
      case _ => false

  def forCatalog(catalog: ExecutableCatalog): LandWarden =
    new LandWarden(catalog)
```

- [ ] **Step 7: Register it**

- In `ActionModifiers.scala`, add `LandWarden` to the `powers.search` import, in alphabetical order. Add `Vector(LandWarden.forCatalog(catalog)) ++` after the `SpinningBee` line.
- In `NewFoundations.scala`, add `LandWardenCard` to the `powers.search` import, in alphabetical order. The card list's `LandWardenCard,` entry stays as it is.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.search.*"`
Expected: PASS.

Then run: `./sbtw test`
Expected: PASS, with 14 tests more than the baseline.

Land Warden is a filler card in many suites, and it is Hearth. If a suite now fails, check these causes:
- `SearchFixture.anyPlainDenizensOf(Suit.Hearth)` no longer returns Land Warden. Name another card in that suite.
- A suite holds Land Warden faceup and starts a Search directly, so the Search now parks on `search.modifiers`. Swap its card for "Wrestlers", unless the suite tests Land Warden itself.
- A suite asserts on `HoldingHearthDenizens`.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala src/test/scala/oathdigital/gameplay/powers/search/LandWardenSuite.scala
git commit -m "feat(search): Land Warden keeps two cards and sends one to a site"
```

---

### Task 4: The Search line keeps none, and two

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (`search`, near line 184)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogSearchKeepSuite.scala`

**Interfaces:**
- Consumes: `SpinningBee.id` and `SpinningBee.suitDecisionId` (Task 2), and `LandWarden.id` (Task 3).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/application/gamelog/GameLogSearchKeepSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GameCommand, StartPayload}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.search.{LandWarden, SpinningBee}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** The lines of the Searches slice 4 adds: Spinning Bee's own line, a
  * Search that keeps none, and one that keeps two (catalog batch 6, slice
  * 4 plan-time rulings 2 and 10). */
class GameLogSearchKeepSuite extends munit.FunSuite:
  /** p1's depth-1 lines after a Search at `table` that picks `modifiers`,
    * with `answers` over the defaults. */
  private def searchLines(name: String, table: Table,
      modifiers: Vector[PowerId], answers: Answers)(
      using munit.Location): Vector[String] =
    val (service, act) = atTable(name, table.banditsRefilled)
    act.withAnswers(answers.orElse(ModifierPlay.answers(
      PowerWindow.SearchModifierSelection, modifiers)))
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(p1)))
    texts(format(Script(name, service, p1), Some(p1)).filter(_.depth == 1))

  private def declaring(suit: Suit): Answers = {
    case park if park.decisionId == SpinningBee.suitDecisionId =>
      ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))
  }

  /** p1 at Ancient City holding Spinning Bee faceup. The Cradle pile holds
    * Homesteaders (Hearth) under Wrestlers (Order) under Defame
    * (Discord). */
  private val besideBee: Table = Table.start.pawn(p1, "Ancient City")
    .adviser(p1, "Spinning Bee")
    .discarded(Region.Cradle, "Homesteaders", "Wrestlers", "Defame")

  test("Spinning Bee: its line names the pile and the suit, before the " +
      "Search line"):
    val lines = searchLines("spinning-bee", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Order))
    val bee = lines.indexOf(
      "Spinning Bee: Drawing from the Cradle discard until Order card is drawn")
    val drew = lines.indexWhere(_.startsWith("Drew "))
    assert(bee >= 0 && drew > bee, lines)
    assert(lines(drew).contains(" from the Cradle discard and kept "),
      lines(drew))

  test("Spinning Bee with no match: drew the pile and kept none"):
    val lines = searchLines("spinning-bee-none", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Nomad))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.endsWith(" from the Cradle discard and kept none"), drew)

  test("Land Warden: kept two, then a line for each play"):
    val drawn = Vector("Threatening Roar", "Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    def placed(card: DenizenId, key: String) =
      DecisionPlacement(DecisionOptionRef.Denizen(card), key)
    val keepFirstTwo: Answers = {
      case park if park.decisionId == SearchProcedure.cardDecisionId =>
        PartitionAnswer(Vector(placed(drawn(0), SearchProcedure.keepKey),
          placed(drawn(1), SearchProcedure.keepKey),
          placed(drawn(2), SearchProcedure.discardKey)))
    }
    val lines = searchLines("land-warden", Table.start
      .pawn(p1, "Ancient City").adviser(p1, "Land Warden")
      .worldDeckTop(drawn*), Vector(LandWarden.id), keepFirstTwo)
    val drew = lineStarting(lines, "Drew ")
    assert(drew.endsWith(" and kept Threatening Roar and Fae Merchant"),
      drew)
    // By default the first goes to the discard pile, its first option.
    // Land Warden then leaves the second only the site.
    assert(lines.contains(
      "Discarded Threatening Roar to the Provinces discard"), lines)
    assert(lines.contains("Played Fae Merchant to Ancient City"), lines)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogSearchKeepSuite"`
Expected: FAIL in "Spinning Bee with no match": no line starts with "Drew ". The other two tests may already pass, since they need no change in `ActionLines`.

- [ ] **Step 3: Say "kept none"**

In `ActionLines.scala`, `search`:

1. In the `kept` match, add this case before `case _: WalkerCompleted if drawn.size == 1 && ...`:
   ```scala
          // Nothing kept: every drawn card went to a discard pile, and
          // nothing was asked (Spinning Bee drew no match).
          case _: WalkerCompleted if !journal.answers(run, at).exists(
              answer => answer.decisionId == SearchProcedure.cardDecisionId ||
                answer.decisionId.startsWith(PlacePrefix)) &&
              journal.readyAfter(at).exists(after => drawn.forall(
                (card, _) => inDiscardPile(after, card))) =>
            Some(Vector.empty)
   ```
2. Replace the line-building expression at the end with:
   ```scala
        kept.toVector.map(ids => action(Vector(Text("Drew ")) ++
          words.cards(drawn.map(_._2)) ++
          Vector(Text(s" from $from and kept ")) ++
          (if ids.isEmpty then Vector(Text("none"))
          else words.cards(ids.flatMap(id =>
            drawn.find(_._1 == id).map(_._2))))))
   ```
3. Add after `drawnFrom`:
   ```scala
     /** Whether `card` lies in a regional discard pile in `ready`. */
     private def inDiscardPile(ready: ReadyGame, card: CardId): Boolean =
       Region.all.exists(region =>
         ready.game.current.commonCards.discard(region).contains(card))
   ```
4. In the doc of `search`, change `or at completion when only one card was drawn.` to `or at completion when only one card was drawn or none was kept ("kept none").`

- [ ] **Step 4: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. The golden logs are unchanged: every earlier Search asks a keep or a placement question before it completes.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/GameLogSearchKeepSuite.scala
git commit -m "feat(gamelog): a Search that keeps nothing says so"
```

---

### Task 5: The keep panel for one or two cards

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (`keepOne` near line 314, `partitionInstruction` near line 331, `partitionZone` near line 379)
- Test: `frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala`

- [ ] **Step 1: Write the failing tests**

In `PartitionPanelRenderSuite`, after the test "a partition that must meet a minimum in each zone is not keep-one", add:

```scala
  /** Land Warden's Search keeps one or two: the capped zone holds up to
    * two, and the order of its cards is the order they are played in. */
  private val keepUpToTwo = keepDiscard.copy(sections = Vector(
    DecisionSectionState("keep", "Keep", 1, Some(2)),
    DecisionSectionState("discard", "Discard", 0)))

  private val keepUpToTwoDecision = WalkerDecisionState("setup", "setup-1",
    "decide", query = Some(keepUpToTwo))

  /** The draft opened on `keepUpToTwo`: Denizens 1 and 2 kept. */
  private def keepUpToTwoDraft(): Option[WalkerPartitionDraft] =
    WalkerPartitionDraft.reconcile(None, ForgeFixture.context,
      Some(keepUpToTwoDecision))

  test("a keep-one-or-two partition says so, without minimum lines"):
    val panel = render(controls(), keepUpToTwoDraft(),
      decision = keepUpToTwoDecision)
    assertEquals(one(panel, ".partition-instruction").textContent,
      "Keep one or two; discard the rest.")
    assertEquals(all(panel, ".decision-zone-helper"), Vector.empty)

  test("a keep zone holding two says they are played in that order"):
    def keepZone(draft: Option[WalkerPartitionDraft]) = one(render(
      controls(), draft, decision = keepUpToTwoDecision),
      """[data-section-key="keep"]""")
    assertEquals(one(keepZone(keepUpToTwoDraft()),
      ".decision-zone-order").textContent, "They are played in this order.")
    val keepingOne = keepUpToTwoDraft().map(_.move(optionKey(2), "discard"))
    assertEquals(all(keepZone(keepingOne), ".decision-zone-order"),
      Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PartitionPanelRenderSuite"`
Expected: FAIL. The instruction reads "Assign every option: Keep (1), Discard (0).", and the keep zone has no order line.

- [ ] **Step 3: Give the shape its sentence**

In `WalkerPanelSupport.scala`:

1. Replace the body of `keepOne` (keep its doc) with:
   ```scala
       cappedAndLeftover(query)(_.maxAllowed.contains(1))
   ```
2. After `keepOne`, add:
   ```scala
     /** The keep-some shape: as [[keepOne]], but the capped section holds
       * one to more than one (Land Warden's Search keeps one or two). The
       * order of the cards it holds is the order they are played in. */
     private[frontend] def keepSome(query: DecisionQueryState.Partition)
         : Option[(DecisionSectionState, DecisionSectionState)] =
       cappedAndLeftover(query)(_.maxAllowed.exists(_ > 1))

     /** Exactly two sections, in either order: one with a minimum of one
       * whose cap `cap` accepts, and one that takes whatever is left. The
       * pair is (the capped section, the leftover section). */
     private def cappedAndLeftover(query: DecisionQueryState.Partition)(
         cap: DecisionSectionState => Boolean)
         : Option[(DecisionSectionState, DecisionSectionState)] =
       query.sections match
         case Vector(first, second) =>
           def capped(section: DecisionSectionState) =
             section.minRequired == 1 && cap(section)
           def leftover(section: DecisionSectionState) =
             section.minRequired == 0 && section.maxAllowed.isEmpty
           if capped(first) && leftover(second) then Some(first -> second)
           else if capped(second) && leftover(first) then
             Some(second -> first)
           else None
         case _ => None

     /** "one", "one or two", or "one to {most}" for a capped section. */
     private def upTo(section: DecisionSectionState): String =
       section.maxAllowed.filter(_ > 1).fold("one") {
         case 2 => "one or two"
         case most => s"one to $most"
       }
   ```
3. In `partitionInstruction`, replace `keepOne(query) match` and its `Some` case with:
   ```scala
       keepOne(query).orElse(keepSome(query)) match
         case Some((capped, leftover)) =>
           s"${capped.label} ${upTo(capped)}; " +
             s"${leftover.label.toLowerCase} the rest."
   ```
   Update its doc: change `The keep-one shape reads like the rulebook's own sentence;` to `The keep-one and keep-some shapes read like the rulebook's own sentence;`.
4. In `partitionZone`:
   - Change `if keepOne(query).isEmpty then` to `if keepOne(query).isEmpty && keepSome(query).isEmpty then`.
   - After the `if ordered(section, held.size) then ...` block, add:
     ```scala
         // On the keep-some shape the kept cards are played in order.
         val playedInOrder = keepSome(query).exists(_._1.key == section.key)
         if playedInOrder && held.size > 1 then
           zone.appendChild(text("p", "decision-zone-order",
             "They are played in this order."))
     ```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.*"`
Expected: PASS, with 2 tests more than the frontend baseline.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala
git commit -m "feat(frontend): a keep panel for one or two cards says so, and their order"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 6)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 17, and the frontend count the baseline plus 2.

- [ ] **Step 2: Update the roadmap**

Slice 4 is the batch's last, so the phase is done. In `docs/ROADMAP.md`:

- Delete the whole "### Phase - Catalog batch 6" section: the heading, its paragraph, the slice 4 bullet, and the blank line after it.
- In "## Now", delete the sentence `**Phase - Catalog batch 6** is in progress: slices 1 to 3 are done.` Re-wrap the paragraph at its width. If another session has rewritten the sentence meanwhile, delete it all the same, and keep the rest of the paragraph as it is.
- Leave the mentions of batch 6 in "Phase - Modifier selection on the walker" and in the engine-blocked phase as they are.

Then, in the design's **Status** paragraph (`docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`), change `Slices 1 to 3 are done. Slice 4 is planned against` to `All four slices are done. Slice 4 was planned against`. Re-wrap the paragraph.

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md
git commit -m "docs: record catalog batch 6 slice 4, closing the batch"
```
