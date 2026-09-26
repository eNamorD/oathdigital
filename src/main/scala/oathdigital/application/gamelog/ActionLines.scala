package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** The action line each procedure posts, at the event its facts complete
  * (spec, "Action lines"). A rule reads the event being formatted and the
  * run's events before it; a name alone may come from later in the same
  * segment. The subject is omitted: it is the actor's own action.
  */
private[gamelog] final class ActionLines(words: LogWords):
  import ActionLines._

  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val ops = journal.ops(at)
    run.procedure match
      case ActionRef.Travel => ops.collect {
        case OpStep(Move(Piece.Pawn(mover), _,
            PositionedLocation(Location.Site(to), _), _), _, _)
            if mover == actor =>
          action(Vector(Text("Travelled to "), words.site(to)))
      }
      case TriggeredProcedureRef.Oathkeeper => ops.collect {
        case OpStep(SetOathkeeper(holder), _, _) =>
          Posted.line(LogKind.Trigger, Vector(Text("Oathkeeper passed to "),
            holder.fold[LogSpan](Text("the bank"))(words.player)))
      }
      case PhaseTransitionRef.FinishRest => ops.collect {
        case OpStep(GainSupply(player, _), before, after) =>
          Posted.line(LogKind.Delta, Vector(Text(
            s"Increased supply from ${LogJournal.supply(before, player)} " +
              s"to ${LogJournal.supply(after, player)}")))
      }
      // The phase changes show in the lines around them.
      case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
        Vector.empty
      // Setup lines: the second slice.
      case TriggeredProcedureRef.Setup => Vector.empty
      // Filled by Tasks 5 to 7; Task 7 deletes this case, making the match
      // exhaustive over `ProcedureRef`.
      case _ => Vector.empty

private[gamelog] object ActionLines:
  def action(spans: Vector[LogSpan]): Posted = Posted.line(LogKind.Action, spans)

  def plural(count: Int, one: String, many: String): String =
    if count == 1 then one else many

  /** "3 favor", "1 secret", "2 secrets". */
  def resource(piece: Piece): String = piece match
    case Piece.Favor(count) => s"$count favor"
    case Piece.Secrets(count) =>
      s"$count ${plural(count, "secret", "secrets")}"
    case _ => ""
