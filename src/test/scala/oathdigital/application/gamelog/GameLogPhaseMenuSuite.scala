package oathdigital.application.gamelog

import LogScripts._

/** A phase menu's walk in the Game Log (walker-driven game loop design,
  * ruling 12). */
class GameLogPhaseMenuSuite extends munit.FunSuite:
  test("a pick writes nothing: a take from the menu reads as a direct take"):
    assertEquals(texts(format(wealthFromMenu, None)),
      texts(format(wealthStarted, None)))

  test("an Act pick writes nothing of its own: only the peek's line follows " +
      "the phase"):
    // The round, the turn and the Act phase, then the peek: the pick of
    // Peek Site Relics from the Act menu adds no line, no section.
    val entries = format(peekRelics, None)
    assertEquals(entries.size, 4)
    assertEquals(entries.map(_.kind), Vector(LogKind.Round, LogKind.Turn,
      LogKind.Phase, LogKind.Action))
