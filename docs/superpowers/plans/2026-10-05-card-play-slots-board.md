# Card Play Through Card Slots: Board Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player plays a card by clicking where it goes on the table: the empty site slot, an adviser slot, the revealed Vision spot or the discard pile lights up, a card the play would discard carries a "Replace" badge, an adviser slot asks for a facing in the Action panel, and "Play facedown adviser" picks its card on the board.

**Architecture:** Plan 1 already parks card play as one board-presented choose-one over slot refs; today those options have no home on the table, so the client draws them as panel rows. This plan gives each slot ref a board home (`BoardHomes`, the renderers' keys), keys the table's option views by home so that two options can share one element, adds an open-home state to `BoardSelectionDraft` with a variant toggle in `BoardDecisionPanel`, and moves the facedown-adviser pick onto the board through the same `BoardOptions`. The projection gains one field, an option's `variant` ("Faceup", "Facedown"), and a card play's Replace options are reworded as the board's summary line.

**Tech Stack:** Scala 3, Scala.js with jsdom render suites, ujson with hand-written `exact` codecs, munit. Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-04-card-play-slots-design.md`. Read it whole before Task 1, then read plan 1 (`docs/superpowers/plans/2026-10-04-card-play-slots-engine.md`) for the refs and labels it built. This plan is the spec's plan 2 (Board).

## Global Constraints

- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only, on port 8081, started through Bash, never through `preview_start`.
- Work in a git worktree: from the main checkout run `git worktree add -b card-play-slots-board .claude/worktrees/card-play-slots-board main`, then `ln -s /Users/roman/projects/oathdigital/.tooling .claude/worktrees/card-play-slots-board/.tooling`, then `EnterWorktree`. Inside the worktree run `git merge --ff-only main` first. Before merging from the main checkout, `ExitWorktree(keep)`.
- Inside the worktree keep Bash plain: no `cd` chained with git, no heredocs. Write any helper script into the session scratchpad and run it with `python3`. Use `sed -i ''` on macOS.
- Other sessions commit to `main` while you work, and HEAD can move. Check `git log -1` before any amend or merge. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` unless the committing model is another one, in which case use that model's own line.
- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build, and so does a non-exhaustive match.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`.
- Codec rule: every new projection field is written always and decoded with a default, so a payload from before this plan still decodes; every `exact` field set gains the new name.
- Visibility rule: the one function `GamePresentationProjector.identifiesCard` decides whether a viewer sees a card's face. No other test of knowledge.
- Names rule (`model/Decisions.scala` header): a game object's name never enters a query, an answer or a validator. The projector resolves names from refs.
- Copy rule: player text is plain sentences, no em dashes, numbers as digits.
- The players pane must never scroll (`PlayerBoardSuite`'s header). A change that adds a line to every player board needs a reason.
- Gates, in order, before the final commit of each task: the task's own `testOnly` runs, then `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. The last task also runs `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

### Decisions this plan makes that the spec left open or worded differently

1. **The toggle's words are a `variant` field.** `DecisionOptionProjection.variant: Option[String]` is the word that tells an option from the others on its home: "Faceup" or "Facedown" on an adviser play, `None` elsewhere. The toggle shows `variant`, or the label when there is none. The label stays the whole summary ("Adviser slot 3, faceup"), so the summary line and the toggle need no client parsing.
2. **The projector writes the Replace summary.** The spec's "Discard Tavern Songs; play Wayward Spirit here" names the played card, which only the decision id knows. `WalkerDecisionProjector` rewords a `cardplay.place.*` decision's Replace options to that sentence for a viewer who may identify the played card, with ", faceup" or ", facedown" after an adviser play. Other viewers and other decisions keep plan 1's "Replace {card} at {site}".
3. **An option's home is a key, and the table's views are keyed by home.** `BoardOptionViews.home(option)` is the key of the element that draws it: an adviser play's home is its adviser slot (`adviser-slot:{owner}:{index}`); every other option's home is its own key. `BoardOptions` marks `data-option-ref` with the home, so hover highlights the board element and the panel row together.
4. **Only a choose-one shares homes.** Two options on one home open the home on a click; a choose-many has no such options today and keeps its toggling.
5. **The pending prompt is generic.** An open home with no pick reads "Choose one: Faceup or Facedown." in the summary and under Confirm. The words come from the variants.
6. **The empty Vision spot is drawn only while it is lit.** The spec draws the spot empty on every board, which would add a row to every player board and break the never-scroll rule. The spot is drawn when there is a revealed Vision or when the parked decision offers the spot. `BoardHomes` still treats every player board as the spot's home, since the renderer draws it whenever an option names it.
7. **Empty adviser placeholders sit in the card row.** They come after the advisers and before the relics, up to `adviserLimit`, for every player. They are card-width boxes in a row that already wraps, so they add no line.
8. **A discard pile option keeps its card list behind an "i".** When the pile is an option, a click on the pile picks it and the card list opens from an inspect button beside it, as a card option's face does. An empty pile is enabled while it is an option.
9. **The Replace badge is an element, not a pseudo-element.** `BoardOptions` appends a `span.board-option-badge` reading "Replace" to a marked element whose option `replaces` a card, so the word is in the accessible name as well as on screen. It sits in the bottom-left corner: the top-left holds the pick mark, the top-right the pip and dice, the bottom-right the inspect button.
10. **The facedown-adviser pick uses `BoardOptions` with no parked decision.** `BoardOptions.facedown(draft, ...)` lights each facedown adviser by its card key and picks it through `ActionControls.chooseFacedownAdviser`. The panel keeps the heading, shows "Play {card}" once a card is picked, and keeps "Cancel action"; the cards are no longer drawn in the panel.

---

### Task 1: Option variant and the Replace summary

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:170-176` (`DecisionOptionProjection`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:349-371` (`encodeOptionRow`, `decodeOptionRow`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`optionProjection`'s `AdviserPlay` case near line 390, `headed` near line 487)
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Test: `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala`

**Interfaces:**
- Consumes: plan 1's `DecisionOptionProjection.replaces: Option[CardDetailsProjection]`, `WalkerDecisionProjector.optionProjection`, `subjectCards`.
- Produces: `DecisionOptionProjection.variant: Option[String] = None` (last field); frontend alias `DecisionOptionState` gets it for free. `WalkerDecisionProjector.replacing(query: DecisionQueryProjection, played: CardDetailsProjection): DecisionQueryProjection`, `private[application]`.

- [ ] **Step 1: Write the failing tests**

Append to `ProjectionProtocolSuite` (inside the class, at the end):

```scala
  test("an option's variant round-trips, and a payload without it decodes " +
      "to none"):
    val faceup = DecisionOptionProjection("adviser-play", "red:2:faceup",
      "Adviser slot 3, faceup", variant = Some("Faceup"))
    def parked(option: DecisionOptionProjection) = projection.copy(
      walkerDecision = Some(WalkerDecisionProjection("search",
        "cardplay.place.denizen.known", "decide", query = Some(
          DecisionQueryProjection.ChooseOne(Vector(option))))))
    val written = parked(faceup)
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(
      written)), Right(written))
    def strip(value: ujson.Value): Unit = value match
      case obj: ujson.Obj =>
        obj.value.remove("variant")
        obj.value.values.foreach(strip)
      case arr: ujson.Arr => arr.value.foreach(strip)
      case _ => ()
    val json = ujson.read(GameProjectionCodec.encode(written))
    strip(json)
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(parked(faceup.copy(variant = None))))
```

Add to `SlotOptionRowsSuite`. Extend its protocol import to `import oathdigital.protocol.projection.{CardDetailsProjection, DecisionOptionProjection, DecisionQueryProjection}`, then append:

```scala
  test("an adviser play carries its facing as the word its toggle shows"):
    assertEquals(Vector(true, false).map(faceup => rowOf(
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(p1, 1,
        faceup))).variant), Vector(Some("Faceup"), Some("Facedown")))
    assertEquals(rowOf(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 1))).variant, None)

  test("a card play's Replace options read as the board's summary, and " +
      "every other option keeps its label"):
    val played = CardDetailsProjection("denizen:spirit", "denizen",
      "Wayward Spirit")
    val site = rowOf(DecisionOption.Replacing(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 0)), wrestlers))
    val adviser = rowOf(DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar))
    val empty = rowOf(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(projector.replacing(DecisionQueryProjection.ChooseOne(
      Vector(site, adviser, empty)), played),
      DecisionQueryProjection.ChooseOne(Vector(
        site.copy(label = "Discard Wrestlers; play Wayward Spirit here"),
        adviser.copy(label =
          "Discard Threatening Roar; play Wayward Spirit here, facedown"),
        empty)))

  test("a Replace option whose card the viewer may not identify keeps " +
      "its label"):
    val played = CardDetailsProjection("denizen:spirit", "denizen",
      "Wayward Spirit")
    val unseen = rowOf(DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar),
      viewer = p2)
    val query = DecisionQueryProjection.ChooseOne(Vector(unseen))
    assertEquals(projector.replacing(query, played), query)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.SlotOptionRowsSuite"`
Expected: compile errors, "value variant is not a member" and "value replacing is not a member".

- [ ] **Step 3: Add the field and its codec**

In `ActionProjectionDtos.scala`, give `DecisionOptionProjection` a last field and document it in the case class's doc comment (add the sentence beside the comment's existing field notes):

```scala
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty,
    badge: Option[String] = None,
    state: String = "available",
    reason: Option[String] = None,
    replaces: Option[CardDetailsProjection] = None,
    variant: Option[String] = None)
```

Doc sentence: "`variant` is the word that tells this option from the others drawn on the same board home, such as an adviser play's facing; the client's toggle shows it."

In `ActionProjectionCodec.scala`:

```scala
  private def encodeOptionRow(row: DecisionOptionProjection): ujson.Value =
    ujson.Obj("kind" -> row.kind, "id" -> row.id, "label" -> row.label,
      "card" -> option(row.card)(encodeCard),
      "details" -> encoded(row.details)(ujson.Str(_)),
      "badge" -> stringOption(row.badge), "state" -> row.state,
      "reason" -> stringOption(row.reason),
      "replaces" -> option(row.replaces)(encodeCard),
      "variant" -> stringOption(row.variant))

  private[projection] def decodeOptionRow(raw: ujson.Value, child: String)
      : Result[DecisionOptionProjection] = for
    row <- obj(raw, child)
    _ <- exact(row, Set("kind", "id", "label", "card", "details", "badge",
      "state", "reason", "replaces", "variant"), child)
    kind <- string(row, "kind", child); id <- string(row, "id", child)
    label <- string(row, "label", child)
    card <- optionalAbsent(row, "card", child)(decodeCard)
    details <- stringsOrEmpty(row, "details", child)
    badge <- optionalString(row, "badge", child)
    state <- default(row, "state", child, "available")(string)
    reason <- optionalAbsent(row, "reason", child)(string)
    replaces <- optionalAbsent(row, "replaces", child)(decodeCard)
    variant <- optionalAbsent(row, "variant", child)(string)
  yield DecisionOptionProjection(kind, id, label, card, details, badge, state,
    reason, replaces, variant)
```

- [ ] **Step 4: Set the variant and reword Replace in the projector**

In `WalkerDecisionProjector.optionProjection`, the `AdviserPlay` case becomes:

```scala
      case DecisionOption.AdviserPlay(play) =>
        if ready.game.current.players.exists(_.player == play.owner) then
          row(s"Adviser slot ${play.index + 1}, ${facing(play.faceup)}")
            .map(_.copy(variant = Some(facing(play.faceup).capitalize)))
        else None
```

Add beside `headed`:

```scala
  /** A card play's Replace options as the board's summary reads them:
    * "Discard {replaced}; play {played} here", with the facing after an
    * adviser play. A replaced card this viewer may not identify keeps the
    * option's own label, as does every option that replaces nothing.
    */
  private[application] def replacing(query: DecisionQueryProjection,
      played: CardDetailsProjection): DecisionQueryProjection =
    def worded(option: DecisionOptionProjection) = option.replaces
      .filterNot(_.hidden).fold(option)(replaced => option.copy(label =
        s"Discard ${replaced.name}; play ${played.name} here" +
          option.variant.fold("")(word => s", ${word.toLowerCase}")))
    query match
      case one: DecisionQueryProjection.ChooseOne =>
        one.copy(options = one.options.map(worded))
      case other => other
```

In `headed`, reword before the heading is set. Replace its first line of body (`val heading =`) block's preamble so the method reads:

```scala
  private def headed(ready: ReadyGame, viewer: Option[PlayerId],
      decisionId: String, projected: DecisionQueryProjection)
      : DecisionQueryProjection =
    val played =
      if decisionId.startsWith("cardplay.place.") then
        subjectCards(ready, viewer, decisionId).headOption.filterNot(_.hidden)
      else None
    val query = played.fold(projected)(replacing(projected, _))
    val heading =
      if decisionId.startsWith("cardplay.place.") then
        played.map(card => s"Play ${card.name}")
      else if decisionId.startsWith("cardplay.discard.") then
        viewer.flatMap(player => ready.game.current.players
          .find(_.player == player)).flatMap(_.pawnSite).map(site =>
            s"Discard more cards from ${presentation.siteLabel(site)}")
      else None
    heading.fold(query)(text => query match
      case one: DecisionQueryProjection.ChooseOne =>
        one.copy(heading = Some(text))
      case many: DecisionQueryProjection.ChooseMany =>
        many.copy(heading = Some(text))
      case other => other)
```

Update `headed`'s doc comment: "A card play's heading names its card or its site, and its Replace options name the card they discard and the card played ([[replacing]]); a game object's name is resolved here. Every other decision keeps its own."

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.SlotOptionRowsSuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS.

- [ ] **Step 6: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS. If a golden or projector suite compares a Replace option's label from a `cardplay.place.*` park, update its expectation to the new sentence.

- [ ] **Step 7: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala
git commit -m "feat(cardplay): project an adviser play's facing as its variant, and word a Replace option as the board's summary"
```

---

### Task 2: Board homes for card slots, and the Replace badge

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala` (`home`, `of`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala` (`control`, `card`, `mark`, `cardKind`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:47-78` (`siteDetails`), `:142-147` (`emptySlot`), `:158-185` (`pileDisplay`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:78-108` (player board slots and cards), `:180-182` (discard pile)
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala` (`row`'s `data-option-ref`)
- Modify: `frontend/styles.css` (after `.board-option-inspect` near line 297, and after `.player-slot-vision` near line 821)
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala`

**Interfaces:**
- Consumes: wire kinds and ids from plan 1: `site-slot` `{site}:{index}`, `adviser-play` `{owner}:{index}:{faceup|facedown}`, `revealed-vision` `{owner}`, `discard-pile` `{region}`; `PlayerBoard.adviserLimit`; `DecisionOptionState.replaces`.
- Produces:
  - `BoardHomes.adviserSlot(id: String): String`, the slot part of an adviser play's id.
  - `BoardOptionViews.home(option: DecisionOptionState): String`.
  - `BoardOptionViews.of` returns views keyed by home.
  - `BoardOptions.control(face: dom.html.Button, keys: Vector[String], inspect: Option[(String, dom.html.Element => Unit)]): dom.Element`.
  - `BoardOptions.cardKind(card: CardDetails): String`.
  - `ServerUiSupport.emptySlot(cardKind: String, label: String = ...)`.
  - `ServerUiSupport.openablePile(label, count, topCardKind, title, cards): dom.html.Button`.

- [ ] **Step 1: Write the failing tests**

In `BoardHomesSuite`, add `adviserLimit = 3` is the default, so the existing "absent things" assertion `!has("adviser-slot", "red:2")` now names a placeholder: change it to `assert(!has("adviser-slot", "red:3"))`. Then append:

```scala
  test("card slots have homes: site slots below capacity, adviser plays " +
      "up to the limit, the Vision spot and the discard piles"):
    assert(has("site-slot", "woods:0"))
    assert(has("site-slot", "woods:2"))
    assert(!has("site-slot", "woods:3"))
    assert(!has("site-slot", "nowhere:0"))
    assert(has("adviser-play", "red:2:faceup"))
    assert(has("adviser-play", "red:0:facedown"))
    assert(!has("adviser-play", "red:3:faceup"))
    assert(!has("adviser-play", "blue:0:faceup"))
    assert(has("revealed-vision", "red"))
    assert(!has("revealed-vision", "blue"))
    assert(has("discard-pile", "cradle"))
    assert(!has("discard-pile", "hinterland"))

  test("a lowered adviser limit draws one slot fewer"):
    val lowered = table.copy(playerBoards = Vector(board.copy(
      advisers = Vector.empty, adviserLimit = 2)))
    assert(BoardHomes.has(lowered, DecisionOptionState("adviser-play",
      "red:1:faceup", "")))
    assert(!BoardHomes.has(lowered, DecisionOptionState("adviser-play",
      "red:2:faceup", "")))
```

In `BoardOptionsRenderSuite`, key `optionsFor` by home: change `BoardOptionViews.key(view.option) -> view` to `BoardOptionViews.home(view.option) -> view`. Then append:

```scala
  test("an empty site slot is marked by its index, and a site card " +
      "answers to its slot"):
    val empty = option("site-slot", "woods:1")
    val (options, picks) = optionsFor(available(empty))
    val root = world(options)
    val slot = one(root, """[data-option-ref="site-slot:woods:1"]""")
    assertEquals(slot.getAttribute("data-slot-index"), "1")
    assertEquals(all(root, ".site-cards .card-slot-empty")
      .map(_.getAttribute("data-slot-index")), Vector("1", "2"))
    click(slot)
    assertEquals(picks.toVector, Vector(empty))
    val replace = option("site-slot", "woods:0").copy(replaces = Some(fox))
    val (byCard, _) = optionsFor(available(replace))
    assertEquals(one(world(byCard), """[data-option-ref="site-slot:woods:0"]""")
      .getAttribute("data-card-id"), "fox")

  test("a Replace option carries the badge, and an empty slot does not"):
    val replace = option("site-slot", "woods:0").copy(replaces = Some(fox))
    val (options, _) = optionsFor(available(replace),
      available(option("site-slot", "woods:1")))
    val root = world(options)
    assertEquals(all(root, ".board-option-badge").map(badge =>
      (badge.textContent, badge.parentElement.getAttribute("data-option-ref"))),
      Vector(("Replace", "site-slot:woods:0")))
    assert(one(root, """[data-option-ref="site-slot:woods:0"]""")
      .classList.contains("board-option-replace"))

  test("an adviser play is drawn on its slot: a placeholder or the card " +
      "there"):
    val placeholder = option("adviser-play", "red:2:facedown")
    val (options, picks) = optionsFor(available(placeholder))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    val slot = one(boards, """[data-option-ref="adviser-slot:red:2"]""")
    assert(slot.classList.contains("card-slot-empty"))
    assertEquals(slot.getAttribute("aria-label"), "Empty adviser slot")
    click(slot)
    assertEquals(picks.toVector, Vector(placeholder))
    val (onCard, _) = optionsFor(available(option("adviser-play",
      "red:0:faceup").copy(replaces = Some(oak))))
    assertEquals(one(WorldBoardRenderer.playerBoards(table, "red", onCard),
      """[data-option-ref="adviser-slot:red:0"]""").textContent
      .contains("Replace"), true)

  test("the revealed Vision spot is drawn empty only while it is an option"):
    assertEquals(all(WorldBoardRenderer.playerBoards(table, "red",
      BoardOptions.none), ".player-slot-vision"), Vector.empty)
    val spot = option("revealed-vision", "red")
    val (options, picks) = optionsFor(available(spot))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    val empty = one(boards, ".player-slot-vision-empty")
    assertEquals(empty.getAttribute("data-option-ref"), "revealed-vision:red")
    click(empty)
    assertEquals(picks.toVector, Vector(spot))

  test("a discard pile option picks on a click, and its cards open from " +
      "the inspect button"):
    val pile = option("discard-pile", "cradle")
    val (options, picks) = optionsFor(available(pile))
    val root = world(options)
    val face = one(root, """[data-option-ref="discard-pile:cradle"]""")
    assert(!face.asInstanceOf[dom.html.Button].disabled)
    click(face)
    assertEquals(picks.toVector, Vector(pile))
    assertEquals(all(root, ".region .board-option-inspect"), Vector.empty)
```

(`table`'s Cradle discard is empty, so the pile has no inspect button and is enabled only because it is an option.)

In `DiscardPileSuite`, append:

```scala
  test("a pile that is an option opens its cards from the inspect button " +
      "only"):
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Cards(title, _, _) => opened :+= title
      case _ => ()
    val pile = DecisionOptionState("discard-pile", "cradle",
      "Discard to the Cradle discard")
    var picked = Vector.empty[DecisionOptionState]
    val options = new BoardOptions(Map(BoardOptionViews.home(pile) ->
      OptionView(pile, OptionState.Available, None)),
      chosen => { picked :+= chosen; () })
    val root = WorldBoardRenderer.world(projection(Vector(top, known)),
      options, SessionDrafts.empty, new RecordingControls())
    discard(root).click()
    assertEquals((picked, opened), (Vector(pile), Vector.empty))
    val inspect = all(root, ".region .board-option-inspect").headOption
      .getOrElse(fail("no inspect button"))
    assertEquals(inspect.getAttribute("aria-label"),
      "Inspect Cradle discard pile")
    inspect.asInstanceOf[dom.html.Button].click()
    assertEquals(opened, Vector("Cradle discard pile (2)"))
```

In `PlayerBoardSuite`, the test "advisers and relics share one unlabelled row; a revealed vision does not" now also draws two placeholders between the adviser and the relic (one adviser, limit 3). Change its class expectation to:

```scala
    assertEquals(all(node, ".board-cards .card-face").map(_.getAttribute("class")),
      Vector("card-face card-face-denizen",
        "card-face card-face-denizen card-slot-empty",
        "card-face card-face-denizen card-slot-empty",
        "card-face card-face-relic"))
```

and append:

```scala
  test("empty adviser slots are drawn up to the player's limit"):
    def placeholders(limit: Int) = all(render(board.copy(adviserLimit = limit),
      supplyMaximum = 7), ".board-cards .card-slot-empty")
      .map(_.getAttribute("data-slot-index"))
    assertEquals(placeholders(3), Vector("1", "2"))
    assertEquals(placeholders(2), Vector("1"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardHomesSuite oathdigital.frontend.BoardOptionsRenderSuite oathdigital.frontend.PlayerBoardSuite oathdigital.frontend.DiscardPileSuite"`
Expected: compile error "value home is not a member of object BoardOptionViews".

- [ ] **Step 3: Homes in `BoardHomes`**

Replace the `adviser-slot` case and add the new kinds before `case _ => false`:

```scala
      case "adviser-slot" | "adviser-play" =>
        val id = if option.kind == "adviser-play" then adviserSlot(option.id)
          else option.id
        slot(id).exists((player, at) => value.playerBoards
          .exists(board => board.playerId == player &&
            at < math.max(board.advisers.size, board.adviserLimit)))
      case "site-slot" =>
        slot(option.id).exists((site, at) => sites(value)
          .exists(found => found.siteId == site && at < found.denizenCapacity))
      case "revealed-vision" => value.playerBoards.exists(_.playerId == option.id)
      case "discard-pile" => value.world.exists(_.regionId == option.id)
```

Add, after `slot`:

```scala
  /** An adviser play's slot: its id without the facing after the last
    * colon. */
  def adviserSlot(id: String): String = id.take(math.max(0, id.lastIndexOf(':')))
```

Update the object's doc comment list: "a site's box, a card or an empty slot in a site's row or on a player board, a revealed Vision spot, a discard pile, a banner, a favor bank, or a player's name and pawns".

- [ ] **Step 4: Views keyed by home in `BoardOptionViews`**

Add after `key(option)`:

```scala
  /** Where the table draws `option`, as the key the renderers hand
    * `BoardOptions`: an adviser play is drawn on its adviser slot, whichever
    * facing it names, and every other option on its own key.
    */
  def home(option: DecisionOptionState): String = option.kind match
    case "adviser-play" => key("adviser-slot", BoardHomes.adviserSlot(option.id))
    case _ => key(option)
```

In `of`, key the result by home: change `.map(found => key(found.option) -> found).toMap` to `.map(found => home(found.option) -> found).toMap`, and say so in its doc comment ("Each option by its home").

- [ ] **Step 5: `control`, the badge and `cardKind` in `BoardOptions`**

Change the import to `import ServerUiSupport.{button, element, text}`. Replace `card` with:

```scala
  /** A control that does something of its own when clicked (a card face
    * opens the inspector, a pile lists its cards), marked when it is an
    * option. An option's body picks it, so the control's own action moves
    * to an inspect button beside it, named "Inspect {name}"; the two share
    * one box, since a button may not hold a button. `inspect` is `None`
    * when there is nothing to read. A marked control is enabled, since an
    * empty pile is still a place to play to. Returns what to append.
    */
  def control(face: dom.html.Button, keys: Vector[String],
      inspect: Option[(String, dom.html.Element => Unit)]): dom.Element =
    view(keys) match
      case None => face
      case Some(found) =>
        face.onclick = null
        face.disabled = false
        mark(face, found)
        inspect.fold[dom.Element](face) { (name, open) =>
          val box = element("span", "board-option-card")
          box.appendChild(face)
          val reader = button("i", "board-option-inspect")
          reader.setAttribute("aria-label", s"Inspect $name")
          reader.addEventListener("click", (event: dom.Event) => {
            event.stopPropagation()
            open(reader)
          })
          box.appendChild(reader)
          box
        }

  /** A card face, marked when it is an option. A card the viewer may not
    * identify has nothing to read and gets no inspect button.
    */
  def card(face: dom.html.Button, details: CardDetails,
      keys: Vector[String]): dom.Element =
    control(face, keys, Option.unless(details.hidden)(
      details.name -> (CardInspection.open(details, _))))
```

In `mark`, mark by home and add the badge. Replace `val key = BoardOptionViews.key(found.option)` with `val key = BoardOptionViews.home(found.option)`, and after the `classes.foreach` line add:

```scala
    if found.option.replaces.nonEmpty then
      target.classList.add("board-option-replace")
      target.appendChild(text("span", "board-option-badge", "Replace"))
```

In the companion, replace `cardKey` with:

```scala
  /** A card's kind as an option names it. */
  def cardKind(card: CardDetails): String = card.cardKind match
    case "relic" | "vision" | "edifice" => card.cardKind
    case _ => "denizen"

  /** A card's own option key: its kind as an option names it, and its id. */
  def cardKey(card: CardDetails): String =
    BoardOptionViews.key(cardKind(card), card.cardId)
```

- [ ] **Step 6: Site slots, empty slots and piles in `ServerUiSupport`**

In `siteDetails`, number the site's cards and its empty slots:

```scala
    site.denizens.zipWithIndex.foreach { (denizen, at) =>
      denizen.details match
        case Some(details) =>
          val face = CardFace.render(details)
          face.setAttribute("data-denizen-id", denizen.denizenId)
          cards.appendChild(options.card(face, details,
            Vector(BoardOptions.cardKey(details), siteSlot(site, at))))
        case None =>
          val back = facedownCard("denizen")
          back.setAttribute("data-denizen-id", denizen.denizenId)
          cards.appendChild(back)
    }
    (site.denizens.size until site.denizenCapacity).foreach(at =>
      cards.appendChild(openSlot(options, "denizen", "Empty denizen slot", at,
        siteSlot(site, at))))
```

Add beside `emptySlot`:

```scala
  /** The key of slot `at` in `site`'s card row. The row's order is the
    * slot order, so a card's index is its slot. */
  private def siteSlot(site: GameSite, at: Int): String =
    BoardOptionViews.key("site-slot", s"${site.siteId}:$at")

  /** Empty slot `at`, which a card play may fill: it carries its index and
    * is marked when an option names `key`. */
  private[frontend] def openSlot(options: BoardOptions, cardKind: String,
      label: String, at: Int, key: String): dom.Element =
    val node = emptySlot(cardKind, label)
    node.setAttribute("data-slot-index", at.toString)
    options.decorate(node, Vector(key))
    node
```

Give `emptySlot` a label: `private[frontend] def emptySlot(cardKind: String, label: String = ""): dom.Element` with `node.setAttribute("aria-label", if label.isEmpty then s"Empty $cardKind slot" else label)`.

Split `pileDisplay` so a listable pile is typed as a button:

```scala
  private[frontend] def pileDisplay(
      label: String,
      count: Int,
      topCardKind: Option[String],
      opens: Option[(String, Vector[CardDetails])] = None
  ): dom.Element = opens match
    case Some((title, cards)) =>
      openablePile(label, count, topCardKind, title, cards)
    case None =>
      val pile = element("div", "pile-display")
      fillPile(pile, label, count, topCardKind)
      pile

  /** A pile whose cards a player may list: a button that opens them top
    * first in the card overlay, disabled while the pile is empty. */
  private[frontend] def openablePile(label: String, count: Int,
      topCardKind: Option[String], title: String,
      cards: Vector[CardDetails]): dom.html.Button =
    val node = button("", "pile-display pile-open")
    node.setAttribute("aria-label",
      s"$title, $count ${if count == 1 then "card" else "cards"}")
    node.disabled = count == 0
    node.onclick = _ => CardInspection.openCards(s"$title ($count)", cards, node)
    fillPile(node, label, count, topCardKind)
    node

  /** The pile's label, one back for the top card, and the count. */
  private def fillPile(pile: dom.Element, label: String, count: Int,
      topCardKind: Option[String]): Unit =
```

`fillPile`'s body is the old `pileDisplay` body from `pile.appendChild(text("span", "pile-label", s"$label:"))` through `pile.appendChild(text("span", "pile-count", s"x$count"))`. Keep `pileDisplay`'s doc comment on `pileDisplay`.

- [ ] **Step 7: Placeholders, the Vision spot and the discard pile in `WorldBoardRenderer`**

Replace the `board.revealedVision.foreach { ... }` block and the `if slots.childNodes.length > 0` line with:

```scala
     // The spot is drawn empty only while the decision lights it: the strip
     // never scrolls, so an empty spot may not spend a row on every board.
     val visionKey = Vector(BoardOptionViews.key("revealed-vision",
       player.playerId))
     board.revealedVision.map { card =>
       val pill = button(card.name, "player-slot-vision")
       pill.onclick = _ => CardInspection.open(card, pill)
       options.control(pill, visionKey,
         Some(card.name -> (CardInspection.open(card, _))))
     }.orElse(options.view(visionKey).map { _ =>
       val spot = button("Vision", "player-slot-vision player-slot-vision-empty")
       spot.setAttribute("aria-label", "Empty revealed Vision spot")
       options.control(spot, visionKey, None)
     }).foreach(slots.appendChild)
     if slots.childNodes.length > 0 then section.appendChild(slots)
```

After the advisers' `zipWithIndex.foreach` and before the relics', draw the placeholders:

```scala
     (board.advisers.size until board.adviserLimit).foreach(at =>
       cards.appendChild(openSlot(options, "denizen", "Empty adviser slot",
         at, BoardOptionViews.key("adviser-slot", s"${player.playerId}:$at"))))
```

Replace the region's discard pile with:

```scala
     val discardTitle = s"$name discard pile"
     section.appendChild(options.control(openablePile("Discard",
       region.discardCount, region.discardTopCardKind, discardTitle,
       region.discardCards),
       Vector(BoardOptionViews.key("discard-pile", region.regionId)),
       Option.when(region.discardCount > 0)(discardTitle -> (anchor =>
         CardInspection.openCards(s"$discardTitle (${region.discardCount})",
           region.discardCards, anchor)))))
```

- [ ] **Step 8: Panel rows hover with their home**

In `BoardDecisionPanel.row`, set `data-option-ref` and `hoverSync` from `BoardOptionViews.home(option)`; keep `BoardOptionViews.key` for matching tally values:

```scala
    val key = BoardOptionViews.key(option)
    val home = BoardOptionViews.home(option)
    ...
    item.setAttribute("data-option-ref", home)
    ...
    BoardOptions.hoverSync(item, home)
```

- [ ] **Step 9: Styles**

After the `.board-option-inspect:hover` rule in `frontend/styles.css`:

```css
/* A Replace option: the play discards this card. Bottom-left, the one
   corner free: the pick mark holds the top-left, the pip and dice the
   top-right, the inspect button the bottom-right. */
.board-option-badge { position: absolute; left: 3px; bottom: 3px; z-index: 1;
  padding: 1px 5px; border-radius: 3px; background: #d2a85e;
  color: #17140f; font-size: 10px; font-weight: 800; line-height: 1.3; }
```

After the `.player-slot-vision` rule:

```css
/* The pill is too short for a corner badge, so the word follows the name. */
.player-slot-vision > .board-option-badge { position: static;
  margin-left: 0.4em; }
.player-slot-vision-empty { border-style: dashed; color: #b7a98f; }
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardHomesSuite oathdigital.frontend.BoardOptionsRenderSuite oathdigital.frontend.PlayerBoardSuite oathdigital.frontend.DiscardPileSuite"`
Expected: PASS.

- [ ] **Step 11: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS. A suite that counted `.card-face` or `.card-slot-empty` on a player board now sees the placeholders; update its expectation, not the renderer.

- [ ] **Step 12: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala
git commit -m "feat(board): draw card slots, the Vision spot and the discard piles as board options, with a Replace badge"
```

---

### Task 3: Several options on one home, and the facing toggle

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala` (`of`, `atHome`, `variant`, `variantPrompt`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardSelectionDraft.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala`
- Modify: `frontend/styles.css` (after `.board-decision-row.board-option-hover` near line 602)
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardSelectionDraftSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala`

**Interfaces:**
- Consumes: Task 1's `DecisionOptionState.variant`; Task 2's `BoardOptionViews.home`.
- Produces:
  - `BoardSelectionDraft.home: Option[String] = None` (fifth field).
  - `BoardSelectionDraft.choose(option): BoardSelectionDraft`.
  - `BoardSelectionDraft.homeOptions: Vector[DecisionOptionState]`.
  - `BoardSelectionDraft.blocker` covers an open home.
  - `BoardOptionViews.atHome(query, option)`, `variant(option)`, `variantPrompt(options)`.

- [ ] **Step 1: Write the failing tests**

Append to `BoardSelectionDraftSuite`:

```scala
  private def play(facing: String) = DecisionOptionState("adviser-play",
    s"red:2:$facing", s"Adviser slot 3, $facing",
    variant = Some(facing.capitalize))
  private val faceup = play("faceup")
  private val facedown = play("facedown")
  private val placement = DecisionQueryState.ChooseOne(
    Vector(site("a"), faceup, facedown), Some("Play Wayward Spirit"))

  test("a click on a home two options share opens it with no pick, and " +
      "Confirm waits for one"):
    val opened = draft(placement).click(faceup)
    assertEquals((opened.home, opened.keys),
      (Some("adviser-slot:red:2"), Vector.empty))
    assertEquals(opened.homeOptions, Vector(faceup, facedown))
    assertEquals(opened.blocker, Some("Choose one: Faceup or Facedown."))
    assertEquals(opened.command, None)
    val chosen = opened.choose(facedown)
    assertEquals(chosen.keys, Vector("adviser-play:red:2:facedown"))
    assertEquals(chosen.command, Some(Intent.ResolveWalker("campaign.targets",
      DecisionAnswerWire.ChooseOneWire("adviser-play", "red:2:facedown"))))

  test("a home with one available option picks it at once, and a second " +
      "click closes the home"):
    val refused = faceup.copy(state = "unavailable",
      reason = Some("Refused by a test power"))
    val query = placement.copy(options = Vector(site("a"), refused, facedown))
    val opened = draft(query).click(facedown)
    assertEquals((opened.home, opened.keys),
      (Some("adviser-slot:red:2"), Vector("adviser-play:red:2:facedown")))
    assertEquals(opened.choose(refused).keys, opened.keys)
    val closed = opened.click(facedown)
    assertEquals((closed.home, closed.keys), (None, Vector.empty))

  test("a click on another home closes the open one"):
    val moved = draft(placement).click(faceup).choose(faceup).click(site("a"))
    assertEquals((moved.home, moved.keys), (None, Vector("site:a")))

  test("Clear closes the open home"):
    val cleared = draft(placement).click(faceup).choose(faceup).cleared
    assertEquals((cleared.home, cleared.keys), (None, Vector.empty))

  test("the table draws a shared home once: available, then selected " +
      "while open"):
    val closed = BoardOptionViews.of(decision(placement), placement,
      Some(draft(placement)))
    assertEquals(closed.get("adviser-slot:red:2").map(_.state),
      Some(OptionState.Available))
    assertEquals(closed.contains("adviser-play:red:2:faceup"), false)
    val open = BoardOptionViews.of(decision(placement), placement,
      Some(draft(placement).click(faceup)))
    assertEquals(open.get("adviser-slot:red:2").map(_.state),
      Some(OptionState.Selected))

  test("a shared home with no available option is unavailable with the " +
      "first reason"):
    val query = placement.copy(options = Vector(
      faceup.copy(state = "unavailable", reason = Some("First reason")),
      facedown.copy(state = "unavailable", reason = Some("Second reason"))))
    assertEquals(BoardOptionViews.of(decision(query), query, None)
      .get("adviser-slot:red:2").map(view => (view.state, view.reason)),
      Some((OptionState.Unavailable, Some("First reason"))))
```

In `BoardDecisionPanelSuite`, give `table` a board for red so the adviser plays have a home: add `.copy(playerBoards = Vector(PlayerBoard("red", warbands = 0, favor = 0, faceUpSecrets = 0, faceDownSecrets = 0, committedSecrets = 0, totalSecrets = 0, supply = 0, pawnSiteId = None, advisers = Vector.empty, relics = Vector.empty, revealedVision = None)))` to its definition. Then append:

```scala
  private def play(facing: String) = DecisionOptionState("adviser-play",
    s"red:2:$facing", s"Adviser slot 3, $facing",
    variant = Some(facing.capitalize))
  private val placement = DecisionQueryState.ChooseOne(
    Vector(play("faceup"), play("facedown")), Some("Play Wayward Spirit"))
  private val placing = WalkerDecisionState("search",
    "cardplay.place.denizen.spirit", "decide", query = Some(placement),
    presentation = "board")
  private def open(query: DecisionQueryState.ChooseOne, keys: String*) =
    Some(BoardSelectionDraft(context, "cardplay.place.denizen.spirit", query,
      keys.toVector, Some("adviser-slot:red:2")))
  private def variants(panel: dom.Element): Vector[dom.html.Button] =
    all(panel, ".board-decision-variant").map(as[dom.html.Button](_))

  test("an open home shows its options as a toggle with no default, and " +
      "Confirm waits for one"):
    val ui = new RecordingControls()
    val panel = pane(ui, placement, placing, open(placement))
    assertEquals(variants(panel).map(_.textContent), Vector("Faceup", "Facedown"))
    assertEquals(variants(panel).map(_.getAttribute("aria-pressed")),
      Vector("false", "false"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Choose one: Faceup or Facedown.")
    assert(as[dom.html.Button](one(panel, ".board-decision-confirm")).disabled)
    variants(panel)(1).click()
    assertEquals(ui.drafts.board.map(_.keys),
      Some(Vector("adviser-play:red:2:facedown")))

  test("a picked facing is pressed, the summary is its label, and Confirm " +
      "submits it"):
    val ui = new RecordingControls()
    val panel = pane(ui, placement, placing,
      open(placement, "adviser-play:red:2:faceup"))
    assertEquals(variants(panel).map(_.getAttribute("aria-pressed")),
      Vector("true", "false"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Adviser slot 3, faceup")
    as[dom.html.Button](one(panel, ".board-decision-confirm")).click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "cardplay.place.denizen.spirit",
      DecisionAnswerWire.ChooseOneWire("adviser-play", "red:2:faceup"))))

  test("an unavailable facing is disabled and says why"):
    val refused = DecisionQueryState.ChooseOne(Vector(
      play("faceup").copy(state = "unavailable",
        reason = Some("Refused by a test power")), play("facedown")),
      Some("Play Wayward Spirit"))
    val panel = pane(new RecordingControls(), refused,
      placing.copy(query = Some(refused)),
      open(refused, "adviser-play:red:2:facedown"))
    assertEquals(variants(panel).map(_.disabled), Vector(true, false))
    assertEquals(all(panel, ".board-decision-variant-reason")
      .map(_.textContent), Vector("Refused by a test power"))

  test("a Replace pick's summary is the sentence the projector wrote"):
    val replace = DecisionOptionState("site-slot", "plains:0",
      "Discard Tavern Songs; play Wayward Spirit here",
      replaces = Some(CardDetails("songs", "denizen", "Tavern Songs")))
    val query = DecisionQueryState.ChooseOne(Vector(replace),
      Some("Play Wayward Spirit"))
    val panel = pane(new RecordingControls(), query,
      placing.copy(query = Some(query)), Some(BoardSelectionDraft(context,
        "cardplay.place.denizen.spirit", query, Vector("site-slot:plains:0"))))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Discard Tavern Songs; play Wayward Spirit here")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardSelectionDraftSuite oathdigital.frontend.BoardDecisionPanelSuite"`
Expected: compile errors, "value home is not a member of BoardSelectionDraft" and "value choose is not a member".

- [ ] **Step 3: Shared homes in `BoardOptionViews`**

Add after `home`:

```scala
  /** The options of `query` drawn on `option`'s home, `option` included, in
    * the query's order. */
  def atHome(query: DecisionQueryState,
      option: DecisionOptionState): Vector[DecisionOptionState] =
    options(query).filter(home(_) == home(option))

  /** An option's word among the options on its home: its variant, or its
    * label when it has none. */
  def variant(option: DecisionOptionState): String =
    option.variant.getOrElse(option.label)

  /** What an open home asks before Confirm, from its options' words. */
  def variantPrompt(options: Vector[DecisionOptionState]): String =
    s"Choose one: ${options.map(variant).mkString(" or ")}."
```

Replace `of` with:

```scala
  /** Each home's view: locked and unavailable as projected, then the draft's
    * picks, then what the draft's limits block. A choose-one's answers so
    * far (a battle-plan window's played plans) show locked. A home that
    * several options share is one view ([[shared]]).
    */
  def of(decision: WalkerDecisionState, query: DecisionQueryState,
      draft: Option[BoardSelectionDraft]): Map[String, OptionView] =
    val current = draft.filter(_.decisionId == decision.decisionId)
    val picks = current.fold(Vector.empty[String])(_.keys)
    val played = query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
        .map(option => OptionView(option, OptionState.Locked, None))
      case _ => Vector.empty
    val offered = options(query).map(view(query, picks, _))
      .groupBy(found => home(found.option)).map((at, views) =>
        at -> (if views.size == 1 then views.head
          else shared(views, current.flatMap(_.home).contains(at))))
    played.map(found => home(found.option) -> found).toMap ++ offered

  /** The one view of a home that several options share. It carries the
    * first available option, so a click opens the home
    * ([[BoardSelectionDraft.click]]). Selected while the draft has the home
    * open or holds one of its options; available while any option is;
    * otherwise unavailable with the first reason.
    */
  private def shared(views: Vector[OptionView], open: Boolean): OptionView =
    val lead = views.find(_.state == OptionState.Available)
      .getOrElse(views.head)
    if open || views.exists(_.state == OptionState.Selected) then
      lead.copy(state = OptionState.Selected, reason = None)
    else if lead.state == OptionState.Available then lead
    else views.head.copy(state = OptionState.Unavailable,
      reason = views.flatMap(_.reason).headOption)
```

- [ ] **Step 4: The open home in `BoardSelectionDraft`**

```scala
/** What a viewer has picked for a parked board decision and not yet
  * confirmed: option keys ([[BoardOptionViews.key]]) in the order picked,
  * and the home the viewer opened when several options share it (an
  * adviser slot's faceup and facedown plays). Like the other walker drafts
  * it knows only the projected query, so its answer names only projected
  * options. A choose-one holds at most one key.
  */
private[frontend] final case class BoardSelectionDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, keys: Vector[String],
    home: Option[String] = None):

  /** After a click on `option`. On a choose-one, a home that several
    * options share opens ([[open]]). Otherwise a picked option is dropped, a
    * choose-one replaces its pick and closes any open home, and a
    * choose-many adds it. An option that may not be chosen, or that the
    * limits block, changes nothing.
    */
  def click(option: DecisionOptionState): BoardSelectionDraft =
    val key = BoardOptionViews.key(option)
    val siblings = BoardOptionViews.atHome(query, option)
    query match
      case _: DecisionQueryState.ChooseOne if siblings.size > 1 =>
        open(BoardOptionViews.home(option), siblings)
      case _ =>
        if keys.contains(key) then
          copy(keys = keys.filterNot(_ == key), home = None)
        else if option.state != "available" ||
            BoardOptionViews.blockedBy(query, keys, option).nonEmpty then this
        else query match
          case _: DecisionQueryState.ChooseOne =>
            copy(keys = Vector(key), home = None)
          case _ => copy(keys = keys :+ key)

  /** A click on a shared home: an open home closes; a closed one opens,
    * with its one available option picked or with no pick when there are
    * more. A home with nothing available stays closed.
    */
  private def open(at: String,
      siblings: Vector[DecisionOptionState]): BoardSelectionDraft =
    if home.contains(at) then copy(keys = Vector.empty, home = None)
    else siblings.filter(_.state == "available") match
      case Vector() => this
      case Vector(only) =>
        copy(keys = Vector(BoardOptionViews.key(only)), home = Some(at))
      case _ => copy(keys = Vector.empty, home = Some(at))

  /** The pick among the open home's options, from the panel's toggle. An
    * option of another home, or one that may not be chosen, changes nothing.
    */
  def choose(option: DecisionOptionState): BoardSelectionDraft =
    if option.state == "available" &&
        home.contains(BoardOptionViews.home(option)) then
      copy(keys = Vector(BoardOptionViews.key(option)))
    else this

  /** The open home's options, in the query's order. */
  def homeOptions: Vector[DecisionOptionState] = home.toVector.flatMap(at =>
    BoardOptionViews.options(query).filter(BoardOptionViews.home(_) == at))

  def cleared: BoardSelectionDraft = copy(keys = Vector.empty, home = None)
```

and replace `blocker`:

```scala
  def blocker: Option[String] =
    if home.nonEmpty && keys.isEmpty then
      Some(BoardOptionViews.variantPrompt(homeOptions))
    else BoardOptionViews.blocker(query, keys)
```

`picked`, `command`, `reconcile` and the companion's `click` stay as they are.

- [ ] **Step 5: The toggle and the summary in `BoardDecisionPanel`**

Add to the object's doc comment, after "the options with no place on the table": "the open home's toggle".

Move the `current` and `blocked` values up to just after `val stage = ...`, with `blocked` reading the draft:

```scala
    // An optional choose-many confirms the empty selection, so Confirm needs
    // a draft even before the first pick.
    val current = draft.orElse(drafts.context.map(context =>
      BoardSelectionDraft(context, decision.decisionId, query, Vector.empty)))
    val blocked = current.fold(BoardOptionViews.blocker(query, picks))(
      _.blocker)
```

Delete the old `current` and `blocked` definitions near Confirm. Replace the summary's choose-one branch:

```scala
      case _ => draft.flatMap(open => open.picked.headOption.map(label)
        .orElse(Option.when(open.home.nonEmpty)(
          BoardOptionViews.variantPrompt(open.homeOptions))))
        .getOrElse("Choose on the board.")))
```

After the rows block and before the tallies, draw the toggle:

```scala
    // A home several options share asks which one in the panel: the
    // table draws the home once, and its options differ by one word.
    draft.filter(_.home.nonEmpty).foreach { open =>
      val toggle = element("div", "board-decision-variants")
      toggle.setAttribute("role", "group")
      open.homeOptions.foreach { option =>
        val choice = button(BoardOptionViews.variant(option),
          "board-decision-variant")
        choice.setAttribute("aria-pressed",
          open.keys.contains(BoardOptionViews.key(option)).toString)
        choice.disabled = !canControl || option.state != "available"
        option.reason.foreach(choice.setAttribute("title", _))
        choice.onclick = _ => controls.stage(Draft.Board(Some(
          open.choose(option))))
        toggle.appendChild(choice)
        option.reason.foreach(reason => toggle.appendChild(
          text("span", "board-decision-variant-reason", reason)))
      }
      panel.appendChild(toggle)
    }
```

- [ ] **Step 6: Styles**

After `.board-decision-row.board-option-hover`:

```css
/* An open home's options: one word each, pressed when picked. */
.board-decision-variants { display: flex; flex-wrap: wrap;
  align-items: baseline; gap: 6px; margin: 6px 0; }
.board-decision-variant[aria-pressed="true"] { border-color: #8ecbff;
  background: #243442; }
.board-decision-variant-reason { font-size: 0.85em; color: #b7a98f; }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardSelectionDraftSuite oathdigital.frontend.BoardDecisionPanelSuite oathdigital.frontend.BoardOptionsRenderSuite"`
Expected: PASS.

- [ ] **Step 8: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala frontend/src/main/scala/oathdigital/frontend/BoardSelectionDraft.scala frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/BoardSelectionDraftSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala
git commit -m "feat(board): open a home that several options share, and pick among them with a toggle in the Action panel"
```

---

### Task 4: Play a facedown adviser by picking it on the board

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala` (companion: `facedown`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/FacedownAdviserRenderer.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:94-100`
- Modify: `frontend/src/test/scala/oathdigital/frontend/RecordingControls.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/CardChoicePanelSuite.scala` (remove the facedown-adviser tests)
- Create: `frontend/src/test/scala/oathdigital/frontend/FacedownAdviserPickSuite.scala`

**Interfaces:**
- Consumes: `FacedownAdviserDraft` (`advisers`, `selectedCardId`, `selected`, `command`), `ActionControls.chooseFacedownAdviser`, `submitTargetCommand`, `cancelTargetAction`; Task 2's `BoardOptions.cardKind` and `cardKey`.
- Produces: `BoardOptions.facedown(draft: FacedownAdviserDraft, canControl: Boolean, controls: ActionControls): BoardOptions`. `RecordingControls` records `chosenFacedown: Vector[String]`, `targetCommands: Vector[Intent.StartWalker]` and `cancelledTargets: Int`.

- [ ] **Step 1: Record the facedown controls in `RecordingControls`**

```scala
  var chosenFacedown: Vector[String] = Vector.empty
  var targetCommands: Vector[Intent.StartWalker] = Vector.empty
  var cancelledTargets: Int = 0
  ...
  def chooseFacedownAdviser(cardId: String): Unit = chosenFacedown :+= cardId
  ...
  def cancelTargetAction(): Unit = cancelledTargets += 1
  def submitTargetCommand(command: Intent.StartWalker): Unit =
    targetCommands :+= command
```

Update its doc comment: "`stage`, `submitCommand`, `chooseFacedownAdviser`, `cancelTargetAction` and `submitTargetCommand` record; every other method is a no-op stub."

- [ ] **Step 2: Write the failing tests**

Create `FacedownAdviserPickSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one}
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent, WalkerStartArgWire}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** "Play facedown adviser" lights the player's facedown advisers on their
  * board before the walker starts; a click picks one, and the panel plays
  * it by name or cancels.
  */
class FacedownAdviserPickSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private def facedown(id: String, name: String) = CardDetails(id, "denizen",
    name, suit = Some("order"), orientation = Some("face-down"))
  private val oak = facedown("a1", "Old Oak")
  private val bandits = facedown("a2", "Bandits")
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val table = TestProjections.actingProjection(players = Vector(red))
    .copy(playerBoards = Vector(PlayerBoard("red", warbands = 3, favor = 0,
      faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
      totalSecrets = 1, supply = 7, pawnSiteId = None,
      advisers = Vector(oak, bandits), relics = Vector.empty,
      revealedVision = None)))
  private val draft = FacedownAdviserDraft(context,
    Vector(MinorAdviser(oak), MinorAdviser(bandits)), None)

  private def boards(value: FacedownAdviserDraft,
      ui: RecordingControls): dom.Element =
    WorldBoardRenderer.playerBoards(table, "red",
      BoardOptions.facedown(value, canControl = true, ui))

  private def panel(value: FacedownAdviserDraft,
      ui: RecordingControls): dom.Element =
    FacedownAdviserRenderer.render(value, canControl = true, ui)

  test("the player's facedown advisers are lit on their board, and a " +
      "click picks one"):
    val ui = new RecordingControls()
    val root = boards(draft, ui)
    assertEquals(all(root, ".board-cards .board-option")
      .map(_.getAttribute("data-option-ref")),
      Vector("denizen:a1", "denizen:a2"))
    click(one(root, """[data-option-ref="denizen:a2"]"""))
    assertEquals(ui.chosenFacedown, Vector("a2"))

  test("the picked adviser is selected, and the panel plays it by name"):
    val ui = new RecordingControls()
    val picked = draft.choose("a2")
    assert(one(boards(picked, ui), """[data-option-ref="denizen:a2"]""")
      .classList.contains("board-option-selected"))
    val start = as[dom.html.Button](one(panel(picked, ui),
      ".facedown-adviser-start"))
    assertEquals(start.textContent, "Play Bandits")
    start.click()
    assertEquals(ui.targetCommands, Vector(Intent.StartWalker(
      "play-facedown-adviser", Vector.empty,
      Vector(WalkerStartArgWire("denizen", "a2")))))

  test("with no pick the panel asks for one on the board, and Cancel " +
      "leaves"):
    val ui = new RecordingControls()
    val shown = panel(draft, ui)
    assertEquals(all(shown, ".facedown-adviser-start"), Vector.empty)
    assertEquals(one(shown, ".decision-instruction").textContent,
      "Choose a facedown adviser on your board.")
    assertEquals(all(shown, ".card-face"), Vector.empty)
    as[dom.html.Button](one(shown, ".modifier-cancel")).click()
    assertEquals(ui.cancelledTargets, 1)

  test("a lone facedown adviser is picked already"):
    val minor = MinorActionsState(Vector(MinorAdviser(oak)),
      canPeekSiteRelics = false, facedownRelics = Vector.empty, siteId = None,
      maxBoardToSite = 0, maxSiteToBoard = 0)
    assertEquals(FacedownAdviserDraft.initial(context, minor)
      .flatMap(_.selectedCardId), Some("a1"))
```

In `CardChoicePanelSuite`, delete the `adviser` helper, `draft`, `advisers` and the three tests that use them ("every facedown adviser on offer is drawn face-up", "choosing is a button of its own, and says which card it picks", "a lone adviser is shown without a picker"), and change the class doc comment's first sentence to "Recover's relic panel asks a player to pick one card out of several."

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.FacedownAdviserPickSuite oathdigital.frontend.CardChoicePanelSuite"`
Expected: compile error "value facedown is not a member of object BoardOptions".

- [ ] **Step 4: `BoardOptions.facedown`**

In the `BoardOptions` companion:

```scala
  /** The facedown advisers "Play facedown adviser" may play, lit on the
    * player's board before the walker starts. Each answers to its own card
    * key, the picked one shows selected, and a click picks it. The pick is
    * a local board choice, not a parked decision, so nothing is submitted
    * until the panel's "Play {card}".
    */
  def facedown(draft: FacedownAdviserDraft, canControl: Boolean,
      controls: ActionControls): BoardOptions =
    new BoardOptions(draft.advisers.map { adviser =>
      val card = adviser.card
      val state = if draft.selectedCardId.contains(card.cardId) then
        OptionState.Selected else OptionState.Available
      cardKey(card) -> OptionView(DecisionOptionState(cardKind(card),
        card.cardId, card.name, card = Some(card)), state, None)
    }.toMap, option => if canControl then
      controls.chooseFacedownAdviser(option.id))
```

- [ ] **Step 5: The panel names the pick**

Replace `FacedownAdviserRenderer` with:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** The Action panel while a player picks which facedown adviser to play.
  * The advisers are lit on the player's board (`BoardOptions.facedown`);
  * the panel plays the pick by name, or cancels.
  */
private[frontend] object FacedownAdviserRenderer:
  def render(draft: FacedownAdviserDraft, canControl: Boolean,
      controls: ActionControls): dom.Element =
    val panel = element("section", "facedown-adviser-draft")
    panel.appendChild(text("h2", "", "Play facedown adviser"))
    draft.selected match
      case Some(adviser) =>
        val start = button(s"Play ${adviser.card.name}",
          "facedown-adviser-start")
        start.disabled = !canControl
        start.onclick = _ => draft.command.foreach(controls.submitTargetCommand)
        panel.appendChild(start)
      case None => panel.appendChild(text("p", "decision-instruction",
        "Choose a facedown adviser on your board."))
    val cancel = button("Cancel action", "modifier-cancel")
    cancel.onclick = _ => controls.cancelTargetAction()
    panel.appendChild(cancel)
    panel
```

- [ ] **Step 6: Light the advisers from `TableScreen`**

```scala
        val options = routed.surface.collect {
          case board: ParkedDecision.Surface.BoardDecision => BoardOptions.of(
            board, drafts, view.controlsAvailable, controls)
        }.orElse(drafts.facedownAdviser.map(BoardOptions.facedown(_,
          view.controlsAvailable, controls)))
          .getOrElse(BoardOptions.none)
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.FacedownAdviserPickSuite oathdigital.frontend.CardChoicePanelSuite oathdigital.frontend.ModifierFlowSuite oathdigital.frontend.SessionDraftsSuite"`
Expected: PASS.

- [ ] **Step 8: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS. If the facedown CSS rules `.card-choices`/`.card-choice` have no other user (`grep -rn "card-choice" frontend/src/main`), leave them: Recover's relic panel uses them.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala frontend/src/main/scala/oathdigital/frontend/FacedownAdviserRenderer.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/test/scala/oathdigital/frontend/RecordingControls.scala frontend/src/test/scala/oathdigital/frontend/CardChoicePanelSuite.scala frontend/src/test/scala/oathdigital/frontend/FacedownAdviserPickSuite.scala
git commit -m "feat(board): pick the facedown adviser to play on the board, and play it by name from the panel"
```

---

### Task 5: Roadmap, browser check and final gates

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Mark the phase and the deferred item done**

The roadmap deletes done work. In `docs/ROADMAP.md`:

1. Cut the **Limit cards that count or ignore advisers** item (from `- [ ] **Limit cards that count or ignore advisers.**` through its last line, `` `CardSlots.adviserSlots` and `AdviserLimit` share. ``).
2. Delete the whole `### Phase - Card play UI` section: from its heading through the line before `### Phase - Board decision follow-ups`.
3. In `### Powers-related deferred items`, replace the **walker-native card play through card slots** item (from its `- [ ]` line through "the board and marks this done.") with the cut **Limit cards** item.

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS. Then `grep -n "Card play UI" docs/ROADMAP.md` prints nothing.

- [ ] **Step 2: Play card slots on the board in the browser**

From the worktree root, with `S` the session scratchpad plus `/db-card-play-board`:

```bash
mkdir -p $S && cp var/oathdigital.script var/oathdigital.properties var/oathdigital.log $S/ && cp -r var/oathdigital.lobs $S/
```

```bash
./sbtw "frontend/fastLinkJS" "runMain oathdigital.server.OathServer --port 8081 --database-path $S/oathdigital"
```

Run the second command through Bash with `run_in_background` and wait for "Start completed" in its log. Open `http://127.0.0.1:8081/?mode=server` in the built-in browser at 1440×900, press "New game", and play Setup through to the first Act with the default choices. Then, with `read_page` for each check, a `wait` of 1 second before each screenshot, and one screenshot per check:

1. Search the world deck and keep one card. On the table, the empty slot at the pawn's site, the first empty adviser slot and the next region's discard pile are lit; the Action panel shows the card, the heading "Play {card}" and "Choose on the board.", and no slot rows.
2. Click the lit adviser slot. The panel shows "Faceup" and "Facedown" with neither pressed, the summary "Choose one: Faceup or Facedown." and Confirm disabled. (A Vision has only "Facedown", already pressed; a site-only card lights no adviser slot.)
3. Press "Facedown", then Confirm. The card joins the player's advisers facedown.
4. In a later Act, press "Play facedown adviser". The facedown adviser is lit on the player's board and the panel shows "Play {card}" (one adviser is picked already) and "Cancel action". Press Cancel, press "Play facedown adviser" again, then "Play {card}". The placement offers that adviser's own slot faceup.
5. Search again and click the lit empty site slot, then Confirm: the card joins the site's row.
6. Search again and click the lit discard pile, then Confirm: the card goes to that discard.
7. When the game reaches one, also check: a full adviser area or a full site shows "Replace" badges and the summary "Discard {card}; play {card} here"; a Vision over a revealed Vision lights the Vision spot with "Replace". When none comes up, record that these stayed covered by unit tests only.
8. `read_console_messages` with `onlyErrors` shows nothing new.

Fix every failure in one batch, reload, confirm once, and stop the server with `lsof -ti :8081 | xargs kill`. Reset the window with `resize_window` preset `desktop`.

- [ ] **Step 3: Run the final gates**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs(roadmap): card play through card slots is done"
```

Then finish the branch with superpowers:finishing-a-development-branch: `ExitWorktree(keep)`, check `git log -1` on `main`, and merge `card-play-slots-board` into local `main` with `--ff-only` after `git merge main` in the worktree if `main` moved.
