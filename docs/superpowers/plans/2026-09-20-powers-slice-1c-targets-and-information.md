# Powers Slice 1c: Targets and Information Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement six powers, each declared only as a `PhasePower` over existing operations: Alchemist, Wolves, Sleight of Hand, Crystal Vial and Ivory Eye (ACTION), and Horned Mask (WAKE).

**Architecture:** Every power extends `PaidAction` (ACTION, from slice 1a) or `PhasePower` directly (Horned Mask, WAKE). The engine pays the cost onto the power's source card and records the once-per-turn use for a Wake power, so each power writes only `build`. Every power asks a decision, so each `build` returns a `Sequence` of a live `Branch` (the decision, present only when there is something to choose) and a `BuildOps` (the effect, read from live state and the recorded answer). No engine change is needed.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (sections "Slice 1: ACTION powers" and "Slice 1: WAKE powers", including "Slice 1a implementation notes"). Builds on the [slice 1a plan](2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), which introduced `PaidAction` and `PowerFixture`.

## Global Constraints

- `BackendArchitectureSuite` applies: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources (a lowercase substring scan of every class that `extends PhasePower` or `ContributingPower`); a power imports nothing from `oathdigital.gameplay.walker`; no direct state writes (`copy(advisers =`, `temporaryHands.updated(` and similar) under `gameplay/powers`.
- A power is declared solely by a `PhasePower`. No engine code is added for it, and no engine file is edited.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the suite with `./sbtw test`. Run one suite with `./sbtw "testOnly <fully.qualified.Suite>"`.
- Test fixtures keep the card inventory whole: a card leaves the place it came from when it is placed, or `CardIndex` fails.
- Other slices (1b, 1d, 2 to 4) are planned in parallel. Shared files (`PhasePowerCatalog.scala`, the design "Slicing" row, the rulings rows) are edited minimally, each addition on its own line, to ease merging. Slice-1c test helpers live in their own file (`TargetsFixture`), not in the shared `PowerFixture`.

## What planning found

These facts are read from the code and shape the tasks. They are not in the design.

1. **`build` runs twice against different states.** `PhasePowerProcedure.build` runs at the start (before the cost is paid), and `rebuild` runs on every resume against the state stored at the park (after the cost is paid). The tree is `Sequence(PayCost, tree, RecordPowerUse)`. A decision built from `ready` inside `build` is therefore derived twice. A decision whose options depend on state is a live `Branch` (the shape recorded in the design, "Walker shapes for powers"), and the effect is a `BuildOps` that reads live state and the recorded answer. Wolves alone builds a plain `Decide`, because its options are the players, which no payment changes.
2. **A one-secret `Take` from a mixed board is ambiguous.** `OperationSecretPlanner` rejects moving one secret from a board that holds both faceup and facedown secrets with `AmbiguousSecretOrientation` (`OperationExecutorSuite`, "secret moves preserve orientation and reject ambiguous mixed sources"). Sleight of Hand ("faceup first, arrives with the same orientation") therefore emits, for a mixed target, `FlipSecrets` of the target's facedown secrets up, the `Take`, and `FlipSecrets` back down. The three run as one atomic batch and net to exactly "one faceup secret moves". A target with only one orientation needs a bare `Take`.
3. **A decision option that names an unidentifiable card suppresses the whole decision.** `WalkerDecisionProjector.card` drops an option for a card the viewer may not identify, and then drops the decision. A facedown adviser of another player is such a card, and `DecisionOptionRef` has an identity-free slot reference only for relics (`RelicSlot`). Ivory Eye therefore offers `Button` options keyed `adviser:<owner>:<slot>` with an authored label. This needs no engine change. A dedicated `AdviserSlot` reference would be one (model, codec, projector, frontend), and is listed as an open question.
4. **A `Peek` is the whole disclosure.** Replaying a recorded `Peek(viewer, card, PlayArea(owner))` adds the card to `ready.knowledge.advisers` for the viewer, and `GamePresentationProjector.identifiesCard` then names that facedown adviser to the viewer and to nobody else (`NegotiationDeal` already relies on this). There is no player-visible action log in the code (it is a ROADMAP item), so the ruling's "log line saying who peeked at whose adviser" cannot be produced by a power. The raw event history carries the `Peek` operation, and its route warns that it can reveal hidden outcomes. This answers the design's "How the journal surfaces a `Peek` to its viewer" check.
5. **`DecisionQueries.wellFormed` reproduces the Alchemist no-decision cases.** A `Distribute` needs at least two variable slots and maximums that differ from its total. With banks that hold at most 4 favor in all, or with one non-empty bank, the query is malformed, which is exactly the ruling's "no decision is asked". With two or more non-empty banks and more than 4 in all, the slot maximums `min(stock, 4)` sum to at least 5, so the query is well formed.
6. **No locked denizen can be at a site.** Every catalog denizen marked `locked` is also `adviser-only`. The ruling's "locked ones are decided by the `Take` restrictions" is therefore moot for Horned Mask, and no `Take` restriction exists in the code.
7. **`Bury.standard` needs a suit and a phase power has no catalog.** `Bury.standard(card, from, suit, favor, secrets, actor)` rejects a favor return without a suit. Crystal Vial and Horned Mask hold the `ExecutableCatalog` in their constructor, like `SilverTongue`, and `PhasePowerCatalog.default(catalog)` builds them. Horned Mask needs the returns half of `Bury.standard` without the bury: it filters the `Bury` out of the vector, which needs no edit to `Discard.returns` (`private[model]`).
8. **The discard destination is duplicated on purpose.** `CardPlay.nextRegion` is private and slice 2 (E6) rewrites card play. Horned Mask's adviser discard repeats the three-case `next` (Cradle to Provinces to Hinterland to Cradle) with a comment. Slice 2 may fold the two together.
9. **A cost is affordable from faceup secrets only** (`Costs.plan` reads `faceUpSecrets`), and a placed cost needs an empty card. A relic carries its own cost, so Ivory Eye and Crystal Vial are limited to once until the relic is emptied.
10. **No reviewed-registry flag is needed.** `ActionPowers` lists When Played cards only, and `WakePowers.powers` is empty. ACTION and WAKE powers register only in `PhasePowerCatalog`.
11. **Test staging.** `initialReady` seats three players (`p1`, `p2`, `p3`; the active player is read as `PowerFixture.actor`). Each board holds 1 favor, 1 faceup secret and 3 warbands, and each player holds one facedown adviser. The board has no site denizens except the homeland edifices. The first game deals only some denizens, so cards 9, 17, 26, 39 and 47 may be absent from the world deck. `PowerFixture.atHome` and `asAdviser` add them when absent. The five vision ids are in the world deck (`FirstGameRulesData.visions`), and `E15` is in the edifice deck.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`: reads of a power's recorded answers from a `PendingTree`.
- Create `src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala`, `Alchemist.scala`, `SleightOfHand.scala`, `CrystalVial.scala`, `IvoryEye.scala`.
- Create `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`.
- Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` (one added line per task).
- Create test support `src/test/scala/oathdigital/gameplay/powers/TargetsFixture.scala`.
- Create one suite per power: `.../powers/action/{Wolves,Alchemist,SleightOfHand,CrystalVial,IvoryEye}Suite.scala` and `.../powers/wake/HornedMaskSuite.scala`.

---

### Task 1: The targets kit and Wolves

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/TargetsFixture.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WolvesSuite.scala`

**Interfaces:**
- Consumes `PaidAction(idValue: String, cost: Cost)` and `PlayerFacts.player` / `PlayerFacts.forceKind` (slice 1a), `PowerFixture` (slice 1a).
- Produces `PowerAnswers.one(pending, decision): Option[DecisionOptionRef]`, `PowerAnswers.distribution(pending, decision): Option[Vector[DistributeAmount]]` and `PowerAnswers.missing(decision): OathViolation`.
- Produces `Wolves.decisionId = "power.wolves.board"`.
- Produces the test support `TargetsFixture` (`rules`, `others`, `updatePlayer`, `withSecrets`, `withPawn`, `giveAdviser`, `giveVision`, `withoutAdvisers`, `use`, `answer`, `after`, `awaits`, `pick`, `replayed`, `usableNow`, `offered`, `queryOf`, `boardOf`, `publicBoardOf`). Later tasks reuse all of it.

Ruling: cost 1 secret placed. Choose one player board, yours included, and kill one warband there. If it has none, nothing happens. Only player boards count.

- [ ] **Step 1: Write the test support**

`src/test/scala/oathdigital/gameplay/powers/TargetsFixture.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{DecisionQueryProjection, PlayerBoardProjection}

/** Staging and driving shared by the slice 1c suites. `PowerFixture` (slice
  * 1a) stays untouched so that parallel slices do not collide on it.
  */
object TargetsFixture {
  import PowerFixture._

  val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val projector = new GameProjector(catalog)

  def others(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.map(_.player).filter(_ != actor)

  def updatePlayer(ready: ReadyGame, id: PlayerId)(
      f: PlayerState => PlayerState): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == id) f(p) else p)))

  def withSecrets(ready: ReadyGame, id: PlayerId, faceUp: Int,
      faceDown: Int): ReadyGame = updatePlayer(ready, id)(p => p.copy(
    board = p.board.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown)))

  def withPawn(ready: ReadyGame, id: PlayerId, site: SiteId): ReadyGame =
    updatePlayer(ready, id)(_.copy(pawnSite = Some(site)))

  private def outOfWorldDeck(ready: ReadyGame, card: WorldCardId): ReadyGame =
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == card))))

  /** `card` becomes an adviser of `owner`, taken from the world deck. */
  def giveAdviser(ready: ReadyGame, owner: PlayerId, card: DenizenId,
      orientation: Orientation): ReadyGame =
    updatePlayer(outOfWorldDeck(ready, card), owner)(p => p.copy(advisers =
      p.advisers :+ DenizenState(card, orientation, Tokens.empty)))

  def giveVision(ready: ReadyGame, owner: PlayerId, card: VisionId,
      orientation: Orientation): ReadyGame =
    updatePlayer(outOfWorldDeck(ready, card), owner)(p => p.copy(advisers =
      p.advisers :+ VisionState(card, orientation)))

  /** Puts `owner`'s advisers at the bottom of the world deck. */
  def withoutAdvisers(ready: ReadyGame, owner: PlayerId): ReadyGame = {
    val held = player(ready, owner).advisers.map(_.id).collect {
      case id: WorldCardId => id }
    updatePlayer(ready, owner)(_.copy(advisers = Vector.empty))
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck ++ held)))
  }

  def use(ready: ReadyGame, power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.startWalker(
    OathState.Ready(ready), ActionRef.UsePower(power.id), actor, Vector.empty,
    Vector(source))

  def answer(from: OathTransition, by: PlayerId, decision: String,
      answered: DecisionAnswer): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, by, decision, answered)

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[OathState.Ready].value

  def awaits(transition: OathTransition, decision: String): Boolean =
    transition.continue ==
      OathContinue.AwaitingPowerDecision(actor, DecisionId(decision))

  def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  def usableNow(ready: ReadyGame) = PhasePowerProcedure.usable(catalog, ready,
    actor, PhasePowerCatalog.default(catalog))

  /** The parked decision as `viewer` is offered it. */
  def queryOf(transition: OathTransition, viewer: PlayerId)
      : Option[DecisionQueryProjection] = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).walkerDecision.flatMap(_.query)

  /** The `(kind, id)` of every option of the parked decision. */
  def offered(transition: OathTransition, viewer: PlayerId)
      : Option[Vector[(String, String)]] =
    queryOf(transition, viewer).map(_.options.map(o => o.kind -> o.id))

  def boardOf(transition: OathTransition, viewer: PlayerId, owner: PlayerId)
      : PlayerBoardProjection = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).playerBoards
    .find(_.playerId == owner.value).get

  def publicBoardOf(transition: OathTransition, owner: PlayerId)
      : PlayerBoardProjection = projector.projectPublic("targets",
    LoadedGame(transition.state, 30L)).playerBoards
    .find(_.playerId == owner.value).get
}
```

- [ ] **Step 2: Write the failing Wolves suite**

`src/test/scala/oathdigital/gameplay/powers/action/WolvesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PlayerFacts, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WolvesSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val wolves = DenizenId("39")
  private val source = DecisionOptionRef.Denizen(wolves)
  private val victim = others(base).head

  private def staged(secrets: Int = 1) = inPhase(
    withSecrets(atHome(base, wolves), actor, secrets, 0), Phase.Act)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == wolves => d }.get
  private def bankOf(ready: ReadyGame, id: PlayerId) =
    warbandBank(ready, PlayerFacts.forceKind(ready, id).toOption.get)
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def parked = use(staged(), Wolves, source).toOption.get

  test("Wolves is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Wolves.id).isDefined)
  }

  test("using it places a secret on its card and asks for a player board") {
    val t = parked
    assert(awaits(t, Wolves.decisionId), t.continue.toString)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor), Some(after(t).game.current.players
      .map(p => "player" -> p.player.value)))
  }

  test("the chosen board loses one warband, which returns to its bank") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(player(after(done), victim).board.warbands,
      player(after(t), victim).board.warbands - 1)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim) + 1)
    assertEquals(replayed(staged(), t.events ++ done.events), Right(done.state))
  }

  test("the acting player's own board is a legal target") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(actor)).toOption.get
    assertEquals(player(after(done)).board.warbands,
      player(after(t)).board.warbands - 1)
  }

  test("a board with no warbands loses nothing and the cost stays paid") {
    val empty = updatePlayer(staged(), victim)(p =>
      p.copy(board = p.board.copy(warbands = 0)))
    val t = use(empty, Wolves, source).toOption.get
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(player(after(done), victim).board.warbands, 0)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim))
    assertEquals(cardOf(after(done)).tokens, Tokens(0, 1))
  }

  test("only the acting player answers, and only with an offered board") {
    val t = parked
    assert(answer(t, victim, Wolves.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, Wolves.decisionId,
      choose(PlayerId("nobody"))).isLeft)
  }

  test("it is unusable without a faceup secret to place") {
    assertEquals(usableNow(staged(secrets = 0)), Vector.empty)
    assert(use(staged(secrets = 0), Wolves, source).isLeft)
  }

  test("it is unusable again while its card holds the secret") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    val again = withSecrets(after(done), actor, 1, 0)
    assertEquals(usableNow(again), Vector.empty)
    assert(use(again, Wolves, source).isLeft)
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WolvesSuite"`
Expected: FAIL to compile, `not found: value Wolves`. The fixture itself compiles.

- [ ] **Step 4: Write `PowerAnswers` and Wolves**

`src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** Reads of the answers a power's own decisions recorded in the pending
  * tree. A power's effect is a `BuildOps`, which runs after the answer is
  * recorded, so it reads the answer from here.
  */
object PowerAnswers {
  def one(pending: PendingTree, decision: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst {
    case Answered(`decision`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
  }

  def distribution(pending: PendingTree, decision: String)
      : Option[Vector[DistributeAmount]] = pending.answered.collectFirst {
    case Answered(`decision`, DecisionAnswer.DistributeAnswer(rows), _) => rows
  }

  def missing(decision: String): OathViolation = OathViolation
    .InvalidEventOrder(s"no answer is recorded for $decision")
}
```

`src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Wolves (card 39), ACTION: place 1 secret on this card, then kill one
  * warband on any one player board, the acting player's included. A board
  * with no warband loses nothing, because `Kill` is optional.
  *
  * The decision is a plain `Decide`: its options are the players, which the
  * cost does not change, so `build` and `rebuild` derive the same query.
  */
case object Wolves extends PaidAction("denizen.wolves", Cost(secret = 1)) {
  val decisionId: String = "power.wolves.board"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      ready.game.current.players.map(p => DecisionOption.Player(
        DecisionOptionRef.Player(p.player))),
      heading = Some("Wolves: kill one warband on a player board"))),
    BuildOps((live, pending) => kill(live, pending)))))

  private def kill(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    ref <- PowerAnswers.one(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
    board <- ref match {
      case DecisionOptionRef.Player(id) => PlayerFacts.player(ready, id)
      case other => Left(OathViolation.InvalidEventOrder(
        s"${other.kind}/${other.wireId} is not a player board"))
    }
    kind <- PlayerFacts.forceKind(ready, board.player)
  } yield Vector(Kill(Piece.Warbands(kind, 1),
    PositionedLocation(Location.PlayArea(board.player))))
}
```

`PhasePowerCatalog.scala`: import `Wolves` and add the new powers on their own line after the slice 1a vector:

```scala
import oathdigital.gameplay.powers.action.{Elders, MagicWaterskin, WaysideInn, Wolves}
```
```scala
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains) ++
      Vector[PhasePower](Wolves))
```

- [ ] **Step 5: Run the Wolves suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WolvesSuite"`
Expected: PASS. If the replay assertion fails, compare `replayed(...)` with `done.state` field by field: the likely cause is a state written outside a recorded operation, which is a bug in the power. If `warbandBank` is off by one, read the `PowerFixture.warbandBank` derivation (supply less boards and sites) before changing the power.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Wolves

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Alchemist

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/AlchemistSuite.scala`

**Interfaces:**
- Consumes `PaidAction`, `PowerAnswers` (Task 1), `TargetsFixture` (Task 1).
- Produces `Alchemist.decisionId = "power.alchemist.banks"` and `Alchemist.Favor = 4`.

Ruling: cost 1 secret placed and 1 secret burnt. Gain 4 favor from any bank or banks: a `Distribute` with a total of exactly min(4, favor available across all banks). No decision is asked when one bank holds all the available favor, or when 4 or fewer are available (you take everything). The favor goes to your board.

The decision is asked exactly when at least two banks are non-empty and more than 4 favor is available in all (see "What planning found", item 5). The effect recomputes that condition from live state, so it needs no record that a decision was asked.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/AlchemistSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class AlchemistSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val alchemist = DenizenId("9")
  private val source = DecisionOptionRef.Denizen(alchemist)

  /** The banks hold exactly `favor`, and the actor holds two faceup secrets
    * beside a site Alchemist.
    */
  private def staged(favor: (Suit, Int)*) = {
    val ready = inPhase(withSecrets(atHome(base, alchemist), actor, 2, 0),
      Phase.Act)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(_ -> 0).toMap ++ favor))
  }
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == alchemist => d }.get
  private def rows(amounts: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    amounts.toVector.map { case (suit, n) =>
      DistributeAmount(DecisionOptionRef.FavorBank(suit), n) })

  test("Alchemist is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Alchemist.id).isDefined)
  }

  test("the cost is one secret placed and one burnt") {
    val ready = staged(Suit.Nomad -> 9)
    val t = use(ready, Alchemist, source).toOption.get
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
  }

  test("one bank holding all the favor gives four from it without a decision") {
    val ready = staged(Suit.Nomad -> 9)
    val t = use(ready, Alchemist, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 4)
    assertEquals(bank(after(t), Suit.Nomad), 5)
  }

  test("four or fewer in all takes everything from every bank, unasked") {
    val ready = staged(Suit.Arcane -> 2, Suit.Discord -> 1)
    val t = use(ready, Alchemist, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 3)
    assertEquals(bank(after(t), Suit.Arcane), 0)
    assertEquals(bank(after(t), Suit.Discord), 0)
  }

  test("empty banks give nothing, and the cost stays paid") {
    val ready = staged()
    val t = use(ready, Alchemist, source).toOption.get
    assertEquals(player(after(t)).board.favor, player(ready).board.favor)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
  }

  test("several banks holding more than four ask for a distribution of four") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = use(ready, Alchemist, source).toOption.get
    assert(awaits(t, Alchemist.decisionId), t.continue.toString)
    val query = queryOf(t, actor).get
    assertEquals(query.form, "distribute")
    assertEquals(query.slots.map(s => (s.option.id, s.minimum, s.maximum)),
      Suit.all.filter(Set(Suit.Arcane, Suit.Discord, Suit.Nomad))
        .map(suit => (suit.key, 0, math.min(ready.banks.favor(suit), 4))))
    assertEquals((query.minTotal, query.maxTotal), (Some(4), Some(4)))
    val done = answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 1, Suit.Nomad -> 0)).toOption.get
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 4)
    assertEquals(Vector(Suit.Arcane, Suit.Discord, Suit.Nomad)
      .map(bank(after(done), _)), Vector(0, 2, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a distribution must total four and respect each bank") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = use(ready, Alchemist, source).toOption.get
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 1, Suit.Discord -> 0, Suit.Nomad -> 3)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 4, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)
  }

  test("only the acting player answers") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3)
    val t = use(ready, Alchemist, source).toOption.get
    assert(answer(t, others(ready).head, Alchemist.decisionId,
      rows(Suit.Arcane -> 2, Suit.Discord -> 2)).isLeft)
  }

  test("the cost needs two faceup secrets, and facedown ones do not count") {
    Vector((1, 0), (1, 5), (0, 5)).foreach { case (up, down) =>
      val ready = withSecrets(staged(Suit.Nomad -> 9), actor, up, down)
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, Alchemist, source).isLeft)
    }
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AlchemistSuite"`
Expected: FAIL to compile, `not found: value Alchemist`.

- [ ] **Step 3: Write Alchemist**

`src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Alchemist (card 9), ACTION: place 1 secret on this card and burn 1, then
  * gain 4 favor from any bank or banks.
  *
  * The player chooses the split only when there is a choice: two or more
  * banks hold favor and more than 4 is available in all. Otherwise every
  * legal answer takes the same favor, so none is asked, which is also what
  * `DecisionQueries.wellFormed` requires of a `Distribute`. The decision is a
  * live `Branch`, read after the cost is paid, and the effect recomputes the
  * same condition from live state.
  */
case object Alchemist extends PaidAction("denizen.alchemist",
    Cost(secret = 1, secretBurnt = 1)) {
  val Favor: Int = 4
  val decisionId: String = "power.alchemist.banks"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => gain(live, player, pending)))))

  /** The banks that hold favor, with their stock, in suit order. */
  private def stocked(ready: ReadyGame): Vector[(Suit, Int)] =
    Suit.all.map(suit => suit -> ready.banks.favor.getOrElse(suit, 0))
      .filter(_._2 > 0)

  private def choosing(banks: Vector[(Suit, Int)]): Boolean =
    banks.size >= 2 && banks.map(_._2).sum > Favor

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val banks = stocked(ready)
    if (!choosing(banks)) Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      banks.map { case (suit, stock) => DistributeSlot(
        DecisionOptionRef.FavorBank(suit), 0, math.min(stock, Favor), None) },
      total = Favor,
      heading = Some("Alchemist: take 4 favor from any banks"),
      confirmLabel = "Take favor")))
  }

  private def gain(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val banks = stocked(ready)
    if (!choosing(banks)) Right(banks.map { case (suit, stock) =>
      Gain.Favor(player, suit, math.min(stock, Favor)) })
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).map(_.collect {
        case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) if n > 0 =>
          Gain.Favor(player, suit, n) })
  }
}
```

`PhasePowerCatalog.scala`: import `Alchemist` and extend the second vector: `Vector[PhasePower](Wolves, Alchemist))`.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AlchemistSuite"`
Expected: PASS. If an answer is rejected with "leaving nothing to decide", the query is malformed. Read `DecisionQueries.wellFormed`: the `choosing` condition must match its "fewer than two variable slots" and "maximums already make the total" rules, and the fix is in `choosing`, not in the validator.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Alchemist

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Sleight of Hand

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SleightOfHandSuite.scala`

**Interfaces:**
- Consumes `PaidAction`, `PowerAnswers`, `PowerAccess.pawnSite` (`private[gameplay]`, reachable from `gameplay.powers`), `TargetsFixture`.
- Produces `SleightOfHand.decisionId = "power.sleight-of-hand.target"`.

Ruling: cost 1 favor placed. Targets are other players whose pawn is at your site and who have 2 or more secrets on their board (faceup and facedown together). Take one secret, faceup first, otherwise facedown. It arrives with the same orientation. With no legal target the cost is paid and nothing else happens. The take is a `Take`, so other powers may restrict it.

The mixed-orientation case needs the three-operation batch from "What planning found", item 2.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/SleightOfHandSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SleightOfHandSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val sleight = DenizenId("17")
  private val source = DecisionOptionRef.Denizen(sleight)
  private val victim = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get

  /** The actor holds Sleight of Hand as an adviser with `favor`. `victim` is
    * at the actor's site holding the given secrets. `bystander` is at
    * another site with plenty of secrets, so it is never a target.
    */
  private def staged(up: Int, down: Int, favor: Int = 1) = {
    val actorReady = inPhase(withBoard(asAdviser(base, sleight))(
      _.copy(favor = favor)), Phase.Act)
    val placed = withPawn(withPawn(actorReady, victim, home(actorReady)),
      bystander, elsewhere(actorReady))
    withSecrets(withSecrets(placed, victim, up, down), bystander, 5, 5)
  }
  private def secretsOf(ready: ReadyGame, id: PlayerId = actor) =
    (player(ready, id).board.faceUpSecrets, player(ready, id).board.faceDownSecrets)
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def cardOf(ready: ReadyGame) = player(ready).advisers.collectFirst {
    case d: DenizenState if d.id == sleight => d }.get

  test("Sleight of Hand is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(SleightOfHand.id).isDefined)
  }

  test("it places a favor on its card and offers players at the actor's site " +
      "holding two or more secrets") {
    val ready = staged(2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(awaits(t, SleightOfHand.decisionId), t.continue.toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)
  }

  test("a target with faceup secrets only gives one faceup secret") {
    val ready = staged(2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (1, 0))
    assertEquals(secretsOf(after(done)), (secretsOf(ready)._1 + 1, 0))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a target with facedown secrets only gives one, arriving facedown") {
    val ready = staged(0, 2)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (0, 1))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1, secretsOf(ready)._2 + 1))
  }

  test("a mixed target gives a faceup secret, arriving faceup, and keeps " +
      "its facedown secrets") {
    val ready = staged(1, 2)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (0, 2))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1 + 1, secretsOf(ready)._2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a player holding one secret is not a target, so nothing is asked") {
    val ready = staged(1, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(secretsOf(after(t), victim), (1, 0))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)
  }

  test("a player at another site is not a target") {
    val ready = withPawn(staged(3, 0), victim, elsewhere(staged(3, 0)))
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    assertEquals(secretsOf(after(t), victim), (3, 0))
  }

  test("every eligible player is offered") {
    val ready = withSecrets(withPawn(staged(2, 0), bystander,
      home(staged(2, 0))), bystander, 2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))
  }

  test("only the acting player answers, with an offered player") {
    val t = use(staged(2, 0), SleightOfHand, source).toOption.get
    assert(answer(t, victim, SleightOfHand.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, SleightOfHand.decisionId, choose(bystander)).isLeft)
    assert(answer(t, actor, SleightOfHand.decisionId, choose(actor)).isLeft)
  }

  test("it is unusable without a favor to place") {
    val ready = staged(2, 0, favor = 0)
    assertEquals(usableNow(ready), Vector.empty)
    assert(use(ready, SleightOfHand, source).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SleightOfHandSuite"`
Expected: FAIL to compile, `not found: value SleightOfHand`.

- [ ] **Step 3: Write Sleight of Hand**

`src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Sleight of Hand (card 17), ACTION: place 1 favor on this card, then take
  * one secret from a player whose pawn is at your site and who holds two or
  * more secrets, so their last secret is never taken. Faceup first,
  * otherwise facedown, arriving with the same orientation.
  *
  * The targets are read live, after the cost is paid. With none, the cost
  * stays paid and nothing else happens.
  *
  * The take is one `Take`. A target holding both orientations makes a bare
  * one-secret `Take` ambiguous (`AmbiguousSecretOrientation`), so the batch
  * flips the target's facedown secrets up first, takes, and flips the same
  * number back down. The net effect is exactly "one faceup secret moves".
  */
case object SleightOfHand extends PaidAction("denizen.sleight-of-hand",
    Cost(favor = 1)) {
  val decisionId: String = "power.sleight-of-hand.target"
  val MinimumSecrets: Int = 2

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => steal(live, player, pending)))))

  private def secretsOf(player: PlayerState): Int =
    player.board.faceUpSecrets + player.board.faceDownSecrets

  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site) && secretsOf(p) >= MinimumSecrets))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = targets(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some(
        "Sleight of Hand: take a secret from a player at your site"))))
  }

  private def steal(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = targets(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a legal Sleight of Hand target"))
    } yield take(actor, target)
  }

  private def take(actor: PlayerId, target: PlayerState)
      : Vector[CoreOperation] = {
    val one = Take(Piece.Secrets(1), actor, Location.PlayArea(target.player),
      Location.PlayArea(actor))
    val faceDown = target.board.faceDownSecrets
    if (target.board.faceUpSecrets > 0 && faceDown > 0) Vector(
      FlipSecrets(target.player, faceDown, SecretSide.FaceDown,
        SecretSide.FaceUp),
      one,
      FlipSecrets(target.player, faceDown, SecretSide.FaceUp,
        SecretSide.FaceDown))
    else Vector(one)
  }
}
```

`PhasePowerCatalog.scala`: import `SleightOfHand` and add it to the second vector.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SleightOfHandSuite"`
Expected: PASS. If the mixed-target test fails with `AmbiguousSecretOrientation`, the flips did not run first: check the order of the three operations. If `others(base)(1)` throws, the first game seats fewer than three players, so read `FirstGameSetupFixture.participants` and adapt the fixture, not the power.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Sleight of Hand

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Crystal Vial

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/CrystalVial.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/CrystalVialSuite.scala`

**Interfaces:**
- Consumes `PaidAction`, `PowerAnswers`, `PowerAccess.pawnSite`, `Bury.standard` (slice 0), `TargetsFixture`.
- Produces `CrystalVial(catalog: ExecutableCatalog)`, `CrystalVial.id`, `CrystalVial.price` and `CrystalVial.decisionId = "power.crystal-vial.card"`. `PhasePowerCatalog.default(catalog)` builds it with the catalog.

Ruling: cost 1 secret placed and 1 secret burnt. Choose an adviser you hold (denizen or Vision, either orientation) or a card in the site's card list at your pawn's site (denizens and the edifice, intact or ruined). Bury it with the standard returns. The choice is required when a candidate exists. Bury ignores the locked restriction, and `Bury` is not a `Discard`, so `DiscardRestrictions` (the Hall of Ministers) does not apply.

The printed text says "a denizen at your site". The approved ruling adds the edifice, which the model treats as a special denizen. The plan follows the ruling.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/CrystalVialSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class CrystalVialSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val vial = RelicId("R21")
  private val source = DecisionOptionRef.Relic(vial)
  private val power = CrystalVial(catalog)
  private val inn = DenizenId("47")
  private val faith = VisionId("vision:vision-of-faith")
  private val held: DenizenId = player(base).advisers.collectFirst {
    case d: DenizenState => d.id }.get
  private val other = others(base).head

  private def staged = inPhase(withSecrets(withRelic(base, vial), actor, 2, 0),
    Phase.Act)
  private def relicOf(ready: ReadyGame) = player(ready).relics
    .find(_.id == vial).get
  private def secretsOf(ready: ReadyGame) =
    (player(ready).board.faceUpSecrets, player(ready).board.faceDownSecrets)
  private def siteCards(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.map {
      case d: DenizenState => "denizen" -> d.id.value
      case e: EdificeState => "edifice" -> e.id.value }
  private def worldDeck(ready: ReadyGame) = ready.game.current.commonCards.worldDeck
  private def withAdviserTokens(ready: ReadyGame, tokens: Tokens) =
    updateActor(ready)(p => p.copy(advisers = p.advisers.map {
      case d: DenizenState if d.id == held => d.copy(tokens = tokens)
      case unchanged => unchanged }))
  private def withSiteTokens(ready: ReadyGame, card: DenizenId, tokens: Tokens) =
    ready.updateCurrent { c =>
      val site = c.map.sites(home(ready))
      c.copy(map = c.map.copy(sites = c.map.sites.updated(home(ready),
        site.copy(denizens = site.denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = tokens)
          case unchanged => unchanged }))))
    }
  /** The actor at a site with no cards, holding no advisers. */
  private def bare: ReadyGame = {
    val site = base.game.current.map.inPlay.last
    val cleared = base.updateCurrent { c =>
      val state = c.map.sites(site)
      c.copy(
        map = c.map.copy(sites = c.map.sites.updated(site,
          state.copy(denizens = Vector.empty))),
        commonCards = c.commonCards.copy(
          worldDeck = c.commonCards.worldDeck ++ state.denizens.collect {
            case d: DenizenState => d.id: WorldCardId },
          edificeDeck = c.commonCards.edificeDeck ++ state.denizens.collect {
            case e: EdificeState => e.id }))
    }
    inPhase(withSecrets(withRelic(withPawn(withoutAdvisers(cleared, actor),
      actor, site), vial), actor, 2, 0), Phase.Act)
  }

  test("Crystal Vial is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(CrystalVial.id).isDefined)
  }

  test("the cost is one secret placed on the Vial and one burnt") {
    val t = use(staged, power, source).toOption.get
    assert(awaits(t, CrystalVial.decisionId), t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))
  }

  test("it offers the actor's advisers in either orientation and the cards " +
      "at the actor's site, and nobody else's") {
    val ready = atHome(giveVision(giveAdviser(staged, actor, DenizenId("26"),
      Orientation.FaceUp), actor, faith, Orientation.FaceDown), inn)
    val t = use(ready, power, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet), Some(Set(
      "denizen" -> held.value, "denizen" -> "26", "vision" -> faith.value) ++
      siteCards(ready)))
  }

  test("an adviser is buried at the bottom of the world deck, and its favor " +
      "and secrets return") {
    val ready = withAdviserTokens(staged, Tokens(2, 1))
    val suit = catalog.suitOf(held).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).toOption.get
    assert(!player(after(done)).advisers.exists(_.id == held))
    assertEquals(worldDeck(after(done)).last, held)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(secretsOf(after(done)), (0, 1))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a Vision adviser can be buried") {
    val ready = giveVision(staged, actor, faith, Orientation.FaceDown)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Vision(faith))).toOption.get
    assert(!player(after(done)).advisers.exists(_.id == faith))
    assertEquals(worldDeck(after(done)).last, faith)
  }

  test("a denizen at the site is buried with its favor and secrets returned") {
    val ready = withSiteTokens(atHome(staged, inn), inn, Tokens(1, 1))
    val suit = catalog.suitOf(inn).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(inn))).toOption.get
    assert(!siteCards(after(done)).contains("denizen" -> inn.value))
    assertEquals(worldDeck(after(done)).last, inn)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 1)
    assertEquals(secretsOf(after(done)), (0, 1))
  }

  test("the edifice at the site is buried whichever face it shows") {
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val edifice = EdificeId("E15")
      val ready = withEdifice(staged, edifice, side, home(staged))
      val t = use(ready, power, source).toOption.get
      val done = answer(t, actor, CrystalVial.decisionId,
        pick(DecisionOptionRef.Edifice(edifice))).toOption.get
      assert(!siteCards(after(done)).contains("edifice" -> edifice.value),
        side.toString)
      assertEquals(after(done).game.current.commonCards.edificeDeck.last,
        edifice, side.toString)
    }
  }

  test("only the acting player answers, with an offered card") {
    val t = use(staged, power, source).toOption.get
    val theirs = player(base, other).advisers.collectFirst {
      case d: DenizenState => d.id }.get
    assert(answer(t, other, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).isLeft)
    assert(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(theirs))).isLeft)
  }

  test("with no candidate the cost is paid and nothing else happens") {
    val ready = bare
    assertEquals(siteCards(ready), Vector.empty)
    val t = use(ready, power, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))
  }

  test("it needs two faceup secrets, and is unusable while the Vial holds one") {
    Vector((1, 0), (1, 5)).foreach { case (up, down) =>
      val ready = withSecrets(staged, actor, up, down)
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, power, source).isLeft)
    }
    val t = use(staged, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).toOption.get
    assertEquals(usableNow(withSecrets(after(done), actor, 2, 0)), Vector.empty)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.CrystalVialSuite"`
Expected: FAIL to compile, `not found: value CrystalVial`.

- [ ] **Step 3: Write Crystal Vial**

`src/main/scala/oathdigital/gameplay/powers/action/CrystalVial.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Crystal Vial (relic R21), ACTION: place 1 secret on this relic and burn
  * 1, then bury an adviser of yours (a denizen or a Vision, in either
  * orientation) or a card in the card list at your pawn's site (a denizen or
  * the edifice, in either state). The burial uses the standard returns:
  * favor to the card's suit bank, secrets to the acting player facedown.
  *
  * The choice is required when a candidate exists. It is a live `Branch`,
  * read after the cost is paid, and the effect recomputes the candidates
  * from live state. The power holds the catalog because `Bury.standard`
  * needs the card's suit to return favor.
  */
final case class CrystalVial(catalog: ExecutableCatalog)
    extends PaidAction(CrystalVial.id.value, CrystalVial.price) {
  import CrystalVial._

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)))))

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] = {
    val current = ready.game.current
    val advisers = current.players.find(_.player == actor).toVector
      .flatMap(_.advisers.map {
        case d: DenizenState => Candidate(DecisionOptionRef.Denizen(d.id),
          BuryableCard.Denizen(d.id), Location.PlayArea(actor), d.tokens)
        case v: VisionState => Candidate(DecisionOptionRef.Vision(v.id),
          BuryableCard.Vision(v.id), Location.PlayArea(actor), Tokens.empty)
      })
    val here = PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      current.map.sites.get(site).toVector.flatMap(_.denizens.map {
        case d: DenizenState => Candidate(DecisionOptionRef.Denizen(d.id),
          BuryableCard.Denizen(d.id), Location.Site(site), d.tokens)
        case e: EdificeState => Candidate(DecisionOptionRef.Edifice(e.id),
          BuryableCard.Edifice(e.id), Location.Site(site), e.tokens)
      }))
    advisers ++ here
  }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = candidates(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.flatMap(c => DecisionOption.forRef(c.ref)), heading = Some(
        "Crystal Vial: bury an adviser of yours or a card at your site"))))
  }

  private def bury(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = candidates(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      chosen <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a card the Vial can bury"))
      suit = catalog.suitOf(chosen.card.id)
      _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
        OathViolation.InvalidEventOrder(
          s"no suit is known for ${chosen.card.id.value}"))
    } yield Bury.standard(chosen.card, PositionedLocation(chosen.from), suit,
      chosen.tokens.favor, chosen.tokens.secrets, actor)
  }
}

object CrystalVial {
  val id: PowerId = PowerId("relic.crystal-vial")
  val price: Cost = Cost(secret = 1, secretBurnt = 1)
  val decisionId: String = "power.crystal-vial.card"

  private[action] final case class Candidate(ref: DecisionOptionRef,
      card: BuryableCard, from: Location, tokens: Tokens)
}
```

`PhasePowerCatalog.scala`: import `CrystalVial` and add `CrystalVial(catalog)` to the second vector.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.CrystalVialSuite"`
Expected: PASS. If `EdificeId("E15")` is not in the edifice deck, `withEdifice` fails fast: take it from the site that holds it instead, keeping the inventory whole. If the last site of the map is not empty after `bare`, check that `bare` moved every card to its deck.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Crystal Vial

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Ivory Eye

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala`

**Interfaces:**
- Consumes `PaidAction`, `PowerAnswers`, `TargetsFixture` (including `boardOf` and `publicBoardOf`).
- Produces `IvoryEye.decisionId = "power.ivory-eye.adviser"` and `IvoryEye.optionFor(owner: PlayerId, slot: Int): DecisionOptionRef.Button`.

Ruling: cost 1 secret placed. Choose any facedown adviser of any player, yours included, and `Peek` at it. The peek is private. Other players see only a log line saying who peeked at whose adviser.

The log line is not implemented: no player-visible action log exists (ROADMAP, Phase 3 lists it as future work). The privacy half is done by `Peek` alone (see "What planning found", item 4). The options are `Button`s, not card references, so that the decision is projectable (item 3).

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class IvoryEyeSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val eye = RelicId("R16")
  private val source = DecisionOptionRef.Relic(eye)
  private val target = others(base)(0)
  private val third = others(base)(1)
  private val faith = VisionId("vision:vision-of-faith")

  private def staged = inPhase(withRelic(base, eye), Phase.Act)
  private def relicOf(ready: ReadyGame) = player(ready).relics
    .find(_.id == eye).get
  private def firstAdviser(ready: ReadyGame, owner: PlayerId): CardId =
    player(ready, owner).advisers.head.id
  private def known(ready: ReadyGame, viewer: PlayerId): Vector[WorldCardId] =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty)
  private def facedown(ready: ReadyGame) = for {
    p <- ready.game.current.players
    (adviser, slot) <- p.advisers.zipWithIndex
    if (adviser match {
      case d: DenizenState => d.orientation == Orientation.FaceDown
      case v: VisionState => v.orientation == Orientation.FaceDown })
  } yield IvoryEye.optionFor(p.player, slot)
  private def peekAt(owner: PlayerId, slot: Int) =
    pick(IvoryEye.optionFor(owner, slot))

  test("Ivory Eye is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(IvoryEye.id).isDefined)
  }

  test("it places a secret on the relic and offers every facedown adviser " +
      "of every player, and no faceup one") {
    val ready = giveVision(giveAdviser(staged, target, DenizenId("26"),
      Orientation.FaceUp), actor, faith, Orientation.FaceDown)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(awaits(t, IvoryEye.decisionId), t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor),
      Some(facedown(ready).map(o => o.kind -> o.wireId)))
    assert(!offered(t, actor).get.contains(
      IvoryEye.optionFor(target, 1).kind -> IvoryEye.optionFor(target, 1).wireId))
    assertEquals(facedown(ready).size, 4)
  }

  test("a peek records knowledge for the actor only and changes nothing else") {
    val t = use(staged, IvoryEye, source).toOption.get
    val card = firstAdviser(staged, target).asInstanceOf[WorldCardId]
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assert(known(after(done), actor).contains(card))
    assert(!known(after(done), target).contains(card))
    assert(!known(after(done), third).contains(card))
    assertEquals(player(after(done), target), player(after(t), target))
    assertEquals(replayed(staged, t.events ++ done.events), Right(done.state))
  }

  test("the peeked adviser is named to the actor and to nobody else") {
    val t = use(staged, IvoryEye, source).toOption.get
    val card = firstAdviser(staged, target).value
    def named(board: oathdigital.protocol.projection.PlayerBoardProjection) =
      board.advisers.exists(c => c.cardId == card && !c.hidden)
    assert(!named(boardOf(t, actor, target)))
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assert(named(boardOf(done, actor, target)))
    assert(!named(boardOf(done, third, target)))
    assert(named(boardOf(t, target, target)), "the owner always knows it")
    assert(!named(publicBoardOf(done, target)))
  }

  test("a facedown Vision can be peeked") {
    val ready = giveVision(staged, target, faith, Orientation.FaceDown)
    val t = use(ready, IvoryEye, source).toOption.get
    val slot = player(ready, target).advisers.indexWhere(_.id == faith)
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, slot))
      .toOption.get
    assert(known(after(done), actor).contains(faith))
  }

  test("the actor's own facedown adviser is a legal target") {
    val t = use(staged, IvoryEye, source).toOption.get
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(actor, 0)).toOption
    assert(done.nonEmpty)
  }

  test("only the acting player answers, with an offered adviser") {
    val ready = giveAdviser(staged, target, DenizenId("26"), Orientation.FaceUp)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(answer(t, target, IvoryEye.decisionId, peekAt(target, 0)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 1)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 7)).isLeft)
  }

  test("with no facedown adviser the cost is paid and nothing else happens") {
    val ready = (others(base) :+ actor).foldLeft(staged)(withoutAdvisers)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
  }

  test("it is unusable without a faceup secret, or while the relic holds one") {
    val broke = withSecrets(staged, actor, 0, 3)
    assertEquals(usableNow(broke), Vector.empty)
    assert(use(broke, IvoryEye, source).isLeft)
    val t = use(staged, IvoryEye, source).toOption.get
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assertEquals(usableNow(withSecrets(after(done), actor, 1, 0)), Vector.empty)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.IvoryEyeSuite"`
Expected: FAIL to compile, `not found: value IvoryEye`.

- [ ] **Step 3: Write Ivory Eye**

`src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Ivory Eye (relic R16), ACTION: place 1 secret on this relic, then peek at
  * any facedown adviser, a Vision included, of any player, the acting
  * player's included.
  *
  * A `Peek` is the whole disclosure. Replaying it records the card in the
  * viewer's knowledge, and the presentation layer then names that facedown
  * adviser to the viewer alone.
  *
  * The options are `Button`s keyed by owner and adviser slot rather than card
  * references. A card option names the card's identity, and the projector
  * drops any decision that names a card its viewer may not identify, which a
  * facedown adviser of another player is. The slot is read from live state, so
  * an answer cannot name a card that has since moved.
  */
case object IvoryEye extends PaidAction("relic.ivory-eye", Cost(secret = 1)) {
  val decisionId: String = "power.ivory-eye.adviser"
  private val Prefix = "adviser:"

  /** The option for the facedown adviser in position `slot` of `owner`'s
    * advisers.
    */
  def optionFor(owner: PlayerId, slot: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"$Prefix${owner.value}:$slot")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => peek(live, player, pending)))))

  private final case class Target(owner: PlayerId, slot: Int,
      card: WorldCardId) {
    def ref: DecisionOptionRef.Button = optionFor(owner, slot)
    def label: String = s"${owner.value}: facedown adviser ${slot + 1}"
  }

  private def targets(ready: ReadyGame): Vector[Target] = for {
    player <- ready.game.current.players
    (adviser, slot) <- player.advisers.zipWithIndex
    card <- adviser match {
      case DenizenState(id, Orientation.FaceDown, _) => Some(id: WorldCardId)
      case VisionState(id, Orientation.FaceDown) => Some(id: WorldCardId)
      case _ => None
    }
  } yield Target(player.player, slot, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = targets(ready)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(t => DecisionOption.Button(t.ref, t.label)),
      heading = Some("Ivory Eye: peek at a facedown adviser"))))
  }

  private def peek(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = targets(ready)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a facedown adviser"))
    } yield Vector(Peek(actor, target.card, Location.PlayArea(target.owner)))
  }
}
```

`PhasePowerCatalog.scala`: import `IvoryEye` and add it to the second vector.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.IvoryEyeSuite"`
Expected: PASS. If "the peeked adviser is named" fails at `boardOf(done, actor, target)`, read `GamePresentationProjector.knownToViewer`: it matches `knowledge.advisers` by `id.value`, and the `Peek` must have been recorded (check `done.events` holds a `WalkerStepRecorded` with a `Peek`). If the projection of the decision is missing (`offered` is `None`), an option was rejected as unpresentable: every option must be a `Button`.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Ivory Eye

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Horned Mask

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`

**Interfaces:**
- Consumes `PhasePower`, `PowerAnswers`, `PlayerFacts.player`, `PowerAccess.pawnSite`, `Bury.standard`, `TargetsFixture`.
- Produces `HornedMask(catalog: ExecutableCatalog)`, `HornedMask.id`, `HornedMask.denizenDecisionId = "power.horned-mask.denizen"`, `HornedMask.discardDecisionId = "power.horned-mask.discard"` and `HornedMask.AdviserLimit = 3`.

Ruling: Wake. Take a non-edifice denizen from your pawn's site as a facedown adviser. `site-only` denizens are eligible, and locked ones are decided by the `Take` restrictions (none exist, and no locked denizen can be at a site: item 6). If you already have 3 advisers you choose one of yours to discard, as in card play. Resources on the taken card return by the standard returns. Once per turn (the engine records the use).

The take is `Take` (site to play area) followed by `Flip` to facedown. The discard of a replaced adviser is `Discard.Denizen` or `Discard.Vision` to the next region's discard pile, as in `CardPlay`. A locked adviser (`LockedAdviserOnly`) cannot be discarded. When the area is full and no adviser is discardable, no denizen can be taken, so nothing is asked.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class HornedMaskSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val mask = RelicId("R06")
  private val source = DecisionOptionRef.Relic(mask)
  private val power = HornedMask(catalog)
  private val inn = DenizenId("47")
  private val elders = DenizenId("26")
  private val wolves = DenizenId("39")
  private val fresh = DenizenId("1")
  private val locked = Vector(DenizenId("28"), DenizenId("15"), DenizenId("133"))
  private val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(mask),
    HornedMask.id)

  private def staged = inPhase(withRelic(base, mask), Phase.Wake)
  private def denizensHere(ready: ReadyGame): Vector[DenizenId] =
    ready.game.current.map.sites(home(ready)).denizens.collect {
      case d: DenizenState => d.id }
  private def adviser(ready: ReadyGame, id: DenizenId) = player(ready).advisers
    .collectFirst { case d: DenizenState if d.id == id => d }
  private def choose(id: DenizenId) = pick(DecisionOptionRef.Denizen(id))
  private def nextRegion(ready: ReadyGame): Region = {
    val region = ready.game.current.map.regionOf(home(ready)).get
    Region.all((Region.all.indexOf(region) + 1) % Region.all.size)
  }
  private def withSiteTokens(ready: ReadyGame, card: DenizenId, tokens: Tokens) =
    ready.updateCurrent { c =>
      val site = c.map.sites(home(ready))
      c.copy(map = c.map.copy(sites = c.map.sites.updated(home(ready),
        site.copy(denizens = site.denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = tokens)
          case unchanged => unchanged }))))
    }
  /** The actor holds exactly `cards`, faceup, beside a site inn. */
  private def holding(cards: DenizenId*) = cards.foldLeft(
    withoutAdvisers(atHome(staged, inn), actor))((ready, id) =>
    giveAdviser(ready, actor, id, Orientation.FaceUp))

  test("Horned Mask is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(HornedMask.id).isDefined)
  }

  test("it offers the denizens at the site and never the edifice") {
    val ready = withEdifice(atHome(atHome(staged, inn), elders),
      EdificeId("E15"), EdificeSide.Intact, home(staged))
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, HornedMask.denizenDecisionId), t.continue.toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(denizensHere(ready).map(id => "denizen" -> id.value).toSet))
    assertEquals(denizensHere(ready).toSet, Set(inn, elders))
  }

  test("a site-only denizen becomes a facedown adviser, and the use is " +
      "recorded once per turn") {
    val ready = atHome(staged, inn)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assert(!denizensHere(after(done)).contains(inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(player(after(done)).advisers.size, 2)
    assert(after(done).game.current.turn.usedPowers.contains(used))
    assertEquals(done.continue, OathContinue.AwaitingWakeAction(actor))
    assertEquals(use(after(done), power, source).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("resources on the taken card return by the standard returns") {
    val ready = withSiteTokens(atHome(staged, inn), inn, Tokens(2, 1))
    val suit = catalog.suitOf(inn).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(adviser(after(done), inn).map(_.tokens), Some(Tokens.empty))
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(player(after(done)).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)
  }

  test("a full adviser area asks which adviser to discard, and it goes to " +
      "the next region's discard pile") {
    val ready = holding(elders, fresh, wolves)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assert(awaits(asked, HornedMask.discardDecisionId), asked.continue.toString)
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value,
      "denizen" -> wolves.value)))
    val done = answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)).toOption.get
    assertEquals(after(done).game.current.commonCards
      .discard(nextRegion(ready)).last, elders)
    assertEquals(player(after(done)).advisers.map(_.id).toSet,
      Set[CardId](fresh, wolves, inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(replayed(ready, t.events ++ asked.events ++ done.events),
      Right(done.state))
  }

  test("a locked adviser cannot be offered for discard") {
    val ready = holding(elders, fresh, locked.head)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value)))
    assert(answer(asked, actor, HornedMask.discardDecisionId,
      choose(locked.head)).isLeft)
  }

  test("a full area of locked advisers takes nothing and asks nothing") {
    val ready = holding(locked: _*)
    val t = use(ready, power, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assert(denizensHere(after(t)).contains(inn))
    assertEquals(player(after(t)).advisers, player(ready).advisers)
  }

  test("with no denizen at the site nothing is asked and nothing moves") {
    val ready = staged
    assertEquals(denizensHere(ready), Vector.empty)
    val t = use(ready, power, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    assertEquals(player(after(t)).advisers, player(ready).advisers)
  }

  test("only the acting player answers, with an offered denizen") {
    val t = use(atHome(staged, inn), power, source).toOption.get
    assert(answer(t, others(base).head, HornedMask.denizenDecisionId,
      choose(inn)).isLeft)
    assert(answer(t, actor, HornedMask.denizenDecisionId, choose(elders)).isLeft)
  }

  test("it needs the mask faceup in the Wake phase") {
    val facedown = inPhase(withRelic(atHome(base, inn), mask,
      Orientation.FaceDown), Phase.Wake)
    assertEquals(usableNow(facedown), Vector.empty)
    assert(use(facedown, power, source).isLeft)
    assert(use(inPhase(atHome(staged, inn), Phase.Act), power, source).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: FAIL to compile, `not found: value HornedMask`.

- [ ] **Step 3: Write Horned Mask**

`src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Horned Mask (relic R06), WAKE: take a non-edifice denizen from your pawn's
  * site as a facedown adviser. The engine records the once-per-turn use.
  *
  * The tree is two live `Branch`es (which denizen, then which adviser to
  * discard when the area is full) and one `BuildOps`. Each reads live state,
  * so the tree is the same whether it is derived at the start or on a resume.
  * The effect is: discard the chosen adviser when the area is full, return the
  * taken card's favor and secrets, `Take` the card into the play area and
  * `Flip` it facedown.
  *
  * The discard follows card play: a denizen or Vision goes to the next
  * region's discard pile, and a `LockedAdviserOnly` card cannot be discarded.
  * When the area is full and nothing is discardable, nothing can be taken.
  */
final case class HornedMask(catalog: ExecutableCatalog) extends PhasePower {
  import HornedMask._

  def id: PowerId = HornedMask.id
  def timing: PowerTiming = PowerTiming.Wake
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askDenizen(live, player)),
    Branch((live, pending) => askDiscard(live, player, pending)),
    BuildOps((live, pending) => take(live, player, pending)))))

  private def site(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, SiteState)] = PowerAccess.pawnSite(ready, actor)
    .flatMap(id => ready.game.current.map.sites.get(id).map(id -> _))

  private def advisers(ready: ReadyGame, actor: PlayerId): Vector[AdviserState] =
    PlayerFacts.player(ready, actor).map(_.advisers).getOrElse(Vector.empty)

  private def full(ready: ReadyGame, actor: PlayerId): Boolean =
    advisers(ready, actor).size >= AdviserLimit

  private def lockedAdviser(id: DenizenId): Boolean = catalog.denizens
    .find(_.id.value == id.value)
    .exists(_.restrictions == CardRestrictions.LockedAdviserOnly)

  private def discardable(ready: ReadyGame, actor: PlayerId)
      : Vector[AdviserState] = advisers(ready, actor).filter {
    case d: DenizenState => !lockedAdviser(d.id)
    case _ => true
  }

  private def takeable(ready: ReadyGame, actor: PlayerId): Vector[DenizenState] =
    if (full(ready, actor) && discardable(ready, actor).isEmpty) Vector.empty
    else site(ready, actor).toVector.flatMap(_._2.denizens.collect {
      case d: DenizenState => d })

  private def ref(adviser: AdviserState): DecisionOptionRef = adviser match {
    case d: DenizenState => DecisionOptionRef.Denizen(d.id)
    case v: VisionState => DecisionOptionRef.Vision(v.id)
  }

  private def askDenizen(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = takeable(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(denizenDecisionId, actor, DecisionQuery.ChooseOne(
      found.map(d => DecisionOption.Denizen(DecisionOptionRef.Denizen(d.id))),
      heading = Some("Horned Mask: take a denizen as a facedown adviser"))))
  }

  private def askDiscard(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    if (!full(ready, actor) ||
        PowerAnswers.one(pending, denizenDecisionId).isEmpty) Vector.empty
    else Vector(Decide(discardDecisionId, actor, DecisionQuery.ChooseOne(
      discardable(ready, actor).flatMap(a => DecisionOption.forRef(ref(a))),
      heading = Some("Horned Mask: choose an adviser to discard"))))

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = takeable(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      answered <- PowerAnswers.one(pending, denizenDecisionId)
        .toRight(PowerAnswers.missing(denizenDecisionId))
      card <- found.find(d => DecisionOptionRef.Denizen(d.id) == answered)
        .toRight(OathViolation.InvalidEventOrder(
          s"${answered.wireId} is not a denizen Horned Mask can take"))
      siteId <- site(ready, actor).map(_._1)
        .toRight(OathViolation.PawnSiteMissing(actor))
      discards <-
        if (full(ready, actor)) discard(ready, actor, pending)
        else Right(Vector.empty[CoreOperation])
      returns <- returnsOf(card, siteId, actor)
    } yield discards ++ returns ++ Vector[CoreOperation](
      Take(Piece.Card(card.id), actor, Location.Site(siteId),
        Location.PlayArea(actor)),
      Flip(card.id, Location.PlayArea(actor), Orientation.FaceDown))
  }

  /** The favor and secrets of the taken card, returned as a discard returns
    * them: favor to the card's suit bank, secrets to the actor facedown. It is
    * the returns half of `Bury.standard`.
    */
  private def returnsOf(card: DenizenState, site: SiteId, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val suit = catalog.suitOf(card.id)
    if (card.tokens.favor > 0 && suit.isEmpty) Left(OathViolation
      .InvalidEventOrder(s"no suit is known for ${card.id.value}"))
    else Right(Bury.standard(BuryableCard.Denizen(card.id),
      PositionedLocation(Location.Site(site)), suit, card.tokens.favor,
      card.tokens.secrets, actor).filterNot(_.isInstanceOf[Bury]))
  }

  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    answered <- PowerAnswers.one(pending, discardDecisionId)
      .toRight(PowerAnswers.missing(discardDecisionId))
    chosen <- discardable(ready, actor).find(ref(_) == answered)
      .toRight(OathViolation.InvalidEventOrder(
        s"${answered.wireId} is not an adviser Horned Mask can discard"))
    region <- PowerAccess.pawnSite(ready, actor)
      .flatMap(ready.game.current.map.regionOf).map(next)
      .toRight(OathViolation.PawnSiteMissing(actor))
    from = PositionedLocation(Location.PlayArea(actor))
    operation <- chosen match {
      case d: DenizenState => catalog.suitOf(d.id)
        .toRight(OathViolation.UnknownWorldCard(d.id)).map(suit =>
          Discard.Denizen(d.id, from, region, suit, d.tokens.favor,
            d.tokens.secrets, actor, required = true))
      case v: VisionState =>
        Right(Discard.Vision(v.id, from, region, required = true))
    }
  } yield Vector[CoreOperation](operation)
}

object HornedMask {
  val id: PowerId = PowerId("relic.horned-mask")
  val AdviserLimit: Int = 3
  val denizenDecisionId: String = "power.horned-mask.denizen"
  val discardDecisionId: String = "power.horned-mask.discard"

  /** The region whose discard pile receives a discard made at a site of
    * `region`. It repeats `CardPlay`'s rule, which is private there and is
    * rewritten by slice 2, so the two may be folded together then.
    */
  private def next(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
}
```

`PhasePowerCatalog.scala`: import `HornedMask` and add `HornedMask(catalog)` to the second vector. After this task the vector reads:

```scala
      Vector[PhasePower](Wolves, Alchemist, SleightOfHand, CrystalVial(catalog),
        IvoryEye, HornedMask(catalog)))
```

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: PASS. If a card in `locked` (28, 15, 133) is absent from the world deck, `giveAdviser` adds it (nothing to remove). If the `Take` is rejected, read the `OperationError`: a card that carries resources must return them first, which `returnsOf` does. If `discardable` offers a locked card, compare `lockedAdviser` with `CardRestrictions.LockedAdviserOnly` in the catalog for the ids used.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Horned Mask

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Gates and documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md`
- Modify: `docs/superpowers/specs/2026-09-20-powers-rulings.md`

- [ ] **Step 1: Run every gate**

Run: `./sbtw test`, then `scripts/check-architecture.py`, then `scripts/check-markdown-links.py`.
Expected: all pass. `BackendArchitectureSuite` scans power names against `gameplay/walker` and `gameplay/operations`. If a name collides, rename the power object rather than loosening the scan. Confirm no production file exceeds 800 lines, and that the full suite still passes `GameApplicationServiceSuite` ("powered-playability"), which drives a scripted game: a power that is now usable at a step of that script may add a `usePower` control, and the script must not park on it.

- [ ] **Step 2: Update the design**

In `2026-09-20-powers-design.md`, add slice 1c to the status line ("Slice 0, slice 1a and slice 1c are implemented") with a link to this plan, and in the "Slicing" section add "Slice 1c is planned: see its [plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md)." beside the existing sentence for 1a, each on its own line so that a parallel slice's edit merges cleanly. Answer the "Verify at plan time" item on `Peek`: replace "How the journal surfaces a `Peek` to its viewer (Ivory Eye)" with a one-line result ("A recorded `Peek` replays into `ready.knowledge`, which the presentation layer reads; there is no player-visible log yet").

- [ ] **Step 3: Record results in the rulings appendix**

Under "Slice 1: ACTION powers" and "Slice 1: WAKE powers", add `Implemented (slice 1c)` beside Alchemist, Wolves, Sleight of Hand, Crystal Vial, Ivory Eye and Horned Mask, and add a "Slice 1c implementation notes" list under "Slice 1a implementation notes":

- Ivory Eye offers `Button` options keyed `adviser:<owner>:<slot>`, because a card option for another player's facedown adviser would suppress the decision. The "log line" is not implemented: no player-visible log exists.
- Sleight of Hand flips a mixed target's facedown secrets up, takes one, and flips them back, because a one-secret `Take` from a mixed board is ambiguous.
- Alchemist asks its distribution only when two or more banks hold favor and more than 4 is available in all.
- Horned Mask's adviser limit is a fixed 3, so a Silver Tongue holder can take a third adviser. (Open question, see the report.)
- Horned Mask, Crystal Vial and Ivory Eye are limited by their cards holding the cost, and a Wake use is recorded even when the mask finds nothing to take.
- No locked denizen can be at a site, so the "locked" clause of Horned Mask never applies.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: record slice 1c

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

## Open questions

These are decisions the plan makes by default. Each needs a product answer only if the default is wrong.

1. **Ivory Eye log line.** The ruling promises other players "a log line saying who peeked at whose adviser". No player-visible log exists (ROADMAP action log). The plan implements the privacy half only. Confirm that the log line waits for the action log.
2. **Ivory Eye option shape.** The plan offers `Button`s keyed by owner and slot (no engine change, no card art in the panel). The alternative is a new `DecisionOptionRef.AdviserSlot`, like `RelicSlot`, which touches the model, its codec, the projector and the frontend. Confirm the `Button` shape is acceptable for now.
3. **Single-candidate prompts.** Wolves, Sleight of Hand, Crystal Vial, Ivory Eye and Horned Mask ask their decision whenever at least one candidate exists, as a consent step, even for a single candidate. The rulings say "no decision" only for Alchemist, Fae Merchant and Brass Horse. Confirm.
4. **Horned Mask and Silver Tongue.** Silver Tongue limits its holder to two advisers, but that limit lives only in the card-play tree (`SearchPlayAdviser`). The plan uses a fixed limit of 3, so a Silver Tongue holder could take a third adviser with the mask. Confirm whether the mask must honour the Silver Tongue limit (this needs a shared adviser-limit helper, best done with slice 2's `PlacementRules`).
5. **Horned Mask with nothing to take.** A use with no denizen at the site (or a full area of locked advisers) does nothing but still records the once-per-turn use, per the general rule "the only gate is that the cost is payable". Confirm that the mask should not instead be hidden when it has nothing to take.

## Risks to check while executing

- **Replay equality.** Every suite asserts `replayed(...) == Right(done.state)`. If one fails for an unrelated reason (a boundary event for an Act power), compare the two states before relaxing the assertion.
- **Sleight of Hand's flip-take-flip batch** records three operations on another player's secrets. If a future restriction hooks `FlipSecrets`, it will see them. An engine alternative (a secret side on `Take`) is not needed today.
- **Sleight of Hand needs an adviser.** Card 17 is `adviser-only`, so the power is used from a held adviser, and its cost is placed on that adviser card.
- **`bare` in the Crystal Vial suite** moves a site's cards to their decks to build an empty site. It keeps the inventory whole, but if the map's last site has a homeland edifice the edifice deck grows by one, which is legal.
- **Test-name and id collisions.** Decision ids are unique per power (`power.<name>.<what>`). A parallel slice must not reuse them.
- **The shared `PhasePowerCatalog` vector** is edited by every parallel slice. Resolve a merge by keeping every slice's entries in a single `Vector[PhasePower]`.
- **`Bury.standard(...).filterNot(_.isInstanceOf[Bury])`** in Horned Mask depends on `Bury.standard` returning the returns as separate operations before the `Bury`. `OperationVocabularySuite` pins that shape.

## Self-review

- **Spec coverage.** The rulings for Alchemist, Wolves, Sleight of Hand, Crystal Vial and Ivory Eye (ACTION) and Horned Mask (WAKE) map to Tasks 2 to 6. Fae Merchant, Gambling Hall, Bone Dice, Murky Fountain and Dowsing Sticks belong to slice 1b, and Whistle, Brass Horse and Magic Carpet to slice 1d.
- **Placeholders.** None.
- **Types.** `PowerAnswers.one` / `distribution` / `missing` (Task 1) are used unchanged in Tasks 2 to 6. `TargetsFixture` members used later (`others`, `withSecrets`, `withPawn`, `giveAdviser`, `giveVision`, `withoutAdvisers`, `use`, `answer`, `after`, `awaits`, `pick`, `replayed`, `usableNow`, `offered`, `queryOf`, `boardOf`, `publicBoardOf`) are defined in Task 1. `PaidAction` (slice 1a) is the base of Tasks 1 to 5. `IvoryEye.optionFor` is defined in Task 5 and used in its suite. `HornedMask.denizenDecisionId` and `discardDecisionId` are defined in Task 6 and used in its suite.
