package oathdigital.gameplay

import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table,
  WarExhaustionChoices}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.UnsupportedRestState
import oathdigital.testkit.Unwrap.{accepted, as, present, rejected}

class RestSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val ancientCity = CatalogNames.site("Ancient City")

  /** p1's Act on the quiet table, p1 at Ancient City. Broken Peaks holds two
    * secrets, so p2, waking next, has wealth to take and the Wake waits. */
  private def restBoard: Table = Table.start
    .siteTokens("Broken Peaks", secrets = 2)

  /** `restBoard`, with Wrestlers as p1's adviser. */
  private def act: ReadyGame = restBoard.adviser(p1, "Wrestlers").ready

  private def rest(state: OathState, player: PlayerId,
      using: OathRules = rules) =
    using.startWalker(state, PhaseTransitionRef.BeginRest, player)

  private def inRest(ready: ReadyGame) =
    Table.from(ready).turn(p1, Phase.Rest).unchecked

  test("Rest returns controlled resources reveals secrets refreshes and wakes next"):
    val siteCard = CatalogNames.denizen("Errand Boy")
    val heldRelic = CatalogNames.relic("Dowsing Sticks")
    val prepared = restBoard.adviser(p1, "Wrestlers")
      .tokens("Wrestlers", favor = 1, secrets = 2)
      .denizen(siteCard, at = ancientCity)
      .tokens(siteCard, favor = 2, secrets = 1).warbandsAt(ancientCity, p1, 2)
      .relic(p1, heldRelic).tokens(heldRelic, secrets = 3)
      .secrets(p1, faceUp = 1, faceDown = 2).supply(p1, 1).ready

    val completed = accepted(rest(Ready(prepared), p1))
    assertEquals(completed.events.collect { case WalkerCompleted(p) => p },
      Vector(PhaseTransitionRef.BeginRest, PhaseTransitionRef.FinishRest))
    val after = Outcomes.ready(completed.state)
    val rested = Look(after).player(p1)

    assertEquals(rested.board.faceDownSecrets, 0)
    // 1 faceup, the 2 facedown revealed, and the 6 secrets on the cards p1
    // controls:
    // 2 on Wrestlers, 1 on Errand Boy, 3 on Dowsing Sticks.
    assertEquals(rested.board.faceUpSecrets, 1 + 2 + 6)
    assertEquals(rested.board.supply, SupplyTrack.full)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, p1)
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    // The favor on the denizens returns to the banks: 1 on Wrestlers and 2 on
    // Errand Boy.
    assertEquals(after.banks.favor.values.sum,
      prepared.banks.favor.values.sum + 3)

  test("Rest rejects a missing bounded warband supply"):
    val ready = act
    val lineage = Look(ready).player(p1).lineage
    val malformed = ready.copy(banks = ready.banks.copy(warbandSupply =
      ready.banks.warbandSupply - ForceKind.Exile(lineage)))

    assert(rejected(rest(Ready(malformed), p1))
      .isInstanceOf[UnsupportedRestState])

  test("Rest globally cleans every in-play denizen and relic"):
    // Ancient City is p1's site, Dunes a site p1 rules, Golden Valley one
    // p1 neither stands at nor rules. The denizens and the edifice span four
    // suits (Beast, Nomad, Order, Discord), so their returned favor reaches
    // more than one bank.
    val ruled = CatalogNames.site("Dunes")
    val outside = CatalogNames.site("Golden Valley")
    val adviser = CatalogNames.denizen("Errand Boy")         // Beast
    val pawnCard = CatalogNames.denizen("Rain Boots")        // Nomad
    val ruledCard = CatalogNames.denizen("Code of Honor")    // Order
    val outsideCard = CatalogNames.denizen("Messenger")      // Order
    val otherAdviser = CatalogNames.denizen("Wrestlers")     // Order
    val pawnEdifice = CatalogNames.edifice("Hall of Debate")
    val relic = CatalogNames.relic("Dowsing Sticks")
    val otherRelic = CatalogNames.relic("Cracked Horn")
    val siteRelic = CatalogNames.relic("Bandit Crown")
    val prepared = restBoard.adviser(p1, adviser)
      .tokens(adviser, favor = 1, secrets = 1).relic(p1, relic)
      .tokens(relic, favor = 7, secrets = 2)
      .secrets(p1, faceUp = 1, faceDown = 2).bandits(ancientCity, 1)
      .denizen(pawnCard, at = ancientCity)
      .tokens(pawnCard, favor = 2, secrets = 2)
      .edifice(pawnEdifice, EdificeSide.Ruined, at = ancientCity)
      .tokens(pawnEdifice, favor = 1, secrets = 1).warbandsAt(ruled, p1, 1)
      .denizen(ruledCard, at = ruled).tokens(ruledCard, favor = 3, secrets = 3)
      .bandits(outside, 1).denizen(outsideCard, at = outside)
      .tokens(outsideCard, favor = 4, secrets = 4)
      .relicAt(siteRelic, at = outside).tokens(siteRelic, secrets = 7)
      .adviser(p2, otherAdviser).tokens(otherAdviser, favor = 5, secrets = 5)
      .relic(p2, otherRelic).tokens(otherRelic, secrets = 6)
      .ready
    val otherBefore = Look(prepared).player(p2)
    val plan = accepted(RestCleanupPlan.derive(catalog, prepared, p1))
    // Every card's secrets: 1 + 2 + 2 + 1 + 3 + 4 + 7 + 5 + 6.
    assertEquals(plan.returnedSecrets, 31)
    // The denizens' and the edifice's favor: 1 + 2 + 1 + 3 + 4 + 5. A relic
    // keeps its favor.
    assertEquals(plan.returnedFavor.values.sum, 16)
    val finished = accepted(rest(Ready(prepared), p1))
    val after = Outcomes.ready(finished.state)
    val rested = Look(after).player(p1)
    assertEquals(rested.board.faceDownSecrets, 0)
    assertEquals(rested.board.faceUpSecrets, 1 + 2 + plan.returnedSecrets)
    assertEquals(rested.advisers.collect { case d: DenizenState => d.tokens },
      Vector(Tokens.empty))
    assertEquals(rested.relics.head.tokens, Tokens(7, 0))
    // Each of the three sites holds denizens, and none keeps a token.
    Vector(ancientCity, ruled, outside).foreach(site =>
      assertEquals(after.game.current.map.sites(site).denizens
        .map(_.tokens).distinct, Vector(Tokens.empty), site.value))
    assertEquals(after.game.current.map.sites(outside).relics.head.tokens,
      Tokens.empty)
    plan.returnedFavor.foreach { case (suit, amount) =>
      assertEquals(after.banks.favor(suit),
        prepared.banks.favor(suit) + amount)
    }
    assert(plan.returnedFavor.size >= 2)
    val otherAfter = Look(after).player(p2)
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
      val rested = accepted(rest(state, player))
      events ++= rested.events
      state = rested.state
      // A Wake with nothing to decide has already ended by itself.
      val turn = Outcomes.ready(state).game.current.turn
      if player != order.last && turn.phase == Phase.Wake then
        val woke = accepted(rules.startWalker(state, PhaseTransitionRef.EndWake,
          turn.activePlayer))
        events ++= woke.events
        state = woke.state
    }
    val after = Outcomes.ready(state)
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.setup.firstPlayer)
    assertNotEquals(after.game.current.turn.phase, Phase.Rest)
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((next, event) => next.flatMap(rules.evolve(_, event))),
      Right(state))

  test("unrelated active powers and the end-die Arbiter legacy do not block"):
    // p1's lineage holds the Arbiter legacy, active, and p1 holds Threatening
    // Roar, whose power is none of the Rest handlers.
    val arbiter = present(catalog.legacies.find(_.name == "Arbiter")
      .map(_.id), "the Arbiter legacy")
    val base = restBoard.adviser(p1, "Threatening Roar").ready
    val lineage = base.game.campaign.lineages(Look(base).player(p1).lineage)
    // No Table step reaches legacies.
    val supported = base.updateCampaign(_.copy(lineages =
      base.game.campaign.lineages.updated(lineage.id, lineage.copy(legacies =
        Vector(LegacyState(arbiter, active = true))))))
    rest(Ready(supported), p1).fold(error => fail(error.toString), _ => ())

  test("each relevant Rest handler records fallback diagnostics without " +
      "blocking, and replay accepts the record but not a tampered one"):
    // Insomnia's and Vow of Poverty's RESTs run as phase powers, so they
    // record no diagnostic.
    val relevant = Map("denizen.naysayers" -> "Naysayers")
    relevant.foreach { (handler, card) =>
      val state = restBoard.adviser(p1, card).ready
      val restedEvents = accepted(rest(Ready(state), p1)).events
      val recorded = as[IgnoredRulesRecorded](restedEvents.head)
      assertEquals(recorded.diagnostics.head.handlerId, handler)
      assert(recorded.diagnostics.head.source.stableKey
        .startsWith(s"adviser:${p1.value}:"))
      assertEquals(rules.evolve(Ready(state), recorded), Right(Ready(state)))
      val tampered = recorded.copy(diagnostics = recorded.diagnostics.map(
        _.copy(handlerId = "denizen.tampered")))
      assertEquals(rules.evolve(Ready(state), tampered), Left(
        OathViolation.InvalidEventOrder(
          "ignored-rule diagnostics do not match authoritative discovery")))
    }
    val siteState = Table.from(act).denizen("Naysayers", at = ancientCity)
      .unchecked
    val siteRecorded = as[IgnoredRulesRecorded](
      accepted(rest(Ready(siteState), p1)).events.head)
    assert(siteRecorded.diagnostics.head.source.stableKey
      .startsWith(s"site-card:${ancientCity.value}:"))

  test("altered banner and Foundation types record stable fallback identities"):
    val base = act
    def diagnostic(ready: ReadyGame) = as[IgnoredRulesRecorded](
      accepted(rest(Ready(ready), p1)).events.head).diagnostics.head
    val banner = Table.from(base).peoplesFavor(None, favor = 1,
      face = PeoplesFavorFace.GrandCouncil).unchecked
    assertEquals(diagnostic(banner).source.stableKey, "banner:peoples-favor")

    val number = base.game.campaign.foundations.keys.head
    // No Table step reaches foundations.
    val foundation = base.updateCampaign(_.copy(foundations =
      base.game.campaign.foundations.updated(number,
        FoundationState(FoundationFace.Altered, Set.empty))))
    assertEquals(diagnostic(foundation).source.stableKey,
      s"foundation:${number.value}")

  test("last player of round eight finishes the game by War Exhaustion, and " +
      "the projection still offers beginRest"):
    // The quiet table's turn order is p1, p2, p3, so p3 rests last.
    val last = p3
    // No Table step sets the round.
    val roundEight = Table.from(act).turn(last, Phase.Act).unchecked
      .updateCurrent(c => c.copy(tracks = c.tracks.copy(round = 8)))

    val deterministic = new OathRules(catalog,
      warExhaustionRandomPort = WarExhaustionChoices.lastCandidate)
    val finished = accepted(rest(Ready(roundEight), last, deterministic))
    assertEquals(finished.events.collect {
      case ended: OathEvent.RoundEnded => ended },
      Vector(OathEvent.RoundEnded(8, None)))
    val after = Outcomes.ready(finished.state)
    assertEquals(after.game.current.result.map(_.winner), Some(last))
    val result = present(finished.events.collectFirst {
      case event: OathEvent.WarExhaustionResolved => event
    }, "the War Exhaustion result")
    assertEquals(result.kind, VictoryKind.RandomSelection)
    assertEquals(result.winner, result.randomCandidates.last)
    assertEquals(rejected(rest(finished.state, last, deterministic)),
      OathViolation.GameEnded)
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "round-eight", oathdigital.application.LoadedGame(
        Ready(roundEight), 30L), last)
    assert(projection.legalControls.contains("beginRest"))

  test("Finish Rest belongs to the active player in the Rest phase, and " +
      "Begin Rest is refused inside Rest"):
    val act = this.act
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, p2).left.toOption,
      Some(OathViolation.WrongPlayer(p1, p2)))
    assertEquals(rules.startWalker(Ready(act), PhaseTransitionRef.FinishRest,
      p1).left.toOption, Some(OathViolation.WrongPhase(Phase.Rest, Phase.Act)))
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.BeginRest, p1).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
    val finished = accepted(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, p1))
    assertEquals(Outcomes.ready(finished.state).game.current.turn.phase,
      Phase.Wake)

  test("the last player's Rest ends the round and wakes the first player"):
    val act = this.act
    // The quiet table's turn order is p1, p2, p3.
    val last = Table.from(act).turn(p3, Phase.Act).unchecked
    val rested = accepted(rest(Ready(last), p3))
    val round = act.game.current.tracks.round
    assertEquals(rested.events.collect { case ended: OathEvent.RoundEnded =>
      ended }, Vector(OathEvent.RoundEnded(round, Some(round + 1))))
    val after = Outcomes.ready(rested.state).game.current
    assertEquals(after.tracks.round, act.game.current.tracks.round + 1)
    assertEquals(after.turn.activePlayer, p1)
    // The first player's Wake has nothing to decide on this board, so it
    // ends in the same command.
    assertEquals(after.turn.phase, Phase.Act)
    assert(rested.events.contains(
      oathdigital.gameplay.walker.WalkerCompleted(PhaseTransitionRef.EndWake)))
