package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.model.{PowerId, PowerWindow, RuleSourceRef}

/** The window index `gather` reads instead of asking every power for its
  * contributions on every call (test speed design, decision 5).
  */
class PowerWindowIndexSuite extends munit.FunSuite:

  /** Stub of ContributingPower: hooks exactly the windows in `hooks`, with
    * the contributions given for each.
    */
  private def powerHooking(name: String,
      hooks: (PowerWindow, Vector[Contribution])*): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = hooks.toMap

  private def hookedAt(index: PowerWindowIndex, window: PowerWindow)
      : Vector[(PowerId, Vector[Contribution])] =
    index.at(window).map(hooked => hooked.power.id -> hooked.contributions)

  test("each window lists the powers that hook it, in input order, with that window's contributions"):
    val recoverTransform = Transform((_, ops) => ops)
    val rollRestriction = Restriction((_, _) => None)
    val recoverOnlyTransform = Transform((_, ops) => ops)
    val hooksBoth = powerHooking("power.both",
      PowerWindow.RecoverEligibility -> Vector(recoverTransform),
      PowerWindow.RecoverBeforeFirstRoll -> Vector(rollRestriction))
    val hooksRecover = powerHooking("power.recover",
      PowerWindow.RecoverEligibility -> Vector(recoverOnlyTransform))

    // Input order puts "power.recover" first, against id order, so the index
    // must keep input order rather than sort.
    val index = PowerWindowIndex.of(Vector(hooksRecover, hooksBoth))

    assertEquals(hookedAt(index, PowerWindow.RecoverEligibility), Vector(
      PowerId("power.recover") -> Vector(recoverOnlyTransform),
      PowerId("power.both") -> Vector(recoverTransform)))
    assertEquals(hookedAt(index, PowerWindow.RecoverBeforeFirstRoll),
      Vector(PowerId("power.both") -> Vector(rollRestriction)))
    assertEquals(hookedAt(index, PowerWindow.TravelCost), Vector.empty)
