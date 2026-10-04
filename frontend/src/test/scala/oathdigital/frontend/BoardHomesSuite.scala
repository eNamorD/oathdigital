package oathdigital.frontend

import oathdigital.model.PlayerColor

/** Whether an option has a place on the table the projection draws, which
  * decides between the board and the Action panel.
  */
class BoardHomesSuite extends munit.FunSuite:
  private val fox = CardDetails("fox", "denizen", "Fox")
  private val woods = GameSite("woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 1,
    denizens = Vector(GameSiteCard("fox", "Fox", Some(fox))),
    relics = GameSiteRelics(1, Vector(CardDetails("cup", "relic", "Cup"))),
    defense = 1)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val board = PlayerBoard("red", warbands = 3, favor = 0,
    faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 1, supply = 7, pawnSiteId = Some("woods"),
    advisers = Vector(CardDetails("oak", "denizen", "Old Oak"),
      CardDetails("hidden", "denizen", "Facedown denizen", hidden = true)),
    relics = Vector(CardDetails("horn", "relic", "Horn")),
    revealedVision = None)
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

  test("buttons, decks, absent things and hidden cards have none"):
    assert(!has("button", "finish"))
    assert(!has("deck", "world"))
    assert(!has("site", "nowhere"))
    assert(!has("adviser-slot", "red:2"))
    assert(!has("denizen", "hidden"))
    assert(!has("player", "blue"))
