package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.Table.{p2, p3}

class WhistleSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val whistle = RelicId("R08")
  private def staged(secrets: Int = 2) = inPhase(
    withSecrets(withRelic(homelands(base), whistle), secrets), Phase.Act)
  private val target = DecisionOptionRef.Player(p2)

  test("it pulls the chosen pawn to the actor's site and hands over the secret"):
    val start = staged()
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assertEquals(relicOf(readyOf(parked.state), whistle).get.tokens, Tokens(0, 1))

    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(pawnOf(after, p2), ancientCity)
    assertEquals(pawnOf(after, p3), buriedGiant)
    assertEquals(pawnOf(after), ancientCity)
    assertEquals(relicOf(after, whistle).get.tokens, Tokens.empty)
    assertEquals(player(after, p2).board.faceUpSecrets,
      player(start, p2).board.faceUpSecrets + 1)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))

  test("only players at other sites are offered, even when there is one"):
    val start = withPawn(staged(), p3, ancientCity)
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assert(choose(parked.state, Whistle.decisionId,
      DecisionOptionRef.Player(p3)).isLeft)
    assert(choose(parked.state, Whistle.decisionId, target).isRight)

  test("with nobody to pull, the cost is paid and the secret stays"):
    val start = withPawn(withPawn(staged(), p3, ancientCity), p2, ancientCity)
    val done = use(start, Whistle.id, whistle).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, whistle).get.tokens, Tokens(0, 1))
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(pawnOf(after, p3), ancientCity)
    assert(!ops(done.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assert(!usable(after, Whistle.id), "the secret still rests on the Whistle")

  test("it is unusable without a secret"):
    assert(usable(staged(), Whistle.id))
    assert(!usable(staged(secrets = 0), Whistle.id))
    assert(use(staged(secrets = 0), Whistle.id, whistle).isLeft)

  test("it writes the pawn it pulled and the secret it gave"):
    val parked = use(staged(), Whistle.id, whistle).toOption.get
    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p2.value} at ${ancientCity.value} and gave " +
        s"${p2.value} the Whistle's secret.", covers = false)))

  test("with nobody to pull it writes that"):
    val start = withPawn(withPawn(staged(), p3, ancientCity), p2, ancientCity)
    val done = use(start, Whistle.id, whistle).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      "used.none", "No pawn could be pulled.", covers = false)))
