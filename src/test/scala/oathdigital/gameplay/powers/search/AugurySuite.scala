package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._

class AugurySuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val augury = DenizenId("56")
  private val modifiers = Vector(Augury.id)
  private val plain: Vector[DenizenId] = Suit.all.flatMap(denizensOf)

  private def withAugury(top: Vector[WorldCardId]): ReadyGame =
    atHome(CardStaging.without(SearchFixture.staged(top), augury), augury)

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  test("a world Search draws one card more than the printed three"):
    val top = plain.take(6)
    val plainSearch = start(withAugury(top)).toOption.get
    assertEquals(hand(plainSearch), top.take(3))
    val ready = withAugury(top)
    val started = start(ready, modifiers).toOption.get
    assertEquals(hand(started), top.take(4))
    // The cost is the printed one: Augury adds a card, not a price.
    assertEquals(player(SearchFixture.after(started)).board.supply.supply, 5 - 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))

  test("the player keeps any one of the drawn cards"):
    val top = plain.take(6)
    val kept = top(3)
    val done = play(withAugury(top), modifiers, kept, "discard")
    assertEquals(SearchFixture.after(done).game.current.temporaryHands(actor),
      Vector.empty)

  test("the draw still stops after a Vision, wherever the Vision falls"):
    val early = plain.take(2) ++ Vector(VisionRules.Faith) ++ plain.drop(2).take(3)
    val stopped = start(withAugury(early), modifiers).toOption.get
    assertEquals(hand(stopped), early.take(3))
    // A Vision is the fourth card, past the printed three: it is drawn, and the
    // draw counts it.
    val late = plain.take(3) ++ Vector(VisionRules.Faith) ++ plain.drop(3).take(2)
    val ready = withAugury(late)
    val reached = start(ready, modifiers).toOption.get
    assertEquals(hand(reached), late.take(4))
    assertEquals(SearchFixture.after(reached).game.current.tracks.visionsDrawn,
      ready.game.current.tracks.visionsDrawn + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, reached.events),
      SearchFixture.after(reached))

  test("a Search from a regional discard draws one card more from its top"):
    val region = player(base).pawnSite.flatMap(base.game.current.map.regionOf).get
    val pile = plain.take(5)
    val staged = pile.foldLeft(withAugury(Vector.empty))(CardStaging.without(_, _))
    val ready = staged.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(regionalDiscards = c.commonCards.regionalDiscards
        .updated(region, pile))))
    val started = rules.startWalker(OathState.Ready(ready), ActionRef.Search,
      actor, modifiers, Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${region.key}"))).toOption.get
    // The pile is drawn from its end, and Augury takes a fourth card.
    assertEquals(hand(started), pile.reverse.take(4))

  test("it is offered for Search, and not for another action"):
    val ready = withAugury(plain.take(6))
    val offered = (action: ActionRef) =>
      rules.offerableWalkerPowers(ready, actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Search).contains(Augury.id))
    assert(!offered(ActionRef.Travel).contains(Augury.id))

  test("it is not offered when the card is out of reach"):
    assert(start(SearchFixture.staged(plain.take(6)), modifiers).isLeft)
