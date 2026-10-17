package oathdigital.gameplay.actions

import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.model.{ActionRef, NoteKey, PowerId, ProcedureRef}

/** The procedures that write their own Game Log lines (procedures journal
  * their own notes design, "ProcedureNotes"): each procedure's id and the
  * keys it writes. A procedure is not a power, so the formatter reads this
  * beside the two power catalogs and `RuleNotes`, in the shape `PrintedRule`
  * set for the restrictions that are not powers either.
  *
  * The id is derived from `ProcedureRef.key` and never spelled by hand, so
  * it cannot drift from the key a section is written under.
  */
object ProcedureNotes:
  /** A `def` over no state, so a procedure may call it while its own
    * object is still initialising. */
  def id(procedure: ProcedureRef): PowerId =
    PowerId(s"procedure.${procedure.key}")

  /** Every procedure, with the keys it writes. A procedure that has not
    * migrated writes none and its line still comes from `ProcedureLog`;
    * listing it anyway means the declared-set union covers it, so a note it
    * is not entitled to write is refused rather than unrecognised.
    *
    * `lazy`, with `ids` below, because this object and every migrated
    * procedure refer to each other: the procedure builds its `NoteSet` from
    * `id`, and `keys` reads the procedure's `noteKeys`. Eager, whichever of
    * the two a program touched first won, and touching the procedure first
    * read its `NoteSet` before its initialiser had assigned it, which is a
    * `NullPointerException` at class initialisation, not a test failure. */
  lazy val all: Vector[(PowerId, Vector[NoteKey])] =
    ProcedureRef.all.map(procedure => id(procedure) -> keys(procedure))

  /** Every procedure by the id `id` builds for it, so a note's id names the
    * procedure that wrote it. The Game Log needs the procedure and not just
    * the fact that one wrote the note, because the kind a note posts at is
    * declared on the procedure's registry entry.
    *
    * `lazy` for the same reason `all` is. */
  private lazy val byId: Map[PowerId, ProcedureRef] =
    ProcedureRef.all.map(procedure => id(procedure) -> procedure).toMap

  /** Whether `power` is a procedure's id rather than a power's or a game
    * rule's. The Game Log asks this, and not whether a note has a source:
    * the source is optional on a type powers also use. */
  def writes(power: PowerId): Boolean = byId.contains(power)

  /** The procedure `power` is the id of, if it is one. */
  def procedureOf(power: PowerId): Option[ProcedureRef] = byId.get(power)

  /** The keys a procedure writes. A procedure joins this match with its own
    * `NoteSet` in the slice that migrates it. No exhaustive match here:
    * `ProcedureLog` is the registry that fails compilation for a new
    * procedure, and `ActionRef.UsePower` is parameterized and absent from
    * `ProcedureRef.all`. */
  private def keys(procedure: ProcedureRef): Vector[NoteKey] =
    procedure match
      case ActionRef.Muster => MusterProcedure.noteKeys
      case ActionRef.Travel => TravelProcedure.noteKeys
      case ActionRef.TakeWealth => TakeWealthProcedure.noteKeys
      case ActionRef.Forge => ForgeProcedure.noteKeys
      case ActionRef.Challenge => ChallengeProcedure.noteKeys
      case _ => Vector.empty
