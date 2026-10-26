package oathdigital.gameplay.walker

import oathdigital.model.PoolKey

/** One fact a procedure shows beside a parked decision, as a label and its
  * value: "Attack dice" and "9" beside a battle-plan window. The procedure
  * words both, from state, as it words its roll feedback. `pool` names the
  * roll a total is about, so a decision that already shows that roll
  * (`WalkerRollFeedback`) leaves the line out rather than say it twice.
  */
final case class WalkerStatusLine(label: String, value: String,
    pool: Option[PoolKey] = None)
