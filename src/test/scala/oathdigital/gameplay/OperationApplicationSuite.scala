package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** The shape guard through the two interfaces that cross it: the resolver's
  * `OperationApplication.validate`, which reports every reason, and
  * `OperationPipeline.run`, whose rejection is the first of them.
  */
class OperationApplicationSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")
  private val redForce = ForceKind.Exile(lineageId)
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
    ReadyGames.of(game.copy(campaign = campaign, current = current))
  }

  private val codes = (values: Vector[OperationReason]) =>
    values.map(_.code)

  private def rejection(state: ReadyGame, operation: CoreOperation,
      allowlist: OperationPolicy = OperationPolicy.Permissive): String =
    OperationPipeline.run(state, Vector(operation), allowlist)(Right(_)) match {
      case Left(OathViolation.CoreOperationRejected(code, _)) => code
      case other => fail(s"expected a CoreOperationRejected, got $other")
    }

  test("short favor bank rejects the drawn amount as insufficient-pieces") {
    val operation = Gain.Favor(playerId, Suit.Order, 7)
    val reasons = OperationApplication.validate(ready, operation)

    assertEquals(reasons.map(_.code), Vector("insufficient-pieces"))
    assertEquals(reasons.head.detail, "favor bank contains 5 of requested favor")
    assertEquals(reasons.head.kind, OperationReasonKind.Impossible)
  }

  test("wrong requested stack source position is an invalid-stack-position") {
    val source = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = Vector(worldDenizen, extraDenizen))))
    val operation = Move(
      Piece.Card(extraDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Hand(playerId))
    )

    val reasons = OperationApplication.validate(source, operation)
    assertEquals(reasons.map(_.code), Vector("invalid-stack-position"))
    assertEquals(reasons.head.detail,
      "card does not match requested stack position")
    assertEquals(reasons.head.kind, OperationReasonKind.Invalid)
    assertEquals(rejection(source, operation), "invalid-stack-position")
  }

  test("one operation moving the same card twice is a conflicting-deltas") {
    val operation = Draw(playerId, Vector(worldDenizen, worldDenizen),
      Location.Deck(CardDeck.World), Location.Hand(playerId))

    val reasons = OperationApplication.validate(ready, operation)
    assertEquals(reasons.headOption.map(_.code), Some("conflicting-deltas"))
    assertEquals(reasons.head.detail,
      "one operation moves the same card more than once")
  }

  test("orienting an edifice into a site is an unsupported-orientation") {
    val edifice = EdificeId("E2")
    val source = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(edifice))))
    val operation = Move(
      Piece.Card(edifice),
      PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Top),
      PositionedLocation(Location.Site(sites.head)),
      Some(Orientation.FaceUp)
    )

    assertEquals(codes(OperationApplication.validate(source, operation)),
      Vector("unsupported-orientation"))
    assertEquals(rejection(source, operation), "unsupported-orientation")
  }

  test("warband moves from an undefined bounded supply are rejected") {
    val missing = ready.copy(banks = ready.banks.copy(
      warbandSupply = ready.banks.warbandSupply - redForce))

    assertEquals(codes(OperationApplication.validate(missing,
      Gain.Warbands(playerId, redForce, 1))),
      Vector("unknown-warband-supply"))
    assertEquals(rejection(missing, Gain.Warbands(playerId, redForce, 1)),
      "unknown-warband-supply")
  }

  test("counted move into an incompatible destination is invalid") {
    val operation = Move(Piece.Favor(7),
      PositionedLocation(Location.FavorBank(Suit.Order)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top))
    val reasons = OperationApplication.validate(ready, operation)
    assert(reasons.exists(_.kind == OperationReasonKind.Impossible))
    assert(reasons.exists(_.kind == OperationReasonKind.Invalid))
  }

  test("a supply spend beyond the track is an insufficient-supply") {
    assertEquals(codes(OperationApplication.validate(ready,
      SpendSupply(playerId, 8))), Vector("insufficient-supply"))
    assertEquals(rejection(ready, SpendSupply(playerId, 8)),
      "insufficient-supply")
  }

  test("an already held banner cannot be claimed from the shared bank") {
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))

    assertEquals(codes(OperationApplication.validate(ready, claim)),
      Vector("insufficient-pieces"))
  }

  test("moving a pawn that is not at the source site is a missing-piece") {
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.Site(sites(2))),
      PositionedLocation(Location.Site(sites(3))))

    assertEquals(codes(OperationApplication.validate(ready, operation)),
      Vector("missing-piece"))
    assertEquals(rejection(ready, operation), "missing-piece")
  }

  test("a pawn with no prior site may move from the player area to a site") {
    val unplaced = ready.updateCurrent(_.copy(players = ready.game.current.players.map {
      player => if (player.player == playerId) player.copy(pawnSite = None) else player
    }))
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))

    assertEquals(codes(OperationApplication.validate(unplaced, operation)),
      Vector.empty)
  }

  test("a pawn already on a site cannot move from the player area again") {
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))

    assertEquals(codes(OperationApplication.validate(ready, operation)),
      Vector("missing-piece"))
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
      val reasons = OperationApplication.validate(ready, operation)
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
            case OathViolation.CoreOperationRejected(code, _) => code
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

  test("the raw executor rejects what the shape guard rejects") {
    val raw = new OperationExecutor
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.Site(sites(2))),
      PositionedLocation(Location.Site(sites(3))))
    assertEquals(raw.execute(ready, operation).left.map(_.code),
      Left("missing-piece"))
  }

  test("allowlist precedes shape so a both-fail operation reports restricted") {
    val operation = Gain.Favor(playerId, Suit.Order, 7) // shape-insufficient
    val allowlist = OperationPolicy.exact(Vector.empty,
      "test batch not permitted")
    val reasons = OperationResolution.reasons(ready, operation, allowlist,
      Vector.empty)
    assertEquals(reasons.head.code, "restricted-operation")
    assertEquals(rejection(ready, operation, allowlist), "restricted-operation")
  }

  test("restriction registry is checked for each operation") {
    val blocking = new OperationRestriction {
      override def reason(ready: ReadyGame, operation: CoreOperation) =
        Some(OperationReason("power-blocked", "test predicate",
          OperationReasonKind.Impossible))
    }
    val operation = Gain.Favor(playerId, Suit.Order, 1)
    assertEquals(OperationResolution.reasons(ready, operation,
      OperationPolicy.Permissive, Vector(blocking)).map(_.code),
      Vector("power-blocked"))
  }
}
