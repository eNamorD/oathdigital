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
  * once given. */
final case class ConsentAsk(granter: PlayerId, powers: Vector[PowerId],
    answer: Option[Boolean] = None)

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

  /** The asks still waiting on their granter. */
  def open: Vector[ConsentAsk] = asks.filter(_.answer.isEmpty)

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
    else Right(copy(pending = Some(request)))

  /** `granter`'s answer. A refusal settles the whole request as refused; the
    * last grant settles it as granted (rulings 9 and 12). */
  def answered(granter: PlayerId, granted: Boolean, now: ConsentTurn)
      : Either[OathViolation, ConsentState] =
    pending.filter(_.open.exists(_.granter == granter)) match
      case None => Left(OathViolation.InvalidEventOrder(
        s"${granter.value} has no open consent ask"))
      case Some(request) =>
        val updated = request.copy(asks = request.asks.map(ask =>
          if ask.granter == granter then ask.copy(answer = Some(granted))
          else ask))
        if !granted then Right(settled(updated, ConsentAnswer.Refused, now))
        else if updated.open.isEmpty then
          Right(settled(updated, ConsentAnswer.Granted, now))
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

  /** Each key `request` settles, remembered as given by the granter of the
    * ask whose powers hold the key's power. */
  private def settled(request: ConsentRequest, answer: ConsentAnswer,
      now: ConsentTurn): ConsentState =
    copy(answers = answersAt(now) ++ request.keys.flatMap(key =>
      request.asks.find(_.powers.contains(key.power))
        .map(ask => key -> answer.by(ask.granter))),
      pending = None, turn = Some(now))

  /** The answers still standing at `now`: none once their turn has ended. */
  private def answersAt(now: ConsentTurn): Map[ConsentKey, Remembered] =
    if turn.contains(now) then answers else Map.empty
