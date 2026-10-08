package oathdigital.testkit

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Muster and Trade driven through the rules to the end: start, then answer
  * the source decision. Each fails with the violation or the state it got. */
object EconomyActions:
  /** A completed economy action and the game it left. `journal` carries every
    * event of the action, but its `state` is the started one, not the end's.
    */
  final case class Completed(journal: OathTransition, result: ReadyGame)

  /** `actor` Musters from `card` with `selected` powers. */
  def muster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(rules.startWalker(Ready(ready),
      ActionRef.Muster, actor, selected)), actor, MusterProcedure.decisionId,
      card)

  /** `actor` Trades with `card` for `resource` ("favor" or "secret"). */
  def trade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(rules.startWalker(Ready(ready),
      ActionRef.Trade, actor, selected,
      Vector(DecisionOptionRef.Button(resource)))), actor,
      TradeProcedure.decisionId, card)

  private def complete(rules: OathRules, started: OathTransition,
      actor: PlayerId, decisionId: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    val done = Unwrap.accepted(rules.resolveWalker(started.state, actor,
      decisionId, DecisionAnswer.ChooseOneAnswer(card)))
    Completed(started.copy(events = started.events ++ done.events),
      Outcomes.ready(done.state))

  /** The Trade [[trade]] drives, except that a refusal at either step comes
    * back as the violation instead of failing the test. */
  def attemptTrade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- rules.startWalker(Ready(ready), ActionRef.Trade, actor,
        selected, Vector(DecisionOptionRef.Button(resource)))
      done <- rules.resolveWalker(started.state, actor,
        TradeProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done

  /** The Muster [[muster]] drives, except that a refusal at either step comes
    * back as the violation instead of failing the test. */
  def attemptMuster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- rules.startWalker(Ready(ready), ActionRef.Muster, actor,
        selected)
      done <- rules.resolveWalker(started.state, actor,
        MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done
