package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRestrictions}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** Card play offers only the discards the global restrictions permit: never an
  * intact edifice or a faceup locked adviser, while a facedown locked adviser
  * has no restrictions (global operation restrictions design, "Rules"). */
class CardPlayRestrictionsSuite extends munit.FunSuite:
  private val actor = p1
  /** Locked and adviser-only. */
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  private val extras = Vector("Rain Boots", "Wrestlers").map(CatalogNames.denizen(_))

  private def siteChoice(ready: ReadyGame, card: DenizenId): CardPlay.Choice =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get

  /** p1 stands at Deep Woods, the Beast Homeland, which p1 rules. It is full
    * (three cards): two plain cards and the Hall on `side`. p1 has the Beast
    * Errand Boy in hand, so it may replace one of them.
    */
  private def fullHomeland(side: EdificeSide): ReadyGame =
    val deepWoods = "Deep Woods"
    Table.start
      .pawn(p1, at = deepWoods).warbandsAt(deepWoods, p1, 1)
      .hand(p1, plainCard)
      .denizen(extras(0), at = deepWoods).denizen(extras(1), at = deepWoods)
      .edifice(hall, side, at = deepWoods)
      .ready

  test("a replacement can never be an intact edifice"):
    assertEquals(siteChoice(fullHomeland(EdificeSide.Intact), plainCard)
      .replacements.toSet, extras.toSet[CardId])

  test("a ruined edifice can be the replacement, and is discarded, not buried"):
    val ready = fullHomeland(EdificeSide.Ruined)
    assertEquals(siteChoice(ready, plainCard).replacements.toSet,
      extras.toSet[CardId] + hall)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, plainCard,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand)
      .toOption.get
    assert(planned.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!planned.exists(_.isInstanceOf[Bury]))

  /** p1 holds three advisers, the limit: two plain facedown ones and the
    * locked Sealing Ward, facedown or faceup. */
  private def fullAdvisers(lockedFacedown: Boolean): ReadyGame =
    Table.start.hand(p1, plainCard)
      .adviser(p1, extras(0), facedown = true)
      .adviser(p1, extras(1), facedown = true)
      .adviser(p1, lockedCard, facedown = lockedFacedown)
      .ready

  private def faceupReplacements(ready: ReadyGame): Set[CardId] =
    CardPlay.legalChoices(catalog, ready, actor, plainCard,
      CardPlay.Origin.TemporaryHand).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
      .replacements.toSet

  test("a faceup locked adviser is not offered as the replacement"):
    assertEquals(faceupReplacements(fullAdvisers(lockedFacedown = false)),
      extras.toSet[CardId])

  test("a facedown locked adviser can be the replacement"):
    assertEquals(faceupReplacements(fullAdvisers(lockedFacedown = true)),
      extras.toSet[CardId] + lockedCard)

  test("a faceup locked adviser named as the replacement is refused when the " +
      "play runs"):
    val ready = fullAdvisers(lockedFacedown = false)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, plainCard,
      SearchPlacement.Adviser(Orientation.FaceUp, Some(lockedCard)),
      CardPlay.Origin.TemporaryHand).toOption.get
    assert(OperationPipeline.run(ready, planned, OperationPolicy.Permissive,
      OperationRestrictions.forCatalog(catalog).active(Vector.empty,
        Vector.empty))(Right(_)).isLeft)
