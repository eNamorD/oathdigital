package oathdigital.gameplay.operations

import oathdigital.model._

/** Card movement: `Move` of a card, `Bury`, and the composites that flatten
  * to them. The guard checks stack-position conventions, that each card is
  * where the operation says it is (and in the stack position it names), and
  * that its destination can hold it; the mutation removes every card before
  * inserting any.
  */
private[operations] object CardMovementOperations {
  import OperationError._
  import OperationStateAdapter.{card, playerState}
  import OperationStateWrites.{
    CardTransfer,
    cardTransfers,
    semanticLocation,
    sequence,
    updateCommonCards,
    updatePlayer,
    updateSite
  }

  def positionViolations(
      leaves: Vector[Operation]
  ): Vector[OperationError] = leaves.iterator.flatMap {
    case move: Move => positionViolations(move.from, move.to)
    case bury: Bury => positionViolations(bury.from, bury.to)
    case _ => Vector.empty
  }.toVector

  private def positionViolations(
      from: PositionedLocation,
      to: PositionedLocation
  ): Vector[OperationError] =
    sourcePositionViolation(from).toVector ++
      destinationPositionViolation(to).toVector

  private def sourcePositionViolation(
      positioned: PositionedLocation
  ): Option[OperationError] =
    if (isStack(positioned.location)) None
    else if (positioned.position == StackPosition.Unspecified) None
    else Some(InvalidStackPosition(
      positioned.location,
      "non-stack source cannot specify top or bottom"
    ))

  private def destinationPositionViolation(
      positioned: PositionedLocation
  ): Option[OperationError] =
    if (isStack(positioned.location)) {
      if (positioned.position != StackPosition.Unspecified) None
      else Some(InvalidStackPosition(
        positioned.location,
        "stack destination must specify top or bottom"
      ))
    } else if (positioned.position == StackPosition.Unspecified) None
    else Some(InvalidStackPosition(
      positioned.location,
      "non-stack destination cannot specify top or bottom"
    ))

  private def isStack(location: Location): Boolean = location match {
    case _: Location.Deck | _: Location.RegionalDiscard => true
    case _ => false
  }

  private final case class ResolvedTransfer(
      transfer: CardTransfer,
      located: LocatedCard
  )

  def cardViolations(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Vector[OperationError] = {
    val all = cardTransfers(leaves)
    val duplicate: Vector[OperationError] =
      if (all.map(_.piece.id).groupBy(identity).exists {
        case (_, occurrences) => occurrences.size > 1
      }) Vector(ConflictingDeltas(
        "one operation moves the same card more than once"))
      else Vector.empty

    val resolved = all.map { transfer =>
      card(ready, transfer.piece.id, transfer.from.location)
        .map(ResolvedTransfer(transfer, _))
    }
    val sourceReasons = resolved.collect { case Left(error) => error }
    val successful = resolved.collect { case Right(value) => value }

    duplicate ++ sourceReasons ++
      cardSourceOrderViolations(ready, successful) ++
      successful.flatMap(value =>
        cardDestinationViolation(value.located, value.transfer).toVector)
  }

  private def cardSourceOrderViolations(
      ready: ReadyGame,
      transfers: Vector[ResolvedTransfer]
  ): Vector[OperationError] = {
    val grouped = transfers.groupBy(_.located.location.container)
    grouped.toVector.flatMap { case (container, values) =>
      stackCards(ready, container) match {
        case None => Vector.empty
        case Some(initial) =>
          val (failure, _) = values.foldLeft[(Option[OperationError],
            Vector[CardId])]((None, initial)) {
            case ((Some(error), cards), _) => (Some(error), cards)
            case ((None, cards), value) =>
              val id = value.located.id
              val valid = value.transfer.from.position match {
                case StackPosition.Unspecified => cards.contains(id)
                case StackPosition.Top => cards.headOption.contains(id)
                case StackPosition.Bottom => cards.lastOption.contains(id)
              }
              if (valid) (None, cards.filterNot(_ == id))
              else (Some(InvalidStackPosition(
                value.transfer.from.location,
                "card does not match requested stack position"
              )), cards)
          }
          failure.toVector
      }
    }
  }

  private def cardDestinationViolation(
      located: LocatedCard,
      transfer: CardTransfer
  ): Option[OperationError] = {
    val id = located.id
    val destination = transfer.to.location
    destination match {
      case Location.Deck(deck) =>
        if (cardDeck(id).contains(deck)) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.RegionalDiscard =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.Hand =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case Location.Reliquary | Location.SetAsideRelics =>
        if (id.isInstanceOf[RelicId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case Location.Dispossessed =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.Site => id match {
        case _: DenizenId | _: EdificeId | _: RelicId =>
          statefulMaterializationViolation(located, transfer)
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
      case _: Location.PlayArea => id match {
        case _: DenizenId | _: VisionId | _: RelicId =>
          statefulMaterializationViolation(located, transfer)
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
      case Location.SharedBank => id match {
        case _: VisionId => None
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
      case Location.Atlas => Some(AmbiguousLocation(
        Location.Atlas,
        "Atlas destination requires a stored-site identity"
      ))
      case _ => Some(InvalidDestination(transfer.piece, destination))
    }
  }

  private def statefulMaterializationViolation(
      located: LocatedCard,
      transfer: CardTransfer
  ): Option[OperationError] = located.id match {
    case id: EdificeId if transfer.resultingOrientation.nonEmpty =>
      Some(UnsupportedOrientation(id, transfer.to.location))
    case id: EdificeId if located.state.isEmpty =>
      Some(UnsupportedOrientation(id, transfer.to.location))
    case id if located.state.isEmpty &&
        transfer.resultingOrientation.isEmpty =>
      Some(MissingOrientation(id, transfer.to.location))
    case _ => None
  }

  private def cardDeck(id: CardId): Option[CardDeck] = id match {
    case _: DenizenId | _: VisionId => Some(CardDeck.World)
    case _: RelicId => Some(CardDeck.Relic)
    case _: EdificeId => Some(CardDeck.Edifice)
    case _: LegacyId => Some(CardDeck.Legacy)
  }

  private def stackCards(
      ready: ReadyGame,
      container: CardContainer
  ): Option[Vector[CardId]] = container match {
    case CardContainer.Deck(CardDeck.World) =>
      Some(ready.game.current.commonCards.worldDeck)
    case CardContainer.Deck(CardDeck.Relic) =>
      Some(ready.game.current.commonCards.relicDeck)
    case CardContainer.Deck(CardDeck.Edifice) =>
      Some(ready.game.current.commonCards.edificeDeck)
    case CardContainer.Deck(CardDeck.Legacy) =>
      Some(ready.game.current.commonCards.legacyDeck)
    case CardContainer.RegionalDiscard(region) =>
      Some(ready.game.current.commonCards.discard(region).reverse)
    case _ => None
  }

  private[operations] def applyCardMoves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] = {
    val transfers = cardTransfers(leaves)
    if (transfers.isEmpty) Right(ready)
    else for {
      index <- CardIndex.from(ready.game).left.map(InvalidCardIndex)
      removedCards <- sequence(transfers.map { transfer =>
        val id = transfer.piece.id
        index.get(id).toRight(UnknownCard(id)).map(transfer -> _)
      })
      without <- removedCards.foldLeft[Either[OperationError, ReadyGame]](
        Right(ready)) { case (result, (_, located)) =>
        result.flatMap(removeCard(_, located))
      }
      inserted <- removedCards.foldLeft[Either[OperationError, ReadyGame]](
        Right(without)) { case (result, (move, located)) =>
        result.flatMap(insertCard(_, move, located))
      }
    } yield inserted
  }

  private def removeCard(
      ready: ReadyGame,
      located: LocatedCard
  ): Either[OperationError, ReadyGame] = located.location.container match {
    case CardContainer.Deck(deck) => updateCommonCards(ready) { cards => deck match {
      case CardDeck.World => cards.copy(worldDeck = cards.worldDeck.filterNot(_ == located.id))
      case CardDeck.Relic => cards.copy(relicDeck = cards.relicDeck.filterNot(_ == located.id))
      case CardDeck.Edifice => cards.copy(edificeDeck = cards.edificeDeck.filterNot(_ == located.id))
      case CardDeck.Legacy => cards.copy(legacyDeck = cards.legacyDeck.filterNot(_ == located.id))
    }}
    case CardContainer.RegionalDiscard(region) => updateCommonCards(ready) { cards =>
      cards.copy(regionalDiscards = cards.regionalDiscards.updated(region,
        cards.discard(region).filterNot(_ == located.id)))
    }
    case CardContainer.Player(player, PlayerCardArea.Hand) => Right(ready.updateCurrent {
      current => current.copy(temporaryHands = current.temporaryHands.updated(
        player, current.temporaryHands.getOrElse(player, Vector.empty)
          .filterNot(_ == located.id)))
    })
    case CardContainer.Player(player, PlayerCardArea.Advisers) => updatePlayer(ready, player)(
      state => state.copy(advisers = state.advisers.filterNot(_.id == located.id)))
    case CardContainer.Player(player, PlayerCardArea.Relics) => updatePlayer(ready, player)(
      state => state.copy(relics = state.relics.filterNot(_.id == located.id)))
    case CardContainer.Player(player, PlayerCardArea.RevealedVision) => updatePlayer(ready,
      player)(state => state.copy(revealedVision = None))
    case CardContainer.Site(site, SiteCardArea.Denizens) => updateSite(ready, site)(
      state => state.copy(denizens = state.denizens.filterNot(_.id == located.id)))
    case CardContainer.Site(site, SiteCardArea.Relics) => updateSite(ready, site)(
      state => state.copy(relics = state.relics.filterNot(_.id == located.id)))
    case CardContainer.Reliquary => Right(ready.copy(game = ready.game.copy(
      campaign = ready.game.campaign.copy(reliquary =
        ready.game.campaign.reliquary.filterNot(_ == located.id)))))
    case CardContainer.SetAsideRelics => Right(ready.updateCurrent(current =>
      current.copy(setAsideRelics = current.setAsideRelics.filterNot(_ == located.id))))
    case CardContainer.Dispossessed => Right(ready.copy(game = ready.game.copy(
      campaign = ready.game.campaign.copy(dispossessed =
        ready.game.campaign.dispossessed.filterNot(_ == located.id)))))
    case CardContainer.AtlasSite(position, _, area) =>
      val entries = ready.game.campaign.atlas.entries.updated(position,
        ready.game.campaign.atlas.entries(position) match {
        case stored: AtlasEntry.StoredSite => area match {
          case SiteCardArea.Denizens => stored.copy(
            denizens = stored.denizens.filterNot(_.id == located.id))
          case SiteCardArea.Relics => stored.copy(
            relics = stored.relics.filterNot(_.id == located.id))
        }
        case other => other
      })
      Right(ready.copy(game = ready.game.copy(campaign = ready.game.campaign.copy(
        atlas = AtlasState(entries)))))
    case _ => Left(AmbiguousLocation(
      semanticLocation(located.location.container),
      "card container is not writable by core operations"
    ))
  }

  private def insertCard(
      ready: ReadyGame,
      transfer: CardTransfer,
      original: LocatedCard
  ): Either[OperationError, ReadyGame] = {
    val id = original.id
    val adjustedState = adjustCardOrientation(original.state,
      transfer.resultingOrientation, id, transfer.to.location)
    transfer.to.location match {
      case Location.Deck(deck) => for {
        _ <- ensureEmptyTokens(original.state, id)
        updated <- insertDeck(ready, deck, id, transfer.to.position)
      } yield updated
      case Location.RegionalDiscard(region) => for {
        _ <- ensureEmptyTokens(original.state, id)
      } yield ready.updateCurrent { current =>
        val cards = current.commonCards
        val existing = cards.discard(region)
        val inserted = insertStack(existing, id.asInstanceOf[WorldCardId],
          transfer.to.position, topAtHead = false)
        current.copy(commonCards = cards.copy(regionalDiscards =
          cards.regionalDiscards.updated(region, inserted)))
      }
      case Location.Hand(player) => ensureEmptyTokens(original.state, id).map {
        _ => ready.updateCurrent { current =>
          current.copy(temporaryHands = current.temporaryHands.updated(player,
            current.temporaryHands.getOrElse(player, Vector.empty) :+
              id.asInstanceOf[WorldCardId]))
        }
      }
      case Location.Reliquary => ensureEmptyTokens(original.state, id).map(_ =>
        ready.copy(game = ready.game.copy(campaign = ready.game.campaign.copy(
          reliquary = ready.game.campaign.reliquary :+ id.asInstanceOf[RelicId]))))
      case Location.SetAsideRelics => ensureEmptyTokens(original.state, id).map(_ =>
        ready.updateCurrent(current => current.copy(
          setAsideRelics = current.setAsideRelics :+ id.asInstanceOf[RelicId])))
      case Location.Dispossessed => ensureEmptyTokens(original.state, id).map(_ =>
        ready.copy(game = ready.game.copy(campaign = ready.game.campaign.copy(
          dispossessed = ready.game.campaign.dispossessed :+
            id.asInstanceOf[WorldCardId]))))
      case Location.SharedBank => ensureEmptyTokens(original.state, id)
        .map(_ => ready)
      case Location.Site(site) => adjustedState.flatMap {
        case state: SiteDenizenState => updateSite(ready, site)(value =>
          value.copy(denizens = value.denizens :+ state))
        case state: RelicState => updateSite(ready, site)(value =>
          value.copy(relics = value.relics :+ state))
        case _ => Left(InvalidDestination(transfer.piece, transfer.to.location))
      }
      case Location.PlayArea(player) => adjustedState.flatMap {
        case state: DenizenState => updatePlayer(ready, player)(value =>
          value.copy(advisers = value.advisers :+ state))
        case state: VisionState if state.orientation == Orientation.FaceDown =>
          updatePlayer(ready, player)(value =>
            value.copy(advisers = value.advisers :+ state))
        case state: VisionState => playerState(ready, player).flatMap { value =>
          Either.cond(value.revealedVision.isEmpty, (),
            ConflictingDeltas("revealed Vision slot is occupied")).flatMap { _ =>
            updatePlayer(ready, player)(_.copy(revealedVision = Some(state)))
          }
        }
        case state: RelicState => updatePlayer(ready, player)(value =>
          value.copy(relics = value.relics :+ state))
        case _ => Left(InvalidDestination(transfer.piece, transfer.to.location))
      }
      case _ => Left(InvalidDestination(transfer.piece, transfer.to.location))
    }
  }

  private def adjustCardOrientation(
      state: Option[CardState],
      orientation: Option[Orientation],
      id: CardId,
      destination: Location
  ): Either[OperationError, CardState] = (state, id) match {
    case (Some(card: DenizenState), _) => Right(orientation.fold(card)(value =>
      card.copy(orientation = value)))
    case (Some(card: VisionState), _) => Right(orientation.fold(card)(value =>
      card.copy(orientation = value)))
    case (Some(card: RelicState), _) => Right(orientation.fold(card)(value =>
      card.copy(orientation = value)))
    case (Some(card: EdificeState), _) if orientation.isEmpty => Right(card)
    case (Some(_: EdificeState), _) => Left(UnsupportedOrientation(id, destination))
    case (Some(card), _) => Right(card)
    case (None, value: DenizenId) => orientation
      .map(face => DenizenState(value, face, Tokens.empty))
      .toRight(MissingOrientation(value, destination))
    case (None, value: VisionId) => orientation
      .map(face => VisionState(value, face))
      .toRight(MissingOrientation(value, destination))
    case (None, value: RelicId) => orientation
      .map(face => RelicState(value, face, Tokens.empty))
      .toRight(MissingOrientation(value, destination))
    case (None, value) => Left(UnsupportedOrientation(value, destination))
  }

  private def ensureEmptyTokens(
      state: Option[CardState],
      id: CardId
  ): Either[OperationError, Unit] = state match {
    case Some(card: SiteDenizenState) => Either.cond(card.tokens.isEmpty, (),
      ConflictingDeltas(s"card kind ${id.kind} still carries resources"))
    case Some(card: RelicState) => Either.cond(card.tokens.isEmpty, (),
      ConflictingDeltas(s"card kind ${id.kind} still carries resources"))
    case _ => Right(())
  }

  private def insertDeck(ready: ReadyGame, deck: CardDeck, id: CardId,
      position: StackPosition): Either[OperationError, ReadyGame] =
    Right(ready.updateCurrent { current =>
      val cards = current.commonCards
      val updated = deck match {
        case CardDeck.World => cards.copy(worldDeck = insertStack(
          cards.worldDeck, id.asInstanceOf[WorldCardId], position, topAtHead = true))
        case CardDeck.Relic => cards.copy(relicDeck = insertStack(
          cards.relicDeck, id.asInstanceOf[RelicId], position, topAtHead = true))
        case CardDeck.Edifice => cards.copy(edificeDeck = insertStack(
          cards.edificeDeck, id.asInstanceOf[EdificeId], position, topAtHead = true))
        case CardDeck.Legacy => cards.copy(legacyDeck = insertStack(
          cards.legacyDeck, id.asInstanceOf[LegacyId], position, topAtHead = true))
      }
      current.copy(commonCards = updated)
    })

  private def insertStack[A](existing: Vector[A], value: A,
      position: StackPosition, topAtHead: Boolean): Vector[A] =
    (position, topAtHead) match {
      case (StackPosition.Top, true) | (StackPosition.Bottom, false) =>
        value +: existing
      case (StackPosition.Bottom, true) | (StackPosition.Top, false) =>
        existing :+ value
      case _ => existing
    }
}
