# Search Source in the Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Search starts with no selection and parks on a board decision over the world deck and the actor's region's discard pile, each badged with its cost under the selected modifiers or shown unavailable with why. Clay Rattle's piles move onto the board with it.

**Architecture:**

- **Pile refs.** `SearchSource` gains `ref`, `fromRef` and `option`, naming a pile as `DecisionOptionRef.Deck(CardDeck.World)` or `DecisionOptionRef.DiscardPile(region)`. The projector labels them "World deck" and "Cradle discard pile". Clay Rattle offers its piles through them.
- **Shared pricing.** The generic part of `TravelOptions` moves to `walker.PricedOptions`, which takes a fallback reason and an optional `explain` that names a refusal before the shortfall does.
- **Tree.** Search's tree is `Sequence(Decide(search.source), Branch(Sequence(cost, draw, query, placement) at SearchActionEligibility))`. The entry opts in to `requiresPlayableOption` and prices its options through `SearchOptions`, which explains an empty pile.
- **Service.** The draw-port check moves from the Search start to the `search.source` answer.
- **Projection.** A `beginSearch` control replaces `legalSearchSources`, and the per-source buttons retire.
- **Frontend.** One "Search" button starts the walker through the modifier flow. The world deck pile becomes a board option, and both piles draw a badge.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-07-search-source-walker-design.md`. Read the whole spec before starting. The Travel destination plan (`docs/superpowers/plans/2026-10-07-travel-destination-walker.md`) moved Travel's destination into the walker the same way. Its Tasks 2, 3 and 6 are the closest template for Tasks 3, 4 and 5 here.

## Global Constraints

- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on every project. An unused import, private member or local fails the build: delete whatever the compiler names.
- **File size.** Every Scala file stays at or under 800 lines (`scripts/check-architecture.py`).
- **Ids and wording, exactly:**
  - Source decision id `search.source`, spelled once as `SearchProcedure.sourceDecisionId`. Heading `Choose a Search source`.
  - Options: `DecisionOptionRef.Deck(CardDeck.World)` (wire `deck` / `world`) and `DecisionOptionRef.DiscardPile(region)` (wire `discard-pile` / region key). Labels `World deck` and `{Region} discard pile`, e.g. `Cradle discard pile`.
  - Badges: `2 Supply`, `0 Supply`, `2 Supply · 1 favor`, `1 favor`.
  - Reasons:
    - `Needs 3 Supply; you have 2`
    - `Needs 1 favor; you have 0`
    - `The world deck is empty`
    - `The Cradle discard pile is empty`
    - fallback `Cannot search there now`
  - Start refusal: `search takes no start selection`. A source answer naming anything but a pile: `a Search source is the world deck or a discard pile`.
  - Legal control `beginSearch`. Button label `Search`, class `act-action search-action`, section kind `search`.
  - Board keys: `deck:world` and `discard-pile:{region key}`.
- **Rulings** (spec, "Rulings"):
  - Modifiers stay before the start.
  - A selected modifier's card payment is not in the badge.
  - Other regions' discards are not offered.
  - An empty pile is shown unavailable, not hidden.
  - Undo takes back the start, and the source answer draws, so it is a barrier.
  - The source pick posts no "Chose" line.
  - Clay Rattle's piles carry no badge, and its empty piles stay pickable.
- **No CSS change.** A pile's badge reuses `.target-detail-badge`, which is an inline-block pill outside a `.site` box. Impeccable is not needed.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.gameplay.SearchProcedureSuite"`.
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.SearchLaunchSuite"`.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 6), in order:
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    4. `git diff --check`

---

### Task 1: Pile refs, their labels, and Clay Rattle on the board

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionValues.scala` (`object SearchSource`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:398-417` (the `Deck` and `DiscardPile` rows)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/ClayRattle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Convoys.scala:19-22` (doc only)
- Create: `src/test/scala/oathdigital/model/SearchSourceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala:76-79`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/ClayRattleSuite.scala:53-60`

**Interfaces:**
- Produces:
  - `SearchSource.ref(source: SearchSource): DecisionOptionRef`
  - `SearchSource.fromRef(ref: DecisionOptionRef): Option[SearchSource]`
  - `SearchSource.option(source: SearchSource): DecisionOption`
  - `ClayRattle.ref(pile: SearchSource): DecisionOptionRef` (was `DecisionOptionRef.Button`)

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/model/SearchSourceSuite.scala`:

```scala
package oathdigital.model

/** A pile a Search draws from, as the board option that names it (search
  * source design, ruling 2). */
class SearchSourceSuite extends munit.FunSuite:
  private val piles: Vector[SearchSource] =
    SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_))

  test("each pile is a board option that names it back"):
    piles.foreach(pile =>
      assertEquals(SearchSource.fromRef(SearchSource.ref(pile)), Some(pile)))
    assertEquals(piles.map(SearchSource.option(_).ref),
      piles.map(SearchSource.ref))
    val world = SearchSource.ref(SearchSource.WorldDeck)
    assertEquals(world.kind -> world.wireId, "deck" -> "world")
    val cradle = SearchSource.ref(SearchSource.RegionalDiscard(Region.Cradle))
    assertEquals(cradle.kind -> cradle.wireId, "discard-pile" -> "cradle")

  test("another deck or any other ref names no pile"):
    assertEquals(SearchSource.fromRef(DecisionOptionRef.Deck(CardDeck.Relic)),
      None)
    assertEquals(SearchSource.fromRef(DecisionOptionRef.Button("world-deck")),
      None)
```

In `SlotOptionRowsSuite`, replace the test "the discard pile names its region" with:

```scala
  test("the discard pile and the world deck name themselves, for a card " +
      "play and a Search alike"):
    assertEquals(rowOf(DecisionOption.DiscardPile(
      DecisionOptionRef.DiscardPile(Region.Provinces))).label,
      "Provinces discard pile")
    assertEquals(rowOf(DecisionOption.Deck(
      DecisionOptionRef.Deck(CardDeck.World))).label, "World deck")
```

In `ClayRattleSuite`, extend "it places two secrets and asks which pile to shuffle" by adding after `assertEquals(ClayRattle.piles.size, 4)`:

```scala
    // The piles are the board's own options, not buttons.
    assertEquals(ClayRattle.piles.map(ClayRattle.ref),
      DecisionOptionRef.Deck(CardDeck.World) +:
        Region.all.map(DecisionOptionRef.DiscardPile(_)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.model.SearchSourceSuite oathdigital.application.SlotOptionRowsSuite oathdigital.gameplay.powers.action.ClayRattleSuite"`
Expected: compile failure, because `ref`, `fromRef` and `option` are not members of `SearchSource`.

- [ ] **Step 3: Add the pile refs**

In `ActionValues.scala`, add to `object SearchSource`, after `name`:

```scala
  /** The board option that names `source`: the world deck, or a region's
    * discard pile. A Search's source decision and Clay Rattle offer these. */
  def ref(source: SearchSource): DecisionOptionRef = source match
    case WorldDeck => DecisionOptionRef.Deck(CardDeck.World)
    case RegionalDiscard(region) => DecisionOptionRef.DiscardPile(region)

  /** The pile `ref` names, when it names a pile a Search could draw from. */
  def fromRef(ref: DecisionOptionRef): Option[SearchSource] = ref match
    case DecisionOptionRef.Deck(CardDeck.World) => Some(WorldDeck)
    case DecisionOptionRef.DiscardPile(region) => Some(RegionalDiscard(region))
    case _ => None

  /** `source` as a decision option. */
  def option(source: SearchSource): DecisionOption = source match
    case WorldDeck =>
      DecisionOption.Deck(DecisionOptionRef.Deck(CardDeck.World))
    case RegionalDiscard(region) =>
      DecisionOption.DiscardPile(DecisionOptionRef.DiscardPile(region))
```

- [ ] **Step 4: Label the piles**

In `WalkerDecisionProjector.optionProjection`, replace the `Deck` and `DiscardPile` cases:

```scala
      case DecisionOption.Deck(deck) =>
        row(s"${presentation.safeLabel(deck.id.key)} deck")
```

```scala
      // A pile names itself: a card play discards to it and a Search draws
      // from it, so the label says neither.
      case DecisionOption.DiscardPile(pile) =>
        row(SearchSource.name(SearchSource.RegionalDiscard(pile.region)))
```

`SearchSource.name` gives "Cradle discard pile". The file already imports `oathdigital.model._`.

In `Convoys.scala`'s doc, replace "the `DiscardPile` option reads "Discard to the X discard", which asks something else" with "the `DiscardPile` option is the pile a card is discarded to or a Search draws from, which is not this card's question".

- [ ] **Step 5: Put Clay Rattle's piles on the board**

In `ClayRattle.scala`, replace the doc's first paragraph after the card text with:

```scala
  * The player chooses the pile on the board, among the world deck and the
  * three discard piles, empty piles included: the card lets any pile be
  * chosen. ...
```

Keep the doc's remaining sentences ("The shuffle is declared with no order…" onward).

Replace `ref`, the `Decide` in `build`, and `chosen`:

```scala
  def ref(pile: SearchSource): DecisionOptionRef = SearchSource.ref(pile)
```

```scala
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      piles.map(SearchSource.option),
      heading = Some("Clay Rattle: shuffle a pile"))),
```

```scala
  private def chosen(answered: Vector[Answered]): Option[SearchSource] =
    answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(picked), _) =>
        picked
    }.flatMap(SearchSource.fromRef)
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.model.SearchSourceSuite oathdigital.application.SlotOptionRowsSuite oathdigital.gameplay.powers.action.ClayRattleSuite oathdigital.gameplay.powers.action.RelicThiefSuite"`
Expected: PASS.

Then run `./sbtw test`. Expected: PASS.
- If a suite asserts the old label "Discard to the … discard" on a projected row, change its expectation to "{Region} discard pile".
- Leave the label strings in `shared/…/ProjectionProtocolSuite` and `frontend/…/DiscardPileSuite` alone: they are fixture data, not projector output.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/ActionValues.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/gameplay/powers/action/ClayRattle.scala \
  src/main/scala/oathdigital/gameplay/powers/action/Convoys.scala \
  src/test/scala/oathdigital/model/SearchSourceSuite.scala \
  src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ClayRattleSuite.scala
git commit -m "feat(search): name piles as board options; Clay Rattle offers them"
```

Stage any other test file Step 6 changed, by explicit path.

---

### Task 2: Share Travel's option pricing

A refactor: Travel's behaviour does not change, and its suites prove it.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelOptions.scala` (rewrite)

**Interfaces:**
- Produces:
  - `PricedOptions.priced(state: ReadyGame, tree: Operation, pending: PendingTree, powers: WalkerPowers, decide: Decide, fallback: String, explain: DecisionOptionRef => Option[String] = _ => None): Vector[DecisionOption]`
  - `PricedOptions.options(decide: Decide): Vector[DecisionOption]`
  - `PricedOptions.badge(outcome: WalkerSimulation.PreviewOutcome, actor: PlayerId): String`
  - `TravelOptions.priced` keeps its signature (the registry's `OptionPricing`).

- [ ] **Step 1: Run Travel's pricing suites green first**

Run: `./sbtw "testOnly oathdigital.application.TravelDestinationRowsSuite oathdigital.gameplay.TravelProcedureSuite"`
Expected: PASS. This is the baseline the refactor must keep.

- [ ] **Step 2: Create `PricedOptions`**

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome,
  PreviewedOption}
import oathdigital.model._

/** A parked choose-one as its player is shown it, for a procedure whose
  * options each cost something different (travel destination design and
  * search source design, rulings 3 and 4): each option the preview accepts
  * badged with what answering it costs, each it refuses unavailable with why.
  *
  * The walk's own decision has already lost every option its look-ahead
  * found no way to finish, so the declared decision is read again for the
  * full list. An option the look-ahead hid with a restriction's reason keeps
  * that reason, which wins over every other. Any other refused option is
  * explained by the procedure's own `explain`, then by what it needs, then by
  * the procedure's `fallback`.
  */
object PricedOptions:
  /** Enough favor for any toll an action can owe, to learn what a refused
    * option needs. */
  private val AmpleFavor = 10

  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, fallback: String,
      explain: DecisionOptionRef => Option[String] = _ => None)
      : Vector[DecisionOption] =
    val actor = decide.owner
    val walked = options(decide)
    val declared = ProcedureWalker.parkedDecide(state, tree, pending, powers,
      probing = false).map(options).getOrElse(walked)
    val previewed = previews(state, tree, pending, powers, followUp = true)
    def refused(option: DecisionOption) = DecisionOption.Unavailable(option,
      OptionReason.Written(explain(option.ref).getOrElse(shortfall(state,
        tree, pending, powers, actor, option.ref, fallback))))
    declared.map(option =>
      walked.find(_.ref == option.ref) match
        case Some(kept) if !kept.available => kept
        case Some(kept) => previewed.get(kept.ref) match
          case Some(Right(outcome)) if answerable(outcome) =>
            DecisionOption.Badged(kept, badge(outcome, actor))
          case _ => refused(kept)
        case None => refused(option))

  /** A choose-one's options as the decision lists them; none for any other
    * query. */
  def options(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case DecisionQuery.ChooseOne(offered, _) => offered
      case _ => Vector.empty

  /** Whether the walk can go on from `outcome`: it finished, its further
    * question could not be previewed, or some answer to it is accepted. */
  private def answerable(outcome: PreviewOutcome): Boolean =
    outcome.complete || outcome.next.isEmpty ||
      outcome.next.exists(_.outcome.isRight)

  /** "2 Supply", "2 Supply · 1 favor", "1 favor", or each answer of a
    * further question joined with " or ": "2 Supply or 1 favor". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    val answers = outcome.next.collect {
      case PreviewedOption(_, Right(following)) =>
        outcome.operations ++ following.operations
    }
    if answers.isEmpty then words(outcome.operations, actor)
    else answers.map(words(_, actor)).distinct.mkString(" or ")

  private def words(operations: Vector[CoreOperation], actor: PlayerId)
      : String =
    val (supply, favor) = spent(operations, actor)
    Vector(Option.when(supply > 0 || favor == 0)(s"$supply Supply"),
      Option.when(favor > 0)(s"$favor favor")).flatten.mkString(" · ")

  /** The Supply and favor an answer takes from `actor`. Favor counts when it
    * leaves their play area for anywhere but a card: a selected modifier's
    * payment onto its card is the same for every option. A `Give` and a
    * `PayCost` are walked as their moves, so the moves are read. */
  private def spent(operations: Vector[CoreOperation], actor: PlayerId)
      : (Int, Int) =
    val supply = operations.collect {
      case SpendSupply(player, amount, _) if player == actor => amount }.sum
    val favor = operations.collect {
      case Move(Piece.Favor(amount),
          PositionedLocation(Location.PlayArea(player), _),
          PositionedLocation(to, _), _)
          if player == actor && !to.isInstanceOf[Location.OnCard] => amount
    }.sum
    (supply, favor)

  /** Why `ref` cannot be answered. The pipeline's refusal carries no amount,
    * so the amount is read from the same preview with the shortfall lifted:
    * Supply at the track's maximum, then favor as well. */
  private def shortfall(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, actor: PlayerId,
      ref: DecisionOptionRef, fallback: String): String =
    val board = state.game.current.players.find(_.player == actor).map(_.board)
    def lifted(favor: Boolean): Option[PreviewOutcome] =
      previews(lift(state, actor, favor), tree, pending, powers,
        followUp = false).get(ref).flatMap(_.toOption)
    (board, lifted(favor = false).orElse(lifted(favor = true))) match
      case (Some(board), Some(outcome)) =>
        val (supply, favor) = spent(outcome.operations, actor)
        if supply > board.supply.supply then
          s"Needs $supply Supply; you have ${board.supply.supply}"
        else if favor > board.favor then
          s"Needs $favor favor; you have ${board.favor}"
        else fallback
      case _ => fallback

  private def lift(state: ReadyGame, actor: PlayerId, favor: Boolean)
      : ReadyGame = state.updateCurrent(current => current.copy(
    players = current.players.map(player =>
      if player.player != actor then player
      else player.copy(board = player.board.copy(
        supply = SupplyTrack(SupplyTrack.Maximum),
        favor = if favor then player.board.favor + AmpleFavor
          else player.board.favor)))))

  private def previews(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, followUp: Boolean)
      : Map[DecisionOptionRef, Either[OathViolation, PreviewOutcome]] =
    WalkerSimulation.previewParked(state, tree, pending, powers, followUp)
      .fold(_ => Map.empty, _.map(previewed =>
        previewed.option.ref -> previewed.outcome).toMap)
```

- [ ] **Step 3: Rewrite `TravelOptions` onto it**

Replace the whole file with:

```scala
package oathdigital.gameplay.actions.travel

import oathdigital.gameplay.walker.{PricedOptions, WalkerPowers}
import oathdigital.model._

/** The destination decision as its player is shown it (travel destination
  * design, rulings 3 and 4), priced by [[PricedOptions]]. Every other
  * decision of a Travel is shown as declared. */
object TravelOptions:
  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    if decide.decisionId != TravelProcedure.destinationDecisionId then
      PricedOptions.options(decide)
    else PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = "Cannot travel there now")
```

- [ ] **Step 4: Run Travel's suites to verify nothing changed**

Run: `./sbtw "testOnly oathdigital.application.TravelDestinationRowsSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.gameplay.powers.travel.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala \
  src/main/scala/oathdigital/gameplay/actions/travel/TravelOptions.scala
git commit -m "refactor(walker): share Travel's option pricing as PricedOptions"
```

---

### Task 3: Search parks on its source

The engine change breaks every test that starts a Search with a source, so this task updates all of them and ends with the whole server suite green. Two interim states are expected inside the branch:
- The frontend still sends the old start until Task 5.
- Until Task 4 prices the sources, the projector shows them through the playable-option filter.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala` (rewrite)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala:86-91` (doc) and `:166-171` (entry)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:464-481`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala:59-63`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:190`
- Create: `src/test/scala/oathdigital/gameplay/SearchPlay.scala`
- Create: `src/test/scala/oathdigital/application/SearchSourceServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ParkedServiceFixture.scala` (add `startSearch`, `pickSearchSource`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala:60-63`
- Modify: `src/test/scala/oathdigital/gameplay/SearchProcedureSuite.scala`
- Modify: every other test that starts a Search with a source selection (Step 7 lists them)
- Modify: `src/test/resources/gamelog/{search,augury}.{actor,other}.log` (regenerated)

**Interfaces:**
- Consumes (Task 1): `SearchSource.ref`, `SearchSource.fromRef`, `SearchSource.option`.
- Produces:
  - `SearchProcedure.sourceDecisionId: String = "search.source"`.
  - `SearchProcedure.build` and `SearchProcedure.rebuild`: `(ExecutableCatalog, ReadyGame, PlayerId, Vector[DecisionOptionRef]) => Either[OathViolation, Operation]`.
  - `SearchProcedure.sourceOf(answer: DecisionAnswer): Either[OathViolation, SearchSource]`.
  - `SearchProcedure.actorRegion(ready: ReadyGame, actor: PlayerId): Either[OathViolation, Region]` (now public).
  - `SearchProcedure.legalSources` keeps its signature for now (Task 4 deletes it), reading the preview.
  - Test helpers:
    - `oathdigital.gameplay.SearchPlay.searched(rules: OathRules, state: OathState, actor: PlayerId, pile: SearchSource = SearchSource.WorldDeck, modifiers: Vector[PowerId] = Vector.empty): Either[OathViolation, OathTransition]`
    - `SearchPlay.source(pile: SearchSource): DecisionAnswer`
    - `ParkedServiceFixture.startSearch(actor: PlayerId): GameCommand`
    - `ParkedServiceFixture.pickSearchSource(actor: PlayerId, pile: SearchSource): GameCommand`

- [ ] **Step 1: Write the test helpers**

Create `src/test/scala/oathdigital/gameplay/SearchPlay.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.model._

/** A Search driven past its source pick, for the suites that test what a
  * Search does rather than the pick. */
object SearchPlay:
  /** The answer that picks `pile` as the source. */
  def source(pile: SearchSource): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(SearchSource.ref(pile))

  /** `actor` starts a Search with `modifiers` and picks `pile`. The events
    * are both commands'. */
  def searched(rules: OathRules, state: OathState, actor: PlayerId,
      pile: SearchSource = SearchSource.WorldDeck,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- rules.startWalker(state, ActionRef.Search, actor, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      SearchProcedure.sourceDecisionId, source(pile))
  yield picked.copy(events = started.events ++ picked.events)
```

In `ParkedServiceFixture`, import `oathdigital.gameplay.SearchPlay` and `oathdigital.gameplay.actions.search.SearchProcedure`, then add after `pickTravelDestination`:

```scala
  /** `actor` starts a Search; the walk parks on the source. */
  def startSearch(actor: PlayerId): GameCommand =
    GameCommand.StartWalker(ActionRef.Search,
      StartPayload(actor, Vector.empty, Vector.empty))

  /** `actor` answers the source decision with `pile`. */
  def pickSearchSource(actor: PlayerId, pile: SearchSource): GameCommand =
    GameCommand.ResolveWalker(actor, TreeDecision(
      SearchProcedure.sourceDecisionId, SearchPlay.source(pile)))
```

In `SearchFixture`, import `oathdigital.gameplay.SearchPlay` and replace `start`:

```scala
  /** Starts a Search with `modifiers` and picks the world deck. */
  def start(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    SearchPlay.searched(rules, Ready(ready), actor, SearchSource.WorldDeck,
      modifiers)
```

- [ ] **Step 2: Write the failing engine and service tests**

In `SearchProcedureSuite`, import `oathdigital.testkit.Unwrap.rejected` beside `accepted`, and add:

```scala
  private def searched(under: OathRules, ready: ReadyGame,
      pile: SearchSource = SearchSource.WorldDeck)
      : Either[OathViolation, OathTransition] =
    SearchPlay.searched(under, OathState.Ready(ready), actor, pile)
```

Replace the first three tests ("world Search starts from one generic source argument…", "Search rejects a source in another region", "Search tree construction defers affordability…") with:

```scala
  test("a Search parks on its source and draws on the answer"):
    val initial = atAncientCity.worldDeckTop(topCards*).ready
    val started = accepted(rules.startWalker(OathState.Ready(initial),
      ActionRef.Search, actor))
    parked.assertParked(started.state, ActionRef.Search,
      SearchProcedure.sourceDecisionId, actor)
    assertEquals(Outcomes.ready(started.state).game.current.temporaryHands
      .getOrElse(actor, Vector.empty), Vector.empty)
    val picked = accepted(rules.resolveWalker(started.state, actor,
      SearchProcedure.sourceDecisionId, SearchPlay.source(SearchSource.WorldDeck)))
    parked.assertParked(picked.state, ActionRef.Search,
      SearchProcedure.cardDecisionId, actor)

  test("the source offers the world deck and the actor's region's discard"):
    val ready = atAncientCity.ready
    val tree = accepted(SearchProcedure.build(catalog, ready, actor,
      Vector.empty))
    val offered = tree.children.head match
      case Decide(SearchProcedure.sourceDecisionId, `actor`,
          DecisionQuery.ChooseOne(options, Some("Choose a Search source")),
          _, _, _, _) => options.map(_.ref)
      case other => fail(s"not the source decision: $other")
    assertEquals(offered, Vector[DecisionOptionRef](
      DecisionOptionRef.Deck(CardDeck.World),
      DecisionOptionRef.DiscardPile(region)))

  test("a start that names a selection is refused"):
    assertEquals(rejected(SearchProcedure.build(catalog, atAncientCity.ready,
      actor, Vector(DecisionOptionRef.Button("search:world")))),
      OathViolation.InvalidEventOrder("search takes no start selection"))

  test("a start with no playable source is refused"):
    // No Supply, and the Cradle discard is empty on the quiet table.
    val broke = atAncientCity.supply(actor, 0).ready
    assertEquals(rejected(rules.startWalker(OathState.Ready(broke),
      ActionRef.Search, actor)), OathViolation.NoPlayableOption(
      ActionRef.Search.key))

  test("another region's discard is not offered, and naming it is refused"):
    val initial = atAncientCity.discarded(Region.Provinces, topCards*).ready
    assert(searched(rules, initial,
      SearchSource.RegionalDiscard(Region.Provinces)).isLeft)
```

The `Decide` pattern lists seven fields (`decisionId, owner, query, window, coOwners, passWhenEmpty, presentation`); match the case class as it is. If `rules.startWalker` takes the modifiers as a required argument, pass `Vector.empty`.

In the remaining tests, apply these rewrites:

| Today | Becomes |
|---|---|
| `X.startWalker(OathState.Ready(initial), ActionRef.Search, actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))` | `searched(X, initial)` |
| The regional test's start | `searched(rules, initial, SearchSource.RegionalDiscard(region))` |

Here `X` is whichever rules the test uses: `rules`, `withPowers` or `withPower`.

Create `src/test/scala/oathdigital/application/SearchSourceServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** The source decision through the service: Undo takes back the start, and
  * the answer draws, so nothing takes it back (search source design,
  * ruling 6). */
class SearchSourceServiceSuite extends munit.FunSuite:
  private val table = Table.start
    .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
    .banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val undone = accept(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("the source answer draws, so it cannot be taken back"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val picked = accept(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickSearchSource(p1, SearchSource.WorldDeck)))
    assertEquals(service.handle("g", picked.nextSequence, GameCommand.Undo(p1)),
      Left(GameApplicationError.CommandRejected(OathViolation.NothingToUndo)))
```

In `UndoBarrierPlaySuite`, delete the test "a Search's start draws, so it cannot be taken back". `SearchSourceServiceSuite` now states both halves.

In `GameApplicationServiceActionsSuite`, rewrite "Search draw port cannot inject card identities inconsistent with state" from `val active = p1` on:

```scala
    val active = p1
    val started = accepted(service.handle("game-search-tamper", 0L,
      ParkedServiceFixture.startSearch(active)))
    assertEquals(service.handle("game-search-tamper", started.nextSequence,
      ParkedServiceFixture.pickSearchSource(active, SearchSource.WorldDeck))
      .left.toOption, Some(GameApplicationError.CommandRejected(
        OathViolation.SearchDrawMismatch(
          "prepared draw does not match authoritative source order"))))
    assertEquals(loaded(service.load("game-search-tamper")).nextSequence,
      started.nextSequence)
```

- [ ] **Step 3: Run the new tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchProcedureSuite oathdigital.application.SearchSourceServiceSuite"`
Expected: compile failure, because `sourceDecisionId` is not a member of `SearchProcedure`.

- [ ] **Step 4: Rewrite `SearchProcedure`**

Replace everything from the imports down to `rebuild` (inclusive), and the private `tree` and `actorRegion`, so the object reads as follows. Keep `option`, `card`, `answered`, `selectedCards`, `selected` and `fail` as they are.

```scala
package oathdigital.gameplay.actions.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** Search on the walker (search source design).
  *
  * {{{
  * Sequence(
  *   Decide(search.source),             // a board choose-one: the world deck
  *                                      // and the actor's region's discard
  *   Branch { after the answer:
  *     Sequence(cost, draw, query, placement) })  // window = SearchActionEligibility
  * }}}
  *
  * **The source is a decision.** It is picked after the modifiers, so the
  * parked decision shows what each source costs under them. The start
  * carries no selection.
  *
  * **The windows sit inside the `Branch`.** A selected modifier's payment,
  * prepended at the eligibility window, is paid after the pick, and nothing
  * runs before the first decision.
  *
  * **The region is read from the pawn.** The pawn does not move during a
  * Search, so a resume reads the same region and needs no start fact.
  *
  * **What does not gate a start.** Supply, tolls and empty piles are not
  * build gates: the registry's playable-option gate previews both sources
  * and refuses a start neither can complete. Placement is the shared subtree.
  */
object SearchProcedure:
  val sourceDecisionId: String = "search.source"
  val cardDecisionId: String = "search.cards"
  val keepKey: String = "keep"
  val discardKey: String = "discard"
  private val heading = "Choose a Search source"

  /** Every source the preview accepts, with the Supply it spends. Retires
    * with the Search control (Task 4). */
  def legalSources(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, powers: WalkerPowers): Vector[(SearchSource, Int)] =
    build(catalog, ready, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, ready, powers))
      .getOrElse(Vector.empty).flatMap {
        case PreviewedOption(option, Right(outcome)) =>
          SearchSource.fromRef(option.ref).map(_ -> outcome.operations.collect {
            case SpendSupply(player, amount, _) if player == actor => amount
          }.sum)
        case _ => None
      }

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    _ <- noSelection(args)
    _ <- Either.cond(ready.game.current.temporaryHands.valuesIterator
      .forall(_.isEmpty), (), OathViolation.SearchDrawMismatch(
      "a temporary card hand already exists"))
    origin <- actorRegion(ready, actor)
  yield tree(catalog, actor, origin)

  /** Resume never re-runs the start-only gates. */
  def rebuild(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noSelection(args)
    origin <- actorRegion(ready, actor)
  yield tree(catalog, actor, origin)

  /** The pile a source answer names. */
  def sourceOf(answer: DecisionAnswer): Either[OathViolation, SearchSource] =
    (answer match
      case DecisionAnswer.ChooseOneAnswer(ref) => SearchSource.fromRef(ref)
      case _ => None).toRight(OathViolation.InvalidEventOrder(
      "a Search source is the world deck or a discard pile"))

  def actorRegion(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Region] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.regionOf)
      .toRight(OathViolation.PawnSiteMissing(actor))

  private def noSelection(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder("search takes no start selection"))

  private def tree(catalog: ExecutableCatalog, actor: PlayerId,
      origin: Region): Operation =
    val pick = Decide(sourceDecisionId, actor, DecisionQuery.ChooseOne(
      Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(origin))
        .map(SearchSource.option), heading = Some(heading)))
    val search = Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`sourceDecisionId`, answer, _) => answer
    }.toVector.map(answer =>
      sourceOf(answer).fold(fail, body(catalog, actor, _))))
    Sequence(Vector(pick, search))

  /** The Search from `source`, once it is picked. */
  private def body(catalog: ExecutableCatalog, actor: PlayerId,
      source: SearchSource): Operation =
```

The body of `body` is the old `tree`'s body unchanged: `val cost`, `val draw`, `val query`, `val placement`, and `Sequence(Vector(cost, draw, query, placement), Some(PowerWindow.SearchActionEligibility))`. Delete the old `sourceOf(args)`, the old `tree` and the old private `actorRegion`. If the compiler reports an unused import, remove it.

- [ ] **Step 5: Register the entry, move the draw-port check, and narrate the pick**

1. **Registry entry.** In `WalkerProcedureRegistry.entries`, replace the Search entry with:

```scala
    /** Search parks on its source first (search source design). Nothing
      * runs before that decision, so the start is previewed through the
      * playable-option gate. */
    ActionRef.Search -> Entry(
      fallbackKind = Some(ActionKind.Search),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.SearchModifierSelection),
      build = SearchProcedure.build,
      rebuild = SearchProcedure.rebuild,
      requiresPlayableOption = true),
```

2. **Registry doc.** In the `Entry` doc, replace "so they take an empty vector and reject anything else; Search cannot name its source without one." with "so they take an empty vector and reject anything else. No registered procedure takes a start selection now: Search and Travel ask theirs inside the walk."

3. **Draw-port check.** In `GameApplicationService.applyUnblockedCommand`, delete the `case GameCommand.StartWalker(ActionRef.Search, start) =>` case, so a Search starts through the generic `StartWalker` case. Insert this case before the generic `case GameCommand.ResolveWalker(actor, treeDecision) =>`:

```scala
      // The source answer is where a Search draws, so the prepared draw is
      // checked there, against the pile the answer names.
      case GameCommand.ResolveWalker(actor,
          TreeDecision(SearchProcedure.sourceDecisionId, answer)) =>
        state match
          case OathState.Ready(ready)
              if ready.game.current.walkerProcedure.contains(ActionRef.Search) =>
            for
              source <- SearchProcedure.sourceOf(answer)
              region <- SearchProcedure.actorRegion(ready, actor)
              prepared <- searchDrawPort.prepare(ready, source, region)
              authoritative <- oathdigital.gameplay.actions.SearchRules.draw(
                ready, source, region)
              _ <- Either.cond(prepared == authoritative, (),
                OathViolation.SearchDrawMismatch(
                  "prepared draw does not match authoritative source order"))
              result <- rules.resolveWalker(state, actor,
                SearchProcedure.sourceDecisionId, answer)
            yield result
          case _ => rules.resolveWalker(state, actor,
            SearchProcedure.sourceDecisionId, answer)
```

4. **Start line.** In `StartLines`, widen the Travel case to Search, and reword its comment:

```scala
      // The first step that does anything after the source or destination
      // is picked: a toll, a modifier's payment, the Supply, the draw or the
      // move.
      case ActionRef.Travel | ActionRef.Search =>
```

5. **No "Chose" line.** In `DetailLines.NarratedIds`, add `SearchProcedure.sourceDecisionId` beside `SearchProcedure.cardDecisionId`.

- [ ] **Step 6: Run the new tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.SearchProcedureSuite oathdigital.application.SearchSourceServiceSuite"`
Expected: PASS. `GameApplicationServiceActionsSuite` passes once Step 7 moves its other Search test.

- [ ] **Step 7: Run the server suite and move the other Search starts**

Run: `./sbtw test`
Expected: failures in suites that start a Search with a source selection, and in the two Search golden logs.

Fix each with one rule: a Search start no longer names a source; the source is the answer to `search.source`.

1. **Engine suites.** A call like `rules.startWalker(…, ActionRef.Search, actor, modifiers, Vector(DecisionOptionRef.Button(s)))` becomes `SearchPlay.searched(rules, state, actor, pile, modifiers)`.
   - `"search:world"` becomes `SearchSource.WorldDeck`.
   - `s"search:regional-discard:${region.key}"` becomes `SearchSource.RegionalDiscard(region)`.
   - A suite that kept the source as a string (`DisciplesSuite`'s `world`/`regional`, `NewsFromAfarSuite.searchedWithNews`) keeps a `SearchSource` instead.
2. **Service suites.** A `GameCommand.StartWalker(ActionRef.Search, StartPayload(actor, modifiers, Vector(Button(…))))` sends `StartPayload(actor, modifiers, Vector.empty)`. It then sends `ParkedServiceFixture.pickSearchSource(actor, SearchSource.WorldDeck)` at the next sequence, and the test reads on from the pick.
3. **`Situation` steps.** `Situation.after` answers the source by default: the first available option, the world deck. This covers `LogScripts.search`, `LogScripts.augury` and `SituationSuite`.
   - Drop the start argument: `start(act, ActionRef.Search)`, and `StartPayload(p1, Vector(Augury.id), Vector.empty)`.
4. **Wire suites.** `shared/…/CommandProtocolSuite` only round-trips a `StartWalker` intent with start args. It keeps its wire shape.
5. **Error expectations.** A refusal changes in two cases:
   - A start that no source can complete is refused with `NoPlayableOption(ActionRef.Search.key)`.
   - A pick of a source that cannot be completed is refused at the answer.
   Change an expected error only where the test is about that refusal, and say which in the diff.
6. **Golden logs.** Regenerate with `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`. Expect `search` and `augury` to change, plus any other script that searches. Read the diff line by line.
   - **Allowed:** the line numbers shift, because the start is its own batch that posts nothing and the "Search" section header moves to the pick's batch.
   - **Not allowed:** any other change, including a "Chose" line for the source.

Files the grep found, all under `src/test/scala/oathdigital/`:
- `application/{GameApplicationServiceActionsSuite, GameApplicationServiceProjectionSuite, ModifierStartControlsSuite}`
- `application/gamelog/LogScripts`
- `gameplay/VisionPlaySuite`
- `gameplay/powers/search/{AugurySuite, DisciplesSuite, NewsFromAfarSuite}`
- `testkit/SituationSuite`

`ForcedLaborSuite` reads `SearchProcedure.legalSources`, which still exists; leave it for Task 4.

Run `./sbtw test` again. Expected: PASS.

- [ ] **Step 8: Commit**

Stage by explicit path:
- the main files above;
- `SearchPlay.scala`, `SearchSourceServiceSuite.scala`, `ParkedServiceFixture.scala`, `SearchFixture.scala`, `SearchProcedureSuite.scala`, `UndoBarrierPlaySuite.scala`;
- every test file and golden log Step 7 changed.

```bash
git commit -m "feat(search): ask the source inside the walker"
```

---

### Task 4: Priced sources and the Search control

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala` (add `startable`, delete `legalSources`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (Search entry)
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala:113-127, 138-145, 189-200`
- Modify: `src/main/scala/oathdigital/application/ScopedProjectionContext.scala:16-20`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:95`
- Create: `src/test/scala/oathdigital/application/SearchSourceRowsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala` (Search section and class doc)
- Modify: `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala:57-66`

**Interfaces:**
- Consumes:
  - Task 2: `PricedOptions.priced`, `PricedOptions.options`.
  - Task 3: `SearchProcedure.sourceDecisionId`, `SearchProcedure.actorRegion`, `SearchProcedure.build`.
- Produces:
  - `SearchOptions.priced`, an `OptionPricing`.
  - `SearchProcedure.startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId, powers: WalkerPowers): Boolean`.
  - Legal control `beginSearch`.
  - `LegalProjection(controls: Vector[String], minorActions: Option[MinorActionsProjection])`, which no longer has a `search` field.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/SearchSourceRowsSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.search.{Disciples, NewsFromAfar}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What the parked source decision shows each pile as: its cost under the
  * selected modifiers, or why it cannot be searched (search source design,
  * rulings 3 and 4). p1 stands at Ancient City, in the Cradle. */
class SearchSourceRowsSuite extends munit.FunSuite:
  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))
  private val home = CatalogNames.site("Ancient City")

  /** The source rows p1 is shown, by pile id ("world", "cradle"), once a
    * Search with `modifiers` starts at `ready`. */
  private def rows(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      (using munit.Location): Map[String, DecisionOptionProjection] =
    val started = accepted(SearchFixture.rules.startWalker(Ready(ready),
      ActionRef.Search, p1, modifiers))
    present(projector.project(ScopedProjectionContext(
      Outcomes.ready(started.state), Some(p1))), "the source decision")
      .query.toVector.flatMap(_.offeredOptions).map(row => row.id -> row).toMap

  /** Both piles hold cards, and p1 has `supply` Supply. */
  private def stocked(supply: Int): Table = Table.start
    .worldDeckTop("Wrestlers", "Errand Boy", "Mercenaries")
    .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant",
      "Second Chance")
    .supply(p1, supply)

  test("each pile is badged with the Supply its Search spends, under its " +
      "own name"):
    val shown = rows(stocked(supply = 5).visionsDrawn(1).ready)
    assertEquals(shown("world").badge, Some("3 Supply"))
    assertEquals(shown("cradle").badge, Some("2 Supply"))
    assertEquals(shown("world").label, "World deck")
    assertEquals(shown("cradle").label, "Cradle discard pile")

  test("News from Afar makes both piles read 0 Supply, and its own favor is " +
      "not in the badge"):
    val ready = stocked(supply = 5)
      .adviser(p1, CatalogNames.denizen("News from Afar")).favor(p1, 2).ready
    val shown = rows(ready, Vector(NewsFromAfar.id))
    assertEquals(shown("world").badge, Some("0 Supply"))
    assertEquals(shown("cradle").badge, Some("0 Supply"))

  test("Disciples lowers a world Search costing 3 to 2"):
    val ready = stocked(supply = 5).adviser(p1, CatalogNames.denizen("Disciples"))
      .darkestSecret(Some(p1), 1).visionsDrawn(1).ready
    assertEquals(rows(ready, Vector(Disciples.id))("world").badge,
      Some("2 Supply"))
    assertEquals(rows(ready)("world").badge, Some("3 Supply"))

  test("Forced Labor's toll joins the badge of both piles"):
    val peaks = CatalogNames.site("Broken Peaks")
    val ready = stocked(supply = 5)
      .denizen(CatalogNames.denizen("Forced Labor"), at = peaks)
      .warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1).ready
    val shown = rows(ready)
    assertEquals(shown("world").badge, Some("2 Supply · 1 favor"))
    assertEquals(shown("cradle").badge, Some("2 Supply · 1 favor"))

  test("a pile the actor cannot afford says how much it needs"):
    // One Vision drawn puts the world deck at 3 Supply.
    val shown = rows(stocked(supply = 2).visionsDrawn(1).ready)
    assertEquals(shown("world").state, "unavailable")
    assertEquals(shown("world").reason, Some("Needs 3 Supply; you have 2"))
    assertEquals(shown("cradle").state, "available")

  test("an empty pile says it is empty rather than what it would cost"):
    // The Cradle discard is empty on the quiet table.
    val ready = Table.start.worldDeckTop("Wrestlers").supply(p1, 5).ready
    val shown = rows(ready)
    assertEquals(shown("cradle").state, "unavailable")
    assertEquals(shown("cradle").reason,
      Some("The Cradle discard pile is empty"))
```

A toll shortfall cannot show at a parked Search: the toll falls on both piles alike, so a start that cannot pay it is refused. `TravelDestinationRowsSuite` covers that wording through the shared `PricedOptions`.

In `ModifierStartControlsSuite`:
- Change the class doc's first line to "A start control counts the modifiers its player".
- Delete `searchSources`.
- Replace the two Search tests with:

```scala
  test("Search is offered when News from Afar would make it free, and the " +
      "start with it is accepted"):
    val ready = brokeSearcher(news = true, favor = 2)
    assert(controls(ready, p1).contains("beginSearch"))
    assert(starts(ready, p1, ActionRef.Search, Vector(NewsFromAfar.id)))
    assert(!starts(ready, p1, ActionRef.Search, Vector.empty))

  test("News from Afar with too little favor to pay offers no Search"):
    assert(!controls(brokeSearcher(news = true, favor = 1), p1)
      .contains("beginSearch"))
    assert(!controls(brokeSearcher(news = false, favor = 2), p1)
      .contains("beginSearch"))
```

In `ForcedLaborSuite`, replace the body of "a searcher who cannot pay is not offered the Search" with:

```scala
    val automatic = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val startable = (ready: ReadyGame) =>
      SearchProcedure.startable(catalog, ready, p1, automatic)
    assert(startable(rivalRulesBoth.ready))
    val broke = rivalRulesBoth.favor(p1, 0).ready
    assert(!startable(broke))
    assert(SearchFixture.start(broke).isLeft)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.SearchSourceRowsSuite oathdigital.application.ModifierStartControlsSuite oathdigital.gameplay.powers.search.ForcedLaborSuite"`
Expected: compile failure, because `startable` is not a member of `SearchProcedure`.

- [ ] **Step 3: Price the sources**

Create `src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala`:

```scala
package oathdigital.gameplay.actions.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.gameplay.walker.{PricedOptions, WalkerPowers}
import oathdigital.model._

/** The source decision as its player is shown it (search source design,
  * rulings 3 and 4), priced by [[PricedOptions]]. An empty pile says so
  * rather than what it would cost. Every other decision of a Search is shown
  * as declared. */
object SearchOptions:
  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    if decide.decisionId != SearchProcedure.sourceDecisionId then
      PricedOptions.options(decide)
    else PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = "Cannot search there now",
      explain = empty(state, decide.owner))

  /** "The world deck is empty", "The Cradle discard pile is empty". */
  private def empty(state: ReadyGame, actor: PlayerId)(
      ref: DecisionOptionRef): Option[String] = for
    source <- SearchSource.fromRef(ref)
    origin <- SearchProcedure.actorRegion(state, actor).toOption
    cards <- SearchRules.draw(state, source, origin).toOption
    if cards.isEmpty
  yield s"The ${SearchSource.name(source)} is empty"
```

In `SearchProcedure`, delete `legalSources` and add:

```scala
  /** Whether a Search could start now: some source survives the same
    * preview a start runs. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))
```

Remove the `PreviewedOption` import if it is now unused.

In the registry's Search entry:
- Add `optionPricing = Some(SearchOptions.priced)` after `requiresPlayableOption = true`.
- Import `SearchOptions` beside `SearchProcedure`.
- Extend the entry's doc with "Its options are priced: each source shows what it costs, or why it cannot be searched."

- [ ] **Step 4: Replace the source list with the Search control**

In `LegalActionProjector`:
- Delete `legalSearch` and its doc.
- In `project`, delete `ordinaryAct` and build `LegalProjection(controls(context, minor, projectedPhasePowers), minor)`.
- In `controls`' `Phase.Act` vector, add `Option.when(searchStartable(context))("beginSearch"),` as the vector's first entry, before `beginRest`.
- Add, after `travelStartable`:

```scala
  /** Whether a Search could start now, asked of the procedure that owns it. */
  private def searchStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Search)(powers =>
      SearchProcedure.startable(catalog, context.ready, context.active.player,
        powers))
```

Remove the `SearchSource` import if the compiler reports it unused.

In `ScopedProjectionContext.scala`, delete `search: Vector[LegalSearchSourceProjection],` from `LegalProjection`.

In `GameProjection.scala`, delete the line `legalSearchSources = legal.search,`. The DTO field keeps its default until Task 5 deletes it. Fix any other `LegalProjection(...)` construction the compiler names.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.SearchSourceRowsSuite oathdigital.application.ModifierStartControlsSuite oathdigital.gameplay.powers.search.ForcedLaborSuite oathdigital.gameplay.SearchProcedureSuite"`
Expected: PASS.

Then run `./sbtw test`. Expected: PASS.
- A projection suite that asserted `legalSearchSources` now asserts `legalControls` contains `beginSearch`.
- A suite listing a viewer's exact controls in Act gains `beginSearch` where a Search is startable. Inspect each such change.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/search/SearchOptions.scala \
  src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/application/LegalActionProjector.scala \
  src/main/scala/oathdigital/application/ScopedProjectionContext.scala \
  src/main/scala/oathdigital/application/GameProjection.scala \
  src/test/scala/oathdigital/application/SearchSourceRowsSuite.scala \
  src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala
git commit -m "feat(search): price each source, and offer one Search control"
```

Stage any other test file Step 5 changed, by explicit path.

---

### Task 5: Frontend: start Search, draw the piles as options, retire the source list

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala:17`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala:90`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala:15, 47-48, 99-100, 146, 170-174`
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala:38`
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala:30-31`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:159-172`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:145-185`
- Create: `frontend/src/test/scala/oathdigital/frontend/SearchLaunchSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala:44-50`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ViewerPresentationSuite.scala:103-104`

**Interfaces:**
- Consumes (Task 4): legal control `beginSearch`. The wire option kinds are `deck` / `world` and `discard-pile` / region key.
- Produces:
  - `GameProjection` no longer has `legalSearchSources`.
  - `BoardHomes.has` is true for `deck` / `world`.

- [ ] **Step 1: Write the failing frontend tests**

Create `frontend/src/test/scala/oathdigital/frontend/SearchLaunchSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** Search starts the walker with no source, and the parked source decision
  * draws each pile's cost or reason on the pile (search source design,
  * rulings 2 to 5). */
class SearchLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val woods = GameSite("woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 1)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))),
    legalControls = Vector("beginSearch"))
    .copy(actionSelectionOpen = true, worldDeckCount = 20)

  private def actions(value: GameProjection, ui: RecordingControls)
      : dom.Element = ActionDecisionRenderer.actionsPanel(value,
    ActionPanels.actingViewer("red"), ParkedDecision.Routed(None, None),
    canControl = true, SessionDrafts.empty, ui).element

  test("the Search control starts the walker and names no source"):
    val ui = new RecordingControls()
    val search = as[dom.html.Button](one(actions(table, ui), ".search-action"))
    assertEquals(search.textContent, "Search")
    search.click()
    assertEquals(ui.submitted, Vector(Intent.StartWalker("search",
      Vector.empty)))

  test("no Search control is drawn without beginSearch"):
    val pane = actions(table.copy(legalControls = Vector.empty),
      new RecordingControls())
    assertEquals(all(pane, ".search-action").size, 0)

  test("a pile's badge is drawn on the pile, and an empty pile says why on " +
      "hover"):
    val reason = "The Cradle discard pile is empty"
    val deck = OptionView(DecisionOptionState("deck", "world", "World deck",
      badge = Some("2 Supply")), OptionState.Available, None)
    val discard = OptionView(DecisionOptionState("discard-pile", "cradle",
      "Cradle discard pile", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val options = new BoardOptions(Vector(deck, discard).map(view =>
      BoardOptionViews.home(view.option) -> view).toMap, _ => ())
    val world = WorldBoardRenderer.world(table, options)
    val pile = one(world, """[data-option-ref="deck:world"]""")
    assert(pile.classList.contains("board-option"))
    assertEquals(one(pile, ".target-detail-badge").textContent, "2 Supply")
    val empty = one(world, """[data-option-ref="discard-pile:cradle"]""")
    assertEquals(empty.getAttribute("title"), reason)
    assertEquals(empty.getAttribute("aria-disabled"), "true")
```

If `Dom` has no `all`, use the helper `DiscardPileSuite` imports.

In `BoardHomesSuite`:
- In "sites, site cards, board cards, slots, banners, banks and players have homes", add `assert(has("deck", "world"))`.
- In "buttons, decks, absent things and hidden cards have none", replace `assert(!has("deck", "world"))` with `assert(!has("deck", "relic"))`.
- Rename that test "buttons, other decks, absent things and hidden cards have none".

In `ViewerPresentationSuite`, change the `.copy(actionSelectionOpen = true, legalSearchSources = …)` to `.copy(actionSelectionOpen = true)`.

In `ProjectionProtocolSuite`, delete the `legalSearchSources = …` line from the fixture.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SearchLaunchSuite oathdigital.frontend.BoardHomesSuite"`
Expected: FAIL.
- `.search-action` is not found, since the renderer still reads `legalSearchSources`.
- `has("deck", "world")` is false.

- [ ] **Step 3: Delete the source list from the wire**

- `GameProjectionDto.scala`: delete `legalSearchSources: Vector[LegalSearchSourceProjection] = Vector.empty,`.
- `WorldProjectionDtos.scala`: delete `final case class LegalSearchSourceProjection(…)`.
- `GameProjectionCodec.scala`:
  - remove `"legalSearchSources",` from `Fields`;
  - delete the `"legalSearchSources" -> encoded(…)` entry (two lines);
  - delete the `sourceRaws` and `sources` generator lines;
  - delete `sources,` from the constructor call in the `yield`;
  - delete `decodeSearchSource`.
- `frontend.scala`: delete the `LegalSearchSource` type and value aliases.

A projection that still carries the field is now refused by `Fields`; no stored projection exists, since projections are built per request.

- [ ] **Step 4: Draw the Search control**

In `ActionDecisionRenderer`, replace the `value.legalSearchSources.foreach { … }` block (from `val groups = new ActionSections;` through its closing `}`) with:

```scala
       val groups = new ActionSections
       if value.legalControls.contains("beginSearch") then
         val search = button("Search", "act-action search-action")
         search.disabled = !canControl || !presentation.showGameplayControls
         search.onclick = _ => submitCommand(
           GameCommand.StartWalker("search", Vector.empty))
         groups.appendKind("search", search)
```

- [ ] **Step 5: Give the world deck a home and draw pile badges**

In `BoardHomes.has`, add after the `"discard-pile"` case:

```scala
      // The world deck is drawn at the top of the World pane; no other deck
      // is drawn.
      case "deck" => option.id == "world"
```

and change the doc's "a discard pile" to "the world deck or a discard pile".

In `WorldBoardRenderer`, add:

```scala
 /** The parked decision's badge for the option on `keys`, drawn inside
   * `target`: what choosing it costs (travel destination and search source
   * designs, ruling 3). */
 private def badged(target: dom.Element, keys: Vector[String],
     options: BoardOptions): Unit =
   options.view(keys).flatMap(_.option.badge).foreach(badge =>
     target.appendChild(text("span", "target-detail-badge", badge)))
```

In `world`, replace the `panel.appendChild(pileDisplay("World deck", …))` call with:

```scala
   // The world deck is a Search's source and Clay Rattle's pile.
   val deckKeys = Vector(BoardOptionViews.key("deck", "world"))
   val deck = pileDisplay("World deck", value.worldDeckCount,
     value.worldDeckTopCardKind)
   options.decorate(deck, deckKeys)
   badged(deck, deckKeys, options)
   panel.appendChild(deck)
```

Replace the discard pile's `section.appendChild(options.control(openablePile(…), Vector(…), …))` with:

```scala
     val discardKeys = Vector(BoardOptionViews.key("discard-pile",
       region.regionId))
     val discard = openablePile("Discard", region.discardCount,
       region.discardTopCardKind, discardTitle, region.discardCards)
     badged(discard, discardKeys, options)
     section.appendChild(options.control(discard, discardKeys,
       Option.when(region.discardCount > 0)(discardTitle -> (anchor =>
         CardInspection.openCards(s"$discardTitle (${region.discardCount})",
           region.discardCards, anchor)))))
```

Replace the site's inline badge (`options.view(siteKeys).flatMap(_.option.badge).foreach(…)`) with `badged(control, siteKeys, options)`, keeping the comment above it.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SearchLaunchSuite oathdigital.frontend.BoardHomesSuite oathdigital.frontend.DiscardPileSuite oathdigital.frontend.TravelLaunchSuite"`
Expected: PASS.

Then run `./sbtw "test" "frontend/test"`. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala \
  shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala \
  frontend/src/main/scala/oathdigital/frontend/frontend.scala \
  frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala \
  frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala \
  frontend/src/test/scala/oathdigital/frontend/SearchLaunchSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ViewerPresentationSuite.scala
git commit -m "feat(frontend): start Search and pick its source on the board"
```

---

### Task 6: Docs and the full gate

**Files:**
- Modify: `docs/ROADMAP.md:315-321`
- Modify: `docs/rules/implementation-traceability.md:95`
- Modify: `docs/superpowers/specs/2026-10-07-search-source-walker-design.md:3`

- [ ] **Step 1: Trim the roadmap item**

Replace the **Start controls and modifier combinations** item with:

```markdown
- [ ] **Start controls and modifier combinations.** A start control is
  offered when the action could start with no modifier, with any one
  selectable modifier, or with all of them together
  (`ModifierOffers.selections`). A start that needs two modifiers but not a
  third is not found.
```

- [ ] **Step 2: Record the source decision in the traceability row**

In the Search row (line 95), insert after the sentence ending "…`SearchSuite`, wire/application/server/frontend suites cover the slice.":

```markdown
The source is the walk's first board decision, picked after the modifiers: each pile shows its cost under them or why it cannot be searched ([search source design](../superpowers/specs/2026-10-07-search-source-walker-design.md)).
```

- [ ] **Step 3: Mark the spec implemented**

Change the spec's status line to `**Status:** designed and implemented 2026-10-07.`, keeping the rest of the paragraph.

- [ ] **Step 4: Run the full gate**

Run, in order:
- `./sbtw "test" "frontend/test"` — Expected: PASS.
- `./sbtw frontend/fullOptJS` — Expected: success.
- `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py` — Expected: both pass.
- `git diff --check` — Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add docs/ROADMAP.md docs/rules/implementation-traceability.md \
  docs/superpowers/specs/2026-10-07-search-source-walker-design.md
git commit -m "docs: record the Search source decision"
```
