package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.OathState.Ready
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

/** Concrete semantic mutations recorded by operation-backed power events.
  * Power-specific adapters remain responsible for reconstructing the expected
  * sequence before these operations are applied.
  */
sealed trait RecordedPowerOperation extends Product with Serializable
object RecordedPowerOperation {
  final case class Pay(payment: Payment) extends RecordedPowerOperation
  final case class PlaceRelic(placement: RelicPlacement)
      extends RecordedPowerOperation

  def evolve(catalog: ExecutableCatalog, state: OathState,
      operations: Vector[RecordedPowerOperation])
      : Either[OathViolation, OathState] = for {
    _ <- Either.cond(operations.nonEmpty, (),
      InvalidEventOrder("recorded power operation list is empty"))
    evolved <- operations.foldLeft[Either[OathViolation, OathState]](Right(state)) {
      case (current, Pay(payment)) =>
        current.flatMap(PayCosts.evolve(_, payment))
      case (current, PlaceRelic(placement)) =>
        current.flatMap(PlaceRelicAtSite.evolve(catalog, _, placement))
    }
  } yield evolved
}

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

  def evolve(state: OathState, payment: Payment)
      : Either[OathViolation, OathState] = state match {
    case Ready(ready) => validate(ready, payment.playerId, payment.source,
      payment.costs).map { _ =>
      val favor = payment.costs.filter(_.resource == ResourceKind.Favor).map(_.amount).sum
      val secrets = payment.costs.filter(_.resource == ResourceKind.Secret).map(_.amount).sum
      val placedFavor = payment.costs.filter(c => c.resource == ResourceKind.Favor &&
        c.disposition == CostDisposition.PlaceOnSource).map(_.amount).sum
      val placedSecrets = payment.costs.filter(c => c.resource == ResourceKind.Secret &&
        c.disposition == CostDisposition.PlaceOnSource).map(_.amount).sum
      val current = ready.game.current
      Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
        players = current.players.map { player =>
          val paid = if (player.player != payment.playerId) player else player.copy(
            board = player.board.copy(favor = player.board.favor - favor,
              faceUpSecrets = player.board.faceUpSecrets - secrets))
          updateOwnedSource(paid, payment.source, placedFavor, placedSecrets)
        },
        map = updateSiteSource(current.map, payment.source, placedFavor, placedSecrets))))
    }
    case _ => Left(GameNotStarted)
  }

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

  private def add(card: SiteDenizenState, favor: Int, secrets: Int) = card match {
    case d: DenizenState => d.copy(tokens = Tokens(d.tokens.favor + favor,
      d.tokens.secrets + secrets))
    case e: EdificeState => e.copy(tokens = Tokens(e.tokens.favor + favor,
      e.tokens.secrets + secrets))
  }
  private def addAdviser(card: DenizenState, favor: Int, secrets: Int) =
    card.copy(tokens = Tokens(card.tokens.favor + favor,
      card.tokens.secrets + secrets))
  private def updateSiteSource(map: MapState, source: RuleSourceRef,
      favor: Int, secrets: Int): MapState = source match {
    case RuleSourceRef.SiteCard(site, id) => map.copy(sites = map.sites.updated(site,
      map.sites(site).copy(denizens = map.sites(site).denizens.map(card =>
        if (card.id == id) add(card, favor, secrets) else card))))
    case RuleSourceRef.Edifice(site, id) => map.copy(sites = map.sites.updated(site,
      map.sites(site).copy(denizens = map.sites(site).denizens.map(card =>
        if (card.id == id) add(card, favor, secrets) else card))))
    case RuleSourceRef.SiteRelic(site, id) => map.copy(sites = map.sites.updated(site,
      map.sites(site).copy(relics = map.sites(site).relics.map(relic =>
        if (relic.id == id) relic.copy(tokens = Tokens(
          relic.tokens.favor + favor, relic.tokens.secrets + secrets)) else relic))))
    case _ => map
  }
  private def updateOwnedSource(player: PlayerState, source: RuleSourceRef,
      favor: Int, secrets: Int): PlayerState = source match {
    case RuleSourceRef.Adviser(owner, id) if owner == player.player => player.copy(
      advisers = player.advisers.map {
        case d: DenizenState if d.id == id => addAdviser(d, favor, secrets)
        case other => other })
    case RuleSourceRef.Relic(owner, id) if owner == player.player => player.copy(
      relics = player.relics.map(r => if (r.id == id) r.copy(tokens = Tokens(
        r.tokens.favor + favor, r.tokens.secrets + secrets)) else r))
    case _ => player
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

  def evolve(catalog: ExecutableCatalog, state: OathState,
      placement: RelicPlacement): Either[OathViolation, OathState] = state match {
    case Ready(ready) => validate(catalog, ready, placement.relicId,
      placement.siteId, placement.orientation).map { _ =>
      val current = ready.game.current
      val site = current.map.sites(placement.siteId)
      Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
        commonCards = current.commonCards.copy(
          relicDeck = current.commonCards.relicDeck.tail),
        map = current.map.copy(sites = current.map.sites.updated(placement.siteId,
          site.copy(relics = site.relics :+ RelicState(placement.relicId,
            placement.orientation, Tokens.empty)))))))
    }
    case _ => Left(GameNotStarted)
  }

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
