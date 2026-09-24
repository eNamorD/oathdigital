package oathdigital.gameplay.operations

import oathdigital.model._

/** State writers shared by every operation family, and the one collector of
  * card transfers that both a guard and a mutation read. Nothing here decides
  * legality.
  */
private[operations] object OperationStateWrites {
  import OperationError._
  import OperationStateAdapter.{playerState, siteState}

  /** A card `Move` or `Bury`, seen the same way by the guard that checks it
    * and the mutation that performs it.
    */
  final case class CardTransfer(
      piece: Piece.Card,
      from: PositionedLocation,
      to: PositionedLocation,
      resultingOrientation: Option[Orientation]
  )

  def cardTransfers(leaves: Vector[Operation]): Vector[CardTransfer] =
    leaves.collect {
      case Move(piece: Piece.Card, from, to, orientation) =>
        CardTransfer(piece, from, to, orientation)
      case bury: Bury => CardTransfer(
        Piece.Card(bury.card.id),
        bury.from,
        bury.to,
        resultingOrientation = None
      )
    }

  def sequence[A](
      values: Vector[Either[OperationError, A]]
  ): Either[OperationError, Vector[A]] =
    values.foldLeft[Either[OperationError, Vector[A]]](Right(Vector.empty)) {
      case (result, value) => for {
        accumulated <- result
        next <- value
      } yield accumulated :+ next
    }

  def updateCommonCards(ready: ReadyGame)(
      f: CardZones => CardZones): Either[OperationError, ReadyGame] =
    Right(ready.updateCurrent(current =>
      current.copy(commonCards = f(current.commonCards))))

  def updatePlayer(ready: ReadyGame, player: PlayerId)(
      f: PlayerState => PlayerState): Either[OperationError, ReadyGame] =
    playerState(ready, player).map { _ => ready.updateCurrent { current =>
      current.copy(players = current.players.map(value =>
        if (value.player == player) f(value) else value))
    }}

  def updateSite(ready: ReadyGame, site: SiteId)(
      f: SiteState => SiteState): Either[OperationError, ReadyGame] =
    siteState(ready, site).map { state => ready.updateCurrent { current =>
      current.copy(map = current.map.copy(sites =
        current.map.sites.updated(site, f(state))))
    }}

  def updateCardTokens(ready: ReadyGame, id: CardId)(
      f: Tokens => Tokens): Either[OperationError, ReadyGame] =
    updateCardState(ready, id) {
      case value: DenizenState => value.copy(tokens = f(value.tokens))
      case value: EdificeState => value.copy(tokens = f(value.tokens))
      case value: RelicState => value.copy(tokens = f(value.tokens))
      case value => value
    }

  def updateCardState(ready: ReadyGame, id: CardId)(
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

  def updateAtlasCardState(
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

  def semanticLocation(container: CardContainer): Location = container match {
    case CardContainer.Deck(deck) => Location.Deck(deck)
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
