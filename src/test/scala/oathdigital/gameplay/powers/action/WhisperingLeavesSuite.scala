package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class WhisperingLeavesSuite extends munit.FunSuite:
  import TargetsFixture._

  private val leaves = CatalogNames.denizen("Whispering Leaves")
  private val source = DecisionOptionRef.Denizen(leaves)

  /** p1's Act holding Whispering Leaves with `secrets` faceup secrets. p2
    * stands at p1's site with `favor` favor. */
  private def staged(favor: Int = 3, secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, leaves).secrets(p1, secrets)
      .pawn(p2, at = Table.homeOf(p1)).favor(p2, favor)

  private def choose(ready: ReadyGame, target: PlayerId) =
    val t = use(ready, WhisperingLeaves, source).toOption.get
    (t, answer(t, p1, WhisperingLeaves.decisionId,
      pick(DecisionOptionRef.Player(target))).toOption.get)

  test("it places a secret and offers every player at the site, the player " +
      "included"):
    val t = use(staged().ready, WhisperingLeaves, source).toOption.get
    assert(awaits(t, WhisperingLeaves.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(leaves), Tokens(0, 1))
    assertEquals(offered(t, p1),
      Some(Vector("player" -> p1.value, "player" -> p2.value)))

  test("the chosen player gives 2 favor onto Whispering Leaves"):
    val ready = staged().ready
    val (t, done) = choose(ready, p2)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).favor(p2), 1)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(2, 1))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the giver and the favor placed"):
    val (_, done) = choose(staged().ready, p2)
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p2.value} placed 2 favor on it.",
        covers = false)))

  test("a player with 1 favor gives it"):
    val (_, done) = choose(staged(favor = 1).ready, p2)
    assertEquals(Look(after(done)).favor(p2), 0)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(1, 1))
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p2.value} placed 1 favor on it.",
        covers = false)))

  test("the player may choose themself"):
    val (_, done) = choose(staged().favor(p1, 2).ready, p1)
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(Look(after(done)).favor(p2), 3)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(2, 1))

  test("a player with no favor gives nothing, and the line says so"):
    val (_, done) = choose(staged(favor = 0).ready, p2)
    assertEquals(Look(after(done)).tokensOn(leaves), Tokens(0, 1))
    assertEquals(NoteText.said(WhisperingLeaves, done.events), Vector(
      NoteText.Said("used.empty", s"${p2.value} had no favor to place.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == WhisperingLeaves.id))
    assert(use(broke, WhisperingLeaves, source).isLeft)
