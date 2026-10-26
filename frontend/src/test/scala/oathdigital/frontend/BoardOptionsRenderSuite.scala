package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one, press}
import oathdigital.frontend.TestBoardOptions.{available, option, recording}
import oathdigital.frontend.TestBoards.*
import oathdigital.frontend.TestSites.*
import oathdigital.model.PlayerColor
import org.scalajs.dom
import scala.scalajs.js

/** The table's elements as board options, under jsdom: each is marked with
  * its state, a click picks only the element it landed on, a card's inspect
  * button reads the card without picking it, and hovering one element of an
  * option highlights every element of it.
  */
class BoardOptionsRenderSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox", rulesText = Some("Sly."))
  /** A relic at Deep Woods the viewer knows, beside one they do not. */
  private val cup = CardDetails("cup", "relic", "Cup",
    orientation = Some("face-down"))
  private val woods = quietSite("woods", "Deep Woods")
    .withDenizenCapacity(3).withDenizens(GameSiteCard("fox", "Fox", Some(fox)))
    .withRelicCapacity(1).withRelics(1)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val oak = CardDetails("oak", "denizen", "Old Oak")
  private val horn = CardDetails("horn", "relic", "Horn")
  private val board = quietBoard("red").standingAt("woods")
    .withAdvisers(oak).withRelics(horn)
    .copy(banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)))
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))))
    .copy(pawnLocations = Vector(GamePawn("red", "woods")),
      playerBoards = Vector(board),
      banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)),
      favorBanks = Vector(FavorBankState("beast", 3)))

  private val woodsSite = option("site", "woods")
  private val redPlayer = option("player", "red")
  private val foxCard = option("denizen", "fox")

  private def world(options: BoardOptions): dom.Element =
    WorldBoardRenderer.world(table, options)

  private def site(root: dom.Element): dom.Element =
    one(root, """article[aria-label="Deep Woods"]""")

  /** The names of the cards the inspector was asked to open, in order. */
  private var inspected = Vector.empty[String]

  override def beforeEach(context: BeforeEach): Unit =
    inspected = Vector.empty
    CardInspection.onOpen:
      case CardInspection.Request.Card(card, _) => inspected :+= card.name
      case _ => ()

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  test("a site option is marked and picked by a click on its box"):
    val shown = recording(available(woodsSite))
    val box = site(world(shown.options))
    assert(box.classList.contains("board-option"))
    assertEquals(box.getAttribute("data-option-ref"), "site:woods")
    assertEquals(box.getAttribute("aria-pressed"), "false")
    click(box)
    assertEquals(shown.picks, Vector(woodsSite))

  test("Enter on a marked site picks it"):
    val shown = recording(available(woodsSite))
    press(site(world(shown.options)), "Enter")
    assertEquals(shown.picks, Vector(woodsSite))

  test("a card inside a site option reads the card and does not pick the site"):
    val shown = recording(available(woodsSite))
    click(one(world(shown.options), ".site button.card-face"))
    assertEquals(inspected, Vector("Fox"))
    assertEquals(shown.picks, Vector.empty)

  test("a card option's body picks it, and its inspect button does not"):
    val shown = recording(available(foxCard))
    val root = world(shown.options)
    val face = one(root, ".site button.card-face")
    assertEquals(face.getAttribute("data-option-ref"), "denizen:fox")
    click(face)
    assertEquals(shown.picks, Vector(foxCard))
    val inspect = one(root, ".board-option-card > .board-option-inspect")
    assertEquals(inspect.getAttribute("aria-label"), "Inspect Fox")
    click(inspect)
    assertEquals(inspected, Vector("Fox"))
    assertEquals(shown.picks, Vector(foxCard))

  test("a pawn picks its player, not the site it stands at"):
    val shown = recording(available(woodsSite),
      available(redPlayer))
    click(one(world(shown.options), ".site-pawns .pawn"))
    assertEquals(shown.picks, Vector(redPlayer))

  test("everything left of a player board's cards picks the player"):
    val shown = recording(available(redPlayer))
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    val summary = one(boards, ".player-summary")
    assertEquals(summary.getAttribute("data-option-ref"), "player:red")
    assert(summary.querySelector(".player-identity") != null)
    click(one(summary, ".player-role"))
    assertEquals(shown.picks, Vector(redPlayer))

  test("Supply inside a player option opens its rule and does not pick"):
    val shown = recording(available(redPlayer))
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    click(one(boards, ".player-summary .supply-open"))
    assertEquals(shown.picks, Vector.empty)

  test("a board's adviser and relic answer to their slots and their cards"):
    val bySlot = option("adviser-slot", "red:0")
    val byCard = option("relic", "horn")
    val shown = recording(available(bySlot), available(byCard))
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    assertEquals(all(boards, ".board-cards [data-option-ref]")
      .map(_.getAttribute("data-option-ref")),
      Vector("adviser-slot:red:0", "relic:horn"))
    click(one(boards, """[data-option-ref="relic:horn"]"""))
    assertEquals(shown.picks, Vector(byCard))

  test("banners and favor banks are marked"):
    val shown = recording(available(option("banner",
      "peoples-favor")), available(option("favor-bank", "beast")))
    val root = world(shown.options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    assertEquals(all(boards, """.banner-face[data-option-ref]""").size, 1)
    assertEquals(all(root, """.shared-banners .banner-face[data-option-ref]""")
      .size, 1)
    assertEquals(all(root, """.favor-bank[data-option-ref="favor-bank:beast"]""")
      .size, 1)

  test("locked and unavailable options take no pick, and say why"):
    // Control: the same two clicks on the same elements, offered as available,
    // do pick, so the locked case's empty picks come from the state.
    val control = recording(available(woodsSite), available(foxCard))
    val controlRoot = world(control.options)
    click(site(controlRoot))
    click(one(controlRoot, ".site button.card-face"))
    assertEquals(control.picks, Vector(woodsSite, foxCard))
    val shown = recording(
      OptionView(woodsSite, OptionState.Unavailable,
        Some("Not ruled by the defender")),
      OptionView(foxCard, OptionState.Locked, None))
    val root = world(shown.options)
    val box = site(root)
    assert(box.classList.contains("board-option-unavailable"))
    assertEquals(box.getAttribute("data-tip"), "Not ruled by the defender")
    assertEquals(box.getAttribute("aria-disabled"), "true")
    click(box)
    val face = one(root, ".site button.card-face")
    assert(face.classList.contains("board-option-locked"))
    click(face)
    assertEquals(shown.picks, Vector.empty)

  test("a played option is numbered in play order and takes no pick"):
    val shown = recording(OptionView(foxCard, OptionState.Played(2), None))
    val face = one(world(shown.options), ".site button.card-face")
    assert(face.classList.contains("board-option-played"))
    assert(!face.classList.contains("board-option-locked"))
    assertEquals(face.getAttribute("data-played-order"), "2")
    assertEquals(face.getAttribute("aria-disabled"), "true")
    click(face)
    assertEquals(shown.picks, Vector.empty)

  test("a card that is no option gets no inspect button"):
    val shown = recording(available(woodsSite))
    assertEquals(all(world(shown.options), ".board-option-inspect"), Vector.empty)

  test("hovering one element of an option highlights every element of it"):
    val shown = recording(available(redPlayer))
    val root = world(shown.options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    dom.document.body.appendChild(root)
    dom.document.body.appendChild(boards)
    try
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseenter", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      val chip = one(boards, ".player-summary")
      assert(chip.classList.contains("board-option-hover"))
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseleave", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      assert(!chip.classList.contains("board-option-hover"))
    finally
      dom.document.body.removeChild(root)
      dom.document.body.removeChild(boards)

  test("an empty site slot is marked by its index and picked by a click"):
    val empty = option("site-slot", "woods:1")
    val shown = recording(available(empty))
    val root = world(shown.options)
    val slot = one(root, """[data-option-ref="site-slot:woods:1"]""")
    assertEquals(slot.getAttribute("data-slot-index"), "1")
    assertEquals(all(root, ".site-cards .card-slot-empty")
      .map(_.getAttribute("data-slot-index")), Vector("1", "2"))
    click(slot)
    assertEquals(shown.picks, Vector(empty))

  test("a site card answers to the slot it would replace"):
    val replace = option("site-slot", "woods:0").copy(replaces = Some(fox))
    val shown = recording(available(replace))
    assertEquals(one(world(shown.options), """[data-option-ref="site-slot:woods:0"]""")
      .getAttribute("data-card-id"), "fox")

  test("a site relic, known or not, answers to its slot"):
    val known = option("site-relic-slot", "woods:0")
    val unknown = option("site-relic-slot", "woods:1")
    val shown = recording(available(known), available(unknown))
    // Deep Woods holds the known Cup first, then an unknown relic.
    val root = WorldBoardRenderer.world(table.copy(world = Vector(GameRegion(
      "cradle", Vector(woods.withRelicCapacity(2).withRelics(2, cup))))),
      shown.options)
    click(one(root, """[data-option-ref="site-relic-slot:woods:0"]"""))
    click(one(root, """[data-option-ref="site-relic-slot:woods:1"]"""))
    assertEquals(shown.picks, Vector(known, unknown))

  test("a bury option carries the Bury badge"):
    val buried = option("site-slot", "woods:0").copy(replaces = Some(fox),
      buries = true)
    val root = world(recording(available(buried)).options)
    assertEquals(all(root, ".board-option-badge").map(_.textContent),
      Vector("Bury"))
    assertEquals(one(root, """[data-option-ref="site-slot:woods:0"]""")
      .getAttribute("aria-label"), "Fox, Bury")

  test("a Replace option carries the badge, and an empty slot does not"):
    val replace = option("site-slot", "woods:0").copy(replaces = Some(fox))
    val shown = recording(available(replace),
      available(option("site-slot", "woods:1")))
    val root = world(shown.options)
    assertEquals(all(root, ".board-option-badge").map(badge =>
      (badge.textContent, badge.parentNode.asInstanceOf[dom.Element].getAttribute("data-option-ref"))),
      Vector(("Replace", "site-slot:woods:0")))
    val marked = one(root, """[data-option-ref="site-slot:woods:0"]""")
    assert(marked.classList.contains("board-option-replace"))
    assertEquals(marked.getAttribute("aria-label"), "Fox, Replace")

  test("an adviser play on a free slot is drawn as a placeholder and picked " +
      "by a click"):
    val placeholder = option("adviser-play", "red:2:facedown")
    val shown = recording(available(placeholder))
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    val slot = one(boards, """[data-option-ref="adviser-slot:red:2"]""")
    assert(slot.classList.contains("card-slot-empty"))
    assertEquals(slot.getAttribute("aria-label"), "Empty adviser slot")
    click(slot)
    assertEquals(shown.picks, Vector(placeholder))

  test("an adviser play on a held slot is drawn on the card it replaces"):
    val onCard = recording(available(option("adviser-play",
      "red:0:faceup").copy(replaces = Some(oak))))
    val held = one(WorldBoardRenderer.playerBoards(table, "red",
      onCard.options), """[data-option-ref="adviser-slot:red:0"]""")
    assert(held.textContent.contains("Replace"), held.textContent)

  test("the revealed Vision spot is drawn empty only while it is an option"):
    assertEquals(all(WorldBoardRenderer.playerBoards(table, "red",
      BoardOptions.none), ".player-slot-vision"), Vector.empty)
    val spot = option("revealed-vision", "red")
    val shown = recording(available(spot))
    val boards = WorldBoardRenderer.playerBoards(table, "red", shown.options)
    val empty = one(boards, ".player-slot-vision-empty")
    assertEquals(empty.getAttribute("data-option-ref"), "revealed-vision:red")
    click(empty)
    assertEquals(shown.picks, Vector(spot))

  test("a discard pile option picks on a click, and with no cards it has " +
      "no inspect button"):
    val pile = option("discard-pile", "cradle")
    val shown = recording(available(pile))
    val root = world(shown.options)
    val face = one(root, """[data-option-ref="discard-pile:cradle"]""")
    assert(!face.asInstanceOf[dom.html.Button].disabled)
    click(face)
    assertEquals(shown.picks, Vector(pile))
    assertEquals(all(root, ".region .board-option-inspect"), Vector.empty)
