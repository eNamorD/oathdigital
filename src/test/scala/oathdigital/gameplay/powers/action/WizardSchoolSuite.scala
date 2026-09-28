package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class WizardSchoolSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val school = DenizenId("34")
  private val source = DecisionOptionRef.Denizen(school)

  /** Wizard School is site-only; it stands at the actor's pawn site. */
  private def staged(favor: Int = 2): ReadyGame =
    act(withBoard(atHome(base, school))(_.copy(favor = favor)))

  test("it places a favor, gains a secret and ends the Act phase"):
    val ready0 = staged()
    val done = use(rules(), ready0, WizardSchool.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, school), Tokens(1, 0))
    assertEquals(player(end).board.favor, 1)
    assertEquals(secrets(end), secrets(ready0) + 1)
    walkerParked.assertResumed(done.state, Phase.Rest, actor)
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0)
    assert(!usableIds(broke).contains(WizardSchool.id))
    assert(use(rules(), broke, WizardSchool.id, source).isLeft)

  test("it writes the gain, covering the generic line, then the phase's end"):
    val done = use(rules(), staged(), WizardSchool.id, source).toOption.get
    assertEquals(NoteText.said(WizardSchool, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} gained 1 secret.",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.",
        covers = false)))
