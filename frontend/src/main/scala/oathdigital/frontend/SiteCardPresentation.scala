package oathdigital.frontend

import oathdigital.presentation._

private[frontend] final case class PeekedRelicPresentation(card: CardDetails)

/** What a site asks of a player, which is never both at once: a site is
  * either buildable or plunderable, and the rules give no site both.
  */
private[frontend] sealed trait SiteRequirement
private[frontend] object SiteRequirement {
  final case class Forge(favor: Int, secrets: Int) extends SiteRequirement
  final case class Recover(difficulty: Int) extends SiteRequirement
}

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
    looseFavor: Int,
    looseSecrets: Int,
    defense: Int,
    requirement: Option[SiteRequirement],
    unknownRelicCount: Int,
    peekedRelics: Vector[PeekedRelicPresentation]
)

private[frontend] object SiteCardPresentation {
  def from(site: GameSite): SiteCardPresentation = {
    val siteEntity = SiteView(
      ViewId(s"site:${site.siteId}"),
      AccessibleLabel(site.label),
      image = None,
      fallback = FallbackVisual(initial(site.label), site.label)
    )
    SiteCardPresentation(
      siteVisual = VisualRenderPlan.from(
        siteEntity,
        ImageLoadResult.NotRequested
      ),
      looseFavor = site.looseFavor,
      looseSecrets = site.looseSecrets,
      defense = site.defense,
      requirement = site.forgeCost
        .map(cost => SiteRequirement.Forge(cost.favor, cost.secrets))
        .orElse(site.recoverDifficulty.map(SiteRequirement.Recover)),
      unknownRelicCount = math.max(0,
        site.relics.facedownCount - site.relics.knownRelics.size),
      peekedRelics = site.relics.knownRelics.map(PeekedRelicPresentation(_))
    )
  }

  private def initial(label: String): String =
    label.trim.headOption.fold("?")(_.toUpper.toString)
}
