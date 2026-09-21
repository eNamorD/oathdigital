package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Conspiracy: when played, take a relic or a banner from a player whose pawn
  * is at the actor's site, then return the card to the box.
  *
  * The card is played faceup from either origin, a Search's temporary hand or
  * a facedown adviser, and card play places nothing for it, so it is still at
  * its origin when this transform runs. The transform adds, after whatever
  * other powers contributed:
  *  - a `Decide` offering each legal target, only when there is at least one;
  *  - the take effects for the chosen target, then the card's removal, in one
  *    batch, so the card leaves the game even when there was nothing to take.
  *
  * A legal target cannot be declined. The decision id sits under the
  * `cardplay.` prefix, which the registry already maps to a prompt
  * continuation for both Search and the facedown-adviser play.
  *
  * The transform must fold to the same vector while the decision is parked: it
  * reads only state that nothing between the fold and the answer changes.
  */
case object ConspiracyWhenPlayed extends ContributingPower {
  val id: PowerId = PowerId("vision.conspiracy")
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val decisionId: String = "cardplay.conspiracy.target"

  override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card, _) => card == VisionRules.Conspiracy
    case _ => false
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      (children ++ targetDecision(ctx.state, ctx.activePlayer)) :+
        BuildOps((ready, pending) =>
          effects(ready, ctx.activePlayer, pending)))))

  /** Every relic slot and banner held by another player whose pawn is at the
    * actor's site, in seat order, relic slots before banners.
    */
  private def legalTargets(ready: ReadyGame, actor: PlayerId)
      : Vector[DecisionOptionRef] = {
    val current = ready.game.current
    val site = current.players.find(_.player == actor).flatMap(_.pawnSite)
    current.players.filter(other => other.player != actor &&
      other.pawnSite == site).flatMap { other =>
      other.relics.indices.map(slot =>
        DecisionOptionRef.RelicSlot(other.player, slot)) ++
        Banner.all.filter(banner => BannerRules.holder(current, banner)
          .contains(other.player)).map(banner => DecisionOptionRef.Banner(banner))
    }
  }

  private def targetDecision(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = {
    val options = legalTargets(ready, actor).flatMap(DecisionOption.forRef)
    if (options.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
      heading = Some("Conspiracy: choose an enemy asset to take"))))
  }

  private def effects(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val legal = legalTargets(ready, actor)
    val chosen = pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }
    val taken: Either[OathViolation, Vector[CoreOperation]] = chosen match {
      case Some(ref) if legal.contains(ref) => Right(take(ready, actor, ref))
      case Some(_) => Left(OathViolation.ConspiracyUnavailable(
        "the chosen target is no longer legal"))
      case None if legal.isEmpty => Right(Vector.empty)
      case None => Left(OathViolation.ConspiracyUnavailable(
        "a legal target must be chosen"))
    }
    taken.map(_ :+ removal(ready, actor))
  }

  private def take(ready: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : Vector[CoreOperation] = {
    val current = ready.game.current
    def banner(held: Banner, owner: PlayerId): CoreOperation = Move(
      Piece.Banner(held), PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.PlayArea(actor)))
    ref match {
      case DecisionOptionRef.RelicSlot(owner, slot) =>
        current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .toVector.map(relic => Give(Piece.Card(relic.id), owner,
            Location.PlayArea(owner), Location.PlayArea(actor)))
      case DecisionOptionRef.Banner(held) =>
        BannerRules.holder(current, held).toVector.flatMap { owner =>
          val leaving: Vector[CoreOperation] = held match {
            case Banner.PeoplesFavor => BannerRules.raidFavorReturn(
              ready.banks.favor, BannerRules.resources(current, held))
              .map(suit => Move(Piece.Favor(1),
                PositionedLocation(Location.OnBanner(held)),
                PositionedLocation(Location.FavorBank(suit))))
            case Banner.DarkestSecret =>
              val secrets = BannerRules.resources(current, held)
              Option.when(secrets > 0)(Burn.secrets(secrets,
                PositionedLocation(Location.OnBanner(held)))).toVector
          }
          leaving :+ banner(held, owner)
        }
      case _ => Vector.empty
    }
  }

  /** The card leaves the game from wherever the actor holds it. */
  private def removal(ready: ReadyGame, actor: PlayerId): CoreOperation = {
    val inHand = ready.game.current.temporaryHands
      .getOrElse(actor, Vector.empty).contains(VisionRules.Conspiracy)
    Move(Piece.Card(VisionRules.Conspiracy),
      PositionedLocation(if (inHand) Location.Hand(actor)
        else Location.PlayArea(actor)),
      PositionedLocation(Location.SharedBank))
  }
}
