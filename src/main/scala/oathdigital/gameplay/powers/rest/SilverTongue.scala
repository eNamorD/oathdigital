package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Silver Tongue (card 92): "You can only have two advisers. REST: Take a
  * favor from a favor bank matching a card at your site."
  *
  * The REST power is a [[PhasePower]]. The adviser limit is a registered
  * transform at `SearchPlayAdviser` reduces the holder's limit in both
  * adviser orientations and checks the resulting area after the card play.
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
    Map(PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if limitFor(ctx.state, ctx.activePlayer).nonEmpty =>
          tree.adjust(children)(_.limitAdvisers(HolderLimit)) :+
            limitGuard(ctx.activePlayer)
        case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
          tree.adjust(children)(_.limitFaceupAdvisers(HolderLimit)) :+
            limitGuard(ctx.activePlayer)
        case _ => children
      })))

  /** The adviser limit Silver Tongue sets on `player`: its holder, and only
    * while it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    holder(ready).filter(_.player == player).map(_ => HolderLimit)

  private def limitGuard(actor: PlayerId): Operation = BuildOps((state, _) => {
    val count = state.game.current.players.find(
      _.player == actor).fold(0)(_.advisers.size)
    if (limitFor(state, actor).forall(count <= _)) Right(Vector.empty)
    else Left(OathViolation.InvalidEventOrder(
      s"${actor.value} holds Silver Tongue and can have only two advisers"))
  })

  private def holder(ready: ReadyGame): Option[PlayerState] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

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
    val suits = cards.flatMap(catalog.suitOf(_)).toSet
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
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  def forCatalog(catalog: ExecutableCatalog): Option[SilverTongue] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new SilverTongue(DenizenId(d.id.value), catalog))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"silver-tongue-${ready.game.current.tracks.round}-${player.value}"

}
