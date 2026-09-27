package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
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
  * due before its own last decision. Hunger, the only one, asks before it
  * changes anything, and cannot bury itself.
  */
object ForcedWakeProcedure:
  def due(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[(PhasePower, DecisionOptionRef)] =
    powers.powers.filter(power => power.forced &&
      power.timing == PowerTiming.Wake).flatMap(power =>
      PhasePowerProcedure.sources(catalog, ready, player, power).collect {
        case (_, ref) if power.usable(ready, player, ref) => power -> ref
      })

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
      built.flatMap(done => power.build(ready, player, source).map(done :+ _))
    }
  yield Sequence(trees)
