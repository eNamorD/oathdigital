# Catalog Batch 8, Slice 10, Part 3 (Giant Python and the Slot Question) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 186 Giant Python, and move Roving Terror onto the same slot question. Giant Python prints two powers:

- "[secret] **ACTION:** If you hold the Darkest Secret, move Giant Python to another site, and you may discard a card there first."
- "Other cards at this site **cannot** be used for non-persistent powers or placing [favor] / [secret]."

**Architecture:**

- **Parts 1 and 2 are prerequisites.** This plan uses these pieces from them:
  - `PowerUse`, `PowerUseRestriction` and `PowerUseRules`;
  - `Placements`, from part 2;
  - the askable search, from part 2;
  - the refusal of automatic powers (`AutomaticUse` and the collector's `refused` test), from part 2.
- **One slot question.** A new helper, `SiteSlotMoves` in `powers/action`, asks one question over the slots of the other sites, as card play does.
  - Each other site in play offers its first empty slot, when the site has room and the power allows empty slots.
  - It also offers each faceup denizen and ruined edifice there, marked Replace (`DecisionOption.Replacing`).
  - Each option is judged by `CardSlots.judge`, so a card the Hall of Ministers protects is askable or shown with its reason.
  - A Replace discards the card the standard way (`SiteCards.discard`). The mover then takes the site's last slot with its tokens, since a row has no gaps.
- **Giant Python's ACTION** uses the question with empty slots.
  - It is usable when its user holds the Darkest Secret, read through Yew Staff's lens (`CardPowerBanners.holdsDarkestSecret`), and some option may be chosen.
  - Its lines are "Moved {card} to {site}." and "Replaced {card} at {site}.".
- **Roving Terror** uses the question with Replace options only. Its line and its decision id stay as they are. Its answers become site slots.
- **Giant Python's restriction** is a power-use restriction plus an operation restriction, as Forest Council's are.
  - It holds for every player, its ruler included.
  - Nobody may waive it, because it names no enemies (consent design, ruling 1).
  - It refuses a non-persistent use of another card at Giant Python's site, and a use whose cost places on one. It also refuses a placement on one.
  - Its reach moves with Giant Python, because it reads where Giant Python stands.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "The bar" (the slot question bullet);
  - "Slice 10: Power-use restrictions" and its "Plan-time rulings";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 10 entry for 186 Giant Python.
- `docs/superpowers/specs/2026-10-04-card-play-slots-design.md`, for the slot options `CardSlots` builds.
- The part 1 and part 2 plans of this slice, `docs/superpowers/plans/2026-10-14-catalog-batch-8-slice10-part1.md` and `2026-10-15-catalog-batch-8-slice10-part2.md`, for the names this plan consumes.

**Starts from:** `main` with slice 10 parts 1 and 2 merged. Check that `src/main/scala/oathdigital/gameplay/powers/poweruse/Placements.scala` exists. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it. Each new file below lists the imports it needs.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - The engine (`gameplay/walker`, `gameplay/operations`) never names a power. No file there may contain "giantpython".
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** Roving Terror's tree keeps its three nodes. Only its decision's options change.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build.
- **Lessons from part 1's tests:**
  - To read a parked decision, match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`. The case's first field is the procedure, not the decision id. `SpellBreakerOfferSuite.jinxAtRecoverReroll` shows the pattern.
  - A plan window, a reroll choice or a non-Act phase menu is not asked when it has nothing usable or askable to offer. A test that expects to see a refused option there needs one other usable option on the board to keep the question open. `SpellBreakerOfferSuite`'s first test adds Battle Honors for this.
- **Log lines omit the actor.** Giant Python's lines name the card and the site only.
- **Note keys** are declared through a `NoteSet`, as plain `val`s after the set. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`. Check that the diff adds only the keys named in the task.
- **Words, exactly:**
  - Giant Python's heading: "Giant Python: move to which slot?"
  - Roving Terror's heading: "Roving Terror: move to which slot?"
  - Giant Python's lines: "Moved {card} to {site}." and "Replaced {card} at {site}."
  - Giant Python's hover reasons: "Giant Python: Cards at its site cannot be used for non-persistent powers." and "Giant Python: Cannot place favor or secrets on cards at its site."
- **Names, exactly:**
  - File `src/main/scala/oathdigital/gameplay/powers/action/SiteSlotMoves.scala`: `private[powers] final class SiteSlotMoves` and `final case class SlotMove`.
  - File `src/main/scala/oathdigital/gameplay/powers/action/GiantPython.scala`: `GiantPythonCard` (moved unchanged from `HoldingBeastDenizens.scala`), `final case class GiantPython`, and `object GiantPython` with `forCatalog`, `decisionId`, `moved` and `replaced`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestriction.scala`: `case object GiantPythonRestriction`.

---

### Task 1: The slot question, with Roving Terror

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SiteSlotMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/RovingTerrorSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`
- Modify: `src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala`

**Interfaces:**
- Produces:
  - `SlotMove(site: SiteId, slot: DecisionOptionRef.SiteSlot, replaced: Option[(SiteDenizenState, Suit)])`.
  - `new SiteSlotMoves(catalog: ExecutableCatalog, mover: DenizenId, empty: Boolean)` with:
    - `moves(ready: ReadyGame): Vector[SlotMove]`: every slot the mover may take, in map order then slot order;
    - `options(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption]`: each move's option, judged for the actor;
    - `chosen(ready: ReadyGame, ref: DecisionOptionRef): Option[SlotMove]`: the move an answer names, read live;
    - `operations(ready: ReadyGame, actor: PlayerId, move: SlotMove): Either[OathViolation, Vector[CoreOperation]]`: the discard, if any, then the mover's move.

- [ ] **Step 1: Move Roving Terror's tests onto slots**

In `RovingTerrorSuite`:
- An option is now a site slot marked Replace. Errand Boy at Broken Peaks is `DecisionOptionRef.SiteSlot(peaks, 0)`, and the ruined Hall of Debate at Buried Giant is `DecisionOptionRef.SiteSlot(giant, 0)`.
- The first test's `offered` becomes `Some(Vector("site-slot" -> s"${peaks.value}:0"))`. If the projection's id for a site slot reads differently, use `DecisionOptionRef.SiteSlot(peaks, 0).wireId`.
- `choose` answers with the slot ref.
- Add one test: a site with room offers no empty slot to Roving Terror. Use Deep Woods, empty and in play: `offered` lists no `site-slot` at Deep Woods.

```scala
  test("it offers only cards to replace, never an empty slot"):
    val ready = staged.ready
    val woods = CatalogNames.site("Deep Woods")
    val t = accepted(use(ready, power, source))
    assert(!offered(t, p1).toVector.flatten.exists((kind, id) =>
      kind == "site-slot" && id.startsWith(woods.value)))
```

In `HallOfMinistersConsentSuite` and `HallOfMinistersConsentServiceSuite`, Roving Terror's option for Errand Boy at Deep Woods is now `DecisionOptionRef.SiteSlot(woods, 0)`. Errand Boy is placed before the Hall, so it holds slot 0.
- The asked target and the answer become that slot.
- The Hall's grant key is unchanged. It still names the card: `ConsentKey(HallOfMinisters.id, p1, HallOfMinisters.discardKey, DecisionOptionRef.Denizen(errandBoy))`.
- `errandBoyRow` finds the row by `_.id == DecisionOptionRef.SiteSlot(woods, 0).wireId`.
- Rename `errandBoyRef` where it now names the slot (`errandBoySlot`), and keep a separate value for the card ref the key uses.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.RovingTerrorSuite oathdigital.gameplay.operations.HallOfMinistersConsentSuite oathdigital.application.HallOfMinistersConsentServiceSuite"`
Expected: the tests that read options or answer fail, because Roving Terror still offers the cards themselves.

- [ ] **Step 3: Create `SiteSlotMoves.scala`**

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.cardplay.CardSlots
import oathdigital.model._

/** Where a site card that moves itself may go (Giant Python, Roving Terror;
  * catalog batch 8, slice 10): `slot` at `site`, replacing `replaced` with
  * its suit, or empty. */
final case class SlotMove(site: SiteId, slot: DecisionOptionRef.SiteSlot,
    replaced: Option[(SiteDenizenState, Suit)])

/** One question over the slots of the sites other than `mover`'s, as card
  * play asks it (`CardSlots`): each site's first empty slot, when `empty`
  * allows one and the site has room, then each card there that may be
  * replaced, marked Replace. A replaceable card is a faceup denizen or a
  * ruined edifice ([[SiteCards]]); an intact edifice is locked and stays.
  *
  * Each option is judged for the actor by the printed restrictions, so a
  * card the Hall of Ministers protects is askable of its ruler or shown
  * with its reason. A Replace discards the card the standard way, and the
  * mover takes the site's last slot with its tokens, since a row has no
  * gaps.
  */
private[powers] final class SiteSlotMoves(catalog: ExecutableCatalog,
    mover: DenizenId, empty: Boolean):
  private val cards = new SiteCards(catalog, Suit.all.toSet)

  def moves(ready: ReadyGame): Vector[SlotMove] =
    SiteRulers.siteOf(ready, mover).toVector.flatMap(here =>
      ready.game.current.map.inPlay.filter(_ != here).flatMap(site =>
        movesAt(ready, site)))

  private def movesAt(ready: ReadyGame, site: SiteId): Vector[SlotMove] =
    val held = ready.game.current.map.sites.get(site).toVector
      .flatMap(_.denizens)
    val room = catalog.site(site).exists(held.size < _.capacity)
    val open = Option.when(empty && room)(SlotMove(site,
      DecisionOptionRef.SiteSlot(site, held.size), None))
    val replacing = cards.at(ready, site).flatMap((card, suit) =>
      Option.when(held.indexWhere(_.id == card.id) >= 0)(SlotMove(site,
        DecisionOptionRef.SiteSlot(site, held.indexWhere(_.id == card.id)),
        Some(card -> suit))))
    open.toVector ++ replacing

  private def option(move: SlotMove): DecisionOption =
    val slot = DecisionOption.SiteSlot(move.slot)
    move.replaced.fold[DecisionOption](slot)(replaced =>
      DecisionOption.Replacing(slot, replaced._1.id))

  def options(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    moves(ready).flatMap(move => CardSlots.judge(catalog, ready, actor,
      option(move), operations(ready, actor, move)))

  def chosen(ready: ReadyGame, ref: DecisionOptionRef): Option[SlotMove] =
    moves(ready).find(_.slot == ref)

  def operations(ready: ReadyGame, actor: PlayerId, move: SlotMove)
      : Either[OathViolation, Vector[CoreOperation]] =
    for
      here <- SiteRulers.siteOf(ready, mover).toRight(
        OathViolation.InvalidEventOrder(s"${mover.value} is not at a site"))
      discard <- move.replaced.fold[Either[OathViolation,
          Vector[CoreOperation]]](Right(Vector.empty))(replaced =>
        cards.discard(ready, move.site, replaced._1, replaced._2, actor,
          required = true).map(Vector(_)))
    yield discard :+ Move(Piece.Card(mover),
      PositionedLocation(Location.Site(here)),
      PositionedLocation(Location.Site(move.site)))
```

`catalog.site(site)` returns the printed site with its `capacity`, as `CardSlots.siteSlots` reads it. If `SiteDenizenState` names its card differently from `id`, use the accessor `CardSlots.siteSlots` uses (`held.id`).

- [ ] **Step 4: Move Roving Terror onto it**

In `RovingTerror.scala`:
- Replace `cards`, `Candidate` and `candidates` with:

  ```scala
  private val slots = new SiteSlotMoves(catalog, RovingTerrorCard.id,
    empty = false)
  ```

- `ask` reads `slots.options(ready, actor)`, keeps its rule that it asks only when some option may be chosen, and uses the heading "Roving Terror: move to which slot?".
- `replace` finds the move with `slots.chosen(ready, ref)`, rejecting an unknown slot with `InvalidEventOrder(s"${ref.wireId} is not a slot Roving Terror can move to")`, and returns `slots.operations(ready, actor, move)`.
- `replacedNote` reads the move from the state before the step:

  ```scala
    PowerSourceRef.of(source).map(card => (for
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      move <- slots.chosen(before, ref)
      replacedCard <- move.replaced
      if SiteRulers.siteOf(states.now, RovingTerrorCard.id)
        .contains(move.site)
    yield replaced(card, NoteArg.Card(replacedCard._1.id),
      NoteArg.Site(move.site))).getOrElse(nothing(card)))
  ```

- Rewrite the class comment's second paragraph to say the question is `SiteSlotMoves`' with Replace options only, each answered by a site slot.

Drop the imports this leaves unused.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.operations.HallOfMinistersConsentSuite oathdigital.application.HallOfMinistersConsentServiceSuite oathdigital.gameplay.powers.poweruse.* oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: all pass. RovingTerrorSuite has 6 tests.

The projection of a `Replacing` site slot names the card it replaces, as card play's does. If a Game Log or projection test pins Roving Terror's options as denizens, update it to slots.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SiteSlotMoves.scala src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala src/test/scala/oathdigital/gameplay/powers/action/RovingTerrorSuite.scala src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala
git commit -m "feat(batch8): Roving Terror asks which slot to move to"
```

---

### Task 2: Giant Python's ACTION

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/GiantPython.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `GiantPythonCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Test: `src/test/scala/oathdigital/gameplay/powers/action/GiantPythonSuite.scala`

**Interfaces:**
- Consumes: Task 1's `SiteSlotMoves`; `CardPowerBanners.holdsDarkestSecret(ready, player)`.
- Produces:
  - `GiantPython.forCatalog(catalog)`, a `PaidAction` of `GiantPythonCard.action`;
  - `GiantPython.decisionId = "power.giant-python.slot"`;
  - note keys `moved` (`used`) and `replaced` (`used.replaced`).

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/action/GiantPythonSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Giant Python's ACTION (card 186): "If you hold the Darkest Secret, move
  * Giant Python to another site, and you may discard a card there first."
  * (catalog batch 8, slice 10, part 3). */
class GiantPythonSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val python = CatalogNames.denizen("Giant Python")
  private val source = DecisionOptionRef.Denizen(python)
  private val power = GiantPython.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  /** Capacity 1. */
  private val peaks = CatalogNames.site("Broken Peaks")
  /** Capacity 3. */
  private val woods = CatalogNames.site("Deep Woods")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")

  /** p1's Act at Ancient City beside Giant Python, holding the Darkest
    * Secret and the start's faceup secret. Errand Boy fills Broken Peaks. */
  private def pythonBesideDarkestSecretHolder: Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(python, at = home).denizen(errandBoy, at = peaks)
      .darkestSecret(Some(p1), 1)

  private def slotOptions(t: OathTransition): Vector[(String, String)] =
    offered(t, p1).toVector.flatten

  test("without the Darkest Secret it is unusable"):
    val ready = pythonBesideDarkestSecretHolder.darkestSecret(None, 1).ready
    assert(!usableNow(ready).exists(_.power.id == power.id))

  test("Yew Staff's holder may use it as if they held the Darkest Secret"):
    val ready = pythonBesideDarkestSecretHolder.darkestSecret(None, 1)
      .relic(p1, "Yew Staff").ready
    assert(usableNow(ready).exists(_.power.id == power.id))

  test("it asks one slot question: an empty slot where there is room, a " +
      "card to replace, and nothing at its own site"):
    val t = accepted(use(pythonBesideDarkestSecretHolder.ready, power,
      source))
    assert(awaits(t, GiantPython.decisionId))
    val options = slotOptions(t)
    assert(options.contains("site-slot" ->
      DecisionOptionRef.SiteSlot(woods, 0).wireId))
    // Broken Peaks is full: only its card, to replace.
    assertEquals(options.filter(_._2.startsWith(peaks.value)),
      Vector("site-slot" -> DecisionOptionRef.SiteSlot(peaks, 0).wireId))
    assert(!options.exists(_._2.startsWith(home.value)))

  test("to an empty slot it moves with its secret, and the line says " +
      "where"):
    val ready = pythonBesideDarkestSecretHolder.ready
    val UsedAndAnswered(t, done) = useAndAnswer(ready, power, source,
      GiantPython.decisionId, DecisionOptionRef.SiteSlot(woods, 0))
    val end = Look(after(done))
    assertEquals(end.cardsAt(woods), Vector[CardId](python))
    assertEquals(end.cardsAt(home), Vector.empty[CardId])
    assertEquals(end.tokensOn(python), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Moved ${python.value} to ${woods.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("replacing a card discards it the standard way and takes its site"):
    val ready = pythonBesideDarkestSecretHolder
      .tokens(errandBoy, favor = 1, secrets = 0).bankFavor(Suit.Beast, 2)
      .ready
    val UsedAndAnswered(_, done) = useAndAnswer(ready, power, source,
      GiantPython.decisionId, DecisionOptionRef.SiteSlot(peaks, 0))
    val end = Look(after(done))
    assertEquals(end.cardsAt(peaks), Vector[CardId](python))
    assert(end.discardAfter(peaks).contains(errandBoy))
    assertEquals(end.bankFavor(Suit.Beast), 2 + 1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.replaced", s"Replaced ${errandBoy.value} at ${peaks.value}.",
      covers = true)))
```

`usableNow`, `use`, `useAndAnswer`, `after`, `offered`, `replayed` and `awaits` are `PhasePowerHarness`'s, as `RovingTerrorSuite` uses them. If the log renders a card argument as its name, match the text `RovingTerrorSuite`'s replaced line expects.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.GiantPythonSuite"`
Expected: compilation fails, because `GiantPython` does not exist.

- [ ] **Step 3: Create `GiantPython.scala`**

Move `GiantPythonCard` here unchanged and remove it from `HoldingBeastDenizens.scala`.

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.banner.CardPowerBanners
import oathdigital.model._

object GiantPythonCard extends Denizen(DenizenId("186"), "Giant Python", Suit.Beast) with SiteOnly:
  val action = PrintedPower(PowerId("denizen.giant-python.action"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** If you hold the Darkest Secret, move Giant " +
      "Python to another site, and you may discard a card there " +
      "first.")
  val restriction = PrintedPower(PowerId("denizen.giant-python.restriction"),
    persistent = true, cost = Cost.free,
    text = "Other cards at this site **cannot** be used for " +
      "non-persistent powers or placing [favor] / [secret].")
  val powers: Vector[PrintedPower] = Vector(action, restriction)

/** Giant Python's ACTION (card 186, site-only): "If you hold the Darkest
  * Secret, move Giant Python to another site, and you may discard a card
  * there first."
  *
  * It is usable when its user holds the Darkest Secret, as Yew Staff lets
  * them act ([[CardPowerBanners.holdsDarkestSecret]]), and some slot may
  * be chosen. After the cost, it asks one question over every other
  * site's slots ([[SiteSlotMoves]]): the first empty slot where there is
  * room, and each card there to replace. At a full site only a Replace is
  * offered. Giant Python moves carrying its secrets; its restriction reads
  * where it stands, so it moves too (`GiantPythonRestriction`).
  *
  * Its line covers the generic discard and move lines: "Moved {card} to
  * {site}." or "Replaced {card} at {site}."
  */
final case class GiantPython private (catalog: ExecutableCatalog)
    extends PaidAction(GiantPythonCard.action):
  import GiantPython._

  private val slots = new SiteSlotMoves(catalog, GiantPythonCard.id,
    empty = true)
  override def noteKeys: Vector[NoteKey] = GiantPython.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    CardPowerBanners.holdsDarkestSecret(ready, player) &&
      slots.options(ready, player).exists(_.available)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(decisionId, player,
      DecisionQuery.ChooseOne(slots.options(live, player),
        heading = Some(heading)), passWhenEmpty = true))),
    BuildOps((live, pending) => move(live, player, pending),
      required = true),
    Note(this.id, movedNote(_, source), covers = true))))

  /** No answer means the question passed, and Giant Python stays. */
  private def move(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => slots.chosen(ready, ref).toRight(
          OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a slot Giant Python can move to"))
        .flatMap(slots.operations(ready, actor, _))

  /** The chosen slot, read before the step, once Giant Python stands at
    * its site. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    for
      card <- PowerSourceRef.of(source)
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      chosen <- slots.chosen(before, ref)
      if SiteRulers.siteOf(states.now, GiantPythonCard.id)
        .contains(chosen.site)
    yield chosen.replaced.fold(moved(card,
      NoteArg.Card(GiantPythonCard.id), NoteArg.Site(chosen.site)))(
      gone => replaced(card, NoteArg.Card(gone._1.id),
        NoteArg.Site(chosen.site)))

object GiantPython:
  val id: PowerId = GiantPythonCard.action.id
  val decisionId: String = "power.giant-python.slot"
  val heading: String = "Giant Python: move to which slot?"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Moved {card} to {site}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "Replaced {card} at {site}." */
  val replaced: NoteKey = notes.key("used.replaced", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): GiantPython =
    new GiantPython(catalog)
```

If `usable` is not where the harness checks a phase power's gate, use the gate `PaidAction` documents. `TheRedSeer` and `TerrorSpells` also read the Darkest Secret. Follow whichever of them checks it the same way.

- [ ] **Step 4: Register it**

- In `WorldActionPowers.scala`, add `Vector(GiantPython.forCatalog(catalog)) ++` after Roving Terror.
- `NewFoundations.scala` already imports `powers.action._`, so `GiantPythonCard` keeps its place in the card list.
- Regenerate the note-key snapshot. The diff must add exactly two lines for `denizen.giant-python.action`: `used` and `used.replaced`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.PhasePowerSuite"`
Expected: all pass. GiantPythonSuite has 5 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/GiantPython.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala src/test/resources/notes/declared-keys.txt src/test/scala/oathdigital/gameplay/powers/action/GiantPythonSuite.scala
git commit -m "feat(batch8): Giant Python moves to a slot it picks"
```

---

### Task 3: Giant Python's restriction

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestriction.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestrictionSuite.scala`

**Interfaces:**
- Consumes: part 2's `Placements.onto` and `Placements.byUse`; Task 2's `GiantPythonCard`.
- Produces: `GiantPythonRestriction.id`, and note keys `usePlain` and `placingPlain`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestrictionSuite.scala`:

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Giant Python's restriction (card 186): "Other cards at this site cannot
  * be used for non-persistent powers or placing [favor] / [secret]."
  * (catalog batch 8, slice 10, part 3). It binds every player and nobody
  * may waive it. */
class GiantPythonRestrictionSuite extends munit.FunSuite:
  private val python = CatalogNames.denizen("Giant Python")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val source = PowerSourceRef.Card(python)

  /** p1's Act at Ancient City, ruled by p1, with Giant Python and Roving
    * Terror there and Wrestlers at Deep Woods. */
  private def pythonAtRuledHome: ReadyGame =
    Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .warbandsAt(home, p1, 1).denizen(python, at = home)
      .denizen(terror, at = home).denizen(wrestlers, at = woods).ready

  private def nonPersistentUseOf(card: DenizenId, by: PlayerId = p1)
      : PowerUse = PowerUse(by, PowerId("test.power"), Some(card),
    persistent = false, costsSecret = false)

  test("it refuses every player, its ruler included, a non-persistent " +
      "power of another card at its site, and nobody may waive it"):
    val refusal = GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(terror))
    assertEquals(refusal.map(_.note),
      Some(GiantPythonRestriction.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver), None)
    assert(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(terror, by = p2)).nonEmpty)

  test("Giant Python itself, and a card at another site, are free"):
    assertEquals(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(python)), None)
    assertEquals(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(wrestlers)), None)

  test("it refuses a payment onto a card at its site"):
    val ban = GiantPythonRestriction.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(terror), Cost(favor = 1))
    assert(ban.reason(pythonAtRuledHome, pay).exists(_.waiver.isEmpty))
    assertEquals(ban.reason(pythonAtRuledHome,
      pay.copy(placedAt = Location.OnCard(python))), None)

  test("the phase menu shows a card at its site with its reason"):
    val option = MenuPlay.menuOptionFor(catalog, pythonAtRuledHome,
      ActionRef.UsePower(RovingTerror.id),
      Vector(DecisionOptionRef.Denizen(terror)))
    assertEquals(option, DecisionOption.Unavailable(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(terror)),
      OptionReason.Noted(GiantPythonRestriction.id,
        GiantPythonRestriction.usePlain(source))))

  test("its reach moves with it"):
    val moved = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(python, at = woods).denizen(terror, at = home)
      .denizen(wrestlers, at = woods).ready
    assertEquals(GiantPythonRestriction.refusal(moved,
      nonPersistentUseOf(terror)), None)
    assert(GiantPythonRestriction.refusal(moved,
      nonPersistentUseOf(wrestlers)).nonEmpty)
```

If `menuOptionFor` builds a different shell, compare against `DecisionOption.forRef(DecisionOptionRef.Denizen(terror)).get`, as part 1's phase suite does.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.GiantPythonRestrictionSuite"`
Expected: compilation fails, because `GiantPythonRestriction` does not exist.

- [ ] **Step 3: Create `GiantPythonRestriction.scala`**

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction}
import oathdigital.gameplay.powers.action.GiantPythonCard
import oathdigital.model._

/** Giant Python's restriction (card 186, site-only): "Other cards at this
  * site cannot be used for non-persistent powers or placing [favor] /
  * [secret]."
  *
  * The other cards are the denizens and edifices at the site where Giant
  * Python stands now, so the restriction moves with it. It refuses a
  * non-persistent use of one, a use whose cost places on one
  * ([[Placements.byUse]]), and a placement on one ([[Placements.onto]]).
  * It binds every player, its ruler included. It names no enemies, so no
  * consent lifts it (consent design, ruling 1). It writes no line.
  */
case object GiantPythonRestriction extends ContributingPower
    with PowerUseRestriction:
  val id: PowerId = GiantPythonCard.restriction.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cards at its site cannot be used for non-persistent powers." */
  val usePlain: NoteKey = notes.key("no-powers-here", Vector(NotePart.Text(
    "Cards at its site cannot be used for non-persistent powers.")))
  /** "Cannot place favor or secrets on cards at its site." */
  val placingPlain: NoteKey = notes.key("no-placing-here", Vector(
    NotePart.Text("Cannot place favor or secrets on cards at its site.")))

  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)

  private val cardSource = PowerSourceRef.Card(GiantPythonCard.id)

  /** Whether `card` is another card at Giant Python's site. */
  private def guarded(ready: ReadyGame, card: CardId): Boolean =
    card != GiantPythonCard.id && SiteRulers.siteOf(ready,
      GiantPythonCard.id).exists(site => ready.game.current.map.sites
      .get(site).exists(_.denizens.exists(_.id == card)))

  private def refused(code: String, player: PlayerId): OperationReason =
    OperationReason(code, s"cards at Giant Python's site cannot be used " +
      s"by ${player.value}", OperationReasonKind.Impossible)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      card <- use.card
      if guarded(ready, card)
      key <- if !use.persistent then Some(usePlain)
        else Placements.byUse(ready, use).map(_ => placingPlain)
    yield PowerUseRefusal(id, key(cardSource),
      refused("giant-python", use.user))

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      Placements.onto(ready, operation).filter(placed =>
        guarded(ready, placed._2)).map(placed =>
        refused("giant-python-placing", placed._1))
```

`GiantPythonRestriction` declares no `waiver`, so `ConsentPowers` does not list it.

- [ ] **Step 4: Register it**

- In `WalkerPowerCatalog.scala`, add `Vector(GiantPythonRestriction) ++` after City Wall.
- In `PowerKindsCatalogSuite.scala`, add `"denizen.giant-python.restriction"` to `persistentRules`.
- Regenerate the note-key snapshot. The diff must add exactly two lines for `denizen.giant-python.restriction`: `no-powers-here` and `no-placing-here`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.powers.action.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: all pass. GiantPythonRestrictionSuite has 5 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestriction.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/resources/notes/declared-keys.txt src/test/scala/oathdigital/gameplay/powers/poweruse/GiantPythonRestrictionSuite.scala
git commit -m "feat(batch8): Giant Python's site cards cannot be used"
```

---

### Task 4: Gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by the tests this plan adds: 1 + 5 + 5 = 11, plus any the reviews ask for.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 2: Record the status**

- In the design's Status line, write "Slices 1 to 10 are done."
- In the ROADMAP's item 10, mark slice 10 done and drop the part note.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 10 done"
```
