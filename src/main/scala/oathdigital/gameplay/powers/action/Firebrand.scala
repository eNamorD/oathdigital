package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object FirebrandCard extends Denizen(DenizenId("233"), "Firebrand", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.firebrand"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Add [favor] to the People's Favor from any " +
      "favor bank, or burn [favor-burnt] from the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Firebrand (card 233), ACTION: place 1 secret on this card, then add 1
  * favor to the People's Favor from any favor bank, or burn 1 favor from
  * the People's Favor.
  *
  * The options, read live after the cost, are each bank that holds favor,
  * in suit order, then a burn button when the People's Favor holds favor.
  * Who holds the banner, or nobody, does not matter. A single option runs
  * without asking. With none, the cost stays paid and one line says so.
  * The choice is narrated: the line names the bank or the burn.
  */
case object Firebrand extends PaidAction(FirebrandCard.power):
  val decisionId: String = "power.firebrand.choice"
  val Moved: Int = 1
  val burn: DecisionOptionRef.Button = DecisionOptionRef.Button("burn")
  /** "{Red} moved {1 favor} from {the Order bank} to the {People's Favor}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" moved "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(" to the "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Red} burned {1 favor} from the {People's Favor}." */
  val burned: NoteKey = NoteKey("used.burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" from the "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Every favor bank and the People's Favor were empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Text(
    "Every favor bank and the People's Favor were empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, burned, empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val banner = PositionedLocation(
    Location.OnBanner(Banner.PeoplesFavor))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => Right(effect(live, pending))),
    Note(id, firedNote(_, player, source)))))

  private def favorOn(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** The choices, in order: each stocked bank, then the burn. */
  private def options(ready: ReadyGame): Vector[DecisionOptionRef] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)
      .map(DecisionOptionRef.FavorBank(_)) ++
      Option.when(favorOn(ready) > 0)(burn)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val found = options(ready)
    if found.size < 2 then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      found.map {
        case bank: DecisionOptionRef.FavorBank => DecisionOption.FavorBank(bank)
        case _ => DecisionOption.Button(burn,
          "Burn 1 favor from the People's Favor")
      },
      heading = Some("Firebrand: add 1 favor to the People's Favor from a " +
        "bank, or burn 1 from it"))))

  /** The only option, or the answer. */
  private def chosen(ready: ReadyGame, pending: PendingTree)
      : Option[DecisionOptionRef] = options(ready) match
    case Vector(only) => Some(only)
    case _ => PowerAnswers.one(pending, decisionId)

  private def effect(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, pending) match
    case Some(DecisionOptionRef.FavorBank(suit)) => Vector(Move(
      Piece.Favor(Moved), PositionedLocation(Location.FavorBank(suit)),
      banner))
    case Some(`burn`) => Vector(Burn.favor(Moved, banner))
    case _ => Vector.empty

  /** What the People's Favor gained or lost in the step before the note.
    * No change means nothing was on offer. */
  private def firedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => states.previous match
      case Some(step @ (before, after)) =>
        (favorOn(after) - favorOn(before), NoteSupport.bankPaid(step)) match
          case (gained, Some(suit)) if gained > 0 => moved(card,
            NoteArg.Player(player), NoteArg.Amount(gained, NoteUnit.Favor),
            NoteArg.Bank(suit), NoteArg.Banner(Banner.PeoplesFavor))
          case (change, _) if change < 0 => burned(card,
            NoteArg.Player(player), NoteArg.Amount(-change, NoteUnit.Favor),
            NoteArg.Banner(Banner.PeoplesFavor))
          case _ => empty(card)
      case None => empty(card))
