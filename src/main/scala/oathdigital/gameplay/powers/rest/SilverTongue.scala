package oathdigital.gameplay.powers.rest

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object SilverTongueCard extends Denizen(DenizenId("92"), "Silver Tongue", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.silver-tongue"),
    persistent = false, cost = Cost.free,
    text = "You can only have two advisers. **REST:** Take [favor] from " +
      "a favor bank matching a card at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Silver Tongue (card 92): "You can only have two advisers. REST: Take a
  * favor from a favor bank matching a card at your site."
  *
  * The REST power is a [[oathdigital.gameplay.powerresolver.PhasePower]]. The
  * adviser limit is a [[HolderAdviserLimit]], a registered transform at
  * `SearchPlayAdviser`, which Insomnia shares.
  */
final case class SilverTongue private (catalog: ExecutableCatalog)
    extends PhasePower with ContributingPower:
  val cardId: DenizenId = SilverTongueCard.id
  import SilverTongue._

  private val limit = HolderAdviserLimit(cardId, HolderLimit, "Silver Tongue")

  def id: PowerId = SilverTongue.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = stocked(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = choiceDecisionId(ready, player)
    Right(Sequence(Vector(Branch((state, _) => stocked(state, player) match {
      case Vector(only) => Vector(take(player, _ => Right(only)))
      case several => Vector(
        Decide(choice, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some("Silver Tongue: take a favor from a bank"))),
        take(player, pending => pending.answered.collectFirst {
          case Answered(`choice`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.FavorBank(suit)), _) => suit
        }.toRight(OathViolation.InvalidEventOrder(
          s"no Silver Tongue bank is recorded for $choice"))))
    }),
      Note(id, NoteSupport.tookNote(source, player), covers = true))))

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(limit.contribution))

  /** The adviser limit Silver Tongue sets on `player`: its holder, and only
    * while it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    limit.limitFor(ready, player)

  /** Suits of faceup denizens and edifices at the player's pawn site whose
    * bank holds favor, in suit order.
    */
  private def stocked(ready: ReadyGame, player: PlayerId): Vector[Suit] =
    val current = ready.game.current
    val cards = current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(current.map.sites.get).toVector.flatMap(_.denizens.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card: CardId
        case EdificeState(card, _, _) => card: CardId
      })
    val suits = cards.flatMap(catalog.suitOf(_)).toSet
    Suit.all.filter(suit => suits(suit) && ready.banks.favor.getOrElse(suit, 0) > 0)

  private def take(player: PlayerId,
      suit: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((_, pending) => suit(pending).map(bank => Vector(Move(
      Piece.Favor(1), PositionedLocation(Location.FavorBank(bank)),
      PositionedLocation(Location.PlayArea(player))))))

object SilverTongue:
  val id: PowerId = SilverTongueCard.power.id
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  def forCatalog(catalog: ExecutableCatalog): SilverTongue =
    new SilverTongue(catalog)

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"silver-tongue-${ready.game.current.tracks.round}-${player.value}"
