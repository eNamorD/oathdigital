package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model._

/** Aggregate boundary between exact-window resolution and Rest-owned typed
  * handlers. It owns deterministic ordering and continuation, never a power-ID
  * switch or a concrete power effect.
  */
object RestPowerIntegration {
  def begin(catalog: ExecutableCatalog, state: OathState, restActor: PlayerId)
      : Either[OathViolation, OathTransition] = state match {
    case Ready(ready) => invocations(catalog, ready, restActor).flatMap(queue =>
      advance(catalog, state, ready, restActor, queue))
    case _ => Left(GameNotStarted)
  }

  def resolveLeagueTreaty(catalog: ExecutableCatalog, state: OathState,
      actor: PlayerId, decision: DecisionId,
      allocations: Vector[FavorAllocation], destinationBank: Suit)
      : Either[OathViolation, OathTransition] =
    pending(state, actor, decision).flatMap { case (ready, pending) => for {
      handler <- handlerFor(catalog, pending.current.powerId)
      treaty <- handler match {
        case value: LeagueTreatyPowerHandler => Right(value)
        case _ => Left(RestOutcomeMismatch(
          "pending Rest power does not accept favor allocations"))
      }
      event <- treaty.resolve(LeagueTreatyResolutionPreparation(catalog, ready,
        actor, pending, allocations, destinationBank))
      transition <- continueAfter(catalog, state, pending, event)
    } yield transition }

  def decline(catalog: ExecutableCatalog, state: OathState, actor: PlayerId,
      decision: DecisionId): Either[OathViolation, OathTransition] =
    pending(state, actor, decision).flatMap { case (ready, pending) => for {
      handler <- handlerFor(catalog, pending.current.powerId)
      event <- handler.decline(RestPowerDeclinePreparation(catalog, ready,
        actor, pending))
      transition <- continueAfter(catalog, state, pending, event)
    } yield transition }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: RestPowerEvent): Either[OathViolation, OathState] = for {
    _ <- event match {
      case started: RestPowerDecisionStarted =>
        validateStarted(catalog, state, started)
      case _: RestPowerDecisionCompleted => Right(())
    }
    prior = state match {
      case Ready(ready) => ready.game.current.pending.collect {
        case value: PendingProcedure.RestPowerDecision
            if value.decision == event.decision &&
              value.current.powerId == event.powerId => value
      }
      case _ => None
    }
    handler <- handlerFor(catalog, event.powerId)
    evolved <- handler.evolve(catalog, state, event)
  } yield prior.fold(evolved)(pending => retainContinuation(evolved, pending))

  private def continueAfter(catalog: ExecutableCatalog, state: OathState,
      pending: PendingProcedure.RestPowerDecision,
      event: RestPowerDecisionCompleted): Either[OathViolation, OathTransition] =
    for {
      after <- evolve(catalog, state, event)
      afterReady <- after match {
        case Ready(value) => Right(value)
        case _ => Left(GameNotStarted)
      }
      queue = afterReady.game.current.pending.collect {
        case value: PendingProcedure.RestPowerContinuation => value.remaining
      }.getOrElse(Vector.empty)
      next <- advance(catalog, after, afterReady, pending.restActor, queue)
    } yield next.copy(events = event +: next.events)

  private def advance(catalog: ExecutableCatalog, state: OathState,
      ready: ReadyGame, restActor: PlayerId,
      queue: Vector[RestPowerInvocationRef])
      : Either[OathViolation, OathTransition] =
    prepareNext(catalog, ready, restActor, queue).flatMap {
      case None => Right(OathTransition(clearContinuation(state), Vector.empty,
        AwaitingRestAction(restActor)))
      case Some(started) => evolve(catalog, state, started).map(nextState =>
        OathTransition(nextState, Vector(started),
          AwaitingRestPowerDecision(started.decisionOwner, started.decision)))
    }

  private def prepareNext(catalog: ExecutableCatalog, ready: ReadyGame,
      restActor: PlayerId, queue: Vector[RestPowerInvocationRef])
      : Either[OathViolation, Option[RestPowerDecisionStarted]] =
    if (queue.isEmpty) Right(None)
    else {
      val current = queue.head
      val remaining = queue.tail
      for {
      handler <- handlerFor(catalog, current.powerId)
      prepared <- handler.prepare(RestPowerPreparation(catalog, ready,
        restActor, current, remaining))
      next <- prepared match {
        case some @ Some(_) => Right(some)
        case None => prepareNext(catalog, ready, restActor, remaining)
      }
      } yield next
    }

  private def validateStarted(catalog: ExecutableCatalog, state: OathState,
      started: RestPowerDecisionStarted): Either[OathViolation, Unit] =
    state match {
      case Ready(ready) => for {
        queue <- ready.game.current.pending match {
          case Some(value: PendingProcedure.RestPowerContinuation)
              if value.restActor == started.restActor => Right(value.remaining)
          case None => invocations(catalog, ready, started.restActor)
          case _ => Left(InvalidEventOrder(
            "Rest power cannot replace this pending procedure"))
        }
        wanted <- prepareNext(catalog, ready, started.restActor, queue)
          .flatMap(_.toRight(InvalidEventOrder(
            "recorded Rest power is not applicable")))
        _ <- Either.cond(wanted == started, (),
          InvalidEventOrder("Rest power decision snapshot does not match"))
      } yield ()
      case _ => Left(GameNotStarted)
    }

  private def invocations(catalog: ExecutableCatalog, ready: ReadyGame,
      restActor: PlayerId): Either[OathViolation, Vector[RestPowerInvocationRef]] = {
    val indexed = RuleSourceIndex.enumerate(catalog, ready)
    val facts = RestPowerFacts(catalog, ready, restActor,
      indexed.map(value => value.source -> value).toMap)
    for {
      resolver <- ReviewedPowerCatalog.resolver(catalog)
      result <- resolver.resolve(PowerWindow.RestReturnFavor,
        indexed.map(value => value.source -> value.powerIds), facts)
        .left.map(error => UnsupportedRestState(error.toString))
      invocations <- result.offered.foldLeft[
        Either[OathViolation, Vector[RestPowerInvocationRef]]](Right(Vector.empty)) {
        case (found, invocation) => for {
          accumulated <- found
          source <- restSource(invocation.source)
        } yield invocation.inspection.decisionPlayer.fold(accumulated)(owner =>
          accumulated :+ RestPowerInvocationRef(invocation.powerId, source, owner))
      }
    } yield invocations
  }

  private def restSource(source: RuleSourceRef)
      : Either[OathViolation, RestPowerSourceRef] = source match {
    case RuleSourceRef.Site(id) => Right(RestPowerSourceRef.Site(id))
    case RuleSourceRef.SiteCard(site, id) =>
      Right(RestPowerSourceRef.SiteCard(site, id))
    case RuleSourceRef.Adviser(player, id) =>
      Right(RestPowerSourceRef.Adviser(player, id))
    case RuleSourceRef.Relic(player, id) =>
      Right(RestPowerSourceRef.Relic(player, id))
    case RuleSourceRef.SiteRelic(site, id) =>
      Right(RestPowerSourceRef.SiteRelic(site, id))
    case RuleSourceRef.Edifice(site, id) =>
      Right(RestPowerSourceRef.Edifice(site, id))
    case RuleSourceRef.Banner(id) => Banner.fromKey(id)
      .map(RestPowerSourceRef.Banner).toRight(
        UnsupportedRestState(s"unknown indexed banner $id"))
    case RuleSourceRef.Foundation(number) =>
      Right(RestPowerSourceRef.Foundation(number))
    case RuleSourceRef.Legacy(lineage, id) =>
      Right(RestPowerSourceRef.Legacy(lineage, id))
    case RuleSourceRef.GameRule(id) => Right(RestPowerSourceRef.GameRule(id))
  }

  private def handlerFor(catalog: ExecutableCatalog, powerId: PowerId)
      : Either[OathViolation, RestPowerHandler] = for {
    registry <- ReviewedPowerCatalog.registry(catalog)
    handler <- registry.handler(powerId, PowerWindow.RestReturnFavor).toRight(
      InvalidEventOrder(s"unknown Rest power ${powerId.value}"))
    executable <- handler match {
      case value: RestPowerHandler => Right(value)
      case _ => Left(InvalidEventOrder(
        s"Rest power ${powerId.value} has no executable handler"))
    }
  } yield executable

  private def pending(state: OathState, actor: PlayerId, decision: DecisionId)
      : Either[OathViolation, (ReadyGame, PendingProcedure.RestPowerDecision)] =
    state match {
      case Ready(ready) => ready.game.current.pending match {
        case Some(value: PendingProcedure.RestPowerDecision) =>
          if (value.decision != decision)
            Left(RestOutcomeMismatch("Rest power decision ID does not match"))
          else if (value.current.decisionOwner != actor)
            Left(WrongPlayer(value.current.decisionOwner, actor))
          else Right(ready -> value)
        case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
        case None => Left(RestOutcomeMismatch("no Rest power decision is pending"))
      }
      case _ => Left(GameNotStarted)
    }

  private def retainContinuation(state: OathState,
      pending: PendingProcedure.RestPowerDecision): OathState = state match {
    case Ready(ready) if pending.remaining.nonEmpty => Ready(ready.copy(game =
      ready.game.copy(current = ready.game.current.copy(pending = Some(
        PendingProcedure.RestPowerContinuation(pending.decision,
          pending.restActor, pending.remaining))))))
    case _ => state
  }

  private def clearContinuation(state: OathState): OathState = state match {
    case Ready(ready) if ready.game.current.pending.exists(
        _.isInstanceOf[PendingProcedure.RestPowerContinuation]) =>
      Ready(ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(pending = None))))
    case _ => state
  }
}
