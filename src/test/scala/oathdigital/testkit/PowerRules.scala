package oathdigital.testkit

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{DeclaredNotes, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions

/** The rules a power suite drives, built on the production power catalogs,
  * and the parked-decision reads that match them: the tree a park sits in is
  * only rebuildable against the same catalogs as the rules that made it. A
  * suite that needs dice or a different catalog builds its own. */
object PowerRules:
  /** The note keys every production power declares, built once since it
    * walks both power catalogs: rules built with it fail a power that
    * writes a note it never declared. */
  val declared: Option[DeclaredNotes] = Some(DeclaredNotes.of(catalog))

  /** Rules with every production walker power: the modifiers and triggers
    * of Search, Travel, Trade, Muster and the like, and the printed
    * protections. No phase power. */
  val withWalkerPowers: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** Rules with every production walker power and every production phase
    * power. */
  val withWalkerAndPhasePowers: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** The parked decision of a walk under [[withWalkerPowers]]. */
  val parkedUnderWalkerPowers: ParkedDecisionAssertions =
    new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog))

  /** The parked decision of a walk under [[withWalkerAndPhasePowers]]. */
  val parkedUnderWalkerAndPhasePowers: ParkedDecisionAssertions =
    new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog))
