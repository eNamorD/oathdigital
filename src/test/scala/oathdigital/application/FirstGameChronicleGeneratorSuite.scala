package oathdigital.application

import oathdigital.gameplay.operations.TheGrandScepterCard
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.powers.PowerImplementationStatus
import oathdigital.model.{CardId, Chronicle, DenizenId, EdificeId, Suit}
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.{accepted, present}

class FirstGameChronicleGeneratorSuite extends munit.FunSuite:
  private val status = PowerImplementationStatus.implemented(catalog)
  private val random = ChronicleRandomPort.random
  private val policy = ShufflePolicy.implementedFirst

  private def generated(using munit.Location): Chronicle =
    accepted(FirstGameChronicleGenerator.generate(catalog, status, random,
      policy))

  /** The printed suit of `card`; fails naming a card the catalog lacks. */
  private def suitOf(card: CardId)(using munit.Location): Suit =
    present(catalog.suitOf(card), s"${card.value} in the catalog")

  test("the atlas box holds all 24 sites, each Homeland carrying its suit's edifice"):
    val chronicle = generated
    assertEquals(chronicle.atlasBox.map(_.site).toSet, catalog.sites.map(_.id).toSet)
    assertEquals(chronicle.atlasBox.map(_.site).distinct.size, 24)
    catalog.sites.foreach { site =>
      val stored = present(chronicle.atlasBox.find(_.site == site.id),
        s"${site.name} in the atlas box")
      site.homeland match
        case Some(suit) =>
          assertEquals(stored.items.size, 1)
          stored.items.head match
            case edifice: EdificeId => assertEquals(suitOf(edifice), suit)
            case other => fail(s"${site.name} stores $other, not an edifice")
        case None => assertEquals(stored.items, Vector.empty)
    }

  test("the world deck has 60 denizens, 10 per suit, implemented ones first"):
    val chronicle = generated
    assertEquals(chronicle.worldDeck.size, 60)
    assertEquals(chronicle.worldDeck.distinct.size, 60)
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    val (_, rest) = chronicle.worldDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)),
      "no unimplemented denizen may precede an implemented one")
    Suit.all.foreach { suit =>
      val suited = chronicle.worldDeck.filter(id => suitOf(id) == suit)
      assertEquals(suited.size, 10)
    }

  test("the dispossessed pile has 12 unimplemented denizens, 2 per suit, " +
      "disjoint from the world deck"):
    val chronicle = generated
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    assertEquals(chronicle.dispossessed.size, 12)
    assertEquals(chronicle.dispossessed.distinct.size, 12)
    assert(chronicle.dispossessed.forall(id => !implemented(id)))
    assertEquals(chronicle.worldDeck.toSet intersect chronicle.dispossessed.toSet,
      Set.empty[oathdigital.model.DenizenId])
    Suit.all.foreach { suit =>
      val suited = chronicle.dispossessed.filter(id => suitOf(id) == suit)
      assertEquals(suited.size, 2)
    }

  test("the relic deck has every ordinary relic exactly once, implemented ones first"):
    val chronicle = generated
    val ordinary = catalog.relics.filter(_.id != TheGrandScepterCard.id)
      .map(r => oathdigital.model.RelicId(r.id.value))
    assertEquals(chronicle.relicDeck.toSet, ordinary.toSet)
    assertEquals(chronicle.relicDeck.size, ordinary.size)
    val implemented = ImplementedCardCatalog.ordinaryRelics(catalog, status)
    val (_, rest) = chronicle.relicDeck.span(implemented)
    assert(rest.forall(id => !implemented(id)))

  test("implemented means what the UI marker reads: a walker-only power " +
      "counts, and a denizen with an unimplemented power is left out"):
    // The Broken Forge, the Great Forge's ruined face, has a walker-only power.
    val brokenForge = CatalogNames.edifice("Broken Forge")
    val greatForge = present(catalog.edifices.find(
      _.id.value == brokenForge.value), "the Broken Forge")
    val walkerOnly =
      present(greatForge.ruined.powers.headOption, "its ruined power").id
    assert(status(walkerOnly))
    val unimplemented = catalog.denizens.flatMap(_.powers.map(_.id))
      .find(id => !status(id))
    assert(unimplemented.nonEmpty, "fixture expects an unimplemented denizen power")
    val denizens = ImplementedCardCatalog.denizens(catalog, status)
    catalog.denizens.filter(_.powers.exists(_.id == unimplemented.get)).foreach { d =>
      assert(!denizens(DenizenId(d.id.value)),
        s"${d.id.value} carries ${unimplemented.get}")
    }

  test("every Homeland carries an implemented edifice when its suit has one"):
    val chronicle = generated
    chronicle.atlasBox.flatMap(_.items).collect { case id: EdificeId => id }
      .foreach { id =>
        val suit = suitOf(id)
        ImplementedCardCatalog.homelandEdifice(catalog, suit, status)
          .foreach(expected => assertEquals(id, expected))
      }

  test("every implemented denizen of a suit reaches the world deck, up to 10"):
    val deck = generated.worldDeck.toSet
    val implemented = ImplementedCardCatalog.denizens(catalog, status)
    Suit.all.foreach { suit =>
      val suited = catalog.denizens.filter(_.suit == suit)
        .map(d => DenizenId(d.id.value)).filter(implemented)
      assertEquals(suited.count(deck), suited.size min 10, s"$suit")
    }

  test("five generated atlas orders are not all the same: the port is " +
      "consulted"):
    val orders = Vector.fill(5)(generated.atlasBox.map(_.site))
    assert(orders.distinct.size > 1, "24 shuffled sites should not repeat every time")
