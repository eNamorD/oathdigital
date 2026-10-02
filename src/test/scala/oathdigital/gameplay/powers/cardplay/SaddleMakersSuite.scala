package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table

class SaddleMakersSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val saddle = DenizenId("142")
  private val nomad = denizensOf(Suit.Nomad).take(3)
  private val order = denizensOf(Suit.Order).take(3)
  private val hearth = denizensOf(Suit.Hearth).take(3)
  private val holder = TargetsFixture.others(base).head

  /** `holder` holds Saddle Makers on `orientation`, and the actor Searches a
    * world deck topped by `top`. */
  private def held(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(SearchFixture.staged(top), holder, saddle,
      orientation)

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor
  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)
  private def withBank(ready: ReadyGame, suit: Suit, n: Int): ReadyGame =
    ready.copy(banks = ready.banks.copy(favor = ready.banks.favor.updated(suit, n)))

  test("another player's nomad card played as a faceup adviser gains the " +
      "holder 2 favor from the Nomad bank"):
    val ready = held(nomad)
    val done = play(ready, Vector.empty, nomad.head, "adviser-faceup")
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(bank(after, Suit.Nomad), bank(ready, Suit.Nomad) - 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)

  test("an order card played to a site counts too"):
    val ready = held(order)
    val after = SearchFixture.after(play(ready, Vector.empty, order.head, "site"))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)

  test("a card of another suit, a facedown play and a discard gain nothing"):
    val other = SearchFixture.after(play(held(hearth), Vector.empty,
      hearth.head, "adviser-faceup"))
    assertEquals(favor(other, holder), favor(held(hearth), holder))
    Vector("adviser-facedown", "discard").foreach { button =>
      val ready = held(nomad)
      val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }

  test("a facedown adviser turned faceup by its play counts"):
    val ready = Table.start.adviser(holder, saddle)
      .adviser(actor, nomad.head, facedown = true).ready
    val after = SearchFixture.after(playFacedown(ready, Vector.empty,
      nomad.head, "adviser-faceup"))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)

  test("the holder's own play gains nothing"):
    val ready = asAdviser(SearchFixture.staged(nomad), saddle)
    val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
      "adviser-faceup"))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(bank(after, Suit.Nomad), bank(ready, Suit.Nomad))

  test("a facedown Saddle Makers is not active"):
    val ready = held(nomad, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
      "adviser-faceup"))
    assertEquals(favor(after, holder), favor(ready, holder))

  // ---- Lines ----

  private val power = SaddleMakers.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the holder's gain is Saddle Makers' line"):
    val done = play(held(nomad), Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(said(done.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 2 favor from the Nomad bank.", covers = true)))

  test("a bank holding 1 favor gives 1, and an empty bank gives nothing and " +
      "writes no line"):
    val low = withBank(held(nomad), Suit.Nomad, 1)
    val one = play(low, Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(favor(SearchFixture.after(one), holder), favor(low, holder) + 1)
    assertEquals(said(one.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 1 favor from the Nomad bank.", covers = true)))
    val empty = withBank(held(nomad), Suit.Nomad, 0)
    val none = play(empty, Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(favor(SearchFixture.after(none), holder), favor(empty, holder))
    assertEquals(said(none.events), Vector.empty)
