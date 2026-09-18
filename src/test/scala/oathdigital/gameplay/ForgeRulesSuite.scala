package oathdigital.gameplay

import oathdigital.gameplay.actions.ForgeRules
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.catalog.CatalogPower

/** `ForgeRules.validate` is Forge's start gate, consumed by
  * `ForgeProcedure.build` on the walker. The legacy `Forge` object,
  * `ForgeCommand` and the `ForgeStarted`/`ForgeCompleted` events this suite
  * also drove are deleted (batch-1 Task 3); the behaviour they proved now
  * lives in `ForgeProcedureSuite` (the tree, its decision's validate and the
  * completion's operations) and in `GameApplicationServiceSuite`'s
  * end-to-end walker Forge (the supply spend, the owner-private prompt, the
  * relic play, the player-funded payments and the rejections).
  */
class ForgeRulesSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

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
    val ready = base.updateCurrent(_.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(ids.toSet)),
      players = base.game.current.players.map(p => if (p.player == actor.player) moved else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(siteId, site))))
    val targets = ids.map(SiteDenizenTarget(siteId, _))
    (ready, moved, siteId, targets, ready.game.current.commonCards.relicDeck.head)
  }

  test("rule, Forge icon, exact empty denizens, Supply, and relic deck are authoritative") {
    val (ready, actor, siteId, _, _) = forgeable
    val site = ready.game.current.map.sites(siteId)
    def validate(s: SiteState, supply: Int = actor.board.supply.supply,
        deck: Vector[RelicId] = ready.game.current.commonCards.relicDeck) = {
      val p = actor.copy(board = actor.board.copy(supply = SupplyTrack(supply)))
      val r = ready.updateCurrent(_.copy(
        players = ready.game.current.players.map(x => if (x.player == actor.player) p else x),
        map = ready.game.current.map.copy(sites = ready.game.current.map.sites.updated(siteId, s)),
        commonCards = ready.game.current.commonCards.copy(relicDeck = deck)))
      ForgeRules.validate(catalog, r, p, siteId)
    }
    assert(validate(site.copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))).isLeft)
    assert(validate(site.copy(denizens = site.denizens.drop(1))).isLeft)
    assert(validate(site, supply = 0).left.toOption.get.isInstanceOf[InsufficientSupply])
    assert(validate(site, deck = Vector.empty).isLeft)
  }

  test("the actor must be able to fund the printed cost from their own play " +
      "area before any Supply is spent") {
    val (ready, actor, siteId, _, _) = forgeable
    val cost = catalog.sites.find(_.id == siteId).get.forgeRequirements.get

    def withResources(favor: Int, secrets: Int) = {
      val p = actor.copy(board = actor.board.copy(favor = favor,
        faceUpSecrets = secrets))
      val r = ready.updateCurrent(_.copy(
        players = ready.game.current.players.map(x =>
          if (x.player == actor.player) p else x)))
      ForgeRules.validate(catalog, r, p, siteId)
    }

    assert(withResources(cost.favor, cost.secrets).isRight,
      "exactly enough of each resource is affordable")

    // A Forge that starts unable to pay would spend Supply, walk to its last
    // node and fail there with nothing recoverable, so the gate is a start
    // gate rather than a completion check.
    if (cost.favor > 0)
      assert(withResources(cost.favor - 1, cost.secrets).left.toOption.get
        .isInstanceOf[InsufficientFavor])
    if (cost.secrets > 0)
      assert(withResources(cost.favor, cost.secrets - 1).left.toOption.get
        .isInstanceOf[InsufficientSecrets])

    // Facedown secrets are not spendable, so they do not fund a Forge.
    val hidden = actor.copy(board = actor.board.copy(favor = cost.favor,
      faceUpSecrets = 0, faceDownSecrets = cost.secrets + 3))
    if (cost.secrets > 0) {
      val r = ready.updateCurrent(_.copy(
        players = ready.game.current.players.map(x =>
          if (x.player == actor.player) hidden else x)))
      assert(ForgeRules.validate(catalog, r, hidden, siteId).isLeft)
    }
  }

  test("unknown active handler outside the audited vocabulary blocks safely") {
    val (ready, actor, site, targets, _) = forgeable
    val active = targets.head.denizenId
    val altered = catalog.copy(denizens = catalog.denizens.map { definition =>
      if (definition.id.value != active.value) definition
      else definition.copy(powers = definition.powers :+ CatalogPower(
        "denizen.future-forge-interaction", persistent = false, "Future power."))
    })
    assert(ForgeRules.validate(altered, ready, actor, site).left.toOption.get
      .isInstanceOf[UnsupportedRuleCatalog])
  }
}
