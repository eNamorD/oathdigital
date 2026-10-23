package oathdigital.testkit

import oathdigital.model.{DecisionOption, DecisionOptionRef}

/** Options a test puts in a question, built the way a procedure offers them. */
object DecisionOptions:
  /** The button whose ref and label are both `key`. */
  def button(key: String): DecisionOption.Button =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
