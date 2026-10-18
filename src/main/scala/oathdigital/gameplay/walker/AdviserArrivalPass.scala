package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRun}
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower}
import oathdigital.model.{NoteStates, OathViolation, PowerId, ReadyGame}

/** The adviser arrival pass (catalog batch 5, "The arrival pass"). After a
  * leaf's operations apply, the advisers that arrived in it, new to a player
  * or turned faceup there, go to every power's `onArrival`, in the order
  * contributions chain. Each follow-up runs as one required batch, recorded
  * as its own step at the leaf's node and credited to its power, and its
  * line is journaled right after it. A follow-up with no operations records
  * no step and journals only its line. A follow-up's own arrivals start no
  * second pass. The leaf's `previous` then spans the leaf and its
  * follow-ups, so a note after the leaf reads the whole effect. The pass
  * names no card.
  */
private[walker] object AdviserArrivalPass:
  /** `ran`, the walk after a leaf that started at `before`, with the
    * follow-ups of the leaf's arrivals run. */
  def after(before: ReadyGame, ran: WalkCtx, nodeId: String)
      : Either[OathViolation, WalkCtx] =
    val arrived = AdviserArrivals.between(ran.actor, before, ran.state)
    if arrived.arrivals.isEmpty then Right(ran)
    else ran.powers.powers.sortBy(ContributingPower.sortKey)
      .foldLeft[Either[OathViolation, WalkCtx]](Right(ran)) {
        (walked, power) => walked.flatMap(ctx =>
          power.onArrival(arrived.copy(after = ctx.state))
            .fold[Either[OathViolation, WalkCtx]](Right(ctx))(
              followed(ctx, power.id, _, nodeId)))
      }.map(ctx => ctx.copy(previous = Some((before, ctx.state))))

  private def followed(ctx: WalkCtx, power: PowerId,
      followUp: ArrivalFollowUp, nodeId: String)
      : Either[OathViolation, WalkCtx] =
    val ran =
      if followUp.operations.isEmpty then
        Right(OperationRun(ctx.state, Vector.empty, Vector.empty))
      else OperationPipeline.run(ctx.state, followUp.operations,
        OperationPolicy.Permissive, ctx.powers.operationRestrictions,
        requireAll = true)(Right(_))
    ran.map { updated =>
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = WalkerStepPayload.DeltaRecorded(
          DeltaMeaning.of(updated.executed, label)),
        ops = updated.executed,
        contributions = Vector(power)))
      val note = followUp.note.build(NoteStates(updated.state,
        Some((ctx.state, updated.state)), ctx.answered)).flatMap(built =>
        NoteCheck.journal(ctx.powers.declared, followUp.note.power, built,
          followUp.note.covers))
      ctx.copy(state = updated.state, events = ctx.events ++ step ++ note)
    }

  /** The fallback name of a follow-up's step. */
  private val label = "AdviserArrival"
