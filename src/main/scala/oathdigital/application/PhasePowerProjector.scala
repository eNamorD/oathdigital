package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.model._
import oathdigital.protocol.projection.PhasePowerProjection

/** The viewer's usable phase powers, and their `usePower` legal controls,
  * from the one usability function the start gate also asks.
  */
private[application] final class PhasePowerProjector(catalog: ExecutableCatalog,
    walkerDecisions: WalkerDecisionProjector,
    powers: PhasePowers) {
  def this(catalog: ExecutableCatalog, walkerDecisions: WalkerDecisionProjector) =
    this(catalog, walkerDecisions, PhasePowerCatalog.default(catalog))

  def project(context: ScopedProjectionContext): Vector[PhasePowerProjection] =
    if (!context.viewerIsActive) Vector.empty
    else {
      val index = CardIndex.from(context.ready.game).toOption
      PhasePowerProcedure.usable(catalog, context.ready, context.active.player,
        powers).flatMap { usable =>
        for {
          (name, power) <- printed(usable.source, usable.power.id)
          option <- DecisionOption.forRef(usable.ref)
          source <- walkerDecisions.optionProjection(context.ready,
            context.viewer, index, option)
        } yield PhasePowerProjection(usable.power.id.value, source, name,
          power.rulesText)
      }
    }

  def controls(context: ScopedProjectionContext): Vector[String] =
    controls(project(context))

  def controls(projected: Vector[PhasePowerProjection]): Vector[String] =
    projected.map(p => s"usePower:${p.powerId}:${p.source.id}")

  private def printed(source: PowerSourceRef, power: PowerId)
      : Option[(String, oathdigital.catalog.CatalogPower)] = source match {
    case PowerSourceRef.Card(id: DenizenId) =>
      catalog.denizens.find(_.id.value == id.value)
        .flatMap(d => d.powers.find(_.id == power).map(d.name -> _))
    case PowerSourceRef.Card(id: RelicId) =>
      catalog.relics.find(_.id.value == id.value)
        .flatMap(r => r.powers.find(_.id == power).map(r.name -> _))
    case PowerSourceRef.Card(id: EdificeId) =>
      catalog.edifices.find(_.id.value == id.value).flatMap(e =>
        Vector(e.intact, e.ruined).flatMap(face =>
          face.powers.find(_.id == power).map(face.name -> _)).headOption)
    case _ => None // Banner faces get their labels with the slice that uses them.
  }
}
