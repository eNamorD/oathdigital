package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class TutorSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val tutor = CatalogNames.denizen("Tutor")
  private val source = DecisionOptionRef.Denizen(tutor)

  /** p1's Act holding Tutor faceup (it is adviser-only), with `favor` favor
    * and `faceUp` faceup secrets. */
  private def staged(favor: Int = 2, faceUp: Int = 1): ReadyGame =
    Table.start.turn(actor, Phase.Act).adviser(actor, tutor)
      .favor(actor, favor).secrets(actor, faceUp).ready

  test("it places a favor and a secret on its card and gains a secret"):
    // 2 favor less the 1 placed leave 1.
    val ready0 = staged(favor = 2, faceUp = 1)
    val done = accepted(use(rules(), ready0, Tutor.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).tokensOn(tutor), Tokens(1, 1))
    assertEquals(Look(end).player(actor).board.favor, 1)
    // One secret placed on the card, one gained from the shared bank.
    assertEquals(allSecrets(end), allSecrets(ready0))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("it is unusable without a favor or without a faceup secret"):
    Vector(staged(favor = 0), staged(faceUp = 0)).foreach { broke =>
      assert(!usableIds(broke).contains(Tutor.id))
      assert(use(rules(), broke, Tutor.id, source).isLeft)
    }

  test("it writes its gain as its own line, covering the generic one"):
    val done = accepted(use(rules(), staged(), Tutor.id, source))
    assertEquals(NoteText.said(Tutor, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
