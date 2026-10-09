package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.actions.AdviserFace
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.model._

object LostTapestryCard extends Relic(RelicId("R29"), "Lost Tapestry", value = 85, defense = 1):
  val power = PrintedPower(PowerId("relic.lost-tapestry"),
    persistent = true, cost = Cost.free,
    text = "Facedown Visions do not count against your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Lost Tapestry (relic R29): "Facedown Visions do not count against your
  * adviser limit."
  *
  * While its holder has it faceup, a transform at `SearchPlayAdviser` marks
  * the holder's facedown Visions, and a Vision the holder plays, as not
  * counting facedown (`CardPlayRules.ignoreLimit`). Card play checks the
  * advisers as they would be after the play, so a Vision played facedown
  * takes a free slot at the limit, and a held one is shown unavailable for
  * Replace. `AdviserLimit` reads `ignored` for Horned Mask and the board.
  * It writes no line.
  */
case object LostTapestry extends ContributingPower:
  val id: PowerId = LostTapestryCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if holds(ctx.state, ctx.activePlayer) =>
          val played = tree.card match
            case vision: VisionId =>
              Vector(AdviserFace(vision, Orientation.FaceDown))
            case _ => Vector.empty
          tree.adjust(children)(rules =>
            (ignored(ctx.state, ctx.activePlayer) ++ played)
              .foldLeft(rules)(_.ignoreLimit(_)))
        case _ => children
      })))

  /** Whether `player` has Lost Tapestry faceup. */
  def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.relics.exists {
        case RelicState(card, Orientation.FaceUp, _) =>
          card == LostTapestryCard.id
        case _ => false
      })

  /** The advisers of `player` that Lost Tapestry keeps from counting: their
    * facedown Visions, while they have it faceup. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    if !holds(ready, player) then Vector.empty
    else ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers.collect {
        case VisionState(vision, Orientation.FaceDown) =>
          AdviserFace(vision, Orientation.FaceDown)
      })
