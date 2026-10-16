package oathdigital.model

import oathdigital.testkit.Table.{p1, p2}

/** A move names the player who moves it (catalog batch 8, slice 11): the
  * player its composite names, or the one its builder gives, or none when
  * nobody moves it. */
class MoveActorSuite extends munit.FunSuite:
  private val site = SiteId("site")
  private val atSite = PositionedLocation(Location.Site(site))
  private val atOtherSite = PositionedLocation(Location.Site(SiteId("other")))
  private val relic = RelicId("R1")
  private val denizen = DenizenId("D1")

  /** The mover of each card move `operation` makes, in order. */
  private def cardMovers(operation: Operation): Vector[Option[PlayerId]] =
    Operation.flatten(operation).collect {
      case move: Move if move.piece.isInstanceOf[Piece.Card] => move.by
    }

  test("a composite that names its player names it on the card it moves"):
    assertEquals(cardMovers(Take(Piece.Card(relic), p1, Location.Site(site),
      Location.PlayArea(p1))), Vector(Some(p1)))
    assertEquals(cardMovers(Give(Piece.Card(relic), p1,
      Location.PlayArea(p1), Location.PlayArea(p2))), Vector(Some(p1)))
    assertEquals(cardMovers(Discard.Denizen(denizen, atSite,
      Region.Provinces, Suit.Beast, 0, 0, p1)), Vector(Some(p1)))
    assertEquals(cardMovers(Discard.Relic(relic, atSite, 0, p1)),
      Vector(Some(p1)))
    assertEquals(cardMovers(Discard.RuinedEdifice(EdificeId("E1"), atSite,
      Suit.Beast, 0, 0, p1)), Vector(Some(p1)))

  test("a swap names its mover on both of its moves"):
    assertEquals(cardMovers(Swap(denizen, atSite, DenizenId("D2"),
      atOtherSite, by = Some(p1))), Vector(Some(p1), Some(p1)))

  test("an actorless discard names no mover, and its player still takes " +
      "the secrets"):
    val discard = Discard.Denizen(denizen, atSite, Region.Provinces,
      Suit.Beast, 0, 2, p1, actorless = true)
    assertEquals(cardMovers(discard), Vector(None))
    assertEquals(discard.by, None)
    assert(Operation.flatten(discard).exists {
      case Move(Piece.Secrets(2), _, PositionedLocation(Location.PlayArea(
        `p1`), _, _), _, _) => true
      case _ => false
    })
    assertEquals(cardMovers(Discard.RuinedEdifice(EdificeId("E1"), atSite,
      Suit.Beast, 0, 0, p1, actorless = true)), Vector(None))

  test("a card moved from a site may name no mover"):
    assertEquals(Move(Piece.Card(relic), atSite,
      PositionedLocation(Location.PlayArea(p1))).by, None)
