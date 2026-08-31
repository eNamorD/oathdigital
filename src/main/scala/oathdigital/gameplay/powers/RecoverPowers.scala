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
            val player = facts.ready.game.current.players.find(
              _.player == facts.actor)
            val site = facts.ready.game.current.map.sites.get(siteId)
            val definition = facts.catalog.sites.find(_.id == siteId)
            player.exists(p =>
              RecoverRules.validatePotential(facts.catalog, facts.ready, p,
                siteId).isRight) &&
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
    } yield PreparedRecoverModifier(Vector(paid, placed))
  }

  object Catacombs extends Power {
    val id = PowerId("denizen.catacombs")
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
