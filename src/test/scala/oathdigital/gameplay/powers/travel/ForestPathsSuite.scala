package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathViolation.TravelPassBlocked
import oathdigital.testkit.{ActPowerHarness, Look, Table, TestCards}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

class ForestPathsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val paths = DenizenId("43")
  private val modifiers = Vector(ForestPaths.id)
  private val beast: DenizenId = catalog.denizens.filter(d =>
    d.suit == Suit.Beast && TestCards.unrestricted(d) &&
      d.id.value != paths.value).map(d => DenizenId(d.id.value)).head
  private def held = Table.from(adviser(board(), paths)).favor(actor, 1)
    .unchecked

  test("a destination with a beast card costs no Supply and ignores the " +
      "terrain of sites"):
    val ready = denizenAt(passRuled(held), beast, mountain)
    // The printed 2 and the Mountain's 1.
    assertEquals(candidates(ready).get(mountain), Some(3))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
    val done = travel(ready, mountain, modifiers).toOption.get
    val result = after(done)
    assertEquals(Look(result).player(actor).pawnSite, Some(mountain))
    assertEquals(supplyOf(result), 7)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("it ignores Narrow Pass too: a pass the actor does not rule blocks nothing"):
    val ready = denizenAt(held, beast, plains(1))
    val blocked = travel(ready, plains(1))
    assertEquals(blocked.left.toOption.get, TravelPassBlocked(pass, plains(1)))
    assertEquals(candidates(ready).get(plains(1)), None)
    assertEquals(candidates(ready, modifiers).get(plains(1)), Some(0))
    val result = after(travel(ready, plains(1), modifiers).toOption.get)
    assertEquals(Look(result).player(actor).pawnSite, Some(plains(1)))
    assertEquals(supplyOf(result), 7)

  test("a ruined beast edifice counts as a beast card"):
    val edifice = catalog.edifices.find(_.suit == Suit.Beast).get
    val ready = edificeAt(passRuled(held), EdificeId(edifice.id.value),
      EdificeSide.Ruined, mountain)
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))

  test("without a beast card at the destination the favor is still paid, but " +
      "no Supply is saved and no site power is ignored"):
    val ready = passRuled(held)
    assertEquals(candidates(ready, modifiers), candidates(ready))
    val result = after(travel(ready, mountain, modifiers).toOption.get)
    // The printed 2 and the Mountain's 1, both still charged.
    assertEquals(supplyOf(result), 7 - 3)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))

  test("Narrow Pass still blocks a route with no beast card at the destination"):
    val ready = held
    assertEquals(travel(ready, plains(1), modifiers).left.toOption.get,
      TravelPassBlocked(pass, plains(1)))

  test("it cannot be selected without a favor to place"):
    val broke = Table.from(denizenAt(passRuled(held), beast, mountain))
      .favor(actor, 0).unchecked
    assertEquals(travel(broke, mountain, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.forest-paths is not applicable to this travel")))

  // ---- Lines ----

  private val power = ForestPaths.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a Travel to a beast card writes that site powers are ignored"):
    val ready = denizenAt(passRuled(held), beast, mountain)
    assertEquals(said(travel(ready, mountain, modifiers).toOption.get.events),
      Vector(NoteText.Said("ignoring", "Ignoring site powers.",
        covers = false)))

  test("without a beast card nothing is ignored, and nothing is written"):
    assertEquals(said(travel(passRuled(held), mountain, modifiers).toOption.get
      .events), Vector.empty)
