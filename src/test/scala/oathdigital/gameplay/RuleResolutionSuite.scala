package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.Unwrap.accepted

class RuleResolutionSuite extends munit.FunSuite:
  test("rule source identities are stable and distinguish broad source kinds"):
    val site = SiteId("site:a")
    assertEquals(RuleSourceRef.Site(site).stableKey, "site:site:a")
    assertNotEquals(
      RuleSourceRef.SiteCard(site, DenizenId("card:a")).stableKey,
      RuleSourceRef.Adviser(PlayerId("p1"), DenizenId("card:a")).stableKey)
    assert(RuleSourceRef.Foundation(FoundationNumber.I).stableKey
      .startsWith("foundation:"))
    assert(RuleSourceRef.GameRule("normal-travel").stableKey.startsWith("game:"))

  test("rule source stable keys round trip for durable diagnostics"):
    val values = Vector[RuleSourceRef](RuleSourceRef.Site(SiteId("site:a")),
      RuleSourceRef.SiteCard(SiteId("a"), DenizenId("denizen:d")),
      RuleSourceRef.Adviser(PlayerId("p"), VisionId("vision:v")),
      RuleSourceRef.Relic(PlayerId("p"), RelicId("relic:r")),
      RuleSourceRef.Foundation(FoundationNumber.III),
      RuleSourceRef.Legacy(LineageId("l"), LegacyId("legacy:x")))
    assertEquals(values.map(v => RuleSourceRef.parse(v.stableKey)), values.map(Some(_)))

  test("Recover registry uses exact power-ID data"):
    val registry = accepted(ReviewedPowerCatalog.registry(catalog))
    assertEquals(registry.lookup(PowerId("edifice.e17.intact")).map(_.id),
      Some(PowerId("edifice.e17.intact")))
    assertEquals(registry.lookup(PowerId("denizen.future-recover-text")), None)
