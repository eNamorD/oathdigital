package oathdigital.gameplay.operations

import oathdigital.gameplay.ReadyGame
import oathdigital.model._

/** Internal immutable mutation planner for counted pieces and primitive effects. */
private[operations] object OperationStateMutation {
  import OperationError._
  import OperationStateAdapter._

  /** Applies a validated operation's leaves. Shape/allowlist checks are
    * owned by OperationShape/OperationValidator and run by OperationPipeline
    * before this object is reached; the remaining Either guards below are
    * mutation-time defenses that only fire if validation drifted.
    */
  private[operations] def applyOperation(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    mutate(ready, operation)

  private[operations] final case class CardTransfer(
      piece: Piece.Card,
      from: PositionedLocation,
      to: PositionedLocation,
      resultingOrientation: Option[Orientation]
  )

  private[operations] def cardTransfers(
      leaves: Vector[Operation]
  ): Vector[CardTransfer] = leaves.collect {
    case Move(piece: Piece.Card, from, to, orientation) =>
      CardTransfer(piece, from, to, orientation)
    case bury: Bury => CardTransfer(
      Piece.Card(bury.card.id),
      bury.from,
      bury.to,
      resultingOrientation = None
    )
  }

  private[operations] def sequence[A](
      values: Vector[Either[OperationError, A]]
  ): Either[OperationError, Vector[A]] =
    values.foldLeft[Either[OperationError, Vector[A]]](Right(Vector.empty)) {
      case (result, value) => for {
        accumulated <- result
        next <- value
      } yield accumulated :+ next
    }

  private def mutate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] = {
    val leaves = Operation.flatten(operation)
    for {
      resources <- applyCountedMoves(ready, leaves)
      pieces <- applyPawnAndBannerMoves(resources, leaves)
      cards <- OperationCardMutation.applyCardMoves(pieces, leaves)
      finished <- applyNonMoveLeaves(cards, leaves)
    } yield finished
  }

  private def applyCountedMoves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] = {
    val favorMoves = leaves.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = leaves.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = leaves.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    for {
      plannedSecrets <- OperationSecretPlanner.plan(ready, secretMoves)
      withoutFavor <- favorMoves.foldLeft[Either[OperationError, ReadyGame]](
        Right(ready)) { (result, move) =>
        val piece = move.piece.asInstanceOf[Piece.Favor]
        result.flatMap(adjustFavor(_, move.from.location, -piece.amount))
      }
      withFavor <- favorMoves.foldLeft[Either[OperationError, ReadyGame]](
        Right(withoutFavor)) { (result, move) =>
        val piece = move.piece.asInstanceOf[Piece.Favor]
        result.flatMap(adjustFavor(_, move.to.location, piece.amount))
      }
      withoutSecrets <- plannedSecrets.foldLeft[
        Either[OperationError, ReadyGame]](Right(withFavor)) {
        case (result, (move, split)) => result.flatMap(adjustSecrets(
          _, move.from.location, -split.faceUp, -split.faceDown))
      }
      withSecrets <- plannedSecrets.foldLeft[
        Either[OperationError, ReadyGame]](Right(withoutSecrets)) {
        case (result, (move, split)) => result.flatMap(adjustSecrets(
          _, move.to.location, split.faceUp, split.faceDown))
      }
      withoutWarbands <- warbandMoves.foldLeft[
        Either[OperationError, ReadyGame]](Right(withSecrets)) {
        case (result, move) =>
          val piece = move.piece.asInstanceOf[Piece.Warbands]
          result.flatMap(adjustWarbands(_, piece.kind, move.from.location,
            -piece.amount))
      }
      withWarbands <- warbandMoves.foldLeft[
        Either[OperationError, ReadyGame]](Right(withoutWarbands)) {
        case (result, move) =>
          val piece = move.piece.asInstanceOf[Piece.Warbands]
          result.flatMap(adjustWarbands(_, piece.kind, move.to.location,
            piece.amount))
      }
    } yield withWarbands
  }

  private def adjustFavor(
      ready: ReadyGame,
      at: Location,
      delta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(favor = state.board.favor + delta))
    }
    case Location.Site(site) => updateSite(ready, site) { state =>
      state.copy(tokens = state.tokens.copy(favor = state.tokens.favor + delta))
    }
    case Location.OnCard(id) => updateCardTokens(ready, id) { tokens =>
      tokens.copy(favor = tokens.favor + delta)
    }
    case Location.OnBanner(Banner.PeoplesFavor) => Right(ready.copy(
      game = ready.game.copy(current = ready.game.current.copy(
        banners = ready.game.current.banners.copy(
          peoplesFavor = ready.game.current.banners.peoplesFavor.copy(
            favor = ready.game.current.banners.peoplesFavor.favor + delta
          )
        )
      ))
    ))
    case Location.FavorBank(suit) => Right(ready.copy(
      banks = ready.banks.copy(favor = ready.banks.favor.updated(
        suit,
        ready.banks.favor.getOrElse(suit, 0) + delta
      ))
    ))
    case Location.SharedBank if delta > 0 => Right(ready)
    case _ => Left(IncompatibleLocation(Piece.Favor(math.max(1, math.abs(delta))), at))
  }

  private def adjustSecrets(
      ready: ReadyGame,
      at: Location,
      faceUpDelta: Int,
      faceDownDelta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(
        faceUpSecrets = state.board.faceUpSecrets + faceUpDelta,
        faceDownSecrets = state.board.faceDownSecrets + faceDownDelta
      ))
    }
    case Location.Site(site) if faceDownDelta == 0 => updateSite(ready, site) {
      state => state.copy(tokens = state.tokens.copy(
        secrets = state.tokens.secrets + faceUpDelta))
    }
    case Location.OnCard(id) if faceDownDelta == 0 =>
      updateCardTokens(ready, id) { tokens =>
        tokens.copy(secrets = tokens.secrets + faceUpDelta)
      }
    case Location.OnBanner(Banner.DarkestSecret) if faceDownDelta == 0 =>
      Right(ready.copy(game = ready.game.copy(current = ready.game.current.copy(
        banners = ready.game.current.banners.copy(
          darkestSecret = ready.game.current.banners.darkestSecret.copy(
            secrets = ready.game.current.banners.darkestSecret.secrets + faceUpDelta
          )
        )
      ))))
    case Location.SharedBank => Right(ready)
    case _ => Left(IncompatibleLocation(Piece.Secrets(
      math.max(1, math.abs(faceUpDelta) + math.abs(faceDownDelta))), at))
  }

  private def adjustWarbands(
      ready: ReadyGame,
      kind: ForceKind,
      at: Location,
      delta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.WarbandBank(`kind`) => Right(ready)
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(warbands = state.board.warbands + delta))
    }.flatMap { updated =>
      quantity(updated, Piece.Warbands(kind, 1), Location.PlayArea(player))
        .map(_ => updated)
    }
    case Location.Site(site) => siteState(ready, site).flatMap { state =>
      state.forces match {
        case SiteForces.Occupied(other, _) if other != kind =>
          Left(ConflictingDeltas("site cannot contain multiple force kinds"))
        case forces =>
          val current = forces match {
            case SiteForces.Empty => 0
            case SiteForces.Occupied(_, count) => count
          }
          val next = current + delta
          Either.cond(next >= 0, (), InsufficientPieces(
            Piece.Warbands(kind, math.max(1, -delta)), at, current)).flatMap { _ =>
            updateSite(ready, site)(_.copy(forces =
              if (next == 0) SiteForces.Empty
              else SiteForces.Occupied(kind, next)))
          }
      }
    }
    case _ => Left(IncompatibleLocation(Piece.Warbands(kind, math.max(1,
      math.abs(delta))), at))
  }

  private def requireFinite(
      piece: Piece,
      location: Location,
      available: AvailableQuantity,
      requested: Int
  ): Either[OperationError, Unit] = available match {
    case AvailableQuantity.Unbounded => Right(())
    case AvailableQuantity.Finite(value) => Either.cond(
      value >= requested,
      (),
      InsufficientPieces(piece, location, value)
    )
  }

  private def applyPawnAndBannerMoves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Move(Piece.Pawn(player), from, to, _)) =>
        result.flatMap(movePawn(_, player, from.location, to.location))
      case (result, Move(Piece.Banner(banner), from, to, _)) =>
        result.flatMap(moveBanner(_, banner, from.location, to.location))
      case (result, _) => result
    }

  private def movePawn(ready: ReadyGame, player: PlayerId,
      from: Location, to: Location): Either[OperationError, ReadyGame] =
    (from, to) match {
      case (Location.Site(source), Location.Site(destination)) =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.contains(source), (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case _ => Left(IncompatibleLocation(Piece.Pawn(player), to))
    }

  private def moveBanner(ready: ReadyGame, banner: Banner,
      from: Location, to: Location): Either[OperationError, ReadyGame] =
    (from, to) match {
      case (Location.PlayArea(source), Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          _ <- Either.cond(bannerHolder(ready, banner).contains(source), (),
            MissingPiece(Piece.Banner(banner), from))
        } yield setBannerHolder(ready, banner, destination)
      case (Location.SharedBank, Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          available <- quantity(ready, Piece.Banner(banner), from)
          _ <- requireFinite(Piece.Banner(banner), from, available, 1)
        } yield setBannerHolder(ready, banner, destination)
      case _ => Left(IncompatibleLocation(Piece.Banner(banner), to))
    }

  private def setBannerHolder(ready: ReadyGame, banner: Banner,
      player: PlayerId): ReadyGame = {
    val current = ready.game.current
    val banners = banner match {
      case Banner.PeoplesFavor => current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = Some(player)))
      case Banner.DarkestSecret => current.banners.copy(
        darkestSecret = current.banners.darkestSecret.copy(holder = Some(player)))
    }
    ready.copy(game = ready.game.copy(current = current.copy(banners = banners)))
  }

  private def applyNonMoveLeaves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(flipCard(_, id, at, orientation))
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(flipPlayerSecrets(_, player, amount, from, to))
      case (result, Peek(viewer, id, at)) =>
        result.flatMap(peek(_, viewer, id, at))
      case (result, AdjustSupply(player, amount)) =>
        result.flatMap(adjustSupply(_, player, amount))
      case (result, ModifyDicePool(pool, delta, _)) =>
        result.flatMap(adjustDicePool(_, pool, delta))
      case (result, _) => result
    }

  private def adjustSupply(ready: ReadyGame, player: PlayerId,
      amount: Int): Either[OperationError, ReadyGame] =
    playerState(ready, player).flatMap { state =>
      val current = state.board.supply.supply
      if (amount < 0) {
        val required = -amount
        Either.cond(current >= required, (), InsufficientSupply(
          required, current)).flatMap { _ =>
          updatePlayer(ready, player)(value => value.copy(
            board = value.board.copy(supply = SupplyTrack(current - required))))
        }
      } else
        updatePlayer(ready, player)(value => value.copy(
          board = value.board.copy(supply = SupplyTrack(math.min(
            SupplyTrack.Maximum, current + amount)))))
    }

  /** Adds `delta` dice to a named pool's count in `rollPools` state. The
    * count must not go below zero; this slice's trees only raise it (the
    * defensive guard below is a mutation-time floor — shape-level dice-pool
    * validation belongs to a later task that defines underflow semantics).
    */
  private def adjustDicePool(ready: ReadyGame, pool: PoolKey,
      delta: Int): Either[OperationError, ReadyGame] = {
    val pools = ready.game.current.rollPools
    val current = pools.get(pool).fold(0)(_.count)
    val next = current + delta
    require(next >= 0,
      s"dice pool '${pool.value}' count must not go below zero")
    Right(updateCurrent(ready)(state => state.copy(
      rollPools = pools.updated(pool, DicePoolState(next)))))
  }

  private def flipCard(ready: ReadyGame, id: CardId, at: Location,
      orientation: Orientation): Either[OperationError, ReadyGame] = for {
    located <- card(ready, id, at)
    updated <- located.state match {
      case Some(_: DenizenState) | Some(_: VisionState) | Some(_: RelicState) =>
        updateCardState(ready, id) {
          case value: DenizenState => value.copy(orientation = orientation)
          case value: VisionState => value.copy(orientation = orientation)
          case value: RelicState => value.copy(orientation = orientation)
          case value => value
        }
      case _ => Left(UnsupportedOrientation(id, at))
    }
  } yield updated

  private def flipPlayerSecrets(ready: ReadyGame, player: PlayerId, amount: Int,
      from: SecretSide, to: SecretSide): Either[OperationError, ReadyGame] =
    playerState(ready, player).flatMap { state =>
      val available = from match {
        case SecretSide.FaceUp => state.board.faceUpSecrets
        case SecretSide.FaceDown => state.board.faceDownSecrets
      }
      Either.cond(available >= amount, (), InsufficientPieces(
        Piece.Secrets(amount), Location.PlayArea(player), available)).flatMap { _ =>
        updatePlayer(ready, player) { value =>
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          value.copy(board = value.board.copy(
            faceUpSecrets = value.board.faceUpSecrets + faceUpDelta,
            faceDownSecrets = value.board.faceDownSecrets - faceUpDelta))
        }
      }
    }

  private def peek(ready: ReadyGame, viewer: PlayerId, id: CardId,
      at: Location): Either[OperationError, ReadyGame] = for {
    _ <- playerState(ready, viewer)
    _ <- Either.cond(id.isInstanceOf[WorldCardId] || id.isInstanceOf[RelicId],
      (), IncompatibleLocation(Piece.Card(id), at))
    located <- card(ready, id, at)
  } yield located.location.container match {
    case CardContainer.Site(site, SiteCardArea.Relics) =>
      recordSiteRelicKnowledge(ready, viewer, site,
        id.asInstanceOf[RelicId])
    case CardContainer.AtlasSite(_, site, SiteCardArea.Relics) =>
      recordSiteRelicKnowledge(ready, viewer, site,
        id.asInstanceOf[RelicId])
    case _ if id.isInstanceOf[RelicId] => ready.copy(knowledge =
      ready.knowledge.copy(heldRelics = ready.knowledge.heldRelics.updated(viewer,
        appendDistinct(ready.knowledge.heldRelics.getOrElse(viewer, Vector.empty),
          id.asInstanceOf[RelicId]))))
    case _ if id.isInstanceOf[WorldCardId] => ready.copy(knowledge =
      ready.knowledge.copy(advisers = ready.knowledge.advisers.updated(viewer,
        appendDistinct(ready.knowledge.advisers.getOrElse(viewer, Vector.empty),
          id.asInstanceOf[WorldCardId]))))
    case _ => ready
  }

  private def appendDistinct[A](values: Vector[A], value: A): Vector[A] =
    if (values.contains(value)) values else values :+ value

  private def recordSiteRelicKnowledge(
      ready: ReadyGame,
      viewer: PlayerId,
      site: SiteId,
      relic: RelicId
  ): ReadyGame = {
    val sites = ready.knowledge.siteRelics.getOrElse(viewer, Map.empty)
    ready.copy(knowledge = ready.knowledge.copy(siteRelics =
      ready.knowledge.siteRelics.updated(viewer, sites.updated(site,
        appendDistinct(sites.getOrElse(site, Vector.empty), relic)))))
  }

  private[operations] def updateCurrent(ready: ReadyGame)(
      f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))

  private[operations] def updateCommonCards(ready: ReadyGame)(
      f: CardZones => CardZones): Either[OperationError, ReadyGame] =
    Right(updateCurrent(ready)(current =>
      current.copy(commonCards = f(current.commonCards))))

  private[operations] def updatePlayer(ready: ReadyGame, player: PlayerId)(
      f: PlayerState => PlayerState): Either[OperationError, ReadyGame] =
    playerState(ready, player).map { _ => updateCurrent(ready) { current =>
      current.copy(players = current.players.map(value =>
        if (value.player == player) f(value) else value))
    }}

  private[operations] def updateSite(ready: ReadyGame, site: SiteId)(
      f: SiteState => SiteState): Either[OperationError, ReadyGame] =
    siteState(ready, site).map { state => updateCurrent(ready) { current =>
      current.copy(map = current.map.copy(sites =
        current.map.sites.updated(site, f(state))))
    }}

  private def updateCardTokens(ready: ReadyGame, id: CardId)(
      f: Tokens => Tokens): Either[OperationError, ReadyGame] =
    updateCardState(ready, id) {
      case value: DenizenState => value.copy(tokens = f(value.tokens))
      case value: EdificeState => value.copy(tokens = f(value.tokens))
      case value: RelicState => value.copy(tokens = f(value.tokens))
      case value => value
    }

  private def updateCardState(ready: ReadyGame, id: CardId)(
      f: CardState => CardState): Either[OperationError, ReadyGame] =
    CardIndex.from(ready.game).left.map(InvalidCardIndex).flatMap { index =>
      index.get(id).toRight(UnknownCard(id)).flatMap { located =>
        located.state.toRight(IncompatibleLocation(Piece.Card(id),
          Location.OnCard(id))).flatMap { _ =>
          located.location.container match {
            case CardContainer.Player(player, PlayerCardArea.Advisers) =>
              updatePlayer(ready, player)(state => state.copy(advisers =
                state.advisers.map(value => if (value.id == id)
                  f(value).asInstanceOf[AdviserState] else value)))
            case CardContainer.Player(player, PlayerCardArea.Relics) =>
              updatePlayer(ready, player)(state => state.copy(relics =
                state.relics.map(value => if (value.id == id)
                  f(value).asInstanceOf[RelicState] else value)))
            case CardContainer.Player(player, PlayerCardArea.RevealedVision) =>
              updatePlayer(ready, player)(state => state.copy(revealedVision =
                state.revealedVision.map(value =>
                  f(value).asInstanceOf[VisionState])))
            case CardContainer.Site(site, SiteCardArea.Denizens) =>
              updateSite(ready, site)(state => state.copy(denizens =
                state.denizens.map(value => if (value.id == id)
                  f(value).asInstanceOf[SiteDenizenState] else value)))
            case CardContainer.Site(site, SiteCardArea.Relics) =>
              updateSite(ready, site)(state => state.copy(relics =
                state.relics.map(value => if (value.id == id)
                  f(value).asInstanceOf[RelicState] else value)))
            case atlas: CardContainer.AtlasSite =>
              updateAtlasCardState(ready, atlas, id, f)
            case _ => Left(IncompatibleLocation(Piece.Card(id), Location.OnCard(id)))
          }
        }
      }
    }

  private def updateAtlasCardState(
      ready: ReadyGame,
      container: CardContainer.AtlasSite,
      id: CardId,
      f: CardState => CardState
  ): Either[OperationError, ReadyGame] = {
    val entries = ready.game.campaign.atlas.entries
    entries.lift(container.atlasPosition).collect {
      case stored: AtlasEntry.StoredSite => stored
    }.toRight(AmbiguousLocation(Location.Atlas,
      "stored Atlas site is unavailable")).flatMap { stored =>
      val updated = container.area match {
        case SiteCardArea.Denizens =>
          stored.denizens.find(_.id == id).toRight(UnknownCard(id)).flatMap {
            existing => f(existing) match {
              case next: SiteDenizenState => Right(stored.copy(denizens =
                stored.denizens.map(value =>
                  if (value.id == id) next else value)))
              case _ => Left(IncompatibleLocation(
                Piece.Card(id), Location.OnCard(id)))
            }
          }
        case SiteCardArea.Relics =>
          stored.relics.find(_.id == id).toRight(UnknownCard(id)).flatMap {
            existing => f(existing) match {
              case next: RelicState => Right(stored.copy(relics =
                stored.relics.map(value =>
                  if (value.id == id) next else value)))
              case _ => Left(IncompatibleLocation(
                Piece.Card(id), Location.OnCard(id)))
            }
          }
      }
      updated.map { next =>
        val atlas = AtlasState(entries.updated(container.atlasPosition, next))
        ready.copy(game = ready.game.copy(campaign =
          ready.game.campaign.copy(atlas = atlas)))
      }
    }
  }

  private[operations] def semanticLocation(container: CardContainer): Location = container match {
    case CardContainer.Deck(kind) => Location.Deck(kind match {
      case DeckKind.World => CardDeck.World
      case DeckKind.Relic => CardDeck.Relic
      case DeckKind.Edifice => CardDeck.Edifice
      case DeckKind.Legacy => CardDeck.Legacy
    })
    case CardContainer.RegionalDiscard(region) => Location.RegionalDiscard(region)
    case CardContainer.Player(player, PlayerCardArea.Hand) => Location.Hand(player)
    case CardContainer.Player(player, _) => Location.PlayArea(player)
    case CardContainer.Site(site, _) => Location.Site(site)
    case CardContainer.Reliquary => Location.Reliquary
    case CardContainer.SetAsideRelics => Location.SetAsideRelics
    case CardContainer.Dispossessed => Location.Dispossessed
    case _: CardContainer.AtlasSite => Location.Atlas
    case _ => Location.Atlas
  }
}
