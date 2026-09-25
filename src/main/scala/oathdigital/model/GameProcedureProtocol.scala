package oathdigital.model

import oathdigital.model._

final case class OathTransition(
    state: OathState,
    events: Vector[OathEvent]
)
