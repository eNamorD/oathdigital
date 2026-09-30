# Global Operation Restrictions, Slice 3 (Grand Scepter, Empty Choices and Take) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Grand Scepter in play wherever an operation runs, let a choice the search leaves empty be passed instead of failing, retire Fae Merchant's own scepter filter, and move the banner by `Take` in Challenge and Conspiracy.

**Architecture:** A printed `GrandScepter` restriction joins `LockedCards` and the Hall of Ministers in `OperationRestrictions.printed`. A choose-one `Decide` marked `passWhenEmpty` is treated by the search like an optional choose-many: with no surviving option it is passed, by the walk and by a search, instead of failing the path. With one option left it parks as any decision does, so the player confirms it. Fae Merchant then offers every held relic in such a choice and lets the restriction and the search hide the scepter. Challenge custody becomes a required `Take`, and Conspiracy's banner transfer an optional `Take`.

**Tech Stack:** Scala 3, sbt through `./sbtw`, munit.

**Spec:** [Global operation restrictions design](../specs/2026-09-30-global-operation-restrictions-design.md): "Rules" (Grand Scepter), "Lazy pruning" (Empty choices), "What retires", "Take for Challenge and Conspiracy", and slice 3 of "Slicing" and "Testing". Read both documents before starting.

## Global Constraints

- Build and test with `./sbtw`. The compiler runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import fails the build.
- No production Scala file may exceed 800 lines (`BackendArchitectureSuite`, "all production Scala files stay bounded"; also `python3 scripts/check-architecture.py`). `ProcedureWalker.scala` has exactly 800 lines at the start. This plan does not change it: the walker already passes an optional decision the search leaves empty.
- `BackendArchitectureSuite` still applies: no power names in walker sources (comments included), and powers do not import `gameplay.walker`.
- Every global restriction refuses as `Impossible`. An optional operation it refuses is skipped; a required one rejects the batch.
- Replay applies recorded operations without re-checking restrictions. Do not change `WalkerReplay`.
- No timing check joins `sbt test`. The `SearchBudget` benchmark stays a program run by hand.
- Never touch the live database under `var/oathdigital`.
- Commits end with the trailer the session's system reminder names.

## Decisions taken at plan time

These answer the questions put to the product owner on 2026-09-30. The spec
already records them.

- **One option still parks.** When the search leaves a decision one option,
  the player sees that option and clicks to confirm it. The walker never
  answers for the player.
- **Empty choices are passed.** Removing Fae Merchant's filter would otherwise
  refuse the action for a player holding only the scepter with an empty relic
  deck: the scepter's bury is refused, so the choice has no option left.
  `Decide.passWhenEmpty` makes such a choose-one pass instead, as an empty
  optional choose-many already does. Only a choose-one may carry the flag.
- **Fae Merchant always asks.** It declares its choice whenever it holds a
  relic, so a lone relic is confirmed with one click, where today it goes
  back silently. Declaring the choice is what lets the search prune the
  scepter and pass an empty choice.
- **Challenge custody** sits directly in the tree, so the walker checks the
  `Take` whole and records its `Move` leaf: the Game Log line, which reads that
  `Move`, needs no change. The custody is a required `Take` (`Take` gains
  `required`, like `Give`), because the payment buys the banner: a refused
  custody fails the path and the search hides that banner.
- **Conspiracy's** banner `Take` runs in its `BuildOps`, so it is recorded as a
  `Take`. Its Game Log line is a note, so it is unchanged. It stays optional,
  like the relic `Give` beside it; whether a refused target is hidden is
  decided with Lost Tongue.
- **`required` stays off the wire**, as it is for `Give` and every discard:
  replay never re-checks restrictions.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala`: the printed restriction.
- Modify `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`: `printed` adds one `GrandScepter` per scepter relic.
- Modify `src/main/scala/oathdigital/model/CoreOperations.scala`: `Decide.passWhenEmpty`; `Take.required`.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`: a choose-one passed when empty, in `narrow`, `narrowed` and `reach`.
- Modify `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala` (becomes a `case object`), `DiceAndRelicDrawPowers.scala` and `gameplay/powers/PhasePowerCatalog.scala`.
- Modify `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala` and `gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`: `Take`.
- Modify `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` and `application/gamelog/CampaignLines.scala`: the `Take` patterns gain a sixth `_`.
- Tests: `OperationRestrictionsSuite`, `WalkerSearchSuite`, new `gameplay/PassWhenEmptySuite`, `FaeMerchantSuite`, `DicePowerDecisionProjectionSuite`, `ChallengeProcedureSuite`, `ConspiracyWhenPlayedSuite`.
- Docs: the spec's status line and `docs/ROADMAP.md`.

---

### Task 1: The Grand Scepter restriction

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`

**Interfaces:**
- Produces: `final case class GrandScepter(relic: RelicId) extends OperationRestriction`, refusing with code `"grand-scepter"`. `OperationRestrictions.forCatalog(catalog).printed` holds one per `RelicRole.GrandScepter` relic.

- [ ] **Step 1: Write the failing tests**

In `OperationRestrictionsSuite.scala`, change the import to
`import oathdigital.gameplay.operations.{GrandScepter, LockedCards, OperationRestrictions}`,
add `private val scepter = RelicId("grand-scepter")` beside `drum`, and add
before the test "the catalog prints a lock for every lock-icon card and only
those":

```scala
  test("the Grand Scepter refuses a discard, a return to the relic deck and " +
      "a Bury, and allows a Take and a Give"):
    val ready = Table.start.relic(p1, scepter).ready
    val mine = PositionedLocation(Location.PlayArea(p1))
    val removals = Vector[CoreOperation](
      Discard.Relic(scepter, mine, 0, p1),
      Move(Piece.Card(scepter), mine, PositionedLocation(
        Location.Deck(CardDeck.Relic), StackPosition.Bottom)),
      Bury(BuryableCard.Relic(scepter), mine))
    assertEquals(removals.map(refusal(ready, _)),
      Vector.fill(3)(Some("grand-scepter")))
    val passes = Vector[CoreOperation](
      Take(Piece.Card(scepter), p2, Location.PlayArea(p1),
        Location.PlayArea(p2)),
      Give(Piece.Card(scepter), p1, Location.PlayArea(p1),
        Location.PlayArea(p2)))
    assertEquals(passes.map(refusal(ready, _)), Vector.fill(2)(None))

  test("the catalog prints one Grand Scepter restriction, for its scepter"):
    assertEquals(set.printed.collect { case value: GrandScepter => value.relic },
      Vector(scepter))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: compile failure, `Not found: GrandScepter`.

- [ ] **Step 3: Write the restriction**

Create `GrandScepter.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** The Grand Scepter cannot be removed from play (global operation
  * restrictions design, "Rules"). A move that takes it from a site or a play
  * area to anywhere else is refused: a discard to the set-aside relics or a
  * return to the relic deck. A `Bury` of it is refused too. Passing it between
  * players, by a `Take` or a `Give`, keeps it in play and is allowed. The
  * scepter is never facedown, so a refusal reveals nothing.
  */
final case class GrandScepter(relic: RelicId) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Option.when(Operation.flatten(operation).exists(removes))(OperationReason(
      "grand-scepter", s"${relic.value} cannot be removed from play",
      OperationReasonKind.Impossible))

  private def removes(step: Operation): Boolean = step match
    case Move(Piece.Card(moved), from, to, _) if moved == relic =>
      OperationRestrictions.inPlay(from.location) &&
        !OperationRestrictions.inPlay(to.location)
    case Bury(BuryableCard.Relic(buried), _, _) => buried == relic
    case _ => false
```

In `OperationRestrictions.scala`:
- change the catalog import to `import oathdigital.catalog.{CardRestrictions, ExecutableCatalog, RelicRole}`;
- in the class doc, change "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every lock-icon card, and the [[HallOfMinisters]]." to "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every lock-icon card, the [[HallOfMinisters]], and one [[GrandScepter]] per scepter relic.";
- replace the last line of `printedBy`, `Vector(LockedCards(locked), HallOfMinisters(catalog))`, with:

```scala
    val scepters = catalog.relics.filter(_.role == RelicRole.GrandScepter)
      .map(relic => GrandScepter(RelicId(relic.id.value)))
    Vector(LockedCards(locked), HallOfMinisters(catalog)) ++ scepters
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.gameplay.powers.action.FaeMerchantSuite"`
Expected: PASS. Fae Merchant still filters the scepter itself, so its suite is unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala
git commit -m "feat(restrictions): keep the Grand Scepter in play"
```

---

### Task 2: Choices passed when empty

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:589-600` (`Decide`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`
- Create: `src/test/scala/oathdigital/gameplay/PassWhenEmptySuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala`

**Interfaces:**
- Produces: `Decide(decisionId, owner, query, window = None, coOwners = Vector.empty, passWhenEmpty: Boolean = false)`, with `passWhenEmpty` allowed only on a `ChooseOne`. `WalkerSearch.narrow` returns `Right(None)` and `WalkerSearch.reach` returns `Right(Reach.Skipped)` for such a decision with no surviving option. `ProcedureWalker` is unchanged: its `runLeaf` already passes a decision `narrow` returns as `None`.

- [ ] **Step 1: Write the failing tests**

In `WalkerSearchSuite.scala`, add after `private val abc`:

```scala
  private def passable(query: DecisionQuery) =
    decide(query).copy(passWhenEmpty = true)
```

and these tests after "an optional choose-many with no survivor is not asked":

```scala
  test("a choose-one passed when empty, with no survivor, is not asked, and " +
      "a search passes it"):
    val asked = passable(DecisionQuery.ChooseOne(Vector(button("a"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Right(None))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)),
      Right(WalkerSearch.Reach.Skipped))

  test("a choose-one passed when empty keeps its one survivor to ask"):
    val asked = passable(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ == ChooseOneAnswer(ref("b")))),
      Right(Some(passable(DecisionQuery.ChooseOne(Vector(button("b")))))))
```

Create `PassWhenEmptySuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerOutcome, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** A choose-one marked `passWhenEmpty` is passed when the search leaves it
  * no option, and parks as any decision does when one is left (global
  * operation restrictions design, "Empty choices"). Locked refuses to discard
  * the faceup Sealing Ward, which is what prunes an option here.
  */
class PassWhenEmptySuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val ready = Table.start.adviser(p1, lockedCard).ready
  private val ask = "test.ask"
  private val next = "test.next"
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)
  private def chose(key: String): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref(key))

  private val discardLocked: Operation = Discard.Denizen(lockedCard,
    PositionedLocation(Location.PlayArea(p1)), Region.Provinces,
    catalog.suitOf(lockedCard).get, 0, 0, p1, required = true)

  /** A choice of `keys` passed when empty, then the discard when "discard"
    * was chosen. */
  private def passable(keys: String*): Operation = Sequence(Vector[Operation](
    Decide(ask, p1, DecisionQuery.ChooseOne(keys.toVector.map(button)),
      passWhenEmpty = true),
    Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`ask`, answer, _) => answer
    }.filter(_ == chose("discard")).toVector.map(_ => discardLocked))))

  private def offered(tree: Operation): Vector[DecisionOptionRef] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        ProcedureWalker.openDecisions(ready, tree, pending, powers).head
          .query match
          case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a park, got $other")

  test("one option left parks, offering it for the player to confirm"):
    assertEquals(offered(passable("discard", "keep")), Vector(ref("keep")))

  test("a single declared option parks"):
    assertEquals(offered(passable("keep")), Vector(ref("keep")))

  test("with no option left the decision is passed and the action still runs"):
    val tree = passable("discard")
    assert(WalkerSimulation.starts(tree, ready, powers))
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect {
          case WalkerStepRecorded(_, choice: ChoicePayload, _, _) => choice
        }, Vector.empty)
      case other => fail(s"expected a finished walk, got $other")

  test("a search passes a later choice with nothing left, so the earlier " +
      "option stays offered"):
    val tree = Sequence(Vector[Operation](
      Decide(next, p1, DecisionQuery.ChooseOne(Vector(button("onward"),
        button("stop")))),
      Branch((_, pending) =>
        if pending.answered.exists(_.answer == chose("onward"))
        then Vector(passable("discard")) else Vector.empty)))
    assertEquals(offered(tree), Vector(ref("onward"), ref("stop")))

  test("only a choose-one may be passed when empty"):
    intercept[IllegalArgumentException](Decide(ask, p1,
      DecisionQuery.ChooseMany(0, 1, Vector(button("a")), None),
      passWhenEmpty = true))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PassWhenEmptySuite oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: compile failure: `passWhenEmpty` is not a member of `Decide`.

- [ ] **Step 3: Add the flag to `Decide`**

In `CoreOperations.scala`, add to the end of the `Decide` doc, after the `window` paragraph:

```scala
  *
  * `passWhenEmpty` lets the search leave a choose-one with no option (global
  * operation restrictions design, "Empty choices"): the walker then passes
  * it, as it passes an empty optional choose-many, instead of failing the
  * path. With one option left it still parks, so the player confirms it.
```

and change the declaration to:

```scala
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None,
    coOwners: Vector[PlayerId] = Vector.empty,
    passWhenEmpty: Boolean = false)
    extends PrimitiveOperation:
  require(!passWhenEmpty || query.isInstanceOf[DecisionQuery.ChooseOne],
    "only a choose-one can be passed when empty")
```

- [ ] **Step 4: Teach the search to pass it**

In `WalkerSearch.scala`:

In `narrow`, change the choose-one case's `required = true` to
`required = !decide.passWhenEmpty`, and change its doc's last sentence to "`Right(None)` is an optional one with nothing left, which is not asked: a choose-many whose minimum is zero, or a choose-one passed when empty."

Replace `reach` with:

```scala
  /** What a search does at `decide`: it passes an optional decision with
    * nothing to ask, stops at the first answer that survives, and fails when
    * none does. A choose-one passed when empty is passed when none does. A
    * kind whose answers are not tried (`Partition`, `Distribute`) counts as
    * answerable, and the answer-time check stays.
    */
  def reach(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Reach] =
    narrowed(decide, _ => true) match
      case None => Right(Reach.Skipped)
      case Some(_) => answers(decide) match
        case None => Right(Reach.Answerable)
        case Some(all) => survivor(all, verdict, None) match
          case Left(_) if decide.passWhenEmpty => Right(Reach.Skipped)
          case found => found.map(_ => Reach.Answerable)
```

In `narrowed`, replace the choose-one case with:

```scala
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filter(o => permitted(o.ref))
        if decide.passWhenEmpty && options.isEmpty then None
        else Some(decide.copy(query = one.copy(options = options)))
```

and change its doc's second sentence to "`None` is an optional choose-many or a choose-one passed when empty, with nothing left, which is not asked."

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.PassWhenEmptySuite oathdigital.gameplay.walker.WalkerSearchSuite oathdigital.gameplay.LazyPruningSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `LazyPruningSuite`'s "a later required decision, another player's, with every option pruned hides the earlier option" still holds: a decision without the flag still fails the path.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(walker): pass a choose-one the search leaves empty when it asks to be"
```

---

### Task 3: Fae Merchant offers every relic held

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala` (whole file)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala:20`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala`, `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes: `Decide(..., passWhenEmpty = true)` (Task 2), `GrandScepter` (Task 1), `BuildOps(build, required = true)` (slice 2).
- Produces: `case object FaeMerchant extends PaidAction` with `decisionId` and `returned` unchanged; `FaeMerchant.forCatalog` is gone. `DiceAndRelicDrawPowers.powers: Vector[PhasePower]` replaces `DiceAndRelicDrawPowers.forCatalog`.

- [ ] **Step 1: Write the failing tests**

In `FaeMerchantSuite.scala`, change `val merchant = FaeMerchant.forCatalog(catalog)` to `val merchant = FaeMerchant`.

Replace the test "with only the Grand Scepter held, the drawn relic is the one relic eligible and goes straight back" with:

```scala
  test("with only the Grand Scepter held, the drawn relic is offered alone " +
      "for the player to confirm"):
    val ready0 = staged(Vector(scepter))
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
```

Replace the test "with no other relic the drawn one is the only candidate and no decision is asked" with:

```scala
  test("with no other relic the drawn one is offered alone, and confirming " +
      "puts it back"):
    val ready0 = staged(Vector.empty)
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
```

Replace the test "an empty relic deck still puts one held relic on the bottom" with the two tests below. `emptied` is the same state that test built:

```scala
  /** `ready0` with the relic deck moved to the reliquary. */
  private def emptied(ready0: ReadyGame): ReadyGame =
    val current = ready0.game.current
    ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))

  test("an empty relic deck still offers the one held relic, and confirming " +
      "puts it on the bottom"):
    val ready0 = emptied(staged(Vector(held1)))
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck, Vector(held1))

  test("with only the Grand Scepter held and an empty relic deck, it asks " +
      "nothing and puts nothing back"):
    val ready0 = emptied(staged(Vector(scepter)))
    assert(usableIds(ready0).contains(FaeMerchant.id))
    val rules0 = rules()
    val done = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck, Vector.empty[RelicId])
    assertEquals(tokensOn(end, fae), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)
```

In `DicePowerDecisionProjectionSuite.scala`, after the test "Fae Merchant names both eligible relics to its owner, including the facedown one just taken", add:

```scala
  test("Fae Merchant does not offer the Grand Scepter"):
    val fae = DenizenId("180")
    val held = RelicId("R08")
    val scepter = RelicId("grand-scepter")
    val ready0 = act(withBoard(withRelic(withRelic(atHome(base, fae), scepter),
      held))(_.copy(faceUpSecrets = 2)))
    val top = ready0.game.current.commonCards.relicDeck.head
    val parked = ready(use(rules(), ready0, FaeMerchant.id,
      DecisionOptionRef.Denizen(fae)).toOption.get.state)
    assertEquals(owner(parked).get.query.get.offeredOptions.map(_.id),
      Vector(held.value, top.value))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite"`
Expected: compile failure on `val merchant = FaeMerchant` (the companion object is not a power). With that line reverted, the three one-relic tests fail because today's Fae Merchant asks nothing when one relic is eligible. "Fae Merchant does not offer the Grand Scepter" and the scepter-only test already pass on the old filter; they are regression guards for its removal.

- [ ] **Step 3: Rewrite Fae Merchant**

Replace `FaeMerchant.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, RelicDraws}
import oathdigital.model._

/** Fae Merchant (card 180), ACTION: place 1 secret on this card, draw a relic
  * and take it facedown, then put exactly one relic you hold, except the
  * Grand Scepter, on the bottom of the relic deck. The relic just taken is
  * eligible.
  *
  * Three siblings run in order: the draw, a live `Branch` that holds only the
  * decision and reads the relics after the draw, and the bury. The decision
  * offers every relic held, so a lone relic is confirmed with one click. The
  * Grand Scepter's restriction refuses its bury, and the bury is a required
  * batch, so the search hides the scepter. The decision is passed when the
  * search leaves it empty (`Decide.passWhenEmpty`): with only the scepter held
  * and the relic deck empty, nothing goes back. The bury returns any secrets
  * on the relic to their holder. Its `returned` line names the relic chosen,
  * so the choice posts no "Chose" line.
  */
case object FaeMerchant extends PaidAction("denizen.fae-merchant",
    Cost(secret = 1)):
  val decisionId: String = "fae-merchant.relic"
  val returned: NoteKey = NoteKey("returned", Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1),
    NotePart.Text(" on the bottom of the relic deck.")))

  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, returned)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(this.id, RelicDraws.drawNote(source, player)),
    Branch((state, _) => held(state, player) match {
      case Vector() => Vector.empty
      case relics => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(relics.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck")),
        passWhenEmpty = true))
    }),
    BuildOps((state, pending) => putBack(state, player, pending),
      required = true),
    Note(this.id, returnNote(_, player, source), covers = true))))

  /** The relic the bury took from the player, in place of its Buried line. */
  private def returnNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
  yield returned(card, NoteArg.Player(player), NoteArg.Card(relic))

  /** The relics the player holds, in play-area order. */
  private def held(state: ReadyGame, player: PlayerId): Vector[RelicId] =
    PlayerFacts.player(state, player).toOption.toVector.flatMap(_.relics)
      .map(_.id)

  /** Buries the chosen relic. No answer means the search left the decision
    * no relic to offer, so nothing goes back. */
  private def putBack(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val chosen = pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Relic(id)), _) => id
    }
    PlayerFacts.player(state, player).map(holder => chosen.toVector.flatMap {
      id =>
        val secrets = holder.relics.find(_.id == id).fold(0)(_.tokens.secrets)
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0, secrets,
          player)
    })
```

Replace `DiceAndRelicDrawPowers.scala`'s object with (and drop the `ExecutableCatalog` import):

```scala
object DiceAndRelicDrawPowers:
  val powers: Vector[PhasePower] =
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain, DowsingSticks,
      FaeMerchant)
```

In `PhasePowerCatalog.scala`, change `DiceAndRelicDrawPowers.forCatalog(catalog) ++` to `DiceAndRelicDrawPowers.powers ++`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS, including the unchanged tests "it draws a relic, then asks which relic to put on the bottom", "the Grand Scepter is never offered, and cannot be chosen" and "a secret on the relic put back returns to its holder facedown".

Run: `grep -rn "FaeMerchant.forCatalog\|DiceAndRelicDrawPowers.forCatalog" src`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "refactor(powers): hide the Grand Scepter from Fae Merchant through the search"
```

---

### Task 4: Challenge and Conspiracy take the banner

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:434-444` (`Take`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:151`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala:128,130`
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala:131-137`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala:108-110`
- Test: `src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`

**Interfaces:**
- Produces: `Take(piece, player, from, to, sourcePosition = StackPosition.Unspecified, required: Boolean = false)`. `required` is not on the wire.

- [ ] **Step 1: Write the failing tests**

In `ChallengeProcedureSuite.scala`, add after "a power's transforms reach the banner and amount selections" (`ProcedureWalkerSuite` is in the same package, so its test power needs no import):

```scala
  test("a restriction on Take sees Challenge's custody and hides the banner"):
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val tree = ChallengeProcedure.build(catalog, board, p1, Vector.empty)
      .getOrElse(fail("the tree must build"))
    val noBannerTaken = new OperationRestriction:
      override def reason(ready: ReadyGame,
          operation: CoreOperation): Option[OperationReason] = operation match
        case Take(Piece.Banner(_), _, _, _, _, _) => Some(OperationReason(
          "test.no-banner", "no banner may be taken",
          OperationReasonKind.Impossible))
        case _ => None
    val powers = WalkerPowers(Vector(
      ProcedureWalkerSuite.TestOperationRestrictionPower(
        PowerId("test.no-banner"), noBannerTaken)))
    assert(ProcedureWalker.advance(board, tree, None, powers).isLeft)
```

In `ConspiracyWhenPlayedSuite.scala`, in the test "Conspiracy taking the Darkest Secret burns every secret and takes the banner", add before `assertEquals(replayed(f, done), after)`:

```scala
    assert(recorded(done).contains(Take(Piece.Banner(Banner.DarkestSecret),
      f.actor, Location.PlayArea(f.enemy), Location.PlayArea(f.actor))),
      recorded(done).toString)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite"`
Expected: compile failure: the `Take` pattern has six arguments. (After Step 3's model change alone, both new tests fail on behaviour: Challenge parks, and Conspiracy records a `Move`.)

- [ ] **Step 3: Give `Take` a `required` flag**

In `CoreOperations.scala`, change `Take` to:

```scala
/** Moves a piece into the prompted player's custody. A required `Take`
  * rejects its batch, or fails the search's path, when a restriction refuses
  * it.
  */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified,
    override val required: Boolean = false)
    extends CoreOperation:
```

(the body is unchanged). Add a sixth `_` to the `Take` patterns:
`WalkerOperationCodec.scala` line 151 becomes
`case Take(piece, player, from, to, sourcePosition, _) => ujson.Obj(`, and
`CampaignLines.scala` lines 128 and 130 become
`OpStep(Take(Piece.Card(id), _, _, _, _, _), before, after)` and
`OpStep(Take(Piece.Banner(banner), _, _, _, _, _), _, _)`.

Run: `./sbtw Test/compile`
Expected: success. If the compiler names another five-argument `Take` pattern, add `_` there too.

- [ ] **Step 4: Take the banner in Challenge and Conspiracy**

In `ChallengeProcedure.scala`, replace `custody` with:

```scala
  /** The banner moves to the challenger by a required `Take`: the payment
    * buys it, so a refused custody fails the path and the search hides that
    * banner. The walker checks the `Take` whole and records its `Move`, which
    * the Game Log reads.
    */
  private def custody(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    Take(Piece.Banner(banner), actor,
      BannerRules.holder(ready.game.current, banner)
        .fold[Location](Location.SharedBank)(Location.PlayArea(_)),
      Location.PlayArea(actor), required = true)
```

In `ConspiracyWhenPlayed.scala`, replace the local `banner` function in `take` with:

```scala
    def banner(held: Banner, owner: PlayerId): CoreOperation = Take(
      Piece.Banner(held), actor, Location.PlayArea(owner),
      Location.PlayArea(actor))
```

and add to the class doc, after the "take effects" bullet list: "The banner moves by a `Take`, so a restriction on taking sees it; a relic moves by a `Give`. Both are optional."

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.application.gamelog.GameLogActionLineSuite oathdigital.application.gamelog.GameLogRareLinesSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. `GameLogActionLineSuite`'s "Challenge takes the banner from the bank; Place Banner Resource names the amount" and Conspiracy's "line names the banner it seized" show the Game Log lines are unchanged.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(actions): take the banner by Take in Challenge and Conspiracy"
```

---

### Task 5: Close the phase

**Files:**
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md:3-4`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full suite and the architecture check**

Run: `./sbtw test`
Expected: every test passes (2149 before this slice, plus the new ones).

Run: `python3 scripts/check-architecture.py`
Expected: `architecture check passed`.

- [ ] **Step 2: Mark the spec done**

Replace the spec's status paragraph

```markdown
**Status:** designed 2026-09-30. It is the prerequisite of
[Catalog batch 3](2026-09-29-catalog-batch-3-design.md), which starts after it.
```

with

```markdown
**Status:** designed and implemented 2026-09-30, in three slices. It is the
prerequisite of [Catalog batch 3](2026-09-29-catalog-batch-3-design.md), which
starts next.
```

- [ ] **Step 3: Update the roadmap**

In `docs/ROADMAP.md`:
- In "## Now", replace "**Phase - Global operation restrictions** is next, then **Phase - Catalog batch 3**, which depends on it, then **Phase - Card classes**." with "**Phase - Catalog batch 3** is next, then **Phase - Card classes**." and rewrap the paragraph.
- Delete the whole "### Phase - Global operation restrictions" section, from its heading through the "Grand Scepter and Take" item.
- In "### Phase - Catalog batch 3", delete the line "Starts after **Phase - Global operation restrictions**."

Run: `grep -n "Global operation restrictions" docs/ROADMAP.md`
Expected: only the Card classes paragraph's mention of the catalog function that phase built.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md docs/ROADMAP.md
git commit -m "docs: close the global operation restrictions phase"
```
