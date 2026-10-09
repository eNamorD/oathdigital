package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  ContributingPower, Contribution, Transform}
import oathdigital.model._

object FamilyWagonCard extends Denizen(DenizenId("168"), "Family Wagon", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.family-wagon"),
    persistent = true, cost = Cost.free,
    text = "You have no adviser limit but may only have [suit-nomad] " +
      "advisers-—bury other advisers you have or would gain.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Family Wagon (card 168, adviser-only): "You have no adviser limit but
  * may only have [Nomad] advisers--bury other advisers you have or would
  * gain."
  *
  * While its holder has it faceup:
  *  - card play gives the holder no limit. A transform at
  *    `SearchPlayAdviser` lifts the base limit while Family Wagon is a
  *    faceup adviser after the play (`CardPlayRules.liftWhileFaceUp`), and
  *    notes each adviser play after which it would bury the played card.
  *  - `AdviserLimit.of` gives the holder no limit, before the lowering
  *    powers apply (`lifts`).
  *  - an adviser arriving in the holder's advisers, by any route, makes the
  *    holder bury every adviser that is not a faceup Nomad, in held order
  *    (`onArrival`). Family Wagon's own arrival counts. A facedown card has
  *    no suit, so it is buried, and Locked does not stop a bury.
  *
  * A card it buries on arrival was never gained, so its WHEN PLAYED does
  * not run (`WhenPlayedPower`). The bury returns the card's favor to its
  * suit bank and its secrets to the active player, facedown. The line names
  * the holder only when the holder is not the active player, and covers the
  * generic "Buried" lines.
  */
final case class FamilyWagon private (catalog: ExecutableCatalog)
    extends ContributingPower:
  import FamilyWagon._
  def id: PowerId = FamilyWagon.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(buried, buriedByHolder)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(_.liftWhileFaceUp(cardId).noteAdviserPlay(
            AdviserPlayNote(OptionReason.Written(buryWarning),
              (after, played) => after.contains(faceUp) && !kept(played))))
        case _ => children
      })))

  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = advisers(arrived.after, holder)
        .filterNot(adviser => kept(AdviserFace.of(adviser)))
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(_, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** Whether `face` stays under Family Wagon: a faceup Nomad. */
  private def kept(face: AdviserFace): Boolean =
    face.orientation == Orientation.FaceUp &&
      catalog.suitOf(face.card).contains(Suit.Nomad)

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
    val wagon = PowerSourceRef.Card(cardId)
    if holder == actor then buried(wagon, NoteArg.Cards(cards))
    else buriedByHolder(wagon, NoteArg.Player(holder), NoteArg.Cards(cards))

object FamilyWagon:
  val id: PowerId = FamilyWagonCard.power.id
  val cardId: DenizenId = FamilyWagonCard.id
  /** The note on a card play Family Wagon will bury. */
  val buryWarning: String = "Family Wagon will bury it"
  /** "Buried {cards}.", on the holder's turn. */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "{Blue} buried {cards}.", when the holder is not the active player. */
  val buriedByHolder: NoteKey = NoteKey("buried.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): FamilyWagon =
    new FamilyWagon(catalog)

  /** Whether `player` has Family Wagon faceup, and so no adviser limit. */
  def lifts(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }

  private val faceUp = AdviserFace(cardId, Orientation.FaceUp)

  /** Whether `player` holds Family Wagon among their advisers, in either
    * face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists(_.id == cardId)

  /** The player who has Family Wagon faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.map(_.player).find(lifts(ready, _))

  private def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)
