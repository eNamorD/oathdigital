package oathdigital.gameplay.setup

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.{accepted, as, rejected}

class GameStartRulesSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val chronicle = FirstGameSetupFixture.chronicle
  private val orders = FirstGameSetupFixture.orders

  test("a valid Chronicle evolves into a Ready game in Phase.Setup with the " +
      "first player active and no pawns or advisers"):
    val ready = accepted(GameStartRules.evolve(catalog, chronicle, orders))
    assertEquals(ready.game.current.turn.phase, Phase.Setup)
    assertEquals(ready.game.current.turn.activePlayer, orders.firstPlayer)
    assert(ready.game.current.players.forall(_.pawnSite.isEmpty))
    assert(ready.game.current.players.forall(_.advisers.isEmpty))

  test("a started game deals each participant 3 temporary cards"):
    val ready = accepted(GameStartRules.evolve(catalog, chronicle, orders))
    // The 3 denizens each player chooses an adviser from during Setup.
    assert(orders.participants.forall(p =>
      ready.game.current.temporaryHands.getOrElse(p.playerId, Vector.empty).size == 3))

  test("an edifice stored at an atlas site beyond the 8 in play stays in the " +
      "atlas and is not also in the edifice deck"):
    // Great Slum is not one of the Chronicle's 8 sites, and Hall of Debate,
    // its Homeland edifice, is stored at none of them.
    val site = CatalogNames.site("Great Slum")
    val edifice = CatalogNames.edifice("Hall of Debate")
    assert(!chronicle.atlasBox.exists(_.site == site))
    assert(!chronicle.atlasBox.exists(_.items.contains(edifice)))
    assertEquals(catalog.sites.find(_.id == site).flatMap(_.homeland),
      catalog.edifices.find(_.id.value == edifice.value).map(_.suit))
    val withStorage = chronicle.copy(
      atlasBox = chronicle.atlasBox :+ StoredSite(site, Vector(edifice)))
    val ready = accepted(GameStartRules.evolve(catalog, withStorage, orders))
    assert(ready.game.campaign.atlas.entries.contains(
      AtlasEntry.StoredSite(site, Vector.empty, Vector.empty, Some(edifice))))
    assert(!ready.game.current.commonCards.edificeDeck.contains(edifice))

  test("a non-empty world is refused"):
    val withWorld = chronicle.copy(world = chronicle.atlasBox.take(1))
    as[OathViolation.UnsupportedChronicle](
      rejected(GameStartRules.evolve(catalog, withWorld, orders)))

  test("a stored denizen on an atlas site is refused"):
    val denizen = chronicle.worldDeck.head
    val polluted = chronicle.copy(atlasBox = chronicle.atlasBox.updated(
      0, chronicle.atlasBox.head.copy(items = Vector(denizen))))
    val result = GameStartRules.evolve(catalog, polluted, orders)
    assertEquals(result.left.toOption, Some(OathViolation.UnsupportedChronicle(
      "stored denizens or relics on an atlas site are not supported yet")))

  test("too few atlas sites is refused"):
    val short = chronicle.copy(atlasBox = chronicle.atlasBox.take(7))
    val result = GameStartRules.evolve(catalog, short, orders)
    assertEquals(result, Left(OathViolation.UnsupportedChronicle(
      "at least 8 atlas sites are required, got 7")))

  test("an unknown denizen id is refused"):
    val polluted = chronicle.copy(
      worldDeck = chronicle.worldDeck.updated(0, DenizenId("no-such-denizen")))
    assertEquals(GameStartRules.evolve(catalog, polluted, orders).left.toOption,
      Some(OathViolation.UnsupportedChronicle("unknown denizen no-such-denizen")))
