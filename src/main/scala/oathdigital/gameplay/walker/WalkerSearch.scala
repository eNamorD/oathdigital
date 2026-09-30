package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionAnswer, DecisionOptionRef,
  DecisionQuery, Draw, Flip, OathViolation, Operation, Orientation, Peek,
  Reveal, Roll, Shuffle}

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

  /** Whether `operation` shows a player something the chooser cannot see
    * yet: dice, a draw, a shuffle, a peek, or a card turned faceup. A search
    * stops there and counts the path as legal, since pruning past it would
    * leak the outcome.
    */
  def hides(operation: Operation): Boolean = operation match
    case _: Roll | _: Shuffle | _: Draw | _: Peek | _: Reveal => true
    case flip: Flip => flip.orientation == Orientation.FaceUp
    case _ => false

  /** `decide` offering only the options that some answer through them
    * survives. A choose-one is tried option by option. A choose-many keeps an
    * option when some accepted selection holding it survives, smallest first.
    * A choose-amount is tried value by value and narrowed when the survivors
    * form one range; with a gap it is left whole. Other kinds are not
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
        val refs = one.options.map(_.ref)
        kept(decide, refs, refs.map(ref =>
          ref -> verdict(DecisionAnswer.ChooseOneAnswer(ref))).toMap,
          required = !decide.passWhenEmpty)
      case many: DecisionQuery.ChooseMany =>
        val refs = many.options.map(_.ref)
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
      case _ => Right(Some(decide))

  /** What a search does at `decide`: it passes an optional decision with
    * nothing to ask, stops at the first answer that survives, and fails when
    * none does. A choose-one passed when empty is passed when none does. A
    * kind whose answers are not tried (`Partition`, `Distribute`) counts as
    * answerable, and the answer-time check stays.
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

  /** `decide` offering only the permitted options. `None` is an optional
    * choose-many or a choose-one passed when empty, with nothing left, which
    * is not asked. Other query kinds are returned unchanged.
    */
  def narrowed(decide: Decide,
      permitted: DecisionOptionRef => Boolean): Option[Decide] =
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filter(o => permitted(o.ref))
        if decide.passWhenEmpty && options.isEmpty then None
        else Some(decide.copy(query = one.copy(options = options)))
      case many: DecisionQuery.ChooseMany =>
        val options = many.options.filter(o => permitted(o.ref))
        if many.min == 0 && options.isEmpty then None
        else Some(decide.copy(query = many.copy(
          min = math.min(many.min, options.size),
          max = math.min(many.max, options.size), options = options)))
      case _ => Some(decide)

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
      case one: DecisionQuery.ChooseOne => Some(one.options.iterator.map(
        option => DecisionAnswer.ChooseOneAnswer(option.ref)))
      case many: DecisionQuery.ChooseMany => Some(selections(
        many.options.map(_.ref), many.min, many.max)
        .map(DecisionAnswer.ChooseManyAnswer(_)))
      case amount: DecisionQuery.ChooseAmount => Some(
        (amount.min to amount.max).iterator
          .map(DecisionAnswer.ChooseAmountAnswer(_)))
      case _ => None

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
