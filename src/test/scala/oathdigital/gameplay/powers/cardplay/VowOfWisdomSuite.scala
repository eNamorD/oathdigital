package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Vow of Wisdom (card 203): its holder's play to a site gains 1 secret and
  * no favor. */
class VowOfWisdomSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park}

  private val actor = Table.p1
  private val vow = CatalogNames.denizen("Vow of Wisdom")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  private val powers = WalkerPowers(Vector(VowOfWisdom))
  private val toSite = DecisionOptionRef.SiteSlot(home, 0)

  /** p1's Act at Ancient City, which holds no card, with Wrestlers in hand,
    * the Order bank holding `orderFavor`, and the advisers `held` adds. */
  private def wrestlersInHandAtEmptyAncientCity(orderFavor: Int)(
      held: Table => Table): ReadyGame =
    held(Table.start.turn(actor, Phase.Act).pawn(actor, home)
      .hand(actor, wrestlers).bankFavor(Suit.Order, orderFavor)).ready

  /** Wrestlers played from hand to `slot`. */
  private def playedTo(slot: DecisionOptionRef)(ready: ReadyGame)
      : WalkerOutcome.Finished =
    val tree = build(ready, actor, wrestlers)
    Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.placementDecisionId(wrestlers), slot, actor))

  /** Wrestlers played from hand to Ancient City's first slot. */
  private def playedToSite(ready: ReadyGame): WalkerOutcome.Finished =
    playedTo(toSite)(ready)

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(actor) + Look(ready).faceDownSecrets(actor)

  test("a play to a site gains a secret and no favor"):
    val ready = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(
      _.adviser(actor, vow))
    val done = playedToSite(ready)
    assertEquals(secretsOf(done.treeless), secretsOf(ready) + 1)
    assertEquals(Look(done.treeless).favor(actor), Look(ready).favor(actor))
    assertEquals(Look(done.treeless).bankFavor(Suit.Order), 3)
    assertWireRoundTrips(done.events)

  test("with the suit bank empty, the play still gains a secret"):
    val ready = wrestlersInHandAtEmptyAncientCity(orderFavor = 0)(
      _.adviser(actor, vow))
    val done = playedToSite(ready)
    assertEquals(secretsOf(done.treeless), secretsOf(ready) + 1)
    assertEquals(Look(done.treeless).favor(actor), Look(ready).favor(actor))

  test("without the Vow faceup, the play gains the favor"):
    val unheld = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(identity)
    val facedown = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(
      _.adviser(actor, vow, facedown = true))
    Vector(unheld, facedown).foreach { ready =>
      val done = playedToSite(ready)
      assertEquals(Look(done.treeless).favor(actor),
        Look(ready).favor(actor) + 1)
      assertEquals(secretsOf(done.treeless), secretsOf(ready))
      assertEquals(Look(done.treeless).bankFavor(Suit.Order), 2)
    }

  test("a play to the advisers gains no secret"):
    val ready = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(
      _.adviser(actor, vow))
    val done = playedTo(DecisionOptionRef.AdviserPlay(actor, 1,
      faceup = true))(ready)
    assertEquals(secretsOf(done.treeless), secretsOf(ready))
    assertEquals(Look(done.treeless).bankFavor(Suit.Order), 3)
