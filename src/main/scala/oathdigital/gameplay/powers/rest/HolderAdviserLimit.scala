package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.model._

/** "You can only have two advisers", printed on an adviser: refactor P4 of
  * catalog batch 3, shared by Silver Tongue and Insomnia.
  *
  * It is a transform at `SearchPlayAdviser`. While `cardId` is faceup in the
  * acting player's advisers, it lowers that player's limit to `limit` in both
  * adviser orientations. While `cardId` is the card being played, it lowers
  * the faceup limit, so playing it faceup needs room under the limit. Either
  * way it checks the resulting area after the play. A limit only lowers, so
  * two such cards compose and the lowest holds.
  */
final case class HolderAdviserLimit(cardId: DenizenId, limit: Int,
    name: String):
  /** `limit`, when `player` holds `cardId` as a faceup adviser. */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    ready.game.current.players.find(_.player == player)
      .filter(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_ => limit)

  val contribution: Contribution = Transform((ctx, children) =>
    ctx.operation match {
      case tree: CardPlayProcedure.PlacementTree
          if limitFor(ctx.state, ctx.activePlayer).nonEmpty =>
        tree.adjust(children)(_.limitAdvisers(limit)) :+ guard(ctx.activePlayer)
      case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
        tree.adjust(children)(_.limitFaceupAdvisers(limit)) :+
          guard(ctx.activePlayer)
      case _ => children
    })

  private def guard(actor: PlayerId): Operation = BuildOps((state, _) => {
    val count = state.game.current.players.find(
      _.player == actor).fold(0)(_.advisers.size)
    if limitFor(state, actor).forall(count <= _) then Right(Vector.empty)
    else Left(OathViolation.InvalidEventOrder(
      s"${actor.value} holds $name and can have only $limit advisers"))
  })
