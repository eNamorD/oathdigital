package oathdigital.gameplay.walker

/** One fact a procedure shows beside a parked decision, as a label and its
  * value: "Attack dice" and "9" beside a battle-plan window. The procedure
  * words both, from state, as it words its roll feedback.
  */
final case class WalkerStatusLine(label: String, value: String)
