package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TinkersFairSuite extends munit.FunSuite:
  import TargetsFixture._

  private val fair = CatalogNames.denizen("Tinker's Fair")
  private val source = DecisionOptionRef.Denizen(fair)
  private val top = RelicId("R08")

  /** p1's Act beside a site Tinker's Fair, with `favor` favor and `top` on
    * the relic deck. Tinker's Fair is site-only; it stands at p1's pawn
    * site. */
  private def staged(favor: Int = 3): ReadyGame =
    Table.start.turn(p1, Phase.Act).denizen(fair, at = Table.homeOf(p1))
      .favor(p1, favor).relicDeckTop(top).ready

  /** `ready` with the relic deck moved to the reliquary. */
  private def emptied(ready: ReadyGame): ReadyGame =
    val current = ready.game.current
    ready.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))

  test("it places 3 favor and takes the top relic facedown"):
    val ready = staged()
    val done = use(ready, TinkersFair, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(top))
    assertEquals(Look(end).player(p1).relics.head.orientation,
      Orientation.FaceDown)
    assertEquals(Look(end).tokensOn(fair), Tokens(3, 0))
    assertEquals(replayed(ready, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))

  test("its line names the relic drawn"):
    val done = use(staged(), TinkersFair, source).toOption.get
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} drew ${top.value} facedown.",
        covers = false)))

  test("an empty relic deck: the cost stays paid and the line says so"):
    val done = use(emptied(staged()), TinkersFair, source).toOption.get
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(after(done)).tokensOn(fair), Tokens(3, 0))
    assertEquals(NoteText.said(TinkersFair, done.events), Vector(
      NoteText.Said("used.empty", "The relic deck was empty.",
        covers = false)))

  test("it is unusable with less than 3 favor"):
    val broke = staged(favor = 2)
    assert(!usableNow(broke).exists(_.power.id == TinkersFair.id))
    assert(use(broke, TinkersFair, source).isLeft)
