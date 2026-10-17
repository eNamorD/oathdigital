package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a queued start waits on when only waivable restrictions refuse it
  * (catalog batch 8, slice 15, ruling 7). */
class QueuedStartConsentSuite extends munit.FunSuite:
  import TravelFixture._

  private val dunes = DecisionOptionRef.Site(provincePlains)
  private val grantKey = ConsentKey(NarrowPassSite.pass, p2,
    TravelProcedure.destinationDecisionId, dunes)

  private def p2SentTo(site: SiteId): QueuedProcedure =
    QueuedProcedure(p2, ActionRef.Travel, Vector(DecisionOptionRef.Site(site)),
      Some(PowerSourceRef.Card(CatalogNames.denizen("Palanquin"))),
      QueueKind.Run,
      sender = Some(p1))

  private def p2BesideP1WithThePassHeldBy(holder: Option[PlayerId])
      : ReadyGame =
    on(board())(table => holder.fold(table)(by =>
      table.warbandsAt(pass, by, 1)).pawn(p2, cradlePlains))

  private def verdict(ready: ReadyGame, entry: QueuedProcedure)
      : QueuedStarts.Verdict =
    QueuedStart.consent(catalog, ready, entry, powers(Vector.empty),
      PhasePowerCatalog.default(catalog))

  test("an open route starts"):
    assertEquals(verdict(p2BesideP1WithThePassHeldBy(None), p2SentTo(coast)),
      QueuedStarts.Verdict.Starts)

  test("a Pass a player rules waits on that player's grant, keyed as the " +
      "traveller's own destination"):
    verdict(p2BesideP1WithThePassHeldBy(Some(p3)), p2SentTo(provincePlains))
      match
        case QueuedStarts.Verdict.Waiting(Vector(needed)) =>
          assertEquals((needed.power, needed.granter, needed.key),
            (NarrowPassSite.pass, p3, grantKey))
        case other => fail(s"expected one grant to wait on, got $other")

  test("a Pass the bandits hold is refused"):
    assert(verdict(p2BesideP1WithThePassHeldBy(None),
      p2SentTo(provincePlains)).isInstanceOf[QueuedStarts.Verdict.Refused])

  test("once granted, it starts"):
    val ready = p2BesideP1WithThePassHeldBy(Some(p3))
    val granted = ready.copy(consent = ConsentState(
      answers = Map(grantKey -> ConsentAnswer.Granted.by(p3)),
      turn = Some(ConsentTurn.of(ready))))
    assertEquals(verdict(granted, p2SentTo(provincePlains)),
      QueuedStarts.Verdict.Starts)
