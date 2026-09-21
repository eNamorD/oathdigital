package oathdigital.gameplay.actions

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.gameplay.operations.DiscardRestrictions
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

  /** One legal placement. `replacements` are the cards the play may discard
    * first. They are required when the placement is otherwise impossible, and
    * `replacementOptional` says the play is also legal with no discard (a
    * site with room, under `PlacementRules.siteDiscardFirst`).
    */
  final case class Choice(placement: SearchPlacement,
      replacements: Vector[CardId], replacementOptional: Boolean = false)

  def legalChoices(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: PlacementRules = PlacementRules.default): Vector[Choice] = {
    val player = ready.game.current.players.find(_.player == actor)
    val restriction = new DiscardRestrictions(catalog, actor)
    // A placement whose plan discards a card the actor may not discard (a
    // site protected by an enemy's intact Hall of Ministers) is not a choice.
    def permitted(operations: Vector[CoreOperation]): Boolean =
      operations.forall(restriction.reason(ready, _).isEmpty)
    val placements = Vector[SearchPlacement](SearchPlacement.Discard,
      SearchPlacement.Site(None),
      SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    placements.flatMap { placement =>
      val direct = plannedOperations(catalog, ready, actor, card,
        placement, origin, rules).exists(permitted)
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
      // A play to a site may be preceded by a discard even where it has room.
      val optional = direct && rules.siteDiscardFirst &&
        placement.isInstanceOf[SearchPlacement.Site]
      val replacements = if (direct && !optional) Vector.empty
      else candidateIds.filter { id =>
        val selected = placement match {
          case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
          case value: SearchPlacement.Adviser => value.copy(replace = Some(id))
          case SearchPlacement.Discard => SearchPlacement.Discard
        }
        plannedOperations(catalog, ready, actor, card, selected,
          origin, rules).exists(permitted)
      }
      Option.when(direct || replacements.nonEmpty)(Choice(placement,
        replacements, replacementOptional = optional && replacements.nonEmpty))
    }
  }

  /** Physical intent of one placement. Replacement cards join the ordered
    * next-region discards or the edifice deck bottom after the kept card moves.
    */
  private final case class PlacementPlan(
      kept: Option[CoreOperation],
      favor: Vector[CoreOperation],
      discardedWorld: Vector[(WorldCardId, PositionedLocation)],
      // Always empty: a replaced edifice is `tokenEdifice`, a discard.
      discardedEdifices: Vector[(EdificeId, PositionedLocation)],
      tokenDenizen: Option[(DenizenId, Suit, Int, Int)] = None,
      tokenEdifice: Option[(EdificeId, Suit, Int, Int)] = None
  )

  /** Pure semantic operation plan shared with the walker card-play subtree. */
  def plannedOperations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, rules: PlacementRules = PlacementRules.default)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(player, card, origin)
    plan <- plan(catalog, ready, player, card, placement, origin, rules)
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

  private def validateOrigin(player: PlayerState, card: WorldCardId,
      origin: Origin): Either[OathViolation, Unit] = origin match {
    case Origin.TemporaryHand => Right(())
    case Origin.FacedownAdviser =>
      player.advisers.find(_.id == card).filter {
        case DenizenState(_, Orientation.FaceDown, _) => true
        case VisionState(_, Orientation.FaceDown) => true
        case _ => false
      }.toRight(MinorActionUnavailable(
        "adviser is not held facedown by the actor")).map(_ => ())
  }

  private def plan(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, rules: PlacementRules)
      : Either[OathViolation, PlacementPlan] = placement match {
    case SearchPlacement.Discard => Right(PlacementPlan(
      None, Vector.empty, Vector.empty, Vector.empty))
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
          definition.suit, replace, rules)
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
          rules.adviserLimit(orientation))
      } yield adviserPlan(origin, player, card, id, orientation, removed)
      case id: VisionId =>
        if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
        else planVision(catalog, player, origin, id, orientation, replace,
          rules.adviserLimit(orientation))
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
          PlacementPlan(kept, favor, Vector.empty, Vector.empty,
            tokenDenizen = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: DenizenState) =>
        Right(PlacementPlan(kept, favor,
          Vector((value.id: WorldCardId) -> site), Vector.empty))
      case Some(value: EdificeState) =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, Vector.empty, Vector.empty,
            tokenEdifice = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case None =>
        Right(PlacementPlan(kept, favor, Vector.empty, Vector.empty))
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
      Vector.empty)
  }

  private def planVision(catalog: ExecutableCatalog, player: PlayerState,
      origin: Origin, id: VisionId, orientation: Orientation,
      replace: Option[CardId], adviserLimit: Int)
      : Either[OathViolation, PlacementPlan] = origin match {
    case _ if id == VisionRules.Conspiracy && orientation == Orientation.FaceUp =>
      // A played Conspiracy is boxed by its WHEN PLAYED power, so from either
      // origin it takes no slot and replaces nothing.
      Either.cond(replace.isEmpty,
        PlacementPlan(None, Vector.empty, Vector.empty, Vector.empty),
        InvalidSearchPlacement("a played Conspiracy replaces nothing"))
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
            Vector.empty)
        }
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      val expected = player.revealedVision.map(_.id)
      Either.cond(replace == expected, (), InvalidSearchPlacement(
        if (expected.nonEmpty) "a revealed Vision must be replaced"
        else "there is no revealed Vision to replace")).map { _ =>
        val from = keptSource(origin, player.player)
        PlacementPlan(
          Some(Play(id, from, Location.PlayArea(player.player),
            Orientation.FaceUp, required = true)),
          Vector.empty,
          expected.toVector.map(value => value ->
            PositionedLocation(Location.PlayArea(player.player))),
          Vector.empty)
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
          Vector.empty)
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
    val edificeOps = plan.tokenEdifice.toVector.flatMap {
      case (id, replacedSuit, favor, secrets) =>
        player.pawnSite.toVector.map(siteId => Discard.RuinedEdifice(id,
          PositionedLocation(Location.Site(siteId)), replacedSuit, favor,
          secrets, player.player, required = true))
    }
    // Replacement-card removals run first so a kept card can
    // enter a vacated container (for example the revealed-Vision slot) in a
    // later staged operation.
    discardOps.map(ops => edificeOps ++ ops ++ plan.kept.toVector ++
      plan.favor)
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
      site: SiteState, suit: Suit, replace: Option[CardId],
      rules: PlacementRules)
      : Either[OathViolation, Option[SiteDenizenState]] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (rules.siteDiscardFirst) replace match {
      // Any site, at any capacity: the discard is optional with room and
      // required without, and it names a denizen of the site's card list
      // (Mob's printed text; an edifice is not a denizen). `DiscardRestrictions`
      // decide which denizen may actually go: a locked card and an active
      // modifier may not.
      case None if full => Left(InvalidSearchPlacement(
        "a full site requires a site-card discard"))
      case None => Right(None)
      case Some(id) => site.denizens.collectFirst {
        case denizen: DenizenState if denizen.id == id => denizen
      }.toRight(InvalidSearchPlacement("replacement card is not a denizen at the site"))
        .map(Some(_))
    }
    else if (!full && replace.isEmpty) Right(None)
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

  /** The region whose discard pile receives a card discarded at a site of
    * `region`.
    */
  def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
}
