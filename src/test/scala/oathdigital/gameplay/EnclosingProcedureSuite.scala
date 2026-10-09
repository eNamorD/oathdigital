package oathdigital.gameplay

import scala.collection.mutable.ArrayBuffer

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** `PowerCtx.procedure`: the procedure a window is walked for. */
class EnclosingProcedureSuite extends munit.FunSuite:
  private val seen = ArrayBuffer.empty[(PowerWindow, Option[ProcedureRef])]

  /** Spy of ContributingPower with `chosen` resolution: records (window,
    * procedure) in `seen` each time it is asked if it applies, at the Muster
    * windows; always applicable. */
  private def spy(name: String, chosen: PowerResolution): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      override def resolution: PowerResolution = chosen
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.MusterSourceSelection -> Vector.empty,
        PowerWindow.MusterCost -> Vector.empty)
      override def applicable(ctx: PowerCtx): Boolean =
        seen += ctx.window -> ctx.procedure
        true

  /** Selected, so it is asked at the Muster modifier window. */
  private val probe = spy("test.enclosing-procedure",
    PowerResolution.PlayerSelected)
  /** Automatic, so a Muster walks it without a pick: it has no card to be
    * picked by. */
  private val walked = spy("test.enclosing-procedure.walked",
    PowerResolution.Automatic)

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(probe)))
  private val walkingRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(walked)))

  /** p1 stands at Ancient City with the Alchemist; the start's 1 favor and
    * 7 Supply pay for a Muster from it. */
  private def staged: ReadyGame =
    Table.start.pawn(p1, at = "Ancient City")
      .denizen("Alchemist", at = "Ancient City").ready

  test("a modifier is selected for the procedure that will run it"):
    seen.clear()
    rules.offerableWalkerPowers(staged, p1, ActionRef.Muster)
    assertEquals(seen.toVector, Vector[(PowerWindow, Option[ProcedureRef])](
      PowerWindow.MusterModifierSelection -> Some(ActionRef.Muster)))

  test("the command that starts a procedure has not recorded it yet, and a " +
      "resume walks its windows for it"):
    seen.clear()
    val started = accepted(walkingRules.startWalker(Ready(staged),
      ActionRef.Muster, p1))
    assert(seen.contains(PowerWindow.MusterSourceSelection -> None), seen.toString)
    seen.clear()
    accepted(walkingRules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(
        CatalogNames.denizen("Alchemist")))))
    assert(seen.contains(PowerWindow.MusterCost -> Some(ActionRef.Muster)),
      seen.toString)
