package oathdigital.gameplay

import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The restriction-icon rule (catalog batch 4 design, "The restriction-icon
  * rule"): whichever operation moves it, an adviser-only card cannot land
  * faceup at a site, and a site-only card cannot land faceup in a player's
  * advisers.
  */
class RestrictionIconsSuite extends munit.FunSuite:
  private val set = OperationRestrictions.forCatalog(catalog)
  /** Adviser-only, and not locked. */
  private val adviserOnly = CatalogNames.denizen("Enchantress")
  /** Site-only. */
  private val siteOnly = CatalogNames.denizen("Wayside Inn")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  /** p1's pawn site on the quiet table. */
  private val home = CatalogNames.site("Ancient City")
  private val atHome = PositionedLocation(Location.Site(home))
  private val mine = PositionedLocation(Location.PlayArea(p1))
  private val theirs = PositionedLocation(Location.PlayArea(p2))

  private def refusal(ready: ReadyGame, operation: CoreOperation)
      : Option[String] =
    set.active(Vector.empty, Vector.empty)
      .flatMap(_.reason(ready, operation)).headOption.map(_.code)

  /** p1 holds the adviser-only card faceup; the plain card stands at p1's
    * site. */
  private def adviserOnlyHeld: ReadyGame =
    Table.start.adviser(p1, adviserOnly).denizen(plainCard, at = home).ready

  /** The site-only card stands at p1's site; p1 holds the plain card
    * faceup. */
  private def siteOnlyAtHome: ReadyGame =
    Table.start.denizen(siteOnly, at = home).adviser(p1, plainCard).ready

  test("an adviser-only adviser cannot be moved or swapped to a site"):
    val ready = adviserOnlyHeld
    assertEquals(Vector[CoreOperation](
      Move(Piece.Card(adviserOnly), mine, atHome),
      Swap(adviserOnly, mine, plainCard, atHome)).map(refusal(ready, _)),
      Vector.fill(2)(Some("restriction-icon")))

  test("a site-only card cannot be taken, moved or swapped into advisers " +
      "faceup"):
    val ready = siteOnlyAtHome
    assertEquals(Vector[CoreOperation](
      Take(Piece.Card(siteOnly), p1, Location.Site(home),
        Location.PlayArea(p1)),
      Move(Piece.Card(siteOnly), atHome, mine),
      Swap(siteOnly, atHome, plainCard, mine)).map(refusal(ready, _)),
      Vector.fill(3)(Some("restriction-icon")))

  test("a site-only card that lands facedown passes, as Horned Mask takes it"):
    assertEquals(refusal(siteOnlyAtHome, Move(Piece.Card(siteOnly), atHome,
      mine, resultingOrientation = Some(Orientation.FaceDown))), None)

  test("a facedown adviser has no restrictions"):
    val ready = Table.start.adviser(p1, siteOnly, facedown = true).ready
    assertEquals(Vector[CoreOperation](
      Move(Piece.Card(siteOnly), mine, theirs),
      Give(Piece.Card(siteOnly), p1, Location.PlayArea(p1),
        Location.PlayArea(p2))).map(refusal(ready, _)),
      Vector.fill(2)(None))

  test("a card that lands where its icon allows passes"):
    val held = Table.start.adviser(p1, adviserOnly).adviser(p2, plainCard)
      .ready
    assertEquals(refusal(held, Swap(adviserOnly, mine, plainCard, theirs)),
      None)
    val elsewhere =
      PositionedLocation(Location.Site(CatalogNames.site("Broken Peaks")))
    assertEquals(refusal(siteOnlyAtHome,
      Move(Piece.Card(siteOnly), atHome, elsewhere)), None)
    val plain = Table.start.adviser(p1, plainCard).ready
    assertEquals(refusal(plain, Move(Piece.Card(plainCard), mine, atHome)),
      None)

  test("a card leaving a hand, or turned over in its own play area, is not " +
      "judged"):
    val drawn = Table.start.hand(p1, adviserOnly).ready
    assertEquals(refusal(drawn, Move(Piece.Card(adviserOnly),
      PositionedLocation(Location.Hand(p1)), atHome,
      Some(Orientation.FaceUp))), None)
    val facedown = Table.start.adviser(p1, siteOnly, facedown = true).ready
    assertEquals(refusal(facedown, Move(Piece.Card(siteOnly), mine, mine,
      Some(Orientation.FaceUp))), None)

  test("Bury ignores the icons"):
    assertEquals(refusal(adviserOnlyHeld,
      Bury(BuryableCard.Denizen(adviserOnly), mine)), None)

  test("the refusal is Impossible, so an optional move is skipped"):
    val reason = set.active(Vector.empty, Vector.empty).flatMap(_.reason(
      adviserOnlyHeld, Move(Piece.Card(adviserOnly), mine, atHome))).head
    assertEquals(reason.kind, OperationReasonKind.Impossible)
