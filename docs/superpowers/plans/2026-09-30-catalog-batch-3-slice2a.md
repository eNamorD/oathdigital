# Catalog Batch 3, Slice 2a (Muster, Trade and Search Modifiers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement six powers that change a major action: Village Idiot (231), Downtrodden (81), Initiation Rite (73), The Old Oak (42), Disciples (205) and Crop Rotation (128).

**Architecture:**

- Slice 2 of the spec is split in two plans. This one, 2a, holds the cards that change a Muster, a Trade or a Search. Slice 2b holds the triggers, the Rest and Wake powers, Lost Tongue and the target-protection fix.
- Five of the six are `SelectedModifier`s (`src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`), each copying an implemented modifier:
  - Village Idiot and Downtrodden add a node to `MusterGain`, as Rowdy Pub does.
  - The Old Oak adds a node to `TradeGain`.
  - Disciples rewrites the Search's `SearchCost` node.
  - Crop Rotation changes the card-play rules at `SearchPlayAdviser`, exactly as the Mob face of the People's Favor does.
- Initiation Rite is an automatic rule of a faceup adviser, as Vow of Obedience's is. It rewrites the Muster's `PayCost` at `MusterCost`.
- All six register in `ActionModifiers`.
- No engine change: no new operation, window, query, option kind, `NoteArg` kind, protocol or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Slicing", "Log lines", "Slice 2", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch" and "Slice 2"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines", "Slice 2"):
  - Crop Rotation, after the discard answer: "Crop Rotation: {Red} may discard a card at their site first." It is `PlacementRules.discardFirst`, key `discard-first`, the key the Mob face writes.
  - **No line:** Initiation Rite and Disciples, whose cost changes the start line's cost span shows. The Old Oak and Downtrodden, whose amount changes the gain lines show, as for Rowdy Pub. Village Idiot's favor gain, which the generic gain line shows.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **Suits.** Only faceup cards have a suit. An edifice has its suit on both faces (`catalog.suitOf`).
  - **Amounts are best effort.** A gain resolves to what its source holds.
  - **Modifiers.** A selected modifier with no cost may be selected whatever the action's source, as the Cup of Plenty may, and then does nothing when its condition fails.
- Baseline: `main` at `ee47fa1d` passes 2266 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in slice 1: `PowerKindsCatalogSuite` and per-card registration pins are not extended. Every suite below runs through `WalkerPowerCatalog.default` (`SearchFixture.rules`), so a power that is not registered is never offered or applied, and its suite fails.
2. **The reviewed stubs stay.** The spec asks to retire stubs such as `MusterPowers.InitiationRite`. Doing so breaks the legacy resolver:
   - `ReviewedPowerCatalog.resolver` registers the catalog's audited handler ids, and a handler with no registered `Power` resolves as `UnknownAbility`. `PowerRuntime` turns that into an `UnsupportedRuleCatalog` "unclassified-handler" failure.
   - Implemented modifiers keep their stubs today: Rowdy Pub, Animal Playmates and the Cup of Plenty.
   - `PowerImplementationStatus` already reports a stub implemented once a walker power covers its id.
   
   So `MusterPowers.InitiationRite`, `TheOldOak`, `Downtrodden` and `VillageIdiot` stay unchanged. If this is wrong, the cost is four dead stubs, as for Rowdy Pub.
3. **Initiation Rite's start line** (spec, "Verify at plan time"). The Muster start line's cost span shows Supply only, for every Muster: it never showed the placed favor, so it shows no secret either. `StartLines.paysCost` anchors the start line on a favor or secret move from the actor's board onto a card, so the line still appears at the cost step. Task 2 pins it with a Game Log test.
4. **Initiation Rite with no faceup secret.** The rewritten `PayCost` stays required. A holder without a faceup secret cannot pay it, the search hides every Muster source, and the Muster cannot run. Task 2 pins it.
5. **Initiation Rite is automatic.** It keeps `ContributingPower`'s default automatic resolution, as Vow of Obedience does, although the catalog marks it `persistent: false`. It is never offered as a modifier.
6. **Disciples tells the world deck by the amount** (spec, "Verify at plan time"). The cost node does not name the source. A regional discard always costs 2, so only a world-deck Search costs more. Disciples lowers any Supply payment above 2 to 2, which is exactly the card. Task 4 pins the regional case. If a later card makes a regional Search cost more than 2, Disciples must read the source instead.
7. **The Old Oak tells a Trade for secrets by its gain.** At `TradeGain` the node holds `Gain.Secrets` only in a Trade for secrets with at least one matching faceup adviser. The Old Oak is a beast card, so with The Old Oak as the source that is exactly "a Trade for secrets with a faceup beast adviser". The power always adds one node, so the window's node count does not depend on the answer.
8. **Downtrodden's "least".** The card's bank must hold strictly less favor than each of the other five banks. The banks are read when the gain node runs. The Muster's favor comes from the player's board, so the Muster itself never changes a bank before the read.
9. **Crop Rotation with Mob** (spec, "Verify at plan time"). Both call `PlacementRules.withSiteDiscardFirstBy`, which keeps one note, so only one line is written when both apply. Task 5 pins the count, not which power's line it is.
10. **Crop Rotation's own card.** The active-modifier restriction refuses a discard of a card that prints a selected power, and `CardPlay.legalChoices` reads it. So Crop Rotation at the player's site is never a legal discard while selected. Task 5 pins it.
11. **Existing suites may shift.** Two effects of registering six more powers:
    - `SearchFixture.denizensOf` lists the plain denizens of a suit with no registered walker power. Registering Downtrodden, Disciples and Crop Rotation removes them from those lists, which existing suites draw on. No existing suite should depend on one of them by position.
    - A suite that walks with the raw `WalkerPowerCatalog.default(catalog)`, not `WalkerPowers.selected(..., modifiers)`, applies every selected modifier unselected (`SelectedModifier.appliesAt` is true by default). `LazyPruningSuite`, `GlobalRestrictionsWalkerSuite`, `PassWhenEmptySuite` and `PeoplesFavorMobSuite` do this. Rowdy Pub and Animal Playmates already apply there, so most such suites should not notice the new ones.
    
    If an existing suite fails for either reason, do not weaken its assertion. When the suite is not about modifiers, switch its walk to `WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)`, and say so in the report. Otherwise stop and report.

## File Structure

Production, under `src/main/scala/oathdigital/gameplay/powers/`:

| File | Change | Responsibility |
|---|---|---|
| `economy/VillageIdiot.scala` | Create | A favor from the Hearth bank when mustering on Village Idiot |
| `economy/Downtrodden.scala` | Create | Two more warbands when the card's bank strictly holds the least |
| `economy/InitiationRite.scala` | Create | Its holder's Muster places a secret instead of a favor |
| `economy/TheOldOak.scala` | Create | One more secret when trading with The Old Oak for secrets |
| `search/Disciples.scala` | Create | A world-deck Search costs 2 while holding the Darkest Secret |
| `search/CropRotation.scala` | Create | A play to a site may discard a card there first |
| `ActionModifiers.scala` | Modify | Registers all six |

Tests, under `src/test/scala/oathdigital/`:

| File | Change |
|---|---|
| `gameplay/powers/economy/VillageIdiotSuite.scala`, `DowntroddenSuite.scala` | Create (Task 1) |
| `gameplay/powers/economy/InitiationRiteSuite.scala` | Create (Task 2) |
| `application/gamelog/LogScripts.scala`, `GameLogActionLineSuite.scala` | Modify (Task 2) |
| `gameplay/powers/economy/TheOldOakSuite.scala` | Create (Task 3) |
| `gameplay/powers/search/DisciplesSuite.scala` | Create (Task 4) |
| `gameplay/powers/search/CropRotationSuite.scala` | Create (Task 5) |

Docs: `docs/ROADMAP.md` (Task 6).

### How the suites drive an action

Read `economy/RowdyPubSuite.scala`, `economy/AnimalPlaymatesSuite.scala`, `banner/PeoplesFavorMobSuite.scala` and `SearchFixture.scala` under `src/test/scala/oathdigital/gameplay/powers/` before writing a suite. In short:

- `Table.start` is the quiet table: p1, p2 and p3 at Ancient City, Broken Peaks and Buried Giant, p1's Act, no card at any site, every board with 1 favor, 1 faceup secret, 3 warbands and 7 Supply. Steps such as `.adviser(p1, name)`, `.denizen(name, at = Table.homeOf(p1))`, `.favor(p1, n)`, `.bankFavor(suit, n)`, `.secrets(p1, faceUp, faceDown)` and `.darkestSecret(holder, n)` each state one fact. `.ready` checks the card inventory.
- `SearchFixture.rules` is an `OathRules` with the production walker powers. `rules.startWalker(Ready(ready), action, p1, modifiers, args)` starts an action with the selected modifiers, and `rules.resolveWalker(state, p1, decisionId, answer)` answers a parked decision.
- A Muster or Trade parks at its source decision (`MusterProcedure.decisionId`, `TradeProcedure.decisionId`), answered with `DecisionOptionRef.Denizen(card)`. A Trade's start argument is `DecisionOptionRef.Button("secret")` or `Button("favor")`.
- A Search pays its cost and draws at the start, then parks at its card decision. `SearchFixture.start`, `keep` and `place` drive it to a card play.
- `Look(ready)` reads the board: `favor`, `faceUpSecrets`, `faceDownSecrets`, `warbands`, `supply`, `tokensOn(card)`.
- `PaidActionHarness.replayed(rules, ready, events)` replays a journal and `PaidActionHarness.wireRoundTrips(events)` round-trips it through the wire codec.
- `NoteText.said(id, keys, events)` lists the notes a power journaled, as `Said(key, text, covers)`. The text renders a player as their id, such as `p1`, and has no card prefix.

---

### Task 1: Village Idiot and Downtrodden

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/VillageIdiot.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Downtrodden.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/VillageIdiotSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/DowntroddenSuite.scala`

**Interfaces:**
- Consumes: `SelectedModifier`, `PowerAnswers.one(pending, decisionId): Option[DecisionOptionRef]`, `PlayerFacts.forceKind(ready, actor): Either[OathViolation, ForceKind]`, `MusterProcedure.decisionId`.
- Produces: `VillageIdiot.id`, `VillageIdiot.forCatalog(catalog): Option[VillageIdiot]`, `Downtrodden.id`, `Downtrodden.forCatalog(catalog): Option[Downtrodden]`, `Downtrodden.least(ready: ReadyGame, suit: Suit): Boolean`.

- [ ] **Step 1: Write the failing Village Idiot suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/VillageIdiotSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class VillageIdiotSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val idiot = CatalogNames.denizen("Village Idiot")
  private val other = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(VillageIdiot.id)

  /** p1's site, Ancient City, holds Errand Boy (Beast) and Village Idiot
    * (Hearth), both token-free, and the Hearth bank holds `hearth` favor.
    * p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def idiotAtSite(hearth: Int = 3): Table = Table.start
    .denizen(other, at = Table.homeOf(p1))
    .denizen(idiot, at = Table.homeOf(p1))
    .bankFavor(Suit.Hearth, hearth)

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def hearth(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("mustering on Village Idiot gains a favor from the Hearth bank"):
    val ready = idiotAtSite().ready
    val (transition, result) = muster(ready, modifiers, idiot)
    // The Muster places p1's only favor on the card, then Village Idiot
    // gives one back from the Hearth bank.
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(hearth(result), 2)
    assertEquals(Look(result).warbands(p1), 3 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("mustering on another card, or without the selection, gains no favor"):
    val ready = idiotAtSite().ready
    assertEquals(hearth(muster(ready, modifiers, other)._2), 3)
    assertEquals(hearth(muster(ready, Vector.empty, idiot)._2), 3)

  test("an empty Hearth bank gives nothing, and the Muster still runs"):
    val (_, result) = muster(idiotAtSite(hearth = 0).ready, modifiers, idiot)
    assertEquals(Look(result).favor(p1), 0)
    assertEquals(Look(result).warbands(p1), 3 + 1)

  test("it is a Muster modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(idiotAtSite().ready, ActionRef.Muster)
      .contains(VillageIdiot.id))
    assert(!offered(idiotAtSite().ready, ActionRef.Trade)
      .contains(VillageIdiot.id))
    val without = Table.start.denizen(other, at = Table.homeOf(p1)).ready
    assert(!offered(without, ActionRef.Muster).contains(VillageIdiot.id))
```

- [ ] **Step 2: Write the failing Downtrodden suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/DowntroddenSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class DowntroddenSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val downtrodden = CatalogNames.denizen("Downtrodden")
  // A plain Beast card with no Muster power, mustered on in every test.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(Downtrodden.id)

  /** p1 holds Downtrodden (Discord) as a faceup adviser and stands at
    * Ancient City with Errand Boy. Every favor bank holds 3, except as
    * `banks` sets. p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def board(banks: (Suit, Int)*): ReadyGame =
    val even = Suit.all.foldLeft(Table.start.adviser(p1, downtrodden)
      .denizen(beast, at = Table.homeOf(p1)))((table, suit) =>
      table.bankFavor(suit, 3))
    banks.foldLeft(even) { case (table, (suit, n)) => table.bankFavor(suit, n) }
      .ready

  /** Musters from Errand Boy and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId])
      : (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("mustering on a card whose bank strictly holds the least favor gains " +
      "two more warbands"):
    val ready = board(Suit.Beast -> 1)
    val (transition, result) = muster(ready, modifiers)
    assertEquals(Look(result).warbands(p1), 3 + 1 + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("a tie for least gives nothing, empty banks included"):
    assertEquals(Look(muster(board(Suit.Beast -> 1, Suit.Order -> 1),
      modifiers)._2).warbands(p1), 3 + 1)
    assertEquals(Look(muster(board(Suit.Beast -> 0, Suit.Order -> 0),
      modifiers)._2).warbands(p1), 3 + 1)

  test("a card whose bank is not the least gives nothing"):
    assertEquals(Look(muster(board(Suit.Order -> 1), modifiers)._2)
      .warbands(p1), 3 + 1)

  test("without the selection the least bank gives nothing"):
    assertEquals(Look(muster(board(Suit.Beast -> 1), Vector.empty)._2)
      .warbands(p1), 3 + 1)

  test("it is a Muster modifier only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(board(),
      p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(Downtrodden.id))
    assert(!offered(ActionRef.Trade).contains(Downtrodden.id))
```

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VillageIdiotSuite oathdigital.gameplay.powers.economy.DowntroddenSuite"`
Expected: compile FAIL with "Not found: VillageIdiot" and "Not found: Downtrodden".

- [ ] **Step 4: Create Village Idiot**

Create `src/main/scala/oathdigital/gameplay/powers/economy/VillageIdiot.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** Village Idiot (card 231, site-only), a selected Muster modifier with no
  * cost: "After mustering with this card, gain [favor] from the Hearth bank."
  *
  * As for Rowdy Pub, the card mustered on is the answer to the Muster's
  * source decision, read when the gain node runs, so the favor is gained only
  * when Village Idiot is the card. The favor is best-effort: an empty Hearth
  * bank gives nothing. The generic gain line tells it, so it writes no line.
  */
final case class VillageIdiot private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = VillageIdiot.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ favor(ctx.activePlayer))))

  private def favor(actor: PlayerId): Operation = BuildOps((_, pending) =>
    Right(if PowerAnswers.one(pending, MusterProcedure.decisionId)
        .contains(DecisionOptionRef.Denizen(cardId)) then
      Vector[CoreOperation](Gain.Favor(actor, Suit.Hearth, VillageIdiot.Favor))
    else Vector.empty))

object VillageIdiot:
  val id: PowerId = PowerId("denizen.village-idiot")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[VillageIdiot] =
    CatalogCards.denizen(catalog, id).map(new VillageIdiot(_, catalog))
```

- [ ] **Step 5: Create Downtrodden**

Create `src/main/scala/oathdigital/gameplay/powers/economy/Downtrodden.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers,
  SelectedModifier}
import oathdigital.model._

/** Downtrodden (card 81), a selected Muster modifier with no cost: "Gain two
  * more warbands if mustering on a card whose favor bank has the least favor
  * (not tied)."
  *
  * The card's suit is read off the answer to the Muster's source decision,
  * and the banks when the gain node runs. The card's bank must hold strictly
  * less favor than each of the other five, so a tie for least, empty banks
  * included, gives nothing. The Muster's own favor comes from the player's
  * board, so it never changes a bank before the read. The gain is best-effort
  * like the base gain, and the generic gain lines tell it.
  */
final case class Downtrodden private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Downtrodden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer))))

  private def bonus(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    if suitOf(pending).exists(Downtrodden.least(ready, _)) then
      PlayerFacts.forceKind(ready, actor).map(kind => Vector[CoreOperation](
        Gain.Warbands(actor, kind, Downtrodden.Warbands)))
    else Right(Vector.empty))

  private def suitOf(pending: PendingTree): Option[Suit] =
    PowerAnswers.one(pending, MusterProcedure.decisionId).flatMap:
      case DecisionOptionRef.Denizen(card) => catalog.suitOf(card)
      case DecisionOptionRef.Edifice(card) => catalog.suitOf(card)
      case _ => None

object Downtrodden:
  val id: PowerId = PowerId("denizen.downtrodden")
  val Warbands: Int = 2

  /** Whether `suit`'s bank holds strictly less favor than every other bank. */
  def least(ready: ReadyGame, suit: Suit): Boolean =
    val favor = (bank: Suit) => ready.banks.favor.getOrElse(bank, 0)
    Suit.all.filter(_ != suit).forall(other => favor(suit) < favor(other))

  def forCatalog(catalog: ExecutableCatalog): Option[Downtrodden] =
    CatalogCards.denizen(catalog, id).map(new Downtrodden(_, catalog))
```

- [ ] **Step 6: Register them**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala` with:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Downtrodden, RowdyPub, VillageIdiot}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together. A
  * power whose card is absent from `catalog` is omitted.
  */
object ActionModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Augury.forCatalog(catalog).toVector ++
      TruthfulHarp.forCatalog(catalog).toVector ++
      CupOfPlenty.forCatalog(catalog).toVector ++
      AnimalPlaymates.forCatalog(catalog).toVector ++
      Birdsong.forCatalog(catalog).toVector ++
      RowdyPub.forCatalog(catalog).toVector ++
      VillageIdiot.forCatalog(catalog).toVector ++
      Downtrodden.forCatalog(catalog).toVector ++
      RelicWorship.forCatalog(catalog).toVector
```

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.*"`
Expected: PASS. 9 new tests (4 in `VillageIdiotSuite`, 5 in `DowntroddenSuite`), and every existing economy suite still passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/VillageIdiot.scala src/main/scala/oathdigital/gameplay/powers/economy/Downtrodden.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/VillageIdiotSuite.scala src/test/scala/oathdigital/gameplay/powers/economy/DowntroddenSuite.scala
git commit -m "feat(powers): add Village Idiot and Downtrodden"
```

---

### Task 2: Initiation Rite

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/InitiationRiteSuite.scala`

**Interfaces:**
- Consumes: `ContributingPower`, `PowerCtx`, `PayCost(player, placedAt, cost, ...)` (a case class), `Cost(favor, secret, favorBurnt, secretBurnt)`.
- Produces: `InitiationRite.id`, `InitiationRite.forCatalog(catalog): Option[InitiationRite]`, `LogScripts.initiatedMuster: Script`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/InitiationRiteSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class InitiationRiteSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val rite = CatalogNames.denizen("Initiation Rite")
  // A plain Beast card with no Muster or Trade power.
  private val beast = CatalogNames.denizen("Errand Boy")

  /** p1 holds Initiation Rite as an adviser, faceup unless `facedown`, and
    * stands at Ancient City with Errand Boy. p1 has the start's 1 favor,
    * 1 faceup secret, 3 warbands and 7 Supply. */
  private def initiated(facedown: Boolean = false): Table = Table.start
    .adviser(p1, rite, facedown = facedown)
    .denizen(beast, at = Table.homeOf(p1))

  /** Musters from Errand Boy, selecting nothing. */
  private def muster(ready: ReadyGame)
      : Either[OathViolation, (OathTransition, ReadyGame)] = for
    started <- rules.startWalker(Ready(ready), ActionRef.Muster, p1, Vector.empty)
    done <- rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
  yield (started.copy(events = started.events ++ done.events),
    done.state.asInstanceOf[Ready].value)

  test("its holder's Muster places a faceup secret on the card instead of " +
      "a favor, without being selected"):
    val ready = initiated().ready
    val (transition, result) = muster(ready).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(0, 1))
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(Look(result).faceUpSecrets(p1), 0)
    assertEquals(Look(result).supply(p1), 6)
    assertEquals(Look(result).warbands(p1), 3 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("it is a rule, never offered as a modifier"):
    assert(!rules.offerableWalkerPowers(initiated().ready, p1,
      ActionRef.Muster).toOption.get.map(_.id).contains(InitiationRite.id))

  test("with no faceup secret its holder cannot Muster"):
    val hidden = initiated().secrets(p1, faceUp = 0, faceDown = 1)
    assert(muster(hidden.ready).isLeft)
    // The same board without the rule musters with its favor.
    val plain = Table.start.denizen(beast, at = Table.homeOf(p1))
      .secrets(p1, faceUp = 0, faceDown = 1).ready
    assert(muster(plain).isRight)

  test("a facedown Initiation Rite does nothing"):
    val (_, result) = muster(initiated(facedown = true).ready).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    assertEquals(Look(result).faceUpSecrets(p1), 1)

  test("a Trade is unaffected: a Trade for secrets still places a favor"):
    val ready = initiated().favor(p1, 2).ready
    val result = (for
      started <- rules.startWalker(Ready(ready), ActionRef.Trade, p1,
        Vector.empty, Vector(DecisionOptionRef.Button("secret")))
      done <- rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
    yield done.state.asInstanceOf[Ready].value).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    assertEquals(Look(result).favor(p1), 0)
```

- [ ] **Step 2: Add the Game Log script and test**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, add this method directly after `def muster`:

```scala
  /** p1 musters at Deep Woods holding Initiation Rite, so the card mustered
    * on receives a secret instead of a favor. */
  def initiatedMuster(using munit.Location): Script =
    val (service, act) = atTable("initiated-muster", source(Table.start
      .pawn(p1, "Deep Woods").adviser(p1, "Initiation Rite")).banditsRefilled)
    start(act, ActionRef.Muster)
    Script("initiated-muster", service, active(act))
```

In `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`, add this test directly after the test "Muster: the start line waits for the cost, then names the card":

```scala
  test("Muster under Initiation Rite: the start line still waits for the " +
      "cost, which places a secret, and shows only the Supply"):
    val script = initiatedMuster
    val mine = lines(script, None)
    val start = mine.indexWhere(_.startsWith("Started Muster"))
    val mustered = mine.indexWhere(_.startsWith("Mustered "))
    assert(start >= 0 && mustered > start, mine)
    assert(mine(start).endsWith("−1 Supply"), mine(start))
```

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.InitiationRiteSuite oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: compile FAIL with "Not found: InitiationRite".

- [ ] **Step 4: Create Initiation Rite**

Create `src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Initiation Rite (card 73, adviser-only, locked): "To muster, you must
  * place [secret] instead of [favor]."
  *
  * An automatic rule of a faceup adviser, as Vow of Obedience's is: "must" is
  * not a choice, so the rule keeps the default automatic resolution although
  * the catalog marks the power `persistent: false`. At its holder's Muster
  * cost, the `PayCost` that places favor on the card places as many secrets
  * instead, moved from the holder's faceup secrets as a Trade for favor
  * places one. The Supply payment is unchanged, and Trade is not touched.
  * The payment stays required, so a holder with no faceup secret cannot pay,
  * and the Muster has no source to offer.
  *
  * It writes no line: the start line's cost span shows Supply only, for every
  * Muster, and the secret's move still anchors the start line.
  */
final case class InitiationRite private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = InitiationRite.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay: PayCost if pay.player == ctx.activePlayer &&
            pay.cost.favor > 0 =>
          pay.copy(cost = pay.cost.copy(favor = 0,
            secret = pay.cost.secret + pay.cost.favor))
        case other => other
      })))

  /** The acting player holds this card as a faceup adviser. */
  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.players.find(_.player == ctx.activePlayer)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      })

object InitiationRite:
  val id: PowerId = PowerId("denizen.initiation-rite")

  def forCatalog(catalog: ExecutableCatalog): Option[InitiationRite] =
    CatalogCards.denizen(catalog, id).map(new InitiationRite(_))
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the economy import to:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Downtrodden, InitiationRite, RowdyPub, VillageIdiot}
```

- Add `InitiationRite.forCatalog(catalog).toVector ++` on the line after `Downtrodden.forCatalog(catalog).toVector ++`.
- Replace the doc comment with:

```scala
/** The Search, Trade, Muster and Recover modifiers, registered together with
  * Initiation Rite, the rule that changes a Muster's cost. A power whose card
  * is absent from `catalog` is omitted.
  */
```

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: PASS. 6 new tests (5 in `InitiationRiteSuite`, 1 in `GameLogActionLineSuite`), and every existing test in those suites still passes.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/InitiationRiteSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "feat(powers): add Initiation Rite"
```

---

### Task 3: The Old Oak

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/TheOldOak.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/TheOldOakSuite.scala`

**Interfaces:**
- Consumes: `SelectedModifier`, `PowerAnswers.one`, `TradeProcedure.decisionId`, `SearchFixture.denizensOf(suit): Vector[DenizenId]`.
- Produces: `TheOldOak.id`, `TheOldOak.forCatalog(catalog): Option[TheOldOak]`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/TheOldOakSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TheOldOakSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val oak = CatalogNames.denizen("The Old Oak")
  // A plain Beast adviser, and another plain Beast card at the site.
  private val adviser = CatalogNames.denizen("Errand Boy")
  private val otherBeast = SearchFixture.denizensOf(Suit.Beast)
    .filterNot(_ == adviser).head
  private val modifiers = Vector(TheOldOak.id)

  /** p1 holds Errand Boy as an adviser, faceup unless `facedown`, and stands
    * at Ancient City with The Old Oak and another Beast card. p1 has 2 favor,
    * which a Trade for secrets costs, and the start's 1 faceup secret. */
  private def board(facedown: Boolean = false): ReadyGame = Table.start
    .favor(p1, 2).adviser(p1, adviser, facedown = facedown)
    .denizen(oak, at = Table.homeOf(p1))
    .denizen(otherBeast, at = Table.homeOf(p1)).ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** Trades for `resource` with `card` and returns the result and the whole
    * journal. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId, resource: String = "secret")
      : (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      selected, Vector(DecisionOptionRef.Button(resource))).toOption.get
    val done = rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("trading with The Old Oak for secrets with a faceup beast adviser " +
      "gains one more secret"):
    val ready = board()
    val (transition, result) = trade(ready, modifiers, oak)
    assertEquals(secrets(result), 1 + 1 + 1)
    assertEquals(secrets(trade(ready, Vector.empty, oak)._2), 1 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("without a faceup beast adviser it gains nothing"):
    assertEquals(secrets(trade(board(facedown = true), modifiers, oak)._2), 1)

  test("trading with another card, or for favor, gains no extra secret"):
    assertEquals(secrets(trade(board(), modifiers, otherBeast)._2), 1 + 1)
    // A Trade for favor pays the faceup secret and gains favor only.
    assertEquals(secrets(trade(board(), modifiers, oak, "favor")._2), 0)

  test("it is a Trade modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(board(), ActionRef.Trade).contains(TheOldOak.id))
    assert(!offered(board(), ActionRef.Muster).contains(TheOldOak.id))
    val without = Table.start.adviser(p1, adviser).ready
    assert(!offered(without, ActionRef.Trade).contains(TheOldOak.id))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.TheOldOakSuite"`
Expected: compile FAIL with "Not found: TheOldOak".

- [ ] **Step 3: Create The Old Oak**

Create `src/main/scala/oathdigital/gameplay/powers/economy/TheOldOak.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** The Old Oak (card 42, site-only), a selected Trade modifier with no cost:
  * "If trading with The Old Oak for [secret], gain one more [secret] if you
  * have any beast advisers."
  *
  * A Trade for secrets gains one secret per faceup adviser matching the
  * card's suit, and The Old Oak is a beast card. So with The Old Oak as the
  * card, the gain node holds a secret gain exactly when the player has a
  * faceup beast adviser, and a Trade for favor holds none. The power adds a
  * node after the gain that gains one more secret when the gain node holds a
  * secret gain for the player and the source decision was answered with The
  * Old Oak. The node is added whatever the Trade, so the window's node count
  * never depends on the answer. The generic gain line tells the secret.
  */
final case class TheOldOak private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = TheOldOak.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer, operations.exists {
        case Gain.Secrets(player, _) => player == ctx.activePlayer
        case _ => false
      }))))

  private def bonus(actor: PlayerId, secrets: Boolean): Operation =
    BuildOps((_, pending) => Right(
      if secrets && PowerAnswers.one(pending, TradeProcedure.decisionId)
          .contains(DecisionOptionRef.Denizen(cardId)) then
        Vector[CoreOperation](Gain.Secrets(actor, TheOldOak.Secrets))
      else Vector.empty))

object TheOldOak:
  val id: PowerId = PowerId("denizen.the-old-oak")
  val Secrets: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[TheOldOak] =
    CatalogCards.denizen(catalog, id).map(new TheOldOak(_, catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the economy import to:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Downtrodden, InitiationRite, RowdyPub, TheOldOak, VillageIdiot}
```

- Add `TheOldOak.forCatalog(catalog).toVector ++` on the line after `Birdsong.forCatalog(catalog).toVector ++`.

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.*"`
Expected: PASS. 4 new tests in `TheOldOakSuite`, and every existing economy suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/TheOldOak.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/economy/TheOldOakSuite.scala
git commit -m "feat(powers): add The Old Oak"
```

---

### Task 4: Disciples

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/DisciplesSuite.scala`

**Interfaces:**
- Consumes: `SelectedModifier`, `BannerRules.holder(current: CurrentGameState, banner: Banner): Option[PlayerId]`, `BuildOps(build, window, required)` (a case class), `SpendSupply(player, amount, required)` (a case class).
- Produces: `Disciples.id`, `Disciples.forCatalog(catalog): Option[Disciples]`, `Disciples.Supply: Int` (2).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/search/DisciplesSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class DisciplesSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val disciples = CatalogNames.denizen("Disciples")
  private val modifiers = Vector(Disciples.id)
  private val deckTop = SearchFixture.denizensOf(Suit.Hearth).take(3)
  private val region = Table.start.ready.game.current.map
    .regionOf(Table.homeOf(p1)).get

  /** p1 in Act at Ancient City, with Disciples as a faceup adviser and 5
    * Supply. The world deck is topped by plain cards, the regional discard
    * holds three, `visions` Visions were drawn so far, and `holder` holds the
    * Darkest Secret. */
  private def board(visions: Int, holder: Option[PlayerId] = Some(p1))
      : ReadyGame = Table.start.worldDeckTop(deckTop*).supply(p1, 5)
    .adviser(p1, disciples)
    .discarded(region, SearchFixture.denizensOf(Suit.Order).take(3)*)
    .darkestSecret(holder, 1)
    // No step states the Visions track.
    .update(_.updateCurrent(c => c.copy(tracks =
      c.tracks.copy(visionsDrawn = visions))))
    .ready

  private val world = "search:world"
  private def regional = s"search:regional-discard:${region.key}"

  /** The Supply a Search from `source` spends at its start. */
  private def spent(ready: ReadyGame, selected: Vector[PowerId],
      source: String = world): Int =
    val started = rules.startWalker(Ready(ready), ActionRef.Search, p1,
      selected, Vector(DecisionOptionRef.Button(source))).toOption.get
    5 - Look(SearchFixture.after(started)).supply(p1)

  test("holding the Darkest Secret, a world Search costing 3 costs 2"):
    val ready = board(visions = 1)
    assertEquals(spent(ready, modifiers), 2)
    assertEquals(spent(ready, Vector.empty), 3)
    val started = rules.startWalker(Ready(ready), ActionRef.Search, p1,
      modifiers, Vector(DecisionOptionRef.Button(world))).toOption.get
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))

  test("holding the Darkest Secret, a world Search costing 4 costs 2"):
    assertEquals(spent(board(visions = 3), modifiers), 2)
    assertEquals(spent(board(visions = 3), Vector.empty), 4)

  test("without the Darkest Secret the cost is unchanged"):
    assertEquals(spent(board(visions = 1, holder = Some(p2)), modifiers), 3)
    assertEquals(spent(board(visions = 1, holder = None), modifiers), 3)

  test("a cost of 2 is unchanged: the first world Search, and a regional " +
      "discard, which always costs 2"):
    assertEquals(spent(board(visions = 0), modifiers), 2)
    assertEquals(spent(board(visions = 1), modifiers, regional), 2)

  test("it may be selected without the Darkest Secret, for a Search only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(
      board(visions = 0, holder = None), p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Search).contains(Disciples.id))
    assert(!offered(ActionRef.Muster).contains(Disciples.id))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.DisciplesSuite"`
Expected: compile FAIL with "Not found: Disciples".

- [ ] **Step 3: Create Disciples**

Create `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Disciples (card 205), a selected Search modifier with no cost: "If you
  * have the Darkest Secret, spend only 2 Supply if you're searching the world
  * deck."
  *
  * The Search's cost node is rewritten so that, when it runs, a Supply
  * payment above 2 is lowered to 2 while the player holds the Darkest Secret.
  * The cost node does not name its source, but a regional discard always
  * costs 2, so only a world-deck Search costs more: lowering any higher cost
  * to 2 is exactly the card. The holder is read when the cost is paid. It may
  * be selected whatever the source, as the Cup of Plenty may. The start
  * line's cost span shows what was paid, so it writes no line.
  */
final case class Disciples private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Disciples.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case cost: BuildOps => cost.copy(build = (ready, pending) =>
          cost.build(ready, pending).map(_.map(capped(ready, ctx.activePlayer))))
        case other => other
      })))

  private def capped(ready: ReadyGame, actor: PlayerId)(
      operation: CoreOperation): CoreOperation = operation match
    case pay @ SpendSupply(player, amount, _) if player == actor &&
        amount > Disciples.Supply && BannerRules.holder(ready.game.current,
          Banner.DarkestSecret).contains(actor) =>
      pay.copy(amount = Disciples.Supply)
    case other => other

object Disciples:
  val id: PowerId = PowerId("denizen.disciples")
  /** What a world-deck Search costs its user while holding the Darkest
    * Secret. */
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[Disciples] =
    CatalogCards.denizen(catalog, id).map(new Disciples(_, catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the search import to:

```scala
import oathdigital.gameplay.powers.search.{Augury, Disciples, TruthfulHarp}
```

- Add `Disciples.forCatalog(catalog).toVector ++` on the line after `TruthfulHarp.forCatalog(catalog).toVector ++`.

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.*"`
Expected: PASS. 5 new tests in `DisciplesSuite`, and `AugurySuite` and `TruthfulHarpSuite` still pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/search/DisciplesSuite.scala
git commit -m "feat(powers): add Disciples"
```

---

### Task 5: Crop Rotation

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala`

**Interfaces:**
- Consumes: `SelectedModifier`, `CardPlayProcedure.PlacementTree.adjust(children)(change)`, `PlacementRules.withSiteDiscardFirstBy(note: Note)`, `PlacementRules.discardFirst: NoteKey`, `CardPlayProcedure.noReplacement`, `BannerFixture.holdingFavor(ready)`.
- Produces: `CropRotation.id`, `CropRotation.forCatalog(catalog): Option[CropRotation]`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.banner.{BannerFixture, PeoplesFavorMob}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

class CropRotationSuite extends munit.FunSuite:
  import SearchFixture.{keep, place, rules, start}

  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private val crop = CatalogNames.denizen("Crop Rotation")
  private val modifiers = Vector(CropRotation.id)
  private val played = SearchFixture.denizensOf(Suit.Beast).head
  private val fillers = SearchFixture.denizensOf(Suit.Order) ++
    SearchFixture.denizensOf(Suit.Arcane)
  private val home = Table.homeOf(p1)
  private val capacity = catalog.sites.find(_.id == home).get.capacity
  private val noDiscard = CardPlayProcedure.noReplacement.ref
  private val replaceId = s"cardplay.replace.denizen.${played.value}"

  /** p1 in Act at Ancient City with 5 Supply, the world deck topped by
    * `played`, and Ancient City holding `site`. p1 holds Crop Rotation as a
    * faceup adviser, or it stands at the site when `atSite`. */
  private def staged(site: Vector[DenizenId], atSite: Boolean = false)
      : ReadyGame =
    val table = Table.start.worldDeckTop(played).supply(p1, 5)
    val held = if atSite then table.denizen(crop, at = home)
      else table.adviser(p1, crop)
    site.foldLeft(held)((t, card) => t.denizen(card, at = home)).ready

  /** Searches with `selected`, keeps `played` and plays it to the site. */
  private def toSite(ready: ReadyGame, selected: Vector[PowerId] = modifiers)
      : Either[OathViolation, OathTransition] = for
    started <- start(ready, selected)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  yield placed.copy(events = started.events ++ kept.events ++ placed.events)

  private def asksToDiscard(transition: OathTransition): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == replaceId && facts.awaiting == p1)

  private def discard(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    p1, replaceId, DecisionAnswer.ChooseOneAnswer(chosen))

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    ready.game.current.map.sites(home).denizens.map(_.id)

  private def cropSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(CropRotation.id, Vector(
      oathdigital.gameplay.actions.PlacementRules.discardFirst), events)

  test("selected, a play to a site with room asks first, and may decline"):
    val ready = staged(Vector(fillers(0)))
    val asked = toSite(ready).toOption.get
    assert(asksToDiscard(asked))
    val done = discard(asked, noDiscard).toOption.get
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](fillers(0), played))
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assert(PaidActionHarness.wireRoundTrips(asked.events ++ done.events))

  test("choosing a card discards it before the play"):
    val asked = toSite(staged(Vector(fillers(0)))).toOption.get
    val done = discard(asked, DecisionOptionRef.Denizen(fillers(0))).toOption.get
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))

  test("without the selection nothing is asked"):
    val done = toSite(staged(Vector(fillers(0))), Vector.empty).toOption.get
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](fillers(0), played))

  test("a full site takes the play only with a discard"):
    val full = staged(fillers.take(capacity))
    assert(toSite(full, Vector.empty).isLeft,
      "a full site accepts no play without a permission")
    val asked = toSite(full).toOption.get
    assert(asksToDiscard(asked))
    assert(discard(asked, noDiscard).isLeft, "a discard is required")
    val done = discard(asked, DecisionOptionRef.Denizen(fillers(0))).toOption.get
    assert(siteCards(SearchFixture.after(done)).contains(played))

  test("Crop Rotation itself, selected at the site, cannot be the discard"):
    val asked = toSite(staged(Vector(fillers(0)), atSite = true)).toOption.get
    assert(asksToDiscard(asked))
    assert(discard(asked, DecisionOptionRef.Denizen(crop)).isLeft,
      "a card that prints a selected power cannot be discarded")
    assert(discard(asked, DecisionOptionRef.Denizen(fillers(0))).isRight)

  test("it applies to a facedown adviser played to a site"):
    val ready = Table.start.adviser(p1, crop)
      .adviser(p1, played, facedown = true)
      .denizen(fillers(0), at = home).ready
    val started = rules.startWalker(Ready(ready), ActionRef.PlayFacedownAdviser,
      p1, modifiers, Vector(DecisionOptionRef.Denizen(played))).toOption.get
    assert(asksToDiscard(place(started, played, "site").toOption.get))

  test("its line is written once the discard is answered"):
    val asked = toSite(staged(Vector(fillers(0)))).toOption.get
    assertEquals(cropSaid(asked.events), Vector.empty)
    val done = discard(asked, noDiscard).toOption.get
    assertEquals(cropSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${p1.value} may discard a card at their site first.", covers = false)))

  test("with the Mob as well, only one line is written"):
    val ready = BannerFixture.holdingFavor(staged(Vector(fillers(0))))
    val asked = toSite(ready).toOption.get
    val done = discard(asked, noDiscard).toOption.get
    val mobSaid = NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys,
      done.events)
    assertEquals(cropSaid(done.events).size + mobSaid.size, 1)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.CropRotationSuite"`
Expected: compile FAIL with "Not found: CropRotation".

- [ ] **Step 3: Create Crop Rotation**

Create `src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Crop Rotation (card 128), a selected Search modifier with no cost: "If
  * playing to a site, you may discard a denizen there first."
  *
  * It permits exactly what the Mob face of the People's Favor permits, through
  * the same `SearchPlayAdviser` window, so the two compose and a full site
  * takes the play only with a discard. The Play-Facedown-Adviser action
  * selects its modifiers at the Search's window, so it applies there too. The
  * generic discard rules decide which cards may go: Crop Rotation itself,
  * being selected, is refused by the active-modifier restriction.
  *
  * Its line, "{Red} may discard a card at their site first.", travels in the
  * rules, and card play writes it after the discard answer. With the Mob as
  * well, the rules keep one line, so only one is written.
  */
final case class CropRotation private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = CropRotation.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def noteKeys: Vector[NoteKey] = Vector(PlacementRules.discardFirst)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.withSiteDiscardFirstBy(note(ctx.activePlayer)))
        case _ => children
      })))

  private def note(actor: PlayerId): Note = Note(id, _ => Some(
    PlacementRules.discardFirst(PowerSourceRef.Card(cardId),
      NoteArg.Player(actor))))

object CropRotation:
  val id: PowerId = PowerId("denizen.crop-rotation")

  def forCatalog(catalog: ExecutableCatalog): Option[CropRotation] =
    CatalogCards.denizen(catalog, id).map(new CropRotation(_, catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Change the search import to:

```scala
import oathdigital.gameplay.powers.search.{Augury, CropRotation, Disciples,
  TruthfulHarp}
```

- Add `CropRotation.forCatalog(catalog).toVector ++` on the line after `Disciples.forCatalog(catalog).toVector ++`.

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.* oathdigital.gameplay.powers.banner.* oathdigital.gameplay.powers.cardplay.*"`
Expected: PASS. 8 new tests in `CropRotationSuite`, and every existing search, banner and card-play suite still passes.

If "a full site takes the play only with a discard" fails on `toSite(full, Vector.empty).isLeft`, Ancient City is the Homeland of the played card's suit. Pick `played` from a suit whose Homeland is not Ancient City and say so in the report.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala
git commit -m "feat(powers): add Crop Rotation"
```

---

### Task 6: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
Spoils, Field Promotion and Military Parade, and the plan surcharge Insect
Swarm. Slices 2 to 4 remain.
```

with:

```markdown
Spoils, Field Promotion and Military Parade, and the plan surcharge Insect
Swarm. Slice 2a is done: the Muster, Trade and Search modifiers Village
Idiot, Downtrodden, The Old Oak, Disciples and Crop Rotation, and the Muster
rule Initiation Rite. Slices 2b to 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 32 (9 in Task 1, 6 in Task 2, 4 in Task 3, 5 in Task 4, 8 in Task 5). From a baseline of 2266, that is 2298. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 2a"
```
