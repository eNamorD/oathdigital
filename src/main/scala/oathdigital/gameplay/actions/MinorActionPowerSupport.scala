package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.model._
import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.OathViolation
import oathdigital.gameplay.OathViolation.{UnsupportedMinorActionCatalogInventory,
  UnsupportedMinorActionRule, UnsupportedVisionRule}

/** Transitional audited seam for component behavior relevant to base minor
  * actions. Phase 3 replaces these explicit unsupported classifications with
  * registered executable handlers.
  */
object MinorActionPowerSupport {
  private val ExpectedInventory =
    "5fc88b0d9622a3f523722c288ea7a78d0ec09b7ce191bdabc7f471139ec85898"

  val Conspiracy: VisionId = VisionId("vision:conspiracy")

  private val ActorVisionRestrictions = Set("denizen.vow-of-obedience")
  private val EnemyVisionRestrictions = Set("denizen.secret-police")
  private val EnemyVisionTriggers = Set("denizen.book-binders")
  private val TrueVisionEdifices = Set(
    "edifice.e08.intact", "edifice.e08.ruined")

  def validateInventory(catalog: ExecutableCatalog): Either[OathViolation, Unit] = {
    val actual = CatalogHandlerInventory.structuralFingerprint(catalog)
    Either.cond(actual == ExpectedInventory, (),
      UnsupportedMinorActionCatalogInventory(ExpectedInventory, actual))
  }

  def validateAdviserPlay(catalog: ExecutableCatalog, source: CardId,
      handlers: Vector[String]): Either[OathViolation, Unit] =
    validateInventory(catalog)

  def validateVisionPlay(catalog: ExecutableCatalog,
      source: VisionId): Either[OathViolation, Unit] =
    validateInventory(catalog).flatMap(_ => Left(UnsupportedMinorActionRule(source,
      Vector(if (source == Conspiracy) "vision.conspiracy"
        else "vision.reveal-procedure"))))

  /** Audited fixed-profile boundary shared by every faceup Vision route. */
  def validateFaceupVision(catalog: ExecutableCatalog, ready: ReadyGame,
      actorId: PlayerId, vision: VisionId): Either[OathViolation, Unit] = {
    val altered = ready.game.campaign.foundations.toVector.sortBy(_._1.value)
      .collectFirst { case (number, foundation)
          if foundation.face != FoundationFace.Normal ||
            foundation.alterationSources.nonEmpty => number }
    altered match {
      case Some(number) => Left(UnsupportedVisionRule(
        oathdigital.gameplay.RuleSourceRef.Foundation(number).stableKey,
        "foundation.altered-vision-rules"))
      case None => validateInventory(catalog).flatMap { _ =>
      val current = ready.game.current
      val actor = current.players.find(_.player == actorId).get
      val isConspiracy = vision == Conspiracy

      def handlers(id: DenizenId): Vector[String] = catalog.denizens
        .find(_.id.value == id.value).toVector.flatMap(_.handlers)
      def edificeHandlers(card: EdificeState): Vector[String] = catalog.edifices
        .find(_.id.value == card.id.value).toVector.flatMap { definition => card.side match {
          case EdificeSide.Intact => definition.intact.handlers
          case EdificeSide.Ruined => definition.ruined.handlers
        }}
      def reject(source: oathdigital.gameplay.RuleSourceRef, handler: String) =
        Left(UnsupportedVisionRule(source.stableKey, handler))

      val accessibleSites = current.map.inPlay.filter { siteId =>
        actor.pawnSite.contains(siteId) || SiteRule.ruledBy(
          current.map.sites(siteId).forces, current.players, actorId).getOrElse(false)
      }
      val actorSources = actor.advisers.collect {
        case d: DenizenState if d.orientation == Orientation.FaceUp =>
          oathdigital.gameplay.RuleSourceRef.Adviser(actorId, d.id) -> handlers(d.id)
      } ++ accessibleSites.flatMap { siteId =>
        current.map.sites(siteId).denizens.map {
          case d: DenizenState =>
            oathdigital.gameplay.RuleSourceRef.SiteCard(siteId, d.id) ->
              (if (d.orientation == Orientation.FaceUp) handlers(d.id) else Vector.empty)
          case e: EdificeState =>
            oathdigital.gameplay.RuleSourceRef.Edifice(siteId, e.id) -> edificeHandlers(e)
        }
      } ++ actor.relics.collect {
        case r if r.orientation == Orientation.FaceUp =>
          oathdigital.gameplay.RuleSourceRef.Relic(actorId, r.id) -> catalog.relics
            .find(_.id.value == r.id.value).toVector.flatMap(_.handlers)
      } ++ ready.game.campaign.lineages(actor.lineage).legacies.filter(_.active).map { legacy =>
        oathdigital.gameplay.RuleSourceRef.Legacy(actor.lineage, legacy.id) -> catalog.legacies
          .find(_.id.value == legacy.id.value).toVector.flatMap(_.handlers)
      }
      actorSources.iterator.flatMap { case (source, ids) =>
        ids.filter(ActorVisionRestrictions).map(source -> _)
      }.toVector.sortBy(x => (x._1.stableKey, x._2)).headOption match {
        case Some((source, handler)) => reject(source, handler)
        case None =>
          val pawnRuler = actor.pawnSite.flatMap(current.map.sites.get)
            .flatMap(site => SiteRule.ruler(site.forces, current.players).toOption)
          val police = current.map.inPlay.flatMap { siteId =>
            val site = current.map.sites(siteId)
            val sourceRuler = SiteRule.ruler(site.forces, current.players).toOption
            site.denizens.collect {
              case d: DenizenState if d.orientation == Orientation.FaceUp =>
                (oathdigital.gameplay.RuleSourceRef.SiteCard(siteId, d.id),
                  handlers(d.id), sourceRuler)
            }
          }.flatMap { case (source, ids, ruler) =>
            ids.filter(EnemyVisionRestrictions).filter(_ =>
              ruler == pawnRuler && ruler.exists(r => SiteRule.enemies(
                r, SiteRuler.Player(actorId))))
              .map(source -> _)
          }.sortBy(x => (x._1.stableKey, x._2)).headOption
          police match {
            case Some((source, handler)) => reject(source, handler)
            case None =>
              val edifices = current.map.inPlay.flatMap { siteId =>
                current.map.sites(siteId).denizens.collect { case e: EdificeState =>
                  (oathdigital.gameplay.RuleSourceRef.Edifice(siteId, e.id), siteId,
                    edificeHandlers(e))
                }
              }.flatMap { case (source, siteId, ids) =>
                ids.filter(TrueVisionEdifices).filter {
                  case "edifice.e08.intact" => !isConspiracy && !actor.pawnSite.contains(siteId)
                  case "edifice.e08.ruined" => !isConspiracy && actor.pawnSite.contains(siteId)
                  case _ => false
                }.map(source -> _)
              }.sortBy(x => (x._1.stableKey, x._2)).headOption
              edifices match {
                case Some((source, handler)) => reject(source, handler)
                case None =>
                  val enemyTriggers = current.players.filterNot(_.player == actorId).flatMap { p =>
                    val advisers = p.advisers.collect {
                      case d: DenizenState if d.orientation == Orientation.FaceUp =>
                        oathdigital.gameplay.RuleSourceRef.Adviser(p.player, d.id) -> handlers(d.id)
                    }
                    val sites = current.map.inPlay.filter { siteId =>
                      p.pawnSite.contains(siteId) || SiteRule.ruledBy(
                        current.map.sites(siteId).forces, current.players,
                        p.player).getOrElse(false)
                    }.flatMap { siteId => current.map.sites(siteId).denizens.collect {
                      case d: DenizenState if d.orientation == Orientation.FaceUp =>
                        oathdigital.gameplay.RuleSourceRef.SiteCard(siteId, d.id) -> handlers(d.id)
                    }}
                    advisers ++ sites
                  }.flatMap { case (source, ids) =>
                    ids.filter(EnemyVisionTriggers).map(source -> _)
                  }
                    .sortBy(x => (x._1.stableKey, x._2)).headOption
                  enemyTriggers match {
                    case Some((source, handler)) => reject(source, handler)
                    case None => Right(())
                  }
              }
          }
      }
      }
    }
  }

}
