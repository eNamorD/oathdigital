package oathdigital.application

import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What the parked destination decision shows each site as: its cost under
  * the selected modifiers, or why it cannot be reached (travel destination
  * design, rulings 3 and 4). */
class TravelDestinationRowsSuite extends munit.FunSuite:
  import TravelFixture._
  import oathdigital.gameplay.powers.PowerFixture.actor

  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))

  /** The destination rows `actor` is shown once a Travel with `modifiers`
    * starts at `ready`. */
  private def rows(ready: ReadyGame, modifiers: Vector[PowerId])
      (using munit.Location): Map[String, DecisionOptionProjection] =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, modifiers))
    present(projector.project(ScopedProjectionContext(
      Outcomes.ready(started.state), Some(actor))), "the destination decision")
      .query.toVector.flatMap(_.offeredOptions).map(row => row.id -> row).toMap

  private def row(ready: ReadyGame, site: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)(using munit.Location)
      : DecisionOptionProjection =
    present(rows(ready, modifiers).get(site.value), site.value)

  /** The pass ruled by the actor, so no route here is blocked by it. */
  private def open(supply: Int): ReadyGame =
    Table.from(board(supply = supply)).warbandsAt(pass, actor, 1).unchecked

  test("a destination is badged with the Supply its Travel spends"):
    val ready = open(supply = 7)
    assertEquals(row(ready, coast).badge, Some("1 Supply"))
    assertEquals(row(ready, provincePlains).badge, Some("2 Supply"))
    assertEquals(row(ready, island).badge, Some("6 Supply"))

  test("a toll adds its favor to the badge"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 1).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    assertEquals(row(ready, coast).badge, Some("1 Supply · 1 favor"))

  test("a selected modifier that makes the Travel free reads 0 Supply, and " +
      "its own card payment is not in the badge"):
    val ready = Table.from(open(supply = 7))
      .adviser(actor, CatalogNames.denizen("Tents")).favor(actor, 1).unchecked
    assertEquals(row(ready, coast, Vector(Tents.id)).badge, Some("0 Supply"))

  test("a destination the actor cannot afford says how much it needs"):
    val shown = row(open(supply = 1), provincePlains)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some("Needs 2 Supply; you have 1"))
    assertEquals(row(open(supply = 1), coast).state, "available")

  test("a toll the actor cannot pay says how much favor it needs"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 0).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    val shown = row(ready, coast)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some("Needs 1 favor; you have 0"))

  test("Way Station's site offers the favor beside the Supply"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Way Station"), at = coast)
      .warbandsAt(coast, Table.p2, 1).favor(actor, 1).unchecked
    assertEquals(row(ready, coast).badge, Some("1 Supply or 1 favor"))
    val broke = Table.from(ready).supply(actor, 0).unchecked
    assertEquals(row(broke, coast).badge, Some("1 favor"))
