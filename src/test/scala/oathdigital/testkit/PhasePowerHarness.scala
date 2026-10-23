package oathdigital.testkit

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.powers.{DeclaredNotes, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.protocol.projection.{DecisionQueryProjection, PlayerBoardProjection}

/** Drives a phase power through the rules for a rule test: the power suites
  * of every phase, and the projection suites that read what a parked power
  * offers. `rules` carries every production phase power and the printed
  * operation restrictions, with no walker powers. The acting player is the
  * quiet table's p1.
  */
object PhasePowerHarness:
  private val actor: PlayerId = Table.p1

  val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector.empty,
      restrictionSet = OperationRestrictions.forCatalog(catalog),
      declared = Some(DeclaredNotes.of(catalog))),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val projector = new GameProjector(catalog)

  /** The parked decision, rebuilt from state with the same catalog and
    * phase power catalog `rules` was built with.
    */
  val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** Every player but the actor, in turn order. */
  def others(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.map(_.player).filter(_ != actor)

  def use(ready: ReadyGame, power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.startWalker(
    OathState.Ready(MenuPlay.withoutMenu(ready)), ActionRef.UsePower(power.id),
    actor, Vector(source))

  def answer(from: OathTransition, by: PlayerId, decision: String,
      answered: DecisionAnswer): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, by, decision, answered)

  /** `from`, parked on `decision`'s Continue screen, continued by the actor
    * pressing Continue. */
  def continuedPast(from: OathTransition, decision: String)
      : Either[OathViolation, OathTransition] =
    answer(from, actor, decision, pick(Decide.Pass))

  /** The game after `transition`; fails naming the state when it is not
    * Ready. */
  def after(transition: OathTransition)(using munit.Location): ReadyGame =
    Outcomes.ready(transition.state)

  /** The walk is parked at `decision`, waiting on the actor. */
  def awaits(transition: OathTransition, decision: String): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == decision && facts.awaiting == actor)

  /** The walk parked at no decision and the actor is acting again. */
  def backToActing(transition: OathTransition)(using munit.Location): Boolean =
    val turn = after(transition).game.current.turn
    parked.parkedDecision(transition.state).forall(facts =>
      facts.procedure == PhaseMenu.Menu.Act.procedure) &&
      turn.phase == Phase.Act && turn.activePlayer == actor

  def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  /** The state a journal replay of `events` reaches from `from`, or the
    * violation that stopped it. */
  def replayed(from: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  /** The operations the walk recorded, in order. */
  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    RecordedOps.all(events)

  /** A phase power the actor's phase menu offers available, and the source
    * its option names. */
  final case class MenuPower(power: PhasePower, ref: DecisionOptionRef)

  /** The phase powers the actor's phase menu offers available now, under
    * the printed operation restrictions. */
  def usableNow(ready: ReadyGame): Vector[MenuPower] =
    menuPowers(catalog, ready, actor, PhasePowerCatalog.default(catalog),
      WalkerPowers(Vector.empty,
        restrictionSet = OperationRestrictions.forCatalog(catalog)))

  /** The phase powers `player`'s phase menu offers available on `ready`
    * under `phasePowers` and `powers`, in menu order. */
  def menuPowers(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, phasePowers: PhasePowers, powers: WalkerPowers)
      : Vector[MenuPower] =
    PhaseMenu.of(ready.game.current.turn.phase).toVector.flatMap(menu =>
      PhaseMenu.offers(menu, catalog, ready, player, phasePowers, powers)
        .filter(_.option.available)
        .flatMap(offered => offered.entry.procedure match
          case ActionRef.UsePower(id) =>
            for
              power <- phasePowers.find(id).toVector
              ref <- offered.entry.startArgs.headOption.toVector
            yield MenuPower(power, ref)
          case _ => Vector.empty))

  /** The parked decision as `viewer` is offered it. */
  def queryOf(transition: OathTransition, viewer: PlayerId)
      : Option[DecisionQueryProjection] = projector.project("targets",
    LoadedGame(transition.state, 30L), viewer).walkerDecision.flatMap(_.query)

  /** The `(kind, id)` of every option of the parked decision. */
  def offered(transition: OathTransition, viewer: PlayerId)
      : Option[Vector[(String, String)]] =
    queryOf(transition, viewer).map(_.offeredOptions.map(o => o.kind -> o.id))

  def boardOf(transition: OathTransition, viewer: PlayerId, owner: PlayerId)(
      using munit.Location): PlayerBoardProjection =
    Unwrap.present(projector.project("targets",
      LoadedGame(transition.state, 30L), viewer).playerBoards
      .find(_.playerId == owner.value), s"a board for $owner")

  def publicBoardOf(transition: OathTransition, owner: PlayerId)(
      using munit.Location): PlayerBoardProjection =
    Unwrap.present(projector.projectPublic("targets",
      LoadedGame(transition.state, 30L)).playerBoards
      .find(_.playerId == owner.value), s"a public board for $owner")

  /** A power used and then its one decision answered: the walk after each
    * step, and the events of both in order. */
  final case class UsedAndAnswered(started: OathTransition,
      answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  /** The actor uses `power` from `source` and answers the decision it parks
    * on by choosing `ref`. Fails with the violation that stopped either
    * step. */
  def useAndAnswer(ready: ReadyGame, power: PhasePower,
      source: DecisionOptionRef, decision: String, ref: DecisionOptionRef)(
      using munit.Location): UsedAndAnswered =
    val started = Unwrap.accepted(use(ready, power, source))
    UsedAndAnswered(started,
      Unwrap.accepted(answer(started, actor, decision, pick(ref))))
