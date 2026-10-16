package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class TavernSongsSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val songs = CatalogNames.denizen("Tavern Songs")
  private val source = DecisionOptionRef.Denizen(songs)
  private val home = CatalogNames.site("Ancient City")
  /** Ancient City's region. */
  private val region = Region.Cradle
  private val pileName = SearchSource.name(SearchSource.RegionalDiscard(region))
  /** Five Arcane cards for the pile, the last on top. */
  private val pile = Vector("Ward of Silence", "Arcane Brokers", "Arcane Armor",
    "Glamor", "Wizard's Conclave").map(CatalogNames.denizen(_))

  /** p1's Act at Ancient City with Tavern Songs at the site, and `cards` in
    * the Cradle discard pile, the last named on top. */
  private def staged(cards: Vector[DenizenId]): ReadyGame =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).denizen(songs, at = home)
      .discarded(region, cards*).ready

  /** Whether `player` has peeked `card`, as `ScryerSuite` reads it. */
  private def knows(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    ready.knowledge.advisers.getOrElse(player, Vector.empty).contains(card)

  test("it peeks at the top three cards of the pawn region's pile and " +
      "shows them top first until Done"):
    val ready = staged(pile)
    val top = pile.reverse.take(3)
    val t = accepted(use(ready, TavernSongs, source))
    assert(awaits(t, TavernSongs.inspectDecisionId),
      parked.parkedDecision(t.state).toString)
    queryOf(t, p1) match
      case Some(inspect: DecisionQueryProjection.Inspect) =>
        assertEquals(inspect.cards.map(_.cardId), top.map(_.value))
        assertEquals(inspect.heading,
          Some(s"Tavern Songs: the top of the $pileName"))
      case other => fail(s"expected an Inspect, got $other")
    val seen = after(t)
    assert(top.forall(knows(seen, p1, _)))
    assert(!pile.take(2).exists(knows(seen, p1, _)))
    others(seen).foreach(other => assert(!top.exists(knows(seen, other, _))))
    val done = accepted(answer(t, p1, TavernSongs.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(after(done).game.current.commonCards.discard(region),
      ready.game.current.commonCards.discard(region))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the cards top first and covers the peeks"):
    val t = accepted(use(staged(pile), TavernSongs, source))
    assertEquals(NoteText.said(TavernSongs, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} peeked at the top of the $pileName: " +
        s"${pile.reverse.take(3).map(_.value).mkString(", ")}.",
      covers = true)))

  test("a pile of two shows both"):
    val t = accepted(use(staged(pile.take(2)), TavernSongs, source))
    queryOf(t, p1) match
      case Some(inspect: DecisionQueryProjection.Inspect) =>
        assertEquals(inspect.cards.map(_.cardId),
          pile.take(2).reverse.map(_.value))
      case other => fail(s"expected an Inspect, got $other")

  test("an empty pile asks nothing and says so"):
    val done = accepted(use(staged(Vector.empty), TavernSongs, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(TavernSongs, done.events), Vector(
      NoteText.Said("used.empty",
        s"${p1.value} peeked at the $pileName, which was empty.",
        covers = true)))
