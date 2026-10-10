package oathdigital.gameplay

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A Campaign the Narrow Pass bears on (consent design, slice 2), on
  * `PassMap`. In p1's Act, p1 stands at Ancient City, in the Cradle, which
  * its bandits rule, with 3 warbands and 4 Supply. p2 stands at Fair Isle
  * and p3 at Dunes, so a Campaign by p1 is a Conquest of the bandits. The
  * Pass, in the Provinces, forbids Buried Giant and Deep Woods as its extra
  * targets.
  */
object CampaignConsentFixture:
  val passHeldByBandits: ReadyGame =
    Table.from(PassMap.withPawnAt(Table.start.ready, p1, PassMap.ancientCity))
      .pawn(p2, at = PassMap.fairIsle).pawn(p3, at = PassMap.dunes)
      .boardWarbands(p1, 3).supply(p1, 4).ready

  /** The same board, with p2 ruling the Pass with one warband. */
  val passRuledByP2: ReadyGame =
    Table.from(passHeldByBandits).warbandsAt(PassMap.pass, p2, 1).ready

  /** p2 rules Ancient City and Deep Woods, and p3 rules the Pass, so a
    * Campaign by p1 is a Conquest of p2 whose only extra target is Deep
    * Woods, which the Pass forbids. */
  val onlyWoodsExtra: ReadyGame =
    Table.from(passHeldByBandits).warbandsAt(PassMap.ancientCity, p2, 1)
      .warbandsAt(PassMap.deepWoods, p2, 1).warbandsAt(PassMap.pass, p3, 1)
      .ready

  val deepWoodsRef: DecisionOptionRef.Site =
    DecisionOptionRef.Site(PassMap.deepWoods)

  /** The catalog's walker powers and `extra`. */
  def rules(extra: Vector[ContributingPower] = Vector.empty): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers(extra))

  /** p1 starts a Campaign on `ready`; the walk parks on the targets. */
  def started(ready: ReadyGame = passRuledByP2,
      extra: Vector[ContributingPower] = Vector.empty)(
      using munit.Location): OathState =
    accepted(rules(extra).startWalker(Ready(ready), ActionRef.Campaign, p1,
      Vector.empty)).state

  /** Every option of the targets decision `state` is parked on, as offered. */
  def targetOptions(state: OathState,
      extra: Vector[ContributingPower] = Vector.empty)(
      using munit.Location): Vector[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowers.selected(powers(extra),
        Vector.empty)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case many: DecisionQuery.ChooseMany => many.options
        case other => munit.Assertions.fail(
          s"expected a choose-many, got $other")
      case other => munit.Assertions.fail(
        s"expected a parked decision, got $other")

  /** The id of the decision `state` is parked on; `None` when nothing is. */
  def parkedAt(state: OathState)(using munit.Location): Option[String] =
    ParkedNode.of(state, catalog, WalkerPowers.selected(powers(Vector.empty),
        Vector.empty)) match
      case Right(node) => node.filter(parked =>
        PhaseMenu.asking(parked.procedure).isEmpty).map(_.decisionId)
      case Left(reason) => munit.Assertions.fail(reason)

  private def powers(extra: Vector[ContributingPower]): WalkerPowers =
    WalkerPowers(WalkerPowerCatalog.default(catalog).powers ++ extra)
