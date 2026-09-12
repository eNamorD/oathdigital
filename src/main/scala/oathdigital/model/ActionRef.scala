package oathdigital.model

/** Stable persisted identity of a procedure whose tree is rebuilt on each
  * walker command. The operation tree itself remains command-local.
  *
  * "Action" is the historical name and no longer the whole membership:
  * `EndWake` is a phase transition, not an action a player spends a turn on.
  * What every member shares is that it declares a tree the walker walks, and
  * that is what the registry keys on.
  */
sealed trait ActionRef extends Product with Serializable {
  def key: String
}

object ActionRef {
  case object Recover extends ActionRef { val key = "recover" }
  case object Forge extends ActionRef { val key = "forge" }
  case object Travel extends ActionRef { val key = "travel" }
  case object TakeWealth extends ActionRef { val key = "take-wealth" }
  case object EndWake extends ActionRef { val key = "end-wake" }

  /** A key here that also names a [[oathdigital.gameplay.MajorActionKind]]
    * bridges to it on the string alone -- `GameApplicationService
    * .walkerAction` and `GameIntentMapper.actionRef` both do that, and the
    * three major actions above rely on it.
    *
    * `TakeWealth` and `EndWake` deliberately do not: they are the two things
    * a player does in the Wake phase, so neither can honestly own the phase's
    * name. `MajorActionKind.Wake` names the phase's power timing, and both
    * entries point at it through `fallbackKind` instead -- which is also what
    * preserves the fallback diagnostics the legacy `Wake` command ran under
    * that same kind. The consequence is that a preview asked for the Wake
    * kind keeps taking its existing path rather than being answered as either
    * of these.
    */
  val all: Vector[ActionRef] =
    Vector(Recover, Forge, Travel, TakeWealth, EndWake)

  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}
