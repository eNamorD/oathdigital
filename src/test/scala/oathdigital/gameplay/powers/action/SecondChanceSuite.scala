package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class SecondChanceSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val chance = CatalogNames.denizen("Second Chance")
  private val source = DecisionOptionRef.Denizen(chance)
  private val power = SecondChance.forCatalog(catalog)
  // Denizens whose powers these tests do not use: only their suits matter.
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val defame = CatalogNames.denizen("Defame")
  private val homesteaders = CatalogNames.denizen("Homesteaders")

  /** p1's Act holding Second Chance with `secrets` faceup secrets. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, chance).secrets(p1, secrets)

  /** The walk Second Chance started and the walk after `target` was
    * chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition)

  private def choose(ready: ReadyGame, target: PlayerId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(target)))))

  test("it places a secret and offers only players with a faceup Order or " +
      "Discord adviser"):
    val ready = staged().adviser(p2, codeOfHonor).adviser(p3, homesteaders)
      .adviser(p3, defame, facedown = true).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, SecondChance.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(chance), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it kills one of the target's warbands, then the player gains one"):
    // 3 warbands each: p2 keeps 3 - 1 = 2 and p1 holds 3 + 1 = 4.
    val ready = staged().boardWarbands(p1, 3).boardWarbands(p2, 3)
      .adviser(p2, codeOfHonor).ready
    val Chosen(t, done) = choose(ready, p2)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).boardWarbands(p2), 2)
    assertEquals(Look(after(done)).boardWarbands(p1), 4)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the kill and the gain"):
    val done = choose(staged().adviser(p2, codeOfHonor).ready, p2).answered
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p2.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("a Discord adviser qualifies, and the player may target themself " +
      "even with an empty supply"):
    // 14 is the printed warband supply, so p1's bank is empty.
    val ready = staged().adviser(p1, defame).boardWarbands(p1, 14).ready
    val t = accepted(use(ready, power, source))
    assertEquals(offered(t, p1), Some(Vector("player" -> p1.value)))
    val done = accepted(answer(t, p1, SecondChance.decisionId,
      pick(DecisionOptionRef.Player(p1))))
    assertEquals(Look(after(done)).boardWarbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${p1.value} warband, and ${p1.value} gained " +
        "1 warband.", covers = false)))

  test("with an empty supply the kill still happens, and the line says so"):
    // 14 is the printed warband supply, so p1's bank is empty; p2 keeps
    // 3 - 1 = 2.
    val ready = staged().adviser(p2, codeOfHonor).boardWarbands(p1, 14)
      .boardWarbands(p2, 3).ready
    val done = choose(ready, p2).answered
    assertEquals(Look(after(done)).boardWarbands(p2), 2)
    assertEquals(Look(after(done)).boardWarbands(p1), 14)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.killed", s"Killed 1 ${p2.value} warband.", covers = false)))

  test("a target with no warband loses nothing, and nothing is gained"):
    val ready = staged().adviser(p2, codeOfHonor).boardWarbands(p2, 0)
      .boardWarbands(p1, 3).ready
    val done = choose(ready, p2).answered
    assertEquals(Look(after(done)).boardWarbands(p1), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.spared", s"${p2.value} had no warband to kill.", covers = false)))

  test("with no candidate, the cost stays paid and the line says so"):
    val ready = staged().adviser(p2, homesteaders).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(chance), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No player had a faceup Order or Discord adviser.",
      covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).adviser(p2, codeOfHonor).ready
    assert(!usableNow(broke).exists(_.power.id == SecondChance.id))
    assert(use(broke, power, source).isLeft)
