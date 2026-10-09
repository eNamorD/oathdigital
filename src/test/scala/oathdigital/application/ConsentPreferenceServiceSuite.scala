package oathdigital.application

import oathdigital.gameplay.powers.travel.{MinesSite, NarrowPassSite,
  TravelFixture}
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentPreferenceSet,
  ConsentPreferencesCleared}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Consent preferences through the service (consent design, rulings 15 to
  * 18, "Undo" and plan decision 1). */
class ConsentPreferenceServiceSuite extends munit.FunSuite:
  import ConsentFixture._
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  test("a preference is accepted during another player's parked decision " +
      "and opens no command batch"):
    val (service, repository) = ConsentFixture.service()
    val started = startTravel(service)
    val set = accepted(service.handle(gameId, started.nextSequence,
      setPreference(ConsentSetting.Always)))
    assertEquals(set.events, Vector(ConsentPreferenceSet(p2,
      NarrowPassSite.pass, p1, ConsentSetting.Always)))
    assertEquals(consent(set).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Always))
    // p1's start is still the step offered back to p1, and a reload, which
    // folds the whole journal in one pass, offers the same one.
    assertEquals(set.undo.map(step => (step.issuer, step.from, step.until)),
      Some((p1, 0L, started.nextSequence)))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.flatMap(_.undo)),
      Right(set.undo))

  test("an undo after a preference keeps it, and a reload agrees"):
    val (service, repository) = ConsentFixture.service()
    val started = startTravel(service)
    val set = accepted(service.handle(gameId, started.nextSequence,
      setPreference(ConsentSetting.Never)))
    val undone = accepted(service.handle(gameId, set.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(consent(undone).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Never))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(_.state)),
      Right(Some(undone.state)))

  test("a redo keeps a preference set after the undo"):
    val (service, _) = ConsentFixture.service()
    val started = startTravel(service)
    val undone = accepted(service.handle(gameId, started.nextSequence,
      GameCommand.Undo(p1)))
    val set = accepted(service.handle(gameId, undone.nextSequence,
      setPreference(ConsentSetting.Always)))
    val redone = accepted(service.handle(gameId, set.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(consent(redone).preference(p2, NarrowPassSite.pass, p1),
      Some(ConsentPreference.Always))

  test("case by case removes the preference"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val cleared = accepted(service.handle(gameId, set.nextSequence,
      setPreference(ConsentSetting.CaseByCase)))
    assertEquals(consent(cleared), ConsentState())

  test("only the power's ruler sets a preference, for another player at " +
      "the table, on a waivable power"):
    val (service, repository) = ConsentFixture.service()
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, granter = p3)),
      rejected(OathViolation.ConsentUnavailable(
        "p3 does not rule site.narrow-pass.pass")))
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, requester = p2)),
      rejected(OathViolation.ConsentUnavailable("p2 is not another player")))
    assertEquals(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always, requester = PlayerId("p9"))),
      rejected(OathViolation.ConsentUnavailable("p9 is not another player")))
    assertEquals(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, MinesSite.mountain, p1,
        ConsentSetting.Always)),
      rejected(OathViolation.ConsentUnavailable(
        "site.mines.mountain cannot be waived")))
    assertEquals(repository.load(gameId), Right(None))

  test("after a command, a ruler's preferences on a card they no longer " +
      "rule are cleared"):
    // The Pass holds bandits, yet the ledger still holds p2's preference:
    // the first command's append clears it.
    val stale = TravelFixture.board().copy(consent = ConsentState()
      .preferenceSet(p2, NarrowPassSite.pass, p1, ConsentSetting.Always))
    val (service, _) = ConsentFixture.service(stale)
    val started = startTravel(service)
    assertEquals(started.events.last,
      ConsentPreferencesCleared(p2, NarrowPassSite.pass))
    assertEquals(consent(started), ConsentState())
    // The clearing is not part of p1's start: the start is still offered.
    assertEquals(started.undo.map(_.until), Some(started.nextSequence - 1L))
