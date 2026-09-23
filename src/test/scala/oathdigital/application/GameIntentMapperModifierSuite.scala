package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.ModifierInvocation

class GameIntentMapperModifierSuite extends munit.FunSuite {
  test("a game-rule modifier invocation binds to its GameRule source") {
    assertEquals(
      GameIntentMapper.bindModifiers(PlayerId("red"), Vector(
        ModifierInvocation("game", "denizen.rowdy-pub", None, "denizen.rowdy-pub"))),
      Right(Vector(OrderedRuleInvocation(
        RuleSourceRef.GameRule("denizen.rowdy-pub"), "denizen.rowdy-pub"))))
  }
}
