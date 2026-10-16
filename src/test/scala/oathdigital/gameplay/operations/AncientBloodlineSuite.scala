package oathdigital.gameplay.operations

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{ConsentPowers, PowerImplementationStatus}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Ancient Bloodline's lock (card 165, catalog batch 8, slice 11): an
  * enemy of its faceup holder may not move a card at a site the holder
  * rules, unless the holder consents. */
class AncientBloodlineSuite extends munit.FunSuite:
  private val bloodline = CatalogNames.denizen("Ancient Bloodline")
  private val woods = CatalogNames.site("Deep Woods")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val sticks = CatalogNames.relic("Dowsing Sticks")
  private val lock = LockedCards(Set.empty, Vector(AncientBloodline))

  /** Errand Boy and Dowsing Sticks at Deep Woods, which `ruled` gives its
    * ruler, beside p2 holding Ancient Bloodline faceup unless `facedown`. */
  private def woodsBesideBloodlineHolder(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1),
      facedown: Boolean = false): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = woods)
      .denizen(errandBoy, at = woods).relicAt(sticks, at = woods)
      .adviser(p2, bloodline, facedown)).ready

  private def sticksTakenBy(mover: PlayerId): Move = Move(
    Piece.Card(sticks), PositionedLocation(Location.Site(woods)),
    PositionedLocation(Location.PlayArea(mover)),
    resultingOrientation = Some(Orientation.FaceDown), by = Some(mover))

  private def errandBoyDiscardedBy(mover: PlayerId): Discard.Denizen =
    Discard.Denizen(errandBoy, PositionedLocation(Location.Site(woods)),
      Region.Provinces, Suit.Beast, 0, 0, mover)

  private val coversSticksForP1 = ConsentKey(AncientBloodline.id, p1,
    AncientBloodline.moveKey, DecisionOptionRef.Relic(sticks))

  test("an enemy's take of a relic at the holder's site is refused, " +
      "waivable by the holder"):
    val ready = woodsBesideBloodlineHolder()
    assert(PowerAccess.ruledSites(ready, p2).contains(woods))
    val refusal = lock.reason(ready, sticksTakenBy(p1))
    assertEquals(refusal.map(_.code), Some("bloodline-locked"))
    assertEquals(refusal.flatMap(_.waiver), Some(OperationWaiver(
      AncientBloodline.id, Some(p2), coversSticksForP1,
      AncientBloodline.lockedConsent(AncientBloodline.source),
      AncientBloodline.locked(AncientBloodline.source))))

  test("an enemy's discard of a denizen there is refused too, and a bury " +
      "goes through"):
    val ready = woodsBesideBloodlineHolder()
    assertEquals(lock.reason(ready, errandBoyDiscardedBy(p1)).map(_.code),
      Some("bloodline-locked"))
    assertEquals(lock.reason(ready, Bury(BuryableCard.Denizen(errandBoy),
      PositionedLocation(Location.Site(woods)))), None)

  test("the holder moves the card freely"):
    assertEquals(lock.reason(woodsBesideBloodlineHolder(),
      sticksTakenBy(p2)), None)

  test("a facedown Bloodline, or a site the holder does not rule, locks " +
      "nothing"):
    assertEquals(lock.reason(woodsBesideBloodlineHolder(facedown = true),
      sticksTakenBy(p1)), None)
    assertEquals(lock.reason(woodsBesideBloodlineHolder(
      _.bandits(woods, 2)), sticksTakenBy(p1)), None)

  test("a card arriving at the holder's site is not refused"):
    val ready = woodsBesideBloodlineHolder()
    val wrestlers = CatalogNames.denizen("Wrestlers")
    assertEquals(lock.reason(ready, Move(Piece.Card(wrestlers),
      PositionedLocation(Location.PlayArea(p1)),
      PositionedLocation(Location.Site(woods)), by = Some(p1))), None)

  test("a grant remembered for the card lifts the lock, and the holder " +
      "is the granter"):
    val ready = woodsBesideBloodlineHolder()
    val granted = ready.copy(consent = ConsentState(answers = Map(
      coversSticksForP1 -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn.of(ready))))
    assertEquals(ConsentedRestriction(lock).reason(granted,
      sticksTakenBy(p1)), None)
    assertEquals(ConsentPowers.default(catalog)
      .granter(ready, AncientBloodline.id), Some(p2))

  test("the printed set holds the lock, and the card counts as " +
      "implemented"):
    val printed = OperationRestrictions.forCatalog(catalog).printed
    assert(printed.exists {
      case locked: LockedCards => locked.asIf.contains(AncientBloodline)
      case _ => false
    })
    assert(PowerImplementationStatus.implemented(catalog)(
      AncientBloodline.id))
