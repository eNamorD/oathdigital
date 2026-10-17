package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers,
  PowerUse}
import oathdigital.model._

/** Forced Wake steps (catalog batch 2, N8).
  *
  * {{{
  * Sequence(power1.build(...), power2.build(...), ...)
  * }}}
  *
  * Every forced WAKE power the waking player can access, in catalog order,
  * each asking its own decision. It is a triggered procedure, started by
  * the turn boundary after the Wake checks, or by the end of Setup for the
  * first Wake. The player can do nothing else until it ends: a parked walker
  * refuses End Wake and every optional power.
  *
  * `build` is also `rebuild`. The list is read from the state the walk
  * resumes in, so a forced power must not change which forced powers are
  * due, by their `usable` or by a power-use refusal (catalog batch 8, slice
  * 10), before its own last decision. Hunger, the only one, asks before it
  * changes anything, and cannot bury itself.
  *
  * Each forced power from a card runs inside an `AutomaticUse`, so a
  * refusal the refusing card's ruler may waive asks them first (automatic
  * power consent design). A power refused by a restriction nobody may
  * waive is not due.
  */
object ForcedWakeProcedure:
  def due(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[(PhasePower, DecisionOptionRef)] =
    powers.powers.filter(power => power.forced &&
      power.timing == PowerTiming.Wake).flatMap(power =>
      PhasePowerProcedure.sources(catalog, ready, player, power).collect {
        case (found, ref) if power.usable(ready, player, ref) &&
            !deactivatedUnasked(catalog, ready, player, power, found,
              powers) => power -> ref
      })

  /** Whether `power` from `source` is refused by a restriction nobody may
    * waive. Such a power is deactivated unasked and is not due. One that
    * only waivable restrictions refuse is due, and its node asks the
    * refusing cards' rulers (automatic power consent design). Raw
    * refusals are read, so a grant or a refusal never changes what is
    * due between the walk and its resume. */
  private def deactivatedUnasked(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, power: PhasePower,
      source: PowerSourceRef, powers: PhasePowers): Boolean =
    source match
      case PowerSourceRef.Card(card) =>
        val use = PowerUse.automatic(player, power.id, card)
        val raised = PowerUseRules.raised(ready, use, powers.useRestrictions)
        raised.nonEmpty && !PowerUseRules.waivable(use, raised)
      case _ => PhasePowerProcedure.refusals(catalog, ready, player, power,
        source, powers.useRestrictions).nonEmpty

  def build(powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "the forced Wake step selects nothing"))
    found = due(catalog, ready, player, powers)
    _ <- Either.cond(found.nonEmpty, (), OathViolation.InvalidEventOrder(
      "no forced Wake power is due"))
    trees <- found.foldLeft[Either[OathViolation, Vector[Operation]]](
      Right(Vector.empty)) { case (built, (power, source)) =>
      built.flatMap(done => power.build(ready, player, source).map(tree =>
        done :+ cardOf(source).fold(tree)(card => AutomaticUse(player,
          power.id, card, (_, _) => Vector(tree)))))
    }
  yield Sequence(trees)

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Relic(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
