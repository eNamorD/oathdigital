package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.actions.{RecoverPowerHandler,
  RecoverPowerPreparation, RecoverRules}
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

  private val catacombsCost = Cost(secret = 1)

  private def catacombsPlacedAt(source: RuleSourceRef.SiteCard): Location =
    Location.OnCard(source.id)

  private val catacombsInspector = PowerInspector.partial {
    case context @ PowerContext(_, _, facts: ReviewedPowerFacts) =>
      val reviewed = ReviewedPowerInspector.inspect(context)
      val applicable = reviewed.applicable && (context.source match {
        case source @ RuleSourceRef.SiteCard(siteId, _: DenizenId) =>
          val site = facts.ready.game.current.map.sites.get(siteId)
          val definition = facts.catalog.sites.find(_.id == siteId)
          facts.sources.get(source).exists(_.powerIds.contains(catacombsId)) &&
            site.exists(value => definition.exists(d =>
              value.relics.size < d.relicSlots)) &&
            Costs.affordable(facts.ready, facts.actor,
              catacombsPlacedAt(source), catacombsCost) &&
            DrawTopRelic.plan(facts.ready).isRight
        case _ => false
      })
      reviewed.copy(applicable = applicable)
  }

  private def prepareCatacombs(input: RecoverPowerPreparation)
      : Either[OathViolation, OathEvent.CatacombsResolved] = for {
    siteSource <- input.source match {
      case source: RuleSourceRef.SiteCard => Right(source)
      case _ => Left(OathViolation.InvalidModifierInvocation(
        "Catacombs requires a denizen site-card source"))
    }
    _ <- catacombsInspector(PowerContext(PowerWindow.RecoverBeforeFirstRoll,
      input.source,
      ReviewedPowerCatalog.facts(input.catalog, input.ready, input.actor))) match {
      case PowerInspection(true, _, _) => Right(())
      case _ => Left(OathViolation.InvalidModifierInvocation(
        "Catacombs is not currently applicable"))
    }
    payCost <- Costs.plan(input.ready, input.actor,
      catacombsPlacedAt(siteSource), catacombsCost)
    placed <- PlaceRelicAtSite.plan(input.catalog, input.ready, input.actor,
      input.drawnRelic, siteSource.siteId, Orientation.FaceDown)
  } yield OathEvent.CatacombsResolved(input.actor, input.decision,
    catacombsId, siteSource, payCost.cost, placed)

  private def canonicalCatacombs(
      catalog: oathdigital.catalog.ExecutableCatalog, ready: ReadyGame,
      resolved: OathEvent.CatacombsResolved)
      : Either[OathViolation, OathEvent.CatacombsResolved] = for {
    _ <- RecoverRules.validateAction(catalog, OathState.Ready(ready),
      resolved.playerId, resolved.source.siteId)
    _ <- catacombsInspector(PowerContext(PowerWindow.RecoverBeforeFirstRoll,
      resolved.source,
      ReviewedPowerCatalog.facts(catalog, ready, resolved.playerId))) match {
      case PowerInspection(true, _, _) => Right(())
      case _ => Left(OathViolation.InvalidEventOrder(
        "Catacombs is not applicable at Recover before-first-roll"))
    }
    expectedCost <- Costs.plan(ready, resolved.playerId,
      catacombsPlacedAt(resolved.source), catacombsCost)
    expectedPlacement <- PlaceRelicAtSite.plan(catalog, ready,
      resolved.playerId, resolved.placement.relicId,
      resolved.source.siteId, Orientation.FaceDown)
    _ <- Either.cond(expectedCost.cost == resolved.cost, (),
      OathViolation.InvalidEventOrder("recorded Catacombs cost does not match"))
  } yield resolved.copy(placement = expectedPlacement)

  private val catacombsExecution =
    RecoverPowerHandler.operationBackedSelected[OathEvent.CatacombsResolved](
      catacombsId, PowerWindow.RecoverBeforeFirstRoll, catacombsInspector, {
        case event: OathEvent.CatacombsResolved => event
      })(prepareCatacombs, canonicalCatacombs, event =>
        Right(Vector(PayCost(event.playerId, catacombsPlacedAt(event.source),
          event.cost)) :+ PowerOperationPlanner.placement(event.placement)))

  object Catacombs extends Power {
    val id = catacombsId
    val modifier = Some(MajorActionType.Recover)
    val handlers: Vector[PowerHandler] = Vector(
      PowerHandlers.automatic(PowerWindow.RecoverActionEligibility)(
        catacombsInspector),
      PowerHandlers.selected(PowerWindow.RecoverModifierSelection)(
        catacombsInspector),
      catacombsExecution)
  }

  val powers: Vector[Power] = Vector(RelicWorship, E13Ruined, E17Intact,
    E17Ruined, Catacombs)
}
