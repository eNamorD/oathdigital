package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The one action line still inferred from the journal rather than
  * journaled by the procedure that caused it: the "Used {card}" fallback
  * for a power that writes no `used` note of its own. Every procedure now
  * writes its own (procedures journal their own notes design).
  *
  * A rule reads the event being formatted and the run's events before it;
  * a name alone may come from later in the same segment. The subject is
  * omitted: it is the actor's own action.
  */
private[gamelog] final class ActionLines(words: LogWords):
  import ActionLines._

  /** The "Used {card}" fallback, for a power that writes no `used` note of
    * its own. */
  def usePowerLines(power: PowerId)(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    usedPower(journal, run, at, power,
      journal.event(at).isInstanceOf[WalkerCompleted], viewer)

  /** "Used {card}" at the first step recording one of the power's own
    * effects: a step that is not only the payment onto its card or to the
    * bank, and not only the use record. At completion if it recorded none. */
  private def usedPower(journal: LogJournal, run: Run, at: Int, power: PowerId,
      completing: Boolean, viewer: Option[PlayerId]): Vector[Posted] =
    val postedEarlier = (run.first until at).exists(effect(journal, run, _))
    if postedEarlier || !(effect(journal, run, at) || completing) ||
        journal.notedUse(run, power, at) then Vector.empty
    else Vector(action(Text("Used ") +: powerSource(journal, run, at, power,
      viewer)))

  private def effect(journal: LogJournal, run: Run, index: Int): Boolean =
    journal.event(index) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, ops, _) =>
        !ops.forall {
          case RecordPowerUse(_) => true
          case QueueProcedure(_, _) => true
          case Move(Piece.Favor(_) | Piece.Secrets(_),
              PositionedLocation(Location.PlayArea(payer), _, _),
              PositionedLocation(Location.OnCard(_) | Location.SharedBank, _, _),
              _, _) => payer == run.actor
          case _ => false
        }
      // A power's own question: its source is already known, so "Used"
      // comes before the "Chose" line its answer posts.
      case WalkerStepRecorded(_, _: ChoicePayload, _, _) => true
      case _ => false

  /** The source the player named when starting (kept on every park), else
    * the use record's source, else the card the power is printed on. */
  private def powerSource(journal: LogJournal, run: Run, at: Int,
      power: PowerId, viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val ready = journal.readyBefore(run.first)
    val started = through.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.startArgs
    }.flatMap(_.headOption).flatMap(ref => ready.flatMap(state =>
      named(ref, state, viewer)))
    val used = through.flatMap(journal.ops).collectFirst {
      case OpStep(RecordPowerUse(PowerUseRef(_, source, _)), _, _) => source
    }.flatMap(source => ready.map(state => words.source(source, Vector(state), viewer)))
    started.orElse(used).orElse(ready.map(state =>
      words.power(state, run.actor, power, viewer)))
      .getOrElse(Vector(Text(power.value)))

  private def named(ref: DecisionOptionRef, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] = ref match
    case DecisionOptionRef.Denizen(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Relic(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Vision(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Edifice(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Banner(banner) => Some(Vector(words.banner(banner)))
    case _ => None

private[gamelog] object ActionLines:
  def action(spans: Vector[LogSpan]): Posted = Posted(LogKind.Action, spans)

  def plural(count: Int, one: String, many: String): String =
    if count == 1 then one else many

  /** `CardPlayProcedure`'s placement decision id prefix; the card follows as
    * `{kind}.{id}`, the same spelling `WalkerDecisionProjector.subjectCards`
    * parses. */
  val PlacePrefix = "cardplay.place."

  def subjectCard(subject: String): Option[CardId] =
    subject.split("\\.", 2).toVector match
      case Vector("denizen", value) => Some(DenizenId(value))
      case Vector("vision", value) => Some(VisionId(value))
      case _ => None
