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

  /** Every key here is also a [[oathdigital.gameplay.MajorActionKind]] key by
    * convention -- `GameApplicationService.walkerAction` and
    * `GameIntentMapper.actionRef` both bridge across on the string alone.
    */
  val all: Vector[ActionRef] = Vector(Recover, Forge, Travel)

  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}
