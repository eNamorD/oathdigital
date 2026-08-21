package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{ForgeCommand, ForgeRules}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation._

class ForgeSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def forgeable: (ReadyGame, PlayerState, SiteId, Vector[SiteDenizenTarget], RelicId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor = base.game.current.players.find(_.player == base.game.current.turn.activePlayer).get
    val siteId = catalog.sites.find(_.forgeRequirements.nonEmpty).get.id
    val ids = catalog.denizens.take(3).map(d => DenizenId(d.id.value))
    val denizens = ids.map(DenizenState(_, Orientation.FaceUp, Tokens.empty))
    val site = base.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(actor.lineage), 1),
      denizens = denizens)
    val moved = actor.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      players = base.game.current.players.map(p => if (p.player == actor.player) moved else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(siteId, site)))))
    val targets = ids.map(SiteDenizenTarget(siteId, _))
    (ready, moved, siteId, targets, ready.game.current.commonCards.relicDeck.head)
  }

  test("Forge spends one Supply and privately projects three stable assignment targets") {
    val (ready, actor, _, targets, _) = forgeable
    val id = DecisionId("forge-private")
    val started = rules.handle(Ready(ready), ForgeCommand.Begin(actor.player, id)).toOption.get
    val owner = new GameProjector(catalog).project("forge", LoadedGame(started.state, 1), actor.player)
    val other = ready.game.current.players.find(_.player != actor.player).get
    val waiting = new GameProjector(catalog).project("forge", LoadedGame(started.state, 1), other.player)
    assertEquals(owner.forge.map(_.targets.map(t => t.siteId -> t.denizenId)),
      Some(targets.map(t => t.siteId.value -> t.denizenId.value)))
    assertEquals(waiting.forge, None)
    assertEquals(waiting.phase, "forge-waiting")
    assertEquals(waiting.legalControls, Vector.empty)
    val Ready(afterStart) = started.state: @unchecked
    assertEquals(afterStart.game.current.players.find(_.player == actor.player).get.board.supply.supply,
      actor.board.supply.supply - 1)
  }

  test("all printed resource permutations complete atomically and draw deck top facedown") {
    val (ready, actor, site, targets, relic) = forgeable
    val cost = ForgeRules.validate(catalog, ready, actor, site).toOption.get._2
    val resources = Vector.fill(cost.favor)(ForgeResource.Favor) ++
      Vector.fill(cost.secrets)(ForgeResource.Secret)
    resources.permutations.foreach { permutation =>
      val id = DecisionId("forge-permutation")
      val started = rules.handle(Ready(ready), ForgeCommand.Begin(actor.player, id)).toOption.get
      val completed = rules.handle(started.state, ForgeCommand.Complete(actor.player,
        id, targets.zip(permutation).map { case (t, r) => ForgeResourceAssignment(t, r) }, relic)).toOption.get
      val Ready(after) = completed.state: @unchecked
      assertEquals(after.game.current.commonCards.relicDeck.headOption,
        ready.game.current.commonCards.relicDeck.drop(1).headOption)
      assertEquals(after.game.current.players.find(_.player == actor.player).get.relics.last,
        RelicState(relic, Orientation.FaceDown, Tokens.empty))
      assertEquals(after.game.current.map.sites(site).denizens.collect {
        case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    }
  }

  test("duplicate stale malformed and tampered outcomes fail without mutation") {
    val (ready, actor, site, targets, relic) = forgeable
    val id = DecisionId("forge-tamper")
    val started = rules.handle(Ready(ready), ForgeCommand.Begin(actor.player, id)).toOption.get
    val duplicate = Vector(
      ForgeResourceAssignment(targets.head, ForgeResource.Favor),
      ForgeResourceAssignment(targets.head, ForgeResource.Favor),
      ForgeResourceAssignment(targets(2), ForgeResource.Secret))
    assert(rules.handle(started.state, ForgeCommand.Complete(actor.player, id,
      duplicate, relic)).isLeft)
    assert(rules.handle(started.state, ForgeCommand.Complete(actor.player,
      DecisionId("stale"), duplicate, relic)).left.toOption.get
      .isInstanceOf[ForgeDecisionMismatch])
    assert(rules.evolve(Ready(ready), ForgeStarted(actor.player, id, site,
      targets, Tokens(99, 0), 1)).left.toOption.get
      .isInstanceOf[ForgeOutcomeMismatch])
  }

  test("rule, Forge icon, exact empty denizens, Supply, and relic deck are authoritative") {
    val (ready, actor, siteId, _, _) = forgeable
    val site = ready.game.current.map.sites(siteId)
    def validate(s: SiteState, supply: Int = actor.board.supply.supply,
        deck: Vector[RelicId] = ready.game.current.commonCards.relicDeck) = {
      val p = actor.copy(board = actor.board.copy(supply = SupplyTrack(supply)))
      val r = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
        players = ready.game.current.players.map(x => if (x.player == actor.player) p else x),
        map = ready.game.current.map.copy(sites = ready.game.current.map.sites.updated(siteId, s)),
        commonCards = ready.game.current.commonCards.copy(relicDeck = deck))))
      ForgeRules.validate(catalog, r, p, siteId)
    }
    assert(validate(site.copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))).isLeft)
    assert(validate(site.copy(denizens = site.denizens.drop(1))).isLeft)
    assert(validate(site, supply = 0).left.toOption.get.isInstanceOf[InsufficientSupply])
    assert(validate(site, deck = Vector.empty).isLeft)
  }

  test("favor assignments consume the matching finite suit banks") {
    val (ready, actor, _, targets, relic) = forgeable
    val id = DecisionId("forge-banks")
    val started = rules.handle(Ready(ready), ForgeCommand.Begin(actor.player, id)).toOption.get
    val printed = catalog.sites.find(_.id == actor.pawnSite.get).get.forgeRequirements.get
    val resources = Vector.fill(printed.favor)(ForgeResource.Favor) ++
      Vector.fill(printed.secrets)(ForgeResource.Secret)
    val assignments = targets.zip(resources).map { case (t, r) => ForgeResourceAssignment(t, r) }
    val completed = rules.handle(started.state,
      ForgeCommand.Complete(actor.player, id, assignments, relic)).toOption.get
    val Ready(after) = completed.state: @unchecked
    assignments.filter(_.resource == ForgeResource.Favor).foreach { assignment =>
      val suit = catalog.denizens.find(_.id.value == assignment.target.denizenId.value)
        .flatMap(d => Suit.all.find(_.key == d.suit.value)).get
      assertEquals(after.support.favorBanks(suit),
        ready.support.favorBanks(suit) - assignments.count(a =>
          a.resource == ForgeResource.Favor && catalog.denizens
            .find(_.id.value == a.target.denizenId.value).exists(_.suit.value == suit.key)))
    }
    val depleted = ready.copy(support = ready.support.copy(favorBanks =
      ready.support.favorBanks.view.mapValues(_ => 0).toMap))
    val depletedStart = rules.handle(Ready(depleted),
      ForgeCommand.Begin(actor.player, id)).toOption.get
    assert(rules.handle(depletedStart.state,
      ForgeCommand.Complete(actor.player, id, assignments, relic)).isLeft)
  }

  test("unknown active handler outside the audited vocabulary blocks safely") {
    val (ready, actor, site, targets, _) = forgeable
    val active = targets.head.denizenId
    val altered = catalog.copy(denizens = catalog.denizens.map { definition =>
      if (definition.id.value != active.value) definition
      else definition.copy(handlers = definition.handlers :+
        "denizen.future-forge-interaction")
    })
    assert(ForgeRules.validate(altered, ready, actor, site).left.toOption.get
      .isInstanceOf[UnsupportedForgeState])
  }
}
