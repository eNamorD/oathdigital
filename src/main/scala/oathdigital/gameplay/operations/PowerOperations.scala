package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.OathViolation._
import oathdigital.model._

sealed trait ResourceKind extends Product with Serializable { def key: String }
object ResourceKind {
  case object Favor extends ResourceKind { val key = "favor" }
  case object Secret extends ResourceKind { val key = "secret" }
  val values = Vector(Favor, Secret)
}

sealed trait CostDisposition extends Product with Serializable { def key: String }
object CostDisposition {
  case object PlaceOnSource extends CostDisposition { val key = "place-on-source" }
  case object Burn extends CostDisposition { val key = "burn" }
  val values = Vector(PlaceOnSource, Burn)
}

final case class ResourceCost(resource: ResourceKind, amount: Int,
    disposition: CostDisposition) {
  require(amount > 0, "resource cost amount must be positive")
}
final case class CostDescription(resource: String, amount: Int,
    disposition: String)
final case class Payment(playerId: PlayerId, source: RuleSourceRef,
    costs: Vector[ResourceCost])
final case class RelicPlacement(playerId: PlayerId, relicId: RelicId,
    siteId: SiteId, orientation: Orientation)

object PayCosts {
  def describe(costs: Vector[ResourceCost]): Vector[CostDescription] =
    costs.map(cost => CostDescription(cost.resource.key, cost.amount,
      cost.disposition.key))

  def affordable(ready: ReadyGame, actor: PlayerId, source: RuleSourceRef,
      costs: Vector[ResourceCost]): Boolean =
    validate(ready, actor, source, costs).isRight

  def plan(ready: ReadyGame, actor: PlayerId, source: RuleSourceRef,
      costs: Vector[ResourceCost]): Either[OathViolation, Payment] =
    validate(ready, actor, source, costs).map(_ =>
      Payment(actor, source, costs))

  private def validate(ready: ReadyGame, actor: PlayerId, source: RuleSourceRef,
      costs: Vector[ResourceCost]): Either[OathViolation, Unit] = for {
    _ <- Either.cond(costs.nonEmpty, (), InvalidEventOrder("cost list is empty"))
    _ <- Either.cond(costs.forall(_.amount > 0), (),
      InvalidEventOrder("cost amounts must be positive"))
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
    favor = costs.filter(_.resource == ResourceKind.Favor).map(_.amount).sum
    secrets = costs.filter(_.resource == ResourceKind.Secret).map(_.amount).sum
    _ <- Either.cond(player.board.favor >= favor, (),
      InsufficientFavor(favor, player.board.favor))
    _ <- Either.cond(player.board.faceUpSecrets >= secrets, (),
      InsufficientSecrets(secrets, player.board.faceUpSecrets))
    _ <- Either.cond(sourceExists(ready, source), (),
      InvalidEventOrder("cost source does not exist"))
    places = costs.exists(_.disposition == CostDisposition.PlaceOnSource)
    _ <- Either.cond(!places || tokenBearing(source), (),
      InvalidEventOrder("placed cost source is not a token-bearing card"))
  } yield ()

  private def tokenBearing(source: RuleSourceRef): Boolean = source match {
      case _: RuleSourceRef.SiteCard | _: RuleSourceRef.Edifice |
          _: RuleSourceRef.Adviser | _: RuleSourceRef.Relic |
          _: RuleSourceRef.SiteRelic => true
      case _ => false
    }

  private def sourceExists(ready: ReadyGame,
      source: RuleSourceRef): Boolean = {
    val current = ready.game.current
    source match {
      case RuleSourceRef.SiteCard(site, id) => current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == id))
      case RuleSourceRef.Edifice(site, id) => current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == id))
      case RuleSourceRef.Adviser(owner, id) => current.players.find(
        _.player == owner).exists(_.advisers.exists(_.id == id))
      case RuleSourceRef.Relic(owner, id) => current.players.find(
        _.player == owner).exists(_.relics.exists(_.id == id))
      case RuleSourceRef.SiteRelic(site, id) => current.map.sites.get(site)
        .exists(_.relics.exists(_.id == id))
      case RuleSourceRef.Site(site) => current.map.sites.contains(site)
      case RuleSourceRef.Banner(id) => id == "peoples-favor" ||
        id == "darkest-secret"
      case RuleSourceRef.Foundation(number) =>
        ready.game.campaign.foundations.contains(number)
      case RuleSourceRef.Legacy(lineage, id) => ready.game.campaign.lineages
        .get(lineage).exists(_.legacies.exists(_.id == id))
      case RuleSourceRef.GameRule(_) => true
    }
  }

}

/** Converts canonical power-event facts into glossary operations. Event-owned
  * handlers validate affordability, source identity, and placement legality
  * before calling this adapter.
  */
object PowerOperationPlanner {
  def payment(payment: Payment): Either[OathViolation, Vector[CoreOperation]] =
    payment.costs.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
      Right(Vector.empty)) { (result, cost) =>
      for {
        operations <- result
        operation <- cost.disposition match {
          case CostDisposition.PlaceOnSource =>
            sourceLocation(payment.source).map(location => Give(
              piece(cost), payment.playerId,
              Location.PlayArea(payment.playerId), location))
          case CostDisposition.Burn => Right(cost.resource match {
            case ResourceKind.Favor => Burn.favor(cost.amount,
              PositionedLocation(Location.PlayArea(payment.playerId)))
            case ResourceKind.Secret => Burn.secrets(cost.amount,
              PositionedLocation(Location.PlayArea(payment.playerId)))
          })
        }
      } yield operations :+ operation
    }

  def placement(placement: RelicPlacement): CoreOperation = Play(
    placement.relicId,
    PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
    Location.Site(placement.siteId),
    placement.orientation)

  private def piece(cost: ResourceCost): Piece.Counted = cost.resource match {
    case ResourceKind.Favor => Piece.Favor(cost.amount)
    case ResourceKind.Secret => Piece.Secrets(cost.amount)
  }

  private def sourceLocation(
      source: RuleSourceRef
  ): Either[OathViolation, Location] = source match {
    case RuleSourceRef.SiteCard(_, id) => Right(Location.OnCard(id))
    case RuleSourceRef.Edifice(_, id) => Right(Location.OnCard(id))
    case RuleSourceRef.Adviser(_, id) => Right(Location.OnCard(id))
    case RuleSourceRef.Relic(_, id) => Right(Location.OnCard(id))
    case RuleSourceRef.SiteRelic(_, id) => Right(Location.OnCard(id))
    case _ => Left(InvalidEventOrder(
      "placed cost source is not a token-bearing card"))
  }
}

object DrawTopRelic {
  def plan(ready: ReadyGame): Either[OathViolation, RelicId] =
    ready.game.current.commonCards.relicDeck.headOption.toRight(
      RecoverUnavailable("relic deck is empty"))
  def validate(ready: ReadyGame, relic: RelicId): Either[OathViolation, Unit] =
    DrawTopRelic.plan(ready).flatMap(top => Either.cond(top == relic, (),
      RecoverOutcomeMismatch("relic is not the top of the relic deck")))
}

object PlaceRelicAtSite {
  def plan(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      relic: RelicId, site: SiteId, orientation: Orientation)
      : Either[OathViolation, RelicPlacement] = for {
    _ <- validate(catalog, ready, relic, site, orientation)
  } yield RelicPlacement(actor, relic, site, orientation)

  private def validate(catalog: ExecutableCatalog, ready: ReadyGame,
      relic: RelicId, siteId: SiteId, orientation: Orientation) = for {
    _ <- Either.cond(orientation == Orientation.FaceDown, (),
      InvalidEventOrder("initial relic placement must be facedown"))
    _ <- DrawTopRelic.validate(ready, relic)
    site <- ready.game.current.map.sites.get(siteId).toRight(SiteNotInPlay(siteId))
    definition <- catalog.sites.find(_.id == siteId).toRight(SiteNotInPlay(siteId))
    _ <- Either.cond(site.relics.size < definition.relicSlots, (),
      RecoverUnavailable("site has no empty relic slot"))
  } yield ()
}
