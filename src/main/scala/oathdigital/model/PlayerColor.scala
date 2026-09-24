package oathdigital.model

/** The closed set of colours a player can be. The server, the wire and the
  * frontend all read this one list, so a new colour is one edit here and the
  * compiler finds every match that has to handle it.
  *
  * Cross-compiled into the frontend (see `build.sbt`), so it depends on
  * nothing else in the model.
  */
sealed abstract class PlayerColor(val key: String)
    extends Product with Serializable

object PlayerColor {
  case object Purple extends PlayerColor("purple")
  case object Red extends PlayerColor("red")
  case object Blue extends PlayerColor("blue")
  case object Yellow extends PlayerColor("yellow")
  case object White extends PlayerColor("white")
  case object Black extends PlayerColor("black")
  case object Pink extends PlayerColor("pink")
  case object Brown extends PlayerColor("brown")

  val all: Vector[PlayerColor] =
    Vector(Purple, Red, Blue, Yellow, White, Black, Pink, Brown)

  /** Safe parse for untrusted (wire or journal) input. */
  def fromKey(value: String): Option[PlayerColor] = all.find(_.key == value)
}
