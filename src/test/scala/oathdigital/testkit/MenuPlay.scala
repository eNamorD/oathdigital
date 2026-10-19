package oathdigital.testkit

import oathdigital.application.{GameCommand, TreeDecision}
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

  /** The step that starts `procedure` as `player`: see `Step.Start`. */
  def start(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef] = Vector.empty): Step =
    Step.Start(player, procedure, args)

  /** The command that picks `procedure`'s option from the parked menu. */
  def starting(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): GameCommand =
    picking(player, optionOf(procedure, args))

  /** Starts `procedure` through the rules: its pick when a menu is parked,
    * a start on a table where none is. */
  def started(rules: OathRules, state: OathState, player: PlayerId,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Either[OathViolation, OathTransition] =
    if parked(state).nonEmpty then
      picked(rules, state, player, optionOf(procedure, args))
    else rules.startWalker(state, procedure, player, args)

  private def optionOf(procedure: StartableRef,
      args: Vector[DecisionOptionRef])(using munit.Location)
      : DecisionOptionRef =
    PhaseMenu.optionFor(procedure, args).getOrElse(munit.Assertions.fail(
      s"no menu lists ${procedure.key}"))

  /** The walk pending in `ready`, apart from a phase menu: the driver parks
    * the phase's menu whenever nothing is queued, so a walk that has ended
    * in Act leaves the Act menu pending. */
  def pendingBeyondMenu(ready: ReadyGame): Option[PendingTree] =
    pendingBeyondMenu(ready.game.current)

  def pendingBeyondMenu(current: CurrentGameState): Option[PendingTree] =
    current.walkerPending.filter(_ => procedureBeyondMenu(current).nonEmpty)

  /** The procedure being walked, apart from a phase menu. */
  def procedureBeyondMenu(ready: ReadyGame): Option[ProcedureRef] =
    procedureBeyondMenu(ready.game.current)

  def procedureBeyondMenu(current: CurrentGameState): Option[ProcedureRef] =
    current.walkerProcedure.filter(PhaseMenu.asking(_).isEmpty)

  /** `ready` with no phase menu parked, as a hand-built table holds it: for
    * a rules-level suite that starts a procedure directly, to test the
    * procedure's own refusals rather than the menu's. */
  def withoutMenu(state: OathState): OathState = state match
    case Ready(ready) => Ready(withoutMenu(ready))
    case other => other

  def withoutMenu(ready: ReadyGame): ReadyGame =
    if ready.game.current.walkerProcedure.exists(PhaseMenu.asking(_).nonEmpty)
    then ready.updateCurrent(_.copy(walkerProcedure = None,
      walkerPending = None))
    else ready

  /** The menu parked in `state`, when one is. */
  def parked(state: OathState): Option[PhaseMenu.Menu] = state match
    case Ready(ready) =>
      ready.game.current.walkerProcedure.flatMap(PhaseMenu.asking)
    case _ => None

  /** The step that ends `menu`'s phase for `player`: see `Step.Start`. */
  def ending(menu: PhaseMenu.Menu, player: PlayerId): Step =
    Step.Start(player, menu.ending)

  /** The parked menu's options, as the walker shows them. */
  def bandedOptions(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers, phasePowers: PhasePowers)
      : Vector[DecisionOption] =
    ParkedNode.of(state, catalog, walkerPowers, phasePowers).toOption.flatten
      .collect { case ParkedNode.Decision(_, decide, _) => decide.query }
      .collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  /** The parked menu's options as the panel receives them, each wrapped in
    * the band it is read in, with the production power catalogs. */
  def bandedOptions(state: OathState, catalog: ExecutableCatalog)
      : Vector[DecisionOption] = bandedOptions(state, catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** The parked menu's options with the band peeled off, which is what an
    * assertion about what one option shows wants. Use `bandedOptions` to
    * assert the band itself. */
  def options(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers, phasePowers: PhasePowers)
      : Vector[DecisionOption] =
    bandedOptions(state, catalog, walkerPowers, phasePowers).map(withoutBand)

  /** The parked menu's options with the production power catalogs. */
  def options(state: OathState, catalog: ExecutableCatalog)
      : Vector[DecisionOption] = options(state, catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** `option` without the band `PhaseMenu` wrapped it in. */
  def withoutBand(option: DecisionOption): DecisionOption = option match
    case DecisionOption.Grouped(inner, _) => inner
    case ungrouped => ungrouped

  /** The option the phase menu shows for starting `procedure` with `args`,
    * on `ready` with its phase's menu parked for the active player, as the
    * driver parks it. A power whose source carries two powers of the phase
    * is found by its own button (slice 4, plan-time ruling 12). */
  def menuOptionFor(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): DecisionOption =
    val ref = optionOf(procedure, args)
    val listed = options(Ready(Table.from(ready).phaseMenuParked.ready),
      catalog)
    val button = procedure match
      case ActionRef.UsePower(id) => Some(PhaseMenu.powerButton(id))
      case _ => None
    listed.find(_.ref == ref)
      .orElse(button.flatMap(power => listed.find(_.ref == power)))
      .getOrElse(munit.Assertions.fail(s"the menu does not list $ref"))

  /** Whether the phase menu on `ready` offers `procedure` available. */
  def availableInMenu(catalog: ExecutableCatalog, ready: ReadyGame,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Boolean =
    menuOptionFor(catalog, ready, procedure, args).available
