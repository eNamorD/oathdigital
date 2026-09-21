package oathdigital.gameplay.powers.action

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.gameplay.powers.{PlayerFacts, RelicDraws}
import oathdigital.model._

/** Fae Merchant (card 180), ACTION: place 1 secret on this card, draw a relic
  * and take it facedown, then put exactly one relic you hold, except the
  * Grand Scepter, on the bottom of the relic deck. The relic just taken is
  * eligible. The choice is asked only when more than one relic is eligible.
  *
  * Three siblings run in order: the draw, a live `Branch` that holds only the
  * decision and reads the relics after the draw, and the bury. The bury reads
  * the eligible relics again, so it needs the recorded answer only when the
  * decision was asked. It returns any secrets on the relic to their holder.
  */
final case class FaeMerchant private (scepters: Set[RelicId])
    extends PaidAction("denizen.fae-merchant", Cost(secret = 1)) {
  import FaeMerchant._

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Branch((state, _) => candidates(state, player) match {
      case several if several.size > 1 => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(several.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck"))))
      case _ => Vector.empty
    }),
    BuildOps((state, pending) => putBack(state, player, pending)))))

  /** The relics the player holds, in play-area order, that may go back. */
  private def candidates(state: ReadyGame, player: PlayerId): Vector[RelicId] =
    PlayerFacts.player(state, player).toOption.toVector.flatMap(_.relics)
      .map(_.id).filterNot(scepters)

  private def putBack(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val chosen: Either[OathViolation, Option[RelicId]] =
      candidates(state, player) match {
        case Vector() => Right(None)
        case Vector(only) => Right(Some(only))
        case _ => pending.answered.collectFirst {
          case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
              DecisionOptionRef.Relic(id)), _) => id
        }.toRight(OathViolation.InvalidEventOrder(
          "no Fae Merchant relic is recorded")).map(Some(_))
      }
    for {
      pick <- chosen
      held <- PlayerFacts.player(state, player)
    } yield pick.toVector.flatMap { id =>
      val secrets = held.relics.find(_.id == id).fold(0)(_.tokens.secrets)
      Bury.standard(BuryableCard.Relic(id),
        PositionedLocation(Location.PlayArea(player)), None, 0, secrets,
        player)
    }
  }
}

object FaeMerchant {
  val decisionId: String = "fae-merchant.relic"
  val id: PowerId = PowerId("denizen.fae-merchant")

  /** The Grand Scepter is read from the catalog's relic roles, not by name. */
  def forCatalog(catalog: ExecutableCatalog): FaeMerchant = new FaeMerchant(
    catalog.relics.filter(_.role == RelicRole.GrandScepter)
      .map(relic => RelicId(relic.id.value)).toSet)
}
