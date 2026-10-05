package oathdigital.gameplay.actions

import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, SiteOnly}
import oathdigital.gameplay.LimitedResource
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model.OathViolation._
import oathdigital.model._

/** The operations of one card play, planned from where the card goes.
  *
  * Which plays the rules allow is `cardplay.CardSlots`'s to say: it reads the
  * site discard permissions and the adviser limits. This refuses only what no
  * rule changes: a Vision or an adviser-only card at a site, a site-only card
  * faceup, a facedown adviser played facedown or replacing an adviser, a full
  * site with nothing discarded, and a discard of a card that is not there. It
  * plans the rest as core operations: the play, its favor, and every card it
  * discards.
  */
object CardPlay:
  enum Origin { case FacedownAdviser, TemporaryHand }

  /** The suit whose Homeland `site` is, as the site prints it (CR p. 31). */
  def homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit] =
    catalog.site(site).flatMap(_.homeland)

  /** The region whose discard pile takes a card `player` discards in a play:
    * the one after their pawn's. */
  def discardRegion(ready: ReadyGame, player: PlayerId): Option[Region] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.regionOf).map(nextRegion)

  /** The operations of playing `card` from `origin` to `placement`, which
    * also discards `more` from the actor's site. Site discards run first,
    * then play-area discards, then the play, so the played card can take a
    * slot they free; the favor comes last.
    */
  def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, more: Vector[CardId] = Vector.empty)
      : Either[OathViolation, Vector[CoreOperation]] = for
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(player, card, origin)
    plan <- plan(catalog, ready, player, card, placement, origin)
    extra <- atSite(ready, player, more)
    _ <- Either.cond(!extra.exists(plan.siteDiscards.contains), (),
      InvalidSearchPlacement("a site card is discarded twice"))
    operations <- operationsOf(catalog, ready, player, card, placement,
      origin, plan.copy(siteDiscards = plan.siteDiscards ++ extra))
  yield operations

  def playedSource(ready: ReadyGame, playerId: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Option[RuleSourceRef] = placement match
    case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
      Some(RuleSourceRef.Adviser(playerId, card))
    case SearchPlacement.Site(_) => ready.game.current.players
      .find(_.player == playerId).flatMap(_.pawnSite)
      .map(RuleSourceRef.SiteCard(_, card))
    case _ => None

  private def keptSource(origin: Origin, player: PlayerId): PositionedLocation =
    origin match
      case Origin.TemporaryHand =>
        PositionedLocation(Location.Hand(player))
      case Origin.FacedownAdviser => PositionedLocation(Location.PlayArea(player))

  private def validateOrigin(player: PlayerState, card: WorldCardId,
      origin: Origin): Either[OathViolation, Unit] = origin match
    case Origin.TemporaryHand => Right(())
    case Origin.FacedownAdviser =>
      player.advisers.find(_.id == card).filter {
        case DenizenState(_, Orientation.FaceDown, _) => true
        case VisionState(_, Orientation.FaceDown) => true
        case _ => false
      }.toRight(MinorActionUnavailable(
        "adviser is not held facedown by the actor")).map(_ => ())

  /** What one play moves: the card it keeps in play, the favor it gains, the
    * play-area cards it discards (a replaced adviser, or the revealed Vision a
    * new one displaces) and the site cards it discards. */
  private final case class PlacementPlan(kept: Option[CoreOperation],
      favor: Vector[CoreOperation], fromPlayArea: Vector[WorldCardId],
      siteDiscards: Vector[SiteDenizenState])

  private val nothing =
    PlacementPlan(None, Vector.empty, Vector.empty, Vector.empty)

  private def plan(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, PlacementPlan] = placement match
    case SearchPlacement.Discard => Right(nothing)
    case SearchPlacement.Site(replace) => card match
      case _: VisionId =>
        Left(InvalidSearchPlacement("Visions cannot be played to sites"))
      case id: DenizenId =>
        for
          definition <- catalog.denizen(id).toRight(UnknownWorldCard(id))
          _ <- Either.cond(!definition.isInstanceOf[AdviserOnly], (),
            InvalidSearchPlacement(
              "adviser-only card cannot be played to a site"))
          siteId <- player.pawnSite.toRight(PawnSiteMissing(player.player))
          site <- ready.game.current.map.sites.get(siteId)
            .toRight(InvalidSearchPlacement("pawn site is not in play"))
          replaced <- atSite(ready, player, replace.toVector)
          capacity = catalog.site(siteId).map(_.capacity).getOrElse(0)
          _ <- Either.cond(site.denizens.size - replaced.size < capacity, (),
            InvalidSearchPlacement("a full site requires a site-card discard"))
        yield sitePlan(ready, origin, player, id, siteId, definition.suit,
          replaced)
    case SearchPlacement.Adviser(orientation, replace) => card match
      case id: DenizenId =>
        for
          definition <- catalog.denizen(id).toRight(UnknownWorldCard(id))
          _ <- Either.cond(origin != Origin.FacedownAdviser ||
            (orientation == Orientation.FaceUp && replace.isEmpty), (),
            InvalidSearchPlacement(
              "facedown adviser must be played faceup to advisers or the pawn's site"))
          _ <- Either.cond(!(orientation == Orientation.FaceUp &&
            definition.isInstanceOf[SiteOnly]), (),
            InvalidSearchPlacement(
              "site-only card can only be held as a facedown adviser"))
          removed <- adviserHeld(player, replace)
        yield adviserPlan(origin, player, id, orientation, removed)
      case id: VisionId =>
        if !FirstGameRulesData.visions.contains(id) then Left(UnknownWorldCard(id))
        else planVision(player, origin, id, orientation, replace)

  private def sitePlan(ready: ReadyGame, origin: Origin, player: PlayerState,
      id: DenizenId, siteId: SiteId, suit: Suit,
      replaced: Vector[SiteDenizenState]): PlacementPlan =
    val kept = Some(Play(id, keptSource(origin, player.player),
      Location.Site(siteId), Orientation.FaceUp, required = true))
    val gain = LimitedResource.clamp(ready.banks.favor.getOrElse(suit, 0), 1)
    val favor = if gain == 1 then
      Vector(Gain.Favor(player.player, suit, 1)) else Vector.empty
    PlacementPlan(kept, favor, Vector.empty, replaced)

  private def adviserPlan(origin: Origin, player: PlayerState, id: DenizenId,
      orientation: Orientation, removed: Option[WorldCardId]): PlacementPlan =
    PlacementPlan(Some(Play(id, keptSource(origin, player.player),
      Location.PlayArea(player.player), orientation, required = true)),
      Vector.empty, removed.toVector, Vector.empty)

  private def planVision(player: PlayerState, origin: Origin, id: VisionId,
      orientation: Orientation, replace: Option[CardId])
      : Either[OathViolation, PlacementPlan] = origin match
    case _ if id == VisionRules.Conspiracy && orientation == Orientation.FaceUp =>
      // A played Conspiracy is boxed by its WHEN PLAYED power, so from either
      // origin it takes no slot and replaces nothing.
      Either.cond(replace.isEmpty, nothing,
        InvalidSearchPlacement("a played Conspiracy replaces nothing"))
    case Origin.FacedownAdviser =>
      Either.cond(orientation == Orientation.FaceUp && replace.isEmpty,
        revealed(player, origin, id), InvalidSearchPlacement(
          "facedown adviser must be played faceup to advisers or the pawn's site"))
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      // The displacement is forced: a player has at most one revealed Vision.
      // An answer that names it is still accepted.
      val expected = player.revealedVision.map(_.id)
      Either.cond(replace.isEmpty || replace == expected,
        revealed(player, origin, id),
        InvalidSearchPlacement("a Vision replaces only the revealed Vision"))
    case Origin.TemporaryHand =>
      adviserHeld(player, replace).map(removed => PlacementPlan(
        Some(Play(id, keptSource(origin, player.player),
          Location.PlayArea(player.player), Orientation.FaceDown,
          required = true)), Vector.empty, removed.toVector, Vector.empty))

  /** `id` played faceup as the revealed Vision, displacing the one there. */
  private def revealed(player: PlayerState, origin: Origin, id: VisionId)
      : PlacementPlan =
    PlacementPlan(Some(Play(id, keptSource(origin, player.player),
      Location.PlayArea(player.player), Orientation.FaceUp, required = true)),
      Vector.empty, player.revealedVision.toVector.map(_.id), Vector.empty)

  /** The adviser `replace` names, which the actor must hold. */
  private def adviserHeld(player: PlayerState, replace: Option[CardId])
      : Either[OathViolation, Option[WorldCardId]] =
    replace.fold[Either[OathViolation, Option[WorldCardId]]](Right(None)) {
      id => player.advisers.find(_.id == id)
        .toRight(InvalidSearchPlacement("replacement adviser is not held"))
        .flatMap(held => held.id match
          case world: WorldCardId => Right(Some(world))
          case _ => Left(InvalidSearchPlacement(
            "replacement adviser is not a world card")))
    }

  /** The cards `ids` names at the actor's site, in that order, each once. */
  private def atSite(ready: ReadyGame, player: PlayerState, ids: Vector[CardId])
      : Either[OathViolation, Vector[SiteDenizenState]] =
    val held = player.pawnSite.flatMap(ready.game.current.map.sites.get)
      .fold(Vector.empty[SiteDenizenState])(_.denizens)
    ids.foldLeft[Either[OathViolation, Vector[SiteDenizenState]]](
        Right(Vector.empty)) { (found, id) =>
      found.flatMap(cards => held.find(_.id == id).filterNot(cards.contains)
        .toRight(InvalidSearchPlacement(
          "a discarded card is not at the site, or is named twice"))
        .map(cards :+ _))
    }

  private def operationsOf(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, plan: PlacementPlan)
      : Either[OathViolation, Vector[CoreOperation]] =
    val playArea = PositionedLocation(Location.PlayArea(player.player))
    val played = if placement == SearchPlacement.Discard then
      Vector(card -> keptSource(origin, player.player)) else Vector.empty
    for
      siteId <- player.pawnSite.toRight(PawnSiteMissing(player.player))
      region <- ready.game.current.map.regionOf(siteId).map(nextRegion)
        .toRight(InvalidSearchPlacement("pawn site is not in play"))
      site = PositionedLocation(Location.Site(siteId))
      fromSite <- each(plan.siteDiscards)(
        siteDiscard(catalog, player.player, site, region))
      fromPlay <- each(plan.fromPlayArea.map(_ -> playArea) ++ played) {
        (id, from) => selectedDiscard(catalog, ready, player.player, id, from,
          region)
      }
    yield fromSite ++ fromPlay ++ plan.kept.toVector ++ plan.favor

  private def each[A](items: Vector[A])(
      discard: A => Either[OathViolation, CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    items.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
      Right(Vector.empty))((done, item) =>
      done.flatMap(operations => discard(item).map(operations :+ _)))

  /** A site card's discard: a denizen to `region`'s pile with its tokens
    * returned, an edifice back to the edifice deck. */
  private def siteDiscard(catalog: ExecutableCatalog, actor: PlayerId,
      site: PositionedLocation, region: Region)(card: SiteDenizenState)
      : Either[OathViolation, CoreOperation] =
    suitOf(catalog, card.id).map(suit => card match
      case denizen: DenizenState => Discard.Denizen(denizen.id, site, region,
        suit, denizen.tokens.favor, denizen.tokens.secrets, actor,
        required = true)
      case edifice: EdificeState => Discard.RuinedEdifice(edifice.id, site,
        suit, edifice.tokens.favor, edifice.tokens.secrets, actor,
        required = true))

  private def suitOf(catalog: ExecutableCatalog, id: CardId)
      : Either[OathViolation, Suit] =
    catalog.suitOf(id)
      .toRight(InvalidSearchPlacement("catalog suit is unknown"))

  private def selectedDiscard(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: PositionedLocation,
      destination: Region): Either[OathViolation, CoreOperation] = card match
    case id: VisionId => Right(Discard.Vision(id, from, destination,
      required = true))
    case id: DenizenId =>
      val suit = catalog.suitOf(id).toRight(UnknownWorldCard(id))
      val held = ready.game.current.players.find(_.player == actor)
        .toVector.flatMap(_.advisers).collectFirst:
          case value: DenizenState if value.id == id => value.tokens
      val tokens = held.getOrElse(Tokens.empty)
      suit.map(value => Discard.Denizen(id, from, destination, value,
        tokens.favor, tokens.secrets, actor, required = true))

  /** The region whose discard pile receives a card discarded at a site of
    * `region`.
    */
  def nextRegion(region: Region): Region = region match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
