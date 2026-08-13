package oathdigital.model

class WorldModelSuite extends munit.FunSuite {
  private def player(id: String, lineage: String): PlayerState = PlayerState(
    PlayerId(id), LineageId(lineage), None,
    PlayerBoardState(0, 0, 0, 0, SupplyTrack.full), Vector.empty,
    Vector.empty, None)

  test("site rule derives players and shared rulers directly from forces") {
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
  }

  test("same-ruler and enemy semantics support Campaign target derivation") {
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
  }

  test("site rule rejects unknown and duplicate current lineage mappings") {
    val red = player("red-player", "red-lineage")
    val duplicate = player("other-red-player", "red-lineage")
    val forces = SiteForces.Occupied(ForceKind.Exile(red.lineage), 1)

    assertEquals(SiteRule.ruler(forces, Vector.empty),
      Left(SiteRuleError.UnknownLineage(red.lineage)))
    assertEquals(SiteRule.ruler(forces, Vector(red, duplicate)),
      Left(SiteRuleError.DuplicateCurrentLineage(
        red.lineage, Vector(red.player, duplicate.player))))
  }

  test("the Atlas is one sequence with Recent and Forgotten ends") {
    val middle = AtlasEntry.EmpireDivider
    val recent = AtlasEntry.StoredSite(
      SiteId("recent"),
      Vector.empty,
      Vector.empty
    )
    val forgotten = AtlasEntry.StoredSite(
      SiteId("forgotten"),
      Vector.empty,
      Vector.empty
    )

    val atlas = AtlasState(Vector(middle, forgotten))
      .addRecent(recent)

    assertEquals(atlas.entries, Vector(recent, middle, forgotten))
    assertEquals(atlas.mostRecent, Some(recent))
    assertEquals(atlas.mostForgotten, Some(forgotten))
  }

  test("bulk Recent additions preserve supplied front-to-back order") {
    val first = AtlasEntry.StoredSite(
      SiteId("first"),
      Vector.empty,
      Vector.empty
    )
    val second = AtlasEntry.StoredSite(
      SiteId("second"),
      Vector.empty,
      Vector.empty
    )
    val atlas = AtlasState(Vector(AtlasEntry.EmpireDivider))
      .addRecent(Vector(first, second))

    assertEquals(
      atlas.entries,
      Vector(first, second, AtlasEntry.EmpireDivider)
    )
  }

  test("Recent removal counts sites and includes an encountered divider") {
    val first = AtlasEntry.StoredSite(
      SiteId("first"),
      Vector.empty,
      Vector.empty
    )
    val second = AtlasEntry.StoredSite(
      SiteId("second"),
      Vector.empty,
      Vector.empty
    )
    val third = AtlasEntry.StoredSite(
      SiteId("third"),
      Vector.empty,
      Vector.empty
    )
    val atlas = AtlasState(
      Vector(first, AtlasEntry.EmpireDivider, second, third)
    )

    val removal = atlas.removeRecent(2)

    assertEquals(
      removal.removed,
      Vector(first, AtlasEntry.EmpireDivider, second)
    )
    assertEquals(
      removal.remaining.entries,
      Vector(third)
    )
    assertEquals(atlas.entries.size, 4)
  }

  test("Forgotten removal returns entries in back-to-front removal order") {
    val first = AtlasEntry.StoredSite(
      SiteId("first"),
      Vector.empty,
      Vector.empty
    )
    val second = AtlasEntry.StoredSite(
      SiteId("second"),
      Vector.empty,
      Vector.empty
    )
    val third = AtlasEntry.StoredSite(
      SiteId("third"),
      Vector.empty,
      Vector.empty
    )
    val atlas = AtlasState(
      Vector(first, second, AtlasEntry.EmpireDivider, third)
    )

    val removal = atlas.removeForgotten(2)

    assertEquals(
      removal.removed,
      Vector(third, AtlasEntry.EmpireDivider, second)
    )
    assertEquals(
      removal.remaining.entries,
      Vector(first)
    )
  }

  test("Atlas removal handles zero, insufficient sites, and invalid counts") {
    val site = AtlasEntry.StoredSite(
      SiteId("only-site"),
      Vector.empty,
      Vector.empty
    )
    val atlas = AtlasState(Vector(AtlasEntry.EmpireDivider, site))

    assertEquals(
      atlas.removeRecent(0),
      AtlasRemoval(Vector.empty, atlas)
    )
    assertEquals(
      atlas.removeForgotten(3),
      AtlasRemoval(
        Vector(site, AtlasEntry.EmpireDivider),
        AtlasState(Vector.empty)
      )
    )
    intercept[IllegalArgumentException](atlas.removeRecent(-1))
    intercept[IllegalArgumentException](atlas.removeForgotten(-1))
  }

  test("banner faces change without changing physical banner families") {
    val banners = BannersState(
      PeoplesFavorState(
        PeoplesFavorFace.Mob,
        Some(PlayerId("pink")),
        favor = 4
      ),
      DarkestSecretState(
        DarkestSecretFace.WanderingFlame,
        Some(PlayerId("blue")),
        secrets = 3
      )
    )

    val flipped = banners.copy(
      peoplesFavor = banners.peoplesFavor.copy(
        active = PeoplesFavorFace.GrandCouncil
      ),
      darkestSecret = banners.darkestSecret.copy(
        active = DarkestSecretFace.Festival
      )
    )

    assertEquals(flipped.peoplesFavor.holder, Some(PlayerId("pink")))
    assertEquals(flipped.peoplesFavor.favor, 4)
    assertEquals(flipped.darkestSecret.holder, Some(PlayerId("blue")))
    assertEquals(flipped.darkestSecret.secrets, 3)
  }

  test("site tokens represent current loose favor and secrets") {
    val site = SiteState(
      SiteForces.Occupied(ForceKind.Bandit, 2),
      Vector.empty,
      Vector.empty,
      Tokens(favor = 2, secrets = 1)
    )

    assertEquals(site.tokens, Tokens(2, 1))
  }
}
