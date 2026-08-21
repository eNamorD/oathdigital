package oathdigital.server

import oathdigital.model.{Banner, CampaignBanner, CampaignRaidTarget, DecisionId, DenizenId,
  EconomyTargetRef, EdificeId, PendingProcedure, PlayerId, RelicId, SiteId}

class AuthenticatedGameHttpWireSuite extends munit.FunSuite {
  test("authenticated Challenge intents are actor-free and PF has no tie-choice intent") {
    val begin = """{"expectedNextSequence":20,"intent":{"type":"beginChallenge","banner":"peoples-favor"}}"""
    val site = """{"expectedNextSequence":21,"intent":{"type":"chooseChallengeSecretSite","decisionId":"challenge-20","siteId":"site:a"}}"""
    val complete = """{"expectedNextSequence":22,"intent":{"type":"completeChallenge","decisionId":"challenge-20","amount":3}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(begin).toOption.get.intent,
      GameIntent.BeginChallenge(Banner.PeoplesFavor))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(site).toOption.get.intent,
      GameIntent.ChooseChallengeSecretSite(DecisionId("challenge-20"), SiteId("site:a")))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(complete).toOption.get.intent,
      GameIntent.CompleteChallenge(DecisionId("challenge-20"), 3))
    assert(AuthenticatedGameHttpWire.decodeCommand(begin.replace(
      "\"banner\"", "\"playerId\":\"spoof\",\"banner\"")).isLeft)
    val removed = """{"expectedNextSequence":21,"intent":{"type":"chooseChallengeFavorBank","decisionId":"challenge-20","suit":"order"}}"""
    assert(AuthenticatedGameHttpWire.decodeCommand(removed).isLeft)
  }

  test("authenticated Campaign intents are actor-free and exclude dice outcomes") {
    val begin = """{"expectedNextSequence":30,"intent":{"type":"beginCampaignConquest","targetSiteIds":["site:a","site:b"],"attackDiceCount":3}}"""
    val sacrifice = """{"expectedNextSequence":31,"intent":{"type":"chooseCampaignSacrifice","decisionId":"campaign-30","count":1}}"""
    val plan = """{"expectedNextSequence":31,"intent":{"type":"chooseCampaignPlan","decisionId":"campaign-30","source":{"kind":"adviser","playerId":"p2","cardId":"143"}}}"""
    val finish = """{"expectedNextSequence":32,"intent":{"type":"finishCampaignPlans","decisionId":"campaign-30"}}"""
    val place = """{"expectedNextSequence":32,"intent":{"type":"placeCampaignForce","decisionId":"campaign-30","allocations":[{"siteId":"site:a","count":2},{"siteId":"site:b","count":0}]}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(begin).toOption.get.intent,
      GameIntent.BeginCampaignConquest(Vector(oathdigital.model.SiteId("site:a"),
        oathdigital.model.SiteId("site:b")), 3))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(sacrifice).toOption.get.intent,
      GameIntent.ChooseCampaignSacrifice(
        oathdigital.model.DecisionId("campaign-30"), 1))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(plan).toOption.get.intent,
      GameIntent.ChooseCampaignPlan(oathdigital.model.DecisionId("campaign-30"),
        PendingProcedure.CampaignPlanSource.Adviser(PlayerId("p2"),
          DenizenId("143"))))
    val relicPlan = plan.replace("\"kind\":\"adviser\"",
      "\"kind\":\"relic\"").replace("\"cardId\":\"143\"",
      "\"cardId\":\"R25\"")
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(relicPlan).toOption.get.intent,
      GameIntent.ChooseCampaignPlan(oathdigital.model.DecisionId("campaign-30"),
        PendingProcedure.CampaignPlanSource.Relic(PlayerId("p2"),
          RelicId("R25"))))
    val titlePlan = """{"expectedNextSequence":31,"intent":{"type":"chooseCampaignPlan","decisionId":"campaign-30","source":{"kind":"title","playerId":"p3"}}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(titlePlan).toOption.get.intent,
      GameIntent.ChooseCampaignPlan(oathdigital.model.DecisionId("campaign-30"),
        PendingProcedure.CampaignPlanSource.Title(PlayerId("p3"))))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(place).toOption.get.intent,
      GameIntent.PlaceCampaignForce(
        oathdigital.model.DecisionId("campaign-30"), Vector(
          oathdigital.model.CampaignForceAllocation(
            oathdigital.model.SiteId("site:a"), 2),
          oathdigital.model.CampaignForceAllocation(
            oathdigital.model.SiteId("site:b"), 0))))
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(finish).toOption.get.intent,
      GameIntent.FinishCampaignPlans(oathdigital.model.DecisionId("campaign-30")))
    val tampered = ujson.read(sacrifice).obj
    tampered("intent").obj("defenseDice") = ujson.Arr("doubler")
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(ujson.write(tampered))
      .left.toOption.get.path, "$.intent.defenseDice")
    val spoofed = ujson.read(begin).obj
    spoofed("intent").obj("playerId") = "other"
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(ujson.write(spoofed))
      .left.toOption.get.path, "$.intent.playerId")
  }

  test("authenticated Campaign counts require non-negative integers") {
    Vector(-1, 1.5).foreach { count =>
      val json = ujson.write(ujson.Obj("expectedNextSequence" -> 30,
        "intent" -> ujson.Obj("type" -> "beginCampaignConquest",
          "targetSiteIds" -> ujson.Arr("site:a"), "attackDiceCount" -> count)))
      assertEquals(AuthenticatedGameHttpWire.decodeCommand(json)
        .left.toOption.get.path, "$.intent.attackDiceCount")
    }
  }

  test("authenticated Raid intents preserve typed targets and relocation") {
    val raid = """{"expectedNextSequence":40,"intent":{"type":"beginCampaignRaid","targets":[{"kind":"pawn","playerId":"p2"},{"kind":"relic","playerId":"p2","relicId":"R1"},{"kind":"peoples-favor","playerId":"p2"}],"attackDiceCount":2}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(raid).toOption.get.intent,
      GameIntent.BeginCampaignRaid(Vector(
        CampaignRaidTarget.Pawn(PlayerId("p2")),
        CampaignRaidTarget.Relic(PlayerId("p2"), RelicId("R1")),
        CampaignRaidTarget.Banner(PlayerId("p2"), CampaignBanner.PeoplesFavor)), 2))
    val relocate = """{"expectedNextSequence":44,"intent":{"type":"relocateCampaignRaidPawn","decisionId":"campaign-40","destinationSiteId":"S3"}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(relocate).toOption.get.intent,
      GameIntent.RelocateCampaignRaidPawn(
        oathdigital.model.DecisionId("campaign-40"), SiteId("S3")))
    assert(AuthenticatedGameHttpWire.decodeCommand(raid.replace(
      "\"relicId\":\"R1\"", "\"relicId\":\"R1\",\"extra\":true")).isLeft)
  }

  test("authenticated Campaign placement requires exact non-negative allocations") {
    def decode(allocations: ujson.Value) = AuthenticatedGameHttpWire.decodeCommand(
      ujson.write(ujson.Obj("expectedNextSequence" -> 32,
        "intent" -> ujson.Obj("type" -> "placeCampaignForce",
          "decisionId" -> "campaign-30", "allocations" -> allocations))))
    assert(decode(ujson.Arr(ujson.Obj("siteId" -> "site:a",
      "count" -> -1))).isLeft)
    assert(decode(ujson.Arr(ujson.Obj("siteId" -> "site:a"))).isLeft)
    assert(decode(ujson.Arr(ujson.Obj("siteId" -> "site:a", "count" -> 0,
      "extra" -> true))).isLeft)
    assert(decode(ujson.Str("site:a")).isLeft)
  }

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
  test("authenticated Forge intents carry only typed assignments") {
    val begin = """{"expectedNextSequence":20,"intent":{"type":"beginForge"}}"""
    assertEquals(AuthenticatedGameHttpWire.decodeCommand(begin).toOption.get.intent,
      GameIntent.BeginForge)
    val complete = """{"expectedNextSequence":21,"intent":{"type":"completeForge","decisionId":"forge-20","assignments":[{"siteId":"site:a","denizenId":"denizen:1","resource":"favor"},{"siteId":"site:a","denizenId":"denizen:2","resource":"secret"},{"siteId":"site:a","denizenId":"denizen:3","resource":"favor"}]}}"""
    val intent = AuthenticatedGameHttpWire.decodeCommand(complete).toOption.get.intent
      .asInstanceOf[GameIntent.CompleteForge]
    assertEquals(intent.assignments.map(_.resource), Vector(
      oathdigital.model.ForgeResource.Favor,
      oathdigital.model.ForgeResource.Secret,
      oathdigital.model.ForgeResource.Favor))
    val spoofed = ujson.read(complete).obj
    spoofed("intent").obj("relicId") = "relic:spoofed"
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
