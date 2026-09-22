package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.powers.{PowerFixture, ReviewedPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

class RuleResolutionSuite extends munit.FunSuite {
  private object AllowHandler extends TypedRuleHandler {
    def resolve(a: RuleActivation, c: RuleQueryContext): RuleOutcome =
      RuleOutcome.Allow
  }

  test("rule source identities are stable and distinguish broad source kinds") {
    val site = SiteId("site:a")
    assertEquals(RuleSourceRef.Site(site).stableKey, "site:site:a")
    assertNotEquals(
      RuleSourceRef.SiteCard(site, DenizenId("card:a")).stableKey,
      RuleSourceRef.Adviser(PlayerId("p1"), DenizenId("card:a")).stableKey)
    assert(RuleSourceRef.Foundation(FoundationNumber.I).stableKey
      .startsWith("foundation:"))
    assert(RuleSourceRef.GameRule("normal-travel").stableKey.startsWith("game:"))
  }

  test("registry lookup is explicit and unknown relevant handlers are safe") {
    val registry = RuleRegistry("known" -> AllowHandler)
    assert(registry.lookup("known").nonEmpty)
    assertEquals(registry.lookup("unknown"), None)
    val activation = RuleActivation(RuleSourceRef.GameRule("test"), "unknown", 0)
    val resolved = registry.resolve(Vector(activation), null)
    assertEquals(resolved.head.outcome,
      RuleOutcome.UnsupportedRelevantRule("unknown"))
  }

  test("resolution ordering is priority source identity then handler ID") {
    val registry = RuleRegistry("a" -> AllowHandler, "b" -> AllowHandler)
    val resolved = registry.resolve(Vector(
      RuleActivation(RuleSourceRef.Site(SiteId("z")), "b", 20),
      RuleActivation(RuleSourceRef.Site(SiteId("z")), "a", 20),
      RuleActivation(RuleSourceRef.Site(SiteId("a")), "b", 20),
      RuleActivation(RuleSourceRef.GameRule("first"), "a", 10)
    ), null)
    assertEquals(resolved.map(value => (value.activation.priority,
      value.activation.source.stableKey, value.activation.handlerId)), Vector(
      (10, "game:first", "a"),
      (20, "site:a", "b"),
      (20, "site:z", "a"),
      (20, "site:z", "b")
    ))
  }

  test("reviewed handlers use precise windows resolution and implementations") {
    val byId = ReviewedPowerCatalog.powers.map(value => value.id -> value).toMap
    assert(byId(PowerId("denizen.map-library")).handlers.map(_.window).contains(
      PowerWindow.TradeModifierSelection))
    assertEquals(byId(PowerId("denizen.map-library")).handlers.head.resolution,
      PowerResolution.PlayerSelected)
    assertEquals(byId(PowerId("denizen.relic-worship")).handlers.map(_.window),
      Vector(PowerWindow.RecoverBeforeFirstRoll))
    assertEquals(byId(PowerId("denizen.insomnia")).handlers.map(_.window),
      Vector(PowerWindow.RestStart))
    assert(byId(PowerId("site.fair-isle.island")).handlers.forall(_.implemented))
    assertEquals(byId(PowerId("denizen.outriders")).handlers.map(_.window),
      Vector(PowerWindow.CampaignAttackerBattlePlans))
    assert(!byId(PowerId("denizen.insomnia")).handlers.map(_.window).contains(
      PowerWindow.TravelModifierSelection))
    assert(ReviewedPowerCatalog.resolver(catalog).toOption.get.validateSources(
      Vector(RuleSourceRef.GameRule("test") ->
        Vector(PowerId("denizen.not-a-rule")))).isLeft)
  }

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
      "legacy ignored-rule diagnostic") {
    val silverTongue = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.silver-tongue")).get.id.value)
    val ready = PowerFixture.asAdviser(PowerFixture.base, silverTongue)
    val source = RuleSourceRef.Adviser(PowerFixture.actor, silverTongue)
    assertEquals(PowerRuntime.ignoredAtSource(catalog, ready, PowerFixture.actor,
      ActionKind.Search, source).toOption.get, Vector.empty)
  }

  test("rule source stable keys round trip for durable diagnostics") {
    val values = Vector[RuleSourceRef](RuleSourceRef.Site(SiteId("site:a")),
      RuleSourceRef.SiteCard(SiteId("a"), DenizenId("denizen:d")),
      RuleSourceRef.Adviser(PlayerId("p"), VisionId("vision:v")),
      RuleSourceRef.Relic(PlayerId("p"), RelicId("relic:r")),
      RuleSourceRef.Foundation(FoundationNumber.III),
      RuleSourceRef.Legacy(LineageId("l"), LegacyId("legacy:x")))
    assertEquals(values.map(v => RuleSourceRef.parse(v.stableKey)), values.map(Some(_)))
  }
}
