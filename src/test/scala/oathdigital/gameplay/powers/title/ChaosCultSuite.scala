package oathdigital.gameplay.powers.title

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.powers.{NoteText, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ChaosCultSuite extends munit.FunSuite:
  import TargetsFixture.{giveAdviser, updatePlayer}

  private val power = ChaosCult.forCatalog(catalog).get
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(power)))
  private val active = base.game.current.turn.activePlayer
  private val cultist = players.find(_ != active).get
  private val leader = players.find(p => p != active && p != cultist).get

  private def favor(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player).get.board.favor

  /** `cultist` holds Chaos Cult (faceup unless told otherwise); `owners`
    * rule the sites; every player holds 2 favor. */
  private def staged(owners: Vector[Option[PlayerId]],
      orientation: Orientation = Orientation.FaceUp,
      holder: Option[PlayerId] = None): ReadyGame =
    val ruled0 = inPhase(ruled(base, owners, holder = holder), Phase.Act)
    players.foldLeft(giveAdviser(ruled0, cultist, power.cardId, orientation))(
      (ready, p) => updatePlayer(ready, p)(s => s.copy(board =
        s.board.copy(favor = 2))))

  private def travel(ready: ReadyGame) =
    val pawn = ready.game.current.players.find(_.player == active).get.pawnSite.get
    val to = ready.game.current.map.inPlay.find(_ != pawn).get
    rules.startWalker(Ready(ready), ActionRef.Travel, active, Vector.empty,
      Vector(DecisionOptionRef.Site(to)))

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  test("Chaos Cult is in the default walker catalog and automatic"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
    assertEquals(power.resolution, PowerResolution.Automatic)

  test("when another player takes the title, the holder takes 1 favor from them"):
    val ready = staged(Vector(Some(leader)))
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
    val done = travel(staged(Vector(Some(cultist)))).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(cultist))
    assertEquals(favor(after, cultist), 2)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a new Oathkeeper with no favor gives nothing and nothing is written"):
    val ready = updatePlayer(staged(Vector(Some(leader))), leader)(s =>
      s.copy(board = s.board.copy(favor = 0)))
    val done = travel(ready).toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)
    assert(!done.events.exists {
      case step: WalkerStepRecorded => step.ops.exists(_.isInstanceOf[Take])
      case _ => false
    })
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events), Vector.empty)

  test("a facedown Chaos Cult takes nothing"):
    val done = travel(staged(Vector(Some(leader)), Orientation.FaceDown))
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(favor(after, cultist), 2)

  test("after a tie the chosen Oathkeeper is taken from, once answered"):
    val ready = staged(Vector(Some(active), Some(leader)), holder = Some(cultist))
    val parked = travel(ready).toOption.get
    val done = rules.resolveWalker(parked.state, cultist,
      oathdigital.gameplay.oathkeeper.OathkeeperProcedure.recipientDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(leader)))
      .toOption.get
    val Ready(after) = done.state: @unchecked
    assertEquals(after.game.current.title.holder, Some(leader))
    assertEquals(favor(after, cultist), 3)
    replays(ready, parked.events ++ done.events, done.state)
