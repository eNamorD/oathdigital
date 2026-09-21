package oathdigital.gameplay.actions.negotiation

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Negotiation on the walker: choose who to negotiate with, then loop one
  * deal decision that every participant may answer until the deal closes,
  * then settle it if it was agreed. A 0-Supply minor action taking no start
  * selection.
  *
  * {{{
  * Sequence(
  *   Decide(negotiators)                  -- omitted when there is one candidate
  *   Repeat(!closed) { Branch -> Decide(deal, co-owned) }
  *   Branch(agreed -> BuildOps(settle)))
  * }}}
  *
  * Nothing about the deal is stored: the guard, the query and the settlement
  * all read `PendingTree.answered` (see [[NegotiationDeal]]).
  */
object NegotiationProcedure {
  val decisionIds: Set[String] = NegotiationDeal.decisionIds

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(NegotiationDeal.eligible(state, actor).nonEmpty, (),
      OathViolation.NegotiationUnavailable(
        "no other player has a pawn at your site"))
  } yield tree(state, actor)

  /** Whether Negotiation could start now: the gates pass and the first walk
    * (up to the first decision) is accepted.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(state, actor))

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Negotiation.key} takes no start " +
        s"selection, got ${args.map(_.kind).mkString(", ")}"))

  private def tree(state: ReadyGame, actor: PlayerId): Operation = {
    val candidates = NegotiationDeal.eligible(state, actor)
    val choose: Vector[Operation] =
      if (candidates.size < 2) Vector.empty
      else Vector(Decide(NegotiationDeal.negotiatorsDecisionId, actor,
        DecisionQuery.ChooseMany(1, candidates.size, candidates.map(id =>
          DecisionOption.Player(DecisionOptionRef.Player(id))),
          heading = Some("Choose who to negotiate with")),
        window = Some(PowerWindow.NegotiationEligibility)))
    Sequence(choose ++ Vector[Operation](
      Repeat((ready, pending) =>
        !NegotiationDeal.deal(ready, actor, pending).closed,
        Branch((ready, pending) => {
          val deal = NegotiationDeal.deal(ready, actor, pending)
          Vector(Decide(NegotiationDeal.dealDecisionId, actor,
            NegotiationDeal.snapshot(ready, deal),
            coOwners = deal.participants.filter(_ != actor)))
        })),
      Branch((ready, pending) => {
        val deal = NegotiationDeal.deal(ready, actor, pending)
        if (deal.agreed) Vector[Operation](BuildOps(
          (state, _) => NegotiationDeal.settle(state, deal),
          window = Some(PowerWindow.NegotiationSettlement)))
        else Vector.empty
      })))
  }
}
