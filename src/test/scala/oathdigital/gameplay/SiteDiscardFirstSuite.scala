package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PlacementPicks, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** A site discard permission through the walker: a Replace site slot
  * discards its card with the standard returns, at any capacity.
  * `discardFirst` is PlacementFixture's stub power that adds
  * `testPermission`, and `limitTwo` its stub that lowers the adviser limit to
  * two. The slot builder's own cases are `CardSlotsSuite`'s.
  */
class SiteDiscardFirstSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, discardFirst,
    limitTwo, options, park}

  private val powers = WalkerPowers(Vector(discardFirst))
  /** No Homeland, room for three, in the Cradle. */
  private val home = CatalogNames.site("Ancient City")
  /** The Beast card p1 plays. The other cards are plain and of other suits. */
  private val card = CatalogNames.denizen("Errand Boy")
  private val kept = CatalogNames.denizen("Rain Boots")
  private val other = CatalogNames.denizen("Ancient Binding")
  private val hall = CatalogNames.edifice("Hall of Ministers")

  /** p1 stands at Ancient City, which holds `cards`, with Errand Boy in hand. */
  private def playing(cards: DenizenId*): Table =
    cards.foldLeft(Table.start.pawn(p1, home).hand(p1, card))((table, held) =>
      table.denizen(held, at = home))

  private def slot(index: Int): DecisionOptionRef =
    DecisionOptionRef.SiteSlot(home, index)

  /** Plays Errand Boy to `ref` under `under`, returning the finished game. */
  private def playedTo(ready: ReadyGame, ref: DecisionOptionRef,
      under: WalkerPowers = powers): ReadyGame =
    val tree = build(ready, p1, card)
    as[WalkerOutcome.Finished](answer(ready, tree, park(ready, tree, under),
      under, cardPlayDecisionId(card, "place"), ref, p1)).treeless

  test("the empty slot plays the card and discards nothing"):
    val done = playedTo(playing(kept).ready, slot(1))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(p1), Vector.empty)

  test("a Replace slot discards its card with the standard returns"):
    val ready = playing(kept).tokens(kept, favor = 1, secrets = 1).ready
    val done = playedTo(ready, slot(0))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](card))
    // Ancient City is in the Cradle, so the discard goes to the Provinces.
    assertEquals(done.game.current.commonCards.discard(Region.Provinces).last,
      kept)
    // The favor on the discarded card returns to its suit bank (Rain Boots is
    // Nomad), its secret to the player facedown.
    assertEquals(done.banks.favor(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 1)
    assertEquals(Look(done).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)

  test("a full site takes the play only through a Replace slot, and only " +
      "under the permission"):
    val fillers = Vector(kept, other, CatalogNames.denizen("Wrestlers"))
    val ready = playing(fillers*).ready
    val tree = build(ready, p1, card)
    def siteSlots(under: WalkerPowers) = PlacementPicks.siteSlotRefs(
      options(ready, tree, park(ready, tree, under), under))
    assertEquals(siteSlots(WalkerPowers.empty), Vector.empty)
    assertEquals(siteSlots(powers), Vector(slot(0), slot(1), slot(2)))
    val done = playedTo(ready, slot(0))
    assertEquals(Look(done).cardsAt(home).size, 3)
    assert(Look(done).cardsAt(home).contains(card))

  test("a ruined edifice may be replaced, and it goes back to the edifice deck"):
    val ready = playing(kept).edifice(hall, EdificeSide.Ruined, at = home)
      .warbandsAt(home, p1, 1).ready
    val done = playedTo(ready, slot(1))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)

  test("the permission composes with an adviser limit through the walker"):
    val ready = playing(kept).adviser(p1, "Wrestlers", facedown = true)
      .adviser(p1, "Battle Honors", facedown = true).ready
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, p1, card)
    val offered = options(ready, tree, park(ready, tree, both), both)
    // A limit of two with two advisers held: a faceup play replaces one.
    assertEquals(offered.filter(ref => CardPlayProcedure.playedAs(ref)
      .contains(PlayedAs.Faceup)), Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true),
        DecisionOptionRef.AdviserPlay(p1, 1, faceup = true)))
    // And the site still offers its empty slot and the Replace slot.
    assertEquals(PlacementPicks.siteSlotRefs(offered),
      Vector(slot(1), slot(0)))
