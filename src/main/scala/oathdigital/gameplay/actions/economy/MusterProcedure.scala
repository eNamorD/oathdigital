package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.model._

/** Muster on the walker: pay one favor on a card at the pawn site and one
  * Supply, and gain a warband for each matching adviser plus one. Takes no
  * start selection; the modifiers are the `muster.modifiers` decision and the
  * card is the `muster.source` decision.
  */
object MusterProcedure:
  val decisionId: String = "muster.source"

  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Muster))
  /** "Mustered 2 warbands with Hearth". The parts are several `Text` spans
    * that read as the one span this line was built from before the
    * procedure wrote it itself, so the count is a `Number` and not an
    * `Amount`, which would render as a typed span. */
  val mustered: NoteKey = notes.key("mustered", Vector(
    NotePart.Text("Mustered "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"), NotePart.Text(" with "),
    NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared

  private def kind(actor: PlayerId) = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterModifierSelection,
    "Start Muster", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (player, _, matching, force) =>
      Some(Gain.Warbands(player, force, 1 + matching)),
    note = Some((ProcedureNotes.id(ActionRef.Muster),
      musteredNote(actor, _, _))))

  /** The warbands that reached the player's play area, read as the
    * difference across the gain rather than as the printed number: a gain
    * the bank clamped reads what arrived. A gain a power removed reads zero
    * and writes no line, as the rule this replaced wrote none without a
    * `Move` out of a warband bank. */
  private def musteredNote(actor: PlayerId, states: NoteStates,
      source: MusterSource): Option[PowerNote] =
    states.previous.flatMap: (before, after) =>
      val gained = warbands(after, actor) - warbands(before, actor)
      Option.when(gained > 0)(mustered.sourceless(
        NoteArg.Number(gained), NoteArg.Card(source.card)))

  private def warbands(ready: ReadyGame, actor: PlayerId): Int =
    ready.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    EconomyTree.build(catalog, state, actor, kind(actor))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    Right(EconomyTree.tree(catalog, state, actor, kind(actor)))
