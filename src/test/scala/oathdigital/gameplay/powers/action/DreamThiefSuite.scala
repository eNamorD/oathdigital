package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class DreamThiefSuite extends munit.FunSuite:
  import TargetsFixture._

  private val thief = CatalogNames.denizen("Dream Thief")
  private val source = DecisionOptionRef.Denizen(thief)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val vision = VisionId("vision:vision-of-faith")

  /** p1's Act holding Dream Thief (slot 0) with `favor` favor and a facedown
    * Vision (slot 1). p2 holds plain(0) facedown and p3 plain(1) facedown,
    * each in slot 0. */
  private def staged(favor: Int = 2): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, thief).favor(p1, favor)
      .adviser(p1, vision, facedown = true)
      .adviser(p2, plain(0), facedown = true)
      .adviser(p3, plain(1), facedown = true)

  private def slot(owner: PlayerId, at: Int): DecisionAnswer =
    pick(DecisionOptionRef.AdviserSlot(owner, at))
  private def wire(owner: PlayerId, at: Int): (String, String) =
    "adviser-slot" -> s"${owner.value}:$at"

  /** Uses it on `ready`, then answers `first` and `second`. */
  private def swap(ready: ReadyGame, first: DecisionAnswer,
      second: DecisionAnswer) =
    val t = use(ready, DreamThief, source).toOption.get
    val one = answer(t, p1, DreamThief.firstDecisionId, first).toOption.get
    val done = answer(one, p1, DreamThief.secondDecisionId, second)
      .toOption.get
    (t.events ++ one.events ++ done.events, done)

  test("it places 2 favor and offers every facedown adviser by slot"):
    val t = use(staged().ready, DreamThief, source).toOption.get
    assert(awaits(t, DreamThief.firstDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(thief), Tokens(2, 0))
    assertEquals(offered(t, p1),
      Some(Vector(wire(p1, 1), wire(p2, 0), wire(p3, 0))))

  test("the second question offers only other players' facedown advisers"):
    val t = use(staged().ready, DreamThief, source).toOption.get
    val one = answer(t, p1, DreamThief.firstDecisionId, slot(p2, 0))
      .toOption.get
    assert(awaits(one, DreamThief.secondDecisionId),
      parked.parkedDecision(one.state).toString)
    assertEquals(offered(one, p1), Some(Vector(wire(p1, 1), wire(p3, 0))))

  test("the two cards swap, stay facedown, and each owner still knows the " +
      "card it gave up"):
    val ready = staged().ready
    val (events, done) = swap(ready, slot(p2, 0), slot(p3, 0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(plain(1), Orientation.FaceDown, Tokens.empty)))
    assertEquals(Look(end).player(p3).advisers, Vector[AdviserState](
      DenizenState(plain(0), Orientation.FaceDown, Tokens.empty)))
    assert(end.knowledge.advisers.getOrElse(p2, Vector.empty)
      .contains(plain(0)), end.knowledge.toString)
    assert(end.knowledge.advisers.getOrElse(p3, Vector.empty)
      .contains(plain(1)), end.knowledge.toString)
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("the player's own facedown adviser may be swapped"):
    val (_, done) = swap(staged().ready, slot(p1, 1), slot(p2, 0))
    val end = after(done)
    assertEquals(Look(end).advisers(p1), Vector[CardId](thief, plain(0)))
    assertEquals(Look(end).advisers(p2), Vector[CardId](vision))

  test("its line names both owners and both cards"):
    val (_, done) = swap(staged().ready, slot(p2, 0), slot(p3, 0))
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} swapped ${p2.value}'s " +
        s"${plain(0).value} with ${p3.value}'s ${plain(1).value}.",
        covers = false)))

  test("with fewer than two players holding a facedown adviser, nothing is " +
      "asked and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, thief)
      .favor(p1, 2).adviser(p2, plain(0), facedown = true)
      .adviser(p2, plain(1), facedown = true).ready
    val done = use(ready, DreamThief, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(thief), Tokens(2, 0))
    assertEquals(Look(after(done)).advisers(p2),
      Vector[CardId](plain(0), plain(1)))
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said("used.none",
        "No two facedown advisers could be swapped.", covers = false)))

  test("it is unusable with 1 favor"):
    val broke = staged(favor = 1).ready
    assert(!usableNow(broke).exists(_.power.id == DreamThief.id))
    assert(use(broke, DreamThief, source).isLeft)
