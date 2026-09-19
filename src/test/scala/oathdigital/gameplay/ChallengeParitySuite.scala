package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{BannerRules, ChallengeCommand}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The legacy Challenge and the walker Challenge, run on the same board and
  * compared on the state they leave and on what each viewer is shown. Legacy
  * asked a tie question the walker no longer asks, so the legacy run answers
  * it with the first tied site, which is the choice the walker's `ChooseMany`
  * is answered with here. Parity is possible only in the states legacy
  * accepts, so every fixture is the first-game one.
  */
class ChallengeParitySuite extends munit.FunSuite {
  import ChallengeFixture._

  private val rules = new OathRules(catalog)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def legacy(board: ReadyGame, banner: Banner, amount: Int): OathState = {
    val actor = active(board)
    val id = DecisionId("parity")
    var transition = rules.handle(Ready(board),
      ChallengeCommand.Begin(actor, id, banner))
      .getOrElse(fail("legacy Begin must succeed"))
    def pending = ready(transition.state).game.current.pending.collect {
      case challenge: PendingProcedure.Challenge => challenge
    }
    while (pending.exists(_.remainingRibbonResources > 0)) {
      val site = BannerRules.leastSites(ready(transition.state).game.current,
        pending.get.secretsPlaced).head
      transition = rules.handle(transition.state,
        ChallengeCommand.ChooseSecretSite(actor, id, site))
        .getOrElse(fail("legacy site choice must succeed"))
    }
    rules.handle(transition.state, ChallengeCommand.Complete(actor, id, amount))
      .getOrElse(fail("legacy Complete must succeed")).state
  }

  private def walker(board: ReadyGame, banner: Banner, amount: Int): OathState = {
    val actor = active(board)
    val started = rules.startWalker(Ready(board), ActionRef.Challenge, actor)
      .getOrElse(fail("walker start must succeed"))
    val chosen = rules.resolveWalker(started.state, actor, "challenge.banner",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Banner(banner)))
      .getOrElse(fail("walker banner must be accepted"))
    var step = rules.resolveWalker(chosen.state, actor, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(amount))
      .getOrElse(fail("walker amount must be accepted"))
    while (ready(step.state).game.current.walkerPending.nonEmpty) {
      val current = ready(step.state).game.current
      val tied = BannerRules.leastSites(current, Vector.empty)
      val remaining = current.banners.darkestSecret.secrets
      step = rules.resolveWalker(step.state, actor, "challenge.ribbon-site",
        DecisionAnswer.ChooseManyAnswer(
          tied.take(remaining).map(DecisionOptionRef.Site(_))))
        .getOrElse(fail("walker site choice must be accepted"))
    }
    step.state
  }

  private def assertSameOutcome(board: ReadyGame, banner: Banner,
      amount: Int): Unit = {
    val expected = legacy(board, banner, amount)
    val actual = walker(board, banner, amount)
    val (a, b) = (ready(expected), ready(actual))
    assertEquals(b.game.current.players, a.game.current.players)
    assertEquals(b.game.current.banners, a.game.current.banners)
    assertEquals(b.game.current.map, a.game.current.map)
    assertEquals(b.game.current.title, a.game.current.title)
    assertEquals(b.game.current.commonCards, a.game.current.commonCards)
    assertEquals(b.banks, a.banks)
    assertEquals(b.game.campaign, a.game.campaign)
    val projector = new GameProjector(catalog)
    val viewers = Vector(active(board), enemy(board).player)
    viewers.foreach(viewer => assertEquals(
      projector.project("parity", LoadedGame(actual, 20), viewer),
      projector.project("parity", LoadedGame(expected, 20), viewer)))
    assertEquals(projector.projectPublic("parity", LoadedGame(actual, 20)),
      projector.projectPublic("parity", LoadedGame(expected, 20)))
  }

  test("People's Favor: unclaimed banner") {
    val (board, _) = ChallengeFixture.ready(resources = 2)
    assertSameOutcome(board, Banner.PeoplesFavor, 3)
  }

  test("People's Favor: enemy-held banner") {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    assertSameOutcome(enemyHolds(base, Banner.PeoplesFavor, 3),
      Banner.PeoplesFavor, 4)
  }

  test("Wandering Flame: unclaimed banner with a unique least site") {
    val (base, _) = ChallengeFixture.ready(resources = 3,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    assertSameOutcome(withSiteSecrets(base, Map(a -> 0, b -> 1, c -> 5)),
      Banner.DarkestSecret, 4)
  }

  test("Wandering Flame: unclaimed banner, fewer secrets than tied sites") {
    val (base, _) = ChallengeFixture.ready(resources = 2,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    assertSameOutcome(withSiteSecrets(base, Map(a -> 0, b -> 0, c -> 0)),
      Banner.DarkestSecret, 3)
  }

  test("Wandering Flame: enemy-held banner with every site tied") {
    val (base, _) = ChallengeFixture.ready(resources = 5,
      banner = Banner.DarkestSecret)
    assertSameOutcome(withSiteSecrets(
      enemyHolds(base, Banner.DarkestSecret, 5), Map.empty),
      Banner.DarkestSecret, 6)
  }

  test("Wandering Flame: enemy-held banner with a unique least site") {
    val (base, _) = ChallengeFixture.ready(resources = 4,
      banner = Banner.DarkestSecret)
    val Vector(a, b) = base.game.current.map.inPlay.take(2): @unchecked
    assertSameOutcome(withSiteSecrets(
      enemyHolds(base, Banner.DarkestSecret, 4), Map(a -> 0, b -> 3)),
      Banner.DarkestSecret, 5)
  }

  test("an illegal People's Favor Challenge is rejected on both paths") {
    val (base, actor) = ChallengeFixture.ready(resources = 2)
    val illegal = Vector(
      "no Supply" -> ChallengeFixture.ready(resources = 2, supply = 0)._1,
      "actor holds the banner" -> ChallengeFixture.ready(resources = 2,
        holder = Some(actor.player))._1,
      "enemy-held without co-location" ->
        enemyHolds(base, Banner.PeoplesFavor, 2, colocated = false),
      "not strictly more" -> ChallengeFixture.ready(resources = 6, favor = 6,
        faceup = 0)._1)
    illegal.foreach { case (name, board) =>
      assert(rules.handle(Ready(board), ChallengeCommand.Begin(active(board),
        DecisionId("x"), Banner.PeoplesFavor)).isLeft, s"legacy: $name")
      // The walker may still start (another banner can be legal, and the
      // banner is a decision), but it must never accept People's Favor.
      val started = rules.startWalker(Ready(board), ActionRef.Challenge,
        active(board))
      assert(started.isLeft || rules.resolveWalker(started.toOption.get.state,
        active(board), "challenge.banner", DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Banner(Banner.PeoplesFavor))).isLeft,
        s"walker: $name")
    }
  }
}
