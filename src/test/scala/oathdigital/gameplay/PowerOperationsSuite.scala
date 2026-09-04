package oathdigital.gameplay

import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PowerOperationsSuite extends munit.FunSuite {
  private val rules = new FirstGameSetupRules(catalog)

  private def operationReady: (ReadyGame, PlayerState, SiteId, DenizenId) = {
    val Ready(base) = FirstGameSetupFixture.execute(rules)._1: @unchecked
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = catalog.sites.find(_.relicSlots > 0).get.id
    val denizenId = DenizenId(catalog.denizens.head.id.value)
    val card = DenizenState(denizenId, Orientation.FaceUp, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(denizens = Vector(card),
      relics = Vector.empty)
    val changedActor = actor.copy(pawnSite = Some(siteId), board = actor.board.copy(
      favor = 3, faceUpSecrets = 3))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p =>
        if (p.player == actor.player) changedActor else p),
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == denizenId)),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site)))))
    (ready, changedActor, siteId, denizenId)
  }

  test("PayCosts places typed resources, burns typed resources, and evolves") {
    val (ready, actor, siteId, denizenId) = operationReady
    val source = RuleSourceRef.SiteCard(siteId, denizenId)
    val costs = Vector(
      ResourceCost(ResourceKind.Favor, 2, CostDisposition.PlaceOnSource),
      ResourceCost(ResourceKind.Secret, 1, CostDisposition.Burn))
    assertEquals(PayCosts.describe(costs), Vector(
      CostDescription("favor", 2, "place-on-source"),
      CostDescription("secret", 1, "burn")))
    val payment = PayCosts.plan(ready, actor.player, source, costs).toOption.get
    val operations = PowerOperationPlanner.payment(payment).toOption.get
    val executor = new OperationExecutor(OperationPolicy.exact(
      operations, "test payment operation is not permitted"))
    val after = OperationTransaction.evolve(
      ready, operations, executor)(Right(_)).toOption.get.ready
    val player = after.game.current.players.find(_.player == actor.player).get
    val card = after.game.current.map.sites(siteId).denizens.head
    assertEquals(player.board.favor, 1)
    assertEquals(player.board.faceUpSecrets, 2)
    assertEquals(card.tokens, Tokens(favor = 2, secrets = 0))
  }

  test("PayCosts rejects empty malformed unaffordable and wrong-source costs") {
    val (ready, actor, siteId, denizenId) = operationReady
    val source = RuleSourceRef.SiteCard(siteId, denizenId)
    assert(PayCosts.plan(ready, actor.player, source, Vector.empty).isLeft)
    intercept[IllegalArgumentException](ResourceCost(ResourceKind.Favor, 0,
      CostDisposition.Burn))
    assert(PayCosts.plan(ready, actor.player, source, Vector(ResourceCost(
      ResourceKind.Secret, 4, CostDisposition.Burn))).isLeft)
    assert(PayCosts.plan(ready, actor.player,
      RuleSourceRef.SiteCard(siteId, DenizenId("wrong")), Vector(ResourceCost(
        ResourceKind.Secret, 1, CostDisposition.PlaceOnSource))).isLeft)
    assert(PayCosts.plan(ready, actor.player,
      RuleSourceRef.SiteCard(siteId, DenizenId("wrong")), Vector(ResourceCost(
        ResourceKind.Secret, 1, CostDisposition.Burn))).isLeft)
  }

  test("PayCosts validates source existence without duplicating power access") {
    val (ready, actor, siteId, denizenId) = operationReady
    val site = ready.game.current.map.sites(siteId)
    val facedown = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(siteId, site.copy(denizens =
          site.denizens.map {
            case card: DenizenState => card.copy(orientation = Orientation.FaceDown)
            case other => other
          }))))))
    assert(PayCosts.plan(facedown, actor.player,
      RuleSourceRef.SiteCard(siteId, denizenId), Vector(ResourceCost(
        ResourceKind.Secret, 1, CostDisposition.Burn))).isRight)
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
    val executor = new OperationExecutor(OperationPolicy.exact(
      operations, "test placement operation is not permitted"))
    val after = OperationTransaction.evolve(
      ready, operations, executor)(Right(_)).toOption.get.ready
    assertEquals(after.game.current.commonCards.relicDeck,
      ready.game.current.commonCards.relicDeck.tail)
    assertEquals(after.game.current.map.sites(siteId).relics,
      Vector(RelicState(relic, Orientation.FaceDown, Tokens.empty)))
    assert(OperationTransaction.evolve(
      after, operations, executor)(Right(_)).isLeft)
  }

  test("power operation plans apply in order and fail without a partial result") {
    val (ready, actor, siteId, denizenId) = operationReady
    val source = RuleSourceRef.SiteCard(siteId, denizenId)
    val payment = PayCosts.plan(ready, actor.player, source, Vector(ResourceCost(
      ResourceKind.Secret, 1, CostDisposition.PlaceOnSource))).toOption.get
    val relic = DrawTopRelic.plan(ready).toOption.get
    val placement = PlaceRelicAtSite.plan(catalog, ready, actor.player, relic,
      siteId, Orientation.FaceDown).toOption.get
    val paymentOperations = PowerOperationPlanner.payment(payment).toOption.get
    val operations = paymentOperations :+
      PowerOperationPlanner.placement(placement)
    val executor = new OperationExecutor(OperationPolicy.exact(
      operations, "test power operation is not permitted"))
    val after = OperationTransaction.evolve(
      ready, operations, executor)(Right(_)).toOption.get.ready
    assertEquals(after.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, actor.board.faceUpSecrets - 1)
    assertEquals(after.game.current.map.sites(siteId).relics.head.id, relic)

    val invalidOperations = paymentOperations :+ Play(
      RelicId("missing"),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.Site(siteId),
      Orientation.FaceDown)
    val invalidExecutor = new OperationExecutor(OperationPolicy.exact(
      invalidOperations, "test invalid operation is not permitted"))
    assert(OperationTransaction.evolve(
      ready, invalidOperations, invalidExecutor)(Right(_)).isLeft)
    assertEquals(ready.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, actor.board.faceUpSecrets)
    assert(OperationTransaction.evolve(ready, Vector.empty,
      new OperationExecutor(OperationPolicy.Permissive))(Right(_)).isLeft)
  }
}
