package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier}
import oathdigital.model._

object DefameCard extends Denizen(DenizenId("102"), "Defame", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.defame"),
    persistent = false, cost = Cost.free,
    text = "After trading with a [suit-discord] card, burn " +
      "[favor-burnt] from any player board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Defame (card 102), a selected Trade modifier with no cost: after trading
  * with a Discord card, burn 1 favor from any player's board.
  *
  * One `Branch` is appended to the Trade's root, as Moving Market's is: a
  * decision inside the Economy `Branch` would fail on resume. It asks only
  * when the source was Discord, a denizen or an edifice by its suit, and some
  * player holds favor. The options are the players with favor, the actor
  * included, in seat order, then Skip when some player holds none, since
  * burning from an empty board is legal and burns nothing. Skip writes
  * nothing.
  *
  * Its line names the chosen player, the actor included: "Burned {1 favor}
  * from {Blue}'s board." No generic line exists for a burn.
  */
final case class Defame private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import Defame._

  val cardId: DenizenId = DefameCard.id
  def id: PowerId = Defame.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Defame.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.activePlayer))))

  private def offer(actor: PlayerId): Operation = Branch((ready, pending) =>
    val players = ready.game.current.players
    val holding = players.filter(_.board.favor > 0).map(_.player)
    if !discordSource(pending) || holding.isEmpty then Vector.empty
    else
      val options: Vector[DecisionOption] = holding.map(player =>
        DecisionOption.Player(DecisionOptionRef.Player(player))) ++
        Option.when(holding.size < players.size)(
          DecisionOption.Button(skip, "Skip")).toVector
      Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
          heading = Some("Defame: burn 1 favor from a player's board"))),
        BuildOps((_, answered) => burn(actor, answered)),
        Note(id, burnedNote)))

  private def discordSource(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId).flatMap {
      case DecisionOptionRef.Denizen(card) => catalog.suitOf(card)
      case DecisionOptionRef.Edifice(card) => catalog.suitOf(card)
      case _ => None
    }.contains(Suit.Discord)

  private def burn(actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`skip`) => Right(Vector.empty)
      case Some(DecisionOptionRef.Player(target)) => Right(Vector(
        Burn.favor(Burned, PositionedLocation(Location.PlayArea(target)),
          Some(actor))))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a player or Skip"))
      case None => Left(PowerAnswers.missing(decisionId))

  /** The favor the burn's step took from the chosen board. */
  private def burnedNote(states: NoteStates): Option[PowerNote] = for
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, decisionId)
    step <- states.previous
    amount = -NoteSupport.favor(step, target)
    if amount > 0
  yield burned(PowerSourceRef.Card(cardId),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(target))

object Defame:
  val id: PowerId = DefameCard.power.id
  /** Under the `trade.` prefix, as Moving Market's is. */
  val decisionId: String = "trade.defame.burn"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  val Burned: Int = 1
  private val notes = NoteSet(id)
  /** "Burned {1 favor} from {Blue}'s board." */
  val burned: NoteKey = notes.key("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text("'s board.")))
  def noteKeys: Vector[NoteKey] = notes.declared

  def forCatalog(catalog: ExecutableCatalog): Defame = new Defame(catalog)
