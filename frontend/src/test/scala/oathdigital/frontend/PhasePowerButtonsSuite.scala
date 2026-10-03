package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.projection

import munit.FunSuite

/** The phase power buttons and the Finish Rest button. */
class PhasePowerButtonsSuite extends FunSuite:

  test("a legal phase power becomes one usePower command"):
    val power = PhasePowerState("denizen.silver-tongue",
      DecisionOptionState("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")
    val legal = projection(Set("usePower:denizen.silver-tongue:92", "finishRest"),
      phase = "rest", phasePowers = Vector(power))
    assertEquals(PhasePowerButtons.actions(legal), Vector(power ->
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))))
    assertEquals(PhasePowerButtons.actions(projection(Set("finishRest"),
      phase = "rest", phasePowers = Vector(power))), Vector.empty)

  test("Finish Rest is offered only when finishRest is legal"):
    assert(PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set.empty,
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "wake")))
