package oathdigital.model

class NegotiationWindowsSuite extends munit.FunSuite {
  test("Negotiation windows have stable keys and no major action") {
    Vector(
      PowerWindow.NegotiationEligibility -> "negotiation.eligibility",
      PowerWindow.NegotiationSettlement -> "negotiation.settlement",
      PowerWindow.NegotiationOffer -> "negotiation.offer").foreach {
      case (window, key) =>
        assertEquals(window.key, key)
        assertEquals(window.associatedMajorAction, None)
    }
  }
}
