package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
  GameIntent => GameCommand}

/** One named bucket a partition interaction spreads its items across: the
  * stable `key` an answer names, the copy its zone heading shows, the fewest
  * items it must hold before the interaction may be confirmed, and the most
  * it will accept.
  *
  * `maxAllowed` is `None` for an unbounded section. A section bounded at
  * exactly one swaps instead of refusing (see
  * [[PartitionDecisionState.placeBefore]]) -- which is how Search's single
  * Keep slot has always behaved, and the only reason a bound is here at all.
  * A parked walker partition declares no maximum: its minima are what the
  * engine offered, and nothing else constrains the spread.
  */
private[frontend] final case class PartitionSection(key: String, label: String,
    minRequired: Int, maxAllowed: Option[Int] = None)

/** The two-zone move/drag interaction, with no idea what it is partitioning.
  *
  * Extracted from `CardDecisionState`'s Keep/Discard zones so a parked
  * walker partition decision can borrow the same interaction rather than
  * grow a second one. Both callers adapt into it and neither is visible
  * from here: an item is an opaque id, a section is whatever its caller
  * declared, and confirmation is `every item placed exactly once` plus
  * `every declared minimum met`. That predicate is the whole point --
  * a client computes it from what it was offered, never from local
  * knowledge of an action's cost.
  *
  * The direction of reuse is one-way. `CardDecisionState` keeps its
  * two-stage `Arrange`/`Resolve` flow, its labels and its discard ordering
  * on top of this; this learns no Search or setup concept in return, and a
  * walker query never depends on the card-decision pipeline.
  */
private[frontend] final case class PartitionDecisionState(
    sections: Vector[PartitionSection],
    items: Vector[String],
    contents: Map[String, Vector[String]]
) {
  def section(key: String): Option[PartitionSection] = sections.find(_.key == key)

  def itemsIn(key: String): Vector[String] = contents.getOrElse(key, Vector.empty)

  def sectionOf(item: String): Option[String] =
    sections.map(_.key).find(key => itemsIn(key).contains(item))

  /** Every placed item, in declared section order then within-section order.
    * Items parked under a key no section declares are deliberately absent,
    * which is what makes a stale placement fail [[complete]].
    */
  def placed: Vector[String] = sections.flatMap(section => itemsIn(section.key))

  /** Each item paired with the section holding it, in declared item order --
    * the order a submitted answer names them in.
    */
  def placements: Vector[(String, String)] =
    items.flatMap(item => sectionOf(item).map(item -> _))

  /** Moves one item into a declared section, appending it. A section this
    * interaction never declared, an item it never offered, and a move into
    * the section the item already occupies are all no-ops, so a stale click
    * cannot build an answer the engine would reject on a key it never
    * offered.
    */
  def moveTo(item: String, sectionKey: String): PartitionDecisionState =
    if (sectionOf(item).contains(sectionKey)) this
    else placeBefore(item, sectionKey, None)

  /** Places one item into a declared section, before `before` when that
    * anchor is present there and at the end otherwise. A full section
    * refuses the move, except a single-slot one, which swaps: its occupant
    * returns to the front of the section the arriving item came from.
    */
  def placeBefore(item: String, sectionKey: String, before: Option[String])
      : PartitionDecisionState =
    section(sectionKey).filter(_ => items.contains(item)).fold(this) { target =>
      val occupants = itemsIn(sectionKey).filterNot(_ == item)
      val full = target.maxAllowed.exists(occupants.size >= _)
      sectionOf(item) match {
        case Some(from) if full && target.maxAllowed.contains(1) =>
          withSection(sectionKey, Vector(item))
            .withSection(from, occupants ++ itemsIn(from).filterNot(_ == item))
        case _ if full => this
        case from =>
          val index = before.map(occupants.indexOf).filter(_ >= 0)
            .getOrElse(occupants.size)
          from.filterNot(_ == sectionKey)
            .fold(this)(key => withSection(key, itemsIn(key).filterNot(_ == item)))
            .withSection(sectionKey, occupants.patch(index, Vector(item), 0))
      }
    }

  /** Slides one item within the section holding it, clamped to that
    * section's ends.
    */
  def shift(item: String, delta: Int): PartitionDecisionState =
    sectionOf(item).fold(this) { key =>
      val current = itemsIn(key)
      val from = current.indexOf(item)
      val to = math.max(0, math.min(current.size - 1, from + delta))
      if (from == to) this
      else withSection(key, current.patch(from, Nil, 1).patch(to, Vector(item), 0))
    }

  /** Every offered item placed in a declared section, exactly once. */
  def complete: Boolean = placed.sorted == items.sorted

  def minimaMet: Boolean =
    sections.forall(section => itemsIn(section.key).size >= section.minRequired)

  def withinCapacity: Boolean = sections.forall(section =>
    section.maxAllowed.forall(itemsIn(section.key).size <= _))

  def canConfirm: Boolean = complete && minimaMet && withinCapacity

  private def withSection(key: String, values: Vector[String])
      : PartitionDecisionState = copy(contents = contents.updated(key, values))
}

private[frontend] object PartitionDecisionState {
  /** Opens with every item in one section -- how a card decision starts, all
    * candidates in Discard until the player moves one across.
    */
  def allIn(sections: Vector[PartitionSection], items: Vector[String],
      sectionKey: String): PartitionDecisionState =
    PartitionDecisionState(sections, items, Map(sectionKey -> items))

  /** Opens by filling each section to its minimum in declared order, then
    * putting any item the minima do not account for into the first section.
    *
    * Forge's minima always sum to its option count, so the padding never
    * fires there; it exists so a partition with slack still opens on a fully
    * placed draft rather than an unanswerable one. Minima that exceed the
    * item count leave a later section short, which [[canConfirm]] refuses --
    * correctly, since no arrangement of those items would satisfy them.
    */
  def filled(sections: Vector[PartitionSection], items: Vector[String])
      : PartitionDecisionState = {
    val required = sections.flatMap(section =>
      Vector.fill(section.minRequired)(section.key))
    val slack = items.size - required.size
    val keys = if (slack <= 0) required.take(items.size)
      else required ++ Vector.fill(slack)(sections.headOption.fold("")(_.key))
    PartitionDecisionState(sections, items, items.zip(keys).groupBy(_._2)
      .map { case (key, placed) => key -> placed.map(_._1) })
  }
}

/** A draft answer to a parked walker partition decision, adapted into the
  * shared interaction above.
  *
  * Everything it needs comes from `WalkerDecisionState.query`: the sections
  * carry their own keys, labels and minima, the options carry their own
  * references. Nothing here knows Forge's printed cost, its resource names
  * or its target discovery -- which is what makes the submitted answer
  * generic, a `PartitionWire` naming each projected option exactly once.
  */
private[frontend] final case class WalkerPartitionDraft(
    context: BoardSelectionContext,
    decisionId: String,
    query: DecisionQueryState,
    partition: PartitionDecisionState
) {
  def optionFor(item: String): Option[DecisionOptionState] =
    query.options.find(option => WalkerPartitionDraft.itemId(option) == item)

  /** The options a zone should render, in the order the player left them. */
  def optionsIn(sectionKey: String): Vector[DecisionOptionState] =
    partition.itemsIn(sectionKey).flatMap(optionFor)

  def move(item: String, sectionKey: String): WalkerPartitionDraft =
    copy(partition = partition.moveTo(item, sectionKey))

  def canConfirm: Boolean = partition.canConfirm

  def command(playerId: String): Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.PartitionWire(partition.placements.flatMap {
        case (item, sectionKey) => optionFor(item).map(option =>
          DecisionPlacementWire(option.kind, option.id, sectionKey))
      })))
}

private[frontend] object WalkerPartitionDraft {
  /** The item identity a zone drags and an answer resolves back to an
    * option: the option's own reference pair, which a query guarantees is
    * unique within it.
    */
  def itemId(option: DecisionOptionState): String =
    s"${option.kind}:${option.id}"

  /** Adopts whichever parked decision projects a partition query, and drops
    * the draft whenever the decision or the query itself changes -- an
    * option added or removed by a power is a different question, so a draft
    * assembled against the old one must not survive it.
    */
  def reconcile(previous: Option[WalkerPartitionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerPartitionDraft] =
    decision.flatMap(parked => parked.query.filter(_.form == "partition")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(draft => draft.context == context &&
            draft.decisionId == decisionId && draft.query == query)
          .getOrElse(WalkerPartitionDraft(context, decisionId, query,
            PartitionDecisionState.filled(query.sections.map(section =>
              PartitionSection(section.key, section.label, section.minRequired)),
              query.options.map(itemId))))
      }
}
