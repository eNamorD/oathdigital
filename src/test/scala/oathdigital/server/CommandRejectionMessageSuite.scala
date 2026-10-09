package oathdigital.server

import munit.FunSuite
import oathdigital.model.OathViolation

class CommandRejectionMessageSuite extends FunSuite:
  test("rejections surface their authoritative detail"):
    assertEquals(CommandRejectionMessage.text(
      OathViolation.NegotiationUnavailable(
        "a promised disclosure is not currently inspectable by its author")),
      "a promised disclosure is not currently inspectable by its author")
    assertEquals(CommandRejectionMessage.text(OathViolation.InsufficientFavor(3, 2)),
      "required favor 3 exceeds available 2")
    assertEquals(CommandRejectionMessage.text(
      OathViolation.InvalidModifierInvocation(
        "no facedown adviser can legally be resolved")),
      "no facedown adviser can legally be resolved")
    assertEquals(CommandRejectionMessage.text(OathViolation.FollowUpPending(
      oathdigital.model.FollowUp(oathdigital.model.PlayerId("p1"),
        oathdigital.model.ActionRef.Campaign,
        oathdigital.model.PowerSourceRef.Card(
          oathdigital.testkit.CatalogNames.denizen("Knights Errant"))))),
      "Start or skip the queued campaign first")
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentPending),
      "Waiting for an answer to a consent request")
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentUnavailable(
      "the game is over")), "the game is over")

  test("unmapped rejections fall back to a stable descriptive shape"):
    assertEquals(CommandRejectionMessage.text(OathViolation.GameNotStarted),
      "GameNotStarted")
