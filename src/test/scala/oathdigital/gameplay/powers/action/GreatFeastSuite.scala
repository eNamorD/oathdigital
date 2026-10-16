package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class GreatFeastSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val feast = CatalogNames.denizen("Great Feast")
  private val source = DecisionOptionRef.Denizen(feast)
  private val power = GreatFeast.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  // Denizens whose powers these tests do not use, a Beast and a Nomad.
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")

  /** p1's Act at Ancient City holding Great Feast, with `supply` Supply and
    * `favor` favor. */
  private def staged(supply: Int = 2, favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).adviser(p1, feast)
      .supply(p1, supply).favor(p1, favor)

  /** The discard pile of the region after Ancient City's (the Cradle):
    * where a card discarded there goes. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    Look(ready).discardAfter(home)

  /** The walk Great Feast started and the walk after `card` was chosen. */
  private case class Eaten(started: OathTransition, answered: OathTransition)

  private def eat(ready: ReadyGame, card: DenizenId): Eaten =
    val started = accepted(use(ready, power, source))
    Eaten(started, accepted(answer(started, p1, GreatFeast.decisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  test("it places a favor and offers only the Beast cards at the site"):
    val ready = staged().denizen(errandBoy, at = home)
      .denizen(wildMounts, at = home).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, GreatFeast.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(feast), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value)))

  test("the card is discarded and the player gains 3 Supply"):
    // 2 Supply + 3 = 5.
    val ready = staged(supply = 2).denizen(errandBoy, at = home).ready
    val Eaten(t, done) = eat(ready, errandBoy)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(pile(end).lastOption, Some(errandBoy))
    assertEquals(Look(end).supply(p1), 5)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${errandBoy.value} and gained 3 Supply.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a nearly full track gains what fits"):
    val ready = staged(supply = 6).denizen(errandBoy, at = home).ready
    val done = eat(ready, errandBoy).answered
    assertEquals(Look(after(done)).supply(p1), 7)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${errandBoy.value} and gained 1 Supply.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val asked = accepted(use(staged().denizen(wildMounts, at = home).ready,
      power, source))
    assert(awaits(asked, GreatFeast.decisionId))
    val t = accepted(continuedPast(asked, GreatFeast.decisionId))
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast card.", covers = true)))

  test("with only a Beast card the Hall protects, the line says it could " +
      "not be discarded"):
    // Bandits rule Ancient City, so nobody may consent to the discard.
    val ready = staged().denizen(errandBoy, at = home)
      .edifice(CatalogNames.edifice("Hall of Ministers"), EdificeSide.Intact,
        at = home).bandits(home, 2).ready
    val asked = accepted(use(ready, power, source))
    assert(awaits(asked, GreatFeast.decisionId))
    val t = accepted(continuedPast(asked, GreatFeast.decisionId))
    assertEquals(Look(after(t)).cardsAt(home).contains(errandBoy), true)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.kept", s"No Beast card at ${home.value} could be discarded.",
      covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).denizen(errandBoy, at = home).ready
    assert(!usableNow(broke).exists(_.power.id == GreatFeast.id))
    assertEquals(use(broke, power, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))

  test("a full track gains nothing, and the line says 0 Supply"):
    val ready = staged(supply = 7).denizen(errandBoy, at = home).ready
    val done = eat(ready, errandBoy).answered
    val end = after(done)
    assertEquals(Look(end).supply(p1), 7)
    assertEquals(pile(end).lastOption, Some(errandBoy))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${errandBoy.value} and gained 0 Supply.",
      covers = true)))
