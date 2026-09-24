package oathdigital.gameplay.setup

import oathdigital.model._

class GameStartRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val chronicle = FirstGameSetupFixture.chronicle
  private val orders = FirstGameSetupFixture.orders

  test("a valid Chronicle evolves into a Ready game in Phase.Setup with no pawns or advisers") {
    val ready = GameStartRules.evolve(catalog, chronicle, orders).toOption.get
    assertEquals(ready.game.current.turn.phase, Phase.Setup)
    assertEquals(ready.game.current.turn.activePlayer, orders.firstPlayer)
    assert(ready.game.current.players.forall(_.pawnSite.isEmpty))
    assert(ready.game.current.players.forall(_.advisers.isEmpty))
    assert(orders.participants.forall(p =>
      ready.game.current.temporaryHands.getOrElse(p.playerId, Vector.empty).size == 3))
  }

  test("an edifice stored at an atlas site beyond the 8 in play stays in the " +
      "atlas and is not also in the edifice deck") {
    val homelandsInPlay = chronicle.atlasBox.flatMap(_.items).toSet
    val offMapHomeland = catalog.sites.map(_.id)
      .filterNot(chronicle.atlasBox.map(_.site).contains)
      .flatMap(site => catalog.sites.find(_.id == site).get.handlers.collectFirst {
        case handler if handler.contains(".homeland-") =>
          val suit = Suit.fromKey(
            handler.substring(handler.indexOf(".homeland-") + 10)).get
          site -> catalog.edifices.map(e => EdificeId(e.id.value))
            .find(id => catalog.edifices.find(_.id.value == id.value).get.suit == suit &&
              !homelandsInPlay.contains(id)).get
      }).head
    val (site, edifice) = offMapHomeland
    val withStorage = chronicle.copy(
      atlasBox = chronicle.atlasBox :+ StoredSite(site, Vector(edifice)))
    val ready = GameStartRules.evolve(catalog, withStorage, orders).toOption.get
    assert(ready.game.campaign.atlas.entries.contains(
      AtlasEntry.StoredSite(site, Vector.empty, Vector.empty, Some(edifice))))
    assert(!ready.game.current.commonCards.edificeDeck.contains(edifice))
  }

  test("a non-empty world is refused") {
    val withWorld = chronicle.copy(world = chronicle.atlasBox.take(1))
    val result = GameStartRules.evolve(catalog, withWorld, orders)
    assert(result.isLeft)
    assert(result.left.toOption.get.isInstanceOf[OathViolation.UnsupportedChronicle])
  }

  test("a stored denizen on an atlas site is refused") {
    val denizen = chronicle.worldDeck.head
    val polluted = chronicle.copy(atlasBox = chronicle.atlasBox.updated(
      0, chronicle.atlasBox.head.copy(items = Vector(denizen))))
    val result = GameStartRules.evolve(catalog, polluted, orders)
    assert(result.isLeft)
  }

  test("too few atlas sites is refused") {
    val short = chronicle.copy(atlasBox = chronicle.atlasBox.take(7))
    val result = GameStartRules.evolve(catalog, short, orders)
    assertEquals(result, Left(OathViolation.UnsupportedChronicle(
      "at least 8 atlas sites are required, got 7")))
  }

  test("an unknown denizen id is refused") {
    val polluted = chronicle.copy(
      worldDeck = chronicle.worldDeck.updated(0, DenizenId("no-such-denizen")))
    assert(GameStartRules.evolve(catalog, polluted, orders).isLeft)
  }
}
