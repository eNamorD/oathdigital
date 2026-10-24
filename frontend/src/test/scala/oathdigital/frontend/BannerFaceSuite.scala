package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one}
import oathdigital.frontend.TestBoardOptions.{available, option, recording}

/** A banner drawn as its component: a square face with its side across the
  * top, its name in the middle and what it holds below. The rules it prints
  * are read in the inspector.
  */
class BannerFaceSuite extends munit.FunSuite:
  private val mob = BannerState("peoples-favor", "mob", None, 3)
  private val flame = BannerState("darkest-secret", "wandering-flame",
    Some("red"), 1)

  /** The title and rules lines of every text the inspector was asked to
    * open, in order. */
  private var opened = Vector.empty[(String, Vector[String])]

  override def beforeEach(context: BeforeEach): Unit =
    opened = Vector.empty
    CardInspection.onOpen:
      case CardInspection.Request.Text(title, lines, _) =>
        opened :+= title -> lines
      case _ => ()

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  test("a banner's face shows its side, its whole name, its initials and its favor"):
    val face = BannerFace.render(mob)
    assertEquals(face.getAttribute("class"),
      "card-face card-face-relic banner-face banner-peoples-favor")
    assertEquals(one(face, ".banner-side").textContent, "Mob")
    assertEquals(one(face, ".card-name").textContent,
      "Banner of the People's Favor")
    assertEquals(one(face, ".banner-name-prefix").textContent, "Banner of the ")
    assertEquals(one(face, ".card-name-short").textContent, "PF")
    assertEquals(all(face, ".card-tokens .token-favor").size, 1)
    assertEquals(one(face, ".card-token-count").textContent, "3")
    assertEquals(face.getAttribute("aria-label"),
      "Banner of the People's Favor, Mob, 3 favor")

  test("the Darkest Secret holds secrets, and a two-word side is spaced"):
    val face = BannerFace.render(flame)
    assertEquals(one(face, ".banner-side").textContent, "Wandering Flame")
    assertEquals(one(face, ".card-name-short").textContent, "DS")
    assertEquals(all(face, ".card-tokens .token-secret").size, 1)
    assertEquals(face.getAttribute("aria-label"),
      "Banner of the Darkest Secret, Wandering Flame, 1 secret")

  /** The compact face fits the short name, not the whole title: the prefix
    * is hidden there. */
  test("the compact name is measured on the short name"):
    val face = BannerFace.render(mob).asInstanceOf[org.scalajs.dom.html.Element]
    assertEquals(face.style.getPropertyValue("--name-em").toDouble,
      math.round(CardFace.nameWidthEm("People's Favor") * 100) / 100.0)

  test("a click on a banner reads the rules its side prints"):
    click(BannerFace.render(flame))
    assertEquals(opened.map(_._1),
      Vector("Banner of the Darkest Secret · Wandering Flame"))
    assertEquals(opened.head._2, Vector(
      "**Action:** Place your pawn at a site with [secret] on it. " +
        "_You may do this multiple times per turn._",
      "**Action:** Place [secret] on the banner or on your site."))

  test("every side of both banners has its rules"):
    Vector("mob", "grand-council").foreach(side =>
      assert(BannerFace.rules(side).nonEmpty, side))
    Vector("wandering-flame", "festival").foreach(side =>
      assert(BannerFace.rules(side).nonEmpty, side))

  test("a banner on offer is picked by its face and read by its inspect button"):
    val offered = option("banner", "peoples-favor")
    val shown = recording(available(offered))
    val placed = BannerFace.place(mob, shown.options)
    click(one(placed, ".banner-face"))
    assertEquals(shown.picks, Vector(offered))
    assertEquals(opened, Vector.empty)
    val reader = one(placed, ".board-option-inspect")
    assertEquals(reader.getAttribute("aria-label"),
      "Inspect Banner of the People's Favor")
    click(reader)
    assertEquals(opened.map(_._1),
      Vector("Banner of the People's Favor · Mob"))
    assertEquals(shown.picks, Vector(offered))
