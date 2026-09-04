package oathdigital.gameplay.operations

import oathdigital.gameplay.ReadyGame
import oathdigital.model._

sealed trait AvailableQuantity extends Product with Serializable
object AvailableQuantity {
  final case class Finite(value: Int) extends AvailableQuantity {
    require(value >= 0, "available quantity must be non-negative")
  }
  case object Unbounded extends AvailableQuantity
}
final case class SecretInventory(faceUp: Int, faceDown: Int) {
  require(faceUp >= 0, "faceup secrets must be non-negative")
  require(faceDown >= 0, "facedown secrets must be non-negative")
}

/** Read-only bridge between semantic operation locations and nested game state.
  * Mutation and transactional preflight belong to migration phase 3.
  */
object OperationStateAdapter {
  import AvailableQuantity._
  import OperationError._

  def card(
      ready: ReadyGame,
      id: CardId,
      at: Location
  ): Either[OperationError, LocatedCard] =
    CardIndex.from(ready.game).left.map(InvalidCardIndex).flatMap { index =>
      index.get(id).toRight(UnknownCard(id)).flatMap { located =>
        Either.cond(matches(located.location.container, at), located,
          MissingPiece(Piece.Card(id), at))
      }
    }

  def quantity(
      ready: ReadyGame,
      piece: Piece,
      at: Location
  ): Either[OperationError, AvailableQuantity] = piece match {
    case Piece.Card(id) => card(ready, id, at).map(_ => Finite(1))
    case Piece.Favor(_) => favor(ready, at).map(Finite)
    case Piece.Secrets(_) => at match {
      case Location.SharedBank => Right(Unbounded)
      case _ => secrets(ready, at).map(value => Finite(value.faceUp + value.faceDown))
    }
    case Piece.Warbands(kind, _) => warbands(ready, kind, at).map(Finite)
    case Piece.Pawn(player) => at match {
      case Location.Site(site) => playerState(ready, player).map { state =>
        Finite(state.pawnSite.count(_ == site))
      }
      case _ => Left(IncompatibleLocation(piece, at))
    }
    case Piece.Banner(banner) => at match {
      case Location.PlayArea(player) => playerState(ready, player).map { _ =>
        Finite(bannerHolder(ready, banner).count(_ == player))
      }
      case Location.SharedBank =>
        Right(Finite(if (bannerHolder(ready, banner).isEmpty) 1 else 0))
      case _ => Left(IncompatibleLocation(piece, at))
    }
  }

  def secrets(
      ready: ReadyGame,
      at: Location
  ): Either[OperationError, SecretInventory] = at match {
    case Location.PlayArea(player) => playerState(ready, player).map { state =>
      SecretInventory(state.board.faceUpSecrets, state.board.faceDownSecrets)
    }
    case Location.Site(site) => siteState(ready, site).map { state =>
      SecretInventory(state.tokens.secrets, 0)
    }
    case Location.OnCard(id) => cardTokens(ready, id).map { tokens =>
      SecretInventory(tokens.secrets, 0)
    }
    case Location.OnBanner(Banner.DarkestSecret) => Right(SecretInventory(
      ready.game.current.banners.darkestSecret.secrets, 0))
    case _ => Left(IncompatibleLocation(Piece.Secrets(1), at))
  }

  def knows(ready: ReadyGame, viewer: PlayerId, card: CardId): Boolean =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty).contains(card) ||
      ready.knowledge.heldRelics.getOrElse(viewer, Vector.empty).contains(card) ||
      ready.knowledge.siteRelics.getOrElse(viewer, Map.empty)
        .valuesIterator.exists(_.contains(card))

  private[operations] def applyOperation(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    OperationStateMutation.applyOperation(ready, operation)

  private def favor(ready: ReadyGame,
      at: Location): Either[OperationError, Int] = at match {
    case Location.PlayArea(player) => playerState(ready, player).map(_.board.favor)
    case Location.Site(site) => siteState(ready, site).map(_.tokens.favor)
    case Location.OnCard(id) => cardTokens(ready, id).map(_.favor)
    case Location.OnBanner(Banner.PeoplesFavor) =>
      Right(ready.game.current.banners.peoplesFavor.favor)
    case Location.FavorBank(suit) => Right(ready.banks.favor.getOrElse(suit, 0))
    case _ => Left(IncompatibleLocation(Piece.Favor(1), at))
  }

  private def warbands(ready: ReadyGame, kind: ForceKind,
      at: Location): Either[OperationError, Int] = at match {
    case Location.PlayArea(player) => playerState(ready, player).flatMap { state =>
      Either.cond(playerForceKind(ready, state).contains(kind),
        state.board.warbands, IncompatibleLocation(Piece.Warbands(kind, 1), at))
    }
    case Location.Site(site) => siteState(ready, site).map {
      _.forces match {
        case SiteForces.Occupied(`kind`, count) => count
        case _ => 0
      }
    }
    case Location.WarbandBank(`kind`) => warbandsInBank(ready, kind)
    case _ => Left(IncompatibleLocation(Piece.Warbands(kind, 1), at))
  }

  private def cardTokens(
      ready: ReadyGame,
      id: CardId
  ): Either[OperationError, Tokens] =
    CardIndex.from(ready.game).left.map(InvalidCardIndex).flatMap { index =>
      index.stateOf(id).collect {
        case state: SiteDenizenState => state.tokens
        case state: RelicState => state.tokens
      }.toRight(IncompatibleLocation(Piece.Card(id), Location.OnCard(id)))
    }

  private[operations] def playerState(ready: ReadyGame,
      player: PlayerId): Either[OperationError, PlayerState] =
    ready.game.current.players.find(_.player == player)
      .toRight(UnknownPlayer(player))

  private[operations] def siteState(ready: ReadyGame,
      site: SiteId): Either[OperationError, SiteState] =
    ready.game.current.map.sites.get(site)
      .toRight(UnknownSite(site))

  private def warbandsInBank(
      ready: ReadyGame,
      kind: ForceKind
  ): Either[OperationError, Int] = {
    val onBoards = ready.game.current.players.iterator.map { player =>
      if (playerForceKind(ready, player).contains(kind)) player.board.warbands
      else 0
    }.sum
    val atSites = ready.game.current.map.sites.valuesIterator.map {
      _.forces match {
        case SiteForces.Occupied(`kind`, count) => count
        case _ => 0
      }
    }.sum
    val inPlay = onBoards + atSites

    ready.banks.warbandSupply.get(kind)
      .toRight(UnknownWarbandSupply(kind)).flatMap { supply =>
        Either.cond(inPlay <= supply, supply - inPlay,
          InvalidWarbandInventory(kind, supply, inPlay))
      }
  }

  private def playerForceKind(
      ready: ReadyGame,
      player: PlayerState
  ): Option[ForceKind] = ready.game.campaign.lineages.get(player.lineage).map {
    case lineage if lineage.role.isImperial => ForceKind.Imperial
    case _ => ForceKind.Exile(player.lineage)
  }

  private[operations] def bannerHolder(ready: ReadyGame, banner: Banner): Option[PlayerId] =
    banner match {
      case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.holder
      case Banner.DarkestSecret => ready.game.current.banners.darkestSecret.holder
    }

  private def matches(container: CardContainer, location: Location): Boolean =
    (container, location) match {
      case (CardContainer.Deck(kind), Location.Deck(deck)) =>
        deckKind(deck) == kind
      case (CardContainer.RegionalDiscard(left), Location.RegionalDiscard(right)) =>
        left == right
      case (CardContainer.Player(left, PlayerCardArea.Hand), Location.Hand(right)) =>
        left == right
      case (CardContainer.Player(left, area), Location.PlayArea(right)) =>
        area != PlayerCardArea.Hand && left == right
      case (CardContainer.Site(left, _), Location.Site(right)) => left == right
      case (CardContainer.Reliquary, Location.Reliquary) => true
      case (CardContainer.SetAsideRelics, Location.SetAsideRelics) => true
      case (CardContainer.Dispossessed, Location.Dispossessed) => true
      case (_: CardContainer.AtlasSite, Location.Atlas) => true
      case _ => false
    }

  private def deckKind(deck: CardDeck): DeckKind = deck match {
    case CardDeck.World => DeckKind.World
    case CardDeck.Relic => DeckKind.Relic
    case CardDeck.Edifice => DeckKind.Edifice
    case CardDeck.Legacy => DeckKind.Legacy
  }
}
