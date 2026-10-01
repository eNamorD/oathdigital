package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Quick Exit (card 58), ACTION: place 1 secret on this card, then place an
  * enemy pawn at your site onto any other site.
  *
  * Every game is all-Exile, so every other player whose pawn stands at the
  * player's site is a candidate, read live after the cost is paid. The player
  * chooses one, then any site in play other than that site. The pawn moves by
  * a plain `Move`, as Whistle's does: it does not travel, so no Travel window
  * runs. With no candidate the cost stays paid and one line says so.
  */
case object QuickExit extends PaidAction("denizen.quick-exit",
    Cost(secret = 1)):
  val targetDecisionId: String = "power.quick-exit.target"
  val siteDecisionId: String = "power.quick-exit.site"
  /** "Placed {Blue} at {site}." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" at "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No other pawn was at {site}." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No other pawn was at "), NotePart.Arg(0),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(placed, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askTarget(live, player, source)),
    Branch((live, pending) => askSite(live, player, pending)),
    BuildOps((live, pending) => place(live, player, pending)),
    Note(id, placedNote(_, source)))))

  /** The other players whose pawn stands at `actor`'s site, in seat order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player
      })

  private def chosen(pending: PendingTree): Option[PlayerId] =
    PowerAnswers.one(pending, targetDecisionId).collect {
      case DecisionOptionRef.Player(target) => target }

  private def askTarget(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector(Note(id, _ => nobodyNote(ready, actor, source)))
      case found => Vector(Decide(targetDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(target => DecisionOption.Player(
          DecisionOptionRef.Player(target))),
          heading = Some("Quick Exit: choose a pawn at your site to place " +
            "elsewhere"))))

  private def nobodyNote(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- PowerAccess.pawnSite(ready, actor)
  yield nobody(card, NoteArg.Site(site))

  /** The sites in play other than the chosen pawn's, once it is chosen. */
  private def askSite(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(pending).toVector.flatMap(target =>
      PowerAccess.pawnSite(ready, target).toVector.map(from =>
        PawnMoves.siteChoice(siteDecisionId, actor,
          PawnMoves.sitesOtherThan(ready, from),
          "Quick Exit: choose the site to place the pawn on")))

  /** No answer means no other pawn stood at the site. */
  private def place(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = chosen(pending) match
    case None => Right(Vector.empty)
    case Some(target) => relocation(ready, actor, pending, target)

  private def relocation(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree, target: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = for
    _ <- Either.cond(targets(ready, actor).contains(target), (),
      OathViolation.InvalidEventOrder(
        s"${target.value} is not a pawn at the actor's site"))
    site <- PawnMoves.chosenSite(pending, siteDecisionId)
    ops <- PawnMoves.relocate(ready, target, site)
  yield ops

  /** Where the chosen pawn stands now. No answer means nobody was at the
    * site, whose line the first `Branch` already wrote. */
  private def placedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, targetDecisionId)
    site <- PowerAccess.pawnSite(states.now, target)
  yield placed(card, NoteArg.Player(target), NoteArg.Site(site))
