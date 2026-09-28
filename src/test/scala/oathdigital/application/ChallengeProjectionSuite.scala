package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

class ChallengeProjectionSuite extends munit.FunSuite:
  private def projection(board: ReadyGame, viewer: PlayerId) =
    new GameProjector(catalog).project("challenge", LoadedGame(Ready(board), 9),
      viewer)

  /** p1, in Act, has 6 favor, 6 faceup and 4 facedown secrets and the
    * start's 7 Supply. */
  private def challenger: Table = Table.start
    .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)

  test("a legal Challenge is offered as a start control, not a board-target selection"):
    val shown = projection(challenger.peoplesFavor(None, favor = 2).ready, p1)
    assert(shown.legalControls.contains("beginChallenge"))
    assert(!shown.boardTargetActions.exists(_.actionKind == "challenge"))

  test("no Supply, or no strictly greater resources, withdraws the Challenge control"):
    val broke = challenger.supply(p1, 0).peoplesFavor(None, favor = 2).ready
    assert(!projection(broke, p1).legalControls.contains("beginChallenge"))
    // 6 favor does not exceed People's Favor's 6, and no faceup secrets.
    val equal = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 6).ready
    assert(!projection(equal, p1).legalControls.contains("beginChallenge"))

  test("a held banner with resources offers Place Banner Resource"):
    val held = challenger.darkestSecret(Some(p1), secrets = 1).ready
    val unheld = challenger.darkestSecret(None, secrets = 1).ready
    assert(projection(held, p1).legalControls.contains("placeBannerResource"))
    assert(!projection(unheld, p1).legalControls.contains("placeBannerResource"))
