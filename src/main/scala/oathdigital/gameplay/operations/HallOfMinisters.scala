package oathdigital.gameplay.operations

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The Hall of Ministers (edifice E16, intact): an enemy of the Hall's ruler
  * acts as if the denizens and relics at the ruler's sites were locked, so it
  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  */
final case class HallOfMinisters(catalog: ExecutableCatalog)
    extends OperationRestriction:
  private val hallPower = PowerId("edifice.e16.intact")

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    discardAt(ready, operation).flatMap { case (site, actor) =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if lineage.role.isImperial then SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      val targetRuler = current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, current.players).toOption)
      for
        sourceSide <- actorSide
        ruler <- targetRuler
        if SiteRule.enemies(sourceSide, ruler)
        if current.map.sites.exists { case (_, state) =>
          SiteRule.ruler(state.forces, current.players).toOption.contains(ruler) &&
            state.denizens.exists:
              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifice(edifice.id)
                  .exists(_.intact.powers.exists(_.id == hallPower))
              case _ => false
        }
      yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible)
    }

  /** The site a discard takes a card from, and who discards it. */
  private def discardAt(ready: ReadyGame, operation: CoreOperation)
      : Option[(SiteId, PlayerId)] =
    val active = ready.game.current.turn.activePlayer
    val source = operation match
      case value: Discard.Denizen => Some(value.from.location -> value.actingPlayer)
      case value: Discard.Vision => Some(value.from.location -> active)
      case value: Discard.RuinedEdifice =>
        Some(value.from.location -> value.actingPlayer)
      case value: Discard.Relic => Some(value.from.location -> value.actingPlayer)
      case _ => None
    source.collect { case (Location.Site(site), actor) => site -> actor }
