package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, GameStateUpdates}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameFoundationProfile
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue.ActActionSelection
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

sealed trait MinorActionCommand extends Product with Serializable
object MinorActionCommand {
  final case class DiscardFacedownAdviser(player: PlayerId, adviser: WorldCardId)
      extends MinorActionCommand
  final case class PlayFacedownAdviser(player: PlayerId, adviser: WorldCardId,
      placement: SearchPlacement) extends MinorActionCommand
  final case class PeekSiteRelics(player: PlayerId) extends MinorActionCommand
  final case class RevealOwnedRelic(player: PlayerId, relic: RelicId)
      extends MinorActionCommand
  final case class MoveWarbands(player: PlayerId, toSite: Boolean, amount: Int)
      extends MinorActionCommand
}

object MinorActions {
  def legalAdviserPlacements(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, adviser: WorldCardId): Vector[SearchPlacement] = {
    val replacements = ready.game.current.players.find(_.player == player)
      .flatMap(_.pawnSite).flatMap(ready.game.current.map.sites.get)
      .toVector.flatMap(_.denizens.map(card => Some(card.id)))
    (Vector(SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Site(None)) ++ replacements.map(SearchPlacement.Site))
      .distinct.filter(placement => validateSearchModifiers(catalog, ready, player).isRight &&
        CardPlay.resolve(catalog, ready, player, adviser, placement,
          CardPlay.Origin.FacedownAdviser).isRight)
  }

  def handle(catalog: ExecutableCatalog, state: OathState,
      command: MinorActionCommand): Either[OathViolation, OathTransition] = {
    val event = command match {
      case MinorActionCommand.DiscardFacedownAdviser(player, adviser) => for {
        ready <- validateAct(catalog, state, player)
        _ <- CardPlay.resolve(catalog, ready, player, adviser,
          SearchPlacement.Discard, CardPlay.Origin.FacedownAdviser)
        actor <- actorAtSite(ready, player).map(_._1)
        region <- actor.pawnSite.flatMap(ready.game.current.map.regionOf)
          .toRight(PawnSiteMissing(player))
      } yield FacedownAdviserDiscarded(player, adviser, nextRegion(region))

      case MinorActionCommand.PlayFacedownAdviser(player, adviser, placement) => for {
        ready <- validateAct(catalog, state, player)
        _ <- validateSearchModifiers(catalog, ready, player)
        result <- CardPlay.resolve(catalog, ready, player, adviser, placement,
          CardPlay.Origin.FacedownAdviser)
      } yield FacedownAdviserPlayed(player, adviser, placement, result.favorGained,
        result.discardedWorld, result.discardedEdifices)

      case MinorActionCommand.PeekSiteRelics(player) => for {
        ready <- validateAct(catalog, state, player)
        at <- actorAtSite(ready, player)
        (_, siteId, site) = at
        _ <- Either.cond(site.relics.nonEmpty, (),
          MinorActionUnavailable("the pawn's site has no relics"))
      } yield SiteRelicsPeeked(player, siteId, site.relics.map(_.id))

      case MinorActionCommand.RevealOwnedRelic(player, relic) => for {
        ready <- validateAct(catalog, state, player)
        actor <- ready.game.current.players.find(_.player == player)
          .toRight(MinorActionUnavailable("actor is not in the game"))
        held <- actor.relics.find(_.id == relic)
          .toRight(MinorActionUnavailable("relic is not held by the actor"))
        _ <- Either.cond(held.orientation == Orientation.FaceDown, (),
          MinorActionUnavailable("relic is already faceup"))
      } yield OwnedRelicRevealed(player, relic)

      case MinorActionCommand.MoveWarbands(player, toSite, amount) => for {
        ready <- validateAct(catalog, state, player)
        at <- actorAtSite(ready, player)
        (actor, siteId, site) = at
        occupied <- site.forces match {
          case value: SiteForces.Occupied => Right(value)
          case SiteForces.Empty => Left(MinorActionUnavailable("the site has no warbands"))
        }
        _ <- Either.cond(amount > 0, (),
          MinorActionUnavailable("warband amount must be positive"))
        _ <- if (toSite) for {
          _ <- Either.cond(SiteRule.ruledBy(site.forces,
            ready.game.current.players, player).getOrElse(false), (),
            MinorActionUnavailable("actor must rule the pawn's site"))
          _ <- Either.cond(amount <= actor.board.warbands, (),
            MinorActionUnavailable("not enough warbands on the player board"))
        } yield () else for {
          _ <- Either.cond(occupied.kind == ForceKind.Exile(actor.lineage), (),
            MinorActionUnavailable("the pawn's site does not hold the actor's warbands"))
          _ <- Either.cond(amount < occupied.count, (),
            MinorActionUnavailable("at least one warband must remain at the site"))
        } yield ()
      } yield WarbandsMoved(player, siteId, toSite, amount,
        actor.board.warbands, occupied.count)
    }
    event.flatMap(e => transition(catalog, state, e))
  }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: FacedownAdviserDiscarded => for {
      ready <- validateAct(catalog, state, e.playerId)
      actor <- ready.game.current.players.find(_.player == e.playerId)
        .toRight(MinorActionUnavailable("actor is not in the game"))
      region <- actor.pawnSite.flatMap(ready.game.current.map.regionOf)
        .toRight(PawnSiteMissing(e.playerId))
      _ <- Either.cond(e.destination == nextRegion(region), (),
        MinorActionOutcomeMismatch("recorded adviser discard region is invalid"))
      outcome <- CardPlay.resolve(catalog, ready, e.playerId, e.adviserId,
        SearchPlacement.Discard, CardPlay.Origin.FacedownAdviser)
    } yield Ready(outcome.ready)

    case e: FacedownAdviserPlayed => for {
      ready <- validateAct(catalog, state, e.playerId)
      _ <- validateSearchModifiers(catalog, ready, e.playerId)
      expected <- CardPlay.resolve(catalog, ready, e.playerId, e.adviserId,
        e.placement, CardPlay.Origin.FacedownAdviser)
      _ <- Either.cond((e.favorGained, e.discardedWorld, e.discardedEdifices) ==
        (expected.favorGained, expected.discardedWorld, expected.discardedEdifices), (),
        MinorActionOutcomeMismatch("recorded adviser placement facts are invalid"))
    } yield Ready(expected.ready)

    case e: SiteRelicsPeeked => for {
      ready <- validateAct(catalog, state, e.playerId)
      at <- actorAtSite(ready, e.playerId)
      (_, siteId, site) = at
      _ <- Either.cond(siteId == e.siteId && site.relics.map(_.id) == e.relics &&
        e.relics.nonEmpty, (), MinorActionOutcomeMismatch(
        "recorded site relic peek does not match the pawn's site"))
    } yield Ready(ready.copy(support = ready.support.copy(relicKnowledge =
      ready.support.relicKnowledge.updated(e.playerId,
        ready.support.relicKnowledge.getOrElse(e.playerId, Map.empty)
          .updated(e.siteId, e.relics)))))

    case e: OwnedRelicRevealed => for {
      ready <- validateAct(catalog, state, e.playerId)
      actor <- ready.game.current.players.find(_.player == e.playerId)
        .toRight(MinorActionUnavailable("actor is not in the game"))
      held <- actor.relics.find(_.id == e.relicId)
        .toRight(MinorActionUnavailable("relic is not held by the actor"))
      _ <- Either.cond(held.orientation == Orientation.FaceDown, (),
        MinorActionOutcomeMismatch("recorded relic was not facedown"))
    } yield Ready(updatePlayer(ready, e.playerId)(p => p.copy(relics = p.relics.map {
      case relic if relic.id == e.relicId => relic.copy(orientation = Orientation.FaceUp)
      case relic => relic
    })))

    case e: WarbandsMoved => for {
      ready <- validateAct(catalog, state, e.playerId)
      at <- actorAtSite(ready, e.playerId)
      (actor, siteId, site) = at
      occupied <- site.forces match {
        case value: SiteForces.Occupied => Right(value)
        case SiteForces.Empty => Left(MinorActionOutcomeMismatch("recorded site is empty"))
      }
      _ <- Either.cond(siteId == e.siteId && actor.board.warbands == e.priorBoardWarbands &&
        occupied.count == e.priorSiteWarbands, (),
        MinorActionOutcomeMismatch("recorded prior warband facts changed"))
      _ <- Either.cond(e.amount > 0, (),
        MinorActionOutcomeMismatch("recorded warband amount is not positive"))
      _ <- if (e.toSite) for {
        _ <- Either.cond(SiteRule.ruledBy(site.forces,
          ready.game.current.players, e.playerId).getOrElse(false), (),
          MinorActionOutcomeMismatch("recorded actor did not rule the site"))
        _ <- Either.cond(e.amount <= actor.board.warbands, (),
          MinorActionOutcomeMismatch("recorded board lacked warbands"))
      } yield () else for {
        _ <- Either.cond(occupied.kind == ForceKind.Exile(actor.lineage), (),
          MinorActionOutcomeMismatch("recorded site had another force"))
        _ <- Either.cond(e.amount < occupied.count, (),
          MinorActionOutcomeMismatch("recorded movement removed the last warband"))
      } yield ()
    } yield Ready(moveWarbands(ready, actor, siteId, occupied, e.toSite, e.amount))

    case _ => Left(InvalidEventOrder("MinorActions received a non-minor-action event"))
  }

  private def actorAtSite(ready: ReadyGame, player: PlayerId) = for {
    actor <- ready.game.current.players.find(_.player == player)
      .toRight(MinorActionUnavailable("actor is not in the game"))
    siteId <- actor.pawnSite.toRight(PawnSiteMissing(player))
    site <- ready.game.current.map.sites.get(siteId).toRight(
      MinorActionUnavailable("pawn site is not in play"))
  } yield (actor, siteId, site)

  private def validateAct(catalog: ExecutableCatalog, state: OathState,
      player: PlayerId): Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, player).flatMap { ready =>
      val supported = ready.support.foundationProfile ==
        FirstGameFoundationProfile.FixedUnaltered &&
        ready.game.campaign.lineages.values.forall(_.role == Role.Exile) &&
        ready.game.campaign.foundations.values.forall(f =>
          f.face == FoundationFace.Normal && f.alterationSources.isEmpty)
      Either.cond(supported, ready, MinorActionUnavailable(
        "minor actions are limited to fixed unaltered all-Exile first-game rules"))
    }

  private def validateSearchModifiers(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId): Either[OathViolation, Unit] = {
    val current = ready.game.current
    val actor = current.players.find(_.player == player).get
    val accessibleSites = current.map.inPlay.filter { siteId =>
      actor.pawnSite.contains(siteId) || SiteRule.ruledBy(current.map.sites(siteId).forces,
        current.players, player).getOrElse(false)
    }.toSet
    val active = actor.advisers.collect {
      case d: DenizenState if d.orientation == Orientation.FaceUp => d.id
    } ++ accessibleSites.toVector.flatMap(siteId => current.map.sites(siteId).denizens.collect {
      case d: DenizenState if d.orientation == Orientation.FaceUp => d.id
    })
    active.iterator.flatMap { id =>
      catalog.denizens.find(_.id.value == id.value).toVector.flatMap { definition =>
        MinorActionPowerSupport.validateSearchModifier(catalog, id,
          definition.handlers).left.toOption
      }
    }.toVector.headOption.toLeft(())
  }

  private def updatePlayer(ready: ReadyGame, player: PlayerId)(f: PlayerState => PlayerState) =
    GameStateUpdates.updateCurrent(ready)(current => current.copy(players =
      current.players.map(p => if (p.player == player) f(p) else p)))

  private def moveWarbands(ready: ReadyGame, actor: PlayerState, siteId: SiteId,
      occupied: SiteForces.Occupied, toSite: Boolean, amount: Int) = {
    val boardDelta = if (toSite) -amount else amount
    val siteDelta = -boardDelta
    val withPlayer = updatePlayer(ready, actor.player)(p => p.copy(board =
      p.board.copy(warbands = p.board.warbands + boardDelta)))
    GameStateUpdates.updateCurrent(withPlayer)(current => current.copy(map = current.map.copy(
      sites = current.map.sites.updated(siteId, current.map.sites(siteId).copy(
        forces = occupied.copy(count = occupied.count + siteDelta))))))
  }

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathTransition] =
    evolve(catalog, state, event).map(OathTransition(_, Vector(event),
      ActActionSelection(playerId(event))))

  private def playerId(event: OathEvent): PlayerId = event match {
    case value: FacedownAdviserDiscarded => value.playerId
    case value: FacedownAdviserPlayed => value.playerId
    case value: SiteRelicsPeeked => value.playerId
    case value: OwnedRelicRevealed => value.playerId
    case value: WarbandsMoved => value.playerId
    case _ => throw new IllegalArgumentException("not a minor-action event")
  }
}
