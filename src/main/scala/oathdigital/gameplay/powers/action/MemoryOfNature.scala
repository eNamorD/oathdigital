package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object MemoryOfNatureCard extends Denizen(DenizenId("191"), "Memory of Nature", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.memory-of-nature"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Move a total of X [favor] from any favor banks " +
      "to the [suit-beast] bank. X is the number of [suit-beast] " +
      "cards on the map.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Memory of Nature (card 191), ACTION: place 1 secret on this card, then
  * move a total of X favor from any favor banks to the Beast bank, where X
  * is the number of Beast cards on the map.
  *
  * X counts the faceup Beast denizens and the Beast edifices, on either
  * face, at the sites in play: Memory of Nature itself when it stands at a
  * site. The favor comes from the five other banks, X or all they hold. The
  * player splits it only when there is a choice ([[FavorSplit]]). Its line
  * reads what the Beast bank gained.
  */
final case class MemoryOfNature private (catalog: ExecutableCatalog)
    extends PaidAction(MemoryOfNatureCard.power):
  import MemoryOfNature._

  override def noteKeys: Vector[NoteKey] = Vector(moved, unmoved)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch { (live, _) =>
      val x = beasts(live)
      banks.ask(live, player, x,
        s"Memory of Nature: move $x favor to the Beast bank")
    },
    BuildOps((live, pending) => banks.split(live, pending, beasts(live)).map(
      _.map { case (suit, n) => Move(Piece.Favor(n),
        PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.FavorBank(Suit.Beast))) })),
    Note(this.id, movedNote(_, source)))))

  /** The Beast cards on the map: faceup denizens, and edifices on either
    * face, at the sites in play. */
  private def beasts(ready: ReadyGame): Int =
    val map = ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case EdificeState(card, _, _) => catalog.suitOf(card).contains(Suit.Beast)
      case _ => false
    }

  /** What the Beast bank gained in the step before the note. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map { card =>
    val gained = states.previous.fold(0)((before, after) =>
      after.banks.favor.getOrElse(Suit.Beast, 0) -
        before.banks.favor.getOrElse(Suit.Beast, 0))
    if gained > 0 then moved(card, NoteArg.Amount(gained, NoteUnit.Favor),
      NoteArg.Bank(Suit.Beast))
    else unmoved(card, NoteArg.Bank(Suit.Beast))
  }

object MemoryOfNature:
  val id: PowerId = PowerId("denizen.memory-of-nature")
  val decisionId: String = "power.memory-of-nature.banks"
  /** "Moved {n favor} to {the Beast bank}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No favor moved to {the Beast bank}." */
  val unmoved: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No favor moved to "), NotePart.Arg(0), NotePart.Text(".")))
  private val banks = new FavorSplit(decisionId,
    Suit.all.filterNot(_ == Suit.Beast), "Move favor")

  def forCatalog(catalog: ExecutableCatalog): Option[MemoryOfNature] =
    CatalogCards.denizen(catalog, id).map(_ => new MemoryOfNature(catalog))
