package oathdigital.model

import oathdigital.model._

sealed trait OathEvent extends Product with Serializable

/** Walker (procedure-walker) event family root (Task 3).
  *
  * Open, not `sealed`: the concrete per-node cases live in
  * `gameplay/walker/WalkerEvents.scala` and later tasks extend per-node
  * payloads from other files, which a sealed root (same-file subclasses only)
  * would forbid — mirroring the `Operation` root decision in Task 1.
  */
trait WalkerEvent extends OathEvent
object OathEvent {
  final case class IgnoredRulesRecorded(
      playerId: PlayerId,
      action: ActionKind,
      diagnostics: Vector[IgnoredRuleDiagnostic]
  ) extends OathEvent {
    require(diagnostics.nonEmpty, "ignored-rule event must not be empty")
  }
  final case class FirstGameStarted(plan: FirstGameSetupPlan)
      extends OathEvent
  final case class GamePawnPlaced(playerId: PlayerId, siteId: SiteId)
      extends OathEvent
  final case class StartingAdviserChosen(
      playerId: PlayerId,
      adviserId: DenizenId
  ) extends OathEvent
  case object FirstGameCompleted extends OathEvent
  final case class SiteRelicsPeeked(
      playerId: PlayerId, siteId: SiteId, relics: Vector[RelicId])
      extends OathEvent
  final case class OwnedRelicRevealed(playerId: PlayerId, relicId: RelicId)
      extends OathEvent
  final case class WarbandsMoved(
      playerId: PlayerId, siteId: SiteId, toSite: Boolean, amount: Int,
      priorBoardWarbands: Int, priorSiteWarbands: Int) extends OathEvent
  final case class BanditsRefilled(sites: Vector[(SiteId, Int)]) extends OathEvent
  final case class RoundEnded(completedRound: Int, nextRound: Option[Int])
      extends OathEvent
  final case class WarExhaustionResolved(
      winner: PlayerId,
      kind: VictoryKind,
      visionId: Option[VisionId],
      randomCandidates: Vector[PlayerId]
  ) extends OathEvent
  final case class UsurperFlipped(playerId: PlayerId) extends OathEvent
  final case class UsurperVictory(playerId: PlayerId) extends OathEvent
  final case class VisionVictory(playerId: PlayerId, visionId: VisionId)
      extends OathEvent
}

sealed trait TradeResource extends Product with Serializable
object TradeResource {
  case object Favor extends TradeResource
  case object Secret extends TradeResource
}

/** `key` is the resource's spelling in a Take Wealth start selection, which
  * is the only place the choice crosses a wire. It lives on the case so the
  * procedure that reads a selection and the one that builds one cannot spell
  * it differently -- see `TakeWealthProcedure.selection`/`resourceOf`.
  */
sealed trait WakeResource extends Product with Serializable { def key: String }
object WakeResource {
  case object Favor extends WakeResource { val key = "favor" }
  case object Secret extends WakeResource { val key = "secret" }

  val all: Vector[WakeResource] = Vector(Favor, Secret)

  def fromKey(key: String): Option[WakeResource] = all.find(_.key == key)
}
