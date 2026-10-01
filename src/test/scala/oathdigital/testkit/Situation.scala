package oathdigital.testkit

import oathdigital.application.{CampaignDicePort, DefenseDicePort,
  EventStreamRepository, GameCommand, GameApplicationService,
  InMemoryEventStreamRepository}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.serialization.GameEventWire

/** One thing a situation is reached by. */
enum Step:
  case Command(command: GameCommand)
  /** Places pieces by recording one arranging `WalkerStepRecorded` and
    * applying it through the replay path, the way a journal would replay it.
    * For arranging a board real play would take too long to reach, never
    * for a rule under test.
    */
  case Arrange(ops: Vector[CoreOperation], label: String = "arrange")

object Step:
  /** A command is a step as it stands; tests list either. */
  def of(step: GameCommand | Step): Step = step match
    case command: GameCommand => Command(command)
    case step: Step => step

/** What an answer policy sees: the parked `Decide`, the state it parks on,
  * the player it awaits and the procedure it belongs to.
  */
final case class Park(decide: Decide, ready: ReadyGame, awaiting: PlayerId,
    procedure: ProcedureRef):
  def decisionId: String = decide.decisionId

/** How a parked decision is answered: by the test where it names one, by
  * [[Situation.defaultAnswer]] otherwise. */
type Answers = PartialFunction[Park, DecisionAnswer]

/** A game reached by real play from a first-game input or from a [[Table]]:
  * every command issued and every parked decision answered along the way, in
  * order (`CONTEXT.md`).
  *
  * `driver` is the adapter that reached it, so a test keeps driving from
  * where the situation left off. `nextSequence` is the journal sequence the
  * next event takes: the number of events for the rules adapter, the
  * stream's next sequence for the journaled one.
  */
final case class Situation(state: OathState, events: Vector[OathEvent],
    nextSequence: Long, driver: SituationDriver):

  def ready(using munit.Location): ReadyGame = state match
    case Ready(ready) => ready
    case other => munit.Assertions.fail(s"expected a ready game, got $other")

  /** Ends `player`'s Wake, unless it has already ended by itself for having
    * nothing to decide. */
  def endingWake(player: PlayerId)(using munit.Location): Situation =
    if ready.game.current.turn.phase == Phase.Wake then
      after(GameCommand.EndWake(player))
    else this

  /** Issues each step and answers every parked decision it causes, the last
    * step's included: the result is not parked. With no steps, answers the
    * park this situation holds. */
  def after(steps: (GameCommand | Step)*)(using munit.Location): Situation =
    driver.run(this, steps.map(Step.of), settle = true)

  /** Issues each step, answering every park but the last step's own: the
    * result is parked where that step stopped, for `ParkedDecisionAssertions`
    * to read. */
  def parkedAfter(steps: (GameCommand | Step)*)(using munit.Location)
      : Situation =
    driver.run(this, steps.map(Step.of), settle = false)

  /** Continue under `overrides` layered over [[Situation.defaultAnswer]],
    * replacing any overrides given earlier. */
  def withAnswers(overrides: Answers): Situation =
    copy(driver = driver.withAnswers(overrides))

  /** Writes this situation's events into `repository` as `gameId`'s whole
    * stream, the way a journal written by real play would hold them. */
  def seedInto(repository: InMemoryEventStreamRepository, gameId: String)(
      using munit.Location): Unit =
    repository.seed(gameId, events.zipWithIndex.map { case (event, index) =>
      ujson.write(GameEventWire.encodeEvent(gameId, index.toLong, event).fold(error => munit.Assertions.fail(
          s"event $index ($event) does not encode: $error"), identity))
    })

object Situation:
  /** The answer a park gets when the test names none. `ChooseOne`: the first
    * option. `Partition`: the first option kept in the first section that
    * demands one, every other option in another section -- the "keep one,
    * discard the rest" shape of Setup's adviser choice. `ChooseMany`: the
    * first `min` options. `ChooseAmount`: `min`. Anything else is not
    * answered by default and fails the walk naming the decision.
    */
  val defaultAnswer: Answers =
    case Park(Decide(_, _, DecisionQuery.ChooseOne(options, _), _, _, _), _, _, _)
        if options.nonEmpty =>
      DecisionAnswer.ChooseOneAnswer(options.head.ref)
    case Park(Decide(_, _, DecisionQuery.Partition(sections, options, _, _),
        _, _, _), _, _, _) if sections.nonEmpty && options.nonEmpty =>
      val keep = sections.find(_.minRequired > 0).getOrElse(sections.head)
      val discard = sections.find(_.key != keep.key).getOrElse(keep)
      val refs = options.map(_.ref)
      DecisionAnswer.PartitionAnswer(DecisionPlacement(refs.head, keep.key) +:
        refs.tail.map(DecisionPlacement(_, discard.key)))
    case Park(Decide(_, _, DecisionQuery.ChooseMany(min, _, options, _), _, _, _),
        _, _, _) if options.size >= min =>
      DecisionAnswer.ChooseManyAnswer(options.take(min).map(_.ref))
    case Park(Decide(_, _, DecisionQuery.ChooseAmount(min, _, _, _, _), _, _, _),
        _, _, _) =>
      DecisionAnswer.ChooseAmountAnswer(min)

  /** Setup's pawn placements at `sites`, in placement order: the n-th pawn
    * placed goes to `sites(n)`, whoever places it. The turn order stays the
    * walker's to know. */
  def pawnsAt(sites: Vector[SiteId]): Answers =
    case park if park.decisionId ==
        SetupProcedure.pawnDecisionId(park.awaiting) =>
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(sites(
        park.ready.game.current.players.count(_.pawnSite.nonEmpty))))

  /** Drives `OathRules` directly: nothing is journaled. The rules are built
    * from the ports and power catalogs given, with `OathRules`' own defaults,
    * and the same power catalogs rebuild each park. */
  def rules(catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers = WalkerPowers.empty,
      phasePowers: PhasePowers = PhasePowers.empty,
      walkerDice: WalkerDice = WalkerDice.unavailable,
      defenseDice: DefenseDicePort = DefenseDicePort.random,
      warExhaustion: WarExhaustionRandomPort = WarExhaustionRandomPort.random)
      : SituationDriver =
    SituationDriver.Rules(catalog, walkerPowers, phasePowers, walkerDice,
      defenseDice, warExhaustion, defaultAnswer)

  /** The rules adapter over the rules `GameApplicationService` builds
    * (`GameApplicationService.scala:85-90`), so it agrees with the journaled
    * adapter given the same input and ports. */
  def serviceRules(catalog: ExecutableCatalog,
      campaignDice: CampaignDicePort = CampaignDicePort.random,
      defenseDice: DefenseDicePort = DefenseDicePort.random,
      warExhaustion: WarExhaustionRandomPort = WarExhaustionRandomPort.random)
      : SituationDriver =
    rules(catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog),
      CampaignDicePort.walkerDice(campaignDice), defenseDice, warExhaustion)

  /** Drives `service` as `gameId`, which must hold no stream yet, and
    * arranges through `repository`, which must be the service's own.
    * `catalog` must be the service's: the parks are rebuilt against it. */
  def journaled(service: GameApplicationService, catalog: ExecutableCatalog,
      repository: EventStreamRepository, gameId: String): SituationDriver =
    SituationDriver.Journaled(service, catalog, Some(repository), gameId,
      defaultAnswer)

  /** As above, for a caller that does not hold the service's repository: an
    * `Arrange` step is rejected. */
  def journaled(service: GameApplicationService, catalog: ExecutableCatalog,
      gameId: String): SituationDriver =
    SituationDriver.Journaled(service, catalog, None, gameId, defaultAnswer)

  /** No game yet: where every situation starts. */
  def start(driver: SituationDriver): Situation =
    Situation(OathState.NoGame, Vector.empty, 0L, driver)

  /** Setup complete: the first player's turn. It is in Wake when Wake has
    * an option; a Wake with nothing to decide has already ended, in Act. */
  def wake(driver: SituationDriver,
      chronicle: Chronicle = FirstGameSetupFixture.chronicle,
      orders: SetupOrders = FirstGameSetupFixture.orders)(
      using munit.Location): Situation =
    start(driver).after(GameCommand.Begin(chronicle, orders))

  /** `actor`'s Wake ended: the first player's Act. */
  def act(driver: SituationDriver, actor: PlayerId,
      chronicle: Chronicle = FirstGameSetupFixture.chronicle,
      orders: SetupOrders = FirstGameSetupFixture.orders)(
      using munit.Location): Situation =
    wake(driver, chronicle, orders).endingWake(actor)

  /** `actor`'s Act ended, stopped at the Rest action. */
  def rest(driver: SituationDriver, actor: PlayerId,
      chronicle: Chronicle = FirstGameSetupFixture.chronicle,
      orders: SetupOrders = FirstGameSetupFixture.orders)(
      using munit.Location): Situation =
    act(driver, actor, chronicle, orders).after(GameCommand.BeginRest(actor))
