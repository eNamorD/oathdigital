package oathdigital.model

class WorldModelSuite extends munit.FunSuite {
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
