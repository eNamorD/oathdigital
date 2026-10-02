package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRules, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.powers.rest.{Insomnia, LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.powers.setup.{BanditMarket, BrokenForge, EmptyGrounds, GreatForge, GreatMarket, ProvingGrounds}
import oathdigital.gameplay.powers.targeting.TargetProtections
import oathdigital.gameplay.powers.title.ChaosCult
import oathdigital.gameplay.powers.travel.{TravelModifiers, TravelSitePowers}
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, ConspiracyWhenPlayed, Dazzle, FaithfulFriend, FamilyHeirloom, Garrison, ShiftingFog, TwinBrother, WhenPlayedPowers}
import oathdigital.gameplay.walker.WalkerPowers

/** The real catalog of `ContributingPower`s wired onto the generic walker
  * seam (Task 5's first entry: Catacombs). Catalog-parameterized like
  * `ReviewedPowerCatalog.resolver`/`registry`: a contribution that carries a
  * catalog-specific card id must be resolved against the same catalog the
  * caller is running. A site power whose site is absent from `catalog`
  * (e.g. a synthetic test catalog) is simply omitted, not a construction
  * failure. Every other card power names its card, so it is always present.
  *
  * A power carrying no catalog id is simply always present: Take Wealth's
  * once-per-turn limit states a rulebook clause about whichever site the pawn
  * stands on, so there is nothing to look up and nothing to omit. It is inert
  * until an action declares `PowerWindow.WakeTakeWealth` (batch-1 Task 7),
  * since discovery keeps only powers that hook the window being gathered.
  * League Treaty is inert until Finish Rest walks its `RestReturnFavor` window.
  * Silver Tongue's and Insomnia's adviser limits are inert until Search walks
  * `SearchPlayAdviser`.
  * Conspiracy's power carries no catalog id (a Vision has no catalog powers),
  * so like Take Wealth's limit it is always present and inert until a card
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
  * Vow of Peace's restriction is inert until Campaign walks
  * `CampaignActionEligibility`.
  * The battle plans are inert until a Campaign folds its plan windows: each
  * offers itself there, and the title's defense, which no card prints, is always
  * present.
  * `restrictionSet` gives every walker step the catalog's global operation
  * restrictions.
  */
object WalkerPowerCatalog:
  def default(catalog: ExecutableCatalog): WalkerPowers =
    WalkerPowers(Vector(CatacombsContribution.forCatalog(catalog)) ++
      Vector(VowOfPeaceContribution) ++
      TravelSitePowers.all ++
      TravelModifiers.forCatalog(catalog) ++
      Vector(LeagueTreatyContribution.forCatalog(catalog)) ++
      Vector(SilverTongue.forCatalog(catalog)) ++
      Vector(Insomnia) ++
      Vector(ASmallFavor) ++
      Vector(FaithfulFriend) ++
      Vector(Garrison) ++
      Vector(FamilyHeirloom) ++
      Vector(ShiftingFog) ++
      Vector(TwinBrother.forCatalog(catalog)) ++
      WhenPlayedPowers.forCatalog(catalog) ++
      Vector(ChaosCult) ++
      ActionModifiers.forCatalog(catalog) ++
      TargetProtections.forCatalog(catalog) ++
      Vector(KnightsErrant.forCatalog(catalog)) ++
      BattlePlans.forCatalog(catalog) ++
      PlanRules.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      BannerFacePowers.contributions ++
      Vector(Dazzle.forCatalog(catalog), GreatMarket.forCatalog(catalog),
        BanditMarket.forCatalog(catalog), GreatForge.forCatalog(catalog),
        BrokenForge.forCatalog(catalog), ProvingGrounds.forCatalog(catalog),
        EmptyGrounds.forCatalog(catalog), TakeWealthLimit,
        ConspiracyWhenPlayed),
      restrictionSet = OperationRestrictions.forCatalog(catalog))
