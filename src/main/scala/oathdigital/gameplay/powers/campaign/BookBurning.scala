package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

object BookBurningCard extends Denizen(DenizenId("22"), "Book Burning", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.book-burning"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious in a raid, burn all [secret] on the " +
      "defender's board except their last [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Book Burning (card 22), an attacker's battle plan: "If you're victorious in
  * a raid, burn all [secret] on the defender's board except their last
  * [secret]."
  *
  * It is free, and offered only in a Raid. A Raid's defender is always a player,
  * whose pawn the attacker reached. Once the Campaign has resolved, if the
  * attacker won, every secret on the defender's board is burnt except one. Only
  * a faceup secret can be burnt, so the facedown ones go first: they are turned
  * faceup and burnt in the same step. The defender keeps one secret, faceup if
  * any remains. A defender with one secret or none loses nothing.
  *
  * When the attacker won it writes "Burned {n secrets} from {Blue}'s board.",
  * or "{Blue} had no secret to burn." when nothing was burnt.
  */
final case class BookBurning private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = BookBurning.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] =
    Vector(BookBurning.burned, BookBurning.none)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Raid)
      .map(source => CampaignPlanOffer(source,
        "Book Burning: burn the defender's secrets but one if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.result.map(_.defender).collect {
        case CampaignDefender.Player(defender) => defender
      }.toVector.flatMap(defender => Vector(
        BuildOps((ready, _) => Right(BookBurning.burn(ready, defender))),
        Note(id, states => states.previous.map { step =>
          val burnt = -NoteSupport.secrets(step, defender)
          if burnt > 0 then BookBurning.burned(PowerSourceRef.Card(cardId),
            NoteArg.Amount(burnt, NoteUnit.Secret), NoteArg.Player(defender))
          else BookBurning.none(PowerSourceRef.Card(cardId),
            NoteArg.Player(defender))
        })))))

object BookBurning:
  val id: PowerId = PowerId("denizen.book-burning")

  /** "Burned {n secrets} from {Blue}'s board." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text("'s board.")))

  /** "{Blue} had no secret to burn." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no secret to burn.")))

  /** Every secret on `defender`'s board but one, the facedown ones turned
    * faceup first. A batch runs its operations in order, so the burn sees the
    * secrets just turned. */
  private def burn(ready: ReadyGame, defender: PlayerId): Vector[CoreOperation] =
    ready.game.current.players.find(_.player == defender).toVector.flatMap {
      held =>
        val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - 1
        val turned = math.min(held.board.faceDownSecrets, burnt)
        if burnt <= 0 then Vector.empty
        else Option.when[CoreOperation](turned > 0)(FlipSecrets(defender,
          turned, SecretSide.FaceDown, SecretSide.FaceUp)).toVector :+
          Burn.secrets(burnt, PositionedLocation(Location.PlayArea(defender)))
    }

  def forCatalog(catalog: ExecutableCatalog): Option[BookBurning] =
    CatalogCards.denizen(catalog, id).map(new BookBurning(_))
