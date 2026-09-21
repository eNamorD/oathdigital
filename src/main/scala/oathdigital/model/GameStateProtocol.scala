package oathdigital.model

import oathdigital.model._

final case class MaterialBankState(
    favor: Map[Suit, Int],
    warbandSupply: Map[ForceKind, Int]
) {
  require(favor.values.forall(_ >= 0), "favor banks must be non-negative")
  require(warbandSupply.values.forall(_ >= 0),
    "warband supplies must be non-negative")
}

object MaterialBankState {
  val ExileWarbandsPerLineage = 14
  val BanditWarbands = 24

  /** The printed warband inventory: a full supply for each Exile lineage in
    * play, plus the bandits. */
  def printedWarbandSupply(lineages: Iterable[LineageId]): Map[ForceKind, Int] =
    lineages.map(lineage =>
      (ForceKind.Exile(lineage): ForceKind) -> ExileWarbandsPerLineage).toMap +
      (ForceKind.Bandit -> BanditWarbands)
}

/** Private card knowledge, independent of physical card ownership. */
final case class CardKnowledge(
    siteRelics: Map[PlayerId, Map[SiteId, Vector[RelicId]]] = Map.empty,
    advisers: Map[PlayerId, Vector[WorldCardId]] = Map.empty,
    heldRelics: Map[PlayerId, Vector[RelicId]] = Map.empty
)

final case class ReadyGame(
    game: OathGame,
    playerColors: Map[PlayerId, PlayerColor],
    setup: FirstGameSupportState,
    banks: MaterialBankState,
    knowledge: CardKnowledge = CardKnowledge()
) {
  def updateCurrent(f: CurrentGameState => CurrentGameState): ReadyGame =
    copy(game = game.copy(current = f(game.current)))

  def updateCampaign(f: CampaignState => CampaignState): ReadyGame =
    copy(game = game.copy(campaign = f(game.campaign)))
}

object ReadyGame {
  /** Table state for a game that has just been set up: the printed warband
    * inventory for the lineages seated in `game`, and the given favor banks.
    * Every setup path, first game or otherwise, builds its `ReadyGame` here so
    * the bookkeeping outside `OathGame` has one definition. */
  def start(
      game: OathGame,
      colors: Map[PlayerId, PlayerColor],
      firstPlayer: PlayerId,
      favorBanks: Map[Suit, Int],
      profile: FirstGameFoundationProfile =
        FirstGameFoundationProfile.FixedUnaltered
  ): ReadyGame =
    ReadyGame(
      game,
      colors,
      FirstGameSupportState(profile, firstPlayer),
      MaterialBankState(
        favorBanks,
        MaterialBankState.printedWarbandSupply(
          game.current.players.map(_.lineage))
      )
    )
}

sealed trait OathState extends Product with Serializable
object OathState {
  case object NoGame extends OathState

  final case class InProgress(
      plan: FirstGameSetupPlan,
      placements: Vector[PawnPlacement],
      adviserChoices: Vector[(PlayerId, DenizenId)],
      temporaryHands: Map[PlayerId, Vector[WorldCardId]]
  ) extends OathState

  final case class Ready(value: ReadyGame) extends OathState
}
