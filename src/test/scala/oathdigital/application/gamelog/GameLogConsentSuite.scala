package oathdigital.application.gamelog

import oathdigital.application.{ConsentFixture, GameAccepted,
  GameApplicationService, GameCommand}
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.{formatter, texts}

/** What the Game Log says about consent, and to whom (consent design,
  * "Game Log"). */
class GameLogConsentSuite extends munit.FunSuite:
  import ConsentFixture._
  private val asked = "Asked p2 for consent to ignore Narrow Pass and " +
    "select Dunes."

  private def log(service: GameApplicationService, viewer: PlayerId)
      : Vector[LogEntry] =
    formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(viewer))

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  test("the request and a grant read the same to every player"):
    val (service, _) = ConsentFixture.service()
    answering(service, asking(service), ConsentChoice.Yes)
    Vector(p1, p2, p3).foreach { viewer =>
      val lines = texts(log(service, viewer))
      assert(lines.contains(asked), lines)
      assert(lines.contains("p2 gave consent."), lines)
    }

  test("a refusal names the ruler"):
    val (service, _) = ConsentFixture.service()
    answering(service, asking(service), ConsentChoice.Never)
    assert(texts(log(service, p1)).contains("p2 gave no consent."))

  test("a preference line is written for its granter alone"):
    val (service, _) = ConsentFixture.service()
    val always = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val never = accepted(service.handle(gameId, always.nextSequence,
      setPreference(ConsentSetting.Never)))
    accepted(service.handle(gameId, never.nextSequence,
      setPreference(ConsentSetting.CaseByCase)))
    val ruler = texts(log(service, p2))
    Vector("p2 now always allows p1 to ignore Narrow Pass.",
      "p2 now never allows p1 to ignore Narrow Pass.",
      "p2 now decides case by case whether p1 may ignore Narrow Pass.")
      .foreach(line => assert(ruler.contains(line), ruler))
    Vector(p1, p3).foreach(viewer =>
      assert(!texts(log(service, viewer)).exists(_.contains("Narrow Pass")),
        texts(log(service, viewer))))

  test("a reset line is written for its granter alone"):
    val stale = TravelFixture.board().copy(consent = ConsentState()
      .preferenceSet(p2, NarrowPassSite.pass, p1, ConsentSetting.Always))
    val (service, _) = ConsentFixture.service(stale)
    startTravel(service)
    val reset = "p2's consent preferences for Narrow Pass were reset."
    assert(texts(log(service, p2)).contains(reset))
    assert(!texts(log(service, p1)).contains(reset))

  test("a withdrawn request's line is struck through, under no header"):
    val (service, _) = ConsentFixture.service()
    val waiting = asking(service)
    accepted(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p1)))
    val entry = present(log(service, p1).find(LogScripts.text(_) == asked),
      "the request line")
    assertEquals(entry.takenBack,
      Some(LogTakenBack(entry.sequence, Vector.empty)))

  test("the requester's log cannot tell an automatic grant from a manual " +
      "one"):
    val (manual, _) = ConsentFixture.service()
    answering(manual, asking(manual), ConsentChoice.Yes)
    val (automatic, _) = ConsentFixture.service()
    val set = accepted(automatic.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(automatic, set.nextSequence)
    accepted(automatic.handle(gameId, started.nextSequence, requestDunes))
    assertEquals(texts(log(automatic, p1)), texts(log(manual, p1)))
