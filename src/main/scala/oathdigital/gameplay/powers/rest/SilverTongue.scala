package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceRef}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Silver Tongue (card 92): "You can only have two advisers. REST: Take a
  * favor from a favor bank matching a card at your site."
  *
  * The REST power is a [[PhasePower]]. The adviser limit is a registered
  * [[Restriction]] at `SearchPlayFacedownAdviser`, inert until Search walks
  * that window. It counts the advisers the holder would have once the
  * tree's visible card moves land (Corrections 12).
  */
final case class SilverTongue private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PhasePower with ContributingPower {
  import SilverTongue._

  def id: PowerId = SilverTongue.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = stocked(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = {
    val choice = choiceDecisionId(ready, player)
    Right(Branch((state, _) => stocked(state, player) match {
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
    }))
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayFacedownAdviser -> Vector(Restriction((ctx, tree) =>
      holder(ctx.state).filter(advisersAfter(_, tree) > 2).map(player =>
        OathViolation.InvalidEventOrder(s"${player.player.value} holds " +
          "Silver Tongue and can have only two advisers")))))

  private def holder(ready: ReadyGame): Option[PlayerState] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  /** The holder's adviser count once the tree's statically visible world-card
    * moves land. `BuildOps` leaves compute their operations at walk time, so
    * a restriction cannot see them (Corrections 12).
    */
  /** Suits of faceup denizens and edifices at the player's pawn site whose
    * bank holds favor, in suit order.
    */
  private def stocked(ready: ReadyGame, player: PlayerId): Vector[Suit] = {
    val current = ready.game.current
    val cards = current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(current.map.sites.get).toVector.flatMap(_.denizens.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card: CardId
        case EdificeState(card, _, _) => card: CardId
      })
    val suits = cards.flatMap(RestCleanupPlan.suitOf(catalog, _)).toSet
    Suit.all.filter(suit => suits(suit) && ready.banks.favor.getOrElse(suit, 0) > 0)
  }

  private def take(player: PlayerId,
      suit: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((_, pending) => suit(pending).map(bank => Vector(Move(
      Piece.Favor(1), PositionedLocation(Location.FavorBank(bank)),
      PositionedLocation(Location.PlayArea(player))))))
}

object SilverTongue {
  val id: PowerId = PowerId("denizen.silver-tongue")
  def forCatalog(catalog: ExecutableCatalog): Option[SilverTongue] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new SilverTongue(DenizenId(d.id.value), catalog))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"silver-tongue-${ready.game.current.tracks.round}-${player.value}"

  private[rest] def advisersAfter(holder: PlayerState, tree: Operation): Int = {
    def leaves(operation: Operation): Vector[Operation] = operation match {
      case leaf: PrimitiveOperation => Vector(leaf)
      case composite => composite.children.flatMap(leaves)
    }
    val area = Location.PlayArea(holder.player)
    leaves(tree).foldLeft(holder.advisers.size) {
      case (count, Move(Piece.Card(_: WorldCardId), from, to, _)) =>
        count + (if (to.location == area) 1 else 0) -
          (if (from.location == area) 1 else 0)
      case (count, _) => count
    }
  }
}
