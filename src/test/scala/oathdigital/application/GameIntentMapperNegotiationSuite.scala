package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.{GameIntent => Intent, DecisionAnswerWire => Wire}

class GameIntentMapperNegotiationSuite extends munit.FunSuite {
  private val red = PlayerId("red")

  private def bound(answer: Wire) =
    GameIntentMapper.bind(red, Intent.ResolveWalker("negotiation.deal", answer))

  test("a proposed terms wire answer maps to the engine's terms") {
    val wire = Wire.ProposeTermsWire(oathdigital.protocol.NegotiationTerms(
      Vector(oathdigital.protocol.NegotiationTransfer("blue", 2, Vector("r1"))),
      Vector(oathdigital.protocol.NegotiationDisclosure("blue",
        oathdigital.protocol.NegotiationInformation.HeldRelic("red", "r2")))))
    assertEquals(bound(wire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.ProposeTerms(
        NegotiationTerms(Vector(NegotiationTransfer(PlayerId("blue"), 2,
          Vector(RelicId("r1")))), Vector(NegotiationDisclosure(PlayerId("blue"),
          NegotiationDisclosureRef.HeldRelic(red, RelicId("r2"))))))))))
  }

  test("accept and decline map to their engine answers") {
    assertEquals(bound(Wire.AcceptDealWire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.AcceptDeal))))
    assertEquals(bound(Wire.DeclineDealWire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.DeclineDeal))))
  }
}
