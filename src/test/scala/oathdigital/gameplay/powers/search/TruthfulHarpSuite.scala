package oathdigital.gameplay.powers.search

import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TruthfulHarpSuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val harp = RelicId("R04")
  private val plain: Vector[DenizenId] = Suit.all.flatMap(denizensOf)
  private val onlyHarp = Vector(TruthfulHarp.id)
  private val both = Vector(TruthfulHarp.id, Augury.id)

  private def withHarp(top: Vector[WorldCardId]): ReadyGame =
    withRelic(SearchFixture.staged(top), harp)

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  private def known(transition: OathTransition, viewer: PlayerId)
      : Vector[WorldCardId] = SearchFixture.after(transition).knowledge
    .advisers.getOrElse(viewer, Vector.empty)

  test("the Harp is a registered selected Search modifier") {
    val power = TruthfulHarp.forCatalog(catalog).get
    assertEquals(power.cardId, harp)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a Search draws two more cards, and every card drawn is revealed") {
    val top = plain.take(7)
    val ready = withHarp(top)
    val started = start(ready, onlyHarp).toOption.get
    assertEquals(hand(started), top.take(5))
    TargetsFixture.others(ready).foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(5).toSet[WorldCardId]))
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))
  }

  test("the reveal does not change the play: the kept card can still be played " +
      "facedown as an adviser, as usual") {
    val top = plain.take(7)
    val ready = withHarp(top)
    val done = play(ready, onlyHarp, top(4), "adviser-facedown")
    val advisers = player(SearchFixture.after(done)).advisers
    assert(advisers.exists {
      case card: DenizenState => card.id == top(4) &&
        card.orientation == Orientation.FaceDown
      case _ => false
    })
    // The other players saw the card while it was revealed, and remember it.
    // That is what a reveal at a table leaves behind, and it needs no rule.
    val other = TargetsFixture.others(ready).head
    assert(known(done, other).contains(top(4)))
  }

  test("the Harp and Augury stack, and the reveal covers all six cards") {
    val top = plain.take(8)
    val augury = DenizenId("56")
    val ready = atHome(CardStaging.without(withHarp(top), augury), augury)
    val started = start(ready, both).toOption.get
    assertEquals(hand(started), top.take(6))
    TargetsFixture.others(ready).foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(6).toSet[WorldCardId]))
  }

  test("nothing is revealed without the Harp") {
    val top = plain.take(7)
    val started = start(withHarp(top)).toOption.get
    assertEquals(hand(started), top.take(3))
    TargetsFixture.others(withHarp(top)).foreach(viewer =>
      assertEquals(known(started, viewer), Vector.empty[WorldCardId]))
  }

  test("a facedown Harp cannot be selected") {
    val facedown = withRelic(SearchFixture.staged(plain.take(7)), harp,
      Orientation.FaceDown)
    assert(start(facedown, onlyHarp).isLeft)
  }
}
