package oathdigital.application.gamelog

import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Situation, SituationDriver}

/** A journal built by real play through `GameApplicationService` on every
  * run (spec, "Test journals"). No stored game is a fixture: a script a new
  * card changes fails at the command that no longer applies.
  */
final case class Script(name: String, service: GameApplicationService,
    actor: PlayerId):
  def history(using munit.Location): GameHistory =
    service.history(name) match
      case Right(Some(history)) => history
      case other => munit.Assertions.fail(s"$name has no history: $other")
  def players(using munit.Location): Vector[PlayerId] =
    history.steps.last.after match
      case OathState.Ready(ready) => ready.game.current.players.map(_.player)
      case other => munit.Assertions.fail(s"$name is not ready: $other")

object LogScripts:
  /** Every attack die a sword, every defense die two shields, so a script
    * reaches the same board every run. */
  val steadyDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.TwoShields)

  /** A fresh service and its journaled driver, the stream named `name`, with
    * pawns spread one per site so no two players share a site by accident. */
  def journaled(name: String, dice: CampaignDicePort = steadyDice,
      spread: Vector[SiteId] = FirstGameSetupFixture.sites)
      : (GameApplicationService, InMemoryEventStreamRepository, SituationDriver) =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = dice)
    (service, repository, Situation.journaled(service, catalog, repository,
      name).withAnswers(Situation.pawnsAt(spread)))

  def active(situation: Situation)(using munit.Location): PlayerId =
    situation.ready.game.current.turn.activePlayer

  def pawn(situation: Situation, player: PlayerId)(using munit.Location)
      : SiteId =
    situation.ready.game.current.players.find(_.player == player)
      .flatMap(_.pawnSite).get

  /** Setup to the first player's Wake. */
  def woken(using munit.Location): Script =
    val (service, _, driver) = journaled("woken")
    val situation = Situation.wake(driver)
    Script("woken", service, active(situation))

  /** Setup; the first player travels and rests; every other player rests;
    * Round 2 begins. */
  def round(using munit.Location): Script =
    val (service, _, driver) = journaled("round")
    val woken = Situation.wake(driver)
    val first = active(woken)
    val acting = woken.after(GameCommand.EndWake(first))
    val destination = acting.ready.game.current.map.inPlay
      .find(_ != pawn(acting, first)).get
    val travelled = acting.after(GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(first, Vector.empty,
        Vector(DecisionOptionRef.Site(destination)))))
    val seats = woken.ready.game.current.players.size
    (1 to seats).foldLeft(travelled) { (situation, turn) =>
      val player = active(situation)
      val awake = if turn == 1 then situation
        else situation.after(GameCommand.EndWake(player))
      // Begin Rest runs on into Finish Rest when Rest asks nothing.
      val resting = awake.after(GameCommand.BeginRest(player))
      if active(resting) == player then
        resting.after(GameCommand.FinishRest(player))
      else resting
    }
    Script("round", service, first)

  val presentation = new GamePresentationProjector(catalog)
  val formatter = new GameLogFormatter(catalog, presentation)

  def format(script: Script, viewer: Option[PlayerId])(using munit.Location)
      : Vector[LogEntry] =
    formatter.format(script.history.steps, viewer)

  /** An entry as a client that ignores span kinds shows it; the cost span
    * set apart by a space, as the pane sets it apart by a margin. */
  def text(entry: LogEntry): String = entry.spans.map {
    case cost: LogSpan.Cost => " " + cost.text
    case span => span.text
  }.mkString

  def texts(entries: Vector[LogEntry]): Vector[String] = entries.map(text)

  def name(player: PlayerId): String = presentation.playerLabel(player)

  /** The service suite's Oathkeeper tie: an arranged board, the active
    * player's Travel, and the holder's choice of the next Oathkeeper. */
  def oathkeeper(using munit.Location): Script =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = steadyDice)
    val (parked, active, _, _) = ParkedServiceFixture.oathkeeperTiePark(
      service, repository, "oathkeeper")
    Situation(parked.state, Vector.empty, parked.nextSequence,
      Situation.journaled(service, catalog, repository, "oathkeeper")).after()
    Script("oathkeeper", service, active)
