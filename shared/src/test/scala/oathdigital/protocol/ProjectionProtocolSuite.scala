package oathdigital.protocol

import oathdigital.model.PlayerColor

import oathdigital.protocol.projection._
import oathdigital.testkit.Unwrap

class ProjectionProtocolSuite extends munit.FunSuite:
  /** Fails unless `candidate` encodes and decodes back to itself. */
  private def assertRoundTrips(candidate: GameProjection)(
      using munit.Location): Unit =
    assertEquals(
      GameProjectionCodec.decode(GameProjectionCodec.encode(candidate)),
      Right(candidate))

  private val hidden = CardDetailsProjection("hidden", "denizen", "Unknown",
    hidden = true)
  private val known = CardDetailsProjection("known", "denizen", "Known",
    Some("beast"), Some("none"), Some("public rule"), Some("faceup"),
    None, favor = 1, secrets = 2, defense = Some(1))
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
    actionSelectionOpen = true,
    worldDeckCount = 5,
    worldDeckTopCardKind = Some("denizen"),
    playerBoards = Vector(PlayerBoardProjection("red", 3, 2, 1, 1, 2, 4, 4,
      Some("site:a"), advisers = Vector(hidden), Vector(known), None)),
    // A decided game and an unheld banner: the optional fields the other
    // fixtures leave empty.
    oathkeeper = Some(OathkeeperProjection("supremacy", Some("red"), "oathkeeper",
      false, Some("red"), Some("oathkeeper"))),
    banners = Vector(BannerProjection("peoples-favor", "mob", Some("red"), 2),
      BannerProjection("darkest-secret", "wandering-flame", None, 0)),
    minorActions = Some(MinorActionsProjection(
      Vector(MinorAdviserProjection(known)),
      true, Vector(hidden), Some("site:a"), 1, 1)),
    favorBanks = Vector(FavorBankProjection("beast", 4)),
    phasePowers = Vector(PhasePowerProjection("denizen.silver-tongue",
      DecisionOptionProjection("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")),
    tracks = Some(GameTracksProjection(round = 4, 3, false, 4, "red")),
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

  test("a fully populated projection round-trips exactly"):
    assertRoundTrips(projection)

  test("the populated projection holds a hidden adviser, round 4 and a " +
      "one-card hand preview"):
    assert(projection.playerBoards.head.advisers.head.hidden)
    assertEquals(projection.playerBoards.head.advisers.head.name, "Unknown")
    assertEquals(projection.tracks.map(_.round), Some(4))
    assertEquals(projection.temporaryHandPreview.map(_.cardId), Vector("known"))

  /** The Rest preview is offered only to the player who can end the Act, so
    * its absence is the common case and has to survive the trip too.
    */
  test("a projection with no Rest preview round-trips as absent"):
    val without = projection.copy(restSupplyGain = None)
    assertRoundTrips(without)

  test("the undo offer and the log revision round-trip"):
    assertRoundTrips(projection.copy(
      undo = Some(StepOfferProjection("Undo: p1 travelled to Buried Giant")),
      logRevision = Some(42L)))

  test("a payload without an undo offer or a log revision decodes to none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw.obj.remove("undo")
    raw.obj.remove("logRevision")
    assertEquals(GameProjectionCodec.decode(ujson.write(raw))
      .map(decoded => (decoded.undo, decoded.logRevision)), Right((None, None)))

  test("a queued follow-up round-trips"):
    assertRoundTrips(projection.copy(followUp = Some(FollowUpProjection("p1",
      "campaign", "Campaign", "Knights Errant", startable = false,
      reason = Some("No site to conquer and no enemy pawn to raid"),
      queuedAfter = Vector("Campaign")))))
    assertRoundTrips(projection.copy(followUp = Some(FollowUpProjection("p1",
      "travel", "Travel", "Second Wind", startable = true))))

  test("a payload without a follow-up decodes to none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw.obj.remove("followUp")
    assertEquals(GameProjectionCodec.decode(ujson.write(raw))
      .map(_.followUp), Right(None))

  test("the consent parts of a projection round-trip"):
    assertRoundTrips(projection.copy(
      consentWaiting = Some(ConsentWaitingProjection(Vector("p2"))),
      consentPrompt = Some(ConsentPromptProjection("p1",
        Vector("Narrow Pass"), "Dunes")),
      consentPreferences = Vector(ConsentPreferencesProjection(
        "site.narrow-pass.pass", "Narrow Pass",
        Vector(ConsentPreferenceRowProjection("p1", "always"))),
        ConsentPreferencesProjection("denizen.secret-police",
          "Secret Police", Vector(ConsentPreferenceRowProjection("p1",
            "never")), Some("113")))))

  test("an askable option's consent ask and a site power's id round-trip"):
    assertRoundTrips(projection.copy(
      walkerDecision = Some(WalkerDecisionProjection("travel",
        "travel.destination", "decide", query = Some(
          DecisionQueryProjection.ChooseOne(Vector(DecisionOptionProjection(
            "site", "site:dunes", "Dunes", state = "askable",
            reason = Some("Narrow Pass: needs consent"),
            consent = Some(ConsentAskProjection(Vector("p2"),
              Vector("Narrow Pass"))))))))),
      world = projection.world.map(region => region.copy(sites =
        region.sites.map(site => site.copy(powers = Vector(
          SitePowerProjection("pass", "Pass", Some("text"),
            "site.narrow-pass.pass"))))))))

  test("a payload without consent parts decodes to none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    Vector("consentWaiting", "consentPrompt", "consentPreferences")
      .foreach(raw.obj.remove)
    assertEquals(GameProjectionCodec.decode(ujson.write(raw)).map(decoded =>
      (decoded.consentWaiting, decoded.consentPrompt,
        decoded.consentPreferences)), Right((None, None, Vector.empty)))

  test("the redo offer round-trips"):
    assertRoundTrips(projection.copy(
      redo = Some(StepOfferProjection("Redo: p1 travelled to Buried Giant"))))

  test("a payload without a redo offer decodes to none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw.obj.remove("redo")
    assertEquals(GameProjectionCodec.decode(ujson.write(raw)).map(_.redo),
      Right(None))

  test("a region's discard cards round-trip, hidden and known alike"):
    val withPile = projection.copy(world = projection.world.map(region =>
      region.copy(discardCards = Vector(known, hidden), discardCount = 2,
        discardTopCardKind = Some("denizen"))))
    assertRoundTrips(withPile)

  test("a board's bag figures and the Supply bands round-trip"):
    val withBag = projection.copy(
      playerBoards = projection.playerBoards.map(_.copy(bankedWarbands = 5,
        printedWarbands = 14, siteWarbands = 3, restSupplyBase = 5)),
      supplyBands = Vector(SupplyBandProjection(9, None, 6),
        SupplyBandProjection(4, Some(8), 5), SupplyBandProjection(0, Some(3), 4)))
    assertRoundTrips(withBag)

  test("a payload without bag figures or bands decodes to zero and none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw.obj.remove("supplyBands")
    raw("playerBoards").arr.foreach { board =>
      Vector("bankedWarbands", "printedWarbands", "siteWarbands", "restSupplyBase")
        .foreach(board.obj.remove) }
    val decoded = GameProjectionCodec.decode(ujson.write(raw))
    assertEquals(decoded.map(_.supplyBands), Right(Vector.empty))
    assertEquals(decoded.map(_.playerBoards.map(_.bankedWarbands)),
      Right(projection.playerBoards.map(_ => 0)))

  test("a payload without discard cards decodes to an empty pile"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw("world").arr.foreach(region => region.obj.remove("discardCards"))
    val decoded = GameProjectionCodec.decode(ujson.write(raw))
    assertEquals(decoded.map(_.world.map(_.discardCards)),
      Right(projection.world.map(_ => Vector.empty)))

  /** Task 5b: a decision's panel copy is two OPTIONAL strings, so the
    * absent case has to round-trip as faithfully as the present one. The
    * populated projection above covers the present case; this covers a
    * query that declares neither, which is what every choose-one park
    * that has nothing to title itself sends.
    */
  test("a decision query declaring no panel copy round-trips as absent"):
    val bare = DecisionQueryProjection.ChooseOne(
      Vector(DecisionOptionProjection("button", "stop", "Stop")))
    assertEquals(bare.heading, None)
    val without = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(bare))))
    assertRoundTrips(without)

  /** A parked roll is the one decision shape that carries a pool and a
    * count instead of a query, so those two fields have no other coverage.
    */
  test("a parked roll round-trips its pool and its count"):
    val rolling = projection.copy(walkerDecision = Some(
      WalkerDecisionProjection("recover", "walker.recover.roll", "roll",
        pool = Some("recover"), count = Some(2),
        rollOutcome = Some(WalkerRollOutcomeProjection("recover",
          Vector.empty, 0, Some(3))))))
    assertRoundTrips(rolling)

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
    assertRoundTrips(repeated)

  test("a choose-one option round-trips its details and defaults them to none"):
    val annotated = DecisionQueryProjection.ChooseOne(Vector(
      DecisionOptionProjection("denizen", "d1", "Old Oak", None,
        Vector("1 Supply", "+2 warbands")),
      DecisionOptionProjection("denizen", "d2", "Rowdy Pub")))
    assertEquals(annotated.options.last.details, Vector.empty)
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(annotated))))
    assertRoundTrips(carrying)

  test("a card defaults to implemented and round-trips implemented = false"):
    assert(known.implemented)
    val unimplemented = known.copy(cardId = "stub", implemented = false)
    val withStub = projection.copy(temporaryHandPreview = Vector(unimplemented))
    assertRoundTrips(withStub)

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
    assertRoundTrips(carrying)

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
      assertRoundTrips(carrying)
    }

  test("a board decision's states, reasons, tallies, summary and " +
      "presentation round-trip"):
    val origin = DecisionOptionProjection("site", "plains", "Plains",
      state = "locked")
    val longbows = DecisionOptionProjection("denizen", "longbows", "Longbows")
    val wolves = DecisionOptionProjection("site", "wolves", "Wolves",
      state = "unavailable", reason = Some("Not ruled by the defender"))
    val many = DecisionQueryProjection.ChooseMany(
      Vector(origin, longbows, wolves), 0, 1, Some("Targets"),
      tallies = Vector(DecisionTallyProjection("Defending force", "warbands",
        Vector(DecisionTallyValueProjection("site", "plains", 2)), Some(4))),
      summary = Some(DecisionSummaryProjection("Target", "site", "sites")))
    assertRoundTrips(projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(many),
        presentation = "board"))))

  test("a decision encoded before board decisions decodes with the old " +
      "defaults"):
    val many = DecisionQueryProjection.ChooseMany(
      Vector(DecisionOptionProjection("site", "plains", "Plains")), 0, 1)
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(many))))
    val json = ujson.read(GameProjectionCodec.encode(carrying))
    val decision = json("walkerDecision")
    decision.obj.remove("presentation")
    decision("query").obj.remove("tallies")
    decision("query").obj.remove("summary")
    decision("query")("options").arr.foreach { row =>
      row.obj.remove("state"); row.obj.remove("reason") }
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(carrying))

  test("a distribute query round-trips its slots, suggestions and total"):
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection.Distribute(
      Vector(DecisionSlotProjection(bank("arcane"), 0, 2, Some(2)),
        DecisionSlotProjection(bank("nomad"), 0, 6, None)),
      minTotal = 6, maxTotal = 6, confirmLabel = "Move favor",
      heading = Some("League Treaty"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertRoundTrips(carrying)

  test("a ranged distribute query round-trips both totals"):
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection.Distribute(
      Vector(DecisionSlotProjection(bank("arcane"), 0, 3, None),
        DecisionSlotProjection(bank("nomad"), 0, 3, None)),
      minTotal = 0, maxTotal = 3, confirmLabel = "Place",
      heading = Some("Place force"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertRoundTrips(carrying)

  test("an inspect query round-trips its cards, its done button and its heading"):
    val inspect = DecisionQueryProjection.Inspect(Vector(known, hidden),
      DecisionOptionProjection("button", "done", "Done"),
      heading = Some("Scryer: the Cradle discard pile"))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(inspect))))
    assertRoundTrips(carrying)

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
    assertRoundTrips(without)

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
    assertEquals(unknown, Some(ProtocolDecodeFailure.UnknownVariant(
      "$.walkerDecision.query.form", "unknown decision form 'choose-two'")))
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
    assertRoundTrips(rolling)

  test("warbands of every player colour round-trip"):
    PlayerColor.all.foreach { color =>
      val carrying = projection.copy(world = projection.world.map(region =>
        region.copy(sites = region.sites.map(_.copy(forces = Some(
          SiteForcesProjection.Exile(2, "red", color, s"${color.key} warbands")))))))
      assertRoundTrips(carrying)
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
    assertEquals(
      Unwrap.rejected(GameProjectionCodec.decode(ujson.write(wrong))).path,
      "$.world[0].sites[0].relics.facedownCount")
    val extra = ujson.read(GameProjectionCodec.encode(projection))
    extra("playerBoards")(0)("privateCardId") = "injected"
    assertEquals(
      Unwrap.rejected(GameProjectionCodec.decode(ujson.write(extra))).path,
      "$.playerBoards[0].privateCardId")

  test("bootstrap requests round-trip and reject malformed exact fields"):
    val request = FirstGameBootstrapRequest(0, Vector(
      BootstrapParticipantRequest("red", PlayerColor.Red),
      BootstrapParticipantRequest("blue", PlayerColor.Blue)), "red")
    assertEquals(FirstGameBootstrapCodec.decode(FirstGameBootstrapCodec.encode(request)),
      Right(request))
    val unexpected = ujson.read(FirstGameBootstrapCodec.encode(request))
    unexpected("participants")(0)("actorPlayerId") = "red"
    assertEquals(Unwrap.rejected(
      FirstGameBootstrapCodec.decode(ujson.write(unexpected))).path,
      "$.participants[0].actorPlayerId")
    // The server derives the lineage from the color, so a request naming one is refused.
    val lineage = ujson.read(FirstGameBootstrapCodec.encode(request))
    lineage("participants")(0)("lineageId") = "red-lineage"
    assertEquals(Unwrap.rejected(
      FirstGameBootstrapCodec.decode(ujson.write(lineage))).path,
      "$.participants[0].lineageId")

  test("an option's replaced card round-trips beside an option with none"):
    val replacing = DecisionOptionProjection("site-slot", "site:a:0",
      "Replace Known at A", replaces = Some(known))
    val pile = DecisionOptionProjection("discard-pile", "cradle",
      "Discard to the Cradle discard")
    assertRoundTrips(projection.copy(walkerDecision = Some(
      WalkerDecisionProjection("search", "cardplay.place.denizen.known",
        "decide", query = Some(DecisionQueryProjection.ChooseOne(
          Vector(replacing, pile)))))))

  test("a board's adviser limit round-trips"):
    assertRoundTrips(projection.copy(playerBoards =
      projection.playerBoards.map(_.copy(adviserLimit = 2))))

  test("a minor adviser written with its old placements still decodes"):
    val json = ujson.read(GameProjectionCodec.encode(projection))
    json("minorActions")("advisers")(0)("placements") = ujson.Arr()
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(projection))

  test("a payload without an adviser limit or a replaced card decodes to the " +
      "defaults"):
    val replacing = DecisionOptionProjection("site-slot", "site:a:0",
      "Replace Known at A", replaces = Some(known))
    val written = projection.copy(
      playerBoards = projection.playerBoards.map(_.copy(adviserLimit = 2)),
      walkerDecision = Some(WalkerDecisionProjection("search",
        "cardplay.place.denizen.known", "decide", query = Some(
          DecisionQueryProjection.ChooseOne(Vector(replacing))))))
    def strip(value: ujson.Value, key: String): Unit = value match
      case obj: ujson.Obj =>
        obj.value.remove(key)
        obj.value.values.foreach(strip(_, key))
      case arr: ujson.Arr => arr.value.foreach(strip(_, key))
      case _ => ()
    val json = ujson.read(GameProjectionCodec.encode(written))
    strip(json, "adviserLimit")
    strip(json, "replaces")
    assertEquals(GameProjectionCodec.decode(ujson.write(json)), Right(
      written.copy(
        playerBoards = projection.playerBoards.map(_.copy(adviserLimit = 3)),
        walkerDecision = Some(WalkerDecisionProjection("search",
          "cardplay.place.denizen.known", "decide", query = Some(
            DecisionQueryProjection.ChooseOne(Vector(
              replacing.copy(replaces = None)))))))))

  test("an option's variant round-trips, and a payload without it decodes " +
      "to none"):
    val faceup = DecisionOptionProjection("adviser-play", "red:2:faceup",
      "Adviser slot 3, faceup", variant = Some("Faceup"))
    def parked(option: DecisionOptionProjection) = projection.copy(
      walkerDecision = Some(WalkerDecisionProjection("search",
        "cardplay.place.denizen.known", "decide", query = Some(
          DecisionQueryProjection.ChooseOne(Vector(option))))))
    val written = parked(faceup)
    assertRoundTrips(written)
    def strip(value: ujson.Value): Unit = value match
      case obj: ujson.Obj =>
        obj.value.remove("variant")
        obj.value.values.foreach(strip)
      case arr: ujson.Arr => arr.value.foreach(strip)
      case _ => ()
    val json = ujson.read(GameProjectionCodec.encode(written))
    strip(json)
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(parked(faceup.copy(variant = None))))
