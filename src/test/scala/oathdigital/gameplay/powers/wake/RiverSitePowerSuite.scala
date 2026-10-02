package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._

class RiverSitePowerSuite extends munit.FunSuite:
  import PowerFixture._
  import RiverFixture._
  import TargetsFixture.{after, answer, awaits, offered, pick, replayed,
    usableNow, withPawn}

  private val source = DecisionOptionRef.Site(ancientCity)
  private def use(ready: ReadyGame) = TargetsFixture.use(ready, river, source)
  private def rivers(ready: ReadyGame) = usableNow(ready).collect {
    case usable if usable.power.isInstanceOf[RiverSitePower] =>
      usable.power.id -> usable.ref }

  /** Starts Ancient City's River and answers the site question. */
  private def placedAt(ready: ReadyGame, at: SiteId) =
    val parked = use(ready).toOption.get
    assert(awaits(parked, RiverSitePower.siteDecisionId))
    (parked, answer(parked, actor, RiverSitePower.siteDecisionId,
      pick(DecisionOptionRef.Site(at))).toOption.get)

  test("every River is a registered phase power"):
    assertEquals(PhasePowerCatalog.default(catalog).powers.collect {
      case power: RiverSitePower => power.id.value }.toSet,
      Set("site.ancient-city.river", "site.headwaters.river",
        "site.riverbank.river", "site.tidal-marshes.river"))

  test("it is usable in the Wake at a River while another River is in play"):
    assertEquals(rivers(staged()), Vector(river.id -> source))

  test("it is not usable when no other River is in play"):
    val alone = staged(Vector.empty)
    assertEquals(rivers(alone), Vector.empty)
    assert(use(alone).isLeft)

  test("it is not usable from a site that is not a River"):
    val ready = staged()
    val dry = ready.game.current.map.inPlay.find(site =>
      !RiverSitePower.isRiver(site)).get
    val away = withPawn(ready, actor, dry)
    assertEquals(rivers(away), Vector.empty)
    assert(use(away).isLeft)

  test("it offers every other River in play, in map order"):
    val ready = staged()
    val expected = ready.game.current.map.inPlay
      .filter(Set(riverbank, tidalMarshes).contains)
    assertEquals(expected.size, 2)
    val parked = use(ready).toOption.get
    assertEquals(offered(parked, actor), Some(expected.map(site =>
      "site" -> site.value)))

  test("it places the pawn at the chosen River without Travel or Supply"):
    val ready = staged()
    val (parked, done) = placedAt(ready, riverbank)
    val placed = after(done)
    assertEquals(player(placed).pawnSite, Some(riverbank))
    assertEquals(player(placed).board.supply, player(ready).board.supply)
    val moves = (parked.events ++ done.events).collect {
      case step: WalkerStepRecorded => step.ops }.flatten.collect {
      case move @ Move(Piece.Pawn(_), _, _, _) => move }
    assertEquals(moves, Vector(Move(Piece.Pawn(actor),
      PositionedLocation(Location.Site(ancientCity)),
      PositionedLocation(Location.Site(riverbank)))))
    assertEquals(replayed(ready, parked.events ++ done.events),
      Right(done.state))

  test("it is once per turn from each River"):
    val (_, done) = placedAt(staged(), riverbank)
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(ancientCity),
      river.id)
    assert(after(done).game.current.turn.usedPowers.contains(used))
    val back = withPawn(after(done), actor, ancientCity)
    assertEquals(use(back).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    // Riverbank's River is another source, so it is still usable.
    assertEquals(rivers(after(done)).map(_._2),
      Vector(DecisionOptionRef.Site(riverbank)))

  test("it writes where it placed the pawn"):
    val (parked, done) = placedAt(staged(), riverbank)
    assertEquals(NoteText.said(river, parked.events ++ done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} placed at ${riverbank.value}.",
        covers = false)))
    assertEquals(river.noteSource, Some("River"))
