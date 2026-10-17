package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, ConsentAnswered,
  ConsentPreferenceSet, ConsentPreferencesCleared, ConsentRequestWithdrawn,
  ConsentRequested}

/** The consent commands (consent design, "Commands"). Each answers with the
  * events to append as one batch, or why it cannot run. Consent events open
  * no command batch, so nothing here is undoable, except the batch that
  * answers a parked decision, which a settled request opens for its
  * requester, or for the issuer of an automatic power's request. */
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
    * preferences decide (ruling 10). `granters` narrows the alternatives
    * asked; empty asks them all. */
  def request(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef, granters: Vector[PlayerId] = Vector.empty)
      : Result = for
    ready <- playing(state)
    _ <- Either.cond(ready.consent.pending.isEmpty, (),
      OathViolation.ConsentPending)
    ask <- rules.consentAsk(state, requester, decisionId, target)
    asks <- chosen(ask.asks, granters)
    covers = ask.covers.filter(key =>
      asks.exists(_.powers.contains(key.power)))
    request = ConsentRequest(requester, decisionId, target, asks, covers)
    events <- settled(state,
      ConsentRequested(request) +: automatic(ready.consent, request))
  yield events

  /** The request a walk parked at an automatic power's step opens
    * (automatic power consent design, "Reaching the step"), with the
    * answers the granters' preferences give and what they settle.
    * `issuer` opened the batch that walked there. Nothing when the walk is
    * not parked at a step, or a request already waits. */
  def opened(state: OathState, issuer: PlayerId): Result =
    rules.powerUseStep(state).filter(_ => pending(state).isEmpty) match
      case None => Right(Vector.empty)
      case Some(use) =>
        val decisionId = PowerUseStep.decisionId(use.power)
        for
          ready <- playing(state)
          ask <- rules.consentAsk(state, use.user, decisionId,
            PowerUseStep.use)
          request = ConsentRequest(use.user, decisionId, PowerUseStep.use,
            ask.asks, ask.covers, Some(ConsentUse(use.card, issuer)))
          events <- settled(state,
            ConsentRequested(request) +: automatic(ready.consent, request))
        yield events

  /** The asks a request sends: all of them, or the required ones and the
    * alternatives whose granter `granters` names (catalog batch 8, slice
    * 15). A request that keeps a single alternative leaves it marked, and
    * it settles alone. */
  private def chosen(asks: Vector[ConsentAsk], granters: Vector[PlayerId])
      : Either[OathViolation, Vector[ConsentAsk]] =
    if granters.isEmpty then Right(asks)
    else
      val kept = asks.filter(ask => !ask.alternative ||
        granters.contains(ask.granter))
      val unknown = granters.filterNot(by => asks.exists(_.granter == by))
      if unknown.nonEmpty then Left(OathViolation.ConsentUnavailable(
        s"${unknown.map(_.value).mkString(", ")} would not be asked"))
      else if asks.exists(_.alternative) && !kept.exists(_.alternative) then
        Left(OathViolation.ConsentUnavailable(
          "ask at least one of the players who could consent"))
      else Right(kept)

  /** `granter`'s answer. Never Allow and Always Allow also set their
    * preference for the requester on every power of their ask, after the
    * answer and anything it settles, so the requester's log reads a manual
    * Always as it reads a Yes: the answer comes right after the question.
    * For a request about an automatic power, they come before the step's
    * answer, so a second step the walk reaches in the same command is
    * answered by them (automatic power consent design). */
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
    events <- settled(state, Vector(ConsentAnswered(granter,
      choice.granted)), preferences)
  yield events

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
    * grants. They are kept until the request settles, so nothing follows
    * the answer that settles it: a required ask's Never settles it, while
    * an alternative's Never refuses only that alternative (catalog batch
    * 8, slice 15). */
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
    answers.foldLeft((Option(request), Vector.empty[ConsentAnswered])) {
      case ((Some(open), kept), answer)
          if open.open.exists(_.granter == answer.granter) =>
        val next = open.copy(asks = open.asks.map(ask =>
          if ask.granter == answer.granter then
            ask.copy(answer = Some(answer.granted))
          else ask))
        val required = !open.asks.exists(ask =>
          ask.granter == answer.granter && ask.alternative)
        val over = next.granted || next.refused ||
          (required && !answer.granted)
        (Option.unless(over)(next), kept :+ answer)
      case (done, _) => done
    }._2

  /** `events`, followed by what their last answer settles.
    *
    *  - A request about an automatic power, settled either way: `first`,
    *    then the issuer's batch answering the step `use` on a grant or
    *    `skip` on a refusal (automatic power consent design, "Settling").
    *  - Any other request, granted at a choose-one: the requester's answer
    *    to the parked decision. That answer is the requester's command
    *    batch, though the granter's command produced it, so the requester
    *    can undo the move (consent design, "Settling"). At a choose-many,
    *    the grant only opens the option, and the requester goes on
    *    choosing (slice 2). `first` follows everything.
    *
    * A resumed walk that parks at another step opens its request. */
  private def settled(state: OathState, events: Vector[OathEvent],
      first: Vector[OathEvent] = Vector.empty): Result =
    folded(state, events).flatMap { after =>
      val request = events.collectFirst { case ConsentRequested(opened) =>
        opened }.orElse(pending(state))
      val last = events.lastOption.collect {
        case ConsentAnswered(_, granted) => granted }
      request.filter(_ => pending(after).isEmpty).zip(last) match
        case Some((done, granted)) if done.use.nonEmpty =>
          val issuer = done.use.fold(done.requester)(_.issuer)
          folded(after, first).flatMap(prepared => resumed(prepared, done,
            if granted then PowerUseStep.use else PowerUseStep.skip, issuer))
            .map(events ++ first ++ _)
        case Some((done, true)) if rules.grantAnswers(after, done.requester,
            done.decisionId) =>
          resumed(after, done, done.target, done.requester)
            .map(events ++ _ ++ first)
        case _ => Right(events ++ first)
    }

  /** `issuer`'s batch answering `done`'s parked decision with `answer` as
    * its requester, then the request of any step the walk parks at next. */
  private def resumed(state: OathState, done: ConsentRequest,
      answer: DecisionOptionRef, issuer: PlayerId): Result = for
    answered <- rules.resolveWalker(state, done.requester, done.decisionId,
      DecisionAnswer.ChooseOneAnswer(answer))
    next <- opened(answered.state, issuer)
  yield (CommandAccepted(issuer) +: answered.events) ++ next

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
