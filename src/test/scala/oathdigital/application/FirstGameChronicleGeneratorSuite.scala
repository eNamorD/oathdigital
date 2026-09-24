package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.powers.{PowerImplementationStatus, ReviewedPowerCatalog}
import oathdigital.model.{Chronicle, DenizenId, EdificeId, PowerId, Suit}

class FirstGameChronicleGeneratorSuite extends munit.FunSuite {
  private val status = PowerImplementationStatus.implemented(catalog)
  private val random = ChronicleRandomPort.random
  private val policy = ShufflePolicy.implementedFirst

  private def generated: Chronicle =
    FirstGameChronicleGenerator.generate(catalog, status, random, policy)
      .toOption.get

  test("the atlas box holds all 24 sites, each Homeland carrying its suit's edifice") {
    val chronicle = generated
    assertEquals(chronicle.atlasBox.map(_.site).toSet, catalog.sites.map(_.id).toSet)
    assertEquals(chronicle.atlasBox.map(_.site).distinct.size, 24)
    catalog.sites.foreach { site =>
      val stored = chronicle.atlasBox.find(_.site == site.id).get
      val handlerSuit = site.handlers.collectFirst {
        case handler if handler.contains(".homeland-") =>
          Suit.fromKey(handler.substring(handler.indexOf(".homeland-") + 10)).get
      }
      handlerSuit match {
        case Some(suit) =>
          assertEquals(stored.items.size, 1)
          val edificeId = stored.items.head.asInstanceOf[oathdigital.model.EdificeId]
          assertEquals(catalog.edifices.find(_.id.value == edificeId.value).get.suit, suit)
        case None => assertEquals(stored.items, Vector.empty)
      }
    }
  }

  test("the world deck has 60 denizens, 10 per suit, implemented ones first") {
    val chronicle = generated
    assertEquals(chronicle.worldDeck.size, 60)
    assertEquals(chronicle.worldDeck.distinct.size, 60)
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    val (_, rest) = chronicle.worldDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)),
      "no unimplemented denizen may precede an implemented one")
    Suit.all.foreach { suit =>
      val suited = chronicle.worldDeck.filter(id =>
        catalog.denizens.find(_.id.value == id.value).get.suit == suit)
      assertEquals(suited.size, 10)
    }
  }

  test("the dispossessed pile has 12 unimplemented denizens, 2 per suit, " +
      "disjoint from the world deck") {
    val chronicle = generated
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    assertEquals(chronicle.dispossessed.size, 12)
    assertEquals(chronicle.dispossessed.distinct.size, 12)
    assert(chronicle.dispossessed.forall(id => !implemented(id)))
    assertEquals(chronicle.worldDeck.toSet intersect chronicle.dispossessed.toSet,
      Set.empty[oathdigital.model.DenizenId])
    Suit.all.foreach { suit =>
      val suited = chronicle.dispossessed.filter(id =>
        catalog.denizens.find(_.id.value == id.value).get.suit == suit)
      assertEquals(suited.size, 2)
    }
  }

  test("the relic deck has every ordinary relic exactly once, implemented ones first") {
    val chronicle = generated
    val ordinary = catalog.relics.filter(_.role == oathdigital.catalog.RelicRole.Ordinary)
      .map(r => oathdigital.model.RelicId(r.id.value))
    assertEquals(chronicle.relicDeck.toSet, ordinary.toSet)
    assertEquals(chronicle.relicDeck.size, ordinary.size)
    val implemented = ImplementedCardCatalog.ordinaryRelics(catalog, status)
    val (_, rest) = chronicle.relicDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)))
  }

  test("implemented means what the UI marker reads, not presence in the " +
      "reviewed registry: a walker-only power counts, a declared stub does not") {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption.get
    val walkerOnly = PowerId("edifice.e06.ruined")
    assert(registry.lookup(walkerOnly).isEmpty && status(walkerOnly))
    val stub = ReviewedPowerCatalog.powers.find(_.handlers.exists(!_.implemented))
      .map(_.id).filterNot(status)
    assert(stub.nonEmpty, "fixture expects at least one declared-unimplemented stub")
    assert(registry.lookup(stub.get).isDefined)
    val denizens = ImplementedCardCatalog.denizens(catalog, status)
    catalog.denizens.filter(_.powers.exists(_.id == stub.get)).foreach { d =>
      assert(!denizens(DenizenId(d.id.value)), s"${d.id.value} carries stub ${stub.get}")
    }
  }

  test("every Homeland carries an implemented edifice when its suit has one") {
    val chronicle = generated
    chronicle.atlasBox.flatMap(_.items).collect { case id: EdificeId => id }
      .foreach { id =>
        val suit = catalog.edifices.find(_.id.value == id.value).get.suit
        ImplementedCardCatalog.homelandEdifice(catalog, suit, status)
          .foreach(expected => assertEquals(id, expected))
      }
  }

  test("every implemented denizen of a suit reaches the world deck, up to 10") {
    val deck = generated.worldDeck.toSet
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    Suit.all.foreach { suit =>
      val suited = catalog.denizens.filter(_.suit == suit)
        .map(d => DenizenId(d.id.value)).filter(implemented)
      assertEquals(suited.count(deck), suited.size min 10, s"$suit")
    }
  }

  test("two runs land different atlas orders: the port is actually consulted") {
    val orders = Vector.fill(5)(generated.atlasBox.map(_.site))
    assert(orders.distinct.size > 1, "24 shuffled sites should not repeat every time")
  }
}
