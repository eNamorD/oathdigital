package oathdigital.model

object TestGameFixtures {
  val playerId: PlayerId = PlayerId("player-red")
  val lineageId: LineageId = LineageId("red")

  val sites: Vector[SiteId] =
    (1 to 8).toVector.map(index => SiteId(s"S$index"))

  val worldDenizen: DenizenId = DenizenId("D1")
  val siteDenizen: DenizenState =
    DenizenState(DenizenId("D2"), Orientation.FaceUp, Tokens(1, 0))
  val adviser: VisionState =
    VisionState(VisionId("V1"), Orientation.FaceDown)
  val siteRelic: RelicState =
    RelicState(RelicId("R1"), Orientation.FaceDown, Tokens.empty)
  val reliquaryRelic: RelicId = RelicId("R2")
  val legacy: LegacyState =
    LegacyState(LegacyId("L1"), active = false)
  val dispossessed: DenizenId = DenizenId("D3")
  val reserved: DenizenId = DenizenId("D4")
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
    pawnSite = Some(sites.head),
    board = PlayerBoardState(
      favor = 1,
      faceUpSecrets = 1,
      faceDownSecrets = 0,
      warbands = 3,
      supply = SupplyTrack.full
    ),
    advisers = Vector(adviser),
    relics = Vector.empty,
    revealedVision = None
  )

  val lineage: LineageState = LineageState(
    id = lineageId,
    previousPlayer = Some(playerId),
    role = Role.Exile,
    legacies = Vector(legacy),
    startingAdvisers = Vector.empty
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
    result = None
  )

  val game: OathGame = OathGame(
    CatalogRef("oath-new-foundations", "2026.07.27-pre2"),
    campaign,
    current
  )

  /** `game` seated at the table: see [[ReadyGames.of]]. */
  val ready: ReadyGame = ReadyGames.of(game)
}
