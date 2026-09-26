package oathdigital.protocol

import oathdigital.model.PlayerColor

import oathdigital.protocol.projection._

class ProjectionProtocolSuite extends munit.FunSuite:
  private val hidden = CardDetailsProjection("hidden", "denizen", "Unknown",
    hidden = true)
  private val known = CardDetailsProjection("known", "denizen", "Known",
    Some("beast"), Some("none"), Some("public rule"), Some("faceup"),
    None, favor = 1, secrets = 2, defense = Some(1))
  private val target = BoardTargetRefProjection.Site("site:a")
  private val projection = GameProjection(
    gameId = "game-1", nextSequence = 7, phase = "act",
    activeParticipantId = Some("red"),
    players = Vector(SetupPlayerProjection("red", "Red Exile", "exile", PlayerColor.Red)),
    world = Vector(SetupRegionProjection("cradle", Vector(SetupSiteProjection(
      "site:a", "Site A", 1, 2, 3, 2,
      Vector(SiteCardProjection("hidden", "Unknown", Some(hidden))),
      SiteRelicsProjection(1, Vector(known)), defense = 2,
      recoverDifficulty = Some(3), forgeCost = Some(ForgeCostProjection(1, 1)),
      powers = Vector(SitePowerProjection("rest", "Rest", Some("public"))),
      forces = Some(SiteForcesProjection.Exile(2, "red", PlayerColor.Red,
        "Red warbands")))), 1, Some("denizen"))),
    pawnLocations = Vector(PawnLocationProjection("red", "site:a")),
    legalControls = Vector("campaign"), ready = true, completed = false,
    activePlayerResources = Some(ActivePlayerResourcesProjection(2, 1, 1, 2, 4, 4)),
    currentSiteResources = Some(CurrentSiteResourcesProjection("site:a", 1, 2)),
    actionSelectionOpen = true, actionFamilies = Vector("campaign"),
    legalTravelDestinations = Vector(LegalTravelDestinationProjection("site:b", 2)),
    legalSearchSources = Vector(LegalSearchSourceProjection("region", Some("cradle"), 1)),
    boardTargetActions = Vector(BoardTargetActionProjection("campaign", "Choose", 1, 1,
      false, Vector(BoardTargetCandidateProjection(target, "Known", Vector("detail"))),
      Some("decision"))),
    pendingCardDecision = Some(PendingCardDecisionProjection("pending",
      "starting-adviser", "red", "Choose adviser", Vector.empty,
      Vector(hidden, known), 1, 1, false,
      Map("hidden" -> Vector.empty, "known" -> Vector.empty))),
    worldDeckCount = 5,
    worldDeckTopCardKind = Some("denizen"),
    playerBoards = Vector(PlayerBoardProjection("red", 3, 2, 1, 1, 2, 4, 4,
      Some("site:a"), Vector(hidden), Vector(known), None)),
    oathkeeper = Some(OathkeeperProjection("supremacy", Some("red"), "oathkeeper",
      false, None)),
    banners = Vector(BannerProjection("peoples-favor", "mob", Some("red"), 2)),
    minorActions = Some(MinorActionsProjection(
      Vector(MinorAdviserProjection(known, Vector(CardResolutionProjection("discard")))),
      true, Vector(hidden), Some("site:a"), 1, 1)),
    favorBanks = Vector(FavorBankProjection("beast", 4)),
    phasePowers = Vector(PhasePowerProjection("denizen.silver-tongue",
      DecisionOptionProjection("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")),
    tracks = Some(GameTracksProjection(4, 3, false, 4, "red")),
    relicDeckCount = 21,
    temporaryHandPreview = Vector(known),
    // A partition query, the form with the most to carry: two sections with
    // minima, options with both a plain label and card details, and both
    // pieces of panel copy. Every other form has its own round trip below,
    // because every other form is now its own type.
    walkerDecision = Some(WalkerDecisionProjection("forge", "forge.assignment",
      "decide", query = Some(DecisionQueryProjection.Partition(
        Vector(DecisionSectionProjection("pay-favor", "Pay Favor", 1),
          DecisionSectionProjection("pay-secret", "Pay Secret", 1)),
        Vector(DecisionOptionProjection("denizen", "known", "Known",
            Some(known)),
          DecisionOptionProjection("button", "skip", "Skip")),
        confirmLabel = Some("Complete Forge"),
        heading = Some("Forge a relic"))),
      rollOutcome = Some(WalkerRollOutcomeProjection("campaign.attack",
        Vector("two-swords-skull"), 2, None, Vector("1 skull loss"))))),
    walkerWaiting = Some(WalkerWaitingProjection("blue", Some("Choose the Oathkeeper"))),
    supplyMaximum = 7, restSupplyGain = Some(3))

  test("populated player-scoped projections round-trip exactly on both runtimes"):
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(projection)),
      Right(projection))
    assert(projection.playerBoards.head.advisers.head.hidden)
    assertEquals(projection.playerBoards.head.advisers.head.name, "Unknown")
    assertEquals(projection.tracks.map(_.round), Some(4))
    assertEquals(projection.temporaryHandPreview.map(_.cardId), Vector("known"))

  /** Task 5b: a decision's panel copy is two OPTIONAL strings, so the
    * absent case has to round-trip as faithfully as the present one. The
    * populated projection above covers the present case; this covers a
    * query that declares neither, which is what every choose-one park
    * that has nothing to title itself sends.
    */
  /** The Rest preview is offered only to the player who can end the Act, so
    * its absence is the common case and has to survive the trip too.
    */
  test("a projection with no Rest preview round-trips as absent"):
    val without = projection.copy(restSupplyGain = None)
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(without)),
      Right(without))

  test("a decision query declaring no panel copy round-trips as absent"):
    val bare = DecisionQueryProjection.ChooseOne(
      Vector(DecisionOptionProjection("button", "stop", "Stop")))
    assertEquals(bare.heading, None)
    val without = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(bare))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(without)),
      Right(without))

  /** A parked roll is the one decision shape that carries a pool and a
    * count instead of a query, so those two fields have no other coverage.
    */
  test("a parked roll round-trips its pool and its count"):
    val rolling = projection.copy(walkerDecision = Some(
      WalkerDecisionProjection("recover", "walker.recover.roll", "roll",
        pool = Some("recover"), count = Some(2),
        rollOutcome = Some(WalkerRollOutcomeProjection("recover",
          Vector.empty, 0, Some(3))))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(rolling)),
      Right(rolling))

  /** Task 8: the answers already recorded at a repeated decision, described
    * as options -- the plan window's own "what has already been applied"
    * strip. `ActionProjectionCodec` is `private[projection]`, so this reaches
    * it the same way every other walker-decision test in this suite does: a
    * full `GameProjection` round-trip through `GameProjectionCodec`.
    */
  test("a walker decision round-trips the options already answered at it"):
    val played = DecisionOptionProjection("relic", "relic:sticky-fire",
      "Sticky Fire", badge = Some("Attack Plan"))
    val repeated = projection.copy(walkerDecision = Some(
      WalkerDecisionProjection("campaign", "campaign.attacker-plan",
        "decide", answeredOptions = Vector(played))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(repeated)),
      Right(repeated))

  test("a choose-one option round-trips its details and defaults them to none"):
    val annotated = DecisionQueryProjection.ChooseOne(Vector(
      DecisionOptionProjection("denizen", "d1", "Old Oak", None,
        Vector("1 Supply", "+2 warbands")),
      DecisionOptionProjection("denizen", "d2", "Rowdy Pub")))
    assertEquals(annotated.options.last.details, Vector.empty)
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(annotated))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))

  test("a card defaults to implemented and round-trips implemented = false"):
    assert(known.implemented)
    val unimplemented = known.copy(cardId = "stub", implemented = false)
    val withStub = projection.copy(temporaryHandPreview = Vector(unimplemented))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(withStub)),
      Right(withStub))

  test("a negotiate query and a waiting deal round trip with and without editing"):
    val card = CardDetailsProjection("r1", "relic", "Relic One",
      orientation = Some("face-down"))
    val editing = NegotiationEditingProjection(5, Vector(card), Vector.empty,
      Vector(NegotiationSiteRelicProjection("s1", card)), canAccept = true)
    val deal = NegotiationDealProjection(Vector("red", "blue"), Vector("blue"),
      Vector(NegotiationTransferProjection("red", "blue", 3, 1, Vector(card))),
      Vector(NegotiationDisclosureProjection("red", "blue", "held-relic", None)),
      Some(editing))
    val query = DecisionQueryProjection.Negotiate(deal,
      heading = Some("Negotiation"))
    val waiting = WalkerWaitingProjection("red", Some("Negotiation"),
      Vector("blue"), Some(deal.copy(editing = None)))
    val carrying = projection.copy(
      walkerDecision = projection.walkerDecision.map(_.copy(query = Some(query))),
      walkerWaiting = Some(waiting))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))

  test("choose-many and choose-amount queries round-trip their counts and bounds"):
    def site(id: String) = DecisionOptionProjection("site", id, id)
    val many = DecisionQueryProjection.ChooseMany(
      Vector(site("a"), site("b"), site("c")), minOptions = 2, maxOptions = 2,
      heading = Some("Choose sites"))
    val amount = DecisionQueryProjection.ChooseAmount(minAmount = 3,
      maxAmount = 6, suggested = Some(4), confirmLabel = "Take banner",
      heading = Some("Place more than 2 favor"))
    Vector[DecisionQueryProjection](many, amount).foreach { query =>
      val carrying = projection.copy(walkerDecision =
        projection.walkerDecision.map(_.copy(query = Some(query))))
      assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
        Right(carrying))
    }

  test("a distribute query round-trips its slots, suggestions and total"):
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection.Distribute(
      Vector(DecisionSlotProjection(bank("arcane"), 0, 2, Some(2)),
        DecisionSlotProjection(bank("nomad"), 0, 6, None)),
      minTotal = 6, maxTotal = 6, confirmLabel = "Move favor",
      heading = Some("League Treaty"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))

  test("a ranged distribute query round-trips both totals"):
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection.Distribute(
      Vector(DecisionSlotProjection(bank("arcane"), 0, 3, None),
        DecisionSlotProjection(bank("nomad"), 0, 3, None)),
      minTotal = 0, maxTotal = 3, confirmLabel = "Place",
      heading = Some("Place force"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))

  /** A partition is the one form whose confirm label is optional, so the
    * absent case needs a trip of its own -- the fixture above carries one.
    */
  test("a partition declaring no confirm label round-trips as absent"):
    val unlabelled = DecisionQueryProjection.Partition(
      Vector(DecisionSectionProjection("keep", "Keep", 1, Some(1)),
        DecisionSectionProjection("rest", "Rest", 0)),
      Vector(DecisionOptionProjection("button", "stop", "Stop")))
    assertEquals(unlabelled.confirmLabel, None)
    val without = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(unlabelled))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(without)),
      Right(without))

  /** The types rule out a form outside the vocabulary and a field one form
    * lends another, so only JSON from outside can carry either. The fixture's
    * query is a partition, so `minTotal` is a distribute field it must refuse
    * and `sections` is one it must require.
    */
  test("the decoder refuses an unknown form and a field the form does not declare"):
    def rejected(edit: ujson.Value => Unit): Option[ProtocolDecodeFailure] =
      val json = ujson.read(GameProjectionCodec.encode(projection))
      edit(json)
      GameProjectionCodec.decode(ujson.write(json)).left.toOption
    val unknown = rejected(_("walkerDecision")("query")("form") = "choose-two")
    assertEquals(unknown.map(_.path), Some("$.walkerDecision.query.form"))
    assert(clue(unknown).exists(_.isInstanceOf[ProtocolDecodeFailure.UnknownVariant]))
    assertEquals(rejected(_("walkerDecision")("query")("minTotal") = 3).map(_.path),
      Some("$.walkerDecision.query.minTotal"))
    assertEquals(
      rejected(_("walkerDecision")("query").obj.remove("sections")).map(_.path),
      Some("$.walkerDecision.query.sections"))

  /** `WalkerWaitingProjection.heading` is `None` for a Roll park (see its
    * doc): the populated projection above only covers the Decide case
    * (`Some("Choose the Oathkeeper")`), so this pins the Roll case's absent
    * heading round-tripping as faithfully as the present one.
    */
  test("walkerWaiting round-trips with an absent heading, for a Roll park"):
    val rolling = projection.copy(walkerWaiting =
      Some(WalkerWaitingProjection("blue", heading = None)))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(rolling)),
      Right(rolling))

  test("warbands of every player colour round-trip"):
    PlayerColor.all.foreach { color =>
      val carrying = projection.copy(world = projection.world.map(region =>
        region.copy(sites = region.sites.map(_.copy(forces = Some(
          SiteForcesProjection.Exile(2, "red", color, s"${color.key} warbands")))))))
      assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
        Right(carrying))
    }

  /** The types rule out an unknown colour or a force whose parts disagree, so
    * only JSON from outside can carry one. The decoder refuses it at the
    * field that is wrong.
    */
  test("the decoder refuses an unknown colour and a force that disagrees"):
    def rejected(edit: ujson.Value => Unit): Option[String] =
      val json = ujson.read(GameProjectionCodec.encode(projection))
      edit(json)
      GameProjectionCodec.decode(ujson.write(json)).left.toOption.map(_.path)
    assertEquals(rejected(_("players")(0)("colorToken") = "green"),
      Some("$.players[0].colorToken"))
    assertEquals(rejected(_("world")(0)("sites")(0)("forces")("colorToken") = "green"),
      Some("$.world[0].sites[0].forces.colorToken"))
    assertEquals(rejected(_("world")(0)("sites")(0)("forces")("forceKind") = "bandit"),
      Some("$.world[0].sites[0].forces"))

  test("projection decoder reports exact nested paths and unexpected fields"):
    val wrong = ujson.read(GameProjectionCodec.encode(projection))
    wrong("world")(0)("sites")(0)("relics")("facedownCount") = "one"
    assertEquals(GameProjectionCodec.decode(ujson.write(wrong)).left.toOption.get.path,
      "$.world[0].sites[0].relics.facedownCount")
    val extra = ujson.read(GameProjectionCodec.encode(projection))
    extra("playerBoards")(0)("privateCardId") = "injected"
    assertEquals(GameProjectionCodec.decode(ujson.write(extra)).left.toOption.get.path,
      "$.playerBoards[0].privateCardId")

  test("bootstrap requests round-trip and reject malformed exact fields"):
    val request = FirstGameBootstrapRequest(0, Vector(
      BootstrapParticipantRequest("red", "red-lineage", PlayerColor.Red),
      BootstrapParticipantRequest("blue", "blue-lineage", PlayerColor.Blue)), "red")
    assertEquals(FirstGameBootstrapCodec.decode(FirstGameBootstrapCodec.encode(request)),
      Right(request))
    val unexpected = ujson.read(FirstGameBootstrapCodec.encode(request))
    unexpected("participants")(0)("actorPlayerId") = "red"
    assertEquals(FirstGameBootstrapCodec.decode(ujson.write(unexpected)).left.toOption.get.path,
      "$.participants[0].actorPlayerId")
