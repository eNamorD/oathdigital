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
  final case class UnknownPlayerLineage(
      player: PlayerId,
      lineage: LineageId
  ) extends DomainProblem
  final case class LineageControllerMismatch(
      player: PlayerId,
      lineage: LineageId,
      controller: Option[PlayerId]
  ) extends DomainProblem
  final case class PawnOutsideMap(player: PlayerId, site: SiteId)
      extends DomainProblem
  final case class UnknownActivePlayer(player: PlayerId) extends DomainProblem
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

    map.inPlay.groupBy(identity).foreach {
      case (site, occurrences) if occurrences.size > 1 =>
        problems += DuplicateMapSite(site)
      case _ => ()
    }

    val inPlay = map.inPlay.toSet
    (inPlay -- map.sites.keySet).toVector.sortBy(_.value).foreach { site =>
      problems += MissingSiteState(site)
    }
    (map.sites.keySet -- inPlay).toVector.sortBy(_.value).foreach { site =>
      problems += ExtraSiteState(site)
    }

    game.current.players.groupBy(_.player).foreach {
      case (player, occurrences) if occurrences.size > 1 =>
        problems += DuplicatePlayer(player)
      case _ => ()
    }
    game.current.players.groupBy(_.lineage).foreach {
      case (lineage, occurrences) if occurrences.size > 1 =>
        problems += DuplicateLineage(lineage)
      case _ => ()
    }

    game.current.players.foreach { player =>
      game.campaign.lineages.get(player.lineage) match {
        case None =>
          problems += UnknownPlayerLineage(player.player, player.lineage)
        case Some(lineage) if lineage.controller != Some(player.player) =>
          problems += LineageControllerMismatch(
            player.player,
            player.lineage,
            lineage.controller
          )
        case Some(_) => ()
      }
      if (!inPlay.contains(player.pawnSite))
        problems += PawnOutsideMap(player.player, player.pawnSite)
    }

    val playerIds = game.current.players.iterator.map(_.player).toSet
    if (!playerIds.contains(game.current.turn.activePlayer))
      problems += UnknownActivePlayer(game.current.turn.activePlayer)

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
