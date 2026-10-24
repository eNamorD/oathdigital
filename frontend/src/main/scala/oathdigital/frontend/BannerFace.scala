package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** A banner drawn as its component: a square face, as the printed banner
  * is, with its side across the top, its name in the middle and what it
  * holds below. It is a card face, so it takes the card's option marks and
  * the map's compact name: the whole title at rest, the short name or its
  * initials once the map is too small for it.
  *
  * The projection carries a banner's key, side, holder and count but no
  * text, so the rules each side prints are kept here.
  */
private[frontend] object BannerFace:

  /** "Banner of the People's Favor". */
  def title(banner: BannerState): String =
    s"Banner of the ${actionLabel(banner.key)}"

  /** The side as printed across its top: "grand-council" reads Grand
    * Council. */
  def side(face: String): String =
    face.split('-').filter(_.nonEmpty).map(_.capitalize).mkString(" ")

  /** What a banner holds: The People's Favor holds favor, and Darkest Secret
    * holds secrets. */
  def holds(banner: BannerState): String = banner.key match
    case "darkest-secret" =>
      if banner.resources == 1 then "1 secret" else s"${banner.resources} secrets"
    case _ => s"${banner.resources} favor"

  /** The text each side prints, as the components read. */
  def rules(face: String): Vector[String] = face match
    case "mob" => Vector(
      "When playing a card to a site, you may discard a denizen there first.",
      "**Action:** Place [favor] on the banner.")
    case "grand-council" => Vector(
      "When playing a card, you may play it to any Imperial site on the " +
        "map, and you may discard a denizen from that site first.",
      "**Action:** Place [favor] on the banner.")
    case "wandering-flame" => Vector(
      "**Action:** Place your pawn at a site with [secret] on it. " +
        "_You may do this multiple times per turn._",
      "**Action:** Place [secret] on the banner or on your site.")
    case "festival" => Vector(
      "**Rest:** You must place the festival marker over the cards at your " +
        "site. _Enemies cannot place resources on these cards or use their " +
        "powers, unless they're persistent powers._",
      "**Action:** Place [secret] on the banner.")
    case _ => Vector.empty

  private def token(banner: BannerState): String = banner.key match
    case "darkest-secret" => "secret"
    case _ => "favor"

  private def inspect(banner: BannerState)
      : Option[(String, dom.html.Element => Unit)] =
    val lines = rules(banner.face)
    Option.when(lines.nonEmpty)(title(banner) -> (origin =>
      CardInspection.openText(s"${title(banner)} · ${side(banner.face)}",
        lines, origin)))

  def render(banner: BannerState): dom.html.Button =
    val name = actionLabel(banner.key)
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.`type` = "button"
    node.className =
      s"card-face card-face-relic banner-face banner-${banner.key}"
    // The compact face fits the short name, since the prefix is hidden there.
    node.style.setProperty("--name-em",
      (math.round(CardFace.nameWidthEm(name) * 100) / 100.0).toString)
    node.setAttribute("aria-label",
      s"${title(banner)}, ${side(banner.face)}, ${holds(banner)}")
    HoverTip.set(node, title(banner))
    node.appendChild(text("span", "banner-side", side(banner.face)))
    val header = element("span", "card-header")
    val full = element("span", "card-name")
    full.appendChild(text("span", "banner-name-prefix", "Banner of the "))
    full.appendChild(dom.document.createTextNode(name))
    header.appendChild(full)
    val short = text("span", "card-name-short", CardFace.initials(name))
    short.setAttribute("aria-hidden", "true")
    header.appendChild(short)
    node.appendChild(header)
    val tokens = element("span", "card-tokens")
    tokens.appendChild(RulesTextRenderer.glyph(token(banner)))
    tokens.appendChild(text("span", "card-token-count",
      banner.resources.toString))
    node.appendChild(tokens)
    inspect(banner).foreach((_, open) => node.onclick = _ => open(node))
    node

  /** The face, marked when it is an option: then the face picks it and an
    * inspect button beside it reads its rules. */
  def place(banner: BannerState, options: BoardOptions): dom.Element =
    options.control(render(banner),
      Vector(BoardOptionViews.key("banner", banner.key)), inspect(banner))
