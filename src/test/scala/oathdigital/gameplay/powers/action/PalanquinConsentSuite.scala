package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TollRoads,
  TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Palanquin's destinations that wait on consent: Toll Roads' payer or
  * ruler, a Pass's ruler, and the user's own toll (catalog batch 8, slice
  * 15, rulings 6 to 12). */
class PalanquinConsentSuite extends munit.FunSuite:
  import TravelFixture.{board, coast, cradlePlains, on, pass, provincePlains}

  private val rules = ActPowerHarness.rulesWithPrintedRestrictions()
  private val source = DecisionOptionRef.Denizen(PalanquinCard.id)
  private val toll = CatalogNames.denizen("Toll Roads")
  private val coastRef = DecisionOptionRef.Site(coast)

  /** p1 holds Palanquin and 1 favor at the Cradle's plains, with p3 beside
    * them holding `p3Favor`. Toll Roads stands at the Provinces' plains,
    * and `tollRuler` rules it and the coast. */
  private def tolledWithP3Beside(tollRuler: PlayerId, p3Favor: Int = 1,
      steps: Table => Table = identity): ReadyGame =
    on(board())(table => steps(table.adviser(p1, PalanquinCard.id)
      .favor(p1, 1).pawn(p3, cradlePlains).favor(p3, p3Favor)
      .denizen(toll, at = provincePlains)
      .warbandsAt(provincePlains, tollRuler, 1)
      .warbandsAt(coast, tollRuler, 1)))

  private def remembering(ready: ReadyGame,
      answers: (ConsentKey, Remembered)*): ReadyGame =
    ready.copy(consent = ConsentState(answers = answers.toMap,
      turn = Some(ConsentTurn.of(ready))))

  /** p1's Palanquin aimed at p3, parked on the destination. */
  private def aimedAtP3(ready: ReadyGame): OathTransition =
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p3)))

  private def parkedDecide(state: OathState): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def offered(state: OathState, ref: DecisionOptionRef)
      : DecisionOption = parkedDecide(state).query match
    case one: DecisionQuery.ChooseOne => one.options.find(_.ref == ref)
      .getOrElse(fail(s"$ref not offered"))
    case other => fail(s"expected a choose-one, got $other")

  private def askOf(option: DecisionOption): OptionReason.Askable =
    option match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) => ask
      case other => fail(s"expected an askable option, got $other")

  /** p1 sends p3 to the coast, from the parked destination. */
  private def sentToTheCoast(ready: ReadyGame): OathTransition =
    accepted(ActPowerHarness.answer(rules, aimedAtP3(ready).state,
      Palanquin.destinationDecisionId, coastRef))

  test("a tolled destination may be asked of the target or the ruler"):
    val ask = askOf(offered(aimedAtP3(tolledWithP3Beside(p2)).state,
      coastRef))
    assertEquals(ask.asks, Vector(
      ConsentAsk(p3, Vector(TollRoads.payerId), alternative = true),
      ConsentAsk(p2, Vector(TollRoads.id), alternative = true)))
    assertEquals(ask.covers, Vector(TollRoads.payerKey(p3, coast),
      TollRoads.freeKey(p3, coast)))

  test("the target's grant sends them paying, with no toll question"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.payerKey(p3, coast) -> ConsentAnswer.Granted.by(p3))
    val after = Look(Outcomes.ready(sentToTheCoast(ready).state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 0)
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)

  test("the ruler's grant sends them free"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.freeKey(p3, coast) -> ConsentAnswer.Granted.by(p2))
    val after = Look(Outcomes.ready(sentToTheCoast(ready).state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 1)
    assertEquals(after.favor(p2), Look(ready).favor(p2))

  test("a target with no favor leaves only the ruler to ask"):
    val ask = askOf(offered(aimedAtP3(tolledWithP3Beside(p2, p3Favor = 0))
      .state, coastRef))
    assertEquals(ask.asks, Vector(ConsentAsk(p2, Vector(TollRoads.id))))

  test("both refused leave the destination unavailable with why"):
    val ready = remembering(tolledWithP3Beside(p2),
      TollRoads.payerKey(p3, coast) -> ConsentAnswer.Refused.by(p3),
      TollRoads.freeKey(p3, coast) -> ConsentAnswer.Refused.by(p2))
    offered(aimedAtP3(ready).state, coastRef) match
      case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
        assertEquals(note.key, TollRoads.sentBothRefused.name)
      case other => fail(s"expected a noted refusal, got $other")

  test("the user rules Toll Roads: a take-or-waive question follows"):
    val ready = tolledWithP3Beside(p1)
    val picked = sentToTheCoast(ready)
    assertEquals(parkedDecide(picked.state).decisionId,
      Palanquin.tollDecisionId)
    assertEquals(askOf(offered(picked.state, Palanquin.take)).asks,
      Vector(ConsentAsk(p3, Vector(TollRoads.payerId))))
    val waived = accepted(ActPowerHarness.answer(rules, picked.state,
      Palanquin.tollDecisionId, Palanquin.waive))
    val after = Look(Outcomes.ready(waived.state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 1)

  test("a destination behind a ruled Pass is asked of its ruler, keyed as " +
      "the target's own"):
    val ready = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, cradlePlains).warbandsAt(pass, p2, 1))
    val dunes = DecisionOptionRef.Site(provincePlains)
    val ask = askOf(offered(aimedAtP3(ready).state, dunes))
    assertEquals(ask.asks, Vector(ConsentAsk(p2,
      Vector(NarrowPassSite.pass))))
    assertEquals(ask.covers, Vector(ConsentKey(NarrowPassSite.pass, p3,
      TravelProcedure.destinationDecisionId, dunes)))

  test("the user's own Pass lets the target through"):
    val ready = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, cradlePlains).warbandsAt(pass, p1, 1))
    assert(offered(aimedAtP3(ready).state,
      DecisionOptionRef.Site(provincePlains)).available)

  test("a destination decision whose every site waits on consent still " +
      "parks"):
    val others = Vector(TravelFixture.coast, TravelFixture.provincePlains,
      TravelFixture.mountain, TravelFixture.pass,
      TravelFixture.hinterlandPlains, TravelFixture.island,
      TravelFixture.secondHinterlandPlains)
    val ready = tolledWithP3Beside(p2, steps = table =>
      others.foldLeft(table)((ruled, site) => ruled.warbandsAt(site, p2, 1)))
    val decide = parkedDecide(aimedAtP3(ready).state)
    assertEquals(decide.decisionId, Palanquin.destinationDecisionId)

  test("under bandits only the target is asked, to burn 1 favor"):
    val ready = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, cradlePlains).favor(p3, 1).denizen(toll, at = provincePlains)
      .bandits(provincePlains, 1).bandits(coast, 1))
    assertEquals(askOf(offered(aimedAtP3(ready).state, coastRef)).asks,
      Vector(ConsentAsk(p3, Vector(TollRoads.payerId))))
    val agreed = remembering(ready,
      TollRoads.payerKey(p3, coast) -> ConsentAnswer.Granted.by(p3))
    val after = Look(Outcomes.ready(sentToTheCoast(agreed).state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 0)

  test("a Pass ruler who also rules the toll is asked once for both, and " +
      "the target not at all"):
    val dunes = DecisionOptionRef.Site(provincePlains)
    val ready = tolledWithP3Beside(p2, steps = _.warbandsAt(pass, p2, 1))
    val ask = askOf(offered(aimedAtP3(ready).state, dunes))
    assertEquals(ask.asks, Vector(ConsentAsk(p2,
      Vector(NarrowPassSite.pass, TollRoads.id))))
    assertEquals(ask.covers, Vector(ConsentKey(NarrowPassSite.pass, p3,
      TravelProcedure.destinationDecisionId, dunes),
      TollRoads.freeKey(p3, provincePlains)))
