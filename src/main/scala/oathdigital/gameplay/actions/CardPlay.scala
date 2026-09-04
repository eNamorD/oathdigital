package oathdigital.gameplay.actions

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.gameplay.GameStateUpdates.updateCurrent
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.gameplay.operations.{Bury, BuryableCard, CoreOperation,
  Discard, Gain, Location, Move => CoreMove, OperationExecutor, OperationPolicy,
  OperationTransaction, Piece, PositionedLocation, StackPosition}
import oathdigital.model._

/** Authoritative placement procedure shared by Search and facedown-adviser play.
  * Legality and recorded placement facts stay here; every card, favor, or
  * edifice change is expressed as core operations and applied atomically.
  * Pending-procedure changes stay explicit direct updates.
  */
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

  /** Physical intent of one placement. Replacement cards join the ordered
    * next-region discards or the edifice deck bottom after the kept card moves.
    */
  private final case class PlacementPlan(
      kept: Option[CoreOperation],
      favor: Vector[CoreOperation],
      favorGained: Int,
      discardedWorld: Vector[(WorldCardId, PositionedLocation)],
      discardedEdifices: Vector[(EdificeId, PositionedLocation)],
      startConspiracy: Option[DecisionId],
      endSearchPending: Boolean,
      tokenDenizen: Option[(DenizenId, Suit, Int, Int)] = None,
      tokenEdifice: Option[(EdificeId, Suit, Int, Int)] = None
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
    plan <- plan(catalog, ready, player, card, placement, origin)
    outcome <- execute(ready, player, card, placement, origin,
      precedingDiscards, plan)
  } yield outcome

  def playedSource(ready: ReadyGame, playerId: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Option[RuleSourceRef] = placement match {
    case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
      Some(RuleSourceRef.Adviser(playerId, card))
    case SearchPlacement.Site(_) => ready.game.current.players
      .find(_.player == playerId).flatMap(_.pawnSite)
      .map(RuleSourceRef.SiteCard(_, card))
    case _ => None
  }

  private def keptSource(origin: Origin, player: PlayerId): PositionedLocation =
    origin match {
      case Origin.Search(_) => PositionedLocation(Location.Hand(player))
      case Origin.FacedownAdviser => PositionedLocation(Location.PlayArea(player))
    }

  private def fromSearch(origin: Origin): Boolean = origin match {
    case Origin.Search(_) => true
    case Origin.FacedownAdviser => false
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

  private def plan(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, PlacementPlan] = placement match {
    case SearchPlacement.Discard => Right(PlacementPlan(
      None, Vector.empty, 0, Vector.empty, Vector.empty,
      None, endSearchPending = fromSearch(origin)))
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
        suit <- Suit.all.find(_.key == definition.suit.value)
          .toRight(InvalidSearchPlacement("catalog suit is unknown"))
        sitePlan <- sitePlan(catalog, ready, origin, player, id, siteId, suit,
          replacement)
      } yield sitePlan
    }
    case SearchPlacement.Adviser(orientation, replace) => card match {
      case id: DenizenId => for {
        definition <- catalog.denizens.find(_.id.value == id.value)
          .toRight(UnknownWorldCard(id))
        _ <- Either.cond(origin != Origin.FacedownAdviser ||
          (orientation == Orientation.FaceUp && replace.isEmpty), (),
          InvalidSearchPlacement(
            "facedown adviser must be played faceup to advisers or the pawn's site"))
        _ <- Either.cond(!(orientation == Orientation.FaceUp &&
          definition.restrictions == CardRestrictions.SiteOnly), (),
          InvalidSearchPlacement(
            "site-only card can only be held as a facedown adviser"))
        // A facedown-adviser play moves that card within the same play area,
        // so it is excluded when deciding whether an adviser replacement is
        // required; the vector below is validation-only (never written back).
        remaining = if (origin == Origin.FacedownAdviser)
          player.advisers.filter(_.id != card) else player.advisers
        removed <- validateAdviserReplacement(catalog, remaining, replace)
      } yield adviserPlan(origin, player, card, id, orientation, removed)
      case id: VisionId =>
        if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
        else planVision(catalog, ready, player, origin, card, id, orientation,
          replace)
    }
  }

  private def sitePlan(catalog: ExecutableCatalog, ready: ReadyGame,
      origin: Origin, player: PlayerState, id: DenizenId, siteId: SiteId,
      suit: Suit, replacement: Option[SiteDenizenState])
      : Either[OathViolation, PlacementPlan] = {
    val from = keptSource(origin, player.player)
    val kept = Some(CoreMove(
      Piece.Card(id), from, PositionedLocation(Location.Site(siteId)),
      resultingOrientation = Some(Orientation.FaceUp)))
    val gain = math.min(1, ready.banks.favor.getOrElse(suit, 0))
    val favor = if (gain == 1)
      Vector(Gain.Favor(player.player, suit, 1)) else Vector.empty
    val site = PositionedLocation(Location.Site(siteId))
    replacement match {
      case Some(value: DenizenState) if !value.tokens.isEmpty =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, gain, Vector.empty, Vector.empty, None,
            endSearchPending = fromSearch(origin),
            tokenDenizen = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: DenizenState) =>
        Right(PlacementPlan(kept, favor, gain,
          Vector((value.id: WorldCardId) -> site), Vector.empty, None,
          endSearchPending = fromSearch(origin)))
      case Some(value: EdificeState) if !value.tokens.isEmpty =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, gain, Vector.empty, Vector.empty, None,
            endSearchPending = fromSearch(origin),
            tokenEdifice = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: EdificeState) =>
        Right(PlacementPlan(kept, favor, gain, Vector.empty,
          Vector(value.id -> site), None,
          endSearchPending = fromSearch(origin)))
      case None =>
        Right(PlacementPlan(kept, favor, gain, Vector.empty, Vector.empty, None,
          endSearchPending = fromSearch(origin)))
    }
  }

  private def suitOf(catalog: ExecutableCatalog, id: CardId)
      : Either[OathViolation, Suit] =
    (catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
      .flatMap(key => Suit.all.find(_.key == key)))
      .toRight(InvalidSearchPlacement("catalog suit is unknown"))

  private def adviserPlan(origin: Origin, player: PlayerState, card: WorldCardId,
      id: DenizenId, orientation: Orientation,
      removed: Option[WorldCardId]): PlacementPlan = {
    val from = keptSource(origin, player.player)
    val kept = Some(CoreMove(
      Piece.Card(id), from, PositionedLocation(Location.PlayArea(player.player)),
      resultingOrientation = Some(orientation)))
    PlacementPlan(kept, Vector.empty, 0,
      removed.toVector.map(value => value ->
        PositionedLocation(Location.PlayArea(player.player))),
      Vector.empty, None, endSearchPending = fromSearch(origin))
  }

  private def planVision(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, origin: Origin, card: WorldCardId, id: VisionId,
      orientation: Orientation, replace: Option[CardId])
      : Either[OathViolation, PlacementPlan] = origin match {
    case Origin.FacedownAdviser =>
      Either.cond(orientation == Orientation.FaceUp && replace.isEmpty, (),
        InvalidSearchPlacement(
          "facedown adviser must be played faceup to advisers or the pawn's site"))
        .map { _ =>
          val from = PositionedLocation(Location.PlayArea(player.player))
          PlacementPlan(
            Some(CoreMove(Piece.Card(id), from, from,
              resultingOrientation = Some(Orientation.FaceUp))),
            Vector.empty, 0,
            player.revealedVision.toVector.map { value =>
              (value.id: WorldCardId) -> from
            },
            Vector.empty, None, endSearchPending = false)
        }
    case Origin.Search(decisionId) if orientation == Orientation.FaceUp =>
      MinorActionPowerSupport.validateFaceupVision(catalog, ready,
        player.player, id).flatMap { _ =>
        val expected = player.revealedVision.map(_.id)
        Either.cond(replace == expected, (), InvalidSearchPlacement(
          if (expected.nonEmpty) "a revealed Vision must be replaced"
          else "there is no revealed Vision to replace")).flatMap { _ =>
          if (id == VisionRules.Conspiracy)
            Right(PlacementPlan(None, Vector.empty, 0, Vector.empty,
              Vector.empty, Some(decisionId), endSearchPending = false))
          else {
            val from = keptSource(origin, player.player)
            Right(PlacementPlan(
              Some(CoreMove(Piece.Card(id), from,
                PositionedLocation(Location.PlayArea(player.player)),
                resultingOrientation = Some(Orientation.FaceUp))),
              Vector.empty, 0,
              expected.toVector.map(value => value ->
                PositionedLocation(Location.PlayArea(player.player))),
              Vector.empty, None, endSearchPending = true))
          }
        }
      }
    case Origin.Search(_) =>
      validateAdviserReplacement(catalog, player.advisers, replace).map { removed =>
        val from = keptSource(origin, player.player)
        PlacementPlan(
          Some(CoreMove(Piece.Card(id), from,
            PositionedLocation(Location.PlayArea(player.player)),
            resultingOrientation = Some(Orientation.FaceDown))),
          Vector.empty, 0,
          removed.toVector.map(value => value ->
            PositionedLocation(Location.PlayArea(player.player))),
          Vector.empty, None, endSearchPending = true)
      }
  }

  private def execute(
      ready: ReadyGame,
      player: PlayerState,
      card: WorldCardId,
      placement: SearchPlacement,
      origin: Origin,
      preceding: Vector[WorldCardId],
      plan: PlacementPlan
  ): Either[OathViolation, Outcome] = {
    val current = ready.game.current
    val destination = player.pawnSite.flatMap(current.map.regionOf).map(nextRegion)
    val source = keptSource(origin, player.player)
    val playedDiscard = if (placement == SearchPlacement.Discard)
      Vector(card -> source) else Vector.empty
    val tokenDenizenOps = for {
      region <- destination.toVector
      (denizenId, replacedSuit, favor, secrets) <- plan.tokenDenizen.toVector
      siteId <- player.pawnSite.toVector
    } yield Discard.Denizen(denizenId,
      PositionedLocation(Location.Site(siteId)), region, replacedSuit, favor,
      secrets, player.player)
    val discardOps = destination.toVector.flatMap { region =>
      val precedingOps = preceding.map(id => CoreMove(
        Piece.Card(id), source,
        PositionedLocation(Location.RegionalDiscard(region), StackPosition.Top)))
      val replacedOps = plan.discardedWorld.map { case (id, from) =>
        CoreMove(
          Piece.Card(id), from,
          PositionedLocation(Location.RegionalDiscard(region), StackPosition.Top))
      }
      val playedOps = playedDiscard.map { case (id, from) =>
        CoreMove(
          Piece.Card(id), from,
          PositionedLocation(Location.RegionalDiscard(region), StackPosition.Top))
      }
      precedingOps ++ replacedOps ++ tokenDenizenOps ++ playedOps
    }
    val edificeOps = plan.discardedEdifices.map { case (id, from) =>
      Bury(BuryableCard.Edifice(id), from)
    } ++ plan.tokenEdifice.toVector.flatMap {
      case (id, replacedSuit, favor, secrets) =>
        player.pawnSite.toVector.map(siteId => Discard.RuinedEdifice(id,
          PositionedLocation(Location.Site(siteId)), replacedSuit, favor,
          secrets, player.player))
    }
    // Replacement and preceding-card removals run first so a kept card can
    // enter a vacated container (for example the revealed-Vision slot) in a
    // later staged operation.
    val operations = edificeOps ++ discardOps ++
      plan.kept.toVector ++ plan.favor
    val executor = new OperationExecutor(OperationPolicy.exact(
      operations, "CardPlay semantic root is not permitted"))
    def update(state: ReadyGame): Either[OathViolation, ReadyGame] =
      Right(updateCurrent(state) { existing =>
        existing.copy(pending = plan.startConspiracy match {
          case Some(decisionId) =>
            Some(PendingProcedure.Conspiracy(decisionId, player.player,
              VisionRules.Conspiracy, None, awaitingTarget = true))
          case None if plan.endSearchPending => None
          case None => existing.pending
        })
      })
    val evolved = if (operations.isEmpty) update(ready)
    else OperationTransaction.evolve(
      ready, operations, executor)(update)
    evolved.map(state => Outcome(
      state,
      plan.favorGained,
      plan.discardedWorld.map(_._1) ++ plan.tokenDenizen.map(_._1).toVector,
      plan.discardedEdifices.map(_._1) ++ plan.tokenEdifice.map(_._1).toVector))
  }

  private def validateAdviserReplacement(catalog: ExecutableCatalog,
      advisers: Vector[AdviserState], replace: Option[CardId])
      : Either[OathViolation, Option[WorldCardId]] = {
    val mustReplace = advisers.size >= 3
    if (mustReplace != replace.nonEmpty) Left(InvalidSearchPlacement(
      if (mustReplace) "a full adviser area requires a discard"
      else "an adviser cannot be discarded when there is free capacity"))
    else replace match {
      case None => Right(None)
      case Some(id) => advisers.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement adviser is not held")).flatMap { _ =>
        val locked = id match {
          case d: DenizenId => catalog.denizens.find(_.id.value == d.value)
            .exists(_.restrictions == CardRestrictions.LockedAdviserOnly)
          case _ => false
        }
        if (locked) Left(LockedAdviserCannotBeDiscarded(id))
        else id match {
          case world: WorldCardId => Right(Some(world))
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

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
}
