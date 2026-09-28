package oathdigital.model

class WorldModelSuite extends munit.FunSuite:
  private def player(id: String, lineage: String): PlayerState = PlayerState(
    PlayerId(id), LineageId(lineage), None,
    PlayerBoardState(0, 0, 0, 0, SupplyTrack.full), Vector.empty,
    Vector.empty, None)

  test("site rule derives players and shared rulers directly from forces"):
    val red = player("red-player", "red-lineage")
    val blue = player("blue-player", "blue-lineage")
    val players = Vector(red, blue)

    assertEquals(SiteRule.ruler(SiteForces.Empty, players),
      Right(SiteRuler.Unruled))
    assertEquals(SiteRule.ruler(
      SiteForces.Occupied(ForceKind.Bandit, 2), players),
      Right(SiteRuler.Bandits))
    assertEquals(SiteRule.ruler(
      SiteForces.Occupied(ForceKind.Imperial, 1), players),
      Right(SiteRuler.Empire))
    assertEquals(SiteRule.ruler(SiteForces.Occupied(
      ForceKind.Exile(red.lineage), 3), players),
      Right(SiteRuler.Player(red.player)))
    assertEquals(SiteRule.ruledBy(SiteForces.Occupied(
      ForceKind.Exile(red.lineage), 1), players, red.player), Right(true))

  test("same-ruler and enemy semantics support Campaign target derivation"):
    val red = player("red-player", "red-lineage")
    val players = Vector(red)
    val redForces = SiteForces.Occupied(ForceKind.Exile(red.lineage), 1)

    assertEquals(SiteRule.sameRuler(redForces,
      SiteForces.Occupied(ForceKind.Exile(red.lineage), 4), players), Right(true))
    assertEquals(SiteRule.sameRuler(SiteForces.Occupied(ForceKind.Imperial, 1),
      SiteForces.Occupied(ForceKind.Imperial, 2), players), Right(true))
    assertEquals(SiteRule.sameRuler(SiteForces.Empty, SiteForces.Empty, players),
      Right(false))
    assert(SiteRule.enemies(SiteRuler.Player(red.player), SiteRuler.Empire))
    assert(!SiteRule.enemies(SiteRuler.Bandits, SiteRuler.Bandits))
    assert(!SiteRule.enemies(SiteRuler.Unruled, SiteRuler.Empire))

  test("site rule rejects unknown and duplicate current lineage mappings"):
    val red = player("red-player", "red-lineage")
    val duplicate = player("other-red-player", "red-lineage")
    val forces = SiteForces.Occupied(ForceKind.Exile(red.lineage), 1)

    assertEquals(SiteRule.ruler(forces, Vector.empty),
      Left(SiteRuleError.UnknownLineage(red.lineage)))
    assertEquals(SiteRule.ruler(forces, Vector(red, duplicate)),
      Left(SiteRuleError.DuplicateCurrentLineage(
        red.lineage, Vector(red.player, duplicate.player))))
