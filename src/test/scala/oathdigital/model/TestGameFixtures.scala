package oathdigital.model

import oathdigital.testkit.Table

/** A small hand-built game with plain ids and no catalog, for the model and
  * operation suites that build states no Table step reaches.
  *
  * The board: 8 sites S1 to S8 (Cradle S1 and S2, Provinces S3 to S5,
  * Hinterland S6 to S8), each ruled by 1 Bandit. S1 holds the faceup denizen
  * D2 with 1 favor on it, and S2 holds the facedown relic R1. The Atlas
  * stores site S9 with the intact edifice E1.
  *
  * One player, red (`player-red`, lineage `red`, an Exile), stands at S1 with
  * 1 favor, 1 faceup secret, no facedown secret, 3 warbands on the board, a
  * full Supply of 7 and the facedown Vision V1 as adviser. Red is active in
  * the Wake of round 1.
  *
  * The world deck holds only D1; the relic, edifice and legacy decks are
  * empty. The Reliquary holds R2, D3 is dispossessed and D4 is the Arcane
  * reserve. Both banners are unheld: the People's Favor with 1 favor, the
  * Darkest Secret with 1 secret.
  *
  * `ready` seats it with [[ReadyGames.of]]: 5 favor in every suit bank.
  */
object TestGameFixtures:
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
    campaign,
    current
  )

  /** `game` seated at the table: see [[ReadyGames.of]]. */
  val ready: ReadyGame = ReadyGames.of(game)

  val blueId: PlayerId = PlayerId("player-blue")
  val blueLineage: LineageId = LineageId("blue")

  /** `game` with a second player seated, which no Table step can do: blue
    * (lineage `blue`, an Exile) stands at S2 with 2 favor, 1 faceup secret,
    * no facedown secret, 2 warbands on the board, a full Supply of 7 and
    * `blueRelics`. Red holds the People's Favor, which carries 1 favor.
    * Seated by [[ReadyGames.of]], so every suit bank holds 5 favor.
    */
  def redAndBlue(blueRelics: Vector[RelicState] = Vector.empty): ReadyGame =
    val blue = PlayerState(blueId, blueLineage, pawnSite = Some(sites(1)),
      board = PlayerBoardState(favor = 2, faceUpSecrets = 1,
        faceDownSecrets = 0, warbands = 2, supply = SupplyTrack.full),
      advisers = Vector.empty, relics = blueRelics, revealedVision = None)
    val seated = game.copy(
      campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
        blueLineage, LineageState(blueLineage, Some(blueId), Role.Exile,
          Vector.empty, Vector.empty))),
      current = game.current.copy(players = game.current.players :+ blue))
    Table.from(ReadyGames.of(seated)).peoplesFavor(Some(playerId), favor = 1)
      .unchecked
