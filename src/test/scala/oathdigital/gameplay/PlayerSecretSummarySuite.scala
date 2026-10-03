package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class PlayerSecretSummarySuite extends munit.FunSuite:
  import FirstGameSetupFixture._
  private val actor = p1
  private val inactive = p2
  // A card whose power these tests do not use, on p1's site or p2's board.
  private val card = CatalogNames.denizen("Wrestlers")
  private val relic = CatalogNames.relic("Sticky Fire")

  /** p1 at Ancient City with `available` faceup and `facedown` facedown
    * secrets and no adviser or relic. Ancient City holds Wrestlers with
    * `siteCommitted` secrets on it, or nothing when that is 0. */
  private def withState(available: Int, facedown: Int,
      siteCommitted: Int): Table =
    val table = Table.start.secrets(actor, faceUp = available,
      faceDown = facedown)
    if siteCommitted == 0 then table
    else table.denizen(card, at = "Ancient City")
      .tokens(card, secrets = siteCommitted)

  test("derived secret accounting reports available facedown committed and total"):
    val examples = Vector(
      withState(1, 0, 0) -> PlayerSecretSummary(1, 0, 0),
      withState(0, 0, 1) -> PlayerSecretSummary(0, 0, 1),
      withState(0, 1, 0) -> PlayerSecretSummary(0, 1, 0),
      withState(1, 0, 1) -> PlayerSecretSummary(1, 0, 1))
    examples.foreach { case (table, expected) =>
      assertEquals(accepted(PlayerSecretSummary.derive(table.ready, actor)),
        expected)
    }

  test("valid inactive player has zero commitments and ignores ruled-site tokens"):
    // p2 rules Ancient City, where Wrestlers carries p1's committed secret.
    val ruledByInactive = withState(0, 0, 1)
      .warbandsAt("Ancient City", inactive, 1).ready
    assertEquals(accepted(PlayerSecretSummary.derive(ruledByInactive,
      inactive)).committed, 0)
    assertEquals(accepted(PlayerSecretSummary.derive(ruledByInactive,
      actor)).committed, 1)

  test("active total includes a committed secret on another player's adviser"):
    val corrupt = Table.start.adviser(inactive, card)
      .tokens(card, secrets = 1).ready
    assertEquals(accepted(PlayerSecretSummary.derive(corrupt,
      actor)).committed, 1)
    assertEquals(accepted(PlayerSecretSummary.derive(corrupt,
      inactive)).committed, 0)

  test("active total includes a committed secret on another player's relic"):
    val corrupt = Table.start.relic(inactive, relic)
      .tokens(relic, secrets = 2).ready
    assertEquals(accepted(PlayerSecretSummary.derive(corrupt,
      actor)).committed, 2)
    assertEquals(accepted(PlayerSecretSummary.derive(corrupt,
      inactive)).committed, 0)
    val projected = new oathdigital.application.GameProjector(catalog)
      .projectPublic("cross-player-secrets", oathdigital.application.LoadedGame(
        OathState.Ready(corrupt), 9L))
    assertEquals(projected.playerBoards.find(_.playerId == actor.value)
      .map(_.committedSecrets), Some(2))
