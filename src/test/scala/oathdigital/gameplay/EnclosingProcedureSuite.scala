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

/** `PowerCtx.procedure`: the procedure a window is walked for. */
class EnclosingProcedureSuite extends munit.FunSuite:
  private val seen = ArrayBuffer.empty[(PowerWindow, Option[ProcedureRef])]

  /** A power that records what it is asked at three Muster windows. */
  private val probe: ContributingPower = new ContributingPower:
    def id: PowerId = PowerId("test.enclosing-procedure")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.MusterSourceSelection -> Vector.empty,
      PowerWindow.MusterCost -> Vector.empty)
    override def applicable(ctx: PowerCtx): Boolean =
      seen += ctx.window -> ctx.procedure
      true

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(probe)))

  /** p1 stands at Ancient City with the Alchemist; the start's 1 favor and
    * 7 Supply pay for a Muster from it. */
  private def staged: ReadyGame =
    Table.start.denizen("Alchemist", at = Table.homeOf(p1)).ready

  test("a modifier is selected for the procedure that will run it"):
    seen.clear()
    rules.offerableWalkerPowers(staged, p1, ActionRef.Muster)
    assertEquals(seen.toVector, Vector[(PowerWindow, Option[ProcedureRef])](
      PowerWindow.MusterModifierSelection -> Some(ActionRef.Muster)))

  test("the command that starts a procedure has not recorded it yet, and a " +
      "resume walks its windows for it"):
    seen.clear()
    val started = rules.startWalker(Ready(staged), ActionRef.Muster, p1,
      Vector(probe.id)).toOption.get
    assert(seen.contains(PowerWindow.MusterSourceSelection -> None), seen.toString)
    seen.clear()
    rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(
        CatalogNames.denizen("Alchemist")))).toOption.get
    assert(seen.contains(PowerWindow.MusterCost -> Some(ActionRef.Muster)),
      seen.toString)

  test("a context built without one names none"):
    val ctx = PowerCtx(staged, p1, probe.source, PowerWindow.MusterCost,
      Vector.empty, Sequence(Vector.empty))
    assertEquals(ctx.procedure, None)
