package oathdigital.model

/** The closed set of colours a player can be. The server, the wire and the
  * frontend all read this one list, so a new colour is one edit here and the
  * compiler finds every match that has to handle it.
  *
  * Cross-compiled into the frontend (see `build.sbt`), so it depends on
  * nothing else in the model.
  */
enum PlayerColor(val key: String):
  case Purple extends PlayerColor("purple")
  case Red extends PlayerColor("red")
  case Blue extends PlayerColor("blue")
  case Yellow extends PlayerColor("yellow")
  case White extends PlayerColor("white")
  case Black extends PlayerColor("black")
  case Pink extends PlayerColor("pink")
  case Brown extends PlayerColor("brown")
object PlayerColor:
  val all: Vector[PlayerColor] =
    Vector(Purple, Red, Blue, Yellow, White, Black, Pink, Brown)

  /** Safe parse for untrusted (wire or journal) input. */
  def fromKey(value: String): Option[PlayerColor] = all.find(_.key == value)
