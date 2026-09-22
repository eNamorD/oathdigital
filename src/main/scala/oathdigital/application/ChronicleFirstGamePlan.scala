package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

sealed trait ChronicleBridgeFailure extends Product with Serializable
object ChronicleBridgeFailure {
  final case class TooFewAtlasSites(actual: Int) extends ChronicleBridgeFailure
  final case class MissingHomelandEdifice(site: SiteId) extends ChronicleBridgeFailure
}

/**
 * Bridges a Chronicle into the unchanged `FirstGameSetupPlan` shape so
 * `FirstGameSetupRules` can run from Chronicle input (2026-09-21 Chronicle
 * design, slice 1). Slice 2 replaces this with setup that reads a Chronicle
 * directly.
 */
object ChronicleFirstGamePlan {
  def build(catalog: ExecutableCatalog, chronicle: Chronicle,
      config: FirstGameBootstrapConfig)
      : Either[ChronicleBridgeFailure, FirstGameSetupPlan] =
    if (chronicle.atlasBox.size < 8)
      Left(ChronicleBridgeFailure.TooFewAtlasSites(chronicle.atlasBox.size))
    else {
      val inPlay = chronicle.atlasBox.take(8)
      homelandEdifices(catalog, inPlay).map { homelands =>
        val dealt = 6 + config.participants.size * 3
        val remaining = chronicle.worldDeck.drop(dealt)
        val worldDeckOrder: Vector[WorldCardId] =
          remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
            remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
            remaining.drop(25)
        FirstGameSetupPlan(
          catalog.ref,
          config.participants,
          config.firstPlayer,
          inPlay.map(_.site),
          chronicle.worldDeck,
          worldDeckOrder,
          chronicle.relicDeck,
          homelands
        )
      }
    }

  private def homelandEdifices(catalog: ExecutableCatalog,
      inPlay: Vector[StoredSite])
      : Either[ChronicleBridgeFailure, Vector[(SiteId, EdificeId)]] = {
    val sitesById = catalog.sites.map(s => s.id -> s).toMap
    inPlay.foldLeft[Either[ChronicleBridgeFailure, Vector[(SiteId, EdificeId)]]](
        Right(Vector.empty)) { (acc, stored) =>
      acc.flatMap { built =>
        homelandSuit(sitesById(stored.site).handlers) match {
          case None => Right(built)
          case Some(_) =>
            stored.items.collectFirst { case id: EdificeId => id } match {
              case Some(edificeId) => Right(built :+ (stored.site -> edificeId))
              case None =>
                Left(ChronicleBridgeFailure.MissingHomelandEdifice(stored.site))
            }
        }
      }
    }
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)
}
