package oathdigital.application.gamelog

import oathdigital.application._
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{Situation, SituationDriver, Step}

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

  private def acting(name: String)(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository, Situation) =
    val (service, repository, driver) = journaled(name)
    val woken = Situation.wake(driver)
    (service, repository, woken.after(GameCommand.EndWake(active(woken))))

  private def start(situation: Situation, action: StartableRef,
      args: DecisionOptionRef*)(using munit.Location): Situation =
    situation.after(GameCommand.StartWalker(action,
      StartPayload(active(situation), Vector.empty, args.toVector)))

  /** Search of the world deck, every decision answered by default. */
  def search(using munit.Location): Script =
    val (service, _, act) = acting("search")
    start(act, ActionRef.Search, DecisionOptionRef.Button("search:world"))
    Script("search", service, active(act))

  /** The setup adviser played from its facedown slot. */
  def facedownAdviser(using munit.Location): Script =
    val (service, _, act) = acting("facedown-adviser")
    val actor = active(act)
    val held = act.ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) =>
          DecisionOptionRef.Denizen(id)
        case VisionState(id, Orientation.FaceDown) =>
          DecisionOptionRef.Vision(id)
      }.get
    start(act, ActionRef.PlayFacedownAdviser, held)
    Script("facedown-adviser", service, actor)

  /** The actor stands on a site with a card to Muster or Trade from,
    * travelling there first if the pawn's own site has none. A first game
    * deals no denizen to a site, so on this board the card is a homeland
    * edifice. */
  private def besideSource(act: Situation)(using munit.Location): Situation =
    val actor = active(act)
    def hasSource(site: SiteId) = act.ready.game.current.map.sites(site)
      .denizens.exists(_.tokens.isEmpty)
    if hasSource(pawn(act, actor)) then act
    else start(act, ActionRef.Travel, DecisionOptionRef.Site(
      act.ready.game.current.map.inPlay.find(hasSource).get))

  def muster(using munit.Location): Script =
    val (service, _, act) = acting("muster")
    start(besideSource(act), ActionRef.Muster)
    Script("muster", service, active(act))

  /** Trade for secrets. It costs two favor and a player starts with one, so
    * a second is arranged into the actor's area before End Wake. With no
    * adviser matching the source's suit, it gains nothing. */
  def trade(using munit.Location): Script =
    val (service, _, driver) = journaled("trade")
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val act = woken.after(Step.Arrange(Vector(Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(Suit.all.head)),
        PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor))
    start(besideSource(act), ActionRef.Trade, DecisionOptionRef.Button("secret"))
    Script("trade", service, actor)

  /** One favor arranged onto the actor's site, then taken in Wake. */
  def takeWealth(using munit.Location): Script =
    val (service, _, driver) = journaled("take-wealth")
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val arranged = woken.after(Step.Arrange(Vector(Move(Piece.Favor(1),
      PositionedLocation(Location.FavorBank(Suit.all.head)),
      PositionedLocation(Location.Site(pawn(woken, actor)))))))
    start(arranged, ActionRef.TakeWealth, DecisionOptionRef.Button("favor"))
    Script("take-wealth", service, actor)

  private def recovering(name: String, dice: CampaignDicePort)
      (using munit.Location): (GameApplicationService, Situation) =
    val (service, _, driver) = journaled(name, dice,
      ParkedServiceFixture.recoverSites)
    val woken = Situation.wake(driver,
      ParkedServiceFixture.recoverChronicle)
    (service, woken.after(GameCommand.EndWake(active(woken))))

  /** Dice that fail every Recover roll: continue once, then stop. */
  def recoverFailed(using munit.Location): Script =
    val (service, act) = recovering("recover-failed",
      ParkedServiceFixture.failingDice)
    val actor = active(act)
    def answer(key: String) = GameCommand.ResolveWalker(actor, TreeDecision(
      RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button(key))))
    act.parkedAfter(GameCommand.StartWalker(ActionRef.Recover,
        StartPayload(actor)))
      .parkedAfter(answer("continue"))
      .after(answer("stop"))
    Script("recover-failed", service, actor)

  /** Steady dice succeed at once; the relic decision is answered by default. */
  def recoverSucceeded(using munit.Location): Script =
    val (service, act) = recovering("recover-succeeded", steadyDice)
    start(act, ActionRef.Recover)
    Script("recover-succeeded", service, active(act))

  /** The Forge fixture's journal (a Conquest, Searches, rounds) and then the
    * Forge itself, which never parks at a single-resource site. */
  def forge(using munit.Location): Script =
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository,
      campaignDicePort = ForgeWalkerFixture.blankCampaignDice)
    val (ready, actor, _) = ForgeWalkerFixture.forgeReadyGame(service, "forge")
    service.handle("forge", ready.nextSequence, GameCommand.StartWalker(
      ActionRef.Forge, StartPayload(actor))).fold(
      error => munit.Assertions.fail(s"Forge refused: $error"), identity)
    Script("forge", service, actor)

  /** A Challenge for a banner from the bank, then resources placed on it.
    * A Challenge needs strictly more favor than the banner holds, and a
    * player starts with too little to challenge and still have favor to
    * place, so two favor are arranged into the actor's area before End Wake.
    */
  def banners(using munit.Location): Script =
    val (service, _, driver) = journaled("banners")
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val act = woken.after(Step.Arrange(Vector(Move(Piece.Favor(2),
        PositionedLocation(Location.FavorBank(Suit.all.head)),
        PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor))
    start(start(act, ActionRef.Challenge), ActionRef.PlaceBannerResource)
    Script("banners", service, actor)
