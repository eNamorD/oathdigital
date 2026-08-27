package oathdigital.gameplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.model._

/** CR pp. 16-17, 19: the one authoritative true-Vision qualification rule. */
object VisionVictoryEligibility {
  def qualifies(playerId: PlayerId, current: CurrentGameState): Boolean =
    current.tracks.visionsDrawn >= 3 && current.players.find(
      _.player == playerId).flatMap(_.revealedVision).exists { revealed =>
      VisionRules.trueGoal(revealed.id).exists {
        case OathkeeperGoal.Supremacy => uniquePositiveLeader(playerId,
          current.players.map(player => player.player ->
            current.map.sites.values.count(_.forces match {
              case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
                lineage == player.lineage
              case _ => false
            })))
        case OathkeeperGoal.Protection => uniquePositiveLeader(playerId,
          current.players.map(player => player.player -> player.relics.size))
        case OathkeeperGoal.ThePeople =>
          current.banners.peoplesFavor.holder.contains(playerId)
        case OathkeeperGoal.Devotion =>
          current.banners.darkestSecret.holder.contains(playerId)
      }
    }

  private def uniquePositiveLeader(playerId: PlayerId,
      counts: Vector[(PlayerId, Int)]): Boolean = {
    val maximum = counts.map(_._2).maxOption.getOrElse(0)
    maximum > 0 && counts.count(_._2 == maximum) == 1 &&
      counts.contains(playerId -> maximum)
  }
}
