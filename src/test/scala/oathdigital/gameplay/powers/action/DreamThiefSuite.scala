package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class DreamThiefSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val thief = CatalogNames.denizen("Dream Thief")
  private val source = DecisionOptionRef.Denizen(thief)
  // Two Hearth denizens with no power of their own.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val vision = VisionRules.Faith

  /** p1's Act holding Dream Thief (slot 0) with `favor` favor and a facedown
    * Vision (slot 1). p2 holds Homesteaders facedown and p3 Land Warden
    * facedown, each in slot 0. */
  private def staged(favor: Int = 2): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, thief).favor(p1, favor)
      .adviser(p1, vision, facedown = true)
      .adviser(p2, homesteaders, facedown = true)
      .adviser(p3, landWarden, facedown = true)

  private def slot(owner: PlayerId, at: Int): DecisionAnswer =
    pick(DecisionOptionRef.AdviserSlot(owner, at))
  private def wire(owner: PlayerId, at: Int): (String, String) =
    "adviser-slot" -> s"${owner.value}:$at"

  /** Every event of the swap's three commands, and the walk after the
    * last. */
  private case class Swapped(events: Vector[OathEvent], done: OathTransition)

  /** Uses it on `ready`, then answers `first` and `second`. */
  private def swap(ready: ReadyGame, first: DecisionAnswer,
      second: DecisionAnswer): Swapped =
    val t = accepted(use(ready, DreamThief, source))
    val one = accepted(answer(t, p1, DreamThief.firstDecisionId, first))
    val done = accepted(answer(one, p1, DreamThief.secondDecisionId, second))
    Swapped(t.events ++ one.events ++ done.events, done)

  test("it places 2 favor and offers every facedown adviser by slot"):
    val t = accepted(use(staged().ready, DreamThief, source))
    assert(awaits(t, DreamThief.firstDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(thief), Tokens(2, 0))
    assertEquals(offered(t, p1),
      Some(Vector(wire(p1, 1), wire(p2, 0), wire(p3, 0))))

  test("the second question offers only other players' facedown advisers"):
    val t = accepted(use(staged().ready, DreamThief, source))
    val one = accepted(answer(t, p1, DreamThief.firstDecisionId, slot(p2, 0)))
    assert(awaits(one, DreamThief.secondDecisionId),
      parked.parkedDecision(one.state).toString)
    assertEquals(offered(one, p1), Some(Vector(wire(p1, 1), wire(p3, 0))))

  test("the two cards swap, stay facedown, and each owner still knows the " +
      "card it gave up"):
    val ready = staged().ready
    val Swapped(events, done) = swap(ready, slot(p2, 0), slot(p3, 0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(landWarden, Orientation.FaceDown, Tokens.empty)))
    assertEquals(Look(end).player(p3).advisers, Vector[AdviserState](
      DenizenState(homesteaders, Orientation.FaceDown, Tokens.empty)))
    assert(end.knowledge.advisers.getOrElse(p2, Vector.empty)
      .contains(homesteaders), end.knowledge.toString)
    assert(end.knowledge.advisers.getOrElse(p3, Vector.empty)
      .contains(landWarden), end.knowledge.toString)
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("the player's own facedown adviser may be swapped"):
    val done = swap(staged().ready, slot(p1, 1), slot(p2, 0)).done
    val end = after(done)
    assertEquals(Look(end).advisers(p1), Vector[CardId](thief, homesteaders))
    assertEquals(Look(end).advisers(p2), Vector[CardId](vision))

  test("its line names both owners and both cards"):
    val done = swap(staged().ready, slot(p2, 0), slot(p3, 0)).done
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} swapped ${p2.value}'s " +
        s"${homesteaders.value} with ${p3.value}'s ${landWarden.value}.",
        covers = false)))

  test("with fewer than two players holding a facedown adviser, nothing is " +
      "asked and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, thief)
      .favor(p1, 2).adviser(p2, homesteaders, facedown = true)
      .adviser(p2, landWarden, facedown = true).ready
    val done = accepted(use(ready, DreamThief, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(thief), Tokens(2, 0))
    assertEquals(Look(after(done)).advisers(p2),
      Vector[CardId](homesteaders, landWarden))
    assertEquals(NoteText.said(DreamThief, done.events), Vector(
      NoteText.Said("used.none",
        "No two facedown advisers could be swapped.", covers = false)))

  test("it is unusable with 1 favor"):
    val broke = staged(favor = 1).ready
    assert(!usableNow(broke).exists(_.power.id == DreamThief.id))
    assert(use(broke, DreamThief, source).isLeft)
