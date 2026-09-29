package oathdigital.application.gamelog

import oathdigital.application._
import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powers.action.{BarbedNet, GamblingHall, Oracle,
  Wolves}
import oathdigital.gameplay.powers.search.Augury
import oathdigital.gameplay.powers.whenplayed.FamilyHeirloom
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseAmountAnswer,
  ChooseOneAnswer, DeclineDeal, ProposeTerms}
import oathdigital.testkit.{Park, Situation, SituationDriver, Step, Table}
import oathdigital.testkit.Table.{p1, p2}

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

  /** The first player wakes, travels and rests; every other player rests;
    * Round 2 begins. */
  def round(using munit.Location): Script =
    val (service, woken) = atTable("round",
      Table.start.turn(p1, Phase.Wake).banditsRefilled)
    val first = active(woken)
    val acting = woken.endingWake(first)
    val destination = acting.ready.game.current.map.inPlay
      .find(_ != pawn(acting, first)).get
    val travelled = acting.after(GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(first, Vector.empty,
        Vector(DecisionOptionRef.Site(destination)))))
    val seats = woken.ready.game.current.players.size
    (1 to seats).foldLeft(travelled) { (situation, turn) =>
      val player = active(situation)
      val awake = if turn == 1 then situation
        else situation.endingWake(player)
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

  /** `steps` with `events` journaled right after position `after`. Each
    * changes no state, and every later sequence moves up to make room. */
  def inserted(steps: Vector[ReplayStep[OathState, OathEvent]], after: Int,
      events: OathEvent*): Vector[ReplayStep[OathState, OathEvent]] =
    val state = steps(after).after
    val at = steps(after).event.index + 1
    val added = events.toVector.zipWithIndex.map { case (event, offset) =>
      ReplayStep(RecordedEvent(at + offset, event), state, state) }
    steps.take(after + 1) ++ added ++ steps.drop(after + 1).map(step =>
      step.copy(event = step.event.copy(index = step.event.index + events.size)))

  /** `steps` without their power notes, renumbered: the journal of a power
    * that writes no line of its own. Notes change no state, so every other
    * step is unchanged. */
  def withoutNotes(steps: Vector[ReplayStep[OathState, OathEvent]])
      : Vector[ReplayStep[OathState, OathEvent]] =
    steps.filterNot(_.event.event.isInstanceOf[PowerNoted]).zipWithIndex.map {
      case (step, index) => step.copy(event = step.event.copy(index = index.toLong))
    }

  def formatWithoutNotes(script: Script, viewer: Option[PlayerId])(using
      munit.Location): Vector[LogEntry] =
    formatter.format(withoutNotes(script.history.steps), viewer)

  /** A fresh service whose stream `name` begins at `table`, and the
    * journaled situation there. */
  def atTable(name: String, table: Table, dice: CampaignDicePort = steadyDice)(
      using munit.Location): (GameApplicationService, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, table.situation(Situation.journaled(service, catalog, repository,
      name)))

  /** The service suite's Oathkeeper tie: a table with two tied leaders, the
    * active player's Travel, and the holder's choice of the next
    * Oathkeeper. */
  def oathkeeper(using munit.Location): Script =
    val (game, active, _, _) = ParkedServiceFixture.oathkeeperTiePark(
      "oathkeeper")
    Situation(game.accepted.state, Vector.empty, game.accepted.nextSequence,
      Situation.journaled(game.service, catalog, game.repository,
        "oathkeeper")).after()
    Script("oathkeeper", game.service, active)

  private def start(situation: Situation, action: StartableRef,
      args: DecisionOptionRef*)(using munit.Location): Situation =
    situation.after(GameCommand.StartWalker(action,
      StartPayload(active(situation), Vector.empty, args.toVector)))

  /** Arranges `ops` at the end of the first player's turn, just before
    * their Begin Rest, and plays on to the second player's Wake, returning
    * it and that player. An arrangement has no closing event of its own, so
    * the formatter reads it as the first step of the run that follows. The
    * run that follows here is the Rest, which tells only its deltas, so the
    * action a script tests stays whole. `ops` reads the situation it is
    * arranged in and the second player. */
  private def arrangedForNext(woken: Situation)(
      ops: (Situation, PlayerId) => Vector[CoreOperation])(
      using munit.Location): (Situation, PlayerId) =
    val first = active(woken)
    val next = FinishRestProcedure.turnOrder(woken.ready)(1)
    val acting = woken.endingWake(first)
    val resting = acting.after(Step.Arrange(ops(acting, next)),
      GameCommand.BeginRest(first))
    val rested = if active(resting) == first then
      resting.after(GameCommand.FinishRest(first)) else resting
    (rested, next)

  /** Search of the world deck, every decision answered by default. */
  def search(using munit.Location): Script =
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, act) = atTable("search", Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .banditsRefilled)
    start(act, ActionRef.Search, DecisionOptionRef.Button("search:world"))
    Script("search", service, active(act))

  /** p1's facedown adviser played from its slot. */
  def facedownAdviser(using munit.Location): Script =
    facedownAdviser("facedown-adviser", None)

  /** As above, the placement answered with the `placement` button when one
    * is given, else by default (the first button, discard). */
  def facedownAdviser(name: String, placement: Option[String])
      (using munit.Location): Script =
    val (service, acted) = atTable(name, Table.start
      .adviser(p1, "Wizard's Conclave", facedown = true).banditsRefilled)
    val act = placement.fold(acted)(key => acted.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(DecisionOptionRef.Button(key))
    })
    val actor = active(act)
    val held = act.ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) =>
          DecisionOptionRef.Denizen(id)
        case VisionState(id, Orientation.FaceDown) =>
          DecisionOptionRef.Vision(id)
      }.get
    start(act, ActionRef.PlayFacedownAdviser, held)
    Script(name, service, actor)

  /** The site with a card to Muster or Trade from: a first game deals no
    * denizen to a site, so on this board the card is a homeland edifice. */
  private def source(table: Table): Table =
    table.edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")

  def muster(using munit.Location): Script =
    val (service, act) = atTable("muster",
      source(Table.start.pawn(p1, "Deep Woods")).banditsRefilled)
    start(act, ActionRef.Muster)
    Script("muster", service, active(act))

  /** Trade for secrets, on the second player's turn, at the site with the
    * source. It costs two favor and a player starts with one, so a second
    * is arranged into their area. With no adviser matching the source's
    * suit, it gains nothing. */
  def trade(using munit.Location): Script =
    val (service, act) = atTable("trade",
      source(Table.start.pawn(p2, "Deep Woods")).banditsRefilled)
    val (waking, actor) = arrangedForNext(act)((_, next) =>
      Vector(Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(Suit.all.head)),
        PositionedLocation(Location.PlayArea(next)))))
    start(waking.endingWake(actor), ActionRef.Trade,
      DecisionOptionRef.Button("secret"))
    Script("trade", service, actor)

  /** One favor lies on p2's site, and p2 wakes there and takes it. That is
    * Wake's only option, so Wake ends with the take. */
  def takeWealth(using munit.Location): Script =
    val (service, waking) = atTable("take-wealth", Table.start
      .turn(p2, Phase.Wake).siteTokens("Broken Peaks", favor = 1)
      .banditsRefilled)
    start(waking, ActionRef.TakeWealth, DecisionOptionRef.Button("favor"))
    Script("take-wealth", service, p2)

  private def recovering(name: String, dice: CampaignDicePort)
      (using munit.Location): (GameApplicationService, Situation) =
    atTable(name, ParkedServiceFixture.recoverTable.banditsRefilled, dice)

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

  /** A successful Recover, then the recovered relic revealed as a minor
    * action. */
  def revealRelic(using munit.Location): Script =
    val (service, act) = recovering("reveal-relic", steadyDice)
    val actor = active(act)
    val recovered = start(act, ActionRef.Recover)
    val relic = recovered.ready.game.current.players.find(_.player == actor)
      .get.relics.collectFirst {
        case RelicState(id, Orientation.FaceDown, _) => id }.get
    recovered.after(GameCommand.RevealOwnedRelic(actor, relic))
    Script("reveal-relic", service, actor)

  /** The Forge fixture's table, then the Forge itself, which never parks at
    * a single-resource site. */
  def forge(using munit.Location): Script =
    val (service, _) = ForgeWalkerFixture.forgeService()
    val actor = Table.p1
    service.handle("forge", 0L, GameCommand.StartWalker(
      ActionRef.Forge, StartPayload(actor))).fold(
      error => munit.Assertions.fail(s"Forge refused: $error"), identity)
    Script("forge", service, actor)

  /** A Challenge for a banner from the bank, then resources placed on it.
    * A Challenge needs strictly more favor than the banner holds, and a
    * player starts with too little to challenge and still have favor to
    * place, so p1 holds three.
    */
  def banners(using munit.Location): Script =
    val (service, act) = atTable("banners",
      Table.start.favor(p1, 3).banditsRefilled)
    start(start(act, ActionRef.Challenge), ActionRef.PlaceBannerResource)
    Script("banners", service, p1)

  /** Attack dice all swords, defense dice all blank. */
  val raidDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** A Raid on the player whose pawn shares the actor's site. Every board
    * warband goes into the force and every survivor is sacrificed, so the
    * attack is twice the force against the defender's board warbands and
    * blank dice: the attacker wins. No battle plan is chosen. */
  def raid(using munit.Location): Script =
    val sites = FirstGameSetupFixture.sites
    val (service, _, driver) = journaled("raid", raidDice,
      Vector(sites(0), sites(0)) ++ sites.drop(1))
    val woken = Situation.wake(driver)
    val actor = active(woken)
    woken.withAnswers {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(DecisionOptionRef.Button("raid"))
      case Park(Decide(CampaignIds.force, _,
          DecisionQuery.ChooseAmount(_, max, _, _, _), _, _), _, _, _) =>
        ChooseAmountAnswer(max)
      case Park(Decide(CampaignIds.sacrifice, _,
          DecisionQuery.ChooseAmount(_, max, _, _, _), _, _), _, _, _) =>
        ChooseAmountAnswer(max)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }.endingWake(actor)
      .after(GameCommand.StartWalker(ActionRef.Campaign, StartPayload(actor)))
    Script("raid", service, actor)

  /** The first two pawns share a site, so the first player can negotiate
    * with exactly one other: the negotiators decision is not asked. */
  private def negotiating(name: String)(using munit.Location)
      : (GameApplicationService, Situation, PlayerId, PlayerId) =
    val sites = FirstGameSetupFixture.sites
    val (service, _, driver) = journaled(name,
      spread = Vector(sites(0), sites(0)) ++ sites.drop(1))
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val act = woken.endingWake(actor)
    val partner = act.ready.game.current.players.find(player =>
      player.player != actor && player.pawnSite == Some(pawn(act, actor)))
      .get.player
    (service, act.parkedAfter(GameCommand.StartWalker(ActionRef.Negotiation,
      StartPayload(actor))), actor, partner)

  private def deal(by: PlayerId, answer: DecisionAnswer): GameCommand =
    GameCommand.ResolveWalker(by, TreeDecision(NegotiationDeal.dealDecisionId,
      answer))

  def negotiationDeclined(using munit.Location): Script =
    val (service, parked, actor, partner) = negotiating("negotiation-declined")
    parked.after(deal(partner, DeclineDeal))
    Script("negotiation-declined", service, actor)

  def negotiationAgreed(using munit.Location): Script =
    val (service, parked, actor, partner) = negotiating("negotiation-agreed")
    parked
      .parkedAfter(deal(actor, ProposeTerms(NegotiationTerms(Vector(
        NegotiationTransfer(partner, 1, Vector.empty))))))
      .parkedAfter(deal(partner, AcceptDeal))
      .after(deal(actor, AcceptDeal))
    Script("negotiation-agreed", service, actor)

  /** The partner shows the actor its facedown starting adviser. */
  def negotiationDisclosed(using munit.Location): Script =
    val (service, parked, actor, partner) =
      negotiating("negotiation-disclosed")
    val adviser = parked.ready.game.current.players
      .find(_.player == partner).get.advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id: WorldCardId
        case VisionState(id, Orientation.FaceDown) => id: WorldCardId
      }.get
    parked
      .parkedAfter(deal(partner, ProposeTerms(NegotiationTerms(Vector.empty,
        Vector(NegotiationDisclosure(actor,
          NegotiationDisclosureRef.Adviser(partner, adviser)))))))
      .parkedAfter(deal(actor, AcceptDeal))
      .after(deal(partner, AcceptDeal))
    Script("negotiation-disclosed", service, actor)

  /** Silver Tongue used in Rest, its bank choice answered by default. */
  def usePower(using munit.Location): Script =
    val (game, actor, _) = ParkedServiceFixture.silverTonguePark("use-power",
      steadyDice)
    Situation(game.accepted.state, Vector.empty, game.accepted.nextSequence,
      Situation.journaled(game.service, catalog, game.repository,
        "use-power")).after()
    Script("use-power", game.service, actor)

  /** Augury, a free Search modifier, stands at the actor's site; the Search
    * selects it. The actor is the second player. */
  def augury(using munit.Location): Script =
    val card = DenizenId("56")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders,
      Vector(card))
    val (service, _, driver) = journaled("augury")
    val (waking, actor) = arrangedForNext(Situation.wake(driver, chronicle,
      orders))((acting, next) => Vector(ParkedServiceFixture.topOfWorldDeck(
        card, Location.Site(pawn(acting, next)))))
    waking.endingWake(actor)
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector(Augury.id), Vector(DecisionOptionRef.Button("search:world")))))
    Script("augury", service, actor)

  /** Gambling Hall at the actor's site, used in Act with a second favor
    * arranged. The steady dice total 8, and the richest bank is chosen so
    * the gain is never empty. */
  def gamblingHall(using munit.Location): Script =
    val card = DenizenId("93")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("gambling-hall")
    val woken = Situation.wake(driver, chronicle, orders)
    val richest = Suit.all.maxBy(suit => woken.ready.banks.favor(suit))
    val spare = Suit.all.find(suit => suit != richest &&
      woken.ready.banks.favor(suit) > 0).get
    val (waking, actor) = arrangedForNext(woken.withAnswers {
      case park if park.decisionId == GamblingHall.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.FavorBank(richest))
    })((acting, next) => Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(acting, next))),
        Move(Piece.Favor(1), PositionedLocation(Location.FavorBank(spare)),
          PositionedLocation(Location.PlayArea(next)))))
    waking.endingWake(actor).after(GameCommand.UsePower(actor, GamblingHall.id,
      DecisionOptionRef.Denizen(card)))
    Script("gambling-hall", service, actor)

  /** Wolves at the actor's site, used in Act with a secret arranged. The
    * board question parks; the answer kills one of the other player's
    * warbands with the most of them. */
  def wolves(using munit.Location): Script =
    val card = DenizenId("39")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("wolves")
    val woken = Situation.wake(driver, chronicle, orders)
    val actor = FinishRestProcedure.turnOrder(woken.ready)(1)
    val victim = woken.ready.game.current.players.filter(_.player != actor)
      .maxBy(_.board.warbands).player
    val (waking, _) = arrangedForNext(woken.withAnswers {
      case park if park.decisionId == Wolves.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(victim))
    })((acting, next) => Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(acting, next))),
        Move(Piece.Secrets(1), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(next)))))
    waking.endingWake(actor).after(GameCommand.UsePower(actor, Wolves.id,
      DecisionOptionRef.Denizen(card)))
    Script("wolves", service, actor)

  /** Oracle at the actor's site, used in Act with two secrets arranged. A
    * first game's world deck holds its first Vision below ten denizens;
    * Oracle draws it, and the actor keeps it as a facedown adviser. */
  def oracle(using munit.Location): Script = oracle("oracle", "adviser-facedown")

  /** Oracle, its drawn Vision placed by `placement`. */
  def oracle(name: String, placement: String)(using munit.Location): Script =
    val card = DenizenId("160")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled(name)
    val woken = Situation.wake(driver, chronicle, orders)
    val (waking, actor) = arrangedForNext(woken.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(DecisionOptionRef.Button(placement))
    })((acting, next) => Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(acting, next))),
        Move(Piece.Secrets(2), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(next)))))
    waking.endingWake(actor).after(GameCommand.UsePower(actor, Oracle.id,
      DecisionOptionRef.Denizen(card)))
    Script(name, service, actor)

  /** Barbed Net in the actor's play area and a relic from the relic deck at
    * the actor's site, used in Act with three secrets arranged. The relic
    * question parks; the answer takes that relic. */
  def barbedNet(using munit.Location): Script =
    val net = RelicId("R36")
    val (service, _, driver) = journaled("barbed-net")
    val woken = Situation.wake(driver, FirstGameSetupFixture.chronicle,
      FirstGameSetupFixture.orders)
    val current = woken.ready.game.current
    val from = current.map.sites.collectFirst {
      case (site, state) if state.relics.exists(_.id == net) =>
        Location.Site(site)
    }.getOrElse(Location.Deck(CardDeck.Relic))
    val target = current.commonCards.relicDeck.find(_ != net).get
    val (waking, actor) = arrangedForNext(woken.withAnswers {
      case park if park.decisionId == BarbedNet.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Relic(target))
    })((acting, next) => Vector(
        Move(Piece.Card(net), PositionedLocation(from),
          PositionedLocation(Location.PlayArea(next)),
          resultingOrientation = Some(Orientation.FaceUp)),
        Move(Piece.Card(target), PositionedLocation(Location.Deck(CardDeck.Relic)),
          PositionedLocation(Location.Site(pawn(acting, next))),
          resultingOrientation = Some(Orientation.FaceDown)),
        Move(Piece.Secrets(3), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(next)))))
    waking.endingWake(actor).after(GameCommand.UsePower(actor, BarbedNet.id,
      DecisionOptionRef.Relic(net)))
    Script("barbed-net", service, actor)

  /** Hunger faceup with the second player, whose Wake begins when the first
    * player rests. The forced step parks for them. Pawns are spread, so the
    * only candidate is their own starting adviser, which the default answer
    * buries. The script's actor is Hunger's holder. */
  def hunger(using munit.Location): Script =
    val card = DenizenId("216")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("hunger")
    val woken = Situation.wake(driver, chronicle, orders)
    val first = active(woken)
    val holder = FinishRestProcedure.turnOrder(woken.ready)(1)
    woken.after(Step.Arrange(Vector(ParkedServiceFixture.topOfWorldDeck(card,
        Location.PlayArea(holder)))))
      .endingWake(first).after(GameCommand.BeginRest(first))
    Script("hunger", service, holder)

  /** Family Heirloom held by the second player as a facedown adviser, then
    * played faceup as an adviser: its When Played draw is kept or put on the
    * bottom by `choice`. */
  def familyHeirloom(name: String, choice: DecisionOptionRef.Button)
      (using munit.Location): Script =
    val card = FamilyHeirloom.forCatalog(catalog).get.cardId
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled(name)
    val woken = Situation.wake(driver, chronicle, orders)
    val (waking, actor) = arrangedForNext(woken.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup"))
      case park if park.decisionId == FamilyHeirloom.decisionId =>
        ChooseOneAnswer(choice)
    })((_, next) => Vector(ParkedServiceFixture.topOfWorldDeck(card,
        Location.PlayArea(next), Orientation.FaceDown)))
    waking.endingWake(actor)
      .after(GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(DecisionOptionRef.Denizen(card)))))
    Script(name, service, actor)

  def heirloomKept(using munit.Location): Script =
    familyHeirloom("heirloom-kept", FamilyHeirloom.keep)

  def heirloomReturned(using munit.Location): Script =
    familyHeirloom("heirloom-returned", FamilyHeirloom.bottom)

  /** Every script by its stream name, for the suites that hold for each. */
  val named: Vector[(String, () => Script)] = Vector(
    "woken" -> (() => woken), "round" -> (() => round),
    "oathkeeper" -> (() => oathkeeper), "search" -> (() => search),
    "augury" -> (() => augury),
    "facedown-adviser" -> (() => facedownAdviser),
    "muster" -> (() => muster), "trade" -> (() => trade),
    "take-wealth" -> (() => takeWealth),
    "recover-failed" -> (() => recoverFailed),
    "recover-succeeded" -> (() => recoverSucceeded),
    "reveal-relic" -> (() => revealRelic), "forge" -> (() => forge),
    "banners" -> (() => banners), "raid" -> (() => raid),
    "negotiation-declined" -> (() => negotiationDeclined),
    "negotiation-agreed" -> (() => negotiationAgreed),
    "negotiation-disclosed" -> (() => negotiationDisclosed),
    "use-power" -> (() => usePower),
    "gambling-hall" -> (() => gamblingHall), "wolves" -> (() => wolves),
    "oracle" -> (() => oracle), "barbed-net" -> (() => barbedNet),
    "hunger" -> (() => hunger),
    "heirloom-kept" -> (() => heirloomKept),
    "heirloom-returned" -> (() => heirloomReturned))

  def all: Vector[Script] = named.map(_._2())
