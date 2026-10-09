package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, ConsentAnswered,
  ConsentPreferenceSet, ConsentPreferencesCleared, ConsentRequestWithdrawn,
  ConsentRequested}

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

  /** Asks the rulers of the waivable powers blocking `target` (consent
    * design, "Commands"), answering at once for each granter whose
    * preferences decide (ruling 10). */
  def request(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef): Result = for
    ready <- playing(state)
    _ <- Either.cond(ready.consent.pending.isEmpty, (),
      OathViolation.ConsentPending)
    asks <- rules.consentAsks(state, requester, decisionId, target)
    request = ConsentRequest(requester, decisionId, target, asks)
    events <- settled(state,
      ConsentRequested(request) +: automatic(ready.consent, request))
  yield events

  /** `granter`'s answer. Never Allow and Always Allow also set their
    * preference for the requester on every power of their ask, after the
    * answer and anything it settles, so the requester's log reads a manual
    * Always as it reads a Yes: the answer comes right after the question. */
  def answer(state: OathState, granter: PlayerId, choice: ConsentChoice)
      : Result = for
    ready <- playing(state)
    request <- ready.consent.pending.toRight(OathViolation.ConsentUnavailable(
      "no consent request is waiting"))
    ask <- request.open.find(_.granter == granter).toRight(
      OathViolation.ConsentUnavailable(
        s"${granter.value} has not been asked for consent"))
    preferences = choice.setting.toVector.flatMap(setting => ask.powers.map(
      ConsentPreferenceSet(granter, _, request.requester, setting)))
    events <- settled(state, Vector(ConsentAnswered(granter, choice.granted)))
  yield events ++ preferences

  /** The requester's Undo while their request waits (ruling 14). Anyone
    * else's Undo waits for the answer. */
  def withdraw(state: OathState, requester: PlayerId): Result =
    pending(state) match
      case Some(request) if request.requester == requester =>
        Right(Vector(ConsentRequestWithdrawn(requester)))
      case _ => Left(OathViolation.ConsentPending)

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

  /** The answers the granters' preferences give, in ask order (ruling 10):
    * Never on any of a granter's powers refuses, Always on all of them
    * grants. The first refusal settles the request, so nothing follows it.
    */
  private def automatic(consent: ConsentState, request: ConsentRequest)
      : Vector[OathEvent] =
    val answers = request.asks.flatMap { ask =>
      val set = ask.powers.map(consent.preference(ask.granter, _,
        request.requester))
      if set.contains(Some(ConsentPreference.Never)) then
        Some(ConsentAnswered(ask.granter, granted = false))
      else if set.forall(_.contains(ConsentPreference.Always)) then
        Some(ConsentAnswered(ask.granter, granted = true))
      else None
    }
    answers.indexWhere(!_.granted) match
      case -1 => answers
      case refusal => answers.take(refusal + 1)

  /** `events`, followed, when their last answer completes the request with
    * every ask granted at a choose-one, by the requester's answer to the
    * parked decision. That answer is the requester's command batch, though
    * the granter's command produced it, so the requester can undo the move
    * (consent design, "Settling"). At a choose-many, the grant only opens
    * the option, and the requester goes on choosing (slice 2). */
  private def settled(state: OathState, events: Vector[OathEvent]): Result =
    folded(state, events).flatMap { after =>
      val request = events.collectFirst { case ConsentRequested(opened) =>
        opened }.orElse(pending(state))
      val granted = pending(after).isEmpty && (events.lastOption match
        case Some(ConsentAnswered(_, true)) => true
        case _ => false)
      request.filter(_ => granted) match
        case Some(done) if rules.grantAnswers(after, done.requester,
            done.decisionId) =>
          rules.resolveWalker(after, done.requester, done.decisionId,
            DecisionAnswer.ChooseOneAnswer(done.target))
          .map(answered =>
            events ++ (CommandAccepted(done.requester) +: answered.events))
        case _ => Right(events)
    }

  private def folded(state: OathState, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (now, event) => now.flatMap(rules.evolve(_, event)))

  private def playing(state: OathState): Either[OathViolation, ReadyGame] =
    state match
      case OathState.Ready(ready) if ready.game.current.result.isEmpty =>
        Right(ready)
      case OathState.Ready(_) =>
        Left(OathViolation.ConsentUnavailable("the game is over"))
      case _ => Left(OathViolation.GameNotStarted)
