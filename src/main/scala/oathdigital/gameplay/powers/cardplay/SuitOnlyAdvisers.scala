package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote,
  CardPlayRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower, Contribution, Transform}
import oathdigital.model._

/** A rule that its holder may have only faceup advisers of one suit, and
  * buries every other adviser they have or would gain: Family Wagon's
  * Nomads and Vow of Union's Beasts.
  *
  * While its holder has the card faceup:
  *  - card play notes each adviser play after which the rule would bury the
  *    played card (`buryWarning`), through a transform at
  *    `SearchPlayAdviser`. `playRules` adds the card's other card-play
  *    rules, such as Family Wagon's lifted limit.
  *  - an adviser arriving in the holder's advisers, by any route, makes the
  *    holder bury every adviser that is not a faceup card of `suit`, in held
  *    order (`onArrival`). The card's own arrival counts. A facedown card
  *    has no suit, so it is buried, and Locked does not stop a bury.
  *
  * A card it buries on arrival was never gained, so its WHEN PLAYED does
  * not run (`WhenPlayedPower`). The bury returns the card's favor to its
  * suit bank and its secrets to the active player, facedown. The line names
  * the holder only when the holder is not the active player, and covers the
  * generic "Buried" lines.
  */
trait SuitOnlyAdvisers extends ContributingPower:
  def catalog: ExecutableCatalog
  /** The card that sets the rule, held as an adviser. */
  def cardId: DenizenId
  /** The suit of the advisers its holder keeps. */
  def suit: Suit
  /** The note on a card play the rule will bury. */
  def buryWarning: String
  /** The card-play rules the card sets besides its bury note. */
  protected def playRules(rules: CardPlayRules): CardPlayRules = rules

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  private val notes = NoteSet(id)
  notes.include(SuitOnlyAdvisers.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(rules => playRules(rules).noteAdviserPlay(
            AdviserPlayNote(OptionReason.Written(buryWarning),
              (after, played) => after.contains(faceUp) && !kept(played))))
        case _ => children
      })))

  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = SuitOnlyAdvisers.advisers(arrived.after, holder)
        .filterNot(adviser => kept(AdviserFace.of(adviser)))
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(_, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** Whether `face` stays under the rule: a faceup card of `suit`. */
  private def kept(face: AdviserFace): Boolean =
    face.orientation == Orientation.FaceUp &&
      catalog.suitOf(face.card).contains(suit)

  /** The bury of `adviser` from `holder`'s advisers, returning its favor to
    * its suit bank and its secrets to `actor`, facedown. */
  private def bury(adviser: AdviserState, holder: PlayerId, actor: PlayerId)
      : Vector[CoreOperation] =
    val from = PositionedLocation(Location.PlayArea(holder))
    adviser match
      case DenizenState(card, _, tokens) => Bury.standard(
        BuryableCard.Denizen(card), from, catalog.suitOf(card), tokens.favor,
        tokens.secrets, actor)
      case VisionState(card, _) => Bury.standard(BuryableCard.Vision(card),
        from, None, favor = 0, secrets = 0, actor)

  private def line(holder: PlayerId, actor: PlayerId, cards: Vector[CardId])
      : PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then SuitOnlyAdvisers.buried(card, NoteArg.Cards(cards))
    else SuitOnlyAdvisers.buriedByHolder(card, NoteArg.Player(holder),
      NoteArg.Cards(cards))

  private def faceUp: AdviserFace = AdviserFace(cardId, Orientation.FaceUp)

  /** Whether `player` holds the card among their advisers, in either
    * face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    SuitOnlyAdvisers.advisers(ready, player).exists(_.id == cardId)

  /** The player who has the card faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.map(_.player)
      .find(SuitOnlyAdvisers.hasFaceUp(ready, _, cardId))

object SuitOnlyAdvisers:
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("SuitOnlyAdvisers")
  /** "Buried {cards}.", on the holder's turn. */
  val buried: NoteKey = notes.key("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "{Blue} buried {cards}.", when the holder is not the active player. */
  val buriedByHolder: NoteKey = notes.key("buried.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(".")))

  /** Whether `player` has `card` faceup among their advisers. */
  def hasFaceUp(ready: ReadyGame, player: PlayerId, card: DenizenId)
      : Boolean = advisers(ready, player).exists {
    case DenizenState(held, Orientation.FaceUp, _) => held == card
    case _ => false
  }

  private[cardplay] def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)
