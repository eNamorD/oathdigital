package oathdigital.protocol.projection


final case class SetupPlayerProjection(
    playerId: String,
    displayName: String,
    role: String,
    colorToken: String
)
final case class CardDetailsProjection(
    cardId: String,
    cardKind: String,
    name: String,
    suit: Option[String] = None,
    restrictions: Option[String] = None,
    rulesText: Option[String] = None,
    orientation: Option[String] = None,
    side: Option[String] = None,
    favor: Int = 0,
    secrets: Int = 0,
    relicValue: Option[Int] = None,
    defense: Option[Int] = None,
    hidden: Boolean = false
)
final case class SiteCardProjection(
    cardId: String,
    label: String,
    details: Option[CardDetailsProjection] = None) {
  def denizenId: String = cardId
}
final case class SiteRelicsProjection(facedownCount: Int,
    knownRelics: Vector[CardDetailsProjection] = Vector.empty)
final case class ForgeCostProjection(favor: Int, secrets: Int)
final case class SiteForcesProjection(
    forceKind: String,
    count: Int,
    rulerKind: String,
    rulerPlayerId: Option[String],
    label: String,
    colorToken: String
)
final case class SetupSiteProjection(
    siteId: String,
    label: String,
    looseFavor: Int,
    looseSecrets: Int,
    denizenCapacity: Int,
    relicCapacity: Int,
    denizens: Vector[SiteCardProjection],
    relics: SiteRelicsProjection,
    defense: Int = 0,
    recoverDifficulty: Option[Int] = None,
    forgeCost: Option[ForgeCostProjection] = None,
    powers: Vector[SitePowerProjection] = Vector.empty,
    forces: Option[SiteForcesProjection] = None
)
final case class SetupRegionProjection(
    regionId: String,
    sites: Vector[SetupSiteProjection],
    discardCount: Int = 0,
    discardTopCardKind: Option[String] = None
)
final case class SitePowerProjection(kind: String, label: String, description: Option[String])
final case class PawnLocationProjection(playerId: String, siteId: String)
final case class ActivePlayerResourcesProjection(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    committedSecrets: Int,
    totalSecrets: Int,
    supply: Int
)
final case class CurrentSiteResourcesProjection(
    siteId: String,
    favor: Int,
    secrets: Int
)
final case class FavorBankProjection(suit: String, count: Int)
final case class GameTracksProjection(round: Int, visionsDrawn: Int,
    usurperLimited: Boolean, limiterRound: Int, firstPlayerId: String)
final case class LegalTravelDestinationProjection(siteId: String, supplyCost: Int)
final case class LegalSearchSourceProjection(kind: String, region: Option[String], supplyCost: Int)
final case class LegalMusterProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    supplyCost: Int, warbandsGained: Int)
final case class LegalTradeProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    resource: String, supplyCost: Int, gained: Int)
