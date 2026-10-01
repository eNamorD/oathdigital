package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class RelicBreakerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val breaker = CatalogNames.denizen("Relic Breaker")
  private val source = DecisionOptionRef.Denizen(breaker)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")
  private val sticks = RelicId("R09")

  /** p1's Act, holding Relic Breaker as an adviser and `relics` faceup. */
  private def staged(relics: Vector[RelicId] = Vector(whistle, sticks))
      : Table =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).adviser(p1, breaker))(
      _.relic(p1, _))

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("it costs nothing and asks which held relic to bury"):
    val t = use(staged().ready, RelicBreaker, source).toOption.get
    assert(awaits(t, RelicBreaker.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> whistle.value, "relic" -> sticks.value)))

  test("the relic goes to the bottom of the relic deck and 1 secret is " +
      "gained"):
    val ready = staged().ready
    val t = use(ready, RelicBreaker, source).toOption.get
    val done = answer(t, p1, RelicBreaker.decisionId, relicRef(whistle))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(sticks))
    assertEquals(end.game.current.commonCards.relicDeck.last, whistle)
    assertEquals(secretsOf(end), secretsOf(ready) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the relic's secrets return facedown, and the line counts only the " +
      "secret gained, covering the Buried and gain lines"):
    val ready = staged(Vector(whistle)).tokens(whistle, secrets = 1).ready
    val t = use(ready, RelicBreaker, source).toOption.get
    val done = answer(t, p1, RelicBreaker.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} buried ${whistle.value} and gained 1 secret.",
        covers = true)))

  test("the Grand Scepter cannot be chosen"):
    val t = use(staged(Vector(scepter, whistle)).ready, RelicBreaker, source)
      .toOption.get
    assert(answer(t, p1, RelicBreaker.decisionId, relicRef(scepter)).isLeft)

  test("holding only the Grand Scepter, it asks nothing and writes nothing"):
    val ready = staged(Vector(scepter)).ready
    val done = use(ready, RelicBreaker, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector.empty)

  test("holding no relic, nothing happens and the line says so"):
    val done = use(staged(Vector.empty).ready, RelicBreaker, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(RelicBreaker, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))
