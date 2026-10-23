package oathdigital.model

class RuleSourceRefSuite extends munit.FunSuite:
  test("rule source identities are stable and distinguish broad source kinds"):
    val site = SiteId("site:a")
    assertEquals(RuleSourceRef.Site(site).stableKey, "site:site:a")
    assertNotEquals(
      RuleSourceRef.SiteCard(site, DenizenId("card:a")).stableKey,
      RuleSourceRef.Adviser(PlayerId("p1"), DenizenId("card:a")).stableKey)
    assert(RuleSourceRef.Foundation(FoundationNumber.I).stableKey
      .startsWith("foundation:"))
    assert(RuleSourceRef.GameRule("normal-travel").stableKey.startsWith("game:"))
