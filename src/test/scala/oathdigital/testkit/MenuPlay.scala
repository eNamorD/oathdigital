package oathdigital.testkit

import oathdigital.application.{GameCommand, StartPayload, TreeDecision}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Picks from a phase menu as its player does (walker-driven game loop
  * design, "The menus"). */
object MenuPlay:
  /** The command that picks `option` from the parked menu as `player`. */
  def picking(player: PlayerId, option: DecisionOptionRef): GameCommand =
    GameCommand.ResolveWalker(player, TreeDecision(PhaseMenu.decisionId,
      DecisionAnswer.ChooseOneAnswer(option)))

  /** Picks `option` through the rules directly. */
  def picked(rules: OathRules, state: OathState, player: PlayerId,
      option: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, player, PhaseMenu.decisionId,
      DecisionAnswer.ChooseOneAnswer(option))

  /** The menu parked in `state`, when one is. */
  def parked(state: OathState): Option[PhaseMenu.Menu] = state match
    case Ready(ready) =>
      ready.game.current.walkerProcedure.flatMap(PhaseMenu.asking)
    case _ => None

  /** The command that ends `menu`'s phase for `player`: the ending's pick
    * when the menu is parked, or else a start of the ending, as on a table
    * no driver has run on. */
  def ending(state: OathState, menu: PhaseMenu.Menu, player: PlayerId)
      : GameCommand =
    if parked(state).contains(menu) then picking(player, menu.endingOption)
    else GameCommand.StartWalker(menu.ending, StartPayload(player))

  /** The parked menu's options, as the walker shows them. */
  def options(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers, phasePowers: PhasePowers)
      : Vector[DecisionOption] =
    ParkedNode.of(state, catalog, walkerPowers, phasePowers).toOption.flatten
      .collect { case ParkedNode.Decision(_, decide, _) => decide.query }
      .collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  /** The parked menu's options with the production power catalogs. */
  def options(state: OathState, catalog: ExecutableCatalog)
      : Vector[DecisionOption] = options(state, catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
