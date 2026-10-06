package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class WitchsBargainSuite extends munit.FunSuite:
  import PhasePowerHarness._
  import WitchsBargain.{exchangeDecisionId, giveFavor, giveSecret,
    playerDecisionId, stop, switch}

  private val bargain = CatalogNames.denizen("Witch's Bargain")
  private val source = DecisionOptionRef.Denizen(bargain)
  private val home = CatalogNames.site("Ancient City")
  private val chooseP2 = playerDecisionId -> DecisionOptionRef.Player(p2)

  /** p1's Act at Ancient City beside Witch's Bargain, with 3 faceup secrets
    * and 4 favor. p2 stands there too, with 3 favor, 1 faceup and 1
    * facedown secret. p3 stays at Buried Giant. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(bargain, at = home)
    .secrets(p1, faceUp = 3).favor(p1, 4)
    .pawn(p2, at = home).favor(p2, 3).secrets(p2, faceUp = 1, faceDown = 1)

  /** Each walk in turn: Witch's Bargain used, then each answer p1 gave. */
  private def walksAnswering(ready: ReadyGame,
      answers: (String, DecisionOptionRef)*): Vector[OathTransition] =
    answers.foldLeft(Vector(accepted(use(ready, WitchsBargain, source)))) {
      case (done, (decision, ref)) =>
        done :+ accepted(answer(done.last, p1, decision, pick(ref)))
    }

  test("it places a secret and asks for another player at the actor's " +
      "site, not the actor or players elsewhere"):
    val t = accepted(use(staged.ready, WitchsBargain, source))
    assert(awaits(t, playerDecisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))
    assertEquals(Look(after(t)).tokensOn(bargain), Tokens(0, 1))

  test("giving a secret takes up to 2 favor, and it asks again"):
    val traded = walksAnswering(staged.favor(p2, 1).ready, chooseP2,
      exchangeDecisionId -> giveSecret).last
    assert(awaits(traded, exchangeDecisionId),
      parked.parkedDecision(traded.state).toString)
    val end = Look(after(traded))
    // 3 secrets less 1 placed and 1 traded; 4 favor and the 1 p2 held.
    assertEquals((end.faceUpSecrets(p1), end.favor(p1)), (1, 5))
    assertEquals((end.faceUpSecrets(p2), end.faceDownSecrets(p2),
      end.favor(p2)), (2, 1, 0))
    assertEquals(NoteText.said(WitchsBargain, traded.events), Vector(
      NoteText.Said("secret",
        s"Gave 1 secret to ${p2.value} and took 1 favor.", covers = false)))

  test("giving 2 favor takes a secret, faceup first"):
    val traded = walksAnswering(staged.ready, chooseP2,
      exchangeDecisionId -> giveFavor).last
    val end = Look(after(traded))
    assertEquals((end.favor(p1), end.faceUpSecrets(p1)), (2, 3))
    assertEquals((end.favor(p2), end.faceUpSecrets(p2),
      end.faceDownSecrets(p2)), (5, 0, 1))
    assertEquals(NoteText.said(WitchsBargain, traded.events), Vector(
      NoteText.Said("favor",
        s"Gave 2 favor to ${p2.value} and took 1 secret.", covers = false)))

  test("the loops run until Stop, and Pick a different player asks for the " +
      "player again"):
    val ready = staged.pawn(p3, at = home).ready
    val walks = walksAnswering(ready, chooseP2,
      exchangeDecisionId -> giveSecret,
      exchangeDecisionId -> switch,
      playerDecisionId -> DecisionOptionRef.Player(p3),
      exchangeDecisionId -> giveFavor,
      exchangeDecisionId -> stop)
    assertEquals(offered(walks(1), p1), Some(Vector(
      "button" -> giveSecret.key, "button" -> giveFavor.key,
      "button" -> switch.key, "button" -> stop.key)))
    assert(awaits(walks(3), playerDecisionId),
      parked.parkedDecision(walks(3).state).toString)
    assert(backToActing(walks.last))
    val events = walks.flatMap(_.events)
    assertEquals(NoteText.said(WitchsBargain, events), Vector(
      NoteText.Said("secret",
        s"Gave 1 secret to ${p2.value} and took 2 favor.", covers = false),
      NoteText.Said("favor",
        s"Gave 2 favor to ${p3.value} and took 1 secret.", covers = false)))
    assertEquals(replayed(ready, events), Right(walks.last.state))
    assertWireRoundTrips(events)

  test("an exchange the actor cannot pay in full is not offered, nor Pick a " +
      "different player without a second player"):
    // The cost takes p1's only secret, and 1 favor cannot pay 2.
    val chosen = walksAnswering(staged.secrets(p1, faceUp = 1).favor(p1, 1)
      .ready, chooseP2).last
    assertEquals(offered(chosen, p1), Some(Vector("button" -> stop.key)))

  test("with no exchange made, or nobody at the site, the line says so"):
    val stopped = walksAnswering(staged.ready, chooseP2,
      exchangeDecisionId -> stop)
    val alone = accepted(use(Table.start.turn(p1, Phase.Act)
      .pawn(p1, at = home).denizen(bargain, at = home).ready, WitchsBargain,
      source))
    assert(backToActing(stopped.last))
    assert(backToActing(alone))
    Vector(stopped.flatMap(_.events), alone.events).foreach(events =>
      assertEquals(NoteText.said(WitchsBargain, events), Vector(
        NoteText.Said(NoteKey.Used, "No exchange was made.", covers = false))))
