# Visions and Conspiracy on the Procedure Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run Reveal Vision and Conspiracy as ordinary facedown-adviser and Search card plays on the procedure walker, with Conspiracy as a `WHEN PLAYED` power, and delete the legacy Visions path.

**Architecture:** Conspiracy becomes an always-present `ContributingPower` at `PowerWindow.ActionCardPlayed` that inserts a target `Decide`, the take effects and the removal of the card. The card leaves the game through a generic `Move` of a Vision to `Location.SharedBank`, which the card-inventory check allows when the batch declares it. Reveal is the existing `planVision` facedown case, enabled by removing an unconditional rejection. The legacy `Visions` object, its commands, intents, events, codecs, pending state and the minor-action audit are deleted last.

**Tech Stack:** Scala 2.13, sbt (`./sbtw`), munit, Scala.js frontend (jsdom tests), ujson.

**Spec:** `docs/superpowers/specs/2026-09-19-visions-conspiracy-walker-design.md`. Read it first; this plan implements it and only departs from it where "Spec clarifications" below says so.

## Global Constraints

- The legacy minor-action audit (`MinorActionPowerSupport.validateFaceupVision`, the handler-inventory fingerprint, the altered-Foundation check) is removed entirely, not ported. Vow of Obedience, Secret Police, Book Binders and the E08 faces become `Restriction` powers later; this slice does not implement them.
- Conspiracy is a `ContributingPower` at `PowerWindow.ActionCardPlayed`, found the way `TakeWealthLimit` is: always present, no catalog lookup. It is automatic, not player-selected.
- Conspiracy and Reveal are ordinary facedown-adviser plays (`ActionRef.PlayFacedownAdviser`) and Search-kept card plays. No new procedure, registry entry or walker change.
- A played Conspiracy places nothing: no `Play` operation, no revealed-Vision replacement, no replacement decision. The card stays at its origin until the power removes it.
- The target is chosen by a `Decide` whose id is `cardplay.conspiracy.target`, so the registry's existing `cardplay.` prefix match supplies the continuation. With no legal target there is no `Decide`, and the power only removes the card. A legal target cannot be declined.
- A legal target is a relic slot or a banner held by another player whose pawn is at the actor's site.
- The card leaves the game as `Move(Piece.Card(conspiracy), from, PositionedLocation(Location.SharedBank))`. Only a `VisionId` may be moved to `SharedBank`. The batch's declared removals are subtracted from the pipeline's expected card set; any other missing card still fails with `CardInventoryChanged`.
- `DecisionOptionRef.RelicSlot(owner, slot)` is opaque and keeps its owner. `DecisionOptionRef.Banner(banner)` carries only the banner; the holder is read from live state.
- Options render in the generic choose-one decision panel. No board-target selection is kept for Conspiracy.
- Reveal costs no Supply. The action boundary runs after both actions, as the legacy path's did.
- Pre-release history compatibility is not required by the approved walker design.
- Repository rules enforced by `BackendArchitectureSuite`: production Scala files stay under 800 lines; modules under `gameplay/actions`, `gameplay/phases` and `gameplay/powers` change owned material only through core operations; nothing under `gameplay/walker` or `gameplay/operations` names a specific power (the scan is a lowercase substring match on the power's declared name, so the power is named `ConspiracyWhenPlayed`); a power imports nothing from `gameplay.walker`.
- Persisted text (code, comments, docs, commit messages) is plain English. End every commit message with the trailer `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Commands: backend tests `./sbtw -no-colors "testOnly <class>"`; frontend tests `./sbtw -no-colors "frontend/testOnly <class>"`; everything `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`.

## Spec clarifications

Found while planning; each is a decision this plan makes where the spec was silent, or a question the spec left open that reading the code settled.

1. **The banner option label.** The spec says labels follow the legacy ones (`<owner> <banner>`), but `DecisionOptionRef.Banner` carries no owner. The projector reads the holder from live state and labels the option `<holder> <banner>`. A banner nobody holds is absent from state, so it suppresses the decision, as an absent card does.
2. **`resultingSource` is not read.** The spec's "Left for the plan" asks whether anything dereferences `CardPlayed.resultingSource`. `git grep resultingSource src/main` shows only its declaration, so the `Adviser` ref `CardPlay.playedSource` returns is safe when the card is still in the temporary hand.
3. **The `DomainValidation` allowance.** `DomainValidation.validate(game, expectedCards)` and `CardIndex.from(game, expectedCards)` take the same expected set the pipeline passes, and only flag cards in that set that are missing. Subtracting the declared removals from the set is therefore the whole allowance. A state without a boxed Conspiracy is already valid: `Campaign.scala` produces one when a Raid boxes it.
4. **The frontend facedown draft already offers Visions.** `LegalActionProjector.minorActionsProjection` lists every facedown adviser, Visions included, and `FacedownAdviserDraft` starts `play-facedown-adviser` with the card's own kind and id. No frontend change is needed to reach Reveal or a facedown Conspiracy. The generic decision panel (`WalkerPanelSupport.chooseOneStep`) renders any option kind from its `kind`/`id` pair, so the two new kinds need no frontend change either.
5. **`VisionRules` outlives `Visions.scala`.** `VisionRules` (the Vision constants and `goals`) lives in `Visions.scala`, but `VisionVictoryEligibility`, `CardPlay`, the power and the projector still use it. Task 6 moves it to its own file before deleting `Visions.scala`.
6. **`BeginConspiracy` is deleted in Task 3.** The spec lists it under the final deletion, but Task 3 stops producing it, and leaving an unused operation with its codec and state mutation for three tasks only invites drift. It goes with its cause.
7. **`ConspiracyUnavailable` stays.** The power rejects a stale or missing target with it. `ConspiracyOutcomeMismatch`, `ConspiracyDecisionMismatch`, `VisionUnavailable` and the audit's violations are deleted in Task 6.
8. **Facedown Conspiracy waits for Task 4.** `CardPlay.validateOrigin` rejects every faceup Vision play from a facedown adviser until Task 4 removes that call, so Task 3's tests cover the Search route and Task 4 adds the facedown route.
9. **The stale-target guard.** The spec's "a stale answer rejects" is tested at the decision boundary: an answer that names a target the current state no longer offers is rejected by the walker's option check. The re-check inside the effect `BuildOps` runs against the same state in the same command, so it cannot disagree with the option check; it stays as defence in depth and has no separate test.

## File map and interfaces

Created:
- `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`: the power. `case object ConspiracyWhenPlayed extends ContributingPower` with `val id: PowerId = PowerId("vision.conspiracy")` and `val decisionId: String = "cardplay.conspiracy.target"`.
- `src/main/scala/oathdigital/gameplay/actions/VisionRules.scala` (Task 6): `VisionRules` moved out of `Visions.scala`, unchanged.
- `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`: walker-level power tests.
- `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`: service-level Conspiracy and Reveal tests.

Modified, by task:
- Task 1: `OperationCardMutation.scala`, `OperationValidator.scala`, `OperationPipeline.scala`; tests in `OperationPipelineSuite.scala` and `GameEventWireSuite.scala`.
- Task 2: `model/Decisions.scala`, `application/WalkerDecisionProjector.scala`; tests in `DecisionOptionRefSuite.scala`, `GameEventWireSuite.scala`, `WalkerDecisionProjectorSuite.scala`.
- Task 3: `CardPlay.scala`, `CardPlayProcedure.scala`, `WalkerPowerCatalog.scala`, `CoreOperations.scala`, `OperationStateMutation.scala`, `WalkerOperationCodec.scala`; tests in `CardPlayProcedureSuite.scala`, `SearchProcedureSuite.scala`.
- Task 4: `CardPlay.scala`; tests in `CardPlayProcedureSuite.scala`, `ConspiracyWhenPlayedSuite.scala`, `VisionPlaySuite.scala`.
- Tasks 5 and 6: deletions (listed per task).

Interfaces later tasks rely on:
- `OperationRun.boxed(executed: Vector[CoreOperation]): Set[CardId]` (Task 1).
- `DecisionOptionRef.RelicSlot(owner: PlayerId, slot: Int)`, kind `"relic-slot"`, wire id `"<owner>:<slot>"`; `DecisionOptionRef.Banner(banner: oathdigital.model.Banner)`, kind `"banner"`, wire id the banner key; `DecisionOption.RelicSlot(ref)` and `DecisionOption.Banner(ref)` (Task 2).
- `ConspiracyWhenPlayed.decisionId` (Task 3).

---

### Task 1: Boxing a Vision with `Move` to `SharedBank`

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationCardMutation.scala` (`insertCard`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala` (`cardDestinationViolation`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala` (`OperationRun`, `run`)
- Test: `src/test/scala/oathdigital/gameplay/OperationPipelineSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: `Move`, `Piece.Card`, `PositionedLocation`, `Location.Hand`, `Location.SharedBank` (existing, `oathdigital.model`).
- Produces: `OperationRun.boxed(executed: Vector[CoreOperation]): Set[CardId]`, and the guarantee that a batch containing `Move(Piece.Card(vision), from, PositionedLocation(Location.SharedBank))` passes the pipeline's card-inventory check with that card gone from the game.

- [ ] **Step 1: Write the failing tests**

In `OperationPipelineSuite`, add these imports at the top (after the existing ones):

```scala
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
```

and add these tests before the closing brace of the class:

```scala
  private def holding(card: WorldCardId): (ReadyGame, PlayerId) = {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    (base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(card)))),
      actor)
  }

  test("a Vision moved to the shared bank leaves the game and the card " +
      "inventory allows it") {
    val card = VisionRules.Conspiracy
    val (state, actor) = holding(card)
    val box = Move(Piece.Card(card), PositionedLocation(Location.Hand(actor)),
      PositionedLocation(Location.SharedBank))
    val run = OperationPipeline.run(state, Vector(box),
      OperationPolicy.Permissive)(Right(_)).toOption.get
    assertEquals(run.executed, Vector[CoreOperation](box))
    assertEquals(run.state.game.current.temporaryHands(actor), Vector.empty)
    assert(!CardIndex.from(run.state.game).toOption.get.ids.contains(card))
    assertEquals(OperationRun.boxed(run.executed), Set[CardId](card))
  }

  test("a card that is not a Vision cannot be moved to the shared bank") {
    val base = initialReady
    val denizen = base.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId => id
    }.get
    val (state, actor) = holding(denizen)
    val box = Move(Piece.Card(denizen), PositionedLocation(Location.Hand(actor)),
      PositionedLocation(Location.SharedBank))
    assert(OperationPipeline.run(state, Vector(box),
      OperationPolicy.Permissive)(Right(_)).isLeft)
  }

  test("a card that leaves the game without a boxing move still fails the " +
      "inventory check") {
    val card = VisionRules.Conspiracy
    val (state, actor) = holding(card)
    val vanish: ReadyGame => Either[OathViolation, ReadyGame] = ready =>
      Right(ready.updateCurrent(current => current.copy(temporaryHands =
        current.temporaryHands.updated(actor, Vector.empty))))
    assert(OperationPipeline.run(state, Vector(GainSupply(actor, 1)),
      OperationPolicy.Permissive)(vanish).isLeft)
  }
```

In `GameEventWireSuite`, add this test after "reduced optional spend is canonical in memory and on the wire":

```scala
  test("a recorded move of a Vision to the shared bank round trips and replays") {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val card = VisionId("vision:conspiracy")
    val prepared = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(card))))
    val box = Move(Piece.Card(card), PositionedLocation(Location.Hand(actor)),
      PositionedLocation(Location.SharedBank))
    val event = WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
      DeltaMeaning.OperationApplied("box")), Vector(box), Vector.empty)
    val encoded = GameEventWire.encodeEvent("walker", catalog.ref, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val decoded = GameEventWire.decode(encoded).toOption.get.event
      .asInstanceOf[WalkerStepRecorded]
    val replayed = OperationPipeline.run(prepared, decoded.ops,
      OperationPolicy.Permissive)(Right(_)).toOption.get.state
    assertEquals(replayed.game.current.temporaryHands(actor), Vector.empty)
  }
```

If `Move`, `Piece`, `PositionedLocation` or `Location` are not already imported in `GameEventWireSuite`, add them to its `oathdigital.model` import.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.OperationPipelineSuite oathdigital.serialization.GameEventWireSuite"`
Expected: compile FAIL, "value boxed is not a member of object OperationRun", and once that is added, the two boxing tests fail with an `InvalidDestination` rejection.

- [ ] **Step 3: Implement the mutation, the validator and the invariant allowance**

In `OperationCardMutation.insertCard`, add this case to the `transfer.to.location match`, after the `Location.Dispossessed` case:

```scala
      case Location.SharedBank => ensureEmptyTokens(original.state, id)
        .map(_ => ready)
```

The card was already removed from its container by `removeCard`, so inserting nothing is the whole mutation.

In `OperationValidator.cardDestinationViolation`, add this case before `case Location.Atlas`:

```scala
      case Location.SharedBank => id match {
        case _: VisionId => None
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
```

In `OperationPipeline.scala`, add to `object OperationRun`:

```scala
  /** The cards this run took out of the game: every card whose executed move
    * ends at the shared bank. The pipeline's card-inventory check allows a
    * card to be missing afterwards only if a declared operation removed it.
    */
  def boxed(executed: Vector[CoreOperation]): Set[CardId] = executed.collect {
    case Move(Piece.Card(id), _, to, _) if to.location == Location.SharedBank =>
      id
  }.toSet
```

In `OperationPipeline.run`, change the invariant call from `OperationStateInvariant.validate(updated, expected)` to:

```scala
        _ <- OperationStateInvariant.validate(updated,
          expected -- OperationRun.boxed(staged.executed))
```

and extend the doc comment on `object OperationPipeline` with this paragraph:

```scala
  * A card may leave the game only through a `Move` to `Location.SharedBank`
  * that the batch declares; its id is then removed from the expected card
  * set. Any other change to the card inventory still fails the batch.
```

- [ ] **Step 3b: Fix any further rejection the tests reveal**

Run the two suites. If the boxing test now fails with a different rejection (for example a shape or location check outside the two methods above), read the error, find the check with `git grep -n "SharedBank" src/main/scala/oathdigital/gameplay/operations`, and allow a `VisionId` card `Move` to `SharedBank` there in the same way. Do not widen it to other card kinds.

- [ ] **Step 4: Run the tests and the operation suites**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.OperationPipelineSuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationStateAdapterSuite oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations src/test/scala/oathdigital/gameplay/OperationPipelineSuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "feat(operations): let a batch move a Vision to the shared bank

A declared move of a Vision to SharedBank removes the card from the game and
from the pipeline's expected card set. Any other missing card still fails the
inventory check.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Relic-slot and banner option references

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Test: `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`

**Interfaces:**
- Consumes: `DecisionOptionRef`, `DecisionOption`, `BannerRules.holder(current, banner): Option[PlayerId]` (`oathdigital.gameplay.actions`), `Banner.fromKey`, `Banner.all`.
- Produces: `DecisionOptionRef.RelicSlot(owner: PlayerId, slot: Int)`, `DecisionOptionRef.Banner(banner: oathdigital.model.Banner)`, `DecisionOption.RelicSlot(ref)`, `DecisionOption.Banner(ref)`; `DecisionOption.forRef` covers both; `fromWire` parses both.

- [ ] **Step 1: Write the failing tests**

Replace the body of `DecisionOptionRefSuite` (keep the existing three tests and add these):

```scala
  private val slot = DecisionOptionRef.RelicSlot(PlayerId("blue"), 2)
  private val banner = DecisionOptionRef.Banner(oathdigital.model.Banner.DarkestSecret)

  test("a relic slot spells itself as a kind and owner-and-index id and parses back") {
    assertEquals((slot.kind, slot.wireId), ("relic-slot", "blue:2"))
    assertEquals(DecisionOptionRef.fromWire(slot.kind, slot.wireId), Some(slot))
  }

  test("a relic slot owner may contain a colon and still round trips") {
    val odd = DecisionOptionRef.RelicSlot(PlayerId("a:b"), 0)
    assertEquals(DecisionOptionRef.fromWire(odd.kind, odd.wireId), Some(odd))
  }

  test("a malformed relic slot is not a reference") {
    Vector("blue", "blue:", ":2", "blue:-1", "blue:x", "blue:1.5").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("relic-slot", id), None, id)
    }
  }

  test("a banner spells itself as its key and an unknown key is not a reference") {
    assertEquals((banner.kind, banner.wireId), ("banner", "darkest-secret"))
    assertEquals(DecisionOptionRef.fromWire(banner.kind, banner.wireId),
      Some(banner))
    assertEquals(DecisionOptionRef.fromWire("banner", "the-crown"), None)
  }

  test("a relic slot and a banner are presentable from the reference alone") {
    assertEquals(DecisionOption.forRef(slot),
      Some(DecisionOption.RelicSlot(slot)))
    assertEquals(DecisionOption.forRef(banner),
      Some(DecisionOption.Banner(banner)))
  }
```

In `GameEventWireSuite`, in the test "every option reference kind round trips through a recorded answer", add two lines to `refs` after the `FavorBank` line (add a comma to that line):

```scala
      DecisionOptionRef.FavorBank(Suit.Hearth),
      DecisionOptionRef.RelicSlot(PlayerId("blue"), 0),
      DecisionOptionRef.Banner(Banner.PeoplesFavor))
```

In `WalkerDecisionProjectorSuite`, add this test after "a declared option whose id is absent from authoritative state suppresses the whole decision projection":

```scala
  test("a relic slot and a banner project from live state, and one the state " +
      "no longer holds suppresses the decision") {
    val (base, actor) = parked(ActionRef.Recover)
    val current = base.ready.game.current
    val enemy = current.players.find(_.player != actor).get
    val relic = RelicState(RelicId("slot-relic"), Orientation.FaceDown,
      Tokens.empty)
    val placed = base.copy(ready = base.ready.updateCurrent(_.copy(
      players = current.players.map(player =>
        if (player.player == enemy.player) player.copy(relics = Vector(relic))
        else player),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(
          holder = Some(enemy.player)),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)))))
    val slot = DecisionOption.RelicSlot(
      DecisionOptionRef.RelicSlot(enemy.player, 0))
    val held = DecisionOption.Banner(
      DecisionOptionRef.Banner(Banner.PeoplesFavor))

    val query = projects(placed, actor, Vector(slot, held))
      .getOrElse(fail("a held relic slot and banner must project"))
    assertEquals(query.options.map(row => (row.kind, row.id)),
      Vector(("relic-slot", s"${enemy.player.value}:0"),
        ("banner", "peoples-favor")))
    assert(query.options.forall(row => row.card.isEmpty && row.label.nonEmpty))
    assert(query.options.head.label.contains("facedown relic"))

    // Controls: a slot past the owner's relics and a banner nobody holds have
    // no live state to describe, so each suppresses the whole decision.
    assertEquals(projects(placed, actor, Vector(DecisionOption.RelicSlot(
      DecisionOptionRef.RelicSlot(enemy.player, 1)))), None)
    assertEquals(projects(placed, actor, Vector(DecisionOption.Banner(
      DecisionOptionRef.Banner(Banner.DarkestSecret)))), None)
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.model.DecisionOptionRefSuite"`
Expected: compile FAIL, "value RelicSlot is not a member of object DecisionOptionRef".

- [ ] **Step 3: Implement the model change**

In `src/main/scala/oathdigital/model/Decisions.scala`:

1. After the `Edifice` case class inside `object DecisionOptionRef`, add:

```scala
  /** One relic of another player, named by its position in their relic
    * vector rather than by the card, so a facedown relic's identity is never
    * disclosed by an option. The owner may contain a colon; the slot is
    * always the text after the last one.
    */
  final case class RelicSlot(owner: PlayerId, slot: Int)
      extends DecisionOptionRef {
    require(slot >= 0, "a relic slot must be non-negative")
    val kind: String = "relic-slot"
    def wireId: String = s"${owner.value}:$slot"
  }
  /** A banner. It names no holder: who holds it is read from live state, so
    * an answer cannot name a holder that has since changed.
    */
  final case class Banner(banner: oathdigital.model.Banner)
      extends DecisionOptionRef {
    val kind: String = "banner"
    def wireId: String = banner.key
  }
```

2. In `fromWire`, add after the `"edifice"` line:

```scala
      case "relic-slot" =>
        val at = wireId.lastIndexOf(':')
        Option.when(at > 0)(wireId.take(at)).filter(_.trim.nonEmpty)
          .flatMap(owner => wireId.drop(at + 1).toIntOption
            .filter(_ >= 0).map(RelicSlot(PlayerId(owner), _)))
      case "banner" => oathdigital.model.Banner.fromKey(wireId).map(Banner(_))
```

3. Change the two comments that say "nine variants" (on the `fromWire` doc and the `kind` doc, if present) to "eleven variants".

4. In `object DecisionOption`, after `Edifice`, add:

```scala
  final case class RelicSlot(ref: DecisionOptionRef.RelicSlot)
      extends DecisionOption
  final case class Banner(ref: DecisionOptionRef.Banner) extends DecisionOption
```

and in `forRef`, after the `Edifice` case, add:

```scala
    case value: DecisionOptionRef.RelicSlot => Some(RelicSlot(value))
    case value: DecisionOptionRef.Banner => Some(Banner(value))
```

- [ ] **Step 4: Implement the projector cases**

In `WalkerDecisionProjector.scala`, change the import `oathdigital.gameplay.actions.RecoverRules` to `oathdigital.gameplay.actions.{BannerRules, RecoverRules}`, and in `optionProjection` add these cases after `DecisionOption.Edifice`:

```scala
      case DecisionOption.RelicSlot(slot) =>
        if (ready.game.current.players.find(_.player == slot.owner)
            .exists(_.relics.isDefinedAt(slot.slot)))
          row(s"${presentation.safeLabel(slot.owner.value)} facedown relic")
        else None
      case DecisionOption.Banner(held) =>
        BannerRules.holder(ready.game.current, held.banner).flatMap(holder =>
          row(s"${presentation.safeLabel(holder.value)} " +
            presentation.safeLabel(held.banner.key)))
```

- [ ] **Step 5: Run the tests and the wider suites**

Run: `./sbtw -no-colors "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS. If the compiler reports a non-exhaustive match over `DecisionOption` or `DecisionOptionRef` in another file, add the two cases there in the same way (`git grep -n "DecisionOption.Edifice" src/main` lists the candidates).

Then run `./sbtw -no-colors test` and expect PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
git commit -m "feat(walker): add relic-slot and banner decision option references

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Conspiracy as a `WHEN PLAYED` power

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` (`planVision`, `PlacementPlan`, `plannedOperations`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala` (the `CardPlayed` hook)
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala`, `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`, `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (delete `BeginConspiracy`)
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala` (create)
- Test: `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala` (create)
- Test: `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/SearchProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/VisionsSuite.scala` (migrate)

**Interfaces:**
- Consumes: Task 1's boxing `Move`; Task 2's `DecisionOptionRef.RelicSlot`/`Banner` and `DecisionOption.forRef`; `BannerRules.holder`, `BannerRules.resources`, `BannerRules.raidFavorReturn` (`oathdigital.gameplay.actions`); `Give`, `Burn.secrets`, `Move`, `Decide`, `BuildOps`, `CardPlayed`, `Transform`.
- Produces: `ConspiracyWhenPlayed` (`id = PowerId("vision.conspiracy")`, `decisionId = "cardplay.conspiracy.target"`), registered in `WalkerPowerCatalog.default`; `CardPlay.planVision` plans a faceup Conspiracy from either origin as no placement; the `CardPlayed` hook fires for it.

- [ ] **Step 1: Write the failing power tests**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

class ConspiracyWhenPlayedSuite extends munit.FunSuite {
  private val powers = WalkerPowers(Vector(ConspiracyWhenPlayed))
  private val conspiracy = VisionRules.Conspiracy
  private val placeId = s"cardplay.place.${conspiracy.kind}.${conspiracy.value}"
  private val faceup = DecisionOptionRef.Button("adviser-faceup")

  private final case class Staged(ready: ReadyGame, actor: PlayerId,
      enemy: PlayerId, origin: CardPlayProcedure.Origin)

  /** The actor holds Conspiracy at `origin`. The enemy stands on the actor's
    * site unless `shared` is false, and holds `relics`. No banner has a holder
    * until `edit` gives one.
    */
  private def fixture(relics: Vector[RelicState] = Vector.empty,
      shared: Boolean = true,
      origin: CardPlayProcedure.Origin = CardPlayProcedure.Origin.TemporaryHand)
      (edit: (ReadyGame, PlayerId) => ReadyGame = (ready, _) => ready)
      : Staged = {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val site = current.players.find(_.player == actor).get.pawnSite
    val enemy = current.players.find(_.player != actor).get.player
    val fromHand = origin == CardPlayProcedure.Origin.TemporaryHand
    val staged = base.updateCurrent(_.copy(
      players = current.players.map { player =>
        if (player.player == enemy) player.copy(
          pawnSite = if (shared) site else None, relics = relics)
        else if (player.player == actor && !fromHand) player.copy(advisers =
          player.advisers :+ VisionState(conspiracy, Orientation.FaceDown))
        else player
      },
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = None),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == conspiracy)),
      temporaryHands = if (fromHand)
        current.temporaryHands.updated(actor, Vector(conspiracy))
      else current.temporaryHands))
    Staged(edit(staged, enemy), actor, enemy, origin)
  }

  private def treeFor(f: Staged): Operation = (f.origin match {
    case CardPlayProcedure.Origin.TemporaryHand =>
      CardPlayProcedure.build(catalog, f.ready, f.actor, conspiracy, f.origin)
    case CardPlayProcedure.Origin.FacedownAdviser =>
      CardPlayProcedure.rebuildFacedown(catalog, f.ready, f.actor,
        Vector(DecisionOptionRef.Vision(conspiracy)))
  }).toOption.get

  private def parked(outcome: Either[OathViolation, WalkerOutcome]): PendingTree =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked].tree

  private def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  private def answer(f: Staged, tree: Operation, at: PendingTree, id: String,
      ref: DecisionOptionRef) =
    ProcedureWalker.resolve(f.ready, tree, at, Answered(id,
      DecisionAnswer.ChooseOneAnswer(ref), f.actor), powers)

  /** Plays Conspiracy faceup and returns the tree with the position parked on
    * the target decision.
    */
  private def atTarget(f: Staged): (Operation, PendingTree) = {
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    (tree, parked(answer(f, tree, place, placeId, faceup)))
  }

  private def targetOptions(f: Staged, tree: Operation, at: PendingTree) =
    ProcedureWalker.parkedDecide(f.ready, tree, at, powers).map(decide =>
      (decide.decisionId, decide.owner, decide.query
        .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)))

  private def recorded(done: WalkerOutcome.Finished): Vector[CoreOperation] =
    done.events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def replayed(f: Staged, done: WalkerOutcome.Finished): ReadyGame =
    OperationPipeline.run(f.ready, recorded(done),
      OperationPolicy.Permissive)(Right(_)).toOption.get.state

  private def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get

  test("the default walker catalog carries the Conspiracy power") {
    assert(WalkerPowerCatalog.default(catalog).powers
      .contains(ConspiracyWhenPlayed))
  }

  test("Conspiracy takes an opaque relic slot and leaves the game") {
    val relic = RelicState(RelicId("conspiracy-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic))()
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at), Some((
      ConspiracyWhenPlayed.decisionId, f.actor,
      Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(f.enemy, 0)))))
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.RelicSlot(f.enemy, 0)))
    val after = done.treeless
    assert(player(after, f.actor).relics.exists(_.id == relic.id))
    assert(!player(after, f.enemy).relics.exists(_.id == relic.id))
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
    assertEquals(replayed(f, done), after)
  }

  test("Conspiracy taking the Peoples Favor returns its favor in the " +
      "least-bank order and takes the banner") {
    val f = fixture()((ready, enemy) => {
      val current = ready.game.current
      ready.copy(banks = ready.banks.copy(favor = ready.banks.favor.map {
        case (suit, count) => suit -> math.max(0, count - 2) }))
        .updateCurrent(_.copy(banners = current.banners.copy(peoplesFavor =
          current.banners.peoplesFavor.copy(holder = Some(enemy), favor = 2))))
    })
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at), Some((
      ConspiracyWhenPlayed.decisionId, f.actor,
      Vector[DecisionOptionRef](DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    val after = done.treeless
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(f.actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(after.banks.favor.values.sum, f.ready.banks.favor.values.sum + 2)
    val returned = recorded(done).collect {
      case Move(Piece.Favor(1), _,
          PositionedLocation(Location.FavorBank(suit), _), _) => suit
    }
    assertEquals(returned, BannerRules.raidFavorReturn(f.ready.banks.favor, 2))
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assertEquals(replayed(f, done), after)
  }

  test("Conspiracy taking the Darkest Secret burns every secret and takes " +
      "the banner") {
    val f = fixture()((ready, enemy) => {
      val current = ready.game.current
      ready.updateCurrent(_.copy(banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(enemy), secrets = 3))))
    })
    def siteSecrets(ready: ReadyGame): Int =
      ready.game.current.map.sites.valuesIterator.map { site =>
        site.tokens.secrets + site.denizens.collect {
          case card: DenizenState => card.tokens.secrets
        }.sum
      }.sum
    val (tree, at) = atTarget(f)
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    val after = done.treeless
    assertEquals(after.game.current.banners.darkestSecret.secrets, 0)
    assertEquals(after.game.current.banners.darkestSecret.holder, Some(f.actor))
    // The burn returns the secrets to the untracked shared bank; none lands on
    // a site.
    assertEquals(siteSecrets(after), siteSecrets(f.ready))
    assertEquals(replayed(f, done), after)
  }

  test("with no legal target Conspiracy asks nothing and only leaves the game") {
    val f = fixture(shared = false)()
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    val done = finished(answer(f, tree, place, placeId, faceup))
    val after = done.treeless
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
    assertEquals(recorded(done), Vector[CoreOperation](Move(
      Piece.Card(conspiracy), PositionedLocation(Location.Hand(f.actor)),
      PositionedLocation(Location.SharedBank))))
  }

  test("a target the current state does not offer is rejected at the decision") {
    val relics = Vector("first", "second").map(id => RelicState(
      RelicId(s"conspiracy-$id"), Orientation.FaceDown, Tokens.empty))
    val f = fixture(relics)()
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at).map(_._3), Some(Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(f.enemy, 0),
      DecisionOptionRef.RelicSlot(f.enemy, 1))))
    Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(f.enemy, 2),
      DecisionOptionRef.RelicSlot(f.actor, 0),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)).foreach { ref =>
      assert(answer(f, tree, at, ConspiracyWhenPlayed.decisionId, ref).isLeft,
        ref.toString)
    }
  }

  test("discarding a Conspiracy does not play it") {
    val relic = RelicState(RelicId("kept-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic))()
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    val done = finished(answer(f, tree, place, placeId,
      DecisionOptionRef.Button("discard")))
    assert(player(done.treeless, f.enemy).relics.exists(_.id == relic.id))
    assert(CardIndex.from(done.treeless.game).toOption.get.ids
      .contains(conspiracy))
  }
}
```

- [ ] **Step 2: Write the failing service test**

Create `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class VisionPlaySuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val conspiracy = VisionRules.Conspiracy
  private val faceup = DecisionAnswer.ChooseOneAnswer(
    DecisionOptionRef.Button("adviser-faceup"))

  private def placeId(card: VisionId) = s"cardplay.place.${card.kind}.${card.value}"

  private def acting: ReadyGame = {
    val state = initialReady
    state.updateCurrent(_.copy(
      turn = state.game.current.turn.copy(phase = Phase.Act)))
  }

  private def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get

  test("a Conspiracy kept faceup from Search parks on its target, takes it " +
      "and leaves the game") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val site = player(base, actor).pawnSite
    val enemy = current.players.find(_.player != actor).get.player
    val deck = current.commonCards.worldDeck
    val initial = base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == enemy) p.copy(pawnSite = site) else p),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = Some(enemy)),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(worldDeck =
        Vector(conspiracy) ++ deck.filterNot(_ == conspiracy))))
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get
    val parked = rules.resolveWalker(started.state, actor, placeId(conspiracy),
      faceup).toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingSearchDecision])
    val OathState.Ready(waiting) = parked.state: @unchecked
    assertEquals(waiting.game.current.temporaryHands(actor),
      Vector[WorldCardId](conspiracy))

    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(parked.state, 11)
    val owner = projector.project("searched-conspiracy", loaded, actor)
    val hidden = projector.project("searched-conspiracy", loaded, enemy)
    assertEquals(owner.walkerDecision.map(_.decisionId),
      Some("cardplay.conspiracy.target"))
    assertEquals(owner.walkerDecision.flatMap(_.query)
      .map(_.options.map(option => (option.kind, option.id))),
      Some(Vector(("banner", "peoples-favor"))))
    assertEquals(hidden.walkerDecision, None)

    val resolved = rules.resolveWalker(parked.state, actor,
      "cardplay.conspiracy.target", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))).toOption.get
    val OathState.Ready(after) = resolved.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(after.game.current.walkerPending, None)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
  }
}
```

- [ ] **Step 3: Migrate the tests that assert the legacy handoff**

In `CardPlayProcedureSuite`, replace the whole test named "faceup Conspiracy from Search hands off to existing pending procedure" (from its `test(` line to the closing `}` before the next `test(`) with:

```scala
  test("a faceup Conspiracy is planned as no placement and offers no replacement") {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val vision = VisionRules.Conspiracy
    val revealed = VisionRules.Faith
    val ready = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          id == vision || id == revealed)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(vision)),
      players = current.players.map(player =>
        if (player.player == actor) player.copy(revealedVision =
          Some(VisionState(revealed, Orientation.FaceUp))) else player)))
    val faceup = SearchPlacement.Adviser(Orientation.FaceUp, None)
    val choices = CardPlay.legalChoices(catalog, ready, actor, vision,
      CardPlay.Origin.TemporaryHand, 3, 3)
    assertEquals(choices.find(_.placement == faceup).map(_.replacements),
      Some(Vector.empty))
    assertEquals(CardPlay.plannedOperations(catalog, ready, actor, vision,
      faceup, CardPlay.Origin.TemporaryHand), Right(Vector.empty))
    assert(CardPlay.plannedOperations(catalog, ready, actor, vision,
      SearchPlacement.Adviser(Orientation.FaceUp, Some(revealed)),
      CardPlay.Origin.TemporaryHand).isLeft)
  }
```

In `SearchProcedureSuite`, replace the test named "faceup Conspiracy keeps its hand and hands off to legacy continuation" with:

```scala
  test("a faceup Conspiracy from Search with nothing to take is played and boxed") {
    val base = ready
    val actor = base.game.current.turn.activePlayer
    val vision = VisionRules.Conspiracy
    val current = base.game.current
    val deck = current.commonCards.worldDeck
    assert(deck.contains(vision))
    val initial = base.updateCurrent(_.copy(
      players = current.players.map(player =>
        if (player.player == actor) player else player.copy(pawnSite = None)),
      commonCards = current.commonCards.copy(worldDeck =
        Vector(vision) ++ deck.filterNot(_ == vision))))
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get
    val result = rules.resolveWalker(started.state, actor,
      s"cardplay.place.${vision.kind}.${vision.value}",
      DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup"))).toOption.get
    val OathState.Ready(after) = result.state: @unchecked
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.pending, None)
    assert(!result.continue.isInstanceOf[OathContinue.AwaitingSearchDecision])
  }
```

In `VisionsSuite`, delete the whole test named "a Conspiracy kept faceup from Search immediately enters its target procedure" (from its `test(` line up to, but not including, `  private def withActorAdviser(`). `VisionPlaySuite` replaces it.

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.gameplay.VisionPlaySuite"`
Expected: compile FAIL, "not found: value ConspiracyWhenPlayed".

- [ ] **Step 5: Implement the power**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Conspiracy: when played, take a relic or a banner from a player whose pawn
  * is at the actor's site, then return the card to the box.
  *
  * The card is played faceup from either origin, a Search's temporary hand or
  * a facedown adviser, and card play places nothing for it, so it is still at
  * its origin when this transform runs. The transform adds, after whatever
  * other powers contributed:
  *  - a `Decide` offering each legal target, only when there is at least one;
  *  - the take effects for the chosen target, then the card's removal, in one
  *    batch, so the card leaves the game even when there was nothing to take.
  *
  * A legal target cannot be declined. The decision id sits under the
  * `cardplay.` prefix, which the registry already maps to a prompt
  * continuation for both Search and the facedown-adviser play.
  *
  * The transform must fold to the same vector while the decision is parked: it
  * reads only state that nothing between the fold and the answer changes.
  */
case object ConspiracyWhenPlayed extends ContributingPower {
  val id: PowerId = PowerId("vision.conspiracy")
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val decisionId: String = "cardplay.conspiracy.target"

  override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayed(card, _) => card == VisionRules.Conspiracy
    case _ => false
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayed -> Vector(Transform((ctx, children) =>
      (children ++ targetDecision(ctx.state, ctx.activePlayer)) :+
        BuildOps((ready, pending) =>
          effects(ready, ctx.activePlayer, pending)))))

  /** Every relic slot and banner held by another player whose pawn is at the
    * actor's site, in seat order, relic slots before banners.
    */
  private def legalTargets(ready: ReadyGame, actor: PlayerId)
      : Vector[DecisionOptionRef] = {
    val current = ready.game.current
    val site = current.players.find(_.player == actor).flatMap(_.pawnSite)
    current.players.filter(other => other.player != actor &&
      other.pawnSite == site).flatMap { other =>
      other.relics.indices.map(slot =>
        DecisionOptionRef.RelicSlot(other.player, slot)) ++
        Banner.all.filter(banner => BannerRules.holder(current, banner)
          .contains(other.player)).map(banner => DecisionOptionRef.Banner(banner))
    }
  }

  private def targetDecision(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = {
    val options = legalTargets(ready, actor).flatMap(DecisionOption.forRef)
    if (options.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
      heading = Some("Conspiracy: choose an enemy asset to take"))))
  }

  private def effects(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val legal = legalTargets(ready, actor)
    val chosen = pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }
    val taken: Either[OathViolation, Vector[CoreOperation]] = chosen match {
      case Some(ref) if legal.contains(ref) => Right(take(ready, actor, ref))
      case Some(_) => Left(OathViolation.ConspiracyUnavailable(
        "the chosen target is no longer legal"))
      case None if legal.isEmpty => Right(Vector.empty)
      case None => Left(OathViolation.ConspiracyUnavailable(
        "a legal target must be chosen"))
    }
    taken.map(_ :+ removal(ready, actor))
  }

  private def take(ready: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : Vector[CoreOperation] = {
    val current = ready.game.current
    def banner(held: Banner, owner: PlayerId): CoreOperation = Move(
      Piece.Banner(held), PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.PlayArea(actor)))
    ref match {
      case DecisionOptionRef.RelicSlot(owner, slot) =>
        current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .toVector.map(relic => Give(Piece.Card(relic.id), owner,
            Location.PlayArea(owner), Location.PlayArea(actor)))
      case DecisionOptionRef.Banner(held) =>
        BannerRules.holder(current, held).toVector.flatMap { owner =>
          val leaving: Vector[CoreOperation] = held match {
            case Banner.PeoplesFavor => BannerRules.raidFavorReturn(
              ready.banks.favor, BannerRules.resources(current, held))
              .map(suit => Move(Piece.Favor(1),
                PositionedLocation(Location.OnBanner(held)),
                PositionedLocation(Location.FavorBank(suit))))
            case Banner.DarkestSecret =>
              val secrets = BannerRules.resources(current, held)
              Option.when(secrets > 0)(Burn.secrets(secrets,
                PositionedLocation(Location.OnBanner(held)))).toVector
          }
          leaving :+ banner(held, owner)
        }
      case _ => Vector.empty
    }
  }

  /** The card leaves the game from wherever the actor holds it. */
  private def removal(ready: ReadyGame, actor: PlayerId): CoreOperation = {
    val inHand = ready.game.current.temporaryHands
      .getOrElse(actor, Vector.empty).contains(VisionRules.Conspiracy)
    Move(Piece.Card(VisionRules.Conspiracy),
      PositionedLocation(if (inHand) Location.Hand(actor)
        else Location.PlayArea(actor)),
      PositionedLocation(Location.SharedBank))
  }
}
```

In `WalkerPowerCatalog.scala`, add `import oathdigital.gameplay.powers.whenplayed.{ConspiracyWhenPlayed, Dazzle}` in place of the `Dazzle` import, and change the catalog's last line from `Dazzle.forCatalog(catalog).toVector ++ ... :+ TakeWealthLimit)` to end with `:+ TakeWealthLimit :+ ConspiracyWhenPlayed)`. Add one sentence to the object's doc comment: "Conspiracy's power carries no catalog id (a Vision has no catalog powers), so like Take Wealth's limit it is always present and inert until a card play runs `ActionCardPlayed` for Conspiracy."

- [ ] **Step 6: Switch card play to the power**

In `CardPlay.scala`:

1. Delete the field `      startConspiracy: Option[DecisionId],` from `PlacementPlan`.

2. Replace the whole `planVision` method (from `  private def planVision(` to the line before `  private def plannedOperations(`) with:

```scala
  private def planVision(catalog: ExecutableCatalog, player: PlayerState,
      origin: Origin, id: VisionId, orientation: Orientation,
      replace: Option[CardId], adviserLimit: Int)
      : Either[OathViolation, PlacementPlan] = origin match {
    case _ if id == VisionRules.Conspiracy && orientation == Orientation.FaceUp =>
      // A played Conspiracy is boxed by its WHEN PLAYED power, so from either
      // origin it takes no slot and replaces nothing.
      Either.cond(replace.isEmpty,
        PlacementPlan(None, Vector.empty, Vector.empty, Vector.empty),
        InvalidSearchPlacement("a played Conspiracy replaces nothing"))
    case Origin.FacedownAdviser =>
      Either.cond(orientation == Orientation.FaceUp && replace.isEmpty, (),
        InvalidSearchPlacement(
          "facedown adviser must be played faceup to advisers or the pawn's site"))
        .map { _ =>
          val from = PositionedLocation(Location.PlayArea(player.player))
          PlacementPlan(
            Some(Play(id, from, Location.PlayArea(player.player),
              Orientation.FaceUp, required = true)),
            Vector.empty,
            player.revealedVision.toVector.map { value =>
              (value.id: WorldCardId) -> from
            },
            Vector.empty)
        }
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      val expected = player.revealedVision.map(_.id)
      Either.cond(replace == expected, (), InvalidSearchPlacement(
        if (expected.nonEmpty) "a revealed Vision must be replaced"
        else "there is no revealed Vision to replace")).map { _ =>
        val from = keptSource(origin, player.player)
        PlacementPlan(
          Some(Play(id, from, Location.PlayArea(player.player),
            Orientation.FaceUp, required = true)),
          Vector.empty,
          expected.toVector.map(value => value ->
            PositionedLocation(Location.PlayArea(player.player))),
          Vector.empty)
      }
    case Origin.TemporaryHand =>
      validateAdviserReplacement(catalog, player.advisers, replace,
        adviserLimit).map { removed =>
        val from = keptSource(origin, player.player)
        PlacementPlan(
          Some(Play(id, from, Location.PlayArea(player.player),
            Orientation.FaceDown, required = true)),
          Vector.empty,
          removed.toVector.map(value => value ->
            PositionedLocation(Location.PlayArea(player.player))),
          Vector.empty)
      }
  }

```

3. In `plan`, change the call `else planVision(catalog, ready, player, origin, card, id, orientation,\n          replace, adviserLimit)` to `else planVision(catalog, player, origin, id, orientation, replace,\n          adviserLimit)`.

4. In `plannedOperations`, replace

```scala
    val handoff = (origin, plan.startConspiracy) match {
      case (Origin.TemporaryHand, Some(decision)) =>
        Vector(BeginConspiracy(player.player, decision, VisionRules.Conspiracy))
      case _ => Vector.empty
    }
    discardOps.map(ops => edificeOps ++ ops ++ plan.kept.toVector ++
      plan.favor ++ handoff)
```

with

```scala
    discardOps.map(ops => edificeOps ++ ops ++ plan.kept.toVector ++
      plan.favor)
```

5. Remove the fifth positional argument (`None`) from every remaining `PlacementPlan(...)` constructor call, which `startConspiracy` occupied. Run this once from the repository root, then read `git diff src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` and check that only those arguments went:

```bash
python3 - <<'EOF'
import re, pathlib
path = pathlib.Path('src/main/scala/oathdigital/gameplay/actions/CardPlay.scala')
text = path.read_text()
fixed = re.sub(r'(Vector\.empty|-> site\)), None(?=[,)])', r'\1', text)
path.write_text(fixed)
EOF
```

In `CardPlayProcedure.scala`, replace

```scala
            val hook = Option.when(card != VisionRules.Conspiracy)(placement)
              .flatMap(CardPlay.playedSource(ready, actor, card, _))
              .map(CardPlayed(card, _)).toVector
```

with

```scala
            val hook = CardPlay.playedSource(ready, actor, card, placement)
              .map(CardPlayed(card, _)).toVector
```

and change the import `oathdigital.gameplay.actions.{CardPlay, VisionRules}` to `oathdigital.gameplay.actions.CardPlay` if `VisionRules` is no longer used in that file (`git grep -n VisionRules src/main/scala/oathdigital/gameplay/actions/cardplay` says).

- [ ] **Step 7: Delete `BeginConspiracy`**

Nothing produces it now. Delete, each exactly once (run `git grep -n BeginConspiracy src` after and expect no hits):

- In `CoreOperations.scala`, the doc comment and class that start at `/** Starts the existing Conspiracy continuation after walker card selection.` and end at the closing `}` of `final case class BeginConspiracy(...)`, plus the blank line after it.
- In `OperationStateMutation.scala`, the whole `case (result, BeginConspiracy(player, decision, source)) =>` clause, through the `}` that closes its `result.flatMap { state =>` block.
- In `WalkerOperationCodec.scala`, the `case BeginConspiracy(player, decision, source) => ujson.Obj(...)` encoding case (three lines) and the `case "begin-conspiracy" => Right(BeginConspiracy(...))` decoding case (four lines).

- [ ] **Step 8: Run the tests**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.gameplay.VisionPlaySuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.VisionsSuite"`
Expected: PASS.

If the replay assertion `assertEquals(replayed(f, done), after)` fails only on a field like `walkerPending`, compare with `DazzleSuite`'s identical assertion, which passes on the same walker; a real difference means the recorded operations are not the whole effect and must be fixed, not the assertion.

Then run `./sbtw -no-colors test`. Expected: PASS. Fix any suite that still refers to the deleted operation or to a legacy Conspiracy handoff by migrating it the way Step 3 does.

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test
git commit -m "feat(walker): play Conspiracy as a WHEN PLAYED power

Conspiracy adds a target decision, the take effects and its own removal at the
ActionCardPlayed window. Card play places nothing for a faceup Conspiracy, so a
Search-kept Conspiracy no longer hands off to a legacy pending procedure, and
BeginConspiracy is deleted.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Reveal Vision and facedown Conspiracy through facedown-adviser play

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` (`validateOrigin`, `plannedOperations`)
- Test: `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`

**Interfaces:**
- Consumes: Task 3's power and `planVision`.
- Produces: a facedown Vision can be started with `StartWalker("play-facedown-adviser", …, [Vision(id)])` and answered `adviser-faceup`; a facedown denizen play no longer depends on the handler-inventory fingerprint.

- [ ] **Step 1: Write the failing tests**

In `CardPlayProcedureSuite`, add after the test added in Task 3:

```scala
  test("a facedown Vision is offered discard and faceup play only, with no replacement") {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val held = Vector(VisionRules.Faith, VisionRules.Conspiracy)
    val revealed = VisionRules.Conquest
    val ready = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          held.contains(id) || id == revealed)),
      players = current.players.map(player =>
        if (player.player == actor) player.copy(
          advisers = player.advisers ++ held.map(VisionState(_,
            Orientation.FaceDown)),
          revealedVision = Some(VisionState(revealed, Orientation.FaceUp)))
        else player)))
    held.foreach { vision =>
      val choices = CardPlay.legalChoices(catalog, ready, actor, vision,
        CardPlay.Origin.FacedownAdviser, 3, 3)
      assertEquals(choices.map(_.placement), Vector[SearchPlacement](
        SearchPlacement.Discard, SearchPlacement.Adviser(Orientation.FaceUp, None)),
        vision.value)
      assertEquals(choices.map(_.replacements), Vector(Vector.empty, Vector.empty),
        vision.value)
    }
  }
```

In `ConspiracyWhenPlayedSuite`, add before the final `}`:

```scala
  test("a Conspiracy played from a facedown adviser takes its target and " +
      "leaves the revealed Vision alone") {
    val relic = RelicState(RelicId("facedown-route-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic),
      origin = CardPlayProcedure.Origin.FacedownAdviser)()
    val revealedBefore = player(f.ready, f.actor).revealedVision
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at).map(_._3), Some(Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(f.enemy, 0))))
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.RelicSlot(f.enemy, 0)))
    val after = done.treeless
    assert(player(after, f.actor).relics.exists(_.id == relic.id))
    assert(!player(after, f.actor).advisers.exists(_.id == conspiracy))
    assertEquals(player(after, f.actor).revealedVision, revealedBefore)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
    assertEquals(replayed(f, done), after)
  }
```

In `VisionPlaySuite`, add before the final `}`:

```scala
  test("Reveal is a facedown Vision played faceup: it replaces the revealed " +
      "Vision and costs no Supply") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val old = VisionRules.Conquest
    val revealed = VisionRules.Faith
    val staged = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          id == old || id == revealed)),
      players = current.players.map(p => if (p.player == actor) p.copy(
        advisers = p.advisers :+ VisionState(revealed, Orientation.FaceDown),
        revealedVision = Some(VisionState(old, Orientation.FaceUp))) else p)))
    val before = player(staged, actor)
    val origin = staged.game.current.map.regionOf(before.pawnSite.get).get
    val destination = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    val started = rules.startWalker(OathState.Ready(staged),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Vision(revealed))).toOption.get
    val done = rules.resolveWalker(started.state, actor, placeId(revealed),
      faceup).toOption.get
    val OathState.Ready(after) = done.state: @unchecked
    val updated = player(after, actor)
    assertEquals(updated.board.supply, before.board.supply)
    assertEquals(updated.revealedVision.map(_.id), Some(revealed))
    assert(!updated.advisers.exists(_.id == revealed))
    assert(after.game.current.commonCards.discard(destination).contains(old))
    assertEquals(after.game.current.walkerPending, None)
  }

  test("a Conspiracy played from a facedown adviser through the service " +
      "with nothing to take is boxed") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val staged = base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == actor) p.copy(advisers = p.advisers :+
          VisionState(conspiracy, Orientation.FaceDown))
        else p.copy(pawnSite = None)),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == conspiracy))))
    val started = rules.startWalker(OathState.Ready(staged),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Vision(conspiracy))).toOption.get
    val done = rules.resolveWalker(started.state, actor, placeId(conspiracy),
      faceup).toOption.get
    val OathState.Ready(after) = done.state: @unchecked
    assert(!player(after, actor).advisers.exists(_.id == conspiracy))
    assertEquals(after.game.current.walkerPending, None)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.VisionPlaySuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite"`
Expected: the three new tests FAIL with `UnsupportedMinorActionRule` (from `validateVisionPlay`) or an empty choice list.

- [ ] **Step 3: Remove the unconditional rejection**

In `CardPlay.scala`, replace `validateOrigin` with:

```scala
  private def validateOrigin(player: PlayerState, card: WorldCardId,
      origin: Origin): Either[OathViolation, Unit] = origin match {
    case Origin.TemporaryHand => Right(())
    case Origin.FacedownAdviser =>
      player.advisers.find(_.id == card).filter {
        case DenizenState(_, Orientation.FaceDown, _) => true
        case VisionState(_, Orientation.FaceDown) => true
        case _ => false
      }.toRight(MinorActionUnavailable(
        "adviser is not held facedown by the actor")).map(_ => ())
  }
```

and in `plannedOperations` change `_ <- validateOrigin(catalog, player, card, placement, origin)` to `_ <- validateOrigin(player, card, origin)`. The catalog and placement checks the old version made belong to `plan`, which already looks a denizen up in the catalog and checks a Vision against `FirstGameRulesData.visions`.

- [ ] **Step 4: Run the tests and the card-play suites**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.VisionPlaySuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.MinorActionsSuite"`
Expected: PASS. `MinorActionsSuite` still calls `MinorActionPowerSupport.validateInventory` directly; it is deleted in Task 6.

Then `./sbtw -no-colors test`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src/main src/test
git commit -m "feat(card-play): let a facedown Vision be played faceup

CardPlay no longer rejects every faceup Vision play from a facedown adviser,
which turns on the placement that already replaces the revealed Vision, and it
no longer gates a facedown adviser play on the handler-inventory fingerprint.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Retire the legacy Vision commands, intents and client surface

Nothing routes to `Visions.handle` after this task, and nothing in the client can send a Reveal or Conspiracy command. The rules code itself goes in Task 6.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentDecoders.scala`, `CommandIntentCodec.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`, `LegalActionProjector.scala`, `PendingProcedureProjector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/ProtocolTestCommands.scala`, `HttpGameClientSuite.scala`, `ServerModeUiSuite.scala`

**Interfaces:**
- Consumes: Tasks 3 and 4, which make the walker the only way to play Conspiracy or Reveal a Vision.
- Produces: no `revealVision` or `playConspiracy` intent, command, control or board-target action; no `conspiracy-target` or `conspiracy-waiting` phase.

- [ ] **Step 1: Check the walker is the only route**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.VisionPlaySuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite"`
Expected: PASS. If it fails, stop and fix Tasks 3 and 4 first: this task removes the only other route.

- [ ] **Step 2: Delete the command surface**

Run this from the repository root. `remove` replaces one exact block and `cut` deletes from one marker up to (or, with `inclusive=True`, through) another; each asserts its marker occurs exactly once, so a file that has drifted fails loudly instead of being edited wrongly. If an assertion fails, make that deletion by hand.

```bash
python3 - <<'EOF'
import pathlib

def read(path):
    return pathlib.Path(path).read_text()

def remove(path, old, new=""):
    source = read(path)
    assert source.count(old) == 1, f"{path}: expected exactly one:\n{old}"
    pathlib.Path(path).write_text(source.replace(old, new))

def cut(path, start, end, inclusive=False):
    source = read(path)
    assert source.count(start) == 1 and source.count(end) == 1, f"{path}: {start!r} / {end!r}"
    a, b = source.index(start), source.index(end)
    assert a < b, path
    b = b + len(end) if inclusive else b
    pathlib.Path(path).write_text(source[:a] + source[b:])

# Shared protocol
p = "shared/src/main/scala/oathdigital/protocol/CommandIntents.scala"
cut(p, "  final case class RevealVision(", "  case object PeekSiteRelics extends GameIntent")
cut(p, "sealed trait ConspiracyTarget extends Product with Serializable", "sealed trait CampaignRaidTarget")
p = "shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala"
cut(p, '    case "revealVision" =>', '    case "peekSiteRelics" =>')
cut(p, "  private def conspiracy(", "  private def campaignRaid(")
p = "shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala"
cut(p, "    case RevealVision(id) =>", "    case PeekSiteRelics =>")
cut(p, "  private def conspiracy(v: ConspiracyTarget)", "  private def raid(")

# Application
cut("src/main/scala/oathdigital/application/GameCommands.scala",
    "  final case class RevealVision(", "  final case class BeginNegotiation(")
cut("src/main/scala/oathdigital/application/Authorization.scala",
    "  def revealVision(", "  def peekSiteRelics")
p = "src/main/scala/oathdigital/application/GameIntentMapper.scala"
remove(p, "      case Intent.RevealVision(id) => Right(actor.revealVision(VisionId(id)))\n"
          "      case Intent.PlayConspiracy(value) => option(value)(conspiracy).map(actor.playConspiracy)\n")
cut(p, "  private def conspiracy(value: oathdigital.protocol.ConspiracyTarget)",
    "  private def raid(value: oathdigital.protocol.CampaignRaidTarget)")
p = "src/main/scala/oathdigital/application/GameApplicationService.scala"
remove(p, "import oathdigital.gameplay.actions.VisionCommand\n")
cut(p, "      case GameCommand.RevealVision(playerId, visionId) =>",
    "      case GameCommand.BeginNegotiation(playerId, participants) =>")
remove("src/main/scala/oathdigital/application/PendingProcedureProjector.scala",
    "      case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&\n"
    "          context.viewer.contains(p.actor) => \"conspiracy-target\"\n"
    "      case Some(_: PendingProcedure.Conspiracy) => \"conspiracy-waiting\"\n")

# Legal projection
p = "src/main/scala/oathdigital/application/LegalActionProjector.scala"
remove(p, "ForgeRules, VisionRules, Visions}", "ForgeRules}")
remove(p, "      context.current.pending match {\n"
          "        case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&\n"
          "            context.viewer.contains(p.actor) =>\n"
          "          Vector(conspiracyTargetAction(context.ready, p))\n"
          "        case _ if ordinaryAct => boardTargetActions(context, travelFacts)\n"
          "        case _ => Vector.empty\n"
          "      },\n",
          "      if (ordinaryAct) boardTargetActions(context, travelFacts)\n"
          "      else Vector.empty,\n")
remove(p, "      case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&\n"
          "          context.viewer.contains(p.actor) => Vector(\"playConspiracy\")\n"
          "      case Some(_: PendingProcedure.Conspiracy) => Vector.empty\n")
cut(p, "          Option.when(active.advisers.exists {\n"
       "            case VisionState(id, Orientation.FaceDown) =>\n"
       "              Visions.canReveal(",
    "          Option.when(context.activeSite.exists(_.relics.nonEmpty))(\"peekSiteRelics\"),")
cut(p, "    val visions = player.advisers.collect {",
    "    Vector(\n      selection(\"travel\", \"Choose a Travel destination\", travel),")
remove(p, "        negotiators, minimum = 1, maximum = negotiators.size),\n"
          "      selection(\"reveal-vision\", \"Choose a Vision to reveal\", visions),\n"
          "      Option.when(hasConspiracy)(BoardTargetActionProjection(\"play-conspiracy\",\n"
          "        if (conspiracy.isEmpty) \"Play Conspiracy\" else \"Choose an enemy asset for Conspiracy\",\n"
          "        if (conspiracy.isEmpty) 0 else 1, if (conspiracy.isEmpty) 0 else 1,\n"
          "        autoActivate = false, conspiracy))).flatten\n",
          "        negotiators, minimum = 1, maximum = negotiators.size)).flatten\n")
cut(p, "  private def conspiracyTargetAction(", "  private def selection(")

# Rules
p = "src/main/scala/oathdigital/gameplay/OathRules.scala"
remove(p, "import oathdigital.gameplay.actions.{Visions, VisionCommand}",
          "import oathdigital.gameplay.actions.Visions")
cut(p, "  def handle(state: OathState, command: VisionCommand)",
    "  def handle(state: OathState, command: NegotiationCommand)")

# Frontend
p = "frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala"
remove(p, "    case \"reveal-vision\" => \"Reveal Vision\"\n"
          "    case \"play-conspiracy\" => \"Play Conspiracy\"\n")
remove(p, "    case \"negotiation\" | \"reveal-vision\" | \"play-conspiracy\" |\n",
          "    case \"negotiation\" |\n")
cut(p, "      case (\"reveal-vision\", Vector(BoardTargetRef.PlayerAdviser(owner, vision)))",
    "      case _ => None\n    }\n\n  private[frontend] def commandForFormation(")

# Tests
remove("shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala",
       "    RevealVision(\"v1\"), PlayConspiracy(Some(ConspiracyTarget.RelicSlot(\"p2\", 0))),\n")
p = "src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala"
remove(p, "    GameCommand.RevealVision(actor, vision),\n    GameCommand.PlayConspiracy(actor, None),\n")
remove(p, "  private val vision = FirstGameRulesData.visions.collectFirst {\n    case id: VisionId => id\n  }.get\n")
p = "src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala"
remove(p, "MinorActionCommand, NegotiationCommand, VisionCommand}", "MinorActionCommand, NegotiationCommand}")
remove(p, "          \"vision\" -> rules.handle(state, VisionCommand.Reveal(actor,\n"
          "            FirstGameRulesData.visions.collectFirst { case id: VisionId => id }.get)),\n")
p = "frontend/src/test/scala/oathdigital/frontend/ProtocolTestCommands.scala"
remove(p, "  def RevealVision(actor: String, id: String) = Intent.RevealVision(id)\n"
          "  def PlayConspiracy(actor: String, target: Option[oathdigital.protocol.ConspiracyTarget]) = Intent.PlayConspiracy(target)\n")
cut(p, "private[frontend] object ConspiracyTarget {", "private[frontend] object DecisionResolution {")
cut("frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala",
    "  test(\"Vision and Conspiracy controls preserve opaque targets and pending decisions\") {",
    "  test(\"Negotiation projection decodes redacted ledger and encodes authored terms\") {")
p = "frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala"
cut(p, "    val reveal = action(\"reveal-vision\")\n",
    "      Some(GameCommand.PlayConspiracy(\"red\", None)))\n", inclusive=True)
remove(p, "    assertEquals(ServerUiSupport.actionLabel(\"reveal-vision\"), \"Reveal Vision\")\n"
          "    assertEquals(ServerUiSupport.actionLabel(\"play-conspiracy\"), \"Play Conspiracy\")\n")
remove(p, "actionKind = \"play-conspiracy\", minimum = 0, maximum = 0)),",
          "actionKind = \"travel\", minimum = 0, maximum = 0)),")
EOF
git diff --stat
```

Read `git diff` for `LegalActionProjector.scala` and `ServerUiSupport.scala` in full: the vector that closes `boardTargetActions` must end `negotiators, minimum = 1, maximum = negotiators.size)).flatten`, and the last `commandForSelection` case must still be `case _ => None`.

- [ ] **Step 3: Compile and fix what the deletions leave behind**

Run: `./sbtw -no-colors Test/compile frontend/Test/compile`
Expected: PASS, possibly with warnings. Fix, in this order:
- An unused import the deletions left (for example `FirstGameRulesData` in `PendingWalkerRulesSuite` or `PendingWalkerInvariantSuite`, `VisionState` in `LegalActionProjector`, `ConspiracyTarget` names in the frontend): delete it.
- A symbol the compiler cannot find because the deletion took its only user: read the error; if the symbol is another Conspiracy or Reveal helper, delete it; otherwise restore what was cut by mistake.
- Run `git grep -n -i -E "reveal-?vision|play-?conspiracy|conspiracy-(target|waiting)" -- src/main shared frontend/src` and expect no hits outside `gameplay/actions/Visions.scala`, which Task 6 deletes.

- [ ] **Step 4: Run the suites**

Run: `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`.
Expected: PASS for both. `VisionsSuite` drives the removed `OathRules.handle(VisionCommand)`, so it no longer compiles: `git rm src/test/scala/oathdigital/gameplay/VisionsSuite.scala` in this task (its coverage is listed under Task 6) and delete the `FirstGameRulesData` import that `PendingWalkerInvariantSuite` and `PendingWalkerRulesSuite` no longer use.

- [ ] **Step 5: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(visions): retire the legacy Reveal and Conspiracy commands and intents

Reveal Vision and Conspiracy are facedown-adviser and Search card plays now.
The revealVision and playConspiracy intents, their commands, controls, the
play-conspiracy board-target action and the conspiracy pending phases go, along
with the frontend cases that sent them.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Delete the legacy Visions rules, events, pending state and audit

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/VisionRules.scala`
- Delete: `src/main/scala/oathdigital/gameplay/actions/Visions.scala`, `src/main/scala/oathdigital/gameplay/actions/MinorActionPowerSupport.scala` (`VisionsSuite` went in Task 5)
- Modify: `OathRules.scala`, `OathRulesWalker.scala`, `model/GameEventProtocol.scala`, `model/PendingProcedures.scala`, `model/GameProcedureProtocol.scala`, `model/GameViolation.scala`, `serialization/GameEventWire.scala`, `serialization/ActionEventCodec.scala`, `serialization/GameEventJsonSupport.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`, `src/test/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedureSuite.scala`

**Interfaces:**
- Consumes: Task 5 (nothing calls `Visions.handle`).
- Produces: `VisionRules` in its own file, unchanged; no `Conspiracy*` or `VisionRevealed` event, no `PendingProcedure.Conspiracy`, no `OathContinue.AwaitingConspiracyDecision`, no minor-action audit.

`VisionsSuite` is deleted, not migrated. Its live coverage is already elsewhere: Reveal (Task 4, `VisionPlaySuite`), a relic target, a Peoples Favor target, a Darkest Secret target, no legal target and a Search-kept Conspiracy (Task 3, `ConspiracyWhenPlayedSuite` and `VisionPlaySuite`). The tests it dropped covered behaviour the spec deletes: the six-check audit, the altered-Foundation check, the direct command, and stale recorded completions.

- [ ] **Step 1: Move `VisionRules` out of `Visions.scala`**

Create `src/main/scala/oathdigital/gameplay/actions/VisionRules.scala`:

```scala
package oathdigital.gameplay.actions

import oathdigital.model._

object VisionRules {
  val Conquest = VisionId("vision:vision-of-conquest")
  val Sanctuary = VisionId("vision:vision-of-sanctuary")
  val Rebellion = VisionId("vision:vision-of-rebellion")
  val Faith = VisionId("vision:vision-of-faith")
  val Conspiracy = VisionId("vision:conspiracy")

  val goals: Map[VisionId, OathkeeperGoal] = Map(
    Conquest -> OathkeeperGoal.Supremacy,
    Sanctuary -> OathkeeperGoal.Protection,
    Rebellion -> OathkeeperGoal.ThePeople,
    Faith -> OathkeeperGoal.Devotion)

  def trueGoal(id: VisionId): Option[OathkeeperGoal] = goals.get(id)
}
```

Then remove the same `object VisionRules { ... }` from `Visions.scala`, and run `./sbtw -no-colors compile`. Expected: PASS (the import path `oathdigital.gameplay.actions.VisionRules` is unchanged).

- [ ] **Step 2: Delete the rules and their tests**

```bash
git rm src/main/scala/oathdigital/gameplay/actions/Visions.scala src/main/scala/oathdigital/gameplay/actions/MinorActionPowerSupport.scala
```

In `OathRules.scala`, delete the import `import oathdigital.gameplay.actions.Visions` and the three evolve cases:

```scala
      case event: VisionRevealed => Visions.evolve(catalog, state, event)
      case event: ConspiracyStarted => Visions.evolve(catalog, state, event)
      case event: ConspiracyCompleted => Visions.evolve(catalog, state, event)
```

In `MinorActionsSuite.scala`, delete the two tests "audited minor-action power inventory rejects changed handler vocabulary" and "Vision inventory fingerprint covers every runtime handler family and edifice face", and `MinorActionPowerSupport` from the import at the top. Delete any import the compiler then reports as unused (`CatalogPower`, `UnsupportedMinorActionCatalogInventory`, `UnsupportedMinorActionRule`).

- [ ] **Step 3: Delete the events, their wire types and codecs**

```bash
python3 - <<'EOF'
import pathlib

def read(path):
    return pathlib.Path(path).read_text()

def remove(path, old, new=""):
    source = read(path)
    assert source.count(old) == 1, f"{path}: expected exactly one:\n{old}"
    pathlib.Path(path).write_text(source.replace(old, new))

def cut(path, start, end, inclusive=False):
    source = read(path)
    assert source.count(start) == 1 and source.count(end) == 1, f"{path}: {start!r} / {end!r}"
    a, b = source.index(start), source.index(end)
    assert a < b, path
    b = b + len(end) if inclusive else b
    pathlib.Path(path).write_text(source[:a] + source[b:])

cut("src/main/scala/oathdigital/model/GameEventProtocol.scala",
    "  final case class VisionRevealed(", "  final case class SiteRelicsPeeked(")
p = "src/main/scala/oathdigital/serialization/GameEventWire.scala"
remove(p, '  val VisionRevealedType = "gameplay.vision-revealed"\n'
          '  val ConspiracyStartedType = "gameplay.conspiracy-started"\n'
          '  val ConspiracyCompletedType = "gameplay.conspiracy-completed"\n')
p = "src/main/scala/oathdigital/serialization/ActionEventCodec.scala"
remove(p, "      case _: VisionRevealed => VisionRevealedType\n"
          "      case _: ConspiracyStarted => ConspiracyStartedType\n"
          "      case _: ConspiracyCompleted => ConspiracyCompletedType\n")
cut(p, "      case VisionRevealed(player, vision, replaced, destination) => ujson.Obj(",
    "  }\n\n  protected final def actionDecode(")
cut(p, "        case VisionRevealedType => for {", "    }\n    decoder.lift(eventType)")
cut("src/main/scala/oathdigital/serialization/GameEventJsonSupport.scala",
    "  protected final def encodeConspiracyTarget(",
    "  protected final def encodeForceKind(")
EOF
git diff --stat
```

- [ ] **Step 4: Delete the pending state and its continuation**

```bash
python3 - <<'EOF'
import pathlib

def read(path):
    return pathlib.Path(path).read_text()

def remove(path, old, new=""):
    source = read(path)
    assert source.count(old) == 1, f"{path}: expected exactly one:\n{old}"
    pathlib.Path(path).write_text(source.replace(old, new))

def cut(path, start, end, inclusive=False):
    source = read(path)
    assert source.count(start) == 1 and source.count(end) == 1, f"{path}: {start!r} / {end!r}"
    a, b = source.index(start), source.index(end)
    assert a < b, path
    b = b + len(end) if inclusive else b
    pathlib.Path(path).write_text(source[:a] + source[b:])

p = "src/main/scala/oathdigital/model/PendingProcedures.scala"
cut(p, "sealed trait ConspiracyTarget extends Product with Serializable",
    "/** Stable, container-qualified target for a denizen printed at a site. */")
cut(p, "  final case class Conspiracy(", "  final case class Negotiation(")
remove("src/main/scala/oathdigital/model/GameProcedureProtocol.scala",
       "  final case class AwaitingConspiracyDecision(playerId: PlayerId,\n"
       "      decision: DecisionId) extends OathContinue\n")
p = "src/main/scala/oathdigital/model/GameViolation.scala"
remove(p, "  final case class UnsupportedMinorActionRule(source: CardId, handlers: Vector[String])\n"
          "      extends OathViolation\n"
          "  final case class UnsupportedVisionRule(source: String, handler: String)\n"
          "      extends OathViolation\n"
          "  final case class VisionUnavailable(detail: String) extends OathViolation\n")
remove(p, "  final case class ConspiracyDecisionMismatch(expected: DecisionId, actual: DecisionId)\n"
          "      extends OathViolation\n"
          "  final case class ConspiracyOutcomeMismatch(detail: String) extends OathViolation\n"
          "  final case class UnsupportedMinorActionCatalogInventory(expected: String, actual: String)\n"
          "      extends OathViolation\n")
p = "src/main/scala/oathdigital/gameplay/OathRulesWalker.scala"
remove(p, "      val conspiracy = treeless.game.current.pending.collect {\n"
          "        case value: PendingProcedure.Conspiracy if value.awaitingTarget => value\n"
          "      }\n"
          "      val continued = conspiracy match {\n"
          "        case Some(value) => Right(OathContinue.AwaitingConspiracyDecision(\n"
          "          value.actor, value.decision))\n"
          "        case None if runsTurnBoundary(procedure) =>\n"
          "          Right(OathContinue.AwaitingWakeAction(turn.activePlayer))\n"
          "        case None => continuationIn(turn.phase, turn.activePlayer)\n"
          "      }\n",
          "      val continued =\n"
          "        if (runsTurnBoundary(procedure))\n"
          "          Right(OathContinue.AwaitingWakeAction(turn.activePlayer))\n"
          "        else continuationIn(turn.phase, turn.activePlayer)\n")
remove(p, "          if (conspiracy.nonEmpty) Right(transition)\n"
          "          else if (runsActionBoundary(procedure)) completeAction(transition)\n",
          "          if (runsActionBoundary(procedure)) completeAction(transition)\n")
EOF
git diff --stat
```

`ConspiracyUnavailable` stays: the power uses it. `MinorActionOutcomeMismatch` and `MinorActionUnavailable` stay too, because `MinorActions.scala` and `CardPlay.scala` use them.

- [ ] **Step 5: Migrate the two tests that referred to deleted symbols**

In `GameEventWireSuite.scala`, replace the whole test named "v12 Vision and Conspiracy events preserve hidden choices exactly" (from its `test(` line to the line before `  test("v11 Negotiation events preserve authored terms and disclosures") {`) with:

```scala
  test("a Vision victory event round trips") {
    val events = Vector[OathEvent](OathEvent.VisionVictory(PlayerId("red"),
      VisionId("vision:vision-of-faith")))
    val encoded = GameEventWire.encodeStream("visions", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

```

In `OathkeeperProcedureSuite.scala`, in the test that builds a legacy pending procedure, replace

```scala
      pending = Some(PendingProcedure.Conspiracy(DecisionId("powered-vision"),
        actor.player, oathdigital.gameplay.actions.VisionRules.Conspiracy,
        None))))
```

with

```scala
      pending = Some(PendingProcedure.Challenge(DecisionId("legacy-pending"),
        actor.player, Banner.PeoplesFavor, None, 0, 1))))
```

The test only needs some legacy pending procedure, and `Challenge` still exists.

- [ ] **Step 6: Compile, fix leftovers and run everything**

Run: `./sbtw -no-colors Test/compile frontend/Test/compile`. Fix in this order: unused imports; a match that was exhaustive over `OathEvent`, `PendingProcedure` or `OathContinue` and now names a deleted case (`git grep -n -E "Conspiracy(Started|Completed)|VisionRevealed|PendingProcedure\.Conspiracy|AwaitingConspiracyDecision|ConspiracyTarget" src shared frontend/src` lists them; there should be none left); and any documentation comment that still says the Conspiracy handoff exists (`git grep -n -i "awaitingTarget\|conspiracy continuation\|legacy continuation" src`).

Run: `./sbtw -no-colors test` and `./sbtw -no-colors frontend/test`.
Expected: PASS. Pay attention to `BackendArchitectureSuite`: its rule "migrated modules never directly edit owned material state" names the Conspiracy boxing bypass in its comment and may count the `// executor bypass:` sentinels. `Visions.scala` held one; `Campaign.scala` holds another that stays. If the suite asserts a sentinel count or file list, update it to the remaining sites.

- [ ] **Step 7: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(visions): delete the legacy Visions rules, events and audit

The Visions object, the VisionRevealed and Conspiracy events and codecs,
PendingProcedure.Conspiracy, its continuation, the minor-action handler audit and
its fingerprint are gone. VisionRules moves to its own file.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Documentation and final verification

**Files:**
- Modify: `docs/superpowers/specs/2026-09-19-visions-conspiracy-walker-design.md` (status line)
- Modify: `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (status line, roadmap step 3, "What remains")

- [ ] **Step 1: Update the specs**

In `2026-09-19-visions-conspiracy-walker-design.md`, change the status line to: `> Status: implemented by [the plan](../plans/2026-09-19-visions-conspiracy-walker.md). Extends the [procedure walker design]...` (keep the links that follow).

In `2026-09-05-procedure-walker-design.md`:
- In the status line, `Reveal Vision and Conspiracy have since moved onto the walker (see the [Visions and Conspiracy design](2026-09-19-visions-conspiracy-walker-design.md)).`
- In roadmap step 3 ("Port remaining actions in batches"), after the sentence "Economy (Muster and Trade) is ported and its legacy path deleted; see the Muster and Trade design.", add: "Visions (Reveal Vision and Conspiracy) are ported and their legacy path deleted; see the Visions and Conspiracy design."
- In "What remains", delete the bullet "- `Conspiracy`: CardPlay and Visions." and lower the count in the sentence before the list by one; remove "and Visions" from the "Step 3:" line above it.

- [ ] **Step 2: Full verification**

Run `./sbtw -no-colors test`, `./sbtw -no-colors frontend/test` and `./sbtw -no-colors frontend/fastLinkJS`. Expected: PASS for all three.

Run `git grep -n -i conspiracy -- src/main shared frontend/src` and read the hits. The only ones left should be: `ConspiracyWhenPlayed` and its catalog entry; `VisionRules.Conspiracy` and its users (`CardPlay`, `LegalActionProjector`'s removal leaves none); `ConspiracyUnavailable`; the Campaign Raid's own `CampaignRules.Conspiracy` and `boxedConspiracy` (a Raid can box a Conspiracy through its own path, which stays); `FirstGameSetup`; and `VisionCardPresentation`.

Run `git grep -n -E "MinorActionPowerSupport|validateFaceupVision|structuralFingerprint" -- src/main`: the only hits should be the Negotiation and Rest fingerprints, which are separate and stay.

Play through it by hand if a dev server is available (`./sbtw run`): Search with a Conspiracy on top of the deck and an enemy pawn and relic at your site, keep it faceup, and choose the relic in the decision panel; then play a facedown Vision faceup from the facedown-adviser button and check it replaces the revealed Vision.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs
git commit -m "docs: record Visions and Conspiracy as ported to the walker

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.**
- Reveal as a facedown Vision play into the revealed slot, with the replaced Vision to the next region and no Supply: Task 4 (`validateOrigin`, the `VisionPlaySuite` Reveal test); the placement itself already existed.
- Conspiracy as a `WHEN PLAYED` power (target `Decide`, effects, removal, no legal target, no decline): Task 3.
- Conspiracy from both origins: Task 3 (temporary hand) and Task 4 (facedown adviser).
- `planVision` special case, no replacement decision, hook no longer suppressed, `validateOrigin` calls removed: Tasks 3 and 4.
- `Move` to `SharedBank`, its validator and mutation, the pipeline's expected-set allowance, replay and wire: Task 1.
- Option refs, wire spellings, projector, decision panel: Task 2 (no frontend change needed; Spec clarification 4).
- Deletions: the command surface in Task 5, the rules, events, codecs, pending state, violations and audit in Task 6.
- Deferred restrictions, the deleted fingerprint and the unchecked Foundations: stated in Global Constraints; no task implements them.
- Verification list: boxing and its rejections (Task 1); relic, Peoples Favor with order, Darkest Secret with burn, no target, stale answer, no replacement (Tasks 3 and 4); Reveal (Task 4); journal round trip and replay (Tasks 1 and 3, with the option refs in Task 2); migrated suites (Tasks 3, 5 and 6).
- "Left for the plan": the window and id are `ActionCardPlayed` and `PowerId("vision.conspiracy")`; `resultingSource` (Spec clarification 2); the label source (clarification 1); the frontend draft (clarification 4); `DomainValidation` (clarification 3); the doc updates (Task 7).

**Placeholder scan.** No "TBD", "TODO" or "similar to Task N". Every code step shows code; deletion steps are scripts whose markers are exact text from the current sources, or name the symbol, the file and the surrounding lines.

**Type consistency.** `ConspiracyWhenPlayed.decisionId` is `"cardplay.conspiracy.target"` in the power, its tests and `VisionPlaySuite`. `DecisionOptionRef.RelicSlot(owner: PlayerId, slot: Int)` and `DecisionOptionRef.Banner(banner: Banner)` are spelled the same in Task 2's model and tests, the power and its suite. `OperationRun.boxed` is defined in Task 1 and used only there. `CardPlay.planVision` takes `(catalog, player, origin, id, orientation, replace, adviserLimit)` in its definition and its one call site (Task 3). `validateOrigin` takes `(player, card, origin)` in its definition and its one call site (Task 4).
