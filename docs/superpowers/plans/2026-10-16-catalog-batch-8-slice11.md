# Catalog Batch 8, Slice 11 (Ancient Bloodline) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 165 Ancient Bloodline: "Your enemies act as if denizens and relics at sites you rule are **locked**."

**Architecture:**

- **A move names its mover.** `Move` gains `by: Option[PlayerId] = None`.
  - Each composite that names a player fills it on its child moves: `Take.player`, `Give.giver` and `Discard.*.actingPlayer`. `Exchange` and `Draw` are built from `Give` and `Take`, so they inherit it.
  - `Swap` gains its own `by` for its two moves.
  - The six bare moves of a site card set it from their user: Recover, `SiteRelicTake` (Skeleton Key, Barbed Net), Horned Mask, `SiteSlotMoves` (Giant Python, Roving Terror), Moving Market and Great Herd.
  - `Move` requires a mover for a card moved from a site. Every suite then guards against a site-card move that forgets its actor.
  - The codec writes `by` only when present. It refuses a site-card move without one.
  - This follows the product owner's rule: anything an `OperationRestriction` needs must be on the operation tree, or derivable from it. The restriction's signature does not change.
- **Bloodline extends the existing lock.** `LockedCards`, the restriction for every lock-icon card (the catalog's `Locked` trait), gains a seam: `AsIfLocked`, a rule under which cards act as if locked for some players only.
  - `LockedCards` flattens an operation once. It refuses a lock-icon card first, then asks each `AsIfLocked` rule about every card moved or flipped, with where it stands and who moves it.
  - `AncientBloodline` is the one `AsIfLocked` rule. It lives in `gameplay/operations` beside the Hall of Ministers, its closest relative. It is printed, so it holds wherever operations run: walker steps, screens, and card play's `CardSlots.judge`.
- **The holder may consent.** Each refusal carries an `OperationWaiver` whose granter is the faceup holder. A refused option is therefore askable of the holder, or shown with the plain reason when nobody may be asked.
- **Where the reason shows.**
  - Recover judges each relic with `CardSlots.judge`, as card play judges a slot.
  - Skeleton Key, Barbed Net and Horned Mask make their take required. The walker's askable search then marks the refused option.
  - Giant Python, Roving Terror and card play already judge their slots with `CardSlots.judge`.
  - Moving Market and Great Herd already require their move.
  - Dazzle and Threatening Roar skip a refused discard, as they skip one the Hall of Ministers refuses.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "The bar" (the slice 11 bullet), "Slice 11: Actor-aware lock" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 11 entry, which Task 2 extends with this plan's rulings.
- `docs/superpowers/specs/2026-10-08-consent-system-design.md`: "Scope" ruling 1 (waivable powers) and the operation path (`ConsentedRestriction`, `OperationConsent`).
- `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala`: the pattern `AncientBloodline` follows.

**Starts from:** `main` at `c67d2aa4` ("slice 10 done") or later. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - `gameplay/operations` may import `catalog`, as `HallOfMinisters` does.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** A journal written before this plan need not decode.
- **Lessons from earlier slices' tests:**
  - To read a parked decision, match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`. The case's first field is the procedure, not the decision id.
  - A choice with nothing usable or askable to offer is not asked. A test that expects a refused option needs the question to stay open. Every test below keeps one askable or usable option.
  - Board a site's ruler with `warbandsAt(site, player, 1)`, and assert the ruler (`PowerAccess.ruledSites`) before relying on it.
- **Note keys** are declared through a `NoteSet`, as plain `val`s after the set. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`. Check that the diff adds only the two keys Task 2 names.
- **Words, exactly.** A note's reason renders as "{card}: {text}", so the hover reasons read:
  - "Ancient Bloodline: Locked." when nobody may be asked;
  - "Ancient Bloodline: Locked and needs the holder's consent." when the holder may be asked.
  - Card play's plain fallback (`CardSlots.reasons`) uses the same "Ancient Bloodline: Locked." text.
- **Names, exactly:**
  - `Move.by`, `Swap.by`, and `Move.fromSite(piece, from): Boolean` on the `Move` companion.
  - `trait AsIfLocked` and `LockedCards(cards, asIf)` in `gameplay/operations/LockedCards.scala`.
  - `case object AncientBloodline extends AsIfLocked` in `gameplay/operations/AncientBloodline.scala`, with `id`, `source`, `moveKey = "operation.move"`, `notes`, `locked`, `lockedConsent`, `granter` and `refusal`.
  - The refusal code `"bloodline-locked"`.
  - `OperationRestrictions.implementedRules: Set[PowerId]`.

---

### Task 1: A move names its mover

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`Move`, `Swap`, `Take`, `Give`, `Discard`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (move and swap, both ways)
- Modify, to add the mover: `gameplay/actions/recover/RecoverProcedure.scala`, `gameplay/powers/action/SiteRelicTake.scala`, `gameplay/powers/wake/HornedMask.scala`, `gameplay/powers/action/SiteSlotMoves.scala`, `gameplay/powers/economy/MovingMarket.scala`, `gameplay/powers/whenplayed/GreatHerd.scala` (all under `src/main/scala/oathdigital/`)
- Modify, for the extractor's new field: every `case Move(a, b, c, d)` and `case Swap(a, b, c, d)` pattern. In main, these are:
  - `WalkerOperationCodec.scala:94,108`
  - `StateBasedOperationPolicy.scala:12`
  - `VowOfWandering.scala:125`
  - `TravelSitePowers.scala:100,110`
  - `OperationStateWrites.scala:25`
  - `OperationPipeline.scala:33`
  - `GrandScepter.scala:44`
  - `LockedCards.scala:25`
  - `OperationDetails.scala:22`
  - `DetailLines.scala:114,119,122,145`
  - `ActionLines.scala:110,361`
  - `BrassArmyMoves.scala:98`
  - `ResourceOperations.scala:65,68`
  - `WalkerEvents.scala:35`

  The compiler names the test sites.
- Create: `src/test/scala/oathdigital/model/MoveActorSuite.scala`
- Modify: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces:
  - `Move(piece, from, to, resultingOrientation = None, by: Option[PlayerId] = None)`
  - `object Move { def fromSite(piece: Piece, from: PositionedLocation): Boolean }`
  - `Swap(firstCard, firstLocation, secondCard, secondLocation, by: Option[PlayerId] = None)`
  - Every card move a `Take`, `Give` or `Discard.Denizen/Relic/RuinedEdifice` builds names its player in `by`.

- [ ] **Step 1: Write the failing model tests**

Create `src/test/scala/oathdigital/model/MoveActorSuite.scala`:

```scala
package oathdigital.model

import oathdigital.testkit.Table.{p1, p2}

/** A move names the player who moves it (catalog batch 8, slice 11): the
  * player its composite names, or the one its builder gives. A card moved
  * from a site must name one. */
class MoveActorSuite extends munit.FunSuite:
  private val site = SiteId("site")
  private val atSite = PositionedLocation(Location.Site(site))
  private val atOtherSite = PositionedLocation(Location.Site(SiteId("other")))
  private val relic = RelicId("R1")
  private val denizen = DenizenId("D1")

  /** The mover of each card move `operation` makes, in order. */
  private def cardMovers(operation: Operation): Vector[Option[PlayerId]] =
    Operation.flatten(operation).collect {
      case move: Move if move.piece.isInstanceOf[Piece.Card] => move.by
    }

  test("a composite that names its player names it on the card it moves"):
    assertEquals(cardMovers(Take(Piece.Card(relic), p1, Location.Site(site),
      Location.PlayArea(p1))), Vector(Some(p1)))
    assertEquals(cardMovers(Give(Piece.Card(relic), p1,
      Location.PlayArea(p1), Location.PlayArea(p2))), Vector(Some(p1)))
    assertEquals(cardMovers(Discard.Denizen(denizen, atSite,
      Region.Provinces, Suit.Beast, 0, 0, p1)), Vector(Some(p1)))
    assertEquals(cardMovers(Discard.Relic(relic, atSite, 0, p1)),
      Vector(Some(p1)))
    assertEquals(cardMovers(Discard.RuinedEdifice(EdificeId("E1"), atSite,
      Suit.Beast, 0, 0, p1)), Vector(Some(p1)))

  test("a swap names its mover on both of its moves"):
    assertEquals(cardMovers(Swap(denizen, atSite, DenizenId("D2"),
      atOtherSite, by = Some(p1))), Vector(Some(p1), Some(p1)))

  test("a card moved from a site must name its mover; a move from " +
      "elsewhere need not"):
    intercept[IllegalArgumentException](Move(Piece.Card(relic), atSite,
      PositionedLocation(Location.PlayArea(p1))))
    assertEquals(Move(Piece.Card(relic), PositionedLocation(
      Location.PlayArea(p1)), atSite).by, None)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.model.MoveActorSuite"`
Expected: compile failure. `Move` has no `by`, and `Swap` has no `by` parameter.

- [ ] **Step 3: Add the mover to the model**

In `CoreOperations.scala`:

- `Move`. Add the field and the require, and replace the doc comment:

```scala
/** Changes a piece's location or role within one play area. `by` is the
  * player who moves it, where a rule names one (catalog batch 8, slice 11):
  * a restriction that holds against some players only reads it off the
  * operation tree. A card moved from a site always names one.
  */
final case class Move(
    piece: Piece,
    from: PositionedLocation,
    to: PositionedLocation,
    resultingOrientation: Option[Orientation] = None,
    by: Option[PlayerId] = None)
    extends PrimitiveOperation:
  // ... the existing requires, unchanged ...
  require(by.isDefined || !Move.fromSite(piece, from),
    "a card moved from a site names who moves it")

object Move:
  /** Whether `piece` is a card leaving a site: a move that must name its
    * mover. */
  def fromSite(piece: Piece, from: PositionedLocation): Boolean =
    piece.isInstanceOf[Piece.Card] &&
      from.location.isInstanceOf[Location.Site]
```

- `Take.move`: `Move(piece, PositionedLocation(from, sourcePosition, sourceSide), PositionedLocation(to), by = Some(player))`.
- `Give.move`: `Move(piece, PositionedLocation(from), PositionedLocation(to), by = Some(giver))`.
- `Discard`. The card move of each case names `actingPlayer`. `Vision` names none: a Vision never stands at a site.
  - `discardWorld(card, from, destination, by: Option[PlayerId])` passes `by` to its `Move`.
  - `Denizen` calls it with `Some(actingPlayer)`, and `Vision` with `None`.
  - `RuinedEdifice` and `Relic` add `by = Some(actingPlayer)` to their card `Move`.
  - The returns (favor and secrets) stay as they are.
- `Swap`. Add the field, and pass it to both moves:

```scala
/** Swaps two cards by moving each one to the other's location. `by` is the
  * player who swaps them. */
final case class Swap(firstCard: CardId, firstLocation: PositionedLocation,
    secondCard: CardId, secondLocation: PositionedLocation,
    by: Option[PlayerId] = None)
    extends CoreOperation:
  // ... the existing requires, unchanged ...
  val first: Move =
    Move(Piece.Card(firstCard), firstLocation, secondLocation, by = by)
  val second: Move =
    Move(Piece.Card(secondCard), secondLocation, firstLocation, by = by)
```

- [ ] **Step 4: Give the six bare site-card moves their mover**

Each adds `by = Some(actor)` to its `Move`, where `actor` is the player already in scope:

- `RecoverProcedure.recovery`'s `moveRelic`: `actor`.
- `SiteRelicTake.take`: its `actor` parameter.
- `HornedMask.take`: its `actor` parameter.
- `SiteSlotMoves.operations`: its `actor` parameter.
- `MovingMarket.move` has no actor today. Give it one: `move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)`, called as `BuildOps((live, answered) => move(live, actor, answered), required = true)` from `offer(actor)`.
- `GreatHerd.swap` and `swapWith` have no actor today. Thread `actor` from `effect` through `swap(ready, actor, pending)` and `swapWith(ready, actor, ref)`, and build `Swap(..., by = Some(actor))`.

- [ ] **Step 5: Fix every extractor pattern**

Add a trailing `_` to each `case Move(a, b, c, d)` pattern listed under **Files**, and to `case Swap(a, b, c, d)` in the codec. Where a pattern binds the orientation by name, keep it; the new field is `_`.

Run: `./sbtw "Test/compile"`
Expected: it compiles once the test sites the compiler names get the same `_`.

- [ ] **Step 6: Carry the mover on the wire**

In `WalkerOperationCodec.scala`:

- Encode `Move` as today. When `by` is defined, add `"by" -> by.value`, as `Take` adds `leaving` only when present.
- Encode `Swap` the same way, with `"by"` only when present.
- Decode both with `by = value.obj.get("by").flatMap(_.strOpt).map(PlayerId(_))`.
- Before building a `Move`, refuse a site card without a mover: `_ <- Either.cond(by.isDefined || !Move.fromSite(piece, from), (), InvalidValue(path, "a card moved from a site names who moves it"))`. The constructor's `require` must never throw while decoding. A swap's moves both start at a location, so refuse a swap without `by` when either location is a site.

Add to `WalkerOperationWireSuite.scala`:

```scala
  test("a move names its mover on the wire, and a site card's move " +
      "without one does not decode"):
    val move = Move(Piece.Card(RelicId("R1")),
      PositionedLocation(Location.Site(SiteId("site"))),
      PositionedLocation(Location.PlayArea(Table.p1)),
      resultingOrientation = Some(Orientation.FaceDown), by = Some(Table.p1))
    val event = recordedOperation("recover", move)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val stripped = ujson.read(ujson.write(encoded))
    stripped("payload")("ops")(0).obj.remove("by")
    assert(GameEventWire.decode(stripped).isLeft)

  test("a swap names its mover on the wire"):
    val swap = Swap(DenizenId("D1"),
      PositionedLocation(Location.Site(SiteId("here"))), DenizenId("D2"),
      PositionedLocation(Location.Site(SiteId("there"))),
      by = Some(Table.p1))
    val event = recordedOperation("swap", swap)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
```

Read how this suite's other tests pass `encoded` to `GameEventWire.decode` and to `ujson.read`, and match them. If `encoded` is a `String` there, use `ujson.read(encoded)` for `stripped` and `ujson.write(stripped)` for the decode.

- [ ] **Step 7: Run the whole suite and name every missing mover**

Run: `./sbtw test`

Two kinds of failure are expected; fix both:

- **"a card moved from a site names who moves it".** A site-card move without a mover.
  - In a test's hand-built operation, name the player the test acts as.
  - In production, name the player whose action or power makes the move.
  - If a rule with no player moves a site card, stop and report it. This plan rules that no such move exists.
- **An expected `Move` that now differs only in `by`.** For example, `RecoverProcedureSuite:164`, `GameApplicationServiceRecoverSuite:150` and `WalkerOperationWireSuite:75`. Add the mover the production code now names.

Expected after the fixes: everything passes, with the 5 new tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala \
  src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala \
  src/test/scala/oathdigital/model/MoveActorSuite.scala \
  src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
# plus every other file Steps 4, 5 and 7 touched, each named explicitly
git commit -m "feat(batch8): a move names the player who moves it"
```

---

### Task 2: Ancient Bloodline's lock

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/LockedCards.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/AncientBloodline.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` (`printedBy`, `implementedRules`, the class doc)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala` (`default`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala` (`all`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala` (`reasons`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerImplementationStatus.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/operations/AncientBloodlineSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 11)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", the slice 11 bullet)

**Interfaces:**
- Consumes: `Move.by` and `Swap.by` (Task 1).
- Produces:
  - `trait AsIfLocked { def refusal(ready: ReadyGame, card: CardId, at: Location, by: Option[PlayerId]): Option[OperationReason] }`
  - `LockedCards(cards: Set[CardId], asIf: Vector[AsIfLocked] = Vector.empty)`
  - `AncientBloodline.id: PowerId`, `source: PowerSourceRef`, `moveKey: String`, `locked: NoteKey`, `lockedConsent: NoteKey`, `granter(ready): Option[PlayerId]`
  - A refusal with code `"bloodline-locked"`, kind `Impossible`, and an `OperationWaiver(AncientBloodline.id, Some(holder), ConsentKey(AncientBloodline.id, mover, "operation.move", cardRef), lockedConsent(source), locked(source))`.

- [ ] **Step 1: Write the failing restriction tests**

Create `src/test/scala/oathdigital/gameplay/operations/AncientBloodlineSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{ConsentPowers, PowerImplementationStatus}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Ancient Bloodline's lock (card 165, catalog batch 8, slice 11): an
  * enemy of its faceup holder may not move a card at a site the holder
  * rules, unless the holder consents. */
class AncientBloodlineSuite extends munit.FunSuite:
  private val bloodline = CatalogNames.denizen("Ancient Bloodline")
  private val woods = CatalogNames.site("Deep Woods")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val sticks = CatalogNames.relic("Dowsing Sticks")
  private val lock = LockedCards(Set.empty, Vector(AncientBloodline))

  /** Errand Boy and Dowsing Sticks at Deep Woods, which `ruled` gives its
    * ruler, beside p2 holding Ancient Bloodline faceup unless `facedown`. */
  private def woodsBesideBloodlineHolder(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1),
      facedown: Boolean = false): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = woods)
      .denizen(errandBoy, at = woods).relicAt(sticks, at = woods)
      .adviser(p2, bloodline, facedown)).ready

  private def sticksTakenBy(mover: PlayerId): Move = Move(
    Piece.Card(sticks), PositionedLocation(Location.Site(woods)),
    PositionedLocation(Location.PlayArea(mover)),
    resultingOrientation = Some(Orientation.FaceDown), by = Some(mover))

  private def errandBoyDiscardedBy(mover: PlayerId): Discard.Denizen =
    Discard.Denizen(errandBoy, PositionedLocation(Location.Site(woods)),
      Region.Provinces, Suit.Beast, 0, 0, mover)

  private val coversSticksForP1 = ConsentKey(AncientBloodline.id, p1,
    AncientBloodline.moveKey, DecisionOptionRef.Relic(sticks))

  test("an enemy's take of a relic at the holder's site is refused, " +
      "waivable by the holder"):
    val ready = woodsBesideBloodlineHolder()
    assert(PowerAccess.ruledSites(ready, p2).contains(woods))
    val refusal = lock.reason(ready, sticksTakenBy(p1))
    assertEquals(refusal.map(_.code), Some("bloodline-locked"))
    assertEquals(refusal.flatMap(_.waiver), Some(OperationWaiver(
      AncientBloodline.id, Some(p2), coversSticksForP1,
      AncientBloodline.lockedConsent(AncientBloodline.source),
      AncientBloodline.locked(AncientBloodline.source))))

  test("an enemy's discard of a denizen there is refused too, and a bury " +
      "goes through"):
    val ready = woodsBesideBloodlineHolder()
    assertEquals(lock.reason(ready, errandBoyDiscardedBy(p1)).map(_.code),
      Some("bloodline-locked"))
    assertEquals(lock.reason(ready, Bury(BuryableCard.Denizen(errandBoy),
      PositionedLocation(Location.Site(woods)))), None)

  test("the holder moves the card freely"):
    assertEquals(lock.reason(woodsBesideBloodlineHolder(),
      sticksTakenBy(p2)), None)

  test("a facedown Bloodline, or a site the holder does not rule, locks " +
      "nothing"):
    assertEquals(lock.reason(woodsBesideBloodlineHolder(facedown = true),
      sticksTakenBy(p1)), None)
    assertEquals(lock.reason(woodsBesideBloodlineHolder(
      _.bandits(woods, 2)), sticksTakenBy(p1)), None)

  test("a card arriving at the holder's site is not refused"):
    val ready = woodsBesideBloodlineHolder()
    val wrestlers = CatalogNames.denizen("Wrestlers")
    assertEquals(lock.reason(ready, Move(Piece.Card(wrestlers),
      PositionedLocation(Location.PlayArea(p1)),
      PositionedLocation(Location.Site(woods)), by = Some(p1))), None)

  test("a grant remembered for the card lifts the lock, and the holder " +
      "is the granter"):
    val ready = woodsBesideBloodlineHolder()
    val granted = ready.copy(consent = ConsentState(answers = Map(
      coversSticksForP1 -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn.of(ready))))
    assertEquals(ConsentedRestriction(lock).reason(granted,
      sticksTakenBy(p1)), None)
    assertEquals(ConsentPowers.default(catalog)
      .granter(ready, AncientBloodline.id), Some(p2))

  test("the printed set holds the lock, and the card counts as " +
      "implemented"):
    val printed = OperationRestrictions.forCatalog(catalog).printed
    assert(printed.exists {
      case locked: LockedCards => locked.asIf.contains(AncientBloodline)
      case _ => false
    })
    assert(PowerImplementationStatus.implemented(catalog)(
      AncientBloodline.id))
```

Read `ConsentAnswer` for how a remembered answer names its giver. `HallOfMinistersConsentSuite.remembering` writes it as `answer.by(p2)`.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.AncientBloodlineSuite"`
Expected: compile failure. `AncientBloodline` and `LockedCards.asIf` do not exist.

- [ ] **Step 3: Open `LockedCards` to as-if locks**

Replace the body of `LockedCards.scala` below its imports:

```scala
/** A rule under which cards act as if locked for some players only: Ancient
  * Bloodline (catalog batch 8, slice 11). It judges each card an operation
  * moves or flips, with where the card stands and who moves it. */
trait AsIfLocked:
  /** Why `by` may not move or flip `card` from `at`, if it may not. */
  def refusal(ready: ReadyGame, card: CardId, at: Location,
      by: Option[PlayerId]): Option[OperationReason]

/** The cards with the lock icon cannot be moved or flipped while they show the
  * lock (global operation restrictions design, "Rules"). A discard, a `Take`,
  * a `Give` and a `Swap` all move the card, so each is refused; `Bury`
  * ignores locked and is never refused. A facedown card has no restrictions,
  * and a card in a hand or a deck is not in play.
  *
  * `asIf` are the rules that lock cards for some players only. A lock-icon
  * card's refusal comes first, since no consent lifts it.
  *
  * One restriction holds every lock, so an operation is flattened once
  * however many cards are locked.
  */
final case class LockedCards(cards: Set[CardId],
    asIf: Vector[AsIfLocked] = Vector.empty) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    val moved = LockedCards.touched(operation)
    moved.collectFirst {
      case (card, _, _) if cards.contains(card) &&
          LockedCards.showing(ready, card) =>
        OperationReason("locked", s"${card.value} is locked",
          OperationReasonKind.Impossible)
    }.orElse(moved.iterator.flatMap((card, at, by) =>
      asIf.iterator.flatMap(_.refusal(ready, card, at, by))).nextOption())

object LockedCards:
  /** The cards `operation` moves or flips, where each stands, and who
    * moves it. A flip names nobody: no rule flips a card at a site. */
  private def touched(operation: CoreOperation)
      : Vector[(CardId, Location, Option[PlayerId])] =
    Operation.flatten(operation).collect {
      case move @ Move(Piece.Card(moved), from, _, _, _) =>
        (moved, from.location, move.by)
      case Flip(flipped, at, _) => (flipped, at, None)
    }

  // `showing`, unchanged.
```

- [ ] **Step 4: Write `AncientBloodline`**

Create `src/main/scala/oathdigital/gameplay/operations/AncientBloodline.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.catalog.holding.AncientBloodlineCard
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Ancient Bloodline (card 165, adviser-only): "Your enemies act as if
  * denizens and relics at sites you rule are locked."
  *
  * While a player holds it faceup, every other player is their enemy, and
  * every card at a site the holder rules is locked for that enemy. The
  * enemy cannot move it, so cannot take, give, swap or discard it. A bury
  * still goes through, as for every lock. The mover is the move's own
  * `by`. A card played to such a site, or a relic that arrives there, is
  * not refused; it is locked once it stands there.
  *
  * The holder may consent outside a Campaign (consent design, ruling 1).
  * A grant covers that card's move by that player for the turn.
  *
  * An Imperial holder rules through the Empire, which the engine does not
  * model yet: `PowerAccess.ruledSites` counts only a player ruler, so such
  * a holder protects nothing. That case is to be handled with the Imperial
  * rules.
  */
case object AncientBloodline extends AsIfLocked:
  val id: PowerId = AncientBloodlineCard.power.id
  val source: PowerSourceRef = PowerSourceRef.Card(AncientBloodlineCard.id)

  /** The decision slot of a grant's key. The grant covers a card's move,
    * which the operation check sees without its decision, so the key names
    * the move instead. */
  val moveKey: String = "operation.move"

  /** The keys this rule owns. An operation restriction is not a power,
    * so `RuleNotes` declares it for the formatter. */
  val notes: NoteSet = NoteSet.unowned("AncientBloodline")
  /** "Locked.": the reason of a card nobody may be asked about. */
  val locked: NoteKey = notes.key("locked", Vector(NotePart.Text(
    "Locked.")))
  /** "Locked and needs the holder's consent.": the reason of a card its
    * holder may be asked about. */
  val lockedConsent: NoteKey = notes.key("locked-consent", Vector(
    NotePart.Text("Locked and needs the holder's consent.")))

  /** The player who holds Ancient Bloodline faceup, who may consent. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.collectFirst {
      case player if player.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) =>
          card == AncientBloodlineCard.id
        case _ => false
      } => player.player
    }

  def refusal(ready: ReadyGame, card: CardId, at: Location,
      by: Option[PlayerId]): Option[OperationReason] =
    for
      site <- Some(at).collect { case Location.Site(site) => site }
      mover <- by
      holder <- granter(ready)
      if mover != holder
      if PowerAccess.ruledSites(ready, holder).contains(site)
      ref <- cardRef(card)
    yield OperationReason("bloodline-locked",
      s"${card.value} is locked for ${mover.value}",
      OperationReasonKind.Impossible, Some(OperationWaiver(id, Some(holder),
        ConsentKey(id, mover, moveKey, ref), lockedConsent(source),
        locked(source))))

  private def cardRef(card: CardId): Option[DecisionOptionRef] = card match
    case denizen: DenizenId => Some(DecisionOptionRef.Denizen(denizen))
    case relic: RelicId => Some(DecisionOptionRef.Relic(relic))
    case edifice: EdificeId => Some(DecisionOptionRef.Edifice(edifice))
    case _ => None
```

Check `PlayerState`'s field names (`advisers`, `player`) against `HallOfMinisters.reason`, which reads them. Check `DenizenState`'s fields in `model/Cards.scala`.

- [ ] **Step 5: Register it**

- `OperationRestrictions.printedBy`: build `LockedCards(locked, Vector(AncientBloodline))`. In the class doc, add Ancient Bloodline to the printed list: "[[LockedCards]], holding every card whose `Locked` trait shows the lock icon and the cards [[AncientBloodline]] locks for its holder's enemies".
- `object OperationRestrictions`: add

```scala
  /** The printed powers an operation restriction implements outright,
    * with no walker power: they count as implemented
    * (`PowerImplementationStatus`). */
  val implementedRules: Set[PowerId] = Set(AncientBloodline.id)
```

- `PowerImplementationStatus.implemented`: add `OperationRestrictions.implementedRules` to `covered`, with the import `oathdigital.gameplay.operations.OperationRestrictions`.
- `ConsentPowers.default`: add `AncientBloodline.id -> Waiver(AncientBloodline.granter, AncientBloodline.source, (_, _) => AncientBloodline.lockedConsent(AncientBloodline.source))` beside the Hall's entry. Extend the class doc's last sentence: "and the operation restrictions in `operations` (the Hall of Ministers, slice 3; Ancient Bloodline, catalog batch 8, slice 11)".
- `RuleNotes.all`: add `AncientBloodline.id -> Vector(AncientBloodline.locked, AncientBloodline.lockedConsent)`.
- `CardSlots.reasons`: add `"bloodline-locked" -> "Ancient Bloodline: Locked."`.

- [ ] **Step 6: Run the suite, then regenerate the note keys**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.AncientBloodlineSuite"`
Expected: 7 tests pass.

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: the diff of `declared-keys.txt` adds exactly the `locked` and `locked-consent` keys of `AncientBloodline`.

- [ ] **Step 7: Record the rulings**

In the rulings appendix, replace the slice 11 entry's sub-bullets with:

```markdown
- **165 Ancient Bloodline.** "Your enemies act as if denizens and relics at
  sites you rule are locked."
  - Ancient Bloodline must be faceup. Enemies are every player but its
    holder. A site counts when the holder rules it as a player. An Imperial
    holder waits for the Imperial rules.
  - For an enemy, every card at those sites is locked: denizens, edifices
    and relics. The enemy cannot move it, so cannot take, give, swap or
    discard it. Bury still goes through, as for every lock.
  - A card played to those sites, or a relic arriving there, is not
    refused. It is locked once it is there.
  - Site relics are never Raid targets. In practice, the lock stops an
    enemy from Recovering a relic at those sites, or taking it with Skeleton
    Key or Barbed Net. It also stops an enemy from taking a denizen there
    (Horned Mask), moving one away (Giant Python, Roving Terror, Moving
    Market, Great Herd) or discarding one (card play's Replace, Dazzle,
    Threatening Roar, a battle plan's discard).
  - The holder may consent outside a Campaign (consent design, ruling 1).
    Inside a Campaign the lock holds.
  - Hover reasons: "Ancient Bloodline: Locked." and "Ancient Bloodline:
    Locked and needs the holder's consent."
  - The mover is named on the operation itself (ruled 2026-10-16). Each
    move names its player (`Move.by`), and a card moved from a site always
    does. A restriction reads the mover off the operation tree.
```

In the design's "The bar", replace the slice 11 bullet with:

```markdown
- **An actor-aware operation restriction** (slice 11, settled at plan
  time). A move names the player who moves it (`Move.by`). A `Take`,
  `Give`, `Discard` or `Swap` fills it from its own player, and a bare move
  from its builder's actor. A card moved from a site always names one. An
  `OperationRestriction` reads the mover off the operation tree, so its
  signature does not change. Ancient Bloodline joins `LockedCards` as an
  as-if lock (`AsIfLocked`).
```

Run: `python3 scripts/check-markdown-links.py`
Expected: it passes.

- [ ] **Step 8: Run the whole suite and commit**

Run: `./sbtw test`
Expected: everything passes.

```bash
git add src/main/scala/oathdigital/gameplay/operations/LockedCards.scala \
  src/main/scala/oathdigital/gameplay/operations/AncientBloodline.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala \
  src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala \
  src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala \
  src/main/scala/oathdigital/gameplay/powers/PowerImplementationStatus.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/scala/oathdigital/gameplay/operations/AncientBloodlineSuite.scala \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md
git commit -m "feat(batch8): Ancient Bloodline locks its holder's sites for enemies"
```

---

### Task 3: A locked relic or denizen shows why

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SiteRelicTake.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`
- Create: `src/test/scala/oathdigital/gameplay/operations/AncientBloodlineTakesSuite.scala`

**Interfaces:**
- Consumes: `AncientBloodline` (Task 2) and `CardSlots.judge(catalog, ready, actor, option, planned, picked = Vector.empty): Option[DecisionOption]`.
- Produces: nothing new. The Recover relic question, the Skeleton Key and Barbed Net relic question, and the Horned Mask denizen question now show a locked card askable or unavailable with its reason.

**Why two mechanisms:**
- **Recover** judges its relics with `CardSlots.judge`, as card play judges its slots. Recover owns its catalog. Its relic move shares a batch with the `RecoverAfterRelic` window, where Relic Worship adds `GainSupply`. Making that batch required would reject the whole Recover when the gain is capped.
- **Skeleton Key, Barbed Net and Horned Mask** have no window on their take. Making the take `required = true` lets the walker's askable search mark the refused option, as Moving Market's and Great Herd's required moves already do.

- [ ] **Step 1: Write the failing walker tests**

Create `src/test/scala/oathdigital/gameplay/operations/AncientBloodlineTakesSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.{OathRules, PowerAccess}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.action.SkeletonKey
import oathdigital.gameplay.powers.wake.HornedMask
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Ancient Bloodline on the questions that take a card from a site
  * (catalog batch 8, slice 11): Recover, Skeleton Key and Horned Mask
  * offer a card its holder locks askable of the holder. */
class AncientBloodlineTakesSuite extends munit.FunSuite:
  import DefenseDieFace.TwoShields

  private val bloodline = CatalogNames.denizen("Ancient Bloodline")
  private val askedReason = Some("Ancient Bloodline: Locked and needs the " +
    "holder's consent.")
  private val askingP2 = Some(ConsentAskProjection(Vector("p2"),
    Vector("Ancient Bloodline")))

  /** `ready` with the answer `answer` remembered, given by p2, for
    * `covers`. */
  private def rememberingP2(ready: ReadyGame, covers: ConsentKey,
      answer: ConsentAnswer): ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers ->
      answer.by(p2)), turn = Some(ConsentTurn.of(ready))))

  /** The row for `ref` in the question `t` is parked on, as p1 sees it. */
  private def rowFor(t: OathTransition, ref: DecisionOptionRef)(
      using munit.Location) =
    present(PhasePowerHarness.queryOf(t, p1).toVector
      .flatMap(_.offeredOptions).find(_.id == ref.wireId), ref.wireId)

  // Recover: Fair Isle's difficulty is 3, and two shields twice make 4.
  private val isle = CatalogNames.site("Fair Isle")
  private val sticks = CatalogNames.relic("Dowsing Sticks")
  private val sticksRef = DecisionOptionRef.Relic(sticks)
  private val recoverRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = scripted(Vector(TwoShields, TwoShields)))

  /** p1's Act at Fair Isle with Dowsing Sticks there, which `ruled` gives
    * its ruler, and p2 holding Ancient Bloodline faceup. */
  private def sticksAtIsleBesideBloodline(
      ruled: Table => Table = _.warbandsAt(isle, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = isle)
      .relicAt(sticks, at = isle).adviser(p2, bloodline)).ready

  private def recoveredUpToTheRelic(ready: ReadyGame): OathTransition =
    accepted(recoverRules.startWalker(Ready(ready), ActionRef.Recover, p1))

  private val coversSticks = ConsentKey(AncientBloodline.id, p1,
    AncientBloodline.moveKey, sticksRef)

  test("Recover offers a relic the holder locks askable of the holder"):
    val ready = sticksAtIsleBesideBloodline()
    assert(PowerAccess.ruledSites(ready, p2).contains(isle))
    val t = recoveredUpToTheRelic(ready)
    assert(PhasePowerHarness.awaits(t, RecoverProcedure.relicDecisionId))
    val row = rowFor(t, sticksRef)
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)
    assertEquals(row.consent, askingP2)

  test("after the holder refuses, the relic shows the plain reason"):
    val t = recoveredUpToTheRelic(rememberingP2(
      sticksAtIsleBesideBloodline(), coversSticks, ConsentAnswer.Refused))
    val row = rowFor(t, sticksRef)
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Ancient Bloodline: Locked."))

  test("a grant opens the relic, and p1 recovers it facedown"):
    val t = recoveredUpToTheRelic(rememberingP2(
      sticksAtIsleBesideBloodline(), coversSticks, ConsentAnswer.Granted))
    assertEquals(rowFor(t, sticksRef).state, "available")
    val done = accepted(recoverRules.resolveWalker(t.state, p1,
      RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(sticksRef)))
    assert(Look(PhasePowerHarness.after(done)).player(p1).relics.exists(
      relic => relic.id == sticks &&
        relic.orientation == Orientation.FaceDown))

  test("the holder's own Recover takes the relic freely"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = isle)
      .relicAt(sticks, at = isle).adviser(p1, bloodline)
      .warbandsAt(isle, p1, 1).ready
    assertEquals(rowFor(recoveredUpToTheRelic(ready), sticksRef).state,
      "available")

  test("Skeleton Key offers a relic the holder locks askable of the " +
      "holder"):
    import PhasePowerHarness.{awaits, use}
    val key = CatalogNames.relic("Skeleton Key")
    val dunes = CatalogNames.site("Dunes")
    val whistle = CatalogNames.relic("Whistle")
    val ready = Table.start.turn(p1, Phase.Act).relic(p1, key)
      .secrets(p1, 2).pawn(p1, dunes).relicAt(whistle, at = dunes)
      .warbandsAt(dunes, p2, 1).adviser(p2, bloodline).ready
    assert(PowerAccess.ruledSites(ready, p2).contains(dunes))
    val t = accepted(use(ready, SkeletonKey, DecisionOptionRef.Relic(key)))
    assert(awaits(t, SkeletonKey.decisionId))
    val row = rowFor(t, DecisionOptionRef.Relic(whistle))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)

  test("Horned Mask offers a denizen the holder locks askable of the " +
      "holder"):
    import PhasePowerHarness.{awaits, use}
    val mask = CatalogNames.relic("Horned Mask")
    val home = CatalogNames.site("Ancient City")
    val inn = CatalogNames.denizen("Wayside Inn")
    val ready = Table.start.turn(p1, Phase.Wake).pawn(p1, home)
      .relic(p1, mask).denizen(inn, at = home)
      .warbandsAt(home, p2, 1).adviser(p2, bloodline).ready
    assert(PowerAccess.ruledSites(ready, p2).contains(home))
    val t = accepted(use(ready, HornedMask(catalog),
      DecisionOptionRef.Relic(mask)))
    assert(awaits(t, HornedMask.denizenDecisionId))
    val row = rowFor(t, DecisionOptionRef.Denizen(inn))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)
```

The suite lives in `gameplay.operations`, so `PowerAccess` (`private[gameplay]`) is visible. Check these names against their sources:
- `DecisionAnswer.ChooseOneAnswer`, against `PhasePowerHarness.pick`.
- `HornedMask.denizenDecisionId`, against `HornedMaskSuite`.
- The `present` helper, against `HallOfMinistersConsentSuite`.

If `Table.start` already has a ruler at Ancient City, Dunes or Fair Isle that one warband does not overcome, the `ruledSites` assert fails first. In that case, add warbands until p2 rules the site.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.AncientBloodlineTakesSuite"`
Expected: the askable and unavailable assertions fail with `"available"`. The relic and the denizen are offered plainly, and their refused moves are skipped. The "freely" test passes already.

- [ ] **Step 3: Recover judges each relic**

In `RecoverProcedure.scala`:

- Pass `catalog` from `recoveryAt` to `recovery(catalog, actor, siteId, difficulty)`.
- Give the relic's move one definition, used by both the step and the judge:

```scala
    /** The recovered relic's move to the actor, facedown. */
    def relicMove(relicId: RelicId): Move = Move(Piece.Card(relicId),
      PositionedLocation(Location.Site(siteId)),
      PositionedLocation(Location.PlayArea(actor)),
      resultingOrientation = Some(Orientation.FaceDown), by = Some(actor))
```

- `moveRelic` emits `Vector[CoreOperation](relicMove(relicId))`.
- The relic `Decide`'s options judge each relic:

```scala
          query = DecisionQuery.ChooseOne(relics.flatMap(relic =>
            // A relic a printed protection keeps (Ancient Bloodline) is
            // askable of its holder, or shown with its reason.
            CardSlots.judge(catalog, ready, actor,
              DecisionOption.Relic(DecisionOptionRef.Relic(relic.id)),
              Right(Vector(relicMove(relic.id))))),
            heading = Some("Take a relic"), confirmVerb = Some("Take"))),
```

- Import `oathdigital.gameplay.actions.cardplay.CardSlots`. In the comment above `afterLoop`, add one sentence: "Each relic is judged as card play judges a slot, so a relic Ancient Bloodline locks is askable of its holder."

- [ ] **Step 4: The powers' takes are required**

- `SiteRelicTake.steps`: `BuildOps((live, pending) => take(live, player, pending), required = true)`. In the class doc, add: "The take is required, so a relic a restriction refuses (Ancient Bloodline) is marked on the question rather than skipped."
- `HornedMask`: the take step's `BuildOps(..., required = true)`, with the same sentence for a denizen in its doc.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.AncientBloodlineTakesSuite oathdigital.gameplay.RecoverProcedureSuite oathdigital.gameplay.powers.action.SkeletonKeySuite oathdigital.gameplay.powers.action.BarbedNetSuite oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: all pass, with the 6 new tests.

If a Skeleton Key or Horned Mask row is still `"available"`, the askable search did not mark a required step's refusal. Stop and report; do not work around it.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SiteRelicTake.scala \
  src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala \
  src/test/scala/oathdigital/gameplay/operations/AncientBloodlineTakesSuite.scala
git commit -m "feat(batch8): a relic or denizen Ancient Bloodline locks shows why"
```

---

### Task 4: The paths that already judge

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/GiantPythonSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala`

**Interfaces:**
- Consumes: `AncientBloodline` (Task 2).
- Produces: tests only. These paths judge through the printed set already, so no production change is expected. If one fails for a reason other than its assertion, stop and report.

- [ ] **Step 1: Card play's Replace**

Add to `CardSlotsSuite.scala` (import `oathdigital.gameplay.operations.AncientBloodline`):

```scala
  /** p1 stands at Deep Woods, full with three plain cards, with Threatening
    * Roar, a Beast card, in hand. p2 rules Deep Woods and holds Ancient
    * Bloodline faceup. */
  private def fullWoodsBesideBloodlineHolder: ReadyGame =
    val roar = CatalogNames.denizen("Threatening Roar")
    Vector("Fae Merchant", "Second Chance", "Wrestlers")
      .map(CatalogNames.denizen(_))
      .foldLeft(Table.start.pawn(p1, at = woods).hand(p1, roar))(
        _.denizen(_, at = woods))
      .warbandsAt(woods, p2, 1)
      .adviser(p2, CatalogNames.denizen("Ancient Bloodline")).ready

  private def askingBloodlineHolder(replaced: DenizenId): OptionReason =
    OptionReason.Askable(AncientBloodline.id,
      AncientBloodline.lockedConsent(AncientBloodline.source),
      Vector(ConsentAsk(p2, Vector(AncientBloodline.id))),
      Vector(ConsentKey(AncientBloodline.id, p1, AncientBloodline.moveKey,
        DecisionOptionRef.Denizen(replaced))))

  test("Ancient Bloodline makes its holder's site cards askable of the " +
      "holder in a Replace"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance", "Wrestlers")
      .map(CatalogNames.denizen(_))
    assertEquals(atSite(slotsOf(fullWoodsBesideBloodlineHolder, roar)),
      fillers.zipWithIndex.map((filler, index) => DecisionOption.Unavailable(
        replacing(siteSlot(index, woods), filler),
        askingBloodlineHolder(filler))))
```

Read the suite's `atSite` and `replacing` helpers first, and the Hall test above, which this one mirrors.

- [ ] **Step 2: Giant Python's discard**

Add to `GiantPythonSuite.scala`:

```scala
  test("a card Ancient Bloodline locks is askable of its holder as a " +
      "Replace"):
    // p2 rules Broken Peaks, where Errand Boy stands, and holds Ancient
    // Bloodline faceup.
    val ready = pythonBesideDarkestSecretHolder
      .warbandsAt(peaks, Table.p2, 1)
      .adviser(Table.p2, CatalogNames.denizen("Ancient Bloodline")).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, GiantPython.decisionId))
    val row = queryOf(t, p1).toVector.flatMap(_.offeredOptions)
      .find(_.id == DecisionOptionRef.SiteSlot(peaks, 0).wireId)
    assertEquals(row.map(_.state), Some("askable"))
    assertEquals(row.flatMap(_.reason), Some("Ancient Bloodline: Locked " +
      "and needs the holder's consent."))
```

Deep Woods keeps an empty slot, so the question stays open.

- [ ] **Step 3: Dazzle**

Add to `DazzleSuite.scala`, after "Dazzle skips a rule-immune target and still discards another":

```scala
  test("Dazzle skips a card Ancient Bloodline locks and still discards " +
      "another"):
    // p2 rules the other Cradle site and holds Ancient Bloodline faceup.
    val ready = holding
      .warbandsAt(homeSite, p1, 1).denizen(homesteaders, at = homeSite)
      .warbandsAt(nearSite, p2, 1).denizen(wrestlers, at = nearSite)
      .adviser(p2, CatalogNames.denizen("Ancient Bloodline")).ready
    val done = Outcomes.finished(play(ready, power, dazzle))
    assert(Look(done.treeless).cardsAt(nearSite).contains(wrestlers))
    assertEquals(discarded(done.events), Vector(homesteaders))
```

- [ ] **Step 4: Run them**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.action.GiantPythonSuite oathdigital.gameplay.powers.whenplayed.DazzleSuite"`
Expected: all pass, with the 3 new tests.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/GiantPythonSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala
git commit -m "test(batch8): Ancient Bloodline in card play, Giant Python and Dazzle"
```

---

### Task 5: Gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by the tests this plan adds: 5 + 7 + 6 + 3 = 21, plus any the reviews ask for.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 2: Record the status**

- In the design's Status line, write "Slices 1 to 11 are done."
- In the ROADMAP's "Now" paragraph, write "slices 1 to 11 are done, and item 10b is next." Mark item 11 done (`- [x] **11. Actor-aware lock:** Ancient Bloodline.`).

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 11 done"
```
