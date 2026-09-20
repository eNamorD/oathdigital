# Powers Slice 0: Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the shared engine changes E1 to E5 that every power in the first powers batch relies on, then check Dazzle, Catacombs and League Treaty against the agreed rulings.

**Architecture:** The engine gains a typed access rule (`PowerAccess`), phase-power costs and card-or-banner sources, a `PayCost` that enforces "place costs only onto empty cards" and settles off-turn payments immediately, and a few operation-vocabulary additions (`Give.required`, `BuryableCard.Vision`, a standard-returns bury helper). No new power is implemented in this slice.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit, the procedure walker (`gameplay.walker`), `OperationPipeline`.

**Spec:** [Design](../specs/2026-09-20-powers-design.md) (engine changes E1 to E5, slice 0) and [rulings](../specs/2026-09-20-powers-rulings.md) (common rules, "Slice 0: verify only").

## Global Constraints

- `BackendArchitectureSuite` caps every production Scala file at 800 lines. `OperationValidator.scala` is at 750, so new validator logic goes in a new file.
- Walker sources (`gameplay/walker/`) must not name any power. Powers (`gameplay/powers/`) must not import `gameplay.walker`.
- Journals are forward-only. New optional wire fields are written only when they differ from the default, and decode with the default when absent, so existing recorded events stay byte-identical.
- Replay re-runs recorded operations through `OperationPipeline`. Anything the pipeline derives from state (off-turn settlement) must be derived again identically from the recorded, canonical operation.
- Run one suite with `./sbtw "testOnly oathdigital.gameplay.SuiteName"`. Run everything with `./sbtw test`.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Doc and code comments are normal prose.

## Decisions the plan makes where the spec was silent

1. **E2 applies to modifiers and persistent rules only.** The catalog marks When Played powers such as Dazzle `persistent: false`, so deriving `resolution` from `persistent` for every power would make Dazzle player-selected and stop it firing. `CatalogResolution.of` is used by modifier and persistent-rule powers. When Played, phase and battle-plan powers keep their own resolution. Task 6 records this in the design doc.
2. **The empty-card rule lives in the validator, not in Muster/Trade.** `MusterSource` keeps its own earlier check because it produces the friendlier `EconomyCardNotEmpty` rejection and drops the option from the projection. Both use the same definition (`Tokens.isEmpty` on the card), and the validator is now the enforcement point for every `PayCost`.
3. **The legacy resolver keeps `RuleSourceAccess`.** `ReviewedPowerInspector` (the pre-walker resolver, still used by `PowerRuntime.requireAudited` and `IgnoredRulesRecorded`) is not changed. `PowerAccess` is used by phase powers and by walker contributions. Unifying the two is deferred.
4. **Catacombs places the relic at the card's own site.** The rulings say "at the card's site". For a site card at a ruled site that differs from the pawn's site, the relic goes to that site. A Catacombs held as an adviser has no site, so it uses the pawn's site.
5. **No `giveOrBurn` operation.** Giving favor to Bandits equals burning it, and a `Give` whose `to` is `Location.SharedBank` already does that. A power that gives to "the ruler" picks `to` itself (`Location.PlayArea(player)` or `Location.SharedBank`). The spec's `giveOrBurn` helper is dropped. The caller decides what an Empire ruler means (unsupported in this batch).

## File Structure

Create:
- `src/main/scala/oathdigital/gameplay/PowerAccess.scala`: E1, the access rule.
- `src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala`: the empty-card check.
- `src/main/scala/oathdigital/gameplay/operations/PayCostSettlement.scala`: off-turn settlement.
- `src/main/scala/oathdigital/gameplay/powers/CatalogResolution.scala`: E2.
- Tests: `PayCostSuite`, `PayCostSettlementSuite`, `PowerAccessSuite`, `PowerKindsCatalogSuite`, `OperationVocabularySuite` (all under `src/test/scala/oathdigital/gameplay/`), `PayCostWireSuite` (`src/test/scala/oathdigital/serialization/`), and Slice 0 conformance additions to the existing Dazzle, Catacombs and League Treaty suites.

Modify:
- `model/CoreOperations.scala`, `model/OperationError.scala`, `model/GameState.scala`.
- `gameplay/operations/OperationPipeline.scala`, `OperationValidator.scala` (one line), `PowerOperations.scala`.
- `gameplay/powerresolver/PhasePower.scala`, `gameplay/phases/PhasePowerProcedure.scala`.
- `gameplay/powers/recover/CatacombsContribution.scala`, `gameplay/powers/rest/LeagueTreatyContribution.scala`.
- `application/PhasePowerProjector.scala`, `serialization/WalkerOperationCodec.scala`.
- `PhasePowerFixture.scala`, `PhasePowerSuite.scala`, `PhasePowerProjectorSuite.scala`.

---

### Task 1: Operation vocabulary (E5)

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`Give`, `BuryableCard`, `Bury`, `Discard`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala` (`OperationRun.canonical`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (`encodeBuryableCard`, `decodeBuryableCard`)
- Test: `src/test/scala/oathdigital/gameplay/OperationVocabularySuite.scala`

**Interfaces:**
- Produces:
  - `Give(piece, giver, from, to, required: Boolean = false)`.
  - `BuryableCard.Vision(id: VisionId)` with `deck = CardDeck.World`.
  - `Bury.standard(card: BuryableCard, from: PositionedLocation, suit: Option[Suit], favor: Int, secrets: Int, actingPlayer: PlayerId): Vector[CoreOperation]`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/OperationVocabularySuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class OperationVocabularySuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val other = current.players.map(_.player).find(_ != actor).get

  private def run(state: ReadyGame, operations: CoreOperation*) =
    OperationPipeline.run(state, operations.toVector,
      OperationPolicy.Permissive)(Right(_))

  test("a default Give shrinks to what the giver holds and a required Give rejects") {
    val funded = base.updateCurrent(_.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(favor = 1)) else p)))
    def give(required: Boolean) = Give(Piece.Favor(3), actor,
      Location.PlayArea(actor), Location.PlayArea(other), required)

    val shrunk = run(funded, give(required = false)).toOption.get
    assertEquals(shrunk.executed, Vector[CoreOperation](Give(Piece.Favor(1),
      actor, Location.PlayArea(actor), Location.PlayArea(other))))
    assert(run(funded, give(required = true)).isLeft)
  }

  test("BuryableCard.Vision buries to the bottom of the world deck") {
    val bury = Bury(BuryableCard.Vision(VisionId("vision:one")),
      PositionedLocation(Location.PlayArea(actor)))
    assertEquals(bury.to, PositionedLocation(Location.Deck(CardDeck.World),
      StackPosition.Bottom))
  }

  test("a Vision adviser can be buried") {
    val vision = current.commonCards.worldDeck.collectFirst {
      case id: VisionId => id
    }.get
    val held = base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck.filterNot(_ == vision)),
      players = c.players.map(p => if (p.player != actor) p else
        p.copy(advisers = Vector(VisionState(vision, Orientation.FaceDown))))))
    val buried = run(held, Bury(BuryableCard.Vision(vision),
      PositionedLocation(Location.PlayArea(actor)))).toOption.get.state
    assertEquals(buried.game.current.commonCards.worldDeck.lastOption,
      Some(vision))
    assert(buried.game.current.players.find(_.player == actor).get
      .advisers.isEmpty)
  }

  test("Bury.standard is the discard's returns followed by the bury") {
    val card = DenizenId("denizen:one")
    val from = PositionedLocation(Location.Site(SiteId("site:one")))
    assertEquals(Bury.standard(BuryableCard.Denizen(card), from,
      Some(Suit.Hearth), favor = 2, secrets = 1, actor), Vector[CoreOperation](
      Move(Piece.Favor(2), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.FavorBank(Suit.Hearth))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.PlayArea(actor))),
      FlipSecrets(actor, 1, SecretSide.FaceUp, SecretSide.FaceDown),
      Bury(BuryableCard.Denizen(card), from)))
    assertEquals(Bury.standard(BuryableCard.Relic(RelicId("relic:one")), from,
      None, favor = 0, secrets = 0, actor),
      Vector[CoreOperation](Bury(BuryableCard.Relic(RelicId("relic:one")), from)))
  }

  test("a buried site denizen returns its favor to the bank and its secrets " +
      "to the acting player facedown") {
    val (siteId, denizen) = current.map.inPlay.iterator.flatMap(id =>
      current.map.sites(id).denizens.collect {
        case d: DenizenState => id -> d }).next()
    val suit = catalog.suitOf(denizen.id).get
    val loaded = base.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(siteId, c.map.sites(siteId).copy(denizens =
        c.map.sites(siteId).denizens.map {
          case d: DenizenState if d.id == denizen.id =>
            d.copy(tokens = Tokens(2, 1))
          case other => other })))))
    val before = loaded.game.current.players.find(_.player == actor).get.board
    val result = run(loaded, Bury.standard(BuryableCard.Denizen(denizen.id),
      PositionedLocation(Location.Site(siteId)), Some(suit), 2, 1, actor): _*)
      .toOption.get.state
    val after = result.game.current.players.find(_.player == actor).get.board
    assertEquals(after.faceDownSecrets, before.faceDownSecrets + 1)
    assertEquals(result.banks.favor.getOrElse(suit, 0), loaded.banks.favor.getOrElse(suit, 0) + 2)
    assertEquals(result.game.current.commonCards.worldDeck.lastOption,
      Some(denizen.id))
  }

  test("a Give to the shared bank moves the favor out of the giver's hands, " +
      "which is how giving to Bandits is modelled") {
    val funded = base.updateCurrent(_.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(favor = 2)) else p)))
    val given = run(funded, Give(Piece.Favor(1), actor,
      Location.PlayArea(actor), Location.SharedBank)).toOption.get
    assertEquals(given.state.game.current.players.find(_.player == actor).get
      .board.favor, 1)
  }
}
```

- [ ] **Step 2: Run the suite to confirm it fails to compile**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationVocabularySuite"`
Expected: compile errors for `Give(... required)`, `BuryableCard.Vision`, `Bury.standard`.

- [ ] **Step 3: Implement in `CoreOperations.scala`**

Change `Give`:

```scala
final case class Give(piece: Piece, giver: PlayerId,
    from: Location, to: Location,
    override val required: Boolean = false)
    extends CoreOperation {
```

Add the Vision case to `BuryableCard`:

```scala
  final case class Vision(id: VisionId) extends BuryableCard {
    override val deck: CardDeck = CardDeck.World
  }
```

Add a companion to `Bury` directly below the class:

```scala
object Bury {
  /** A bury with the returns a discard makes: favor to the suit's bank and
    * secrets to the acting player, facedown. `Bury` alone returns nothing.
    * The returns come first because a card must carry no resources when it
    * enters a deck.
    * `suit` is a fact about the card the caller supplies (the pipeline has no
    * catalog). It may be `None` only when `favor` is zero, as for a relic.
    */
  def standard(card: BuryableCard, from: PositionedLocation,
      suit: Option[Suit], favor: Int, secrets: Int,
      actingPlayer: PlayerId): Vector[CoreOperation] = {
    require(favor == 0 || suit.isDefined, "buried favor needs its suit bank")
    Discard.returns(card.id, suit, favor, secrets, actingPlayer) :+
      Bury(card, from)
  }
}
```

In `object Discard`, add beside `returnedResources`:

```scala
  private[model] def returns(card: CardId, suit: Option[Suit], favor: Int,
      secrets: Int, actingPlayer: PlayerId): Vector[CoreOperation] =
    (positiveMove(favor)(Piece.Favor.apply,
      PositionedLocation(Location.OnCard(card)),
      PositionedLocation(Location.FavorBank(suit.getOrElse(Suit.Arcane)))) ++
      returnedSecrets(card, secrets, actingPlayer)).collect {
      case operation: CoreOperation => operation
    }
```

The `suit.getOrElse` is never reached with a positive favor, because `positiveMove` returns empty for zero and `Bury.standard` requires the suit. Keep the comment above `returns` saying so.

- [ ] **Step 4: Canonicalise `Give` and add the codec case**

In `OperationRun.canonical` (`OperationPipeline.scala`) add `case value: Give => value.copy(required = false)` beside `Replace`.

In `WalkerOperationCodec.scala` add to `encodeBuryableCard`:

```scala
    case BuryableCard.Vision(id) => ujson.Obj("kind" -> "vision",
      "id" -> ujson.Str(id.value))
```

Mirror the exact shape of the neighbouring `BuryableCard.Edifice` case (read it first and copy its field names). Add to `decodeBuryableCard`:

```scala
    case "vision" => Right(BuryableCard.Vision(VisionId(value("id").str)))
```

- [ ] **Step 5: Run the suite and the neighbouring suites**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationVocabularySuite oathdigital.gameplay.CoreOperationsSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. If the Vision bury test fails on a card-index or location rule, read `OperationValidator.cardViolations` and `OperationStateMutation` (`case bury: Bury`) and fix the vision case there. Do not weaken the test.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: add Give.required, BuryableCard.Vision and Bury.standard"
```

---

### Task 2: `PayCost` placement rule (E4, part 1)

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`PayCost`)
- Modify: `src/main/scala/oathdigital/model/OperationError.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala` (one line in `OperationShape.violations`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/PowerOperations.scala` (`Costs`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (`PayCost` encode and decode)
- Test: `src/test/scala/oathdigital/gameplay/PayCostSuite.scala`, `src/test/scala/oathdigital/serialization/PayCostWireSuite.scala`

**Interfaces:**
- Produces:
  - `PayCost(player, placedAt, cost, intoOccupied: Boolean = false, matchingBank: Option[Suit] = None)`.
  - `OperationError.CardOccupied(card: CardId)`, code `"card-occupied"`.
  - `PayCostRules.isEmpty(ready: ReadyGame, card: CardId): Boolean`.
  - `PayCostRules.violations(ready: ReadyGame, operation: CoreOperation): Vector[OperationError]`.
  - `Costs.plan(ready, actor, placedAt, cost, intoOccupied: Boolean = false)` and `Costs.affordable(..., intoOccupied: Boolean = false)`.
  - `Costs.onCard(actor: PlayerId, card: CardId, cost: Cost, catalog: ExecutableCatalog, intoOccupied: Boolean = false): PayCost`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/PayCostSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PayCostSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val (siteId, denizen) = current.map.inPlay.iterator.flatMap(id =>
    current.map.sites(id).denizens.collect {
      case d: DenizenState => id -> d }).next()

  private def withCard(tokens: Tokens): ReadyGame =
    base.updateCurrent(c => c.copy(
      players = c.players.map(p => if (p.player != actor) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3))),
      map = c.map.copy(sites = c.map.sites.updated(siteId,
        c.map.sites(siteId).copy(denizens = c.map.sites(siteId).denizens.map {
          case d: DenizenState if d.id == denizen.id => d.copy(tokens = tokens)
          case other => other })))))

  private def pay(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive)(Right(_))

  private def tokensOn(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case d: DenizenState if d.id == denizen.id => d.tokens }.get

  test("a placed cost is accepted onto an empty card") {
    val state = pay(withCard(Tokens.empty), PayCost(actor,
      Location.OnCard(denizen.id), Cost(favor = 1, secret = 1))).toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("a placed cost onto an occupied card is rejected") {
    assert(pay(withCard(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen.id), Cost(secret = 1))).isLeft)
    assert(pay(withCard(Tokens(0, 1)), PayCost(actor,
      Location.OnCard(denizen.id), Cost(favor = 1))).isLeft)
  }

  test("intoOccupied places onto an occupied card") {
    val state = pay(withCard(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen.id), Cost(secret = 1), intoOccupied = true))
      .toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("a burnt-only cost ignores what the card holds") {
    val state = pay(withCard(Tokens(1, 1)), PayCost(actor,
      Location.OnCard(denizen.id), Cost(secretBurnt = 1))).toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("Costs.affordable applies the same rule") {
    val at = Location.OnCard(denizen.id)
    assert(Costs.affordable(withCard(Tokens.empty), actor, at, Cost(secret = 1)))
    assert(!Costs.affordable(withCard(Tokens(0, 1)), actor, at, Cost(secret = 1)))
    assert(Costs.affordable(withCard(Tokens(0, 1)), actor, at, Cost(secret = 1),
      intoOccupied = true))
  }

  test("Costs.onCard names the card's suit bank") {
    val cost = Costs.onCard(actor, denizen.id, Cost(favor = 1), catalog)
    assertEquals(cost, PayCost(actor, Location.OnCard(denizen.id),
      Cost(favor = 1), matchingBank = catalog.suitOf(denizen.id)))
  }
}
```

Create `src/test/scala/oathdigital/serialization/PayCostWireSuite.scala`:

```scala
package oathdigital.serialization

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{DeltaMeaning, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._

class PayCostWireSuite extends munit.FunSuite {
  private val player = PlayerId("red")
  private def event(operation: CoreOperation): OathEvent = WalkerStepRecorded(
    "0", WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("pay")),
    Vector(operation), Vector.empty)
  private def roundTrip(operation: CoreOperation): ujson.Value = {
    val original = event(operation)
    val encoded = GameEventWire.encodeEvent("walker", catalog.ref, 0, original)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(original))
    ujson.read(encoded)("payload")("ops")(0)
  }

  test("a default PayCost writes neither new field") {
    val op = roundTrip(PayCost(player, Location.OnCard(DenizenId("a")),
      Cost(secret = 1)))
    assert(!op.obj.contains("intoOccupied"))
    assert(!op.obj.contains("matchingBank"))
  }

  test("intoOccupied and matchingBank round-trip") {
    val op = roundTrip(PayCost(player, Location.OnCard(DenizenId("a")),
      Cost(favor = 1), intoOccupied = true, matchingBank = Some(Suit.Order)))
    assertEquals(op("intoOccupied").bool, true)
    assertEquals(op("matchingBank").str, Suit.Order.key)
  }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./sbtw "testOnly oathdigital.gameplay.PayCostSuite oathdigital.serialization.PayCostWireSuite"`
Expected: compile errors for `intoOccupied`, `matchingBank`, `Costs.onCard`.

- [ ] **Step 3: Extend `PayCost`**

```scala
final case class PayCost(player: PlayerId, placedAt: Location, cost: Cost,
    intoOccupied: Boolean = false, matchingBank: Option[Suit] = None)
    extends CoreOperation {
```

Update its scaladoc: `intoOccupied` skips the empty-card rule (battle plans set it); `matchingBank` is the suit bank of the card the cost is paid onto, supplied by the caller because the pipeline has no catalog, and is used only for off-turn settlement (Task 3). The body is unchanged in this task.

- [ ] **Step 4: Add the error and the rule**

In `OperationError.scala`, after `IncompatibleLocation`:

```scala
  final case class CardOccupied(card: CardId) extends OperationError {
    override val code: String = "card-occupied"
    override val detail: String =
      s"${card.value} already holds favor or secrets, so a cost cannot be placed on it"
  }
```

Create `PayCostRules.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** The placement rule for costs: a cost is placed onto a card only when the
  * card is empty. Battle plans opt out with `PayCost.intoOccupied`. Burnt
  * portions never rest on the card, so they are exempt.
  *
  * Muster and Trade check the same fact earlier (`MusterSource`, with its own
  * rejection) so their projection can drop the option. This is the enforcement
  * point for every other `PayCost`.
  */
private[gameplay] object PayCostRules {
  def isEmpty(ready: ReadyGame, card: CardId): Boolean = {
    val at = Location.OnCard(card)
    def held(piece: Piece): Int = OperationStateAdapter.quantity(ready, piece, at)
      .toOption.collect { case AvailableQuantity.Finite(value) => value }
      .getOrElse(0)
    held(Piece.Favor(1)) == 0 && held(Piece.Secrets(1)) == 0
  }

  def violations(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationError] = operation match {
    case pay: PayCost if !pay.intoOccupied && pay.cost.favor + pay.cost.secret > 0 =>
      pay.placedAt match {
        case Location.OnCard(card) if !isEmpty(ready, card) =>
          Vector(OperationError.CardOccupied(card))
        case _ => Vector.empty
      }
    case _ => Vector.empty
  }
}
```

A card whose tokens cannot be read counts as empty here. The move's own shape checks reject an unreadable location.

In `OperationShape.violations` (`OperationValidator.scala`), add one line after `accumulated ++= resourceDescriptionViolations(ready, operation)`:

```scala
    accumulated ++= PayCostRules.violations(ready, operation)
```

- [ ] **Step 4b: Update `Costs`**

In `PowerOperations.scala`:

```scala
object Costs {
  def affordable(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost, intoOccupied: Boolean = false): Boolean =
    plan(ready, actor, placedAt, cost, intoOccupied).isRight

  def plan(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost, intoOccupied: Boolean = false)
      : Either[OathViolation, PayCost] =
    if (cost == Cost.free) Right(PayCost(actor, placedAt, cost))
    else
      for {
        player <- ready.game.current.players.find(_.player == actor)
          .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
        favor = cost.favor + cost.favorBurnt
        secrets = cost.secret + cost.secretBurnt
        _ <- Either.cond(player.board.favor >= favor, (),
          InsufficientFavor(favor, player.board.favor))
        _ <- Either.cond(player.board.faceUpSecrets >= secrets, (),
          InsufficientSecrets(secrets, player.board.faceUpSecrets))
        _ <- validatePlaced(ready, placedAt, cost, intoOccupied)
      } yield PayCost(actor, placedAt, cost, intoOccupied)

  /** The placement every card-sourced cost uses: onto the card, with the
    * card's suit as its off-turn settlement bank. Relics have no suit.
    */
  def onCard(actor: PlayerId, card: CardId, cost: Cost,
      catalog: ExecutableCatalog, intoOccupied: Boolean = false): PayCost =
    PayCost(actor, Location.OnCard(card), cost, intoOccupied,
      catalog.suitOf(card))

  private def validatePlaced(ready: ReadyGame, placedAt: Location,
      cost: Cost, intoOccupied: Boolean): Either[OathViolation, Unit] =
    if (cost.favor + cost.secret == 0) Right(())
    else
      placedAt match {
        case Location.OnCard(id) if statefulCard(ready, id) =>
          Either.cond(intoOccupied || PayCostRules.isEmpty(ready, id), (),
            EconomyCardNotEmpty(id))
        case _ => Left(InvalidEventOrder(
          "placed cost portions require an existing token-bearing card"))
      }
```

Leave `statefulCard` as is. `EconomyCardNotEmpty` is already imported through `OathViolation._`.

- [ ] **Step 5: Codec**

In `WalkerOperationCodec.scala` replace the `PayCost` encode case:

```scala
      case PayCost(player, placedAt, cost, intoOccupied, matchingBank) =>
        val optional: Vector[(String, ujson.Value)] =
          (if (intoOccupied) Vector("intoOccupied" -> (ujson.Bool(true): ujson.Value))
          else Vector.empty) ++
            matchingBank.toVector.map(suit =>
              "matchingBank" -> (ujson.Str(suit.key): ujson.Value))
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "pay-cost", "playerId" -> player.value,
          "placedAt" -> encodeLocation(placedAt),
          "cost" -> encodeCost(cost)) ++ optional)
```

and the decode case:

```scala
      case "pay-cost" => for {
        placedAt <- decodeLocation(value("placedAt"), s"$path.placedAt")
        cost <- decodeCost(value("cost"), s"$path.cost")
        bank <- value.obj.get("matchingBank") match {
          case None | Some(ujson.Null) => Right(None)
          case Some(raw) => decodeSuit(raw.str, s"$path.matchingBank").map(Some(_))
        }
      } yield PayCost(PlayerId(value("playerId").str), placedAt, cost,
        intoOccupied = value.obj.get("intoOccupied").exists(_.bool),
        matchingBank = bank)
```

`decodeSuit(String, String)` exists in this file (used near line 305). If its signature differs, match it.

- [ ] **Step 6: Run the suites, then the whole gameplay tree**

Run: `./sbtw "testOnly oathdigital.gameplay.PayCostSuite oathdigital.serialization.PayCostWireSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: existing suites that placed a cost onto an occupied card now fail with `card-occupied`. For each one, decide whether the test setup was wrong or the rule is. A test that pays a second cost onto the same card needs `intoOccupied = true` only if it is a battle plan. Otherwise fix the setup so the card is empty. Expect the Catacombs, Forge and Economy suites to be the ones to check. Do not weaken `PayCostRules`.

- [ ] **Step 7: Commit**

```bash
git add src/main src/test
git commit -m "feat: place PayCost costs only onto empty cards, with intoOccupied and matchingBank"
```

---

### Task 3: Off-turn settlement (E4, part 2)

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`PayCost.offTurn`, children)
- Create: `src/main/scala/oathdigital/gameplay/operations/PayCostSettlement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/PayCostSettlementSuite.scala`

**Interfaces:**
- Consumes: `PayCost(..., intoOccupied, matchingBank)` from Task 2.
- Produces:
  - `PayCost(..., offTurn: Boolean = false)` as the last parameter. When true, the children are: favor moved straight to `matchingBank`, secrets flipped `FaceUp` to `FaceDown` with `FlipSecrets`, and the burnt portions unchanged.
  - `PayCostSettlement.prepare(ready: ReadyGame, operation: CoreOperation): Either[OathViolation, CoreOperation]`.
  - `OperationRun.canonical` resets `offTurn` to false, so the recorded operation is the requested one.

- [ ] **Step 1: Write the failing tests**

Create `PayCostSettlementSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PayCostSettlementSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val active = current.turn.activePlayer
  private val payer = current.players.map(_.player).find(_ != active).get
  private val (siteId, denizen) = current.map.inPlay.iterator.flatMap(id =>
    current.map.sites(id).denizens.collect {
      case d: DenizenState => id -> d }).next()
  private val suit = catalog.suitOf(denizen.id).get

  private def arranged(cardTokens: Tokens = Tokens.empty): ReadyGame =
    base.updateCurrent(c => c.copy(
      players = c.players.map(p => if (p.player != payer) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3,
          faceDownSecrets = 0))),
      map = c.map.copy(sites = c.map.sites.updated(siteId,
        c.map.sites(siteId).copy(denizens = c.map.sites(siteId).denizens.map {
          case d: DenizenState if d.id == denizen.id => d.copy(tokens = cardTokens)
          case other => other })))))

  private def run(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive)(Right(_))
  private def board(state: ReadyGame, player: PlayerId) =
    state.game.current.players.find(_.player == player).get.board
  private def tokensOn(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case d: DenizenState if d.id == denizen.id => d.tokens }.get
  private def offTurnPay(cost: Cost, bank: Option[Suit] = Some(suit),
      intoOccupied: Boolean = false) = PayCost(payer,
    Location.OnCard(denizen.id), cost, intoOccupied, bank)

  test("an off-turn favor payment goes straight to the matching bank") {
    val start = arranged()
    val done = run(start, offTurnPay(Cost(favor = 2))).toOption.get
    assertEquals(tokensOn(done.state), Tokens.empty)
    assertEquals(board(done.state, payer).favor, 1)
    assertEquals(done.state.banks.favor.getOrElse(suit, 0), start.banks.favor.getOrElse(suit, 0) + 2)
  }

  test("an off-turn secret payment flips facedown instead of resting on the card") {
    val done = run(arranged(), offTurnPay(Cost(secret = 2))).toOption.get
    assertEquals(tokensOn(done.state), Tokens.empty)
    val after = board(done.state, payer)
    assertEquals(after.faceUpSecrets -> after.faceDownSecrets, 1 -> 2)
  }

  test("burnt portions are unchanged off-turn") {
    val start = arranged()
    val done = run(start, offTurnPay(Cost(secretBurnt = 1))).toOption.get
    val after = board(done.state, payer)
    assertEquals(after.faceUpSecrets + after.faceDownSecrets, 2)
  }

  test("an off-turn payment onto an occupied card is allowed, since nothing rests") {
    val done = run(arranged(Tokens(1, 1)), offTurnPay(Cost(favor = 1))).toOption.get
    assertEquals(tokensOn(done.state), Tokens(1, 1))
  }

  test("an off-turn placed favor with no matching bank is rejected") {
    assert(run(arranged(), offTurnPay(Cost(favor = 1), bank = None)).isLeft)
    assert(run(arranged(), offTurnPay(Cost(secret = 1), bank = None)).isRight)
  }

  test("the recorded operation is the requested one, and replay reaches the same state") {
    val start = arranged()
    val requested = offTurnPay(Cost(favor = 1, secret = 1))
    val first = run(start, requested).toOption.get
    assertEquals(first.executed, Vector[CoreOperation](requested))
    val replayed = OperationPipeline.run(start, first.executed,
      OperationPolicy.Permissive)(Right(_)).toOption.get
    assertEquals(replayed.state, first.state)
  }

  test("the active player's payment still rests on the card") {
    val start = arranged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != active) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3)))))
    val done = run(start, PayCost(active, Location.OnCard(denizen.id),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit))).toOption.get
    assertEquals(tokensOn(done.state), Tokens(1, 1))
  }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./sbtw "testOnly oathdigital.gameplay.PayCostSettlementSuite"`
Expected: the first two tests fail (the payment rests on the card). The occupied-card test fails with `card-occupied`.

- [ ] **Step 3: Add `offTurn` and the settled children**

Change the class header and children:

```scala
final case class PayCost(player: PlayerId, placedAt: Location, cost: Cost,
    intoOccupied: Boolean = false, matchingBank: Option[Suit] = None,
    offTurn: Boolean = false) extends CoreOperation {
  require(!offTurn || cost.favor == 0 || matchingBank.isDefined,
    "an off-turn placed favor needs its matching bank")
  override val required: Boolean = true
  override val children: Vector[Operation] =
    placedFavor ++ placedSecrets ++
      favorMove(cost.favorBurnt, Location.SharedBank) ++
      secretMove(cost.secretBurnt, Location.SharedBank)

  // Off turn nothing rests on the card: favor goes straight to the bank and
  // the payer's secrets flip facedown, as a discard would return them.
  private def placedFavor: Vector[Operation] =
    if (offTurn && cost.favor > 0)
      favorMove(cost.favor, Location.FavorBank(matchingBank.get))
    else favorMove(cost.favor, placedAt)
  private def placedSecrets: Vector[Operation] =
    if (cost.secret == 0) Vector.empty
    else if (offTurn) Vector(FlipSecrets(player, cost.secret, SecretSide.FaceUp,
      SecretSide.FaceDown))
    else secretMove(cost.secret, placedAt)
```

The `cost.favor > 0` guard matters: `matchingBank.get` is an argument, so it would be evaluated even for a zero favor, and `matchingBank` may be `None` then. Keep `favorMove` and `secretMove` as they are.

Update the scaladoc: "Off turn (`offTurn`, set only by the pipeline) the placed portions settle immediately instead of resting on the card."

- [ ] **Step 4: Skip the empty check for a settled cost**

In `PayCostRules.violations` change the guard to `if !pay.intoOccupied && !pay.offTurn && ...`.

- [ ] **Step 5: Create `PayCostSettlement.scala`**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Outside its own turn a player pays at once: favor goes straight to the
  * matching suit bank and secrets flip facedown, so nothing rests on the card.
  * The pipeline reads the active player from state and settles every
  * `PayCost` whose payer is somebody else, before validation and execution.
  *
  * The recorded operation is the requested one (`OperationRun.canonical`
  * clears `offTurn`), so replay runs the same function on the same state and
  * expands identically.
  */
private[gameplay] object PayCostSettlement {
  def prepare(ready: ReadyGame,
      operation: CoreOperation): Either[OathViolation, CoreOperation] =
    operation match {
      case pay: PayCost if pay.player != ready.game.current.turn.activePlayer =>
        if (pay.cost.favor > 0 && pay.matchingBank.isEmpty)
          Left(OathViolation.CoreOperationRejected("no-matching-bank",
            "favor paid outside the payer's turn needs the card's suit bank"))
        else Right(pay.copy(offTurn = true))
      case other => Right(other)
    }
}
```

- [ ] **Step 6: Hook the pipeline**

In `OperationPipeline.run` replace the fold body:

```scala
          (result, operation) => result.flatMap { current =>
            PayCostSettlement.prepare(current.state, operation).flatMap { prepared =>
              OperationResolution.resolve(current.state, prepared, validator,
                requireAll)
                .flatMap {
                  case OperationResolution.Skip(reasons) =>
                    Right(current.copy(skipped = current.skipped :+
                      SkippedOperation(operation, reasons)))
                  case OperationResolution.Execute(actual) =>
                    new OperationExecutor().execute(current.state, actual)
                      .left.map(_.toViolation).map(state => current.copy(
                        state = state,
                        executed = current.executed :+
                          OperationRun.canonical(actual)))
                }
            }
          }
```

and add to `OperationRun.canonical`: `case value: PayCost => value.copy(offTurn = false)`.

- [ ] **Step 7: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.PayCostSettlementSuite oathdigital.gameplay.PayCostSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.WalkerReplayDriftSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS. A walker suite that pays a cost as a non-active player and expected the cost to rest on the card now sees it settle. Confirm each such case is a genuine off-turn payer, then update the expectation.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat: settle PayCost immediately when the payer is not the active player"
```

---

### Task 4: `PowerAccess` (E1)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/PowerAccess.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` (`accessible` only)
- Modify: `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CatacombsContributionSuite.scala` (expected `PayCost` only)
- Test: `src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala`

**Interfaces:**
- Consumes: `Costs.onCard` (Task 2).
- Produces, all `private[gameplay]`:
  - `PowerAccess.accessible(ref: RuleSourceRef, face: RuleSourceFace, ready: ReadyGame, actor: PlayerId, facedownAdviser: Boolean = false): Boolean`.
  - `PowerAccess.locate(ready: ReadyGame, actor: PlayerId, card: CardId): Option[PowerAccess.Held]` where `Held` is `AtSite(site: SiteId)` or `InPlayArea`.
  - `PowerAccess.siteOf(ready: ReadyGame, actor: PlayerId, card: CardId): Option[SiteId]`: the card's site, or the pawn's site for a card in the play area.
  - `PowerAccess.pawnSite`, `PowerAccess.ruledSites`.

The rule (rulings, "Rules that apply to every power"): cards at your site, cards at sites you rule, and everything in your play area. A site relic never grants access, because relics at sites are always facedown. Both faces of an edifice count. A banner counts only for its holder.

- [ ] **Step 1: Write the failing tests**

Create `PowerAccessSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PowerAccessSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val me = current.players.find(_.player == actor).get
  private val other = current.players.map(_.player).find(_ != actor).get
  private val home = me.pawnSite.get
  private val far = current.map.inPlay.find(_ != home).get
  private val exile = SiteForces.Occupied(ForceKind.Exile(me.lineage), 1)
  private val bandit = SiteForces.Occupied(ForceKind.Bandit, 1)
  private val card = DenizenId(catalog.denizens.head.id.value)

  private def farRuledBy(forces: SiteForces): ReadyGame =
    base.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(far, c.map.sites(far).copy(forces = forces)))))
  private def viaBoard(update: PlayerState => PlayerState): ReadyGame =
    base.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actor) update(p) else p)))

  private def siteCard(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
    RuleSourceRef.SiteCard(site, card), RuleSourceFace.FaceUp, state, actor)

  test("a site card is usable at the pawn's site and at a ruled site only") {
    assert(siteCard(home, base))
    assert(!siteCard(far, farRuledBy(bandit)))
    assert(siteCard(far, farRuledBy(exile)))
  }

  test("a facedown site card is never usable") {
    assert(!PowerAccess.accessible(RuleSourceRef.SiteCard(home, card),
      RuleSourceFace.FaceDown, base, actor))
  }

  test("an edifice is usable on either face, at the pawn's site or a ruled site") {
    Vector(RuleSourceFace.Intact, RuleSourceFace.Ruined).foreach { face =>
      def edifice(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
        RuleSourceRef.Edifice(site, EdificeId("e")), face, state, actor)
      assert(edifice(home, base), face.toString)
      assert(edifice(far, farRuledBy(exile)), face.toString)
      assert(!edifice(far, farRuledBy(bandit)), face.toString)
    }
  }

  test("a relic at a site never grants access") {
    Vector(RuleSourceFace.FaceUp, RuleSourceFace.FaceDown).foreach { face =>
      assert(!PowerAccess.accessible(RuleSourceRef.SiteRelic(home,
        RelicId("r")), face, base, actor))
    }
  }

  test("advisers: your own faceup ones, and facedown ones only when asked") {
    def adviser(owner: PlayerId, face: RuleSourceFace, facedown: Boolean) =
      PowerAccess.accessible(RuleSourceRef.Adviser(owner, card), face, base,
        actor, facedown)
    assert(adviser(actor, RuleSourceFace.FaceUp, facedown = false))
    assert(!adviser(actor, RuleSourceFace.FaceDown, facedown = false))
    assert(adviser(actor, RuleSourceFace.FaceDown, facedown = true))
    assert(!adviser(other, RuleSourceFace.FaceUp, facedown = false))
  }

  test("a relic in your play area must be faceup") {
    def relic(owner: PlayerId, face: RuleSourceFace) = PowerAccess.accessible(
      RuleSourceRef.Relic(owner, RelicId("r")), face, base, actor)
    assert(relic(actor, RuleSourceFace.FaceUp))
    assert(!relic(actor, RuleSourceFace.FaceDown))
    assert(!relic(other, RuleSourceFace.FaceUp))
  }

  test("a banner is usable by its holder only") {
    def banner(state: ReadyGame) = PowerAccess.accessible(
      RuleSourceRef.Banner(Banner.PeoplesFavor.key), RuleSourceFace.GrandCouncil,
      state, actor)
    def heldBy(holder: Option[PlayerId]) = base.updateCurrent(c => c.copy(
      banners = c.banners.copy(peoplesFavor =
        c.banners.peoplesFavor.copy(holder = holder))))
    assert(banner(heldBy(Some(actor))))
    assert(!banner(heldBy(Some(other))))
    assert(!banner(heldBy(None)))
  }

  test("locate finds a card the actor may use, and says where it is") {
    val atHome = base.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(home, c.map.sites(home).copy(denizens =
        Vector(DenizenState(card, Orientation.FaceUp, Tokens.empty)))))))
    assertEquals(PowerAccess.locate(atHome, actor, card),
      Some(PowerAccess.Held.AtSite(home)))
    assertEquals(PowerAccess.siteOf(atHome, actor, card), Some(home))

    val ruledFar = farRuledBy(exile).updateCurrent(c => c.copy(map =
      c.map.copy(sites = c.map.sites.updated(far, c.map.sites(far).copy(
        denizens = Vector(DenizenState(card, Orientation.FaceUp, Tokens.empty)))))))
    assertEquals(PowerAccess.locate(ruledFar, actor, card),
      Some(PowerAccess.Held.AtSite(far)))
    val unruledFar = ruledFar.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(far, c.map.sites(far).copy(forces = bandit)))))
    assertEquals(PowerAccess.locate(unruledFar, actor, card), None)

    val adviser = viaBoard(_.copy(advisers = Vector(DenizenState(card,
      Orientation.FaceUp, Tokens.empty))))
    assertEquals(PowerAccess.locate(adviser, actor, card),
      Some(PowerAccess.Held.InPlayArea))
    assertEquals(PowerAccess.siteOf(adviser, actor, card), Some(home))
    val facedown = viaBoard(_.copy(advisers = Vector(DenizenState(card,
      Orientation.FaceDown, Tokens.empty))))
    assertEquals(PowerAccess.locate(facedown, actor, card), None)
  }

  test("locate finds an edifice at a ruled site, intact or ruined") {
    val edifice = EdificeId(catalog.edifices.head.id.value)
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = farRuledBy(exile).updateCurrent(c => c.copy(map = c.map.copy(
        sites = c.map.sites.updated(far, c.map.sites(far).copy(denizens =
          Vector(EdificeState(edifice, side, Tokens.empty)))))))
      assertEquals(PowerAccess.locate(state, actor, edifice),
        Some(PowerAccess.Held.AtSite(far)), side.toString)
    }
  }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite"`
Expected: compile error, `PowerAccess` does not exist.

- [ ] **Step 3: Create `PowerAccess.scala`**

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The rulebook's access rule for using a card's power: cards at your site,
  * cards at sites you rule, and everything in your play area (relics,
  * banners, advisers and legacies). One definition, used by phase powers and
  * by every walker contribution's `applicable`.
  *
  * Denizens at sites are always faceup and relics at sites are always
  * facedown, so a site relic never grants access. Both faces of an edifice
  * count. Faceup advisers and relics only, unless the caller opts in to
  * facedown advisers (battle plans).
  *
  * The pre-walker resolver (`RuleSourceAccess`, used by `ReviewedPowerInspector`)
  * is a separate, older rule and is deliberately not changed here.
  */
private[gameplay] object PowerAccess {
  sealed trait Held extends Product with Serializable
  object Held {
    final case class AtSite(site: SiteId) extends Held
    case object InPlayArea extends Held
  }

  def pawnSite(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  def ruledSites(ready: ReadyGame, actor: PlayerId): Set[SiteId] = {
    val current = ready.game.current
    current.map.inPlay.filter(id => current.map.sites.get(id).exists(state =>
      SiteRule.ruler(state.forces, current.players).toOption
        .contains(SiteRuler.Player(actor)))).toSet
  }

  private def usableSites(ready: ReadyGame, actor: PlayerId): Set[SiteId] =
    ruledSites(ready, actor) ++ pawnSite(ready, actor)

  def accessible(ref: RuleSourceRef, face: RuleSourceFace, ready: ReadyGame,
      actor: PlayerId, facedownAdviser: Boolean = false): Boolean = {
    lazy val sites = usableSites(ready, actor)
    ref match {
      case RuleSourceRef.Site(id) => pawnSite(ready, actor).contains(id)
      case RuleSourceRef.SiteCard(id, _) =>
        face == RuleSourceFace.FaceUp && sites(id)
      case RuleSourceRef.SiteRelic(_, _) => false
      case RuleSourceRef.Edifice(id, _) =>
        (face == RuleSourceFace.Intact || face == RuleSourceFace.Ruined) &&
          sites(id)
      case RuleSourceRef.Adviser(owner, _) => owner == actor &&
        (face == RuleSourceFace.FaceUp ||
          (facedownAdviser && face == RuleSourceFace.FaceDown))
      case RuleSourceRef.Relic(owner, _) =>
        owner == actor && face == RuleSourceFace.FaceUp
      case RuleSourceRef.Banner(key) => Banner.fromKey(key).exists(banner =>
        BannerRules.holder(ready.game.current, banner).contains(actor))
      case RuleSourceRef.Foundation(_) => true
      case RuleSourceRef.Legacy(lineage, _) =>
        ready.game.current.players.find(_.player == actor)
          .exists(_.lineage == lineage) && face == RuleSourceFace.Active
      case _ => false
    }
  }

  /** Where `card` is, if `actor` may use it. A site card or edifice at a
    * usable site is `AtSite`. A faceup adviser or relic in the actor's play
    * area is `InPlayArea`.
    */
  def locate(ready: ReadyGame, actor: PlayerId, card: CardId): Option[Held] = {
    val current = ready.game.current
    val atSite = usableSites(ready, actor).toVector.sortBy(_.value).collectFirst {
      case id if current.map.sites.get(id).exists(_.denizens.exists {
        case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
        case e: EdificeState => e.id == card
      }) => Held.AtSite(id)
    }
    lazy val inPlay = current.players.find(_.player == actor).exists(player =>
      player.advisers.exists {
        case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
        case _ => false
      } || player.relics.exists(relic =>
        relic.id == card && relic.orientation == Orientation.FaceUp))
    atSite.orElse(Option.when(inPlay)(Held.InPlayArea))
  }

  def siteOf(ready: ReadyGame, actor: PlayerId, card: CardId): Option[SiteId] =
    locate(ready, actor, card).flatMap {
      case Held.AtSite(site) => Some(site)
      case Held.InPlayArea => pawnSite(ready, actor)
    }
}
```

`SiteState.denizens` holds `DenizenState` and `EdificeState` only. If the compiler reports a non-exhaustive match, add the missing case.

- [ ] **Step 4: Point `PhasePowerProcedure.accessible` at it**

Replace the body of `accessible` and delete `rules`:

```scala
  private def accessible(source: IndexedRuleSource, ready: ReadyGame,
      player: PlayerId): Boolean =
    PowerAccess.accessible(source.source, source.face, ready, player)
```

Fix the imports: `import oathdigital.gameplay.{IndexedRuleSource, PowerAccess, RuleSourceIndex}`, and remove `RuleSourceAccess`, `RuleSourceFace` and any other import that is now unused. Update the scaladoc above `accessible` to point at `PowerAccess`.

- [ ] **Step 5: Move Catacombs onto `PowerAccess`**

In `CatacombsContribution.scala` add `import oathdigital.gameplay.PowerAccess` and `import oathdigital.gameplay.operations.Costs`. Replace `applicable`:

```scala
  // Stable across the action: card presence, never the relic/secrets spent.
  override def applicable(ctx: PowerCtx): Boolean =
    PowerAccess.locate(ctx.state, ctx.activePlayer, cardId).isDefined
```

Replace the head of `place` so the relic goes to the card's own site, and use `Costs.onCard`:

```scala
  private def place(actor: PlayerId): Operation = BuildOps((ready, _) => for {
    siteId <- PowerAccess.siteOf(ready, actor, cardId)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- Either.cond(catalog.sites.find(_.id == siteId).exists(d =>
      ready.game.current.map.sites.get(siteId).fold(0)(_.relics.size) < d.relicSlots),
      (), OathViolation.RecoverUnavailable("site has no empty relic slot"))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(OathViolation.RecoverUnavailable("relic deck is empty"))
  } yield Vector[CoreOperation](
    Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      PositionedLocation(Location.Site(siteId)),
      resultingOrientation = Some(Orientation.FaceDown)),
    Costs.onCard(actor, cardId, Cost(secret = 1), catalog)))
```

Update the class comment: the card may sit at the pawn's site, at a site the actor rules, or be an adviser. Say the relic goes to the card's own site, or to the pawn's site for an adviser.

- [ ] **Step 6: Update the tests that pin the old `PayCost` value**

`Costs.onCard` sets `matchingBank`, so the recorded `PayCost` is no longer the bare one. Update:
- `CatacombsContributionSuite.scala` (the `first.ops` assertion): `PayCost(fixture.actor, Location.OnCard(catacombsCard), Cost(secret = 1), matchingBank = catalog.suitOf(catacombsCard))`.
- Every other place that builds Catacombs' expected op vector by hand: run `grep -rn "catacombs" src/test | grep -i PayCost` and fix each the same way. `GameApplicationServiceSuite.scala:519` and `GameEventWireSuite.scala:347` build their own vectors. Change one only if it must equal what the contribution now emits.

- [ ] **Step 7: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite oathdigital.gameplay.PhasePowerSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.application.GameApplicationServiceSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat: add PowerAccess and use it for phase powers and Catacombs"
```

---

### Task 5: Phase-power costs, card-or-banner sources, Act use tracking (E3)

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`PowerSourceRef.Banner`)
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (`record-power-use`)
- Modify: `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PhasePowerFixture.scala`, `PhasePowerSuite.scala`, `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala`
- Test: `PhasePowerSuite.scala`, `PayCostWireSuite.scala`

**Interfaces:**
- Consumes: `Costs.plan`, `Costs.onCard` (Task 2), `PowerAccess.accessible` (Task 4).
- Produces:
  - `PhasePower.cost: Cost` (default `Cost.free`).
  - `PowerSourceRef.Banner(banner: Banner)`.
  - `PhasePowerProcedure.PowerSource(power: PhasePower, source: PowerSourceRef, ref: DecisionOptionRef)`.
  - `PhasePowerProcedure.useRef(power: PhasePower, source: PowerSourceRef): PowerUseRef`.
  - `PhasePowerProcedure.sources(...): Vector[(PowerSourceRef, DecisionOptionRef)]`.
  - `PhasePowerProcedure.check(...): Either[OathViolation, PowerSourceRef]`.
  - The tree built for a use is `Sequence(PayCost?, power tree, RecordPowerUse?)`. The `PayCost` is present when `power.cost` is not free and the source is a card. `RecordPowerUse` is present only for Wake and Rest.

- [ ] **Step 1: Extend the test fixture**

In `PhasePowerFixture.scala` give `TestPower` a cost:

```scala
  final case class TestPower(id: PowerId, timing: PowerTiming,
      tree: PlayerId => Operation = _ => BuildOps((_, _) => Right(Vector.empty)),
      override val cost: Cost = Cost.free)
      extends PhasePower {
```

- [ ] **Step 2: Write the failing tests**

Add to `PhasePowerSuite.scala`:

```scala
  private def withSecrets(state: ReadyGame, count: Int): ReadyGame =
    state.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else
        p.copy(board = p.board.copy(faceUpSecrets = count)))))
  private def heldOnCard(state: ReadyGame): Tokens =
    state.game.current.players.find(_.player == actor).get.advisers.collectFirst {
      case d: DenizenState if d.id == card => d.tokens }.get

  test("a costed ACTION power places its cost on its card, leaves no use " +
      "record and is limited only by the empty-card rule") {
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val powers = PhasePowers(Vector(power))
    val funded = withSecrets(inPhase(Phase.Act), 2)
    assertEquals(PhasePowerProcedure.usable(catalog, funded, actor, powers)
      .map(_.ref), Vector(source))

    val used = use(power, Ready(funded)).toOption.get
    val after = ready(used.state)
    assertEquals(heldOnCard(after), Tokens(0, 1))
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    assertEquals(PhasePowerProcedure.usable(catalog, after, actor, powers),
      Vector.empty)
    assert(use(power, used.state).isLeft)
  }

  test("an unaffordable cost makes the power unusable") {
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val broke = withSecrets(inPhase(Phase.Act), 0)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowers(Vector(power))), Vector.empty)
    assert(use(power, Ready(broke)).isLeft)
  }

  test("a free ACTION power is unlimited and records no use") {
    val power = TestPower(powerId, PowerTiming.Act)
    val first = use(power, Ready(inPhase(Phase.Act))).toOption.get
    val second = use(power, first.state).toOption.get
    assertEquals(ready(second.state).game.current.turn.usedPowers,
      Set.empty[PowerUseRef])
  }

  test("a costed WAKE power pays and is still once per turn") {
    val power = TestPower(powerId, PowerTiming.Wake, cost = Cost(secret = 1))
    val used = use(power, Ready(withSecrets(inPhase(Phase.Wake), 2))).toOption.get
    assertEquals(heldOnCard(ready(used.state)), Tokens(0, 1))
    val recorded = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(ready(used.state).game.current.turn.usedPowers.contains(recorded))
    assertEquals(use(power, used.state).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(recorded)))
  }

  test("an edifice at the pawn's site is a source, on either face") {
    val edifice = catalog.edifices.head
    val id = EdificeId(edifice.id.value)
    val printed = catalog.denizens.find(_.id.value == card.value).get.powers
      .find(_.id == powerId).get
    val powered = catalog.copy(edifices = catalog.edifices.map(e =>
      if (e.id == edifice.id) e.copy(
        intact = e.intact.copy(powers = e.intact.powers :+ printed),
        ruined = e.ruined.copy(powers = e.ruined.powers :+ printed)) else e))
    val current = base.game.current
    val home = current.players.find(_.player == actor).get.pawnSite.get
    val power = TestPower(powerId, PowerTiming.Act)
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = base.updateCurrent(c => c.copy(
        turn = TurnState(actor, Phase.Act, Set.empty),
        players = c.players.map(p => if (p.player != actor) p else
          p.copy(advisers = Vector.empty)),
        map = c.map.copy(sites = c.map.sites.updated(home,
          c.map.sites(home).copy(denizens =
            Vector(EdificeState(id, side, Tokens.empty)))))))
      assertEquals(PhasePowerProcedure.usable(powered, state, actor,
        PhasePowers(Vector(power))).map(p => p.source -> p.ref),
        Vector(PowerSourceRef.Card(id) -> DecisionOptionRef.Edifice(id)),
        side.toString)
    }
  }

  test("a banner is a source only for its holder") {
    val bannerPower = PowerId("banner.peoples-favor.grand-council")
    val power = TestPower(bannerPower, PowerTiming.Act)
    def state(holder: Option[PlayerId]) = inPhase(Phase.Act).updateCurrent(c =>
      c.copy(banners = c.banners.copy(peoplesFavor = c.banners.peoplesFavor.copy(
        active = PeoplesFavorFace.GrandCouncil, holder = holder))))
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, state(Some(actor)), actor,
      powers).map(p => p.source -> p.ref), Vector(
      PowerSourceRef.Banner(Banner.PeoplesFavor) ->
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(PhasePowerProcedure.usable(catalog, state(None), actor, powers),
      Vector.empty)
  }
```

Also update the existing assertions in this file for the widened types:
- `PhasePowerProcedure.PowerSource(power, card, source)` becomes `PowerSource(power, PowerSourceRef.Card(card), source)`.
- `PhasePowerProcedure.check(...)` results become `Right(PowerSourceRef.Card(second))`.

Add to `PayCostWireSuite.scala`:

```scala
  test("a banner power use round-trips") {
    val original = RecordPowerUse(PowerUseRef(PowerTiming.Wake,
      PowerSourceRef.Banner(Banner.DarkestSecret), PowerId("banner.test")))
    val op = roundTrip(original)
    assertEquals(op("bannerKey").str, Banner.DarkestSecret.key)
  }
```

- [ ] **Step 3: Run to confirm failure**

Run: `./sbtw "testOnly oathdigital.gameplay.PhasePowerSuite oathdigital.serialization.PayCostWireSuite"`
Expected: compile errors for `cost`, `PowerSourceRef.Banner`, `PowerSource.source`.

- [ ] **Step 4: Implement the model and codec pieces**

`PhasePower.scala`: add `Cost` to the model import and

```scala
  /** What using the power costs, placed onto its source card. The engine
    * prepends the payment and reads "cost payable, including the empty-card
    * rule" as part of `usable`. Free by default. A cost is not accepted from
    * a banner source: banners have no costs.
    */
  def cost: Cost = Cost.free
```

`GameState.scala` inside `object PowerSourceRef`:

```scala
  /** A banner whose printed face power was used. */
  final case class Banner(banner: oathdigital.model.Banner) extends PowerSourceRef
```

`WalkerOperationCodec.scala`: add to the encode `source match`:

```scala
          case PowerSourceRef.Banner(banner) =>
            Vector("bannerKey" -> ujson.Str(banner.key))
```

and replace the decode `source <-` line:

```scala
        source <- (if (value.obj.contains("siteId"))
            Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
          else if (value.obj.contains("bannerKey"))
            Banner.fromKey(value("bannerKey").str).map(PowerSourceRef.Banner(_))
              .toRight(InvalidValue(s"$path.bannerKey", "unknown banner"))
          else decodePowerCard(value("cardKind").str, value("cardId").str,
            s"$path.cardKind").map(PowerSourceRef.Card)
          ): Either[WireError, PowerSourceRef]
```

- [ ] **Step 5: Rewrite `PhasePowerProcedure`**

Replace the file body below the scaladoc (keep `timingOf`, `find`, `single`):

```scala
package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, PowerAccess, RuleSourceIndex}
import oathdigital.gameplay.operations.Costs
import oathdigital.model.OathViolation._
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.model._

/** Use Power (rest-walker spec, Phase powers).
  *
  * {{{
  * Sequence(PayCost(cost, on the source card),   // when the cost is not free
  *   power.build(...),
  *   RecordPowerUse(timing, source, id))         // Wake and Rest only
  * }}}
  *
  * `usable` is the single usability function: the start gate, legal
  * controls, the `phasePowers` projection and the Rest auto-skip all ask it.
  * A power is usable when its source is accessible, its once-per-turn limit
  * (Wake and Rest) is unspent, its cost is payable (including the empty-card
  * rule) and its own `usable` agrees.
  *
  * Act powers have no once-each limit. A costed one is limited only because
  * its card holds the cost afterwards.
  */
object PhasePowerProcedure {
  final case class PowerSource(power: PhasePower, source: PowerSourceRef,
      ref: DecisionOptionRef)

  def timingOf(phase: Phase): Option[PowerTiming] = phase match {
    case Phase.Wake => Some(PowerTiming.Wake)
    case Phase.Act => Some(PowerTiming.Act)
    case Phase.Rest => Some(PowerTiming.Rest)
    case _ => None
  }

  def useRef(power: PhasePower, source: PowerSourceRef): PowerUseRef =
    PowerUseRef(power.timing, source, power.id)

  private def limited(power: PhasePower): Boolean =
    power.timing != PowerTiming.Act

  /** Sources of `power` that `player` can access, in index order. */
  def sources(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      power: PhasePower): Vector[(PowerSourceRef, DecisionOptionRef)] =
    sourcesFrom(RuleSourceIndex.enumerate(catalog, ready), ready, player, power)

  private def sourcesFrom(index: Vector[IndexedRuleSource], ready: ReadyGame,
      player: PlayerId, power: PhasePower)
      : Vector[(PowerSourceRef, DecisionOptionRef)] =
    index.filter(source => source.powerIds.contains(power.id) &&
      PowerAccess.accessible(source.source, source.face, ready, player))
      .flatMap(source => sourceRef(source.source))

  /** The cost is payable from `source`. A free cost is always payable. */
  private def payable(ready: ReadyGame, player: PlayerId, power: PhasePower,
      source: PowerSourceRef): Either[OathViolation, Unit] =
    if (power.cost == Cost.free) Right(())
    else source match {
      case PowerSourceRef.Card(card) => Costs.plan(ready, player,
        Location.OnCard(card), power.cost).map(_ => ())
      case other => Left(InvalidEventOrder(
        s"${power.id.value} cannot charge a cost to $other"))
    }

  def check(catalog: ExecutableCatalog, ready: ReadyGame, requester: PlayerId,
      power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, PowerSourceRef] = {
    val current = ready.game.current
    val active = current.turn.activePlayer
    val phase = current.turn.phase
    for {
      _ <- Either.cond(current.result.isEmpty, (), GameEnded)
      _ <- Either.cond(requester == active, (), WrongPlayer(active, requester))
      _ <- Either.cond(current.walkerPending.isEmpty &&
        current.walkerProcedure.isEmpty, (),
        InvalidEventOrder("a procedure is already pending"))
      _ <- Either.cond(timingOf(phase).contains(power.timing), (),
        InvalidEventOrder(s"${power.id.value} is a ${power.timing} power " +
          s"and cannot be used in the ${phase.productPrefix} phase"))
      found <- sources(catalog, ready, active, power).collectFirst {
        case (found, `source`) => found
      }.toRight(InvalidEventOrder(s"${source.kind}/${source.wireId} is not " +
        s"an accessible source of ${power.id.value}"))
      _ <- Either.cond(!limited(power) ||
        !current.turn.usedPowers.contains(useRef(power, found)), (),
        PowerAlreadyUsed(useRef(power, found)))
      _ <- payable(ready, active, power, found)
      _ <- Either.cond(power.usable(ready, active, source), (),
        InvalidEventOrder(s"${power.id.value} has nothing to do from " +
          s"${source.kind}/${source.wireId}"))
    } yield found
  }

  def usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[PowerSource] = {
    val current = ready.game.current
    val available = current.result.isEmpty &&
      player == current.turn.activePlayer &&
      current.walkerPending.isEmpty && current.walkerProcedure.isEmpty
    if (!available) Vector.empty
    else {
      val index = RuleSourceIndex.enumerate(catalog, ready)
      powers.powers.flatMap { power =>
        if (!timingOf(current.turn.phase).contains(power.timing)) Vector.empty
        else sourcesFrom(index, ready, player, power).collect {
          case (found, ref)
              if (!limited(power) ||
                !current.turn.usedPowers.contains(useRef(power, found))) &&
                payable(ready, player, power, found).isRight &&
                power.usable(ready, player, ref) =>
            PowerSource(power, found, ref)
        }
      }
    }
  }

  def build(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    found <- check(catalog, ready, player, power, source)
    tree <- power.build(ready, player, source)
  } yield assemble(catalog, power, player, found, tree)

  /** Resume skips the gate: the walker is pending and the use is not yet
    * recorded, so only the tree is rebuilt.
    */
  def rebuild(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    found <- sourceOf(source)
    tree <- power.build(ready, player, source)
  } yield assemble(catalog, power, player, found, tree)

  private def assemble(catalog: ExecutableCatalog, power: PhasePower,
      player: PlayerId, source: PowerSourceRef, tree: Operation): Operation = {
    val payment: Vector[Operation] = source match {
      case PowerSourceRef.Card(card) if power.cost != Cost.free =>
        Vector(Costs.onCard(player, card, power.cost, catalog))
      case _ => Vector.empty
    }
    val record: Vector[Operation] =
      if (limited(power)) Vector(RecordPowerUse(useRef(power, source)))
      else Vector.empty
    Sequence(payment ++ Vector(tree) ++ record)
  }

  private def find(id: PowerId, powers: PhasePowers) = powers.find(id)
    .toRight(InvalidEventOrder(s"no phase power is registered for ${id.value}"))

  private def single(id: PowerId, args: Vector[DecisionOptionRef]) = args match {
    case Vector(source) => Right(source)
    case other => Left(InvalidEventOrder(s"using ${id.value} names exactly " +
      s"one source, got ${other.size}"))
  }

  private def sourceRef(source: RuleSourceRef)
      : Option[(PowerSourceRef, DecisionOptionRef)] = source match {
    case RuleSourceRef.SiteCard(_, id: DenizenId) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Denizen(id))
    case RuleSourceRef.Adviser(_, id: DenizenId) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Denizen(id))
    case RuleSourceRef.Relic(_, id) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Relic(id))
    case RuleSourceRef.Edifice(_, id) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Edifice(id))
    case RuleSourceRef.Banner(key) => Banner.fromKey(key).map(banner =>
      PowerSourceRef.Banner(banner) -> DecisionOptionRef.Banner(banner))
    case _ => None
  }

  private def sourceOf(source: DecisionOptionRef)
      : Either[OathViolation, PowerSourceRef] = source match {
    case DecisionOptionRef.Denizen(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Relic(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Edifice(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Banner(banner) => Right(PowerSourceRef.Banner(banner))
    case other => Left(InvalidEventOrder(
      s"${other.kind}/${other.wireId} is not a power source"))
  }
}
```

`RuleSourceRef.SiteRelic` is no longer mapped: it is never accessible.

If the walker rejects a `Sequence` whose first child is a `PayCost` because of how it treats `required` composites, read `ProcedureWalker.scala:358` and the `EconomyTree` cost sequence, which does the same thing, and follow that shape.

- [ ] **Step 6: Projector**

In `PhasePowerProjector.scala` change the call to `printed(usable.source, usable.power.id)` and the helper:

```scala
  private def printed(source: PowerSourceRef, power: PowerId)
      : Option[(String, oathdigital.catalog.CatalogPower)] = source match {
    case PowerSourceRef.Card(id: DenizenId) =>
      catalog.denizens.find(_.id.value == id.value)
        .flatMap(d => d.powers.find(_.id == power).map(d.name -> _))
    case PowerSourceRef.Card(id: RelicId) =>
      catalog.relics.find(_.id.value == id.value)
        .flatMap(r => r.powers.find(_.id == power).map(r.name -> _))
    case PowerSourceRef.Card(id: EdificeId) =>
      catalog.edifices.find(_.id.value == id.value).flatMap(e =>
        Vector(e.intact, e.ruined).flatMap(face =>
          face.powers.find(_.id == power).map(face.name -> _)).headOption)
    case _ => None // Banner faces get their labels with the slice that uses them.
  }
```

Then fix every remaining compile error: `grep -rn "usable\.card\|PowerSource(\|PhasePowerProcedure\.\(check\|sources\|useRef\)" src` lists them. `PhasePowerProjectorSuite.scala` is one.

- [ ] **Step 7: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.PhasePowerSuite oathdigital.application.PhasePowerProjectorSuite oathdigital.serialization.PayCostWireSuite oathdigital.gameplay.RestWalkerSuite oathdigital.gameplay.TakeWealthProcedureSuite"`
Expected: PASS.

Then `./sbtw test`. Expected: PASS. If `DecisionOption.forRef` or the projector cannot render an `Edifice` ref, add the case where `forRef` lives and add a projector test that mirrors the edifice source test above.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat: phase powers carry a cost, accept edifice and banner sources, and Act powers leave usedPowers"
```

---

### Task 6: Activation from the catalog (E2)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/CatalogResolution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`, `rest/LeagueTreatyContribution.scala`
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md` (E2)
- Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Produces:
  - `CatalogResolution.printed(catalog: ExecutableCatalog, id: PowerId): Option[CatalogPower]`.
  - `CatalogResolution.of(catalog: ExecutableCatalog, id: PowerId): PowerResolution`: `Automatic` when the printed power is persistent, else `PlayerSelected`.
  - The catalog table the later slices lean on, pinned by `PowerKindsCatalogSuite`.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Pins the catalog flags the first powers batch depends on. A modifier is
  * catalogued `persistent: false` and a persistent rule `persistent: true`.
  * When Played, ACTION, WAKE, REST and battle-plan powers are not listed: the
  * flag does not decide how they activate.
  */
class PowerKindsCatalogSuite extends munit.FunSuite {
  private val modifiers = Vector("denizen.augury", "relic.truthful-harp",
    "denizen.tents", "denizen.forest-paths", "relic.cup-of-plenty",
    "denizen.rowdy-pub", "relic.dragonskin-drum", "denizen.relic-worship",
    "denizen.knights-errant", "denizen.catacombs", "denizen.wild-cry",
    "denizen.welcoming-party")
  private val persistentRules = Vector("denizen.toll-roads",
    "denizen.grasping-vines", "relic.circlet-of-command", "denizen.gossip",
    "denizen.league-treaty", "denizen.gleaming-armor", "edifice.e28.intact",
    "edifice.e28.ruined")

  private def flag(id: String): Option[Boolean] =
    CatalogResolution.printed(catalog, PowerId(id)).map(_.persistent)

  test("every in-scope modifier is catalogued non-persistent") {
    modifiers.foreach(id => assertEquals(flag(id), Some(false), id))
  }

  test("every in-scope persistent rule is catalogued persistent") {
    persistentRules.foreach(id => assertEquals(flag(id), Some(true), id))
  }

  test("resolution follows the flag") {
    modifiers.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.PlayerSelected, id))
    persistentRules.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.Automatic, id))
  }

  test("Dazzle is a When Played power whatever its flag: it must fire on the " +
      "play, so it does not derive its resolution from the flag") {
    assertEquals(flag("denizen.dazzle"), Some(false))
    assertEquals(oathdigital.gameplay.powers.whenplayed.Dazzle
      .forCatalog(catalog).get.resolution, PowerResolution.Automatic)
  }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: compile error, `CatalogResolution` does not exist.

- [ ] **Step 3: Create `CatalogResolution.scala`**

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.{CatalogPower, ExecutableCatalog}
import oathdigital.model.{PowerId, PowerResolution}

/** How a modifier or a persistent rule activates, read from the catalog: a
  * persistent power is automatic, a non-persistent one is selected by the
  * player at the start of the major action.
  *
  * Only modifiers and persistent rules use this. The catalog also marks When
  * Played powers (Dazzle) `persistent: false`, but those fire on the play and
  * must stay automatic. Phase powers and battle plans do not activate this
  * way either.
  */
object CatalogResolution {
  def printed(catalog: ExecutableCatalog, id: PowerId): Option[CatalogPower] =
    (catalog.denizens.flatMap(_.powers) ++ catalog.relics.flatMap(_.powers) ++
      catalog.edifices.flatMap(e => e.intact.powers ++ e.ruined.powers) ++
      catalog.legacies.flatMap(_.powers)).find(_.id == id)

  def of(catalog: ExecutableCatalog, id: PowerId): PowerResolution =
    if (printed(catalog, id).exists(_.persistent)) PowerResolution.Automatic
    else PowerResolution.PlayerSelected
}
```

- [ ] **Step 4: Use it in the two existing modifier and persistent contributions**

`CatacombsContribution.scala`: replace `override def resolution: PowerResolution = PowerResolution.PlayerSelected` with

```scala
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
```

and import `oathdigital.gameplay.powers.CatalogResolution`. In `LeagueTreatyContribution.scala` add the same override, since the treaty is persistent, so `Automatic`, as before.

- [ ] **Step 5: Record the refinement in the design doc**

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace the body of "E2. Activation from the catalog" with:

```
`CatalogResolution.of` derives a power's `resolution` from `CatalogPower.persistent`: false is `PlayerSelected`, true is `Automatic`. Only modifiers and persistent rules use it. When Played powers (the catalog marks Dazzle `persistent: false`), phase powers and battle plans keep their own resolution, because they do not activate by selection at the start of an action. `PowerKindsCatalogSuite` pins the flags for every in-scope modifier and persistent rule. The product owner corrected Relic Worship to `persistent: false` in the catalog and in `reference/catalog-ingestion`.
```

- [ ] **Step 6: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.gameplay.powers.rest.LeagueTreatySuite"`
Expected: PASS.

```bash
git add src docs
git commit -m "feat: derive modifier and persistent-rule resolution from the catalog"
```

---

### Task 7: Verify Dazzle, Catacombs and League Treaty

The design says slice 0 checks these three against the rulings and reports mismatches. It does not rebuild them. The product owner has since ruled on the two open questions:
- **Dazzle discards ruined edifices** (Hearth and Order, in the actor's region) as well as denizens. This is a small fix to `Dazzle`, done in Step 1. Intact edifices are locked and stay.
- **Catacombs runs as written.** When the card is at a ruled site other than the pawn's, the relic goes to Catacombs' site and Recover continues at the pawn's site. If no relic ends up recovered, that is permitted. It needs no change.

Write conformance tests for the rulings. A test that fails for any other reason is a finding to report.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala`, `src/test/scala/oathdigital/gameplay/CatacombsContributionSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatySuite.scala`
- Modify: `docs/superpowers/specs/2026-09-20-powers-rulings.md` (append a results section)

- [ ] **Step 1: Dazzle, ruined edifices (fix), suits and regions**

First write the failing test in `DazzleSuite`:

```scala
  test("Dazzle discards ruined Hearth and Order edifices, not intact ones or other suits") {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val dazzle = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id == Dazzle.id)).get.id.value)
    val home = current.players.find(_.player == actor).get.pawnSite.get
    def pick(suit: Suit, taken: Set[EdificeId]): EdificeId =
      current.commonCards.edificeDeck.find(id => !taken(id) &&
        catalog.suitOf(id).contains(suit)).get
    val ruinedHearth = pick(Suit.Hearth, Set.empty)
    val intactHearth = pick(Suit.Hearth, Set(ruinedHearth))
    val ruinedBeast = pick(Suit.Beast, Set.empty)
    val placed = Set(ruinedHearth, intactHearth, ruinedBeast)
    val prepared = base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.filterNot(_ == dazzle),
        edificeDeck = c.commonCards.edificeDeck.filterNot(placed)),
      players = c.players.map(p => if (p.player != actor) p else
        p.copy(advisers = p.advisers :+ DenizenState(dazzle,
          Orientation.FaceUp, Tokens.empty))),
      map = c.map.copy(sites = c.map.sites.updated(home,
        c.map.sites(home).copy(denizens = c.map.sites(home).denizens ++ Vector(
          EdificeState(ruinedHearth, EdificeSide.Ruined, Tokens.empty),
          EdificeState(intactHearth, EdificeSide.Intact, Tokens.empty),
          EdificeState(ruinedBeast, EdificeSide.Ruined, Tokens.empty)))))))
    val hook = CardPlayed(dazzle, RuleSourceRef.Adviser(actor, dazzle))
    val finished = ProcedureWalker.advance(prepared, hook, None,
      WalkerPowers(Vector(Dazzle.forCatalog(catalog).get))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    val after = finished.treeless.game.current
    val remaining = after.map.sites(home).denizens.collect {
      case e: EdificeState => e.id }
    assert(!remaining.contains(ruinedHearth), "a ruined Hearth edifice is discarded")
    assert(remaining.contains(intactHearth), "an intact edifice is locked")
    assert(remaining.contains(ruinedBeast), "another suit is not discarded")
    assertEquals(after.commonCards.edificeDeck.lastOption, Some(ruinedHearth))
  }
```

Run it: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.DazzleSuite"`. Expected: this test fails, because `Dazzle` collects denizens only.

Then fix `Dazzle.effects`. Replace the `candidates` value and the fold below it with one pass over each site's cards, in card order, emitting a `Discard.Denizen` for a denizen and a `Discard.RuinedEdifice` for a ruined edifice, both only for Hearth and Order:

```scala
      val candidates = current.map.inPlay.filter(site =>
        current.map.regionOf(site).contains(origin)).flatMap { siteId =>
        current.map.sites.get(siteId).toVector.flatMap(_.denizens.map(siteId -> _))
      }
      candidates.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty)) { case (acc, (siteId, card)) => for {
        operations <- acc
        suit <- catalog.suitOf(card.id).toRight(OathViolation.UnknownWorldCard(card.id))
      } yield if (suit != Suit.Hearth && suit != Suit.Order) operations
      else card match {
        case denizen: DenizenState => operations :+ Discard.Denizen(denizen.id,
          PositionedLocation(Location.Site(siteId)), destination, suit,
          denizen.tokens.favor, denizen.tokens.secrets, actor)
        case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
          operations :+ Discard.RuinedEdifice(edifice.id,
            PositionedLocation(Location.Site(siteId)), suit,
            edifice.tokens.favor, edifice.tokens.secrets, actor)
        case _ => operations
      } }
```

Keep the `DiscardRestrictions` on the `BuildOps`, which already covers `Discard.RuinedEdifice`. Update the class comment to say Dazzle discards Hearth and Order denizens and ruined edifices, and that intact edifices are locked. `UnknownWorldCard` takes a world card id. If it does not accept an edifice id, add `OathViolation.UnknownEdifice(id)` or reuse the nearest existing violation for a missing catalog card, and say which in the commit message.

Re-run the suite. Expected: PASS, including the three existing Dazzle tests.

Then add the suit and region test to `DazzleSuite`:

```scala
  test("Dazzle leaves other suits and other regions alone") {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val dazzle = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id == Dazzle.id)).get.id.value)
    val home = current.players.find(_.player == actor).get.pawnSite.get
    val region = current.map.regionOf(home).get
    val away = current.map.inPlay.find(id =>
      current.map.regionOf(id).exists(_ != region)).get
    def pick(suit: Suit, taken: Set[DenizenId]): DenizenId =
      current.commonCards.worldDeck.collectFirst {
        case id: DenizenId if id != dazzle && !taken(id) &&
          catalog.suitOf(id).contains(suit) => id }.get
    val beast = pick(Suit.Beast, Set.empty)
    val faraway = pick(Suit.Hearth, Set(beast))
    val near = pick(Suit.Hearth, Set(beast, faraway))
    val placed = Set[CardId](dazzle, beast, faraway, near)
    def add(c: CurrentGameState, site: SiteId, id: DenizenId) =
      c.copy(map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          DenizenState(id, Orientation.FaceUp, Tokens.empty)))))
    val prepared = base.updateCurrent { c =>
      val cleared = c.copy(
        commonCards = c.commonCards.copy(worldDeck =
          c.commonCards.worldDeck.filterNot(placed)),
        players = c.players.map(p => if (p.player != actor) p else
          p.copy(advisers = p.advisers :+ DenizenState(dazzle,
            Orientation.FaceUp, Tokens.empty))))
      add(add(add(cleared, home, beast), home, near), away, faraway)
    }
    val hook = CardPlayed(dazzle, RuleSourceRef.Adviser(actor, dazzle))
    val finished = ProcedureWalker.advance(prepared, hook, None,
      WalkerPowers(Vector(Dazzle.forCatalog(catalog).get))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    val after = finished.treeless.game.current.map.sites
    def at(site: SiteId, id: DenizenId) = after(site).denizens.exists(_.id == id)
    assert(at(home, beast), "a Beast card in the region is not discarded")
    assert(!at(home, near), "a Hearth card in the region is discarded")
    assert(at(away, faraway), "a Hearth card in another region is not discarded")
  }
```

- [ ] **Step 2: Catacombs at a ruled site**

Add to the `CatacombsContributionSuite` companion:

```scala
  /** The pawn stands on a Recover site that already holds a facedown relic.
    * The Catacombs card sits at a different in-play site with a free relic
    * slot, which the actor rules. `site` is the Catacombs site.
    */
  def ruledElsewhere(setup: FirstGameSetupRules,
      ruled: Boolean = true): Fixture = {
    val home = relicSite(setup)
    val current = home.ready.game.current
    val lineage = current.players.find(_.player == home.actor).get.lineage
    val far = current.map.inPlay.find(id => id != home.site &&
      catalog.sites.find(_.id == id).exists(_.relicSlots > 0) &&
      current.map.sites(id).relics.isEmpty).get
    val forces =
      if (ruled) SiteForces.Occupied(ForceKind.Exile(lineage), 1)
      else SiteForces.Occupied(ForceKind.Bandit, 1)
    val moved = home.ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites
        .updated(home.site, c.map.sites(home.site).copy(denizens = Vector.empty))
        .updated(far, c.map.sites(far).copy(forces = forces, denizens =
          Vector(DenizenState(catacombsCard, Orientation.FaceUp,
            Tokens.empty)))))))
    home.copy(ready = moved, site = far)
  }
```

and two tests:

```scala
  test("Catacombs at a site the actor rules places the relic at its own site") {
    val fixture = ruledElsewhere(setup)
    val transition = started(fixture, Vector(catacombsId))
    val Ready(after) = transition.state: @unchecked
    val there = after.game.current.map.sites(fixture.site)
    assertEquals(there.relics.map(_.id), Vector(fixture.topRelic))
    assertEquals(there.denizens.head match {
      case d: DenizenState => d.tokens.secrets
      case _ => -1 }, 1)
  }

  test("Catacombs at a site the actor neither rules nor stands on does nothing") {
    val fixture = ruledElsewhere(setup, ruled = false)
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
      Vector(catacombsId)).foreach { transition =>
      val Ready(after) = transition.state: @unchecked
      assertEquals(after.game.current.map.sites(fixture.site).relics,
        Vector.empty)
    }
  }
```

- [ ] **Step 3: League Treaty and edifices**

Add to `LeagueTreatySuite`:

```scala
  test("favor on an edifice in the region moves with the rest, on either face") {
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val owner = offTurn(act)
      val (arrangedReady, site) = arranged(Some(owner), Vector(Suit.Arcane -> 1))
      val edifice = arrangedReady.game.current.commonCards.edificeDeck.head
      val edificeSuit = catalog.suitOf(edifice).get
      val ready = arrangedReady.updateCurrent(c => c.copy(
        commonCards = c.commonCards.copy(edificeDeck =
          c.commonCards.edificeDeck.tail),
        map = c.map.copy(sites = c.map.sites.updated(site,
          c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
            EdificeState(edifice, side, Tokens(2, 0)))))))
      val destinationBank = Suit.all.find(s =>
        s != Suit.Arcane && s != edificeSuit).get
      val destination = LeagueTreatyContribution.destinationDecisionId(ready,
        rester(ready), site, treatyCard)
      val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
        rester(ready), site, treatyCard)
      val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
        rester(ready)).toOption.get
      val chosen = rules.resolveWalker(parked.state, owner, destination,
        DecisionAnswer.ChooseOneAnswer(bank(destinationBank))).toOption.get
      val sources = (Set(Suit.Arcane, edificeSuit) - destinationBank).toVector
      val answer = DecisionAnswer.DistributeAnswer(
        (sources.map(_ -> 0) :+ (destinationBank -> 3)).map {
          case (suit, n) => DistributeAmount(bank(suit), n) })
      val done = rules.resolveWalker(chosen.state, owner, distribution, answer)
        .toOption.get
      assertEquals(banks(done.state)(destinationBank),
        ready.banks.favor(destinationBank) + 3, side.toString)
    }
  }
```

- [ ] **Step 4: Run and read the results**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.DazzleSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.gameplay.powers.rest.LeagueTreatySuite"`
Expected: PASS. A failing test is a finding. Do not weaken the assertion. If the cause is a fixture mistake, fix the fixture. If the cause is behaviour that differs from the ruling, mark the test with munit's `.fail` (`test("name".fail) { ... }`) so the suite stays green and the mismatch is recorded, and list it in Step 5.

- [ ] **Step 5: Append the results to the rulings doc**

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, update the "Slice 0: verify only" table:
- **Dazzle:** "Discards every Hearth and Order denizen and every ruined Hearth or Order edifice at sites in your region, as far as the generic discard rules permit. Intact edifices are locked."
- **Catacombs:** replace the "Likely mismatch" sentence with "Usable from a card at your site, at a site you rule, or held as an adviser. The relic goes to the card's own site (the pawn's site for an adviser), and Recover continues at the pawn's site. If that leaves no relic recovered, that is permitted."

Then append a "Slice 0 verification results" subsection with one line per card. Each line states that the power matches its ruling, or what differs. Also update the design doc's "Verify only" bullet to say Dazzle gained ruined edifices.

- [ ] **Step 6: Commit**

```bash
git add src/test docs
git commit -m "test: verify Dazzle, Catacombs and League Treaty against the rulings"
```

---

### Task 8: Docs and gates

**Files:**
- Modify: docs that describe the changed contracts (found by grep below)
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md` (status line)

- [ ] **Step 1: Find and update the docs**

Run: `grep -rln "PayCost\|usedPowers\|PowerSourceRef\|PhasePower\b\|RuleSourceAccess" docs --include='*.md' | grep -v superpowers/plans`
For each hit that describes behaviour this slice changed, update it:
- `PayCost` places onto an empty card only, with `intoOccupied` and `matchingBank`, and settles immediately off-turn.
- Act phase powers leave `usedPowers`. Wake and Rest keep the once-per-turn limit.
- `PhasePower.cost` exists, and edifices and banners can be sources.

In the design doc, change the status line to add: "Slice 0 (E1 to E5) implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md)."

- [ ] **Step 2: Run every gate**

Run: `./sbtw test`
Expected: PASS, including `BackendArchitectureSuite` (files under 800 lines, no power names in walker sources, no power imports of `gameplay.walker`).

Run: `python3 scripts/check-architecture.py`
Expected: exits 0.

Run: `python3 scripts/check-markdown-links.py`
Expected: no new failures. Report any that exist on `main` separately.

Run: `wc -l src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
Expected: each under 800.

- [ ] **Step 3: Commit**

```bash
git add docs
git commit -m "docs: record Slice 0 foundations"
```

- [ ] **Step 4: Finish**

Use superpowers:finishing-a-development-branch to verify tests and offer merge, PR or keep. Report the Task 7 findings to the product owner in the same message.

---

## Self-review against the spec

| Spec item | Task |
| --- | --- |
| E1 `PowerAccess`, removes the three known deviations | 4 (`PhasePowerProcedure.accessible`, Catacombs `applicable`, site relic branch) |
| E2 activation from the catalog | 6 (refined for When Played) |
| E3 `PhasePower.cost`, `payOnSource`, `usedPowers`, banner source | 5 (`Costs.onCard` is the `payOnSource` helper, added in 2) |
| E4 empty-card rule, `intoOccupied`, `matchingBank`, off-turn settlement | 2, 3 |
| E5 `Give.required`, `BuryableCard.Vision`, standard-returns bury (`giveOrBurn` dropped: a `Give` to `Location.SharedBank` burns) | 1 |
| Verify Dazzle (plus ruined edifices), Catacombs, League Treaty | 7 |
| Verify at plan time: shared-bank secrets unbounded | Task 3's off-turn tests and the Task 1 bury test run against the validator. `quantity(Secrets, SharedBank)` is `Unbounded` (`OperationStateAdapter`), so this is confirmed. |
| Verify at plan time: where Muster and Trade enforce the empty denizen | Decision 2: `MusterSource` (lines 25 and 42). |
| Verify at plan time: fingerprints and window keys (E6) | Slice 2. |
| Verify at plan time: `Location.Site` secrets, `Peek`, `PlaceBannerResource`, `Discard.Relic`, `nodePath`, Fae Merchant | Later slices. |
