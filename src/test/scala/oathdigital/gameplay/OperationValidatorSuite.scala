package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
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

  test("a supply spend beyond the track is an insufficient-supply") {
    assertEquals(codes(OperationShape.validate(ready,
      AdjustSupply(playerId, -8))), Vector("insufficient-supply"))
    assertEquals(OperationShape.first(ready, AdjustSupply(playerId, -8))
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
}
