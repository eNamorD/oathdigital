package oathdigital.gameplay

import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, TakeWealthFixture,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Take Wealth on the generic walker (batch 1, Tasks 7-8).
  *
  * This is the first registered action that runs in a phase other than Act,
  * which is the point of porting it: a completed Take Wealth runs the action
  * boundary, because the boundary follows the completed procedure's family
  * (Task 8), not the phase it ran in. Take Wealth is once per site per turn,
  * so on a board with no other Wake option the take is Wake's last choice,
  * and Wake ends in the same command.
  */
class TakeWealthProcedureSuite extends munit.FunSuite:
  private val powers: WalkerPowers = WalkerPowerCatalog.default(catalog)
  private def rules = new OathRules(catalog, walkerPowerCatalog = powers)
  private val parked = new ParkedDecisionAssertions(catalog, powers)

  private val favorArg = TakeWealthFixture.favorArg
  private val secretArg = TakeWealthFixture.secretArg
  private val actor = p1
  // p1's site on the quiet table.
  private val home = CatalogNames.site("Ancient City")

  /** p1's Wake on the quiet table, Ancient City holding `favor` and
    * `secrets`; with `sharedEnemy`, p2 and p3 stand there too. */
  private def wake(favor: Int, secrets: Int,
      sharedEnemy: Boolean = false): ReadyGame =
    TakeWealthFixture.wakeReady(Table.start.turn(actor, Phase.Wake).ready,
      favor = favor, secrets = secrets, sharedEnemy = sharedEnemy)

  private def board(ready: ReadyGame): PlayerBoardState =
    Look(ready).player(actor).board

  private def take(ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg)
      : Either[OathViolation, OathTransition] =
    TakeWealthFixture.take(rules, ready, args)

  private def took(ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg): OathTransition =
    accepted(take(ready, args))

  /** A stocked site, an empty favor, an empty secret, an enemy pawn, and
    * `taken`, a site already taken from this turn. */
  private def oneBoardPerTakeGate(taken: ReadyGame): Vector[ReadyGame] = Vector(
    wake(favor = 1, secrets = 1), wake(favor = 0, secrets = 1),
    wake(favor = 1, secrets = 0),
    wake(favor = 1, secrets = 1, sharedEnemy = true), taken)

  private def after(transition: OathTransition): ReadyGame =
    Outcomes.ready(transition.state)

  test("a take moves the favor, records its use and ends a Wake with nothing left"):
    val ready = wake(favor = 1, secrets = 1)
    val before = board(ready).favor
    val transition = took(ready)
    val state = after(transition)

    assertEquals(board(state).favor, before + 1)
    assertEquals(state.game.current.map.sites(home).tokens.favor, 0)
    assert(state.game.current.turn.usedPowers.contains(
      TakeWealthLimit.useRef(home)))
    parked.assertResumed(transition.state, Phase.Act, actor)
    assertEquals(transition.events.collect {
      case oathdigital.gameplay.walker.WalkerCompleted(procedure) => procedure
    }, Vector(ActionRef.TakeWealth, PhaseTransitionRef.EndWake))
    assertEquals(state.game.current.walkerPending, None)
    assertEquals(state.game.current.walkerProcedure, None)

  test("a take moves a face-up secret"):
    val ready = wake(favor = 1, secrets = 1)
    val before = board(ready).faceUpSecrets
    val state = after(took(ready, secretArg))
    assertEquals(board(state).faceUpSecrets, before + 1)
    assertEquals(state.game.current.map.sites(home).tokens.secrets, 0)

  test("a completed Take Wealth runs the action boundary before Wake ends"):
    // An empty site with capacity makes the boundary observable; the
    // precondition proves it has something to do here.
    // Ancient City, capacity 3, holds no bandits.
    val ready = Table.from(wake(favor = 1, secrets = 1))
      .bandits("Ancient City", 0).unchecked
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(ready))
      .toOption.flatten.nonEmpty,
      "precondition: the boundary would refill bandits in this state")
    val result = took(ready)
    val refilled = result.events.indexWhere(_.isInstanceOf[BanditsRefilled])
    assertEquals(result.events.collect { case event: BanditsRefilled => event }.size, 1)
    assert(refilled >= 0 && refilled < result.events.indexWhere {
      case oathdigital.gameplay.walker.WalkerCompleted(procedure) =>
        procedure == PhaseTransitionRef.EndWake
      case _ => false
    }, result.events.toString)
    parked.assertResumed(result.state, Phase.Act, actor)

  test("a second take at the same site this turn is refused by the walker"):
    val ready = wake(favor = 2, secrets = 1)
    // The take ends Wake; put the turn back in Wake to try the site again.
    val tookOnce = after(took(ready))
    val once = Table.from(tookOnce).backInPhase(Phase.Wake).unchecked
    assertEquals(take(once).left.toOption,
      Some(PowerAlreadyUsed(TakeWealthLimit.useRef(home))))

  test("a take outside the Wake phase is rejected"):
    val ready = wake(favor = 1, secrets = 1)
    val inAct = Table.from(ready).turn(actor, Phase.Act).unchecked
    assertEquals(rejected(take(inAct)),
      WrongPhase(Phase.Wake, Phase.Act): OathViolation)

  test("an enemy pawn at the site blocks the take"):
    assertEquals(take(wake(favor = 1, secrets = 1, sharedEnemy = true))
      .left.toOption, Some(EnemyPawnBlocksTakeWealth(home, Vector(p2, p3))))

  test("the requested resource must be present at the site"):
    val ready = wake(favor = 0, secrets = 1)
    assertEquals(rejected(take(ready)),
      ResourceUnavailable(home, WakeResource.Favor): OathViolation)

  test("the start selection must name exactly one known resource"):
    val ready = wake(favor = 1, secrets = 1)
    assertEquals(Vector(
      Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Site(home)),
      favorArg ++ secretArg,
      Vector[DecisionOptionRef](DecisionOptionRef.Button("warbands"))
    ).map(args => take(ready, args).left.toOption), Vector(
      "take wealth requires favor or secret as its start selection",
      "take wealth takes exactly one resource as its start selection, got " +
        "site/site:ancient-city",
      "take wealth takes exactly one resource as its start selection, got " +
        "button/favor, button/secret",
      "take wealth does not recognise the resource 'warbands'")
      .map(detail => Some(InvalidEventOrder(detail))))

  test("the start selection spelling is one definition in both directions"):
    // The literals here are the wire spelling a client must send. `selection`
    // is what the projection and any future preview build from, `resourceOf`
    // is what the command reads, and both now read `WakeResource.key` -- so
    // this is where that key is pinned to the wire, once.
    assertEquals(TakeWealthProcedure.selection(WakeResource.Favor), favorArg)
    assertEquals(TakeWealthProcedure.selection(WakeResource.Secret), secretArg)
    WakeResource.all.foreach { resource =>
      assertEquals(WakeResource.fromKey(resource.key), Some(resource))
    }

  test("the candidates are exactly the resources the command accepts"):
    // `candidates` is the gameplay-owned answer the projector consumes, so
    // it is proved against the command directly rather than only through the
    // projection below.
    val taken = after(took(wake(favor = 2, secrets = 1)))
    oneBoardPerTakeGate(taken).foreach { ready =>
      assertEquals(TakeWealthProcedure.candidates(catalog, ready,
        actor, powers),
        WakeResource.all.filter(resource => take(ready,
          TakeWealthProcedure.selection(resource)).isRight))
    }

  test("what the projection offers is what the command accepts"):
    // Ported from `WakeSuite`, and extended with the case the legacy pair
    // could not drift on but this one could: the offer is now the procedure's
    // own `candidates`, so a site already taken from this turn disappears
    // because its restriction rejects the simulation, not because a second
    // copy of the rule says so. What this adds over the candidates test above
    // is the projector's half -- that it consumes them and names them as the
    // controls a client binds.
    val taken = after(took(wake(favor = 2, secrets = 1)))
    oneBoardPerTakeGate(taken).foreach { ready =>
      val projection = new oathdigital.application.GameProjector(catalog)
        .project("take-wealth", oathdigital.application.LoadedGame(
          Ready(ready), 9), actor)
      assertEquals(projection.legalControls.contains("takeFavor"),
        take(ready).isRight, "takeFavor")
      assertEquals(projection.legalControls.contains("takeSecret"),
        take(ready, secretArg).isRight, "takeSecret")
    }

  test("the declared tree is the take and its use record under one window"):
    val ready = wake(favor = 1, secrets = 1)
    assertEquals(TakeWealthProcedure.build(catalog, ready, actor,
      favorArg), Right(Sequence(Vector(
        Take(Piece.Favor(1), actor, Location.Site(home),
          Location.PlayArea(actor)),
        RecordPowerUse(TakeWealthLimit.useRef(home))),
      Some(PowerWindow.WakeTakeWealth))))

  test("the recorded operations are the resource move and the use record"):
    val ready = wake(favor = 1, secrets = 1)
    val recorded = took(ready).events.takeWhile {
      case _: oathdigital.gameplay.walker.WalkerCompleted => false
      case _ => true
    }.collect {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded => step.ops
    }.flatten
    assertEquals(recorded, Vector(
      Move(Piece.Favor(1), PositionedLocation(Location.Site(home)),
        PositionedLocation(Location.PlayArea(actor))),
      RecordPowerUse(TakeWealthLimit.useRef(home))))
