# Powers Slice 1d: Movement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three movement relics, each declared only as a `PhasePower` over existing operations: Whistle, Brass Horse and Magic Carpet.

**Architecture:** Each relic is an Act-timed `PhasePower` built on the slice 1a `PaidAction` kit, so the engine pays the cost onto the relic and each power writes only `build`. A pawn relocation is a plain `Move`, never Travel. Conditional decisions use the "live decision" walker shape recorded in the design. The one engine change is small: `Reveal` of a card in a regional discard is accepted as a no-op, so Brass Horse can reveal publicly (Task 1).

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (row "Slice 1: ACTION powers": R08 Whistle, R03 Brass Horse, R39 Magic Carpet; the "Movement" rule). Follows the [slice 1a plan](2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), whose `PaidAction` and `PowerFixture` this plan reuses, and the slice 1b and 1c plans, whose registration pattern (`MovementPowers`, like `DiceAndRelicDrawPowers` and `TargetPowers`), `PowerAnswers` and `TargetsFixture` it reuses.

> **Revised after approval.** The user decided open question 1 in favour of a public `Reveal` and an engine change to allow it (now Task 1, in its own commit). Slices 1b and 1c merged first, so this plan registers through one `MovementPowers` object, reads answers through `PowerAnswers`, and builds its test helpers on `TargetsFixture`. The code below is the code as executed.

## Global Constraints

- `BackendArchitectureSuite` applies: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources (a lowercase substring scan); a power imports nothing from `oathdigital.gameplay.walker`; no `copy(advisers =`, `temporaryHands.updated(` or similar direct state writes under `gameplay/powers`.
- A power is declared solely by a `ContributingPower` or `PhasePower`. The only engine change is Task 1's, and it names no power. If another task finds it cannot be declared that way, stop and report the minimal engine change instead of adding one.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the suite with `./sbtw test`. Run one suite with `./sbtw "testOnly <fully.qualified.Suite>"`. After every task run `./sbtw test` and `python3 scripts/check-architecture.py`; both must be green before the commit.
- Test fixtures keep the card inventory whole: a card leaves the place it came from when it is placed, or `CardIndex` fails.
- Shared files (`PowerFixture.scala`, the design's Slicing table, the rulings appendix, `PhasePowerCatalog.scala`) are edited by other slices in parallel. Keep every edit to them minimal: new test helpers live in new files, and the shared registry gets one line.

## What planning found

These facts were read from the code or checked by running it. They are not in the design.

1. **`PhasePower.build` receives no catalog.** A card's suit is a catalog fact, and Brass Horse needs it, so `BrassHorse` is a class holding the `ExecutableCatalog` and `MovementPowers.forCatalog(catalog)` constructs it. Whistle and Magic Carpet need no catalog and are case objects.
2. **A regional discard holds card ids only, so a card there has no orientation state.** `Reveal` expands to a faceup `Flip`, and both were rejected with `unsupported-orientation` at `Location.RegionalDiscard`. A discard is always facedown, so a reveal there changes nothing. Task 1 accepts a faceup look at a regional discard as a no-op and leaves a facedown `Flip` unsupported. The top of a pile is its last element (discards insert with `topAtHead = false`).
3. **Secrets move faceup between a card and a board.** A cost places a faceup secret, and a `Move` of secrets from a card to another player's board adds it faceup to the target's `faceUpSecrets`. Whistle therefore leaves a faceup secret on the target's board.
4. **A relic `Give` between two play areas is valid and carries the card's tokens.** A secret resting on the Carpet travels with it. `Discard.Relic` with the relic's secrets succeeds and returns them to the holder facedown, as `Bury.standard` does for Magic Waterskin.
5. **Another player's pawn can be moved by a plain `Move`.** `Move(Piece.Pawn(target), Site(from), Site(to))` is valid whoever the actor is. A `Move` must change location (`require(from != to ...)`), so Magic Carpet skips the move when the chosen site is the current one.
6. **Decision vocabulary needed already exists.** `ChooseOne` accepts a single option and mixed `Button` and `Player` options. `DecisionOption.Site` and `DecisionOption.Player` project generically. A decision inside a phase power parks as `OathContinue.AwaitingPowerDecision(actor, DecisionId(id))` and the registry names no power.
7. **The live-decision shape is safe for all three.** A `Branch` whose `select` returns only a `Decide` is re-selected on resume against the state stored at the park. Whistle's and Brass Horse's branches read pawns, the discard pile and the map, none of which the preceding `PayCost` or `Reveal` changes. Magic Carpet's second branch sits after the pawn move it depends on.
8. **No registry work beyond `PhasePowerCatalog`.** The relic power ids `relic.whistle`, `relic.brass-horse` and `relic.magic-carpet` already exist in the catalog, no relic is listed in `ActionPowers`, and `PowerAccess` already grants only faceup relics in the holder's play area.
9. **First-game layout used by the tests.** Three players: the actor `p2` at ancient-city, `p1` at buried-giant, `p3` at broken-peaks. Regions: Cradle holds ancient-city and broken-peaks, Provinces holds buried-giant, deep-woods and desolate-shore, Hinterland holds dunes, fair-isle and golden-valley. The only site cards are a ruined beast edifice (E26) at deep-woods and a ruined hearth edifice (E21) at golden-valley. Every regional discard already holds cards, and relics R03, R08 and R39 are in the relic deck.

## Decisions on the rulings

Defaults used, decided with the user.

1. **Brass Horse reveals publicly**, with `Reveal`, after the engine change of Task 1.
2. **Whistle asks even with a single candidate.**
3. **Magic Carpet asks no second question when nobody is eligible**: it discards the Carpet.
4. **Whistle's secret arrives faceup** on the target's board.
5. **A ruined edifice counts as a card at a site for Brass Horse**; either face does, because the suit belongs to the card.
6. **A secret resting on a given Carpet goes with it.**

## File Structure

- Modify `src/main/scala/oathdigital/gameplay/operations/OperationStateAdapter.scala`, `OperationValidator.scala` and `OperationStateMutation.scala` (Task 1).
- Create `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`: the pawn reads and writes shared by the three relics.
- Create `.../action/Whistle.scala`, `MagicCarpet.scala`, `BrassHorse.scala` and `MovementPowers.scala`.
- Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` (one added line).
- Create test support `src/test/scala/oathdigital/gameplay/powers/action/MovementFixture.scala`, built on `TargetsFixture`.
- Create `src/test/scala/oathdigital/gameplay/RevealDiscardSuite.scala` and, under `.../powers/action/`, `WhistleSuite.scala`, `MagicCarpetSuite.scala`, `BrassHorseSuite.scala`.
- Modify the design's Slicing row and status line and the rulings appendix (Task 5).

---

### Task 1: Accept `Reveal` of a regional discard

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateAdapter.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`
- Test: `src/test/scala/oathdigital/gameplay/RevealDiscardSuite.scala`

**Interfaces:**
- Produces `OperationStateAdapter.isDiscardLook(at: Location, orientation: Orientation): Boolean`, `private[operations]`. Both the validator and the mutation ask it, so the two cannot disagree.
- Later tasks rely on `Reveal(card, Location.RegionalDiscard(region))` being accepted and changing no state.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/RevealDiscardSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powers.PowerFixture.base
import oathdigital.model._

/** Revealing the top card of a regional discard is a public no-op: a discarded
  * card has no orientation state, so nothing changes, and a facedown flip of
  * it is still not accepted.
  */
class RevealDiscardSuite extends munit.FunSuite {
  private val region = Region.Cradle
  private val at = Location.RegionalDiscard(region)
  private val top = base.game.current.commonCards.discard(region).last
  private def run(ops: CoreOperation*) = OperationPipeline.run(base,
    ops.toVector, OperationPolicy.Permissive)(Right(_))

  test("revealing a discarded card is accepted and changes no state") {
    val result = run(Reveal(top, at)).toOption.get
    assertEquals(result.state, base)
    assertEquals(result.executed, Vector(Reveal(top, at)))
  }

  test("a card that is not in that discard cannot be revealed there") {
    val other = base.game.current.commonCards.discard(Region.Provinces).last
    assert(run(Reveal(other, at)).isLeft)
  }

  test("a facedown flip of a discarded card stays unsupported") {
    assert(run(Flip(top, at, Orientation.FaceDown)).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.RevealDiscardSuite"`
Expected: FAIL, the first test with `unsupported-orientation`.

- [ ] **Step 3: Write the helper**

In `OperationStateAdapter.scala`, after `import OperationError._`:

```scala
  /** A `Reveal` of a card in a regional discard. A discarded card has no
    * orientation state, because a discard is always facedown, so looking at it
    * is accepted and changes nothing. A facedown flip of such a card stays
    * unsupported.
    */
  private[operations] def isDiscardLook(at: Location,
      orientation: Orientation): Boolean =
    at.isInstanceOf[Location.RegionalDiscard] &&
      orientation == Orientation.FaceUp
```

- [ ] **Step 4: Ask it in the validator and the mutation**

In `OperationValidator.scala`, pass the orientation to `flipViolation` and accept the look:

```scala
      case ((result, state), Flip(id, at, orientation)) =>
        (result ++ flipViolation(ready, id, at, orientation), state)
```
```scala
  private def flipViolation(
      ready: ReadyGame,
      id: CardId,
      at: Location,
      orientation: Orientation
  ): Vector[OperationError] = card(ready, id, at) match {
    case Left(error) => Vector(error)
    case Right(located) => located.state match {
      case Some(_: DenizenState) | Some(_: VisionState) |
          Some(_: RelicState) => Vector.empty
      // Looking at a discarded card: it has no orientation state (a discard is
      // always facedown), so revealing it changes nothing.
      case None if OperationStateAdapter.isDiscardLook(at, orientation) =>
        Vector.empty
      case _ => Vector(UnsupportedOrientation(id, at))
    }
  }
```

In `OperationStateMutation.scala`, in `flipCard`, add the case before the fallback:

```scala
      case None if isDiscardLook(at, orientation) => Right(ready)
```

- [ ] **Step 5: Run the suite, then the gates**

Run: `./sbtw "testOnly oathdigital.gameplay.RevealDiscardSuite"`
Expected: PASS, 3 tests. Then `./sbtw test` and `python3 scripts/check-architecture.py`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: accept Reveal of a regional discard as a no-op

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: The movement kit and Whistle

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Whistle.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MovementFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WhistleSuite.scala`

**Interfaces:**
- Consumes `PaidAction(idValue: String, cost: Cost)` from slice 1a, `PowerAnswers.one(pending, decision)` and `PowerAnswers.missing(decision)` from slice 1b, and `TargetsFixture` (`rules`, `withPawn`, `replayed`) from slice 1c.
- Produces `PawnMoves.pawnSite(ready, player): Either[OathViolation, SiteId]` and `PawnMoves.atOtherSites(ready, player): Vector[PlayerId]`. Tasks 3 and 4 extend `PawnMoves`.
- Produces `case object Whistle` with `Whistle.id` and `Whistle.decisionId = "power.whistle.target"`, and `MovementPowers.forCatalog(catalog): Vector[PhasePower]`, which Tasks 3 and 4 extend.
- Produces the fixture `MovementFixture`: the players `p1`, `p3`; the sites `ancientCity`, `brokenPeaks`, `buriedGiant`, `deepWoods`, `desolateShore`, `dunes`; and `pawnOf`, `withSecrets`, `relicOf`, `withRelicTokens`, `use`, `choose`, `readyOf`, `usable`, `parkedAt`, `backToActing`, `ops`. Task 4 adds `withDiscard`, `freshDenizen` and `aVision`.

Ruling: cost 1 secret placed on the Whistle. Choose another player whose pawn is at a different site, move their pawn to your site, then move the secret from the Whistle to their board. With no eligible player the cost is paid, nothing else happens and the secret stays.

- [ ] **Step 1: Write the test support**

`src/test/scala/oathdigital/gameplay/powers/action/MovementFixture.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Staging and driving shared by the Whistle, Brass Horse and Magic Carpet
  * suites. The first game seats three players: the actor (p2) at
  * ancient-city, p1 at buried-giant and p3 at broken-peaks.
  */
object MovementFixture {
  import PowerFixture._
  import TargetsFixture.rules

  val p1: PlayerId = PlayerId("p1")
  val p3: PlayerId = PlayerId("p3")

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val brokenPeaks: SiteId = SiteId("site:broken-peaks")
  val buriedGiant: SiteId = SiteId("site:buried-giant")
  val deepWoods: SiteId = SiteId("site:deep-woods")
  val desolateShore: SiteId = SiteId("site:desolate-shore")
  val dunes: SiteId = SiteId("site:dunes")

  def pawnOf(ready: ReadyGame, id: PlayerId = actor): SiteId =
    player(ready, id).pawnSite.get

  def withSecrets(ready: ReadyGame, faceUp: Int): ReadyGame =
    withBoard(ready)(_.copy(faceUpSecrets = faceUp))

  def relicOf(ready: ReadyGame, id: RelicId,
      holder: PlayerId = actor): Option[RelicState] =
    player(ready, holder).relics.find(_.id == id)

  def withRelicTokens(ready: ReadyGame, id: RelicId, tokens: Tokens)
      : ReadyGame = updateActor(ready)(p => p.copy(relics = p.relics.map(r =>
    if (r.id == id) r.copy(tokens = tokens) else r)))

  def use(ready: ReadyGame, power: PowerId, relic: RelicId)
      : Either[OathViolation, OathTransition] = rules.startWalker(Ready(ready),
    ActionRef.UsePower(power), actor, Vector.empty,
    Vector(DecisionOptionRef.Relic(relic)))

  def choose(state: OathState, decisionId: String, ref: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(state,
    actor, decisionId, DecisionAnswer.ChooseOneAnswer(ref))

  def readyOf(state: OathState): ReadyGame = state.asInstanceOf[Ready].value

  def usable(ready: ReadyGame, power: PowerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == power)

  def parkedAt(transition: OathTransition, decisionId: String): Boolean =
    transition.continue ==
      OathContinue.AwaitingPowerDecision(actor, DecisionId(decisionId))

  def backToActing(transition: OathTransition): Boolean =
    transition.continue == OathContinue.ActActionSelection(actor)

  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
}
```

- [ ] **Step 2: Write the failing Whistle suite**

`src/test/scala/oathdigital/gameplay/powers/action/WhistleSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WhistleSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val whistle = RelicId("R08")
  private def staged(secrets: Int = 2) = inPhase(
    withSecrets(withRelic(base, whistle), secrets), Phase.Act)
  private val target = DecisionOptionRef.Player(p3)

  test("Whistle is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Whistle.id).isDefined)
  }

  test("it pulls the chosen pawn to the actor's site and hands over the secret") {
    val start = staged()
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assertEquals(relicOf(readyOf(parked.state), whistle).get.tokens, Tokens(0, 1))

    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(pawnOf(after, p3), ancientCity)
    assertEquals(pawnOf(after, p1), buriedGiant)
    assertEquals(pawnOf(after), ancientCity)
    assertEquals(relicOf(after, whistle).get.tokens, Tokens.empty)
    assertEquals(player(after, p3).board.faceUpSecrets,
      player(start, p3).board.faceUpSecrets + 1)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))
  }

  test("only players at other sites are offered, even when there is one") {
    val start = withPawn(staged(), p1, ancientCity)
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assert(choose(parked.state, Whistle.decisionId,
      DecisionOptionRef.Player(p1)).isLeft)
    assert(choose(parked.state, Whistle.decisionId, target).isRight)
  }

  test("with nobody to pull, the cost is paid and the secret stays") {
    val start = withPawn(withPawn(staged(), p1, ancientCity), p3, ancientCity)
    val done = use(start, Whistle.id, whistle).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, whistle).get.tokens, Tokens(0, 1))
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(pawnOf(after, p1), ancientCity)
    assert(!ops(done.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assert(!usable(after, Whistle.id), "the secret still rests on the Whistle")
  }

  test("it is unusable without a secret, when occupied, or facedown") {
    assert(usable(staged(), Whistle.id))
    assert(!usable(staged(secrets = 0), Whistle.id))
    assert(use(staged(secrets = 0), Whistle.id, whistle).isLeft)
    assert(!usable(withRelicTokens(staged(), whistle, Tokens(0, 1)), Whistle.id))
    val facedown = inPhase(withSecrets(
      withRelic(base, whistle, Orientation.FaceDown), 2), Phase.Act)
    assert(!usable(facedown, Whistle.id))
    assert(use(facedown, Whistle.id, whistle).isLeft)
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WhistleSuite"`
Expected: FAIL to compile, `not found: value Whistle`.

- [ ] **Step 4: Write `PawnMoves`, Whistle and the registration**

`src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Reads and writes shared by the movement relics (Whistle, Brass Horse,
  * Magic Carpet). A pawn relocation that is not Travel is a plain `Move`, and
  * nothing here runs a Travel window.
  */
object PawnMoves {
  def pawnSite(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, SiteId] =
    PowerAccess.pawnSite(ready, player).toRight(OathViolation.InvalidEventOrder(
      s"${player.value} has no pawn on the map"))

  /** Other players whose pawn is at a site other than `player`'s. */
  def atOtherSites(ready: ReadyGame, player: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(here =>
      ready.game.current.players.collect {
        case other if other.player != player &&
            other.pawnSite.exists(_ != here) => other.player
      })
}
```

`src/main/scala/oathdigital/gameplay/powers/action/Whistle.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Whistle (relic R08), ACTION: place 1 secret on this relic, take the pawn
  * of another player who is at a different site, place it at your site, and
  * move the secret from the Whistle to that player's board.
  *
  * The decision is a live `Branch` that is empty when no player qualifies. The
  * cost is paid whatever happens, so with nobody to pull the secret stays on
  * the Whistle, and the empty-card rule keeps it unusable until it is gone.
  */
case object Whistle extends PaidAction("relic.whistle", Cost(secret = 1)) {
  val decisionId: String = "power.whistle.target"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(whistle) => Right(Sequence(Vector[Operation](
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => pull(state, player, whistle, pending)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val targets = PawnMoves.atOtherSites(ready, player)
    if (targets.isEmpty) Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      targets.map(target => DecisionOption.Player(
        DecisionOptionRef.Player(target))),
      heading = Some("Whistle: choose the player whose pawn you pull to " +
        "your site"))))
  }

  private def pull(ready: ReadyGame, player: PlayerId, whistle: RelicId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    if (PawnMoves.atOtherSites(ready, player).isEmpty) Right(Vector.empty)
    else for {
      here <- PawnMoves.pawnSite(ready, player)
      target <- PowerAnswers.one(pending, decisionId).collect {
        case DecisionOptionRef.Player(id) => id
      }.toRight(PowerAnswers.missing(decisionId))
      from <- PawnMoves.pawnSite(ready, target)
    } yield Vector[CoreOperation](
      Move(Piece.Pawn(target), PositionedLocation(Location.Site(from)),
        PositionedLocation(Location.Site(here))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(whistle)),
        PositionedLocation(Location.PlayArea(target))))
}
```

`src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of slice 1d, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[DiceAndRelicDrawPowers]] and [[TargetPowers]].
  */
object MovementPowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Whistle)
}
```

In `PhasePowerCatalog.scala`, add `MovementPowers` to the `action` import and one line at the end of the vector:

```scala
      TargetPowers.forCatalog(catalog) ++
      MovementPowers.forCatalog(catalog))
```

- [ ] **Step 5: Run the suite, then the gates**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WhistleSuite"`
Expected: PASS, 5 tests. Then `./sbtw test` and `python3 scripts/check-architecture.py`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Whistle

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Magic Carpet

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MagicCarpet.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MagicCarpetSuite.scala`

**Interfaces:**
- Consumes `PawnMoves.pawnSite`, `atOtherSites` and the `MovementFixture` helpers from Task 2.
- Produces `PawnMoves.siteChoice(decisionId, owner, sites, heading): Decide`, `PawnMoves.relocate(ready, player, to): Either[OathViolation, Vector[CoreOperation]]` and `PawnMoves.chosenSite(pending, decisionId): Either[OathViolation, SiteId]`.
- Produces `case object MagicCarpet` with `MagicCarpet.id`, `siteDecisionId = "power.magic-carpet.site"`, `fateDecisionId = "power.magic-carpet.fate"` and `discard: DecisionOptionRef.Button`.

Ruling: no cost. Place your pawn at any site, including the current one, in which case the move is skipped. Then choose one: discard the Carpet with `Discard.Relic`, or give it, faceup, to a player whose pawn is at a site different from your new one. With no eligible player the only choice is to discard.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/MagicCarpetSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class MagicCarpetSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val carpet = RelicId("R39")
  private def staged = inPhase(withRelic(base, carpet), Phase.Act)
  private def site(id: SiteId) = DecisionOptionRef.Site(id)

  /** Starts the Carpet and answers the site question. */
  private def placedAt(start: ReadyGame, at: SiteId) = {
    val parked = use(start, MagicCarpet.id, carpet).toOption.get
    assert(parkedAt(parked, MagicCarpet.siteDecisionId))
    (parked, choose(parked.state, MagicCarpet.siteDecisionId, site(at)).toOption.get)
  }

  test("Magic Carpet is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MagicCarpet.id).isDefined)
  }

  test("it moves the pawn to the chosen site, then can be discarded") {
    val (first, placed) = placedAt(staged, deepWoods)
    assert(parkedAt(placed, MagicCarpet.fateDecisionId))
    assertEquals(pawnOf(readyOf(placed.state)), deepWoods)

    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(replayed(staged, first.events ++ placed.events ++ done.events),
      Right(done.state))
  }

  test("it can be given faceup to a player at a different site") {
    val (_, placed) = placedAt(staged, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(relicOf(after, carpet, p3).map(_.orientation),
      Some(Orientation.FaceUp))
    assertEquals(after.game.current.setAsideRelics, Vector.empty)
  }

  test("a player at the new site is not eligible to receive it") {
    val (_, placed) = placedAt(staged, brokenPeaks)
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).isLeft)
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p1)).isRight)
  }

  test("choosing the current site skips the move") {
    val (_, placed) = placedAt(staged, ancientCity)
    assert(parkedAt(placed, MagicCarpet.fateDecisionId))
    assert(!ops(placed.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assertEquals(pawnOf(readyOf(placed.state)), ancientCity)
  }

  test("with nobody eligible the Carpet is discarded without a second question") {
    val crowded = withPawn(withPawn(staged, p1, deepWoods), p3, deepWoods)
    val (_, placed) = placedAt(crowded, deepWoods)
    val after = readyOf(placed.state)
    assert(backToActing(placed))
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(relicOf(after, carpet), None)
  }

  test("a secret on the Carpet returns to its holder facedown when it is discarded") {
    val start = withRelicTokens(staged, carpet, Tokens(0, 1))
    val (_, placed) = placedAt(start, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    assertEquals(player(readyOf(done.state)).board.faceDownSecrets,
      player(start).board.faceDownSecrets + 1)
  }

  test("it costs nothing and needs no secret") {
    assert(usable(withSecrets(staged, 0), MagicCarpet.id))
  }

  test("a facedown Carpet cannot be used") {
    val facedown = inPhase(withRelic(base, carpet, Orientation.FaceDown), Phase.Act)
    assert(!usable(facedown, MagicCarpet.id))
    assert(use(facedown, MagicCarpet.id, carpet).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MagicCarpetSuite"`
Expected: FAIL to compile, `not found: value MagicCarpet`.

- [ ] **Step 3: Extend `PawnMoves` and write Magic Carpet**

In `PawnMoves.scala`, add these members after `atOtherSites`:

```scala
  def siteChoice(decisionId: String, owner: PlayerId, sites: Vector[SiteId],
      heading: String): Decide = Decide(decisionId, owner,
    DecisionQuery.ChooseOne(sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading)))

  /** The `Move` that puts `player`'s pawn at `to`. Nothing when the pawn is
    * already there, because a `Move` must change location.
    */
  def relocate(ready: ReadyGame, player: PlayerId, to: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    pawnSite(ready, player).map(from =>
      if (from == to) Vector.empty
      else Vector(Move(Piece.Pawn(player),
        PositionedLocation(Location.Site(from)),
        PositionedLocation(Location.Site(to)))))

  def chosenSite(pending: PendingTree, decisionId: String)
      : Either[OathViolation, SiteId] = PowerAnswers.one(pending, decisionId)
    .collect { case DecisionOptionRef.Site(site) => site }
    .toRight(PowerAnswers.missing(decisionId))
```

`src/main/scala/oathdigital/gameplay/powers/action/MagicCarpet.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Magic Carpet (relic R39), ACTION, no cost: place your pawn at any site,
  * then discard the Carpet or give it to a player whose pawn is at a site
  * different from your new one.
  *
  * The first decision names the site, and the move is skipped when it is the
  * current site. The second is a live `Branch` after the move, so it reads the
  * new pawn site. It is not asked when nobody is eligible: the Carpet is then
  * discarded.
  */
case object MagicCarpet extends PaidAction("relic.magic-carpet", Cost.free) {
  val siteDecisionId: String = "power.magic-carpet.site"
  val fateDecisionId: String = "power.magic-carpet.fate"
  val discard: DecisionOptionRef.Button = DecisionOptionRef.Button("discard")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(carpet) => Right(Sequence(Vector[Operation](
      PawnMoves.siteChoice(siteDecisionId, player,
        ready.game.current.map.inPlay,
        "Magic Carpet: choose the site to place your pawn at"),
      BuildOps((state, pending) => PawnMoves.chosenSite(pending,
        siteDecisionId).flatMap(PawnMoves.relocate(state, player, _))),
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => settle(state, player, carpet, pending)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val takers = PawnMoves.atOtherSites(ready, player)
    if (takers.isEmpty) Vector.empty
    else Vector(Decide(fateDecisionId, player, DecisionQuery.ChooseOne(
      DecisionOption.Button(discard, "Discard Magic Carpet") +:
        takers.map(taker => DecisionOption.Player(
          DecisionOptionRef.Player(taker))),
      heading = Some("Magic Carpet: discard it, or give it to a player at " +
        "another site"))))
  }

  private def settle(ready: ReadyGame, player: PlayerId, carpet: RelicId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    for {
      held <- PlayerFacts.player(ready, player)
      relic <- held.relics.find(_.id == carpet).toRight(
        OathViolation.InvalidEventOrder(
          s"${carpet.value} is not held by ${player.value}"))
    } yield PowerAnswers.one(pending, fateDecisionId) match {
      case Some(DecisionOptionRef.Player(taker)) => Vector[CoreOperation](
        Give(Piece.Card(carpet), player, Location.PlayArea(player),
          Location.PlayArea(taker)))
      case _ => Vector[CoreOperation](Discard.Relic(carpet,
        PositionedLocation(Location.PlayArea(player)), relic.tokens.secrets,
        player))
    }
}
```

In `MovementPowers.scala`: `Vector[PhasePower](Whistle, MagicCarpet)`.

- [ ] **Step 4: Run the suite, then the gates**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MagicCarpetSuite"`
Expected: PASS, 9 tests. Then `./sbtw test` and `python3 scripts/check-architecture.py`.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: implement Magic Carpet

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Brass Horse

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BrassHorse.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MovementPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MovementFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BrassHorseSuite.scala`

**Interfaces:**
- Consumes `PawnMoves.pawnSite`, `siteChoice`, `relocate`, `chosenSite`, the `MovementFixture` helpers from Tasks 2 and 3, and `Reveal` at a regional discard from Task 1.
- Produces `PawnMoves.sitesOtherThan(ready, site): Vector[SiteId]`.
- Produces `final class BrassHorse(catalog: ExecutableCatalog)` with `BrassHorse.id` and `BrassHorse.decisionId = "power.brass-horse.site"`. It is constructed by `MovementPowers.forCatalog(catalog)`, because a suit is a catalog fact.
- Produces the fixture members `withDiscard(ready, region, pile): ReadyGame`, `freshDenizen(ready, suit, skip = 0): DenizenId` and `aVision(ready): VisionId`.

Ruling: cost 1 secret placed. "Your region" is the region of your pawn's site. Reveal the top card of that region's discard pile, then turn it facedown again. Place your pawn at a different site holding a card of the same suit (denizen or edifice). No decision is asked when exactly one site matches. If the pile is empty, the top is a Vision, or no site matches, place it at any other site.

- [ ] **Step 1: Add the fixture helpers**

In `MovementFixture.scala`, insert these members before `def use(`:

```scala
  /** Replaces a regional discard pile. The old pile goes back under the world
    * deck and a new card leaves it if it is there, so the inventory stays
    * whole. A card the first game did not deal is simply added.
    */
  def withDiscard(ready: ReadyGame, region: Region,
      pile: Vector[WorldCardId]): ReadyGame = ready.updateCurrent { c =>
    val cards = c.commonCards
    c.copy(commonCards = cards.copy(
      worldDeck = cards.worldDeck.filterNot(pile.contains) ++
        cards.discard(region).filterNot(pile.contains),
      regionalDiscards = cards.regionalDiscards.updated(region, pile)))
  }

  /** A denizen of `suit` that is nowhere in the game yet. */
  def freshDenizen(ready: ReadyGame, suit: Suit, skip: Int = 0): DenizenId = {
    val present = CardIndex.from(ready.game).toOption.get.ids
    catalog.denizens.filter(_.suit == suit).map(d => DenizenId(d.id.value))
      .filterNot(present.contains)(skip)
  }

  def aVision(ready: ReadyGame): VisionId =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case vision: VisionId => vision }.get
```

- [ ] **Step 2: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/BrassHorseSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BrassHorseSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val horse = RelicId("R03")
  private def staged = inPhase(withSecrets(withRelic(base, horse), 2), Phase.Act)
  private val beastTop = freshDenizen(staged, Suit.Beast)
  private val beastElsewhere = freshDenizen(staged, Suit.Beast, skip = 1)
  private val nomadTop = freshDenizen(staged, Suit.Nomad)
  private def cradleTopped(card: WorldCardId) =
    withDiscard(staged, Region.Cradle, Vector(card))
  private def site(id: SiteId) = DecisionOptionRef.Site(id)
  private def reveals(events: Vector[OathEvent]) = ops(events).collect {
    case reveal: Reveal => reveal }

  // The first game puts a ruined beast edifice at deep-woods and a ruined
  // hearth edifice at golden-valley, and no other card at any site.

  test("Brass Horse is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(BrassHorse.id).isDefined)
    assert(usable(staged, BrassHorse.id))
  }

  test("one matching site takes the pawn there without a question") {
    val start = cradleTopped(beastTop)
    val done = use(start, BrassHorse.id, horse).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(pawnOf(after), deepWoods)
    assertEquals(relicOf(after, horse).get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.commonCards.discard(Region.Cradle).last,
      beastTop)
    assertEquals(reveals(done.events), Vector(
      Reveal(beastTop, Location.RegionalDiscard(Region.Cradle))))
    assertEquals(after.knowledge, start.knowledge)
    assert(PaidActionHarness.wireRoundTrips(done.events))
    assertEquals(replayed(start, done.events), Right(done.state))
  }

  test("several matching sites ask, and only they are offered") {
    val start = atSite(cradleTopped(beastTop), beastElsewhere, dunes)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site(buriedGiant)).isLeft)
    val done = choose(parked.state, BrassHorse.decisionId, site(dunes)).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(readyOf(done.state)), dunes)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))
  }

  test("the current site never counts as a match") {
    val start = atSite(cradleTopped(beastTop), beastElsewhere, ancientCity)
    val done = use(start, BrassHorse.id, horse).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(readyOf(done.state)), deepWoods)
  }

  test("with no matching site any other site may be chosen") {
    val start = cradleTopped(nomadTop)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site(ancientCity)).isLeft)
    val done = choose(parked.state, BrassHorse.decisionId,
      site(desolateShore)).toOption.get
    assertEquals(pawnOf(readyOf(done.state)), desolateShore)
  }

  test("an empty pile reveals nothing and any other site may be chosen") {
    val start = withDiscard(staged, Region.Cradle, Vector.empty)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events), Vector.empty)
    assert(choose(parked.state, BrassHorse.decisionId, site(dunes)).isRight)
  }

  test("a Vision on top has no suit, so any other site may be chosen") {
    val vision = aVision(staged)
    val start = cradleTopped(vision)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events),
      Vector(Reveal(vision, Location.RegionalDiscard(Region.Cradle))))
    assert(choose(parked.state, BrassHorse.decisionId, site(deepWoods)).isRight)
  }

  test("the region is the one the pawn's site is in") {
    val provincesTop = freshDenizen(staged, Suit.Beast)
    val start = withDiscard(withPawn(staged, actor, buriedGiant),
      Region.Provinces, Vector(provincesTop))
    val done = use(start, BrassHorse.id, horse).toOption.get
    assertEquals(reveals(done.events), Vector(
      Reveal(provincesTop, Location.RegionalDiscard(Region.Provinces))))
    assertEquals(pawnOf(readyOf(done.state)), deepWoods)
  }

  test("it is unusable without a secret, when occupied, or facedown") {
    val id = BrassHorse.id
    assert(!usable(withSecrets(staged, 0), id))
    assert(use(withSecrets(staged, 0), id, horse).isLeft)
    assert(!usable(withRelicTokens(staged, horse, Tokens(0, 1)), id))
    val facedown = inPhase(withSecrets(
      withRelic(base, horse, Orientation.FaceDown), 2), Phase.Act)
    assert(!usable(facedown, id))
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BrassHorseSuite"`
Expected: FAIL to compile, `not found: value BrassHorse`.

- [ ] **Step 4: Extend `PawnMoves` and write Brass Horse**

In `PawnMoves.scala`, add this member before `atOtherSites`:

```scala
  /** Every site in play except `site`, in map order. */
  def sitesOtherThan(ready: ReadyGame, site: SiteId): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(_ != site)
```

`src/main/scala/oathdigital/gameplay/powers/action/BrassHorse.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Brass Horse (relic R03), ACTION: place 1 secret on this relic, reveal the
  * top card of the discard pile of the region your pawn is in, and place your
  * pawn at a different site holding a card of the same suit (a denizen or an
  * edifice). If it cannot, place it at any other site.
  *
  * The reveal is a public `Reveal` of the pile's top card. A discarded card
  * has no orientation state, so the reveal changes nothing and "turn it
  * facedown again" needs no operation. The destination decision is a live
  * `Branch`, asked only when more than one site qualifies. The reveal sits
  * before it and changes neither the pile nor the pawn.
  *
  * The power holds the catalog because a card's suit is a catalog fact and
  * `PhasePower.build` receives no catalog.
  */
final class BrassHorse(catalog: ExecutableCatalog)
    extends PaidAction(BrassHorse.id.value, Cost(secret = 1)) {
  import BrassHorse._

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => reveal(state, player)),
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => place(state, player, pending)))))

  private def region(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Region] = for {
    site <- PawnMoves.pawnSite(ready, player)
    found <- ready.game.current.map.regionOf(site).toRight(
      OathViolation.InvalidEventOrder(s"${site.value} is in no region"))
  } yield found

  private def top(ready: ReadyGame, region: Region): Option[WorldCardId] =
    ready.game.current.commonCards.discard(region).lastOption

  private def holds(ready: ReadyGame, site: SiteId, suit: Suit): Boolean =
    ready.game.current.map.sites.get(site).exists(_.denizens.exists(card =>
      catalog.suitOf(card.id).contains(suit)))

  /** The sites the pawn may be placed at: other sites holding a card of the
    * revealed suit, or every other site when there is none or no card was
    * revealed. A Vision has no suit.
    */
  private def destinations(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[SiteId]] = for {
    here <- PawnMoves.pawnSite(ready, player)
    found <- region(ready, player)
  } yield {
    val others = PawnMoves.sitesOtherThan(ready, here)
    val matching = top(ready, found).flatMap(catalog.suitOf).toVector
      .flatMap(suit => others.filter(holds(ready, _, suit)))
    if (matching.nonEmpty) matching else others
  }

  private def reveal(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    region(ready, player).map(found => top(ready, found).toVector.map(card =>
      Reveal(card, Location.RegionalDiscard(found)): CoreOperation))

  /** An error surfaces later, from `place`, so it is not swallowed here. */
  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    destinations(ready, player) match {
      case Right(sites) if sites.size > 1 => Vector(PawnMoves.siteChoice(
        decisionId, player, sites,
        "Brass Horse: choose the site to place your pawn at"))
      case _ => Vector.empty
    }

  private def place(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    destinations(ready, player).flatMap {
      case Vector() => Right(Vector.empty)
      case Vector(only) => PawnMoves.relocate(ready, player, only)
      case _ => PawnMoves.chosenSite(pending, decisionId)
        .flatMap(PawnMoves.relocate(ready, player, _))
    }
}

object BrassHorse {
  val id: PowerId = PowerId("relic.brass-horse")
  val decisionId: String = "power.brass-horse.site"
}
```

In `MovementPowers.scala`: `Vector[PhasePower](Whistle, MagicCarpet, new BrassHorse(catalog))`.

- [ ] **Step 5: Run the suite, then the gates**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BrassHorseSuite"`
Expected: PASS, 9 tests. Then `./sbtw test` and `python3 scripts/check-architecture.py`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Brass Horse

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md` (status line, the 1d row and the slice 1d plan sentence)
- Modify: `docs/superpowers/specs/2026-09-20-powers-rulings.md` (the three slice 1d rows and one new notes list)
- Modify: this plan, to match what was executed.

- [ ] **Step 1: Update the design**

In `2026-09-20-powers-design.md`, add slice 1d and its plan to the status line, mark the 1d Slicing row `(implemented)` with the engine column `none beyond accepting Reveal at a regional discard`, and add the slice 1d plan sentence after the slice 1c one.

- [ ] **Step 2: Record results in the rulings appendix**

Append `Implemented (slice 1d).` to the rulings of R08 Whistle, R03 Brass Horse and R39 Magic Carpet. Add a `### Slice 1d implementation notes` list before `## Slice 2: modifiers`, covering the shape, the Reveal change and the decisions above.

- [ ] **Step 3: Run the link check and commit**

Run: `python3 scripts/check-markdown-links.py`

```bash
git add docs
git commit -m "docs: record slice 1d

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

## Self-review

- **Spec coverage.** The rulings for R08 Whistle, R03 Brass Horse and R39 Magic Carpet map to Tasks 2, 4 and 3. Each ruling clause has a test: Whistle's cost, target choice, pawn pull, secret hand-over, the no-target case and the unusable cases; Brass Horse's region, public `Reveal` of the top card, one or several matches, the empty pile, the Vision, the different-site rule, unchanged knowledge, the journal wire round trip and the unusable cases; Magic Carpet's free use, same-site skip, both fates, the eligibility rule and the no-eligible case. The Movement rule is satisfied: every relocation is a plain `Move`, and no Travel power can fire.
- **Placeholders.** None. Every step carries its code or command.
- **Types.** `PawnMoves` members are introduced in the task that first uses them and consumed unchanged after. `MovementFixture` members used by a suite are defined in Task 2, except `withDiscard`, `freshDenizen` and `aVision`, which only the Brass Horse suite uses and Task 4 adds. `BrassHorse.id` is defined in the companion and used by the class, the suite and `MovementPowers`.
- **Engine.** Task 1 is the only engine change: three operations files, no power name.
