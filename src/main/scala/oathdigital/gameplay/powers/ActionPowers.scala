package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerWindow

object ActionPowers {
  private def played = Vector(ReviewedHandler.automatic(PowerWindow.ActionCardPlayed))
  private def playedDone = Vector(ReviewedHandler.automatic(
    PowerWindow.ActionCardPlayed, implemented = true))
  object Dazzle extends ReviewedPower("denizen.dazzle", None,
    Vector(ReviewedHandler.automatic(PowerWindow.ActionCardPlayed,
      implemented = true)))
  object Revelation extends ReviewedPower("denizen.revelation", None, played)
  object ThreateningRoar extends ReviewedPower("denizen.threatening-roar", None, played)
  object AnimalHost extends ReviewedPower("denizen.animal-host", None, played)
  object ASmallFavor extends ReviewedPower("denizen.a-small-favor", None, playedDone)
  object KeyToTheCity extends ReviewedPower("denizen.key-to-the-city", None, played)
  object Charlatan extends ReviewedPower("denizen.charlatan", None, played)
  object Blackmail extends ReviewedPower("denizen.blackmail", None, played)
  object Dissent extends ReviewedPower("denizen.dissent", None, played)
  object FalseProphet extends ReviewedPower("denizen.false-prophet", None, played)
  object FamilyHeirloom extends ReviewedPower("denizen.family-heirloom", None, playedDone)
  object FabledFeast extends ReviewedPower("denizen.fabled-feast", None, played)
  object SaladDays extends ReviewedPower("denizen.salad-days", None, played)
  object TheGathering extends ReviewedPower("denizen.the-gathering", None, played)
  object FaithfulFriend extends ReviewedPower("denizen.faithful-friend", None, playedDone)
  object GreatHerd extends ReviewedPower("denizen.great-herd", None, played)
  object Pilgrimage extends ReviewedPower("denizen.pilgrimage", None, played)
  object TwinBrother extends ReviewedPower("denizen.twin-brother", None, played)
  object Garrison extends ReviewedPower("denizen.garrison", None, playedDone)
  object RoyalTax extends ReviewedPower("denizen.royal-tax", None, played)
  object Bewitch extends ReviewedPower("denizen.bewitch", None, played)
  object WizardsConclave extends ReviewedPower("denizen.wizard-s-conclave", None, played)
  object LongLostHeir extends ReviewedPower("denizen.long-lost-heir", None, played)
  object TrueOath extends ReviewedPower("denizen.true-oath", None, played)
  object AutumnWind extends ReviewedPower("denizen.autumn-wind", None, played)
  object ShiftingFog extends ReviewedPower("denizen.shifting-fog", None, played)
  object RoyalAmbitions extends ReviewedPower("denizen.royal-ambitions", None, played)
  object Riots extends ReviewedPower("denizen.riots", None, played)
  object BanditChief extends ReviewedPower("denizen.bandit-chief", None, played)
  object ReliquaryRaid extends ReviewedPower("denizen.reliquary-raid", None, played)
  object BanditPrince extends ReviewedPower("denizen.bandit-prince", None, played)
  object ARoundOfAle extends ReviewedPower("denizen.a-round-of-ale", None, played)
  object FavoredSon extends ReviewedPower("denizen.favored-son", None, played)
  object TownMeeting extends ReviewedPower("denizen.town-meeting", None, played)
  object AncientPact extends ReviewedPower("denizen.ancient-pact", None, played)
  object SearchParty extends ReviewedPower("denizen.search-party", None, played)
  object CallForHelp extends ReviewedPower("denizen.call-for-help", None, played)

  private def boundary = Vector(
    ReviewedHandler.automatic(PowerWindow.ActionAfterMajorAction),
    ReviewedHandler.automatic(PowerWindow.WakeBoundary),
    ReviewedHandler.automatic(PowerWindow.RestStart))
  object PeoplesFavorGrandCouncil extends ReviewedPower(
    "banner.peoples-favor.grand-council", None, boundary)
  object DarkestSecretFestival extends ReviewedPower(
    "banner.darkest-secret.festival", None, boundary)
  object AlteredFoundation extends ReviewedPower("foundation.altered", None, boundary)

  val powers: Vector[Power] = Vector(Dazzle, Revelation, ThreateningRoar,
    AnimalHost, ASmallFavor, KeyToTheCity, Charlatan, Blackmail, Dissent,
    FalseProphet, FamilyHeirloom, FabledFeast, SaladDays, TheGathering,
    FaithfulFriend, GreatHerd, Pilgrimage, TwinBrother, Garrison, RoyalTax,
    Bewitch, WizardsConclave, LongLostHeir, TrueOath, AutumnWind, ShiftingFog,
    RoyalAmbitions, Riots, BanditChief, ReliquaryRaid, BanditPrince, ARoundOfAle,
    FavoredSon, TownMeeting, AncientPact, SearchParty, CallForHelp,
    PeoplesFavorGrandCouncil, DarkestSecretFestival, AlteredFoundation)
}
