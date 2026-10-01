# Catalog Batch 3, Slice 3c (Actions on Sites, Banks and Banners) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the ACTION powers Taming Charm (37), Dark Enforcer (227), Great Feast (257), Plague Engines (65), Memory of Nature (191), Bandit Paymaster (219), Storyteller (52), Levelers (135), Memory of Home (49), Firebrand (233) and Ballot Box (141).

**Architecture:**

- Every power is a `PaidAction`, the ACTION phase power whose engine pays the cost onto the source card.
- They register in a new `WorldActionPowers` object, which `PhasePowerCatalog` includes after `OtherActionPowers` (spec, "Verify at plan time", "Registration").
- Powers-side refactors, which the spec's bar allows:
  - **P8:** Alchemist's favor split becomes `FavorSplit`, with the amount and the source banks as parameters. Memory of Nature uses it now and Town Meeting in slice 4.
  - `PlayerFacts.banked`, a read of the warbands left in a bank, for Ballot Box (and Bandit Prince in slice 4).
  - `SiteCards`, the discardable cards of given suits at a site with their standard discard, shared by Dark Enforcer, Taming Charm and Great Feast.
  - `BankMoves`, a favor move between two banks and the line that reads it back, shared by Levelers and Memory of Home.
- Seams used by a power for the first time, each with its own test:
  - a move from the shared bank onto a banner (Storyteller);
  - a move from a bank onto a banner, and a burn from an unheld banner (Firebrand);
  - a mixed `ChooseOne` of favor banks and a button (Firebrand, with a frontend test);
  - `Replace` with a full, short and empty supply (Ballot Box).

**Engine changes:** none. No new operation, walker window, decision query kind, option kind, procedure step, `NoteArg` kind, protocol change or frontend production change. The only frontend change is one test.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Seams used for the first time", "Log lines", "Slice 3: ACTION powers", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch" and "Slice 3c: actions on sites, banks and banners"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- Scala 3 syntax: never write `case X => for` with its generators and `yield` below. It does not parse ("yield or do expected"). Put the `for` in a helper method, as the code below does.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines", "Slice 3"). `{Red}` is the acting player, `{Blue}` another player. Each line's key is given:
  - Taming Charm, key `used`: "Taming Charm: {Red} discarded {card} and gained {n favor} from {the Beast bank}." Key `used.discarded`, the bank was empty: "Taming Charm: {Red} discarded {card}." Key `used.none`: "Taming Charm: {site} held no Beast or Nomad card."
  - Dark Enforcer, key `used`: "Dark Enforcer: Discarded {cards}." Key `used.none`: "Dark Enforcer: {site} held no Order or Hearth card to discard."
  - Great Feast, key `used`: "Great Feast: {Red} discarded {card} and gained {n} Supply." Key `used.none`: "Great Feast: {site} held no Beast card."
  - Plague Engines, key `used`, one line per player who paid: "Plague Engines: {Blue} put {n favor} into {the Arcane bank}." Key `used.none`: "Plague Engines: No player put favor into {the Arcane bank}."
  - Memory of Nature, key `used`: "Memory of Nature: Moved {n favor} to {the Beast bank}." Key `used.none`: "Memory of Nature: No favor moved to {the Beast bank}."
  - Bandit Paymaster, key `used`: "Bandit Paymaster: Removed {1} bandit warband from {site}, and {Red} gained {n warbands}." Key `used.none`: "Bandit Paymaster: {site} had no bandit to spare."
  - Storyteller, key `used`: "Storyteller: {Red} placed {1 secret} on the {Darkest Secret}."
  - Levelers, key `used`: "Levelers: Moved {n favor} from {the Beast bank} to {the Arcane bank}." Key `used.empty`: "Levelers: Every favor bank was empty."
  - Memory of Home, key `used`: "Memory of Home: Moved {n favor} from {the Order bank} to {the Hearth bank}." Key `used.empty`: "Memory of Home: Every other favor bank was empty."
  - Firebrand, key `used`: "Firebrand: {Red} moved {1 favor} from {the Order bank} to the {People's Favor}." Key `used.burned`: "Firebrand: {Red} burned {1 favor} from the {People's Favor}." Key `used.empty`: "Firebrand: Every favor bank and the People's Favor were empty."
  - Ballot Box, key `used`: "Ballot Box: Replaced {n} {Blue} warband at {site}." Key `used.bandits`: "Ballot Box: Replaced {n} bandit warband at {site}." Keys `removed` and `removed.bandits`, the supply ran short: "Ballot Box: Removed {n} {Blue} warband at {site}." / "Ballot Box: Removed {n} bandit warband at {site}." Key `used.unmatched`: "Ballot Box: {Red} had no adviser matching a card at {site}." Key `used.none`: "Ballot Box: {site} held no warband to replace."
  - `{n}` before "warband" and "bandit warband" is `NoteArg.Number` with `Plural`, as for Siege Engines.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **All-Exile.** Every other player is an enemy. There are no Imperial warbands.
  - **Suits.** Only faceup cards have a suit. An edifice has its suit on both faces (`catalog.suitOf`).
  - **Locked.** No power filters locked cards itself: the restriction refuses the operation, and the search hides the option.
  - **"X to gain Y".** Taming Charm, Great Feast and Bandit Paymaster gain only when the discard or removal happened.
  - **No target.** A paid power with no legal target pays its cost and does nothing, as Charming Friend does.
  - **Amounts are best effort.** A gain, give, move or burn resolves to what its source holds.
- Baseline: `main` after slice 3b passes 2439 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in the earlier slices. Every suite below runs through `PhasePowerCatalog.default`, so an unregistered power fails its suite. No card in this slice has a reviewed stub in Scala to retire.
2. **Generic lines, checked in `application/gamelog/DetailLines.scala`.**
   - Write a line: `Gain.Favor` (the Gain line), `Discard.Denizen` (the Discarded line, merged per pile) and a `Move` of warbands onto a site (the Moved line), which is the second child of a `Replace`.
   - Write no line: `Discard.RuinedEdifice`, a `Move` between two banks, a `Move` from the shared bank or a bank onto a banner, a `Burn` from a banner, a `Give` into a bank, a `Kill` at a site, `Gain.Warbands` and `GainSupply`.
   - So these notes cover (`covers = true`): Taming Charm, Dark Enforcer, Great Feast, and Ballot Box's `used` and `used.bandits` lines. Every other note keeps `covers = false`.
3. **A note covers only the step before it** (`LogJournal.covered`). Taming Charm's gain runs as its own step (spec, "Verify at plan time", "Same-bank gains"): the discard returns the card's favor to the bank the gain takes from. So when the gain runs, the note covers the Gain line and the generic Discarded line of a denizen stays beside it. When the bank is empty the gain step does not run, and the `used.discarded` note covers the Discarded line. If this is wrong, a later change merges the steps.
4. **Narrated decisions**, as the spec lists: Taming Charm, Great Feast, both of Levelers', Memory of Home and Firebrand. Memory of Nature's `Distribute` writes no generic line, so it needs none.
5. **One step or two.**
   - Great Feast's discard and its Supply gain run in one required batch, so its note covers the Discarded line. The gain is capped at build time to the room on the track, because a required batch refuses an operation it cannot make in full (as Bog caps its gain).
   - Bandit Paymaster's kill and gain run in one optional batch: the banks differ, and the gain is best effort.
   - Ballot Box's kill and `Replace` run in one optional batch, the kill first (spec, "Seams used for the first time").
6. **Amounts that come to zero.** Bandit Paymaster with an empty supply writes "gained 0 warbands". Great Feast on a full track writes "gained 0 Supply". The spec gives no other key. If this is wrong, add a variant key.
7. **Levelers** moves 2 favor, or all the source holds, with no amount question.
8. **Plague Engines' seat order** starts at the acting player and follows `current.players`. The order only decides the order of the lines.
9. **Single options run unasked** where the rulings say so: Memory of Home's one stocked bank, Firebrand's one option, and Levelers' one source or destination. Taming Charm and Great Feast always ask when a card is on offer, as Armed Mob and Bog do.
10. **Storyteller's secret** is a plain `Move` from the shared bank onto the Darkest Secret. The shared bank's secrets are unbounded.
11. **Firebrand's options** are each stocked bank in suit order, then the burn button. The burn is offered only when the People's Favor holds favor.
12. **Ballot Box's match** reads the player's faceup denizen advisers against the faceup denizens and the edifices, on either face, at Ballot Box's site. The `used` line comes before the `removed` line.
13. **Dazzle is unchanged.** Its region discard is refactor P7, in slice 4. `SiteCards` reads one site.

## File Structure

All paths are under `src/main/scala/oathdigital/gameplay/powers/` or `src/test/scala/oathdigital/gameplay/powers/` unless given in full.

| File | Responsibility | Task |
|---|---|---|
| `action/FavorSplit.scala` (create) | P8: a favor move out of a set of banks, split by the player when there is a choice | 1 |
| `action/Alchemist.scala` (modify) | Uses `FavorSplit`; behavior and suite unchanged | 1 |
| `action/MemoryOfNature.scala` (create) | Memory of Nature | 1 |
| `action/WorldActionPowers.scala` (create) | Registers every power of this slice | 1 to 11 |
| `PhasePowerCatalog.scala` (modify) | Calls `WorldActionPowers.forCatalog(catalog)` | 1 |
| `action/Storyteller.scala` (create) | Storyteller | 2 |
| `action/Firebrand.scala` (create) | Firebrand | 3 |
| `frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala` (modify) | A mixed bank and button choice | 3 |
| `action/BankMoves.scala` (create) | A favor move between two banks, and its line | 4 |
| `action/Levelers.scala` (create) | Levelers | 4 |
| `action/MemoryOfHome.scala` (create) | Memory of Home | 5 |
| `action/PlagueEngines.scala` (create) | Plague Engines | 6 |
| `action/BanditPaymaster.scala` (create) | Bandit Paymaster | 7 |
| `PlayerFacts.scala` (modify) | `banked`: the warbands left in a bank | 8 |
| `action/BallotBox.scala` (create) | Ballot Box | 8 |
| `action/SiteCards.scala` (create) | The discardable cards of given suits at a site, the question over them, and their standard discard | 9 |
| `action/DarkEnforcer.scala` (create) | Dark Enforcer | 9 |
| `action/TamingCharm.scala` (create) | Taming Charm | 10 |
| `action/GreatFeast.scala` (create) | Great Feast | 11 |
| `action/<Card>Suite.scala` (create) | One suite per card | 1 to 11 |
| `docs/ROADMAP.md` (modify) | Records the slice | 12 |

Every new suite drives the power through `TargetsFixture` (`use`, `answer`, `after`, `awaits`, `offered`, `pick`, `replayed`, `usableNow`, `parked`). It stages with `Table` and reads with `Look`. `TargetsFixture.use` acts as `p1`. `Table.start` is the quiet table: every site empty with no forces and no cards, and no player holds an adviser. p1 stands at Ancient City (capacity 3), p2 at Broken Peaks and p3 at Buried Giant, each with 1 favor, 1 faceup secret and 3 warbands, in p1's Act. A site a command leaves empty is refilled with bandits after the command. `SearchFixture.denizensOf(suit)` gives plain denizens of a suit from the world deck.

Suit order is `Suit.all`: Discord, Arcane, Order, Hearth, Beast, Nomad. A suit's wire key is its lower-case name, and `NoteText` renders a bank as "the Order bank".

---

### Task 1: `FavorSplit` (P8), Memory of Nature, and `WorldActionPowers`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/FavorSplit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MemoryOfNature.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MemoryOfNatureSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.distribution`, `PowerAnswers.missing`, `DecisionQuery.Distribute.exactly`, `CatalogCards.denizen`, `catalog.suitOf`, `NoteSupport.gainNote`.
- Produces:
  - `private[powers] final class FavorSplit(decisionId: String, sources: Vector[Suit], confirmLabel: String)` with `stocked(ready): Vector[(Suit, Int)]`, `ask(ready, player, amount: Int, heading: String): Vector[Operation]` and `split(ready, pending, amount: Int): Either[OathViolation, Vector[(Suit, Int)]]`. Town Meeting uses it in slice 4.
  - `MemoryOfNature` (`decisionId`, `moved`, `unmoved`, `forCatalog`).
  - `WorldActionPowers.forCatalog(catalog: ExecutableCatalog): Vector[PhasePower]`, which later tasks extend.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/MemoryOfNatureSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class MemoryOfNatureSuite extends munit.FunSuite:
  import TargetsFixture._

  private val nature = CatalogNames.denizen("Memory of Nature")
  private val source = DecisionOptionRef.Denizen(nature)
  private val power = MemoryOfNature.forCatalog(catalog).get
  private val beasts = SearchFixture.denizensOf(Suit.Beast)
    .filterNot(_ == nature)
  private val beastEdifice =
    EdificeId(catalog.edifices.find(_.suit == Suit.Beast).get.id.value)
  private val acting = Table.start.turn(p1, Phase.Act)

  /** `table` with the banks holding exactly `favor`. */
  private def withBanks(table: Table, favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(table) {
      case (staged, (suit, n)) => staged.bankFavor(suit, n) }

  private def bank(ready: ReadyGame, suit: Suit) =
    ready.banks.favor.getOrElse(suit, 0)
  private def rows(amounts: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    amounts.toVector.map { case (suit, n) =>
      DistributeAmount(DecisionOptionRef.FavorBank(suit), n) })

  test("it places a secret and moves one favor per Beast card, an " +
      "edifice on either face included"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(beasts(0), at = Table.homeOf(p1))
      .edifice(beastEdifice, EdificeSide.Intact, at = Table.homeOf(p2)),
      Suit.Arcane -> 5).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(nature), Tokens(0, 1))
    assertEquals((bank(end, Suit.Arcane), bank(end, Suit.Beast)), (3, 2))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor to the Beast bank.", covers = false)))

  test("it counts itself at a site, and a facedown card not at all"):
    val ready = withBanks(acting.denizen(nature, at = Table.homeOf(p1))
      .denizen(beasts(0), at = Table.homeOf(p2), facedown = true),
      Suit.Arcane -> 5).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(bank(after(t), Suit.Beast), 1)

  test("several banks holding more than X ask for the split"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(beasts(0), at = Table.homeOf(p1))
      .denizen(beasts(1), at = Table.homeOf(p2)),
      Suit.Arcane -> 2, Suit.Order -> 2).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, MemoryOfNature.decisionId),
      parked.parkedDecision(t.state).toString)
    val done = answer(t, p1, MemoryOfNature.decisionId,
      rows(Suit.Arcane -> 1, Suit.Order -> 1)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(done), _)), Vector(1, 1, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the other banks holding X or less give all they hold, unasked, " +
      "and the Beast bank's own favor stays"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(beasts(0), at = Table.homeOf(p1))
      .denizen(beasts(1), at = Table.homeOf(p1))
      .denizen(beasts(2), at = Table.homeOf(p2)),
      Suit.Arcane -> 1, Suit.Order -> 1, Suit.Beast -> 4).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(t), _)), Vector(0, 0, 6))

  test("with no Beast card nothing moves, and the line says so"):
    val ready = withBanks(acting.adviser(p1, nature), Suit.Arcane -> 3).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(bank(after(t), Suit.Arcane), 3)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "No favor moved to the Beast bank.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MemoryOfNatureSuite"`
Expected: compile error, `MemoryOfNature` not found.

- [ ] **Step 3: Extract `FavorSplit` from Alchemist**

Create `src/main/scala/oathdigital/gameplay/powers/action/FavorSplit.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** A move of `amount` favor out of `sources`, the favor banks it may come
  * from, for Alchemist, Memory of Nature and Town Meeting (catalog batch 3,
  * refactor P8).
  *
  * The player splits the favor only when there is a choice: two or more of
  * the banks hold favor and more than `amount` is available in all.
  * Otherwise every legal answer moves the same favor, so none is asked,
  * which is also what `DecisionQueries.wellFormed` requires of a
  * `Distribute`. The question is asked from a live `Branch`, read after the
  * cost is paid, and `split` recomputes the same condition from live state.
  */
private[powers] final class FavorSplit(decisionId: String,
    sources: Vector[Suit], confirmLabel: String):

  /** The source banks that hold favor, with their stock, in suit order. */
  def stocked(ready: ReadyGame): Vector[(Suit, Int)] =
    sources.map(suit => suit -> ready.banks.favor.getOrElse(suit, 0))
      .filter(_._2 > 0)

  private def choosing(banks: Vector[(Suit, Int)], amount: Int): Boolean =
    amount > 0 && banks.size >= 2 && banks.map(_._2).sum > amount

  /** The question, when there is a choice. */
  def ask(ready: ReadyGame, player: PlayerId, amount: Int, heading: String)
      : Vector[Operation] =
    val banks = stocked(ready)
    if !choosing(banks, amount) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      banks.map { case (suit, stock) => DistributeSlot(
        DecisionOptionRef.FavorBank(suit), 0, math.min(stock, amount), None) },
      total = amount,
      heading = Some(heading),
      confirmLabel = confirmLabel)))

  /** The favor each bank gives: the answer when one was asked, otherwise
    * all of it, up to `amount`, from each stocked bank. */
  def split(ready: ReadyGame, pending: PendingTree, amount: Int)
      : Either[OathViolation, Vector[(Suit, Int)]] =
    val banks = stocked(ready)
    if amount <= 0 then Right(Vector.empty)
    else if !choosing(banks, amount) then
      Right(banks.map { case (suit, stock) => suit -> math.min(stock, amount) })
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).map(_.collect {
        case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) if n > 0 =>
          suit -> n })
```

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Alchemist (card 9), ACTION: place 1 secret on this card and burn 1, then
  * gain 4 favor from any bank or banks.
  *
  * The split is [[FavorSplit]]'s: the player chooses it only when two or
  * more banks hold favor and more than 4 is available in all.
  */
case object Alchemist extends PaidAction("denizen.alchemist",
    Cost(secret = 1, secretBurnt = 1)):
  val Favor: Int = 4
  val decisionId: String = "power.alchemist.banks"
  /** Its own line: the whole favor it gained. Each bank's Gain line stays. */
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  private val banks = new FavorSplit(decisionId, Suit.all, "Take favor")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => banks.ask(live, player, Favor,
      "Alchemist: take 4 favor from any banks")),
    BuildOps((live, pending) => banks.split(live, pending, Favor).map(
      _.map { case (suit, n) => Gain.Favor(player, suit, n) })),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Favor,
      NoteSupport.favor)))))
```

`testOnly` compiles every test source, so the new suite blocks a run until Step 4 is done. Step 5 runs Alchemist's suite beside the new one.

- [ ] **Step 4: Write Memory of Nature and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/MemoryOfNature.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Memory of Nature (card 191), ACTION: place 1 secret on this card, then
  * move a total of X favor from any favor banks to the Beast bank, where X
  * is the number of Beast cards on the map.
  *
  * X counts the faceup Beast denizens and the Beast edifices, on either
  * face, at the sites in play: Memory of Nature itself when it stands at a
  * site. The favor comes from the five other banks, X or all they hold. The
  * player splits it only when there is a choice ([[FavorSplit]]). Its line
  * reads what the Beast bank gained.
  */
final case class MemoryOfNature private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.memory-of-nature", Cost(secret = 1)):
  import MemoryOfNature._

  override def noteKeys: Vector[NoteKey] = Vector(moved, unmoved)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch { (live, _) =>
      val x = beasts(live)
      banks.ask(live, player, x,
        s"Memory of Nature: move $x favor to the Beast bank")
    },
    BuildOps((live, pending) => banks.split(live, pending, beasts(live)).map(
      _.map { case (suit, n) => Move(Piece.Favor(n),
        PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.FavorBank(Suit.Beast))) })),
    Note(this.id, movedNote(_, source)))))

  /** The Beast cards on the map: faceup denizens, and edifices on either
    * face, at the sites in play. */
  private def beasts(ready: ReadyGame): Int =
    val map = ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case EdificeState(card, _, _) => catalog.suitOf(card).contains(Suit.Beast)
      case _ => false
    }

  /** What the Beast bank gained in the step before the note. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map { card =>
    val gained = states.previous.fold(0)((before, after) =>
      after.banks.favor.getOrElse(Suit.Beast, 0) -
        before.banks.favor.getOrElse(Suit.Beast, 0))
    if gained > 0 then moved(card, NoteArg.Amount(gained, NoteUnit.Favor),
      NoteArg.Bank(Suit.Beast))
    else unmoved(card, NoteArg.Bank(Suit.Beast))
  }

object MemoryOfNature:
  val id: PowerId = PowerId("denizen.memory-of-nature")
  val decisionId: String = "power.memory-of-nature.banks"
  /** "Moved {n favor} to {the Beast bank}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No favor moved to {the Beast bank}." */
  val unmoved: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No favor moved to "), NotePart.Arg(0), NotePart.Text(".")))
  private val banks = new FavorSplit(decisionId,
    Suit.all.filterNot(_ == Suit.Beast), "Move favor")

  def forCatalog(catalog: ExecutableCatalog): Option[MemoryOfNature] =
    CatalogCards.denizen(catalog, id).map(_ => new MemoryOfNature(catalog))
```

Create `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 3's slice 3c, actions on sites, banks
  * and banners, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[OtherActionPowers]]. A power that needs the catalog is omitted when
  * its card is absent.
  */
object WorldActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    MemoryOfNature.forCatalog(catalog).toVector
```

Edit `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` with Python (macOS `sed -i` needs an extension argument):

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala")
s = p.read_text()
old_import = "OtherActionPowers, SelfActionPowers, TargetPowers, WaysideInn}"
new_import = ("OtherActionPowers, SelfActionPowers, TargetPowers, WaysideInn,\n"
              "  WorldActionPowers}")
old_use = "      OtherActionPowers.forCatalog(catalog) ++\n"
new_use = old_use + "      WorldActionPowers.forCatalog(catalog) ++\n"
assert s.count(old_import) == 1 and s.count(old_use) == 1
p.write_text(s.replace(old_import, new_import).replace(old_use, new_use))
PY
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MemoryOfNatureSuite oathdigital.gameplay.powers.action.AlchemistSuite"`
Expected: PASS, 5 new tests and Alchemist's unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/FavorSplit.scala \
  src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala \
  src/main/scala/oathdigital/gameplay/powers/action/MemoryOfNature.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/action/MemoryOfNatureSuite.scala
git commit -m "feat(powers): add Memory of Nature and share Alchemist's favor split"
```

### Task 2: Storyteller

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Storyteller.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/StorytellerSuite.scala`

**Interfaces:**
- Consumes: `BannerRules.resources(current, banner): Int`, `WorldActionPowers.forCatalog` (Task 1).
- Produces: `Storyteller` (`Placed`, `placed`).

This task tests a seam for the first time: a `Move` of secrets from the shared bank onto a banner, held or unheld.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/StorytellerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class StorytellerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val teller = CatalogNames.denizen("Storyteller")
  private val source = DecisionOptionRef.Denizen(teller)

  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, teller).favor(p1, favor)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  test("it places a favor, then a secret from the shared bank on a held " +
      "Darkest Secret"):
    val ready = staged().darkestSecret(Some(p2), 1).ready
    val t = use(ready, Storyteller, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(teller), Tokens(1, 0))
    assertEquals(onBanner(end), 2)
    assertEquals(end.game.current.banners.darkestSecret.holder, Some(p2))
    assertEquals(NoteText.said(Storyteller, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} placed 1 secret on the Darkest Secret.",
      covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("a Darkest Secret nobody holds takes the secret too"):
    val t = use(staged().darkestSecret(None, 0).ready, Storyteller, source)
      .toOption.get
    assertEquals(onBanner(after(t)), 1)

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == Storyteller.id))
    assert(use(broke, Storyteller, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.StorytellerSuite"`
Expected: compile error, `Storyteller` not found.

- [ ] **Step 3: Write Storyteller and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/Storyteller.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Storyteller (card 52), ACTION: place 1 favor on this card, then place 1
  * secret from the shared bank on the Darkest Secret.
  *
  * The secret moves onto the banner wherever it is, held by a player or by
  * nobody. The shared bank's secrets are unbounded, so the move always
  * happens. Its line reads what the banner gained.
  */
case object Storyteller extends PaidAction("denizen.storyteller",
    Cost(favor = 1)):
  val Placed: Int = 1
  /** "{Red} placed {1 secret} on the {Darkest Secret}." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on the "),
    NotePart.Arg(2), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(placed)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Move(Piece.Secrets(Placed), PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))),
    Note(id, placedNote(_, player, source)))))

  /** What the Darkest Secret gained in the step before the note. */
  private def placedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    (before, after) <- states.previous
    gained = BannerRules.resources(after.game.current, Banner.DarkestSecret) -
      BannerRules.resources(before.game.current, Banner.DarkestSecret)
    if gained > 0
  yield placed(card, NoteArg.Player(player),
    NoteArg.Amount(gained, NoteUnit.Secret),
    NoteArg.Banner(Banner.DarkestSecret))
```

In `WorldActionPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[PhasePower](Storyteller) ++
      MemoryOfNature.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.StorytellerSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Storyteller.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/StorytellerSuite.scala
git commit -m "feat(powers): add Storyteller"
```

### Task 3: Firebrand

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Firebrand.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/FirebrandSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.bankPaid(step): Option[Suit]`, `PowerAnswers.one`.
- Produces: `Firebrand` (`decisionId`, `burn: DecisionOptionRef.Button`, `moved`, `burned`, `empty`).

This task tests two seams for the first time: a `Move` from a bank onto a banner with a `Burn` from it, and a mixed `ChooseOne` of favor banks and a button, in the engine and in the frontend panel. The frontend routes any choose-one with a `decide` kind to `WalkerPanelSupport.renderChooseOnePanel`, which draws each option by its kind, so it needs no production change.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/action/FirebrandSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class FirebrandSuite extends munit.FunSuite:
  import TargetsFixture._

  private val brand = CatalogNames.denizen("Firebrand")
  private val source = DecisionOptionRef.Denizen(brand)

  /** p1's Act holding Firebrand, the banks holding exactly `favor`, and the
    * People's Favor unheld with `banner` favor. */
  private def staged(banner: Int, favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, brand).peoplesFavor(None, banner)) {
      case (table, (suit, n)) => table.bankFavor(suit, n) }

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor
  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  test("it places a secret and offers each stocked bank, then the burn"):
    val t = use(staged(1, Suit.Order -> 2, Suit.Hearth -> 1).ready,
      Firebrand, source).toOption.get
    assert(awaits(t, Firebrand.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(brand), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("favor-bank" -> "order",
      "favor-bank" -> "hearth", "button" -> "burn")))

  test("a bank adds 1 favor to the People's Favor"):
    val ready = staged(1, Suit.Order -> 2, Suit.Hearth -> 1).ready
    val t = use(ready, Firebrand, source).toOption.get
    val done = answer(t, p1, Firebrand.decisionId,
      pick(DecisionOptionRef.FavorBank(Suit.Order))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals((bank(end, Suit.Order), onBanner(end)), (1, 2))
    assertEquals(NoteText.said(Firebrand, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} moved 1 favor from the Order bank to the People's Favor.",
      covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the burn takes 1 favor from the People's Favor"):
    val ready = staged(1, Suit.Order -> 2).ready
    val t = use(ready, Firebrand, source).toOption.get
    val done = answer(t, p1, Firebrand.decisionId, pick(Firebrand.burn))
      .toOption.get
    val end = after(done)
    assertEquals((bank(end, Suit.Order), onBanner(end)), (2, 0))
    assertEquals(NoteText.said(Firebrand, done.events), Vector(NoteText.Said(
      "used.burned", s"${p1.value} burned 1 favor from the People's Favor.",
      covers = false)))

  test("a single option runs without asking, whoever holds the banner"):
    val ready = staged(2).peoplesFavor(Some(p2), 2).ready
    val t = use(ready, Firebrand, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(onBanner(after(t)), 1)

  test("with nothing to move or burn the cost stays paid, and the line " +
      "says so"):
    val t = use(staged(0).ready, Firebrand, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(brand), Tokens(0, 1))
    assertEquals(NoteText.said(Firebrand, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank and the People's Favor were empty.",
      covers = false)))
```

Append this test to `frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala`, after the test "a bank offered as a choice carries its symbol too":

```scala

  test("a bank offered beside a button keeps its symbol, and the button " +
      "has none"):
    val query = DecisionQueryState.ChooseOne(
      Vector(DecisionOptionState("favor-bank", "order", "Order"),
        DecisionOptionState("button", "burn",
          "Burn 1 favor from the People's Favor")),
      Some("Firebrand: add 1 favor to the People's Favor from a bank, or " +
        "burn 1 from it"))
    val parked = WalkerDecisionState("use-power", "power.firebrand.choice",
      "decide", query = Some(query))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      ParkedDecision.Surface.ChooseOne(parked, query),
      GameProjection("game", 9L, "act", Some("red"), Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false),
      canControl = true, panel, new RecordingControls())
    val choices = panel.querySelectorAll(".walker-choice").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(choices.map(_.textContent),
      Vector("Order", "Burn 1 favor from the People's Favor"))
    assertEquals(choices.map(glyphs(_).size), Vector(1, 0))
```

- [ ] **Step 2: Run them to verify the server suite fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FirebrandSuite" "frontend/testOnly oathdigital.frontend.SuitGlyphSuite"`
Expected: the server suite fails to compile, `Firebrand` not found. The frontend suite passes already: this is the "no panel change" check. If it fails, stop and report; the spec drops Firebrand under the bar when the panel needs a change.

- [ ] **Step 3: Write Firebrand and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/Firebrand.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Firebrand (card 233), ACTION: place 1 secret on this card, then add 1
  * favor to the People's Favor from any favor bank, or burn 1 favor from
  * the People's Favor.
  *
  * The options, read live after the cost, are each bank that holds favor,
  * in suit order, then a burn button when the People's Favor holds favor.
  * Who holds the banner, or nobody, does not matter. A single option runs
  * without asking. With none, the cost stays paid and one line says so.
  * The choice is narrated: the line names the bank or the burn.
  */
case object Firebrand extends PaidAction("denizen.firebrand", Cost(secret = 1)):
  val decisionId: String = "power.firebrand.choice"
  val Moved: Int = 1
  val burn: DecisionOptionRef.Button = DecisionOptionRef.Button("burn")
  /** "{Red} moved {1 favor} from {the Order bank} to the {People's Favor}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" moved "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(" to the "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Red} burned {1 favor} from the {People's Favor}." */
  val burned: NoteKey = NoteKey("used.burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" from the "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Every favor bank and the People's Favor were empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Text(
    "Every favor bank and the People's Favor were empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, burned, empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val banner = PositionedLocation(
    Location.OnBanner(Banner.PeoplesFavor))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => Right(effect(live, pending))),
    Note(id, firedNote(_, player, source)))))

  private def favorOn(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** The choices, in order: each stocked bank, then the burn. */
  private def options(ready: ReadyGame): Vector[DecisionOptionRef] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)
      .map(DecisionOptionRef.FavorBank(_)) ++
      Option.when(favorOn(ready) > 0)(burn)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val found = options(ready)
    if found.size < 2 then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      found.map {
        case bank: DecisionOptionRef.FavorBank => DecisionOption.FavorBank(bank)
        case _ => DecisionOption.Button(burn,
          "Burn 1 favor from the People's Favor")
      },
      heading = Some("Firebrand: add 1 favor to the People's Favor from a " +
        "bank, or burn 1 from it"))))

  /** The only option, or the answer. */
  private def chosen(ready: ReadyGame, pending: PendingTree)
      : Option[DecisionOptionRef] = options(ready) match
    case Vector(only) => Some(only)
    case _ => PowerAnswers.one(pending, decisionId)

  private def effect(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, pending) match
    case Some(DecisionOptionRef.FavorBank(suit)) => Vector(Move(
      Piece.Favor(Moved), PositionedLocation(Location.FavorBank(suit)),
      banner))
    case Some(`burn`) => Vector(Burn.favor(Moved, banner))
    case _ => Vector.empty

  /** What the People's Favor gained or lost in the step before the note.
    * No change means nothing was on offer. */
  private def firedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => states.previous match
      case Some(step @ (before, after)) =>
        (favorOn(after) - favorOn(before), NoteSupport.bankPaid(step)) match
          case (gained, Some(suit)) if gained > 0 => moved(card,
            NoteArg.Player(player), NoteArg.Amount(gained, NoteUnit.Favor),
            NoteArg.Bank(suit), NoteArg.Banner(Banner.PeoplesFavor))
          case (change, _) if change < 0 => burned(card,
            NoteArg.Player(player), NoteArg.Amount(-change, NoteUnit.Favor),
            NoteArg.Banner(Banner.PeoplesFavor))
          case _ => empty(card)
      case None => empty(card))
```

In `WorldActionPowers.scala`, change `Vector[PhasePower](Storyteller)` to `Vector[PhasePower](Storyteller, Firebrand)`.

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FirebrandSuite" "frontend/testOnly oathdigital.frontend.SuitGlyphSuite"`
Expected: PASS, 5 server tests and the frontend suite.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Firebrand.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/FirebrandSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala
git commit -m "feat(powers): add Firebrand"
```

### Task 4: `BankMoves` and Levelers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BankMoves.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Levelers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/LevelersSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`.
- Produces:
  - `private[action] object BankMoves` with `move(amount: Int, from: Suit, to: Suit): Move`, `moved: NoteKey` (key `used`) and `movedNote(card: PowerSourceRef)(states: NoteStates): Option[PowerNote]`. Task 5 uses it.
  - `Levelers` (`sourceDecisionId`, `destinationDecisionId`, `Moved`, `moved`, `empty`, `sources`, `destinations`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/LevelersSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class LevelersSuite extends munit.FunSuite:
  import TargetsFixture._

  private val levelers = CatalogNames.denizen("Levelers")
  private val source = DecisionOptionRef.Denizen(levelers)

  /** p1's Act holding Levelers, with the banks holding exactly `favor`. */
  private def staged(favor: (Suit, Int)*): ReadyGame =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, levelers)) { case (table, (suit, n)) =>
      table.bankFavor(suit, n) }.ready

  /** Every bank's favor, in suit order: Discord, Arcane, Order, Hearth,
    * Beast, Nomad. */
  private def banks(ready: ReadyGame): Vector[Int] =
    Suit.all.map(ready.banks.favor.getOrElse(_, 0))
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def offeredBanks(suits: Suit*) =
    Some(suits.toVector.map(suit => "favor-bank" -> suit.key))

  test("a single fullest and a single emptiest bank move 2 favor, unasked"):
    val ready = staged(Suit.Discord -> 2, Suit.Arcane -> 5, Suit.Hearth -> 2,
      Suit.Beast -> 2, Suit.Nomad -> 2)
    val t = use(ready, Levelers, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(levelers), Tokens(0, 1))
    assertEquals(banks(end), Vector(2, 3, 2, 2, 2, 2))
    assertEquals(NoteText.said(Levelers, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor from the Arcane bank to the Order bank.",
      covers = false)))

  test("tied banks are asked, the source first, then the destination"):
    val ready = staged(Suit.Arcane -> 4, Suit.Beast -> 4, Suit.Discord -> 1,
      Suit.Hearth -> 1, Suit.Order -> 2, Suit.Nomad -> 2)
    val t = use(ready, Levelers, source).toOption.get
    assert(awaits(t, Levelers.sourceDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), offeredBanks(Suit.Arcane, Suit.Beast))
    val second = answer(t, p1, Levelers.sourceDecisionId,
      pick(bank(Suit.Beast))).toOption.get
    assert(awaits(second, Levelers.destinationDecisionId),
      parked.parkedDecision(second.state).toString)
    assertEquals(offered(second, p1), offeredBanks(Suit.Discord, Suit.Hearth))
    val done = answer(second, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Hearth))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(banks(after(done)), Vector(1, 4, 2, 3, 2, 2))
    val events = t.events ++ second.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("a source holding 1 favor moves what it holds"):
    val t = use(staged(Suit.Arcane -> 1), Levelers, source).toOption.get
    assertEquals(offered(t, p1), offeredBanks(Suit.Discord, Suit.Order,
      Suit.Hearth, Suit.Beast, Suit.Nomad))
    val done = answer(t, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Order))).toOption.get
    assertEquals(banks(after(done)), Vector(0, 0, 1, 0, 0, 0))
    assertEquals(NoteText.said(Levelers, done.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 1 favor from the Arcane bank to the Order bank.",
      covers = false)))

  test("when every bank holds the same, any source, then any other bank"):
    val t = use(staged(Suit.all.map(_ -> 1)*), Levelers, source).toOption.get
    assertEquals(offered(t, p1), offeredBanks(Suit.all*))
    val second = answer(t, p1, Levelers.sourceDecisionId,
      pick(bank(Suit.Nomad))).toOption.get
    assertEquals(offered(second, p1), offeredBanks(Suit.Discord, Suit.Arcane,
      Suit.Order, Suit.Hearth, Suit.Beast))
    val done = answer(second, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Arcane))).toOption.get
    assertEquals(banks(after(done)), Vector(1, 2, 1, 1, 1, 0))

  test("with every bank empty nothing moves, and the line says so"):
    val t = use(staged(), Levelers, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(levelers), Tokens(0, 1))
    assertEquals(NoteText.said(Levelers, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank was empty.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.LevelersSuite"`
Expected: compile error, `Levelers` not found.

- [ ] **Step 3: Write `BankMoves` and Levelers, and register Levelers**

Create `src/main/scala/oathdigital/gameplay/powers/action/BankMoves.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** A move of favor from one favor bank to another, for Levelers and Memory
  * of Home: the move, and the line that reads it back. */
private[action] object BankMoves:
  def move(amount: Int, from: Suit, to: Suit): Move = Move(Piece.Favor(amount),
    PositionedLocation(Location.FavorBank(from)),
    PositionedLocation(Location.FavorBank(to)))

  /** "Moved {n favor} from {the Beast bank} to {the Arcane bank}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(".")))

  /** The bank that lost favor in the step before the note, how much, and
    * the bank that gained it. Nothing moved writes nothing. */
  def movedNote(card: PowerSourceRef)(states: NoteStates): Option[PowerNote] =
    for
      (before, after) <- states.previous
      from <- Suit.all.find(suit => stock(after, suit) < stock(before, suit))
      to <- Suit.all.find(suit => stock(after, suit) > stock(before, suit))
    yield moved(card, NoteArg.Amount(stock(before, from) - stock(after, from),
      NoteUnit.Favor), NoteArg.Bank(from), NoteArg.Bank(to))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)
```

Create `src/main/scala/oathdigital/gameplay/powers/action/Levelers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Levelers (card 135), ACTION: place 1 secret on this card, then move 2
  * favor from the favor bank with the most favor to the bank with the
  * least. You decide ties.
  *
  * The banks are read live, after the cost. The source is a bank holding
  * the most, and the destination another bank holding the least of the
  * rest. When every bank holds the same, that is any source and any other
  * bank. The player chooses among tied banks, the source first, then the
  * destination. A single candidate is taken without asking. The move takes
  * 2 favor, or all the source holds. With every bank empty, nothing moves
  * and one line says so. Both choices are narrated: the line names the
  * banks.
  */
case object Levelers extends PaidAction("denizen.levelers", Cost(secret = 1)):
  val sourceDecisionId: String = "power.levelers.source"
  val destinationDecisionId: String = "power.levelers.destination"
  val Moved: Int = 2
  val moved: NoteKey = BankMoves.moved
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, empty)
  override def narratedDecisions: Set[String] =
    Set(sourceDecisionId, destinationDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askSource(live, player, source)),
    Branch((live, pending) => askDestination(live, player, pending)),
    BuildOps((live, pending) => Right(level(live, pending))),
    Note(id, note(_, source)))))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  /** The banks holding the most favor, in suit order: every bank when all
    * hold the same, and none when all are empty. */
  def sources(ready: ReadyGame): Vector[Suit] =
    val most = Suit.all.map(stock(ready, _)).max
    if most == 0 then Vector.empty
    else Suit.all.filter(stock(ready, _) == most)

  /** The banks other than `from` holding the least favor of the rest, in
    * suit order. */
  def destinations(ready: ReadyGame, from: Suit): Vector[Suit] =
    val others = Suit.all.filterNot(_ == from)
    val least = others.map(stock(ready, _)).min
    others.filter(stock(ready, _) == least)

  private def choice(decisionId: String, player: PlayerId, banks: Vector[Suit],
      heading: String): Vector[Operation] = Vector(Decide(decisionId, player,
    DecisionQuery.ChooseOne(banks.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(heading))))

  private def askSource(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Vector[Operation] = sources(ready) match
    case Vector() =>
      Vector(Note(id, _ => PowerSourceRef.of(source).map(empty(_))))
    case Vector(_) => Vector.empty
    case several => choice(sourceDecisionId, player, several,
      "Levelers: move 2 favor from a bank holding the most")

  private def answered(pending: PendingTree, decisionId: String): Option[Suit] =
    PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.FavorBank(suit) => suit }

  /** The source: the only candidate, or the answer. */
  private def chosenSource(ready: ReadyGame, pending: PendingTree)
      : Option[Suit] = sources(ready) match
    case Vector(only) => Some(only)
    case _ => answered(pending, sourceDecisionId)

  private def askDestination(ready: ReadyGame, player: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosenSource(ready, pending).map(destinations(ready, _)) match
      case Some(several) if several.size >= 2 => choice(destinationDecisionId,
        player, several, "Levelers: move it to a bank holding the least")
      case _ => Vector.empty

  /** The destination: the only candidate, or the answer. */
  private def chosenDestination(ready: ReadyGame, from: Suit,
      pending: PendingTree): Option[Suit] = destinations(ready, from) match
    case Vector(only) => Some(only)
    case _ => answered(pending, destinationDecisionId)

  private def level(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = (for
    from <- chosenSource(ready, pending)
    to <- chosenDestination(ready, from, pending)
    amount = math.min(Moved, stock(ready, from))
    if amount > 0
  yield BankMoves.move(amount, from, to)).toVector

  /** The move's line. With every bank empty, the source's `Branch` already
    * wrote the empty line, and no bank changed. */
  private def note(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(BankMoves.movedNote(_)(states))
```

In `WorldActionPowers.scala`, change `Vector[PhasePower](Storyteller, Firebrand)` to `Vector[PhasePower](Storyteller, Firebrand, Levelers)`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.LevelersSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/BankMoves.scala \
  src/main/scala/oathdigital/gameplay/powers/action/Levelers.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/LevelersSuite.scala
git commit -m "feat(powers): add Levelers"
```

### Task 5: Memory of Home

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/MemoryOfHome.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MemoryOfHomeSuite.scala`

**Interfaces:**
- Consumes: `BankMoves.move`, `BankMoves.moved`, `BankMoves.movedNote` (Task 4), `PowerAnswers.one`.
- Produces: `MemoryOfHome` (`decisionId`, `moved`, `empty`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/MemoryOfHomeSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class MemoryOfHomeSuite extends munit.FunSuite:
  import TargetsFixture._

  private val memory = CatalogNames.denizen("Memory of Home")
  private val source = DecisionOptionRef.Denizen(memory)

  /** p1's Act holding Memory of Home with two secrets, the banks holding
    * exactly `favor`. */
  private def staged(favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, memory).secrets(p1, 2)) { case (table, (suit, n)) =>
      table.bankFavor(suit, n) }

  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  test("one stocked bank is emptied into the Hearth bank, unasked"):
    val ready = staged(Suit.Order -> 3, Suit.Hearth -> 1).ready
    val t = use(ready, MemoryOfHome, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(memory), Tokens(0, 1))
    assertEquals(Look(end).faceUpSecrets(p1), 0)
    assertEquals((bank(end, Suit.Order), bank(end, Suit.Hearth)), (0, 4))
    assertEquals(NoteText.said(MemoryOfHome, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 3 favor from the Order bank to the Hearth bank.",
      covers = false)))

  test("several stocked banks are asked, the Hearth bank not among them"):
    val ready = staged(Suit.Arcane -> 2, Suit.Nomad -> 1, Suit.Hearth -> 5)
      .ready
    val t = use(ready, MemoryOfHome, source).toOption.get
    assert(awaits(t, MemoryOfHome.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("favor-bank" -> "arcane",
      "favor-bank" -> "nomad")))
    val done = answer(t, p1, MemoryOfHome.decisionId,
      pick(DecisionOptionRef.FavorBank(Suit.Nomad))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Vector(Suit.Arcane, Suit.Nomad, Suit.Hearth).map(bank(end, _)),
      Vector(2, 0, 6))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("with only the Hearth bank stocked nothing moves, and the line " +
      "says so"):
    val t = use(staged(Suit.Hearth -> 4).ready, MemoryOfHome, source)
      .toOption.get
    assertEquals(bank(after(t), Suit.Hearth), 4)
    assertEquals(NoteText.said(MemoryOfHome, t.events), Vector(NoteText.Said(
      "used.empty", "Every other favor bank was empty.", covers = false)))

  test("it is unusable with a single secret"):
    val short = staged(Suit.Order -> 3).secrets(p1, 1).ready
    assert(!usableNow(short).exists(_.power.id == MemoryOfHome.id))
    assert(use(short, MemoryOfHome, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MemoryOfHomeSuite"`
Expected: compile error, `MemoryOfHome` not found.

- [ ] **Step 3: Write Memory of Home and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/MemoryOfHome.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Memory of Home (card 49), ACTION: place 1 secret on this card and burn
  * 1, then move all the favor from any one favor bank to the Hearth bank.
  *
  * The banks offered are those other than Hearth that hold favor, read live
  * after the cost. One stocked bank is taken without asking. With none,
  * nothing moves and one line says so. The choice is narrated: the line
  * names the bank.
  */
case object MemoryOfHome extends PaidAction("denizen.memory-of-home",
    Cost(secret = 1, secretBurnt = 1)):
  val decisionId: String = "power.memory-of-home.bank"
  val moved: NoteKey = BankMoves.moved
  /** "Every other favor bank was empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every other favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => Right(gather(live, pending))),
    Note(id, note(_, source)))))

  /** The banks other than Hearth that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => suit != Suit.Hearth &&
      ready.banks.favor.getOrElse(suit, 0) > 0)

  private def ask(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Vector[Operation] = stocked(ready) match
    case Vector() =>
      Vector(Note(id, _ => PowerSourceRef.of(source).map(empty(_))))
    case Vector(_) => Vector.empty
    case several => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      several.map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some("Memory of Home: move all of a bank's favor to the " +
        "Hearth bank"))))

  /** The only stocked bank, or the answer. */
  private def chosen(ready: ReadyGame, pending: PendingTree): Option[Suit] =
    stocked(ready) match
      case Vector(only) => Some(only)
      case _ => PowerAnswers.one(pending, decisionId).collect {
        case DecisionOptionRef.FavorBank(suit) => suit }

  private def gather(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, pending).toVector.map(suit =>
    BankMoves.move(ready.banks.favor.getOrElse(suit, 0), suit, Suit.Hearth))

  /** The move's line. With no stocked bank, the `Branch` already wrote the
    * empty line, and no bank changed. */
  private def note(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(BankMoves.movedNote(_)(states))
```

In `WorldActionPowers.scala`, change `Vector[PhasePower](Storyteller, Firebrand, Levelers)` to `Vector[PhasePower](Storyteller, Firebrand, Levelers, MemoryOfHome)`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MemoryOfHomeSuite"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/MemoryOfHome.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/MemoryOfHomeSuite.scala
git commit -m "feat(powers): add Memory of Home"
```

### Task 6: Plague Engines

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/PlagueEngines.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/PlagueEnginesSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.ruledSites(ready, player): Set[SiteId]`, `NoteSupport.board`, `NoteSupport.favor`.
- Produces: `PlagueEngines` (`paid`, `nobody`).

A `Give` by a player who is not active was first used by Whispering Leaves (slice 3b). Here it gives into a bank.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/PlagueEnginesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class PlagueEnginesSuite extends munit.FunSuite:
  import TargetsFixture._

  private val engines = CatalogNames.denizen("Plague Engines")
  private val source = DecisionOptionRef.Denizen(engines)

  /** p1's Act holding Plague Engines with two secrets, the Arcane bank
    * empty. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .adviser(p1, engines).secrets(p1, 2).bankFavor(Suit.Arcane, 0)

  private def arcane(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Arcane, 0)

  test("each player puts 1 favor per site they rule into the Arcane bank, " +
      "the user first"):
    val ready = staged.favor(p1, 5).favor(p2, 3)
      .warbandsAt(Table.homeOf(p1), p1, 1).warbandsAt(Table.homeOf(p3), p1, 1)
      .warbandsAt(Table.homeOf(p2), p2, 1).ready
    val t = use(ready, PlagueEngines, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(engines), Tokens(0, 1))
    assertEquals(Vector(p1, p2, p3).map(Look(end).favor), Vector(3, 2, 1))
    assertEquals(arcane(end), 3)
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} put 2 favor into the Arcane bank.", covers = false),
      NoteText.Said(NoteKey.Used,
        s"${p2.value} put 1 favor into the Arcane bank.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("a player short of favor gives all they have"):
    val ready = staged.favor(p2, 1)
      .warbandsAt(Table.homeOf(p2), p2, 1).warbandsAt(Table.homeOf(p3), p2, 1)
      .ready
    val t = use(ready, PlagueEngines, source).toOption.get
    assertEquals((Look(after(t)).favor(p2), arcane(after(t))), (0, 1))
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p2.value} put 1 favor into the Arcane bank.", covers = false)))

  test("with no site ruled nothing moves, and the line says so"):
    val t = use(staged.ready, PlagueEngines, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(arcane(after(t)), 0)
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said("used.none", "No player put favor into the Arcane bank.",
        covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PlagueEnginesSuite"`
Expected: compile error, `PlagueEngines` not found.

- [ ] **Step 3: Write Plague Engines and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/PlagueEngines.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Plague Engines (card 65), ACTION: place 1 secret on this card and burn
  * 1, then each player, even you, places 1 favor per site they rule into
  * the Arcane bank.
  *
  * In seat order from the player, each player gives from their board 1
  * favor per site they rule, or all they have, with a `Give`. Nothing is
  * asked. One line per player who paid; with none, one line says so.
  */
case object PlagueEngines extends PaidAction("denizen.plague-engines",
    Cost(secret = 1, secretBurnt = 1)):
  /** "{Blue} put {n favor} into {the Arcane bank}." */
  val paid: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1), NotePart.Text(" into "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No player put favor into {the Arcane bank}." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player put favor into "), NotePart.Arg(0),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(paid, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val order = seated(ready, player)
    Right(Sequence(Vector[Operation](
      BuildOps((live, _) => Right(order.flatMap(give(live, _))))) ++
      order.map(payer => Note(id, paidNote(_, payer, source))) :+
      Note(id, nobodyNote(_, source))))

  /** Every player in seat order, from `player`. */
  private def seated(ready: ReadyGame, player: PlayerId): Vector[PlayerId] =
    val all = ready.game.current.players.map(_.player)
    val at = all.indexOf(player).max(0)
    all.drop(at) ++ all.take(at)

  private def give(ready: ReadyGame, payer: PlayerId): Option[CoreOperation] =
    val owed = PowerAccess.ruledSites(ready, payer).size
      .min(NoteSupport.board(ready, payer).fold(0)(_.favor))
    Option.when(owed > 0)(Give(Piece.Favor(owed), payer,
      Location.PlayArea(payer), Location.FavorBank(Suit.Arcane)))

  /** What `payer` gave in the step before the note. */
  private def paidNote(states: NoteStates, payer: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    gave = -NoteSupport.favor(step, payer)
    if gave > 0
  yield paid(card, NoteArg.Player(payer), NoteArg.Amount(gave, NoteUnit.Favor),
    NoteArg.Bank(Suit.Arcane))

  /** The line when the Arcane bank gained nothing in the step before it. */
  private def nobodyNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    if states.previous.forall((before, after) =>
      after.banks.favor.getOrElse(Suit.Arcane, 0) <=
        before.banks.favor.getOrElse(Suit.Arcane, 0))
  yield nobody(card, NoteArg.Bank(Suit.Arcane))
```

In `WorldActionPowers.scala`, change the `Vector[PhasePower](...)` list to `Vector[PhasePower](Storyteller, Firebrand, Levelers, MemoryOfHome, PlagueEngines)`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PlagueEnginesSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/PlagueEngines.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/PlagueEnginesSuite.scala
git commit -m "feat(powers): add Plague Engines"
```

### Task 7: Bandit Paymaster

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BanditPaymaster.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BanditPaymasterSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite`, `PlayerFacts.forceKind`, `NoteSupport.warbands`.
- Produces: `BanditPaymaster` (`Removed`, `Gained`, `paid`, `spared`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BanditPaymasterSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BanditPaymasterSuite extends munit.FunSuite:
  import TargetsFixture._

  private val paymaster = CatalogNames.denizen("Bandit Paymaster")
  private val source = DecisionOptionRef.Denizen(paymaster)
  private val home = Table.homeOf(p1)

  /** p1's Act holding Bandit Paymaster, with `bandits` bandit warbands at
    * p1's site. */
  private def staged(bandits: Int, favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, paymaster).favor(p1, favor)
      .bandits(home, bandits)

  test("with two bandits at the site, one is removed and the player gains " +
      "3 warbands"):
    val ready = staged(2).ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(paymaster), Tokens(1, 0))
    assertEquals(Look(end).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(Look(end).warbands(p1), Look(ready).warbands(p1) + 3)
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said(NoteKey.Used, s"Removed 1 bandit warband from " +
        s"${home.value}, and ${p1.value} gained 3 warbands.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("the last bandit is spared, and the line says so"):
    val ready = staged(1).ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    val end = after(t)
    assertEquals(Look(end).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(Look(end).warbands(p1), Look(ready).warbands(p1))
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said("used.none", s"${home.value} had no bandit to spare.",
        covers = false)))

  test("a short supply gains what it holds"):
    val base = staged(3).ready
    val kind = PlayerForceKind.of(base, Look(base).player(p1)).get
    val ready = staged(3).warbands(p1, base.banks.warbandSupply(kind) - 1)
      .ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    assertEquals(Look(after(t)).warbands(p1), Look(ready).warbands(p1) + 1)
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said(NoteKey.Used, s"Removed 1 bandit warband from " +
        s"${home.value}, and ${p1.value} gained 1 warband.", covers = false)))

  test("it is unusable without a favor"):
    val broke = staged(2, favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == BanditPaymaster.id))
    assert(use(broke, BanditPaymaster, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BanditPaymasterSuite"`
Expected: compile error, `BanditPaymaster` not found.

- [ ] **Step 3: Write Bandit Paymaster and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/BanditPaymaster.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

/** Bandit Paymaster (card 219), ACTION: place 1 favor on this card, then
  * remove a bandit from your site, except the last, to gain 3 warbands.
  *
  * When the player's site holds 2 or more bandit warbands, one is killed
  * back to the bandit bank and the player gains 3 warbands, or what their
  * supply holds. Both run in one step: the banks differ. Otherwise nothing
  * happens and one line says so. Nothing is asked.
  */
case object BanditPaymaster extends PaidAction("denizen.bandit-paymaster",
    Cost(favor = 1)):
  val Removed: Int = 1
  val Gained: Int = 3
  /** "Removed {1} bandit warband from {site}, and {Red} gained
    * {n warbands}." */
  val paid: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Removed "),
    NotePart.Arg(0), NotePart.Plural(0, " bandit warband from ",
      " bandit warbands from "), NotePart.Arg(1), NotePart.Text(", and "),
    NotePart.Arg(2), NotePart.Text(" gained "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{site} had no bandit to spare." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no bandit to spare.")))
  override def noteKeys: Vector[NoteKey] = Vector(paid, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => pay(live, player)),
    Note(id, paidNote(_, player, source)))))

  private def bandits(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(ForceKind.Bandit, count)) => count
      case _ => 0

  private def pay(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAccess.pawnSite(ready, player)
      .filter(bandits(ready, _) > Removed) match
      case None => Right(Vector.empty)
      case Some(site) => PlayerFacts.forceKind(ready, player).map(kind =>
        Vector[CoreOperation](Kill(Piece.Warbands(ForceKind.Bandit, Removed),
          PositionedLocation(Location.Site(site))),
          Gain.Warbands(player, kind, Gained)))

  private def paidNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).zip(PowerAccess.pawnSite(states.now, player))
      .map((card, site) => line(states, card, site, player))

  /** The bandits the site lost and the warbands the player gained in the
    * step before the note. */
  private def line(states: NoteStates, card: PowerSourceRef, site: SiteId,
      player: PlayerId): PowerNote =
    val removed = states.previous.fold(0)((before, after) =>
      bandits(before, site) - bandits(after, site))
    val gained = states.previous.fold(0)(NoteSupport.warbands(_, player))
    if removed > 0 then paid(card, NoteArg.Number(removed),
      NoteArg.Site(site), NoteArg.Player(player),
      NoteArg.Amount(gained, NoteUnit.Warband))
    else spared(card, NoteArg.Site(site))
```

In `WorldActionPowers.scala`, add `BanditPaymaster` at the end of the `Vector[PhasePower](...)` list.

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BanditPaymasterSuite"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/BanditPaymaster.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/BanditPaymasterSuite.scala
git commit -m "feat(powers): add Bandit Paymaster"
```

### Task 8: `PlayerFacts.banked` and Ballot Box

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BallotBox.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BallotBoxSuite.scala`

**Interfaces:**
- Consumes: `SiteRulers.siteOf(ready, card: DenizenId): Option[SiteId]` (package-private to `gameplay`, so reachable from `gameplay.powers.action`), `PlayerFacts.forceKind`, `PlayerForceKind.of(ready, state): Option[ForceKind]`, `catalog.suitOf`.
- Produces: `PlayerFacts.banked(ready: ReadyGame, kind: ForceKind): Int`, which Bandit Prince uses in slice 4. `BallotBox` (`replaced`, `replacedBandits`, `removed`, `removedBandits`, `unmatched`, `empty`, `forCatalog`).

This task tests a seam for the first time: `Replace` with a full, a short and an empty supply.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BallotBoxSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class BallotBoxSuite extends munit.FunSuite:
  import TargetsFixture._

  private val box = CatalogNames.denizen("Ballot Box")
  private val source = DecisionOptionRef.Denizen(box)
  private val power = BallotBox.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
    .filterNot(_ == box)
  private val order = SearchFixture.denizensOf(Suit.Order)

  /** p1's Act beside Ballot Box, site-only, at p1's site, with 2 favor and
    * an `adviser`. */
  private def staged(adviser: DenizenId): Table =
    Table.start.turn(p1, Phase.Act).denizen(box, at = home).favor(p1, 2)
      .adviser(p1, adviser)

  /** A faceup Hearth adviser, which matches Ballot Box itself. */
  private def matching: Table = staged(hearth(0))

  private def mine(ready: ReadyGame): ForceKind =
    PlayerForceKind.of(ready, Look(ready).player(p1)).get

  /** `table` with p1's bank holding only `left` warbands. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    table.warbands(p1, ready.banks.warbandSupply(mine(ready)) - left)

  test("a matching adviser replaces an enemy's warbands with the player's"):
    val ready = matching.warbandsAt(home, p2, 2).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(box), Tokens(2, 0))
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 2))
    assertEquals(Look(end).warbands(p1), Look(ready).warbands(p1))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"Replaced 2 ${p2.value} warbands at ${home.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("bandits are replaced too"):
    val t = use(matching.bandits(home, 3).ready, power, source).toOption.get
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 3))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.bandits", s"Replaced 3 bandit warbands at ${home.value}.",
      covers = true)))

  test("a short supply removes the warbands it cannot replace"):
    val ready = banking(matching.warbandsAt(home, p2, 3), 1).ready
    val t = use(ready, power, source).toOption.get
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 1))
    assertEquals(NoteText.said(power, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Replaced 1 ${p2.value} warband at ${home.value}.", covers = true),
      NoteText.Said("removed",
        s"Removed 2 ${p2.value} warbands at ${home.value}.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))

  test("an empty supply removes them all, and bandits refill the site"):
    val ready = banking(matching.warbandsAt(home, p2, 2), 0).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(Look(after(t)).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "removed", s"Removed 2 ${p2.value} warbands at ${home.value}.",
      covers = false)))

  test("any card at the site can match, not only Ballot Box"):
    val ready = staged(order(0)).denizen(order(1), at = home)
      .warbandsAt(home, p2, 1).ready
    val t = use(ready, power, source).toOption.get
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 1))

  test("with no matching adviser nothing changes, and the line says so"):
    val ready = staged(order(0)).warbandsAt(home, p2, 2).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).forces(home), Look(ready).forces(home))
    assertEquals(Look(after(t)).tokensOn(box), Tokens(2, 0))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.unmatched",
      s"${p1.value} had no adviser matching a card at ${home.value}.",
      covers = false)))

  test("the player's own warbands are not replaced"):
    val ready = matching.warbandsAt(home, p1, 2).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(Look(after(t)).forces(home), Look(ready).forces(home))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no warband to replace.",
      covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BallotBoxSuite"`
Expected: compile error, `BallotBox` not found.

- [ ] **Step 3: Add `PlayerFacts.banked`**

Append to the `PlayerFacts` object in `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala`:

```scala

  /** The warbands of `kind` left in their bank: the printed supply less
    * those on boards and at sites. */
  def banked(ready: ReadyGame, kind: ForceKind): Int =
    val current = ready.game.current
    val onBoards = current.players
      .filter(state => PlayerForceKind.of(ready, state).contains(kind))
      .map(_.board.warbands).sum
    val atSites = current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum
    math.max(0,
      ready.banks.warbandSupply.getOrElse(kind, 0) - onBoards - atSites)
```

- [ ] **Step 4: Write Ballot Box and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/BallotBox.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts}
import oathdigital.model._

/** Ballot Box (card 141, site-only), ACTION: place 2 favor on this card. If
  * you have an adviser matching a card at this site, replace all warbands on
  * this site with your warbands. Remove any that cannot be replaced.
  *
  * "This site" is Ballot Box's. A match is a faceup denizen adviser of the
  * player's whose suit is that of a faceup denizen or an edifice, on either
  * face, at the site, Ballot Box included. Every warband there that is not
  * the player's, bandits included, is replaced from the player's supply with
  * a `Replace`. Those the supply cannot cover are killed first, since a site
  * holds one kind of warband. A site left empty is refilled with bandits
  * after the action. Nothing is asked.
  *
  * Its `used` lines cover the Moved line of the `Replace`. A short supply
  * adds a `removed` line for the warbands killed.
  */
final case class BallotBox private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.ballot-box", Cost(favor = 2)):
  import BallotBox._

  override def noteKeys: Vector[NoteKey] = Vector(replaced, replacedBandits,
    removed, removedBandits, unmatched, empty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => SiteRulers.siteOf(ready, card)
      .toRight(OathViolation.InvalidEventOrder(
        s"${card.value} is not at a site"))
      .map(site => Sequence(Vector[Operation](
        Branch((live, _) => vote(live, player, source, site)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.wireId} is not Ballot Box"))

  /** Read after the cost: the line for no match or no warband to replace,
    * or the replacement and its lines. */
  private def vote(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, site: SiteId): Vector[Operation] =
    def line(key: NoteKey, args: NoteArg*): Vector[Operation] =
      Vector(Note(this.id, _ => PowerSourceRef.of(source).map(key(_, args*))))
    if !matched(ready, player, site) then
      line(unmatched, NoteArg.Player(player), NoteArg.Site(site))
    else enemies(ready, player, site) match
      case None => line(empty, NoteArg.Site(site))
      case Some((kind, count)) => Vector(
        BuildOps((live, _) => replacement(live, player, site, kind, count)),
        Note(this.id, replacedNote(_, site, kind, source), covers = true),
        Note(this.id, removedNote(_, site, kind, source)))

  /** Whether a faceup denizen adviser of the player's shares a suit with a
    * faceup denizen or an edifice at the site. */
  private def matched(ready: ReadyGame, player: PlayerId, site: SiteId)
      : Boolean =
    val advised = PlayerFacts.player(ready, player).toOption.toVector
      .flatMap(_.advisers.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card })
      .flatMap(catalog.suitOf).toSet
    ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens.collect {
      case DenizenState(card, Orientation.FaceUp, _) => card: CardId
      case EdificeState(card, _, _) => card: CardId
    }).flatMap(catalog.suitOf).exists(advised)

  /** The warbands at the site that are not the player's: their kind and
    * count. */
  private def enemies(ready: ReadyGame, player: PlayerId, site: SiteId)
      : Option[(ForceKind, Int)] =
    val mine = PlayerFacts.forceKind(ready, player).toOption
    forcesAt(ready, site).collect {
      case SiteForces.Occupied(kind, count) if !mine.contains(kind) =>
        kind -> count }

  /** The kill of what the supply cannot cover, then the `Replace` of the
    * rest. */
  private def replacement(ready: ReadyGame, player: PlayerId, site: SiteId,
      kind: ForceKind, count: Int)
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, player).map { mine =>
      val covered = count.min(PlayerFacts.banked(ready, mine))
      val at = PositionedLocation(Location.Site(site))
      Vector[CoreOperation]() ++
        Option.when(count > covered)(
          Kill(Piece.Warbands(kind, count - covered), at)) ++
        Option.when(covered > 0)(Replace(Piece.Warbands(kind, covered),
          Piece.Warbands(mine, covered), at))
    }

  /** The player's warbands the step put at the site, and the warbands of
    * `kind` it killed there. */
  private def counts(states: NoteStates, site: SiteId, kind: ForceKind)
      : (Int, Int) = states.previous.fold((0, 0)) { (before, after) =>
    val placed = forcesAt(after, site) match
      case Some(SiteForces.Occupied(other, n)) if other != kind => n
      case _ => 0
    (placed, held(before, site, kind) - held(after, site, kind) - placed)
  }

  private def replacedNote(states: NoteStates, site: SiteId, kind: ForceKind,
      source: DecisionOptionRef): Option[PowerNote] =
    val (placed, _) = counts(states, site, kind)
    PowerSourceRef.of(source).filter(_ => placed > 0).map(card =>
      whose(states.now, kind) match
        case Some(owner) => replaced(card, NoteArg.Number(placed),
          NoteArg.Player(owner), NoteArg.Site(site))
        case None => replacedBandits(card, NoteArg.Number(placed),
          NoteArg.Site(site)))

  private def removedNote(states: NoteStates, site: SiteId, kind: ForceKind,
      source: DecisionOptionRef): Option[PowerNote] =
    val (_, killed) = counts(states, site, kind)
    PowerSourceRef.of(source).filter(_ => killed > 0).map(card =>
      whose(states.now, kind) match
        case Some(owner) => removed(card, NoteArg.Number(killed),
          NoteArg.Player(owner), NoteArg.Site(site))
        case None => removedBandits(card, NoteArg.Number(killed),
          NoteArg.Site(site)))

object BallotBox:
  val id: PowerId = PowerId("denizen.ballot-box")
  /** "Replaced {n} {Blue} warband at {site}." */
  val replaced: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Arg(1), NotePart.Plural(0, " warband at ", " warbands at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Replaced {n} bandit warband at {site}." */
  val replacedBandits: NoteKey = NoteKey("used.bandits", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "Removed {n} {Blue} warband at {site}." */
  val removed: NoteKey = NoteKey("removed", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Arg(1), NotePart.Plural(0, " warband at ", " warbands at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Removed {n} bandit warband at {site}." */
  val removedBandits: NoteKey = NoteKey("removed.bandits", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "{Red} had no adviser matching a card at {site}." */
  val unmatched: NoteKey = NoteKey("used.unmatched", Vector(NotePart.Arg(0),
    NotePart.Text(" had no adviser matching a card at "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{site} held no warband to replace." */
  val empty: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no warband to replace.")))

  private def forcesAt(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case Some(SiteForces.Occupied(`kind`, n)) => n
      case _ => 0

  /** The player whose warbands are of `kind`; none for bandits. */
  private def whose(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(state =>
      PlayerForceKind.of(ready, state).contains(kind)).map(_.player)

  def forCatalog(catalog: ExecutableCatalog): Option[BallotBox] =
    CatalogCards.denizen(catalog, id).map(_ => new BallotBox(catalog))
```

In `WorldActionPowers.scala`, append `++ BallotBox.forCatalog(catalog).toVector` after `MemoryOfNature.forCatalog(catalog).toVector`.

- [ ] **Step 5: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BallotBoxSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala \
  src/main/scala/oathdigital/gameplay/powers/action/BallotBox.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/BallotBoxSuite.scala
git commit -m "feat(powers): add Ballot Box"
```

### Task 9: `SiteCards` and Dark Enforcer

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/DarkEnforcer.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/DarkEnforcerSuite.scala`

**Interfaces:**
- Consumes: `CardPlay.nextRegion(region): Region`, `PowerAccess.pawnSite`, `PowerAnswers.one`, `catalog.suitOf`.
- Produces:
  - `private[action] final class SiteCards(catalog: ExecutableCatalog, suits: Set[Suit])` with `at(ready, site): Vector[(SiteDenizenState, Suit)]`, `discard(ready, site, card: SiteDenizenState, suit, actor, required: Boolean): Either[OathViolation, CoreOperation]`, `option(card): DecisionOption`, `refOf(card): DecisionOptionRef`, `cardOf(ref): Option[CardId]`, `ask(ready, player, decisionId, heading): Vector[Operation]` and `chosen(ready, player, pending, decisionId): Option[(SiteId, SiteDenizenState, Suit)]`. Tasks 10 and 11 use it.
  - `DarkEnforcer` (`discarded`, `kept`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/DarkEnforcerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class DarkEnforcerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val enforcer = CatalogNames.denizen("Dark Enforcer")
  private val source = DecisionOptionRef.Denizen(enforcer)
  private val power = DarkEnforcer.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private def edificeOf(suit: Suit): EdificeId =
    EdificeId(catalog.edifices.find(_.suit == suit).get.id.value)

  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, enforcer).favor(p1, favor)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  test("it burns a favor and discards every Order and Hearth card at the " +
      "site, with their returns"):
    val ruined = edificeOf(Suit.Order)
    val ready = staged().denizen(order(0), at = home)
      .denizen(hearth(0), at = home).denizen(beast(0), at = home)
      .edifice(ruined, EdificeSide.Ruined, at = home)
      .tokens(order(0), favor = 1).ready
    val orderBank = ready.banks.favor.getOrElse(Suit.Order, 0)
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(Look(end).denizens(home), Vector[CardId](beast(0)))
    assertEquals(pile(end).takeRight(2),
      Vector[WorldCardId](order(0), hearth(0)))
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(ruined))
    assertEquals(end.banks.favor.getOrElse(Suit.Order, 0), orderBank + 1)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Discarded ${order(0).value}, ${hearth(0).value}, ${ruined.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("an intact edifice and a facedown card stay, and the line names " +
      "the site"):
    val ready = staged()
      .edifice(edificeOf(Suit.Hearth), EdificeSide.Intact, at = home)
      .denizen(order(0), at = home, facedown = true).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(Look(after(t)).denizens(home), Look(ready).denizens(home))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Order or Hearth card to discard.",
      covers = true)))

  test("it is unusable without a favor to burn"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == DarkEnforcer.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DarkEnforcerSuite"`
Expected: compile error, `DarkEnforcer` not found.

- [ ] **Step 3: Write `SiteCards`**

Create `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** The cards of `suits` at a site that a power may discard, for Dark
  * Enforcer, Taming Charm and Great Feast: faceup denizens and ruined
  * edifices, in site order, each with its suit. An intact edifice is locked
  * and stays, and a facedown card has no suit.
  *
  * The standard discard sends a denizen to the discard pile of the region
  * after the site's, and a ruined edifice to the bottom of the edifice deck.
  * Either returns its favor to its suit's bank and its secrets to the
  * acting player facedown.
  */
private[action] final class SiteCards(catalog: ExecutableCatalog,
    suits: Set[Suit]):

  def at(ready: ReadyGame, site: SiteId): Vector[(SiteDenizenState, Suit)] =
    ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens)
      .flatMap(card => discardable(card).flatMap(catalog.suitOf)
        .filter(suits).map(card -> _))

  private def discardable(card: SiteDenizenState): Option[CardId] = card match
    case DenizenState(id, Orientation.FaceUp, _) => Some(id)
    case EdificeState(id, EdificeSide.Ruined, _) => Some(id)
    case _ => None

  /** The standard discard of `card`, of `suit`, from `site`. */
  def discard(ready: ReadyGame, site: SiteId, card: SiteDenizenState,
      suit: Suit, actor: PlayerId, required: Boolean)
      : Either[OathViolation, CoreOperation] =
    val from = PositionedLocation(Location.Site(site))
    card match
      case denizen: DenizenState => ready.game.current.map.regionOf(site)
        .map(CardPlay.nextRegion)
        .toRight(OathViolation.InvalidEventOrder(
          s"${site.value} is not in play"))
        .map(region => Discard.Denizen(denizen.id, from, region, suit,
          denizen.tokens.favor, denizen.tokens.secrets, actor, required))
      case edifice: EdificeState => Right(Discard.RuinedEdifice(edifice.id,
        from, suit, edifice.tokens.favor, edifice.tokens.secrets, actor,
        required))

  def option(card: SiteDenizenState): DecisionOption = card match
    case denizen: DenizenState =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(denizen.id))
    case edifice: EdificeState =>
      DecisionOption.Edifice(DecisionOptionRef.Edifice(edifice.id))

  def refOf(card: SiteDenizenState): DecisionOptionRef = option(card).ref

  /** The card an answer names. */
  def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  /** The question over the cards at the player's site, or nothing when
    * there is none. The discard that follows is a required batch, so a
    * refused discard hides its option, and the question passes when none is
    * left. */
  def ask(ready: ReadyGame, player: PlayerId, decisionId: String,
      heading: String): Vector[Operation] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(at(ready, _)) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
        found.map((card, _) => option(card)), heading = Some(heading)),
        passWhenEmpty = true))

  /** The answered card, with its site and suit, while it is still at the
    * player's site. Nothing when the question was not asked, or passed. */
  def chosen(ready: ReadyGame, player: PlayerId, pending: PendingTree,
      decisionId: String): Option[(SiteId, SiteDenizenState, Suit)] = for
    answer <- PowerAnswers.one(pending, decisionId)
    site <- PowerAccess.pawnSite(ready, player)
    (card, suit) <- at(ready, site).find((card, _) => refOf(card) == answer)
  yield (site, card, suit)
```

- [ ] **Step 4: Write Dark Enforcer and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/DarkEnforcer.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Dark Enforcer (card 227), ACTION: burn 1 favor, then discard all Order
  * and Hearth cards from your site.
  *
  * Every faceup Order and Hearth denizen and ruined edifice at the player's
  * site gets the standard discard ([[SiteCards]]), as far as the discard
  * rules permit: each discard is optional, so one a restriction refuses is
  * skipped and the rest go. Nothing is asked.
  *
  * Its line names the cards that left the site, as Dazzle's does, and
  * covers the generic discard lines. With none, the line names the site.
  */
final case class DarkEnforcer private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.dark-enforcer", Cost(favorBurnt = 1)):
  import DarkEnforcer._

  private val cards = new SiteCards(catalog, Set(Suit.Order, Suit.Hearth))
  override def noteKeys: Vector[NoteKey] = Vector(discarded, kept)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => discards(live, player)),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  private def discards(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(here =>
      cards.at(ready, here).map((card, suit) =>
        cards.discard(ready, here, card, suit, player, required = false)))
      .foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty))((done, next) =>
        done.flatMap(ops => next.map(ops :+ _)))

  /** The cards at the site before the step that are gone after it. None
    * gone names the site. */
  private def discardedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- PowerAccess.pawnSite(states.now, player)
  yield
    val gone: Vector[CardId] = states.previous.toVector.flatMap(
      (before, after) => cards.at(before, site).map(_._1.id).filterNot(id =>
        after.game.current.map.sites.get(site)
          .exists(_.denizens.exists(_.id == id))))
    if gone.nonEmpty then discarded(card, NoteArg.Cards(gone))
    else kept(card, NoteArg.Site(site))

object DarkEnforcer:
  val id: PowerId = PowerId("denizen.dark-enforcer")
  /** "Discarded {cards}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
  /** "{site} held no Order or Hearth card to discard." */
  val kept: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Order or Hearth card to discard.")))

  def forCatalog(catalog: ExecutableCatalog): Option[DarkEnforcer] =
    CatalogCards.denizen(catalog, id).map(_ => new DarkEnforcer(catalog))
```

In `WorldActionPowers.scala`, append `++ DarkEnforcer.forCatalog(catalog).toVector` after `BallotBox.forCatalog(catalog).toVector`.

- [ ] **Step 5: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DarkEnforcerSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala \
  src/main/scala/oathdigital/gameplay/powers/action/DarkEnforcer.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/DarkEnforcerSuite.scala
git commit -m "feat(powers): add Dark Enforcer"
```

### Task 10: Taming Charm

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/TamingCharm.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/TamingCharmSuite.scala`

**Interfaces:**
- Consumes: `SiteCards` (Task 9), `NoteSupport.answer`, `NoteSupport.favor`, `PowerAnswers.one`.
- Produces: `TamingCharm` (`decisionId`, `Gained`, `gained`, `discardedOnly`, `bare`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/TamingCharmSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TamingCharmSuite extends munit.FunSuite:
  import TargetsFixture._

  private val charm = CatalogNames.denizen("Taming Charm")
  private val source = DecisionOptionRef.Denizen(charm)
  private val power = TamingCharm.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val ruinedBeast =
    EdificeId(catalog.edifices.find(_.suit == Suit.Beast).get.id.value)

  /** p1's Act holding Taming Charm, the Beast and Nomad banks holding
    * `beasts` and `nomads`. */
  private def staged(beasts: Int = 5, nomads: Int = 5): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, charm)
      .bankFavor(Suit.Beast, beasts).bankFavor(Suit.Nomad, nomads)

  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  private def tame(ready: ReadyGame, card: DecisionOptionRef) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, TamingCharm.decisionId, pick(card)).toOption.get)

  test("it places a secret and offers the Beast and Nomad cards at the " +
      "site, not others or facedown ones"):
    val ready = staged().denizen(beast(0), at = home)
      .denizen(nomad(0), at = home).denizen(order(0), at = home)
      .denizen(beast(1), at = home, facedown = true)
      .edifice(ruinedBeast, EdificeSide.Ruined, at = home).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, TamingCharm.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> beast(0).value,
      "denizen" -> nomad(0).value, "edifice" -> ruinedBeast.value)))

  test("a Beast card is discarded, then 2 favor come from the Beast bank"):
    val ready = staged().denizen(beast(0), at = home).ready
    val (t, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).denizens(home), Vector.empty[CardId])
    assertEquals(pile(end).lastOption, Some(beast(0)))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(bank(end, Suit.Beast), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${beast(0).value} and gained " +
        "2 favor from the Beast bank.", covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the gain runs after the discard returned its favor to the same " +
      "bank"):
    val ready = staged(beasts = 0).denizen(beast(0), at = home)
      .tokens(beast(0), favor = 1).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(bank(end, Suit.Beast), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${beast(0).value} and gained " +
        "1 favor from the Beast bank.", covers = true)))

  test("a Nomad card pays from the Nomad bank"):
    val ready = staged(nomads = 3).denizen(nomad(0), at = home).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(nomad(0)))
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals((bank(end, Suit.Nomad), bank(end, Suit.Beast)), (1, 5))

  test("a ruined edifice goes to the bottom of the edifice deck"):
    val ready = staged().edifice(ruinedBeast, EdificeSide.Ruined, at = home)
      .ready
    val (_, done) = tame(ready, DecisionOptionRef.Edifice(ruinedBeast))
    val end = after(done)
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(ruinedBeast))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)

  test("with the bank empty the card is still discarded, and the line " +
      "says only that"):
    val ready = staged(beasts = 0).denizen(beast(0), at = home).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    assertEquals(Look(after(done)).favor(p1), Look(ready).favor(p1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.discarded", s"${p1.value} discarded ${beast(0).value}.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val ready = staged().denizen(order(0), at = home).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast or Nomad card.",
      covers = true)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TamingCharmSuite"`
Expected: compile error, `TamingCharm` not found.

- [ ] **Step 3: Write Taming Charm and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/TamingCharm.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PowerAnswers}
import oathdigital.model._

/** Taming Charm (card 37), ACTION: place 1 secret on this card, then
  * discard a Beast or Nomad card at your site to gain 2 favor from the
  * matching favor bank.
  *
  * The player chooses a faceup Beast or Nomad denizen or ruined edifice at
  * their site ([[SiteCards]]), which gets the standard discard. The discard
  * returns the card's favor to the bank the gain takes from, so the gain
  * runs as its own step, read after the discard: 2 favor, or all the bank
  * holds. The choice is narrated: the line names the card.
  *
  * The line covers the step before it: the gain's Gain line, or, with the
  * bank empty, the discard's Discarded line. With nothing to discard, the
  * cost stays paid and the line names the site.
  */
final case class TamingCharm private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.taming-charm", Cost(secret = 1)):
  import TamingCharm._

  private val cards = new SiteCards(catalog, Set(Suit.Beast, Suit.Nomad))
  override def noteKeys: Vector[NoteKey] = Vector(gained, discardedOnly, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => cards.ask(live, player, decisionId,
      "Taming Charm: discard a Beast or Nomad card at your site")),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    BuildOps((live, pending) => Right(gain(live, player, pending))),
    Note(this.id, tamedNote(_, player, source), covers = true))))

  private def discard(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    cards.chosen(ready, player, pending, decisionId) match
      case None => Right(Vector.empty)
      case Some((site, card, suit)) => cards.discard(ready, site, card, suit,
        player, required = true).map(Vector(_))

  /** The gain from the bank of the answered card's suit, read from the
    * catalog: the card has left the site by now. */
  private def gain(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = (for
    answer <- PowerAnswers.one(pending, decisionId)
    card <- cards.cardOf(answer)
    suit <- catalog.suitOf(card)
    take = Gained.min(ready.banks.favor.getOrElse(suit, 0))
    if take > 0
  yield Gain.Favor(player, suit, take)).toVector

  private def tamedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).flatMap(cards.cardOf) match
        case Some(chosen) => Some(line(states, card, player, chosen))
        case None => PowerAccess.pawnSite(states.now, player).map(site =>
          bare(card, NoteArg.Site(site))))

  /** The favor the player gained in the step before the note: the gain's,
    * or none when the empty bank skipped it. */
  private def line(states: NoteStates, card: PowerSourceRef, player: PlayerId,
      chosen: CardId): PowerNote =
    val favor = states.previous.fold(0)(NoteSupport.favor(_, player))
    catalog.suitOf(chosen).filter(_ => favor > 0) match
      case Some(suit) => gained(card, NoteArg.Player(player),
        NoteArg.Card(chosen), NoteArg.Amount(favor, NoteUnit.Favor),
        NoteArg.Bank(suit))
      case None =>
        discardedOnly(card, NoteArg.Player(player), NoteArg.Card(chosen))

object TamingCharm:
  val id: PowerId = PowerId("denizen.taming-charm")
  val decisionId: String = "power.taming-charm.card"
  val Gained: Int = 2
  /** "{Red} discarded {card} and gained {n favor} from {the Beast bank}." */
  val gained: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(" from "),
    NotePart.Arg(3), NotePart.Text(".")))
  /** "{Red} discarded {card}." */
  val discardedOnly: NoteKey = NoteKey("used.discarded", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{site} held no Beast or Nomad card." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Beast or Nomad card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[TamingCharm] =
    CatalogCards.denizen(catalog, id).map(_ => new TamingCharm(catalog))
```

In `WorldActionPowers.scala`, append `++ TamingCharm.forCatalog(catalog).toVector` after `DarkEnforcer.forCatalog(catalog).toVector`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TamingCharmSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/TamingCharm.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/TamingCharmSuite.scala
git commit -m "feat(powers): add Taming Charm"
```

### Task 11: Great Feast

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/GreatFeast.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/GreatFeastSuite.scala`

**Interfaces:**
- Consumes: `SiteCards` (Task 9), `PlayerFacts.player`, `NoteSupport.answer`, `NoteSupport.supply`, `SupplyTrack.Maximum`.
- Produces: `GreatFeast` (`decisionId`, `Gained`, `feasted`, `bare`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/GreatFeastSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class GreatFeastSuite extends munit.FunSuite:
  import TargetsFixture._

  private val feast = CatalogNames.denizen("Great Feast")
  private val source = DecisionOptionRef.Denizen(feast)
  private val power = GreatFeast.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)

  private def staged(supply: Int = 2, favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, feast).supply(p1, supply)
      .favor(p1, favor)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  private def eat(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, GreatFeast.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a favor and offers only the Beast cards at the site"):
    val ready = staged().denizen(beast(0), at = home)
      .denizen(nomad(0), at = home).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, GreatFeast.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(feast), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> beast(0).value)))

  test("the card is discarded and the player gains 3 Supply"):
    val ready = staged().denizen(beast(0), at = home).ready
    val (t, done) = eat(ready, beast(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(pile(end).lastOption, Some(beast(0)))
    assertEquals(Look(end).supply(p1), 5)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${beast(0).value} and gained 3 Supply.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("a nearly full track gains what fits"):
    val ready = staged(supply = 6).denizen(beast(0), at = home).ready
    val (_, done) = eat(ready, beast(0))
    assertEquals(Look(after(done)).supply(p1), 7)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${beast(0).value} and gained 1 Supply.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val t = use(staged().denizen(nomad(0), at = home).ready, power, source)
      .toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast card.", covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).denizen(beast(0), at = home).ready
    assert(!usableNow(broke).exists(_.power.id == GreatFeast.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.GreatFeastSuite"`
Expected: compile error, `GreatFeast` not found.

- [ ] **Step 3: Write Great Feast and register it**

Create `src/main/scala/oathdigital/gameplay/powers/action/GreatFeast.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts}
import oathdigital.model._

/** Great Feast (card 257), ACTION: place 1 favor on this card, then discard
  * a Beast card at your site to gain 3 Supply.
  *
  * The player chooses a faceup Beast denizen or ruined Beast edifice at
  * their site ([[SiteCards]]), which gets the standard discard. The Supply
  * runs in the same required batch, capped to the room on the track: a
  * required batch refuses an operation it cannot make in full. The choice is
  * narrated: the line names the card, and covers the Discarded line. With
  * nothing to discard, the cost stays paid and the line names the site.
  */
final case class GreatFeast private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.great-feast", Cost(favor = 1)):
  import GreatFeast._

  private val cards = new SiteCards(catalog, Set(Suit.Beast))
  override def noteKeys: Vector[NoteKey] = Vector(feasted, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => cards.ask(live, player, decisionId,
      "Great Feast: discard a Beast card at your site")),
    BuildOps((live, pending) => feast(live, player, pending), required = true),
    Note(this.id, feastNote(_, player, source), covers = true))))

  private def feast(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    cards.chosen(ready, player, pending, decisionId) match
      case None => Right(Vector.empty)
      case Some((site, card, suit)) => meal(ready, player, site, card, suit)

  /** The discard, then the Supply the track has room for. */
  private def meal(ready: ReadyGame, player: PlayerId, site: SiteId,
      card: SiteDenizenState, suit: Suit)
      : Either[OathViolation, Vector[CoreOperation]] = for
    discard <- cards.discard(ready, site, card, suit, player, required = true)
    held <- PlayerFacts.player(ready, player)
  yield
    val room = Gained.min(SupplyTrack.Maximum - held.board.supply.supply)
    Vector[CoreOperation](discard) ++
      Option.when(room > 0)(GainSupply(player, room))

  private def feastNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).flatMap(cards.cardOf) match
        case Some(chosen) => Some(feasted(card, NoteArg.Player(player),
          NoteArg.Card(chosen), NoteArg.Amount(
            states.previous.fold(0)(NoteSupport.supply(_, player)),
            NoteUnit.Supply)))
        case None => PowerAccess.pawnSite(states.now, player).map(site =>
          bare(card, NoteArg.Site(site))))

object GreatFeast:
  val id: PowerId = PowerId("denizen.great-feast")
  val decisionId: String = "power.great-feast.card"
  val Gained: Int = 3
  /** "{Red} discarded {card} and gained {n} Supply." */
  val feasted: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{site} held no Beast card." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Beast card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[GreatFeast] =
    CatalogCards.denizen(catalog, id).map(_ => new GreatFeast(catalog))
```

In `WorldActionPowers.scala`, append `++ GreatFeast.forCatalog(catalog).toVector` after `TamingCharm.forCatalog(catalog).toVector`. The finished object reads:

```scala
object WorldActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Storyteller, Firebrand, Levelers, MemoryOfHome,
      PlagueEngines, BanditPaymaster) ++
      MemoryOfNature.forCatalog(catalog).toVector ++
      BallotBox.forCatalog(catalog).toVector ++
      DarkEnforcer.forCatalog(catalog).toVector ++
      TamingCharm.forCatalog(catalog).toVector ++
      GreatFeast.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.GreatFeastSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/GreatFeast.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/GreatFeastSuite.scala
git commit -m "feat(powers): add Great Feast"
```

### Task 12: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Update the roadmap**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("docs/ROADMAP.md")
s = p.read_text()
old = "Honor Guard and Amber Flame. Slices 3c and 4 remain."
new = ("Honor Guard and Amber Flame. Slice 3c is done: the ACTION powers Taming\n"
       "Charm, Dark Enforcer, Great Feast, Plague Engines, Memory of Nature, Bandit\n"
       "Paymaster, Storyteller, Levelers, Memory of Home, Firebrand and Ballot Box.\n"
       "Slice 4 remains.")
assert s.count(old) == 1
p.write_text(s.replace(old, new))
PY
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. Server: the baseline plus 51 (Tasks 1 to 11 add 5, 3, 5, 5, 4, 3, 4, 7, 3, 7 and 5 tests). Frontend: the baseline plus 1.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 3c"
```
