package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.MajorActionType._
import oathdigital.gameplay.powerresolver.PowerResolution._
import oathdigital.gameplay.powerresolver.PowerWindow._
import oathdigital.model.{PlayerId, PowerId, RuleSourceRef, SiteId}

private object PowerResolverSuiteFixtures {
  final case class RecoverFacts(actor: PlayerId, emptySlot: Boolean)
      extends PowerFacts
}

class PowerResolverSuite extends munit.FunSuite {
  import PowerResolverSuiteFixtures._

  private val sourceA = RuleSourceRef.Site(SiteId("a"))
  private val sourceZ = RuleSourceRef.Site(SiteId("z"))

  private final case class TestPower(id: PowerId,
      modifier: Option[MajorActionType], handlers: Vector[PowerHandler])
      extends Power

  private val always: PowerContext => PowerInspection = _ =>
    PowerInspection(applicable = true)
  private val never: PowerContext => PowerInspection = _ =>
    PowerInspection(applicable = false)
  private val recover: PowerContext => PowerInspection = context =>
    context.facts match {
      case facts: RecoverFacts => PowerInspection(facts.emptySlot,
        eligiblePlayer = Some(facts.actor), decisionPlayer = Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }

  private def power(id: String, windows: Vector[PowerWindow],
      inspect: PowerContext => PowerInspection = always,
      implemented: Boolean = true,
      resolution: PowerResolution = PlayerSelected,
      modifier: Option[MajorActionType] = None): Power = {
    val inspector = PowerInspector(inspect)
    TestPower(PowerId(id), modifier, windows.map(window => resolution match {
      case Automatic => PowerHandlers.automatic(window, implemented)(inspector)
      case PlayerSelected => PowerHandlers.selected(window, implemented)(inspector)
    }))
  }

  test("major action vocabulary contains only selectable major actions") {
    assertEquals(MajorActionType.values.map(_.key), Vector("search", "travel",
      "campaign", "muster", "trade", "forge", "recover", "challenge"))
  }

  test("PowerId enforces the catalog stable identity vocabulary exactly") {
    assertEquals(PowerId("denizen.catacombs").value, "denizen.catacombs")
    Vector("", "catacombs", " denizen.catacombs", "denizen.catacombs ",
      "Denizen.catacombs", "denizen.catacombs_clause", "denizen..catacombs")
      .foreach(value => intercept[IllegalArgumentException](PowerId(value)))
  }

  test("windows expose typed major-action associations independent of keys") {
    assertEquals(SearchModifierSelection.associatedMajorAction, Some(Search))
    assertEquals(RecoverEligibility.associatedMajorAction, Some(Recover))
    assertEquals(ChallengeActionEligibility.associatedMajorAction, Some(Challenge))
    assertEquals(RestStart.associatedMajorAction, None)
    assertEquals(NegotiationOffer.associatedMajorAction, None)
  }

  test("powers require non-empty unique windows and typed modifier consistency") {
    intercept[IllegalArgumentException](PowerRegistry(power("test.empty", Vector.empty)))
    intercept[IllegalArgumentException](PowerRegistry(power("test.duplicate",
      Vector(RecoverEligibility, RecoverEligibility))))
    intercept[IllegalArgumentException](PowerRegistry(power("test.wrong",
      Vector(RecoverEligibility), modifier = Some(Search))))
    intercept[IllegalArgumentException](PowerRegistry(power("test.non-action",
      Vector(RestStart), modifier = Some(Recover))))
    assertEquals(power("test.right", Vector(RecoverActionEligibility,
      RecoverModifierSelection, RecoverEligibility), modifier = Some(Recover))
      .modifier, Some(Recover))
  }

  test("resolver routing uses only the precise window and ignores modifier metadata") {
    val withModifier = power("test.with-modifier", Vector(RecoverEligibility),
      recover, modifier = Some(Recover))
    val withoutModifier = power("test.without-modifier",
      Vector(RecoverEligibility), recover)
    val resolver = new PowerResolver(PowerRegistry(withModifier, withoutModifier))
    val sources = Vector(sourceA -> Vector(withModifier.id, withoutModifier.id))
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    assertEquals(resolver.resolve(SearchEligibility, sources, facts).toOption.get
      .offered, Vector.empty)
    assertEquals(resolver.resolve(RecoverEligibility, sources, facts).toOption.get
      .offered.map(_.powerId), Vector(PowerId("test.with-modifier"),
        PowerId("test.without-modifier")))
  }

  test("typed facts reach inspectors and inapplicable implemented powers disappear") {
    val applicable = power("test.applicable", Vector(RecoverEligibility), recover)
    val inapplicable = power("test.inapplicable", Vector(RecoverEligibility), never)
    val resolver = new PowerResolver(PowerRegistry(applicable, inapplicable))
    val actor = PlayerId("p1")
    val result = resolver.resolve(RecoverEligibility, Vector(sourceA -> Vector(
      applicable.id, inapplicable.id)),
      RecoverFacts(actor, emptySlot = true)).toOption.get
    assertEquals(result.offered.map(_.powerId), Vector(PowerId("test.applicable")))
    assertEquals(result.offered.head.inspection.eligiblePlayer, Some(actor))
    assertEquals(result.offered.head.inspection.decisionPlayer, Some(actor))
  }

  test("fallback diagnoses only applicable unimplemented automatic powers") {
    val selected = power("test.selected", Vector(RestStart),
      implemented = false)
    val applicableAutomatic = power("test.applicable-automatic",
      Vector(RestStart), implemented = false, resolution = Automatic)
    val inapplicableAutomatic = power("test.inapplicable-automatic",
      Vector(RestStart), never, implemented = false,
      resolution = Automatic)
    val implementedAutomatic = power("test.implemented-automatic",
      Vector(RestStart), resolution = Automatic)
    val powers = Vector(selected, applicableAutomatic, inapplicableAutomatic,
      implementedAutomatic)
    val result = new PowerResolver(PowerRegistry(powers: _*)).resolve(RestStart,
      Vector(sourceA -> powers.map(_.id)), NoFacts).toOption.get
    assertEquals(result.offered, Vector.empty)
    assertEquals(result.automatic.map(_.powerId),
      Vector(PowerId("test.implemented-automatic")))
    assertEquals(result.diagnostics.map(_.powerId),
      Vector(PowerId("test.applicable-automatic")))
  }

  test("resolution order is stable source identity then typed power ID") {
    val alpha = power("test.alpha", Vector(RecoverEligibility), recover)
    val beta = power("test.beta", Vector(RecoverEligibility), recover)
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    val result = new PowerResolver(PowerRegistry(beta, alpha)).resolve(
      RecoverEligibility, Vector(sourceZ -> Vector(beta.id,
        alpha.id), sourceA -> Vector(beta.id)), facts)
      .toOption.get
    assertEquals(result.offered.map(i => i.source.stableKey -> i.powerId), Vector(
      "site:a" -> PowerId("test.beta"), "site:z" -> PowerId("test.alpha"),
      "site:z" -> PowerId("test.beta")))
  }

  test("registry rejects duplicates and unknown abilities fail before routing") {
    val first = power("test.same", Vector(RestStart))
    val second = power("test.same", Vector(RestEnd))
    intercept[IllegalArgumentException](PowerRegistry(first, second))
    val resolver = new PowerResolver(PowerRegistry())
    val sources = Vector(sourceA -> Vector(PowerId("test.unknown")))
    assert(resolver.validateSources(sources).isLeft)
    assert(resolver.resolve(RestStart, sources, NoFacts).isLeft)
  }

  test("one power may use different resolution modes at distinct windows") {
    val inspector = PowerInspector(always)
    val multi = TestPower(PowerId("test.multi"), None, Vector(
      PowerHandlers.automatic(RestStart)(inspector),
      PowerHandlers.selected(RestEnd)(inspector)))
    val resolver = new PowerResolver(PowerRegistry(multi))
    val sources = Vector(sourceA -> Vector(multi.id))
    assertEquals(resolver.resolve(RestStart, sources, NoFacts).toOption.get
      .automatic.map(_.powerId), Vector(multi.id))
    assertEquals(resolver.resolve(RestEnd, sources, NoFacts).toOption.get
      .offered.map(_.powerId), Vector(multi.id))
  }

  test("partial inspectors safely reject unrelated facts and factory modes persist") {
    val inspector = PowerInspector.partial {
      case PowerContext(_, _, facts: RecoverFacts) =>
        PowerInspection(facts.emptySlot, Some(facts.actor), Some(facts.actor))
    }
    val automatic = PowerHandlers.automatic(RecoverEligibility)(inspector)
    val selected = PowerHandlers.selected(RecoverModifierSelection,
      implemented = false)(inspector)
    assertEquals(automatic.resolution, Automatic)
    assertEquals(automatic.implemented, true)
    assertEquals(selected.resolution, PlayerSelected)
    assertEquals(selected.implemented, false)
    assertEquals(automatic.inspect(PowerContext(RecoverEligibility, sourceA,
      NoFacts)), PowerInspection(applicable = false))
    assert(automatic.inspect(PowerContext(RecoverEligibility, sourceA,
      RecoverFacts(PlayerId("p1"), emptySlot = true))).applicable)
  }
}
