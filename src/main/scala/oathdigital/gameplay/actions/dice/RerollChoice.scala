package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.powerresolver.{OfferHost, PowerCtx, RerollHost}
import oathdigital.model._

/** One pass of a roll step's reroll choice: the node the powers'
  * `OfferReroll`s are folded into (catalog batch 7, slice 1). A `Repeat` of
  * passes follows the roll in [[RollStep]].
  *
  * What a pass walks, modelled on the battle-plan window's
  * `CampaignPlanChoice`:
  *
  *  - The roller is asked to pick a reroll or "Keep the roll". The rerolls
  *    listed are those not yet used on this throw, each a button naming its
  *    card after its cost: those the roller can pay for (a dry run of its
  *    payment says so), the rest shown unavailable with why. Nothing usable or askable of a ruler means
  *    nothing is asked, the pass does nothing, and the `Repeat` ends.
  *  - The picked reroll pays and runs its steps.
  *
  * A walk resuming inside a pass keeps its two slots whatever is offered,
  * because the reroll it is running has been picked and is no longer
  * listed.
  */
final class RerollChoice(val pool: PoolKey, val die: DiceKind,
    val roller: PlayerId) extends RerollHost:
  override val window: Option[PowerWindow] = Some(PowerWindow.Reroll)
  override val children: Vector[Operation] = Vector.empty

  /** The throw this choice is about, once the pool has rolled. */
  def throwAt(ready: ReadyGame): Option[DiceThrow] =
    val indices = DiceThrow.lastIndices(ready, pool)
    ready.game.current.rollOutcomes.get(pool).filter(_ => indices.nonEmpty)
      .map(outcome => DiceThrow(pool, die, roller, outcome.throws, indices,
        indices.map(outcome.faces)))

  def expand(offers: Vector[OfferedReroll], pass: OfferHost.Pass)
      : Vector[Operation] =
    throwAt(pass.state).fold(Vector.empty[Operation]) { rolled =>
      val decisionId = RerollChoice.decisionId(pool, rolled.number)
      val pending = PendingTree(Vector.empty, pass.answered)
      val used = RerollChoice.used(pending, decisionId)
      val listed = offers.filterNot(offered =>
        used.contains(RerollChoice.refOf(offered))).map(option(_, pass))
      val playable = listed.exists(option =>
        option.available || option.askable)
      if !playable && !pass.resuming then Vector.empty
      else Vector[Operation](
        // A walk parked on the question keeps it, though a ruler's refusal
        // has since left nothing to pick (catalog batch 8, slice 10).
        Branch((ready, tree) => if !playable && !ready.game.current
            .walkerPending.exists(_.at.startsWith(tree.at)) then Vector.empty
          else Vector(decision(decisionId, listed))),
        Branch((_, tree) => RerollChoice.picked(tree, decisionId) match {
          case None => Vector.empty
          case Some(ref) => offers.find(RerollChoice.refOf(_) == ref) match
            case Some(offered) => Vector(RerollChoice.application(offered))
            case None => Vector(BuildOps((_, _) => Left(
              OathViolation.InvalidEventOrder(
                "a picked reroll is no longer offered"))))
        }))
    }

  private def option(offered: OfferedReroll, pass: OfferHost.Pass)
      : DecisionOption =
    val shown = DecisionOption.Button(RerollChoice.refOf(offered),
      RerollChoice.label(offered.offer))
    offered.offer.withheld match
      case Some(reason) => DecisionOption.Unavailable(shown, reason)
      case None => pass.applies(RerollChoice.application(offered)) match
        case Left(violation) => DecisionOption.Unavailable(shown,
          OptionReason.Written(RerollChoice.shortfall(pass.state, roller,
            offered.offer, violation)))
        case Right(_) => shown

  private def decision(id: String, listed: Vector[DecisionOption]): Decide =
    Decide(id, roller, DecisionQuery.ChooseOne(listed :+
      DecisionOption.Button(RerollChoice.keep, RerollChoice.keepLabel),
      heading = Some(RerollChoice.heading(die))))

object RerollChoice:
  /** "Keep the roll": ends the choice. */
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep-roll")
  val keepLabel: String = "Keep the roll"
  private val Infix = ".reroll."

  /** `{pool}.reroll.{n}`, for the `n`th throw of `pool`. A Campaign pool's
    * key starts with `campaign.`, so its reroll decisions are Campaign
    * decisions. */
  def decisionId(pool: PoolKey, number: Int): String =
    s"${pool.value}$Infix$number"

  /** `{pool}.reroll.{n}.{name}`: a question a reroll power asks while it
    * rerolls the `n`th throw of `pool`, such as Master at Arms' "how
    * many". It names its throw, so its panel shows that pool's dice, as the
    * reroll decision's does. */
  def followUpId(pool: PoolKey, number: Int, name: String): String =
    s"${decisionId(pool, number)}.$name"

  /** The pool a reroll decision, or a question under one, is about; `None`
    * for any other decision. */
  def poolOf(decisionId: String): Option[PoolKey] =
    decisionId.lastIndexOf(Infix) match
      case -1 => None
      case at =>
        val rest = decisionId.drop(at + Infix.length)
        val number = rest.takeWhile(_.isDigit)
        val name = rest.drop(number.length)
        Option.when(number.toIntOption.exists(_ > 0) &&
          (name.isEmpty || (name.startsWith(".") && name.length > 1)))(
          PoolKey(decisionId.take(at)))

  /** A reroll decision, or a question a reroll power asks under one. */
  def isDecision(decisionId: String): Boolean = poolOf(decisionId).nonEmpty

  /** "Reroll the attack dice, or keep the roll". */
  def heading(die: DiceKind): String = s"Reroll the ${word(die)} dice, " +
    "or keep the roll"

  /** "attack" or "defense". */
  def word(die: DiceKind): String = die match
    case DiceKind.Attack => "attack"
    case DiceKind.Defense => "defense"

  /** The throw a reroll offer is asked about: the hooked node's, when it
    * is a reroll choice. */
  def throwOf(ctx: PowerCtx): Option[DiceThrow] = ctx.operation match
    case choice: RerollChoice => choice.throwAt(ctx.state)
    case _ => None

  /** The answer that picks `card`'s reroll. */
  def optionRef(card: DenizenId): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"reroll.${card.value}")

  private[dice] def refOf(offered: OfferedReroll): DecisionOptionRef.Button =
    optionRef(offered.offer.card)

  /** "[secret] Reroll all with Jinx": the offer's label after its cost's
    * glyphs, as a card prints a power's cost before its text. */
  def label(offer: RerollOffer): String =
    val cost = offer.payment.map(_.cost).getOrElse(Cost.free)
    val glyphs = Vector(cost.favor -> "[favor]",
      cost.favorBurnt -> "[favor-burnt]", cost.secret -> "[secret]",
      cost.secretBurnt -> "[secret-burnt]")
      .flatMap((count, glyph) => Vector.fill(count)(glyph))
    (glyphs :+ offer.label).mkString(" ")

  /** The rerolls picked on this throw so far, without "Keep the roll". */
  private[dice] def used(pending: PendingTree, decisionId: String)
      : Vector[DecisionOptionRef] = pending.answered.collect:
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
        if ref != keep => ref

  /** Whether the roller kept the roll. */
  private[dice] def kept(pending: PendingTree, decisionId: String): Boolean =
    pending.answered.reverse.collectFirst {
      case Answered(`decisionId`, answer, _) => answer
    }.contains(DecisionAnswer.ChooseOneAnswer(keep))

  /** The reroll picked last, unless the roller kept the roll. */
  private[dice] def picked(pending: PendingTree, decisionId: String)
      : Option[DecisionOptionRef] = pending.answered.reverse.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
  }.filter(_ != keep)

  /** The payment runs as a batch, not as the composite's own moves, so the
    * pipeline settles it at once when its payer is not the active player,
    * as a battle plan's does. */
  private[dice] def application(offered: OfferedReroll): Operation =
    Sequence(offered.offer.payment.toVector.map(pay =>
      BuildOps((_, _) => Right(Vector[CoreOperation](pay)))) ++
      offered.offer.steps)

  /** Why a reroll's dry run could not pay for it, as player text. The
    * pipeline may report only a code, so the shortfall is read off the
    * roller's board against the payment when it does. */
  private[dice] def shortfall(ready: ReadyGame, roller: PlayerId,
      offer: RerollOffer, violation: OathViolation): String =
    val cost = offer.payment.map(_.cost).getOrElse(Cost.free)
    violation match
      case OathViolation.InsufficientSecrets(required, available) =>
        needs(required, secrets(required), available)
      case OathViolation.InsufficientFavor(required, available) =>
        needs(required, "favor", available)
      case _ => ready.game.current.players.find(_.player == roller)
        .map(_.board).flatMap(board =>
          if board.faceUpSecrets < cost.secret then Some(needs(cost.secret,
            secrets(cost.secret), board.faceUpSecrets))
          else if board.favor < cost.favor then
            Some(needs(cost.favor, "favor", board.favor))
          else None)
        .getOrElse("Cannot be paid for now")

  private def secrets(count: Int): String =
    if count == 1 then "secret" else "secrets"

  private def needs(required: Int, what: String, available: Int): String =
    s"Needs $required $what; you have $available"
