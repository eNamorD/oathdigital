package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection

class ScryerSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val scryer = DenizenId("19")
  private val source = DecisionOptionRef.Denizen(scryer)
  /** Whether `player` has peeked `card`, which the projector then names to
    * them alone (`CardKnowledgeSuite`). */
  private def knows(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    ready.knowledge.advisers.getOrElse(player, Vector.empty).contains(card)

  /** The actor holds one faceup secret beside a site Scryer. The Cradle
    * discard pile holds the world deck's top six cards, stored top last, and
    * the Hinterland's is empty. */
  private val staged: ReadyGame =
    inPhase(withSecrets(atHome(base, scryer), actor, 1, 0), Phase.Act)
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.drop(6),
        regionalDiscards = c.commonCards.regionalDiscards
          .updated(Region.Cradle, c.commonCards.worldDeck.take(6))
          .updated(Region.Hinterland, Vector.empty))))
  /** The Cradle discard pile, top first. */
  private val top: Vector[WorldCardId] =
    staged.game.current.commonCards.discard(Region.Cradle).reverse
  private def started = use(staged, Scryer, source).toOption.get
  private def peekAt(from: OathTransition, region: Region) =
    answer(from, actor, Scryer.pileDecisionId, pick(Scryer.ref(region)))
      .toOption.get

  test("it places a secret and asks which discard pile"):
    val t = started
    assert(awaits(t, Scryer.pileDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(PaidActionHarness.tokensOn(after(t), scryer), Tokens(0, 1))
    assertEquals(offered(t, actor),
      Some(Scryer.piles.map(Scryer.ref).map(r => r.kind -> r.wireId)))

  test("the pile's cards are peeked and shown top first until Done"):
    val t = started
    val peeking = peekAt(t, Region.Cradle)
    assert(awaits(peeking, Scryer.inspectDecisionId),
      parked.parkedDecision(peeking.state).toString)
    queryOf(peeking, actor) match
      case Some(DecisionQueryProjection.Inspect(cards, done, heading)) =>
        assertEquals(cards.map(_.cardId), top.map(_.value))
        assert(cards.forall(!_.hidden), cards.toString)
        assertEquals(heading, Some("Scryer: the Cradle discard pile"))
        assertEquals(done.label, "Done")
      case other => fail(s"expected an Inspect, got $other")
    val seen = after(peeking)
    assert(top.forall(knows(seen, actor, _)))
    others(seen).foreach(other =>
      assert(!top.exists(knows(seen, other, _))))
    assertEquals(seen.game.current.commonCards.discard(Region.Cradle),
      staged.game.current.commonCards.discard(Region.Cradle))
    val done = answer(peeking, actor, Scryer.inspectDecisionId,
      pick(DecisionQuery.Inspect.Done)).toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val events = t.events ++ peeking.events ++ done.events
    assertEquals(replayed(staged, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("its line lists the pile top first and covers the peeks"):
    val t = started
    val peeking = peekAt(t, Region.Cradle)
    assertEquals(NoteText.said(Scryer, t.events ++ peeking.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} peeked at the Cradle discard pile: " +
          s"${top.map(_.value).mkString(", ")}.", covers = true)))

  test("an empty pile asks nothing more and says so"):
    val t = started
    val peeking = peekAt(t, Region.Hinterland)
    parked.assertResumed(peeking.state, Phase.Act, actor)
    assertEquals(NoteText.said(Scryer, t.events ++ peeking.events),
      Vector(NoteText.Said("used.empty",
        s"${actor.value} peeked at the Hinterland discard pile, " +
          "which was empty.", covers = true)))

  test("it is unusable without a faceup secret"):
    val broke = withSecrets(staged, actor, 0, 1)
    assert(!usableNow(broke).exists(_.power.id == Scryer.id))
    assert(use(broke, Scryer, source).isLeft)
