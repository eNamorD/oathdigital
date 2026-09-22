package oathdigital.gameplay.setup

import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** Walks `tree` from scratch, answering every park with the first option a
  * `Decide` offers, until the tree finishes -- shared by `SetupProcedureSuite`
  * and the slice-3 edifice-power suites, which pass a real `WalkerPowers`
  * (unlike the bare-procedure suite, which passes `WalkerPowers.empty`) so
  * their `ContributingPower`s actually fire.
  */
object SetupWalkDriver extends WalkerRecordedOpsReducer with munit.Assertions {
  def driveToCompletion(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : ReadyGame = {
    var state = ready
    var outcome = ProcedureWalker.advance(state, tree, None, powers).toOption.get
    while (outcome.isInstanceOf[WalkerOutcome.Parked]) {
      val WalkerOutcome.Parked(pending, events) = outcome: @unchecked
      state = foldRecordedOps(state, events, "setup walk failed")
      val decide = ProcedureWalker.parkedDecide(state, tree, pending, powers).get
      val answer = Answered(decide.decisionId, defaultAnswer(decide.query),
        decide.owner)
      outcome = ProcedureWalker.resolve(state, tree, pending, answer, powers)
        .toOption.get
    }
    val WalkerOutcome.Finished(finished, _) = outcome: @unchecked
    finished
  }

  /** The first option a query offers: the whole option for `ChooseOne`, or,
    * for `Partition`, the first option kept in whichever section demands at
    * least one, with every other option discarded into another section --
    * the "keep one, discard the rest" shape Setup's adviser choice uses.
    */
  private def defaultAnswer(query: DecisionQuery): DecisionAnswer = query match {
    case DecisionQuery.ChooseOne(options, _) =>
      DecisionAnswer.ChooseOneAnswer(options.head.ref)
    case DecisionQuery.Partition(sections, options, _, _) =>
      val keepSection = sections.find(_.minRequired > 0).getOrElse(sections.head)
      val discardSection = sections.find(_.key != keepSection.key)
        .getOrElse(keepSection)
      val refs = options.map(_.ref)
      DecisionAnswer.PartitionAnswer(
        DecisionPlacement(refs.head, keepSection.key) +:
        refs.tail.map(DecisionPlacement(_, discardSection.key)))
    case other =>
      throw new IllegalArgumentException(
        s"SetupWalkDriver cannot auto-answer $other")
  }
}
