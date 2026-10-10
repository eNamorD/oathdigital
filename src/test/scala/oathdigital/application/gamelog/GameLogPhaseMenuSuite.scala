package oathdigital.application.gamelog

import LogScripts._

/** A phase menu's walk in the Game Log (walker-driven game loop design,
  * ruling 12). */
class GameLogPhaseMenuSuite extends munit.FunSuite:
  test("a pick writes nothing: a take from the menu reads as a direct take"):
    assertEquals(texts(format(wealthFromMenu, None)),
      texts(format(wealthStarted, None)))
