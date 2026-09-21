package oathdigital.gameplay.powerresolver

import oathdigital.model.{PowerResolution, PowerWindow}

/** Total inspection function used by concise handler declarations. */
final class PowerInspector private (
    private val run: PowerContext => PowerInspection
) extends (PowerContext => PowerInspection) with Serializable {
  def apply(context: PowerContext): PowerInspection = run(context)
}

object PowerInspector {
  private val inapplicable = PowerInspection(applicable = false)

  def apply(run: PowerContext => PowerInspection): PowerInspector =
    new PowerInspector(run)

  /** Converts a focused pattern match into a total, safely inapplicable
    * inspector for unrelated facts or sources.
    */
  def partial(run: PartialFunction[PowerContext, PowerInspection])
      : PowerInspector =
    new PowerInspector(context => run.applyOrElse(context,
      (_: PowerContext) => inapplicable))
}

object PowerHandlers {
  def automatic(window: PowerWindow, implemented: Boolean = true)(
      inspect: PowerInspector): PowerHandler =
    FunctionalPowerHandler(window, PowerResolution.Automatic, implemented,
      inspect)

  def selected(window: PowerWindow, implemented: Boolean = true)(
      inspect: PowerInspector): PowerHandler =
    FunctionalPowerHandler(window, PowerResolution.PlayerSelected, implemented,
      inspect)

  private final case class FunctionalPowerHandler(
      window: PowerWindow,
      resolution: PowerResolution,
      implemented: Boolean,
      inspectPower: PowerInspector
  ) extends PowerHandler {
    def inspect(context: PowerContext): PowerInspection = inspectPower(context)
  }
}
