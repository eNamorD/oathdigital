package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.Table

class PowerOperationsSuite extends munit.FunSuite:

  private def operationReady: (ReadyGame, PlayerState, SiteId, DenizenId) =
    val base = Table.start.ready
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
        if p.player == actor.player then changedActor else p),
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == denizenId)),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site))))
    (ready, changedActor, siteId, denizenId)

  test("Costs rejects malformed unaffordable and misplaced costs"):
    val (ready, actor, siteId, denizenId) = operationReady
    val placedAt = Location.OnCard(denizenId)
    intercept[IllegalArgumentException](Cost(favor = -1))
    assert(Costs.plan(ready, actor.player, placedAt,
      Cost(secretBurnt = 4)).isLeft)
    assert(Costs.plan(ready, actor.player,
      Location.OnCard(DenizenId("missing")),
      Cost(secret = 1)).isLeft)

  test("Costs accepts a burnt-only cost on a facedown card"):
    val (ready, actor, siteId, denizenId) = operationReady
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

  test("PayCost executes placed and burnt portions atomically"):
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

  test("a free PayCost is an inert no-op"):
    val (ready, actor, _, _) = operationReady
    val payCost = Costs.plan(ready, actor.player, Location.OnCard(
      DenizenId("irrelevant")), Cost.free).toOption.get
    assertEquals(Operation.flatten(payCost), Vector.empty)
    val after = OperationPipeline.run(ready, Vector(payCost), OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))(Right(_)).toOption.get.state
    assertEquals(after.game, ready.game)
    assertEquals(after.banks, ready.banks)
