package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.twoSeatProjection

import munit.FunSuite

/** The phase power buttons and the Finish Rest button. */
class PhasePowerButtonsSuite extends FunSuite:

  test("a legal phase power becomes one usePower command"):
    val power = PhasePowerState("denizen.silver-tongue",
      DecisionOptionState("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")
    val legal = twoSeatProjection(
      Set("usePower:denizen.silver-tongue:92", "finishRest"),
      phase = "rest", phasePowers = Vector(power))
    assertEquals(PhasePowerButtons.actions(legal), Vector(power ->
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))))
    assertEquals(PhasePowerButtons.actions(twoSeatProjection(Set("finishRest"),
      phase = "rest", phasePowers = Vector(power))), Vector.empty)

  test("Finish Rest is offered only when finishRest is legal"):
    assert(PhasePowerButtons.showsFinishRest(
      twoSeatProjection(Set("finishRest"), phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(
      twoSeatProjection(Set.empty, phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(
      twoSeatProjection(Set("finishRest"), phase = "wake")))
