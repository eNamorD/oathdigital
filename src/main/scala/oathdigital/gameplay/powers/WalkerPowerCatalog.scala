package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRules, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.powers.targeting.TargetProtections
import oathdigital.gameplay.powers.travel.{TravelModifiers, TravelSitePowers}
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, ConspiracyWhenPlayed, Dazzle, FaithfulFriend, FamilyHeirloom, Garrison}
import oathdigital.gameplay.walker.WalkerPowers

/** The real catalog of `ContributingPower`s wired onto the generic walker
  * seam (Task 5's first entry: Catacombs). Catalog-parameterized like
  * `ReviewedPowerCatalog.resolver`/`registry`: a contribution that carries a
  * catalog-specific card id must be resolved against the same catalog the
  * caller is running. A power whose card is absent from `catalog` (e.g. a
  * synthetic test catalog) is simply omitted, not a construction failure.
  *
  * A power carrying no catalog id is simply always present: Take Wealth's
  * once-per-turn limit states a rulebook clause about whichever site the pawn
  * stands on, so there is nothing to look up and nothing to omit. It is inert
  * until an action declares `PowerWindow.WakeTakeWealth` (batch-1 Task 7),
  * since discovery keeps only powers that hook the window being gathered.
  * League Treaty is inert until Finish Rest walks its `RestReturnFavor` window.
  * Silver Tongue's restriction is inert until Search walks
  * `SearchPlayAdviser`.
  * Conspiracy's power carries no catalog id (a Vision has no catalog powers),
  * so like Take Wealth's limit it is always present and inert until a card
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
  * Vow of Peace's restriction is inert until Campaign walks
  * `CampaignActionEligibility`.
  * The battle plans are inert until a Campaign folds its plan windows: each
  * offers itself there, and the title's defense, which no card prints, is always
  * present.
  */
object WalkerPowerCatalog {
  def default(catalog: ExecutableCatalog): WalkerPowers =
    WalkerPowers(CatacombsContribution.forCatalog(catalog).toVector ++
      VowOfPeaceContribution.forCatalog(catalog).toVector ++
      TravelSitePowers.forCatalog(catalog) ++
      TravelModifiers.forCatalog(catalog) ++
      LeagueTreatyContribution.forCatalog(catalog) ++
      SilverTongue.forCatalog(catalog) ++
      ASmallFavor.forCatalog(catalog).toVector ++
      FaithfulFriend.forCatalog(catalog).toVector ++
      Garrison.forCatalog(catalog).toVector ++
      FamilyHeirloom.forCatalog(catalog).toVector ++
      ActionModifiers.forCatalog(catalog) ++
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      PlanRules.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      BannerFacePowers.contributions ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
}
