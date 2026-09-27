# Catalog Batch 2, Slice 3b (Actions on Yourself, Part 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Scryer (19) and Oracular Pig (R14), with the rest of the card-list view the spec calls N5: the `Inspect` decision, the card list in the frontend, and `NoteArg.Cards` above five cards.

**Architecture:**

- Both cards are ACTION phase powers built on `PaidAction`, registered through `SelfActionPowers` like slice 3a's. Each records one `Peek` per card it looks at, then writes its note, then parks on an `Inspect` decision that shows the cards until the player presses Done.
- A peek at a card in the world deck or a discard pile already records `knowledge.advisers`, but `GamePresentationProjector.identifiesCard` never names a card in a pile. Task 1 names such a card to its peeker. That is safe because a card that enters a pile is forgotten (`CardKnowledgeMoves`). Task 1 also makes a `Shuffle` forget its pile's cards, since after a shuffle nobody knows which card is where.
- `NoteArg.Cards` renders more than five cards as a new `LogSpan.Cards`, "6 cards", which carries each card's face or back as a `CardDetailsProjection`. With no card its viewer may identify, it stays plain text. Generic lines keep today's phrase.
- `DecisionQuery.Inspect(cards, heading, doneLabel)` has no choice. Its one answer is the choose-one answer of its Done button, `DecisionQuery.Inspect.Done`, so the answer, journal and command wires are unchanged. The projector sends the cards as `CardDetailsProjection`s, faces or backs by the same rule as a decision's subject cards. Pressing Done writes no "Chose" log line.
- The frontend gets one card-list builder, `CardList`. The Inspect panel draws it inline above Done. A log line's "6 cards" control opens it in the existing card overlay, which keeps DESIGN.md's count of overlay surfaces.

**Tech Stack:** Scala 3 on the JVM (server) and Scala.js (frontend, tests under jsdom), munit, built through `./sbtw`. The frontend styling uses the `impeccable` skill, as the spec requires.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md` ("N5. The card-list view", "Slicing", "Log lines", "Testing"), with the per-card rulings in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 3: actions on yourself"). Read both before starting. Slice 3a (plan `docs/superpowers/plans/2026-09-27-catalog-batch-2-slice3a.md`) is merged and supplies `NoteArg.Pile`, `SearchSource.name` and `CardZones.pile`.

**Rulings made while planning:**

- `Inspect` answers with its Done button rather than a new answer kind. A lone button is already a legal consent step (`DecisionQueries.wellFormed`), and this keeps `DecisionAnswerCodec`, `GameIntentMapper` and the shared command intents untouched.
- The Inspect panel shows the card list inline rather than opening the overlay by itself. A panel opening a modal on render would need the table's re-render bookkeeping; the inline list is the same view.
- Oracular Pig with an empty world deck peeks at nothing, asks nothing and writes no note. The spec gives it no line for that case.
- Scryer's empty-pile note is built by the same covering `Note` node as its full one. It has no peek line to cover, so covering changes nothing.
- A discard pile is offered even when empty: the card lets the player peek at "any one discard pile".

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, on the server, shared and frontend builds. An unused import or private member, or a non-exhaustive match over a sealed type, fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `WalkerPanelSupport.scala` is at 468 lines; new panels go in their own files.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines are exactly the spec's (`Log lines`, "Phase powers"), where `{Red}` is the acting player:

  | Card | Key | Line | Covers |
  |---|---|---|---|
  | Scryer | `used` | Scryer: {Red} peeked at the {Cradle discard pile}: {cards}. | yes |
  | Scryer, empty pile | `used.empty` | Scryer: {Red} peeked at the {Cradle discard pile}, which was empty. | yes (nothing to cover) |
  | Oracular Pig | `used` | Oracular Pig: {Red} peeked at the top of the world deck: {cards}. | yes |

  `{cards}` is `NoteArg.Cards`, top first. `{Cradle discard pile}` is `NoteArg.Pile`.
- Card ids: Scryer `DenizenId("19")`, power `denizen.scryer`, cost 1 secret placed. Oracular Pig `RelicId("R14")`, power `relic.oracular-pig`, free.
- Storage order: the world deck is stored top first; a discard pile is stored top last. Both cards show their cards top first.
- Up to five cards is `LogWords.Inline`; a card list of more is "6 cards", never "6 Denizens".
- Baselines: record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare against them at the end. Shared suites (`shared/src/test`) run in both builds.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Modify `src/main/scala/oathdigital/application/GamePresentationProjector.scala` | Names a peeked card in the world deck or a discard pile to its peeker. |
| Modify `src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala` | `forget` visible to the package. |
| Modify `src/main/scala/oathdigital/gameplay/operations/PileOperations.scala` | A shuffle forgets its pile's cards. |
| Modify `src/main/scala/oathdigital/application/gamelog/LogEntry.scala` | `LogSpan.Cards`. |
| Modify `src/main/scala/oathdigital/application/gamelog/LogWords.scala` | `listed`, the note card list rule. |
| Modify `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` | `NoteArg.Cards` through `listed`. |
| Modify `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala` | The `cards` span on the wire. |
| Modify `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala` | `LogSpanWire.cards`. |
| Modify `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala` | Encodes and decodes `cards`. |
| Modify `src/main/scala/oathdigital/model/Decisions.scala` | `DecisionQuery.Inspect`. |
| Modify `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` | Validates `Inspect` and its answer. |
| Modify `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | Projects `Inspect`. |
| Modify `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` | `DecisionQueryProjection.Inspect`. |
| Modify `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala` | The `inspect` form. |
| Modify `src/main/scala/oathdigital/application/gamelog/DetailLines.scala` | No "Chose" line for Done. |
| Create `frontend/src/main/scala/oathdigital/frontend/CardList.scala` | The card-list grid. |
| Create `frontend/src/main/scala/oathdigital/frontend/InspectPanel.scala` | The Inspect decision's panel. |
| Modify `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala` | Routes `Inspect` to its panel. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/Scryer.scala` | Scryer. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/OracularPig.scala` | Oracular Pig. |
| Modify `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` | Registers both. |
| Modify `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala` | A `Cards` request. |
| Modify `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala` | `showCards`. |
| Modify `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala` | Wires the `Cards` request. |
| Modify `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` | The "6 cards" control. |
| Modify `frontend/styles.css`, `DESIGN.md` | The card list's styling and its design record. |

---

### Task 1: A peeked pile card is known to its peeker; a shuffle forgets

**Files:**
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` (`identifiesCard` and its scaladoc)
- Modify: `src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/PileOperations.scala`
- Test: `src/test/scala/oathdigital/application/CardKnowledgeSuite.scala`, `src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala`

**Interfaces:**
- Produces: `GamePresentationProjector.identifiesCard` and `identifiesAt` return true for a world card in `CardContainer.Deck(CardDeck.World)` or `CardContainer.RegionalDiscard(_)` when `ready.knowledge.advisers(viewer)` holds it. `CardKnowledgeMoves.forget(knowledge: CardKnowledge, id: CardId): CardKnowledge` is visible to `gameplay.operations`.

- [ ] **Step 1: Write the failing tests**

In `CardKnowledgeSuite.scala`, after the test "a face-down adviser discarded or dispossessed is forgotten by everyone", add:

```scala

  /** Scryer and Oracular Pig peek into a pile. A card entering a pile is
    * forgotten, so a record of a card still in one comes from such a peek. */
  test("a card peeked in the world deck or a discard pile is named to its peeker alone"):
    val Vector(top, next) = current.commonCards.worldDeck.take(2): @unchecked
    val staged = initialReady.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(worldDeck = c.commonCards.worldDeck.filterNot(_ == next),
        regionalDiscards = c.commonCards.regionalDiscards.updated(Region.Cradle,
          c.commonCards.discard(Region.Cradle) :+ next))))
    val peeked = Vector(Peek(owner, top, Location.Deck(CardDeck.World)),
      Peek(owner, next, Location.RegionalDiscard(Region.Cradle)))
      .foldLeft(staged)((ready, op) =>
        executor.execute(ready, op).fold(error => fail(s"$op: $error"), identity))
    Vector[CardId](top, next).foreach { card =>
      assert(!knows(staged, owner, card), s"$card before the peek")
      assert(knows(peeked, owner, card), s"$card to its peeker")
      assert(!knows(peeked, other, card), s"$card to another player")
    }
```

In `ShuffleOperationSuite.scala`, add at the end:

```scala

  test("a shuffle forgets its pile's cards, since no one knows which is where"):
    val peeker = discarded.game.current.players.head.player
    val peeked = pile.foldLeft(discarded)((ready, card) => executor.execute(
      ready, Peek(peeker, card, Location.RegionalDiscard(Region.Cradle)))
      .toOption.get)
    assert(pile.forall(peeked.knowledge.advisers(peeker).contains))
    val shuffled = executor.execute(peeked, Shuffle(cradle, Some(pile.reverse)))
      .toOption.get
    assert(shuffled.knowledge.advisers.values.forall(known =>
      pile.forall(!known.contains(_))), shuffled.knowledge.advisers.toString)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.CardKnowledgeSuite oathdigital.gameplay.operations.ShuffleOperationSuite"`
Expected: FAIL. The peeker is not told the card ("to its peeker"), and the shuffled pile is still known.

- [ ] **Step 3: Name a peeked pile card to its peeker**

In `GamePresentationProjector.identifiesCard`, add a case just before the final `case _ => false`:

```scala
      // A world card peeked in the world deck or a discard pile (Scryer,
      // Oracular Pig). A card entering a pile is forgotten, and so is a
      // shuffled pile's (`CardKnowledgeMoves`, `PileOperations`), so only
      // such a peek leaves a record here.
      case CardContainer.Deck(CardDeck.World) | CardContainer.RegionalDiscard(_) =>
        viewer.exists(player => ready.knowledge.advisers
          .getOrElse(player, Vector.empty).exists(_ == id))
```

In the scaladoc above `identifiesCard`, replace the last bullet:

```scala
    *  - Everything else -- decks, discards, the reliquary, set-aside relics,
    *    the dispossessed pile, suited reserves, atlas sites -- is never
    *    identified. A card in a deck has no orientation at all, so it must be
    *    rejected by its container rather than by being facedown.
```

with:

```scala
    *  - A world card in the world deck or a discard pile is named only to a
    *    viewer who peeked at it there (Scryer, Oracular Pig).
    *  - Everything else -- other decks, the reliquary, set-aside relics, the
    *    dispossessed pile, suited reserves, atlas sites -- is never
    *    identified. A card in a deck has no orientation at all, so it must be
    *    rejected by its container rather than by being facedown.
```

- [ ] **Step 4: Make a shuffle forget its pile**

In `CardKnowledgeMoves.scala`, change `private def forget(` to `def forget(`, and give it a scaladoc:

```scala
  /** Nobody knows `id` any more: it went into a pile, or its pile was
    * shuffled. */
  def forget(knowledge: CardKnowledge, id: CardId): CardKnowledge =
```

In `PileOperations.scala`, extend the object's scaladoc with a second paragraph:

```scala
  *
  * A shuffle forgets every card of its pile, for every player: a peek told
  * a player which card lay where, and after a shuffle nobody knows.
```

and replace the last case of `shuffle`:

```scala
      case Some(cards) => Right(ready.updateCurrent(current => current.copy(
        commonCards = current.commonCards.withPile(pile, cards))))
```

with:

```scala
      case Some(cards) => Right(ready.updateCurrent(current => current.copy(
        commonCards = current.commonCards.withPile(pile, cards))).copy(
        knowledge = cards.foldLeft(ready.knowledge)(CardKnowledgeMoves.forget)))
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.CardKnowledgeSuite oathdigital.gameplay.operations.ShuffleOperationSuite oathdigital.application.gamelog.*"`
Expected: PASS, the game log suites included.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/GamePresentationProjector.scala \
  src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala \
  src/main/scala/oathdigital/gameplay/operations/PileOperations.scala \
  src/test/scala/oathdigital/application/CardKnowledgeSuite.scala \
  src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala
git commit -m "feat(engine): a card peeked in a pile is known to its peeker until shuffled"
```

---

### Task 2: A note's list of more than five cards

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`, `LogWords.scala`, `PowerLines.scala`, `GameLogProjector.scala` (all in `src/main/scala/oathdigital/application/gamelog/`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`, `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`, `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`

**Interfaces:**
- Consumes: Task 1's `identifiesAt`, through `LogWords.seen`.
- Produces: `LogSpan.Cards(cards: Vector[CardDetailsProjection])` with `text` "N cards"; `LogSpanWire(..., cards: Vector[CardDetailsProjection] = Vector.empty)`, kind `"cards"`; `LogWords.Inline = 5`.

- [ ] **Step 1: Write the failing tests**

In `GameLogPowerLinesSuite.scala`, after the test "a card list reads as one phrase, and a banner by its name", add:

```scala

  test("more than five cards read as a card list, each its face or its back"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val deck = last.game.current.commonCards.worldDeck.take(5)
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(ParkedServiceFixture.silverTongueCard +: deck)))
    val entry = ours(listed, Some(script.actor)).head
    val lists = entry.spans.collect { case cards: LogSpan.Cards => cards }
    assertEquals(lists.map(_.text), Vector("6 cards"))
    assertEquals(lists.head.cards.map(card => card.hidden -> card.cardId),
      (false -> ParkedServiceFixture.silverTongueCard.value) +:
        deck.map(_ => true -> "hidden"))
    assertEquals(text(entry),
      s"Silver Tongue: ${name(script.actor)} said 6 cards.")

  test("more than five cards its viewer may identify none of read as a count"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(last.game.current.commonCards.worldDeck.take(6))))
    val entry = ours(listed, Some(script.actor)).head
    assert(!entry.spans.exists(_.isInstanceOf[LogSpan.Cards]),
      entry.spans.toString)
    assertEquals(text(entry),
      s"Silver Tongue: ${name(script.actor)} said 6 cards.")
```

In `LogPageCodecSuite.scala`, add at the end:

```scala

  test("a card list span round-trips its faces and backs"):
    val list = LogPageWire("g", 0, 9, Vector(LogEntryWire(7, 0, "action", 1,
      Vector(LogSpanWire("cards", "6 cards", cards = Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")),
        CardDetailsProjection("hidden", "vision", "Facedown vision",
          orientation = Some("face-down"), hidden = true)))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(list)), Right(list))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite oathdigital.protocol.LogPageCodecSuite"`
Expected: compilation FAILS: `LogSpan.Cards` and `LogSpanWire`'s `cards` do not exist.

- [ ] **Step 3: Add the span and its wire field**

In `LogEntry.scala`, add the import `import oathdigital.protocol.projection.CardDetailsProjection` below the package line, and add a case after `Cost` in `object LogSpan`:

```scala
  /** More cards than a line names inline, read as "6 cards": the list a
    * client opens, in order, each card's face if its viewer may identify it
    * and its back otherwise (catalog batch 2, N5). */
  final case class Cards(cards: Vector[CardDetailsProjection]) extends LogSpan:
    def text: String = s"${cards.size} cards"
```

In `LogProjectionDtos.scala`, replace the `LogSpanWire` scaladoc and case class with:

```scala
/** One span of a log entry on the wire. `kind` is `text`, `player`, `card`,
  * `site`, `amount`, `cost`, `dice` or `cards`; `text` is always present, so
  * a client that ignores kinds still shows a sentence. A card shown by its
  * back is a `text` span: no field carries a hidden card's id. A `dice`
  * span's `id` holds its face wire names separated by spaces, and its `unit`
  * names the die, `attack` or `defense`. A `cards` span's `cards` are the
  * cards it lists, in order, each a face or a back (a hidden card projects
  * as `hidden`). */
final case class LogSpanWire(kind: String, text: String,
    id: Option[String] = None, value: Option[Int] = None,
    unit: Option[String] = None,
    cards: Vector[CardDetailsProjection] = Vector.empty)
```

In `LogPageCodec.scala`:

- Change `SpanFields` to `Set("kind", "text", "id", "value", "unit", "cards")`.
- In `encodeSpan`, append after the `unit` line (inside the `ujson.Obj.from(...)` argument):

  ```scala
      ++ Option.when(span.cards.nonEmpty)("cards" ->
        (encoded(span.cards)(WorldProjectionCodec.encodeCard): ujson.Value)))
  ```

  so the expression ends `span.unit.map(...) ++ Option.when(...)(...))`.
- In `decodeSpan`, add after the `unit` line:

  ```scala
      cards <- default(value, "cards", path, Vector.empty[CardDetailsProjection])(
        (raw, child) => array(raw, child).flatMap(
          traverse(_, child)(WorldProjectionCodec.decodeCard)))
  ```

  and yield `LogSpanWire(kind, text, id, amount, unit, cards)`.

In `GameLogProjector.span`, add a case:

```scala
    case list: LogSpan.Cards => LogSpanWire("cards", list.text, cards = list.cards)
```

In `GoldenLog.span` (test sources), add a case:

```scala
    case list: LogSpan.Cards => s"[cards:${list.cards.map(card =>
      if card.hidden then s"back ${card.cardKind}" else card.cardId)
      .mkString(" ")}|${list.text}]"
```

- [ ] **Step 4: Render a note's card list through `listed`**

In `LogWords.scala`, after `def one(word: CardWord)`, add:

```scala
  /** A power note's card list (catalog batch 2, N5): up to
    * [[LogWords.Inline]] cards as one phrase, as `cards` writes it. More are
    * a `Cards` span a client opens, each card its face or its back, or plain
    * "6 cards" when the viewer may identify none of them. */
  def listed(ids: Vector[CardId], states: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val words = ids.map(seen(_, states, viewer))
    if ids.size <= LogWords.Inline then cards(words)
    else if words.forall(_.isInstanceOf[CardWord.Back]) then
      Vector(LogSpan.Text(s"${ids.size} cards"))
    else Vector(LogSpan.Cards(ids.zip(words).map((id, word) => face(id, word))))

  /** A card as a card list shows it: its face when named, else its back. */
  private def face(id: CardId, word: CardWord)
      : oathdigital.protocol.projection.CardDetailsProjection = word match
    case CardWord.Named(_) => presentation.cardDetails(id, None, hidden = false)
    case CardWord.Back(_) => presentation.hiddenCard(id match
      case _: RelicId => "relic"
      case other => presentation.cardKind(other))
```

In `object LogWords`, before `def backOf`, add:

```scala
  /** The most cards a power note names inline (catalog batch 2, N5). */
  val Inline: Int = 5

```

In `PowerLines.argument`, replace:

```scala
    case NoteArg.Cards(ids) => words.cards(ids.map(words.seen(_, seen, viewer)))
```

with:

```scala
    case NoteArg.Cards(ids) => words.listed(ids, seen, viewer)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.gamelog.* oathdigital.protocol.LogPageCodecSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/LogEntry.scala \
  src/main/scala/oathdigital/application/gamelog/LogWords.scala \
  src/main/scala/oathdigital/application/gamelog/PowerLines.scala \
  src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala \
  shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala \
  src/test/scala/oathdigital/application/gamelog/GoldenLog.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala
git commit -m "feat(log): a note's list of more than five cards opens as a card list"
```

---

### Task 3: The `Inspect` decision and its panel

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/CardList.scala`, `frontend/src/main/scala/oathdigital/frontend/InspectPanel.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala`
- Test: create `src/test/scala/oathdigital/gameplay/walker/InspectQuerySuite.scala` and `frontend/src/test/scala/oathdigital/frontend/InspectPanelSuite.scala`; modify `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala`

**Interfaces:**
- Consumes: Task 1's `identifiesCard`.
- Produces:
  - `DecisionQuery.Inspect(cards: Vector[CardId], heading: Option[String], doneLabel: String = "Done")` and `DecisionQuery.Inspect.Done: DecisionOptionRef.Button` (key `"done"`).
  - `DecisionQueryProjection.Inspect(cards: Vector[CardDetailsProjection], done: DecisionOptionProjection, heading: Option[String] = None)`, form `"inspect"`.
  - Frontend: `CardList.render(title: String, cards: Vector[CardDetails]): dom.Element` (a `section.card-list` holding `h3.card-list-title` and `ol.card-list-grid` of `li.card-list-item`, each holding one `CardFace`); `ParkedDecision.Surface.Inspect(decision, query)`; `InspectPanel.render(surface, canControl, panel, controls)` and `InspectPanel.cardCount(count: Int): String`.

- [ ] **Step 1: Write the failing server and shared tests**

Create `src/test/scala/oathdigital/gameplay/walker/InspectQuerySuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{DecisionAnswer, DecisionOptionRef, DecisionQuery,
  DenizenId, OathViolation, PlayerId}

/** The `Inspect` decision (catalog batch 2, N5): cards to look at and one
  * Done button, checked with hand-built queries as `DecisionQuerySuite`
  * checks the other shapes. */
class InspectQuerySuite extends munit.FunSuite:
  private val decisionId = "power.scryer.inspect"
  private val anyone = PlayerId("player-red")
  private val cards = Vector(DenizenId("1"), DenizenId("2"))
  private val inspect = DecisionQuery.Inspect(cards,
    heading = Some("Scryer: the Cradle discard pile"))

  private def wellFormed(query: DecisionQuery) =
    DecisionQueries.wellFormed(decisionId, query)
  private def accepts(answer: DecisionAnswer) =
    DecisionQueries.accepts(decisionId, inspect, answer, anyone)
  private def invalid(detail: String): Either[OathViolation, Unit] =
    Left(OathViolation.InvalidEventOrder(s"decision $decisionId $detail"))

  test("an inspect query with cards, a heading and a done label is well formed"):
    assertEquals(wellFormed(inspect), Right(()))

  test("an inspect query with no cards, a repeated card or blank copy is malformed"):
    assertEquals(wellFormed(inspect.copy(cards = Vector.empty)),
      invalid("declares no cards"))
    assertEquals(wellFormed(inspect.copy(cards = cards :+ cards.head)),
      invalid("declares duplicate cards"))
    assertEquals(wellFormed(inspect.copy(heading = None)),
      invalid("declares no heading"))
    assertEquals(wellFormed(inspect.copy(doneLabel = " ")),
      invalid("declares a blank done label"))

  test("its one answer is its Done button"):
    assertEquals(accepts(
      DecisionAnswer.ChooseOneAnswer(DecisionQuery.Inspect.Done)), Right(()))

  test("any other answer is refused"):
    assertEquals(accepts(DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("stop"))), invalid("expects its Done button"))
    assertEquals(accepts(DecisionAnswer.ChooseAmountAnswer(1)),
      invalid("expects its Done button"))
```

In `ProjectionProtocolSuite.scala`, after the test "a ranged distribute query round-trips both totals", add:

```scala

  test("an inspect query round-trips its cards, its done button and its heading"):
    val inspect = DecisionQueryProjection.Inspect(Vector(known, hidden),
      DecisionOptionProjection("button", "done", "Done"),
      heading = Some("Scryer: the Cradle discard pile"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(inspect))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))
```

In `GameLogPowerLinesSuite.scala`, add `ChoicePayload` to the `oathdigital.gameplay.walker` import, and after the test "more than five cards its viewer may identify none of read as a count" add:

```scala

  test("pressing Done on an Inspect writes no Chose line"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    def pressed(ref: DecisionOptionRef) = inserted(steps, take(steps),
      WalkerStepRecorded("inspect", ChoicePayload("power.scryer.inspect",
        DecisionAnswer.ChooseOneAnswer(ref), script.actor), Vector.empty,
        Vector.empty))
    def chose(lines: Vector[String]) = lines.count(_.startsWith("Chose "))
    val before = chose(lines(steps))
    assertEquals(chose(lines(pressed(DecisionOptionRef.Button("other")))),
      before + 1)
    assertEquals(chose(lines(pressed(DecisionQuery.Inspect.Done))), before)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.InspectQuerySuite oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: compilation FAILS: `DecisionQuery.Inspect` and `DecisionQueryProjection.Inspect` do not exist.

- [ ] **Step 3: Declare the query and validate it**

In `Decisions.scala`, inside `object DecisionQuery`, after `Negotiate`, add:

```scala

  /** Look at `cards`, in order, then press Done (catalog batch 2, N5). There
    * is no choice: the one answer is the choose-one answer of the `Done`
    * button, labelled `doneLabel`. The action records a `Peek` of each card
    * before it, so the owner may identify them. The heading is required: a
    * list of cards with no heading says nothing about where they lie.
    */
  final case class Inspect(cards: Vector[CardId], heading: Option[String],
      doneLabel: String = "Done") extends DecisionQuery
  object Inspect:
    /** The one answer an `Inspect` takes. */
    val Done: DecisionOptionRef.Button = DecisionOptionRef.Button("done")
```

In `DecisionQueries.scala`, add `DecisionQuery.Inspect` handling. In `wellFormed`, add a case after `ChooseOne`:

```scala

    case DecisionQuery.Inspect(cards, heading, doneLabel) =>
      for
        _ <- require(cards.nonEmpty, decisionId, "declares no cards")
        _ <- require(cards.distinct.size == cards.size, decisionId,
          "declares duplicate cards")
        _ <- require(heading.exists(_.trim.nonEmpty), decisionId,
          "declares no heading")
        _ <- require(doneLabel.trim.nonEmpty, decisionId,
          "declares a blank done label")
      yield ()
```

In `accepts`, add a case after `ChooseOne`:

```scala

    case _: DecisionQuery.Inspect => answer match
      case DecisionAnswer.ChooseOneAnswer(DecisionQuery.Inspect.Done) =>
        Right(())
      case _ =>
        reject(decisionId, "expects its Done button")
```

- [ ] **Step 4: Project it, and put it on the wire**

In `ActionProjectionDtos.scala`, inside `object DecisionQueryProjection`, after `Negotiate`, add:

```scala

  /** Look at `cards`, in order, then press `done` (catalog batch 2, N5).
    * Each card is its face if the owner may identify it, else its back.
    * `done` is the one option, answered as a choose-one.
    */
  final case class Inspect(cards: Vector[CardDetailsProjection],
      done: DecisionOptionProjection, heading: Option[String] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = Vector(done)
```

In `ActionProjectionCodec.scala`:

- In `encodeDecisionQuery`, add a case:

  ```scala
      case DecisionQueryProjection.Inspect(cards, done, heading) => ujson.Obj(
        "form" -> "inspect",
        "cards" -> encoded(cards)(encodeCard),
        "done" -> encodeOptionRow(done),
        "heading" -> stringOption(heading))
  ```

- In `decodeDecisionQuery`, add `case "inspect" => decodeInspect(value, path)` before `case other`.
- After `decodeNegotiate`, add:

  ```scala

  private def decodeInspect(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "cards", "done", "heading"), path)
    cardRaws <- array(value, "cards", path)
    cards <- traverse(cardRaws, s"$path.cards")(decodeCard)
    done <- field(value, "done", path).flatMap(
      decodeOptionRow(_, s"$path.done"))
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.Inspect(cards, done, heading)
  ```

- In the scaladoc above `encodeDecisionQuery`, change "The six form spellings" to "The seven form spellings".

In `WalkerDecisionProjector.scala`:

- Add a private helper after `subjectCards`, and use it inside `subjectCards`:

  ```scala
  /** A card as its viewer may see it: its face when they may identify it,
    * else the redacted back [[GamePresentationProjector.hiddenCard]] gives an
    * unidentifiable board slot. */
  private def shown(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex], id: CardId): CardDetailsProjection =
    index.flatMap(_.get(id)) match
      case Some(located) =>
        val orientation = orientationOf(located.state)
        if presentation.identifiesCard(ready, viewer, id, orientation,
            located.location.container) then
          presentation.cardDetails(id, orientation, hidden = false)
        else presentation.hiddenCard(presentation.cardKind(id))
      case None => presentation.hiddenCard(presentation.cardKind(id))
  ```

  `subjectCards` then ends with `}.map(shown(ready, viewer, index, _))` in place of its own `.map { id => ... }` block.

- In `queryProjection`, add a case after `Negotiate`:

  ```scala
      case DecisionQuery.Inspect(cards, heading, doneLabel) =>
        Some(DecisionQueryProjection.Inspect(
          cards.map(shown(ready, viewer, index, _)),
          DecisionOptionProjection(DecisionQuery.Inspect.Done.kind,
            DecisionQuery.Inspect.Done.wireId, doneLabel), heading))
  ```

In `DetailLines.decision`, make the first case of `val refs = answer match`:

```scala
        // Pressing Done after looking at cards is not a choice (N5).
        case ChooseOneAnswer(DecisionQuery.Inspect.Done) => Vector.empty
```

If the compiler reports another exhaustive match over `DecisionQuery` or `DecisionQueryProjection`, add an arm that treats `Inspect` as a query with no options to narrow or preview, and note it in your report.

- [ ] **Step 5: Run the server and shared tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.protocol.* oathdigital.application.*"`
Expected: PASS.

- [ ] **Step 6: Write the failing frontend tests**

In `ParkedDecisionSuite.scala`, after the test "each form routes to the surface that answers it", add:

```scala

  test("an inspect routes to its card list panel"):
    val inspect = DecisionQueryState.Inspect(Vector.empty,
      DecisionOptionState("button", "done", "Done"), heading)
    val at = parked("use-power", "power.scryer.inspect", inspect)
    assertEquals(routeOf(at).surface, Some(Surface.Inspect(at, inspect)))
```

Create `frontend/src/test/scala/oathdigital/frontend/InspectPanelSuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.{DecisionAnswerWire, GameIntent}

/** The Inspect decision's panel: the cards in order, faces and backs, and
  * the one Done button. Runs under jsdom. */
class InspectPanelSuite extends munit.FunSuite:
  private val oak = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"))
  private val back = CardDetails("hidden", "vision", "Facedown vision",
    orientation = Some("face-down"), hidden = true)
  private val done = DecisionOptionState("button", "done", "Done")
  private val query = DecisionQueryState.Inspect(Vector(oak, back), done,
    Some("Scryer: the Cradle discard pile"))
  private val decision = WalkerDecisionState("use-power",
    "power.scryer.inspect", "decide", query = Some(query))

  private def render(canControl: Boolean, ui: RecordingControls): dom.Element =
    val panel = dom.document.createElement("div")
    InspectPanel.render(ParkedDecision.Surface.Inspect(decision, query),
      canControl, panel, ui)
    panel

  private def doneButton(panel: dom.Element): dom.html.Button =
    panel.querySelector("button.walker-choice").asInstanceOf[dom.html.Button]

  test("the panel shows its heading and every card in order, face or back"):
    val panel = render(canControl = true, new RecordingControls())
    assertEquals(panel.querySelector("h2").textContent,
      "Scryer: the Cradle discard pile")
    assertEquals(panel.querySelector(".card-list-title").textContent, "2 cards")
    val faces = panel.querySelectorAll(".card-list-item .card-face").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(faces.map(_.getAttribute("data-card-id")),
      Vector("d1", "hidden"))
    assert(faces(1).classList.contains("card-face-down"))

  test("Done answers with its one button, and only for a player in control"):
    val ui = new RecordingControls()
    doneButton(render(canControl = true, ui)).click()
    assertEquals(ui.submitted, Vector(GameIntent.ResolveWalker(
      "power.scryer.inspect", DecisionAnswerWire.ChooseOneWire("button", "done"))))
    assert(doneButton(render(canControl = false, new RecordingControls()))
      .disabled)
```

- [ ] **Step 7: Run the frontend tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ParkedDecisionSuite oathdigital.frontend.InspectPanelSuite"`
Expected: compilation FAILS: `Surface.Inspect` and `InspectPanel` do not exist (and `formSurface` no longer covers every form).

- [ ] **Step 8: Build the card list, the panel and the route**

Create `frontend/src/main/scala/oathdigital/frontend/CardList.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{element, text}

/** Cards in the order given, as a scrolling grid of card faces, each its
  * face or its back as the server projected it (catalog batch 2, N5). The
  * Inspect panel draws one, and the card overlay draws one for a log line's
  * card list. A face is still a button that opens the card overlay.
  */
private[frontend] object CardList:
  def render(title: String, cards: Vector[CardDetails]): dom.Element =
    val list = element("section", "card-list")
    list.appendChild(text("h3", "card-list-title", title))
    val grid = element("ol", "card-list-grid")
    cards.foreach { card =>
      val item = element("li", "card-list-item")
      item.appendChild(CardFace.render(card))
      grid.appendChild(item)
    }
    list.appendChild(grid)
    list
```

Create `frontend/src/main/scala/oathdigital/frontend/InspectPanel.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, text}

/** The Inspect decision's panel (catalog batch 2, N5): its heading, the
  * cards to look at as a card list, and the one Done button that answers
  * it. The list is drawn inline, so the decision shows its cards without
  * opening an overlay by itself.
  */
private[frontend] object InspectPanel:
  def render(surface: ParkedDecision.Surface.Inspect, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val query = surface.query
    panel.appendChild(text("h2", "",
      WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(CardList.render(cardCount(query.cards.size),
      query.cards))
    val done = button(query.done.label, "walker-choice")
    done.disabled = !canControl
    done.onclick = _ => controls.submitCommand(
      WalkerPanelSupport.resolveChooseOneCommand(surface.decision, query.done))
    panel.appendChild(done)

  def cardCount(count: Int): String =
    if count == 1 then "1 card" else s"$count cards"
```

In `ParkedDecision.scala`:

- In `enum Surface`, after `Selection`, add:

  ```scala
    /** Cards to look at and a Done button (catalog batch 2, N5). */
    case Inspect(decision: WalkerDecisionState,
        query: DecisionQueryState.Inspect)
  ```

- In `render`, add an arm after `Surface.Selection`:

  ```scala
      case surface: Surface.Inspect => InspectPanel.render(
        surface, canControl, panel, controls)
  ```

- In `formSurface`, add an arm after `ChooseAmount`:

  ```scala
      case inspect: DecisionQueryState.Inspect =>
        Some(Surface.Inspect(decision, inspect))
  ```

- [ ] **Step 9: Run the frontend tests to verify they pass**

Run: `./sbtw "frontend/test"`
Expected: PASS, the shared suites included.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala \
  src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/gamelog/DetailLines.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala \
  frontend/src/main/scala/oathdigital/frontend/CardList.scala \
  frontend/src/main/scala/oathdigital/frontend/InspectPanel.scala \
  frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala \
  src/test/scala/oathdigital/gameplay/walker/InspectQuerySuite.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/InspectPanelSuite.scala
git commit -m "feat(engine): an Inspect decision that shows cards until Done"
```

Add any file the compiler made you touch in Step 4 to this list.

---

### Task 4: Scryer and Oracular Pig

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Scryer.scala`, `src/main/scala/oathdigital/gameplay/powers/action/OracularPig.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: create `src/test/scala/oathdigital/gameplay/powers/action/ScryerSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/action/OracularPigSuite.scala`

**Interfaces:**
- Consumes: Task 1's knowledge rule, Task 2's `NoteArg.Cards` rendering (through the log only; the suites read notes with `NoteText`), Task 3's `DecisionQuery.Inspect`, `DecisionQuery.Inspect.Done` and `DecisionQueryProjection.Inspect`.
- Produces: `Scryer` (`pileDecisionId`, `inspectDecisionId`, `piles: Vector[Region]`, `ref(region): DecisionOptionRef.Button`), `OracularPig` (`inspectDecisionId`, `Count = 3`).

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/action/ScryerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.application.GamePresentationProjector
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection

class ScryerSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val scryer = DenizenId("19")
  private val source = DecisionOptionRef.Denizen(scryer)
  private val presentation = new GamePresentationProjector(catalog)

  /** The actor holds one faceup secret beside a site Scryer. The Cradle
    * discard pile holds the world deck's top six cards, stored top last, and
    * the Hinterland's is empty. */
  private val staged: ReadyGame =
    inPhase(withSecrets(atHome(base, scryer), actor, 1, 0), Phase.Act)
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.drop(6),
        regionalDiscards = c.commonCards.regionalDiscards
          .updated(Region.Cradle, c.commonCards.worldDeck.take(6))
          .updated(Region.Hinterland, Vector.empty))))
  /** The Cradle discard pile, top first. */
  private val top: Vector[WorldCardId] =
    staged.game.current.commonCards.discard(Region.Cradle).reverse
  private def started = use(staged, Scryer, source).toOption.get
  private def peekAt(from: OathTransition, region: Region) =
    answer(from, actor, Scryer.pileDecisionId, pick(Scryer.ref(region)))
      .toOption.get

  test("Scryer is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(Scryer.id).isDefined)

  test("it places a secret and asks which discard pile"):
    val t = started
    assert(awaits(t, Scryer.pileDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(PaidActionHarness.tokensOn(after(t), scryer), Tokens(0, 1))
    assertEquals(offered(t, actor),
      Some(Scryer.piles.map(Scryer.ref).map(r => r.kind -> r.wireId)))

  test("the pile's cards are peeked and shown top first until Done"):
    val t = started
    val peeking = peekAt(t, Region.Cradle)
    assert(awaits(peeking, Scryer.inspectDecisionId),
      parked.parkedDecision(peeking.state).toString)
    queryOf(peeking, actor) match
      case Some(DecisionQueryProjection.Inspect(cards, done, heading)) =>
        assertEquals(cards.map(_.cardId), top.map(_.value))
        assert(cards.forall(!_.hidden), cards.toString)
        assertEquals(heading, Some("Scryer: the Cradle discard pile"))
        assertEquals(done.label, "Done")
      case other => fail(s"expected an Inspect, got $other")
    val seen = after(peeking)
    assert(top.forall(presentation.identifiesAt(seen, Some(actor), _)))
    others(seen).foreach(other =>
      assert(!top.exists(presentation.identifiesAt(seen, Some(other), _))))
    assertEquals(seen.game.current.commonCards.discard(Region.Cradle),
      staged.game.current.commonCards.discard(Region.Cradle))
    val done = answer(peeking, actor, Scryer.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val events = t.events ++ peeking.events ++ done.events
    assertEquals(replayed(staged, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("its line lists the pile top first and covers the peeks"):
    val t = started
    val peeking = peekAt(t, Region.Cradle)
    assertEquals(NoteText.said(Scryer, t.events ++ peeking.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} peeked at the Cradle discard pile: " +
          s"${top.map(_.value).mkString(", ")}.", covers = true)))

  test("an empty pile asks nothing more and says so"):
    val t = started
    val peeking = peekAt(t, Region.Hinterland)
    parked.assertResumed(peeking.state, Phase.Act, actor)
    assertEquals(NoteText.said(Scryer, t.events ++ peeking.events),
      Vector(NoteText.Said("used.empty",
        s"${actor.value} peeked at the Hinterland discard pile, " +
          "which was empty.", covers = true)))

  test("it is unusable without a faceup secret"):
    val broke = withSecrets(staged, actor, 0, 1)
    assert(!usableNow(broke).exists(_.power.id == Scryer.id))
    assert(use(broke, Scryer, source).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/powers/action/OracularPigSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.application.GamePresentationProjector
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection

class OracularPigSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val pig = RelicId("R14")
  private val source = DecisionOptionRef.Relic(pig)
  private val presentation = new GamePresentationProjector(catalog)

  /** The actor holds Oracular Pig. With `deck`, the world deck holds its
    * first `deck` cards and the rest wait in the Provinces discard pile. */
  private def staged(deck: Option[Int] = None): ReadyGame =
    val holding = inPhase(withRelic(base, pig), Phase.Act)
    deck.fold(holding)(size => holding.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.take(size),
        regionalDiscards = c.commonCards.regionalDiscards.updated(
          Region.Provinces, c.commonCards.discard(Region.Provinces) ++
            c.commonCards.worldDeck.drop(size))))))
  private def deckOf(ready: ReadyGame) = ready.game.current.commonCards.worldDeck

  private def shown(t: OathTransition): Vector[String] =
    queryOf(t, actor) match
      case Some(DecisionQueryProjection.Inspect(cards, _, heading)) =>
        assertEquals(heading, Some("Oracular Pig: the top of the world deck"))
        assert(cards.forall(!_.hidden), cards.toString)
        cards.map(_.cardId)
      case other => fail(s"expected an Inspect, got $other")

  test("Oracular Pig is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(OracularPig.id).isDefined)

  test("the top three cards are peeked and shown top first until Done"):
    val ready = staged()
    val top = deckOf(ready).take(3)
    val t = use(ready, OracularPig, source).toOption.get
    assert(awaits(t, OracularPig.inspectDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(shown(t), top.map(_.value))
    assert(top.forall(presentation.identifiesAt(after(t), Some(actor), _)))
    others(after(t)).foreach(other => assert(
      !top.exists(presentation.identifiesAt(after(t), Some(other), _))))
    assertEquals(deckOf(after(t)), deckOf(ready))
    val done = answer(t, actor, OracularPig.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line lists the top of the deck and covers the peeks"):
    val ready = staged()
    val top = deckOf(ready).take(3)
    val t = use(ready, OracularPig, source).toOption.get
    assertEquals(NoteText.said(OracularPig, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} peeked at the top of the world deck: " +
        s"${top.map(_.value).mkString(", ")}.", covers = true)))

  test("a deck of two shows both"):
    val ready = staged(deck = Some(2))
    val t = use(ready, OracularPig, source).toOption.get
    assertEquals(shown(t), deckOf(ready).map(_.value))

  test("an empty world deck asks nothing and writes no line"):
    val ready = staged(deck = Some(0))
    val t = use(ready, OracularPig, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, actor)
    assertEquals(NoteText.said(OracularPig, t.events), Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ScryerSuite oathdigital.gameplay.powers.action.OracularPigSuite"`
Expected: compilation FAILS: `Scryer` and `OracularPig` do not exist.

- [ ] **Step 3: Implement Scryer**

Create `src/main/scala/oathdigital/gameplay/powers/action/Scryer.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Scryer (denizen 19), ACTION: place 1 secret on this card, then peek at
  * any one discard pile.
  *
  * The player chooses among the three regions' piles, empty ones included:
  * the card lets any pile be chosen. Every card in the chosen pile is
  * recorded as a `Peek`, so the player may identify it, then an `Inspect`
  * decision shows the cards top first. A pile is stored top last, so the
  * order is reversed. An empty pile asks nothing more.
  *
  * The note comes between the peeks and the `Inspect`, so it posts when the
  * player looks. Its empty variant is built by the same covering `Note`; it
  * has no peek line to cover.
  */
case object Scryer extends PaidAction("denizen.scryer", Cost(secret = 1)):
  val pileDecisionId: String = "power.scryer.pile"
  val inspectDecisionId: String = "power.scryer.inspect"
  /** "{Red} peeked at the {Cradle discard pile}: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1), NotePart.Text(": "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} peeked at the {Cradle discard pile}, which was empty." */
  val peekedEmpty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1),
    NotePart.Text(", which was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked, peekedEmpty)

  /** The regions whose discard piles it may peek at, in the order offered. */
  val piles: Vector[Region] = Region.all

  def ref(region: Region): DecisionOptionRef.Button =
    DecisionOptionRef.Button(region.key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(pileDecisionId, player, DecisionQuery.ChooseOne(piles.map(region =>
      DecisionOption.Button(ref(region), name(region).capitalize)),
      heading = Some("Scryer: peek at a discard pile"))),
    BuildOps((live, pending) => Right(chosen(pending.answered).toVector
      .flatMap(region => topFirst(live, region).map(card =>
        Peek(player, card, Location.RegionalDiscard(region)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      region <- chosen(states.answered)
    yield
      val pile = NoteArg.Pile(SearchSource.RegionalDiscard(region))
      val cards = topFirst(states.now, region)
      if cards.isEmpty then peekedEmpty(card, NoteArg.Player(player), pile)
      else peeked(card, NoteArg.Player(player), pile, NoteArg.Cards(cards)),
      covers = true),
    Branch((live, pending) => chosen(pending.answered).toVector.flatMap {
      region =>
        val cards = topFirst(live, region)
        Option.when(cards.nonEmpty)(Decide(inspectDecisionId, player,
          DecisionQuery.Inspect(cards,
            heading = Some(s"Scryer: the ${name(region)}")))).toVector
    }))))

  /** "Cradle discard pile". */
  private def name(region: Region): String =
    SearchSource.name(SearchSource.RegionalDiscard(region))

  /** The pile's cards, top first. */
  private def topFirst(ready: ReadyGame, region: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(region).reverse

  private def chosen(answered: Vector[Answered]): Option[Region] =
    answered.collectFirst {
      case Answered(`pileDecisionId`, DecisionAnswer.ChooseOneAnswer(picked),
          _) => picked
    }.flatMap(picked => piles.find(ref(_) == picked))
```

- [ ] **Step 4: Implement Oracular Pig**

Create `src/main/scala/oathdigital/gameplay/powers/action/OracularPig.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Oracular Pig (relic R14), ACTION, no cost: peek at the top 3 cards of the
  * world deck.
  *
  * Each card, or every card when the deck holds fewer, is recorded as a
  * `Peek`, then an `Inspect` decision shows them top first; the world deck
  * is stored top first. An empty deck peeks at nothing, asks nothing and
  * writes no line: the spec gives it none.
  */
case object OracularPig extends PaidAction("relic.oracular-pig", Cost.free):
  val inspectDecisionId: String = "power.oracular-pig.inspect"
  val Count: Int = 3
  /** "{Red} peeked at the top of the world deck: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the top of the world deck: "), NotePart.Arg(1),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(top(live).map(card =>
      Peek(player, card, Location.Deck(CardDeck.World))))),
    Note(id, states => PowerSourceRef.of(source)
      .filter(_ => top(states.now).nonEmpty)
      .map(peeked(_, NoteArg.Player(player), NoteArg.Cards(top(states.now)))),
      covers = true),
    Branch((live, _) => Option.when(top(live).nonEmpty)(Decide(
      inspectDecisionId, player, DecisionQuery.Inspect(top(live),
        heading = Some("Oracular Pig: the top of the world deck")))).toVector))))

  /** The top of the world deck, top first. */
  private def top(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck.take(Count)
```

- [ ] **Step 5: Register both**

In `SelfActionPowers.scala`, make the list:

```scala
  val powers: Vector[PhasePower] = Vector(Tutor, ShiftingMap, DemonTail,
    WizardSchool, SpiritSnare, ClayRattle, Scryer, OracularPig)
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS, `PowerNoteCatalogSuite` and `BackendArchitectureSuite` included.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Scryer.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OracularPig.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ScryerSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/OracularPigSuite.scala
git commit -m "feat(powers): Scryer and Oracular Pig peek at a pile and show it"
```

---

### Task 5: The log's card list, and the card list's design

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`, `CardInspectionOverlay.scala`, `GameTableShell.scala`, `GameLogPane.scala` (all in `frontend/src/main/scala/oathdigital/frontend/`)
- Modify: `frontend/styles.css`, `DESIGN.md`
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala`

**Interfaces:**
- Consumes: Task 2's `LogSpanWire.cards` (kind `"cards"`), Task 3's `CardList.render`.
- Produces: `CardInspection.Request.Cards(title, cards, origin)`, `CardInspection.openCards(title, cards, origin)`, `CardInspectionOverlay.showCards(title, cards, origin)`; the log's `button.log-cards`.

- [ ] **Step 1: Write the failing tests**

In `GameLogPaneSuite.scala`, add at the end:

```scala

  test("a card list is a control that opens it; few cards or a count are words"):
    val cards = (1 to 6).toVector.map(n =>
      CardDetails(s"d$n", "denizen", s"Card $n"))
    val listed = entry(7, "action", 1, LogSpanWire("text", "Scryer: "),
      LogSpanWire("cards", "6 cards", cards = cards))
    val plain = entry(8, "action", 1, LogSpanWire("text", "Oracle: 6 cards"))
    val few = entry(9, "action", 1, LogSpanWire("text", "Pig: "),
      LogSpanWire("card", "Old Oak", id = Some("d1")))
    var opened = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => opened :+= request)
    try
      val content = box(0, 0, 0)
      new GameLogPane(content).show("g|red", Vector(setup, listed, plain, few),
        Map.empty)
      val shown = items(content)
      val short = shown.find(_.textContent.startsWith("Pig")).get
      assertEquals(short.querySelector(".log-card").textContent, "Old Oak")
      assertEquals(short.querySelector(".log-cards"), null)
      val link = shown.find(_.textContent.startsWith("Scryer")).get
        .querySelector("button.log-cards").asInstanceOf[dom.html.Button]
      assertEquals(link.textContent, "6 cards")
      link.click()
      assertEquals(opened.collect {
        case CardInspection.Request.Cards(title, sent, _) =>
          title -> sent.map(_.cardId)
      }, Vector("6 cards" -> cards.map(_.cardId)))
      assertEquals(shown.find(_.textContent.startsWith("Oracle")).get
        .querySelector(".log-cards"), null)
    finally CardInspection.clear()
```

In `CardInspectionOverlaySuite.scala`, add at the end:

```scala

  test("a card list shows every card in order, each its face or its back"):
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.showCards("6 cards", Vector(card, hidden), opener)
    assert(overlay.isOpen)
    val node = root.querySelector(".card-overlay")
    assertEquals(node.getAttribute("aria-label"), "6 cards")
    val faces = all(node, ".card-list-item .card-face")
    assertEquals(faces.map(_.getAttribute("data-card-id")),
      Vector("d1", "hidden"))
    assert(faces(1).classList.contains("card-face-down"))
    overlay.hide()
    assertEquals(dom.document.activeElement, opener)
    overlay.dispose(); root.remove(); opener.remove()
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite oathdigital.frontend.CardInspectionOverlaySuite"`
Expected: compilation FAILS: `CardInspection.Request.Cards` and `showCards` do not exist.

- [ ] **Step 3: Open a card list from the log**

In `CardInspection.scala`, add a request case after `Text`:

```scala
    /** Cards in order, as a log line's card list opens them (N5). */
    final case class Cards(title: String, cards: Vector[CardDetails],
        origin: dom.html.Element) extends Request
```

and a method after `openText`:

```scala

  def openCards(title: String, cards: Vector[CardDetails],
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Cards(title, cards, origin)))
```

In `CardInspectionOverlay.scala`, after `showText`, add:

```scala

  /** Cards in the order given, as a card list (catalog batch 2, N5): a log
    * line's "6 cards". Each is its face or its back as projected; a face
    * still opens that one card here.
    */
  def showCards(title: String, cards: Vector[CardDetails],
      origin: dom.html.Element): Unit =
    clear()
    node.setAttribute("aria-label", title)
    body.appendChild(CardList.render(title, cards))
    open(origin)
```

In `GameTableShell.scala`, add an arm to the `CardInspection.onOpen` match:

```scala
    case CardInspection.Request.Cards(title, cards, origin) =>
      inspector.showCards(title, cards, origin)
```

In `GameLogPane.spanNode`, add an arm before `case _`:

```scala
    // More cards than a line names: a control that opens them as a card
    // list (catalog batch 2, N5). A span with no cards stays words.
    case "cards" if span.cards.nonEmpty =>
      val open = button(span.text, "log-cards")
      open.onclick = _ => CardInspection.openCards(span.text, span.cards, open)
      open
```

`GameLogPane.scala` already imports `ServerUiSupport._`, which supplies `button`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "frontend/test"`
Expected: PASS.

- [ ] **Step 5: Design the card list with Impeccable**

Load the `impeccable` skill and follow it for this step. The DOM contract above is fixed by the tests; the skill decides how it looks. Style, in `frontend/styles.css`:

- `.card-list`, `.card-list-title`, `.card-list-grid` and `.card-list-item`: a grid in pile order that holds dozens of cards and scrolls inside its container. Size the cards through the container's `font-size` (DESIGN.md, "The Font-Size Handle Rule"), and keep card text at or above the Eleven-Pixel Floor.
- The Inspect panel's list inside the action pane, with a bounded height so Done stays reachable without scrolling the pane.
- The card list inside `.card-overlay-body`, where it takes the overlay's width rather than the single card's 1.9em face.
- `.log-cards`: a control that reads as part of the log sentence and is visibly clickable, with the same focus ring as the log's other controls.
- The card overlay stacks above the log overlay, so "6 cards" clicked in the full-window log opens above it.

Follow DESIGN.md's Don'ts: no `transition` or `animation`, no new shadow or lift, and the existing overlay scrim. Record the card list in DESIGN.md beside the card overlay and log overlay entries: what it is, where it appears (the Inspect panel, the card overlay from a log line) and its sizing rule. Keep the design record in normal prose.

If the Browser preview can reach a game, open the Inspect panel and a log card list at desktop width and at 375px, and check both scroll without horizontal page scroll. Say in your report whether you could check it.

- [ ] **Step 6: Run the frontend tests again**

Run: `./sbtw "frontend/test"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/CardInspection.scala \
  frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala \
  frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala \
  frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala \
  frontend/styles.css DESIGN.md \
  frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala
git commit -m "feat(frontend): the card list, from the Inspect panel and the log"
```

---

### Task 6: Pin the cards, record the slice, run the gates

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`, `docs/ROADMAP.md`

- [ ] **Step 1: Pin the two cards**

In `PowerImplementationStatusSuite.scala`, after the test "catalog batch 2's first actions on yourself are implemented", add:

```scala

  test("catalog batch 2's peeking actions are implemented"):
    Vector("denizen.scryer", "relic.oracular-pig")
      .foreach(id => assert(implemented(PowerId(id)), id))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Record the slice**

In `docs/ROADMAP.md`, "Phase - Catalog batch 2", replace:

```markdown
Clay Rattle, with the `Shuffle` operation. Slice 3b (Scryer, Oracular Pig
and the card-list view) and 3c (Oracle and drawing a Vision) remain, then
slice 4, actions on others, and slice 5, triggers and when-played powers.
```

with:

```markdown
Clay Rattle, with the `Shuffle` operation. Slice 3b is done: Scryer and
Oracular Pig, with the `Inspect` decision and the card list. Slice 3c
(Oracle and drawing a Vision) remains, then slice 4, actions on others, and
slice 5, triggers and when-played powers.
```

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every suite passes.
- Server: the baseline plus 23 (2 in Task 1, 3 in Task 2, 6 in Task 3, 11 in Task 4, 1 in Task 6).
- Frontend: the baseline plus 7 (the 2 shared suite tests from Tasks 2 and 3, 3 more in Task 3, 2 in Task 5).

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 3b"
```
