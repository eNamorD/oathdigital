package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powers.action.{Assassin, PiedPiper}
import oathdigital.gameplay.powers.banner.{BannerFacePowers, SecretTestament}
import oathdigital.gameplay.powers.burn.{VowOfDivision, VowOfSilence}
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRestrictions,
  PlanRewrites, PlanRules, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.dice.Jinx
import oathdigital.gameplay.powers.economy.{KnightsErrant, Marriage}
import oathdigital.gameplay.powers.cardplay.{CardPlayTriggers, FamilyWagon,
  LostTapestry, VowOfUnionAdvisers}
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.powers.rest.{Insomnia, LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.powers.setup.{BanditMarket, BrokenForge, EmptyGrounds, GreatForge, GreatMarket, ProvingGrounds}
import oathdigital.gameplay.powers.targeting.TargetProtections
import oathdigital.gameplay.powers.title.ChaosCult
import oathdigital.gameplay.powers.travel.{BrassArmyMoves, TravelModifiers, TravelSitePowers}
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
  * Silver Tongue's, Insomnia's and Assassin's adviser limits are inert until
  * Search walks `SearchPlayAdviser`. Lost Tapestry's and Pied Piper's
  * exemptions are inert until Search walks `SearchPlayAdviser` too. Family
  * Wagon's lifted limit and note are inert until Search walks
  * `SearchPlayAdviser` too, and its bury runs from the walker's arrival
  * pass.
  * Conspiracy's power carries no catalog id (a Vision has no catalog powers),
  * so like Take Wealth's limit it is always present and inert until a card
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
  * Vow of Peace's restriction is inert until Campaign walks
  * `CampaignActionEligibility`.
  * Vow of Silence and Vow of Division claim enemies' burns whenever the walker
  * runs a batch, and are inert at the Challenge until a Challenge walks its
  * windows.
  * The battle plans are inert until a Campaign folds its plan windows: each
  * offers itself there, and the title's defense, which no card prints, is always
  * present.
  * Brass Army's restriction is inert until a batch moves its holder's pawn.
  * Secret Testament is inert until a Challenge walks `ChallengeRibbon`.
  * Jinx is inert until a roll step asks for rerolls.
  * `restrictionSet` gives every walker step the catalog's global operation
  * restrictions.
  */
object WalkerPowerCatalog:
  def default(catalog: ExecutableCatalog): WalkerPowers =
    WalkerPowers(Vector(CatacombsContribution.forCatalog(catalog)) ++
      Vector(VowOfPeaceContribution) ++
      Vector(VowOfSilence, VowOfDivision) ++
      Vector(BrassArmyMoves) ++
      Vector(SecretTestament) ++
      TravelSitePowers.all ++
      TravelModifiers.forCatalog(catalog) ++
      Vector(LeagueTreatyContribution.forCatalog(catalog)) ++
      Vector(SilverTongue.forCatalog(catalog)) ++
      Vector(Insomnia) ++
      Vector(Assassin.forCatalog(catalog)) ++
      Vector(LostTapestry) ++
      Vector(PiedPiper) ++
      Vector(FamilyWagon.forCatalog(catalog)) ++
      Vector(VowOfUnionAdvisers.forCatalog(catalog)) ++
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
      Vector(Marriage) ++
      Vector(Jinx.forCatalog(catalog)) ++
      BattlePlans.forCatalog(catalog) ++
      PlanRules.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      PlanRestrictions.forCatalog(catalog) ++
      PlanRewrites.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      BannerFacePowers.contributions ++
      Vector(Dazzle.forCatalog(catalog), GreatMarket.forCatalog(catalog),
        BanditMarket.forCatalog(catalog), GreatForge.forCatalog(catalog),
        BrokenForge.forCatalog(catalog), ProvingGrounds.forCatalog(catalog),
        EmptyGrounds.forCatalog(catalog), TakeWealthLimit,
        ConspiracyWhenPlayed),
      restrictionSet = OperationRestrictions.forCatalog(catalog))
