package oathdigital.model

/** The kind of warband a player's own warbands are: the shared Imperial kind
  * for a Citizen or Chancellor, the lineage's own Exile kind otherwise. The
  * one definition that operation validation and the Muster tree both read.
  */
object PlayerForceKind {
  def of(ready: ReadyGame, player: PlayerState): Option[ForceKind] =
    ready.game.campaign.lineages.get(player.lineage).map { lineage =>
      if (lineage.role.isImperial) ForceKind.Imperial
      else ForceKind.Exile(player.lineage)
    }
}
