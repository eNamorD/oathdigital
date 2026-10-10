package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.SpecialEnvoy
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A queued action spends no Supply (follow-up actions design, ruling 4),
  * on its start and on every resume, and a queued action may run in Rest. */
class FollowUpSupplyWaiverSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def owed(action: ActionRef): QueuedProcedure = QueuedProcedure(p1,
      action, Vector.empty, Some(knights), QueueKind.Offer)

  /** p1 at Ancient City, which two bandits rule, with `supply` Supply; p2 at
    * Broken Peaks and p3 at Buried Giant. `queue` is owed to p1. */
  private def table(supply: Int, queue: QueuedProcedure*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant")
    .bandits(ancientCity, 2).supply(p1, supply)
    .update(_.updateCurrent(_.copy(queue = queue.toVector)))

  private def spent(events: Vector[OathEvent]): Vector[SpendSupply] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
      .collect { case spend: SpendSupply => spend }

  test("a queued Campaign spends no Supply, even with none to spend"):
    val started = accepted(rules.startWalker(
      table(supply = 0, owed(ActionRef.Campaign)).state,
      ActionRef.Campaign, p1))
    assertEquals(spent(started.events), Vector.empty)
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)

  test("the same Campaign started normally spends its Supply"):
    val started = accepted(rules.startWalker(table(supply = 2).state,
      ActionRef.Campaign, p1))
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)
    assert(rules.startWalker(table(supply = 0).state, ActionRef.Campaign, p1)
      .isLeft)

  test("the waiver holds on a resume: a queued Travel pays after its " +
      "destination is chosen"):
    val started = accepted(rules.startWalker(
      table(supply = 0, owed(ActionRef.Travel)).state, ActionRef.Travel, p1))
    val done = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assertEquals(spent(done.events), Vector.empty)
    assertEquals(Look(Outcomes.ready(done.state)).pawn(p1), brokenPeaks)

  test("a queued Travel with Special Envoy enters Rest, and the queued " +
      "Campaign still starts there"):
    val board = table(supply = 0, owed(ActionRef.Travel),
      owed(ActionRef.Campaign)).adviser(p1, "Special Envoy").state
    val started = accepted(ModifierPlay.started(rules, board,
      ActionRef.Travel, p1, PowerWindow.TravelModifierSelection,
      Vector(SpecialEnvoy.id)))
    val travelled = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assertEquals(Look(Outcomes.ready(travelled.state)).phase, Phase.Rest)
    assertEquals(Outcomes.ready(travelled.state).game.current.queue,
      Vector(owed(ActionRef.Campaign)))
    // p2 stands at Broken Peaks, so a Raid is legal there.
    assert(rules.startWalker(travelled.state, ActionRef.Campaign, p1).isRight)

  test("each action pays at its own cost window"):
    import oathdigital.gameplay.walker.FollowUpSupplyWaiver.costWindow
    assertEquals(Vector(ActionRef.Campaign, ActionRef.Travel, ActionRef.Search,
      ActionRef.Muster, ActionRef.Trade, ActionRef.Forge, ActionRef.Challenge,
      ActionRef.Recover).map(costWindow), Vector(
      Some(PowerWindow.CampaignCost), Some(PowerWindow.TravelCost),
      Some(PowerWindow.SearchCost), Some(PowerWindow.MusterCost),
      Some(PowerWindow.TradeCost), Some(PowerWindow.ForgeCost),
      Some(PowerWindow.ChallengeCost), Some(PowerWindow.RecoverCost)))

  test("a queued Recover spends no Supply on its roll"):
    // p1 has no Supply, so a Recover started normally cannot pay its roll.
    val fairIsle = CatalogNames.site("Fair Isle")
    val base = Table.start.pawn(p1, at = fairIsle)
      .relicAt(CatalogNames.relic("Dowsing Sticks"), at = fairIsle)
      .supply(p1, 0)
    assert(rules.startWalker(base.state, ActionRef.Recover, p1).isLeft)
    val queued = base.update(_.updateCurrent(_.copy(
      queue = Vector(owed(ActionRef.Recover)))))
    val started = accepted(rules.startWalker(queued.state, ActionRef.Recover,
      p1))
    assertEquals(spent(started.events), Vector.empty)
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)
