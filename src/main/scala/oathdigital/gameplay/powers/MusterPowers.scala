package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object MusterPowers {
  private def economy = Vector(
    ReviewedHandler.selected(PowerWindow.MusterModifierSelection),
    ReviewedHandler.selected(PowerWindow.TradeModifierSelection))
  object InitiationRite extends ReviewedPower("denizen.initiation-rite", None, economy)
  object MapLibrary extends ReviewedPower("denizen.map-library", None, economy)
  object AnimalPlaymates extends ReviewedPower("denizen.animal-playmates", None, economy)
  object TheOldOak extends ReviewedPower("denizen.the-old-oak", None, economy)
  object Birdsong extends ReviewedPower("denizen.birdsong", None, economy)
  object SmallFriends extends ReviewedPower("denizen.small-friends", None, economy)
  object VowOfBeastkin extends ReviewedPower("denizen.vow-of-beastkin", None, economy)
  object Downtrodden extends ReviewedPower("denizen.downtrodden", None, economy)
  object RowdyPub extends ReviewedPower("denizen.rowdy-pub", None, economy)
  object Pressgangs extends ReviewedPower("denizen.pressgangs", None, economy)
  object Curfew extends ReviewedPower("denizen.curfew", None, economy)
  object KnightsErrant extends ReviewedPower("denizen.knights-errant", None, economy)
  object GolemLegions extends ReviewedPower("denizen.golem-legions", None, economy)
  object Defame extends ReviewedPower("denizen.defame", None, economy)
  object FriendlyFamiliar extends ReviewedPower("denizen.friendly-familiar", None, economy)
  object OldSongs extends ReviewedPower("denizen.old-songs", None, economy)
  object VillageIdiot extends ReviewedPower("denizen.village-idiot", None, economy)
  object SkilledMerchants extends ReviewedPower("denizen.skilled-merchants", None, economy)
  object MovingMarket extends ReviewedPower("denizen.moving-market", None, economy)
  object MountedLibrary extends ReviewedPower("denizen.mounted-library", None, economy)
  object CupOfPlenty extends ReviewedPower("relic.cup-of-plenty", None, economy)
  object SpitefulMirror extends ReviewedPower("relic.spiteful-mirror", None, economy)
  object E26Intact extends ReviewedPower("edifice.e26.intact", None, economy)
  object Beloved extends ReviewedPower("legacy.beloved", None, economy)
  val powers: Vector[Power] = Vector(InitiationRite, MapLibrary, AnimalPlaymates,
    TheOldOak, Birdsong, SmallFriends, VowOfBeastkin, Downtrodden, RowdyPub,
    Pressgangs, Curfew, KnightsErrant, GolemLegions, Defame, FriendlyFamiliar,
    OldSongs, VillageIdiot, SkilledMerchants, MovingMarket, MountedLibrary,
    CupOfPlenty, SpitefulMirror, E26Intact, Beloved)
}
