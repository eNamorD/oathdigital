package oathdigital.frontend

import org.scalajs.dom

/** The seventeen catalog icon tokens as one inline SVG sprite.
  *
  * Inline SVG rather than Unicode because colour-emoji glyphs ignore CSS
  * `color` entirely, and non-emoji Unicode has no character resembling a
  * cracked coin or a torn book -- which is exactly what the two burnt tokens
  * need. `currentColor` inside a `<symbol>` resolves against the referencing
  * `<use>`, so a class on the `<use>`'s owner colours the glyph.
  */
private[frontend] object TokenSprite {
  private val Svg = "http://www.w3.org/2000/svg"

  /** token name -> (accessible name, path data). Order is the sprite order. */
  private val glyphs: Vector[(String, String, String)] = Vector(
    ("favor", "favor",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z"),
    ("favor-burnt", "burnt favor",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z" +
        "M11.2 2.1l2 .2-1.4 4.6 2.6 2.4-2.9 3.3 2.1 9.3-1.9.2-1.8-9.7 2.8-3.2-2.5-2.3z"),
    ("secret", "secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zm3 3v12h10V6z"),
    ("secret-burnt", "burnt secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zm3 3v12h10V6z" +
        "M11.4 3l1.9.4-1.7 4.3 2.7 2.6-2.6 3 1.6 7.6-1.9.4-1.8-8.2 2.5-2.9-2.4-2.3z"),
    ("suit-arcane", "arcane suit",
      "M12 2 22 21H2zm0 6-1.2 3.6H7l3 2.3-1.2 3.6L12 15.2l3.2 2.3L14 13.9l3-2.3h-3.8z"),
    ("suit-beast", "beast suit",
      "M2 2l5 6h10l5-6-2 9-8 11-8-11zm7 9h2v2H9zm4 0h2v2h-2z"),
    ("suit-discord", "discord suit",
      "M3 2l4 6a8 8 0 0 1 10 0l4-6-2 8a8 8 0 1 1-14 0zM9 12h2v2H9zm4 0h2v2h-2z"),
    ("suit-hearth", "hearth suit",
      "M12 2 2 12h3v9h14v-9h3zm-2 12h4v7h-4z"),
    ("suit-nomad", "nomad suit",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM7 10h4v1.5H7zm6 0h4v1.5h-4z" +
        "m-5.2 4.3 1.6-.9A4 4 0 0 0 12 15.5a4 4 0 0 0 3.6-2.1l1.6.9A5.8 5.8 0 0 1 12 17.5" +
        "a5.8 5.8 0 0 1-4.2-3.2z"),
    ("suit-order", "order suit",
      "M3 3h18v18H3zm2 2v14h14V5zm3 3h2v2H8zm6 0h2v2h-2zm-6 6h2v2H8zm6 0h2v2h-2z" +
        "m-3-3h2v2h-2z"),
    ("attack-die", "attack die",
      "M4 4h16v16H4zm2 2v12h12V6zm2 2h2.5v2.5H8zm2.75 2.75h2.5v2.5h-2.5z" +
        "M13.5 13.5H16V16h-2.5z"),
    ("defense-die", "defense die",
      "M12 2 22 12 12 22 2 12zm0 3.5L5.5 12 12 18.5 18.5 12zM10 9h2v2h-2z" +
        "m2 4h2v2h-2z"),
    ("round-die", "round die",
      "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z" +
        "m0 2a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z" +
        "m4.5 5.5h3v3h-3z"),
    ("skull", "skull",
      "M12 2a8 8 0 0 0-8 8v4l2 2v4h3v-3h6v3h3v-4l2-2v-4a8 8 0 0 0-8-8z" +
        "M8.5 9.5a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm7 0a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"),
    ("shield", "shield",
      "M12 2 3 5v7c0 5.2 3.8 9.4 9 11 5.2-1.6 9-5.8 9-11V5z" +
        "m0 2.3 7 2.3V12c0 4-2.8 7.4-7 8.9-4.2-1.5-7-4.9-7-8.9V6.6z"),
    ("sword", "sword",
      "M12 1 14.5 5v9h-5V5zM6 14h12v2.5H6zm4.75 2.5h2.5V23h-2.5z"),
    ("hollow-sword", "hollow sword",
      "M12 1 14.5 5v9h-5V5zm0 3.4L11.5 6v6.5h1V6zM6 14h12v2.5H6z" +
        "m1.2 1.1v.3h9.6v-.3zm3.55 1.4h2.5V23h-2.5zm1 1v5h.5v-5z")
  )

  val ids: Vector[String] = glyphs.map(_._1)

  private val labels: Map[String, String] =
    glyphs.map { case (id, label, _) => id -> label }.toMap

  def label(token: String): String = labels.getOrElse(token, token)

  def isToken(value: String): Boolean = labels.contains(value)

  /** Appends the hidden sprite once. A second call on the same root is inert,
    * so callers need not track whether the document already has it.
    */
  def mount(root: dom.Element): Unit =
    if (root.querySelector("svg.token-sprite") == null) {
      val sprite = dom.document.createElementNS(Svg, "svg")
      sprite.setAttribute("class", "token-sprite")
      sprite.setAttribute("aria-hidden", "true")
      glyphs.foreach { case (id, _, path) =>
        val symbol = dom.document.createElementNS(Svg, "symbol")
        symbol.setAttribute("id", s"token-$id")
        symbol.setAttribute("viewBox", "0 0 24 24")
        val shape = dom.document.createElementNS(Svg, "path")
        shape.setAttribute("d", path)
        shape.setAttribute("fill", "currentColor")
        shape.setAttribute("fill-rule", "evenodd")
        symbol.appendChild(shape)
        sprite.appendChild(symbol)
      }
      root.appendChild(sprite)
    }
}
