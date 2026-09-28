package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._

class TutorSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val tutor = DenizenId("69")
  private val source = DecisionOptionRef.Denizen(tutor)

  /** Tutor is adviser-only, so the actor holds it faceup. */
  private def staged(favor: Int = 2, faceUp: Int = 1): ReadyGame =
    act(withBoard(asAdviser(base, tutor))(
      _.copy(favor = favor, faceUpSecrets = faceUp)))
  private def tokens(ready: ReadyGame): Tokens =
    player(ready).advisers.collectFirst {
      case card: DenizenState if card.id == tutor => card.tokens }.get

  test("it places a favor and a secret on its card and gains a secret"):
    val ready0 = staged()
    val done = use(rules(), ready0, Tutor.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokens(end), Tokens(1, 1))
    assertEquals(player(end).board.favor, 1)
    // One secret placed on the card, one gained from the shared bank.
    assertEquals(secrets(end), secrets(ready0))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("it is unusable without a favor or without a faceup secret"):
    Vector(staged(favor = 0), staged(faceUp = 0)).foreach { broke =>
      assert(!usableIds(broke).contains(Tutor.id))
      assert(use(rules(), broke, Tutor.id, source).isLeft)
    }

  test("it writes its gain as its own line, covering the generic one"):
    val done = use(rules(), staged(), Tutor.id, source).toOption.get
    assertEquals(NoteText.said(Tutor, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
