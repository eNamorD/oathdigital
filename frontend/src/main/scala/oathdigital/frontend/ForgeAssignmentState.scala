package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
  GameIntent => GameCommand}

/** Draft answer to a parked partition decision: one section key per
  * projected option, in the query's own option order.
  *
  * Task 4 re-sourced this from `WalkerDecisionState.query`. Nothing here
  * knows Forge's printed cost, its resource names, or its target discovery:
  * the sections carry their own labels and minima, the options carry their
  * own references, and the confirmation predicate is computed from those
  * minima. That is what makes the submitted answer generic -- a
  * `PartitionWire` of placements naming each projected option exactly once.
  *
  * Still named for Forge because Forge is its only caller and it still
  * carries Forge's dropdown interaction; Task 5 replaces both with the
  * shared two-zone `PartitionDecisionState`.
  */
private[frontend] final case class ForgeAssignmentState(
    context: BoardSelectionContext,
    decisionId: String,
    query: DecisionQueryState,
    assignments: Vector[String]
) {
  /** Moves one option into a declared section. A section the query does not
    * declare is ignored rather than recorded, so a stale click cannot build
    * an answer the engine would reject on a key it never offered.
    */
  def choose(index: Int, sectionKey: String): ForgeAssignmentState =
    if (!assignments.indices.contains(index) ||
        !query.sections.exists(_.key == sectionKey)) this
    else copy(assignments = assignments.updated(index, sectionKey))

  /** Every option placed in a declared section, and every projected minimum
    * met. Both halves come from the projection: this is the whole reason
    * the client no longer needs to be told "two favor and one secret".
    */
  def canConfirm: Boolean = assignments.size == query.options.size &&
    assignments.forall(key => query.sections.exists(_.key == key)) &&
    query.sections.forall(section =>
      assignments.count(_ == section.key) >= section.minRequired)

  def command(playerId: String): Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.PartitionWire(
        query.options.zip(assignments).map { case (option, sectionKey) =>
          DecisionPlacementWire(option.kind, option.id, sectionKey)
        })))
}

private[frontend] object ForgeAssignmentState {
  /** Adopts whichever parked decision projects a partition query, and drops
    * the draft whenever the decision or the query itself changes -- an
    * option added or removed by a power is a different question, so a draft
    * assembled against the old one must not survive it.
    */
  def reconcile(previous: Option[ForgeAssignmentState],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[ForgeAssignmentState] =
    decision.flatMap(parked => parked.query.filter(_.form == "partition")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(state => state.context == context &&
            state.decisionId == decisionId && state.query == query)
          .getOrElse(ForgeAssignmentState(context, decisionId, query,
            opening(query)))
      }

  /** The opening draft fills each section to its minimum in declared order,
    * then puts any option the minima do not account for into the first
    * section. Forge's minima always sum to its option count, so the padding
    * never fires there; it exists so a partition with slack still opens on
    * a fully placed draft rather than an unanswerable one.
    */
  private def opening(query: DecisionQueryState): Vector[String] = {
    val required = query.sections.flatMap(section =>
      Vector.fill(section.minRequired)(section.key))
    val slack = query.options.size - required.size
    if (slack <= 0) required.take(query.options.size)
    else required ++ Vector.fill(slack)(
      query.sections.headOption.fold("")(_.key))
  }
}
