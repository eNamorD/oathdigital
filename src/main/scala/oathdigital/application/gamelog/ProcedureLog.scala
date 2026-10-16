package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.LogSpan

/** Whether the tag posts at this event: the anchor each procedure declares
  * (log action sections design, "The tag entry"). */
private[gamelog] type Anchor = (LogJournal, Run, Int) => Boolean

/** What the line after the tag says beyond the modifiers. */
private[gamelog] type Opening =
  (LogJournal, Run, Int, Option[PlayerId]) => Vector[LogSpan]

/** The repeated line a procedure posts when its player pays again. */
private[gamelog] type Continued =
  (LogJournal, Run, Int, Option[PlayerId]) => Option[Posted]

/** The action line a procedure posts, at the event its facts complete. */
private[gamelog] type ActionLine =
  (LogJournal, Run, Int, Option[PlayerId]) => Vector[Posted]

private[gamelog] object Anchor:
  /** The run's first event, where a procedure declares no later anchor. */
  val AtFirstEvent: Anchor = (_, run, at) => at == run.first

private[gamelog] object Opening:
  val none: Opening = (_, _, _, _) => Vector.empty

private[gamelog] object Continued:
  val none: Continued = (_, _, _, _) => None

private[gamelog] object ActionLine:
  val none: ActionLine = (_, _, _, _) => Vector.empty

/** What one procedure declares to the game log: its tag and where the tag
  * posts, what its opening line says, the line it repeats when the player
  * pays again, and whether its run draws a section band. Apart from the tag,
  * which is the name the log prints, every member is a declaration rather
  * than a wording: the rules themselves stay in `StartLines`, `ActionLines`
  * and their helpers, and an entry names them.
  */
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    lines: ActionLine,
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    isSection: Boolean = true)

/** Every procedure's log lines, in one table.
  *
  * `rules` is an exhaustive match over the sealed `ProcedureRef` hierarchy,
  * with every finite member named and no family wildcard, so a new procedure
  * fails compilation here. `entries` is built from that match over
  * `ProcedureRef.all`, so the table is total by construction and no lookup
  * can miss — unlike `WalkerProcedureRegistry`, whose literal map is guarded
  * by a test and answers a miss at runtime.
  *
  * `ActionRef.UsePower` is parameterized, so `ProcedureRef.all` does not list
  * it and `of` builds its entry from the same match on demand.
  */
private[gamelog] final class ProcedureLogs(starts: StartLines,
    actions: ActionLines):
  private val entries: Map[ProcedureRef, ProcedureLog] =
    ProcedureRef.all.map(procedure => procedure -> rules(procedure)).toMap

  def of(procedure: ProcedureRef): ProcedureLog =
    entries.getOrElse(procedure, rules(procedure))

  private def rules(procedure: ProcedureRef): ProcedureLog = procedure match
    case ActionRef.Search => ProcedureLog(tag = Some("Search"),
      lines = actions.searchLines, anchor = starts.picksSettled)
    case ActionRef.PlayFacedownAdviser =>
      ProcedureLog(tag = Some("Play facedown adviser"),
        lines = actions.playedAdviser, anchor = starts.adviserPicked)
    case ActionRef.Recover => ProcedureLog(tag = Some("Recover"),
      lines = actions.recoverLines, anchor = starts.opensDicePool,
      continued = starts.continuedRecover)
    case ActionRef.Forge => ProcedureLog(tag = Some("Forge"),
      lines = actions.forgeLines)
    case ActionRef.Travel => ProcedureLog(tag = Some("Travel"),
      lines = actions.travelLines, anchor = starts.picksSettled)
    case ActionRef.Muster => ProcedureLog(tag = Some("Muster"),
      lines = actions.musterLines, anchor = starts.paysCost)
    case ActionRef.Trade => ProcedureLog(tag = Some("Trade"),
      lines = actions.tradeLines, anchor = starts.paysCost)
    case ActionRef.Challenge => ProcedureLog(tag = Some("Challenge"),
      lines = actions.challengeLines)
    case ActionRef.Campaign => ProcedureLog(tag = Some("Campaign"),
      lines = actions.campaignLines, anchor = starts.campaignSettled,
      opening = starts.campaignWords)
    // Wake's own action: its lines stand outside any band.
    case ActionRef.TakeWealth => ProcedureLog(tag = None,
      lines = actions.takeWealthLines, isSection = false)
    // No modifier window, so no tag.
    case ActionRef.PlaceBannerResource => ProcedureLog(tag = None,
      lines = actions.bannerLines)
    case ActionRef.Negotiation => ProcedureLog(tag = None,
      lines = actions.negotiationLines)
    case ActionRef.PeekSiteRelics => ProcedureLog(tag = None,
      lines = actions.peekLines)
    case ActionRef.RevealOwnedRelic => ProcedureLog(tag = None,
      lines = actions.revealLines)
    case ActionRef.MoveWarbands => ProcedureLog(tag = None,
      lines = actions.warbandLines)
    // A power that plays a card as if searched (Oracle) posts where the card
    // went, as a Search does.
    case ActionRef.UsePower(power) => ProcedureLog(tag = None,
      lines = actions.usePowerLines(power))
    // The phase changes show in the lines around them.
    case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
      ProcedureLog(tag = None, lines = ActionLine.none, isSection = false)
    case PhaseTransitionRef.FinishRest => ProcedureLog(tag = None,
      lines = actions.finishRestLines, isSection = false)
    case TriggeredProcedureRef.Oathkeeper => ProcedureLog(tag = None,
      lines = actions.oathkeeperLines, isSection = false)
    case TriggeredProcedureRef.Setup => ProcedureLog(tag = None,
      lines = actions.setupLines, isSection = false)
    // Each forced power writes its own lines. The follow-up offer and the
    // phase menus write none: a pick starts its own walk, which writes them,
    // and a Skip is a line of its own (`FollowUpSkipped`).
    case TriggeredProcedureRef.ForcedWake |
        TriggeredProcedureRef.FollowUpOffer |
        TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, lines = ActionLine.none, isSection = false)
