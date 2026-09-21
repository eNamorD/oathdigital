package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WhistleSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val whistle = RelicId("R08")
  private def staged(secrets: Int = 2) = inPhase(
    withSecrets(withRelic(base, whistle), secrets), Phase.Act)
  private val target = DecisionOptionRef.Player(p3)

  test("Whistle is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Whistle.id).isDefined)
  }

  test("it pulls the chosen pawn to the actor's site and hands over the secret") {
    val start = staged()
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assertEquals(relicOf(readyOf(parked.state), whistle).get.tokens, Tokens(0, 1))

    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(pawnOf(after, p3), ancientCity)
    assertEquals(pawnOf(after, p1), buriedGiant)
    assertEquals(pawnOf(after), ancientCity)
    assertEquals(relicOf(after, whistle).get.tokens, Tokens.empty)
    assertEquals(player(after, p3).board.faceUpSecrets,
      player(start, p3).board.faceUpSecrets + 1)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))
  }

  test("only players at other sites are offered, even when there is one") {
    val start = withPawn(staged(), p1, ancientCity)
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(parkedAt(parked, Whistle.decisionId))
    assert(choose(parked.state, Whistle.decisionId,
      DecisionOptionRef.Player(p1)).isLeft)
    assert(choose(parked.state, Whistle.decisionId, target).isRight)
  }

  test("with nobody to pull, the cost is paid and the secret stays") {
    val start = withPawn(withPawn(staged(), p1, ancientCity), p3, ancientCity)
    val done = use(start, Whistle.id, whistle).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, whistle).get.tokens, Tokens(0, 1))
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(pawnOf(after, p1), ancientCity)
    assert(!ops(done.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assert(!usable(after, Whistle.id), "the secret still rests on the Whistle")
  }

  test("it is unusable without a secret, when occupied, or facedown") {
    assert(usable(staged(), Whistle.id))
    assert(!usable(staged(secrets = 0), Whistle.id))
    assert(use(staged(secrets = 0), Whistle.id, whistle).isLeft)
    assert(!usable(withRelicTokens(staged(), whistle, Tokens(0, 1)), Whistle.id))
    val facedown = inPhase(withSecrets(
      withRelic(base, whistle, Orientation.FaceDown), 2), Phase.Act)
    assert(!usable(facedown, Whistle.id))
    assert(use(facedown, Whistle.id, whistle).isLeft)
  }
}
