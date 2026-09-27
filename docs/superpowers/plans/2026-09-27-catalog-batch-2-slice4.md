# Catalog Batch 2, Slice 4 (Actions on Others) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the five ACTION powers that act on other players or on places: Spoiled Supplies (denizen 228), Charming Friend (131), Siege Engines (116), Book of Records (relic R19) and Barbed Net (relic R36).

**Architecture:**

- Each power is a `PaidAction`, as in slices 3a to 3c, so the engine pays its cost onto the card or to the bank and reads "payable" as its usability. None needs an engine addition.
- They register through one new object, `OtherActionPowers`, which `PhasePowerCatalog` adds beside `SelfActionPowers`. No power here needs the catalog.
- The shapes they copy:
  - Spoiled Supplies: a `Branch` that reads the other players at the actor's site and emits one `SpendSupply` and one `Note` per player who has Supply.
  - Charming Friend: Sleight of Hand's shape (`Branch` asking for a player at the site, `BuildOps` taking, `Note`), taking one favor with `Take`.
  - Siege Engines: Wolves' shape (a plain `Decide`, `BuildOps` killing, `Note`), with the sites of the pawn's region as options and `Kill` from a site. The existing post-action bandit refill fills a site it empties.
  - Book of Records: Sleight of Hand's shape with banners as options, taking favor or secrets from the banner with `Take`.
  - Barbed Net: Scryer's peek-then-note, then Recover's relic question and facedown `Move`.
- None of these effects writes a generic detail line. `SpendSupply`, a `Take` of favor between boards or from a banner, a `Kill` at a site and a relic `Move` from a site to a board all post nothing, so only Barbed Net's peek note covers anything: the `Peek` lines.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. There is no frontend work.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md` ("Slicing", "Log lines", "Testing"), with the per-card rulings in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 4: actions on others"). Read both before starting. Slices 1 to 3c are merged on `main`.

**Rulings made while planning:**

- **Siege Engines' bandit line uses key `used.bandits`, not `bandits`.** A bandit kill is an alternative outcome, not an extra line. A key that is not `used` or `used.*` would leave "Used Siege Engines" posted beside it and mark the line as a trigger. Cost if wrong: a key rename.
- **Barbed Net's peek gets its own line, key `used.peeked`: "{Red} peeked at the relics at {site}: {relics}."** It covers the `Peek` lines. The spec has the take line cover them, but the relic question splits the peek step from the take step, and a note covers only the step right before it. Both lines are `used.*`, so neither "Used Barbed Net" nor a generic peek line posts. Cost if wrong: one extra log line per use.
- **Book of Records with no banner held at the actor's site stays usable.** Batch 1's usability rule makes the cost the only gate. The cost is paid and it writes `used.none`: "No player at the site held a banner." The spec lists no line for this case.
- **Spoiled Supplies writes one `used` line per player who lost Supply,** each right after that player's `SpendSupply`, so each note reads its own step. A player at the site with 0 Supply is skipped, not charged. "Enemies" are every other player, since every game is all-Exile.
- **Charming Friend asks even with one target, as Sleight of Hand does.** A target with no favor is still offered; choosing them takes nothing and writes `used.empty`.
- **Siege Engines offers every site in the pawn's region,** including the pawn's own site and sites with no warband. It kills `min(2, count)` of whatever kind holds the site.
- **Barbed Net peeks at every relic at the site, faceup or facedown,** and asks even with one relic, as Recover does. The taken relic arrives facedown through a `Move` with `resultingOrientation = Some(Orientation.FaceDown)`, as Recover's is.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member, or a non-exhaustive match over a sealed type, fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules: `gameplay` never imports `application`, `serialization` or `server`. A power under `gameplay/powers` never imports `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree branched from local `main`, with the main checkout's `.tooling` symlinked in before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Card ids and costs:

  | Card | Source ref | Power id | Cost |
  |---|---|---|---|
  | Spoiled Supplies | `DecisionOptionRef.Denizen(DenizenId("228"))` | `denizen.spoiled-supplies` | `Cost(favor = 1)` |
  | Charming Friend (adviser-only) | `DecisionOptionRef.Denizen(DenizenId("131"))` | `denizen.charming-friend` | `Cost(secret = 1)` |
  | Siege Engines | `DecisionOptionRef.Denizen(DenizenId("116"))` | `denizen.siege-engines` | `Cost(favor = 1)` |
  | Book of Records | `DecisionOptionRef.Relic(RelicId("R19"))` | `relic.book-of-records` | `Cost(secret = 1, secretBurnt = 2)` |
  | Barbed Net | `DecisionOptionRef.Relic(RelicId("R36"))` | `relic.barbed-net` | `Cost(secretBurnt = 3)` |

- Log lines, where `{Red}` is the acting player and `{Blue}` another. None covers except where marked.

  | Card | Key | Line |
  |---|---|---|
  | Spoiled Supplies, per player | `used` | Spoiled Supplies: {Blue} lost {1 Supply}. |
  | Spoiled Supplies, nobody | `used.none` | Spoiled Supplies: No enemy lost Supply. |
  | Charming Friend | `used` | Charming Friend: {Red} took {1 favor} from {Blue}. |
  | Charming Friend, nobody at the site | `used.none` | Charming Friend: No player could be robbed. |
  | Charming Friend, no favor | `used.empty` | Charming Friend: {Blue} had no favor to take. |
  | Siege Engines | `used` | Siege Engines: Killed {n} {Blue} warband(s) at {site}. |
  | Siege Engines, bandits | `used.bandits` | Siege Engines: Killed {n} bandit warband(s) at {site}. |
  | Siege Engines, no warband | `used.none` | Siege Engines: {site} had no warband to kill. |
  | Book of Records | `used` | Book of Records: {Red} took {2 favor} from {Blue}'s {People's Favor}. |
  | Book of Records, empty banner | `used.empty` | Book of Records: {Blue}'s {banner} held nothing to take. |
  | Book of Records, no banner | `used.none` | Book of Records: No player at the site held a banner. |
  | Barbed Net, the peek (covers) | `used.peeked` | Barbed Net: {Red} peeked at the relics at {site}: {relics}. |
  | Barbed Net, the take | `used` | Barbed Net: {Red} took {relic} facedown from {site}. |
  | Barbed Net, no relic | `used.none` | Barbed Net: {site} held no relic. |

- Baselines: record the server test count from your first full `./sbtw "test"` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala` | Registers slice 4's powers. |
| Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` | Adds `OtherActionPowers.powers`. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/SpoiledSupplies.scala` | Every other player at the site loses 1 Supply. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/CharmingFriend.scala` | Take 1 favor from a player at the site. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/SiegeEngines.scala` | Kill up to 2 warbands at a site in the pawn's region. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala` | Take up to 2 from a banner held at the site. |
| Create `src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala` | Peek at the site's relics, take one facedown. |
| Create one suite per card under `src/test/scala/oathdigital/gameplay/powers/action/` | Walker-driven suites. |
| Modify `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` | A `barbed-net` script, added to `named`. |
| Create `src/test/resources/gamelog/barbed-net.actor.log`, `barbed-net.other.log` | The script's golden logs. |
| Modify `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` | Barbed Net's two lines and who may read the relic. |
| Modify `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala` | Pins the five powers. |
| Modify `docs/ROADMAP.md` | Records slice 4 done. |

---

### Task 1: Spoiled Supplies and Charming Friend

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SpoiledSupplies.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/CharmingFriend.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SpoiledSuppliesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/CharmingFriendSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `NoteSupport.took`, `NoteSupport.favor`, `NoteSupport.supply`, `NoteSupport.answer`, `PowerAnswers.one`, `PowerAnswers.missing`, `PowerAccess.pawnSite`; in tests `PowerFixture`, `TargetsFixture`, `PaidActionHarness.wireRoundTrips`, `NoteText`.
- Produces: `object OtherActionPowers { val powers: Vector[PhasePower] }`, which Tasks 2 to 4 extend; `SpoiledSupplies`; `CharmingFriend` with `decisionId = "power.charming-friend.target"`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/action/SpoiledSuppliesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SpoiledSuppliesSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val spoiled = DenizenId("228")
  private val source = DecisionOptionRef.Denizen(spoiled)
  private val first = others(base)(0)
  private val second = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withSupply(ready: ReadyGame, id: PlayerId, amount: Int) =
    updatePlayer(ready, id)(p => p.copy(board =
      p.board.copy(supply = SupplyTrack(amount))))
  private def supplyOf(ready: ReadyGame, id: PlayerId = actor) =
    player(ready, id).board.supply.supply
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == spoiled => d }.get

  /** Spoiled Supplies at the actor's site, with `favor` on the actor's
    * board. `first` stands at the actor's site with `supply`; `second`
    * stands elsewhere with 3 Supply, so it is never hit.
    */
  private def staged(supply: Int = 3, favor: Int = 1) =
    val actorReady = inPhase(withBoard(atHome(base, spoiled))(
      _.copy(favor = favor)), Phase.Act)
    val placed = withPawn(withPawn(actorReady, first, home(actorReady)),
      second, elsewhere(actorReady))
    withSupply(withSupply(placed, first, supply), second, 3)

  test("Spoiled Supplies is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SpoiledSupplies.id).isDefined)

  test("each other player at the actor's site loses 1 Supply, and nobody " +
      "else does"):
    val ready = staged()
    val done = use(ready, SpoiledSupplies, source).toOption.get
    parked.assertNotParked(done.state)
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 3)
    assertEquals(supplyOf(after(done)), supplyOf(ready))
    assertEquals(cardOf(after(done)).tokens, Tokens(1, 0))
    assertEquals(player(after(done)).board.favor, 0)
    assertEquals(replayed(ready, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))

  test("it writes the Supply each player lost"):
    val done = use(staged(), SpoiledSupplies, source).toOption.get
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false)))

  test("two players at the site each lose 1 Supply, each with a line"):
    val ready = withPawn(staged(), second, home(staged()))
    val done = use(ready, SpoiledSupplies, source).toOption.get
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 2)
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false),
      NoteText.Said(NoteKey.Used, s"${second.value} lost 1 Supply.",
        covers = false)))

  test("a player with no Supply loses none, and the line says nobody did"):
    val done = use(staged(supply = 0), SpoiledSupplies, source).toOption.get
    assertEquals(supplyOf(after(done), first), 0)
    assertEquals(cardOf(after(done)).tokens, Tokens(1, 0))
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said("used.none", "No enemy lost Supply.", covers = false)))

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SpoiledSupplies.id))
    assert(use(ready, SpoiledSupplies, source).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/powers/action/CharmingFriendSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class CharmingFriendSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val friend = DenizenId("131")
  private val source = DecisionOptionRef.Denizen(friend)
  private val victim = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withFavor(ready: ReadyGame, id: PlayerId, amount: Int) =
    updatePlayer(ready, id)(p => p.copy(board = p.board.copy(favor = amount)))
  private def favorOf(ready: ReadyGame, id: PlayerId = actor) =
    player(ready, id).board.favor
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def cardOf(ready: ReadyGame) = player(ready).advisers.collectFirst {
    case d: DenizenState if d.id == friend => d }.get

  /** The actor holds Charming Friend as an adviser with `secrets` faceup.
    * `victim` stands at the actor's site with `favor`; `bystander` stands
    * elsewhere with 5 favor, so it is never a target.
    */
  private def staged(favor: Int = 2, secrets: Int = 1) =
    val actorReady = inPhase(withSecrets(asAdviser(base, friend), actor,
      secrets, 0), Phase.Act)
    val placed = withPawn(withPawn(actorReady, victim, home(actorReady)),
      bystander, elsewhere(actorReady))
    withFavor(withFavor(placed, victim, favor), bystander, 5)

  test("Charming Friend is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(CharmingFriend.id).isDefined)

  test("it places a secret on its card and offers the players at the " +
      "actor's site"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    assert(awaits(t, CharmingFriend.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)

  test("the chosen player gives one favor"):
    val ready = staged()
    val t = use(ready, CharmingFriend, source).toOption.get
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(favorOf(after(done), victim), 1)
    assertEquals(favorOf(after(done)), favorOf(ready) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("it writes the favor it took"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from ${victim.value}.", covers = false)))

  test("a chosen player with no favor gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val t = use(ready, CharmingFriend, source).toOption.get
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(favorOf(after(done), victim), 0)
    assertEquals(favorOf(after(done)), favorOf(ready))
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said("used.empty", s"${victim.value} had no favor to take.",
        covers = false)))

  test("with nobody at the site the cost is paid and nothing else happens"):
    val ready = withPawn(staged(), victim, elsewhere(staged()))
    val t = use(ready, CharmingFriend, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(favorOf(after(t), victim), 2)
    assertEquals(NoteText.said(CharmingFriend, t.events), Vector(
      NoteText.Said("used.none", "No player could be robbed.",
        covers = false)))

  test("every other player at the site is offered"):
    val ready = withPawn(staged(), bystander, home(staged()))
    val t = use(ready, CharmingFriend, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))

  test("only the acting player answers, with an offered player"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    assert(answer(t, victim, CharmingFriend.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, CharmingFriend.decisionId, choose(bystander))
      .isLeft)
    assert(answer(t, actor, CharmingFriend.decisionId, choose(actor)).isLeft)

  test("it is unusable without a secret to place"):
    val ready = staged(secrets = 0)
    assert(!usableNow(ready).exists(_.power.id == CharmingFriend.id))
    assert(use(ready, CharmingFriend, source).isLeft)
```

- [ ] **Step 2: Run the suites to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SpoiledSuppliesSuite oathdigital.gameplay.powers.action.CharmingFriendSuite"`
Expected: compilation fails: `SpoiledSupplies` and `CharmingFriend` are not found.

- [ ] **Step 3: Write Spoiled Supplies**

Create `src/main/scala/oathdigital/gameplay/powers/action/SpoiledSupplies.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Spoiled Supplies (card 228), ACTION: place 1 favor on this card, then
  * every enemy whose pawn is at the player's site loses 1 Supply. Every game
  * is all-Exile, so every other player is an enemy. Nothing is asked.
  *
  * A player with no Supply is skipped rather than charged. Each loss is its
  * own `SpendSupply` followed by its own note, so each line reads the step
  * it restates. With nobody losing any, one line says so.
  */
case object SpoiledSupplies extends PaidAction("denizen.spoiled-supplies",
    Cost(favor = 1)):
  val Loss: Int = 1
  /** "{Blue} lost {1 Supply}." */
  val lost: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" lost "), NotePart.Arg(1), NotePart.Text(".")))
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No enemy lost Supply.")))
  override def noteKeys: Vector[NoteKey] = Vector(lost, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Branch((live, _) => {
    val hit = enemies(live, player).filter(_.board.supply.supply > 0)
    if hit.isEmpty then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
    else hit.flatMap(target => Vector[Operation](
      SpendSupply(target.player, Loss, required = false),
      Note(id, lostNote(_, target.player, source))))
  }))

  /** The Supply `target` lost in the step before the note. */
  private def lostNote(states: NoteStates, target: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = -NoteSupport.supply(step, target)
    if amount > 0
  yield lost(card, NoteArg.Player(target), NoteArg.Amount(amount,
    NoteUnit.Supply))

  private def enemies(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))
```

- [ ] **Step 4: Write Charming Friend**

Create `src/main/scala/oathdigital/gameplay/powers/action/CharmingFriend.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Charming Friend (card 131, adviser-only), ACTION: place 1 secret on this
  * card, then take 1 favor from a player whose pawn is at the player's site.
  *
  * The shape is Sleight of Hand's. The targets are read live, after the cost
  * is paid, and the question is asked whenever there is one. With none, the
  * cost stays paid and nothing else happens. A chosen player with no favor
  * is still a legal choice and gives nothing. The take is a `Take`, so a
  * restriction on taking applies to it.
  */
case object CharmingFriend extends PaidAction("denizen.charming-friend",
    Cost(secret = 1)):
  val decisionId: String = "power.charming-friend.target"
  val Taken: Int = 1
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player could be robbed.")))
  /** "{Blue} had no favor to take." */
  val broke: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to take.")))
  override def noteKeys: Vector[NoteKey] =
    Vector(NoteSupport.took, nobody, broke)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => charm(live, player, pending)),
    Note(id, charmNote(_, player, source)))))

  /** No answer means nobody was at the site. */
  private def charmNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Player(target)) =>
          val amount = states.previous.fold(0)(NoteSupport.favor(_, actor))
          if amount > 0 then NoteSupport.took(card, NoteArg.Player(actor),
            NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(target))
          else broke(card, NoteArg.Player(target))
        case _ => nobody(card))

  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    val found = targets(ready, actor)
    if found.isEmpty then Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some(
        "Charming Friend: take a favor from a player at your site"))))

  private def charm(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = targets(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a legal Charming Friend target"))
    yield
      if target.board.favor == 0 then Vector.empty
      else Vector(Take(Piece.Favor(Taken), actor,
        Location.PlayArea(target.player), Location.PlayArea(actor)))
```

- [ ] **Step 5: Register them**

Create `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4, actions on others,
  * registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]] through
  * this one object, like [[SelfActionPowers]].
  */
object OtherActionPowers:
  val powers: Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend)
```

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, add `OtherActionPowers` to the `oathdigital.gameplay.powers.action` import, keeping it alphabetical:

```scala
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, MovementPowers, OtherActionPowers, SelfActionPowers, TargetPowers, WaysideInn}
```

and replace:

```scala
      SelfActionPowers.forCatalog(catalog) ++
```

with:

```scala
      SelfActionPowers.forCatalog(catalog) ++
      OtherActionPowers.powers ++
```

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SpoiledSuppliesSuite oathdigital.gameplay.powers.action.CharmingFriendSuite"`
Expected: PASS, 15 tests.

If the walker refuses the `SpendSupply` of a player who is not acting, stop and report it: that is an engine gap the plan did not foresee, not something to work around inside the power.

- [ ] **Step 7: Run the power suites and the architecture suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.* oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `PowerNoteCatalogSuite` checks the new templates.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SpoiledSupplies.scala \
  src/main/scala/oathdigital/gameplay/powers/action/CharmingFriend.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/action/SpoiledSuppliesSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/CharmingFriendSuite.scala
git commit -m "feat(powers): Spoiled Supplies and Charming Friend act on players at your site"
```

---

### Task 2: Siege Engines

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SiegeEngines.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SiegeEnginesSuite.scala`

**Interfaces:**
- Consumes: `OtherActionPowers.powers` from Task 1; `PlayerForceKind.of(ready, state): Option[ForceKind]`; `MapState.regionOf`; `Kill`; in tests `PowerFixture.warbandBank`, `PlayerFacts.forceKind`.
- Produces: `SiegeEngines` with `decisionId = "power.siege-engines.site"` and `def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId]`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/SiegeEnginesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PlayerFacts,
  PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SiegeEnginesSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val engines = DenizenId("116")
  private val source = DecisionOptionRef.Denizen(engines)
  private val victim = others(base).head

  private def region(ready: ReadyGame): Region =
    ready.game.current.map.regionOf(home(ready)).get
  private def inRegion(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(
      ready.game.current.map.regionOf(_).contains(region(ready)))
  /** Another site in the actor's region. */
  private def neighbour(ready: ReadyGame): SiteId =
    inRegion(ready).find(_ != home(ready)).get
  private def faraway(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(!inRegion(ready).contains(_)).get
  private def withForces(ready: ReadyGame, site: SiteId, forces: SiteForces) =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces = forces)))))
  private def forcesAt(ready: ReadyGame, site: SiteId) =
    ready.game.current.map.sites(site).forces
  private def kindOf(ready: ReadyGame, id: PlayerId) =
    PlayerFacts.forceKind(ready, id).toOption.get
  private def choose(site: SiteId) = pick(DecisionOptionRef.Site(site))
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == engines => d }.get

  /** Siege Engines at the actor's site with `favor` on the actor's board. */
  private def staged(favor: Int = 1) = inPhase(withBoard(atHome(base,
    engines))(_.copy(favor = favor)), Phase.Act)

  /** Uses it and answers `site`, with `forces` staged there first. */
  private def killAt(site: ReadyGame => SiteId, forces: ReadyGame => SiteForces) =
    val ready = withForces(staged(), site(staged()), forces(staged()))
    val t = use(ready, SiegeEngines, source).toOption.get
    val done = answer(t, actor, SiegeEngines.decisionId, choose(site(ready)))
      .toOption.get
    (ready, t, done)

  test("Siege Engines is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SiegeEngines.id).isDefined)

  test("it places a favor on its card and offers every site in the " +
      "actor's region"):
    val t = use(staged(), SiegeEngines, source).toOption.get
    assert(awaits(t, SiegeEngines.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(inRegion(staged()).map(site => "site" -> site.value).toSet))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)

  test("it kills two of the chosen site's warbands, which return to their " +
      "bank"):
    val (ready, t, done) = killAt(neighbour,
      r => SiteForces.Occupied(kindOf(r, victim), 3))
    val kind = kindOf(ready, victim)
    assertEquals(forcesAt(after(done), neighbour(ready)),
      SiteForces.Occupied(kind, 1))
    assertEquals(warbandBank(after(done), kind), warbandBank(after(t), kind) + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${victim.value} warbands at ${neighbour(ready).value}.",
        covers = false)))

  test("the actor's own warbands die too, and the emptied site fills with " +
      "bandits"):
    val (ready, _, done) = killAt(home,
      r => SiteForces.Occupied(kindOf(r, actor), 2))
    val capacity = catalog.site(home(ready)).get.capacity
    assertEquals(forcesAt(after(done), home(ready)),
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${actor.value} warbands at ${home(ready).value}.",
        covers = false)))

  test("a site with one warband loses it"):
    val (ready, _, done) = killAt(neighbour,
      r => SiteForces.Occupied(kindOf(r, victim), 1))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 1 ${victim.value} warband at ${neighbour(ready).value}.",
        covers = false)))

  test("bandits die with their own line"):
    val (ready, _, done) = killAt(neighbour,
      _ => SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(forcesAt(after(done), neighbour(ready)),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.bandits",
        s"Killed 2 bandit warbands at ${neighbour(ready).value}.",
        covers = false)))

  test("a site with no warband loses none, and the line says so"):
    val (ready, _, done) = killAt(neighbour, _ => SiteForces.Empty)
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.none",
        s"${neighbour(ready).value} had no warband to kill.", covers = false)))

  test("a site outside the actor's region is refused"):
    val t = use(staged(), SiegeEngines, source).toOption.get
    assert(answer(t, actor, SiegeEngines.decisionId, choose(faraway(staged())))
      .isLeft)

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SiegeEngines.id))
    assert(use(ready, SiegeEngines, source).isLeft)
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SiegeEnginesSuite"`
Expected: compilation fails: `SiegeEngines` is not found.

- [ ] **Step 3: Write Siege Engines**

Create `src/main/scala/oathdigital/gameplay/powers/action/SiegeEngines.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Siege Engines (card 116), ACTION: place 1 favor on this card, then kill
  * two warbands, even the player's own, at any one site in the region of the
  * player's pawn.
  *
  * Every site in the region is offered, whoever rules it and whether or not
  * it holds warbands. A site holds one kind of warband, so the kill is one
  * `Kill` of up to two of that kind. A site the kill empties is refilled with
  * bandits by the existing refill after the action.
  *
  * The decision is a plain `Decide`, as Wolves' is: its options are the
  * region's sites, which the cost does not change.
  */
case object SiegeEngines extends PaidAction("denizen.siege-engines",
    Cost(favor = 1)):
  val decisionId: String = "power.siege-engines.site"
  val Kills: Int = 2
  /** "Killed {n} {Blue} warbands at {site}." */
  val killed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warbands at {site}." */
  val bandits: NoteKey = NoteKey("used.bandits", Vector(
    NotePart.Text("Killed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "{site} had no warband to kill." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  override def noteKeys: Vector[NoteKey] = Vector(killed, bandits, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      sites(ready, player).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Siege Engines: kill two warbands at a site in your region"))),
    BuildOps((live, pending) => kill(live, player, pending)),
    Note(id, killNote(_, source)))))

  /** The sites in the region of the player's pawn, in map order. */
  def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val map = ready.game.current.map
    PowerAccess.pawnSite(ready, player).flatMap(map.regionOf).toVector
      .flatMap(region => map.inPlay.filter(map.regionOf(_).contains(region)))

  private def forces(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def kill(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    ref <- PowerAnswers.one(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
    site <- sites(ready, actor).find(DecisionOptionRef.Site(_) == ref)
      .toRight(OathViolation.InvalidEventOrder(
        s"${ref.wireId} is not a site in the actor's region"))
  yield forces(ready, site) match
    case Some(SiteForces.Occupied(kind, count)) => Vector(Kill(
      Piece.Warbands(kind, math.min(Kills, count)),
      PositionedLocation(Location.Site(site))))
    case _ => Vector.empty

  /** The warbands the chosen site lost in the kill step, and whose. */
  private def killNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Site(id) => id }
    step <- states.previous
    note <- forces(step._1, site) match
      case Some(SiteForces.Occupied(kind, before)) =>
        val lost = before - remaining(step._2, site, kind)
        if lost <= 0 then Some(spared(card, NoteArg.Site(site)))
        else if kind == ForceKind.Bandit then
          Some(bandits(card, NoteArg.Number(lost), NoteArg.Site(site)))
        else owner(step._1, kind).map(whose => killed(card,
          NoteArg.Number(lost), NoteArg.Player(whose), NoteArg.Site(site)))
      case _ => Some(spared(card, NoteArg.Site(site)))
  yield note

  private def remaining(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forces(ready, site) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The player whose warbands are of `kind`. */
  private def owner(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(p =>
      PlayerForceKind.of(ready, p).contains(kind)).map(_.player)
```

In `OtherActionPowers.scala`, replace `Vector[PhasePower](SpoiledSupplies, CharmingFriend)` with `Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines)`.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SiegeEnginesSuite"`
Expected: PASS, 9 tests.

If "the emptied site fills with bandits" fails because no refill ran after the power, stop and report it: the ruling relies on the post-action refill, and a `UsePower` run that skips it is an engine gap.

- [ ] **Step 5: Run the power suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SiegeEngines.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/SiegeEnginesSuite.scala
git commit -m "feat(powers): Siege Engines kills two warbands at a site in your region"
```

---

### Task 3: Book of Records

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BookOfRecordsSuite.scala`

**Interfaces:**
- Consumes: `OtherActionPowers.powers`; `BannerRules.holder`, `BannerRules.resources`; `DecisionOption.Banner`; in tests `PowerFixture.withRelic`, `PaidActionHarness.{secrets, tokensOn, wireRoundTrips}`.
- Produces: `BookOfRecords` with `decisionId = "power.book-of-records.banner"`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BookOfRecordsSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BookOfRecordsSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._
  import PaidActionHarness.{secrets, tokensOn, wireRoundTrips}

  private val book = RelicId("R19")
  private val source = DecisionOptionRef.Relic(book)
  private val holder = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withBanner(ready: ReadyGame, banner: Banner,
      owner: Option[PlayerId], amount: Int): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = banner match
      case Banner.PeoplesFavor => c.banners.copy(peoplesFavor =
        c.banners.peoplesFavor.copy(holder = owner, favor = amount))
      case Banner.DarkestSecret => c.banners.copy(darkestSecret =
        c.banners.darkestSecret.copy(holder = owner, secrets = amount))))
  private def held(ready: ReadyGame, banner: Banner): Int = banner match
    case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.favor
    case Banner.DarkestSecret => ready.game.current.banners.darkestSecret.secrets
  private def choose(banner: Banner) = pick(DecisionOptionRef.Banner(banner))

  /** The actor holds Book of Records faceup with `faceUp` secrets. `holder`
    * stands at the actor's site holding the People's Favor with `favor`;
    * `bystander` stands elsewhere holding the Darkest Secret with 3 secrets,
    * so it is never offered.
    */
  private def staged(favor: Int = 3, faceUp: Int = 3): ReadyGame =
    val actorReady = inPhase(withSecrets(withRelic(base, book), actor, faceUp,
      0), Phase.Act)
    val placed = withPawn(withPawn(actorReady, holder, home(actorReady)),
      bystander, elsewhere(actorReady))
    withBanner(withBanner(placed, Banner.PeoplesFavor, Some(holder), favor),
      Banner.DarkestSecret, Some(bystander), 3)

  /** Uses it on `ready` and answers `banner`. */
  private def takeFrom(ready: ReadyGame, banner: Banner) =
    val t = use(ready, BookOfRecords, source).toOption.get
    (t, answer(t, actor, BookOfRecords.decisionId, choose(banner)).toOption.get)

  test("Book of Records is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(BookOfRecords.id).isDefined)

  test("it places a secret, burns two, and offers the banners held at the " +
      "actor's site"):
    val t = use(staged(), BookOfRecords, source).toOption.get
    assert(awaits(t, BookOfRecords.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    assertEquals(tokensOn(after(t), book), Tokens(0, 1))
    assertEquals(secrets(after(t)), 0)

  test("the People's Favor gives two favor"):
    val ready = staged()
    val (t, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 2)
    assertEquals(held(after(done), Banner.PeoplesFavor), 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the Darkest Secret gives two secrets, faceup"):
    val ready = withBanner(staged(), Banner.DarkestSecret, Some(holder), 3)
    val (_, done) = takeFrom(ready, Banner.DarkestSecret)
    assertEquals(player(after(done)).board.faceUpSecrets, 2)
    assertEquals(held(after(done), Banner.DarkestSecret), 1)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 secrets from " +
        s"${holder.value}'s Darkest Secret.", covers = false)))

  test("a banner holding one gives one"):
    val ready = staged(favor = 1)
    val (_, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(held(after(done), Banner.PeoplesFavor), 0)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 1 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the actor's own banner may be chosen"):
    val ready = withBanner(staged(), Banner.PeoplesFavor, Some(actor), 2)
    val t = use(ready, BookOfRecords, source).toOption.get
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    val done = answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.PeoplesFavor)).toOption.get
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 2)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${actor.value}'s People's Favor.", covers = false)))

  test("an empty banner gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val (_, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said("used.empty",
        s"${holder.value}'s People's Favor held nothing to take.",
        covers = false)))

  test("with no banner held at the site the cost is paid and nothing else " +
      "happens"):
    val ready = withBanner(staged(), Banner.PeoplesFavor, None, 3)
    val t = use(ready, BookOfRecords, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(tokensOn(after(t), book), Tokens(0, 1))
    assertEquals(held(after(t), Banner.PeoplesFavor), 3)
    assertEquals(NoteText.said(BookOfRecords, t.events), Vector(
      NoteText.Said("used.none", "No player at the site held a banner.",
        covers = false)))

  test("a banner held away from the site is refused"):
    val t = use(staged(), BookOfRecords, source).toOption.get
    assert(answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.DarkestSecret)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BookOfRecords.id))
    assert(use(ready, BookOfRecords, source).isLeft)
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BookOfRecordsSuite"`
Expected: compilation fails: `BookOfRecords` is not found.

- [ ] **Step 3: Write Book of Records**

Create `src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Book of Records (relic R19), ACTION: place 1 secret on this card and burn
  * 2, then take two favor or two secrets from a banner held by a player whose
  * pawn is at the player's site, the player's own banner included. The
  * People's Favor gives favor and the Darkest Secret gives secrets, up to two
  * of what it holds.
  *
  * The banners are read live, after the cost is paid. With none held at the
  * site the cost stays paid and one line says so. An empty banner is still a
  * legal choice and gives nothing. The take is a `Take`, so a restriction on
  * taking applies to it.
  */
case object BookOfRecords extends PaidAction("relic.book-of-records",
    Cost(secret = 1, secretBurnt = 2)):
  val decisionId: String = "power.book-of-records.banner"
  val Most: Int = 2
  /** "{Red} took {2 favor} from {Blue}'s {People's Favor}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Blue}'s {banner} held nothing to take." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" held nothing to take.")))
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player at the site held a banner.")))
  override def noteKeys: Vector[NoteKey] = Vector(took, empty, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => take(live, player, pending)),
    Note(id, tookNote(_, player, source)))))

  /** The banners held by a player whose pawn is at the actor's site. */
  private def banners(ready: ReadyGame, actor: PlayerId): Vector[Banner] =
    val current = ready.game.current
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      Banner.all.filter(banner => BannerRules.holder(current, banner)
        .exists(holder => current.players.exists(p =>
          p.player == holder && p.pawnSite.contains(site)))))

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    val found = banners(ready, actor)
    if found.isEmpty then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(banner => DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
      heading = Some("Book of Records: take from a banner held at your site"))))

  private def piece(banner: Banner, amount: Int): Piece = banner match
    case Banner.PeoplesFavor => Piece.Favor(amount)
    case Banner.DarkestSecret => Piece.Secrets(amount)

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = banners(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      banner <- found.find(DecisionOptionRef.Banner(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a banner held at the actor's site"))
    yield
      val amount = math.min(Most, BannerRules.resources(ready.game.current,
        banner))
      if amount == 0 then Vector.empty
      else Vector(Take(piece(banner, amount), actor, Location.OnBanner(banner),
        Location.PlayArea(actor)))

  /** What the take moved to the actor, from whose banner. No answer means no
    * banner was held at the site, whose line the `Branch` already wrote. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    banner <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Banner(chosen) => chosen }
    step <- states.previous
    holder <- BannerRules.holder(step._1.game.current, banner)
  yield
    val (amount, unit) = banner match
      case Banner.PeoplesFavor => (NoteSupport.favor(step, actor), NoteUnit.Favor)
      case Banner.DarkestSecret =>
        (NoteSupport.secrets(step, actor), NoteUnit.Secret)
    if amount > 0 then took(card, NoteArg.Player(actor),
      NoteArg.Amount(amount, unit), NoteArg.Player(holder),
      NoteArg.Banner(banner))
    else empty(card, NoteArg.Player(holder), NoteArg.Banner(banner))
```

In `OtherActionPowers.scala`, replace `Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines)` with `Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines, BookOfRecords)`.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BookOfRecordsSuite"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Run the power suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/BookOfRecordsSuite.scala
git commit -m "feat(powers): Book of Records takes from a banner held at your site"
```

---

### Task 4: Barbed Net and its log

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BarbedNetSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Create: `src/test/resources/gamelog/barbed-net.actor.log`, `src/test/resources/gamelog/barbed-net.other.log`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: `OtherActionPowers.powers`; `Peek`; `NoteSupport.relicsGained`; `LogScripts`' `journaled`, `Situation.wake`, `active`, `pawn`, `Step.Arrange`, `withAnswers`; `GameLogPowerLinesSuite`'s `name`, `format`, `texts`.
- Produces: `BarbedNet` with `decisionId = "power.barbed-net.relic"`; `LogScripts.barbedNet`, named `barbed-net`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/BarbedNetSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BarbedNetSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._
  import PaidActionHarness.{secrets, tokensOn, wireRoundTrips}

  private val net = RelicId("R36")
  private val source = DecisionOptionRef.Relic(net)

  /** Relics in the relic deck other than Barbed Net. */
  private def spare(ready: ReadyGame): Vector[RelicId] =
    ready.game.current.commonCards.relicDeck.filterNot(_ == net)

  /** The actor's site holds exactly `relics`, facedown, taken from the relic
    * deck. The relics it held return to the bottom of the relic deck.
    */
  private def withSiteRelics(ready: ReadyGame, relics: Vector[RelicId])
      : ReadyGame =
    val site = home(ready)
    ready.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck =
        c.commonCards.relicDeck.filterNot(relics.contains) ++
          c.map.sites(site).relics.map(_.id)),
      map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(relics = relics.map(relic =>
          RelicState(relic, Orientation.FaceDown, Tokens.empty)))))))

  /** The actor holds Barbed Net faceup with `faceUp` secrets, and the site
    * holds `count` relics. */
  private def staged(count: Int = 2, faceUp: Int = 3): ReadyGame =
    val held = inPhase(withSecrets(withRelic(base, net), actor, faceUp, 0),
      Phase.Act)
    withSiteRelics(held, spare(held).take(count))
  private def atSite(ready: ReadyGame): Vector[RelicId] =
    ready.game.current.map.sites(home(ready)).relics.map(_.id)
  private def knows(ready: ReadyGame, relic: RelicId): Boolean =
    ready.knowledge.siteRelics.getOrElse(actor, Map.empty)
      .valuesIterator.exists(_.contains(relic))
  private def choose(relic: RelicId) = pick(DecisionOptionRef.Relic(relic))

  test("Barbed Net is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(BarbedNet.id).isDefined)

  test("it burns three secrets, peeks at every relic at the site and asks " +
      "which to take"):
    val ready = staged()
    val relics = atSite(ready)
    val t = use(ready, BarbedNet, source).toOption.get
    assert(awaits(t, BarbedNet.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(secrets(after(t)), 0)
    assertEquals(tokensOn(after(t), net), Tokens.empty)
    relics.foreach(relic => assert(knows(after(t), relic), relic.value))
    assertEquals(offered(t, actor),
      Some(relics.map(relic => "relic" -> relic.value)))

  test("the chosen relic moves facedown to the actor, and the others stay"):
    val ready = staged()
    val relics = atSite(ready)
    val (taken, left) = (relics(0), relics(1))
    val t = use(ready, BarbedNet, source).toOption.get
    val done = answer(t, actor, BarbedNet.decisionId, choose(taken)).toOption.get
    assert(player(after(done)).relics.exists(relic =>
      relic.id == taken && relic.orientation == Orientation.FaceDown))
    assertEquals(atSite(after(done)), Vector(left))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(wireRoundTrips(t.events ++ done.events))

  test("it writes its peek, covering the peek lines, then its take"):
    val ready = staged()
    val relics = atSite(ready)
    val (taken, left) = (relics(0), relics(1))
    val t = use(ready, BarbedNet, source).toOption.get
    val done = answer(t, actor, BarbedNet.decisionId, choose(taken)).toOption.get
    val site = home(ready).value
    assertEquals(NoteText.said(BarbedNet, t.events ++ done.events), Vector(
      NoteText.Said("used.peeked", s"${actor.value} peeked at the relics at " +
        s"$site: ${taken.value}, ${left.value}.", covers = true),
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took ${taken.value} facedown from $site.",
        covers = false)))

  test("a site with no relic: the cost is paid and nothing else happens"):
    val ready = staged(count = 0)
    val t = use(ready, BarbedNet, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(secrets(after(t)), 0)
    assertEquals(NoteText.said(BarbedNet, t.events), Vector(NoteText.Said(
      "used.none", s"${home(ready).value} held no relic.", covers = false)))

  test("a relic not at the site is refused"):
    val ready = staged()
    val t = use(ready, BarbedNet, source).toOption.get
    assert(answer(t, actor, BarbedNet.decisionId,
      choose(spare(ready).last)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BarbedNet.id))
    assert(use(ready, BarbedNet, source).isLeft)
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BarbedNetSuite"`
Expected: compilation fails: `BarbedNet` is not found.

- [ ] **Step 3: Write Barbed Net**

Create `src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Barbed Net (relic R36), ACTION: burn 3 secrets, then take a relic from
  * the player's site. The player may keep it facedown; the existing minor
  * action that reveals an owned relic covers turning it up later.
  *
  * Every relic at the site is recorded as a `Peek`, so the player may
  * identify it, and then the player chooses one, as Recover asks. It moves
  * to the player's board facedown, as a recovered relic does. With no relic
  * at the site the cost stays paid and nothing else happens.
  *
  * The relic question separates the peek step from the take step, and a
  * note covers only the step before it. So the peek has its own covering
  * line, `used.peeked`, as Scryer's does, and the take has the `used` line.
  */
case object BarbedNet extends PaidAction("relic.barbed-net",
    Cost(secretBurnt = 3)):
  val decisionId: String = "power.barbed-net.relic"
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
  override def noteKeys: Vector[NoteKey] = Vector(peeked, took, bare)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(here(live, player).toVector.flatMap(
      (site, relics) => relics.map(relic =>
        Peek(player, relic, Location.Site(site)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      (site, relics) <- here(states.now, player)
      if relics.nonEmpty
    yield peeked(card, NoteArg.Player(player), NoteArg.Site(site),
      NoteArg.Cards(relics)), covers = true),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      (site, relics) <- here(states.now, player)
      if relics.isEmpty
    yield bare(card, NoteArg.Site(site))),
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => take(live, player, pending)),
    Note(id, tookNote(_, player, source)))))

  /** The player's pawn site and the relics there, in the site's order. */
  private def here(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, Vector[RelicId])] = for
    site <- PowerAccess.pawnSite(ready, actor)
    state <- ready.game.current.map.sites.get(site)
  yield (site, state.relics.map(_.id))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    here(ready, actor).filter(_._2.nonEmpty).toVector.map((_, relics) =>
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        relics.map(relic => DecisionOption.Relic(DecisionOptionRef.Relic(relic))),
        heading = Some("Barbed Net: take a relic from your site"))))

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = here(ready, actor) match
    case Some((site, relics)) if relics.nonEmpty => for
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
    site <- PowerAccess.pawnSite(states.now, actor)
  yield took(card, NoteArg.Player(actor), NoteArg.Card(relic),
    NoteArg.Site(site))
```

In `OtherActionPowers.scala`, replace `Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines, BookOfRecords)` with `Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines, BookOfRecords, BarbedNet)`.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BarbedNetSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Write the failing log test**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, add `BarbedNet` to the `oathdigital.gameplay.powers.action` import:

```scala
import oathdigital.gameplay.powers.action.{BarbedNet, GamblingHall, Oracle,
  Wolves}
```

After the `oracle` script, add:

```scala
  /** Barbed Net in the actor's play area and a relic from the relic deck at
    * the actor's site, used in Act with three secrets arranged. The relic
    * question parks; the answer takes that relic. */
  def barbedNet(using munit.Location): Script =
    val net = RelicId("R36")
    val (service, _, driver) = journaled("barbed-net")
    val woken = Situation.wake(driver, FirstGameSetupFixture.chronicle,
      FirstGameSetupFixture.orders)
    val actor = active(woken)
    val current = woken.ready.game.current
    val from = current.map.sites.collectFirst {
      case (site, state) if state.relics.exists(_.id == net) =>
        Location.Site(site)
    }.getOrElse(Location.Deck(CardDeck.Relic))
    val target = current.commonCards.relicDeck.find(_ != net).get
    woken.withAnswers {
      case park if park.decisionId == BarbedNet.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Relic(target))
    }.after(Step.Arrange(Vector(
        Move(Piece.Card(net), PositionedLocation(from),
          PositionedLocation(Location.PlayArea(actor)),
          resultingOrientation = Some(Orientation.FaceUp)),
        Move(Piece.Card(target), PositionedLocation(Location.Deck(CardDeck.Relic)),
          PositionedLocation(Location.Site(pawn(woken, actor))),
          resultingOrientation = Some(Orientation.FaceDown)),
        Move(Piece.Secrets(3), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor),
      GameCommand.UsePower(actor, BarbedNet.id, DecisionOptionRef.Relic(net)))
    Script("barbed-net", service, actor)
```

and add it to `named`, after `"oracle" -> (() => oracle)`:

```scala
    "oracle" -> (() => oracle), "barbed-net" -> (() => barbedNet))
```

If `Step.Arrange` refuses a relic move from the relic deck because the deck position is unspecified, give the move `PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top)` and take `target` as the deck's first relic that is not Barbed Net only if the deck is stored top first; check `CommonCardsState` or an existing relic-deck test before choosing.

In `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`, after the Oracle test, add:

```scala
  test("Barbed Net writes its peek and its take as its action's lines, " +
      "naming the relic to its taker alone"):
    val script = barbedNet
    val actor = name(script.actor)
    val other = script.players.find(_ != script.actor).get
    val last = script.history.steps.last.after match
      case OathState.Ready(ready) => ready
      case state => fail(s"expected a ready game, got $state")
    val taken = last.game.current.players.find(_.player == script.actor).get
      .relics.collectFirst { case RelicState(id, Orientation.FaceDown, _) => id }
      .get
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val peeked = all.indexWhere(
        _.startsWith(s"Barbed Net: $actor peeked at the relics at "))
      val took = all.indexWhere(_.startsWith(s"Barbed Net: $actor took "))
      assert(peeked >= 0 && took > peeked, all)
      assert(all(took).contains(" facedown from "), all(took))
      assertEquals(shown(peeked).kind, LogKind.Action)
      assertEquals(shown(took).kind, LogKind.Action)
      assert(!all.exists(_.startsWith("Used Barbed Net")), all)
      assertEquals(all.count(_.contains("peeked at")), 1, all)
      val named = shown(took).spans.exists {
        case LogSpan.Card(id, _) => id == taken.value
        case _ => false
      }
      assertEquals(named, viewer == script.actor, shown(took).spans.toString)
    }
```

- [ ] **Step 6: Run the log test to see the golden check fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: PASS for the new test. If it fails, fix the power or the script, not the assertions.

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: FAIL: `barbed-net.actor.log` and `barbed-net.other.log` do not exist.

- [ ] **Step 7: Write the golden logs and review them**

Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`

Read both new files in full. Check:
- The actor's log reads "Barbed Net: {actor} peeked at the relics at {site}: …", names every relic at the site, then a "chose" line for the relic, then "Barbed Net: {actor} took [relic] facedown from {site}." naming the relic.
- The other seat's log reads the same lines with relic backs instead of names.
- Neither has a "Used Barbed Net" line or a generic "peeked at" line.
- No other golden file changed: `git status --short src/test/resources/gamelog` lists only the two new files.

- [ ] **Step 8: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/BarbedNet.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/test/scala/oathdigital/gameplay/powers/action/BarbedNetSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  src/test/resources/gamelog/barbed-net.actor.log \
  src/test/resources/gamelog/barbed-net.other.log
git commit -m "feat(powers): Barbed Net peeks at your site's relics and takes one facedown"
```

---

### Task 5: Pin the cards, record the slice, run the gates

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Pin the five powers**

In `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`, after the test "catalog batch 2's Vision draw is implemented", add:

```scala

  test("catalog batch 2's actions on others are implemented"):
    Vector("denizen.spoiled-supplies", "denizen.charming-friend",
      "denizen.siege-engines", "relic.book-of-records", "relic.barbed-net")
      .foreach(id => assert(implemented(PowerId(id)), id))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Record the slice in the roadmap**

In `docs/ROADMAP.md`, replace:

```text
done: Oracle, which draws a Vision and plays it through Search's placement.
Slice 4, actions on others, and slice 5, triggers and when-played powers,
remain.
```

with:

```text
done: Oracle, which draws a Vision and plays it through Search's placement.
Slice 4 is done: the actions on others, Spoiled Supplies, Charming Friend,
Siege Engines, Book of Records and Barbed Net. Slice 5, triggers and
when-played powers, remains.
```

If the text around it has changed on `main`, keep its facts and change only the slice 4 sentence.

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 43 (6 in `SpoiledSuppliesSuite`, 9 in `CharmingFriendSuite`, 9 in `SiegeEnginesSuite`, 10 in `BookOfRecordsSuite`, 7 in `BarbedNetSuite`, 1 in `GameLogPowerLinesSuite` and 1 in `PowerImplementationStatusSuite`), plus the tests that run once per named script for `barbed-net`. Count those from the suites that iterate `LogScripts.named` and state the exact expected total in your report. The frontend count equals its baseline.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 4"
```
