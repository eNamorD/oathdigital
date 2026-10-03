package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** Covers `LegalActionProjector.recoverEligible`: projection delegates to the
  * same Recover procedure builder as command execution, and relic availability
  * does not gate the action.
  */
class RecoverEligibilitySuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)

  /** p1, in Act, stands at Broken Peaks, whose Recover difficulty is 4. On
    * the quiet table the site holds no relic and no card, Catacombs
    * included. */
  private val recoverSite = CatalogNames.site("Broken Peaks")
  private def atRecoverSite: Table = Table.start.pawn(p1, at = recoverSite)

  private def legalControls(ready: ReadyGame, actor: PlayerId): Vector[String] =
    projector.project("recover-eligibility", LoadedGame(Ready(ready), 1), actor)
      .legalControls

  test("beginRecover is offered without a relic or Catacombs"):
    assertEquals(RecoverRules.difficulty(catalog, recoverSite), Some(4))
    assert(legalControls(atRecoverSite.ready, p1).contains("beginRecover"))

  test("beginRecover is not offered without the Supply to pay for it"):
    def withSupply(supply: Int) = atRecoverSite.supply(p1, supply).ready
    assert(legalControls(withSupply(1), p1).contains("beginRecover"))
    assert(!legalControls(withSupply(0), p1).contains("beginRecover"))
