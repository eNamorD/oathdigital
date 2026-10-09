package oathdigital.testkit

import oathdigital.gameplay.{ModifierPlay, OathRules}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Muster and Trade driven through the rules to the end: start, pick the
  * modifiers in the modifier decision and start the action, then answer the
  * source decision. Each fails with the violation or the state it got. */
object EconomyActions:
  /** A completed economy action and the game it left. `journal` carries every
    * event of the action, but its `state` is the started one, not the end's.
    */
  final case class Completed(journal: OathTransition, result: ReadyGame)

  /** `actor` Musters from `card`, picking `selected`. */
  def muster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(musterStarted(rules, ready, actor,
      selected)), actor, MusterProcedure.decisionId, card)

  /** `actor` Trades with `card` for `resource` ("favor" or "secret"),
    * picking `selected`. */
  def trade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(tradeStarted(rules, ready, actor,
      selected, resource)), actor, TradeProcedure.decisionId, card)

  private def complete(rules: OathRules, started: OathTransition,
      actor: PlayerId, decisionId: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    val done = Unwrap.accepted(rules.resolveWalker(started.state, actor,
      decisionId, DecisionAnswer.ChooseOneAnswer(card)))
    Completed(started.copy(events = started.events ++ done.events),
      Outcomes.ready(done.state))

  /** The Trade [[trade]] drives, except that a refusal at any step comes
    * back as the violation instead of failing the test. */
  def attemptTrade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- tradeStarted(rules, ready, actor, selected, resource)
      done <- rules.resolveWalker(started.state, actor,
        TradeProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done

  /** The Muster [[muster]] drives, except that a refusal at any step comes
    * back as the violation instead of failing the test. */
  def attemptMuster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- musterStarted(rules, ready, actor, selected)
      done <- rules.resolveWalker(started.state, actor,
        MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done

  /** `actor` starts a Muster and picks `selected`, in order, in its
    * modifier decision. */
  private def musterStarted(rules: OathRules, ready: ReadyGame,
      actor: PlayerId, selected: Vector[PowerId])
      : Either[OathViolation, OathTransition] =
    ModifierPlay.started(rules, Ready(ready), ActionRef.Muster, actor,
      PowerWindow.MusterModifierSelection, selected)

  /** `actor` starts a Trade for `resource` and picks `selected`, in order,
    * in its modifier decision. */
  private def tradeStarted(rules: OathRules, ready: ReadyGame,
      actor: PlayerId, selected: Vector[PowerId], resource: String)
      : Either[OathViolation, OathTransition] =
    ModifierPlay.started(rules, Ready(ready), ActionRef.Trade, actor,
      PowerWindow.TradeModifierSelection, selected,
      startArgs = Vector(DecisionOptionRef.Button(resource)))

  /** The decision a walk at `state` is parked on, rebuilt as the projector
    * rebuilds it, with the production powers. */
  def parkedDecision(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other =>
        munit.Assertions.fail(s"expected a parked decision, got $other")

  /** Each option's id and state, as the projector shows them, of the
    * decision `actor`'s start of `action` at `ready` parks on. For a Muster
    * or Trade with a modifier to pick, "start" comes first. */
  def optionStatesAtStart(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      action: ActionRef, startArgs: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Option[Vector[(String, String)]] =
    PhasePowerHarness.queryOf(Unwrap.accepted(rules.startWalker(Ready(ready),
      action, actor, startArgs = startArgs)), actor)
      .map(_.offeredOptions.map(option => option.id -> option.state))
