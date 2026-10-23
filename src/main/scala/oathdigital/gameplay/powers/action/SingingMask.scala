package oathdigital.gameplay.powers.action

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.{NoteSupport, PowerAccess}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object SingingMaskCard extends Relic(RelicId("R28"), "Singing Mask", value = 54, defense = 1):
  val power = PrintedPower(PowerId("relic.singing-mask"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Choose a denizen at your site. Move up to " +
      "[favor] [favor] between the matching favor bank and the " +
      "People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Singing Mask (relic R28), ACTION: place 1 secret on this relic, choose a
  * denizen at your site, and move up to 2 favor between the matching favor
  * bank and the People's Favor, whoever holds it.
  *
  * Three questions, read live after the cost. The card: each faceup denizen
  * and ruined edifice at the pawn's site, in site order ([[SiteCards]]),
  * whose suit's bank or the People's Favor holds favor. The direction, only
  * when both hold favor; otherwise the side that holds favor gives. The
  * amount, from 0 to 2, at most what the giving side holds. The move is one
  * `Move`. With no card to choose nothing is asked, and with nothing moved
  * one line says so. No generic line exists for the move.
  */
final case class SingingMask private (catalog: ExecutableCatalog)
    extends PaidAction(SingingMaskCard.power):
  import SingingMask._

  private val siteCards = new SiteCards(catalog, Suit.all.toSet)
  private val banner =
    PositionedLocation(Location.OnBanner(Banner.PeoplesFavor))

  override def noteKeys: Vector[NoteKey] = SingingMask.noteKeys
  override def narratedDecisions: Set[String] =
    Set(cardDecisionId, directionDecisionId, amountDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askCard(live, player)),
    Branch((live, pending) => askDirection(live, player, pending)),
    Branch((live, pending) => askAmount(live, player, pending)),
    BuildOps((live, pending) => Right(move(live, player, pending))),
    Note(this.id, movedNote(_, source)))))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** The cards at the pawn's site with favor to move, each with its suit. */
  private def cards(ready: ReadyGame, actor: PlayerId)
      : Vector[(SiteDenizenState, Suit)] =
    PowerAccess.pawnSite(ready, actor).toVector
      .flatMap(siteCards.at(ready, _))
      .filter((_, suit) => stock(ready, suit) > 0 || onBanner(ready) > 0)

  private def askCard(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    cards(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(cardDecisionId, actor,
        DecisionQuery.ChooseOne(found.map((card, _) => siteCards.option(card)),
          heading = Some("Singing Mask: choose a card at your site"))))

  /** The chosen card's suit. */
  private def suitChosen(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Option[Suit] = for
    answer <- PowerAnswers.one(pending, cardDecisionId)
    (_, suit) <- cards(ready, actor)
      .find((card, _) => siteCards.refOf(card) == answer)
  yield suit

  /** The ways favor can move for `suit`, toward the People's Favor first. */
  private def directions(ready: ReadyGame, suit: Suit)
      : Vector[DecisionOptionRef.Button] =
    Option.when(stock(ready, suit) > 0)(toBanner).toVector ++
      Option.when(onBanner(ready) > 0)(toBank)

  private def askDirection(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    suitChosen(ready, actor, pending).toVector.flatMap(suit =>
      if directions(ready, suit).size < 2 then Vector.empty
      else Vector(Decide(directionDecisionId, actor, DecisionQuery.ChooseOne(
        Vector(
          DecisionOption.Button(toBanner, "Move favor to the People's Favor"),
          DecisionOption.Button(toBank, s"Move favor to the $suit bank")),
        heading = Some("Singing Mask: choose which way the favor moves")))))

  /** The chosen suit and its way: the only open one, or the answer. */
  private def way(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[(Suit, DecisionOptionRef.Button)] =
    suitChosen(ready, actor, pending).flatMap(suit =>
      directions(ready, suit) match
        case Vector(only) => Some(suit -> only)
        case Vector(_, _) => PowerAnswers.one(pending, directionDecisionId)
          .collect { case chosen: DecisionOptionRef.Button
              if chosen == toBanner || chosen == toBank => suit -> chosen }
        case _ => None)

  /** The favor the giving side holds. */
  private def giving(ready: ReadyGame, suit: Suit,
      direction: DecisionOptionRef.Button): Int =
    if direction == toBanner then stock(ready, suit) else onBanner(ready)

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    way(ready, actor, pending).toVector.map((suit, direction) =>
      Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(0,
        math.min(Most, giving(ready, suit, direction)),
        Some("Singing Mask: move up to 2 favor"), "Move favor")))

  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = (for
    (suit, direction) <- way(ready, actor, pending)
    amount <- PowerAnswers.amount(pending, amountDecisionId)
    if amount > 0
    bank = PositionedLocation(Location.FavorBank(suit))
  yield
    if direction == toBanner then Move(Piece.Favor(amount), bank, banner)
    else Move(Piece.Favor(amount), banner, bank)).toVector

  /** The move the step before the note made, or that none did. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[LogNote] = PowerSourceRef.of(source).map(card =>
    states.previous.flatMap(restated(card, _)).getOrElse(nothing(card)))

  /** Favor onto the People's Favor from the bank that lost it, or off it to
    * the bank that gained it. */
  private def restated(card: PowerSourceRef, step: NoteSupport.Step)
      : Option[LogNote] =
    val (before, after) = step
    val change = onBanner(after) - onBanner(before)
    if change > 0 then NoteSupport.bankPaid(step).map(suit => moved(card,
      NoteArg.Amount(change, NoteUnit.Favor), NoteArg.Bank(suit),
      NoteArg.Banner(Banner.PeoplesFavor)))
    else if change < 0 then Suit.all.find(suit =>
        stock(after, suit) > stock(before, suit)).map(suit => returned(card,
      NoteArg.Amount(-change, NoteUnit.Favor),
      NoteArg.Banner(Banner.PeoplesFavor), NoteArg.Bank(suit)))
    else None

object SingingMask:
  val id: PowerId = SingingMaskCard.power.id
  val cardDecisionId: String = "power.singing-mask.card"
  val directionDecisionId: String = "power.singing-mask.direction"
  val amountDecisionId: String = "power.singing-mask.amount"
  /** The most favor it moves. */
  val Most: Int = 2
  val toBanner: DecisionOptionRef.Button =
    DecisionOptionRef.Button("to-peoples-favor")
  val toBank: DecisionOptionRef.Button = DecisionOptionRef.Button("to-bank")
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Moved {n favor} from {the Beast bank} to the {People's Favor}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(" to the "), NotePart.Arg(2), NotePart.Text(".")))
  /** "Moved {n favor} from the {People's Favor} to {the Beast bank}." */
  val returned: NoteKey = notes.key("used.returned", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0), NotePart.Text(" from the "),
    NotePart.Arg(1), NotePart.Text(" to "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "No favor moved." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No favor moved.")))

  def forCatalog(catalog: ExecutableCatalog): SingingMask =
    new SingingMask(catalog)
