package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.ReadyGame
import oathdigital.model._

/** Catalog-backed site-card discard restrictions for the acting player. */
final class DiscardRestrictions(catalog: ExecutableCatalog,
    actor: PlayerId) extends OperationRestriction {
  private val hallPower = PowerId("edifice.e16.intact")

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] = {
    val source = operation match {
      case value: Discard.Denizen => Some(value.from.location)
      case value: Discard.Vision => Some(value.from.location)
      case value: Discard.RuinedEdifice => Some(value.from.location)
      case value: Discard.Relic => Some(value.from.location)
      case _ => None
    }
    source.collect { case Location.Site(site) => site }.flatMap { site =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if (lineage.role.isImperial) SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      val targetRuler = current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, current.players).toOption)
      for {
        sourceSide <- actorSide
        ruler <- targetRuler
        if SiteRule.enemies(sourceSide, ruler)
        if current.map.sites.exists { case (_, state) =>
          SiteRule.ruler(state.forces, current.players).toOption.contains(ruler) &&
            state.denizens.exists {
              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifices.find(_.id.value == edifice.id.value)
                  .exists(_.intact.powers.exists(_.id == hallPower))
              case _ => false
            }
        }
      } yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible)
    }
  }
}
