package oathdigital.server

import oathdigital.application.{CardDecisionResolution, GameCommand, GameProjection,
  OathkeeperProjection}
import oathdigital.model.{DecisionId, DenizenId, EconomyTargetRef, EdificeId,
  CampaignBanner, CampaignRaidTarget, PendingProcedure, PlayerId, RelicId, SiteId}
import oathdigital.serialization.{
  GameEventWire
}

class GameHttpWireSuite extends munit.FunSuite {
  test("development Negotiation command retains its explicit loopback actor") {
    val json = """{"gameId":"game","expectedNextSequence":50,"command":{"type":"beginNegotiation","playerId":"red","participantPlayerIds":["blue","yellow"]}}"""
    assertEquals(GameHttpWire.decodeCommand(json).toOption.get.command,
      oathdigital.application.GameCommand.BeginNegotiation(PlayerId("red"),
        Vector(PlayerId("blue"), PlayerId("yellow"))))
  }
  test("development Forge commands retain actor and exclude relic identity") {
    val begin = commandRequest(ujson.Obj("type" -> "beginForge",
      "playerId" -> "p2"))
    assertEquals(GameHttpWire.decodeCommand(begin).toOption.get.command,
      GameCommand.BeginForge(PlayerId("p2")))
    val complete = commandRequest(ujson.Obj("type" -> "completeForge",
      "playerId" -> "p2", "decisionId" -> "forge-8",
      "assignments" -> ujson.Arr(
        ujson.Obj("siteId" -> "site:a", "denizenId" -> "denizen:1",
          "resource" -> "favor"))))
    assert(GameHttpWire.decodeCommand(complete).toOption.get.command
      .isInstanceOf[GameCommand.CompleteForge])
    val spoofed = ujson.read(complete).obj
    spoofed("command").obj("relicId") = "relic:spoofed"
    assert(GameHttpWire.decodeCommand(ujson.write(spoofed)).isLeft)
  }
  test("development Campaign commands retain explicit selector actor") {
    val begin = commandRequest(ujson.Obj("type" -> "beginCampaignConquest",
      "playerId" -> "p2", "targetSiteIds" -> ujson.Arr("site:a", "site:b"),
      "attackDiceCount" -> 3))
    val sacrifice = commandRequest(ujson.Obj("type" -> "chooseCampaignSacrifice",
      "playerId" -> "p2", "decisionId" -> "campaign-8", "count" -> 1))
    val plan = commandRequest(ujson.Obj("type" -> "chooseCampaignPlan",
      "playerId" -> "p2", "decisionId" -> "campaign-8",
      "source" -> ujson.Obj("kind" -> "adviser", "playerId" -> "p2",
        "cardId" -> "143")))
    val place = commandRequest(ujson.Obj("type" -> "placeCampaignForce",
      "playerId" -> "p2", "decisionId" -> "campaign-8",
      "allocations" -> ujson.Arr(
        ujson.Obj("siteId" -> "site:a", "count" -> 2),
        ujson.Obj("siteId" -> "site:b", "count" -> 0))))
    val finish = commandRequest(ujson.Obj("type" -> "finishCampaignPlans",
      "playerId" -> "p2", "decisionId" -> "campaign-8"))
    assertEquals(GameHttpWire.decodeCommand(begin).toOption.get.command,
      GameCommand.BeginCampaignConquest(PlayerId("p2"),
        Vector(oathdigital.model.SiteId("site:a"),
          oathdigital.model.SiteId("site:b")), 3))
    assertEquals(GameHttpWire.decodeCommand(sacrifice).toOption.get.command,
      GameCommand.ChooseCampaignSacrifice(PlayerId("p2"),
        DecisionId("campaign-8"), 1))
    assertEquals(GameHttpWire.decodeCommand(plan).toOption.get.command,
      GameCommand.ChooseCampaignPlan(PlayerId("p2"), DecisionId("campaign-8"),
        PendingProcedure.CampaignPlanSource.Adviser(PlayerId("p2"),
          DenizenId("143"))))
    val relicPlan = commandRequest(ujson.Obj("type" -> "chooseCampaignPlan",
      "playerId" -> "p2", "decisionId" -> "campaign-8",
      "source" -> ujson.Obj("kind" -> "relic", "playerId" -> "p2",
        "cardId" -> "R25")))
    assertEquals(GameHttpWire.decodeCommand(relicPlan).toOption.get.command,
      GameCommand.ChooseCampaignPlan(PlayerId("p2"), DecisionId("campaign-8"),
        PendingProcedure.CampaignPlanSource.Relic(PlayerId("p2"),
          RelicId("R25"))))
    assertEquals(GameHttpWire.decodeCommand(place).toOption.get.command,
      GameCommand.PlaceCampaignForce(PlayerId("p2"),
        DecisionId("campaign-8"), Vector(
          oathdigital.model.CampaignForceAllocation(
            oathdigital.model.SiteId("site:a"), 2),
          oathdigital.model.CampaignForceAllocation(
            oathdigital.model.SiteId("site:b"), 0))))
    assertEquals(GameHttpWire.decodeCommand(finish).toOption.get.command,
      GameCommand.FinishCampaignPlans(PlayerId("p2"), DecisionId("campaign-8")))
  }

  test("development Raid commands retain typed targets and actor") {
    val raid = commandRequest(ujson.Obj("type" -> "beginCampaignRaid",
      "playerId" -> "p1", "targets" -> ujson.Arr(
        ujson.Obj("kind" -> "pawn", "playerId" -> "p2"),
        ujson.Obj("kind" -> "darkest-secret", "playerId" -> "p2")),
      "attackDiceCount" -> 1))
    assertEquals(GameHttpWire.decodeCommand(raid).toOption.get.command,
      GameCommand.BeginCampaignRaid(PlayerId("p1"), Vector(
        CampaignRaidTarget.Pawn(PlayerId("p2")),
        CampaignRaidTarget.Banner(PlayerId("p2"), CampaignBanner.DarkestSecret)), 1))
    val relocate = commandRequest(ujson.Obj("type" -> "relocateCampaignRaidPawn",
      "playerId" -> "p1", "decisionId" -> "campaign-9",
      "destinationSiteId" -> "S4"))
    assertEquals(GameHttpWire.decodeCommand(relocate).toOption.get.command,
      GameCommand.RelocateCampaignRaidPawn(PlayerId("p1"),
        DecisionId("campaign-9"), SiteId("S4")))
  }

  test("projection exposes public scoped Oathkeeper and victory status") {
    val projection = GameProjection("game", 12L, "game-over", Some("p2"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty,
      ready = true, completed = true,
      oathkeeper = Some(OathkeeperProjection("supremacy", Some("p2"),
        "usurper", usurperLimited = false, Some("p2"))))
    val json = ujson.read(GameHttpWire.encodeProjection(projection))
    assertEquals(json("oathkeeper")("holderPlayerId").str, "p2")
    assertEquals(json("oathkeeper")("side").str, "usurper")
    assertEquals(json("oathkeeper")("winnerPlayerId").str, "p2")
    assert(!GameHttpWire.encodeProjection(projection).contains("lineage"))
  }
  test("generic development card decision carries actor and typed resolution") {
    val json = commandRequest(ujson.Obj("type" -> "resolveCardDecision",
      "playerId" -> "p2", "decisionId" -> "setup-adviser-0-p2",
      "resolution" -> ujson.Obj("kind" -> "starting-adviser",
        "adviserId" -> "denizen:a")))
    assertEquals(GameHttpWire.decodeCommand(json).toOption.get.command,
      GameCommand.ResolveCardDecision(PlayerId("p2"),
        DecisionId("setup-adviser-0-p2"),
        CardDecisionResolution.StartingAdviser(DenizenId("denizen:a"))))
  }
  test("development Rest commands retain the explicit selector actor") {
    val begin = GameHttpWire.decodeCommand(
      """{"expectedNextSequence":12,"command":{"type":"beginRest","playerId":"p1"}}""")
      .toOption.get
    val finish = GameHttpWire.decodeCommand(
      """{"expectedNextSequence":13,"command":{"type":"finishRest","playerId":"p1"}}""")
      .toOption.get
    assertEquals(begin.command, GameCommand.BeginRest(PlayerId("p1")))
    assertEquals(finish.command, GameCommand.FinishRest(PlayerId("p1")))
  }
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
    val error = GameHttpWire
      .decodeCommand(beginRequest(ujson.Num(0))).left.toOption.get

    assertEquals(error.path, "$.command.type")
    assert(error.message.contains("bootstrap"))
  }

  test("pawn remains explicit and legacy adviser command is rejected") {
    val pawn =
      """{"expectedNextSequence":1,"command":{"type":"placePawn",
        |"playerId":"p2","siteId":"site:a"}}""".stripMargin
    val adviser =
      """{"expectedNextSequence":2,"command":{"type":"chooseAdviser",
        |"playerId":"p2","adviserId":"9"}}""".stripMargin

    assert(GameHttpWire.decodeCommand(pawn).toOption.get.command
      .isInstanceOf[GameCommand.PlacePawn])
    assert(GameHttpWire.decodeCommand(adviser).isLeft)
  }

  test("Wake commands decode explicit actor and wealth choice") {
    val wealth = commandRequest(
      ujson.Obj("type" -> "takeWealth", "playerId" -> "p2",
        "resource" -> "favor"))
    val end = commandRequest(
      ujson.Obj("type" -> "endWake", "playerId" -> "p2"))
    assertEquals(
      GameHttpWire.decodeCommand(wealth).toOption.get.command,
      GameCommand.TakeWealth(
        oathdigital.model.PlayerId("p2"),
        oathdigital.setup.WakeResource.Favor
      )
    )
    assertEquals(
      GameHttpWire.decodeCommand(end).toOption.get.command,
      GameCommand.EndWake(oathdigital.model.PlayerId("p2"))
    )
  }

  test("development Travel command retains explicit selector actor") {
    val travel = commandRequest(ujson.Obj(
      "type" -> "travel",
      "playerId" -> "p2",
      "destinationSiteId" -> "site:b"
    ))
    assertEquals(GameHttpWire.decodeCommand(travel).toOption.get.command,
      GameCommand.Travel(
        oathdigital.model.PlayerId("p2"),
        oathdigital.model.SiteId("site:b")))
  }

  test("development Search begin rejects client-provided hidden outcomes") {
    val valid = commandRequest(ujson.Obj(
      "type" -> "beginSearch", "playerId" -> "p2", "source" -> "world"))
    assert(GameHttpWire.decodeCommand(valid).toOption.get.command
      .isInstanceOf[GameCommand.BeginSearch])
    val hidden = commandRequest(ujson.Obj(
      "type" -> "beginSearch", "playerId" -> "p2", "source" -> "world",
      "drawn" -> ujson.Arr("denizen:chosen")))
    assertEquals(GameHttpWire.decodeCommand(hidden).left.toOption.get.path,
      "$.command.drawn")
  }

  test("development Economy commands preserve typed targets exactly") {
    val target = ujson.Obj("kind" -> "edifice", "id" -> "E26")
    val muster = commandRequest(ujson.Obj("type" -> "muster",
      "playerId" -> "p2", "target" -> target))
    val trade = commandRequest(ujson.Obj("type" -> "trade",
      "playerId" -> "p2", "target" -> target, "resource" -> "favor"))
    val expected = EconomyTargetRef.Edifice(EdificeId("E26"))
    assertEquals(GameHttpWire.decodeCommand(muster).toOption.get.command,
      GameCommand.Muster(PlayerId("p2"), expected))
    assertEquals(GameHttpWire.decodeCommand(trade).toOption.get.command,
      GameCommand.Trade(PlayerId("p2"), expected,
        oathdigital.setup.TradeResource.Favor))
    val unsupported = ujson.read(muster).obj
    unsupported("command").obj("target").obj("kind") = "relic"
    assertEquals(GameHttpWire.decodeCommand(ujson.write(unsupported))
      .left.toOption.get.path, "$.command.target.kind")
    val hidden = ujson.read(muster).obj
    hidden("command").obj("outcome") = ujson.Obj("gained" -> 99)
    assertEquals(GameHttpWire.decodeCommand(ujson.write(hidden))
      .left.toOption.get.path, "$.command.outcome")
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
    val request = GameHttpWire.decodeBootstrap(json).toOption.get

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
      GameHttpWire.decodeCommand(
        """{"expectedNextSequence":0,"command":{"type":"placePawn",
          |"playerId":"p1"}}""".stripMargin
      ).left.toOption.get.path,
      "$.command.siteId"
    )
    Vector(
      ujson.Num(-1),
      ujson.Num(0.5),
      ujson.Num((GameEventWire.MaxSafeSequence + 1L).toDouble)
    ).foreach { value =>
      assertEquals(
        GameHttpWire.decodeCommand(beginRequest(value))
          .left.toOption.get.path,
        "$.expectedNextSequence"
      )
    }
    assertEquals(
      GameHttpWire.decodeCommand("{").left.toOption.get.path,
      "$"
    )
  }
}
