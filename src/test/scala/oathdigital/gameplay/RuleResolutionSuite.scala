package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.powers.{PowerFixture, ReviewedPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

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

  /** Silver Tongue's legacy `ReviewedPower` handler at `SearchModifierSelection`
    * ([[oathdigital.gameplay.powers.RestPowers]]) is still declared
    * `implemented = false`, since nobody rewrote it once Search's restriction
    * shipped as a real `ContributingPower`. Before
    * [[oathdigital.gameplay.powers.PowerImplementationStatus]] folded walker
    * and phase coverage into `ReviewedPowerCatalog`'s registry, this stale
    * flag made every Search near an accessible Silver Tongue log a
    * "rule ignored" diagnostic for a rule the engine already enforces.
    */
  test("a power covered by the walker or phase catalog no longer reports the " +
      "legacy ignored-rule diagnostic"):
    val silverTongue = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.silver-tongue")).get.id.value)
    val ready = PowerFixture.asAdviser(PowerFixture.base, silverTongue)
    val source = RuleSourceRef.Adviser(PowerFixture.actor, silverTongue)
    assertEquals(PowerRuntime.ignoredAtSource(catalog, ready, PowerFixture.actor,
      ActionKind.Search, source).toOption.get, Vector.empty)

  test("rule source stable keys round trip for durable diagnostics"):
    val values = Vector[RuleSourceRef](RuleSourceRef.Site(SiteId("site:a")),
      RuleSourceRef.SiteCard(SiteId("a"), DenizenId("denizen:d")),
      RuleSourceRef.Adviser(PlayerId("p"), VisionId("vision:v")),
      RuleSourceRef.Relic(PlayerId("p"), RelicId("relic:r")),
      RuleSourceRef.Foundation(FoundationNumber.III),
      RuleSourceRef.Legacy(LineageId("l"), LegacyId("legacy:x")))
    assertEquals(values.map(v => RuleSourceRef.parse(v.stableKey)), values.map(Some(_)))

  test("Recover registry uses exact power-ID data"):
    val registry = ReviewedPowerCatalog.registry(catalog).toOption.get
    assertEquals(registry.lookup(PowerId("edifice.e17.intact")).map(_.id),
      Some(PowerId("edifice.e17.intact")))
    assertEquals(registry.lookup(PowerId("denizen.future-recover-text")), None)
