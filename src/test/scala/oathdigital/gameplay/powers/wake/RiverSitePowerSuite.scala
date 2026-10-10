package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.Unwrap.accepted

class RiverSitePowerSuite extends munit.FunSuite:
  import PowerFixture._
  import RiverFixture._
  import PhasePowerHarness.{after, answer, awaits, offered, ops, pick,
    replayed, usableNow, UsedAndAnswered}

  private val source = DecisionOptionRef.Site(ancientCity)
  private def use(ready: ReadyGame) = PhasePowerHarness.use(ready, river,
    source)
  private def rivers(ready: ReadyGame) = usableNow(ready).collect {
    case usable if usable.power.isInstanceOf[RiverSitePower] =>
      usable.power.id -> usable.ref }

  /** Starts Ancient City's River and answers the site question: the use
    * that asked for a site, and the answer that placed the pawn. */
  private def placedAt(ready: ReadyGame, at: SiteId): UsedAndAnswered =
    val asked = accepted(use(ready))
    assert(awaits(asked, RiverSitePower.siteDecisionId))
    val siteChoice = pick(DecisionOptionRef.Site(at))
    UsedAndAnswered(asked, accepted(
      answer(asked, actor, RiverSitePower.siteDecisionId, siteChoice)))

  test("every River the catalog prints is a registered phase power"):
    val printed = catalog.sites.flatMap(_.handlers).filter(
      _.value.endsWith(".river")).map(_.value).toSet
    assertEquals(PhasePowerCatalog.default(catalog).powers.collect {
      case power: RiverSitePower => power.id.value }.toSet, printed)

  test("it is usable in the Wake at a River while another River is in play"):
    assertEquals(rivers(staged(Vector(riverbank, tidalMarshes), Phase.Wake)),
      Vector(river.id -> source))

  test("it is not usable when no other River is in play"):
    val alone = staged(Vector.empty, Phase.Wake)
    assertEquals(rivers(alone), Vector.empty)
    assertEquals(use(alone).left.toOption,
      Some(Refusals.nothingToDo(river.id, source)))

  test("it is not usable from a site that is not a River"):
    val ready = staged(Vector(riverbank, tidalMarshes), Phase.Wake)
    // Broken Peaks is in play and is not a River.
    val dry = CatalogNames.site("Broken Peaks")
    assert(ready.game.current.map.inPlay.contains(dry))
    assert(!RiverSitePower.isRiver(dry))
    val away = Table.from(ready).pawn(actor, dry).unchecked
    assertEquals(rivers(away), Vector.empty)
    assertEquals(use(away).left.toOption,
      Some(Refusals.inaccessibleSource(river.id, source)))

  test("it offers every other River in play, in map order"):
    val ready = staged(Vector(riverbank, tidalMarshes), Phase.Wake)
    val expected = ready.game.current.map.inPlay
      .filter(Set(riverbank, tidalMarshes).contains)
    assertEquals(expected.size, 2)
    val parked = accepted(use(ready))
    assertEquals(offered(parked, actor), Some(expected.map(site =>
      "site" -> site.value)))

  test("it places the pawn at the chosen River without Travel or Supply"):
    val ready = staged(Vector(riverbank, tidalMarshes), Phase.Wake)
    val placement = placedAt(ready, riverbank)
    val placed = after(placement.answered)
    assertEquals(Look(placed).player(actor).pawnSite, Some(riverbank))
    assertEquals(Look(placed).supply(actor), Look(ready).supply(actor))
    val moves = ops(placement.events).collect {
      case move @ Move(Piece.Pawn(_), _, _, _) => move }
    assertEquals(moves, Vector(Move(Piece.Pawn(actor),
      PositionedLocation(Location.Site(ancientCity)),
      PositionedLocation(Location.Site(riverbank)))))
    assertEquals(replayed(ready, placement.events),
      Right(placement.answered.state))

  test("it is once per turn from each River"):
    val done = placedAt(staged(Vector(riverbank, tidalMarshes), Phase.Wake),
      riverbank).answered
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(ancientCity),
      river.id)
    assert(after(done).game.current.turn.usedPowers.contains(used))
    // The driver has parked the Wake menu, which is not the subject here.
    val unparked = Table.from(after(done)).update(_.updateCurrent(_.copy(
      walkerPending = None, walkerProcedure = None)))
    val back = unparked.pawn(actor, ancientCity).unchecked
    assertEquals(use(back).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    // Riverbank's River is another source, so it is still usable.
    assertEquals(rivers(unparked.unchecked).map(_._2),
      Vector(DecisionOptionRef.Site(riverbank)))

  test("it writes where it placed the pawn"):
    val placement = placedAt(staged(Vector(riverbank, tidalMarshes),
      Phase.Wake), riverbank)
    assertEquals(NoteText.said(river, placement.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} placed at ${riverbank.value}.", covers = false)))

  test("its note names the River as its source"):
    assertEquals(river.noteSource, Some("River"))
