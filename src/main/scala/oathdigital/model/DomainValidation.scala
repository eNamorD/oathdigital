package oathdigital.model

sealed trait DomainProblem extends Product with Serializable
object DomainProblem {
  final case class WrongRegionSize(
      region: Region,
      expected: Int,
      actual: Int
  ) extends DomainProblem

  final case class DuplicateMapSite(site: SiteId) extends DomainProblem
  final case class MissingSiteState(site: SiteId) extends DomainProblem
  final case class ExtraSiteState(site: SiteId) extends DomainProblem
  final case class DuplicatePlayer(player: PlayerId) extends DomainProblem
  final case class DuplicateLineage(lineage: LineageId) extends DomainProblem
  final case class LineageKeyMismatch(key: LineageId, stateId: LineageId)
      extends DomainProblem
  final case class MissingFoundation(number: FoundationNumber)
      extends DomainProblem
  final case class DuplicateAtlasSite(site: SiteId) extends DomainProblem
  final case class SiteInMapAndAtlas(site: SiteId) extends DomainProblem
  final case class UnknownPlayerLineage(
      player: PlayerId,
      lineage: LineageId
  ) extends DomainProblem
  final case class PawnOutsideMap(player: PlayerId, site: SiteId)
      extends DomainProblem
  final case class UnknownForceLineage(site: SiteId, lineage: LineageId)
      extends DomainProblem
  final case class UnknownActivePlayer(player: PlayerId) extends DomainProblem
  final case class UnknownPeoplesFavorHolder(player: PlayerId)
      extends DomainProblem
  final case class UnknownDarkestSecretHolder(player: PlayerId)
      extends DomainProblem
  final case class UnknownTitleHolder(player: PlayerId) extends DomainProblem
  final case class MultipleChancellors(lineages: Vector[LineageId])
      extends DomainProblem
  final case class CardProblem(problem: CardIndexProblem) extends DomainProblem
}

object DomainValidation {
  import DomainProblem._

  def validate(
      game: OathGame,
      expectedCards: Set[CardId] = Set.empty
  ): Vector[DomainProblem] = {
    val problems = Vector.newBuilder[DomainProblem]
    val map = game.current.map

    Vector(
      (Region.Cradle: Region, 2, map.cradle.size),
      (Region.Provinces: Region, 3, map.provinces.size),
      (Region.Hinterland: Region, 3, map.hinterland.size)
    ).foreach { case (region, expected, actual) =>
      if (actual != expected)
        problems += WrongRegionSize(region, expected, actual)
    }

    map.inPlay
      .groupBy(identity)
      .toVector
      .collect { case (site, occurrences) if occurrences.size > 1 => site }
      .sortBy(_.value)
      .foreach(site => problems += DuplicateMapSite(site))

    val inPlay = map.inPlay.toSet
    (inPlay -- map.sites.keySet).toVector.sortBy(_.value).foreach { site =>
      problems += MissingSiteState(site)
    }
    (map.sites.keySet -- inPlay).toVector.sortBy(_.value).foreach { site =>
      problems += ExtraSiteState(site)
    }

    val atlasSites = game.campaign.atlas.entries.collect {
      case stored: AtlasEntry.StoredSite => stored.id
    }
    atlasSites
      .groupBy(identity)
      .toVector
      .collect { case (site, occurrences) if occurrences.size > 1 => site }
      .sortBy(_.value)
      .foreach(site => problems += DuplicateAtlasSite(site))
    (inPlay intersect atlasSites.toSet).toVector.sortBy(_.value).foreach {
      site =>
        problems += SiteInMapAndAtlas(site)
    }

    FoundationNumber.all.foreach { number =>
      if (!game.campaign.foundations.contains(number))
        problems += MissingFoundation(number)
    }

    game.campaign.lineages.toVector
      .sortBy(_._1.value)
      .foreach { case (key, lineage) =>
        if (key != lineage.id)
          problems += LineageKeyMismatch(key, lineage.id)
      }

    game.current.players
      .groupBy(_.player)
      .toVector
      .collect { case (player, occurrences) if occurrences.size > 1 => player }
      .sortBy(_.value)
      .foreach(player => problems += DuplicatePlayer(player))
    game.current.players
      .groupBy(_.lineage)
      .toVector
      .collect {
        case (lineage, occurrences) if occurrences.size > 1 => lineage
      }
      .sortBy(_.value)
      .foreach(lineage => problems += DuplicateLineage(lineage))

    game.current.players.foreach { player =>
      if (!game.campaign.lineages.contains(player.lineage))
        problems += UnknownPlayerLineage(player.player, player.lineage)
      player.pawnSite.foreach { site =>
        if (!inPlay.contains(site))
          problems += PawnOutsideMap(player.player, site)
      }
    }

    val playerIds = game.current.players.iterator.map(_.player).toSet
    if (!playerIds.contains(game.current.turn.activePlayer))
      problems += UnknownActivePlayer(game.current.turn.activePlayer)

    map.sites.toVector.sortBy(_._1.value).foreach {
      case (site, SiteState(SiteForces.Occupied(ForceKind.Exile(lineage), _), _, _, _))
          if !game.campaign.lineages.contains(lineage) =>
        problems += UnknownForceLineage(site, lineage)
      case _ => ()
    }

    game.current.banners.peoplesFavor.holder.foreach { holder =>
      if (!playerIds.contains(holder))
        problems += UnknownPeoplesFavorHolder(holder)
    }
    game.current.banners.darkestSecret.holder.foreach { holder =>
      if (!playerIds.contains(holder))
        problems += UnknownDarkestSecretHolder(holder)
    }
    game.current.title.holder.foreach { holder =>
      if (!playerIds.contains(holder))
        problems += UnknownTitleHolder(holder)
    }

    val chancellors = game.campaign.lineages.valuesIterator
      .filter(_.role == Role.Chancellor)
      .map(_.id)
      .toVector
      .sortBy(_.value)
    if (chancellors.size > 1)
      problems += MultipleChancellors(chancellors)

    CardIndex.from(game, expectedCards) match {
      case Left(cardProblems) =>
        cardProblems.foreach(problem => problems += CardProblem(problem))
      case Right(_) => ()
    }

    problems.result()
  }
}
