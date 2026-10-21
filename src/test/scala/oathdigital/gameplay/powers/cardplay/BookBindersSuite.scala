package oathdigital.gameplay.powers.cardplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayed
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PowerRules, Table}
import oathdigital.testkit.Unwrap.accepted

class BookBindersSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val binders = CatalogNames.denizen("Book Binders")
  private val holder = Table.p2
  // A relic for the Conspiracy to take from Book Binders' holder.
  private val targetRelic = CatalogNames.relic("Circlet of Command")
  private val parked = PowerRules.parkedUnderWalkerPowers

  /** A Search drawing Faith, `owner` holding Book Binders, and exactly these
    * banks holding favor.
    */
  private def arranged(banks: Map[Suit, Int], owner: PlayerId = holder,
      orientation: Orientation = Orientation.FaceUp,
      vision: VisionId = VisionRules.Faith)(using munit.Location): ReadyGame =
    Table.from(SearchFixture.staged(Vector(vision), supply = 5))
      .adviser(owner, binders, facedown = orientation == Orientation.FaceDown)
      .banksHoldingOnly(banks.toSeq*).ready

  /** Book Binders' holder answers its bank question with `suit`. */
  private def holderChooses(placed: OathTransition, decisionId: String,
      suit: Suit): OathTransition =
    accepted(SearchFixture.rules.resolveWalker(placed.state, holder, decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))))

  private def faceup(ready: ReadyGame): OathTransition =
    SearchFixture.play(ready, Vector.empty, VisionRules.Faith, PlayedAs.Faceup)

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    Look(ready).player(id).board.favor

  /** `holder` also holds a relic, and stands at the actor's own site, so
    * the actor's faceup Conspiracy has a legal target: another player's pawn
    * holding a relic at the actor's site, the way `ConspiracyWhenPlayedSuite`
    * arranges one.
    */
  private def withConspiracyTarget(banks: Map[Suit, Int])(
      using munit.Location): ReadyGame =
    Table.from(
      SearchFixture.staged(Vector(VisionRules.Conspiracy), supply = 5))
      .adviser(holder, binders).pawn(holder, "Ancient City")
      .relic(holder, targetRelic, facedown = true)
      .banksHoldingOnly(banks.toSeq*).ready

  private val power = BookBinders.forCatalog(catalog)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def gained(amount: Int, suit: Suit) = NoteText.Said("gained",
    s"${holder.value} gained $amount favor from the $suit bank.", covers = true)

  test("another player's faceup Vision gives the holder a choice of bank, " +
      "then two favor"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val placed = faceup(ready)
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    parked.assertParked(placed.state, ActionRef.Search, choice, holder)
    val taken = holderChooses(placed, choice, Suit.Order)
    val after = SearchFixture.after(taken)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Order), 1)
    assertEquals(after.banks.favor(Suit.Arcane), 3)

  test("a single stocked bank is taken without asking"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Hearth), 3)

  test("a bank holding one favor gives one"):
    val ready = arranged(Map(Suit.Hearth -> 1))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)

  test("with every bank empty nothing happens"):
    val ready = arranged(Map.empty)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("the holder's own faceup Vision gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5), owner = actor)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("a facedown Book Binders is not active"):
    val ready = arranged(Map(Suit.Hearth -> 5),
      orientation = Orientation.FaceDown)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("a facedown play gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(SearchFixture.play(ready, Vector.empty,
      VisionRules.Faith, PlayedAs.Facedown))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("the Conspiracy still resolves when Book Binders' own take drains " +
      "the bank it emptied, which used to shift the target decision's index"):
    // Two banks stocked; the one Book Binders' holder picks (Arcane, holding
    // only 1) empties, leaving only Order stocked -- the shape-changing case
    // the fix covers, since Book Binders' own contribution used to be 2 nodes
    // (a Decide plus a Move) with two banks stocked, and only 1 (a bare
    // Move) with one, shifting every later sibling at this window.
    val ready = withConspiracyTarget(Map(Suit.Arcane -> 1, Suit.Order -> 5))
    val started = accepted(SearchFixture.start(ready))
    val kept = accepted(SearchFixture.keep(started, VisionRules.Conspiracy))
    val placed = accepted(SearchFixture.place(kept, VisionRules.Conspiracy,
      PlayedAs.Faceup))
    // Book Binders sorts before Conspiracy at this window (source keys
    // "game:denizen.book-binders" < "game:vision.conspiracy"), so its own
    // take parks first.
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Conspiracy)
    parked.assertParked(placed.state, ActionRef.Search, choice, holder)
    val tookBank = holderChooses(placed, choice, Suit.Arcane)
    // The Conspiracy's target decision now parks, at whatever index this
    // command's refold gives it -- the fix keeps that index stable.
    parked.assertParked(tookBank.state, ActionRef.Search,
      ConspiracyWhenPlayed.decisionId, actor)
    val done = SearchFixture.rules.resolveWalker(tookBank.state, actor,
      ConspiracyWhenPlayed.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.RelicSlot(holder, 0)))
    assert(done.isRight, done)
    val after = SearchFixture.after(accepted(done))
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    assertEquals(after.banks.favor(Suit.Arcane), 0)
    assertEquals(after.banks.favor(Suit.Order), 5)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assert(Look(after).player(actor).relics.exists(_.id == targetRelic))
    assert(!Look(after).player(holder).relics.exists(_.id == targetRelic))

  test("the Conspiracy still resolves, and the holder still gains favor, " +
      "when Book Binders' own take drains the only stocked bank"):
    // Exactly one bank stocked, holding 2 or fewer favor: Book Binders' own
    // take asks nothing (FavorBankChoice.take auto-takes a single bank) and
    // drains it to zero within the SAME command that places the Vision, so
    // the walk continues straight to Conspiracy's target decision without
    // parking on Book Binders at all. The regression is on the NEXT command
    // (answering the Conspiracy target), whose refold of this window must
    // still find Book Binders' node even though every bank is now empty.
    val ready = withConspiracyTarget(Map(Suit.Hearth -> 1))
    val started = accepted(SearchFixture.start(ready))
    val kept = accepted(SearchFixture.keep(started, VisionRules.Conspiracy))
    val placed = accepted(SearchFixture.place(kept, VisionRules.Conspiracy,
      PlayedAs.Faceup))
    parked.assertParked(placed.state, ActionRef.Search,
      ConspiracyWhenPlayed.decisionId, actor)
    val done = SearchFixture.rules.resolveWalker(placed.state, actor,
      ConspiracyWhenPlayed.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.RelicSlot(holder, 0)))
    assert(done.isRight, done)
    val after = SearchFixture.after(accepted(done))
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    assertEquals(after.banks.favor(Suit.Hearth), 0)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assert(Look(after).player(actor).relics.exists(_.id == targetRelic))
    assert(!Look(after).player(holder).relics.exists(_.id == targetRelic))

  test("a facedown Vision played from the advisers gives the holder favor " +
      "too, off turn, pinning the rebuildFacedown settle path"):
    val ready = Table.start.adviser(holder, binders)
      .banksHoldingOnly(Suit.Arcane -> 3, Suit.Order -> 3).ready
    val atPlacement = fromAdvisers(ready, VisionRules.Faith)
    val placed = accepted(SearchFixture.place(atPlacement, VisionRules.Faith,
      PlayedAs.Faceup))
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    parked.assertParked(placed.state, ActionRef.PlayFacedownAdviser, choice,
      holder)
    val taken = holderChooses(placed, choice, Suit.Order)
    val after = SearchFixture.after(taken)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Order), 1)
    assertEquals(after.banks.favor(Suit.Arcane), 3)

  // ---- Lines ----

  test("the favor taken from the one stocked bank is the Binders' line"):
    assertEquals(said(faceup(arranged(Map(Suit.Order -> 3))).events),
      Vector(gained(2, Suit.Order)))

  test("a bank chosen off turn writes the line when the take happens"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 1))
    val placed = faceup(ready)
    assertEquals(said(placed.events), Vector.empty)
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    val taken = holderChooses(placed, choice, Suit.Order)
    assertEquals(said(taken.events), Vector(gained(1, Suit.Order)))

  test("the holder's own faceup Vision writes nothing"):
    assertEquals(said(faceup(arranged(Map(Suit.Hearth -> 5), owner = actor))
      .events), Vector.empty)
