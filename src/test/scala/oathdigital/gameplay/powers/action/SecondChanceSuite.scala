package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class SecondChanceSuite extends munit.FunSuite:
  import TargetsFixture._

  private val chance = CatalogNames.denizen("Second Chance")
  private val source = DecisionOptionRef.Denizen(chance)
  private val power = SecondChance.forCatalog(catalog).get
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val discord = SearchFixture.denizensOf(Suit.Discord)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** p1's Act holding Second Chance with `secrets` faceup secrets. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, chance).secrets(p1, secrets)

  private def choose(ready: ReadyGame, target: PlayerId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(target))).toOption.get)

  test("it places a secret and offers only players with a faceup Order or " +
      "Discord adviser"):
    val ready = staged().adviser(p2, order(0)).adviser(p3, hearth(0))
      .adviser(p3, discord(0), facedown = true).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, SecondChance.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(chance), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it kills one of the target's warbands, then the player gains one"):
    val ready = staged().adviser(p2, order(0)).ready
    val (t, done) = choose(ready, p2)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).warbands(p2), 2)
    assertEquals(Look(after(done)).warbands(p1), 4)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the kill and the gain"):
    val (_, done) = choose(staged().adviser(p2, order(0)).ready, p2)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p2.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("a Discord adviser qualifies, and the player may target themself " +
      "even with an empty supply"):
    val ready = staged().adviser(p1, discord(0)).warbands(p1, 14).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(offered(t, p1), Some(Vector("player" -> p1.value)))
    val done = answer(t, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(p1))).toOption.get
    assertEquals(Look(after(done)).warbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p1.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("with an empty supply the kill still happens, and the line says so"):
    val ready = staged().adviser(p2, order(0)).warbands(p1, 14).ready
    val (_, done) = choose(ready, p2)
    assertEquals(Look(after(done)).warbands(p2), 2)
    assertEquals(Look(after(done)).warbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.killed", s"Killed 1 ${p2.value} warband.", covers = false)))

  test("a target with no warband loses nothing, and nothing is gained"):
    val ready = staged().adviser(p2, order(0)).warbands(p2, 0).ready
    val (_, done) = choose(ready, p2)
    assertEquals(Look(after(done)).warbands(p1), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.spared", s"${p2.value} had no warband to kill.", covers = false)))

  test("with no candidate, the cost stays paid and the line says so"):
    val ready = staged().adviser(p2, hearth(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(chance), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No player had a faceup Order or Discord adviser.",
      covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).adviser(p2, order(0)).ready
    assert(!usableNow(broke).exists(_.power.id == SecondChance.id))
    assert(use(broke, power, source).isLeft)
