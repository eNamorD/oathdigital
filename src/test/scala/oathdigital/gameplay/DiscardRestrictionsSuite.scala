package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The discard restrictions: a locked denizen, an intact edifice and a
  * modifier selected for the running action cannot be discarded, whichever
  * path discards them, and a card that can be discarded is unaffected.
  */
class DiscardRestrictionsSuite extends munit.FunSuite:
  private val actor = p1
  private val restrictions = new DiscardRestrictions(catalog, actor)
  /** Locked and adviser-only. */
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  /** The Order edifice Hall of Ministers. */
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val wildCry = CatalogNames.denizen("Wild Cry")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")

  private def discard(card: DenizenId, from: Location): Discard.Denizen =
    Discard.Denizen(card, PositionedLocation(from), Region.Provinces,
      catalog.suitOf(card).get, 0, 0, actor)

  private def refused(ready: ReadyGame, operation: CoreOperation): Boolean =
    restrictions.reason(ready, operation).nonEmpty

  private def holding(card: DenizenId, facedown: Boolean = false): ReadyGame =
    Table.start.adviser(p1, card, facedown = facedown).ready

  private def selecting(ready: ReadyGame, powers: PowerId*): ReadyGame =
    ready.updateCurrent(_.copy(walkerModifiers = powers.toVector))

  test("a locked adviser cannot be discarded from play, and reports why"):
    val ready = holding(lockedCard)
    val reason = restrictions.reason(ready,
      discard(lockedCard, Location.PlayArea(actor))).get
    assertEquals(reason.code, "locked")
    assertEquals(reason.kind, OperationReasonKind.Impossible)

  test("a locked adviser is locked only while it is faceup"):
    val facedown = holding(lockedCard, facedown = true)
    assert(!refused(facedown, discard(lockedCard, Location.PlayArea(actor))))

  test("a locked card drawn by a Search can still be discarded from the hand"):
    val drawn = Table.start.hand(p1, lockedCard).ready
    assert(!refused(drawn, discard(lockedCard, Location.Hand(actor))))

  test("an ordinary adviser can be discarded"):
    assert(!refused(holding(plainCard),
      discard(plainCard, Location.PlayArea(actor))))

  /** The Hall on `side` at p1's site, which p1 rules. */
  private def edificeAt(side: EdificeSide): (ReadyGame, SiteId) =
    val home = Table.homeOf(p1)
    (Table.start.edifice(hall, side, at = home).warbandsAt(home, p1, 1).ready,
      home)

  private def edificeDiscard(site: SiteId) = Discard.RuinedEdifice(hall,
    PositionedLocation(Location.Site(site)), catalog.suitOf(hall).get, 0, 0,
    actor)

  test("an intact edifice is locked, and a ruined one is not"):
    val (intact, site) = edificeAt(EdificeSide.Intact)
    assertEquals(restrictions.reason(intact, edificeDiscard(site)).map(_.code),
      Some("locked"))
    val (ruined, ruinedSite) = edificeAt(EdificeSide.Ruined)
    assert(!refused(ruined, edificeDiscard(ruinedSite)))

  test("a modifier selected for the running action cannot be discarded"):
    val ready = holding(wildCry)
    val operation = discard(wildCry, Location.PlayArea(actor))
    assert(!refused(ready, operation))
    val active = selecting(ready, PowerId("denizen.wild-cry"))
    assertEquals(restrictions.reason(active, operation).map(_.code),
      Some("active-modifier"))
    // A different modifier selected leaves the card free.
    assert(!refused(selecting(ready, PowerId("denizen.tents")), operation))

  test("a relic modifier selected for the running action cannot be discarded"):
    val drum = CatalogNames.relic("Dragonskin Drum")
    val relic = Discard.Relic(drum,
      PositionedLocation(Location.PlayArea(actor)), 0, actor)
    val ready = Table.start.relic(p1, drum).ready
    assert(!refused(ready, relic))
    assert(refused(selecting(ready, PowerId("relic.dragonskin-drum")), relic))

  // ---- The card-play path ----

  private def siteChoice(ready: ReadyGame, card: DenizenId): CardPlay.Choice =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get

  /** p1 stands at Deep Woods, the Beast Homeland, which p1 rules. It is full
    * (three cards): two plain cards and the Hall on `side`. p1 has the Beast
    * Errand Boy in hand, so it may replace one of them.
    */
  private def fullHomeland(side: EdificeSide)
      : (ReadyGame, DenizenId, Vector[DenizenId]) =
    val deepWoods = "Deep Woods"
    val fillers = Vector("Rain Boots", "Wrestlers").map(CatalogNames.denizen(_))
    val ready = Table.start
      .pawn(p1, at = deepWoods).warbandsAt(deepWoods, p1, 1)
      .hand(p1, plainCard)
      .denizen(fillers(0), at = deepWoods).denizen(fillers(1), at = deepWoods)
      .edifice(hall, side, at = deepWoods)
      .ready
    (ready, plainCard, fillers)

  test("a replacement can never be an intact edifice"):
    val (ready, card, fillers) = fullHomeland(EdificeSide.Intact)
    assertEquals(siteChoice(ready, card).replacements.toSet,
      fillers.toSet[CardId])

  test("a ruined edifice can be the replacement, and is discarded, not buried"):
    val (ready, card, fillers) = fullHomeland(EdificeSide.Ruined)
    val choice = siteChoice(ready, card)
    assertEquals(choice.replacements.toSet, fillers.toSet[CardId] + hall)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand)
      .toOption.get
    assert(planned.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!planned.exists(_.isInstanceOf[Bury]))

  test("a locked adviser is not offered as the replacement of a faceup adviser"):
    // p1 holds three facedown advisers, the limit: two plain ones and the
    // locked Sealing Ward.
    val extras = Vector("Rain Boots", "Wrestlers").map(CatalogNames.denizen(_))
    val full = Table.start.hand(p1, plainCard)
      .adviser(p1, extras(0), facedown = true)
      .adviser(p1, extras(1), facedown = true)
      .adviser(p1, lockedCard, facedown = true)
      .ready
    val faceup = CardPlay.legalChoices(catalog, full, actor, plainCard,
      CardPlay.Origin.TemporaryHand).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
    assertEquals(faceup.replacements.toSet, extras.toSet[CardId])
