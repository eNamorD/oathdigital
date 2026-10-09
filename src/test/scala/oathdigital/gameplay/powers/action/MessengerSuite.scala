package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class MessengerSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  import PhasePowerHarness._

  private val messenger = CatalogNames.denizen("Messenger")
  private val source = DecisionOptionRef.Denizen(messenger)
  private val home = CatalogNames.site("Ancient City")
  private val far = CatalogNames.site("Deep Woods")

  /** p1's Act at Ancient City holding Messenger with `favor` favor and
    * `board` warbands on the board. Unless `ruling` is false, p1 rules
    * Ancient City with `atHome` warbands and Deep Woods with one. */
  private def staged(board: Int, atHome: Int, ruling: Boolean = true,
      favor: Int = 1): ReadyGame =
    val table = Table.start.turn(p1, Phase.Act).pawn(p1, home)
      .adviser(p1, messenger).favor(p1, favor).boardWarbands(p1, board)
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
    answer(accepted(use(ready, Messenger, source)), p1,
      Messenger.decisionId, answered)

  test("it places a favor and asks for one arrangement over the board and " +
      "every ruled site"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    val t = accepted(use(ready, Messenger, source))
    assert(awaits(t, Messenger.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(messenger), Tokens(1, 0))

  test("the answer moves warbands from the board to the sites, keeping the " +
      "total"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    val t = accepted(use(ready, Messenger, source))
    val answered = arrangement(0, 4, 2)
    val done = accepted(answer(t, p1, Messenger.decisionId, answered))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).boardWarbands(p1), 0)
    assertEquals(count(end, home), 4)
    assertEquals(count(end, far), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("warbands may go back to the board"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    val end = after(accepted(arranged(ready, arrangement(4, 1, 1))))
    assertEquals(Look(end).boardWarbands(p1), 4)
    assertEquals(count(end, home), 1)
    assertEquals(count(end, far), 1)

  test("a site must keep a warband, and the total must be kept"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    assertEquals(arranged(ready, arrangement(4, 2, 0)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${Messenger.decisionId} distributes to " +
          s"site/${far.value} outside 1..5")))
    assertEquals(arranged(ready, arrangement(3, 2, 2)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${Messenger.decisionId} distributes an amount other " +
          "than its total of 6")))

  test("an answer that changes nothing is allowed"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    val done = accepted(arranged(ready, arrangement(3, 2, 1)))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).boardWarbands(p1), 3)
    assertEquals(count(after(done), home), 2)

  test("its line follows the answer and covers nothing"):
    // 3 warbands on the board, 2 at Ancient City and 1 at Deep Woods: 6.
    val ready = staged(board = 3, atHome = 2)
    val done = accepted(arranged(ready, arrangement(0, 4, 2)))
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} redistributed their warbands.",
      covers = false)))

  test("with no ruled site nothing is asked, the favor stays paid, and the " +
      "line says so"):
    val done = accepted(use(staged(board = 3, atHome = 2, ruling = false),
      Messenger, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(messenger), Tokens(1, 0))
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("with one warband on each ruled site and none on the board, nothing " +
      "can move"):
    val done = accepted(use(staged(board = 0, atHome = 1), Messenger, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Messenger, done.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))

  test("it is unusable without a favor"):
    val broke = staged(board = 3, atHome = 2, favor = 0)
    assert(!usableNow(broke).exists(_.power.id == Messenger.id))
    assertEquals(use(broke, Messenger, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))

  test("a holder of Vow of Wandering may only bring warbands back to the " +
      "board"):
    // 3 on the board, 2 at Ancient City and 1 at Deep Woods: 6. No site
    // may grow.
    val ready = Table.from(staged(board = 3, atHome = 2)).adviser(p1, vow)
      .ready
    assertEquals(arranged(ready, arrangement(1, 4, 1)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${Messenger.decisionId} distributes to " +
          s"site/${home.value} outside 1..2")))
    val end = after(accepted(arranged(ready, arrangement(4, 1, 1))))
    assertEquals(Look(end).boardWarbands(p1), 4)
    assertEquals(count(end, home), 1)

  test("a holder whose sites keep one warband each has nothing to move"):
    val ready = Table.from(staged(board = 3, atHome = 1)).adviser(p1, vow)
      .ready
    val t = accepted(use(ready, Messenger, source))
    assert(!awaits(t, Messenger.decisionId))
    assertEquals(NoteText.said(Messenger, t.events), Vector(NoteText.Said(
      "used.none", "No warband could be moved.", covers = false)))
