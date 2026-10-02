package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class TinkersFairSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val fair = CatalogNames.denizen("Tinker's Fair")
  private val source = DecisionOptionRef.Denizen(fair)
  private val top = CatalogNames.relic("Whistle")

  /** p1's Act at Ancient City with Tinker's Fair at the site, `favor` favor
    * and `top` on the relic deck. Tinker's Fair is site-only. */
  private def stagedTable(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, "Ancient City")
      .denizen(fair, at = "Ancient City").favor(p1, favor).relicDeckTop(top)
  private def staged(favor: Int = 3): ReadyGame = stagedTable(favor).ready

  test("it places 3 favor and takes the top relic facedown"):
    val ready = staged()
    val done = accepted(use(ready, TinkersFair, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(top))
    assertEquals(Look(end).player(p1).relics.head.orientation,
      Orientation.FaceDown)
    assertEquals(Look(end).tokensOn(fair), Tokens(3, 0))
    assertEquals(replayed(ready, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("its line names the relic drawn"):
    val done = accepted(use(staged(), TinkersFair, source))
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} drew ${top.value} facedown.",
        covers = false)))

  test("an empty relic deck: the cost stays paid and the line says so"):
    val done = accepted(use(stagedTable().relicDeckEmptied.ready, TinkersFair,
      source))
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(after(done)).tokensOn(fair), Tokens(3, 0))
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said("used.empty", "The relic deck was empty.",
        covers = false)))

  test("it is unusable with less than 3 favor"):
    val broke = staged(favor = 2)
    assert(!usableNow(broke).exists(_.power.id == TinkersFair.id))
    assert(use(broke, TinkersFair, source).isLeft)
