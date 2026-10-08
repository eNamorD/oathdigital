package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class OperationResolutionSuite extends munit.FunSuite:
  /** The one-player game of [[TestGameFixtures]]: red holds 1 favor, 1
    * faceup secret and 3 warbands on the board, with a full Supply of 7; S1
    * holds 1 Bandit; every suit bank holds 5 favor. */
  private val ready = ReadyGames.of(game)

  test("optional favor gain uses maximal available bank amount"):
    val result = OperationResolution.resolve(ready,
      Gain.Favor(playerId, Suit.Order, 7), OperationPolicy.Permissive, Vector.empty)
    // The Order bank holds 5.
    assertEquals(result, Right(OperationResolution.Execute(
      Gain.Favor(playerId, Suit.Order, 5))))

  test("optional spend shrinks but required spend rejects"):
    val oneSupply = Table.from(ready).supply(playerId, 1).unchecked
    assertEquals(OperationResolution.resolve(oneSupply,
      SpendSupply(playerId, 3, required = false), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(
        SpendSupply(playerId, 1, required = false))))
    assertEquals(OperationResolution.resolve(oneSupply,
      SpendSupply(playerId, 3), OperationPolicy.Permissive, Vector.empty),
      Left(OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 3 exceeds the 1 available")))

  test("zero available optional gain skips while wrong card source rejects"):
    val empty = Table.from(ready).bankFavor(Suit.Order, 0).unchecked
    assertEquals(accepted(OperationResolution.resolve(empty,
      Gain.Favor(playerId, Suit.Order, 2), OperationPolicy.Permissive,
      Vector.empty)), OperationResolution.Skip(Vector(OperationReason(
        "insufficient-pieces", "favor bank contains 0 of requested favor",
        OperationReasonKind.Impossible))))
    val wrongCard = Move(Piece.Card(worldDenizen),
      PositionedLocation(Location.Site(sites.head)),
      PositionedLocation(Location.Hand(playerId)))
    assertEquals(OperationResolution.resolve(ready, wrongCard,
      OperationPolicy.Permissive, Vector.empty),
      Left(OathViolation.CoreOperationRejected("missing-piece",
        "denizen card is not present at site")))

  test("an operation with an impossible and an invalid reason is rejected"):
    // The Order bank's 5 cannot pay 7, and the restriction finds it invalid.
    val impossibleAndInvalid = Gain.Favor(playerId, Suit.Order, 7)
    /** Stub of OperationRestriction: refuses every operation as Invalid. */
    val invalidRestriction = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation): Option[OperationReason] =
        Some(OperationReason("invalid-test", "invalid alongside shortage",
          OperationReasonKind.Invalid))
    val mixed = Vector[OperationRestriction](invalidRestriction)
    val mixedReasons = OperationResolution.reasons(ready, impossibleAndInvalid,
      OperationPolicy.Permissive, mixed)
    assert(mixedReasons.exists(_.kind == OperationReasonKind.Impossible))
    assert(mixedReasons.exists(_.kind == OperationReasonKind.Invalid))
    assertEquals(OperationResolution.resolve(ready, impossibleAndInvalid,
      OperationPolicy.Permissive, mixed),
      Left(OathViolation.CoreOperationRejected("invalid-test",
        "invalid alongside shortage")))

  test("replacement shrinks both colors together"):
    // S1 holds 1 Bandit.
    val operation = Replace(Piece.Warbands(ForceKind.Bandit, 3),
      Piece.Warbands(ForceKind.Exile(lineageId), 3),
      PositionedLocation(Location.Site(sites.head)))
    assertEquals(OperationResolution.resolve(ready, operation, OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(Replace(
        Piece.Warbands(ForceKind.Bandit, 1),
        Piece.Warbands(ForceKind.Exile(lineageId), 1),
        PositionedLocation(Location.Site(sites.head))))))

  test("counted moves, takes, gives, burns, kills, and sacrifices shrink"):
    val fromBank = PositionedLocation(Location.FavorBank(Suit.Order))
    val toArea = PositionedLocation(Location.PlayArea(playerId))
    val site = PositionedLocation(Location.Site(sites.head))
    // The Order bank holds 5, red 1 favor and 3 warbands, S1 1 Bandit.
    val cases: Vector[(CoreOperation, CoreOperation)] = Vector(
      Move(Piece.Favor(7), fromBank, toArea) ->
        Move(Piece.Favor(5), fromBank, toArea),
      Take(Piece.Favor(7), playerId, Location.FavorBank(Suit.Order),
        Location.PlayArea(playerId)) ->
        Take(Piece.Favor(5), playerId, Location.FavorBank(Suit.Order),
          Location.PlayArea(playerId)),
      Give(Piece.Favor(3), playerId, Location.PlayArea(playerId),
        Location.Site(sites.head)) ->
        Give(Piece.Favor(1), playerId, Location.PlayArea(playerId),
          Location.Site(sites.head)),
      Burn.favor(3, toArea) -> Burn.favor(1, toArea),
      Kill(Piece.Warbands(ForceKind.Bandit, 3), site) ->
        Kill(Piece.Warbands(ForceKind.Bandit, 1), site),
      Sacrifice(playerId, Piece.Warbands(ForceKind.Exile(lineageId), 5),
        toArea) ->
        Sacrifice(playerId, Piece.Warbands(ForceKind.Exile(lineageId), 3),
          toArea))
    cases.foreach { case (requested, expected) =>
      assertEquals(OperationResolution.resolve(ready, requested, OperationPolicy.Permissive, Vector.empty),
        Right(OperationResolution.Execute(expected)))
    }

  test("secret flip and dice-pool changes clamp to what the pool holds"):
    // Red holds 1 faceup secret.
    val flip = FlipSecrets(playerId, 3, SecretSide.FaceUp,
      SecretSide.FaceDown)
    assertEquals(OperationResolution.resolve(ready, flip, OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(flip.copy(amount = 1))))
    val dice = ready.updateCurrent(_.copy(rollPools = Map(PoolKey("recover") ->
        DicePoolState(2))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), -4), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), -2))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), Int.MinValue), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), -2))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), Int.MaxValue), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), Int.MaxValue - 2))))

  test("Supply gain shrinks at the track maximum"):
    val six = Table.from(ready).supply(playerId, 6).unchecked
    assertEquals(OperationResolution.resolve(six,
      GainSupply(playerId, 3), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(GainSupply(playerId, 1))))
    // A gain that fits under the maximum is left as asked, not raised to it.
    val five = Table.from(ready).supply(playerId, 5).unchecked
    assertEquals(OperationResolution.resolve(five,
      GainSupply(playerId, 1), OperationPolicy.Permissive, Vector.empty),
      Right(OperationResolution.Execute(GainSupply(playerId, 1))))
    val full =
      Table.from(ready).supply(playerId, SupplyTrack.Maximum).unchecked
    assert(accepted(OperationResolution.resolve(full,
      GainSupply(playerId, 1), OperationPolicy.Permissive, Vector.empty))
      .isInstanceOf[OperationResolution.Skip])

  test("Hall of Ministers blocks enemy site-card discard"):
    // p1 rules Ancient City and Broken Peaks, where the Hall stands; the
    // enemy p2 discards Errand Boy from Ancient City.
    val hallSite = CatalogNames.site("Broken Peaks")
    val cardSite = CatalogNames.site("Ancient City")
    val card = CatalogNames.denizen("Errand Boy")
    val changed = Table.start.edifice(CatalogNames.edifice("Hall of Ministers"),
        EdificeSide.Intact, at = hallSite)
      .warbandsAt(hallSite, p1, 1).warbandsAt(cardSite, p1, 1)
      .denizen(card, at = cardSite).ready
    val restriction = HallOfMinisters
    val discard = Discard.Denizen(card,
      PositionedLocation(Location.Site(cardSite)), Region.Cradle,
      Suit.Beast, 0, 0, p2)
    val reasons = OperationResolution.reasons(changed, discard,
      OperationPolicy.Permissive, Vector(restriction))
    assertEquals(reasons.map(_.kind), Vector(OperationReasonKind.Impossible))
    assert(accepted(OperationResolution.resolve(changed, discard,
      OperationPolicy.Permissive, Vector(restriction)))
      .isInstanceOf[OperationResolution.Skip])
    assertEquals(OperationResolution.resolve(changed,
      discard.copy(actingPlayer = p1),
      OperationPolicy.Permissive, Vector(restriction)),
      Right(OperationResolution.Execute(discard.copy(actingPlayer = p1))))

  test("discard resource fields describe the card and cannot shrink"):
    val stale = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, favor = 2, secrets = 0, playerId)
    val reasons = OperationResolution.reasons(ready, stale,
      OperationPolicy.Permissive, Vector.empty)
    assert(reasons.exists(_.kind == OperationReasonKind.Invalid))
    assertEquals(OperationResolution.resolve(ready, stale,
      OperationPolicy.Permissive, Vector.empty),
      Left(OathViolation.CoreOperationRejected("invalid-description",
        "discard resources on D2 do not match the card")))
    val understated = stale.copy(favor = 0)
    assert(OperationResolution.reasons(ready, understated,
      OperationPolicy.Permissive, Vector.empty)
      .exists(_.kind == OperationReasonKind.Invalid))
    assertEquals(OperationResolution.resolve(ready, understated,
      OperationPolicy.Permissive, Vector.empty),
      Left(OathViolation.CoreOperationRejected("invalid-description",
        "discard resources on D2 do not match the card")))
