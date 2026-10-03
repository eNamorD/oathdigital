package oathdigital.engine

import oathdigital.testkit.RunningTotalEvolution

/** The running-total fake keeps the `EventEvolution` contract, which the
  * replay engine's tests rely on. */
class RunningTotalEvolutionSuite
    extends EventEvolutionContract[Int, Int, String]:
  protected val evolution = RunningTotalEvolution
  protected def events = Vector(2, 3, 5)
  protected def corrupt = -1
