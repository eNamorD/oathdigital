package oathdigital.gameplay.actions

import oathdigital.model.{NoteKey, PowerId, ProcedureRef}

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
  def id(procedure: ProcedureRef): PowerId =
    PowerId(s"procedure.${procedure.key}")

  /** Every procedure, with the keys it writes. A procedure that has not
    * migrated writes none and its line still comes from `ProcedureLog`;
    * listing it anyway means the declared-set union covers it, so a note it
    * is not entitled to write is refused rather than unrecognised. */
  val all: Vector[(PowerId, Vector[NoteKey])] =
    ProcedureRef.all.map(procedure => id(procedure) -> keys(procedure))

  private val ids: Set[PowerId] = all.map(_._1).toSet

  /** Whether `power` is a procedure's id rather than a power's or a game
    * rule's. The Game Log asks this, and not whether a note has a source:
    * the source is optional on a type powers also use. */
  def writes(power: PowerId): Boolean = ids.contains(power)

  /** The keys a procedure writes. A procedure joins this match with its own
    * `NoteSet` in the slice that migrates it. No exhaustive match here:
    * `ProcedureLog` is the registry that fails compilation for a new
    * procedure, and `ActionRef.UsePower` is parameterized and absent from
    * `ProcedureRef.all`. */
  private def keys(procedure: ProcedureRef): Vector[NoteKey] =
    procedure match
      case _ => Vector.empty
