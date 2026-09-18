package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.model._

final case class RelicPlacement(playerId: PlayerId, relicId: RelicId,
    siteId: SiteId, orientation: Orientation)

object Costs {
  def affordable(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost): Boolean = plan(ready, actor, placedAt, cost).isRight

  /** Pre-flight affordability + placement validation owned by the caller's
    * power or action path. Rejects unaffordable costs early with a typed
    * OathViolation; the executor remains authoritative for atomic batch
    * sufficiency. A free cost (Cost.free) is always affordable and needs no
    * destination check.
    */
  def plan(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost): Either[OathViolation, PayCost] =
    if (cost == Cost.free) Right(PayCost(actor, placedAt, cost))
    else
      for {
        player <- ready.game.current.players.find(_.player == actor)
          .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
        favor = cost.favor + cost.favorBurnt
        secrets = cost.secret + cost.secretBurnt
        _ <- Either.cond(player.board.favor >= favor, (),
          InsufficientFavor(favor, player.board.favor))
        _ <- Either.cond(player.board.faceUpSecrets >= secrets, (),
          InsufficientSecrets(secrets, player.board.faceUpSecrets))
        _ <- validatePlaced(ready, placedAt, cost)
      } yield PayCost(actor, placedAt, cost)

  private def validatePlaced(ready: ReadyGame, placedAt: Location,
      cost: Cost): Either[OathViolation, Unit] =
    if (cost.favor + cost.secret == 0) Right(())
    else
      placedAt match {
        case Location.OnCard(id) if statefulCard(ready, id) => Right(())
        case _ => Left(InvalidEventOrder(
          "placed cost portions require an existing token-bearing card"))
      }

  private def statefulCard(ready: ReadyGame, id: CardId): Boolean =
    CardIndex.from(ready.game).toOption.exists { index =>
      index.get(id).flatMap(_.state).exists {
        case _: DenizenState | _: EdificeState | _: RelicState => true
        case _ => false
      }
    }
}

/** Converts canonical power-event facts into glossary operations. Event-owned
  * handlers validate affordability, source identity, and placement legality
  * (Costs.plan / affordable) before calling this adapter; the executor
  * then re-checks the emitted batch atomically, so this adapter never needs
  * its own sufficiency audit.
  */
object PowerOperationPlanner {
  def placement(placement: RelicPlacement): CoreOperation = Play(
    placement.relicId,
    PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
    Location.Site(placement.siteId),
    placement.orientation)
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
