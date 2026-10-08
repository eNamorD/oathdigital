package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerWindow

/** A power that hooks a window, with its contributions at that window. */
final case class HookedPower(power: ContributingPower,
    contributions: Vector[Contribution])

/** A vector of powers indexed by the windows they hook (test speed design,
  * decision 4). Each window lists the powers whose `contributions` declare
  * it, in the vector's order, with their contributions there.
  *
  * Building it calls `contributions` once per power, and `gather` reads it
  * instead of calling `contributions` again. That relies on the contract on
  * `ContributingPower.contributions`.
  */
final class PowerWindowIndex private (
    byWindow: Map[PowerWindow, Vector[HookedPower]]):

  /** The powers that hook `window`, in input order. */
  def at(window: PowerWindow): Vector[HookedPower] =
    byWindow.getOrElse(window, Vector.empty)

  /** Whether any power declares a tree-level `Restriction` at any window. */
  lazy val hasRestrictions: Boolean = byWindow.valuesIterator.exists(
    _.exists(_.contributions.exists(_.isInstanceOf[Restriction])))

object PowerWindowIndex:
  def of(powers: Vector[ContributingPower]): PowerWindowIndex =
    val hooks = for
      power <- powers
      (window, contributions) <- power.contributions.toVector
    yield window -> HookedPower(power, contributions)
    new PowerWindowIndex(hooks.groupMap(_._1)(_._2))
