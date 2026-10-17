package oathdigital.model

/** A granter's standing answer to one requester on one power (consent
  * design, ruling 15). Case-by-case is no entry. */
enum ConsentPreference(val key: String):
  case Never extends ConsentPreference("never")
  case Always extends ConsentPreference("always")

/** What a preference change sets: a standing answer, or case by case, which
  * removes the entry. */
enum ConsentSetting(val key: String):
  case Never extends ConsentSetting("never")
  case CaseByCase extends ConsentSetting("case-by-case")
  case Always extends ConsentSetting("always")

  def preference: Option[ConsentPreference] = this match
    case Never => Some(ConsentPreference.Never)
    case CaseByCase => None
    case Always => Some(ConsentPreference.Always)

object ConsentSetting:
  def fromKey(key: String): Option[ConsentSetting] = values.find(_.key == key)

/** A granter's answer to a prompt. Never Allow and Always Allow also set
  * the granter's preference for the requester (user story, step 6). */
enum ConsentChoice(val key: String):
  case Never extends ConsentChoice("never")
  case No extends ConsentChoice("no")
  case Yes extends ConsentChoice("yes")
  case Always extends ConsentChoice("always")

  def granted: Boolean = this == Yes || this == Always

  def setting: Option[ConsentSetting] = this match
    case Never => Some(ConsentSetting.Never)
    case Always => Some(ConsentSetting.Always)
    case No | Yes => None

object ConsentChoice:
  def fromKey(key: String): Option[ConsentChoice] = values.find(_.key == key)

enum ConsentAnswer:
  case Granted, Refused

  /** This answer, given by `player`. */
  def by(player: PlayerId): Remembered =
    Remembered(this, Granter.Player(player))

/** Who gave a remembered answer: a player, or nobody but a search
  * supposing a grant (`ConsentState.granting`), which never persists. */
enum Granter:
  case Player(player: PlayerId)
  case Supposed

/** An answer remembered for the turn, with who gave it. It counts only
  * while that player may still answer (consent design, ruling 12). */
final case class Remembered(answer: ConsentAnswer, by: Granter)

/** What one answer is remembered for (ruling 12): the power, the requester,
  * the decision and the option. The decision keeps Deep Woods as a Travel
  * destination apart from Deep Woods in another question. */
final case class ConsentKey(power: PowerId, requester: PlayerId,
    decisionId: String, target: DecisionOptionRef)

/** One granter asked about their powers on one option, and their answer
  * once given. An `alternative` ask is one of several, any of whose grant
  * will do (catalog batch 8, slice 15). */
final case class ConsentAsk(granter: PlayerId, powers: Vector[PowerId],
    answer: Option[Boolean] = None, alternative: Boolean = false)

final case class ConsentRequest(requester: PlayerId, decisionId: String,
    target: DecisionOptionRef, asks: Vector[ConsentAsk],
    covers: Vector[ConsentKey] = Vector.empty):
  /** Every key the request settles: what it covers when it names any (an
    * operation restriction's grant, slice 3), else each asked power for this
    * option. */
  def keys: Vector[ConsentKey] =
    if covers.nonEmpty then covers
    else asks.flatMap(ask =>
      ask.powers.map(ConsentKey(_, requester, decisionId, target)))

  def required: Vector[ConsentAsk] = asks.filterNot(_.alternative)
  def alternatives: Vector[ConsentAsk] = asks.filter(_.alternative)

  /** Every required ask granted and, when it has alternatives, one of
    * them. */
  def granted: Boolean = required.forall(_.answer.contains(true)) &&
    (alternatives.isEmpty || alternatives.exists(_.answer.contains(true)))

  /** A required ask refused, or every alternative. */
  def refused: Boolean = required.exists(_.answer.contains(false)) ||
    (alternatives.nonEmpty && alternatives.forall(_.answer.contains(false)))

  /** The keys `ask`'s answer settles. */
  def keysOf(ask: ConsentAsk): Vector[ConsentKey] =
    keys.filter(key => ask.powers.contains(key.power))

  /** The asks still waiting on their granter: each unanswered required
    * one, and the unanswered alternatives until one of them grants. */
  def open: Vector[ConsentAsk] =
    val settled = alternatives.exists(_.answer.contains(true))
    asks.filter(ask => ask.answer.isEmpty && !(ask.alternative && settled))

/** The turn answers belong to: a round and its active player. */
final case class ConsentTurn(round: Int, player: PlayerId)
object ConsentTurn:
  def of(ready: ReadyGame): ConsentTurn = ConsentTurn(
    ready.game.current.tracks.round, ready.game.current.turn.activePlayer)

/** The consent ledger (consent design, "State"): preferences keyed by
  * granter, power and requester; the answers of `turn`; and the request
  * waiting on its granters, if any. It lives beside the game, not in it,
  * because undo rewinds the game to a saved state and the ledger must
  * outlive that. */
final case class ConsentState(
    preferences: Map[(PlayerId, PowerId, PlayerId), ConsentPreference] =
      Map.empty,
    answers: Map[ConsentKey, Remembered] = Map.empty,
    pending: Option[ConsentRequest] = None,
    turn: Option[ConsentTurn] = None):

  def preference(granter: PlayerId, power: PowerId, requester: PlayerId)
      : Option[ConsentPreference] = preferences.get((granter, power, requester))

  /** The answer remembered for `key` this turn, whoever gave it: none once
    * the turn the answers belong to has ended (plan decision 4). For
    * display; a ledger check reads [[answer]]. */
  def remembered(key: ConsentKey, now: ConsentTurn): Option[ConsentAnswer] =
    rememberedAt(key, now).map(_.answer)

  /** The answer remembered for `key` this turn that counts now: one given
    * by `granter`, who may answer `key` now, or one a search supposes. A
    * ruler who lost the site, to another player or to bandits, no longer
    * lifts or blocks anything (consent design, ruling 12). */
  def answer(key: ConsentKey, granter: Option[PlayerId], now: ConsentTurn)
      : Option[ConsentAnswer] = rememberedAt(key, now).collect {
    case Remembered(answer, Granter.Supposed) => answer
    case Remembered(answer, Granter.Player(by)) if granter.contains(by) =>
      answer
  }

  private def rememberedAt(key: ConsentKey, now: ConsentTurn)
      : Option[Remembered] = if turn.contains(now) then answers.get(key)
    else None

  def requested(request: ConsentRequest): Either[OathViolation, ConsentState] =
    if pending.nonEmpty then Left(OathViolation.InvalidEventOrder(
      "a consent request is already waiting"))
    else if request.asks.isEmpty || request.asks.exists(_.answer.nonEmpty) then
      Left(OathViolation.InvalidEventOrder(
        "a consent request opens with every ask unanswered"))
    // Each key is remembered as its ask's granter's answer (ruling 12).
    else if request.keys.exists(key =>
        !request.asks.exists(_.powers.contains(key.power))) then
      Left(OathViolation.InvalidEventOrder(
        "a consent request covers a power none of its asks holds"))
    else if request.asks.map(_.granter).distinct.size != request.asks.size then
      Left(OathViolation.InvalidEventOrder(
        "a consent request asks each granter once"))
    else Right(copy(pending = Some(request)))

  /** `granter`'s answer (rulings 9 and 12; catalog batch 8, slice 15). A
    * required refusal settles the whole request as refused. An
    * alternative's refusal is remembered and the others stay asked, until
    * all have refused. The last grant needed settles it as granted. */
  def answered(granter: PlayerId, granted: Boolean, now: ConsentTurn)
      : Either[OathViolation, ConsentState] =
    pending.filter(_.open.exists(_.granter == granter)) match
      case None => Left(OathViolation.InvalidEventOrder(
        s"${granter.value} has no open consent ask"))
      case Some(request) =>
        val updated = request.copy(asks = request.asks.map(ask =>
          if ask.granter == granter then ask.copy(answer = Some(granted))
          else ask))
        val alternative = request.asks.exists(ask =>
          ask.granter == granter && ask.alternative)
        if !granted && !alternative then
          Right(refusedBy(granter, updated, now))
        else if updated.granted || updated.refused then
          Right(settledBy(updated, now))
        else if !granted then Right(copy(
          answers = answersAt(now) ++ answersOf(updated), pending =
            Some(updated), turn = Some(now)))
        else Right(copy(pending = Some(updated)))

  /** The requester takes their unanswered request back; nothing is
    * remembered (ruling 14). */
  def withdrawn(requester: PlayerId): Either[OathViolation, ConsentState] =
    if pending.exists(_.requester == requester) then Right(copy(pending = None))
    else Left(OathViolation.InvalidEventOrder(
      s"${requester.value} has no consent request to withdraw"))

  def preferenceSet(granter: PlayerId, power: PowerId, requester: PlayerId,
      setting: ConsentSetting): ConsentState =
    val key = (granter, power, requester)
    copy(preferences = setting.preference.fold(preferences - key)(
      preferences.updated(key, _)))

  /** Every preference `granter` set on `power` (ruling 17). */
  def cleared(granter: PlayerId, power: PowerId): ConsentState =
    copy(preferences = preferences.filterNot { case ((by, on, _), _) =>
      by == granter && on == power })

  /** The ledger had `keys` been granted at `now`: what the look-ahead tries
    * before it offers to ask, so an option a grant still would not open is
    * never askable. */
  def granting(keys: Vector[ConsentKey], now: ConsentTurn): ConsentState =
    copy(answers = answersAt(now) ++ keys.map(_ ->
      Remembered(ConsentAnswer.Granted, Granter.Supposed)), turn = Some(now))

  /** What survives an undo or a redo: everything but a waiting request
    * (consent design, "Undo"). */
  def carried: ConsentState = copy(pending = None)

  /** Each answered ask's keys, as its granter answered them. */
  private def answersOf(request: ConsentRequest)
      : Vector[(ConsentKey, Remembered)] =
    request.asks.flatMap(ask => ask.answer.toVector.flatMap(grant =>
      request.keysOf(ask).map(_ -> (if grant then ConsentAnswer.Granted
        else ConsentAnswer.Refused).by(ask.granter))))

  /** The request settled: each answered ask's keys remembered as answered,
    * and nothing waiting. With no alternatives, every key is granted by
    * the ask whose powers hold its power (`requested` checks there is
    * one). */
  private def settledBy(request: ConsentRequest, now: ConsentTurn)
      : ConsentState =
    copy(answers = answersAt(now) ++ answersOf(request), pending = None,
      turn = Some(now))

  /** Every key `request` settles, refused by `refuser`: their refusal
    * settles the whole request (ruling 9), and it lapses for every key once
    * they may no longer answer (ruling 12), whoever was asked about it. */
  private def refusedBy(refuser: PlayerId, request: ConsentRequest,
      now: ConsentTurn): ConsentState =
    copy(answers = answersAt(now) ++ request.keys.map(_ ->
      ConsentAnswer.Refused.by(refuser)), pending = None, turn = Some(now))

  /** The answers still standing at `now`: none once their turn has ended. */
  private def answersAt(now: ConsentTurn): Map[ConsentKey, Remembered] =
    if turn.contains(now) then answers else Map.empty
