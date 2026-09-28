package oathdigital.testkit

import oathdigital.application.{GameApplicationService, GameCommand,
  InMemoryEventStreamRepository, StartPayload}
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import Table.{p1, p2, p3}

/** `Table` builds rule-test states from the real game start without the
  * setup walk. These tests pin what the quiet table holds, that every step
  * moves a card rather than copying it, and that the rules accept the
  * result as a real game. */
class TableSuite extends munit.FunSuite:

  test("the quiet table: p1's Act, each pawn on its own site, nothing at the sites and no advisers"):
    val look = Look(Table.start.ready)
    assertEquals(look.active, p1)
    assertEquals(look.phase, Phase.Act)
    assertEquals(Vector(p1, p2, p3).map(look.pawn),
      Vector("Ancient City", "Broken Peaks", "Buried Giant").map(
        CatalogNames.site(_)))
    Table.start.ready.game.current.map.inPlay.foreach { site =>
      assertEquals(look.denizens(site), Vector.empty, site.value)
      assertEquals(look.relicsAt(site), Vector.empty, site.value)
      assertEquals(look.forces(site), SiteForces.Empty, site.value)
      assertEquals(look.siteTokens(site), Tokens.empty, site.value)
    }
    Vector(p1, p2, p3).foreach { p =>
      assertEquals(look.advisers(p), Vector.empty)
      assertEquals(look.relics(p), Vector.empty)
    }
    val current = Table.start.ready.game.current
    assertEquals(current.temporaryHands, Map.empty)
    assert(Region.all.forall(current.commonCards.discard(_).isEmpty))
    assertEquals(current.title.holder, None)
    assertEquals((current.banners.peoplesFavor.holder,
      current.banners.darkestSecret.holder), (None, None))

  test("the turn passes p1, p2, p3"):
    assertEquals(FinishRestProcedure.turnOrder(Table.start.ready),
      Vector(p1, p2, p3))

  test("boards keep their printed start: 1 favor, 1 faceup secret, 3 warbands, 7 Supply"):
    val look = Look(Table.start.ready)
    assertEquals((look.favor(p1), look.faceUpSecrets(p1), look.warbands(p1),
      look.supply(p1)), (1, 1, 3, 7))

  test("the quiet table holds exactly the cards the game started with"):
    val started = GameStartRules.evolve(catalog, FirstGameSetupFixture.chronicle,
      FirstGameSetupFixture.orders).toOption.get
    assertEquals(CardIndex.from(Table.start.ready.game).toOption.get.ids,
      CardIndex.from(started.game).toOption.get.ids)

  test("placing a card takes it out of the zone that held it"):
    val dealt = Table.start.ready.game.current.commonCards.worldDeck
      .collectFirst { case id: DenizenId => id }.get
    val advised = Table.start.adviser(p1, dealt)
    assert(!advised.ready.game.current.commonCards.worldDeck.contains(dealt))
    val moved = advised.denizen(dealt, at = "Dunes")
    assertEquals(Look(moved.ready).advisers(p1), Vector.empty)
    assertEquals(Look(moved.ready).denizens("Dunes"), Vector(dealt))

  test("placing a card takes it from a discard, a player's relics and every viewer's memory"):
    val discarded = Table.start.update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.filterNot(
          _ == CatalogNames.denizen("Alchemist")),
        regionalDiscards = c.commonCards.regionalDiscards.updated(
          Region.Cradle, Vector(CatalogNames.denizen("Alchemist")))))))
    val placed = discarded.adviser(p1, "Alchemist").ready
    assertEquals(placed.game.current.commonCards.discard(Region.Cradle), Vector.empty)
    val peeked = Table.start.relic(p2, "Brass Horse", facedown = true)
      .update(r => r.copy(knowledge = r.knowledge.copy(heldRelics =
        Map(p1 -> Vector(CatalogNames.relic("Brass Horse"))))))
    val moved = peeked.relicAt("Brass Horse", at = "Dunes").ready
    assertEquals(Look(moved).relics(p2), Vector.empty)
    assertEquals(moved.knowledge.heldRelics(p1), Vector.empty)

  test("a card the first game did not deal joins the table"):
    val inGame = CardIndex.from(Table.start.ready.game).toOption.get.ids
    val outside = catalog.denizens.map(d => DenizenId(d.id.value))
      .find(id => !inGame(id)).get
    val ready = Table.start.adviser(p1, outside).ready
    assertEquals(Look(ready).advisers(p1), Vector(outside))

  test("a card in two places is rejected when the state is read"):
    val card = CatalogNames.denizen("Alchemist")
    val twice = Table.start.adviser(p1, card).update(_.updateCurrent(c =>
      c.copy(players = c.players.map(p => if p.player == p2 then
        p.copy(advisers = Vector(DenizenState(card, Orientation.FaceUp,
          Tokens.empty))) else p))))
    val failure = intercept[munit.FailException](twice.ready)
    assert(failure.getMessage.contains("Alchemist"), failure.getMessage)

  test("a lost card is rejected when the state is read"):
    val lost = Table.start.update(_.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(worldDeck = c.commonCards.worldDeck.tail))))
    val failure = intercept[munit.FailException](lost.ready)
    assert(failure.getMessage.contains("is missing"), failure.getMessage)

  test("more warbands than the printed supply are rejected when the state is read"):
    val bandits = intercept[munit.FailException](
      Table.start.bandits("Dunes", 25).ready)
    assert(bandits.getMessage.contains("Bandit"), bandits.getMessage)
    // A lineage prints 14: 12 on the board and 3 at a site are one too many.
    val exiles = intercept[munit.FailException](
      Table.start.warbands(p1, 12).warbandsAt("Dunes", p1, 3).ready)
    assert(exiles.getMessage.contains("15 warbands"), exiles.getMessage)

  test("an edifice named by one face must be placed on that side"):
    val failure = intercept[munit.FailException](Table.start.edifice(
      "Hallowed Spring", EdificeSide.Ruined, at = "Dunes"))
    assert(failure.getMessage.contains("Hiding Place"), failure.getMessage)

  test("an unknown name fails and lists the closest names"):
    val failure = intercept[munit.FailException](CatalogNames.denizen("Mercenary"))
    assert(failure.getMessage.contains("Mercenaries"), failure.getMessage)

  test("each step states one fact"):
    val ready = Table.start
      .turn(p2, Phase.Wake)
      .pawn(p1, at = "Dunes")
      .adviser(p1, "Alchemist", facedown = true)
      .relic(p2, "Circlet of Command")
      .denizen("Magician's Code", at = "Dunes")
      .relicAt("Brass Horse", at = "Dunes")
      .edifice("Hallowed Spring", EdificeSide.Intact, at = "Fair Isle")
      .bandits("Fair Isle", 2)
      .warbandsAt("Dunes", p1, 3)
      .favor(p1, 4).secrets(p1, faceUp = 2, faceDown = 1).supply(p1, 5)
      .warbands(p1, 6)
      .tokens("Magician's Code", favor = 1)
      .siteTokens("Dunes", secrets = 2)
      .peoplesFavor(Some(p3), favor = 2)
      .oathkeeper(Some(p2))
      .bankFavor(Suit.Arcane, 1)
      .ready
    val look = Look(ready)
    assertEquals((look.active, look.phase), (p2, Phase.Wake))
    assertEquals(look.pawn(p1), CatalogNames.site("Dunes"))
    assertEquals(look.player(p1).advisers, Vector(DenizenState(
      CatalogNames.denizen("Alchemist"), Orientation.FaceDown, Tokens.empty)))
    assertEquals(look.relics(p2), Vector(CatalogNames.relic("Circlet of Command")))
    assertEquals(look.denizens("Dunes"),
      Vector(CatalogNames.denizen("Magician's Code")))
    assertEquals(look.relicsAt("Dunes"), Vector(CatalogNames.relic("Brass Horse")))
    assertEquals(look.denizens("Fair Isle"),
      Vector(CatalogNames.edifice("Hallowed Spring")))
    assertEquals(look.forces("Fair Isle"), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(look.forces("Dunes"), SiteForces.Occupied(
      ForceKind.Exile(look.player(p1).lineage), 3))
    assertEquals((look.favor(p1), look.faceUpSecrets(p1),
      look.faceDownSecrets(p1), look.supply(p1), look.warbands(p1)),
      (4, 2, 1, 5, 6))
    assertEquals(look.tokensOn("Magician's Code"), Tokens(1, 0))
    assertEquals(look.siteTokens("Dunes"), Tokens(0, 2))
    assertEquals(ready.game.current.banners.peoplesFavor.holder, Some(p3))
    assertEquals(ready.game.current.title.holder, Some(p2))
    assertEquals(ready.banks.favor(Suit.Arcane), 1)

  test("the world deck's top can be named"):
    val ready = Table.start.worldDeckTop("Alchemist", "Magician's Code").ready
    assertEquals(ready.game.current.commonCards.worldDeck.take(2), Vector(
      CatalogNames.denizen("Alchemist"), CatalogNames.denizen("Magician's Code")))

  test("real commands run from a table: Rest passes the turn to p2"):
    val rested = Table.start.situation(Situation.rules(catalog))
      .after(GameCommand.BeginRest(p1))
    assertEquals(Look(rested.state).active, p2)

  test("real commands run from a table: Muster from a denizen at the pawn's site"):
    val mustered = Table.start.denizen("Alchemist", at = Table.homeOf(p1))
      .situation(Situation.rules(catalog))
      .after(GameCommand.StartWalker(ActionRef.Muster, StartPayload(p1)))
    val look = Look(mustered.state)
    assertEquals((look.favor(p1), look.warbands(p1), look.supply(p1)), (0, 4, 6))
    assertEquals(look.tokensOn("Alchemist"), Tokens(1, 0))

  test("real commands run from a table: Travel moves the pawn"):
    val travelled = Table.start.situation(Situation.rules(catalog))
      .after(GameCommand.StartWalker(ActionRef.Travel, StartPayload(p1,
        startArgs = Vector(DecisionOptionRef.Site(CatalogNames.site("Dunes"))))))
    assertEquals(Look(travelled.state).pawn(p1), CatalogNames.site("Dunes"))

  test("a journaled situation cannot start at a table, since a stream begins with GameStarted"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val driver = Situation.journaled(service, catalog, repository, "table")
    intercept[munit.FailException](Table.start.situation(driver))
