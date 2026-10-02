package oathdigital.gameplay.powers.title

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class ChaosCultSuite extends munit.FunSuite:
  private val power = ChaosCult
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(power)))
  private val (active, cultist, leader) = (p1, p2, p3)

  private def favor(ready: ReadyGame, player: PlayerId): Int =
    Look(ready).favor(player)

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
      Vector(DecisionOptionRef.Site(Table.homeOf(p2))))

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  test("Chaos Cult is in the default walker catalog and automatic"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
    assertEquals(power.resolution, PowerResolution.Automatic)

  test("when another player takes the title, the holder takes 1 favor from them"):
    val ready = staged(Vector(leader)).ready
    val done = travel(ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(leader))
    assertEquals(favor(after, cultist), 3)
    assertEquals(favor(after, leader), 1)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("took", s"${cultist.value} took 1 favor from " +
        s"${leader.value}.", covers = false)))
    replays(ready, done.events, done.state)

  test("the holder taking the title takes nothing"):
    val done = travel(staged(Vector(cultist)).ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(cultist))
    assertEquals(favor(after, cultist), 2)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a new Oathkeeper with no favor gives nothing and nothing is written"):
    val ready = staged(Vector(leader)).favor(leader, 0).ready
    val done = travel(ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)
    assert(!done.events.exists {
      case step: WalkerStepRecorded => step.ops.exists(_.isInstanceOf[Take])
      case _ => false
    })
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a facedown Chaos Cult takes nothing"):
    val done = travel(staged(Vector(leader), facedown = true).ready)
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)

  test("after a tie the chosen Oathkeeper is taken from, once answered"):
    val ready = staged(Vector(active, leader), holder = Some(cultist)).ready
    val parked = travel(ready).toOption.get
    val done = rules.resolveWalker(parked.state, cultist,
      oathdigital.gameplay.oathkeeper.OathkeeperProcedure.recipientDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(leader)))
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(leader))
    assertEquals(favor(after, cultist), 3)
    replays(ready, parked.events ++ done.events, done.state)
