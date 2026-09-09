package oathdigital.frontend

import munit.FunSuite
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  GameIntent, MajorActionPreviewRequest, ModifierInvocation}

class HttpGameClientSuite extends FunSuite {
  test("trusted viewer identity round trips while old projections omit it") {
    val old = GameJson.decodeProjection(projectionJson(1)).toOption.get
    assertEquals(old.viewerPlayerId, None)
    val json = oathdigital.protocol.projection.GameProjectionCodec.encode(
      old.copy(viewerPlayerId = Some("blue-exile")))
    assertEquals(GameJson.decodeProjection(json).toOption.get.viewerPlayerId,
      Some("blue-exile"))
    assert(!oathdigital.protocol.projection.GameProjectionCodec.encode(old)
      .contains("viewerPlayerId"))
  }
  test("trusted client uses canonical cookie APIs and actorless bodies") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(7))),
      Right(TransportResponse(200, """{"nextSequence":7,"action":"trade","modifiers":[],"ignoredRules":[],"targets":[]}""")),
      Right(TransportResponse(200, projectionJson(8)))))
    val client = new TrustedHttpGameClient(transport)
    client.load("game /?", "seat-must-not-travel").flatMap { loaded =>
      assertEquals(loaded.toOption.get.nextSequence, 7L)
      client.preview("game /?", "seat-must-not-travel",
        MajorActionPreviewRequest(7, "trade", Map("resource" -> "favor")))
    }.flatMap { preview =>
      assertEquals(preview.toOption.get.nextSequence, 7L)
      client.submit("game /?", "seat-must-not-travel", 7, GameIntent.PlacePawn("site:a"))
    }.map { submitted =>
      assertEquals(submitted.toOption.get.nextSequence, 8L)
      assertEquals(transport.requests.map(r => r._1 -> r._2).toVector, Vector(
        "GET" -> "/games/game%20%2F%3F/api",
        "POST" -> "/games/game%20%2F%3F/api/preview",
        "POST" -> "/games/game%20%2F%3F/api/commands"))
      assertEquals(transport.requests.head._3, None)
      assertEquals(ActorlessCommandCodec.decode(transport.requests.last._3.get),
        Right(ActorlessCommandRequest(7, GameIntent.PlacePawn("site:a"))))
      assert(!transport.requests.toString.contains("seat-must-not-travel"))
    }
  }

  test("trusted conflict allows one reload without retry and bootstrap never sends") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(409, """{"error":"stale-client-position","message":"position changed"}""")),
      Right(TransportResponse(200, projectionJson(8)))))
    val client = new TrustedHttpGameClient(transport)
    client.submit("game-1", "red-exile", 7, GameIntent.PlacePawn("site:a")).flatMap {
      case Left(_: GameClientFailure.StalePosition) => client.load("game-1", "red-exile")
      case other => fail(s"expected conflict: $other")
    }.flatMap { loaded =>
      assertEquals(loaded.toOption.get.nextSequence, 8L)
      client.bootstrap("game-1", "red-exile",
        oathdigital.protocol.FirstGameBootstrapRequest(0, Vector.empty, "red-exile"))
    }.map { result =>
      assert(result.isLeft)
      assertEquals(transport.requests.map(_._1).toVector, Vector("POST", "GET"))
    }
  }

  test("production client previews and submits the same ordered modifiers") {
    val previewJson = """{"nextSequence":7,"action":"trade","modifiers":[{"sourceKey":"adviser:red-exile:denizen:35","handlerId":"h.one","description":"First"},{"sourceKey":"site-card:site:a:denizen:36","handlerId":"h.two","description":"Second"}],"ignoredRules":[],"targets":[]}"""
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, previewJson)),
      Right(TransportResponse(200, projectionJson(sequence = 8)))))
    val client = new HttpGameClient(transport)
    val ordered = Vector(ModifierInvocation("adviser", "35", None, "h.one"),
      ModifierInvocation("site-card", "36", Some("site:a"), "h.two"))
    client.preview("game-1", "red-exile", MajorActionPreviewRequest(7, "trade",
      Map("resource" -> "favor"))).flatMap { result =>
      assertEquals(result.toOption.get.modifiers.map(_.handlerId), Vector("h.one", "h.two"))
      client.submit("game-1", "red-exile", 7,
        GameIntent.Trade(oathdigital.protocol.EconomyTarget("denizen", "10"), "favor"),
        ordered)
    }.map { _ =>
      assert(transport.requests.head._2.endsWith("/preview?playerId=red-exile"))
      val submitted = ActorlessCommandCodec.decode(transport.requests(1)._3.get).toOption.get
      assertEquals(submitted.orderedModifiers, ordered)
    }
  }

  test("Vision and Conspiracy controls preserve opaque targets and pending decisions") {
    val actions = """[{"actionKind":"play-conspiracy","decisionId":"conspiracy-12","prompt":"Choose an enemy asset for Conspiracy","minimum":1,"maximum":1,"autoActivate":false,"explicitConfirm":false,"requiredTargets":[],"formation":null,"candidates":[{"target":{"kind":"player-relic","playerId":"blue-exile","relicId":"0"},"label":"Blue facedown relic","details":[]}]}]"""
    val json = projectionJson(sequence = 13, phase = "conspiracy-target",
      ready = true, completed = false, choices = false)
      .replace("\"boardTargetActions\":[]", s"\"boardTargetActions\":$actions")
    val action = GameJson.decodeProjection(json).toOption.get.boardTargetActions.head
    assertEquals(action.decisionId, Some("conspiracy-12"))
    assertEquals(action.candidates.map(_.target), Vector(
      BoardTargetRef.PlayerRelic("blue-exile", "0")))

    val reveal = GameJson.encodeCommand(13,
      GameCommand.RevealVision("red-exile", "vision-faith"))
    assert(reveal.contains("\"type\":\"revealVision\""))
    assert(reveal.contains("\"visionId\":\"vision-faith\""))
    val relic = GameJson.encodeCommand(13, GameCommand.PlayConspiracy(
      "red-exile", Some(ConspiracyTarget.RelicSlot("blue-exile", 1))))
    assert(relic.contains("\"kind\":\"relic-slot\""))
    assert(relic.contains("\"slot\":1"))
    assert(!relic.contains("relicId"))
    val banner = GameJson.encodeCommand(13, GameCommand.PlayConspiracy(
      "red-exile", Some(ConspiracyTarget.Banner(
        "blue-exile", "darkest-secret"))))
    assert(banner.contains("\"banner\":\"darkest-secret\""))
    val noTarget = GameJson.encodeCommand(13,
      GameCommand.PlayConspiracy("red-exile", None))
    assert(noTarget.contains("\"target\":null"))
  }

  test("Negotiation projection decodes redacted ledger and encodes authored terms") {
    val card = """{"cardId":"R1","cardKind":"relic","name":"Old Crown","suit":null,"restrictions":null,"rulesText":null,"orientation":"face-down","side":null,"favor":0,"secrets":0,"relicValue":2,"defense":1,"hidden":false}"""
    val deal = s"""{"decisionId":"negotiation-50","actorPlayerId":"red-exile","siteId":"site:a","participantPlayerIds":["red-exile","blue-exile"],"acceptedPlayerIds":["blue-exile"],"transfers":[{"authorPlayerId":"red-exile","recipientPlayerId":"blue-exile","favor":2,"relicCount":1,"relics":[$card]}],"disclosures":[{"authorPlayerId":"blue-exile","recipientPlayerId":"red-exile","kind":"adviser","card":null}],"editableFavor":4,"editableRelics":[$card],"editableAdvisers":[],"editableSiteRelics":[]}"""
    val json = projectionJson(sequence = 51, phase = "act-action-selection",
      ready = true, completed = false, choices = false).replace(
      "\"pendingCardDecision\":null",
      s"\"pendingCardDecision\":null,\"negotiation\":$deal")
    val decoded = GameJson.decodeProjection(json).toOption.get.negotiation.get
    assertEquals(decoded.acceptedPlayerIds, Vector("blue-exile"))
    assertEquals(decoded.disclosures.head.card, None)
    val terms = NegotiationTermsInput(Vector(NegotiationTransferInput(
      "blue-exile", 2, Vector("R1"))), Vector.empty)
    val encoded = GameJson.encodeCommand(51, GameCommand.ReplaceNegotiationTerms(
      "red-exile", decoded.decisionId, terms))
    assert(encoded.contains("replaceNegotiationTerms"))
    assert(encoded.contains("\"relicIds\":[\"R1\"]"))
    val waiting = projectionJson(sequence = 51, phase = "act-action-selection",
      ready = true, completed = false, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null,\"negotiationWaiting\":true")
    assert(GameJson.decodeProjection(waiting).toOption.get.negotiationWaiting)
    assertEquals(GameJson.decodeProjection(waiting).toOption.get.negotiation, None)
  }
  test("minor actions decode private state and encode typed controls") {
    val card = """{"cardId":"42","cardKind":"denizen","name":"Scout","suit":"nomad","restrictions":null,"rulesText":null,"orientation":"face-down","side":null,"favor":0,"secrets":0,"relicValue":null,"defense":null,"hidden":false}"""
    val relic = """{"cardId":"R1","cardKind":"relic","name":"Old Crown","suit":null,"restrictions":null,"rulesText":null,"orientation":"face-down","side":null,"favor":0,"secrets":0,"relicValue":2,"defense":1,"hidden":false}"""
    val minor = s"""{"advisers":[{"card":$card,"placements":[{"kind":"play-adviser","orientation":null,"replacementRequired":false,"replacementTargets":[]},{"kind":"discard","orientation":null,"replacementRequired":false,"replacementTargets":[]}]}],"canPeekSiteRelics":true,"facedownRelics":[$relic],"siteId":"site:a","maxBoardToSite":3,"maxSiteToBoard":2}"""
    val json = projectionJson(sequence = 40, phase = "act-action-selection",
      ready = true, completed = true, choices = false).replace(
      "\"pendingCardDecision\":null",
      s"\"pendingCardDecision\":null,\"minorActions\":$minor")
    val decoded = GameJson.decodeProjection(json).toOption.get
    assertEquals(decoded.minorActions.map(_.maxSiteToBoard), Some(2))
    val adviser = decoded.minorActions.get.advisers.head.card
    assert(GameJson.encodeCommand(40,
      oathdigital.protocol.GameIntent.ResolveFacedownAdviser(
        oathdigital.protocol.WorldCard(adviser.cardKind, adviser.cardId), None))
      .contains("resolveFacedownAdviser"))
    assert(GameJson.encodeCommand(40,
      oathdigital.protocol.GameIntent.ResolveFacedownAdviser(
        oathdigital.protocol.WorldCard(adviser.cardKind, adviser.cardId),
        Some(oathdigital.protocol.Placement("adviser-face-up", None))))
      .contains("adviser-face-up"))
    assert(GameJson.encodeCommand(40, GameCommand.PeekSiteRelics(
      "red-exile")).contains("peekSiteRelics"))
    assert(GameJson.encodeCommand(40, GameCommand.MoveWarbands(
      "red-exile", toSite = false, 2)).contains("\"amount\":2"))
  }

  test("Challenge projection decodes owner choices and commands omit PF tie controls") {
    val json = projectionJson(sequence = 21, phase = "challenge-decision",
      ready = true, completed = true, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null,\"banners\":[{" +
        "\"banner\":\"peoples-favor\",\"face\":\"mob\"," +
        "\"holderPlayerId\":null,\"resources\":2}],\"challenge\":{" +
        "\"decisionId\":\"challenge-20\",\"actorPlayerId\":\"red-exile\"," +
        "\"banner\":\"peoples-favor\",\"priorHolderPlayerId\":null," +
        "\"priorResources\":2,\"legalSecretSiteIds\":[]," +
        "\"minimumPlacement\":3,\"maximumPlacement\":5}")
    val decoded = GameJson.decodeProjection(json).toOption.get
    assertEquals(decoded.banners.head,
      BannerState("peoples-favor", "mob", None, 2))
    assertEquals(decoded.challenge, Some(ChallengeState("challenge-20",
      "red-exile", "peoples-favor", None, 2, Vector.empty, 3, 5)))
    assert(GameJson.encodeCommand(20, GameCommand.BeginChallenge(
      "red-exile", "peoples-favor")).contains("\"type\":\"beginChallenge\""))
    assert(GameJson.encodeCommand(21, GameCommand.CompleteChallenge(
      "red-exile", "challenge-20", 3)).contains("\"amount\":3"))
    assert(!GameJson.encodeCommand(21, GameCommand.CompleteChallenge(
      "red-exile", "challenge-20", 3)).contains("FavorBank"))
  }

  test("projection decodes scoped Oathkeeper and Usurper victory status") {
    val json = projectionJson(sequence = 30, phase = "game-over",
      ready = true, completed = true, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null,\"oathkeeper\":{" +
        "\"goal\":\"supremacy\",\"holderPlayerId\":\"red-exile\"," +
        "\"side\":\"usurper\",\"usurperLimited\":false," +
        "\"winnerPlayerId\":\"red-exile\",\"winnerVictoryKind\":\"usurper\"}")
    val decoded = GameJson.decodeProjection(json).toOption.get
    assertEquals(decoded.oathkeeper, Some(OathkeeperStatus("supremacy",
      Some("red-exile"), "usurper", usurperLimited = false,
      Some("red-exile"), Some("usurper"))))
  }
  test("scoped Oathkeeper recipient decision decodes and encodes its choice") {
    val json = projectionJson(sequence = 31, phase = "oathkeeper-recipient",
      ready = true, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null,\"oathkeeperRecipient\":{" +
        "\"decisionId\":\"oath-31\",\"actorPlayerId\":\"red-exile\"," +
        "\"candidatePlayerIds\":[\"blue-exile\",\"yellow-exile\"]}")
    assertEquals(GameJson.decodeProjection(json).toOption.get.oathkeeperRecipient,
      Some(OathkeeperRecipientDecision("oath-31", "red-exile",
        Vector("blue-exile", "yellow-exile"))))
    val encoded = GameJson.encodeCommand(31,
      GameCommand.ChooseOathkeeperRecipient("red-exile", "oath-31",
        "blue-exile"))
    assert(encoded.contains("\"type\":\"chooseOathkeeperRecipient\""))
    assert(encoded.contains("\"recipientPlayerId\":\"blue-exile\""))
  }
  test("Recover commands encode decisions and private relic resolution") {
    assert(GameJson.encodeCommand(20, GameCommand.BeginRecover("red"))
      .contains("\"type\":\"beginRecover\""))
    assert(GameJson.encodeCommand(21, GameCommand.AddRecoverDice("red", "recover-20"))
      .contains("\"decisionId\":\"recover-20\""))
    assert(GameJson.encodeCommand(21, GameCommand.StopRecover("red", "recover-20"))
      .contains("\"type\":\"stopRecover\""))
    val take = GameJson.encodeCommand(22, GameCommand.ResolveCardDecision(
      "red", "recover-20", DecisionResolution.TakeFacedownRelic("relic:R1")))
    assert(take.contains("\"kind\":\"take-facedown-relic\""))
    assert(take.contains("\"relicId\":\"relic:R1\""))
  }
  test("Forge commands encode stable assignment targets without relic identity") {
    val target = ForgeTarget("site:a", "denizen:1", "One")
    assert(GameJson.encodeCommand(20, GameCommand.BeginForge("red"))
      .contains("\"type\":\"beginForge\""))
    val completed = GameJson.encodeCommand(21, GameCommand.CompleteForge(
      "red", "forge-20", Vector(target -> "favor")))
    assert(completed.contains("\"denizenId\":\"denizen:1\""))
    assert(!completed.contains("relicId"))
  }
  test("Rest commands encode current sequence without an actor") {
    val begin = GameJson.encodeCommand(12L, GameCommand.BeginRest("red-exile"))
    val finish = GameJson.encodeCommand(13L, GameCommand.FinishRest("red-exile"))
    assert(begin.contains("\"type\":\"beginRest\""))
    assert(begin.contains("\"expectedNextSequence\":12"))
    assert(finish.contains("\"type\":\"finishRest\""))
    assert(!finish.contains("\"playerId\""))
    assert(finish.contains("\"intent\""))
    val resolve = GameJson.encodeCommand(14L, GameCommand.ResolveRestPower(
      "blue-exile", "rest-13", Vector(oathdigital.protocol.RestFavorAllocation(
        oathdigital.protocol.RestFavorSource("relic-slot", "site:a", "0"), 2)),
      "hearth"))
    assert(resolve.contains("\"type\":\"resolveRestPower\""))
    assert(resolve.contains("\"sourceId\":\"0\""))
    assert(!resolve.contains("blue-exile"))
    assert(GameJson.encodeCommand(15L, GameCommand.DeclineRestPower(
      "blue-exile", "rest-13")).contains("\"type\":\"declineRestPower\""))
  }
  private val bootstrap = oathdigital.protocol.FirstGameBootstrapRequest(
    0,
    Vector(
      oathdigital.protocol.BootstrapParticipantRequest("red-exile", "red-lineage", "red"),
      oathdigital.protocol.BootstrapParticipantRequest("blue-exile", "blue-lineage", "blue"),
      oathdigital.protocol.BootstrapParticipantRequest("yellow-exile", "yellow-lineage", "yellow")
    ),
    "red-exile"
  )

  test("bootstrap uses same-origin route and server nextSequence") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 1)))
    ))
    val client = new HttpGameClient(transport)

    client.bootstrap("new game", "red-exile", bootstrap).map { result =>
      assertEquals(result.toOption.get.nextSequence, 1L)
      assertEquals(
        transport.requests.head._2,
        "/api/dev/first-games/new%20game/bootstrap?playerId=red-exile"
      )
      assert(transport.requests.head._3.exists(
        _.contains("\"expectedNextSequence\":0")
      ))
    }
  }

  test("pawn and adviser commands preserve explicit sequence and opaque IDs") {
    val opaqueSite = "site:ancient-city"
    val opaqueAdviser = "denizen:0612"
    val target = BoardTargetRef.Site(opaqueSite)
    val setupAction = BoardTargetAction(
      "place-pawn",
      "Choose a starting site",
      1,
      1,
      autoActivate = true,
      Vector(BoardTargetCandidate(target, "Ancient City"))
    )
    val setupCommand = ServerUiSupport.commandForSelection(
      setupAction,
      Vector(target),
      "red-exile"
    ).get
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(
        sequence = 2,
        phase = "awaiting-adviser",
        siteId = opaqueSite,
        adviserId = opaqueAdviser
      ))),
      Right(TransportResponse(200, projectionJson(
        sequence = 3,
        phase = "awaiting-pawn",
        siteId = opaqueSite,
        choices = false
      )))
    ))
    val client = new HttpGameClient(transport)

    client
      .submit(
        "game-1",
        "red-exile",
        1L,
        setupCommand
      )
      .flatMap { pawn =>
        val adviser = pawn.toOption.get.pendingCardDecision.get.cards.head
        assertEquals(adviser.cardId, opaqueAdviser)
        client.submit(
          "game-1",
          "red-exile",
          pawn.toOption.get.nextSequence,
          GameCommand.ResolveCardDecision("red-exile",
            pawn.toOption.get.pendingCardDecision.get.decisionId,
            DecisionResolution.StartingAdviser(adviser.cardId))
        )
      }
      .map { _ =>
        val setupBody = transport.requests.head._3.get
        assertEquals(ujson.read(setupBody).obj.keySet,
          Set("expectedNextSequence", "intent"))
        assertEquals(ActorlessCommandCodec.decode(setupBody),
          Right(ActorlessCommandRequest(1L, GameIntent.PlacePawn(opaqueSite))))
        assert(transport.requests(1)._3.exists(_.contains(opaqueAdviser)))
        assert(transport.requests(1)._3.exists(
          _.contains("\"expectedNextSequence\":2")
        ))
      }
  }

  test("Wake commands and action-selection projection are deterministic") {
    val wealth = GameJson.encodeCommand(
      8L,
      GameCommand.TakeWealth("red-exile", "favor")
    )
    val end = GameJson.encodeCommand(
      9L,
      GameCommand.EndWake("red-exile")
    )
    assert(wealth.contains("\"type\":\"takeWealth\""))
    assert(wealth.contains("\"resource\":\"favor\""))
    assert(end.contains("\"type\":\"endWake\""))

    val json = projectionJson(
      sequence = 10,
      phase = "act-action-selection",
      ready = true,
      completed = true,
      choices = false
    ).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null," +
        "\"activePlayerResources\":{" +
        "\"favor\":2,\"faceUpSecrets\":1," +
        "\"faceDownSecrets\":0,\"committedSecrets\":1," +
        "\"totalSecrets\":2,\"supply\":7}," +
        "\"currentSiteResources\":{" +
        "\"siteId\":\"site:001\",\"favor\":0,\"secrets\":1}," +
        "\"actionSelectionOpen\":true," +
        "\"actionFamilies\":[\"Search\",\"Travel\"]"
    )
    val projection = GameJson.decodeProjection(json).toOption.get
    assert(projection.actionSelectionOpen)
    assertEquals(projection.actionFamilies, Vector("Search", "Travel"))
    assertEquals(projection.activePlayerResources.map(_.supply), Some(7))
  }

  test("Travel encodes destination and decodes authoritative legal costs") {
    val command = GameJson.encodeCommand(10L,
      GameCommand.Travel("red-exile", "site:b"))
    assert(command.contains("\"type\":\"travel\""))
    assert(command.contains("\"destinationSiteId\":\"site:b\""))
    val json = projectionJson(sequence = 10, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null,\"legalTravelDestinations\":[" +
        "{\"siteId\":\"site:b\",\"supplyCost\":2}]"
    )
    assertEquals(GameJson.decodeProjection(json).toOption.get
      .legalTravelDestinations,
      Vector(LegalTravelDestination("site:b", 2)))
  }

  test("Campaign encodes and decodes the authoritative target set") {
    val encoded = GameJson.encodeCommand(17,
      GameCommand.CampaignConquest("red-exile", "site:b", 3))
    assert(encoded.contains("\"expectedNextSequence\":17"))
    assert(encoded.contains("\"type\":\"beginCampaignConquest\""))
    assert(!ujson.read(encoded)("intent").obj.contains("playerId"))
    assert(encoded.contains("\"targetSiteIds\":[\"site:b\"]"))
    assert(encoded.contains("\"attackDiceCount\":3"))
    assert(!encoded.contains("\"attackDice\":"))
    assert(!encoded.contains("defenseDice"))

    val action = """[{"actionKind":"campaign-conquest","prompt":"Conquer Site B","minimum":1,"maximum":1,"autoActivate":false,"explicitConfirm":false,"requiredTargets":[{"kind":"site","siteId":"site:b"}],"formation":{"minimumForce":0,"maximumForce":3,"availableWarbands":3,"supplyCost":2},"candidates":[{"target":{"kind":"site","siteId":"site:b"},"label":"Site B","details":["2 Supply","Choose 0 to 3 board warbands"]}]}]"""
    val projected = projectionJson(sequence = 17, choices = false)
      .replace("\"boardTargetActions\":[]",
        s"\"boardTargetActions\":$action")
    val projectionResult = GameJson.decodeProjection(projected)
    assert(projectionResult.isRight, projectionResult.left.toOption.toString)
    val decoded = projectionResult.toOption.get.boardTargetActions.head
    assertEquals(decoded.actionKind, "campaign-conquest")
    assertEquals(decoded.minimum -> decoded.maximum, 1 -> 1)
    assertEquals(decoded.candidates.map(_.target),
      Vector(BoardTargetRef.Site("site:b")))
    assertEquals(decoded.formation, Some(BoardTargetFormation(0, 3, 3, 2)))

    val pending = """{"decisionId":"campaign-17","targetSiteIds":["site:b"],"defenderKind":"player","defenderPlayerId":"blue-exile","defenderForce":3,"defenseDiceCount":2,"placementTargets":[{"siteId":"site:b","label":"Site B"}],"force":3,"plansFinished":true,"planChoices":[],"selectedPlans":[],"attackDice":["two-swords","skull"],"attack":2,"skullLosses":1,"maxSacrifice":2,"sacrificed":null,"defenseDice":[],"defense":null,"victorious":null,"maxPlacement":0}"""
    val pendingJson = projectionJson(sequence = 18, choices = false)
      .replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaign\":$pending")
    val pendingResult = GameJson.decodeProjection(pendingJson)
    assert(pendingResult.isRight, pendingResult.left.toOption.toString)
    assertEquals(pendingResult.toOption.get.campaign,
      Some(CampaignState("campaign-17", "site:b", 3,
        plansFinished = true, Vector.empty, Vector.empty,
        Vector("two-swords", "skull"), 2, 1, 2, None, Vector.empty,
        None, None, 0).copy(placementTargets =
          Vector(CampaignPlacementTarget("site:b", "Site B")),
          defenderKind = "player", defenderPlayerId = Some("blue-exile"),
          defenderForce = 3, defenseDiceCount = 2)))

    val sacrifice = GameJson.encodeCommand(18,
      GameCommand.ChooseCampaignSacrifice("red-exile", "campaign-17", 1))
    assert(sacrifice.contains("\"type\":\"chooseCampaignSacrifice\""))
    assert(sacrifice.contains("\"decisionId\":\"campaign-17\""))
    assert(sacrifice.contains("\"count\":1"))
    val placement = GameJson.encodeCommand(20,
      GameCommand.PlaceCampaignForce("red-exile", "campaign-17",
        Vector(CampaignPlacement("site:a", 0))))
    assert(placement.contains("\"type\":\"placeCampaignForce\""))
    assert(placement.contains("\"count\":0"))

    val planPending = """{"decisionId":"campaign-17","targetSiteIds":["site:b"],"placementTargets":[{"siteId":"site:b","label":"Site B"}],"force":3,"plansFinished":false,"planChoices":[{"kind":"adviser","sourceKey":"adviser:red-exile:denizen:143","playerId":"red-exile","siteId":null,"cardId":"143","label":"Outriders","handlerId":"denizen.outriders","favorCost":0,"secretCost":0,"mechanicalResult":"Ignore all attack-roll skull losses"}],"selectedPlans":[],"attackDice":[],"attack":0,"skullLosses":0,"maxSacrifice":3,"sacrificed":null,"defenseDice":[],"defense":null,"victorious":null,"maxPlacement":3}"""
    val planResult = GameJson.decodeProjection(projectionJson(sequence = 18,
      choices = false).replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaign\":$planPending"))
    assert(planResult.isRight, planResult.left.toOption.toString)
    val planState = planResult.toOption.get.campaign.get
    assertEquals(planState.planChoices.map(_.kind), Vector("adviser"))
    val adviserJson = """{"kind":"adviser","sourceKey":"adviser:red-exile:denizen:143","playerId":"red-exile","siteId":null,"cardId":"143","label":"Outriders","handlerId":"denizen.outriders","favorCost":0,"secretCost":0,"mechanicalResult":"Ignore all attack-roll skull losses"}"""
    val duplicateSelected = planPending.replace("\"selectedPlans\":[]",
      s"\"selectedPlans\":[$adviserJson,$adviserJson]")
    assert(GameJson.decodeProjection(projectionJson(sequence = 18,
      choices = false).replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaign\":$duplicateSelected")).isLeft)
    val selectedAndAvailable = planPending.replace("\"selectedPlans\":[]",
      s"\"selectedPlans\":[$adviserJson]")
    assert(GameJson.decodeProjection(projectionJson(sequence = 18,
      choices = false).replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaign\":$selectedAndAvailable")).isLeft)
    val choosePlan = GameJson.encodeCommand(18, GameCommand.ChooseCampaignPlan(
      "red-exile", "campaign-17", planState.planChoices.head))
    assert(choosePlan.contains("\"type\":\"chooseCampaignPlan\""))
    assert(choosePlan.contains("\"kind\":\"adviser\""))
    assert(choosePlan.contains("\"cardId\":\"143\""))
    assert(!choosePlan.contains("denizen.outriders"))
    val finishPlans = GameJson.encodeCommand(18, GameCommand.FinishCampaignPlans(
      "red-exile", "campaign-17"))
    assert(finishPlans.contains("\"type\":\"finishCampaignPlans\""))
    def projectionWithPlan(value: String) = projectionJson(sequence = 18,
      choices = false).replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaign\":$value")
    val brassPending = planPending.replace(
      "{\"kind\":\"adviser\",\"sourceKey\":\"adviser:red-exile:denizen:143\",\"playerId\":\"red-exile\",\"siteId\":null,\"cardId\":\"143\",\"label\":\"Outriders\",\"handlerId\":\"denizen.outriders\",\"favorCost\":0,\"secretCost\":0,\"mechanicalResult\":\"Ignore all attack-roll skull losses\"}",
      "{\"kind\":\"relic\",\"sourceKey\":\"relic:red-exile:R25\",\"playerId\":\"red-exile\",\"siteId\":null,\"cardId\":\"R25\",\"label\":\"Brass Army\",\"handlerId\":\"relic.brass-army.campaign\",\"favorCost\":0,\"secretCost\":1,\"mechanicalResult\":\"Add 4 attack dice\"}")
    val brassChoice = GameJson.decodeProjection(projectionWithPlan(brassPending))
      .toOption.get.campaign.get.planChoices.head
    val brassCommand = GameJson.encodeCommand(18, GameCommand.ChooseCampaignPlan(
      "red-exile", "campaign-17", brassChoice))
    assert(brassCommand.contains("\"kind\":\"relic\""))
    assert(brassCommand.contains("\"cardId\":\"R25\""))
    assert(!brassCommand.contains("relic.brass-army.campaign"))
    val titlePending = planPending
      .replace("\"force\":3", "\"force\":3,\"planSide\":\"defender\",\"decisionOwnerPlayerId\":\"blue-exile\"")
      .replace(adviserJson,
        """{"kind":"title","sourceKey":"title:blue-exile","playerId":"blue-exile","siteId":null,"cardId":null,"label":"Oathkeeper","handlerId":"title.oathkeeper-defense","favorCost":0,"secretCost":0,"mechanicalResult":"Add 1 defense die"}""")
    val titleState = GameJson.decodeProjection(projectionWithPlan(titlePending))
      .toOption.get.campaign.get
    assertEquals(titleState.planSide -> titleState.decisionOwnerPlayerId,
      "defender" -> Some("blue-exile"))
    val titleCommand = GameJson.encodeCommand(18, GameCommand.ChooseCampaignPlan(
      "blue-exile", "campaign-17", titleState.planChoices.head))
    assert(titleCommand.contains("\"kind\":\"title\""))
    assert(!titleCommand.contains("title.oathkeeper-defense"))

    val unknownKind = planPending.replace("\"kind\":\"adviser\"",
      "\"kind\":\"future-plan\"")
    assert(GameJson.decodeProjection(projectionWithPlan(unknownKind))
      .left.toOption.get.isInstanceOf[GameClientFailure.DecodeFailure])
    val missingAdviserCard = planPending.replace(
      "\"cardId\":\"143\"", "\"cardId\":null")
    assert(GameJson.decodeProjection(projectionWithPlan(missingAdviserCard))
      .left.toOption.get.isInstanceOf[GameClientFailure.DecodeFailure])
  }

  test("Raid targets and owner relocation use typed wire shapes") {
    val targets = Vector[BoardTargetRef](BoardTargetRef.PlayerPawn("blue-exile"),
      BoardTargetRef.PlayerRelic("blue-exile", "R03"),
      BoardTargetRef.PlayerBanner("blue-exile", "peoples-favor"))
    val encoded = GameJson.encodeCommand(24,
      GameCommand.CampaignRaid("red-exile", targets, 2))
    assert(encoded.contains("\"type\":\"beginCampaignRaid\""))
    assert(encoded.contains("\"kind\":\"pawn\""))
    assert(encoded.contains("\"kind\":\"relic\""))
    assert(encoded.contains("\"relicId\":\"R03\""))
    assert(encoded.contains("\"kind\":\"peoples-favor\""))
    assert(encoded.contains("\"attackDiceCount\":2"))

    val relocation = GameJson.encodeCommand(25,
      GameCommand.RelocateCampaignRaidPawn("red-exile", "raid-24", "site:c"))
    assert(relocation.contains("\"type\":\"relocateCampaignRaidPawn\""))
    assert(relocation.contains("\"decisionId\":\"raid-24\""))
    assert(relocation.contains("\"destinationSiteId\":\"site:c\""))

    val action = """[{"actionKind":"campaign-raid","prompt":"Raid Blue","minimum":1,"maximum":3,"autoActivate":false,"explicitConfirm":false,"requiredTargets":[{"kind":"player-pawn","playerId":"blue-exile"}],"formation":{"minimumForce":0,"maximumForce":2,"availableWarbands":2,"supplyCost":2},"candidates":[{"target":{"kind":"player-pawn","playerId":"blue-exile"},"label":"Blue pawn","details":[]},{"target":{"kind":"player-relic","playerId":"blue-exile","relicId":"R03"},"label":"Relic","details":[]},{"target":{"kind":"player-banner","playerId":"blue-exile","banner":"peoples-favor"},"label":"People's Favor","details":[]}]}]"""
    val relocationProjection = """{"decisionId":"raid-24","actorPlayerId":"red-exile","defenderPlayerId":"blue-exile","originSiteId":"site:b","legalSiteIds":["site:a","site:c"]}"""
    val json = projectionJson(sequence = 24, choices = false)
      .replace("\"boardTargetActions\":[]", s"\"boardTargetActions\":$action")
      .replace("\"pendingCardDecision\":null",
        s"\"pendingCardDecision\":null,\"campaignRaidRelocation\":$relocationProjection")
    val decoded = GameJson.decodeProjection(json)
    assert(decoded.isRight, decoded.left.toOption.toString)
    assertEquals(decoded.toOption.get.boardTargetActions.head.candidates.map(_.target),
      targets)
    assertEquals(decoded.toOption.get.campaignRaidRelocation,
      Some(CampaignRaidRelocation("raid-24", "red-exile", "blue-exile",
        "site:b", Vector("site:a", "site:c"))))

    val hidden = GameJson.decodeProjection(projectionJson(sequence = 24,
      choices = false)).toOption.get
    assertEquals(hidden.campaignRaidRelocation, None)
  }

  test("typed board-target actions decode sites cards advisers relics and reject malformed refs") {
    val actions = """[{"actionKind":"campaign-hooks","prompt":"Choose targets","minimum":1,"maximum":4,"autoActivate":false,"explicitConfirm":false,"requiredTargets":[],"candidates":[{"target":{"kind":"site","siteId":"site:b"},"label":"Site B","details":["2 Supply"]},{"target":{"kind":"site-card","siteId":"site:b","cardKind":"edifice","cardId":"E26"},"label":"Spring","details":["+2 warbands"]},{"target":{"kind":"player-adviser","playerId":"red-exile","cardId":"D1"},"label":"Adviser","details":[]},{"target":{"kind":"player-relic","playerId":"red-exile","relicId":"R1"},"label":"Relic","details":[]}]}]"""
    val json = projectionJson(sequence = 10, choices = false)
      .replace("\"boardTargetActions\":[]",
        s"\"boardTargetActions\":$actions")
    val decoded = GameJson.decodeProjection(json).toOption.get
      .boardTargetActions.head
    assertEquals(decoded.minimum -> decoded.maximum, 1 -> 4)
    assertEquals(decoded.candidates.map(_.target), Vector(
      BoardTargetRef.Site("site:b"),
      BoardTargetRef.SiteCard("site:b", "edifice", "E26"),
      BoardTargetRef.PlayerAdviser("red-exile", "D1"),
      BoardTargetRef.PlayerRelic("red-exile", "R1")))
    assert(GameJson.decodeProjection(json.replace("player-relic", "unknown")).isLeft)
    assert(GameJson.decodeProjection(json.replace("\"maximum\":4", "\"maximum\":5")).isLeft)
    assert(GameJson.decodeProjection(json.replace("\"cardKind\":\"edifice\"",
      "\"cardKind\":\"relic\"")).isLeft)
  }

  test("Search encodes only source and decisions and decodes private controls") {
    val begin = GameJson.encodeCommand(10L,
      GameCommand.BeginSearch("red-exile", "world", None))
    assert(begin.contains("\"type\":\"beginSearch\""))
    assert(!begin.contains("drawn"))
    val json = projectionJson(sequence = 11, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":{" +
        "\"decisionId\":\"search-10\",\"kind\":\"search\"," +
        "\"actorPlayerId\":\"red-exile\",\"prompt\":\"Resolve Search\"," +
        "\"instructions\":[],\"cards\":[" + cardJson("denizen:a", "denizen", "A") + "]," +
        "\"keepMinimum\":1,\"keepMaximum\":1,\"orderingRequired\":true," +
        "\"resolutionsByCard\":{\"denizen:a\":[{\"kind\":\"discard\"," +
        "\"orientation\":null,\"replacementRequired\":false," +
        "\"replacementTargets\":[]}]}} ," +
        "\"legalSearchSources\":[{\"kind\":\"world\",\"region\":null," +
        "\"supplyCost\":2}]" )
    val projection = GameJson.decodeProjection(json).toOption.get
    assertEquals(projection.legalSearchSources,
      Vector(LegalSearchSource("world", None, 2)))
    assertEquals(projection.pendingCardDecision.map(_.cards.map(_.cardId)),
      Some(Vector("denizen:a")))
    assertEquals(projection.pendingCardDecision.get
      .resolutionsByCard("denizen:a").head.replacementRequired, false)
    val complete = GameJson.encodeCommand(11L,
      GameCommand.ResolveCardDecision("red-exile", "search-10",
        DecisionResolution.Search(CardDetails("denizen:a", "denizen", "A"),
          Vector.empty, "discard", None, None)))
    assert(complete.contains("\"decisionId\":\"search-10\""))
  }

  test("Economy encodes typed intents and decodes authoritative yields") {
    val muster = GameJson.encodeCommand(12L,
      GameCommand.Muster("red-exile", EconomyTarget("edifice", "E26")))
    val trade = GameJson.encodeCommand(13L,
      GameCommand.Trade("red-exile", EconomyTarget("edifice", "E26"), "secret"))
    assert(muster.contains("\"type\":\"muster\""))
    assert(muster.contains("\"target\":{\"kind\":\"edifice\",\"id\":\"E26\"}"))
    assert(trade.contains("\"resource\":\"secret\""))
    val json = projectionJson(sequence = 12, choices = false).replace(
      "\"pendingCardDecision\":null",
      "\"pendingCardDecision\":null," +
        "\"legalMusters\":[{\"target\":{\"kind\":\"edifice\",\"id\":\"E26\"}," +
        "\"label\":\"Ruined Hallowed Spring\",\"suit\":\"order\",\"supplyCost\":1,\"warbandsGained\":2}]," +
        "\"legalTrades\":[{\"target\":{\"kind\":\"edifice\",\"id\":\"E26\"}," +
        "\"label\":\"Ruined Hallowed Spring\",\"suit\":\"order\",\"resource\":\"secret\"," +
        "\"supplyCost\":1,\"gained\":1}]"
    )
    val projection = GameJson.decodeProjection(json).toOption.get
    assertEquals(projection.legalMusters.head.warbandsGained, 2)
    assertEquals(projection.legalMusters.head.label, "Ruined Hallowed Spring")
    assertEquals(projection.legalTrades.head,
      LegalTrade(EconomyTarget("edifice", "E26"), "Ruined Hallowed Spring",
        "order", "secret", 1, 1))
    val invalid = json.replace("\"kind\":\"edifice\"", "\"kind\":\"relic\"")
    assert(GameJson.decodeProjection(invalid).isLeft)
  }

  test("site detail decoder preserves populated and empty site projections") {
    val projection = GameJson.decodeProjection(
      projectionJson(sequence = 2)
    ).toOption.get
    val populated = projection.world.head.sites.head
    val empty = projection.world.head.sites(1)

    assertEquals(populated.looseFavor, 2)
    assertEquals(populated.looseSecrets, 1)
    assertEquals(populated.denizenCapacity, 3)
    assertEquals(populated.relicCapacity, 2)
    assertEquals(
      populated.denizens,
      Vector(
        GameSiteCard("denizen:z", "Zed"),
        GameSiteCard("denizen:a", "Able")
      )
    )
    assertEquals(populated.relics, GameSiteRelics(2))
    assertEquals(populated.forces, Some(SiteForces("exile", 2, "player",
      Some("red-exile"), "Red Warbands", "red")))
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(empty.forces, None)

    val invalidCount = projectionJson(sequence = 2)
      .replace("\"count\":2", "\"count\":0")
    assert(GameJson.decodeProjection(invalidCount).isLeft)
    val invalidRuler = projectionJson(sequence = 2)
      .replace("\"rulerKind\":\"player\"", "\"rulerKind\":\"mystery\"")
    assert(GameJson.decodeProjection(invalidRuler).isLeft)
    val mismatchedColor = projectionJson(sequence = 2)
      .replace("\"colorToken\":\"red\"", "\"colorToken\":\"bandit\"")
    assert(GameJson.decodeProjection(mismatchedColor).isLeft)
  }

  test("409 is surfaced and caller refreshes without command retry") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(
        409,
        """{"error":"stale-client-position","message":"expected 1 actual 2"}"""
      )),
      Right(TransportResponse(200, projectionJson(sequence = 2)))
    ))
    val client = new HttpGameClient(transport)

    client
      .submit(
        "game-1",
        "red-exile",
        1L,
        GameCommand.PlacePawn("red-exile", "site:001")
      )
      .flatMap { conflict =>
        assert(conflict.left.toOption.exists(
          _.isInstanceOf[GameClientFailure.StalePosition]
        ))
        client.load("game-1", "red-exile")
      }
      .map { refreshed =>
        assertEquals(refreshed.toOption.get.nextSequence, 2L)
        assertEquals(
          transport.requests.map(_._1).toVector,
          Vector("POST", "GET")
        )
      }
  }

  test("existing game reload uses selected identity without creating history") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 6)))
    ))
    val client = new HttpGameClient(transport)

    client.load("persisted-game", "blue-exile").map { loaded =>
      assertEquals(loaded.toOption.get.nextSequence, 6L)
      assertEquals(transport.requests.toVector, Vector((
        "GET",
        "/api/dev/first-games/persisted-game?playerId=blue-exile",
        None
      )))
    }
  }

  test("transient disconnect reconnects by GET at authoritative sequence") {
    val transport = new StubTransport(Vector(
      Left(GameClientFailure.NetworkFailure("offline")),
      Right(TransportResponse(200, projectionJson(sequence = 9)))
    ))
    val client = new HttpGameClient(transport)
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val initial = coordinator.switchSession("game-1", "red-exile")

    client.load("game-1", "red-exile").flatMap {
      case Left(error) =>
        assert(coordinator.recordFailure(initial, error))
        assert(coordinator.connectionState.isInstanceOf[
          ServerConnectionState.Disconnected
        ])
        val reconnect = coordinator.reconnect()
        client.load(reconnect.gameId, reconnect.playerId).map {
          case Right(authoritative) =>
            assertEquals(authoritative.nextSequence, 9L)
            assert(coordinator.route(reconnect, authoritative, None).nonEmpty)
            assertEquals(
              coordinator.connectionState,
              ServerConnectionState.Connected
            )
          case Left(failure) => fail(failure.message)
        }
      case Right(_) => fail("expected initial disconnect")
    }.map { _ =>
      assertEquals(transport.requests.map(_._1).toVector, Vector("GET", "GET"))
      assert(!transport.requests.exists(_._1 == "POST"))
    }
  }

  test("reconnect generation rejects late load and command callbacks") {
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val oldLoad = coordinator.switchSession("game-1", "red-exile")
    val offline = GameClientFailure.RequestTimedOut("GET", "/api", 10000)
    assert(coordinator.recordFailure(oldLoad, offline))

    val reconnect = coordinator.reconnect()
    assert(!coordinator.accepts(oldLoad))
    assert(!coordinator.recordFailure(
      oldLoad,
      GameClientFailure.NetworkFailure("late command failure")
    ))
    assertEquals(coordinator.route(
      oldLoad,
      projection(activePlayer = "blue-exile"),
      None
    ), None)
    assert(coordinator.accepts(reconnect))
  }

  test("only transport failures produce disconnected state") {
    val transient = Vector[GameClientFailure](
      GameClientFailure.NetworkFailure("offline"),
      GameClientFailure.RequestTimedOut("GET", "/api", 10000),
      GameClientFailure.RequestAborted("GET", "/api")
    )
    transient.foreach(error => assert(GameClientFailure.isTransient(error)))
    assert(!GameClientFailure.isTransient(
      GameClientFailure.StalePosition("conflict")
    ))
    assert(!GameClientFailure.isTransient(
      GameClientFailure.DecodeFailure("$", "bad projection")
    ))
  }

  test("malformed projection is a typed decode failure") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, """{"gameId":"game-1"}"""))
    ))

    new HttpGameClient(transport)
      .load("game-1", "red-exile")
      .map(result =>
        assert(result.left.toOption.exists(
          _.isInstanceOf[GameClientFailure.DecodeFailure]
        )))
  }

  test("null, scalar, and malformed nested projections are decode failures") {
    val malformed = Vector(
      "null",
      "7",
      "[]",
      projectionJson(sequence = 1).replace(
        "\"players\":[",
        "\"players\":[null,"
      ),
      projectionJson(sequence = 1).replace(
        "\"sites\":[{",
        "\"sites\":[7,{"
      )
    )

    malformed.foreach { json =>
      val result = GameJson.decodeProjection(json)
      assert(result.left.toOption.exists(
        _.isInstanceOf[GameClientFailure.DecodeFailure]
      ), json)
    }
  }

  test("nextSequence is bounded to the largest JSON-safe integer") {
    val maximum = GameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740991"
      )
    )
    assertEquals(maximum.toOption.get.nextSequence, 9007199254740991L)

    val unsafe = GameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740992"
      )
    )
    assert(unsafe.left.toOption.exists(
      _.isInstanceOf[GameClientFailure.DecodeFailure]
    ))
  }

  test("stale refresh routes through active-player selection with notice") {
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val request = coordinator.switchSession("game-1", "red-exile")
    val stale = GameClientFailure.StalePosition("position changed")
    val refreshed = projection(activePlayer = "blue-exile")

    coordinator.route(request, refreshed, Some(stale)) match {
      case Some(ProjectionRoute.ReloadForActivePlayer(
            value,
            nextRequest,
            notice
          )) =>
        assertEquals(value.activeParticipantId, Some("blue-exile"))
        assertEquals(nextRequest.playerId, "blue-exile")
        assertEquals(notice, Some(stale))
      case other => fail(s"unexpected route: $other")
    }
  }

  test("late callbacks from an old game or player selection are discarded") {
    val coordinator = new ServerSessionCoordinator("game-a", "red-exile")
    val oldGame = coordinator.switchSession("game-a", "red-exile")
    val newGame = coordinator.switchSession("game-b", "red-exile")

    assert(!coordinator.accepts(oldGame))
    assertEquals(coordinator.route(
      oldGame,
      projection(gameId = "game-a"),
      None
    ), None)
    assert(coordinator.accepts(newGame))

    val blueView = coordinator.switchSession("game-b", "blue-exile")
    assert(!coordinator.accepts(newGame))
    assert(coordinator.accepts(blueView))
  }

  test("transport timeout and abort are typed failures") {
    val timeout = GameClientFailure.RequestTimedOut("GET", "/api", 10000)
    val aborted = GameClientFailure.RequestAborted("POST", "/api")
    assert(timeout.message.contains("10000 ms"))
    assert(aborted.message.contains("aborted"))
  }

  test("new persisted test uses a distinct bootstrap route, never mutation") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 1))),
      Right(TransportResponse(200, projectionJson(sequence = 1)))
    ))
    val client = new HttpGameClient(transport)

    client.bootstrap("game-a", "red-exile", bootstrap)
      .flatMap(_ => client.bootstrap("game-b", "red-exile", bootstrap))
      .map { _ =>
        assertEquals(
          transport.requests.map(_._2).toVector,
          Vector(
            "/api/dev/first-games/game-a/bootstrap?playerId=red-exile",
            "/api/dev/first-games/game-b/bootstrap?playerId=red-exile"
          )
        )
        assert(!transport.requests.exists(_._1 == "DELETE"))
      }
  }

  test("projection contains scoped choices, no hidden plan, and Ready state") {
    val json = projectionJson(
      sequence = 7,
      phase = "ready",
      ready = true,
      completed = true,
      choices = false
    )
    assert(!json.contains("denizenOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("relicOrder"))

    GameJson.decodeProjection(json) match {
      case Right(projection) =>
        assert(projection.ready)
        assert(projection.completed)
        assertEquals(projection.phase, "ready")
        assertEquals(projection.pendingCardDecision, None)
      case Left(failure) => fail(failure.message)
    }
  }

  private def projectionJson(
      sequence: Long,
      phase: String = "awaiting-pawn",
      siteId: String = "site:001",
      adviserId: String = "denizen:0612",
      ready: Boolean = false,
      completed: Boolean = false,
      choices: Boolean = true
  ): String = {
    val pendingDecision =
      if (choices)
        s"""{"decisionId":"setup-adviser-0-red-exile","kind":"starting-adviser","actorPlayerId":"red-exile","prompt":"Choose adviser","instructions":[],"cards":[${cardJson(adviserId, "denizen", "Printed Adviser")}],"keepMinimum":1,"keepMaximum":1,"orderingRequired":false,"resolutionsByCard":{"$adviserId":[{"kind":"starting-adviser","orientation":null,"replacementRequired":false,"replacementTargets":[]}]}}"""
      else "null"
    s"""{
       |"gameId":"game-1",
       |"nextSequence":$sequence,
       |"phase":"$phase",
       |"activeParticipantId":"red-exile",
       |"players":[
       |{"playerId":"red-exile","displayName":"Red Exile","role":"exile","colorToken":"red"},
       |{"playerId":"blue-exile","displayName":"Blue Exile","role":"exile","colorToken":"blue"},
       |{"playerId":"yellow-exile","displayName":"Yellow Exile","role":"exile","colorToken":"yellow"}
       |],
       |"world":[
       |{"regionId":"cradle","sites":[${siteJson(siteId, "Printed Site", populated = true)},${siteJson("site:002", "Second")}]},
       |{"regionId":"provinces","sites":[${siteJson("site:003", "Third")},${siteJson("site:004", "Fourth")},${siteJson("site:005", "Fifth")}]},
       |{"regionId":"hinterland","sites":[${siteJson("site:006", "Sixth")},${siteJson("site:007", "Seventh")},${siteJson("site:008", "Eighth")}]}
       |],
       |"pawnLocations":[],
       |"legalControls":["placePawn","chooseAdviser"],
       |"ready":$ready,
       |"completed":$completed,
       |"boardTargetActions":[],
       |"pendingCardDecision":$pendingDecision
       |}""".stripMargin
  }

  private def cardJson(id: String, kind: String, name: String): String =
    s"""{"cardId":"$id","cardKind":"$kind","name":"$name","suit":null,"restrictions":null,"rulesText":null,"orientation":"face-up","hidden":false}"""

  private def siteJson(
      siteId: String,
      label: String,
      populated: Boolean = false
  ): String =
    if (populated)
      s"""{"siteId":"$siteId","label":"$label","looseFavor":2,"looseSecrets":1,"denizenCapacity":3,"relicCapacity":2,"denizens":[{"denizenId":"denizen:z","label":"Zed"},{"denizenId":"denizen:a","label":"Able"}],"relics":{"facedownCount":2},"forces":{"forceKind":"exile","count":2,"rulerKind":"player","rulerPlayerId":"red-exile","label":"Red Warbands","colorToken":"red"}}"""
    else
      s"""{"siteId":"$siteId","label":"$label","looseFavor":0,"looseSecrets":0,"denizenCapacity":0,"relicCapacity":0,"denizens":[],"relics":{"facedownCount":0},"forces":null}"""

  private def projection(
      gameId: String = "game-1",
      activePlayer: String = "red-exile"
  ): GameProjection =
    GameJson.decodeProjection(
      projectionJson(sequence = 2).replace(
        "\"gameId\":\"game-1\"",
        s"\"gameId\":\"$gameId\""
      ).replace(
        "\"activeParticipantId\":\"red-exile\"",
        s"\"activeParticipantId\":\"$activePlayer\""
      )
    ).toOption.get

  private final class StubTransport(
      responses: Vector[Either[GameClientFailure, TransportResponse]]
  ) extends JsonTransport {
    private val remaining = mutable.Queue(responses: _*)
    val requests =
      mutable.ArrayBuffer.empty[(String, String, Option[String])]

    override def request(
        method: String,
        url: String,
        body: Option[String]
    ) = {
      requests += ((method, url, body))
      Future.successful(remaining.dequeue())
    }
  }
}
