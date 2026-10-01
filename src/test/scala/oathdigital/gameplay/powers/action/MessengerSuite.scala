package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class MessengerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val messenger = CatalogNames.denizen("Messenger")
  private val source = DecisionOptionRef.Denizen(messenger)
  private val home = Table.homeOf(p1)
  private val far = CatalogNames.site("Deep Woods")

  /** p1's Act holding Messenger with `favor` favor and `board` warbands on
    * the board. Unless `ruling` is false, p1 rules its home site with
    * `atHome` warbands and Deep Woods with one. */
  private def staged(board: Int = 3, atHome: Int = 2, ruling: Boolean = true,
      favor: Int = 1): ReadyGame =
    val table = Table.start.turn(p1, Phase.Act).adviser(p1, messenger)
      .favor(p1, favor).warbands(p1, board)
    (if ruling then table.warbandsAt(home, p1, atHome).warbandsAt(far, p1, 1)
    else table).ready

  private def count(ready: ReadyGame, site: SiteId): Int =
    Look(ready).forces(site) match
      case SiteForces.Occupied(_, n) => n
      case _ => 0
  private def arrangement(board: Int, atHome: Int, atFar: Int)
      : DecisionAnswer = DecisionAnswer.DistributeAnswer(Vector(
    DistributeAmount(DecisionOptionRef.Player(p1), board),
    DistributeAmount(DecisionOptionRef.Site(home), atHome),
    DistributeAmount(DecisionOptionRef.Site(far), atFar)))
  private def arranged(ready: ReadyGame, answered: DecisionAnswer)
      : Either[OathViolation, OathTransition] =
    answer(use(ready, Messenger, source).toOption.get, p1,
      Messenger.decisionId, answered)

  test("it places a favor and asks for one arrangement over the board and " +
      "every ruled site"):
    val t = use(staged(), Messenger, source).toOption.get
    assert(awaits(t, Messenger.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(messenger), Tokens(1, 0))

  test("the answer moves warbands from the board to the sites, keeping the " +
      "total"):
    val ready = staged()
    val t = use(ready, Messenger, source).toOption.get
    val done = answer(t, p1, Messenger.decisionId, arrangement(0, 4, 2))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).warbands(p1), 0)
    assertEquals(count(end, home), 4)
    assertEquals(count(end, far), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("warbands may go back to the board"):
    val end = after(arranged(staged(), arrangement(4, 1, 1)).toOption.get)
    assertEquals(Look(end).warbands(p1), 4)
    assertEquals(count(end, home), 1)
    assertEquals(count(end, far), 1)

  test("a site must keep a warband, and the total must be kept"):
    assert(arranged(staged(), arrangement(4, 2, 0)).isLeft)
    assert(arranged(staged(), arrangement(3, 2, 2)).isLeft)

  test("an answer that changes nothing is allowed"):
    val done = arranged(staged(), arrangement(3, 2, 1)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p1), 3)
    assertEquals(count(after(done), home), 2)

  test("its line follows the answer and covers nothing"):
    val done = arranged(staged(), arrangement(0, 4, 2)).toOption.get
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} redistributed their warbands.",
      covers = false)))

  test("with no ruled site nothing is asked, the favor stays paid, and the " +
      "line says so"):
    val done = use(staged(ruling = false), Messenger, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(messenger), Tokens(1, 0))
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("with one warband on each ruled site and none on the board, nothing " +
      "can move"):
    val done = use(staged(board = 0, atHome = 1), Messenger, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0)
    assert(!usableNow(broke).exists(_.power.id == Messenger.id))
    assert(use(broke, Messenger, source).isLeft)
