package oathdigital.application

import oathdigital.gameplay.powers.PowerImplementationStatus
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.model._
import oathdigital.testkit.Unwrap.{accepted, present}

class GeneratedFirstGamePlanFactorySuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val config = FirstGameBootstrapConfig(FirstGameSetupFixture.participants,
    PlayerId("p2"))
  private val factory = new GeneratedFirstGamePlanFactory(catalog)

  test("implemented cards are still dealt first after setup: seeded discards, " +
      "hands, then the world deck; site relic slots, then the relic deck"):
    val status = PowerImplementationStatus.implemented(catalog)
    val denizens = ImplementedCardCatalog.denizens(catalog, status)
    val relics = ImplementedCardCatalog.ordinaryRelics(catalog, status)
    def implementedFirst[A](cards: Vector[A], implemented: A => Boolean): Boolean =
      cards.dropWhile(implemented).forall(card => !implemented(card))

    val plan = accepted(factory.build(config))
    val orders = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    val current = accepted(
      GameStartRules.evolve(catalog, plan.chronicle, orders)).game.current
    val dealtWorld: Vector[WorldCardId] =
      Region.all.flatMap(current.commonCards.discard) ++
        plan.resolvedConfig.participants.flatMap(p => current.temporaryHands(p.playerId)) ++
        current.commonCards.worldDeck
    val worldDenizens = dealtWorld.collect { case id: DenizenId => id }
    // 2 seeded discards in each of the 3 regions.
    assert(worldDenizens.take(6).forall(denizens), "seeded discards")
    assert(implementedFirst(worldDenizens, denizens), worldDenizens.toString)

    val dealtRelics = current.map.inPlay.flatMap(site =>
      current.map.sites(site).relics.map(_.id)) ++ current.commonCards.relicDeck
    assertEquals(dealtRelics, plan.chronicle.relicDeck)
    assert(implementedFirst(dealtRelics, relics), dealtRelics.toString)
    assert(current.map.inPlay.flatMap(current.map.sites(_).denizens).forall {
      case edifice: EdificeState =>
        val printed = present(catalog.edifices
          .find(_.id.value == edifice.id.value), edifice.id.value)
        ImplementedCardCatalog.homelandEdifice(catalog, printed.suit, status)
          .forall(_ == edifice.id)
      case _ => true
    }, "a Homeland in play carries its suit's implemented edifice")

  test("seating order and first player are shuffled, keeping every participant"):
    val plans = Vector.fill(30)(accepted(factory.build(config)).resolvedConfig)
    plans.foreach { resolved =>
      assertEquals(resolved.participants.toSet, config.participants.toSet)
      assertEquals(resolved.firstPlayer, resolved.participants.head.playerId)
    }
    assert(plans.map(_.firstPlayer).distinct.size > 1,
      "the first player should vary across generated plans")
    assert(plans.map(_.participants).distinct.size > 1,
      "the seating order should vary across generated plans")
