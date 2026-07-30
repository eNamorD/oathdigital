package oathdigital.model

class DomainValidationSuite extends munit.FunSuite {
  import TestGameFixtures._

  test("campaign maps retain their structural identities and required slots") {
    val wrongId = LineageId("wrong-state-id")
    val invalid = game.copy(
      campaign = game.campaign.copy(
        foundations = game.campaign.foundations - FoundationNumber.VI,
        lineages = Map(lineageId -> lineage.copy(id = wrongId))
      )
    )

    val problems = DomainValidation.validate(invalid)

    assert(problems.contains(DomainProblem.MissingFoundation(FoundationNumber.VI)))
    assert(
      problems.contains(DomainProblem.LineageKeyMismatch(lineageId, wrongId))
    )
  }

  test("a site definition occupies only one place in the world") {
    val duplicateStoredSite = AtlasEntry.StoredSite(
      sites.head,
      Vector.empty,
      Vector.empty
    )
    val invalid = game.copy(
      campaign = game.campaign.copy(
        atlas = AtlasState(Vector(duplicateStoredSite, duplicateStoredSite))
      )
    )

    val problems = DomainValidation.validate(invalid)

    assert(problems.contains(DomainProblem.DuplicateAtlasSite(sites.head)))
    assert(problems.contains(DomainProblem.SiteInMapAndAtlas(sites.head)))
  }

  test("holders and Exile forces reference current aggregate identities") {
    val absentPlayer = PlayerId("absent-player")
    val absentLineage = LineageId("absent-lineage")
    val occupied = game.current.map.sites(sites.head).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(absentLineage), 1)
    )
    val invalid = game.copy(
      current = game.current.copy(
        map = game.current.map.copy(
          sites = game.current.map.sites.updated(sites.head, occupied)
        ),
        banners = game.current.banners.copy(
          peoplesFavor =
            game.current.banners.peoplesFavor.copy(holder = Some(absentPlayer)),
          darkestSecret =
            game.current.banners.darkestSecret.copy(holder = Some(absentPlayer))
        ),
        title = game.current.title.copy(holder = Some(absentPlayer))
      )
    )

    val problems = DomainValidation.validate(invalid)

    assert(
      problems.contains(
        DomainProblem.UnknownForceLineage(sites.head, absentLineage)
      )
    )
    assert(
      problems.contains(DomainProblem.UnknownPeoplesFavorHolder(absentPlayer))
    )
    assert(
      problems.contains(DomainProblem.UnknownDarkestSecretHolder(absentPlayer))
    )
    assert(problems.contains(DomainProblem.UnknownTitleHolder(absentPlayer)))
  }
}
