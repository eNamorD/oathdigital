package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{MenuPlay, Table}
import oathdigital.testkit.Table.p1

class ChallengeProjectionSuite extends munit.FunSuite:
  /** p1, in Act, has 6 favor, 6 faceup and 4 facedown secrets and the
    * start's 7 Supply. */
  private def challenger: Table = Table.start
    .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)

  test("a legal Challenge is available in the Act menu"):
    val shown = challenger.peoplesFavor(None, favor = 2).ready
    assert(MenuPlay.availableInMenu(catalog, shown, ActionRef.Challenge))

  test("no Supply, or no strictly greater resources, makes the Challenge " +
      "unavailable"):
    val broke = challenger.supply(p1, 0).peoplesFavor(None, favor = 2).ready
    assert(!MenuPlay.availableInMenu(catalog, broke, ActionRef.Challenge))
    // 6 favor does not exceed People's Favor's 6, and no faceup secrets.
    val equal = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 6).ready
    assert(!MenuPlay.availableInMenu(catalog, equal, ActionRef.Challenge))

  test("a held banner with resources makes Place Banner Resource available"):
    val held = challenger.darkestSecret(Some(p1), secrets = 1).ready
    val unheld = challenger.darkestSecret(None, secrets = 1).ready
    assert(MenuPlay.availableInMenu(catalog, held,
      ActionRef.PlaceBannerResource))
    assert(!MenuPlay.availableInMenu(catalog, unheld,
      ActionRef.PlaceBannerResource))
