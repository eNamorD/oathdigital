package oathdigital.testkit

import oathdigital.gameplay.operations._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.TestGameFixtures._
import oathdigital.testkit.Outcomes.recorded
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.WalkerStubs.{continueOption, stopOption}

/** The synthetic trees, leaves and state the ProcedureWalker suites walk.
  * The state is TestGameFixtures' game with S3 cleared of its Bandit, so
  * red's pawn may move there from S1.
  */
object ProcedureWalkerFixture:
  val actor: PlayerId = playerId

  /** Every walk entry point states its power source explicitly -- there is
    * no default, so a suite can never silently walk unpowered while
    * production walks with powers.
    */
  val noPowers: WalkerPowers = WalkerPowers.empty

  val decide: Decide = Decide(
    decisionId = "recover.choice",
    owner = actor,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(continueOption, "Continue"),
      DecisionOption.Button(stopOption, "Stop"))))

  /** Legal ready state: supply full, pawn on S1; S3 cleared so a pawn Move
    * there is unambiguously legal (mirrors the OperationExecutorSuite fixture).
    */
  val ready: ReadyGame =
    Table.from(ReadyGames.of(game)).bandits(sites(2), 0).ready

  val movePawnToS3: Move = Move(Piece.Pawn(actor),
    PositionedLocation(Location.Site(sites.head)),
    PositionedLocation(Location.Site(sites(2))))
  val spendOneSupply: SpendSupply = SpendSupply(actor, 1)

  def pawnSiteOf(state: ReadyGame): Option[SiteId] =
    Look(state).player(actor).pawnSite

  def supplyOf(state: ReadyGame): Int = Look(state).supply(actor)

  /** Applies a recorded step's ops from `state` (the caller's replay of the
    * journal, mirroring how the app layer rebuilds state after a park).
    */
  def applyEvents(state: ReadyGame, events: Vector[OathEvent])(
      using munit.Location): ReadyGame =
    events.foldLeft(state) { (current, event) =>
      accepted(OperationPipeline.run(current, RecordedOps.of(recorded(event)),
        OperationPolicy.Permissive, Vector.empty)(Right(_))).state
    }

  /** The pool a Recover defense roll fills, and that roll. */
  val recoverPool: PoolKey = PoolKey("recover")
  val defenseRoll: Roll = Roll(recoverPool, DiceSpec(DiceKind.Defense))
