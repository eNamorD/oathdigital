package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay._
import oathdigital.gameplay.operations.{CardPlayed, Discard,
  OperationPipeline, OperationPolicy}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

class DazzleSuite extends munit.FunSuite {
  private val setupRules = new oathdigital.gameplay.setup.FirstGameSetupRules(catalog)

  test("Dazzle discards Hearth and Order site cards from the actor region") {
    val OathState.Ready(base) = execute(setupRules)._1: @unchecked
    val actor = base.game.current.turn.activePlayer
    val dazzle = catalog.denizens.find(_.powers.exists(
      _.id == Dazzle.id)).map(d => DenizenId(d.id.value)).get
    val targets = Vector(Suit.Hearth, Suit.Order).map(suit =>
      catalog.denizens.find(d => d.suit == suit &&
        d.id.value != dazzle.value).map(d => DenizenId(d.id.value)).get)
    val current = base.game.current
    val siteId = current.players.find(_.player == actor).get.pawnSite.get
    val site = current.map.sites(siteId)
    val hearthBefore = base.banks.favor(Suit.Hearth)
    val prepared = base.copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Hearth, hearthBefore - 1)),
      game = base.game.copy(current = current.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          id == dazzle || targets.contains(id))),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = p.advisers :+ DenizenState(dazzle,
          Orientation.FaceUp, Tokens.empty)) else p),
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(denizens = site.denizens ++ targets.zipWithIndex.map {
          case (id, index) => DenizenState(id, Orientation.FaceUp,
            if (index == 0) Tokens(1, 0) else Tokens.empty)
        }))))))
    val hook = CardPlayed(dazzle, RuleSourceRef.Adviser(actor, dazzle))
    val power = Dazzle.forCatalog(catalog).get
    val finished = ProcedureWalker.advance(prepared, hook, None,
      WalkerPowers(Vector(power))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    val after = finished.treeless.game.current
    assert(targets.forall(id => !after.map.sites(siteId).denizens.exists(_.id == id)))
    val ops = finished.events.collect { case step: WalkerStepRecorded =>
      step.ops }.flatten
    assertEquals(ops.collect { case value: Discard.Denizen => value.card }, targets)
    assertEquals(finished.treeless.banks.favor(Suit.Hearth), hearthBefore)
    val replayed = OperationPipeline.run(prepared, ops,
      OperationPolicy.Permissive)(Right(_)).toOption.get.state
    assertEquals(replayed, finished.treeless)
    assertEquals(PowerRuntime.ignoredAtSource(catalog, prepared, actor,
      MajorActionKind.WhenPlayed, RuleSourceRef.Adviser(actor, dazzle)),
      Right(Vector.empty))
  }

  test("Dazzle skips a rule-immune target and still discards another") {
    val OathState.Ready(base) = execute(setupRules)._1: @unchecked
    val current = base.game.current
    val actor = current.turn.activePlayer
    val player = current.players.find(_.player == actor).get
    val enemy = current.players.find(_.player != actor).get
    val actorSite = player.pawnSite.get
    val region = current.map.regionOf(actorSite).get
    val enemySite = current.map.inPlay.find(site => site != actorSite &&
      current.map.regionOf(site).contains(region)).get
    val dazzle = catalog.denizens.find(_.powers.exists(
      _.id == Dazzle.id)).map(d => DenizenId(d.id.value)).get
    val targets = Vector(Suit.Hearth, Suit.Order).map(suit =>
      current.commonCards.worldDeck.collectFirst { case id: DenizenId
          if catalog.denizens.exists(d => d.id.value == id.value &&
            d.suit == suit) => id }.get)
    val hall = EdificeId("E16")
    assert(current.commonCards.edificeDeck.contains(hall))
    val friendly = current.map.sites(actorSite).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(player.lineage), 1),
      denizens = current.map.sites(actorSite).denizens :+
        DenizenState(targets.head, Orientation.FaceUp, Tokens.empty))
    val hostile = current.map.sites(enemySite).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(enemy.lineage), 1),
      denizens = current.map.sites(enemySite).denizens ++ Vector(
        DenizenState(targets(1), Orientation.FaceUp, Tokens.empty),
        EdificeState(hall, EdificeSide.Intact, Tokens.empty)))
    val prepared = base.copy(game = base.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = p.advisers :+ DenizenState(dazzle,
          Orientation.FaceUp, Tokens.empty)) else p),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(id =>
          id == dazzle || targets.contains(id)),
        edificeDeck = current.commonCards.edificeDeck.filterNot(_ == hall)),
      map = current.map.copy(sites = current.map.sites
        .updated(actorSite, friendly).updated(enemySite, hostile)))))
    val finished = ProcedureWalker.advance(prepared,
      CardPlayed(dazzle, RuleSourceRef.Adviser(actor, dazzle)), None,
      WalkerPowers(Vector(Dazzle.forCatalog(catalog).get))).toOption.get
      .asInstanceOf[WalkerOutcome.Finished]
    val after = finished.treeless.game.current
    assert(!after.map.sites(actorSite).denizens.exists(_.id == targets.head))
    assert(after.map.sites(enemySite).denizens.exists(_.id == targets(1)))
    val ops = finished.events.collect { case step: WalkerStepRecorded =>
      step.ops }.flatten
    assertEquals(ops.collect { case value: Discard.Denizen => value.card },
      Vector(targets.head))
  }

  test("Dazzle rejects a site denizen absent from the catalog") {
    val OathState.Ready(base) = execute(setupRules)._1: @unchecked
    val current = base.game.current
    val actor = current.turn.activePlayer
    val siteId = current.players.find(_.player == actor).get.pawnSite.get
    val unknown = DenizenId("denizen:missing-from-catalog")
    val site = current.map.sites(siteId)
    val prepared = base.copy(game = base.game.copy(current = current.copy(
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(denizens = site.denizens :+
          DenizenState(unknown, Orientation.FaceUp, Tokens.empty)))))))
    val dazzle = Dazzle.forCatalog(catalog).get
    val dazzleId = catalog.denizens.find(_.powers.exists(_.id == Dazzle.id))
      .map(d => DenizenId(d.id.value)).get
    val hook = CardPlayed(dazzleId, RuleSourceRef.Adviser(actor, dazzleId))
    assert(ProcedureWalker.advance(prepared, hook, None,
      WalkerPowers(Vector(dazzle))).isLeft)
  }
}
