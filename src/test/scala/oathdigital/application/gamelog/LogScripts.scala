package oathdigital.application.gamelog

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.application._
import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.{ModifierPlay, TravelPlay}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.minor.MinorActionProcedures
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.action.{BarbedNet, GamblingHall, Oracle,
  Wolves}
import oathdigital.gameplay.powers.cardplay.WildCry
import oathdigital.gameplay.powers.economy.GolemLegions
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, NewsFromAfar}
import oathdigital.gameplay.powers.travel.Tents
import oathdigital.gameplay.powers.whenplayed.FamilyHeirloom
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{FollowUpOffer, PowerNoted, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseAmountAnswer,
  ChooseOneAnswer, DeclineDeal, ProposeTerms}
import oathdigital.protocol.projection.{LogEntry, LogSpan}
import oathdigital.testkit.{Answers, CatalogNames, Dice, Look, MenuPlay, Park,
  PlacementPicks, Situation, Step, Table}
import oathdigital.testkit.Unwrap.as
import oathdigital.testkit.Table.{p1, p2, p3}

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

  /** The first seated player who is not the actor: p2 when p1 acts, p1
    * otherwise. */
  def otherSeat(using munit.Location): PlayerId =
    players.find(_ != actor).getOrElse(munit.Assertions.fail(
      s"$name has no player besides $actor among $players"))

object LogScripts:
  /** The table every script below begins at: the quiet table with each
    * pawn stated at the site the scripts' log lines and goldens name, p1 at
    * Ancient City, p2 at Broken Peaks and p3 at Buried Giant. A script that
    * needs a pawn elsewhere moves it after this. */
  private def tableWithOnePawnPerSite: Table =
    Table.start.pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")

  /** A short journal on a table, for the suites that append their own
    * recorded events to a real board and read the lines they post. The board
    * holds the facts those suites lean on, each stated here: a relic lying at
    * Broken Peaks, a homeland edifice at Deep Woods, and a facedown adviser
    * with each of p2 and p3. p1, the actor, wakes and ends Wake, and it is
    * their Act. */
  def recordedEventsBoard(using munit.Location): Script =
    val (service, waking) = atTable("board", tableWithOnePawnPerSite.turn(p1, Phase.Wake)
      .relicAt("Sticky Fire", "Broken Peaks")
      .edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")
      .adviser(p2, "Birdsong", facedown = true)
      .adviser(p3, "Wizard's Conclave", facedown = true)
      .banditsRefilled)
    waking.endingWake(p1)
    Script("board", service, p1)

  /** p1's Wake with one favor at Ancient City and nothing else to take. */
  private def wealthTable: Table = tableWithOnePawnPerSite
    .turn(p1, Phase.Wake).siteTokens("Ancient City", favor = 1, secrets = 0)
    .banditsRefilled

  /** p1 takes the favor by picking it from the Wake menu. */
  def wealthFromMenu(using munit.Location): Script =
    val (service, waking) = atTable("menu-wealth",
      wealthTable.phaseMenuParked)
    waking.after(MenuPlay.picking(p1,
      PhaseMenu.takeWealth(WakeResource.Favor)))
    Script("menu-wealth", service, p1)

  /** p1 rests holding Insomnia and uses it by picking it from the Rest menu,
    * which ends the Rest. */
  def restFromMenu(using munit.Location): Script =
    val (service, resting) = atTable("menu-rest", tableWithOnePawnPerSite
      .adviser(p1, "Insomnia").turn(p1, Phase.Rest).banditsRefilled
      .phaseMenuParked)
    resting.after(MenuPlay.picking(p1,
      DecisionOptionRef.Denizen(CatalogNames.denizen("Insomnia"))))
    Script("menu-rest", service, p1)

  /** The same take, started directly, as before the menus. */
  def wealthStarted(using munit.Location): Script =
    val (service, waking) = atTable("started-wealth", wealthTable)
    waking.after(MenuPlay.start(p1, ActionRef.TakeWealth, TakeWealthProcedure.selection(WakeResource.Favor)))
    Script("started-wealth", service, p1)

  /** Setup to the first player's Wake, on the real start: the script that
    * replays Setup with every park answered by default, so its log carries
    * the setup lines. Pawns are spread one per site. */
  def woken(using munit.Location): Script =
    wokenAnswering("woken", PartialFunction.empty)

  /** As `woken`, with every Reveal Cards answered by revealing every card it
    * offers. */
  def wokenRevealingAll(using munit.Location): Script =
    wokenAnswering("revealed", {
      case park if park.decisionId ==
          SetupProcedure.revealDecisionId(park.awaiting) =>
        val offered = as[DecisionQuery.ChooseMany](park.decide.query).options
        DecisionAnswer.ChooseManyAnswer(offered.map(_.ref))
    })

  /** Setup to the first player's Wake on stream `name`, pawns spread one per
    * site, `answers` answering the parks they match first. */
  private def wokenAnswering(name: String, answers: Answers)(
      using munit.Location): Script =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = Dice.steady)
    val situation = Situation.wake(Situation.journaled(service, catalog,
      repository, name).withAnswers(
        answers orElse Situation.pawnsAt(FirstGameSetupFixture.sites)))
    Script(name, service, Look(situation.ready).active)

  /** The first player wakes, travels and rests; every other player rests;
    * Round 2 begins. */
  def round(using munit.Location): Script =
    val (service, woken) = atTable("round",
      tableWithOnePawnPerSite.turn(p1, Phase.Wake).banditsRefilled)
    val first = Look(woken.ready).active
    val acting = woken.endingWake(first)
    val destination = acting.ready.game.current.map.inPlay
      .find(_ != Look(acting.ready).pawn(first)).get
    val travelled = acting
      .parkedAfter(ParkedServiceFixture.startTravel(first))
      .after(ParkedServiceFixture.pickTravelDestination(first, destination))
    val seats = woken.ready.game.current.players.size
    (1 to seats).foldLeft(travelled) { (situation, turn) =>
      val player = Look(situation.ready).active
      val awake = if turn == 1 then situation
        else situation.endingWake(player)
      // Begin Rest runs on into Finish Rest when Rest asks nothing.
      val resting = awake.after(MenuPlay.start(player, PhaseTransitionRef.BeginRest))
      if Look(resting.ready).active == player then
        resting.finishingRest(player)
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

  /** The first of `lines` that starts with `prefix`; fails with the lines. */
  def lineStarting(lines: Vector[String], prefix: String)(
      using munit.Location): String =
    lines.find(_.startsWith(prefix)).getOrElse(munit.Assertions.fail(
      s"no line starts with '$prefix' in $lines"))

  /** The first of `entries` whose text starts with `prefix`; fails with the
    * entries' texts. */
  def entryStarting(entries: Vector[LogEntry], prefix: String)(
      using munit.Location): LogEntry =
    entries.find(entry => text(entry).startsWith(prefix)).getOrElse(
      munit.Assertions.fail(
        s"no entry starts with '$prefix' in ${texts(entries)}"))

  def playerLabel(player: PlayerId): String = presentation.playerLabel(player)

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

  /** `script`'s log for `viewer` with `events` journaled after its last
    * step. Each appended step starts from `stateBefore` of the script's last
    * state and ends in `lastStateAfter` of it: both unchanged unless a test
    * gives the state an event needs or the change it stands for. */
  def formatAppended(script: Script, events: Vector[OathEvent],
      viewer: Option[PlayerId] = None,
      lastStateAfter: OathState => OathState = identity,
      stateBefore: OathState => OathState = identity)(
      using munit.Location): Vector[LogEntry] =
    val steps = script.history.steps
    // The appended events stand for a walk of their own: the script's phase
    // menu, if it ends at one, is picked from first and so completed.
    val last = MenuPlay.withoutMenu(steps.last.after)
    val picked: Vector[OathEvent] = MenuPlay.parked(steps.last.after)
      .map(menu => WalkerCompleted(menu.procedure)).toVector
    formatter.format(steps ++ (picked ++ events).zipWithIndex.map { case (event, index) =>
      ReplayStep(RecordedEvent(steps.size.toLong + index, event),
        stateBefore(last), lastStateAfter(last))
    }, viewer)

  /** `format` over `withoutNotes`: the log of the script as a power that
    * writes no note would leave it. */
  def formatWithoutNotes(script: Script, viewer: Option[PlayerId])(using
      munit.Location): Vector[LogEntry] =
    formatter.format(withoutNotes(script.history.steps), viewer)

  /** A fresh service whose stream `name` begins at `table`, and the
    * journaled situation there. A script's table ends with `banditsRefilled`,
    * or its first command journals the bandit refill as well and the log
    * reads a "Bandits returned" line no script means to test. */
  def atTable(name: String, table: Table,
      dice: CampaignDicePort = Dice.steady)(
      using munit.Location): (GameApplicationService, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, table.situation(Situation.journaled(service, catalog, repository,
      name)))

  /** The service suite's Oathkeeper tie: a table with two tied leaders, the
    * active player's Travel, and the holder's choice of the next
    * Oathkeeper, answered by default. ParkedServiceFixture.oathkeeperTiePark
    * states where each pawn stands. */
  def oathkeeperTie(using munit.Location): Script =
    val ParkedServiceFixture.OathkeeperTiePark(game, mover, _, _) =
      ParkedServiceFixture.oathkeeperTiePark("oathkeeper")
    game.situation.after()
    Script("oathkeeper", game.service, mover)

  private def start(situation: Situation, action: StartableRef,
      args: DecisionOptionRef*)(using munit.Location): Situation =
    situation.after(MenuPlay.start(Look(situation.ready).active, action, args.toVector))

  /** Arranges `ops` at the end of the first player's turn, just before
    * their Begin Rest, and plays on to the second player's Wake, returning
    * it and that player. An arrangement has no closing event of its own, so
    * the formatter reads it as the first step of the run that follows. The
    * run that follows here is the Rest, which tells only its deltas, so the
    * action a script tests stays whole. `ops` reads the situation it is
    * arranged in and the second player. */
  private def arrangedForNext(acting: Situation)(
      ops: (Situation, PlayerId) => Vector[CoreOperation])(
      using munit.Location): (Situation, PlayerId) =
    val first = Look(acting.ready).active
    val next = FinishRestProcedure.turnOrder(acting.ready)(1)
    val resting = acting.after(Step.Arrange(ops(acting, next)),
      MenuPlay.starting(first, PhaseTransitionRef.BeginRest))
    val rested = if Look(resting.ready).active == first then
      resting.finishingRest(first) else resting
    (rested, next)

  /** Search of the world deck, every decision answered by default. */
  def search(using munit.Location): Script =
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, act) = atTable("search", tableWithOnePawnPerSite
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .banditsRefilled)
    start(act, ActionRef.Search)
    Script("search", service, Look(act.ready).active)

  /** Forced Labor at Broken Peaks, and p2 ruling both Broken Peaks and
    * Ancient City, where p1 stands: p1's Search of the world deck spends 2
    * Supply and, its step answered by paying, gives p2 1 favor. p1 holds 5
    * Supply and the start's 1 favor. */
  def forcedLaborSearch(using munit.Location): Script =
    val (service, act) = atTable("forced-labor", tableWithOnePawnPerSite
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .supply(p1, 5).denizen("Forced Labor", "Broken Peaks")
      .warbandsAt("Broken Peaks", p2, 1).warbandsAt("Ancient City", p2, 1)
      .banditsRefilled)
    start(act, ActionRef.Search)
    Script("forced-labor", service, p1)

  /** p1's facedown adviser played from its slot. */
  def facedownAdviser(using munit.Location): Script =
    facedownAdviser("facedown-adviser", None)

  /** As above, the card played as `placement` names when one is given, else
    * by default (the first option, discard). */
  def facedownAdviser(name: String, placement: Option[PlayedAs])
      (using munit.Location): Script =
    val conclave = CatalogNames.denizen("Wizard's Conclave")
    val (service, acted) = atTable(name, tableWithOnePawnPerSite
      .adviser(p1, conclave, facedown = true).banditsRefilled)
    val act = placement.fold(acted)(way => acted.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(PlacementPicks.pick(park.decide, way))
    })
    start(act, ActionRef.PlayFacedownAdviser)
    Script(name, service, p1)

  /** Deep Woods with a card to Muster or Trade from: a first game deals no
    * denizen to a site, so on this board the card is a homeland edifice. The
    * actor's pawn must stand there. */
  private def source(table: Table): Table =
    table.edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")

  /** p1 musters at Deep Woods holding `advisers` faceup. */
  private def musterHolding(name: String, advisers: String*)(
      using munit.Location): Script =
    val (service, act) = atTable(name, source(advisers.foldLeft(
      tableWithOnePawnPerSite.pawn(p1, "Deep Woods"))(_.adviser(p1, _)))
      .banditsRefilled)
    start(act, ActionRef.Muster)
    Script(name, service, Look(act.ready).active)

  /** p1 musters at Deep Woods. */
  def muster(using munit.Location): Script = musterHolding("muster")

  /** p1 musters at Deep Woods holding Initiation Rite, so the card mustered
    * on receives a secret instead of a favor. */
  def initiatedMuster(using munit.Location): Script =
    musterHolding("initiated-muster", "Initiation Rite")

  /** p1 musters at Deep Woods holding Vow of Wandering. */
  def vowMuster(using munit.Location): Script =
    musterHolding("vow-muster", "Vow of Wandering")

  /** p1 musters on Golem Legions, their adviser, with no card at their site.
    * The Muster picks Golem Legions, its only modifier, so it starts by
    * itself. */
  def golemMuster(using munit.Location): Script =
    val (service, act) = atTable("golem-muster", tableWithOnePawnPerSite
      .adviser(p1, "Golem Legions").banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.MusterModifierSelection,
      Vector(GolemLegions.id)))
      .after(MenuPlay.start(p1, ActionRef.Muster))
    Script("golem-muster", service, p1)

  /** Trade for secrets, on the second player's turn, at the site with the
    * source. It costs two favor and a player starts with one, so a second
    * is arranged into their area. With no adviser matching the source's
    * suit, it gains nothing. */
  def trade(using munit.Location): Script =
    val (service, act) = atTable("trade",
      source(tableWithOnePawnPerSite.pawn(p2, "Deep Woods")).banditsRefilled)
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
    val (service, waking) = atTable("take-wealth", tableWithOnePawnPerSite
      .turn(p2, Phase.Wake).pawn(p2, "Broken Peaks")
      .siteTokens("Broken Peaks", favor = 1)
      .banditsRefilled)
    start(waking, ActionRef.TakeWealth, DecisionOptionRef.Button("favor"))
    Script("take-wealth", service, p2)

  private def recovering(name: String, dice: CampaignDicePort)
      (using munit.Location): (GameApplicationService, Situation) =
    // p2 is off Broken Peaks (see `recoverTable`), so the relics stay hidden
    // from the other seat.
    atTable(name, ParkedServiceFixture.recoverTable.banditsRefilled, dice)

  /** Dice that fail every Recover roll: continue once, then stop. */
  def recoverFailed(using munit.Location): Script =
    val (service, act) = recovering("recover-failed",
      Dice.blanks)
    val actor = Look(act.ready).active
    def answer(key: String) = GameCommand.ResolveWalker(actor, TreeDecision(
      RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button(key))))
    act.parkedAfter(MenuPlay.start(actor, ActionRef.Recover))
      .parkedAfter(answer("continue"))
      .after(answer("stop"))
    Script("recover-failed", service, actor)

  /** Steady dice succeed at once; the relic decision is answered by default. */
  def recoverSucceeded(using munit.Location): Script =
    val (service, act) = recovering("recover-succeeded", Dice.steady)
    start(act, ActionRef.Recover)
    Script("recover-succeeded", service, Look(act.ready).active)

  /** A successful Recover, then the recovered relic revealed with Reveal
    * Owned Relic. The relic choice is answered by default. */
  def revealRelic(using munit.Location): Script =
    val (service, act) = recovering("reveal-relic", Dice.steady)
    val actor = Look(act.ready).active
    start(act, ActionRef.Recover).after(
      MenuPlay.start(actor, ActionRef.RevealOwnedRelic))
    Script("reveal-relic", service, actor)

  /** p1 peeks at Broken Peaks, where Sticky Fire lies. p2 and p3 stand
    * elsewhere, so only p1 learns the relic. */
  def peekRelics(using munit.Location): Script =
    val (service, act) = atTable("peek-relics", Table.start
      .pawn(p1, at = "Broken Peaks").pawn(p2, at = "Ancient City")
      .pawn(p3, at = "Buried Giant").relicAt("Sticky Fire", "Broken Peaks")
      .banditsRefilled)
    act.after(MenuPlay.start(p1, ActionRef.PeekSiteRelics))
    Script("peek-relics", service, p1)

  /** p1 rules Broken Peaks with 3 warbands and holds 4 more. Two minor
    * actions: 2 warbands to the site, then 1 back to the board. */
  def warbandsMoved(using munit.Location): Script =
    val (service, act) = atTable("warbands-moved", Table.start
      .pawn(p1, at = "Broken Peaks").boardWarbands(p1, 4)
      .warbandsAt("Broken Peaks", p1, 3).banditsRefilled)
    def answer(decisionId: String, value: DecisionAnswer) =
      GameCommand.ResolveWalker(p1, TreeDecision(decisionId, value))
    def moved(from: Situation, way: DecisionOptionRef.Button,
        amount: Int): Situation =
      from.parkedAfter(MenuPlay.start(p1, ActionRef.MoveWarbands))
        .parkedAfter(answer(MinorActionProcedures.directionDecisionId,
          ChooseOneAnswer(way)))
        .after(answer(MinorActionProcedures.amountDecisionId,
          ChooseAmountAnswer(amount)))
    moved(moved(act, MinorActionProcedures.toSite, 2),
      MinorActionProcedures.toBoard, 1)
    Script("warbands-moved", service, p1)

  /** p1 recovers at Broken Peaks with Relic Worship, their adviser, and one
    * faceup secret. Relic Worship is the only modifier, so its pick starts
    * the Recover by itself. */
  def recoverWorship(using munit.Location): Script =
    val (service, act) = atTable("recover-worship",
      ParkedServiceFixture.recoverTable.adviser(p1, "Relic Worship")
        .secrets(p1, faceUp = 1).banditsRefilled, Dice.steady)
    act.withAnswers(ModifierPlay.answers(
      PowerWindow.RecoverModifierSelection, Vector(RelicWorship.id)))
      .after(MenuPlay.start(p1, ActionRef.Recover))
    Script("recover-worship", service, p1)

  /** The Forge fixture's table, then the Forge itself, which never parks at
    * a single-resource site. */
  def forge(using munit.Location): Script =
    val (service, _) = ForgeWalkerFixture.forgeService()
    val actor = Table.p1
    service.handle("forge", 0L, MenuPlay.starting(actor, ActionRef.Forge)).fold(
      error => munit.Assertions.fail(s"Forge refused: $error"), identity)
    Script("forge", service, actor)

  /** A Challenge for a banner from the bank, then resources placed on it.
    * A Challenge needs strictly more favor than the banner holds, and a
    * player starts with too little to challenge and still have favor to
    * place, so p1 holds three.
    */
  def banners(using munit.Location): Script =
    val (service, act) = atTable("banners",
      tableWithOnePawnPerSite.favor(p1, 3).banditsRefilled)
    start(start(act, ActionRef.Challenge), ActionRef.PlaceBannerResource)
    Script("banners", service, p1)

  /** A Raid on p2, whose pawn shares p1's site and who holds a facedown
    * adviser to lose. Every board warband goes into the force and every
    * survivor is sacrificed, so the attack is twice the force against the
    * defender's board warbands and blank dice: the attacker wins. No battle
    * plan is chosen. */
  def raid(using munit.Location): Script =
    val (service, act) = atTable("raid", tableWithOnePawnPerSite
      .pawn(p2, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true).banditsRefilled, Dice.raid)
    raidAnswers(act).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("raid", service, p1)

  /** The `raid` table with Careful Plans faceup as p1's adviser and one
    * favor to pay for it. p1 chooses it, so p2's defense dice roll before
    * p1's attack dice. Everything else is answered as in `raid`. */
  def raidUnderCarefulPlans(using munit.Location): Script =
    val careful = CatalogNames.denizen("Careful Plans")
    val (service, act) = atTable("careful-raid", tableWithOnePawnPerSite
      .pawn(p2, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .adviser(p1, careful).favor(p1, 1).banditsRefilled, Dice.raid)
    // Chosen once: the favor it places marks it used.
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.attackerPlan &&
          Look(park.ready).adviserTokens(p1, careful) == Tokens(0, 0) =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(careful))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("careful-raid", service, p1)

  /** The `raid` table with a Campaign offered to p1 by Knights Errant, which
    * p1 holds faceup: p1 takes the offer and the Raid runs as that queued
    * Campaign. */
  def queuedRaid(using munit.Location): Script =
    val (service, act) = offeredRaidTable("queued-raid", Vector.empty)
    raidAnswers(act).after(takeOffer)
    Script("queued-raid", service, p1)

  /** The same with a second Campaign behind the first: p1 takes the first,
    * which runs, and skips the second. */
  def queuedRaidThenSkip(using munit.Location): Script =
    val (service, act) = offeredRaidTable("queued-raid-skip",
      Vector(knightsOffer))
    raidAnswers(act).withAnswers {
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(FollowUpOffer.skip)
    }.after(takeOffer)
    Script("queued-raid-skip", service, p1)

  private def knightsOffer: QueuedProcedure = QueuedProcedure(p1,
    ActionRef.Campaign, Vector.empty,
    Some(PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))),
    QueueKind.Offer)

  private def takeOffer: GameCommand = GameCommand.ResolveWalker(p1,
    TreeDecision(FollowUpOffer.decisionId, ChooseOneAnswer(FollowUpOffer.take)))

  /** The `raid` table with Knights Errant held by p1, a Campaign offer
    * parked for p1 and `behind` queued after it. */
  private def offeredRaidTable(name: String, behind: Vector[QueuedProcedure])(
      using munit.Location): (GameApplicationService, Situation) =
    atTable(name, tableWithOnePawnPerSite
      .pawn(p2, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .adviser(p1, CatalogNames.denizen("Knights Errant"))
      .update(_.updateCurrent(_.copy(queue = behind)))
      .offering(knightsOffer)
      .banditsRefilled, Dice.raid)

  /** A Raid's answers: all force committed and sacrificed, and Finish at
    * every battle plan window unless `plans` answers it first. */
  def raidAnswers(act: Situation,
      plans: Answers = PartialFunction.empty): Situation =
    act.withAnswers(plans.orElse {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(DecisionOptionRef.Button("raid"))
      case Park(Decide(CampaignIds.force, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case Park(Decide(CampaignIds.sacrifice, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    })

  /** p1 and p2 share Ancient City, so p1 can negotiate with exactly one
    * other: the negotiators decision is not asked. `extra` adds to the
    * table. */
  private def negotiating(name: String, extra: Table => Table = identity)(
      using munit.Location)
      : (GameApplicationService, Situation, PlayerId, PlayerId) =
    val (service, act) = atTable(name,
      extra(tableWithOnePawnPerSite.pawn(p2, "Ancient City")).banditsRefilled)
    (service, act.parkedAfter(MenuPlay.start(p1, ActionRef.Negotiation)), p1, p2)

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

  /** The partner shows the actor its facedown adviser. */
  def negotiationDisclosed(using munit.Location): Script =
    val birdsong = CatalogNames.denizen("Birdsong")
    val (service, parked, actor, partner) = negotiating(
      "negotiation-disclosed", _.adviser(p2, birdsong, facedown = true))
    val adviser: WorldCardId = birdsong
    parked
      .parkedAfter(deal(partner, ProposeTerms(NegotiationTerms(Vector.empty,
        Vector(NegotiationDisclosure(actor,
          NegotiationDisclosureRef.Adviser(partner, adviser)))))))
      .parkedAfter(deal(actor, AcceptDeal))
      .after(deal(partner, AcceptDeal))
    Script("negotiation-disclosed", service, actor)

  /** Silver Tongue used in Rest, its bank choice answered by default.
    * ParkedServiceFixture.silverTonguePark states where each pawn stands. */
  def silverTongue(using munit.Location): Script =
    val ParkedServiceFixture.SilverTonguePark(game, actor, _) =
      ParkedServiceFixture.silverTonguePark("use-power", Dice.steady)
    game.situation.after()
    Script("use-power", game.service, actor)

  /** Augury, a free Search modifier, stands at p1's site; the Search picks
    * it. */
  def augury(using munit.Location): Script =
    val (service, act) = atTable("augury", tableWithOnePawnPerSite
      .denizen("Augury", "Ancient City").banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.SearchModifierSelection,
      Vector(Augury.id))).after(MenuPlay.start(p1, ActionRef.Search))
    Script("augury", service, p1)

  /** Augury at p1's site and News from Afar as p1's adviser, with 2 favor
    * to pay for it. The Search picks News from Afar, then Augury; every
    * modifier is then picked, so it starts by itself. News from Afar spends
    * no Supply. */
  def twoPicks(using munit.Location): Script =
    val (service, act) = atTable("two-picks", tableWithOnePawnPerSite
      .denizen("Augury", "Ancient City").adviser(p1, "News from Afar")
      .favor(p1, 2).banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.SearchModifierSelection,
      Vector(NewsFromAfar.id, Augury.id))).after(MenuPlay.start(p1, ActionRef.Search))
    Script("two-picks", service, p1)

  /** Wild Cry at p1's site and a facedown adviser: the play picks Wild Cry,
    * then plays the adviser by default. */
  def facedownWithCry(using munit.Location): Script =
    val conclave = CatalogNames.denizen("Wizard's Conclave")
    val (service, act) = atTable("facedown-cry", tableWithOnePawnPerSite
      .denizen("Wild Cry", "Ancient City")
      .adviser(p1, conclave, facedown = true).banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.SearchModifierSelection,
      Vector(WildCry.id))).after(MenuPlay.start(p1, ActionRef.PlayFacedownAdviser))
    Script("facedown-cry", service, p1)

  /** Tents as p1's adviser, with 1 favor to pay for it. The Travel picks
    * Tents, its only modifier, so it starts by itself, then goes to the
    * other site of Ancient City's region, which Tents makes free. */
  def travelWithTents(using munit.Location): Script =
    val (service, act) = atTable("tents", tableWithOnePawnPerSite
      .adviser(p1, "Tents").favor(p1, 1).banditsRefilled)
    val map = act.ready.game.current.map
    val home = Look(act.ready).pawn(p1)
    val nearby = map.inPlay.find(site => site != home &&
      map.regionOf(site) == map.regionOf(home)).getOrElse(
      munit.Assertions.fail("Ancient City's region has one site"))
    val destination: Answers = {
      case park if park.decisionId == TravelProcedure.destinationDecisionId =>
        TravelPlay.destination(nearby)
    }
    act.withAnswers(destination.orElse(ModifierPlay.answers(
      PowerWindow.TravelModifierSelection, Vector(Tents.id))))
      .after(ParkedServiceFixture.startTravel(p1))
    Script("tents", service, p1)

  /** Gambling Hall at p1's site, used in Act by a player holding two favor.
    * Gambling Hall rolls 4 defense dice and Dice.steady makes each two
    * shields, so they total 8, and the richest bank is chosen so the gain is
    * never empty. */
  def gamblingHall(using munit.Location): Script =
    val card = CatalogNames.denizen("Gambling Hall")
    val table = tableWithOnePawnPerSite.denizen("Gambling Hall", "Ancient City")
      .favor(p1, 2).banditsRefilled
    val richest = Suit.all.maxBy(suit => table.ready.banks.favor(suit))
    val (service, act) = atTable("gambling-hall", table)
    act.withAnswers {
      case park if park.decisionId == GamblingHall.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.FavorBank(richest))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(GamblingHall.id), Vector(DecisionOptionRef.Denizen(card))))
    Script("gambling-hall", service, p1)

  /** Wolves at p1's site, used in Act by a player holding two secrets. The
    * board question parks; the answer kills one of the other player's
    * warbands with the most of them. */
  def wolves(using munit.Location): Script =
    val card = CatalogNames.denizen("Wolves")
    // p3 has fewer warbands on its board, so p2 is the one with the most.
    val table = tableWithOnePawnPerSite.denizen("Wolves", "Ancient City")
      .secrets(p1, faceUp = 2).boardWarbands(p3, 2).banditsRefilled
    val victim = table.ready.game.current.players.filter(_.player != p1)
      .maxBy(_.board.warbands).player
    val (service, act) = atTable("wolves", table)
    act.withAnswers {
      case park if park.decisionId == Wolves.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(victim))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(Wolves.id), Vector(DecisionOptionRef.Denizen(card))))
    Script("wolves", service, p1)

  /** Oracle at p1's site, used in Act by a player holding three secrets.
    * Vision of Sanctuary tops the world deck; Oracle draws it, and the actor
    * keeps it as a facedown adviser. */
  def oracle(using munit.Location): Script =
    oracle("oracle", PlayedAs.Facedown)

  /** Oracle, its drawn Vision of Sanctuary played as `placement` names. */
  def oracle(name: String, placement: PlayedAs)(using munit.Location): Script =
    val card = CatalogNames.denizen("Oracle")
    val (service, act) = atTable(name, tableWithOnePawnPerSite
      .denizen("Oracle", "Ancient City").secrets(p1, faceUp = 3)
      .worldDeckTop(VisionRules.Sanctuary).banditsRefilled)
    act.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(PlacementPicks.pick(park.decide, placement))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(Oracle.id), Vector(DecisionOptionRef.Denizen(card))))
    Script(name, service, p1)

  /** Barbed Net in p1's play area and two relics at p1's site, used in Act
    * by a player holding four secrets. The relic question parks; the answer
    * takes Dowsing Sticks. */
  def barbedNet(using munit.Location): Script =
    val net = CatalogNames.relic("Barbed Net")
    val target = CatalogNames.relic("Dowsing Sticks")
    val (service, act) = atTable("barbed-net", tableWithOnePawnPerSite
      .relic(p1, "Barbed Net")
      .relicAt("Dowsing Sticks", "Ancient City")
      .relicAt("Sticky Fire", "Ancient City")
      .secrets(p1, faceUp = 4).banditsRefilled)
    act.withAnswers {
      case park if park.decisionId == BarbedNet.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Relic(target))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(BarbedNet.id), Vector(DecisionOptionRef.Relic(net))))
    Script("barbed-net", service, p1)

  /** Hunger faceup with p2, added after their facedown adviser (so that
    * adviser is slot 1), whose Wake begins when p1 rests. The forced step
    * parks for them. Their only candidate is their own facedown adviser,
    * which the default answer buries, and nothing else keeps Wake open, so
    * it ends and the log ends with Act. The script's actor is Hunger's
    * holder, p2. */
  def hunger(using munit.Location): Script =
    val (service, act) = atTable("hunger", tableWithOnePawnPerSite
      .adviser(p2, "Birdsong", facedown = true).adviser(p2, "Hunger")
      .banditsRefilled)
    act.after(MenuPlay.start(p1, PhaseTransitionRef.BeginRest))
    Script("hunger", service, p2)

  /** Family Heirloom held by p1 as a facedown adviser, then played faceup as
    * an adviser: its When Played draw is kept or put on the bottom by
    * `choice`. Dowsing Sticks tops the relic deck. */
  def familyHeirloom(name: String, choice: DecisionOptionRef.Button)
      (using munit.Location): Script =
    val (service, act) = atTable(name, tableWithOnePawnPerSite
      .adviser(p1, "Family Heirloom", facedown = true)
      .relicDeckTop("Dowsing Sticks").banditsRefilled)
    act.withAnswers {
      case park if park.decisionId.startsWith(ActionLines.PlacePrefix) =>
        ChooseOneAnswer(PlacementPicks.pick(park.decide, PlayedAs.Faceup))
      case park if park.decisionId == FamilyHeirloom.decisionId =>
        ChooseOneAnswer(choice)
    }.after(MenuPlay.start(p1, ActionRef.PlayFacedownAdviser))
    Script(name, service, p1)

  def heirloomKept(using munit.Location): Script =
    familyHeirloom("heirloom-kept", FamilyHeirloom.keep)

  def heirloomReturned(using munit.Location): Script =
    familyHeirloom("heirloom-returned", FamilyHeirloom.bottom)

  /** Every script by its stream name, for the suites that hold for each. */
  val named: Vector[(String, () => Script)] = Vector(
    "woken" -> (() => woken), "round" -> (() => round),
    "oathkeeper" -> (() => oathkeeperTie), "search" -> (() => search),
    "augury" -> (() => augury), "facedown-adviser" -> (() => facedownAdviser),
    "muster" -> (() => muster), "trade" -> (() => trade),
    "take-wealth" -> (() => takeWealth),
    "recover-failed" -> (() => recoverFailed),
    "recover-succeeded" -> (() => recoverSucceeded),
    "reveal-relic" -> (() => revealRelic),
    "peek-relics" -> (() => peekRelics),
    "warbands-moved" -> (() => warbandsMoved), "forge" -> (() => forge),
    "banners" -> (() => banners), "raid" -> (() => raid),
    "negotiation-declined" -> (() => negotiationDeclined),
    "negotiation-agreed" -> (() => negotiationAgreed),
    "negotiation-disclosed" -> (() => negotiationDisclosed),
    "use-power" -> (() => silverTongue),
    "gambling-hall" -> (() => gamblingHall), "wolves" -> (() => wolves),
    "oracle" -> (() => oracle), "barbed-net" -> (() => barbedNet),
    "hunger" -> (() => hunger),
    "menu-wealth" -> (() => wealthFromMenu),
    "menu-rest" -> (() => restFromMenu),
    "queued-raid-skip" -> (() => queuedRaidThenSkip),
    "heirloom-kept" -> (() => heirloomKept),
    "heirloom-returned" -> (() => heirloomReturned))

  def all: Vector[Script] = named.map(_._2())
