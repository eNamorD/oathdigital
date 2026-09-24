package oathdigital.frontend

import oathdigital.model.PlayerColor

/** The CSS class a player colour is drawn with. A class exists in
  * `styles.css` for every `PlayerColor`, plus the neutral one for text that
  * names no player.
  */
object PlayerColorCss {
  val neutral = "player-neutral"

  def of(color: PlayerColor): String = s"player-${color.key}"
  def of(color: Option[PlayerColor]): String = color.fold(neutral)(of)
}
