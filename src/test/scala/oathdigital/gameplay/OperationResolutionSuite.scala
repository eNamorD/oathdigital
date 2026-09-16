package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationResolutionSuite extends munit.FunSuite {
  private val ready = ReadyGame(game,
    Map(playerId -> PlayerColor("red")),
    FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered, playerId),
    MaterialBankState(Suit.all.map(_ -> 5).toMap,
      Map(ForceKind.Exile(lineageId) -> 14, ForceKind.Bandit -> 24)))
  private val validator = new OperationValidator(OperationPolicy.Permissive,
    Vector.empty)

  test("optional favor gain uses maximal available bank amount") {
    val result = OperationResolution.resolve(ready,
      Gain.Favor(playerId, Suit.Order, 7), validator)
    assertEquals(result, Right(OperationResolution.Execute(
      Gain.Favor(playerId, Suit.Order, 5))))
  }

  test("optional spend shrinks but required spend rejects") {
    val oneSupply = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { player =>
        if (player.player == playerId) player.copy(board = player.board.copy(
          supply = SupplyTrack(1))) else player
      })))
    assertEquals(OperationResolution.resolve(oneSupply,
      SpendSupply(playerId, 3, required = false), validator),
      Right(OperationResolution.Execute(
        SpendSupply(playerId, 1, required = false))))
    assert(OperationResolution.resolve(oneSupply,
      SpendSupply(playerId, 3), validator).isLeft)
  }

  test("zero available optional gain skips while wrong card source rejects") {
    val empty = ready.copy(banks = ready.banks.copy(favor =
      ready.banks.favor.updated(Suit.Order, 0)))
    assert(OperationResolution.resolve(empty,
      Gain.Favor(playerId, Suit.Order, 2), validator).toOption.get
      .isInstanceOf[OperationResolution.Skip])
    val wrongCard = Move(Piece.Card(worldDenizen),
      PositionedLocation(Location.Site(sites.head)),
      PositionedLocation(Location.Hand(playerId)))
    assert(OperationResolution.resolve(ready, wrongCard, validator).isLeft)
    val impossibleAndInvalid = Gain.Favor(playerId, Suit.Order, 7)
    val invalidRestriction = new OperationRestriction {
      override def reason(state: ReadyGame, operation: CoreOperation) =
        Some(OperationReason("invalid-test", "invalid alongside shortage",
          OperationReasonKind.Invalid))
    }
    val mixedValidator = new OperationValidator(OperationPolicy.Permissive,
      Vector(invalidRestriction))
    val mixedReasons = mixedValidator.validateOne(ready, impossibleAndInvalid)
    assert(mixedReasons.exists(_.kind == OperationReasonKind.Impossible))
    assert(mixedReasons.exists(_.kind == OperationReasonKind.Invalid))
    assert(OperationResolution.resolve(ready, impossibleAndInvalid,
      mixedValidator).isLeft)
  }

  test("replacement shrinks both colors together") {
    val operation = Replace(Piece.Warbands(ForceKind.Bandit, 3),
      Piece.Warbands(ForceKind.Exile(lineageId), 3),
      PositionedLocation(Location.Site(sites.head)))
    assertEquals(OperationResolution.resolve(ready, operation, validator),
      Right(OperationResolution.Execute(Replace(
        Piece.Warbands(ForceKind.Bandit, 1),
        Piece.Warbands(ForceKind.Exile(lineageId), 1),
        PositionedLocation(Location.Site(sites.head))))))
  }

  test("counted moves, takes, gives, burns, kills, and sacrifices shrink") {
    val fromBank = PositionedLocation(Location.FavorBank(Suit.Order))
    val toArea = PositionedLocation(Location.PlayArea(playerId))
    val site = PositionedLocation(Location.Site(sites.head))
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
      assertEquals(OperationResolution.resolve(ready, requested, validator),
        Right(OperationResolution.Execute(expected)))
    }
  }

  test("secret flip and dice-pool subtraction shrink at the available count") {
    val flip = FlipSecrets(playerId, 3, SecretSide.FaceUp,
      SecretSide.FaceDown)
    assertEquals(OperationResolution.resolve(ready, flip, validator),
      Right(OperationResolution.Execute(flip.copy(amount = 1))))
    val dice = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(rollPools = Map(PoolKey("recover") ->
        DicePoolState(2)))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), -4), validator),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), -2))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), Int.MinValue), validator),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), -2))))
    assertEquals(OperationResolution.resolve(dice,
      ModifyDicePool(PoolKey("recover"), Int.MaxValue), validator),
      Right(OperationResolution.Execute(
        ModifyDicePool(PoolKey("recover"), Int.MaxValue - 2))))
  }

  test("Supply gain shrinks at the track maximum") {
    val six = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { player =>
        player.copy(board = player.board.copy(supply = SupplyTrack(6)))
      })))
    assertEquals(OperationResolution.resolve(six,
      GainSupply(playerId, 3), validator),
      Right(OperationResolution.Execute(GainSupply(playerId, 1))))
    val full = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { player =>
        player.copy(board = player.board.copy(supply = SupplyTrack.full))
      })))
    assert(OperationResolution.resolve(full,
      GainSupply(playerId, 1), validator).toOption.get
      .isInstanceOf[OperationResolution.Skip])
  }

  test("Hall of Ministers blocks enemy site-card discard") {
    val blue = PlayerId("blue")
    val blueLineage = LineageId("blue")
    val bluePlayer = player.copy(player = blue, lineage = blueLineage,
      advisers = Vector.empty, pawnSite = Some(sites(2)))
    val current = ready.game.current
    val first = current.map.sites(sites.head).copy(forces =
      SiteForces.Occupied(ForceKind.Exile(lineageId), 1))
    val second = current.map.sites(sites(1)).copy(forces =
      SiteForces.Occupied(ForceKind.Exile(lineageId), 1),
      denizens = Vector(EdificeState(EdificeId("E16"), EdificeSide.Intact,
        Tokens.empty)))
    val changed = ready.copy(game = ready.game.copy(
      campaign = ready.game.campaign.copy(lineages =
        ready.game.campaign.lineages.updated(blueLineage,
          LineageState(blueLineage, Some(blue), Role.Exile,
            Vector.empty, Vector.empty))),
      current = current.copy(players = current.players :+ bluePlayer,
        map = current.map.copy(sites = current.map.sites
          .updated(sites.head, first).updated(sites(1), second)))))
    val restriction = new DiscardRestrictions(FirstGameSetupFixture.catalog,
      blue)
    val discard = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, blue)
    val reasons = new OperationValidator(OperationPolicy.Permissive,
      Vector(restriction)).validateOne(changed, discard)
    assertEquals(reasons.map(_.kind), Vector(OperationReasonKind.Impossible))
    assert(OperationResolution.resolve(changed, discard,
      new OperationValidator(OperationPolicy.Permissive, Vector(restriction)))
      .toOption.get.isInstanceOf[OperationResolution.Skip])
    val rulerRestriction = new DiscardRestrictions(FirstGameSetupFixture.catalog,
      playerId)
    assertEquals(OperationResolution.resolve(changed,
      discard.copy(actingPlayer = playerId),
      new OperationValidator(OperationPolicy.Permissive,
        Vector(rulerRestriction))),
      Right(OperationResolution.Execute(discard.copy(actingPlayer = playerId))))
  }

  test("discard resource fields describe the card and cannot shrink") {
    val stale = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, favor = 2, secrets = 0, playerId)
    val reasons = validator.validateOne(ready, stale)
    assert(reasons.exists(_.kind == OperationReasonKind.Invalid))
    assert(OperationResolution.resolve(ready, stale, validator).isLeft)
    val understated = stale.copy(favor = 0)
    assert(validator.validateOne(ready, understated)
      .exists(_.kind == OperationReasonKind.Invalid))
    assert(OperationResolution.resolve(ready, understated, validator).isLeft)
  }
}
