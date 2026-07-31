package oathdigital.server

import oathdigital.application.FirstGameCommand
import oathdigital.serialization.{
  FirstGameEventWire
}
import oathdigital.setup.FirstGameSetupEvent.FirstGameStarted
import oathdigital.setup.FirstGameSetupFixture._

class FirstGameHttpWireSuite extends munit.FunSuite {
  private def beginRequest(expected: ujson.Value): String = {
    val event = FirstGameEventWire
      .encodeEvent("game", catalogRef, 0L, FirstGameStarted(plan))
      .toOption.get
    ujson.write(ujson.Obj(
      "expectedNextSequence" -> expected,
      "command" -> ujson.Obj(
        "type" -> "begin",
        "plan" -> event("payload")
      )
    ))
  }

  test("begin command decodes every structured plan field") {
    val request =
      FirstGameHttpWire.decodeCommand(beginRequest(ujson.Num(0)))
        .toOption.get

    assertEquals(request.expectedNextSequence, 0L)
    assertEquals(request.command, FirstGameCommand.Begin(plan))
  }

  test("pawn and adviser commands use explicit discriminators") {
    val pawn =
      """{"expectedNextSequence":1,"command":{"type":"placePawn",
        |"playerId":"p2","siteId":"site:a"}}""".stripMargin
    val adviser =
      """{"expectedNextSequence":2,"command":{"type":"chooseAdviser",
        |"playerId":"p2","adviserId":"denizen:a"}}""".stripMargin

    assert(FirstGameHttpWire.decodeCommand(pawn).toOption.get.command
      .isInstanceOf[FirstGameCommand.PlacePawn])
    assert(FirstGameHttpWire.decodeCommand(adviser).toOption.get.command
      .isInstanceOf[FirstGameCommand.ChooseAdviser])
  }

  test("malformed fields report stable paths and unsafe sequences fail") {
    assertEquals(
      FirstGameHttpWire.decodeCommand(
        """{"expectedNextSequence":0,"command":{"type":"placePawn",
          |"playerId":"p1"}}""".stripMargin
      ).left.toOption.get.path,
      "$.command.siteId"
    )
    Vector(
      ujson.Num(-1),
      ujson.Num(0.5),
      ujson.Num((FirstGameEventWire.MaxSafeSequence + 1L).toDouble)
    ).foreach { value =>
      assertEquals(
        FirstGameHttpWire.decodeCommand(beginRequest(value))
          .left.toOption.get.path,
        "$.expectedNextSequence"
      )
    }
    assertEquals(
      FirstGameHttpWire.decodeCommand("{").left.toOption.get.path,
      "$"
    )
  }
}
