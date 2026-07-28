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

    val atlas = AtlasState(Vector(middle))
      .addRecent(recent)
      .addForgotten(forgotten)

    assertEquals(atlas.entries, Vector(recent, middle, forgotten))
    assertEquals(atlas.mostRecent, Some(recent))
    assertEquals(atlas.mostForgotten, Some(forgotten))
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
