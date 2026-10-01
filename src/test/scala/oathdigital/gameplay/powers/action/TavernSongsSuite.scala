package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

class TavernSongsSuite extends munit.FunSuite:
  import TargetsFixture._

  private val songs = CatalogNames.denizen("Tavern Songs")
  private val source = DecisionOptionRef.Denizen(songs)
  private val home = Table.homeOf(p1)
  private val region = Table.start.ready.game.current.map.regionOf(home).get
  private val pileName = SearchSource.name(SearchSource.RegionalDiscard(region))
  /** Five cards for the pile, the last on top. */
  private val pile = SearchFixture.denizensOf(Suit.Arcane).take(5)

  /** p1's Act beside a site Tavern Songs, with `cards` in the discard pile
    * of p1's region, the last named on top. */
  private def staged(cards: Vector[DenizenId]): ReadyGame =
    Table.start.turn(p1, Phase.Act).denizen(songs, at = home)
      .discarded(region, cards*).ready

  /** Whether `player` has peeked `card`, as `ScryerSuite` reads it. */
  private def knows(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    ready.knowledge.advisers.getOrElse(player, Vector.empty).contains(card)

  test("it peeks at the top three cards of the pawn region's pile and " +
      "shows them top first until Done"):
    val ready = staged(pile)
    val top = pile.reverse.take(3)
    val t = use(ready, TavernSongs, source).toOption.get
    assert(awaits(t, TavernSongs.inspectDecisionId),
      parked.parkedDecision(t.state).toString)
    queryOf(t, p1) match
      case Some(DecisionQueryProjection.Inspect(cards, _, heading)) =>
        assertEquals(cards.map(_.cardId), top.map(_.value))
        assertEquals(heading, Some(s"Tavern Songs: the top of the $pileName"))
      case other => fail(s"expected an Inspect, got $other")
    val seen = after(t)
    assert(top.forall(knows(seen, p1, _)))
    assert(!pile.take(2).exists(knows(seen, p1, _)))
    others(seen).foreach(other => assert(!top.exists(knows(seen, other, _))))
    val done = answer(t, p1, TavernSongs.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(after(done).game.current.commonCards.discard(region),
      ready.game.current.commonCards.discard(region))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the cards top first and covers the peeks"):
    val t = use(staged(pile), TavernSongs, source).toOption.get
    assertEquals(NoteText.said(TavernSongs, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} peeked at the top of the $pileName: " +
        s"${pile.reverse.take(3).map(_.value).mkString(", ")}.",
      covers = true)))

  test("a pile of two shows both"):
    val t = use(staged(pile.take(2)), TavernSongs, source).toOption.get
    queryOf(t, p1) match
      case Some(DecisionQueryProjection.Inspect(cards, _, _)) =>
        assertEquals(cards.map(_.cardId), pile.take(2).reverse.map(_.value))
      case other => fail(s"expected an Inspect, got $other")

  test("an empty pile asks nothing and says so"):
    val done = use(staged(Vector.empty), TavernSongs, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(TavernSongs, done.events), Vector(
      NoteText.Said("used.empty",
        s"${p1.value} peeked at the $pileName, which was empty.",
        covers = true)))
