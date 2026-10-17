package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
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
  * Its line: "Moved to {Coast}; {Blue} travels there."
  */
case object Palanquin extends PaidAction(PalanquinCard.power):
  val cardId: DenizenId = PalanquinCard.id
  val targetDecisionId: String = "power.palanquin.target"
  val destinationDecisionId: String = "power.palanquin.destination"
  val noDestination: String = "Cannot travel anywhere now"
  private val notes = NoteSet(id)
  /** "Moved to {Coast}; {Blue} travels there." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0), NotePart.Text("; "),
    NotePart.Arg(1), NotePart.Text(" travels there.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(targetDecisionId, destinationDecisionId)

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

  /** Every site but `user`'s, one the target's Travel would not reach
    * unavailable with why. */
  def destinationOptions(ready: ReadyGame, user: PlayerId, target: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    destinations(ready, user).map { site =>
      val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
      starts.refusal(ready, entry(user, target, site))
        .fold[DecisionOption](shown)(violation => DecisionOption.Unavailable(
          shown, OptionReason.Written(reason(violation))))
    }

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
