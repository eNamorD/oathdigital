package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.board
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._

/** The Act menu's budget (walker-driven game loop design, ruling 8): on a
  * full board, building and walking the menu to its park, and reading the
  * park back, each take under 50 ms. A benchmark run by hand, never by
  * `sbt test`:
  *
  * {{{./sbtw "Test/runMain oathdigital.gameplay.ActMenuBudget"}}}
  *
  * Each measure is the best of five runs after three warm-up runs. It exits
  * with status 1 when a measure is over budget.
  */
object ActMenuBudget:
  private val budgetMillis = 50L

  private def best(body: => Any): Long =
    (1 to 3).foreach(_ => body)
    (1 to 5).map { _ =>
      val start = System.nanoTime()
      body
      (System.nanoTime() - start) / 1000000L
    }.min

  def main(args: Array[String]): Unit =
    val walkerPowers = WalkerPowers.selected(
      WalkerPowerCatalog.default(catalog), Vector.empty)
    val phasePowers = PhasePowerCatalog.default(catalog)
    // A rich board answers "Start" at once; a poor one cannot afford any
    // start, so every option searches all its modifier picks to the end.
    val measures = Vector(7, 0).map { supply =>
      val b = board(extras = 3, warbands = 5, supply = supply)
      val menu = TriggeredProcedureRef.ActMenu
      def tree: Operation = WalkerProcedureRegistry.build(menu, catalog,
          b.ready, b.actor, Vector.empty, phasePowers,
          walkerPowers = walkerPowers)
        .fold(violation => sys.error(s"the Act menu must build: $violation"),
          identity)
      def parked: PendingTree = ProcedureWalker.advance(b.ready, tree, None,
          walkerPowers) match
        case Right(WalkerOutcome.Parked(pending, _)) => pending
        case other => sys.error(s"the Act menu must park: $other")
      val park = best(parked)
      val pending = parked
      val ready = b.ready.updateCurrent(_.copy(
        walkerPending = Some(pending), walkerProcedure = Some(menu)))
      val read = best(ProcedureWalker.openDecisions(ready, tree, pending,
        walkerPowers))
      println(s"Act menu with $supply Supply: park $park ms; reading it " +
        s"$read ms (budget $budgetMillis ms each)")
      (park, read)
    }
    if measures.exists((park, read) =>
        park >= budgetMillis || read >= budgetMillis) then sys.exit(1)
