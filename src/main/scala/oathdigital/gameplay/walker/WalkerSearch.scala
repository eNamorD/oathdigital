package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionAnswer, DecisionOption,
  DecisionOptionRef, DecisionPlacement, DecisionQuery, DecisionSection,
  OathViolation, OptionReason}

/** The lazy pruning's answers and narrowing (global operation restrictions
  * design, "Lazy pruning"), apart from the walk. A verdict says whether one
  * answer to a decision can reach a legal end of the action;
  * [[ProcedureWalker]] gives it by walking a copy of the state from that
  * answer.
  */
private[walker] object WalkerSearch:

  /** What a search does at a decision it reaches. */
  enum Reach:
    /** Some answer leads on, or the decision's answers are not tried. */
    case Answerable
    /** An optional decision with nothing to ask, which the walk passes. */
    case Skipped

  /** `decide` offering only the options that some answer through them
    * survives. A choose-one is tried option by option. A choose-many keeps an
    * option when some accepted selection holding it survives, smallest first.
    * A choose-amount is tried value by value and narrowed when the survivors
    * form one range; with a gap it is left whole. A small partition is
    * tried split by split and kept whole, since no option of it can be
    * dropped: it fails when no split survives. Other kinds are not
    * narrowed. `Left` is a required decision with nothing left, carrying the
    * first option's rejection. `Right(None)` is an optional one with nothing
    * left, which is not asked: a choose-many whose minimum is zero, or a
    * choose-one passed when empty.
    */
  def narrow(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Option[Decide]] =
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val refs = choosable(decide)
        kept(decide, refs, refs.map(ref =>
          ref -> verdict(DecisionAnswer.ChooseOneAnswer(ref))).toMap,
          required = !decide.passWhenEmpty)
      case many: DecisionQuery.ChooseMany =>
        val refs = choosable(decide)
        kept(decide, refs, refs.map(ref => ref -> survivor(
          selections(refs, many.min, many.max).filter(_.contains(ref))
            .map(DecisionAnswer.ChooseManyAnswer(_)), verdict, None)).toMap,
          required = many.min >= 1)
      case amount: DecisionQuery.ChooseAmount =>
        val verdicts = (amount.min to amount.max).toVector.map(value =>
          value -> verdict(DecisionAnswer.ChooseAmountAnswer(value)))
        val allowed = verdicts.collect { case (value, Right(_)) => value }
        if verdicts.isEmpty then Right(Some(decide))
        else if allowed.isEmpty then
          Left(verdicts.collect { case (_, Left(violation)) => violation }.head)
        else if allowed.last - allowed.head + 1 != allowed.size then
          Right(Some(decide))
        else Right(Some(decide.copy(query = amount.copy(min = allowed.head,
          max = allowed.last, suggested = amount.suggested.map(value =>
            math.max(allowed.head, math.min(allowed.last, value)))))))
      case partition: DecisionQuery.Partition =>
        splits(decide, partition).fold(Right(Some(decide)))(all =>
          survivor(all, verdict, None).map(_ => Some(decide)))
      case _ => Right(Some(decide))

  /** What a search does at `decide`: it passes an optional decision with
    * nothing to ask, stops at the first answer that survives, and fails when
    * none does. A choose-one passed when empty is passed when none does. A
    * kind whose answers are not tried (a large `Partition`, `Distribute`)
    * counts as answerable, and the answer-time check stays.
    */
  def reach(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Reach] =
    narrowed(decide, _ => true) match
      case None => Right(Reach.Skipped)
      case Some(_) => answers(decide) match
        case None => Right(Reach.Answerable)
        case Some(all) => survivor(all, verdict, None) match
          case Left(_) if decide.passWhenEmpty => Right(Reach.Skipped)
          case found => found.map(_ => Reach.Answerable)

  /** `decide` offering only the permitted options. An option `permitted`
    * refuses is kept as unavailable when `explain` gives its reason, and is
    * removed otherwise; an option already unavailable stays as it is.
    * `None` is an optional choose-many or a choose-one passed when empty,
    * left with no option that may be chosen, which is not asked. A
    * choose-many with an askable option is kept, so its player can ask
    * (consent slice 2). Other query kinds are returned unchanged.
    */
  def narrowed(decide: Decide, permitted: DecisionOptionRef => Boolean,
      explain: DecisionOptionRef => Option[OptionReason] = _ => None)
      : Option[Decide] =
    def cut(options: Vector[DecisionOption]): Vector[DecisionOption] =
      options.flatMap(option =>
        if !option.available || permitted(option.ref) then Some(option)
        else explain(option.ref).map(DecisionOption.Unavailable(option, _)))
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val options = cut(one.options)
        if decide.passWhenEmpty && !options.exists(_.available) then None
        else Some(decide.copy(query = one.copy(options = options)))
      case many: DecisionQuery.ChooseMany =>
        val options = cut(many.options)
        val left = options.count(_.available)
        if many.min == 0 && left == 0 && !options.exists(_.askable) then None
        else Some(decide.copy(query = many.copy(
          min = math.min(many.min, left), max = math.min(many.max, left),
          options = options)))
      case _ => Some(decide)

  /** The references of the options a player may choose in a choose-one or a
    * choose-many, in declared order; empty for any other kind.
    */
  def choosable(decide: Decide): Vector[DecisionOptionRef] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options.filter(_.available)
        .map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.filter(_.available)
        .map(_.ref)
      case _ => Vector.empty

  /** `narrowed`, the search's cut of `decide`, with each option it dropped
    * back in its place as unavailable when `reasons` says why.
    */
  def explained(decide: Decide, narrowed: Decide,
      reasons: Map[DecisionOptionRef, OptionReason]): Decide =
    if reasons.isEmpty then narrowed
    else decide.query match
      case _: DecisionQuery.ChooseOne | _: DecisionQuery.ChooseMany =>
        val kept = choosable(narrowed).toSet
        this.narrowed(decide, kept, reasons.get).getOrElse(narrowed)
      case _ => narrowed

  /** `decide` with each unavailable option `reasons` names given that reason
    * instead: an option the fold barred with its note, made askable once the
    * search found that a grant would open it (consent slice 4). */
  def reasoned(decide: Decide, reasons: Map[DecisionOptionRef, OptionReason])
      : Decide =
    if reasons.isEmpty then decide
    else decide.query match
      case one: DecisionQuery.ChooseOne => decide.copy(query = one.copy(
        options = one.options.map {
          case DecisionOption.Unavailable(option, _)
              if reasons.contains(option.ref) =>
            DecisionOption.Unavailable(option, reasons(option.ref))
          case other => other
        }))
      case _ => decide

  private def kept(decide: Decide, refs: Vector[DecisionOptionRef],
      verdicts: Map[DecisionOptionRef, Either[OathViolation, Unit]],
      required: Boolean): Either[OathViolation, Option[Decide]] =
    val rejected = refs.map(verdicts).collect { case Left(violation) => violation }
    if required && refs.nonEmpty && rejected.size == refs.size then
      Left(rejected.head)
    else Right(narrowed(decide, ref => verdicts(ref).isRight))

  /** Every answer `decide` accepts, in the order a search tries them, or
    * `None` for a kind whose answers are not tried.
    */
  private def answers(decide: Decide): Option[Iterator[DecisionAnswer]] =
    decide.query match
      case _: DecisionQuery.ChooseOne => Some(choosable(decide).iterator
        .map(DecisionAnswer.ChooseOneAnswer(_)))
      case many: DecisionQuery.ChooseMany => Some(selections(
        choosable(decide), many.min, many.max)
        .map(DecisionAnswer.ChooseManyAnswer(_)))
      case amount: DecisionQuery.ChooseAmount => Some(
        (amount.min to amount.max).iterator
          .map(DecisionAnswer.ChooseAmountAnswer(_)))
      case partition: DecisionQuery.Partition => splits(decide, partition)
      case _ => None

  /** The most ways of placing a partition's options in its sections that
    * the search walks through: a Forge's three resources make at most 8. A
    * larger partition counts as answerable. */
  private val SplitLimit = 64

  /** Every split `partition` accepts, each option in one section and each
    * section within its bounds, when it has at most `SplitLimit` raw ways
    * to place its options. A malformed partition is left to the
    * answer-time check, which names what is wrong with it. */
  private def splits(decide: Decide, partition: DecisionQuery.Partition)
      : Option[Iterator[DecisionAnswer]] =
    val refs = partition.options.map(_.ref)
    val keys = partition.sections.map(_.key)
    Option.when(BigInt(keys.size).pow(refs.size) <= SplitLimit &&
        DecisionQueries.wellFormed(decide.decisionId, partition).isRight)(
      refs.foldLeft(Iterator(Vector.empty[String]))((placed, _) =>
        placed.flatMap(prefix => keys.iterator.map(prefix :+ _)))
        .filter(fits(partition.sections, _))
        .map(chosen => DecisionAnswer.PartitionAnswer(refs.zip(chosen)
          .map(DecisionPlacement(_, _)))))

  /** Whether `chosen`, one section key per option, fills each section
    * within its bounds. */
  private def fits(sections: Vector[DecisionSection], chosen: Vector[String])
      : Boolean = sections.forall(section =>
    val count = chosen.count(_ == section.key)
    count >= section.minRequired && section.maxAllowed.forall(count <= _))

  /** The selections of `refs` a choose-many accepts, smallest first, each in
    * declared order. */
  private def selections(refs: Vector[DecisionOptionRef], min: Int,
      max: Int): Iterator[Vector[DecisionOptionRef]] =
    (math.max(min, 0) to math.min(max, refs.size)).iterator
      .flatMap(size => refs.combinations(size))

  /** The first surviving answer's `Right`, or the first rejection. */
  @annotation.tailrec
  private def survivor(answers: Iterator[DecisionAnswer],
      verdict: DecisionAnswer => Either[OathViolation, Unit],
      first: Option[OathViolation]): Either[OathViolation, Unit] =
    if !answers.hasNext then Left(first.getOrElse(
      OathViolation.InvalidEventOrder("the decision offers no answer")))
    else verdict(answers.next()) match
      case Right(()) => Right(())
      case Left(violation) =>
        survivor(answers, verdict, first.orElse(Some(violation)))
