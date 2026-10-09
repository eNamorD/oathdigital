package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks,
  Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Toll Roads' pay-or-ask step (consent design, "Toll and penalty steps",
  * rulings 7, 8 and 12). */
class TollRoadsConsentSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val rival = Table.p2
  private val power = TollRoads.forCatalog(catalog)
  private val card = PowerSourceRef.Card(TollRoadsCard.id)
  private val free = TollRoads.free(coast)
  private val key = ConsentKey(TollRoads.id, actor, TollRoads.decisionId, free)
  private val freeButton =
    DecisionOption.Button(free, "Travel without paying")

  /** Toll Roads at the Provinces' plains; `rival` rules it and the coast.
    * The actor, at the Cradle's plains with 1 favor, travels to the coast. */
  private val rivalRules: ReadyGame =
    on(board(supply = 7))(_.denizen(CatalogNames.denizen("Toll Roads"),
      at = provincePlains).favor(actor, 1)
      .warbandsAt(provincePlains, rival, 1).warbandsAt(coast, rival, 1))

  private def granted(ready: ReadyGame): ReadyGame = ready.copy(consent =
    ConsentState().granting(Vector(key), ConsentTurn.of(ready)))

  /** The option `ref` of the step a Travel to `destination` parks on. */
  private def offered(ready: ReadyGame, destination: SiteId,
      ref: DecisionOptionRef)(using munit.Location): DecisionOption =
    present(PlacementPicks.parkedOptions(accepted(travel(ready,
      destination)).state, powers(Vector.empty)).find(_.ref == ref),
      s"${ref.wireId} on the step")

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy of a player ruler is asked to pay, and may ask to go free"):
    assertEquals(offered(rivalRules, coast, PayOrAsk.pay),
      DecisionOption.Button(PayOrAsk.pay, s"Give 1 favor to ${rival.value}"))
    assertEquals(offered(rivalRules, coast, free), DecisionOption.Unavailable(
      freeButton, OptionReason.Askable(TollRoads.id,
        TollRoads.noFreeTravelConsent(card),
        Vector(ConsentAsk(rival, Vector(TollRoads.id))))))

  test("a remembered grant lets the traveller go free, with its line"):
    val ready = granted(rivalRules)
    val done = accepted(travel(ready, coast).flatMap(
      answeringStep(_, TollRoads.decisionId, free)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).favor(actor), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("waived",
      s"${actor.value} paid nothing, with ${rival.value}'s consent.",
      covers = false)))

  test("a remembered refusal leaves the free option its ordinary reason"):
    val refused = rivalRules.copy(consent = ConsentState(
      answers = Map(key -> ConsentAnswer.Refused),
      turn = Some(ConsentTurn.of(rivalRules))))
    assertEquals(offered(refused, coast, free), DecisionOption.Unavailable(
      freeButton, OptionReason.Noted(TollRoads.id,
        TollRoads.noFreeTravel(card))))

  test("a grant covers its destination only"):
    // With the Pass the actor's, the ruler's own site is reachable too.
    val bothOpen = granted(on(rivalRules)(_.warbandsAt(pass, actor, 1)))
    assert(offered(bothOpen, provincePlains,
      TollRoads.free(provincePlains)).askable)
