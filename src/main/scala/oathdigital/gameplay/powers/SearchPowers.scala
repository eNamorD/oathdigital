package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.{IgnoredRuleDiagnostic, MajorActionKind, OathEvent,
  OathTransition, OathViolation, PowerRuntime, ReadyGame}
import oathdigital.gameplay.powerresolver.Power
import oathdigital.model.{PlayerId, SearchPlacement, WorldCardId}

object SearchPowers {
  val powers: Vector[Power] = Vector.empty

  /** Search-derived facedown-adviser play shares Search modifier discovery.
    * No current reviewed Search modifier is executable, but this exact-window
    * query preserves authoritative audit and future option revalidation.
    */
  def validateModifierSelection(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Either[OathViolation, Unit] =
    PowerRuntime.options(catalog, ready, player, MajorActionKind.Search).map(_ => ())

  /** Shared Search placement hook. Current reviewed When Played mechanics stay
    * fallback-only, so reached source handlers become durable diagnostics.
    */
  def recordPlayHooks(catalog: ExecutableCatalog, transition: OathTransition,
      ready: ReadyGame, player: PlayerId, card: WorldCardId,
      placement: SearchPlacement, prepend: Boolean)
      : Either[OathViolation, OathTransition] =
    CardPlay.playedSource(ready, player, card, placement)
      .fold[Either[OathViolation, OathTransition]](Right(transition)) { source =>
        PowerRuntime.ignoredAtSource(catalog, ready, player,
          MajorActionKind.WhenPlayed, source).map { diagnostics =>
          appendDiagnostics(transition, player, diagnostics, prepend)
        }
      }

  private def appendDiagnostics(transition: OathTransition, player: PlayerId,
      diagnostics: Vector[IgnoredRuleDiagnostic], prepend: Boolean) =
    if (diagnostics.isEmpty) transition else {
      val event = OathEvent.IgnoredRulesRecorded(player,
        MajorActionKind.WhenPlayed, diagnostics)
      transition.copy(events = if (prepend) event +: transition.events
        else transition.events :+ event)
    }
}
