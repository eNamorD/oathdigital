package oathdigital.model

/** Stable persisted identity of a procedure whose tree is rebuilt on each
  * walker command. The operation tree itself remains command-local.
  *
  * Three families share this one hierarchy because Scala 2.13 has no union
  * types: `key` is unique across all of them, so a bare key resolves to at
  * most one member, and `family` is what the wire and the journal carry
  * alongside it so a decoder can reject a reference read back under the
  * wrong family (Task 4).
  */
sealed trait ProcedureRef extends Product with Serializable {
  def key: String
  def family: String
}

/** A procedure a command can name to start. Both members below extend this;
  * a [[TriggeredProcedureRef]] does not, so `StartWalker` -- typed to take a
  * `StartableRef` -- cannot express starting one from a client.
  */
sealed trait StartableRef extends ProcedureRef

/** What a player spends as their action, in any phase. The Act action
  * boundary runs after one of these completes -- see
  * `OathRulesWalker.completionIn`.
  */
sealed trait ActionRef extends StartableRef { final def family = "action" }

/** A player-commanded change of phase. These are procedures because a phase
  * change is a state write, but they are not actions: no Act action boundary
  * runs after one, whichever phase it lands in.
  */
sealed trait PhaseTransitionRef extends StartableRef {
  final def family = "phase-transition"
}

/** A procedure the engine starts on its own. No command can name one --
  * `StartableRef` excludes this family -- and no boundary runs after one.
  * Oathkeeper (Task 7) is the first: the action boundary starts it.
  */
sealed trait TriggeredProcedureRef extends ProcedureRef {
  final def family = "triggered"
}

object ActionRef {
  case object Recover extends ActionRef { val key = "recover" }
  case object Forge extends ActionRef { val key = "forge" }
  case object Travel extends ActionRef { val key = "travel" }
  case object TakeWealth extends ActionRef { val key = "take-wealth" }

  /** A key here that also names a [[oathdigital.gameplay.MajorActionKind]]
    * bridges to it on the string alone -- `GameApplicationService
    * .walkerAction` and `GameIntentMapper.actionRef` both do that, and the
    * three major actions above rely on it.
    *
    * `TakeWealth` deliberately does not: it is one of the two things a
    * player does in the Wake phase (the other, End Wake, is a
    * [[PhaseTransitionRef]] and never named here at all), so it cannot
    * honestly own the phase's name. `MajorActionKind.Wake` names the
    * phase's power timing, and Take Wealth points at it through
    * `fallbackKind` instead -- which is also what preserves the fallback
    * diagnostics the legacy `Wake` command ran under that same kind. The
    * consequence is that a preview asked for the Wake kind keeps taking its
    * existing path rather than being answered as Take Wealth.
    */
  val all: Vector[ActionRef] = Vector(Recover, Forge, Travel, TakeWealth)

  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}

object PhaseTransitionRef {
  /** Batch-1 Task 7 moved End Wake here from `ActionRef`: it is the Wake
    * phase's transition to Act, not something a player spends a turn on, so
    * no Act action boundary runs after it.
    */
  case object EndWake extends PhaseTransitionRef { val key = "end-wake" }

  val all: Vector[PhaseTransitionRef] = Vector(EndWake)
}

object TriggeredProcedureRef {
  /** Every change of the Oathkeeper title holder at an action boundary. */
  case object Oathkeeper extends TriggeredProcedureRef { val key = "oathkeeper" }
  val all: Vector[TriggeredProcedureRef] = Vector(Oathkeeper)
}

object StartableRef {
  val all: Vector[StartableRef] = ActionRef.all ++ PhaseTransitionRef.all

  def fromKey(key: String): Option[StartableRef] = all.find(_.key == key)
}

object ProcedureRef {
  val all: Vector[ProcedureRef] = StartableRef.all ++ TriggeredProcedureRef.all

  /** Resolves a reference from the wire/journal spelling of both its family
    * and its key -- rejecting a reference read back under the wrong family
    * (an End Wake key spelled `"action"`) or an unknown family, rather than
    * resolving on the key alone and trusting the family tag as decoration.
    */
  def fromFamilyKey(family: String, key: String): Option[ProcedureRef] =
    all.find(ref => ref.family == family && ref.key == key)
}
