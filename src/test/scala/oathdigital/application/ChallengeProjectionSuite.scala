package oathdigital.application

import oathdigital.gameplay.ChallengeFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ChallengeProjectionSuite extends munit.FunSuite {
  import ChallengeFixture._

  private def projection(board: ReadyGame, viewer: PlayerId) =
    new GameProjector(catalog).project("challenge", LoadedGame(Ready(board), 9),
      viewer)

  test("a legal Challenge is offered as a start control, not a board-target selection") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val shown = projection(board, actor.player)
    assert(shown.legalControls.contains("beginChallenge"))
    assert(!shown.boardTargetActions.exists(_.actionKind == "challenge"))
  }

  test("no Supply, or no strictly greater resources, withdraws the Challenge control") {
    val (broke, actor) = ChallengeFixture.ready(resources = 2, supply = 0)
    assert(!projection(broke, actor.player).legalControls.contains("beginChallenge"))
    val (equal, actor2) = ChallengeFixture.ready(resources = 6, faceup = 0)
    assert(!projection(equal, actor2.player).legalControls.contains("beginChallenge"))
  }

  test("a held banner with resources offers Place Banner Resource") {
    val (base, actor) = ChallengeFixture.ready(banner = Banner.DarkestSecret,
      resources = 1)
    val held = base.updateCurrent(current => current.copy(banners =
      current.banners.copy(darkestSecret = current.banners.darkestSecret.copy(
        holder = Some(actor.player)))))
    assert(projection(held, actor.player).legalControls
      .contains("placeBannerResource"))
    assert(!projection(base, actor.player).legalControls
      .contains("placeBannerResource"))
  }
}
