package oathdigital.gameplay.consent

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** The rules fold consent facts into the ledger and nothing else. */
class ConsentEvolutionSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val pass = PowerId("site.narrow-pass.pass")
  private val dunes = DecisionOptionRef.Site(SiteId("site:dunes"))
  private val request = ConsentRequest(p1, "travel.destination", dunes,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val table = Table.start.state

  private def folded(events: OathEvent*): ReadyGame =
    Outcomes.ready(accepted(events.foldLeft[Either[OathViolation, OathState]](
      Right(table))((state, event) => state.flatMap(rules.evolve(_, event)))))

  test("a request, then its grant, is remembered for the active turn"):
    val after = folded(ConsentRequested(request), ConsentAnswered(p2, true))
    assertEquals(after.consent.pending, None)
    assertEquals(after.consent.remembered(
      ConsentKey(pass, p1, "travel.destination", dunes),
      ConsentTurn.of(after)), Some(ConsentAnswer.Granted))
    assertEquals(after.game, Outcomes.ready(table).game)

  test("a withdrawal, a preference and a clearing change only the ledger"):
    val after = folded(ConsentRequested(request), ConsentRequestWithdrawn(p1),
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always))
    assertEquals(after.consent.pending, None)
    assertEquals(after.consent.preference(p2, pass, p1),
      Some(ConsentPreference.Always))
    assertEquals(folded(ConsentPreferenceSet(p2, pass, p1,
      ConsentSetting.Always), ConsentPreferencesCleared(p2, pass)).consent,
      ConsentState())

  test("an answer with no request waiting is refused"):
    assert(rules.evolve(table, ConsentAnswered(p2, true)).isLeft)

  test("a request that covers other keys settles those, not its option's"):
    val covered = ConsentKey(PowerId("edifice.e16.intact"), p1,
      "operation.discard", DecisionOptionRef.Denizen(
        CatalogNames.denizen("Errand Boy")))
    val after = folded(ConsentRequested(request.copy(covers = Vector(covered),
        asks = Vector(ConsentAsk(p2, Vector(covered.power))))),
      ConsentAnswered(p2, true))
    val now = ConsentTurn.of(after)
    assertEquals(after.consent.remembered(covered, now),
      Some(ConsentAnswer.Granted))
    assertEquals(after.consent.remembered(
      ConsentKey(pass, p1, "travel.destination", dunes), now), None)
