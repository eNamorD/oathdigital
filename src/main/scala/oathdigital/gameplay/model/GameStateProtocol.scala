package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.setup.{FirstGameSetupPlan, FirstGameSupportState,
  PawnPlacement, PlayerColor}

final case class MaterialBankState(
    favor: Map[Suit, Int],
    warbandSupply: Map[ForceKind, Int]
) {
  require(favor.values.forall(_ >= 0), "favor banks must be non-negative")
  require(warbandSupply.values.forall(_ >= 0),
    "warband supplies must be non-negative")
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
)

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
