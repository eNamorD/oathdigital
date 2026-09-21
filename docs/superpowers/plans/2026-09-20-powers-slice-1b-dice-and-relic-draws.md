# Powers Slice 1b: Dice and Relic Draws Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement five powers, each declared only as a `PhasePower` over existing operations: Gambling Hall, Bone Dice, Murky Fountain (the ruined face of E15), Dowsing Sticks and Fae Merchant.

**Architecture:** Every power is an ACTION `PhasePower` built on the slice 1a `PaidAction`, so the engine pays its cost onto the source card and the power writes only `build`. Rolls are `RollMode.Automatic` nodes inside the power's tree, and a result that decides what happens next is read by a `BuildOps` or a live-decision `Branch` from `rollOutcomes`. A relic draw is a `Play` from the top of the relic deck to the player's play area, facedown. No engine change is needed.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (section "Slice 1: ACTION powers", rows 93 Gambling Hall, R24 Bone Dice, E15 Murky Fountain, R09 Dowsing Sticks and 180 Fae Merchant, and "Slice 1a implementation notes"). Slice 1b is the second of four parts of design slice 1, after the [slice 1a plan](2026-09-20-powers-slice-1a-when-played-and-simple-actions.md).

## Global Constraints

- `BackendArchitectureSuite` applies: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources (a lowercase substring scan of the names of files that declare `extends ContributingPower` or `extends PhasePower`); a power imports nothing from `oathdigital.gameplay.walker`; no direct state writes under `gameplay/powers`.
- A power is declared solely by a `ContributingPower` or `PhasePower`. No engine code is added for it. If a task finds that a power cannot be, stop and report the minimal engine change instead of adding it.
- Non-battle rolls are automatic (rulings, "Rules that apply to every power"). Defense dice score with `DefenseDieFace.score` (a Doubler multiplies the total). Attack dice score with `AttackDieFace.score` (a skull face counts two swords, hollow swords one per pair). A skull is any skull face.
- Relics taken by a power draw are taken facedown.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the whole suite with `./sbtw test`. Run one suite with `./sbtw "testOnly <fully.qualified.Suite>"`.
- A fresh git worktree has no `.tooling` directory, which `./sbtw` needs. It is gitignored. Link the main checkout's copy once: `ln -s /Users/roman/projects/oathdigital/.tooling .tooling`.
- Test fixtures keep the card inventory whole: a card leaves the place it came from when it is placed, or `CardIndex` fails.

## What planning found

These facts are read from the code and shaped the tasks. Each was exercised by a throwaway implementation of this whole plan, compiled and run against `main` and then removed: every new suite (the five power suites with their replay and wire round trips, and the projection suite) passed, the full suite passed and `scripts/check-architecture.py` passed. Executing the plan should reproduce that. A difference means `main` has moved.

1. **Nothing here needs an engine change.** Every power is a `PhasePower`. The cost half is the engine's (`PhasePower.cost`, paid onto the source card before `build`'s tree, the empty-card rule included). The rest is `ModifyDicePool`, `Roll`, `Branch`, `BuildOps`, `Decide`, `Gain`, `GainSupply`, `Play`, `Bury.standard` and `EnterPhase`, all existing.
2. **A phase power's roll must be automatic.** `WalkerProcedureRegistry.usePowerEntry` declares `rollDecisionId = None`, so a parked `Roll` inside a `UsePower` has no client continuation and would be rejected. An `Automatic` roll never parks: the walker asks its `WalkerDice` and records a `RollPayload` marked `automatic`. Production dice come from `CampaignDicePort.walkerDice`. Tests pass `CampaignFixture.dice(...)`, which fails loudly on a wrong count, and `WalkerDice.unavailable` (the default) proves that a power did not roll.
3. **Pool life.** `ModifyDicePool(pool, n)` creates the pool, the automatic `Roll` writes the outcome into `CurrentGameState.rollOutcomes`, and `WalkerCompleted` clears `rollPools` and `rollOutcomes`. One `PoolKey` per power is therefore enough. `RollOutcome.score` is `DefenseDieFace.score` for defense faces and `AttackDieFace.score` for attack faces. `RollOutcome.skulls` is the count of `TwoSwordsSkull`, the only skull face.
4. **The live-decision shape covers the roll too.** Recover already reads `rollOutcomes` in a `Branch` after its `Roll`. A `Branch` that returns only a `Decide`, placed after the roll that wrote the state it reads, selects the same decision when the walker resumes against the state stored at the park. Gambling Hall (bank) and Fae Merchant (relic) use it. `Sequence`, `Branch` and `BuildOps` inside a `UsePower` tree are proven by Silver Tongue.
5. **`build` sees the state before the engine pays the cost.** `PhasePowerProcedure.assemble` prepends the `PayCost`, so anything that depends on what the cost changed is read inside a `BuildOps`, which runs after it. Bone Dice reads the relic's tokens there, so the secret it just placed is the one returned on the bury.
6. **Cost shapes.** `Cost(secret = n)` places n faceup secrets on the card, and `Cost(secretBurnt = n)` burns them to the shared bank. Dowsing Sticks therefore needs three faceup secrets and leaves one on the relic. The empty-card rule makes each of these powers unusable while its own card still holds the cost. A relic source is an ordinary card source (`RuleSourceRef.Relic`, faceup, in the holder's play area), and a ruined edifice is one too (`RuleSourceFace.Ruined`, at a site the actor stands on or rules).
7. **`EnterPhase(Rest)` is what Begin Rest emits** after its validation. When the tree finishes, `walkerTransition` reads the continuation off the phase it finished in, so the player lands in `AwaitingRestAction`. `autoFinishRest` runs only after `PhaseTransitionRef.BeginRest`, so a player sent to Rest by Murky Fountain finishes Rest manually even with no usable REST power. This is open question 2.
8. **Murky Fountain and Marble Fountains are one card, E15.** The face decides the power id: `edifice.e15.intact` or `edifice.e15.ruined`. `RuleSourceIndex` reads the edifice's current side, so the wrong face is never a source.
9. **The first game's initial state** (read by a probe): the active player is `p2`, in Wake, with 1 favor, 1 faceup secret, 3 warbands and Supply 7; three players; banks hold Nomad 3, Hearth 4, Order 3, Arcane 3, Beast 4, Discord 3. Card 93 is not dealt, so `atHome` adds it. Card 180 is in the world deck, so `atHome` removes it. E15 is in the edifice deck. The relic deck starts R09, R10, ... R47 (so R09 and R24 are in it, and `withRelic` takes them out); R01 to R08 sit at sites; the Grand Scepter is nowhere, so a test may put it in a hand.
10. **The projector shows an owner their own facedown relic.** `GamePresentationProjector.identifiesCard` accepts `player == owner` in the play area, so Fae Merchant's relic decision projects. A `WalkerDecisionProjection` is dropped whole when one option cannot be presented, which is why Task 5 pins it.
11. **Journal.** Every event these powers emit round-trips through `GameEventWire`, and a replay through `OathRules.evolve` reaches the same state. The tests pin both.
12. **Architecture scan.** Power names come from files that declare `extends PhasePower` or `extends ContributingPower` directly. The five powers extend `PaidAction`, so the scan sees only `PaidAction`, as it does for Wayside Inn. No file needs renaming.
13. **Shared registry.** `PhasePowerCatalog` is edited once, in Task 1: one import and one `++` line that call `DiceAndRelicDrawPowers.forCatalog(catalog)`. Tasks 2 to 5 edit only that new object, so the parallel slices 1c and 1d touch other lines.
14. **The reviewed registry needs nothing.** `ActionPowers` lists When Played cards only, and slice 1a's ACTION powers touched no reviewed entry. `PowerKindsCatalogSuite` pins only modifier and persistent flags. All five catalog powers are `persistent: false`.

## Open questions

Resolve these before executing. The plan takes the default named in each, and says where a different answer changes a file.

1. **Fae Merchant with an empty relic deck.** The rules text is "Draw a relic and take it. Put any relic you hold except the Grand Scepter on the bottom of the relic deck." The ruling says an empty deck is a no-op for Dowsing Sticks and says nothing for Fae Merchant. Default: the put-back is independent of the draw, so with an empty deck the player still puts one held relic on the bottom, and with no eligible relic nothing happens. If the answer is "no draw, no put-back", `FaeMerchant.build` gains a guard on the deck and the test "an empty relic deck still puts one held relic on the bottom" changes to expect no change.
2. **Murky Fountain and auto-finishing Rest.** After a total of zero the player is in the Rest phase awaiting a Rest action. Begin Rest, when the player has no usable REST power, finishes Rest in the same command. Default: Murky Fountain does not, per the ruling's "`EnterPhase(Rest)`, without the Begin Rest validation gate". If it should, that is an engine change (a `runsAutoFinishRest` rule beyond `BeginRest`), so it would need its own plan.
3. **Fae Merchant's taken relic is facedown.** The design lists this as assumed. Dowsing Sticks' text confirms facedown ("You may keep it facedown"). Default: facedown for both. A faceup Fae Merchant relic changes only `RelicDraws.takeTop` for that power.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`: the shared draw.
- Create `src/main/scala/oathdigital/gameplay/powers/action/RollResults.scala`: reads a roll's score and skulls.
- Create `.../action/GamblingHall.scala`, `BoneDice.scala`, `MurkyFountain.scala`, `DowsingSticks.scala`, `FaeMerchant.scala`.
- Create `.../action/DiceAndRelicDrawPowers.scala`: the slice's registry group.
- Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`: one import and one line.
- Create test support `src/test/scala/oathdigital/gameplay/powers/action/PaidActionHarness.scala`.
- Create one suite per power under `src/test/scala/oathdigital/gameplay/powers/action/`, and `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`.
- Modify the design and rulings docs (Task 6).

---

### Task 1: The dice kit and Gambling Hall

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/RollResults.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/PaidActionHarness.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/GamblingHallSuite.scala`
- Test: `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes slice 1a's `PaidAction(idValue, cost)` and `PowerFixture` (`base`, `actor`, `player`, `home`, `withBoard`, `inPhase`, `atHome`, `atSite`), and `CampaignFixture.dice`.
- Produces `private[action] RollResults.score(ready, pool): Int` and `RollResults.skulls(ready, pool): Int`. Later tasks use both.
- Produces `DiceAndRelicDrawPowers.forCatalog(catalog): Vector[PhasePower]`. Tasks 2 to 5 add their power to its vector.
- Produces `GamblingHall.id`, `.pool`, `.decisionId = "gambling-hall.bank"`.
- Produces the test support `PaidActionHarness`: `rules(dice)`, `defenseDice(faces*)`, `attackDice(faces*)`, `act`, `use`, `answer`, `ready`, `usableIds`, `replayed`, `wireRoundTrips`, `tokensOn`, `secrets`. Every later suite uses it.

Ruling: cost 2 favor placed. Roll 4 defense dice. When the total X is above zero, choose any favor bank, even an empty one, and take min(X, its stock).

- [ ] **Step 1: Write the test support**

`src/test/scala/oathdigital/gameplay/powers/action/PaidActionHarness.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.model.OathState.Ready

/** Drives an Act phase power through the rules, the way `MagicWaterskinSuite`
  * does, with fixed dice and a journal replay check. Shared by the slice 1b
  * suites.
  */
object PaidActionHarness {
  import PowerFixture._

  /** Rules with every production phase power. Dice fail loudly unless given,
    * so a power that must not roll proves it by not asking.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable): OathRules =
    new OathRules(catalog,
      phasePowerCatalog = PhasePowerCatalog.default(catalog),
      walkerDice = dice)

  def defenseDice(faces: DefenseDieFace*): WalkerDice =
    CampaignFixture.dice(defense = faces.toVector)

  def attackDice(faces: AttackDieFace*): WalkerDice =
    CampaignFixture.dice(attack = faces.toVector)

  def act(ready: ReadyGame): ReadyGame = inPhase(ready, Phase.Act)

  def use(rules: OathRules, ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.UsePower(id), actor,
      Vector.empty, Vector(source))

  def answer(rules: OathRules, state: OathState, decisionId: String,
      ref: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  def ready(state: OathState): ReadyGame = state.asInstanceOf[Ready].value

  /** Ids of the phase powers the actor can use now. */
  def usableIds(ready: ReadyGame): Vector[PowerId] =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).map(_.power.id)

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(rules: OathRules, from: ReadyGame, events: Vector[OathEvent])
      : ReadyGame = ready(events.foldLeft[Either[OathViolation, OathState]](
    Right(Ready(from))) {
    case (state, event) => state.flatMap(rules.evolve(_, event))
  }.toOption.get)

  /** Every event survives the journal wire: encoded, decoded and equal. */
  def wireRoundTrips(events: Vector[OathEvent]): Boolean =
    events.zipWithIndex.forall { case (event, index) =>
      GameEventWire.encodeEvent("g", catalog.ref, index.toLong, event).toOption
        .flatMap(GameEventWire.decode(_).toOption).map(_.event)
        .contains(event)
    }

  /** The tokens on card `id` wherever it sits. */
  def tokensOn(state: ReadyGame, id: CardId): Tokens = {
    val current = state.game.current
    val onSites = current.map.sites.values.flatMap(_.denizens.collect {
      case card: DenizenState if card.id == id => card.tokens
      case card: EdificeState if card.id == id => card.tokens
    })
    val held = current.players.flatMap(_.relics.collect {
      case relic if relic.id == id => relic.tokens
    })
    (onSites ++ held).head
  }

  /** The actor's secrets, faceup and facedown together. */
  def secrets(state: ReadyGame): Int =
    player(state).board.faceUpSecrets + player(state).board.faceDownSecrets
}
```

- [ ] **Step 2: Write the failing Gambling Hall suite**

`src/test/scala/oathdigital/gameplay/powers/action/GamblingHallSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GamblingHallSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val hall = DenizenId("93")
  private val source = DecisionOptionRef.Denizen(hall)
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def staged(favor: Int = 3) =
    act(withBoard(atHome(base, hall))(_.copy(favor = favor)))

  private val total4 = defenseDice(DefenseDieFace.OneShield,
    DefenseDieFace.OneShield, DefenseDieFace.TwoShields, DefenseDieFace.Blank)

  test("Gambling Hall is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(GamblingHall.id).isDefined)
  }

  test("it places 2 favor, rolls 4 dice and takes the total from the chosen bank") {
    val rules0 = rules(total4)
    val ready0 = staged()
    val parked = use(rules0, ready0, GamblingHall.id, source).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingPowerDecision(actor,
      DecisionId(GamblingHall.decisionId)))
    val paid = ready(parked.state)
    assertEquals(player(paid).board.favor, 1)
    assertEquals(tokensOn(paid, hall), Tokens(2, 0))
    assertEquals(paid.game.current.rollOutcomes(GamblingHall.pool).score, 4)

    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.favor, 5)
    assertEquals(end.banks.favor(Suit.Beast),
      ready0.banks.favor(Suit.Beast) - 4)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(end.game.current.rollPools, Map.empty[PoolKey, DicePoolState])
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assert(wireRoundTrips(parked.events ++ done.events))
  }

  test("a Doubler multiplies the total, and a bank with less than X gives what it has") {
    val doubled = defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)
    val rules0 = rules(doubled)
    val ready0 = staged()
    val parked = use(rules0, ready0, GamblingHall.id, source).toOption.get
    assertEquals(ready(parked.state).game.current.rollOutcomes(
      GamblingHall.pool).score, 6)
    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Nomad)).toOption.get
    assertEquals(ready(done.state).banks.favor(Suit.Nomad), 0)
    assertEquals(player(ready(done.state)).board.favor,
      1 + ready0.banks.favor(Suit.Nomad))
  }

  test("an empty bank may be chosen and gives nothing") {
    val drained = staged().copy(banks = staged().banks.copy(
      favor = staged().banks.favor.updated(Suit.Discord, 0)))
    val rules0 = rules(total4)
    val parked = use(rules0, drained, GamblingHall.id, source).toOption.get
    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Discord)).toOption.get
    assertEquals(player(ready(done.state)).board.favor, 1)
    assertEquals(ready(done.state).banks.favor(Suit.Discord), 0)
  }

  test("a total of zero asks nothing and takes nothing") {
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.Blank, DefenseDieFace.Blank))
    val done = use(rules0, staged(), GamblingHall.id, source).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(player(ready(done.state)).board.favor, 1)
    assertEquals(tokensOn(ready(done.state), hall), Tokens(2, 0))
  }

  test("a bank the decision does not offer is rejected") {
    val rules0 = rules(total4)
    val parked = use(rules0, staged(), GamblingHall.id, source).toOption.get
    assert(answer(rules0, parked.state, GamblingHall.decisionId,
      DecisionOptionRef.Button("elsewhere")).isLeft)
  }

  test("it is unusable without 2 favor, and again while its card holds favor") {
    assert(!usableIds(staged(favor = 1)).contains(GamblingHall.id))
    assert(use(rules(total4), staged(favor = 1), GamblingHall.id, source).isLeft)
    val parked = use(rules(total4), staged(), GamblingHall.id, source).toOption.get
    val done = answer(rules(total4), parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)).toOption.get
    val again = ready(done.state)
    assert(!usableIds(again).contains(GamblingHall.id))
    assert(use(rules(total4), again, GamblingHall.id, source).isLeft)
  }

  test("a Gambling Hall at a site the actor rules is usable from another site") {
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != home(base)).get
    val kind = oathdigital.gameplay.powers.PlayerFacts.forceKind(base, actor)
      .toOption.get
    val ready0 = act(withBoard(atSite(base, hall, far).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1)))))))(
      _.copy(favor = 3)))
    assert(usableIds(ready0).contains(GamblingHall.id))
  }
}
```

- [ ] **Step 3: Write the failing projection suite**

`src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`. Task 5 adds a second test to it.

```scala
package oathdigital.application

import oathdigital.gameplay.powers.PowerFixture._
import oathdigital.gameplay.powers.action.{GamblingHall, PaidActionHarness}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The parked decisions of the slice 1b powers reach their owner with every
  * option presented, and reach nobody else. A projection is dropped whole when
  * one option cannot be presented.
  */
class DicePowerDecisionProjectionSuite extends munit.FunSuite {
  import PaidActionHarness._

  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))

  private def owner(state: ReadyGame) =
    projector.project(ScopedProjectionContext(state, Some(actor)))
  private def other(state: ReadyGame) = projector.project(
    ScopedProjectionContext(state, Some(state.game.current.players.map(_.player)
      .find(_ != actor).get)))

  test("Gambling Hall offers the owner all six favor banks") {
    val hall = DenizenId("93")
    val ready0 = act(withBoard(atHome(base, hall))(_.copy(favor = 3)))
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield, DefenseDieFace.TwoShields,
      DefenseDieFace.Blank))
    val parked = ready(use(rules0, ready0, GamblingHall.id,
      DecisionOptionRef.Denizen(hall)).toOption.get.state)
    val projection = owner(parked).get
    assertEquals(projection.decisionId, GamblingHall.decisionId)
    assertEquals(projection.query.get.options.map(_.kind).distinct,
      Vector("favor-bank"))
    assertEquals(projection.query.get.options.size, Suit.all.size)
    assertEquals(other(parked), None)
  }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.GamblingHallSuite oathdigital.application.DicePowerDecisionProjectionSuite"`
Expected: FAIL to compile, `not found: value GamblingHall`.

- [ ] **Step 5: Write `RollResults` and Gambling Hall**

`src/main/scala/oathdigital/gameplay/powers/action/RollResults.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model.{PoolKey, ReadyGame}

/** Reads the outcome an automatic `Roll` wrote into state. A pool that never
  * rolled reads as zero, so a `BuildOps` that runs after a skipped roll needs
  * no case of its own.
  */
private[action] object RollResults {
  /** Shields for a defense roll, swords for an attack roll, scored by
    * `DefenseDieFace.score` and `AttackDieFace.score` when the roll was
    * recorded.
    */
  def score(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.score)

  /** Skull faces rolled in an attack pool. */
  def skulls(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.skulls)
}
```

`src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Gambling Hall (card 93), ACTION: place 2 favor on this card, roll 4
  * defense dice, and when the total X is above zero choose any favor bank
  * (even an empty one) and take the smaller of X and its stock.
  *
  * The tree is `ModifyDicePool`, an automatic `Roll`, a `Branch` that holds
  * only the bank decision, and a `BuildOps` that reads the answer. The
  * `Branch` is the walker's "live decision" shape: it sits after the roll that
  * wrote the state it reads, so it selects the same decision again when the
  * walker resumes against the state stored at the park. `Gain.Favor` is
  * optional, so it reduces to what the bank holds.
  */
case object GamblingHall extends PaidAction("denizen.gambling-hall",
    Cost(favor = 2)) {
  val Dice: Int = 4
  val pool: PoolKey = PoolKey("gambling-hall")
  val decisionId: String = "gambling-hall.bank"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    ModifyDicePool(pool, Dice),
    Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
    Branch((state, _) =>
      if (RollResults.score(state, pool) > 0) Vector(choice(player))
      else Vector.empty),
    BuildOps((state, pending) => take(state, player, pending)))))

  private def choice(player: PlayerId): Decide = Decide(decisionId, player,
    DecisionQuery.ChooseOne(Suit.all.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some("Gambling Hall: take favor from a bank")))

  private def take(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val total = RollResults.score(state, pool)
    if (total <= 0) Right(Vector.empty)
    else pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(suit)), _) => suit
    }.toRight(OathViolation.InvalidEventOrder(
      "no Gambling Hall bank is recorded")).map(suit =>
      Vector(Gain.Favor(player, suit, total)))
  }
}
```

- [ ] **Step 6: Register the slice's group**

`src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of slice 1b, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object so that later slices add their own group without
  * editing the same lines.
  */
object DiceAndRelicDrawPowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](GamblingHall)
}
```

`src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`: extend the import and the vector (the two changed lines only):

```scala
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, WaysideInn}
```
```scala
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains) ++
      DiceAndRelicDrawPowers.forCatalog(catalog))
```

- [ ] **Step 7: Run the suites, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.GamblingHallSuite oathdigital.application.DicePowerDecisionProjectionSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: all pass. If a scripted game in `GameApplicationServiceSuite` now offers a new usable power and parks, see "What planning found" item 9: card 93 is not dealt in the first game, so it should not.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Gambling Hall

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Bone Dice

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BoneDice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BoneDiceSuite.scala`

**Interfaces:**
- Consumes `RollResults.score`, `RollResults.skulls`, `PaidActionHarness.attackDice`, `PlayerFacts.player`.
- Produces `BoneDice.id`, `BoneDice.pool`.

Ruling: cost 1 secret placed. Roll 2 attack dice. Gain Supply equal to the sword score. If any skull face rolled, bury this relic afterwards with the standard returns, so the secret just placed returns to the player facedown.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/BoneDiceSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BoneDiceSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val bones = RelicId("R24")
  private val source = DecisionOptionRef.Relic(bones)
  private def staged(supply: Int = 2, orientation: Orientation = Orientation.FaceUp) =
    act(withBoard(withRelic(base, bones, orientation))(
      _.copy(supply = SupplyTrack(supply))))
  private def held(state: ReadyGame) = player(state).relics.map(_.id)

  test("Bone Dice is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(BoneDice.id).isDefined)
  }

  test("no skull: the relic stays, holding the secret, and Supply rises by the swords") {
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.HollowSword))
    val ready0 = staged()
    val done = use(rules0, ready0, BoneDice.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))
    assert(held(end).contains(bones))
    assertEquals(tokensOn(end, bones), Tokens(0, 1))
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("two hollow swords score one sword") {
    val rules0 = rules(attackDice(AttackDieFace.HollowSword, AttackDieFace.HollowSword))
    val end = ready(use(rules0, staged(), BoneDice.id, source).toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))
  }

  test("a skull counts two swords, and buries the relic with the secret returned facedown") {
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull))
    val ready0 = staged()
    val done = use(rules0, ready0, BoneDice.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(5))
    assert(!held(end).contains(bones))
    assertEquals(end.game.current.commonCards.relicDeck.last, bones)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready0).board.faceDownSecrets + 1)
    assertEquals(player(end).board.faceUpSecrets,
      player(ready0).board.faceUpSecrets - 1)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("the gain is clamped at the track maximum, and a skull still buries") {
    val rules0 = rules(attackDice(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.TwoSwordsSkull))
    val end = ready(use(rules0, staged(supply = 6), BoneDice.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(7))
    assert(!held(end).contains(bones))
  }

  test("it is unusable without a faceup secret, with a secret already on the relic, or facedown") {
    val noSecret = withBoard(staged())(_.copy(faceUpSecrets = 0))
    assert(!usableIds(noSecret).contains(BoneDice.id))
    assert(use(rules(), noSecret, BoneDice.id, source).isLeft)
    val occupied = staged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else p.copy(relics = p.relics.map(r =>
        r.copy(tokens = Tokens(0, 1)))))))
    assert(!usableIds(occupied).contains(BoneDice.id))
    val facedown = staged(orientation = Orientation.FaceDown)
    assert(!usableIds(facedown).contains(BoneDice.id))
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BoneDiceSuite"`
Expected: FAIL to compile, `not found: value BoneDice`.

- [ ] **Step 3: Write Bone Dice**

`src/main/scala/oathdigital/gameplay/powers/action/BoneDice.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Bone Dice (relic R24), ACTION: place 1 secret on this relic, roll 2 attack
  * dice, gain Supply equal to the sword score, then bury this relic if any
  * skull face rolled.
  *
  * The bury uses the standard returns, so the secret the cost just placed on
  * the relic goes back to its holder facedown. The tokens are read inside the
  * `BuildOps`, after the engine has paid the cost, and not in `build`, which
  * runs before it.
  */
case object BoneDice extends PaidAction("relic.bone-dice", Cost(secret = 1)) {
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("bone-dice")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(id) => Right(Sequence(Vector(
      ModifyDicePool(pool, Dice),
      Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic),
      BuildOps((state, _) => settle(state, player, id)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def settle(state: ReadyGame, player: PlayerId, id: RelicId)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    held <- PlayerFacts.player(state, player)
    relic <- held.relics.find(_.id == id).toRight(
      OathViolation.InvalidEventOrder(
        s"${id.value} is not held by ${player.value}"))
  } yield {
    val supply = RollResults.score(state, pool)
    val gain: Vector[CoreOperation] =
      if (supply > 0) Vector(GainSupply(player, supply)) else Vector.empty
    val bury: Vector[CoreOperation] =
      if (RollResults.skulls(state, pool) > 0)
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0,
          relic.tokens.secrets, player)
      else Vector.empty
    gain ++ bury
  }
}
```

- [ ] **Step 4: Register it**

In `DiceAndRelicDrawPowers.scala` change the vector to:

```scala
    Vector[PhasePower](GamblingHall, BoneDice)
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BoneDiceSuite"`
Expected: PASS. If the skull test shows the secret still on the relic, the `BuildOps` ran before the cost: re-read "What planning found" item 5 before changing the power.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Bone Dice

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Murky Fountain

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MurkyFountainSuite.scala`

**Interfaces:**
- Consumes `PowerAccess.siteOf`, `PowerAccess.pawnSite` (`private[gameplay]`, reachable from `gameplay.powers`), `RollResults.score`, `PaidActionHarness.defenseDice`, `PowerFixture.withEdifice`.
- Produces `MurkyFountain.id = PowerId("edifice.e15.ruined")`, `MurkyFountain.pool`.

Ruling: cost 1 secret placed on the edifice card. If your pawn is at this site, roll 2 defense dice and gain Supply equal to the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`, without the Begin Rest validation gate. If your pawn is elsewhere, the cost is paid and nothing else happens.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/MurkyFountainSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PlayerFacts, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class MurkyFountainSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val fountain = EdificeId("E15")
  private val source = DecisionOptionRef.Edifice(fountain)
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The edifice is at the pawn's site, or at a far site the actor rules, so
    * that in the second case only the pawn condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Ruined,
      pawnAtEdifice: Boolean = true, supply: Int = 1): ReadyGame = {
    val site = home(base)
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != site).get
    val placed = withEdifice(base, fountain, side,
      if (pawnAtEdifice) site else far)
    val ruled = if (pawnAtEdifice) placed else placed.updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1))))))
    act(withBoard(ruled)(_.copy(supply = SupplyTrack(supply))))
  }

  test("Murky Fountain is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MurkyFountain.id).isDefined)
  }

  test("at its site it places a secret and gains Supply equal to the total") {
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields))
    val ready0 = staged()
    val done = use(rules0, ready0, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(4))
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    assertEquals(end.game.current.turn.phase, Phase.Act)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("a Doubler doubles the shields") {
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler))
    val end = ready(use(rules0, staged(), MurkyFountain.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(5))
  }

  test("the gain is clamped at the track maximum") {
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.TwoShields))
    val end = ready(use(rules0, staged(supply = 5), MurkyFountain.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(7))
  }

  test("a total of zero ends the Act phase without gaining Supply") {
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank))
    val ready0 = staged()
    val done = use(rules0, ready0, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(end.game.current.turn.phase, Phase.Rest)
    assertEquals(done.continue, OathContinue.AwaitingRestAction(actor))
    assertEquals(player(end).board.supply, SupplyTrack(1))
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("with the pawn elsewhere the cost is paid, nothing rolls and the phase stays") {
    val away = staged(pawnAtEdifice = false)
    assert(usableIds(away).contains(MurkyFountain.id))
    val done = use(rules(), away, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    assertEquals(secrets(end), secrets(away) - 1)
    assertEquals(player(end).board.supply, SupplyTrack(1))
    assertEquals(end.game.current.turn.phase, Phase.Act)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
  }

  test("it is unusable without a faceup secret, or with a secret already on the card") {
    val noSecret = withBoard(staged())(_.copy(faceUpSecrets = 0))
    assert(!usableIds(noSecret).contains(MurkyFountain.id))
    assert(use(rules(), noSecret, MurkyFountain.id, source).isLeft)
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield))
    val first = ready(use(rules0, withBoard(staged())(_.copy(faceUpSecrets = 2)),
      MurkyFountain.id, source).toOption.get.state)
    assert(!usableIds(first).contains(MurkyFountain.id))
  }

  test("the intact face is Marble Fountains and offers no Murky Fountain") {
    val intact = staged(side = EdificeSide.Intact)
    assert(!usableIds(intact).contains(MurkyFountain.id))
    assert(use(rules(), intact, MurkyFountain.id, source).isLeft)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MurkyFountainSuite"`
Expected: FAIL to compile, `not found: value MurkyFountain`.

- [ ] **Step 3: Write Murky Fountain**

`src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Murky Fountain (edifice E15, ruined), ACTION: place 1 secret on this card.
  * If your pawn is at this site, roll 2 defense dice and gain Supply equal to
  * the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`,
  * which is what Begin Rest does once its validation gate has passed. If your
  * pawn is elsewhere the cost is paid and nothing else happens, and the dice
  * are never asked for.
  *
  * The ruined face carries its own power id, so an intact Marble Fountains
  * never offers this power. The pawn test is in `build`, which is safe because
  * a pawn cannot move between the command that starts the power and the end of
  * a tree that never parks.
  */
case object MurkyFountain extends PaidAction("edifice.e15.ruined",
    Cost(secret = 1)) {
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("murky-fountain")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Edifice(id) =>
      if (!atPawnSite(ready, player, id)) Right(Sequence(Vector.empty))
      else Right(Sequence(Vector(
        ModifyDicePool(pool, Dice),
        Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
        BuildOps((state, _) => Right(outcome(state, player))))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not an edifice source"))
  }

  private def atPawnSite(ready: ReadyGame, player: PlayerId, id: EdificeId)
      : Boolean = PowerAccess.siteOf(ready, player, id)
    .exists(PowerAccess.pawnSite(ready, player).contains)

  private def outcome(state: ReadyGame, player: PlayerId)
      : Vector[CoreOperation] = {
    val total = RollResults.score(state, pool)
    if (total > 0) Vector(GainSupply(player, total))
    else Vector(EnterPhase(Phase.Rest))
  }
}
```

- [ ] **Step 4: Register it**

In `DiceAndRelicDrawPowers.scala` change the vector to:

```scala
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain)
```

- [ ] **Step 5: Run the suite, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MurkyFountainSuite"` then `./sbtw test`
Expected: PASS. If the pawn-elsewhere test fails on `Sequence(Vector.empty)`, replace it with `BuildOps((_, _) => Right(Vector.empty))` and record the finding. If the zero-total test ends in `ActActionSelection`, the phase change did not run: read `walkerTransition` (item 7) before changing the power.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Murky Fountain

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: The relic draw and Dowsing Sticks

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/DowsingSticksSuite.scala`

**Interfaces:**
- Produces `RelicDraws.takeTop(ready, actor): Vector[CoreOperation]`, empty when the relic deck is empty. Task 5 uses it. Slice 1a's Family Heirloom keeps its own draw, because it must fail inside a guarded `Repeat`.
- Produces `DowsingSticks.id`.

Ruling: cost 1 secret placed and 2 secrets burnt. Draw a relic from the relic deck and take it facedown. An empty deck does nothing.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/DowsingSticksSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DowsingSticksSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val sticks = RelicId("R09")
  private val source = DecisionOptionRef.Relic(sticks)
  private def staged(secrets: Int = 3) = act(withBoard(withRelic(base, sticks))(
    _.copy(faceUpSecrets = secrets)))

  test("Dowsing Sticks is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(DowsingSticks.id).isDefined)
  }

  test("it places 1 secret, burns 2 and takes the top relic facedown") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val done = use(rules0, ready0, DowsingSticks.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).relics.find(_.id == top).map(_.orientation),
      Some(Orientation.FaceDown))
    assert(!end.game.current.commonCards.relicDeck.contains(top))
    assertEquals(tokensOn(end, sticks), Tokens(0, 1))
    assertEquals(player(end).board.faceUpSecrets, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("an empty relic deck pays the cost and draws nothing") {
    val ready0 = staged()
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = use(rules(), emptied, DowsingSticks.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).relics.map(_.id), Vector(sticks))
    assertEquals(tokensOn(end, sticks), Tokens(0, 1))
    assertEquals(player(end).board.faceUpSecrets, 0)
  }

  test("it is unusable with fewer than 3 faceup secrets") {
    assert(!usableIds(staged(secrets = 2)).contains(DowsingSticks.id))
    assert(use(rules(), staged(secrets = 2), DowsingSticks.id, source).isLeft)
  }

  test("it is unusable while a secret already rests on the relic") {
    val done = use(rules(), staged(secrets = 6), DowsingSticks.id, source)
      .toOption.get
    assert(!usableIds(ready(done.state)).contains(DowsingSticks.id))
  }

  test("a facedown Dowsing Sticks cannot be used") {
    val facedown = act(withBoard(withRelic(base, sticks, Orientation.FaceDown))(
      _.copy(faceUpSecrets = 3)))
    assert(!usableIds(facedown).contains(DowsingSticks.id))
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DowsingSticksSuite"`
Expected: FAIL to compile, `not found: value DowsingSticks`.

- [ ] **Step 3: Write the draw and Dowsing Sticks**

`src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** Relic draws that powers share. */
object RelicDraws {
  /** Draws the top relic of the relic deck and takes it facedown into the
    * player's play area. An empty relic deck draws nothing.
    */
  def takeTop(ready: ReadyGame, actor: PlayerId): Vector[CoreOperation] =
    ready.game.current.commonCards.relicDeck.headOption.toVector.map(relic =>
      Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown))
}
```

`src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Dowsing Sticks (relic R09), ACTION: place 1 secret on this relic and burn
  * 2 secrets, then draw a relic and take it facedown. An empty relic deck
  * pays the cost and does nothing else.
  *
  * The draw sits in a `BuildOps` because whether the deck has a top card is a
  * fact of the state when the draw runs, not when the tree was built.
  */
case object DowsingSticks extends PaidAction("relic.dowsing-sticks",
    Cost(secret = 1, secretBurnt = 2)) {
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))))
}
```

- [ ] **Step 4: Register it**

In `DiceAndRelicDrawPowers.scala` change the vector to:

```scala
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain, DowsingSticks)
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DowsingSticksSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Dowsing Sticks

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Fae Merchant

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala`
- Test: `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes `RelicDraws.takeTop`, `PlayerFacts.player`, `Bury.standard`, and `RelicRole.GrandScepter` from the catalog.
- Produces `FaeMerchant.forCatalog(catalog): FaeMerchant` (a `PhasePower` value, always present), `FaeMerchant.id`, `FaeMerchant.decisionId = "fae-merchant.relic"`.

Ruling: cost 1 secret placed. Draw a relic and take it (facedown, see open question 3). Then put exactly one relic you hold, except the Grand Scepter, on the bottom of the relic deck. The just-taken relic is eligible. A decision is asked only when there is more than one candidate. Open question 1 covers an empty deck.

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class FaeMerchantSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val fae = DenizenId("180")
  private val source = DecisionOptionRef.Denizen(fae)
  private val scepter = RelicId("grand-scepter")
  private val held1 = RelicId("R08")
  private val held2 = RelicId("R01")
  private def relicRef(id: RelicId) = DecisionOptionRef.Relic(id)
  private def staged(held: Vector[RelicId] = Vector(held1), secrets: Int = 2) = {
    val ready0 = held.foldLeft(atHome(base, fae))((r, id) => withRelic(r, id))
    act(withBoard(ready0)(_.copy(faceUpSecrets = secrets)))
  }
  private def relicIds(state: ReadyGame) = player(state).relics.map(_.id)

  test("Fae Merchant is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(FaeMerchant.id).isDefined)
  }

  test("it draws a relic, then asks which relic to put on the bottom") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingPowerDecision(actor,
      DecisionId(FaeMerchant.decisionId)))
    val mid = ready(parked.state)
    assertEquals(relicIds(mid), Vector(held1, top))
    assertEquals(player(mid).relics.last.orientation, Orientation.FaceDown)
    assertEquals(tokensOn(mid, fae), Tokens(0, 1))

    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(top))
    assertEquals(end.game.current.commonCards.relicDeck.last, held1)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assert(wireRoundTrips(parked.events ++ done.events))
  }

  test("the relic just taken may be the one put back") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get.state)
    assertEquals(relicIds(end), Vector(held1))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("the Grand Scepter is never offered, and cannot be chosen") {
    val ready0 = staged(Vector(scepter, held1))
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assert(relicIds(end).contains(scepter))
  }

  test("with only the Grand Scepter held, the drawn relic is the one relic eligible and goes straight back") {
    val ready0 = staged(Vector(scepter))
    val top = ready0.game.current.commonCards.relicDeck.head
    val done = use(rules(), ready0, FaeMerchant.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("with no other relic the drawn one is the only candidate and no decision is asked") {
    val ready0 = staged(Vector.empty)
    val top = ready0.game.current.commonCards.relicDeck.head
    val done = use(rules(), ready0, FaeMerchant.id, source).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("a secret on the relic put back returns to its holder facedown") {
    val ready0 = staged(Vector(held1)).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player != actor) p else p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(0, 1)))))))
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready0).board.faceDownSecrets + 1)
  }

  test("an empty relic deck still puts one held relic on the bottom") {
    val ready0 = staged(Vector(held1))
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = use(rules(), emptied, FaeMerchant.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck, Vector(held1))
  }

  test("it is unusable without a faceup secret or with a secret already on the card") {
    assert(!usableIds(staged(secrets = 0)).contains(FaeMerchant.id))
    val rules0 = rules()
    val parked = use(rules0, staged(Vector(held1, held2), secrets = 3),
      FaeMerchant.id, source).toOption.get
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    assert(!usableIds(ready(done.state)).contains(FaeMerchant.id))
  }
}
```

- [ ] **Step 2: Add the projection test**

In `DicePowerDecisionProjectionSuite.scala` change the imports to:

```scala
import oathdigital.gameplay.powers.PowerFixture._
import oathdigital.gameplay.powers.action.{FaeMerchant, GamblingHall,
  PaidActionHarness}
```

and add this test after the Gambling Hall one:

```scala
  test("Fae Merchant names both eligible relics to its owner, including the facedown one just taken") {
    val fae = DenizenId("180")
    val held = RelicId("R08")
    val ready0 = act(withBoard(withRelic(atHome(base, fae), held))(
      _.copy(faceUpSecrets = 2)))
    val top = ready0.game.current.commonCards.relicDeck.head
    val parked = ready(use(rules(), ready0, FaeMerchant.id,
      DecisionOptionRef.Denizen(fae)).toOption.get.state)
    val projection = owner(parked).get
    assertEquals(projection.decisionId, FaeMerchant.decisionId)
    assertEquals(projection.query.get.options.map(_.id), Vector(held.value,
      top.value))
    assert(projection.query.get.options.forall(_.card.exists(!_.hidden)))
    assertEquals(other(parked), None)
  }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite"`
Expected: FAIL to compile, `not found: value FaeMerchant`.

- [ ] **Step 4: Write Fae Merchant**

`src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.gameplay.powers.{PlayerFacts, RelicDraws}
import oathdigital.model._

/** Fae Merchant (card 180), ACTION: place 1 secret on this card, draw a relic
  * and take it facedown, then put exactly one relic you hold, except the
  * Grand Scepter, on the bottom of the relic deck. The relic just taken is
  * eligible. The choice is asked only when more than one relic is eligible.
  *
  * Three siblings run in order: the draw, a live `Branch` that holds only the
  * decision and reads the relics after the draw, and the bury. The bury reads
  * the eligible relics again, so it needs the recorded answer only when the
  * decision was asked. It returns any secrets on the relic to their holder.
  */
final case class FaeMerchant private (scepters: Set[RelicId])
    extends PaidAction("denizen.fae-merchant", Cost(secret = 1)) {
  import FaeMerchant._

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Branch((state, _) => candidates(state, player) match {
      case several if several.size > 1 => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(several.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck"))))
      case _ => Vector.empty
    }),
    BuildOps((state, pending) => putBack(state, player, pending)))))

  /** The relics the player holds, in play-area order, that may go back. */
  private def candidates(state: ReadyGame, player: PlayerId): Vector[RelicId] =
    PlayerFacts.player(state, player).toOption.toVector.flatMap(_.relics)
      .map(_.id).filterNot(scepters)

  private def putBack(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val chosen: Either[OathViolation, Option[RelicId]] =
      candidates(state, player) match {
        case Vector() => Right(None)
        case Vector(only) => Right(Some(only))
        case _ => pending.answered.collectFirst {
          case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
              DecisionOptionRef.Relic(id)), _) => id
        }.toRight(OathViolation.InvalidEventOrder(
          "no Fae Merchant relic is recorded")).map(Some(_))
      }
    for {
      pick <- chosen
      held <- PlayerFacts.player(state, player)
    } yield pick.toVector.flatMap { id =>
      val secrets = held.relics.find(_.id == id).fold(0)(_.tokens.secrets)
      Bury.standard(BuryableCard.Relic(id),
        PositionedLocation(Location.PlayArea(player)), None, 0, secrets,
        player)
    }
  }
}

object FaeMerchant {
  val decisionId: String = "fae-merchant.relic"
  val id: PowerId = PowerId("denizen.fae-merchant")

  /** The Grand Scepter is read from the catalog's relic roles, not by name. */
  def forCatalog(catalog: ExecutableCatalog): FaeMerchant = new FaeMerchant(
    catalog.relics.filter(_.role == RelicRole.GrandScepter)
      .map(relic => RelicId(relic.id.value)).toSet)
}
```

- [ ] **Step 5: Register it**

In `DiceAndRelicDrawPowers.scala` change the vector to its final form:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of slice 1b, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object so that later slices add their own group without
  * editing the same lines.
  */
object DiceAndRelicDrawPowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain, DowsingSticks,
      FaeMerchant.forCatalog(catalog))
}
```

- [ ] **Step 6: Run the suites, then the full suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite"` then `./sbtw test`
Expected: PASS. If the projection test returns `None` for the owner, an option could not be presented: read `WalkerDecisionProjector.card` and `GamePresentationProjector.identifiesCard` (item 10) and report rather than changing the projector.

- [ ] **Step 7: Commit**

```bash
git add src/main src/test
git commit -m "feat: implement Fae Merchant

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Gates and documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-20-powers-design.md`
- Modify: `docs/superpowers/specs/2026-09-20-powers-rulings.md`

- [ ] **Step 1: Run every gate**

Run: `./sbtw test`, then `python3 scripts/check-architecture.py`, then `python3 scripts/check-markdown-links.py`.
Expected: all pass. Confirm no production file exceeds 800 lines: the largest new file is `FaeMerchant.scala` at about 70 lines.

- [ ] **Step 2: Update the design**

In `2026-09-20-powers-design.md`, touch only these places:
- The status line: add slice 1b to "are implemented" and link its plan beside the slice 1a link.
- The paragraph under the Slicing table: add "Slice 1b is planned: see its [plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md)." after the slice 1a sentence.
- "Walker shapes for powers": add a third shape, worded:

  > - **Roll, then a live decision.** `ModifyDicePool`, an `Automatic` `Roll`, then a `Branch` that returns only a `Decide` and reads `rollOutcomes`. Recover's continue-or-stop decision uses it, and so do Gambling Hall's bank and Fae Merchant's relic (the latter after a draw instead of a roll). A phase power must use an `Automatic` roll, because a `UsePower` entry declares no roll decision id and a parked `Roll` would have no continuation.

- "Verify at plan time": if open question 3 was answered, strike or reword the last bullet (Fae Merchant's relic is facedown).

- [ ] **Step 3: Record results in the rulings appendix**

In `2026-09-20-powers-rulings.md`, append ` Implemented (slice 1b).` to the five rows: 93 Gambling Hall, R24 Bone Dice, E15 Murky Fountain, R09 Dowsing Sticks and 180 Fae Merchant. Then add a list under "Slice 1a implementation notes" titled "Slice 1b implementation notes" with these points, adjusted to the answers to the open questions:
- Each power is a `PaidAction` in `gameplay/powers/action`, registered through `DiceAndRelicDrawPowers`.
- Rolls are automatic. Pool keys are one per power. Gambling Hall's bank choice is a live `Branch` after the roll.
- Bone Dice reads the relic's tokens after the engine pays the cost, so the placed secret returns facedown on the bury.
- Murky Fountain's zero total emits `EnterPhase(Rest)` and leaves the player awaiting a Rest action. It does not auto-finish Rest (open question 2).
- Fae Merchant's put-back is independent of the draw (open question 1), reads eligibility after the draw, and asks only for more than one candidate. The Grand Scepter is read from the catalog's relic role.
- Dowsing Sticks needs three faceup secrets: one placed, two burnt.
- Test staging: card 93 is not dealt in the first game and is added by the fixture, card 180 is in the world deck and is removed, E15 is placed from the edifice deck, and R09 and R24 are taken from the relic deck.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: record slice 1b

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

## Self-review

- **Spec coverage.** The rulings for Gambling Hall, Bone Dice, Murky Fountain, Dowsing Sticks and Fae Merchant map to Tasks 1 to 5, one task per power, with the shared kit in Tasks 1 and 4. The design's slice 1b row says "none expected" for engine changes, and none was needed. Task 6 covers the docs.
- **Placeholders.** None. Every step names its files and carries the code, and every changed line in a shared file is shown.
- **Types.** `PaidActionHarness` members are defined in Task 1 and used unchanged in Tasks 2 to 5. `RollResults` is defined in Task 1 and used by Tasks 2 and 3. `RelicDraws.takeTop` is defined in Task 4 and used in Task 5. `DiceAndRelicDrawPowers` grows one entry per task and its final form is shown in Task 5.
