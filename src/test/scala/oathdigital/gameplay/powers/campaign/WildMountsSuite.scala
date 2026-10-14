package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Wild Mounts: a free plan that lets its user discard one Beast card they
  * rule instead of their Nomad battle plans at the end of the Campaign. */
class WildMountsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Wild Mounts")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val lancers = CatalogNames.denizen("Lancers")
  private val stormCaller = CatalogNames.denizen("Storm Caller")
  private val mountedPatrol = CatalogNames.denizen("Mounted Patrol")
  private val warningSignals = CatalogNames.denizen("Warning Signals")
  private val forestWarden = planlessCard("Forest Warden")
  private val python = planlessCard("Giant Python")
  private val newGrowth = planlessCard("New Growth")

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    WildMounts.id, Vector(WildMounts.swapped, WildMounts.swappedOffTurn),
    run.events)

  private def swapped(beast: DenizenId): NoteText.Said = NoteText.Said(
    "swapped", s"Discarded ${beast.value} instead of the battle plans.",
    covers = false)

  /** The actor holds Wild Mounts, Forest Warden (a Beast card) and `plans`
    * faceup. Two bandits rule the origin. */
  private def mountsBeastAndPlansHeldByActor(plans: DenizenId*): Board =
    (Vector(card, forestWarden) ++ plans).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))

  /** The actor chooses Wild Mounts, then `plans`, and every window is
    * finished: a won Conquest, nothing placed. */
  private def attackerChoosesMountsThen(b: Board, plans: DenizenId*): Run =
    attackerChoosesMountsUnder(rules(winningDice), b, plans*)

  /** As `attackerChoosesMountsThen`, under `game`'s rules. */
  private def attackerChoosesMountsUnder(game: OathRules, b: Board,
      plans: DenizenId*): Run =
    plans.foldLeft(commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref))((run, plan) =>
      run.pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(plan))).finishPlanWindows

  test("with no Nomad plan to discard, nothing is asked"):
    val b = mountsBeastAndPlansHeldByActor()
    val done = attackerChoosesMountsThen(b)
    assert(done.winner.nonEmpty)
    assert(!awaits(done, b.actor, WildMounts.decisionId))
    assert(Look(done.state).advisers(b.actor).contains(forestWarden))
    assertEquals(lines(done), Vector.empty)

  test("a Nomad plan's discard is replaced by a Beast card the user rules"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val asked = attackerChoosesMountsThen(b, horseArchers)
    assert(awaits(asked, b.actor, WildMounts.decisionId))
    assertEquals(asked.options(b.actor).map(_.ref),
      Vector(DecisionOptionRef.Denizen(forestWarden), WildMounts.skip))
    val done = asked.pick(b.actor, WildMounts.decisionId,
      DecisionOptionRef.Denizen(forestWarden))
    assert(Look(done.state).inRegionalDiscard(forestWarden))
    assert(Look(done.state).advisers(b.actor).contains(horseArchers))
    assertEquals(lines(done), Vector(swapped(forestWarden)))

  test("Skip Wild Mounts discards the plans as usual"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val done = attackerChoosesMountsThen(b, horseArchers)
      .pick(b.actor, WildMounts.decisionId, WildMounts.skip)
    assert(Look(done.state).inRegionalDiscard(horseArchers))
    assert(Look(done.state).advisers(b.actor).contains(forestWarden))
    assertEquals(lines(done), Vector.empty)

  test("one Beast card replaces two Nomad plans"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers, lancers)
    val done = attackerChoosesMountsThen(b, horseArchers, lancers)
      .pick(b.actor, WildMounts.decisionId,
        DecisionOptionRef.Denizen(forestWarden))
    val advisers = Look(done.state).advisers(b.actor)
    assert(advisers.contains(horseArchers) && advisers.contains(lancers))
    assert(Look(done.state).inRegionalDiscard(forestWarden))
    assertEquals(lines(done), Vector(swapped(forestWarden)))

  test("a user who rules no Beast card is not asked"):
    val b = Vector(card, horseArchers).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))
    val done = attackerChoosesMountsThen(b, horseArchers)
    assert(!awaits(done, b.actor, WildMounts.decisionId))
    assert(Look(done.state).inRegionalDiscard(horseArchers))

  test("a defender is asked, and the line names them"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = Vector(card, stormCaller, forestWarden).foldLeft(base)(
      (b, id) => withAdviserFor(b, base.rival, id, Orientation.FaceUp))
    val asked = commit(rules(losingDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(stormCaller)).finishPlanWindows
    assert(awaits(asked, b.rival, WildMounts.decisionId))
    val done = asked.pick(b.rival, WildMounts.decisionId,
      DecisionOptionRef.Denizen(forestWarden))
    assert(Look(done.state).advisers(b.rival).contains(stormCaller))
    assertEquals(lines(done), Vector(NoteText.Said("swapped-off-turn",
      s"${b.rival.value} discarded ${forestWarden.value} instead of the " +
        "battle plans.", covers = false)))

  test("Warning Signals' discard is replaced too"):
    // One warband at the origin and none on the board: Warning Signals has
    // nothing to move, so it asks nothing.
    val ruled = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val base = on(ruled)(_.warbandsAt(ruled.origin, ruled.rival, 1)
      .boardWarbands(ruled.rival, 0))
    val b = Vector(card, warningSignals, forestWarden).foldLeft(base)(
      (b, id) => withAdviserFor(b, base.rival, id, Orientation.FaceUp))
    val asked = commit(rules(losingDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(warningSignals)).finishPlanWindows
    assert(awaits(asked, b.rival, WildMounts.decisionId))
    val done = asked.pick(b.rival, WildMounts.decisionId,
      DecisionOptionRef.Denizen(forestWarden))
    assert(Look(done.state).advisers(b.rival).contains(warningSignals))
    assert(Look(done.state).inRegionalDiscard(forestWarden))

  test("bandits discard the first Beast card they rule, in map order, " +
      "instead of two Nomad plans"):
    val base = board(extras = 2, warbands = 5, supply = 7)
    val first = base.extras(0)
    val second = base.extras(1)
    // Wild Mounts, Mounted Patrol and Storm Caller are free defender plans
    // the bandits apply at the origin.
    val b = withSiteCard(withSiteCard(Vector(card, mountedPatrol,
      stormCaller).foldLeft(base)((b, id) => withSiteCard(b, base.origin,
      id)), second, newGrowth), first, python)
    val inOrder = b.ready.game.current.map.inPlay
    val expected = if inOrder.indexOf(first) < inOrder.indexOf(second)
      then python else newGrowth
    val done = commit(rules(losingDice), b, 4).finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(expected))
    assert(!Look(done.state).inRegionalDiscard(
      if expected == python then newGrowth else python))
    val atOrigin = Look(done.state).cardsAt(base.origin)
    assert(atOrigin.contains(mountedPatrol) && atOrigin.contains(stormCaller))
    assertEquals(lines(done), Vector(swapped(expected)))

  test("bandits with one Nomad plan discard it as usual"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(withSiteCard(base, base.origin, card),
      base.origin, mountedPatrol), base.extras.head, python)
    val done = commit(rules(losingDice), b, 4).finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(mountedPatrol))
    assert(!Look(done.state).inRegionalDiscard(python))
    assertEquals(lines(done), Vector.empty)

  test("its rewrite folds Late, after every plan's own steps at the root"):
    val phases = WildMounts.forCatalog(catalog).contributions(
      PowerWindow.CampaignActionEligibility).collect {
        case transform: Transform => transform.phase }
    assertEquals(phases, Vector(FoldPhase.Late))

  test("a Nomad plan whose discard is a bare PlanDiscard.denizen is " +
      "replaced too"):
    val powers = WalkerPowerCatalog.default(catalog).powers
      .filterNot(_.id == HorseArchersCard.power.id) :+
      BareDiscardNomadPlan(catalog)
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val done = attackerChoosesMountsUnder(rulesWith(powers, winningDice), b,
      horseArchers).pick(b.actor, WildMounts.decisionId,
      DecisionOptionRef.Denizen(forestWarden))
    assert(Look(done.state).advisers(b.actor).contains(horseArchers))
    assert(Look(done.state).inRegionalDiscard(forestWarden))

/** Horse Archers' card as a free attacker plan whose discard at the end of
  * the Campaign is a bare `PlanDiscard.denizen`, with no helper and no
  * line, as a plan that forgets `afterCampaign` would build it. */
private final case class BareDiscardNomadPlan(
    catalog: oathdigital.catalog.ExecutableCatalog) extends BattlePlan:
  val cardId: DenizenId = HorseArchersCard.id
  def id: PowerId = PowerId("test.bare-discard-nomad-plan")
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Bare discard", Vector.empty, Vector.empty))
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))
