package oathdigital.application

import java.nio.file.{Files, Paths}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import ParkedServiceFixture.loaded
import oathdigital.testkit.Unwrap.{accepted, present, rejected}

/** The application half of the pending-walker invariant (spec, Testing).
  * Over each of the spec's four parked contexts, one instance of every
  * `GameCommand` constructor is submitted for every player and refused, and
  * the parked walker's own resume is then accepted.
  */
class PendingWalkerInvariantSuite extends munit.FunSuite:

  /** The parked decision, as this suite rebuilds it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  private val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** One instance of every `GameCommand` constructor, bound to `actor`. */
  private def everyCommand(actor: PlayerId): Vector[GameCommand] = Vector(
    GameCommand.Begin(chronicle, orders),
    GameCommand.ResolveWalker(actor, TreeDecision("not-parked",
      ChooseOneAnswer(DecisionOptionRef.Button("go")))),
    GameCommand.RollWalker(actor, PoolKey("not-parked")),
    GameCommand.Undo(actor), GameCommand.Redo(actor),
    GameCommand.SetConsentPreference(actor,
      oathdigital.gameplay.powers.travel.MinesSite.mountain,
      PlayerId("p2"), ConsentSetting.Always),
    GameCommand.RequestConsent(actor, "not-parked",
      DecisionOptionRef.Button("go")),
    GameCommand.AnswerConsent(actor, ConsentChoice.Yes))

  private def isStepCommand(command: GameCommand): Boolean = command match
    case _: GameCommand.Undo | _: GameCommand.Redo => true
    case _ => false

  test("the sample holds every GameCommand constructor"):
    val source = Files.readString(Paths.get(
      "src/main/scala/oathdigital/application/GameCommands.scala"))
    val start = source.indexOf("object GameCommand:")
    val end = "\n\\S".r.findFirstMatchIn(source.substring(start + 1))
      .fold(source.length)(start + 1 + _.start)
    val body = source.substring(start, end)
    val declared = "final case class (\\w+)".r.findAllMatchIn(body)
      .map(_.group(1)).toSet
    assertEquals(everyCommand(PlayerId("p1")).map(_.productPrefix).toSet, declared)

  /** Every constructor but Undo and Redo, for every player, is refused and
    * appends nothing. A preference is legal over a park by design (consent
    * design, ruling 18); the sample's is refused only because the Mines'
    * power cannot be waived.
    * The sample's `ResolveWalker` and `RollWalker` name no parked position,
    * so they are refused too. Then `resume`, the parked walker's own answer,
    * is accepted and appends: only a matching resume runs.
    */
  private def assertOnlyItsResume(game: ParkedServiceFixture.ParkedGame,
      gameId: String, players: Vector[PlayerId], resume: GameCommand): Unit =
    val ParkedServiceFixture.ParkedGame(service, repository, parked, _) = game
    def records = loaded(repository.load(gameId)).records
    val before = records
    for
      player <- players
      // Undo and Redo over a park are legal by design (undo design,
      // "Rulings" 4); UndoServiceSuite and RedoServiceSuite cover them.
      command <- everyCommand(player) if !isStepCommand(command)
    do
      // The refusal differs by command (a rule violation, a duplicate game,
      // a stale position); that every one is refused is the invariant.
      rejected(service.handle(gameId, parked.nextSequence, command))
    assertEquals(records, before, "a refused command must append nothing")
    // The parked walker's own answer runs.
    accepted(service.handle(gameId, parked.nextSequence, resume))
    assert(records.size > before.size, "an accepted resume appends its events")

  private val everyone = participants.map(_.playerId)

  test("over a parked Recover choice in Act, only its answer is accepted"):
    val ParkedServiceFixture.RecoverChoicePark(game, actor, players) =
      ParkedServiceFixture.recoverChoicePark("invariant-recover")
    assertOnlyItsResume(game, "invariant-recover", players,
      GameCommand.ResolveWalker(actor, TreeDecision(
        RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("stop")))))

  test("over an off-turn Oathkeeper recipient, only the holder's answer is accepted"):
    val ParkedServiceFixture.OathkeeperTiePark(game, _, holder, leaderB) =
      ParkedServiceFixture.oathkeeperTiePark("invariant-oathkeeper")
    assertOnlyItsResume(game, "invariant-oathkeeper", everyone,
      GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB)))))

  test("over an off-turn League Treaty decision in Rest, only the ruler's answer " +
      "is accepted"):
    val ParkedServiceFixture.LeagueTreatyPark(game, _, ruler) =
      ParkedServiceFixture.leagueTreatyPark("invariant-league-treaty")
    val decision = present(parkedAssertions.parkedDecision(game.accepted.state),
      "the parked decision").decision
    assertOnlyItsResume(game, "invariant-league-treaty", everyone,
      GameCommand.ResolveWalker(ruler, TreeDecision(decision,
        ChooseOneAnswer(DecisionOptionRef.Button("decline")))))

  test("over a Silver Tongue bank choice in Rest, only its answer is accepted"):
    val ParkedServiceFixture.SilverTonguePark(game, active, bank) =
      ParkedServiceFixture.silverTonguePark("invariant-silver-tongue")
    val decision = present(parkedAssertions.parkedDecision(game.accepted.state),
      "the parked decision").decision
    assertOnlyItsResume(game, "invariant-silver-tongue", everyone,
      GameCommand.ResolveWalker(active, TreeDecision(decision,
        ChooseOneAnswer(DecisionOptionRef.FavorBank(bank)))))
