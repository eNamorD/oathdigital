package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class CharmingFriendSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val friend = CatalogNames.denizen("Charming Friend")
  private val source = DecisionOptionRef.Denizen(friend)
  private val victim = Table.p2
  private val bystander = Table.p3

  private def favorOf(ready: ReadyGame, id: PlayerId = actor) =
    Look(ready).player(id).board.favor
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))

  /** p1's Act at Ancient City holding Charming Friend as an adviser with
    * `secrets` faceup. `victim` (p2) stands at Ancient City with `favor`;
    * `bystander` (p3) stands at Buried Giant with 5 favor, so it is never a
    * target.
    */
  private def staged(favor: Int = 2, secrets: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, friend).secrets(actor, secrets)
      .pawn(victim, "Ancient City").favor(victim, favor)
      .pawn(bystander, "Buried Giant").favor(bystander, 5).ready

  test("it places a secret on its card and offers the players at the " +
      "actor's site"):
    val t = accepted(use(staged(), CharmingFriend, source))
    assert(awaits(t, CharmingFriend.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(Look(after(t)).tokensOn(friend), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.faceUpSecrets, 0)

  test("the chosen player gives one favor"):
    val ready = staged()
    val t = accepted(use(ready, CharmingFriend, source))
    val done = accepted(answer(t, actor, CharmingFriend.decisionId,
      choose(victim)))
    assertEquals(favorOf(after(done), victim), 1)
    assertEquals(favorOf(after(done)), favorOf(ready) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("it writes the favor it took"):
    val t = accepted(use(staged(), CharmingFriend, source))
    val done = accepted(answer(t, actor, CharmingFriend.decisionId,
      choose(victim)))
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from ${victim.value}.", covers = false)))

  test("a chosen player with no favor gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val t = accepted(use(ready, CharmingFriend, source))
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    val done = accepted(answer(t, actor, CharmingFriend.decisionId,
      choose(victim)))
    assertEquals(favorOf(after(done), victim), 0)
    assertEquals(favorOf(after(done)), favorOf(ready))
    assertEquals(NoteText.said(CharmingFriend, done.events), Vector(
      NoteText.Said("used.empty", s"${victim.value} had no favor to take.",
        covers = false)))

  test("with nobody at the site the cost is paid and nothing else happens"):
    val ready = Table.from(staged()).pawn(victim, "Buried Giant").ready
    val t = accepted(use(ready, CharmingFriend, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(friend), Tokens(0, 1))
    assertEquals(favorOf(after(t), victim), 2)
    assertEquals(NoteText.said(CharmingFriend, t.events), Vector(
      NoteText.Said("used.none", "No player could be robbed.",
        covers = false)))

  test("every other player at the site is offered"):
    val ready = Table.from(staged()).pawn(bystander, "Ancient City").ready
    val t = accepted(use(ready, CharmingFriend, source))
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))

  test("only the acting player answers, with an offered player"):
    val t = accepted(use(staged(), CharmingFriend, source))
    assertEquals(
      answer(t, victim, CharmingFriend.decisionId,
        choose(victim)).left.toOption,
      Some(OathViolation.WrongPlayer(actor, victim)))
    assertEquals(answer(t, actor, CharmingFriend.decisionId, choose(bystander))
      .left.toOption, Some(Refusals.notOffered(CharmingFriend.decisionId)))
    assertEquals(
      answer(t, actor, CharmingFriend.decisionId, choose(actor)).left.toOption,
      Some(Refusals.notOffered(CharmingFriend.decisionId)))

  test("it is unusable without a secret to place"):
    val ready = staged(secrets = 0)
    assert(!usableNow(ready).exists(_.power.id == CharmingFriend.id))
    assertEquals(use(ready, CharmingFriend, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
