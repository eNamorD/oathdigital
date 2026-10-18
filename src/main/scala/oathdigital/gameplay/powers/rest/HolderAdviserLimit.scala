package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.model._

/** "You can only have two advisers", printed on an adviser: refactor P4 of
  * catalog batch 3, shared by Silver Tongue and Insomnia.
  *
  * It is a transform at `SearchPlayAdviser`. While the acting player holds
  * `cardId`, in either face, or plays it, it sets `cardId`'s limit
  * (`CardPlayRules.limitWhileFaceUp`). Card play checks the advisers as they
  * would be after the play, so the limit holds only while the card is a
  * faceup adviser then: a facedown play sets none, and replacing the card
  * lifts it. Card play offers no adviser slot past a limit, so the limit
  * writes no line. A limit only lowers, so two such cards compose and the
  * lowest holds.
  */
final case class HolderAdviserLimit(cardId: DenizenId, limit: Int):
  /** `limit`, when `player` holds `cardId` as a faceup adviser. */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    ready.game.current.players.find(_.player == player)
      .filter(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_ => limit)

  /** Whether `player` holds `cardId` among their advisers, in either face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.advisers.exists(_.id == cardId))

  val contribution: Contribution = Transform((ctx, children) =>
    ctx.operation match {
      case tree: CardPlayProcedure.PlacementTree
          if tree.card == cardId || holds(ctx.state, ctx.actor) =>
        tree.adjust(children)(_.limitWhileFaceUp(cardId, limit))
      case _ => children
    })
