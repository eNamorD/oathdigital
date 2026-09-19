# Muster and Trade on the Procedure Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run Muster and Trade on the procedure walker with a source `Decide`, a generic preview of a parked `ChooseOne`, and no legacy Economy path left.

**Architecture:** Each action is a registry entry whose tree is `Sequence(Decide(source), Branch(cost node, gain node))`. The walker gains `WalkerSimulation.preview`, which answers each `ChooseOne` option against the parked tree and reports its recorded operations. Projection uses that preview to offer start controls, to prune and annotate the parked decision's options, and `StartWalker` uses it to reject a start with nothing playable. The legacy `Economy` object, its commands, intents, events, codecs and projections are deleted last, after a differential parity test.

**Tech Stack:** Scala 2.13, sbt (`./sbtw`), munit, Scala.js frontend (jsdom tests), ujson.

**Spec:** `docs/superpowers/specs/2026-09-18-economy-walker-design.md`. Read it first; this plan implements it and only departs from it where "Spec clarifications" below says so.

## Global Constraints

- One deliberate rule change: the legacy first-game gates (exile-only roles and the fixed unaltered Foundation profile) do not exist in the walker version, as they do not in Travel. Everything else is preserved: costs, gains, other gates, candidate order, labels and control strings.
- The 24 Muster/Trade powers in `MusterPowers` stay diagnostic-only (`ReviewedHandler`) and keep working through the registry's `fallbackKind`.
- Two-step flow: the action control sends `StartWalker("muster")` or `StartWalker("trade", [resource])`, the walk parks at the source decision, and the answer is the chosen card.
- Trade's resource is a `DecisionOptionRef.Button` start argument, `favor` or `secret`, meaning the resource gained.
- The token-free rule applies to every source, whatever added it. This slice has no exception mechanism.
- Gains are requested at their unclamped amount (`1 + matching`, or `matching` for Secrets) and the best-effort `Gain` takes what the supply or bank has. No manual `min`.
- The preview is restricted to `ChooseOne`. The start rejection is opt-in per registry entry; Recover, Forge and Search behave exactly as before.
- Muster and Trade for the Imperial force kind are reachable in code but untested until Empire games exist. Do not add hand-built-fixture tests for them.
- Pre-release history compatibility is not required by the approved walker design.
- Repository rules enforced by `BackendArchitectureSuite`: production Scala files stay under 800 lines; modules under `gameplay/actions`, `gameplay/phases` and `gameplay/powers` change owned material only through core operations; nothing under `gameplay/walker` or `gameplay/operations` names a specific power.
- Persisted text (code, comments, docs, commit messages) is plain English. End every commit message with the trailer `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Commands: backend tests `./sbtw -no-colors "testOnly <class>"`; frontend tests `./sbtw -no-colors "frontend/testOnly <class>"`; everything `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`.

## Spec clarifications

Found while planning; each is a decision this plan makes where the spec was silent or inconsistent.

1. **Preview targets.** The spec's Preview section says `GameApplicationService.walkerTargets` serves muster/trade preview targets, but its Cutover section deletes the `GameRoutes` preview cases. Once the board-target actions are gone nothing consumes those targets, so this plan deletes the cases and adds no `walkerTargets` entry.
2. **Control strings.** Muster and Trade never had control strings, only board-target action kinds (`muster`, `trade-favor`, `trade-secret`). The action kinds keep their labels and family grouping. The new start controls follow `beginRecover`/`beginForge`: `beginMuster`, `beginTradeFavor`, `beginTradeSecret`.
3. **Zero-gain detail.** Legacy candidates showed `+0 warbands` when the supply was exhausted. The walker preview reads details off the recorded operations, and a gain shrunk to nothing is skipped and recorded nowhere, so a fully shrunk gain shows no gain line.
4. **Edifice label.** Legacy labelled every edifice target with its ruined name. The walker option is labelled with the edifice's actual side.
5. **Prefix-free start preview.** `WalkerSimulation.preview` from a fresh start supports only a tree that parks before running any operation, because the state after an executed prefix is not returned by `advance`. Muster and Trade satisfy this. Previewing an already-parked decision (`previewParked`) has no such limit.
6. **The added-option test.** The spec's test-only transform "adds an option and asserts it appears, annotated, in the preview". No valid option can be added in this slice: the base query already offers every token-free site card and `resolve` accepts nothing else. The test asserts the transform's option appears in the preview and is dropped with `resolve`'s violation, which proves the hook and marks extension point 2 (the wider accepted-card rule) that lands with Golem Legions.
7. **Projector doc.** `WalkerDecisionProjector` documents that it "never filters an option it merely dislikes". For a procedure that opts in, it now shows only options the preview accepts. The doc comment is amended in the same task.
8. **New continuation.** A parked Muster/Trade decision needs a client-facing continuation. This plan adds `OathContinue.AwaitingEconomyDecision`, as Search added `AwaitingSearchDecision`.
9. **Kept violations.** `UnsupportedEconomyState`, `EconomyCardUnavailable` and `EconomyCardNotEmpty` stay because `MusterSource.resolve` uses them. `EconomySourceMismatch` and `EconomyOutcomeMismatch` are deleted with `Economy.evolve`.
10. **Site-ruler check kept.** The spec leaves the legacy site-ruler mapping check to the plan, to survive "only if nothing else already rejects an invalid mapping". Nothing does: `SiteRule.ruler` fails on an unknown or duplicated lineage, but the operation invariants never call it. The check is therefore kept as a start gate in `EconomyTree.build`, with the same `UnsupportedEconomyState` violation legacy used.

## File map and interfaces

Create:
- `src/main/scala/oathdigital/model/PlayerForceKind.scala`: `PlayerForceKind.of(ready, player): Option[ForceKind]`, the one role-to-warband-kind mapping.
- `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala`: `MusterSource` value plus `atSite`, `resolve`, `matching`.
- `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`: the shared tree builder and its `Kind` parameters.
- `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala` and `TradeProcedure.scala`: `build`, `rebuild`, `startOptions`, `decisionId`.
- `src/main/scala/oathdigital/application/OperationDetails.scala`: recorded operations to detail strings.
- Tests: `PlayerForceKindSuite`, `DecisionOptionRefSuite`, `WalkerPreviewSuite`, `EconomyFixture` (shared board builder), `MusterProcedureSuite`, `TradeProcedureSuite`, `EconomyWalkerSuite`, `EconomyProjectionSuite`, `EconomyParitySuite`, `WalkerChoicePanelRenderSuite`.

Modify: `model/Decisions.scala`, `model/PowerWindow.scala`, `model/GameViolation.scala`, `model/GameProcedureProtocol.scala`, `model/ProcedureRef.scala`, `gameplay/operations/OperationStateAdapter.scala`, `gameplay/operations/OperationExecutor.scala`, `gameplay/walker/WalkerSimulation.scala`, `gameplay/walker/WalkerProcedureRegistry.scala`, `gameplay/OathRulesWalker.scala`, `application/WalkerDecisionProjector.scala`, `application/LegalActionProjector.scala`, `shared/.../ActionProjectionDtos.scala` and `ActionProjectionCodec.scala`, `frontend/.../WalkerPanelSupport.scala`, `ActionDecisionRenderer.scala`, `ModifierWorkflow.scala`; then the deletion list in Tasks 10 to 12.

Interfaces later tasks rely on (exact signatures):

```scala
// model
final case class DecisionOptionRef.Edifice(id: EdificeId)          // kind "edifice", wireId id.value
final case class DecisionOption.Edifice(ref: DecisionOptionRef.Edifice)
object PlayerForceKind { def of(ready: ReadyGame, player: PlayerState): Option[ForceKind] }
final case class OathViolation.NoPlayableOption(procedure: String)
final case class OathContinue.AwaitingEconomyDecision(playerId: PlayerId, decision: DecisionId)
// PowerWindow: MusterSourceSelection, MusterGain, TradeSourceSelection, TradeGain

// gameplay.walker.WalkerSimulation
final case class PreviewOutcome(operations: Vector[CoreOperation], complete: Boolean)
final case class PreviewedOption(option: DecisionOption, outcome: Either[OathViolation, PreviewOutcome])
def preview(tree: Operation, state: ReadyGame, powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]]
def previewParked(state: ReadyGame, tree: Operation, pending: PendingTree, powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]]

// gameplay.walker.WalkerProcedureRegistry
Entry(..., requiresPlayableOption: Boolean = false)
def requiresPlayableOption(procedure: ProcedureRef): Boolean

// gameplay.actions.economy
object MusterProcedure {
  val decisionId: String                                            // "muster.source"
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Either[OathViolation, Operation]
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Either[OathViolation, Operation]
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId, powers: WalkerPowers): Vector[PreviewedOption]
}
object TradeProcedure {
  val decisionId: String                                            // "trade.source"
  def resourceOf(args: Vector[DecisionOptionRef]): Either[OathViolation, TradeResource]
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId, args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId, args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId, resource: TradeResource, powers: WalkerPowers): Vector[PreviewedOption]
}
// shared DTO
final case class DecisionOptionProjection(kind: String, id: String, label: String, card: Option[CardDetailsProjection] = None, details: Vector[String] = Vector.empty)
```

---

### Task 1: Edifice option reference

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Create: `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`
- Modify: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala` (test "every option reference kind round trips through a recorded answer")
- Modify: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`

**Interfaces:**
- Produces: `DecisionOptionRef.Edifice(id: EdificeId)` with `kind = "edifice"` and `wireId = id.value`; `DecisionOption.Edifice(ref: DecisionOptionRef.Edifice)`; `DecisionOption.forRef` returns it. Every wire path (answer codec, start arguments, journal, `GameIntentMapper`) already goes through `kind`/`wireId`/`fromWire`, so this is the only place the wire learns the kind.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`:

```scala
package oathdigital.model

class DecisionOptionRefSuite extends munit.FunSuite {
  private val hall = DecisionOptionRef.Edifice(EdificeId("E16"))

  test("an edifice reference spells itself as a kind and id pair and parses back") {
    assertEquals((hall.kind, hall.wireId), ("edifice", "E16"))
    assertEquals(DecisionOptionRef.fromWire(hall.kind, hall.wireId), Some(hall))
  }

  test("a blank edifice id is not a reference") {
    assertEquals(DecisionOptionRef.fromWire("edifice", "  "), None)
  }

  test("an edifice reference is presentable from the reference alone") {
    assertEquals(DecisionOption.forRef(hall), Some(DecisionOption.Edifice(hall)))
  }
}
```

In `GameEventWireSuite`, add one line to the `refs` vector of the test named "every option reference kind round trips through a recorded answer", after the `Vision` line:

```scala
      DecisionOptionRef.Vision(VisionId("vision:v1")),
      DecisionOptionRef.Edifice(EdificeId("E16")),
      DecisionOptionRef.Deck(CardDeck.Relic),
```

In `WalkerDecisionProjectorSuite`, add this test after "a declared option whose id is absent from authoritative state suppresses the whole decision projection":

```scala
  test("an edifice option projects its label and details from the card's side, " +
      "and one that is not at a site suppresses the decision") {
    val (base, actor) = parked(ActionRef.Recover)
    val hall = EdificeId("E16")
    val current = base.ready.game.current
    assert(current.commonCards.edificeDeck.contains(hall),
      "the fixture must still hold the hall in the edifice deck")
    val siteId = current.players.find(_.player == actor).get.pawnSite.get
    val site = current.map.sites(siteId)
    val placed = base.copy(ready = base.ready.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(
        edificeDeck = current.commonCards.edificeDeck.filterNot(_ == hall)),
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(denizens = site.denizens :+
          EdificeState(hall, EdificeSide.Intact, Tokens.empty)))))))
    val option = DecisionOption.Edifice(DecisionOptionRef.Edifice(hall))

    val query = projects(placed, actor, Vector(option))
      .getOrElse(fail("an edifice at a site must project"))
    assertEquals(query.options.map(row => (row.kind, row.id)),
      Vector(("edifice", "E16")))
    assert(query.options.head.label.nonEmpty)
    assert(query.options.head.card.nonEmpty)

    // Control: the same option while the hall still sits in the deck has no
    // located state to describe, so the whole decision is suppressed.
    assertEquals(projects(base, actor, Vector(option)), None)
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.model.DecisionOptionRefSuite"`
Expected: compile FAIL, "value Edifice is not a member of object DecisionOptionRef".

- [ ] **Step 3: Implement the model change**

In `src/main/scala/oathdigital/model/Decisions.scala`:

1. After the `Vision` case class inside `object DecisionOptionRef`, add:

```scala
  final case class Edifice(id: EdificeId) extends DecisionOptionRef {
    val kind: String = "edifice"
    def wireId: String = id.value
  }
```

2. In `fromWire`, add after the `"vision"` line:

```scala
      case "edifice" => Some(Edifice(EdificeId(wireId)))
```

3. In `object DecisionOption`, after `Vision`, add `final case class Edifice(ref: DecisionOptionRef.Edifice) extends DecisionOption`, and in `forRef` add `case value: DecisionOptionRef.Edifice => Some(Edifice(value))` after the `Vision` case.

4. Change the two comments that say "eight variants" (on `kind` and on `fromWire`) to "nine variants".

- [ ] **Step 4: Implement the projector case**

In `WalkerDecisionProjector.optionProjection`, add this case after `DecisionOption.Vision`:

```scala
      case DecisionOption.Edifice(edifice) =>
        index.flatMap(_.stateOf(edifice.id)).collect {
          case state: EdificeState => state
        }.flatMap(state => row(presentation.edificeLabel(state.id, state.side),
          Some(presentation.edificeCardDetails(state))))
```

- [ ] **Step 5: Run the tests and the wider suites**

Run: `./sbtw -no-colors "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS. If the compiler reports a non-exhaustive match over `DecisionOption` or `DecisionOptionRef` in another file, add the `Edifice` case there in the same way (`grep -rn "DecisionOption.Vision" src/main` lists the candidates).

Then run `./sbtw -no-colors test` and expect PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
git commit -m "feat(walker): add the Edifice decision option reference

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 2: Per-option detail annotation

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (`DecisionOptionProjection`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala` (`encodeOptionRow`, `decodeOptionRow`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (`renderChooseOnePanel`)
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Produces: `DecisionOptionProjection(kind, id, label, card = None, details: Vector[String] = Vector.empty)`. `details` is optional on the wire: an absent key decodes as empty, so no existing payload changes.

- [ ] **Step 1: Write the failing tests**

In `ProjectionProtocolSuite`, add after the test "a decision query declaring no panel copy round-trips as absent":

```scala
  test("a choose-one option round-trips its details and defaults them to none") {
    val annotated = DecisionQueryProjection("choose-one", Vector(
      DecisionOptionProjection("denizen", "d1", "Old Oak", None,
        Vector("1 Supply", "+2 warbands")),
      DecisionOptionProjection("denizen", "d2", "Rowdy Pub")))
    assertEquals(annotated.options.last.details, Vector.empty)
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(annotated))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))
  }
```

Create `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** The generic choose-one panel, at the DOM: every projected option is one
  * control, and the consequences the engine annotated on it show beside it.
  * Runs under jsdom, like `PartitionPanelRenderSuite`.
  */
class WalkerChoicePanelRenderSuite extends munit.FunSuite {
  private val oak = DecisionOptionState("denizen", "d1", "Old Oak", None,
    Vector("1 Supply", "+2 warbands"))
  private val pub = DecisionOptionState("denizen", "d2", "Rowdy Pub")
  private val query = DecisionQueryState("choose-one", Vector(oak, pub),
    heading = Some("Choose a card to Muster from"))
  private val parked = WalkerDecisionState("muster", "muster.source", "decide",
    query = Some(query))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def render(ui: RecordingView): dom.Element = {
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, ui)
    panel
  }

  private def all(root: dom.Element, selector: String): Vector[dom.Element] =
    root.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  test("each projected option is one control and its details show beside it") {
    val panel = render(new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Old Oak", "Rowdy Pub"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Supply · +2 warbands"))
  }

  test("choosing an option submits the generic answer for its kind and id") {
    val ui = new RecordingView("game", "red")
    val panel = render(ui)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted,
      Vector(WalkerPanelSupport.resolveChooseOneCommand(parked, oak)))
  }
}
```

If `js` is unused the compiler only warns; drop the import if `-Xfatal-warnings` is on.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compile FAIL, too many arguments for `DecisionOptionProjection`.

- [ ] **Step 3: Implement the DTO and codec**

`ActionProjectionDtos.scala`: replace the case class with

```scala
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty)
```

and add to its doc comment: "`details` are the consequences the engine annotated on the option (for example the Supply an answer costs and what it yields), already worded for display; empty for an option nothing was annotated on."

`ActionProjectionCodec.scala`:

```scala
  private def encodeOptionRow(row: DecisionOptionProjection): ujson.Value =
    ujson.Obj("kind" -> row.kind, "id" -> row.id, "label" -> row.label,
      "card" -> option(row.card)(encodeCard),
      "details" -> encoded(row.details)(ujson.Str(_)))

  private[projection] def decodeOptionRow(raw: ujson.Value, child: String)
      : Result[DecisionOptionProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("kind", "id", "label", "card", "details"), child)
    kind <- string(row, "kind", child); id <- string(row, "id", child)
    label <- string(row, "label", child)
    card <- optionalAbsent(row, "card", child)(decodeCard)
    details <- stringsOrEmpty(row, "details", child)
  } yield DecisionOptionProjection(kind, id, label, card, details)
```

- [ ] **Step 4: Implement the panel**

In `WalkerPanelSupport.renderChooseOnePanel`, after `panel.appendChild(choose)` inside the `query.options.foreach`, add:

```scala
          if (option.details.nonEmpty) panel.appendChild(text("p",
            "walker-choice-details", option.details.mkString(" · ")))
```

- [ ] **Step 5: Run the tests**

Run: `./sbtw -no-colors "testOnly oathdigital.protocol.ProjectionProtocolSuite"` then `./sbtw -no-colors "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite oathdigital.frontend.PartitionPanelRenderSuite"`
Expected: PASS. Then `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared frontend
git commit -m "feat(protocol): let a decision option carry display details

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 3: Walker preview of a parked choose-one

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`
- Create: `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala`

**Interfaces:**
- Consumes: `ProcedureWalker.advance(state, tree, None, powers)`, `ProcedureWalker.resolve(state, tree, pending, Answered, powers)`, `ProcedureWalker.parkedDecide(state, tree, pending, powers)`, `ProcedureWalker.restrictionViolations`.
- Produces (exact):

```scala
final case class PreviewOutcome(operations: Vector[CoreOperation], complete: Boolean)
final case class PreviewedOption(option: DecisionOption,
    outcome: Either[OathViolation, PreviewOutcome])
def preview(tree: Operation, state: ReadyGame, powers: WalkerPowers)
    : Either[OathViolation, Vector[PreviewedOption]]
def previewParked(state: ReadyGame, tree: Operation, pending: PendingTree,
    powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]]
```

`preview` walks a fresh tree to its first park and previews it; it is `Left` if the tree finishes without parking, runs an operation before parking, or parks on anything but a `ChooseOne`. `previewParked` previews an already-parked position. In both, each option's outcome is the operations the walk recorded after answering it (`complete = true` when the tree then finished, `false` when it parked again), or the violation that dropped it.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class WalkerPreviewSuite extends munit.FunSuite {
  private val cheap = DecisionOptionRef.Button("cheap")
  private val dear = DecisionOptionRef.Button("dear")
  private val extra = DecisionOptionRef.Button("extra")
  private val hooked = PowerWindow.SearchEligibility

  private def withSupply(amount: Int): (ReadyGame, PlayerId) = {
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val players = base.game.current.players.map(player =>
      if (player.player == actor)
        player.copy(board = player.board.copy(supply = SupplyTrack(amount)))
      else player)
    (base.updateCurrent(_.copy(players = players)), actor)
  }

  /** A decision, then a cost that depends on the answer. */
  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Decide("pick", actor, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(cheap, "Cheap"),
      DecisionOption.Button(dear, "Dear"))), window = Some(hooked)),
    Branch((_, pending) => pending.answered.collectFirst {
      case Answered("pick", DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match {
      case Some(`cheap`) => Vector[Operation](SpendSupply(actor, 1))
      case Some(`dear`) => Vector[Operation](SpendSupply(actor, 3))
      case _ => Vector.empty[Operation]
    })))

  test("each option of a parked choose-one is answered against the tree") {
    val (ready, actor) = withSupply(2)
    val previewed = WalkerSimulation.preview(tree(actor), ready, WalkerPowers.empty)
      .getOrElse(fail("a tree parked on a choose-one must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap, dear))
    assertEquals(previewed.head.outcome, Right(PreviewOutcome(
      Vector(SpendSupply(actor, 1)), complete = true)))
    assert(previewed(1).outcome.isLeft, "three Supply is not affordable with two")
  }

  test("an option that finishes the tree is complete and one that parks again is not") {
    val (ready, actor) = withSupply(5)
    val twoStep = Sequence(Vector[Operation](
      Decide("first", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))),
      Decide("second", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(dear, "Dear"))))))
    val previewed = WalkerSimulation.preview(twoStep, ready, WalkerPowers.empty)
      .getOrElse(fail("a two-decision tree must preview its first decision"))
    assertEquals(previewed.map(_.outcome.map(_.complete)), Vector(Right(false)))
  }

  test("a tree that never parks, or runs an operation first, or parks on another " +
      "query shape has nothing to preview") {
    val (ready, actor) = withSupply(5)
    assert(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(actor, 1))), ready, WalkerPowers.empty).isLeft)
    assert(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(actor, 1),
      Decide("late", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))))), ready,
      WalkerPowers.empty).isLeft)
    val distribute = Sequence(Vector[Operation](Decide("split", actor,
      DecisionQuery.Distribute(Vector(
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 2, Some(2)),
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Nomad), 0, 6, Some(0))),
        total = 2, heading = Some("League Treaty"), confirmLabel = "Move"))))
    assert(WalkerSimulation.preview(distribute, ready, WalkerPowers.empty).isLeft)
  }

  test("a transform on the decision's window changes what is previewed") {
    val (ready, actor) = withSupply(5)
    val previewed = WalkerSimulation.preview(tree(actor), ready,
      WalkerPowers(Vector(AddExtra(PowerId("test.add-extra")))))
      .getOrElse(fail("the folded decision must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap, dear, extra))
    assertEquals(previewed.last.outcome, Right(PreviewOutcome(Vector.empty,
      complete = true)))
  }

  test("previewParked reads the same options from an already-parked position") {
    val (ready, actor) = withSupply(2)
    val action = tree(actor)
    val parked = ProcedureWalker.advance(ready, action, None, WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked]
    assertEquals(
      WalkerSimulation.previewParked(ready, action, parked.tree, WalkerPowers.empty),
      WalkerSimulation.preview(action, ready, WalkerPowers.empty))
  }

  private final case class AddExtra(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hooked -> Vector(Transform((_, operations) => operations.map {
        case decide: Decide => decide.query match {
          case DecisionQuery.ChooseOne(options, heading) =>
            decide.copy(query = DecisionQuery.ChooseOne(
              options :+ DecisionOption.Button(extra, "Extra"), heading)): Operation
          case _ => decide
        }
        case other => other
      })))
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.WalkerPreviewSuite"`
Expected: compile FAIL, "value preview is not a member of object WalkerSimulation".

- [ ] **Step 3: Implement**

Replace the body of `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` below the existing doc comment with the following (keep the existing scaladoc above `object WalkerSimulation`, and extend it with: "`preview` and `previewParked` extend the same idea to a tree that parks on a `ChooseOne`: they answer each option against the tree and report what that answer would record."):

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{Answered, CoreOperation, Decide, DecisionAnswer,
  DecisionOption, DecisionQuery, OathEvent, OathViolation, Operation,
  PendingTree, ReadyGame}

object WalkerSimulation {

  /** What answering one option would record. `complete` is true when the tree
    * then finished and false when it parked at a further decision.
    */
  final case class PreviewOutcome(operations: Vector[CoreOperation],
      complete: Boolean)

  /** One option of a previewed decision and what answering it does, or the
    * violation that makes it unanswerable.
    */
  final case class PreviewedOption(option: DecisionOption,
      outcome: Either[OathViolation, PreviewOutcome])

  def run(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers))
        .flatMap {
          case WalkerOutcome.Finished(_, events) =>
            Right(recordedOperations(events))
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a simulated walker tree parked; " +
              "only a tree that runs to the end has an outcome to simulate"))
        }
    }

  /** Previews a freshly built tree: walks it to its first park and previews
    * that decision. The tree must park before it runs any operation, because
    * the state after an executed prefix is not reported by `advance`.
    */
  def preview(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers))
        .flatMap {
          case WalkerOutcome.Parked(pending, events)
              if recordedOperations(events).isEmpty =>
            previewParked(state, tree, pending, powers)
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a previewed tree may not run " +
              "operations before its first decision"))
          case WalkerOutcome.Finished(_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a previewed tree finished without parking on a decision"))
        }
    }

  /** Previews the decision `pending` is parked on: each `ChooseOne` option is
    * answered as the decision's owner, against a copy of the parked position,
    * and the walk continues until it finishes or parks again. Nothing is
    * persisted. Any other query shape is rejected.
    */
  def previewParked(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]] =
    guarded {
      ProcedureWalker.parkedDecide(state, tree, pending, powers)
        .toRight(OathViolation.InvalidEventOrder(
          "the walker is not parked on a decision"))
        .flatMap { decide =>
          decide.query match {
            case DecisionQuery.ChooseOne(options, _) =>
              Right(options.map(option => PreviewedOption(option,
                answer(state, tree, pending, powers, decide, option))))
            case _ => Left(OathViolation.InvalidEventOrder(
              s"decision ${decide.decisionId} is not a choose-one, so it " +
                "cannot be previewed"))
          }
        }
    }

  private def answer(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, option: DecisionOption)
      : Either[OathViolation, PreviewOutcome] = guarded {
    ProcedureWalker.resolve(state, tree, pending,
      Answered(decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
        decide.owner), powers).map {
      case WalkerOutcome.Finished(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = true)
      case WalkerOutcome.Parked(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = false)
    }
  }

  private def recordedOperations(events: Vector[OathEvent])
      : Vector[CoreOperation] = events.collect {
    case step: WalkerStepRecorded => step.ops
  }.flatten

  // `ProcedureWalker` reports a broken resume position by throwing, and a
  // simulation must never propagate that to a projector building a view.
  private def guarded[A](body: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try body
    catch {
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid simulated walker position")))
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.WalkerPreviewSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.gameplay.TakeWealthProcedureSuite"`
Expected: PASS (Travel and Take Wealth exercise `run`, which was rewritten around the shared `guarded`).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala
git commit -m "feat(walker): preview each option of a parked choose-one

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 4: Shared model additions and the role-to-force-kind helper

**Files:**
- Create: `src/main/scala/oathdigital/model/PlayerForceKind.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateAdapter.scala` (`playerForceKind`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala` (`validateWarbands`)
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Modify: `src/main/scala/oathdigital/model/GameProcedureProtocol.scala`
- Create: `src/test/scala/oathdigital/model/PlayerForceKindSuite.scala`

**Interfaces:**
- Produces: `PlayerForceKind.of(ready: ReadyGame, player: PlayerState): Option[ForceKind]`; the windows `MusterSourceSelection`, `MusterGain`, `TradeSourceSelection`, `TradeGain`; `OathViolation.NoPlayableOption(procedure: String)`; `OathContinue.AwaitingEconomyDecision(playerId: PlayerId, decision: DecisionId)`.

The mapping already exists twice as a private copy. This task extracts it, so the Muster tree does not add a third.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/model/PlayerForceKindSuite.scala`. It tests the pure helper only, on a copy of the setup board whose lineage role is changed; it does not run Muster or Trade for an Imperial player.

```scala
package oathdigital.model

import oathdigital.gameplay.setup.FirstGameSetupFixture._

class PlayerForceKindSuite extends munit.FunSuite {
  private val ready = initialReady
  private val actor = ready.game.current.players.head

  private def withRole(role: Role): ReadyGame = {
    val lineage = ready.game.campaign.lineages(actor.lineage)
    ready.copy(game = ready.game.copy(campaign = ready.game.campaign.copy(
      lineages = ready.game.campaign.lineages.updated(actor.lineage,
        lineage.copy(role = role)))))
  }

  test("an Exile's warbands are their lineage's Exile kind") {
    assertEquals(PlayerForceKind.of(ready, actor),
      Some(ForceKind.Exile(actor.lineage)))
  }

  test("a Citizen's and a Chancellor's warbands are the shared Imperial kind") {
    Vector(Role.Citizen, Role.Chancellor).foreach { role =>
      assertEquals(PlayerForceKind.of(withRole(role), actor),
        Some(ForceKind.Imperial), role.toString)
    }
  }

  test("a player whose lineage is unknown has no force kind") {
    val orphan = ready.copy(game = ready.game.copy(campaign =
      ready.game.campaign.copy(lineages =
        ready.game.campaign.lineages - actor.lineage)))
    assertEquals(PlayerForceKind.of(orphan, actor), None)
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw -no-colors "testOnly oathdigital.model.PlayerForceKindSuite"`
Expected: compile FAIL, "not found: value PlayerForceKind".

- [ ] **Step 3: Implement the helper and use it**

Create `src/main/scala/oathdigital/model/PlayerForceKind.scala`:

```scala
package oathdigital.model

/** The kind of warband a player's own warbands are: the shared Imperial kind
  * for a Citizen or Chancellor, the lineage's own Exile kind otherwise. The
  * one definition that operation validation and the Muster tree both read.
  */
object PlayerForceKind {
  def of(ready: ReadyGame, player: PlayerState): Option[ForceKind] =
    ready.game.campaign.lineages.get(player.lineage).map { lineage =>
      if (lineage.role.isImperial) ForceKind.Imperial
      else ForceKind.Exile(player.lineage)
    }
}
```

In `OperationStateAdapter`, replace the body of `playerForceKind` with a call:

```scala
  private def playerForceKind(
      ready: ReadyGame,
      player: PlayerState
  ): Option[ForceKind] = PlayerForceKind.of(ready, player)
```

In `OperationExecutor.validateWarbands`, replace the `playerKinds` definition with:

```scala
    val playerKinds = ready.game.current.players.flatMap(player =>
      PlayerForceKind.of(ready, player))
```

- [ ] **Step 4: Add the windows, the violation and the continuation**

`PowerWindow.scala`, after `case object TradeCost extends TradeWindow { val key = "trade.cost" }`:

```scala
  case object MusterSourceSelection extends MusterWindow {
    val key = "muster.source-selection"
  }
  case object MusterGain extends MusterWindow { val key = "muster.gain" }
  case object TradeSourceSelection extends TradeWindow {
    val key = "trade.source-selection"
  }
  case object TradeGain extends TradeWindow { val key = "trade.gain" }
```

`GameViolation.scala`, after `EconomyOutcomeMismatch`:

```scala
  /** A start whose first decision offers nothing the procedure would accept. */
  final case class NoPlayableOption(procedure: String) extends OathViolation
```

`GameProcedureProtocol.scala`, after `AwaitingSearchDecision`:

```scala
  final case class AwaitingEconomyDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
```

- [ ] **Step 5: Run the whole backend suite**

Run: `./sbtw -no-colors test`
Expected: PASS. The extraction must not change behaviour, so every operation and Economy suite stays green.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model src/main/scala/oathdigital/gameplay/operations src/test/scala/oathdigital/model/PlayerForceKindSuite.scala
git commit -m "refactor(model): share the role-to-warband-kind mapping and add Economy windows

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 5: The Muster and Trade trees

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`
- Create: `src/test/scala/oathdigital/gameplay/EconomyFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/TradeProcedureSuite.scala`

**Interfaces:**
- Consumes: `DecisionOptionRef.Edifice`, `DecisionOption.Edifice` (Task 1); `WalkerSimulation.preview` and `PreviewedOption` (Task 3); `PlayerForceKind.of` and the four windows (Task 4).
- Produces: the `MusterProcedure`, `TradeProcedure` and `MusterSource` signatures listed in "File map and interfaces". Trade's tree is built from `resourceOf(args)`; both trees park at `decisionId` ("muster.source" / "trade.source") with a `ChooseOne` of the token-free site cards and heading "Choose a card to Muster from", "Choose a card to Trade for favor" or "Choose a card to Trade for secrets".

- [ ] **Step 1: Write the shared test board**

Create `src/test/scala/oathdigital/gameplay/EconomyFixture.scala`. It is the board `EconomySuite` builds, extracted so the new suites and the parity suite share it (`EconomySuite` itself is deleted in Task 12).

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Muster and Trade suites share: the active player at a site
  * holding one token-free plain denizen, with a matching-suit denizen available
  * to hold as an adviser. Denizens with an Economy power are excluded so a
  * power cannot change the arithmetic under test.
  */
object EconomyFixture {
  private val setup = new FirstGameSetupRules(catalog)
  private val economic = Set("73", "76", "40", "42", "176", "177", "193",
    "196", "81", "144", "6", "119", "120", "199", "102", "224",
    "229", "231", "238", "241", "248")

  val plain = catalog.denizens.find(d => !economic(d.id.value)).get
  val matching = catalog.denizens.find(d => d.suit == plain.suit &&
    d.id != plain.id && !economic(d.id.value)).get
  val plainId = DenizenId(plain.id.value)
  val matchingId = DenizenId(matching.id.value)
  val springDefinition = catalog.edifices.find(
    _.intact.handlers.contains("edifice.e26.intact")).get
  val springId = EdificeId(springDefinition.id.value)

  def act(tokens: Tokens = Tokens.empty, favor: Int = 4,
      secrets: Int = 2, supply: Int = 7,
      advisers: Vector[AdviserState] = Vector.empty,
      bank: Int = 5, boardWarbands: Int = 3): ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    val activeId = initial.game.current.turn.activePlayer
    val active = initial.game.current.players.find(_.player == activeId).get
    val siteId = active.pawnSite.get
    val site = initial.game.current.map.sites(siteId).copy(denizens = Vector(
      DenizenState(plainId, Orientation.FaceUp, tokens)))
    val inserted = advisers.map(_.id).toSet + plainId
    initial.copy(
      banks = initial.banks.copy(favor = initial.banks.favor.updated(
        plain.suit, bank)),
      game = initial.game.copy(current = initial.game.current.copy(
        turn = initial.game.current.turn.copy(phase = Phase.Act),
        commonCards = initial.game.current.commonCards.copy(worldDeck =
          initial.game.current.commonCards.worldDeck.filterNot(inserted)),
        map = initial.game.current.map.copy(sites =
          initial.game.current.map.sites.updated(siteId, site)),
        players = initial.game.current.players.map(p => if (p.player != activeId) p
          else p.copy(board = p.board.copy(favor = favor,
            faceUpSecrets = secrets, supply = SupplyTrack(supply),
            warbands = boardWarbands), advisers = advisers)))))
  }

  def player(ready: ReadyGame): PlayerState = ready.game.current.players.find(
    _.player == ready.game.current.turn.activePlayer).get

  /** The actor's site holds only the Hallowed Spring edifice, on `side`. */
  def spring(ready: ReadyGame, side: EdificeSide): ReadyGame = {
    val actor = player(ready)
    val siteId = actor.pawnSite.get
    val withoutSpring = ready.game.current.map.sites.map {
      case (id, site) => id -> site.copy(
        denizens = site.denizens.filterNot(_.id == springId))
    }
    ready.updateCurrent(_.copy(
      map = ready.game.current.map.copy(sites = withoutSpring.updated(
        siteId, ready.game.current.map.sites(siteId).copy(denizens = Vector(
          EdificeState(springId, side, Tokens.empty))))),
      commonCards = ready.game.current.commonCards.copy(edificeDeck =
        ready.game.current.commonCards.edificeDeck.filterNot(_ == springId))))
  }

  def matchingAdviser: AdviserState =
    DenizenState(matchingId, Orientation.FaceUp, Tokens.empty)

  /** Adds the actor's own adviser to the Muster source decision, which is
    * exactly what Golem Legions will do; only the acceptance rule stops it
    * today.
    */
  final case class AddAdviserSource(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) =>
              decide.copy(query = DecisionQuery.ChooseOne(options :+
                DecisionOption.Denizen(DecisionOptionRef.Denizen(matchingId)),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })))
  }
}
```

- [ ] **Step 2: Write the failing Muster suite**

Create `src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, MusterSource}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathViolation._

class MusterProcedureSuite extends munit.FunSuite {
  import EconomyFixture._

  private def parked(ready: ReadyGame): (Operation, PendingTree) = {
    val tree = MusterProcedure.build(catalog, ready, player(ready).player)
      .getOrElse(fail("a legal Muster must build"))
    val outcome = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Muster tree must walk to its source decision"))
    (tree, outcome.asInstanceOf[WalkerOutcome.Parked].tree)
  }

  private def muster(ready: ReadyGame, ref: DecisionOptionRef): ReadyGame = {
    val (tree, pending) = parked(ready)
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(ref),
      player(ready).player), WalkerPowers.empty)
      .getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless
  }

  private def siteTokens(ready: ReadyGame): Tokens =
    ready.game.current.map.sites(player(ready).pawnSite.get).denizens.head.tokens

  test("the tree parks on the token-free cards at the pawn site") {
    val ready = act()
    val (tree, pending) = parked(ready)
    val decide = ProcedureWalker.parkedDecide(ready, tree, pending,
      WalkerPowers.empty).getOrElse(fail("the walk must park on a decision"))
    assertEquals(decide.decisionId, "muster.source")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Denizen(DecisionOptionRef.Denizen(plainId))),
      Some("Choose a card to Muster from")))
  }

  test("Muster costs one Supply and one favor and gains one warband per " +
      "matching adviser plus one") {
    val after = muster(act(advisers = Vector(matchingAdviser)),
      DecisionOptionRef.Denizen(plainId))
    assertEquals(player(after).board.favor, 3)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.warbands, 5)
    assertEquals(siteTokens(after), Tokens(1, 0))
  }

  test("the gain shrinks to the warbands the supply still holds") {
    val after = muster(act(boardWarbands = 14), DecisionOptionRef.Denizen(plainId))
    assertEquals(player(after).board.warbands, 14)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.favor, 3)
  }

  test("an edifice of either side is a legal source and takes the favor") {
    Vector(EdificeSide.Ruined, EdificeSide.Intact).foreach { side =>
      val ready = spring(act(), side)
      val options = MusterProcedure.startOptions(catalog, ready,
        player(ready).player, WalkerPowers.empty).map(_.option.ref)
      assertEquals(options, Vector(DecisionOptionRef.Edifice(springId)), side.toString)
      assertEquals(siteTokens(muster(ready, DecisionOptionRef.Edifice(springId))),
        Tokens(1, 0), side.toString)
    }
  }

  test("a card carrying tokens is not offered, so there is nothing to start") {
    val ready = act(tokens = Tokens(0, 1))
    assertEquals(MusterProcedure.startOptions(catalog, ready,
      player(ready).player, WalkerPowers.empty), Vector.empty)
  }

  test("an option the actor cannot pay for is previewed as dropped") {
    val ready = act(favor = 0)
    val previewed = MusterProcedure.startOptions(catalog, ready,
      player(ready).player, WalkerPowers.empty)
    assertEquals(previewed.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(plainId)))
    assert(previewed.forall(_.outcome.isLeft))
  }

  test("resolve names why a reference is not a source") {
    val actor = player(act()).player
    val siteId = player(act()).pawnSite.get
    assertEquals(MusterSource.resolve(catalog, act(tokens = Tokens(0, 1)), actor,
      DecisionOptionRef.Denizen(plainId)), Left(EconomyCardNotEmpty(plainId)))
    assertEquals(MusterSource.resolve(catalog, act(), actor,
      DecisionOptionRef.Denizen(matchingId)),
      Left(EconomyCardUnavailable(siteId, matchingId)))
    assert(MusterSource.resolve(catalog, act(), actor,
      DecisionOptionRef.Button("site")).isLeft)
  }

  test("matching counts the actor's faceup advisers of the source's suit") {
    val ready = act(advisers = Vector(matchingAdviser))
    assertEquals(MusterSource.matching(catalog, ready, player(ready).player,
      plain.suit), 1)
    assertEquals(MusterSource.matching(catalog, act(), player(act()).player,
      plain.suit), 0)
  }

  test("a board whose site forces name an unknown lineage cannot start") {
    val ready = act()
    val siteId = player(ready).pawnSite.get
    val broken = ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(siteId,
        current.map.sites(siteId).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(LineageId("ghost")), 1))))))
    assert(MusterProcedure.build(catalog, broken, player(broken).player).isLeft)
  }

  test("Muster cannot start outside the Act phase") {
    val ready = act().updateCurrent(current =>
      current.copy(turn = current.turn.copy(phase = Phase.Wake)))
    assert(MusterProcedure.build(catalog, ready, player(ready).player).isLeft)
  }

  test("a power that adds an option to the source decision has it previewed, " +
      "and this slice's acceptance rule drops it") {
    val ready = act(advisers = Vector(matchingAdviser))
    val previewed = MusterProcedure.startOptions(catalog, ready,
      player(ready).player, WalkerPowers(Vector(AddAdviserSource(
        PowerId("test.add-adviser-source")))))
    assertEquals(previewed.map(_.option.ref), Vector(
      DecisionOptionRef.Denizen(plainId), DecisionOptionRef.Denizen(matchingId)))
    assert(previewed.head.outcome.isRight)
    previewed.last.outcome match {
      case Left(_: EconomyCardUnavailable) => ()
      case other => fail(s"expected the acceptance rule to drop it, got $other")
    }
  }
}
```

- [ ] **Step 3: Write the failing Trade suite**

Create `src/test/scala/oathdigital/gameplay/TradeProcedureSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

class TradeProcedureSuite extends munit.FunSuite {
  import EconomyFixture._

  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))

  private def trade(ready: ReadyGame, args: Vector[DecisionOptionRef]): ReadyGame = {
    val actor = player(ready).player
    val tree = TradeProcedure.build(catalog, ready, actor, args)
      .getOrElse(fail("a legal Trade must build"))
    val pending = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Trade tree must walk to its source decision"))
      .asInstanceOf[WalkerOutcome.Parked].tree
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(plainId)), actor),
      WalkerPowers.empty).getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless
  }

  private def siteTokens(ready: ReadyGame): Tokens =
    ready.game.current.map.sites(player(ready).pawnSite.get).denizens.head.tokens

  test("Trade for favor moves a secret and its yield is capped by the bank") {
    val after = trade(act(advisers = Vector(matchingAdviser), bank = 1), favor)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(player(after).board.favor, 5)
    assertEquals(after.banks.favor(plain.suit), 0)
  }

  test("Trade for favor without a matching adviser yields one favor") {
    val after = trade(act(), favor)
    assertEquals(player(after).board.favor, 5)
    assertEquals(after.banks.favor(plain.suit), 4)
  }

  test("Trade for secrets places one favor, burns one and yields the matches") {
    val after = trade(act(advisers = Vector(matchingAdviser)), secret)
    assertEquals(player(after).board.favor, 2)
    assertEquals(player(after).board.faceUpSecrets, 3)
    assertEquals(siteTokens(after), Tokens(1, 0))
  }

  test("Trade for secrets with no matching adviser yields nothing and still pays") {
    val after = trade(act(), secret)
    assertEquals(player(after).board.favor, 2)
    assertEquals(player(after).board.faceUpSecrets, 2)
  }

  test("a Trade the actor cannot pay for is previewed as dropped") {
    val poor = act(favor = 1)
    val previewed = TradeProcedure.startOptions(catalog, poor,
      player(poor).player, TradeResource.Secret, WalkerPowers.empty)
    assert(previewed.nonEmpty && previewed.forall(_.outcome.isLeft))
    val noSecrets = act(secrets = 0)
    assert(TradeProcedure.startOptions(catalog, noSecrets,
      player(noSecrets).player, TradeResource.Favor, WalkerPowers.empty)
      .forall(_.outcome.isLeft))
  }

  test("the start selection must be exactly one resource button") {
    val ready = act()
    val actor = player(ready).player
    assertEquals(TradeProcedure.resourceOf(favor), Right(TradeResource.Favor))
    assertEquals(TradeProcedure.resourceOf(secret), Right(TradeResource.Secret))
    Vector(Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Button("gold")),
      Vector[DecisionOptionRef](DecisionOptionRef.Denizen(plainId)),
      favor ++ secret).foreach { args =>
      assert(TradeProcedure.build(catalog, ready, actor, args).isLeft, args.toString)
      assert(TradeProcedure.rebuild(catalog, ready, actor, args).isLeft, args.toString)
    }
  }

  test("an intact edifice is a legal Trade source") {
    val ready = spring(act(), EdificeSide.Intact)
    assert(TradeProcedure.startOptions(catalog, ready, player(ready).player,
      TradeResource.Secret, WalkerPowers.empty).exists(_.outcome.isRight))
  }
}
```

- [ ] **Step 4: Run the suites to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.TradeProcedureSuite"`
Expected: compile FAIL, "object economy is not a member of package oathdigital.gameplay.actions".

- [ ] **Step 5: Implement `MusterSource`**

Create `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala`:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** What a Muster or Trade draws on: the card its cost is placed on, the suit
  * that decides which advisers match and which favor bank a Trade draws from,
  * and where the card sits. Resolved from the answered reference on every
  * command and never persisted.
  */
final case class MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef,
    option: DecisionOption)

object MusterSource {

  /** The sources the base rule offers: the token-free cards at the actor's
    * pawn site, in site order.
    */
  def atSite(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[MusterSource] =
    for {
      siteId <- pawnSite(state, actor).toVector
      site <- state.game.current.map.sites.get(siteId).toVector
      card <- site.denizens
      if card.tokens.isEmpty
      suit <- catalog.suitOf(card.id).toVector
    } yield sourceOf(siteId, card, suit)

  /** The source a reference names, or why it is not one. This is the single
    * acceptance function: a rule that lets a Muster draw on some other kind of
    * card widens it here. The token-free rule applies to every source.
    */
  def resolve(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef): Either[OathViolation, MusterSource] = for {
    id <- cardIdOf(ref).toRight(OathViolation.InvalidEventOrder(
      s"${ref.kind}/${ref.wireId} is not a card a Muster or Trade can draw on"))
    siteId <- pawnSite(state, actor).toRight(OathViolation.PawnSiteMissing(actor))
    site <- state.game.current.map.sites.get(siteId)
      .toRight(OathViolation.SiteNotInPlay(siteId))
    card <- site.denizens.find(_.id == id)
      .toRight(OathViolation.EconomyCardUnavailable(siteId, id))
    _ <- Either.cond(card.tokens.isEmpty, (),
      OathViolation.EconomyCardNotEmpty(id))
    suit <- catalog.suitOf(card.id).toRight(
      OathViolation.UnsupportedEconomyState(s"no catalog suit for ${card.id}"))
  } yield sourceOf(siteId, card, suit)

  /** The actor's faceup advisers of `suit`: how many warbands, favor or
    * secrets a Muster or Trade adds to its base yield.
    */
  def matching(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      suit: Suit): Int =
    state.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers).count {
        case DenizenState(id, Orientation.FaceUp, _) =>
          catalog.suitOf(id).contains(suit)
        case _ => false
      }

  private def cardIdOf(ref: DecisionOptionRef): Option[CardId] = ref match {
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
  }

  private def pawnSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  private def sourceOf(siteId: SiteId, card: SiteDenizenState,
      suit: Suit): MusterSource = card match {
    case value: DenizenState => MusterSource(value.id, suit,
      RuleSourceRef.SiteCard(siteId, value.id),
      DecisionOption.Denizen(DecisionOptionRef.Denizen(value.id)))
    case value: EdificeState => MusterSource(value.id, suit,
      RuleSourceRef.Edifice(siteId, value.id),
      DecisionOption.Edifice(DecisionOptionRef.Edifice(value.id)))
  }
}
```

- [ ] **Step 6: Implement the shared tree**

Create `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.model._

/** The tree Muster and Trade share:
  *
  * {{{
  * Sequence(                                       // window = <Action>ActionEligibility
  *   Decide(source),                               // window = <Action>SourceSelection
  *   Branch { after the answer:
  *     Sequence(PayCost, SpendSupply(1)),          // window = <Action>Cost
  *     Sequence(the gain) })                       // window = <Action>Gain
  * }}}
  *
  * The source is a decision, not a start argument, so a power can add to or
  * remove from the offered cards through the decision's window. The gain is a
  * concrete operation inside its own windowed node, not an opaque `BuildOps`,
  * so a power can see and rewrite the requested amount; it is requested
  * unclamped and the best-effort `Gain` takes what the supply or bank holds.
  * Legacy computed the gain from the state before the cost, and no cost feeds
  * a gain input, so it is built from the state at the `Branch`.
  */
private[economy] object EconomyTree {

  /** What differs between Muster and the two Trades. */
  final case class Kind(
      decisionId: String,
      heading: String,
      eligibility: PowerWindow,
      sourceSelection: PowerWindow,
      cost: PowerWindow,
      gain: PowerWindow,
      payment: Cost,
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation])

  /** Fresh start: the lifecycle gate, a pawn site, a resolvable ruler for
    * every site, and the audited catalog.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- siteRulers(state)
    _ <- PowerRuntime.requireAudited(catalog)
  } yield tree(catalog, state, actor, kind)

  /** Every site's forces must name a ruler the game can identify. Legacy
    * Economy refused a board where one did not, and nothing else rejects an
    * unknown or duplicated lineage on a site, so the check survives as a start
    * gate.
    */
  private def siteRulers(state: ReadyGame): Either[OathViolation, Unit] =
    state.game.current.map.sites.valuesIterator
      .map(site => SiteRule.ruler(site.forces, state.game.current.players))
      .collectFirst { case Left(error) => OathViolation.UnsupportedEconomyState(
        s"invalid site ruler mapping: $error") }
      .toLeft(())

  /** The same tree without the start-only gates, for a resume. */
  def tree(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Operation = Sequence(Vector[Operation](
    Decide(kind.decisionId, actor, DecisionQuery.ChooseOne(
      MusterSource.atSite(catalog, state, actor).map(_.option),
      heading = Some(kind.heading)), window = Some(kind.sourceSelection)),
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref)
        .fold(error => Vector[Operation](fail(error)), identity)))),
    Some(kind.eligibility))

  private def afterSource(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, kind: Kind, ref: DecisionOptionRef)
      : Either[OathViolation, Vector[Operation]] = for {
    source <- MusterSource.resolve(catalog, ready, actor, ref)
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(OathViolation.PawnSiteMissing(actor))
    force <- PlayerForceKind.of(ready, player).toRight(
      OathViolation.UnsupportedEconomyState(
        s"no warband kind for lineage ${player.lineage.value}"))
  } yield Vector[Operation](
    Sequence(Vector[Operation](
      PayCost(actor, Location.OnCard(source.card), kind.payment),
      SpendSupply(actor, 1)), Some(kind.cost)),
    Sequence(kind.yields(actor, source,
      MusterSource.matching(catalog, ready, actor, source.suit), force).toVector,
      Some(kind.gain)))

  private def answered(pending: PendingTree, decisionId: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
  }

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
}
```

- [ ] **Step 7: Implement the two procedures**

Create `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** Muster on the walker: pay one favor on a card at the pawn site and one
  * Supply, and gain a warband for each matching adviser plus one. Takes no
  * start selection; the card is the `muster.source` decision.
  */
object MusterProcedure {
  val decisionId: String = "muster.source"

  private val kind = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (actor, _, matching, force) => Some(Gain.Warbands(actor, force, 1 + matching)))

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    EconomyTree.build(catalog, state, actor, kind)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    Right(EconomyTree.tree(catalog, state, actor, kind))

  /** Every source the actor could Muster from now, each previewed through the
    * same tree a start walks. Empty when the action cannot start at all. This
    * is the single definition the start control and the parked decision read.
    */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, powers: WalkerPowers): Vector[PreviewedOption] =
    build(catalog, state, actor)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty)
}
```

Create `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** Trade on the walker. The start selection is one resource button, `favor`
  * or `secret`, naming what the actor gains: Trade for favor pays one secret
  * and yields favor of the card's suit; Trade for secrets pays two favor, one
  * of them burnt, and yields secrets. Both spend one Supply and both draw on
  * the `trade.source` decision.
  */
object TradeProcedure {
  val decisionId: String = "trade.source"

  private def kind(resource: TradeResource): EconomyTree.Kind = resource match {
    case TradeResource.Favor => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for favor", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(secret = 1),
      (actor, source, matching, _) =>
        Some(Gain.Favor(actor, source.suit, 1 + matching)))
    case TradeResource.Secret => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for secrets", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(favor = 1, favorBurnt = 1),
      (actor, _, matching, _) =>
        Option.when(matching > 0)(Gain.Secrets(actor, matching)))
  }

  def resourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, TradeResource] = args match {
    case Vector(DecisionOptionRef.Button("favor")) => Right(TradeResource.Favor)
    case Vector(DecisionOptionRef.Button("secret")) => Right(TradeResource.Secret)
    case other => Left(OathViolation.InvalidEventOrder(
      "trade takes exactly one resource button, favor or secret, as its start " +
        s"selection, got ${other.map(ref => s"${ref.kind}/${ref.wireId}")
          .mkString(", ")}"))
  }

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).flatMap(resource =>
      EconomyTree.build(catalog, state, actor, kind(resource)))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).map(resource =>
      EconomyTree.tree(catalog, state, actor, kind(resource)))

  /** As [[MusterProcedure.startOptions]], for one resource. */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, resource: TradeResource,
      powers: WalkerPowers): Vector[PreviewedOption] =
    EconomyTree.build(catalog, state, actor, kind(resource))
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty)
}
```

`TradeResource` is defined in `model/GameEventProtocol.scala` and stays after the legacy events are deleted in Task 12: keep the `sealed trait TradeResource` block when deleting `Mustered`/`Traded`.

- [ ] **Step 8: Run the suites**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.TradeProcedureSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. Likely adjustments if a test fails:
- If the numbers differ from those in the legacy `EconomySuite` (favor 3, supply 6, warbands 5 for Muster with one matching adviser; favor 5 and bank 0 for Trade for favor with bank 1), read the recorded operations with `WalkerSimulation.preview` and compare them to `Economy.musterOperations`/`tradeOperations`; the cost and gain operations must be identical.
- If `resolve` returns a different violation class than the test expects, keep the class the implementation reports only if it is one of `EconomyCardUnavailable`, `EconomyCardNotEmpty`, `InvalidEventOrder`; otherwise fix `resolve`.
- If `BackendArchitectureSuite` reports a direct owned-material write, the tree must express it as a core operation (it does; check the new files contain none of the marker strings the suite lists).

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/economy src/test/scala/oathdigital/gameplay/EconomyFixture.scala src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala src/test/scala/oathdigital/gameplay/TradeProcedureSuite.scala
git commit -m "feat(economy): declare Muster and Trade as walker trees

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 6: Register Muster and Trade and gate a start with nothing playable

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startWalker`)
- Modify: `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala`
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/EconomyWalkerSuite.scala`

**Interfaces:**
- Consumes: `MusterProcedure`, `TradeProcedure` (Task 5); `WalkerSimulation.previewParked` and `PreviewedOption` (Task 3); `OathViolation.NoPlayableOption`, `OathContinue.AwaitingEconomyDecision` (Task 4).
- Produces: `ActionRef.Muster` (key "muster") and `ActionRef.Trade` (key "trade"), both in `ActionRef.all`; `WalkerProcedureRegistry.requiresPlayableOption(procedure: ProcedureRef): Boolean`, true for exactly these two. The keys equal the `MajorActionKind` keys, so `GameApplicationService.walkerAction` and `GameIntentMapper.actionRef` bridge them with no further change.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/EconomyWalkerSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.oathkeeper.OathkeeperFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

/** Muster and Trade through the rules, as a client drives them: start, park on
  * the source decision, answer.
  */
class EconomyWalkerSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))
  private val card = DecisionOptionRef.Denizen(plainId)

  private def start(ready: ReadyGame, action: StartableRef = ActionRef.Muster,
      args: Vector[DecisionOptionRef] = Vector.empty) =
    rules.startWalker(Ready(ready), action, player(ready).player, startArgs = args)

  private def answer(state: OathState, actor: PlayerId, decisionId: String,
      ref: DecisionOptionRef) =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  test("starting Muster parks on the source decision and changes nothing yet") {
    val board = act()
    val actor = player(board).player
    val started = start(board).getOrElse(fail("a legal Muster must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(actor,
      DecisionId(MusterProcedure.decisionId)))
    val parked = ready(started.state)
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Muster))
    assert(parked.game.current.walkerPending.nonEmpty)
    assertEquals(player(parked).board.favor, 4)
    assertEquals(player(parked).board.supply.supply, 7)
  }

  test("answering the source decision pays, gains and completes the action") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val started = start(board).toOption.get
    val accepted = answer(started.state, actor, MusterProcedure.decisionId, card)
      .getOrElse(fail("the offered card must be accepted"))
    val after = ready(accepted.state)
    assertEquals(player(after).board.favor, 3)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.warbands, 5)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(accepted.continue, OathContinue.ActActionSelection(actor))
    assert(accepted.events.exists {
      case WalkerCompleted(ActionRef.Muster) => true
      case _ => false
    })
  }

  test("the action boundary starts the Oathkeeper procedure within the " +
      "answering command") {
    val initial = act()
    val actor = player(initial).player
    val leader = initial.game.current.players.map(_.player).find(_ != actor).get
    val board = OathkeeperFixture.ruled(initial, Vector(Some(leader)))
    val started = start(board).toOption.get
    val accepted = answer(started.state, actor, MusterProcedure.decisionId, card)
      .toOption.get
    assertEquals(accepted.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    assertEquals(ready(accepted.state).game.current.title,
      OathkeeperState(Some(leader), TitleSide.Oathkeeper))
  }

  test("Trade carries its resource as the start selection through the park") {
    Vector(favor -> "favor", secret -> "secret").foreach { case (args, name) =>
      val board = act(advisers = Vector(matchingAdviser))
      val actor = player(board).player
      val started = start(board, ActionRef.Trade, args)
        .getOrElse(fail(s"Trade for $name must start"))
      assertEquals(ready(started.state).game.current.walkerStartArgs, args)
      assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(actor,
        DecisionId(TradeProcedure.decisionId)))
      val accepted = answer(started.state, actor, TradeProcedure.decisionId, card)
        .getOrElse(fail(s"Trade for $name must complete"))
      assertEquals(ready(accepted.state).game.current.walkerPending, None)
    }
  }

  test("a start with nothing playable is rejected before anything is persisted") {
    assertEquals(start(act(favor = 0)).left.toOption,
      Some(NoPlayableOption("muster")))
    assertEquals(start(act(secrets = 0), ActionRef.Trade, favor).left.toOption,
      Some(NoPlayableOption("trade")))
    assertEquals(start(act(supply = 0)).left.toOption,
      Some(NoPlayableOption("muster")))
  }

  test("a start with no token-free card, or a wrong selection, is rejected") {
    assert(start(act(tokens = Tokens(0, 1))).isLeft)
    assert(start(act(), ActionRef.Muster, favor).isLeft)
    assert(start(act(), ActionRef.Trade).isLeft)
    assert(start(act(), ActionRef.Trade,
      Vector(DecisionOptionRef.Button("gold"))).isLeft)
  }

  test("only the actor can answer, and only with an offered card") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val other = board.game.current.players.map(_.player).find(_ != actor).get
    val started = start(board).toOption.get
    assert(answer(started.state, other, MusterProcedure.decisionId, card).isLeft)
    assert(answer(started.state, actor, MusterProcedure.decisionId,
      DecisionOptionRef.Denizen(matchingId)).isLeft)
    assert(answer(started.state, actor, MusterProcedure.decisionId, card).isRight)
  }
}
```

In `WalkerProcedureRegistrySuite`, add:

```scala
  test("only Muster and Trade require a playable option") {
    assertEquals(ProcedureRef.all.filter(
      WalkerProcedureRegistry.requiresPlayableOption).toSet,
      Set[ProcedureRef](ActionRef.Muster, ActionRef.Trade))
  }
```

In `GameApplicationServiceSuite`, add `import oathdigital.gameplay.actions.economy.MusterProcedure` to the imports and this test directly after "ruined edifice Economy target persists and replays with its kind" (the legacy test stays until Task 12):

```scala
  test("a walker Muster on an edifice persists, reloads and replays with its kind") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (siteId, edificeId) = plan.homelandEdifices.head
    val placements = siteId +: sites.filterNot(_ == siteId).take(2)
    val setup = execute(service, "game-walker-economy", placements)
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-walker-economy", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val started = service.handle("game-walker-economy", ended.nextSequence,
      GameCommand.StartWalker(ActionRef.Muster, StartPayload(active))).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(active,
      DecisionId(MusterProcedure.decisionId)))
    val mustered = service.handle("game-walker-economy", started.nextSequence,
      GameCommand.ResolveWalker(active, TreeDecision(MusterProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Edifice(edificeId))))).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-walker-economy").toOption.flatten.get
    assertEquals(loaded.state, mustered.state)
    val Ready(after) = loaded.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val records = repository.load("game-walker-economy").toOption.flatten.get.records
    assert(records.exists(record => record.contains("edifice") &&
      record.contains(edificeId.value)),
      "the journalled answer must spell the edifice kind and id")
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.EconomyWalkerSuite"`
Expected: compile FAIL, "value Muster is not a member of object ActionRef".

- [ ] **Step 3: Add the references**

In `ProcedureRef.scala`, inside `object ActionRef`, after `case object TakeWealth`:

```scala
  case object Muster extends ActionRef { val key = "muster" }
  case object Trade extends ActionRef { val key = "trade" }
```

Extend `all`:

```scala
  val all: Vector[ActionRef] = Vector(Search, PlayFacedownAdviser,
    Recover, Forge, Travel, TakeWealth, Muster, Trade)
```

In the comment above `all`, change "the three major actions above rely on it" to "the major actions above rely on it".

- [ ] **Step 4: Register them and add the opt-in**

In `WalkerProcedureRegistry.scala`: add `import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}`; extend `Entry`:

```scala
  private[gameplay] final case class Entry(
      fallbackKind: Option[MajorActionKind],
      rollDecisionId: Option[String],
      modifierWindow: Option[PowerWindow],
      continuationFor: (String, PlayerId, DecisionId) => Option[OathContinue],
      build: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      rebuild: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      requiresPlayableOption: Boolean = false)
```

and add to the doc comment above it: "`requiresPlayableOption` opts a procedure in to the preview gate: `StartWalker` rejects a start whose first decision has no option the procedure's own answer would accept, and the parked decision is shown with only such options, each annotated with what answering it records. Procedures that do not opt in are untouched."

Add the entries after `ActionRef.Travel`:

```scala
    /** Economy. The first walker actions whose first node is a `Decide`
      * and that opt in to the playable-option gate: nothing runs before the
      * source decision, so the start can be previewed from the fresh tree.
      */
    ActionRef.Muster -> Entry(
      fallbackKind = Some(MajorActionKind.Muster),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.MusterModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId == MusterProcedure.decisionId)(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.rebuild(catalog, state, activePlayer)),
      requiresPlayableOption = true),

    ActionRef.Trade -> Entry(
      fallbackKind = Some(MajorActionKind.Trade),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TradeModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId == TradeProcedure.decisionId)(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = TradeProcedure.build,
      rebuild = TradeProcedure.rebuild,
      requiresPlayableOption = true),
```

Add the accessor next to `isRegistered`:

```scala
  /** Whether `procedure` opts in to the playable-option gate (see `Entry`). */
  def requiresPlayableOption(procedure: ProcedureRef): Boolean =
    lookup(procedure, entries).exists(_.requiresPlayableOption)
```

- [ ] **Step 5: Gate the start**

In `OathRulesWalker.scala`, add `WalkerSimulation` to the `oathdigital.gameplay.walker` import list, then in `startWalker` insert the gate between the walk and the transition:

```scala
            outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
              powers))
            _ <- requirePlayableOption(procedure, ready, tree, outcome, powers)
            transition <- walkerTransition(state, procedure, tree,
              outcome, powers, modifiers, startArgs)
```

and add this method next to `walkerCall`:

```scala
  /** A procedure that opts in (`Entry.requiresPlayableOption`) is rejected at
    * start when its first decision offers no option its own answer would
    * accept, before anything is persisted. The decision must be the first
    * thing the tree does, because the preview answers it against the state
    * before the start.
    */
  private def requirePlayableOption(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, outcome: WalkerOutcome, powers: WalkerPowers)
      : Either[OathViolation, Unit] =
    if (!WalkerProcedureRegistry.requiresPlayableOption(procedure)) Right(())
    else outcome match {
      case WalkerOutcome.Parked(pending, events) =>
        if (events.exists {
          case step: WalkerStepRecorded => step.ops.nonEmpty
          case _ => false
        }) Left(InvalidEventOrder(s"${procedure.key} runs operations before " +
          "its first decision, so its start cannot be previewed"))
        else WalkerSimulation.previewParked(ready, tree, pending, powers)
          .flatMap(options => Either.cond(options.exists(_.outcome.isRight), (),
            NoPlayableOption(procedure.key)))
      case _ => Right(())
    }
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.EconomyWalkerSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.application.GameApplicationServiceSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.gameplay.PendingWalkerRulesSuite"`
Expected: PASS. `PendingWalkerRulesSuite` iterates `StartableRef.all`, so it now also proves that no walker start (including Muster and Trade) runs over a parked decision. If `EconomyWalkerSuite`'s "only the actor can answer" fails on the wrong-player case, read what `resumeWalker` returns for a foreign requester and assert that violation instead of `isLeft` only if the existing Forge suite does the same.

Then run `./sbtw -no-colors test`: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/ProcedureRef.scala src/main/scala/oathdigital/gameplay src/test/scala/oathdigital/gameplay src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala
git commit -m "feat(economy): run Muster and Trade on the walker

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 7: Start controls and annotated decision options

**Files:**
- Create: `src/main/scala/oathdigital/application/OperationDetails.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala`
- Create: `src/test/scala/oathdigital/application/EconomyProjectionSuite.scala`

**Interfaces:**
- Consumes: `MusterProcedure.startOptions`, `TradeProcedure.startOptions` (Task 5); `WalkerSimulation.previewParked` (Task 3); `WalkerProcedureRegistry.requiresPlayableOption` (Task 6); `DecisionOptionProjection.details` (Task 2).
- Produces: the controls `beginMuster`, `beginTradeFavor` and `beginTradeSecret` in `GameProjection.legalControls` for the active viewer, each present exactly when at least one source survives the preview; and, for a procedure that opts in, a parked decision whose options are only the surviving ones, each with `details` such as `Vector("1 Supply", "+2 warbands")`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/EconomyProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.{EconomyFixture, OathRules}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection

class EconomyProjectionSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))

  private def controls(ready: ReadyGame, viewer: PlayerId): Vector[String] =
    projector.project("economy", LoadedGame(Ready(ready), 4), viewer).legalControls

  private def parkedOptions(ready: ReadyGame, action: StartableRef,
      args: Vector[DecisionOptionRef]): Vector[DecisionOptionProjection] = {
    val actor = player(ready).player
    val started = rules.startWalker(Ready(ready), action, actor, startArgs = args)
      .getOrElse(fail("the action must start"))
    projector.project("economy", LoadedGame(started.state, 5), actor)
      .walkerDecision.flatMap(_.query).map(_.options)
      .getOrElse(fail("the parked decision must project a query"))
  }

  test("the active player is offered Muster and both Trades, and nobody else") {
    val board = act()
    val actor = player(board).player
    val other = board.game.current.players.map(_.player).find(_ != actor).get
    assert(Vector("beginMuster", "beginTradeFavor", "beginTradeSecret")
      .forall(controls(board, actor).contains))
    assert(!controls(board, other).exists(_.startsWith("beginTrade")))
    assert(!controls(board, other).contains("beginMuster"))
  }

  test("a control is offered only while a source survives the preview") {
    val oneFavor = act(favor = 1)
    val actor = player(oneFavor).player
    assert(controls(oneFavor, actor).contains("beginMuster"))
    assert(controls(oneFavor, actor).contains("beginTradeFavor"))
    assert(!controls(oneFavor, actor).contains("beginTradeSecret"))
    val noSupply = act(supply = 0)
    assert(!controls(noSupply, player(noSupply).player).exists(control =>
      control == "beginMuster" || control.startsWith("beginTrade")))
    val noSecrets = act(secrets = 0)
    assert(!controls(noSecrets, player(noSecrets).player)
      .contains("beginTradeFavor"))
  }

  test("a parked Muster shows its source with what answering costs and yields") {
    val options = parkedOptions(act(advisers = Vector(matchingAdviser)),
      ActionRef.Muster, Vector.empty)
    assertEquals(options.map(option => (option.kind, option.id)),
      Vector(("denizen", plainId.value)))
    assertEquals(options.map(_.details),
      Vector(Vector("1 Supply", "+2 warbands")))
    assert(options.head.label.nonEmpty)
  }

  test("a parked Trade shows its yield in the resource it gains") {
    val board = act(advisers = Vector(matchingAdviser), bank = 1)
    assertEquals(parkedOptions(board, ActionRef.Trade, favor).map(_.details),
      Vector(Vector("1 Supply", "+1 favor")))
    assertEquals(parkedOptions(board, ActionRef.Trade, secret).map(_.details),
      Vector(Vector("1 Supply", "+1 secrets")))
  }

  test("a gain that shrinks to nothing shows no gain line") {
    assertEquals(parkedOptions(act(boardWarbands = 14), ActionRef.Muster,
      Vector.empty).map(_.details), Vector(Vector("1 Supply")))
  }

  test("an option the preview drops is not shown") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val started = rules.startWalker(Ready(board), ActionRef.Muster, actor)
      .getOrElse(fail("the action must start"))
    val parked = started.state.asInstanceOf[Ready].value
    val context = ScopedProjectionContext(parked, Some(actor))
    val withPower = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers(Vector(
        AddAdviserSource(PowerId("test.add-adviser-source")))))
    val options = withPower.project(context).flatMap(_.query).map(_.options)
      .getOrElse(fail("the decision must project"))
    assertEquals(options.map(_.id), Vector(plainId.value))
  }

  test("operation details word the recorded Supply and gains") {
    val actor = PlayerId("p")
    assertEquals(OperationDetails.of(Vector(
      PayCost(actor, Location.OnCard(plainId), Cost(favor = 1)),
      SpendSupply(actor, 1), Gain.Warbands(actor, ForceKind.Imperial, 2),
      Gain.Favor(actor, Suit.Hearth, 3), Gain.Secrets(actor, 1),
      GainSupply(actor, 2))),
      Vector("1 Supply", "+2 warbands", "+3 favor", "+1 secrets", "+2 Supply"))
  }
}
```

Add `import oathdigital.gameplay.EconomyFixture.AddAdviserSource` if the wildcard import does not bring the case class into scope.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.application.EconomyProjectionSuite"`
Expected: compile FAIL, "not found: value OperationDetails".

- [ ] **Step 3: Implement `OperationDetails`**

Create `src/main/scala/oathdigital/application/OperationDetails.scala`:

```scala
package oathdigital.application

import oathdigital.model._

/** The consequences of answering a decision option, worded for display, read
  * off the operations the walk recorded for it. It words only what a player
  * weighs when choosing (Supply spent and what is gained), in the order the
  * operations ran, and only what actually ran: a gain that shrank to nothing
  * was skipped and so is not mentioned.
  */
private[application] object OperationDetails {
  def of(operations: Vector[CoreOperation]): Vector[String] = operations.collect {
    case SpendSupply(_, amount, _) => s"$amount Supply"
    case GainSupply(_, amount) => s"+$amount Supply"
    case Gain.Warbands(_, _, amount) => s"+$amount warbands"
    case Gain.Favor(_, _, amount) => s"+$amount favor"
    case Gain.Secrets(_, amount) => s"+$amount secrets"
  }
}
```

- [ ] **Step 4: Prune and annotate in `WalkerDecisionProjector`**

Add `WalkerSimulation` to the `oathdigital.gameplay.walker` import. Replace the `case None =>` branch of `parked(...)`:

```scala
      case None => ProcedureWalker.parkedDecide(ready, tree, pending, powers)
        .flatMap { decide =>
          val (query, details) = playable(procedure, ready, tree, pending,
            powers, decide)
          queryProjection(ready, Some(awaited), query, details).map(projected =>
            WalkerDecisionProjection(procedure.key, decide.decisionId, "decide",
              query = Some(projected),
              rollOutcome = rollOutcome(ready, awaited)))
        }
```

Add this method after `parked`:

```scala
  /** The decision's query as this viewer is offered it. For a procedure that
    * opts in (`Entry.requiresPlayableOption`), only the options its own
    * preview accepts, each with the consequences that answering it records;
    * for every other procedure the declared query, untouched. A preview that
    * cannot run leaves the declared query, so a projection is never lost to
    * it.
    */
  private def playable(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, pending: PendingTree, powers: WalkerPowers,
      decide: Decide): (DecisionQuery, Map[DecisionOptionRef, Vector[String]]) =
    if (!WalkerProcedureRegistry.requiresPlayableOption(procedure))
      (decide.query, Map.empty)
    else WalkerSimulation.previewParked(ready, tree, pending, powers) match {
      case Left(_) => (decide.query, Map.empty)
      case Right(previewed) =>
        val accepted = previewed.flatMap(option => option.outcome.toOption
          .map(outcome => option.option -> outcome))
        val query = decide.query match {
          case one: DecisionQuery.ChooseOne =>
            one.copy(options = accepted.map(_._1))
          case other => other
        }
        (query, accepted.map { case (option, outcome) =>
          option.ref -> OperationDetails.of(outcome.operations) }.toMap)
    }
```

Change `queryProjection` to take the details and pass them to each option:

```scala
  private def queryProjection(ready: ReadyGame, viewer: Option[PlayerId],
      query: DecisionQuery,
      details: Map[DecisionOptionRef, Vector[String]] = Map.empty)
      : Option[DecisionQueryProjection] = {
    val index = CardIndex.from(ready.game).toOption
    def described(options: Vector[DecisionOption])
        : Option[Vector[DecisionOptionProjection]] = {
      val projected = options.flatMap(option => optionProjection(ready, viewer,
        index, option, details.getOrElse(option.ref, Vector.empty)))
      Option.when(projected.size == options.size)(projected)
    }
```

(keep the rest of the method as it is), and give `optionProjection` a defaulted `details` parameter used by `row`:

```scala
  private[application] def optionProjection(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex], option: DecisionOption,
      details: Vector[String] = Vector.empty): Option[DecisionOptionProjection] = {
    val ref = option.ref
    def row(label: String, card: Option[CardDetailsProjection] = None) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card, details))
```

Amend the doc comment on `queryProjection` that says "The projector never filters an option it merely dislikes": append "One exception is by declaration: a procedure whose registry entry sets `requiresPlayableOption` has its query narrowed by `playable` to the options its own preview accepts."

- [ ] **Step 5: Offer the start controls**

In `LegalActionProjector.scala`, import `oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}`, then add:

```scala
  /** Whether a Muster or Trade could start now: at least one source survives
    * the same preview a start runs. Asked of the procedures that own them.
    */
  private def musterStartable(context: ScopedProjectionContext): Boolean =
    MusterProcedure.startOptions(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
      .exists(_.outcome.isRight)

  private def tradeStartable(context: ScopedProjectionContext,
      resource: TradeResource): Boolean =
    TradeProcedure.startOptions(catalog, context.ready, context.active.player,
      resource, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
      .exists(_.outcome.isRight)
```

and in `controls`, in the `Phase.Act` vector right after the `"beginForge"` entry:

```scala
          Option.when(musterStartable(context))("beginMuster"),
          Option.when(tradeStartable(context, TradeResource.Favor))(
            "beginTradeFavor"),
          Option.when(tradeStartable(context, TradeResource.Secret))(
            "beginTradeSecret"),
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw -no-colors "testOnly oathdigital.application.EconomyProjectionSuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.application.WalkerDecisionProjectionSuite oathdigital.application.PhasePowerProjectorSuite"`
Expected: PASS. Then `./sbtw -no-colors test`: PASS. The legacy `EconomySuite` still passes because the board-target actions and `legalMusters` are untouched until Task 10.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application src/test/scala/oathdigital/application/EconomyProjectionSuite.scala
git commit -m "feat(economy): offer Muster and Trade starts and annotate the parked source

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 8: Frontend start controls and modifier workflow

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/EconomyControls.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/EconomyControlsSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`

**Interfaces:**
- Consumes: the controls `beginMuster`, `beginTradeFavor`, `beginTradeSecret` (Task 7); the generic choose-one panel (Task 2).
- Produces: three buttons that send `StartWalker("muster")` and `StartWalker("trade", [button favor|secret])`; `ModifierWorkflow.walkerActions` includes `muster` and `trade`, so a player-selected modifier is offered before the start exactly as for Recover and Forge.

The old board-target buttons for Muster and Trade keep rendering until Task 10 removes them, so during Tasks 8 and 9 the panel shows both. That is expected on this branch.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/test/scala/oathdigital/frontend/EconomyControlsSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent, WalkerStartArgWire}
import org.scalajs.dom

/** The Muster and Trade start controls at the DOM, under jsdom. */
class EconomyControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    EconomyControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("only the offered controls render, in Muster then Trade order") {
    val (buttons, _) = render(Vector("beginTradeSecret", "beginMuster"))
    assertEquals(buttons.map(_.textContent),
      Vector("Muster (1 Supply)", "Trade for secrets (1 Supply)"))
  }

  test("each control starts its action, with Trade's resource as the start selection") {
    val (_, submitted) = render(
      Vector("beginMuster", "beginTradeFavor", "beginTradeSecret"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "favor"))),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "secret")))))
  }

  test("no control renders when none is offered, and all are disabled without control") {
    assertEquals(render(Vector.empty)._1, Vector.empty)
    val (buttons, _) = render(Vector("beginMuster"), canControl = false)
    assert(buttons.forall(_.disabled))
  }
}
```

In `ModifierSelectionStateSuite`, in the test "targeted actions preview before commands while direct actions retain their stage", add two commands after `GameIntent.StartWalker("forge", Vector.empty),` and update the expectation:

```scala
      GameIntent.StartWalker("forge", Vector.empty),
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))),
```

```scala
    assertEquals(commands.flatMap(ModifierWorkflow.action).map(_._1),
      Vector("recover", "forge", "muster", "trade", "travel", "search", "search"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "frontend/testOnly oathdigital.frontend.EconomyControlsSuite oathdigital.frontend.ModifierSelectionStateSuite"`
Expected: compile FAIL, "not found: value EconomyControls".

- [ ] **Step 3: Implement**

Create `frontend/src/main/scala/oathdigital/frontend/EconomyControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import ServerUiSupport._

/** The Act-phase start controls for Muster and Trade. Each is offered by the
  * engine only while a source survives its preview, so this layer decides
  * nothing: it draws what `legalControls` names. The card is chosen at the
  * parked decision, not here.
  */
private[frontend] object EconomyControls {
  private final case class Control(control: String, kind: String,
      label: String, command: GameCommand)

  // `kind` reuses the board-target action kinds so the action panel groups
  // and orders these under the same Muster and Trade families as before.
  private val controls = Vector(
    Control("beginMuster", "muster", "Muster (1 Supply)",
      GameCommand.StartWalker("muster", Vector.empty)),
    Control("beginTradeFavor", "trade-favor", "Trade for favor (1 Supply)",
      GameCommand.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "favor")))),
    Control("beginTradeSecret", "trade-secret", "Trade for secrets (1 Supply)",
      GameCommand.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "secret")))))

  def render(value: GameProjection, canControl: Boolean,
      groups: ActionSections, submit: GameCommand => Unit): Unit =
    controls.filter(control => value.legalControls.contains(control.control))
      .foreach { control =>
        val node = button(control.label, s"act-action ${control.kind}-action")
        node.disabled = !canControl
        node.onclick = _ => submit(control.command)
        groups.appendKind(control.kind, node)
      }
}
```

In `ActionDecisionRenderer.actionsPanel`, directly after the `beginForge` block (the `if (value.legalControls.contains("beginForge")) { ... }`), add:

```scala
       EconomyControls.render(value, canControl, groups, submitCommand)
```

In `ModifierWorkflow`, change the walker actions set:

```scala
  private val walkerActions: Set[String] = Set("search", "recover", "forge",
    "travel", "muster", "trade")
```

- [ ] **Step 4: Run the tests**

Run: `./sbtw -no-colors "frontend/testOnly oathdigital.frontend.EconomyControlsSuite oathdigital.frontend.ModifierSelectionStateSuite"` then `./sbtw -no-colors frontend/test`
Expected: PASS. If `ActionSections` or `button` is not visible from the new file, they live in `ServerUiSupport` as `private[frontend]`; the `import ServerUiSupport._` above brings them in.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat(frontend): start Muster and Trade from action controls

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 9: Differential parity gate

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/EconomyParitySuite.scala`

**Interfaces:**
- Consumes: the legacy `Economy` (still present), `MusterProcedure`, `TradeProcedure`, `EconomyFixture`.
- Produces: proof, before any deletion, that for every legal scenario the walker path yields the same candidate set and order, the same cost and gain, and the same authoritative state, `CardIndex` and projections as the legacy path. Task 12 deletes this suite together with the code it compares against.

Parity is possible only in exile-only, fixed-unaltered-Foundation states, because legacy rejects every other state. Every fixture below is one.

- [ ] **Step 1: Write the suite**

Create `src/test/scala/oathdigital/gameplay/EconomyParitySuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{Economy, EconomyCommand}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Legacy versus walker, on exile-only boards with the fixed unaltered
  * Foundation profile, the only states the legacy path accepts.
  *
  * A scenario is compared three ways: the candidates each path offers (target,
  * Supply cost and gain, in order), the authoritative state each ends in, and
  * the projections of those states. A scenario the legacy path rejects must be
  * offered by the walker path nowhere: no candidate and no start.
  */
class EconomyParitySuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private val boards: Vector[(String, ReadyGame)] = Vector(
    "matching adviser" -> act(advisers = Vector(matchingAdviser)),
    "no adviser" -> act(),
    "warband shortage" -> act(boardWarbands = 14),
    "warband shortage with an adviser" -> act(
      advisers = Vector(matchingAdviser), boardWarbands = 13),
    "bank shortage" -> act(advisers = Vector(matchingAdviser), bank = 1),
    "empty bank" -> act(bank = 0),
    "ruined edifice" -> spring(act(), EdificeSide.Ruined),
    "intact edifice" -> spring(act(), EdificeSide.Intact),
    "no favor" -> act(favor = 0),
    "one favor" -> act(favor = 1),
    "no secrets" -> act(secrets = 0),
    "no supply" -> act(supply = 0),
    "occupied card" -> act(tokens = Tokens(0, 1)))

  private def cardOf(option: DecisionOption): CardId = option.ref match {
    case DecisionOptionRef.Denizen(id) => id
    case DecisionOptionRef.Edifice(id) => id
    case other => fail(s"unexpected source $other")
  }

  private def refOf(target: EconomyTargetRef): DecisionOptionRef = target match {
    case EconomyTargetRef.Denizen(id) => DecisionOptionRef.Denizen(id)
    case EconomyTargetRef.Edifice(id) => DecisionOptionRef.Edifice(id)
  }

  private def supplySpent(operations: Vector[CoreOperation]): Int =
    operations.collect { case SpendSupply(_, amount, _) => amount }.sum

  private def walkerCandidates(previewed: Vector[PreviewedOption])
      (gain: Vector[CoreOperation] => Int): Vector[(CardId, Int, Int)] =
    previewed.collect { case PreviewedOption(option, Right(outcome)) =>
      (cardOf(option), supplySpent(outcome.operations), gain(outcome.operations))
    }

  private def warbands(operations: Vector[CoreOperation]): Int =
    operations.collect { case Gain.Warbands(_, _, amount) => amount }.sum
  private def favor(operations: Vector[CoreOperation]): Int =
    operations.collect { case Gain.Favor(_, _, amount) => amount }.sum
  private def secrets(operations: Vector[CoreOperation]): Int =
    operations.collect { case Gain.Secrets(_, amount) => amount }.sum

  private def walkerRun(ready: ReadyGame, action: StartableRef,
      args: Vector[DecisionOptionRef], decisionId: String,
      ref: DecisionOptionRef): Either[OathViolation, OathState] = for {
    started <- rules.startWalker(Ready(ready), action, player(ready).player,
      startArgs = args)
    finished <- rules.resolveWalker(started.state, player(ready).player,
      decisionId, DecisionAnswer.ChooseOneAnswer(ref))
  } yield finished.state

  private def assertSameOutcome(name: String, ready: ReadyGame,
      legacy: OathState, walker: OathState): Unit = {
    assertEquals(walker, legacy, s"$name: authoritative state")
    val Ready(expected) = legacy: @unchecked
    val Ready(actual) = walker: @unchecked
    assertEquals(CardIndex.from(actual.game), CardIndex.from(expected.game),
      s"$name: card index")
    ready.game.current.players.map(_.player).foreach { viewer =>
      assertEquals(projector.project("parity", LoadedGame(walker, 9), viewer),
        projector.project("parity", LoadedGame(legacy, 9), viewer),
        s"$name: projection for ${viewer.value}")
    }
  }

  boards.foreach { case (name, ready) =>
    val actor = player(ready).player

    test(s"Muster parity: $name") {
      val legacy = Economy.legalMuster(catalog, ready, player(ready))
      val walker = walkerCandidates(MusterProcedure.startOptions(catalog, ready,
        actor, WalkerPowers.empty))(warbands)
      assertEquals(walker,
        legacy.map(result => (result.target.id, result.supplySpent,
          result.warbandsGained)), s"$name: candidates")
      if (legacy.isEmpty) assert(rules.startWalker(Ready(ready), ActionRef.Muster,
        actor).isLeft, s"$name: a start must be rejected")
      legacy.foreach { result =>
        val expected = rules.handle(Ready(ready),
          EconomyCommand.Muster(actor, result.target)).toOption.get.state
        val actual = walkerRun(ready, ActionRef.Muster, Vector.empty,
          MusterProcedure.decisionId, refOf(result.target))
          .getOrElse(fail(s"$name: the walker must accept ${result.target}"))
        assertSameOutcome(s"$name muster ${result.target.id.value}", ready,
          expected, actual)
      }
    }

    Vector[(TradeResource, (String, Vector[CoreOperation] => Int))](
      TradeResource.Favor -> (("favor", favor _)),
      TradeResource.Secret -> (("secret", secrets _))).foreach {
      case (resource, (key, gain)) =>
        test(s"Trade for $key parity: $name") {
          val args = Vector[DecisionOptionRef](DecisionOptionRef.Button(key))
          val legacy = Economy.legalTrades(catalog, ready, player(ready))
            .filter(_.resource == resource)
          val walker = walkerCandidates(TradeProcedure.startOptions(catalog,
            ready, actor, resource, WalkerPowers.empty))(gain)
          assertEquals(walker,
            legacy.map(result => (result.target.id, result.supplySpent,
              result.gained)), s"$name: candidates")
          if (legacy.isEmpty) assert(rules.startWalker(Ready(ready),
            ActionRef.Trade, actor, startArgs = args).isLeft,
            s"$name: a start must be rejected")
          legacy.foreach { result =>
            val expected = rules.handle(Ready(ready), EconomyCommand.Trade(actor,
              result.target, resource)).toOption.get.state
            val actual = walkerRun(ready, ActionRef.Trade, args,
              TradeProcedure.decisionId, refOf(result.target))
              .getOrElse(fail(s"$name: the walker must accept ${result.target}"))
            assertSameOutcome(s"$name trade $key ${result.target.id.value}",
              ready, expected, actual)
          }
        }
    }
  }
}
```

- [ ] **Step 2: Run the suite**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.EconomyParitySuite"`
Expected: PASS for every board. When a comparison fails, do not weaken the assertion. Diagnose in this order:
1. Candidates differ: print both vectors. A difference in gain with a shortage means the best-effort shrink and legacy `min` disagree; check `OperationStateAdapter.warbandsInBank` against `Economy.availableWarbands` (they agree for an Exile kind by the spec).
2. State differs: compare `walker` and `legacy` field by field with `assertEquals(actual.game.current, expected.game.current)`; munit prints the differing field. Legitimate differences must be limited to the removed gates; anything else is a defect in the tree.
3. The two candidate lists differ only by a candidate with a zero gain: legacy lists a Trade whose yield is zero (for example Trade for secrets with no matching adviser). The walker offers it too, because the payment is still legal; if it does not, the Skip of a zero gain is wrongly failing the option.

- [ ] **Step 3: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/EconomyParitySuite.scala
git commit -m "test(economy): prove legacy and walker Muster and Trade agree

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 10: Retire the legacy Muster and Trade projection and client surface

**Files:**
- Delete: `src/test/scala/oathdigital/gameplay/EconomySuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/EconomyWalkerSuite.scala` (two replacement tests)
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala`, `ScopedProjectionContext.scala`, `GameProjection.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala`, `WorldProjectionDtos.scala`, `GameProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/package.scala`, `ServerUiSupport.scala`, `ModifierWorkflow.scala`
- Modify tests: `shared/.../ProjectionProtocolSuite.scala`, `frontend/.../ServerModeUiSuite.scala`, `ModifierSelectionStateSuite.scala`, `BoardTargetSelectionStateSuite.scala`, `HttpGameClientSuite.scala`

**Interfaces:**
- Consumes: the walker start controls and parked decision (Tasks 6 to 8), and the parity proof (Task 9).
- Produces: a projection with no `legalMusters`, `legalTrades`, or `muster`/`trade-favor`/`trade-secret` board-target actions; a frontend that starts Muster and Trade only through `EconomyControls`.

This is one commit because the shared DTO change reaches the backend, the frontend and their tests together; the build does not compile in between.

- [ ] **Step 1: Move the legacy suite's coverage over, then delete it**

`EconomySuite` covered the legacy commands, which Task 9 proved equal to the walker path. Its remaining assertions map to suites that already exist: costs and yield to `MusterProcedureSuite`/`TradeProcedureSuite`; the Oathkeeper boundary to `EconomyWalkerSuite`; occupied and poor legality to the procedure suites and `EconomyParitySuite`; redacted controls to `EconomyProjectionSuite`; the edifice target to `MusterProcedureSuite` and the service test. Two behaviours have no walker test yet. Add them to `EconomyWalkerSuite` first:

```scala
  test("a lineage with no warband supply cannot Muster") {
    val board = act()
    val actor = player(board)
    val malformed = board.copy(banks = board.banks.copy(warbandSupply =
      board.banks.warbandSupply - ForceKind.Exile(actor.lineage)))
    assert(start(malformed).isLeft)
  }

  test("an unimplemented optional Economy power does not block a base Trade") {
    val board = spring(act(), EdificeSide.Intact)
    val actor = player(board).player
    val started = start(board, ActionRef.Trade, secret)
      .getOrElse(fail("the Trade must start"))
    val finished = answer(started.state, actor, TradeProcedure.decisionId,
      DecisionOptionRef.Edifice(springId))
    assert(finished.isRight, finished.toString)
  }
```

Then run `./sbtw -no-colors "testOnly oathdigital.gameplay.EconomyWalkerSuite"` (PASS) and delete the legacy suite: `git rm src/test/scala/oathdigital/gameplay/EconomySuite.scala`.

- [ ] **Step 2: Remove the backend projection**

`LegalActionProjector.scala`:
1. In the `oathdigital.gameplay.actions` import, delete `Economy, ` so it reads `{BannerRules, CampaignRules, ChallengeRules, ForgeRules, VisionRules, Visions}`.
2. In `project`, delete the two arguments that build `LegalMusterProjection` and `LegalTradeProjection` (the `if (ordinaryAct) Economy.legalMuster(...)` and `if (ordinaryAct) Economy.legalTrades(...)` expressions). The remaining arguments are, in order: controls, travel, search, the pending-match for board targets, `minor`.
3. In `boardTargetActions`, delete `val musters`, `val trades`, `val favor` and `val secret`, and delete the last three entries of the final `Vector(...)` (`selection("muster", ...)`, `selection("trade-favor", ...)`, `selection("trade-secret", ...)`). The entry before them, `Option.when(hasConspiracy)(BoardTargetActionProjection(...))`, then ends the vector: remove its trailing comma so the vector closes with `).flatten`.
4. Delete the `economyLabel` and `economyCandidate` helpers.

`ScopedProjectionContext.scala`: remove `musters` and `trades` from `LegalProjection`.
`GameProjection.scala`: remove `legalMusters = legal.musters,` and `legalTrades = legal.trades,`.
`GameRoutes.scala`: delete the `case "muster" =>` and `case "trade" =>` branches of the preview-target match (the spec clarification: nothing consumes them once the board-target actions are gone).

- [ ] **Step 3: Remove the shared DTOs and codec**

`GameProjectionDto.scala`: delete `legalMusters` and `legalTrades`. `WorldProjectionDtos.scala`: delete `LegalMusterProjection` and `LegalTradeProjection`. `GameProjectionCodec.scala`:
- delete `"legalMusters", "legalTrades",` from the `Fields` set;
- delete the two `"legalMusters" -> ...` and `"legalTrades" -> ...` entries in `encode`;
- delete the four decode lines beginning `musterRaws`, `musters`, `tradeRaws`, `trades`;
- delete `musters, trades,` from the positional `GameProjection(...)` call in the final `yield`;
- delete the private helpers `decodeMuster`, `decodeTrade` and the `decodeEconomy` helper they use (search for `expected denizen or edifice`).

- [ ] **Step 4: Remove the frontend surface**

- `package.scala`: delete the `LegalMuster` and `LegalTrade` type aliases and their companion objects (keep `EconomyTarget` in `GameClient.scala` until Task 11).
- `ServerUiSupport.scala`: delete the three `case ("muster", ...)`, `case ("trade-favor", ...)`, `case ("trade-secret", ...)` branches of `commandForSelection`, and the three `actionLabel` cases for `"muster"`, `"trade-favor"`, `"trade-secret"`. Keep the family and category entries that mention them: `EconomyControls` groups its buttons under those kinds.
- `ModifierWorkflow.scala`: delete the `"muster"`, `"trade-favor"` and `"trade-secret"` entries of `targetedActions`.

- [ ] **Step 5: Migrate the tests**

- `ProjectionProtocolSuite`: delete the `legalMusters = ...` and `legalTrades = ...` lines of the shared `projection` value.
- `HttpGameClientSuite`: delete the whole test "Economy encodes typed intents and decodes authoritative yields".
- `ServerModeUiSuite`: delete the three `commandForSelection` assertions for `muster`, `trade-favor` and `trade-secret`; delete the `actionLabel("trade-secret")` assertion; in "inactive Act viewer sees no action-selection controls" remove the `legalMusters` and `legalTrades` arguments so the `.copy(...)` ends after `legalTravelDestinations = Vector(LegalTravelDestination("site:1", 2)))`.
- `ModifierSelectionStateSuite`: in "targeted actions preview before commands while direct actions retain their stage", remove `"muster"`, `"trade-favor"`, `"trade-secret"` from the first vector and `"muster", "trade", "trade"` from its expectation, so it reads `Vector("travel", "campaign-conquest", "campaign-raid", "play-facedown-adviser")` and `Vector("travel", "campaign", "campaign", "search")`. In "zero modifiers skip ordering and Economy still requires explicit confirmation" and "target Back restores ordering only when present and stale context clears flow", the kinds are only labels for generic behaviour: rename the first test to "zero modifiers skip ordering and a targeted action still requires explicit confirmation", and replace `"trade-secret"`/`"trade-favor"` with `"travel"`, the response action `"trade"` with `"travel"`, and `Map("resource" -> ...)` with `Map.empty[String, String]`.
- `BoardTargetSelectionStateSuite`: rename "Economy target mode requires explicit confirmation and supports cancel" to "explicit-confirm target mode requires confirmation and supports cancel" and use `"travel"` for the action kind.

- [ ] **Step 6: Compile and run everything**

Run: `./sbtw -no-colors "Test / compile" "frontend / Test / compile"`
Expected: no errors. A leftover reference points at a symbol this task removed; delete or migrate it in the same way.

Run: `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(economy): retire the legacy Muster and Trade projection

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 11: Retire the legacy Muster and Trade commands and intents

**Files:**
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentDecoders.scala`, `CommandIntentCodec.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala`
- Modify tests: `shared/.../CommandProtocolSuite.scala`, `src/test/.../GameHttpWireSuite.scala`, `PendingWalkerInvariantSuite.scala`, `GameApplicationServiceSuite.scala`, `frontend/.../HttpGameClientSuite.scala`, `ProtocolTestCommands.scala`

**Interfaces:**
- Produces: no `GameCommand.Muster/Trade`, no `GameIntent.Muster/Trade`, no `EconomyTarget` wire type. Muster and Trade are reachable only as `StartWalker("muster" | "trade")` followed by `ResolveWalker`.

- [ ] **Step 1: Remove the application commands**

- `GameCommands.scala`: delete `Muster` and `Trade` from `GameCommand`.
- `Authorization.scala`: delete `muster` and `trade` (the two methods returning `GameCommand.Muster`/`GameCommand.Trade`).
- `GameIntentMapper.scala`: delete the `Intent.Muster` and `Intent.Trade` cases, and the private `trade` and `economy` helpers.
- `GameApplicationService.scala`: delete the `case GameCommand.Muster(...)` and `case GameCommand.Trade(...)` dispatch branches, the two `MajorActionKind.Muster`/`Trade` lines in the action-kind match, and `EconomyCommand` from the `oathdigital.gameplay.actions` import. In the doc comment above `walkerAction`, replace the sentence "a legacy-only kind like `Muster` simply has no matching `ActionRef`" with "a legacy-only kind like `Campaign` simply has no matching `ActionRef`".

- [ ] **Step 2: Remove the wire intents**

- `CommandIntents.scala`: delete `Muster`, `Trade` and the `EconomyTarget` case class.
- `CommandIntentDecoders.scala`: delete the `"muster"` and `"trade"` decode cases and the private `economy` helper.
- `CommandIntentCodec.scala`: delete the `Muster` and `Trade` encode cases and the private `economy` helper.
- `GameClient.scala` (frontend): delete `final case class EconomyTarget`.

- [ ] **Step 3: Migrate the tests**

- `CommandProtocolSuite`: delete the `Muster(...)` and `Trade(...)` examples.
- `GameHttpWireSuite`, test "domain conversion rejects unknown protocol identifiers without throwing": replace the `Trade` intent with an intent whose start selection has an unknown kind:

```scala
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("teleport", "x"))))
      .left.toOption.get
    assertEquals(failure.path, "$.intent.startArgs[0]")
```

- `PendingWalkerInvariantSuite`: delete the `GameCommand.Muster(...)` and `GameCommand.Trade(...)` entries of `everyCommand`.
- `GameApplicationServiceSuite`: in the test that starts a walker Recover and then submits a legacy command expecting rejection, replace `GameCommand.Muster(actor, EconomyTargetRef.Denizen(DenizenId("any-denizen")))` with `GameCommand.BeginChallenge(actor, Banner.PeoplesFavor)`; delete the legacy test "ruined edifice Economy target persists and replays with its kind" (the walker test from Task 6 replaces it).
- `HttpGameClientSuite`, test "production client previews and submits the same ordered modifiers": replace `GameIntent.Trade(oathdigital.protocol.EconomyTarget("denizen", "10"), "favor")` with `GameIntent.BeginChallenge("peoples-favor")`.
- `ProtocolTestCommands.scala`: delete the `Muster` and `Trade` helper definitions.

- [ ] **Step 4: Compile and run everything**

Run: `./sbtw -no-colors "Test / compile" "frontend / Test / compile"` (no errors), then `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test` (PASS). `OathRules.handle(EconomyCommand)` and `Economy` still exist and are still exercised by `EconomyParitySuite`.

- [ ] **Step 5: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(economy): retire the legacy Muster and Trade commands and intents

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 12: Delete the legacy Economy rules, events and codecs

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/actions/Economy.scala`, `src/test/scala/oathdigital/gameplay/EconomyParitySuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`, `model/GameEventProtocol.scala`, `model/Cards.scala`, `model/GameViolation.scala`, `gameplay/setup/FirstGameSetup.scala`, `serialization/ActionEventCodec.scala`, `serialization/GameEventWire.scala`, `serialization/GameEventJsonSupport.scala`
- Modify tests: `src/test/.../GameEventWireSuite.scala`, `PendingWalkerRulesSuite.scala`

**Interfaces:**
- Produces: no `Economy`, `EconomyCommand`, `MusterResult`, `TradeResult`, `Mustered`, `Traded`, `EconomyTargetRef`, `EconomySourceMismatch` or `EconomyOutcomeMismatch`. `TradeResource` stays (the Trade procedure parses its start button into it). `EconomyCardUnavailable`, `EconomyCardNotEmpty` and `UnsupportedEconomyState` stay (`MusterSource.resolve` and the tree use them).

- [ ] **Step 1: Delete the rules and the parity gate**

`git rm src/main/scala/oathdigital/gameplay/actions/Economy.scala src/test/scala/oathdigital/gameplay/EconomyParitySuite.scala`. The parity suite compared the walker to the code deleted here, so it ends with it; the walker's own expectations live in `MusterProcedureSuite`, `TradeProcedureSuite`, `EconomyWalkerSuite` and `EconomyProjectionSuite`.

- [ ] **Step 2: Remove the rules dispatch**

`OathRules.scala`: delete `Economy, EconomyCommand` from the `oathdigital.gameplay.actions` import list, the whole `def handle(state: OathState, command: EconomyCommand)` method, and the two evolve cases `case event: Mustered => ...` and `case event: Traded => ...`.

- [ ] **Step 3: Remove the events and their codecs**

- `GameEventProtocol.scala`: delete `Mustered` and `Traded` from `OathEvent`; keep the `TradeResource` trait and object further down.
- `ActionEventCodec.scala`: delete the two `actionDiscriminator` cases, the two `actionEncoder` cases and the two `actionDecode` cases for `Mustered`/`Traded`.
- `GameEventWire.scala`: delete the `MusteredType` and `TradedType` constants and any place listing them (`grep -rn "MusteredType\|TradedType" src` must return nothing).
- `GameEventJsonSupport.scala`: delete `decodeEconomyTarget`.
- `FirstGameSetup.scala`: delete the `case _: Mustered | _: Traded => Left(InvalidEventOrder("Economy requires the gameplay evolution"))` branch.
- `Cards.scala`: delete `EconomyTargetRef` (`grep -rn "EconomyTargetRef" src shared frontend/src` must return nothing first).
- `GameViolation.scala`: delete `EconomySourceMismatch` and `EconomyOutcomeMismatch`.

- [ ] **Step 4: Migrate the tests**

- `GameEventWireSuite`: delete the test "v6 Economy events round-trip source cost yield and NF resource mode" and `Mustered, Traded` from the `OathEvent` import.
- `PendingWalkerRulesSuite`: delete the `"economy" -> rules.handle(state, EconomyCommand.Muster(...))` entry and `EconomyCommand` from its import. The suite's loop over `StartableRef.all` already proves Muster and Trade cannot start over a parked decision.

- [ ] **Step 5: Verify nothing is left**

Run:

```bash
git grep -n -E "Mustered|Traded|EconomyCommand|EconomyTargetRef|EconomyTarget|legalMusters|legalTrades|LegalMuster|LegalTrade|MusterResult|TradeResult|Economy\.(handle|evolve|legal)" -- src shared frontend
```

Expected: no output. Then `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`: PASS. `BackendArchitectureSuite` must stay green.

- [ ] **Step 6: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(economy): delete the legacy Economy rules, events and codecs

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 13: Documentation and final verification

**Files:**
- Modify: `docs/superpowers/specs/2026-09-18-economy-walker-design.md` (status line)
- Modify: `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (status line and roadmap step 3)

- [ ] **Step 1: Update the specs**

In `2026-09-18-economy-walker-design.md`, change the status line to: `> Status: implemented by [the plan](../plans/2026-09-18-economy-walker.md). Extends the [procedure walker design]...` (keep the links that follow).

In `2026-09-05-procedure-walker-design.md`:
- In the status line, append: "Muster and Trade have since moved onto the walker (see the [Muster and Trade design](2026-09-18-economy-walker-design.md))."
- In roadmap step 3 ("Port remaining actions in batches"), after the "*In progress:*" sentence, add: "Economy (Muster and Trade) is ported and its legacy path deleted; see the Muster and Trade design."

- [ ] **Step 2: Full verification**

Run `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`. Expected: PASS for both.

Run `git grep -n -i -E "muster|trade" -- src/main/scala/oathdigital/gameplay/actions src/main/scala/oathdigital/application | grep -v -i "economy/"` and read the hits: the only remaining ones should be the walker registry entries, `PowerRuntime`'s fallback kinds, the 24 diagnostic powers and the new controls and projection.

Play one Muster and one Trade by hand if a dev server is available (`./sbtw run`): the Act panel shows the three start buttons for a player who can afford them, starting one parks on a card list whose entries show `1 Supply · +N ...`, choosing a card completes the action, and a player who cannot afford any of them sees no button.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs
git commit -m "docs: record Muster and Trade as ported to the walker

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.**
- Procedure composition (Decide, Branch, windows, MusterSource, costs and gains, gates, registry entries): Tasks 4 to 6.
- Start flow (two steps, controls, generic panel): Tasks 7 and 8.
- Preview of a parked decision and its three uses (projection, start, answer): Tasks 3, 6 and 7.
- Vocabulary (`Edifice` reference, option `details`): Tasks 1 and 2.
- Extension by powers (Transform on the decision window, both known extension points named): Task 5's hook test and its doc comments.
- Cutover and deletion, in the spec's order: preview and windows (Tasks 1 to 4), procedures and registry (5 and 6), parity (9), deletion (10 to 12).
- Verification (parity fixtures limited to exile-only boards, illegal states, journal round trip and replay, edifice reference through every codec, migrated tests): Tasks 1, 5, 6, 9, 10.
- Removed gates and the untested Imperial paths: stated in the Global Constraints; no Imperial Muster/Trade test is written, and `PlayerForceKindSuite` tests only the pure mapping.
- Settled items: token-free (Task 5 `resolve`), no manual `min` (Task 5 tree), a rejected start leaves no state (Task 6 test), warband supply check dropped (no gate in `EconomyTree.build`; Task 10 test asserts the executor's rejection).
- Left-for-plan items: the start rejection's registry opt-in and violation (Task 6); the legacy site-ruler mapping check is kept as a start gate (`siteRulers`, clarification 10), because nothing else rejects an unknown or duplicated lineage on a site.

**Placeholder scan.** No "TBD", "TODO" or "similar to Task N". Every code step shows code; deletion steps name the symbol, the file and the surrounding lines.

**Type consistency.** `PreviewedOption` and `PreviewOutcome` are nested in `WalkerSimulation` and imported as `WalkerSimulation.PreviewedOption`. `startOptions` returns `Vector[PreviewedOption]` for both procedures and is the only thing `LegalActionProjector`, the parity suite and the tests consume. `requiresPlayableOption` is spelled the same in `Entry`, the accessor, `OathRulesWalker` and `WalkerDecisionProjector`. `MusterProcedure.decisionId` and `TradeProcedure.decisionId` are the values the registry's `continuationFor` compares against. The control strings `beginMuster`, `beginTradeFavor` and `beginTradeSecret` are identical in `LegalActionProjector`, `EconomyProjectionSuite` and `EconomyControls`.

## Execution notes

Where execution departed from the plan's code samples. Each was found by a failing test, not by inspection.

1. **The payment is a bare `PayCost`, which needed a walker fix.** `PayCost` is a composite, and the walker used to walk its best-effort `Move` children one at a time, dropping the composite's `required`: an unaffordable payment shrank to nothing. That was a walker bug, not an Economy quirk; it affected every required composite (`PayCost`, `Draw`, `Exchange`, a required `Play`/`Replace`/`Discard`). The walker now marks the children of a required composite strict (`WalkerHooks.strict`, `OperationPipeline.run(requireAll)`), so a reduced or skipped child rejects. Until that fix `EconomyTree` wrapped the payment in a `BuildOps` batch; it now puts `PayCost` and `SpendSupply(1)` directly under the `<Action>Cost` window. A power that changes or removes the cost rewrites those children, and whatever payment remains is enforced after the fold. An unaffordable payment is a rejection, so the preview drops the option and the start gate rejects a start with nothing playable. (A first version checked `Costs.plan` before the fold; review found that hid the cost window from powers.)
2. **Recorded gains are moves.** The walker records the `Move` a `Gain` expands into, not the `Gain`. `OperationDetails` and the parity suite read a move from a bank to the actor's play area; a `Gain` is worded the same way for a caller holding the requested operation.
3. **Test-only power.** `WalkerPreviewSuite` uses the existing `ProcedureWalkerSuite.TestTransformPower` instead of a new power class.
4. **Task 8 to 12 counts.** The parity suite (39 tests) passed on its first run and was checked with a mutation of the Muster gain, which made 8 of them fail. It was deleted with the code it compared against.
