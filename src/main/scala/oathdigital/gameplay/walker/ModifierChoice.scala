package oathdigital.gameplay.walker

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.{ContributingPower, ModifierPicks,
  OfferReason, PowerCtx}
import oathdigital.model._

/** One pass of an action's modifier decision (modifier walker design): the
  * walk's first decision, a `Repeat` of these passes that ends on "Start".
  * The modifier counterpart of the battle-plan window's pass.
  *
  * A pass lists "Start" first, then every player-selected power that may be
  * picked at the window and whose card the actor may use, less those
  * already picked. Each is judged by its own `offerReason`, then by a dry
  * run of its payment, then by whether it applies now. A pick pays at once,
  * as the pass's second child, so the next pass is priced after it.
  *
  * "Start" is unavailable, with a reason, when no answer to the action's
  * first decision can be completed under the picks.
  *
  * The first pass asks only when a modifier can be picked. A later pass asks
  * while any modifier is still listed. A walk resuming inside a pass keeps
  * its two children whatever is listed.
  */
final class ModifierChoice(val actor: PlayerId, modifierWindow: PowerWindow,
    rest: Operation, startLabel: String,
    explain: Option[WalkerProcedureRegistry.OptionPricing])
    extends Operation:
  override val window: Option[PowerWindow] = Some(modifierWindow)
  override val children: Vector[Operation] = Vector.empty
  private val decisionId = ModifierPicks.decisionId(modifierWindow)

  def expand(pass: ModifierChoice.Pass): Vector[Operation] =
    val picked = ModifierPicks.picks(pass.powers.pool, pass.answered).toSet
    val listed = candidates(pass).filterNot(power => picked(power.id))
      .flatMap(option(pass, _))
    val asks = listed.exists(_.available) ||
      (picked.nonEmpty && listed.nonEmpty)
    if !asks && !pass.resuming then Vector.empty
    else Vector(
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        start(pass) +: listed, heading = Some(ModifierChoice.heading))),
      payment(pass.powers))

  private def candidates(pass: ModifierChoice.Pass)
      : Vector[ContributingPower] = pass.powers.pool.filter(power =>
    power.resolution == PowerResolution.PlayerSelected &&
      power.selectionWindows(modifierWindow) &&
      power.selectionCard.exists(card =>
        PowerAccess.locate(pass.state, actor, card).isDefined))
    .sortBy(ContributingPower.sortKey)

  private def option(pass: ModifierChoice.Pass, power: ContributingPower)
      : Option[DecisionOption] = for
    card <- power.selectionCard
    ref <- ModifierPicks.cardRef(card)
    shown <- DecisionOption.forRef(ref)
  yield
    val ctx = PowerCtx(pass.state, actor, power.source, modifierWindow,
      Vector.empty, this, None, pass.answered)
    power.offerReason(ctx) match
      case Some(OfferReason.Unavailable(reason)) => unavailable(shown, reason)
      case judged =>
        val priced = WalkerSimulation.applies(paying(power), pass.state,
            pass.powers.quiet) match
          case Left(_) => unavailable(shown, shortfall(pass.state,
            power.selectionPayments(pass.state, actor)))
          case Right(_) if !power.applicable(ctx) =>
            unavailable(shown, ModifierChoice.unusable)
          case Right(operations) => price(shown, operations)
        judged match
          case Some(OfferReason.Warned(reason)) if priced.available =>
            DecisionOption.Warned(priced, OptionReason.Written(reason))
          case _ => priced

  /** The pick's payment, onto its card, required. */
  private def paying(power: ContributingPower): Operation =
    BuildOps((ready, _) => Right(power.selectionPayments(ready, actor)),
      required = true)

  /** After the pass's answer: the payment of the modifier it picked. */
  private def payment(powers: WalkerPowers): Operation =
    Branch((_, tree) => tree.answered.reverseIterator.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) =>
        ref
    }.flatMap(ModifierPicks.powerFor(powers.pool, modifierWindow, _))
      .toVector.map(paying))

  private def price(shown: DecisionOption, operations: Vector[CoreOperation])
      : DecisionOption =
    val paid = operations.foldLeft(OptionPrice()):
      case (total, PayCost(_, _, cost, _, _, _)) => total.copy(
        favor = total.favor + cost.favor,
        secrets = total.secrets + cost.secret,
        favorBurnt = total.favorBurnt + cost.favorBurnt,
        secretsBurnt = total.secretsBurnt + cost.secretBurnt)
      case (total, _) => total
    if paid.isFree then shown else DecisionOption.Priced(shown, paid)

  /** Why `payments` cannot be made, read off the actor's board: the
    * pipeline's refusal carries only a code. */
  private def shortfall(state: ReadyGame, payments: Vector[CoreOperation])
      : String =
    val costs = payments.collect { case PayCost(_, _, cost, _, _, _) => cost }
    val favor = costs.map(cost => cost.favor + cost.favorBurnt).sum
    val secrets = costs.map(cost => cost.secret + cost.secretBurnt).sum
    state.game.current.players.find(_.player == actor).map(_.board)
      .flatMap(board =>
        if board.favor < favor then Some(needs(favor, "favor", board.favor))
        else if board.faceUpSecrets < secrets then Some(needs(secrets,
          if secrets == 1 then "secret" else "secrets", board.faceUpSecrets))
        else None)
      .getOrElse(ModifierChoice.unpayable)

  private def needs(required: Int, what: String, available: Int): String =
    s"Needs $required $what; you have $available"

  private def start(pass: ModifierChoice.Pass): DecisionOption =
    val button = DecisionOption.Button(ModifierPicks.start, startLabel)
    refusal(pass).fold[DecisionOption](button)(unavailable(button, _))

  /** Why no answer to the action's first decision completes under the
    * picks, or `None` when one does. */
  private def refusal(pass: ModifierChoice.Pass): Option[String] =
    val powers = pass.powers.quiet
    val previewed = WalkerSimulation.preview(rest, pass.state, powers)
    if previewed.exists(_.exists(_.outcome.isRight)) then None
    else Some(explained(pass.state, powers)
      .getOrElse(ModifierChoice.cannotStart))

  /** The first refused option of the action's first decision, as the
    * procedure prices it. */
  private def explained(state: ReadyGame, powers: WalkerPowers)
      : Option[String] = for
    pricing <- explain
    pending <- ProcedureWalker.advance(state, rest, None, powers).toOption
      .collect { case WalkerOutcome.Parked(at, _) => at }
    decide <- ProcedureWalker.parkedDecide(state, rest, pending, powers,
      probing = false)
    reason <- pricing(state, rest, pending, powers, decide).collectFirst {
      case DecisionOption.Unavailable(_, OptionReason.Written(text)) => text
    }
  yield reason

  private def unavailable(option: DecisionOption, reason: String)
      : DecisionOption =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

object ModifierChoice:
  /** What the walker tells a pass about this fold: the state, the answers
    * so far, whether the walk resumes inside the pass, and the powers it
    * walks with, picks included. */
  final case class Pass(state: ReadyGame, answered: Vector[Answered],
      resuming: Boolean, powers: WalkerPowers)

  val heading: String = "Choose modifiers, or start"
  val cannotStart: String = "Cannot start now"
  val unusable: String = "Cannot be used now"
  val unpayable: String = "Cannot be paid for now"

  /** The modifier decision for `window`, as the first node of an action's
    * tree: passes until "Start". `rest` is what the tree runs after it,
    * which "Start" previews; `explain` is the procedure's option pricing,
    * which words why "Start" is unavailable. */
  def loop(actor: PlayerId, window: PowerWindow, rest: Operation,
      startLabel: String,
      explain: Option[WalkerProcedureRegistry.OptionPricing] = None)
      : Operation =
    val id = ModifierPicks.decisionId(window)
    Repeat((_, pending) => !ModifierPicks.started(pending.answered, id),
      new ModifierChoice(actor, window, rest, startLabel, explain))
