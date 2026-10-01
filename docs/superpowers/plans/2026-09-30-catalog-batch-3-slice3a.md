# Catalog Batch 3, Slice 3a (Actions on Yourself) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the ACTION powers Blood Pact (62), Arcane Brokers (204), Bog (210), Relic Breaker (139), Bed of Roots (212), Tavern Songs (54), Tinker's Fair (13), Skeleton Key (R13) and Messenger (105), with the refactors P5 and P6.

**Architecture:**

- Every power is a `PaidAction`, the ACTION phase power whose engine pays the cost onto the source card. Each is registered in `SelfActionPowers`, which `PhasePowerCatalog` already includes.
- **P5.** `PowerAnswers.amount` reads a `ChooseAmount` answer. Blood Pact is the first power to park a `ChooseAmount`.
- **P6.** Warning Signals' warband distribution moves into `WarbandArrangement`, which takes the site set. Warning Signals passes the sites it defends and Messenger the sites its user rules.
- Three shared pieces are extracted inside `gameplay/powers`, which the spec's bar allows:
  - `HeldRelicSpend`: choose a relic you hold, then spend it and gain the reward in one required batch. Arcane Brokers, Bog and Relic Breaker use it.
  - `RelicDraws.drawSteps`: Dowsing Sticks' draw and its two lines, shared with Tinker's Fair.
  - `SiteRelicTake`: Barbed Net's peek, choice and take, parameterised by the site it reaches, shared with Skeleton Key.
- Each extraction keeps the existing suites of the power it comes from passing unchanged.

**Engine changes:** none. No new operation, walker window, decision query kind, option kind, procedure step, `NoteArg` kind, protocol change or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Seams used for the first time", "Log lines", "Slice 3: ACTION powers", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch" and "Slice 3a: actions on yourself"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

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
- Log lines, exactly (spec, "Log lines", "Slice 3"). `{Red}` is the acting player. Each line's key is given, and "covers" names the generic lines it replaces:
  - Blood Pact, key `used`: "Blood Pact: {Red} sacrificed {n warbands} and gained {m secrets}." Covers the gain line. Key `used.none`: "Blood Pact: {Red} sacrificed no warbands."
  - Arcane Brokers, key `used`: "Arcane Brokers: {Red} discarded {relic} and gained {n secrets}." Covers the gain line. A relic discard has no generic line. Key `used.none`: "Arcane Brokers: {Red} held no relic."
  - Bog, key `used`: "Bog: {Red} discarded {relic} and gained {n favor} from {the Beast bank}." Covers the gain line. Key `used.discarded`, when the Beast bank was empty: "Bog: {Red} discarded {relic}." Key `used.none`: "Bog: {Red} held no relic."
  - Relic Breaker, key `used`: "Relic Breaker: {Red} buried {relic} and gained {1 secret}." Covers the Buried and gain lines. Key `used.none`: "Relic Breaker: {Red} held no relic."
  - Bed of Roots, key `used`: "Bed of Roots: {Red} buried {card} and gained {n secrets}." Covers the Buried and gain lines. Key `used.none`: "Bed of Roots: {Red} had no faceup adviser."
  - Tavern Songs, key `used`: "Tavern Songs: {Red} peeked at the top of the {Cradle discard pile}: {cards}." Covers the Peeked lines. Key `used.empty`: "Tavern Songs: {Red} peeked at the {Cradle discard pile}, which was empty."
  - Tinker's Fair, key `used`: "Tinker's Fair: {Red} drew {relic} facedown." Key `used.empty`: "Tinker's Fair: The relic deck was empty."
  - Skeleton Key, key `used.peeked`: "Skeleton Key: {Red} peeked at the relics at {site}: {relics}." Covers the Peeked lines (ruling 6). Key `used`: "Skeleton Key: {Red} took {relic} facedown from {site}." Key `used.none`: "Skeleton Key: {site} held no relic." Key `used.away`: "Skeleton Key: {Red} was not at a Hinterland site."
  - Messenger, key `used`: "Messenger: {Red} redistributed their warbands." Covers nothing: the Moved lines stay. Key `used.none`: "Messenger: No warband could be moved."
  - **No line:** Tavern Songs' `Inspect`, which the peek line tells, as for Scryer.
  - Every decision whose choice the note names is declared narrated, as Fae Merchant's is: Arcane Brokers, Bog, Relic Breaker and Bed of Roots. A `ChooseAmount` and a `Distribute` answer post no "Chose" line anyway.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **Locked and the Grand Scepter.** The Grand Scepter cannot be discarded or buried. No power filters it: its restriction refuses the operation, and the search hides the option. `Bury` still ignores locked.
  - **"X to gain Y".** Y is gained only when X happened: Arcane Brokers, Bog and Relic Breaker.
  - **No target.** A paid power with no legal target pays its cost and does nothing, as Charming Friend does.
  - **Amounts are best effort.** A gain resolves to what its source holds.
- Baseline: `main` after slice 2b passes 2331 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in slices 1 and 2. Every suite below runs through `PhasePowerCatalog.default`, so an unregistered power fails its suite. No card in this slice has a reviewed stub in Scala to retire.
2. **The spend and the gain run as one required batch.** Arcane Brokers, Bog, Relic Breaker and Bed of Roots put the discard or bury and the gain in one `BuildOps` with `required = true`.
   - The gain then comes only with the spending, and the search hides an option whose spending a restriction refuses, such as the Grand Scepter.
   - One note after the batch covers the batch's generic lines: the Buried line and the gain line.
   - The decision is `passWhenEmpty`, as Fae Merchant's is, so a player holding only the Grand Scepter is asked nothing.
   - None of these gains takes from a bank the spending pays into, so the spec's "same-bank gains" point does not arise.
3. **Amounts in the lines.** The shared secret bank never runs out, so a secret gain always gives its full amount.
   - A discard or bury also returns the card's secrets to the player facedown, in the same step.
   - So a line's "gained {n secrets}" is the player's secret change less the secrets the card carried. Bog's favor amount is the player's favor change, since a relic carries no favor back.
   - If this is wrong, a line overstates a gain by the returned secrets.
4. **Holding only the Grand Scepter.** Arcane Brokers, Bog and Relic Breaker write no line then, since "held no relic" would be false. The log falls back to "Used Arcane Brokers". The `used.none` line is written only when the player holds no relic at all.
5. **Blood Pact's sacrifice** is `Sacrifice` from the player's board to the warband bank, in one batch with `Gain.Secrets`. It has no generic line (`DetailLines` reads only warband moves to a site). Choosing 0 is legal and writes the `used.none` line.
6. **Skeleton Key gains a peek line.** The spec says its take line "covers the Peeked lines". A note covers only the step before it, and the relic question separates the peek step from the take step (Barbed Net's doc). So Skeleton Key keeps Barbed Net's shape: a `used.peeked` line covers the peeks, and the `used` line tells the take. This adds one line to the spec's table. If it is wrong, the peek line is redundant and can be dropped without changing state.
7. **Bed of Roots' candidates** are the player's faceup denizen advisers. A Vision is not an adviser of that kind, and a facedown adviser is not faceup. They are offered as denizen options, since a faceup card discloses nothing.
8. **Messenger's sites** are the sites the player rules (`PowerAccess.ruledSites`), in map order. It writes its `used.none` line whenever nothing can move: no ruled site, or no warband beyond the one each site keeps.
9. **Free ACTION powers can be used again.** `PhasePowerProcedure` gives Act powers no once-each limit; a costed one is limited by the cost it leaves on its card. Bog, Relic Breaker and Tavern Songs are free, so a player may use them more than once in a turn. That is the engine's existing rule, and this slice does not change it.

## File Structure

All paths are under `src/main/scala/oathdigital/gameplay/powers/` or `src/test/scala/oathdigital/gameplay/powers/`.

| File | Responsibility | Task |
|---|---|---|
| `PowerAnswers.scala` (modify) | P5: `amount`, the reading of a `ChooseAmount` answer | 1 |
| `action/BloodPact.scala` (create) | Blood Pact | 1 |
| `action/SelfActionPowers.scala` (modify) | Registers every power of this slice, task by task | 1 to 7 |
| `action/HeldRelicSpend.scala` (create) | Choose a held relic, spend it and gain in one required batch | 2 |
| `action/ArcaneBrokers.scala`, `action/Bog.scala`, `action/RelicBreaker.scala` (create) | The three relic spenders | 2 |
| `action/BedOfRoots.scala` (create) | Bed of Roots | 3 |
| `action/TavernSongs.scala` (create) | Tavern Songs | 4 |
| `RelicDraws.scala` (modify) | `emptyDeck` and `drawSteps`, moved from Dowsing Sticks | 5 |
| `action/DowsingSticks.scala` (modify) | Uses `RelicDraws.drawSteps` | 5 |
| `action/TinkersFair.scala` (create) | Tinker's Fair | 5 |
| `action/SiteRelicTake.scala` (create) | Barbed Net's peek, choice and take, for a site the power reaches | 6 |
| `action/BarbedNet.scala` (modify) | Uses `SiteRelicTake` at the pawn site | 6 |
| `action/SkeletonKey.scala` (create) | Skeleton Key | 6 |
| `WarbandArrangement.scala` (create) | P6: the distribution over a board and a site set | 7 |
| `campaign/WarningSignals.scala` (modify) | Uses `WarbandArrangement` over the sites it defends | 7 |
| `action/Messenger.scala` (create) | Messenger | 7 |
| `action/<Card>Suite.scala` (create) | One suite per card | 1 to 7 |
| `docs/ROADMAP.md` (modify) | Records the slice | 8 |

Every new suite drives the power through `TargetsFixture` (`use`, `answer`, `after`, `awaits`, `offered`, `queryOf`, `replayed`, `usableNow`, `parked`), whose rules hold the printed operation restrictions, so the search hides the Grand Scepter. It stages with `Table` and reads with `Look`.

---

### Task 1: P5 and Blood Pact

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BloodPact.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BloodPactSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.player(ready, player): Either[OathViolation, PlayerState]`, `PlayerFacts.forceKind(ready, player): Either[OathViolation, ForceKind]`, `NoteSupport.warbands(step, player): Int`, `Sacrifice(player, Piece.Warbands, PositionedLocation)`, `Gain.Secrets(player, amount)`, `DecisionQuery.ChooseAmount(min, max, heading, confirmLabel)`.
- Produces: `PowerAnswers.amount(pending: PendingTree, decision: String): Option[Int]`; `BloodPact` (`decisionId`, `sacrificed`, `spared`); `SelfActionPowers.forCatalog` gains a trailing `Vector[PhasePower](...)` that later tasks extend.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BloodPactSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BloodPactSuite extends munit.FunSuite:
  import TargetsFixture._

  private val pact = CatalogNames.denizen("Blood Pact")
  private val source = DecisionOptionRef.Denizen(pact)

  /** p1's Act, holding Blood Pact as an adviser, with `warbands` on the
    * board and `secrets` faceup secrets. */
  private def staged(warbands: Int = 5, secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, pact)
      .warbands(p1, warbands).secrets(p1, secrets).ready

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def pairs(count: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(count)

  test("it places a secret and asks how many pairs, up to half the board"):
    val t = use(staged(), BloodPact, source).toOption.get
    assert(awaits(t, BloodPact.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(pact), Tokens(0, 1))
    assert(answer(t, p1, BloodPact.decisionId, pairs(3)).isLeft)

  test("each pair sacrificed from the board gains a secret"):
    val ready = staged()
    val t = use(ready, BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(2)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).warbands(p1), 1)
    assertEquals(secretsOf(end), secretsOf(ready) - 1 + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the warbands sacrificed and the secrets gained, " +
      "covering the gain line"):
    val t = use(staged(), BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(2)).toOption.get
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} sacrificed 4 warbands and gained 2 secrets.",
      covers = true)))

  test("choosing no pair sacrifices nothing and says so"):
    val t = use(staged(), BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(0)).toOption.get
    assertEquals(Look(after(done)).warbands(p1), 5)
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("with fewer than two warbands it asks nothing, and the cost stays " +
      "paid"):
    val done = use(staged(warbands = 1), BloodPact, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p1), 1)
    assertEquals(Look(after(done)).tokensOn(pact), Tokens(0, 1))
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("it is unusable without a faceup secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == BloodPact.id))
    assert(use(broke, BloodPact, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BloodPactSuite"`
Expected: compilation fails with "Not found: BloodPact".

- [ ] **Step 3: Add P5**

In `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`, replace:

```scala
  def distribution(pending: PendingTree, decision: String)
```

with:

```scala
  /** The amount a power's `ChooseAmount` was answered with. */
  def amount(pending: PendingTree, decision: String): Option[Int] =
    pending.answered.collectFirst:
      case Answered(`decision`, DecisionAnswer.ChooseAmountAnswer(count), _) =>
        count

  def distribution(pending: PendingTree, decision: String)
```

- [ ] **Step 4: Create Blood Pact**

Create `src/main/scala/oathdigital/gameplay/powers/action/BloodPact.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Blood Pact (card 62), ACTION: place 1 secret on this card, then sacrifice
  * an even number of warbands on your board, gaining 1 secret for every two.
  *
  * With 2 or more warbands on the board, the player chooses a number of pairs
  * from 0 to half their warbands, rounded down, in a `ChooseAmount`. The
  * sacrifice and the gain run in one batch, so its line covers the generic
  * gain line. With fewer than 2 warbands nothing is asked and nothing
  * happens, and choosing 0 sacrifices nothing.
  */
case object BloodPact extends PaidAction("denizen.blood-pact",
    Cost(secret = 1)):
  val decisionId: String = "power.blood-pact.pairs"
  /** "{Red} sacrificed {n warbands} and gained {m secrets}." */
  val sacrificed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" sacrificed "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} sacrificed no warbands." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" sacrificed no warbands.")))
  override def noteKeys: Vector[NoteKey] = Vector(sacrificed, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => pact(live, player, pending)),
    Note(id, pactNote(_, player, source), covers = true))))

  /** The most pairs the player's board can give. */
  private def pairs(ready: ReadyGame, player: PlayerId): Int =
    PlayerFacts.player(ready, player).toOption.fold(0)(_.board.warbands / 2)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val most = pairs(ready, player)
    if most == 0 then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseAmount(0, most,
      Some("Blood Pact: choose how many pairs of warbands to sacrifice. " +
        "Each pair gains a secret"), "Sacrifice")))

  /** No answer means the board held fewer than two warbands. */
  private def pact(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    if pairs(ready, player) == 0 then Right(Vector.empty)
    else for
      count <- PowerAnswers.amount(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      kind <- PlayerFacts.forceKind(ready, player)
    yield
      if count == 0 then Vector.empty
      else Vector[CoreOperation](Sacrifice(player,
        Piece.Warbands(kind, 2 * count),
        PositionedLocation(Location.PlayArea(player))),
        Gain.Secrets(player, count))

  /** The warbands the step before the note took from the board. */
  private def pactNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map { card =>
      val lost = states.previous.fold(0)(step =>
        -NoteSupport.warbands(step, player))
      if lost > 0 then sacrificed(card, NoteArg.Player(player),
        NoteArg.Amount(lost, NoteUnit.Warband),
        NoteArg.Amount(lost / 2, NoteUnit.Secret))
      else spared(card, NoteArg.Player(player))
    }
```

- [ ] **Step 5: Register it**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3 and catalog batch 3's slice
  * 3a, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object, like [[DiceAndRelicDrawPowers]]. A power that
  * needs the catalog is omitted when its card is absent.
  */
object SelfActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Tutor, ShiftingMap, DemonTail, WizardSchool,
      SpiritSnare, ClayRattle, Scryer, OracularPig) ++
      Oracle.forCatalog(catalog).toVector ++
      Vector[PhasePower](BloodPact)
```

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BloodPactSuite"`
Expected: 6 tests pass.

If the out-of-range answer in the first test is accepted, the walker does not check a `ChooseAmount` answer against its range for a power's decision. Stop and report it: that is an engine gap, not this power's to fix.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala src/main/scala/oathdigital/gameplay/powers/action/BloodPact.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/BloodPactSuite.scala
git commit -m "feat(powers): add Blood Pact"
```

---

### Task 2: Arcane Brokers, Bog and Relic Breaker

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/HeldRelicSpend.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/ArcaneBrokers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Bog.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/RelicBreaker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/ArcaneBrokersSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/RelicBreakerSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`, `PowerAnswers.missing`, `PlayerFacts.player`, `NoteSupport.relicsLost(step, player): Vector[RelicId]`, `NoteSupport.secrets(step, player)`, `NoteSupport.favor(step, player)`, `Discard.Relic(card, from, secrets, actingPlayer)`, `Bury.standard(card, from, suit, favor, secrets, actingPlayer)`, `Gain.Secrets`, `Gain.Favor(player, suit, amount)` (best effort), `Decide(..., passWhenEmpty = true)`.
- Produces: `final class HeldRelicSpend(decisionId: String, heading: String, spend: (PlayerId, RelicState) => Vector[CoreOperation])` with `steps(player): Vector[Operation]`, `spentRelic(states, player): Option[(RelicId, Tokens)]` and `emptyHanded(states, player): Boolean`; `ArcaneBrokers`, `Bog` and `RelicBreaker`, each with `decisionId`.

- [ ] **Step 1: Write the three failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/action/ArcaneBrokersSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class ArcaneBrokersSuite extends munit.FunSuite:
  import TargetsFixture._

  private val brokers = CatalogNames.denizen("Arcane Brokers")
  private val source = DecisionOptionRef.Denizen(brokers)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")
  private val sticks = RelicId("R09")

  /** p1's Act, holding Arcane Brokers as an adviser, `favor` favor and
    * `relics` faceup. */
  private def staged(relics: Vector[RelicId] = Vector(whistle, sticks),
      favor: Int = 1): Table =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).adviser(p1, brokers)
      .favor(p1, favor))(_.relic(p1, _))

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("it places a favor and asks which held relic to discard"):
    val t = use(staged().ready, ArcaneBrokers, source).toOption.get
    assert(awaits(t, ArcaneBrokers.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(brokers), Tokens(1, 0))
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> whistle.value, "relic" -> sticks.value)))

  test("a lone relic is still asked about"):
    val t = use(staged(Vector(whistle)).ready, ArcaneBrokers, source)
      .toOption.get
    assert(awaits(t, ArcaneBrokers.decisionId),
      parked.parkedDecision(t.state).toString)

  test("the chosen relic is set aside and 2 secrets are gained"):
    val ready = staged().ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(sticks))
    assert(end.game.current.setAsideRelics.contains(whistle))
    assertEquals(secretsOf(end), secretsOf(ready) + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("a facedown relic may be discarded"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, brokers)
      .favor(p1, 1).relic(p1, whistle, facedown = true).ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])

  test("the relic's secrets return facedown, and the line counts only the " +
      "2 gained, covering the gain line"):
    val ready = staged(Vector(whistle)).tokens(whistle, secrets = 1).ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} discarded ${whistle.value} and gained 2 secrets.",
        covers = true)))

  test("the Grand Scepter cannot be chosen"):
    val t = use(staged(Vector(scepter, whistle)).ready, ArcaneBrokers, source)
      .toOption.get
    assert(answer(t, p1, ArcaneBrokers.decisionId, relicRef(scepter)).isLeft)

  test("holding only the Grand Scepter, it asks nothing, discards nothing " +
      "and writes nothing"):
    val ready = staged(Vector(scepter)).ready
    val done = use(ready, ArcaneBrokers, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(secretsOf(after(done)), secretsOf(ready))
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector.empty)

  test("holding no relic, the favor stays paid and the line says so"):
    val done = use(staged(Vector.empty).ready, ArcaneBrokers, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(brokers), Tokens(1, 0))
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == ArcaneBrokers.id))
    assert(use(broke, ArcaneBrokers, source).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/powers/action/BogSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BogSuite extends munit.FunSuite:
  import TargetsFixture._

  private val bog = CatalogNames.denizen("Bog")
  private val source = DecisionOptionRef.Denizen(bog)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")

  /** p1's Act beside a site Bog, holding `relics` faceup, with `beast` favor
    * in the Beast bank. Bog is site-only; it stands at p1's pawn site. */
  private def staged(relics: Vector[RelicId] = Vector(whistle),
      beast: Int = 5): ReadyGame =
    relics.foldLeft(Table.start.turn(p1, Phase.Act)
      .denizen(bog, at = Table.homeOf(p1)).bankFavor(Suit.Beast, beast))(
      _.relic(p1, _)).ready

  private def beastBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Beast, 0)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))
  private def used(ready: ReadyGame): OathTransition =
    val t = use(ready, Bog, source).toOption.get
    answer(t, p1, Bog.decisionId, relicRef(whistle)).toOption.get

  test("it costs nothing and asks which held relic to discard"):
    val t = use(staged(), Bog, source).toOption.get
    assert(awaits(t, Bog.decisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("relic" -> whistle.value)))
    assertEquals(Look(after(t)).tokensOn(bog), Tokens.empty)

  test("the relic is discarded and 3 favor come from the Beast bank"):
    val ready = staged()
    val t = use(ready, Bog, source).toOption.get
    val done = answer(t, p1, Bog.decisionId, relicRef(whistle)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 3)
    assertEquals(beastBank(end), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the relic and the favor, covering the gain line"):
    assertEquals(NoteText.said(Bog, used(staged()).events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} discarded ${whistle.value} " +
        "and gained 3 favor from the Beast bank.", covers = true)))

  test("a Beast bank holding one favor gives one"):
    val done = used(staged(beast = 1))
    assertEquals(beastBank(after(done)), 0)
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} discarded ${whistle.value} " +
        "and gained 1 favor from the Beast bank.", covers = true)))

  test("an empty Beast bank: the relic is still discarded, and the line " +
      "names only the relic"):
    val ready = staged(beast = 0)
    val done = used(ready)
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(after(done)).favor(p1), Look(ready).favor(p1))
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said("used.discarded",
        s"${p1.value} discarded ${whistle.value}.", covers = true)))

  test("holding only the Grand Scepter, it asks nothing and writes nothing"):
    val ready = staged(Vector(scepter))
    val done = use(ready, Bog, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(beastBank(after(done)), 5)
    assertEquals(NoteText.said(Bog, done.events), Vector.empty)

  test("holding no relic, nothing happens and the line says so"):
    val done = use(staged(Vector.empty), Bog, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))
```

Create `src/test/scala/oathdigital/gameplay/powers/action/RelicBreakerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class RelicBreakerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val breaker = CatalogNames.denizen("Relic Breaker")
  private val source = DecisionOptionRef.Denizen(breaker)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")
  private val sticks = RelicId("R09")

  /** p1's Act, holding Relic Breaker as an adviser and `relics` faceup. */
  private def staged(relics: Vector[RelicId] = Vector(whistle, sticks))
      : Table =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).adviser(p1, breaker))(
      _.relic(p1, _))

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("it costs nothing and asks which held relic to bury"):
    val t = use(staged().ready, RelicBreaker, source).toOption.get
    assert(awaits(t, RelicBreaker.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> whistle.value, "relic" -> sticks.value)))

  test("the relic goes to the bottom of the relic deck and 1 secret is " +
      "gained"):
    val ready = staged().ready
    val t = use(ready, RelicBreaker, source).toOption.get
    val done = answer(t, p1, RelicBreaker.decisionId, relicRef(whistle))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(sticks))
    assertEquals(end.game.current.commonCards.relicDeck.last, whistle)
    assertEquals(secretsOf(end), secretsOf(ready) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the relic's secrets return facedown, and the line counts only the " +
      "secret gained, covering the Buried and gain lines"):
    val ready = staged(Vector(whistle)).tokens(whistle, secrets = 1).ready
    val t = use(ready, RelicBreaker, source).toOption.get
    val done = answer(t, p1, RelicBreaker.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} buried ${whistle.value} and gained 1 secret.",
        covers = true)))

  test("the Grand Scepter cannot be chosen"):
    val t = use(staged(Vector(scepter, whistle)).ready, RelicBreaker, source)
      .toOption.get
    assert(answer(t, p1, RelicBreaker.decisionId, relicRef(scepter)).isLeft)

  test("holding only the Grand Scepter, it asks nothing and writes nothing"):
    val ready = staged(Vector(scepter)).ready
    val done = use(ready, RelicBreaker, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector.empty)

  test("holding no relic, nothing happens and the line says so"):
    val done = use(staged(Vector.empty).ready, RelicBreaker, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArcaneBrokersSuite oathdigital.gameplay.powers.action.BogSuite oathdigital.gameplay.powers.action.RelicBreakerSuite"`
Expected: compilation fails with "Not found: ArcaneBrokers", "Not found: Bog" and "Not found: RelicBreaker".

- [ ] **Step 3: Create the shared choice**

Create `src/main/scala/oathdigital/gameplay/powers/action/HeldRelicSpend.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** A relic the player holds, faceup or facedown, chosen and then spent:
  * Arcane Brokers and Bog discard it, Relic Breaker buries it (catalog batch
  * 3 rulings, slice 3a).
  *
  * Two steps. A live `Branch` asks whenever the player holds a relic, even
  * one. A required batch then spends the chosen relic and gains the reward
  * together, so the reward comes only with the spending ("X to gain Y"). The
  * Grand Scepter's restriction refuses its discard and its bury, and the
  * batch is required, so the search hides the scepter. The decision is passed
  * when the search leaves it empty (`Decide.passWhenEmpty`), so a player
  * holding only the scepter spends and gains nothing.
  *
  * @param spend the spending and the reward for the chosen relic, as it
  *   stands in the player's play area.
  */
final class HeldRelicSpend(decisionId: String, heading: String,
    spend: (PlayerId, RelicState) => Vector[CoreOperation]):
  def steps(player: PlayerId): Vector[Operation] = Vector(
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => spent(live, player, pending), required = true))

  /** The relic the step before the note took from the player, with the
    * tokens it carried then. Nothing when no relic was spent. */
  def spentRelic(states: NoteStates, player: PlayerId)
      : Option[(RelicId, Tokens)] = for
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
    before <- held(step._1, player).find(_.id == relic)
  yield (relic, before.tokens)

  /** Whether the player holds no relic at all now. */
  def emptyHanded(states: NoteStates, player: PlayerId): Boolean =
    held(states.now, player).isEmpty

  private def held(ready: ReadyGame, player: PlayerId): Vector[RelicState] =
    PlayerFacts.player(ready, player).toOption.toVector.flatMap(_.relics)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    held(ready, player) match
      case Vector() => Vector.empty
      case relics => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
        relics.map(relic => DecisionOption.Relic(
          DecisionOptionRef.Relic(relic.id))), heading = Some(heading)),
        passWhenEmpty = true))

  /** No answer means the search left no relic to offer. */
  private def spent(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => held(ready, player)
        .find(relic => DecisionOptionRef.Relic(relic.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a relic the player holds"))
        .map(spend(player, _))
```

- [ ] **Step 4: Create the three powers**

Create `src/main/scala/oathdigital/gameplay/powers/action/ArcaneBrokers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Arcane Brokers (card 204), ACTION: place 1 favor on this card, then
  * discard a relic you hold, faceup or facedown, to gain 2 secrets.
  *
  * The relic is chosen and discarded as [[HeldRelicSpend]] describes. A
  * discarded relic is set aside until Chronicle, and its secrets return to
  * the player facedown. Its line names the relic and the secrets gained, not
  * those returned, and covers the generic gain line; a relic discard has no
  * generic line. With no relic held the line says so. Holding only the Grand
  * Scepter, it writes nothing.
  */
case object ArcaneBrokers extends PaidAction("denizen.arcane-brokers",
    Cost(favor = 1)):
  val decisionId: String = "power.arcane-brokers.relic"
  val Gained: Int = 2
  /** "{Red} discarded {relic} and gained {n secrets}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] = Vector(discarded, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Arcane Brokers: discard a relic to gain 2 secrets", (player, held) =>
      Vector[CoreOperation](Discard.Relic(held.id,
        PositionedLocation(Location.PlayArea(player)), held.tokens.secrets,
        player), Gain.Secrets(player, Gained)))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, discardNote(_, player, source),
      covers = true)))

  private def discardNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, tokens)) => states.previous.map(step =>
          discarded(card, NoteArg.Player(player), NoteArg.Card(spent),
            NoteArg.Amount(NoteSupport.secrets(step, player) - tokens.secrets,
              NoteUnit.Secret)))
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
```

Create `src/main/scala/oathdigital/gameplay/powers/action/Bog.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Bog (card 210, site-only), ACTION: discard a relic you hold, faceup or
  * facedown, to gain 3 favor from the Beast bank.
  *
  * Free. The relic is chosen and discarded as for Arcane Brokers
  * ([[HeldRelicSpend]]). The gain is best effort: it takes what the Beast
  * bank holds. Its line names the relic and the favor gained, and covers the
  * generic gain line. With the bank empty it names only the relic. With no
  * relic held the line says so. Holding only the Grand Scepter, it writes
  * nothing.
  */
case object Bog extends PaidAction("denizen.bog", Cost.free):
  val decisionId: String = "power.bog.relic"
  val Gained: Int = 3
  /** "{Red} discarded {relic} and gained {n favor} from {the Beast bank}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(" from "),
    NotePart.Arg(3), NotePart.Text(".")))
  /** "{Red} discarded {relic}." */
  val discardedOnly: NoteKey = NoteKey("used.discarded", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] =
    Vector(discarded, discardedOnly, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Bog: discard a relic to gain 3 favor from the Beast bank",
    (player, held) => Vector[CoreOperation](Discard.Relic(held.id,
      PositionedLocation(Location.PlayArea(player)), held.tokens.secrets,
      player), Gain.Favor(player, Suit.Beast, Gained)))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, discardNote(_, player, source),
      covers = true)))

  private def discardNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, _)) => states.previous.map { step =>
          val favor = NoteSupport.favor(step, player)
          if favor > 0 then discarded(card, NoteArg.Player(player),
            NoteArg.Card(spent), NoteArg.Amount(favor, NoteUnit.Favor),
            NoteArg.Bank(Suit.Beast))
          else discardedOnly(card, NoteArg.Player(player), NoteArg.Card(spent))
        }
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
```

Create `src/main/scala/oathdigital/gameplay/powers/action/RelicBreaker.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Relic Breaker (card 139), ACTION: bury a relic you hold, faceup or
  * facedown, to gain 1 secret.
  *
  * Free. The relic is chosen as for Arcane Brokers ([[HeldRelicSpend]]) and
  * buried at the bottom of the relic deck with the standard returns: its
  * secrets go back to the player facedown. Its line names the relic and the
  * secret gained, not those returned, and covers the generic Buried and gain
  * lines. With no relic held the line says so. Holding only the Grand
  * Scepter, it writes nothing.
  */
case object RelicBreaker extends PaidAction("denizen.relic-breaker",
    Cost.free):
  val decisionId: String = "power.relic-breaker.relic"
  val Gained: Int = 1
  /** "{Red} buried {relic} and gained {n secrets}." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] = Vector(buried, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Relic Breaker: bury a relic to gain a secret", (player, held) =>
      Bury.standard(BuryableCard.Relic(held.id),
        PositionedLocation(Location.PlayArea(player)), None, 0,
        held.tokens.secrets, player) :+ Gain.Secrets(player, Gained))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, buryNote(_, player, source),
      covers = true)))

  private def buryNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, tokens)) => states.previous.map(step =>
          buried(card, NoteArg.Player(player), NoteArg.Card(spent),
            NoteArg.Amount(NoteSupport.secrets(step, player) - tokens.secrets,
              NoteUnit.Secret)))
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
```

- [ ] **Step 5: Register them**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`, replace:

```scala
      Vector[PhasePower](BloodPact)
```

with:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker)
```

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArcaneBrokersSuite oathdigital.gameplay.powers.action.BogSuite oathdigital.gameplay.powers.action.RelicBreakerSuite oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.gameplay.powers.action.MagicCarpetSuite"`
Expected: 9, 7 and 6 tests pass, and Fae Merchant's and Magic Carpet's suites still pass.

If the "secrets return facedown" tests fail because the discard moves the relic before its secrets leave it, compare with `MagicCarpet`, which discards a relic with secrets on it the same way. Do not change `Discard.Relic`; report the failure.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/HeldRelicSpend.scala src/main/scala/oathdigital/gameplay/powers/action/ArcaneBrokers.scala src/main/scala/oathdigital/gameplay/powers/action/Bog.scala src/main/scala/oathdigital/gameplay/powers/action/RelicBreaker.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/ArcaneBrokersSuite.scala src/test/scala/oathdigital/gameplay/powers/action/BogSuite.scala src/test/scala/oathdigital/gameplay/powers/action/RelicBreakerSuite.scala
git commit -m "feat(powers): add Arcane Brokers, Bog and Relic Breaker"
```

---

### Task 3: Bed of Roots

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BedOfRoots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BedOfRootsSuite.scala`

**Interfaces:**
- Consumes: `CatalogCards.denizen(catalog, power): Option[DenizenId]`, `catalog.suitOf(card): Option[Suit]`, `PlayerFacts.player`, `PowerAnswers.one`, `NoteSupport.secrets`, `Bury.standard`, `Gain.Secrets`, `SearchFixture.denizensOf(suit): Vector[DenizenId]` (test).
- Produces: `BedOfRoots.forCatalog(catalog): Option[BedOfRoots]`, `BedOfRoots.id`, `BedOfRoots.decisionId`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BedOfRootsSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BedOfRootsSuite extends munit.FunSuite:
  import TargetsFixture._

  private val bed = CatalogNames.denizen("Bed of Roots")
  private val source = DecisionOptionRef.Denizen(bed)
  private val power = BedOfRoots.forCatalog(catalog).get
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val vision = VisionId("vision:vision-of-faith")

  /** p1's Act beside a site Bed of Roots, with `favor` favor. Bed of Roots
    * is site-only; it stands at p1's pawn site. */
  private def staged(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).denizen(bed, at = Table.homeOf(p1))
      .favor(p1, favor)

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def denizenRef(id: DenizenId): DecisionAnswer =
    pick(DecisionOptionRef.Denizen(id))

  test("it burns 3 favor and offers only the faceup denizen advisers"):
    val ready = staged().adviser(p1, plain(0))
      .adviser(p1, plain(1), facedown = true).adviser(p1, vision).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, BedOfRoots.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).favor(p1), 0)
    assertEquals(offered(t, p1).map(_.map(_._2)), Some(Vector(plain(0).value)))

  test("the adviser is buried with its returns, and 2 secrets are gained"):
    val ready = staged().adviser(p1, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1).ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(plain(0)))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p1), Vector.empty[CardId])
    assertEquals(end.game.current.commonCards.worldDeck.last, plain(0))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(secretsOf(end), secretsOf(ready) + 3)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the adviser and the secrets gained, covering the " +
      "Buried and gain lines"):
    val ready = staged().adviser(p1, plain(0))
      .tokens(plain(0), secrets = 1).ready
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(plain(0)))
      .toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} buried ${plain(0).value} and gained 2 secrets.",
      covers = true)))

  test("a locked adviser may be buried"):
    val ready = staged().adviser(p1, insomnia).ready
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(insomnia))
      .toOption.get
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("with no faceup adviser, the cost stays paid and the line says so"):
    val ready = staged().adviser(p1, plain(1), facedown = true).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(Look(after(done)).advisers(p1), Vector[CardId](plain(1)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} had no faceup adviser.", covers = true)))

  test("it is unusable with less than 3 favor"):
    val broke = staged(favor = 2).adviser(p1, plain(0)).ready
    assert(!usableNow(broke).exists(_.power.id == BedOfRoots.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BedOfRootsSuite"`
Expected: compilation fails with "Not found: BedOfRoots".

- [ ] **Step 3: Create Bed of Roots**

Create `src/main/scala/oathdigital/gameplay/powers/action/BedOfRoots.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

/** Bed of Roots (card 212, site-only), ACTION: burn 3 favor, then bury a
  * faceup adviser you have, even if locked, to gain 2 secrets.
  *
  * The candidates are the player's faceup denizen advisers, in adviser
  * order. A Vision is not an adviser of that kind, and a facedown adviser is
  * not faceup. `Bury` ignores locked. The question is asked whenever there
  * is a candidate, even one. The bury and the gain run as one required
  * batch, so the gain comes only with the bury. The bury uses the standard
  * returns: favor to the card's suit bank, secrets to the player facedown.
  * With no candidate the cost stays paid and nothing else happens.
  *
  * Its line names the card and the secrets gained, not those returned, and
  * covers the generic Buried and gain lines.
  */
final case class BedOfRoots private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.bed-of-roots", Cost(favorBurnt = 3)):
  import BedOfRoots._

  override def noteKeys: Vector[NoteKey] = Vector(buried, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending), required = true),
    Note(this.id, buriedNote(_, player, source), covers = true))))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    candidates(ready, player) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
        found.map(card => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(card.id))),
        heading = Some("Bed of Roots: bury one of your faceup advisers to " +
          "gain 2 secrets")), passWhenEmpty = true))

  /** No answer means the player had no faceup adviser to offer. */
  private def bury(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        chosen <- candidates(ready, player)
          .find(card => DecisionOptionRef.Denizen(card.id) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a faceup adviser Bed of Roots can bury"))
        suit = catalog.suitOf(chosen.id)
        _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
          OathViolation.InvalidEventOrder(
            s"no suit is known for ${chosen.id.value}"))
      yield Bury.standard(BuryableCard.Denizen(chosen.id),
        PositionedLocation(Location.PlayArea(player)), suit,
        chosen.tokens.favor, chosen.tokens.secrets, player) :+
        Gain.Secrets(player, Gained)

  /** The adviser the step before the note took from the player, read where
    * it stood before. No adviser taken means there was none to bury. */
  private def buriedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      step <- states.previous
      chosen <- candidates(step._1, player).find(before =>
        !candidates(step._2, player).exists(_.id == before.id))
    yield buried(card, NoteArg.Player(player), NoteArg.Card(chosen.id),
      NoteArg.Amount(NoteSupport.secrets(step, player) -
        chosen.tokens.secrets, NoteUnit.Secret)))
      .getOrElse(bare(card, NoteArg.Player(player))))

object BedOfRoots:
  val id: PowerId = PowerId("denizen.bed-of-roots")
  val decisionId: String = "power.bed-of-roots.adviser"
  val Gained: Int = 2
  /** "{Red} buried {card} and gained {n secrets}." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} had no faceup adviser." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no faceup adviser.")))

  /** The player's faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame, player: PlayerId)
      : Vector[DenizenState] =
    PlayerFacts.player(ready, player).toOption.toVector.flatMap(_.advisers
      .collect { case card @ DenizenState(_, Orientation.FaceUp, _) => card })

  def forCatalog(catalog: ExecutableCatalog): Option[BedOfRoots] =
    CatalogCards.denizen(catalog, id).map(_ => new BedOfRoots(catalog))
```

The class refers to its own power id as `this.id`, as `Oracle` does, because `import BedOfRoots._` brings the companion's `id` into scope too.

- [ ] **Step 4: Register it**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3 and catalog batch 3's slice
  * 3a, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object, like [[DiceAndRelicDrawPowers]]. A power that
  * needs the catalog is omitted when its card is absent.
  */
object SelfActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Tutor, ShiftingMap, DemonTail, WizardSchool,
      SpiritSnare, ClayRattle, Scryer, OracularPig) ++
      Oracle.forCatalog(catalog).toVector ++
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker) ++
      BedOfRoots.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BedOfRootsSuite"`
Expected: 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/BedOfRoots.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/BedOfRootsSuite.scala
git commit -m "feat(powers): add Bed of Roots"
```

---

### Task 4: Tavern Songs

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/TavernSongs.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/TavernSongsSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite(ready, player): Option[SiteId]`, `MapState.regionOf(site): Option[Region]`, `CommonCards.discard(region): Vector[WorldCardId]` (top last), `Peek(player, card, location)`, `DecisionQuery.Inspect(cards, heading)`, `SearchSource.name`, `NoteArg.Pile`.
- Produces: `TavernSongs` (`inspectDecisionId`, `Depth`, `peeked`, `peekedEmpty`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/TavernSongsSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

class TavernSongsSuite extends munit.FunSuite:
  import TargetsFixture._

  private val songs = CatalogNames.denizen("Tavern Songs")
  private val source = DecisionOptionRef.Denizen(songs)
  private val home = Table.homeOf(p1)
  private val region = Table.start.ready.game.current.map.regionOf(home).get
  private val pileName = SearchSource.name(SearchSource.RegionalDiscard(region))
  /** Five cards for the pile, the last on top. */
  private val pile = SearchFixture.denizensOf(Suit.Arcane).take(5)

  /** p1's Act beside a site Tavern Songs, with `cards` in the discard pile
    * of p1's region, the last named on top. */
  private def staged(cards: Vector[DenizenId]): ReadyGame =
    Table.start.turn(p1, Phase.Act).denizen(songs, at = home)
      .discarded(region, cards*).ready

  /** Whether `player` has peeked `card`, as `ScryerSuite` reads it. */
  private def knows(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    ready.knowledge.advisers.getOrElse(player, Vector.empty).contains(card)

  test("it peeks at the top three cards of the pawn region's pile and " +
      "shows them top first until Done"):
    val ready = staged(pile)
    val top = pile.reverse.take(3)
    val t = use(ready, TavernSongs, source).toOption.get
    assert(awaits(t, TavernSongs.inspectDecisionId),
      parked.parkedDecision(t.state).toString)
    queryOf(t, p1) match
      case Some(DecisionQueryProjection.Inspect(cards, _, heading)) =>
        assertEquals(cards.map(_.cardId), top.map(_.value))
        assertEquals(heading, Some(s"Tavern Songs: the top of the $pileName"))
      case other => fail(s"expected an Inspect, got $other")
    val seen = after(t)
    assert(top.forall(knows(seen, p1, _)))
    assert(!pile.take(2).exists(knows(seen, p1, _)))
    others(seen).foreach(other => assert(!top.exists(knows(seen, other, _))))
    val done = answer(t, p1, TavernSongs.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(after(done).game.current.commonCards.discard(region),
      ready.game.current.commonCards.discard(region))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the cards top first and covers the peeks"):
    val t = use(staged(pile), TavernSongs, source).toOption.get
    assertEquals(NoteText.said(TavernSongs, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} peeked at the top of the $pileName: " +
        s"${pile.reverse.take(3).map(_.value).mkString(", ")}.",
      covers = true)))

  test("a pile of two shows both"):
    val t = use(staged(pile.take(2)), TavernSongs, source).toOption.get
    queryOf(t, p1) match
      case Some(DecisionQueryProjection.Inspect(cards, _, _)) =>
        assertEquals(cards.map(_.cardId), pile.take(2).reverse.map(_.value))
      case other => fail(s"expected an Inspect, got $other")

  test("an empty pile asks nothing and says so"):
    val done = use(staged(Vector.empty), TavernSongs, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(TavernSongs, done.events), Vector(
      NoteText.Said("used.empty",
        s"${p1.value} peeked at the $pileName, which was empty.",
        covers = true)))
```

`Table.start` puts every seeded regional discard back, so the region's pile holds only what `staged` adds.

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TavernSongsSuite"`
Expected: compilation fails with "Not found: TavernSongs".

- [ ] **Step 3: Create Tavern Songs**

Create `src/main/scala/oathdigital/gameplay/powers/action/TavernSongs.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Tavern Songs (card 54), ACTION: peek at the top three cards of your
  * region's discard pile.
  *
  * Free. The pile is the discard pile of the region of the player's pawn
  * site. Its top three cards, or fewer, are recorded as `Peek`s, so the
  * player may identify them, then an `Inspect` decision shows them top
  * first, as Scryer's does. A pile is stored top last, so the order is
  * reversed. An empty pile shows nothing and asks nothing.
  *
  * Its line comes between the peeks and the `Inspect`, so it posts when the
  * player looks, and covers the peek lines. Its empty variant is built by
  * the same covering `Note`; it has no peek line to cover.
  */
case object TavernSongs extends PaidAction("denizen.tavern-songs",
    Cost.free):
  val inspectDecisionId: String = "power.tavern-songs.inspect"
  /** How many cards it peeks at. */
  val Depth: Int = 3
  /** "{Red} peeked at the top of the {Cradle discard pile}: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the top of the "), NotePart.Arg(1),
    NotePart.Text(": "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} peeked at the {Cradle discard pile}, which was empty." */
  val peekedEmpty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1),
    NotePart.Text(", which was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked, peekedEmpty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(region(live, player).toVector.flatMap(at =>
      top(live, at).map(card =>
        Peek(player, card, Location.RegionalDiscard(at)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      at <- region(states.now, player)
    yield
      val pile = NoteArg.Pile(SearchSource.RegionalDiscard(at))
      val cards = top(states.now, at)
      if cards.isEmpty then peekedEmpty(card, NoteArg.Player(player), pile)
      else peeked(card, NoteArg.Player(player), pile, NoteArg.Cards(cards)),
      covers = true),
    Branch((live, _) => region(live, player).toVector.flatMap { at =>
      val cards = top(live, at)
      Option.when(cards.nonEmpty)(Decide(inspectDecisionId, player,
        DecisionQuery.Inspect(cards, heading = Some("Tavern Songs: the top " +
          s"of the ${SearchSource.name(SearchSource.RegionalDiscard(at))}"))))
        .toVector
    }))))

  /** The region of the player's pawn site. */
  private def region(ready: ReadyGame, player: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, player).flatMap(ready.game.current.map.regionOf)

  /** The pile's top cards, top first. */
  private def top(ready: ReadyGame, at: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(at).reverse.take(Depth)
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`, replace:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker) ++
```

with:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs) ++
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TavernSongsSuite"`
Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/TavernSongs.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/TavernSongsSuite.scala
git commit -m "feat(powers): add Tavern Songs"
```

---

### Task 5: Tinker's Fair

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/TinkersFair.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/TinkersFairSuite.scala`

**Interfaces:**
- Consumes: `RelicDraws.takeTop(ready, actor)`, `RelicDraws.drawNote(source, player)(states)`, `RelicDraws.drew`.
- Produces: `RelicDraws.emptyDeck: NoteKey`, `RelicDraws.drawSteps(power: PowerId, player: PlayerId, source: DecisionOptionRef): Vector[Operation]`; `DowsingSticks.emptyDeck` keeps its value; `TinkersFair`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/TinkersFairSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TinkersFairSuite extends munit.FunSuite:
  import TargetsFixture._

  private val fair = CatalogNames.denizen("Tinker's Fair")
  private val source = DecisionOptionRef.Denizen(fair)
  private val top = RelicId("R08")

  /** p1's Act beside a site Tinker's Fair, with `favor` favor and `top` on
    * the relic deck. Tinker's Fair is site-only; it stands at p1's pawn
    * site. */
  private def staged(favor: Int = 3): ReadyGame =
    Table.start.turn(p1, Phase.Act).denizen(fair, at = Table.homeOf(p1))
      .favor(p1, favor).relicDeckTop(top).ready

  /** `ready` with the relic deck moved to the reliquary. */
  private def emptied(ready: ReadyGame): ReadyGame =
    val current = ready.game.current
    ready.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))

  test("it places 3 favor and takes the top relic facedown"):
    val ready = staged()
    val done = use(ready, TinkersFair, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(top))
    assertEquals(Look(end).player(p1).relics.head.orientation,
      Orientation.FaceDown)
    assertEquals(Look(end).tokensOn(fair), Tokens(3, 0))
    assertEquals(replayed(ready, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))

  test("its line names the relic drawn"):
    val done = use(staged(), TinkersFair, source).toOption.get
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} drew ${top.value} facedown.",
        covers = false)))

  test("an empty relic deck: the cost stays paid and the line says so"):
    val done = use(emptied(staged()), TinkersFair, source).toOption.get
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(after(done)).tokensOn(fair), Tokens(3, 0))
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said("used.empty", "The relic deck was empty.",
        covers = false)))

  test("it is unusable with less than 3 favor"):
    val broke = staged(favor = 2)
    assert(!usableNow(broke).exists(_.power.id == TinkersFair.id))
    assert(use(broke, TinkersFair, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TinkersFairSuite"`
Expected: compilation fails with "Not found: TinkersFair".

- [ ] **Step 3: Move Dowsing Sticks' draw into `RelicDraws`**

In `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`, replace:

```scala
  /** "{player} drew {relic} facedown.": a relic draw's own line. */
```

with:

```scala
  /** "The relic deck was empty.": a relic draw's line when it drew none. */
  val emptyDeck: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("The relic deck was empty.")))

  /** A power's draw of the top relic, taken facedown, then its line: the
    * relic drawn, or the empty deck. Dowsing Sticks and Tinker's Fair.
    *
    * The draw sits in a `BuildOps` because whether the deck has a top card
    * is a fact of the state when the draw runs, not when the tree was built.
    */
  def drawSteps(power: PowerId, player: PlayerId, source: DecisionOptionRef)
      : Vector[Operation] = Vector(
    BuildOps((state, _) => Right(takeTop(state, player))),
    Note(power, states => drawNote(source, player)(states)
      .orElse(PowerSourceRef.of(source).map(emptyDeck(_)))))

  /** "{player} drew {relic} facedown.": a relic draw's own line. */
```

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Dowsing Sticks (relic R09), ACTION: place 1 secret on this relic and burn
  * 2 secrets, then draw a relic and take it facedown. An empty relic deck
  * pays the cost and does nothing else. The draw and its line are
  * `RelicDraws.drawSteps`, shared with Tinker's Fair.
  */
case object DowsingSticks extends PaidAction("relic.dowsing-sticks",
    Cost(secret = 1, secretBurnt = 2)):
  /** Its line when the deck had no relic to draw. */
  val emptyDeck: NoteKey = RelicDraws.emptyDeck
  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(RelicDraws.drawSteps(id, player, source)))
```

- [ ] **Step 4: Create Tinker's Fair**

Create `src/main/scala/oathdigital/gameplay/powers/action/TinkersFair.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Tinker's Fair (card 13, site-only), ACTION: place 3 favor on this card,
  * then draw a relic and take it facedown, as Dowsing Sticks does. An empty
  * relic deck pays the cost and does nothing else.
  */
case object TinkersFair extends PaidAction("denizen.tinker-s-fair",
    Cost(favor = 3)):
  override def noteKeys: Vector[NoteKey] =
    Vector(RelicDraws.drew, RelicDraws.emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(RelicDraws.drawSteps(id, player, source)))
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`, replace:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs) ++
```

with:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs, TinkersFair) ++
```

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TinkersFairSuite oathdigital.gameplay.powers.action.DowsingSticksSuite oathdigital.gameplay.powers.action.FaeMerchantSuite"`
Expected: Tinker's Fair's 4 tests pass, and Dowsing Sticks' and Fae Merchant's suites pass unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala src/main/scala/oathdigital/gameplay/powers/action/TinkersFair.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/TinkersFairSuite.scala
git commit -m "feat(powers): add Tinker's Fair"
```

---

### Task 6: Skeleton Key

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SiteRelicTake.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SkeletonKey.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SkeletonKeySuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`, `NoteSupport.relicsGained(step, player)`, `PowerAccess.pawnSite`, `MapState.regionOf`.
- Produces: `final class SiteRelicTake(power: PowerId, decisionId: String, heading: String, reach: (ReadyGame, PlayerId) => Option[SiteId])` with `peeked`, `took`, `bare`, `keys` and `steps(player, source): Vector[Operation]`; `BarbedNet.peeked`, `BarbedNet.took`, `BarbedNet.bare` and `BarbedNet.decisionId` keep their values; `SkeletonKey` (`decisionId`, `away`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/SkeletonKeySuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class SkeletonKeySuite extends munit.FunSuite:
  import TargetsFixture._

  private val key = CatalogNames.relic("Skeleton Key")
  private val source = DecisionOptionRef.Relic(key)
  private val wild = Table.start.ready.game.current.map.hinterland.head
  private val home = Table.homeOf(p1)
  private val (first, second) = (RelicId("R08"), RelicId("R09"))

  /** p1's Act holding Skeleton Key faceup with `secrets` faceup secrets,
    * the pawn at `at`, and `relics` facedown there. */
  private def staged(at: SiteId = wild,
      relics: Vector[RelicId] = Vector(first, second),
      secrets: Int = 2): ReadyGame =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).relic(p1, key)
      .secrets(p1, secrets).pawn(p1, at))(_.relicAt(_, at)).ready

  private def knows(ready: ReadyGame, relic: RelicId): Boolean =
    ready.knowledge.siteRelics.getOrElse(p1, Map.empty)
      .valuesIterator.exists(_.contains(relic))
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("at a Hinterland site it peeks at every relic there and asks which " +
      "to take"):
    val t = use(staged(), SkeletonKey, source).toOption.get
    assert(awaits(t, SkeletonKey.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).faceUpSecrets(p1), 0)
    assertEquals(Look(after(t)).tokensOn(key), Tokens(0, 1))
    assert(knows(after(t), first) && knows(after(t), second))
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> first.value, "relic" -> second.value)))

  test("the chosen relic moves facedown to the player, and the other stays"):
    val ready = staged()
    val t = use(ready, SkeletonKey, source).toOption.get
    val done = answer(t, p1, SkeletonKey.decisionId, relicRef(first))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assert(Look(end).player(p1).relics.exists(relic =>
      relic.id == first && relic.orientation == Orientation.FaceDown))
    assertEquals(Look(end).relicsAt(wild), Vector(second))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("it writes its peek, covering the peek lines, then its take"):
    val t = use(staged(), SkeletonKey, source).toOption.get
    val done = answer(t, p1, SkeletonKey.decisionId, relicRef(first))
      .toOption.get
    assertEquals(NoteText.said(SkeletonKey, t.events ++ done.events), Vector(
      NoteText.Said("used.peeked", s"${p1.value} peeked at the relics at " +
        s"${wild.value}: ${first.value}, ${second.value}.", covers = true),
      NoteText.Said(NoteKey.Used,
        s"${p1.value} took ${first.value} facedown from ${wild.value}.",
        covers = false)))

  test("away from the Hinterland the cost is paid and nothing else happens"):
    assert(!Table.start.ready.game.current.map.hinterland.contains(home))
    val ready = staged(at = home)
    val done = use(ready, SkeletonKey, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(key), Tokens(0, 1))
    assertEquals(Look(after(done)).relicsAt(home), Vector(first, second))
    assertEquals(NoteText.said(SkeletonKey, done.events), Vector(
      NoteText.Said("used.away", s"${p1.value} was not at a Hinterland site.",
        covers = false)))

  test("a Hinterland site with no relic: the line says so"):
    val done = use(staged(relics = Vector.empty), SkeletonKey, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(SkeletonKey, done.events), Vector(
      NoteText.Said("used.none", s"${wild.value} held no relic.",
        covers = false)))

  test("one secret is not enough"):
    val broke = staged(secrets = 1)
    assert(!usableNow(broke).exists(_.power.id == SkeletonKey.id))
    assert(use(broke, SkeletonKey, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SkeletonKeySuite"`
Expected: compilation fails with "Not found: SkeletonKey".

- [ ] **Step 3: Move Barbed Net's take into `SiteRelicTake`**

Create `src/main/scala/oathdigital/gameplay/powers/action/SiteRelicTake.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Taking a relic from a site, as Barbed Net and Skeleton Key do.
  *
  * Every relic at the site is recorded as a `Peek`, so the player may
  * identify it, and then the player chooses one, as Recover asks. It moves
  * to the player's board facedown, as a recovered relic does. With no relic
  * at the site nothing else happens.
  *
  * The relic question separates the peek step from the take step, and a
  * note covers only the step before it. So the peek has its own covering
  * line, `used.peeked`, as Scryer's does, and the take has the `used` line.
  *
  * @param reach the site the power reaches for the player, if any. With
  *   none, nothing is peeked, asked or written.
  */
final class SiteRelicTake(power: PowerId, decisionId: String, heading: String,
    reach: (ReadyGame, PlayerId) => Option[SiteId]):
  /** "{Red} peeked at the relics at {site}: {relics}." */
  val peeked: NoteKey = NoteKey("used.peeked", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the relics at "), NotePart.Arg(1),
    NotePart.Text(": "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} took {relic} facedown from {site}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" facedown from "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{site} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  def keys: Vector[NoteKey] = Vector(peeked, took, bare)

  def steps(player: PlayerId, source: DecisionOptionRef): Vector[Operation] =
    Vector[Operation](
      BuildOps((live, _) => Right(here(live, player).toVector.flatMap(
        (site, relics) => relics.map(relic =>
          Peek(player, relic, Location.Site(site)))))),
      Note(power, states => for
        card <- PowerSourceRef.of(source)
        (site, relics) <- here(states.now, player)
        if relics.nonEmpty
      yield peeked(card, NoteArg.Player(player), NoteArg.Site(site),
        NoteArg.Cards(relics)), covers = true),
      Note(power, states => for
        card <- PowerSourceRef.of(source)
        (site, relics) <- here(states.now, player)
        if relics.isEmpty
      yield bare(card, NoteArg.Site(site))),
      Branch((live, _) => ask(live, player)),
      BuildOps((live, pending) => take(live, player, pending)),
      Note(power, tookNote(_, player, source)))

  /** The site the power reaches and the relics there, in the site's order. */
  private def here(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, Vector[RelicId])] = for
    site <- reach(ready, actor)
    state <- ready.game.current.map.sites.get(site)
  yield (site, state.relics.map(_.id))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    here(ready, actor).filter(_._2.nonEmpty).toVector.map((_, relics) =>
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        relics.map(relic => DecisionOption.Relic(DecisionOptionRef.Relic(relic))),
        heading = Some(heading))))

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = here(ready, actor) match
    case Some((site, relics)) if relics.nonEmpty =>
      for
        ref <- PowerAnswers.one(pending, decisionId)
          .toRight(PowerAnswers.missing(decisionId))
        relic <- relics.find(DecisionOptionRef.Relic(_) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a relic at the actor's site"))
      yield Vector[CoreOperation](Move(Piece.Card(relic),
        PositionedLocation(Location.Site(site)),
        PositionedLocation(Location.PlayArea(actor)),
        resultingOrientation = Some(Orientation.FaceDown)))
    case _ => Right(Vector.empty)

  /** The relic the take step gave the player. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, actor).headOption
    site <- reach(states.now, actor)
  yield took(card, NoteArg.Player(actor), NoteArg.Card(relic),
    NoteArg.Site(site))
```

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Barbed Net (relic R36), ACTION: burn 3 secrets, then take a relic from
  * the player's site. The player may keep it facedown; the existing minor
  * action that reveals an owned relic covers turning it up later.
  *
  * The peek, the choice, the take and their lines are [[SiteRelicTake]]'s,
  * at the player's pawn site. With no relic there the cost stays paid and
  * nothing else happens.
  */
case object BarbedNet extends PaidAction("relic.barbed-net",
    Cost(secretBurnt = 3)):
  val decisionId: String = "power.barbed-net.relic"
  private val take = new SiteRelicTake(id, decisionId,
    "Barbed Net: take a relic from your site", PowerAccess.pawnSite)
  /** "{Red} peeked at the relics at {site}: {relics}." */
  val peeked: NoteKey = take.peeked
  /** "{Red} took {relic} facedown from {site}." */
  val took: NoteKey = take.took
  /** "{site} held no relic." */
  val bare: NoteKey = take.bare
  override def noteKeys: Vector[NoteKey] = take.keys

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(take.steps(player, source)))
```

- [ ] **Step 4: Run Barbed Net's suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BarbedNetSuite"`
Expected: all 6 tests pass unchanged. (`SkeletonKeySuite` does not compile yet; if sbt refuses to run any test because of it, move on to Step 5 and run both together in Step 7.)

- [ ] **Step 5: Create Skeleton Key**

Create `src/main/scala/oathdigital/gameplay/powers/action/SkeletonKey.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Skeleton Key (relic R13), ACTION: place 1 secret on this relic and burn 1
  * secret, then, if your pawn is at a Hinterland site, take a relic from
  * your site.
  *
  * The peek, the choice, the take and their lines are Barbed Net's
  * ([[SiteRelicTake]]), reached only from a Hinterland site. Away from the
  * Hinterland, or with no relic at the site, the cost stays paid and nothing
  * else happens. Away from the Hinterland its own line says so.
  */
case object SkeletonKey extends PaidAction("relic.skeleton-key",
    Cost(secret = 1, secretBurnt = 1)):
  val decisionId: String = "power.skeleton-key.relic"
  /** "{Red} was not at a Hinterland site." */
  val away: NoteKey = NoteKey("used.away", Vector(NotePart.Arg(0),
    NotePart.Text(" was not at a Hinterland site.")))
  private val take = new SiteRelicTake(id, decisionId,
    "Skeleton Key: take a relic from your site", hinterland)
  override def noteKeys: Vector[NoteKey] = take.keys :+ away

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    Note(id, states =>
      if hinterland(states.now, player).isDefined then None
      else PowerSourceRef.of(source).map(away(_, NoteArg.Player(player)))) +:
      take.steps(player, source)))

  /** The player's pawn site, when it is in the Hinterland. */
  private def hinterland(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    PowerAccess.pawnSite(ready, player).filter(site =>
      ready.game.current.map.regionOf(site).contains(Region.Hinterland))
```

- [ ] **Step 6: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`, replace:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs, TinkersFair) ++
```

with:

```scala
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs, TinkersFair, SkeletonKey) ++
```

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SkeletonKeySuite oathdigital.gameplay.powers.action.BarbedNetSuite"`
Expected: Skeleton Key's 6 tests pass, and Barbed Net's 6 pass unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SiteRelicTake.scala src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala src/main/scala/oathdigital/gameplay/powers/action/SkeletonKey.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/SkeletonKeySuite.scala
git commit -m "feat(powers): add Skeleton Key"
```

---

### Task 7: P6 and Messenger

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Messenger.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MessengerSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.forceKind`, `PowerAnswers.distribution(pending, decision): Option[Vector[DistributeAmount]]`, `PowerAccess.ruledSites(ready, player): Set[SiteId]`, `CampaignSetup.defenderAt(ready, site): Option[CampaignDefender]`, `DecisionQuery.Distribute.exactly(slots, total, heading, confirmLabel)`.
- Produces: `WarbandArrangement.holdings(ready, user, sites: Vector[SiteId]): (Int, Vector[(SiteId, Int)])`, `WarbandArrangement.movable(board: Int, sites: Vector[(SiteId, Int)]): Boolean`, `WarbandArrangement.query(user, board, sites, heading): DecisionQuery`, `WarbandArrangement.moves(ready, user, sites: Vector[SiteId], rows): Either[OathViolation, Vector[CoreOperation]]`; `Messenger` (`decisionId`, `redistributed`, `stuck`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/MessengerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class MessengerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val messenger = CatalogNames.denizen("Messenger")
  private val source = DecisionOptionRef.Denizen(messenger)
  private val home = Table.homeOf(p1)
  private val far = CatalogNames.site("Deep Woods")

  /** p1's Act holding Messenger with `favor` favor and `board` warbands on
    * the board. Unless `ruling` is false, p1 rules its home site with
    * `atHome` warbands and Deep Woods with one. */
  private def staged(board: Int = 3, atHome: Int = 2, ruling: Boolean = true,
      favor: Int = 1): ReadyGame =
    val table = Table.start.turn(p1, Phase.Act).adviser(p1, messenger)
      .favor(p1, favor).warbands(p1, board)
    (if ruling then table.warbandsAt(home, p1, atHome).warbandsAt(far, p1, 1)
    else table).ready

  private def count(ready: ReadyGame, site: SiteId): Int =
    Look(ready).forces(site) match
      case SiteForces.Occupied(_, n) => n
      case _ => 0
  private def arrangement(board: Int, atHome: Int, atFar: Int)
      : DecisionAnswer = DecisionAnswer.DistributeAnswer(Vector(
    DistributeAmount(DecisionOptionRef.Player(p1), board),
    DistributeAmount(DecisionOptionRef.Site(home), atHome),
    DistributeAmount(DecisionOptionRef.Site(far), atFar)))
  private def arranged(ready: ReadyGame, answered: DecisionAnswer)
      : Either[OathViolation, OathTransition] =
    answer(use(ready, Messenger, source).toOption.get, p1,
      Messenger.decisionId, answered)

  test("it places a favor and asks for one arrangement over the board and " +
      "every ruled site"):
    val t = use(staged(), Messenger, source).toOption.get
    assert(awaits(t, Messenger.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(messenger), Tokens(1, 0))

  test("the answer moves warbands from the board to the sites, keeping the " +
      "total"):
    val ready = staged()
    val t = use(ready, Messenger, source).toOption.get
    val done = answer(t, p1, Messenger.decisionId, arrangement(0, 4, 2))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).warbands(p1), 0)
    assertEquals(count(end, home), 4)
    assertEquals(count(end, far), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("warbands may go back to the board"):
    val end = after(arranged(staged(), arrangement(4, 1, 1)).toOption.get)
    assertEquals(Look(end).warbands(p1), 4)
    assertEquals(count(end, home), 1)
    assertEquals(count(end, far), 1)

  test("a site must keep a warband, and the total must be kept"):
    assert(arranged(staged(), arrangement(4, 2, 0)).isLeft)
    assert(arranged(staged(), arrangement(3, 2, 2)).isLeft)

  test("an answer that changes nothing is allowed"):
    val done = arranged(staged(), arrangement(3, 2, 1)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p1), 3)
    assertEquals(count(after(done), home), 2)

  test("its line follows the answer and covers nothing"):
    val done = arranged(staged(), arrangement(0, 4, 2)).toOption.get
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} redistributed their warbands.",
      covers = false)))

  test("with no ruled site nothing is asked, the favor stays paid, and the " +
      "line says so"):
    val done = use(staged(ruling = false), Messenger, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(messenger), Tokens(1, 0))
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("with one warband on each ruled site and none on the board, nothing " +
      "can move"):
    val done = use(staged(board = 0, atHome = 1), Messenger, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0)
    assert(!usableNow(broke).exists(_.power.id == Messenger.id))
    assert(use(broke, Messenger, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MessengerSuite"`
Expected: compilation fails with "Not found: Messenger".

- [ ] **Step 3: Add P6**

Create `src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** A player's warbands arranged again over their board and a set of sites
  * (P6): Warning Signals over the sites it defends, Messenger over the sites
  * its user rules. One exact distribution keeps the total and leaves each
  * site at least one warband.
  */
object WarbandArrangement:
  /** The player's board, and each of `sites` that holds warbands, with what
    * each holds now, in the order given. */
  def holdings(ready: ReadyGame, user: PlayerId, sites: Vector[SiteId])
      : (Int, Vector[(SiteId, Int)]) =
    val current = ready.game.current
    val board = current.players.find(_.player == user).fold(0)(_.board.warbands)
    (board, sites.flatMap(site => current.map.sites(site).forces match {
      case SiteForces.Occupied(_, count) if count > 0 => Some(site -> count)
      case _ => None
    }))

  /** Whether a warband can move: there is a site, and a warband beyond the
    * one each site keeps. */
  def movable(board: Int, sites: Vector[(SiteId, Int)]): Boolean =
    sites.nonEmpty && board + sites.map(_._2).sum > sites.size

  /** The exact distribution over the board and `sites`, each slot opening on
    * what it holds now. */
  def query(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      heading: String): DecisionQuery =
    val total = board + sites.map(_._2).sum
    val room = total - (sites.size - 1)
    val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
      Some(board)) +: sites.map { case (site, count) => DistributeSlot(
        DecisionOptionRef.Site(site), 1, room, Some(count)) }
    DecisionQuery.Distribute.exactly(slots, total, Some(heading),
      "Move warbands")

  /** The moves from what each of `sites` holds now to what the answer gives
    * it: every site that shrinks sends its extras to the board first, so the
    * board always holds what the sites that grow are given.
    */
  def moves(ready: ReadyGame, user: PlayerId, sites: Vector[SiteId],
      rows: Vector[DistributeAmount])
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, user).map { kind =>
      val (_, held) = holdings(ready, user, sites)
      val changes = held.flatMap { case (site, now) => rows.collectFirst {
        case DistributeAmount(DecisionOptionRef.Site(`site`), wanted) =>
          (site, wanted - now)
      }}
      def move(site: SiteId, count: Int, out: Boolean): CoreOperation =
        Move(Piece.Warbands(kind, count),
          PositionedLocation(if out then Location.Site(site)
            else Location.PlayArea(user)),
          PositionedLocation(if out then Location.PlayArea(user)
            else Location.Site(site)))
      changes.collect { case (site, delta) if delta < 0 =>
        move(site, -delta, out = true) } ++
        changes.collect { case (site, delta) if delta > 0 =>
          move(site, delta, out = false) }
    }
```

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers,
  WarbandArrangement}
import oathdigital.model._

/** Warning Signals (card 25), a defender's battle plan: "Move any warbands to and
  * from your board and any sites you rule (except the last warband from a site).
  * At end, discard Warning Signals."
  *
  * Only a player defender uses it, from an adviser or a site the defender rules.
  * It costs nothing. When it is chosen the defender arranges their warbands
  * again, before their force is scored: one distribution over their board and
  * every site they rule, whether or not it is targeted, that keeps the total and
  * leaves each site at least one warband ([[WarbandArrangement]], shared with
  * Messenger). Nothing is asked when there is nowhere to move to: no ruled
  * site, or no warband beyond the one each site keeps. The card is discarded
  * when the Campaign has resolved, whether or not the defender won.
  *
  * Once the defender has answered it writes "{Blue} redistributed their
  * warbands."; the line follows the decision, so a game parked on it resumes
  * where it was.
  */
final case class WarningSignals private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(WarningSignals.redistributed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Warning Signals: rearrange your warbands",
        Vector.empty, Vector(CampaignPlanEffect.Run(Vector(rearrange(user)))))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))

  /** The sites the user defends as a player, in map order. */
  private def defended(ready: ReadyGame, user: PlayerId): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(site => CampaignSetup
      .defenderAt(ready, site).contains(CampaignDefender.Player(user)))

  private def rearrange(user: PlayerId): Operation = Branch((ready, _) => {
    val (board, sites) = WarbandArrangement.holdings(ready, user,
      defended(ready, user))
    if !WarbandArrangement.movable(board, sites) then Vector.empty
    else Vector(
      Decide(WarningSignals.decisionId, user, WarbandArrangement.query(user,
        board, sites, "Warning Signals: arrange your warbands. Your board " +
          "holds the ones no site keeps, and each site keeps at least one")),
      Note(id, _ => Some(WarningSignals.redistributed(
        PowerSourceRef.Card(cardId), NoteArg.Player(user)))),
      BuildOps((state, pending) => PowerAnswers.distribution(pending,
        WarningSignals.decisionId).toRight(PowerAnswers.missing(
        WarningSignals.decisionId)).flatMap(rows => WarbandArrangement.moves(
        state, user, defended(state, user), rows))))
  })

object WarningSignals:
  val id: PowerId = PowerId("denizen.warning-signals")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "warning-signals"
  /** "{Blue} redistributed their warbands." */
  val redistributed: NoteKey = NoteKey("redistributed", Vector(NotePart.Arg(0),
    NotePart.Text(" redistributed their warbands.")))

  def forCatalog(catalog: ExecutableCatalog): Option[WarningSignals] =
    CatalogCards.denizen(catalog, id).map(new WarningSignals(_, catalog))
```

- [ ] **Step 4: Run Warning Signals' suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WarningSignalsSuite"`
Expected: every test passes unchanged. (If sbt refuses to compile because `MessengerSuite` names `Messenger`, do Step 5 first and run both in Step 7.)

- [ ] **Step 5: Create Messenger**

Create `src/main/scala/oathdigital/gameplay/powers/action/Messenger.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{PowerAnswers, WarbandArrangement}
import oathdigital.model._

/** Messenger (card 105), ACTION: place 1 favor on this card, then move any
  * warbands to and from your board and any sites you rule, except the last
  * warband from a site.
  *
  * The player arranges their warbands again over their board and every site
  * they rule, as Warning Signals does ([[WarbandArrangement]]): one exact
  * distribution that keeps the total and leaves each site at least one
  * warband. It is asked only when a warband can move, and an answer that
  * changes nothing is allowed. With nothing to move the cost stays paid and
  * the line says so.
  *
  * The line follows the answer, as Warning Signals' does, and covers
  * nothing: the generic Moved lines stay, since they tell where warbands
  * went.
  */
case object Messenger extends PaidAction("denizen.messenger",
    Cost(favor = 1)):
  val decisionId: String = "power.messenger.arrange"
  /** "{Red} redistributed their warbands." */
  val redistributed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" redistributed their warbands.")))
  /** "No warband could be moved." */
  val stuck: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No warband could be moved.")))
  override def noteKeys: Vector[NoteKey] = Vector(redistributed, stuck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Branch((live, _) => {
    val (board, sites) = WarbandArrangement.holdings(live, player,
      ruled(live, player))
    if !WarbandArrangement.movable(board, sites) then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(stuck(_))))
    else Vector(
      Decide(decisionId, player, WarbandArrangement.query(player, board, sites,
        "Messenger: arrange your warbands. Your board holds the ones no site " +
          "keeps, and each site keeps at least one")),
      Note(id, _ => PowerSourceRef.of(source).map(card =>
        redistributed(card, NoteArg.Player(player)))),
      BuildOps((state, pending) => PowerAnswers.distribution(pending,
        decisionId).toRight(PowerAnswers.missing(decisionId)).flatMap(rows =>
        WarbandArrangement.moves(state, player, ruled(state, player), rows))))
  }))

  /** The sites the player rules, in map order. */
  private def ruled(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val sites = PowerAccess.ruledSites(ready, player)
    ready.game.current.map.inPlay.filter(sites.contains)
```

- [ ] **Step 6: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`, replace:

```scala
        TavernSongs, TinkersFair, SkeletonKey) ++
```

with:

```scala
        TavernSongs, TinkersFair, SkeletonKey, Messenger) ++
```

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.MessengerSuite oathdigital.gameplay.powers.campaign.WarningSignalsSuite"`
Expected: Messenger's 9 tests pass, and Warning Signals' suite passes unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/WarbandArrangement.scala src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala src/main/scala/oathdigital/gameplay/powers/action/Messenger.scala src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/MessengerSuite.scala
git commit -m "feat(powers): add Messenger"
```

---

### Task 8: Docs and gates

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
protect Campaign targets only. Slices 3a to 4 remain.
```

with:

```markdown
protect Campaign targets only. Slice 3a is done: the ACTION powers Blood
Pact, Arcane Brokers, Bog, Relic Breaker, Bed of Roots, Tavern Songs, Tinker's
Fair, Skeleton Key and Messenger. Slices 3b to 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 57 (6 in Task 1, 22 in Task 2, 6 in Task 3, 4 in Task 4, 4 in Task 5, 6 in Task 6, 9 in Task 7). From a baseline of 2331, that is 2388. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 3a"
```
