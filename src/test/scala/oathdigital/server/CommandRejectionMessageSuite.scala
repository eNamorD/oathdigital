package oathdigital.server

import munit.FunSuite
import oathdigital.model.{ActionRef, OathViolation, PlayerId,
  PowerSourceRef, QueueKind, QueuedProcedure}
import oathdigital.testkit.CatalogNames

class CommandRejectionMessageSuite extends FunSuite:
  test("rejections surface their authoritative detail"):
    assertEquals(CommandRejectionMessage.text(
      OathViolation.NegotiationUnavailable(
        "a promised disclosure is not currently inspectable by its author")),
      "a promised disclosure is not currently inspectable by its author")
    assertEquals(CommandRejectionMessage.text(OathViolation.InsufficientFavor(3, 2)),
      "required favor 3 exceeds available 2")
    assertEquals(CommandRejectionMessage.text(OathViolation.FollowUpPending(
      QueuedProcedure(PlayerId("p1"), ActionRef.Campaign, Vector.empty,
        Some(PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))),
        QueueKind.Offer))),
      "Start or skip the queued campaign first")
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentPending),
      "Waiting for an answer to a consent request")
    assertEquals(CommandRejectionMessage.text(OathViolation.ConsentUnavailable(
      "the game is over")), "the game is over")

  test("unmapped rejections fall back to a stable descriptive shape"):
    assertEquals(CommandRejectionMessage.text(OathViolation.GameNotStarted),
      "GameNotStarted")
