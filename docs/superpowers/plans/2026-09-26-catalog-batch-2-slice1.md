# Catalog Batch 2, Slice 1 (Modifiers and Restrictions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Animal Playmates (40), Birdsong (176), Royal Stables (245) and Forgotten Vault (75), with the Supply reduction the spec calls N1.

**Architecture:**

- Animal Playmates and Birdsong are selected modifiers with the Cup of Plenty's shape. Task 1 moves that shape into a shared `SupplyWaiver` helper, rewrites the Cup of Plenty on it, then adds the two cards.
- Royal Stables is a selected Travel modifier that lowers the Travel's `SpendSupply` by 1, never below 1. It must fold after terrain. Every power sorts at priority 0 today, and a selected modifier's source key (`game:…`) sorts before a site's (`site:…`), so Royal Stables raises its own `priority` to fold last. It is the first power to override `priority`.
- Forgotten Vault is a persistent site rule with the Circlet of Command's shape, restricted to relics and to the Vault's ruler. It writes its log line through the hide hook, and through a `Note` where its Conspiracy `Transform` narrows the target list.
- Every power registers in an existing registry, so `PowerImplementationStatus`, the implemented-first world deck and the UI marker pick it up with no further wiring.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change, so Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md`, with the per-card rulings in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 1: modifiers and restrictions"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md` ("Emission path 2: the hide hook" and "Placement").

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Animal Playmates, Birdsong and Royal Stables write no log line (spec, "No line"). Forgotten Vault's line is exactly "Forgotten Vault: {Blue}'s relics cannot be targeted.", where `{Blue}` is the Vault's ruler.
- The reviewed-catalog stubs `MusterPowers.AnimalPlaymates` and `MusterPowers.Birdsong` stay, as `MusterPowers.CupOfPlenty` stays beside the real Cup of Plenty. `PowerImplementationStatus` already treats a walker-catalog power as implemented.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala` | The shared "spend no Supply when the source card's suit qualifies" transform for Muster and Trade. |
| Modify `src/main/scala/oathdigital/gameplay/powers/economy/CupOfPlenty.scala` | Uses `SupplyWaiver`. Behaviour unchanged. |
| Create `src/main/scala/oathdigital/gameplay/powers/economy/AnimalPlaymates.scala` | Muster waiver for a beast source. |
| Create `src/main/scala/oathdigital/gameplay/powers/economy/Birdsong.scala` | Trade waiver for a beast or nomad source. |
| Modify `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala` | Registers both. |
| Create `src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala` | The Travel reduction (N1). |
| Modify `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala` | Registers it. |
| Create `src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala` | The relic protection and its note. |
| Modify `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala` | Registers it. |
| Modify `src/test/scala/oathdigital/gameplay/powers/NoteText.scala` | Reads any power's notes by id, unless Power log lines slice 3 already added that. |
| Create the four card suites beside their packages' existing suites | One per card. |
| Modify `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala` | Pins the four catalog flags. |
| Modify `docs/ROADMAP.md` | Records the slice. |

---

### Task 1: Supply waivers (Cup of Plenty refactor, Animal Playmates, Birdsong)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/CupOfPlenty.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/AnimalPlaymates.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Birdsong.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/AnimalPlaymatesSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/economy/BirdsongSuite.scala`; existing `CupOfPlentySuite` must stay green.

**Interfaces:**
- Consumes: `PowerAnswers.one(pending: PendingTree, decisionId: String): Option[DecisionOptionRef]`, `MusterProcedure.decisionId` (`"muster.source"`), `TradeProcedure.decisionId` (`"trade.source"`), `MusterSource.matching(catalog, ready, actor, suit): Int`, `CatalogCards.denizen(catalog, id): Option[DenizenId]`.
- Produces: `SupplyWaiver.effects(window: PowerWindow, decisionId: String, catalog: ExecutableCatalog)(free: (ReadyGame, PlayerId, Suit) => Boolean): Map[PowerWindow, Vector[Contribution]]` (package-private to `economy`); `AnimalPlaymates.id`, `Birdsong.id`.

- [ ] **Step 1: Create `SupplyWaiver`**

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** The shape of the Muster and Trade modifiers that waive the Supply payment
  * by the suit of the card the action uses (the Cup of Plenty, Animal
  * Playmates, Birdsong).
  *
  * The card is the answer to the action's source decision, which the cost node
  * cannot see, so the actor's Supply payment is replaced by a node that reads
  * the answer when it runs and pays unless `free` holds for the card's suit.
  * The suit is read off the answered card, not resolved as a source: this node
  * runs after the payment that placed a token on the card, which a source must
  * not yet hold. A card whose suit cannot be read pays.
  */
private[economy] object SupplyWaiver:
  def effects(window: PowerWindow, decisionId: String,
      catalog: ExecutableCatalog)(free: (ReadyGame, PlayerId, Suit) => Boolean)
      : Map[PowerWindow, Vector[Contribution]] = Map(
    window -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay @ SpendSupply(player, _, _) if player == ctx.activePlayer =>
          unlessFree(pay, decisionId, catalog, free)
        case other => other
      })))

  private def unlessFree(pay: SpendSupply, decisionId: String,
      catalog: ExecutableCatalog, free: (ReadyGame, PlayerId, Suit) => Boolean)
      : Operation = BuildOps((ready, pending) => Right(
    if suitOf(pending, decisionId, catalog).exists(free(ready, pay.player, _))
    then Vector.empty
    else Vector[CoreOperation](pay)))

  private def suitOf(pending: PendingTree, decisionId: String,
      catalog: ExecutableCatalog): Option[Suit] =
    PowerAnswers.one(pending, decisionId).flatMap:
      case DecisionOptionRef.Denizen(id) => catalog.suitOf(id)
      case DecisionOptionRef.Edifice(id) => catalog.suitOf(id)
      case _ => None
```

- [ ] **Step 2: Rewrite the Cup of Plenty on it**

Replace the whole file `CupOfPlenty.scala` with:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** The Cup of Plenty (relic R07), a selected Trade modifier: trading with a
  * card whose suit differs from every faceup adviser the player holds costs no
  * Supply. A facedown adviser does not count, and a player with no faceup
  * adviser trades free. The waiver is [[SupplyWaiver]].
  */
final case class CupOfPlenty private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = CupOfPlenty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = SupplyWaiver.effects(
    PowerWindow.TradeCost, TradeProcedure.decisionId, catalog)(
    (ready, actor, suit) =>
      MusterSource.matching(catalog, ready, actor, suit) == 0)

object CupOfPlenty:
  val id: PowerId = PowerId("relic.cup-of-plenty")

  def forCatalog(catalog: ExecutableCatalog): Option[CupOfPlenty] =
    CatalogCards.relic(catalog, id).map(new CupOfPlenty(_, catalog))
```

- [ ] **Step 3: Run the Cup of Plenty suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.CupOfPlentySuite"`
Expected: PASS, every test. This step is a refactor; a failure means the helper changed behaviour.

- [ ] **Step 4: Commit the refactor**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/CupOfPlenty.scala
git commit -m "refactor(powers): share the Cup of Plenty's Supply waiver"
```

- [ ] **Step 5: Write the failing Animal Playmates suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/AnimalPlaymatesSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class AnimalPlaymatesSuite extends munit.FunSuite:
  import EconomyFixture._
  import SearchFixture.rules

  private val playmates = DenizenId("40")
  private val modifiers = Vector(AnimalPlaymates.id)
  private val economy = MusterPowers.powers.map(_.id).toSet

  /** A token-free denizen of `suit` with no Muster or Trade power. */
  private def denizenOf(suit: Suit): DenizenId = DenizenId(catalog.denizens
    .find(d => d.suit == suit && d.id.value != playmates.value &&
      !d.powers.exists(p => economy(p.id))).get.id.value)

  /** A beast edifice whose intact face has no Muster or Trade power. */
  private val beastEdifice: EdificeId = EdificeId(catalog.edifices
    .find(e => e.suit == Suit.Beast &&
      !e.intact.powers.exists(p => economy(p.id))).get.id.value)

  /** The actor holds Animal Playmates as a faceup adviser; their site holds
    * the plain card and `source`.
    */
  private def at(source: SiteDenizenState,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val ready = CardStaging.without(act(advisers = Vector(
      DenizenState(playmates, orientation, Tokens.empty))), source.id)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(denizens = (Vector[SiteDenizenState](
        DenizenState(plainId, Orientation.FaceUp, Tokens.empty), source))
        .distinctBy(_.id))))))

  private def denizen(id: DenizenId): SiteDenizenState =
    DenizenState(id, Orientation.FaceUp, Tokens.empty)

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DecisionOptionRef): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster,
      PowerFixture.actor, selected).toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def supply(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.supply.supply

  test("Animal Playmates is a registered free selected Muster modifier"):
    val power = AnimalPlaymates.forCatalog(catalog).get
    assertEquals(power.cardId, playmates)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("mustering on a beast denizen spends no Supply"):
    val beast = denizenOf(Suit.Beast)
    val ready = at(denizen(beast))
    val (transition, result) = muster(ready, modifiers,
      DecisionOptionRef.Denizen(beast))
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.tokensOn(result, beast), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("mustering on a beast edifice spends no Supply"):
    val ready = at(EdificeState(beastEdifice, EdificeSide.Intact, Tokens.empty))
    val (_, result) = muster(ready, modifiers,
      DecisionOptionRef.Edifice(beastEdifice))
    assertEquals(supply(result), 7)

  test("mustering on a card of another suit pays the Supply"):
    val order = denizenOf(Suit.Order)
    val (_, result) = muster(at(denizen(order)), modifiers,
      DecisionOptionRef.Denizen(order))
    assertEquals(supply(result), 6)

  test("without the selection a beast card pays the Supply"):
    val beast = denizenOf(Suit.Beast)
    val (_, result) = muster(at(denizen(beast)), Vector.empty,
      DecisionOptionRef.Denizen(beast))
    assertEquals(supply(result), 6)

  test("it may be selected whatever the site holds, and only for a Muster"):
    val ready = at(denizen(denizenOf(Suit.Order)))
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(ready,
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(AnimalPlaymates.id))
    assert(!offered(ActionRef.Trade).contains(AnimalPlaymates.id))

  test("a facedown Animal Playmates is not offered"):
    val ready = at(denizen(denizenOf(Suit.Beast)), Orientation.FaceDown)
    assert(!rules.offerableWalkerPowers(ready, PowerFixture.actor,
      ActionRef.Muster).toOption.get.map(_.id).contains(AnimalPlaymates.id))
```

- [ ] **Step 6: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.AnimalPlaymatesSuite"`
Expected: compile error, `AnimalPlaymates` is not found in `oathdigital.gameplay.powers.economy`.

- [ ] **Step 7: Implement Animal Playmates**

Create `src/main/scala/oathdigital/gameplay/powers/economy/AnimalPlaymates.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Animal Playmates (card 40, adviser-only), a selected Muster modifier with
  * no cost: mustering on a beast denizen or edifice spends no Supply. On any
  * other card the Muster pays as usual. It may be selected whatever the card,
  * as the Cup of Plenty may. The waiver is [[SupplyWaiver]].
  */
final case class AnimalPlaymates private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = AnimalPlaymates.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = SupplyWaiver.effects(
    PowerWindow.MusterCost, MusterProcedure.decisionId, catalog)(
    (_, _, suit) => suit == Suit.Beast)

object AnimalPlaymates:
  val id: PowerId = PowerId("denizen.animal-playmates")

  def forCatalog(catalog: ExecutableCatalog): Option[AnimalPlaymates] =
    CatalogCards.denizen(catalog, id).map(new AnimalPlaymates(_, catalog))
```

In `ActionModifiers.scala`, import `oathdigital.gameplay.powers.economy.{AnimalPlaymates, CupOfPlenty, RowdyPub}` and add `AnimalPlaymates.forCatalog(catalog).toVector ++` after the `CupOfPlenty` line.

- [ ] **Step 8: Run the suite to see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.AnimalPlaymatesSuite oathdigital.gameplay.powers.economy.CupOfPlentySuite"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/AnimalPlaymates.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/AnimalPlaymatesSuite.scala
git commit -m "feat(powers): Animal Playmates waives a beast Muster's Supply"
```

- [ ] **Step 10: Write the failing Birdsong suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/BirdsongSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class BirdsongSuite extends munit.FunSuite:
  import EconomyFixture._
  import SearchFixture.rules

  private val birdsong = DenizenId("176")
  private val modifiers = Vector(Birdsong.id)
  private val economy = MusterPowers.powers.map(_.id).toSet

  private def denizenOf(suit: Suit): DenizenId = DenizenId(catalog.denizens
    .find(d => d.suit == suit && d.id.value != birdsong.value &&
      !d.powers.exists(p => economy(p.id))).get.id.value)

  /** The actor holds Birdsong as a faceup adviser; their site holds the plain
    * card and `source`, token-free.
    */
  private def at(source: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val ready = CardStaging.without(act(advisers = Vector(
      DenizenState(birdsong, orientation, Tokens.empty))), source)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(denizens = Vector(plainId, source).distinct
        .map(id => DenizenState(id, Orientation.FaceUp, Tokens.empty)))))))

  /** Trades for favor with `card`, returning the result and the journal. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade,
      PowerFixture.actor, selected, Vector(DecisionOptionRef.Button("favor")))
      .toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def supply(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.supply.supply

  test("Birdsong is a registered free selected Trade modifier"):
    val power = Birdsong.forCatalog(catalog).get
    assertEquals(power.cardId, birdsong)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Trade))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("trading with a beast card spends no Supply"):
    val beast = denizenOf(Suit.Beast)
    val ready = at(beast)
    val (transition, result) = trade(ready, modifiers, beast)
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("trading with a nomad card spends no Supply"):
    val nomad = denizenOf(Suit.Nomad)
    assertEquals(supply(trade(at(nomad), modifiers, nomad)._2), 7)

  test("trading with a card of another suit pays the Supply"):
    val order = denizenOf(Suit.Order)
    assertEquals(supply(trade(at(order), modifiers, order)._2), 6)

  test("without the selection a beast card pays the Supply"):
    val beast = denizenOf(Suit.Beast)
    assertEquals(supply(trade(at(beast), Vector.empty, beast)._2), 6)

  test("it may be selected whatever the site holds, and only for a Trade"):
    val ready = at(denizenOf(Suit.Order))
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(ready,
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Trade).contains(Birdsong.id))
    assert(!offered(ActionRef.Muster).contains(Birdsong.id))

  test("a facedown Birdsong is not offered"):
    val ready = at(denizenOf(Suit.Beast), Orientation.FaceDown)
    assert(!rules.offerableWalkerPowers(ready, PowerFixture.actor,
      ActionRef.Trade).toOption.get.map(_.id).contains(Birdsong.id))
```

- [ ] **Step 11: Run it to see it fail, then implement Birdsong**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.BirdsongSuite"`
Expected: compile error, `Birdsong` is not found.

Create `src/main/scala/oathdigital/gameplay/powers/economy/Birdsong.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Birdsong (card 176, adviser-only), a selected Trade modifier with no cost:
  * trading with a beast or nomad card spends no Supply. It may be selected
  * whatever the card, as the Cup of Plenty may. The waiver is
  * [[SupplyWaiver]].
  */
final case class Birdsong private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Birdsong.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = SupplyWaiver.effects(
    PowerWindow.TradeCost, TradeProcedure.decisionId, catalog)(
    (_, _, suit) => Birdsong.Suits(suit))

object Birdsong:
  val id: PowerId = PowerId("denizen.birdsong")
  val Suits: Set[Suit] = Set(Suit.Beast, Suit.Nomad)

  def forCatalog(catalog: ExecutableCatalog): Option[Birdsong] =
    CatalogCards.denizen(catalog, id).map(new Birdsong(_, catalog))
```

In `ActionModifiers.scala`, import `{AnimalPlaymates, Birdsong, CupOfPlenty, RowdyPub}` and add `Birdsong.forCatalog(catalog).toVector ++` after the `AnimalPlaymates` line.

In `PowerKindsCatalogSuite.scala`, append `"denizen.animal-playmates", "denizen.birdsong"` to `modifiers`.

- [ ] **Step 12: Run the three suites and the catalog pins**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/Birdsong.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/BirdsongSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Birdsong waives a beast or nomad Trade's Supply"
```

---

### Task 2: Royal Stables and the Supply reduction (N1)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/RoyalStablesSuite.scala`

**Interfaces:**
- Consumes: `TravelRoute.pawnMove(operation): Option[PawnMove]` and `TravelRoute.adjustSupply(operations, actor)(rewrite: Int => Int)`, both `private[travel]` in `TravelSitePowers.scala`; `ContributingPower.priority: Int` (default 0, sorted ascending by `ContributingPower.sortKey`); the test fixture `TravelFixture`.
- Produces: `RoyalStables.id`, `RoyalStables.Priority`.

Why the priority: `ContributionCollector.gather` folds a window's transforms in `sortKey` order, `(priority, source.stableKey, id)`. A selected modifier's source key is `game:denizen.royal-stables`, which sorts before a terrain power's `site:…`. At priority 0 the reduction would run before the mountain's +1, and a route whose printed base is 1 would cost 2 instead of 1. The suite's terrain test proves the order.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/travel/RoyalStablesSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class RoyalStablesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val stables = DenizenId("245")
  private val modifiers = Vector(RoyalStables.id)
  /** Royal Stables at the actor's site, the first plains. */
  private def atHome: ReadyGame = denizenAt(board(), stables, plains.head)

  test("Royal Stables is a registered free selected Travel modifier"):
    val power = RoyalStables.forCatalog(catalog).get
    assertEquals(power.cardId, stables)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Travel))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("a Travel costs one less Supply"):
    val ready = passRuled(atHome)
    val province = plains(1)
    assertEquals(candidates(ready).get(province), Some(2))
    assertEquals(candidates(ready, modifiers).get(province), Some(1))
    val done = travel(ready, province, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(province))
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))

  test("a Travel never costs less than 1 Supply"):
    assertEquals(candidates(atHome).get(coast), Some(1))
    assertEquals(candidates(atHome, modifiers).get(coast), Some(1))
    assertEquals(supplyOf(after(travel(atHome, coast, modifiers).toOption.get)),
      7 - 1)

  test("terrain applies first: a mountain in the region costs 1 + 1 - 1"):
    // The cradle holds the mountain and the coast here, so the printed base
    // from the coast to the mountain is 1 and the mountain adds 1. Reducing
    // first would clamp 1 to 1 and then add 1, costing 2.
    val cradle = updateActor(board(source = mountain))(
      _.copy(pawnSite = Some(coast)))
    val ready = denizenAt(cradle, stables, coast)
    assertEquals(candidates(ready).get(mountain), Some(2))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(1))

  test("with Tents the Travel is free: Tents removes the payment"):
    val tents = DenizenId("29")
    val ready = withBoard(adviser(atHome, tents))(_.copy(favor = 1))
    val both = Vector(Tents.id, RoyalStables.id)
    assertEquals(candidates(ready, both).get(coast), Some(0))
    assertEquals(supplyOf(after(travel(ready, coast, both).toOption.get)), 7)

  test("it may be used at a site the actor rules"):
    val ruled = ruledBy(denizenAt(board(), stables, plains(2)), plains(2), actor)
    val ready = passRuled(ruled)
    assertEquals(candidates(ready, modifiers).get(plains(1)), Some(1))

  test("it may not be used at a site the actor neither stands at nor rules"):
    val ready = passRuled(denizenAt(board(), stables, plains(2)))
    assert(travel(ready, plains(1), modifiers).isLeft)

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(atHome,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Travel).contains(RoyalStables.id))
    assert(!offered(ActionRef.Muster).contains(RoyalStables.id))
```

If `candidates(ready).get(mountain)` is not `Some(2)` in the terrain test, the fixture does not put the mountain and the coast in one region as the comment says. Fix the staging so the printed base is 1 and the mountain adds 1; do not change the expected `Some(1)` with Royal Stables.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.RoyalStablesSuite"`
Expected: compile error, `RoyalStables` is not found.

- [ ] **Step 3: Implement Royal Stables**

Create `src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Royal Stables (card 245, site-only), a selected Travel modifier with no
  * cost, usable at the pawn's site or a site the player rules: Travel costs
  * one less Supply, never less than 1.
  *
  * It lowers the amount of the Travel's `SpendSupply`, when the Travel has
  * one. Tents and Forest Paths remove the payment entirely, so with either the
  * Travel stays free. Terrain and the other Travel powers settle the amount
  * first: they all sort at priority 0, and Royal Stables sorts after them.
  */
final case class RoyalStables private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = RoyalStables.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def priority: Int = RoyalStables.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.adjustSupply(operations, ctx.activePlayer)(amount =>
        (amount - RoyalStables.Reduction).max(RoyalStables.Minimum)))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

object RoyalStables:
  val id: PowerId = PowerId("denizen.royal-stables")
  val Reduction: Int = 1
  val Minimum: Int = 1
  /** Folds after every Travel power at the default priority 0. */
  val Priority: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[RoyalStables] =
    CatalogCards.denizen(catalog, id).map(new RoyalStables(_, catalog))
```

In `TravelModifiers.scala`, add `RoyalStables.forCatalog(catalog).toVector ++` after the `Tents` line. In `PowerKindsCatalogSuite.scala`, append `"denizen.royal-stables"` to `modifiers`.

- [ ] **Step 4: Run the Travel suites and the catalog pins**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. If the terrain test fails with 2, the fold does not run in ascending priority; read `ContributionCollector.gather` and fix the order there only if it contradicts `sortKey`'s scaladoc, otherwise adjust `Priority`.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala src/test/scala/oathdigital/gameplay/powers/travel/RoyalStablesSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Royal Stables lowers a Travel's Supply by one"
```

---

### Task 3: Forgotten Vault

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala`

**Interfaces:**
- Consumes: `SiteRulers.rulerOfCard(ready, card: DenizenId): Option[SiteRuler]` (player or bandits only), `SiteRule.enemies(left: SiteRuler, right: SiteRuler): Boolean`, `OptionRestriction(fn, note)`, `Note(power, build, covers = false)`, `NoteKey`, `PowerSourceRef.Card`, `NoteArg.Player`; the fixtures `TargetingFixture`, `CampaignFixture.raidBoard`, `PowerFixture`, `CardStaging`.
- Produces: `ForgottenVault.id`, `ForgottenVault.shielded: NoteKey` (name `"shielded"`); `NoteText.said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent]): Vector[Said]`, unless it already exists.

Rulings to hold to: it protects every relic the Vault's ruler holds, faceup or facedown, from that ruler's enemies (in an all-Exile game, every other player). It restricts a Raid's target options (`CampaignTargetSelection`) and a played Conspiracy's targets (`ConspiracyTargetSelection`). It does not touch banners. Ruled by bandits or unruled, it protects nothing.

Log line: this follows the Power log lines slice 3 plan's rulings for the Circlet of Command, whose note key is also `"shielded"`. Each relic it hides from the Raid is noted through the hide hook; the Game Log posts an identical line once per action (power log lines design, "Placement"). The Conspiracy list is narrowed by a `Transform`, as the Circlet's is, because an `OptionRestriction` cannot drop a decision left with no option. There the `Transform` places the same note as a `Note` before the narrowed decision, or in its place when no option is left.

- [ ] **Step 1: Let `NoteText` read any power's notes**

The Power log lines slice 3 plan (`docs/superpowers/plans/2026-09-26-power-log-lines-slice3.md`, Task 1) adds the same overload. If `NoteText.said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent])` already exists on `main`, skip this step. Otherwise, in `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`, replace `said` with exactly the code that plan uses, so whichever lands second merges cleanly:

```scala
  /** The notes `power` journaled in `events`, in order. */
  def said(power: PhasePower, events: Vector[OathEvent]): Vector[Said] =
    said(power.id, power.noteKeys, events)

  /** The notes the power `id`, declaring `keys`, journaled in `events`. */
  def said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent])
      : Vector[Said] =
    events.collect { case PowerNoted(`id`, note, covers) =>
      Said(note.key, sentence(keys, note), covers) }
```

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class ForgottenVaultSuite extends munit.FunSuite:
  import TargetingFixture._

  private val vault = DenizenId("75")
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))
  private val power = ForgottenVault.forCatalog(catalog).get

  private def notes(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def line(ruler: PlayerId): NoteText.Said = NoteText.Said("shielded",
    s"${ruler.value}'s relics cannot be targeted.", covers = false)

  /** The Vault at a site other than `avoid`, ruled by `ruler` (`None` leaves
    * the site's bandits).
    */
  private def vaultAt(state: ReadyGame, avoid: SiteId,
      ruler: Option[PlayerId]): ReadyGame =
    val site = state.game.current.map.inPlay.find(_ != avoid).get
    val placed = PowerFixture.atSite(CardStaging.without(state, vault), vault,
      site)
    ruler.fold(placed)(ruledBy(placed, site, _))

  test("the Vault is a registered persistent rule, so it is automatic"):
    assertEquals(power.cardId, vault)
    assertEquals(power.resolution, PowerResolution.Automatic)

  // ---- Raid ----

  enum Ruler:
    case Defender, Attacker, Bandits

  /** Starts a Raid on the defender with the Vault's site ruled by `ruler`, and
    * answers the kind. Returns the defender, their faceup relic, the Raid's
    * target options and the journal of the answer.
    */
  private def raidTargets(ruler: Ruler)
      : (PlayerId, RelicId, Vector[DecisionOptionRef], Vector[OathEvent]) =
    val (b, relic) = raidBoard()
    val held = vaultAt(b.ready, b.origin, ruler match
      case Ruler.Defender => Some(b.other)
      case Ruler.Attacker => Some(b.actor)
      case Ruler.Bandits => None)
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    val kind = rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    (b.other, relic, optionsAt(kind, ActionRef.Campaign), kind.events)

  test("a Raid may not target the ruler's relics, but may target their banners"):
    val (defender, relic, options, events) = raidTargets(Ruler.Defender)
    assert(!options.contains(DecisionOptionRef.Relic(relic)), options.toString)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(notes(events), Vector(line(defender)))

  test("the Vault's ruler may target an enemy's relics"):
    val (_, relic, options, events) = raidTargets(Ruler.Attacker)
    assert(options.contains(DecisionOptionRef.Relic(relic)))
    assertEquals(notes(events), Vector.empty)

  test("a Vault ruled by bandits protects nothing"):
    val (_, relic, options, _) = raidTargets(Ruler.Bandits)
    assert(options.contains(DecisionOptionRef.Relic(relic)))

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val other = RelicId("R10")

  /** The actor plays Conspiracy at a site the enemy shares. The enemy holds
    * relic R10, and the People's Favor when `banner`. The Vault stands at
    * another site, ruled by the enemy. Returns the enemy, the target options
    * (empty when no decision was asked) and the journal.
    */
  private def conspiracyTargets(banner: Boolean)
      : (PlayerId, Vector[DecisionOptionRef], Vector[OathEvent]) =
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = base.game.current.players.map(_.player).find(_ != actor).get
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), other)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if p.player == enemy then
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(
            holder = Option.when(banner)(enemy)),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val ready = vaultAt(holds(staged, enemy, other), site.get, Some(enemy))
    val hook = CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    ProcedureWalker.advance(ready, hook, None, powers).toOption.get match
      case WalkerOutcome.Parked(pending, events) =>
        (enemy, ProcedureWalker.parkedDecide(ready, hook, pending, powers).get
          .query.asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref), events)
      case WalkerOutcome.Finished(_, events) => (enemy, Vector.empty, events)

  test("Conspiracy may take the ruler's banner, but not their relic"):
    val (enemy, options, events) = conspiracyTargets(banner = true)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(notes(events), Vector(line(enemy)))

  test("a Conspiracy left with no target asks nothing and still says why"):
    val (enemy, options, events) = conspiracyTargets(banner = false)
    assertEquals(options, Vector.empty)
    assertEquals(notes(events), Vector(line(enemy)))
```

`vaultAt` puts the Vault at a site that is not the Raid's origin, so ruling that site changes nothing about the Raid itself.

- [ ] **Step 3: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.ForgottenVaultSuite"`
Expected: compile error, `ForgottenVault` is not found.

- [ ] **Step 4: Implement Forgotten Vault**

Create `src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Forgotten Vault (card 75, site-only), a persistent rule: enemies of the
  * Vault's ruler cannot target relics that ruler holds, as the Circlet of
  * Command protects its holder's relics. The ruler is the ruler of the Vault's
  * site. Ruled by bandits or unruled, it protects nothing, because bandits hold
  * no relics. Empire rulers are not supported.
  *
  * It restricts the two decisions that name another player's relic:
  *
  *  - a Raid's optional targets (`CampaignTargetSelection`), through an
  *    `OptionRestriction` whose hide hook notes each relic it hides;
  *  - a played Conspiracy's target (`ConspiracyTargetSelection`), through a
  *    `Transform`, as the Circlet's is, because a decision left with no option
  *    is dropped there. The same note goes before the narrowed decision, or
  *    in its place.
  *
  * The Game Log posts identical notes once per action.
  */
final case class ForgottenVault private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = ForgottenVault.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(ForgottenVault.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => protectedRuler(ctx).map(said))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Forgotten Vault protects its ruler's relics"))

  private def said(ruler: PlayerId): PowerNote =
    ForgottenVault.shielded(PowerSourceRef.Card(cardId), NoteArg.Player(ruler))

  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] = operations.flatMap:
    case decide: Decide => decide.query match
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filterNot(option => shields(ctx, option.ref))
        if options.size == one.options.size then Vector(decide)
        else
          val note = protectedRuler(ctx).toVector.map(ruler =>
            Note(id, _ => Some(said(ruler))))
          if options.isEmpty then note
          else note :+ decide.copy(query = one.copy(options = options))
      case _ => Vector(decide)
    case other => Vector(other)

  /** The Vault's ruler, when the acting player is one of their enemies. */
  private def protectedRuler(ctx: PowerCtx): Option[PlayerId] =
    SiteRulers.rulerOfCard(ctx.state, cardId).collect {
      case ruler @ SiteRuler.Player(owner)
          if SiteRule.enemies(ruler, SiteRuler.Player(ctx.activePlayer)) => owner
    }

  /** Whether `ref` names a relic the protected ruler holds. */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    protectedRuler(ctx).exists(owner => ref match
      case DecisionOptionRef.Relic(relic) =>
        ctx.state.game.current.players.exists(p =>
          p.player == owner && p.relics.exists(_.id == relic))
      case DecisionOptionRef.RelicSlot(slotOwner, _) => slotOwner == owner
      case _ => false)

object ForgottenVault:
  val id: PowerId = PowerId("denizen.forgotten-vault")

  /** "{ruler}'s relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[ForgottenVault] =
    CatalogCards.denizen(catalog, id).map(new ForgottenVault(_, catalog))
```

In `TargetProtections.scala`, add `ForgottenVault.forCatalog(catalog).toVector ++` after the `CircletOfCommand` line, and change its scaladoc's first sentence to "The persistent rules that keep a player, or what they hold, from being targeted by a Raid, a Challenge or a Conspiracy, registered together." In `PowerKindsCatalogSuite.scala`, append `"denizen.forgotten-vault"` to `persistentRules`.

If Power log lines slice 3 has merged and `CircletOfCommand` now narrows the Conspiracy through a shared helper that places the note, use that helper for `dropShielded` instead of the copy above, and keep the behaviour the suite asserts.

- [ ] **Step 5: Run the targeting suites, the note suites and the catalog pins**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. The phase-power suites that call `NoteText.said` compile unchanged; the full run in Task 4 confirms it.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala src/test/scala/oathdigital/gameplay/powers/NoteText.scala src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Forgotten Vault protects its ruler's relics"
```

---

### Task 4: Gates and roadmap

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `docs/ROADMAP.md` (section "Phase - Catalog batch 2")

- [ ] **Step 1: Pin the four cards as implemented**

At the end of `PowerImplementationStatusSuite`, add:

```scala
  test("catalog batch 2's modifiers and restrictions are implemented"):
    Vector("denizen.animal-playmates", "denizen.birdsong",
      "denizen.royal-stables", "denizen.forgotten-vault").foreach(id =>
      assert(implemented(PowerId(id)), id))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Run the full gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, with the server test count above the baseline by the new tests. Four more implemented denizens change the implemented-first world deck. A suite that pins a generated deck or an implemented-card list by value fails here; update that pin to the new value and name the suite in the commit body. A behaviour failure is a bug in Tasks 1 to 3; fix it there.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Update the roadmap**

In `docs/ROADMAP.md`, section "### Phase - Catalog batch 2", replace the sentences

```
denizens a generated world deck holds, so every card in it works. 37 of 255
denizens and 16 of 48 relics are implemented today; after this phase 60 and
24 are.
```

with

```
denizens a generated world deck holds, so every card in it works. 37 of 255
denizens and 16 of 48 relics were implemented before this phase; after it 60
and 24 are.
```

and replace

```
settles each card. Implementation starts once the Power log lines phase
merges its `Note` mechanism, so each card writes its line as it lands.
```

with

```
settles each card. Each card writes its Game Log line through the Power log
lines `Note` mechanism as it lands.

Slice 1 is done: Animal Playmates, Birdsong, Royal Stables and Forgotten
Vault, with the Travel Supply reduction. Slices 2 to 5 remain: battle plans,
actions on yourself, actions on others, then triggers and when-played powers.
```

Rewrap the paragraph to the file's line width.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 1"
```

If Step 2 changed a pinned suite, stage it by path in this commit and name it in the body.
