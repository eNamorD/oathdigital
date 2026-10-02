package oathdigital.gameplay

import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.UnsupportedRestState

class RestSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)

  /** p1's Act, with Wrestlers as p1's adviser. Broken Peaks holds two
    * secrets, so p2, waking next, has wealth to take and the Wake waits. */
  private def act: ReadyGame = Table.start
    .adviser(Table.p1, "Wrestlers")
    .siteTokens(Table.homeOf(Table.p2), secrets = 2)
    .ready

  private def rest(state: OathState, player: PlayerId,
      using: OathRules = rules) =
    using.startWalker(state, PhaseTransitionRef.BeginRest, player)

  private def inRest(ready: ReadyGame) = ready.updateCurrent(_.copy(
    turn = ready.game.current.turn.copy(phase = Phase.Rest)))
  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  /** Picks `count` denizens still sitting in the world deck (never physically
    * placed) and removes them from that deck, so a prepared state stays
    * CardIndex-consistent when those cards are placed elsewhere. When
    * `minSuits` exceeds one, the picks span that many distinct catalog suits
    * (one from each group first, then any remaining from the leftover pool).
    */
  private def takeUnplacedDenizens(base: ReadyGame, count: Int,
      minSuits: Int = 1): (Vector[DenizenId], ReadyGame) =
    val deck = base.game.current.commonCards.worldDeck.collect:
      case id: DenizenId => id
    val groups = deck.groupBy { id => catalog.denizens
      .find(_.id.value == id.value).map(_.suit.key).getOrElse("") }
      .toVector.sortBy(_._1).map(_._2)
    val representatives = groups.take(minSuits).flatMap(_.headOption)
    val remainder = groups.flatten.filterNot(representatives.toSet)
    val chosen = (representatives ++ remainder).take(count)
    val worldDeck = base.game.current.commonCards.worldDeck.filterNot(
      id => chosen.contains(id))
    chosen -> base.updateCurrent(_.copy(commonCards =
        base.game.current.commonCards.copy(worldDeck = worldDeck)))

  /** Picks `count` relics still in the relic deck and removes them from it. */
  private def takeUnplacedRelics(base: ReadyGame, count: Int)
      : (Vector[RelicId], ReadyGame) =
    val chosen = base.game.current.commonCards.relicDeck.take(count)
    val relicDeck = base.game.current.commonCards.relicDeck.filterNot(
      id => chosen.contains(id))
    chosen -> base.updateCurrent(_.copy(commonCards =
        base.game.current.commonCards.copy(relicDeck = relicDeck)))

  /** Picks an edifice still in the edifice deck and removes it. */
  private def takeUnplacedEdifice(base: ReadyGame)
      : (EdificeId, ReadyGame) =
    val chosen = base.game.current.commonCards.edificeDeck.head
    val edificeDeck = base.game.current.commonCards.edificeDeck.filterNot(
      _ == chosen)
    chosen -> base.updateCurrent(_.copy(commonCards =
        base.game.current.commonCards.copy(edificeDeck = edificeDeck)))

  test("Rest returns controlled resources reveals secrets refreshes and wakes next"):
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
    val prepared = withRelics.updateCurrent(_.copy(
        map = withRelics.game.current.map.copy(sites =
          withRelics.game.current.map.sites.updated(siteId, site)),
        players = withRelics.game.current.players.map { player =>
          if player.player != actor.player then player
          else player.copy(
            board = player.board.copy(faceDownSecrets = 2,
              supply = SupplyTrack(1)),
            advisers = Vector(DenizenState(adviserId, Orientation.FaceUp,
              Tokens(1, 2))),
            relics = Vector(RelicState(heldRelic,
              Orientation.FaceUp, Tokens(0, 3))))
        }))

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

  test("Rest rejects a missing bounded warband supply"):
    val ready = act
    val actor = ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get
    val malformed = ready.copy(banks = ready.banks.copy(warbandSupply =
      ready.banks.warbandSupply - ForceKind.Exile(actor.lineage)))

    assert(rest(Ready(malformed), actor.player)
      .left.toOption.get.isInstanceOf[UnsupportedRestState])

  test("Rest globally cleans every in-play denizen and relic"):
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
    val prepared = withRelics.updateCurrent(_.copy(
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
        if p.player == actor.player then
          p.copy(board = p.board.copy(faceDownSecrets = 2), advisers = Vector(adviser),
            relics = Vector(relic))
        else if p.player == otherBefore.player then
          p.copy(advisers = Vector(otherAdviser), relics = Vector(otherRelic))
        else p
      }))
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

  test("replaying a round of walker Rests reproduces the state and advances the round"):
    var state: OathState = Ready(act)
    var events = Vector.empty[OathEvent]
    val participants = act.game.current.players.map(_.player)
    val start = participants.indexOf(act.setup.firstPlayer)
    val order = participants.drop(start) ++ participants.take(start)
    order.foreach { player =>
      val rested = rest(state, player).toOption.get
      events ++= rested.events
      state = rested.state
      // A Wake with nothing to decide has already ended by itself.
      val turn = state.asInstanceOf[Ready].value.game.current.turn
      if player != order.last && turn.phase == Phase.Wake then
        val woke = rules.startWalker(state, PhaseTransitionRef.EndWake,
          turn.activePlayer).toOption.get
        events ++= woke.events
        state = woke.state
    }
    val Ready(after) = state: @unchecked
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.setup.firstPlayer)
    assertNotEquals(after.game.current.turn.phase, Phase.Rest)
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((next, event) => next.flatMap(rules.evolve(_, event))),
      Right(state))

  test("unrelated active powers and the end-die Arbiter legacy do not block"):
    val base = act
    val lineage = base.game.campaign.lineages.values.head
    val unrelatedHandlers = Set(
      "denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.silver-tongue", "denizen.insomnia",
      "denizen.vow-of-obedience")
    val unrelated = base.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId if catalog.denizens.find(_.id.value == id.value)
          .exists(d => !d.powers.exists(p => unrelatedHandlers(p.id.value))) => id
    }.get
    val supported = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
        lineage.id, lineage.copy(legacies = Vector(
          LegacyState(LegacyId("L23"), active = true))))), current =
      base.game.current.copy(commonCards = base.game.current.commonCards.copy(
        worldDeck = base.game.current.commonCards.worldDeck.filterNot(_ == unrelated)),
        players = base.game.current.players.map { player =>
        if player.player != base.game.current.turn.activePlayer then player
        else player.copy(advisers = Vector(DenizenState(
          unrelated, Orientation.FaceUp, Tokens.empty)))
      })))
    rest(Ready(supported), supported.game.current.turn.activePlayer)
      .fold(error => fail(error.toString), _ => ())

  test("each relevant Rest handler records fallback diagnostics without blocking"):
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    // Insomnia's REST runs as a phase power, so it records no diagnostic.
    val relevant = Set("denizen.vow-of-poverty", "denizen.naysayers")
    relevant.foreach { handler =>
      val definition = catalog.denizenWithPower(PowerId(handler)).get
      val adviser = DenizenState(DenizenId(definition.id.value),
        Orientation.FaceUp, Tokens.empty)
      val state = base.updateCurrent(_.copy(
        players = base.game.current.players.map(p => if p.player == actor.player then
          p.copy(advisers = Vector(adviser)) else p)))
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
    val handler = "denizen.naysayers"
    val definition = catalog.denizenWithPower(PowerId(handler)).get
    val siteId = actor.pawnSite.get
    val siteState = base.updateCurrent(_.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        siteId, base.game.current.map.sites(siteId).copy(denizens = Vector(
          DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
            Tokens.empty)))))))
    val siteAccepted = rest(Ready(siteState), actor.player)
      .toOption.get.events.head.asInstanceOf[IgnoredRulesRecorded]
    assert(siteAccepted.diagnostics.head.source.stableKey
      .startsWith(s"site-card:${siteId.value}:"))

  test("altered banner and Foundation types record stable fallback identities"):
    val base = act
    val actor = base.game.current.turn.activePlayer
    def diagnostic(ready: ReadyGame) = rest(Ready(ready), actor)
      .toOption.get.events.head
      .asInstanceOf[IgnoredRulesRecorded].diagnostics.head
    val banner = base.updateCurrent(_.copy(
      banners = base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(
          active = PeoplesFavorFace.GrandCouncil))))
    assertEquals(diagnostic(banner).source.stableKey, "banner:peoples-favor")

    val number = base.game.campaign.foundations.keys.head
    val foundation = base.updateCampaign(_.copy(foundations = base.game.campaign.foundations.updated(
        number, FoundationState(FoundationFace.Altered, Set.empty))))
    assertEquals(diagnostic(foundation).source.stableKey,
      s"foundation:${number.value}")

  test("last player of round eight finishes the game by War Exhaustion"):
    val base = act
    val participants = base.game.current.players.map(_.player)
    val start = participants.indexOf(base.setup.firstPlayer)
    val last = (participants.drop(start) ++ participants.take(start)).last
    val unsupported = base.updateCurrent(_.copy(
        tracks = base.game.current.tracks.copy(round = 8),
        turn = TurnState(last, Phase.Act, Set.empty)))

    val deterministic = new OathRules(catalog, warExhaustionRandomPort =
      new WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.last
      })
    val finished = rest(Ready(unsupported), last, deterministic).toOption.get
    assert(finished.events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    assert(finished.events.exists(_.isInstanceOf[OathEvent.WarExhaustionResolved]))
    val Ready(after) = finished.state: @unchecked
    assertEquals(after.game.current.result.map(_.winner), Some(last))
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

  test("Finish Rest belongs to the active player in the Rest phase"):
    val act = this.act
    val actor = act.game.current.turn.activePlayer
    val other = act.game.current.players.map(_.player).find(_ != actor).get
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, other).left.toOption,
      Some(OathViolation.WrongPlayer(actor, other)))
    assertEquals(rules.startWalker(Ready(act), PhaseTransitionRef.FinishRest,
      actor).left.toOption, Some(OathViolation.WrongPhase(Phase.Rest, Phase.Act)))
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.BeginRest, actor).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
    val finished = rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, actor).toOption.get
    assertEquals(ready(finished.state).game.current.turn.phase, Phase.Wake)

  test("the last player's Rest ends the round and wakes the first player"):
    val act = this.act
    val order =
      val participants = act.game.current.players.map(_.player)
      val start = participants.indexOf(act.setup.firstPlayer)
      participants.drop(start) ++ participants.take(start)
    val last = act.updateCurrent(_.copy(
      turn = TurnState(order.last, Phase.Act, Set.empty)))
    val rested = rest(Ready(last), order.last).toOption.get
    assert(rested.events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    val after = ready(rested.state).game.current
    assertEquals(after.tracks.round, act.game.current.tracks.round + 1)
    assertEquals(after.turn.activePlayer, order.head)
    // The first player's Wake has nothing to decide on this board, so it
    // ends in the same command.
    assertEquals(after.turn.phase, Phase.Act)
    assert(rested.events.contains(
      oathdigital.gameplay.walker.WalkerCompleted(PhaseTransitionRef.EndWake)))
