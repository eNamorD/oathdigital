package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

/** Hospitality: after its holder travels, 1 favor from a bank matching both
  * a card at the destination and one of the holder's faceup advisers. */
class HospitalitySuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val hospitality = CatalogNames.denizen("Hospitality")
  /** Order. */
  private val messenger = CatalogNames.denizen("Messenger")
  /** Beast. */
  private val wolves = CatalogNames.denizen("Wolves")
  /** Nomad. */
  private val elders = CatalogNames.denizen("Elders")
  /** Order. */
  private val siegeEngines = CatalogNames.denizen("Siege Engines")
  /** Beast. */
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  /** Arcane. */
  private val bloodPact = CatalogNames.denizen("Blood Pact")

  /** The actor at Buried Giant holds Hospitality (Nomad), Messenger (Order)
    * and Wolves (Beast) faceup, or Hospitality facedown when
    * `hospitalityFacedown`. `atCoast` stand at the coast, and only `stocks`
    * hold favor. */
  private def holdingHospitalityAndCoastHolding(atCoast: Vector[DenizenId],
      stocks: (Suit, Int)*)(hospitalityFacedown: Boolean = false)
      : ReadyGame =
    on(board(supply = 7))(table => atCoast.foldLeft(table
      .adviser(actor, hospitality, facedown = hospitalityFacedown)
      .adviser(actor, messenger).adviser(actor, wolves)
      .banksHoldingOnly(stocks*))((placed, card) =>
        placed.denizen(card, coast)))

  private def bank(suit: Suit): DecisionOptionRef.FavorBank =
    DecisionOptionRef.FavorBank(suit)

  private def favorGains(events: Vector[OathEvent]): Vector[Gain.Favor] =
    PhasePowerHarness.ops(events).collect { case gain: Gain.Favor => gain }

  test("with two stocked matching banks it asks after the move, shows an " +
      "empty one unavailable, and the chosen bank gives 1 favor"):
    val ready = holdingHospitalityAndCoastHolding(
      Vector(elders, siegeEngines, faeMerchant),
      Suit.Nomad -> 2, Suit.Order -> 2)()
    val asked = accepted(travel(ready, coast))
    val decide = parkedDecision(asked.state)
    assertEquals(decide.decisionId, Hospitality.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Suit.all.filter(Set(Suit.Order, Suit.Beast, Suit.Nomad)).map(suit =>
        val shown = DecisionOption.FavorBank(bank(suit))
        if suit == Suit.Beast then DecisionOption.Unavailable(shown,
          OptionReason.Written(Hospitality.emptyBank))
        else shown),
      heading = Some(Hospitality.heading)))
    // The pawn arrived before the question.
    assertEquals(Look(Outcomes.ready(asked.state)).pawn(actor), coast)
    val done = accepted(answeringStep(asked, Hospitality.decisionId,
      bank(Suit.Order)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), Look(ready).favor(actor) + 1)
    assertEquals(Look(result).bankFavor(Suit.Order), 1)
    assertEquals(Look(result).bankFavor(Suit.Nomad), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("an empty bank cannot be chosen"):
    val ready = holdingHospitalityAndCoastHolding(
      Vector(elders, siegeEngines, faeMerchant),
      Suit.Nomad -> 2, Suit.Order -> 2)()
    val asked = accepted(travel(ready, coast))
    assert(answeringStep(asked, Hospitality.decisionId, bank(Suit.Beast))
      .isLeft)

  test("one stocked matching bank gives its favor without asking"):
    val ready = holdingHospitalityAndCoastHolding(Vector(elders, siegeEngines),
      Suit.Nomad -> 2)()
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(favorGains(done.events),
      Vector(Gain.Favor(actor, Suit.Nomad, 1)))
    assertEquals(Look(result).bankFavor(Suit.Nomad), 1)

  test("a bank matching only a card at the site, or only an adviser, gives " +
      "nothing"):
    // Arcane is at the coast but not among the advisers; Order is among the
    // advisers but not at the coast.
    val ready = holdingHospitalityAndCoastHolding(Vector(bloodPact),
      Suit.Arcane -> 2, Suit.Order -> 2)()
    val done = accepted(travel(ready, coast))
    assertEquals(favorGains(done.events), Vector.empty)

  test("a facedown Hospitality does nothing"):
    val ready = holdingHospitalityAndCoastHolding(Vector(elders),
      Suit.Nomad -> 2)(hospitalityFacedown = true)
    val done = accepted(travel(ready, coast))
    assertEquals(favorGains(done.events), Vector.empty)
