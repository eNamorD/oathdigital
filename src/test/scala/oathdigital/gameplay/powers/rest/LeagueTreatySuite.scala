package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class LeagueTreatySuite extends munit.FunSuite:
  import LeagueTreatyFixture._

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val walkerParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def rester(ready: ReadyGame) = ready.game.current.turn.activePlayer
  private def offTurn(ready: ReadyGame) =
    ready.game.current.players.map(_.player).find(_ != rester(ready)).get
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def banks(state: OathState) = state.asInstanceOf[Ready].value.banks.favor
  private val example = Vector(Suit.Arcane -> 2, Suit.Discord -> 2,
    Suit.Hearth -> 2)

  /** Nothing is parked, the game is in Wake, and (since a completed Rest
    * always passes the turn) the active player is not `resting`.
    */
  private def assertWake(state: OathState, resting: PlayerId): Unit =
    val Ready(ready) = state: @unchecked
    val next = ready.game.current.turn.activePlayer
    assertNotEquals(next, resting)
    walkerParked.assertResumed(state, Phase.Wake, next)

  test("an unruled treaty site or a region without card favor asks nothing"):
    Vector(arranged(None, example), arranged(Some(offTurn(act)), Vector.empty))
      .foreach { case (ready, _) =>
      val rested = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
        rester(ready)).toOption.get
      assertWake(rested.state, rester(ready))
    }

  test("the off-turn ruler alone answers the destination, and declining " +
      "leaves cleanup to printed banks"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    walkerParked.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      destination, owner)
    val decline = DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline"))
    assert(rules.resolveWalker(parked.state, rester(ready), destination,
      decline).isLeft)
    val declined = rules.resolveWalker(parked.state, owner, destination,
      decline).toOption.get
    assertWake(declined.state, rester(ready))
    example.foreach { case (suit, amount) =>
      assertEquals(banks(declined.state)(suit), ready.banks.favor(suit) + amount)
    }

  test("a destination that is the only source suit moves nothing and asks " +
      "no distribution"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner),
      Vector(Suit.Nomad -> 2, Suit.Nomad -> 1))
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    assertWake(chosen.state, rester(ready))
    assertEquals(banks(chosen.state)(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 3)

  test("the worked example moves 2 Arcane and 1 Discord favor to Nomad, " +
      "and a replayed park resumes to the same decision"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    walkerParked.assertParked(chosen.state, PhaseTransitionRef.FinishRest,
      distribution, owner)

    val replayed = (parked.events ++ chosen.events)
      .foldLeft[Either[OathViolation, OathState]](Right(Ready(ready)))(
        (state, event) => state.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(chosen.state))

    val answer = DecisionAnswer.DistributeAnswer(Vector(
      Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3)
      .map { case (suit, n) => DistributeAmount(bank(suit), n) })
    val done = rules.resolveWalker(replayed.toOption.get, owner, distribution,
      answer).toOption.get
    assertWake(done.state, rester(ready))
    Vector(Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2,
      Suit.Nomad -> 3).foreach { case (suit, delta) =>
      assertEquals(banks(done.state)(suit), ready.banks.favor(suit) + delta,
        suit.toString)
    }

  test("favor on an edifice in the region moves with the rest, on either face"):
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val owner = offTurn(act)
      val (arrangedReady, site) = arranged(Some(owner), Vector(Suit.Arcane -> 1))
      val edifice = arrangedReady.game.current.commonCards.edificeDeck.head
      val edificeSuit = catalog.suitOf(edifice).get
      val ready = arrangedReady.updateCurrent(c => c.copy(
        commonCards = c.commonCards.copy(edificeDeck =
          c.commonCards.edificeDeck.tail),
        map = c.map.copy(sites = c.map.sites.updated(site,
          c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
            EdificeState(edifice, side, Tokens(2, 0)))))))
      val destinationBank = Suit.all.find(s =>
        s != Suit.Arcane && s != edificeSuit).get
      val destination = LeagueTreatyContribution.destinationDecisionId(ready,
        rester(ready), site, treatyCard)
      val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
        rester(ready), site, treatyCard)
      val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
        rester(ready)).toOption.get
      val chosen = rules.resolveWalker(parked.state, owner, destination,
        DecisionAnswer.ChooseOneAnswer(bank(destinationBank))).toOption.get
      val sources = (Set(Suit.Arcane, edificeSuit) - destinationBank).toVector
      val answer = DecisionAnswer.DistributeAnswer(
        (sources.map(_ -> 0) :+ (destinationBank -> 3)).map {
          case (suit, n) => DistributeAmount(bank(suit), n) })
      val done = rules.resolveWalker(chosen.state, owner, distribution, answer)
        .toOption.get
      assertEquals(banks(done.state)(destinationBank),
        ready.banks.favor(destinationBank) + 3, side.toString)
    }

  // ---- Lines ----

  private val power = LeagueTreatyContribution.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the treaty's line says how much favor the ruler sent to one bank"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    val done = rules.resolveWalker(chosen.state, owner, distribution,
      DecisionAnswer.DistributeAnswer(Vector(Suit.Arcane -> 0,
        Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3).map {
          case (suit, n) => DistributeAmount(bank(suit), n) })).toOption.get
    assertEquals(said(parked.events ++ chosen.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("sent",
      s"${owner.value} sent 3 favor to the Nomad bank.", covers = false)))

  test("a ruler who declines sends nothing, and writes nothing"):
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val declined = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline")))
      .toOption.get
    assertEquals(said(parked.events ++ declined.events), Vector.empty)
