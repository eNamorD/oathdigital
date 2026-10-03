package oathdigital.model

import oathdigital.testkit.Table
import oathdigital.testkit.Unwrap.present

class PlayerForceKindSuite extends munit.FunSuite:
  private val ready = Table.start.ready
  private val actor = present(
    ready.game.current.players.find(_.player == Table.p1), "player p1")

  // No Table step sets a lineage role or drops a lineage, so the lineage is
  // patched by hand.
  private def withRole(role: Role): ReadyGame =
    val lineage = ready.game.campaign.lineages(actor.lineage)
    ready.copy(game = ready.game.copy(campaign = ready.game.campaign.copy(
      lineages = ready.game.campaign.lineages.updated(actor.lineage,
        lineage.copy(role = role)))))

  test("an Exile's warbands are their lineage's Exile kind"):
    assertEquals(PlayerForceKind.of(withRole(Role.Exile), actor),
      Some(ForceKind.Exile(actor.lineage)))

  test("a Citizen's and a Chancellor's warbands are the shared Imperial kind"):
    Vector(Role.Citizen, Role.Chancellor).foreach { role =>
      assertEquals(PlayerForceKind.of(withRole(role), actor),
        Some(ForceKind.Imperial), role.toString)
    }

  test("a player whose lineage is unknown has no force kind"):
    // No Table step sets a lineage role or drops a lineage.
    val orphan = ready.copy(game = ready.game.copy(campaign =
      ready.game.campaign.copy(lineages =
        ready.game.campaign.lineages - actor.lineage)))
    assertEquals(PlayerForceKind.of(orphan, actor), None)
