package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId

import oathdigital.gameplay.powerresolver.PowerResolution._
import oathdigital.gameplay.powerresolver.PowerResolverError._
import oathdigital.model.RuleSourceRef

/** Resolves relevance solely from the exact requested window. Source discovery
  * supplies factual ability IDs; handlers alone decide dynamic applicability.
  */
final class PowerResolver(registry: PowerRegistry) {
  def resolve(window: PowerWindow,
      sources: Vector[(RuleSourceRef, Vector[PowerId])], facts: PowerFacts)
      : Either[PowerResolverError, PowerResolutionResult] =
    validateSources(sources).flatMap(_ => resolveKnown(window, sources, facts))

  private def resolveKnown(window: PowerWindow,
      sources: Vector[(RuleSourceRef, Vector[PowerId])], facts: PowerFacts)
      : Either[PowerResolverError, PowerResolutionResult] = {
    val declaredAtWindow = registry.at(window).map(_._1.id).toSet
    val candidates = sources.flatMap { case (source, ids) =>
      ids.filter(declaredAtWindow).map(source -> _)
    }.sortBy { case (source, id) => (source.stableKey, id.value) }

    candidates.foldLeft[Either[PowerResolverError, PowerResolutionResult]](
      Right(PowerResolutionResult(Vector.empty, Vector.empty, Vector.empty))) {
      case (result, (source, id)) => result.flatMap { current =>
        registry.handler(id, window).toRight(UnknownAbility(source, id)).map { handler =>
          val inspection = handler.inspect(
            PowerContext(window, source, facts))
          if (!inspection.applicable) current
          else if (handler.implemented) handler.resolution match {
                case PlayerSelected => current.copy(offered = current.offered :+
                  PowerInvocation(source, id, inspection))
                case Automatic => current.copy(automatic = current.automatic :+
                  PowerInvocation(source, id, inspection))
              }
          else handler.resolution match {
            case Automatic =>
              current.copy(diagnostics = current.diagnostics :+ PowerDiagnostic(
                source, id, window, "reviewed-unimplemented-pre-alpha-fallback"))
            case PlayerSelected => current
          }
        }
      }
    }
  }

  def validateSources(sources: Vector[(RuleSourceRef, Vector[PowerId])])
      : Either[PowerResolverError, Unit] = sources.iterator.flatMap {
    case (source, ids) => ids.iterator.map(source -> _)
  }.find { case (_, id) => !registry.isAudited(id) } match {
    case Some((source, id)) => Left(UnknownAbility(source, id))
    case None => Right(())
  }
}
