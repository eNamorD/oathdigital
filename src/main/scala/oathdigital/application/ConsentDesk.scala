package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentPreferenceSet,
  ConsentPreferencesCleared}

/** The consent commands (consent design, "Commands"). Each answers with the
  * events to append as one batch, or why it cannot run. Consent events open
  * no command batch, so nothing here is undoable. */
private[application] final class ConsentDesk(rules: OathRules,
    powers: ConsentPowers):
  private type Result = Either[OathViolation, Vector[OathEvent]]

  def pending(state: OathState): Option[ConsentRequest] = state match
    case OathState.Ready(ready) => ready.consent.pending
    case _ => None

  /** Accepted at any time, even during another player's decision (ruling
    * 18), from the player who rules the power's card now. */
  def setPreference(state: OathState, granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting): Result = for
    ready <- playing(state)
    _ <- powers.waiver(power).toRight(OathViolation.ConsentUnavailable(
      s"${power.value} cannot be waived"))
    _ <- Either.cond(powers.granter(ready, power).contains(granter), (),
      OathViolation.ConsentUnavailable(
        s"${granter.value} does not rule ${power.value}"))
    _ <- Either.cond(requester != granter &&
      ready.game.current.players.exists(_.player == requester), (),
      OathViolation.ConsentUnavailable(
        s"${requester.value} is not another player"))
  yield Vector(ConsentPreferenceSet(granter, power, requester, setting))

  /** One clearing per granter and power whose granter no longer rules the
    * power's card (ruling 17), appended after every command. */
  def stale(state: OathState): Vector[OathEvent] = state match
    case OathState.Ready(ready) =>
      ready.consent.preferences.keys.toVector
        .map((granter, power, _) => (granter, power)).distinct
        .filterNot((granter, power) =>
          powers.granter(ready, power).contains(granter))
        .sortBy((granter, power) => (granter.value, power.value))
        .map(ConsentPreferencesCleared(_, _))
    case _ => Vector.empty

  private def playing(state: OathState): Either[OathViolation, ReadyGame] =
    state match
      case OathState.Ready(ready) if ready.game.current.result.isEmpty =>
        Right(ready)
      case OathState.Ready(_) =>
        Left(OathViolation.ConsentUnavailable("the game is over"))
      case _ => Left(OathViolation.GameNotStarted)
