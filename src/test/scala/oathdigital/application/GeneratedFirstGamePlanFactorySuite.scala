package oathdigital.application

import oathdigital.gameplay.powers.PowerImplementationStatus
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.model._

class GeneratedFirstGamePlanFactorySuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val config = FirstGameBootstrapConfig(FirstGameSetupFixture.participants,
    PlayerId("p2"))
  private val factory = new GeneratedFirstGamePlanFactory(catalog)

  test("a generated Chronicle feeds GameStartRules") {
    val plan = factory.build(config).toOption.get
    // The generated Chronicle stores every catalog site (between-game
    // storage for sites not currently in play); only the first 8 are the
    // in-play atlas GameStartRules deals from.
    assert(plan.chronicle.atlasBox.size >= 8)
    assertEquals(plan.chronicle.worldDeck.size, 60)
    val orders = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    assert(GameStartRules.evolve(catalog, plan.chronicle, orders).isRight)
  }

  test("implemented cards are still dealt first after setup: seeded discards, " +
      "hands, then the world deck; site relic slots, then the relic deck") {
    val status = PowerImplementationStatus.implemented(catalog)
    val denizens = ImplementedCardCatalog.denizens(catalog, status)
    val relics = ImplementedCardCatalog.ordinaryRelics(catalog, status)
    def implementedFirst[A](cards: Vector[A], implemented: A => Boolean): Boolean =
      cards.dropWhile(implemented).forall(card => !implemented(card))

    val plan = factory.build(config).toOption.get
    val orders = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    val current = GameStartRules.evolve(catalog, plan.chronicle, orders)
      .toOption.get.game.current
    val dealtWorld: Vector[WorldCardId] =
      Region.all.flatMap(current.commonCards.discard) ++
        plan.resolvedConfig.participants.flatMap(p => current.temporaryHands(p.playerId)) ++
        current.commonCards.worldDeck
    val worldDenizens = dealtWorld.collect { case id: DenizenId => id }
    assert(worldDenizens.take(6).forall(denizens), "seeded discards")
    assert(implementedFirst(worldDenizens, denizens), worldDenizens.toString)

    val dealtRelics = current.map.inPlay.flatMap(site =>
      current.map.sites(site).relics.map(_.id)) ++ current.commonCards.relicDeck
    assertEquals(dealtRelics, plan.chronicle.relicDeck)
    assert(implementedFirst(dealtRelics, relics), dealtRelics.toString)
    assert(current.map.inPlay.flatMap(current.map.sites(_).denizens).forall {
      case edifice: EdificeState =>
        ImplementedCardCatalog.homelandEdifice(catalog,
          catalog.edifices.find(_.id.value == edifice.id.value).get.suit, status)
          .forall(_ == edifice.id)
      case _ => true
    }, "a Homeland in play carries its suit's implemented edifice")
  }

  test("seating order and first player are shuffled, keeping every participant") {
    val plans = Vector.fill(30)(factory.build(config).toOption.get.resolvedConfig)
    plans.foreach { resolved =>
      assertEquals(resolved.participants.toSet, config.participants.toSet)
      assertEquals(resolved.firstPlayer, resolved.participants.head.playerId)
    }
    assert(plans.map(_.firstPlayer).distinct.size > 1,
      "the first player should vary across generated plans")
    assert(plans.map(_.participants).distinct.size > 1,
      "the seating order should vary across generated plans")
  }

  test("successive plans are randomized, not the fixed dev order") {
    val atlasBoxes = Vector.fill(5)(factory.build(config).toOption.get.chronicle.atlasBox)
    assert(atlasBoxes.distinct.size > 1, "site order should vary across generated plans")
  }
}
