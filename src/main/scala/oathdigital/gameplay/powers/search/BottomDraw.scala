package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._

/** How a Search modifier makes the draw take cards from the bottom of its
  * pile (Wine of Welcome, Mushrooms).
  *
  * It wraps the Search's draw node, as [[DrawExtension]] does, and draws
  * `count` cards from the bottom of the pile the node drew from, when
  * `takes` accepts that pile. A power using it folds at [[Priority]],
  * before the draw extensions, so an extension then adds cards from the
  * same end. The world deck's Vision stop still applies, counted from the
  * bottom.
  */
private[search] object BottomDraw:
  /** Before the draw extensions, which fold at 0. */
  val Priority: Int = -1

  def reshaped(operations: Vector[Operation], count: Int,
      takes: SearchSource => Boolean): Vector[Operation] =
    operations.map:
      case node: BuildOps if node.window.contains(PowerWindow.SearchBeforeDraw) =>
        node.copy(build = (ready, pending) =>
          node.build(ready, pending).flatMap(redrawn(ready, count, takes)))
      case other => other

  private def redrawn(ready: ReadyGame, count: Int,
      takes: SearchSource => Boolean)(operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match
      case None => Right(operations)
      case Some(draw) => DrawExtension.sourceOf(draw.source).map(source =>
        if !takes(source) then operations
        else DrawExtension.redrawn(
          draw.copy(position = StackPosition.Bottom), source,
          SearchRules.draw(ready, source,
            extra = count - SearchRules.DrawSize,
            position = StackPosition.Bottom)))
