package oathdigital.gameplay.oathkeeper

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Declared tree for every Oathkeeper title change (triggered, started by the
  * action boundary).
  *
  * {{{
  * Transfer(holder)  -> Sequence(SetOathkeeper(holder))              // window = OathkeeperTitleChange
  * Choose(holder, c) -> Sequence(Decide(recipient, owner = holder),
  *                        BuildOps(SetOathkeeper))                  // window = OathkeeperTitleChange
  * }}}
  *
  * A single leader is a forced choice, so it omits the `Decide` and applies the
  * transfer itself. `build` is also `rebuild`: only resume commands are
  * accepted while this parks, so the outcome cannot change under it. The
  * window (catalog batch 2, N7) lets a power append what follows a change
  * of holder, after the change: every applied `SetOathkeeper` changes the
  * holder, since the operation rejects an unchanged one.
  */
object OathkeeperProcedure:
  val recipientDecisionId: String = "oathkeeper.recipient"

  private val notes =
    NoteSet(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper))
  /** "Oathkeeper passed to p1". */
  val passedToPlayer: NoteKey = notes.key("passed-to-player", Vector(
    NotePart.Text("Oathkeeper passed to "), NotePart.Arg(0)))
  /** No leader qualifies, so the title returns to the bank. */
  val passedToBank: NoteKey = notes.key("passed-to-bank", Vector(
    NotePart.Text("Oathkeeper passed to the bank")))
  def noteKeys: Vector[NoteKey] = notes.declared

  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "the Oathkeeper procedure selects nothing, got " +
        args.map(ref => s"${ref.kind}/${ref.wireId}").mkString(", ")))
    tree <- OathkeeperRules.outcome(state) match
      case OathkeeperOutcome.NoChange => Left(OathViolation.InvalidEventOrder(
        "no Oathkeeper change to perform"))
      case OathkeeperOutcome.Transfer(holder) =>
        Right(Sequence(Vector(SetOathkeeper(holder),
          Note(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper),
            passedNote)),
          Some(PowerWindow.OathkeeperTitleChange)))
      case OathkeeperOutcome.Choose(holder, candidates) => Right(Sequence(Vector(
        Decide(
          decisionId = recipientDecisionId,
          owner = holder,
          query = DecisionQuery.ChooseOne(candidates.map(candidate =>
            DecisionOption.Player(DecisionOptionRef.Player(candidate))),
            heading = Some("Choose the Oathkeeper"))),
        BuildOps((_, pending) => pending.answered.lastOption match {
          case Some(Answered(`recipientDecisionId`,
              ChooseOneAnswer(DecisionOptionRef.Player(chosen)), _)) =>
            Right(Vector[CoreOperation](SetOathkeeper(Some(chosen))))
          case _ => Left(OathViolation.InvalidEventOrder(
            "no Oathkeeper recipient answer is recorded"))
        }),
        Note(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper),
          passedNote)), Some(PowerWindow.OathkeeperTitleChange)))
  yield tree

  /** Who holds the title after the step before the note, when that step
    * changed it. `SetOathkeeper` rejects an unchanged holder, so the rule
    * this replaced posted nothing without a recorded change; the note reads
    * the same fact off the state rather than off the operation. The note is
    * the window's own last child, so a power appending to the window runs
    * after it and cannot leave it reading another step. */
  private def passedNote(states: NoteStates): Option[PowerNote] =
    states.previous.filter((before, after) => holder(before) != holder(after))
      .map((_, after) => holder(after).fold(passedToBank.sourceless())(
        taker => passedToPlayer.sourceless(NoteArg.Player(taker))))

  private def holder(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.title.holder
