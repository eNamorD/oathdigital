package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerRuntime
import oathdigital.model._

/** Pure Search source and cost rules shared by the walker and projections. */
object SearchRules {
  import OathViolation._

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Search is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Search")
      else None
    reason.fold[Either[OathViolation, Unit]](
      PowerRuntime.requireAudited(catalog))(
      value => Left(UnsupportedSearchState(value)))
  }

  def cost(ready: ReadyGame, source: SearchSource,
      origin: Region): Either[OathViolation, Int] = source match {
    case SearchSource.WorldDeck =>
      Right(ready.game.current.tracks.visionsDrawn match {
        case 0 => 2
        case 1 | 2 => 3
        case _ => 4
      })
    case SearchSource.RegionalDiscard(region) if region == origin => Right(2)
    case SearchSource.RegionalDiscard(_) => Left(SearchSourceUnavailable(source))
  }

  /** World decks use head-as-top; discard piles use last-as-top. */
  def draw(ready: ReadyGame, source: SearchSource,
      origin: Region): Either[OathViolation, Vector[WorldCardId]] =
    cost(ready, source, origin).map { _ => source match {
      case SearchSource.WorldDeck =>
        ready.game.current.commonCards.worldDeck.take(3)
          .takeThrough(_.isInstanceOf[VisionId])
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse.take(3)
    }}

  private implicit final class TakeThrough[A](private val values: Vector[A])
      extends AnyVal {
    def takeThrough(stop: A => Boolean): Vector[A] = {
      val index = values.indexWhere(stop)
      if (index < 0) values else values.take(index + 1)
    }
  }
}
