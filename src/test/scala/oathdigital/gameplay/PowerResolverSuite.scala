package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.MajorActionType._
import oathdigital.gameplay.powerresolver.PowerResolution._
import oathdigital.gameplay.powerresolver.PowerWindow._
import oathdigital.model.SiteId

class PowerResolverSuite extends munit.FunSuite {
  private val source = RuleSourceRef.Site(SiteId("site:test"))
  private object Applicable extends PowerHandler {
    def inspect(context: PowerContext) = PowerInspection(applicable = true,
      effects = Vector(PowerEffect.Typed("proof",
        Map("window" -> context.window.key))))
  }

  test("major action vocabulary contains only selectable major actions") {
    assertEquals(MajorActionType.values.map(_.key), Vector("search", "travel",
      "campaign", "muster", "trade", "forge", "recover", "challenge"))
  }

  test("definitions require windows and reject contradictory modifier metadata") {
    intercept[IllegalArgumentException](PowerDefinition("empty", None,
      Vector.empty, Automatic))
    intercept[IllegalArgumentException](PowerDefinition("wrong", Some(Search),
      Vector(RecoverEligibility), PlayerSelected))
  }

  test("resolver routing depends only on the precise window") {
    val power = RegisteredPower(PowerDefinition("catacombs", Some(Recover),
      Vector(RecoverEligibility, RecoverBeforeFirstRoll), PlayerSelected),
      Some(Applicable))
    val resolver = new PowerResolver(PowerRegistry(power))
    val sources = Vector(source -> Vector("catacombs"))
    assertEquals(resolver.resolve(SearchEligibility, sources, ()).toOption.get.offered,
      Vector.empty)
    val result = resolver.resolve(RecoverEligibility, sources, ()).toOption.get
    assertEquals(result.offered.map(_.powerId), Vector("catacombs"))
    assertEquals(result.offered.head.inspection.effects,
      Vector(PowerEffect.Typed("proof", Map("window" -> "recover.eligibility"))))
  }

  test("fallback omits selected powers and diagnoses relevant automatic powers") {
    val selected = RegisteredPower(PowerDefinition("selected", None,
      Vector(RestStart), PlayerSelected), None)
    val automatic = RegisteredPower(PowerDefinition("automatic", None,
      Vector(RestStart), Automatic), None)
    val result = new PowerResolver(PowerRegistry(selected, automatic)).resolve(
      RestStart, Vector(source -> Vector("selected", "automatic")), ()).toOption.get
    assertEquals(result.offered, Vector.empty)
    assertEquals(result.automatic, Vector.empty)
    assertEquals(result.diagnostics.map(_.powerId), Vector("automatic"))
  }

  test("unknown unaudited abilities fail validation") {
    val resolver = new PowerResolver(PowerRegistry())
    val sources = Vector(source -> Vector("unknown"))
    assert(resolver.validateSources(sources).isLeft)
    assert(resolver.resolve(RestStart, sources, ()).isLeft)
  }
}
