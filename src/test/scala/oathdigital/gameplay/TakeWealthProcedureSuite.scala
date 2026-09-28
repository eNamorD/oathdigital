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
import oathdigital.testkit.Table

/** Take Wealth's board setup and command, extracted from
  * `TakeWealthProcedureSuite` (batch 1, Task 8) so
  * `OathkeeperProcedureSuite` can drive the same Wake board its park test
  * needs, without a second definition of "a Wake board with a takeable
  * resource" drifting from this one.
  */
object TakeWealthFixture extends munit.Assertions:
  val favorArg: Vector[DecisionOptionRef] = Vector(
    DecisionOptionRef.Button("favor"))
  val secretArg: Vector[DecisionOptionRef] = Vector(
    DecisionOptionRef.Button("secret"))

  /** p1's Wake on the quiet table. */
  private def freshBase: ReadyGame =
    Table.start.turn(Table.p1, Phase.Wake).ready

  /** The active player's pawn site holds `favor`/`secrets`; `sharedEnemy`
    * parks another player's pawn on it. Mirrors `WakeSuite`'s board so the
    * gates this suite asserts are the gates the legacy path asserted.
    *
    * `from` is the game to start from, defaulting to a fresh first-game
    * setup -- `OathkeeperProcedureSuite`'s park test instead starts from a
    * game already `ruled` for a tie.
    */
  def wakeReady(from: ReadyGame = freshBase, favor: Int = 1, secrets: Int = 1,
      sharedEnemy: Boolean = false): ReadyGame =
    val current = from.game.current
    val actor = current.turn.activePlayer
    val site = current.players.find(_.player == actor).flatMap(_.pawnSite).get
    val players = current.players.map { player =>
      if sharedEnemy && player.player != actor then player.copy(
        pawnSite = Some(site))
      else player
    }
    from.updateCurrent(_.copy(
      players = players,
      map = current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(tokens = Tokens(favor, secrets))))))

  private def actor(ready: ReadyGame): PlayerId =
    ready.game.current.turn.activePlayer

  def take(rules: OathRules, ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.TakeWealth, actor(ready),
      Vector.empty, args)

  def accepted(rules: OathRules, ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg): OathTransition =
    take(rules, ready, args).fold(error => fail(s"take rejected: $error"),
      identity)

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

  private def wake(favor: Int = 1, secrets: Int = 1,
      sharedEnemy: Boolean = false): ReadyGame =
    TakeWealthFixture.wakeReady(favor = favor, secrets = secrets,
      sharedEnemy = sharedEnemy)

  private def actor(ready: ReadyGame): PlayerId =
    ready.game.current.turn.activePlayer
  private def pawnSite(ready: ReadyGame): SiteId =
    ready.game.current.players.find(_.player == actor(ready))
      .flatMap(_.pawnSite).get
  private def board(ready: ReadyGame): PlayerBoardState =
    ready.game.current.players.find(_.player == actor(ready)).get.board

  private def take(ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg)
      : Either[OathViolation, OathTransition] =
    TakeWealthFixture.take(rules, ready, args)

  private def accepted(ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg): OathTransition =
    TakeWealthFixture.accepted(rules, ready, args)

  private def after(transition: OathTransition): ReadyGame =
    transition.state match
      case Ready(value) => value
      case other => fail(s"expected Ready, got $other")

  test("a take moves the favor, records its use and ends a Wake with nothing left"):
    val ready = wake()
    val site = pawnSite(ready)
    val before = board(ready).favor
    val transition = accepted(ready)
    val state = after(transition)

    assertEquals(board(state).favor, before + 1)
    assertEquals(state.game.current.map.sites(site).tokens.favor, 0)
    assert(state.game.current.turn.usedPowers.contains(
      TakeWealthLimit.useRef(site)))
    parked.assertResumed(transition.state, Phase.Act, actor(ready))
    assertEquals(transition.events.collect {
      case oathdigital.gameplay.walker.WalkerCompleted(procedure) => procedure
    }, Vector(ActionRef.TakeWealth, PhaseTransitionRef.EndWake))
    assertEquals(state.game.current.walkerPending, None)
    assertEquals(state.game.current.walkerProcedure, None)

  test("a take moves a face-up secret"):
    val ready = wake()
    val before = board(ready).faceUpSecrets
    val state = after(accepted(ready, secretArg))
    assertEquals(board(state).faceUpSecrets, before + 1)
    assertEquals(state.game.current.map.sites(pawnSite(ready)).tokens.secrets, 0)

  test("a completed Take Wealth runs the action boundary before Wake ends"):
    // An empty site with capacity makes the boundary observable; the
    // precondition proves it has something to do here.
    val base = wake()
    val empty = base.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val ready = base.updateCurrent(_.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(empty,
          base.game.current.map.sites(empty).copy(
            forces = SiteForces.Empty)))))
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(ready))
      .toOption.flatten.nonEmpty,
      "precondition: the boundary would refill bandits in this state")
    val result = accepted(ready)
    val refilled = result.events.indexWhere(_.isInstanceOf[BanditsRefilled])
    assertEquals(result.events.collect { case event: BanditsRefilled => event }.size, 1)
    assert(refilled >= 0 && refilled < result.events.indexWhere {
      case oathdigital.gameplay.walker.WalkerCompleted(procedure) =>
        procedure == PhaseTransitionRef.EndWake
      case _ => false
    }, result.events.toString)
    parked.assertResumed(result.state, Phase.Act,
      ready.game.current.turn.activePlayer)

  test("a second take at the same site this turn is refused by the walker"):
    val ready = wake(favor = 2)
    // The take ends Wake; put the turn back in Wake to try the site again.
    val took = after(accepted(ready))
    val once = took.updateCurrent(current => current.copy(turn =
      current.turn.copy(phase = Phase.Wake)))
    val site = once.game.current.players.find(_.player == actor(once))
      .flatMap(_.pawnSite).get
    assertEquals(take(once).left.toOption,
      Some(PowerAlreadyUsed(TakeWealthLimit.useRef(site))))

  test("a take outside the Wake phase is rejected"):
    val ready = wake()
    val inAct = ready.updateCurrent(_.copy(turn = ready.game.current.turn.copy(
        phase = Phase.Act)))
    assertEquals(take(inAct).left.toOption.get,
      WrongPhase(Phase.Wake, Phase.Act): OathViolation)

  test("an enemy pawn at the site blocks the take"):
    assertEquals(take(wake(sharedEnemy = true)).left.toOption, Some(EnemyPawnBlocksTakeWealth(SiteId("site:ancient-city"),
      Vector(PlayerId("p2"), PlayerId("p3")))))

  test("the requested resource must be present at the site"):
    val ready = wake(favor = 0)
    assertEquals(take(ready).left.toOption.get,
      ResourceUnavailable(pawnSite(ready), WakeResource.Favor): OathViolation)

  test("the start selection must name exactly one known resource"):
    val ready = wake()
    assertEquals(Vector(
      Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Site(pawnSite(ready))),
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
    val taken = after(accepted(wake(favor = 2)))
    Vector(wake(), wake(favor = 0), wake(secrets = 0),
      wake(sharedEnemy = true), taken).foreach { ready =>
      assertEquals(TakeWealthProcedure.candidates(catalog, ready,
        actor(ready), powers),
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
    val taken = after(accepted(wake(favor = 2)))
    Vector(wake(), wake(favor = 0), wake(secrets = 0),
      wake(sharedEnemy = true), taken).foreach { ready =>
      val projection = new oathdigital.application.GameProjector(catalog)
        .project("take-wealth", oathdigital.application.LoadedGame(
          Ready(ready), 9), actor(ready))
      assertEquals(projection.legalControls.contains("takeFavor"),
        take(ready).isRight, "takeFavor")
      assertEquals(projection.legalControls.contains("takeSecret"),
        take(ready, secretArg).isRight, "takeSecret")
    }

  test("the declared tree is the take and its use record under one window"):
    val ready = wake()
    val site = pawnSite(ready)
    assertEquals(TakeWealthProcedure.build(catalog, ready, actor(ready),
      favorArg), Right(Sequence(Vector(
        Take(Piece.Favor(1), actor(ready), Location.Site(site),
          Location.PlayArea(actor(ready))),
        RecordPowerUse(TakeWealthLimit.useRef(site))),
      Some(PowerWindow.WakeTakeWealth))))

  test("the recorded operations are the resource move and the use record"):
    val ready = wake()
    val site = pawnSite(ready)
    val recorded = accepted(ready).events.takeWhile {
      case _: oathdigital.gameplay.walker.WalkerCompleted => false
      case _ => true
    }.collect {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded => step.ops
    }.flatten
    assertEquals(recorded, Vector(
      Move(Piece.Favor(1), PositionedLocation(Location.Site(site)),
        PositionedLocation(Location.PlayArea(actor(ready)))),
      RecordPowerUse(TakeWealthLimit.useRef(site))))
