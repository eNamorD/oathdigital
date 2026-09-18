package oathdigital.gameplay.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Pure CR pp. 6-7 table setup shared by setup projection and completion. */
final case class FirstGameSetupMaterial(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    tracks: GameTracks,
    favorBanks: Map[Suit, Int]
)

final class FirstGameSetupMaterializer(catalog: ExecutableCatalog) {
  private val sitesById = catalog.sites.map(site => site.id -> site).toMap
  private val edificesById = catalog.edifices.map(e => EdificeId(e.id.value) -> e).toMap

  def materialize(plan: FirstGameSetupPlan,
      placements: Vector[PawnPlacement],
      adviserChoices: Vector[(PlayerId, DenizenId)]): FirstGameSetupMaterial = {
    val placementMap = placements.map(p => p.playerId -> p.siteId).toMap
    val adviserMap = adviserChoices.toMap
    val relicsBySite = assignRelics(plan)
    val edifices = plan.homelandEdifices.toMap
    val map = MapState(
      plan.orderedSites.take(2),
      plan.orderedSites.slice(2, 5),
      plan.orderedSites.slice(5, 8),
      plan.orderedSites.map { siteId =>
        val definition = sitesById(siteId)
        val forces = if (definition.capacity == 0) SiteForces.Empty
          else SiteForces.Occupied(ForceKind.Bandit, definition.capacity)
        val denizens = edifices.get(siteId).toVector.map(id =>
          EdificeState(id, EdificeSide.Ruined, Tokens.empty))
        val relics = relicsBySite.getOrElse(siteId, Vector.empty).map(id =>
          RelicState(id, Orientation.FaceDown, Tokens.empty))
        siteId -> SiteState(forces, denizens, relics, definition.startingResources)
      }.toMap)
    val usedRelics = relicsBySite.valuesIterator.flatten.toSet
    val players = plan.participants.map { participant =>
      PlayerState(participant.playerId, participant.lineageId,
        placementMap.get(participant.playerId),
        PlayerBoardState(1, 1, 0, 3, SupplyTrack.full),
        adviserMap.get(participant.playerId).toVector.map(id =>
          DenizenState(id, Orientation.FaceDown, Tokens.empty)),
        Vector.empty, None)
    }
    FirstGameSetupMaterial(players, map,
      CardZones(plan.worldDeckOrder, plan.relicOrder.filterNot(usedRelics),
        catalog.edifices.map(e => EdificeId(e.id.value))
          .filterNot(edifices.values.toSet), Vector.empty,
        initialDiscards(plan, placementMap, adviserMap)),
      BannersState(
        PeoplesFavorState(PeoplesFavorFace.Mob, None, 1),
        DarkestSecretState(DarkestSecretFace.WanderingFlame, None, 1)),
      GameTracks(1, 0, usurperLimited = true), favorBanks(plan))
  }

  def handFor(plan: FirstGameSetupPlan, playerId: PlayerId): Vector[DenizenId] = {
    val index = plan.participants.indexWhere(_.playerId == playerId)
    plan.denizenOrder.slice(6 + index * 3, 9 + index * 3)
  }

  private def assignRelics(plan: FirstGameSetupPlan): Map[SiteId, Vector[RelicId]] = {
    var offset = 0
    plan.orderedSites.map { site =>
      val count = sitesById(site).relicSlots
      val assigned = plan.relicOrder.slice(offset, offset + count)
      offset += count
      site -> assigned
    }.toMap
  }

  private def initialDiscards(plan: FirstGameSetupPlan,
      placements: Map[PlayerId, SiteId],
      choices: Map[PlayerId, DenizenId]): Map[Region, Vector[WorldCardId]] = {
    val seeded = Map[Region, Vector[WorldCardId]](
      Region.Cradle -> plan.denizenOrder.slice(0, 2),
      Region.Provinces -> plan.denizenOrder.slice(2, 4),
      Region.Hinterland -> plan.denizenOrder.slice(4, 6))
    plan.participants.foldLeft(seeded) { (discards, participant) =>
      (placements.get(participant.playerId), choices.get(participant.playerId)) match {
        case (Some(site), Some(choice)) =>
          val rejected = handFor(plan, participant.playerId).filterNot(_ == choice)
          val destination = regionOf(plan, site) match {
            case Region.Cradle => Region.Provinces
            case Region.Provinces => Region.Hinterland
            case Region.Hinterland => Region.Cradle
          }
          discards.updated(destination, discards(destination) ++ rejected)
        case _ => discards
      }
    }
  }

  private def regionOf(plan: FirstGameSetupPlan, site: SiteId): Region =
    if (plan.orderedSites.take(2).contains(site)) Region.Cradle
    else if (plan.orderedSites.slice(2, 5).contains(site)) Region.Provinces
    else Region.Hinterland

  private def favorBanks(plan: FirstGameSetupPlan): Map[Suit, Int] = {
    val bonus = if (plan.participants.size >= 5) 1 else 0
    val edificeSuits = plan.homelandEdifices.map { case (_, id) =>
      edificesById(id).suit
    }
    Suit.all.map(suit => suit -> (3 + bonus + edificeSuits.count(_ == suit))).toMap
  }
}
