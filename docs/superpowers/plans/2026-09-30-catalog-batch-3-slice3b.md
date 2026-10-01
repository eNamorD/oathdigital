# Catalog Batch 3, Slice 3b (Actions on Other Players) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the ACTION powers Quick Exit (58), Dream Thief (70), Second Chance (181), Whispering Leaves (211), Enchantress (96), Armed Mob (53), Honor Guard (251) and Amber Flame (R32).

**Architecture:**

- Every power is a `PaidAction`, the ACTION phase power whose engine pays the cost onto the source card.
- Each is registered in `OtherActionPowers`, which `PhasePowerCatalog` already includes. Task 1 turns its `powers` value into `forCatalog(catalog)`, because Second Chance, Armed Mob and Honor Guard read card suits from the catalog.
- Three seams are used by a power for the first time, each with its own test:
  - a `Give` whose giver is not the active player (Whispering Leaves);
  - a `Burn` from a held banner (Amber Flame);
  - a `Swap` of two facedown advisers between two other players (Dream Thief).
- One refactor inside `gameplay/powers`, which the spec's bar allows: Book of Records' `banners` read (the banners held by a player at the actor's site) stops being private, so Amber Flame shares it.

**Engine changes:** none. No new operation, walker window, decision query kind, option kind, procedure step, `NoteArg` kind, protocol change or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Seams used for the first time", "Log lines", "Slice 3: ACTION powers", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch" and "Slice 3b: actions on other players"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

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
- Log lines, exactly (spec, "Log lines", "Slice 3"). `{Red}` is the acting player, `{Blue}` and `{Green}` other players. Each line's key is given, and "covers" names the generic lines it replaces:
  - Quick Exit, key `used`: "Quick Exit: Placed {Blue} at {site}." Key `used.none`: "Quick Exit: No other pawn was at {site}."
  - Dream Thief, key `used`: "Dream Thief: {Red} swapped {Blue}'s {card} with {Green}'s {card}." Key `used.none`: "Dream Thief: No two facedown advisers could be swapped."
  - Second Chance, key `used`: "Second Chance: Killed {n} {Blue} warband, and {Red} gained {1 warband}." Key `used.killed`, when the player's supply was empty: "Second Chance: Killed {n} {Blue} warband." Key `used.spared`: "Second Chance: {Blue} had no warband to kill." Key `used.none`: "Second Chance: No player had a faceup Order or Discord adviser."
  - Whispering Leaves, key `used`: "Whispering Leaves: {Blue} placed {n favor} on it." Key `used.empty`: "Whispering Leaves: {Blue} had no favor to place."
  - Enchantress, key `used`: "Enchantress: {Red} swapped it for {Blue}'s {card}." Key `used.none`: "Enchantress: No faceup adviser could be swapped."
  - Armed Mob, key `used`: "Armed Mob: {Red} discarded {card} from {Blue}'s advisers." Covers the Discarded line. Key `used.none`: "Armed Mob: No player held the Darkest Secret without the People's Favor." Key `used.empty`: "Armed Mob: {Blue} had no faceup adviser to discard."
  - Honor Guard, key `used`: "Honor Guard: {Red} buried {card} from {Blue}'s advisers." Covers the Buried line. Key `used.none`: "Honor Guard: No adviser could be buried."
  - Amber Flame, key `used`: "Amber Flame: {Red} burned {1 favor} from {Blue}'s {People's Favor}." or "... burned {1 secret} from {Blue}'s {Darkest Secret}." Key `used.empty`: "Amber Flame: {Blue}'s {banner} held nothing to burn." Key `used.none`: "Amber Flame: No player at the site held a banner."
  - `{n}` before "warband" is `NoteArg.Number` with `Plural`, as for Siege Engines.
  - Amber Flame's decision is declared narrated, as Fae Merchant's is (spec, "Slice 3").
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **All-Exile.** Every other player is an enemy.
  - **Acting on yourself.** Second Chance, Whispering Leaves and Armed Mob may target the acting player.
  - **Locked.** A locked card refuses Move, Flip and Swap, so a discard or swap of it is refused. No power filters locked cards itself: the restriction refuses the operation, and the search hides the option. `Bury` still ignores locked.
  - **"X to gain Y".** Second Chance's warband is gained only when a warband was killed.
  - **No target.** A paid power with no legal target pays its cost and does nothing, as Charming Friend does.
  - **Amounts are best effort.** A gain, give or burn resolves to what its source holds.
- Baseline: `main` after slice 3a passes 2388 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in the earlier slices. Every suite below runs through `PhasePowerCatalog.default`, so an unregistered power fails its suite. No card in this slice has a reviewed stub in Scala to retire.
2. **Generic lines, checked in `application/gamelog/DetailLines.scala`.** A pawn `Move`, a `Swap`, a `Give` onto a card, a `Kill` from a board, a `Gain.Warbands` and a `Burn` from a banner write no generic line. `Discard.Denizen` writes the Discarded line and `Bury` the Buried line. So only Armed Mob's and Honor Guard's notes cover (`covers = true`). The other notes keep `covers = false`.
3. **Narrated decisions.** Only Amber Flame's, as the spec lists. The other choices keep their generic "Chose" line, as Charming Friend's, Siege Engines' and Whistle's do. If this is wrong, a "Chose" line repeats what the note says, and adding the decision to `narratedDecisions` removes it.
4. **Two questions, the second built live.** Quick Exit asks for the pawn, then the site. Dream Thief asks for one facedown adviser, then one of another player. Each second question is a `Branch` that reads the first answer, so its options follow from it.
5. **Second Chance's gain runs as its own step** (spec, "Verify at plan time", "Same-bank gains"). A kill on the player's own board returns the warband to the bank the gain takes from. A `Branch` after the answer reads the target's board. With a warband there, it returns the kill, then the gain, then the note. With none, it returns only the `used.spared` note. The note reads the player's warband change in the step before it: a gain that ran gives 1, and an empty supply gives 0 and the `used.killed` line.
6. **Swaps and the Locked rule.** Enchantress's swap and Armed Mob's discard run as one required `BuildOps`, and their decision is `passWhenEmpty`, as Twin Brother's and Bed of Roots' are. The search then hides a locked adviser, and a player offered only locked advisers is asked nothing. Dream Thief's swap is a plain `BuildOps`: a facedown card has no printed restriction.
7. **Options.** Dream Thief offers adviser slots, so no facedown card is named (as Hunger does). Enchantress, Armed Mob and Honor Guard offer faceup denizen advisers by card, since a faceup card discloses nothing (as Bed of Roots does). A faceup Vision is not an adviser of that kind.
8. **Armed Mob's discard** goes to the discard pile of the region after the acting player's, as Horned Mask's and a plan discard do (`CardPlay.nextRegion`).
9. **Whispering Leaves always asks.** The acting player stands at their own site, so there is always a candidate and no `used.none` line.
10. **Amber Flame shares Book of Records' banners read.** `BookOfRecords.banners` stops being private. Its behavior and suite are unchanged.

## File Structure

All paths are under `src/main/scala/oathdigital/gameplay/powers/` or `src/test/scala/oathdigital/gameplay/powers/`.

| File | Responsibility | Task |
|---|---|---|
| `action/OtherActionPowers.scala` (modify) | Registers every power of this slice; becomes `forCatalog` | 1 to 8 |
| `PhasePowerCatalog.scala` (modify) | Calls `OtherActionPowers.forCatalog(catalog)` | 1 |
| `action/QuickExit.scala` (create) | Quick Exit | 1 |
| `action/DreamThief.scala` (create) | Dream Thief | 2 |
| `action/SecondChance.scala` (create) | Second Chance | 3 |
| `action/WhisperingLeaves.scala` (create) | Whispering Leaves | 4 |
| `action/Enchantress.scala` (create) | Enchantress | 5 |
| `action/ArmedMob.scala` (create) | Armed Mob | 6 |
| `action/HonorGuard.scala` (create) | Honor Guard | 7 |
| `action/BookOfRecords.scala` (modify) | `banners` becomes public | 8 |
| `action/AmberFlame.scala` (create) | Amber Flame | 8 |
| `action/<Card>Suite.scala` (create) | One suite per card | 1 to 8 |
| `docs/ROADMAP.md` (modify) | Records the slice | 9 |

Every new suite drives the power through `TargetsFixture` (`use`, `answer`, `after`, `awaits`, `offered`, `pick`, `replayed`, `usableNow`, `parked`), whose rules hold the printed operation restrictions, so the search hides a locked adviser. It stages with `Table` and reads with `Look`. `TargetsFixture.use` acts as `p1`. `Table.start` puts p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant, each with 1 favor, 1 faceup secret and 3 warbands, in p1's Act.

---

### Task 1: Quick Exit, and `OtherActionPowers.forCatalog`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/QuickExit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala:26`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/QuickExitSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite(ready, player): Option[SiteId]`, `PawnMoves.sitesOtherThan(ready, site): Vector[SiteId]`, `PawnMoves.siteChoice(decisionId, owner, sites, heading): Decide`, `PawnMoves.chosenSite(pending, decisionId): Either[OathViolation, SiteId]`, `PawnMoves.relocate(ready, player, to): Either[OathViolation, Vector[CoreOperation]]`, `PowerAnswers.one`, `NoteSupport.answer`.
- Produces: `QuickExit` (`targetDecisionId`, `siteDecisionId`, `placed`, `nobody`); `OtherActionPowers.forCatalog(catalog: ExecutableCatalog): Vector[PhasePower]`, which later tasks extend.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/QuickExitSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class QuickExitSuite extends munit.FunSuite:
  import TargetsFixture._

  private val exit = CatalogNames.denizen("Quick Exit")
  private val source = DecisionOptionRef.Denizen(exit)
  private val home = Table.homeOf(p1)
  private val away = Table.homeOf(p3)

  /** p1's Act holding Quick Exit with `secrets` faceup secrets. p2 stands at
    * p1's site and p3 at its own. */
  private def staged(secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, exit).secrets(p1, secrets)
      .pawn(p2, at = home).ready

  private def target(t: OathTransition, who: PlayerId): OathTransition =
    answer(t, p1, QuickExit.targetDecisionId,
      pick(DecisionOptionRef.Player(who))).toOption.get

  test("it places a secret and offers only the other pawns at the site"):
    val t = use(staged(), QuickExit, source).toOption.get
    assert(awaits(t, QuickExit.targetDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(exit), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it then offers every other site in play"):
    val ready = staged()
    val chosen = target(use(ready, QuickExit, source).toOption.get, p2)
    assert(awaits(chosen, QuickExit.siteDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(offered(chosen, p1), Some(ready.game.current.map.inPlay
      .filter(_ != home).map(site => "site" -> site.value)))

  test("the pawn is placed at the chosen site, and the line says where"):
    val ready = staged()
    val t = use(ready, QuickExit, source).toOption.get
    val chosen = target(t, p2)
    val done = answer(chosen, p1, QuickExit.siteDecisionId,
      pick(DecisionOptionRef.Site(away))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).pawn(p2), away)
    assertEquals(Look(after(done)).pawn(p1), home)
    val events = t.events ++ chosen.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p2.value} at ${away.value}.", covers = false)))

  test("with no other pawn at the site, the cost stays paid and the line " +
      "says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, exit)
      .secrets(p1, 1).ready
    val done = use(ready, QuickExit, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(exit), Tokens(0, 1))
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      "used.none", s"No other pawn was at ${home.value}.", covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == QuickExit.id))
    assert(use(broke, QuickExit, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.QuickExitSuite"`
Expected: compilation fails with "Not found: QuickExit".

- [ ] **Step 3: Create Quick Exit**

Create `src/main/scala/oathdigital/gameplay/powers/action/QuickExit.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Quick Exit (card 58), ACTION: place 1 secret on this card, then place an
  * enemy pawn at your site onto any other site.
  *
  * Every game is all-Exile, so every other player whose pawn stands at the
  * player's site is a candidate, read live after the cost is paid. The player
  * chooses one, then any site in play other than that site. The pawn moves by
  * a plain `Move`, as Whistle's does: it does not travel, so no Travel window
  * runs. With no candidate the cost stays paid and one line says so.
  */
case object QuickExit extends PaidAction("denizen.quick-exit",
    Cost(secret = 1)):
  val targetDecisionId: String = "power.quick-exit.target"
  val siteDecisionId: String = "power.quick-exit.site"
  /** "Placed {Blue} at {site}." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" at "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No other pawn was at {site}." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No other pawn was at "), NotePart.Arg(0),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(placed, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askTarget(live, player, source)),
    Branch((live, pending) => askSite(live, player, pending)),
    BuildOps((live, pending) => place(live, player, pending)),
    Note(id, placedNote(_, source)))))

  /** The other players whose pawn stands at `actor`'s site, in seat order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player
      })

  private def chosen(pending: PendingTree): Option[PlayerId] =
    PowerAnswers.one(pending, targetDecisionId).collect {
      case DecisionOptionRef.Player(target) => target }

  private def askTarget(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector(Note(id, _ => nobodyNote(ready, actor, source)))
      case found => Vector(Decide(targetDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(target => DecisionOption.Player(
          DecisionOptionRef.Player(target))),
          heading = Some("Quick Exit: choose a pawn at your site to place " +
            "elsewhere"))))

  private def nobodyNote(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- PowerAccess.pawnSite(ready, actor)
  yield nobody(card, NoteArg.Site(site))

  /** The sites in play other than the chosen pawn's, once it is chosen. */
  private def askSite(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(pending).toVector.flatMap(target =>
      PowerAccess.pawnSite(ready, target).toVector.map(from =>
        PawnMoves.siteChoice(siteDecisionId, actor,
          PawnMoves.sitesOtherThan(ready, from),
          "Quick Exit: choose the site to place the pawn on")))

  /** No answer means no other pawn stood at the site. */
  private def place(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = chosen(pending) match
    case None => Right(Vector.empty)
    case Some(target) => for
      _ <- Either.cond(targets(ready, actor).contains(target), (),
        OathViolation.InvalidEventOrder(
          s"${target.value} is not a pawn at the actor's site"))
      site <- PawnMoves.chosenSite(pending, siteDecisionId)
      ops <- PawnMoves.relocate(ready, target, site)
    yield ops

  /** Where the chosen pawn stands now. No answer means nobody was at the
    * site, whose line the first `Branch` already wrote. */
  private def placedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, targetDecisionId)
    site <- PowerAccess.pawnSite(states.now, target)
  yield placed(card, NoteArg.Player(target), NoteArg.Site(site))
```

- [ ] **Step 4: Register it, through a catalog-aware `OtherActionPowers`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4 and catalog batch 3's slice
  * 3b, actions on others, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[SelfActionPowers]]. A power that needs the catalog is omitted when
  * its card is absent.
  */
object OtherActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines,
      BookOfRecords, BarbedNet, QuickExit)
```

`catalog` is unused until Task 3. That compiles: this build's `-Wunused` checks imports, privates, locals and implicits, not method parameters.

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, replace:

```scala
      OtherActionPowers.powers ++
```

with:

```scala
      OtherActionPowers.forCatalog(catalog) ++
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.QuickExitSuite oathdigital.gameplay.powers.action.CharmingFriendSuite oathdigital.gameplay.powers.action.BookOfRecordsSuite"`
Expected: Quick Exit's 5 tests pass, and the two existing suites pass unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/QuickExit.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/action/QuickExitSuite.scala
git commit -m "feat(powers): add Quick Exit"
```

---

### Task 2: Dream Thief

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/DreamThief.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/DreamThiefSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`, `PowerAnswers.missing`, `NoteSupport.answer`, `Swap(firstCard, firstLocation, secondCard, secondLocation)`, `DecisionOptionRef.AdviserSlot(owner, slot)` (wire kind `adviser-slot`, id `owner:slot`), `SearchFixture.denizensOf(suit): Vector[DenizenId]` (test).
- Produces: `DreamThief` (`firstDecisionId`, `secondDecisionId`, `swapped`, `nothing`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/DreamThiefSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class DreamThiefSuite extends munit.FunSuite:
  import TargetsFixture._

  private val thief = CatalogNames.denizen("Dream Thief")
  private val source = DecisionOptionRef.Denizen(thief)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val vision = VisionId("vision:vision-of-faith")

  /** p1's Act holding Dream Thief (slot 0) with `favor` favor and a facedown
    * Vision (slot 1). p2 holds plain(0) facedown and p3 plain(1) facedown,
    * each in slot 0. */
  private def staged(favor: Int = 2): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, thief).favor(p1, favor)
      .adviser(p1, vision, facedown = true)
      .adviser(p2, plain(0), facedown = true)
      .adviser(p3, plain(1), facedown = true)

  private def slot(owner: PlayerId, at: Int): DecisionAnswer =
    pick(DecisionOptionRef.AdviserSlot(owner, at))
  private def wire(owner: PlayerId, at: Int): (String, String) =
    "adviser-slot" -> s"${owner.value}:$at"

  /** Uses it on `ready`, then answers `first` and `second`. */
  private def swap(ready: ReadyGame, first: DecisionAnswer,
      second: DecisionAnswer) =
    val t = use(ready, DreamThief, source).toOption.get
    val one = answer(t, p1, DreamThief.firstDecisionId, first).toOption.get
    val done = answer(one, p1, DreamThief.secondDecisionId, second)
      .toOption.get
    (t.events ++ one.events ++ done.events, done)

  test("it places 2 favor and offers every facedown adviser by slot"):
    val t = use(staged().ready, DreamThief, source).toOption.get
    assert(awaits(t, DreamThief.firstDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(thief), Tokens(2, 0))
    assertEquals(offered(t, p1),
      Some(Vector(wire(p1, 1), wire(p2, 0), wire(p3, 0))))

  test("the second question offers only other players' facedown advisers"):
    val t = use(staged().ready, DreamThief, source).toOption.get
    val one = answer(t, p1, DreamThief.firstDecisionId, slot(p2, 0))
      .toOption.get
    assert(awaits(one, DreamThief.secondDecisionId),
      parked.parkedDecision(one.state).toString)
    assertEquals(offered(one, p1), Some(Vector(wire(p1, 1), wire(p3, 0))))

  test("the two cards swap, stay facedown, and each owner still knows the " +
      "card it gave up"):
    val ready = staged().ready
    val (events, done) = swap(ready, slot(p2, 0), slot(p3, 0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(plain(1), Orientation.FaceDown, Tokens.empty)))
    assertEquals(Look(end).player(p3).advisers, Vector[AdviserState](
      DenizenState(plain(0), Orientation.FaceDown, Tokens.empty)))
    assert(end.knowledge.advisers.getOrElse(p2, Vector.empty)
      .contains(plain(0)), end.knowledge.toString)
    assert(end.knowledge.advisers.getOrElse(p3, Vector.empty)
      .contains(plain(1)), end.knowledge.toString)
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("the player's own facedown adviser may be swapped"):
    val (_, done) = swap(staged().ready, slot(p1, 1), slot(p2, 0))
    val end = after(done)
    assertEquals(Look(end).advisers(p1), Vector[CardId](thief, plain(0)))
    assertEquals(Look(end).advisers(p2), Vector[CardId](vision))

  test("its line names both owners and both cards"):
    val (_, done) = swap(staged().ready, slot(p2, 0), slot(p3, 0))
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} swapped ${p2.value}'s " +
        s"${plain(0).value} with ${p3.value}'s ${plain(1).value}.",
        covers = false)))

  test("with fewer than two players holding a facedown adviser, nothing is " +
      "asked and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, thief)
      .favor(p1, 2).adviser(p2, plain(0), facedown = true)
      .adviser(p2, plain(1), facedown = true).ready
    val done = use(ready, DreamThief, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(thief), Tokens(2, 0))
    assertEquals(Look(after(done)).advisers(p2),
      Vector[CardId](plain(0), plain(1)))
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said("used.none",
        "No two facedown advisers could be swapped.", covers = false)))

  test("it is unusable with 1 favor"):
    val broke = staged(favor = 1).ready
    assert(!usableNow(broke).exists(_.power.id == DreamThief.id))
    assert(use(broke, DreamThief, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DreamThiefSuite"`
Expected: compilation fails with "Not found: DreamThief".

- [ ] **Step 3: Create Dream Thief**

Create `src/main/scala/oathdigital/gameplay/powers/action/DreamThief.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Dream Thief (card 70), ACTION: place 2 favor on this card, then swap any
  * two facedown advisers.
  *
  * Two questions, both over adviser slots, so no facedown card is named in
  * an option. The first offers every facedown adviser, denizen or Vision, of
  * every player, the player's own included, in seat then adviser order. The
  * second offers the facedown advisers of every player but the first one's
  * owner. They are asked only when at least two players hold a facedown
  * adviser; otherwise the cost stays paid and one line says so.
  *
  * The swap is one `Swap` between the two play areas, so each card keeps its
  * orientation. Knowledge follows the card: each owner knows the card they
  * receive as their own, and still knows the one they gave up. A facedown
  * card has no printed restriction, so the batch is a plain `BuildOps`.
  */
case object DreamThief extends PaidAction("denizen.dream-thief",
    Cost(favor = 2)):
  val firstDecisionId: String = "power.dream-thief.first"
  val secondDecisionId: String = "power.dream-thief.second"
  /** "{Red} swapped {Blue}'s {card} with {Green}'s {card}." */
  val swapped: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" swapped "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(" with "), NotePart.Arg(3),
    NotePart.Text("'s "), NotePart.Arg(4), NotePart.Text(".")))
  /** "No two facedown advisers could be swapped." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No two facedown advisers could be swapped.")))
  override def noteKeys: Vector[NoteKey] = Vector(swapped, nothing)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askFirst(live, player, source)),
    Branch((live, pending) => askSecond(live, player, pending)),
    BuildOps((live, pending) => swap(live, pending)),
    Note(id, swappedNote(_, player, source)))))

  private final case class Slot(owner: PlayerId, slot: Int, card: CardId):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  private def isFacedown(held: AdviserState): Boolean = held match
    case DenizenState(_, orientation, _) => orientation == Orientation.FaceDown
    case VisionState(_, orientation) => orientation == Orientation.FaceDown

  /** Every facedown adviser, in seat then adviser order. */
  private def facedown(ready: ReadyGame): Vector[Slot] = for
    owner <- ready.game.current.players
    (held, slot) <- owner.advisers.zipWithIndex
    if isFacedown(held)
  yield Slot(owner.player, slot, held.id)

  private def options(found: Vector[Slot]): Vector[DecisionOption] =
    found.map(held => DecisionOption.AdviserSlot(held.ref))

  private def askFirst(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    val found = facedown(ready)
    if found.map(_.owner).distinct.size < 2 then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nothing(_))))
    else Vector(Decide(firstDecisionId, actor, DecisionQuery.ChooseOne(
      options(found),
      heading = Some("Dream Thief: choose a facedown adviser to swap"))))

  private def first(ready: ReadyGame, pending: PendingTree): Option[Slot] =
    PowerAnswers.one(pending, firstDecisionId).flatMap(ref =>
      facedown(ready).find(_.ref == ref))

  private def askSecond(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    first(ready, pending).toVector.map(chosen => Decide(secondDecisionId,
      actor, DecisionQuery.ChooseOne(
        options(facedown(ready).filter(_.owner != chosen.owner)),
        heading = Some("Dream Thief: choose another player's facedown " +
          "adviser to swap it with"))))

  /** No first answer means fewer than two players held a facedown adviser. */
  private def swap(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, firstDecisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        one <- facedown(ready).find(_.ref == ref).toRight(OathViolation
          .InvalidEventOrder(s"${ref.wireId} is not a facedown adviser"))
        answered <- PowerAnswers.one(pending, secondDecisionId)
          .toRight(PowerAnswers.missing(secondDecisionId))
        other <- facedown(ready).find(held => held.ref == answered &&
          held.owner != one.owner).toRight(OathViolation.InvalidEventOrder(
            s"${answered.wireId} is not another player's facedown adviser"))
      yield Vector(Swap(one.card, PositionedLocation(Location.PlayArea(
        one.owner)), other.card, PositionedLocation(Location.PlayArea(
        other.owner))))

  /** The two cards, read where they stood before the swap. No answer means
    * nothing could be swapped, whose line the first `Branch` already wrote. */
  private def swappedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    firstRef <- NoteSupport.answer(states, firstDecisionId)
    secondRef <- NoteSupport.answer(states, secondDecisionId)
    step <- states.previous
    one <- facedown(step._1).find(_.ref == firstRef)
    other <- facedown(step._1).find(_.ref == secondRef)
  yield swapped(card, NoteArg.Player(actor), NoteArg.Player(one.owner),
    NoteArg.Card(one.card), NoteArg.Player(other.owner),
    NoteArg.Card(other.card))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      BookOfRecords, BarbedNet, QuickExit)
```

with:

```scala
      BookOfRecords, BarbedNet, QuickExit, DreamThief)
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DreamThiefSuite"`
Expected: 7 tests pass. If the knowledge assertion fails, `CardKnowledgeMoves.follow` does not see a `Swap`'s moves: stop and report, because the ruling needs each owner to keep knowing the card they gave up.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/DreamThief.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/DreamThiefSuite.scala
git commit -m "feat(powers): add Dream Thief"
```

---

### Task 3: Second Chance

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SecondChance.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SecondChanceSuite.scala`

**Interfaces:**
- Consumes: `CatalogCards.denizen(catalog, power): Option[DenizenId]`, `catalog.suitOf(card): Option[Suit]`, `PlayerFacts.player`, `PlayerFacts.forceKind(ready, player): Either[OathViolation, ForceKind]`, `PowerAnswers.one`, `NoteSupport.warbands(step, player): Int`, `NoteSupport.killedKey(name): NoteKey` ("Killed {n} {player} warband."), `Kill(Piece.Warbands, PositionedLocation)`, `Gain.Warbands(player, kind, amount)`.
- Produces: `SecondChance.forCatalog(catalog): Option[SecondChance]`, `SecondChance.id`, `SecondChance.decisionId`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/SecondChanceSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class SecondChanceSuite extends munit.FunSuite:
  import TargetsFixture._

  private val chance = CatalogNames.denizen("Second Chance")
  private val source = DecisionOptionRef.Denizen(chance)
  private val power = SecondChance.forCatalog(catalog).get
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val discord = SearchFixture.denizensOf(Suit.Discord)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** p1's Act holding Second Chance with `secrets` faceup secrets. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, chance).secrets(p1, secrets)

  private def choose(ready: ReadyGame, target: PlayerId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(target))).toOption.get)

  test("it places a secret and offers only players with a faceup Order or " +
      "Discord adviser"):
    val ready = staged().adviser(p2, order(0)).adviser(p3, hearth(0))
      .adviser(p3, discord(0), facedown = true).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, SecondChance.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(chance), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it kills one of the target's warbands, then the player gains one"):
    val ready = staged().adviser(p2, order(0)).ready
    val (t, done) = choose(ready, p2)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p2), 2)
    assertEquals(Look(after(done)).warbands(p1), 4)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the kill and the gain"):
    val (_, done) = choose(staged().adviser(p2, order(0)).ready, p2)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p2.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("a Discord adviser qualifies, and the player may target themself " +
      "even with an empty supply"):
    val ready = staged().adviser(p1, discord(0)).warbands(p1, 14).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(offered(t, p1), Some(Vector("player" -> p1.value)))
    val done = answer(t, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(p1))).toOption.get
    assertEquals(Look(after(done)).warbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p1.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("with an empty supply the kill still happens, and the line says so"):
    val ready = staged().adviser(p2, order(0)).warbands(p1, 14).ready
    val (_, done) = choose(ready, p2)
    assertEquals(Look(after(done)).warbands(p2), 2)
    assertEquals(Look(after(done)).warbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.killed", s"Killed 1 ${p2.value} warband.", covers = false)))

  test("a target with no warband loses nothing, and nothing is gained"):
    val ready = staged().adviser(p2, order(0)).warbands(p2, 0).ready
    val (_, done) = choose(ready, p2)
    assertEquals(Look(after(done)).warbands(p1), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.spared", s"${p2.value} had no warband to kill.", covers = false)))

  test("with no candidate, the cost stays paid and the line says so"):
    val ready = staged().adviser(p2, hearth(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(chance), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No player had a faceup Order or Discord adviser.",
      covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).adviser(p2, order(0)).ready
    assert(!usableNow(broke).exists(_.power.id == SecondChance.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SecondChanceSuite"`
Expected: compilation fails with "Not found: SecondChance".

- [ ] **Step 3: Create Second Chance**

Create `src/main/scala/oathdigital/gameplay/powers/action/SecondChance.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

/** Second Chance (card 181), ACTION: place 1 secret on this card, then kill
  * one warband on the board of a player who has an Order or Discord adviser,
  * to gain one warband.
  *
  * The candidates are the players with a faceup Order or Discord denizen
  * adviser, the player included, in seat order. With none, the cost stays
  * paid and one line says so. The kill takes one warband from the chosen
  * board. The warband is gained only when one was killed, and in its own
  * step after the kill: a kill on the player's own board returns the warband
  * to the bank the gain takes from. The gain is best effort, so an empty
  * supply gives nothing.
  */
final case class SecondChance private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.second-chance", Cost(secret = 1)):
  import SecondChance._

  override def noteKeys: Vector[NoteKey] =
    Vector(gained, killedOnly, spared, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    Branch((live, pending) => strike(live, player, source, pending)))))

  /** The players with a faceup Order or Discord adviser, in seat order. */
  private def candidates(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.filter(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).exists(Suits.contains)
      case _ => false
    }).map(_.player)

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    candidates(ready) match
      case Vector() =>
        Vector(Note(this.id, _ => PowerSourceRef.of(source).map(nobody(_))))
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p))),
        heading = Some("Second Chance: kill a warband on the board of a " +
          "player with a faceup Order or Discord adviser"))))

  /** Read once the target is chosen: with a warband on its board, the kill,
    * then the gain as its own step, then the line; with none, the line. */
  private def strike(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef, pending: PendingTree): Vector[Operation] =
    PowerAnswers.one(pending, decisionId).toVector.flatMap {
      case DecisionOptionRef.Player(target) =>
        val held = PlayerFacts.player(ready, target)
          .fold(_ => 0, _.board.warbands)
        if held == 0 then Vector(Note(this.id, _ => PowerSourceRef.of(source)
          .map(spared(_, NoteArg.Player(target)))))
        else Vector(
          BuildOps((live, _) => PlayerFacts.forceKind(live, target).map(kind =>
            Vector(Kill(Piece.Warbands(kind, Killed),
              PositionedLocation(Location.PlayArea(target)))))),
          BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
            Vector(Gain.Warbands(actor, kind, Gained)))),
          Note(this.id, gainNote(_, actor, target, source)))
      case _ => Vector.empty
    }

  /** The player's warband change in the step before the note: the gain's,
    * or nothing when the empty supply skipped it. */
  private def gainNote(states: NoteStates, actor: PlayerId, target: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map { card =>
      val got = states.previous.fold(0)(NoteSupport.warbands(_, actor))
      if got > 0 then gained(card, NoteArg.Number(Killed),
        NoteArg.Player(target), NoteArg.Player(actor),
        NoteArg.Amount(got, NoteUnit.Warband))
      else killedOnly(card, NoteArg.Number(Killed), NoteArg.Player(target))
    }

object SecondChance:
  val id: PowerId = PowerId("denizen.second-chance")
  val decisionId: String = "power.second-chance.target"
  val Killed: Int = 1
  val Gained: Int = 1
  private val Suits: Set[Suit] = Set(Suit.Order, Suit.Discord)
  /** "Killed {n} {Blue} warband, and {Red} gained {1 warband}." */
  val gained: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband, and ", " warbands, and "), NotePart.Arg(2),
    NotePart.Text(" gained "), NotePart.Arg(3), NotePart.Text(".")))
  /** "Killed {n} {Blue} warband." */
  val killedOnly: NoteKey = NoteSupport.killedKey("used.killed")
  /** "{Blue} had no warband to kill." */
  val spared: NoteKey = NoteKey("used.spared", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  /** "No player had a faceup Order or Discord adviser." */
  val nobody: NoteKey = NoteKey("used.none", Vector(NotePart.Text(
    "No player had a faceup Order or Discord adviser.")))

  def forCatalog(catalog: ExecutableCatalog): Option[SecondChance] =
    CatalogCards.denizen(catalog, id).map(_ => new SecondChance(catalog))
```

The class refers to its own power id as `this.id`, as `BedOfRoots` does, because `import SecondChance._` brings the companion's `id` into scope too.

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace the `forCatalog` method with:

```scala
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines,
      BookOfRecords, BarbedNet, QuickExit, DreamThief) ++
      SecondChance.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SecondChanceSuite"`
Expected: 8 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SecondChance.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/SecondChanceSuite.scala
git commit -m "feat(powers): add Second Chance"
```

---

### Task 4: Whispering Leaves

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/WhisperingLeaves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WhisperingLeavesSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite`, `PlayerFacts.player`, `PowerAnswers.one`, `NoteSupport.answer`, `NoteSupport.favor(step, player): Int`, `Give(piece, giver, from, to)` (its `from` must belong to `giver`).
- Produces: `WhisperingLeaves` (`decisionId`, `Placed`, `placed`, `broke`).

The `Give` here is the first one whose giver is not the active player (spec, "Seams used for the first time"). The pipeline checks only that the source belongs to the giver, so it is expected to pass. If the suite shows the pipeline refusing it, replace the `Give` with `Move(Piece.Favor(amount), PositionedLocation(Location.PlayArea(target)), PositionedLocation(Location.OnCard(leaves)))`, as the spec allows, and say so in the commit message.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/WhisperingLeavesSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class WhisperingLeavesSuite extends munit.FunSuite:
  import TargetsFixture._

  private val leaves = CatalogNames.denizen("Whispering Leaves")
  private val source = DecisionOptionRef.Denizen(leaves)

  /** p1's Act holding Whispering Leaves with `secrets` faceup secrets. p2
    * stands at p1's site with `favor` favor. */
  private def staged(favor: Int = 3, secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, leaves).secrets(p1, secrets)
      .pawn(p2, at = Table.homeOf(p1)).favor(p2, favor)

  private def choose(ready: ReadyGame, target: PlayerId) =
    val t = use(ready, WhisperingLeaves, source).toOption.get
    (t, answer(t, p1, WhisperingLeaves.decisionId,
      pick(DecisionOptionRef.Player(target))).toOption.get)

  test("it places a secret and offers every player at the site, the player " +
      "included"):
    val t = use(staged().ready, WhisperingLeaves, source).toOption.get
    assert(awaits(t, WhisperingLeaves.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(leaves), Tokens(0, 1))
    assertEquals(offered(t, p1),
      Some(Vector("player" -> p1.value, "player" -> p2.value)))

  test("the chosen player gives 2 favor onto Whispering Leaves"):
    val ready = staged().ready
    val (t, done) = choose(ready, p2)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).favor(p2), 1)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(2, 1))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the giver and the favor placed"):
    val (_, done) = choose(staged().ready, p2)
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p2.value} placed 2 favor on it.",
        covers = false)))

  test("a player with 1 favor gives it"):
    val (_, done) = choose(staged(favor = 1).ready, p2)
    assertEquals(Look(after(done)).favor(p2), 0)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(1, 1))
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p2.value} placed 1 favor on it.",
        covers = false)))

  test("the player may choose themself"):
    val (_, done) = choose(staged().favor(p1, 2).ready, p1)
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(Look(after(done)).favor(p2), 3)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(2, 1))

  test("a player with no favor gives nothing, and the line says so"):
    val (_, done) = choose(staged(favor = 0).ready, p2)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(0, 1))
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said("used.empty", s"${p2.value} had no favor to place.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == WhisperingLeaves.id))
    assert(use(broke, WhisperingLeaves, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WhisperingLeavesSuite"`
Expected: compilation fails with "Not found: WhisperingLeaves".

- [ ] **Step 3: Create Whispering Leaves**

Create `src/main/scala/oathdigital/gameplay/powers/action/WhisperingLeaves.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Whispering Leaves (card 211, adviser-only), ACTION: place 1 secret on this
  * card, then choose a player whose pawn is at your site. They must place 2
  * favor on this card.
  *
  * The candidates are every player at the player's site, the player
  * included, in seat order, so the question is always asked. Pawns do not
  * move while the cost is paid, so the decision is a plain `Decide`. The
  * chosen player gives 2 favor from their board, or all they have, with a
  * `Give` onto Whispering Leaves; nothing is asked of them. The favor stays
  * on the card until Rest returns it to the Beast bank, as any favor on a
  * card does.
  */
case object WhisperingLeaves extends PaidAction("denizen.whispering-leaves",
    Cost(secret = 1)):
  val decisionId: String = "power.whispering-leaves.target"
  val Placed: Int = 2
  /** "{Blue} placed {n favor} on it." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on it.")))
  /** "{Blue} had no favor to place." */
  val broke: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to place.")))
  override def noteKeys: Vector[NoteKey] = Vector(placed, broke)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(leaves) => Right(Sequence(Vector[Operation](
      Decide(decisionId, player, DecisionQuery.ChooseOne(
        targets(ready, player).map(target => DecisionOption.Player(
          DecisionOptionRef.Player(target))),
        heading = Some("Whispering Leaves: choose a player at your site to " +
          "place 2 favor on it"))),
      BuildOps((live, pending) => give(live, player, leaves, pending)),
      Note(id, placedNote(_, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  /** Every player whose pawn is at `actor`'s site, in seat order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(_.pawnSite.contains(site))
        .map(_.player))

  private def give(ready: ReadyGame, actor: PlayerId, leaves: DenizenId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- targets(ready, actor).find(DecisionOptionRef.Player(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a player at the actor's site"))
      state <- PlayerFacts.player(ready, target)
    yield
      val amount = math.min(Placed, state.board.favor)
      if amount == 0 then Vector.empty
      else Vector(Give(Piece.Favor(amount), target, Location.PlayArea(target),
        Location.OnCard(leaves)))

  /** The favor the chosen player lost in the step before the note. With
    * nothing to give no step ran, and the step before changed no favor. */
  private def placedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, decisionId)
  yield
    val amount = -states.previous.fold(0)(NoteSupport.favor(_, target))
    if amount > 0 then placed(card, NoteArg.Player(target),
      NoteArg.Amount(amount, NoteUnit.Favor))
    else broke(card, NoteArg.Player(target))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      BookOfRecords, BarbedNet, QuickExit, DreamThief) ++
```

with:

```scala
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves) ++
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WhisperingLeavesSuite"`
Expected: 7 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/WhisperingLeaves.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/WhisperingLeavesSuite.scala
git commit -m "feat(powers): add Whispering Leaves"
```

---

### Task 5: Enchantress

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Enchantress.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/EnchantressSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`, `NoteSupport.answer`, `Swap`, `Decide(..., passWhenEmpty = true)`, `BuildOps(..., required = true)`, `SearchFixture.denizensOf` (test). Insomnia (card 97) is a locked Discord denizen.
- Produces: `Enchantress` (`decisionId`, `swapped`, `nothing`).

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/EnchantressSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class EnchantressSuite extends munit.FunSuite:
  import TargetsFixture._

  private val enchantress = CatalogNames.denizen("Enchantress")
  private val source = DecisionOptionRef.Denizen(enchantress)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act holding Enchantress with `secrets` faceup secrets. p2 holds
    * plain(0) faceup with 1 favor and 2 secrets on it. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, secrets).adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 2)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, Enchantress, source).toOption.get
    (t, answer(t, p1, Enchantress.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a secret and offers other players' faceup advisers, not " +
      "its own, facedown or locked ones"):
    val ready = staged().adviser(p1, plain(1))
      .adviser(p2, plain(2), facedown = true).adviser(p3, insomnia).ready
    val t = use(ready, Enchantress, source).toOption.get
    assert(awaits(t, Enchantress.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the two cards swap faceup, each with its favor and secrets"):
    val ready = staged().ready
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p1).advisers, Vector[AdviserState](
      DenizenState(plain(0), Orientation.FaceUp, Tokens(1, 2))))
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(enchantress, Orientation.FaceUp, Tokens(0, 1))))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card taken and its former owner"):
    val (_, done) = choose(staged().ready, plain(0))
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} swapped it for ${p2.value}'s ${plain(0).value}.",
        covers = false)))

  test("offered only a locked adviser, it asks nothing and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, 1).adviser(p2, insomnia).ready
    val done = use(ready, Enchantress, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(Look(after(done)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said("used.none", "No faceup adviser could be swapped.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == Enchantress.id))
    assert(use(broke, Enchantress, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.EnchantressSuite"`
Expected: compilation fails with "Not found: Enchantress".

- [ ] **Step 3: Create Enchantress**

Create `src/main/scala/oathdigital/gameplay/powers/action/Enchantress.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Enchantress (card 96, adviser-only), ACTION: place 1 secret on this card,
  * then swap it with any faceup adviser.
  *
  * The candidates are the faceup denizen advisers of every other player, in
  * seat then adviser order, read live after the cost is paid. The swap is one
  * `Swap` between the two play areas: both cards stay faceup and carry their
  * favor and secrets, Enchantress' new secret included. It runs as a required
  * batch, so the Locked restriction, which refuses swapping a locked card,
  * hides a locked adviser from the choice. The question passes when nothing
  * is left. With no candidate the cost stays paid and one line says so.
  */
case object Enchantress extends PaidAction("denizen.enchantress",
    Cost(secret = 1)):
  val decisionId: String = "power.enchantress.adviser"
  /** "{Red} swapped it for {Blue}'s {card}." */
  val swapped: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it for "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No faceup adviser could be swapped." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No faceup adviser could be swapped.")))
  override def noteKeys: Vector[NoteKey] = Vector(swapped, nothing)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => Right(Sequence(Vector[Operation](
      Branch((live, _) => ask(live, player)),
      BuildOps((live, pending) => swap(live, player, card, pending),
        required = true),
      Note(id, swappedNote(_, player, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  /** Every other player's faceup denizen advisers. */
  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    for
      other <- ready.game.current.players if other.player != actor
      card <- other.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id }
    yield Candidate(other.player, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(c => DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))),
        heading = Some("Enchantress: swap it with another player's faceup " +
          "adviser")), passWhenEmpty = true))

  /** No answer means no adviser could be swapped. */
  private def swap(ready: ReadyGame, actor: PlayerId, enchantress: DenizenId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => candidates(ready, actor)
        .find(c => DecisionOptionRef.Denizen(c.card) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a faceup adviser Enchantress can swap with"))
        .map(chosen => Vector(Swap(enchantress,
          PositionedLocation(Location.PlayArea(actor)), chosen.card,
          PositionedLocation(Location.PlayArea(chosen.owner)))))

  /** The card taken and its owner, read where it stood before the swap. */
  private def swappedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(target) <-
        NoteSupport.answer(states, decisionId)
      step <- states.previous
      chosen <- candidates(step._1, actor).find(_.card == target)
    yield swapped(card, NoteArg.Player(actor), NoteArg.Player(chosen.owner),
      NoteArg.Card(target))).getOrElse(nothing(card)))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves) ++
```

with:

```scala
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves,
      Enchantress) ++
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.EnchantressSuite"`
Expected: 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Enchantress.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/EnchantressSuite.scala
git commit -m "feat(powers): add Enchantress"
```

---

### Task 6: Armed Mob

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/ArmedMobSuite.scala`

**Interfaces:**
- Consumes: `BannerRules.holder(current, banner): Option[PlayerId]`, `CardPlay.nextRegion(region): Region`, `PowerAccess.pawnSite`, `CatalogCards.denizen`, `catalog.suitOf`, `PlayerFacts.player`, `PowerAnswers.one`, `NoteSupport.answer`, `Discard.Denizen(card, from, to, suit, favor, secrets, actingPlayer, required)`, `OathViolation.UnknownWorldCard(card)`, `OathViolation.PawnSiteMissing(player)`.
- Produces: `ArmedMob.forCatalog(catalog): Option[ArmedMob]`, `ArmedMob.id`, `ArmedMob.decisionId`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/ArmedMobSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class ArmedMobSuite extends munit.FunSuite:
  import TargetsFixture._

  private val mob = CatalogNames.denizen("Armed Mob")
  private val source = DecisionOptionRef.Denizen(mob)
  private val power = ArmedMob.forCatalog(catalog).get
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act beside a site Armed Mob, with `favor` favor. Armed Mob is
    * site-only; it stands at p1's pawn site. p2 holds the Darkest Secret. */
  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).denizen(mob, at = Table.homeOf(p1))
      .favor(p1, favor).darkestSecret(Some(p2), 1)

  private def discarded(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    val region = current.map.regionOf(Table.homeOf(p1)).map(CardPlay.nextRegion)
    current.commonCards.discard(region.get)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, ArmedMob.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a favor and offers the Darkest Secret holder's faceup " +
      "advisers, not facedown or locked ones"):
    val ready = staged().adviser(p2, plain(0))
      .adviser(p2, plain(1), facedown = true).adviser(p2, insomnia).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, ArmedMob.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(mob), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the adviser is discarded to the next region's pile with its returns"):
    val ready = staged().adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1).ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(discarded(end).lastOption, Some(plain(0)))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card and its owner, covering the Discarded line"):
    val (_, done) = choose(staged().adviser(p2, plain(0)).ready, plain(0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${plain(0).value} from ${p2.value}'s advisers.",
      covers = true)))

  test("the player may be the target"):
    val ready = staged().darkestSecret(Some(p1), 1).adviser(p1, plain(0))
      .ready
    val (_, done) = choose(ready, plain(0))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("a holder of both banners is no target, and the line says so"):
    val ready = staged().peoplesFavor(Some(p2), 1).adviser(p2, plain(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none",
      "No player held the Darkest Secret without the People's Favor.",
      covers = true)))

  test("a target with only a locked adviser keeps it, and the line says so"):
    val ready = staged().adviser(p2, insomnia).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.empty", s"${p2.value} had no faceup adviser to discard.",
      covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).adviser(p2, plain(0)).ready
    assert(!usableNow(broke).exists(_.power.id == ArmedMob.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArmedMobSuite"`
Expected: compilation fails with "Not found: ArmedMob".

- [ ] **Step 3: Create Armed Mob**

Create `src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.{BannerRules, CardPlay}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

/** Armed Mob (card 53, site-only), ACTION: place 1 favor on this card, then
  * discard a faceup adviser from a player who holds the Darkest Secret but
  * not the People's Favor.
  *
  * The target is the Darkest Secret's holder when that player does not also
  * hold the People's Favor; it may be the player. The candidates are the
  * target's faceup denizen advisers, in adviser order. The chosen card gets
  * the standard discard: to the discard pile of the region after the
  * player's, its favor to its suit's bank and its secrets to the player
  * facedown. The discard is a required batch, so the Locked restriction
  * hides a locked adviser, and the question passes when nothing is left.
  * With no target, or nothing to discard, the cost stays paid and one line
  * says so.
  *
  * Its line covers the generic Discarded line.
  */
final case class ArmedMob private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.armed-mob", Cost(favor = 1)):
  import ArmedMob._

  override def noteKeys: Vector[NoteKey] = Vector(discarded, nobody, bare)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(card => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(card.id))),
        heading = Some("Armed Mob: discard a faceup adviser of the Darkest " +
          "Secret's holder")), passWhenEmpty = true))

  /** No answer means there was no target or nothing it could lose. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        owner <- target(ready).toRight(OathViolation.InvalidEventOrder(
          "no player is an Armed Mob target"))
        chosen <- candidates(ready)
          .find(card => DecisionOptionRef.Denizen(card.id) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not an adviser Armed Mob can discard"))
        suit <- catalog.suitOf(chosen.id)
          .toRight(OathViolation.UnknownWorldCard(chosen.id))
        region <- PowerAccess.pawnSite(ready, actor)
          .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
          .toRight(OathViolation.PawnSiteMissing(actor))
      yield Vector[CoreOperation](Discard.Denizen(chosen.id,
        PositionedLocation(Location.PlayArea(owner)), region, suit,
        chosen.tokens.favor, chosen.tokens.secrets, actor, required = true))

  /** The card discarded and its owner; with no answer, whether there was a
    * target. */
  private def discardedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Denizen(chosen)) => for
          step <- states.previous
          owner <- target(step._1)
        yield discarded(card, NoteArg.Player(actor), NoteArg.Card(chosen),
          NoteArg.Player(owner))
        case _ => Some(target(states.now).fold(nobody(card))(owner =>
          bare(card, NoteArg.Player(owner)))))

object ArmedMob:
  val id: PowerId = PowerId("denizen.armed-mob")
  val decisionId: String = "power.armed-mob.adviser"
  /** "{Red} discarded {card} from {Blue}'s advisers." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No player held the Darkest Secret without the People's Favor." */
  val nobody: NoteKey = NoteKey("used.none", Vector(NotePart.Text(
    "No player held the Darkest Secret without the People's Favor.")))
  /** "{Blue} had no faceup adviser to discard." */
  val bare: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no faceup adviser to discard.")))

  /** The Darkest Secret's holder, unless they also hold the People's Favor. */
  def target(ready: ReadyGame): Option[PlayerId] =
    val current = ready.game.current
    BannerRules.holder(current, Banner.DarkestSecret).filterNot(holder =>
      BannerRules.holder(current, Banner.PeoplesFavor).contains(holder))

  /** The target's faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame): Vector[DenizenState] =
    target(ready).toVector.flatMap(owner => PlayerFacts.player(ready, owner)
      .toOption.toVector.flatMap(_.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card }))

  def forCatalog(catalog: ExecutableCatalog): Option[ArmedMob] =
    CatalogCards.denizen(catalog, id).map(_ => new ArmedMob(catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      SecondChance.forCatalog(catalog).toVector
```

with:

```scala
      SecondChance.forCatalog(catalog).toVector ++
      ArmedMob.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ArmedMobSuite"`
Expected: 7 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/ArmedMob.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/ArmedMobSuite.scala
git commit -m "feat(powers): add Armed Mob"
```

---

### Task 7: Honor Guard

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/HonorGuard.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/HonorGuardSuite.scala`

**Interfaces:**
- Consumes: `PowerAccess.pawnSite`, `CatalogCards.denizen`, `catalog.suitOf`, `PowerAnswers.one`, `NoteSupport.answer`, `Bury.standard(card, from, suit, favor, secrets, actingPlayer): Vector[CoreOperation]`.
- Produces: `HonorGuard.forCatalog(catalog): Option[HonorGuard]`, `HonorGuard.id`, `HonorGuard.decisionId`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/HonorGuardSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class HonorGuardSuite extends munit.FunSuite:
  import TargetsFixture._

  private val guard = CatalogNames.denizen("Honor Guard")
  private val source = DecisionOptionRef.Denizen(guard)
  private val power = HonorGuard.forCatalog(catalog).get
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val home = Table.homeOf(p1)

  /** p1's Act holding Honor Guard with `favor` favor. p2 stands at p1's site
    * holding plain(0) faceup with 1 favor and 1 secret on it. */
  private def staged(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, guard).favor(p1, favor)
      .pawn(p2, at = home).adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, HonorGuard.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places 2 favor, burns 1, and offers the faceup advisers of " +
      "players at the site with no faceup Order adviser"):
    val ready = staged().adviser(p1, plain(1))
      .adviser(p2, plain(2), facedown = true).pawn(p3, at = home)
      .adviser(p3, order(0)).adviser(p3, plain(3)).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, HonorGuard.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(guard), Tokens(2, 0))
    assertEquals(Look(after(t)).favor(p1), 0)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the adviser is buried with its returns"):
    val ready = staged().ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(end.game.current.commonCards.worldDeck.last, plain(0))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card and its owner, covering the Buried line"):
    val (_, done) = choose(staged().ready, plain(0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} buried ${plain(0).value} from ${p2.value}'s advisers.",
      covers = true)))

  test("a locked adviser may be buried"):
    val (_, done) = choose(staged().adviser(p2, insomnia).ready, insomnia)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))

  test("with nobody else at the site, the cost stays paid and the line says " +
      "so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, guard)
      .favor(p1, 3).adviser(p2, plain(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No adviser could be buried.", covers = true)))

  test("it is unusable with 2 favor"):
    val broke = staged(favor = 2).ready
    assert(!usableNow(broke).exists(_.power.id == HonorGuard.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.HonorGuardSuite"`
Expected: compilation fails with "Not found: HonorGuard".

- [ ] **Step 3: Create Honor Guard**

Create `src/main/scala/oathdigital/gameplay/powers/action/HonorGuard.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PowerAnswers}
import oathdigital.model._

/** Honor Guard (card 251, adviser-only), ACTION: place 2 favor on this card
  * and burn 1, then choose a player with no Order advisers whose pawn is at
  * your site, and bury a faceup adviser they have.
  *
  * One question, as Hunger asks: the faceup denizen advisers of every player
  * at the player's site who has no faceup Order adviser, in seat then adviser
  * order. The user holds Honor Guard, an Order adviser, so is never a
  * candidate. `Bury` ignores locked, so a locked adviser is a candidate. The
  * burial uses the standard returns: favor to the card's suit bank, secrets
  * to the player facedown. With no candidate the cost stays paid and one line
  * says so.
  *
  * Its line covers the generic Buried line.
  */
final case class HonorGuard private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.honor-guard", Cost(favor = 2, favorBurnt = 1)):
  import HonorGuard._

  override def noteKeys: Vector[NoteKey] = Vector(buried, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)),
    Note(this.id, buriedNote(_, player, source), covers = true))))

  private final case class Candidate(owner: PlayerId, card: DenizenState)

  private def ordered(owner: PlayerState): Boolean = owner.advisers.exists {
    case DenizenState(card, Orientation.FaceUp, _) =>
      catalog.suitOf(card).contains(Suit.Order)
    case _ => false
  }

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    val site = PowerAccess.pawnSite(ready, actor)
    for
      owner <- ready.game.current.players
      if site.nonEmpty && owner.pawnSite == site && !ordered(owner)
      card <- owner.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card }
    yield Candidate(owner.player, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(c => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(c.card.id))),
        heading = Some("Honor Guard: bury a faceup adviser of a player at " +
          "your site with no Order adviser"))))

  /** No answer means no adviser could be buried. */
  private def bury(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        chosen <- candidates(ready, actor)
          .find(c => DecisionOptionRef.Denizen(c.card.id) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not an adviser Honor Guard can bury"))
        suit = catalog.suitOf(chosen.card.id)
        _ <- Either.cond(chosen.card.tokens.favor == 0 || suit.isDefined, (),
          OathViolation.InvalidEventOrder(
            s"no suit is known for ${chosen.card.id.value}"))
      yield Bury.standard(BuryableCard.Denizen(chosen.card.id),
        PositionedLocation(Location.PlayArea(chosen.owner)), suit,
        chosen.card.tokens.favor, chosen.card.tokens.secrets, actor)

  /** The chosen adviser, read where it stood before the bury step. */
  private def buriedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case None => Some(spared(card))
        case Some(ref) => for
          step <- states.previous
          chosen <- candidates(step._1, actor)
            .find(c => DecisionOptionRef.Denizen(c.card.id) == ref)
        yield buried(card, NoteArg.Player(actor),
          NoteArg.Card(chosen.card.id), NoteArg.Player(chosen.owner)))

object HonorGuard:
  val id: PowerId = PowerId("denizen.honor-guard")
  val decisionId: String = "power.honor-guard.adviser"
  /** "{Red} buried {card} from {Blue}'s advisers." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No adviser could be buried." */
  val spared: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No adviser could be buried.")))

  def forCatalog(catalog: ExecutableCatalog): Option[HonorGuard] =
    CatalogCards.denizen(catalog, id).map(_ => new HonorGuard(catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      ArmedMob.forCatalog(catalog).toVector
```

with:

```scala
      ArmedMob.forCatalog(catalog).toVector ++
      HonorGuard.forCatalog(catalog).toVector
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.HonorGuardSuite"`
Expected: 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/HonorGuard.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/HonorGuardSuite.scala
git commit -m "feat(powers): add Honor Guard"
```

---

### Task 8: Amber Flame

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala:42-43`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/AmberFlame.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/AmberFlameSuite.scala`

**Interfaces:**
- Consumes: `BannerRules.holder`, `BannerRules.resources(current, banner): Int`, `PowerAnswers.one`, `NoteSupport.answer`, `Burn.favor(amount, from)`, `Burn.secrets(amount, from)`, `Banner.key` (test), `Table.peoplesFavor(holder, favor)`, `Table.darkestSecret(holder, secrets)` (test).
- Produces: `BookOfRecords.banners(ready: ReadyGame, actor: PlayerId): Vector[Banner]` (now public); `AmberFlame` (`decisionId`, `Burned`, `burned`, `empty`, `nobody`).

The burn from a held banner is the first `Burn` whose source is a banner (spec, "Seams used for the first time"). A burn from an unheld banner belongs to Charlatan and Riots, in later slices.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/AmberFlameSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class AmberFlameSuite extends munit.FunSuite:
  import TargetsFixture._

  private val flame = CatalogNames.relic("Amber Flame")
  private val source = DecisionOptionRef.Relic(flame)

  /** p1's Act holding Amber Flame faceup with `secrets` faceup secrets. p2
    * stands at p1's site holding the People's Favor with `favor`; p3 stands
    * at its own site holding the Darkest Secret with 2, so it is never
    * offered. */
  private def staged(favor: Int = 2, secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).relic(p1, flame).secrets(p1, secrets)
      .pawn(p2, at = Table.homeOf(p1)).peoplesFavor(Some(p2), favor)
      .darkestSecret(Some(p3), 2)

  private def banner(ready: ReadyGame, which: Banner): Int = which match
    case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.favor
    case Banner.DarkestSecret =>
      ready.game.current.banners.darkestSecret.secrets
  private def offer(which: Banner): (String, String) = "banner" -> which.key

  private def burn(ready: ReadyGame, which: Banner) =
    val t = use(ready, AmberFlame, source).toOption.get
    (t, answer(t, p1, AmberFlame.decisionId,
      pick(DecisionOptionRef.Banner(which))).toOption.get)

  test("it places a secret and offers the banners held at the site"):
    val t = use(staged().ready, AmberFlame, source).toOption.get
    assert(awaits(t, AmberFlame.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(flame), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector(offer(Banner.PeoplesFavor))))

  test("the People's Favor loses one favor, and the line says whose"):
    val ready = staged().ready
    val (t, done) = burn(ready, Banner.PeoplesFavor)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(banner(after(done), Banner.PeoplesFavor), 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} burned 1 favor from " +
        s"${p2.value}'s People's Favor.", covers = false)))

  test("the player's own Darkest Secret may be chosen, and loses a secret"):
    val ready = staged().darkestSecret(Some(p1), 2).ready
    val t = use(ready, AmberFlame, source).toOption.get
    assertEquals(offered(t, p1), Some(Vector(offer(Banner.PeoplesFavor),
      offer(Banner.DarkestSecret))))
    val done = answer(t, p1, AmberFlame.decisionId,
      pick(DecisionOptionRef.Banner(Banner.DarkestSecret))).toOption.get
    assertEquals(banner(after(done), Banner.DarkestSecret), 1)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} burned 1 secret from " +
        s"${p1.value}'s Darkest Secret.", covers = false)))

  test("an empty banner burns nothing, and the line says so"):
    val (_, done) = burn(staged(favor = 0).ready, Banner.PeoplesFavor)
    assertEquals(banner(after(done), Banner.PeoplesFavor), 0)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said("used.empty",
        s"${p2.value}'s People's Favor held nothing to burn.",
        covers = false)))

  test("with no banner held at the site, the cost stays paid"):
    val ready = staged().peoplesFavor(None, 2).ready
    val done = use(ready, AmberFlame, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(flame), Tokens(0, 1))
    assertEquals(banner(after(done), Banner.PeoplesFavor), 2)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said("used.none", "No player at the site held a banner.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == AmberFlame.id))
    assert(use(broke, AmberFlame, source).isLeft)
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AmberFlameSuite"`
Expected: compilation fails with "Not found: AmberFlame".

- [ ] **Step 3: Share Book of Records' banners read**

In `src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala`, replace:

```scala
  /** The banners held by a player whose pawn is at the actor's site. */
  private def banners(ready: ReadyGame, actor: PlayerId): Vector[Banner] =
```

with:

```scala
  /** The banners held by a player whose pawn is at the actor's site, the
    * actor included. Amber Flame reads the same banners. */
  def banners(ready: ReadyGame, actor: PlayerId): Vector[Banner] =
```

- [ ] **Step 4: Create Amber Flame**

Create `src/main/scala/oathdigital/gameplay/powers/action/AmberFlame.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Amber Flame (relic R32), ACTION: place 1 secret on this relic, then burn 1
  * favor or 1 secret from a banner held by a player whose pawn is at your
  * site.
  *
  * The banners are Book of Records' read, taken live after the cost is paid:
  * each one held by a player at the player's site, the player's own
  * included. The People's Favor burns a favor and the Darkest Secret a
  * secret. An empty banner is still a legal choice and burns nothing. With
  * no banner held there, the cost stays paid and one line says so.
  */
case object AmberFlame extends PaidAction("relic.amber-flame",
    Cost(secret = 1)):
  val decisionId: String = "power.amber-flame.banner"
  val Burned: Int = 1
  /** "{Red} burned {1 favor} from {Blue}'s {People's Favor}." */
  val burned: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Blue}'s {banner} held nothing to burn." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" held nothing to burn.")))
  /** "No player at the site held a banner." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player at the site held a banner.")))
  override def noteKeys: Vector[NoteKey] = Vector(burned, empty, nobody)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => burn(live, player, pending)),
    Note(id, burnedNote(_, player, source)))))

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    BookOfRecords.banners(ready, actor) match
      case Vector() =>
        Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(banner => DecisionOption.Banner(
          DecisionOptionRef.Banner(banner))),
        heading = Some("Amber Flame: burn from a banner held at your site"))))

  private def burn(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = BookOfRecords.banners(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      banner <- found.find(DecisionOptionRef.Banner(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a banner held at the actor's site"))
    yield
      val from = PositionedLocation(Location.OnBanner(banner))
      val burnt: CoreOperation = banner match
        case Banner.PeoplesFavor => Burn.favor(Burned, from)
        case Banner.DarkestSecret => Burn.secrets(Burned, from)
      if BannerRules.resources(ready.game.current, banner) == 0 then
        Vector.empty
      else Vector(burnt)

  /** What the chosen banner lost in the step before the note, and whose it
    * is. No answer means no banner was held at the site, whose line the
    * `Branch` already wrote. */
  private def burnedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Banner(banner) <-
      NoteSupport.answer(states, decisionId)
    holder <- BannerRules.holder(states.now.game.current, banner)
  yield
    val lost = states.previous.fold(0)((before, after) =>
      BannerRules.resources(before.game.current, banner) -
        BannerRules.resources(after.game.current, banner))
    val unit = banner match
      case Banner.PeoplesFavor => NoteUnit.Favor
      case Banner.DarkestSecret => NoteUnit.Secret
    if lost > 0 then burned(card, NoteArg.Player(actor),
      NoteArg.Amount(lost, unit), NoteArg.Player(holder),
      NoteArg.Banner(banner))
    else empty(card, NoteArg.Player(holder), NoteArg.Banner(banner))
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, replace:

```scala
      Enchantress) ++
```

with:

```scala
      Enchantress, AmberFlame) ++
```

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AmberFlameSuite oathdigital.gameplay.powers.action.BookOfRecordsSuite"`
Expected: Amber Flame's 6 tests pass, and Book of Records' suite passes unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/AmberFlame.scala src/main/scala/oathdigital/gameplay/powers/action/BookOfRecords.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/test/scala/oathdigital/gameplay/powers/action/AmberFlameSuite.scala
git commit -m "feat(powers): add Amber Flame"
```

---

### Task 9: Docs and gates

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
Fair, Skeleton Key and Messenger. Slices 3b to 4 remain.
```

with:

```markdown
Fair, Skeleton Key and Messenger. Slice 3b is done: the ACTION powers Quick
Exit, Dream Thief, Second Chance, Whispering Leaves, Enchantress, Armed Mob,
Honor Guard and Amber Flame. Slices 3c and 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 51 (5 in Task 1, 7 in Task 2, 8 in Task 3, 7 in Task 4, 5 in Task 5, 7 in Task 6, 6 in Task 7, 6 in Task 8). From a baseline of 2388, that is 2439. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 3b"
```
