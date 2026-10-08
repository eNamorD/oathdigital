package oathdigital.frontend

import oathdigital.frontend.TestBoards.*
import oathdigital.frontend.TestSites.*
import oathdigital.model.PlayerColor

/** Whether an option has a place on the table the projection draws, which
  * decides between the board and the Action panel.
  */
class BoardHomesSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox")
  private val woods = quietSite("woods", "Deep Woods")
    .withDenizenCapacity(3).withRelicCapacity(1)
    .withDenizens(GameSiteCard("fox", "Fox", Some(fox)))
    .withRelics(1, CardDetails("cup", "relic", "Cup"))
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val board = quietBoard("red").standingAt("woods")
    .withAdvisers(CardDetails("oak", "denizen", "Old Oak"),
      CardDetails("hidden", "denizen", "Facedown denizen", hidden = true))
    .withRelics(CardDetails("horn", "relic", "Horn"))
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))))
    .copy(playerBoards = Vector(board),
      banners = Vector(BannerState("peoples-favor", "mob", None, 1)),
      favorBanks = Vector(FavorBankState("beast", 3)))
  private def has(kind: String, id: String) =
    BoardHomes.has(table, DecisionOptionState(kind, id, id))

  test("sites, site cards, board cards, slots, banners, banks and players " +
      "have homes"):
    assert(has("site", "woods"))
    assert(has("denizen", "fox"))
    assert(has("relic", "cup"))
    assert(has("denizen", "oak"))
    assert(has("relic", "horn"))
    assert(has("adviser-slot", "red:1"))
    assert(has("relic-slot", "red:0"))
    assert(has("banner", "peoples-favor"))
    assert(has("favor-bank", "beast"))
    assert(has("player", "red"))
    assert(has("deck", "world"))

  test("buttons, other decks, absent things and hidden cards have none"):
    assert(!has("button", "finish"))
    assert(!has("deck", "relic"))
    assert(!has("site", "nowhere"))
    assert(!has("adviser-slot", "red:3"))
    assert(!has("denizen", "hidden"))
    assert(!has("player", "blue"))

  test("card slots have homes: site slots below capacity, adviser plays " +
      "up to the limit, the Vision spot and the discard piles"):
    assert(has("site-slot", "woods:0"))
    assert(has("site-slot", "woods:2"))
    assert(!has("site-slot", "woods:3"))
    assert(!has("site-slot", "nowhere:0"))
    assert(has("adviser-play", "red:2:faceup"))
    assert(has("adviser-play", "red:0:facedown"))
    assert(!has("adviser-play", "red:3:faceup"))
    assert(!has("adviser-play", "blue:0:faceup"))
    assert(has("revealed-vision", "red"))
    assert(!has("revealed-vision", "blue"))
    assert(has("discard-pile", "cradle"))
    assert(!has("discard-pile", "hinterland"))

  test("a lowered adviser limit draws one slot fewer"):
    val lowered = table.copy(playerBoards = Vector(board.copy(
      advisers = Vector.empty, adviserLimit = 2)))
    assert(BoardHomes.has(lowered, DecisionOptionState("adviser-play",
      "red:1:faceup", "")))
    assert(!BoardHomes.has(lowered, DecisionOptionState("adviser-play",
      "red:2:faceup", "")))
