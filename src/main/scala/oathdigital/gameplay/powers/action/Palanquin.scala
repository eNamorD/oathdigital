package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.travel.{TollRoads, TollRoadsCard}
import oathdigital.model._

object PalanquinCard extends Denizen(DenizenId("107"), "Palanquin",
    Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.palanquin"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. Put " +
      "your pawn on a site that they can travel to. Make them " +
      "travel to that site, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Palanquin (card 107, catalog batch 8, slice 15): the user chooses
  * another player at their site, and a site that player's Travel would
  * reach, previewed through `QueuedStarts`. The user's pawn is placed
  * there, then the target's Travel to it is queued as a `Run` at the front,
  * with Palanquin as its source and the user as its sender. The target
  * walks it off turn, for no Supply, and owns its decisions.
  *
  * A target with no reachable site is unavailable with `noDestination`; a
  * site the Travel refuses, with why. With no target who can reach a site,
  * the ACTION is unavailable with "No valid enemy pawn".
  *
  * A site the target's Travel reaches only by consent is askable, on the
  * user's behalf. The power builds the ask: the grants
  * `QueuedStarts.consent` names (a ruled Narrow Pass), keyed as the
  * target's own answer, and Toll Roads' two alternatives, the target
  * agreeing to pay or its ruler waiving it (`TollRoads.sending`). When the
  * user rules Toll Roads, a question of theirs follows the pick: take 1
  * favor, which the target must agree to, or waive the toll.
  *
  * Its line: "Moved to {Coast}; {Blue} travels there."
  */
case object Palanquin extends PaidAction(PalanquinCard.power):
  val cardId: DenizenId = PalanquinCard.id
  val targetDecisionId: String = "power.palanquin.target"
  val destinationDecisionId: String = "power.palanquin.destination"
  val noDestination: String = "Cannot travel anywhere now"
  val tollDecisionId: String = "power.palanquin.toll"
  val take: DecisionOptionRef.Button = DecisionOptionRef.Button("toll.take")
  val waive: DecisionOptionRef.Button = DecisionOptionRef.Button("toll.waive")
  private val notes = NoteSet(id)
  /** "Moved to {Coast}; {Blue} travels there." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0), NotePart.Text("; "),
    NotePart.Arg(1), NotePart.Text(" travels there.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(targetDecisionId, destinationDecisionId, tollDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    build(ready, player, source, QueuedStarts.unchecked)

  override def build(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(targetDecisionId, player,
      DecisionQuery.ChooseOne(targetOptions(live, player, starts),
        heading = Some("Palanquin: choose a player at your site"))))),
    Branch((live, pending) => chosenTarget(pending).toVector.map(target =>
      Decide(destinationDecisionId, player, DecisionQuery.ChooseOne(
        destinationOptions(live, player, target, starts),
        heading = Some("Palanquin: choose where you both go"),
        confirmVerb = Some("Go to"))))),
    Branch((live, pending) => (chosenTarget(pending),
        PowerAnswers.one(pending, destinationDecisionId)) match
      case (Some(target), Some(DecisionOptionRef.Site(site)))
          if TollRoads.sending(live, target, site, player) ==
            TollRoads.Sending.SenderRules =>
        Vector(Decide(tollDecisionId, player, DecisionQuery.ChooseOne(
          Vector(takeOption(live, target, site, player),
            DecisionOption.Button(waive, "Waive the toll")),
          heading = Some("Toll Roads: take 1 favor, or waive the toll"))))
      case _ => Vector.empty),
    BuildOps((live, pending) => sent(live, player, pending)),
    Note(id, states => for
      site <- NoteSupport.answer(states, destinationDecisionId).collect {
        case DecisionOptionRef.Site(site) => site }
      target <- NoteSupport.answer(states, targetDecisionId).collect {
        case DecisionOptionRef.Player(target) => target }
    yield moved(PowerSourceRef.Card(cardId), NoteArg.Site(site),
      NoteArg.Player(target))))))

  override def refusal(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts): Option[String] =
    Option.unless(targetOptions(ready, player, starts).exists(_.available))(
      PawnMoves.noValidEnemyPawn)

  /** The target's queued Travel to `site`, sent by `user`. */
  def entry(user: PlayerId, target: PlayerId, site: SiteId)
      : QueuedProcedure =
    QueuedProcedure(target, ActionRef.Travel,
      Vector(DecisionOptionRef.Site(site)),
      Some(PowerSourceRef.Card(cardId)), QueueKind.Run, sender = Some(user))

  /** The other players at `user`'s site, one who can reach no site
    * unavailable. */
  def targetOptions(ready: ReadyGame, user: PlayerId, starts: QueuedStarts)
      : Vector[DecisionOption] = targets(ready, user).map { target =>
    val shown = DecisionOption.Player(DecisionOptionRef.Player(target))
    if destinationOptions(ready, user, target, starts).exists(reachable)
    then shown
    else DecisionOption.Unavailable(shown,
      OptionReason.Written(noDestination))
  }

  /** Every site but `user`'s: available, askable or unavailable with why
    * (`destinationOption`). */
  def destinationOptions(ready: ReadyGame, user: PlayerId, target: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    destinations(ready, user).map(site =>
      destinationOption(ready, user, target, site, starts))

  /** `site`, available once every grant it waits on was given; askable
    * while some are still to ask; unavailable with why once nobody may
    * give one. */
  private def destinationOption(ready: ReadyGame, user: PlayerId,
      target: PlayerId, site: SiteId, starts: QueuedStarts)
      : DecisionOption =
    val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
    def unavailable(reason: OptionReason) =
      DecisionOption.Unavailable(shown, reason)
    starts.consent(ready, entry(user, target, site)) match
      case QueuedStarts.Verdict.Refused(violation) =>
        unavailable(OptionReason.Written(reason(violation)))
      case verdict =>
        val turn = ConsentTurn.of(ready)
        val needed = verdict match
          case QueuedStarts.Verdict.Waiting(needed) => needed
          case _ => Vector.empty
        def answer(one: QueuedStarts.Needed) =
          ready.consent.answer(one.key, Some(one.granter), turn)
        val pending = needed.filterNot(answer(_)
          .contains(ConsentAnswer.Granted))
        pending.find(answer(_).contains(ConsentAnswer.Refused)) match
          case Some(refused) => unavailable(refused.plain.fold[OptionReason](
            OptionReason.Written("Cannot travel there now"))(
            OptionReason.Noted(refused.power, _)))
          case None => TollRoads.sending(ready, target, site, user) match
            case TollRoads.Sending.Barred(note) =>
              unavailable(OptionReason.Noted(TollRoads.id, note))
            case TollRoads.Sending.Asks(alternatives, hover) =>
              unavailable(askable(pending, alternatives, Some(hover)))
            case _ if pending.nonEmpty =>
              unavailable(askable(pending, Vector.empty, None))
            case _ => shown

  /** The ask for a destination: each pending grant's granter, required,
    * and Toll Roads' alternatives (ruling 8). A toll ruler already asked
    * for a required grant answers for the toll too, and the target is
    * then not asked (ruling 12). `pending` or `alternatives` is not
    * empty. */
  private def askable(pending: Vector[QueuedStarts.Needed],
      alternatives: Vector[TollRoads.Alternative], hover: Option[PowerNote])
      : OptionReason.Askable =
    val required = pending.map(_.granter).distinct.map(granter =>
      ConsentAsk(granter, pending.filter(_.granter == granter)
        .map(_.power).distinct))
    val joined = alternatives.filter(alt =>
      required.exists(_.granter == alt.granter))
    val asks =
      if joined.nonEmpty then required.map(ask => joined
        .find(_.granter == ask.granter)
        .fold(ask)(alt => ask.copy(powers = ask.powers :+ alt.power)))
      else required ++ alternatives.map(alt => ConsentAsk(alt.granter,
        Vector(alt.power), alternative = alternatives.size > 1))
    val keys = pending.map(_.key) ++
      (if joined.nonEmpty then joined else alternatives).map(_.key)
    (pending.headOption, hover) match
      case (Some(first), _) => OptionReason.Askable(first.power, first.asked,
        asks, keys)
      case (None, Some(note)) => OptionReason.Askable(TollRoads.id, note,
        asks, keys)
      case (None, None) => OptionReason.Askable(TollRoads.id,
        TollRoads.payerAsked(PowerSourceRef.Card(TollRoadsCard.id)), asks,
        keys)

  /** "Take 1 favor": askable of the target, available once they agreed,
    * unavailable once they refused or with no favor (ruling 10). */
  private def takeOption(ready: ReadyGame, target: PlayerId, site: SiteId,
      user: PlayerId): DecisionOption =
    val shown = DecisionOption.Button(take, "Take 1 favor")
    val card = PowerSourceRef.Card(TollRoadsCard.id)
    val key = TollRoads.payerKey(target, site)
    val them = NoteArg.Player(target)
    val agreed = ready.consent.answer(key, Some(target),
      ConsentTurn.of(ready))
    if agreed.contains(ConsentAnswer.Granted) then shown
    else if agreed.contains(ConsentAnswer.Refused) then
      DecisionOption.Unavailable(shown, OptionReason.Noted(TollRoads.id,
        TollRoads.sentRefused(card, them)))
    else if !TollRoads.canPay(ready, target) then DecisionOption.Unavailable(
      shown, OptionReason.Noted(TollRoads.id,
        TollRoads.sentNoFavor(card, them)))
    else DecisionOption.Unavailable(shown, OptionReason.Askable(
      TollRoads.payerId, TollRoads.sentPayer(card, them,
        NoteArg.Amount(TollRoads.Favor, NoteUnit.Favor),
        NoteArg.Player(user)), Vector(ConsentAsk(target,
          Vector(TollRoads.payerId))), Vector(key)))

  /** Why the target's Travel would not reach a site, in the player's
    * words. */
  def reason(violation: OathViolation): String = violation match
    case _: OathViolation.TravelPassBlocked =>
      "Cannot travel to other sites in the region"
    case OathViolation.StartUnavailable(text) => text
    case _ => "Cannot travel there now"

  /** Whether a destination option can be chosen, now or once asked. */
  private def reachable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case other => other.available

  private def targets(ready: ReadyGame, user: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, user).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != user &&
            other.pawnSite.contains(site) => other.player
      })

  private def destinations(ready: ReadyGame, user: PlayerId)
      : Vector[SiteId] = PowerAccess.pawnSite(ready, user).toVector
    .flatMap(PawnMoves.sitesOtherThan(ready, _))

  private def chosenTarget(pending: PendingTree): Option[PlayerId] =
    PowerAnswers.one(pending, targetDecisionId).collect {
      case DecisionOptionRef.Player(target) => target }

  /** The placement and the queued Travel, once both are answered. */
  private def sent(ready: ReadyGame, user: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    (chosenTarget(pending), PowerAnswers.one(pending,
        destinationDecisionId)) match
      case (Some(target), Some(DecisionOptionRef.Site(site))) =>
        PawnMoves.relocate(ready, user, site).map(moves => moves :+
          QueueProcedure(entry(user, target, site), atFront = true))
      case _ => Right(Vector.empty)
