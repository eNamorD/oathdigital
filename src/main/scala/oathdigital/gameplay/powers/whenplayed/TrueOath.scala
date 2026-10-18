package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, OathkeeperGoalChoice,
  PowerAnswers, RuledCards}
import oathdigital.model._

object TrueOathCard extends Denizen(DenizenId("209"), "True Oath",
    Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-oath"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you rule at least three other " +
      "[suit-beast] cards, you may change the Oathkeeper goal.")
  val powers: Vector[PrintedPower] = Vector(power)

/** True Oath (card 209), WHEN PLAYED: "if you rule at least three other
  * Beast cards, you may change the Oathkeeper goal."
  *
  * The other Beast cards its player rules are read live, as
  * [[RuledCards]] reads ruled cards, without True Oath itself. With three
  * or more, the player chooses a new goal or keeps the current one, which
  * is shown unavailable among the four. With fewer, nothing is asked. The
  * title follows at the end of the action, where the Oathkeeper check reads
  * the new goal.
  *
  * Lines: "Changed the Oathkeeper goal to Protection.", "Kept the
  * Oathkeeper goal." and "Ruled fewer than three other Beast cards." It
  * narrates its question.
  */
final case class TrueOath private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = TrueOathCard.id
  import TrueOath._
  def id: PowerId = TrueOath.id

  override def noteKeys: Vector[NoteKey] = TrueOath.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  /** The other Beast cards `actor` rules. */
  private def others(ready: ReadyGame, actor: PlayerId): Int =
    RuledCards.of(catalog, ready, SiteRuler.Player(actor), Suit.Beast)
      .count(_ != cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      if others(live, actor) < Others then
        Vector(Note(id, _ => Some(unmet(cardSource))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          OathkeeperGoalChoice.options(live, Map.empty) :+
            DecisionOption.Button(keep, "Keep the current goal"),
          heading = Some("True Oath: you may change the Oathkeeper goal"))),
        BuildOps((ready, pending) => settle(ready, pending)),
        Note(id, note))))

  private def settle(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    if PowerAnswers.one(pending, decisionId).contains(keep) then
      Right(Vector.empty)
    else OathkeeperGoalChoice.set(ready, pending, decisionId, Set.empty)

  private def note(states: NoteStates): Option[LogNote] =
    OathkeeperGoalChoice.changedNote(states, changed, cardSource).orElse(
      Option.when(NoteSupport.answer(states, decisionId).contains(keep))(
        kept(cardSource)))

object TrueOath:
  val id: PowerId = TrueOathCard.power.id
  val decisionId: String = "cardplay.true-oath.goal"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** The other Beast cards it needs. */
  val Others: Int = 3
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Changed the Oathkeeper goal to Protection." */
  val changed: NoteKey = OathkeeperGoalChoice.changed(notes, "changed")
  /** "Kept the Oathkeeper goal." */
  val kept: NoteKey = notes.key("kept", Vector(
    NotePart.Text("Kept the Oathkeeper goal.")))
  /** "Ruled fewer than three other Beast cards." */
  val unmet: NoteKey = notes.key("unmet", Vector(
    NotePart.Text("Ruled fewer than three other Beast cards.")))

  def forCatalog(catalog: ExecutableCatalog): TrueOath = new TrueOath(catalog)
