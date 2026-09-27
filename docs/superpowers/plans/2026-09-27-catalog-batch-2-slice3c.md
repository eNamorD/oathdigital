# Catalog Batch 2, Slice 3c (Oracle and Drawing a Vision) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Oracle (denizen 160) with the addition the spec calls N6: draw the first Vision from the world deck and play or discard it through Search's placement.

**Architecture:**

- Oracle is an ACTION phase power built on `PaidAction`, costing 2 secrets placed. It is registered through `SelfActionPowers`, like the rest of slice 3. Unlike them it needs the catalog, because card placement plans with it. `SelfActionPowers.powers` therefore becomes `SelfActionPowers.forCatalog(catalog)`.
- Its tree has three nodes:
  - A `BuildOps` finds the first `VisionId` in the world deck, which is stored top first. It takes that Vision into the player's temporary hand with a `Take` from an unspecified stack position, and advances the Visions Drawn track with `AdvanceVisionsDrawn`. `Draw` cannot do this: it always takes from the top. The cards above the Vision stay where they are, and nobody peeks at them.
  - A `Note` writes the draw, or the no-Vision line.
  - A `Branch` embeds `CardPlayProcedure.unchecked(catalog, live, player, vision, CardPlayProcedure.Origin.TemporaryHand)`. This is the same placement subtree a Search's kept card goes through, so it offers faceup, facedown as an adviser, or discard, and never a site for a Vision. The walker's restriction look-ahead hides a faceup play that Vow of Obedience, Secret Police or Sacred Ground forbids.
- On resume the tree is rebuilt from live state. The `Branch` reads the Vision from the temporary hand, or, once it has been played, from the placement answer (`CardPlayProcedure.placedCard`), as `SearchProcedure.selectedCards` does.
- The log already writes a Search's placement line ("Played {card} as an adviser", "Discarded {card} to the Cradle discard") from `ActionLines.playedAdviser`, but only for Search and facedown-adviser runs. Task 2 posts it for a `UsePower` run too. Only a run that answered a `cardplay.place.*` decision is affected, and Oracle's is the only power run that asks one.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. There is no frontend work: the placement decision, its subject card and the Vision's back already render.

**Spec:** `docs/superpowers/specs/2026-09-26-catalog-batch-2-design.md` ("N6. Drawing a Vision", "Slicing", "Log lines", "Testing"), with the per-card ruling in `docs/superpowers/specs/2026-09-26-catalog-batch-2-rulings.md` ("Slice 3: actions on yourself", 160 Oracle). Read both before starting. Slices 3a and 3b (plans `docs/superpowers/plans/2026-09-27-catalog-batch-2-slice3a.md` and `…-slice3b.md`) are merged.

**Rulings made while planning:**

- The spec's "verify at plan time" question, how Search's placement is entered without a Search, has this answer. `CardPlayProcedure.unchecked` is already an embeddable subtree; Silver Tongue and Mob match on its `PlacementTree`. Oracle puts the Vision in the temporary hand, which is the origin a Search's kept card plays from, and embeds the subtree.
- The subtree keeps its `SearchPlayAdviser` window, so Search's placement modifiers (Silver Tongue's adviser limit, Mob's site discard) apply to Oracle's play too. The card says "as if you searched".
- Oracle's line covers nothing. A `Take` of a card from the deck into a hand writes no generic line, and `AdvanceVisionsDrawn` writes none either.
- Oracle is usable with a world deck that holds no Vision. The ruling says "the cost is paid and nothing happens", so the note `used.none` replaces "Used {card}".
- A Vision is taken from the deck without a `Peek` of the cards above it. They stay unseen.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member, or a non-exhaustive match over a sealed type, fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Importing `gameplay.actions.cardplay.CardPlayProcedure` is allowed; Silver Tongue does.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines are exactly the spec's (`Log lines`, "Phase powers"), where `{Red}` is the acting player:

  | Card | Key | Line | Covers |
  |---|---|---|---|
  | Oracle | `used` | Oracle: {Red} drew {Vision} from the world deck. | no |
  | Oracle, no Vision | `used.none` | Oracle: The world deck held no Vision. | no |

  `{Vision}` is `NoteArg.Card`, so a viewer who may not identify it reads its back. The placement line that follows stays.
- Card ids: Oracle `DenizenId("160")`, power `denizen.oracle`, site-only, cost 2 secrets placed. The test Vision is `VisionRules.Faith` (`VisionId("vision:vision-of-faith")`); Vow of Obedience is `DenizenId("121")`.
- Storage order: the world deck is stored top first.
- Placement buttons: `discard`, `adviser-faceup`, `adviser-facedown` (and `site`, never offered for a Vision). The placement decision id is `CardPlayProcedure.placementDecisionId(card)`, `cardplay.place.vision.<id>`.
- Baselines: record the server test count from your first full `./sbtw "test"` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/scala/oathdigital/gameplay/powers/action/Oracle.scala` | The Oracle power: take the first Vision, note it, embed its placement. |
| Modify `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` | `powers` becomes `forCatalog(catalog)`, adding Oracle. |
| Modify `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` | Calls `SelfActionPowers.forCatalog(catalog)`. |
| Create `src/test/scala/oathdigital/gameplay/powers/action/OracleSuite.scala` | Walker-driven suite: draw, placements, restriction, empty deck, cost, replay, line. |
| Modify `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` | A `UsePower` run posts the placement line after its own lines. |
| Modify `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` | An `oracle` script, added to `named`. |
| Create `src/test/resources/gamelog/oracle.actor.log`, `oracle.other.log` | The script's golden logs. |
| Modify `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` | Oracle's line, the placement line after it, and the Vision's back for another seat. |
| Modify `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala` | Pins `denizen.oracle`. |
| Modify `docs/ROADMAP.md` | Records slice 3 done. |

---

### Task 1: Oracle draws a Vision and plays it as if searched

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Oracle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala:22`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/OracleSuite.scala`

**Interfaces:**
- Consumes: `PaidAction(idValue, cost)`, `CardPlayProcedure.unchecked`, `CardPlayProcedure.placedCard`, `CardPlayProcedure.placementDecisionId`, `CatalogCards.denizen(catalog, power)`, `PowerSourceRef.of`, `NoteKey`, `NoteArg.Player`, `NoteArg.Card`.
- Produces:
  - `final case class Oracle private (catalog: ExecutableCatalog) extends PaidAction("denizen.oracle", Cost(secret = 2))`, with `noteKeys = Vector(Oracle.drew, Oracle.noVision)`.
  - `object Oracle` with `val id: PowerId`, `val drew: NoteKey` (key `used`), `val noVision: NoteKey` (key `used.none`), and `def forCatalog(catalog: ExecutableCatalog): Option[Oracle]`.
  - `SelfActionPowers.forCatalog(catalog: ExecutableCatalog): Vector[PhasePower]`, which replaces `SelfActionPowers.powers`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/OracleSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{CardStaging, NoteText, PhasePowerCatalog,
  PowerFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.ParkedNode

class OracleSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture.{after, awaits, giveAdviser, parked, pick, usableNow,
    withSecrets}

  private val oracle = DenizenId("160")
  private val source = DecisionOptionRef.Denizen(oracle)
  private val faith = VisionRules.Faith
  private val vow = DenizenId("121")
  private val power = Oracle.forCatalog(catalog).get
  private val placement = CardPlayProcedure.placementDecisionId(faith)
  /** The production walker rules and phase powers, so a Restriction such as
    * Vow of Obedience's hides what it forbids. */
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** The actor stands beside a site Oracle with two faceup secrets. Every
    * Vision in the world deck waits in the Provinces discard pile, and Faith
    * is the deck's third card, or, `withVision = false`, nowhere in it. */
  private def staged(withVision: Boolean = true): ReadyGame =
    val ready = inPhase(withSecrets(atHome(CardStaging.without(base, faith),
      oracle), actor, 2, 0), Phase.Act)
    ready.updateCurrent { c =>
      val deck = c.commonCards.worldDeck
      val visions = deck.collect { case vision: VisionId => vision }
      val plain = deck.filterNot(visions.contains)
      c.copy(commonCards = c.commonCards.copy(
        worldDeck = if withVision then plain.take(2) ++ Vector(faith) ++
          plain.drop(2) else plain,
        regionalDiscards = c.commonCards.regionalDiscards.updated(
          Region.Provinces, c.commonCards.discard(Region.Provinces) ++
            visions)))
    }
  private def deckOf(ready: ReadyGame) = ready.game.current.commonCards.worldDeck
  private def drawn(ready: ReadyGame) = ready.game.current.tracks.visionsDrawn

  private def start(ready: ReadyGame): Either[OathViolation, OathTransition] =
    rules.startWalker(OathState.Ready(ready), ActionRef.UsePower(Oracle.id),
      actor, Vector.empty, Vector(source))
  private def place(from: OathTransition, button: String) =
    rules.resolveWalker(from.state, actor, placement,
      pick(DecisionOptionRef.Button(button)))
  private def replayed(from: ReadyGame, events: Vector[OathEvent]) =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  /** The buttons of the placement the walk is parked on, after the
    * restriction look-ahead. */
  private def buttons(from: OathTransition): Vector[String] =
    ParkedNode.of(from.state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref).collect {
          case DecisionOptionRef.Button(key) => key }
        case other => fail(s"not a choose-one: $other")
      case other => fail(s"not parked on a decision: $other")

  test("Oracle is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(Oracle.id).isDefined)

  test("it places two secrets and draws the first Vision, the cards above " +
      "it staying in place"):
    val ready = staged()
    val t = start(ready).toOption.get
    assert(awaits(t, placement), parked.parkedDecision(t.state).toString)
    val drew = after(t)
    assertEquals(PaidActionHarness.tokensOn(drew, oracle), Tokens(0, 2))
    assertEquals(drew.game.current.temporaryHands.get(actor),
      Some(Vector(faith)))
    assertEquals(deckOf(drew), deckOf(ready).filterNot(_ == faith))
    assertEquals(drawn(drew), drawn(ready) + 1)
    assert(!drew.knowledge.advisers.getOrElse(actor, Vector.empty)
      .exists(deckOf(ready).take(2).contains))
    assertEquals(buttons(t).toSet,
      Set("discard", "adviser-faceup", "adviser-facedown"))

  test("played facedown, the Vision becomes an adviser; replay and the wire " +
      "agree"):
    val ready = staged()
    val t = start(ready).toOption.get
    val done = place(t, "adviser-facedown").toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val held = after(done)
    assert(player(held).advisers.contains(
      VisionState(faith, Orientation.FaceDown)), player(held).advisers.toString)
    assertEquals(held.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val events = t.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("discarded, the Vision goes to a discard pile"):
    val t = start(staged()).toOption.get
    val done = place(t, "discard").toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val located = CardIndex.from(after(done).game).toOption
      .flatMap(_.get(faith)).map(_.location.container)
    assert(located.exists {
      case CardContainer.RegionalDiscard(_) => true
      case _ => false
    }, located.toString)

  test("a faceup Vow of Obedience holder may not play it faceup"):
    val holding = giveAdviser(CardStaging.without(staged(), vow), actor, vow,
      Orientation.FaceUp)
    val t = start(holding).toOption.get
    assert(!buttons(t).contains("adviser-faceup"), buttons(t).toString)
    assert(buttons(t).contains("adviser-facedown"))
    assert(buttons(t).contains("discard"))
    assert(place(t, "adviser-faceup").isLeft)

  test("its line names the Vision it drew"):
    val t = start(staged()).toOption.get
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${actor.value} drew ${faith.value} from the world deck.",
      covers = false)))

  test("a world deck with no Vision takes the cost and says so"):
    val ready = staged(withVision = false)
    val t = start(ready).toOption.get
    parked.assertResumed(t.state, Phase.Act, actor)
    val paid = after(t)
    assertEquals(PaidActionHarness.tokensOn(paid, oracle), Tokens(0, 2))
    assertEquals(deckOf(paid), deckOf(ready))
    assertEquals(drawn(paid), drawn(ready))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "The world deck held no Vision.", covers = false)))

  test("it is unusable without two faceup secrets"):
    val broke = withSecrets(staged(), actor, 1, 1)
    assert(!usableNow(broke).exists(_.power.id == Oracle.id))
    assert(start(broke).isLeft)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.OracleSuite"`
Expected: compile failure, `Not found: Oracle`.

- [ ] **Step 3: Write Oracle**

Create `src/main/scala/oathdigital/gameplay/powers/action/Oracle.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Oracle (denizen 160), site-only, ACTION: place 2 secrets on this card,
  * then draw the Vision closest to the top of the world deck and play or
  * discard it as if you searched.
  *
  * The Vision is taken from wherever it lies, into the player's temporary
  * hand, and the Visions Drawn track advances. The cards above it stay in
  * place, unseen. The Vision is then placed through the same card-play
  * subtree a Search's kept card goes through, so the restrictions on a
  * faceup Vision and Search's placement modifiers apply. A deck with no
  * Vision draws nothing and asks nothing.
  *
  * The tree is rebuilt on every resume. The placement reads the Vision from
  * the temporary hand, or, once it is played, from the placement answer, as
  * a Search does.
  */
final case class Oracle private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.oracle", Cost(secret = 2)):
  import Oracle._

  override def noteKeys: Vector[NoteKey] = Vector(drew, noVision)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(first(live).toVector.flatMap(vision =>
      Vector[CoreOperation](Take(Piece.Card(vision), player,
        Location.Deck(CardDeck.World), Location.Hand(player)),
        AdvanceVisionsDrawn)))),
    Note(this.id, states => PowerSourceRef.of(source).map(card =>
      held(states.now, player).fold(noVision(card))(vision =>
        drew(card, NoteArg.Player(player), NoteArg.Card(vision))))),
    Branch((live, pending) => held(live, player)
      .orElse(CardPlayProcedure.placedCard(pending)).toVector.map(vision =>
        CardPlayProcedure.unchecked(catalog, live, player, vision,
          CardPlayProcedure.Origin.TemporaryHand))))))

object Oracle:
  val id: PowerId = PowerId("denizen.oracle")
  /** "{Red} drew {Vision} from the world deck." */
  val drew: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1),
    NotePart.Text(" from the world deck.")))
  /** "The world deck held no Vision." */
  val noVision: NoteKey = NoteKey("used.none",
    Vector(NotePart.Text("The world deck held no Vision.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Oracle] =
    CatalogCards.denizen(catalog, id).map(_ => new Oracle(catalog))

  /** The Vision closest to the top of the world deck, stored top first. */
  private def first(ready: ReadyGame): Option[VisionId] =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case vision: VisionId => vision }

  /** The Vision in the player's temporary hand: the one Oracle drew, until
    * it is played. */
  private def held(ready: ReadyGame, player: PlayerId): Option[VisionId] =
    ready.game.current.temporaryHands.getOrElse(player, Vector.empty)
      .collectFirst { case vision: VisionId => vision }
```

If `-Werror` flags `catalog` as a private field clash with the inherited name or reports an unused import, fix only that; keep the behavior.

- [ ] **Step 4: Register it**

Replace `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[DiceAndRelicDrawPowers]]. Oracle plans card placement, so it needs
  * the catalog, and is omitted when its card is absent.
  */
object SelfActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Tutor, ShiftingMap, DemonTail, WizardSchool,
      SpiritSnare, ClayRattle, Scryer, OracularPig) ++
      Oracle.forCatalog(catalog).toVector
```

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, replace `SelfActionPowers.powers ++` with `SelfActionPowers.forCatalog(catalog) ++`.

Then check nothing else reads the old name:

Run: `grep -rn "SelfActionPowers.powers" src`
Expected: no output.

- [ ] **Step 5: Run the suite to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.OracleSuite"`
Expected: PASS, 8 tests.

If the unusable test fails because the fixture's secret staging differs (`withSecrets` sets both faceup and facedown counts), read `PaidAction`'s cost check and `ScryerSuite`'s "unusable" test before changing the assertion. The cost is 2 faceup secrets placed. If the Vow test finds `adviser-faceup` still offered, the restriction look-ahead does not reach the embedded subtree. Stop and report: that is a design question, not a test fix.

- [ ] **Step 6: Run the power suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.* oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `PowerNoteCatalogSuite` accepts both templates: `drew` starts with an argument and `noVision` with a capital letter.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Oracle.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/action/OracleSuite.scala
git commit -m "feat(powers): Oracle draws the first Vision and plays it as if searched"
```

---

### Task 2: A power run's placement line

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (the `ActionRef.UsePower` arm of `lines`)
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (a new `oracle` script, and `named`)
- Create: `src/test/resources/gamelog/oracle.actor.log`, `src/test/resources/gamelog/oracle.other.log`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: `Oracle.id` (Task 1), `ActionLines.PlacePrefix`, `ParkedServiceFixture.withWorldDeckTop`, `ParkedServiceFixture.topOfWorldDeck`, `Step.Arrange`, `GameCommand.UsePower`.
- Produces: `LogScripts.oracle: Script`, named `"oracle"`.

- [ ] **Step 1: Add the script**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, add `Oracle` to the powers import:

```scala
import oathdigital.gameplay.powers.action.{GamblingHall, Oracle, Wolves}
```

Add this script after `wolves`:

```scala
  /** Oracle at the actor's site, used in Act with two secrets arranged. A
    * first game's world deck holds its first Vision below ten denizens;
    * Oracle draws it, and the actor keeps it as a facedown adviser. */
  def oracle(using munit.Location): Script =
    val card = DenizenId("160")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("oracle")
    val woken = Situation.wake(driver, chronicle, orders)
    val actor = active(woken)
    woken.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(DecisionOptionRef.Button("adviser-facedown"))
    }.after(Step.Arrange(Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(woken, actor))),
        Move(Piece.Secrets(2), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor),
      GameCommand.UsePower(actor, Oracle.id, DecisionOptionRef.Denizen(card)))
    Script("oracle", service, actor)
```

In `named`, change the last entry to:

```scala
    "gambling-hall" -> (() => gamblingHall), "wolves" -> (() => wolves),
    "oracle" -> (() => oracle))
```

- [ ] **Step 2: Write the failing test**

Append to `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`:

```scala

  test("Oracle writes its draw, then where the Vision went, named to its " +
      "drawer alone"):
    val script = oracle
    val actor = name(script.actor)
    val other = script.players.find(_ != script.actor).get
    val last = script.history.steps.last.after match
      case OathState.Ready(ready) => ready
      case state => fail(s"expected a ready game, got $state")
    val vision = last.game.current.players.find(_.player == script.actor).get
      .advisers.collectFirst {
        case VisionState(id, Orientation.FaceDown) => id }.get
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val drew = all.indexWhere(_.startsWith(s"Oracle: $actor drew "))
      assert(drew >= 0, all)
      assert(all(drew).endsWith(" from the world deck."), all(drew))
      assertEquals(shown(drew).kind, LogKind.Action)
      assert(all(drew + 1).startsWith("Played "), all)
      assert(all(drew + 1).endsWith(" as an adviser"), all)
      assert(!all.drop(drew).exists(_.startsWith("Used ")), all)
      val named = shown(drew).spans.exists {
        case LogSpan.Card(id, _) => id == vision.value
        case _ => false
      }
      assertEquals(named, viewer == script.actor, shown(drew).spans.toString)
    }
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: FAIL on the new test only: `all(drew + 1)` is not a "Played " line, because a `UsePower` run posts no placement line.

If instead the script fails to build (for example, the arranged secrets are refused, or no Vision is drawn), fix the script's staging first. The arranged `Move` of secrets copies the Wolves script; the actor needs two faceup secrets.

- [ ] **Step 4: Post the placement line for a power run**

In `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`, replace the `UsePower` arm:

```scala
      case ActionRef.UsePower(power) =>
        usedPower(journal, run, at, power, completing, viewer)
```

with:

```scala
      // A power that plays a card as if searched (Oracle) posts where the
      // card went, as a Search does. Only a run that answered a placement
      // decision has such a line.
      case ActionRef.UsePower(power) =>
        usedPower(journal, run, at, power, completing, viewer) ++
          playedAdviser(journal, run, at, viewer)
```

- [ ] **Step 5: Write the golden logs and review them**

Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`

Then run: `git status --short src/test/resources/gamelog`
Expected: exactly two new files, `oracle.actor.log` and `oracle.other.log`, and no modified golden. A modified golden means the `ActionLines` change altered another script's log. Revert that golden with `git checkout -- <file>` and find out why before continuing.

Read both new files line by line. Check:
- The actor's file has `Oracle: [player:…] drew [card:vision:…|…] from the world deck.`, then `Played [card:vision:…|…] as an adviser`.
- The other seat's file has the same two lines with the Vision as a back, not as `[card:vision:…]`.
- No `Used` line follows the Oracle line. No `Drew` or `Peeked` line comes from the draw.

- [ ] **Step 6: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, including `GameLogGoldenSuite` for `oracle` and the property suites that run every named script.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  src/test/resources/gamelog/oracle.actor.log \
  src/test/resources/gamelog/oracle.other.log
git commit -m "feat(log): a power that plays a card posts where it went"
```

---

### Task 3: Pin the card, record the slice, run the gates

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Pin Oracle**

In `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`, after the test "catalog batch 2's peeking actions are implemented", add:

```scala

  test("catalog batch 2's Vision draw is implemented"):
    assert(implemented(PowerId("denizen.oracle")))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS.

- [ ] **Step 2: Record the slice in the roadmap**

In `docs/ROADMAP.md`, replace:

```text
Clay Rattle, with the `Shuffle` operation. Slice 3b is done: Scryer and
Oracular Pig, with the `Inspect` decision and the card list. Slice 3c
(Oracle and drawing a Vision) remains, then slice 4, actions on others, and
slice 5, triggers and when-played powers.
```

with:

```text
Clay Rattle, with the `Shuffle` operation. Slice 3b is done: Scryer and
Oracular Pig, with the `Inspect` decision and the card list. Slice 3c is
done: Oracle, which draws a Vision and plays it through Search's placement.
Slice 4, actions on others, and slice 5, triggers and when-played powers,
remain.
```

If the text around it has changed on `main`, keep its facts and change only the slice 3c sentence.

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 10 (8 in `OracleSuite`, 1 in `GameLogPowerLinesSuite`, 1 in `PowerImplementationStatusSuite`) plus the tests that run once per named script for `oracle`. The frontend count equals its baseline.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  docs/ROADMAP.md
git commit -m "docs: record catalog batch 2 slice 3c"
```
