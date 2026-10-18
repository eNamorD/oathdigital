package oathdigital.gameplay.powers

import oathdigital.model._

/** The question a power asks to change the Oathkeeper goal, and the line
  * that tells the change (catalog batch 8, slice 2). The Red Seer and True
  * Oath ask it.
  *
  * Every goal is listed in printed order, as a button keyed by the goal's
  * own key. The current goal is shown unavailable, and so is each goal the
  * power bars, the current goal's reason first.
  */
object OathkeeperGoalChoice:
  val currentGoal: String = "The current goal"

  def label(goal: OathkeeperGoal): String = goal match
    case OathkeeperGoal.Supremacy => "Supremacy"
    case OathkeeperGoal.Protection => "Protection"
    case OathkeeperGoal.ThePeople => "The People"
    case OathkeeperGoal.Devotion => "Devotion"

  /** The goal's name inside a sentence, "the People". */
  def named(goal: OathkeeperGoal): String = goal match
    case OathkeeperGoal.ThePeople => "the People"
    case other => label(other)

  def ref(goal: OathkeeperGoal): DecisionOptionRef.Button =
    DecisionOptionRef.Button(goal.key)

  def options(ready: ReadyGame, barred: Map[OathkeeperGoal, String])
      : Vector[DecisionOption] =
    val current = ready.game.campaign.oathkeeperGoal
    OathkeeperGoal.all.map { goal =>
      val shown = DecisionOption.Button(ref(goal), label(goal))
      val reason = if goal == current then Some(currentGoal)
        else barred.get(goal)
      reason.fold[DecisionOption](shown)(written =>
        DecisionOption.Unavailable(shown, OptionReason.Written(written)))
    }

  /** The goal answered to `decisionId`, when the answer names one. */
  def chosen(pending: PendingTree, decisionId: String)
      : Option[OathkeeperGoal] =
    PowerAnswers.one(pending, decisionId).flatMap {
      case DecisionOptionRef.Button(key) =>
        OathkeeperGoal.all.find(_.key == key)
      case _ => None
    }

  /** The answered goal set, while it is neither the current goal nor
    * barred. */
  def set(ready: ReadyGame, pending: PendingTree, decisionId: String,
      barred: Set[OathkeeperGoal])
      : Either[OathViolation, Vector[CoreOperation]] =
    chosen(pending, decisionId).toRight(PowerAnswers.missing(decisionId))
      .flatMap(goal => Either.cond(
        goal != ready.game.campaign.oathkeeperGoal && !barred(goal),
        Vector[CoreOperation](SetOathkeeperGoal(goal)),
        OathViolation.InvalidEventOrder(
          s"the Oathkeeper goal cannot change to ${goal.key}")))

  /** "Changed the Oathkeeper goal to Protection.", named `name`. */
  def changed(notes: NoteSet, name: String): NoteKey =
    notes.key(name, Vector(
    NotePart.Text("Changed the Oathkeeper goal to "), NotePart.Arg(0),
    NotePart.Text(".")))

  /** The line for the goal the step before set, if it changed one. */
  def changedNote(states: NoteStates, key: NoteKey,
      source: PowerSourceRef): Option[LogNote] = for
    (before, after) <- states.previous
    goal = after.game.campaign.oathkeeperGoal
    if goal != before.game.campaign.oathkeeperGoal
  yield key(source, NoteArg.Goal(goal))
