package oathdigital.model

object TestGameFixtures {
  val playerId: PlayerId = PlayerId("player-red")
  val lineageId: LineageId = LineageId("red")

  val sites: Vector[SiteId] =
    (1 to 8).toVector.map(index => SiteId(s"S$index"))

  val worldDenizen: DenizenState =
    DenizenState(DenizenId("D1"), Orientation.FaceDown, Tokens.empty)
  val siteDenizen: DenizenState =
    DenizenState(DenizenId("D2"), Orientation.FaceUp, Tokens(1, 0))
  val adviser: VisionState =
    VisionState(VisionId("V1"), Orientation.FaceDown)
  val siteRelic: RelicState =
    RelicState(RelicId("R1"), Orientation.FaceDown, Tokens.empty)
  val reliquaryRelic: RelicState =
    RelicState(RelicId("R2"), Orientation.FaceDown, Tokens.empty)
  val legacy: LegacyState =
    LegacyState(LegacyId("L1"), active = false)
  val dispossessed: DenizenState =
    DenizenState(DenizenId("D3"), Orientation.FaceDown, Tokens.empty)
  val reserved: DenizenState =
    DenizenState(DenizenId("D4"), Orientation.FaceDown, Tokens.empty)
  val storedEdifice: EdificeState =
    EdificeState(EdificeId("E1"), EdificeSide.Intact, Tokens.empty)

  private val emptySite: SiteState =
    SiteState(
      SiteForces.Occupied(ForceKind.Bandit, 1),
      Vector.empty,
      Vector.empty,
      Tokens.empty
    )

  val map: MapState = MapState(
    cradle = sites.take(2),
    provinces = sites.slice(2, 5),
    hinterland = sites.drop(5),
    sites = sites.map(_ -> emptySite).toMap
      .updated(
        sites.head,
        emptySite.copy(denizens = Vector(siteDenizen))
      )
      .updated(
        sites(1),
        emptySite.copy(relics = Vector(siteRelic))
      )
  )

  val player: PlayerState = PlayerState(
    player = playerId,
    lineage = lineageId,
    pawnSite = sites.head,
    board = PlayerBoardState(
      favor = 1,
      faceUpSecrets = 1,
      faceDownSecrets = 0,
      warbands = 3,
      supply = Supply.full
    ),
    advisers = Vector(adviser),
    relics = Vector.empty,
    revealedVision = None
  )

  val lineage: LineageState = LineageState(
    id = lineageId,
    controller = Some(playerId),
    role = Role.Exile,
    legacies = Vector(legacy),
    startingAdviser = None
  )

  val campaign: CampaignState = CampaignState(
    atlas = AtlasState(
      Vector(
        AtlasEntry.StoredSite(
          SiteId("S9"),
          Vector(storedEdifice),
          Vector.empty
        ),
        AtlasEntry.EmpireDivider
      )
    ),
    foundations = FoundationNumber.all.map { number =>
      number -> FoundationState(FoundationFace.Normal, Set.empty)
    }.toMap,
    lineages = Map(lineageId -> lineage),
    reliquary = Vector(reliquaryRelic),
    dispossessed = Vector(dispossessed),
    suitedReserves = Map(Suit.Arcane -> Vector(reserved)),
    oathkeeperGoal = OathkeeperGoal.Supremacy,
    era = EraState(20, Map(lineageId -> 0))
  )

  val current: CurrentGameState = CurrentGameState(
    players = Vector(player),
    map = map,
    commonCards = CardZones(
      worldDeck = Vector(worldDenizen),
      relicDeck = Vector.empty,
      edificeDeck = Vector.empty,
      legacyDeck = Vector.empty,
      regionalDiscards = Map.empty
    ),
    banners = BannersState(
      PeoplesFavorState(
        PeoplesFavorFace.Mob,
        holder = None,
        favor = 1
      ),
      DarkestSecretState(
        DarkestSecretFace.WanderingFlame,
        holder = None,
        secrets = 1
      )
    ),
    title = OathkeeperState(None, TitleSide.Oathkeeper),
    turn = TurnState(playerId, Phase.Wake, Set.empty),
    tracks = GameTracks(round = 1, visionsDrawn = 0, usurperLimited = true),
    pending = None,
    result = None
  )

  val game: OathGame = OathGame(
    CatalogRef("oath-new-foundations", "2026.07.27-pre2"),
    campaign,
    current
  )
}
