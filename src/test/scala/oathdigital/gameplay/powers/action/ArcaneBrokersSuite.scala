package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class ArcaneBrokersSuite extends munit.FunSuite:
  import TargetsFixture._

  private val brokers = CatalogNames.denizen("Arcane Brokers")
  private val source = DecisionOptionRef.Denizen(brokers)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")
  private val sticks = RelicId("R09")

  /** p1's Act, holding Arcane Brokers as an adviser, `favor` favor and
    * `relics` faceup. */
  private def staged(relics: Vector[RelicId] = Vector(whistle, sticks),
      favor: Int = 1): Table =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).adviser(p1, brokers)
      .favor(p1, favor))(_.relic(p1, _))

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("it places a favor and asks which held relic to discard"):
    val t = use(staged().ready, ArcaneBrokers, source).toOption.get
    assert(awaits(t, ArcaneBrokers.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(brokers), Tokens(1, 0))
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> whistle.value, "relic" -> sticks.value)))

  test("a lone relic is still asked about"):
    val t = use(staged(Vector(whistle)).ready, ArcaneBrokers, source)
      .toOption.get
    assert(awaits(t, ArcaneBrokers.decisionId),
      parked.parkedDecision(t.state).toString)

  test("the chosen relic is set aside and 2 secrets are gained"):
    val ready = staged().ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector(sticks))
    assert(end.game.current.setAsideRelics.contains(whistle))
    assertEquals(secretsOf(end), secretsOf(ready) + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("a facedown relic may be discarded"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, brokers)
      .favor(p1, 1).relic(p1, whistle, facedown = true).ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])

  test("the relic's secrets return facedown, and the line counts only the " +
      "2 gained, covering the gain line"):
    val ready = staged(Vector(whistle)).tokens(whistle, secrets = 1).ready
    val t = use(ready, ArcaneBrokers, source).toOption.get
    val done = answer(t, p1, ArcaneBrokers.decisionId, relicRef(whistle))
      .toOption.get
    assertEquals(Look(after(done)).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} discarded ${whistle.value} and gained 2 secrets.",
        covers = true)))

  test("the Grand Scepter cannot be chosen"):
    val t = use(staged(Vector(scepter, whistle)).ready, ArcaneBrokers, source)
      .toOption.get
    assert(answer(t, p1, ArcaneBrokers.decisionId, relicRef(scepter)).isLeft)

  test("holding only the Grand Scepter, it asks nothing, discards nothing " +
      "and writes nothing"):
    val ready = staged(Vector(scepter)).ready
    val done = use(ready, ArcaneBrokers, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(secretsOf(after(done)), secretsOf(ready))
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector.empty)

  test("holding no relic, the favor stays paid and the line says so"):
    val done = use(staged(Vector.empty).ready, ArcaneBrokers, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(brokers), Tokens(1, 0))
    assertEquals(NoteText.said(ArcaneBrokers, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == ArcaneBrokers.id))
    assert(use(broke, ArcaneBrokers, source).isLeft)
