package oathdigital.gameplay.actions

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.gameplay.GameStateUpdates.updateCurrent
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

/** Authoritative placement procedure shared by Search and facedown-adviser play. */
object CardPlay {
  sealed trait Origin extends Product with Serializable
  object Origin {
    final case class Search(decision: DecisionId) extends Origin
    case object FacedownAdviser extends Origin
  }

  final case class Outcome(
      ready: ReadyGame,
      favorGained: Int = 0,
      discardedWorld: Vector[WorldCardId] = Vector.empty,
      discardedEdifices: Vector[EdificeId] = Vector.empty
  )

  def resolve(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      playerId: PlayerId,
      card: WorldCardId,
      placement: SearchPlacement,
      origin: Origin,
      precedingDiscards: Vector[WorldCardId] = Vector.empty
  ): Either[OathViolation, Outcome] = for {
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(catalog, player, card, placement, origin)
    placed <- place(catalog, ready, player, card, placement, origin)
  } yield finish(placed, player, card, placement, origin, precedingDiscards)

  def playedSource(ready: ReadyGame, playerId: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Option[RuleSourceRef] = placement match {
    case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
      Some(RuleSourceRef.Adviser(playerId, card))
    case SearchPlacement.Site(_) => ready.game.current.players
      .find(_.player == playerId).flatMap(_.pawnSite)
      .map(RuleSourceRef.SiteCard(_, card))
    case _ => None
  }

  private def validateOrigin(catalog: ExecutableCatalog, player: PlayerState,
      card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, Unit] = origin match {
    case Origin.Search(_) => Right(())
    case Origin.FacedownAdviser =>
      player.advisers.find(_.id == card).filter {
        case DenizenState(_, Orientation.FaceDown, _) => true
        case VisionState(_, Orientation.FaceDown) => true
        case _ => false
      }.toRight(MinorActionUnavailable(
        "adviser is not held facedown by the actor")).flatMap { _ => placement match {
        case SearchPlacement.Discard => Right(())
        case _ => card match {
        case id: DenizenId => catalog.denizens.find(_.id.value == id.value)
          .toRight(UnknownWorldCard(id)).flatMap(definition =>
            MinorActionPowerSupport.validateAdviserPlay(catalog, id,
              definition.handlers))
        case id: VisionId =>
          if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
          else MinorActionPowerSupport.validateVisionPlay(catalog, id)
      }}}
  }

  private def place(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, Outcome] = placement match {
    case SearchPlacement.Discard => origin match {
      case Origin.Search(_) => Right(Outcome(ready))
      case Origin.FacedownAdviser => Right(Outcome(removeAdviser(ready,
        player.player, card)))
    }
    case SearchPlacement.Site(replace) => card match {
      case _: VisionId => Left(InvalidSearchPlacement("Visions cannot be played to sites"))
      case id: DenizenId => for {
        definition <- catalog.denizens.find(_.id.value == id.value)
          .toRight(UnknownWorldCard(id))
        _ <- Either.cond(definition.restrictions != CardRestrictions.AdviserOnly &&
          definition.restrictions != CardRestrictions.LockedAdviserOnly, (),
          InvalidSearchPlacement("adviser-only card cannot be played to a site"))
        siteId <- player.pawnSite.toRight(PawnSiteMissing(player.player))
        site <- ready.game.current.map.sites.get(siteId)
          .toRight(InvalidSearchPlacement("pawn site is not in play"))
        replacement <- validateSiteReplacement(catalog, siteId, site,
          definition.suit.value, replace)
      } yield placeAtSite(ready, player, siteId, site, id,
        definition.suit.value, replacement, origin)
    }
    case SearchPlacement.Adviser(orientation, replace) =>
      placeAsAdviser(catalog, ready, player, card, orientation, replace, origin)
  }

  private def placeAsAdviser(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, orientation: Orientation,
      replace: Option[CardId], origin: Origin): Either[OathViolation, Outcome] = {
    val allowed = origin match {
      case Origin.Search(_) => true
      case Origin.FacedownAdviser => orientation == Orientation.FaceUp && replace.isEmpty
    }
    if (!allowed) Left(InvalidSearchPlacement(
      "facedown adviser must be played faceup to advisers or the pawn's site"))
    else card match {
      case id: DenizenId => catalog.denizens.find(_.id.value == id.value)
        .toRight(UnknownWorldCard(id)).flatMap { definition =>
          if (orientation == Orientation.FaceUp &&
              definition.restrictions == CardRestrictions.SiteOnly)
            Left(InvalidSearchPlacement(
              "site-only card can only be held as a facedown adviser"))
          else validateAdviserReplacement(catalog, origin match {
            case Origin.Search(_) => player
            case Origin.FacedownAdviser => player.copy(advisers =
              player.advisers.filterNot(_.id == card))
          }, replace).map {
            case (advisers, removed) =>
              val next = updatePlayer(ready, player.player)(_.copy(advisers =
                advisers :+ DenizenState(id, orientation, Tokens.empty)))
              Outcome(next, discardedWorld = removed.toVector)
          }
        }
      case id: VisionId => placeVision(catalog, ready, player, id, orientation,
        replace, origin)
    }
  }

  private def placeVision(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, id: VisionId, orientation: Orientation,
      replace: Option[CardId], origin: Origin): Either[OathViolation, Outcome] = {
    if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
    else origin match {
      case Origin.FacedownAdviser =>
        val replaced = player.revealedVision.map(_.id: WorldCardId).toVector
        val next = updatePlayer(ready, player.player)(_.copy(
          advisers = player.advisers.filterNot(_.id == id),
          revealedVision = Some(VisionState(id, Orientation.FaceUp))))
        Right(Outcome(next, discardedWorld = replaced))
      case Origin.Search(_) if orientation == Orientation.FaceUp =>
        MinorActionPowerSupport.validateFaceupVision(catalog, ready,
          player.player, id).flatMap { _ =>
          val expected = if (id == VisionRules.Conspiracy) None
            else player.revealedVision.map(_.id)
          if (replace != expected) Left(InvalidSearchPlacement(
            if (expected.nonEmpty) "a revealed Vision must be replaced"
            else "there is no revealed Vision to replace"))
          else {
            val next = if (id == VisionRules.Conspiracy) ready else
              updatePlayer(ready, player.player)(_.copy(
                revealedVision = Some(VisionState(id, Orientation.FaceUp))))
            Right(Outcome(next, discardedWorld = expected.toVector))
          }
        }
      case Origin.Search(_) => validateAdviserReplacement(catalog, player,
        replace).map { case (advisers, removed) =>
          Outcome(updatePlayer(ready, player.player)(_.copy(advisers = advisers :+
            VisionState(id, Orientation.FaceDown))),
            discardedWorld = removed.toVector)
        }
    }
  }

  private def validateAdviserReplacement(catalog: ExecutableCatalog,
      player: PlayerState, replace: Option[CardId])
      : Either[OathViolation, (Vector[AdviserState], Option[WorldCardId])] = {
    val base = player.advisers
    val mustReplace = base.size >= 3
    if (mustReplace != replace.nonEmpty) Left(InvalidSearchPlacement(
      if (mustReplace) "a full adviser area requires a discard"
      else "an adviser cannot be discarded when there is free capacity"))
    else replace match {
      case None => Right(base -> None)
      case Some(id) => base.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement adviser is not held")).flatMap { _ =>
        val locked = id match {
          case d: DenizenId => catalog.denizens.find(_.id.value == d.value)
            .exists(_.restrictions == CardRestrictions.LockedAdviserOnly)
          case _ => false
        }
        if (locked) Left(LockedAdviserCannotBeDiscarded(id))
        else id match {
          case world: WorldCardId => Right(base.filterNot(_.id == id) -> Some(world))
          case _ => Left(InvalidSearchPlacement("replacement adviser is not a world card"))
        }
      }
    }
  }

  private def validateSiteReplacement(catalog: ExecutableCatalog, siteId: SiteId,
      site: SiteState, suit: String, replace: Option[CardId])
      : Either[OathViolation, Option[SiteDenizenState]] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (!full && replace.isEmpty) Right(None)
    else if (!full) Left(InvalidSearchPlacement(
      "site replacement is allowed only at a full Homeland"))
    else {
      val homelandMatches = site.denizens.collectFirst { case e: EdificeState =>
        catalog.edifices.find(_.id.value == e.id.value).exists(_.suit.value == suit)
      }.contains(true)
      if (!homelandMatches) Left(InvalidSearchPlacement(
        "full non-matching site cannot accept a denizen"))
      else replace.flatMap(id => site.denizens.find(_.id == id)).toRight(
        InvalidSearchPlacement("full matching Homeland requires a site-card discard")).map(Some(_))
    }
  }

  private def placeAtSite(ready: ReadyGame, player: PlayerState, siteId: SiteId,
      site: SiteState, card: DenizenId, suit: String,
      replacement: Option[SiteDenizenState], origin: Origin): Outcome = {
    val modelSuit = Suit.all.find(_.key == suit).get
    val gain = math.min(1, ready.support.favorBanks.getOrElse(modelSuit, 0))
    val updatedSite = site.copy(denizens = site.denizens.filterNot(value =>
      replacement.exists(_.id == value.id)) :+
      DenizenState(card, Orientation.FaceUp, Tokens.empty))
    val withoutSource = origin match {
      case Origin.Search(_) => ready
      case Origin.FacedownAdviser => removeAdviser(ready, player.player, card)
    }
    val next = updateCurrent(withoutSource) { current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(siteId, updatedSite)),
      players = current.players.map(p => if (p.player != player.player) p else
        p.copy(board = p.board.copy(favor = p.board.favor + gain))))
    }.copy(support = ready.support.copy(favorBanks = ready.support.favorBanks.updated(
      modelSuit, ready.support.favorBanks.getOrElse(modelSuit, 0) - gain)))
    replacement match {
      case Some(value: DenizenState) => Outcome(next, gain, Vector(value.id))
      case Some(value: EdificeState) => Outcome(next, gain,
        discardedEdifices = Vector(value.id))
      case _ => Outcome(next, gain)
    }
  }

  private def finish(outcome: Outcome, player: PlayerState, card: WorldCardId,
      placement: SearchPlacement, origin: Origin,
      preceding: Vector[WorldCardId]): Outcome = {
    val playedDiscard = if (placement == SearchPlacement.Discard) Vector(card)
      else Vector.empty
    val world = preceding ++ outcome.discardedWorld ++ playedDiscard
    val destination = player.pawnSite.flatMap(
      outcome.ready.game.current.map.regionOf).map(nextRegion)
    val withDiscards = destination.filter(_ => world.nonEmpty).fold(outcome.ready) {
      region => updateCurrent(outcome.ready) { current => current.copy(commonCards =
        current.commonCards.copy(regionalDiscards = current.commonCards.regionalDiscards
          .updated(region, current.commonCards.discard(region) ++ world))) }
    }
    val withEdifices = if (outcome.discardedEdifices.isEmpty) withDiscards else
      updateCurrent(withDiscards) { current => current.copy(commonCards =
        current.commonCards.copy(edificeDeck = current.commonCards.edificeDeck ++
          outcome.discardedEdifices)) }
    val withPending = (origin, card, placement) match {
      case (Origin.Search(decision), id, SearchPlacement.Adviser(
          Orientation.FaceUp, None)) if id == VisionRules.Conspiracy =>
        updateCurrent(withEdifices)(_.copy(pending = Some(PendingProcedure.Conspiracy(
          decision, player.player, VisionRules.Conspiracy, None, 0,
          awaitingTarget = true))))
      case (Origin.Search(_), _, _) => updateCurrent(withEdifices)(_.copy(pending = None))
      case _ => withEdifices
    }
    outcome.copy(ready = withPending)
  }

  private def removeAdviser(ready: ReadyGame, player: PlayerId, card: WorldCardId) =
    updatePlayer(ready, player)(p => p.copy(advisers =
      p.advisers.filterNot(_.id == card)))

  private def updatePlayer(ready: ReadyGame, player: PlayerId)
      (f: PlayerState => PlayerState) = updateCurrent(ready)(current => current.copy(
    players = current.players.map(p => if (p.player == player) f(p) else p)))

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
}
