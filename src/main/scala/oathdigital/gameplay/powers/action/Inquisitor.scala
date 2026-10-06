package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.{CardPlay, VisionRules}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object InquisitorCard extends Denizen(DenizenId("38"), "Inquisitor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.inquisitor"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Guess the name of a facedown Vision _(even the " +
      "Conspiracy)_ held by a player whose pawn is at your site. " +
      "If you are correct, discard it.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Inquisitor (card 38), ACTION: place 1 secret on this card, then guess
  * the name of a facedown Vision held by a player whose pawn is at your
  * site, the player's own included. A right guess discards it.
  *
  * Two questions. The first offers each such Vision by its adviser slot, in
  * seat then adviser order: card backs are public, so a slot discloses
  * nothing, while a card option the viewer cannot identify would suppress
  * the question. The second offers the five Vision names as buttons, the
  * Conspiracy included. A right guess discards the Vision to the discard
  * pile of the region after the player's, then reveals it there: it stays
  * facedown in the pile, and every player then knows it. The batch
  * is not required, so the search never hides a guess, which would disclose
  * the Vision; a wrong guess runs nothing.
  *
  * The line names the guess in its own words, one note key per Vision,
  * since on a wrong guess a card argument would read as a card back to a
  * viewer who cannot identify it. Its line covers the right guess's generic
  * Discarded and Revealed lines.
  */
case object Inquisitor extends PaidAction(InquisitorCard.power):
  val visionDecisionId: String = "power.inquisitor.vision"
  val guessDecisionId: String = "power.inquisitor.guess"
  /** The five Visions a guess may name, in the order offered, with their
    * names. */
  val visions: Vector[(VisionId, String)] = Vector(
    VisionRules.Conquest -> "Vision of Conquest",
    VisionRules.Sanctuary -> "Vision of Sanctuary",
    VisionRules.Rebellion -> "Vision of Rebellion",
    VisionRules.Faith -> "Vision of Faith",
    VisionRules.Conspiracy -> "Conspiracy")

  /** The button that guesses `vision`: its id without "vision:". */
  def guess(vision: VisionId): DecisionOptionRef.Button =
    DecisionOptionRef.Button(vision.value.stripPrefix("vision:"))

  /** "Guessed {name} and discarded {card} from {Blue}.", one per Vision. */
  val right: Map[VisionId, NoteKey] = visions.map((vision, name) =>
    vision -> NoteKey(s"used.${guess(vision).key}.right", Vector(
      NotePart.Text(s"Guessed $name and discarded "), NotePart.Arg(0),
      NotePart.Text(" from "), NotePart.Arg(1), NotePart.Text(".")))).toMap
  /** "Guessed {name} for {Blue}'s Vision, wrongly.", one per Vision. */
  val wrong: Map[VisionId, NoteKey] = visions.map((vision, name) =>
    vision -> NoteKey(s"used.${guess(vision).key}.wrong", Vector(
      NotePart.Text(s"Guessed $name for "), NotePart.Arg(0),
      NotePart.Text("'s Vision, wrongly.")))).toMap
  /** "No player at the site held a facedown Vision." */
  val nothing: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("No player at the site held a facedown Vision.")))
  override def noteKeys: Vector[NoteKey] =
    visions.flatMap((vision, _) => Vector(right(vision), wrong(vision))) :+
      nothing
  override def narratedDecisions: Set[String] =
    Set(visionDecisionId, guessDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askVision(live, player)),
    Branch((_, pending) => askGuess(player, pending)),
    BuildOps((live, pending) => discard(live, player, pending)),
    Note(id, guessedNote(_, source), covers = true))))

  private final case class Target(owner: PlayerId, slot: Int,
      vision: VisionId):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  /** The facedown Visions held by players whose pawn is at the player's
    * site, the player's own included, in seat then adviser order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      held <- ready.game.current.players if held.pawnSite.contains(site)
      (adviser, slot) <- held.advisers.zipWithIndex
      vision <- adviser match
        case VisionState(id, Orientation.FaceDown) => Some(id)
        case _ => None
    yield Target(held.player, slot, vision))

  private def askVision(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(visionDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(target =>
          DecisionOption.AdviserSlot(target.ref)),
          heading = Some("Inquisitor: choose a facedown Vision held by a " +
            "player at your site"))))

  /** Asked once a Vision is chosen. */
  private def askGuess(actor: PlayerId, pending: PendingTree)
      : Vector[Operation] =
    if PowerAnswers.one(pending, visionDecisionId).isEmpty then Vector.empty
    else Vector(Decide(guessDecisionId, actor, DecisionQuery.ChooseOne(
      visions.map((vision, name) => DecisionOption.Button(guess(vision), name)),
      heading = Some("Inquisitor: guess the Vision's name"))))

  /** A right guess discards the Vision; a wrong one runs nothing. No Vision
    * answer means there was none to guess. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, visionDecisionId) match
      case None => Right(Vector.empty)
      case Some(ref) =>
        for
          target <- targets(ready, actor).find(_.ref == ref)
            .toRight(OathViolation.InvalidEventOrder(
              s"${ref.wireId} is not a facedown Vision Inquisitor can guess"))
          answer <- PowerAnswers.one(pending, guessDecisionId)
            .toRight(PowerAnswers.missing(guessDecisionId))
          guessed <- visions.collectFirst {
            case (vision, _) if guess(vision) == answer => vision }
            .toRight(OathViolation.InvalidEventOrder(
              s"${answer.wireId} names no Vision"))
          region <- PowerAccess.pawnSite(ready, actor)
            .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
            .toRight(OathViolation.PawnSiteMissing(actor))
        yield
          if guessed != target.vision then Vector.empty
          else Vector(Discard.Vision(target.vision,
              PositionedLocation(Location.PlayArea(target.owner)), region),
            Reveal(target.vision, Location.RegionalDiscard(region)))

  /** The guess and whose Vision it was about. It was right when that player
    * held the guessed Vision before the step and not after it. */
  private def guessedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map(card =>
    (NoteSupport.answer(states, visionDecisionId),
        NoteSupport.answer(states, guessDecisionId)) match
      case (Some(DecisionOptionRef.AdviserSlot(owner, _)), Some(answer)) =>
        visions.collectFirst { case (vision, _) if guess(vision) == answer =>
          if discarded(states, owner, vision) then
            right(vision)(card, NoteArg.Card(vision), NoteArg.Player(owner))
          else wrong(vision)(card, NoteArg.Player(owner))
        }.getOrElse(nothing(card))
      case _ => nothing(card))

  private def discarded(states: NoteStates, owner: PlayerId,
      vision: VisionId): Boolean = states.previous.exists {
    case (before, after) => holds(before, owner, vision) &&
      !holds(after, owner, vision) }

  private def holds(ready: ReadyGame, owner: PlayerId, vision: VisionId)
      : Boolean = ready.game.current.players.exists(held =>
    held.player == owner && held.advisers.exists(_.id == vision))
