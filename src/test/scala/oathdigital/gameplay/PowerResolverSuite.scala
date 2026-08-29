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

  private object Applicable extends PowerHandler {
    def inspect(context: PowerContext) = context.facts match {
      case facts: RecoverFacts => PowerInspection(facts.emptySlot,
        eligiblePlayer = Some(facts.actor), decisionPlayer = Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }
  }
  private object Inapplicable extends PowerHandler {
    def inspect(context: PowerContext) = PowerInspection(applicable = false)
  }

  private def definition(id: String, windows: Vector[PowerWindow],
      resolution: PowerResolution = PlayerSelected,
      modifier: Option[MajorActionType] = None) =
    PowerDefinition(PowerId(id), modifier, windows, resolution)

  test("major action vocabulary contains only selectable major actions") {
    assertEquals(MajorActionType.values.map(_.key), Vector("search", "travel",
      "campaign", "muster", "trade", "forge", "recover", "challenge"))
  }

  test("definitions require non-empty unique windows and validate modifier metadata") {
    intercept[IllegalArgumentException](definition("empty", Vector.empty))
    intercept[IllegalArgumentException](definition("duplicate",
      Vector(RecoverEligibility, RecoverEligibility)))
    intercept[IllegalArgumentException](definition("wrong",
      Vector(RecoverEligibility), modifier = Some(Search)))
    assertEquals(definition("right", Vector(RecoverActionEligibility,
      RecoverModifierSelection, RecoverEligibility), modifier = Some(Recover))
      .modifier, Some(Recover))
  }

  test("resolver routing uses only the precise window and ignores modifier metadata") {
    val withModifier = RegisteredPower(definition("with-modifier",
      Vector(RecoverEligibility), modifier = Some(Recover)), Some(Applicable))
    val withoutModifier = RegisteredPower(definition("without-modifier",
      Vector(RecoverEligibility)), Some(Applicable))
    val resolver = new PowerResolver(PowerRegistry(withModifier, withoutModifier))
    val sources = Vector(sourceA -> Vector(withModifier.definition.id,
      withoutModifier.definition.id))
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    assertEquals(resolver.resolve(SearchEligibility, sources, facts).toOption.get
      .offered, Vector.empty)
    assertEquals(resolver.resolve(RecoverEligibility, sources, facts).toOption.get
      .offered.map(_.powerId), Vector(PowerId("with-modifier"),
        PowerId("without-modifier")))
  }

  test("typed facts reach handlers and inapplicable handlers produce no result") {
    val applicable = RegisteredPower(definition("applicable",
      Vector(RecoverEligibility)), Some(Applicable))
    val inapplicable = RegisteredPower(definition("inapplicable",
      Vector(RecoverEligibility)), Some(Inapplicable))
    val resolver = new PowerResolver(PowerRegistry(applicable, inapplicable))
    val actor = PlayerId("p1")
    val result = resolver.resolve(RecoverEligibility, Vector(sourceA -> Vector(
      applicable.definition.id, inapplicable.definition.id)),
      RecoverFacts(actor, emptySlot = true)).toOption.get
    assertEquals(result.offered.map(_.powerId), Vector(PowerId("applicable")))
    assertEquals(result.offered.head.inspection.eligiblePlayer, Some(actor))
    assertEquals(result.offered.head.inspection.decisionPlayer, Some(actor))
    assertEquals(resolver.resolve(RecoverEligibility,
      Vector(sourceA -> Vector(applicable.definition.id)),
      RecoverFacts(actor, emptySlot = false)).toOption.get.offered, Vector.empty)
  }

  test("resolution order is stable source identity then typed power ID") {
    val alpha = RegisteredPower(definition("alpha", Vector(RecoverEligibility)),
      Some(Applicable))
    val beta = RegisteredPower(definition("beta", Vector(RecoverEligibility)),
      Some(Applicable))
    val facts = RecoverFacts(PlayerId("p1"), emptySlot = true)
    val result = new PowerResolver(PowerRegistry(beta, alpha)).resolve(
      RecoverEligibility, Vector(sourceZ -> Vector(beta.definition.id,
        alpha.definition.id), sourceA -> Vector(beta.definition.id)), facts)
      .toOption.get
    assertEquals(result.offered.map(i => i.source.stableKey -> i.powerId), Vector(
      "site:a" -> PowerId("beta"), "site:z" -> PowerId("alpha"),
      "site:z" -> PowerId("beta")))
  }

  test("registry rejects duplicate typed power IDs") {
    val first = RegisteredPower(definition("same", Vector(RestStart)), None)
    val second = RegisteredPower(definition("same", Vector(RestEnd)), None)
    intercept[IllegalArgumentException](PowerRegistry(first, second))
  }

  test("fallback omits selected powers and diagnoses relevant automatic powers") {
    val selected = RegisteredPower(definition("selected", Vector(RestStart)), None)
    val automatic = RegisteredPower(definition("automatic", Vector(RestStart),
      Automatic), None)
    val result = new PowerResolver(PowerRegistry(selected, automatic)).resolve(
      RestStart, Vector(sourceA -> Vector(selected.definition.id,
        automatic.definition.id)), RecoverFacts(PlayerId("p1"), true)).toOption.get
    assertEquals(result.offered, Vector.empty)
    assertEquals(result.automatic, Vector.empty)
    assertEquals(result.diagnostics.map(_.powerId), Vector(PowerId("automatic")))
  }

  test("unknown unaudited abilities fail before window routing") {
    val resolver = new PowerResolver(PowerRegistry())
    val sources = Vector(sourceA -> Vector(PowerId("unknown")))
    val facts = RecoverFacts(PlayerId("p1"), true)
    assert(resolver.validateSources(sources).isLeft)
    assert(resolver.resolve(RestStart, sources, facts).isLeft)
  }
}
