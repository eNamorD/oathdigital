package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.accepted

class WolvesSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val wolves = CatalogNames.denizen("Wolves")
  private val source = DecisionOptionRef.Denizen(wolves)
  private val victim = Table.p2

  /** p1's Act at Ancient City with Wolves at the site and `secrets` faceup
    * secrets. */
  private def staged(secrets: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(wolves, at = "Ancient City").secrets(actor, secrets).ready
  private def bankOf(ready: ReadyGame, id: PlayerId) =
    warbandBank(ready, Look(ready).forceKind(id))
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def started = accepted(use(staged(), Wolves, source))

  test("using it places a secret on its card and asks for a player board"):
    val t = started
    assert(awaits(t, Wolves.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(wolves), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor), Some(after(t).game.current.players
      .map(p => "player" -> p.player.value)))

  test("the chosen board loses one warband, which returns to its bank"):
    val t = started
    val done = accepted(answer(t, actor, Wolves.decisionId, choose(victim)))
    assertEquals(Look(after(done)).player(victim).board.warbands,
      Look(after(t)).player(victim).board.warbands - 1)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim) + 1)
    assertEquals(replayed(staged(), t.events ++ done.events), Right(done.state))

  test("the acting player's own board is a legal target"):
    val t = started
    val done = accepted(answer(t, actor, Wolves.decisionId, choose(actor)))
    assertEquals(Look(after(done)).player(actor).board.warbands,
      Look(after(t)).player(actor).board.warbands - 1)

  test("the kill is best-effort: a board with no warbands loses nothing and " +
      "the cost stays paid"):
    val empty = Table.from(staged()).boardWarbands(victim, 0).unchecked
    val t = accepted(use(empty, Wolves, source))
    val done = accepted(answer(t, actor, Wolves.decisionId, choose(victim)))
    assertEquals(Look(after(done)).player(victim).board.warbands, 0)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim))
    assertEquals(Look(after(done)).tokensOn(wolves), Tokens(0, 1))

  test("only the acting player answers, and only with an offered board"):
    val t = started
    assert(answer(t, victim, Wolves.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, Wolves.decisionId,
      choose(PlayerId("nobody"))).isLeft)

  test("it is unusable without a faceup secret to place"):
    assertEquals(usableNow(staged(secrets = 0)), Vector.empty)
    assert(use(staged(secrets = 0), Wolves, source).isLeft)

  test("it writes the warband it killed"):
    val done = accepted(answer(started, actor, Wolves.decisionId,
      choose(victim)))
    assertEquals(NoteText.said(Wolves, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${victim.value} warband.", covers = false)))

  test("a board with no warband is written as such"):
    val empty = Table.from(staged()).boardWarbands(victim, 0).unchecked
    val t = accepted(use(empty, Wolves, source))
    val done = accepted(answer(t, actor, Wolves.decisionId, choose(victim)))
    assertEquals(NoteText.said(Wolves, done.events), Vector(NoteText.Said(
      "used.none", s"${victim.value} had no warband to kill.", covers = false)))
