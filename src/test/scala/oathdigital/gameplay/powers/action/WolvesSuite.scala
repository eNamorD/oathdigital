package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PlayerFacts, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WolvesSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val wolves = DenizenId("39")
  private val source = DecisionOptionRef.Denizen(wolves)
  private val victim = others(base).head

  private def staged(secrets: Int = 1) = inPhase(
    withSecrets(atHome(base, wolves), actor, secrets, 0), Phase.Act)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == wolves => d }.get
  private def bankOf(ready: ReadyGame, id: PlayerId) =
    warbandBank(ready, PlayerFacts.forceKind(ready, id).toOption.get)
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))
  private def parked = use(staged(), Wolves, source).toOption.get

  test("Wolves is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Wolves.id).isDefined)
  }

  test("using it places a secret on its card and asks for a player board") {
    val t = parked
    assert(awaits(t, Wolves.decisionId), t.continue.toString)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor), Some(after(t).game.current.players
      .map(p => "player" -> p.player.value)))
  }

  test("the chosen board loses one warband, which returns to its bank") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(player(after(done), victim).board.warbands,
      player(after(t), victim).board.warbands - 1)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim) + 1)
    assertEquals(replayed(staged(), t.events ++ done.events), Right(done.state))
  }

  test("the acting player's own board is a legal target") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(actor)).toOption.get
    assertEquals(player(after(done)).board.warbands,
      player(after(t)).board.warbands - 1)
  }

  test("the kill is best-effort: a board with no warbands loses nothing and " +
      "the cost stays paid") {
    val empty = updatePlayer(staged(), victim)(p =>
      p.copy(board = p.board.copy(warbands = 0)))
    val t = use(empty, Wolves, source).toOption.get
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(player(after(done), victim).board.warbands, 0)
    assertEquals(bankOf(after(done), victim), bankOf(after(t), victim))
    assertEquals(cardOf(after(done)).tokens, Tokens(0, 1))
  }

  test("only the acting player answers, and only with an offered board") {
    val t = parked
    assert(answer(t, victim, Wolves.decisionId, choose(victim)).isLeft)
    assert(answer(t, actor, Wolves.decisionId,
      choose(PlayerId("nobody"))).isLeft)
  }

  test("it is unusable without a faceup secret to place") {
    assertEquals(usableNow(staged(secrets = 0)), Vector.empty)
    assert(use(staged(secrets = 0), Wolves, source).isLeft)
  }

  test("it is unusable again while its card holds the secret") {
    val t = parked
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    val again = withSecrets(after(done), actor, 1, 0)
    assertEquals(usableNow(again), Vector.empty)
    assert(use(again, Wolves, source).isLeft)
  }
}
