package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.RuleSourceRef
import oathdigital.gameplay.powerresolver.PowerResolution._
import oathdigital.gameplay.powerresolver.PowerResolverError._

/** Resolves relevance solely from the exact requested window. Source discovery
  * supplies factual ability IDs; handlers alone decide dynamic applicability.
  */
final class PowerResolver(registry: PowerRegistry) {
  def resolve(window: PowerWindow, sources: Vector[(RuleSourceRef, Vector[String])],
      facts: Any): Either[PowerResolverError, PowerResolutionResult] =
    validateSources(sources).flatMap(_ => resolveKnown(window, sources, facts))

  private def resolveKnown(window: PowerWindow,
      sources: Vector[(RuleSourceRef, Vector[String])], facts: Any)
      : Either[PowerResolverError, PowerResolutionResult] = {
    val declaredAtWindow = registry.at(window).map(_.definition.id).toSet
    val candidates = sources.flatMap { case (source, ids) =>
      ids.filter(declaredAtWindow).map(source -> _)
    }.sortBy { case (source, id) => (source.stableKey, id) }

    candidates.foldLeft[Either[PowerResolverError, PowerResolutionResult]](
      Right(PowerResolutionResult(Vector.empty, Vector.empty, Vector.empty))) {
      case (result, (source, id)) => result.flatMap { current =>
        registry.lookup(id).toRight(UnknownAbility(source, id)).map { registered =>
          registered.handler match {
            case Some(handler) =>
              val inspection = handler.inspect(PowerContext(window, source, facts))
              if (!inspection.applicable) current
              else registered.definition.resolution match {
                case PlayerSelected => current.copy(offered = current.offered :+
                  PowerInvocation(source, id, inspection))
                case Automatic => current.copy(automatic = current.automatic :+
                  PowerInvocation(source, id, inspection))
              }
            case None if registered.definition.resolution == Automatic =>
              current.copy(diagnostics = current.diagnostics :+ PowerDiagnostic(
                source, id, window, "reviewed-unimplemented-pre-alpha-fallback"))
            case None => current // selected, unimplemented powers are not offered
          }
        }
      }
    }
  }

  def validateSources(sources: Vector[(RuleSourceRef, Vector[String])])
      : Either[PowerResolverError, Unit] = sources.iterator.flatMap {
    case (source, ids) => ids.iterator.map(source -> _)
  }.find { case (_, id) => registry.lookup(id).isEmpty } match {
    case Some((source, id)) => Left(UnknownAbility(source, id))
    case None => Right(())
  }
}
