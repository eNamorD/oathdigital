package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SleightOfHandSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val sleight = DenizenId("17")
  private val source = DecisionOptionRef.Denizen(sleight)
  private val victim = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get

  /** The actor holds Sleight of Hand as an adviser with `favor`. `victim` is
    * at the actor's site holding the given secrets. `bystander` is at
    * another site with plenty of secrets, so it is never a target.
    */
  private def staged(up: Int, down: Int, favor: Int = 1) = {
    val actorReady = inPhase(withBoard(asAdviser(base, sleight))(
      _.copy(favor = favor)), Phase.Act)
    val placed = withPawn(withPawn(actorReady, victim, home(actorReady)),
      bystander, elsewhere(actorReady))
    withSecrets(withSecrets(placed, victim, up, down), bystander, 5, 5)
  }
  private def secretsOf(ready: ReadyGame, id: PlayerId = actor) =
    (player(ready, id).board.faceUpSecrets, player(ready, id).board.faceDownSecrets)
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def cardOf(ready: ReadyGame) = player(ready).advisers.collectFirst {
    case d: DenizenState if d.id == sleight => d }.get

  test("Sleight of Hand is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(SleightOfHand.id).isDefined)
  }

  test("it places a favor on its card and offers players at the actor's site " +
      "holding two or more secrets") {
    val ready = staged(2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(awaits(t, SleightOfHand.decisionId), t.continue.toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)
  }

  test("a target with faceup secrets only gives one faceup secret") {
    val ready = staged(2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (1, 0))
    assertEquals(secretsOf(after(done)), (secretsOf(ready)._1 + 1, 0))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a target with facedown secrets only gives one, arriving facedown") {
    val ready = staged(0, 2)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (0, 1))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1, secretsOf(ready)._2 + 1))
  }

  test("a mixed target gives a faceup secret, arriving faceup, and keeps " +
      "its facedown secrets") {
    val ready = staged(1, 2)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(secretsOf(after(done), victim), (0, 2))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1 + 1, secretsOf(ready)._2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("the mixed-target batch survives the journal wire") {
    val ready = staged(1, 2)
    val t = use(ready, SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
  }

  test("a player holding one secret is not a target, so nothing is asked") {
    val ready = staged(1, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(secretsOf(after(t), victim), (1, 0))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)
  }

  test("a player at another site is not a target") {
    val ready = withPawn(staged(3, 0), victim, elsewhere(staged(3, 0)))
    val t = use(ready, SleightOfHand, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    assertEquals(secretsOf(after(t), victim), (3, 0))
  }

  test("every eligible player is offered") {
    val ready = withSecrets(withPawn(staged(2, 0), bystander,
      home(staged(2, 0))), bystander, 2, 0)
    val t = use(ready, SleightOfHand, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))
  }

  test("only the acting player answers, with an offered player") {
    val t = use(staged(2, 0), SleightOfHand, source).toOption.get
    assert(answer(t, victim, SleightOfHand.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, SleightOfHand.decisionId, choose(bystander)).isLeft)
    assert(answer(t, actor, SleightOfHand.decisionId, choose(actor)).isLeft)
  }

  test("it is unusable without a favor to place") {
    val ready = staged(2, 0, favor = 0)
    assertEquals(usableNow(ready), Vector.empty)
    assert(use(ready, SleightOfHand, source).isLeft)
  }
}
