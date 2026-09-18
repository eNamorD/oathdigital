package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.{MajorActionType, PowerWindow}

/** Reviewed-but-unimplemented Recover classifications. Every power that
  * actually contributes to a Recover procedure -- Catacombs today -- is a
  * `ContributingPower` on the generic walker (see
  * [[oathdigital.gameplay.powers.recover.CatacombsContribution]], wired
  * through [[oathdigital.gameplay.powers.WalkerPowerCatalog]]); these four
  * remain catalog entries only so the audited-power inventory stays complete,
  * with no handler of their own yet.
  */
object RecoverPowers {
  private val modifier = Some(MajorActionType.Recover)

  object RelicWorship extends ReviewedPower("denizen.relic-worship", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E13Ruined extends ReviewedPower("edifice.e13.ruined", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E17Intact extends ReviewedPower("edifice.e17.intact", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E17Ruined extends ReviewedPower("edifice.e17.ruined", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))

  val powers: Vector[Power] = Vector(RelicWorship, E13Ruined, E17Intact,
    E17Ruined)
}
