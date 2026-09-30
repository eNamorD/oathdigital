package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{board, rules}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The lazy pruning's budget (global operation restrictions design,
  * "Performance budget"): a Campaign park on a full board answers in under
  * 50 ms. A benchmark run by hand, never by `sbt test`:
  *
  * {{{./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"}}}
  *
  * Each measure is the best of five runs after three warm-up runs, so a cold
  * first run does not count. It exits with status 1 when a measure is over
  * budget.
  */
object SearchBudget:
  private val budgetMillis = 50L

  private def best(body: => Any): Long =
    (1 to 3).foreach(_ => body)
    (1 to 5).map { _ =>
      val start = System.nanoTime()
      body
      (System.nanoTime() - start) / 1000000L
    }.min

  def main(args: Array[String]): Unit =
    val r = rules(CampaignFixture.anyDice)
    val walkerPowers = WalkerPowerCatalog.default(catalog)
    val b = board(extras = 3)
    def started: ReadyGame =
      r.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor) match
        case Right(transition) => transition.state match
          case Ready(value) => value
          case other => sys.error(s"expected a ready game, got $other")
        case Left(violation) => sys.error(s"Campaign must start: $violation")
    val start = best(started)
    val ready = started
    val pending = ready.game.current.walkerPending.get
    val procedure = ready.game.current.walkerProcedure.get
    val tree = WalkerProcedureRegistry.rebuild(procedure, catalog, ready,
      b.actor, Vector.empty).toOption.get
    val read = best(ProcedureWalker.openDecisions(ready, tree, pending,
      walkerPowers))
    println(s"Campaign start: $start ms; reading its park: $read ms " +
      s"(budget $budgetMillis ms each)")
    if start >= budgetMillis || read >= budgetMillis then sys.exit(1)
