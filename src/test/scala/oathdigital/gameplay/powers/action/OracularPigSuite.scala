package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection

class OracularPigSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val pig = RelicId("R14")
  private val source = DecisionOptionRef.Relic(pig)
  /** Whether `player` has peeked `card`, which the projector then names to
    * them alone (`CardKnowledgeSuite`). */
  private def knows(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    ready.knowledge.advisers.getOrElse(player, Vector.empty).contains(card)

  /** The actor holds Oracular Pig. With `deck`, the world deck holds its
    * first `deck` cards and the rest wait in the Provinces discard pile. */
  private def staged(deck: Option[Int] = None): ReadyGame =
    val holding = inPhase(withRelic(base, pig), Phase.Act)
    deck.fold(holding)(size => holding.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.take(size),
        regionalDiscards = c.commonCards.regionalDiscards.updated(
          Region.Provinces, c.commonCards.discard(Region.Provinces) ++
            c.commonCards.worldDeck.drop(size))))))
  private def deckOf(ready: ReadyGame) = ready.game.current.commonCards.worldDeck

  private def shown(t: OathTransition): Vector[String] =
    queryOf(t, actor) match
      case Some(DecisionQueryProjection.Inspect(cards, _, heading)) =>
        assertEquals(heading, Some("Oracular Pig: the top of the world deck"))
        assert(cards.forall(!_.hidden), cards.toString)
        cards.map(_.cardId)
      case other => fail(s"expected an Inspect, got $other")

  test("Oracular Pig is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(OracularPig.id).isDefined)

  test("the top three cards are peeked and shown top first until Done"):
    val ready = staged()
    val top = deckOf(ready).take(3)
    val t = use(ready, OracularPig, source).toOption.get
    assert(awaits(t, OracularPig.inspectDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(shown(t), top.map(_.value))
    assert(top.forall(knows(after(t), actor, _)))
    others(after(t)).foreach(other => assert(
      !top.exists(knows(after(t), other, _))))
    assertEquals(deckOf(after(t)), deckOf(ready))
    val done = answer(t, actor, OracularPig.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line lists the top of the deck and covers the peeks"):
    val ready = staged()
    val top = deckOf(ready).take(3)
    val t = use(ready, OracularPig, source).toOption.get
    assertEquals(NoteText.said(OracularPig, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} peeked at the top of the world deck: " +
        s"${top.map(_.value).mkString(", ")}.", covers = true)))

  test("a deck of two shows both"):
    val ready = staged(deck = Some(2))
    val t = use(ready, OracularPig, source).toOption.get
    assertEquals(shown(t), deckOf(ready).map(_.value))

  test("an empty world deck asks nothing and writes no line"):
    val ready = staged(deck = Some(0))
    val t = use(ready, OracularPig, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, actor)
    assertEquals(NoteText.said(OracularPig, t.events), Vector.empty)
