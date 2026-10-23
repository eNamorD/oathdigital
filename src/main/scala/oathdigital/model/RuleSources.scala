package oathdigital.model

/** Stable identity for a runtime rule source. PowerUseRef remains the narrower,
  * wire-compatible identity for use-limited powers.
  */
sealed trait RuleSourceRef extends Product with Serializable:
  def stableKey: String
object RuleSourceRef:
  final case class Site(id: SiteId) extends RuleSourceRef:
    def stableKey: String = s"site:${id.value}"
  final case class SiteCard(siteId: SiteId, id: CardId) extends RuleSourceRef:
    def stableKey: String = s"site-card:${siteId.value}:${id.kind}:${id.value}"
  final case class Adviser(playerId: PlayerId, id: CardId) extends RuleSourceRef:
    def stableKey: String = s"adviser:${playerId.value}:${id.kind}:${id.value}"
  final case class Relic(playerId: PlayerId, id: RelicId) extends RuleSourceRef:
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
  final case class SiteRelic(siteId: SiteId, id: RelicId) extends RuleSourceRef:
    def stableKey: String = s"site-relic:${siteId.value}:${id.value}"
  final case class Edifice(siteId: SiteId, id: EdificeId) extends RuleSourceRef:
    def stableKey: String = s"edifice:${siteId.value}:${id.value}"
  final case class Banner(id: String) extends RuleSourceRef:
    def stableKey: String = s"banner:$id"
  final case class Foundation(number: FoundationNumber) extends RuleSourceRef:
    def stableKey: String = s"foundation:${number.value}"
  final case class Legacy(lineageId: LineageId, id: LegacyId)
      extends RuleSourceRef:
    def stableKey: String = s"legacy:${lineageId.value}:${id.value}"
  final case class GameRule(id: String) extends RuleSourceRef:
    def stableKey: String = s"game:$id"
