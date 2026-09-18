package oathdigital.gameplay.oathkeeper

import oathdigital.model._

sealed trait OathkeeperOutcome extends Product with Serializable
object OathkeeperOutcome {
  case object NoChange extends OathkeeperOutcome
  final case class Transfer(holder: Option[PlayerId]) extends OathkeeperOutcome
  /** `candidates` are the tied leaders in seat order; always two or more. */
  final case class Choose(holder: PlayerId, candidates: Vector[PlayerId])
      extends OathkeeperOutcome
}

/** The only definition of what the Oathkeeper title does at an action
  * boundary. The boundary asks it whether anything happens, and
  * `OathkeeperProcedure` asks it what, so the two cannot disagree.
  */
object OathkeeperRules {
  import OathkeeperOutcome._

  def outcome(ready: ReadyGame): OathkeeperOutcome = {
    val current = ready.game.current
    val leaders = qualifyingPlayers(ready.game.campaign.oathkeeperGoal, current)
    current.title.holder match {
      case Some(holder) if leaders(holder) => NoChange
      case Some(holder) if leaders.size > 1 =>
        Choose(holder, current.players.map(_.player).filter(leaders))
      case _ if leaders.size == 1 => Transfer(leaders.headOption)
      case Some(_) => Transfer(None)
      case None => NoChange
    }
  }

  private def qualifyingPlayers(goal: OathkeeperGoal,
      current: CurrentGameState): Set[PlayerId] =
    goal match {
      case OathkeeperGoal.Supremacy =>
        leadersWithPositiveCount(current.players.map { player =>
          player.player -> current.map.sites.values.count(_.forces match {
            case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
              lineage == player.lineage
            case _ => false
          })
        })
      case OathkeeperGoal.Protection =>
        leadersWithPositiveCount(current.players.map(player =>
          player.player -> player.relics.size))
      case OathkeeperGoal.ThePeople =>
        current.banners.peoplesFavor.holder.toSet
      case OathkeeperGoal.Devotion =>
        current.banners.darkestSecret.holder.toSet
    }

  private def leadersWithPositiveCount(
      counts: Vector[(PlayerId, Int)]): Set[PlayerId] = {
    val maximum = counts.map(_._2).maxOption.getOrElse(0)
    counts.collect { case (player, count) if maximum > 0 && count == maximum =>
      player
    }.toSet
  }
}
