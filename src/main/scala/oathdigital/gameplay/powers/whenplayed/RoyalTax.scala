package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Royal Tax (card 117), WHEN PLAYED: take 2 favor from each player whose
  * pawn is at a site you rule in your pawn's region.
  *
  * The targets are read live: every other player, in seat order from the
  * actor, whose pawn stands at a site the actor rules in the region of the
  * actor's pawn. Nothing is asked. Each target gives 2 favor by a `Take`, or
  * what they hold, with a line of its own. A target holding no favor is
  * named instead. A take a restriction refuses writes no line.
  */
final case class RoyalTax private (cardId: DenizenId)
    extends WhenPlayedPower:
  import RoyalTax._
  def id: PowerId = RoyalTax.id

  override def noteKeys: Vector[NoteKey] = Vector(took, broke, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = targets(live, actor)
      if found.isEmpty then Vector(Note(id, _ => Some(none(cardSource))))
      else found.flatMap(tax(actor, _))))

  private def tax(actor: PlayerId, target: PlayerState): Vector[Operation] =
    if target.board.favor == 0 then Vector(Note(id, _ =>
      Some(broke(cardSource, NoteArg.Player(target.player)))))
    else Vector(
      BuildOps((_, _) => Right(Vector(Take(Piece.Favor(Taken), actor,
        Location.PlayArea(target.player), Location.PlayArea(actor))))),
      Note(id, tookNote(_, actor, target.player)))

  /** The favor the take step moved from `target`. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      target: PlayerId): Option[PowerNote] =
    val lost = -states.previous.fold(0)(NoteSupport.favor(_, target))
    Option.when(lost > 0)(took(cardSource, NoteArg.Player(actor),
      NoteArg.Amount(lost, NoteUnit.Favor), NoteArg.Player(target)))

object RoyalTax:
  val id: PowerId = PowerId("denizen.royal-tax")
  /** The favor each target gives. */
  val Taken: Int = 2
  /** "{Red} took {n favor} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Blue} had no favor to take." */
  val broke: NoteKey = NoteKey("broke", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to take.")))
  /** "No player could be taxed." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No player could be taxed.")))

  def forCatalog(catalog: ExecutableCatalog): Option[RoyalTax] =
    WhenPlayedPower.cardOf(catalog, id).map(new RoyalTax(_))

  /** Every other player, in seat order from the actor, whose pawn stands at
    * a site the actor rules in the region of the actor's pawn. */
  private def targets(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    val current = ready.game.current
    val region = current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)
    val taxed = PowerAccess.ruledSites(ready, actor)
      .filter(site => region.exists(current.map.regionOf(site).contains))
    val seat = current.players.indexWhere(_.player == actor)
    (current.players.drop(seat + 1) ++ current.players.take(seat))
      .filter(_.pawnSite.exists(taxed.contains))
