package oathdigital.application.gamelog

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}

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

/** The kind a procedure's note posts at, by the procedure and the note's
  * key: `ProcedureLogs.noteKind`, as a function, so a caller that only
  * words a note need not build the whole table. */
private[gamelog] type NoteKinds = (ProcedureRef, String) => LogKind

private[gamelog] object NoteKinds:
  /** For a caller that words a note and never posts it, so never asks. */
  val unasked: NoteKinds = (_, _) => LogKind.Action

private[gamelog] object Anchor:
  /** The run's first event, where a procedure declares no later anchor. */
  val AtFirstEvent: Anchor = (_, run, at) => at == run.first

private[gamelog] object Opening:
  val none: Opening = (_, _, _, _) => Vector.empty

private[gamelog] object Continued:
  val none: Continued = (_, _, _, _) => None

/** Where a procedure's action line comes from (procedures journal their own
  * notes design, ruling 4). One `none` meant two things, "posts nothing" and
  * "posts from its own notes", so a procedure whose notes went missing read
  * as correct. */
private[gamelog] enum ActionLineSource:
  /** A rule in `ActionLines` that reads the journal. */
  case Rule(lines: ActionLine)
  /** The procedure journals its own notes, which `PowerLines` posts. Each
    * posts at `LogKind.Action` unless `kinds` names another for its key: a
    * kind is layout, so it is declared here and never beside the procedure
    * (procedures journal their own notes design, ruling 2). */
  case Notes(kinds: Map[NoteKey, LogKind] = Map.empty)
  /** The procedure posts no action line, and says why. */
  case Silent(reason: String)

/** A decision whose answer another line already tells, and the note key that
  * tells it (procedures journal their own notes design, ruling 5).
  *
  * What naming the key buys, exactly: deleting or renaming the key breaks
  * this reference, so the entry cannot outlive it. That the named key is one
  * the procedure declares is not something the compiler can see, and is
  * asserted by `GameLogPropertiesSuite` instead. A procedure whose line is
  * still a rule has no key to name. */
private[gamelog] final case class Told(decision: String,
    by: Option[NoteKey] = None)

/** What one procedure declares to the game log: its tag and where the tag
  * posts, what its opening line says, the line it repeats when the player
  * pays again, and whether its run draws a section band. Apart from the tag,
  * which is the name the log prints, every member is a declaration rather
  * than a wording: the rules themselves stay in `StartLines`, `ActionLines`
  * and their helpers, and an entry names them.
  */
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    lines: ActionLineSource,
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    narrated: Vector[Told] = Vector.empty,
    narratedPrefixes: Vector[String] = Vector.empty,
    isSection: Boolean = true):

  /** Whether another of this procedure's lines already tells the answer to
    * `decisionId`, so no "Chose …" line is posted for it. Two procedures
    * that ask the same decision each declare it: the compiler holds every
    * entry to stating its own narration, so nothing can drift. */
  def narrates(decisionId: String): Boolean =
    narrated.exists(_.decision == decisionId) ||
      narratedPrefixes.exists(decisionId.startsWith)

  /** The kind the note named `key` posts at. A procedure whose line is still
    * a rule answers `Action`, which nothing asks it for. */
  def noteKind(key: String): LogKind = lines match
    case ActionLineSource.Notes(kinds) => kinds.collectFirst {
      case (declared, kind) if declared.name == key => kind
    }.getOrElse(LogKind.Action)
    case _ => LogKind.Action

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

  /** The kind `procedure`'s note named `key` posts at, as a `NoteKinds`. */
  def noteKind(procedure: ProcedureRef, key: String): LogKind =
    of(procedure).noteKind(key)

  private def rules(procedure: ProcedureRef): ProcedureLog = procedure match
    // Card Play's placement line names the card it placed; the follow-up
    // discard of a Replace site slot is told by the note of the power that
    // permitted it.
    case ActionRef.Search => ProcedureLog(tag = Some("Search"),
      lines = ActionLineSource.Rule(actions.searchLines),
      anchor = starts.picksSettled,
      narrated = Vector(Told(SearchProcedure.sourceDecisionId),
        Told(SearchProcedure.cardDecisionId)),
      narratedPrefixes = Vector(ActionLines.PlacePrefix,
        "cardplay.discard."))
    // The played adviser's line names the facedown pick.
    case ActionRef.PlayFacedownAdviser =>
      ProcedureLog(tag = Some("Play facedown adviser"),
        lines = ActionLineSource.Rule(actions.playedAdviser),
        anchor = starts.adviserPicked,
        narrated = Vector(Told(CardPlayProcedure.facedownAdviserDecisionId)),
        narratedPrefixes = Vector(ActionLines.PlacePrefix,
          "cardplay.discard."))
    // The Recover lines name the relic taken and the site, so the choice
    // and the relic pick are told. The same holds of every row below that
    // names a decision: its own action line says what was chosen.
    case ActionRef.Recover => ProcedureLog(tag = Some("Recover"),
      lines = ActionLineSource.Rule(actions.recoverLines),
      anchor = starts.opensDicePool, continued = starts.continuedRecover,
      narrated = Vector(Told(RecoverProcedure.choiceDecisionId),
        Told(RecoverProcedure.relicDecisionId)))
    case ActionRef.Forge => ProcedureLog(tag = Some("Forge"),
      lines = ActionLineSource.Rule(actions.forgeLines),
      narrated = Vector(Told(ForgeProcedure.assignmentDecisionId)))
    // The Travel line names the destination, and a travel power's own line
    // names what it was asked.
    case ActionRef.Travel => ProcedureLog(tag = Some("Travel"),
      lines = ActionLineSource.Notes(),
      anchor = starts.picksSettled,
      narratedPrefixes = Vector("travel."))
    case ActionRef.Muster => ProcedureLog(tag = Some("Muster"),
      lines = ActionLineSource.Notes(), anchor = starts.paysCost,
      narrated = Vector(Told(MusterProcedure.decisionId,
        Some(MusterProcedure.mustered))))
    case ActionRef.Trade => ProcedureLog(tag = Some("Trade"),
      lines = ActionLineSource.Rule(actions.tradeLines),
      anchor = starts.paysCost,
      narrated = Vector(Told(TradeProcedure.decisionId)))
    case ActionRef.Challenge => ProcedureLog(tag = Some("Challenge"),
      lines = ActionLineSource.Rule(actions.challengeLines),
      narrated = Vector(Told(ChallengeProcedure.bannerDecisionId),
        Told(ChallengeProcedure.amountDecisionId)))
    // Every Campaign line tells its own decision.
    case ActionRef.Campaign => ProcedureLog(tag = Some("Campaign"),
      lines = ActionLineSource.Rule(actions.campaignLines),
      anchor = starts.campaignSettled,
      opening = starts.campaignWords,
      narratedPrefixes = Vector("campaign."))
    // Wake's own action: its lines stand outside any band.
    case ActionRef.TakeWealth => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(), isSection = false)
    // No modifier window, so no tag.
    case ActionRef.PlaceBannerResource => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.bannerLines),
      narrated = Vector(Told(PlaceBannerResourceProcedure.bannerDecisionId),
        Told(PlaceBannerResourceProcedure.amountDecisionId)))
    // Every Negotiation line tells its own decision.
    case ActionRef.Negotiation => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.negotiationLines),
      narratedPrefixes = Vector("negotiation."))
    case ActionRef.PeekSiteRelics => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.peekLines))
    case ActionRef.RevealOwnedRelic => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.revealLines))
    case ActionRef.MoveWarbands => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.warbandLines))
    // A power that plays a card as if searched (Oracle) posts where the card
    // went, as a Search does, and tells the same card-play decisions.
    case ActionRef.UsePower(power) => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.usePowerLines(power)),
      narratedPrefixes = Vector(ActionLines.PlacePrefix,
        "cardplay.discard."))
    case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
      ProcedureLog(tag = None, isSection = false,
        lines = ActionLineSource.Silent(
          "the phase changes show in the lines around them"))
    case PhaseTransitionRef.FinishRest => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.finishRestLines), isSection = false)
    case TriggeredProcedureRef.Oathkeeper => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.oathkeeperLines),
      narrated = Vector(Told(OathkeeperProcedure.recipientDecisionId)),
      isSection = false)
    // Setup's own lines tell every decision it asks.
    case TriggeredProcedureRef.Setup => ProcedureLog(tag = None,
      lines = ActionLineSource.Rule(actions.setupLines),
      narratedPrefixes = Vector("setup."), isSection = false)
    // One decision id serves the Wake, Rest and Act menus, so all three
    // declare it.
    case TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, isSection = false,
        narrated = Vector(Told(PhaseMenu.decisionId)),
        lines = ActionLineSource.Silent(
          "a pick starts its own walk, which writes the lines"))
    case TriggeredProcedureRef.ForcedWake =>
      ProcedureLog(tag = None, isSection = false,
        lines = ActionLineSource.Silent(
          "each forced power writes its own lines, and narrates nothing here"))
    case TriggeredProcedureRef.FollowUpOffer =>
      ProcedureLog(tag = None, isSection = false,
        narrated = Vector(Told(FollowUpOffer.decisionId)),
        lines = ActionLineSource.Silent(
          "the queued action's tag, or the Skip line, tells its answer"))
