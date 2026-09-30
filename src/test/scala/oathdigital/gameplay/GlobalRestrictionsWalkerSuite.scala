package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The production powers carry the global restrictions into every walker
  * step, whichever node runs the operation (global operation restrictions
  * design, "Testing: Slice 1"). */
class GlobalRestrictionsWalkerSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val wildCry = CatalogNames.denizen("Wild Cry")
  private val lockedCard = CatalogNames.denizen("Sealing Ward")

  private def unchanged(ready: ReadyGame, tree: Operation,
      walked: WalkerPowers)(using munit.Location): Unit =
    ProcedureWalker.advance(ready, tree, None, walked) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")

  test("a relic discard at a Hall-protected site is skipped, as Broken " +
      "Forge's is"):
    val home = Table.homeOf(p1)
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).relicAt(drum, at = home).ready
    unchanged(ready, Sequence(Vector(BuildOps((_, _) => Right(Vector(
      Discard.Relic(drum, PositionedLocation(Location.Site(home)), 0, p2)))))),
      powers)

  test("a modifier selected for the command protects its card on the " +
      "action's first walk"):
    val ready = Table.start.adviser(p1, wildCry).ready
    val tree = Sequence(Vector(BuildOps((_, _) => Right(Vector(
      Discard.Denizen(wildCry, PositionedLocation(Location.PlayArea(p1)),
        Region.Provinces, catalog.suitOf(wildCry).get, 0, 0, p1))))))
    unchanged(ready, tree, WalkerPowers.selected(powers,
      Vector(PowerId("denizen.wild-cry"))))

  test("a faceup locked adviser is not discarded by a plain walker step"):
    val ready = Table.start.adviser(p1, lockedCard).ready
    unchanged(ready, Sequence(Vector(Discard.Denizen(lockedCard,
      PositionedLocation(Location.PlayArea(p1)), Region.Provinces,
      catalog.suitOf(lockedCard).get, 0, 0, p1))), powers)

  private val swapping = Table.start.adviser(p1, lockedCard)
    .adviser(p2, wildCry).ready
  private def swapBatch(required: Boolean): Operation = Sequence(Vector(
    BuildOps((_, _) => Right(Vector(Swap(lockedCard,
      PositionedLocation(Location.PlayArea(p1)), wildCry,
      PositionedLocation(Location.PlayArea(p2))))), required = required)))

  test("a plain BuildOps skips a refused swap"):
    unchanged(swapping, swapBatch(required = false), powers)

  test("a required BuildOps rejects the whole batch when one operation is " +
      "refused"):
    assert(ProcedureWalker.advance(swapping, swapBatch(required = true), None,
      powers).isLeft)
