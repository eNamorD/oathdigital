package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Family Heirloom (card 133), WHEN PLAYED: draw a relic. Take it or put it
  * on the bottom of the relic deck.
  *
  * A relic cannot wait in a temporary hand (`temporaryHands` holds world
  * cards), so the draw puts it facedown in the player's play area, where
  * only they can identify it, and "bottom" buries it again. The three steps
  * sit in a once-guarded `Repeat`: the guard runs only at pass boundaries,
  * so the draw emptying the relic deck cannot make the walker lose the
  * parked decision on resume.
  */
final case class FamilyHeirloom private (cardId: DenizenId)
    extends WhenPlayedPower {
  import FamilyHeirloom._
  def id: PowerId = FamilyHeirloom.id

  def effect(ctx: PowerCtx): Vector[Operation] = {
    val actor = ctx.activePlayer
    Vector(Repeat(
      (ready, pending) => !asked(pending) &&
        ready.game.current.commonCards.relicDeck.nonEmpty,
      Sequence(Vector[Operation](
        BuildOps((ready, _) => draw(ready, actor)),
        Decide(decisionId, actor, DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(keep, "Take the relic"),
          DecisionOption.Button(bottom,
            "Put it on the bottom of the relic deck")),
          heading = Some("Family Heirloom: take the relic you drew, or put it " +
            "on the bottom of the relic deck"))),
        BuildOps((ready, pending) => settle(ready, actor, pending))))))
  }
}

object FamilyHeirloom {
  val id: PowerId = PowerId("denizen.family-heirloom")
  val decisionId: String = "cardplay.family-heirloom.keep"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  val bottom: DecisionOptionRef.Button = DecisionOptionRef.Button("bottom")

  def forCatalog(catalog: ExecutableCatalog): Option[FamilyHeirloom] =
    WhenPlayedPower.cardOf(catalog, id).map(new FamilyHeirloom(_))

  private def asked(pending: PendingTree): Boolean =
    pending.answered.exists(_.decisionId == decisionId)

  private def draw(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable("relic deck is empty")).map(relic =>
      Vector(Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown)))

  private def settle(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match {
      case Some(`bottom`) => for {
        held <- PlayerFacts.player(ready, actor)
        drawn <- held.relics.lastOption.toRight(OathViolation.InvalidEventOrder(
          "no drawn relic to put back"))
      } yield Vector(Bury(BuryableCard.Relic(drawn.id),
        PositionedLocation(Location.PlayArea(actor))))
      case Some(`keep`) => Right(Vector.empty)
      case _ => Left(OathViolation.InvalidEventOrder(
        "no Family Heirloom choice is recorded"))
    }
}
