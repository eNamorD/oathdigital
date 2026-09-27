# Power Log Lines, Slice 3 (Removed and Hidden Options) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each implemented power that hides an option at a decision its own Game Log line, using the slice-1 hide hook, so the log says why an option was missing.

**Architecture:**

- Every power in this slice already hides its options. Slice 1 built the hook that lets it say so:
  - An `OptionRestriction` hides options of the `Decide` its window hooks. Its `note` field is asked once for each option it hid.
  - A `Restriction` hides options through the look-ahead. Its `note` field is asked for each option whose answer would break it.
- Each power fills that `note` field and declares the `NoteKey` it uses. The walker, the journal and the formatter do not change.
- The Circlet of Command narrows a Conspiracy's target with a `Transform`, not a restriction. There the transform leaves a `Note` node before the decision, as Vow of Peace's transform does.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change. Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

- Before starting, read the Rulings, section 1's "Emission path 2: the hide hook", section 2's "Removed and hidden options" table, and "Settled in slice 1" and "Settled in slice 2".
- The slice 2 plan, `docs/superpowers/plans/2026-09-26-power-log-lines-slice2.md`, shows how phase powers got their lines.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file edited below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). This slice touches no file near the limit, and does not touch anything under `gameplay/walker`.
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
- Line wording follows the spec's "Removed and hidden options" table, except where "Decisions this plan makes" below changes it. A template starts with an argument or a capital letter. A catalog test already checks this.
- Players are named by chip, never "you". A hide note names the acting player as `ctx.activePlayer`.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 4 records each one in the spec.

1. **Narrow Pass writes no Travel line.** Travel's destination is part of the command. No decision offers destinations, so there is no option to hide. A Travel the Pass blocks is refused whole, which ruling 3 leaves without a line. The spec's "Narrow Pass, Travel" row moves to "No line". The Campaign row stays.
2. **A Raid hidden from several protected players.** The hook is asked once per hidden option. The kind decision's Raid is one option, and it can be hidden because of several players. Only the Rotting Fortress protects more than one player at a site: an Oaken Fortress protects only its ruler.
   - One player could be raided: key `shielded`, "Oaken Fortress: {Blue} cannot be targeted.".
   - Several players could be raided: key `all-shielded`, "Rotting Fortress: No player at {site} can be targeted.".
3. **The Circlet's Conspiracy line.** The Conspiracy's target decision is narrowed by the Circlet's `Transform`, not by a hide hook. When the transform drops any option, it puts a `Note` before the decision. That includes the case where it drops every option and the decision goes: ruling 1 gives a removed decision its line. The sentence is the one the Circlet writes at a Raid or Challenge.
4. **One shared Vision sentence.** Vow of Obedience, Secret Police and Sacred Ground share one key, `no-faceup`, declared in `VisionPlay`: "{Red} cannot play a Vision faceup.". A Search that draws two Visions hides two faceup options. The formatter's existing merge then posts one line per action.
5. **One line per hidden option, from one power.** The look-ahead credits a hidden option to the first restriction whose violation the option's answer adds. When Vow of Obedience and Sacred Ground both forbid the same Vision, one line posts. This is the slice-1 mechanism unchanged; the plan records it.
6. **A card suite reads any power's notes.** `NoteText.said` takes a `PhasePower`. It gains an overload taking a power id and its keys, so the suites of contributing powers can read their notes too.

### Facts this plan relies on (verified against the code on 2026-09-26, at `36657766`)

- **Mechanism (slices 1 and 2):**
  - `model/PowerNotes.scala`: `PowerNote(source, key, args)`, `NoteArg.{Player, Card, Site, ...}`, `NotePart.{Text, Arg, Plural}`, `NoteKey(name, template)` with `apply(source: PowerSourceRef, args: NoteArg*): PowerNote`.
  - `PowerSourceRef.{Card(id: CardId), Site(id: SiteId), Banner(banner)}` in `model/GameState.scala`. `DenizenId`, `RelicId` and `EdificeId` are `CardId`s.
  - `gameplay/powerresolver/ContributingPower.scala`:
    - `Restriction(fn, note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent)`;
    - `OptionRestriction(fn, note: ... = Contribution.silent)`;
    - `trait NotingPower { def noteKeys: Vector[NoteKey] }`, which `ContributingPower` and `PhasePower` share.
  - `gameplay/walker/WalkerPowerGather.scala`:
    - `hiddenNotes` asks, for each option an `OptionRestriction` forbids, the note of the first power that forbids it.
    - `lookAheadNotes` asks, for each option the look-ahead hid, the note of the `Restriction` whose violation that answer adds.
    - Both run only at a decision the walker reaches fresh. The notes journal as `PowerNoted(power, note, covers = false)` before the decision parks.
  - `application/gamelog/PowerLines.scala` holds a note until its action's start line posts, and drops a note equal to an earlier one of the same power in the same action (`repeated`).
  - `NoteWordings.default` reads `noteKeys` from every power in `WalkerPowerCatalog` and `PhasePowerCatalog`. `PowerNoteCatalogSuite` checks that every template starts with an argument or a capital letter.
  - Vow of Peace's `withoutSacrifice` in `gameplay/powers/campaign/VowOfPeaceContribution.scala` is the pattern for a transform that leaves a `Note`: `Vector(Note(id, _ => Some(note)))`.
- **The powers:**
  - `gameplay/powers/cardplay/VisionPlay.scala`: `private[cardplay] object VisionPlay` with `played`, `pending`, `forbidden`.
  - `VowOfObedience` (card `DenizenId("121")`, field `cardId`) is a `PhasePower with ContributingPower`. Its `noteKeys` is `Vector(NoteSupport.took)`. Its rule is `Restriction((ctx, _) => ...)` at `PowerWindow.ActionCardPlayedFaceup`.
  - `SecretPolice` (field `cardId: DenizenId`) and `SacredGround` (field `edifice: EdificeId`) each contribute one `Restriction((ctx, _) => blocked(ctx))` at `ActionCardPlayedFaceup`.
  - `gameplay/powers/targeting/FortressRules.scala`: `sealed abstract class FortressRule` with `protected def fortress: EdificeId`, `pawnSite`, `raidBlocked`, and four contributions: `kindGuard` (hides `Button("raid")`), `defenderGuard` (hides `Player(defender)`), `startGuard` (a whole-action `Restriction`, which stays silent) and `bannerGuard` (hides `Banner(banner)`). `OakenFortress` and `RottingFortress` extend it. There is no `object FortressRule` yet.
  - `CampaignSetup.raidDefenders(ready, actor): Vector[PlayerId]` lists the players a Raid could target.
  - `gameplay/powers/targeting/CircletOfCommand.scala`: `OptionRestriction(guard)` at `CampaignTargetSelection` and `ChallengeBannerSelection`, and `Transform(dropShielded)` at `ConspiracyTargetSelection`. `shields` computes the faceup holder inline.
  - `gameplay/powers/travel/TravelSitePowers.scala`: `NarrowPassSitePower(id, site, coastSites, coastOrIslandSites)`, `source` is `RuleSourceRef.Site(site)`. `OptionRestriction(campaignBlocked)` at `CampaignTargetSelection`; `Restriction((ctx, _) => blocked(ctx))` at `TravelActionEligibility`, a whole-action block.
  - Travel's destination is a command argument (`TravelProcedure.candidates` dry-runs each one). No `Decide` offers travel destinations.
- **Tests:**
  - `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`: `Said(key, text, covers)` and `said(power: PhasePower, events)`. A player argument reads as its id.
  - `VisionPlayFixture.searched(ready, vision)` and `fromAdvisers(ready, vision)` return the `OathTransition` that parks on the placement decision. Its `events` hold the notes. `PowerFixture.actor` is the acting player.
  - `TargetingFixture.start(state, procedure, actor): Either[OathViolation, OathTransition]`; `TargetingFixture.rules.resolveWalker(...)` answers a parked decision. `CampaignFixture.Board(ready, actor, other, origin)`; `origin` is the actor's pawn site.
  - `CampaignPowersSuite` tests the Narrow Pass on a hand-built `Decide` at `CampaignTargetSelection` through `ProcedureWalker.advance`, which returns `WalkerOutcome.Parked(pending, events)`.

---

### Task 1: The Vision powers say a Vision cannot be played faceup

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/SacredGround.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala`, `SecretPoliceSuite.scala`, `SacredGroundSuite.scala`

**Interfaces:**
- Produces: `NoteText.said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent]): Vector[Said]`; `VisionPlay.noFaceup: NoteKey` (name `"no-faceup"`); `VisionPlay.note(card: CardId): (PowerCtx, DecisionOptionRef) => Option[PowerNote]`.

- [ ] **Step 1: Let `NoteText` read any power's notes**

In `NoteText.scala`, replace `said` with:

```scala
  /** The notes `power` journaled in `events`, in order. */
  def said(power: PhasePower, events: Vector[OathEvent]): Vector[Said] =
    said(power.id, power.noteKeys, events)

  /** The notes the power `id`, declaring `keys`, journaled in `events`. */
  def said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent])
      : Vector[Said] =
    events.collect { case PowerNoted(`id`, note, covers) =>
      Said(note.key, sentence(keys, note), covers) }
```

- [ ] **Step 2: Write the failing tests**

In `VowOfObedienceSuite.scala`, add after the test "the Conspiracy is a Vision, so it is forbidden too":

```scala
  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = VowOfObedience.forCatalog(catalog).get
    NoteText.said(power.id, power.noteKeys, from.events)

  private val noFaceup = NoteText.Said("no-faceup",
    s"${actor.value} cannot play a Vision faceup.", covers = false)

  test("the hidden faceup placement is written as the Vow's line"):
    assertEquals(hidden(search(VisionRules.Faith, holding(_))).distinct,
      Vector(noFaceup))
    assertEquals(hidden(fromAdvisers(holding(inPhase(base, Phase.Act)),
      VisionRules.Faith)).distinct, Vector(noFaceup))

  test("a Vow that forbids nothing writes nothing"):
    assertEquals(hidden(search(VisionRules.Faith, holding(_,
      orientation = Orientation.FaceDown))), Vector.empty)
```

In `SecretPoliceSuite.scala`, add `NoteText` to the `oathdigital.gameplay.powers` import, and add at the end:

```scala
  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SecretPolice.forCatalog(catalog).get
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement is written as the Police's line"):
    assertEquals(hidden(search(home(base), exile(other), exile(other))).distinct,
      Vector(NoteText.Said("no-faceup",
        s"${actor.value} cannot play a Vision faceup.", covers = false)))

  test("a player the Police do not bind reads no line"):
    assertEquals(hidden(search(home(base), exile(actor), exile(actor))),
      Vector.empty)
```

In `SacredGroundSuite.scala`, add `NoteText` to the `oathdigital.gameplay.powers` import, and add at the end:

```scala
  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SacredGround.forCatalog(catalog).get
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement is written as Sacred Ground's line"):
    assertEquals(hidden(search(elsewhere)).distinct, Vector(NoteText.Said(
      "no-faceup", s"${actor.value} cannot play a Vision faceup.",
      covers = false)))

  test("the Conspiracy, which Sacred Ground excepts, reads no line"):
    assertEquals(hidden(search(elsewhere, VisionRules.Conspiracy)), Vector.empty)
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.VowOfObedienceSuite oathdigital.gameplay.powers.cardplay.SecretPoliceSuite oathdigital.gameplay.powers.cardplay.SacredGroundSuite"`
Expected: the three "hidden faceup placement" tests FAIL, each with an empty vector where one `Said` was expected. The "reads no line" tests pass.

- [ ] **Step 4: Declare the shared key in `VisionPlay`**

In `VisionPlay.scala`, add inside the object, after `forbidden`:

```scala
  /** Where a faceup placement was hidden (power log lines design, "Removed
    * and hidden options"): "{Red} cannot play a Vision faceup." */
  val noFaceup: NoteKey = NoteKey("no-faceup", Vector(NotePart.Arg(0),
    NotePart.Text(" cannot play a Vision faceup.")))

  /** The hide hook of a rule printed on `card`: the acting player cannot
    * play a Vision faceup. */
  def note(card: CardId): (PowerCtx, DecisionOptionRef) => Option[PowerNote] =
    (ctx, _) => Some(noFaceup(PowerSourceRef.Card(card),
      NoteArg.Player(ctx.activePlayer)))
```

The file already imports `PowerCtx` and `oathdigital.model._`, which covers `NoteKey`, `NotePart`, `CardId`, `PowerNote`, `PowerSourceRef` and `NoteArg`.

- [ ] **Step 5: Give each Vision power its note**

In `VowOfObedience.scala`:

```scala
  override def noteKeys: Vector[NoteKey] =
    Vector(NoteSupport.took, VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Restriction((ctx, _) =>
      VisionPlay.pending(ctx).filter(_ => holds(ctx.state, ctx.activePlayer))
        .map(_ => VisionPlay.forbidden("Vow of Obedience")),
      VisionPlay.note(cardId))))
```

In `SecretPolice.scala`:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(cardId))))
```

In `SacredGround.scala`:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(edifice))))
```

Add a sentence to each class's doc comment: "When the look-ahead hides a faceup placement because of it, it writes \"{Red} cannot play a Vision faceup.\"".

- [ ] **Step 6: Run the tests to verify they pass**

Run: the Step 3 command, then `./sbtw "testOnly oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`.
Expected: all PASS. If a golden log under `src/test/resources/gamelog/` now differs, stop: no script in this plan plays a forbidden Vision, so a difference is a bug.

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/NoteText.scala src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala src/main/scala/oathdigital/gameplay/powers/cardplay/SacredGround.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SacredGroundSuite.scala
git commit -m "feat(powers): the Vision rules say why a faceup placement is missing"
```

---

### Task 2: The Fortress says who cannot be targeted

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/targeting/FortressRulesSuite.scala`

**Interfaces:**
- Consumes: `NoteText.said(id, keys, events)` (Task 1).
- Produces: `FortressRule.shielded: NoteKey` (name `"shielded"`), `FortressRule.allShielded: NoteKey` (name `"all-shielded"`).

- [ ] **Step 1: Write the failing tests**

In `FortressRulesSuite.scala`, add `import oathdigital.gameplay.powers.NoteText` and, at the end of the class:

```scala
  // ---- Lines ----

  private def hidden(power: FortressRule, from: OathTransition)
      : Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, from.events)
  private val oaken = OakenFortress.forCatalog(catalog).get
  private val rotting = RottingFortress.forCatalog(catalog).get
  private def shielded(player: PlayerId) = NoteText.Said("shielded",
    s"${player.value} cannot be targeted.", covers = false)

  test("a Raid the Oaken Fortress hides names the protected ruler"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    assertEquals(hidden(oaken, startOf(b).toOption.get), Vector(shielded(b.other)))

  test("a defender the Oaken Fortress hides is named when the Raid stays"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    val started = start(pawnAt(b.ready, third(b), b.origin), ActionRef.Campaign,
      b.actor).toOption.get
    assertEquals(hidden(oaken, started), Vector.empty)
    val kind = TargetingFixture.rules.resolveWalker(started.state, b.actor,
      CampaignIds.kind, ChooseOneAnswer(raid)).toOption.get
    assertEquals(hidden(oaken, kind), Vector(shielded(b.other)))

  test("a Raid the Rotting Fortress hides from several players names the site"):
    val b = fortified(EdificeSide.Ruined, ruled = true)
    val crowded = pawnAt(b.ready, third(b), b.origin)
    assertEquals(hidden(rotting, start(crowded, ActionRef.Campaign, b.actor)
      .toOption.get), Vector(NoteText.Said("all-shielded",
        s"No player at ${b.origin.value} can be targeted.", covers = false)))

  test("a banner a protected player holds is named by its holder"):
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val held = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(held).player
    val staged = fortressAt(held, EdificeSide.Ruined,
      playerOf(held, actor).pawnSite.get)
    assertEquals(hidden(rotting, start(staged, ActionRef.Challenge, actor)
      .toOption.get), Vector(shielded(enemy)))

  test("a Fortress that protects nobody writes nothing"):
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4))
    val ready = fortressAt(b.ready, EdificeSide.Ruined, b.extras.head)
    assertEquals(hidden(rotting, start(ready, ActionRef.Campaign, b.actor)
      .toOption.get), Vector.empty)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.FortressRulesSuite"`
Expected: the four tests that expect a line FAIL with an empty vector. "a Fortress that protects nobody writes nothing" passes.

- [ ] **Step 3: Write the notes**

In `FortressRules.scala`:

1. Extend the class doc with a paragraph:

   ```scala
    * Each option it hides writes a line (power log lines design, "Removed and
    * hidden options"). A hidden defender or banner names the player it
    * protects. A hidden Raid names the one player it could have targeted, or,
    * when a Rotting Fortress protects several, their site. The start refusal
    * blocks the whole action and writes nothing.
   ```

2. Add to `FortressRule`:

   ```scala
  final override def noteKeys: Vector[NoteKey] =
    Vector(FortressRule.shielded, FortressRule.allShielded)

  private def card: PowerSourceRef = PowerSourceRef.Card(fortress)

  private def shieldedNote(player: PlayerId): Option[PowerNote] =
    Some(FortressRule.shielded(card, NoteArg.Player(player)))

  private def kindNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] =
    CampaignSetup.raidDefenders(ctx.state, ctx.activePlayer) match
      case Vector(defender) => shieldedNote(defender)
      case _ => pawnSite(ctx.state, ctx.activePlayer).map(site =>
        FortressRule.allShielded(card, NoteArg.Site(site)))

  private def defenderNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = ref match
    case DecisionOptionRef.Player(defender) => shieldedNote(defender)
    case _ => None

  private def bannerNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = ref match
    case DecisionOptionRef.Banner(banner) =>
      BannerRules.holder(ctx.state.game.current, banner).flatMap(shieldedNote)
    case _ => None
   ```

3. Pass the notes in `contributions`:

   ```scala
  final def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignKindSelection ->
      Vector(OptionRestriction(kindGuard, kindNote)),
    PowerWindow.CampaignDefenderSelection ->
      Vector(OptionRestriction(defenderGuard, defenderNote)),
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => startGuard(ctx))),
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(bannerGuard, bannerNote)))
   ```

4. Add a companion after the class:

   ```scala
object FortressRule:
  /** "{Blue} cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text(" cannot be targeted.")))
  /** "No player at {site} can be targeted.", for a Raid hidden from several
    * protected players. */
  val allShielded: NoteKey = NoteKey("all-shielded", Vector(
    NotePart.Text("No player at "), NotePart.Arg(0),
    NotePart.Text(" can be targeted.")))
   ```

The hooks ignore their unused `ctx` or `ref` parameter. `-Wunused` does not flag unused method parameters, since they are not locals or privates. If it does, name the parameter `_ctx` or `_ref`; do not remove it, since the hook's type needs both.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS, the golden logs unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala src/test/scala/oathdigital/gameplay/powers/targeting/FortressRulesSuite.scala
git commit -m "feat(powers): the Fortress says who it keeps from being targeted"
```

---

### Checkpoint A

Dispatch one review subagent (Sonnet or lower). Give it the spec, this plan, and `git diff <plan commit>..HEAD`. Ask it to check:

- each note's wording against the spec table and decisions 2, 4 and 5;
- that a note is written only for an option its own power hid, never for a whole-action block;
- that no file under `gameplay/walker` or `application` changed;
- that the tests would fail without the change.

Fix what it confirms before Task 3.

---

### Task 3: The Circlet of Command says whose things cannot be targeted

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala`

**Interfaces:**
- Consumes: `NoteText.said(id, keys, events)` (Task 1).
- Produces: `CircletOfCommand.shielded: NoteKey` (name `"shielded"`).

- [ ] **Step 1: Write the failing tests**

In `CircletOfCommandSuite.scala`:

1. Add `NoteText` to the `oathdigital.gameplay.powers` import.
2. Replace `raidTargets` with a function returning the transition that parks on the targets, and a wrapper for its options:

   ```scala
  /** The Raid board with the Circlet on the defender (or the attacker), the
    * Raid chosen: the transition that parks on the Raid's targets. */
  private def raidKind(circletSide: Option[Orientation],
      attacker: Boolean = false): OathTransition =
    val (b, _) = raidBoard()
    val held = circletSide.fold(b.ready)(side =>
      holds(b.ready, if attacker then b.actor else b.other, circlet, side))
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get

  private def raidTargets(circletSide: Option[Orientation],
      attacker: Boolean = false): Vector[DecisionOptionRef] =
    optionsAt(raidKind(circletSide, attacker), ActionRef.Campaign)
   ```

3. Replace `challengeBanners` the same way:

   ```scala
  private def challenge(circletSide: Option[Orientation])
      : (PlayerId, OathTransition) =
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val withHolder = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(withHolder).player
    val held = circletSide.fold(withHolder)(holds(withHolder, enemy, circlet, _))
    (enemy, start(held, ActionRef.Challenge, actor).toOption.get)

  private def challengeBanners(circletSide: Option[Orientation])
      : Vector[DecisionOptionRef] =
    optionsAt(challenge(circletSide)._2, ActionRef.Challenge)
   ```

4. In `conspiracyTargets`, return the walk's events as a fourth element:

   ```scala
  private def conspiracyTargets(circletSide: Orientation)
      : (ReadyGame, PlayerId, Vector[DecisionOptionRef], Vector[OathEvent]) =
   ```

   Its body ends:

   ```scala
    val (options, events) = parked match
      case WalkerOutcome.Parked(pending, events) =>
        (ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref), events)
      case _ => (Vector.empty, Vector.empty)
    (ready, enemy, options, events)
   ```

   Update the two existing Conspiracy tests to destructure four elements (`val (_, enemy, options, _) = ...`) and `._1` stays `._1`.

5. Add at the end of the class:

   ```scala
  // ---- Lines ----

  private val power = CircletOfCommand.forCatalog(catalog).get
  private def hidden(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)
  private def shielded(holder: PlayerId) = NoteText.Said("shielded",
    s"${holder.value}'s banners and relics cannot be targeted.", covers = false)

  test("the Raid targets the Circlet hides name their holder, once each"):
    val (b, _) = raidBoard()
    val said = hidden(raidKind(Some(Orientation.FaceUp)).events)
    assertEquals(said.size, 3)
    assertEquals(said.distinct, Vector(shielded(b.other)))
    assertEquals(hidden(raidKind(Some(Orientation.FaceDown)).events),
      Vector.empty)

  test("the banner the Circlet hides from a Challenge names its holder"):
    val (enemy, started) = challenge(Some(Orientation.FaceUp))
    assertEquals(hidden(started.events), Vector(shielded(enemy)))

  test("a Conspiracy whose targets the Circlet narrows names the holder"):
    val (_, enemy, _, events) = conspiracyTargets(Orientation.FaceUp)
    assertEquals(hidden(events), Vector(shielded(enemy)))
    assertEquals(hidden(conspiracyTargets(Orientation.FaceDown)._4),
      Vector.empty)
   ```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.CircletOfCommandSuite"`
Expected: the three new tests FAIL with empty vectors where lines were expected. The existing tests pass.

- [ ] **Step 3: Write the notes**

In `CircletOfCommand.scala`:

1. Extend the class doc with:

   ```scala
    * Each option it hides writes "{Blue}'s banners and relics cannot be
    * targeted.", naming the holder (power log lines design, "Removed and
    * hidden options"). At a Conspiracy, whose decision it narrows with a
    * transform, the same line is a note put before the decision whenever it
    * drops an option, and in its place when it drops them all.
   ```

2. Add `override def noteKeys: Vector[NoteKey] = Vector(CircletOfCommand.shielded)`.

3. Pull the holder out of `shields` into its own method, and use it there:

   ```scala
  /** The player holding this Circlet faceup, if any. */
  private def holder(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)
   ```

   In `shields`, replace the local `val holder = ...` with `val owner = holder(ctx.state)` and read `owner.contains(...)` in `held`.

4. Add the note and pass it:

   ```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))),
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  private def note(ctx: PowerCtx): Option[PowerNote] =
    holder(ctx.state).map(owner => CircletOfCommand.shielded(
      PowerSourceRef.Card(cardId), NoteArg.Player(owner)))
   ```

5. Make `dropShielded` leave the note when it drops anything:

   ```scala
  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] =
    val kept = operations.flatMap:
      case decide: Decide => decide.query match
        case one: DecisionQuery.ChooseOne =>
          val options = one.options.filterNot(option => shields(ctx, option.ref))
          if options.isEmpty then Vector.empty
          else Vector(decide.copy(query = one.copy(options = options)))
        case _ => Vector(decide)
      case other => Vector(other)
    if kept == operations then operations
    else note(ctx).map(said => Note(id, _ => Some(said))).toVector ++ kept
   ```

6. Add to the companion:

   ```scala
  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))
   ```

`dropShielded` must stay a pure function of the state it is handed, as `Transform` requires. The note reads only `ctx.state`, so it is.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala
git commit -m "feat(powers): the Circlet of Command says whose things it protects"
```

---

### Task 4: The Narrow Pass says which targets it forbids; record the slice

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala`
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes: `NoteText.said(id, keys, events)` (Task 1).
- Produces: `NarrowPassSitePower.noTarget: NoteKey` (name `"no-target"`).

- [ ] **Step 1: Write the failing tests**

In `CampaignPowersSuite.scala`, add `import oathdigital.gameplay.powers.NoteText`. After the Narrow Pass test "a Pass that is not in play forbids nothing", add:

```scala
  /** The notes the Pass writes when the walk parks on `options`. */
  private def passNotes(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[NoteText.Said] =
    val Right(WalkerOutcome.Parked(_, events)) = ProcedureWalker.advance(ready,
      targets(options), None, WalkerPowers(Vector(passPower))): @unchecked
    NoteText.said(passPower.id, passPower.noteKeys, events)

  test("each site the Pass hides writes the same line, which merges to one"):
    assertEquals(passNotes(withPawnAt(ordered.head)), Vector.fill(2)(
      NoteText.Said("no-target",
        s"${actor.value} cannot target other sites in the region.",
        covers = false)))

  test("a Pass that hides nothing writes nothing"):
    assertEquals(passNotes(withPawnAt(ordered(3))), Vector.empty)
```

The first test expects two notes because two sites are hidden (`ordered(3)` and `ordered(4)`). The formatter posts one line for them (slice 1's merge).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite"`
Expected: "each site the Pass hides …" FAILS with an empty vector. The other passes.

- [ ] **Step 3: Write the note**

In `TravelSitePowers.scala`:

1. Extend the model import with `NoteArg, NoteKey, NotePart, PowerNote, PowerSourceRef`.
2. In `NarrowPassSitePower`, add:

   ```scala
  override def noteKeys: Vector[NoteKey] = Vector(NarrowPassSitePower.noTarget)
   ```

   and pass the note to the Campaign restriction:

   ```scala
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked, campaignNote)))
   ```

   ```scala
  /** A hidden target site's line. A Travel the Pass blocks is refused whole
    * and writes nothing (power log lines design, ruling 3). */
  private def campaignNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = Some(NarrowPassSitePower.noTarget(
    PowerSourceRef.Site(site), NoteArg.Player(ctx.activePlayer)))
   ```

3. Add a companion after the class:

   ```scala
object NarrowPassSitePower:
  /** "{Red} cannot target other sites in the region." */
  val noTarget: NoteKey = NoteKey("no-target", Vector(NotePart.Arg(0),
    NotePart.Text(" cannot target other sites in the region.")))
   ```

   A case class keeps its generated `apply` and `unapply` when it has an explicit companion, so `TravelSitePowers.forCatalog` and the suite's `collectFirst` still compile.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`
Expected: all PASS.

- [ ] **Step 5: Amend the spec**

Add a line under the slice 2 status line:

```markdown
**Slice 3 (2026-09-26):** the removed and hidden options. "Settled in slice
3" records what it decided.
```

In section 2's "Removed and hidden options" table, replace the Fortress, Circlet and Narrow Pass rows with:

```markdown
| Oaken Fortress, Rotting Fortress | hide hook, per protected target | Oaken Fortress: {Blue} cannot be targeted. |
| Rotting Fortress, a Raid hidden from several players, key `all-shielded` | hide hook | Rotting Fortress: No player at {site} can be targeted. |
| Circlet of Command, Raid and Challenge | hide hook | Circlet of Command: {Blue}'s banners and relics cannot be targeted. |
| Circlet of Command, Conspiracy | `Note` from its transform | Circlet of Command: {Blue}'s banners and relics cannot be targeted. |
| Narrow Pass, Campaign targets | hide hook | Narrow Pass: {Red} cannot target other sites in the region. |
```

In "No line", replace the last bullet with:

```markdown
- Every whole-action block (ruling 3): Vow of Peace's first sentence, the
  Fortress's Raid-only block, and a Travel the Narrow Pass blocks. Travel's
  destination is part of the command, so no decision hides it.
```

Add this section after "Settled in slice 2":

```markdown
## Settled in slice 3

The third slice settled these:

- The Narrow Pass writes no Travel line. Travel's destination is part of
  the command, and no decision offers destinations, so a blocked Travel is a
  whole-action block.
- The hide hook is asked once per hidden option. A Raid hidden at the kind
  decision names the one player it could have targeted, or, when a Rotting
  Fortress protects several, their site: "No player at {site} can be
  targeted." An Oaken Fortress protects only its ruler.
- The Circlet narrows a Conspiracy's target with a transform, which puts a
  `Note` before the decision whenever it drops an option, and in the
  decision's place when it drops them all.
- Vow of Obedience, Secret Police and Sacred Ground share one key,
  `no-faceup`, declared in `VisionPlay`. Several Visions hidden in one
  action post one line, through the merge slice 1 built.
- A hidden option is credited to the first restriction whose violation its
  answer adds. When two powers forbid the same option, only that one writes
  a line.
- `NoteText.said` reads any power's notes by id and keys, so the suites of
  contributing powers assert their lines as the phase power suites do.
```

In `docs/ROADMAP.md`, in the paragraph starting "Slice 1 merged on 2026-09-26", replace "Slices 3 to 5 remain: removed and hidden options, added effects and altered procedures, then setup." with:

```markdown
Slice 3 gives every removed and hidden option its line. Slices 4 and 5
remain: added effects and altered procedures, then setup.
```

- [ ] **Step 6: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass. The server count is your baseline plus the new tests (16).

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala docs/superpowers/specs/2026-09-26-power-log-lines-design.md docs/ROADMAP.md
git commit -m "feat(powers): the Narrow Pass says which targets it forbids; record slice 3"
```

---

### Checkpoint B

Dispatch one review subagent (Sonnet or lower). Give it the spec, this plan, and `git diff <plan commit>..HEAD`. Ask it to check:

- every row of the amended "Removed and hidden options" table has a note and a test asserting its exact sentence;
- the Circlet's transform stays a pure function of state, and adds its `Note` only when an option was dropped;
- "Settled in slice 3" matches what the code does;
- the gates' output.

Fix what it confirms, re-run the gates, then finish the branch with superpowers:finishing-a-development-branch.
