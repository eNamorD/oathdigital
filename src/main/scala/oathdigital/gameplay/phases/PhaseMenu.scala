package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers}
import oathdigital.model._

/** A phase's menu (walker-driven game loop design, "The menus"): one
  * decision over everything the active player may do now in Wake or Rest.
  * Each option is the procedure its pick queues.
  *
  * {{{
  * Sequence(
  *   Decide(phase.menu, actor, ChooseOne(options), Panel),
  *   Branch(QueueProcedure(the picked option's entry)))
  * }}}
  *
  * The driver starts it when the queue is empty and more than the phase's
  * ending is available (ruling 3), and starts the picked procedure when this
  * walk ends. An option is available when its start would be accepted
  * (`QueuedStart`), and the walker refuses a pick of any other. The pick's
  * entry has no source, so its walk spends Supply as any start does.
  *
  * The tree runs nothing before its decision, so a resume rebuilds the same
  * question from the same state. The menu is not an action: its walk leaves
  * no boundary due, and the Game Log gives it no section and no line.
  *
  * Phase powers come from the phase power catalog, as Use Power and Forced
  * Wake read them (plan-time ruling 1 of slice 2): one option for each
  * accessible source of each power the player may choose in this phase.
  */
object PhaseMenu:
  val decisionId: String = "phase.menu"

  /** One phase's menu: the procedure that asks it, the procedure that ends
    * the phase, which is always its last option, and its words. */
  enum Menu(val phase: Phase, val procedure: TriggeredProcedureRef,
      val ending: PhaseTransitionRef,
      val endingOption: DecisionOptionRef.Button, val endingLabel: String,
      val heading: String):
    case Wake extends Menu(Phase.Wake, TriggeredProcedureRef.WakeMenu,
      PhaseTransitionRef.EndWake, DecisionOptionRef.Button("end-wake"),
      "End Wake", "Wake: take wealth, use a power or end Wake")
    case Rest extends Menu(Phase.Rest, TriggeredProcedureRef.RestMenu,
      PhaseTransitionRef.FinishRest, DecisionOptionRef.Button("finish-rest"),
      "Finish Rest", "Rest: use a power or finish Rest")

  /** The menu `phase` asks, when it has one. Act's comes in slice 4. */
  def of(phase: Phase): Option[Menu] = Menu.values.find(_.phase == phase)

  /** The menu `procedure` asks, when it is a menu. */
  def asking(procedure: ProcedureRef): Option[Menu] =
    Menu.values.find(_.procedure == procedure)

  /** The option that takes `resource` with Take Wealth. */
  def takeWealth(resource: WakeResource): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"take-wealth.${resource.key}")

  /** One option of a menu: the entry its pick queues, as the player sees
    * it. */
  final case class Offered(entry: QueuedProcedure, option: DecisionOption)

  /** The menu's options, in order: Take Wealth's two resources in Wake, each
    * phase power's source, then the ending. A source that offers two powers
    * of one phase is listed once, for the first. */
  def offers(menu: Menu, catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, phasePowers: PhasePowers, powers: WalkerPowers)
      : Vector[Offered] =
    def offered(procedure: StartableRef, args: Vector[DecisionOptionRef],
        shown: DecisionOption): Offered =
      val entry = QueuedProcedure(actor, procedure, args, None, QueueKind.Run)
      Offered(entry, QueuedStart.refusal(catalog, ready, entry, powers,
          phasePowers)
        .fold(shown)(violation => DecisionOption.Unavailable(shown,
          OptionReason.Written(reason(procedure, violation)))))
    val wealth: Vector[Offered] =
      if menu != Menu.Wake then Vector.empty
      else WakeResource.all.map(resource => offered(ActionRef.TakeWealth,
        TakeWealthProcedure.selection(resource),
        DecisionOption.Button(takeWealth(resource),
          s"Take Wealth: 1 ${resource.key}")))
    val used: Vector[Offered] = for
      power <- phasePowers.powers
      if !power.forced &&
        PhasePowerProcedure.timingOf(menu.phase).contains(power.timing)
      ref <- PhasePowerProcedure.sources(catalog, ready, actor, power)
        .map(_._2)
      shown <- DecisionOption.forRef(ref).toVector
    yield offered(ActionRef.UsePower(power.id), Vector(ref), shown)
    (wealth ++ used).distinctBy(_.option.ref) :+ offered(menu.ending,
      Vector.empty, DecisionOption.Button(menu.endingOption,
        menu.endingLabel))

  /** Whether the menu has nothing to ask: its ending is the only option
    * available (ruling 3). */
  def onlyEnding(menu: Menu, offers: Vector[Offered]): Boolean =
    offers.forall(offered => !offered.option.available ||
      offered.entry.procedure == menu.ending)

  def build(menu: Menu, phasePowers: PhasePowers, powers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    val phase = ready.game.current.turn.phase
    for
      _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
        s"the ${menu.phase.key} menu takes no start selection"))
      _ <- Either.cond(phase == menu.phase, (),
        OathViolation.WrongPhase(menu.phase, phase))
    yield
      val listed = offers(menu, catalog, ready, actor, phasePowers, powers)
      Sequence(Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          listed.map(_.option), heading = Some(menu.heading)),
          presentation = DecisionPresentation.Panel),
        Branch((_, pending) => listed.find(offered =>
          picked(pending, offered.option.ref)).map(offered =>
          QueueProcedure(offered.entry): Operation).toVector)))

  private def picked(pending: PendingTree, ref: DecisionOptionRef): Boolean =
    pending.answered.exists:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(answer), _) =>
        answer == ref
      case _ => false

  /** Why an option cannot start, in the player's words. */
  private def reason(procedure: StartableRef, violation: OathViolation)
      : String = (procedure, violation) match
    case (_, OathViolation.ResourceUnavailable(_, resource)) =>
      s"No ${resource.key} at your site"
    case (_, _: OathViolation.EnemyPawnBlocksTakeWealth) =>
      "Another player's pawn is at your site"
    case (ActionRef.TakeWealth, _: OathViolation.PowerAlreadyUsed) =>
      "Already taken from this site this turn"
    case (ActionRef.TakeWealth, _) => "Cannot take wealth now"
    case (_: ActionRef.UsePower, _: OathViolation.PowerAlreadyUsed) =>
      "Already used this turn"
    case (_: ActionRef.UsePower, _) => "Nothing to do now"
    case _ => "Cannot end the phase now"
