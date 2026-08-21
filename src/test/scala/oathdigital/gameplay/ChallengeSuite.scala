package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.{BannerRules, ChallengeCommand, ChallengeRules}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathState.Ready

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

  test("People's Favor tie choices stay pending and replacement is atomic") {
    val (base, actor) = ready(resources = 2)
    val id = DecisionId("challenge-favor")
    val started = rules.handle(Ready(base), ChallengeCommand.Begin(
      actor.player, id, Banner.PeoplesFavor)).toOption.get
    val Ready(pending0) = started.state: @unchecked
    assertEquals(pending0.game.current.banners.peoplesFavor.holder, None)
    val choice1 = BannerRules.leastFavorBanks(pending0.support.favorBanks).head
    val once = rules.handle(started.state, ChallengeCommand.ChooseFavorBank(
      actor.player, id, choice1)).toOption.get
    val Ready(pending1) = once.state: @unchecked
    val challenge = pending1.game.current.pending.get.asInstanceOf[PendingProcedure.Challenge]
    val resolved = if (challenge.remainingRibbonResources == 0) once else {
      val suit = BannerRules.leastFavorBanks(BannerRules.addFavor(
        pending1.support.favorBanks, challenge.favorReturned)).head
      rules.handle(once.state, ChallengeCommand.ChooseFavorBank(
        actor.player, id, suit)).toOption.get
    }
    val completed = rules.handle(resolved.state, ChallengeCommand.Complete(
      actor.player, id, 3)).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 3)
    assertEquals(after.game.current.players.find(_.player == actor.player).get.board.favor,
      actor.board.favor - 3)
    val replayed = new EventReplayEngine(rules).replay(
      (started.events ++ once.events ++ (if (resolved eq once) Vector.empty else resolved.events) ++
        completed.events).zipWithIndex.map { case (e, i) => RecordedEvent(i.toLong, e) })
    assert(replayed.isLeft) // setup-less gameplay streams are rejected at event zero.
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
}
