# Chronicle Setup Slice 2: Setup on the Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the standalone `FirstGameSetupRules` event machine with setup that runs as an ordinary triggered procedure on the generic procedure walker, reading a `Chronicle` directly instead of a bridged `FirstGameSetupPlan` command.

**Architecture:** `GameCommand.Begin(chronicle, orders)` builds and evolves a `GameStarted` event that materializes the whole table (map, decks, banks, temporary hands) as a `Ready` game in a new `Phase.Setup`, with every player's pawn unplaced and every hand unresolved -- no more `OathState.InProgress`. Right after that event evolves, `OathRules` triggers a declared `Setup` procedure (`TriggeredProcedureRef`, alongside Oathkeeper) on the ordinary walker: for each player in turn order it parks a `Decide` for the pawn site, then a `Decide` for the starting adviser, executing real `Move` operations for both (a small, additive extension to the pawn-move primitive covers the very first placement). A `SetupEnd` window closes the procedure with no contributions yet (slice 3 hooks it), and the tree's last step is `BeginTurn(firstPlayer, Wake)`. The existing generic walker decision panel renders both decisions with no new frontend code, because `Decide`/`DecisionOption.Site`/`DecisionOption.Denizen` are already generic. `FirstGameSetupMaterializer` and the `FirstGameSetupPlan` shape survive as an internal, chronicle-fed materialization helper (slimmed of its `catalog`/legacy-validation fields); `FirstGameSetupRules`, the `PlacePawn`/`ChooseAdviser`/`ResolveCardDecision` commands, their intents, codecs and events, and the bespoke setup projection are deleted.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [docs/superpowers/specs/2026-09-21-chronicle-setup-design.md](../specs/2026-09-21-chronicle-setup-design.md) -- this plan implements slice 2 only ("Setup on the walker" in the spec's "Slices" section). Slice 3 ("Setup powers": E02/E06/E22, both faces) is untouched here; this plan only guarantees the `SetupPawnPlaced`/`SetupEnd` windows exist for it to hook.

## Global Constraints

- Scala 2.13 project; run tests with `./sbtw "testOnly <FQCN>"` for a single suite or `./sbtw test` for everything. Frontend: `./sbtw frontend/test`.
- Layering (enforced by `scripts/check-architecture.py`): `model` must not import `application`, `gameplay`, `persistence`, `serialization` or `server`; `application` must not import `persistence`, `serialization` or `server` (importing `gameplay` and `catalog` from `application` is fine and already done elsewhere). `SetupOrders` (the deal-order companion to `Chronicle` on `GameStarted`) is a plain `model` value for exactly this reason: the gameplay-layer evolve that consumes it must never import `application.FirstGameBootstrapConfig`.
- Every new or modified production file stays well under the 800-line architecture-check limit.
- "Deck order is kept but not meaningful between games" (Chronicle model, slice 1): `Chronicle.worldDeck`/`relicDeck` are the between-game record; `SetupOrders.worldDeckOrder`/`relicOrder` are the concrete per-game deal this Setup actually deals from. For a first game the two happen to agree card-for-card (the generator already produced a validly-shuffled `Chronicle`), but they are recorded as two facts so a later, non-generated Chronicle can supply one without the other's shape constraining it.
- Setup's own validation is deliberately smaller than the deleted `FirstGameSetupRules.validatePlan`: known and unique ids, enough cards to deal, and the per-Homeland edifice a first game requires. No suit-count check, no Vision-packet audit, no "game kind" check -- those are the deleted generator-adjacent invariants the spec explicitly drops from Setup itself ("Setup from a Chronicle", "Validation").
- Full verification gate before finishing: `./sbtw test`, `./sbtw frontend/test`, `scripts/check-architecture.py`, `scripts/check-markdown-links.py`.

---

## Task 1: `Phase.Setup` and the `GameStarted` event family

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`Phase`)
- Modify: `src/main/scala/oathdigital/model/GameEventProtocol.scala` (replace the four legacy setup events with `GameStarted`)
- Modify: `src/main/scala/oathdigital/model/Setup.scala` (add `SetupOrders`; slim `FirstGameSetupPlan`; delete `FirstGameSetupCommand`)
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala` (add `UnsupportedChronicle`)
- Modify: `src/main/scala/oathdigital/model/GameProcedureProtocol.scala` (replace `AwaitingPawn`/`AwaitingAdviser`/`ReadyForFirstTurn` with `AwaitingSetupPawn`/`AwaitingSetupAdviser`)
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (add `SetupPawnPlaced`, `SetupEnd`)
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (add `TriggeredProcedureRef.Setup`)
- Modify: `src/main/scala/oathdigital/model/World.scala` (`AtlasEntry.StoredSite` gains a trailing `edifice` field)
- Test: `src/test/scala/oathdigital/model/GameStartedSuite.scala`

**Interfaces:**
- Produces: `Phase.Setup` (`key = "setup"`); `model.SetupOrders(participants: Vector[FirstGameParticipant], firstPlayer: PlayerId, worldDeckOrder: Vector[WorldCardId], relicOrder: Vector[RelicId])`; `OathEvent.GameStarted(chronicle: Chronicle, orders: SetupOrders)`; `OathViolation.UnsupportedChronicle(reason: String)`; `OathContinue.AwaitingSetupPawn(playerId: PlayerId, decision: DecisionId)` / `AwaitingSetupAdviser(playerId: PlayerId, decision: DecisionId)`; `PowerWindow.SetupPawnPlaced` / `SetupEnd` (both `OtherWindow`); `TriggeredProcedureRef.Setup` (`key = "setup"`).
- Consumes: nothing new; every type above is built from types already in `model`.

- [ ] **Step 1: Add `Phase.Setup`**

In `src/main/scala/oathdigital/model/GameState.scala`, add the case before `Wake` and list it first in `all`:

```scala
sealed trait Phase extends Product with Serializable { def key: String }
object Phase {
  case object Setup extends Phase { val key = "setup" }
  case object Wake extends Phase { val key = "wake" }
  case object Act extends Phase { val key = "act" }
  case object Rest extends Phase { val key = "rest" }
  private[oathdigital] case object RoundEnd extends Phase {
    val key = "round-end"
  }
  private[oathdigital] case object WarExhaustion extends Phase {
    val key = "war-exhaustion"
  }

  val all: Vector[Phase] = Vector(Setup, Wake, Act, Rest, RoundEnd, WarExhaustion)

  def fromKey(key: String): Option[Phase] = all.find(_.key == key)
}
```

- [ ] **Step 2: Replace the legacy setup events with `GameStarted`**

In `src/main/scala/oathdigital/model/GameEventProtocol.scala`, delete `FirstGameStarted`, `GamePawnPlaced`, `StartingAdviserChosen` and `FirstGameCompleted`, and add in their place:

```scala
  /** Replaces `FirstGameStarted` (2026-09-21 Chronicle design, slice 2,
    * "Setup from a Chronicle"). `chronicle` is the between-game record;
    * `orders` is the concrete per-game deal Setup actually deals from --
    * see the Global Constraints note on why the two are recorded
    * separately. Evolving this event alone (before the triggered `Setup`
    * procedure runs a single step) produces a `Ready` game in `Phase.Setup`
    * with every pawn unplaced and every hand unresolved.
    */
  final case class GameStarted(chronicle: Chronicle, orders: SetupOrders)
      extends OathEvent
```

Remove the now-dead `import` of nothing extra is needed (`Chronicle`/`SetupOrders` are both in `oathdigital.model`, already wildcard-imported).

- [ ] **Step 3: Add `SetupOrders`, slim `FirstGameSetupPlan`, delete `FirstGameSetupCommand`**

Rewrite `src/main/scala/oathdigital/model/Setup.scala` in full:

```scala
package oathdigital.model

final case class PlayerColor(value: String) {
  require(value.trim.nonEmpty, "player color must not be blank")
}

final case class FirstGameParticipant(
    playerId: PlayerId,
    lineageId: LineageId,
    color: PlayerColor
)

final case class PawnPlacement(playerId: PlayerId, siteId: SiteId)

/** The concrete per-game deal derived from a Chronicle: who is seated and in
  * what order, and the dealt orders of the two decks a Chronicle's own
  * order is not meaningful for (2026-09-21 Chronicle design, slice 2,
  * "Setup from a Chronicle"). Recorded on `GameStarted` alongside the
  * Chronicle itself, so replay never re-derives either from live state.
  */
final case class SetupOrders(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId]
)

sealed trait FirstGameFoundationProfile extends Product with Serializable
object FirstGameFoundationProfile {
  case object FixedUnaltered extends FirstGameFoundationProfile
}

final case class FirstGameSupportState(
    foundationProfile: FirstGameFoundationProfile,
    firstPlayer: PlayerId
)

/** The validated shape `FirstGameSetupMaterializer` builds a table from.
  * Built fresh from a Chronicle by `GameStartRules.evolve` (slice 2); no
  * longer a command payload, so it carries no `catalog` field -- Chronicle
  * validation now checks ids directly against the live catalog instead of
  * comparing a stored `CatalogRef`.
  *
  * `denizenOrder` deals hands and the six seeded regional discards (CR pp.
  * 6-7); it never contains a Vision. `worldDeckOrder` is `denizenOrder`
  * with the five fixed Vision identities spliced into the 10+2/15+3
  * packets, and becomes the draw deck once dealing is done.
  */
final case class FirstGameSetupPlan(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    orderedSites: Vector[SiteId],
    denizenOrder: Vector[DenizenId],
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId],
    homelandEdifices: Vector[(SiteId, EdificeId)],
    oathkeeperGoal: OathkeeperGoal = OathkeeperGoal.Supremacy
)

/** Pure CR pp. 6-7 table setup, shared by `GameStartRules`. */
final case class FirstGameSetupMaterial(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    tracks: GameTracks,
    favorBanks: Map[Suit, Int],
    temporaryHands: Map[PlayerId, Vector[WorldCardId]]
)
```

- [ ] **Step 4: Add `UnsupportedChronicle`**

In `src/main/scala/oathdigital/model/GameViolation.scala`, add near the other setup-related violations (next to `UnsupportedRestState` is a reasonable home):

```scala
  /** A Chronicle shape Setup does not support yet: a non-empty `world`
    * (needs the Empire), stored denizens or relics on an atlas site (needs
    * placement rules not specified yet), an unknown or duplicate id, or not
    * enough cards to deal (2026-09-21 Chronicle design, slice 2, "Setup
    * from a Chronicle").
    */
  final case class UnsupportedChronicle(reason: String) extends OathViolation
```

- [ ] **Step 5: Replace the legacy setup continuations**

In `src/main/scala/oathdigital/model/GameProcedureProtocol.scala`, delete `AwaitingPawn`, `AwaitingAdviser` and `ReadyForFirstTurn`, and add in their place (same shape as every other per-decision continuation, e.g. `AwaitingRecoverRoll`):

```scala
  final case class AwaitingSetupPawn(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingSetupAdviser(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
```

- [ ] **Step 6: Add the two Setup windows**

In `src/main/scala/oathdigital/model/PowerWindow.scala`, add near `RestStart`/`RestEnd`:

```scala
  /** Where the placing player's site is fixed for this player's Setup turn
    * (2026-09-21 Chronicle design, "Setup powers"): Great Forge and Broken
    * Forge hook here. */
  case object SetupPawnPlaced extends OtherWindow { val key = "setup.pawn-placed" }
  /** Runs once, in site order, after every player has placed a pawn and
    * chosen an adviser: Great Market, Bandit Market and Empty Grounds hook
    * here. */
  case object SetupEnd extends OtherWindow { val key = "setup.end" }
```

- [ ] **Step 7: Add the triggered `Setup` procedure reference**

In `src/main/scala/oathdigital/model/ProcedureRef.scala`, add to `TriggeredProcedureRef`:

```scala
object TriggeredProcedureRef {
  /** Every change of the Oathkeeper title holder at an action boundary. */
  case object Oathkeeper extends TriggeredProcedureRef { val key = "oathkeeper" }
  /** Runs once, right after `GameStarted` evolves (2026-09-21 Chronicle
    * design, slice 2, "Setup on the walker"). No client command starts it. */
  case object Setup extends TriggeredProcedureRef { val key = "setup" }
  val all: Vector[TriggeredProcedureRef] = Vector(Oathkeeper, Setup)
}
```

- [ ] **Step 8: Let a stored Atlas site remember its edifice**

In `src/main/scala/oathdigital/model/World.scala`, widen `AtlasEntry.StoredSite` with a trailing, defaulted field (every existing 3-arg call site keeps compiling unchanged):

```scala
  final case class StoredSite(
      id: SiteId,
      denizens: Vector[SiteDenizenState],
      relics: Vector[RelicState],
      edifice: Option[EdificeId] = None
  ) extends AtlasEntry
```

This is what `GameStartRules` (Task 3) uses to carry a Homeland's edifice into long-term Atlas storage when that Homeland is not one of the 8 sites in play -- "A Homeland outside the first 8 sites keeps its edifice in storage" (spec, "The first-game generator").

- [ ] **Step 9: Compile and fix every exhaustiveness error**

```bash
./sbtw compile
```

Expected: a list of non-exhaustive-match warnings-as-errors (this project promotes them) at every site that matched `Phase` or `OathState.InProgress` without a default case, and every reference to the four deleted events, three deleted continuations, or `FirstGameSetupCommand`. Do **not** fix these yet -- Tasks 2-7 fix each site as part of the work that touches it. This step exists so the rest of this plan has a concrete, compiler-verified checklist; note the reported file/line list in your working notes before continuing (`LegalActionProjector.scala`, `PendingProjector.scala`, `OathRulesWalker.scala`, `phases/PhasePowerProcedure.scala`, `OathLifecycle.scala`, `GameApplicationService.scala`, `GameProjection.scala`, `gameplay/setup/FirstGameSetup.scala`, and the frontend's phase-label rendering are the ones already known from this plan's own research; the compiler may surface others).

- [ ] **Step 10: Write the `GameStarted` shape test**

```scala
package oathdigital.model

class GameStartedSuite extends munit.FunSuite {
  private val participant = FirstGameParticipant(
    PlayerId("p1"), LineageId("lineage-1"), PlayerColor("red"))
  private val orders = SetupOrders(
    Vector(participant), PlayerId("p1"), Vector.empty, Vector.empty)
  private val chronicle = Chronicle(
    atlasBox = Vector.empty, worldDeck = Vector.empty, relicDeck = Vector.empty)

  test("GameStarted carries a Chronicle and its resolved deal order") {
    val event = OathEvent.GameStarted(chronicle, orders)
    assertEquals(event.chronicle, chronicle)
    assertEquals(event.orders.firstPlayer, PlayerId("p1"))
  }

  test("Phase.Setup round-trips through its wire key") {
    assertEquals(Phase.fromKey("setup"), Some(Phase.Setup))
    assertEquals(Phase.Setup.key, "setup")
  }
}
```

- [ ] **Step 11: Run the new suite**

Run: `./sbtw "testOnly oathdigital.model.GameStartedSuite"`
Expected: PASS. The project will not fully compile again until Tasks 2-7 land (Step 9 above), so do not run the full suite yet.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/model/GameEventProtocol.scala \
  src/main/scala/oathdigital/model/Setup.scala \
  src/main/scala/oathdigital/model/GameViolation.scala \
  src/main/scala/oathdigital/model/GameProcedureProtocol.scala \
  src/main/scala/oathdigital/model/PowerWindow.scala \
  src/main/scala/oathdigital/model/ProcedureRef.scala \
  src/main/scala/oathdigital/model/World.scala \
  src/test/scala/oathdigital/model/GameStartedSuite.scala
git commit -m "feat: add the GameStarted event and Phase.Setup model shapes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Initial pawn placement through `Move`

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala` (`movePawn`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala` (`pawnMoveViolation`)
- Test: `src/test/scala/oathdigital/gameplay/operations/OperationStateMutationSuite.scala` (extend)
- Test: `src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala` if it exists, otherwise add to the same suite `OperationStateMutationSuite.scala` covers validator rejections in

**Interfaces:**
- Consumes: `Piece.Pawn`, `Location.PlayArea`, `Location.Site`, `PlayerState.pawnSite: Option[SiteId]` (all already defined).
- Produces: `Move(Piece.Pawn(player), PositionedLocation(Location.PlayArea(player)), PositionedLocation(Location.Site(destination)))` becomes a legal operation exactly once per player, when `pawnSite` is `None`. Every other `Move` shape for a pawn is unchanged (still only `Site -> Site`, still rejected otherwise).

- [ ] **Step 1: Write the failing mutation test**

Find `OperationStateMutationSuite.scala` and add (adjust the fixture-building helper names to whatever the file already uses for a fresh `ReadyGame`/`PlayerState`):

```scala
test("a pawn may move from the player area to a site once, with no prior site") {
  val ready = /* existing fixture helper, with pawnSite = None for the player */
  val result = OperationExecutor.execute(catalog, ready, Vector(
    Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(siteId)))))
  assert(result.isRight)
  assertEquals(
    result.toOption.get.game.current.players.find(_.player == playerId)
      .flatMap(_.pawnSite),
    Some(siteId))
}

test("a pawn already on a site cannot move from the player area again") {
  val ready = /* fixture with pawnSite = Some(otherSite) */
  val result = OperationExecutor.execute(catalog, ready, Vector(
    Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(siteId)))))
  assert(result.isLeft)
}
```

Use whatever this suite's existing helper produces a runnable `ready`/`catalog` from (mirror an existing pawn-`Move` test in the same file for the exact fixture shape and `OperationExecutor` entry point).

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: FAIL on the first new test with `IncompatibleLocation`.

- [ ] **Step 3: Extend `movePawn`**

In `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`, add a case above the existing `Site -> Site` one:

```scala
  private def movePawn(ready: ReadyGame, player: PlayerId,
      from: Location, to: Location): Either[OperationError, ReadyGame] =
    (from, to) match {
      case (Location.PlayArea(source), Location.Site(destination))
          if source == player =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.isEmpty, (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case (Location.Site(source), Location.Site(destination)) =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.contains(source), (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case _ => Left(IncompatibleLocation(Piece.Pawn(player), to))
    }
```

`MissingPiece(Piece.Pawn(player), from)` reads correctly for the new case too: once `pawnSite` is `Some(_)`, the pawn is no longer at `PlayArea(player)` (it is at its site), so the same "the piece is not at the stated `from` location" error applies without a new error type.

- [ ] **Step 4: Extend `pawnMoveViolation`**

In `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`, mirror the same addition:

```scala
  private def pawnMoveViolation(
      ready: ReadyGame,
      player: PlayerId,
      from: Location,
      to: Location,
      state: MovedPieces
  ): (Vector[OperationError], MovedPieces) = (from, to) match {
    case (Location.PlayArea(source), Location.Site(destination))
        if source == player =>
      playerState(ready, player) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val located = state.pawnSites.getOrElse(player, None)
          if (located.nonEmpty)
            (Vector(MissingPiece(Piece.Pawn(player), from)), state)
          else siteState(ready, destination) match {
            case Left(error) => (Vector(error), state)
            case Right(_) => (Vector.empty, state.copy(
              pawnSites = state.pawnSites.updated(player, Some(destination))))
          }
      }
    case (Location.Site(source), Location.Site(destination)) =>
      playerState(ready, player) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val located = state.pawnSites.getOrElse(player, None)
          if (!located.contains(source))
            (Vector(MissingPiece(Piece.Pawn(player), from)), state)
          else siteState(ready, destination) match {
            case Left(error) => (Vector(error), state)
            case Right(_) => (Vector.empty, state.copy(
              pawnSites = state.pawnSites.updated(player, Some(destination))))
          }
      }
    case _ =>
      (Vector(IncompatibleLocation(Piece.Pawn(player), to)), state)
  }
```

Note `located` reads from `state.pawnSites` (the fold's running snapshot, seeded from live `ready.game.current.players`), not from `ready` directly -- this is what lets two setup pawn placements validate correctly inside one batch, exactly like the existing `Site -> Site` case already does for two sequential travels.

- [ ] **Step 5: Run the suite again**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala \
  src/test/scala/oathdigital/gameplay/operations/OperationStateMutationSuite.scala
git commit -m "feat: allow a pawn's first placement as a Move from the player area

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: `GameStartRules` -- Chronicle validation and the fresh `ReadyGame`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/setup/GameStartRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/setup/FirstGameSetupMaterializer.scala` (add `temporaryHands` to `materialize`'s result)
- Modify: `src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala` (delete everything except `FirstGameRulesData`)
- Delete: `src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala` is **not** deleted yet (Task 6 repurposes it) -- do not touch it in this task.
- Test: `src/test/scala/oathdigital/gameplay/setup/GameStartRulesSuite.scala`

**Interfaces:**
- Consumes: `Chronicle`, `SetupOrders`, `FirstGameSetupPlan`, `FirstGameSetupMaterial` (Task 1), `ExecutableCatalog`.
- Produces: `GameStartRules.evolve(catalog: ExecutableCatalog, chronicle: Chronicle, orders: SetupOrders): Either[OathViolation, ReadyGame]` -- the single function Task 5 calls from `OathRules.evolve`'s `GameStarted` case.

- [ ] **Step 1: Trim `FirstGameSetupMaterializer.materialize`**

In `src/main/scala/oathdigital/gameplay/setup/FirstGameSetupMaterializer.scala`, add one field to the final construction (the method already computes everything `handFor` needs):

```scala
  def materialize(plan: FirstGameSetupPlan,
      placements: Vector[PawnPlacement],
      adviserChoices: Vector[(PlayerId, DenizenId)]): FirstGameSetupMaterial = {
    // ... unchanged body above ...
    FirstGameSetupMaterial(players, map,
      CardZones(plan.worldDeckOrder, plan.relicOrder.filterNot(usedRelics),
        catalog.edifices.map(e => EdificeId(e.id.value))
          .filterNot(edifices.values.toSet), Vector.empty,
        initialDiscards(plan, placementMap, adviserMap)),
      BannersState(
        PeoplesFavorState(PeoplesFavorFace.Mob, None, 1),
        DarkestSecretState(DarkestSecretFace.WanderingFlame, None, 1)),
      GameTracks(1, 0, usurperLimited = true), favorBanks(plan),
      temporaryHands = plan.participants.map(p =>
        p.playerId -> handFor(plan, p.playerId)).toMap)
  }
```

This is the only change this file needs: called with `placements = Vector.empty, adviserChoices = Vector.empty` (Setup's fresh state, before any player has acted), `placementMap`/`adviserMap` are already empty, so `players` already come out with `pawnSite = None` and `advisers = Vector.empty` exactly as today -- only the new `temporaryHands` field is added.

- [ ] **Step 2: Trim `FirstGameSetup.scala` to just its rules data**

Rewrite `src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala` in full, deleting `FirstGameSetupRules` and every import it alone needed:

```scala
package oathdigital.gameplay.setup

import oathdigital.model.VisionId

object FirstGameRulesData {
  val visions: Vector[VisionId] = Vector(
    VisionId("vision:vision-of-sanctuary"),
    VisionId("vision:vision-of-rebellion"),
    VisionId("vision:vision-of-faith"),
    VisionId("vision:conspiracy"),
    VisionId("vision:vision-of-conquest")
  )
}
```

- [ ] **Step 3: Write `GameStartRules`**

```scala
package oathdigital.gameplay.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.model.OathViolation._

/**
 * Builds the initial `ReadyGame` a `GameStarted` event evolves into
 * (2026-09-21 Chronicle design, slice 2, "Setup from a Chronicle").
 * Replaces the application-layer `ChronicleFirstGamePlan` bridge into the
 * old `FirstGameSetupPlan` command and the setup half of the deleted
 * `FirstGameSetupRules`.
 *
 * Validation here is only what Setup itself needs: known and unique ids,
 * enough cards to deal, and one edifice per Homeland in play. The old
 * plan's suit-count and Vision-packet audit is the generator's own job now
 * (`FirstGameChronicleGenerator.validate`), checked once at generation
 * time rather than again at every setup.
 */
object GameStartRules {
  def evolve(catalog: ExecutableCatalog, chronicle: Chronicle,
      orders: SetupOrders): Either[OathViolation, ReadyGame] =
    for {
      plan <- buildPlan(catalog, chronicle, orders)
      material = new FirstGameSetupMaterializer(catalog)
        .materialize(plan, Vector.empty, Vector.empty)
      lineages = plan.participants.map(participant =>
        participant.lineageId -> LineageState(
          participant.lineageId, None, Role.Exile, Vector.empty, Vector.empty)
      ).toMap
      foundations = FoundationNumber.all.map(number =>
        number -> FoundationState(FoundationFace.Normal, Set.empty)).toMap
      atlas = AtlasState(chronicle.atlasBox.drop(8).map(stored =>
        AtlasEntry.StoredSite(stored.site, Vector.empty, Vector.empty,
          stored.items.collectFirst { case id: EdificeId => id })))
      game = OathGame(catalog.ref,
        CampaignState(atlas, foundations, lineages, chronicle.reliquary,
          chronicle.dispossessed, Map.empty, OathkeeperGoal.Supremacy,
          EraState(20, lineages.keys.map(_ -> 0).toMap)),
        CurrentGameState(material.players, material.map, material.commonCards,
          material.banners, OathkeeperState(None, TitleSide.Oathkeeper),
          TurnState(orders.firstPlayer, Phase.Setup, Set.empty),
          material.tracks, None, material.temporaryHands))
      problems = DomainValidation.validate(game)
      _ <- Either.cond(problems.isEmpty, (), InvalidAggregate(problems))
    } yield ReadyGame.start(game,
      plan.participants.map(p => p.playerId -> p.color).toMap,
      orders.firstPlayer, material.favorBanks)

  private def buildPlan(catalog: ExecutableCatalog, chronicle: Chronicle,
      orders: SetupOrders): Either[OathViolation, FirstGameSetupPlan] =
    for {
      _ <- Either.cond(chronicle.world.isEmpty, (),
        UnsupportedChronicle("a non-empty world requires the Empire"))
      _ <- Either.cond(chronicle.atlasBox.size >= 8, (),
        UnsupportedChronicle(
          s"at least 8 atlas sites are required, got ${chronicle.atlasBox.size}"))
      _ <- Either.cond(
        chronicle.atlasBox.forall(_.items.forall(_.isInstanceOf[EdificeId])), (),
        UnsupportedChronicle(
          "stored denizens or relics on an atlas site are not supported yet"))
      _ <- validateIds(catalog, chronicle)
      _ <- Either.cond(
        chronicle.worldDeck.size >= 6 + orders.participants.size * 3, (),
        UnsupportedChronicle("not enough world-deck cards to deal"))
      inPlay = chronicle.atlasBox.take(8)
      homelands <- homelandEdifices(catalog, inPlay)
      _ <- Either.cond(
        chronicle.relicDeck.size >= inPlay.map(s => relicSlots(catalog, s.site)).sum,
        (), UnsupportedChronicle("not enough relics to fill site slots"))
      _ <- validateParticipants(orders)
    } yield FirstGameSetupPlan(orders.participants, orders.firstPlayer,
      inPlay.map(_.site), chronicle.worldDeck, orders.worldDeckOrder,
      chronicle.relicDeck, homelands)

  private def duplicate[A](values: Vector[A]): Option[A] = {
    val seen = scala.collection.mutable.HashSet.empty[A]
    values.find(value => !seen.add(value))
  }

  private def validateIds(catalog: ExecutableCatalog, chronicle: Chronicle)
      : Either[OathViolation, Unit] = {
    val siteIds = catalog.sites.map(_.id).toSet
    val edificeIds = catalog.edifices.map(e => EdificeId(e.id.value)).toSet
    val denizenIds = catalog.denizens.map(d => DenizenId(d.id.value)).toSet
    val relicIds = catalog.relics.map(r => RelicId(r.id.value)).toSet
    val atlasSites = chronicle.atlasBox.map(_.site)
    val atlasEdifices = chronicle.atlasBox.flatMap(_.items)
      .collect { case id: EdificeId => id }

    if (atlasSites.exists(id => !siteIds.contains(id)))
      Left(UnsupportedChronicle(
        s"unknown atlas site ${atlasSites.find(id => !siteIds.contains(id)).get.value}"))
    else if (duplicate(atlasSites).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate atlas site ${duplicate(atlasSites).get.value}"))
    else if (atlasEdifices.exists(id => !edificeIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown edifice " +
        s"${atlasEdifices.find(id => !edificeIds.contains(id)).get.value}"))
    else if (chronicle.worldDeck.exists(id => !denizenIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown denizen " +
        s"${chronicle.worldDeck.find(id => !denizenIds.contains(id)).get.value}"))
    else if (duplicate(chronicle.worldDeck).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate world-deck denizen ${duplicate(chronicle.worldDeck).get.value}"))
    else if (chronicle.relicDeck.exists(id => !relicIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown relic " +
        s"${chronicle.relicDeck.find(id => !relicIds.contains(id)).get.value}"))
    else if (duplicate(chronicle.relicDeck).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate relic ${duplicate(chronicle.relicDeck).get.value}"))
    else Right(())
  }

  private def validateParticipants(orders: SetupOrders)
      : Either[OathViolation, Unit] = {
    val playerIds = orders.participants.map(_.playerId)
    val lineageIds = orders.participants.map(_.lineageId)
    val colors = orders.participants.map(_.color)
    if (orders.participants.isEmpty) Left(ParticipantsEmpty)
    else if (duplicate(playerIds).nonEmpty)
      Left(DuplicatePlayer(duplicate(playerIds).get))
    else if (duplicate(lineageIds).nonEmpty)
      Left(DuplicateLineage(duplicate(lineageIds).get))
    else if (duplicate(colors).nonEmpty)
      Left(DuplicateColor(duplicate(colors).get))
    else if (!playerIds.contains(orders.firstPlayer))
      Left(UnknownFirstPlayer(orders.firstPlayer))
    else Right(())
  }

  private def homelandEdifices(catalog: ExecutableCatalog,
      inPlay: Vector[StoredSite])
      : Either[OathViolation, Vector[(SiteId, EdificeId)]] = {
    val sitesById = catalog.sites.map(s => s.id -> s).toMap
    val edificesById = catalog.edifices.map(e => EdificeId(e.id.value) -> e).toMap
    inPlay.foldLeft[Either[OathViolation, Vector[(SiteId, EdificeId)]]](
        Right(Vector.empty)) { (acc, stored) =>
      acc.flatMap { built =>
        homelandSuit(sitesById(stored.site).handlers) match {
          case None => Right(built)
          case Some(suit) =>
            stored.items.collectFirst { case id: EdificeId => id } match {
              case Some(edificeId) if edificesById.get(edificeId)
                    .exists(_.suit == suit) =>
                Right(built :+ (stored.site -> edificeId))
              case Some(edificeId) => Left(UnsupportedChronicle(
                s"edifice ${edificeId.value} at ${stored.site.value} does " +
                  "not match its Homeland suit"))
              case None => Left(UnsupportedChronicle(
                s"Homeland ${stored.site.value} has no stored edifice"))
            }
        }
      }
    }
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)

  private def relicSlots(catalog: ExecutableCatalog, site: SiteId): Int =
    catalog.sites.find(_.id == site).get.relicSlots
}
```

- [ ] **Step 4: Write the `GameStartRulesSuite`**

Base the fixture on `FirstGameSetupFixture.catalog` (already used by `GeneratedFirstGamePlanFactorySuite`) so the 24-site/255-denizen/48-relic catalog facts are real. Cover, at minimum:

```scala
package oathdigital.gameplay.setup

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

class GameStartRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val chronicle = /* build a valid Chronicle the same way
    FirstGameChronicleGeneratorSuite does, or reuse a generated one via
    FirstGameChronicleGenerator.generate with a fixed-seed-free
    ChronicleRandomPort stub if one already exists in that suite's fixtures */
  private val orders = SetupOrders(FirstGameSetupFixture.participants,
    PlayerId("p1"), chronicle.worldDeck, chronicle.relicDeck)

  test("a valid Chronicle evolves into a Ready game in Phase.Setup with no pawns or advisers") {
    val ready = GameStartRules.evolve(catalog, chronicle, orders).toOption.get
    assertEquals(ready.game.current.turn.phase, Phase.Setup)
    assertEquals(ready.game.current.turn.activePlayer, orders.firstPlayer)
    assert(ready.game.current.players.forall(_.pawnSite.isEmpty))
    assert(ready.game.current.players.forall(_.advisers.isEmpty))
    assert(orders.participants.forall(p =>
      ready.game.current.temporaryHands.getOrElse(p.playerId, Vector.empty).size == 3))
  }

  test("a non-empty world is refused") {
    val withWorld = chronicle.copy(world = chronicle.atlasBox.take(1))
    val result = GameStartRules.evolve(catalog, withWorld, orders)
    assert(result.isLeft)
    assert(result.left.toOption.get.isInstanceOf[OathViolation.UnsupportedChronicle])
  }

  test("a stored denizen on an atlas site is refused") {
    val denizen = chronicle.worldDeck.head
    val polluted = chronicle.copy(atlasBox = chronicle.atlasBox.updated(
      0, chronicle.atlasBox.head.copy(items = Vector(denizen))))
    val result = GameStartRules.evolve(catalog, polluted, orders)
    assert(result.isLeft)
  }

  test("too few atlas sites is refused") {
    val short = chronicle.copy(atlasBox = chronicle.atlasBox.take(7))
    val result = GameStartRules.evolve(catalog, short, orders)
    assertEquals(result, Left(OathViolation.UnsupportedChronicle(
      "at least 8 atlas sites are required, got 7")))
  }

  test("an unknown denizen id is refused") {
    val polluted = chronicle.copy(
      worldDeck = chronicle.worldDeck.updated(0, DenizenId("no-such-denizen")))
    assert(GameStartRules.evolve(catalog, polluted, orders).isLeft)
  }
}
```

Adjust the Chronicle-construction comment to whatever fixture the codebase already exposes -- check `FirstGameChronicleGeneratorSuite.scala` and `ChronicleFirstGamePlanSuite.scala` first; both already build a valid `Chronicle` against `FirstGameSetupFixture.catalog` and one of them is the right model to copy rather than re-deriving from scratch.

- [ ] **Step 5: Run the new suite**

Run: `./sbtw "testOnly oathdigital.gameplay.setup.GameStartRulesSuite"`
Expected: PASS. The project as a whole still will not compile (Tasks 4-7 remain); this is expected until Task 7 finishes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/setup/GameStartRules.scala \
  src/main/scala/oathdigital/gameplay/setup/FirstGameSetupMaterializer.scala \
  src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala \
  src/test/scala/oathdigital/gameplay/setup/GameStartRulesSuite.scala
git commit -m "feat: build the initial Ready game directly from a Chronicle

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: The `Setup` walker procedure

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/setup/SetupProcedure.scala`
- Test: `src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala`

**Interfaces:**
- Consumes: `ReadyGame` in `Phase.Setup` with populated `temporaryHands` and every `pawnSite`/`advisers` empty (Task 3's output); the generic walker vocabulary (`Sequence`, `Decide`, `BuildOps`, `Move`, `BeginTurn`, `DecisionQuery.ChooseOne`, `DecisionOption.Site`/`Denizen`) from `CoreOperations.scala`/`Decisions.scala`.
- Produces: `SetupProcedure.build(catalog: ExecutableCatalog, ready: ReadyGame, activePlayer: PlayerId, args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]` and `SetupProcedure.pawnDecisionId(player: PlayerId): String` / `adviserDecisionId(player: PlayerId): String` (both consumed by Task 5's registry entry).

- [ ] **Step 1: Write the failing turn-order/discard test**

```scala
package oathdigital.gameplay.setup

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._

class SetupProcedureSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val ready = /* GameStartRules.evolve(...).toOption.get, three
    participants, same fixture GameStartRulesSuite uses */

  test("each player places a pawn, then chooses an adviser, in turn order") {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val first = ready.game.current.turn.activePlayer

    val parked1 = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get
    val pending1 = parked1 match {
      case oathdigital.gameplay.walker.WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"expected a park, got $other")
    }
    val decide1 = ProcedureWalker.parkedDecide(ready, tree, pending1,
      WalkerPowers.empty).get
    assertEquals(decide1.decisionId, SetupProcedure.pawnDecisionId(first))
    assertEquals(decide1.owner, first)
  }

  test("the whole procedure finishes in Wake of round 1 with three pawns and three advisers") {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val finished = driveToCompletion(ready, tree) // walk every park, answering
      // each Decide with its first offered option -- write this helper by
      // resolving with ProcedureWalker.resolve/advance in a loop until
      // WalkerOutcome.Finished, matching the pattern
      // WalkerReplayDriftSuite.scala or RecoverProcedureSuite.scala already
      // use to drive a multi-park tree to completion in a test
    assertEquals(finished.game.current.turn.phase, Phase.Wake)
    assertEquals(finished.game.current.turn.activePlayer,
      ready.game.current.turn.activePlayer)
    assertEquals(finished.game.current.players.count(_.pawnSite.nonEmpty), 3)
    assertEquals(finished.game.current.players.count(_.advisers.size == 1), 3)
    assert(finished.game.current.temporaryHands.values.forall(_.isEmpty))
  }
}
```

Look at `RecoverProcedureSuite.scala` or `ChallengeProcedureSuite.scala` for the exact "drive a multi-`Decide` tree to completion inside a test" helper this codebase already has (a loop of `advance`/`resolve` alternation); reuse that pattern rather than inventing a new one.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.setup.SetupProcedureSuite"`
Expected: FAIL to compile (`SetupProcedure` does not exist yet).

- [ ] **Step 3: Write `SetupProcedure`**

```scala
package oathdigital.gameplay.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.model.OathViolation._

/**
 * Declared Setup procedure tree for the walker (2026-09-21 Chronicle
 * design, slice 2, "Setup on the walker"). Triggered once, right after
 * `GameStarted` evolves a fresh `Ready` game in `Phase.Setup`; no client
 * command starts it (`TriggeredProcedureRef.Setup`).
 *
 * {{{
 * Sequence(                                              // no window
 *   Sequence(                                            // per participant
 *     Decide(pawn-placement, participant),
 *     BuildOps(place the pawn)                           // window = SetupPawnPlaced
 *     Decide(adviser-choice, participant),
 *     BuildOps(keep one adviser, discard the other two))
 *   ...
 *   Sequence(Vector.empty)                                // window = SetupEnd
 *   BeginTurn(firstPlayer, Wake))
 * }}}
 *
 * Turn order and every hand are already fixed the moment `GameStarted`
 * evolves -- read off `ready.setup.firstPlayer`/`ready.game.current
 * .players`/`.temporaryHands`, never off a start selection -- so `build`
 * doubles as `rebuild`, like Oathkeeper and End Wake: nothing here is a
 * start-only gate, and a resume rebuilds the identical tree against live
 * state.
 *
 * The `SetupEnd` window is declared with no static children (unlike End
 * Wake's undeclared window, on purpose): slice 3's E02/E06/E22 SETUP
 * powers hook it with a `Transform` that adds their own operations: nothing
 * runs there yet.
 */
object SetupProcedure {
  def pawnDecisionId(player: PlayerId): String =
    s"setup.pawn-placement.${player.value}"
  def adviserDecisionId(player: PlayerId): String =
    s"setup.adviser-choice.${player.value}"

  def build(catalog: ExecutableCatalog, ready: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    Either.cond(args.isEmpty, (),
      InvalidEventOrder("Setup selects nothing")).map(_ => tree(ready))

  private def tree(ready: ReadyGame): Operation = {
    val participants = turnOrder(ready)
    val siteOptions = ready.game.current.map.inPlay.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site)))
    val steps = participants.map(playerStep(ready, _, siteOptions))
    val setupEnd = Sequence(Vector.empty, Some(PowerWindow.SetupEnd))
    Sequence(steps :+ setupEnd :+
      BeginTurn(participants.head.playerId, Phase.Wake))
  }

  private def turnOrder(ready: ReadyGame): Vector[FirstGameParticipant] = {
    val order = ready.game.current.players.map(_.player)
    val start = order.indexOf(ready.setup.firstPlayer)
    (order.drop(start) ++ order.take(start)).map { playerId =>
      val player = ready.game.current.players.find(_.player == playerId).get
      FirstGameParticipant(playerId, player.lineage,
        ready.playerColors(playerId))
    }
  }

  private def playerStep(ready: ReadyGame, participant: FirstGameParticipant,
      siteOptions: Vector[DecisionOption.Site]): Operation = {
    val pawnId = pawnDecisionId(participant.playerId)
    val adviserId = adviserDecisionId(participant.playerId)
    val hand = ready.game.current.temporaryHands
      .getOrElse(participant.playerId, Vector.empty)
      .collect { case id: DenizenId => id }
    val handOptions = hand.map(id =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(id)))
    Sequence(Vector(
      Decide(pawnId, participant.playerId,
        DecisionQuery.ChooseOne(siteOptions,
          heading = Some("Choose your starting site"))),
      BuildOps(placePawn(participant.playerId, pawnId),
        window = Some(PowerWindow.SetupPawnPlaced)),
      Decide(adviserId, participant.playerId,
        DecisionQuery.ChooseOne(handOptions,
          heading = Some("Choose your starting adviser"))),
      BuildOps(chooseAdviser(participant.playerId, adviserId))))
  }

  private def placePawn(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (_, pending) => siteAnswer(pending, decisionId).map(site => Vector(
      Move(Piece.Pawn(player), PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.Site(site)))))

  private def siteAnswer(pending: PendingTree, decisionId: String)
      : Either[OathViolation, SiteId] =
    pending.answered.find(_.decisionId == decisionId).map(_.answer) match {
      case Some(DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site))) =>
        Right(site)
      case _ => Left(InvalidEventOrder(
        s"no pawn-placement answer is recorded for $decisionId"))
    }

  private def chooseAdviser(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (ready, pending) => for {
      chosen <- adviserAnswer(pending, decisionId)
      hand = ready.game.current.temporaryHands.getOrElse(player, Vector.empty)
      rejected = hand.filterNot(_ == chosen)
      pawnSite <- ready.game.current.players.find(_.player == player)
        .flatMap(_.pawnSite).toRight(PawnSiteMissing(player))
      region <- ready.game.current.map.regionOf(pawnSite)
        .toRight(InvalidEventOrder(s"${pawnSite.value} is not in play"))
    } yield Move(Piece.Card(chosen), PositionedLocation(Location.Hand(player)),
        PositionedLocation(Location.PlayArea(player)),
        resultingOrientation = Some(Orientation.FaceDown)) +:
      rejected.map(id => Move(Piece.Card(id),
        PositionedLocation(Location.Hand(player)),
        PositionedLocation(Location.RegionalDiscard(nextRegion(region)))))

  private def adviserAnswer(pending: PendingTree, decisionId: String)
      : Either[OathViolation, DenizenId] =
    pending.answered.find(_.decisionId == decisionId).map(_.answer) match {
      case Some(DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(id))) =>
        Right(id)
      case _ => Left(InvalidEventOrder(
        s"no adviser-choice answer is recorded for $decisionId"))
    }

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
}
```

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.setup.SetupProcedureSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/setup/SetupProcedure.scala \
  src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala
git commit -m "feat: declare the Setup walker procedure tree

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Wire `GameCommand.Begin`, `OathRules` and the walker registry

**Files:**
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala` (`GameCommand.Begin`; delete `PlacePawn`, `ChooseAdviser`, `ResolveCardDecision`, `CardDecisionResolution`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala` (delete the `setup` field and its `evolve` fallback; add `GameStarted`/`beginGame`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`continuationIn` needs no new case -- Setup finishes in `Phase.Wake`, already handled; verify only)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (register `TriggeredProcedureRef.Setup`)
- Modify: `src/main/scala/oathdigital/gameplay/OathLifecycle.scala` (drop `_: InProgress` from both matches)
- Modify: `src/main/scala/oathdigital/model/GameStateProtocol.scala` (delete `OathState.InProgress`)
- Test: `src/test/scala/oathdigital/gameplay/GameStartRulesWiringSuite.scala` (or fold into `OathRulesWalkerPowerSuite.scala`/an existing suite that already exercises `OathRules` end-to-end -- check first)

**Interfaces:**
- Consumes: `GameStartRules.evolve` (Task 3), `SetupProcedure.build` (Task 4).
- Produces: `OathRules.beginGame(state: OathState, chronicle: Chronicle, orders: SetupOrders): Either[OathViolation, OathTransition]`, called from `GameApplicationService` (Task 6).

- [ ] **Step 1: Delete `OathState.InProgress`**

In `src/main/scala/oathdigital/model/GameStateProtocol.scala`:

```scala
sealed trait OathState extends Product with Serializable
object OathState {
  case object NoGame extends OathState
  final case class Ready(value: ReadyGame) extends OathState
}
```

- [ ] **Step 2: Fix `OathLifecycle`'s two matches**

In `src/main/scala/oathdigital/gameplay/OathLifecycle.scala`, both `validateReady` and `validateAct` change `case NoGame | _: InProgress => Left(GameNotStarted)` to `case NoGame => Left(GameNotStarted)`.

- [ ] **Step 3: Replace `GameCommand.Begin`'s payload; delete the dead setup commands**

In `src/main/scala/oathdigital/application/GameCommands.scala`:

```scala
  final case class Begin(chronicle: Chronicle, orders: SetupOrders)
      extends GameCommand
```

Delete `PlacePawn`, `ChooseAdviser` (both `GameCommand` cases), the whole `ResolveCardDecision` case and its comment, and the whole `sealed trait CardDecisionResolution`/`object CardDecisionResolution` block at the bottom of the file (its only case, `StartingAdviser`, has no other purpose once the setup-adviser flow moves to the generic walker `Decide`).

- [ ] **Step 4: Delete the corresponding `Authorization` helpers**

In `src/main/scala/oathdigital/application/Authorization.scala`, delete `placePawn`, `chooseAdviser` and `resolveCardDecision` from `AuthorizedPlayer`.

- [ ] **Step 5: Register `TriggeredProcedureRef.Setup` in the walker registry**

In `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, add an import for `oathdigital.gameplay.setup.SetupProcedure` and an entry alongside `TriggeredProcedureRef.Oathkeeper`:

```scala
    TriggeredProcedureRef.Setup -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, awaited, decision) =>
        if (decisionId.startsWith("setup.pawn-placement."))
          Some(OathContinue.AwaitingSetupPawn(awaited, decision))
        else if (decisionId.startsWith("setup.adviser-choice."))
          Some(OathContinue.AwaitingSetupAdviser(awaited, decision))
        else None,
      build = SetupProcedure.build,
      rebuild = SetupProcedure.build)
```

- [ ] **Step 6: Wire `GameStarted` into `OathRules.evolve` and add `beginGame`**

In `src/main/scala/oathdigital/gameplay/OathRules.scala`:

- Delete `private val setup = new FirstGameSetupRules(catalog)` and its import of `oathdigital.gameplay.setup.FirstGameSetupRules`.
- Replace the fallback `case setupEvent => setup.evolve(state, setupEvent)` at the bottom of `evolve`'s match with an explicit case (the match is now exhaustive without a fallback, so remove the `setupEvent =>` wildcard entirely and add):

```scala
      case GameStarted(chronicle, orders) =>
        state match {
          case NoGame =>
            oathdigital.gameplay.setup.GameStartRules
              .evolve(catalog, chronicle, orders).map(Ready)
          case _ => Left(GameAlreadyExists)
        }
```

- Add a new public method (mirrors `handle(state, MinorActionCommand)`'s shape):

```scala
  /** Builds and evolves `GameStarted`, then immediately runs the triggered
    * `Setup` procedure to its first park or its end -- both land in the
    * same command, so a client sees one command produce however many
    * `WalkerStepRecorded` facts Setup's first player's turn takes (2026-09-21
    * Chronicle design, slice 2).
    */
  def beginGame(state: OathState, chronicle: Chronicle, orders: SetupOrders)
      : Either[OathViolation, OathTransition] = state match {
    case NoGame =>
      val event = GameStarted(chronicle, orders)
      for {
        started <- GameplayTransition(state, Vector(event),
          OathContinue.AwaitingSetupPawn(orders.firstPlayer,
            DecisionId(oathdigital.gameplay.setup.SetupProcedure
              .pawnDecisionId(orders.firstPlayer))))(evolve)
        withSetup <- startTriggered(started, TriggeredProcedureRef.Setup)
      } yield withSetup
    case _ => Left(GameAlreadyExists)
  }
```

Check the file's existing imports for `oathdigital.model.OathEvent._`/`OathState._` (already present per the file's header) so `GameStarted`/`NoGame`/`Ready` resolve unqualified; only the two `oathdigital.gameplay.setup.*` references need their full path (or add an import instead, matching this file's existing style -- check whether it already imports specific `gameplay.setup` members before choosing).

- [ ] **Step 7: Verify `continuationIn` needs no change**

Confirm by inspection (no edit expected) that `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`'s `continuationIn` already has a `Phase.Wake => Right(OathContinue.AwaitingWakeAction(actor))` case -- Setup's tree ends with `BeginTurn(firstPlayer, Phase.Wake)`, so the walker's generic `Finished` branch (`walkerTransition`) already produces the correct continuation with no change to this method.

- [ ] **Step 8: Compile**

```bash
./sbtw compile
```

Expected: remaining errors are only in `GameApplicationService.scala`/`GameProjection.scala`/`LegalActionProjector.scala`/`PendingProjector.scala`/`PhasePowerProcedure.scala`/the frontend, and every test file the earlier grep found referencing deleted symbols -- all fixed by Tasks 6-7.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/application/GameCommands.scala \
  src/main/scala/oathdigital/application/Authorization.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/OathRules.scala \
  src/main/scala/oathdigital/gameplay/OathLifecycle.scala \
  src/main/scala/oathdigital/model/GameStateProtocol.scala
git commit -m "feat: run Setup as a triggered walker procedure from OathRules

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Application-layer bootstrap -- factories, the deal-order helper, `prepareBootstrap`

**Files:**
- Modify: `src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala` (repurpose into a total `SetupOrders` builder; drop `ChronicleBridgeFailure`)
- Modify: `src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala` (`FirstGamePlanFactory.build` returns `Chronicle`)
- Modify: `src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala` (same)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (`prepareBootstrap` signature; `applyCommand`'s `Begin`/`PlacePawn`/`ChooseAdviser`/`ResolveCardDecision` cases)
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala`, `AuthenticatedGameRoutes.scala`, `TrustedGameProvisioning.scala` (each call site's two-line change)
- Test: `src/test/scala/oathdigital/application/ChronicleFirstGamePlanSuite.scala` (adjust to the new return shape)
- Test: `src/test/scala/oathdigital/application/DevelopmentFirstGamePlanFactorySuite.scala`, `GeneratedFirstGamePlanFactorySuite.scala` (adjust assertions from `FirstGameSetupPlan` fields to `Chronicle` fields)

**Interfaces:**
- Produces: `trait FirstGamePlanFactory { def build(config: FirstGameBootstrapConfig): Either[BootstrapPlanFailure, Chronicle] }`; `ChronicleFirstGamePlan.dealOrder(chronicle: Chronicle, config: FirstGameBootstrapConfig): SetupOrders` (total, no `Either`); `GameApplicationService.prepareBootstrap(gameId: String, chronicle: Chronicle, config: FirstGameBootstrapConfig): Either[GameApplicationError, PreparedGameBootstrap]`.

- [ ] **Step 1: Repurpose `ChronicleFirstGamePlan` into a total `SetupOrders` builder**

Rewrite `src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala` in full:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

/**
 * Derives the concrete per-game deal (2026-09-21 Chronicle design, slice 2):
 * the five fixed Vision identities spliced into `chronicle.worldDeck`'s
 * 10+2/15+3 packets, dealt after `config.participants.size * 3 + 6` cards
 * are set aside for hands and the seeded regional discards. Total, unlike
 * slice 1's bridge: Setup itself validates card counts against the live
 * catalog (`GameStartRules`); this is pure arithmetic over whatever
 * `chronicle`/`config` it is given, correct-by-construction even when the
 * result later fails that validation (e.g. too few cards).
 */
object ChronicleFirstGamePlan {
  def dealOrder(chronicle: Chronicle, config: FirstGameBootstrapConfig)
      : SetupOrders = {
    val dealt = 6 + config.participants.size * 3
    val remaining = chronicle.worldDeck.drop(dealt)
    val worldDeckOrder: Vector[WorldCardId] =
      remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
        remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
        remaining.drop(25)
    SetupOrders(config.participants, config.firstPlayer, worldDeckOrder,
      chronicle.relicDeck)
  }
}
```

- [ ] **Step 2: `FirstGamePlanFactory.build` returns `Chronicle`**

In `src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala`:

```scala
trait FirstGamePlanFactory {
  def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, Chronicle]
}

final class DevelopmentFirstGamePlanFactory(catalog: ExecutableCatalog)
    extends FirstGamePlanFactory {
  override def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, Chronicle] = devChronicle

  // devChronicle, devAtlasBox, devDenizens, homelandSuit: unchanged bodies.
  // Delete the `ChronicleFirstGamePlan.build(...)` call this method used to
  // make -- `devChronicle` is already the whole return value.
}
```

- [ ] **Step 3: Same for `GeneratedFirstGamePlanFactory`**

In `src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala`, change `build`'s return type to `Either[BootstrapPlanFailure, Chronicle]` and delete its `plan <- ChronicleFirstGamePlan.build(...)` step -- the method now ends at `chronicle <- FirstGameChronicleGenerator.generate(...)` (mapped to `BootstrapPlanFailure`), with the seating shuffle unchanged (it shuffles `config.participants`/`firstPlayer` before generation, which stays exactly as committed in `685b56c` since the shuffled `config` still flows into `FirstGameChronicleGenerator.generate` and now also into `ChronicleFirstGamePlan.dealOrder` at the `prepareBootstrap` call site, Step 5 below).

- [ ] **Step 4: `GameApplicationService.prepareBootstrap` and `applyCommand`**

In `src/main/scala/oathdigital/application/GameApplicationService.scala`:

```scala
  def prepareBootstrap(
      gameId: String,
      chronicle: Chronicle,
      config: FirstGameBootstrapConfig
  ): Either[GameApplicationError, PreparedGameBootstrap] =
    prepareTransition(gameId, rules.initialState,
      GameCommand.Begin(chronicle, ChronicleFirstGamePlan.dealOrder(chronicle, config)),
      0L)
      .map { case (transition, records) => PreparedGameBootstrap(
        records, transition.state, transition.events, transition.continue) }
```

Delete `private val setupRules = new FirstGameSetupRules(catalog)` and its import.

In `applyCommand`'s big match:

```scala
      case GameCommand.Begin(chronicle, orders) =>
        rules.beginGame(state, chronicle, orders)
```

Delete the `GameCommand.PlacePawn`, `GameCommand.ChooseAdviser` cases and the `GameCommand.ResolveCardDecision` case's `CardDecisionResolution.StartingAdviser` branch entirely (`ResolveCardDecision` itself no longer exists as a `GameCommand`, per Task 5 Step 3, so this whole `case GameCommand.ResolveCardDecision(...) => ...` block is deleted, not edited).

- [ ] **Step 5: The three route call sites**

In each of `src/main/scala/oathdigital/server/GameRoutes.scala`, `AuthenticatedGameRoutes.scala` and `TrustedGameProvisioning.scala`, the existing two-step call:

```scala
plan <- planFactory.build(config)
prepared <- service.prepareBootstrap(gameId, plan)
```

(exact local names vary per file -- `plan`/`valid.gameId` in `TrustedGameProvisioning.scala`, `request`/`FirstGameBootstrapMapper.map(request)` in `GameRoutes.scala`) becomes:

```scala
chronicle <- planFactory.build(config)
prepared <- service.prepareBootstrap(gameId, chronicle, config)
```

`config` is already in scope at every one of the three call sites (it is what `planFactory.build` was already called with), so this is a rename (`plan` -> `chronicle`) plus one added argument at each site, not a restructuring.

- [ ] **Step 6: Fix the three factory suites and the bridge suite**

- `ChronicleFirstGamePlanSuite.scala`: change every assertion from inspecting a returned `FirstGameSetupPlan`'s fields to inspecting a returned `SetupOrders`' fields (`participants`, `firstPlayer`, `worldDeckOrder`, `relicOrder`), and drop any test of a `TooFewAtlasSites`/`MissingHomelandEdifice` failure mode (the function is total now; those cases move to `GameStartRulesSuite`, already covered in Task 3).
- `DevelopmentFirstGamePlanFactorySuite.scala` / `GeneratedFirstGamePlanFactorySuite.scala`: change every assertion that reads `plan.denizenOrder`/`plan.orderedSites`/`plan.homelandEdifices`/etc. to read the equivalent `Chronicle` field (`chronicle.worldDeck`, `chronicle.atlasBox.take(8).map(_.site)`, `chronicle.atlasBox.take(8).flatMap(_.items).collect { case id: EdificeId => id }`); the seating-shuffle test (`GeneratedFirstGamePlanFactorySuite`'s "seating order and first player are shuffled" test) reads `config`-shuffle behavior that this task does not touch, so only its plan-vs-chronicle field accesses change, not its assertions' substance.

- [ ] **Step 7: Compile and run the application-layer suites**

```bash
./sbtw compile
./sbtw "testOnly oathdigital.application.*"
```

Expected: PASS (server suites are covered in Task 7, since they also touch the deleted routes/commands).

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/ChronicleFirstGamePlan.scala \
  src/main/scala/oathdigital/application/DevelopmentFirstGamePlanFactory.scala \
  src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/main/scala/oathdigital/server/GameRoutes.scala \
  src/main/scala/oathdigital/server/AuthenticatedGameRoutes.scala \
  src/main/scala/oathdigital/server/TrustedGameProvisioning.scala \
  src/test/scala/oathdigital/application/ChronicleFirstGamePlanSuite.scala \
  src/test/scala/oathdigital/application/DevelopmentFirstGamePlanFactorySuite.scala \
  src/test/scala/oathdigital/application/GeneratedFirstGamePlanFactorySuite.scala
git commit -m "feat: bootstrap games from a Chronicle end to end

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Delete the legacy setup machinery

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/setup/FirstGameSetupRules.scala` -- **note:** per Task 3 Step 2 this content was already removed from `FirstGameSetup.scala` (the class lived in that file, not a separate one); this task's job is the remaining call sites and dead code, not a second file deletion. Confirm with `grep -rn "FirstGameSetupRules" src/main src/test frontend/src` that nothing outside test files still references it before continuing.
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala` (delete `setupProjection`, the `InProgress` branch of `projectFor`, and `setupMaterializer`)
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala` (delete the `Intent.PlacePawn` case)
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala` (add `case Phase.Setup => Vector.empty`)
- Modify: `src/main/scala/oathdigital/application/PendingProjector.scala` (add `case Phase.Setup => "setup"`)
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` (add whatever `Phase.Setup` arm the compiler demands -- almost certainly "no phase powers usable during Setup")
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala` (delete now-dead cases: `WrongDenizenSuitCount`, `InvalidHomelandEdifice`, `WrongCount`, `DuplicateComponent`, `UnknownComponent`, `InvalidWorldDeck`, `InvalidRelicOrder`, `SiteNotInPlay`, `AdviserNotInHand`, `GameAlreadyReady`, `CatalogMismatch` -- **verify each is genuinely unused first** with `grep -rn "<CaseName>" src/main src/test frontend/src`, and skip any that still has a live use site; do not delete on assumption)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala` (delete `placePawn`/`chooseAdviser` client calls; verify the generic walker decision panel already covers what these controlled)
- Modify every remaining test file the Task 1 Step 9 compiler run and this task's own compile flagged: `src/test/scala/oathdigital/gameplay/setup/FirstGameSetupRulesSuite.scala` (delete the whole file -- its subject is gone), `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala` (swap its `OathContinue.ReadyForFirstTurn` fixture value for `OathContinue.AwaitingSetupPawn`), and the remaining files this plan's research already found: `ForgeWalkerFixture.scala`, `GameApplicationServiceSuite.scala`, `MembershipAuthorizationServiceSuite.scala`, `ParkedServiceFixture.scala`, `PendingWalkerInvariantSuite.scala`, `WalkerDecisionProjectionSuite.scala`, `AuthenticatedGameRoutesSuite.scala`, `GameHttpWireSuite.scala`, `GameRoutesSuite.scala`, `GameTrustBoundaryRoutesSuite.scala`, `TrustedSeatRoutesSuite.scala`, plus `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`, `ProtocolTestCommands.scala`, `ServerModeUiSuite.scala`.
- Modify: `src/main/scala/oathdigital/serialization/GameEventJsonSupport.scala` (delete the `FirstGameStarted` wire case; add `GameStarted`)

**Interfaces:**
- Consumes: everything landed in Tasks 1-6.
- Produces: a fully compiling, fully green tree with no reference to any of the deleted symbols.

- [ ] **Step 1: Confirm the deletion surface**

```bash
grep -rn "FirstGameSetupRules\|FirstGameSetupCommand\|GameCommand.PlacePawn\|GameCommand.ChooseAdviser\|ResolveCardDecision\|CardDecisionResolution\|FirstGameStarted\|GamePawnPlaced\|StartingAdviserChosen\|FirstGameCompleted\|OathState.InProgress\|AwaitingPawn\|AwaitingAdviser\|ReadyForFirstTurn" src/main src/test frontend/src
```

Every hit is a file this task must edit. Do not proceed past this step by assumption -- the list above was compiled during this plan's own research and may miss a file introduced since.

- [ ] **Step 2: `GameProjection.scala`**

Delete the `case progress: InProgress => setupProjection(...)` branch from `projectFor` (leaving `case NoGame => ...` and `case Ready(ready) => ...`), the whole `setupProjection` method, and `private val setupMaterializer = new FirstGameSetupMaterializer(catalog)`. A `Phase.Setup` game is now an ordinary `Ready` state and flows through the existing `readyProjection` path unchanged -- the generic `walkerDecisions`/`pendingProjector` machinery already renders any parked `Decide`, including Setup's, exactly as it renders Recover's or Search's.

- [ ] **Step 3: `GameIntentMapper.scala`, `LegalActionProjector.scala`, `PendingProjector.scala`, `PhasePowerProcedure.scala`**

Delete `case Intent.PlacePawn(site) => Right(actor.placePawn(SiteId(site)))` from `GameIntentMapper.scala` (the walker's generic `ResolveWalker`/`TreeDecision` intent already covers answering Setup's `Decide` nodes, the same way it already covers Recover's).

In `LegalActionProjector.scala`'s `Phase` match (around line 138-164), add `case Phase.Setup => Vector.empty` -- no player-chosen "legal action" exists during Setup; the walker decision panel drives it entirely.

In `PendingProjector.scala`'s `Phase` match, add `case Phase.Setup => "setup"`.

In `PhasePowerProcedure.scala`, follow the compiler's exhaustiveness error to whichever `Phase` match it flags and add a `Phase.Setup` arm returning the same "nothing usable" value its `Phase.RoundEnd`/`Phase.WarExhaustion` arms already return (read those two arms first and mirror their shape exactly).

- [ ] **Step 4: `GameViolation.scala` dead-code sweep**

Run the verification grep from Step 1's pattern, once per candidate case name (`WrongDenizenSuitCount`, `InvalidHomelandEdifice`, `WrongCount`, `DuplicateComponent`, `UnknownComponent`, `InvalidWorldDeck`, `InvalidRelicOrder`, `SiteNotInPlay`, `AdviserNotInHand`, `GameAlreadyReady`, `CatalogMismatch`), and delete only the ones with zero remaining hits outside `GameViolation.scala` itself and outside this plan's own new files.

- [ ] **Step 5: Serialization codec**

In `src/main/scala/oathdigital/serialization/GameEventJsonSupport.scala`, delete the `FirstGameStarted` wire case (`case FirstGameStarted(plan) if plan.catalog != catalog => ...` and any encode/decode branch for it), and add an equivalent `GameStarted` case encoding `chronicle`/`orders` -- follow the exact pattern the file already uses for another event carrying two nested model values (e.g. however `WarExhaustionResolved` or `BanditsRefilled` round-trips a compound payload) rather than inventing a new JSON shape convention. Add or extend a round-trip test in this file's paired suite (`GameEventWireSuite.scala`, already in the earlier grep's file list) asserting `GameStarted(chronicle, orders)` survives encode/decode.

- [ ] **Step 6: Frontend**

In `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`, delete the `placePawn`/`chooseAdviser` command-building helpers and whatever UI affordance called them (a "place pawn on the board" click handler and an "adviser choice" panel distinct from the generic walker decision panel). Confirm with a manual read of `WalkerDecisionProjectionSuite.scala`/the frontend's existing Recover or Search decision-rendering path that a `DecisionQuery.ChooseOne` over `DecisionOption.Site`/`DecisionOption.Denizen` options already renders through the generic panel with no Setup-specific branch -- if the frontend has a `status == "not-started"`/`"awaiting-pawn"`/`"awaiting-adviser"` special case anywhere (mirroring the deleted `GameProjection.setupProjection`'s status strings), delete that branch too, since `GameProjection` no longer emits those statuses.

- [ ] **Step 7: Update every remaining test file**

For each file in this task's header list: remove references to the deleted commands/events/continuations/violations, and where a test specifically exercised the OLD bespoke setup flow (e.g. `FirstGameSetupRulesSuite.scala` in full, or a `GameApplicationServiceSuite.scala` test that drove `Begin`/`PlacePawn`/`ChooseAdviser` end-to-end), replace it with the equivalent drive through `GameCommand.Begin(chronicle, orders)` followed by `GameCommand.ResolveWalker` answers, matching how this same suite already drives Recover or Search end-to-end elsewhere in the file. Delete `FirstGameSetupRulesSuite.scala` outright -- its subject no longer exists, and `GameStartRulesSuite`/`SetupProcedureSuite` (Tasks 3-4) are its replacement.

- [ ] **Step 8: Full compile and full suite**

```bash
./sbtw compile
./sbtw test
./sbtw frontend/test
```

Expected: PASS. Iterate on any remaining reference the earlier greps missed.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: delete the legacy first-game setup event machine

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: End-to-end Setup walker suite and replay determinism

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/setup/GameStartToWakeSuite.scala`

**Interfaces:**
- Consumes: `OathRules.beginGame` (Task 5), everything else already landed.
- Produces: no new production code; this task is verification depth beyond what Tasks 3-4's unit suites already cover.

- [ ] **Step 1: Write the full-journey suite**

```scala
package oathdigital.gameplay.setup

import oathdigital.gameplay.OathRules
import oathdigital.model._

class GameStartToWakeSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val rules = new OathRules(catalog)
  private val chronicle = /* a valid generated-shape Chronicle, as in
    GameStartRulesSuite */
  private val orders = SetupOrders(FirstGameSetupFixture.participants,
    PlayerId("p1"), chronicle.worldDeck, chronicle.relicDeck)

  test("beginGame parks on the first player's pawn-placement decision") {
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    assertEquals(transition.continue,
      OathContinue.AwaitingSetupPawn(orders.firstPlayer,
        DecisionId(SetupProcedure.pawnDecisionId(orders.firstPlayer))))
  }

  test("driving every player's two decisions ends in Wake with the recorded seating") {
    var state: OathState = OathState.NoGame
    var transition = rules.beginGame(state, chronicle, orders).toOption.get
    state = transition.state
    // Loop: read the parked decision off `transition.continue`, resolve it
    // with the first offered option via `rules.resolveWalker`, until
    // `transition.continue` is `OathContinue.AwaitingWakeAction`. Mirror
    // the resolve-loop helper `WalkerReplayDriftSuite.scala` already uses
    // for a multi-park action, rather than reinventing one here.
    assertEquals(transition.continue,
      OathContinue.AwaitingWakeAction(orders.firstPlayer))
    val Ready(ready) = state
    assertEquals(ready.game.current.turn.phase, Phase.Wake)
    assertEquals(ready.game.current.players.count(_.pawnSite.nonEmpty),
      orders.participants.size)
  }

  test("replaying the recorded events reproduces the same Ready state with no RNG") {
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    val replayed = transition.events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.NoGame)) {
      case (Right(state), event) => rules.evolve(state, event)
      case (left, _) => left
    }
    assertEquals(replayed, Right(transition.state))
  }
}
```

- [ ] **Step 2: Run it**

Run: `./sbtw "testOnly oathdigital.gameplay.setup.GameStartToWakeSuite"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/setup/GameStartToWakeSuite.scala
git commit -m "test: cover the full Setup journey from Begin to Wake

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: Full verification gate and slice tracking

**Files:**
- Modify: `docs/ROADMAP.md` (the "Phase - Randomized setup for alpha" section)

- [ ] **Step 1: Run the complete verification gate**

```bash
./sbtw test
./sbtw frontend/test
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
```

Expected: everything passes. If `check-architecture.py` reports a forbidden import, check which task introduced it against the Global Constraints' layering rule (in particular: `SetupOrders`/`Chronicle` must stay reachable from `model` alone, so `GameStartRules`/`SetupProcedure` must never import `oathdigital.application`).

- [ ] **Step 2: Record slice 2 as done**

In `docs/ROADMAP.md`, under "### Phase - Randomized setup for alpha", replace the sentence "Slices 2 (setup on the walker) and 3 (the E02/E06/E22 powers) remain." with:

```markdown
Slice 2 (setup on the walker: `Phase.Setup`, the triggered `Setup` procedure,
and deletion of the legacy setup event machine) is done -- plan at
[docs/superpowers/plans/2026-09-21-chronicle-setup-slice2.md](superpowers/plans/2026-09-21-chronicle-setup-slice2.md).
Slice 3 (the E02/E06/E22 powers) remains.
```

- [ ] **Step 3: Run the markdown link check again**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS (the new plan-file link resolves).

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record Chronicle setup slice 2

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** "Setup from a Chronicle" (`GameStarted`, `Phase.Setup`, validation, Atlas/dispossessed population) -> Task 3. "Setup on the walker" (the per-player pawn/adviser loop, `SetupEnd`, entry into Wake, deletion of `FirstGameSetupRules`/the bespoke commands/frontend controls) -> Tasks 4, 5, 7. "The Chronicle model"'s "Deck order is kept but not meaningful" -> the `SetupOrders` split (Task 1) and `ChronicleFirstGamePlan.dealOrder` (Task 6). "Setup powers"' windows (`SetupPawnPlaced`, `SetupEnd`) -> Task 1 Step 6, declared but left un-hooked for slice 3. The spec's testing list ("replay of `GameStarted` reproduces the same `ReadyGame` with no RNG", "a walker setup suite: turn order, pawn then adviser, discards..., `SetupEnd` order, entry into Wake") -> Tasks 4 and 8.
- **Placeholder scan:** every step has complete code except the three tests explicitly delegating a "drive a multi-park tree to completion" helper to an existing suite's established pattern (`RecoverProcedureSuite.scala`/`WalkerReplayDriftSuite.scala`) rather than re-deriving it inline -- this mirrors slice 1's own Task 6 precedent of calling out a regression-guarded refactor rather than faking new-behavior TDD, and is a deliberate "reuse this codebase's own established test-driving idiom" instruction, not a vague "write tests" placeholder.
- **Type consistency:** `SetupOrders`/`FirstGameSetupPlan`/`FirstGameSetupMaterial` (Task 1/3) keep the exact shape Tasks 4-6 find them in. `GameStartRules.evolve`'s signature (Task 3) is the one signature `OathRules.evolve`'s `GameStarted` case (Task 5) and `GameStartRulesSuite`/`GameStartToWakeSuite` (Tasks 3, 8) all call. `SetupProcedure.build`'s signature matches `WalkerProcedureRegistry.Entry.build`'s type exactly (Task 4 vs. Task 5). `ChronicleFirstGamePlan.dealOrder`'s total, `Either`-free signature (Task 6) is used identically by `GameApplicationService.prepareBootstrap`.
- **Scope discipline:** slice 3's own work (E02/E06/E22 `ContributingPower`s hooking `SetupPawnPlaced`/`SetupEnd`) is explicitly out of scope; this plan only guarantees both windows exist and fold correctly over an empty automatic set. The Chronicle string codec, later-game setup (a non-empty `world`), and Chancellor/legacy setup choices remain the spec's own deferred follow-ups, untouched here.
