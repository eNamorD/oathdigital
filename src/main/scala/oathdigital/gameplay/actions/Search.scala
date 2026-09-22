package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerRuntime
import oathdigital.model._

/** Pure Search source and cost rules shared by the walker and projections. */
object SearchRules {
  import OathViolation._

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] =
    PowerRuntime.requireAudited(catalog)

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

  /** How many cards a Search draws before any power changes it. */
  val DrawSize: Int = 3

  /** World decks use head-as-top; discard piles use last-as-top. `extra` is the
    * number of cards a power adds to the printed draw.
    */
  def draw(ready: ReadyGame, source: SearchSource, origin: Region,
      extra: Int = 0): Either[OathViolation, Vector[WorldCardId]] =
    cost(ready, source, origin).map { _ => source match {
      case SearchSource.WorldDeck =>
        ready.game.current.commonCards.worldDeck.take(DrawSize + extra)
          .takeThrough(_.isInstanceOf[VisionId])
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse
          .take(DrawSize + extra)
    }}

  private implicit final class TakeThrough[A](private val values: Vector[A])
      extends AnyVal {
    def takeThrough(stop: A => Boolean): Vector[A] = {
      val index = values.indexWhere(stop)
      if (index < 0) values else values.take(index + 1)
    }
  }
}
