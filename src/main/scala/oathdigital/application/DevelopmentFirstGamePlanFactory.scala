package oathdigital.application

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.model._

final case class FirstGameBootstrapConfig(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId
)

final case class BootstrapPlanFailure(message: String)

/** A generated Chronicle plus the config it was actually dealt against --
  * `GeneratedFirstGamePlanFactory` shuffles seating and picks a new first
  * player before generating, so the config a caller passed in is stale the
  * moment `build` returns; `resolvedConfig` is the one to deal `SetupOrders`
  * from (2026-09-21 Chronicle design, slice 2).
  */
final case class FirstGamePlan(
    chronicle: Chronicle,
    resolvedConfig: FirstGameBootstrapConfig
)

trait FirstGamePlanFactory {
  def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGamePlan]
}

/**
 * Development-only deterministic Chronicle derivation: assembles a fixed
 * dev Chronicle (first 8 sites, first 10 denizens per suit, lowest-id
 * edifice per suit, all ordinary relics by printed value) (2026-09-21
 * Chronicle design, slice 2).
 *
 * This is reproducible fixture construction, not production randomness.
 */
final class DevelopmentFirstGamePlanFactory(catalog: ExecutableCatalog)
    extends FirstGamePlanFactory {
  override def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGamePlan] =
    devChronicle.map(FirstGamePlan(_, config))

  private def devChronicle: Either[BootstrapPlanFailure, Chronicle] =
    for {
      atlasBox <- devAtlasBox
      denizens <- devDenizens
    } yield Chronicle(
      atlasBox,
      worldDeck = denizens,
      relicDeck = catalog.relics.filter(_.role == RelicRole.Ordinary)
        .sortBy(relic => relic.value -> relic.id.value)
        .map(relic => RelicId(relic.id.value))
    )

  private def devAtlasBox: Either[BootstrapPlanFailure, Vector[StoredSite]] = {
    val orderedSites = catalog.sites.sortBy(_.id.value).map(_.id)
    if (orderedSites.size < 8)
      Left(BootstrapPlanFailure(
        s"catalog has ${orderedSites.size} sites; first-game setup requires 8"
      ))
    else
      orderedSites.take(8).foldLeft[Either[BootstrapPlanFailure, Vector[StoredSite]]](
          Right(Vector.empty)) { (acc, siteId) =>
        acc.flatMap { built =>
          val site = catalog.sites.find(_.id == siteId).get
          homelandSuit(site.handlers) match {
            case None => Right(built :+ StoredSite(siteId))
            case Some(suit) =>
              catalog.edifices.filter(_.suit == suit).sortBy(_.id.value).headOption match {
                case Some(edifice) =>
                  Right(built :+ StoredSite(siteId, Vector(EdificeId(edifice.id.value))))
                case None =>
                  Left(BootstrapPlanFailure(
                    s"no edifice exists for Homeland suit '${suit.key}'"
                  ))
              }
          }
        }
      }
  }

  /** The dev plan deals from suits in key order. This is a fixed selection
    * order for reproducible dev games, not the rules order in `Suit.all`. */
  private val alphabeticalSuits: Vector[Suit] = Suit.all.sortBy(_.key)

  private def devDenizens: Either[BootstrapPlanFailure, Vector[DenizenId]] = {
    val selected = alphabeticalSuits.flatMap { suit =>
      catalog.denizens.filter(_.suit == suit)
        .sortBy(_.id.value)
        .take(10)
        .map(denizen => DenizenId(denizen.id.value))
    }
    alphabeticalSuits.collectFirst {
      case suit
          if catalog.denizens.count(_.suit == suit) < 10 =>
        BootstrapPlanFailure(
          s"catalog suit '${suit.key}' has fewer than 10 denizens"
        )
    }.toLeft(selected)
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)
}
