package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.setup.{FirstGameSetupPlan, FirstGameSupportState,
  PawnPlacement, PlayerColor}

final case class ReadyGame(
    game: OathGame,
    playerColors: Map[PlayerId, PlayerColor],
    support: FirstGameSupportState
)

sealed trait OathState extends Product with Serializable
object OathState {
  case object NoGame extends OathState

  final case class InProgress(
      plan: FirstGameSetupPlan,
      placements: Vector[PawnPlacement],
      adviserChoices: Vector[(PlayerId, DenizenId)]
  ) extends OathState

  final case class Ready(value: ReadyGame) extends OathState
}
