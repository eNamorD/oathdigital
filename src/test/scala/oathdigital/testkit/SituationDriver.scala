package oathdigital.testkit

import oathdigital.application.{DefenseDicePort, ExpectedStream, GameCommand,
  GameApplicationService, InMemoryEventStreamRepository, TreeDecision}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.DeltaMeaning.OperationApplied
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.serialization.GameEventWire

/** How a [[Situation]] is reached: one step becomes one transition, and every
  * park it causes is answered by the shared loop in [[run]]. The two
  * adapters differ only in how a step becomes a transition and which power
  * catalogs rebuild a park.
  */
sealed trait SituationDriver:
  def catalog: ExecutableCatalog
  /** The whole policy: what every park is answered with. */
  def answers: Answers
  def withAnswers(answers: Answers): SituationDriver

  /** One step, no park handling. */
  protected def apply(from: Situation, step: Step)
      : Either[String, Situation]
  protected def parkedNode(state: OathState)
      : Either[String, Option[ParkedNode]]

  /** Issues `steps` in order. After each step, every park it caused is
    * answered -- a Roll park by rolling the parked pool with the adapter's
    * dice, a decision by [[answers]] -- until nothing is parked; except that
    * with `settle = false` the last step's parks are left in place. Anything
    * that cannot go on fails the test naming the step and the decision.
    */
  final def run(from: Situation, steps: Seq[Step], settle: Boolean)
      : Situation =
    steps.zipWithIndex.foldLeft(from) { case (situation, (step, index)) =>
      def failed(detail: String): Nothing = munit.Assertions.fail(
        s"step $index ($step): $detail")
      val stepped = apply(situation, step)
        .fold(violation => failed(s"rejected: $violation"), identity)
      if !settle && index == steps.size - 1 then stepped
      else answerParks(stepped, failed)
    }

  private def answerParks(from: Situation, failed: String => Nothing)
      : Situation =
    var situation = from
    var answered = 0
    var parked = parkedNode(situation.state).fold(failed, identity)
    while parked.nonEmpty do
      if answered >= SituationDriver.parkLimit then failed(
        s"still parked after ${SituationDriver.parkLimit} answers, on " +
          parked.get.decisionId)
      val resume: GameCommand = parked.get match
        case ParkedNode.Roll(_, pool, _, _, awaiting) =>
          GameCommand.RollWalker(awaiting, pool)
        case ParkedNode.Decision(procedure, decide, awaiting) =>
          val park = Park(decide, situation.ready, awaiting, procedure)
          val answer = answers.applyOrElse(park, _ => failed(
            s"no answer for ${decide.decisionId} " +
              s"(${decide.query.getClass.getSimpleName}) of $procedure " +
              s"awaiting $awaiting"))
          GameCommand.ResolveWalker(awaiting,
            TreeDecision(decide.decisionId, answer))
      situation = apply(situation, Step.Command(resume)).fold(violation =>
        failed(s"answering ${parked.get.decisionId} awaiting " +
          s"${parked.get.awaiting} with $resume was rejected: $violation"),
        identity)
      answered += 1
      parked = parkedNode(situation.state).fold(failed, identity)
    situation

object SituationDriver:
  /** More parks than any real step causes: a walk still parked after this
    * many answers is looping on a default that never finishes it. */
  private val parkLimit = 500

  private def arranging(ops: Vector[CoreOperation], label: String)
      : WalkerStepRecorded =
    WalkerStepRecorded("0", DeltaRecorded(OperationApplied(label)), ops,
      Vector.empty)

  /** Calls `OathRules` directly, dispatching each command exactly as
    * `GameApplicationService.applyCommand` does. Search needs no draw port
    * here: the service only checks its port against `SearchRules.draw`. An
    * `Arrange` is applied through `OathRules.evolve`, the replay path.
    */
  final case class Rules(catalog: ExecutableCatalog, walkerPowers: WalkerPowers,
      phasePowers: PhasePowers, walkerDice: WalkerDice,
      defenseDice: DefenseDicePort, answers: Answers)
      extends SituationDriver:
    private val rules = new OathRules(catalog,
      walkerPowerCatalog = walkerPowers, phasePowerCatalog = phasePowers,
      walkerDice = walkerDice)

    def withAnswers(answers: Answers): SituationDriver =
      copy(answers = answers)

    protected def parkedNode(state: OathState)
        : Either[String, Option[ParkedNode]] =
      ParkedNode.of(state, catalog, walkerPowers, phasePowers)

    protected def apply(from: Situation, step: Step)
        : Either[String, Situation] = step match
      case Step.Command(command) => transition(from.state, command)
        .left.map(_.toString).map(result => advanced(from, result.state,
          result.events))
      case Step.Arrange(ops, label) =>
        val event = arranging(ops, label)
        rules.evolve(from.state, event).left.map(_.toString)
          .map(advanced(from, _, Vector(event)))

    private def advanced(from: Situation, state: OathState,
        events: Vector[OathEvent]): Situation =
      Situation(state, from.events ++ events,
        from.nextSequence + events.size, this)

    private def transition(state: OathState, command: GameCommand)
        : Either[OathViolation, OathTransition] = command match
      case GameCommand.WithModifiers(inner, _) => transition(state, inner)
      case GameCommand.Begin(chronicle, orders) =>
        rules.beginGame(state, chronicle, orders)
      case GameCommand.StartWalker(procedure, start) =>
        rules.startWalker(state, procedure, start.actor, start.modifiers,
          start.startArgs)
      case GameCommand.ResolveWalker(actor, decision) =>
        rules.resolveWalker(state, actor, decision.decisionId,
          decision.answer)
      case GameCommand.RollWalker(actor, pool) =>
        rules.rollWalkerPrepared(state, actor, pool) { count =>
          Either.cond(count == defenseDice.diceCount, defenseDice.rollTwo(),
            OathViolation.InvalidEventOrder(s"walker roll pool ${pool.value} " +
              s"requested $count dice but the defense dice port only rolls " +
              s"${defenseDice.diceCount}"))
        }
      case GameCommand.EndWake(player) =>
        rules.startWalker(state, PhaseTransitionRef.EndWake, player)
      case GameCommand.BeginRest(player) =>
        rules.startWalker(state, PhaseTransitionRef.BeginRest, player)
      case GameCommand.FinishRest(player) =>
        rules.startWalker(state, PhaseTransitionRef.FinishRest, player)
      case GameCommand.UsePower(player, power, source) =>
        rules.startWalker(state, ActionRef.UsePower(power), player,
          Vector.empty, Vector(source))
      case GameCommand.PeekSiteRelics(player) =>
        rules.handle(state, MinorActionCommand.PeekSiteRelics(player))
      case GameCommand.RevealOwnedRelic(player, relic) =>
        rules.handle(state, MinorActionCommand.RevealOwnedRelic(player, relic))
      case GameCommand.MoveWarbands(player, toSite, amount) =>
        rules.handle(state, MinorActionCommand.MoveWarbands(player, toSite,
          amount))

  /** Drives `service`, journaling into its `repository` as `gameId`. An
    * `Arrange` appends the arranging record at the stream's next sequence and
    * reloads, so the service replays it exactly as it would a real one. Parks
    * are rebuilt with the power catalogs the service's rules run.
    */
  final case class Journaled(service: GameApplicationService,
      catalog: ExecutableCatalog, repository: InMemoryEventStreamRepository,
      gameId: String, answers: Answers) extends SituationDriver:
    private val walkerPowers = WalkerPowerCatalog.default(catalog)
    private val phasePowers = PhasePowerCatalog.default(catalog)

    def withAnswers(answers: Answers): SituationDriver =
      copy(answers = answers)

    protected def parkedNode(state: OathState)
        : Either[String, Option[ParkedNode]] =
      ParkedNode.of(state, catalog, walkerPowers, phasePowers)

    protected def apply(from: Situation, step: Step)
        : Either[String, Situation] = step match
      case Step.Command(command) =>
        service.handle(gameId, from.nextSequence, command).left.map(_.toString)
          .map(accepted => Situation(accepted.state,
            from.events ++ accepted.events, accepted.nextSequence, this))
      case Step.Arrange(ops, label) =>
        val event = arranging(ops, label)
        for
          record <- GameEventWire.encodeEvent(gameId, catalog.ref,
            from.nextSequence, event).left.map(_.toString)
          _ <- repository.append(gameId,
            ExpectedStream.AtNextSequence(from.nextSequence),
            Vector(ujson.write(record))).left.map(_.toString)
          loaded <- service.load(gameId).left.map(_.toString)
            .flatMap(_.toRight(s"no stream $gameId after arranging"))
        yield Situation(loaded.state, from.events :+ event,
          loaded.nextSequence, this)
