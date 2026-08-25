package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Orientation is a factual property only; callers still own activation rules. */
sealed trait RuleSourceFace extends Product with Serializable
object RuleSourceFace {
  case object FaceUp extends RuleSourceFace
  case object FaceDown extends RuleSourceFace
  case object Intact extends RuleSourceFace
  case object Ruined extends RuleSourceFace
  case object Printed extends RuleSourceFace
  case object Active extends RuleSourceFace
  case object Inactive extends RuleSourceFace
}

final case class IndexedRuleSource(
    source: RuleSourceRef,
    handlerIds: Vector[String],
    face: RuleSourceFace
)

/** Redaction-neutral inventory of runtime sources and their declared handlers.
  * This index deliberately makes no accessibility, relevance, or activation
  * decision.
  */
object RuleSourceIndex {
  def enumerate(
      catalog: ExecutableCatalog,
      ready: ReadyGame
  ): Vector[IndexedRuleSource] = {
    val current = ready.game.current
    val sites = current.map.inPlay.flatMap { siteId =>
      val printed = catalog.sites.find(_.id == siteId).toVector.map(definition =>
        IndexedRuleSource(RuleSourceRef.Site(siteId), definition.handlers,
          RuleSourceFace.Printed))
      val cards = current.map.sites(siteId).denizens.flatMap {
        case denizen: DenizenState =>
          catalog.denizens.find(_.id.value == denizen.id.value).toVector.map(
            definition => IndexedRuleSource(
              RuleSourceRef.SiteCard(siteId, denizen.id), definition.handlers,
              orientation(denizen.orientation)))
        case edifice: EdificeState =>
          catalog.edifices.find(_.id.value == edifice.id.value).toVector.map {
            definition =>
              val face = if (edifice.side == EdificeSide.Intact)
                definition.intact else definition.ruined
              IndexedRuleSource(RuleSourceRef.Edifice(siteId, edifice.id),
                face.handlers, if (edifice.side == EdificeSide.Intact)
                  RuleSourceFace.Intact else RuleSourceFace.Ruined)
          }
      }
      printed ++ cards
    }
    val players = current.players.flatMap { player =>
      val advisers = player.advisers.collect { case denizen: DenizenState =>
        catalog.denizens.find(_.id.value == denizen.id.value).toVector.map(
          definition => IndexedRuleSource(
            RuleSourceRef.Adviser(player.player, denizen.id), definition.handlers,
            orientation(denizen.orientation)))
      }.flatten
      val relics = player.relics.flatMap { relic =>
        catalog.relics.find(_.id.value == relic.id.value).toVector.map(
          definition => IndexedRuleSource(
            RuleSourceRef.Relic(player.player, relic.id), definition.handlers,
            orientation(relic.orientation)))
      }
      advisers ++ relics
    }
    val legacies = ready.game.campaign.lineages.toVector.sortBy(_._1.value)
      .flatMap { case (lineageId, lineage) =>
        lineage.legacies.flatMap { legacy =>
          catalog.legacies.find(_.id.value == legacy.id.value).toVector.map(
            definition => IndexedRuleSource(
              RuleSourceRef.Legacy(lineageId, legacy.id), definition.handlers,
              if (legacy.active) RuleSourceFace.Active else RuleSourceFace.Inactive))
        }
      }
    sites ++ players ++ legacies
  }

  private def orientation(value: Orientation): RuleSourceFace = value match {
    case Orientation.FaceUp => RuleSourceFace.FaceUp
    case Orientation.FaceDown => RuleSourceFace.FaceDown
  }
}
