package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Ivory Eye (relic R16), ACTION: place 1 secret on this relic, then peek at
  * any facedown adviser, a Vision included, of any player, the acting
  * player's included.
  *
  * A `Peek` is the whole disclosure. Replaying it records the card in the
  * viewer's knowledge, and the presentation layer then names that facedown
  * adviser to the viewer alone.
  *
  * The options are `Button`s keyed by owner and adviser slot rather than card
  * references. A card option names the card's identity, and the projector
  * drops any decision that names a card its viewer may not identify, which a
  * facedown adviser of another player is. The slot is read from live state, so
  * an answer cannot name a card that has since moved.
  */
case object IvoryEye extends PaidAction("relic.ivory-eye", Cost(secret = 1)) {
  val decisionId: String = "power.ivory-eye.adviser"
  private val Prefix = "adviser:"

  /** The option for the facedown adviser in position `slot` of `owner`'s
    * advisers.
    */
  def optionFor(owner: PlayerId, slot: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"$Prefix${owner.value}:$slot")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => peek(live, player, pending)))))

  private final case class Target(owner: PlayerId, slot: Int,
      card: WorldCardId) {
    def ref: DecisionOptionRef.Button = optionFor(owner, slot)
    def label: String = s"${owner.value}: facedown adviser ${slot + 1}"
  }

  private def targets(ready: ReadyGame): Vector[Target] = for {
    player <- ready.game.current.players
    (adviser, slot) <- player.advisers.zipWithIndex
    card <- adviser match {
      case DenizenState(id, Orientation.FaceDown, _) => Some(id: WorldCardId)
      case VisionState(id, Orientation.FaceDown) => Some(id: WorldCardId)
      case _ => None
    }
  } yield Target(player.player, slot, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = targets(ready)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(t => DecisionOption.Button(t.ref, t.label)),
      heading = Some("Ivory Eye: peek at a facedown adviser"))))
  }

  private def peek(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = targets(ready)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a facedown adviser"))
    } yield Vector(Peek(actor, target.card, Location.PlayArea(target.owner)))
  }
}
