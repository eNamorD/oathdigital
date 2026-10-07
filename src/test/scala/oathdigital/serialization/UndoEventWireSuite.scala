package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, StepTakenBack}

/** Undo's journal facts round-trip through the envelope (undo design,
  * "Storage"). */
class UndoEventWireSuite extends munit.FunSuite:
  private def roundTrip(event: OathEvent): Either[Any, OathEvent] =
    GameEventWire.encodeEvent("g", 7L, event).flatMap(encoded =>
      GameEventWire.decode(encoded)).map(_.event)

  test("a command batch's opening fact round-trips with its issuer"):
    assertEquals(roundTrip(CommandAccepted(PlayerId("p2"))),
      Right(CommandAccepted(PlayerId("p2"))))

  test("a taken-back marker round-trips with its range and issuer"):
    assertEquals(roundTrip(StepTakenBack(12L, 19L, PlayerId("p1"))),
      Right(StepTakenBack(12L, 19L, PlayerId("p1"))))

  test("each fact has its own event type"):
    assertEquals(
      GameEventWire.encodeEvent("g", 0L, CommandAccepted(PlayerId("p1")))
        .map(_("eventType").str),
      Right("undo.command-accepted"))
    assertEquals(
      GameEventWire.encodeEvent("g", 0L, StepTakenBack(0L, 1L, PlayerId("p1")))
        .map(_("eventType").str),
      Right("undo.step-taken-back"))
