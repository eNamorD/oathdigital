package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.{LogSection}

/** Which runs are sections (log action sections design, "Where a section
  * comes from"): every top-level action is one. Wake's Take Wealth, phase
  * transitions and triggered procedures are not actions a reader looks for,
  * so their lines stand outside any band. */
private[gamelog] object LogSections:
  def of(procedure: ProcedureRef): Boolean = procedure match
    case ActionRef.TakeWealth => false
    case _: ActionRef => true
    case _: PhaseTransitionRef | _: TriggeredProcedureRef => false

  def forRun(journal: LogJournal, run: Run): Option[LogSection] =
    Option.when(of(run.procedure))(
      LogSection(journal.sequence(run.first), run.procedure.key))
