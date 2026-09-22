package oathdigital.frontend

private[frontend] final case class PeekedRelicPresentation(card: CardDetails)

/** What a site asks of a player, which is never both at once: a site is
  * either buildable or plunderable, and the rules give no site both.
  */
private[frontend] sealed trait SiteRequirement
private[frontend] object SiteRequirement {
  final case class Forge(favor: Int, secrets: Int) extends SiteRequirement
  final case class Recover(difficulty: Int) extends SiteRequirement
}

private[frontend] final case class SiteCardPresentation(
    looseFavor: Int,
    looseSecrets: Int,
    defense: Int,
    requirement: Option[SiteRequirement],
    unknownRelicCount: Int,
    peekedRelics: Vector[PeekedRelicPresentation]
)

private[frontend] object SiteCardPresentation {
  def from(site: GameSite): SiteCardPresentation = {
    SiteCardPresentation(
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
}
