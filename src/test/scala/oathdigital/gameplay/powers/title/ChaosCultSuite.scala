package oathdigital.gameplay.powers.title

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class ChaosCultSuite extends munit.FunSuite:
  private val power = ChaosCult
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(power)))
  private val (active, cultist, leader) = (p1, p2, p3)

  /** p2 (the cultist) holds Chaos Cult, faceup unless told otherwise, and
    * `holder` holds the title. Each of `rulers` rules one site: the first
    * Dunes, the second Fair Isle. Every player has 2 favor; it is p1's Act. */
  private def staged(rulers: Vector[PlayerId], facedown: Boolean = false,
      holder: Option[PlayerId] = None): Table =
    rulers.zip(Vector("Dunes", "Fair Isle")).foldLeft(Table.start
      .adviser(cultist, power.cardId, facedown = facedown)
      .oathkeeper(holder)
      .favor(p1, 2).favor(p2, 2).favor(p3, 2)) { case (t, (ruler, site)) =>
        t.warbandsAt(site, ruler, 1) }

  /** p1 travels to Broken Peaks; its action boundary settles the title. */
  private def travel(ready: ReadyGame) =
    rules.startWalker(Ready(ready), ActionRef.Travel, active, Vector.empty,
      Vector(DecisionOptionRef.Site(CatalogNames.site("Broken Peaks"))))

  test("Chaos Cult is in the default walker catalog and automatic"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
    assertEquals(power.resolution, PowerResolution.Automatic)

  test("when another player takes the title, the holder takes 1 favor from them"):
    val ready = staged(Vector(leader)).ready
    val done = accepted(travel(ready))
    val after = Outcomes.ready(done.state)
    assertEquals(after.game.current.title.holder, Some(leader))
    // Each started with 2: the cultist took 1 from the new Oathkeeper.
    assertEquals(Look(after).favor(cultist), 3)
    assertEquals(Look(after).favor(leader), 1)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("took", s"${cultist.value} took 1 favor from " +
        s"${leader.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), after)

  test("the holder taking the title takes nothing"):
    val done = accepted(travel(staged(Vector(cultist)).ready))
    val after = Outcomes.ready(done.state)
    assertEquals(after.game.current.title.holder, Some(cultist))
    // The cultist keeps the 2 favor they started with.
    assertEquals(Look(after).favor(cultist), 2)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a new Oathkeeper with no favor gives nothing and nothing is written"):
    val ready = staged(Vector(leader)).favor(leader, 0).ready
    val done = accepted(travel(ready))
    // The cultist keeps the 2 favor they started with.
    assertEquals(Look(done.state).favor(cultist), 2)
    assert(!done.events.exists {
      case step: WalkerStepRecorded => step.ops.exists(_.isInstanceOf[Take])
      case _ => false
    })
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a facedown Chaos Cult takes nothing"):
    val done = accepted(travel(staged(Vector(leader), facedown = true).ready))
    // The cultist keeps the 2 favor they started with.
    assertEquals(Look(done.state).favor(cultist), 2)

  test("after a tie the chosen Oathkeeper is taken from, once answered"):
    val ready = staged(Vector(active, leader), holder = Some(cultist)).ready
    val parked = accepted(travel(ready))
    val done = accepted(rules.resolveWalker(parked.state, cultist,
      oathdigital.gameplay.oathkeeper.OathkeeperProcedure.recipientDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(leader))))
    val after = Outcomes.ready(done.state)
    assertEquals(after.game.current.title.holder, Some(leader))
    // The cultist's 2, plus 1 taken from the chosen Oathkeeper.
    assertEquals(Look(after).favor(cultist), 3)
    assertEquals(
      ActPowerHarness.replayed(rules, ready, parked.events ++ done.events),
      after)
