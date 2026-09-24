package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

class PlaceBannerResourceProcedureSuite extends munit.FunSuite {
  import ChallengeFixture._

  private val rules = new OathRules(catalog)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  /** The actor holds Darkest Secret with one secret on it. */
  private def holding: (ReadyGame, PlayerState) = {
    val (base, actor) = ChallengeFixture.ready(banner = Banner.DarkestSecret,
      resources = 1)
    base.updateCurrent(current => current.copy(banners = current.banners.copy(
      darkestSecret = current.banners.darkestSecret.copy(
        holder = Some(actor.player))))) -> actor
  }

  private def start(board: ReadyGame) = rules.startWalker(Ready(board),
    ActionRef.PlaceBannerResource, active(board))

  test("placing resources moves faceup secrets onto the held banner for no Supply") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("a held banner must allow placing"))
    val banner = rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("the banner must be accepted"))
    val done = rules.resolveWalker(banner.state, actor.player,
      "place-banner-resource.amount", DecisionAnswer.ChooseAmountAnswer(2))
      .getOrElse(fail("the amount must be accepted"))
    val Ready(after) = done.state: @unchecked
    val player = after.game.current.players.find(_.player == actor.player).get
    assertEquals(player.board.faceUpSecrets, actor.board.faceUpSecrets - 2)
    assertEquals(player.board.faceDownSecrets, actor.board.faceDownSecrets)
    assertEquals(player.board.supply, actor.board.supply)
    assertEquals(after.game.current.banners.darkestSecret.secrets, 3)
    assertEquals(after.game.current.walkerPending, None)
  }

  test("the amount ranges from one to the actor's relevant resources") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("must start"))
    val banner = rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("banner"))
    Vector(0, actor.board.faceUpSecrets + 1).foreach(amount =>
      assert(rules.resolveWalker(banner.state, actor.player,
        "place-banner-resource.amount",
        DecisionAnswer.ChooseAmountAnswer(amount)).isLeft, s"amount $amount"))
    assert(rules.resolveWalker(banner.state, actor.player,
      "place-banner-resource.amount",
      DecisionAnswer.ChooseAmountAnswer(actor.board.faceUpSecrets)).isRight)
  }

  test("the amount question names the banner and the resource it takes") {
    // A banner is named as it is printed, and each takes one resource, so
    // the question says which rather than "resources on darkest-secret".
    assertEquals(oathdigital.gameplay.actions.challenge
      .PlaceBannerResourceProcedure.amountHeading(Banner.DarkestSecret),
      "Place secrets on Darkest Secret")
    assertEquals(oathdigital.gameplay.actions.challenge
      .PlaceBannerResourceProcedure.amountHeading(Banner.PeoplesFavor),
      "Place favor on People's Favor")
  }

  test("only a banner the actor holds is offered") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("must start"))
    assert(rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))).isLeft)
  }

  test("a start with no held banner or no resources is rejected") {
    val (unheld, _) = ChallengeFixture.ready(resources = 1)
    assertEquals(start(unheld),
      Left(NoPlayableOption("place-banner-resource")))
    val (board, actor) = holding
    val broke = board.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == actor.player)
        p.copy(board = p.board.copy(faceUpSecrets = 0)) else p)))
    assertEquals(start(broke), Left(NoPlayableOption("place-banner-resource")))
  }
}
