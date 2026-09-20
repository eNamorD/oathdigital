# Powers Slice 1a: When Played and Simple Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement eight powers, each declared only as a `ContributingPower` (When Played) or a `PhasePower` (ACTION, WAKE) over existing operations: A Small Favor, Faithful Friend, Garrison, Family Heirloom, Wayside Inn, Elders, Magic Waterskin and Marble Fountains.

**Architecture:** A When Played power hooks `PowerWindow.ActionCardPlayed` through a small `WhenPlayedPower` trait that fixes the card match and the window, so each power writes only its effect. A phase power is a `PhasePower` whose `cost` the engine pays (slice 0), so each writes only `build`. Conditional decisions use two walker shapes recorded below. No engine change is needed.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (sections "Slice 1: When Played", "Slice 1: ACTION powers", "Slice 1: WAKE powers"). Slice 1 of the design is split into 1a to 1d, see Task 7.

## Global Constraints

- `BackendArchitectureSuite` applies: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources (a lowercase substring scan); a power imports nothing from `oathdigital.gameplay.walker`; no `copy(advisers =`, `temporaryHands.updated(` or similar direct state writes under `gameplay/powers`.
- A power is declared solely by a `ContributingPower` or `PhasePower`. No engine code is added for it.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the suite with `./sbtw test`. Run one suite with `./sbtw "testOnly <fully.qualified.Suite>"`.
- Test fixtures keep the card inventory whole: a card leaves the place it came from when it is placed, or `CardIndex` fails.

## What planning found

These facts are read from the code and shape the tasks. They are not in the design.

1. **A relic cannot wait in a temporary hand.** `CurrentGameState.temporaryHands` holds `WorldCardId`s only. Family Heirloom's "draw, then take or put on the bottom" therefore draws the relic facedown into the player's play area, asks the choice, and buries it again on "bottom". The owner sees their own facedown relic. No engine change. Other players briefly see a facedown relic appear.
2. **The walker re-derives the tree on every command.** A `Branch.select` and a `Transform` run again on resume against the state stored at the park. Two shapes are therefore safe:
   - *Live decision.* A `Branch` whose `select` returns only a `Decide`, placed after the sibling that changed the state it reads. Between the park and the answer nothing runs, so `select` returns the same decision. Garrison uses it.
   - *Once guard.* `Repeat(guard, body)` where `guard` is "no answer with this decision id yet, and the precondition holds". The guard runs only at pass boundaries, never on a resume inside the body, so a body that changes its own precondition (a draw that empties the deck) is safe. The body must contain the `Decide` with that id, or the loop never ends. Family Heirloom uses it.
   - A `Branch` whose selection is changed by an operation inside the same selected vector is not safe, because the resume selects again against the changed state.
3. **Warband supply is derived, not stored.** The bank holds `banks.warbandSupply(kind) - board warbands - warbands at sites` (14 per Exile lineage). `Gain.Warbands` is optional and reduces to what the bank has.
4. **`GainSupply` clamps** at `SupplyTrack.Maximum` (7) in both the validator and the mutation.
5. **Card play runs the hook for every play.** `CardPlayed(card, source)` at `ActionCardPlayed` is the hook for Search and for a facedown adviser play. Decision ids under the `cardplay.` prefix already map to a prompt continuation in both (`WalkerProcedureRegistry`).
6. **The reviewed registry needs `implemented = true`.** `ActionPowers` lists every When Played card with `implemented = false`, which makes a played card record an "unimplemented" diagnostic. Each implemented When Played power flips its own entry, as Dazzle did.
7. **Phase powers are registered by id, not by card.** `PhasePowerCatalog.default` lists them. The engine finds their sources from the rule-source index by power id, so a phase power needs no card lookup.
8. **`GameApplicationServiceSuite` "powered-playability"** plays a scripted full game and answers only `cardplay.place.*`. If a newly implemented When Played card with its own decision is played in that script, the script parks. Tasks 2 and 3 run the full suite for this reason.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala`: `player` and `forceKind` reads shared by powers.
- Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`: the trait and `cardOf`.
- Create `.../whenplayed/ASmallFavor.scala`, `FaithfulFriend.scala`, `Garrison.scala`, `FamilyHeirloom.scala`.
- Create `src/main/scala/oathdigital/gameplay/powers/action/PaidAction.scala`, `WaysideInn.scala`, `Elders.scala`, `MagicWaterskin.scala`.
- Create `src/main/scala/oathdigital/gameplay/powers/wake/MarbleFountains.scala`.
- Modify `WalkerPowerCatalog.scala`, `PhasePowerCatalog.scala`, `ActionPowers.scala` (flags).
- Create test support `src/test/scala/oathdigital/gameplay/powers/PowerFixture.scala` and `whenplayed/WhenPlayedHarness.scala`.
- Create one suite per power under `src/test/scala/oathdigital/gameplay/powers/{whenplayed,action,wake}/`.

---

### Task 1: The When Played kit, A Small Favor and Faithful Friend

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ASmallFavor.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/FaithfulFriend.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/PowerFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ASmallFavorSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/FaithfulFriendSuite.scala`

**Interfaces:**
- Produces `PlayerFacts.player(ready, actor): Either[OathViolation, PlayerState]` and `PlayerFacts.forceKind(ready, actor): Either[OathViolation, ForceKind]`.
- Produces `trait WhenPlayedPower extends ContributingPower { def cardId: DenizenId; def effect(ctx: PowerCtx): Vector[Operation] }` and `WhenPlayedPower.cardOf(catalog, id): Option[DenizenId]`. Later tasks and slices extend it.
- Produces the test support `PowerFixture` (`base`, `actor`, `player`, `home`, `updateActor`, `withBoard`, `inPhase`, `asAdviser`, `atHome`, `atSite`, `withRelic`, `withEdifice`, `warbandBank`, `leaveInBank`) and `WhenPlayedHarness` (`hook`, `play`, `finished`, `parked`, `recorded`, `replayed`).

- [ ] **Step 1: Write the test support**

`src/test/scala/oathdigital/gameplay/powers/PowerFixture.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

/** Staging shared by the batch-1 power suites. Every method keeps the card
  * inventory whole: a card leaves the place it came from when it is placed.
  */
object PowerFixture {
  val base: ReadyGame = initialReady
  val actor: PlayerId = base.game.current.turn.activePlayer

  def player(ready: ReadyGame, id: PlayerId = actor): PlayerState =
    ready.game.current.players.find(_.player == id).get
  def home(ready: ReadyGame): SiteId = player(ready).pawnSite.get

  def updateActor(ready: ReadyGame)(f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actor) f(p) else p)))

  def withBoard(ready: ReadyGame)(
      f: PlayerBoardState => PlayerBoardState): ReadyGame =
    updateActor(ready)(p => p.copy(board = f(p.board)))

  def inPhase(ready: ReadyGame, phase: Phase): ReadyGame =
    ready.updateCurrent(_.copy(turn = TurnState(actor, phase, Set.empty)))

  private def outOfWorldDeck(ready: ReadyGame, id: DenizenId): ReadyGame = {
    require(ready.game.current.commonCards.worldDeck.contains(id),
      s"${id.value} is not in the world deck")
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == id))))
  }

  def asAdviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    updateActor(outOfWorldDeck(ready, id))(p => p.copy(advisers =
      p.advisers :+ DenizenState(id, orientation, Tokens.empty)))

  def atSite(ready: ReadyGame, id: DenizenId, site: SiteId): ReadyGame =
    outOfWorldDeck(ready, id).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(site, c.map.sites(site).copy(denizens =
        c.map.sites(site).denizens :+
          DenizenState(id, Orientation.FaceUp, Tokens.empty))))))

  def atHome(ready: ReadyGame, id: DenizenId): ReadyGame =
    atSite(ready, id, home(ready))

  /** A faceup relic in the actor's play area, taken from the relic deck or
    * from whichever site holds it.
    */
  def withRelic(ready: ReadyGame, id: RelicId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    ready.updateCurrent { c =>
      val cleared = c.copy(
        commonCards = c.commonCards.copy(
          relicDeck = c.commonCards.relicDeck.filterNot(_ == id)),
        map = c.map.copy(sites = c.map.sites.map { case (site, state) =>
          site -> state.copy(relics = state.relics.filterNot(_.id == id)) }))
      cleared.copy(players = cleared.players.map(p =>
        if (p.player != actor) p
        else p.copy(relics = p.relics :+
          RelicState(id, orientation, Tokens.empty))))
    }

  /** An edifice from the edifice deck, placed at `site` on `side`. */
  def withEdifice(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      site: SiteId): ReadyGame = {
    require(ready.game.current.commonCards.edificeDeck.contains(id),
      s"${id.value} is not in the edifice deck")
    ready.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        edificeDeck = c.commonCards.edificeDeck.filterNot(_ == id)),
      map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(id, side, Tokens.empty))))))
  }

  /** Warbands of `kind` still in the bank: the printed supply less every
    * board and site.
    */
  def warbandBank(ready: ReadyGame, kind: ForceKind): Int = {
    val current = ready.game.current
    val boards = current.players.filter(p =>
      PlayerForceKind.of(ready, p).contains(kind)).map(_.board.warbands).sum
    val sites = current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum
    ready.banks.warbandSupply(kind) - boards - sites
  }

  /** Moves warbands from the bank onto the actor's board until the bank
    * holds `left`.
    */
  def leaveInBank(ready: ReadyGame, kind: ForceKind, left: Int): ReadyGame =
    withBoard(ready)(board => board.copy(
      warbands = board.warbands + warbandBank(ready, kind) - left))
}
```

`src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.PowerFixture.actor
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** Drives a When Played power through its `CardPlayed` hook, the way the
  * Dazzle and Conspiracy suites do.
  */
object WhenPlayedHarness {
  def hook(card: DenizenId): CardPlayed =
    CardPlayed(card, RuleSourceRef.Adviser(actor, card))

  def powers(power: ContributingPower): WalkerPowers =
    WalkerPowers(Vector(power))

  def play(ready: ReadyGame, power: ContributingPower, card: DenizenId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None, powers(power))

  def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  def parked(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Parked =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked]

  def recorded(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    OperationPipeline.run(from, recorded(events),
      OperationPolicy.Permissive)(Right(_)).toOption.get.state
}
```

- [ ] **Step 2: Write the failing A Small Favor suite**

`src/test/scala/oathdigital/gameplay/powers/whenplayed/ASmallFavorSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class ASmallFavorSuite extends munit.FunSuite {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = ASmallFavor.forCatalog(catalog).get
  private val card = power.cardId
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get
  private def staged = leaveInBank(asAdviser(base, card), kind, 6)

  test("A Small Favor is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("playing it gains four warbands") {
    val done = finished(play(staged, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(staged).board.warbands + 4)
    assertEquals(replayed(staged, done.events), done.treeless)
  }

  test("the gain is capped by the warband bank") {
    val short = leaveInBank(asAdviser(base, card), kind, 2)
    val done = finished(play(short, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(short).board.warbands + 2)
    assertEquals(warbandBank(done.treeless, kind), 0)
  }

  test("an empty bank gains nothing and records nothing") {
    val empty = leaveInBank(asAdviser(base, card), kind, 0)
    val done = finished(play(empty, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(empty).board.warbands)
    assertEquals(recorded(done.events), Vector.empty)
  }

  test("another card being played does nothing") {
    val other = DenizenId("1")
    val ready = asAdviser(staged, other)
    val done = finished(play(ready, power, other))
    assertEquals(recorded(done.events), Vector.empty)
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.ASmallFavorSuite"`
Expected: FAIL to compile, `not found: value ASmallFavor`.

- [ ] **Step 4: Write `PlayerFacts` and `WhenPlayedPower`**

`src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** Reads about the acting player that several powers need. */
object PlayerFacts {
  def player(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, PlayerState] =
    ready.game.current.players.find(_.player == actor).toRight(
      OathViolation.WrongPlayer(ready.game.current.turn.activePlayer, actor))

  /** The kind of warband the player's own warbands are. */
  def forceKind(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, ForceKind] =
    player(ready, actor).flatMap(state => PlayerForceKind.of(ready, state)
      .toRight(OathViolation.UnsupportedEconomyState(
        s"no warband kind for lineage ${state.lineage.value}")))
}
```

`src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** A When Played power of one denizen. When that card is played, `effect`'s
  * operations run after whatever other powers contributed at the
  * card-played window.
  *
  * `effect` is called on every fold, and the walker folds again on every
  * command, so it must be a pure function of the state in `ctx`. Anything
  * that depends on state an earlier operation changes belongs inside a
  * `BuildOps`, `Branch` or `Repeat` it returns, which the walker evaluates
  * at walk time.
  */
trait WhenPlayedPower extends ContributingPower {
  def cardId: DenizenId
  def effect(ctx: PowerCtx): Vector[Operation]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  final override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayed(card, _) => card == cardId
    case _ => false
  }

  final def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayed -> Vector(Transform((ctx, children) =>
      children ++ effect(ctx))))
}

object WhenPlayedPower {
  /** The denizen that prints power `id`, or `None` for a catalog without it. */
  def cardOf(catalog: ExecutableCatalog, id: PowerId): Option[DenizenId] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(definition => DenizenId(definition.id.value))
}
```

- [ ] **Step 5: Write A Small Favor and Faithful Friend**

`.../whenplayed/ASmallFavor.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** A Small Favor (card 15), WHEN PLAYED: gain four warbands. The gain is
  * optional, so it is capped by what the warband bank still holds.
  */
final case class ASmallFavor private (cardId: DenizenId)
    extends WhenPlayedPower {
  def id: PowerId = ASmallFavor.id

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(BuildOps((ready, _) =>
    PlayerFacts.forceKind(ready, ctx.activePlayer).map(kind => Vector(
      Gain.Warbands(ctx.activePlayer, kind, ASmallFavor.Warbands)))))
}

object ASmallFavor {
  val id: PowerId = PowerId("denizen.a-small-favor")
  val Warbands: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[ASmallFavor] =
    WhenPlayedPower.cardOf(catalog, id).map(new ASmallFavor(_))
}
```

`.../whenplayed/FaithfulFriend.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Faithful Friend (card 28), WHEN PLAYED: gain 4 Supply. `GainSupply`
  * clamps at the track maximum.
  */
final case class FaithfulFriend private (cardId: DenizenId)
    extends WhenPlayedPower {
  def id: PowerId = FaithfulFriend.id

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(GainSupply(ctx.activePlayer, FaithfulFriend.Supply))
}

object FaithfulFriend {
  val id: PowerId = PowerId("denizen.faithful-friend")
  val Supply: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[FaithfulFriend] =
    WhenPlayedPower.cardOf(catalog, id).map(new FaithfulFriend(_))
}
```

- [ ] **Step 6: Write the Faithful Friend suite**

`src/test/scala/oathdigital/gameplay/powers/whenplayed/FaithfulFriendSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class FaithfulFriendSuite extends munit.FunSuite {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = FaithfulFriend.forCatalog(catalog).get
  private val card = power.cardId
  private def withSupply(amount: Int) = withBoard(asAdviser(base, card))(
    _.copy(supply = SupplyTrack(amount)))

  test("Faithful Friend is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("playing it gains 4 Supply") {
    val ready = withSupply(2)
    val done = finished(play(ready, power, card))
    assertEquals(player(done.treeless).board.supply, SupplyTrack(6))
    assertEquals(replayed(ready, done.events), done.treeless)
  }

  test("the gain is clamped at the track maximum") {
    val done = finished(play(withSupply(5), power, card))
    assertEquals(player(done.treeless).board.supply, SupplyTrack(7))
  }

  test("another card being played does nothing") {
    val other = DenizenId("1")
    val ready = asAdviser(withSupply(2), other)
    assertEquals(recorded(finished(play(ready, power, other)).events),
      Vector.empty)
  }
}
```

- [ ] **Step 7: Register both powers and flip their reviewed flags**

`WalkerPowerCatalog.scala`: add the import and extend `default`:

```scala
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, ConspiracyWhenPlayed, Dazzle, FaithfulFriend}
```
```scala
      SilverTongue.forCatalog(catalog) ++
      ASmallFavor.forCatalog(catalog).toVector ++
      FaithfulFriend.forCatalog(catalog).toVector ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```
(`Vector ++ Option` adds the option's element; keep the file's existing `++` chain and put the new lines before `Dazzle.forCatalog(catalog)`.)

`ActionPowers.scala`: add beside `played`:

```scala
  private def playedDone = Vector(ReviewedHandler.automatic(
    PowerWindow.ActionCardPlayed, implemented = true))
```
and change the two entries:

```scala
  object ASmallFavor extends ReviewedPower("denizen.a-small-favor", None, playedDone)
  object FaithfulFriend extends ReviewedPower("denizen.faithful-friend", None, playedDone)
```

- [ ] **Step 8: Run the suites, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. If `the gain is capped` fails because `Gain.Warbands` did not reduce, read `OperationResolution.scala`: the optional counted `Move` inside a `Gain` must shrink. Record the finding and adjust the fixture, not the power.

Run: `./sbtw test`
Expected: all pass. If `powered-playability` fails, see "What planning found" item 8.

- [ ] **Step 9: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement A Small Favor and Faithful Friend

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Garrison

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Garrison.scala`
- Modify: `WalkerPowerCatalog.scala`, `ActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/GarrisonSuite.scala`

**Interfaces:**
- Consumes `WhenPlayedPower`, `PlayerFacts`, `PowerAccess.ruledSites` (`private[gameplay]`, reachable from `gameplay.powers`).
- Produces `Garrison.decisionId = "cardplay.garrison.sites"`.

Ruling: count the sites you rule once, when played. Gain that many warbands (capped by the bank). Then put one warband from your board on each ruled site. If the board is short, the player chooses which sites receive one. Otherwise no decision is asked.

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.model._

class GarrisonSuite extends munit.FunSuite with WalkerRecordedOpsReducer {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = Garrison.forCatalog(catalog).get
  private val card = power.cardId
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The actor rules exactly `counts.size` sites, holding `counts` warbands
    * each. Every other site is emptied. Returns the state and the sites in
    * value order.
    */
  private def ruling(counts: Vector[Int], onBoard: Int)
      : (ReadyGame, Vector[SiteId]) = {
    val current = base.game.current
    val sites = current.map.inPlay.toVector.sortBy(_.value).take(counts.size)
    val cleared = current.map.sites.map { case (id, site) =>
      id -> site.copy(forces = SiteForces.Empty) }
    val ruled = sites.zip(counts).foldLeft(cleared) { case (all, (id, n)) =>
      all.updated(id, all(id).copy(forces = SiteForces.Occupied(kind, n))) }
    val staged = base.updateCurrent(_.copy(map = current.map.copy(sites = ruled)))
    (withBoard(asAdviser(staged, card))(_.copy(warbands = onBoard)), sites)
  }

  private def forcesAt(ready: ReadyGame, site: SiteId): SiteForces =
    ready.game.current.map.sites(site).forces

  test("Garrison is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("it gains one warband per ruled site and puts one on each") {
    val (ready, sites) = ruling(Vector(1, 1, 1), onBoard = 5)
    val done = finished(play(ready, power, card))
    sites.foreach(site => assertEquals(forcesAt(done.treeless, site),
      SiteForces.Occupied(kind, 2)))
    assertEquals(player(done.treeless).board.warbands, 5)
    assertEquals(replayed(ready, done.events), done.treeless)
  }

  test("a player who rules no site gains and places nothing") {
    val (ready, _) = ruling(Vector.empty, onBoard = 5)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(player(done.treeless).board.warbands, 5)
  }

  test("a short bank leaves a short board, and the player chooses the sites") {
    // 13 warbands sit at the three ruled sites and none on the board, so the
    // bank holds one and the gain is capped at one.
    val (ready, sites) = ruling(Vector(4, 4, 5), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 1)
    val first = parked(play(ready, power, card))
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    assertEquals(player(atChoice).board.warbands, 1)
    val decide = ProcedureWalker.parkedDecide(atChoice, hook(card), first.tree,
      powers(power)).get
    assertEquals(decide.decisionId, Garrison.decisionId)
    assertEquals(decide.owner, actor)
    assertEquals(decide.query, DecisionQuery.ChooseMany(1, 1, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))),
      decide.query.heading))

    val chosen = sites(1)
    val done = finished(ProcedureWalker.resolve(atChoice, hook(card),
      first.tree, Answered(Garrison.decisionId, DecisionAnswer.ChooseManyAnswer(
        Vector(DecisionOptionRef.Site(chosen))), actor), powers(power)))
    assertEquals(forcesAt(done.treeless, chosen), SiteForces.Occupied(kind, 6))
    assertEquals(forcesAt(done.treeless, sites.head), SiteForces.Occupied(kind, 4))
    assertEquals(forcesAt(done.treeless, sites(2)), SiteForces.Occupied(kind, 5))
    assertEquals(player(done.treeless).board.warbands, 0)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
  }

  test("an empty board and an empty bank ask nothing") {
    val (ready, _) = ruling(Vector(5, 5, 4), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 0)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
  }

  test("a choice naming an unruled site is rejected") {
    val (ready, sites) = ruling(Vector(4, 4, 5), onBoard = 0)
    val first = parked(play(ready, power, card))
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    val unruled = base.game.current.map.inPlay.toVector
      .find(!sites.contains(_)).get
    assert(ProcedureWalker.resolve(atChoice, hook(card), first.tree,
      Answered(Garrison.decisionId, DecisionAnswer.ChooseManyAnswer(
        Vector(DecisionOptionRef.Site(unruled))), actor), powers(power)).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.GarrisonSuite"`
Expected: FAIL to compile, `not found: value Garrison`.

- [ ] **Step 3: Write Garrison**

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Garrison (card 7), WHEN PLAYED: gain one warband per site you rule, and
  * put one warband from your board on each site you rule.
  *
  * Three siblings run in order. The gain is optional and capped by the bank.
  * The decision is a live `Branch` that exists only when the board holds
  * fewer warbands than there are ruled sites, read after the gain ran (the
  * walker re-selects it against the same stored state on resume). The
  * placement puts a warband on every ruled site, or on the chosen ones.
  */
final case class Garrison private (cardId: DenizenId) extends WhenPlayedPower {
  import Garrison._
  def id: PowerId = Garrison.id

  def effect(ctx: PowerCtx): Vector[Operation] = {
    val actor = ctx.activePlayer
    Vector(
      BuildOps((ready, _) => gain(ready, actor)),
      Branch((ready, _) => ask(ready, actor)),
      BuildOps((ready, pending) => place(ready, actor, pending)))
  }
}

object Garrison {
  val id: PowerId = PowerId("denizen.garrison")
  val decisionId: String = "cardplay.garrison.sites"

  def forCatalog(catalog: ExecutableCatalog): Option[Garrison] =
    WhenPlayedPower.cardOf(catalog, id).map(new Garrison(_))

  private def ruled(ready: ReadyGame, actor: PlayerId): Vector[SiteId] =
    PowerAccess.ruledSites(ready, actor).toVector.sortBy(_.value)

  private def gain(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val count = ruled(ready, actor).size
    if (count == 0) Right(Vector.empty)
    else PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, count)))
  }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val sites = ruled(ready, actor)
    val held = PlayerFacts.player(ready, actor).map(_.board.warbands)
      .getOrElse(0)
    if (held == 0 || held >= sites.size) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseMany(held, held,
      sites.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Garrison: choose the sites that each receive a warband"))))
  }

  private def place(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val sites = ruled(ready, actor)
    for {
      held <- PlayerFacts.player(ready, actor).map(_.board.warbands)
      kind <- PlayerFacts.forceKind(ready, actor)
      chosen <-
        if (held >= sites.size) Right(sites)
        else if (held == 0) Right(Vector.empty[SiteId])
        else answered(pending)
    } yield chosen.map(site => Move(Piece.Warbands(kind, 1),
      PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.Site(site))))
  }

  private def answered(pending: PendingTree)
      : Either[OathViolation, Vector[SiteId]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseManyAnswer(chosen), _) =>
        chosen.collect { case DecisionOptionRef.Site(site) => site }
    }.toRight(OathViolation.InvalidEventOrder(
      "no Garrison site choice is recorded"))
}
```

`ChooseManyAnswer` selections that are not among the offered options are rejected by the generic answer validation, which is what the last test pins.

- [ ] **Step 4: Register and flip the flag**

`WalkerPowerCatalog.scala`: add `Garrison` to the whenplayed import and `Garrison.forCatalog(catalog).toVector ++` beside the others. `ActionPowers.scala`: `object Garrison extends ReviewedPower("denizen.garrison", None, playedDone)`.

- [ ] **Step 5: Run the suite, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.GarrisonSuite"` then `./sbtw test`
Expected: PASS. If the short-bank test cannot reach a bank of 1 because `initialReady` seats another player of the same force kind, print `warbandBank(ready, kind)` in the failing assertion and choose counts that make it 1. Do not change the power.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Garrison

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Family Heirloom

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/FamilyHeirloom.scala`
- Modify: `WalkerPowerCatalog.scala`, `ActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/FamilyHeirloomSuite.scala`

**Interfaces:**
- Produces `FamilyHeirloom.decisionId = "cardplay.family-heirloom.keep"`, `FamilyHeirloom.keep` and `FamilyHeirloom.bottom` (`DecisionOptionRef.Button`).

Ruling: draw a relic, only you see it. Choose to take it facedown, or put it on the bottom of the relic deck. An empty relic deck does nothing.

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.model._

class FamilyHeirloomSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = FamilyHeirloom.forCatalog(catalog).get
  private val card = power.cardId
  private val staged = asAdviser(base, card)
  private val top = staged.game.current.commonCards.relicDeck.head

  private def answer(ready: ReadyGame, tree: PendingTree, ref: DecisionOptionRef) =
    ProcedureWalker.resolve(ready, hook(card), tree, Answered(
      FamilyHeirloom.decisionId, DecisionAnswer.ChooseOneAnswer(ref), actor),
      powers(power))

  private def atChoice = {
    val first = parked(play(staged, power, card))
    (first, foldRecordedOps(staged, first.events, "the draw did not replay"))
  }

  test("Family Heirloom is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("the relic is drawn facedown to the player and the choice is theirs") {
    val (first, state) = atChoice
    assert(player(state).relics.exists(relic =>
      relic.id == top && relic.orientation == Orientation.FaceDown))
    assert(!state.game.current.commonCards.relicDeck.contains(top))
    val decide = ProcedureWalker.parkedDecide(state, hook(card), first.tree,
      powers(power)).get
    assertEquals(decide.decisionId, FamilyHeirloom.decisionId)
    assertEquals(decide.owner, actor)
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector(FamilyHeirloom.keep, FamilyHeirloom.bottom))
  }

  test("taking it keeps the relic") {
    val (first, state) = atChoice
    val done = finished(answer(state, first.tree, FamilyHeirloom.keep))
    assert(player(done.treeless).relics.exists(_.id == top))
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
  }

  test("putting it on the bottom returns it to the relic deck") {
    val (first, state) = atChoice
    val done = finished(answer(state, first.tree, FamilyHeirloom.bottom))
    assert(!player(done.treeless).relics.exists(_.id == top))
    assertEquals(done.treeless.game.current.commonCards.relicDeck.last, top)
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
  }

  test("an empty relic deck does nothing and asks nothing") {
    val current = staged.game.current
    val emptied = staged.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = finished(play(emptied, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(player(done.treeless).relics, player(emptied).relics)
  }

  test("a choice that is not offered is rejected") {
    val (first, state) = atChoice
    assert(answer(state, first.tree, DecisionOptionRef.Button("elsewhere")).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.FamilyHeirloomSuite"`
Expected: FAIL to compile, `not found: value FamilyHeirloom`.

- [ ] **Step 3: Write Family Heirloom**

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Family Heirloom (card 133), WHEN PLAYED: draw a relic. Take it or put it
  * on the bottom of the relic deck.
  *
  * A relic cannot wait in a temporary hand (`temporaryHands` holds world
  * cards), so the draw puts it facedown in the player's play area, where
  * only they can identify it, and "bottom" buries it again. The three steps
  * sit in a once-guarded `Repeat`: the guard runs only at pass boundaries,
  * so the draw emptying the relic deck cannot make the walker lose the
  * parked decision on resume.
  */
final case class FamilyHeirloom private (cardId: DenizenId)
    extends WhenPlayedPower {
  import FamilyHeirloom._
  def id: PowerId = FamilyHeirloom.id

  def effect(ctx: PowerCtx): Vector[Operation] = {
    val actor = ctx.activePlayer
    Vector(Repeat(
      (ready, pending) => !asked(pending) &&
        ready.game.current.commonCards.relicDeck.nonEmpty,
      Sequence(Vector[Operation](
        BuildOps((ready, _) => draw(ready, actor)),
        Decide(decisionId, actor, DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(keep, "Take the relic"),
          DecisionOption.Button(bottom,
            "Put it on the bottom of the relic deck")),
          heading = Some("Family Heirloom: take the relic you drew, or put it " +
            "on the bottom of the relic deck"))),
        BuildOps((ready, pending) => settle(ready, actor, pending))))))
  }
}

object FamilyHeirloom {
  val id: PowerId = PowerId("denizen.family-heirloom")
  val decisionId: String = "cardplay.family-heirloom.keep"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  val bottom: DecisionOptionRef.Button = DecisionOptionRef.Button("bottom")

  def forCatalog(catalog: ExecutableCatalog): Option[FamilyHeirloom] =
    WhenPlayedPower.cardOf(catalog, id).map(new FamilyHeirloom(_))

  private def asked(pending: PendingTree): Boolean =
    pending.answered.exists(_.decisionId == decisionId)

  private def draw(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable("relic deck is empty")).map(relic =>
      Vector(Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown)))

  private def settle(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match {
      case Some(`bottom`) => for {
        held <- PlayerFacts.player(ready, actor)
        drawn <- held.relics.lastOption.toRight(OathViolation.InvalidEventOrder(
          "no drawn relic to put back"))
      } yield Vector(Bury(BuryableCard.Relic(drawn.id),
        PositionedLocation(Location.PlayArea(actor))))
      case Some(`keep`) => Right(Vector.empty)
      case _ => Left(OathViolation.InvalidEventOrder(
        "no Family Heirloom choice is recorded"))
    }
}
```

- [ ] **Step 4: Register and flip the flag**

`WalkerPowerCatalog.scala`: add `FamilyHeirloom` to the import and `FamilyHeirloom.forCatalog(catalog).toVector ++`. `ActionPowers.scala`: `object FamilyHeirloom extends ReviewedPower("denizen.family-heirloom", None, playedDone)`.

- [ ] **Step 5: Run the suite, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.FamilyHeirloomSuite"` then `./sbtw test`
Expected: PASS. If the once guard or a resume misbehaves (`Parked` where `Finished` was expected, or a misaddressed park), read `walkRepeat` in `ProcedureWalker.scala` and re-check "What planning found" item 2 before changing the power. If a fold's `answered` does not include the answer at the guard, the guard needs `pending.answered` from the pass context; report it rather than patching the walker.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Family Heirloom

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: The ACTION kit, Wayside Inn and Elders

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/PaidAction.scala`
- Create: `.../action/WaysideInn.scala`, `.../action/Elders.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WaysideInnSuite.scala`, `ElderSuite.scala`

**Interfaces:**
- Produces `abstract class PaidAction(idValue: String, override val cost: Cost) extends PhasePower`, an Act-timed power gated only by its cost. Later slices extend it.
- Consumes the slice 0 engine: `PhasePower.cost`, the empty-card rule, `usedPowers` untouched for Act.

Rulings: Wayside Inn costs 1 favor placed and gains 2 Supply. Elders costs 2 favor placed and gains 1 secret from the shared bank.

- [ ] **Step 1: Write the failing Wayside Inn suite**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class WaysideInnSuite extends munit.FunSuite {
  import PowerFixture._

  private val inn = DenizenId("47")
  private val source = DecisionOptionRef.Denizen(inn)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(favor: Int, supply: Int) = inPhase(withBoard(
    atHome(base, inn))(_.copy(favor = favor, supply = SupplyTrack(supply))),
    Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(WaysideInn.id), actor, Vector.empty, Vector(source))
  private def after(state: OathState) = state.asInstanceOf[Ready].value
  private def innCard(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == inn => d }.get

  test("Wayside Inn is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(WaysideInn.id).isDefined)
  }

  test("it places 1 favor on its card and gains 2 Supply") {
    val used = after(use(staged(favor = 3, supply = 2)).toOption.get.state)
    assertEquals(player(used).board.favor, 2)
    assertEquals(player(used).board.supply, SupplyTrack(4))
    assertEquals(innCard(used).tokens, Tokens(1, 0))
    assertEquals(used.game.current.turn.usedPowers, Set.empty[PowerUseRef])
  }

  test("the gain is clamped at the track maximum") {
    val used = after(use(staged(favor = 1, supply = 6)).toOption.get.state)
    assertEquals(player(used).board.supply, SupplyTrack(7))
  }

  test("it is unusable without favor to place") {
    val broke = staged(favor = 0, supply = 2)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(broke).isLeft)
  }

  test("it is unusable again while its card holds the favor") {
    val used = after(use(staged(favor = 3, supply = 2)).toOption.get.state)
    assertEquals(PhasePowerProcedure.usable(catalog, used, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(used).isLeft)
  }

  test("it is usable in the Act phase only") {
    val wake = inPhase(staged(favor = 3, supply = 2), Phase.Wake)
    assertEquals(PhasePowerProcedure.usable(catalog, wake, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WaysideInnSuite"`
Expected: FAIL to compile, `not found: value WaysideInn`.

- [ ] **Step 3: Write `PaidAction`, Wayside Inn and Elders**

`PaidAction.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** An ACTION phase power gated only by its cost. The engine pays `cost`
  * onto the power's source card and reads "payable, including the
  * empty-card rule" as its usability, so a subclass writes only `build`.
  */
abstract class PaidAction(idValue: String, override val cost: Cost)
    extends PhasePower {
  final val id: PowerId = PowerId(idValue)
  final def timing: PowerTiming = PowerTiming.Act
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true
}
```

`WaysideInn.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Wayside Inn (card 47), ACTION: place 1 favor on this card, then gain
  * 2 Supply.
  */
case object WaysideInn extends PaidAction("denizen.wayside-inn",
    Cost(favor = 1)) {
  val Supply: Int = 2

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(GainSupply(player, Supply))
}
```

`Elders.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Elders (card 26), ACTION: place 2 favor on this card, then gain 1 secret
  * from the shared bank, which holds an unlimited supply.
  */
case object Elders extends PaidAction("denizen.elders", Cost(favor = 2)) {
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Gain.Secrets(player, 1))
}
```

`PhasePowerCatalog.scala`:

```scala
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.powers.action.{Elders, WaysideInn}
```
```scala
  def default(catalog: ExecutableCatalog): PhasePowers =
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      Vector[PhasePower](WaysideInn, Elders))
```

- [ ] **Step 4: Write the Elders suite**

`ElderSuite.scala` mirrors Wayside Inn. Complete file:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ElderSuite extends munit.FunSuite {
  import PowerFixture._

  private val elders = DenizenId("26")
  private val source = DecisionOptionRef.Denizen(elders)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(favor: Int) = inPhase(withBoard(atHome(base, elders))(
    _.copy(favor = favor)), Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Elders.id), actor, Vector.empty, Vector(source))
  private def secrets(ready: ReadyGame) =
    player(ready).board.faceUpSecrets + player(ready).board.faceDownSecrets

  test("Elders is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Elders.id).isDefined)
  }

  test("it places 2 favor on its card and gains a secret") {
    val ready = staged(favor = 3)
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.favor, 1)
    assertEquals(secrets(used), secrets(ready) + 1)
    assertEquals(used.game.current.map.sites(home(used)).denizens.collectFirst {
      case d: DenizenState if d.id == elders => d.tokens }.get, Tokens(2, 0))
  }

  test("one favor is not enough") {
    val broke = staged(favor = 1)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(broke).isLeft)
  }
}
```

- [ ] **Step 5: Run the action suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*"`
Expected: PASS. If a denizen id is not in the world deck, `PowerFixture` fails fast with its message; pick another fixture site, not another card.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Wayside Inn and Elders

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Magic Waterskin

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MagicWaterskin.scala`
- Modify: `PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MagicWaterskinSuite.scala`

Ruling: the relic must be faceup in your play area. Bury it first, with the standard returns, then gain 4 Supply. No cost.

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class MagicWaterskinSuite extends munit.FunSuite {
  import PowerFixture._

  private val skin = RelicId("R45")
  private val source = DecisionOptionRef.Relic(skin)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(orientation: Orientation = Orientation.FaceUp,
      supply: Int = 1) = inPhase(withBoard(withRelic(base, skin, orientation))(
    _.copy(supply = SupplyTrack(supply))), Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MagicWaterskin.id), actor, Vector.empty, Vector(source))

  test("Magic Waterskin is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MagicWaterskin.id).isDefined)
  }

  test("it buries itself at the bottom of the relic deck and gains 4 Supply") {
    val used = use(staged()).toOption.get.state.asInstanceOf[Ready].value
    assert(!player(used).relics.exists(_.id == skin))
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)
    assertEquals(player(used).board.supply, SupplyTrack(5))
  }

  test("a secret on the relic returns to its holder facedown") {
    val ready = staged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else p.copy(relics = p.relics.map(r =>
        r.copy(tokens = Tokens(0, 1)))))))
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)
  }

  test("the gain is clamped at the track maximum") {
    val used = use(staged(supply = 5)).toOption.get.state
      .asInstanceOf[Ready].value
    assertEquals(player(used).board.supply, SupplyTrack(7))
  }

  test("a facedown relic cannot be used") {
    val facedown = staged(Orientation.FaceDown)
    assertEquals(PhasePowerProcedure.usable(catalog, facedown, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(facedown).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MagicWaterskinSuite"`
Expected: FAIL to compile, `not found: value MagicWaterskin`.

- [ ] **Step 3: Write Magic Waterskin**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Magic Waterskin (relic R45), ACTION: bury this relic, then gain 4 Supply.
  * The bury returns any secrets on the relic to its holder facedown before
  * the relic enters the deck. Only a faceup relic in the holder's play area
  * is a source, so the engine's access rule supplies the "faceup" condition.
  */
case object MagicWaterskin extends PaidAction("relic.magic-waterskin",
    Cost.free) {
  val Supply: Int = 4

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(id) => for {
      held <- PlayerFacts.player(ready, player)
      relic <- held.relics.find(_.id == id).toRight(
        OathViolation.InvalidEventOrder(s"${id.value} is not held by ${player.value}"))
    } yield Sequence(Bury.standard(BuryableCard.Relic(id),
      PositionedLocation(Location.PlayArea(player)), None, 0,
      relic.tokens.secrets, player) :+ GainSupply(player, Supply))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }
}
```

`PhasePowerCatalog.scala`: import `MagicWaterskin` and add it to the `Vector[PhasePower](WaysideInn, Elders, MagicWaterskin)`.

- [ ] **Step 4: Run the action suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*"`
Expected: PASS. `Cost.free` is a constructor argument of `PaidAction`, which is the same value `PhasePower` defaults to, so the engine pays nothing.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Magic Waterskin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Marble Fountains

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/MarbleFountains.scala`
- Modify: `PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/wake/MarbleFountainsSuite.scala`

Ruling: Wake. If your pawn is at this site, refresh Supply to the leftmost space (7). Once per turn. Both faces of an edifice are sources, but only the intact face carries this power id, so a ruined Marble Fountains offers nothing.

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{PhasePowerCatalog, PlayerFacts, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class MarbleFountainsSuite extends munit.FunSuite {
  import PowerFixture._

  private val fountains = EdificeId("E15")
  private val source = DecisionOptionRef.Edifice(fountains)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The edifice is at the pawn's site, or at a far site the actor rules, so
    * that in the second case only the pawn condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Intact,
      pawnAtEdifice: Boolean = true) = {
    val site = home(base)
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != site).get
    val placed = withEdifice(base, fountains, side,
      if (pawnAtEdifice) site else far)
    val ruled = if (pawnAtEdifice) placed else placed.updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1))))))
    inPhase(withBoard(ruled)(_.copy(supply = SupplyTrack(1))), Phase.Wake)
  }
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MarbleFountains.id), actor, Vector.empty, Vector(source))

  test("Marble Fountains is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MarbleFountains.id).isDefined)
  }

  test("it refreshes Supply to the maximum when the pawn is at the site") {
    val used = use(staged()).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.supply, SupplyTrack(7))
  }

  test("it is once per turn") {
    val first = use(staged()).toOption.get.state
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(fountains),
      MarbleFountains.id)
    assert(first.asInstanceOf[Ready].value.game.current.turn.usedPowers
      .contains(used))
    assertEquals(use(first.asInstanceOf[Ready].value).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
  }

  test("it is unusable when the pawn is at another site") {
    val away = staged(pawnAtEdifice = false)
    assert(PowerAccess.locate(away, actor, fountains).isDefined,
      "the edifice must be reachable, or this test proves nothing")
    assertEquals(PhasePowerProcedure.usable(catalog, away, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(away).isLeft)
  }

  test("a ruined Marble Fountains offers nothing") {
    val ruined = staged(EdificeSide.Ruined)
    assertEquals(PhasePowerProcedure.usable(catalog, ruined, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.MarbleFountainsSuite"`
Expected: FAIL to compile, `not found: value MarbleFountains`.

- [ ] **Step 3: Write Marble Fountains**

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** Marble Fountains (edifice E15, intact), WAKE: if your pawn is at this
  * site, refresh your Supply to the leftmost space. `GainSupply` clamps at
  * the track maximum, so gaining the maximum refreshes it. Wake powers are
  * once per turn, which the engine enforces.
  */
case object MarbleFountains extends PhasePower {
  val id: PowerId = PowerId("edifice.e15.intact")
  def timing: PowerTiming = PowerTiming.Wake

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = source match {
    case DecisionOptionRef.Edifice(edifice) =>
      PowerAccess.pawnSite(ready, player).flatMap(ready.game.current.map.sites.get)
        .exists(_.denizens.exists {
          case card: EdificeState => card.id == edifice
          case _ => false
        })
    case _ => false
  }

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(GainSupply(player, SupplyTrack.Maximum))
}
```

`PhasePowerCatalog.scala`: import `oathdigital.gameplay.powers.wake.MarbleFountains` and add it to the vector.

- [ ] **Step 4: Run the wake suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.*"`
Expected: PASS. If `E15` is not in the edifice deck (`PowerFixture.withEdifice` fails fast), it was placed at a site by setup. Take it from that site in the fixture instead, keeping the inventory whole.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Marble Fountains

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Gates and documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md`
- Modify: `docs/superpowers/specs/2026-09-20-powers-rulings.md`

- [ ] **Step 1: Run every gate**

Run: `./sbtw test`, then `scripts/check-architecture.py`, then `scripts/check-markdown-links.py`.
Expected: all pass. `BackendArchitectureSuite` scans power names against `gameplay/walker` and `gameplay/operations`. If a name collides, rename the power object rather than loosening the scan. Confirm no production file exceeds 800 lines.

- [ ] **Step 2: Update the design**

In `2026-09-20-powers-design.md`, change the status line to say slice 1a is implemented (the split of slice 1 into 1a to 1d is already in the Slicing table). Add a short section "Walker shapes for powers" before "Testing" recording the two shapes from "What planning found" item 2 and the Family Heirloom relic-in-play-area decision from item 1.

- [ ] **Step 3: Record results in the rulings appendix**

Under "Slice 1: When Played", "Slice 1: ACTION powers" and "Slice 1: WAKE powers", add `Implemented (slice 1a)` beside each of the eight cards, plus a "Slice 1a implementation notes" list: Family Heirloom holds the drawn relic facedown in the play area during the choice; Garrison's decision appears only when the bank was too short to fill the board; any behaviour the suites found that differs from the ruling text.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: record slice 1a and split slice 1

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

## Self-review

- **Spec coverage.** Rulings for A Small Favor, Faithful Friend, Garrison, Family Heirloom, Wayside Inn, Elders, Magic Waterskin and Marble Fountains map to Tasks 1 to 6. Horned Mask, the other slice 1 WAKE power, moves to slice 1c with the other target-and-information powers.
- **Placeholders.** None.
- **Types.** `WhenPlayedPower.cardId: DenizenId` and `effect(ctx): Vector[Operation]` are used unchanged in Tasks 1 to 3. `PaidAction` is defined in Task 4 and used in Task 5. `PowerFixture` members used later (`withRelic`, `withEdifice`, `warbandBank`, `leaveInBank`) are defined in Task 1.
