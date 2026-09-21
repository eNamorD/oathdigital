package oathdigital.application

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameRulesData

final case class FirstGameBootstrapConfig(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId
)

final case class BootstrapPlanFailure(message: String)

trait FirstGamePlanFactory {
  def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGameSetupPlan]
}

/**
 * Development-only deterministic plan derivation.
 *
 * This is reproducible fixture construction, not production randomness.
 */
final class DevelopmentFirstGamePlanFactory(catalog: ExecutableCatalog)
    extends FirstGamePlanFactory {
  override def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGameSetupPlan] =
    for {
      sites <- selectedSites
      denizens <- selectedDenizens
      homelands <- homelandEdifices(sites)
    } yield {
      val dealt = 6 + config.participants.size * 3
      val remaining = denizens.drop(dealt)
      val world: Vector[WorldCardId] =
        remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
          remaining.slice(10, 25) ++
          FirstGameRulesData.visions.drop(2) ++
          remaining.drop(25)
      val relics = catalog.relics
        .filter(_.role == RelicRole.Ordinary)
        .sortBy(relic => relic.value -> relic.id.value)
        .map(relic => RelicId(relic.id.value))

      FirstGameSetupPlan(
        catalog.ref,
        config.participants,
        config.firstPlayer,
        sites,
        denizens,
        world,
        relics,
        homelands
      )
    }

  private def selectedSites
      : Either[BootstrapPlanFailure, Vector[SiteId]] = {
    val sites = catalog.sites.sortBy(_.id.value).take(8).map(_.id)
    if (sites.size == 8) Right(sites)
    else Left(BootstrapPlanFailure(
      s"catalog has ${sites.size} sites; first-game setup requires 8"
    ))
  }

  /** The dev plan deals from suits in key order. This is a fixed selection
    * order for reproducible dev games, not the rules order in `Suit.all`. */
  private val alphabeticalSuits: Vector[Suit] = Suit.all.sortBy(_.key)

  private def selectedDenizens
      : Either[BootstrapPlanFailure, Vector[DenizenId]] = {
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

  private def homelandEdifices(
      sites: Vector[SiteId]
  ): Either[BootstrapPlanFailure, Vector[(SiteId, EdificeId)]] =
    sites.foldLeft[
      Either[BootstrapPlanFailure, Vector[(SiteId, EdificeId)]]
    ](Right(Vector.empty)) {
      case (Right(accumulated), siteId) =>
        val site = catalog.sites.find(_.id == siteId).get
        homelandSuit(site.handlers) match {
          case None => Right(accumulated)
          case Some(suit) =>
            catalog.edifices.filter(_.suit == suit)
              .sortBy(_.id.value).headOption match {
              case Some(edifice) =>
                Right(accumulated :+ (siteId -> EdificeId(edifice.id.value)))
              case None =>
                Left(BootstrapPlanFailure(
                  s"no edifice exists for Homeland suit '${suit.key}'"
                ))
            }
        }
      case (failure @ Left(_), _) => failure
    }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)
}
