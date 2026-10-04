package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one, press}
import oathdigital.model.PlayerColor
import org.scalajs.dom
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

/** The table's elements as board options, under jsdom: each is marked with
  * its state, a click picks only the element it landed on, a card's inspect
  * button reads the card without picking it, and hovering one element of an
  * option highlights every element of it.
  */
class BoardOptionsRenderSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox", rulesText = Some("Sly."))
  private val woods = GameSite("woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector(GameSiteCard("fox", "Fox", Some(fox))),
    relics = GameSiteRelics(0), defense = 1)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val oak = CardDetails("oak", "denizen", "Old Oak")
  private val horn = CardDetails("horn", "relic", "Horn")
  private val board = PlayerBoard("red", warbands = 3, favor = 0,
    faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 1, supply = 7, pawnSiteId = Some("woods"),
    advisers = Vector(oak), relics = Vector(horn), revealedVision = None,
    banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)))
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))))
    .copy(pawnLocations = Vector(GamePawn("red", "woods")),
      playerBoards = Vector(board),
      banners = Vector(BannerState("peoples-favor", "mob", Some("red"), 2)),
      favorBanks = Vector(FavorBankState("beast", 3)))

  private def option(kind: String, id: String) =
    DecisionOptionState(kind, id, id)
  private val woodsSite = option("site", "woods")
  private val redPlayer = option("player", "red")
  private val foxCard = option("denizen", "fox")

  /** Options for `views`, and the picks they record. */
  private def optionsFor(views: OptionView*)
      : (BoardOptions, ArrayBuffer[DecisionOptionState]) =
    val picks = ArrayBuffer.empty[DecisionOptionState]
    (new BoardOptions(views.map(view =>
      BoardOptionViews.key(view.option) -> view).toMap,
      chosen => { picks += chosen; () }), picks)

  private def available(option: DecisionOptionState) =
    OptionView(option, OptionState.Available, None)

  private def world(options: BoardOptions): dom.Element =
    WorldBoardRenderer.world(table, None, canControl = true,
      SessionDrafts.empty, new RecordingControls(), options)

  private def site(root: dom.Element): dom.Element =
    one(root, """article[aria-label="Deep Woods"]""")

  test("a site option is marked and picked by a click on its box"):
    val (options, picks) = optionsFor(available(woodsSite))
    val box = site(world(options))
    assert(box.classList.contains("board-option"))
    assertEquals(box.getAttribute("data-option-ref"), "site:woods")
    assertEquals(box.getAttribute("aria-pressed"), "false")
    click(box)
    assertEquals(picks.toVector, Vector(woodsSite))

  test("Enter on a marked site picks it"):
    val (options, picks) = optionsFor(available(woodsSite))
    press(site(world(options)), "Enter")
    assertEquals(picks.toVector, Vector(woodsSite))

  test("a card inside a site option reads the card and does not pick the site"):
    val (options, picks) = optionsFor(available(woodsSite))
    click(one(world(options), "button.card-face"))
    assertEquals(picks.toVector, Vector.empty)

  test("a card option's body picks it, and its inspect button does not"):
    val (options, picks) = optionsFor(available(foxCard))
    val root = world(options)
    val face = one(root, "button.card-face")
    assertEquals(face.getAttribute("data-option-ref"), "denizen:fox")
    click(face)
    assertEquals(picks.toVector, Vector(foxCard))
    val inspect = one(root, ".board-option-card > .board-option-inspect")
    assertEquals(inspect.getAttribute("aria-label"), "Inspect Fox")
    click(inspect)
    assertEquals(picks.toVector, Vector(foxCard))

  test("a pawn picks its player, not the site it stands at"):
    val (options, picks) = optionsFor(available(woodsSite),
      available(redPlayer))
    click(one(world(options), ".site-pawns .pawn"))
    assertEquals(picks.toVector, Vector(redPlayer))

  test("the name in a player board's header picks the player"):
    val (options, picks) = optionsFor(available(redPlayer))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    click(one(boards, ".player-identity .player-ref"))
    assertEquals(picks.toVector, Vector(redPlayer))

  test("a board's adviser and relic answer to their slots and their cards"):
    val bySlot = option("adviser-slot", "red:0")
    val byCard = option("relic", "horn")
    val (options, picks) = optionsFor(available(bySlot), available(byCard))
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    assertEquals(all(boards, ".board-cards [data-option-ref]")
      .map(_.getAttribute("data-option-ref")),
      Vector("adviser-slot:red:0", "relic:horn"))
    click(one(boards, """[data-option-ref="relic:horn"]"""))
    assertEquals(picks.toVector, Vector(byCard))

  test("banners and favor banks are marked"):
    val (options, _) = optionsFor(available(option("banner",
      "peoples-favor")), available(option("favor-bank", "beast")))
    val root = world(options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    assertEquals(all(boards, """.player-banner[data-option-ref]""").size, 1)
    assertEquals(all(root, """.shared-banner[data-option-ref]""").size, 1)
    assertEquals(all(root, """.favor-bank[data-option-ref="favor-bank:beast"]""")
      .size, 1)

  test("locked and unavailable options take no pick, and say why"):
    val (options, picks) = optionsFor(
      OptionView(woodsSite, OptionState.Unavailable,
        Some("Not ruled by the defender")),
      OptionView(foxCard, OptionState.Locked, None))
    val root = world(options)
    val box = site(root)
    assert(box.classList.contains("board-option-unavailable"))
    assertEquals(box.getAttribute("title"), "Not ruled by the defender")
    assertEquals(box.getAttribute("aria-disabled"), "true")
    click(box)
    val face = one(root, "button.card-face")
    assert(face.classList.contains("board-option-locked"))
    click(face)
    assertEquals(picks.toVector, Vector.empty)

  test("a card that is no option still opens on a click and gets no " +
      "inspect button"):
    val (options, _) = optionsFor(available(woodsSite))
    assertEquals(all(world(options), ".board-option-inspect"), Vector.empty)

  test("hovering one element of an option highlights every element of it"):
    val (options, _) = optionsFor(available(redPlayer))
    val root = world(options)
    val boards = WorldBoardRenderer.playerBoards(table, "red", options)
    dom.document.body.appendChild(root)
    dom.document.body.appendChild(boards)
    try
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseenter", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      val chip = one(boards, ".player-identity .player-ref")
      assert(chip.classList.contains("board-option-hover"))
      one(root, ".site-pawns .pawn").dispatchEvent(new dom.MouseEvent(
        "mouseleave", js.Dynamic.literal().asInstanceOf[dom.MouseEventInit]))
      assert(!chip.classList.contains("board-option-hover"))
    finally
      dom.document.body.removeChild(root)
      dom.document.body.removeChild(boards)
