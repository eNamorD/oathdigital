package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** Marble Fountains (edifice E15, intact), WAKE: if your pawn is at this
  * site, refresh your Supply to the leftmost space. `GainSupply` clamps at
  * the track maximum, so gaining the maximum refreshes it. Wake powers are
  * once per turn, which the engine enforces.
  */
case object MarbleFountains extends PhasePower {
  val id: PowerId = PowerId("edifice.e15.intact")
  def timing: PowerTiming = PowerTiming.Wake

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = source match {
    case DecisionOptionRef.Edifice(edifice) =>
      PowerAccess.pawnSite(ready, player).flatMap(ready.game.current.map.sites.get)
        .exists(_.denizens.exists {
          case card: EdificeState => card.id == edifice
          case _ => false
        })
    case _ => false
  }

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(GainSupply(player, SupplyTrack.Maximum))
}
