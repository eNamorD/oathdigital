package oathdigital.gameplay.powers.title

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

/** Chaos Cult (card 101, adviser-only), a rule of a faceup adviser: "After
  * another player takes the Oathkeeper title, you take [favor] from them."
  *
  * A `Transform` on the title-change window (catalog batch 2, N7) appends a
  * take after the change. The take reads the new holder live, so it runs
  * after the `SetOathkeeper` of either the forced transfer or the holder's
  * choice. It takes 1 favor with `Take` when the new holder is another
  * player with favor, and nothing otherwise. The appended nodes depend only
  * on who holds Chaos Cult faceup, which a title change does not alter, so
  * the fold is the same while the choice is parked.
  *
  * The card is catalogued persistent, a rule rather than a modifier, so its
  * resolution is the trait default, `Automatic`: it fires on every title
  * change.
  */
final case class ChaosCult private (cardId: DenizenId)
    extends ContributingPower:
  import ChaosCult._
  def id: PowerId = ChaosCult.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(took)

  override def applicable(ctx: PowerCtx): Boolean = holderOf(ctx.state).nonEmpty

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.OathkeeperTitleChange -> Vector(Transform((ctx, children) =>
      holderOf(ctx.state).fold(children)(holder => children ++ Vector(
        BuildOps((live, _) => Right(take(live, holder))),
        Note(id, tookNote(holder)))))))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  private def take(ready: ReadyGame, holder: PlayerId): Vector[CoreOperation] =
    ready.game.current.title.holder.filter(_ != holder)
      .flatMap(taker => ready.game.current.players.find(_.player == taker))
      .filter(_.board.favor > 0).toVector.map(from => Take(Piece.Favor(Favor),
        holder, Location.PlayArea(from.player), Location.PlayArea(holder)))

  /** What the holder gained in the take step, from the new Oathkeeper. */
  private def tookNote(holder: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    amount = NoteSupport.favor(step, holder)
    if amount > 0
    from <- step._2.game.current.title.holder
  yield took(PowerSourceRef.Card(cardId), NoteArg.Player(holder),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(from))

object ChaosCult:
  val id: PowerId = PowerId("denizen.chaos-cult")
  val Favor: Int = 1
  /** "{Red} took {1 favor} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): Option[ChaosCult] =
    CatalogCards.denizen(catalog, id).map(new ChaosCult(_))
