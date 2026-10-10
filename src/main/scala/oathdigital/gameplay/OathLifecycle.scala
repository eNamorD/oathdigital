package oathdigital.gameplay

import oathdigital.model._

private[gameplay] object GameplayTransition:
  def apply(
      state: OathState,
      events: Vector[OathEvent]
  )(
      evolve: (OathState, OathEvent) => Either[OathViolation, OathState]
  ): Either[OathViolation, OathTransition] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state)) {
      case (Right(current), event) => evolve(current, event)
      case (failure @ Left(_), _) => failure
    }.map(OathTransition(_, events))
