# Catalog Batch 5, Slice 4 (Adviser Arrival) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Family Wagon (168). While its holder has it faceup, the holder has no adviser limit, and any adviser arriving in the holder's advisers makes the holder bury every adviser that is not a faceup Nomad. This needs a walker pass that notices arrivals by any route.

**Architecture:**

- **The arrival pass runs where every walker leaf runs its operations.**
  - `WalkerSteps.recordBatch` hands each leaf to a new `AdviserArrivalPass`. The pass compares each player's advisers before and after the leaf (`AdviserArrivals.between`).
  - It asks every power's new `ContributingPower.onArrival` for an `ArrivalFollowUp`: operations and a line.
  - Each follow-up runs as one required batch. It is recorded as its own step at the leaf's node, credited to its power, and its line is journaled right after it.
  - Replay applies recorded operations only, so it needs no change. The pass names no card.
- **Card play learns Family Wagon through `CardPlayRules`,** as slice 3's limits do.
  - `liftWhileFaceUp(card)` removes the base limit while that card is a faceup adviser after the play. Cards' own limits still apply.
  - `noteAdviserPlay(note)` adds an `AdviserPlayNote`. `CardSlots.adviserSlots` wraps an adviser play in `DecisionOption.Warned` when a note applies to the advisers after it.
- **Family Wagon** (`gameplay/powers/cardplay/FamilyWagon.scala`):
  - a Transform at `SearchPlayAdviser` that lifts the limit and adds the note;
  - `onArrival`, which buries;
  - `lifts`, which `AdviserLimit.of` reads.
- **A card buried on arrival was never gained.** `WhenPlayedPower` skips a card that went to the world deck before its hook.
- **The board** draws an unbounded holder's advisers and one empty slot (`AdviserLimit.drawn`).

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The frontend gets no change: it already shows a "warning" board option as choosable.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`: "The bar", "Slice 4: The adviser arrival pass and Family Wagon", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`: "Rules that apply to the whole batch" and "Slice 4: Adviser arrival".

Task 1 amends both with the rulings made at plan time, so read them after Task 1 too. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

**Starts from:** slice 3 on `main` (`688fdc9d`). This plan uses its `AdviserFace`, `CardPlayRules.fits`, `AdviserLimit.ignored` and `PiedPiper`.

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
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Log lines omit the actor.** A power's line names only other players, or a player acting off turn.
- **Texts, exactly:**
  - The card play note: "Family Wagon will bury it" (`FamilyWagon.buryWarning`).
  - Family Wagon's lines: "Buried {cards}." (key `buried`) on the holder's turn, and "{Blue} buried {cards}." (key `buried.holder`) when the holder is not the active player. `{cards}` is one `NoteArg.Cards`.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 20 server tests and no frontend test.
  - It rewrites no existing test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled these on 2026-10-09. Task 1 writes them into the spec.

1. **Where the pass records, and the log order.** The bury is its own recorded step at the leaf's node, credited to Family Wagon, followed by Family Wagon's line. A line after the leaf reads the leaf and its follow-ups together. So when Pied Piper moves into a Wagon holder's advisers, "Family Wagon: p2 buried Pied Piper." comes before Pied Piper's own line.
2. **A buried card was never gained.** Family Wagon says "would gain", so a card it buries on arrival never became an adviser, and its WHEN PLAYED does not run.
3. **The tokens of a buried card.** Favor goes to the card's suit bank. Secrets go to the active player's board, facedown, as `Bury.standard` returns them to its acting player.
4. **The board** draws an unbounded holder's advisers and one empty slot. A lowering power held beside Family Wagon still wins, and then the board draws as usual.
5. **The line:** "Buried {cards}." on the holder's turn, "{Blue} buried {cards}." otherwise. It covers the generic "Buried" lines.
6. **The card play note.** It is a `Warned` option with "Family Wagon will bury it", carried through `CardPlayRules` and `CardSlots`. This is a seventh approved change outside `gameplay/powers/`. A play that replaces Family Wagon carries no note, since Family Wagon is gone after it. Playing Family Wagon itself carries no note.

Also settled at plan time:

7. **Powers that trigger on a play still fire.** Saddle Makers, Wild Cry, Gossip and Welcoming Party react to a card being played, and the card was played. Only the buried card's own WHEN PLAYED is skipped.
8. **A follow-up is required** (`requireAll = true`, one argument). No restriction in this edition refuses a `Bury` of an adviser: Locked does not, and the Grand Scepter's refusal covers only that relic. So this is a guard, not a rule any game reaches. It needs no test.
9. **How WHEN PLAYED learns the card was buried.** `WhenPlayedPower` checks that its card is not in the world deck when the hook is gathered. A check of the card's place would break a resumed walk after Twin Brother or Great Herd moves its own card. No WHEN PLAYED power puts its own card into the world deck.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`

- [ ] **Step 1: Amend "The bar"**

In the design, replace `The product owner approved six` with `The product owner approved seven`. Replace the bullet

```markdown
- **The board's adviser slots** in `GamePresentationProjector` add one slot
  per adviser that does not count (slice 3, approved at plan time).
- **An adviser arrival pass** in the walker (slice 4).
```

with

```markdown
- **The board's adviser slots** in `GamePresentationProjector` add one slot
  per adviser that does not count (slice 3, approved at plan time), and draw
  the advisers and one empty slot for a player with no limit (slice 4).
- **An adviser arrival pass** in the walker, with `ContributingPower.onArrival`
  and `AdviserArrivals` in `gameplay/powerresolver` (slice 4).
- **Card play's lifted limit and adviser play notes** in `CardPlayRules` and
  `CardSlots.adviserSlots` (slice 4, approved at plan time).
```

- [ ] **Step 2: Replace the slice 4 section of the design**

Replace everything from `## Slice 4: The adviser arrival pass and Family Wagon` up to, not including, `## Seams used for the first time` with:

```markdown
## Slice 4: The adviser arrival pass and Family Wagon

### The arrival pass

No hook runs when an adviser arrives by a route other than card play:
Horned Mask, `Swap` (Enchantress, Twin Brother, Dream Thief) and Pied
Piper's move add advisers silently. The pass sits where every walker leaf
runs its operations (`WalkerSteps.recordBatch`), so it sees every route,
card play included. After a leaf's operations apply, it compares each
player's advisers before and after (`AdviserArrivals.between`). An adviser
that is new, or that turned faceup, is an arrival.

Powers register arrival rules through `ContributingPower.onArrival`, which
returns an `ArrivalFollowUp`: operations and a line. The pass asks every
power, in the order contributions chain. Each follow-up runs as one
required batch, recorded as its own step at the leaf's node and credited to
its power, and its line is journaled right after it. Replay applies the
recorded operations, and the Game Log reads the step and the line. The pass
names no card. It runs once per leaf, and the follow-up operations it adds
do not start another pass.

A line after the leaf reads the leaf and its follow-ups together. So when
Pied Piper moves into a Family Wagon holder's advisers, Family Wagon's line
comes before Pied Piper's.

### Family Wagon

While a player holds Family Wagon faceup, any arrival in that player's
advisers buries every adviser that is not a faceup Nomad. Family Wagon's
own arrival counts. The buried cards include:
- facedown advisers, Visions too, since a suitless card is not a Nomad
  adviser
- locked advisers such as Marriage, because Locked guards against a
  discard and a bury is not a discard

Advisers are buried in held order with `Bury.standard`: favor goes to the
card's suit bank, and secrets to the active player, facedown. The line
names the buried cards each viewer may identify: "Buried {cards}." on the
holder's turn, "{Blue} buried {cards}." otherwise. It covers the generic
"Buried" lines.

A card Family Wagon buries on arrival was never gained, so its WHEN PLAYED
does not run: `WhenPlayedPower` skips a card that went to the world deck
before its hook. Powers that trigger on a play (Saddle Makers, Wild Cry,
Gossip, Welcoming Party) still fire, because the card was played.

Family Wagon makes its holder's limit unbounded. `AdviserLimit.of` gives
its holder no limit before the lowering powers apply. In card play, Family
Wagon's Transform at `SearchPlayAdviser` lifts the base limit while Family
Wagon is a faceup adviser after the play (`CardPlayRules.liftWhileFaceUp`),
and cards' own limits still apply. The board draws the holder's advisers and
one empty slot.

A card play that would add a non-Nomad or facedown adviser stays on offer.
Card play shows it `Warned` with the reason "Family Wagon will bury it",
from the note Family Wagon adds to `CardPlayRules`. A play that replaces
Family Wagon, and the play of Family Wagon itself, carry no note.
```

- [ ] **Step 3: Add the slice 4 rulings**

In the rulings, under `## Slice 4: Adviser arrival`, after the bullet that ends `with a note that the card will be buried.`, add these sub-bullets at the same indent as `- Advisers are buried in held order.`:

```markdown
  - A card it buries on arrival was never gained, since it says "would
    gain": its WHEN PLAYED does not run. Powers that trigger on a play still
    fire, because the card was played.
  - The buried cards' favor goes to their suit banks, and their secrets to
    the active player's board, facedown.
  - Its line reads "Buried {cards}." on the holder's turn, and "{Blue}
    buried {cards}." when the holder is not the active player.
  - The note on a card play it will bury reads "Family Wagon will bury it".
    Playing Family Wagon itself carries no note.
  - The board draws the holder's advisers and one empty slot.
```

- [ ] **Step 4: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md
git commit -m "docs(spec): settle the adviser arrival pass and Family Wagon"
```

---

### Task 2: The adviser arrival pass

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/AdviserArrivals.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (trait `ContributingPower`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala` (`recordBatch`)
- Test: `src/test/scala/oathdigital/gameplay/walker/AdviserArrivalPassSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.gameplay.powerresolver`:
  - `final case class AdviserArrival(player: PlayerId, card: CardId)`
  - `final case class AdviserArrivals(actor: PlayerId, before: ReadyGame, after: ReadyGame, arrivals: Vector[AdviserArrival])`, with `AdviserArrivals.between(actor, before, after): AdviserArrivals`
  - `final case class ArrivalFollowUp(operations: Vector[CoreOperation], note: Note)`
  - `ContributingPower.onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp]`, `None` by default

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/walker/AdviserArrivalPassSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{AdviserArrival, AdviserArrivals,
  ArrivalFollowUp, ContributingPower, Contribution}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The adviser arrival pass (catalog batch 5, "The arrival pass"), driven by
  * stub rules over one leaf. */
class AdviserArrivalPassSuite extends munit.FunSuite:
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  /** "{cards}": the arrivals a stub heard. */
  private val heard = NoteKey("heard", Vector(NotePart.Arg(0)))

  /** Stub of ContributingPower: hooks no window. Whenever advisers arrive,
    * it follows up with `follow(arrived)` and notes the arrivals it heard.
    */
  private final case class HearsArrivals(name: String,
      follow: AdviserArrivals => Vector[CoreOperation])
      extends ContributingPower:
    def id: PowerId = PowerId(name)
    def source: RuleSourceRef = RuleSourceRef.GameRule(name)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def onArrival(arrived: AdviserArrivals)
        : Option[ArrivalFollowUp] =
      val cards = arrived.arrivals.map(_.card)
      Some(ArrivalFollowUp(follow(arrived), Note(id, _ => Some(heard(
        PowerSourceRef.Card(wrestlers), NoteArg.Cards(cards))))))

  /** Hears arrivals and does nothing more. */
  private val hears = HearsArrivals("test.hears", _ => Vector.empty)

  /** Buries each denizen that arrived in p1's advisers. */
  private val buriesP1Arrivals = HearsArrivals("test.bury-p1", arrived =>
    arrived.arrivals.collect {
      case AdviserArrival(`p1`, card: DenizenId) => Bury(
        BuryableCard.Denizen(card), PositionedLocation(Location.PlayArea(p1)))
    })

  /** Each time it is asked, gives p1 the first adviser p2 still holds. */
  private val passesP2AdviserOn = HearsArrivals("test.pass-on", arrived =>
    arrived.after.game.current.players.find(_.player == p2).toVector
      .flatMap(_.advisers.headOption).map(next => Give(Piece.Card(next.id),
        p2, Location.PlayArea(p2), Location.PlayArea(p1), required = true)))

  private def givesToP1(card: DenizenId): CoreOperation = Give(
    Piece.Card(card), p2, Location.PlayArea(p2), Location.PlayArea(p1),
    required = true)

  /** `ops` walked as the one leaf of a sequence, under `power`. */
  private def walked(ready: ReadyGame, power: ContributingPower,
      ops: Vector[CoreOperation]): WalkerOutcome.Finished =
    Outcomes.finished(ProcedureWalker.advance(ready,
      Sequence(Vector(BuildOps((_, _) => Right(ops), required = true))),
      None, WalkerPowers(Vector(power))))

  /** The arguments of every line `power` journaled in `events`. */
  private def heardBy(power: ContributingPower, events: Vector[OathEvent])
      : Vector[NoteArg] = events.collect {
    case PowerNoted(id, note, _) if id == power.id => note.args }.flatten

  test("an adviser new to a player is an arrival; the rule's follow-up runs " +
      "as its own step at the leaf's node, credited to it, with its line " +
      "after it, and replays"):
    val ready = Table.start.adviser(p2, wrestlers).ready
    val done = walked(ready, buriesP1Arrivals, Vector(givesToP1(wrestlers)))
    assertEquals(Look(done.treeless).advisers(p1), Vector.empty)
    assertEquals(done.treeless.game.current.commonCards.worldDeck.lastOption,
      Some(wrestlers))
    val steps = done.events.collect { case step: WalkerStepRecorded => step }
    assertEquals(steps.map(_.nodeId), Vector("0", "0"))
    assertEquals(steps.map(_.contributions),
      Vector(Vector.empty, Vector(buriesP1Arrivals.id)))
    assert(done.events.lastOption.exists(_.isInstanceOf[PowerNoted]),
      done.events.toString)
    assertEquals(heardBy(buriesP1Arrivals, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))
    val replayed = done.events.collect { case event: WalkerEvent => event }
      .foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready)))((state, event) =>
        state.flatMap(ProcedureWalker.applyRecorded(_, event)))
    assertEquals(replayed.map {
      case OathState.Ready(game) => game.game.current.players
      case other => fail(s"expected a ready game, got $other")
    }, Right(done.treeless.game.current.players))

  test("an adviser turned faceup is an arrival; one that stays faceup is " +
      "not"):
    val ready = Table.start.adviser(p1, wrestlers, facedown = true)
      .adviser(p1, elders).ready
    // Card play turns a facedown adviser faceup with this `Play`.
    val turned = Play(wrestlers, PositionedLocation(Location.PlayArea(p1)),
      Location.PlayArea(p1), Orientation.FaceUp, required = true)
    val done = walked(ready, hears, Vector(turned))
    assertEquals(heardBy(hears, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))

  test("a leaf in which no adviser arrives asks no rule"):
    val ready = Table.start.adviser(p1, wrestlers).supply(p1, 2).ready
    val done = walked(ready, hears, Vector(GainSupply(p1, 1)))
    assertEquals(heardBy(hears, done.events), Vector.empty)
    assertEquals(done.events.count(_.isInstanceOf[WalkerStepRecorded]), 1)

  test("a follow-up's own arrivals start no second pass"):
    val ready = Table.start.adviser(p2, wrestlers).adviser(p2, elders)
      .adviser(p2, rainBoots).ready
    val done = walked(ready, passesP2AdviserOn, Vector(givesToP1(wrestlers)))
    // The leaf gave Wrestlers; the one follow-up gave Elders. A second pass
    // would have given Rain Boots too.
    assertEquals(Look(done.treeless).advisers(p1),
      Vector[CardId](wrestlers, elders))
    assertEquals(Look(done.treeless).advisers(p2), Vector[CardId](rainBoots))
    assertEquals(heardBy(passesP2AdviserOn, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.AdviserArrivalPassSuite"`
Expected: FAIL to compile, because `AdviserArrival`, `AdviserArrivals`, `ArrivalFollowUp` and `onArrival` do not exist.

- [ ] **Step 3: Create the arrival types**

Create `src/main/scala/oathdigital/gameplay/powerresolver/AdviserArrivals.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{AdviserState, CardId, CoreOperation, DenizenState,
  Note, Orientation, PlayerId, ReadyGame, VisionState}

/** An adviser that arrived during one walker leaf: `card` is new among
  * `player`'s advisers, or turned faceup there. */
final case class AdviserArrival(player: PlayerId, card: CardId)

/** What an arrival rule reads (catalog batch 5, "The arrival pass"): the
  * active player, the states before and after the leaf, and the advisers
  * that arrived in it, in seat and then held order. `after` already holds
  * the follow-ups of the rules asked before. */
final case class AdviserArrivals(actor: PlayerId, before: ReadyGame,
    after: ReadyGame, arrivals: Vector[AdviserArrival])

object AdviserArrivals:
  /** The advisers that arrived between `before` and `after`. */
  def between(actor: PlayerId, before: ReadyGame, after: ReadyGame)
      : AdviserArrivals =
    val held = before.game.current.players
      .map(player => player.player -> player.advisers).toMap
    AdviserArrivals(actor, before, after, for
      player <- after.game.current.players
      adviser <- player.advisers
      earlier = held.getOrElse(player.player, Vector.empty)
        .find(_.id == adviser.id)
      if earlier.forall(was => !faceUp(was) && faceUp(adviser))
    yield AdviserArrival(player.player, adviser.id))

  private def faceUp(adviser: AdviserState): Boolean = adviser match
    case DenizenState(_, orientation, _) => orientation == Orientation.FaceUp
    case VisionState(_, orientation) => orientation == Orientation.FaceUp

/** An arrival rule's answer: the operations to run after the leaf, as one
  * required batch, and the line journaled after them. */
final case class ArrivalFollowUp(operations: Vector[CoreOperation],
    note: Note)
```

- [ ] **Step 4: Add `onArrival` to `ContributingPower`**

In `ContributingPower.scala`, in the trait `ContributingPower`, after `warning`, add:

```scala
  /** The follow-up this power adds after a walker leaf in which advisers
    * arrived (catalog batch 5, "The arrival pass"). None by default. */
  def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] = None
```

Both types are in the same package, so no import changes.

- [ ] **Step 5: Create the pass**

Create `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower}
import oathdigital.model.{NoteStates, OathViolation, PowerId, ReadyGame}

/** The adviser arrival pass (catalog batch 5, "The arrival pass"). After a
  * leaf's operations apply, the advisers that arrived in it, new to a player
  * or turned faceup there, go to every power's `onArrival`, in the order
  * contributions chain. Each follow-up runs as one required batch, recorded
  * as its own step at the leaf's node and credited to its power, and its
  * line is journaled right after it. A follow-up's own arrivals start no
  * second pass. The leaf's `previous` then spans the leaf and its
  * follow-ups, so a note after the leaf reads the whole effect. The pass
  * names no card.
  */
private[walker] object AdviserArrivalPass:
  /** `ran`, the walk after a leaf that started at `before`, with the
    * follow-ups of the leaf's arrivals run. */
  def after(before: ReadyGame, ran: WalkCtx, nodeId: String)
      : Either[OathViolation, WalkCtx] =
    val arrived = AdviserArrivals.between(ran.activePlayer, before, ran.state)
    if arrived.arrivals.isEmpty then Right(ran)
    else ran.powers.powers.sortBy(ContributingPower.sortKey)
      .foldLeft[Either[OathViolation, WalkCtx]](Right(ran)) {
        (walked, power) => walked.flatMap(ctx =>
          power.onArrival(arrived.copy(after = ctx.state))
            .fold[Either[OathViolation, WalkCtx]](Right(ctx))(
              followed(ctx, power.id, _, nodeId)))
      }.map(ctx => ctx.copy(previous = Some((before, ctx.state))))

  private def followed(ctx: WalkCtx, power: PowerId,
      followUp: ArrivalFollowUp, nodeId: String)
      : Either[OathViolation, WalkCtx] =
    OperationPipeline.run(ctx.state, followUp.operations,
      OperationPolicy.Permissive, ctx.powers.operationRestrictions,
      requireAll = true)(Right(_)).map { updated =>
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = WalkerStepPayload.DeltaRecorded(
          DeltaMeaning.of(updated.executed, label)),
        ops = updated.executed,
        contributions = Vector(power)))
      val note = followUp.note.build(NoteStates(updated.state,
        Some((ctx.state, updated.state)), ctx.answered)).map(built =>
        PowerNoted(followUp.note.power, built, followUp.note.covers))
      ctx.copy(state = updated.state, events = ctx.events ++ step ++ note)
    }

  /** The fallback name of a follow-up's step. */
  private val label = "AdviserArrival"
```

- [ ] **Step 6: Run the pass after every leaf**

In `WalkerSteps.scala`, in `recordBatch`, change `Right(_)).map { updated =>` to `Right(_)).flatMap { updated =>`, and replace the last expression of that block,

```scala
      ctx.copy(state = updated.state, events = events,
        previous = Some((ctx.state, updated.state)))
```

with

```scala
      AdviserArrivalPass.after(ctx.state, ctx.copy(state = updated.state,
        events = events, previous = Some((ctx.state, updated.state))),
        nodeId)
```

Add one sentence to `recordBatch`'s doc comment: `The adviser arrival pass then runs the follow-ups of any adviser that arrived in the batch (`AdviserArrivalPass`).`

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.*"`
Expected: PASS, the existing walker suites unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/AdviserArrivals.scala src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala src/test/scala/oathdigital/gameplay/walker/AdviserArrivalPassSuite.scala
git commit -m "feat(walker): run powers' follow-ups when an adviser arrives"
```

---

### Task 3: Card play's lifted limit and adviser play notes

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala` (`adviserSlots`, the object's doc comment)
- Test: `src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.gameplay.actions`:
  - `final case class AdviserPlayNote(reason: OptionReason, applies: (Vector[AdviserFace], AdviserFace) => Boolean)`
  - `CardPlayRules.liftWhileFaceUp(card: CardId): CardPlayRules`
  - `CardPlayRules.noteAdviserPlay(note: AdviserPlayNote): CardPlayRules`
  - `CardPlayRules.noteOn(advisers: Vector[AdviserFace], played: AdviserFace): Option[OptionReason]`
  - `CardPlayRules.Unbounded: Int` (`Int.MaxValue`)

- [ ] **Step 1: Write the failing rules tests**

In `CardPlayRulesSuite.scala`:
- Change `import oathdigital.gameplay.actions.{AdviserFace, CardPlayRules}` to `import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote, CardPlayRules}`.
- Add the field `private val wagon = CatalogNames.denizen("Family Wagon")` after `wrestlers`.
- After the test `an adviser face that ignores the limit is not counted, in that face only`, add:

```scala
  test("a card that lifts the limit removes it while it is a faceup " +
      "adviser after the play; cards' own limits still apply"):
    val rules = CardPlayRules.default.liftWhileFaceUp(wagon)
    val five = Vector(faceup(wagon), faceup(card), facedown(tongue),
      faceup(wrestlers), facedown(piper))
    assertEquals(rules.limitOver(five), CardPlayRules.Unbounded)
    assert(rules.fits(five))
    assertEquals(rules.limitOver(five.updated(0, facedown(wagon))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitWhileFaceUp(tongue, 2)
      .limitOver(five.updated(2, faceup(tongue))), 2)

  test("an adviser play's note is the first that applies after the play"):
    val first = OptionReason.Written("First")
    val second = OptionReason.Written("Second")
    val rules = CardPlayRules.default
      .noteAdviserPlay(AdviserPlayNote(first, (after, _) => after.size > 1))
      .noteAdviserPlay(AdviserPlayNote(second, (_, _) => true))
    assertEquals(rules.noteOn(Vector(faceup(tongue), faceup(card)),
      faceup(card)), Some(first))
    assertEquals(rules.noteOn(Vector(faceup(card)), faceup(card)),
      Some(second))
    assertEquals(CardPlayRules.default.noteOn(Vector(faceup(card)),
      faceup(card)), None)
```

- [ ] **Step 2: Write the failing slot tests**

In `CardSlotsSuite.scala`:
- Change the first import to `import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote, CardPlay, CardPlayRules, RuleNotes, VisionRules}`, wrapped to 80 columns.
- After the test `a facedown adviser turned faceup must fit its own limit`, add:

```scala
  test("a card that lifts the limit, faceup after the play, gives a new " +
      "slot past three; played facedown it lifts nothing"):
    val rules = CardPlayRules.default.liftWhileFaceUp(card)
    val three = holding(rainBoots, binding, wrestlers).ready
    assertEquals(advisers(slotsOf(three, rules = rules), faceup = true),
      Vector(adviserPlay(3, faceup = true)))
    assertEquals(advisers(slotsOf(three, rules = rules), faceup = false),
      Vector(replacing(adviserPlay(0, faceup = false), rainBoots),
        replacing(adviserPlay(1, faceup = false), binding),
        replacing(adviserPlay(2, faceup = false), wrestlers)))

  test("an adviser play after which a note applies carries it and stays " +
      "on offer"):
    val note = OptionReason.Written("Test note")
    val rules = CardPlayRules.default.noteAdviserPlay(AdviserPlayNote(note,
      (_, played) => played.orientation == Orientation.FaceDown))
    val slots = slotsOf(atAncientCity.ready, rules = rules)
    assertEquals(advisers(slots, faceup = true),
      Vector(adviserPlay(0, faceup = true)))
    assertEquals(advisers(slots, faceup = false),
      Vector(DecisionOption.Warned(adviserPlay(0, faceup = false), note)))
```

`AdviserFace` stays imported, since the suite already uses it.

- [ ] **Step 3: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.gameplay.CardSlotsSuite"`
Expected: FAIL to compile, because `AdviserPlayNote`, `liftWhileFaceUp`, `noteAdviserPlay`, `noteOn` and `Unbounded` do not exist.

- [ ] **Step 4: Extend `CardPlayRules`**

In `CardPlayRules.scala`:

1. After the object `AdviserFace`, add:

```scala
/** A note card play shows on an adviser play: `reason`, when `applies`
  * holds of the advisers after the play and the played face (Family Wagon's
  * "Family Wagon will bury it"). The play stays on offer. */
final case class AdviserPlayNote(reason: OptionReason,
    applies: (Vector[AdviserFace], AdviserFace) => Boolean)
```

2. In the class doc comment, replace the `adviserLimit` bullet with:

```scala
  *  - `adviserLimit` is the base limit on all of the player's advisers,
  *    whatever they hold.
  *  - `lifts` are the cards that remove the base limit while they are a
  *    faceup adviser (Family Wagon). Cards' own limits still apply.
```

   After the `siteDiscards` bullet, add:

```scala
  *  - `adviserNotes` are the notes an adviser play may carry. The first
  *    that applies to the advisers after the play wins.
```

   Replace the closing paragraph `Every change lowers a limit, exempts a face or adds a permission, so contributions compose in any order.` with:

```scala
  * Every change lowers or lifts a limit, exempts a face, or adds a
  * permission or a note, so contributions compose in any order. Only the
  * notes keep their order, and no two powers note the same play.
```

3. Add the fields `lifts: Set[CardId] = Set.empty` and `adviserNotes: Vector[AdviserPlayNote] = Vector.empty` after `siteDiscards`, in that order.

4. Replace `limitOver` and its doc with:

```scala
  /** The limit over `advisers`, the advisers after a play: `adviserLimit`,
    * or none while a card that lifts it is faceup, lowered by each faceup
    * card that sets a limit. */
  def limitOver(advisers: Vector[AdviserFace]): Int =
    val faceUp = advisers.collect {
      case AdviserFace(card, Orientation.FaceUp) => card }
    val base = if faceUp.exists(lifts) then CardPlayRules.Unbounded
      else adviserLimit
    faceUp.flatMap(cardLimits.get).foldLeft(base)(math.min)
```

5. Change `limitAdvisers`'s doc to `/** Lowers the base limit to at most `limit`. */`.

6. After `ignoreLimit`, add:

```scala
  /** `card` removes the base limit while it is a faceup adviser. */
  def liftWhileFaceUp(card: CardId): CardPlayRules =
    copy(lifts = lifts + card)

  /** Adds a note an adviser play may carry. */
  def noteAdviserPlay(note: AdviserPlayNote): CardPlayRules =
    copy(adviserNotes = adviserNotes :+ note)

  /** The reason of the first note that applies to the play of `played`
    * that leaves `advisers`. */
  def noteOn(advisers: Vector[AdviserFace], played: AdviserFace)
      : Option[OptionReason] =
    adviserNotes.find(_.applies(advisers, played)).map(_.reason)
```

7. In `object CardPlayRules`, after `DefaultAdviserLimit`, add:

```scala
  /** No limit: the base limit while a card that lifts it is faceup. */
  val Unbounded: Int = Int.MaxValue
```

- [ ] **Step 5: Note the adviser plays in `CardSlots`**

In `CardSlots.scala`:
- In the object's doc comment, after the sentence that ends `and nothing when no replacement fits.`, add `An adviser play after which a note of `CardPlayRules` applies is shown `Warned` with its reason, and stays on offer.` Rewrap the paragraph to 80 columns.
- Replace the body of `adviserSlots` from `origin match` to its end with:

```scala
    def noted(option: DecisionOption, after: Vector[AdviserFace])
        : DecisionOption =
      rules.noteOn(after, played).fold(option)(
        DecisionOption.Warned(option, _))
    origin match
      case CardPlay.Origin.FacedownAdviser =>
        // The card turns faceup where it lies.
        val own = held.indexWhere(_.id == card)
        if own < 0 then Vector.empty
        else
          val after = faces.updated(own, played)
          if !rules.fits(after) then Vector.empty
          else Vector(noted(slot(own), after) ->
            SearchPlacement.Adviser(orientation, None))
      case CardPlay.Origin.TemporaryHand =>
        val added = faces :+ played
        if rules.fits(added) then Vector(noted(slot(held.size), added) ->
          SearchPlacement.Adviser(orientation, None))
        else replacements(faces, played, rules).map((index, fits) =>
          val option = DecisionOption.Replacing(slot(index), held(index).id)
          (if fits then noted(option, faces.updated(index, played))
          else DecisionOption.Unavailable(option,
            OptionReason.Written(CardPlayRules.notCounted))) ->
            SearchPlacement.Adviser(orientation, Some(held(index).id)))
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.powers.action.PiedPiperSuite"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala
git commit -m "feat(cardplay): let a power lift the adviser limit and note a play"
```

---

### Task 4: Family Wagon

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (delete `FamilyWagonCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.cardplay` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala` (`of`)
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala` (`persistentRules`)
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala`

**Interfaces:**
- Consumes: Task 2's `AdviserArrivals`, `ArrivalFollowUp`, `onArrival`; Task 3's `AdviserPlayNote`, `liftWhileFaceUp`, `noteAdviserPlay`, `Unbounded`.
- Produces, in `oathdigital.gameplay.powers.cardplay`:
  - `FamilyWagonCard`
  - `final case class FamilyWagon` with `FamilyWagon.forCatalog(catalog)`, `FamilyWagon.id`, `FamilyWagon.cardId`, `FamilyWagon.buryWarning`, the keys `FamilyWagon.buried` and `FamilyWagon.buriedByHolder`, and `FamilyWagon.lifts(ready, player): Boolean`

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, FaithfulFriend}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Family Wagon (card 168) through card play: under a faceup Family Wagon
  * the arrival pass buries every adviser that is not a faceup Nomad, and
  * card play gives the holder no limit and notes the plays it will bury.
  * The other arrival routes are in `FamilyWagonArrivalsSuite`. */
class FamilyWagonSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park, placementOptions}

  private val actor = Table.p1
  private val wagon = CatalogNames.denizen("Family Wagon")
  private val power = FamilyWagon.forCatalog(catalog)
  // Nomads.
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val lancers = CatalogNames.denizen("Lancers")
  /** Nomad, locked, adviser-only. WHEN PLAYED: gain 4 Supply. */
  private val friend = CatalogNames.denizen("Faithful Friend")
  /** Discord, locked, adviser-only. WHEN PLAYED: gain four warbands. */
  private val smallFavor = CatalogNames.denizen("A Small Favor")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val faith = VisionRules.Faith
  private val powers =
    WalkerPowers(Vector(power, ASmallFavor, FaithfulFriend))
  private val buryNote = OptionReason.Written(FamilyWagon.buryWarning)

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

  private def wagonLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events)

  /** Family Wagon's line on its holder's turn, naming `cards`. */
  private def buriedLine(cards: CardId*): Vector[NoteText.Said] =
    Vector(NoteText.Said("buried",
      s"Buried ${cards.map(_.value).mkString(", ")}.", covers = true))

  private def worldDeck(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck

  test("a non-Nomad played faceup is buried at once, and its WHEN PLAYED " +
      "does not run"):
    val ready = actWith(smallFavor)(_.adviser(actor, wagon)
      .adviser(actor, elders))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, elders))
    assertEquals(worldDeck(done.treeless).lastOption, Some(smallFavor))
    // It was never gained, so A Small Favor's four warbands never came.
    assertEquals(Look(done.treeless).boardWarbands(actor), 3)
    assertEquals(wagonLines(done.events), buriedLine(smallFavor))
    assertWireRoundTrips(done.events)
    // Without Family Wagon, the same play gains them.
    val unheld = actWith(smallFavor)(_.adviser(actor, elders))
    assertEquals(Look(playedToAdvisers(unheld, smallFavor, 1,
      faceup = true).treeless).boardWarbands(actor), 3 + 4)

  test("a Nomad played faceup is kept, and its WHEN PLAYED runs"):
    val ready = actWith(friend)(_.adviser(actor, wagon))
    val done = playedToAdvisers(ready, friend, 1, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, friend))
    assertEquals(Look(done.treeless).supply(actor), 2 + 4)
    assertEquals(wagonLines(done.events), Vector.empty)

  test("a Nomad played facedown is buried, since a facedown card is not a " +
      "Nomad adviser"):
    val ready = actWith(rainBoots)(_.adviser(actor, wagon))
    val done = playedToAdvisers(ready, rainBoots, 1, faceup = false)
    assertEquals(Look(done.treeless).advisers(actor), Vector[CardId](wagon))
    assertEquals(worldDeck(done.treeless).lastOption, Some(rainBoots))
    assertEquals(wagonLines(done.events), buriedLine(rainBoots))

  test("Family Wagon's own arrival buries every adviser that is not a " +
      "faceup Nomad, in held order, a locked one and a Vision too"):
    val ready = actWith(wagon)(_.adviser(actor, wrestlers)
      .adviser(actor, elders).adviser(actor, smallFavor)
      .adviser(actor, faith, facedown = true)
      .adviser(actor, rainBoots, facedown = true))
    // Family Wagon faceup after the play lifts the limit: a new sixth slot.
    val done = playedToAdvisers(ready, wagon, 5, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](elders, wagon))
    assertEquals(worldDeck(done.treeless).takeRight(4),
      Vector[WorldCardId](wrestlers, smallFavor, faith, rainBoots))
    assertEquals(wagonLines(done.events),
      buriedLine(wrestlers, smallFavor, faith, rainBoots))

  test("a facedown Family Wagon played faceup buries the advisers beside " +
      "it"):
    val ready = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, wagon, facedown = true).adviser(actor, wrestlers)
      .adviser(actor, elders).ready
    val tree = Unwrap.accepted(CardPlayProcedure.buildFacedown(catalog, ready,
      actor, Vector.empty))
    val picked = Unwrap.as[WalkerOutcome.Parked](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.facedownAdviserDecisionId,
      DecisionOptionRef.Denizen(wagon), actor))
    val done = Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      picked.tree, powers, CardPlayProcedure.placementDecisionId(wagon),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = true), actor))
    assertEquals(Look(done.treeless).advisers(actor).toSet,
      Set[CardId](wagon, elders))
    assertEquals(worldDeck(done.treeless).lastOption, Some(wrestlers))
    assertEquals(wagonLines(done.events), buriedLine(wrestlers))

  test("the holder has no limit, and a play Family Wagon will bury carries " +
      "a note: a non-Nomad either way, a Nomad facedown"):
    def adviserOptions(card: DenizenId): Vector[DecisionOption] =
      val ready = actWith(card)(_.adviser(actor, wagon)
        .adviser(actor, elders).adviser(actor, rainBoots)
        .adviser(actor, lancers))
      val tree = build(ready, actor, card)
      placementOptions(ready, tree, park(ready, tree, powers), powers)
        .filter(_.ref.isInstanceOf[DecisionOptionRef.AdviserPlay])
    def fifthSlot(faceup: Boolean): DecisionOption =
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(actor, 4,
        faceup))
    assertEquals(adviserOptions(smallFavor), Vector[DecisionOption](
      DecisionOption.Warned(fifthSlot(faceup = true), buryNote),
      DecisionOption.Warned(fifthSlot(faceup = false), buryNote)))
    assertEquals(adviserOptions(friend), Vector[DecisionOption](
      fifthSlot(faceup = true),
      DecisionOption.Warned(fifthSlot(faceup = false), buryNote)))

  test("a facedown Family Wagon buries nothing"):
    val ready = actWith(smallFavor)(_.adviser(actor, wagon, facedown = true)
      .adviser(actor, elders))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, elders, smallFavor))
    assertEquals(wagonLines(done.events), Vector.empty)
```

In `AdviserLimitSuite.scala`, change `import oathdigital.gameplay.actions.{AdviserFace, VisionRules}` to `import oathdigital.gameplay.actions.{AdviserFace, CardPlayRules, VisionRules}` and add at the end:

```scala
  test("a faceup Family Wagon gives its holder no limit; a lowering power " +
      "still applies, and facedown it lifts nothing"):
    val wagon = CatalogNames.denizen("Family Wagon")
    assertEquals(AdviserLimit.of(catalog,
      Table.start.adviser(p1, wagon).ready, p1), CardPlayRules.Unbounded)
    assertEquals(AdviserLimit.of(catalog,
      Table.start.adviser(p1, wagon).adviser(p1, tongue).ready, p1), 2)
    assertEquals(AdviserLimit.of(catalog,
      Table.start.adviser(p1, wagon, facedown = true).ready, p1), 3)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.FamilyWagonSuite oathdigital.gameplay.powers.AdviserLimitSuite"`
Expected: FAIL to compile, because `FamilyWagon` does not exist.

- [ ] **Step 3: Create the card and its power**

Delete `object FamilyWagonCard ...` (its six lines and the blank line after) from `HoldingNomadDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower, Contribution, Transform}
import oathdigital.model._

object FamilyWagonCard extends Denizen(DenizenId("168"), "Family Wagon", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.family-wagon"),
    persistent = true, cost = Cost.free,
    text = "You have no adviser limit but may only have [suit-nomad] " +
      "advisers-—bury other advisers you have or would gain.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Family Wagon (card 168, adviser-only): "You have no adviser limit but
  * may only have [Nomad] advisers--bury other advisers you have or would
  * gain."
  *
  * While its holder has it faceup:
  *  - card play gives the holder no limit. A transform at
  *    `SearchPlayAdviser` lifts the base limit while Family Wagon is a
  *    faceup adviser after the play (`CardPlayRules.liftWhileFaceUp`), and
  *    notes each adviser play after which it would bury the played card.
  *  - `AdviserLimit.of` gives the holder no limit, before the lowering
  *    powers apply (`lifts`).
  *  - an adviser arriving in the holder's advisers, by any route, makes the
  *    holder bury every adviser that is not a faceup Nomad, in held order
  *    (`onArrival`). Family Wagon's own arrival counts. A facedown card has
  *    no suit, so it is buried, and Locked does not stop a bury.
  *
  * A card it buries on arrival was never gained, so its WHEN PLAYED does
  * not run (`WhenPlayedPower`). The bury returns the card's favor to its
  * suit bank and its secrets to the active player, facedown. The line names
  * the holder only when the holder is not the active player, and covers the
  * generic "Buried" lines.
  */
final case class FamilyWagon private (catalog: ExecutableCatalog)
    extends ContributingPower:
  import FamilyWagon._
  def id: PowerId = FamilyWagon.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(buried, buriedByHolder)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(_.liftWhileFaceUp(cardId).noteAdviserPlay(
            AdviserPlayNote(OptionReason.Written(buryWarning),
              (after, played) => after.contains(faceUp) && !kept(played))))
        case _ => children
      })))

  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = advisers(arrived.after, holder)
        .filterNot(adviser => kept(AdviserFace.of(adviser)))
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(_, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** Whether `face` stays under Family Wagon: a faceup Nomad. */
  private def kept(face: AdviserFace): Boolean =
    face.orientation == Orientation.FaceUp &&
      catalog.suitOf(face.card).contains(Suit.Nomad)

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
    val wagon = PowerSourceRef.Card(cardId)
    if holder == actor then buried(wagon, NoteArg.Cards(cards))
    else buriedByHolder(wagon, NoteArg.Player(holder), NoteArg.Cards(cards))

object FamilyWagon:
  val id: PowerId = FamilyWagonCard.power.id
  val cardId: DenizenId = FamilyWagonCard.id
  /** The note on a card play Family Wagon will bury. */
  val buryWarning: String = "Family Wagon will bury it"
  /** "Buried {cards}.", on the holder's turn. */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "{Blue} buried {cards}.", when the holder is not the active player. */
  val buriedByHolder: NoteKey = NoteKey("buried.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): FamilyWagon =
    new FamilyWagon(catalog)

  /** Whether `player` has Family Wagon faceup, and so no adviser limit. */
  def lifts(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }

  private val faceUp = AdviserFace(cardId, Orientation.FaceUp)

  /** Whether `player` holds Family Wagon among their advisers, in either
    * face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists(_.id == cardId)

  /** The player who has Family Wagon faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.map(_.player).find(lifts(ready, _))

  private def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)
```

- [ ] **Step 4: Skip WHEN PLAYED for a buried card**

In `WhenPlayedPower.scala`, replace `applicable` with:

```scala
  /** Fires on its card's faceup play, unless the card went to the world
    * deck before the hook: Family Wagon buried it on arrival, so it was
    * never gained (catalog batch 5 rulings, slice 4). A move of the card
    * elsewhere (Twin Brother, Great Herd) keeps it firing, so a resumed walk
    * folds the hook the same way. */
  final override def applicable(ctx: PowerCtx): Boolean = ctx.operation match
    case CardPlayedFaceup(card, _) => card == cardId &&
      !ctx.state.game.current.commonCards.worldDeck.contains(cardId)
    case _ => false
```

If any WHEN PLAYED power puts its own card into the world deck, stop and ask.

- [ ] **Step 5: Give the holder no limit in `AdviserLimit`**

In `AdviserLimit.scala`:
- Change `import oathdigital.gameplay.powers.cardplay.LostTapestry` to `import oathdigital.gameplay.powers.cardplay.{FamilyWagon, LostTapestry}`.
- Replace `.flatten.minOption.getOrElse(Default)` in `of` with:

```scala
      .flatten.minOption.getOrElse(
        if FamilyWagon.lifts(ready, player) then CardPlayRules.Unbounded
        else Default)
```

- In the object's doc comment, after `the lowest limit a held card sets wins.`, add `A faceup Family Wagon gives its holder no limit (`CardPlayRules.Unbounded`) before those apply.` Rewrap to 80 columns.

- [ ] **Step 6: Register it and import the card**

- `WalkerPowerCatalog.scala`:
  - Change the `cardplay` import to `{CardPlayTriggers, FamilyWagon, LostTapestry}`.
  - Add `Vector(FamilyWagon.forCatalog(catalog)) ++` after `Vector(PiedPiper) ++`.
  - In the doc comment, after the sentence about Lost Tapestry's and Pied Piper's exemptions, add `Family Wagon's lifted limit and note are inert until Search walks `SearchPlayAdviser` too, and its bury runs from the walker's arrival pass.`
- `NewFoundations.scala`: add `FamilyWagonCard` to the `oathdigital.gameplay.powers.cardplay.{...}` import, in alphabetical order, wrapped to 80 columns. The list entry `FamilyWagonCard,` stays.
- `PowerKindsCatalogSuite.scala`: add `"denizen.family-wagon"` to the end of `persistentRules`, after `"relic.lost-tapestry"`.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.powers.AdviserLimitSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CardCatalogSuite oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. If `CardCatalogSuite` or `PowerKindsCatalogSuite` sit in another package, run them by their simple names with `*`.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonSuite.scala src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala
git commit -m "feat(powers): Family Wagon buries every adviser but faceup Nomads"
```

---

### Task 5: The other arrival routes and the board

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala` (add `drawn`)
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` (line ~206)
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonArrivalsSuite.scala`
- Test: `src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala`

**Interfaces:**
- Consumes: `FamilyWagon` and its keys from Task 4; `HornedMask.id`, `HornedMask.denizenDecisionId`, `Enchantress.id`, `Enchantress.decisionId`, `PiedPiper.id`, `PiedPiper.decisionId`.
- Produces: `AdviserLimit.drawn(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int`.

- [ ] **Step 1: Write the route tests**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonArrivalsSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.action.{Enchantress, PiedPiper}
import oathdigital.gameplay.powers.wake.HornedMask
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Family Wagon's bury after the arrival routes other than card play:
  * Horned Mask, a swap (Enchantress) and Pied Piper's move. They run under
  * the production walker and phase powers, so the arrival pass hears them.
  */
class FamilyWagonArrivalsSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerAndPhasePowers
  private val power = FamilyWagon.forCatalog(catalog)
  private val wagon = CatalogNames.denizen("Family Wagon")
  // Nomads.
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val lancers = CatalogNames.denizen("Lancers")
  /** Site-only: Horned Mask takes it as a facedown adviser. */
  private val inn = CatalogNames.denizen("Wayside Inn")
  private val mask = CatalogNames.relic("Horned Mask")
  private val enchantress = CatalogNames.denizen("Enchantress")
  private val piper = CatalogNames.denizen("Pied Piper")

  /** p1 starts the power `id` from `source`. */
  private def started(ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.UsePower(id), p1,
      Vector.empty, Vector(source)))

  /** p1 answers `decision` with `ref`. */
  private def answered(from: OathTransition, decision: String,
      ref: DecisionOptionRef): OathTransition =
    accepted(rules.resolveWalker(from.state, p1, decision,
      ChooseOneAnswer(ref)))

  private def wagonLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events)

  private def worldDeck(state: OathState): Vector[WorldCardId] =
    Outcomes.ready(state).game.current.commonCards.worldDeck

  test("Horned Mask: the holder has no limit, so the Mask takes the " +
      "denizen without a discard, and Family Wagon buries it, since it " +
      "arrives facedown"):
    val ready = Table.start.turn(p1, Phase.Wake).pawn(p1, "Ancient City")
      .relic(p1, mask).adviser(p1, wagon).adviser(p1, elders)
      .adviser(p1, rainBoots).adviser(p1, lancers)
      .denizen(inn, at = "Ancient City").ready
    val asked = started(ready, HornedMask.id, DecisionOptionRef.Relic(mask))
    val done = answered(asked, HornedMask.denizenDecisionId,
      DecisionOptionRef.Denizen(inn))
    assertEquals(Look(Outcomes.ready(done.state)).advisers(p1),
      Vector[CardId](wagon, elders, rainBoots, lancers))
    assertEquals(worldDeck(done.state).lastOption, Some(inn))
    assertEquals(wagonLines(done.events), Vector(NoteText.Said("buried",
      s"Buried ${inn.value}.", covers = true)))

  test("a swap: Enchantress given into a Family Wagon holder's advisers is " +
      "buried, its secret going to the active player, and the line names " +
      "the holder"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, 1).adviser(p2, wagon).adviser(p2, elders).ready
    val asked = started(ready, Enchantress.id,
      DecisionOptionRef.Denizen(enchantress))
    val done = answered(asked, Enchantress.decisionId,
      DecisionOptionRef.Denizen(elders))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.advisers(p1), Vector[CardId](elders))
    assertEquals(after.advisers(p2), Vector[CardId](wagon))
    assertEquals(worldDeck(done.state).lastOption, Some(enchantress))
    assertEquals(after.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(wagonLines(done.events), Vector(NoteText.Said(
      "buried.holder", s"${p2.value} buried ${enchantress.value}.",
      covers = true)))

  test("Pied Piper moved to a Family Wagon holder is buried; the favor is " +
      "still taken, its secret goes to the active player, and Family " +
      "Wagon's line comes first"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, "Ancient City")
      .adviser(p1, piper).secrets(p1, 1)
      .pawn(p2, "Buried Giant").adviser(p2, wagon).favor(p2, 3).ready
    val asked = started(ready, PiedPiper.id, DecisionOptionRef.Denizen(piper))
    val done = answered(asked, PiedPiper.decisionId,
      DecisionOptionRef.Player(p2))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.advisers(p2), Vector[CardId](wagon))
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(worldDeck(done.state).lastOption, Some(piper))
    assertEquals(after.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(wagonLines(done.events), Vector(NoteText.Said(
      "buried.holder", s"${p2.value} buried ${piper.value}.", covers = true)))
    assertEquals(NoteText.said(PiedPiper, done.events).map(_.text), Vector(
      s"Moved to ${p2.value}'s advisers and took 2 favor from them."))
    assertEquals(done.events.collect { case PowerNoted(id, _, _) => id },
      Vector(FamilyWagon.id, PiedPiper.id))
```

In `AdviserLimitProjectionSuite.scala`, add at the end:

```scala
  test("a board with no limit draws its advisers and one empty slot"):
    val ready = Table.start.adviser(p1, "Family Wagon").adviser(p1, "Elders")
      .adviser(p1, "Rain Boots").adviser(p1, "Lancers").ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 5)
    assertEquals(limits(p2.value), 3)
```

- [ ] **Step 2: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.FamilyWagonArrivalsSuite oathdigital.application.AdviserLimitProjectionSuite"`
Expected:
- The three route tests PASS already, because Task 2's pass and Task 4's power cover every route.
- The board test FAILS: the projector adds to `Int.MaxValue` and overflows.

If a route test fails, read the failure before changing anything. A failure there is a gap in the pass, not in the test. Stop and ask if the fix would touch more than Tasks 2 and 4's files.

- [ ] **Step 3: Draw the board through `AdviserLimit.drawn`**

In `AdviserLimit.scala`, after `towardLimit`, add:

```scala
  /** How many adviser slots `player`'s board draws: the limit and one more
    * per adviser that does not count, or, with no limit, the advisers held
    * and one empty slot. */
  def drawn(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Int =
    val limit = of(catalog, ready, player)
    if limit == CardPlayRules.Unbounded then
      ready.game.current.players.find(_.player == player)
        .fold(0)(_.advisers.size) + 1
    else limit + ignored(ready, player).size
```

In `GamePresentationProjector.scala`, replace

```scala
        // An adviser that does not count takes a slot past the limit.
        AdviserLimit.of(catalog, ready, player.player) +
          AdviserLimit.ignored(ready, player.player).size)
```

with

```scala
        AdviserLimit.drawn(catalog, ready, player.player))
```

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.FamilyWagonArrivalsSuite oathdigital.application.AdviserLimitProjectionSuite oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonArrivalsSuite.scala src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala
git commit -m "feat(powers): Family Wagon hears every arrival, and its board has room"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 5)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass, with 20 server tests more than the baseline and the same frontend count.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:
- Under "### Phase - Catalog batch 5", replace the bullet `- [ ] **Slice 4: Adviser arrival.** Family Wagon.` with the line `Every slice is done.`
- In "## Now", replace `**Phase - Catalog batch 5** is in progress: slices 1` / `to 3 are done.` (it spans two lines) with `**Phase - Catalog batch 5** is complete.`, wrapped as the paragraph is.

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 5 slice 4"
```
