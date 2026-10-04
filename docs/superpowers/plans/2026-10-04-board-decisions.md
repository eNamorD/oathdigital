# Board Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player answers a decision about something on the table by clicking that thing on the table, sees why an option is not on offer, and confirms from a panel that states the running selection.

**Architecture:** The engine's decision vocabulary gains an unavailable option with a reason, and a choose-many gains locked options, tallies and a summary line; `Decide` gains a presentation flag. The projector carries all of it to the client, wording a power's reason with the Game Log's own templates. The frontend routes every board-presented choose-one or choose-many with an option on the table to a new board surface: a pure model (`BoardOptionViews`, `BoardSelectionDraft`, `BoardHomes`) computes each option's state, a DOM layer (`BoardOptions`) marks the table's elements, and `BoardDecisionPanel` draws the prompt, the summary, the rows, the totals and Confirm. The walker's two pruning paths then keep a pruned option as unavailable when a power's note says why, and the Campaign and Bandit Prince builders emit locked targets, tallies and their own reasons.

**Tech Stack:** Scala 3, Scala.js with raw DOM, ujson with hand-written `exact` codecs, munit (jsdom for the frontend). Build through `./sbtw`. Impeccable (`~/.claude/skills/impeccable`) for the CSS task.

**Spec:** `docs/superpowers/specs/2026-10-03-board-decisions-design.md`. Read it whole before Task 1. This plan argues from its Rulings, Protocol, Frontend, Decisions spec 1 changes, Slices and Testing sections.

## Global Constraints

- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only, on port 8081, started through Bash, never through `preview_start`.
- Work in a git worktree: from the main checkout run `git worktree add -b board-decisions .claude/worktrees/board-decisions main`, then `ln -s /Users/roman/projects/oathdigital/.tooling .claude/worktrees/board-decisions/.tooling`, then `EnterWorktree`. Inside the worktree run `git merge --ff-only main` first. Before merging from the main checkout, `ExitWorktree(keep)`.
- Inside the worktree keep Bash plain: no `cd` chained with git, no heredocs. Write any helper script into the session scratchpad and run it with `python3`. Use `sed -i ''` on macOS.
- Other sessions commit to `main` while you work, and HEAD can move. Check `git log -1` before any amend or merge. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` unless the committing model is another one, in which case use that model's own line.
- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build, and so does a non-exhaustive match.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`. The model may not import gameplay.
- Codec rule: every new projection field is written always and decoded with a default, so a payload from before this plan still decodes; every `exact` field set gains the new name.
- Visibility rule: the one function `GamePresentationProjector.identifiesCard` decides whether a viewer sees a card's face. No other test of knowledge.
- Copy rule: player text is plain sentences, no em dashes, numbers as digits.
- UI work follows Impeccable. Run `~/.claude/skills/impeccable/scripts/impeccable context` once per session with cwd at the worktree root. Read `~/.claude/skills/impeccable/reference/craft-floor.md` immediately before any CSS edit. DESIGN.md forbids `transition`, `animation` and resting shadows other than the inset rings `.board-target` already uses. Functional text holds the 11px floor in the panes.
- Timing checks never join `sbt test`.
- Gates, in order, before the final commit of each task: the task's own `testOnly` runs, then for the last task `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open or worded differently

1. **A reason is typed, not a string.** `PowerNote` is a typed record that the Game Log words per viewer, so `DecisionOption.Unavailable` carries an `OptionReason`: `Written(text)` from a builder, or `Noted(power, note)` from a restriction. The projector words a `Noted` reason with the Game Log's templates through a new `NoteReasons`, which gives "{source}: {sentence}", the power's name first, as the spec asks.
2. **`summaryNoun` is `summary: DecisionSummary(verb, one, many)`.** "Target 2 sites" needs a verb as well as a noun. Conquest is `("Target", "site", "sites")`, Raid `("Raid", "target", "targets")`, Bandit Prince `("Replace bandits at", "site", "sites")`.
3. **A tally has a `unit`.** A row reads "2 defense · 2 warbands" and a limit reads "Needs 5 warbands; limit 4", and neither can be built from the total's label alone.
4. **The projection's `presentation` decodes to `"panel"` when absent.** A payload from before this plan was answered in the panel. The projector always writes the engine's value, whose default is `Board`, so every live decision is board-presented unless its builder says `Panel`. No builder says `Panel` in this plan.
5. **Both pruning paths keep explained options.** The spec names `narrow`; an `OptionRestriction` hides options in the window fold before the search runs, and its note answers the same player question, so `restrictOptions` wraps too.
6. **A plan's cost reason reads "Needs 1 favor; you have 0".** "Costs" would misstate a surcharge, which adds to the cost without being printed on the plan.
7. **Encirclement's force rule is an offer field.** `CampaignPlanOffer.withheld: Option[String]` lets a plan say why it stands but cannot be used; a bandit defender never applies a withheld plan.
8. **A player option marks the name chip in the board header, not the whole header.** The header holds the Supply button, and a control may not sit inside another control.
9. **An option card sits in a wrapper beside its inspect button.** A card face is a `<button>`, and a button may not hold a button, so `span.board-option-card` holds both and the inspect button is placed over the card's bottom-right corner.
10. **Deck options stay in the panel.** No builder emits a deck option, so the board home table's `deck` row is not built.
11. **The check and lock badges sit inside the top-left corner.** Card faces and site boxes clip their overflow, so the mock-up's outside badge would be cut off.
12. **No board decision draws Cancel.** No choose-one or choose-many has a way back today, and spec 1 adds none.
13. **The title's battle plan stays a button.** It has no card on the table, so it is a Decide button and submits at once, as today.
14. **An unavailable option the viewer may not identify is dropped, not fatal.** An available option that cannot be presented still suppresses the whole decision; an unavailable one only drops itself.
15. **Raid reasons cover banners and other players' faceup relics.** A defender's facedown relics are not shown: no rule makes them targets, so there is nothing to explain.

### File structure

Engine and protocol:

- `src/main/scala/oathdigital/model/Decisions.scala` — `OptionReason`, `DecisionOption.Unavailable` and `available`, `ChooseMany` fields, `DecisionTally`, `DecisionSummary`, `DecisionPresentation`.
- `src/main/scala/oathdigital/model/CoreOperations.scala` — `Decide.presentation`.
- `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` — counts available options; checks locked options and tallies.
- `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala` — `choosable`, `narrowed` with `explain`, `explained`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` — `restrictOptions` explains; `lookAheadNoted`.
- `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` — `narrowAt` returns the explained decision and its notes.
- `src/main/scala/oathdigital/application/gamelog/PowerLines.scala` — `spoken`, extracted from `line`.
- `src/main/scala/oathdigital/application/gamelog/NoteReasons.scala` (new) — a note as one line of plain text.
- `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala` — new patterns.
- `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` — option state and reason, locked options, tallies, summary, presentation.
- `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` and `ActionProjectionCodec.scala` — the new fields and DTOs.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignTargets.scala` (new) — locked target, tallies, summary and withheld targets.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala` — `defenseOf`, `forceOf`, `targetRefs`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala` — `targetsStep` uses `CampaignTargets`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala` and `PlanPrice.scala` — unavailable plans with reasons.
- `src/main/scala/oathdigital/model/CampaignTypes.scala` — `CampaignPlanOffer.withheld`.
- `src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala` — withheld when the force is not larger.
- `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala` — tally, summary and site reasons.

Frontend:

- `frontend/src/main/scala/oathdigital/frontend/frontend.scala` — aliases for the new DTOs.
- `frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala` (new) — `OptionState`, `OptionView`, each option's state, limits and Confirm's blocker.
- `frontend/src/main/scala/oathdigital/frontend/BoardSelectionDraft.scala` (new) — the board draft; replaces `WalkerBoardDraft.scala`, which is deleted.
- `frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala` (new) — whether an option has a place on the table.
- `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala` (new) — marks table elements and handles clicks, the inspect button and hover sync.
- `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala` (new) — the Action panel of a board decision.
- `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala` — `Surface.BoardDecision` replaces `Surface.Board`.
- `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala`, `WorldBoardRenderer.scala`, `ServerUiSupport.scala`, `TableScreen.scala`, `WalkerPanelSupport.scala`, `WalkerSelectionPanels.scala`, `WalkerSelectionDraft.scala` — wiring and the unavailable and locked states.
- `frontend/styles.css` — option states, the inspect button, the panel.

Docs: `docs/ROADMAP.md`.

---

### Task 1: The decision vocabulary

Spec slice 1, engine half. No player-visible change: nothing emits the new shapes yet.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:606-617` (`Decide`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala:42-55`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:237-239,293-299`
- Modify: `src/test/scala/oathdigital/testkit/Situation.scala:101-117`
- Test: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala`, `src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.model`:
  - `sealed trait OptionReason`; `OptionReason.Written(text: String)`; `OptionReason.Noted(power: PowerId, note: PowerNote)`.
  - `DecisionOption.available: Boolean` (default `true`; `Priced` and `Badged` ask what they wrap).
  - `DecisionOption.Unavailable(option: DecisionOption, reason: OptionReason)`, `available == false`, `ref` is the wrapped option's.
  - `DecisionQuery.ChooseMany(min, max, options, heading = None, locked: Vector[DecisionOption] = Vector.empty, tallies: Vector[DecisionTally] = Vector.empty, summary: Option[DecisionSummary] = None)`.
  - `DecisionTally(label: String, unit: String, perOption: Map[DecisionOptionRef, Int], limit: Option[Int] = None)`.
  - `DecisionSummary(verb: String, one: String, many: String)` with `of(count: Int): String`.
  - `enum DecisionPresentation(val key: String)`: `Board` ("board"), `Panel` ("panel").
  - `Decide(..., passWhenEmpty: Boolean = false, presentation: DecisionPresentation = DecisionPresentation.Board)`.
- Produces, in `oathdigital.gameplay.walker.WalkerSearch` (`private[walker]`):
  - `choosable(decide: Decide): Vector[DecisionOptionRef]`.
  - `narrowed(decide: Decide, permitted: DecisionOptionRef => Boolean, explain: DecisionOptionRef => Option[OptionReason] = _ => None): Option[Decide]`.
  - `explained(decide: Decide, narrowed: Decide, reasons: Map[DecisionOptionRef, OptionReason]): Decide`.

- [ ] **Step 1: Write the failing decision-contract tests**

In `DecisionQuerySuite.scala`, add `DecisionTally` and `OptionReason` to the `oathdigital.model` import, then append:

```scala
  // (k) An unavailable option is shown, and never answerable.

  private val withheldStop = DecisionOption.Unavailable(stopOption,
    OptionReason.Written("Not now"))

  test("a choose-one refuses an option it shows as unavailable"):
    val query = DecisionQuery.ChooseOne(Vector(continueOption, withheldStop))
    assertEquals(wellFormed(query), Right(()))
    assertEquals(accepts(query, DecisionAnswer.ChooseOneAnswer(stopRef)),
      invalid(s"decision $decisionId does not offer the selected option"))
    assertEquals(accepts(query, DecisionAnswer.ChooseOneAnswer(continueRef)),
      Right(()))

  test("a choose-one whose every option is unavailable is malformed"):
    assertEquals(wellFormed(DecisionQuery.ChooseOne(Vector(withheldStop))),
      invalid(s"decision $decisionId declares no option that may be chosen"))

  test("a choose-many counts only the options that may be chosen"):
    assertEquals(wellFormed(DecisionQuery.ChooseMany(0, 2,
      Vector(continueOption, withheldStop))),
      invalid(s"decision $decisionId declares a maximum above its option count"))
    val fits = DecisionQuery.ChooseMany(0, 1,
      Vector(continueOption, withheldStop))
    assertEquals(wellFormed(fits), Right(()))
    assertEquals(accepts(fits, DecisionAnswer.ChooseManyAnswer(Vector(stopRef))),
      invalid(s"decision $decisionId does not offer a selected option"))

  // (l) Locked options and tallies.

  test("a locked option may not also be offered"):
    assertEquals(wellFormed(DecisionQuery.ChooseMany(0, 1,
      Vector(continueOption, stopOption), locked = Vector(stopOption))),
      invalid(s"decision $decisionId declares duplicate options"))

  test("a locked option is decided already, so no answer names it"):
    val query = DecisionQuery.ChooseMany(0, 1,
      Vector(continueOption, stopOption),
      locked = Vector(DecisionOption.Relic(relicRef)))
    assertEquals(wellFormed(query), Right(()))
    assertEquals(accepts(query, DecisionAnswer.ChooseManyAnswer(
      Vector(relicRef))),
      invalid(s"decision $decisionId does not offer a selected option"))

  test("a tally counts only offered and locked options"):
    val tally = DecisionTally("Defense", "defense", Map(relicRef -> 2))
    assertEquals(wellFormed(DecisionQuery.ChooseMany(0, 1,
      Vector(continueOption, stopOption), tallies = Vector(tally))),
      invalid(s"decision $decisionId declares a tally over an option it " +
        "does not offer"))
    assertEquals(wellFormed(DecisionQuery.ChooseMany(0, 1,
      Vector(continueOption, stopOption),
      locked = Vector(DecisionOption.Relic(relicRef)),
      tallies = Vector(tally))), Right(()))

  test("a tally is advice: an answer past its limit is still accepted"):
    val tally = DecisionTally("Warbands needed", "warbands",
      Map(continueRef -> 3, stopRef -> 3), Some(4))
    val query = DecisionQuery.ChooseMany(0, 2,
      Vector(continueOption, stopOption), tallies = Vector(tally))
    assertEquals(wellFormed(query), Right(()))
    assertEquals(accepts(query, DecisionAnswer.ChooseManyAnswer(
      Vector(continueRef, stopRef))), Right(()))
```

In `WalkerSearchSuite.scala` append:

```scala
  private val because = OptionReason.Written("Not now")

  test("an option the search drops is kept unavailable when its reason is known"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    assertEquals(WalkerSearch.narrowed(asked, _ != ref("b"),
      found => Option.when(found == ref("b"))(because)).map(_.query),
      Some(DecisionQuery.ChooseMany(1, 2, Vector(button("a"),
        DecisionOption.Unavailable(button("b"), because), button("c")), None)))

  test("an option the search drops without a reason is removed"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    assertEquals(WalkerSearch.narrowed(asked, _ != ref("b")).map(_.query),
      Some(DecisionQuery.ChooseMany(1, 2, Vector(button("a"), button("c")),
        None)))

  test("an optional choose-many left with only unavailable options is not asked"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, Vector(button("b")), None))
    assertEquals(WalkerSearch.narrowed(asked, _ => false, _ => Some(because)),
      None)

  test("an unavailable option is never tried as an answer"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      DecisionOption.Unavailable(button("b"), because))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => true)),
      Right(Some(asked)))
    assertEquals(WalkerSearch.choosable(asked), Vector(ref("a")))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: compilation fails: `Unavailable`, `OptionReason`, `DecisionTally`, `locked`, `tallies` and `choosable` are not members.

- [ ] **Step 3: Add the vocabulary to `Decisions.scala`**

In the trait `DecisionOption`, after `def ref: DecisionOptionRef`, add:

```scala
  /** Whether a player may choose the option now. Only
    * [[DecisionOption.Unavailable]] says no, and a wrapper asks what it
    * wraps.
    */
  def available: Boolean = true
```

In `Priced` and in `Badged`, after `def ref: DecisionOptionRef = option.ref`, add:

```scala
    override def available: Boolean = option.available
```

After `Badged`, add:

```scala
  /** An option shown with why it may not be chosen now. `ref` is the wrapped
    * option's, and the answer check reads only available options, so an
    * answer naming it is refused as an absent option is.
    */
  final case class Unavailable(option: DecisionOption, reason: OptionReason)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = false
```

After `OptionPrice`, add:

```scala
/** Why an option is shown but may not be chosen. It is player text, either
  * written by the builder that withheld the option, or the note of the
  * power that hid it, which the projector words as the Game Log does.
  */
sealed trait OptionReason extends Product with Serializable
object OptionReason:
  final case class Written(text: String) extends OptionReason
  final case class Noted(power: PowerId, note: PowerNote) extends OptionReason
```

After `DistributeSlot`, add:

```scala
/** A running sum a client keeps while a choose-many is answered: each
  * selected or locked option adds its value. `limit` is the most the sum may
  * reach, and a client blocks an option while choosing it would pass the
  * limit. It is advice: the answer check never reads it, so the action that
  * declares a tally still refuses an answer that breaks it.
  *
  * @param label the total's name, "Defending force".
  * @param unit what a value counts, "warbands", for a row and a limit's
  *   reason.
  */
final case class DecisionTally(label: String, unit: String,
    perOption: Map[DecisionOptionRef, Int], limit: Option[Int] = None)

/** The line a choose-many's panel states its selection with: "Target 2
  * sites" is verb "Target", one "site", many "sites".
  */
final case class DecisionSummary(verb: String, one: String, many: String):
  def of(count: Int): String =
    s"$verb $count ${if count == 1 then one else many}"

/** Where a decision is answered. `Board` puts every option with a place on
  * the table there; `Panel` keeps the whole decision in the Action panel.
  */
enum DecisionPresentation(val key: String):
  case Board extends DecisionPresentation("board")
  case Panel extends DecisionPresentation("panel")
```

Replace `ChooseMany` and its doc comment with:

```scala
  /** Pick between `min` and `max` distinct available options, inclusive.
    * Well-formed only when `0 <= min <= max <= n`, `max >= 1`, and not the
    * forced case `min == max == n`, where `n` counts the options that may be
    * chosen. `min == 0` makes the pick optional: the empty selection is an
    * answer. An exact count is `min == max`.
    *
    * `locked` options are decided already, such as a Campaign's origin: a
    * client shows them chosen, and no answer names them. `tallies` and
    * `summary` are display only, like `heading`.
    */
  final case class ChooseMany(min: Int, max: Int,
      options: Vector[DecisionOption], heading: Option[String] = None,
      locked: Vector[DecisionOption] = Vector.empty,
      tallies: Vector[DecisionTally] = Vector.empty,
      summary: Option[DecisionSummary] = None) extends DecisionQuery
```

- [ ] **Step 4: Add the flag to `Decide`**

In `CoreOperations.scala`, add to `Decide`'s doc comment, before its closing `*/`:

```scala
  *
  * `presentation` says where a client answers it: on the board, where each
  * option with a place on the table is drawn, or in the Action panel.
```

and add the parameter after `passWhenEmpty: Boolean = false`:

```scala
    passWhenEmpty: Boolean = false,
    presentation: DecisionPresentation = DecisionPresentation.Board)
```

- [ ] **Step 5: Count available options in `DecisionQueries`**

Replace the `ChooseOne` case of `wellFormed` with:

```scala
    case DecisionQuery.ChooseOne(options, _) =>
      val refs = options.map(_.ref)
      for
        _ <- require(refs.nonEmpty, decisionId, "declares no options")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(options.exists(_.available), decisionId,
          "declares no option that may be chosen")
      yield ()
```

Replace the `ChooseMany` case of `wellFormed` with:

```scala
    case many: DecisionQuery.ChooseMany =>
      val (min, max) = (many.min, many.max)
      val shown = (many.options ++ many.locked).map(_.ref)
      val choosable = many.options.count(_.available)
      for
        _ <- require(shown.distinct.size == shown.size, decisionId,
          "declares duplicate options")
        _ <- require(max >= 1, decisionId, "declares no selection to make")
        _ <- require(min >= 0 && min <= max, decisionId,
          s"declares a selection range $min..$max")
        _ <- require(max <= choosable, decisionId,
          "declares a maximum above its option count")
        _ <- require(!(min == max && max == choosable), decisionId,
          "declares a selection that already takes every option, leaving " +
            "nothing to decide")
        _ <- require(many.locked.forall(_.available), decisionId,
          "declares a locked option that may not be chosen")
        _ <- require(many.tallies.forall(_.perOption.keySet
          .subsetOf(shown.toSet)), decisionId,
          "declares a tally over an option it does not offer")
        _ <- require(many.tallies.forall(_.limit.forall(_ >= 0)), decisionId,
          "declares a negative tally limit")
      yield ()
```

In `accepts`, replace the `ChooseOne` case's check with:

```scala
        require(options.filter(_.available).map(_.ref).contains(selected),
          decisionId, "does not offer the selected option")
```

and replace the `ChooseMany` case with:

```scala
    case many: DecisionQuery.ChooseMany => answer match
      case DecisionAnswer.ChooseManyAnswer(selected) =>
        val (min, max) = (many.min, many.max)
        val offered = many.options.filter(_.available).map(_.ref)
        for
          _ <- require(selected.forall(offered.contains),
            decisionId, "does not offer a selected option")
          _ <- require(selected.distinct.size == selected.size, decisionId,
            "selects an option more than once")
          _ <- require(selected.size >= min && selected.size <= max,
            decisionId,
            if min == max then s"selects ${selected.size} options instead of $min"
            else s"selects ${selected.size} options outside $min..$max")
        yield ()
      case _ =>
        reject(decisionId, "expects a multiple-choice answer")
```

Update the class doc's sentence about staleness to add, after "the answer naming it is rejected below.": "An option shown as unavailable is answered the same way: only available options are offered."

- [ ] **Step 6: Teach the search to skip unavailable options**

In `WalkerSearch.scala`, add `DecisionOption` and `OptionReason` to the `oathdigital.model` import. In `narrow`, replace the two `val refs = ... .options.map(_.ref)` lines with `val refs = choosable(decide)`. In `answers`, replace `one.options.iterator.map(option => DecisionAnswer.ChooseOneAnswer(option.ref))` with `choosable(decide).iterator.map(DecisionAnswer.ChooseOneAnswer(_))`, and `selections(many.options.map(_.ref), many.min, many.max)` with `selections(choosable(decide), many.min, many.max)`.

Replace `narrowed` with:

```scala
  /** `decide` offering only the permitted options. An option `permitted`
    * refuses is kept as unavailable when `explain` gives its reason, and is
    * removed otherwise; an option already unavailable stays as it is.
    * `None` is an optional choose-many or a choose-one passed when empty,
    * left with no option that may be chosen, which is not asked. Other query
    * kinds are returned unchanged.
    */
  def narrowed(decide: Decide, permitted: DecisionOptionRef => Boolean,
      explain: DecisionOptionRef => Option[OptionReason] = _ => None)
      : Option[Decide] =
    def cut(options: Vector[DecisionOption]): Vector[DecisionOption] =
      options.flatMap(option =>
        if !option.available || permitted(option.ref) then Some(option)
        else explain(option.ref).map(DecisionOption.Unavailable(option, _)))
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val options = cut(one.options)
        if decide.passWhenEmpty && !options.exists(_.available) then None
        else Some(decide.copy(query = one.copy(options = options)))
      case many: DecisionQuery.ChooseMany =>
        val options = cut(many.options)
        val left = options.count(_.available)
        if many.min == 0 && left == 0 then None
        else Some(decide.copy(query = many.copy(
          min = math.min(many.min, left), max = math.min(many.max, left),
          options = options)))
      case _ => Some(decide)

  /** The references of the options a player may choose in a choose-one or a
    * choose-many, in declared order; empty for any other kind.
    */
  def choosable(decide: Decide): Vector[DecisionOptionRef] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options.filter(_.available)
        .map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.filter(_.available)
        .map(_.ref)
      case _ => Vector.empty

  /** `narrowed`, the search's cut of `decide`, with each option it dropped
    * back in its place as unavailable when `reasons` says why.
    */
  def explained(decide: Decide, narrowed: Decide,
      reasons: Map[DecisionOptionRef, OptionReason]): Decide =
    if reasons.isEmpty then narrowed
    else decide.query match
      case _: DecisionQuery.ChooseOne | _: DecisionQuery.ChooseMany =>
        val kept = choosable(narrowed).toSet
        this.narrowed(decide, kept, reasons.get).getOrElse(narrowed)
      case _ => narrowed
```

- [ ] **Step 7: Fix the other matches the new fields break**

In `ChoiceWords.scala`, in `shown`, replace the two option cases with:

```scala
      case one: DecisionQuery.ChooseOne => one.options
      case many: DecisionQuery.ChooseMany => many.options
```

and in `labelled` add, after the `Badged` case:

```scala
    case DecisionOption.Unavailable(inner, _) => labelled(ref)(inner)
```

In `WalkerDecisionProjector.scala`, replace the `ChooseMany` case of `queryProjection` with:

```scala
      case many: DecisionQuery.ChooseMany =>
        described(many.options).map(projected =>
          DecisionQueryProjection.ChooseMany(projected, many.min, many.max,
            many.heading))
```

and add to `optionProjection`, after the `Badged` case (Task 2 replaces this line):

```scala
      case DecisionOption.Unavailable(inner, _) => optionProjection(ready,
        viewer, index, inner, details)
```

In `Situation.scala`, replace the `ChooseOne` and `ChooseMany` cases of `defaultAnswer` with:

```scala
    case Park(Decide(_, _, DecisionQuery.ChooseOne(options, _), _, _, _, _),
        _, _, _) if options.exists(_.available) =>
      DecisionAnswer.ChooseOneAnswer(options.filter(_.available).head.ref)
```

```scala
    case Park(Decide(_, _, many: DecisionQuery.ChooseMany, _, _, _, _),
        _, _, _) if many.options.count(_.available) >= many.min =>
      DecisionAnswer.ChooseManyAnswer(
        many.options.filter(_.available).take(many.min).map(_.ref))
```

and add one more `_` to the `Decide(...)` patterns of the `Partition` and `ChooseAmount` cases (seven fields now). Update the doc comment above `defaultAnswer`: "`ChooseOne`: the first available option ... `ChooseMany`: the first `min` available options."

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: PASS.

Then run the whole server suite: `./sbtw test`. Expected: PASS. Record the count; later tasks compare against it.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/testkit/Situation.scala src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala
git commit -m "feat(walker): unavailable options, locked options, tallies and presentation in the decision vocabulary"
```

---

### Task 2: The projection carries states, reasons, tallies and presentation

Spec slice 1, wire half. Still no player-visible change: no builder emits the new shapes, and `presentation` decodes to `"panel"` on the client until Task 6 routes on it.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:96-103,166-169,217-237`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala:31-42`
- Create: `src/main/scala/oathdigital/application/gamelog/NoteReasons.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/gamelog/NoteReasonsSuite.scala` (new), `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`, `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes: Task 1's `OptionReason`, `DecisionOption.Unavailable`, `ChooseMany.locked/tallies/summary`, `Decide.presentation`.
- Produces, in `oathdigital.protocol.projection`:
  - `DecisionOptionProjection(kind, id, label, card = None, details = Vector.empty, badge = None, state: String = "available", reason: Option[String] = None)`; `state` is `"available"`, `"locked"` or `"unavailable"`.
  - `DecisionTallyProjection(label: String, unit: String, values: Vector[DecisionTallyValueProjection], limit: Option[Int] = None)`.
  - `DecisionTallyValueProjection(kind: String, id: String, value: Int)`.
  - `DecisionSummaryProjection(verb: String, one: String, many: String)` with `of(count: Int): String`.
  - `DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions, heading = None, tallies: Vector[DecisionTallyProjection] = Vector.empty, summary: Option[DecisionSummaryProjection] = None)`; locked options are in `options` with `state == "locked"`.
  - `WalkerDecisionProjection(..., answeredOptions = Vector.empty, presentation: String = "panel")`.
- Produces, in `oathdigital.application.gamelog`: `private[application] final class NoteReasons(catalog: ExecutableCatalog, presentation: GamePresentationProjector, wordings: NoteWordings)` with `text(power: PowerId, note: PowerNote, ready: ReadyGame, viewer: Option[PlayerId]): Option[String]`, and a secondary constructor `(catalog, presentation)` that uses `NoteWordings.default(catalog)`.
- Produces, frontend aliases: `DecisionTallyState`, `DecisionTallyValueState`, `DecisionSummaryState` (type and val each).

- [ ] **Step 1: Write the failing codec test**

In `ProjectionProtocolSuite.scala`, after the test "choose-many and choose-amount queries round-trip their counts and bounds", add two tests. They use the suite's own `projection` fixture and `assertRoundTrips`, and reach the codec through `GameProjectionCodec`, as every test there does:

```scala
  test("a board decision's states, reasons, tallies, summary and " +
      "presentation round-trip"):
    val origin = DecisionOptionProjection("site", "plains", "Plains",
      state = "locked")
    val longbows = DecisionOptionProjection("denizen", "longbows", "Longbows")
    val wolves = DecisionOptionProjection("site", "wolves", "Wolves",
      state = "unavailable", reason = Some("Not ruled by the defender"))
    val many = DecisionQueryProjection.ChooseMany(
      Vector(origin, longbows, wolves), 0, 1, Some("Targets"),
      tallies = Vector(DecisionTallyProjection("Defending force", "warbands",
        Vector(DecisionTallyValueProjection("site", "plains", 2)), Some(4))),
      summary = Some(DecisionSummaryProjection("Target", "site", "sites")))
    assertRoundTrips(projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(many),
        presentation = "board"))))

  test("a decision encoded before board decisions decodes with the old " +
      "defaults"):
    val many = DecisionQueryProjection.ChooseMany(
      Vector(DecisionOptionProjection("site", "plains", "Plains")), 0, 1)
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(many))))
    val json = ujson.read(GameProjectionCodec.encode(carrying))
    val decision = json("walkerDecision")
    decision.obj.remove("presentation")
    decision("query").obj.remove("tallies")
    decision("query").obj.remove("summary")
    decision("query")("options").arr.foreach { row =>
      row.obj.remove("state"); row.obj.remove("reason") }
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(carrying))
```

The fixture's `walkerDecision` must be a `Some`; it is, since the existing choose-many test maps over it the same way. `DecisionTallyProjection` and the other new DTOs come in through the suite's `oathdigital.protocol.projection._` import.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compilation fails: `state`, `tallies`, `DecisionTallyProjection` and `presentation` are not members.

- [ ] **Step 3: Add the DTOs**

In `ActionProjectionDtos.scala`, replace `DecisionQueryProjection.ChooseMany` with:

```scala
  /** Pick between `minOptions` and `maxOptions` of the options that are
    * `available`. The bounds count OPTIONS; `ChooseAmount`'s bound a value,
    * which is why they are not shared. Options whose `state` is `locked`
    * are decided already and are never part of the answer. `tallies` and
    * `summary` are display only.
    */
  final case class ChooseMany(options: Vector[DecisionOptionProjection],
      minOptions: Int, maxOptions: Int, heading: Option[String] = None,
      tallies: Vector[DecisionTallyProjection] = Vector.empty,
      summary: Option[DecisionSummaryProjection] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = options
```

Replace `DecisionOptionProjection` with the version below, and extend its doc comment with: "`state` is `available`, `locked` (decided already, shown chosen) or `unavailable` (shown, with `reason` saying why it may not be chosen now)."

```scala
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty,
    badge: Option[String] = None,
    state: String = "available",
    reason: Option[String] = None)
```

After `DecisionSlotProjection`, add:

```scala
/** A running sum over a choose-many's options: the total's `label`, the
  * `unit` one value counts in, each option's value by its `kind` and `id`,
  * and the most the total may reach. Advice for the client; the engine
  * still refuses an answer that breaks the action's own rule.
  */
final case class DecisionTallyProjection(label: String, unit: String,
    values: Vector[DecisionTallyValueProjection], limit: Option[Int] = None)
final case class DecisionTallyValueProjection(kind: String, id: String,
    value: Int)

/** "Target 2 sites": a choose-many's selection, stated. */
final case class DecisionSummaryProjection(verb: String, one: String,
    many: String):
  def of(count: Int): String =
    s"$verb $count ${if count == 1 then one else many}"
```

In `WalkerDecisionProjection`, after `answeredOptions`, add:

```scala
    /** Where the client answers the decision: `"board"` draws each option
      * with a place on the table there, `"panel"` keeps it in the Action
      * panel. A payload without the field was answered in the panel.
      */
    presentation: String = "panel"
```

- [ ] **Step 4: Encode and decode them**

In `ActionProjectionCodec.scala`:

`encodeWalkerDecision` gains `"presentation" -> value.presentation`. `decodeWalkerDecision`'s `exact` set gains `"presentation"`, and it reads `presentation <- default(value, "presentation", path, "panel")(string)` and passes it last to the constructor.

The `ChooseMany` encoder becomes:

```scala
      case DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
          heading, tallies, summary) => ujson.Obj(
        "form" -> "choose-many",
        "options" -> encoded(options)(encodeOptionRow),
        "minOptions" -> minOptions, "maxOptions" -> maxOptions,
        "heading" -> stringOption(heading),
        "tallies" -> encoded(tallies)(encodeTally),
        "summary" -> option(summary)(encodeSummary))
```

`decodeChooseMany` becomes:

```scala
  private def decodeChooseMany(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "options", "minOptions", "maxOptions",
      "heading", "tallies", "summary"), path)
    options <- optionRows(value, path)
    minOptions <- int(value, "minOptions", path)
    maxOptions <- int(value, "maxOptions", path)
    heading <- optionalString(value, "heading", path)
    tallies <- default(value, "tallies", path,
      Vector.empty[DecisionTallyProjection])((raw, child) =>
        array(raw, child).flatMap(traverse(_, child)(decodeTally)))
    summary <- optionalAbsent(value, "summary", path)(decodeSummary)
  yield DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
    heading, tallies, summary)

  private def encodeTally(tally: DecisionTallyProjection): ujson.Value =
    ujson.Obj("label" -> tally.label, "unit" -> tally.unit,
      "values" -> encoded(tally.values)(value => ujson.Obj(
        "kind" -> value.kind, "id" -> value.id, "value" -> value.value)),
      "limit" -> intOption(tally.limit))

  private def decodeTally(raw: ujson.Value, path: String)
      : Result[DecisionTallyProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("label", "unit", "values", "limit"), path)
    label <- string(value, "label", path)
    unit <- string(value, "unit", path)
    rows <- array(value, "values", path)
    values <- traverse(rows, s"$path.values")((row, child) => for
      entry <- obj(row, child)
      _ <- exact(entry, Set("kind", "id", "value"), child)
      kind <- string(entry, "kind", child)
      id <- string(entry, "id", child)
      amount <- int(entry, "value", child)
    yield DecisionTallyValueProjection(kind, id, amount))
    limit <- optionalInt(value, "limit", path)
  yield DecisionTallyProjection(label, unit, values, limit)

  private def encodeSummary(summary: DecisionSummaryProjection): ujson.Value =
    ujson.Obj("verb" -> summary.verb, "one" -> summary.one,
      "many" -> summary.many)

  private def decodeSummary(raw: ujson.Value, path: String)
      : Result[DecisionSummaryProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("verb", "one", "many"), path)
    verb <- string(value, "verb", path)
    one <- string(value, "one", path)
    many <- string(value, "many", path)
  yield DecisionSummaryProjection(verb, one, many)
```

`encodeOptionRow` gains `"state" -> row.state, "reason" -> stringOption(row.reason)`. `decodeOptionRow`'s `exact` set gains `"state"` and `"reason"`, and it reads:

```scala
    state <- default(row, "state", child, "available")(string)
    reason <- optionalAbsent(row, "reason", child)(string)
  yield DecisionOptionProjection(kind, id, label, card, details, badge, state,
    reason)
```

If `int` has no `(ujson.Obj, String, String)` overload, use the one `decodeChooseMany` already uses for `minOptions`.

- [ ] **Step 5: Run the codec test to verify it passes**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 6: Write the failing note-wording test**

Create `src/test/scala/oathdigital/application/gamelog/NoteReasonsSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table

/** A power's note as the reason an option is not on offer: worded by the
  * Game Log's own template, after the power's source.
  */
class NoteReasonsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val ready = Table.start.ready
  private val site = ready.game.current.map.inPlay.head
  private val power = PowerId("test.hider")
  private val hid = NoteKey("hid", Vector(NotePart.Text("This is hidden.")))
  private val reasons = new NoteReasons(catalog, presentation,
    NoteWordings.of(power, Vector(hid)))

  test("a note reads as its source, then its sentence"):
    assertEquals(reasons.text(power, hid(PowerSourceRef.Site(site)), ready,
      None), Some(s"${presentation.siteLabel(site)}: This is hidden."))

  test("a note whose power declares no template has no text"):
    assertEquals(reasons.text(PowerId("test.silent"),
      hid(PowerSourceRef.Site(site)), ready, None), None)
```

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.NoteReasonsSuite"`
Expected: compilation fails: `NoteReasons` not found.

- [ ] **Step 8: Extract `spoken` and add `NoteReasons`**

In `PowerLines.scala`, add after `own`:

```scala
  /** "{source}: {sentence}" for `note`, its cards judged at `seen`, or
    * `None` when `power` declares no template for the note's key.
    */
  def spoken(power: PowerId, note: PowerNote, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] =
    wordings.template(power, note.key).map(template =>
      wordings.source(power).fold(words.source(note.source, seen, viewer))(
        named => Vector(Text(named))) ++
        (Text(": ") +: sentence(template, note.args, seen, viewer)))
```

and make `line`'s `PowerNoted` case use it:

```scala
    case PowerNoted(power, note, _) if !repeated(journal, run, at, power, note) =>
      for
        after <- journal.readyAfter(at)
        spans <- spoken(power, note, states(journal, run, at) :+ after, viewer)
      yield Posted.line(kind(run, power, note), spans)
```

Create `src/main/scala/oathdigital/application/gamelog/NoteReasons.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{PlayerId, PowerId, PowerNote, ReadyGame}

/** A power's note as one line of plain text: the reason an option it hid is
  * not on offer (board decisions design, "Unavailable options"). It is
  * worded exactly as the Game Log words the same note, with its cards
  * judged at the state the decision is asked in.
  */
private[application] final class NoteReasons(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector, wordings: NoteWordings):
  def this(catalog: ExecutableCatalog,
      presentation: GamePresentationProjector) =
    this(catalog, presentation, NoteWordings.default(catalog))

  private val lines = new PowerLines(new LogWords(catalog, presentation),
    wordings)

  def text(power: PowerId, note: PowerNote, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[String] =
    lines.spoken(power, note, Vector(ready), viewer)
      .map(_.map(_.text).mkString)
```

- [ ] **Step 9: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.application.gamelog.NoteReasonsSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: PASS.

- [ ] **Step 10: Write the failing projector tests**

In `WalkerDecisionProjectorSuite.scala`, add `DecisionSummaryProjection`, `DecisionTallyProjection` and `DecisionTallyValueProjection` to the projection import, then append:

```scala
  private def site(name: String): DecisionOptionRef.Site =
    DecisionOptionRef.Site(CatalogNames.site(name))

  test("an unavailable option projects its state and written reason"):
    val context = parked(ActionRef.Recover)
    val tree = Sequence(Decide("test.decide", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Site(site("Dunes")), DecisionOption.Unavailable(
        DecisionOption.Site(site("Broken Peaks")),
        OptionReason.Written("Not ruled by the defender"))))))
    val options = projectorFor(tree).project(context).flatMap(_.query)
      .map(_.offeredOptions).getOrElse(fail("expected a projected query"))
    assertEquals(options.map(o => (o.state, o.reason)), Vector(
      ("available", None),
      ("unavailable", Some("Not ruled by the defender"))))

  test("a choose-many projects its locked options, tallies and summary"):
    val context = parked(ActionRef.Recover)
    val origin = site("Dunes")
    val extra = site("Broken Peaks")
    val tree = Sequence(Decide("test.decide", p1, DecisionQuery.ChooseMany(0,
      1, Vector(DecisionOption.Site(extra)), Some("Targets"),
      locked = Vector(DecisionOption.Site(origin)),
      tallies = Vector(DecisionTally("Defending force", "warbands",
        Map(origin -> 2, extra -> 1))),
      summary = Some(DecisionSummary("Target", "site", "sites")))))
    projectorFor(tree).project(context).flatMap(_.query) match
      case Some(many: DecisionQueryProjection.ChooseMany) =>
        assertEquals(many.options.map(o => (o.id, o.state)), Vector(
          (origin.wireId, "locked"), (extra.wireId, "available")))
        assertEquals(many.tallies, Vector(DecisionTallyProjection(
          "Defending force", "warbands", Vector(
            DecisionTallyValueProjection("site", extra.wireId, 1),
            DecisionTallyValueProjection("site", origin.wireId, 2))
            .sortBy(v => (v.kind, v.id)))))
        assertEquals(many.summary,
          Some(DecisionSummaryProjection("Target", "site", "sites")))
      case other => fail(s"expected a choose-many, got $other")

  test("a decide park projects where it is answered"):
    val context = parked(ActionRef.Recover)
    val board = Sequence(Decide("test.decide", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Site(site("Dunes"))))))
    val panel = Sequence(Decide("test.decide", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Site(site("Dunes")))),
      presentation = DecisionPresentation.Panel))
    assertEquals(projectorFor(board).project(context).map(_.presentation),
      Some("board"))
    assertEquals(projectorFor(panel).project(context).map(_.presentation),
      Some("panel"))

  test("an unavailable option the viewer may not identify is dropped, not " +
      "fatal"):
    val context = parked(ActionRef.Recover)
    // p2's facedown Birdsong is not a card p1 may identify.
    val tree = Sequence(Decide("test.decide", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Site(site("Dunes")), DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(birdsong)),
        OptionReason.Written("No"))))))
    assertEquals(projectorFor(tree).project(context).flatMap(_.query)
      .map(_.offeredOptions.map(_.id)),
      Some(Vector(site("Dunes").wireId)))
```

`projectorFor` and `parked` are the suite's existing helpers; `birdsong` is its existing value.

- [ ] **Step 11: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL: the states are all `"available"`, the tallies empty, and `presentation` is `"panel"`.

- [ ] **Step 12: Project the new fields**

In `WalkerDecisionProjector.scala`:

Add `oathdigital.protocol.projection.{DecisionSummaryProjection, DecisionTallyProjection, DecisionTallyValueProjection}` to the imports, and a field after `deals`:

```scala
  private lazy val reasons = new gamelog.NoteReasons(catalog, presentation)
```

In `parked`, pass `presentation = decide.presentation.key` to the `WalkerDecisionProjection` of a decide park.

Replace `described` inside `queryProjection` with:

```scala
    // An available option that cannot be presented suppresses the whole
    // decision. An unavailable one is only shown, never answered, so it
    // drops itself instead.
    def described(options: Vector[DecisionOption])
        : Option[Vector[DecisionOptionProjection]] =
      val projected = options.map(option => option -> optionProjection(ready,
        viewer, index, option, details.getOrElse(option.ref, Vector.empty)))
      Option.when(projected.forall((option, row) =>
        row.nonEmpty || !option.available))(projected.flatMap(_._2))
```

Replace the `ChooseMany` case (Task 1's version) with:

```scala
      case many: DecisionQuery.ChooseMany =>
        for
          offered <- described(many.options)
          fixed <- described(many.locked)
        yield DecisionQueryProjection.ChooseMany(
          fixed.map(_.copy(state = "locked")) ++ offered, many.min, many.max,
          many.heading,
          many.tallies.map(tally => DecisionTallyProjection(tally.label,
            tally.unit, tally.perOption.toVector.map((ref, value) =>
              DecisionTallyValueProjection(ref.kind, ref.wireId, value))
              .sortBy(value => (value.kind, value.id)), tally.limit)),
          many.summary.map(summary => DecisionSummaryProjection(summary.verb,
            summary.one, summary.many)))
```

Replace Task 1's `Unavailable` case in `optionProjection` with:

```scala
      case DecisionOption.Unavailable(inner, reason) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(state = "unavailable",
          reason = Some(reasonText(ready, viewer, reason))))
```

and add after `optionProjection`:

```scala
  /** An unavailable option's reason as player text: a builder's own words,
    * or the note of the power that hid it, worded as the Game Log words it.
    */
  private def reasonText(ready: ReadyGame, viewer: Option[PlayerId],
      reason: OptionReason): String = reason match
    case OptionReason.Written(text) => text
    case OptionReason.Noted(power, note) =>
      reasons.text(power, note, ready, viewer).getOrElse("Not on offer now")
```

In the class doc of `queryProjection`, after "that suppresses.", add: "An unavailable option is the exception: it is shown, never answered, so one that cannot be presented drops itself."

- [ ] **Step 13: Add the frontend aliases**

In `frontend.scala`, after the `DecisionSlotState` pair, add:

```scala
type DecisionTallyState = protocol.projection.DecisionTallyProjection
val DecisionTallyState = protocol.projection.DecisionTallyProjection
type DecisionTallyValueState = protocol.projection.DecisionTallyValueProjection
val DecisionTallyValueState = protocol.projection.DecisionTallyValueProjection
type DecisionSummaryState = protocol.projection.DecisionSummaryProjection
val DecisionSummaryState = protocol.projection.DecisionSummaryProjection
```

- [ ] **Step 14: Update the projections the new field changes**

`WalkerDecisionProjectionSuite.scala` builds four expected `WalkerDecisionProjection`s with `kind = "decide"` (near lines 160, 189, 234 and 339). Each now gets `presentation = "board"`; the `"roll"` one near line 122 keeps the default.

- [ ] **Step 15: Run the affected suites**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite oathdigital.application.WalkerDecisionProjectionSuite oathdigital.protocol.ProjectionProtocolSuite" "frontend/test"`
Expected: PASS. Then `./sbtw test`: PASS, at Task 1's count plus this task's new tests.

- [ ] **Step 16: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala src/main/scala/oathdigital/application/gamelog/PowerLines.scala src/main/scala/oathdigital/application/gamelog/NoteReasons.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala frontend/src/main/scala/oathdigital/frontend/frontend.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/test/scala/oathdigital/application/gamelog/NoteReasonsSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala
git commit -m "feat(projection): project option states and reasons, choose-many tallies and summary, and presentation"
```

---

### Task 3: The panels show unavailable and locked options

Spec slice 2, first part. A panel-answered decision already receives option states after Task 2; this task makes the panels honour them, so a reason that Task 8 adds anywhere reads correctly before any board work lands.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (the choose-one and Recover option buttons)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala:39-53`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala:27-37`
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`

**Interfaces:**
- Consumes: Task 2's `DecisionOptionState.state` and `.reason`.
- Produces: `WalkerPanelSupport.withholds(control: dom.html.Button, option: DecisionOptionState, after: dom.Element): Unit`, which disables an unavailable option's button, sets its `title` to the reason, and appends `p.walker-choice-reason` with the reason to `after`.

- [ ] **Step 1: Write the failing tests**

In `WalkerChoicePanelRenderSuite.scala` append:

```scala
  test("an unavailable option is a disabled control that says why"):
    val ui = new RecordingControls()
    val blocked = pub.copy(state = "unavailable",
      reason = Some("Lost Tongue: Red may not be targeted."))
    val panel = draw(parked.copy(query = Some(query.copy(
      options = Vector(oak, blocked)))), ui)
    val control = all(panel, ".walker-choice").last
      .asInstanceOf[dom.html.Button]
    assert(control.disabled)
    assertEquals(control.getAttribute("title"),
      "Lost Tongue: Red may not be targeted.")
    assertEquals(all(panel, ".walker-choice-reason").map(_.textContent),
      Vector("Lost Tongue: Red may not be targeted."))
    control.click()
    assertEquals(ui.submitted, Vector.empty)
```

In `WalkerSelectionPanelsSuite.scala` append:

```scala
  test("a locked option shows pressed and an unavailable one cannot be toggled"):
    val ui = new RecordingControls()
    val shown = many.copy(options = Vector(site("a").copy(state = "locked"),
      site("b"), site("c").copy(state = "unavailable",
        reason = Some("Not ruled by the defender"))), minOptions = 0,
      maxOptions = 1)
    val draft = opened("campaign.targets", shown)
    val panel = render(ui, draft, "campaign.targets", shown)
    val locked = button(panel, """[data-option-id="site:a"]""")
    assertEquals(locked.getAttribute("aria-pressed"), "true")
    assert(locked.disabled)
    val blocked = button(panel, """[data-option-id="site:c"]""")
    assert(blocked.disabled)
    assertEquals(blocked.getAttribute("title"), "Not ruled by the defender")
    assertEquals(draft.collect { case d: WalkerChooseManyDraft => d }
      .map(_.toggle("site:c").selected), Some(Vector.empty))
    assertEquals(draft.collect { case d: WalkerChooseManyDraft => d }
      .map(_.toggle("site:a").selected), Some(Vector.empty))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: FAIL: the controls are enabled and carry no title.

- [ ] **Step 3: Honour the states**

In `WalkerPanelSupport.scala`, add:

```scala
  /** An option shown with why it may not be chosen: its control is disabled,
    * names the reason on hover, and the reason is printed under `after`'s
    * last child so a touch screen can read it too. Any other option is left
    * as it is.
    */
  private[frontend] def withholds(control: dom.html.Button,
      option: DecisionOptionState, after: dom.Element): Unit =
    if option.state == "unavailable" then
      control.disabled = true
      option.reason.foreach { reason =>
        control.setAttribute("title", reason)
        after.appendChild(text("p", "walker-choice-reason", reason))
      }
```

In `renderChooseOnePanel`, after the `match` that appends either the `card-choice` or `choose` to `panel`, add `withholds(choose, option, panel)`; keep `choose.disabled = !canControl` before it, so a disabled viewer stays disabled. In `renderRecoverPanel`, after each `panel.appendChild(control)` of the `Choice` step add `withholds(control, option, panel)`, and after each `relics.appendChild(choice)` of the `Relic` step add `withholds(choose, option, choice)`.

In `WalkerSelectionPanels.renderMany`, inside the loop, after `toggle.disabled = !canControl`, add:

```scala
      option.state match
        case "locked" =>
          toggle.setAttribute("aria-pressed", "true")
          toggle.disabled = true
        case _ => WalkerPanelSupport.withholds(toggle, option, rows)
```

In `WalkerChooseManyDraft.toggle`, only an available option may be added:

```scala
    else if selected.size < maximum && query.options.exists(option =>
        WalkerPartitionDraft.itemId(option) == item &&
          option.state == "available") then
      copy(selected = selected :+ item)
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite oathdigital.frontend.WalkerSelectionPanelsSuite oathdigital.frontend.RecoverPanelSuite oathdigital.frontend.WalkerSelectionDraftSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala
git commit -m "feat(frontend): show unavailable options with their reasons and locked options as chosen in the decision panels"
```

---

### Task 4: The board decision model

Spec slice 2, the pure half. Three new modules, each tested alone; nothing uses them until Task 6.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/BoardSelectionDraft.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardSelectionDraftSuite.scala` (new), `frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala` (new)

**Interfaces:**
- Consumes: Task 2's DTO fields and aliases.
- Produces:
  - `enum OptionState(val classes: Vector[String])`: `Available`, `Selected`, `Locked`, `Unavailable`.
  - `final case class OptionView(option: DecisionOptionState, state: OptionState, reason: Option[String])`.
  - `object BoardOptionViews`: `key(kind: String, id: String): String`, `key(option: DecisionOptionState): String` (both `s"$kind:$id"`); `options(query: DecisionQueryState): Vector[DecisionOptionState]`; `of(decision: WalkerDecisionState, query: DecisionQueryState, draft: Option[BoardSelectionDraft]): Map[String, OptionView]`; `blockedBy(query, picks: Vector[String], option): Option[String]`; `total(many: DecisionQueryState.ChooseMany, tally: DecisionTallyState, picks: Vector[String]): Int`; `blocker(query, picks: Vector[String]): Option[String]`.
  - `final case class BoardSelectionDraft(context: BoardSelectionContext, decisionId: String, query: DecisionQueryState, keys: Vector[String])` with `click(option): BoardSelectionDraft`, `cleared`, `picked: Vector[DecisionOptionState]`, `blocker: Option[String]`, `command: Option[GameCommand.ResolveWalker]`; companion `reconcile(previous, context, decision): Option[BoardSelectionDraft]` and `click(previous: Option[BoardSelectionDraft], context, decisionId, query, option): BoardSelectionDraft`.
  - `object BoardHomes`: `has(value: GameProjection, option: DecisionOptionState): Boolean`.

- [ ] **Step 1: Write the failing draft tests**

Create `frontend/src/test/scala/oathdigital/frontend/BoardSelectionDraftSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}

/** The board draft and the option states drawn from it, with no DOM: a
  * choose-one replaces its pick, a choose-many toggles within its maximum
  * and its tallies' limits, and locked and unavailable options never move.
  */
class BoardSelectionDraftSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9L)
  private def site(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val origin = site("origin").copy(state = "locked")
  private val wolves = site("wolves").copy(state = "unavailable",
    reason = Some("Not ruled by the defender"))
  private val needed = DecisionTallyState("Warbands needed", "warbands",
    Vector(DecisionTallyValueState("site", "origin", 1),
      DecisionTallyValueState("site", "a", 2),
      DecisionTallyValueState("site", "b", 2)), Some(4))
  private val many = DecisionQueryState.ChooseMany(
    Vector(origin, site("a"), site("b"), site("c"), wolves), 0, 3,
    Some("Targets"), tallies = Vector(needed))
  private val one = DecisionQueryState.ChooseOne(
    Vector(site("a"), site("b"), wolves), Some("Where?"))
  private def decision(query: DecisionQueryState) =
    WalkerDecisionState("campaign", "campaign.targets", "decide",
      query = Some(query), presentation = "board")
  private def draft(query: DecisionQueryState, keys: String*) =
    BoardSelectionDraft(context, "campaign.targets", query, keys.toVector)

  test("a choose-one click replaces the pick, and a second click clears it"):
    val picked = draft(one).click(site("a")).click(site("b"))
    assertEquals(picked.keys, Vector("site:b"))
    assertEquals(picked.click(site("b")).keys, Vector.empty)

  test("a choose-many click toggles, in the order picked"):
    val picked = draft(many).click(site("c")).click(site("a"))
    assertEquals(picked.keys, Vector("site:c", "site:a"))
    assertEquals(picked.click(site("c")).keys, Vector("site:a"))

  test("locked and unavailable options never move"):
    assertEquals(draft(many).click(origin).keys, Vector.empty)
    assertEquals(draft(many).click(wolves).keys, Vector.empty)
    assertEquals(draft(one).click(wolves).keys, Vector.empty)

  test("a tally's limit blocks the option that would pass it, counting " +
      "locked options"):
    // Locked origin 1, a 2: 3 of 4. Adding b would make 5.
    val picked = draft(many, "site:a")
    assertEquals(BoardOptionViews.blockedBy(many, picked.keys, site("b")),
      Some("Needs 5 warbands; limit 4"))
    assertEquals(picked.click(site("b")).keys, Vector("site:a"))
    assertEquals(BoardOptionViews.total(many, needed, picked.keys), 3)

  test("the maximum blocks a further pick"):
    val full = draft(many.copy(maxOptions = 1), "site:c")
    assertEquals(BoardOptionViews.blockedBy(full.query, full.keys, site("a")),
      Some("Choose at most 1."))

  test("each option's view: locked, unavailable with its reason, selected, " +
      "and blocked by a limit"):
    val views = BoardOptionViews.of(decision(many), many,
      Some(draft(many, "site:a")))
    assertEquals(views("site:origin").state, OptionState.Locked)
    assertEquals(views("site:wolves"), OptionView(wolves,
      OptionState.Unavailable, Some("Not ruled by the defender")))
    assertEquals(views("site:a").state, OptionState.Selected)
    assertEquals(views("site:b"), OptionView(site("b"),
      OptionState.Unavailable, Some("Needs 5 warbands; limit 4")))
    assertEquals(views("site:c").state, OptionState.Available)

  test("a choose-one's answers so far show locked"):
    val played = DecisionOptionState("denizen", "wrestlers", "Wrestlers")
    val views = BoardOptionViews.of(decision(one).copy(
      answeredOptions = Vector(played)), one, None)
    assertEquals(views("denizen:wrestlers").state, OptionState.Locked)

  test("a draft of another decision marks nothing selected"):
    val other = draft(many, "site:a").copy(decisionId = "other")
    assertEquals(BoardOptionViews.of(decision(many), many, Some(other))(
      "site:a").state, OptionState.Available)

  test("Confirm waits for the minimum and submits the picks in order"):
    val needsTwo = many.copy(minOptions = 2)
    assertEquals(draft(needsTwo, "site:c").blocker, Some("Choose at least 2."))
    assertEquals(draft(needsTwo, "site:c", "site:a").command,
      Some(Intent.ResolveWalker("campaign.targets",
        DecisionAnswerWire.ChooseManyWire(Vector(
          DecisionOptionWire("site", "c"), DecisionOptionWire("site", "a"))))))

  test("an optional choose-many confirms the empty selection"):
    assertEquals(draft(many).command, Some(Intent.ResolveWalker(
      "campaign.targets", DecisionAnswerWire.ChooseManyWire(Vector.empty))))

  test("a choose-one confirms its one pick and nothing before it"):
    assertEquals(draft(one).blocker, Some("Choose an option first."))
    assertEquals(draft(one, "site:b").command, Some(Intent.ResolveWalker(
      "campaign.targets", DecisionAnswerWire.ChooseOneWire("site", "b"))))

  test("a draft is kept while the same question is asked, and dropped " +
      "otherwise"):
    val kept = draft(many, "site:a")
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context,
      Some(decision(many))), Some(kept))
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context,
      Some(decision(many.copy(maxOptions = 2)))), None)
    assertEquals(BoardSelectionDraft.reconcile(Some(kept),
      context.copy(sequence = 10L), Some(decision(many))), None)
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context, None),
      None)

  test("a click on an empty or stale draft starts a fresh one"):
    val stale = draft(one, "site:a").copy(decisionId = "other")
    assertEquals(BoardSelectionDraft.click(Some(stale), context,
      "campaign.targets", many, site("c")).keys, Vector("site:c"))
    assertEquals(BoardSelectionDraft.click(None, context,
      "campaign.targets", many, site("c")).keys, Vector("site:c"))
```

- [ ] **Step 2: Write the failing board-home tests**

Create `frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor

/** Whether an option has a place on the table the projection draws, which
  * decides between the board and the Action panel.
  */
class BoardHomesSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox")
  private val woods = GameSite("woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 1,
    denizens = Vector(GameSiteCard("fox", "Fox", Some(fox))),
    relics = GameSiteRelics(1, Vector(CardDetails("cup", "relic", "Cup"))),
    defense = 1)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val board = PlayerBoard("red", warbands = 3, favor = 0,
    faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 1, supply = 7, pawnSiteId = Some("woods"),
    advisers = Vector(CardDetails("oak", "denizen", "Old Oak"),
      CardDetails("hidden", "denizen", "Facedown denizen", hidden = true)),
    relics = Vector(CardDetails("horn", "relic", "Horn")),
    revealedVision = None)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))))
    .copy(playerBoards = Vector(board),
      banners = Vector(BannerState("peoples-favor", "mob", None, 1)),
      favorBanks = Vector(FavorBankState("beast", 3)))
  private def has(kind: String, id: String) =
    BoardHomes.has(table, DecisionOptionState(kind, id, id))

  test("sites, site cards, board cards, slots, banners, banks and players " +
      "have homes"):
    assert(has("site", "woods"))
    assert(has("denizen", "fox"))
    assert(has("relic", "cup"))
    assert(has("denizen", "oak"))
    assert(has("relic", "horn"))
    assert(has("adviser-slot", "red:1"))
    assert(has("relic-slot", "red:0"))
    assert(has("banner", "peoples-favor"))
    assert(has("favor-bank", "beast"))
    assert(has("player", "red"))

  test("buttons, decks, absent things and hidden cards have none"):
    assert(!has("button", "finish"))
    assert(!has("deck", "world"))
    assert(!has("site", "nowhere"))
    assert(!has("adviser-slot", "red:2"))
    assert(!has("denizen", "hidden"))
    assert(!has("player", "blue"))
```

Check the constructors against `frontend.scala` and the existing `BoardHomesSuite`-like fixtures (`BoardSurfaceSuite`, `PlayerBoardSuite`): if `BannerState` or `FavorBankState` take other fields, follow their real shape.

- [ ] **Step 3: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardSelectionDraftSuite oathdigital.frontend.BoardHomesSuite"`
Expected: compilation fails: `BoardSelectionDraft`, `BoardOptionViews`, `OptionState` and `BoardHomes` are not found.

- [ ] **Step 4: Write `BoardOptionViews.scala`**

```scala
package oathdigital.frontend

/** How a board option is drawn; `classes` are its CSS classes. */
private[frontend] enum OptionState(val classes: Vector[String]):
  case Available extends OptionState(Vector("board-option"))
  case Selected extends OptionState(
    Vector("board-option", "board-option-selected"))
  case Locked extends OptionState(
    Vector("board-option", "board-option-locked"))
  case Unavailable extends OptionState(
    Vector("board-option", "board-option-unavailable"))

/** One option as the table draws it, and why it may not be chosen. */
private[frontend] final case class OptionView(option: DecisionOptionState,
    state: OptionState, reason: Option[String])

/** The parked board decision's options as the table and the panel draw
  * them (board decisions design, "Selection"). Pure: the projected query and
  * the draft decide everything.
  */
private[frontend] object BoardOptionViews:
  /** The option's identity on the page: its `data-option-ref`, and what a
    * draft records.
    */
  def key(kind: String, id: String): String = s"$kind:$id"
  def key(option: DecisionOptionState): String = key(option.kind, option.id)

  /** Every option the query shows, locked and unavailable ones included. */
  def options(query: DecisionQueryState): Vector[DecisionOptionState] =
    query match
      case one: DecisionQueryState.ChooseOne => one.options
      case many: DecisionQueryState.ChooseMany => many.options
      case _ => Vector.empty

  /** Each option by key: locked and unavailable as projected, then the
    * draft's picks, then what the draft's limits block. A choose-one's
    * answers so far (a battle-plan window's played plans) show locked.
    */
  def of(decision: WalkerDecisionState, query: DecisionQueryState,
      draft: Option[BoardSelectionDraft]): Map[String, OptionView] =
    val picks = draft.filter(_.decisionId == decision.decisionId)
      .fold(Vector.empty[String])(_.keys)
    val played = query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
        .map(option => OptionView(option, OptionState.Locked, None))
      case _ => Vector.empty
    (played ++ options(query).map(view(query, picks, _)))
      .map(found => key(found.option) -> found).toMap

  private def view(query: DecisionQueryState, picks: Vector[String],
      option: DecisionOptionState): OptionView = option.state match
    case "locked" => OptionView(option, OptionState.Locked, None)
    case "unavailable" =>
      OptionView(option, OptionState.Unavailable, option.reason)
    case _ if picks.contains(key(option)) =>
      OptionView(option, OptionState.Selected, None)
    case _ => blockedBy(query, picks, option).fold(
      OptionView(option, OptionState.Available, None))(reason =>
        OptionView(option, OptionState.Unavailable, Some(reason)))

  /** Why adding `option` to `picks` would pass the maximum or a tally's
    * limit. A choose-one replaces its pick, so nothing blocks it.
    */
  def blockedBy(query: DecisionQueryState, picks: Vector[String],
      option: DecisionOptionState): Option[String] = query match
    case many: DecisionQueryState.ChooseMany
        if !picks.contains(key(option)) =>
      if picks.size >= many.maxOptions then
        Some(s"Choose at most ${many.maxOptions}.")
      else overLimit(many, picks :+ key(option))
    case _ => None

  /** The first tally whose limit `picks` pass, as "Needs 5 warbands; limit
    * 4".
    */
  private def overLimit(many: DecisionQueryState.ChooseMany,
      picks: Vector[String]): Option[String] =
    many.tallies.iterator.flatMap(tally => tally.limit.flatMap { limit =>
      val sum = total(many, tally, picks)
      Option.when(sum > limit)(s"Needs $sum ${tally.unit}; limit $limit")
    }).nextOption()

  /** A tally's total over `picks` and the locked options. */
  def total(many: DecisionQueryState.ChooseMany, tally: DecisionTallyState,
      picks: Vector[String]): Int =
    val counted = picks.toSet ++
      many.options.filter(_.state == "locked").map(key)
    tally.values.filter(value => counted(key(value.kind, value.id)))
      .map(_.value).sum

  /** Why Confirm may not submit `picks` yet, or `None` when it may. */
  def blocker(query: DecisionQueryState, picks: Vector[String])
      : Option[String] = query match
    case _: DecisionQueryState.ChooseOne =>
      Option.when(picks.isEmpty)("Choose an option first.")
    case many: DecisionQueryState.ChooseMany =>
      if picks.size < many.minOptions then
        Some(s"Choose at least ${many.minOptions}.")
      else overLimit(many, picks)
    case _ => Some("This decision is not answered on the board.")
```

- [ ] **Step 5: Write `BoardSelectionDraft.scala`**

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** What a viewer has picked for a parked board decision and not yet
  * confirmed: option keys ([[BoardOptionViews.key]]) in the order picked.
  * Like the other walker drafts it knows only the projected query, so its
  * answer names only projected options. A choose-one holds at most one key.
  */
private[frontend] final case class BoardSelectionDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, keys: Vector[String]):

  /** After a click on `option`: a picked option is dropped; otherwise a
    * choose-one replaces its pick and a choose-many adds it. An option that
    * may not be chosen, or that the limits block, changes nothing.
    */
  def click(option: DecisionOptionState): BoardSelectionDraft =
    val key = BoardOptionViews.key(option)
    if keys.contains(key) then copy(keys = keys.filterNot(_ == key))
    else if option.state != "available" ||
        BoardOptionViews.blockedBy(query, keys, option).nonEmpty then this
    else query match
      case _: DecisionQueryState.ChooseOne => copy(keys = Vector(key))
      case _ => copy(keys = keys :+ key)

  def cleared: BoardSelectionDraft = copy(keys = Vector.empty)

  /** The picked options, in the order picked. */
  def picked: Vector[DecisionOptionState] =
    val shown = BoardOptionViews.options(query)
    keys.flatMap(key => shown.find(BoardOptionViews.key(_) == key))

  def blocker: Option[String] = BoardOptionViews.blocker(query, keys)

  def command: Option[GameCommand.ResolveWalker] =
    if blocker.nonEmpty then None
    else query match
      case _: DecisionQueryState.ChooseOne => picked.headOption.map(option =>
        GameCommand.ResolveWalker(decisionId,
          DecisionAnswerWire.ChooseOneWire(option.kind, option.id)))
      case _: DecisionQueryState.ChooseMany => Some(GameCommand.ResolveWalker(
        decisionId, DecisionAnswerWire.ChooseManyWire(picked.map(option =>
          DecisionOptionWire(option.kind, option.id)))))
      case _ => None

private[frontend] object BoardSelectionDraft:
  /** Keeps a draft while the projection asks the same question in the same
    * context, and drops it otherwise: a new sequence, decision or option set
    * is a different question.
    */
  def reconcile(previous: Option[BoardSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[BoardSelectionDraft] =
    previous.filter(draft => draft.context == context &&
      decision.exists(parked => parked.decisionId == draft.decisionId &&
        parked.query.contains(draft.query)))

  /** The draft after a click on `option`, starting a fresh one when
    * `previous` is empty or answers another question.
    */
  def click(previous: Option[BoardSelectionDraft],
      context: BoardSelectionContext, decisionId: String,
      query: DecisionQueryState, option: DecisionOptionState)
      : BoardSelectionDraft =
    previous.filter(draft => draft.context == context &&
        draft.decisionId == decisionId && draft.query == query)
      .getOrElse(BoardSelectionDraft(context, decisionId, query, Vector.empty))
      .click(option)
```

- [ ] **Step 6: Write `BoardHomes.scala`**

```scala
package oathdigital.frontend

/** Whether an option has a place on the table (board decisions design,
  * "Board home"): a site's box, a card in a site's row or on a player
  * board, a banner, a favor bank, or a player's name and pawns. An option
  * with none is drawn in the Action panel. The rule mirrors the keys the
  * table's renderers hand `BoardOptions`, so a home here is an element there.
  */
private[frontend] object BoardHomes:
  def has(value: GameProjection, option: DecisionOptionState): Boolean =
    option.kind match
      case "site" => sites(value).exists(_.siteId == option.id)
      case "denizen" | "edifice" | "vision" =>
        siteCards(value).contains(option.id) ||
          shown(value.playerBoards.flatMap(_.advisers)).contains(option.id)
      case "relic" =>
        siteRelics(value).contains(option.id) ||
          shown(value.playerBoards.flatMap(_.relics)).contains(option.id)
      case "adviser-slot" =>
        slot(option.id).exists((player, at) => value.playerBoards
          .exists(board => board.playerId == player &&
            board.advisers.isDefinedAt(at)))
      case "relic-slot" =>
        slot(option.id).exists((player, at) => value.playerBoards
          .exists(board => board.playerId == player &&
            board.relics.isDefinedAt(at)))
      case "banner" => value.banners.exists(_.key == option.id)
      case "favor-bank" => value.favorBanks.exists(_.suit == option.id)
      case "player" => value.players.exists(_.playerId == option.id)
      case _ => false

  private def sites(value: GameProjection): Vector[GameSite] =
    value.world.flatMap(_.sites)

  private def siteCards(value: GameProjection): Vector[String] =
    sites(value).flatMap(_.denizens.filter(_.details.nonEmpty).map(_.cardId))

  private def siteRelics(value: GameProjection): Vector[String] =
    sites(value).flatMap(_.relics.knownRelics.map(_.cardId))

  private def shown(cards: Vector[CardDetails]): Vector[String] =
    cards.filterNot(_.hidden).map(_.cardId)

  /** A slot reference's owner and index: the owner may hold a colon, so the
    * index is what follows the last one.
    */
  private def slot(id: String): Option[(String, Int)] =
    val at = id.lastIndexOf(':')
    Option.when(at > 0)(id.take(at)).flatMap(owner =>
      id.drop(at + 1).toIntOption.filter(_ >= 0).map(owner -> _))
```

If the projection's world field is not `world: Vector[GameRegion]` with `sites`, or a site's relics are not `relics.knownRelics`, follow the names `WorldBoardRenderer` uses.

- [ ] **Step 7: Run them to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardSelectionDraftSuite oathdigital.frontend.BoardHomesSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardOptionViews.scala frontend/src/main/scala/oathdigital/frontend/BoardSelectionDraft.scala frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala frontend/src/test/scala/oathdigital/frontend/BoardSelectionDraftSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardHomesSuite.scala
git commit -m "feat(frontend): model board decisions: option states, the selection draft and board homes"
```

---

### Task 5: The table draws board options

Spec slice 2, the DOM half. A new `BoardOptions` marks the table's elements; the renderers hand it every element an option can live on. Nothing builds a non-empty `BoardOptions` until Task 6, so the table is unchanged in play.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:47-69` (`siteDetails`)
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala` (new)

**Interfaces:**
- Consumes: Task 4's `OptionView`, `OptionState`, `BoardOptionViews.key`.
- Produces:
  - `final class BoardOptions(views: Map[String, OptionView], pick: DecisionOptionState => Unit)` with `view(keys: Vector[String]): Option[OptionView]`, `decorate(element: dom.Element, keys: Vector[String]): Unit`, `card(face: dom.html.Button, details: CardDetails, keys: Vector[String]): dom.Element`.
  - `object BoardOptions`: `none: BoardOptions`; `cardKey(card: CardDetails): String`; `hoverSync(element: dom.Element, key: String): Unit`; `highlight(key: String, on: Boolean): Unit`.
  - `WorldBoardRenderer.players(value, playerId, options: BoardOptions = BoardOptions.none)`, `playerBoards(value, playerId, options: BoardOptions = BoardOptions.none)`, and `world(value, board, canControl, drafts, controls, options: BoardOptions = BoardOptions.none)` (Task 6 drops `board` and `canControl`).
  - `ServerUiSupport.siteDetails(site: GameSite, options: BoardOptions = BoardOptions.none)`.
  - CSS class names the renderers set: `board-option`, `board-option-selected`, `board-option-locked`, `board-option-unavailable`, `board-option-hover`, `board-option-card`, `board-option-inspect`.

- [ ] **Step 1: Write the failing render tests**

Create `frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one, press}
import oathdigital.model.PlayerColor
import org.scalajs.dom
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

/** The table's elements as board options, under jsdom: each is marked with
  * its state, a click picks only the element it landed on, a card's inspect
  * button reads the card without picking it, and hovering one element of an
  * option highlights every element of it.
  */
class BoardOptionsRenderSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox", rulesText = Some("Sly."))
  private val woods = GameSite("woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector(GameSiteCard("fox", "Fox", Some(fox))),
    relics = GameSiteRelics(0), defense = 1)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val oak = CardDetails("oak", "denizen", "Old Oak")
  private val horn = CardDetails("horn", "relic", "Horn")
  private val board = PlayerBoard("red", warbands = 3, favor = 0,
    faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 1, supply = 7, pawnSiteId = Some("woods"),
    advisers = Vector(oak), relics = Vector(horn), revealedVision = None,
    banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)))
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))))
    .copy(pawnLocations = Vector(GamePawn("red", "woods")),
      playerBoards = Vector(board),
      banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)),
      favorBanks = Vector(FavorBankState("beast", 3)))

  private def option(kind: String, id: String) =
    DecisionOptionState(kind, id, id)
  private val woodsSite = option("site", "woods")
  private val redPlayer = option("player", "red")
  private val foxCard = option("denizen", "fox")

  /** Options for `views`, and the picks they record. */
  private def optionsFor(views: OptionView*)
      : (BoardOptions, ArrayBuffer[DecisionOptionState]) =
    val picks = ArrayBuffer.empty[DecisionOptionState]
    (new BoardOptions(views.map(view =>
      BoardOptionViews.key(view.option) -> view).toMap,
      chosen => { picks += chosen; () }), picks)

  private def available(option: DecisionOptionState) =
    OptionView(option, OptionState.Available, None)

  private def world(options: BoardOptions): dom.Element =
    WorldBoardRenderer.world(table, None, canControl = true,
      SessionDrafts.empty, new RecordingControls(), options)

  private def site(root: dom.Element): dom.Element =
    one(root, """article[aria-label="Deep Woods"]""")

  test("a site option is marked and picked by a click on its box"):
    val (options, picks) = optionsFor(available(woodsSite))
    val box = site(world(options))
    assert(box.classList.contains("board-option"))
    assertEquals(box.getAttribute("data-option-ref"), "site:woods")
    assertEquals(box.getAttribute("aria-pressed"), "false")
    click(box)
    assertEquals(picks.toVector, Vector(woodsSite))

  test("Enter on a marked site picks it"):
    val (options, picks) = optionsFor(available(woodsSite))
    press(site(world(options)), "Enter")
    assertEquals(picks.toVector, Vector(woodsSite))

  test("a card inside a site option reads the card and does not pick the site"):
    val (options, picks) = optionsFor(available(woodsSite))
    click(one(world(options), "button.card-face"))
    assertEquals(picks.toVector, Vector.empty)

  test("a card option's body picks it, and its inspect button does not"):
    val (options, picks) = optionsFor(available(foxCard))
    val root = world(options)
    val face = one(root, "button.card-face")
    assertEquals(face.getAttribute("data-option-ref"), "denizen:fox")
    click(face)
    assertEquals(picks.toVector, Vector(foxCard))
    val inspect = one(root, ".board-option-card > .board-option-inspect")
    assertEquals(inspect.getAttribute("aria-label"), "Inspect Fox")
    click(inspect)
    assertEquals(picks.toVector, Vector(foxCard))

  test("a pawn picks its player, not the site it stands at"):
    val (options, picks) = optionsFor(available(woodsSite),
      available(redPlayer))
    click(one(world(options), ".site-pawns .pawn"))
    assertEquals(picks.toVector, Vector(redPlayer))

  test("the name in a player board's header picks the player"):
    val (options, picks) = optionsFor(available(redPlayer))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    click(one(boards, ".player-identity .player-ref"))
    assertEquals(picks.toVector, Vector(redPlayer))

  test("a board's adviser and relic answer to their slots and their cards"):
    val bySlot = option("adviser-slot", "red:0")
    val byCard = option("relic", "horn")
    val (options, picks) = optionsFor(available(bySlot), available(byCard))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    assertEquals(all(boards, ".board-cards [data-option-ref]")
      .map(_.getAttribute("data-option-ref")),
      Vector("adviser-slot:red:0", "relic:horn"))
    click(one(boards, """[data-option-ref="relic:horn"]"""))
    assertEquals(picks.toVector, Vector(byCard))

  test("banners and favor banks are marked"):
    val (options, _) = optionsFor(available(option("banner",
      "peoples-favor")), available(option("favor-bank", "beast")))
    val root = world(options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    assertEquals(all(boards, """.player-banner[data-option-ref]""").size, 1)
    assertEquals(all(root, """.shared-banner[data-option-ref]""").size, 1)
    assertEquals(all(root, """.favor-bank[data-option-ref="favor-bank:beast"]""")
      .size, 1)

  test("locked and unavailable options take no pick, and say why"):
    val (options, picks) = optionsFor(
      OptionView(woodsSite, OptionState.Unavailable,
        Some("Not ruled by the defender")),
      OptionView(foxCard, OptionState.Locked, None))
    val root = world(options)
    val box = site(root)
    assert(box.classList.contains("board-option-unavailable"))
    assertEquals(box.getAttribute("title"), "Not ruled by the defender")
    assertEquals(box.getAttribute("aria-disabled"), "true")
    click(box)
    val face = one(root, "button.card-face")
    assert(face.classList.contains("board-option-locked"))
    click(face)
    assertEquals(picks.toVector, Vector.empty)

  test("a card that is no option still opens on a click and gets no " +
      "inspect button"):
    val (options, _) = optionsFor(available(woodsSite))
    assertEquals(all(world(options), ".board-option-inspect"), Vector.empty)

  test("hovering one element of an option highlights every element of it"):
    val (options, _) = optionsFor(available(redPlayer))
    val root = world(options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    dom.document.body.appendChild(root)
    dom.document.body.appendChild(boards)
    try
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseenter", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      val chip = one(boards, ".player-identity .player-ref")
      assert(chip.classList.contains("board-option-hover"))
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseleave", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      assert(!chip.classList.contains("board-option-hover"))
    finally
      dom.document.body.removeChild(root)
      dom.document.body.removeChild(boards)
```

Use `GamePawn`'s real field order (`playerId`, `siteId`, as `WorldBoardRenderer` reads it).

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardOptionsRenderSuite"`
Expected: compilation fails: `BoardOptions` is not found, and `world` and `playerBoards` take no options.

- [ ] **Step 3: Write `BoardOptions.scala`**

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element}

/** The parked board decision drawn onto the table (board decisions design,
  * "BoardOptions"). A renderer hands each element it draws the option keys
  * that element stands for; the first key the decision shows marks the
  * element with that option's state, and an available or selected option
  * answers a click, Enter or Space with `pick`.
  *
  * A click belongs to the element only when no control inside it took the
  * click first: a card inside a site box, a pawn inside a site, the inspect
  * button beside a card. Each of those answers for itself. `none` marks
  * nothing.
  */
private[frontend] final class BoardOptions(views: Map[String, OptionView],
    pick: DecisionOptionState => Unit):

  def view(keys: Vector[String]): Option[OptionView] =
    keys.iterator.flatMap(views.get).nextOption()

  /** Marks `target` as the option the first of `keys` names, if any. */
  def decorate(target: dom.Element, keys: Vector[String]): Unit =
    view(keys).foreach(mark(target, _))

  /** A card face, marked when it is an option. An option card's body
    * selects it, so reading it moves to an inspect button beside it; the
    * two share one box, since a button may not hold a button. A card the
    * viewer may not identify has nothing to read and gets no button.
    * Returns what to append.
    */
  def card(face: dom.html.Button, details: CardDetails,
      keys: Vector[String]): dom.Element = view(keys) match
    case None => face
    case Some(found) =>
      face.onclick = null
      mark(face, found)
      if details.hidden then face
      else
        val box = element("span", "board-option-card")
        box.appendChild(face)
        val inspect = button("i", "board-option-inspect")
        inspect.setAttribute("aria-label", s"Inspect ${details.name}")
        inspect.addEventListener("click", (event: dom.Event) => {
          event.stopPropagation()
          CardInspection.open(details, inspect)
        })
        box.appendChild(inspect)
        box

  private def mark(target: dom.Element, found: OptionView): Unit =
    val key = BoardOptionViews.key(found.option)
    found.state.classes.foreach(target.classList.add)
    target.setAttribute("data-option-ref", key)
    found.reason.orElse(Option.when(found.option.details.nonEmpty)(
      found.option.details.mkString(" · ")))
      .foreach(target.setAttribute("title", _))
    BoardOptions.hoverSync(target, key)
    val native = target.tagName == "BUTTON"
    if !native then
      target.setAttribute("role", "button")
      target.setAttribute("tabindex", "0")
    found.state match
      case OptionState.Available | OptionState.Selected =>
        target.setAttribute("aria-pressed",
          (found.state == OptionState.Selected).toString)
        target.addEventListener("click", (event: dom.Event) =>
          if own(event, target) then
            event.stopPropagation()
            pick(found.option))
        // A button turns Enter and Space into a click by itself.
        if !native then
          target.addEventListener("keydown", (event: dom.Event) => {
            val pressed = event.asInstanceOf[dom.KeyboardEvent].key
            if (pressed == "Enter" || pressed == " ") && own(event, target)
            then
              event.preventDefault()
              event.stopPropagation()
              pick(found.option)
          })
      case OptionState.Locked =>
        target.setAttribute("aria-pressed", "true")
        target.setAttribute("aria-disabled", "true")
      case OptionState.Unavailable =>
        target.setAttribute("aria-disabled", "true")

  /** Whether `event` landed on `target` itself rather than on a control
    * inside it.
    */
  private def own(event: dom.Event, target: dom.Element): Boolean =
    event.target match
      case from: dom.Element =>
        val nearest = from.closest("button, [role=button]")
        nearest == null || nearest == target
      case _ => true

private[frontend] object BoardOptions:
  val none: BoardOptions = new BoardOptions(Map.empty, _ => ())

  /** A card's own option key: its kind as an option names it, and its id. */
  def cardKey(card: CardDetails): String =
    BoardOptionViews.key(card.cardKind match
      case "relic" => "relic"
      case "vision" => "vision"
      case "edifice" => "edifice"
      case _ => "denizen", card.cardId)

  /** Hovering `target` highlights every element of the same option, on the
    * table and in the panel, and leaving it clears them.
    */
  def hoverSync(target: dom.Element, key: String): Unit =
    target.addEventListener("mouseenter", (_: dom.Event) =>
      highlight(key, on = true))
    target.addEventListener("mouseleave", (_: dom.Event) =>
      highlight(key, on = false))

  def highlight(key: String, on: Boolean): Unit =
    dom.document.querySelectorAll(s"""[data-option-ref="$key"]""").toVector
      .map(_.asInstanceOf[dom.Element]).foreach(node =>
        if on then node.classList.add("board-option-hover")
        else node.classList.remove("board-option-hover"))
```

- [ ] **Step 4: Hand the renderers' elements to it**

In `ServerUiSupport.siteDetails`, take `options: BoardOptions = BoardOptions.none` and replace the denizen loop and the peeked-relic line with:

```scala
    site.denizens.foreach { denizen =>
      denizen.details match
        case Some(details) =>
          val face = CardFace.render(details)
          face.setAttribute("data-denizen-id", denizen.denizenId)
          cards.appendChild(options.card(face, details,
            Vector(BoardOptions.cardKey(details))))
        case None =>
          val back = facedownCard("denizen")
          back.setAttribute("data-denizen-id", denizen.denizenId)
          cards.appendChild(back)
    }
```

```scala
    presentation.peekedRelics.foreach(value => cards.appendChild(options.card(
      CardFace.render(value.card), value.card,
      Vector(BoardOptions.cardKey(value.card)))))
```

In `WorldBoardRenderer.scala`:

- `players` and `playerBoards` take `options: BoardOptions = BoardOptions.none`; `players` passes it on.
- In `playerBoards`, build the header's name as `val name = playerReference(value, player.playerId)`, call `options.decorate(name, Vector(BoardOptionViews.key("player", player.playerId)))`, and append `name` where `playerReference(...)` was appended.
- Replace the two card loops with:

```scala
     board.advisers.zipWithIndex.foreach((card, at) => cards.appendChild(
       options.card(CardFace.render(card), card,
         boardCardKeys("adviser-slot", player.playerId, at, card))))
     board.relics.zipWithIndex.foreach((card, at) => cards.appendChild(
       options.card(CardFace.render(card), card,
         boardCardKeys("relic-slot", player.playerId, at, card))))
```

- Replace the board banner loop's body with one that keeps the `p` in a value, calls `options.decorate(mark, Vector(BoardOptionViews.key("banner", banner.key)))`, then appends it.
- Add the helper:

```scala
 /** The keys a card on a player board answers to: its slot, and the card
   * itself when the viewer may know which card it is.
   */
 private def boardCardKeys(slot: String, playerId: String, at: Int,
     card: CardDetails): Vector[String] =
   BoardOptionViews.key(slot, s"$playerId:$at") +:
     Option.unless(card.hidden)(BoardOptions.cardKey(card)).toVector
```

- `world` takes `options: BoardOptions = BoardOptions.none` last. After the `siteOption.foreach { ... }` block call `options.decorate(control, Vector(BoardOptionViews.key("site", site.siteId)))`; after each pawn `marker` is built call `options.decorate(marker, Vector(BoardOptionViews.key("player", pawn.playerId)))`; pass `options` to `siteDetails(site, options)`, `favorBanks(value, options)` and `sharedBank(value, options)`.
- `favorBanks` and `sharedBank` take `options: BoardOptions`. Decorate each bank chip with `BoardOptionViews.key("favor-bank", bank.suit)` and each shared banner `p` with `BoardOptionViews.key("banner", banner.key)`.

- [ ] **Step 5: Run the render tests and the board suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardOptionsRenderSuite oathdigital.frontend.BoardSurfaceSuite oathdigital.frontend.PlayerBoardSuite oathdigital.frontend.SiteBoxLayoutSuite oathdigital.frontend.DiscardPileSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala
git commit -m "feat(frontend): mark board options on sites, cards, pawns, player boards, banners and favor banks"
```

---

### Task 6: Board decisions route to the table and the panel

Spec slice 2, the switch. Every board-presented choose-one or choose-many with an option on the table now goes to `Surface.BoardDecision`; setup pawn placement moves onto it; `Surface.Board`, `WalkerBoardDraft` and `renderBoardPanel` go.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala`
- Delete: `frontend/src/main/scala/oathdigital/frontend/WalkerBoardDraft.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala:21,40-41,51,110`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala` (companion `of` and `stager`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala` (`world`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:91-104`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:129-147` (delete `renderBoardPanel`)
- Test: `frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/BoardSurfaceSuite.scala` (rewritten), `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala:71-75`, `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala` (new); the `world` call sites of `BoardOptionsRenderSuite`, `DiscardPileSuite`, `PanelPlacementSuite`, `SiteBoxLayoutSuite` and `SuitGlyphSuite`

**Interfaces:**
- Consumes: Tasks 3 to 5.
- Produces:
  - `ParkedDecision.BoardForm = DecisionQueryState.ChooseOne | DecisionQueryState.ChooseMany`; `Surface.BoardDecision(decision: WalkerDecisionState, query: BoardForm)`.
  - `SessionDrafts.board: Option[BoardSelectionDraft]`; `Draft.Board(value: Option[BoardSelectionDraft])`.
  - `BoardOptions.stager(surface: ParkedDecision.Surface.BoardDecision, drafts: SessionDrafts, canControl: Boolean, controls: TableControls): DecisionOptionState => Unit` and `BoardOptions.of(surface, drafts, canControl, controls): BoardOptions`.
  - `BoardDecisionPanel.render(surface, value: GameProjection, drafts: SessionDrafts, canControl: Boolean, panel: dom.Element, controls: TableControls): Unit`.
  - `WorldBoardRenderer.world(value: GameProjection, options: BoardOptions, drafts: SessionDrafts, controls: TableControls)`.
  - Panel classes: `board-decision-summary`, `board-decision-rows`, `board-decision-row`, `board-decision-row-locked`, `board-decision-row-values`, `board-decision-total`, `board-decision-total-over`, `board-decision-loose-option`, `board-decision-rule`, `board-decision-decide`, `board-decision-rule-labelled`, `board-decision-clear`, `board-decision-confirm`, `board-decision-reason`.

- [ ] **Step 1: Write the failing routing tests**

In `ParkedDecisionSuite.scala`, replace the test "Setup's pawn placement routes to the board, confirmed from the pane" with:

```scala
  test("a board-presented decision with an option on the table routes to " +
      "the board"):
    // Blue is a player at the table, so the option's home is Blue's name
    // and pawns.
    val atBoard = parked("muster", "muster.source", one)
      .copy(presentation = "board")
    assertEquals(routeOf(atBoard).surface,
      Some(Surface.BoardDecision(atBoard, one)))
    val manyAtBoard = parked("challenge", "challenge.ribbon-site", many)
      .copy(presentation = "board")
    assertEquals(routeOf(manyAtBoard).surface,
      Some(Surface.BoardDecision(manyAtBoard, many)))

  test("a board-presented decision with nothing on the table stays in the " +
      "panel"):
    val buttons = DecisionQueryState.ChooseOne(Vector(
      DecisionOptionState("button", "finish", "Finish")), heading)
    val decision = parked("campaign", "campaign.attacker-plan", buttons)
      .copy(presentation = "board")
    assertEquals(routeOf(decision).surface,
      Some(Surface.ChooseOne(decision, buttons)))

  test("a panel-presented decision stays in the panel"):
    val decision = parked("muster", "muster.source", one)
    assertEquals(decision.presentation, "panel")
    assertEquals(routeOf(decision).surface,
      Some(Surface.ChooseOne(decision, one)))
```

Update the suite's doc comment: "pawn placement to the board" becomes "board-presented decisions to the board".

- [ ] **Step 2: Write the failing panel tests**

Create `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import ParkedDecision.{Routed, Surface}
import org.scalajs.dom

/** The Action panel of a board decision, under jsdom: the summary, the
  * locked and picked rows with their tally values, the totals, the panel-only
  * options, the Decide buttons, and Clear and Confirm.
  */
class BoardDecisionPanelSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def site(id: String) = GameSite(id, s"Site $id", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 1)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(site("plains"),
      site("hills"), site("marsh")))))
  private def option(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val origin = option("plains").copy(state = "locked")
  private val force = DecisionTallyState("Defending force", "warbands",
    Vector(DecisionTallyValueState("site", "plains", 2),
      DecisionTallyValueState("site", "hills", 1)))
  private val targets = DecisionQueryState.ChooseMany(
    Vector(origin, option("hills"), option("marsh")), 0, 2,
    Some("Also target these sites"), tallies = Vector(force),
    summary = Some(DecisionSummaryState("Target", "site", "sites")))
  private val decision = WalkerDecisionState("campaign", "campaign.targets",
    "decide", query = Some(targets), presentation = "board")

  private def pane(ui: RecordingControls, query: ParkedDecision.BoardForm,
      parked: WalkerDecisionState,
      draft: Option[BoardSelectionDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    val drafts = SessionDrafts.empty.copy(context = Some(context),
      board = draft)
    ParkedDecision.render(table, ServerUiSupport.viewerPresentation(table,
      "red"), Routed(Some(Surface.BoardDecision(parked, query)), None),
      canControl = true, panel, drafts, ui)
    panel

  private def picked(keys: String*) = Some(BoardSelectionDraft(context,
    "campaign.targets", targets, keys.toVector))

  test("the summary counts the locked and picked targets, and each row " +
      "shows its tally values"):
    val panel = pane(new RecordingControls(), targets, decision,
      picked("site:hills"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Target 2 sites")
    assertEquals(all(panel, ".board-decision-row").map(row =>
      (row.getAttribute("data-option-ref"), row.textContent)), Vector(
      ("site:plains", "Locked: Site plains2 warbands"),
      ("site:hills", "Site hills1 warbands")))
    assertEquals(all(panel, ".board-decision-total").map(_.textContent),
      Vector("Defending force3"))

  test("Confirm submits the picks, and an empty optional selection too"):
    val ui = new RecordingControls()
    as[dom.html.Button](one(pane(ui, targets, decision, picked("site:hills")),
      ".board-decision-confirm")).click()
    as[dom.html.Button](one(pane(ui, targets, decision),
      ".board-decision-confirm")).click()
    assertEquals(ui.submitted, Vector(
      Intent.ResolveWalker("campaign.targets", DecisionAnswerWire
        .ChooseManyWire(Vector(oathdigital.protocol.DecisionOptionWire(
          "site", "hills")))),
      Intent.ResolveWalker("campaign.targets",
        DecisionAnswerWire.ChooseManyWire(Vector.empty))))

  test("Confirm is disabled below the minimum and says why"):
    val needsOne = targets.copy(minOptions = 1)
    val panel = pane(new RecordingControls(), needsOne,
      decision.copy(query = Some(needsOne)))
    assert(as[dom.html.Button](one(panel, ".board-decision-confirm")).disabled)
    assertEquals(one(panel, ".board-decision-reason").textContent,
      "Choose at least 1.")

  test("Clear appears with a pick and empties the draft"):
    val ui = new RecordingControls()
    assertEquals(all(pane(ui, targets, decision), ".board-decision-clear"),
      Vector.empty)
    as[dom.html.Button](one(pane(ui, targets, decision, picked("site:hills")),
      ".board-decision-clear")).click()
    assertEquals(ui.staged, Vector(Draft.Board(picked().map(_.cleared))))

  test("a Decide button submits at once, under its own rule"):
    val ui = new RecordingControls()
    val plans = DecisionQueryState.ChooseOne(Vector(option("hills"),
      DecisionOptionState("button", "finish", "Finish battle plans")),
      Some("Choose a battle plan, or finish"))
    val panel = pane(ui, plans, decision.copy(decisionId =
      "campaign.attacker-plan", query = Some(plans)))
    assertEquals(all(panel, ".board-decision-rule").size, 1)
    val finish = as[dom.html.Button](one(panel, ".board-decision-decide"))
    assertEquals(finish.textContent, "Finish battle plans")
    finish.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "campaign.attacker-plan",
      DecisionAnswerWire.ChooseOneWire("button", "finish"))))
    assertEquals(one(panel, ".board-decision-rule-labelled").textContent,
      "Decision")

  test("an option with no place on the table is a toggle in the panel"):
    val ui = new RecordingControls()
    val elsewhere = DecisionOptionState("relic", "cup", "Cup")
    val query = targets.copy(options = targets.options :+ elsewhere)
    val panel = pane(ui, query, decision.copy(query = Some(query)))
    val toggle = as[dom.html.Button](one(panel,
      ".board-decision-loose-option"))
    assertEquals(toggle.textContent, "Cup")
    toggle.click()
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("relic:cup")))
```

- [ ] **Step 3: Rewrite `BoardSurfaceSuite.scala` for the board decision**

Keep its fixtures (`fox`, `woods`, `city`, `red`, `woodsOption`, `cityOption`, `context`, `placeAtWoods`, `projection`, `site`, `SelectingView`) and the Travel test "a card click inside a board-selection target does not choose it". Change:

```scala
  private val query = DecisionQueryState.ChooseOne(
    Vector(woodsOption, cityOption), Some("Choose your starting site"))
  private val decision = WalkerDecisionState("setup", "setup.pawn-placement.red",
    "decide", query = Some(query), presentation = "board")
  private val surface = Surface.BoardDecision(decision, query)
  private val woodsDraft = BoardSelectionDraft(context, decision.decisionId,
    query, Vector("site:site:woods"))

  private def world(ui: RecordingControls,
      draft: Option[BoardSelectionDraft] = None,
      targets: Option[BoardTargetSelectionState] = None,
      board: Boolean = true): dom.Element =
    val drafts = SessionDrafts.empty.copy(context = Some(context),
      board = draft, boardTargets = targets)
    WorldBoardRenderer.world(projection(), if board then
      BoardOptions.of(surface, drafts, canControl = true, ui)
    else BoardOptions.none, drafts, ui)

  private def pane(ui: RecordingControls,
      draft: Option[BoardSelectionDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    ParkedDecision.render(projection(),
      ServerUiSupport.viewerPresentation(projection(), "red"),
      Routed(Some(surface), None), canControl = true, panel,
      SessionDrafts.empty.copy(context = Some(context), board = draft), ui)
    panel
```

The Travel test calls `world(ui, targets = Some(targets), board = false)`. Replace every other test with:

```scala
  test("a click on a card inside a site reads the card, not the site"):
    val ui = new RecordingControls()
    click(one(world(ui), "button.card-face"))
    assertEquals(ui.staged, Vector.empty)
    assertEquals(ui.submitted, Vector.empty)

  test("Enter on a card inside a site does not pick the site"):
    val ui = new RecordingControls()
    press(one(world(ui), "button.card-face"), "Enter")
    assertEquals(ui.staged, Vector.empty)

  test("a site click drafts the pick and submits nothing"):
    val ui = new RecordingControls()
    click(site(world(ui), "Deep Woods"))
    assertEquals(ui.drafts.board, Some(woodsDraft))
    assertEquals(ui.submitted, Vector.empty)

  test("a second click on the drafted site clears the pick"):
    val ui = new RecordingControls()
    click(site(world(ui, Some(woodsDraft)), "Deep Woods"))
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector.empty))

  test("a click on another site moves the pick"):
    val ui = new RecordingControls()
    click(site(world(ui, Some(woodsDraft)), "Ancient City"))
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("site:site:city")))

  test("the drafted site is drawn selected"):
    val root = world(new RecordingControls(), Some(woodsDraft))
    assert(site(root, "Deep Woods").classList
      .contains("board-option-selected"))
    assertEquals(site(root, "Deep Woods").getAttribute("aria-pressed"), "true")
    assertEquals(site(root, "Ancient City").getAttribute("aria-pressed"),
      "false")

  test("the pane's Confirm waits for a pick, then submits it"):
    val ui = new RecordingControls()
    val empty = pane(ui)
    assertEquals(one(empty, "h2").textContent, "Choose your starting site")
    assertEquals(one(empty, ".board-decision-summary").textContent,
      "Choose on the board.")
    assert(one(empty, ".board-decision-confirm")
      .asInstanceOf[dom.html.Button].disabled)
    val drafted = pane(ui, Some(woodsDraft))
    assertEquals(one(drafted, ".board-decision-summary").textContent,
      "Deep Woods")
    val confirm = one(drafted, ".board-decision-confirm")
      .asInstanceOf[dom.html.Button]
    assertEquals(confirm.textContent, "Confirm")
    confirm.click()
    assertEquals(ui.submitted, Vector(placeAtWoods))
```

Delete the old reconcile test; `BoardSelectionDraftSuite` covers it. Update the class doc: "A site is picked by clicking it ... a pick waits in a board draft for the action pane's Confirm button." In `SessionDraftsSuite.scala` (near line 71) build the board draft as `BoardSelectionDraft(context, "forge-9", ForgeFixture.query, Vector("site:site:a"))`.

Five more suites call `world` with Task 5's signature. In `BoardOptionsRenderSuite`, `DiscardPileSuite`, `PanelPlacementSuite`, `SiteBoxLayoutSuite` and `SuitGlyphSuite`, replace `WorldBoardRenderer.world(<projection>, None, canControl = true, <drafts>, <controls>)` (and, in `BoardOptionsRenderSuite`, its trailing `options` argument) with `WorldBoardRenderer.world(<projection>, <options or BoardOptions.none>, <drafts>, <controls>)`.

- [ ] **Step 4: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ParkedDecisionSuite oathdigital.frontend.BoardDecisionPanelSuite oathdigital.frontend.BoardSurfaceSuite oathdigital.frontend.SessionDraftsSuite"`
Expected: compilation fails: `Surface.BoardDecision`, `BoardForm`, `BoardDecisionPanel` and `BoardOptions.of` are not found.

- [ ] **Step 5: Switch the drafts**

In `SessionDrafts.scala`: the field is `board: Option[BoardSelectionDraft]`, `reconcile` uses `BoardSelectionDraft.reconcile(board, context, projection.walkerDecision)`, `Draft.Board(value: Option[BoardSelectionDraft])`, and the `Draft` doc's "a second click on the picked site clears the pick" stays true. Delete `WalkerBoardDraft.scala` with `git rm`.

- [ ] **Step 6: Route board decisions**

In `ParkedDecision.scala`:

- Delete `pawnPlacementDecisionIdPrefix` and its mention in the ids doc comment.
- Add after `SelectionForm`:

```scala
  /** The two forms a board decision answers: a pick of one, or of several. */
  type BoardForm = DecisionQueryState.ChooseOne | DecisionQueryState.ChooseMany
```

- Replace `case Board(...)` and its doc with:

```scala
    /** A choose-one or choose-many answered on the table (board decisions
      * design): its options are drawn where they lie, by `BoardOptions`,
      * and the action pane states the selection and confirms it. Every
      * board-presented decision with an option on the table is routed here.
      */
    case BoardDecision(decision: WalkerDecisionState, query: BoardForm)
```

- In `render`, replace the `Surface.Board` case with `case surface: Surface.BoardDecision => BoardDecisionPanel.render(surface, value, drafts, canControl, panel, controls)`, and in its doc replace the sentence about the board surface with: "A board decision is drawn twice over: its options by the table's renderers through `BoardOptions`, its prompt and Confirm by the pane here."
- `surface` passes `value` to `decisionSurface`, which passes it to `formSurface(decision, query, value, showGameplayControls)`.
- In `formSurface`, replace the pawn-placement case with these two cases, first in the match:

```scala
      // On the table: the decision says it is answered there, and the table
      // draws at least one of its options.
      case one: DecisionQueryState.ChooseOne if decision.kind == "decide" &&
          decision.action != "recover" && onTable(decision, one.options, value) =>
        Some(Surface.BoardDecision(decision, one))
      case many: DecisionQueryState.ChooseMany
          if onTable(decision, many.options, value) =>
        Some(Surface.BoardDecision(decision, many))
```

and add:

```scala
  private def onTable(decision: WalkerDecisionState,
      options: Vector[DecisionOptionState], value: GameProjection): Boolean =
    decision.presentation == "board" &&
      options.exists(BoardHomes.has(value, _))
```

- Update the object's doc where it says which surfaces exist, if it lists `Board`.

- [ ] **Step 7: Build the options from the surface, and draw the table from them**

In the `BoardOptions` companion add:

```scala
  /** What a pick on the table or in the panel does: stage the board draft
    * after the click, in the session's current context. Without a context
    * (a seat change's interim frame) or without control it does nothing.
    */
  def stager(surface: ParkedDecision.Surface.BoardDecision,
      drafts: SessionDrafts, canControl: Boolean,
      controls: TableControls): DecisionOptionState => Unit =
    option => if canControl then drafts.context.foreach(context =>
      controls.stage(Draft.Board(Some(BoardSelectionDraft.click(drafts.board,
        context, surface.decision.decisionId, surface.query, option)))))

  def of(surface: ParkedDecision.Surface.BoardDecision, drafts: SessionDrafts,
      canControl: Boolean, controls: TableControls): BoardOptions =
    new BoardOptions(BoardOptionViews.of(surface.decision, surface.query,
      drafts.board), stager(surface, drafts, canControl, controls))
```

In `WorldBoardRenderer.world`, the signature becomes `world(value: GameProjection, options: BoardOptions, drafts: SessionDrafts, controls: TableControls)`. Delete `siteOption`, the `siteOption.foreach { ... }` block and the board half of `isSelected`; the site box is now:

```scala
       val selected = drafts.boardTargets.exists(_.selected(siteTarget))
       val control = element("article", siteTargetClasses(candidate.nonEmpty,
         selected))
       control.setAttribute("aria-label", site.label)
       control.setAttribute("data-target-ref", siteTarget.stableKey)
       if candidate.nonEmpty then
         control.setAttribute("aria-pressed", selected.toString)
```

followed by the unchanged `candidate.foreach` and Task 5's `options.decorate`. Replace `world`'s doc with: "The World pane. `options` draws the parked board decision onto its sites, cards, pawns, banks and banners; the board targets of an activated Travel still pick sites through `pickable`."

In `TableScreen.scala`, replace the `(WorldBoardRenderer.players(...), WorldBoardRenderer.world(...), decisionKey)` tuple with:

```scala
        val options = routed.surface.collect {
          case board: ParkedDecision.Surface.BoardDecision => BoardOptions.of(
            board, drafts, view.controlsAvailable, controls)
        }.getOrElse(BoardOptions.none)
        (WorldBoardRenderer.players(value, selectedPlayer, options),
          WorldBoardRenderer.world(value, options, drafts, controls),
          decisionKey)
```

In `WalkerPanelSupport.scala`, delete `renderBoardPanel` and its doc comment.

- [ ] **Step 8: Write `BoardDecisionPanel.scala`**

```scala
package oathdigital.frontend

import org.scalajs.dom

/** The Action panel of a board decision (board decisions design, "Action
  * panel"). Top to bottom: the prompt, the summary line, a row per locked
  * and picked option with its tally values, the tally totals, the options
  * with no place on the table, the Decide buttons under an unlabelled rule,
  * then a rule labelled "Decision" over Clear and Confirm. The table draws
  * the options themselves, through `BoardOptions`.
  */
private[frontend] object BoardDecisionPanel:
  import ServerUiSupport.{button, element, text}

  def render(surface: ParkedDecision.Surface.BoardDecision,
      value: GameProjection, drafts: SessionDrafts, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val decision = surface.decision
    val query = surface.query
    val draft = drafts.board.filter(_.decisionId == decision.decisionId)
    val picks = draft.fold(Vector.empty[String])(_.keys)
    val views = BoardOptionViews.of(decision, query, draft)
    val shown = BoardOptionViews.options(query)
    val stage = BoardOptions.stager(surface, drafts, canControl, controls)
    def label(option: DecisionOptionState): String =
      if option.kind == "player" then value.players
        .find(_.playerId == option.id).map(_.displayName).getOrElse(option.label)
      else option.label

    WalkerPanelSupport.rollFeedback(decision, panel)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(text("p", "board-decision-summary", query match
      case many: DecisionQueryState.ChooseMany =>
        val count = picks.size + many.options.count(_.state == "locked")
        many.summary.fold(s"$count chosen")(_.of(count))
      case _ => draft.flatMap(_.picked.headOption).fold(
        "Choose on the board.")(label)))

    // Decided already: a choose-many's locked options, a choose-one's answers
    // so far. Then what this draft holds.
    val locked = (query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
      case _ => Vector.empty) ++ shown.filter(_.state == "locked")
    val chosen = draft.fold(Vector.empty[DecisionOptionState])(_.picked)
    if locked.nonEmpty || chosen.nonEmpty then
      val rows = element("ul", "board-decision-rows")
      locked.foreach(option => rows.appendChild(row(query, option,
        label(option), locked = true)))
      chosen.foreach(option => rows.appendChild(row(query, option,
        label(option), locked = false)))
      panel.appendChild(rows)

    query match
      case many: DecisionQueryState.ChooseMany => many.tallies.foreach { tally =>
        val sum = BoardOptionViews.total(many, tally, picks)
        val over = tally.limit.exists(sum > _)
        val line = element("p", if over then
          "board-decision-total board-decision-total-over"
          else "board-decision-total")
        line.appendChild(text("span", "", tally.label))
        line.appendChild(text("b", "",
          tally.limit.fold(sum.toString)(limit => s"$sum / $limit")))
        panel.appendChild(line)
      }
      case _ => ()

    // A choose-one's buttons answer at once; every other option the table
    // does not draw is picked here, like a board option.
    val decides = query match
      case _: DecisionQueryState.ChooseOne => shown.filter(_.kind == "button")
      case _ => Vector.empty
    val loose = shown.filter(option => !decides.contains(option) &&
      option.state != "locked" && !BoardHomes.has(value, option))
    loose.foreach { option =>
      val key = BoardOptionViews.key(option)
      val found = views.get(key)
      val toggle = button(label(option), "board-decision-loose-option")
      toggle.setAttribute("data-option-ref", key)
      toggle.setAttribute("aria-pressed",
        found.exists(_.state == OptionState.Selected).toString)
      toggle.disabled = !canControl ||
        found.exists(_.state == OptionState.Unavailable)
      found.flatMap(_.reason).foreach(toggle.setAttribute("title", _))
      toggle.onclick = _ => stage(option)
      BoardOptions.hoverSync(toggle, key)
      panel.appendChild(toggle)
    }
    if decides.nonEmpty then
      panel.appendChild(element("hr", "board-decision-rule"))
      decides.foreach { option =>
        val decide = button(option.label, "board-decision-decide")
        decide.disabled = !canControl
        decide.onclick = _ => controls.submitCommand(
          WalkerPanelSupport.resolveChooseOneCommand(decision, option))
        panel.appendChild(decide)
        WalkerPanelSupport.withholds(decide, option, panel)
      }

    panel.appendChild(text("p", "board-decision-rule-labelled", "Decision"))
    if picks.nonEmpty then
      val clear = button("Clear", "board-decision-clear")
      clear.disabled = !canControl
      clear.onclick = _ => controls.stage(Draft.Board(draft.map(_.cleared)))
      panel.appendChild(clear)
    // An optional choose-many confirms the empty selection, so Confirm needs
    // a draft even before the first pick.
    val current = draft.orElse(drafts.context.map(context =>
      BoardSelectionDraft(context, decision.decisionId, query, Vector.empty)))
    val blocked = BoardOptionViews.blocker(query, picks)
    val confirm = button("Confirm", "board-decision-confirm")
    confirm.disabled = !canControl || blocked.nonEmpty || current.isEmpty
    confirm.onclick = _ =>
      current.flatMap(_.command).foreach(controls.submitCommand)
    panel.appendChild(confirm)
    blocked.foreach(reason =>
      panel.appendChild(text("p", "board-decision-reason", reason)))

  /** One locked or picked option: its name, then its tally values and what
    * the engine annotated on it, such as a battle plan's price.
    */
  private def row(query: DecisionQueryState, option: DecisionOptionState,
      name: String, locked: Boolean): dom.Element =
    val key = BoardOptionViews.key(option)
    val item = element("li", if locked then
      "board-decision-row board-decision-row-locked"
      else "board-decision-row")
    item.setAttribute("data-option-ref", key)
    item.appendChild(text("span", "",
      if locked then s"Locked: $name" else name))
    val values = (query match
      case many: DecisionQueryState.ChooseMany => many.tallies.flatMap(tally =>
        tally.values.find(value => BoardOptionViews.key(value.kind, value.id) ==
          key).map(value => s"${value.value} ${tally.unit}"))
      case _ => Vector.empty) ++ option.details
    if values.nonEmpty then item.appendChild(text("span",
      "board-decision-row-values", values.mkString(" · ")))
    BoardOptions.hoverSync(item, key)
    item
```

`WalkerPanelSupport.rollFeedback`, `decisionHeading` and `resolveChooseOneCommand` are already `private[frontend]`; if `decisionHeading` takes only some query types, widen its parameter to `DecisionQueryState`.

- [ ] **Step 9: Run the suites to verify they pass**

Run: `./sbtw "frontend/test"`
Expected: PASS. A suite that still builds `Surface.Board` or `WalkerBoardDraft` fails to compile; move it to the board decision exactly as Step 3 does.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardSurfaceSuite.scala frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala frontend/src/test/scala/oathdigital/frontend/PanelPlacementSuite.scala frontend/src/test/scala/oathdigital/frontend/SiteBoxLayoutSuite.scala frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala
git rm frontend/src/main/scala/oathdigital/frontend/WalkerBoardDraft.scala
git commit -m "feat(frontend): answer board decisions on the table, with a panel that states and confirms the selection"
```

---

### Task 7: The option styles, checked in the browser

Spec slice 2, the look. No suite reads `styles.css`, so this task is verified in the browser over a scratch database.

**Files:**
- Modify: `frontend/styles.css` (after the `.board-target-control` rules near line 260, and after the decision panel rules)

**Interfaces:**
- Consumes: the class names Tasks 5 and 6 set.

- [ ] **Step 1: Load the design context**

Run `~/.claude/skills/impeccable/scripts/impeccable context` from the worktree root, then read `~/.claude/skills/impeccable/reference/craft-floor.md`.

- [ ] **Step 2: Add the option states and the inspect button**

After the `.board-target-control[aria-pressed="true"]::before` rule, add. The colours are the mock-up's, which reuse `.board-target`'s brass and blue:

```css
/* Board decisions (spec 2026-10-03): an option drawn where it lies. Nothing
   that is not an option changes, and only an unavailable option is dimmed. */
.board-option { position: relative; cursor: pointer;
  border-color: #d2a85e; box-shadow: inset 0 0 0 1px #d2a85e; }
.board-option:hover, .board-option:focus-visible, .board-option-hover {
  border-color: #fff2d6; box-shadow: 0 0 0 3px rgba(210, 168, 94, 0.35); }
.board-option-selected, .board-option-locked {
  border-color: #8ecbff; box-shadow: inset 0 0 0 2px #8ecbff;
  background: #243442; }
.board-option-locked { cursor: default; }
.board-option-unavailable { opacity: 0.45; cursor: not-allowed;
  box-shadow: none; }
/* The badge sits inside the corner: card faces and site boxes clip what
   overflows them. Small marks (a name, a pawn, a banner) carry the ring
   alone. */
.card-face.board-option-selected::before, .site.board-option-selected::before,
.card-face.board-option-locked::before, .site.board-option-locked::before {
  position: absolute; top: 3px; left: 3px; z-index: 1;
  display: flex; align-items: center; justify-content: center;
  width: 20px; height: 20px; border-radius: 50%;
  background: #8ecbff; color: #10202c; font-size: 12px; font-weight: 800; }
.card-face.board-option-selected::before,
.site.board-option-selected::before { content: "✓"; }
.card-face.board-option-locked::before,
.site.board-option-locked::before { content: "🔒"; font-size: 11px; }
/* A card and its inspect button share one box: a button may not hold a
   button. The button sits over the card's bottom-right corner, where no
   card content is drawn; the top-right holds defense dice and the pip. */
.board-option-card { position: relative; display: inline-flex;
  flex: 0 0 auto; }
.board-option-inspect { position: absolute; right: 4px; bottom: 4px;
  z-index: 1; width: 22px; height: 22px; padding: 0;
  border: 1px solid #d2a85e; border-radius: 50%; background: #211d17;
  color: #fff2d6; font: inherit; font-size: 12px; font-weight: 800;
  line-height: 1; cursor: pointer; }
.board-option-inspect:hover, .board-option-inspect:focus-visible {
  outline: 2px solid #fff2d6; outline-offset: 1px; }
```

- [ ] **Step 3: Add the panel**

After the `.walker-many-option[aria-pressed="true"]::before` rule (near line 559), add:

```css
.board-decision-summary { margin: 0 0 8px; color: #fff2d6; font-weight: 700; }
.board-decision-rows { margin: 0 0 6px; padding: 0; list-style: none; }
.board-decision-row, .board-decision-total { display: flex;
  justify-content: space-between; gap: 8px; margin: 0; padding: 3px 0;
  border-bottom: 1px dotted #40382c; font-size: 13px; }
.board-decision-row.board-option-hover { background: #243442; }
.board-decision-row-values { color: #c4b89f; text-align: right; }
.board-decision-total b { color: #fff2d6; }
.board-decision-total-over b { color: #e06a6a; }
.board-decision-rule { margin: 12px 0 6px; border: 0;
  border-top: 1px solid #40382c; }
.board-decision-rule-labelled { display: flex; align-items: center; gap: 8px;
  margin: 12px 0 6px; color: #b3a184; font-size: 12px;
  letter-spacing: 0.06em; text-transform: uppercase; }
.board-decision-rule-labelled::before, .board-decision-rule-labelled::after {
  content: ""; flex: 1; border-top: 1px solid #40382c; }
.board-decision-decide, .board-decision-loose-option, .board-decision-clear {
  display: block; width: 100%; min-height: 34px; margin: 6px 0;
  border: 1px solid #675a43; border-radius: 6px; background: #211d17;
  color: #f5ecd7; font: inherit; font-size: 13px; }
.board-decision-loose-option[aria-pressed="true"] { border-color: #8ecbff;
  background: #243442; }
.board-decision-confirm { display: block; width: 100%; min-height: 40px;
  border: 0; border-radius: 6px; background: #d2a85e; color: #17140f;
  font: inherit; font-size: 14px; font-weight: 800; }
.board-decision-confirm:disabled { background: #4a4232; color: #a99f8c; }
.board-decision-reason, .walker-choice-reason { margin: 5px 0 0;
  color: #f1c781; font-size: 12px; }
```

If `.site-readonly { cursor: default; }` wins over `.board-option` on a site box (same specificity, earlier rule), the order above already puts `.board-option` later; confirm in Step 5.

- [ ] **Step 4: Run the design detector**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css` from the worktree root. Compare against the findings on `main` by line: fix anything new in the lines this task added, except the deliberate dimming of `.board-option-unavailable`.

- [ ] **Step 5: Check it in the browser over a scratch database**

From the worktree root, with `S` the session scratchpad plus `/db-board-decisions`:

```bash
mkdir -p $S && cp var/oathdigital.script var/oathdigital.properties var/oathdigital.log $S/ && cp -r var/oathdigital.lobs $S/
```

```bash
./sbtw "frontend/fastLinkJS" "runMain oathdigital.server.OathServer --port 8081 --database-path $S/oathdigital"
```

Run the second command through Bash with `run_in_background` and wait for "Start completed" in its log. Open `http://127.0.0.1:8081/?mode=server` in the built-in browser at 1440×900 and press "New game". Setup's pawn placement is a board decision. Check in one batched pass, with `read_page`, `javascript_tool` and one screenshot:

1. Every offered site has `board-option`, the brass ring, and `data-option-ref`; sites not offered look as before.
2. A site click marks it selected with the check badge inside its top-left corner; the panel's summary names it; Confirm becomes enabled and is the primary brass button under the "Decision" rule.
3. Hovering a site rings it; clicking a card inside a site opens the card summary and does not pick the site.
4. Confirm places the pawn; the next player's placement highlights again.
5. At 1024×768, the same, with no horizontal scroll in the panes.
6. `read_console_messages` with `onlyErrors` shows nothing new.

Fix every failure in one batch, reload, confirm once, and stop.

- [ ] **Step 6: Commit**

```bash
git add frontend/styles.css
git commit -m "style(frontend): draw board options as available, selected, locked and unavailable, and the board decision panel"
```

Leave the scratch server running for Task 12, or stop it with `lsof -ti :8081 | xargs kill` and call `resize_window` with preset `desktop`.

---

### Task 8: A pruned option says which power hid it

Spec slice 3. Both pruning paths keep an option a power's note explains, as unavailable with that note; an option no note explains is still removed.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala:87-131,239-266`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:4,419-451,502-508`
- Test: `src/test/scala/oathdigital/gameplay/UnavailableReasonsSuite.scala` (new); the suites Step 6 finds

**Interfaces:**
- Consumes: Task 1's `WalkerSearch.narrowed(decide, permitted, explain)`, `explained`, `choosable`, `OptionReason.Noted`.
- Produces: `WalkerPowerGather.lookAheadNoted(root, decide, narrowed, state, activePlayer, powers, answered, procedure): Vector[(DecisionOptionRef, PowerNoted)]`, replacing `lookAheadNotes`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/UnavailableReasonsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.PowerNoteHideSuite.{ForbiddingPower, HidingPower,
  hid, hideSource}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Outcomes.parked

/** An option a power hides is still shown, as unavailable, when the power
  * says why (board decisions design, "Unavailable options"): the reason is
  * the power's own note. An option no note explains is removed as before,
  * and an unavailable option is never an answer.
  */
class UnavailableReasonsSuite extends munit.FunSuite:
  private val ready = Table.start.ready
  private val actor = ready.game.current.turn.activePlayer
  private val window = PowerWindow.ChallengeAmountSelection
  private val hider = PowerId("test.hider")
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def option(id: String) = DecisionOption.Site(site(id))
  private val pick = Sequence(Vector[Operation](Decide("pick", actor,
    DecisionQuery.ChooseOne(Vector(option("a"), option("b"))),
    window = Some(window))))

  private def asked(tree: Operation, powers: WalkerPowers): DecisionQuery =
    val pending = parked(ProcedureWalker.advance(ready, tree, None, powers)).tree
    ProcedureWalker.openDecisions(ready, tree, pending, powers).head.query

  test("an option a power forbids and explains is shown unavailable"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")))))
    assertEquals(asked(pick, powers), DecisionQuery.ChooseOne(Vector(
      option("a"), DecisionOption.Unavailable(option("b"),
        OptionReason.Noted(hider, hid(site("b")))))))

  test("an option a power forbids silently is removed"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")),
      says = false)))
    assertEquals(asked(pick, powers),
      DecisionQuery.ChooseOne(Vector(option("a"))))

  test("an option the look-ahead hides is shown with the note of the " +
      "restriction it would break, and is still refused"):
    val nested = PowerWindow.CampaignActionEligibility
    val forbidder = PowerId("test.forbidder")
    val powers = WalkerPowers(Vector(ForbiddingPower(forbidder, nested)))
    def button(key: String) =
      DecisionOption.Button(DecisionOptionRef.Button(key), key)
    val tree = Sequence(Vector[Operation](Decide("ask", actor,
        DecisionQuery.ChooseOne(Vector(button("yes"), button("no")))),
      Branch((_, pending) => if pending.answered.exists {
        case Answered("ask", DecisionAnswer.ChooseOneAnswer(ref), _) =>
          ref == DecisionOptionRef.Button("yes")
        case _ => false
      } then Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))
    assertEquals(asked(tree, powers), DecisionQuery.ChooseOne(Vector(
      DecisionOption.Unavailable(button("yes"), OptionReason.Noted(forbidder,
        PowerNote(hideSource, "forbade", Vector.empty))), button("no"))))
    val pending = parked(ProcedureWalker.advance(ready, tree, None, powers)).tree
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered("ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("yes")), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        "decision ask does not offer the selected option")))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.UnavailableReasonsSuite"`
Expected: FAIL: the hidden options are absent rather than unavailable.

- [ ] **Step 3: Explain in the window fold**

In `WalkerPowerGather.scala`, add `OptionReason` to the `oathdigital.model` import. Replace `optionRefs`'s body with `WalkerSearch.choosable(decide)` and its doc with "The options of a choose-one or choose-many decision that a player may choose, in order." Add:

```scala
  /** The note of the first power in `restrictions` that forbids `ref`, when
    * that power says why.
    */
  private def firstNote(restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower])(ref: DecisionOptionRef)
      : Option[PowerNoted] =
    restrictions.find { case (id, restriction) =>
      restriction.fn(ctxFor(byId(id)), ref).nonEmpty
    }.flatMap { case (id, restriction) =>
      restriction.note(ctxFor(byId(id)), ref)
        .map(PowerNoted(id, _, covers = false))
    }
```

Make `hiddenNotes` use it:

```scala
    if restrictions.isEmpty then Vector.empty
    else ops.collect { case decide: Decide => decide }.flatMap(decide =>
      optionRefs(decide).flatMap(firstNote(restrictions, ctxFor, byId)))
```

and in `restrictOptions` replace `WalkerSearch.narrowed(decide, permitted).toVector` with:

```scala
        case decide: Decide => WalkerSearch.narrowed(decide, permitted, ref =>
          firstNote(restrictions, ctxFor, byId)(ref).map(noted =>
            OptionReason.Noted(noted.power, noted.note))).toVector
```

Update `restrictOptions`' doc: "Removes the forbidden options from every `Decide` in `ops`, keeping one as unavailable when the restriction that forbids it notes why; see [[OptionRestriction]] for what happens when nothing is left."

- [ ] **Step 4: Keep the look-ahead's notes with their options**

Rename `lookAheadNotes` to `lookAheadNoted`, returning `Vector[(DecisionOptionRef, PowerNoted)]`:

```scala
  /** The notes the look-ahead writes for the options `narrowed` no longer
    * offers, each with its option: the note of the restriction whose
    * violation answering it would add. Empty without a power that restricts,
    * since only a restriction can explain a hidden option.
    */
  def lookAheadNoted(root: Operation, decide: Decide, narrowed: Decide,
      state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef])
      : Vector[(DecisionOptionRef, PowerNoted)] =
    val offered = optionRefs(narrowed).toSet
    val hidden = optionRefs(decide).filterNot(offered)
    if hidden.isEmpty || !powers.hasRestrictions then Vector.empty
    else
      val quiet = powers.quiet
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def found(answers: Vector[Answered]): Vector[Attributed] =
        attributed(root, quiet, seen, activePlayer, answers)._1
      val baseline = found(answered).map(_.violation).toSet
      hidden.flatMap { ref =>
        val answer = decide.query match
          case _: DecisionQuery.ChooseMany =>
            DecisionAnswer.ChooseManyAnswer(Vector(ref))
          case _ => DecisionAnswer.ChooseOneAnswer(ref)
        found(answered :+ Answered(decide.decisionId, answer, decide.owner))
          .find(breach => !baseline(breach.violation))
          .flatMap(breach => breach.restriction.note(breach.ctx, ref)
            .map(note => ref -> PowerNoted(breach.power, note, covers = false)))
      }
```

- [ ] **Step 5: Narrow with the explanations, and journal their notes once**

In `ProcedureWalker.scala`, add `DecisionOptionRef` and `OptionReason` to the `oathdigital.model` import, and add inside `object ProcedureWalker`:

```scala
  /** A decision as the look-ahead leaves it: narrowed, with each option it
    * hid that a restriction's note explains kept as unavailable, and those
    * notes, which the walk journals when it reaches the decision fresh.
    */
  private final case class Narrowed(decide: Decide, notes: Vector[PowerNoted])
```

Replace `narrowAt` with:

```scala
  /** `decide` narrowed to the options from which some path reaches a legal
    * end (global operation restrictions design, "Lazy pruning"), each hidden
    * option a restriction's note explains kept as unavailable, or as
    * declared when the powers do not search. */
  private def narrowAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Option[Narrowed]] =
    if !ctx.powers.probing then Right(Some(Narrowed(decide, Vector.empty)))
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path)).map(_.map {
      cut =>
        val noted = WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
          ctx.state, ctx.activePlayer, ctx.powers, ctx.answered, ctx.procedure)
        val reasons: Map[DecisionOptionRef, OptionReason] = noted.map(
          (ref, note) => ref -> OptionReason.Noted(note.power, note.note)).toMap
        Narrowed(WalkerSearch.explained(decide, cut, reasons), noted.map(_._2))
    })
```

In `runLeaf`, replace the `case Some(narrowed) =>` arm with:

```scala
          case Some(narrowed) =>
            // Reached fresh, so this is the only time it is asked this pass.
            val hidden = if cursor.nonEmpty then Vector.empty else narrowed.notes
            runNarrowed(narrowed.decide, ctx.copy(events = ctx.events ++ hidden),
              path, cursor, resume, contributions, strict)
```

and in `narrowParked` replace the last line with `narrowAt(decide, ctx, pending.at).toOption.flatten.map(_.decide).getOrElse(decide)`.

- [ ] **Step 6: Run the new suite, then the whole server suite**

Run: `./sbtw "testOnly oathdigital.gameplay.UnavailableReasonsSuite oathdigital.gameplay.PowerNoteHideSuite oathdigital.gameplay.RestrictionLookAheadSuite"`
Expected: PASS.

Run: `./sbtw test`. Suites of real restricting powers that note what they hide (Lost Tongue, Forgotten Vault, the Fortress rules, Circlet of Command, the Travel site powers, and any other the run names) may fail where they assert a forbidden option is absent from a parked query. For each failure of that kind, change only the assertion's reading of the options from `.options.map(_.ref)` (or `.options`) to `.options.filter(_.available).map(_.ref)` (or `.filter(_.available)`), which keeps its meaning: the option is not on offer. Do not change any other failure; stop and report it instead. Rerun until PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/UnavailableReasonsSuite.scala
git commit -m "feat(walker): show an option a power hides as unavailable, with the power's note as the reason"
```

Stage, by explicit path, each suite Step 6 changed, in the same commit.

---

### Task 9: Campaign targets show what is decided, what they add up to, and what is not on offer

Spec slice 4, targets half.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala:8-20,63-78`
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignTargets.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala:58-60,155-169`
- Test: `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`

**Interfaces:**
- Consumes: Task 1's `ChooseMany.locked/tallies/summary`, `DecisionTally`, `DecisionSummary`, `DecisionOption.Unavailable`, `OptionReason.Written`.
- Produces:
  - `CampaignBattle.defenseOf(catalog: ExecutableCatalog, target: DecisionOptionRef): Int`, `CampaignBattle.forceOf(ready: ReadyGame, target: DecisionOptionRef): Int`, `CampaignBattle.targetRefs(setup: CampaignSetup): Vector[DecisionOptionRef]`.
  - `private[campaign] object CampaignTargets` with `DefenseLabel = "Defense dice (before plans and powers)"`, `ForceLabel = "Defending force"`, `lockedOf`, `tallies`, `summary`, `withheld`.

- [ ] **Step 1: Write the failing tests**

In `CampaignProcedureSuite.scala`, add `as` to the `oathdigital.testkit.Unwrap` import. Replace the body of "extra same-ruler sites are offered as optional targets, in map order" with:

```scala
    val b = board(extras = 2, warbands = 5, supply = 7)
    val started = accepted(start(b))
    parked.assertParked(started.state, ActionRef.Campaign, CampaignIds.targets,
      b.actor)
    val query = as[DecisionQuery.ChooseMany](parkedDecision(b, started).query)
    assertEquals(query.options.filter(_.available),
      b.extras.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))))
    assertEquals((query.min, query.max, query.heading), (0, 2,
      Some("Also target these sites ruled by the same defender")))
    val chosen = accepted(answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.last)))))
    parked.assertParked(chosen.state, ActionRef.Campaign, CampaignIds.force,
      b.actor)
```

and append:

```scala
  test("a Conquest's targets lock the origin and tally its defense and force"):
    val b = board(extras = 1, warbands = 5, supply = 7)
    val query = as[DecisionQuery.ChooseMany](
      parkedDecision(b, accepted(start(b))).query)
    val origin = DecisionOptionRef.Site(b.origin)
    val extra = DecisionOptionRef.Site(b.extras.head)
    def printed(site: SiteId) = catalog.site(site).fold(0)(_.defense)
    assertEquals(query.locked, Vector(DecisionOption.Site(origin)))
    assertEquals(query.summary, Some(DecisionSummary("Target", "site",
      "sites")))
    // Two bandits stand at each ruled site.
    assertEquals(query.tallies, Vector(
      DecisionTally("Defense dice (before plans and powers)", "defense",
        Map(origin -> printed(b.origin), extra -> printed(b.extras.head))),
      DecisionTally("Defending force", "warbands",
        Map(origin -> 2, extra -> 2))))

  test("a Conquest shows every other site as not on offer, and why"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val inPlay = base.ready.game.current.map.inPlay
    val mine = inPlay.find(site => site != base.origin &&
      !base.extras.contains(site)).get
    val b = on(base)(_.warbandsAt(mine, base.actor, 1))
    val query = as[DecisionQuery.ChooseMany](
      parkedDecision(b, accepted(start(b))).query)
    val reasons = query.options.collect {
      case DecisionOption.Unavailable(DecisionOption.Site(ref),
          OptionReason.Written(text)) => ref.id -> text }.toMap
    assertEquals(reasons.keySet,
      inPlay.filterNot(site => site == b.origin || b.extras.contains(site))
        .toSet)
    assertEquals(reasons(mine), "You rule this site")
    assert(reasons.removed(mine).values.forall(_ == "Not ruled by the defender"))

  test("a Raid locks the defender's pawn, and shows what it may not take"):
    val shared = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val cauldron = CatalogNames.relic("Cursed Cauldron")
    val sticks = CatalogNames.relic("Dowsing Sticks")
    // The rival holds Cursed Cauldron faceup and three warbands; the
    // bystander holds Dowsing Sticks faceup; the actor holds the Darkest
    // Secret.
    val b = on(shared)(_.relic(shared.rival, cauldron)
      .relic(shared.bystander, sticks).boardWarbands(shared.rival, 3)
      .darkestSecret(Some(shared.actor), secrets = 1))
    val started = accepted(start(b))
    val raid = accepted(answer(started.state, b.actor, CampaignIds.kind,
      button("raid")))
    val query = as[DecisionQuery.ChooseMany](parkedDecision(b, raid).query)
    val pawn = DecisionOptionRef.Player(b.rival)
    val relic = DecisionOptionRef.Relic(cauldron)
    assertEquals(query.locked, Vector(DecisionOption.Player(pawn)))
    assertEquals(query.options.filter(_.available),
      Vector(DecisionOption.Relic(relic)))
    assertEquals(query.summary, Some(DecisionSummary("Raid", "target",
      "targets")))
    assertEquals(query.tallies.map(_.perOption), Vector(
      Map(pawn -> 2, relic -> catalog.relics.find(_.id.value == cauldron.value)
        .fold(0)(_.defense)),
      Map(pawn -> 3, relic -> 0)))
    assertEquals(query.options.collect {
      case DecisionOption.Unavailable(inner, OptionReason.Written(text)) =>
        inner.ref -> text }, Vector(
      DecisionOptionRef.Relic(sticks) -> "Not held by the defender",
      DecisionOptionRef.Banner(Banner.PeoplesFavor) ->
        "Not held by the defender",
      DecisionOptionRef.Banner(Banner.DarkestSecret) -> "You hold this banner"))
```

`Table.relic` takes a name or an id; `catalog` is the suite's `FirstGameSetupFixture.catalog`. Keep the rival's relic: a Raid with no target option is not asked, and then there is no question to inspect.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite"`
Expected: FAIL: `locked`, `tallies` and `summary` are empty, and no option is unavailable.

- [ ] **Step 3: One source for each target's defense and force**

In `CampaignBattle.scala`, replace `printedDefense` and `defenderForce` with:

```scala
  /** The printed defense dice of the targets: a Conquest's sites, or a Raid's
    * pawn (2), each targeted relic's printed defense and each banner (3).
    */
  def printedDefense(catalog: ExecutableCatalog, setup: CampaignSetup): Int =
    targetRefs(setup).map(defenseOf(catalog, _)).sum

  /** The printed defense one target adds; the targets question tallies the
    * same numbers.
    */
  def defenseOf(catalog: ExecutableCatalog, target: DecisionOptionRef): Int =
    target match
      case DecisionOptionRef.Site(site) => catalog.site(site).fold(0)(_.defense)
      case _: DecisionOptionRef.Player => 2
      case DecisionOptionRef.Relic(relic) => catalog.relics
        .find(_.id.value == relic.value).fold(0)(_.defense)
      case _: DecisionOptionRef.Banner => 3
      case _ => 0

  /** The targets of `setup`, as the options that name them. */
  def targetRefs(setup: CampaignSetup): Vector[DecisionOptionRef] =
    setup.kind match
      case CampaignKind.Conquest => setup.targetSites.map(DecisionOptionRef.Site(_))
      case CampaignKind.Raid => setup.raidTargets.map:
        case CampaignRaidTarget.Pawn(player) => DecisionOptionRef.Player(player)
        case CampaignRaidTarget.Relic(_, relic) => DecisionOptionRef.Relic(relic)
        case CampaignRaidTarget.Banner(_, banner) =>
          DecisionOptionRef.Banner(banner)

  /** The force a defender adds to its dice: the warbands at every target, or a
    * Raid defender's board.
    */
  def defenderForce(ready: ReadyGame, setup: CampaignSetup): Int =
    setup.kind match
      case CampaignKind.Conquest => setup.targetSites
        .map(site => forceOf(ready, DecisionOptionRef.Site(site))).sum
      case CampaignKind.Raid => setup.defender match
        case CampaignDefender.Player(player) =>
          forceOf(ready, DecisionOptionRef.Player(player))
        case CampaignDefender.Bandits => 0

  /** The warbands one target brings to the defense: those at a site, or a
    * Raid defender's board for their pawn. A relic or a banner brings none.
    */
  def forceOf(ready: ReadyGame, target: DecisionOptionRef): Int =
    val current = ready.game.current
    target match
      case DecisionOptionRef.Site(site) => current.map.sites.get(site)
        .fold(0)(_.forces match
          case SiteForces.Occupied(_, count) => count
          case SiteForces.Empty => 0)
      case DecisionOptionRef.Player(player) => current.players
        .find(_.player == player).fold(0)(_.board.warbands)
      case _ => 0
```

- [ ] **Step 4: Write `CampaignTargets.scala`**

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The Campaign targets question as the table shows it (board decisions
  * design, "Campaign targets"): the target the Campaign already has, the two
  * sums a player weighs, and why each target not on offer is not.
  */
private[campaign] object CampaignTargets:
  val DefenseLabel = "Defense dice (before plans and powers)"
  val ForceLabel = "Defending force"

  /** The mandatory target: a Conquest's origin, a Raid defender's pawn. */
  def lockedOf(kind: CampaignKind, origin: SiteId,
      defender: CampaignDefender): Option[DecisionOption] =
    (kind, defender) match
      case (CampaignKind.Conquest, _) =>
        Some(DecisionOption.Site(DecisionOptionRef.Site(origin)))
      case (CampaignKind.Raid, CampaignDefender.Player(player)) =>
        Some(DecisionOption.Player(DecisionOptionRef.Player(player)))
      case _ => None

  /** The printed defense and the defending force of each target, from the
    * same sources the battle reads (`CampaignBattle`).
    */
  def tallies(catalog: ExecutableCatalog, ready: ReadyGame,
      targets: Vector[DecisionOptionRef]): Vector[DecisionTally] =
    Vector(
      DecisionTally(DefenseLabel, "defense", targets.map(target =>
        target -> CampaignBattle.defenseOf(catalog, target)).toMap),
      DecisionTally(ForceLabel, "warbands", targets.map(target =>
        target -> CampaignBattle.forceOf(ready, target)).toMap))

  def summary(kind: CampaignKind): DecisionSummary = kind match
    case CampaignKind.Conquest => DecisionSummary("Target", "site", "sites")
    case CampaignKind.Raid => DecisionSummary("Raid", "target", "targets")

  /** The targets the Campaign may not add, each with why: in a Conquest,
    * every other site in play; in a Raid, the banners the defender does not
    * hold and other players' faceup relics.
    */
  def withheld(ready: ReadyGame, actor: PlayerId, kind: CampaignKind,
      origin: SiteId, defender: CampaignDefender,
      offered: Vector[DecisionOption]): Vector[DecisionOption] =
    val current = ready.game.current
    val shown = offered.map(_.ref).toSet
    def unavailable(option: DecisionOption, reason: String): DecisionOption =
      DecisionOption.Unavailable(option, OptionReason.Written(reason))
    (kind, defender) match
      case (CampaignKind.Conquest, _) => current.map.inPlay
        .filter(site => site != origin &&
          !shown(DecisionOptionRef.Site(site)))
        .map(site => unavailable(DecisionOption.Site(
          DecisionOptionRef.Site(site)),
          if CampaignSetup.defenderAt(ready, site)
              .contains(CampaignDefender.Player(actor))
          then "You rule this site" else "Not ruled by the defender"))
      case (CampaignKind.Raid, CampaignDefender.Player(target)) =>
        val relics = current.players
          .filter(held => held.player != actor && held.player != target)
          .flatMap(_.relics.filter(_.orientation == Orientation.FaceUp))
          .map(relic => unavailable(DecisionOption.Relic(
            DecisionOptionRef.Relic(relic.id)), "Not held by the defender"))
        val banners = Banner.all
          .filter(banner => !shown(DecisionOptionRef.Banner(banner)))
          .map(banner => unavailable(DecisionOption.Banner(
            DecisionOptionRef.Banner(banner)),
            if BannerRules.holder(current, banner).contains(actor)
            then "You hold this banner" else "Not held by the defender"))
        relics ++ banners
      case _ => Vector.empty
```

`Banner.all` lists the People's Favor first, which is the order the Raid test expects. If `Table.start` gives the People's Favor to the actor, add `.peoplesFavor(None, favor = 0)` to the test's board.

- [ ] **Step 5: Build the question from it**

In `CampaignProcedure.scala`, pass the catalog: `targetsStep(catalog, actor)` in `tree`, and replace `targetsStep` with:

```scala
  /** The optional additions to the mandatory target, which is shown locked,
    * with the two sums a player weighs and why every other target is not on
    * offer.
    */
  private def targetsStep(catalog: ExecutableCatalog, actor: PlayerId)
      : Operation = Branch((ready, pending) =>
    (for
      kind <- CampaignSetup.kindOf(ready, actor, pending)
      defender <- CampaignSetup.defenderOf(ready, actor, pending, kind)
      origin <- CampaignSetup.originOf(ready, actor)
      options = CampaignSetup.targetOptions(ready, actor, kind, defender)
      if options.nonEmpty
    yield
      val locked = CampaignTargets.lockedOf(kind, origin, defender).toVector
      Vector[Operation](Decide(CampaignIds.targets, actor,
        DecisionQuery.ChooseMany(0, options.size, options ++
          CampaignTargets.withheld(ready, actor, kind, origin, defender,
            options), heading = Some(kind match {
            case CampaignKind.Conquest =>
              "Also target these sites ruled by the same defender"
            case CampaignKind.Raid =>
              "Also target the defender's faceup relics and banners"
          }), locked = locked,
          tallies = CampaignTargets.tallies(catalog, ready,
            (locked ++ options).map(_.ref)),
          summary = Some(CampaignTargets.summary(kind))),
        window = Some(PowerWindow.CampaignTargetSelection))))
      .getOrElse(Vector.empty))
```

- [ ] **Step 6: Run the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.CampaignPlanWindowSuite"`
Expected: PASS. A suite that compares the whole targets query with `assertEquals` now fails on the new fields; compare its available options instead, as Step 1 does.

Then `./sbtw test`: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignTargets.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala
git commit -m "feat(campaign): lock the mandatory target, tally defense and force, and say why other targets are not on offer"
```

---

### Task 10: Battle plans not on offer say why

Spec slice 4, plans half. The `Repeat` of a choose-one stays; a plan its user cannot pay for, or that Encirclement's force rule withholds, is shown unavailable with the reason. Played plans already show locked on the table from `answeredOptions` (Task 4's `BoardOptionViews.of`), and the plan cards already route to the board (Task 6).

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala:108-110` (`CampaignPlanOffer`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala:52-90`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala:112-120`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/EncirclementSuite.scala`

**Interfaces:**
- Consumes: Task 1's `DecisionOption.Unavailable`, `OptionReason.Written`.
- Produces:
  - `CampaignPlanOffer(source, label, costs, effects, sides = Set.empty, withheld: Option[String] = None)`.
  - `Encirclement.SmallerForce: String` = "Your force must be larger than your enemy's".
  - `PlanPrice.shortfall(violation: OathViolation): String`.
  - `PlanDriver.Run.withheld(actor: PlayerId, ref: DecisionOptionRef): Option[OptionReason]`; `Run.offered` now lists only available options.

- [ ] **Step 1: Write the failing tests**

In `PlanDriver.scala`, replace `offered` and add `withheld`:

```scala
    /** The options the parked choice offers that may be chosen. */
    def offered(actor: PlayerId): Vector[DecisionOptionRef] =
      options(actor).filter(_.available).map(_.ref)

    /** Why the parked choice shows `ref` without offering it. */
    def withheld(actor: PlayerId, ref: DecisionOptionRef)
        : Option[OptionReason] =
      options(actor).collectFirst {
        case shown: DecisionOption.Unavailable if shown.ref == ref =>
          shown.reason
      }
```

In `EncirclementSuite.scala`, in the test "a plan chosen earlier in the window counts: a Wrestlers sacrifice evens the forces", after its last assertion add:

```scala
    assertEquals(sacrificed.withheld(b.actor, ref),
      Some(OptionReason.Written("Your force must be larger than your enemy's")))
```

and append:

```scala
  test("a plan its user cannot pay for is shown with what it needs"):
    // The rival rules the origin with three warbands against two and holds
    // the title, whose defense keeps the window asked, but has no favor.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.warbandsAt(base.origin, base.rival, 3).favor(base.rival, 0))
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offers(b.actor, b.rival, CampaignIds.defenderPlan, ref))
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 1 favor; you have 0")))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.EncirclementSuite"`
Expected: FAIL: `withheld` is `None`, since the plans are left out rather than shown.

- [ ] **Step 3: Let an offer stand withheld**

In `CampaignTypes.scala`, give `CampaignPlanOffer` a last field, and say so in its doc:

```scala
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect],
    sides: Set[CampaignPlanSide] = Set.empty,
    withheld: Option[String] = None)
```

Doc addition: "`withheld` is why the plan stands but cannot be used now, such as Encirclement's force rule: the plan window shows it unavailable with that reason, and no one applies it."

In `Encirclement.scala`, replace `plan` and add the reason to the companion:

```scala
  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val offer = CampaignPlanOffer(source,
        PlanDice.label("Encirclement", context.side, Encirclement.Dice),
        Vector(CampaignPlanCost.Favor(1)),
        Vector(PlanDice.effect(context.side, Encirclement.Dice)))
      if larger(context) then offer
      else offer.copy(withheld = Some(Encirclement.SmallerForce))
    }
```

```scala
  /** Why the plan is shown but not offered. */
  val SmallerForce: String = "Your force must be larger than your enemy's"
```

and amend the class doc: "It is offered only while its user's force is strictly larger than the enemy's; otherwise it is shown withheld, with that rule as the reason."

- [ ] **Step 4: Word a payment that fails**

In `PlanPrice.scala` add:

```scala
  /** Why a plan's dry run could not pay for it, as player text. A
    * surcharge counts: the dry run pays the plan's cost and every power's
    * addition to it together.
    */
  def shortfall(violation: OathViolation): String = violation match
    case OathViolation.InsufficientFavor(required, available) =>
      s"Needs $required favor; you have $available"
    case OathViolation.InsufficientSecrets(required, available) =>
      s"Needs $required ${if required == 1 then "secret" else "secrets"}; " +
        s"you have $available"
    case _ => "Cannot be paid for now"
```

- [ ] **Step 5: Show the plans not on offer**

In `CampaignPlanChoice.scala`, bandits never apply a withheld plan:

```scala
  private def banditPlans(setup: CampaignSetup, offers: Vector[OfferedPlan],
      pass: OfferHost.Pass): Vector[Operation] = offers
    .filter(offered => offered.offer.costs.isEmpty &&
      offered.offer.withheld.isEmpty)
    .map(applicationOf(setup, _))
    .filter(application => pass.applies(application).isRight)
```

and `userPass` lists every unchosen offer, each either priced or unavailable:

```scala
  private def userPass(setup: CampaignSetup, user: PlayerId,
      offers: Vector[OfferedPlan], pending: PendingTree, pass: OfferHost.Pass)
      : Vector[Operation] =
    val decisionId = CampaignIds.planDecision(side)
    val chosen = CampaignAnswers.picks(pending, decisionId)
    def unavailable(offered: OfferedPlan, reason: String): DecisionOption =
      DecisionOption.Unavailable(CampaignPlans.optionOf(offered),
        OptionReason.Written(reason))
    val listed = offers.filterNot(offered =>
      chosen.contains(CampaignPlans.refOf(offered.offer.source)))
      .map(offered => offered.offer.withheld match
        case Some(reason) => unavailable(offered, reason)
        case None => pass.applies(applicationOf(setup, offered)) match
          case Right(operations) => PlanPrice.priced(
            CampaignPlans.optionOf(offered), offered.offer, operations)
          case Left(violation) =>
            unavailable(offered, PlanPrice.shortfall(violation)))
    val playable = listed.exists(_.available)
    if !playable && !pass.resuming then Vector.empty
    else Vector[Operation](
      Branch((_, _) => if !playable then Vector.empty
        else Vector(decision(decisionId, user, listed))),
      Branch((_, tree) => CampaignAnswers.lastPick(tree, decisionId) match {
        case None => Vector.empty
        case Some(ref) => offers.find(offered =>
          CampaignPlans.refOf(offered.offer.source) == ref) match {
          case Some(offered) => Vector(applicationOf(setup, offered))
          case None => Vector(BuildOps((_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a chosen Campaign battle plan is no longer available"))))
        }
      }))
```

Update the class doc's first bullet: "A player asks a decision listing the plans still unchosen: those the user can pay for (a dry run of each says so, and prices it), the rest shown unavailable with why, then Finish. Nothing usable means nothing is asked."

- [ ] **Step 6: Run the plan suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.CampaignPlanWindowSuite"`
Expected: PASS. If the shortfall test reads "Cannot be paid for now", print the violation the dry run returned and add its case to `shortfall` with the same "Needs …; you have …" wording; do not loosen the test.

Then `./sbtw test`: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala src/test/scala/oathdigital/gameplay/powers/campaign/EncirclementSuite.scala
git commit -m "feat(campaign): show battle plans that cannot be used, with what they need"
```

---

### Task 11: Bandit Prince tallies the warbands it needs

Spec slice 5.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala`

**Interfaces:**
- Consumes: Task 1's `DecisionTally`, `DecisionSummary`, `DecisionOption.Unavailable`, `OptionReason.Written`.

- [ ] **Step 1: Write the failing tests**

In `BanditPrinceSuite.scala`, in the first test replace the query assertion with:

```scala
    val query = as[DecisionQuery.ChooseMany](decide.query)
    assertEquals((query.min, query.max), (0, 2))
    assertEquals(query.options, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))))
    assertEquals(query.summary,
      Some(DecisionSummary("Replace bandits at", "site", "sites")))
    // The limit is what p1's bank holds, the warbands the replacement draws.
    assertEquals(query.tallies, Vector(DecisionTally("Warbands needed",
      "warbands", Map(DecisionOptionRef.Site(homeSite) -> 2,
        DecisionOptionRef.Site(awaySite) -> 1),
      Some(PlayerFacts.banked(ready, mine)))))
```

In "sites holding more bandits than the bank holds warbands are refused", read `offered` as `as[DecisionQuery.ChooseMany](query).options.filter(_.available)`. In "only the sites that fit the bank are offered, and the question is still asked", read `fitting` with `.filter(_.available)` the same way, and append:

```scala
    assertEquals(as[DecisionQuery.ChooseMany](question(tight, power, played,
      asked.tree).query).options.collect {
      case DecisionOption.Unavailable(inner, OptionReason.Written(reason)) =>
        inner.ref -> reason }, Vector(DecisionOptionRef.Site(homeSite) ->
      "Needs 2 warbands; your bank holds 1"))
```

Import `oathdigital.gameplay.powers.PlayerFacts` beside `NoteText`; `mine` is the suite's force kind for p1 and `ready` the first test's `bandited.ready`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditPrinceSuite"`
Expected: FAIL: no tallies, no summary, and the unaffordable site is absent.

- [ ] **Step 3: Declare the tally, the summary and the reasons**

In `BanditPrince.scala`, replace `affordable` with a bank reader:

```scala
  /** The warbands in the actor's bank: what the replacement can draw on. */
  private def bank(ready: ReadyGame, actor: PlayerId): Int =
    PlayerFacts.forceKind(ready, actor).toOption
      .fold(0)(PlayerFacts.banked(ready, _))
```

and replace the body of `effect`'s first `Branch` with:

```scala
    Vector(Branch((live, _) =>
      val sites = banditSites(live)
      val banked = bank(live, actor)
      val fits = sites.filter(bandits(live, _) <= banked)
      def ref(site: SiteId) = DecisionOptionRef.Site(site)
      if sites.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else if fits.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseMany(0, fits.size,
          sites.map(site =>
            if fits.contains(site) then DecisionOption.Site(ref(site))
            else DecisionOption.Unavailable(DecisionOption.Site(ref(site)),
              OptionReason.Written(needs(bandits(live, site), banked)))),
          heading = Some("Bandit Prince: choose the sites whose bandits " +
            "your warbands replace"),
          tallies = Vector(DecisionTally("Warbands needed", "warbands",
            fits.map(site => ref(site) -> bandits(live, site)).toMap,
            Some(banked))),
          summary = Some(DecisionSummary("Replace bandits at", "site",
            "sites")))),
        BuildOps((ready, pending) => replace(ready, actor, pending)),
        Branch((_, pending) => chosen(pending).map(site =>
          Note(id, replacedNote(_, actor, site), covers = true))))))

  /** Why a site's bandits alone are more than the bank holds. */
  private def needs(count: Int, banked: Int): String =
    s"Needs $count ${if count == 1 then "warband" else "warbands"}; " +
      s"your bank holds $banked"
```

Update the object's doc: "The walker's search hides a site whose bandits alone exceed the bank" becomes "A site whose bandits alone exceed the bank is shown unavailable with what it needs, and a tally of the warbands needed, limited by the bank, guides the choice; the answer's sum check stays."

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditPrinceSuite"`
Expected: PASS. Then `./sbtw test`: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala
git commit -m "feat(powers): Bandit Prince tallies the warbands its sites need against the bank"
```

---

### Task 12: The roadmap, the gates and the browser check

Spec slice 6, and the Testing section's browser check.

**Files:**
- Modify: `docs/ROADMAP.md:49-65` (Phase - Card play UI and Phase - Campaign UI) and the item **walker-native card play through card slots** under Powers-related deferred items (near line 276)

- [ ] **Step 1: Update the roadmap**

Write this script to the session scratchpad as `roadmap_board_decisions.py` and run it with `python3` from the worktree root:

```python
import pathlib
p = pathlib.Path("docs/ROADMAP.md")
s = p.read_text()

def swap(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)

card_start = s.index("### Phase - Card play UI\n")
campaign_start = s.index("### Phase - Campaign UI\n")
s = s[:card_start] + """### Phase - Card play UI

Playing a card takes too many clicks, found in the playtest of 2026-10-01.
Board decisions
([spec](superpowers/specs/2026-10-03-board-decisions-design.md)) answer a
decision about the table on the table. Spec 2 builds **walker-native card
play through card slots** (Powers-related deferred items) on them. Settled
already:

- Choosing a slot merges the discard: a site slot holding a card the player
  may replace is an option marked "replace". People's Favor discards only
  when playing to a site.
- People's Favor and Homeland together allow up to two discards; spec 2
  decides how that choice is presented.
- Playing a facedown adviser starts from the Minor Actions button; the
  player then picks the adviser on the board.
- Empty site slots and empty adviser slots need option references; today's
  adviser slot names an occupied position.
- Card-play reasons for options not on offer belong to spec 2.
- Partition decisions keep their drag and drop.

### Phase - Board decision follow-ups

- Move Travel onto board decisions; it still picks its destination through
  the older board targets.
- Add reasons to the remaining option builders as playtests find options
  that people wonder about.

""" + s[campaign_start:]

swap("""- Target selection: the confirm button looks like every other button. List
  each target's defense and warbands, and tally them.
""", """- [x] Target selection: answered on the table, with the mandatory target
  locked, defense and defending force tallied, a primary Confirm, and a
  reason for every target not on offer (board decisions, 2026-10-04).
""")
swap("- Results: make it clear what a Campaign's battle did.",
     "- [ ] Results: make it clear what a Campaign's battle did.")

swap("""  so it needs its own spec. Until then Mob uses a single `PlacementRules` value""",
     """  so it needs its own spec: spec 2 of board decisions, under **Phase - Card
  play UI**. Until then Mob uses a single `PlacementRules` value""")

p.write_text(s)
```

If an `assert` fails, read the roadmap at that point and adjust the script's `old` text to match it exactly; do not edit the roadmap by hand.

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS, at Task 11's server count and the frontend count from Task 7 plus nothing lost.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: PASS.

- [ ] **Step 3: Commit the roadmap**

```bash
git add docs/ROADMAP.md
git commit -m "docs: mark Campaign target selection done and rewrite Card play UI as spec 2 of board decisions"
```

- [ ] **Step 4: Check Campaign in the browser**

Start the scratch server as Task 7 Step 5 does (rebuild with `frontend/fastLinkJS` first if Task 7's server is gone). In a new game, play Setup through, and with the first player whose pawn stands at a ruled site, start a Campaign. Check in one batched pass, at 1440×900, with `read_page`, `javascript_tool` and one screenshot per step:

1. Targets: the origin site shows locked (lock badge, blue fill) and is the panel's first row, "Locked: …" with its defense and warbands; same-ruler sites are brass; every other site is dimmed and its `title` is "Not ruled by the defender" or "You rule this site"; the summary reads "Target 1 site" and grows as sites are picked; the totals read "Defense dice (before plans and powers)" and "Defending force"; Confirm is the primary button and submits with no extra target.
2. If a Raid is possible (an enemy pawn shares the site), the defender is picked by a click on their pawn or on their name in their board's header, and both highlight together on hover.
3. Battle plans: the offered plan cards are brass where they lie, on boards and at sites; a pick plus Confirm plays one; the played plan shows locked on the next pass; "Finish battle plans" is a Decide button above the "Decision" rule and submits at once; a plan not on offer is dimmed with its reason.
4. The inspect button opens the card summary and does not pick the card.
5. `read_console_messages` with `onlyErrors` shows nothing new.

Bandit Prince needs the card in hand, which a fresh game rarely deals; its tally and reasons are covered by `BanditPrinceSuite` and `BoardDecisionPanelSuite`, and the browser check of it is skipped unless a game in the scratch copy reaches it. Say which in the report.

Fix every failure in one batch, commit each fix against the file it touches, confirm once, and stop. Then stop the server (`lsof -ti :8081 | xargs kill`) and call `resize_window` with preset `desktop`.

- [ ] **Step 5: Hand back**

Leave the worktree for the merge. The merge into local `main` (origin lags it) is the user's call: present the finishing options, then, on their choice, run `ExitWorktree(keep)` before merging from the main checkout, and check `git log -1` on `main` first, since other sessions commit there.
