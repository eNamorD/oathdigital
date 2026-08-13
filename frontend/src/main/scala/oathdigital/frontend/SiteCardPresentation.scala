package oathdigital.frontend

import oathdigital.presentation._

private[frontend] final case class SiteMetric(label: String, value: String)

private[frontend] final case class VisualRenderPlan(
    instruction: VisualInstruction,
    fallback: VisualInstruction.Placeholder
) {
  def afterFailure: VisualInstruction.Placeholder = fallback
}

private[frontend] object VisualRenderPlan {
  def from(
      entity: PresentedEntity,
      result: ImageLoadResult
  ): VisualRenderPlan = {
    val fallback = VisualResolver
      .resolve(entity, ImageLoadResult.NotRequested)
      .asInstanceOf[VisualInstruction.Placeholder]
    VisualRenderPlan(VisualResolver.resolve(entity, result), fallback)
  }
}

private[frontend] final case class SiteCardPresentation(
    siteVisual: VisualRenderPlan,
    metrics: Vector[SiteMetric],
    denizenVisuals: Vector[(String, VisualRenderPlan)],
    denizenEmpty: String,
    relicSummary: String
)

private[frontend] object SiteCardPresentation {
  def from(site: GameSite): SiteCardPresentation = {
    val siteEntity = SiteView(
      ViewId(s"site:${site.siteId}"),
      AccessibleLabel(site.label),
      image = None,
      fallback = FallbackVisual(initial(site.label), site.label)
    )
    val denizens = site.denizens.map { denizen =>
      val entity = CardView(
        ViewId(s"card:${denizen.denizenId}"),
        AccessibleLabel(denizen.label),
        image = None,
        fallback = FallbackVisual(initial(denizen.label), denizen.label),
        rulesText = ""
      )
      denizen.denizenId -> VisualRenderPlan.from(
        entity,
        ImageLoadResult.NotRequested
      )
    }

    SiteCardPresentation(
      siteVisual = VisualRenderPlan.from(
        siteEntity,
        ImageLoadResult.NotRequested
      ),
      metrics = Vector(
        SiteMetric("Favor", site.looseFavor.toString),
        SiteMetric("Secrets", site.looseSecrets.toString),
        SiteMetric("Defense", site.defense.toString)
      ) ++ site.forgeCost.map(cost => SiteMetric("Forge cost",
        s"${cost.favor} favor · ${cost.secrets} secrets"))
        .orElse(site.recoverDifficulty.map(value =>
          SiteMetric("Recover difficulty", value.toString))),
      denizenVisuals = denizens,
      denizenEmpty = "None",
      relicSummary =
        if (site.relics.facedownCount == 0) "None"
        else if (site.relics.facedownCount == 1) "1 facedown relic"
        else s"${site.relics.facedownCount} facedown relics"
    )
  }

  private def initial(label: String): String =
    label.trim.headOption.fold("?")(_.toUpper.toString)
}
