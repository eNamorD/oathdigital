# Power Log Lines, Slice 4 (Added Effects and Altered Procedures) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each implemented power that adds an effect or alters a procedure its own Game Log line, so the log says what the power did.

**Architecture:**

- Each power in this slice already changes its action through a `Transform`, a battle plan's `later` or `wrapping`, or a tree it builds. It gains a `Note` node where its effect happens and declares the `NoteKey` that words it. This is the slice-1 emission path 1 (`Note`), as the phase powers of slice 2 use it.
- Three mechanism gaps are closed first (Task 1):
  - A list of cards argument.
  - A banner argument.
  - A note after a leaf that ran and changed nothing reads "no change", not an older step.
- The Homeland rule is not a power. It gets an id and a key through a small registry, `RuleNotes`, which the formatter reads beside the power catalogs (spec section 1, "Game rules").

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change. Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

- Before starting, read the Rulings, section 1 (all of it), section 2's "Added effects" and "Altered procedures" tables, and "Settled in slice 1" to "Settled in slice 3".
- The slice 2 plan, `docs/superpowers/plans/2026-09-26-power-log-lines-slice2.md`, shows how phase powers got their lines. The slice 3 plan shows how a contributing power's suite reads its notes.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file edited below lists the imports it needs. A non-exhaustive match on the sealed `NoteArg` also fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `gameplay/walker/ProcedureWalker.scala` is at 743 lines. Task 1 adds at most 2 lines to it.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - To merge at the end, leave the worktree with `ExitWorktree` (`keep`) first. The worktree guard refuses `git -C` on the main checkout.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Line wording follows the spec's "Added effects" and "Altered procedures" tables, except where "Decisions this plan makes" below changes it. A template starts with an argument or a capital letter. `PowerNoteCatalogSuite` already checks this.
- Players are named by chip, never "you".
- **A note never comes before a decision already in the tree** (decision 1 below). Every placement in this plan follows it. Do not move a `Note` earlier than the plan puts it.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
- If a golden log under `src/test/resources/gamelog/` changes, stop and report which line changed. Do not regenerate it.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 5 records each one in the spec.

1. **A note never comes before a decision already in the tree.** A game parked on a decision stores that decision's position in the tree. A node inserted before it moves it, and the parked game can no longer resume. So a note goes after the last decision of the node it joins, or where no decision follows it. Several "may" lines therefore post after the choice they explain:
   - Knights Errant's line posts after the choice to campaign.
   - Warning Signals' line posts after the defender's arrangement.
   - The Mob's and the Homeland's line posts after the discard answer, before the play.
   - League Treaty's line posts after the moves.
2. **Wording changes that follow from 1, and from "amounts are what happened":**
   - Warning Signals: "Warning Signals: {Blue} redistributed their warbands."
   - League Treaty: "League Treaty: {Blue} sent {3 favor} to {the Nomad bank}." It writes nothing when the ruler declines or nothing moves.
   - Dragonskin Drum: "Dragonskin Drum: {Red} gained {1 warband}.", the shared gain sentence. Every other gain line names its player.
   - Toll Roads: key `paid`, "{Red} paid {1 favor} to {Blue}.", and key `burned`, "{Red} burned {1 favor}.".
   - Sticky Fire has three keys:
     - `burned`: "Killed {n} {Blue} warband(s), and {Blue} gained {1 favor}."
     - `killed`: "Killed {n} {Blue} warband(s)." (the winner had no favor to give).
     - `gained`: "{Blue} gained {1 favor}." (the loser's board held no warband).
     - Against bandits it writes nothing: no player lost a warband or gained the favor.
   - Outriders writes "Skulls ignored." only when the attack rolled a skull.
   - Forest Paths writes "Ignoring site powers." only when the destination holds a beast card, which is when it ignores them.
   - Gleaming Armor's line posts after the taxed plan's own effects. The extra secret is a `Number` with a `Plural`: "{Red}'s battle plans cost {1} extra secret."
3. **Two new note arguments.**
   - `NoteArg.Cards(ids)` renders as one phrase through `LogWords.cards`, "Tinker, 2 Denizens and a Vision". Each card passes the knowledge rule.
   - `NoteArg.Banner(banner)` renders as the banner's name. Conspiracy seizes relics and banners.
   - Wire kinds: `"cards"` with a `cards` array, `"banner"` with `bannerKey`.
4. **A leaf that ran and changed nothing is the step before a note.**
   - Today an empty `BuildOps`, or a batch the pipeline executed none of, leaves `previous` pointing at an older step. A note after it then restates that older step.
   - After this slice, such a leaf sets `previous` to its own unchanged state twice, so a note after it reads no change and writes nothing.
   - Every existing note reads a difference, and an unchanged pair reads zero, so every existing line stays as it is.
5. **A note that cannot read one step reads the state its transform was folded with.**
   - Dazzle's discards may be refused one by one by the discard restrictions.
   - Its note lists the cards it would discard at the fold that no site holds when the note runs.
   - The fold is the state when the walk first reaches the window, which is the state before Dazzle's step.
6. **The Mob's line travels in `PlacementRules`, so card play still names no power.**
   - `PlacementRules` gains `siteDiscardNote: Option[Note]`, set by `withSiteDiscardFirstBy(note)`.
   - Card play puts that note after the discard answer at a site.
   - Without the permission, the discard is asked only at a full Homeland of the card's suit, and card play puts the Homeland rule's note there instead.
   - Both notes share one key, `PlacementRules.discardFirst`: "{Red} may discard a card at their site first.".
7. **`RuleNotes` lives in `gameplay.actions`.**
   - It sits beside `PlacementRules` and declares `rule.homeland-discard` with that key.
   - `NoteWordings.default` and `PowerNoteCatalogSuite` read it beside the two power catalogs.
8. **Shared sentences move to `NoteSupport`:**
   - `killedKey(name)`, which Wolves already uses as its `used` line.
   - `gainedFromKey(name)`, which Gambling Hall already uses as `gained`.
   - Their note builders take a `PowerSourceRef`, since a contributing power has no decision option to name its card.

### Facts this plan relies on (verified against the code on 2026-09-26, at `5eaf3149`)

- **Mechanism (slices 1 to 3):**
  - `model/PowerNotes.scala`:
    - `PowerNote(source, key, args)`;
    - `NoteArg.{Player, Card, Site, Amount, Number, Bank, Dice}`;
    - `NotePart.{Text, Arg, Plural}`;
    - `NoteKey(name, template)`, with `apply(source: PowerSourceRef, args: NoteArg*)`;
    - `NoteStates(now, previous, answered)`;
    - `Note(power, build, covers = false)`.
  - `PowerSourceRef.{Card(id: CardId), Site(id: SiteId), Banner(banner)}` in `model/GameState.scala`.
  - `gameplay/walker/ProcedureWalker.scala`:
    - `runBuildOps` (about line 629) returns `Right(ctx)` for an empty batch.
    - `recordBatch` (about line 647) keeps `ctx.previous` when `updated.executed.isEmpty`.
    - `noted` passes `NoteStates(ctx.state, ctx.previous, ctx.answered)`.
  - `serialization/WalkerEventCodec.scala`:
    - `encodeNoteArg` and `decodeNoteArg`;
    - `decodeBanner(key, path)`;
    - `decodeCardId`, `encodeCardId`;
    - `traverse(values)(f)`.
  - `application/gamelog/PowerLines.scala` `argument` renders each `NoteArg`. `LogWords` has `cards(words: Vector[CardWord])`, `seen(id, states, viewer)` and `banner(banner)`.
  - `application/gamelog/NoteWordings.scala`: `default(catalog)` folds `of(id, keys)` over the walker and phase catalogs.
  - `gameplay/powers/NoteSupport.scala`: `favor`, `supply`, `secrets`, `warbands`, `bankPaid`, `relicsGained`, `answer`, `gainedKey`, `gainNote(key, source: DecisionOptionRef, …)`, `took`.
  - `LogJournal.covered(index)` drops a step's roll and delta lines when a covering `PowerNoted` follows it before the next step.
- **The powers** (all read in full while planning):
  - Toll Roads, Grasping Vines, Forest Paths and Dragonskin Drum hook `PowerWindow.TravelCost`.
    - Travel is one command and asks no decision.
    - `TravelRoute.pawnMove(ctx.operation)` gives `route.player`, `route.source` and `route.destination`.
  - Gossip appends `Gain.Favor(holder, Suit.Discord, 1)` at `ActionCardPlayedFacedown`.
  - Book Binders appends a `Branch` of `FavorBankChoice.take(...)` at `ActionCardPlayedFaceup`:
    - `take` is empty, or `Vector(move)` for one stocked bank, or `Vector(Decide, move)` for several.
    - The window's node count must not depend on live state (its doc comment).
  - Truthful Harp appends `reveal(actor)`, a `BuildOps` of `Peek`s for every other player and every card in the actor's temporary hand, at `SearchBeforeDraw`.
  - Dazzle appends one `BuildOps` of `Discard.Denizen` and `Discard.RuinedEdifice`, restricted by `DiscardRestrictions`, at `ActionCardPlayedFaceup`.
  - `ConspiracyWhenPlayed` (a `case object`, `decisionId = "cardplay.conspiracy.target"`) appends an optional target `Decide` and one `BuildOps` (take and removal).
  - Gleaming Armor prepends `surcharge` to a `CampaignPlanApplication`'s children.
  - Mercenaries, Sticky Fire, Outriders and Warning Signals are `BattlePlan`s.
    - `later` runs after the window's children.
    - `wrapping` wraps them.
    - `noteKeys` is not final in `BattlePlan`.
  - Knights Errant's `offer` is a `Branch` holding one `Decide`. `campaign` is a `Branch` holding the nested Campaign.
  - `PeoplesFavorMob` (an `object`, source `RuleSourceRef.Banner`) calls `tree.adjust(children)(_.withSiteDiscardFirst)`.
  - `CardPlayProcedure.childrenFor`'s `selected` branch returns `choice ++ Vector(apply) ++ hook`.
    - `choice` is the replacement `Decide`, or empty.
    - The `settled` path keeps the same shape.
    - A Vision is never played to a site.
  - League Treaty's `inserted` is `Vector(Decide, Branch, BuildOps)`, prepended to the cleanup `BuildOps` of `FinishRestProcedure`, which asks nothing.
- **Tests:**
  - `NoteText.said(id, keys, events)` in `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`. A player reads as its id, an amount as "1 favor", a bank as "the Order bank".
  - Travel suites: `TravelFixture.travel(ready, destination, modifiers = Vector.empty): Either[OathViolation, OathTransition]` is the whole Travel. `rival = TargetsFixture.others(base).head`.
  - Card play suites:
    - `SearchFixture.play(ready, modifiers, kept, button): OathTransition` keeps the events of the whole Search.
    - `SearchFixture.start(ready, modifiers)` starts one.
  - Campaign suites: `PlanDriver.Run.events` accumulates every command's events, and `commit`, `pick`, `answer` and `finish` return a `Run`.
  - `CampaignFixture.dice(attack, defense)` fixes the faces. The attack count equals the force, and the defense count is the site's printed defense (`CampaignProcedureSuite`, "the sacrifice decision is bounded by the force the skulls left").
  - `ConspiracyWhenPlayedSuite` and `DazzleSuite` walk with `ProcedureWalker` directly. A `WalkerOutcome.Finished(_, events)` holds that command's events.
  - `PlacementFixture` offers:
    - `staged(card, site): (ReadyGame, PlayerId, SiteId)`;
    - `ruledByActor`, `denizen`, `plain`, `build`, `park`, `answer`, `options`;
    - `decisionId(card, kind)`.
  - `GameLogPowerLinesSuite` offers:
    - `usePower`, `withoutNotes`, `inserted`, `take`;
    - `saying(actor, arg)`, which renders "{actor} said {arg}.";
    - `ours(steps, viewer)`, `text`, `name`.

---

### Task 1: The mechanism: card lists, banners, and an unchanged step

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala`, `action/GamblingHall.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`, `src/test/scala/oathdigital/gameplay/PowerNoteWalkerSuite.scala`

**Interfaces:**
- Produces:
  - `NoteArg.Cards(ids: Vector[CardId])`, `NoteArg.Banner(banner: Banner)`.
  - `NoteSupport.killedKey(name: String): NoteKey`, `NoteSupport.gainedFromKey(name: String): NoteKey`.
  - `NoteSupport.killedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(states: NoteStates): Option[PowerNote]`.
  - `NoteSupport.gainedFromNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(states: NoteStates): Option[PowerNote]`.
  - `NoteSupport.gainedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId, unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates): Option[PowerNote]`.

- [ ] **Step 1: Write the failing tests**

In `PowerNoteWalkerSuite.scala`, add after "a note before any step has no previous step":

```scala
  test("a note after a leaf that changed nothing reads no change, not an older step"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); None })
    val tree = Sequence(Vector[Operation](ModifyDicePool(pool, 1),
      BuildOps((_, _) => Right(Vector.empty)), reading))
    assert(ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty).isRight)
    val (before, after) = seen.get.previous.get
    assertEquals(before, after)
    assert(after.game.current.rollPools.contains(pool))
```

In `GameEventWireSuite.scala`, add after "a power note's site and banner sources round trip":

```scala
  test("a power note's card list and banner arguments round trip"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("93")),
      NoteArg.Cards(Vector(DenizenId("12"), RelicId("r1"))),
      NoteArg.Cards(Vector.empty), NoteArg.Banner(Banner.PeoplesFavor))
    val encoded = GameEventWire.encodeEvent("notes", catalog.ref, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
```

In `GameLogPowerLinesSuite.scala`, add after "a card its viewer may not identify is not named to them":

```scala
  test("a card list reads as one phrase, and a banner by its name"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val hidden: CardId = last.game.current.players
      .filter(_.player != script.actor).flatMap(_.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => id: CardId
        case VisionState(id, Orientation.FaceDown) => id: CardId
      }).head
    val back = hidden match
      case _: VisionId => "a Vision"
      case _ => "a Denizen"
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(Vector(ParkedServiceFixture.silverTongueCard, hidden))))
    assertEquals(text(ours(listed, Some(script.actor)).head),
      s"Silver Tongue: ${name(script.actor)} said Silver Tongue and $back.")
    val banner = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Banner(Banner.DarkestSecret)))
    assertEquals(text(ours(banner).head),
      s"Silver Tongue: ${name(script.actor)} said Darkest Secret.")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteWalkerSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: compilation FAILS: `NoteArg.Cards` and `NoteArg.Banner` do not exist.

- [ ] **Step 3: Add the arguments to the model**

In `model/PowerNotes.scala`, inside `object NoteArg`, after `Dice`:

```scala
  /** Several cards as one phrase, "Tinker, 2 Denizens and a Vision". Each
    * card still passes the log's knowledge rule. */
  final case class Cards(ids: Vector[CardId]) extends NoteArg
  final case class Banner(banner: oathdigital.model.Banner) extends NoteArg
```

Replace the `NoteStates` doc comment with:

```scala
/** What a note may read when the walker reaches it. `previous` is the states
  * before and after the leaf this command ran last, if any, so a note that
  * restates a step reads the applied amount instead of repeating the
  * operation's cap logic. A leaf that ran and changed nothing gives the same
  * state twice, so a note after it reads no change. `answered` holds the
  * action's decisions so far. */
```

- [ ] **Step 4: Encode and decode them**

In `WalkerEventCodec.scala`, add to `encodeNoteArg` after the `Dice` case:

```scala
    case NoteArg.Cards(ids) => ujson.Obj("kind" -> "cards",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))
    case NoteArg.Banner(banner) => ujson.Obj("kind" -> "banner",
      "bannerKey" -> banner.key)
```

Add to `decodeNoteArg` before the `case other =>` line:

```scala
    case "cards" => traverse(value("cards").arr.zipWithIndex.toVector)({
      case (card, index) => decodeCardId(card, s"$path.cards[$index]") })
      .map(NoteArg.Cards.apply)
    case "banner" => decodeBanner(value("bannerKey").str, s"$path.bannerKey")
      .map(NoteArg.Banner.apply)
```

- [ ] **Step 5: Render them**

In `PowerLines.scala`, add to `argument` after the `Dice` case:

```scala
    case NoteArg.Cards(ids) => words.cards(ids.map(words.seen(_, seen, viewer)))
    case NoteArg.Banner(banner) => Vector(words.banner(banner))
```

In the test helper `NoteText.scala`, add `import oathdigital.gameplay.actions.BannerRules`, and to `plain` after the `Dice` case:

```scala
    case NoteArg.Cards(ids) => ids.map(_.value).mkString(", ")
    case NoteArg.Banner(banner) => BannerRules.displayName(banner)
```

- [ ] **Step 6: Let an unchanged leaf be the step a note reads**

In `ProcedureWalker.scala`:

1. In `runBuildOps`, replace `if ops.isEmpty then Right(ctx)` with:

   ```scala
      if ops.isEmpty then Right(ctx.copy(previous = Some((ctx.state, ctx.state))))
   ```

   Its doc comment's last sentence becomes: "An empty batch runs nothing and records nothing, and a note after it reads that nothing changed."

2. In `recordBatch`, replace

   ```scala
        previous = if updated.executed.isEmpty then ctx.previous
          else Some((ctx.state, updated.state)))
   ```

   with

   ```scala
        previous = Some((ctx.state, updated.state)))
   ```

3. In `WalkCtx`, the doc of `previous` becomes: "The states before and after the leaf this command ran last, which a note reads. A leaf that changed nothing gives the same state twice."

The file must stay at or under 800 lines (`wc -l`).

- [ ] **Step 7: Share the kill and gain-from-bank sentences**

In `NoteSupport.scala`, add after `gainNote`:

```scala
  /** What `player` gained in the step before the note, by `read`, for a
    * power whose card is `card`. Nothing gained writes nothing. */
  def gainedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId,
      unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    amount = read(step, player)
    if amount > 0
  yield key(card, NoteArg.Player(player), NoteArg.Amount(amount, unit))

  /** "{player} gained {amount} from {bank}." */
  def gainedFromKey(name: String): NoteKey = NoteKey(name, Vector(
    NotePart.Arg(0), NotePart.Text(" gained "), NotePart.Arg(1),
    NotePart.Text(" from "), NotePart.Arg(2), NotePart.Text(".")))

  /** The favor `player` took from one bank in the step before the note. */
  def gainedFromNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    amount = favor(step, player)
    if amount > 0
    bank <- bankPaid(step)
  yield key(card, NoteArg.Player(player), NoteArg.Amount(amount, NoteUnit.Favor),
    NoteArg.Bank(bank))

  /** "Killed {n} {player} warband." */
  def killedKey(name: String): NoteKey = NoteKey(name, Vector(
    NotePart.Text("Killed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Arg(1), NotePart.Plural(0, " warband.", " warbands.")))

  /** The warbands `player` lost in the step before the note. */
  def killedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    lost = -warbands(step, player)
    if lost > 0
  yield key(card, NoteArg.Number(lost), NoteArg.Player(player))
```

Make `gainNote` delegate, so the two cannot drift:

```scala
  def gainNote(key: NoteKey, source: DecisionOptionRef, player: PlayerId,
      unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates)
      : Option[PowerNote] = PowerSourceRef.of(source).flatMap(card =>
    gainedNote(key, card, player, unit, read)(states))
```

In `Wolves.scala`, replace the `killed` definition with `val killed: NoteKey = NoteSupport.killedKey(NoteKey.Used)`.

In `GamblingHall.scala`, replace the `gained` definition with `val gained: NoteKey = NoteSupport.gainedFromKey("gained")`. Keep its doc comment.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteWalkerSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.gamelog.* oathdigital.gameplay.powers.*"`
Expected: all PASS, the golden logs unchanged.

If an existing note's test fails, the unchanged-step change altered what it reads. Stop and report which note and why. Do not adjust the note to pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/main/scala/oathdigital/application/gamelog/PowerLines.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala src/test/scala/oathdigital/gameplay/powers/NoteText.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/scala/oathdigital/gameplay/PowerNoteWalkerSuite.scala
git commit -m "feat(log): card list and banner note arguments; an unchanged step reads as no change"
```

---

### Task 2: The Travel powers say what they did

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala`, `GraspingVines.scala`, `ForestPaths.scala`, `DragonskinDrum.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`, `GraspingVinesSuite.scala`, `ForestPathsSuite.scala`, `DragonskinDrumSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.killedKey`, `killedNote`, `gainedKey`, `gainedNote` (Task 1).
- Produces:
  - `TollRoads.paid: NoteKey` (`"paid"`), `TollRoads.burned: NoteKey` (`"burned"`).
  - `GraspingVines.killed: NoteKey` (`"killed"`).
  - `ForestPaths.ignoring: NoteKey` (`"ignoring"`).
  - `DragonskinDrum.gained: NoteKey` (`"gained"`).

- [ ] **Step 1: Write the failing tests**

In each of the four suites, add `NoteText` to the `oathdigital.gameplay.powers` import (`{NoteText, PowerFixture, TargetsFixture}`, `{NoteText, PowerFixture}`, `{NoteText, PlayerFacts, PowerFixture}`), and append at the end of the class:

`TollRoadsSuite.scala`:

```scala
  // ---- Lines ----

  private val power = TollRoads.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the toll paid to the ruler is written as the Roads' line"):
    assertEquals(said(travel(rivalRules, coast).toOption.get.events),
      Vector(NoteText.Said("paid",
        s"${actor.value} paid 1 favor to ${rival.value}.", covers = false)))

  test("a toll burnt for bandits is written; a free Travel writes nothing"):
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    assertEquals(said(travel(ready, coast).toOption.get.events),
      Vector(NoteText.Said("burned", s"${actor.value} burned 1 favor.",
        covers = false)))
    assertEquals(said(travel(rivalRules, plains(2)).toOption.get.events),
      Vector.empty)
```

`GraspingVinesSuite.scala`:

```scala
  // ---- Lines ----

  private val power = GraspingVines.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the kill is written as the Vines' line, naming the traveller"):
    assertEquals(said(travel(vinesAtHome(Some(rival)), coast).toOption.get
      .events), Vector(NoteText.Said("killed",
        s"Killed 1 ${actor.value} warband.", covers = false)))

  test("a traveller with no warband to lose reads no line"):
    val ready = withBoard(vinesAtHome(Some(rival)))(_.copy(warbands = 0))
    assertEquals(said(travel(ready, coast).toOption.get.events), Vector.empty)
```

`ForestPathsSuite.scala`:

```scala
  // ---- Lines ----

  private val power = ForestPaths.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a Travel to a beast card writes that site powers are ignored"):
    val ready = denizenAt(passRuled(held), beast, mountain)
    assertEquals(said(travel(ready, mountain, modifiers).toOption.get.events),
      Vector(NoteText.Said("ignoring", "Ignoring site powers.",
        covers = false)))

  test("without a beast card nothing is ignored, and nothing is written"):
    assertEquals(said(travel(passRuled(held), mountain, modifiers).toOption.get
      .events), Vector.empty)
```

`DragonskinDrumSuite.scala`:

```scala
  // ---- Lines ----

  private val power = DragonskinDrum.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the warband gained is written as the Drum's line"):
    assertEquals(said(travel(held, coast, modifiers).toOption.get.events),
      Vector(NoteText.Said("gained", s"${actor.value} gained 1 warband.",
        covers = false)))

  test("an empty warband bank gains nothing, and writes nothing"):
    val kind = PlayerFacts.forceKind(held, actor).toOption.get
    assertEquals(said(travel(leaveInBank(held, kind, 0), coast, modifiers)
      .toOption.get.events), Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.*"`
Expected: the tests that expect a line FAIL with an empty vector. The "writes nothing" halves pass.

- [ ] **Step 3: Toll Roads**

In `TollRoads.scala`:

1. Change the import to `oathdigital.gameplay.powers.{CatalogCards, CatalogResolution, NoteSupport}`.
2. Add to the class doc: "The payment writes "{Red} paid 1 favor to {Blue}.", or "{Red} burned 1 favor." when bandits rule, read from the payment's step."
3. Replace `contributions`, `applicable` and `toll` with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(TollRoads.paid, TollRoads.burned)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Travel owes and its line, if it owes one. */
  private def toll(ctx: PowerCtx): Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  yield Vector(payment(route.player, ruler),
    Note(id, paidNote(route.player, ruler)))

  private def payment(traveller: PlayerId, ruler: SiteRuler): CoreOperation =
    ruler match
      case SiteRuler.Player(owner) => Give(Piece.Favor(TollRoads.Favor),
        traveller, Location.PlayArea(traveller), Location.PlayArea(owner),
        required = true)
      case _ => PayCost(traveller, Location.SharedBank,
        Cost(favorBurnt = TollRoads.Favor))

  /** What the payment's step took from the traveller. */
  private def paidNote(traveller: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    paid = -NoteSupport.favor(step, traveller)
    if paid > 0
  yield
    val card = PowerSourceRef.Card(cardId)
    val amount = NoteArg.Amount(paid, NoteUnit.Favor)
    ruler match
      case SiteRuler.Player(owner) => TollRoads.paid(card,
        NoteArg.Player(traveller), amount, NoteArg.Player(owner))
      case _ => TollRoads.burned(card, NoteArg.Player(traveller), amount)
```

4. Add to the companion:

```scala
  /** "{Red} paid {1 favor} to {Blue}." */
  val paid: NoteKey = NoteKey("paid", Vector(NotePart.Arg(0),
    NotePart.Text(" paid "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(".")))
```

- [ ] **Step 4: Grasping Vines**

In `GraspingVines.scala`, import `{CatalogCards, CatalogResolution, NoteSupport}`, add to the class doc "The kill writes "Killed 1 {Red} warband.", read from its step, so a traveller with no warband reads nothing.", and replace `contributions`, `applicable` and `kill` with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(GraspingVines.killed)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      kill(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = kill(ctx).nonEmpty

  /** The kill this Travel owes and its line, if it owes one. */
  private def kill(ctx: PowerCtx): Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  yield Vector(Kill(warband, PositionedLocation(Location.PlayArea(route.player))),
    Note(id, NoteSupport.killedNote(GraspingVines.killed,
      PowerSourceRef.Card(cardId), route.player)))
```

Add to the companion: `val killed: NoteKey = NoteSupport.killedKey("killed")`.

- [ ] **Step 5: Forest Paths**

In `ForestPaths.scala`, add to the class doc "While it ignores the site powers it writes "Ignoring site powers."; its Supply waiver writes nothing (power log lines design, "No line").", and replace the `TravelCost` entry of `effects` with:

```scala
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if beastAtDestination(ctx) then
        Note(id, _ => Some(ForestPaths.ignoring(PowerSourceRef.Card(cardId)))) +:
          TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)),
```

Add `override def noteKeys: Vector[NoteKey] = Vector(ForestPaths.ignoring)` to the class, and to the companion:

```scala
  /** "Ignoring site powers." */
  val ignoring: NoteKey = NoteKey("ignoring",
    Vector(NotePart.Text("Ignoring site powers.")))
```

- [ ] **Step 6: Dragonskin Drum**

In `DragonskinDrum.scala`, import `{CatalogCards, NoteSupport, PlayerFacts, SelectedModifier}`, add to the class doc "Its line, "{Red} gained 1 warband.", reads the gain's step.", and replace `effects` with:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(DragonskinDrum.gained)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      operations :+ gain(ctx.activePlayer) :+ Note(id,
        NoteSupport.gainedNote(DragonskinDrum.gained,
          PowerSourceRef.Card(cardId), ctx.activePlayer, NoteUnit.Warband,
          NoteSupport.warbands)))))
```

Add to the companion: `val gained: NoteKey = NoteSupport.gainedKey("gained")`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS.

If a line appears twice, Travel's destination dry-run journaled into the command. Stop and report.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala src/main/scala/oathdigital/gameplay/powers/travel/DragonskinDrum.scala src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala src/test/scala/oathdigital/gameplay/powers/travel/GraspingVinesSuite.scala src/test/scala/oathdigital/gameplay/powers/travel/ForestPathsSuite.scala src/test/scala/oathdigital/gameplay/powers/travel/DragonskinDrumSuite.scala
git commit -m "feat(powers): the Travel powers say what they did"
```

---

### Task 3: The card-play powers say what they did

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/Gossip.scala`, `cardplay/BookBinders.scala`, `search/TruthfulHarp.scala`, `whenplayed/Dazzle.scala`, `whenplayed/ConspiracyWhenPlayed.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/GossipSuite.scala`, `cardplay/BookBindersSuite.scala`, `search/TruthfulHarpSuite.scala`, `whenplayed/DazzleSuite.scala`, `whenplayed/ConspiracyWhenPlayedSuite.scala`

**Interfaces:**
- Consumes: `NoteArg.Cards`, `NoteArg.Banner`, `NoteSupport.gainedFromKey`, `gainedFromNote` (Task 1).
- Produces:
  - `Gossip.gained`, `BookBinders.gained` (both `"gained"`, covering).
  - `TruthfulHarp.revealed` (`"revealed"`, covering).
  - `Dazzle.discarded` (`"discarded"`, covering).
  - `ConspiracyWhenPlayed.seized` (`"seized"`).

- [ ] **Step 1: Write the failing tests**

`GossipSuite.scala`: add `NoteText` to the `oathdigital.gameplay.powers` import and append:

```scala
  // ---- Lines ----

  private val power = Gossip.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the favor another player's facedown play gains is Gossip's line"):
    val done = play(held(plain), Vector.empty, plain.head, "adviser-facedown")
    assertEquals(said(done.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 1 favor from the Discord bank.", covers = true)))

  test("a faceup play writes no Gossip line"):
    assertEquals(said(play(held(plain), Vector.empty, plain.head,
      "adviser-faceup").events), Vector.empty)
```

`BookBindersSuite.scala`: add `NoteText` to the `oathdigital.gameplay.powers` import and append:

```scala
  // ---- Lines ----

  private val power = BookBinders.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)
  private def gained(amount: Int, suit: Suit) = NoteText.Said("gained",
    s"${holder.value} gained $amount favor from the $suit bank.", covers = true)

  test("the favor taken from the one stocked bank is the Binders' line"):
    assertEquals(said(faceup(arranged(Map(Suit.Order -> 3))).events),
      Vector(gained(2, Suit.Order)))

  test("a bank chosen off turn writes the line when the take happens"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 1))
    val placed = faceup(ready)
    assertEquals(said(placed.events), Vector.empty)
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    val taken = SearchFixture.rules.resolveWalker(placed.state, holder, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .toOption.get
    assertEquals(said(taken.events), Vector(gained(1, Suit.Order)))

  test("the holder's own faceup Vision writes nothing"):
    assertEquals(said(faceup(arranged(Map(Suit.Hearth -> 5), owner = actor))
      .events), Vector.empty)
```

`TruthfulHarpSuite.scala`: add `NoteText` to the `oathdigital.gameplay.powers` import and append:

```scala
  // ---- Lines ----

  private val power = TruthfulHarp.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the revealed hand is the Harp's line, in place of the peeks"):
    val top = plain.take(7)
    val started = start(withHarp(top), onlyHarp).toOption.get
    assertEquals(said(started.events), Vector(NoteText.Said("revealed",
      s"Revealed ${top.take(5).map(_.value).mkString(", ")}.", covers = true)))

  test("a Search without the Harp writes no Harp line"):
    assertEquals(said(start(withHarp(plain.take(7))).toOption.get.events),
      Vector.empty)
```

`DazzleSuite.scala`: add `import oathdigital.gameplay.powers.NoteText`. At the end of the test "Dazzle discards Hearth and Order site cards from the actor region", after its last `assertEquals`, append:

```scala
    assertEquals(NoteText.said(power.id, power.noteKeys, finished.events),
      Vector(NoteText.Said("discarded",
        s"Discarded ${targets.map(_.value).mkString(", ")}.", covers = true)))
    // A second Dazzle finds nothing left to discard, and writes nothing.
    val again = ProcedureWalker.advance(finished.treeless, hook, None,
      WalkerPowers(Vector(power))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    assertEquals(NoteText.said(power.id, power.noteKeys, again.events),
      Vector.empty)
```

`ConspiracyWhenPlayedSuite.scala`: change the `oathdigital.gameplay.powers` import to `{NoteText, WalkerPowerCatalog}` and append:

```scala
  // ---- Lines ----

  private def seized(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(ConspiracyWhenPlayed.id, ConspiracyWhenPlayed.noteKeys, events)

  test("Conspiracy's line names the relic it seized and its owner"):
    val relic = RelicState(RelicId("conspiracy-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic))()
    val (tree, at) = atTarget(f)
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.RelicSlot(f.enemy, 0)))
    assertEquals(seized(done.events), Vector(NoteText.Said("seized",
      s"${f.actor.value} seized conspiracy-relic from ${f.enemy.value}.",
      covers = false)))

  test("Conspiracy's line names the banner it seized"):
    val f = fixture()((ready, enemy) => {
      val current = ready.game.current
      ready.updateCurrent(_.copy(banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(enemy), secrets = 3))))
    })
    val (tree, at) = atTarget(f)
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(seized(done.events), Vector(NoteText.Said("seized",
      s"${f.actor.value} seized Darkest Secret from ${f.enemy.value}.",
      covers = false)))

  test("a Conspiracy with no target writes no line"):
    val f = fixture(shared = false)()
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    assertEquals(seized(finished(answer(f, tree, place, placeId, faceup))
      .events), Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.GossipSuite oathdigital.gameplay.powers.cardplay.BookBindersSuite oathdigital.gameplay.powers.search.TruthfulHarpSuite oathdigital.gameplay.powers.whenplayed.*"`
Expected: the tests that expect a line FAIL with an empty vector (every power still has the default, empty `noteKeys`). The "writes nothing" tests pass.

- [ ] **Step 3: Gossip**

In `Gossip.scala`, import `{CatalogCards, CatalogResolution, NoteSupport}`, add to the class doc "Its line, "{Blue} gained 1 favor from the Discord bank.", covers the generic gain line, and reads the gain's step, so an empty bank writes nothing.", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(Gossip.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, children) =>
      holderOf(ctx).fold(children)(holder => children ++ Vector(
        Gain.Favor(holder, Suit.Discord, Gossip.Favor),
        Note(id, NoteSupport.gainedFromNote(Gossip.gained,
          PowerSourceRef.Card(cardId), holder), covers = true))))))
```

Add to the companion: `val gained: NoteKey = NoteSupport.gainedFromKey("gained")`.

- [ ] **Step 4: Book Binders**

In `BookBinders.scala`, import `{CatalogCards, CatalogResolution, NoteSupport}`, add to the class doc "Its line, "{Blue} gained 2 favor from the Order bank.", follows the take inside the `Branch`, so the window's node count still does not depend on live state.", add `override def noteKeys: Vector[NoteKey] = Vector(BookBinders.gained)`, and replace the `yield` of `reward` with:

```scala
  yield
    val decisionId = BookBinders.decisionId(ctx.state, holder, vision)
    Branch((state, _) => {
      val take = FavorBankChoice.take(state, holder, BookBinders.Favor,
        decisionId, "Book Binders: take two favor from a bank")
      if take.isEmpty then take
      else take :+ Note(id, NoteSupport.gainedFromNote(BookBinders.gained,
        PowerSourceRef.Card(cardId), holder), covers = true)
    })
```

Add to the companion: `val gained: NoteKey = NoteSupport.gainedFromKey("gained")`.

- [ ] **Step 5: Truthful Harp**

In `TruthfulHarp.scala`, add to the class doc "Its line names every card revealed, "Revealed {cards}.", and covers the generic peek lines.", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(TruthfulHarp.revealed)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      DrawExtension.extend(operations, TruthfulHarp.More) :+
        reveal(ctx.activePlayer) :+
        Note(id, revealNote(ctx.activePlayer), covers = true))))

  /** The hand the reveal showed. */
  private def revealNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    val hand = states.now.game.current.temporaryHands
      .getOrElse(actor, Vector.empty)
    Option.when(hand.nonEmpty)(TruthfulHarp.revealed(
      PowerSourceRef.Card(cardId), NoteArg.Cards(hand)))
```

Add to the companion:

```scala
  /** "Revealed {cards}." */
  val revealed: NoteKey = NoteKey("revealed", Vector(
    NotePart.Text("Revealed "), NotePart.Arg(0), NotePart.Text(".")))
```

- [ ] **Step 6: Dazzle**

In `Dazzle.scala`, add to the class doc "Its line names the cards it discarded, "Discarded {cards}.", and covers the generic discard lines. It lists the cards it would discard when the window is folded, which is before its step, that no site holds when the line is written, so a card a restriction kept is not named (power log lines slice 4, decision 5).", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(Dazzle.discarded)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children :+ BuildOps((ready, _) => effects(ready, ctx.activePlayer),
        restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, ctx.activePlayer))) :+
        Note(id, discardNote(ctx.state, ctx.activePlayer), covers = true))))

  /** The cards Dazzle would discard in `before` that no site holds now. */
  private def discardNote(before: ReadyGame, actor: PlayerId)(
      states: NoteStates): Option[PowerNote] =
    val sites = states.now.game.current.map.sites.values
    val gone = targets(before, actor).filterNot(id =>
      sites.exists(_.denizens.exists(_.id == id)))
    Option.when(gone.nonEmpty)(Dazzle.discarded(PowerSourceRef.Card(cardId),
      NoteArg.Cards(gone)))

  /** The Hearth and Order site denizens and ruined edifices in the actor's
    * region, in map order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[CardId] =
    val current = ready.game.current
    current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf).toVector.flatMap(region =>
        current.map.inPlay.filter(current.map.regionOf(_).contains(region))
          .flatMap(site => current.map.sites(site).denizens.collect {
            case denizen: DenizenState => denizen.id: CardId
            case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
              edifice.id: CardId
          }))
      .filter(id => catalog.suitOf(id).exists(suit =>
        suit == Suit.Hearth || suit == Suit.Order))
```

Add to the companion:

```scala
  /** "Discarded {cards}." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
```

If `catalog.suitOf` does not take a `CardId`, use the lookup `effects` uses for the same card.

- [ ] **Step 7: Conspiracy**

In `ConspiracyWhenPlayed.scala`, add `import oathdigital.gameplay.powers.NoteSupport`, add to the object doc "After the take it writes "{Red} seized {relic or banner} from {Blue}.", read from the take's step, and nothing when nothing was taken.", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(seized)

  /** "{Red} seized {relic or banner} from {Blue}." */
  val seized: NoteKey = NoteKey("seized", Vector(NotePart.Arg(0),
    NotePart.Text(" seized "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      (children ++ targetDecision(ctx.state, ctx.activePlayer)) :+
        BuildOps((ready, pending) =>
          effects(ready, ctx.activePlayer, pending)) :+
        Note(id, seizedNote(ctx.activePlayer)))))

  /** What the take moved to the actor, and from whom. */
  private def seizedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    (what, owner) <- NoteSupport.answer(states, decisionId) match
      case Some(DecisionOptionRef.RelicSlot(owner, _)) =>
        NoteSupport.relicsGained(step, actor).headOption
          .map(relic => (NoteArg.Card(relic), owner))
      case Some(DecisionOptionRef.Banner(banner))
          if BannerRules.holder(step._2.game.current, banner).contains(actor) =>
        BannerRules.holder(step._1.game.current, banner)
          .map(owner => (NoteArg.Banner(banner), owner))
      case _ => None
  yield seized(PowerSourceRef.Card(VisionRules.Conspiracy),
    NoteArg.Player(actor), what, NoteArg.Player(owner))
```

`BannerRules` and `VisionRules` are already imported.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS, the golden logs unchanged.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/Gossip.scala src/main/scala/oathdigital/gameplay/powers/cardplay/BookBinders.scala src/main/scala/oathdigital/gameplay/powers/search/TruthfulHarp.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala src/test/scala/oathdigital/gameplay/powers/cardplay/GossipSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/BookBindersSuite.scala src/test/scala/oathdigital/gameplay/powers/search/TruthfulHarpSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala
git commit -m "feat(powers): the card-play powers say what they did"
```

---

### Checkpoint A

Dispatch one review subagent (Sonnet or lower). Give it the spec, this plan, and `git diff <plan commit>..HEAD`. Ask it to check:

- each note's wording against the spec tables and decisions 2 and 3;
- that no `Note` comes before a `Decide` that was already in its tree (decision 1), and that Book Binders' window still adds the same number of nodes whatever the banks hold;
- that the unchanged-step change in `ProcedureWalker` touches only `previous`, and that the file is at or under 800 lines;
- that every note reads what happened (a step, the live state, or Dazzle's fold), never a printed number, except Gleaming Armor's fixed cost in Task 4;
- that the tests would fail without the change.

Fix what it confirms before Task 4.

---

### Task 4: The Campaign powers say what they did

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala`, `Mercenaries.scala`, `StickyFire.scala`, `Outriders.scala`, `WarningSignals.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/GleamingArmorSuite.scala`, `MercenariesSuite.scala`, `StickyFireSuite.scala`, `WarningSignalsSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/OutridersSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.killedKey`, `gainedKey`, `answer` (Task 1).
- Produces:
  - `GleamingArmor.taxed` (`"taxed"`), `GleamingArmor.Secret: Int`.
  - `Mercenaries.discarded` (`"discarded"`).
  - `StickyFire.burned` (`"burned"`), `StickyFire.killed` (`"killed"`), `StickyFire.gave` (`"gained"`).
  - `Outriders.ignored` (`"ignored"`).
  - `WarningSignals.redistributed` (`"redistributed"`).
  - `KnightsErrant.campaigns` (`"campaigns"`).

- [ ] **Step 1: Write the failing tests**

In each existing suite below, add `import oathdigital.gameplay.powers.NoteText`. In `StickyFireSuite.scala` and `WarningSignalsSuite.scala`, also add `import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog`. In `KnightsErrantSuite.scala`, add `NoteText` to the `oathdigital.gameplay.powers` import instead.

`GleamingArmorSuite.scala`, append:

```scala
  // ---- Lines ----

  private val power = GleamingArmor.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a taxed plan writes the Armor's line, naming the plan's user"):
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    // Pricing the offered plans writes nothing: only a plan applied does.
    assertEquals(said(run.events), Vector.empty)
    val picked = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(watchdog)))
    assertEquals(said(picked.events), Vector(NoteText.Said("taxed",
      s"${b.other.value}'s battle plans cost 1 extra secret.", covers = false)))

  test("the holder's own plan is not taxed, and writes nothing"):
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(withAdviserFor(base, base.other, armor,
      Orientation.FaceUp), base.other, watchdog, Orientation.FaceUp), base.other, 0)
    val picked = commit(rules(losing), b, 4).pick(b.other,
      CampaignIds.defenderPlan, DecisionOptionRef.Denizen(DenizenId(watchdog)))
    assertEquals(said(picked.events), Vector.empty)
```

If the first `assertEquals(said(run.events), Vector.empty)` fails, pricing journals notes. Stop and report.

`MercenariesSuite.scala`, append:

```scala
  // ---- Lines ----

  private val power = Mercenaries.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a defeated user's discard is written as the Mercenaries' line"):
    val b = attackerBoard
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(said(done.events), Vector(NoteText.Said("discarded",
      s"Discarded after ${b.actor.value} lost.", covers = false)))

  test("a winner keeps Mercenaries and reads no line"):
    val b = attackerBoard
    val done = commit(rules(winning), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(said(done.events), Vector.empty)
```

`StickyFireSuite.scala`, append:

```scala
  // ---- Lines ----

  private val power = StickyFire.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a burnt force is written: what died, and the favor given"):
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = favored(withRelic(replacePlayer(base, base.other)(p => p.copy(
      board = p.board.copy(warbands = 3))), relic), base.actor, 1)
    val done = commit(rules(winning), b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      s"Killed 3 ${b.other.value} warbands, and ${b.other.value} gained 1 favor.",
      covers = false)))

  test("a winner with no favor to give writes only the kill"):
    val b = favored(conquest, conquest.actor, 0)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${b.other.value} warband.", covers = false)))

  test("sparing the force, or burning bandits, writes nothing"):
    val b = conquest
    val spared = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.no).finish
    assertEquals(said(spared.events), Vector.empty)
    val base = board()
    val bandits = favored(withRelic(base, relic), base.actor, 2)
    val burnt = commit(rules(winning), bandits, 4)
      .pick(bandits.actor, CampaignIds.attackerPlan, ref)
      .answer(bandits.actor, CampaignIds.sacrifice,
        DecisionAnswer.ChooseAmountAnswer(0))
      .pick(bandits.actor, decision, StickyFire.yes).finish
    assertEquals(said(burnt.events), Vector.empty)
```

`WarningSignalsSuite.scala`, append:

```scala
  // ---- Lines ----

  private val power = WarningSignals.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the arrangement is written as the Signals' line, after the answer"):
    val b = defending
    val asked = chosen(b)
    assertEquals(said(asked.events), Vector.empty)
    val done = asked.answer(b.other, decision, arrangement(b, 1, 3, 3))
    assertEquals(said(done.events), Vector(NoteText.Said("redistributed",
      s"${b.other.value} redistributed their warbands.", covers = false)))

  test("a defender with nowhere to move to writes nothing"):
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = replacePlayer(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other)(p => p.copy(board = p.board.copy(
      warbands = 3)))
    val done = commit(rules(losing), b, 2, raid = true).pick(b.other,
      CampaignIds.defenderPlan, ref).finish
    assertEquals(said(done.events), Vector.empty)
```

`KnightsErrantSuite.scala`, append:

```scala
  // ---- Lines ----

  private val power = KnightsErrant.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("choosing to campaign writes the Knights' line after the choice"):
    val asked = musterFrom(staged(), modifiers)
    assertEquals(said(asked.events), Vector.empty)
    val chosen = answer(asked, KnightsErrant.decisionId, campaign)
    assertEquals(said(chosen.events), Vector(NoteText.Said("campaigns",
      s"${actor.value} campaigns for no Supply.", covers = false)))

  test("declining writes nothing"):
    assertEquals(said(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, decline).events), Vector.empty)
```

Create `OutridersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Outriders' line (power log lines design, "Altered procedures"): "Skulls
  * ignored.", written only when the attack rolled a skull to ignore.
  */
class OutridersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.outriders")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val power = Outriders.forCatalog(catalog).get

  /** The attacker holds Outriders, Campaigns with two warbands, chooses it,
    * and rolls `attack`. */
  private def rolled(attack: Vector[AttackDieFace]): Run =
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val defense = Vector.fill(catalog.sites.find(_.id == b.origin).get.defense)(
      DefenseDieFace.Blank)
    commit(rules(dice(attack, defense)), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish

  private def said(run: Run): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, run.events)

  test("an attack roll with a skull writes that skulls were ignored"):
    assertEquals(said(rolled(Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword))), Vector(NoteText.Said("ignored",
      "Skulls ignored.", covers = false)))

  test("a roll with no skull writes nothing"):
    assertEquals(said(rolled(Vector.fill(2)(AttackDieFace.OneSword))),
      Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.economy.KnightsErrantSuite"`
Expected: the tests that expect a line FAIL with an empty vector. The "writes nothing" tests pass.

- [ ] **Step 3: Gleaming Armor**

In `GleamingArmor.scala`:

1. Add to the class doc: "Each taxed plan writes "{Red}'s battle plans cost 1 extra secret." after the plan's own effects, so it never comes before a decision the plan asks. Two taxed plans in one Campaign post one line."
2. Add `override def noteKeys: Vector[NoteKey] = Vector(GleamingArmor.taxed)`.
3. In `contributions`, replace `surcharge(ctx, application).fold(children)(_ +: children)` with `surcharge(ctx, application).fold(children)(cost => cost +: children :+ note(application))`.
4. Add:

   ```scala
  /** Its line, naming the plan's user; a bandit plan has none and pays
    * nothing. */
  private def note(application: CampaignPlanApplication): Note =
    Note(id, _ => application.user.map(user => GleamingArmor.taxed(
      PowerSourceRef.Card(cardId), NoteArg.Player(user),
      NoteArg.Number(GleamingArmor.Secret))))
   ```

5. Replace `Cost(secret = 1)` with `Cost(secret = GleamingArmor.Secret)` and `FlipSecrets(user, 1,` with `FlipSecrets(user, GleamingArmor.Secret,`.
6. Add to the companion:

   ```scala
  /** The added cost, in secrets. */
  val Secret: Int = 1
  /** "{Red}'s battle plans cost {1} extra secret." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Plural(1, " extra secret.", " extra secrets.")))
   ```

- [ ] **Step 4: Mercenaries**

In `Mercenaries.scala`, add to the class doc "The discard writes "Discarded after {Red} lost.", read from the discard's step.", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(Mercenaries.discarded)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(false) then Vector.empty
      else use.user.toVector.flatMap(user => Vector(
        PlanDiscard.denizen(catalog, user, cardId),
        Note(id, discardNote(user))))))

  /** Its line, when the discard's step took the card out of play. */
  private def discardNote(user: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    if inPlay(step._1) && !inPlay(step._2)
  yield Mercenaries.discarded(PowerSourceRef.Card(cardId), NoteArg.Player(user))

  private def inPlay(ready: ReadyGame): Boolean =
    val current = ready.game.current
    current.players.exists(_.advisers.exists(_.id == cardId)) ||
      current.map.sites.values.exists(_.denizens.exists(_.id == cardId))
```

Add to the companion:

```scala
  /** "Discarded after {Red} lost." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded after "), NotePart.Arg(0), NotePart.Text(" lost.")))
```

- [ ] **Step 5: Sticky Fire**

In `StickyFire.scala`, import `{CatalogCards, NoteSupport, PlayerFacts, PowerAnswers}`, and add to the class doc: "After the burn it writes what died and the favor given, read from the burn's step: "Killed 3 {Blue} warbands, and {Blue} gained 1 favor.", or either half alone. Against bandits it writes nothing: no player lost a warband or gained the favor."

Add to the class:

```scala
  override def noteKeys: Vector[NoteKey] =
    Vector(StickyFire.burned, StickyFire.killed, StickyFire.gave)
```

In `wrapping`, replace `yield ask(user) +: (losses :+ burn(use, user, result))` with:

```scala
    yield ask(user) +: (losses :+ burn(use, user, result) :+
      Note(id, burnNote(use.side, result)))
```

Add:

```scala
  /** What the burn did to the loser, read from its step. */
  private def burnNote(side: CampaignPlanSide, result: CampaignResult)(
      states: NoteStates): Option[PowerNote] =
    val card = PowerSourceRef.Card(relicId)
    for
      loser <- loserOf(side, result)
      if NoteSupport.answer(states, StickyFire.decisionId).contains(StickyFire.yes)
      step <- states.previous
      killed = -NoteSupport.warbands(step, loser)
      gift = NoteSupport.favor(step, loser)
      note <- (killed > 0, gift > 0) match
        case (true, true) => Some(StickyFire.burned(card, NoteArg.Number(killed),
          NoteArg.Player(loser), NoteArg.Amount(gift, NoteUnit.Favor)))
        case (true, false) => Some(StickyFire.killed(card,
          NoteArg.Number(killed), NoteArg.Player(loser)))
        case (false, true) => Some(StickyFire.gave(card, NoteArg.Player(loser),
          NoteArg.Amount(gift, NoteUnit.Favor)))
        case _ => None
    yield note

  /** The player the user beat; bandits are no player. */
  private def loserOf(side: CampaignPlanSide, result: CampaignResult)
      : Option[PlayerId] = (side, result.defender) match
    case (CampaignPlanSide.Defender, _) => Some(result.attacker)
    case (_, CampaignDefender.Player(defender)) => Some(defender)
    case (_, CampaignDefender.Bandits) => None
```

Add to the companion:

```scala
  /** "Killed {n} {Blue} warband, and {Blue} gained {1 favor}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband, and ", " warbands, and "), NotePart.Arg(1),
    NotePart.Text(" gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** The kill alone, when the winner had no favor to give. */
  val killed: NoteKey = NoteSupport.killedKey("killed")
  /** The favor alone, when the loser's board held no warband. */
  val gave: NoteKey = NoteSupport.gainedKey("gained")
```

- [ ] **Step 6: Outriders**

In `Outriders.scala`, add to the class doc "When the attack rolled a skull it writes "Skulls ignored."", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (_ => Vector(BuildOps((ready, _) =>
      Right(ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .map(_ => ModifyRollOutcome(CampaignIds.attackPool, Some(0),
          Some(AttackDieFace.score(CampaignBattle.attackFacesOf(ready))))))),
      Note(id, ignoredNote))))

  /** Its line, only when the attack rolled a skull to ignore. The rewrite
    * keeps the faces, so they still show the skulls. */
  private def ignoredNote(states: NoteStates): Option[PowerNote] =
    Option.when(AttackDieFace.skulls(CampaignBattle.attackFacesOf(states.now)) > 0)(
      Outriders.ignored(PowerSourceRef.Card(cardId)))
```

Add to the companion:

```scala
  /** "Skulls ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Skulls ignored.")))
```

- [ ] **Step 7: Warning Signals**

In `WarningSignals.scala`, add to the class doc "Once the defender has answered it writes "{Blue} redistributed their warbands."; the line follows the decision, so a game parked on it resumes where it was.", add `override def noteKeys: Vector[NoteKey] = Vector(WarningSignals.redistributed)`, and in `rearrange` put the note between the `Decide` and the `BuildOps`:

```scala
      Vector(
        Decide(WarningSignals.decisionId, user, DecisionQuery.Distribute.exactly(
          slots, total, Some("Warning Signals: arrange your warbands. Your " +
            "board holds the ones no site keeps, and each site keeps at least one"),
          "Move warbands")),
        Note(id, _ => Some(WarningSignals.redistributed(
          PowerSourceRef.Card(cardId), NoteArg.Player(user)))),
        BuildOps((state, pending) => PowerAnswers.distribution(pending,
          WarningSignals.decisionId).toRight(PowerAnswers.missing(
          WarningSignals.decisionId)).flatMap(rows => moves(state, user, rows))))
```

Add to the companion:

```scala
  /** "{Blue} redistributed their warbands." */
  val redistributed: NoteKey = NoteKey("redistributed", Vector(NotePart.Arg(0),
    NotePart.Text(" redistributed their warbands.")))
```

- [ ] **Step 8: Knights Errant**

In `KnightsErrant.scala`, import `{CatalogCards, NoteSupport, PowerAnswers, SelectedModifier}`, add to the class doc "Choosing to campaign writes "{Red} campaigns for no Supply." right after the choice.", add `override def noteKeys: Vector[NoteKey] = Vector(KnightsErrant.campaigns)`, and make `offer` end with the note:

```scala
  private def offer(actor: PlayerId): Operation = Branch((ready, _) =>
    if CampaignSetup.legalKinds(ready, actor).isEmpty then Vector.empty
    else Vector(Decide(KnightsErrant.decisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(KnightsErrant.campaignOption, "Campaign"),
        DecisionOption.Button(KnightsErrant.declineOption, "Do not campaign")),
      heading = Some("Knights Errant: campaign for no Supply?"))),
      Note(id, campaignNote(actor))))

  /** Its line, once the player chose to campaign. */
  private def campaignNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    Option.when(NoteSupport.answer(states, KnightsErrant.decisionId)
      .contains(KnightsErrant.campaignOption))(KnightsErrant.campaigns(
      PowerSourceRef.Card(cardId), NoteArg.Player(actor)))
```

Add to the companion:

```scala
  /** "{Red} campaigns for no Supply." */
  val campaigns: NoteKey = NoteKey("campaigns", Vector(NotePart.Arg(0),
    NotePart.Text(" campaigns for no Supply.")))
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.economy.* oathdigital.gameplay.Campaign* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS, the golden logs unchanged.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala src/main/scala/oathdigital/gameplay/powers/campaign/Mercenaries.scala src/main/scala/oathdigital/gameplay/powers/campaign/StickyFire.scala src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala src/test/scala/oathdigital/gameplay/powers/campaign/GleamingArmorSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/MercenariesSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/StickyFireSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/WarningSignalsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/OutridersSuite.scala src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala
git commit -m "feat(powers): the Campaign powers say what they did"
```

---

### Task 5: The Mob, the Homeland rule and League Treaty; record the slice

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyContribution.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala`
- Create: `src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatySuite.scala`, `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`, `docs/ROADMAP.md`

**Interfaces:**
- Produces:
  - `PlacementRules.discardFirst: NoteKey` (`"discard-first"`).
  - `PlacementRules.siteDiscardNote: Option[Note]`, `PlacementRules.withSiteDiscardFirstBy(note: Note): PlacementRules`.
  - `RuleNotes.homelandDiscard: PowerId` (`"rule.homeland-discard"`), `RuleNotes.all: Vector[(PowerId, Vector[NoteKey])]`.
  - `LeagueTreatyContribution.sent` (`"sent"`).

- [ ] **Step 1: Write the failing tests**

`PeoplesFavorMobSuite.scala`: add `NoteText` to the `oathdigital.gameplay.powers` import and append:

```scala
  // ---- Lines ----

  private def mobSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys, events)

  test("the discard the Mob permits writes its line once it is answered"):
    val hearth = denizensOf(Suit.Hearth)
    val asked = toSite(staged(Vector(hearth(0), hearth(1))))
    assertEquals(mobSaid(asked.events), Vector.empty)
    val done = discardAnswer(asked, noDiscard).toOption.get
    assertEquals(mobSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a play asked no discard writes no Mob line"):
    assertEquals(mobSaid(toSite(staged(Vector.empty)).events), Vector.empty)
```

Create `src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * a full Homeland of the card's suit asks for a discard, and the Homeland
  * says why once the discard is answered.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture._

  private val hall = EdificeId("E16")
  private val none = WalkerPowers.empty

  /** A full site whose Homeland is the Hall, ruled by the actor, and a card
    * in hand whose suit matches the Hall's or not. */
  private def fullHomeland(matching: Boolean): (ReadyGame, PlayerId, DenizenId) =
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(id =>
      catalog.suitOf(id).contains(hallSuit) == matching).get
    val (_, _, probe) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == probe).get.capacity
    val fillers = cards.filter(_ != card).take(capacity - 1)
    val (ready, actor, site) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, EdificeSide.Intact, Tokens.empty))
    (ruledByActor(ready, site), actor, card)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(PlacementRules.discardFirst),
      events)

  test("a play to a full matching Homeland writes the Homeland's line after " +
      "the discard"):
    val (ready, actor, card) = fullHomeland(matching = true)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, asked) = answer(ready, tree,
      park(ready, tree, none), none, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor): @unchecked
    assertEquals(said(asked), Vector.empty)
    val discarded = options(ready, tree, pending, none).head
    val WalkerOutcome.Finished(_, events) = answer(ready, tree, pending, none,
      decisionId(card, "replace"), discarded, actor): @unchecked
    assertEquals(said(events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a full Homeland of another suit offers no site, and writes nothing"):
    val (ready, actor, card) = fullHomeland(matching = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, events) = ProcedureWalker.advance(ready,
      tree, None, none).toOption.get: @unchecked
    assert(!options(ready, tree, pending, none)
      .contains(DecisionOptionRef.Button("site")))
    assertEquals(said(events), Vector.empty)
```

Add `ProcedureWalker` to the `oathdigital.gameplay.walker` import of that file (`{ProcedureWalker, WalkerOutcome, WalkerPowers}`).

`LeagueTreatySuite.scala`: change the `oathdigital.gameplay.powers` import to `{NoteText, WalkerPowerCatalog}` and append:

```scala
  // ---- Lines ----

  private val power = LeagueTreatyContribution.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the treaty's line says how much favor the ruler sent to one bank"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    val done = rules.resolveWalker(chosen.state, owner, distribution,
      DecisionAnswer.DistributeAnswer(Vector(Suit.Arcane -> 0,
        Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3).map {
          case (suit, n) => DistributeAmount(bank(suit), n) })).toOption.get
    assertEquals(said(parked.events ++ chosen.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("sent",
      s"${owner.value} sent 3 favor to the Nomad bank.", covers = false)))

  test("a ruler who declines sends nothing, and writes nothing"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val declined = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline")))
      .toOption.get
    assertEquals(said(parked.events ++ declined.events), Vector.empty)
```

`PowerNoteCatalogSuite.scala`: add `import oathdigital.gameplay.actions.RuleNotes`, append `++ RuleNotes.all` to `declared`, and add:

```scala
  test("the Homeland rule declares the line it writes"):
    assertEquals(declared.toMap.get(RuleNotes.homelandDiscard)
      .map(_.map(_.name)), Some(Vector("discard-first")))
```

`GameLogPowerLinesSuite.scala`: add `import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}` and:

```scala
  test("a game rule's line is worded from RuleNotes"):
    assertEquals(NoteWordings.default(catalog).template(RuleNotes.homelandDiscard,
      PlacementRules.discardFirst.name), Some(PlacementRules.discardFirst.template))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.HomelandLineSuite oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite oathdigital.gameplay.powers.rest.LeagueTreatySuite"`
Expected: compilation FAILS: `RuleNotes` and `PlacementRules.discardFirst` do not exist.

- [ ] **Step 3: The shared key and the Mob's note in `PlacementRules`**

In `PlacementRules.scala`:

1. Change the import to `import oathdigital.model.{Note, NoteKey, NotePart, Orientation}`.
2. Add a bullet to the class doc after `siteDiscardFirst`'s:

   ```scala
  *  - `siteDiscardNote` is the line of the power that permits the discard.
  *    Card play writes it after the discard answer at a site. Without the
  *    permission, the discard is asked only at a full Homeland of the card's
  *    suit, and card play writes the Homeland rule's line there instead.
   ```

3. Add the field last, so positional construction still compiles:

   ```scala
    siteDiscardFirst: Boolean = false,
    siteDiscardNote: Option[Note] = None):
   ```

4. Add after `withSiteDiscardFirst`:

   ```scala
  /** Permits a discard before a play to a site, and names the line the
    * permitting power writes where the discard is asked. */
  def withSiteDiscardFirstBy(note: Note): PlacementRules =
    copy(siteDiscardFirst = true, siteDiscardNote = Some(note))
   ```

5. Add to the companion:

   ```scala
  /** "{Red} may discard a card at their site first.": the line of the power
    * or rule that lets a play to a site discard first. */
  val discardFirst: NoteKey = NoteKey("discard-first", Vector(NotePart.Arg(0),
    NotePart.Text(" may discard a card at their site first.")))
   ```

- [ ] **Step 4: Create `RuleNotes`**

```scala
package oathdigital.gameplay.actions

import oathdigital.model.{NoteKey, PowerId}

/** The game rules that write Game Log lines (power log lines design, "Game
  * rules"): each rule's id and the keys it writes. A rule is not a power, so
  * the formatter reads this beside the two power catalogs.
  */
object RuleNotes:
  /** Card play's Homeland rule: a full Homeland takes a card of its suit
    * after a discard. */
  val homelandDiscard: PowerId = PowerId("rule.homeland-discard")

  val all: Vector[(PowerId, Vector[NoteKey])] =
    Vector(homelandDiscard -> Vector(PlacementRules.discardFirst))
```

- [ ] **Step 5: Card play writes the line after the discard answer**

In `CardPlayProcedure.scala`:

1. Change the import to `import oathdigital.gameplay.actions.{CardPlay, PlacementRules, RuleNotes}`.
2. In `childrenFor`'s `selected` branch, replace `choice ++ Vector(apply) ++ hook` with:

   ```scala
            // The line of whatever asked for the discard, after its answer
            // and before the play. A note never comes before a decision
            // already in the tree, so a game parked on one resumes where it
            // was; it joins the play's own node, keeping the hook's place.
            val notice: Vector[Operation] = placement match
              case _: SearchPlacement.Site if replacements.nonEmpty =>
                if rules.siteDiscardFirst then rules.siteDiscardNote.toVector
                else homelandNote(ready, actor).toVector
              case _ => Vector.empty
            val played = if notice.isEmpty then apply
              else Sequence(notice :+ apply)
            choice ++ Vector(played) ++ hook
   ```

3. Add:

   ```scala
  /** The Homeland rule's line, "{site}: {Red} may discard a card at their
    * site first.": without a power's permission, only a full Homeland of the
    * card's suit asks for a discard at a site. */
  private def homelandNote(ready: ReadyGame, actor: PlayerId): Option[Note] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .map(site => Note(RuleNotes.homelandDiscard, _ => Some(
        PlacementRules.discardFirst(PowerSourceRef.Site(site),
          NoteArg.Player(actor)))))
   ```

The `settled` path yields replacements exactly when the discard was asked, so its tree keeps the same shape.

- [ ] **Step 6: The Mob names itself**

In `PeoplesFavorMob.scala`, add `import oathdigital.gameplay.actions.PlacementRules`, add to the object doc "Its line, "{Red} may discard a card at their site first.", travels in the rules, and card play writes it after the discard answer.", and:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(PlacementRules.discardFirst)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.withSiteDiscardFirstBy(note(ctx.activePlayer)))
        case _ => children
      })))

  private def note(actor: PlayerId): Note = Note(id, _ => Some(
    PlacementRules.discardFirst(PowerSourceRef.Banner(Banner.PeoplesFavor),
      NoteArg.Player(actor))))
```

- [ ] **Step 7: League Treaty says what it sent**

In `LeagueTreatyContribution.scala`:

1. Add to the class doc: "After the moves it writes "{Blue} sent 3 favor to the Nomad bank.", read from their step. Nothing is written when the ruler declines or nothing moves. The line follows the moves, so it never comes before either decision."
2. Add `override def noteKeys: Vector[NoteKey] = Vector(sent)`.
3. Append the note to `inserted`'s vector, after the `BuildOps`:

   ```scala
      Note(id, sentNote(treaty.ruler, destination)))
   ```

4. Add:

   ```scala
  /** What the moves' step sent to the chosen bank. */
  private def sentNote(ruler: PlayerId, destination: String)(
      states: NoteStates): Option[PowerNote] = for
    bank <- states.answered.collectFirst {
      case Answered(`destination`, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.FavorBank(suit)), _) => suit }
    step <- states.previous
    moved = step._2.banks.favor.getOrElse(bank, 0) -
      step._1.banks.favor.getOrElse(bank, 0)
    if moved > 0
  yield sent(PowerSourceRef.Card(cardId), NoteArg.Player(ruler),
    NoteArg.Amount(moved, NoteUnit.Favor), NoteArg.Bank(bank))
   ```

5. Add to the companion:

   ```scala
  /** "{Blue} sent {3 favor} to {the Nomad bank}." */
  val sent: NoteKey = NoteKey("sent", Vector(NotePart.Arg(0),
    NotePart.Text(" sent "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))
   ```

- [ ] **Step 8: The formatter reads the rules' keys**

In `NoteWordings.scala`, add `import oathdigital.gameplay.actions.RuleNotes`, and make `default`:

```scala
  /** The walker powers', the phase powers' and the game rules' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      of(power.id, power.noteKeys)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, power.noteKeys)) ++
      RuleNotes.all.map((id, keys) => of(id, keys)))
      .foldLeft(NoteWordings(Map.empty))(_ ++ _)
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.HomelandLineSuite oathdigital.gameplay.SiteDiscardFirstSuite oathdigital.gameplay.PlacementRulesSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.DiscardRestrictionsSuite oathdigital.gameplay.powers.banner.* oathdigital.gameplay.powers.rest.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS, the golden logs unchanged.

- [ ] **Step 10: Amend the spec**

In `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`:

1. Add under the slice 3 status line:

   ```markdown
   **Slice 4 (2026-09-26):** the added effects and altered procedures.
   "Settled in slice 4" records what it decided.
   ```

2. In section 1's `NoteArg` table, add after the `Dice` row:

   ```markdown
   | `Number(n)` | a bare number, "Total: 8" |
   | `Cards(ids)` | one phrase of card chips, "Tinker, 2 Denizens and a Vision", each card by the knowledge rule |
   | `Banner(banner)` | the banner's name |
   ```

3. In "Amounts are what happened", append to the bullet that starts "**Amounts are what happened**": "A leaf that ran and changed nothing gives `previous` the same state twice, so a note after it reads no change."

4. Replace the "Added effects" table with:

   ```markdown
   | Card | Line | Covers |
   |---|---|---|
   | Toll Roads, key `paid` | Toll Roads: {Red} paid {1 favor} to {Blue}. | |
   | Toll Roads, key `burned`, when bandits rule | Toll Roads: {Red} burned {1 favor}. | |
   | Grasping Vines | Grasping Vines: Killed {n} {Red} warband. | |
   | Gossip | Gossip: {Blue} gained {n} favor from {the Discord bank}. | the Gain line |
   | Book Binders | Book Binders: {Blue} gained {n} favor from {the Order bank}. | the Gain line |
   | Gleaming Armor, after the taxed plan | Gleaming Armor: {Red}'s battle plans cost {1} extra secret. | |
   | Conspiracy (when played) | Conspiracy: {Red} seized {relic or banner} from {Blue}. | |
   | Dazzle | Dazzle: Discarded {cards}. | the Discard line |
   | Mercenaries | Mercenaries: Discarded after {Red} lost. | |
   | Sticky Fire, key `burned` | Sticky Fire: Killed {n} {Blue} warband, and {Blue} gained {1 favor}. | |
   | Sticky Fire, key `killed`, the winner had no favor | Sticky Fire: Killed {n} {Blue} warband. | |
   | Sticky Fire, key `gained`, no warband to kill | Sticky Fire: {Blue} gained {1 favor}. | |
   | Truthful Harp | Truthful Harp: Revealed {cards}. | the Peeked lines |
   | Forest Paths, destination with a beast card | Forest Paths: Ignoring site powers. | |
   | Dragonskin Drum | Dragonskin Drum: {Red} gained {1 warband}. | |
   ```

5. Replace the "Altered procedures" table with:

   ```markdown
   | Card | Line |
   |---|---|
   | Outriders, when the attack rolled a skull | Outriders: Skulls ignored. |
   | Warning Signals, after the arrangement | Warning Signals: {Blue} redistributed their warbands. |
   | Knights Errant, after the choice to campaign | Knights Errant: {Red} campaigns for no Supply. |
   | People's Favor (Mob face), after the discard answer | People's Favor: {Red} may discard a card at their site first. |
   | Homeland rule, at a full Homeland matching the played card's suit, after the discard answer | {Homeland site}: {Red} may discard a card at their site first. |
   | League Treaty, after the moves | League Treaty: {Blue} sent {n favor} to {the Nomad bank}. |
   ```

6. In "Game rules", replace "declares its templates in `RuleNotes`, a small registry the formatter consults beside the two power catalogs." with "declares its templates in `RuleNotes` (in `gameplay.actions`), a small registry the formatter consults beside the two power catalogs.", and replace "The card-play planner puts a `Note` with that id in the placement tree when the rule offers the discard." with "The card-play planner puts a `Note` with that id in the placement tree after the discard answer."

7. Add this section after "Settled in slice 3":

   ```markdown
   ## Settled in slice 4

   The fourth slice settled these:

   - A note never comes before a decision already in the tree. A game
     parked on a decision stores its position, and a node inserted before it
     would move it. The "may" lines of Knights Errant, the Mob and the
     Homeland rule therefore post after the choice they explain. Warning
     Signals and League Treaty post after it too, and say what happened:
     "{Blue} redistributed their warbands.", "{Blue} sent {3 favor} to {the
     Nomad bank}.".
   - A leaf that ran and changed nothing is the step a note after it reads,
     as its unchanged state twice. Every note reads a difference, so it
     writes nothing there, and no older step is restated by mistake.
   - `NoteArg.Cards` names several cards as one phrase, and `NoteArg.Banner`
     names a banner. Truthful Harp and Dazzle list their cards. Conspiracy
     names the relic or banner it seized.
   - A note that cannot read one step reads the state its transform was
     folded with, which is the state before the step. Dazzle lists the
     cards it would discard there that no site holds when it writes.
   - The Mob's line travels in `PlacementRules.siteDiscardNote`, so card
     play still names no power. Without that permission card play writes
     the Homeland rule's line. Both use `PlacementRules.discardFirst`.
   - `RuleNotes` lives in `gameplay.actions`, beside `PlacementRules`.
   - Sticky Fire writes what the burn did, in three keys, and nothing
     against bandits. Outriders writes only when a skull was rolled. Forest
     Paths writes only when it ignores site powers. Toll Roads writes `paid`
     or `burned`. Dragonskin Drum names the player who gained.
   - Gleaming Armor's line posts after the taxed plan's own effects. Its
     extra secret is a `Number` with a `Plural`.
   - `NoteSupport.killedKey` and `NoteSupport.gainedFromKey` are the kill and
     the gain-from-a-bank sentences that Wolves, Gambling Hall and this
     slice's powers share.
   ```

In `docs/ROADMAP.md`, replace "Slices 4 and 5 remain: added effects and altered procedures, then setup." with:

```markdown
Slice 4 gives every added effect and altered procedure its line. Slice 5
remains: setup.
```

- [ ] **Step 11: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass. The server count is your baseline plus this slice's new tests.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyContribution.scala src/main/scala/oathdigital/application/gamelog/NoteWordings.scala src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatySuite.scala src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala docs/superpowers/specs/2026-09-26-power-log-lines-design.md docs/ROADMAP.md
git commit -m "feat(powers): the Mob, the Homeland rule and League Treaty say what they did; record slice 4"
```

---

### Checkpoint B

Dispatch one review subagent (Sonnet or lower). Give it the spec, this plan, and `git diff <plan commit>..HEAD`. Ask it to check:

- every row of the amended "Added effects" and "Altered procedures" tables has a note and a test asserting its exact sentence;
- that no `Note` comes before a decision already in its tree, including card play's `Sequence(notice :+ apply)`, whose `settled` path must keep the same shape;
- that card play names no power: the Mob's note arrives through `PlacementRules`;
- that "Settled in slice 4" matches what the code does;
- the gates' output.

Fix what it confirms, re-run the gates, then finish the branch with superpowers:finishing-a-development-branch.
