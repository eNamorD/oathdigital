package oathdigital.server

import oathdigital.model.{EconomyTargetRef, EdificeId}

class AuthenticatedGameHttpWireSuite extends munit.FunSuite {
  test("authenticated Recover intents and private relic choice are actor-free") {
    val begin = """{"expectedNextSequence":20,"intent":{"type":"beginRecover"}}"""
    val add = """{"expectedNextSequence":21,"intent":{"type":"addRecoverDice","decisionId":"recover-20"}}"""
    val stop = """{"expectedNextSequence":21,"intent":{"type":"stopRecover","decisionId":"recover-20"}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(begin).toOption.get.intent,
      GameIntent.BeginRecover)
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(add).toOption.get.intent,
      GameIntent.AddRecoverDice(oathdigital.model.DecisionId("recover-20")))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(stop).toOption.get.intent,
      GameIntent.StopRecover(oathdigital.model.DecisionId("recover-20")))
    val take = """{"expectedNextSequence":22,"intent":{"type":"resolveCardDecision","decisionId":"recover-20","resolution":{"kind":"take-facedown-relic","relicId":"relic:R1"}}}"""
    val resolved = AuthenticatedGameHttpWire.decodeCommand(take).toOption.get.intent
      .asInstanceOf[GameIntent.ResolveCardDecision]
    assertEquals(resolved.resolution,
      oathdigital.application.CardDecisionResolution.TakeFacedownRelic(
        oathdigital.model.RelicId("relic:R1")))
    val spoofed = ujson.read(add).obj
    spoofed("intent").obj("playerId") = "other"
    assert(AuthenticatedGameHttpWire.decodeCommand(ujson.write(spoofed)).isLeft)
  }
  test("generic authenticated card decision is actor-free") {
    val valid = """{"expectedNextSequence":2,"intent":{"type":"resolveCardDecision","decisionId":"setup-adviser-0-p2","resolution":{"kind":"starting-adviser","adviserId":"denizen:a"}}}"""
    assert(AuthenticatedGameHttpWire.decodeCommand(valid).toOption.get.intent
      .isInstanceOf[GameIntent.ResolveCardDecision])
    val impersonation = ujson.read(valid).obj
    impersonation("intent").obj("playerId") = "p1"
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(ujson.write(impersonation))
      .left.toOption.get.path, "$.intent.playerId")
  }
  test("authenticated Rest intents remain actor-free") {
    val begin = AuthenticatedGameHttpWire.decodeCommand(
      """{"expectedNextSequence":12,"intent":{"type":"beginRest"}}""")
      .toOption.get
    val finish = AuthenticatedGameHttpWire.decodeCommand(
      """{"expectedNextSequence":13,"intent":{"type":"finishRest"}}""")
      .toOption.get
    assertEquals(begin.intent, GameIntent.BeginRest)
    assertEquals(finish.intent, GameIntent.FinishRest)
    assert(AuthenticatedGameHttpWire.decodeCommand(
      """{"expectedNextSequence":12,"intent":{"type":"beginRest","playerId":"p1"}}""")
      .isLeft)
  }
  test("authenticated command intents contain no actor field") {
    val valid =
      """{"expectedNextSequence":1,"intent":{"type":"placePawn","siteId":"S1"}}"""
    assert(AuthenticatedGameHttpWire.decodeCommand(valid).isRight)

    val impersonation =
      """{"expectedNextSequence":1,"intent":{"type":"placePawn","playerId":"p2","siteId":"S1"}}"""
    assertEquals(
      AuthenticatedGameHttpWire.decodeCommand(impersonation)
        .left.toOption.get.path,
      "$.intent.playerId"
    )
  }

  test("authenticated Wake intents derive their actor server-side") {
    val wealth =
      """{"expectedNextSequence":8,"intent":{"type":"takeWealth","resource":"secret"}}"""
    val end =
      """{"expectedNextSequence":9,"intent":{"type":"endWake"}}"""
    assertEquals(
      AuthenticatedGameHttpWire.decodeCommand(wealth).toOption.get.intent,
      GameIntent.TakeWealth(oathdigital.setup.WakeResource.Secret)
    )
    assertEquals(
      AuthenticatedGameHttpWire.decodeCommand(end).toOption.get.intent,
      GameIntent.EndWake
    )
    val impersonation =
      """{"expectedNextSequence":8,"intent":{"type":"endWake","playerId":"p2"}}"""
    assert(AuthenticatedGameHttpWire.decodeCommand(impersonation).isLeft)
  }

  test("authenticated Travel intent is actor-free and exact") {
    val valid =
      """{"expectedNextSequence":10,"intent":{"type":"travel","destinationSiteId":"site:b"}}"""
    assertEquals(
      AuthenticatedGameHttpWire.decodeCommand(valid).toOption.get.intent,
      GameIntent.Travel(oathdigital.model.SiteId("site:b")))
    val impersonation =
      """{"expectedNextSequence":10,"intent":{"type":"travel","destinationSiteId":"site:b","playerId":"p2"}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(impersonation)
      .left.toOption.get.path, "$.intent.playerId")
  }

  test("authenticated Search intents are actor-free and reject hidden deck input") {
    val begin =
      """{"expectedNextSequence":10,"intent":{"type":"beginSearch","source":"world"}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(begin).toOption.get.intent,
      GameIntent.BeginSearch(oathdigital.model.SearchSource.WorldDeck))
    val impersonation = ujson.read(begin).obj
    impersonation("intent").obj("playerId") = "p2"
    assert(AuthenticatedGameHttpWire.decodeCommand(ujson.write(impersonation)).isLeft)
    val hiddenOrder = ujson.read(begin).obj
    hiddenOrder("intent").obj("drawn") = ujson.Arr("denizen:secret")
    assert(AuthenticatedGameHttpWire.decodeCommand(ujson.write(hiddenOrder)).isLeft)

    val complete =
      """{"expectedNextSequence":11,"intent":{"type":"completeSearch","decisionId":"search-10","kept":{"kind":"denizen","id":"denizen:a"},"discardedInOrder":[{"kind":"vision","id":"vision:b"}],"placement":{"kind":"discard"}}}"""
    assert(AuthenticatedGameHttpWire.decodeCommand(complete).isLeft)
  }

  test("authenticated Economy intents preserve typed targets and reject hidden input") {
    val muster =
      """{"expectedNextSequence":12,"intent":{"type":"muster","target":{"kind":"edifice","id":"E26"}}}"""
    val trade =
      """{"expectedNextSequence":13,"intent":{"type":"trade","target":{"kind":"edifice","id":"E26"},"resource":"secret"}}"""
    val target = EconomyTargetRef.Edifice(EdificeId("E26"))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(muster).toOption.get.intent,
      GameIntent.Muster(target))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(trade).toOption.get.intent,
      GameIntent.Trade(target, oathdigital.setup.TradeResource.Secret))
    val unsupported = ujson.read(muster).obj
    unsupported("intent").obj("target").obj("kind") = "relic"
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(ujson.write(unsupported))
      .left.toOption.get.path, "$.intent.target.kind")
    val hidden = ujson.read(muster).obj
    hidden("intent").obj("target").obj("side") = "ruined"
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(ujson.write(hidden))
      .left.toOption.get.path, "$.intent.target.side")
  }

  test("authenticated bootstrap accepts only visible seat configuration") {
    val valid =
      """{"expectedNextSequence":0,"participants":[{"playerId":"p1","lineageId":"l1","color":"red"}],"firstPlayer":"p1"}"""
    val decoded = AuthenticatedGameHttpWire.decodeBootstrap(valid)
      .toOption.get
    assertEquals(decoded.config.participants.map(_.playerId.value), Vector("p1"))

    Vector("userId", "actor", "worldDeckOrder", "denizenOrder", "relicOrder")
      .foreach { field =>
        val json = ujson.read(valid).obj
        json(field) = ujson.Str("not-accepted")
        assertEquals(
          AuthenticatedGameHttpWire.decodeBootstrap(ujson.write(json))
            .left.toOption.get.path,
          s"$$.$field"
        )
      }

    val withIdentity = ujson.read(valid).obj
    withIdentity("participants").arr.head.obj("userId") = ujson.Str("user-1")
    assertEquals(
      AuthenticatedGameHttpWire.decodeBootstrap(ujson.write(withIdentity))
        .left.toOption.get.path,
      "$.participants[0].userId"
    )
  }
}
