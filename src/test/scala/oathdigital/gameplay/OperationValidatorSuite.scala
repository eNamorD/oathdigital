package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationValidatorSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")
  private val redForce = ForceKind.Exile(lineageId)
  private val blueForce = ForceKind.Exile(blueLineage)
  private val extraDenizen = DenizenId("D5")

  private val bluePlayer = PlayerState(
    blueId,
    blueLineage,
    Some(sites(1)),
    PlayerBoardState(2, 1, 0, 2, SupplyTrack.full),
    Vector.empty,
    Vector.empty,
    None
  )

  private val ready = {
    val current = game.current.copy(
      players = game.current.players :+ bluePlayer,
      banners = game.current.banners.copy(
        peoplesFavor = game.current.banners.peoplesFavor.copy(
          holder = Some(playerId)))
    )
    val campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
      blueLineage,
      LineageState(blueLineage, Some(blueId), Role.Exile,
        Vector.empty, Vector.empty)
    ))
    ReadyGame(
      game.copy(campaign = campaign, current = current),
      Map(playerId -> PlayerColor("red"), blueId -> PlayerColor("blue")),
      FirstGameSupportState(
        FirstGameFoundationProfile.FixedUnaltered,
        playerId
      ),
      MaterialBankState(
        Suit.all.map(_ -> 5).toMap,
        Map(redForce -> 14, blueForce -> 14, ForceKind.Bandit -> 24)
      )
    )
  }

  private val codes = (values: Vector[OperationReason]) =>
    values.map(_.code)

  test("short favor bank rejects the drawn amount as insufficient-pieces") {
    val operation = Gain.Favor(playerId, Suit.Order, 7)
    val reasons = OperationShape.validate(ready, operation)

    assertEquals(reasons.map(_.code), Vector("insufficient-pieces"))
    assertEquals(reasons.head.detail, "favor bank contains 5 of requested favor")
    assertEquals(reasons.head.kind, OperationReasonKind.Impossible)
    assertEquals(OperationShape.first(ready, operation).map(_.code),
      Some("insufficient-pieces"))
  }

  test("wrong requested stack source position is an invalid-stack-position") {
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = Vector(worldDenizen, extraDenizen)))))
    val operation = Move(
      Piece.Card(extraDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Hand(playerId))
    )

    val reasons = OperationShape.validate(source, operation)
    assertEquals(reasons.map(_.code), Vector("invalid-stack-position"))
    assertEquals(reasons.head.detail,
      "card does not match requested stack position")
    assertEquals(reasons.head.kind, OperationReasonKind.Invalid)
  }

  test("one operation moving the same card twice is a conflicting-deltas") {
    val operation = Draw(playerId, Vector(worldDenizen, worldDenizen),
      Location.Deck(CardDeck.World), Location.Hand(playerId))

    val reasons = OperationShape.validate(ready, operation)
    assertEquals(reasons.headOption.map(_.code), Some("conflicting-deltas"))
    assertEquals(reasons.head.detail,
      "one operation moves the same card more than once")
  }

  test("orienting an edifice into a site is an unsupported-orientation") {
    val edifice = EdificeId("E2")
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(edifice)))))
    val operation = Move(
      Piece.Card(edifice),
      PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Top),
      PositionedLocation(Location.Site(sites.head)),
      Some(Orientation.FaceUp)
    )

    assertEquals(codes(OperationShape.validate(source, operation)),
      Vector("unsupported-orientation"))
  }

  test("warband moves from an undefined bounded supply are rejected") {
    val missing = ready.copy(banks = ready.banks.copy(
      warbandSupply = ready.banks.warbandSupply - redForce))

    assertEquals(codes(OperationShape.validate(missing,
      Gain.Warbands(playerId, redForce, 1))),
      Vector("unknown-warband-supply"))
    assertEquals(OperationShape.first(missing,
      Gain.Warbands(playerId, redForce, 1)).map(_.code),
      Some("unknown-warband-supply"))
  }

  test("counted move into an incompatible destination is invalid") {
    val operation = Move(Piece.Favor(7),
      PositionedLocation(Location.FavorBank(Suit.Order)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top))
    val reasons = OperationShape.validate(ready, operation)
    assert(reasons.exists(_.kind == OperationReasonKind.Impossible))
    assert(reasons.exists(_.kind == OperationReasonKind.Invalid))
  }

  test("a supply spend beyond the track is an insufficient-supply") {
    assertEquals(codes(OperationShape.validate(ready,
      SpendSupply(playerId, 8))), Vector("insufficient-supply"))
    assertEquals(OperationShape.first(ready, SpendSupply(playerId, 8))
      .map(_.code), Some("insufficient-supply"))
  }

  test("an already held banner cannot be claimed from the shared bank") {
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))

    assertEquals(codes(OperationShape.validate(ready, claim)),
      Vector("insufficient-pieces"))
  }

  test("moving a pawn that is not at the source site is a missing-piece") {
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.Site(sites(2))),
      PositionedLocation(Location.Site(sites(3))))

    assertEquals(codes(OperationShape.validate(ready, operation)),
      Vector("missing-piece"))
  }

  test("validateBatch reports a cross-operation same-card move") {
    val op1 = Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Hand(playerId))
    )
    val op2 = Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Hand(blueId))
    )
    // Each operation alone is well formed against the initial state.
    assertEquals(codes(OperationShape.validate(ready, op1)), Vector.empty)
    assertEquals(codes(OperationShape.validate(ready, op2)), Vector.empty)

    val reasons = OperationShape.validateBatch(ready, Vector(op1, op2))
    assert(reasons.exists(_.code == "conflicting-deltas"))
    assertEquals(reasons.filter(_.code == "conflicting-deltas").size, 1)
  }

  test("pipeline rejection matches the first shape reason byte-for-byte") {
    val corpus = Vector[CoreOperation](
      Gain.Favor(playerId, Suit.Order, 7),          // insufficient-pieces
      Gain.Favor(playerId, Suit.Order, 1),          // valid
      SpendSupply(playerId, 8),                   // insufficient-supply
      Move(Piece.Pawn(playerId),
        PositionedLocation(Location.Site(sites(2))),
        PositionedLocation(Location.Site(sites(3)))) // missing-piece
    )
    val raw = new OperationExecutor
    corpus.foreach { operation =>
      val reasons = OperationShape.validate(ready, operation)
      val pipeline = OperationPipeline.run(ready, Vector(operation),
        OperationPolicy.Permissive)(Right(_))
      (reasons.headOption, pipeline) match {
        case (None, Right(after)) =>
          assertEquals(after.state.game,
            raw.execute(ready, operation).toOption.get.game)
        case (Some(reason), Right(after))
            if reason.kind == OperationReasonKind.Impossible &&
              !operation.required =>
          assert(after.executed.nonEmpty || after.skipped.nonEmpty)
        case (Some(reason), Left(violation)) =>
          val code = violation match {
            case oathdigital.model.OathViolation
                .CoreOperationRejected(code, _) => code
            case other => fail(s"unexpected violation $other")
          }
          assertEquals(code, reason.code)
        case (Some(reason), Right(_)) =>
          fail(s"expected rejection for ${reason.code}")
        case (None, Left(violation)) =>
          fail(s"unexpected rejection $violation")
      }
    }
  }

  test("allowlist precedes shape so a both-fail operation reports restricted") {
    val operation = Gain.Favor(playerId, Suit.Order, 7) // shape-insufficient
    val validator = new OperationValidator(
      OperationPolicy.exact(Vector.empty, "test batch not permitted"),
      Vector.empty)
    val reasons = validator.validateOne(ready, operation)
    assertEquals(reasons.head.code, "restricted-operation")

    val pipeline = OperationPipeline.run(ready, Vector(operation),
      OperationPolicy.exact(Vector.empty, "test batch not permitted"))(Right(_))
    val code = pipeline.left.toOption.get match {
      case oathdigital.model.OathViolation.CoreOperationRejected(code, _) =>
        code
      case other => fail(s"unexpected violation $other")
    }
    assertEquals(code, "restricted-operation")
  }

  test("restriction registry is checked for each operation") {
    val blocking = new OperationRestriction {
      override def reason(ready: ReadyGame, operation: CoreOperation) =
        Some(OperationReason("power-blocked", "test predicate",
          OperationReasonKind.Impossible))
    }
    val validator = new OperationValidator(
      OperationPolicy.Permissive, Vector(blocking))
    val operation = Gain.Favor(playerId, Suit.Order, 1)
    assertEquals(validator.validateOne(ready, operation).map(_.code),
      Vector("power-blocked"))
  }

  test("report aggregates whole-batch reasons with allowlist precedence") {
    val operation = Gain.Favor(playerId, Suit.Order, 7)
    val reported = OperationPipeline.report(ready, Vector(operation),
      OperationPolicy.Permissive)
    assertEquals(reported.head.code, "insufficient-pieces")
  }
}
