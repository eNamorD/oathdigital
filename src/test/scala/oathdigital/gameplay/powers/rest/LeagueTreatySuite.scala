package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Outcomes}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class LeagueTreatySuite extends munit.FunSuite:
  import LeagueTreatyFixture._

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val walkerParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def banks(state: OathState) = Outcomes.ready(state).banks.favor
  /** The worked example: 2 favor each on an Arcane (Disciples), a Discord
    * (Bandit Chief) and a Hearth (Homesteaders) card in the Cradle. */
  private val example = Vector(("Disciples", "Ancient City", 2),
    ("Bandit Chief", "Broken Peaks", 2), ("Homesteaders", "Ancient City", 2))
  /** The worked example's favor, by suit. */
  private val exampleFavorBySuit = Vector(Suit.Arcane -> 2, Suit.Discord -> 2,
    Suit.Hearth -> 2)
  /** Of the worked example's 2 Arcane, 2 Discord and 2 Hearth favor, Arcane
    * keeps 0, Discord 1 and Hearth 2, and Nomad takes the other 3. */
  private val workedDistribution = Vector(Suit.Arcane -> 0, Suit.Discord -> 1,
    Suit.Hearth -> 2, Suit.Nomad -> 3)

  /** Nothing is parked, the game is in Wake, and (since a completed Rest
    * always passes the turn) the active player is not `resting`.
    */
  private def assertWake(state: OathState, resting: PlayerId): Unit =
    val ready = Outcomes.ready(state)
    val next = ready.game.current.turn.activePlayer
    assertNotEquals(next, resting)
    walkerParked.assertResumed(state, Phase.Wake, next)

  test("a bandit-ruled treaty site, or a region without card favor, asks " +
      "nothing"):
    Vector(leagueTreatyAct(None, example*).ready,
      leagueTreatyAct(Some(p2)).ready)
      .foreach { ready =>
      val rested = accepted(
        rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
      assertWake(rested.state, p1)
    }

  test("the off-turn ruler alone answers the destination, and declining " +
      "leaves cleanup to printed banks"):
    val owner = p2 // off p1's turn
    val ready = leagueTreatyAct(Some(owner), example*).ready
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      p1, treatySite, treatyCard)
    val parked = accepted(
      rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
    walkerParked.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      destination, owner)
    val decline = DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline"))
    assert(rules.resolveWalker(parked.state, p1, destination, decline).isLeft)
    val declined = accepted(
      rules.resolveWalker(parked.state, owner, destination, decline))
    assertWake(declined.state, p1)
    exampleFavorBySuit.foreach { case (suit, amount) =>
      assertEquals(banks(declined.state)(suit), ready.banks.favor(suit) + amount)
    }

  test("a destination that is the only source suit moves nothing and asks " +
      "no distribution"):
    val owner = p2 // off p1's turn
    // Two Nomad cards: Rain Boots with 2 favor, Convoys with 1.
    val ready = leagueTreatyAct(Some(owner), ("Rain Boots", "Ancient City", 2),
      ("Convoys", "Broken Peaks", 1)).ready
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      p1, treatySite, treatyCard)
    val parked = accepted(
      rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
    val chosen = accepted(
      rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))))
    assertWake(chosen.state, p1)
    assertEquals(banks(chosen.state)(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 3)

  test("the worked example moves 2 Arcane and 1 Discord favor to Nomad, " +
      "and a replayed park resumes to the same decision"):
    val owner = p2 // off p1's turn
    val ready = leagueTreatyAct(Some(owner), example*).ready
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      p1, treatySite, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      p1, treatySite, treatyCard)
    val parked = accepted(
      rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
    val chosen = accepted(
      rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))))
    walkerParked.assertParked(chosen.state, PhaseTransitionRef.FinishRest,
      distribution, owner)

    val replayed = ActPowerHarness.replayed(rules, ready,
      parked.events ++ chosen.events)
    assertEquals(Ready(replayed), chosen.state)

    val answer = DecisionAnswer.DistributeAnswer(workedDistribution.map {
      case (suit, n) => DistributeAmount(bank(suit), n) })
    val done = accepted(
      rules.resolveWalker(Ready(replayed), owner, distribution, answer))
    assertWake(done.state, p1)
    workedDistribution.foreach { case (suit, delta) =>
      assertEquals(banks(done.state)(suit), ready.banks.favor(suit) + delta,
        suit.toString)
    }

  test("favor on an edifice in the region moves with the rest, on either face"):
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val owner = p2 // off p1's turn
      val edifice = CatalogNames.edifice("Hall of Debate")
      val edificeSuit = Suit.Discord // Hall of Debate's printed suit
      val ready = leagueTreatyAct(Some(owner), ("Disciples", "Ancient City", 1))
        .edifice(edifice, side, at = treatySite).tokens(edifice, favor = 2)
        .ready
      // A suit that is neither Arcane nor Discord.
      val destinationBank = Suit.Order
      val destination = LeagueTreatyContribution.destinationDecisionId(ready,
        p1, treatySite, treatyCard)
      val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
        p1, treatySite, treatyCard)
      val parked = accepted(
        rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
      val chosen = accepted(
        rules.resolveWalker(parked.state, owner, destination,
        DecisionAnswer.ChooseOneAnswer(bank(destinationBank))))
      val sources = (Set(Suit.Arcane, edificeSuit) - destinationBank).toVector
      val answer = DecisionAnswer.DistributeAnswer(
        (sources.map(_ -> 0) :+ (destinationBank -> 3)).map {
          case (suit, n) => DistributeAmount(bank(suit), n) })
      val done = accepted(
        rules.resolveWalker(chosen.state, owner, distribution, answer))
      // 1 Arcane favor and the edifice's 2 move to the destination.
      assertEquals(banks(done.state)(destinationBank),
        ready.banks.favor(destinationBank) + 3, side.toString)
    }

  // ---- Lines ----

  private val power = LeagueTreatyContribution.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the treaty's line says how much favor the ruler sent to one bank"):
    val owner = p2 // off p1's turn
    val ready = leagueTreatyAct(Some(owner), example*).ready
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      p1, treatySite, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      p1, treatySite, treatyCard)
    val parked = accepted(
      rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
    val chosen = accepted(
      rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))))
    val done = accepted(rules.resolveWalker(chosen.state, owner, distribution,
      DecisionAnswer.DistributeAnswer(workedDistribution.map {
        case (suit, n) => DistributeAmount(bank(suit), n) })))
    assertEquals(said(parked.events ++ chosen.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("sent",
      s"${owner.value} sent 3 favor to the Nomad bank.", covers = false)))

  test("a ruler who declines sends nothing, and writes nothing"):
    val owner = p2 // off p1's turn
    val ready = leagueTreatyAct(Some(owner), example*).ready
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      p1, treatySite, treatyCard)
    val parked = accepted(
      rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, p1))
    val declined = accepted(
      rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline"))))
    assertEquals(said(parked.events ++ declined.events), Vector.empty)
