package oathdigital.application

import java.nio.file.{Files, Paths}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** The application half of the pending-walker invariant (spec, Testing).
  * Over each of the spec's four parked contexts, one instance of every
  * `GameCommand` constructor is submitted for every player and refused, and
  * the parked walker's own resume is then accepted.
  */
class PendingWalkerInvariantSuite extends munit.FunSuite {
  private val relic = relics.head

  /** One instance of every `GameCommand` constructor, bound to `actor`. */
  private def everyCommand(actor: PlayerId): Vector[GameCommand] = Vector(
    GameCommand.WithModifiers(GameCommand.EndWake(actor), Vector.empty),
    GameCommand.Begin(chronicle, orders),
    GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)),
    GameCommand.ResolveWalker(actor, TreeDecision("not-parked",
      ChooseOneAnswer(DecisionOptionRef.Button("go")))),
    GameCommand.RollWalker(actor, PoolKey("not-parked")),
    GameCommand.EndWake(actor),
    GameCommand.PeekSiteRelics(actor),
    GameCommand.RevealOwnedRelic(actor, relic),
    GameCommand.MoveWarbands(actor, toSite = true, 1),
    GameCommand.BeginRest(actor),
    GameCommand.FinishRest(actor),
    GameCommand.UsePower(actor, PowerId("denizen.silver-tongue"),
      DecisionOptionRef.Denizen(DenizenId("92"))))

  test("the sample holds every GameCommand constructor") {
    val source = Files.readString(Paths.get(
      "src/main/scala/oathdigital/application/GameCommands.scala"))
    val start = source.indexOf("object GameCommand {")
    val body = source.substring(start, source.indexOf("\n}\n", start))
    val declared = "final case class (\\w+)".r.findAllMatchIn(body)
      .map(_.group(1)).toSet
    assertEquals(everyCommand(PlayerId("p1")).map(_.productPrefix).toSet, declared)
  }

  /** Every constructor, for every player, is refused and appends nothing.
    * The sample's `ResolveWalker` and `RollWalker` name no parked position,
    * so they are refused too. Then `resume`, the parked walker's own answer,
    * is accepted and appends: only a matching resume runs.
    */
  private def assertOnlyItsResume(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String,
      parked: GameAccepted, players: Vector[PlayerId],
      resume: GameCommand): Unit = {
    def records = repository.load(gameId).toOption.flatten.get.records
    val before = records
    for {
      player <- players
      command <- everyCommand(player)
    } assert(service.handle(gameId, parked.nextSequence, command).isLeft,
      s"$command by $player must be refused over a parked walker")
    assertEquals(records, before, "a refused command must append nothing")
    val resumed = service.handle(gameId, parked.nextSequence, resume)
    assert(resumed.isRight, s"the parked walker's own $resume must run: $resumed")
    assert(records.size > before.size, "an accepted resume appends its events")
  }

  private val everyone = participants.map(_.playerId)

  test("over a parked Recover choice in Act, only its answer is accepted") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = ParkedServiceFixture.failingDice)
    val (parked, actor, players) =
      ParkedServiceFixture.recoverChoicePark(service, "invariant-recover")
    assertOnlyItsResume(service, repository, "invariant-recover", parked,
      players, GameCommand.ResolveWalker(actor, TreeDecision(
        RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("stop")))))
  }

  test("over an off-turn Oathkeeper recipient, only the holder's answer is accepted") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (parked, _, holder, leaderB) = ParkedServiceFixture.oathkeeperTiePark(
      service, repository, "invariant-oathkeeper")
    assertOnlyItsResume(service, repository, "invariant-oathkeeper", parked,
      everyone, GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB)))))
  }

  test("over an off-turn League Treaty decision in Rest, only the ruler's answer " +
      "is accepted") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (parked, _, ruler) = ParkedServiceFixture.leagueTreatyPark(service,
      repository, "invariant-league-treaty")
    val OathContinue.AwaitingRestDecision(_, decision) = parked.continue: @unchecked
    assertOnlyItsResume(service, repository, "invariant-league-treaty", parked,
      everyone, GameCommand.ResolveWalker(ruler, TreeDecision(decision.value,
        ChooseOneAnswer(DecisionOptionRef.Button("decline")))))
  }

  test("over a Silver Tongue bank choice in Rest, only its answer is accepted") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (parked, active, bank) = ParkedServiceFixture.silverTonguePark(service,
      repository, "invariant-silver-tongue")
    val OathContinue.AwaitingPowerDecision(_, decision) = parked.continue: @unchecked
    assertOnlyItsResume(service, repository, "invariant-silver-tongue", parked,
      everyone, GameCommand.ResolveWalker(active, TreeDecision(decision.value,
        ChooseOneAnswer(DecisionOptionRef.FavorBank(bank)))))
  }
}
