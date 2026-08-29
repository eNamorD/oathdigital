package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.MajorActionType._
import oathdigital.gameplay.powerresolver.PowerResolution._
import oathdigital.gameplay.powerresolver.PowerWindow._
import oathdigital.model.{PlayerId, SiteId}

private object PowerResolverSuiteFixtures {
  final case class RecoverFacts(actor: PlayerId, emptySlot: Boolean)
      extends PowerFacts
}

class PowerResolverSuite extends munit.FunSuite {
  import PowerResolverSuiteFixtures._

  private val sourceA = RuleSourceRef.Site(SiteId("a"))
  private val sourceZ = RuleSourceRef.Site(SiteId("z"))

  private object AlwaysApplicable extends PowerInspector {
    def inspect(context: PowerContext) = PowerInspection(applicable = true)
  }
  private object NeverApplicable extends PowerInspector {
    def inspect(context: PowerContext) = PowerInspection(applicable = false)
  }
  private object RecoverInspector extends PowerInspector {
    def inspect(context: PowerContext) = context.facts match {
      case facts: RecoverFacts => PowerInspection(facts.emptySlot,
        eligiblePlayer = Some(facts.actor), decisionPlayer = Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }
  }
  private object Implemented extends PowerHandler

  private def definition(id: String, windows: Vector[PowerWindow],
      resolution: PowerResolution = PlayerSelected,
      modifier: Option[MajorActionType] = None) =
    PowerDefinition(PowerId(id), modifier, windows, resolution)

  private def registered(id: String, windows: Vector[PowerWindow],
      inspector: PowerInspector = AlwaysApplicable,
      implemented: Boolean = true,
      resolution: PowerResolution = PlayerSelected,
      modifier: Option[MajorActionType] = None) =
    RegisteredPower(definition(id, windows, resolution, modifier), inspector,
      Option.when(implemented)(Implemented))

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

  test("definitions require non-empty unique windows and typed modifier consistency") {
    intercept[IllegalArgumentException](definition("test.empty", Vector.empty))
    intercept[IllegalArgumentException](definition("test.duplicate",
      Vector(RecoverEligibility, RecoverEligibility)))
    intercept[IllegalArgumentException](definition("test.wrong",
      Vector(RecoverEligibility), modifier = Some(Search)))
    intercept[IllegalArgumentException](definition("test.non-action",
      Vector(RestStart), modifier = Some(Recover)))
    assertEquals(definition("test.right", Vector(RecoverActionEligibility,
      RecoverModifierSelection, RecoverEligibility), modifier = Some(Recover))
      .modifier, Some(Recover))
  }

  test("resolver routing uses only the precise window and ignores modifier metadata") {
    val withModifier = registered("test.with-modifier", Vector(RecoverEligibility),
      RecoverInspector, modifier = Some(Recover))
    val withoutModifier = registered("test.without-modifier",
      Vector(RecoverEligibility), RecoverInspector)
    val resolver = new PowerResolver(PowerRegistry(withModifier, withoutModifier))
    val sources = Vector(sourceA -> Vector(withModifier.definition.id,
      withoutModifier.definition.id))
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    assertEquals(resolver.resolve(SearchEligibility, sources, facts).toOption.get
      .offered, Vector.empty)
    assertEquals(resolver.resolve(RecoverEligibility, sources, facts).toOption.get
      .offered.map(_.powerId), Vector(PowerId("test.with-modifier"),
        PowerId("test.without-modifier")))
  }

  test("typed facts reach inspectors and inapplicable implemented powers disappear") {
    val applicable = registered("test.applicable", Vector(RecoverEligibility),
      RecoverInspector)
    val inapplicable = registered("test.inapplicable", Vector(RecoverEligibility),
      NeverApplicable)
    val resolver = new PowerResolver(PowerRegistry(applicable, inapplicable))
    val actor = PlayerId("p1")
    val result = resolver.resolve(RecoverEligibility, Vector(sourceA -> Vector(
      applicable.definition.id, inapplicable.definition.id)),
      RecoverFacts(actor, emptySlot = true)).toOption.get
    assertEquals(result.offered.map(_.powerId), Vector(PowerId("test.applicable")))
    assertEquals(result.offered.head.inspection.eligiblePlayer, Some(actor))
    assertEquals(result.offered.head.inspection.decisionPlayer, Some(actor))
  }

  test("fallback diagnoses only applicable unimplemented automatic powers") {
    val selected = registered("test.selected", Vector(RestStart),
      implemented = false)
    val applicableAutomatic = registered("test.applicable-automatic",
      Vector(RestStart), implemented = false, resolution = Automatic)
    val inapplicableAutomatic = registered("test.inapplicable-automatic",
      Vector(RestStart), NeverApplicable, implemented = false,
      resolution = Automatic)
    val implementedAutomatic = registered("test.implemented-automatic",
      Vector(RestStart), resolution = Automatic)
    val powers = Vector(selected, applicableAutomatic, inapplicableAutomatic,
      implementedAutomatic)
    val result = new PowerResolver(PowerRegistry(powers: _*)).resolve(RestStart,
      Vector(sourceA -> powers.map(_.definition.id)), NoFacts).toOption.get
    assertEquals(result.offered, Vector.empty)
    assertEquals(result.automatic.map(_.powerId),
      Vector(PowerId("test.implemented-automatic")))
    assertEquals(result.diagnostics.map(_.powerId),
      Vector(PowerId("test.applicable-automatic")))
  }

  test("resolution order is stable source identity then typed power ID") {
    val alpha = registered("test.alpha", Vector(RecoverEligibility), RecoverInspector)
    val beta = registered("test.beta", Vector(RecoverEligibility), RecoverInspector)
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    val result = new PowerResolver(PowerRegistry(beta, alpha)).resolve(
      RecoverEligibility, Vector(sourceZ -> Vector(beta.definition.id,
        alpha.definition.id), sourceA -> Vector(beta.definition.id)), facts)
      .toOption.get
    assertEquals(result.offered.map(i => i.source.stableKey -> i.powerId), Vector(
      "site:a" -> PowerId("test.beta"), "site:z" -> PowerId("test.alpha"),
      "site:z" -> PowerId("test.beta")))
  }

  test("registry rejects duplicates and unknown abilities fail before routing") {
    val first = registered("test.same", Vector(RestStart))
    val second = registered("test.same", Vector(RestEnd))
    intercept[IllegalArgumentException](PowerRegistry(first, second))
    val resolver = new PowerResolver(PowerRegistry())
    val sources = Vector(sourceA -> Vector(PowerId("test.unknown")))
    assert(resolver.validateSources(sources).isLeft)
    assert(resolver.resolve(RestStart, sources, NoFacts).isLeft)
  }
}
