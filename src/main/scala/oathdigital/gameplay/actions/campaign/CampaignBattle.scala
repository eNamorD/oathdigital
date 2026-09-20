package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The battle arithmetic and its operations. */
object CampaignBattle {
  /** The printed defense dice of the targets: a Conquest's sites, or a Raid's
    * pawn (2), each targeted relic's printed defense and each banner (3).
    */
  def printedDefense(catalog: ExecutableCatalog, setup: CampaignSetup): Int =
    setup.kind match {
      case CampaignKind.Conquest => setup.targetSites
        .flatMap(site => catalog.sites.find(_.id == site)).map(_.defense).sum
      case CampaignKind.Raid => setup.raidTargets.map {
        case _: CampaignRaidTarget.Pawn => 2
        case CampaignRaidTarget.Relic(_, relic) => catalog.relics
          .find(_.id.value == relic.value).map(_.defense).getOrElse(0)
        case _: CampaignRaidTarget.Banner => 3
      }.sum
    }

  /** Both pools, gathered once the force is known. A pool of zero is not
    * created: an empty pool is never rolled.
    */
  def gatherPools(catalog: ExecutableCatalog, setup: CampaignSetup)
      : Vector[CoreOperation] = {
    val printed = printedDefense(catalog, setup)
    Vector[Option[CoreOperation]](
      Option.when(setup.force > 0)(
        ModifyDicePool(CampaignIds.attackPool, setup.force)),
      Option.when(printed > 0)(
        ModifyDicePool(CampaignIds.defensePool, printed))).flatten
  }
}
