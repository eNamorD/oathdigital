package oathdigital.frontend

sealed trait PlayerColorToken extends Product with Serializable {
  def cssClass: String
}

object PlayerColorToken {
  case object Purple extends PlayerColorToken { val cssClass = "player-purple" }
  case object Blue extends PlayerColorToken { val cssClass = "player-blue" }
  case object Red extends PlayerColorToken { val cssClass = "player-red" }
  case object Yellow extends PlayerColorToken { val cssClass = "player-yellow" }
  case object Neutral extends PlayerColorToken { val cssClass = "player-neutral" }
}
