package oathdigital.server

import munit.FunSuite
import oathdigital.model.OathViolation

class CommandRejectionMessageSuite extends FunSuite {
  test("negotiation rejections surface their authoritative detail") {
    assertEquals(CommandRejectionMessage.text(
      OathViolation.NegotiationUnavailable(
        "a promised disclosure is not currently inspectable by its author")),
      "a promised disclosure is not currently inspectable by its author")
    assertEquals(CommandRejectionMessage.text(OathViolation.InsufficientFavor(3, 2)),
      "required favor 3 exceeds available 2")
    assertEquals(CommandRejectionMessage.text(
      OathViolation.InvalidModifierInvocation("no facedown adviser can legally be resolved")),
      "no facedown adviser can legally be resolved")
  }

  test("unmapped rejections fall back to a stable descriptive shape") {
    assertEquals(CommandRejectionMessage.text(OathViolation.GameNotStarted),
      "GameNotStarted")
  }
}
