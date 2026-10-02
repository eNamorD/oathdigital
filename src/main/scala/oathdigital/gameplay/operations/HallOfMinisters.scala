package oathdigital.gameplay.operations

import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
import oathdigital.model._

object HallOfMinistersCard extends Edifice(EdificeId("E16"), Suit.Order):
  object intact extends EdificeFace("Hall of Ministers") with Locked:
    val power = PrintedPower(PowerId("edifice.e16.intact"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** discard cards from " +
        "sites ruled by this card's ruler. _They can still be " +
        "buried._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Hall of Bandits"):
    val power = PrintedPower(PowerId("edifice.e16.ruined"),
      persistent = true, cost = Cost.free,
      text = "**CHRONICLE:** Ruined edifices ruled by bandits are not " +
        "discarded.")
    val powers: Vector[PrintedPower] = Vector(power)

/** The Hall of Ministers (edifice E16, intact): an enemy of the Hall's ruler
  * acts as if the denizens and relics at the ruler's sites were locked, so it
  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  *
  * There is one Hall of Ministers, so the restriction names its card and
  * holds whichever edifices a catalog lists.
  */
case object HallOfMinisters extends OperationRestriction:
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
                edifice.id == HallOfMinistersCard.id
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
