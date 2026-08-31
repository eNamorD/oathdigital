package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** League Treaty owns its typed decision, validation, events, and effects. */
object LeagueTreatyPower extends Power {
  val id = PowerId("denizen.league-treaty")
  val modifier = None

  private val inspectPower = PowerInspector.partial {
    case PowerContext(_, source @ RuleSourceRef.SiteCard(siteId, _: DenizenId),
        facts: RestPowerFacts)
        if facts.sources.get(source).exists(indexed =>
          indexed.powerIds.contains(id) &&
            indexed.face == RuleSourceFace.FaceUp) =>
      val owner = (for {
        site <- facts.ready.game.current.map.sites.get(siteId)
        ruler <- SiteRule.ruler(site.forces,
          facts.ready.game.current.players).toOption
      } yield ruler).collect { case SiteRuler.Player(playerId) => playerId }
      PowerInspection(owner.nonEmpty &&
        eligibleSources(facts.ready, siteId).nonEmpty, owner, owner)
  }

  private def preparePower(input: RestPowerPreparation)
      : Either[OathViolation, Option[RestPowerDecisionStarted]] =
    input.invocation.source match {
      case RestPowerSourceRef.SiteCard(siteId, denizenId: DenizenId) =>
        snapshotFor(input.ready, input.invocation).fold(_ => Right(None), snapshot =>
          Right(Some(LeagueTreatyDecisionStarted(input.restActor,
            decisionId(input.ready, input.restActor, siteId, denizenId), id,
            SiteDenizenTarget(siteId, denizenId),
            input.invocation.decisionOwner, input.remaining,
            snapshot._1, snapshot._2))))
      case _ => Right(None)
    }

  private def resolvePower(input: LeagueTreatyResolutionPreparation)
      : Either[OathViolation, RestPowerDecisionCompleted] = for {
    _ <- treatyPayload(input.pending)
    source <- treatySource(input.pending.current)
  } yield LeagueTreatyResolved(
    input.pending.restActor, input.pending.decision,
    input.pending.current.powerId, source, input.actor,
    input.allocations, input.destinationBank)

  private def declinePower(input: RestPowerDeclinePreparation)
      : Either[OathViolation, RestPowerDecisionCompleted] = for {
    _ <- treatyPayload(input.pending)
    source <- treatySource(input.pending.current)
  } yield LeagueTreatyDeclined(
    input.pending.restActor, input.pending.decision,
    input.pending.current.powerId, source, input.actor)

  private def evolvePower(catalog: oathdigital.catalog.ExecutableCatalog,
      state: OathState, event: RestPowerEvent)
      : Either[OathViolation, OathState] = event match {
      case started: LeagueTreatyDecisionStarted => state match {
        case Ready(ready) => Right(Ready(ready.copy(game = ready.game.copy(current =
          ready.game.current.copy(pending = Some(PendingProcedure.RestPowerDecision(
            started.decision, started.restActor,
            RestPowerInvocationRef(started.powerId, RestPowerSourceRef.SiteCard(
              started.source.siteId, started.source.denizenId),
              started.decisionOwner), started.remaining,
            RestPowerDecisionPayload.LeagueTreaty(started.eligibleSources,
              started.legalBanks))))))))
        case _ => Left(GameNotStarted)
      }
      case resolved: LeagueTreatyResolved => state match {
        case Ready(ready) => for {
          pending <- matchingPending(ready, resolved.restActor, resolved.decision,
            resolved.powerId, resolved.source, resolved.decisionOwner)
          _ <- treatyPayload(pending)
          snapshot <- snapshotFor(ready, pending.current)
          _ <- validateAllocations(ready, snapshot, resolved.allocations,
            resolved.destinationBank)
        } yield Ready(moveFavor(ready, resolved.allocations,
          resolved.destinationBank))
        case _ => Left(GameNotStarted)
      }
      case declined: LeagueTreatyDeclined => state match {
        case Ready(ready) => matchingPending(ready, declined.restActor,
          declined.decision, declined.powerId, declined.source,
          declined.decisionOwner).flatMap(treatyPayload).map(_ =>
            Ready(clearPending(ready)))
        case _ => Left(GameNotStarted)
      }
      case _ => Left(InvalidEventOrder(
        "League Treaty handler received another Rest power event"))
    }

  private val handler = LeagueTreatyPowerHandler.functional(
    PowerWindow.RestReturnFavor, inspectPower)(preparePower, resolvePower,
    declinePower, evolvePower)
  val handlers: Vector[PowerHandler] = Vector(handler)

  private def treatyPayload(pending: PendingProcedure.RestPowerDecision)
      : Either[OathViolation, RestPowerDecisionPayload.LeagueTreaty] =
    pending.payload match {
      case value: RestPowerDecisionPayload.LeagueTreaty => Right(value)
      case _ => Left(InvalidEventOrder(
        "League Treaty received another Rest power decision payload"))
    }

  private def treatySource(invocation: RestPowerInvocationRef)
      : Either[OathViolation, SiteDenizenTarget] = invocation.source match {
    case RestPowerSourceRef.SiteCard(siteId, denizenId: DenizenId) =>
      Right(SiteDenizenTarget(siteId, denizenId))
    case _ => Left(InvalidEventOrder(
      "League Treaty requires a denizen site-card source"))
  }

  private def snapshotFor(ready: ReadyGame, invocation: RestPowerInvocationRef)
      : Either[OathViolation, (Vector[SiteFavorSource], Vector[Suit])] = {
    val current = ready.game.current
    for {
      source <- treatySource(invocation).left.map(error =>
        UnsupportedRestState(error.toString))
      site <- current.map.sites.get(source.siteId)
        .toRight(UnsupportedRestState("League Treaty source site is missing"))
      _ <- site.denizens.collectFirst {
        case DenizenState(id, Orientation.FaceUp, _)
            if id == source.denizenId => ()
      }.toRight(UnsupportedRestState("League Treaty source is not faceup"))
      owner <- SiteRule.ruler(site.forces, current.players)
        .left.map(error => UnsupportedRestState(error.toString)).flatMap {
          case SiteRuler.Player(playerId) => Right(playerId)
          case _ => Left(UnsupportedRestState("League Treaty has no player ruler"))
        }
      _ <- Either.cond(owner == invocation.decisionOwner, (),
        UnsupportedRestState("League Treaty ruler changed"))
      sources = eligibleSources(ready, source.siteId)
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

  private def matchingPending(ready: ReadyGame, restActor: PlayerId,
      decision: DecisionId, powerId: PowerId, source: SiteDenizenTarget,
      owner: PlayerId): Either[OathViolation, PendingProcedure.RestPowerDecision] =
    ready.game.current.pending match {
      case Some(value: PendingProcedure.RestPowerDecision) if
          value.decision == decision && value.restActor == restActor &&
          value.current == RestPowerInvocationRef(powerId,
            RestPowerSourceRef.SiteCard(source.siteId, source.denizenId), owner) =>
        Right(value)
      case _ => Left(InvalidEventOrder(
        "League Treaty pending decision does not match"))
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

  private def decisionId(ready: ReadyGame, restActor: PlayerId,
      siteId: SiteId, denizenId: DenizenId): DecisionId = DecisionId(
    s"rest-${ready.game.current.tracks.round}-${restActor.value}-" +
      s"${id.value}-${siteId.value}-${denizenId.value}")
}
