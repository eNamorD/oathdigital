package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object ShiftingFogCard extends Denizen(DenizenId("214"), "Shifting Fog", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.shifting-fog"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** move all favor in each favor bank to the " +
      "next bank to the right. [suit-nomad] moves to " +
      "[suit-discord].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Shifting Fog (card 214), WHEN PLAYED: move all favor in each favor bank
  * to the next bank to the right. Nomad's moves to Discord's.
  *
  * The move is simultaneous. Each bank sends what it held before any move,
  * all read live when the step runs, so a bank that receives favor first
  * still sends only its own. The effect is one `Branch`, so the node count
  * this power adds to the card-played window never depends on live state.
  * With every bank empty nothing moves and no line is written.
  */
final case class ShiftingFog private (cardId: DenizenId)
    extends WhenPlayedPower:
  import ShiftingFog._
  def id: PowerId = ShiftingFog.id

  override def noteKeys: Vector[NoteKey] = Vector(moved)

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(Branch((live, _) =>
    if moves(live).isEmpty then Vector.empty
    else Vector(BuildOps((ready, _) => Right(moves(ready))),
      Note(id, _ => Some(moved(PowerSourceRef.Card(cardId)))))))

object ShiftingFog:
  val id: PowerId = PowerId("denizen.shifting-fog")
  /** "Every bank's favor moved to the next bank." */
  val moved: NoteKey = NoteKey("moved", Vector(
    NotePart.Text("Every bank's favor moved to the next bank.")))

  def forCatalog(catalog: ExecutableCatalog): Option[ShiftingFog] =
    WhenPlayedPower.cardOf(catalog, id).map(new ShiftingFog(_))

  /** The bank to the right of `suit`, in the printed order. */
  def next(suit: Suit): Suit =
    Suit.all((Suit.all.indexOf(suit) + 1) % Suit.all.size)

  /** One move per stocked bank, each of what that bank holds now. */
  def moves(ready: ReadyGame): Vector[CoreOperation] = Suit.all.flatMap { suit =>
    val held = ready.banks.favor.getOrElse(suit, 0)
    Option.when(held > 0)(Move(Piece.Favor(held),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.FavorBank(next(suit)))))
  }
