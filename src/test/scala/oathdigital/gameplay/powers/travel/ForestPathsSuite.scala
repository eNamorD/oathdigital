package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathViolation.TravelPassBlocked
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.{accepted, rejected}

class ForestPathsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val paths = CatalogNames.denizen("Forest Paths")
  private val modifiers = Vector(ForestPaths.id)
  // A Beast denizen and a Beast edifice whose powers these tests do not use.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val beastEdifice = CatalogNames.edifice("Hallowed Spring")
  private def held = Table.from(board(supply = 7)).adviser(actor, paths)
    .favor(actor, 1).unchecked

  test("a destination with a beast card costs no Supply and ignores the " +
      "terrain of sites"):
    val ready = Table.from(held).warbandsAt(pass, actor, 1)
      .denizen(beast, at = mountain).unchecked
    // The printed 2 and the Mountain's 1.
    assertEquals(candidates(ready).get(mountain), Some(3))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
    val done = accepted(travel(ready, mountain, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(mountain))
    // The board's 7 Supply, untouched: a beast card costs no Supply.
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(paths), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("it ignores Narrow Pass too: a pass the actor does not rule blocks nothing"):
    val ready = Table.from(held).denizen(beast, at = provincePlains).unchecked
    val blocked = travel(ready, provincePlains)
    assertEquals(rejected(blocked), TravelPassBlocked(pass, provincePlains))
    assertEquals(candidates(ready).get(provincePlains), None)
    // A beast card costs no Supply.
    assertEquals(candidates(ready, modifiers).get(provincePlains), Some(0))
    val result = Outcomes.ready(
      accepted(travel(ready, provincePlains, modifiers)).state)
    assertEquals(Look(result).player(actor).pawnSite, Some(provincePlains))
    // 7 Supply, nothing paid.
    assertEquals(Look(result).supply(actor), 7)

  test("a ruined beast edifice counts as a beast card"):
    val ready = Table.from(held).warbandsAt(pass, actor, 1)
      .edifice(beastEdifice, EdificeSide.Ruined, at = mountain).unchecked
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))

  test("without a beast card at the destination the favor is still paid, but " +
      "no Supply is saved and no site power is ignored"):
    val ready = Table.from(held).warbandsAt(pass, actor, 1).unchecked
    assertEquals(candidates(ready, modifiers), candidates(ready))
    val result = Outcomes.ready(accepted(travel(ready, mountain, modifiers))
      .state)
    // The printed 2 and the Mountain's 1, both still charged.
    assertEquals(Look(result).supply(actor), 7 - 3)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(paths), Tokens(1, 0))

  test("Narrow Pass still blocks a route with no beast card at the destination"):
    val ready = held
    assertEquals(rejected(travel(ready, provincePlains, modifiers)),
      TravelPassBlocked(pass, provincePlains))

  test("it cannot be selected without a favor to place"):
    val broke = Table.from(held).warbandsAt(pass, actor, 1)
      .denizen(beast, at = mountain).favor(actor, 0).unchecked
    assertEquals(travel(broke, mountain, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.forest-paths is not applicable to this travel")))

  // ---- Lines ----

  private val power = ForestPaths.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a Travel to a beast card writes that site powers are ignored"):
    val ready = Table.from(held).warbandsAt(pass, actor, 1)
      .denizen(beast, at = mountain).unchecked
    assertEquals(said(accepted(travel(ready, mountain, modifiers)).events),
      Vector(NoteText.Said("ignoring", "Ignoring site powers.",
        covers = false)))

  test("without a beast card nothing is ignored, and nothing is written"):
    val ready = Table.from(held).warbandsAt(pass, actor, 1).unchecked
    assertEquals(said(accepted(travel(ready, mountain, modifiers)).events),
      Vector.empty)
