package oathdigital.application

import oathdigital.model.PlayerId
import oathdigital.protocol.GameIntent

class GameIntentMapperUndoSuite extends munit.FunSuite:
  test("an Undo intent binds to its sender"):
    assertEquals(GameIntentMapper.bind(PlayerId("p2"), GameIntent.Undo),
      Right(GameCommand.Undo(PlayerId("p2"))))

  test("a Redo intent binds to its sender"):
    assertEquals(GameIntentMapper.bind(PlayerId("p2"), GameIntent.Redo),
      Right(GameCommand.Redo(PlayerId("p2"))))
