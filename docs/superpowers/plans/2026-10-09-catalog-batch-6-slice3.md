# Catalog Batch 6, Slice 3 (Search Sources) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement five selected Search modifiers:

- Observatory (64) and Errand Boy (11) offer other regions' discard piles.
- Mushrooms (183) and Wine of Welcome (R33) draw from the bottom of a pile.
- Painted Trumpet (R23) draws the top card of each discard pile.

**Architecture:**

- **The source decision gets a window.** `search.source` gains `PowerWindow.SearchSourceSelection`.
  - Transforms there add discard piles, warn options, or make the world deck unavailable.
  - Each change goes through a shared helper, `SearchSources`, as Golem Legions widens the Muster source.
- **Any pile is priced and drawn alike.** `SearchRules.cost` charges 2 Supply for any region's discard pile, and `SearchRules.draw` draws from any pile.
  - Which piles a Search may pick is the source decision's business. The walker accepts only an answer the decision offers.
  - Both rules become total functions. `SearchDrawPort.prepare` drops the region it no longer needs.
- **`Draw` takes a stack position.** `Draw.position` defaults to `StackPosition.Top`.
  - `Bottom` takes the pile's bottom cards, bottom first. The move guard already checks bottom takes.
  - `SearchRules.draw` takes the same position.
- **Draw shapes fold in a fixed order, set by power priority.**
  1. Wine of Welcome and Mushrooms fold at priority -1 (`BottomDraw.Priority`). They redraw from the bottom.
  2. The draw extensions (Augury, Truthful Harp) fold at 0. They keep the end the draw takes from.
  3. Painted Trumpet folds at priority 1. It replaces the draw with one `Draw` per pile, so nothing modifies it.
- **Modifier rules use the modifier walker's `offerReason`.** Each card states its own:
  - Mushrooms and Painted Trumpet exclude each other.
  - Wine of Welcome and Mushrooms warn each other.
- **Source warnings use the `warning` hook, as Hunting Party's does** (Wine of Welcome, Painted Trumpet).
  - Errand Boy's warning depends on the actor's region, which the hook cannot see. So Errand Boy marks its warning in its own Transform.
- **The Search line** reads "from the bottom of …" for a bottom draw, and "from each discard pile" for Painted Trumpet.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

- No frontend change. All three discard piles already have board homes, since Clay Rattle offers them (search source design, ruling 8).

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`: "Goal and scope", "The bar", "Slice 3: Search sources" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`: "Rules that apply to the whole batch" and "Slice 3: Search sources".
- `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`: rulings 1 to 7 and plan-time rulings 1 to 3. They cover the modifier decision and `offerReason`.
- `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`: the source decision, its pricing and its wording.

Task 1 amends the first two with the plan-time rulings, so read them again after Task 1.

**Starts from:** `main` at `393e8d80` or later. Modifier walker slice 1 and catalog batch 6 slice 2 are merged there.

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
- **Concurrent sessions.** Other sessions commit to `main` while you work. Modifier walker slice 2 (Travel) is in flight.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Texts, exactly.** No slice 3 power writes a Game Log line of its own.
  - Errand Boy warns its actor's own pile and the world deck: "Errand Boy does nothing here".
  - Mushrooms makes the world deck unavailable: "Mushrooms draws from a discard pile".
  - Wine of Welcome warns each discard pile: "Wine of Welcome needs the world deck".
  - Painted Trumpet warns the world deck: "Painted Trumpet needs a discard pile".
  - Painted Trumpet is unavailable while Mushrooms is picked: "Cannot be used with Mushrooms".
  - Mushrooms is unavailable while Painted Trumpet is picked: "Cannot be used with Painted Trumpet".
  - Wine of Welcome is warned while Mushrooms is picked, and Mushrooms while Wine of Welcome is. Both warnings read "Wine of Welcome does nothing with Mushrooms".
  - The Search line reads "Drew {cards} from the bottom of the Cradle discard and kept {card}", or "… from the bottom of the World Deck …".
  - Painted Trumpet's Search line reads "Drew {cards} from each discard pile and kept {card}".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 28 server tests and no frontend test.
  - It rewrites three existing tests:
    - `SearchSuite`'s two rule tests, for the new signatures.
    - `SearchDrawPortContract`'s refusal test. It becomes a draw from another region.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled rulings 1 to 4 on 2026-10-09. Task 1 writes all of them into the spec.

1. **The Search line.** A bottom draw reads "from the bottom of the Cradle discard" or "from the bottom of the World Deck". Painted Trumpet's draw reads "from each discard pile".
2. **The modifier rules' texts** are the ones in Global Constraints. Wine of Welcome and Mushrooms warn each other, whichever was picked first.
3. **Wine of Welcome and Painted Trumpet warn the sources they leave alone**, as Hunting Party warns a discard pile. Both sources stay choosable.
4. **Painted Trumpet and an empty pile.** With Painted Trumpet picked, the player may pick an empty discard pile while another pile holds a card. The pile costs its usual 2 Supply. A pile is unavailable only when every discard pile is empty.
5. **Any pile, one gate.** `SearchRules` prices and draws any region's discard pile. The source decision is the only gate, and the walker accepts only an answer it offers. A pile costs 2 Supply wherever it lies.
6. **Order of the piles.** A power adds piles after the printed two, in region order (Cradle, Provinces, Hinterland), each once. Painted Trumpet draws the piles in the same order.
7. **Mushrooms waives only Supply.** A toll such as Forced Labor's is still paid, as with News from Afar.
8. **Draw order.**
   - Wine of Welcome and Mushrooms shape the draw before the draw extensions add to it, so Augury adds cards from the bottom too.
   - Painted Trumpet replaces the draw after the extensions, so nothing modifies its draw.
   - Truthful Harp still reveals the cards Painted Trumpet drew.

## File map

**Engine (Task 2):**
- `src/main/scala/oathdigital/model/CoreOperations.scala`: `Draw.position`.
- `src/main/scala/oathdigital/model/PowerWindow.scala`: `SearchSourceSelection`.
- `src/main/scala/oathdigital/gameplay/actions/Search.scala`: `SearchRules`, total, with a position.
- `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`: the window on `search.source`, and the new rule calls.
- `src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala`: the empty-pile reason without a region.
- `src/main/scala/oathdigital/gameplay/powers/search/DrawExtension.scala`: keeps the draw's position, and shares `sourceOf` and `redrawn`.
- `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`: `Draw.position` on the wire.
- `src/main/scala/oathdigital/application/GameRandomPorts.scala`: `SearchDrawPort.prepare(ready, source)`.
- `src/main/scala/oathdigital/application/GameApplicationService.scala`: the draw-port check without a region.
- `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` and `DetailLines.scala`: the `Draw` patterns.
- Tests:
  - Rewritten: `SearchSuite`, `SearchDrawPortContract` and the stub port in `GameApplicationServiceActionsSuite`.
  - Extended: `OperationApplicationSuite` and `WalkerOperationWireSuite`.
  - Testkit: `Table.worldDeckBottom`.

**Powers (Tasks 3 to 5)**, all in `src/main/scala/oathdigital/gameplay/powers/search/`:
- `SearchSources.scala`: adds, warns and withholds source options.
- `BottomDraw.scala`: redraws from the bottom.
- `Observatory.scala`, `ErrandBoy.scala`, `Mushrooms.scala`, `WineOfWelcome.scala` and `PaintedTrumpet.scala`. Each card object moves out of `catalog/holding`.
- Registration: `ActionModifiers.scala`, plus the imports in `NewFoundations.scala`.
- Tests in `src/test/scala/oathdigital/gameplay/powers/search/`:
  - `ShownOptions.scala`, the test helper.
  - One suite per card.

**Game Log (Task 6):**
- `ActionLines.search` reads every `Draw` of the run.
- `GameLogSearchDrawSuite` tests the new lines.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`
- Modify: `docs/superpowers/specs/2026-10-07-search-source-walker-design.md` (ruling 2)

- [ ] **Step 1: Amend the design**

In `2026-10-09-catalog-batch-6-design.md`:

1. Replace the **Status** paragraph with the text below, wrapped at 76 columns:

   ```
   **Status:** designed 2026-10-09. Slices 1 and 2 are done. Slice 3 is
   planned against [modifier selection on the walker](2026-10-09-modifier-walker-design.md),
   whose slice 1 moved Search's modifiers into the walk. Slice 4 is re-read
   against that design before it is planned. The per-card rulings are in
   [the rulings appendix](2026-10-09-catalog-batch-6-rulings.md).
   ```

2. Under "### The bar", replace the bullet that starts `- **Search costs and draws** (slice 3).` with:

   ```
   - **Search costs and draws** (slice 3, settled at plan time).
     `SearchRules.cost` charges 2 Supply for any region's discard pile, and
     `SearchRules.draw` draws from any pile, from the top or the bottom.
     Which piles a Search may pick is the source decision's business.
     `Draw` gains `position`, and `SearchDrawPort.prepare` no longer takes
     the actor's region.
   ```

3. In the same list, replace the bullet that starts `- **Modifiers that restrict or warn about other modifiers**` with:

   ```
   - **Modifiers that restrict or warn about other modifiers** (slices 3 and
     4) use the modifier walker's `offerReason` (modifier walker design,
     ruling 5).
   ```

4. Under "## Slice 3: Search sources", replace `Re-read against the modifier walker design before planning.` with `Planned against slice 1 of the modifier walker design.`

5. Under "### Costs and draws", replace the paragraph that starts `` `SearchRules.cost` refuses another region's discard pile today. `` with:

   ```
   `SearchRules.cost` charges 2 Supply for a discard pile wherever it lies.
   The source decision offers another region's pile only when a picked
   modifier adds it, and the walker accepts only an answer the decision
   offers. `SearchRules.draw` takes a draw shape:
   ```

6. Replace the paragraph that starts ``The plan checks whether `Draw` `` with:

   ```
   `Draw` takes cards from the top or the bottom (`Draw.position`). The move
   guard already checks a bottom take. Painted Trumpet's draw is one `Draw`
   per pile.
   ```

7. Replace the paragraph that starts `The modifier walker phase supplies two modifier rules:` with:

   ```
   Each card states its own modifier rules through `offerReason`. Mushrooms
   and Painted Trumpet exclude each other, and Wine of Welcome and Mushrooms
   warn each other.

   ### Plan-time rulings

   - **The draw order is the powers' priority.** Wine of Welcome and
     Mushrooms fold at -1 and redraw from the bottom. The draw extensions
     fold at 0 and keep the end the draw takes from, so Augury adds cards
     from the bottom too. Painted Trumpet folds at 1 and replaces the draw,
     so nothing modifies it.
   - **Warnings.** Wine of Welcome and Painted Trumpet warn the sources they
     leave alone through the `warning` hook, as Hunting Party does. Errand
     Boy's warning depends on the actor's region, which that hook cannot
     see, so Errand Boy marks it in its own Transform.
   - **The Search line** names a bottom draw ("from the bottom of the Cradle
     discard") and Painted Trumpet's draw ("from each discard pile"). No
     slice 3 power writes a line of its own.
   ```

8. Under "## Seams used for the first time", append:

   ```
   - A Transform at the Search source window that adds and marks options
     (Observatory, Errand Boy, Mushrooms).
   - A draw from the bottom of a pile (Mushrooms, Wine of Welcome).
   ```

- [ ] **Step 2: Amend the rulings**

In `2026-10-09-catalog-batch-6-rulings.md`, under "## Slice 3: Search sources":

1. Append to the **R33 Wine of Welcome** bullet: `While it is picked, each discard pile is warned "Wine of Welcome needs the world deck" and stays choosable.`
2. Append to the **R23 Painted Trumpet** bullet: `While it is picked, the world deck is warned "Painted Trumpet needs a discard pile" and stays choosable. The pile picked may be empty while another pile holds a card, and it costs its usual 2 Supply. Truthful Harp still reveals the cards drawn.`
3. Replace the bullet that starts `- **Modifier rules,** from the modifier walker phase.` with:

   ```
   - **Modifier rules.** Each card states its own, through `offerReason`.
     Painted Trumpet is unavailable while Mushrooms is picked, with the
     reason "Cannot be used with Mushrooms". Mushrooms is unavailable while
     Painted Trumpet is picked, with "Cannot be used with Painted Trumpet".
     Wine of Welcome is warned while Mushrooms is picked, and Mushrooms
     while Wine of Welcome is, each with "Wine of Welcome does nothing with
     Mushrooms".
   - **Plan-time rulings** (2026-10-09):
     - Any region's discard pile costs 2 Supply. The source decision is the
       only gate.
     - A power adds piles after the printed two, in region order (Cradle,
       Provinces, Hinterland), each once. Painted Trumpet draws the piles
       in the same order.
     - Mushrooms waives only Supply. Forced Labor's toll is still paid, as
       with News from Afar.
     - The Search line reads "Drew {cards} from the bottom of the Cradle
       discard and kept {card}", "… from the bottom of the World Deck …",
       and for Painted Trumpet "Drew {cards} from each discard pile and
       kept {card}".
   ```

- [ ] **Step 3: Amend the search source design**

In `2026-10-07-search-source-walker-design.md`, ruling 2, replace `Other regions' discards are not offered.` with `Other regions' discards are offered only when a picked modifier adds them (catalog batch 6, slice 3: Observatory and Errand Boy).` Re-wrap the ruling at its width.

- [ ] **Step 4: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md docs/superpowers/specs/2026-10-07-search-source-walker-design.md
git commit -m "docs: record catalog batch 6 slice 3 plan-time rulings"
```

---

### Task 2: Any pile, either end

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`Draw`, near line 248)
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (after `SearchPlayAdviser`, near line 67)
- Modify: `src/main/scala/oathdigital/gameplay/actions/Search.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/DrawExtension.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (encode near line 119, decode near line 324)
- Modify: `src/main/scala/oathdigital/application/GameRandomPorts.scala` (`SearchDrawPort`)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (the `search.source` case, near line 512)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (the `Draw` pattern in `search`)
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala` (the `Draw` pattern, near line 114)
- Modify: `src/test/scala/oathdigital/testkit/Table.scala` (after `worldDeckTop`)
- Test: `src/test/scala/oathdigital/gameplay/SearchSuite.scala`, `src/test/scala/oathdigital/gameplay/OperationApplicationSuite.scala`, `src/test/scala/oathdigital/application/SearchDrawPortContract.scala`, `src/test/scala/oathdigital/application/GameApplicationServiceActionsSuite.scala`, `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces:
  - `Draw(player, cards, source, destination, position: StackPosition = StackPosition.Top)`
  - `PowerWindow.SearchSourceSelection`, the window of the `search.source` decision.
  - `SearchRules.cost(ready: ReadyGame, source: SearchSource): Int`
  - `SearchRules.draw(ready: ReadyGame, source: SearchSource, extra: Int = 0, position: StackPosition = StackPosition.Top): Vector[WorldCardId]`
  - `SearchDrawPort.prepare(ready: ReadyGame, source: SearchSource): Either[OathViolation, Vector[WorldCardId]]`
  - `DrawExtension.sourceOf(location: Location): Either[OathViolation, SearchSource]`
  - `DrawExtension.redrawn(draw: Draw, source: SearchSource, cards: Vector[WorldCardId]): Vector[CoreOperation]`. Both are `private[search]`.
  - `Table.worldDeckBottom(cards*)`: the last named card ends at the bottom.

- [ ] **Step 1: Write the failing tests**

Replace the body of `SearchSuite` (keep its package line). The imports and class become:

```scala
import oathdigital.gameplay.actions.{SearchRules, VisionRules}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

class SearchSuite extends munit.FunSuite:

  private def ready: ReadyGame = Table.start.ready

  test("world Search cost follows Visions Drawn track bands"):
    val base = ready
    Vector(0 -> 2, 1 -> 3, 2 -> 3, 3 -> 4, 4 -> 4, 5 -> 4).foreach:
      case (visions, expected) =>
        val state = Table.from(base).visionsDrawn(visions).unchecked
        assertEquals(SearchRules.cost(state, SearchSource.WorldDeck),
          expected)
        assertEquals(SearchRules.cost(state,
          SearchSource.RegionalDiscard(Region.Cradle)), 2)

  test("every region's discard pile costs 2 Supply"):
    Region.all.foreach(region => assertEquals(
      SearchRules.cost(ready, SearchSource.RegionalDiscard(region)), 2))

  test("world Search draw stops at first Vision"):
    val first = CatalogNames.denizen("Threatening Roar")
    val vision = VisionRules.Faith
    val state = Table.start.worldDeckTop(first, vision, "Fae Merchant",
      "Second Chance").ready
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck),
      Vector(first, vision))

  test("a draw from the bottom of a discard pile takes its bottom cards, " +
      "bottom first"):
    val pile = Vector("Threatening Roar", "Fae Merchant", "Second Chance",
      "Wrestlers").map(CatalogNames.denizen(_))
    val state = Table.start.discarded(Region.Provinces, pile*).ready
    assertEquals(SearchRules.draw(state,
      SearchSource.RegionalDiscard(Region.Provinces),
      position = StackPosition.Bottom), pile.take(3))

  test("a draw from the bottom of the world deck stops at the first " +
      "Vision"):
    val above = CatalogNames.denizen("Threatening Roar")
    val bottom = CatalogNames.denizen("Fae Merchant")
    val state = Table.start.worldDeckBottom(above, VisionRules.Faith,
      bottom).ready
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck,
      position = StackPosition.Bottom), Vector(bottom, VisionRules.Faith))
```

In `OperationApplicationSuite`, change the testkit import to `import oathdigital.testkit.{CatalogNames, Table}`. Then add at the end of the class:

```scala
  /** Three denizens in the Cradle discard: the first named at the bottom. */
  private val cradlePile = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  private def bottomDraw(cards: Vector[CardId]): Draw =
    Draw(Table.p1, cards, Location.RegionalDiscard(Region.Cradle),
      Location.Hand(Table.p1), StackPosition.Bottom)

  test("a Draw from the bottom takes the pile's bottom cards, bottom first"):
    val source = Table.start.discarded(Region.Cradle, cradlePile*).ready
    val after = accepted(OperationPipeline.run(source,
      Vector(bottomDraw(cradlePile.take(2))), OperationPolicy.Permissive,
      Vector.empty)(Right(_))).state
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(cradlePile(2)))
    assertEquals(after.game.current.temporaryHands(Table.p1),
      cradlePile.take(2))

  test("a Draw from the bottom refuses a card above the bottom"):
    val source = Table.start.discarded(Region.Cradle, cradlePile*).ready
    val operation = bottomDraw(Vector(cradlePile(1)))
    assertEquals(codes(OperationApplication.validate(source, operation)),
      Vector("invalid-stack-position"))
```

Replace `SearchDrawPortContract.scala` whole:

```scala
package oathdigital.application

import oathdigital.model.{Region, SearchSource}
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.*

/** What every `SearchDrawPort` promises (the trait's doc): a draw takes its
  * cards from the pile of the source it was asked for, in the pile's own
  * order, whichever region the pile lies in. Which piles a Search may pick
  * is the source decision's business (catalog batch 6, slice 3). Only the
  * authoritative port exists in production; the one stub of this port,
  * which draws a card the pile does not hold, breaks the promise on purpose
  * to test that the service refuses it. */
trait SearchDrawPortContract extends munit.FunSuite:
  protected def port: SearchDrawPort

  // Ancient City lies in the Cradle.
  private val origin = Region.Cradle
  private val atAncientCity = Table.start.pawn(p1, "Ancient City")

  test("a world deck draw takes the top cards, top first"):
    val table = atAncientCity.worldDeckTop("Errand Boy", "Wrestlers",
      "Alchemist", "Homesteaders")
    assertEquals(
      accepted(port.prepare(table.ready, SearchSource.WorldDeck)),
      table.ready.game.current.commonCards.worldDeck.take(3))

  test("a regional discard draw takes the pile's last-placed cards first"):
    val table = atAncientCity.discarded(origin, "Errand Boy", "Wrestlers",
      "Alchemist", "Homesteaders")
    assertEquals(
      accepted(port.prepare(table.ready,
        SearchSource.RegionalDiscard(origin))),
      table.ready.game.current.commonCards.discard(origin).reverse.take(3))

  test("another region's discard is drawn the same way"):
    val table = atAncientCity.discarded(Region.Provinces, "Errand Boy",
      "Wrestlers")
    assertEquals(
      accepted(port.prepare(table.ready,
        SearchSource.RegionalDiscard(Region.Provinces))),
      table.ready.game.current.commonCards.discard(Region.Provinces).reverse)

class AuthoritativeSearchDrawPortSuite extends SearchDrawPortContract:
  protected def port = SearchDrawPort.authoritative
```

In `GameApplicationServiceActionsSuite`, test "Search draw port cannot inject card identities inconsistent with state", change the stub's method to:

```scala
      def prepare(ready: ReadyGame, source: SearchSource)
          : Either[OathViolation, Vector[WorldCardId]] =
        Right(Vector(DenizenId("denizen:tampered")))
```

In `WalkerOperationWireSuite`, find the `operations` list that holds `Draw(player, Vector(relic), Location.Deck(CardDeck.Relic), Location.PlayArea(player))`. Add after that entry:

```scala
      Draw(player, Vector(denizen), Location.RegionalDiscard(Region.Cradle),
        Location.Hand(player), StackPosition.Bottom),
```

In `Table.scala`, after `worldDeckTop`, add:

```scala
  /** These cards at the bottom of the world deck, last named at the
    * bottom. */
  def worldDeckBottom(cards: (String | WorldCardId)*)(
      using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.worldCard)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck ++ ids))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchSuite oathdigital.gameplay.OperationApplicationSuite oathdigital.application.AuthoritativeSearchDrawPortSuite"`
Expected: the test sources fail to compile. `SearchRules.cost` still takes a region, `Draw` has no `position`, and `prepare` takes three arguments.

- [ ] **Step 3: Give `Draw` a position**

In `CoreOperations.scala`, replace the doc and head of `Draw` with:

```scala
/** Takes known cards from a prompted draw source, in the order drawn: from
  * the top, or from the bottom when `position` says so (a Search drawn
  * bottom up, catalog batch 6). The destination is intentionally supplied
  * by the prompting rule.
  */
final case class Draw(player: PlayerId, cards: Vector[CardId],
    source: Location, destination: Location,
    position: StackPosition = StackPosition.Top)
    extends CoreOperation:
  override val required: Boolean = true
  require(cards.nonEmpty, "draw must contain at least one card")
  require(position != StackPosition.Unspecified,
    "a draw takes from the top or the bottom")

  val takes: Vector[Take] = cards.map(card => Take(Piece.Card(card),
    player, source, destination, sourcePosition = position))
```

The `children` line stays as it is.

- [ ] **Step 4: Add the window**

In `PowerWindow.scala`, after `SearchPlayAdviser`, add:

```scala
  /** The `search.source` decision: a power adds piles to it, warns one, or
    * makes one unavailable (catalog batch 6, slice 3). */
  case object SearchSourceSelection extends SearchWindow:
    val key = "search.source-selection"
```

- [ ] **Step 5: Make the Search rules total**

Replace `Search.scala` whole:

```scala
package oathdigital.gameplay.actions

import oathdigital.model._

/** Pure Search source and cost rules shared by the walker and projections.
  * Which piles a Search may pick is the source decision's business: a power
  * offers another region's discard pile there (catalog batch 6, slice 3),
  * and these rules price and draw any pile alike.
  */
object SearchRules:
  def cost(ready: ReadyGame, source: SearchSource): Int = source match
    case SearchSource.WorldDeck => ready.game.current.tracks.visionsDrawn match
      case 0 => 2
      case 1 | 2 => 3
      case _ => 4
    case _: SearchSource.RegionalDiscard => 2

  /** How many cards a Search draws before any power changes it. */
  val DrawSize: Int = 3

  /** The cards a Search of `source` draws, in the order drawn: from the
    * top, or from the bottom when `position` says so. World decks use
    * head-as-top; discard piles use last-as-top. A world deck draw stops
    * after its first Vision, counted from the end it draws. `extra` is the
    * number of cards a power adds to the printed draw, or, below zero,
    * takes from it.
    */
  def draw(ready: ReadyGame, source: SearchSource, extra: Int = 0,
      position: StackPosition = StackPosition.Top): Vector[WorldCardId] =
    val topFirst = source match
      case SearchSource.WorldDeck => ready.game.current.commonCards.worldDeck
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse
    val ordered =
      if position == StackPosition.Bottom then topFirst.reverse else topFirst
    val drawn = ordered.take(DrawSize + extra)
    if source == SearchSource.WorldDeck then
      drawn.takeThrough(_.isInstanceOf[VisionId])
    else drawn

  extension [A](values: Vector[A])
    private def takeThrough(stop: A => Boolean): Vector[A] =
      val index = values.indexWhere(stop)
      if index < 0 then values else values.take(index + 1)
```

- [ ] **Step 6: Use the rules in the procedure**

In `SearchProcedure.scala`:

1. In the class doc's tree, change the source line to:
   ```
     *   Decide(search.source),             // a board choose-one: the world deck
     *                                      // and the actor's region's discard;
     *                                      // window = SearchSourceSelection
   ```
2. In `afterModifiers`, give the source `Decide` its window:
   ```scala
       val pick = Decide(sourceDecisionId, actor, DecisionQuery.ChooseOne(
         Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(origin))
           .map(SearchSource.option), heading = Some(heading)),
         window = Some(PowerWindow.SearchSourceSelection))
   ```
3. In `body`, replace the `cost` and `draw` nodes with:
   ```scala
       val cost = BuildOps((ready, _) =>
         Right(Vector(SpendSupply(actor, SearchRules.cost(ready, source)))),
         window = Some(PowerWindow.SearchCost))

       val draw = BuildOps((ready, _) => {
         val cards = SearchRules.draw(ready, source)
         val location = source match {
           case SearchSource.WorldDeck => Location.Deck(CardDeck.World)
           case SearchSource.RegionalDiscard(region) =>
             Location.RegionalDiscard(region)
         }
         // Not `Either.cond`: it builds its value eagerly, and a `Draw`
         // of no card fails its `require`.
         if cards.isEmpty then
           Left(OathViolation.SearchSourceUnavailable(source))
         else Right(Vector[CoreOperation](Draw(actor, cards, location,
           Location.Hand(actor))) ++
           Option.when(source == SearchSource.WorldDeck &&
             cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn))
       }, window = Some(PowerWindow.SearchBeforeDraw))
   ```

In `SearchOptions.scala`, replace the `explain` argument and `empty` with:

```scala
      explain = empty(state))

  /** "The world deck is empty", "The Cradle discard pile is empty". */
  private def empty(state: ReadyGame)(ref: DecisionOptionRef)
      : Option[String] =
    SearchSource.fromRef(ref).filter(SearchRules.draw(state, _).isEmpty)
      .map(source => s"The ${SearchSource.name(source)} is empty")
```

- [ ] **Step 7: Keep the position through the extensions**

Replace the body of `DrawExtension.scala` from `private[search] object DrawExtension:` to the end with:

```scala
private[search] object DrawExtension:

  def extend(operations: Vector[Operation], more: Int): Vector[Operation] =
    operations.map:
      case node: BuildOps if node.window.contains(PowerWindow.SearchBeforeDraw) =>
        node.copy(build = (ready, pending) =>
          node.build(ready, pending).flatMap(extended(ready, more)))
      case other => other

  private def extended(ready: ReadyGame, more: Int)(
      operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match
      case None => Right(operations)
      case Some(draw) => sourceOf(draw.source).map(source =>
        redrawn(draw, source, SearchRules.draw(ready, source,
          extra = draw.cards.size + more - SearchRules.DrawSize,
          position = draw.position)))

  /** `draw` taking `cards` instead, from the end it names, and one more
    * Vision drawn when the world deck gave one. */
  def redrawn(draw: Draw, source: SearchSource, cards: Vector[WorldCardId])
      : Vector[CoreOperation] =
    Vector[CoreOperation](draw.copy(cards = cards)) ++
      Option.when(source == SearchSource.WorldDeck &&
        cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn)

  def sourceOf(location: Location): Either[OathViolation, SearchSource] =
    location match
      case Location.Deck(CardDeck.World) => Right(SearchSource.WorldDeck)
      case Location.RegionalDiscard(region) =>
        Right(SearchSource.RegionalDiscard(region))
      case other => Left(OathViolation.InvalidEventOrder(
        s"a Search draws from a world deck or a regional discard, not $other"))
```

In the object's doc, replace `a regional pile is drawn from its top.` with `a pile is drawn from the end the draw already takes from (Wine of Welcome and Mushrooms draw from the bottom).`

- [ ] **Step 8: Carry the position on the wire**

In `WalkerOperationCodec.scala`, change the `Draw` encoding to:

```scala
      case Draw(player, cards, source, destination, position) => ujson.Obj(
        "kind" -> "draw", "playerId" -> player.value,
        "cards" -> ujson.Arr.from(cards.map(encodeCardRef)),
        "source" -> encodeLocation(source),
        "destination" -> encodeLocation(destination),
        "position" -> encodeStackPosition(position))
```

Change the `"draw"` decoding's last lines to:

```scala
      source <- decodeLocation(value("source"), s"$path.source")
      destination <- decodeLocation(value("destination"), s"$path.destination")
      position <- value.obj.get("position").fold[Either[WireError,
          StackPosition]](Right(StackPosition.Top))(found =>
        decodeStackPosition(found.str, s"$path.position"))
      _ <- Either.cond(position != StackPosition.Unspecified, (),
        InvalidValue(s"$path.position",
          "a draw takes from the top or the bottom"))
    yield Draw(PlayerId(value("playerId").str), cards, source, destination,
      position)
```

- [ ] **Step 9: Drop the region from the draw port**

In `GameRandomPorts.scala`, replace `SearchDrawPort` and its companion with:

```scala
trait SearchDrawPort:
  def prepare(
      ready: oathdigital.model.ReadyGame,
      source: SearchSource
  ): Either[OathViolation, Vector[WorldCardId]]

object SearchDrawPort:
  val authoritative: SearchDrawPort = new SearchDrawPort:
    def prepare(ready: oathdigital.model.ReadyGame, source: SearchSource) =
      Right(SearchRules.draw(ready, source))
```

In `GameApplicationService.scala`, in the `search.source` case, replace the `for` block's generators with:

```scala
            for
              source <- SearchProcedure.sourceOf(answer)
              prepared <- searchDrawPort.prepare(ready, source)
              authoritative = oathdigital.gameplay.actions.SearchRules.draw(
                ready, source)
              _ <- Either.cond(prepared == authoritative, (),
                OathViolation.SearchDrawMismatch(
                  "prepared draw does not match authoritative source order"))
              result <- rules.resolveWalker(state, actor,
                SearchProcedure.sourceDecisionId, answer)
            yield result
```

- [ ] **Step 10: Widen the `Draw` patterns**

- In `ActionLines.scala` (`search`), change `OpStep(Draw(_, cards, from, _), before, after)` to `OpStep(Draw(_, cards, from, _, _), before, after)`. Task 6 rewrites this method.
- In `DetailLines.scala`, change `case Draw(player, cards, _, _) if` to `case Draw(player, cards, _, _, _) if`.

- [ ] **Step 11: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchSuite oathdigital.gameplay.OperationApplicationSuite oathdigital.application.AuthoritativeSearchDrawPortSuite oathdigital.application.GameApplicationServiceActionsSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: PASS.

Then run: `./sbtw test`
Expected: PASS, with 5 tests more than the baseline.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/actions/Search.scala src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala src/main/scala/oathdigital/gameplay/powers/search/DrawExtension.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/application/GameRandomPorts.scala src/main/scala/oathdigital/application/GameApplicationService.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/test/scala/oathdigital/testkit/Table.scala src/test/scala/oathdigital/gameplay/SearchSuite.scala src/test/scala/oathdigital/gameplay/OperationApplicationSuite.scala src/test/scala/oathdigital/application/SearchDrawPortContract.scala src/test/scala/oathdigital/application/GameApplicationServiceActionsSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "feat(search): price and draw any pile from either end, and window the source decision"
```

---

### Task 3: Observatory and Errand Boy

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/Observatory.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/ErrandBoy.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `ObservatoryCard` and the `SiteOnly` import)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `ErrandBoyCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.search` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/search/ShownOptions.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/ObservatorySuite.scala`, `src/test/scala/oathdigital/gameplay/powers/search/ErrandBoySuite.scala`

**Interfaces:**
- Consumes: `PowerWindow.SearchSourceSelection` and `SearchRules` from Task 2.
- Produces:
  - `SearchSources.withPiles(operations: Vector[Operation], regions: Vector[Region]): Vector[Operation]`
  - `SearchSources.warned(operations: Vector[Operation], warns: DecisionOptionRef => Boolean, reason: String): Vector[Operation]`
  - `SearchSources.withheld(operations: Vector[Operation], withholds: DecisionOptionRef => Boolean, reason: String): Vector[Operation]`
  - The test helpers `ShownOptions.atParkedDecision(state)`, `ShownOptions.sourcesAfterPicking(ready, modifiers)` and `ShownOptions.optionFor(shown, ref)`.
  - `Observatory.id` and `ErrandBoy.id`, plus the cards `ObservatoryCard` and `ErrandBoyCard` in `oathdigital.gameplay.powers.search`.

- [ ] **Step 1: Write the test helper**

Create `src/test/scala/oathdigital/gameplay/powers/search/ShownOptions.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{DecisionOptionProjection,
  DecisionQueryProjection}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** A parked Search decision's options as p1 is shown them: each option's
  * state, badge and reason, after the procedure's pricing and the powers'
  * warnings. */
object ShownOptions:
  /** The options of the choose-one `state` is parked on, as p1 sees
    * them. */
  def atParkedDecision(state: OathState): Vector[DecisionOptionProjection] =
    new GameProjector(catalog).project("search", LoadedGame(state, 1), p1)
      .walkerDecision.flatMap(_.query).collect {
        case one: DecisionQueryProjection.ChooseOne => one.options
      }.getOrElse(Vector.empty)

  /** The source decision's options once p1 has picked every one of
    * `modifiers` and started the Search. */
  def sourcesAfterPicking(ready: ReadyGame, modifiers: Vector[PowerId])(
      using munit.Location): Vector[DecisionOptionProjection] =
    atParkedDecision(accepted(ModifierPlay.started(SearchFixture.rules,
      Ready(ready), ActionRef.Search, p1,
      PowerWindow.SearchModifierSelection, modifiers)).state)

  /** The option among `shown` that names `ref`. */
  def optionFor(shown: Vector[DecisionOptionProjection],
      ref: DecisionOptionRef): Option[DecisionOptionProjection] =
    shown.find(option => option.kind == ref.kind && option.id == ref.wireId)
```

- [ ] **Step 2: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/search/ObservatorySuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Observatory (card 64): while the actor's pawn is at its site, every
  * region's discard pile is offered, each at 2 Supply (catalog batch 6,
  * slice 3). */
class ObservatorySuite extends munit.FunSuite:
  import SearchFixture.rules

  private val observatory = CatalogNames.denizen("Observatory")
  private val modifiers = Vector(Observatory.id)
  /** Three plain denizens in each region's discard pile; none is played. */
  private val piles: Map[Region, Vector[DenizenId]] = Map(
    Region.Cradle -> Vector("Threatening Roar", "Fae Merchant",
      "Second Chance"),
    Region.Provinces -> Vector("Wrestlers", "Mercenaries", "Homesteaders"),
    Region.Hinterland -> Vector("Defame", "Scryer", "Blackmail"))
    .view.mapValues(_.map(CatalogNames.denizen(_))).toMap
  /** The world deck, then each region's pile in region order. */
  private val everySource: Vector[DecisionOptionRef] =
    (SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_)))
      .map(SearchSource.ref)

  /** p1 at Ancient City (Cradle), which holds Observatory, with 5 Supply
    * and three cards in each region's discard pile. */
  private def besideObservatory: Table = Region.all.foldLeft(Table.start
      .pawn(p1, "Ancient City").denizen(observatory, at = "Ancient City")
      .supply(p1, 5))((table, region) => table.discarded(region,
    piles(region)*))

  test("picked, every region's discard pile is offered at 2 Supply"):
    val shown = ShownOptions.sourcesAfterPicking(besideObservatory.ready,
      modifiers)
    assertEquals(shown.map(option => (option.kind, option.id, option.state,
      option.badge)), everySource.map(ref => (ref.kind, ref.wireId,
      "available", Some("2 Supply"))))

  test("a Search of another region's pile draws its top three for 2 " +
      "Supply"):
    val searched = accepted(SearchPlay.searched(rules,
      Ready(besideObservatory.ready), p1,
      SearchSource.RegionalDiscard(Region.Provinces), modifiers))
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1),
      piles(Region.Provinces).reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("it is offered only while p1's pawn is at its site"):
    val offered = (ready: ReadyGame) =>
      accepted(rules.offerableWalkerPowers(ready, p1, ActionRef.Search))
        .map(_.id)
    assert(offered(besideObservatory.ready).contains(Observatory.id))
    assert(!offered(besideObservatory.pawn(p1, "Broken Peaks").ready)
      .contains(Observatory.id))
```

Create `src/test/scala/oathdigital/gameplay/powers/search/ErrandBoySuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{ModifierPlay, SearchPlay}
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Errand Boy (card 11): for 1 favor, the other regions' discard piles are
  * offered, each at 2 Supply. The actor's own pile and the world deck stay
  * choosable, warned (catalog batch 6, slice 3). */
class ErrandBoySuite extends munit.FunSuite:
  import SearchFixture.rules

  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(ErrandBoy.id)
  private val provinces = Vector("Wrestlers", "Mercenaries", "Homesteaders")
    .map(CatalogNames.denizen(_))
  private val hinterland = Vector("Defame", "Scryer", "Blackmail")
    .map(CatalogNames.denizen(_))
  private val everySource: Vector[DecisionOptionRef] =
    (SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_)))
      .map(SearchSource.ref)

  /** p1 at Ancient City (Cradle) holding Errand Boy faceup, with the
    * start's 1 favor, 5 Supply, and three cards in the Provinces and
    * Hinterland discard piles. The Cradle pile holds one. */
  private def holdingErrandBoy: Table = Table.start
    .pawn(p1, "Ancient City").adviser(p1, errandBoy).supply(p1, 5)
    .discarded(Region.Cradle, "Threatening Roar")
    .discarded(Region.Provinces, provinces*)
    .discarded(Region.Hinterland, hinterland*)

  test("picked, it places 1 favor on itself and offers the other regions' " +
      "piles at 2 Supply"):
    val started = accepted(ModifierPlay.started(rules,
      Ready(holdingErrandBoy.ready), ActionRef.Search, p1,
      PowerWindow.SearchModifierSelection, modifiers))
    val after = Look(SearchFixture.after(started))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.tokensOn(errandBoy), Tokens(1, 0))
    val shown = ShownOptions.atParkedDecision(started.state)
    assertEquals(shown.map(option => (option.kind, option.id, option.badge)),
      everySource.map(ref => (ref.kind, ref.wireId, Some("2 Supply"))))

  test("the world deck and the actor's own pile stay choosable, warned"):
    val shown = ShownOptions.sourcesAfterPicking(holdingErrandBoy.ready,
      modifiers)
    def stateOf(source: SearchSource) = ShownOptions.optionFor(shown,
      SearchSource.ref(source)).map(option => (option.state, option.reason))
    Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(Region.Cradle))
      .foreach(source => assertEquals(stateOf(source),
        Some(("warning", Some(ErrandBoy.idle)))))
    Vector(Region.Provinces, Region.Hinterland).foreach(region =>
      assertEquals(stateOf(SearchSource.RegionalDiscard(region)),
        Some(("available", None))))

  test("a Search of another region's pile draws its top three"):
    val searched = accepted(SearchPlay.searched(rules,
      Ready(holdingErrandBoy.ready), p1,
      SearchSource.RegionalDiscard(Region.Hinterland), modifiers))
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1), hinterland.reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("with Observatory, each pile is offered once"):
    val ready = holdingErrandBoy.denizen("Observatory", at = "Ancient City")
      .ready
    val shown = ShownOptions.sourcesAfterPicking(ready,
      Vector(Observatory.id, ErrandBoy.id))
    assertEquals(shown.map(option => (option.kind, option.id)),
      everySource.map(ref => (ref.kind, ref.wireId)))
```

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.ObservatorySuite oathdigital.gameplay.powers.search.ErrandBoySuite"`
Expected: the test sources fail to compile: `not found: Observatory` and `not found: ErrandBoy`.

- [ ] **Step 4: Write the shared source helper**

Create `src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.model._

/** What the Search source powers share (catalog batch 6, slice 3). Each
  * rewrites the source decision, which its window (`SearchSourceSelection`)
  * folds as `Vector(decide)`. Every other operation is left alone.
  */
private[search] object SearchSources:

  /** `operations` with each of `regions`' discard piles added to the source
    * decision, after the options it offers, unless it offers that pile
    * already. */
  def withPiles(operations: Vector[Operation], regions: Vector[Region])
      : Vector[Operation] = changed(operations) { options =>
    val offered = options.map(_.ref).toSet
    options ++ regions.map(region =>
      SearchSource.option(SearchSource.RegionalDiscard(region)))
      .filterNot(option => offered(option.ref))
  }

  /** `operations` with each available source `warns` names warned with
    * `reason`. It stays choosable. */
  def warned(operations: Vector[Operation],
      warns: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(operations)(_.map(option =>
    if option.available && warns(option.ref) then
      DecisionOption.Warned(option, OptionReason.Written(reason))
    else option))

  /** `operations` with each available source `withholds` names unavailable,
    * with `reason`. */
  def withheld(operations: Vector[Operation],
      withholds: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(operations)(_.map(option =>
    if option.available && withholds(option.ref) then
      DecisionOption.Unavailable(option, OptionReason.Written(reason))
    else option))

  private def changed(operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] = operations.map {
    case decide: Decide
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      decide.query match
        case one: DecisionQuery.ChooseOne =>
          decide.copy(query = one.copy(options = change(one.options)))
        case _ => decide
    case other => other
  }
```

- [ ] **Step 5: Write Observatory**

Delete the whole `ObservatoryCard` object from `HoldingArcaneDenizens.scala`. Then change that file's catalog import to `import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}`, because Observatory was its only `SiteOnly` card.

Create `src/main/scala/oathdigital/gameplay/powers/search/Observatory.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ObservatoryCard extends Denizen(DenizenId("64"), "Observatory", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.observatory"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is here, you may draw from any one discard " +
      "pile.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Observatory (card 64, site-only), a free selected Search modifier,
  * usable while the actor's pawn is at its site (`PowerAccess`). Every
  * region's discard pile is offered at the source decision, each at 2
  * Supply, after the printed two and in region order (catalog batch 6,
  * slice 3). It writes no line: the start line names it, and the Search
  * line names the pile.
  */
final case class Observatory private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ObservatoryCard.id
  def id: PowerId = Observatory.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
      SearchSources.withPiles(operations, Region.all))))

object Observatory:
  val id: PowerId = ObservatoryCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Observatory =
    new Observatory(catalog)
```

- [ ] **Step 6: Write Errand Boy**

Delete the whole `ErrandBoyCard` object from `HoldingBeastDenizens.scala`. That file's imports stay as they are.

Create `src/main/scala/oathdigital/gameplay/powers/search/ErrandBoy.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ErrandBoyCard extends Denizen(DenizenId("11"), "Errand Boy", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.errand-boy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may draw from a discard pile in a different region " +
      "instead of yours.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Errand Boy (card 11), a selected Search modifier costing 1 favor placed
  * on it, paid when it is picked whatever the pile. The other regions'
  * discard piles are offered at the source decision, each at 2 Supply,
  * after the printed two and in region order (catalog batch 6, slice 3).
  *
  * The actor's own pile and the world deck stay choosable, warned that
  * Errand Boy does nothing there. The warning names the actor's region,
  * which the `warning` hook cannot see, so the Transform marks it. It
  * writes no line: the start line names it, and the Search line names the
  * pile.
  */
final case class ErrandBoy private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ErrandBoyCard.id
  def id: PowerId = ErrandBoy.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = ErrandBoy.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      SearchProcedure.actorRegion(ctx.state, ctx.activePlayer)
        .fold(_ => operations, offered(operations, _)))))

  /** The other regions' piles added, and the sources it leaves alone
    * warned. */
  private def offered(operations: Vector[Operation], own: Region)
      : Vector[Operation] =
    val idle: Set[DecisionOptionRef] = Set[SearchSource](
      SearchSource.WorldDeck, SearchSource.RegionalDiscard(own))
      .map(SearchSource.ref)
    SearchSources.warned(SearchSources.withPiles(operations,
      Region.all.filterNot(_ == own)), idle, ErrandBoy.idle)

object ErrandBoy:
  val id: PowerId = ErrandBoyCard.power.id
  val Favor: Int = 1
  val idle: String = "Errand Boy does nothing here"

  def forCatalog(catalog: ExecutableCatalog): ErrandBoy =
    new ErrandBoy(catalog)
```

- [ ] **Step 7: Register both**

In `NewFoundations.scala`, change the `powers.search` import to:

```scala
import oathdigital.gameplay.powers.search.{AuguryCard, CropRotationCard,
  DisciplesCard, ErrandBoyCard, ForcedLaborCard, HuntingPartyCard,
  NewsFromAfarCard, ObservatoryCard, TruthfulHarpCard}
```

The card lists keep `ErrandBoyCard` and `ObservatoryCard` where they stand.

In `ActionModifiers.scala`, change the search import to:

```scala
import oathdigital.gameplay.powers.search.{Augury, CropRotation, Disciples,
  ErrandBoy, ForcedLabor, HuntingParty, NewsFromAfar, Observatory,
  TruthfulHarp}
```

Then add after `Vector(HuntingParty.forCatalog(catalog)) ++`:

```scala
      Vector(Observatory.forCatalog(catalog)) ++
      Vector(ErrandBoy.forCatalog(catalog)) ++
```

- [ ] **Step 8: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.*"`
Expected: PASS.

Then run: `./sbtw test`
Expected: PASS, with 12 tests more than the baseline.

Errand Boy and Observatory are now Search modifiers. A suite that holds one of them faceup within p1's reach and starts a Search now parks on `search.modifiers`. If such a suite fails, change its filler card to `"Wrestlers"`, which has no production power, and say so in the commit message. The plan's survey found no such suite, so expect none.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala src/main/scala/oathdigital/gameplay/powers/search/Observatory.scala src/main/scala/oathdigital/gameplay/powers/search/ErrandBoy.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/search/ShownOptions.scala src/test/scala/oathdigital/gameplay/powers/search/ObservatorySuite.scala src/test/scala/oathdigital/gameplay/powers/search/ErrandBoySuite.scala
git commit -m "feat(powers): Observatory and Errand Boy offer other regions' discard piles"
```

---

### Task 4: Mushrooms and Wine of Welcome

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/BottomDraw.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/WineOfWelcome.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `MushroomsCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `WineOfWelcomeCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.search` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/MushroomsSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/search/WineOfWelcomeSuite.scala`

**Interfaces:**
- Consumes:
  - From Task 2: `SearchRules.draw(..., position)`, `DrawExtension.sourceOf` and `DrawExtension.redrawn`.
  - From Task 3: `SearchSources.withheld` and the `ShownOptions` helpers.
- Produces:
  - `BottomDraw.Priority: Int = -1`
  - `BottomDraw.reshaped(operations: Vector[Operation], count: Int, takes: SearchSource => Boolean): Vector[Operation]`
  - `Mushrooms.id`, `Mushrooms.worldDeck` and `MushroomsCard`.
  - `WineOfWelcome.id`, `WineOfWelcome.discardWarning`, `WineOfWelcome.withMushrooms` and `WineOfWelcomeCard`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/search/MushroomsSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Mushrooms (card 183): for 1 secret, the Search spends no Supply and
  * draws one card from the bottom of a discard pile; the world deck is
  * unavailable (catalog batch 6, slice 3). */
class MushroomsSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val mushrooms = CatalogNames.denizen("Mushrooms")
  private val modifiers = Vector(Mushrooms.id)
  /** The Cradle pile, first named at the bottom. */
  private val cradle = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))
  private val provinces = Vector("Wrestlers", "Mercenaries", "Homesteaders")
    .map(CatalogNames.denizen(_))

  /** p1 at Ancient City (Cradle), which holds Mushrooms, with 5 Supply,
    * the start's 1 faceup secret, and three cards in the Cradle and
    * Provinces discard piles. */
  private def besideMushrooms: Table = Table.start
    .pawn(p1, "Ancient City").denizen(mushrooms, at = "Ancient City")
    .supply(p1, 5).discarded(Region.Cradle, cradle*)
    .discarded(Region.Provinces, provinces*)

  private def searched(table: Table, pile: Region,
      picked: Vector[PowerId]): ReadyGame =
    SearchFixture.after(accepted(SearchPlay.searched(rules,
      Ready(table.ready), p1, SearchSource.RegionalDiscard(pile), picked)))

  test("picked, the world deck is unavailable and the pile costs no " +
      "Supply"):
    val shown = ShownOptions.sourcesAfterPicking(besideMushrooms.ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(Mushrooms.worldDeck))))
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(
      SearchSource.RegionalDiscard(Region.Cradle))).map(option =>
      (option.state, option.badge)), Some(("available", Some("0 Supply"))))

  test("a Search of p1's pile pays a secret onto it and draws the bottom " +
      "card"):
    val after = searched(besideMushrooms, Region.Cradle, modifiers)
    assertEquals(after.game.current.temporaryHands(p1), Vector(cradle(0)))
    assertEquals(Look(after).supply(p1), 5)
    assertEquals(Look(after).tokensOn(mushrooms), Tokens(0, 1))
    assertEquals(Look(after).player(p1).board.faceUpSecrets, 0)

  test("with Augury it draws two cards from the bottom"):
    val table = besideMushrooms.denizen("Augury", at = "Ancient City")
    val after = searched(table, Region.Cradle,
      Vector(Mushrooms.id, Augury.id))
    assertEquals(after.game.current.temporaryHands(p1), cradle.take(2))

  test("with Observatory it draws the bottom card of another region's " +
      "pile"):
    val table = besideMushrooms.denizen("Observatory", at = "Ancient City")
    val after = searched(table, Region.Provinces,
      Vector(Mushrooms.id, Observatory.id))
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(provinces(0)))
```

Create `src/test/scala/oathdigital/gameplay/powers/search/WineOfWelcomeSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Wine of Welcome (relic R33): a Search of the world deck draws from its
  * bottom, stopping after a Vision as usual (catalog batch 6, slice 3). */
class WineOfWelcomeSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val modifiers = Vector(WineOfWelcome.id)
  /** The world deck's bottom, last named at the very bottom. */
  private val bottom = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  /** p1 at Ancient City holding Wine of Welcome faceup, with 5 Supply and
    * one card in the Cradle discard pile. */
  private def holdingWine: Table = Table.start.pawn(p1, "Ancient City")
    .relic(p1, "Wine of Welcome").supply(p1, 5)
    .discarded(Region.Cradle, "Wrestlers")

  private def searchedWorld(table: Table): ReadyGame =
    SearchFixture.after(accepted(SearchFixture.start(table.ready,
      modifiers)))

  test("picked, a world Search draws the bottom three, bottom first"):
    val after = searchedWorld(holdingWine.worldDeckBottom(bottom*))
    assertEquals(after.game.current.temporaryHands(p1), bottom.reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("the draw from the bottom stops after a Vision"):
    val table = holdingWine.worldDeckBottom(bottom(0), VisionRules.Faith,
      bottom(1))
    val before = table.ready.game.current.tracks.visionsDrawn
    val after = searchedWorld(table)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(bottom(1), VisionRules.Faith))
    assertEquals(after.game.current.tracks.visionsDrawn, before + 1)

  test("while it is picked, a discard pile warns and stays choosable"):
    val shown = ShownOptions.sourcesAfterPicking(holdingWine.ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(
      SearchSource.RegionalDiscard(Region.Cradle))).map(option =>
      (option.state, option.reason)),
      Some(("warning", Some(WineOfWelcome.discardWarning))))
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(_.state),
      Some("available"))

  test("Wine of Welcome and Mushrooms warn each other"):
    val ready = holdingWine.denizen("Mushrooms", at = "Ancient City").ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val decisionId =
      ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
    def shownAfterPicking(power: PowerId) = ShownOptions.atParkedDecision(
      accepted(rules.resolveWalker(begun.state, p1, decisionId,
        ModifierPlay.pick(power))).state)
    val warned = Some(("warning", Some(WineOfWelcome.withMushrooms)))
    assertEquals(ShownOptions.optionFor(shownAfterPicking(Mushrooms.id),
      DecisionOptionRef.Relic(WineOfWelcomeCard.id)).map(option =>
      (option.state, option.reason)), warned)
    assertEquals(ShownOptions.optionFor(shownAfterPicking(WineOfWelcome.id),
      DecisionOptionRef.Denizen(MushroomsCard.id)).map(option =>
      (option.state, option.reason)), warned)
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.MushroomsSuite oathdigital.gameplay.powers.search.WineOfWelcomeSuite"`
Expected: the test sources fail to compile: `not found: Mushrooms` and `not found: WineOfWelcome`.

- [ ] **Step 3: Write the bottom draw**

Create `src/main/scala/oathdigital/gameplay/powers/search/BottomDraw.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._

/** How a Search modifier makes the draw take cards from the bottom of its
  * pile (Wine of Welcome, Mushrooms).
  *
  * It wraps the Search's draw node, as [[DrawExtension]] does, and draws
  * `count` cards from the bottom of the pile the node drew from, when
  * `takes` accepts that pile. A power using it folds at [[Priority]],
  * before the draw extensions, so an extension then adds cards from the
  * same end. The world deck's Vision stop still applies, counted from the
  * bottom.
  */
private[search] object BottomDraw:
  /** Before the draw extensions, which fold at 0. */
  val Priority: Int = -1

  def reshaped(operations: Vector[Operation], count: Int,
      takes: SearchSource => Boolean): Vector[Operation] =
    operations.map:
      case node: BuildOps if node.window.contains(PowerWindow.SearchBeforeDraw) =>
        node.copy(build = (ready, pending) =>
          node.build(ready, pending).flatMap(redrawn(ready, count, takes)))
      case other => other

  private def redrawn(ready: ReadyGame, count: Int,
      takes: SearchSource => Boolean)(operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match
      case None => Right(operations)
      case Some(draw) => DrawExtension.sourceOf(draw.source).map(source =>
        if !takes(source) then operations
        else DrawExtension.redrawn(
          draw.copy(position = StackPosition.Bottom), source,
          SearchRules.draw(ready, source,
            extra = count - SearchRules.DrawSize,
            position = StackPosition.Bottom)))
```

- [ ] **Step 4: Write Mushrooms**

Delete the whole `MushroomsCard` object from `HoldingBeastDenizens.scala`. `SiteOnly` stays imported there, because Giant Python and Forest Council use it.

Create `src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object MushroomsCard extends Denizen(DenizenId("183"), "Mushrooms", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mushrooms"),
    persistent = false, cost = Cost(secret = 1),
    text = "Spend no Supply, but draw only one card _(not three)_ from " +
      "the bottom of your region's discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mushrooms (card 183, site-only), a selected Search modifier usable while
  * the actor's pawn is at its site, costing 1 secret placed on it (catalog
  * batch 6, slice 3):
  *
  *  - The world deck is unavailable at the source decision.
  *  - The Search spends no Supply. A toll such as Forced Labor's is a
  *    separate operation and is still paid, as with News from Afar.
  *  - It draws one card from the bottom of the discard pile picked, which
  *    may be another region's when Observatory or Errand Boy offers it. A
  *    draw extension such as Augury adds cards, still from the bottom: it
  *    folds first ([[BottomDraw.Priority]]).
  *  - It is warned while Wine of Welcome is picked, which then does
  *    nothing.
  *
  * It writes no line: the start line names it, and the Search line says
  * the card came from the bottom.
  */
final case class Mushrooms private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = MushroomsCard.id
  def id: PowerId = Mushrooms.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(secret = Mushrooms.Secrets)
  override def priority: Int = BottomDraw.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
      SearchSources.withheld(operations,
        _ == SearchSource.ref(SearchSource.WorldDeck), Mushrooms.worldDeck))),
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx.activePlayer, operations))),
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      BottomDraw.reshaped(operations, Mushrooms.Cards,
        _.isInstanceOf[SearchSource.RegionalDiscard]))))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      WineOfWelcomeCard.id))(OfferReason.Warned(WineOfWelcome.withMushrooms))

object Mushrooms:
  val id: PowerId = MushroomsCard.power.id
  val Secrets: Int = 1
  /** The cards it draws, before any draw extension. */
  val Cards: Int = 1
  val worldDeck: String = "Mushrooms draws from a discard pile"

  def forCatalog(catalog: ExecutableCatalog): Mushrooms =
    new Mushrooms(catalog)
```

- [ ] **Step 5: Write Wine of Welcome**

Delete the whole `WineOfWelcomeCard` object from `HoldingRelics.scala`. That file's imports stay as they are.

Create `src/main/scala/oathdigital/gameplay/powers/search/WineOfWelcome.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.SearchRules
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object WineOfWelcomeCard extends Relic(RelicId("R33"), "Wine of Welcome", value = 7, defense = 1):
  val power = PrintedPower(PowerId("relic.wine-of-welcome"),
    persistent = false, cost = Cost.free,
    text = "Draw from the bottom of the world deck instead of the top.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wine of Welcome (relic R33), a free selected Search modifier: a Search
  * of the world deck draws from its bottom, stopping after a Vision as
  * usual (catalog batch 6, slice 3). It folds before the draw extensions
  * ([[BottomDraw.Priority]]), so they add cards from the bottom too.
  *
  * While it is picked, each discard pile stays choosable, warned that it
  * needs the world deck. It is warned while Mushrooms is picked, which
  * makes the world deck unavailable. It writes no line: the start line
  * names it, and the Search line says the cards came from the bottom.
  */
final case class WineOfWelcome private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = WineOfWelcomeCard.id
  def id: PowerId = WineOfWelcome.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def priority: Int = BottomDraw.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      BottomDraw.reshaped(operations, SearchRules.DrawSize,
        _ == SearchSource.WorldDeck))))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case _: DecisionOptionRef.DiscardPile
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(WineOfWelcome.discardWarning))
    case _ => None

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      MushroomsCard.id))(OfferReason.Warned(WineOfWelcome.withMushrooms))

object WineOfWelcome:
  val id: PowerId = WineOfWelcomeCard.power.id
  val discardWarning: String = "Wine of Welcome needs the world deck"
  /** Both Wine of Welcome's and Mushrooms' warning, whichever is picked
    * first. */
  val withMushrooms: String = "Wine of Welcome does nothing with Mushrooms"

  def forCatalog(catalog: ExecutableCatalog): WineOfWelcome =
    new WineOfWelcome(catalog)
```

- [ ] **Step 6: Register both**

In `NewFoundations.scala`, change the `powers.search` import to:

```scala
import oathdigital.gameplay.powers.search.{AuguryCard, CropRotationCard,
  DisciplesCard, ErrandBoyCard, ForcedLaborCard, HuntingPartyCard,
  MushroomsCard, NewsFromAfarCard, ObservatoryCard, TruthfulHarpCard,
  WineOfWelcomeCard}
```

In `ActionModifiers.scala`, add `Mushrooms` and `WineOfWelcome` to the search import, in alphabetical order. Then add after `Vector(ErrandBoy.forCatalog(catalog)) ++`:

```scala
      Vector(Mushrooms.forCatalog(catalog)) ++
      Vector(WineOfWelcome.forCatalog(catalog)) ++
```

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.*"`
Expected: PASS. This includes `AugurySuite` and `TruthfulHarpSuite`, whose draws now keep their position.

Then run: `./sbtw test`
Expected: PASS, with 20 tests more than the baseline. Apply Task 3 Step 8's filler rule to Mushrooms too.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/BottomDraw.scala src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala src/main/scala/oathdigital/gameplay/powers/search/WineOfWelcome.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/search/MushroomsSuite.scala src/test/scala/oathdigital/gameplay/powers/search/WineOfWelcomeSuite.scala
git commit -m "feat(powers): Mushrooms and Wine of Welcome draw from the bottom"
```

---

### Task 5: Painted Trumpet

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/PaintedTrumpet.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala` (`offerReason` and its doc)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `PaintedTrumpetCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.search` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/PaintedTrumpetSuite.scala`

**Interfaces:**
- Consumes: `ShownOptions` (Task 3), plus `MushroomsCard` and `Mushrooms.id` (Task 4).
- Produces: `PaintedTrumpet.id`, `PaintedTrumpet.Priority`, `PaintedTrumpet.deckWarning`, `PaintedTrumpet.notWithMushrooms`, `Mushrooms.notWithTrumpet` and `PaintedTrumpetCard`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/search/PaintedTrumpetSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{ModifierPlay, SearchPlay}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Painted Trumpet (relic R23): a Search of a discard pile draws the top
  * card of each discard pile instead, and nothing modifies that draw
  * (catalog batch 6, slice 3). */
class PaintedTrumpetSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val modifiers = Vector(PaintedTrumpet.id)
  private val cradleTop = CatalogNames.denizen("Fae Merchant")
  private val cradleBelow = CatalogNames.denizen("Threatening Roar")
  private val provincesTop = CatalogNames.denizen("Mercenaries")
  private val hinterlandTop = CatalogNames.denizen("Scryer")
  private val deckTop = Vector("Second Chance", "Homesteaders", "Messenger")
    .map(CatalogNames.denizen(_))

  /** p1 at Ancient City holding Painted Trumpet faceup, with 5 Supply and
    * the world deck topped by three plain cards. The Provinces and
    * Hinterland piles hold two cards each. The Cradle pile holds two
    * unless `emptyCradle`. */
  private def holdingTrumpet(emptyCradle: Boolean = false): Table =
    val table = Table.start.pawn(p1, "Ancient City")
      .relic(p1, "Painted Trumpet").supply(p1, 5).worldDeckTop(deckTop*)
      .discarded(Region.Provinces, "Wrestlers", "Mercenaries")
      .discarded(Region.Hinterland, "Defame", "Scryer")
    if emptyCradle then table
    else table.discarded(Region.Cradle, cradleBelow, cradleTop)

  private def searched(table: Table, pile: SearchSource,
      picked: Vector[PowerId]): ReadyGame =
    SearchFixture.after(accepted(SearchPlay.searched(rules,
      Ready(table.ready), p1, pile, picked)))

  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  test("a discard pile picked draws the top card of each pile, for 2 " +
      "Supply"):
    val after = searched(holdingTrumpet(), cradle, modifiers)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(cradleTop, provincesTop, hinterlandTop))
    assertEquals(Look(after).supply(p1), 5 - 2)
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(cradleBelow))

  test("Augury adds no card to its draw"):
    val table = holdingTrumpet().denizen("Augury", at = "Ancient City")
    val after = searched(table, cradle,
      Vector(PaintedTrumpet.id, Augury.id))
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(cradleTop, provincesTop, hinterlandTop))

  test("an empty pile may be picked while another pile has a card"):
    val table = holdingTrumpet(emptyCradle = true)
    val shown = ShownOptions.sourcesAfterPicking(table.ready, modifiers)
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(cradle))
      .map(option => (option.state, option.badge)),
      Some(("available", Some("2 Supply"))))
    assertEquals(searched(table, cradle, modifiers).game.current
      .temporaryHands(p1), Vector(provincesTop, hinterlandTop))

  test("a world Search is unchanged, and the world deck warns"):
    val after = searched(holdingTrumpet(), SearchSource.WorldDeck,
      modifiers)
    assertEquals(after.game.current.temporaryHands(p1), deckTop)
    val shown = ShownOptions.sourcesAfterPicking(holdingTrumpet().ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(option =>
      (option.state, option.reason)),
      Some(("warning", Some(PaintedTrumpet.deckWarning))))

  test("Painted Trumpet and Mushrooms exclude each other"):
    val ready = holdingTrumpet().denizen("Mushrooms", at = "Ancient City")
      .ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val decisionId =
      ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
    def shownAfterPicking(power: PowerId) = ShownOptions.atParkedDecision(
      accepted(rules.resolveWalker(begun.state, p1, decisionId,
        ModifierPlay.pick(power))).state)
    assertEquals(ShownOptions.optionFor(shownAfterPicking(Mushrooms.id),
      DecisionOptionRef.Relic(PaintedTrumpetCard.id)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(PaintedTrumpet.notWithMushrooms))))
    assertEquals(ShownOptions.optionFor(shownAfterPicking(PaintedTrumpet.id),
      DecisionOptionRef.Denizen(MushroomsCard.id)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(Mushrooms.notWithTrumpet))))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.PaintedTrumpetSuite"`
Expected: the test sources fail to compile: `not found: PaintedTrumpet`.

- [ ] **Step 3: Write Painted Trumpet**

Delete the whole `PaintedTrumpetCard` object from `HoldingRelics.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/search/PaintedTrumpet.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object PaintedTrumpetCard extends Relic(RelicId("R23"), "Painted Trumpet", value = 46, defense = 1):
  val power = PrintedPower(PowerId("relic.painted-trumpet"),
    persistent = false, cost = Cost.free,
    text = "Instead of drawing from one discard pile, you may draw the " +
      "top card from each discard pile. This draw amount " +
      "**cannot** be modified.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Painted Trumpet (relic R23), a free selected Search modifier (catalog
  * batch 6, slice 3). A Search of a discard pile draws the top card of
  * each discard pile instead: one `Draw` per pile that holds a card, in
  * region order. The Search costs what the pile picked costs.
  *
  * The pile picked may be empty while another holds a card. The draw
  * cannot be modified: it folds after the draw extensions
  * ([[PaintedTrumpet.Priority]]) and replaces what they drew. Truthful
  * Harp still reveals the cards drawn.
  *
  * A Search of the world deck is unchanged, and the world deck is warned
  * while it is picked. It cannot be picked with Mushrooms. It writes no
  * line: the Search line says the cards came from each discard pile.
  */
final case class PaintedTrumpet private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = PaintedTrumpetCard.id
  def id: PowerId = PaintedTrumpet.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def priority: Int = PaintedTrumpet.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      operations.map {
        case node: BuildOps
            if node.window.contains(PowerWindow.SearchBeforeDraw) =>
          node.copy(build = (ready, pending) => picked(pending) match {
            case Some(pile: SearchSource.RegionalDiscard) =>
              tops(ready, ctx.activePlayer, pile)
            case _ => node.build(ready, pending)
          })
        case other => other
      })))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case DecisionOptionRef.Deck(CardDeck.World)
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(PaintedTrumpet.deckWarning))
    case _ => None

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      MushroomsCard.id))(OfferReason.Unavailable(
      PaintedTrumpet.notWithMushrooms))

  /** The pile the source decision picked. */
  private def picked(pending: PendingTree): Option[SearchSource] =
    PowerAnswers.one(pending, SearchProcedure.sourceDecisionId)
      .flatMap(SearchSource.fromRef)

  /** The top card of each discard pile that holds one, in region order. */
  private def tops(ready: ReadyGame, actor: PlayerId,
      pile: SearchSource): Either[OathViolation, Vector[CoreOperation]] =
    val draws = Region.all.flatMap(region =>
      ready.game.current.commonCards.discard(region).lastOption.map(card =>
        Draw(actor, Vector(card), Location.RegionalDiscard(region),
          Location.Hand(actor))))
    Either.cond(draws.nonEmpty, draws,
      OathViolation.SearchSourceUnavailable(pile))

object PaintedTrumpet:
  val id: PowerId = PaintedTrumpetCard.power.id
  /** After the draw extensions, which fold at 0, so that nothing modifies
    * its draw. */
  val Priority: Int = 1
  val deckWarning: String = "Painted Trumpet needs a discard pile"
  val notWithMushrooms: String = "Cannot be used with Mushrooms"

  def forCatalog(catalog: ExecutableCatalog): PaintedTrumpet =
    new PaintedTrumpet(catalog)
```

- [ ] **Step 4: Make Mushrooms refuse Painted Trumpet**

In `Mushrooms.scala`:

1. In the class doc, replace the last bullet with:
   ```
     *  - It cannot be picked with Painted Trumpet, and is warned while Wine
     *    of Welcome is picked, which then does nothing.
   ```
2. Replace `offerReason` with:
   ```scala
     override def offerReason(ctx: PowerCtx): Option[OfferReason] =
       if ModifierPicks.isPicked(ctx.answered, ctx.window,
           PaintedTrumpetCard.id) then
         Some(OfferReason.Unavailable(Mushrooms.notWithTrumpet))
       else Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
         WineOfWelcomeCard.id))(OfferReason.Warned(
         WineOfWelcome.withMushrooms))
   ```
3. In `object Mushrooms`, add after `worldDeck`:
   ```scala
     val notWithTrumpet: String = "Cannot be used with Painted Trumpet"
   ```

- [ ] **Step 5: Register it**

In `NewFoundations.scala`, add `PaintedTrumpetCard` to the `powers.search` import, in alphabetical order.

In `ActionModifiers.scala`, add `PaintedTrumpet` to the search import, in alphabetical order. Then add after `Vector(WineOfWelcome.forCatalog(catalog)) ++`:

```scala
      Vector(PaintedTrumpet.forCatalog(catalog)) ++
```

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.*"`
Expected: PASS.

Then run: `./sbtw test`
Expected: PASS, with 25 tests more than the baseline.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/PaintedTrumpet.scala src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/search/PaintedTrumpetSuite.scala
git commit -m "feat(powers): Painted Trumpet draws the top card of each discard pile"
```

---

### Task 6: The Search line names the new draws

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (`search`, near line 187)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogSearchDrawSuite.scala`

**Interfaces:**
- Consumes: `Draw.position` (Task 2), plus `Mushrooms.id`, `WineOfWelcome.id` and `PaintedTrumpet.id` (Tasks 4 and 5).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/application/gamelog/GameLogSearchDrawSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GameCommand, StartPayload}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.search.{Mushrooms, PaintedTrumpet,
  WineOfWelcome}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{Answers, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** The Search line for the draws catalog batch 6 adds: from the bottom of
  * a pile, and from each discard pile (slice 3 plan-time ruling 1). */
class GameLogSearchDrawSuite extends munit.FunSuite:
  /** p1's depth-1 lines after a Search at `table` that picks `modifiers`,
    * then `source`, every other decision answered by default. */
  private def searchLines(name: String, table: Table,
      modifiers: Vector[PowerId], source: SearchSource)(
      using munit.Location): Vector[String] =
    val (service, act) = atTable(name, table.banditsRefilled)
    val pile: Answers = {
      case park if park.decisionId == SearchProcedure.sourceDecisionId =>
        ChooseOneAnswer(SearchSource.ref(source))
    }
    act.withAnswers(pile.orElse(ModifierPlay.answers(
      PowerWindow.SearchModifierSelection, modifiers)))
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(p1)))
    texts(format(Script(name, service, p1), Some(p1)).filter(_.depth == 1))

  test("Wine of Welcome: drew from the bottom of the World Deck"):
    val lines = searchLines("wine-of-welcome", Table.start
      .relic(p1, "Wine of Welcome")
      .worldDeckBottom("Threatening Roar", "Fae Merchant", "Second Chance"),
      Vector(WineOfWelcome.id), SearchSource.WorldDeck)
    val drew = lineStarting(lines, "Drew ")
    assert(drew.contains(" from the bottom of the World Deck and kept "),
      drew)

  test("Mushrooms: drew one card from the bottom of the Cradle discard"):
    val lines = searchLines("mushrooms", Table.start
      .denizen("Mushrooms", "Ancient City")
      .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant"),
      Vector(Mushrooms.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.startsWith(
      "Drew Threatening Roar from the bottom of the Cradle discard and " +
        "kept "), drew)

  test("Painted Trumpet: drew from each discard pile"):
    val lines = searchLines("painted-trumpet", Table.start
      .relic(p1, "Painted Trumpet")
      .discarded(Region.Cradle, "Threatening Roar")
      .discarded(Region.Provinces, "Fae Merchant"),
      Vector(PaintedTrumpet.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.contains(" from each discard pile and kept "), drew)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogSearchDrawSuite"`
Expected: FAIL. The lines read " from the World Deck …" and " from the Cradle discard …".

- [ ] **Step 3: Read every draw of the run**

In `ActionLines.scala`, replace the `search` method and its doc with:

```scala
  /** "Drew {cards} from the {source} and kept {cards}", at the keep/discard
    * answer, or at completion when only one card was drawn. Each card is
    * judged where its draw took it from and put it. Painted Trumpet draws
    * with one `Draw` per pile, read together ([[drawnFrom]]). */
  private def search(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val draws = journal.runOps(run, at).collect {
      case (_, OpStep(draw: Draw, before, after)) => draw -> draw.cards.map(
        card => card -> words.card(card, before, after, viewer))
    }
    Option.when(draws.nonEmpty)(
      (draws.flatMap(_._2), drawnFrom(draws.map(_._1))))
      .toVector.flatMap { case (drawn, from) =>
        val kept: Option[Vector[CardId]] = journal.event(at) match
          case WalkerStepRecorded(_, ChoicePayload(
              SearchProcedure.cardDecisionId, PartitionAnswer(placements), _),
              _, _) =>
            Some(placements.collect {
              case DecisionPlacement(ref, SearchProcedure.keepKey) => ref
            }.flatMap(worldCard))
          // One card drawn: its placement answer completes the line, so the
          // line precedes the placement line it leads to.
          case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _)
              if drawn.size == 1 && id.startsWith(PlacePrefix) =>
            Some(drawn.map(_._1))
          case _: WalkerCompleted if drawn.size == 1 && !journal.answers(run,
              at).exists(_.decisionId.startsWith(PlacePrefix)) =>
            Some(drawn.map(_._1))
          case _ => None
        kept.toVector.map(ids => action(Vector(Text("Drew ")) ++
          words.cards(drawn.map(_._2)) ++
          Vector(Text(s" from $from and kept ")) ++
          words.cards(ids.flatMap(id => drawn.find(_._1 == id).map(_._2)))))
      }

  /** Where a Search drew from: "the World Deck", "the bottom of the Cradle
    * discard", or "each discard pile" for Painted Trumpet's draw of every
    * pile's top card. */
  private def drawnFrom(draws: Vector[Draw]): String = draws match
    case Vector(draw) if draw.position == StackPosition.Bottom =>
      s"the bottom of ${sourceName(draw.source)}"
    case Vector(draw) => sourceName(draw.source)
    case _ => "each discard pile"
```

- [ ] **Step 4: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. The golden logs are unchanged, since a single top draw still reads "from the World Deck".

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/GameLogSearchDrawSuite.scala
git commit -m "feat(gamelog): name a Search drawn from the bottom or from each discard pile"
```

---

### Task 7: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 6)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 28, and the frontend count is unchanged.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the slice 3 bullet under "### Phase - Catalog batch 6". It starts `- [ ] Slice 3, Search sources:` and spans two lines.
- In "## Now", change the batch 6 sentence so it names slices 1, 2 and 3 as done, for example `**Phase - Catalog batch 6** is in progress: slices 1 to 3 are done.` Re-wrap it at the paragraph's width. If a later slice has been recorded meanwhile, keep its wording and add slice 3.
- In the slice 4 bullet, replace `Waits for the modifier walker phase.` with `Its modifiers are on the walker since modifier walker slice 1.`

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 6 slice 3"
```
