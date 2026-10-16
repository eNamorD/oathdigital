package oathdigital.gameplay.actions

import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, SiteOnly}
import oathdigital.gameplay.{EffectiveSuit, LimitedResource}
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model.OathViolation._
import oathdigital.model._

/** The operations of one card play, planned from where the card goes.
  *
  * Which plays the rules allow is `cardplay.CardSlots`'s to say: it reads the
  * site discard permissions and the adviser limits. This refuses only what no
  * rule changes: a Vision or an adviser-only card at a site, a site-only card
  * faceup, a facedown adviser played facedown or replacing an adviser, a full
  * site with nothing discarded, and a discard or bury of a card that is not
  * at the site played to. It plans the rest as core operations: the play,
  * its favor, and every card it discards or buries. A site card discarded
  * in a play goes to the pile after the site's region; a play-area card to
  * the pile after the pawn's.
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
    * also discards `more` from the site played to and buries `buried`
    * there: the replaced card, or another card or relic at the site. Site
    * removals run first, then play-area discards, then the play, so the
    * played card can take a slot they free; the favor comes last.
    */
  def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, more: Vector[CardId] = Vector.empty,
      buried: Option[CardId] = None)
      : Either[OathViolation, Vector[CoreOperation]] = for
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(player, card, origin)
    plan <- plan(catalog, ready, player, card, placement, origin)
    site = placement match
      case SearchPlacement.Site(at, _) => Some(at)
      case _ => None
    extra <- rowAt(ready, site, more)
    _ <- Either.cond(!extra.exists(e => plan.fromSite.exists(_.card ==
      e.card)), (), InvalidSearchPlacement("a site card is discarded twice"))
    bury <- buried.filterNot(id => plan.fromSite.exists(_.card == id))
      .fold[Either[OathViolation, Option[Removed]]](Right(None))(id =>
        buryableAt(ready, site, id).map(Some(_)))
    _ <- Either.cond(!bury.exists(b => extra.exists(_.card == b.card)), (),
      InvalidSearchPlacement("a site card is both discarded and buried"))
    removed = plan.fromSite.map(r =>
      if buried.contains(r.card) then r.copy(buried = true) else r) ++
      extra ++ bury.toVector
    operations <- operationsOf(catalog, ready, player, card, placement,
      origin, plan.copy(fromSite = removed))
  yield operations

  def playedSource(ready: ReadyGame, playerId: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Option[RuleSourceRef] = placement match
    case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
      Some(RuleSourceRef.Adviser(playerId, card))
    case SearchPlacement.Site(site, _) =>
      Some(RuleSourceRef.SiteCard(site, card))
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
    * new one displaces) and the cards it removes from the site played to. */
  private final case class PlacementPlan(kept: Option[CoreOperation],
      favor: Vector[CoreOperation], fromPlayArea: Vector[WorldCardId],
      fromSite: Vector[Removed])

  /** A card at the play's site the play removes first, with what it
    * carries: discarded, or buried when `buried`. */
  private final case class Removed(card: CardId, tokens: Tokens,
      buried: Boolean)

  private val nothing =
    PlacementPlan(None, Vector.empty, Vector.empty, Vector.empty)

  private def plan(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, PlacementPlan] = placement match
    case SearchPlacement.Discard => Right(nothing)
    case SearchPlacement.Site(siteId, replace) => card match
      case _: VisionId =>
        Left(InvalidSearchPlacement("Visions cannot be played to sites"))
      case id: DenizenId =>
        for
          definition <- catalog.denizen(id).toRight(UnknownWorldCard(id))
          _ <- Either.cond(!definition.isInstanceOf[AdviserOnly], (),
            InvalidSearchPlacement(
              "adviser-only card cannot be played to a site"))
          site <- ready.game.current.map.sites.get(siteId)
            .filter(_ => ready.game.current.map.inPlay.contains(siteId))
            .toRight(InvalidSearchPlacement("the site is not in play"))
          replaced <- rowAt(ready, Some(siteId), replace.toVector)
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
      replaced: Vector[Removed]): PlacementPlan =
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

  /** The cards `ids` names in `site`'s row, in that order, each once, to
    * be discarded. With no site, nothing may be named. */
  private def rowAt(ready: ReadyGame, site: Option[SiteId],
      ids: Vector[CardId]): Either[OathViolation, Vector[Removed]] =
    site match
      case None => Either.cond(ids.isEmpty, Vector.empty,
        InvalidSearchPlacement("nothing to discard: the play is not to a " +
          "site"))
      case Some(at) =>
        val held = ready.game.current.map.sites.get(at)
          .fold(Vector.empty[SiteDenizenState])(_.denizens)
        ids.foldLeft[Either[OathViolation, Vector[Removed]]](
            Right(Vector.empty)) { (found, id) =>
          found.flatMap(cards => held.find(_.id == id)
            .filterNot(c => cards.exists(_.card == c.id))
            .toRight(InvalidSearchPlacement(
              "a discarded card is not at the site, or is named twice"))
            .map(c => cards :+ Removed(c.id, c.tokens, buried = false)))
        }

  /** The card `id` names at `site`, a row card or a relic there, to be
    * buried. */
  private def buryableAt(ready: ReadyGame, site: Option[SiteId], id: CardId)
      : Either[OathViolation, Removed] =
    val state = site.flatMap(ready.game.current.map.sites.get)
    state.flatMap(at => at.denizens.find(_.id == id).map(_.tokens)
      .orElse(at.relics.find(_.id == id).map(_.tokens)))
      .map(Removed(id, _, buried = true))
      .toRight(InvalidSearchPlacement("a buried card is not at the site"))

  private def operationsOf(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, plan: PlacementPlan)
      : Either[OathViolation, Vector[CoreOperation]] =
    val playArea = PositionedLocation(Location.PlayArea(player.player))
    val played = if placement == SearchPlacement.Discard then
      Vector(card -> keptSource(origin, player.player)) else Vector.empty
    val target = placement match
      case SearchPlacement.Site(at, _) => Some(at)
      case _ => None
    for
      pawnSite <- player.pawnSite.toRight(PawnSiteMissing(player.player))
      region <- ready.game.current.map.regionOf(pawnSite).map(nextRegion)
        .toRight(InvalidSearchPlacement("pawn site is not in play"))
      fromSite <- target.fold[Either[OathViolation, Vector[CoreOperation]]](
          Right(Vector.empty)) { siteId =>
        for
          pile <- ready.game.current.map.regionOf(siteId).map(nextRegion)
            .toRight(InvalidSearchPlacement("the site is not in play"))
          removed <- each(plan.fromSite)(removal(catalog, player.player,
            PositionedLocation(Location.Site(siteId)), pile))
        yield removed.flatten
      }
      fromPlay <- each(plan.fromPlayArea.map(_ -> playArea) ++ played) {
        (id, from) => selectedDiscard(catalog, ready, player.player, id, from,
          region)
      }
    yield fromSite ++ fromPlay ++ plan.kept.toVector ++ plan.favor

  private def each[A, B](items: Vector[A])(
      f: A => Either[OathViolation, B]): Either[OathViolation, Vector[B]] =
    items.foldLeft[Either[OathViolation, Vector[B]]](
      Right(Vector.empty))((done, item) =>
      done.flatMap(operations => f(item).map(operations :+ _)))

  /** A card the play removes from the site at `from`, its row or its
    * relics: a row card discarded to `region`'s pile with its tokens
    * returned (a ruined edifice back to its deck), or any card there buried
    * with them returned. Favor returns to its suit's bank, so a card with
    * favor and no suit is refused rather than buried. */
  private def removal(catalog: ExecutableCatalog, actor: PlayerId,
      from: PositionedLocation, region: Region)(removed: Removed)
      : Either[OathViolation, Vector[CoreOperation]] =
    val Removed(card, tokens, buried) = removed
    val suit = catalog.suitOf(card)
    if buried then for
      target <- buryable(card)
      _ <- Either.cond(tokens.favor == 0 || suit.isDefined, (),
        InvalidSearchPlacement("buried favor needs its suit's bank"))
    yield Bury.standard(target, from, suit, tokens.favor, tokens.secrets,
      actor)
    else suitOf(catalog, card).flatMap(suit => card match
      case id: DenizenId => Right(Vector(Discard.Denizen(id, from, region,
        suit, tokens.favor, tokens.secrets, actor, required = true)))
      case id: EdificeId => Right(Vector(Discard.RuinedEdifice(id, from,
        suit, tokens.favor, tokens.secrets, actor, required = true)))
      case _ => Left(InvalidSearchPlacement(
        "only a card of the site's row is discarded")))

  private def buryable(card: CardId): Either[OathViolation, BuryableCard] =
    card match
      case id: DenizenId => Right(BuryableCard.Denizen(id))
      case id: EdificeId => Right(BuryableCard.Edifice(id))
      case id: RelicId => Right(BuryableCard.Relic(id))
      case _ => Left(InvalidSearchPlacement("the card cannot be buried"))

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
      // The replaced adviser is read before it leaves.
      val suit = EffectiveSuit.of(catalog, ready, id)
        .toRight(UnknownWorldCard(id))
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
