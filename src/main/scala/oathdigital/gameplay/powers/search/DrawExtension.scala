package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._

/** How a Search modifier makes the draw take more cards.
  *
  * A Search's draw is one windowed `BuildOps` (`SearchBeforeDraw`) that
  * returns a `Draw`. The extension wraps that node so its result draws `more`
  * cards beyond what it already draws, by asking [[SearchRules.draw]] for the
  * whole longer draw again. Asking the rule rather than counting cards here
  * keeps the draw's own limits: a world draw still stops after a Vision, a
  * pile that is short gives what it has, and a regional pile is drawn from its
  * top.
  *
  * The extension adds to what the wrapped node drew, so two extensions compose
  * in either order. It replaces the count of Visions drawn along with the cards,
  * because a longer draw may reach a Vision the printed one did not.
  */
private[search] object DrawExtension {

  def extend(operations: Vector[Operation], more: Int): Vector[Operation] =
    operations.map {
      case node: BuildOps if node.window.contains(PowerWindow.SearchBeforeDraw) =>
        node.copy(build = (ready, pending) =>
          node.build(ready, pending).flatMap(extended(ready, more)))
      case other => other
    }

  private def extended(ready: ReadyGame, more: Int)(
      operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match {
      case None => Right(operations)
      case Some(draw) => for {
        source <- sourceOf(draw.source)
        origin <- ready.game.current.players.find(_.player == draw.player)
          .flatMap(_.pawnSite).flatMap(ready.game.current.map.regionOf)
          .toRight(OathViolation.PawnSiteMissing(draw.player))
        cards <- SearchRules.draw(ready, source, origin,
          extra = draw.cards.size + more - SearchRules.DrawSize)
      } yield Vector[CoreOperation](draw.copy(cards = cards)) ++
        Option.when(source == SearchSource.WorldDeck &&
          cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn)
    }

  private def sourceOf(location: Location)
      : Either[OathViolation, SearchSource] = location match {
    case Location.Deck(CardDeck.World) => Right(SearchSource.WorldDeck)
    case Location.RegionalDiscard(region) =>
      Right(SearchSource.RegionalDiscard(region))
    case other => Left(OathViolation.InvalidEventOrder(
      s"a Search draws from a world deck or a regional discard, not $other"))
  }
}
