package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class WizardSchoolSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val school = CatalogNames.denizen("Wizard School")
  private val source = DecisionOptionRef.Denizen(school)

  /** p1's Act at Ancient City with Wizard School (site-only) at the site
    * and `favor` favor. */
  private def staged(favor: Int = 2): ReadyGame =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(school, at = "Ancient City").favor(actor, favor).ready

  test("it places a favor, gains a secret and ends the Act phase"):
    // 2 favor less the 1 placed leave 1.
    val ready0 = staged(favor = 2)
    val done = accepted(use(rules(), ready0, WizardSchool.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).tokensOn(school), Tokens(1, 0))
    assertEquals(Look(end).player(actor).board.favor, 1)
    assertEquals(allSecrets(end), allSecrets(ready0) + 1)
    walkerParked.assertResumed(done.state, Phase.Rest, actor)
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("it is unusable without a favor"):
    val broke = staged(favor = 0)
    assert(!usableIds(broke).contains(WizardSchool.id))
    assert(use(rules(), broke, WizardSchool.id, source).isLeft)

  test("it writes the gain, covering the generic line, then the phase's end"):
    val done = accepted(use(rules(), staged(), WizardSchool.id, source))
    assertEquals(NoteText.said(WizardSchool, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} gained 1 secret.",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.",
        covers = false)))
