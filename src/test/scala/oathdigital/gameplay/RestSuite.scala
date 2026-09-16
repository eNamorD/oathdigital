package oathdigital.gameplay

import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent.IgnoredRulesRecorded
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation.{UnsupportedRestState,
  UnsupportedRoundEndCatalogInventory,
  UnsupportedRuleCatalog}
import oathdigital.catalog.CatalogPower

class RestSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def act: ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    initial.copy(game = initial.game.copy(current = initial.game.current.copy(
      turn = initial.game.current.turn.copy(phase = Phase.Act))))
  }

  private def rest(state: OathState, player: PlayerId,
      using: OathRules = rules) =
    using.startWalker(state, PhaseTransitionRef.BeginRest, player)

  /** Picks `count` denizens still sitting in the world deck (never physically
    * placed) and removes them from that deck, so a prepared state stays
    * CardIndex-consistent when those cards are placed elsewhere. When
    * `minSuits` exceeds one, the picks span that many distinct catalog suits
    * (one from each group first, then any remaining from the leftover pool).
    */
  private def takeUnplacedDenizens(base: ReadyGame, count: Int,
      minSuits: Int = 1): (Vector[DenizenId], ReadyGame) = {
    val deck = base.game.current.commonCards.worldDeck.collect {
      case id: DenizenId => id
    }
    val groups = deck.groupBy { id => catalog.denizens
      .find(_.id.value == id.value).map(_.suit.value).getOrElse("") }
      .toVector.sortBy(_._1).map(_._2)
    val representatives = groups.take(minSuits).flatMap(_.headOption)
    val remainder = groups.flatten.filterNot(representatives.toSet)
    val chosen = (representatives ++ remainder).take(count)
    val worldDeck = base.game.current.commonCards.worldDeck.filterNot(
      id => chosen.contains(id))
    chosen -> base.copy(game = base.game.copy(current =
      base.game.current.copy(commonCards =
        base.game.current.commonCards.copy(worldDeck = worldDeck))))
  }

  /** Picks `count` relics still in the relic deck and removes them from it. */
  private def takeUnplacedRelics(base: ReadyGame, count: Int)
      : (Vector[RelicId], ReadyGame) = {
    val chosen = base.game.current.commonCards.relicDeck.take(count)
    val relicDeck = base.game.current.commonCards.relicDeck.filterNot(
      id => chosen.contains(id))
    chosen -> base.copy(game = base.game.copy(current =
      base.game.current.copy(commonCards =
        base.game.current.commonCards.copy(relicDeck = relicDeck))))
  }

  /** Picks an edifice still in the edifice deck and removes it. */
  private def takeUnplacedEdifice(base: ReadyGame)
      : (EdificeId, ReadyGame) = {
    val chosen = base.game.current.commonCards.edificeDeck.head
    val edificeDeck = base.game.current.commonCards.edificeDeck.filterNot(
      _ == chosen)
    chosen -> base.copy(game = base.game.copy(current =
      base.game.current.copy(commonCards =
        base.game.current.commonCards.copy(edificeDeck = edificeDeck))))
  }

  test("Rest returns controlled resources reveals secrets refreshes and wakes next") {
    val base0 = act
    val actor = base0.game.current.players.find(
      _.player == base0.game.current.turn.activePlayer).get
    val adviserId = actor.advisers.collectFirst {
      case DenizenState(id, _, _) => id
    }.get
    val siteId = actor.pawnSite.get
    // Pick an unplaced denizen for the site and an unplaced relic for the
    // resting player's holdings, and filter both from their decks so the
    // prepared state stays CardIndex-consistent.
    val (siteCards, base) = takeUnplacedDenizens(base0, 1)
    val (heldRelics, withRelics) = takeUnplacedRelics(base, 1)
    val siteCardId = siteCards.find(_ != adviserId).get
    val heldRelic = heldRelics.head
    val site = withRelics.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(actor.lineage), 2),
      denizens = Vector(DenizenState(siteCardId, Orientation.FaceUp, Tokens(2, 1))))
    val prepared = withRelics.copy(game = withRelics.game.copy(current =
      withRelics.game.current.copy(
        map = withRelics.game.current.map.copy(sites =
          withRelics.game.current.map.sites.updated(siteId, site)),
        players = withRelics.game.current.players.map { player =>
          if (player.player != actor.player) player
          else player.copy(
            board = player.board.copy(faceDownSecrets = 2,
              supply = SupplyTrack(1)),
            advisers = Vector(DenizenState(adviserId, Orientation.FaceUp,
              Tokens(1, 2))),
            relics = Vector(RelicState(heldRelic,
              Orientation.FaceUp, Tokens(0, 3))))
        })))

    val completed = rest(Ready(prepared), actor.player).toOption.get
    assertEquals(completed.events.collect { case WalkerCompleted(p) => p },
      Vector(PhaseTransitionRef.BeginRest, PhaseTransitionRef.FinishRest))
    val Ready(after) = completed.state: @unchecked
    val rested = after.game.current.players.find(_.player == actor.player).get

    assertEquals(rested.board.faceDownSecrets, 0)
    assertEquals(rested.board.faceUpSecrets,
      actor.board.faceUpSecrets + 2 + 6)
    assertEquals(rested.board.supply, SupplyTrack.full)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, actor.player)
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    assertEquals(after.banks.favor.values.sum,
      prepared.banks.favor.values.sum + 3)
  }

  test("Rest rejects a missing bounded warband supply") {
    val ready = act
    val actor = ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get
    val malformed = ready.copy(banks = ready.banks.copy(warbandSupply =
      ready.banks.warbandSupply - ForceKind.Exile(actor.lineage)))

    assert(rest(Ready(malformed), actor.player)
      .left.toOption.get.isInstanceOf[UnsupportedRestState])
  }

  test("Rest globally cleans every in-play denizen and relic") {
    val base0 = act
    val actor = base0.game.current.players.find(
      _.player == base0.game.current.turn.activePlayer).get
    val pawn = actor.pawnSite.get
    val ruled = base0.game.current.map.sites.keys.find(_ != pawn).get
    val outside = base0.game.current.map.sites.keys.find(id => id != pawn && id != ruled).get
    val otherBefore = base0.game.current.players.find(_.player != actor.player).get
    // All injected cards come from the still-in-deck pools (never physically
    // placed in the base first game) and are removed from those decks, so the
    // prepared state satisfies the executor's CardIndex uniqueness invariant.
    val (denizenPool, withDenizens) = takeUnplacedDenizens(base0, 5, minSuits = 3)
    val (edificePick, withEdifice) = takeUnplacedEdifice(withDenizens)
    val (relicPool, withRelics) = takeUnplacedRelics(withEdifice, 3)
    val adviser = DenizenState(denizenPool(0), Orientation.FaceUp, Tokens(1, 1))
    val pawnCard = DenizenState(denizenPool(1), Orientation.FaceUp, Tokens(2, 2))
    val pawnEdifice = EdificeState(edificePick, EdificeSide.Ruined, Tokens(1, 1))
    val ruledCard = DenizenState(denizenPool(2), Orientation.FaceUp, Tokens(3, 3))
    val outsideCard = DenizenState(denizenPool(3), Orientation.FaceUp, Tokens(4, 4))
    val relic = RelicState(relicPool(0), Orientation.FaceUp, Tokens(7, 2))
    val otherAdviser = DenizenState(denizenPool(4), Orientation.FaceUp, Tokens(5, 5))
    val otherRelic = RelicState(relicPool(1), Orientation.FaceUp, Tokens(0, 6))
    val siteRelic = RelicState(relicPool(2), Orientation.FaceDown, Tokens(0, 7))
    val prepared = withRelics.copy(game = withRelics.game.copy(current = withRelics.game.current.copy(
      map = withRelics.game.current.map.copy(sites = withRelics.game.current.map.sites
        .updated(pawn, withRelics.game.current.map.sites(pawn).copy(
          forces = SiteForces.Occupied(ForceKind.Bandit, 1),
          denizens = Vector(pawnCard, pawnEdifice)))
        .updated(ruled, withRelics.game.current.map.sites(ruled).copy(
          forces = SiteForces.Occupied(ForceKind.Exile(actor.lineage), 1),
          denizens = Vector(ruledCard)))
        .updated(outside, withRelics.game.current.map.sites(outside).copy(
          forces = SiteForces.Occupied(ForceKind.Bandit, 1),
          denizens = Vector(outsideCard), relics = Vector(siteRelic)))),
      players = withRelics.game.current.players.map { p =>
        if (p.player == actor.player)
          p.copy(board = p.board.copy(faceDownSecrets = 2), advisers = Vector(adviser),
            relics = Vector(relic))
        else if (p.player == otherBefore.player)
          p.copy(advisers = Vector(otherAdviser), relics = Vector(otherRelic))
        else p
      })))
    val plan = RestCleanupPlan.derive(catalog, prepared, actor.player).toOption.get
    assertEquals(plan.returnedSecrets, 31)
    assertEquals(plan.returnedFavor.values.sum, 16)
    val finished = rest(Ready(prepared), actor.player).toOption.get
    val Ready(after) = finished.state: @unchecked
    val rested = after.game.current.players.find(_.player == actor.player).get
    assertEquals(rested.board.faceDownSecrets, 0)
    assertEquals(rested.board.faceUpSecrets,
      actor.board.faceUpSecrets + 2 + plan.returnedSecrets)
    assertEquals(rested.advisers.collect { case d: DenizenState => d.tokens },
      Vector(Tokens.empty))
    assertEquals(rested.relics.head.tokens, Tokens(7, 0))
    assert(after.game.current.map.sites(pawn).denizens.forall(_.tokens.isEmpty))
    assert(after.game.current.map.sites(ruled).denizens.forall(_.tokens.isEmpty))
    assert(after.game.current.map.sites(outside).denizens.forall(_.tokens.isEmpty))
    assertEquals(after.game.current.map.sites(outside).relics.head.tokens,
      Tokens.empty)
    plan.returnedFavor.foreach { case (suit, amount) =>
      assertEquals(after.banks.favor(suit),
        prepared.banks.favor(suit) + amount)
    }
    assert(plan.returnedFavor.size >= 2)
    val otherAfter = after.game.current.players.find(_.player == otherBefore.player).get
    assertEquals(otherAfter.board, otherBefore.board)
    assert(otherAfter.advisers.collect {
      case denizen: DenizenState => denizen.tokens
    }.forall(_.isEmpty))
    assert(otherAfter.relics.forall(_.tokens.isEmpty))
  }

  test("replaying a round of walker Rests reproduces the state and advances the round") {
    var state: OathState = Ready(act)
    var events = Vector.empty[OathEvent]
    val participants = act.game.current.players.map(_.player)
    val start = participants.indexOf(act.setup.firstPlayer)
    val order = participants.drop(start) ++ participants.take(start)
    order.foreach { player =>
      val rested = rest(state, player).toOption.get
      events ++= rested.events
      state = rested.state
      if (player != order.last) {
        val woke = rules.startWalker(state, PhaseTransitionRef.EndWake,
          state.asInstanceOf[Ready].value.game.current.turn.activePlayer).toOption.get
        events ++= woke.events
        state = woke.state
      }
    }
    val Ready(after) = state: @unchecked
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.setup.firstPlayer)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((next, event) => next.flatMap(rules.evolve(_, event))),
      Right(state))
  }

  test("unrelated active powers and the end-die Arbiter legacy do not block") {
    val base = act
    val lineage = base.game.campaign.lineages.values.head
    val unrelatedHandlers = Set(
      "denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.silver-tongue", "denizen.insomnia",
      "denizen.vow-of-obedience")
    val unrelated = base.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId if catalog.denizens.find(_.id.value == id.value)
          .exists(d => !unrelatedHandlers.exists(d.handlers.contains)) => id
    }.get
    val supported = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
        lineage.id, lineage.copy(legacies = Vector(
          LegacyState(LegacyId("L23"), active = true))))), current =
      base.game.current.copy(commonCards = base.game.current.commonCards.copy(
        worldDeck = base.game.current.commonCards.worldDeck.filterNot(_ == unrelated)),
        players = base.game.current.players.map { player =>
        if (player.player != base.game.current.turn.activePlayer) player
        else player.copy(advisers = Vector(DenizenState(
          unrelated, Orientation.FaceUp, Tokens.empty)))
      })))
    rest(Ready(supported), supported.game.current.turn.activePlayer)
      .fold(error => fail(error.toString), _ => ())
  }

  test("each relevant Rest handler records fallback diagnostics without blocking") {
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val relevant = Set("denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.insomnia", "denizen.vow-of-obedience")
    relevant.foreach { handler =>
      val definition = catalog.denizens.find(_.handlers.contains(handler)).get
      val adviser = DenizenState(DenizenId(definition.id.value),
        Orientation.FaceUp, Tokens.empty)
      val state = base.copy(game = base.game.copy(current = base.game.current.copy(
        players = base.game.current.players.map(p => if (p.player == actor.player)
          p.copy(advisers = Vector(adviser)) else p))))
      val accepted = rest(Ready(state), actor.player).toOption.get
      val recorded = accepted.events.head.asInstanceOf[IgnoredRulesRecorded]
      assertEquals(recorded.diagnostics.head.handlerId, handler)
      assert(recorded.diagnostics.head.source.stableKey
        .startsWith(s"adviser:${actor.player.value}:"))
      assertEquals(rules.evolve(Ready(state), recorded), Right(Ready(state)))
      val tampered = recorded.copy(diagnostics = recorded.diagnostics.map(
        _.copy(handlerId = "denizen.tampered")))
      assert(rules.evolve(Ready(state), tampered).isLeft)
    }
    val handler = "denizen.insomnia"
    val definition = catalog.denizens.find(_.handlers.contains(handler)).get
    val siteId = actor.pawnSite.get
    val siteState = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        siteId, base.game.current.map.sites(siteId).copy(denizens = Vector(
          DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
            Tokens.empty))))))))
    val siteAccepted = rest(Ready(siteState), actor.player)
      .toOption.get.events.head.asInstanceOf[IgnoredRulesRecorded]
    assert(siteAccepted.diagnostics.head.source.stableKey
      .startsWith(s"site-card:${siteId.value}:"))
  }

  test("changed inventory in every catalog family fails before runtime discovery") {
    val base = act
    val actor = base.game.current.turn.activePlayer
    def rejects(c: oathdigital.catalog.ExecutableCatalog) =
      rest(Ready(base), actor, new OathRules(c))
        .left.toOption.exists(error =>
          error.isInstanceOf[UnsupportedRoundEndCatalogInventory] ||
          error.isInstanceOf[UnsupportedRuleCatalog])
    val changed = Vector(
      catalog.copy(denizens = catalog.denizens.updated(0,
        catalog.denizens.head.copy(powers = catalog.denizens.head.powers :+
          CatalogPower("test.changed-denizen", persistent = false, "Changed.")))),
      catalog.copy(relics = catalog.relics.updated(0,
        catalog.relics.head.copy(powers = catalog.relics.head.powers :+
          CatalogPower("test.changed-relic", persistent = false, "Changed.")))),
      catalog.copy(edifices = catalog.edifices.updated(0, catalog.edifices.head.copy(
        intact = catalog.edifices.head.intact.copy(
          powers = catalog.edifices.head.intact.powers :+
            CatalogPower("test.changed-intact", persistent = false, "Changed."))))),
      catalog.copy(edifices = catalog.edifices.updated(0, catalog.edifices.head.copy(
        ruined = catalog.edifices.head.ruined.copy(
          powers = catalog.edifices.head.ruined.powers :+
            CatalogPower("test.changed-ruined", persistent = false, "Changed."))))),
      catalog.copy(legacies = catalog.legacies.updated(0,
        catalog.legacies.head.copy(powers = catalog.legacies.head.powers :+
          CatalogPower("test.changed-legacy", persistent = false, "Changed.")))),
      catalog.copy(sites = catalog.sites.updated(0,
        catalog.sites.head.copy(handlers = catalog.sites.head.handlers :+ "changed"))))
    assert(changed.forall(rejects))
  }

  test("altered banner and Foundation types record stable fallback identities") {
    val base = act
    val actor = base.game.current.turn.activePlayer
    def diagnostic(ready: ReadyGame) = rest(Ready(ready), actor)
      .toOption.get.events.head
      .asInstanceOf[IgnoredRulesRecorded].diagnostics.head
    val banner = base.copy(game = base.game.copy(current = base.game.current.copy(
      banners = base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(
          active = PeoplesFavorFace.GrandCouncil)))))
    assertEquals(diagnostic(banner).source.stableKey, "banner:peoples-favor")

    val number = base.game.campaign.foundations.keys.head
    val foundation = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(foundations = base.game.campaign.foundations.updated(
        number, FoundationState(FoundationFace.Altered, Set.empty)))))
    assertEquals(diagnostic(foundation).source.stableKey,
      s"foundation:${number.value}")
  }

  test("last player of round eight finishes the game by War Exhaustion") {
    val base = act
    val participants = base.game.current.players.map(_.player)
    val start = participants.indexOf(base.setup.firstPlayer)
    val last = (participants.drop(start) ++ participants.take(start)).last
    val unsupported = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        tracks = base.game.current.tracks.copy(round = 8),
        turn = TurnState(last, Phase.Act, Set.empty))))

    val deterministic = new OathRules(catalog, warExhaustionRandomPort =
      new WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.last
      })
    val finished = rest(Ready(unsupported), last, deterministic).toOption.get
    assert(finished.events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    assert(finished.events.exists(_.isInstanceOf[OathEvent.WarExhaustionResolved]))
    assert(finished.continue.isInstanceOf[OathContinue.GameFinished])
    val result = finished.events.collectFirst {
      case event: OathEvent.WarExhaustionResolved => event
    }.get
    assertEquals(result.kind, VictoryKind.RandomSelection)
    assertEquals(result.winner, result.randomCandidates.last)
    assertEquals(rest(finished.state, last, deterministic)
      .left.toOption.get, OathViolation.GameEnded)
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "round-eight", oathdigital.application.LoadedGame(
        Ready(unsupported), 30L), last)
    assert(projection.legalControls.contains("beginRest"))
  }

}
