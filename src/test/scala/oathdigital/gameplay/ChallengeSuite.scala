package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{BannerRules, ChallengeCommand, ChallengeRules}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathState.Ready

class ChallengeSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready(resources: Int, favor: Int = 6, faceup: Int = 6, facedown: Int = 4,
      banner: Banner = Banner.PeoplesFavor,
      holder: Option[PlayerId] = None): (ReadyGame, PlayerState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor0 = base.game.current.players.find(_.player == base.game.current.turn.activePlayer).get
    val actor = actor0.copy(board = actor0.board.copy(favor = favor,
      faceUpSecrets = faceup, faceDownSecrets = facedown))
    val banners = banner match {
      case Banner.PeoplesFavor => base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(holder = holder, favor = resources))
      case Banner.DarkestSecret => base.game.current.banners.copy(darkestSecret =
        base.game.current.banners.darkestSecret.copy(holder = holder, secrets = resources))
    }
    val value = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player) actor else p),
      banners = banners, turn = base.game.current.turn.copy(phase = Phase.Act))))
    value -> actor
  }

  test("eligibility uses strict favor and faceup-secret comparisons") {
    val (pf, actor) = ready(favor = 2, resources = 2)
    assert(ChallengeRules.validate(catalog, pf, actor.player, Banner.PeoplesFavor).isLeft)
    val (ds, secretActor) = ready(faceup = 2, facedown = 20,
      banner = Banner.DarkestSecret, resources = 2)
    assert(ChallengeRules.validate(catalog, ds, secretActor.player,
      Banner.DarkestSecret).isLeft)
  }

  test("legal unclaimed banners project as shared-bank targets and illegal banners do not") {
    val (base, actor) = ready(resources = 2, favor = 6, faceup = 0,
      banner = Banner.PeoplesFavor, holder = None)
    val projection = new GameProjector(catalog).project("challenge-targets",
      LoadedGame(Ready(base), 9), actor.player)
    val challenge = projection.boardTargetActions.find(_.actionKind == "challenge").get
    assertEquals(challenge.candidates.map(_.target), Vector(
      oathdigital.protocol.projection.BoardTargetRefProjection.PlayerBanner(
        "shared-bank", "peoples-favor")))
    assertEquals(projection.banners.map(_.key).toSet,
      Set("peoples-favor", "darkest-secret"))
  }

  test("People's Favor resolves every least-bank tie leftmost and proceeds to replacement") {
    val (initial, actor) = ready(resources = 2)
    val base = initial.copy(game = initial.game.copy(campaign =
      initial.game.campaign.copy(oathkeeperGoal = OathkeeperGoal.ThePeople)))
    val id = DecisionId("challenge-favor")
    val started = rules.handle(Ready(base), ChallengeCommand.Begin(
      actor.player, id, Banner.PeoplesFavor)).toOption.get
    val Ready(pending0) = started.state: @unchecked
    assertEquals(pending0.game.current.banners.peoplesFavor.holder, None)
    val challenge = pending0.game.current.pending.get.asInstanceOf[PendingProcedure.Challenge]
    assertEquals(challenge.remainingRibbonResources, 0)
    val expectedOrder = BannerRules.raidFavorReturn(base.banks.favor, 2)
    assertEquals(challenge.favorReturned, expectedOrder)
    assertEquals(started.events.collectFirst {
      case e: BannerChallengeStarted => e.automaticFavorReturns
    }, Some(expectedOrder))
    val completed = rules.handle(started.state, ChallengeCommand.Complete(
      actor.player, id, 3)).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 3)
    assertEquals(after.game.current.players.find(_.player == actor.player).get.board.favor,
      actor.board.favor - 3)
    assertEquals(completed.events.last, OathkeeperChanged(Some(actor.player)))
    assertEquals(after.game.current.title.holder, Some(actor.player))
  }

  test("valid setup history replays exactly through People's Favor completion") {
    val wealthSite = catalog.sites.find(_.startingResources.favor > 0).get.id
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val replayPlan = plan.copy(orderedSites = orderedSites)
    val (setupState, setupEvents) = execute(setup, replayPlan)
    val active = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val wealth = rules.startWalker(setupState, ActionRef.TakeWealth, active,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val act = rules.startWalker(wealth.state, PhaseTransitionRef.EndWake, active)
      .toOption.get
    val started = rules.handle(act.state, ChallengeCommand.Begin(active,
      DecisionId("replay-challenge"), Banner.PeoplesFavor)).toOption.get
    val completed = rules.handle(started.state, ChallengeCommand.Complete(active,
      DecisionId("replay-challenge"), 2)).toOption.get
    val events = setupEvents ++ wealth.events ++ act.events ++ started.events ++ completed.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, completed.state)
  }

  test("base banner placement moves only faceup resources and costs no Supply") {
    val (base0, actor) = ready(banner = Banner.DarkestSecret, resources = 1,
      holder = None)
    val base = base0.copy(game = base0.game.copy(current = base0.game.current.copy(
      banners = base0.game.current.banners.copy(darkestSecret =
        base0.game.current.banners.darkestSecret.copy(holder = Some(actor.player))))))
    val transition = rules.handle(Ready(base), ChallengeCommand.PlaceResource(
      actor.player, Banner.DarkestSecret, 2)).toOption.get
    val Ready(after) = transition.state: @unchecked
    val player = after.game.current.players.find(_.player == actor.player).get
    assertEquals(player.board.faceUpSecrets, actor.board.faceUpSecrets - 2)
    assertEquals(player.board.faceDownSecrets, actor.board.faceDownSecrets)
    assertEquals(player.board.supply, actor.board.supply)
    assertEquals(after.game.current.banners.darkestSecret.secrets, 3)
  }

  test("Wandering Flame places all bank-held secrets and half enemy-held secrets") {
    val (bank, actor) = ready(banner = Banner.DarkestSecret, resources = 3)
    val id = DecisionId("challenge-secret-bank")
    var transition = rules.handle(Ready(bank), ChallengeCommand.Begin(
      actor.player, id, Banner.DarkestSecret)).toOption.get
    while (transition.state.asInstanceOf[Ready].value.game.current.pending
        .exists(_.asInstanceOf[PendingProcedure.Challenge].remainingRibbonResources > 0)) {
      val r = transition.state.asInstanceOf[Ready].value
      val p = r.game.current.pending.get.asInstanceOf[PendingProcedure.Challenge]
      val site = BannerRules.leastSites(r.game.current, p.secretsPlaced).head
      transition = rules.handle(transition.state, ChallengeCommand.ChooseSecretSite(
        actor.player, id, site)).toOption.get
    }
    val completed = rules.handle(transition.state, ChallengeCommand.Complete(
      actor.player, id, 4)).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.map.sites.values.map(_.tokens.secrets).sum -
      bank.game.current.map.sites.values.map(_.tokens.secrets).sum, 3)

    val enemy = bank.game.current.players.find(_.player != actor.player).get
    val colocated = enemy.copy(pawnSite = actor.pawnSite)
    val enemyHeld = bank.copy(game = bank.game.copy(current = bank.game.current.copy(
      players = bank.game.current.players.map(p => if (p.player == enemy.player) colocated else p),
      banners = bank.game.current.banners.copy(darkestSecret =
        bank.game.current.banners.darkestSecret.copy(holder = Some(enemy.player), secrets = 3)))))
    val started = rules.handle(Ready(enemyHeld), ChallengeCommand.Begin(
      actor.player, DecisionId("enemy-secret"), Banner.DarkestSecret)).toOption.get
    val Ready(pending) = started.state: @unchecked
    val procedure = pending.game.current.pending.get.asInstanceOf[PendingProcedure.Challenge]
    assertEquals(procedure.secretsPlaced.size + procedure.remainingRibbonResources, 1)
  }

  test("held Darkest Secret completion returns the remainder to the prior holder") {
    val (bank, actor) = ready(banner = Banner.DarkestSecret, resources = 3)
    val enemy0 = bank.game.current.players.find(_.player != actor.player).get
    val enemy = enemy0.copy(pawnSite = actor.pawnSite)
    val enemyHeld = bank.copy(game = bank.game.copy(current = bank.game.current.copy(
      players = bank.game.current.players.map(p => if (p.player == enemy.player) enemy else p),
      banners = bank.game.current.banners.copy(darkestSecret =
        bank.game.current.banners.darkestSecret.copy(
          holder = Some(enemy.player), secrets = 3)))))
    val id = DecisionId("enemy-secret")
    var transition = rules.handle(Ready(enemyHeld), ChallengeCommand.Begin(
      actor.player, id, Banner.DarkestSecret)).toOption.get
    while (transition.state.asInstanceOf[Ready].value.game.current.pending
        .exists(_.asInstanceOf[PendingProcedure.Challenge].remainingRibbonResources > 0)) {
      val r = transition.state.asInstanceOf[Ready].value
      val p = r.game.current.pending.get.asInstanceOf[PendingProcedure.Challenge]
      val site = BannerRules.leastSites(r.game.current, p.secretsPlaced).head
      transition = rules.handle(transition.state, ChallengeCommand.ChooseSecretSite(
        actor.player, id, site)).toOption.get
    }
    val placed = transition.state.asInstanceOf[Ready].value.game.current.pending.get
      .asInstanceOf[PendingProcedure.Challenge].secretsPlaced.size
    val completed = rules.handle(transition.state, ChallengeCommand.Complete(
      actor.player, id, 4)).toOption.get
    val Ready(after) = completed.state: @unchecked
    // The prior holder regains (prior - placed) faceup secrets.
    val priorHolder = after.game.current.players.find(_.player == enemy.player).get
    assertEquals(priorHolder.board.faceUpSecrets, enemy.board.faceUpSecrets + (3 - placed))
    // Sites gain exactly the placed secrets.
    assertEquals(after.game.current.map.sites.values.map(_.tokens.secrets).sum -
      enemyHeld.game.current.map.sites.values.map(_.tokens.secrets).sum, placed)
    // The banner ends at the challenger's strictly greater replacement.
    assertEquals(after.game.current.banners.darkestSecret.holder, Some(actor.player))
    assertEquals(after.game.current.banners.darkestSecret.secrets, 4)
    // The challenger paid the replacement from faceup secrets.
    val challenger = after.game.current.players.find(_.player == actor.player).get
    assertEquals(challenger.board.faceUpSecrets, actor.board.faceUpSecrets - 4)
  }

  test("pending Challenge projection and controls are owner-only") {
    val (base, actor) = ready(resources = 3, banner = Banner.DarkestSecret)
    val other = base.game.current.players.find(_.player != actor.player).get.player
    val started = rules.handle(Ready(base), ChallengeCommand.Begin(actor.player,
      DecisionId("private-challenge"), Banner.DarkestSecret)).toOption.get
    val loaded = LoadedGame(started.state, 12)
    val projector = new GameProjector(catalog)
    val owner = projector.project("challenge", loaded, actor.player)
    val waiting = projector.project("challenge", loaded, other)
    val public = projector.projectPublic("challenge", loaded)
    assert(owner.challenge.nonEmpty)
    assert(owner.legalControls.forall(Set("chooseChallengeSecretSite",
      "completeChallenge")))
    assertEquals(waiting.challenge, None)
    assertEquals(waiting.legalControls, Vector.empty)
    assertEquals(waiting.phase, "challenge-waiting")
    assertEquals(public.challenge, None)
    assertEquals(public.legalControls, Vector.empty)
  }
}
