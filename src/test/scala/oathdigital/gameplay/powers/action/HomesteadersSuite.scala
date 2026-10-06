package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class HomesteadersSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val source = DecisionOptionRef.Denizen(homesteaders)
  private val power = Homesteaders.forCatalog(catalog)
  /** p1's pawn site on the quiet table, with room for three cards. */
  private val home = CatalogNames.site("Ancient City")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** Adviser-only. */
  private val pilgrimage = CatalogNames.denizen("Pilgrimage")
  /** Locked and adviser-only. */
  private val marriage = CatalogNames.denizen("Marriage")

  /** p1's Act at Ancient City, holding Homesteaders faceup. */
  private def holdingHomesteaders: Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, homesteaders)

  /** The walk Homesteaders started and the walk after `card` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition)

  private def choose(ready: ReadyGame, card: DenizenId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, Homesteaders.decisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  test("it offers the player's faceup advisers, itself included, and not a " +
      "facedown one"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .adviser(p1, errandBoy, facedown = true).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Homesteaders.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> homesteaders.value,
      "denizen" -> landWarden.value)))

  test("adviser-only and locked advisers are not offered"):
    val ready = holdingHomesteaders.adviser(p1, pilgrimage)
      .adviser(p1, marriage).ready
    val t = accepted(use(ready, power, source))
    assertEquals(offered(t, p1),
      Some(Vector("denizen" -> homesteaders.value)))

  test("the chosen adviser moves to the pawn's site with its tokens, and the " +
      "line names it"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .tokens(landWarden, favor = 1).ready
    val Chosen(t, done) = choose(ready, landWarden)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(home), Vector[CardId](landWarden))
    assertEquals(end.tokensOn(landWarden), Tokens(1, 0))
    assertEquals(end.advisers(p1), Vector[CardId](homesteaders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Moved ${landWarden.value} to ${home.value}.",
      covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("it can move itself"):
    val Chosen(_, done) = choose(holdingHomesteaders.ready, homesteaders)
    assertEquals(Look(after(done)).cardsAt(home), Vector[CardId](homesteaders))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("with the pawn's site full it asks nothing and the line says so"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .denizen(errandBoy, at = home).denizen("Wrestlers", at = home)
      .denizen("Elders", at = home).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p1),
      Vector[CardId](homesteaders, landWarden))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No adviser could be moved.", covers = false)))
