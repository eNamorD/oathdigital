package oathdigital.protocol

import oathdigital.protocol.projection._

class ProjectionProtocolSuite extends munit.FunSuite {
  private val hidden = CardDetailsProjection("hidden", "denizen", "Unknown",
    hidden = true)
  private val known = CardDetailsProjection("known", "denizen", "Known",
    Some("beast"), Some("none"), Some("public rule"), Some("faceup"),
    None, favor = 1, secrets = 2, defense = Some(1))
  private val target = BoardTargetRefProjection.SiteCard("site:a", "denizen", "known")
  private val choice = CampaignPlanChoiceProjection("site-card", Some("site:a:known"),
    None, Some("site:a"), Some("known"), "Known", Some("campaign-plan"),
    1, 0, "adds one die")
  private val projection = GameProjection(
    gameId = "game-1", nextSequence = 7, phase = "act",
    activeParticipantId = Some("red"),
    players = Vector(SetupPlayerProjection("red", "Red Exile", "exile", "red")),
    world = Vector(SetupRegionProjection("cradle", Vector(SetupSiteProjection(
      "site:a", "Site A", 1, 2, 3, 2,
      Vector(SiteCardProjection("hidden", "Unknown", Some(hidden))),
      SiteRelicsProjection(1, Vector(known)), defense = 2,
      recoverDifficulty = Some(3), forgeCost = Some(ForgeCostProjection(1, 1)),
      powers = Vector(SitePowerProjection("rest", "Rest", Some("public"))),
      forces = Some(SiteForcesProjection("exile", 2, "player", Some("red"),
        "Red warbands", "red")))), 1, Some("denizen"))),
    pawnLocations = Vector(PawnLocationProjection("red", "site:a")),
    legalControls = Vector("campaign"), ready = true, completed = false,
    activePlayerResources = Some(ActivePlayerResourcesProjection(2, 1, 1, 2, 4, 4)),
    currentSiteResources = Some(CurrentSiteResourcesProjection("site:a", 1, 2)),
    actionSelectionOpen = true, actionFamilies = Vector("campaign"),
    legalTravelDestinations = Vector(LegalTravelDestinationProjection("site:b", 2)),
    legalSearchSources = Vector(LegalSearchSourceProjection("region", Some("cradle"), 1)),
    legalMusters = Vector(LegalMusterProjection("denizen", "known", "Known", "beast", 1, 2)),
    legalTrades = Vector(LegalTradeProjection("denizen", "known", "Known", "beast", "favor", 1, 2)),
    boardTargetActions = Vector(BoardTargetActionProjection("campaign", "Choose", 1, 1,
      false, Vector(BoardTargetCandidateProjection(target, "Known", Vector("detail"))),
      Some(BoardTargetFormationProjection(1, 2, 2, 1)), Vector(target), Some("decision"))),
    pendingCardDecision = Some(PendingCardDecisionProjection("pending", "search", "red",
      "Choose cards", Vector("Keep one"), Vector(hidden, known), 1, 1, false,
      Map("hidden" -> Vector(CardResolutionProjection("discard")),
        "known" -> Vector(CardResolutionProjection("play-site", Some("faceup"), true,
          Vector(hidden)))))),
    campaign = Some(CampaignProjection("campaign", Vector("site:a"), 2, false,
      Vector(choice), Vector.empty, Vector("two"), 2, 0, 1, None,
      Vector("one"), None, None, 2,
      Vector(CampaignPlacementTargetProjection("site:a", "Site A")))),
    campaignRaidRelocation = Some(CampaignRaidRelocationProjection("raid", "red", "blue",
      "site:a", Vector("site:b"))), worldDeckCount = 5,
    worldDeckTopCardKind = Some("denizen"),
    playerBoards = Vector(PlayerBoardProjection("red", 3, 2, 1, 1, 2, 4, 4,
      Some("site:a"), Vector(hidden), Vector(known), None)),
    oathkeeper = Some(OathkeeperProjection("supremacy", Some("red"), "oathkeeper",
      false, None)),
    oathkeeperRecipient = Some(OathkeeperRecipientProjection("recipient", "red", Vector("blue"))),
    banners = Vector(BannerProjection("peoples-favor", "mob", Some("red"), 2)),
    challenge = Some(ChallengeProjection("challenge", "red", "peoples-favor", Some("blue"),
      1, Vector("site:a"), 0, 1)),
    minorActions = Some(MinorActionsProjection(
      Vector(MinorAdviserProjection(known, Vector(CardResolutionProjection("discard")))),
      true, Vector(hidden), Some("site:a"), 1, 1)),
    negotiation = Some(NegotiationProjection("deal", "red", "site:a", Vector("red", "blue"),
      Vector("blue"), Vector(NegotiationTransferProjection("red", "blue", 1, 0, Vector.empty)),
      Vector(NegotiationDisclosureProjection("red", "blue", "adviser", Some(hidden))),
      2, Vector(known), Vector(hidden),
      Vector(NegotiationSiteRelicProjection("site:b", known)))),
    negotiationWaiting = true,
    favorBanks = Vector(FavorBankProjection("beast", 4)),
    tracks = Some(GameTracksProjection(4, 3, false, 4, "red")),
    relicDeckCount = 21,
    privateAdviserPreview = Vector(known),
    restPower = Some(RestPowerProjection("rest-power", "red", "blue",
      "denizen.league-treaty", LeagueTreatyProjection(Vector(
        RestFavorSourceProjection("denizen", "site:a", "known", "Known", 2)),
        Vector("beast", "hearth")))),
    restPowerWaiting = true,
    // A partition query, the shape with every field populated: a form, two
    // sections with minima, and options carrying both a plain label and
    // card details. A choose-one query is the same type with no sections,
    // so this one round-trip covers both.
    walkerDecision = Some(WalkerDecisionProjection("forge", "forge.assignment",
      "decide", query = Some(DecisionQueryProjection("partition",
        Vector(DecisionOptionProjection("denizen", "known", "Known",
            Some(known)),
          DecisionOptionProjection("button", "skip", "Skip")),
        Vector(DecisionSectionProjection("pay-favor", "Pay Favor", 1),
          DecisionSectionProjection("pay-secret", "Pay Secret", 1)))),
      rollOutcome = Some(WalkerRollOutcomeProjection(Vector("one-shield"), 1, 2)))))

  test("populated player-scoped projections round-trip exactly on both runtimes") {
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(projection)),
      Right(projection))
    assert(projection.playerBoards.head.advisers.head.hidden)
    assertEquals(projection.playerBoards.head.advisers.head.name, "Unknown")
    assertEquals(projection.tracks.map(_.round), Some(4))
    assertEquals(projection.privateAdviserPreview.map(_.cardId), Vector("known"))
  }

  test("projection decoder reports exact nested paths and unexpected fields") {
    val wrong = ujson.read(GameProjectionCodec.encode(projection))
    wrong("world")(0)("sites")(0)("relics")("facedownCount") = "one"
    assertEquals(GameProjectionCodec.decode(ujson.write(wrong)).left.toOption.get.path,
      "$.world[0].sites[0].relics.facedownCount")
    val extra = ujson.read(GameProjectionCodec.encode(projection))
    extra("playerBoards")(0)("privateCardId") = "injected"
    assertEquals(GameProjectionCodec.decode(ujson.write(extra)).left.toOption.get.path,
      "$.playerBoards[0].privateCardId")
  }

  test("bootstrap requests round-trip and reject malformed exact fields") {
    val request = FirstGameBootstrapRequest(0, Vector(
      BootstrapParticipantRequest("red", "red-lineage", "red"),
      BootstrapParticipantRequest("blue", "blue-lineage", "blue")), "red")
    assertEquals(FirstGameBootstrapCodec.decode(FirstGameBootstrapCodec.encode(request)),
      Right(request))
    val unexpected = ujson.read(FirstGameBootstrapCodec.encode(request))
    unexpected("participants")(0)("actorPlayerId") = "red"
    assertEquals(FirstGameBootstrapCodec.decode(ujson.write(unexpected)).left.toOption.get.path,
      "$.participants[0].actorPlayerId")
  }
}
