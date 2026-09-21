package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PowerOperationsSuite extends munit.FunSuite {

  private def operationReady: (ReadyGame, PlayerState, SiteId, DenizenId) = {
    val base = FirstGameSetupFixture.initialReady
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = catalog.sites.find(_.relicSlots > 0).get.id
    val denizenId = DenizenId(catalog.denizens.head.id.value)
    val card = DenizenState(denizenId, Orientation.FaceUp, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(denizens = Vector(card),
      relics = Vector.empty)
    val changedActor = actor.copy(pawnSite = Some(siteId), board = actor.board.copy(
      favor = 3, faceUpSecrets = 3))
    val ready = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if (p.player == actor.player) changedActor else p),
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == denizenId)),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site))))
    (ready, changedActor, siteId, denizenId)
  }

  test("Costs plans a PayCost that places favor and burns secrets atomically") {
    val (ready, actor, siteId, denizenId) = operationReady
    val placedAt = Location.OnCard(denizenId)
    val cost = Cost(favor = 2, secretBurnt = 1)
    val payCost = Costs.plan(ready, actor.player, placedAt, cost).toOption.get
    val after = OperationPipeline.run(ready, Vector(payCost), OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))(Right(_)).toOption.get.state
    val player = after.game.current.players.find(_.player == actor.player).get
    val card = after.game.current.map.sites(siteId).denizens.head
    assertEquals(player.board.favor, 1)
    assertEquals(player.board.faceUpSecrets, 2)
    assertEquals(card.tokens, Tokens(favor = 2, secrets = 0))
  }

  test("Costs rejects malformed unaffordable and misplaced costs") {
    val (ready, actor, siteId, denizenId) = operationReady
    val placedAt = Location.OnCard(denizenId)
    intercept[IllegalArgumentException](Cost(favor = -1))
    assert(Costs.plan(ready, actor.player, placedAt,
      Cost(secretBurnt = 4)).isLeft)
    assert(Costs.plan(ready, actor.player,
      Location.OnCard(DenizenId("missing")),
      Cost(secret = 1)).isLeft)
  }

  test("Costs accepts a free cost and validates existence without duplicating access") {
    val (ready, actor, siteId, denizenId) = operationReady
    val free = Costs.plan(ready, actor.player, Location.OnCard(
      DenizenId("missing")), Cost.free).toOption.get
    assertEquals(Operation.flatten(free), Vector.empty)

    val site = ready.game.current.map.sites(siteId)
    val facedown = ready.updateCurrent(_.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(siteId,
          site.copy(denizens = site.denizens.map {
            case card: DenizenState =>
              card.copy(orientation = Orientation.FaceDown)
            case other => other
          })))))
    assert(Costs.plan(facedown, actor.player, Location.OnCard(denizenId),
      Cost(secretBurnt = 1)).isRight)
  }

  test("DrawTopRelic and PlaceRelicAtSite preserve top-card and facedown rules") {
    val (ready, actor, siteId, _) = operationReady
    val relic = DrawTopRelic.plan(ready).toOption.get
    assert(DrawTopRelic.validate(ready, RelicId("wrong")).isLeft)
    assert(PlaceRelicAtSite.plan(catalog, ready, actor.player, relic, siteId,
      Orientation.FaceUp).isLeft)
    val placement = PlaceRelicAtSite.plan(catalog, ready, actor.player, relic,
      siteId, Orientation.FaceDown).toOption.get
    val operation = PowerOperationPlanner.placement(placement)
    val operations = Vector(operation)
    val allowlist = OperationPolicy.exact(
      operations, "test placement operation is not permitted")
    val after = OperationPipeline.run(
      ready, operations, allowlist)(Right(_)).toOption.get.state
    assertEquals(after.game.current.commonCards.relicDeck,
      ready.game.current.commonCards.relicDeck.tail)
    assertEquals(after.game.current.map.sites(siteId).relics,
      Vector(RelicState(relic, Orientation.FaceDown, Tokens.empty)))
    assert(OperationPipeline.run(
      after, operations, allowlist)(Right(_)).isLeft)
  }

  test("power operation plans apply in order and fail without a partial result") {
    val (ready, actor, siteId, denizenId) = operationReady
    val payCost = Costs.plan(ready, actor.player, Location.OnCard(denizenId),
      Cost(secret = 1)).toOption.get
    val relic = DrawTopRelic.plan(ready).toOption.get
    val placement = PlaceRelicAtSite.plan(catalog, ready, actor.player, relic,
      siteId, Orientation.FaceDown).toOption.get
    val operations = Vector[CoreOperation](payCost) :+
      PowerOperationPlanner.placement(placement)
    val allowlist = OperationPolicy.exact(
      operations, "test power operation is not permitted")
    val after = OperationPipeline.run(
      ready, operations, allowlist)(Right(_)).toOption.get.state
    assertEquals(after.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, actor.board.faceUpSecrets - 1)
    assertEquals(after.game.current.map.sites(siteId).relics.head.id, relic)

    val invalidOperations = Vector[CoreOperation](payCost) :+ Play(
      RelicId("missing"),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.Site(siteId),
      Orientation.FaceDown)
    val invalidAllowlist = OperationPolicy.exact(
      invalidOperations, "test invalid operation is not permitted")
    assert(OperationPipeline.run(
      ready, invalidOperations, invalidAllowlist)(Right(_)).isLeft)
    assertEquals(ready.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, actor.board.faceUpSecrets)
    assert(OperationPipeline.run(ready, Vector.empty,
      OperationPolicy.Permissive)(Right(_)).isLeft)
  }

  test("PayCost executes placed and burnt portions atomically") {
    val (ready, actor, siteId, denizenId) = operationReady
    val placedAt = Location.OnCard(denizenId)
    val cost = Cost(favor = 1, secret = 1, favorBurnt = 1, secretBurnt = 1)
    val payCost = Costs.plan(ready, actor.player, placedAt, cost).toOption.get
    val after = OperationPipeline.run(ready, Vector(payCost), OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))(Right(_)).toOption.get.state
    val player = after.game.current.players.find(_.player == actor.player).get
    val card = after.game.current.map.sites(siteId).denizens.head
    assertEquals(player.board.favor, actor.board.favor - 2)
    assertEquals(player.board.faceUpSecrets, actor.board.faceUpSecrets - 2)
    assertEquals(card.tokens, Tokens(favor = 1, secrets = 1))
  }

  test("a free PayCost is an inert no-op") {
    val (ready, actor, _, _) = operationReady
    val payCost = Costs.plan(ready, actor.player, Location.OnCard(
      DenizenId("irrelevant")), Cost.free).toOption.get
    assertEquals(Operation.flatten(payCost), Vector.empty)
    val after = OperationPipeline.run(ready, Vector(payCost), OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))(Right(_)).toOption.get.state
    assertEquals(after.game, ready.game)
    assertEquals(after.banks, ready.banks)
  }
}
