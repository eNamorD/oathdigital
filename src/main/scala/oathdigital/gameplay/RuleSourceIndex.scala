package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.catalog.CatalogPower
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
  case object Mob extends RuleSourceFace
  case object GrandCouncil extends RuleSourceFace
  case object WanderingFlame extends RuleSourceFace
  case object Festival extends RuleSourceFace
  case object Normal extends RuleSourceFace
  case object Altered extends RuleSourceFace
}

/** Mutable facts carried by sources that are not catalog cards. */
sealed trait RuleSourceState extends Product with Serializable
object RuleSourceState {
  case object Stateless extends RuleSourceState
  final case class Banner(holder: Option[PlayerId], resources: Int)
      extends RuleSourceState
  final case class Foundation(alterationSources: Vector[LegacyId])
      extends RuleSourceState
}

final case class IndexedRuleSource(
    source: RuleSourceRef,
    powerIds: Vector[PowerId],
    face: RuleSourceFace,
    state: RuleSourceState = RuleSourceState.Stateless
) {
  /** Temporary compatibility projection for callers not yet migrated to the
    * window resolver. Factual source identity is typed at this boundary.
    */
  def handlerIds: Vector[String] = powerIds.map(_.value)
}

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
        IndexedRuleSource(RuleSourceRef.Site(siteId), ids(definition.handlers),
          RuleSourceFace.Printed))
      val cards = current.map.sites(siteId).denizens.flatMap {
        case denizen: DenizenState =>
          catalog.denizens.find(_.id.value == denizen.id.value).toVector.map(
            definition => IndexedRuleSource(
              RuleSourceRef.SiteCard(siteId, denizen.id), ids(definition.powers),
              orientation(denizen.orientation)))
        case edifice: EdificeState =>
          catalog.edifices.find(_.id.value == edifice.id.value).toVector.map {
            definition =>
              val face = if (edifice.side == EdificeSide.Intact)
                definition.intact else definition.ruined
              IndexedRuleSource(RuleSourceRef.Edifice(siteId, edifice.id),
                ids(face.powers), if (edifice.side == EdificeSide.Intact)
                  RuleSourceFace.Intact else RuleSourceFace.Ruined)
          }
      }
      val relics = current.map.sites(siteId).relics.flatMap { relic =>
        catalog.relics.find(_.id.value == relic.id.value).toVector.map(
          definition => IndexedRuleSource(
            RuleSourceRef.SiteRelic(siteId, relic.id), ids(definition.powers),
            orientation(relic.orientation)))
      }
      printed ++ cards ++ relics
    }
    val players = current.players.flatMap { player =>
      val advisers = player.advisers.collect { case denizen: DenizenState =>
        catalog.denizens.find(_.id.value == denizen.id.value).toVector.map(
          definition => IndexedRuleSource(
            RuleSourceRef.Adviser(player.player, denizen.id), ids(definition.powers),
            orientation(denizen.orientation)))
      }.flatten
      val relics = player.relics.flatMap { relic =>
        catalog.relics.find(_.id.value == relic.id.value).toVector.map(
          definition => IndexedRuleSource(
            RuleSourceRef.Relic(player.player, relic.id), ids(definition.powers),
            orientation(relic.orientation)))
      }
      advisers ++ relics
    }
    val legacies = ready.game.campaign.lineages.toVector.sortBy(_._1.value)
      .flatMap { case (lineageId, lineage) =>
        lineage.legacies.flatMap { legacy =>
          catalog.legacies.find(_.id.value == legacy.id.value).toVector.map(
            definition => IndexedRuleSource(
              RuleSourceRef.Legacy(lineageId, legacy.id), ids(definition.powers),
              if (legacy.active) RuleSourceFace.Active else RuleSourceFace.Inactive))
        }
      }
    val banners = Vector(
      IndexedRuleSource(
        RuleSourceRef.Banner(Banner.PeoplesFavor.key),
        current.banners.peoplesFavor.active match {
          case PeoplesFavorFace.Mob => Vector.empty
          case PeoplesFavorFace.GrandCouncil =>
            ids(Vector("banner.peoples-favor.grand-council"))
        },
        current.banners.peoplesFavor.active match {
          case PeoplesFavorFace.Mob => RuleSourceFace.Mob
          case PeoplesFavorFace.GrandCouncil => RuleSourceFace.GrandCouncil
        },
        RuleSourceState.Banner(current.banners.peoplesFavor.holder,
          current.banners.peoplesFavor.favor)),
      IndexedRuleSource(
        RuleSourceRef.Banner(Banner.DarkestSecret.key),
        current.banners.darkestSecret.active match {
          case DarkestSecretFace.WanderingFlame => Vector.empty
          case DarkestSecretFace.Festival =>
            ids(Vector("banner.darkest-secret.festival"))
        },
        current.banners.darkestSecret.active match {
          case DarkestSecretFace.WanderingFlame => RuleSourceFace.WanderingFlame
          case DarkestSecretFace.Festival => RuleSourceFace.Festival
        },
        RuleSourceState.Banner(current.banners.darkestSecret.holder,
          current.banners.darkestSecret.secrets)))
    val foundations = FoundationNumber.all.flatMap { number =>
      ready.game.campaign.foundations.get(number).map { foundation =>
        IndexedRuleSource(
          RuleSourceRef.Foundation(number),
          foundation.face match {
            case FoundationFace.Normal => Vector.empty
            case FoundationFace.Altered => ids(Vector("foundation.altered"))
          },
          foundation.face match {
            case FoundationFace.Normal => RuleSourceFace.Normal
            case FoundationFace.Altered => RuleSourceFace.Altered
          },
          RuleSourceState.Foundation(
            foundation.alterationSources.toVector.sortBy(_.value)))
      }
    }
    sites ++ players ++ banners ++ foundations ++ legacies
  }

  private def orientation(value: Orientation): RuleSourceFace = value match {
    case Orientation.FaceUp => RuleSourceFace.FaceUp
    case Orientation.FaceDown => RuleSourceFace.FaceDown
  }

  private def ids(values: Vector[String]): Vector[PowerId] = values.map(PowerId)
  private def ids(values: Vector[CatalogPower]): Vector[PowerId] =
    values.map(_.id)
}
