package oathdigital.gameplay.consent

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

/** Folds the consent facts into `ReadyGame.consent` (consent design,
  * "Events"). Nothing else in the game changes. */
object ConsentEvolution:
  def evolve(ready: ReadyGame, event: ConsentEvent)
      : Either[OathViolation, ReadyGame] =
    val consent = ready.consent
    (event match
      case ConsentRequested(request) => consent.requested(request)
      case ConsentAnswered(granter, granted) =>
        consent.answered(granter, granted, ConsentTurn.of(ready))
      case ConsentRequestWithdrawn(requester) => consent.withdrawn(requester)
      case ConsentPreferenceSet(granter, power, requester, setting) =>
        Right(consent.preferenceSet(granter, power, requester, setting))
      case ConsentPreferencesCleared(granter, power) =>
        Right(consent.cleared(granter, power))
    ).map(next => ready.copy(consent = next))
