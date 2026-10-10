package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class SpecialEnvoySuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val envoy = CatalogNames.denizen("Special Envoy")
  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val power = SpecialEnvoy.forCatalog(catalog)
  private val modifiers = Vector(SpecialEnvoy.id)

  /** The actor holds Special Envoy faceup and 7 Supply, at Buried Giant, the
    * Cradle's plains. */
  private def holdingEnvoy: ReadyGame =
    on(board(supply = 7))(_.adviser(actor, envoy))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("selected, Travel spends no Supply and then the Act Phase ends"):
    val ready = holdingEnvoy
    assertEquals(candidates(ready, modifiers).get(coast), Some(0))
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).supply(actor), 7)
    // The Act Phase ended, and with no REST power usable the driver
    // finished Rest, so the turn has passed.
    assertEquals(Look(ActPowerHarness.replayed(rules, ready,
      ActPowerHarness.untilWalkEnds(done.events))).phase, Phase.Rest)
    assertNotEquals(Look(result).active, actor)
    assertEquals(said(done.events), Vector(NoteText.Said("ended",
      "The Act Phase ended.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("Dragonskin Drum's gain comes before the Act Phase ends"):
    val ready = withRelic(holdingEnvoy, drum)
    val done = accepted(travel(ready, coast, modifiers :+ DragonskinDrum.id))
    val ops = PhasePowerHarness.ops(done.events)
    val gained = ops.indexWhere {
      case Gain.Warbands(player, _, _) => player == actor
      case _ => false
    }
    val ended = ops.indexOf(EnterPhase(Phase.Rest))
    assert(gained >= 0 && ended > gained, ops.toString)
    assertEquals(Look(Outcomes.ready(done.state)).boardWarbands(actor),
      Look(ready).boardWarbands(actor) + 1)

  test("it is a Travel modifier, and an unselected Travel keeps the Act " +
      "Phase"):
    val offered = (action: ActionRef) => accepted(
      rules.offerableWalkerPowers(holdingEnvoy, actor, action)).map(_.id)
    assert(offered(ActionRef.Travel).contains(SpecialEnvoy.id))
    assert(!offered(ActionRef.Search).contains(SpecialEnvoy.id))
    val result = Outcomes.ready(accepted(travel(holdingEnvoy, coast)).state)
    assertEquals(Look(result).phase, Phase.Act)
    // The printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
