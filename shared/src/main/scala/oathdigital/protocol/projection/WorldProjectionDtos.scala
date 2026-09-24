package oathdigital.protocol.projection

import oathdigital.model.PlayerColor

final case class SetupPlayerProjection(
    playerId: String,
    displayName: String,
    role: String,
    color: PlayerColor
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
    hidden: Boolean = false,
    implemented: Boolean = true
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
/** Whose warbands hold a site. One case per kind of force, so a force, its
  * ruler and its colour cannot disagree.
  */
sealed trait SiteForcesProjection extends Product with Serializable {
  def count: Int
  def label: String
}
object SiteForcesProjection {
  final case class Exile(count: Int, rulerPlayerId: String, color: PlayerColor,
      label: String) extends SiteForcesProjection
  final case class Imperial(count: Int, label: String)
      extends SiteForcesProjection
  final case class Bandit(count: Int, label: String)
      extends SiteForcesProjection
}
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
