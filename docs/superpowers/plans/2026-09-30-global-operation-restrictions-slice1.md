# Global Operation Restrictions, Slice 1 (The Seam) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make operation restrictions global: one restriction set, built from the catalog and the offered powers, that every walker step, `MinorActions`, `StateBasedEvaluation` and the modifier payment dry run apply, checked against composites before the walker splits them. Locked, the active-modifier rule and the Hall of Ministers move onto it, and `DiscardRestrictions` retires.

**Architecture:**

- `OperationRestrictions` (new, `gameplay/operations`) holds a catalog's **printed** restrictions (a `LockedCard` per lock-icon card, and `HallOfMinisters`) and builds the set for a command with `active(powerRestrictions, modifiers)`, which adds `ActiveModifier` for the selected modifiers and the powers' own restrictions.
- A power registers restrictions through a new member, `ContributingPower.operationRestrictions`. It hooks no window.
- `WalkerPowers` (moved to its own file) carries the catalog's `OperationRestrictions` and the command's modifiers, and exposes `operationRestrictions`. `WalkerPowers.selected` binds the modifiers, so the active-modifier rule sees them on an action's first walk.
- The walker's `recordBatch` runs every step with `ctx.powers.operationRestrictions`, and `walkComposite` screens a composite with the same set before splitting it, so a restriction sees `Take`, `Swap` or `Discard.Denizen` whole.
- `OperationPipeline.run` takes the set as a required argument. `BuildOps.restrictions`, `DiscardRestrictions`, its suites, and `CardPlay`'s locked-adviser check are deleted. Horned Mask and Twin Brother keep their option filters (slice 2 deletes them), pointed at the shared, faceup-aware `OperationRestrictions.isLocked`.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md` ("Today", "Rules", "The restriction set", "Composites are checked whole", "What retires", "Testing: Slice 1"). Read it before starting.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Remove imports a change leaves unused.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `ProcedureWalker.scala` is at 746, which is why Task 2 moves `WalkerPowers` out of it.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Powers may import `gameplay.operations`.
  - `gameplay/operations` must not import `gameplay.powerresolver` or `gameplay.walker`: `OperationRestrictions.active` takes the powers' restrictions as a plain vector.
- Every global restriction refuses as `OperationReasonKind.Impossible`, with the codes `locked`, `active-modifier` and `discard-immune`.
- Locked: a card with the lock icon (`CardRestrictions.LockedAdviserOnly` or `CardRestrictions.Locked` on a denizen, `Locked` on an edifice's intact face) refuses any `Move` or `Flip` of itself, found through `Operation.flatten`, while it shows the lock: faceup in a play area, at a site, and for an edifice only on its intact side. `Bury` is never refused. Facedown cards and cards in a hand or deck have no restrictions.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

---

### Task 1: The restriction rules

Pure addition: the new restrictions and the set exist and are tested, and nothing uses them yet.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/LockedCard.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`

**Interfaces:**
- Consumes: `OperationRestriction`, `OperationReason`, `OperationReasonKind` (`model/OperationReason.scala`); `Operation.flatten` (`model/Operation.scala:27`); `ExecutableCatalog` (`catalog/CatalogModel.scala:78`).
- Produces:
  - `final class OperationRestrictions` with `val printed: Vector[OperationRestriction]` and `def active(powerRestrictions: Vector[OperationRestriction], modifiers: Vector[PowerId]): Vector[OperationRestriction]`.
  - `object OperationRestrictions` with `val none: OperationRestrictions`, `def forCatalog(catalog: ExecutableCatalog): OperationRestrictions` and `def isLocked(catalog: ExecutableCatalog, ready: ReadyGame, card: CardId): Boolean`.
  - `final case class LockedCard(card: CardId) extends OperationRestriction`, with `LockedCard.showing(ready: ReadyGame, card: CardId): Boolean`.
  - `final case class HallOfMinisters(catalog: ExecutableCatalog) extends OperationRestriction`.
  - `final case class ActiveModifier(catalog: ExecutableCatalog, modifiers: Vector[PowerId]) extends OperationRestriction` (in `OperationRestrictions.scala`).

- [ ] **Step 1: Write the failing suite**

`src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.{LockedCard, OperationRestrictions}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The global operation restrictions (global operation restrictions design,
  * "Rules"): a locked card cannot be moved or flipped while it shows its lock,
  * a modifier selected for the running action cannot be discarded, and the
  * Hall of Ministers protects its ruler's sites from enemy discards.
  */
class OperationRestrictionsSuite extends munit.FunSuite:
  private val set = OperationRestrictions.forCatalog(catalog)
  /** Locked and adviser-only. */
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  /** The Order edifice Hall of Ministers, locked while intact. */
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val wildCry = CatalogNames.denizen("Wild Cry")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  private val drum = CatalogNames.relic("Dragonskin Drum")

  private def refusal(ready: ReadyGame, operation: CoreOperation,
      modifiers: PowerId*): Option[String] =
    set.active(Vector.empty, modifiers.toVector)
      .flatMap(_.reason(ready, operation)).headOption.map(_.code)

  private def discard(card: DenizenId, from: Location,
      actor: PlayerId = p1): Discard.Denizen =
    Discard.Denizen(card, PositionedLocation(from), Region.Provinces,
      catalog.suitOf(card).get, 0, 0, actor)

  /** p1 holds `card`, p2 holds the plain card. */
  private def holding(card: DenizenId, facedown: Boolean = false): ReadyGame =
    Table.start.adviser(p1, card, facedown = facedown)
      .adviser(p2, plainCard).ready

  /** Every way to move or flip p1's `card`: a discard, a Move, a Flip, a
    * Swap with p2's plain card, a Take by p2 and a Give to p2. */
  private def movesOf(card: DenizenId): Vector[CoreOperation] =
    val mine = PositionedLocation(Location.PlayArea(p1))
    val theirs = PositionedLocation(Location.PlayArea(p2))
    Vector(
      discard(card, Location.PlayArea(p1)),
      Move(Piece.Card(card), mine, theirs),
      Flip(card, Location.PlayArea(p1), Orientation.FaceDown),
      Swap(card, mine, plainCard, theirs),
      Take(Piece.Card(card), p2, Location.PlayArea(p1), Location.PlayArea(p2)),
      Give(Piece.Card(card), p1, Location.PlayArea(p1), Location.PlayArea(p2)))

  test("a faceup locked adviser refuses every move and flip of itself"):
    val ready = holding(lockedCard)
    assertEquals(movesOf(lockedCard).map(refusal(ready, _)),
      Vector.fill(6)(Some("locked")))

  test("the lock refuses as Impossible, so an optional operation is skipped"):
    val reason = set.active(Vector.empty, Vector.empty).flatMap(
      _.reason(holding(lockedCard), movesOf(lockedCard).head)).head
    assertEquals(reason.kind, OperationReasonKind.Impossible)

  test("a facedown locked adviser has no restrictions"):
    val ready = holding(lockedCard, facedown = true)
    assertEquals(movesOf(lockedCard).map(refusal(ready, _)),
      Vector.fill(6)(None))

  test("Bury ignores locked"):
    val bury = Bury(BuryableCard.Denizen(lockedCard),
      PositionedLocation(Location.PlayArea(p1)))
    assertEquals(refusal(holding(lockedCard), bury), None)

  test("a locked card drawn by a Search can still be discarded from the hand"):
    val drawn = Table.start.hand(p1, lockedCard).ready
    assertEquals(refusal(drawn, discard(lockedCard, Location.Hand(p1))), None)

  test("an ordinary adviser can be discarded"):
    val ready = Table.start.adviser(p1, plainCard).ready
    assertEquals(refusal(ready, discard(plainCard, Location.PlayArea(p1))), None)

  /** The Hall on `side` at p1's site, which p1 rules. */
  private def edificeAt(side: EdificeSide): (ReadyGame, SiteId) =
    val home = Table.homeOf(p1)
    (Table.start.edifice(hall, side, at = home).warbandsAt(home, p1, 1).ready,
      home)

  test("an intact edifice is locked, and a ruined one is not"):
    def edificeOps(site: SiteId): Vector[CoreOperation] = Vector(
      Discard.RuinedEdifice(hall, PositionedLocation(Location.Site(site)),
        catalog.suitOf(hall).get, 0, 0, p1),
      Take(Piece.Card(hall), p1, Location.Site(site), Location.PlayArea(p1)))
    val (intact, site) = edificeAt(EdificeSide.Intact)
    assertEquals(edificeOps(site).map(refusal(intact, _)),
      Vector.fill(2)(Some("locked")))
    val (ruined, ruinedSite) = edificeAt(EdificeSide.Ruined)
    assertEquals(edificeOps(ruinedSite).map(refusal(ruined, _)),
      Vector.fill(2)(None))

  test("a modifier selected for the running action cannot be discarded"):
    val ready = Table.start.adviser(p1, wildCry).ready
    val operation = discard(wildCry, Location.PlayArea(p1))
    assertEquals(refusal(ready, operation), None)
    assertEquals(refusal(ready, operation, PowerId("denizen.wild-cry")),
      Some("active-modifier"))
    // A different modifier selected leaves the card free.
    assertEquals(refusal(ready, operation, PowerId("denizen.tents")), None)

  test("a relic modifier selected for the running action cannot be discarded"):
    val relic = Discard.Relic(drum,
      PositionedLocation(Location.PlayArea(p1)), 0, p1)
    val ready = Table.start.relic(p1, drum).ready
    assertEquals(refusal(ready, relic), None)
    assertEquals(refusal(ready, relic, PowerId("relic.dragonskin-drum")),
      Some("active-modifier"))

  test("the Hall of Ministers refuses an enemy's discard at its ruler's sites, " +
      "relics included, and names the discard's own acting player"):
    val home = Table.homeOf(p1)
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).denizen(plainCard, at = home)
      .relicAt(drum, at = home).ready
    val byEnemy = discard(plainCard, Location.Site(home), actor = p2)
    val relicByEnemy = Discard.Relic(drum,
      PositionedLocation(Location.Site(home)), 0, p2)
    assertEquals(refusal(ready, byEnemy), Some("discard-immune"))
    assertEquals(refusal(ready, relicByEnemy), Some("discard-immune"))
    assertEquals(refusal(ready, byEnemy.copy(actingPlayer = p1)), None)

  test("the catalog prints a lock for every lock-icon card and only those"):
    assert(set.printed.contains(LockedCard(lockedCard)))
    assert(set.printed.contains(LockedCard(hall)))
    assert(!set.printed.contains(LockedCard(plainCard)))

  test("isLocked is faceup-aware"):
    assert(OperationRestrictions.isLocked(catalog, holding(lockedCard),
      lockedCard))
    assert(!OperationRestrictions.isLocked(catalog,
      holding(lockedCard, facedown = true), lockedCard))
    assert(!OperationRestrictions.isLocked(catalog, holding(lockedCard),
      plainCard))

  test("no catalog holds only the powers' restrictions"):
    assertEquals(OperationRestrictions.none.active(Vector.empty,
      Vector(PowerId("denizen.wild-cry"))), Vector.empty)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: compile failure, `value OperationRestrictions is not a member of oathdigital.gameplay.operations`.

- [ ] **Step 3: Write `LockedCard`**

`src/main/scala/oathdigital/gameplay/operations/LockedCard.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** A card with the lock icon cannot be moved or flipped while it shows the
  * lock (global operation restrictions design, "Rules"). A discard, a `Take`,
  * a `Give` and a `Swap` all move the card, so each is refused; `Bury`
  * ignores locked and is never refused. A facedown card has no restrictions,
  * and a card in a hand or a deck is not in play.
  *
  * One instance exists per lock-icon card (`OperationRestrictions.printed`),
  * so a refusal names its card.
  */
final case class LockedCard(card: CardId) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Option.when(touches(operation) && LockedCard.showing(ready, card))(
      OperationReason("locked", s"${card.value} is locked",
        OperationReasonKind.Impossible))

  private def touches(operation: CoreOperation): Boolean =
    Operation.flatten(operation).exists {
      case Move(Piece.Card(moved), _, _, _) => moved == card
      case Flip(flipped, _, _) => flipped == card
      case _ => false
    }

object LockedCard:
  /** `card` shows its lock now: a faceup adviser in a play area, a denizen at
    * a site (always faceup), or an edifice at a site on its intact side. */
  def showing(ready: ReadyGame, card: CardId): Boolean =
    val current = ready.game.current
    current.players.exists(_.advisers.exists {
      case held: DenizenState =>
        held.id == card && held.orientation == Orientation.FaceUp
      case _ => false
    }) || current.map.sites.values.exists(_.denizens.exists {
      case edifice: EdificeState =>
        edifice.id == card && edifice.side == EdificeSide.Intact
      case other => other.id == card
    })
```

- [ ] **Step 4: Write `HallOfMinisters`**

`src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala`. The rule is `DiscardRestrictions.hallReason` (`gameplay/operations/DiscardRestrictions.scala:102-134`), except the actor is the discard's own acting player (the active player for a Vision, which names none), so one instance serves every path:

```scala
package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The Hall of Ministers (edifice E16, intact): an enemy of the Hall's ruler
  * acts as if the denizens and relics at the ruler's sites were locked, so it
  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  */
final case class HallOfMinisters(catalog: ExecutableCatalog)
    extends OperationRestriction:
  private val hallPower = PowerId("edifice.e16.intact")

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    discardAt(ready, operation).flatMap { case (site, actor) =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if lineage.role.isImperial then SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      val targetRuler = current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, current.players).toOption)
      for
        sourceSide <- actorSide
        ruler <- targetRuler
        if SiteRule.enemies(sourceSide, ruler)
        if current.map.sites.exists { case (_, state) =>
          SiteRule.ruler(state.forces, current.players).toOption.contains(ruler) &&
            state.denizens.exists:
              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifice(edifice.id)
                  .exists(_.intact.powers.exists(_.id == hallPower))
              case _ => false
        }
      yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible)
    }

  /** The site a discard takes a card from, and who discards it. */
  private def discardAt(ready: ReadyGame, operation: CoreOperation)
      : Option[(SiteId, PlayerId)] =
    val active = ready.game.current.turn.activePlayer
    val source = operation match
      case value: Discard.Denizen => Some(value.from.location -> value.actingPlayer)
      case value: Discard.Vision => Some(value.from.location -> active)
      case value: Discard.RuinedEdifice =>
        Some(value.from.location -> value.actingPlayer)
      case value: Discard.Relic => Some(value.from.location -> value.actingPlayer)
      case _ => None
    source.collect { case (Location.Site(site), actor) => site -> actor }
```

- [ ] **Step 5: Write `OperationRestrictions` and `ActiveModifier`**

`src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.model._

/** The operation restrictions that hold for a command, wherever its
  * operations run (global operation restrictions design, "The restriction
  * set").
  *
  * `printed` are the restrictions a catalog's cards print: a [[LockedCard]]
  * for every lock-icon card, and the [[HallOfMinisters]]. `active` adds the
  * [[ActiveModifier]] rule for the modifiers selected for the running action,
  * and the restrictions the offered powers register. Every one refuses as
  * `Impossible`: an optional operation is skipped, a required one rejects.
  *
  * The card-classes phase replaces `printed`: a card class will mix its
  * restriction in instead of this reading the catalog.
  */
final class OperationRestrictions private (catalog: Option[ExecutableCatalog]):
  val printed: Vector[OperationRestriction] =
    catalog.fold(Vector.empty)(OperationRestrictions.printedBy)

  def active(powerRestrictions: Vector[OperationRestriction],
      modifiers: Vector[PowerId]): Vector[OperationRestriction] =
    printed ++ catalog.filter(_ => modifiers.nonEmpty)
      .map(ActiveModifier(_, modifiers)).toVector ++ powerRestrictions

object OperationRestrictions:
  /** No catalog: only the powers' own restrictions hold. For suites that
    * walk a hand-built power list. */
  val none: OperationRestrictions = new OperationRestrictions(None)

  def forCatalog(catalog: ExecutableCatalog): OperationRestrictions =
    new OperationRestrictions(Some(catalog))

  /** `card` is locked now: it prints the lock icon and shows it. The option
    * filters that hide locked cards before slice 2's search read this. */
  def isLocked(catalog: ExecutableCatalog, ready: ReadyGame,
      card: CardId): Boolean =
    lockIcon(catalog, card) && LockedCard.showing(ready, card)

  /** Only a card at a site or in a play area is in play. A card drawn by a
    * Search and discarded from the temporary hand is not. */
  private[operations] def inPlay(from: Location): Boolean = from match
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false

  private def locking(restrictions: CardRestrictions): Boolean =
    restrictions == CardRestrictions.Locked ||
      restrictions == CardRestrictions.LockedAdviserOnly

  private def lockIcon(catalog: ExecutableCatalog, card: CardId): Boolean =
    card match
      case id: DenizenId => catalog.denizen(id).exists(d => locking(d.restrictions))
      case id: EdificeId =>
        catalog.edifice(id).exists(e => locking(e.intact.restrictions))
      case _ => false

  private def printedBy(catalog: ExecutableCatalog): Vector[OperationRestriction] =
    catalog.denizens.filter(d => locking(d.restrictions))
      .map(d => LockedCard(DenizenId(d.id.value))) ++
      catalog.edifices.filter(e => locking(e.intact.restrictions))
        .map(e => LockedCard(EdificeId(e.id.value))) :+
      HallOfMinisters(catalog)

/** A card that prints a power selected for the running action cannot be
  * discarded while the action runs: a modifier a player selected at the start
  * of an action stays in play until the action ends. */
final case class ActiveModifier(catalog: ExecutableCatalog,
    modifiers: Vector[PowerId]) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] = operation match
    case value: Discard.Denizen
        if OperationRestrictions.inPlay(value.from.location) =>
      refused(value.card)
    case value: Discard.Relic
        if OperationRestrictions.inPlay(value.from.location) =>
      refused(value.card)
    case _ => None

  private def refused(card: CardId): Option[OperationReason] =
    Option.when(modifiers.exists(power => printedBy(power).contains(card)))(
      OperationReason("active-modifier", s"${card.value} is a modifier " +
        "selected for this action and cannot be discarded",
        OperationReasonKind.Impossible))

  private def printedBy(power: PowerId): Option[CardId] =
    catalog.denizenWithPower(power)
      .map(card => DenizenId(card.id.value): CardId)
      .orElse(catalog.relicWithPower(power)
        .map(card => RelicId(card.id.value): CardId))
```

If `EdificeDefinition.id` is already an `EdificeId`, drop the `EdificeId(...)` wrapping; the compiler says which.

- [ ] **Step 6: Run the suite to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: PASS, 13 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala src/main/scala/oathdigital/gameplay/operations/LockedCard.scala src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala
git commit -m "feat(restrictions): add the global operation restriction set"
```

---

### Task 2: Every walker step applies the set

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (remove `WalkerPowers` and its companion, lines 34-65; `recordBatch`, lines 651-669)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (the `ContributingPower` trait, around line 162)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala:43-68`
- Test: `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/GlobalRestrictionsWalkerSuite.scala` (create)

**Interfaces:**
- Consumes: `OperationRestrictions` (Task 1).
- Produces:
  - `ContributingPower.operationRestrictions: Vector[OperationRestriction]` (default empty).
  - `WalkerPowers(powers, probing = true, restrictionSet: OperationRestrictions = OperationRestrictions.none, modifiers: Vector[PowerId] = Vector.empty)` with `lazy val operationRestrictions: Vector[OperationRestriction]`.
  - `WalkerPowers.selected(catalog, modifiers)` sets `modifiers`.
  - Test helper `ProcedureWalkerSuite.TestOperationRestrictionPower(id: PowerId, restriction: OperationRestriction)` and the suite's private `restricting(restriction): WalkerPowers`.

- [ ] **Step 1: Write the failing tests**

In `ProcedureWalkerSuite`'s companion object (beside `TestRestrictionPower`, around line 50), add:

```scala
  /** A `ContributingPower` registering exactly one operation restriction. */
  final case class TestOperationRestrictionPower(id: PowerId,
      restriction: OperationRestriction) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def operationRestrictions: Vector[OperationRestriction] =
      Vector(restriction)
```

In the class, after `noPowers` (line 69), add:

```scala
  import ProcedureWalkerSuite.TestOperationRestrictionPower

  /** The powers of one test power that registers `restriction`. */
  private def restricting(restriction: OperationRestriction): WalkerPowers =
    WalkerPowers(Vector(TestOperationRestrictionPower(
      PowerId("test.restriction"), restriction)))

  /** Refuses every `SpendSupply`. */
  private val noSpending = new OperationRestriction:
    override def reason(state: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case _: SpendSupply => Some(OperationReason("no-spending",
        "supply cannot be spent", OperationReasonKind.Impossible))
      case _ => None
```

and the test:

```scala
  test("a power's operation restriction holds at a plain delta leaf"):
    val optional = Sequence(Vector(SpendSupply(actor, 1, required = false)))
    ProcedureWalker.advance(ready, optional, None, restricting(noSpending)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    assert(ProcedureWalker.advance(ready, Sequence(Vector(SpendSupply(actor, 1))),
      None, restricting(noSpending)).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/GlobalRestrictionsWalkerSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The production powers carry the global restrictions into every walker
  * step, whichever node runs the operation (global operation restrictions
  * design, "Testing: Slice 1"). */
class GlobalRestrictionsWalkerSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val wildCry = CatalogNames.denizen("Wild Cry")
  private val lockedCard = CatalogNames.denizen("Sealing Ward")

  private def unchanged(ready: ReadyGame, tree: Operation,
      walked: WalkerPowers)(using munit.Location): Unit =
    ProcedureWalker.advance(ready, tree, None, walked) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")

  test("a relic discard at a Hall-protected site is skipped, as Broken " +
      "Forge's is"):
    val home = Table.homeOf(p1)
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).relicAt(drum, at = home).ready
    unchanged(ready, Sequence(Vector(BuildOps((_, _) => Right(Vector(
      Discard.Relic(drum, PositionedLocation(Location.Site(home)), 0, p2)))))),
      powers)

  test("a modifier selected for the command protects its card on the " +
      "action's first walk"):
    val ready = Table.start.adviser(p1, wildCry).ready
    val tree = Sequence(Vector(BuildOps((_, _) => Right(Vector(
      Discard.Denizen(wildCry, PositionedLocation(Location.PlayArea(p1)),
        Region.Provinces, catalog.suitOf(wildCry).get, 0, 0, p1))))))
    unchanged(ready, tree, WalkerPowers.selected(powers,
      Vector(PowerId("denizen.wild-cry"))))

  test("a faceup locked adviser is not discarded by a plain walker step"):
    val ready = Table.start.adviser(p1, lockedCard).ready
    unchanged(ready, Sequence(Vector(Discard.Denizen(lockedCard,
      PositionedLocation(Location.PlayArea(p1)), Region.Provinces,
      catalog.suitOf(lockedCard).get, 0, 0, p1))), powers)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.GlobalRestrictionsWalkerSuite"`
Expected: compile failure, `method operationRestrictions overrides nothing`.

- [ ] **Step 3: Add the power member**

In `ContributingPower.scala`, add `OperationRestriction` to the `oathdigital.model` import list, and add to `trait ContributingPower`, after `applicable`:

```scala
  /** Operation restrictions that hold wherever this power is offered: every
    * walker step checks them, and a composite is checked whole before it is
    * split (global operation restrictions design). Unlike a `Restriction`, it
    * hooks no window. None by default. */
  def operationRestrictions: Vector[OperationRestriction] = Vector.empty
```

- [ ] **Step 4: Move `WalkerPowers` to its own file and give it the set**

Cut the `WalkerPowers` case class, its doc comment and its companion object from `ProcedureWalker.scala` (the doc starting "The powers available to one `advance`/`roll`/`resolve` command" through the end of `WalkerPowers.selected`). Create `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction}
import oathdigital.model.{OperationRestriction, PowerId, PowerResolution}

/** The powers available to one `advance`/`roll`/`resolve` command (Task 3).
  * `OathRules` supplies it at command entry; `applyRecorded` (replay) never
  * takes one -- replay applies recorded ops only (spec decision 5) and must
  * never re-gather or re-transform.
  *
  * `probing` is on for every command. The restriction look-ahead turns it off
  * for the traversal it runs, so a dry run inside a probe (an `OfferHost`
  * pass) never probes in turn.
  *
  * `restrictionSet` is the catalog's global operation restrictions and
  * `modifiers` the powers selected for this command. Together with the
  * offered powers' own restrictions they make `operationRestrictions`, which
  * every walker step runs with (global operation restrictions design).
  */
final case class WalkerPowers(powers: Vector[ContributingPower],
    probing: Boolean = true,
    restrictionSet: OperationRestrictions = OperationRestrictions.none,
    modifiers: Vector[PowerId] = Vector.empty):
  /** Whether any power can reject an action. The restriction look-ahead
    * ([[WalkerPowerGather.probe]]) has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = powers.exists(_.contributions.values
    .exists(_.exists(_.isInstanceOf[Restriction])))

  /** The operation restrictions every step of this command runs with. */
  lazy val operationRestrictions: Vector[OperationRestriction] =
    restrictionSet.active(powers.flatMap(_.operationRestrictions), modifiers)

object WalkerPowers:
  val empty: WalkerPowers = WalkerPowers(Vector.empty)

  /** Powers offered to one command out of a full catalog: an `Automatic`
    * power fires unconditionally; a `PlayerSelected` power fires only when
    * its id appears in `modifiers`. Shared by `OathRules.walkerPowers`
    * (command time) and `WalkerDecisionProjector` (park-time projection) so
    * both always fold a shared window identically (Task 5 projector seam).
    * The modifiers are kept, so the active-modifier restriction sees them
    * from the action's first walk.
    */
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    catalog.copy(powers = catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)), modifiers = modifiers)
```

Keep the moved doc text verbatim where it existed; compare with `git show HEAD:src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`. Remove `ContributingPower`, `Restriction` and `PowerResolution` from `ProcedureWalker.scala`'s imports if nothing else there uses them.

- [ ] **Step 5: Run every step with the set**

In `ProcedureWalker.recordBatch`, change the pipeline call so the command's set joins the node's own restrictions (the node's go away in Task 4):

```scala
    OperationPipeline.run(ctx.state, ops, OperationPolicy.Permissive,
      ctx.powers.operationRestrictions ++ restrictions, requireAll)(
```

- [ ] **Step 6: Give the production powers the catalog's set**

In `WalkerPowerCatalog.default`, pass the set to `WalkerPowers`. The call is one expression; add the named argument after the power vector:

```scala
      EmptyGrounds.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed,
      restrictionSet = OperationRestrictions.forCatalog(catalog))
```

and import `oathdigital.gameplay.operations.OperationRestrictions`. Extend the class doc with one sentence: "`restrictionSet` gives every walker step the catalog's global operation restrictions."

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.GlobalRestrictionsWalkerSuite oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: PASS.

- [ ] **Step 8: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. A failure here is a path that discarded or moved a locked card, a Hall-protected card or an active modifier without a restriction before. Read the failing assertion against the spec's "Rules": if the new refusal is what the rules say, the test's expectation was wrong and must change with a comment saying why; otherwise stop and report it.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala src/test/scala/oathdigital/gameplay/GlobalRestrictionsWalkerSuite.scala
git commit -m "feat(walker): run every walker step with the global restrictions"
```

---

### Task 3: Composites are checked whole

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`walkComposite`, around line 398)
- Test: `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`

**Interfaces:**
- Consumes: `WalkerPowers.operationRestrictions` (Task 2), `restricting` (Task 2).
- Produces: `OperationResolution.screen(ready: ReadyGame, operation: CoreOperation, restrictions: Vector[OperationRestriction], required: Boolean): Either[OathViolation, Boolean]`.

- [ ] **Step 1: Write the failing test**

In `ProcedureWalkerSuite`, after the Task 2 test:

```scala
  /** Refuses the `Discard.Denizen` composite only, never the `Move`s it holds,
    * so it can only act if the walker checks the composite before splitting
    * it. */
  private val wholeDiscard = new OperationRestriction:
    override def reason(state: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case _: Discard.Denizen => Some(OperationReason("immune",
        "the discard is refused", OperationReasonKind.Impossible))
      case _ => None

  test("a composite in the tree is checked whole before it is split"):
    val discard = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    ProcedureWalker.advance(ready, Sequence(Vector(discard)), None,
      restricting(wholeDiscard)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    assert(ProcedureWalker.advance(ready,
      Sequence(Vector(discard.copy(required = true))), None,
      restricting(wholeDiscard)).isLeft)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.ProcedureWalkerSuite -- *checked whole*"`
Expected: FAIL: the walk records the discard's `Move`s, so the state changed.

- [ ] **Step 3: Add `OperationResolution.screen`**

In `OperationResolution`, after `resolve`:

```scala
  /** Whether `operation` may run under `restrictions` alone. The walker asks
    * it for a composite before splitting it into its children, so a
    * restriction sees `Take` or `Discard.Denizen` rather than their `Move`s
    * (global operation restrictions design, "Composites are checked whole").
    * `Right(false)` skips an optional operation a restriction refuses. A
    * refusal of a required one, or an `Invalid` refusal, rejects.
    */
  def screen(ready: ReadyGame, operation: CoreOperation,
      restrictions: Vector[OperationRestriction], required: Boolean)
      : Either[OathViolation, Boolean] =
    val refusals = restrictions.flatMap(_.reason(ready, operation))
    refusals.find(reason =>
      required || reason.kind == OperationReasonKind.Invalid) match
      case Some(reason) => Left(rejection(reason))
      case None => Right(refusals.isEmpty)
```

- [ ] **Step 4: Screen in `walkComposite`**

Replace `walkComposite`'s body:

```scala
  private def walkComposite(composite: Operation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, hooks: WalkerHooks): Either[OathViolation, Step] =
    // The composite is walked as its children, and a bare child is
    // best-effort: without this its own `required` would be lost, and an
    // unaffordable `PayCost` would shrink to what the player holds.
    val required = composite match
      case core: CoreOperation => core.required
      case _ => false
    // A fresh composite is checked whole first; a resumed one already was.
    val runs = composite match
      case core: CoreOperation if cursor.isEmpty =>
        OperationResolution.screen(ctx.state, core,
          ctx.powers.operationRestrictions, required || hooks.strict)
      case _ => Right(true)
    runs.flatMap { run =>
      if !run then Right(Done(ctx.copy(previous = Some((ctx.state, ctx.state)))))
      else walkFolded(composite.window, composite, composite.children, ctx,
        path, cursor, resume,
        if required then hooks.copy(strict = true) else hooks)
    }
```

Add `OperationResolution` to the `oathdigital.gameplay.operations` import.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.GlobalRestrictionsWalkerSuite"`
Expected: PASS.

- [ ] **Step 6: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. Handle a failure as in Task 2, Step 8.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala
git commit -m "feat(walker): check a composite whole before splitting it"
```

---

### Task 4: `DiscardRestrictions` retires

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/operations/DiscardRestrictions.scala`
- Delete: `src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala`
- Delete: `src/test/scala/oathdigital/gameplay/DiscardRestrictionsCoverageSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/CardPlayRestrictionsSuite.scala`
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:602-617` (`BuildOps`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`runBuildOps`, `recordBatch`, imports)
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala:31-33`
- Modify: `src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala:5,116-117`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala:3,6,41-43,71-78`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala:5,17,23-25`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala:3,51,89-90`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:6,216-217`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:5,29-33,344-368,378-381`
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala:70`
- Test: `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala:157-205`
- Test: `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala:143-160`
- Test: `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala:138-157`
- Test: `src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala:217-218` (comment)

**Interfaces:**
- Consumes: `OperationRestrictions.forCatalog`, `.active`, `.isLocked`, `HallOfMinisters` (Task 1); `restricting` (Task 2).
- Produces: `BuildOps(build, window)` with no `restrictions` field. `CardPlay.legalChoices` keeps its signature.

- [ ] **Step 1: Write the failing facedown tests**

Create `src/test/scala/oathdigital/gameplay/CardPlayRestrictionsSuite.scala`, carrying over `DiscardRestrictionsSuite`'s card-play tests with the facedown fix:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRestrictions}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** Card play offers only the discards the global restrictions permit: never an
  * intact edifice or a faceup locked adviser, while a facedown locked adviser
  * has no restrictions (global operation restrictions design, "Rules"). */
class CardPlayRestrictionsSuite extends munit.FunSuite:
  private val actor = p1
  /** Locked and adviser-only. */
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  private val extras = Vector("Rain Boots", "Wrestlers").map(CatalogNames.denizen(_))

  private def siteChoice(ready: ReadyGame, card: DenizenId): CardPlay.Choice =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get

  /** p1 stands at Deep Woods, the Beast Homeland, which p1 rules. It is full
    * (three cards): two plain cards and the Hall on `side`. p1 has the Beast
    * Errand Boy in hand, so it may replace one of them.
    */
  private def fullHomeland(side: EdificeSide): ReadyGame =
    val deepWoods = "Deep Woods"
    Table.start
      .pawn(p1, at = deepWoods).warbandsAt(deepWoods, p1, 1)
      .hand(p1, plainCard)
      .denizen(extras(0), at = deepWoods).denizen(extras(1), at = deepWoods)
      .edifice(hall, side, at = deepWoods)
      .ready

  test("a replacement can never be an intact edifice"):
    assertEquals(siteChoice(fullHomeland(EdificeSide.Intact), plainCard)
      .replacements.toSet, extras.toSet[CardId])

  test("a ruined edifice can be the replacement, and is discarded, not buried"):
    val ready = fullHomeland(EdificeSide.Ruined)
    assertEquals(siteChoice(ready, plainCard).replacements.toSet,
      extras.toSet[CardId] + hall)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, plainCard,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand)
      .toOption.get
    assert(planned.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!planned.exists(_.isInstanceOf[Bury]))

  /** p1 holds three advisers, the limit: two plain facedown ones and the
    * locked Sealing Ward, facedown or faceup. */
  private def fullAdvisers(lockedFacedown: Boolean): ReadyGame =
    Table.start.hand(p1, plainCard)
      .adviser(p1, extras(0), facedown = true)
      .adviser(p1, extras(1), facedown = true)
      .adviser(p1, lockedCard, facedown = lockedFacedown)
      .ready

  private def faceupReplacements(ready: ReadyGame): Set[CardId] =
    CardPlay.legalChoices(catalog, ready, actor, plainCard,
      CardPlay.Origin.TemporaryHand).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
      .replacements.toSet

  test("a faceup locked adviser is not offered as the replacement"):
    assertEquals(faceupReplacements(fullAdvisers(lockedFacedown = false)),
      extras.toSet[CardId])

  test("a facedown locked adviser can be the replacement"):
    assertEquals(faceupReplacements(fullAdvisers(lockedFacedown = true)),
      extras.toSet[CardId] + lockedCard)

  test("a faceup locked adviser named as the replacement is refused when the " +
      "play runs"):
    val ready = fullAdvisers(lockedFacedown = false)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, plainCard,
      SearchPlacement.Adviser(Orientation.FaceUp, Some(lockedCard)),
      CardPlay.Origin.TemporaryHand).toOption.get
    assert(OperationPipeline.run(ready, planned, OperationPolicy.Permissive,
      OperationRestrictions.forCatalog(catalog).active(Vector.empty,
        Vector.empty))(Right(_)).isLeft)
```

In `HornedMaskSuite`, after "a locked adviser cannot be offered for discard":

```scala
  test("a facedown locked adviser can be offered for discard"):
    val ready = giveAdviser(holding(elders, fresh), actor, locked.head,
      Orientation.FaceDown)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value,
      "denizen" -> locked.head.value)))
```

In `CardPlayProcedureSuite`, "locked full adviser area offers no adviser placement" (line 138) builds the locked advisers `Orientation.FaceDown`. Change them to `Orientation.FaceUp`, so the test keeps asserting what it names, and add beside it:

```scala
  test("a full adviser area of facedown locked advisers offers the adviser " +
      "placement, since a facedown card has no restrictions"):
    val (base, actor, card) = handState
    val current = base.game.current
    val locked = catalog.denizens.filter(
      _.restrictions == oathdigital.catalog.CardRestrictions.LockedAdviserOnly)
      .map(d => DenizenId(d.id.value)).filterNot(_ == card).take(3)
    val full = base.updateCurrent(_.copy(
      players = current.players.map(p => if p.player == actor then
        p.copy(advisers = locked.map(id =>
          DenizenState(id, Orientation.FaceDown, Tokens.empty))) else p),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(locked.contains))))
    assert(CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand).exists(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRestrictionsSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: FAIL: "a facedown locked adviser can be the replacement", the new `CardPlayProcedureSuite` test and "a facedown locked adviser can be offered for discard" fail, because `CardPlay` and Horned Mask refuse a facedown locked adviser. "a faceup locked adviser named as the replacement is refused when the play runs" errors on `.toOption.get`, because `validateAdviserReplacement` still refuses the plan before the pipeline sees it.

- [ ] **Step 3: Remove `BuildOps.restrictions`**

In `CoreOperations.scala`, `BuildOps` becomes:

```scala
final case class BuildOps(
    build: (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]],
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation
```

In `ProcedureWalker.scala`:
- `runBuildOps`: `else recordBatch(ops, contributions, ctx, path, leafLabel(build))`.
- `recordBatch`: delete the `restrictions` parameter, and run with `ctx.powers.operationRestrictions` alone.
- Remove `OperationRestriction` from the `oathdigital.model` import.

Drop the `restrictions = ...` argument and the `DiscardRestrictions` import in:
- `Dazzle.scala:31-33`: `children :+ BuildOps((ready, _) => effects(ready, ctx.activePlayer)) :+`
- `ProvingGroundsRules.scala:116-117`: `case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site)) :+`
- `HornedMask.scala:41-43`: `BuildOps((live, pending) => take(live, player, pending)),`
- `PlanDiscard.scala:23-25`: `: Operation = BuildOps((ready, _) => operations(catalog, ready, user, card))`. Also delete the doc sentence "Like every discard of a card in play, it attaches `DiscardRestrictions`." and the blank doc line before it.
- `CardPlayProcedure.scala:216-217`: delete `, restrictions = (_, _) => Vector(new DiscardRestrictions(catalog, actor))`, so the `BuildOps(...)` call closes right after its lambda's `}`.

In `ProcedureWalkerSuite`, the two tests at lines 157-205 pass `restrictions = (_, _) => Vector(immunity)` to `BuildOps`. Drop that argument and walk with `restricting(immunity)` in place of `noPowers`, in every `ProcedureWalker.advance` call of those two tests. Keep their names and assertions.

- [ ] **Step 4: Point card play and the two option filters at the set**

`CardPlay.legalChoices` (line 29):

```scala
    // A placement whose plan discards a card the global restrictions refuse
    // (a faceup locked adviser, an intact edifice, an active modifier, a site
    // an enemy's intact Hall of Ministers protects) is not a choice. A tree
    // builder holds only the catalog, so powers' own restrictions are not
    // consulted here; slice 2's search covers them.
    val restrictions = OperationRestrictions.forCatalog(catalog)
      .active(Vector.empty, ready.game.current.walkerModifiers)
    def permitted(operations: Vector[CoreOperation]): Boolean =
      operations.forall(operation =>
        restrictions.forall(_.reason(ready, operation).isEmpty))
```

and replace the `DiscardRestrictions` import with `import oathdigital.gameplay.operations.OperationRestrictions`.

`CardPlay.validateAdviserReplacement` (line 344): delete the locked check. The pipeline refuses a faceup locked adviser's discard, and a facedown one has no restrictions. The `Some(id)` case becomes:

```scala
      case Some(id) => advisers.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement adviser is not held")).flatMap { _ =>
        id match
          case world: WorldCardId => Right(Some(world))
          case _ => Left(InvalidSearchPlacement(
            "replacement adviser is not a world card"))
      }
```

Drop the now-unused `catalog` parameter of `validateAdviserReplacement` and its argument at the call site. In `validateSiteReplacement`'s comment (line 380), change "`DiscardRestrictions` decide" to "The global operation restrictions decide". Delete `LockedAdviserCannotBeDiscarded` from `GameViolation.scala:70`; nothing else produces or decodes it.

`HornedMask`: delete `lockedAdviser`, and read the shared predicate:

```scala
  private def discardable(ready: ReadyGame, actor: PlayerId)
      : Vector[AdviserState] = advisers(ready, actor).filterNot(adviser =>
    OperationRestrictions.isLocked(catalog, ready, adviser.id))
```

Imports: `oathdigital.catalog.ExecutableCatalog` (drop `CardRestrictions`), and `oathdigital.gameplay.operations.OperationRestrictions` in place of `DiscardRestrictions`.

`TwinBrother`: in `candidates`, `if definition.suit == Suit.Nomad && !OperationRestrictions.isLocked(catalog, ready, card)`. Delete the private `Locked` set. Imports: `oathdigital.catalog.ExecutableCatalog` and `oathdigital.gameplay.operations.OperationRestrictions`. Both filters stay until slice 2's search hides refused options; say so in one line of each class doc: "Its locked filter goes when the restriction search hides refused options (global operation restrictions, slice 2)."

- [ ] **Step 5: Delete `DiscardRestrictions` and fix its last users**

```bash
git rm src/main/scala/oathdigital/gameplay/operations/DiscardRestrictions.scala src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala src/test/scala/oathdigital/gameplay/DiscardRestrictionsCoverageSuite.scala
```

`OperationResolutionSuite` (lines 143-160) builds `DiscardRestrictions` for two actors. Replace both with one restriction, since the Hall now reads the discard's acting player:

```scala
    val restriction = HallOfMinisters(FirstGameSetupFixture.catalog)
```

and use `restriction` in place of `rulerRestriction`. Its `import oathdigital.gameplay.operations._` already covers `HallOfMinisters`.

In `MinorActionsSuite` (lines 217-218), change the comment to: "The lock itself, which stops a faceup adviser being discarded, is OperationRestrictionsSuite's; this is the facedown side of it."

Check nothing else names it:

Run: `grep -rn "DiscardRestrictions\|LockedAdviserCannotBeDiscarded" src`
Expected: no output.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRestrictionsSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.OperationResolutionSuite"`
Expected: PASS.

- [ ] **Step 7: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS, including `DazzleSuite`, the Proving Grounds, Horned Mask, `PlanDiscard` and card-play suites with their per-call restrictions gone. Handle a failure as in Task 2, Step 8.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/GameViolation.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/TwinBrother.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/test/scala/oathdigital/gameplay/CardPlayRestrictionsSuite.scala src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala
git commit -m "refactor(restrictions): retire DiscardRestrictions for the global set"
```

(The `git rm` in Step 5 already staged the deletions.)

---

### Task 5: The pipeline requires the set

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala:58-66`
- Modify: `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala` (`handle`, `evolve`, `evolveOperations`, `transition`)
- Modify: `src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala` (`evolve`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:59,88-97`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:220`
- Modify: every test file that calls `OperationPipeline.run`, `MinorActions.handle`/`evolve` or `StateBasedEvaluation.evolve` (the compiler lists them)
- Modify: `docs/ROADMAP.md` (the restrictions phase's slice list)
- Test: `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`

**Interfaces:**
- Consumes: `WalkerPowers.selected(...).operationRestrictions` (Task 2).
- Produces: `OperationPipeline.run(ready, operations, allowlist, restrictions: Vector[OperationRestriction], requireAll = false)(update)`; `MinorActions.handle(catalog, state, command, restrictions)`; `MinorActions.evolve(catalog, state, event, restrictions)`; `StateBasedEvaluation.evolve(catalog, state, event, restrictions)`.

- [ ] **Step 1: Write the failing test**

In `MinorActionsSuite`, after "warband moves use core operations in both directions and replay":

```scala
  test("minor actions run under the global restrictions"):
    val (base, actor, _, _, _) = ready()
    val refuseAll = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    assert(MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(actor.player, toSite = true, 2),
      Vector(refuseAll)).isLeft)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.MinorActionsSuite"`
Expected: compile failure, `too many arguments for method handle`.

- [ ] **Step 3: Thread the set through the legacy paths**

`MinorActions`: add `restrictions: Vector[OperationRestriction]` as the last parameter of `handle`, `evolve`, `transition` and `evolveOperations`, pass it along each call between them, and run with it:

```scala
    OperationPipeline.run(ready, operations, operationAllowlist,
      restrictions)(Right(_))
```

`StateBasedEvaluation.evolve`: add the same last parameter and pass it to the bandit refill's `OperationPipeline.run(ready, operations, operationAllowlist, restrictions)(Right(_))`.

`OathRules`: add, beside the other members:

```scala
  /** The global operation restrictions outside a walker action: the
    * catalog's printed ones and the automatic powers', with no modifiers,
    * since no action is running. Minor actions and state-based effects run
    * under them. */
  private lazy val outsideActions: Vector[OperationRestriction] =
    WalkerPowers.selected(walkerPowerCatalog, Vector.empty).operationRestrictions
```

and pass `outsideActions` as the new last argument at `MinorActions.handle` (line 59) and at every `MinorActions.evolve` and `StateBasedEvaluation.evolve` call (lines 88-97).

`OathRulesWalker.requirePayable` (line 220):

```scala
    else OperationPipeline.run(ready, payments, OperationPolicy.Permissive,
      WalkerPowers.selected(walkerPowerCatalog, modifiers)
        .operationRestrictions)(
```

- [ ] **Step 4: Make the argument required**

In `OperationPipeline.run`, delete `= Vector.empty` from `restrictions`, and add to the class doc: "`restrictions` has no default, so a new caller cannot run operations outside the global restrictions (global operation restrictions design)."

- [ ] **Step 5: Fix the test call sites**

Run: `./sbtw Test/compile`
Expected: compile errors at each test call of `OperationPipeline.run` without restrictions, and of `MinorActions.handle`/`evolve` and `StateBasedEvaluation.evolve`. At each one, pass `Vector.empty` as the restrictions argument: the fourth argument of `OperationPipeline.run`, the last of the others. These suites test the pipeline and replay, not restrictions. The `OperationPipeline.run` callers are in `GameEventWireSuite`, `OperationVocabularySuite`, `OperationExecutorSuite`, `OperationApplicationSuite`, `PayCostSettlementSuite`, `ProcedureWalkerSuite`, `OperationPipelineSuite`, `PowerOperationsSuite`, `RevealDiscardSuite`, `PayCostSuite`, `WalkerRecordedOpsReducer`, `WhenPlayedHarness`, `ConspiracyWhenPlayedSuite` and `DazzleSuite`. A call that already passes restrictions positionally compiles unchanged. Repeat until `Test/compile` succeeds.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.MinorActionsSuite"`
Expected: PASS.

- [ ] **Step 7: Update the roadmap**

In `docs/ROADMAP.md`, "Phase - Global operation restrictions", delete slice item 1 ("**The seam.** …") and renumber the other two to 1 and 2, keeping their text. Finished work is deleted from the roadmap, not ticked.

- [ ] **Step 8: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server test count is the baseline plus the new tests, minus the deleted `DiscardRestrictionsSuite` and `DiscardRestrictionsCoverageSuite` tests.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala src/main/scala/oathdigital/gameplay/actions/MinorActions.scala src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala src/main/scala/oathdigital/gameplay/OathRules.scala src/main/scala/oathdigital/gameplay/OathRulesWalker.scala docs/ROADMAP.md
git add $(git diff --name-only -- src/test)
git commit -m "feat(restrictions): require the restriction set at every pipeline call"
```
