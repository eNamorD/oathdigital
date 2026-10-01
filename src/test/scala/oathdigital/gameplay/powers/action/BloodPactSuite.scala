package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BloodPactSuite extends munit.FunSuite:
  import TargetsFixture._

  private val pact = CatalogNames.denizen("Blood Pact")
  private val source = DecisionOptionRef.Denizen(pact)

  /** p1's Act, holding Blood Pact as an adviser, with `warbands` on the
    * board and `secrets` faceup secrets. */
  private def staged(warbands: Int = 5, secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, pact)
      .warbands(p1, warbands).secrets(p1, secrets).ready

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def pairs(count: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(count)

  test("it places a secret and asks how many pairs, up to half the board"):
    val t = use(staged(), BloodPact, source).toOption.get
    assert(awaits(t, BloodPact.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(pact), Tokens(0, 1))
    assert(answer(t, p1, BloodPact.decisionId, pairs(3)).isLeft)

  test("each pair sacrificed from the board gains a secret"):
    val ready = staged()
    val t = use(ready, BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(2)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).warbands(p1), 1)
    assertEquals(secretsOf(end), secretsOf(ready) - 1 + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the warbands sacrificed and the secrets gained, " +
      "covering the gain line"):
    val t = use(staged(), BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(2)).toOption.get
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} sacrificed 4 warbands and gained 2 secrets.",
      covers = true)))

  test("choosing no pair sacrifices nothing and says so"):
    val t = use(staged(), BloodPact, source).toOption.get
    val done = answer(t, p1, BloodPact.decisionId, pairs(0)).toOption.get
    assertEquals(Look(after(done)).warbands(p1), 5)
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("with fewer than two warbands it asks nothing, and the cost stays " +
      "paid"):
    val done = use(staged(warbands = 1), BloodPact, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p1), 1)
    assertEquals(Look(after(done)).tokensOn(pact), Tokens(0, 1))
    assertEquals(NoteText.said(BloodPact, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} sacrificed no warbands.", covers = true)))

  test("it is unusable without a faceup secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == BloodPact.id))
    assert(use(broke, BloodPact, source).isLeft)
