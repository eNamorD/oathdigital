package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.actions.{PreparedRecoverModifier,
  RecoverPowerHandler, RecoverPowerPreparation, RecoverRules}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Reviewed Recover classifications and their procedure-specific handlers.
  * Power objects contain identity and per-window routing only; Catacombs owns
  * its typed Recover contribution and composes generic semantic operations.
  */
object RecoverPowers {
  private val modifier = Some(MajorActionType.Recover)
  private val catacombsId = PowerId("denizen.catacombs")

  object RelicWorship extends ReviewedPower("denizen.relic-worship", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E13Ruined extends ReviewedPower("edifice.e13.ruined", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E17Intact extends ReviewedPower("edifice.e17.intact", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
  object E17Ruined extends ReviewedPower("edifice.e17.ruined", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))

  private val catacombsCost = Vector(ResourceCost(ResourceKind.Secret, 1,
    CostDisposition.PlaceOnSource))

  private final class CatacombsInspectorHandler(
      val window: PowerWindow,
      val resolution: PowerResolution
  ) extends PowerHandler {
    val implemented = true

    def inspect(context: PowerContext): PowerInspection = context.facts match {
      case facts: ReviewedPowerFacts =>
        val reviewed = ReviewedPowerInspector.inspect(context)
        val applicable = reviewed.applicable && (context.source match {
          case source @ RuleSourceRef.SiteCard(siteId, _: DenizenId) =>
            val site = facts.ready.game.current.map.sites.get(siteId)
            val definition = facts.catalog.sites.find(_.id == siteId)
            facts.sources.get(source).exists(_.powerIds.contains(catacombsId)) &&
              site.exists(value => definition.exists(d =>
                value.relics.size < d.relicSlots)) &&
              PayCosts.affordable(facts.ready, facts.actor, source,
                catacombsCost) && DrawTopRelic.plan(facts.ready).isRight
          case _ => false
        })
        reviewed.copy(applicable = applicable)
      case _ => PowerInspection(applicable = false)
    }
  }

  private object CatacombsExecution extends RecoverPowerHandler {
    val window = PowerWindow.RecoverBeforeFirstRoll
    val resolution = PowerResolution.PlayerSelected
    val implemented = true

    def inspect(context: PowerContext): PowerInspection =
      new CatacombsInspectorHandler(window, resolution).inspect(context)

    def prepare(input: RecoverPowerPreparation)
        : Either[OathViolation, PreparedRecoverModifier] = for {
      siteSource <- input.source match {
        case source: RuleSourceRef.SiteCard => Right(source)
        case _ => Left(OathViolation.InvalidModifierInvocation(
          "Catacombs requires a denizen site-card source"))
      }
      _ <- inspect(PowerContext(window, input.source,
        ReviewedPowerCatalog.facts(input.catalog, input.ready, input.actor))) match {
        case PowerInspection(true, _, _) => Right(())
        case _ => Left(OathViolation.InvalidModifierInvocation(
          "Catacombs is not currently applicable"))
      }
      paid <- PayCosts.plan(input.ready, input.actor, siteSource,
        catacombsCost)
      _ <- DrawTopRelic.validate(input.ready, input.drawnRelic)
      placed <- PlaceRelicAtSite.plan(input.catalog, input.ready, input.actor,
        input.drawnRelic, siteSource.siteId, Orientation.FaceDown)
    } yield PreparedRecoverModifier(Vector(OathEvent.CatacombsResolved(
      input.actor, input.decision, catacombsId, siteSource, paid, placed)))

    def evolve(catalog: oathdigital.catalog.ExecutableCatalog, state: OathState,
        event: RecoverPowerEvent): Either[OathViolation, OathState] = event match {
      case resolved: OathEvent.CatacombsResolved => state match {
        case OathState.Ready(ready) => for {
          _ <- Either.cond(resolved.powerId == catacombsId, (),
            OathViolation.InvalidEventOrder("Catacombs power ID does not match"))
          _ <- Either.cond(resolved.payment.playerId == resolved.playerId &&
            resolved.payment.source == resolved.source, (),
            OathViolation.InvalidEventOrder(
              "Catacombs payment attribution does not match"))
          _ <- Either.cond(resolved.placement.playerId == resolved.playerId &&
            resolved.placement.siteId == resolved.source.siteId &&
            resolved.placement.orientation == Orientation.FaceDown, (),
            OathViolation.InvalidEventOrder(
              "Catacombs relic placement does not match"))
          _ <- RecoverRules.validateAction(catalog, state, resolved.playerId,
            resolved.source.siteId)
          _ <- inspect(PowerContext(window, resolved.source,
            ReviewedPowerCatalog.facts(catalog, ready,
              resolved.playerId))) match {
            case PowerInspection(true, _, _) => Right(())
            case _ => Left(OathViolation.InvalidEventOrder(
              "Catacombs is not applicable at Recover before-first-roll"))
          }
          expectedPayment <- PayCosts.plan(ready, resolved.playerId,
            resolved.source, catacombsCost)
          _ <- Either.cond(resolved.payment == expectedPayment, (),
            OathViolation.InvalidEventOrder("Catacombs cost does not match"))
          expectedPlacement <- PlaceRelicAtSite.plan(catalog, ready,
            resolved.playerId, resolved.placement.relicId,
            resolved.source.siteId, Orientation.FaceDown)
          _ <- Either.cond(resolved.placement == expectedPlacement, (),
            OathViolation.InvalidEventOrder(
              "Catacombs relic outcome does not match"))
          paidState <- PayCosts.evolve(state, expectedPayment)
          placedState <- PlaceRelicAtSite.evolve(catalog, paidState,
            expectedPlacement)
        } yield placedState
        case _ => Left(OathViolation.GameNotStarted)
      }
      case _ => Left(OathViolation.InvalidEventOrder(
        "Catacombs handler received another Recover power event"))
    }
  }

  object Catacombs extends Power {
    val id = catacombsId
    val modifier = Some(MajorActionType.Recover)
    val handlers: Vector[PowerHandler] = Vector(
      new CatacombsInspectorHandler(PowerWindow.RecoverActionEligibility,
        PowerResolution.Automatic),
      new CatacombsInspectorHandler(PowerWindow.RecoverModifierSelection,
        PowerResolution.PlayerSelected),
      CatacombsExecution)
  }

  val powers: Vector[Power] = Vector(RelicWorship, E13Ruined, E17Intact,
    E17Ruined, Catacombs)
}
