package oathdigital.gameplay.powers.search

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{DecisionOptionProjection,
  DecisionQueryProjection}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** A parked Search decision's options as p1 is shown them: each option's
  * state, badge and reason, after the procedure's pricing and the powers'
  * warnings. */
object ShownOptions:
  /** The options of the choose-one `state` is parked on, as p1 sees
    * them. */
  def atParkedDecision(state: OathState): Vector[DecisionOptionProjection] =
    new GameProjector(catalog).project("search", LoadedGame(state, 1), p1)
      .walkerDecision.flatMap(_.query).collect {
        case one: DecisionQueryProjection.ChooseOne => one.options
      }.getOrElse(Vector.empty)

  /** The source decision's options once p1 has picked every one of
    * `modifiers` and started the Search. */
  def sourcesAfterPicking(ready: ReadyGame, modifiers: Vector[PowerId])(
      using munit.Location): Vector[DecisionOptionProjection] =
    atParkedDecision(accepted(ModifierPlay.started(SearchFixture.rules,
      Ready(ready), ActionRef.Search, p1,
      PowerWindow.SearchModifierSelection, modifiers)).state)

  /** The option among `shown` that names `ref`. */
  def optionFor(shown: Vector[DecisionOptionProjection],
      ref: DecisionOptionRef): Option[DecisionOptionProjection] =
    shown.find(option => option.kind == ref.kind && option.id == ref.wireId)
