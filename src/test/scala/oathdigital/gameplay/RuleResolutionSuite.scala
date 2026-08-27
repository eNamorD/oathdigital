package oathdigital.gameplay

import oathdigital.model._

class RuleResolutionSuite extends munit.FunSuite {
  test("Campaign timing windows preserve the printed procedure order") {
    assertEquals(CampaignTimingWindow.ordered.map(_.order), (0 to 9).toVector)
    assertEquals(CampaignTimingWindow.ordered, Vector(
      CampaignTimingWindow.TargetAndForceFormation,
      CampaignTimingWindow.AttackerBattlePlans,
      CampaignTimingWindow.AttackRollAndSkullLosses,
      CampaignTimingWindow.AttackerSacrifice,
      CampaignTimingWindow.DefenderBattlePlansAndRoll,
      CampaignTimingWindow.Outcome,
      CampaignTimingWindow.ConquestPlacement,
      CampaignTimingWindow.RaidResolution,
      CampaignTimingWindow.RaidPawnRelocation,
      CampaignTimingWindow.RemainingEndVictoryDefeatEffects))
  }
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
    assertEquals(RuntimeRuleRegistry.default.lookup(
      "site.fair-isle.island").flatMap(_.travelModifierKind),
      Some(TravelModifierKind.Island))
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

  test("reviewed handlers classify optional mandatory triggered battle-plan and inherent behavior") {
    assertEquals(MajorActionPowerShell.classify("denizen.map-library",
      MajorActionKind.Trade).behavior, RuleBehavior.OptionalModifier)
    assertEquals(MajorActionPowerShell.classify("denizen.relic-worship",
      MajorActionKind.Recover).behavior, RuleBehavior.Mandatory)
    assertEquals(MajorActionPowerShell.classify("denizen.insomnia",
      MajorActionKind.Rest).behavior, RuleBehavior.Triggered)
    assertEquals(MajorActionPowerShell.classify("site.fair-isle.island",
      MajorActionKind.Travel), RuleClassification(MajorActionKind.Travel,
      RuleTiming.Inherent, RuleBehavior.Inherent, implemented = true))
    assertEquals(MajorActionPowerShell.classify("denizen.outriders",
      MajorActionKind.Campaign).timing, RuleTiming.BattlePlan)
    assertEquals(MajorActionPowerShell.classify("denizen.insomnia",
      MajorActionKind.Travel).behavior, RuleBehavior.Irrelevant)
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
