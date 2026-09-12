package oathdigital.gameplay

import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{Location, Move, Piece, Sequence,
  RecordPowerUse, Take}
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** Take Wealth on the generic walker (batch 1, Task 7).
  *
  * This is the first registered action that runs in a phase other than Act,
  * which is the point of porting it: a completed Wake action returns the
  * player to their Wake phase and does not run the Act action boundary. Those
  * two facts are asserted here, not inferred.
  */
class TakeWealthProcedureSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val powers: WalkerPowers = WalkerPowerCatalog.default(catalog)
  private def rules = new OathRules(catalog, walkerPowerCatalog = powers)

  private val favorArg = Vector[DecisionOptionRef](
    DecisionOptionRef.Button("favor"))
  private val secretArg = Vector[DecisionOptionRef](
    DecisionOptionRef.Button("secret"))

  /** The active player's pawn site holds `favor`/`secrets`; `sharedEnemy`
    * parks another player's pawn on it. Mirrors `WakeSuite`'s board so the
    * gates this suite asserts are the gates the legacy path asserted.
    */
  private def wake(favor: Int = 1, secrets: Int = 1,
      sharedEnemy: Boolean = false): ReadyGame = {
    val Ready(value) = execute(setup)._1: @unchecked
    val current = value.game.current
    val actor = current.turn.activePlayer
    val site = current.players.find(_.player == actor).flatMap(_.pawnSite).get
    val players = current.players.map { player =>
      if (sharedEnemy && player.player != actor) player.copy(
        pawnSite = Some(site))
      else player
    }
    value.copy(game = value.game.copy(current = current.copy(
      players = players,
      map = current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(tokens = Tokens(favor, secrets)))))))
  }

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
    rules.startWalker(Ready(ready), ActionRef.TakeWealth, actor(ready),
      Vector.empty, args)

  private def accepted(ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg): OathTransition =
    take(ready, args).fold(error => fail(s"take rejected: $error"), identity)

  private def after(transition: OathTransition): ReadyGame =
    transition.state match {
      case Ready(value) => value
      case other => fail(s"expected Ready, got $other")
    }

  test("a take moves the favor, records its use and stays in Wake") {
    val ready = wake()
    val site = pawnSite(ready)
    val before = board(ready).favor
    val transition = accepted(ready)
    val state = after(transition)

    assertEquals(board(state).favor, before + 1)
    assertEquals(state.game.current.map.sites(site).tokens.favor, 0)
    assert(state.game.current.turn.usedPowers.contains(
      TakeWealthLimit.useRef(site)))
    assertEquals(state.game.current.turn.phase, Phase.Wake)
    assertEquals(transition.continue, AwaitingWakeAction(actor(ready)))
    assertEquals(transition.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerStepRecorded", "WalkerCompleted"))
    assertEquals(state.game.current.walkerPending, None)
    assertEquals(state.game.current.walkerAction, None)
  }

  test("a take moves a face-up secret") {
    val ready = wake()
    val before = board(ready).faceUpSecrets
    val state = after(accepted(ready, secretArg))
    assertEquals(board(state).faceUpSecrets, before + 1)
    assertEquals(state.game.current.map.sites(pawnSite(ready)).tokens.secrets, 0)
  }

  test("a completed Wake action does not run the Act action boundary") {
    // Bandit refill is the boundary's most visible half, so the board is set
    // up to make it fire: an in-play site with capacity and no forces. The
    // first assertion proves the boundary WOULD have something to say here,
    // which is what stops the second from passing vacuously.
    val base = wake()
    val empty = base.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(empty,
          base.game.current.map.sites(empty).copy(
            forces = SiteForces.Empty))))))

    assert(StateBasedEvaluation.banditRefill(catalog, Ready(ready))
      .toOption.flatten.nonEmpty,
      "precondition: the Act boundary would refill bandits in this state")
    assertEquals(accepted(ready).events.collect {
      case event: BanditsRefilled => event
    }, Vector.empty)
  }

  test("a second take at the same site this turn is blocked") {
    val ready = wake(favor = 2)
    val once = after(accepted(ready))
    assert(take(once).left.toOption.get.isInstanceOf[PowerAlreadyUsed],
      "the once-per-turn restriction must reject the second take")
  }

  test("a take outside the Wake phase is rejected") {
    val ready = wake()
    val inAct = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(turn = ready.game.current.turn.copy(
        phase = Phase.Act))))
    assertEquals(take(inAct).left.toOption.get,
      WrongPhase(Phase.Wake, Phase.Act): OathViolation)
  }

  test("an enemy pawn at the site blocks the take") {
    assert(take(wake(sharedEnemy = true)).left.toOption.get
      .isInstanceOf[EnemyPawnBlocksTakeWealth])
  }

  test("the requested resource must be present at the site") {
    val ready = wake(favor = 0)
    assertEquals(take(ready).left.toOption.get,
      ResourceUnavailable(pawnSite(ready), WakeResource.Favor): OathViolation)
  }

  test("the start selection must name exactly one known resource") {
    val ready = wake()
    Vector(
      Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Site(pawnSite(ready))),
      favorArg ++ secretArg,
      Vector[DecisionOptionRef](DecisionOptionRef.Button("warbands"))
    ).foreach { args =>
      assert(take(ready, args).isLeft,
        s"start selection $args must be rejected")
    }
  }

  test("what the projection offers is what the command accepts") {
    // Ported from `WakeSuite`, and extended with the case the legacy pair
    // could not drift on but this one could: the projector now answers by
    // dry-running the declared tree, so a site already taken from this turn
    // has to disappear from the offer because its restriction rejects the
    // simulation, not because a second copy of the rule says so.
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
  }

  test("the declared tree is the take and its use record under one window") {
    val ready = wake()
    val site = pawnSite(ready)
    assertEquals(TakeWealthProcedure.build(catalog, ready, actor(ready),
      favorArg), Right(Sequence(Vector(
        Take(Piece.Favor(1), actor(ready), Location.Site(site),
          Location.PlayArea(actor(ready))),
        RecordPowerUse(TakeWealthLimit.useRef(site))),
      Some(PowerWindow.WakeTakeWealth))))
  }

  test("the recorded operations are the resource move and the use record") {
    val ready = wake()
    val site = pawnSite(ready)
    val recorded = accepted(ready).events.collect {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded => step.ops
    }.flatten
    assertEquals(recorded, Vector(
      Move(Piece.Favor(1), operations.PositionedLocation(Location.Site(site)),
        operations.PositionedLocation(Location.PlayArea(actor(ready)))),
      RecordPowerUse(TakeWealthLimit.useRef(site))))
  }
}
