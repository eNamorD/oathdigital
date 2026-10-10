package oathdigital.serialization

import oathdigital.gameplay.actions.VisionRules
import oathdigital.model._
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.walker.{DeltaMeaning, RollPayload,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.serialization.WalkerWireEvents.{encodedStream,
  encodedStreamThatRoundTrips, recordedOperation}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap.{accepted, as}

/** Walker step events on the wire: the operations, locations, pieces and
  * payloads a recorded step carries, and the typed errors for ones the codec
  * cannot write. */
class WalkerOperationWireSuite extends munit.FunSuite:
  test("recorded Visions Drawn advancement round trips without an amount " +
      "and replays as one more Vision drawn"):
    val event = recordedOperation("vision", AdvanceVisionsDrawn)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assertEquals(ujson.read(encoded)("payload")("ops")(0),
      ujson.Obj("kind" -> "advance-visions-drawn"))
    val decoded = as[WalkerStepRecorded](
      accepted(GameEventWire.decode(encoded)).event)
    val base = Table.start.ready
    val replayed = accepted(OperationPipeline.run(base, decoded.ops,
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state
    assertEquals(replayed.game.current.tracks.visionsDrawn,
      base.game.current.tracks.visionsDrawn + 1)

  test("reduced optional spend is canonical in memory and on the wire"):
    val withOneSupply = Table.start.supply(Table.p1, 1).ready
    val run = accepted(OperationPipeline.run(withOneSupply,
      Vector(SpendSupply(Table.p1, 3, required = false)),
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    val event = WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
      DeltaMeaning.SupplySpent(Table.p1, 1)), run.executed, Vector.empty)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assert(!ujson.read(encoded)("payload")("ops")(0).obj.contains("required"))

  test("a recorded move of a Vision to the shared bank round trips and replays"):
    val actor = Table.p1
    val card = VisionRules.Conspiracy
    val prepared = Table.start.hand(actor, card).ready
    val box = Move(Piece.Card(card), PositionedLocation(Location.Hand(actor)),
      PositionedLocation(Location.SharedBank))
    val event = recordedOperation("box", box)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val decoded = as[WalkerStepRecorded](
      accepted(GameEventWire.decode(encoded)).event)
    val replayed = accepted(OperationPipeline.run(prepared, decoded.ops,
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state
    assertEquals(replayed.game.current.temporaryHands(actor), Vector.empty)

  test("walker delta events round-trip independent semantic facts"):
    val player = PlayerId("red")
    val pool = PoolKey("recover")
    val relic = RelicId("R1")
    val site = SiteId("site")
    val move = Move(Piece.Card(relic),
      PositionedLocation(Location.Site(site)),
      PositionedLocation(Location.PlayArea(player)),
      resultingOrientation = Some(Orientation.FaceDown))
    val events = Vector[OathEvent](
      WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.DicePoolModified(pool, 2)),
        Vector(ModifyDicePool(pool, 2)), Vector.empty),
      WalkerStepRecorded("1.0.1", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.SupplySpent(player, 1)),
        Vector(SpendSupply(player, 1)), Vector.empty),
      WalkerStepRecorded("2.1", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.RelicAcquired(player, relic, site)), Vector(move),
        Vector.empty))

    val encoded = encodedStreamThatRoundTrips("walker", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step")("meaning")("kind").str).toVector,
      Vector("dice-pool-modified", "supply-spent", "relic-acquired"))

  test("a journalled phase change round-trips the phase it names"):
    // `EnterPhase` is only ever declared with `Act` today (End Wake is its
    // one caller), so a codec that ignored the value and decoded `Act`
    // unconditionally would pass every other test in the tree. Rest is used
    // here for exactly that reason.
    val event = recordedOperation("enter-phase", EnterPhase(Phase.Rest))
    val encoded = accepted(GameEventWire.encodeEvent("phase", 0L, event))
    assertEquals(encoded("payload")("ops")(0)("phase").str, "rest")
    assertEquals(accepted(GameEventWire.decode(encoded)).event, event)

  test("Catacombs' recorded batch round-trips: a relic move off the top of " +
      "the relic deck and a cost placed on a card"):
    // The exact op vector `CatacombsContribution` builds. Encoding it used to
    // throw on `Location.Deck` and again on `PayCost`, which the append path
    // turned into a codec failure -- so `StartWalker` + Catacombs, the only
    // end-to-end route to this power once the legacy object goes, could never
    // be persisted.
    val player = PlayerId("red")
    val relic = RelicId("R1")
    val site = SiteId("site")
    val denizen = DenizenId("catacombs")
    val event: OathEvent = WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied(
        "catacombs.place")),
      Vector(
        Move(Piece.Card(relic),
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          PositionedLocation(Location.Site(site)),
          resultingOrientation = Some(Orientation.FaceDown)),
        PayCost(player, Location.OnCard(denizen), Cost(secret = 1))),
      Vector(PowerId("denizen.catacombs")))

    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    val ops = ujson.read(encoded).arr.head("payload")("ops").arr
    assertEquals(ops.map(_("kind").str).toVector, Vector("move", "pay-cost"))
    assertEquals(ops(0)("from")("location")("deck").str, "relic")
    assertEquals(ops(0)("from")("position").str, "top")
    assertEquals(ops(1)("placedAt")("card")("id").str, "catacombs")
    assertEquals(ops(1)("cost")("secret").num, 1d)

  test("every Location variant round-trips through the walker codec"):
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
    val events = locations.map(location => recordedOperation("pay",
      PayCost(player, location, Cost(favor = 1))))

    val encoded = encodedStreamThatRoundTrips("walker", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("placedAt")("kind").str).toVector.distinct.size,
      locations.map(_.getClass.getSimpleName).distinct.size)

  test("an unknown walker location kind, deck, operation kind or negative " +
      "cost decodes to a typed WireError"):
    val player = PlayerId("red")
    val event = recordedOperation("pay",
      PayCost(player, Location.Deck(CardDeck.Relic), Cost(secret = 1)))
    val encoded = encodedStream("walker", Vector(event))
    accepted(GameEventWire.decodeStream(encoded))

    def refusing(edit: ujson.Value => Unit, refusal: WireError) =
      (edit, refusal)
    Vector(
      refusing(_("payload")("ops")(0)("placedAt")("kind") = "not-a-location",
        WireError.InvalidValue("$[0].payload.ops[0].placedAt.kind",
          "unknown recorded walker location 'not-a-location'")),
      refusing(_("payload")("ops")(0)("placedAt")("deck") = "not-a-deck",
        WireError.InvalidValue("$[0].payload.ops[0].placedAt.deck",
          "unknown card deck 'not-a-deck'")),
      refusing(_("payload")("ops")(0)("cost")("secret") = -1,
        WireError.InvalidValue("$[0].payload.ops[0].cost.secret",
          "must be between 0 and 9007199254740991 inclusive")),
      refusing(_("payload")("ops")(0)("kind") = "not-an-operation",
        WireError.InvalidValue("$[0].payload.ops[0].kind",
          "unknown recorded walker operation 'not-an-operation'"))
    ).foreach { (corrupt, refusal) =>
      val injected = ujson.read(encoded).arr
      corrupt(injected(0))
      assertEquals(GameEventWire.decodeStream(ujson.write(injected)),
        Left(refusal), ujson.write(injected))
    }

  test("a draw written before it had a position decodes as a draw from the " +
      "top, and a draw of no position is refused"):
    val player = PlayerId("red")
    val draw = Draw(player, Vector(DenizenId("denizen")),
      Location.RegionalDiscard(Region.Cradle), Location.Hand(player))
    val encoded = encodedStream("walker",
      Vector(recordedOperation("draw", draw)))
    def withPosition(edit: ujson.Value => Unit): String =
      val stream = ujson.read(encoded).arr
      edit(stream(0)("payload")("ops")(0))
      ujson.write(stream)
    assertEquals(ujson.read(encoded).arr(0)("payload")("ops")(0)("position")
      .str, "top")
    val oldJournal = withPosition(_.obj.remove("position"))
    assertEquals(accepted(GameEventWire.decodeStream(oldJournal))
      .map(_.event), Vector(recordedOperation("draw", draw)))
    assertEquals(GameEventWire.decodeStream(withPosition(
      _("position") = "unspecified")), Left(WireError.InvalidValue(
        "$[0].payload.ops[0].position",
        "a draw takes from the top or the bottom")))

  test("a WalkerStepRecorded carrying two contribution ids round-trips"):
    val pool = PoolKey("recover")
    val event: OathEvent = WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.DicePoolModified(pool, 2)),
      Vector(ModifyDicePool(pool, 2)),
      Vector(PowerId("power.one"), PowerId("power.two")))

    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    assertEquals(ujson.read(encoded).arr.head("payload")("contributions").arr
      .map(_.str).toVector, Vector("power.one", "power.two"))

  test("an unencodable walker step payload returns a typed WireError, " +
      "not a thrown exception"):
    // WalkerStepPayload is intentionally open (WalkerEvents.scala) so a
    // later action's payload can't be exhaustively matched at compile time.
    // A shape this codec doesn't know must fail the append path as a
    // WireError, exactly like an unrecognized value on the decode side.

    // Stub of WalkerStepPayload: a payload shape the codec has no case for.
    case object UnknownStepPayload extends WalkerStepPayload
    val event = WalkerStepRecorded("0", UnknownStepPayload,
      Vector.empty, Vector.empty)
    GameEventWire.encodeEvent("walker", 0, event) match
      case Left(_) => ()
      case Right(value) => fail(s"expected a WireError, got $value")

  test("every CoreOperation variant round-trips through the walker codec"):
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
    val legacy = LegacyId("legacy")
    val lineage = LineageId("lineage")
    val operations: Vector[CoreOperation] = Vector(
      GainSupply(player, 2),
      SpendSupply(player, 1),
      ModifyDicePool(PoolKey("recover"), 2),
      RecordPowerUse(PowerUseRef(PowerTiming.Wake,
        PowerSourceRef.Site(site), PowerId("site.take-wealth"))),
      RecordPowerUse(PowerUseRef(PowerTiming.Rest,
        PowerSourceRef.Card(denizen), PowerId("denizen.silver-tongue"))),
      RecordPowerUse(PowerUseRef(PowerTiming.Act,
        PowerSourceRef.Card(relic), PowerId("relic.test-power"))),
      RecordPowerUse(PowerUseRef(PowerTiming.Act,
        PowerSourceRef.Card(legacy), PowerId("legacy.test-power"))),
      EnterPhase(Phase.Act),
      BeginTurn(player, Phase.Wake),
      BeginTurn(other, Phase.RoundEnd),
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
      Draw(player, Vector(denizen), Location.RegionalDiscard(Region.Cradle),
        Location.Hand(player), StackPosition.Bottom),
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
      Reroll(PoolKey("recover"), Vector(0, 2),
        Some(Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank))),
      Reroll(PoolKey("campaign.attack"), Vector(1)),
      SetDieFaces(PoolKey("campaign.defense"),
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)),
      RecordCampaignResult(CampaignResult(player, CampaignKind.Conquest,
        CampaignDefender.Bandits, Vector(site), Vector.empty, force = 2,
        attackFaces = Vector(AttackDieFace.OneSword), attackScore = 1,
        skullLosses = 0, sacrificed = 0,
        defenseFaces = Vector(DefenseDieFace.Blank), defenseScore = 2,
        attackerWins = false)),
      Shuffle(SearchSource.WorldDeck, Some(Vector(denizen, vision))),
      Shuffle(SearchSource.RegionalDiscard(Region.Cradle), Some(Vector(vision))),
      Shuffle(SearchSource.RegionalDiscard(Region.Hinterland)),
      SetOathkeeper(Some(PlayerId("red"))),
      SetOathkeeper(None),
      QueueProcedure(QueuedProcedure(PlayerId("p1"), ActionRef.Campaign,
          Vector.empty,
          Some(PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))),
          QueueKind.Offer)),
      QueueProcedure(QueuedProcedure(PlayerId("p1"), ActionRef.Trade,
        Vector(DecisionOptionRef.Button("favor")), None, QueueKind.Run),
        atFront = true))

    val events = operations.map(operation =>
      recordedOperation("op", operation))

    val encoded = encodedStreamThatRoundTrips("walker-ops", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("kind").str).toVector.distinct.size,
      operations.map(_.getClass.getSimpleName).distinct.size)

  test("a walker roll payload round-trips attack and defense faces"):
    val events = Vector[OathEvent](
      WalkerStepRecorded("1", RollPayload(PoolKey("campaign.attack"),
        Vector(AttackDieFace.HollowSword, AttackDieFace.OneSword,
          AttackDieFace.TwoSwordsSkull)), Vector.empty, Vector.empty),
      WalkerStepRecorded("2", RollPayload(PoolKey("campaign.defense"),
        Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank),
        automatic = true), Vector.empty, Vector.empty))
    val encoded = encodedStreamThatRoundTrips("walker-rolls", events)
    // `automatic` is written only when set, so a parked roll's wire form is
    // unchanged.
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step").obj.contains("automatic")).toVector,
      Vector(false, true))

  test("every Piece variant round-trips through the walker codec"):
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
    val events = pieces.map(piece => recordedOperation("take",
      Take(piece, player, Location.SharedBank, Location.PlayArea(player))))

    val encoded = encodedStreamThatRoundTrips("walker-pieces", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("piece")("kind").str).toVector.distinct.size,
      pieces.map(_.getClass.getSimpleName).distinct.size)

  test("a Take's leaving operations round trip, and a Take without them " +
      "writes none"):
    val player = PlayerId("red")
    val holder = PlayerId("blue")
    val leaving = Vector[CoreOperation](
      Move(Piece.Favor(1),
        PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order))),
      Burn.secrets(2, PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))
    val takes = Vector(
      Take(Piece.Banner(Banner.PeoplesFavor), player, Location.PlayArea(holder),
        Location.PlayArea(player), leaving = leaving),
      Take(Piece.Banner(Banner.DarkestSecret), player, Location.PlayArea(holder),
        Location.PlayArea(player)))
    val events = takes.map(take => recordedOperation("take", take))
    val encoded = encodedStreamThatRoundTrips("take-leaving", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("ops")(0))
    assertEquals(ops(0)("leaving").arr.size, 2)
    assert(!ops(1).obj.contains("leaving"))

  test("a walker tree-control node can never be recorded, and surfaces a " +
      "typed WireError instead of an opaque exception"):
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
      Repeat((_, _) => false, SpendSupply(player, 1)),
      Branch((_, _) => Vector.empty),
      Sequence(Vector(SpendSupply(player, 1))))
    nodes.foreach { node =>
      val event = recordedOperation("bad", node)
      GameEventWire.encodeEvent("walker", 0, event) match
        case Left(_: WireError) => ()
        case Right(value) => fail(s"expected a typed WireError for $node, " +
          s"got $value")
    }

  test("a deferral and its take round trip, with the held ops"):
    val key = DeferralKey("test.held")
    val held = Vector[CoreOperation](ModifyDicePool(PoolKey("test.pool"), 2),
      Gain.Secrets(Table.p1, 1))
    Vector[CoreOperation](Defer(key, held), TakeDeferred(key)).foreach { op =>
      val event = recordedOperation("held", op)
      val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }
    val deferral = accepted(GameEventWire.encodeEvent("walker", 0,
      recordedOperation("held", Defer(key, held))))
    assertEquals(ujson.read(deferral)("payload")("ops")(0)("kind").str,
      "defer")
