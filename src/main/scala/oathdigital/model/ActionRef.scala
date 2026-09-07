package oathdigital.model

/** Stable persisted identity of an action whose tree is rebuilt on each
  * walker command. The operation tree itself remains command-local.
  */
sealed trait ActionRef extends Product with Serializable {
  def key: String
}

object ActionRef {
  case object Recover extends ActionRef { val key = "recover" }

  val all: Vector[ActionRef] = Vector(Recover)

  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}
