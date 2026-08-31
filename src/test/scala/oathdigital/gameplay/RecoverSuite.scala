package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{PreparedRecoverModifier, RecoverCommand,
  RecoverRules}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{CostDisposition, ResourceCost,
  ResourceKind}
import oathdigital.gameplay.powers.recover.RecoverPowerIntegration

class RecoverSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def recoverable: (ReadyGame, PlayerState, SiteId, RelicState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find { id =>
      RecoverRules.difficulty(catalog, id).nonEmpty
    }.get
    val relic = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector(relic))
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      players = base.game.current.players.map(p => if (p.player == active.player) moved else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(siteId, site)))))
    (ready, moved, siteId, relic)
  }

  private def catacombsReady: (ReadyGame, PlayerState, SiteId, DenizenId, RelicId) = {
    val (base, actor0, siteId, _) = recoverable
    val definition = catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.catacombs")).get
    val cardId = DenizenId(definition.id.value)
    val actor = actor0.copy(board = actor0.board.copy(faceUpSecrets = 2))
    val site = base.game.current.map.sites(siteId).copy(relics = Vector.empty,
      denizens = Vector(DenizenState(cardId, Orientation.FaceUp, Tokens.empty)))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p =>
        if (p.player == actor.player) actor else p),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site)))))
    (ready, actor, siteId, cardId, ready.game.current.commonCards.relicDeck.head)
  }

  test("defense faces accumulate shields then apply every doubler") {
    assertEquals(RecoverRules.score(Vector(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler,
      DefenseDieFace.Doubler)), 12)
    assertEquals(RecoverRules.score(Vector(DefenseDieFace.Blank,
      DefenseDieFace.Doubler)), 0)
  }

  test("ordinary Act projects Recover and Rest as additive legal controls") {
    val (ready, player, _, _) = recoverable
    val projection = new GameProjector(catalog).project(
      "recover-controls", LoadedGame(Ready(ready), 1), player.player)

    assertEquals(projection.legalControls.toSet,
      Set("beginRecover", "beginRest", "facedownAdviserMinorAction",
        "peekSiteRelics", "beginNegotiation"))
  }

  test("each payment records two dice and stop returns to Act without revealing") {
    val (ready, player, _, _) = recoverable
    val id = DecisionId("recover-test")
    val first = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.Blank, DefenseDieFace.OneShield))).toOption.get
    val Ready(pending) = first.state: @unchecked
    assertEquals(pending.game.current.pending.collect {
      case r: PendingProcedure.Recover => r.supplySpent }, Some(1))
    assertEquals(pending.game.current.players.find(_.player == player.player).get
      .board.supply.supply, player.board.supply.supply - 1)
    val stopped = rules.handle(first.state, RecoverCommand.Stop(player.player, id)).toOption.get
    val Ready(after) = stopped.state: @unchecked
    assertEquals(after.game.current.pending, None)
    assertEquals(after.game.current.turn.phase, Phase.Act)
  }

  test("success privately peeks and transfers exactly one facedown relic") {
    val (base, player, siteId, relic) = recoverable
    val ready = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(oathkeeperGoal = OathkeeperGoal.Protection)))
    val other = ready.game.current.players.find(_.player != player.player).get
    val id = DecisionId("recover-private")
    val success = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler))).toOption.get
    val projector = new GameProjector(catalog)
    val owner = projector.project("recover", LoadedGame(success.state, 1), player.player)
    val hidden = projector.project("recover", LoadedGame(success.state, 1), other.player)
    assertEquals(owner.pendingCardDecision.map(_.cards.map(_.cardId)),
      Some(Vector(relic.id.value)))
    assertEquals(hidden.pendingCardDecision, None)
    assertEquals(hidden.recover, None)
    assertEquals(hidden.phase, "recover-waiting")
    val taken = rules.handle(success.state,
      RecoverCommand.TakeRelic(player.player, id, relic.id)).toOption.get
    val Ready(after) = taken.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).relics, Vector.empty)
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .relics.map(r => r.id -> r.orientation), Vector(relic.id -> Orientation.FaceDown))
    assertEquals(after.game.current.pending, None)
    assertEquals(taken.events.last, OathkeeperChanged(Some(player.player)))
    assertEquals(after.game.current.title.holder, Some(player.player))
  }

  test("replay rejects wrong cost roll size stale decision and unavailable relic") {
    val (ready, player, site, relic) = recoverable
    val id = DecisionId("recover-tamper")
    assert(rules.evolve(Ready(ready), RecoverRolled(player.player, id, site, 2,
      Vector(DefenseDieFace.OneShield, DefenseDieFace.OneShield))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
    assert(rules.evolve(Ready(ready), RecoverRolled(player.player, id, site, 1,
      Vector(DefenseDieFace.OneShield))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
    val started = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler))).toOption.get
    assert(rules.handle(started.state, RecoverCommand.TakeRelic(player.player,
      DecisionId("stale"), relic.id)).left.toOption.get
      .isInstanceOf[RecoverDecisionMismatch])
    assert(rules.handle(started.state, RecoverCommand.TakeRelic(player.player,
      id, RelicId("not-there"))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
  }

  test("Catacombs enables empty-site Recover then enters the ordinary roll") {
    val (ready, actor, siteId, cardId, relicId) = catacombsReady
    val source = RuleSourceRef.SiteCard(siteId, cardId)
    assert(RecoverRules.validate(catalog, ready, actor, siteId).isLeft)
    assert(RecoverPowerIntegration.validatePotential(catalog, ready, actor,
      siteId).isRight)
    val projection = new GameProjector(catalog).project("catacombs",
      LoadedGame(Ready(ready), 1), actor.player)
    assert(projection.legalControls.contains("beginRecover"))
    assertEquals(PowerRuntime.options(catalog, ready, actor.player,
      MajorActionKind.Recover).toOption.get,
      Vector(OrderedRuleInvocation(source, "denizen.catacombs")))

    val decision = DecisionId("recover-catacombs")
    val contribution = RecoverPowerIntegration.prepare(catalog, ready, actor.player,
      decision,
      Vector(OrderedRuleInvocation(source, "denizen.catacombs")),
      () => Right(relicId)).toOption.get
    val started = rules.handle(Ready(ready), RecoverCommand.Start(actor.player,
      decision, Vector(DefenseDieFace.Blank, DefenseDieFace.OneShield),
      contribution)).toOption.get
    assertEquals(started.events.take(2).map(_.getClass.getSimpleName),
      Vector("CatacombsResolved", "RecoverRolled"))
    val Ready(after) = started.state: @unchecked
    assertEquals(after.game.current.commonCards.relicDeck,
      ready.game.current.commonCards.relicDeck.tail)
    assertEquals(after.game.current.map.sites(siteId).relics.map(r =>
      r.id -> r.orientation), Vector(relicId -> Orientation.FaceDown))
    assertEquals(after.game.current.map.sites(siteId).denizens.head.tokens.secrets, 1)
    assertEquals(after.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, 1)
    assert(after.game.current.pending.exists(_.isInstanceOf[PendingProcedure.Recover]))
    assert(rules.handle(Ready(ready), RecoverCommand.Start(actor.player, decision,
      Vector(DefenseDieFace.Blank, DefenseDieFace.Blank), None)).isLeft)
  }

  test("Catacombs replay rejects wrong power source window cost relic and slot atomically") {
    val (ready, actor, siteId, cardId, relicId) = catacombsReady
    val decision = DecisionId("recover-catacombs-replay")
    val source = RuleSourceRef.SiteCard(siteId, cardId)
    val Some(PreparedRecoverModifier(Vector(valid: CatacombsResolved))) =
      RecoverPowerIntegration.prepare(catalog, ready, actor.player, decision,
        Vector(OrderedRuleInvocation(source, "denizen.catacombs")),
        () => Right(relicId)).toOption.get: @unchecked
    val original = Ready(ready)
    def rejects(event: CatacombsResolved, state: OathState = original) = {
      assert(rules.evolve(state, event).isLeft)
      assertEquals(original, Ready(ready))
    }
    rejects(valid.copy(powerId = PowerId("denizen.relic-worship")))
    rejects(valid.copy(source = source.copy(id = DenizenId("wrong"))))
    rejects(valid.copy(payment = valid.payment.copy(costs = Vector(ResourceCost(
      ResourceKind.Secret, actor.board.faceUpSecrets + 1,
      CostDisposition.PlaceOnSource)))))
    rejects(valid.copy(placement = valid.placement.copy(
      relicId = RelicId("wrong"))))
    val wake = Ready(ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(turn = ready.game.current.turn.copy(
        phase = Phase.Wake)))))
    rejects(valid, wake)
    val unaffordableActor = actor.copy(board = actor.board.copy(faceUpSecrets = 0))
    val unaffordable = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map(p =>
        if (p.player == actor.player) unaffordableActor else p))))
    rejects(valid, Ready(unaffordable))
    val site = ready.game.current.map.sites(siteId)
    val slots = catalog.sites.find(_.id == siteId).get.relicSlots
    val occupiedReady = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(siteId, site.copy(relics =
          Vector.tabulate(slots)(index => RelicState(RelicId(s"occupied-$index"),
            Orientation.FaceDown, Tokens.empty))))))))
    rejects(valid, Ready(occupiedReady))
    val applied = rules.evolve(original, valid).toOption.get
    val Ready(after) = applied: @unchecked
    assertEquals(after.game.current.players.find(_.player == actor.player).get
      .board.faceUpSecrets, actor.board.faceUpSecrets - 1)
    assertEquals(after.game.current.map.sites(siteId).relics.map(_.id),
      Vector(relicId))
    assert(after.game.current.pending.exists(
      _.isInstanceOf[PendingProcedure.RecoverPowerApplied]))
    val partial = new GameProjector(catalog).project("partial-catacombs",
      LoadedGame(applied, 1), actor.player)
    assertEquals(partial.phase, "recover-waiting")
    assertEquals(partial.legalControls, Vector.empty)
    assert(rules.evolve(applied, valid).isLeft)
    assert(rules.evolve(applied, RecoverRolled(actor.player,
      DecisionId("wrong-decision"), siteId, 1,
      Vector(DefenseDieFace.Blank, DefenseDieFace.Blank))).isLeft)
  }
}
