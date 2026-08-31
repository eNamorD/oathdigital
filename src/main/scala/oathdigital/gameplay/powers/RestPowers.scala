package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Reviewed Rest classifications plus ordered, procedure-owned Rest hooks. */
object RestPowers {
  private val leagueTreatyId = PowerId("denizen.league-treaty")
  private def rest = Vector(ReviewedHandler.automatic(PowerWindow.RestStart))

  object Naysayers extends ReviewedPower("denizen.naysayers", None, rest)
  object SilverTongue extends ReviewedPower("denizen.silver-tongue", None, rest)
  object Insomnia extends ReviewedPower("denizen.insomnia", None, rest)
  object VowOfObedience extends ReviewedPower("denizen.vow-of-obedience", None, rest)
  object VowOfPoverty extends ReviewedPower("denizen.vow-of-poverty", None, Vector(
    ReviewedHandler.selected(PowerWindow.MusterModifierSelection),
    ReviewedHandler.selected(PowerWindow.TradeModifierSelection),
    ReviewedHandler.automatic(PowerWindow.RestStart)))

  final case class RestPowerFacts(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      restActor: PlayerId,
      sources: Map[RuleSourceRef, IndexedRuleSource]
  ) extends PowerFacts

  private object LeagueTreatyHandler extends PowerHandler {
    val window = PowerWindow.RestReturnFavor
    val resolution = PowerResolution.PlayerSelected
    val implemented = true

    def inspect(context: PowerContext): PowerInspection = context.facts match {
      case facts: RestPowerFacts => context.source match {
        case source @ RuleSourceRef.SiteCard(siteId, _: DenizenId)
            if facts.sources.get(source).exists(indexed =>
              indexed.powerIds.contains(leagueTreatyId) &&
                indexed.face == RuleSourceFace.FaceUp) =>
          val owner = (for {
            site <- facts.ready.game.current.map.sites.get(siteId)
            ruler <- SiteRule.ruler(site.forces,
              facts.ready.game.current.players).toOption
          } yield ruler)
            .collect { case SiteRuler.Player(playerId) => playerId }
          PowerInspection(owner.nonEmpty && eligibleSources(facts.ready, siteId).nonEmpty,
            owner, owner)
        case _ => PowerInspection(applicable = false)
      }
      case _ => PowerInspection(applicable = false)
    }
  }

  object LeagueTreaty extends Power {
    val id = leagueTreatyId
    val modifier = None
    val handlers: Vector[PowerHandler] = Vector(LeagueTreatyHandler)
  }

  val powers: Vector[Power] = Vector(Naysayers, SilverTongue, Insomnia,
    VowOfObedience, VowOfPoverty, LeagueTreaty)

  def begin(catalog: ExecutableCatalog, state: OathState, restActor: PlayerId)
      : Either[OathViolation, OathTransition] = state match {
    case Ready(ready) =>
      invocations(catalog, ready, restActor).flatMap { queue =>
        nextEvent(ready, restActor, queue).flatMap {
          case None => Right(OathTransition(state, Vector.empty,
            AwaitingRestAction(restActor)))
          case Some(event) => evolve(catalog, state, event).map(nextState =>
            OathTransition(nextState, Vector(event),
              AwaitingRestPowerDecision(event.decisionOwner, event.decision)))
        }
      }
    case _ => Left(GameNotStarted)
  }

  def resolve(catalog: ExecutableCatalog, state: OathState, actor: PlayerId,
      decision: DecisionId, allocations: Vector[FavorAllocation],
      destinationBank: Suit): Either[OathViolation, OathTransition] =
    pending(state, actor, decision).flatMap { case (ready, pending) =>
      val event = LeagueTreatyResolved(pending.restActor, decision,
        pending.current.powerId, pending.current.source, actor, allocations,
        destinationBank)
      continueAfter(catalog, state, ready, pending, event)
    }

  def decline(catalog: ExecutableCatalog, state: OathState, actor: PlayerId,
      decision: DecisionId): Either[OathViolation, OathTransition] =
    pending(state, actor, decision).flatMap { case (ready, pending) =>
      val event = LeagueTreatyDeclined(pending.restActor, decision,
        pending.current.powerId, pending.current.source, actor)
      continueAfter(catalog, state, ready, pending, event)
    }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: RestPowerEvent)
      : Either[OathViolation, OathState] = event match {
    case started: LeagueTreatyDecisionStarted => state match {
      case Ready(ready) => for {
        queue <- ready.game.current.pending match {
          case Some(value: PendingProcedure.RestPowerContinuation)
              if value.restActor == started.restActor => Right(value.remaining)
          case None => invocations(catalog, ready, started.restActor)
          case _ => Left(InvalidEventOrder(
            "League Treaty cannot replace this pending procedure"))
        }
        wantedOption <- nextEvent(ready, started.restActor, queue)
        wanted <- wantedOption.toRight(
          InvalidEventOrder("League Treaty is not applicable"))
        _ <- Either.cond(wanted == started, (),
          InvalidEventOrder("League Treaty decision snapshot does not match"))
      } yield Ready(ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(pending = Some(PendingProcedure.RestPowerDecision(
          started.decision, started.restActor,
          RestPowerInvocationRef(started.powerId, started.source,
            started.decisionOwner), started.remaining,
          started.eligibleSources, started.legalBanks))))))
      case _ => Left(GameNotStarted)
    }
    case resolved: LeagueTreatyResolved => state match {
      case Ready(ready) => for {
        pending <- matchingPending(ready, resolved.restActor, resolved.decision,
          resolved.powerId, resolved.source, resolved.decisionOwner)
        snapshot <- snapshotFor(ready, pending.current)
        _ <- validateAllocations(ready, snapshot, resolved.allocations,
          resolved.destinationBank)
        moved = moveFavor(ready, resolved.allocations, resolved.destinationBank)
        continued <- retainContinuation(moved, pending)
      } yield Ready(continued)
      case _ => Left(GameNotStarted)
    }
    case declined: LeagueTreatyDeclined => state match {
      case Ready(ready) => for {
        pending <- matchingPending(ready, declined.restActor,
        declined.decision, declined.powerId, declined.source,
          declined.decisionOwner)
        continued <- retainContinuation(clearPending(ready), pending)
      } yield Ready(continued)
      case _ => Left(GameNotStarted)
    }
  }

  private def continueAfter(catalog: ExecutableCatalog, state: OathState,
      ready: ReadyGame, pending: PendingProcedure.RestPowerDecision,
      event: RestPowerEvent): Either[OathViolation, OathTransition] = for {
    after <- evolve(catalog, state, event)
    afterReady <- after match {
      case Ready(value) => Right(value)
      case _ => Left(GameNotStarted)
    }
    queue = afterReady.game.current.pending.collect {
      case value: PendingProcedure.RestPowerContinuation => value.remaining
    }.getOrElse(Vector.empty)
    next <- nextEvent(afterReady, pending.restActor, queue)
    transition <- next match {
      case None => Right(OathTransition(after, Vector(event),
        AwaitingRestAction(pending.restActor)))
      case Some(started) => evolve(catalog, after, started).map(nextState =>
        OathTransition(nextState, Vector(event, started),
          AwaitingRestPowerDecision(started.decisionOwner, started.decision)))
    }
  } yield transition

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
    } yield {
      result.offered.flatMap { invocation => invocation.source match {
        case RuleSourceRef.SiteCard(siteId, denizenId: DenizenId) =>
          invocation.inspection.decisionPlayer.map(owner => RestPowerInvocationRef(
            invocation.powerId, SiteDenizenTarget(siteId, denizenId), owner))
        case _ => None
      }}
    }
  }

  private def nextEvent(ready: ReadyGame, restActor: PlayerId,
      queue: Vector[RestPowerInvocationRef])
      : Either[OathViolation, Option[LeagueTreatyDecisionStarted]] =
    if (queue.isEmpty) Right(None)
    else {
      val current = queue.head
      val remaining = queue.tail
      snapshotFor(ready, current) match {
      case Right(snapshot) => Right(Some(LeagueTreatyDecisionStarted(restActor,
        decisionId(ready, restActor, current), current.powerId, current.source,
        current.decisionOwner, remaining, snapshot._1, snapshot._2)))
      case Left(_) => nextEvent(ready, restActor, remaining)
    }
    }

  private def snapshotFor(ready: ReadyGame, invocation: RestPowerInvocationRef)
      : Either[OathViolation, (Vector[SiteFavorSource], Vector[Suit])] = {
    val current = ready.game.current
    for {
      site <- current.map.sites.get(invocation.source.siteId)
        .toRight(UnsupportedRestState("League Treaty source site is missing"))
      _ <- site.denizens.collectFirst {
        case DenizenState(id, Orientation.FaceUp, _)
            if id == invocation.source.denizenId => ()
      }.toRight(UnsupportedRestState("League Treaty source is not faceup"))
      owner <- SiteRule.ruler(site.forces, current.players)
        .left.map(error => UnsupportedRestState(error.toString)).flatMap {
          case SiteRuler.Player(playerId) => Right(playerId)
          case _ => Left(UnsupportedRestState("League Treaty has no player ruler"))
        }
      _ <- Either.cond(owner == invocation.decisionOwner, (),
        UnsupportedRestState("League Treaty ruler changed"))
      sources = eligibleSources(ready, invocation.source.siteId)
      _ <- Either.cond(sources.nonEmpty, (),
        UnsupportedRestState("League Treaty has no eligible favor"))
    } yield sources -> Suit.all
  }

  private def eligibleSources(ready: ReadyGame, treatySite: SiteId)
      : Vector[SiteFavorSource] = ready.game.current.map.regionOf(treatySite)
    .toVector.flatMap { region =>
      val map = ready.game.current.map
      map.inPlay.filter(siteId => map.regionOf(siteId).contains(region)).flatMap {
        siteId =>
          val site = map.sites(siteId)
          val denizens = site.denizens.collect {
            case DenizenState(id, Orientation.FaceUp, tokens) if tokens.favor > 0 =>
              SiteFavorSource.Denizen(siteId, id)
            case EdificeState(id, _, tokens) if tokens.favor > 0 =>
              SiteFavorSource.Edifice(siteId, id)
          }
          val relics = site.relics.zipWithIndex.collect {
            case (RelicState(_, _, tokens), slot) if tokens.favor > 0 =>
              SiteFavorSource.Relic(siteId, slot)
          }
          denizens ++ relics
      }
    }

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

  private def matchingPending(ready: ReadyGame, restActor: PlayerId,
      decision: DecisionId, powerId: PowerId, source: SiteDenizenTarget,
      owner: PlayerId): Either[OathViolation, PendingProcedure.RestPowerDecision] =
    ready.game.current.pending match {
      case Some(value: PendingProcedure.RestPowerDecision) if
          value.decision == decision && value.restActor == restActor &&
          value.current == RestPowerInvocationRef(powerId, source, owner) => Right(value)
      case _ => Left(InvalidEventOrder("League Treaty pending decision does not match"))
    }

  private def validateAllocations(ready: ReadyGame,
      snapshot: (Vector[SiteFavorSource], Vector[Suit]),
      allocations: Vector[FavorAllocation], destination: Suit)
      : Either[OathViolation, Unit] = {
    val (eligible, banks) = snapshot
    val keys = allocations.map(_.source.stableKey)
    val available = eligible.map(source => source.stableKey -> source).toMap
    if (allocations.isEmpty)
      Left(RestOutcomeMismatch("League Treaty must move positive favor or decline"))
    else if (keys.distinct.size != keys.size)
      Left(RestOutcomeMismatch("League Treaty sources must be distinct"))
    else if (!banks.contains(destination))
      Left(RestOutcomeMismatch("League Treaty destination bank is not legal"))
    else allocations.find { allocation => available.get(allocation.source.stableKey)
      .forall(source => source != allocation.source ||
        favorOn(ready, source) < allocation.amount) } match {
      case Some(_) => Left(RestOutcomeMismatch(
        "League Treaty allocation exceeds eligible favor"))
      case None => Right(())
    }
  }

  private def favorOn(ready: ReadyGame, source: SiteFavorSource): Int = {
    val site = ready.game.current.map.sites(source.siteId)
    source match {
      case SiteFavorSource.Denizen(_, id) => site.denizens.collectFirst {
        case DenizenState(`id`, _, tokens) => tokens.favor }.getOrElse(0)
      case SiteFavorSource.Edifice(_, id) => site.denizens.collectFirst {
        case EdificeState(`id`, _, tokens) => tokens.favor }.getOrElse(0)
      case SiteFavorSource.Relic(_, slot) => site.relics.lift(slot)
        .map(_.tokens.favor).getOrElse(0)
    }
  }

  private def moveFavor(ready: ReadyGame, allocations: Vector[FavorAllocation],
      destination: Suit): ReadyGame = {
    val bySource = allocations.map(value => value.source -> value.amount).toMap
    val sites = ready.game.current.map.sites.map { case (siteId, site) =>
      val denizens = site.denizens.map {
        case card @ DenizenState(id, _, tokens) => bySource
          .get(SiteFavorSource.Denizen(siteId, id)).fold(card)(amount =>
            card.copy(tokens = tokens.copy(favor = tokens.favor - amount)))
        case card @ EdificeState(id, _, tokens) => bySource
          .get(SiteFavorSource.Edifice(siteId, id)).fold(card)(amount =>
            card.copy(tokens = tokens.copy(favor = tokens.favor - amount)))
      }
      val relics = site.relics.zipWithIndex.map { case (card, slot) => bySource
        .get(SiteFavorSource.Relic(siteId, slot)).fold(card)(amount =>
          card.copy(tokens = card.tokens.copy(favor = card.tokens.favor - amount))) }
      siteId -> site.copy(denizens = denizens, relics = relics)
    }
    val moved = allocations.map(_.amount).sum
    ready.copy(support = ready.support.copy(favorBanks =
      ready.support.favorBanks.updated(destination,
        ready.support.favorBanks.getOrElse(destination, 0) + moved)),
      game = ready.game.copy(current = ready.game.current.copy(
        map = ready.game.current.map.copy(sites = sites), pending = None)))
  }

  private def clearPending(ready: ReadyGame): ReadyGame = ready.copy(game =
    ready.game.copy(current = ready.game.current.copy(pending = None)))

  private def retainContinuation(ready: ReadyGame,
      pending: PendingProcedure.RestPowerDecision): Either[OathViolation, ReadyGame] =
    nextEvent(ready, pending.restActor, pending.remaining).map {
      case None => clearPending(ready)
      case Some(_) => ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(pending = Some(
          PendingProcedure.RestPowerContinuation(pending.decision,
            pending.restActor, pending.remaining)))))
    }

  private def decisionId(ready: ReadyGame, restActor: PlayerId,
      invocation: RestPowerInvocationRef): DecisionId = DecisionId(
    s"rest-${ready.game.current.tracks.round}-${restActor.value}-" +
      s"${invocation.powerId.value}-${invocation.source.siteId.value}-" +
      invocation.source.denizenId.value)
}
