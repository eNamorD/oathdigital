package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.{CampaignLosingForceResolver, CampaignRules}
import oathdigital.gameplay._
import oathdigital.gameplay.setup._
import oathdigital.model._
import oathdigital.gameplay.OathEvent.{FirstGameCompleted, Mustered, Traded, WakeEnded,
  RestCompleted, RestStarted, SearchCompleted, SearchStarted, Traveled,
  WealthTaken, RecoverRolled, RecoverStopped, RelicRecovered}
import oathdigital.gameplay.OathEvent.{OathkeeperChanged, UsurperFlipped,
  UsurperVictory, OathkeeperRecipientChoiceStarted,
  OathkeeperRecipientChosen, RoundEnded, WarExhaustionResolved}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

class GameEventWireSuite extends munit.FunSuite {
  test("v13 round ending and War Exhaustion preserve outcome and random domain") {
    val p1 = PlayerId("p1")
    val p2 = PlayerId("p2")
    val events = Vector[OathEvent](RoundEnded(8, None),
      WarExhaustionResolved(p2, VictoryKind.RandomSelection, None,
        Vector(p1, p2)))
    events.zipWithIndex.foreach { case (event, index) =>
      val encoded = GameEventWire.encodeEvent("war", catalog.ref, index, event)
        .toOption.get
      val decoded = GameEventWire.decode(encoded).toOption.get
      assertEquals(decoded.formatVersion, GameEventWire.RoundEndFormatVersion)
      assertEquals(decoded.event, event)
    }
  }
  private val rules = new FirstGameSetupRules(catalog)

  test("v12 Vision and Conspiracy events preserve hidden choices exactly") {
    val red = PlayerId("red")
    val conspiracy = VisionId("vision:conspiracy")
    val decision = DecisionId("conspiracy-12")
    val target = ConspiracyTarget.Relic(PlayerId("blue"), RelicId("R12"))
    val events = Vector[OathEvent](
      OathEvent.VisionRevealed(red, VisionId("vision:vision-of-faith"),
        Some(VisionId("vision:vision-of-conquest")), Region.Provinces),
      OathEvent.ConspiracyStarted(red, decision, conspiracy, Some(target),
        Vector(SiteId("S1")), Vector(Suit.Order)),
      OathEvent.ConspiracySecretSiteChosen(red, decision, SiteId("S2"),
        Vector(SiteId("S3"))),
      OathEvent.ConspiracyCompleted(red, decision, conspiracy, Some(target),
        Vector(SiteId("S1"), SiteId("S2"), SiteId("S3")),
        Vector(Suit.Order, Suit.Beast)),
      OathEvent.VisionVictory(red, VisionId("vision:vision-of-faith")))
    val encoded = GameEventWire.encodeStream("visions", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(12))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)

    val banner = OathEvent.ConspiracyStarted(red, decision, conspiracy,
      Some(ConspiracyTarget.Banner(PlayerId("blue"), Banner.DarkestSecret)),
      Vector.empty, Vector.empty)
    val bannerJson = GameEventWire.encodeEvent("visions", catalogRef, 20, banner)
      .toOption.get
    assertEquals(GameEventWire.decode(bannerJson).toOption.get.event, banner)
    bannerJson("payload")("target")("banner") = "unknown"
    assert(GameEventWire.decode(bannerJson).isLeft)
  }

  test("v11 Negotiation events preserve authored terms and disclosures") {
    val red = PlayerId("red"); val blue = PlayerId("blue")
    val terms = NegotiationTerms(Vector(NegotiationTransfer(blue, 2,
      Vector(RelicId("R1")))), Vector(NegotiationDisclosure(blue,
      NegotiationDisclosureRef.Adviser(red, VisionId("vision:test")))))
    val participants = Vector(red, blue)
    val events = Vector[OathEvent](
      OathEvent.NegotiationStarted(red, DecisionId("deal"), SiteId("site:a"), participants),
      OathEvent.NegotiationTermsReplaced(red, DecisionId("deal"), terms),
      OathEvent.NegotiationAccepted(red, DecisionId("deal")),
      OathEvent.NegotiationAccepted(blue, DecisionId("deal")),
      OathEvent.NegotiationCompleted(red, DecisionId("deal"), participants,
        Map(red -> terms, blue -> NegotiationTerms())))
    val encoded = GameEventWire.encodeStream("negotiation", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(11))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("v10 minor actions preserve private identities and prior force facts") {
    val events = Vector[OathEvent](
      OathEvent.FacedownAdviserDiscarded(PlayerId("red"), DenizenId("42"),
        Region.Provinces),
      OathEvent.FacedownAdviserPlayed(PlayerId("red"), DenizenId("43"),
        SearchPlacement.Site(Some(DenizenId("44"))), 1,
        Vector(DenizenId("44")), Vector.empty),
      OathEvent.SiteRelicsPeeked(PlayerId("red"), SiteId("site:a"),
        Vector(RelicId("R1"), RelicId("R2"))),
      OathEvent.OwnedRelicRevealed(PlayerId("red"), RelicId("R3")),
      OathEvent.WarbandsMoved(PlayerId("red"), SiteId("site:a"),
        toSite = false, 2, 3, 4))
    val encoded = GameEventWire.encodeStream("minor", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(10))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("v9 banner events preserve ordered ribbon and replacement facts") {
    val events = Vector[OathEvent](
      OathEvent.BannerChallengeStarted(PlayerId("red"), DecisionId("challenge-9"),
        Banner.PeoplesFavor, Some(PlayerId("blue")), 3, 1,
        Vector(Suit.Order), Vector.empty),
      OathEvent.BannerRibbonChoiceMade(PlayerId("red"), DecisionId("challenge-9"),
        Banner.DarkestSecret, SiteId("site:one"), Vector(SiteId("site:two"))),
      OathEvent.BannerChallengeCompleted(PlayerId("red"), DecisionId("challenge-9"),
        Banner.PeoplesFavor, Some(PlayerId("blue")), 3, 4,
        Vector(Suit.Order, Suit.Beast, Suit.Arcane), Vector.empty, 0),
      OathEvent.BannerResourcePlaced(PlayerId("red"), Banner.PeoplesFavor, 2))
    val encoded = GameEventWire.encodeStream("banners", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(4)(9))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("v8 Forge events round trip exact targets resources and relic top") {
    val site = SiteId("site:forge")
    val targets = Vector("1", "2", "3").map(id =>
      SiteDenizenTarget(site, DenizenId(s"denizen:$id")))
    val assignments = targets.zip(Vector(ForgeResource.Favor,
      ForgeResource.Secret, ForgeResource.Favor)).map {
        case (target, resource) => ForgeResourceAssignment(target, resource) }
    val events = Vector[OathEvent](
      OathEvent.ForgeStarted(PlayerId("red"), DecisionId("forge-8"), site,
        targets, Tokens(2, 1), 1),
      OathEvent.ForgeCompleted(PlayerId("red"), DecisionId("forge-8"), site,
        assignments, RelicId("relic:top")))
    val encoded = GameEventWire.encodeStream("forge", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector(8, 8))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("Campaign Raid targets have stable canonical keys and round trip") {
    val defender = PlayerId("blue")
    val targets = Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(defender),
      CampaignRaidTarget.Relic(defender, RelicId("R03")),
      CampaignRaidTarget.Relic(defender, RelicId("R12")),
      CampaignRaidTarget.Banner(defender, CampaignBanner.PeoplesFavor),
      CampaignRaidTarget.Banner(defender, CampaignBanner.DarkestSecret))
    assertEquals(targets.map(_.stableKey), Vector(
      "pawn:blue", "relic:blue:R03", "relic:blue:R12",
      "banner:blue:peoples-favor", "banner:blue:darkest-secret"))
    assertEquals(CampaignRaidTarget.canonical(targets.reverse), targets)

    val event = OathEvent.CampaignStarted(PlayerId("red"),
      DecisionId("raid-1"), Vector.empty, CampaignDefender.Player(defender),
      supplySpent = 2, force = 3, CampaignKind.Raid, targets)
    val encoded = GameEventWire.encodeStream("raid", catalogRef,
      Vector(RecordedEvent(0, event))).toOption.get
    val decoded = GameEventWire.decodeStream(encoded).toOption.get
    assertEquals(decoded.map(_.event), Vector(event))

    val wrongOrder = ujson.read(encoded).arr
    wrongOrder.head("payload")("raidTargets") = ujson.Arr.from(
      wrongOrder.head("payload")("raidTargets").arr.reverse)
    assert(GameEventWire.decodeStream(ujson.write(wrongOrder)).isLeft)

    val unknownBanner = ujson.read(encoded).arr
    unknownBanner.head("payload")("raidTargets")(3)("banner") = "unknown"
    assert(GameEventWire.decodeStream(ujson.write(unknownBanner)).isLeft)
  }

  test("Raid resolution and relocation round trip hidden disposals exactly") {
    val events = Vector[OathEvent](
      OathEvent.CampaignRaided(PlayerId("red"), DecisionId("raid-1"),
        CampaignLosingForceResolver.default.id,
        CampaignRaidBoardLoss(PlayerId("blue"), 2, 3),
        Vector(RelicId("R1")), Vector(CampaignBanner.PeoplesFavor),
        Vector(DenizenId("D1"), VisionId("V1")), Region.Provinces,
        Some(CampaignRules.Conspiracy), Vector(RelicId("R2")),
        favorBurned = 2, bannerFavorReturned = Map(Suit.Order -> 2),
        darkestSecretBurned = 3),
      OathEvent.CampaignRaidPawnRelocated(PlayerId("red"), DecisionId("raid-1"),
        PlayerId("blue"), SiteId("S1"), SiteId("S2")))
    val encoded = GameEventWire.encodeStream("raid", catalogRef,
      events.zipWithIndex.map { case (event, i) => RecordedEvent(i.toLong, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
    val tampered = ujson.read(encoded).arr
    tampered.head("payload")("takenBanners")(0) = "unknown"
    assert(GameEventWire.decodeStream(ujson.write(tampered)).isLeft)

    val negativeBurn = ujson.read(encoded).arr
    negativeBurn.head("payload")("darkestSecretBurned") = -1
    assert(GameEventWire.decodeStream(ujson.write(negativeBurn)).isLeft)

    val fractionalBurn = ujson.read(encoded).arr
    fractionalBurn.head("payload")("darkestSecretBurned") = 1.5
    assert(GameEventWire.decodeStream(ujson.write(fractionalBurn)).isLeft)
  }

  test("current pre-release Campaign events round-trip exact dice and choices") {
    val events = Vector[OathEvent](
      OathEvent.CampaignStarted(PlayerId("red"), DecisionId("campaign-1"),
        SiteId("site"), 2, 2),
      OathEvent.CampaignPlanChosen(PlayerId("red"), DecisionId("campaign-1"),
        PendingProcedure.CampaignPlanSource.Relic(PlayerId("red"),
          RelicId("R25")), "relic.brass-army",
        PendingProcedure.CampaignPlanSide.Attacker,
        Vector(PendingProcedure.CampaignPlanCost.Secret(1)),
        Vector(PendingProcedure.CampaignPlanEffect.AddAttackDice(4))),
      OathEvent.CampaignPlansFinished(PlayerId("red"), DecisionId("campaign-1"),
        PendingProcedure.CampaignPlanSide.Attacker,
        Vector(PendingProcedure.CampaignPlanSource.Relic(PlayerId("red"),
          RelicId("R25"))), Vector("relic.brass-army"),
        Vector(PendingProcedure.CampaignPlanEffect.AddAttackDice(4)),
        Vector.fill(6)(AttackDieFace.OneSword), attack = 6, skullLosses = 0),
      OathEvent.CampaignPlanChosen(PlayerId("blue"), DecisionId("campaign-1"),
        PendingProcedure.CampaignPlanSource.Title(PlayerId("blue")),
        "title.oathkeeper-defense", PendingProcedure.CampaignPlanSide.Defender,
        Vector.empty, Vector(PendingProcedure.CampaignPlanEffect.AddDefenseDice(1))),
      OathEvent.CampaignSacrificed(PlayerId("red"), DecisionId("campaign-1"),
        1, Vector(DefenseDieFace.OneShield), 3, 4, 1, victorious = false),
      OathEvent.CampaignConquered(PlayerId("red"), DecisionId("campaign-2"),
        CampaignLosingForceResolver.removeAllBandits.id,
        Vector(CampaignLosingForceEffect.Remove(SiteId("site"),
          ForceKind.Bandit, 2)),
        Vector(CampaignForceAllocation(SiteId("site"), 1))),
      OathEvent.BanditsRefilled(Vector(SiteId("empty") -> 2)))
    val encoded = GameEventWire.encodeStream("campaign", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index.toLong, event) })
      .toOption.get
    val decoded = GameEventWire.decodeStream(encoded).toOption.get
    assertEquals(decoded.map(_.formatVersion), Vector.fill(events.size)(7))
    assertEquals(decoded.map(_.event), events)
    val tampered = ujson.read(encoded).arr
    tampered(2)("payload")("attackDice")(0) = "unknown-face"
    assert(GameEventWire.decodeStream(ujson.write(tampered)).isLeft)
    val duplicate = ujson.read(encoded).arr
    val source = duplicate(2)("payload")("orderedSources")(0)
    duplicate(2)("payload")("orderedSources") = ujson.Arr(source, source)
    assert(GameEventWire.decodeStream(ujson.write(duplicate)).isLeft)
  }

  test("v7 Recover events round-trip exact dice costs and chosen relic") {
    val events = Vector[OathEvent](
      RecoverRolled(PlayerId("red"), DecisionId("recover-1"), SiteId("site"), 1,
        Vector(DefenseDieFace.OneShield, DefenseDieFace.Doubler)),
      RecoverStopped(PlayerId("red"), DecisionId("recover-1")),
      RelicRecovered(PlayerId("red"), DecisionId("recover-2"), SiteId("site"),
        RelicId("relic")))
    val encoded = GameEventWire.encodeStream("recover", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index.toLong, event) })
      .toOption.get
    val decoded = GameEventWire.decodeStream(encoded).toOption.get
    assertEquals(decoded.map(_.formatVersion), Vector(7, 7, 7))
    assertEquals(decoded.map(_.event), events)
    val tampered = ujson.read(encoded).arr
    tampered.head("payload")("dice")(0) = "opaque-integer"
    assert(GameEventWire.decodeStream(ujson.write(tampered)).isLeft)
  }

  test("v6 Economy events round-trip source cost yield and NF resource mode") {
    val denizen = DenizenId(catalog.denizens.head.id.value)
    val edifice = EdificeId(catalog.edifices.head.id.value)
    val site = catalog.sites.head.id
    val events = Vector[OathEvent](
      Mustered(PlayerId("red"), site, EconomyTargetRef.Denizen(denizen),
        Suit.Order, 1, 3),
      Traded(PlayerId("red"), site, EconomyTargetRef.Edifice(edifice),
        Suit.Hearth, TradeResource.Secret, 1, 2))
    val encoded = GameEventWire.encodeStream("economy", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).fold(error => fail(error.toString), identity)
    val decoded = GameEventWire.decodeStream(encoded)
      .fold(error => fail(error.toString), identity)
    assertEquals(decoded.map(_.formatVersion), Vector(6, 6))
    assertEquals(decoded.map(_.event), events)
    val invalid = ujson.read(encoded).arr
    invalid.head("payload")("target")("kind") = "relic"
    assert(GameEventWire.decodeStream(ujson.write(invalid)).isLeft)
  }

  test("v5 Rest events round-trip all authoritative transition facts") {
    val events = Vector[OathEvent](
      RestStarted(PlayerId("red")),
      RestCompleted(PlayerId("red"), Map(Suit.Beast -> 2, Suit.Order -> 1),
        returnedSecrets = 3, refreshedSupply = 6, PlayerId("blue"), 2,
        usurperLimited = true)
    )
    val encoded = GameEventWire.encodeStream("rest", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    val decoded = GameEventWire.decodeStream(encoded).toOption.get

    assertEquals(decoded.map(_.formatVersion), Vector(5, 5))
    assertEquals(decoded.map(_.event), events)
    assertEquals(decoded.map(_.eventType), Vector(
      GameEventWire.RestStartedType, GameEventWire.RestCompletedType))
  }

  test("v5 Rest numeric facts require exact non-negative Int values") {
    def restValue(): ujson.Obj = GameEventWire.encodeEvent(
      "rest", catalogRef, 0L,
      RestCompleted(PlayerId("red"), Map(Suit.Beast -> 2), 3, 6,
        PlayerId("blue"), 2, usurperLimited = true)).toOption.get.obj
    def reject(field: String, value: Double, expected: WireError): Unit = {
      val encoded = restValue()
      encoded("payload")(field) = ujson.Num(value)
      assertEquals(GameEventWire.decode(encoded), Left(expected))
    }
    val safeRange = s"must be between 0 and ${GameEventWire.MaxSafeSequence} inclusive"
    val intRange = s"must be between 0 and ${Int.MaxValue} inclusive"

    reject("returnedSecrets", 1.5,
      WireError.WrongType("$.payload.returnedSecrets", "expected an integer"))
    reject("refreshedSupply", -1,
      WireError.InvalidValue("$.payload.refreshedSupply", safeRange))
    reject("completedRound", Double.PositiveInfinity,
      WireError.WrongType("$.payload.completedRound", "expected an integer"))
    reject("returnedSecrets", GameEventWire.MaxSafeSequence.toDouble + 1,
      WireError.InvalidValue("$.payload.returnedSecrets", safeRange))
    reject("completedRound", Int.MaxValue.toDouble + 1,
      WireError.InvalidValue("$.payload.completedRound", intRange))

    val favor = restValue()
    favor("payload")("returnedFavor")("beast") =
      ujson.Num(Int.MaxValue.toDouble + 1)
    assertEquals(GameEventWire.decode(favor), Left(WireError.InvalidValue(
      "$.payload.returnedFavor.beast", intRange)))
  }

  test("v2 serialized replay equals command state and preserves ordering") {
    val (commandState, events) = execute(rules)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val json = GameEventWire
      .encodeStream("first-game-001", catalogRef, records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get
    val replayRecords = decoded.map { envelope =>
      RecordedEvent(envelope.sequence, envelope.event)
    }

    assertEquals(decoded.map(_.formatVersion).distinct, Vector(2))
    assertEquals(
      decoded.map(_.eventType),
      Vector(
        GameEventWire.FirstGameStartedType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.FirstGameCompletedType
      )
    )
    assertEquals(
      new EventReplayEngine(rules).replay(replayRecords),
      Right(commandState)
    )
  }

  test("v2 rejects catalog disagreement and non-contiguous order") {
    val events = execute(rules)._2
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    assert(
      GameEventWire
        .encodeStream(
          "game",
          catalogRef.copy(version = "other"),
          records
        )
        .left
        .toOption
        .get
        .isInstanceOf[WireError.CatalogMismatch]
    )
    assert(
      GameEventWire
        .encodeStream(
          "game",
          catalogRef,
          records.updated(1, records(1).copy(index = 3))
        )
        .left
        .toOption
        .get
        .isInstanceOf[WireError.InvalidSequence]
    )
  }

  test("decoded v2 streams reject duplicate missing and out-of-order positions") {
    Vector(
      Vector(0L, 0L) -> WireError.InvalidSequence("$[1].sequence", 1L, 0L),
      Vector(0L, 2L) -> WireError.InvalidSequence("$[1].sequence", 1L, 2L),
      Vector(1L, 0L) -> WireError.InvalidSequence("$[1].sequence", 2L, 0L)
    ).foreach { case (positions, expected) =>
      val values = positions.map { sequence =>
        val value = completedValue()
        value("sequence") = ujson.Num(sequence.toDouble)
        value
      }
      assertEquals(
        GameEventWire.decodeStream(ujson.write(ujson.Arr.from(values))),
        Left(expected)
      )
    }
  }

  test("malformed unsupported and unknown v2 envelopes fail explicitly") {
    assert(GameEventWire.decodeStream("{").left.toOption.get
      .isInstanceOf[WireError.MalformedJson])

    val unsupported = completedValue()
    unsupported("formatVersion") = 1
    assertEquals(
      GameEventWire.decode(unsupported),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 1, 2))
    )

    val unknown = completedValue()
    unknown("eventType") = "scala.internal.Event"
    assertEquals(
      GameEventWire.decode(unknown),
      Left(WireError.UnknownEventType(
        "$.payload.eventType",
        "scala.internal.Event"
      ))
    )
  }

  test("single-event and batch writers preserve nonzero absolute sequences") {
    val single = GameEventWire
      .encodeEvent("game", catalogRef, 41L, FirstGameCompleted)
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(single).toOption.get.sequence,
      41L
    )

    val events = execute(rules)._2.take(2)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(41L + index, event)
    }
    val json = GameEventWire
      .encodeStream("game", catalogRef, 41L, records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get

    assertEquals(decoded.map(_.sequence), Vector(41L, 42L))
    assertEquals(decoded.map(_.event), events)
  }

  test("Campaign losing-force policy effects round-trip without narrowing") {
    val effects = Vector[CampaignLosingForceEffect](
      CampaignLosingForceEffect.Preserve(SiteId("a"), ForceKind.Bandit, 2),
      CampaignLosingForceEffect.Relocate(SiteId("b"), SiteId("c"),
        ForceKind.Exile(LineageId("red")), 3),
      CampaignLosingForceEffect.Replace(SiteId("d"), ForceKind.Imperial, 1,
        Some(ForceKind.Bandit), 2),
      CampaignLosingForceEffect.ReturnToBoard(SiteId("d"), PlayerId("red"),
        ForceKind.Exile(LineageId("red")), 1),
      CampaignLosingForceEffect.KillCommitted(SiteId("a"), PlayerId("red"),
        ForceKind.Exile(LineageId("red")), 1),
      CampaignLosingForceEffect.PreserveCommitted(SiteId("a"), PlayerId("red"),
        ForceKind.Exile(LineageId("red")), 1),
      CampaignLosingForceEffect.RelocateCommitted(SiteId("c"), PlayerId("red"),
        ForceKind.Exile(LineageId("red")), 1))
    val event = OathEvent.CampaignConquered(PlayerId("red"),
      DecisionId("campaign-effects"), "campaign.loss.synthetic", effects,
      Vector(CampaignForceAllocation(SiteId("a"), 0)))
    val encoded = GameEventWire.encodeEvent("campaign", catalogRef, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).toOption.get.event, event)
  }

  test("mixed contiguous v2 setup and v3 gameplay records round trip") {
    val setupEvents = execute(rules)._2
    val gameplay = Vector(
      WealthTaken(PlayerId("p2"), sites.head, WakeResource.Favor),
      WakeEnded(PlayerId("p2"))
    )
    val events = setupEvents ++ gameplay
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val decoded = GameEventWire.decodeStream(
      GameEventWire.encodeStream("mixed", catalogRef, records)
        .toOption.get).toOption.get

    assertEquals(decoded.map(_.formatVersion),
      Vector.fill(setupEvents.size)(2) ++ Vector(3, 3))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.take-wealth", "gameplay.wake-ended"))
    assertEquals(decoded.map(_.event), events)

    val wrongVersion = GameEventWire.encodeEvent(
      "mixed", catalogRef, 8L, gameplay.head).toOption.get
    wrongVersion("formatVersion") = 2
    assert(GameEventWire.decode(wrongVersion).isLeft)
  }

  test("v3 traveled has exact discriminator payload and mixed compatibility") {
    val event = Traveled(
      PlayerId("p2"), SiteId("source"), SiteId("destination"), 3)
    val encoded = GameEventWire.encodeEvent(
      "travel", catalogRef, 10L, event).toOption.get
    assertEquals(encoded("formatVersion").num.toInt, 3)
    assertEquals(encoded("eventType").str, "gameplay.traveled")
    assertEquals(encoded("payload")("sourceSiteId").str, "source")
    assertEquals(encoded("payload")("destinationSiteId").str, "destination")
    assertEquals(encoded("payload")("supplySpent").num.toInt, 3)
    assertEquals(GameEventWire.decode(encoded).toOption.get.event, event)

    val events = execute(rules)._2 ++ Vector(
      WakeEnded(PlayerId("p2")), event)
    val records = events.zipWithIndex.map { case (value, index) =>
      RecordedEvent(index.toLong, value)
    }
    val decoded = GameEventWire.decodeStream(
      GameEventWire.encodeStream("travel", catalogRef, records)
        .toOption.get).toOption.get
    assertEquals(decoded.map(_.formatVersion).takeRight(2), Vector(3, 3))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.wake-ended", "gameplay.traveled"))

    encoded("payload")("supplySpent") = -1
    assert(GameEventWire.decode(encoded).isLeft)
  }

  test("current state-based Oathkeeper and Usurper events round trip") {
    val events = Vector[OathEvent](
      OathkeeperChanged(Some(PlayerId("p2"))),
      UsurperFlipped(PlayerId("p2")),
      UsurperVictory(PlayerId("p2")),
      OathkeeperRecipientChoiceStarted(PlayerId("p3"), DecisionId("oath-1"),
        Vector(PlayerId("p1"), PlayerId("p2"))),
      OathkeeperRecipientChosen(PlayerId("p3"), DecisionId("oath-1"),
        PlayerId("p2")))
    val encoded = events.zipWithIndex.map { case (event, index) =>
      GameEventWire.encodeEvent("oath", catalogRef, 20L + index, event)
        .toOption.get
    }
    assertEquals(encoded.map(_("formatVersion").num.toInt), Vector.fill(5)(7))
    assertEquals(encoded.map(value => GameEventWire.decode(value).toOption.get.event),
      events)
    val noHolder = GameEventWire.encodeEvent("oath", catalogRef, 23L,
      OathkeeperChanged(None)).toOption.get
    assertEquals(GameEventWire.decode(noHolder).toOption.get.event,
      OathkeeperChanged(None))
  }

  test("setup history preserves every Oathkeeper goal") {
    OathkeeperGoal.all.foreach { goal =>
      val event = OathEvent.FirstGameStarted(plan.copy(oathkeeperGoal = goal))
      val encoded = GameEventWire.encodeEvent("goal", catalogRef, 0L, event)
        .toOption.get
      assertEquals(encoded("payload")("oathkeeperGoal").str, goal.key)
      assertEquals(GameEventWire.decode(encoded).toOption.get.event, event)
    }
    val invalid = GameEventWire.encodeEvent("goal", catalogRef, 0L,
      OathEvent.FirstGameStarted(plan)).toOption.get
    invalid("payload")("oathkeeperGoal") = "unknown"
    assert(GameEventWire.decode(invalid).left.toOption.exists {
      case WireError.InvalidValue(path, _) => path.endsWith(".oathkeeperGoal")
      case _ => false
    })
  }

  test("v4 Search events round trip exact hidden outcome and player choices") {
    val drawn = Vector[WorldCardId](DenizenId("denizen:a"), VisionId("vision:b"))
    val started = SearchStarted(PlayerId("p2"), DecisionId("search-9"),
      SearchSource.WorldDeck, Region.Cradle, 3, drawn)
    val completed = SearchCompleted(PlayerId("p2"), DecisionId("search-9"),
      drawn.head, Vector(drawn(1)),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    val encoded = Vector(started, completed).zipWithIndex.map {
      case (event, index) => GameEventWire.encodeEvent(
        "search", catalogRef, 9L + index, event).toOption.get
    }
    assertEquals(encoded.map(_("formatVersion").num.toInt), Vector(4, 4))
    assertEquals(encoded.map(_("eventType").str),
      Vector("gameplay.search-started", "gameplay.search-completed"))
    assertEquals(encoded.map(value => GameEventWire.decode(value)
      .toOption.get.event), Vector(started, completed))
    val fixture = scala.io.Source.fromResource(
      "serialization/search-event-stream-v4.json").mkString.trim
    assertEquals(ujson.read(fixture), ujson.Arr.from(encoded))

    encoded.head("payload")("drawn")(0)("id") = "denizen:tampered"
    assert(GameEventWire.decode(encoded.head).isRight)
    // Wire decoding preserves the recorded outcome; authoritative replay is
    // responsible for rejecting disagreement with the deck.
  }

  test("format version and sequence reject fractional and nonfinite numbers") {
    Vector(2.9, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = completedValue()
      value("formatVersion") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(
          WireError.WrongType(
            "$.formatVersion",
            "expected an integer"
          )
        )
      )
    }

    Vector(0.5, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = completedValue()
      value("sequence") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(WireError.WrongType("$.sequence", "expected an integer"))
      )
    }
  }

  test("numeric boundaries reject negatives and unsafe integer overflow") {
    val rangeMessage =
      s"must be between 0 and ${GameEventWire.MaxSafeSequence} inclusive"

    Vector(
      -1d,
      (GameEventWire.MaxSafeSequence + 1L).toDouble
    ).foreach { number =>
      val value = completedValue()
      value("sequence") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(WireError.InvalidValue("$.sequence", rangeMessage))
      )
    }

    val negativeVersion = completedValue()
    negativeVersion("formatVersion") = ujson.Num(-1)
    assertEquals(
      GameEventWire.decode(negativeVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    val overflowVersion = completedValue()
    overflowVersion("formatVersion") =
      ujson.Num((GameEventWire.MaxSafeSequence + 1L).toDouble)
    assertEquals(
      GameEventWire.decode(overflowVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    assert(
      GameEventWire
        .encodeEvent("game", catalogRef, -1L, FirstGameCompleted)
        .isLeft
    )
    assert(
      GameEventWire
        .encodeEvent(
          "game",
          catalogRef,
          GameEventWire.MaxSafeSequence + 1L,
          FirstGameCompleted
        )
        .isLeft
    )

    val boundary = GameEventWire
      .encodeEvent(
        "game",
        catalogRef,
        GameEventWire.MaxSafeSequence,
        FirstGameCompleted
      )
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(boundary).toOption.get.sequence,
      GameEventWire.MaxSafeSequence
    )
  }

  private def completedValue(): ujson.Value =
    GameEventWire
      .encodeEvent("game", catalogRef, 0L, FirstGameCompleted)
      .toOption
      .get
}
