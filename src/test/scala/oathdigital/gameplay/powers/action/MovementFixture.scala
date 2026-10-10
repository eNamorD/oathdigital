package oathdigital.gameplay.powers.action

import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, MenuPlay, PhasePowerHarness, Table}

/** Staging and driving shared by the Whistle, Brass Horse and Magic Carpet
  * suites, on the quiet table: the actor p1 at Ancient City, p2 at Broken
  * Peaks and p3 at Buried Giant. `homelands` adds the ruined edifices the
  * first game stands at its two Homelands. The suites read the parked
  * decision and the recorded operations through `PhasePowerHarness`.
  */
object MovementFixture:
  import PhasePowerHarness.rules
  private val actor: PlayerId = Table.p1

  /** A ruined Beast edifice (Hiding Place) at Deep Woods and a ruined Hearth
    * one (Squalid District) at Golden Valley, as the first game stands them
    * at its Homelands; no other card lies at any site. */
  def homelands(ready: ReadyGame): ReadyGame = Table.from(ready)
    .edifice("Hiding Place", EdificeSide.Ruined, at = "Deep Woods")
    .edifice("Squalid District", EdificeSide.Ruined, at = "Golden Valley")
    .unchecked

  /** The relic `id` in `holder`'s play area, if it is there. */
  def relicOf(ready: ReadyGame, id: RelicId,
      holder: PlayerId = actor): Option[RelicState] =
    Look(ready).player(holder).relics.find(_.id == id)

  /** The actor uses `power` of their relic `relic`. */
  def use(ready: ReadyGame, power: PowerId, relic: RelicId)
      : Either[OathViolation, OathTransition] = rules.startWalker(Ready(MenuPlay.withoutMenu(ready)),
    ActionRef.UsePower(power), actor, Vector(DecisionOptionRef.Relic(relic)))

  def choose(state: OathState, decisionId: String, ref: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(state,
    actor, decisionId, DecisionAnswer.ChooseOneAnswer(ref))
