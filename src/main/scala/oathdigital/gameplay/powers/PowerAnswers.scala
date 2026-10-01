package oathdigital.gameplay.powers

import oathdigital.model._

/** Reads of the answers a power's own decisions recorded in the pending
  * tree. A power's effect is a `BuildOps`, which runs after the answer is
  * recorded, so it reads the answer from here.
  */
object PowerAnswers:
  def one(pending: PendingTree, decision: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst:
    case Answered(`decision`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref

  /** The amount a power's `ChooseAmount` was answered with. */
  def amount(pending: PendingTree, decision: String): Option[Int] =
    pending.answered.collectFirst:
      case Answered(`decision`, DecisionAnswer.ChooseAmountAnswer(count), _) =>
        count

  /** The options a power's `ChooseMany` was answered with. */
  def many(pending: PendingTree, decision: String)
      : Option[Vector[DecisionOptionRef]] = pending.answered.collectFirst:
    case Answered(`decision`, DecisionAnswer.ChooseManyAnswer(refs), _) => refs

  def distribution(pending: PendingTree, decision: String)
      : Option[Vector[DistributeAmount]] = pending.answered.collectFirst:
    case Answered(`decision`, DecisionAnswer.DistributeAnswer(rows), _) => rows

  def missing(decision: String): OathViolation = OathViolation
    .InvalidEventOrder(s"no answer is recorded for $decision")
