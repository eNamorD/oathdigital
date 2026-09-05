package oathdigital.gameplay.operations

import oathdigital.gameplay.ReadyGame
import oathdigital.model._

/** Internal immutable mutation planner for counted pieces and primitive effects. */
private[operations] object OperationStateMutation {
  import OperationError._
  import OperationStateAdapter._

  private[operations] def applyOperation(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] = {
    val primitives = operation.primitives
    for {
      _ <- validatePrimitivePositions(primitives)
      _ <- validateCardSources(ready, primitives)
      resources <- applyCountedMoves(ready, primitives)
      pieces <- applyPawnAndBannerMoves(resources, primitives)
      cards <- OperationCardMutation.applyCardMoves(pieces, primitives)
      finished <- applyNonMovePrimitives(cards, primitives)
    } yield finished
  }

  private def validatePrimitivePositions(
      primitives: Vector[PrimitiveOperation]
  ): Either[OperationError, Unit] =
    primitives.foldLeft[Either[OperationError, Unit]](Right(())) {
      case (result, move: Move) =>
        result.flatMap(_ => validatePositions(move.from, move.to))
      case (result, bury: Bury) =>
        result.flatMap(_ => validatePositions(bury.from, bury.to))
      case (result, _) => result
    }

  private def validatePositions(
      from: PositionedLocation,
      to: PositionedLocation
  ): Either[OperationError, Unit] = for {
    _ <- validateSourcePosition(from)
    _ <- validateDestinationPosition(to)
  } yield ()

  private def validateSourcePosition(
      positioned: PositionedLocation
  ): Either[OperationError, Unit] =
    if (isStack(positioned.location)) Right(())
    else Either.cond(
      positioned.position == StackPosition.Unspecified,
      (),
      InvalidStackPosition(
        positioned.location,
        "non-stack source cannot specify top or bottom"
      )
    )

  private def validateDestinationPosition(
      positioned: PositionedLocation
  ): Either[OperationError, Unit] =
    if (isStack(positioned.location))
      Either.cond(
        positioned.position != StackPosition.Unspecified,
        (),
        InvalidStackPosition(
          positioned.location,
          "stack destination must specify top or bottom"
        )
      )
    else
      Either.cond(
        positioned.position == StackPosition.Unspecified,
        (),
        InvalidStackPosition(
          positioned.location,
          "non-stack destination cannot specify top or bottom"
        )
      )

  private def isStack(location: Location): Boolean = location match {
    case _: Location.Deck | _: Location.RegionalDiscard => true
    case _ => false
  }

  private[operations] final case class CardTransfer(
      piece: Piece.Card,
      from: PositionedLocation,
      to: PositionedLocation,
      resultingOrientation: Option[Orientation]
  )

  private final case class ResolvedCardTransfer(
      transfer: CardTransfer,
      located: LocatedCard
  )

  private[operations] def cardTransfers(
      primitives: Vector[PrimitiveOperation]
  ): Vector[CardTransfer] = primitives.collect {
    case Move(piece: Piece.Card, from, to, orientation) =>
      CardTransfer(piece, from, to, orientation)
    case bury: Bury => CardTransfer(
      Piece.Card(bury.card.id),
      bury.from,
      bury.to,
      resultingOrientation = None
    )
  }

  private def validateCardSources(
      ready: ReadyGame,
      primitives: Vector[PrimitiveOperation]
  ): Either[OperationError, Unit] = {
    val transfers = cardTransfers(primitives)
    val duplicate = transfers.map(_.piece.id)
      .groupBy(identity).collectFirst { case (id, occurrences)
          if occurrences.size > 1 => id }
    for {
      _ <- duplicate.toLeft(()).left.map(_ =>
        ConflictingDeltas("one operation moves the same card more than once"))
      resolved <- sequence(transfers.map { transfer =>
        card(ready, transfer.piece.id, transfer.from.location)
          .map(ResolvedCardTransfer(transfer, _))
      })
      _ <- validateCardSourceOrder(ready, resolved)
      _ <- sequence(resolved.map(value =>
        validateCardDestination(value.located, value.transfer))).map(_ => ())
    } yield ()
  }

  private def validateCardSourceOrder(
      ready: ReadyGame,
      transfers: Vector[ResolvedCardTransfer]
  ): Either[OperationError, Unit] = {
    val grouped = transfers.groupBy(_.located.location.container)
    grouped.toVector.foldLeft[Either[OperationError, Unit]](Right(())) {
      case (result, (container, values)) => result.flatMap { _ =>
        stackCards(ready, container) match {
          case None => Right(())
          case Some(initial) =>
            values.foldLeft[Either[OperationError, Vector[CardId]]](
              Right(initial)
            ) { (current, value) =>
              current.flatMap { cards =>
                val id = value.located.id
                val valid = value.transfer.from.position match {
                  case StackPosition.Unspecified => cards.contains(id)
                  case StackPosition.Top => cards.headOption.contains(id)
                  case StackPosition.Bottom => cards.lastOption.contains(id)
                }
                Either.cond(
                  valid,
                  cards.filterNot(_ == id),
                  InvalidStackPosition(
                    value.transfer.from.location,
                    "card does not match requested stack position"
                  )
                )
              }
            }.map(_ => ())
        }
      }
    }
  }

  private def stackCards(
      ready: ReadyGame,
      container: CardContainer
  ): Option[Vector[CardId]] = container match {
    case CardContainer.Deck(DeckKind.World) =>
      Some(ready.game.current.commonCards.worldDeck)
    case CardContainer.Deck(DeckKind.Relic) =>
      Some(ready.game.current.commonCards.relicDeck)
    case CardContainer.Deck(DeckKind.Edifice) =>
      Some(ready.game.current.commonCards.edificeDeck)
    case CardContainer.Deck(DeckKind.Legacy) =>
      Some(ready.game.current.commonCards.legacyDeck)
    case CardContainer.RegionalDiscard(region) =>
      Some(ready.game.current.commonCards.discard(region).reverse)
    case _ => None
  }

  private def validateCardDestination(
      located: LocatedCard,
      transfer: CardTransfer
  ): Either[OperationError, Unit] = {
    val id = located.id
    val destination = transfer.to.location
    destination match {
      case Location.Deck(deck) => Either.cond(
        cardDeck(id).contains(deck), (),
        InvalidDestination(transfer.piece, destination))
      case _: Location.RegionalDiscard => Either.cond(
        id.isInstanceOf[WorldCardId], (),
        InvalidDestination(transfer.piece, destination))
      case _: Location.Hand => Either.cond(
        id.isInstanceOf[WorldCardId], (),
        InvalidDestination(transfer.piece, destination))
      case Location.Reliquary | Location.SetAsideRelics => Either.cond(
        id.isInstanceOf[RelicId], (),
        InvalidDestination(transfer.piece, destination))
      case Location.Dispossessed => Either.cond(
        id.isInstanceOf[WorldCardId], (),
        InvalidDestination(transfer.piece, destination))
      case _: Location.Site => id match {
        case _: DenizenId | _: EdificeId | _: RelicId =>
          validateStatefulMaterialization(located, transfer)
        case _ => Left(InvalidDestination(transfer.piece, destination))
      }
      case _: Location.PlayArea => id match {
        case _: DenizenId | _: VisionId | _: RelicId =>
          validateStatefulMaterialization(located, transfer)
        case _ => Left(InvalidDestination(transfer.piece, destination))
      }
      case Location.Atlas => Left(AmbiguousLocation(
        Location.Atlas,
        "Atlas destination requires a stored-site identity"
      ))
      case _ => Left(InvalidDestination(transfer.piece, destination))
    }
  }

  private def validateStatefulMaterialization(
      located: LocatedCard,
      transfer: CardTransfer
  ): Either[OperationError, Unit] = located.id match {
    case id: EdificeId if transfer.resultingOrientation.nonEmpty =>
      Left(UnsupportedOrientation(id, transfer.to.location))
    case id: EdificeId if located.state.isEmpty =>
      Left(UnsupportedOrientation(id, transfer.to.location))
    case id if located.state.isEmpty && transfer.resultingOrientation.isEmpty =>
      Left(MissingOrientation(id, transfer.to.location))
    case _ => Right(())
  }

  private def cardDeck(id: CardId): Option[CardDeck] = id match {
    case _: DenizenId | _: VisionId => Some(CardDeck.World)
    case _: RelicId => Some(CardDeck.Relic)
    case _: EdificeId => Some(CardDeck.Edifice)
    case _: LegacyId => Some(CardDeck.Legacy)
  }

  private def applyCountedMoves(
      ready: ReadyGame,
      primitives: Vector[PrimitiveOperation]
  ): Either[OperationError, ReadyGame] = {
    val favorMoves = primitives.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = primitives.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = primitives.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    for {
      _ <- validateFavorSources(ready, favorMoves)
      plannedSecrets <- OperationSecretPlanner.plan(ready, secretMoves)
      _ <- validateWarbandSources(ready, warbandMoves)
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

  private def validateFavorSources(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Either[OperationError, Unit] =
    moves.groupBy(_.from.location).toVector.foldLeft[
      Either[OperationError, Unit]](Right(())) {
      case (result, (location, values)) => result.flatMap { _ =>
        val amount = values.map(_.piece.asInstanceOf[Piece.Favor].amount).sum
        quantity(ready, Piece.Favor(amount), location).flatMap(
          requireFinite(Piece.Favor(amount), location, _, amount))
      }
    }

  private def validateWarbandSources(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Either[OperationError, Unit] =
    moves.groupBy(move =>
      move.piece.asInstanceOf[Piece.Warbands].kind -> move.from.location)
      .toVector.foldLeft[Either[OperationError, Unit]](Right(())) {
        case (result, ((kind, location), values)) => result.flatMap { _ =>
          val amount = values.map(
            _.piece.asInstanceOf[Piece.Warbands].amount).sum
          val piece = Piece.Warbands(kind, amount)
          quantity(ready, piece, location).flatMap(
            requireFinite(piece, location, _, amount))
        }
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
      primitives: Vector[PrimitiveOperation]
  ): Either[OperationError, ReadyGame] =
    primitives.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
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

  private def applyNonMovePrimitives(
      ready: ReadyGame,
      primitives: Vector[PrimitiveOperation]
  ): Either[OperationError, ReadyGame] =
    primitives.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(flipCard(_, id, at, orientation))
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(flipPlayerSecrets(_, player, amount, from, to))
      case (result, Peek(viewer, id, at)) =>
        result.flatMap(peek(_, viewer, id, at))
      case (result, AdjustSupply(player, amount)) =>
        result.flatMap(adjustSupply(_, player, amount))
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

  private[operations] def sequence[A](
      values: Vector[Either[OperationError, A]]
  ): Either[OperationError, Vector[A]] =
    values.foldLeft[Either[OperationError, Vector[A]]](Right(Vector.empty)) {
      case (result, value) => for {
        accumulated <- result
        next <- value
      } yield accumulated :+ next
    }
}
