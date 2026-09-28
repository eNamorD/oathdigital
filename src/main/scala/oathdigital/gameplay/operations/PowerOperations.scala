package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.model._

object Costs:
  def affordable(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost, intoOccupied: Boolean = false): Boolean =
    plan(ready, actor, placedAt, cost, intoOccupied).isRight

  /** Pre-flight affordability + placement validation owned by the caller's
    * power or action path. Rejects unaffordable costs early with a typed
    * OathViolation; the executor remains authoritative for atomic batch
    * sufficiency. A free cost (Cost.free) is always affordable and needs no
    * destination check.
    */
  def plan(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost, intoOccupied: Boolean = false)
      : Either[OathViolation, PayCost] =
    if cost == Cost.free then Right(PayCost(actor, placedAt, cost))
    else
      for
        player <- ready.game.current.players.find(_.player == actor)
          .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
        favor = cost.favor + cost.favorBurnt
        secrets = cost.secret + cost.secretBurnt
        _ <- Either.cond(player.board.favor >= favor, (),
          InsufficientFavor(favor, player.board.favor))
        _ <- Either.cond(player.board.faceUpSecrets >= secrets, (),
          InsufficientSecrets(secrets, player.board.faceUpSecrets))
        _ <- validatePlaced(ready, placedAt, cost, intoOccupied)
      yield PayCost(actor, placedAt, cost, intoOccupied)

  /** The placement every card-sourced cost uses: onto the card, with the
    * card's suit as its off-turn settlement bank. Relics have no suit.
    */
  def onCard(actor: PlayerId, card: CardId, cost: Cost,
      catalog: ExecutableCatalog, intoOccupied: Boolean = false): PayCost =
    PayCost(actor, Location.OnCard(card), cost, intoOccupied,
      catalog.suitOf(card))

  private def validatePlaced(ready: ReadyGame, placedAt: Location,
      cost: Cost, intoOccupied: Boolean): Either[OathViolation, Unit] =
    if cost.favor + cost.secret == 0 then Right(())
    else
      placedAt match
        case Location.OnCard(id) if statefulCard(ready, id) =>
          Either.cond(intoOccupied || PayCostRules.isEmpty(ready, id), (),
            EconomyCardNotEmpty(id))
        case _ => Left(InvalidEventOrder(
          "placed cost portions require an existing token-bearing card"))

  private def statefulCard(ready: ReadyGame, id: CardId): Boolean =
    CardIndex.from(ready.game).toOption.exists { index =>
      index.get(id).flatMap(_.state).exists:
        case _: DenizenState | _: EdificeState | _: RelicState => true
        case _ => false
    }
