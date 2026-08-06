package oathdigital.frontend

import oathdigital.presentation._

private[frontend] final case class SiteMetric(label: String, value: Int)

private[frontend] final case class SiteCardPresentation(
    siteVisual: VisualInstruction,
    metrics: Vector[SiteMetric],
    denizenVisuals: Vector[(String, VisualInstruction)],
    denizenEmpty: String,
    relicSummary: String
)

private[frontend] object SiteCardPresentation {
  def from(site: FirstGameSite): SiteCardPresentation = {
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
      denizen.denizenId -> resolve(entity, ImageLoadResult.NotRequested)
    }

    SiteCardPresentation(
      siteVisual = resolve(siteEntity, ImageLoadResult.NotRequested),
      metrics = Vector(
        SiteMetric("Favor", site.looseFavor),
        SiteMetric("Secrets", site.looseSecrets),
        SiteMetric("Denizen slots", site.denizenCapacity),
        SiteMetric("Relic slots", site.relicCapacity)
      ),
      denizenVisuals = denizens,
      denizenEmpty = "None",
      relicSummary =
        if (site.relics.facedownCount == 0) "None"
        else if (site.relics.facedownCount == 1) "1 facedown relic"
        else s"${site.relics.facedownCount} facedown relics"
    )
  }

  /** Stable boundary for a future platform image loader. */
  def resolve(
      entity: PresentedEntity,
      result: ImageLoadResult
  ): VisualInstruction = VisualResolver.resolve(entity, result)

  private def initial(label: String): String =
    label.trim.headOption.fold("?")(_.toUpper.toString)
}
