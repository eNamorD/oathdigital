package oathdigital.testkit

import oathdigital.application.{GameApplicationService, GameCommand,
  InMemoryEventStreamRepository, ParkedServiceFixture, StartPayload}
import oathdigital.gameplay.StateBasedEvaluation
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import Table.{p1, p2, p3}
import Unwrap.{accepted, present}

/** `Table` builds rule-test states from the real game start without the
  * setup walk. These tests pin what the quiet table holds, that every step
  * moves a card rather than copying it, and that the rules accept the
  * result as a real game. */
class TableSuite extends munit.FunSuite:

  test("the quiet table: p1's Act, each pawn on its own site, nothing at the " +
      "sites, and no advisers, relics, hands, discards, Oathkeeper or banners"):
    val look = Look(Table.start.ready)
    assertEquals(look.active, p1)
    assertEquals(look.phase, Phase.Act)
    assertEquals(Vector(p1, p2, p3).map(look.pawn),
      Vector("Ancient City", "Broken Peaks", "Buried Giant").map(
        CatalogNames.site(_)))
    Table.start.ready.game.current.map.inPlay.foreach { site =>
      assertEquals(look.cardsAt(site), Vector.empty, site.value)
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

  test("banditsRefilled leaves the refill nothing to add and keeps the forces " +
      "already at a site"):
    val held = Table.start.warbandsAt("Ancient City", p1, 1).boardWarbands(p1, 2)
    val table = held.banditsRefilled
    assertEquals(StateBasedEvaluation.banditRefill(catalog, table.state),
      Right(None))
    val look = Look(table.ready)
    assertEquals(look.forces(CatalogNames.site("Ancient City")),
      Look(held.ready).forces(CatalogNames.site("Ancient City")))
    assertEquals(look.forces(CatalogNames.site("Broken Peaks")),
      SiteForces.Occupied(ForceKind.Bandit,
        present(catalog.site(CatalogNames.site("Broken Peaks")),
          "Broken Peaks").capacity))

  test("relicDeckTop puts relics on top of the relic deck, first named on top"):
    val ready = Table.start.relicDeckTop("Dowsing Sticks", "Sticky Fire").ready
    assertEquals(ready.game.current.commonCards.relicDeck.take(2),
      Vector(CatalogNames.relic("Dowsing Sticks"),
        CatalogNames.relic("Sticky Fire")))

  test("the turn passes p1, p2, p3"):
    assertEquals(FinishRestProcedure.turnOrder(Table.start.ready),
      Vector(p1, p2, p3))

  test("boards keep their printed start: 1 favor, 1 faceup secret, 3 warbands, 7 Supply"):
    val look = Look(Table.start.ready)
    assertEquals((look.favor(p1), look.faceUpSecrets(p1), look.boardWarbands(p1),
      look.supply(p1)), (1, 1, 3, 7))

  test("the quiet table holds exactly the cards the game started with"):
    // Starts the game from the Chronicle: the start it was built from is the
    // thing compared.
    val started = accepted(GameStartRules.evolve(catalog,
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders))
    assertEquals(accepted(CardIndex.from(Table.start.ready.game)).ids,
      accepted(CardIndex.from(started.game)).ids)

  test("placing a card takes it out of the zone that held it"):
    // Threatening Roar tops the quiet table's world deck.
    val dealt = CatalogNames.denizen("Threatening Roar")
    assertEquals(
      Table.start.ready.game.current.commonCards.worldDeck.headOption,
      Some(dealt))
    val advised = Table.start.adviser(p1, dealt)
    assert(!advised.ready.game.current.commonCards.worldDeck.contains(dealt))
    val moved = advised.denizen(dealt, at = "Dunes")
    assertEquals(Look(moved.ready).advisers(p1), Vector.empty)
    assertEquals(Look(moved.ready).cardsAt("Dunes"), Vector(dealt))

  test("placing a card takes it from a discard, a player's relics and every viewer's memory"):
    val discarded = Table.start.discarded(Region.Cradle, "Alchemist")
    val placed = discarded.adviser(p1, "Alchemist").ready
    assertEquals(placed.game.current.commonCards.discard(Region.Cradle), Vector.empty)
    // No Table step records knowledge of a held relic.
    val peeked = Table.start.relic(p2, "Brass Horse", facedown = true)
      .update(r => r.copy(knowledge = r.knowledge.copy(heldRelics =
        Map(p1 -> Vector(CatalogNames.relic("Brass Horse"))))))
    val moved = peeked.relicAt("Brass Horse", at = "Dunes").ready
    assertEquals(Look(moved).relics(p2), Vector.empty)
    assertEquals(moved.knowledge.heldRelics(p1), Vector.empty)

  test("a card the first game did not deal joins the table"):
    val inGame = accepted(CardIndex.from(Table.start.ready.game)).ids
    // The first game does not deal Forced Labor.
    val outside = CatalogNames.denizen("Forced Labor")
    assert(!inGame(outside))
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

  test("unchecked reads a state a fixture is still assembling, which ready refuses"):
    val lost = Table.start.update(_.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(worldDeck = c.commonCards.worldDeck.tail))))
    assertEquals(lost.unchecked.game.current.commonCards.worldDeck.size,
      Table.start.ready.game.current.commonCards.worldDeck.size - 1)
    intercept[munit.FailException](lost.ready)

  test("more warbands than the printed supply are rejected when the state is read"):
    // Bandits print 24, so 25 at one site are one too many.
    val bandits = intercept[munit.FailException](
      Table.start.bandits("Dunes", MaterialBankState.BanditWarbands + 1).ready)
    assert(bandits.getMessage.contains("Bandit"), bandits.getMessage)
    // A lineage prints 14: 12 on the board and 3 at a site are one too many.
    val exiles = intercept[munit.FailException](
      Table.start.boardWarbands(p1, 12).warbandsAt("Dunes", p1, 3).ready)
    assert(exiles.getMessage.contains("15 warbands"), exiles.getMessage)

  test("banksHoldingOnly stocks the banks named and empties the rest"):
    val banks = Table.start.banksHoldingOnly(Suit.Order -> 5, Suit.Hearth -> 2)
      .ready.banks.favor
    assertEquals(Suit.all.map(banks(_)), Suit.all.map {
      case Suit.Order => 5
      case Suit.Hearth => 2
      case _ => 0
    })

  test("warbandsInBank fills the board until the bank holds exactly n"):
    // A lineage prints 14: 2 at a site and 4 left in the bank leave 8 for
    // the board.
    val table = Table.start.warbandsAt("Ancient City", p1, 2)
      .warbandsInBank(p1, 4)
    assertEquals(Look(table.ready).boardWarbands(p1), 8)
    // It gives warbands back too: 10 on the board leave 4 in the bank, and
    // a bank of 6 takes 2 of them.
    val returned = Table.start.boardWarbands(p1, 10).warbandsInBank(p1, 6)
    assertEquals(Look(returned.ready).boardWarbands(p1), 8)

  test("an edifice named by one face must be placed on that side"):
    // Hiding Place is the ruined face of Hallowed Spring.
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
      .boardWarbands(p1, 6)
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
    assertEquals(look.cardsAt("Dunes"),
      Vector(CatalogNames.denizen("Magician's Code")))
    assertEquals(look.relicsAt("Dunes"), Vector(CatalogNames.relic("Brass Horse")))
    assertEquals(look.cardsAt("Fair Isle"),
      Vector(CatalogNames.edifice("Hallowed Spring")))
    assertEquals(look.forces("Fair Isle"), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(look.forces("Dunes"), SiteForces.Occupied(
      ForceKind.Exile(look.player(p1).lineage), 3))
    assertEquals((look.favor(p1), look.faceUpSecrets(p1),
      look.faceDownSecrets(p1), look.supply(p1), look.boardWarbands(p1)),
      (4, 2, 1, 5, 6))
    assertEquals(look.tokensOn("Magician's Code"), Tokens(1, 0))
    assertEquals(look.siteTokens("Dunes"), Tokens(0, 2))
    assertEquals(ready.game.current.banners.peoplesFavor.holder, Some(p3))
    assertEquals(ready.game.current.title.holder, Some(p2))
    assertEquals(ready.banks.favor(Suit.Arcane), 1)

  test("a viewer can know a relic lying at a site"):
    val ready = Table.start.relicAt("Sticky Fire", at = "Dunes")
      .knowsRelicAt(p2, "Sticky Fire", at = "Dunes").ready
    assertEquals(ready.knowledge.siteRelics(p2)(CatalogNames.site("Dunes")),
      Vector(CatalogNames.relic("Sticky Fire")))
    intercept[munit.FailException](
      Table.start.knowsRelicAt(p2, "Sticky Fire", at = "Dunes"))

  test("a card can be put in a player's hand"):
    val ready = Table.start.hand(p1, "Alchemist", "Magician's Code").ready
    assertEquals(ready.game.current.temporaryHands(p1), Vector(
      CatalogNames.denizen("Alchemist"), CatalogNames.denizen("Magician's Code")))
    assert(!ready.game.current.commonCards.worldDeck.contains(
      CatalogNames.denizen("Alchemist")))

  test("the world deck's top can be named"):
    val ready = Table.start.worldDeckTop("Alchemist", "Magician's Code").ready
    assertEquals(ready.game.current.commonCards.worldDeck.take(2), Vector(
      CatalogNames.denizen("Alchemist"), CatalogNames.denizen("Magician's Code")))

  test("real commands run from a table: Rest passes the turn to p2"):
    val rested = Table.start.situation(Situation.rules(catalog))
      .after(GameCommand.BeginRest(p1))
    assertEquals(Look(rested.state).active, p2)

  test("real commands run from a table: Muster from a denizen at the pawn's site"):
    val mustered = Table.start.pawn(p1, "Ancient City")
      .denizen("Alchemist", at = "Ancient City")
      .situation(Situation.rules(catalog))
      .after(GameCommand.StartWalker(ActionRef.Muster, StartPayload(p1)))
    val look = Look(mustered.state)
    // From the quiet start of 1 favor, 3 warbands and 7 Supply: the favor
    // moves onto the card, one warband joins the board, and Muster costs
    // 1 Supply.
    assertEquals((look.favor(p1), look.boardWarbands(p1), look.supply(p1)), (0, 4, 6))
    assertEquals(look.tokensOn("Alchemist"), Tokens(1, 0))

  test("real commands run from a table: Travel moves the pawn"):
    val travelled = Table.start.situation(Situation.rules(catalog))
      .parkedAfter(ParkedServiceFixture.startTravel(p1))
      .after(ParkedServiceFixture.pickTravelDestination(p1,
        CatalogNames.site("Dunes")))
    assertEquals(Look(travelled.state).pawn(p1), CatalogNames.site("Dunes"))

  test("a journaled situation cannot start at a table, since a stream begins with GameStarted"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val driver = Situation.journaled(service, catalog, repository, "table")
    intercept[munit.FailException](Table.start.situation(driver))

  test("Look.discardAfter reads the discard pile of the region after the site's"):
    val table = Table.start.discarded(Region.Provinces, "Wolves")
    assertEquals(Look(table.ready).discardAfter("Ancient City"),
      Vector(CatalogNames.worldCard("Wolves")))

  test("Look.forceKind reads the kind of a player's own warbands"):
    val look = Look(Table.start.ready)
    assertNotEquals(look.forceKind(p1), look.forceKind(p2))
    assertNotEquals(look.forceKind(p1), ForceKind.Bandit)

  test("relicDeckEmptied moves the relic deck to the reliquary, losing no relic"):
    val before = Table.start.ready
    val after = Table.start.relicDeckEmptied.ready
    assertEquals(after.game.current.commonCards.relicDeck, Vector.empty)
    assertEquals(after.game.campaign.reliquary,
      before.game.campaign.reliquary ++ before.game.current.commonCards.relicDeck)

  test("parkedWalker parks the action's walker at the given step"):
    val current = Table.start.parkedWalker(ActionRef.Recover).ready.game.current
    assertEquals(current.walkerProcedure, Some(ActionRef.Recover))
    assertEquals(current.walkerPending,
      Some(PendingTree(Vector("0"), Vector.empty)))

  test("visionsDrawn sets the Visions Drawn track"):
    assertEquals(Table.start.visionsDrawn(3).ready.game.current.tracks
      .visionsDrawn, 3)

  test("revealedVision reveals the Vision faceup and takes it from the deck"):
    val current = Table.start.revealedVision(p1, VisionRules.Faith).ready
      .game.current
    assertEquals(current.players.find(_.player == p1).flatMap(_.revealedVision),
      Some(VisionState(VisionRules.Faith, Orientation.FaceUp)))
    assert(!current.commonCards.worldDeck.contains(VisionRules.Faith))

  test("backInPhase changes the phase and keeps the powers used this turn"):
    val wolves = CatalogNames.denizen("Wolves")
    val use = PowerUseRef(PowerTiming.Act, PowerSourceRef.Card(wolves),
      PowerId("denizen.wolves"))
    // The used power is the subject: no step records a use.
    val used = Table.start.update(_.updateCurrent(c => c.copy(turn =
      c.turn.copy(usedPowers = Set(use)))))
    val turn = used.backInPhase(Phase.Wake).ready.game.current.turn
    assertEquals(turn.phase, Phase.Wake)
    assertEquals(turn.usedPowers, Set(use))

  test("the quiet table's banners lie on the faces the banner steps default to"):
    val banners = Table.start.ready.game.current.banners
    assertEquals(banners.peoplesFavor.active, PeoplesFavorFace.Mob)
    assertEquals(banners.darkestSecret.active, DarkestSecretFace.WanderingFlame)

  test("the banner steps set the face they are given"):
    val banners = Table.start
      .peoplesFavor(Some(p1), 2, face = PeoplesFavorFace.GrandCouncil)
      .darkestSecret(Some(p2), 1, face = DarkestSecretFace.Festival)
      .ready.game.current.banners
    assertEquals(banners.peoplesFavor.active, PeoplesFavorFace.GrandCouncil)
    assertEquals(banners.darkestSecret.active, DarkestSecretFace.Festival)

  test("Look.tokensOn fails for a card at no site and in no play area"):
    // The quiet table deals no denizen to a site or a player.
    intercept[munit.FailException](Look(Table.start.ready).tokensOn("Alchemist"))
