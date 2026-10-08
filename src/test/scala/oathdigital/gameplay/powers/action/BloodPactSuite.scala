package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class BloodPactSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val pact = CatalogNames.denizen("Blood Pact")
  private val source = DecisionOptionRef.Denizen(pact)

  /** p1's Act, holding Blood Pact as an adviser, with `warbands` on the
    * board and `secrets` faceup secrets. */
  private def staged(warbands: Int = 5, secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, pact)
      .boardWarbands(p1, warbands).secrets(p1, secrets).ready

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def pairs(count: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(count)

  test("it places a secret and asks how many pairs, up to half the board"):
    // 5 warbands make 2 pairs at most, so 3 is refused.
    val t = accepted(use(staged(warbands = 5), BloodPact, source))
    assert(awaits(t, BloodPact.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(pact), Tokens(0, 1))
    assertEquals(answer(t, p1, BloodPact.decisionId, pairs(3)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${BloodPact.decisionId} amount 3 is outside 0..2")))

  test("each pair sacrificed from the board gains a secret"):
    // 5 warbands less 2 pairs leave 1.
    val ready = staged(warbands = 5)
    val t = accepted(use(ready, BloodPact, source))
    val done = accepted(answer(t, p1, BloodPact.decisionId, pairs(2)))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).boardWarbands(p1), 1)
    assertEquals(secretsOf(end), secretsOf(ready) - 1 + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the warbands sacrificed and the secrets gained, " +
      "covering the gain line"):
    val t = accepted(use(staged(), BloodPact, source))
    val done = accepted(answer(t, p1, BloodPact.decisionId, pairs(2)))
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} sacrificed 4 warbands and gained 2 secrets.",
      covers = true)))

  test("choosing no pair sacrifices nothing and says so"):
    val t = accepted(use(staged(warbands = 5), BloodPact, source))
    val done = accepted(answer(t, p1, BloodPact.decisionId, pairs(0)))
    assertEquals(Look(after(done)).boardWarbands(p1), 5)
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("with fewer than two warbands it asks nothing, and the cost stays " +
      "paid"):
    val done = accepted(use(staged(warbands = 1), BloodPact, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).boardWarbands(p1), 1)
    assertEquals(Look(after(done)).tokensOn(pact), Tokens(0, 1))
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("it is unusable without a faceup secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == BloodPact.id))
    assertEquals(use(broke, BloodPact, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
