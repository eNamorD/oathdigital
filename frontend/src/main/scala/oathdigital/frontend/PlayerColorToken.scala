package oathdigital.frontend

sealed trait PlayerColorToken extends Product with Serializable {
  def cssClass: String
}

object PlayerColorToken {
  case object Purple extends PlayerColorToken { val cssClass = "player-purple" }
  case object Blue extends PlayerColorToken { val cssClass = "player-blue" }
  case object Red extends PlayerColorToken { val cssClass = "player-red" }
  case object Yellow extends PlayerColorToken { val cssClass = "player-yellow" }
  case object White extends PlayerColorToken { val cssClass = "player-white" }
  case object Black extends PlayerColorToken { val cssClass = "player-black" }
  case object Pink extends PlayerColorToken { val cssClass = "player-pink" }
  case object Brown extends PlayerColorToken { val cssClass = "player-brown" }
  case object Neutral extends PlayerColorToken { val cssClass = "player-neutral" }

  def fromKey(value: String): PlayerColorToken = value match {
    case "purple" => Purple
    case "blue" => Blue
    case "red" => Red
    case "yellow" => Yellow
    case "white" => White
    case "black" => Black
    case "pink" => Pink
    case "brown" => Brown
    case _ => Neutral
  }
}
