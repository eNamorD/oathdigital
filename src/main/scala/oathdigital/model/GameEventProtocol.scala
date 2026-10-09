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

/** The consent facts (consent design, "Events"). None opens a command
  * batch: the journal fold closes any open batch before one, so consent
  * stays off the undo stack. */
sealed trait ConsentEvent extends OathEvent
object OathEvent:
  final case class IgnoredRulesRecorded(
      playerId: PlayerId,
      action: ActionKind,
      diagnostics: Vector[IgnoredRuleDiagnostic]
  ) extends OathEvent:
    require(diagnostics.nonEmpty, "ignored-rule event must not be empty")
  /** Replaces `FirstGameStarted` (2026-09-21 Chronicle design, slice 2,
    * "Setup from a Chronicle"). `chronicle` is the between-game record;
    * `orders` is the concrete per-game deal Setup actually deals from --
    * see the Global Constraints note on why the two are recorded
    * separately. Evolving this event alone (before the triggered `Setup`
    * procedure runs a single step) produces a `Ready` game in `Phase.Setup`
    * with every pawn unplaced and every hand unresolved.
    */
  final case class GameStarted(chronicle: Chronicle, orders: SetupOrders)
      extends OathEvent
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
  /** Opens every command batch but Begin's (undo design, "Storage"): `by` is
    * the command's actor. The application layer prepends it; the rules
    * engine never builds it, and evolving it changes nothing. */
  final case class CommandAccepted(by: PlayerId) extends OathEvent
  /** Voids the events in `[from, until)`, the latest live command batch,
    * which `by` issued (undo design, "Storage"). Only the application's
    * journal fold replays it. */
  final case class StepTakenBack(from: Long, until: Long, by: PlayerId)
      extends OathEvent
  /** Un-voids the events in `[from, until)`, the batch most recently taken
    * back since the last command batch, which `by` issued (undo design,
    * "Redo"). Only the application's journal fold replays it. */
  final case class StepRestored(from: Long, until: Long, by: PlayerId)
      extends OathEvent
  /** `request.requester` asks the granters in `request.asks`; every ask is
    * unanswered. */
  final case class ConsentRequested(request: ConsentRequest)
      extends ConsentEvent
  /** `granter`'s answer to the waiting request. A refusal, or the last
    * grant, settles it. */
  final case class ConsentAnswered(granter: PlayerId, granted: Boolean)
      extends ConsentEvent
  /** The requester took their unanswered request back with Undo. */
  final case class ConsentRequestWithdrawn(requester: PlayerId)
      extends ConsentEvent
  final case class ConsentPreferenceSet(granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting) extends ConsentEvent
  /** `granter` no longer rules `power`'s card: their preferences on it go
    * (ruling 17). */
  final case class ConsentPreferencesCleared(granter: PlayerId,
      power: PowerId) extends ConsentEvent

enum TradeResource { case Favor, Secret }

/** `key` is the resource's spelling in a Take Wealth start selection, which
  * is the only place the choice crosses a wire. It lives on the case so the
  * procedure that reads a selection and the one that builds one cannot spell
  * it differently -- see `TakeWealthProcedure.selection`/`resourceOf`.
  */
enum WakeResource(val key: String):
  case Favor extends WakeResource("favor")
  case Secret extends WakeResource("secret")
object WakeResource:
  val all: Vector[WakeResource] = Vector(Favor, Secret)

  def fromKey(key: String): Option[WakeResource] = all.find(_.key == key)
