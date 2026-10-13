package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.OathkeeperGoalChoice
import oathdigital.model._

object TheRedSeerCard extends Denizen(DenizenId("244"), "The Red Seer",
    Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.the-red-seer"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 2),
    text = "**ACTION:** If you hold the Darkest Secret, change the " +
      "Oathkeeper goal to anything but Devotion.")
  val powers: Vector[PrintedPower] = Vector(power)

/** The Red Seer (card 244), ACTION: "If you hold the Darkest Secret, change
  * the Oathkeeper goal to anything but Devotion."
  *
  * The engine pays the printed cost. It is usable only by a player who
  * holds the Darkest Secret; otherwise the Act menu shows it unavailable
  * with "Nothing to do now". The user chooses a new goal from the four,
  * with the current goal and Devotion shown unavailable. The title follows
  * at the action's end, where the Oathkeeper check reads the new goal.
  *
  * Its line is the action's: "Changed the Oathkeeper goal to Protection."
  * It narrates its question.
  */
case object TheRedSeer extends PaidAction(TheRedSeerCard.power):
  val decisionId: String = "power.the-red-seer.goal"
  val notDevotion: String = "The Red Seer cannot choose Devotion"
  private val barred: Map[OathkeeperGoal, String] =
    Map(OathkeeperGoal.Devotion -> notDevotion)

  override def noteKeys: Vector[NoteKey] =
    OathkeeperGoalChoice.changedKeys(NoteKey.Used)
  override def narratedDecisions: Set[String] = Set(decisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(decisionId, player,
      DecisionQuery.ChooseOne(OathkeeperGoalChoice.options(live, barred),
        heading = Some("The Red Seer: change the Oathkeeper goal to " +
          "anything but Devotion"))))),
    BuildOps((live, pending) =>
      OathkeeperGoalChoice.set(live, pending, decisionId, barred.keySet)),
    Note(id, states => PowerSourceRef.of(source).flatMap(card =>
      OathkeeperGoalChoice.changedNote(states, NoteKey.Used, card))))))
