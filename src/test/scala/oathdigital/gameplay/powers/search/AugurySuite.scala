package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class AugurySuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val augury = CatalogNames.denizen("Augury")
  private val modifiers = Vector(Augury.id)

  /** p1's Act at Ancient City with Augury at the site and 5 Supply, the
    * world deck topped by `top`. */
  private def withAugury(top: Vector[WorldCardId]): ReadyGame =
    Table.start.pawn(p1, "Ancient City").denizen(augury, at = "Ancient City")
      .worldDeckTop(top*).supply(p1, 5).ready

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  test("a world Search draws one card more than the printed three"):
    val top = discordCards.take(6)
    val plainSearch = accepted(start(withAugury(top)))
    assertEquals(hand(plainSearch), top.take(3))
    val ready = withAugury(top)
    val started = accepted(start(ready, modifiers))
    assertEquals(hand(started), top.take(4))
    // A world Search costs 2 until a Vision is drawn: Augury adds a card,
    // not a price. 5 Supply less 2 leave 3.
    assertEquals(Look(SearchFixture.after(started)).player(actor)
      .board.supply.supply, 5 - 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("the player keeps any one of the drawn cards"):
    val top = discordCards.take(6)
    val kept = top(3)
    val done = play(withAugury(top), modifiers, kept, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assert(Look(after).player(actor).advisers.exists(_.id == kept),
      "the fourth drawn card is the one kept")

  test("the draw still stops after a Vision, wherever the Vision falls"):
    val early = discordCards.take(2) ++ Vector(VisionRules.Faith) ++
      discordCards.drop(2).take(3)
    val stopped = accepted(start(withAugury(early), modifiers))
    assertEquals(hand(stopped), early.take(3))
    // A Vision is the fourth card, past the printed three: it is drawn, and the
    // draw counts it.
    val late = discordCards.take(3) ++ Vector(VisionRules.Faith) ++
      discordCards.drop(3).take(2)
    val ready = withAugury(late)
    val reached = accepted(start(ready, modifiers))
    assertEquals(hand(reached), late.take(4))
    assertEquals(SearchFixture.after(reached).game.current.tracks.visionsDrawn,
      ready.game.current.tracks.visionsDrawn + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, reached.events),
      SearchFixture.after(reached))

  test("a Search from a regional discard draws one card more from its top"):
    val region = Region.Cradle // Ancient City's region
    val pile = discordCards.take(5)
    val ready = Table.from(withAugury(Vector.empty)).discarded(region, pile*)
      .unchecked
    val regional = DecisionOptionRef.Button(
      s"search:regional-discard:${region.key}")
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Search, actor, modifiers, Vector(regional)))
    // The pile is drawn from its end, and Augury takes a fourth card.
    assertEquals(hand(started), pile.reverse.take(4))

  test("it is offered for Search, and not for another action"):
    val ready = withAugury(discordCards.take(6))
    val offered = (action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, actor, action)).map(_.id)
    assert(offered(ActionRef.Search).contains(Augury.id))
    assert(!offered(ActionRef.Travel).contains(Augury.id))

  test("it is not offered when the card is out of reach"):
    // `staged` leaves Augury off the table: no player or site holds it.
    assertEquals(
      start(SearchFixture.staged(discordCards.take(6), supply = 5), modifiers)
      .left.toOption, Some(OathViolation.InvalidEventOrder(
        "power denizen.augury is not applicable to this search")))
