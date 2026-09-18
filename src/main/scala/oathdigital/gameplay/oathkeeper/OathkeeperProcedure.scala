package oathdigital.gameplay.oathkeeper

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.{BuildOps, CoreOperation, Decide,
  Operation, Sequence, SetOathkeeper}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Declared tree for every Oathkeeper title change (triggered, started by the
  * action boundary).
  *
  * {{{
  * Transfer(holder)  -> Sequence(SetOathkeeper(holder))
  * Choose(holder, c) -> Sequence(Decide(recipient, owner = holder), BuildOps(SetOathkeeper))
  * }}}
  *
  * A single leader is a forced choice, so it omits the `Decide` and applies the
  * transfer itself. `build` is also `rebuild`: only resume commands are
  * accepted while this parks, so the outcome cannot change under it. No window:
  * nothing may transform a title change until a real power needs to.
  */
object OathkeeperProcedure {
  val recipientDecisionId: String = "oathkeeper.recipient"

  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "the Oathkeeper procedure selects nothing, got " +
        args.map(ref => s"${ref.kind}/${ref.wireId}").mkString(", ")))
    tree <- OathkeeperRules.outcome(state) match {
      case OathkeeperOutcome.NoChange => Left(OathViolation.InvalidEventOrder(
        "no Oathkeeper change to perform"))
      case OathkeeperOutcome.Transfer(holder) =>
        Right(Sequence(Vector(SetOathkeeper(holder))))
      case OathkeeperOutcome.Choose(holder, candidates) => Right(Sequence(Vector(
        Decide(
          decisionId = recipientDecisionId,
          owner = holder,
          query = DecisionQuery.ChooseOne(candidates.map(candidate =>
            DecisionOption.Player(DecisionOptionRef.Player(candidate))),
            heading = Some("Choose the Oathkeeper"))),
        BuildOps((_, pending) => pending.answered.lastOption match {
          case Some(Answered(`recipientDecisionId`,
              ChooseOneAnswer(DecisionOptionRef.Player(chosen)), _)) =>
            Right(Vector[CoreOperation](SetOathkeeper(Some(chosen))))
          case _ => Left(OathViolation.InvalidEventOrder(
            "no Oathkeeper recipient answer is recorded"))
        }))))
    }
  } yield tree
}
