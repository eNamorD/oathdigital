package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, rejected}

class RevelationSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Revelation.type]
  private val revelation = power.cardId
  private val played = hook(revelation)

  /** Revelation as p1's adviser, with p1, p2 and p3 holding the favor given
    * and the quiet table's one faceup secret each. */
  private def holdingFavor(p1Favor: Int, p2Favor: Int, p3Favor: Int): Table =
    Table.start.adviser(p1, revelation).favor(p1, p1Favor)
      .favor(p2, p2Favor).favor(p3, p3Favor)

  private def burn(amount: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(amount)

  private def burned(player: PlayerId, n: Int): NoteText.Said =
    NoteText.Said("burned", s"${player.value} burned $n favor and gained " +
      s"$n ${if n == 1 then "secret" else "secrets"}.", covers = true)

  test("each player with favor, in turn order from the first player, burns " +
      "what they choose and gains as many secrets"):
    // p2 is the first player, so the order is p2, p3, then p1. No Table step
    // sets the first player, which only this power's turn order reads.
    val ready = holdingFavor(2, 1, 3).update(game => game.copy(
      setup = game.setup.copy(firstPlayer = p2))).ready
    val walked = walkAnswering(ready, power, played, burn(1), burn(2),
      burn(0))
    assertEquals(walked.asked.map(_.owner), Vector(p2, p3, p1))
    assertEquals(amountMaximaAsked(walked), Vector(1, 3, 2))
    val end = Look(walked.end)
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(2, 0, 1))
    assertEquals(Vector(p1, p2, p3).map(end.faceUpSecrets), Vector(1, 2, 3))
    assertEquals(said(power, walked.events), Vector(burned(p2, 1),
      burned(p3, 2), NoteText.Said("declined",
        s"${p1.value} did not burn any favor.", covers = false)))
    // Every event survives the wire, as a stored game's journal does.
    assertJournalRebuilds(ready, walked.events, walked.end)
    assertWireRoundTrips(walked.events)

  test("a player with no favor is not asked"):
    val walked = walkAnswering(holdingFavor(1, 0, 1).ready, power, played,
      burn(1), burn(1))
    assertEquals(walked.asked.map(_.owner), Vector(p1, p3))
    assertEquals(said(power, walked.events),
      Vector(burned(p1, 1), burned(p3, 1)))

  test("with nobody holding favor nothing is asked, and the line says so"):
    val done = Outcomes.finished(play(holdingFavor(0, 0, 0).ready, power,
      revelation))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "Nobody had favor to burn.", covers = false)))

  test("played from a Search, each question waits for its own player, and " +
      "no other player may answer it"):
    // Played from a Search, not a bare walk: the rules' resolveWalker, which
    // checks who answers a parked question, is the subject.
    val ready = SearchFixture.staged(Vector(revelation), supply = 5)
    val placed = SearchFixture.play(ready, Vector.empty, revelation,
      PlayedAs.Faceup)
    def answer(from: OathTransition, by: PlayerId, amount: Int) =
      SearchFixture.rules.resolveWalker(from.state, by, Revelation.decisionId,
        burn(amount))
    val p1Burnt = accepted(answer(placed, p1, 1))
    assertEquals(rejected(answer(p1Burnt, p1, 0)),
      OathViolation.WrongPlayer(p2, p1))
    val p2Declined = accepted(answer(p1Burnt, p2, 0))
    val done = accepted(answer(p2Declined, p3, 1))
    val end = Look(SearchFixture.after(done))
    // Each started with 1 favor and 1 faceup secret.
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(0, 1, 0))
    assertEquals(Vector(p1, p2, p3).map(end.faceUpSecrets), Vector(2, 1, 2))
