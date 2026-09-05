package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId

/** One typed cost fact declared by a power for a window. Powers hold these as
  * data; the window fold (owned by the powers module) reads them. Add sums an
  * amount onto the running cost; Replace overwrites it. Semantic kind is
  * carried by the concrete contribution (Coast replaces with 1, Island/Mountain
  * add their printed amounts), so a fold can distinguish terrain roles without
  * a catalog-ID switch.
  */
sealed trait CostContribution extends Product with Serializable {
  def window: PowerWindow
}
object CostContribution {
  final case class Add(window: PowerWindow, amount: Int) extends CostContribution
  final case class Replace(window: PowerWindow, amount: Int)
      extends CostContribution
}

/** Minimal context a window fold hands to suppression predicates. Concrete
  * window families extend this with their route facts so predicates can match
  * on them without the registry knowing any window specifics.
  */
trait WindowContext {
  /** Power ids gathered as applicable at the window before suppression. */
  def activePowers: Vector[PowerId]
}

/** Generic 'ignore' registry keyed by window. One power (the dominant) may
  * declare that while it applies, other powers at the same window are ignored.
  * Static and empty by default; powers register at load; the registry is inert
  * until entries exist, so any window fold can consult it unconditionally.
  */
object SuppressionRegistry {
  final case class Suppression(
      window: PowerWindow,
      dominant: PowerId,
      suppressed: Vector[PowerId],
      when: WindowContext => Boolean
  )

  private val entries =
    scala.collection.mutable.Map.empty[(PowerWindow, PowerId), Suppression]

  /** Registers one suppression rule; a second registration for the same
    * (window, dominant) pair is a programming error.
    */
  def register(
      window: PowerWindow,
      dominant: PowerId,
      suppressed: Vector[PowerId]
  )(when: WindowContext => Boolean): Unit = {
    val key = (window, dominant)
    require(!entries.contains(key), s"suppression for $key already registered")
    entries.update(key, Suppression(window, dominant, suppressed, when))
  }

  /** The active ids dropped because an applicable dominant suppresses them.
    * A suppression applies when its dominant is active and its predicate
    * accepts the context; only suppressed ids that are actually active matter.
    */
  def suppressed(
      window: PowerWindow,
      active: Vector[PowerId],
      context: WindowContext
  ): Set[PowerId] = {
    val activeIds = active.toSet
    entries.valuesIterator.collect {
      case entry if entry.window == window &&
          activeIds.contains(entry.dominant) &&
          entry.when(context) =>
        entry.suppressed.filter(activeIds.contains).toSet
    }.foldLeft(Set.empty[PowerId])(_ ++ _)
  }
}
