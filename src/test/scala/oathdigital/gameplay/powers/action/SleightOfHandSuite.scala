package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class SleightOfHandSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val sleight = CatalogNames.denizen("Sleight of Hand")
  private val source = DecisionOptionRef.Denizen(sleight)
  private val victim = Table.p2
  private val bystander = Table.p3

  /** p1's Act at Ancient City holding Sleight of Hand as an adviser with
    * `favor`. `victim` (p2) stands at Ancient City holding the given
    * secrets; `bystander` (p3) stands at Buried Giant with 5 faceup and 5
    * facedown secrets, so it is never a target.
    */
  private def staged(up: Int, down: Int, favor: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, sleight).favor(actor, favor)
      .pawn(victim, "Ancient City").secrets(victim, up, down)
      .pawn(bystander, "Buried Giant").secrets(bystander, 5, 5).ready
  private def secretsOf(ready: ReadyGame, id: PlayerId = actor) =
    (Look(ready).player(id).board.faceUpSecrets, Look(ready).player(id)
      .board.faceDownSecrets)
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))

  test("it places a favor on its card and offers players at the actor's site " +
      "holding two or more secrets"):
    val ready = staged(2, 0)
    val t = accepted(use(ready, SleightOfHand, source))
    assert(awaits(t, SleightOfHand.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Vector("player" -> victim.value)))
    assertEquals(Look(after(t)).tokensOn(sleight), Tokens(1, 0))
    assertEquals(Look(after(t)).player(actor).board.favor, 0)

  test("a target with faceup secrets only gives one faceup secret"):
    val ready = staged(2, 0)
    val t = accepted(use(ready, SleightOfHand, source))
    val done = accepted(answer(t, actor, SleightOfHand.decisionId,
      choose(victim)))
    assertEquals(secretsOf(after(done), victim), (1, 0))
    assertEquals(secretsOf(after(done)), (secretsOf(ready)._1 + 1, 0))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))

  test("a target with facedown secrets only gives one, arriving facedown"):
    val ready = staged(0, 2)
    val t = accepted(use(ready, SleightOfHand, source))
    val done = accepted(answer(t, actor, SleightOfHand.decisionId,
      choose(victim)))
    assertEquals(secretsOf(after(done), victim), (0, 1))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1, secretsOf(ready)._2 + 1))

  test("a mixed target gives a faceup secret, arriving faceup, and keeps " +
      "its facedown secrets"):
    val ready = staged(1, 2)
    val t = accepted(use(ready, SleightOfHand, source))
    val done = accepted(answer(t, actor, SleightOfHand.decisionId,
      choose(victim)))
    assertEquals(secretsOf(after(done), victim), (0, 2))
    assertEquals(secretsOf(after(done)),
      (secretsOf(ready)._1 + 1, secretsOf(ready)._2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))

  test("the mixed-target batch survives the journal wire"):
    val ready = staged(1, 2)
    val t = accepted(use(ready, SleightOfHand, source))
    val done = accepted(answer(t, actor, SleightOfHand.decisionId,
      choose(victim)))
    assertWireRoundTrips(t.events ++ done.events)

  test("a player holding one secret is not a target, so nothing is asked"):
    val ready = staged(1, 0)
    val t = accepted(use(ready, SleightOfHand, source))
    parked.assertNotParked(t.state)
    assertEquals(secretsOf(after(t), victim), (1, 0))
    assertEquals(Look(after(t)).tokensOn(sleight), Tokens(1, 0))
    assertEquals(Look(after(t)).player(actor).board.favor, 0)

  test("a player at another site is not a target"):
    val ready = Table.from(staged(3, 0)).pawn(victim, "Buried Giant").ready
    val t = accepted(use(ready, SleightOfHand, source))
    parked.assertNotParked(t.state)
    assertEquals(secretsOf(after(t), victim), (3, 0))

  test("every eligible player is offered"):
    val ready = Table.from(staged(2, 0)).pawn(bystander, "Ancient City")
      .secrets(bystander, 2, 0).ready
    val t = accepted(use(ready, SleightOfHand, source))
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("player" -> victim.value, "player" -> bystander.value)))

  test("only the acting player answers, with an offered player"):
    val t = accepted(use(staged(2, 0), SleightOfHand, source))
    assertEquals(
      answer(t, victim, SleightOfHand.decisionId, choose(victim)).left.toOption,
      Some(OathViolation.WrongPlayer(actor, victim)))
    assertEquals(
      answer(t, actor, SleightOfHand.decisionId,
        choose(bystander)).left.toOption,
      Some(Refusals.notOffered(SleightOfHand.decisionId)))
    assertEquals(
      answer(t, actor, SleightOfHand.decisionId, choose(actor)).left.toOption,
      Some(Refusals.notOffered(SleightOfHand.decisionId)))

  test("it is unusable without a favor to place"):
    val ready = staged(2, 0, favor = 0)
    assertEquals(usableNow(ready), Vector.empty)
    assertEquals(use(ready, SleightOfHand, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))

  test("it writes the secret it took"):
    val t = accepted(use(staged(2, 0), SleightOfHand, source))
    val done = accepted(answer(t, actor, SleightOfHand.decisionId,
      choose(victim)))
    assertEquals(NoteText.said(SleightOfHand, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 secret from ${victim.value}.",
      covers = false)))

  test("with nobody to rob it writes that"):
    val t = accepted(use(staged(1, 0), SleightOfHand, source))
    assertEquals(NoteText.said(SleightOfHand, t.events), Vector(NoteText.Said(
      "used.none", "No player could be robbed.", covers = false)))
