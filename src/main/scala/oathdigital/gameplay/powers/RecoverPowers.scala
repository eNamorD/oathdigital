package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerId

object RecoverPowers {
  val Catacombs: PowerId = PowerId("denizen.catacombs")
  private val ids = Set("denizen.relic-worship", "edifice.e13.ruined",
    "edifice.e17.intact", "edifice.e17.ruined")

  private object CatacombsInspector extends PowerInspector {
    def inspect(context: PowerContext): PowerInspection = context.facts match {
      case facts: ReviewedPowerFacts =>
        val applicable = context.source match {
          case RuleSourceRef.SiteCard(siteId, _) =>
            val player = facts.ready.game.current.players.find(_.player == facts.actor)
            val site = facts.ready.game.current.map.sites.get(siteId)
            val definition = facts.catalog.sites.find(_.id == siteId)
            ReviewedPowerInspector.inspect(context).applicable &&
              facts.ready.game.campaign.lineages.values.forall(
                _.role == oathdigital.model.Role.Exile) &&
              facts.ready.game.campaign.foundations.values.forall(f =>
                f.face == oathdigital.model.FoundationFace.Normal &&
                  f.alterationSources.isEmpty) &&
              player.exists(p => p.board.faceUpSecrets > 0 &&
                p.board.supply.supply > 0 && p.pawnSite.contains(siteId)) &&
              site.exists(value => definition.exists(d =>
                value.relics.size < d.relicSlots && d.recoverDifficulty.nonEmpty)) &&
              facts.ready.game.current.commonCards.relicDeck.nonEmpty
          case _ => false
        }
        PowerInspection(applicable, Some(facts.actor), Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }
  }
  private object CatacombsHandler extends PowerHandler

  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, Some(MajorActionType.Recover),
      Vector(PowerWindow.RecoverBeforeFirstRoll))) :+ RegisteredPower(
    PowerDefinition(Catacombs, Some(MajorActionType.Recover), Vector(
      PowerWindow.RecoverEligibility, PowerWindow.RecoverModifierSelection,
      PowerWindow.RecoverBeforeFirstRoll), PowerResolution.PlayerSelected),
    CatacombsInspector, Some(CatacombsHandler))
}
