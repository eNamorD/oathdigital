package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{MajorActionPowerShell, OathLifecycle, RuleSourceRef}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameFoundationProfile
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue.ActActionSelection
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

sealed trait EconomyCommand extends Product with Serializable
object EconomyCommand {
  final case class Muster(playerId: PlayerId, target: EconomyTargetRef)
      extends EconomyCommand
  final case class Trade(
      playerId: PlayerId,
      target: EconomyTargetRef,
      resource: TradeResource
  ) extends EconomyCommand
}

final case class MusterResult(target: EconomyTargetRef, source: RuleSourceRef, suit: Suit,
    supplySpent: Int, warbandsGained: Int)
final case class TradeResult(target: EconomyTargetRef, source: RuleSourceRef, suit: Suit,
    resource: TradeResource, supplySpent: Int, gained: Int)

object Economy {
  private val SupplyCost = 1
  private val ExileWarbands = 14

  def handle(catalog: ExecutableCatalog, state: OathState,
      command: EconomyCommand): Either[OathViolation, OathTransition] =
    command match {
      case EconomyCommand.Muster(playerId, target) =>
        validate(catalog, state, playerId, target).flatMap {
          case (ready, player, siteId, card, suit) =>
            for {
              _ <- requireFavor(player, 1)
              _ <- requireSupply(player)
              matching = matchingAdvisers(catalog, player, suit)
              available = availableWarbands(ready, player)
              gained = math.min(1 + matching, available)
              event = Mustered(playerId, siteId, target, suit, SupplyCost, gained)
              next <- evolve(catalog, state, event)
            } yield OathTransition(next, Vector(event), ActActionSelection(playerId))
        }
      case EconomyCommand.Trade(playerId, target, resource) =>
        validate(catalog, state, playerId, target).flatMap {
          case (ready, player, siteId, card, suit) =>
            for {
              _ <- requireSupply(player)
              matches = matchingAdvisers(catalog, player, suit)
              gained <- resource match {
                case TradeResource.Favor =>
                  requireSecrets(player, 1).map(_ => math.min(
                    1 + matches, ready.support.favorBanks.getOrElse(suit, 0)))
                case TradeResource.Secret =>
                  requireFavor(player, 2).map(_ => matches)
              }
              event = Traded(playerId, siteId, target, suit, resource,
                SupplyCost, gained)
              next <- evolve(catalog, state, event)
            } yield OathTransition(next, Vector(event), ActActionSelection(playerId))
        }
    }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] = event match {
    case recorded: Mustered =>
      validate(catalog, state, recorded.playerId, recorded.target).flatMap {
        case (ready, player, siteId, card, suit) =>
          val expected = math.min(1 + matchingAdvisers(catalog, player, suit),
            availableWarbands(ready, player))
          if (recorded.siteId != siteId || recorded.target.id != card.id ||
              recorded.suit != suit ||
              recorded.supplySpent != SupplyCost)
            Left(EconomySourceMismatch(s"expected $siteId/${card.id}/$suit/1 but recorded $recorded"))
          else if (recorded.warbandsGained != expected)
            Left(EconomyOutcomeMismatch(s"expected $expected warbands, recorded ${recorded.warbandsGained}"))
          else for {
            _ <- requireFavor(player, 1)
            _ <- requireSupply(player)
          } yield Ready(applyMuster(ready, recorded))
      }
    case recorded: Traded =>
      validate(catalog, state, recorded.playerId, recorded.target).flatMap {
        case (ready, player, siteId, card, suit) =>
          val matches = matchingAdvisers(catalog, player, suit)
          val expected = recorded.resource match {
            case TradeResource.Favor => math.min(1 + matches,
              ready.support.favorBanks.getOrElse(suit, 0))
            case TradeResource.Secret => matches
          }
          if (recorded.siteId != siteId || recorded.target.id != card.id ||
              recorded.suit != suit ||
              recorded.supplySpent != SupplyCost)
            Left(EconomySourceMismatch(s"expected $siteId/${card.id}/$suit/1 but recorded $recorded"))
          else if (recorded.gained != expected)
            Left(EconomyOutcomeMismatch(s"expected yield $expected, recorded ${recorded.gained}"))
          else for {
            _ <- requireSupply(player)
            _ <- recorded.resource match {
              case TradeResource.Favor => requireSecrets(player, 1)
              case TradeResource.Secret => requireFavor(player, 2)
            }
          } yield Ready(applyTrade(ready, recorded))
      }
    case _ => Left(InvalidEventOrder("Economy received a non-Economy event"))
  }

  def legalMuster(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState): Vector[MusterResult] =
    legalCards(catalog, ready, player).flatMap { case (site, card, suit) =>
      Option.when(player.board.supply.supply >= 1 && player.board.favor >= 1)(
        MusterResult(EconomyTargetRef.fromCard(card.id).get,
          sourceOf(site, card), suit, 1,
          math.min(1 + matchingAdvisers(catalog, player, suit),
            availableWarbands(ready, player))))
    }

  def legalTrades(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState): Vector[TradeResult] =
    legalCards(catalog, ready, player).flatMap { case (site, card, suit) =>
      if (player.board.supply.supply < 1) Vector.empty else Vector(
        Option.when(player.board.faceUpSecrets >= 1)(TradeResult(
          EconomyTargetRef.fromCard(card.id).get, sourceOf(site, card), suit,
          TradeResource.Favor, 1,
          math.min(1 + matchingAdvisers(catalog, player, suit),
            ready.support.favorBanks.getOrElse(suit, 0)))),
        Option.when(player.board.favor >= 2)(TradeResult(
          EconomyTargetRef.fromCard(card.id).get, sourceOf(site, card), suit,
          TradeResource.Secret, 1,
          matchingAdvisers(catalog, player, suit)))
      ).flatten
    }

  private def validate(catalog: ExecutableCatalog, state: OathState,
      playerId: PlayerId, target: EconomyTargetRef) =
    OathLifecycle.validateAct(state, playerId).flatMap { ready =>
      val player = ready.game.current.players.find(_.player == playerId).get
      for {
        _ <- validateSupportedState(catalog, ready, player)
        siteId <- player.pawnSite.toRight(PawnSiteMissing(playerId))
        site <- ready.game.current.map.sites.get(siteId).toRight(SiteNotInPlay(siteId))
        card <- site.denizens.find(_.id == target.id)
          .toRight(EconomyCardUnavailable(siteId, target.id))
        _ <- Either.cond(card.tokens.isEmpty, (), EconomyCardNotEmpty(target.id))
        suit <- suitOf(catalog, card.id).toRight(
          UnsupportedEconomyState(s"no catalog suit for ${card.id}"))
      } yield (ready, player, siteId, card, suit)
    }

  private def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerState): Either[OathViolation, Unit] = {
    val game = ready.game
    val ruledSites = game.current.map.sites.toVector.foldLeft[
      Either[OathViolation, Vector[(SiteId, SiteState)]]](Right(Vector.empty)) {
      case (Right(acc), entry @ (_, site)) =>
        SiteRule.ruledBy(site.forces, game.current.players, player.player)
          .left.map(error => UnsupportedEconomyState(
            s"invalid site ruler mapping: $error"))
          .map(ruled => if (ruled) acc :+ entry else acc)
      case (left @ Left(_), _) => left
    }
    val accessibleSites = ruledSites.map(_.filterNot(entry =>
      player.pawnSite.contains(entry._1))).map { ruled =>
      player.pawnSite.toVector.flatMap(siteId =>
        game.current.map.sites.get(siteId).map(siteId -> _)) ++ ruled
    }
    if (accessibleSites.isLeft) accessibleSites.map(_ => ())
    else if (ready.support.foundationProfile != FirstGameFoundationProfile.FixedUnaltered)
      Left(UnsupportedEconomyState("altered Foundations are not supported"))
    else if (game.campaign.lineages.values.exists(_.role != Role.Exile))
      Left(UnsupportedEconomyState("Economy is limited to the exile-only first game"))
    else MajorActionPowerShell.requireAudited(catalog)
  }

  private def legalCards(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState): Vector[(SiteId, SiteDenizenState, Suit)] =
    validateSupportedState(catalog, ready, player).toOption.toVector.flatMap(_ =>
      player.pawnSite.toVector.flatMap(site => ready.game.current.map.sites.get(site)
        .toVector.flatMap(_.denizens.filter(_.tokens.isEmpty).flatMap(card =>
          suitOf(catalog, card.id).map(suit => (site, card, suit))))))

  private def matchingAdvisers(catalog: ExecutableCatalog,
      player: PlayerState, suit: Suit): Int = player.advisers.count {
    case DenizenState(id, Orientation.FaceUp, _) => suitOf(catalog, id).contains(suit)
    case _ => false
  }
  private def availableWarbands(ready: ReadyGame, player: PlayerState): Int = {
    val onSites = ready.game.current.map.sites.valuesIterator.map(_.forces).collect {
      case SiteForces.Occupied(ForceKind.Exile(owner), count)
          if owner == player.lineage => count
    }.sum
    math.max(0, ExileWarbands - player.board.warbands - onSites)
  }
  private def requireSupply(player: PlayerState) = Either.cond(
    player.board.supply.supply >= 1, (), InsufficientSupply(1, player.board.supply.supply))
  private def requireFavor(player: PlayerState, amount: Int) = Either.cond(
    player.board.favor >= amount, (), InsufficientFavor(amount, player.board.favor))
  private def requireSecrets(player: PlayerState, amount: Int) = Either.cond(
    player.board.faceUpSecrets >= amount, (),
    InsufficientSecrets(amount, player.board.faceUpSecrets))

  private def applyMuster(ready: ReadyGame, event: Mustered): ReadyGame =
    update(ready, event.playerId, event.siteId, event.target.id,
      card => withTokens(card, Tokens(1, 0)), board => board.copy(
        favor = board.favor - 1,
        warbands = board.warbands + event.warbandsGained,
        supply = SupplyTrack(board.supply.supply - event.supplySpent)))

  private def applyTrade(ready: ReadyGame, event: Traded): ReadyGame = {
    val updated = event.resource match {
      case TradeResource.Favor => update(ready, event.playerId, event.siteId,
        event.target.id, card => withTokens(card, Tokens(0, 1)), board => board.copy(
          favor = board.favor + event.gained,
          faceUpSecrets = board.faceUpSecrets - 1,
          supply = SupplyTrack(board.supply.supply - event.supplySpent)))
      case TradeResource.Secret => update(ready, event.playerId, event.siteId,
        event.target.id, card => withTokens(card, Tokens(1, 0)), board => board.copy(
          favor = board.favor - 2,
          faceUpSecrets = board.faceUpSecrets + event.gained,
          supply = SupplyTrack(board.supply.supply - event.supplySpent)))
    }
    if (event.resource == TradeResource.Favor) updated.copy(support =
      updated.support.copy(favorBanks = updated.support.favorBanks.updated(
        event.suit, updated.support.favorBanks.getOrElse(event.suit, 0) - event.gained)))
    else updated
  }

  private def update(ready: ReadyGame, playerId: PlayerId, siteId: SiteId,
      cardId: CardId, cardUpdate: SiteDenizenState => SiteDenizenState,
      boardUpdate: PlayerBoardState => PlayerBoardState): ReadyGame = {
    val current = ready.game.current
    ready.copy(game = ready.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player == playerId)
        p.copy(board = boardUpdate(p.board)) else p),
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        current.map.sites(siteId).copy(denizens = current.map.sites(siteId).denizens
          .map(card => if (card.id == cardId) cardUpdate(card) else card)))))))
  }
  private def withTokens(card: SiteDenizenState, tokens: Tokens) = card match {
    case d: DenizenState => d.copy(tokens = tokens)
    case e: EdificeState => e.copy(tokens = tokens)
  }
  private def sourceOf(siteId: SiteId, card: SiteDenizenState): RuleSourceRef =
    card match {
      case value: DenizenState => RuleSourceRef.SiteCard(siteId, value.id)
      case value: EdificeState => RuleSourceRef.Edifice(siteId, value.id)
    }
  private def suitOf(catalog: ExecutableCatalog, id: CardId): Option[Suit] =
    catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
      .flatMap(key => Suit.all.find(_.key == key))
}
