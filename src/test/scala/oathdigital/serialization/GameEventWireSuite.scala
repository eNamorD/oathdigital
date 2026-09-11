package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.{CampaignLosingForceResolver, CampaignRules}
import oathdigital.gameplay._
import oathdigital.gameplay.setup._
import oathdigital.model._
import oathdigital.gameplay.OathEvent.{FirstGameCompleted, Mustered, Traded, WakeEnded,
  RestCompleted, RestStarted, SearchCompleted, SearchStarted, Traveled,
  WealthTaken}
import oathdigital.gameplay.operations.{AdjustSupply, BuildOps, Branch, Burn,
  BuryableCard, Bury, ClearDicePool, CoreOperation, Cost, Decide,
  Discard, Draw, Exchange, Flip, FlipSecrets, Gain, Give, Kill, Location,
  ModifyDicePool, ModifyRollOutcome, Move, PayCost, Peek, Piece, Play,
  PositionedLocation, Repeat, Replace, Reveal, Roll, Sacrifice, SecretSide,
  Sequence, StackPosition, Swap, Take}
import oathdigital.gameplay.walker.{ChoicePayload, DeltaMeaning,
  WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.OathEvent.{OathkeeperChanged, UsurperFlipped,
  UsurperVictory, OathkeeperRecipientChoiceStarted,
  OathkeeperRecipientChosen, RoundEnded, WarExhaustionResolved}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}

class GameEventWireSuite extends munit.FunSuite {
  test("ignored-rule diagnostics round trip durable source timing and reason") {
    val event = OathEvent.IgnoredRulesRecorded(PlayerId("red"),
      MajorActionKind.Rest, Vector(IgnoredRuleDiagnostic(
        RuleSourceRef.Adviser(PlayerId("red"), DenizenId("insomnia")),
        "denizen.insomnia", MajorActionKind.Rest, RuleTiming.Trigger,
        "reviewed-unimplemented-pre-alpha-fallback")))
    val encoded = GameEventWire.encodeEvent("g", catalog.ref, 0, event).toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
  }
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
      assertEquals(decoded.formatVersion, GameEventWire.FormatVersion)
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
        Vector(Suit.Order)),
      OathEvent.ConspiracyCompleted(red, decision, conspiracy, Some(target),
        Vector(Suit.Order, Suit.Beast)),
      OathEvent.VisionVictory(red, VisionId("vision:vision-of-faith")))
    val encoded = GameEventWire.encodeStream("visions", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(1))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)

    val banner = OathEvent.ConspiracyStarted(red, decision, conspiracy,
      Some(ConspiracyTarget.Banner(PlayerId("blue"), Banner.DarkestSecret)),
      Vector.empty)
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
      Vector.fill(events.size)(1))
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
      Vector.fill(events.size)(1))
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
      Vector.fill(4)(1))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("both generic walker decision answers round trip on the step and on " +
      "the park") {
    // The answer fact rides a walker `ChoicePayload` and the parked
    // `answered` vector, so both directions of `DecisionAnswerCodec` are
    // reached by any journalled walker action.
    val player = PlayerId("red")
    val partition = PartitionAnswer(Vector("1", "2", "3")
      .map(id => DecisionOptionRef.Denizen(DenizenId(s"denizen:$id")))
      .zip(Vector("pay-favor", "pay-secret", "pay-favor"))
      .map { case (option, section) => DecisionPlacement(option, section) })
    val chooseOne = ChooseOneAnswer(DecisionOptionRef.Relic(RelicId("R01")))
    val answered = Vector(Answered("forge.assignment", partition),
      Answered("recover.relic", chooseOne))
    val events = Vector[OathEvent](
      WalkerStepRecorded(player, "1",
        ChoicePayload("forge.assignment", partition), Vector.empty,
        Vector.empty),
      WalkerStepRecorded(player, "2",
        ChoicePayload("recover.relic", chooseOne), Vector.empty, Vector.empty),
      WalkerParked(player, ActionRef.Forge, Vector("2"), answered,
        Vector.empty))
    val encoded = GameEventWire.encodeStream("forge", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)
  }

  test("every option reference kind round trips through a recorded answer") {
    val player = PlayerId("red")
    val refs = Vector[DecisionOptionRef](
      DecisionOptionRef.Button("continue"),
      DecisionOptionRef.Player(PlayerId("blue")),
      DecisionOptionRef.Site(SiteId("site:s1")),
      DecisionOptionRef.Denizen(DenizenId("denizen:d1")),
      DecisionOptionRef.Relic(RelicId("R01")),
      DecisionOptionRef.Vision(VisionId("vision:v1")),
      DecisionOptionRef.Deck(CardDeck.Relic))
    val events = refs.zipWithIndex.map { case (ref, index) =>
      WalkerStepRecorded(player, index.toString,
        ChoicePayload("d", ChooseOneAnswer(ref)), Vector.empty,
        Vector.empty): OathEvent }
    val encoded = GameEventWire.encodeStream("refs", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
  }

  test("a journalled answer carrying a deleted legacy tag is rejected with a " +
      "typed decode failure") {
    val player = PlayerId("red")
    val event: OathEvent = WalkerStepRecorded(player, "1",
      ChoicePayload("recover.choice",
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))),
      Vector.empty, Vector.empty)
    val encoded = GameEventWire.encodeStream("legacy", catalogRef,
      Vector(RecordedEvent(0L, event))).toOption.get
    Vector("recover-choice", "recover-relic", "forge-assignment").foreach { tag =>
      val mutated = ujson.read(encoded).arr
      mutated.head("payload")("step")("payload")("kind") = tag
      assert(GameEventWire.decodeStream(ujson.write(mutated)).isLeft,
        s"a '$tag' answer must not decode")
    }
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
          RelicId("R25")), "relic.brass-army.campaign",
        PendingProcedure.CampaignPlanSide.Attacker,
        Vector(PendingProcedure.CampaignPlanCost.Secret(1)),
        Vector(PendingProcedure.CampaignPlanEffect.AddAttackDice(4))),
      OathEvent.CampaignPlansFinished(PlayerId("red"), DecisionId("campaign-1"),
        PendingProcedure.CampaignPlanSide.Attacker,
        Vector(PendingProcedure.CampaignPlanSource.Relic(PlayerId("red"),
          RelicId("R25"))), Vector("relic.brass-army.campaign"),
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
    assertEquals(decoded.map(_.formatVersion), Vector.fill(events.size)(1))
    assertEquals(decoded.map(_.event), events)
    val tampered = ujson.read(encoded).arr
    tampered(2)("payload")("attackDice")(0) = "unknown-face"
    assert(GameEventWire.decodeStream(ujson.write(tampered)).isLeft)
    val duplicate = ujson.read(encoded).arr
    val source = duplicate(2)("payload")("orderedSources")(0)
    duplicate(2)("payload")("orderedSources") = ujson.Arr(source, source)
    assert(GameEventWire.decodeStream(ujson.write(duplicate)).isLeft)
  }

  test("walker delta events round-trip independent semantic facts") {
    val player = PlayerId("red")
    val pool = PoolKey("recover")
    val relic = RelicId("R1")
    val site = SiteId("site")
    val move = Move(Piece.Card(relic),
      PositionedLocation(Location.Site(site)),
      PositionedLocation(Location.PlayArea(player)),
      resultingOrientation = Some(Orientation.FaceDown))
    val events = Vector[OathEvent](
      WalkerStepRecorded(player, "0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.DicePoolModified(pool, 2)),
        Vector(ModifyDicePool(pool, 2)), Vector.empty),
      WalkerStepRecorded(player, "1.0.1", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.SupplySpent(player, 1)),
        Vector(AdjustSupply(player, -1)), Vector.empty),
      WalkerStepRecorded(player, "2.1", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.RelicAcquired(player, relic, site)), Vector(move),
        Vector.empty))

    val encoded = GameEventWire.encodeStream("walker", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step")("meaning")("kind").str).toVector,
      Vector("dice-pool-modified", "supply-spent", "relic-acquired"))
  }

  test("Catacombs' recorded batch round-trips: a relic move off the top of " +
      "the relic deck and a cost placed on a card") {
    // The exact op vector `CatacombsContribution` builds. Encoding it used to
    // throw on `Location.Deck` and again on `PayCost`, which the append path
    // turned into a codec failure -- so `StartWalker` + Catacombs, the only
    // end-to-end route to this power once the legacy object goes, could never
    // be persisted.
    val player = PlayerId("red")
    val relic = RelicId("R1")
    val site = SiteId("site")
    val denizen = DenizenId("catacombs")
    val event: OathEvent = WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied(
        "catacombs.place")),
      Vector(
        Move(Piece.Card(relic),
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          PositionedLocation(Location.Site(site)),
          resultingOrientation = Some(Orientation.FaceDown)),
        PayCost(player, Location.OnCard(denizen), Cost(secret = 1))),
      Vector(PowerId("denizen.catacombs")))

    val encoded = GameEventWire.encodeStream("walker", catalogRef,
      Vector(RecordedEvent(0L, event))).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      Vector(event))
    val ops = ujson.read(encoded).arr.head("payload")("ops").arr
    assertEquals(ops.map(_("kind").str).toVector, Vector("move", "pay-cost"))
    assertEquals(ops(0)("from")("location")("deck").str, "relic")
    assertEquals(ops(0)("from")("position").str, "top")
    assertEquals(ops(1)("placedAt")("card")("id").str, "catacombs")
    assertEquals(ops(1)("cost")("secret").num, 1d)
  }

  test("every Location variant round-trips through the walker codec") {
    // `encodeLocation` is total over the sealed hierarchy, so this list is
    // the whole of it. Keeping the list exhaustive is what makes the
    // compiler's totality check meaningful on the decode side too: a new
    // variant fails to compile in the encoder and fails this test in the
    // decoder.
    val player = PlayerId("red")
    val locations = Vector[Location](
      Location.Hand(player),
      // Not `player`'s own play area: `PayCost`'s children move the favor out
      // of the payer's play area, and a move to the same location is illegal.
      Location.PlayArea(PlayerId("blue")),
      Location.Site(SiteId("site")),
      Location.OnCard(DenizenId("catacombs")),
      Location.OnBanner(Banner.DarkestSecret),
      Location.FavorBank(Suit.Arcane),
      Location.WarbandBank(ForceKind.Exile(LineageId("lineage"))),
      Location.WarbandBank(ForceKind.Imperial),
      Location.Deck(CardDeck.World),
      Location.Deck(CardDeck.Relic),
      Location.Deck(CardDeck.Edifice),
      Location.Deck(CardDeck.Legacy),
      Location.RegionalDiscard(Region.Cradle),
      Location.SharedBank,
      Location.SetAsideRelics,
      Location.Reliquary,
      Location.Atlas,
      Location.Dispossessed)
    // `PayCost` carries a bare `Location`, so it exercises each variant
    // without the stack-position wrapper or `Move`'s from/to constraints.
    val events = locations.map(location => WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("pay")),
      Vector(PayCost(player, location, Cost(favor = 1))),
      Vector.empty): OathEvent)

    val encoded = GameEventWire.encodeStream("walker", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("placedAt")("kind").str).toVector.distinct.size,
      locations.map(_.getClass.getSimpleName).distinct.size)
  }

  test("an unknown walker location kind, deck or negative cost decodes to a " +
      "typed WireError") {
    val player = PlayerId("red")
    val event: OathEvent = WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("pay")),
      Vector(PayCost(player, Location.Deck(CardDeck.Relic), Cost(secret = 1))),
      Vector.empty)
    val encoded = GameEventWire.encodeStream("walker", catalogRef,
      Vector(RecordedEvent(0L, event))).toOption.get
    assert(GameEventWire.decodeStream(encoded).isRight)

    Vector[ujson.Value => Unit](
      _("payload")("ops")(0)("placedAt")("kind") = "not-a-location",
      _("payload")("ops")(0)("placedAt")("deck") = "not-a-deck",
      _("payload")("ops")(0)("cost")("secret") = -1,
      _("payload")("ops")(0)("kind") = "not-an-operation"
    ).foreach { corrupt =>
      val injected = ujson.read(encoded).arr
      corrupt(injected(0))
      assert(GameEventWire.decodeStream(ujson.write(injected)).isLeft,
        s"expected a typed WireError for ${ujson.write(injected)}")
    }
  }

  test("a WalkerStepRecorded carrying two contribution ids round-trips") {
    val player = PlayerId("red")
    val pool = PoolKey("recover")
    val event: OathEvent = WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.DicePoolModified(pool, 2)),
      Vector(ModifyDicePool(pool, 2)),
      Vector(PowerId("power.one"), PowerId("power.two")))

    val encoded = GameEventWire.encodeStream("walker", catalogRef,
      Vector(RecordedEvent(0L, event))).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      Vector(event))
    assertEquals(ujson.read(encoded).arr.head("payload")("contributions").arr
      .map(_.str).toVector, Vector("power.one", "power.two"))
  }

  test("an unencodable walker step payload returns a typed WireError, " +
      "not a thrown exception") {
    // WalkerStepPayload is intentionally open (WalkerEvents.scala) so a
    // later action's payload can't be exhaustively matched at compile time.
    // A shape this codec doesn't know must fail the append path as a
    // WireError, exactly like an unrecognized value on the decode side.
    case object UnknownStepPayload extends WalkerStepPayload
    val event = WalkerStepRecorded(PlayerId("red"), "0", UnknownStepPayload,
      Vector.empty, Vector.empty)
    GameEventWire.encodeEvent("walker", catalogRef, 0, event) match {
      case Left(_) => ()
      case Right(value) => fail(s"expected a WireError, got $value")
    }
  }

  test("every CoreOperation variant round-trips through the walker codec") {
    // `encodeOperation`/`encodePiece` are total over the sealed hierarchy
    // (I8), exactly like `encodeLocation` above -- this list is every
    // `CoreOperation` case EXCEPT the five walker tree-control nodes
    // (`Decide`/`BuildOps`/`Repeat`/`Branch`/`Sequence`), which can never be
    // a recorded, already-applied operation and are covered by the
    // "surfaces a typed WireError" test below instead.
    val player = PlayerId("red")
    val other = PlayerId("blue")
    val site = SiteId("site")
    val denizen = DenizenId("denizen")
    val vision = VisionId("vision")
    val relic = RelicId("relic")
    val edifice = EdificeId("edifice")
    val lineage = LineageId("lineage")
    val operations: Vector[CoreOperation] = Vector(
      AdjustSupply(player, 2),
      ModifyDicePool(PoolKey("recover"), 2),
      Move(Piece.Card(relic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(site)), Some(Orientation.FaceDown)),
      PayCost(player, Location.OnCard(denizen), Cost(favor = 1)),
      Peek(player, denizen, Location.Site(site)),
      Flip(denizen, Location.Site(site), Orientation.FaceUp),
      FlipSecrets(player, 2, SecretSide.FaceUp, SecretSide.FaceDown),
      Burn.favor(3, PositionedLocation(Location.PlayArea(player))),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.PlayArea(player))),
      Discard.Denizen(denizen, PositionedLocation(Location.Site(site)),
        Region.Cradle, Suit.Arcane, favor = 1, secrets = 1,
        actingPlayer = player),
      Discard.Vision(vision, PositionedLocation(Location.Hand(player)),
        Region.Provinces),
      Discard.RuinedEdifice(edifice, PositionedLocation(Location.Site(site)),
        Suit.Order, favor = 0, secrets = 0, actingPlayer = player),
      Discard.Relic(relic, PositionedLocation(Location.PlayArea(player)),
        secrets = 2, actingPlayer = player),
      Draw(player, Vector(relic), Location.Deck(CardDeck.Relic),
        Location.PlayArea(player)),
      Exchange(
        Give(Piece.Favor(1), player, Location.PlayArea(player),
          Location.PlayArea(other)),
        Give(Piece.Secrets(1), other, Location.PlayArea(other),
          Location.PlayArea(player))),
      Gain.Favor(player, Suit.Beast, 2),
      Gain.Secrets(player, 3),
      Gain.Warbands(player, ForceKind.Imperial, 4),
      Give(Piece.Secrets(2), player, Location.PlayArea(player),
        Location.PlayArea(other)),
      Kill(Piece.Warbands(ForceKind.Imperial, 3),
        PositionedLocation(Location.Site(site))),
      Play(denizen, PositionedLocation(Location.Hand(player)),
        Location.Site(site), Orientation.FaceUp),
      Replace(Piece.Warbands(ForceKind.Imperial, 2),
        Piece.Warbands(ForceKind.Exile(lineage), 2),
        PositionedLocation(Location.Site(site))),
      Reveal(denizen, Location.Site(site)),
      Sacrifice(player, Piece.Warbands(ForceKind.Exile(lineage), 1),
        PositionedLocation(Location.Site(site))),
      Swap(denizen, PositionedLocation(Location.Hand(player)),
        vision, PositionedLocation(Location.PlayArea(player))),
      Take(Piece.Secrets(1), player, Location.SharedBank,
        Location.PlayArea(player)),
      Roll(PoolKey("recover"), DiceSpec(DiceKind.Defense)),
      ModifyRollOutcome(PoolKey("recover"), Some(1), Some(2)),
      ClearDicePool(PoolKey("recover")))

    val events = operations.map(operation => WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("op")),
      Vector(operation), Vector.empty): OathEvent)

    val encoded = GameEventWire.encodeStream("walker-ops", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("kind").str).toVector.distinct.size,
      operations.map(_.getClass.getSimpleName).distinct.size)
  }

  test("every Piece variant round-trips through the walker codec") {
    // `encodePiece` is fully total (I8): unlike `encodeOperation`, it has no
    // throwing arm at all, so this list is the whole of `Piece`. `Take`
    // carries a bare `Piece`, so it exercises each variant the same way
    // `PayCost` exercises `Location` above.
    val player = PlayerId("red")
    val relic = RelicId("relic")
    val lineage = LineageId("lineage")
    val pieces: Vector[Piece] = Vector(
      Piece.Card(relic),
      Piece.Banner(Banner.PeoplesFavor),
      Piece.Pawn(player),
      Piece.Favor(1),
      Piece.Secrets(2),
      Piece.Warbands(ForceKind.Exile(lineage), 3))
    val events = pieces.map(piece => WalkerStepRecorded(player, "0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("take")),
      Vector(Take(piece, player, Location.SharedBank,
        Location.PlayArea(player))), Vector.empty): OathEvent)

    val encoded = GameEventWire.encodeStream("walker-pieces", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("piece")("kind").str).toVector.distinct.size,
      pieces.map(_.getClass.getSimpleName).distinct.size)
  }

  test("a walker tree-control node can never be recorded, and surfaces a " +
      "typed WireError instead of an opaque exception") {
    // Decide/BuildOps/Repeat/Branch/Sequence are `encodeOperation`'s five
    // documented exceptions to totality (I8): three close over a Scala
    // function value with no data representation, and none can legally
    // reach this codec (ProcedureWalker only ever records an
    // ALREADY-APPLIED delta batch, never one of its own control nodes). If
    // one somehow did, this proves the failure is `UnencodableOperation`
    // unwrapped into a typed `WireError` -- not an exception escaping the
    // append path.
    val player = PlayerId("red")
    val nodes: Vector[CoreOperation] = Vector(
      Decide("recover.relic", player, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Relic(DecisionOptionRef.Relic(RelicId("r")))))),
      BuildOps((_, _) => Right(Vector.empty)),
      Repeat((_, _) => false, AdjustSupply(player, 1)),
      Branch((_, _) => Vector.empty),
      Sequence(Vector(AdjustSupply(player, 1))))
    nodes.foreach { node =>
      val event: OathEvent = WalkerStepRecorded(player, "0",
        WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("bad")),
        Vector(node), Vector.empty)
      GameEventWire.encodeEvent("walker", catalogRef, 0, event) match {
        case Left(_: WireError) => ()
        case Right(value) => fail(s"expected a typed WireError for $node, " +
          s"got $value")
      }
    }
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
    assertEquals(decoded.map(_.formatVersion), Vector(1, 1))
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

    assertEquals(decoded.map(_.formatVersion), Vector(1, 1))
    assertEquals(decoded.map(_.event), events)
    assertEquals(decoded.map(_.eventType), Vector(
      GameEventWire.RestStartedType, GameEventWire.RestCompletedType))
  }

  test("League Treaty events round-trip typed sources allocations and ownership") {
    val actor = PlayerId("red")
    val owner = PlayerId("blue")
    val decision = DecisionId("rest-league")
    val power = PowerId("denizen.league-treaty")
    val source = SiteDenizenTarget(SiteId("site-a"), DenizenId("237"))
    val favorSources = Vector[SiteFavorSource](
      SiteFavorSource.Denizen(SiteId("site-a"), DenizenId("237")),
      SiteFavorSource.Edifice(SiteId("site-b"), EdificeId("E1")),
      SiteFavorSource.Relic(SiteId("site-b"), 0))
    val remaining = Vector(RestPowerInvocationRef(
      PowerId("banner.darkest-secret.festival"),
      RestPowerSourceRef.Banner(Banner.DarkestSecret), owner))
    val events = Vector[OathEvent](
      OathEvent.LeagueTreatyDecisionStarted(actor, decision, power, source,
        owner, remaining, favorSources, Suit.all),
      OathEvent.LeagueTreatyResolved(actor, decision, power, source, owner,
        Vector(FavorAllocation(favorSources.head, 1),
          FavorAllocation(favorSources.last, 2)), Suit.Hearth),
      OathEvent.LeagueTreatyDeclined(actor, decision, power, source, owner))
    events.zipWithIndex.foreach { case (event, index) =>
      val encoded = GameEventWire.encodeEvent("rest-power", catalogRef,
        index.toLong, event).toOption.get
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }
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

    assertEquals(decoded.map(_.formatVersion).distinct, Vector(1))
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
    unsupported("formatVersion") = 2
    assertEquals(
      GameEventWire.decode(unsupported),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 2, 1))
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

    assertEquals(decoded.map(_.formatVersion), Vector.fill(events.size)(1))
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
    assertEquals(encoded("formatVersion").num.toInt, 1)
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
    assertEquals(decoded.map(_.formatVersion).takeRight(2), Vector(1, 1))
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
    assertEquals(encoded.map(_("formatVersion").num.toInt), Vector.fill(5)(1))
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
    assertEquals(encoded.map(_("formatVersion").num.toInt), Vector(1, 1))
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
