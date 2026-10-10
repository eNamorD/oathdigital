package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{Answers, ParkedNode}

/** A start that picks its modifiers through the walk's modifier decision,
  * as a player does (modifier walker design). */
object ModifierPlay:
  private val production: WalkerPowers = WalkerPowerCatalog.default(catalog)

  /** The answer that picks `power`: its card. */
  def pick(power: PowerId, powers: WalkerPowers = production)
      : DecisionAnswer =
    powers.powers.find(_.id == power).flatMap(_.selectionCard)
      .flatMap(ModifierPicks.cardRef)
      .fold(throw new IllegalArgumentException(
        s"${power.value} is not a player-selected power"))(
        DecisionAnswer.ChooseOneAnswer(_))

  /** The answer that ends the decision and starts the action. */
  val start: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ModifierPicks.start)

  /** `actor` starts `procedure` with `startArgs`, picks `modifiers` in
    * order and starts. With no modifier, "Start" is answered only when the
    * decision was asked. The events are every command's. */
  def started(rules: OathRules, state: OathState, procedure: StartableRef,
      actor: PlayerId, window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production,
      startArgs: Vector[DecisionOptionRef] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(state, procedure, actor, startArgs = startArgs)
      .flatMap(picking(rules, _, actor, window, modifiers, powers))

  /** The same, for a walk `begun` some other way: a taken follow-up offer,
    * say. */
  def picking(rules: OathRules, begun: OathTransition, actor: PlayerId,
      window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production)
      : Either[OathViolation, OathTransition] =
    val decisionId = ModifierPicks.decisionId(window)
    def answer(from: OathTransition, chosen: DecisionAnswer) =
      rules.resolveWalker(from.state, actor, decisionId, chosen)
        .map(next => next.copy(events = from.events ++ next.events))
    for
      picked <- modifiers.foldLeft[Either[OathViolation, OathTransition]](
        Right(begun))((done, power) => done.flatMap(answer(_,
          pick(power, powers))))
      ended <-
        if asking(picked.state, decisionId, powers) then answer(picked, start)
        else Right(picked)
    yield ended

  /** A `Situation`'s answers at `window`'s decision: each of `modifiers`
    * not yet picked, in order, then "Start". */
  def answers(window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production): Answers =
    case park if park.decisionId == ModifierPicks.decisionId(window) =>
      val answered = park.ready.game.current.walkerPending
        .fold(Vector.empty[Answered])(_.answered)
      val done = ModifierPicks.picks(powers.powers, answered)
      modifiers.find(!done.contains(_)).fold(start)(pick(_, powers))

  private def asking(state: OathState, decisionId: String,
      powers: WalkerPowers): Boolean =
    ParkedNode.of(state, catalog, powers).toOption.flatten
      .exists(_.decisionId == decisionId)
