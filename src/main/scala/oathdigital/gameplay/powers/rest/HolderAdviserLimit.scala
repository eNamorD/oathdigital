package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.model._

/** "You can only have two advisers", printed on an adviser: refactor P4 of
  * catalog batch 3, shared by Silver Tongue and Insomnia.
  *
  * It is a transform at `SearchPlayAdviser`. While `cardId` is faceup in the
  * acting player's advisers, it lowers that player's limit to `limit`. While
  * `cardId` is the card being played, it sets the played card's own limit
  * (the rulebook checks such a play against the new limit), so playing
  * it faceup needs room under the limit. Card play offers no adviser slot
  * past a limit, so nothing is checked after the play, and the limit writes
  * no line. A limit only lowers, so two such cards compose and the lowest
  * holds.
  */
final case class HolderAdviserLimit(cardId: DenizenId, limit: Int):
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
        tree.adjust(children)(_.limitAdvisers(limit))
      case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
        tree.adjust(children)(_.limitPlayedCard(limit))
      case _ => children
    })
