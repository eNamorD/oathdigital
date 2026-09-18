package oathdigital.gameplay.actions

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.model.OathViolation._
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

/** Pure placement planner shared by Search and facedown-adviser walker trees.
  * Every card, favor, or edifice change is expressed as core operations.
  */
object CardPlay {
  sealed trait Origin extends Product with Serializable
  object Origin {
    case object FacedownAdviser extends Origin
    case object TemporaryHand extends Origin
  }

  final case class Choice(placement: SearchPlacement,
      replacements: Vector[CardId])

  def legalChoices(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      faceupLimit: Int, facedownLimit: Int): Vector[Choice] = {
    val player = ready.game.current.players.find(_.player == actor)
    val placements = Vector[SearchPlacement](SearchPlacement.Discard,
      SearchPlacement.Site(None),
      SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    placements.flatMap { placement =>
      val limit = placement match {
        case SearchPlacement.Adviser(Orientation.FaceUp, _) => faceupLimit
        case _ => facedownLimit
      }
      val direct = plannedOperations(catalog, ready, actor, card,
        placement, origin, limit).isRight
      val candidateIds: Vector[CardId] = placement match {
        case _: SearchPlacement.Site => player.toVector.flatMap(_.pawnSite)
          .flatMap(ready.game.current.map.sites.get)
          .flatMap(_.denizens.map(_.id))
        case SearchPlacement.Adviser(Orientation.FaceUp, _)
            if card.isInstanceOf[VisionId] =>
          player.toVector.flatMap(_.revealedVision).map(_.id)
        case _: SearchPlacement.Adviser => player.toVector.flatMap(_.advisers)
          .filterNot(value => origin == Origin.FacedownAdviser &&
            value.id == card).map(_.id)
        case SearchPlacement.Discard => Vector.empty
      }
      val replacements = if (direct) Vector.empty else candidateIds.filter { id =>
        val selected = placement match {
          case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
          case value: SearchPlacement.Adviser => value.copy(replace = Some(id))
          case SearchPlacement.Discard => SearchPlacement.Discard
        }
        plannedOperations(catalog, ready, actor, card, selected,
          origin, limit).isRight
      }
      Option.when(direct || replacements.nonEmpty)(Choice(placement, replacements))
    }
  }

  /** Physical intent of one placement. Replacement cards join the ordered
    * next-region discards or the edifice deck bottom after the kept card moves.
    */
  private final case class PlacementPlan(
      kept: Option[CoreOperation],
      favor: Vector[CoreOperation],
      discardedWorld: Vector[(WorldCardId, PositionedLocation)],
      discardedEdifices: Vector[(EdificeId, PositionedLocation)],
      startConspiracy: Option[DecisionId],
      tokenDenizen: Option[(DenizenId, Suit, Int, Int)] = None,
      tokenEdifice: Option[(EdificeId, Suit, Int, Int)] = None
  )

  /** Pure semantic operation plan shared with the walker card-play subtree. */
  def plannedOperations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, adviserLimit: Int = 3)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(catalog, player, card, placement, origin)
    plan <- plan(catalog, ready, player, card, placement, origin, adviserLimit)
    operations <- plannedOperations(catalog, ready, player, card, placement,
      origin, plan)
  } yield operations

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
      case Origin.TemporaryHand =>
        PositionedLocation(Location.Hand(player))
      case Origin.FacedownAdviser => PositionedLocation(Location.PlayArea(player))
    }

  private def validateOrigin(catalog: ExecutableCatalog, player: PlayerState,
      card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, Unit] = origin match {
    case Origin.TemporaryHand => Right(())
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
      origin: Origin, adviserLimit: Int)
      : Either[OathViolation, PlacementPlan] = placement match {
    case SearchPlacement.Discard => Right(PlacementPlan(
      None, Vector.empty, Vector.empty, Vector.empty, None))
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
          definition.suit, replace)
        sitePlan <- sitePlan(catalog, ready, origin, player, id, siteId,
          definition.suit, replacement)
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
        removed <- validateAdviserReplacement(catalog, remaining, replace,
          adviserLimit)
      } yield adviserPlan(origin, player, card, id, orientation, removed)
      case id: VisionId =>
        if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
        else planVision(catalog, ready, player, origin, card, id, orientation,
          replace, adviserLimit)
    }
  }

  private def sitePlan(catalog: ExecutableCatalog, ready: ReadyGame,
      origin: Origin, player: PlayerState, id: DenizenId, siteId: SiteId,
      suit: Suit, replacement: Option[SiteDenizenState])
      : Either[OathViolation, PlacementPlan] = {
    val from = keptSource(origin, player.player)
    val kept = Some(Play(id, from, Location.Site(siteId),
      Orientation.FaceUp, required = true))
    val gain = LimitedResource.clamp(ready.banks.favor.getOrElse(suit, 0), 1)
    val favor = if (gain == 1)
      Vector(Gain.Favor(player.player, suit, 1)) else Vector.empty
    val site = PositionedLocation(Location.Site(siteId))
    replacement match {
      case Some(value: DenizenState) if !value.tokens.isEmpty =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, Vector.empty, Vector.empty, None,
            tokenDenizen = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: DenizenState) =>
        Right(PlacementPlan(kept, favor,
          Vector((value.id: WorldCardId) -> site), Vector.empty, None))
      case Some(value: EdificeState) if !value.tokens.isEmpty =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, Vector.empty, Vector.empty, None,
            tokenEdifice = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: EdificeState) =>
        Right(PlacementPlan(kept, favor, Vector.empty,
          Vector(value.id -> site), None))
      case None =>
        Right(PlacementPlan(kept, favor, Vector.empty, Vector.empty, None))
    }
  }

  private def suitOf(catalog: ExecutableCatalog, id: CardId)
      : Either[OathViolation, Suit] =
    catalog.suitOf(id)
      .toRight(InvalidSearchPlacement("catalog suit is unknown"))

  private def adviserPlan(origin: Origin, player: PlayerState, card: WorldCardId,
      id: DenizenId, orientation: Orientation,
      removed: Option[WorldCardId]): PlacementPlan = {
    val from = keptSource(origin, player.player)
    val kept = Some(Play(id, from, Location.PlayArea(player.player),
      orientation, required = true))
    PlacementPlan(kept, Vector.empty,
      removed.toVector.map(value => value ->
        PositionedLocation(Location.PlayArea(player.player))),
      Vector.empty, None)
  }

  private def planVision(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, origin: Origin, card: WorldCardId, id: VisionId,
      orientation: Orientation, replace: Option[CardId], adviserLimit: Int)
      : Either[OathViolation, PlacementPlan] = origin match {
    case Origin.FacedownAdviser =>
      Either.cond(orientation == Orientation.FaceUp && replace.isEmpty, (),
        InvalidSearchPlacement(
          "facedown adviser must be played faceup to advisers or the pawn's site"))
        .map { _ =>
          val from = PositionedLocation(Location.PlayArea(player.player))
          PlacementPlan(
            Some(Play(id, from, Location.PlayArea(player.player),
              Orientation.FaceUp, required = true)),
            Vector.empty,
            player.revealedVision.toVector.map { value =>
              (value.id: WorldCardId) -> from
            },
            Vector.empty, None)
        }
    case Origin.TemporaryHand if orientation == Orientation.FaceUp &&
        id == VisionRules.Conspiracy =>
      MinorActionPowerSupport.validateFaceupVision(catalog, ready,
        player.player, id).map(_ => PlacementPlan(None, Vector.empty,
        Vector.empty, Vector.empty, Some(DecisionId(
          s"conspiracy-${player.player.value}-${id.value}"))))
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      MinorActionPowerSupport.validateFaceupVision(catalog, ready,
        player.player, id).flatMap { _ =>
        val expected = player.revealedVision.map(_.id)
        Either.cond(replace == expected, (), InvalidSearchPlacement(
          if (expected.nonEmpty) "a revealed Vision must be replaced"
          else "there is no revealed Vision to replace")).flatMap { _ =>
          val from = keptSource(origin, player.player)
          Right(PlacementPlan(
            Some(Play(id, from, Location.PlayArea(player.player),
              Orientation.FaceUp, required = true)),
            Vector.empty,
            expected.toVector.map(value => value ->
              PositionedLocation(Location.PlayArea(player.player))),
            Vector.empty, None))
        }
      }
    case Origin.TemporaryHand =>
      validateAdviserReplacement(catalog, player.advisers, replace,
        adviserLimit).map { removed =>
        val from = keptSource(origin, player.player)
        PlacementPlan(
          Some(Play(id, from, Location.PlayArea(player.player),
            Orientation.FaceDown, required = true)),
          Vector.empty,
          removed.toVector.map(value => value ->
            PositionedLocation(Location.PlayArea(player.player))),
          Vector.empty, None)
      }
  }

  private def plannedOperations(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState,
      card: WorldCardId, placement: SearchPlacement, origin: Origin,
      plan: PlacementPlan)
      : Either[OathViolation, Vector[CoreOperation]] = {
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
      secrets, player.player, required = true)
    val discardOps = destination.toVector.foldLeft[Either[OathViolation,
        Vector[CoreOperation]]](Right(Vector.empty)) { (acc, region) =>
      (plan.discardedWorld ++ playedDiscard).foldLeft(acc) {
        case (previous, (id, from)) => for {
          ops <- previous
          discard <- selectedDiscard(catalog, ready, player.player, id,
            from, region)
        } yield ops :+ discard
      }.map(_ ++ tokenDenizenOps)
    }
    val edificeOps = plan.discardedEdifices.map { case (id, from) =>
      Bury(BuryableCard.Edifice(id), from, required = true)
    } ++ plan.tokenEdifice.toVector.flatMap {
      case (id, replacedSuit, favor, secrets) =>
        player.pawnSite.toVector.map(siteId => Discard.RuinedEdifice(id,
          PositionedLocation(Location.Site(siteId)), replacedSuit, favor,
          secrets, player.player, required = true))
    }
    // Replacement-card removals run first so a kept card can
    // enter a vacated container (for example the revealed-Vision slot) in a
    // later staged operation.
    val handoff = (origin, plan.startConspiracy) match {
      case (Origin.TemporaryHand, Some(decision)) =>
        Vector(BeginConspiracy(player.player, decision, VisionRules.Conspiracy))
      case _ => Vector.empty
    }
    discardOps.map(ops => edificeOps ++ ops ++ plan.kept.toVector ++
      plan.favor ++ handoff)
  }

  private def selectedDiscard(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: PositionedLocation,
      destination: Region): Either[OathViolation, CoreOperation] = card match {
    case id: VisionId => Right(Discard.Vision(id, from, destination,
      required = true))
    case id: DenizenId =>
      val suit = catalog.suitOf(id).toRight(UnknownWorldCard(id))
      val held = ready.game.current.players.find(_.player == actor)
        .toVector.flatMap(_.advisers).collectFirst {
          case value: DenizenState if value.id == id => value.tokens
        }
      val site = from.location match {
        case Location.Site(siteId) => ready.game.current.map.sites.get(siteId)
          .toVector.flatMap(_.denizens).collectFirst {
            case value: DenizenState if value.id == id => value.tokens
          }
        case _ => None
      }
      val tokens = held.orElse(site).getOrElse(Tokens.empty)
      suit.map(value => Discard.Denizen(id, from, destination, value,
        tokens.favor, tokens.secrets, actor, required = true))
  }

  private def validateAdviserReplacement(catalog: ExecutableCatalog,
      advisers: Vector[AdviserState], replace: Option[CardId], limit: Int)
      : Either[OathViolation, Option[WorldCardId]] = {
    val mustReplace = advisers.size >= limit
    if (mustReplace != replace.nonEmpty) Left(InvalidSearchPlacement(
      if (mustReplace) "a full adviser area requires a discard"
      else "an adviser cannot be discarded when there is free capacity"))
    else replace match {
      case None => Right(None)
      case Some(id) => advisers.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement adviser is not held")).flatMap { _ =>
        val discardable = id match {
          case d: DenizenId => catalog.denizens.find(_.id.value == d.value)
            .toRight(UnknownWorldCard(d)).map(
              _.restrictions != CardRestrictions.LockedAdviserOnly)
          case _: VisionId => Right(true)
          case _ => Left(InvalidSearchPlacement(
            "replacement adviser is not a world card"))
        }
        discardable.flatMap { allowed =>
          if (!allowed) Left(LockedAdviserCannotBeDiscarded(id))
          else id match {
            case world: WorldCardId => Right(Some(world))
            case _ => Left(InvalidSearchPlacement(
              "replacement adviser is not a world card"))
          }
        }
      }
    }
  }

  private def validateSiteReplacement(catalog: ExecutableCatalog, siteId: SiteId,
      site: SiteState, suit: Suit, replace: Option[CardId])
      : Either[OathViolation, Option[SiteDenizenState]] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (!full && replace.isEmpty) Right(None)
    else if (!full) Left(InvalidSearchPlacement(
      "site replacement is allowed only at a full Homeland"))
    else {
      val homelandMatches = site.denizens.exists {
        case e: EdificeState => catalog.edifices.find(_.id.value == e.id.value)
          .exists(_.suit == suit)
        case _ => false
      }
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
