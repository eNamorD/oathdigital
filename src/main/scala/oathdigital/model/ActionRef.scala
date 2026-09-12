package oathdigital.model

/** Stable persisted identity of an action whose tree is rebuilt on each
  * walker command. The operation tree itself remains command-local.
  */
sealed trait ActionRef extends Product with Serializable {
  def key: String
}

object ActionRef {
  case object Recover extends ActionRef { val key = "recover" }
  case object Forge extends ActionRef { val key = "forge" }
  case object Travel extends ActionRef { val key = "travel" }
  case object TakeWealth extends ActionRef { val key = "take-wealth" }

  /** A key here that also names a [[oathdigital.gameplay.MajorActionKind]]
    * bridges to it on the string alone -- `GameApplicationService
    * .walkerAction` and `GameIntentMapper.actionRef` both do that, and the
    * four major actions above rely on it.
    *
    * `TakeWealth` deliberately does not: it is one action inside the Wake
    * phase, which also holds ending Wake, so there is no major-action kind it
    * could honestly share a key with. `MajorActionKind.Wake` names the phase's
    * power timing, and the entry points at it through `fallbackKind` instead.
    * The consequence is that a preview asked for the Wake kind keeps taking
    * its existing path rather than being answered as this action.
    */
  val all: Vector[ActionRef] = Vector(Recover, Forge, Travel, TakeWealth)

  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}
