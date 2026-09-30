package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction}
import oathdigital.model.{OperationRestriction, PowerId, PowerResolution}

/** The powers available to one `advance`/`roll`/`resolve` command (Task 3).
  * `OathRules` supplies it at command entry; `applyRecorded` (replay) never
  * takes one -- replay applies recorded ops only (spec decision 5) and must
  * never re-gather or re-transform.
  *
  * `probing` is on for every command. The restriction look-ahead turns it off
  * for the traversal it runs, so a dry run inside a probe (an `OfferHost`
  * pass) never probes in turn.
  *
  * `restrictionSet` is the catalog's global operation restrictions and
  * `modifiers` the powers selected for this command. Together with the
  * offered powers' own restrictions they make `operationRestrictions`, which
  * every walker step runs with (global operation restrictions design).
  */
final case class WalkerPowers(powers: Vector[ContributingPower],
    probing: Boolean = true,
    restrictionSet: OperationRestrictions = OperationRestrictions.none,
    modifiers: Vector[PowerId] = Vector.empty):
  /** Whether any power can reject an action. The restriction look-ahead
    * ([[WalkerPowerGather.probe]]) has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = powers.exists(_.contributions.values
    .exists(_.exists(_.isInstanceOf[Restriction])))

  /** The operation restrictions every step of this command runs with. */
  lazy val operationRestrictions: Vector[OperationRestriction] =
    restrictionSet.active(powers.flatMap(_.operationRestrictions), modifiers)

object WalkerPowers:
  val empty: WalkerPowers = WalkerPowers(Vector.empty)

  /** Powers offered to one command out of a full catalog: an `Automatic`
    * power fires unconditionally; a `PlayerSelected` power fires only when
    * its id appears in `modifiers`. Shared by `OathRules.walkerPowers`
    * (command time) and `WalkerDecisionProjector` (park-time projection) so
    * both always fold a shared window identically (Task 5 projector seam).
    * The modifiers are kept, so the active-modifier restriction sees them
    * from the action's first walk.
    */
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    catalog.copy(powers = catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)), modifiers = modifiers)
