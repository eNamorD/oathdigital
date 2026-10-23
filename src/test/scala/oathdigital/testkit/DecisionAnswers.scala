package oathdigital.testkit

import oathdigital.model.{DecisionAnswer, DecisionOptionRef}

/** Answers to a parked decision, built the way a client would send them. */
object DecisionAnswers:
  /** The answer that picks `ref` from a ChooseOne question. */
  def chooseOne(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)
