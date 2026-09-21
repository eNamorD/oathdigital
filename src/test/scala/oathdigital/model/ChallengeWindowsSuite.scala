package oathdigital.model

class ChallengeWindowsSuite extends munit.FunSuite {
  test("Challenge windows carry the Challenge major action and stable keys") {
    val windows = Vector(
      PowerWindow.ChallengeBannerSelection -> "challenge.banner-selection",
      PowerWindow.ChallengeAmountSelection -> "challenge.amount-selection",
      PowerWindow.ChallengeCost -> "challenge.cost",
      PowerWindow.ChallengeRibbon -> "challenge.ribbon",
      PowerWindow.ChallengePlacement -> "challenge.placement")
    windows.foreach { case (window, key) =>
      assertEquals(window.key, key)
      assertEquals(window.associatedMajorAction, Some(MajorActionType.Challenge))
    }
  }

  test("Place Banner Resource windows are not tied to a major action") {
    val windows = Vector(
      PowerWindow.PlaceBannerResourceEligibility ->
        "place-banner-resource.eligibility",
      PowerWindow.PlaceBannerResourceBannerSelection ->
        "place-banner-resource.banner-selection",
      PowerWindow.PlaceBannerResourceAmountSelection ->
        "place-banner-resource.amount-selection",
      PowerWindow.PlaceBannerResourcePlacement ->
        "place-banner-resource.placement")
    windows.foreach { case (window, key) =>
      assertEquals(window.key, key)
      assertEquals(window.associatedMajorAction, None)
    }
  }
}
