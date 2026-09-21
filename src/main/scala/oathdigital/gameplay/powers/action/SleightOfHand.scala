package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Sleight of Hand (card 17), ACTION: place 1 favor on this card, then take
  * one secret from a player whose pawn is at your site and who holds two or
  * more secrets, so their last secret is never taken. Faceup first,
  * otherwise facedown, arriving with the same orientation.
  *
  * The targets are read live, after the cost is paid. With none, the cost
  * stays paid and nothing else happens.
  *
  * The take is one `Take`. A target holding both orientations makes a bare
  * one-secret `Take` ambiguous (`AmbiguousSecretOrientation`), so the batch
  * flips the target's facedown secrets up first, takes, and flips the same
  * number back down. The net effect is exactly "one faceup secret moves".
  */
case object SleightOfHand extends PaidAction("denizen.sleight-of-hand",
    Cost(favor = 1)) {
  val decisionId: String = "power.sleight-of-hand.target"
  val MinimumSecrets: Int = 2

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => steal(live, player, pending)))))

  private def secretsOf(player: PlayerState): Int =
    player.board.faceUpSecrets + player.board.faceDownSecrets

  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site) && secretsOf(p) >= MinimumSecrets))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = targets(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some(
        "Sleight of Hand: take a secret from a player at your site"))))
  }

  private def steal(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = targets(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a legal Sleight of Hand target"))
    } yield take(actor, target)
  }

  private def take(actor: PlayerId, target: PlayerState)
      : Vector[CoreOperation] = {
    val one = Take(Piece.Secrets(1), actor, Location.PlayArea(target.player),
      Location.PlayArea(actor))
    val faceDown = target.board.faceDownSecrets
    if (target.board.faceUpSecrets > 0 && faceDown > 0) Vector(
      FlipSecrets(target.player, faceDown, SecretSide.FaceDown,
        SecretSide.FaceUp),
      one,
      FlipSecrets(target.player, faceDown, SecretSide.FaceUp,
        SecretSide.FaceDown))
    else Vector(one)
  }
}
