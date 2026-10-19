package oathdigital.serialization

import oathdigital.gameplay.actions.VisionRules
import oathdigital.model._
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.walker.{DeltaPayload, RollPayload,
  WalkerStepRecorded}
import oathdigital.serialization.WalkerWireEvents.{encodedStream,
  encodedStreamThatRoundTrips, recordedOperation}
import oathdigital.testkit.{CatalogNames, RecordedOps, Table}
import oathdigital.testkit.Unwrap.{accepted, as}

/** Walker step events on the wire: the operations, locations, pieces and
  * payloads a recorded step carries, and the typed errors for ones the codec
  * cannot write. */
class WalkerOperationWireSuite extends munit.FunSuite:
  test("recorded Visions Drawn advancement round trips without an amount " +
      "and replays as one more Vision drawn"):
    val event = recordedOperation(AdvanceVisionsDrawn)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assertEquals(ujson.read(encoded)("payload")("step")("ops")(0),
      ujson.Obj("kind" -> "advance-visions-drawn"))
    val decoded = as[WalkerStepRecorded](
      accepted(GameEventWire.decode(encoded)).event)
    val base = Table.start.ready
    val replayed = accepted(OperationPipeline.run(base, RecordedOps.of(decoded),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state
    assertEquals(replayed.game.current.tracks.visionsDrawn,
      base.game.current.tracks.visionsDrawn + 1)

  test("reduced optional spend is canonical in memory and on the wire"):
    val withOneSupply = Table.start.supply(Table.p1, 1).ready
    val run = accepted(OperationPipeline.run(withOneSupply,
      Vector(SpendSupply(Table.p1, 3, required = false)),
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    val event = WalkerStepRecorded("0", DeltaPayload(run.executed), Vector.empty)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assert(!ujson.read(encoded)("payload")("step")("ops")(0).obj.contains("required"))

  test("a recorded move of a Vision to the shared bank round trips and replays"):
    val actor = Table.p1
    val card = VisionRules.Conspiracy
    val prepared = Table.start.hand(actor, card).ready
    val box = Move(Piece.Card(card), PositionedLocation(Location.Hand(actor)),
      PositionedLocation(Location.SharedBank))
    val event = recordedOperation(box)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val decoded = as[WalkerStepRecorded](
      accepted(GameEventWire.decode(encoded)).event)
    val replayed = accepted(OperationPipeline.run(prepared, RecordedOps.of(decoded),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state
    assertEquals(replayed.game.current.temporaryHands(actor), Vector.empty)

  test("a move names its mover on the wire, and a site card's move " +
      "without one decodes as moved by nobody"):
    val move = Move(Piece.Card(RelicId("R1")),
      PositionedLocation(Location.Site(SiteId("site"))),
      PositionedLocation(Location.PlayArea(Table.p1)),
      resultingOrientation = Some(Orientation.FaceDown), by = Some(Table.p1))
    val event = recordedOperation(move)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val stripped = ujson.read(ujson.write(encoded))
    stripped("payload")("step")("ops")(0).obj.remove("by")
    assertEquals(GameEventWire.decode(stripped).map(_.event),
      Right(recordedOperation(move.copy(by = None))))

  test("an actorless discard says so on the wire"):
    val discard = Discard.Denizen(DenizenId("D1"),
      PositionedLocation(Location.Site(SiteId("site"))), Region.Provinces,
      Suit.Beast, 0, 1, Table.p1, actorless = true)
    val event = recordedOperation(discard)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("a swap names its mover on the wire"):
    val swap = Swap(DenizenId("D1"),
      PositionedLocation(Location.Site(SiteId("here"))), DenizenId("D2"),
      PositionedLocation(Location.Site(SiteId("there"))),
      by = Some(Table.p1))
    val event = recordedOperation(swap)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("walker delta events round-trip the node path each one ran at"):
    val player = PlayerId("red")
    val pool = PoolKey("recover")
    val relic = RelicId("R1")
    val site = SiteId("site")
    val move = Move(Piece.Card(relic),
      PositionedLocation(Location.Site(site)),
      PositionedLocation(Location.PlayArea(player)),
      resultingOrientation = Some(Orientation.FaceDown), by = Some(player))
    val events = Vector[OathEvent](
      WalkerStepRecorded("0", DeltaPayload(Vector(ModifyDicePool(pool, 2))), Vector.empty),
      WalkerStepRecorded("1.0.1", DeltaPayload(Vector(SpendSupply(player, 1))), Vector.empty),
      WalkerStepRecorded("2.1", DeltaPayload(Vector(move)), Vector.empty))

    val encoded = encodedStreamThatRoundTrips("walker", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("nodeId").str).toVector,
      Vector("0", "1.0.1", "2.1"))

  test("a journalled phase change round-trips the phase it names"):
    // `EnterPhase` is only ever declared with `Act` today (End Wake is its
    // one caller), so a codec that ignored the value and decoded `Act`
    // unconditionally would pass every other test in the tree. Rest is used
    // here for exactly that reason.
    val event = recordedOperation(EnterPhase(Phase.Rest))
    val encoded = accepted(GameEventWire.encodeEvent("phase", 0L, event))
    assertEquals(encoded("payload")("step")("ops")(0)("phase").str, "rest")
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
      DeltaPayload(Vector(
        Move(Piece.Card(relic),
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          PositionedLocation(Location.Site(site)),
          resultingOrientation = Some(Orientation.FaceDown)),
        PayCost(player, Location.OnCard(denizen), Cost(secret = 1)))),
      Vector(PowerId("denizen.catacombs")))

    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    val ops = ujson.read(encoded).arr.head("payload")("step")("ops").arr
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
    val events = locations.map(location =>
      recordedOperation(PayCost(player, location, Cost(favor = 1))))

    val encoded = encodedStreamThatRoundTrips("walker", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step")("ops")(0)("placedAt")("kind").str).toVector.distinct.size,
      locations.map(_.getClass.getSimpleName).distinct.size)

  test("an unknown walker location kind, deck, operation kind or negative " +
      "cost decodes to a typed WireError"):
    val player = PlayerId("red")
    val event = recordedOperation(
      PayCost(player, Location.Deck(CardDeck.Relic), Cost(secret = 1)))
    val encoded = encodedStream("walker", Vector(event))
    accepted(GameEventWire.decodeStream(encoded))

    def refusing(edit: ujson.Value => Unit, refusal: WireError) =
      (edit, refusal)
    Vector(
      refusing(_("payload")("step")("ops")(0)("placedAt")("kind") = "not-a-location",
        WireError.InvalidValue("$[0].payload.step.ops[0].placedAt.kind",
          "unknown recorded walker location 'not-a-location'")),
      refusing(_("payload")("step")("ops")(0)("placedAt")("deck") = "not-a-deck",
        WireError.InvalidValue("$[0].payload.step.ops[0].placedAt.deck",
          "unknown card deck 'not-a-deck'")),
      refusing(_("payload")("step")("ops")(0)("cost")("secret") = -1,
        WireError.InvalidValue("$[0].payload.step.ops[0].cost.secret",
          "must be between 0 and 9007199254740991 inclusive")),
      refusing(_("payload")("step")("ops")(0)("kind") = "not-an-operation",
        WireError.InvalidValue("$[0].payload.step.ops[0].kind",
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
      Vector(recordedOperation(draw)))
    def withPosition(edit: ujson.Value => Unit): String =
      val stream = ujson.read(encoded).arr
      edit(stream(0)("payload")("step")("ops")(0))
      ujson.write(stream)
    assertEquals(ujson.read(encoded).arr(0)("payload")("step")("ops")(0)("position")
      .str, "top")
    val oldJournal = withPosition(_.obj.remove("position"))
    assertEquals(accepted(GameEventWire.decodeStream(oldJournal))
      .map(_.event), Vector(recordedOperation(draw)))
    assertEquals(GameEventWire.decodeStream(withPosition(
      _("position") = "unspecified")), Left(WireError.InvalidValue(
        "$[0].payload.step.ops[0].position",
        "a draw takes from the top or the bottom")))

  test("a WalkerStepRecorded carrying two contribution ids round-trips"):
    val pool = PoolKey("recover")
    val event: OathEvent = WalkerStepRecorded("0",
      DeltaPayload(Vector(ModifyDicePool(pool, 2))),
      Vector(PowerId("power.one"), PowerId("power.two")))

    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    assertEquals(ujson.read(encoded).arr.head("payload")("contributions").arr
      .map(_.str).toVector, Vector("power.one", "power.two"))

  test("a delta's operations are written under its payload, and a step with " +
      "a top-level ops key no longer decodes"):
    val event: OathEvent = WalkerStepRecorded("0",
      DeltaPayload(Vector(ModifyDicePool(PoolKey("recover"), 2))),
      Vector.empty)
    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    val written = ujson.read(encoded).arr.head("payload")
    assertEquals(written("step")("ops")(0)("kind").str, "modify-dice-pool")
    assert(!written.obj.contains("ops"), written)

    // The shape written before this change: operations beside the payload
    // rather than inside it. It fails on the key the payload now requires,
    // which is a journal that does not load rather than one that silently
    // replays without its batch.
    val old = ujson.read(encoded)
    val moved = old.arr.head("payload")("step")("ops")
    old.arr.head("payload")("step").obj.remove("ops")
    old.arr.head("payload")("ops") = moved
    // The message is ujson's own wording for a missing key, so assert the
    // path and the error type rather than the text.
    GameEventWire.decodeStream(ujson.write(old)) match
      case Left(WireError.InvalidValue(path, _)) =>
        assertEquals(path, "$[0].payload")
      case other => fail(s"expected an InvalidValue at the step, got $other")

  test("an unencodable die face returns a typed WireError, " +
      "not a thrown exception"):
    // The append path must report a value it cannot write as a WireError,
    // exactly like an unrecognized value on the decode side
    // (`encodePayloadSafe`, GameEventWire.scala). This is the only test
    // that reaches that catch: the other encode-direction failures are
    // envelope rejections from `validateEnvelope`, which never enter it.
    //
    // The stub is a DieFace rather than a WalkerStepPayload because the
    // payload family is sealed and a fourth member is a compile error, not a
    // runtime one. DieFace stays open (DieFace.scala) and RollPayload holds
    // a plain Vector[DieFace], so an unknown face still reaches
    // `encodeDieFace`'s throw inside the protected encode. The event itself
    // must be one the encoder registers -- `encodeEvent` resolves the
    // discriminator before `encodePayloadSafe`, so an unregistered event
    // escapes as a raw MatchError and never reaches the catch.

    // Stub of DieFace: a face neither die family declares.
    case object UnknownDieFace extends DieFace
    val event = WalkerStepRecorded("0",
      RollPayload(PoolKey("recover"), Vector(UnknownDieFace)), Vector.empty)
    GameEventWire.encodeEvent("walker", 0, event) match
      case Left(WireError.InvalidValue("$.payload", _)) => ()
      case other => fail(s"expected an invalid-payload WireError, got $other")

  test("every CoreOperation variant round-trips through the walker codec"):
    // `encodeOperation`/`encodePiece` are total over the sealed hierarchy
    // (I8), exactly like `encodeLocation` above -- this list is every
    // `CoreOperation` case. The walker's tree-control nodes are not in this
    // list because they are not `CoreOperation`s: they live under the
    // unsealed `Operation` and cannot be passed to the encoder at all.
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
      Burn.favor(3, PositionedLocation(Location.PlayArea(player)), None),
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
      SetOathkeeperGoal(OathkeeperGoal.ThePeople),
      QueueProcedure(QueuedProcedure(PlayerId("p1"), ActionRef.Campaign,
          Vector.empty,
          Some(PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))),
          QueueKind.Offer)),
      QueueProcedure(QueuedProcedure(PlayerId("p1"), ActionRef.Trade,
        Vector(DecisionOptionRef.Button("favor")), None, QueueKind.Run),
        atFront = true))

    val events = operations.map(operation =>
      recordedOperation(operation))

    val encoded = encodedStreamThatRoundTrips("walker-ops", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step")("ops")(0)("kind").str).toVector.distinct.size,
      operations.map(_.getClass.getSimpleName).distinct.size)

  test("a walker roll payload round-trips attack and defense faces"):
    val events = Vector[OathEvent](
      WalkerStepRecorded("1", RollPayload(PoolKey("campaign.attack"),
        Vector(AttackDieFace.HollowSword, AttackDieFace.OneSword,
          AttackDieFace.TwoSwordsSkull)), Vector.empty),
      WalkerStepRecorded("2", RollPayload(PoolKey("campaign.defense"),
        Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank),
        automatic = true), Vector.empty))
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
    val events = pieces.map(piece => recordedOperation(Take(piece, player,
      Location.SharedBank, Location.PlayArea(player))))

    val encoded = encodedStreamThatRoundTrips("walker-pieces", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("step")("ops")(0)("piece")("kind").str).toVector.distinct.size,
      pieces.map(_.getClass.getSimpleName).distinct.size)

  test("a Take's leaving operations round trip, and a Take without them " +
      "writes none"):
    val player = PlayerId("red")
    val holder = PlayerId("blue")
    val leaving = Vector[CoreOperation](
      Move(Piece.Favor(1),
        PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order))),
      Burn.secrets(2, PositionedLocation(Location.OnBanner(Banner.DarkestSecret)),
        None))
    val takes = Vector(
      Take(Piece.Banner(Banner.PeoplesFavor), player, Location.PlayArea(holder),
        Location.PlayArea(player), leaving = leaving),
      Take(Piece.Banner(Banner.DarkestSecret), player, Location.PlayArea(holder),
        Location.PlayArea(player)))
    val events = takes.map(take => recordedOperation(take))
    val encoded = encodedStreamThatRoundTrips("take-leaving", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("step")("ops")(0))
    assertEquals(ops(0)("leaving").arr.size, 2)
    assert(!ops(1).obj.contains("leaving"))

  test("a secret move's side round trips, and a move without one writes " +
      "none"):
    val red = PlayerId("red")
    val blue = PlayerId("blue")
    val moves = Vector[CoreOperation](
      Move(Piece.Secrets(2), PositionedLocation(Location.PlayArea(red),
        side = Some(SecretSide.FaceDown)),
        PositionedLocation(Location.PlayArea(blue))),
      Take(Piece.Secrets(1), blue, Location.PlayArea(red),
        Location.PlayArea(blue), sourceSide = Some(SecretSide.FaceUp)),
      Move(Piece.Favor(1), PositionedLocation(Location.PlayArea(red)),
        PositionedLocation(Location.PlayArea(blue))))
    val events = moves.map(move => recordedOperation(move))
    val encoded = encodedStreamThatRoundTrips("secret-side", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("step")("ops")(0))
    assertEquals(ops(0)("from")("side").str, "face-down")
    assertEquals(ops(1)("sourceSide").str, "face-up")
    assert(!ops(2)("from").obj.contains("side"))

  test("a burn's burner round trips, and a burn no player makes writes " +
      "null"):
    val red = PlayerId("red")
    val burns = Vector[CoreOperation](
      Burn.favor(2, PositionedLocation(Location.PlayArea(red)), Some(red)),
      Burn.favor(1, PositionedLocation(Location.FavorBank(Suit.Order)),
        None))
    val events = burns.map(burn => recordedOperation(burn))
    val encoded = encodedStreamThatRoundTrips("burn-by", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("step")("ops")(0))
    assertEquals(ops(0)("by").str, "red")
    assertEquals(ops(1)("by"), ujson.Null)

  test("a deferral and its take round trip, with the held ops"):
    val key = DeferralKey("test.held")
    val held = Vector[CoreOperation](ModifyDicePool(PoolKey("test.pool"), 2),
      Gain.Secrets(Table.p1, 1))
    Vector[CoreOperation](Defer(key, held), TakeDeferred(key)).foreach { op =>
      val event = recordedOperation(op)
      val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }
    val deferral = accepted(GameEventWire.encodeEvent("walker", 0,
      recordedOperation(Defer(key, held))))
    assertEquals(ujson.read(deferral)("payload")("step")("ops")(0)("kind").str,
      "defer")

  test("an as-if site round trips"):
    val event = recordedOperation(ActAsIf(CatalogNames.site("Deep Woods")))
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assertEquals(ujson.read(encoded)("payload")("step")("ops")(0)("kind").str,
      "act-as-if")
