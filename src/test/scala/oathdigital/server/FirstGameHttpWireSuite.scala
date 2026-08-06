package oathdigital.server

import oathdigital.application.FirstGameCommand
import oathdigital.serialization.{
  FirstGameEventWire
}

class FirstGameHttpWireSuite extends munit.FunSuite {
  private def commandRequest(command: ujson.Obj): String =
    ujson.write(ujson.Obj(
      "expectedNextSequence" -> 8,
      "command" -> command
    ))

  private def beginRequest(expected: ujson.Value): String = {
    ujson.write(ujson.Obj(
      "expectedNextSequence" -> expected,
      "command" -> ujson.Obj(
        "type" -> "begin",
        "plan" -> ujson.Obj(
          "relicOrder" -> ujson.Arr("hidden")
        )
      )
    ))
  }

  test("generic command transport rejects begin and hidden plan input") {
    val error = FirstGameHttpWire
      .decodeCommand(beginRequest(ujson.Num(0))).left.toOption.get

    assertEquals(error.path, "$.command.type")
    assert(error.message.contains("bootstrap"))
  }

  test("pawn and adviser commands use explicit discriminators") {
    val pawn =
      """{"expectedNextSequence":1,"command":{"type":"placePawn",
        |"playerId":"p2","siteId":"site:a"}}""".stripMargin
    val adviser =
      """{"expectedNextSequence":2,"command":{"type":"chooseAdviser",
        |"playerId":"p2","adviserId":"9"}}""".stripMargin

    assert(FirstGameHttpWire.decodeCommand(pawn).toOption.get.command
      .isInstanceOf[FirstGameCommand.PlacePawn])
    assert(FirstGameHttpWire.decodeCommand(adviser).toOption.get.command
      .isInstanceOf[FirstGameCommand.ChooseAdviser])
  }

  test("Wake commands decode explicit actor and wealth choice") {
    val wealth = commandRequest(
      ujson.Obj("type" -> "takeWealth", "playerId" -> "p2",
        "resource" -> "favor"))
    val end = commandRequest(
      ujson.Obj("type" -> "endWake", "playerId" -> "p2"))
    assertEquals(
      FirstGameHttpWire.decodeCommand(wealth).toOption.get.command,
      FirstGameCommand.TakeWealth(
        oathdigital.model.PlayerId("p2"),
        oathdigital.setup.WakeResource.Favor
      )
    )
    assertEquals(
      FirstGameHttpWire.decodeCommand(end).toOption.get.command,
      FirstGameCommand.EndWake(oathdigital.model.PlayerId("p2"))
    )
  }

  test("development Travel command retains explicit selector actor") {
    val travel = commandRequest(ujson.Obj(
      "type" -> "travel",
      "playerId" -> "p2",
      "destinationSiteId" -> "site:b"
    ))
    assertEquals(FirstGameHttpWire.decodeCommand(travel).toOption.get.command,
      FirstGameCommand.Travel(
        oathdigital.model.PlayerId("p2"),
        oathdigital.model.SiteId("site:b")))
  }

  test("development bootstrap decodes only participant configuration") {
    val json = ujson.write(ujson.Obj(
      "expectedNextSequence" -> 0,
      "participants" -> ujson.Arr.from(Vector(
        ujson.Obj(
          "playerId" -> "p1",
          "lineageId" -> "l1",
          "color" -> "red"
        )
      )),
      "firstPlayer" -> "p1"
    ))
    val request = FirstGameHttpWire.decodeBootstrap(json).toOption.get

    assertEquals(request.expectedNextSequence, 0L)
    assertEquals(
      request.config.participants.map(_.playerId),
      Vector(oathdigital.model.PlayerId("p1"))
    )
    assertEquals(request.config.firstPlayer.value, "p1")
    assert(!json.contains("relicOrder"))
    assert(!json.contains("worldDeckOrder"))
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
