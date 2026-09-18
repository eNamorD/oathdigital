package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationStateAdapterSuite extends munit.FunSuite {
  private val exile = ForceKind.Exile(lineageId)
  private val ready = ReadyGames.of(game).copy(knowledge = CardKnowledge(
      siteRelics = Map(playerId -> Map(sites(1) -> Vector(siteRelic.id))),
      advisers = Map(playerId -> Vector(adviser.id)),
      heldRelics = Map(playerId -> Vector(reliquaryRelic))
    ))

  test("card lookup maps precise containers to semantic locations") {
    assert(OperationStateAdapter.card(ready, worldDenizen,
      Location.Deck(CardDeck.World)).isRight)
    assert(OperationStateAdapter.card(ready, siteDenizen.id,
      Location.Site(sites.head)).isRight)
    assert(OperationStateAdapter.card(ready, adviser.id,
      Location.PlayArea(playerId)).isRight)
    assert(OperationStateAdapter.card(ready, storedEdifice.id,
      Location.Atlas).isRight)
    assert(OperationStateAdapter.card(ready, reliquaryRelic,
      Location.Reliquary).isRight)
    assert(OperationStateAdapter.card(ready, dispossessed,
      Location.Dispossessed).isRight)
    assert(OperationStateAdapter.card(ready, worldDenizen,
      Location.PlayArea(playerId)).left.toOption.get
      .isInstanceOf[OperationError.MissingPiece])
  }

  test("temporary hands are not part of a player's play area") {
    val inHand = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(
        commonCards = ready.game.current.commonCards.copy(worldDeck = Vector.empty),
        temporaryHands = Map(playerId -> Vector(worldDenizen)))))

    assert(OperationStateAdapter.card(inHand, worldDenizen,
      Location.Hand(playerId)).isRight)
    assert(OperationStateAdapter.card(inHand, worldDenizen,
      Location.PlayArea(playerId)).left.toOption.get
      .isInstanceOf[OperationError.MissingPiece])
  }

  test("piece quantities cover boards sites cards banners and bounded banks") {
    import AvailableQuantity._

    assertEquals(OperationStateAdapter.quantity(ready, Piece.Favor(1),
      Location.PlayArea(playerId)), Right(Finite(1)))
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Favor(1),
      Location.OnCard(siteDenizen.id)), Right(Finite(1)))
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Favor(1),
      Location.OnBanner(Banner.PeoplesFavor)), Right(Finite(1)))
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Favor(1),
      Location.FavorBank(Suit.Order)), Right(Finite(5)))
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Secrets(1),
      Location.SharedBank), Right(Unbounded))
    assertEquals(OperationStateAdapter.quantity(ready,
      Piece.Warbands(exile, 1), Location.PlayArea(playerId)), Right(Finite(3)))
    assertEquals(OperationStateAdapter.quantity(ready,
      Piece.Warbands(exile, 1), Location.WarbandBank(exile)), Right(Finite(11)))
    assertEquals(OperationStateAdapter.quantity(ready,
      Piece.Warbands(ForceKind.Bandit, 1),
      Location.WarbandBank(ForceKind.Bandit)), Right(Finite(16)))
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Pawn(playerId),
      Location.Site(sites.head)), Right(Finite(1)))
    val unknown = PlayerId("unknown")
    assertEquals(OperationStateAdapter.quantity(ready, Piece.Pawn(unknown),
      Location.Site(sites.head)), Left(OperationError.UnknownPlayer(unknown)))
  }

  test("secret orientation and private knowledge remain separate from location") {
    assertEquals(OperationStateAdapter.secrets(ready,
      Location.PlayArea(playerId)), Right(SecretInventory(1, 0)))
    assert(OperationStateAdapter.knows(ready, playerId, siteRelic.id))
    assert(OperationStateAdapter.knows(ready, playerId, adviser.id))
    assert(OperationStateAdapter.knows(ready, playerId, reliquaryRelic))
  }
}
