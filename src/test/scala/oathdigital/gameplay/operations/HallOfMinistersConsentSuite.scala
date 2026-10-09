package oathdigital.gameplay.operations

import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The Hall of Ministers on a power's site-card question (consent design,
  * slice 3): Roving Terror, which discards a card at another site, asks the
  * Hall's ruler before it takes a card the Hall protects. */
class HallOfMinistersConsentSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val terror = CatalogNames.denizen("Roving Terror")
  private val source = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val errandBoyRef = DecisionOptionRef.Denizen(errandBoy)
  private val covers = ConsentKey(HallOfMinisters.id, p1,
    HallOfMinisters.discardKey, errandBoyRef)

  /** p1's Act at Ancient City beside Roving Terror. Errand Boy stands at
    * Deep Woods with the intact Hall, which `ruled` gives its ruler, and,
    * when `beside`, Wrestlers stands at Broken Peaks, which the Hall does
    * not protect. */
  private def guarded(ruled: Table => Table = _.warbandsAt(woods, p2, 1),
      beside: Boolean = true): ReadyGame =
    val table = ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home).denizen(errandBoy, at = woods)
      .edifice(hall, EdificeSide.Intact, at = woods))
    (if beside then table.denizen(wrestlers, at = peaks) else table).ready

  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers -> answer),
      turn = Some(ConsentTurn.of(ready))))

  private def errandBoyRow(t: OathTransition)(using munit.Location) =
    present(queryOf(t, p1).toVector.flatMap(_.offeredOptions)
      .find(_.id == errandBoy.value), "the Errand Boy row")

  test("a card the Hall protects for a player is askable of that player, " +
      "beside a card it does not protect"):
    val t = accepted(use(guarded(), power, source))
    assert(awaits(t, RovingTerror.decisionId),
      parked.parkedDecision(t.state).toString)
    val row = errandBoyRow(t)
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Hall of Ministers: Cannot discard cards " +
      "from its ruler's sites without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Hall of Ministers"))))

  test("the ask names its granter and covers the card"):
    val t = accepted(use(guarded(), power, source))
    assertEquals(rules.consentAsk(t.state, p1, RovingTerror.decisionId,
      errandBoyRef), Right(OptionReason.Askable(HallOfMinisters.id,
        HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
        Vector(ConsentAsk(p2, Vector(HallOfMinisters.id))), Vector(covers))))

  test("under bandits a protected card shows the Hall's reason"):
    val row = errandBoyRow(accepted(use(guarded(_.bandits(woods, 2)), power,
      source)))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Protected by the Hall of Ministers"))

  test("when the Hall protects every candidate the question is not asked: " +
      "a refusal would leave nothing to choose"):
    assert(backToActing(accepted(use(guarded(beside = false), power,
      source))))

  test("the Hall's own ruler takes the card freely"):
    val t = accepted(use(guarded(_.warbandsAt(woods, p1, 1)), power, source))
    assertEquals(errandBoyRow(t).state, "available")

  test("a grant remembered for the card lets Roving Terror take it"):
    val ready = remembering(guarded(), ConsentAnswer.Granted)
    val t = accepted(use(ready, power, source))
    assertEquals(errandBoyRow(t).state, "available")
    val done = accepted(answer(t, p1, RovingTerror.decisionId,
      pick(errandBoyRef)))
    assert(Look(after(done)).cardsAt(woods).contains(terror))
    assert(Look(after(done)).discardAfter(woods).contains(errandBoy))

  test("a refusal remembered this turn leaves the Hall's reason"):
    val ready = remembering(guarded(), ConsentAnswer.Refused)
    val row = errandBoyRow(accepted(use(ready, power, source)))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Protected by the Hall of Ministers"))
