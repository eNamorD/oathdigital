# Catalog Batch 3, Slice 4 (When Played) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the WHEN PLAYED powers Threatening Roar (179), Riots (91), Animal Host (190), Key to the City (18), Charlatan (79), Bandit Prince (226), Salad Days (147), Fabled Feast (136), Town Meeting (236), Great Herd (30) and Royal Tax (117), which completes catalog batch 3.

**Architecture:**

- Every power is a `WhenPlayedPower`: its `effect` runs at the card-played window, after the card is in place.
- Each reads live state from a `Branch` or a `BuildOps`, never the fold-time `ctx.state`, so the card it was played as is always where the play put it.
- They register in a new `WhenPlayedPowers` object, which `WalkerPowerCatalog.default` includes. Their reviewed stubs in `ActionPowers.scala` flip to implemented (spec, "Verify at plan time", "Registration").
- Powers-side refactor P7: Dazzle's region discard becomes `RegionDiscard`, with the kept suits as a parameter. Dazzle, Threatening Roar and Riots use it, and Dazzle gains the `none` line.
- Town Meeting reuses slice 3c's `FavorSplit`. Bandit Prince reuses `PlayerFacts.banked`. Fabled Feast and Town Meeting count with `RuledCards.of` (P1).
- Seams used by a power for the first time, each with its own test:
  - a `Swap` of two site cards (Great Herd);
  - `ChooseMany` with favor-bank options (Salad Days, with a frontend test).

**Engine changes:** none. No new operation, walker window, decision query kind, option kind, procedure step, `NoteArg` kind, protocol change or frontend production change. The only frontend change is one test.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Seams used for the first time", "Log lines", "Slice 4: when played", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch" and "Slice 4: when played"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

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
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this). A power names each note key once.
- A WHEN PLAYED note is not a use note: its keys are plain names such as `discarded` or `none`, never `used`.
- Log lines, exactly (spec, "Log lines", "Slice 4"). `{Red}` is the acting player, `{Blue}` another player. Each line's key is given:
  - Dazzle, key `discarded`: "Dazzle: Discarded {cards}." Key `none` (new): "Dazzle: Nothing was discarded."
  - Threatening Roar, keys `discarded` and `none`: the same two sentences.
  - Riots, keys `discarded` and `none`: the same two sentences. Key `burned`: "Riots: Burned {n favor} from the {People's Favor}." Key `unburned`: "Riots: The {People's Favor} had no favor to burn."
  - Animal Host, key `gained`: "Animal Host: {Red} gained {n warbands}." Key `none`: "Animal Host: {Red} gained no warbands."
  - Key to the City, key `killed`: "Key to the City: Killed {n} {Blue} warband at {site}." Key `bandits`: "Key to the City: Killed {n} bandit warband at {site}." Key `placed`: "Key to the City: {Red} placed {1 warband} at {site}." Key `unplaced`: "Key to the City: {Red} had no warband to place." Key `guarded`: "Key to the City: {Blue} was at {site}."
  - Charlatan, key `burned`: "Charlatan: Burned {n secrets} from the {Darkest Secret}." Key `none`: "Charlatan: The {Darkest Secret} had no secret to burn."
  - Bandit Prince, key `replaced`, one line per site: "Bandit Prince: Replaced {n} bandit at {site} with {Red}'s warbands." Key `none`: "Bandit Prince: No site was ruled by bandits."
  - Salad Days, key `none`: "Salad Days: Every favor bank was empty."
  - Fabled Feast, key `took`: "Fabled Feast: {Red} took {n favor} from {the Hearth bank}." Key `empty`: "Fabled Feast: Every favor bank was empty." Key `none`: "Fabled Feast: {Red} ruled no Hearth card."
  - Town Meeting, key `gained`: "Town Meeting: {Red} gained {n favor}." Key `empty`: "Town Meeting: Every favor bank was empty." Key `none`: "Town Meeting: {Red} ruled no Hearth card."
  - Great Herd, key `swapped`: "Great Herd: {Red} swapped it with {card} at {site}." Key `none`: "Great Herd: No Nomad card could be swapped."
  - Royal Tax, key `took`, one line per player: "Royal Tax: {Red} took {n favor} from {Blue}." Key `broke`: "Royal Tax: {Blue} had no favor to take." Key `none`: "Royal Tax: No player could be taxed."
  - `{n}` before "warband", "bandit warband" and "bandit" is `NoteArg.Number` with `Plural`, as for Siege Engines and Ballot Box.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **All-Exile.** Every other player is an enemy. There are no Imperial warbands.
  - **Suits.** Only faceup cards have a suit. An edifice has its suit on both faces (`catalog.suitOf`).
  - **Cards a player rules.** Their faceup advisers, and the denizens and edifices, on either face, at the sites they rule (`RuledCards.of`).
  - **Cards counting themselves.** A card counts itself only where the main text reaches it: Animal Host played as an adviser is not at a site, and Fabled Feast at a site its player does not rule is not ruled.
  - **Locked.** No power filters locked cards itself: the restriction refuses the operation, and the search hides the option.
  - **Amounts are best effort.** A gain, take or burn resolves to what its source holds.
  - **When played.** The power runs only when its card is played faceup, to a site or as a faceup adviser, and the card is already in place. For an adviser, "this site" and "this region" mean the pawn's site and region.
- Baseline: `main` after slice 3c passes about 2493 server and 467 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **Registration.** Every power joins `WhenPlayedPowers`, which `WalkerPowerCatalog.default` includes, and its `ActionPowers.scala` entry flips from `played` to `playedDone`, as Dazzle, A Small Favor and Garrison are. There are no registration pin tests (the product owner's standing instruction). Instead each suite takes its power from the default catalog with `WhenPlayedHarness.registered[P]`, so an unregistered power fails its suite.
2. **Live reads, not fold-time reads.** The spec asks whether the fold-time state holds the played card at its destination (spec, "Verify at plan time", "Riots"). The powers here never read `ctx.state`. Each reads its targets in a `Branch` or `BuildOps`, which the walker reaches after the card play has run. Dazzle moves to the same shape, so a card it names is one it read live. If this is wrong, a played card is missed by its own power.
3. **A note reads the step right before it.** `NoteStates.previous` is the last `BuildOps` the walker reached, and an empty `BuildOps` gives the same state twice (`ProcedureWalker`, "an empty batch records nothing", with `previous = (state, state)`). So every note below follows a `BuildOps` of its own, and reads "no change" when that step did nothing.
4. **Generic lines, checked in `application/gamelog/DetailLines.scala`.**
   - Write a line: `Gain.Favor` (the Gain line), `Discard.Denizen` (the Discarded line) and a `Move` of warbands onto a site (the Moved line), which is also the second child of a `Replace`.
   - Write no line: `Discard.RuinedEdifice`, `Kill`, `Gain.Warbands`, a `Burn` from a banner, a `Take` of favor between two play areas, and a `Swap` of two site cards.
   - So these notes cover (`covers = true`): the region discard's `discarded` and `none` (Dazzle, Threatening Roar, Riots), Key to the City's `placed` and `unplaced`, Bandit Prince's `replaced`, and Fabled Feast's `took`. Every other note keeps `covers = false`. Town Meeting keeps each bank's Gain line, as Alchemist does. Salad Days writes no line of its own for its gains.
5. **Dazzle gains a `none` line.** The spec adds it (spec, "Slice 4", the Dazzle row). So `DazzleSuite`'s last assertion, "a second Dazzle writes nothing", becomes "a second Dazzle says nothing was discarded". This is the only change to an existing suite's expectations.
6. **Riots' burn** counts the targets no site holds after the discard step, so a card a restriction kept is not counted. It is a best-effort `Burn` from the People's Favor, whoever holds it. The `burned` or `unburned` line is written only when something was discarded. With nothing discarded, only the `none` line is written.
7. **Key to the City** runs three steps in order: kill everything at the site, gain 1 warband, then move 1 warband from the board to the site. The place step reads the board after the gain, so a full bank and an empty board still place 1. The ruler is read when the power runs, and a ruler whose pawn is at the site stops everything. The actor's own pawn is always at the site (Key to the City is site-only), so a site the actor rules is always guarded.
8. **Bandit Prince.**
   - The question is a `ChooseMany(0, n)` over the bandit-ruled sites in map order. Declining is the empty answer.
   - The replacement is one batch of `Replace`s, then one note per chosen site, in answer order, each reading that site in the batch's step.
   - An answer whose sites hold more bandits than the player's warband bank holds is refused: the `BuildOps` returns `Left`, so the command is rejected and the decision stays parked (spec, "Verify at plan time", "Bandit Prince"). A test pins this.
   - The walker's search keeps a site only when some selection holding it survives, smallest first. So a site whose bandits alone exceed the bank is hidden. With every site hidden, the question is not asked (its minimum is 0), nothing happens and nothing is written. A test pins this.
   - Its decision is narrated: the `replaced` lines name the sites, and a decline writes nothing.
9. **Salad Days** asks only with four or more stocked banks, a `ChooseMany(3, 3)` in suit order. With one to three, it gains 1 from each, unasked. Its decision is narrated, since the Gain lines name the banks.
10. **Fabled Feast** asks only with two or more stocked banks, a `ChooseOne` in suit order. One stocked bank is taken unasked. The gain is best effort, so the `took` line reads what was gained. Its decision is narrated.
11. **Town Meeting** splits as `FavorSplit` does: the player is asked only when two or more banks hold favor and they hold more than X in all. The rulings mark this unresolved (one bank or a split). The roadmap records it.
12. **Great Herd** offers the faceup Nomad denizens and ruined Nomad edifices at every other site in play, in map order, then "Keep Great Herd". Its swap is a required batch, so the search hides a card whose swap a restriction refuses. It is not narrated, as Twin Brother is not. A site-to-site `Swap` appends each card to the other site's list, so their order changes (spec, "Verify at plan time", "Great Herd"). The board lists a site's cards in state order, so this is accepted. The tests assert where the cards are, not their order.
13. **Royal Tax** reads its targets in seat order, starting after the actor and following `current.players`. A target holding no favor gets the `broke` line and no `Take`. A `Take` that a restriction refuses writes no line.
14. **Animal Host** gains best effort. A gain of 0, from no Beast card or an empty bank, writes the `none` line.
15. **The frontend.** `WalkerSelectionPanels` renders a `ChooseMany` option as a toggle with its label and no suit symbol. It works for favor banks as it is, so there is no production change (the spec's bar). The roadmap records the missing symbol as a deferred item.

## File Structure

All paths are under `src/main/scala/oathdigital/gameplay/powers/` or `src/test/scala/oathdigital/gameplay/powers/` unless given in full.

| File | Responsibility | Task |
|---|---|---|
| `whenplayed/WhenPlayedHarness.scala` (test, modify) | Site plays, questions, answers, registration lookup, and the quiet table's sites | 1 |
| `whenplayed/RegionDiscard.scala` (create) | P7: the region discard with a suit filter, and its two lines | 1 |
| `whenplayed/Dazzle.scala` (modify) | Uses `RegionDiscard` | 1 |
| `whenplayed/DazzleSuite.scala` (test, modify) | The `none` line | 1 |
| `whenplayed/ThreateningRoar.scala` (create) | Threatening Roar | 1 |
| `whenplayed/WhenPlayedPowers.scala` (create) | Registers every power of this slice | 1 to 9 |
| `WalkerPowerCatalog.scala` (modify) | Calls `WhenPlayedPowers.forCatalog(catalog)` | 1 |
| `whenplayed/Riots.scala` (create) | Riots | 2 |
| `whenplayed/AnimalHost.scala` (create) | Animal Host | 3 |
| `whenplayed/Charlatan.scala` (create) | Charlatan | 3 |
| `whenplayed/KeyToTheCity.scala` (create) | Key to the City | 4 |
| `PowerAnswers.scala` (modify) | `many`: a `ChooseMany` answer | 5 |
| `whenplayed/BanditPrince.scala` (create) | Bandit Prince | 5 |
| `whenplayed/SaladDays.scala` (create) | Salad Days | 6 |
| `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala` (modify) | A choose-many of favor banks | 6 |
| `whenplayed/FabledFeast.scala` (create) | Fabled Feast | 7 |
| `whenplayed/TownMeeting.scala` (create) | Town Meeting | 7 |
| `whenplayed/GreatHerd.scala` (create) | Great Herd | 8 |
| `whenplayed/RoyalTax.scala` (create) | Royal Tax | 9 |
| `whenplayed/<Card>Suite.scala` (create) | One suite per card | 1 to 9 |
| `ActionPowers.scala` (modify) | Flips the eleven reviewed stubs to implemented | 10 |
| `docs/ROADMAP.md` (modify) | Records the slice and the batch | 10 |

Every new suite drives its power through `WhenPlayedHarness`: `play` (played as the actor's adviser), `fire` with `hookAt` (played to a site), `question`, `resume`, `finished`, `parked`, `recorded` and `replayed`. It stages with `Table` and reads with `Look`. The actor is always `p1`. `Table.start` is the quiet table: every site empty with no forces and no cards, and no player holds an adviser. p1 stands at Ancient City, p2 at Broken Peaks and p3 at Buried Giant, each with 1 favor, 1 faceup secret and 3 warbands, in p1's Act. The harness names three sites: `homeSite` (p1's), `nearSite` (another site in its region) and `awaySite` (a site in another region). `SearchFixture.denizensOf(suit)` gives plain denizens of a suit, without a walker power of their own. `WhenPlayedHarness.edificesOf(suit)` gives the edifices of a suit in the edifice deck. There are five per suit.

The walker's harness walks the card-played hook alone. It does not run the card play before it, nor the bandit refill after the command, so a suite stages the card where the play would put it.

Suit order is `Suit.all`: Discord, Arcane, Order, Hearth, Beast, Nomad. `NoteText` renders a player, card or site by its id, a bank as "the Order bank" and a banner by its name.

---

### Task 1: `RegionDiscard` (P7), Dazzle's `none` line, Threatening Roar, and `WhenPlayedPowers`

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/RegionDiscard.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoar.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoarSuite.scala`

**Interfaces:**
- Consumes: `WhenPlayedPower` (`cardId`, `effect`, `WhenPlayedPower.cardOf`), `catalog.suitOf`, `Discard.Denizen`, `Discard.RuinedEdifice`.
- Produces:
  - `WhenPlayedHarness`: `hookAt(card, site)`, `fire(ready, power, hook)`, `question(ready, power, hook, tree): Decide`, `resume(ready, power, hook, tree, decisionId, answer)`, `registered[P]`, `homeSite`, `nearSite`, `awaySite`, `inMapOrder(cards*)`, `edificesOf(suit)`. Every later suite uses them.
  - `private[whenplayed] final class RegionDiscard(catalog: ExecutableCatalog, kept: Suit => Boolean)` with `targets(ready, actor): Vector[CardId]`, `discards(ready, actor): Either[OathViolation, Vector[CoreOperation]]` and `effect(power: PowerId, card: DenizenId, actor: PlayerId): Vector[Operation]`.
  - `object RegionDiscard` with `discarded: NoteKey`, `none: NoteKey`, `next(origin: Region): Region`, `gone(found: Vector[CardId], ready: ReadyGame): Vector[CardId]` and `said(card: DenizenId, found: Vector[CardId])(states: NoteStates): Option[PowerNote]`. Riots uses them in Task 2.
  - `ThreateningRoar` (`id`, `forCatalog`).
  - `WhenPlayedPowers.forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower]`, which later tasks extend.

- [ ] **Step 1: Extend the harness**

Replace `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala` with:

```scala
package oathdigital.gameplay.powers.whenplayed

import scala.reflect.ClassTag

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy, OperationRestrictions}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.PowerFixture.{actor, base}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** Drives a When Played power through its `CardPlayed` hook, the way the
  * Dazzle and Conspiracy suites do. The actor is `PowerFixture.actor`, p1.
  */
object WhenPlayedHarness:
  def hook(card: DenizenId): CardPlayedFaceup =
    CardPlayedFaceup(card, RuleSourceRef.Adviser(actor, card))

  /** The hook of `card` played to `site`. */
  def hookAt(card: DenizenId, site: SiteId): CardPlayedFaceup =
    CardPlayedFaceup(card, RuleSourceRef.SiteCard(site, card))

  def powers(power: ContributingPower): WalkerPowers =
    WalkerPowers(Vector(power),
      restrictionSet = OperationRestrictions.forCatalog(catalog))

  def play(ready: ReadyGame, power: ContributingPower, card: DenizenId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None, powers(power))

  /** Walks `hook`, for a card played to a site or as an adviser. */
  def fire(ready: ReadyGame, power: ContributingPower, hook: CardPlayedFaceup)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook, None, powers(power))

  /** The question parked at `tree`, as the walker offers it. */
  def question(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree): Decide =
    ProcedureWalker.parkedDecide(ready, hook, tree, powers(power)).get

  /** Resumes `tree` with the actor's `answer` to `decisionId`. */
  def resume(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(ready, hook, tree,
      Answered(decisionId, answer, actor), powers(power))

  def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  def parked(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Parked =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked]

  def recorded(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    OperationPipeline.run(from, recorded(events),
      OperationPolicy.Permissive, Vector.empty)(Right(_)).toOption.get.state

  /** The power of type `P` that the default walker catalog registers, so a
    * suite fails when its power is not wired in. */
  def registered[P <: ContributingPower](using tag: ClassTag[P]): P =
    WalkerPowerCatalog.default(catalog).powers.collectFirst {
      case tag(power) => power }.get

  /** p1's site on the quiet table. */
  val homeSite: SiteId = PowerFixture.home(base)
  /** Another site in p1's region. */
  val nearSite: SiteId = inRegion.find(_ != homeSite).get
  /** A site in another region. */
  val awaySite: SiteId =
    base.game.current.map.inPlay.find(!inRegion.contains(_)).get

  private def inRegion: Vector[SiteId] =
    val map = base.game.current.map
    map.inPlay.filter(map.regionOf(_) == map.regionOf(homeSite))

  /** `cards` by site, flattened in map order. */
  def inMapOrder(cards: (SiteId, Vector[CardId])*): Vector[CardId] =
    val bySite = cards.toMap
    base.game.current.map.inPlay.flatMap(bySite.getOrElse(_, Vector.empty))

  /** The edifices of `suit` in the quiet table's edifice deck. */
  def edificesOf(suit: Suit): Vector[EdificeId] =
    base.game.current.commonCards.edificeDeck
      .filter(catalog.suitOf(_).contains(suit))
```

- [ ] **Step 2: Write the failing suite, and the Dazzle `none` assertion**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoarSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class ThreateningRoarSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[ThreateningRoar]
  private val roar = power.cardId
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beastEdifices = edificesOf(Suit.Beast)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  test("as an adviser it discards the Beast and Nomad cards at sites in " +
      "the pawn's region"):
    val ruined = beastEdifices(0)
    val intact = beastEdifices(1)
    val ready = Table.start.adviser(p1, roar)
      .denizen(beast(0), at = homeSite).denizen(hearth(0), at = homeSite)
      .denizen(nomad(0), at = nearSite)
      .edifice(ruined, EdificeSide.Ruined, at = nearSite)
      .edifice(intact, EdificeSide.Intact, at = nearSite)
      .denizen(beast(1), at = awaySite).ready
    val done = finished(play(ready, power, roar))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector[CardId](hearth(0)))
    assertEquals(after.denizens(nearSite), Vector[CardId](intact))
    assertEquals(after.denizens(awaySite), Vector[CardId](beast(1)))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(discarded(inMapOrder(
      homeSite -> Vector(beast(0)), nearSite -> Vector(nomad(0), ruined)))))

  test("played to a site it discards itself with the rest"):
    val ready = Table.start.denizen(roar, at = homeSite)
      .denizen(nomad(0), at = homeSite).ready
    val done = finished(fire(ready, power, hookAt(roar, homeSite)))
    assertEquals(Look(done.treeless).denizens(homeSite), Vector.empty)
    assertEquals(said(done.events), Vector(discarded(Vector(roar, nomad(0)))))

  test("with nothing to discard it says so"):
    val ready = Table.start.adviser(p1, roar)
      .denizen(hearth(0), at = homeSite).ready
    val done = finished(play(ready, power, roar))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
```

Then change `DazzleSuite`'s last assertion (rulings, 5):

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala")
s = p.read_text()
old = """    // A second Dazzle finds nothing left to discard, and writes nothing.
    val again = ProcedureWalker.advance(finished.treeless, hook, None,
      WalkerPowers(Vector(power))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    assertEquals(NoteText.said(power.id, power.noteKeys, again.events),
      Vector.empty)
"""
new = """    // A second Dazzle finds nothing left to discard, and says so.
    val again = ProcedureWalker.advance(finished.treeless, hook, None,
      WalkerPowers(Vector(power))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    assertEquals(NoteText.said(power.id, power.noteKeys, again.events),
      Vector(NoteText.Said("none", "Nothing was discarded.", covers = true)))
"""
assert s.count(old) == 1
p.write_text(s.replace(old, new))
PY
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.ThreateningRoarSuite oathdigital.gameplay.powers.whenplayed.DazzleSuite"`
Expected: compile error, `ThreateningRoar` not found.

- [ ] **Step 4: Extract `RegionDiscard` and move Dazzle onto it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/RegionDiscard.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The region discard of Dazzle, Threatening Roar and Riots (catalog batch
  * 3, refactor P7): every site denizen and ruined edifice of a kept suit at
  * the sites in the actor's region, as far as the generic discard rules
  * permit. Intact edifices are locked, so they stay. The region is the
  * actor's pawn's, so a card played to a site discards itself when its suit
  * is kept.
  *
  * A power reads its targets live, from a `Branch` the walker reaches after
  * the card is in place, then discards them in one step. Its line names the
  * targets no site holds after that step, so a card a restriction kept is
  * not named, and it says so when nothing was discarded.
  */
private[whenplayed] final class RegionDiscard(catalog: ExecutableCatalog,
    kept: Suit => Boolean):

  /** The kept site denizens and ruined edifices in the actor's region, in
    * map order. A card the catalog does not know is not listed; `discards`
    * rejects it. */
  def targets(ready: ReadyGame, actor: PlayerId): Vector[CardId] =
    cards(ready, actor).collect {
      case (_, card) if discardable(card) &&
          catalog.suitOf(card.id).exists(kept) => card.id }

  /** A discard of each target, read live, to the next region's discard
    * pile. */
  def discards(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    region(ready, actor).toRight(OathViolation.PawnSiteMissing(actor))
      .flatMap(origin => cards(ready, actor).foldLeft[Either[OathViolation,
          Vector[CoreOperation]]](Right(Vector.empty)) {
        case (acc, (site, card)) => acc.flatMap(done =>
          suitOf(card).map(suit =>
            if kept(suit) then done ++ discard(site, card, suit,
              RegionDiscard.next(origin), actor)
            else done))
      })

  /** The whole effect of a power that only discards: the discard step, then
    * its line. */
  def effect(power: PowerId, card: DenizenId, actor: PlayerId)
      : Vector[Operation] = Vector(Branch((live, _) => Vector(
    BuildOps((ready, _) => discards(ready, actor)),
    Note(power, RegionDiscard.said(card, targets(live, actor)),
      covers = true))))

  private def region(ready: ReadyGame, actor: PlayerId): Option[Region] =
    val current = ready.game.current
    current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)

  /** Every site card in the actor's region, with its site, in map order. */
  private def cards(ready: ReadyGame, actor: PlayerId)
      : Vector[(SiteId, SiteDenizenState)] =
    val map = ready.game.current.map
    region(ready, actor).toVector.flatMap(origin => map.inPlay
      .filter(map.regionOf(_).contains(origin))
      .flatMap(site => map.sites.get(site).toVector
        .flatMap(_.denizens.map(site -> _))))

  private def suitOf(card: SiteDenizenState): Either[OathViolation, Suit] =
    catalog.suitOf(card.id).toRight(card match {
      case denizen: DenizenState => OathViolation.UnknownWorldCard(denizen.id)
      case edifice: EdificeState => OathViolation.UnknownEdifice(edifice.id)
    })

  private def discard(site: SiteId, card: SiteDenizenState, suit: Suit,
      to: Region, actor: PlayerId): Option[CoreOperation] =
    val from = PositionedLocation(Location.Site(site))
    card match
      case denizen: DenizenState => Some(Discard.Denizen(denizen.id, from, to,
        suit, denizen.tokens.favor, denizen.tokens.secrets, actor))
      case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
        Some(Discard.RuinedEdifice(edifice.id, from, suit,
          edifice.tokens.favor, edifice.tokens.secrets, actor))
      case _ => None

  private def discardable(card: SiteDenizenState): Boolean = card match
    case _: DenizenState => true
    case edifice: EdificeState => edifice.side == EdificeSide.Ruined

private[whenplayed] object RegionDiscard:
  /** "Discarded {cards}." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
  /** "Nothing was discarded." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nothing was discarded.")))

  /** The region whose discard pile takes the cards discarded in `origin`. */
  def next(origin: Region): Region = origin match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle

  /** The cards of `found` that no site holds in `ready`. */
  def gone(found: Vector[CardId], ready: ReadyGame): Vector[CardId] =
    val sites = ready.game.current.map.sites.values
    found.filterNot(id => sites.exists(_.denizens.exists(_.id == id)))

  /** "Discarded {cards}." for the cards of `found` that left the sites, or
    * "Nothing was discarded." */
  def said(card: DenizenId, found: Vector[CardId])(states: NoteStates)
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(card)
    val left = gone(found, states.now)
    Some(if left.nonEmpty then discarded(source, NoteArg.Cards(left))
      else none(source))
```

Replace `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala` with:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Dazzle (card 35), WHEN PLAYED: discard all Hearth and Order cards at
  * sites in your region.
  *
  * The discard is [[RegionDiscard]]'s, with Hearth and Order kept: the
  * Hearth and Order site denizens and ruined edifices in the actor's region,
  * as far as the generic discard rules permit. Its line names the cards it
  * discarded, "Discarded {cards}.", and covers the generic discard lines.
  * With nothing discarded it says "Nothing was discarded."
  */
final case class Dazzle private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  def id: PowerId = Dazzle.id

  override def noteKeys: Vector[NoteKey] =
    Vector(RegionDiscard.discarded, RegionDiscard.none)

  private val region = new RegionDiscard(catalog, Dazzle.suits)

  def effect(ctx: PowerCtx): Vector[Operation] =
    region.effect(id, cardId, ctx.activePlayer)

object Dazzle:
  val id: PowerId = PowerId("denizen.dazzle")
  private val suits: Set[Suit] = Set(Suit.Hearth, Suit.Order)

  def forCatalog(catalog: ExecutableCatalog): Option[Dazzle] =
    WhenPlayedPower.cardOf(catalog, id).map(new Dazzle(_, catalog))
```

- [ ] **Step 5: Write Threatening Roar and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoar.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Threatening Roar (card 179), WHEN PLAYED: discard all Nomad and Beast
  * cards at sites in your region.
  *
  * The discard is [[RegionDiscard]]'s, as Dazzle's is, with Nomad and Beast
  * kept. Played to a site, it is a Beast card at a site in the region, so it
  * discards itself.
  */
final case class ThreateningRoar private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  def id: PowerId = ThreateningRoar.id

  override def noteKeys: Vector[NoteKey] =
    Vector(RegionDiscard.discarded, RegionDiscard.none)

  private val region = new RegionDiscard(catalog, ThreateningRoar.suits)

  def effect(ctx: PowerCtx): Vector[Operation] =
    region.effect(id, cardId, ctx.activePlayer)

object ThreateningRoar:
  val id: PowerId = PowerId("denizen.threatening-roar")
  private val suits: Set[Suit] = Set(Suit.Nomad, Suit.Beast)

  def forCatalog(catalog: ExecutableCatalog): Option[ThreateningRoar] =
    WhenPlayedPower.cardOf(catalog, id).map(new ThreateningRoar(_, catalog))
```

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The When Played powers of catalog batch 3, which `WalkerPowerCatalog`
  * wires in. A power whose card is absent from `catalog` is left out. */
object WhenPlayedPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector
```

Register it in `WalkerPowerCatalog`:

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala")
s = p.read_text()
old_import = "Garrison, ShiftingFog, TwinBrother}"
new_import = "Garrison, ShiftingFog, TwinBrother, WhenPlayedPowers}"
old_line = "      TwinBrother.forCatalog(catalog).toVector ++\n"
new_line = old_line + "      WhenPlayedPowers.forCatalog(catalog) ++\n"
assert s.count(old_import) == 1 and s.count(old_line) == 1
p.write_text(s.replace(old_import, new_import).replace(old_line, new_line))
PY
```

- [ ] **Step 6: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. `ThreateningRoarSuite` runs 3 tests, and every other suite in the package, Dazzle's included, passes.

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/RegionDiscard.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoar.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/ThreateningRoarSuite.scala
git commit -m "feat(powers): add Threatening Roar on a shared region discard"
```

### Task 2: Riots

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Riots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/RiotsSuite.scala`

**Interfaces:**
- Consumes: `RegionDiscard` (`targets`, `discards`, `RegionDiscard.said`, `RegionDiscard.gone`, `RegionDiscard.discarded`, `RegionDiscard.none`), `WhenPlayedPowers.forCatalog` (Task 1).
- Produces: `Riots` (`id`, `burned`, `unburned`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/RiotsSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class RiotsSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Riots]
  private val riots = power.cardId
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val orderEdifices = edificesOf(Suit.Order)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  private def burned(n: Int): NoteText.Said = NoteText.Said("burned",
    s"Burned $n favor from the People's Favor.", covers = false)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** Riots as p1's adviser, with four discardable cards in p1's region, an
    * intact edifice there and a denizen in another region. */
  private def staged: Table = Table.start.adviser(p1, riots)
    .denizen(hearth(0), at = homeSite).denizen(beast(0), at = homeSite)
    .denizen(nomad(0), at = nearSite)
    .edifice(orderEdifices(0), EdificeSide.Ruined, at = nearSite)
    .edifice(orderEdifices(1), EdificeSide.Intact, at = nearSite)
    .denizen(hearth(1), at = awaySite)

  private val targets: Vector[CardId] = inMapOrder(
    homeSite -> Vector(hearth(0), beast(0)),
    nearSite -> Vector(nomad(0), orderEdifices(0)))

  test("it discards every denizen and ruined edifice in the region, then " +
      "burns as many favor from a held People's Favor"):
    val ready = staged.peoplesFavor(Some(p2), 5).ready
    val done = finished(play(ready, power, riots))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector.empty)
    assertEquals(after.denizens(nearSite), Vector[CardId](orderEdifices(1)))
    assertEquals(after.denizens(awaySite), Vector[CardId](hearth(1)))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(done.treeless.game.current.banners.peoplesFavor.holder,
      Some(p2))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(discarded(targets), burned(4)))

  test("played to a site it discards and counts itself"):
    val ready = Table.start.denizen(riots, at = homeSite)
      .denizen(hearth(0), at = homeSite).peoplesFavor(None, 5).ready
    val done = finished(fire(ready, power, hookAt(riots, homeSite)))
    assertEquals(Look(done.treeless).denizens(homeSite), Vector.empty)
    assertEquals(onBanner(done.treeless), 3)
    assertEquals(said(done.events),
      Vector(discarded(Vector(riots, hearth(0))), burned(2)))

  test("the burn takes what the People's Favor holds"):
    val done = finished(play(staged.peoplesFavor(None, 1).ready, power, riots))
    assertEquals(onBanner(done.treeless), 0)
    assertEquals(said(done.events), Vector(discarded(targets), burned(1)))

  test("an empty People's Favor burns nothing, and the line says so"):
    val done = finished(play(staged.peoplesFavor(None, 0).ready, power, riots))
    assertEquals(said(done.events), Vector(discarded(targets),
      NoteText.Said("unburned", "The People's Favor had no favor to burn.",
        covers = false)))

  test("with nothing to discard it burns nothing"):
    val ready = Table.start.adviser(p1, riots)
      .denizen(hearth(1), at = awaySite).peoplesFavor(None, 5).ready
    val done = finished(play(ready, power, riots))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(onBanner(done.treeless), 5)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RiotsSuite"`
Expected: compile error, `Riots` not found.

- [ ] **Step 3: Write Riots and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Riots.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Riots (card 91), WHEN PLAYED: discard all denizens at sites in this
  * region. Burn the same number of favor from the People's Favor.
  *
  * The discard is [[RegionDiscard]]'s with every suit kept: every site
  * denizen and ruined edifice in the actor's region, as far as the generic
  * discard rules permit. Played to a site, Riots stands there, so it
  * discards and counts itself. The burn is one favor for each target no
  * site holds after the discard, from the People's Favor, whoever holds it,
  * as far as it holds favor. With nothing discarded nothing is burned, and
  * only the `none` line is written.
  */
final case class Riots private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import Riots._
  def id: PowerId = Riots.id

  override def noteKeys: Vector[NoteKey] =
    Vector(RegionDiscard.discarded, burned, unburned, RegionDiscard.none)

  private val region = new RegionDiscard(catalog, _ => true)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = region.targets(live, actor)
      Vector(
        BuildOps((ready, _) => region.discards(ready, actor)),
        Note(id, RegionDiscard.said(cardId, found), covers = true),
        BuildOps((ready, _) => Right(burn(ready, found))),
        Note(id, burnedNote(_, found)))))

  /** What the burn step took from the banner, or that it held none. Nothing
    * when nothing was discarded. */
  private def burnedNote(states: NoteStates, found: Vector[CardId])
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val banner = NoteArg.Banner(Banner.PeoplesFavor)
    val burnt = states.previous.fold(0)((before, after) =>
      favorOn(before) - favorOn(after))
    Option.when(RegionDiscard.gone(found, states.now).nonEmpty)(
      if burnt > 0 then burned(source, NoteArg.Amount(burnt, NoteUnit.Favor),
        banner)
      else unburned(source, banner))

object Riots:
  val id: PowerId = PowerId("denizen.riots")
  /** "Burned {n favor} from the {People's Favor}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "The {People's Favor} had no favor to burn." */
  val unburned: NoteKey = NoteKey("unburned", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" had no favor to burn.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Riots] =
    WhenPlayedPower.cardOf(catalog, id).map(new Riots(_, catalog))

  private def favorOn(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** One favor for each of `found` that no site holds now. */
  private def burn(ready: ReadyGame, found: Vector[CardId])
      : Vector[CoreOperation] =
    val count = RegionDiscard.gone(found, ready).size
    if count == 0 then Vector.empty
    else Vector(Burn.favor(count,
      PositionedLocation(Location.OnBanner(Banner.PeoplesFavor))))
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RiotsSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/Riots.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/RiotsSuite.scala
git commit -m "feat(powers): add Riots"
```

### Task 3: Animal Host and Charlatan

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/AnimalHost.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Charlatan.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/AnimalHostSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/CharlatanSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.forceKind`, `NoteSupport.warbands`, `NoteSupport.gainedKey`, `WhenPlayedPowers.forCatalog` (Task 2).
- Produces: `AnimalHost` (`id`, `gained`, `none`, `forCatalog`) and `Charlatan` (`id`, `Kept`, `burned`, `none`, `forCatalog`).

Both powers are one `BuildOps` and one note, so neither needs a `Branch`: the step reads live state, and the node count never changes.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/AnimalHostSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PlayerFacts, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class AnimalHostSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[AnimalHost]
  private val host = power.cardId
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beastEdifices = edificesOf(Suit.Beast)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n ${if n == 1 then "warband" else "warbands"}.",
    covers = false)

  /** `table` with p1's warband bank holding only `left`. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    val kind = PlayerFacts.forceKind(ready, p1).toOption.get
    table.warbands(p1, ready.banks.warbandSupply(kind) - left)

  test("as an adviser it counts the Beast cards at every site, whoever " +
      "rules them"):
    // Four count: a denizen at p1's site, a ruined edifice at a bandit site,
    // and an intact edifice and a denizen at p2's site. A Hearth card and
    // p2's Beast adviser do not.
    val ready = Table.start.adviser(p1, host)
      .denizen(beast(0), at = homeSite).denizen(hearth(0), at = homeSite)
      .bandits(nearSite, 2)
      .edifice(beastEdifices(0), EdificeSide.Ruined, at = nearSite)
      .edifice(beastEdifices(1), EdificeSide.Intact, at = awaySite)
      .denizen(beast(1), at = awaySite).warbandsAt(awaySite, p2, 1)
      .adviser(p2, beast(2)).ready
    val done = finished(play(ready, power, host))
    assertEquals(Look(done.treeless).warbands(p1), 3 + 4)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(gained(4)))

  test("played to a site it counts itself"):
    val ready = Table.start.denizen(host, at = homeSite)
      .denizen(beast(0), at = nearSite).ready
    val done = finished(fire(ready, power, hookAt(host, homeSite)))
    assertEquals(Look(done.treeless).warbands(p1), 3 + 2)
    assertEquals(said(done.events), Vector(gained(2)))

  test("the gain takes what the bank holds"):
    val ready = banking(Table.start.adviser(p1, host)
      .denizen(beast(0), at = homeSite).denizen(beast(1), at = nearSite),
      left = 1).ready
    val done = finished(play(ready, power, host))
    assertEquals(said(done.events), Vector(gained(1)))

  test("with no Beast card at a site it gains none, and the line says so"):
    val ready = Table.start.adviser(p1, host).adviser(p1, beast(0)).ready
    val done = finished(play(ready, power, host))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} gained no warbands.", covers = false)))
```

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/CharlatanSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

class CharlatanSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Charlatan]
  private val charlatan = power.cardId

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  private val none = NoteText.Said("none",
    "The Darkest Secret had no secret to burn.", covers = false)

  private def withBanner(holder: Option[PlayerId], secrets: Int): ReadyGame =
    Table.start.adviser(p1, charlatan).darkestSecret(holder, secrets).ready

  test("it burns all but one secret from a held Darkest Secret"):
    val ready = withBanner(Some(p2), 4)
    val done = finished(play(ready, power, charlatan))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(done.treeless.game.current.banners.darkestSecret.holder,
      Some(p2))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      "Burned 3 secrets from the Darkest Secret.", covers = false)))

  test("a single secret stays, and the line says none was burned"):
    val done = finished(play(withBanner(None, 1), power, charlatan))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(none))

  test("an empty Darkest Secret burns nothing"):
    val done = finished(play(withBanner(None, 0), power, charlatan))
    assertEquals(said(done.events), Vector(none))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.AnimalHostSuite oathdigital.gameplay.powers.whenplayed.CharlatanSuite"`
Expected: compile errors, `AnimalHost` and `Charlatan` not found.

- [ ] **Step 3: Write both powers and register them**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/AnimalHost.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

/** Animal Host (card 190), WHEN PLAYED: gain warbands equal to the total
  * number of Beast cards (including Animal Host) at any sites (regardless of
  * rule).
  *
  * The count is every faceup Beast denizen and every Beast edifice, on
  * either face, at every site in play, read live. Played to a site, Animal
  * Host is one of them; played as an adviser it is not at a site, so it does
  * not count itself (rulings, "Cards counting themselves"). The gain is best
  * effort, so it takes what the bank holds. A gain of 0 writes the `none`
  * line.
  */
final case class AnimalHost private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import AnimalHost._
  def id: PowerId = AnimalHost.id

  override def noteKeys: Vector[NoteKey] = Vector(gained, none)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(BuildOps((ready, _) => gain(ready, actor)),
      Note(id, gainedNote(_, actor)))

  private def gain(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val count = beasts(ready)
    if count == 0 then Right(Vector.empty)
    else PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, count)))

  /** The faceup Beast denizens and the Beast edifices at every site in
    * play. */
  private def beasts(ready: ReadyGame): Int =
    val map = ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case edifice: EdificeState =>
        catalog.suitOf(edifice.id).contains(Suit.Beast)
      case _ => false
    }

  /** The warbands the gain step added to the actor's board. */
  private def gainedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val count = states.previous.fold(0)(NoteSupport.warbands(_, actor))
    Some(if count > 0 then gained(source, NoteArg.Player(actor),
      NoteArg.Amount(count, NoteUnit.Warband))
    else none(source, NoteArg.Player(actor)))

object AnimalHost:
  val id: PowerId = PowerId("denizen.animal-host")
  /** "{Red} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** "{Red} gained no warbands." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" gained no warbands.")))

  def forCatalog(catalog: ExecutableCatalog): Option[AnimalHost] =
    WhenPlayedPower.cardOf(catalog, id).map(new AnimalHost(_, catalog))
```

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Charlatan.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Charlatan (card 79), WHEN PLAYED: burn all secrets but one from the
  * Darkest Secret.
  *
  * The banner's secrets are read live. When it holds more than 1, all but 1
  * burn, whoever holds it, or nobody. Otherwise nothing burns and the line
  * says so.
  */
final case class Charlatan private (cardId: DenizenId)
    extends WhenPlayedPower:
  import Charlatan._
  def id: PowerId = Charlatan.id

  override def noteKeys: Vector[NoteKey] = Vector(burned, none)

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(
    BuildOps((ready, _) => Right(burn(ready))),
    Note(id, burnedNote))

  /** What the burn step took from the banner. */
  private def burnedNote(states: NoteStates): Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val banner = NoteArg.Banner(Banner.DarkestSecret)
    val burnt = states.previous.fold(0)((before, after) =>
      secretsOn(before) - secretsOn(after))
    Some(if burnt > 0 then burned(source,
      NoteArg.Amount(burnt, NoteUnit.Secret), banner)
    else none(source, banner))

object Charlatan:
  val id: PowerId = PowerId("denizen.charlatan")
  /** The secrets the Darkest Secret keeps. */
  val Kept: Int = 1
  /** "Burned {n secrets} from the {Darkest Secret}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "The {Darkest Secret} had no secret to burn." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" had no secret to burn.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Charlatan] =
    WhenPlayedPower.cardOf(catalog, id).map(new Charlatan(_))

  private def secretsOn(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  private def burn(ready: ReadyGame): Vector[CoreOperation] =
    val extra = secretsOn(ready) - Kept
    if extra <= 0 then Vector.empty
    else Vector(Burn.secrets(extra,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.AnimalHostSuite oathdigital.gameplay.powers.whenplayed.CharlatanSuite"`
Expected: PASS, 4 and 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/AnimalHost.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/Charlatan.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/AnimalHostSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/CharlatanSuite.scala
git commit -m "feat(powers): add Animal Host and Charlatan"
```

### Task 4: Key to the City

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCity.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCitySuite.scala`

**Interfaces:**
- Consumes: `SiteRulers.siteOf`, `SiteRulers.rulerOf`, `PlayerFacts.forceKind`, `PlayerForceKind.of`, `WhenPlayedPowers.forCatalog` (Task 3).
- Produces: `KeyToTheCity` (`id`, `Placed`, `killed`, `bandits`, `placed`, `unplaced`, `guarded`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCitySuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PlayerFacts}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class KeyToTheCitySuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[KeyToTheCity]
  private val key = power.cardId
  private val played = hookAt(key, homeSite)
  /** Key to the City is site-only: it stands at p1's site. */
  private val start = Table.start.denizen(key, at = homeSite)
  private val mine = PlayerFacts.forceKind(start.ready, p1).toOption.get

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private val killedTwo = NoteText.Said("killed",
    s"Killed 2 ${p2.value} warbands at ${homeSite.value}.", covers = false)
  private val placed = NoteText.Said("placed",
    s"${p1.value} placed 1 warband at ${homeSite.value}.", covers = true)

  test("an absent ruler's warbands are killed, and the actor gains and " +
      "places one"):
    val ready = start.warbandsAt(homeSite, p2, 2).ready
    val done = finished(fire(ready, power, played))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Occupied(mine, 1))
    assertEquals(after.warbands(p1), 3)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(killedTwo, placed))

  test("bandits are killed too"):
    val done = finished(fire(start.bandits(homeSite, 3).ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(mine, 1))
    assertEquals(said(done.events), Vector(NoteText.Said("bandits",
      s"Killed 3 bandit warbands at ${homeSite.value}.", covers = false),
      placed))

  test("an empty site only receives the warband"):
    val done = finished(fire(start.ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(mine, 1))
    assertEquals(said(done.events), Vector(placed))

  test("the actor rules the site and stands there, so nothing happens"):
    val ready = start.warbandsAt(homeSite, p1, 2).ready
    val done = finished(fire(ready, power, played))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("guarded",
      s"${p1.value} was at ${homeSite.value}.", covers = false)))

  test("a ruler whose pawn stands at the site keeps it"):
    val ready = start.warbandsAt(homeSite, p2, 2).pawn(p2, homeSite).ready
    val theirs = PlayerFacts.forceKind(ready, p2).toOption.get
    val done = finished(fire(ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(theirs, 2))
    assertEquals(said(done.events), Vector(NoteText.Said("guarded",
      s"${p2.value} was at ${homeSite.value}.", covers = false)))

  test("with no warband on the board or in the bank nothing is placed"):
    // Every one of p1's warbands stands at a site in another region.
    val supply = start.ready.banks.warbandSupply(mine)
    val ready = start.warbandsAt(homeSite, p2, 2).warbands(p1, 0)
      .warbandsAt(awaySite, p1, supply).ready
    val done = finished(fire(ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite), SiteForces.Empty)
    assertEquals(said(done.events), Vector(killedTwo,
      NoteText.Said("unplaced", s"${p1.value} had no warband to place.",
        covers = true)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.KeyToTheCitySuite"`
Expected: compile error, `KeyToTheCity` not found.

- [ ] **Step 3: Write Key to the City and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCity.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Key to the City (card 18, site-only), WHEN PLAYED: if the ruler's pawn
  * is not at this site, kill any warbands at this site, then gain a warband
  * and place it here.
  *
  * The site and its ruler are read live, after the card is in place. When
  * the ruler is a player whose pawn stands there, nothing happens and one
  * line says who was there. The actor's pawn is always there, so a site the
  * actor rules is kept. Otherwise three steps run in order: every warband
  * there is killed, the actor gains 1 warband, and 1 warband moves from the
  * actor's board to the site. Each is best effort. With no warband on the
  * board, nothing is placed, and the refill after the command puts bandits
  * on the emptied site.
  */
final case class KeyToTheCity private (cardId: DenizenId)
    extends WhenPlayedPower:
  import KeyToTheCity._
  def id: PowerId = KeyToTheCity.id

  override def noteKeys: Vector[NoteKey] =
    Vector(killed, bandits, placed, unplaced, guarded)

  private def source: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      SiteRulers.siteOf(live, cardId).toVector.flatMap(steps(live, actor, _))))

  private def steps(ready: ReadyGame, actor: PlayerId, site: SiteId)
      : Vector[Operation] = guard(ready, site) match
    case Some(ruler) => Vector(Note(id, _ => Some(guarded(source,
      NoteArg.Player(ruler), NoteArg.Site(site)))))
    case None => Vector(
      BuildOps((live, _) => Right(kill(live, site))),
      Note(id, killedNote(_, site)),
      BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
        Vector(Gain.Warbands(actor, kind, Placed)))),
      BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
        Vector(Move(Piece.Warbands(kind, Placed),
          PositionedLocation(Location.PlayArea(actor)),
          PositionedLocation(Location.Site(site)))))),
      Note(id, placedNote(_, actor, site), covers = true))

  /** The warbands the kill step took from the site. */
  private def killedNote(states: NoteStates, site: SiteId)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    case SiteForces.Occupied(kind, count) <- forcesAt(before, site)
    lost = count - held(after, site, kind)
    if lost > 0
  yield killedLine(before, kind, lost, site)

  private def killedLine(ready: ReadyGame, kind: ForceKind, lost: Int,
      site: SiteId): PowerNote = owner(ready, kind) match
    case Some(player) => killed(source, NoteArg.Number(lost),
      NoteArg.Player(player), NoteArg.Site(site))
    case None => bandits(source, NoteArg.Number(lost), NoteArg.Site(site))

  /** The actor's warbands the place step put at the site. */
  private def placedNote(states: NoteStates, actor: PlayerId, site: SiteId)
      : Option[PowerNote] =
    PlayerFacts.forceKind(states.now, actor).toOption.map { kind =>
      val moved = states.previous.fold(0)((before, after) =>
        held(after, site, kind) - held(before, site, kind))
      if moved > 0 then placed(source, NoteArg.Player(actor),
        NoteArg.Amount(moved, NoteUnit.Warband), NoteArg.Site(site))
      else unplaced(source, NoteArg.Player(actor))
    }

object KeyToTheCity:
  val id: PowerId = PowerId("denizen.key-to-the-city")
  /** The warbands gained and placed. */
  val Placed: Int = 1
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warband at {site}." */
  val bandits: NoteKey = NoteKey("bandits", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Plural(0, " bandit warband at ",
      " bandit warbands at "), NotePart.Arg(1), NotePart.Text(".")))
  /** "{Red} placed {1 warband} at {site}." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} had no warband to place." */
  val unplaced: NoteKey = NoteKey("unplaced", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to place.")))
  /** "{Blue} was at {site}." */
  val guarded: NoteKey = NoteKey("guarded", Vector(NotePart.Arg(0),
    NotePart.Text(" was at "), NotePart.Arg(1), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): Option[KeyToTheCity] =
    WhenPlayedPower.cardOf(catalog, id).map(new KeyToTheCity(_))

  /** The player ruling `site` whose pawn stands there, if any. */
  private def guard(ready: ReadyGame, site: SiteId): Option[PlayerId] =
    SiteRulers.rulerOf(ready, site).collect {
      case SiteRuler.Player(ruler) if ready.game.current.players.exists(
        state => state.player == ruler && state.pawnSite.contains(site)) =>
        ruler
    }

  private def kill(ready: ReadyGame, site: SiteId): Vector[CoreOperation] =
    forcesAt(ready, site).toVector.collect {
      case SiteForces.Occupied(kind, count) if count > 0 => Kill(
        Piece.Warbands(kind, count), PositionedLocation(Location.Site(site)))
    }

  private def forcesAt(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The player whose warbands are of `kind`; none for bandits. */
  private def owner(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(state =>
      PlayerForceKind.of(ready, state).contains(kind)).map(_.player)
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.KeyToTheCitySuite"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCity.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/KeyToTheCitySuite.scala
git commit -m "feat(powers): add Key to the City"
```

### Task 5: Bandit Prince

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.forceKind`, `PlayerFacts.banked`, `Replace`, `WhenPlayedPowers.forCatalog` (Task 4).
- Produces:
  - `PowerAnswers.many(pending: PendingTree, decision: String): Option[Vector[DecisionOptionRef]]`. Salad Days uses it in Task 6.
  - `BanditPrince` (`id`, `decisionId`, `replaced`, `none`, `forCatalog`).

This task pins the spec's "Verify at plan time" item for Bandit Prince: a `BuildOps` that rejects after the answer refuses the command, so the decision stays parked (rulings, 8).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PlayerFacts}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class BanditPrinceSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[BanditPrince]
  private val prince = power.cardId
  private val played = hook(prince)
  /** Bandit Prince is adviser-only: p1 holds it. */
  private val start = Table.start.adviser(p1, prince)
  /** Bandits hold p1's site (2) and a site in another region (1). */
  private val bandited = start.bandits(homeSite, 2).bandits(awaySite, 1)
  private val mine = PlayerFacts.forceKind(start.ready, p1).toOption.get
  /** The two bandit sites, in map order. */
  private val sites: Vector[SiteId] =
    start.ready.game.current.map.inPlay.filter(Set(homeSite, awaySite))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def choose(chosen: Vector[SiteId]): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(chosen.map(DecisionOptionRef.Site(_)))

  /** `table` with p1's warband bank holding only `left`. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    table.warbands(p1, ready.banks.warbandSupply(mine) - left)

  private def replacedAt(site: SiteId): NoteText.Said =
    val n = if site == homeSite then 2 else 1
    NoteText.Said("replaced", s"Replaced $n " +
      s"${if n == 1 then "bandit" else "bandits"} at ${site.value} with " +
      s"${p1.value}'s warbands.", covers = true)

  test("it offers every bandit site, and replaces the bandits at the sites " +
      "chosen"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, BanditPrince.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(0, 2, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))), decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(sites)))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Occupied(mine, 2))
    assertEquals(after.forces(awaySite), SiteForces.Occupied(mine, 1))
    assertEquals(after.warbands(p1), 3)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), sites.map(replacedAt))

  test("declining replaces nothing and writes nothing"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    val done = finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(Vector.empty)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)

  test("with no bandit site nothing is asked, and the line says so"):
    val done = finished(play(start.ready, power, prince))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No site was ruled by bandits.", covers = false)))

  test("sites holding more bandits than the bank holds warbands are refused"):
    val ready = banking(bandited, left = 2).ready
    val first = parked(play(ready, power, prince))
    val offered = question(ready, power, played, first.tree).query
      .asInstanceOf[DecisionQuery.ChooseMany].options
    assertEquals(offered.size, 2)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(sites)).isLeft)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(homeSite))).isRight)

  test("a site the bandits do not rule is refused"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(nearSite))).isLeft)

  test("with an empty bank no site can be replaced, so nothing is asked"):
    val ready = banking(bandited, left = 0).ready
    val done = finished(play(ready, power, prince))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditPrinceSuite"`
Expected: compile error, `BanditPrince` not found.

- [ ] **Step 3: Add `PowerAnswers.many`**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala")
s = p.read_text()
old = "  def distribution(pending: PendingTree, decision: String)\n"
new = """  /** The options a power's `ChooseMany` was answered with. */
  def many(pending: PendingTree, decision: String)
      : Option[Vector[DecisionOptionRef]] = pending.answered.collectFirst:
    case Answered(`decision`, DecisionAnswer.ChooseManyAnswer(refs), _) => refs

""" + old
assert s.count(old) == 1
p.write_text(s.replace(old, new))
PY
```

- [ ] **Step 4: Write Bandit Prince and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Bandit Prince (card 226, adviser-only, locked), WHEN PLAYED: you may
  * replace all bandits at any sites you choose with your warbands.
  *
  * The bandit-ruled sites are read live and offered in map order. The actor
  * chooses any number of them, or none. At each chosen site a `Replace`
  * swaps all its bandits for the actor's warbands from their bank, in one
  * step. An answer whose sites hold more bandits than the bank holds is
  * refused, so a site is never half replaced. The walker's search hides a
  * site whose bandits alone exceed the bank; with none left the question is
  * not asked. One line per site tells the replacement, in answer order, and
  * covers that site's Moved line.
  */
final case class BanditPrince private (cardId: DenizenId)
    extends WhenPlayedPower:
  import BanditPrince._
  def id: PowerId = BanditPrince.id

  override def noteKeys: Vector[NoteKey] = Vector(replaced, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val sites = banditSites(live)
      if sites.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseMany(0, sites.size,
          sites.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))),
          heading = Some("Bandit Prince: choose the sites whose bandits your " +
            "warbands replace"))),
        BuildOps((ready, pending) => replace(ready, actor, pending)),
        Branch((_, pending) => chosen(pending).map(site =>
          Note(id, replacedNote(_, actor, site), covers = true))))))

  /** The actor's warbands the replacement put at `site`. */
  private def replacedNote(states: NoteStates, actor: PlayerId,
      site: SiteId): Option[PowerNote] = for
    (before, after) <- states.previous
    kind <- PlayerFacts.forceKind(after, actor).toOption
    count = held(after, site, kind)
    if count > 0 && bandits(before, site) > 0
  yield replaced(PowerSourceRef.Card(cardId), NoteArg.Number(count),
    NoteArg.Site(site), NoteArg.Player(actor))

object BanditPrince:
  val id: PowerId = PowerId("denizen.bandit-prince")
  val decisionId: String = "cardplay.bandit-prince.sites"
  /** "Replaced {n} bandit at {site} with {Red}'s warbands." */
  val replaced: NoteKey = NoteKey("replaced", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit at ", " bandits at "), NotePart.Arg(1),
    NotePart.Text(" with "), NotePart.Arg(2), NotePart.Text("'s warbands.")))
  /** "No site was ruled by bandits." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No site was ruled by bandits.")))

  def forCatalog(catalog: ExecutableCatalog): Option[BanditPrince] =
    WhenPlayedPower.cardOf(catalog, id).map(new BanditPrince(_))

  /** The sites in play that bandits hold, in map order. */
  private def banditSites(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(bandits(ready, _) > 0)

  private def bandits(ready: ReadyGame, site: SiteId): Int =
    held(ready, site, ForceKind.Bandit)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The sites answered, in answer order. A question the search did not ask
    * reads as none. */
  private def chosen(pending: PendingTree): Vector[SiteId] =
    PowerAnswers.many(pending, decisionId).getOrElse(Vector.empty).collect {
      case DecisionOptionRef.Site(site) => site }

  /** A `Replace` of all the bandits at each chosen site, refused when a
    * site is not a bandit site or the bank cannot cover them all. */
  private def replace(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val sites = chosen(pending)
    val offered = banditSites(ready)
    val counts = sites.map(site => site -> bandits(ready, site))
    val total = counts.map(_._2).sum
    for
      _ <- sites.find(!offered.contains(_)).toLeft(()).left.map(site =>
        OathViolation.InvalidEventOrder(
          s"${site.value} is not a site the bandits rule"))
      kind <- PlayerFacts.forceKind(ready, actor)
      banked = PlayerFacts.banked(ready, kind)
      _ <- Either.cond(total <= banked, (), OathViolation.InvalidEventOrder(
        s"the chosen sites hold $total bandits, more than the $banked " +
          "warbands in the bank"))
    yield counts.map { case (site, count) => Replace(
      Piece.Warbands(ForceKind.Bandit, count), Piece.Warbands(kind, count),
      PositionedLocation(Location.Site(site))) }
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector ++
      BanditPrince.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditPrinceSuite"`
Expected: PASS, 6 tests.

If "with an empty bank ... nothing is asked" parks instead, the search does not narrow a `ChooseMany` here. Stop and report: rulings 8 relies on it.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditPrince.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditPrinceSuite.scala
git commit -m "feat(powers): add Bandit Prince"
```

### Task 6: Salad Days

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/SaladDays.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/SaladDaysSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.many` (Task 5), `WhenPlayedPowers.forCatalog` (Task 5).
- Produces: `SaladDays` (`id`, `decisionId`, `Banks`, `empty`, `forCatalog`).

This task tests a seam for the first time: a `ChooseMany` whose options are favor banks, on the server and in the frontend panel (rulings, 15).

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/SaladDaysSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class SaladDaysSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[SaladDays]
  private val salad = power.cardId
  private val played = hook(salad)

  /** Salad Days as p1's adviser, each bank holding what `stocks` gives it
    * and every other bank empty. */
  private def staged(stocks: (Suit, Int)*): ReadyGame =
    val held = stocks.toMap
    Suit.all.foldLeft(Table.start.adviser(p1, salad))((table, suit) =>
      table.bankFavor(suit, held.getOrElse(suit, 0))).ready

  private def banks(ready: ReadyGame, suits: Suit*): Vector[Int] =
    suits.toVector.map(ready.banks.favor.getOrElse(_, 0))

  private def choose(suits: Suit*): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(
      suits.toVector.map(DecisionOptionRef.FavorBank(_)))

  private val four = Vector(Suit.Discord -> 2, Suit.Order -> 1,
    Suit.Hearth -> 3, Suit.Beast -> 1)

  test("with four stocked banks it asks for three, and takes 1 favor from " +
      "each"):
    val ready = staged(four*)
    val first = parked(play(ready, power, salad))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, SaladDays.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(3, 3,
      four.map((suit, _) => DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(suit))), decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      SaladDays.decisionId, choose(Suit.Discord, Suit.Hearth, Suit.Beast)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(banks(done.treeless, Suit.Discord, Suit.Order, Suit.Hearth,
      Suit.Beast), Vector(1, 1, 2, 0))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)

  test("with three stocked banks it takes 1 from each without asking"):
    val ready = staged(Suit.Arcane -> 1, Suit.Order -> 2, Suit.Nomad -> 5)
    val done = finished(play(ready, power, salad))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(banks(done.treeless, Suit.Arcane, Suit.Order, Suit.Nomad),
      Vector(0, 1, 4))

  test("with two stocked banks it takes 1 from each"):
    val done = finished(play(staged(Suit.Order -> 2, Suit.Nomad -> 5), power,
      salad))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)

  test("with every bank empty it gains nothing, and the line says so"):
    val done = finished(play(staged(), power, salad))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("none", "Every favor bank was empty.",
        covers = false)))

  test("an empty bank is not a choice"):
    val ready = staged(four*)
    val first = parked(play(ready, power, salad))
    assert(resume(ready, power, played, first.tree, SaladDays.decisionId,
      choose(Suit.Discord, Suit.Arcane, Suit.Beast)).isLeft)
```

Append the frontend test to `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`:

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala")
s = p.read_text()
test = '''
  test("choose-many offers favor banks as toggles and submits the banks " +
      "chosen"):
    def bank(suit: String, label: String) =
      DecisionOptionState("favor-bank", suit, label)
    val banks = DecisionQueryState.ChooseMany(Vector(bank("discord", "Discord"),
      bank("order", "Order"), bank("hearth", "Hearth"), bank("beast", "Beast")),
      minOptions = 3, maxOptions = 3,
      heading = Some("Salad Days: choose three banks to gain 1 favor from each"))
    val id = "cardplay.salad-days.banks"
    val chosen = Vector("discord", "hearth", "beast")
    val ui = new RecordingControls()
    val draft = chosen.foldLeft(opened(id, banks)) { (current, suit) =>
      one(render(ui, current, id, banks),
        s"""[data-option-id="favor-bank:$suit"]""")
        .asInstanceOf[dom.html.Button].click()
      ui.drafts.selection
    }
    val panel = render(ui, draft, id, banks)
    assertEquals(panel.querySelectorAll(".walker-many-option").length, 4)
    val confirm = one(panel, ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(id,
      DecisionAnswerWire.ChooseManyWire(
        chosen.map(DecisionOptionWire("favor-bank", _))))))
'''
p.write_text(s.rstrip("\n") + "\n" + test)
PY
```

- [ ] **Step 2: Run them to verify the server suite fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.SaladDaysSuite" "frontend/testOnly oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: the server suite fails to compile, `SaladDays` not found. The frontend suite passes as it is (rulings, 15). If the frontend test fails, the panel needs a production change: stop and report, since the spec drops the card under the bar.

- [ ] **Step 3: Write Salad Days and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/SaladDays.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Salad Days (card 147), WHEN PLAYED: gain 3 favor, one each from three
  * different favor banks.
  *
  * The banks holding favor are read live, in suit order. With four or more,
  * the actor chooses three. With one to three, the actor gains 1 from each
  * and is not asked. The Gain lines name the banks, so the choice is
  * narrated and the power writes no line of its own, except when every bank
  * is empty.
  */
final case class SaladDays private (cardId: DenizenId)
    extends WhenPlayedPower:
  import SaladDays._
  def id: PowerId = SaladDays.id

  override def noteKeys: Vector[NoteKey] = Vector(empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val banks = stocked(live)
      if banks.isEmpty then Vector(Note(id, _ =>
        Some(empty(PowerSourceRef.Card(cardId)))))
      else if banks.size <= Banks then Vector(BuildOps((ready, _) =>
        Right(gains(actor, stocked(ready)))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseMany(Banks, Banks,
          banks.map(suit => DecisionOption.FavorBank(
            DecisionOptionRef.FavorBank(suit))),
          heading = Some("Salad Days: choose three banks to gain 1 favor " +
            "from each"))),
        BuildOps((ready, pending) => chosen(ready, pending)
          .map(gains(actor, _))))))

object SaladDays:
  val id: PowerId = PowerId("denizen.salad-days")
  val decisionId: String = "cardplay.salad-days.banks"
  /** The banks it takes from. */
  val Banks: Int = 3
  /** The favor it takes from each. */
  val Each: Int = 1
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Every favor bank was empty.")))

  def forCatalog(catalog: ExecutableCatalog): Option[SaladDays] =
    WhenPlayedPower.cardOf(catalog, id).map(new SaladDays(_))

  /** The banks that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  private def gains(actor: PlayerId, suits: Vector[Suit])
      : Vector[CoreOperation] = suits.map(Gain.Favor(actor, _, Each))

  /** The three different stocked banks answered. */
  private def chosen(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[Suit]] =
    val banks = stocked(ready)
    PowerAnswers.many(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).flatMap { refs =>
        val suits = refs.collect { case DecisionOptionRef.FavorBank(suit) =>
          suit }
        Either.cond(suits.size == refs.size && suits.distinct.size == Banks &&
          suits.forall(banks.contains), suits, OathViolation.InvalidEventOrder(
            "Salad Days takes from three different banks that hold favor"))
      }
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector ++
      BanditPrince.forCatalog(catalog).toVector ++
      SaladDays.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.SaladDaysSuite" "frontend/testOnly oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: PASS, 5 server tests, and the frontend suite with its new test.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/SaladDays.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/SaladDaysSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala
git commit -m "feat(powers): add Salad Days"
```

### Task 7: Fabled Feast and Town Meeting

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/FabledFeast.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TownMeeting.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/FabledFeastSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/TownMeetingSuite.scala`

**Interfaces:**
- Consumes: `RuledCards.of(catalog, ready, ruler: SiteRuler, suit): Vector[CardId]` (P1), `FavorSplit` (`stocked`, `ask`, `split`, P8), `NoteSupport.gainedFromNote`, `NoteSupport.gainedNote`, `NoteSupport.gainedKey`, `PowerAnswers.one`, `WhenPlayedPowers.forCatalog` (Task 6).
- Produces: `FabledFeast` (`id`, `decisionId`, `took`, `empty`, `none`, `forCatalog`) and `TownMeeting` (`id`, `decisionId`, `gained`, `empty`, `none`, `forCatalog`).

Both count X the same way: the Hearth cards the actor rules, read live with `RuledCards.of`, the card itself included where it is ruled.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/FabledFeastSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class FabledFeastSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[FabledFeast]
  private val feast = power.cardId
  private val played = hook(feast)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** `table` with every bank empty except `stocks`. */
  private def banks(table: Table, stocks: (Suit, Int)*): Table =
    val held = stocks.toMap
    Suit.all.foldLeft(table)((next, suit) =>
      next.bankFavor(suit, held.getOrElse(suit, 0)))

  /** Three Hearth cards p1 rules: Fabled Feast and a Hearth adviser, and a
    * Hearth denizen at p1's site, which p1 rules. A Hearth denizen at a site
    * nobody rules does not count. */
  private val ruling: Table = Table.start.adviser(p1, feast)
    .adviser(p1, hearth(0)).warbandsAt(homeSite, p1, 1)
    .denizen(hearth(1), at = homeSite).denizen(hearth(2), at = nearSite)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(n: Int, suit: Suit): NoteText.Said = NoteText.Said("took",
    s"${p1.value} took $n favor from the $suit bank.", covers = true)

  private def bankAnswer(suit: Suit): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))

  test("with two stocked banks it asks which, and takes X favor from it"):
    val ready = banks(ruling, Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, feast))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, FabledFeast.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(Suit.Order, Suit.Hearth).map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Order)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(done.treeless.banks.favor(Suit.Order), 2)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(took(3, Suit.Order)))

  test("a short bank gives what it holds"):
    val ready = banks(ruling, Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, feast))
    val done = finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Hearth)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(took(2, Suit.Hearth)))

  test("one stocked bank is taken from without asking"):
    val done = finished(play(banks(ruling, Suit.Nomad -> 5).ready, power,
      feast))
    assertEquals(said(done.events), Vector(took(3, Suit.Nomad)))

  test("played to a site p1 rules it counts itself"):
    val ready = banks(Table.start.denizen(feast, at = homeSite)
      .warbandsAt(homeSite, p1, 1), Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(said(done.events), Vector(took(1, Suit.Hearth)))

  test("played to a site nobody rules, with no other Hearth card, it takes " +
      "nothing"):
    val ready = banks(Table.start.denizen(feast, at = homeSite),
      Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val done = finished(play(banks(ruling).ready, power, feast))
    assertEquals(said(done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
```

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/TownMeetingSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class TownMeetingSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[TownMeeting]
  private val meeting = power.cardId
  private val played = hook(meeting)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** `table` with every bank empty except `stocks`. */
  private def banks(table: Table, stocks: (Suit, Int)*): Table =
    val held = stocks.toMap
    Suit.all.foldLeft(table)((next, suit) =>
      next.bankFavor(suit, held.getOrElse(suit, 0)))

  /** Three Hearth cards p1 rules: Town Meeting and two Hearth advisers. */
  private val ruling: Table = Table.start.adviser(p1, meeting)
    .adviser(p1, hearth(0)).adviser(p1, hearth(1))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n favor.", covers = false)

  test("with more than X favor across two banks it asks for the split"):
    val ready = banks(ruling, Suit.Order -> 2, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, meeting))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      TownMeeting.decisionId)
    val done = finished(resume(ready, power, played, first.tree,
      TownMeeting.decisionId, DecisionAnswer.DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Order), 1),
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Hearth), 2)))))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(Vector(Suit.Order, Suit.Hearth).map(
      done.treeless.banks.favor(_)), Vector(1, 0))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(gained(3)))

  test("with X or less in all it takes everything without asking"):
    val done = finished(play(banks(ruling, Suit.Order -> 1,
      Suit.Hearth -> 1).ready, power, meeting))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(gained(2)))

  test("ruling no Hearth card it takes nothing, and the line says so"):
    val ready = banks(Table.start.denizen(meeting, at = homeSite),
      Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(meeting, homeSite)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val done = finished(play(banks(ruling).ready, power, meeting))
    assertEquals(said(done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.FabledFeastSuite oathdigital.gameplay.powers.whenplayed.TownMeetingSuite"`
Expected: compile errors, `FabledFeast` and `TownMeeting` not found.

- [ ] **Step 3: Write both powers and register them**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/FabledFeast.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, RuledCards}
import oathdigital.model._

/** Fabled Feast (card 136), WHEN PLAYED: take X favor equal to the number of
  * Hearth cards you rule (including Fabled Feast) from any one favor bank.
  *
  * X is read live with `RuledCards.of`, so Fabled Feast counts itself as a
  * faceup adviser or at a site the actor rules, and not at a site the actor
  * does not rule. The actor chooses one bank that holds favor; with only one
  * it is taken unasked. The gain is best effort, so a short bank gives what
  * it holds. Its line names the bank, so the choice is narrated, and it
  * covers the Gain line.
  */
final case class FabledFeast private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import FabledFeast._
  def id: PowerId = FabledFeast.id

  override def noteKeys: Vector[NoteKey] = Vector(took, empty, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    val source = PowerSourceRef.Card(cardId)
    Vector(Branch((live, _) =>
      val count = RuledCards.of(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth).size
      val banks = stocked(live)
      if count == 0 then Vector(Note(id, _ =>
        Some(none(source, NoteArg.Player(actor)))))
      else if banks.isEmpty then Vector(Note(id, _ => Some(empty(source))))
      else ask(actor, banks, count) ++ Vector(
        BuildOps((ready, pending) => take(ready, actor, pending, count)),
        Note(id, NoteSupport.gainedFromNote(took, source, actor),
          covers = true))))

object FabledFeast:
  val id: PowerId = PowerId("denizen.fabled-feast")
  val decisionId: String = "cardplay.fabled-feast.bank"
  /** "{Red} took {n favor} from {the Hearth bank}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  /** "{Red} ruled no Hearth card." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" ruled no Hearth card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[FabledFeast] =
    WhenPlayedPower.cardOf(catalog, id).map(new FabledFeast(_, catalog))

  /** The banks that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  /** The question, when two or more banks hold favor. */
  private def ask(actor: PlayerId, banks: Vector[Suit], count: Int)
      : Vector[Operation] =
    if banks.size < 2 then Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      banks.map(suit => DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(suit))),
      heading = Some(s"Fabled Feast: take $count favor from one bank"))))

  /** The gain from the only stocked bank, or from the bank answered. */
  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree,
      count: Int): Either[OathViolation, Vector[CoreOperation]] =
    val banks = stocked(ready)
    val bank = banks match
      case Vector(only) => Right(only)
      case _ => PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId)).flatMap {
          case DecisionOptionRef.FavorBank(suit) if banks.contains(suit) =>
            Right(suit)
          case other => Left(OathViolation.InvalidEventOrder(
            s"${other.wireId} is not a bank that holds favor"))
        }
    bank.map(suit => Vector(Gain.Favor(actor, suit, count)))
```

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/TownMeeting.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, RuledCards}
import oathdigital.gameplay.powers.action.FavorSplit
import oathdigital.model._

/** Town Meeting (card 236), WHEN PLAYED: gain favor from any favor bank for
  * each Hearth card you rule. This includes your advisers, even Town
  * Meeting.
  *
  * X is counted as Fabled Feast counts it. The favor comes from any banks,
  * split as Alchemist splits ([[FavorSplit]]): the actor is asked only when
  * two or more banks hold favor and they hold more than X in all. The
  * rulings leave open whether the text means one bank for all; the split is
  * used until that is settled. Its line tells the whole gain; each bank's
  * Gain line stays.
  */
final case class TownMeeting private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import TownMeeting._
  def id: PowerId = TownMeeting.id

  override def noteKeys: Vector[NoteKey] = Vector(gained, empty, none)

  private val banks = new FavorSplit(decisionId, Suit.all, "Take favor")

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    val source = PowerSourceRef.Card(cardId)
    Vector(Branch((live, _) =>
      val count = RuledCards.of(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth).size
      if count == 0 then Vector(Note(id, _ =>
        Some(none(source, NoteArg.Player(actor)))))
      else if banks.stocked(live).isEmpty then
        Vector(Note(id, _ => Some(empty(source))))
      else banks.ask(live, actor, count,
          s"Town Meeting: take $count favor from any banks") ++ Vector(
        BuildOps((ready, pending) => banks.split(ready, pending, count).map(
          _.map { case (suit, n) => Gain.Favor(actor, suit, n) })),
        Note(id, NoteSupport.gainedNote(gained, source, actor,
          NoteUnit.Favor, NoteSupport.favor)))))

object TownMeeting:
  val id: PowerId = PowerId("denizen.town-meeting")
  val decisionId: String = "cardplay.town-meeting.banks"
  /** "{Red} gained {n favor}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  /** "{Red} ruled no Hearth card." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" ruled no Hearth card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[TownMeeting] =
    WhenPlayedPower.cardOf(catalog, id).map(new TownMeeting(_, catalog))
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector ++
      BanditPrince.forCatalog(catalog).toVector ++
      SaladDays.forCatalog(catalog).toVector ++
      FabledFeast.forCatalog(catalog).toVector ++
      TownMeeting.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.FabledFeastSuite oathdigital.gameplay.powers.whenplayed.TownMeetingSuite"`
Expected: PASS, 6 and 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/FabledFeast.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/TownMeeting.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/FabledFeastSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/TownMeetingSuite.scala
git commit -m "feat(powers): add Fabled Feast and Town Meeting"
```

### Task 8: Great Herd

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/GreatHerd.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/GreatHerdSuite.scala`

**Interfaces:**
- Consumes: `SiteRulers.siteOf`, `Swap`, `PowerAnswers.one`, `NoteSupport.answer`, `WhenPlayedPowers.forCatalog` (Task 7).
- Produces: `GreatHerd` (`id`, `decisionId`, `keep`, `swapped`, `none`, `forCatalog`).

This task tests a seam for the first time: a `Swap` of two site cards (rulings, 12).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/GreatHerdSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class GreatHerdSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[GreatHerd]
  private val herd = power.cardId
  private val played = hookAt(herd, homeSite)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val nomadEdifices = edificesOf(Suit.Nomad)
  /** Great Herd is site-only: it stands at p1's site. */
  private val start = Table.start.denizen(herd, at = homeSite)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def offered(ready: ReadyGame, tree: PendingTree)
      : Vector[DecisionOptionRef] = question(ready, power, played, tree).query
    .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)

  private def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  test("it offers the Nomad cards at other sites, then keeping it"):
    // A Nomad denizen at Great Herd's own site and an intact Nomad edifice
    // are not offered.
    val ready = start.denizen(nomad(0), at = nearSite)
      .denizen(nomad(1), at = homeSite)
      .edifice(nomadEdifices(0), EdificeSide.Intact, at = awaySite).ready
    val first = parked(fire(ready, power, played))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      GreatHerd.decisionId)
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Denizen(nomad(0)), GreatHerd.keep))

  test("the swap moves each card to the other's site with its tokens"):
    val ready = start.denizen(nomad(0), at = nearSite)
      .tokens(nomad(0), favor = 1, secrets = 2).ready
    val first = parked(fire(ready, power, played))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Denizen(nomad(0)))))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector[CardId](nomad(0)))
    assertEquals(after.denizens(nearSite), Vector[CardId](herd))
    assertEquals(after.tokensOn(nomad(0)), Tokens(1, 2))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(NoteText.Said("swapped",
      s"${p1.value} swapped it with ${nomad(0).value} at ${nearSite.value}.",
      covers = false)))

  test("a ruined Nomad edifice can be swapped too"):
    val ruined = nomadEdifices(0)
    val ready = start.edifice(ruined, EdificeSide.Ruined, at = awaySite).ready
    val first = parked(fire(ready, power, played))
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Edifice(ruined), GreatHerd.keep))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Edifice(ruined))))
    assertEquals(Look(done.treeless).denizens(homeSite),
      Vector[CardId](ruined))
    assertEquals(Look(done.treeless).denizens(awaySite), Vector[CardId](herd))

  test("keeping Great Herd changes nothing and writes nothing"):
    val ready = start.denizen(nomad(0), at = nearSite).ready
    val first = parked(fire(ready, power, played))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(GreatHerd.keep)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)

  test("with no Nomad card at another site nothing is asked, and the line " +
      "says so"):
    val done = finished(fire(start.denizen(nomad(1), at = homeSite).ready,
      power, played))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No Nomad card could be swapped.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.GreatHerdSuite"`
Expected: compile error, `GreatHerd` not found.

- [ ] **Step 3: Write Great Herd and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/GreatHerd.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Great Herd (card 30, site-only), WHEN PLAYED: you may swap Great Herd
  * with a Nomad card at any site.
  *
  * The candidates are the faceup Nomad denizens and ruined Nomad edifices at
  * every other site in play, whoever rules it, read live and in map order.
  * With none, nothing is asked and the line says so. Otherwise the actor
  * picks one or keeps Great Herd. The swap is a required batch, so the
  * walker's search hides a card whose swap a restriction refuses. Each card
  * keeps its favor and secrets, and the card moved in does not run its own
  * WHEN PLAYED. The effect is one `Branch`, so the node count this power
  * adds to the card-played window never depends on live state.
  */
final case class GreatHerd private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import GreatHerd._
  def id: PowerId = GreatHerd.id

  override def noteKeys: Vector[NoteKey] = Vector(swapped, none)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = candidates(live)
      if found.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          found.map(_.option) :+ DecisionOption.Button(keep, "Keep Great Herd"),
          heading = Some("Great Herd: swap it with a Nomad card at another " +
            "site?"))),
        BuildOps((ready, pending) => swap(ready, pending), required = true),
        Note(id, swappedNote(_, actor)))))

  /** The faceup Nomad denizens and ruined Nomad edifices at every other
    * site in play, in map order. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    val map = ready.game.current.map
    SiteRulers.siteOf(ready, cardId).toVector.flatMap(here => map.inPlay
      .filter(_ != here).flatMap(site => map.sites.get(site).toVector
        .flatMap(_.denizens.collect {
          case DenizenState(card, Orientation.FaceUp, _) if nomad(card) =>
            Candidate(site, card, DecisionOption.Denizen(
              DecisionOptionRef.Denizen(card)))
          case EdificeState(card, EdificeSide.Ruined, _) if nomad(card) =>
            Candidate(site, card, DecisionOption.Edifice(
              DecisionOptionRef.Edifice(card)))
        })))

  private def nomad(card: CardId): Boolean =
    catalog.suitOf(card).contains(Suit.Nomad)

  private def swap(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`keep`) => Right(Vector.empty)
      case Some(ref) => swapWith(ready, ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a Nomad card Great Herd " +
          "can swap with"))
      case None => Left(PowerAnswers.missing(decisionId))

  private def swapWith(ready: ReadyGame, ref: DecisionOptionRef)
      : Option[Vector[CoreOperation]] = for
    here <- SiteRulers.siteOf(ready, cardId)
    chosen <- candidates(ready).find(_.option.ref == ref)
  yield Vector(Swap(cardId, PositionedLocation(Location.Site(here)),
    chosen.card, PositionedLocation(Location.Site(chosen.site))))

  /** Written only when the chosen card now stands where Great Herd stood. */
  private def swappedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    ref <- NoteSupport.answer(states, decisionId)
    (before, after) <- states.previous
    here <- SiteRulers.siteOf(before, cardId)
    chosen <- candidates(before).find(_.option.ref == ref)
    if after.game.current.map.sites.get(here)
      .exists(_.denizens.exists(_.id == chosen.card))
  yield swapped(PowerSourceRef.Card(cardId), NoteArg.Player(actor),
    NoteArg.Card(chosen.card), NoteArg.Site(chosen.site))

object GreatHerd:
  val id: PowerId = PowerId("denizen.great-herd")
  val decisionId: String = "cardplay.great-herd.swap"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** "{Red} swapped it with {card} at {site}." */
  val swapped: NoteKey = NoteKey("swapped", Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it with "), NotePart.Arg(1), NotePart.Text(" at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No Nomad card could be swapped." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No Nomad card could be swapped.")))

  private final case class Candidate(site: SiteId, card: CardId,
      option: DecisionOption)

  def forCatalog(catalog: ExecutableCatalog): Option[GreatHerd] =
    WhenPlayedPower.cardOf(catalog, id).map(new GreatHerd(_, catalog))
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector ++
      BanditPrince.forCatalog(catalog).toVector ++
      SaladDays.forCatalog(catalog).toVector ++
      FabledFeast.forCatalog(catalog).toVector ++
      TownMeeting.forCatalog(catalog).toVector ++
      GreatHerd.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.GreatHerdSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/GreatHerd.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/GreatHerdSuite.scala
git commit -m "feat(powers): add Great Herd"
```

### Task 9: Royal Tax

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/RoyalTax.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/RoyalTaxSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.ruledSites(ready, actor): Set[SiteId]`, `Take`, `NoteSupport.favor`, `WhenPlayedPowers.forCatalog` (Task 8).
- Produces: `RoyalTax` (`id`, `Taken`, `took`, `broke`, `none`, `forCatalog`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/RoyalTaxSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class RoyalTaxSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[RoyalTax]
  private val tax = power.cardId

  /** Royal Tax as p1's adviser. p1 rules its own site and another site in
    * its region. p2 stands at the other site with 3 favor, and p3 at p1's
    * site with `held`. */
  private def staged(held: Int): ReadyGame = Table.start.adviser(p1, tax)
    .warbandsAt(homeSite, p1, 1).warbandsAt(nearSite, p1, 1)
    .pawn(p2, nearSite).favor(p2, 3).pawn(p3, homeSite).favor(p3, held).ready

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(n: Int, from: PlayerId): NoteText.Said = NoteText.Said(
    "took", s"${p1.value} took $n favor from ${from.value}.", covers = false)

  test("each player at a site p1 rules in p1's region gives 2 favor, or " +
      "what they hold"):
    val ready = staged(held = 1)
    val done = finished(play(ready, power, tax))
    val after = Look(done.treeless)
    assertEquals(after.favor(p1), 1 + 2 + 1)
    assertEquals(after.favor(p2), 1)
    assertEquals(after.favor(p3), 0)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(took(2, p2), took(1, p3)))

  test("a player with no favor is named, and nothing is taken from them"):
    val done = finished(play(staged(held = 0), power, tax))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(took(2, p2), NoteText.Said("broke",
      s"${p3.value} had no favor to take.", covers = false)))

  test("players at unruled sites or outside the region are not taxed"):
    // p2 stands in p1's region at a site nobody rules; p3 stands at a site
    // p1 rules in another region.
    val ready = Table.start.adviser(p1, tax).warbandsAt(awaySite, p1, 1)
      .pawn(p2, nearSite).pawn(p3, awaySite).ready
    val done = finished(play(ready, power, tax))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No player could be taxed.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RoyalTaxSuite"`
Expected: compile error, `RoyalTax` not found.

- [ ] **Step 3: Write Royal Tax and register it**

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/RoyalTax.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Royal Tax (card 117), WHEN PLAYED: take 2 favor from each player whose
  * pawn is at a site you rule in your pawn's region.
  *
  * The targets are read live: every other player, in seat order from the
  * actor, whose pawn stands at a site the actor rules in the region of the
  * actor's pawn. Nothing is asked. Each target gives 2 favor by a `Take`, or
  * what they hold, with a line of its own. A target holding no favor is
  * named instead. A take a restriction refuses writes no line.
  */
final case class RoyalTax private (cardId: DenizenId)
    extends WhenPlayedPower:
  import RoyalTax._
  def id: PowerId = RoyalTax.id

  override def noteKeys: Vector[NoteKey] = Vector(took, broke, none)

  private def source: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = targets(live, actor)
      if found.isEmpty then Vector(Note(id, _ => Some(none(source))))
      else found.flatMap(tax(actor, _))))

  private def tax(actor: PlayerId, target: PlayerState): Vector[Operation] =
    if target.board.favor == 0 then Vector(Note(id, _ =>
      Some(broke(source, NoteArg.Player(target.player)))))
    else Vector(
      BuildOps((_, _) => Right(Vector(Take(Piece.Favor(Taken), actor,
        Location.PlayArea(target.player), Location.PlayArea(actor))))),
      Note(id, tookNote(_, actor, target.player)))

  /** The favor the take step moved from `target`. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      target: PlayerId): Option[PowerNote] =
    val lost = -states.previous.fold(0)(NoteSupport.favor(_, target))
    Option.when(lost > 0)(took(source, NoteArg.Player(actor),
      NoteArg.Amount(lost, NoteUnit.Favor), NoteArg.Player(target)))

object RoyalTax:
  val id: PowerId = PowerId("denizen.royal-tax")
  /** The favor each target gives. */
  val Taken: Int = 2
  /** "{Red} took {n favor} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Blue} had no favor to take." */
  val broke: NoteKey = NoteKey("broke", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to take.")))
  /** "No player could be taxed." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No player could be taxed.")))

  def forCatalog(catalog: ExecutableCatalog): Option[RoyalTax] =
    WhenPlayedPower.cardOf(catalog, id).map(new RoyalTax(_))

  /** Every other player, in seat order from the actor, whose pawn stands at
    * a site the actor rules in the region of the actor's pawn. */
  private def targets(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    val current = ready.game.current
    val region = current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)
    val taxed = PowerAccess.ruledSites(ready, actor)
      .filter(site => region.exists(current.map.regionOf(site).contains))
    val seat = current.players.indexWhere(_.player == actor)
    (current.players.drop(seat + 1) ++ current.players.take(seat))
      .filter(_.pawnSite.exists(taxed.contains))
```

In `WhenPlayedPowers.scala`, replace the body of `forCatalog` with:

```scala
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector ++
      AnimalHost.forCatalog(catalog).toVector ++
      Charlatan.forCatalog(catalog).toVector ++
      KeyToTheCity.forCatalog(catalog).toVector ++
      BanditPrince.forCatalog(catalog).toVector ++
      SaladDays.forCatalog(catalog).toVector ++
      FabledFeast.forCatalog(catalog).toVector ++
      TownMeeting.forCatalog(catalog).toVector ++
      GreatHerd.forCatalog(catalog).toVector ++
      RoyalTax.forCatalog(catalog).toVector
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RoyalTaxSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/RoyalTax.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/RoyalTaxSuite.scala
git commit -m "feat(powers): add Royal Tax"
```

### Task 10: Flip the reviewed stubs, record the batch, and run the gates

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Flip the eleven reviewed stubs to implemented**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala")
s = p.read_text()
for name in ["ThreateningRoar", "AnimalHost", "KeyToTheCity", "Charlatan",
             "FabledFeast", "SaladDays", "GreatHerd", "RoyalTax", "Riots",
             "BanditPrince", "TownMeeting"]:
    start = s.index(f"  object {name} extends ReviewedPower(")
    end = s.index("\n", start)
    line = s[start:end]
    assert line.endswith(", played)"), line
    s = s[:start] + line[:-len("played)")] + "playedDone)" + s[end:]
p.write_text(s)
PY
grep -c "playedDone)" src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala
```

Expected: `15` (the eleven new ones, plus A Small Favor, Family Heirloom, Faithful Friend and Garrison).

- [ ] **Step 2: Update the roadmap**

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("docs/ROADMAP.md")
s = p.read_text()
replacements = [
    ("**Phase - Cleanup tasks** is done except for one item blocked on the\n"
     "Chronicle Phase. **Phase - Catalog batch 3** is next, then **Phase - Card\n"
     "classes**.",
     "**Phase - Cleanup tasks** is done except for one item blocked on the\n"
     "Chronicle Phase. **Phase - Catalog batch 3** is complete. **Phase - Card\n"
     "classes** is next."),
    ("Paymaster, Storyteller, Levelers, Memory of Home, Firebrand and Ballot Box.\n"
     "Slice 4 remains.",
     "Paymaster, Storyteller, Levelers, Memory of Home, Firebrand and Ballot Box.\n"
     "Slice 4 is done: the When Played powers Threatening Roar, Riots, Animal\n"
     "Host, Key to the City, Charlatan, Bandit Prince, Salad Days, Fabled Feast,\n"
     "Town Meeting, Great Herd and Royal Tax, with Dazzle's region discard\n"
     "shared and its `none` line. Catalog batch 3 is complete."),
    ("### Powers-related deferred items\n\n",
     "### Powers-related deferred items\n\n"
     "- [ ] **Town Meeting's ruling.** Town Meeting splits its favor across the\n"
     "  banks, as Alchemist does, but its text may mean one bank for all, as\n"
     "  Fabled Feast's does. The\n"
     "  [catalog batch 3 rulings](superpowers/specs/2026-09-29-catalog-batch-3-rulings.md)\n"
     "  leave it unresolved. A product-owner answer settles it; one bank would\n"
     "  reuse Fabled Feast's question.\n"
     "- [ ] **a favor bank in a choose-many shows no suit symbol.**\n"
     "  `WalkerSelectionPanels` renders a choose-many option as a toggle with\n"
     "  its label only, while a choose-one bank option prints the suit symbol\n"
     "  (`SuitGlyphSuite`). Salad Days is the first choose-many of banks.\n"),
]
for old, new in replacements:
    assert s.count(old) == 1, old
    s = s.replace(old, new)
p.write_text(s)
PY
```

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. Server: the baseline plus 50 (Tasks 1 to 9 add 3, 5, 7, 6, 6, 5, 10, 5 and 3 tests). Frontend: the baseline plus 1.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 4"
```
