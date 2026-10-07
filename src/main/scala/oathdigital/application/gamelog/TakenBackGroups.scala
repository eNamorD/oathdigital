package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** Marks every entry posted by a voided event with its taken-back group
  * (undo design, "Game Log"): one group per contiguous run of voided
  * entries, headed by the players whose undos made it. */
private[gamelog] final class TakenBackGroups(words: LogWords):
  def mark(markers: Vector[OathEvent.StepTakenBack],
      entries: Vector[LogEntry]): Vector[LogEntry] =
    def issuer(entry: LogEntry): Option[PlayerId] = markers.collectFirst {
      case OathEvent.StepTakenBack(from, until, by) if entry.sequence >= from &&
          entry.sequence < until => by }
    val runs = entries.foldLeft(
        Vector.empty[Vector[(LogEntry, Option[PlayerId])]]) { (done, entry) =>
      val tagged = entry -> issuer(entry)
      done.lastOption match
        case Some(run) if run.last._2.nonEmpty == tagged._2.nonEmpty =>
          done.init :+ (run :+ tagged)
        case _ => done :+ Vector(tagged)
    }
    runs.flatMap { run =>
      if run.head._2.isEmpty then run.map(_._1)
      else
        val group = LogTakenBack(run.head._1.sequence,
          header(run.flatMap(_._2).distinct))
        run.map((entry, _) => entry.copy(takenBack = Some(group)))
    }

  /** "p1 took back the following moves:", naming `players` as "p1 and p2"
    * or "p1, p2 and p3". */
  private def header(players: Vector[PlayerId]): Vector[LogSpan] =
    val names = players.map(words.player)
    val listed = names match
      case Vector(only) => Vector(only)
      case _ => names.init.zipWithIndex.flatMap { (name, index) =>
        if index == 0 then Vector(name) else Vector(Text(", "), name)
      } ++ Vector(Text(" and "), names.last)
    listed :+ Text(" took back the following moves:")
