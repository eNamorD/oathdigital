package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.model._

class CharmingFriendSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val friend = DenizenId("131")
  private val source = DecisionOptionRef.Denizen(friend)
  private val victim = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withFavor(ready: ReadyGame, id: PlayerId, amount: Int) =
    updatePlayer(ready, id)(p => p.copy(board = p.board.copy(favor = amount)))
  private def favorOf(ready: ReadyGame, id: PlayerId = actor) =
    player(ready, id).board.favor
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def cardOf(ready: ReadyGame) = player(ready).advisers.collectFirst {
    case d: DenizenState if d.id == friend => d }.get

  /** The actor holds Charming Friend as an adviser with `secrets` faceup.
    * `victim` stands at the actor's site with `favor`; `bystander` stands
    * elsewhere with 5 favor, so it is never a target.
    */
  private def staged(favor: Int = 2, secrets: Int = 1) =
    val actorReady = inPhase(withSecrets(asAdviser(base, friend), actor,
      secrets, 0), Phase.Act)
    val placed = withPawn(withPawn(actorReady, victim, home(actorReady)),
      bystander, elsewhere(actorReady))
    withFavor(withFavor(placed, victim, favor), bystander, 5)

  test("it places a secret on its card and offers the players at the " +
      "actor's site"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    assert(awaits(t, CharmingFriend.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)

  test("the chosen player gives one favor"):
    val ready = staged()
    val t = use(ready, CharmingFriend, source).toOption.get
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(favorOf(after(done), victim), 1)
    assertEquals(favorOf(after(done)), favorOf(ready) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("it writes the favor it took"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from ${victim.value}.", covers = false)))

  test("a chosen player with no favor gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val t = use(ready, CharmingFriend, source).toOption.get
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    val done = answer(t, actor, CharmingFriend.decisionId, choose(victim))
      .toOption.get
    assertEquals(favorOf(after(done), victim), 0)
    assertEquals(favorOf(after(done)), favorOf(ready))
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said("used.empty", s"${victim.value} had no favor to take.",
        covers = false)))

  test("with nobody at the site the cost is paid and nothing else happens"):
    val ready = withPawn(staged(), victim, elsewhere(staged()))
    val t = use(ready, CharmingFriend, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(favorOf(after(t), victim), 2)
    assertEquals(NoteText.said(CharmingFriend, t.events), Vector(
      NoteText.Said("used.none", "No player could be robbed.",
        covers = false)))

  test("every other player at the site is offered"):
    val ready = withPawn(staged(), bystander, home(staged()))
    val t = use(ready, CharmingFriend, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))

  test("only the acting player answers, with an offered player"):
    val t = use(staged(), CharmingFriend, source).toOption.get
    assert(answer(t, victim, CharmingFriend.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, CharmingFriend.decisionId, choose(bystander))
      .isLeft)
    assert(answer(t, actor, CharmingFriend.decisionId, choose(actor)).isLeft)

  test("it is unusable without a secret to place"):
    val ready = staged(secrets = 0)
    assert(!usableNow(ready).exists(_.power.id == CharmingFriend.id))
    assert(use(ready, CharmingFriend, source).isLeft)
